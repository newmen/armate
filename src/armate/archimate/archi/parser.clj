(ns armate.archimate.archi.parser
  (:require [clojure.tools.logging :as log]
            [clojure.string :as s]
            [clojure.xml :as xml]
            [armate.archimate.builder :as abd]
            [armate.archimate.multi-graph :as mg])
  (:import [java.io ByteArrayInputStream]))

(defonce idx-value (atom 0))
(defonce idx-map (atom {}))

(defn get-idx
  ([]
   (str (swap! idx-value inc)))
  ([id]
   (if-let [idx (get @idx-map id)]
     idx
     (let [idx (get-idx)]
       (swap! idx-map assoc id idx)
       idx))))

(def elemenet-folder-types
  #{"strategy"
    "business"
    "application"
    "technology"
    "motivation"
    "implementation_migration"
    "other"})

(defn get-type
  ([item]
   (get-type item nil))
  ([item default]
   (get-in item [:attrs :type] default)))

(defn element-folder?
  [item]
  (and (= :folder (:tag item))
       (elemenet-folder-types (get-type item))))

(defn relation-folder?
  [item]
  (and (= :folder (:tag item))
       (= "relations" (get-type item))))

(defn view-folder?
  [item]
  (and (= :folder (:tag item))
       (= "diagrams" (get-type item))))

(defn element?
  [item]
  (= :element (:tag item)))

(defn get-file-name
  [file-path]
  (-> (s/split file-path #"/")
      (last)
      (s/split #"\.")
      (first)))

(defn read-archi-file
  [file-path]
  (-> (slurp file-path)
      (.getBytes)
      (ByteArrayInputStream.)
      (xml/parse)
      (:content)))

(defn get-id
  [item]
  (get-in item [:attrs :id]))

(defn cut-id
  [item]
  (-> (get-id item)
      (s/replace-first #"^id-" "")))

(defn get-xtype
  [item]
  (-> (get-in item [:attrs :xsi:type])
      (s/replace-first #"archimate:" "")))

(defn get-childen
  [child? content]
  (loop [items content
         result []]
    (if (empty? items)
      result
      (let [item (first items)]
        (recur (rest items)
               (if (child? item)
                 (conj result item)
                 (concat result
                         (get-childen child? (:content item)))))))))

(defn get-inside
  [holder? child? content]
  (->> (filter holder? content)
       (mapcat :content)
       (get-childen child?)
       (into #{})))

(defn get-elements
  [content]
  (get-inside element-folder? element? content))

(defn get-relations
  [content]
  (get-inside relation-folder? element? content))

(defn get-views
  [content]
  (get-inside view-folder? element? content))

(defn parse-model
  [content]
  {:elements (get-elements content)
   :relations (get-relations content)
   :views (get-views content)})

;; (defn search-elements
;;   [model substr]
;;   (let [ln (s/lower-case substr)]
;;     (->> (:elements model)
;;          (filter (fn [item]
;;                    (s/includes? (s/lower-case (get-in item [:attrs :name])) ln))))))

;; (defn get-relations-between
;;   [model target-id source-id]
;;   (->> (:relations model)
;;        (filter (fn [{attrs :attrs}]
;;                  (or (and (= target-id (:target attrs))
;;                           (= source-id (:source attrs)))
;;                      (and (= target-id (:source attrs))
;;                           (= source-id (:target attrs))))))))

;; (defn search-relations-between
;;   [model target-substr source-substr]
;;   (let [targets (search-elements model target-substr)
;;         sources (search-elements model source-substr)]
;;     (set (apply concat
;;                 (for [target targets
;;                       source sources]
;;                   (let [target-id (get-id target)
;;                         source-id (get-id source)]
;;                     (->> (get-relations-between model target-id source-id)
;;                          (map (comp (partial vector target source) get-id)))))))))

;; (defn src-connect?
;;   [rel-ids item]
;;   (and (= :sourceConnection (:tag item))
;;        (rel-ids (get-in item [:attrs :archimateRelationship]))))

;; (defn has-relations?
;;   [rel-ids view]
;;   (seq (get-inside #(= :child (:tag %))
;;                    (partial src-connect? rel-ids)
;;                    (:content view))))

;; (defn search-view-with-rel-btw
;;   [model target-substr source-substr]
;;   (let [rel-ids (->> (search-relations-between model target-substr source-substr)
;;                      (map last)
;;                      (into #{}))]
;;     (->> (:views model)
;;          (filter (partial has-relations? rel-ids))
;;          (map #(get-in % [:attrs :name]))
;;          (into #{}))))

;; (defn has-elements?
;;   [element-ids view]
;;   (seq (get-inside #(and (= :child (:tag %))
;;                          (element-ids (get-in % [:attrs :archimateElement])))
;;                    identity
;;                    (:content view))))

;; (defn search-view-with-element
;;   [model target-substr]
;;   (let [target-ids (->> (search-elements model target-substr)
;;                         (map #(get-in % [:attrs :id]))
;;                         (set))]
;;     (->> (:views model)
;;          (filter (partial has-elements? target-ids))
;;          (map #(get-in % [:attrs :name]))
;;          (into #{}))))

(defn build-map
  [model key f]
  (->> (model key)
       (map (juxt f identity))
       (into {})))

(defn enrich-model
  [model]
  {:source model
   :maps {:elements (build-map model :elements get-id)
          :relations (build-map model :relations get-id)
          :views (build-map model :views #(get-in % [:attrs :name]))}})

(defn get-model
  [model-path]
  (let [model (->> (read-archi-file model-path)
                   (parse-model)
                   (enrich-model))]
    (-> model
        (assoc :path model-path)
        (assoc :name (get-file-name model-path)))))

(def element-kinds-map
  {"ApplicationCollaboration" :application-collaboration
   "ApplicationComponent" :application-component
   "ApplicationEvent" :application-event
   "ApplicationFunction" :application-function
   "ApplicationInteraction" :application-interaction
   "ApplicationInterface" :application-interface
   "ApplicationProcess" :application-process
   "ApplicationService" :application-service
   "Artifact" :technology-artifact
   "Assessment" :motivation-assessment
   "BusinessActor" :business-actor
   "BusinessCollaboration" :business-collaboration
   "BusinessEvent" :business-event
   "BusinessFunction" :business-function
   "BusinessInteraction" :business-interaction
   "BusinessObject" :business-object
   "BusinessProcess" :business-process
   "BusinessRole" :business-role
   "BusinessService" :business-service
   "Contract" :business-contract
   "Constraint" :motivation-constraint
   "CommunicationNetwork" :technology-communication-network
   "DataObject" :application-data-object
   "Deliverable" :implementation-deliverable
   "Driver" :motivation-driver
   "ImplementationEvent" :implementation-event
   "Goal" :motivation-goal
   "Node" :technology-node
   "Outcome" :motivation-outcome
   "Principle" :motivation-principle
   "Product" :business-product
   "Requirement" :motivation-requirement
   "Stakeholder" :motivation-stakeholder
   "SystemSoftware" :technology-system-software
   "TechnologyCollaboration" :technology-collaboration
   "TechnologyEvent" :technology-event
   "TechnologyFunction" :technology-function
   "TechnologyInterface" :technology-interface
   "TechnologyProcess" :technology-process
   "TechnologyService" :technology-service
   "WorkPackage" :implementation-workpackage})

(defn rel-types-map
  [type]
  (-> (s/replace-first type #"Relationship$" "")
      (s/lower-case)
      (keyword)))

(defn add-element
  ([context item]
   (add-element context item nil))
  ([context item names-replacer]
   (let [id (get-id item)
         xtype (get-xtype item)
         name (get-in item [:attrs :name])
         name (if names-replacer
                (names-replacer name)
                name)
         idx (get-idx id)]
     (cons id
           (case xtype
             "Grouping" (abd/add-grouping context idx name)
             "Junction" (let [jt (keyword (get-type item "and"))]
                          (abd/add-connector context jt idx name))
             (let [kind (element-kinds-map xtype)]
               (if kind
                 (abd/add-element context kind idx name)
                 (throw (ex-info (str "Undefined " xtype)
                                 {:type xtype :id id :name name})))))))))

(defn add-elements
  ([context elements]
   (add-elements context elements nil))
  ([context elements names-replacer]
   (reduce (fn [acc item]
             (if item
               (let [[id ctx element] (add-element acc item names-replacer)]
                 (assoc-in ctx [:misc :archi id] element))
               acc))
           context
           elements)))

(defn add-relations
  [context relations]
  (reduce (fn [acc item]
            (if item
              (let [gef #(get-in acc [:misc :archi (get-in item [:attrs %])])
                    source (gef :source)
                    target (gef :target)
                    strength (get-in item [:attrs :strength])
                    type (rel-types-map (get-xtype item))
                    type2 (if (= :access type)
                            (case (get-in item [:attrs :accessType])
                              nil :access_w
                              "1" :access_r
                              "2" :access
                              "3" :access_rw)
                            type)
                    dir (when (= :specialization type2) :up)
                    desc (or strength (get-in item [:attrs :name]))]
                #_(abd/add-relation acc source target type2 nil desc)
                (abd/add-relation acc source target type2 dir desc))
              acc))
          context
          relations))

(defn get-full-graph
  ([model]
   (get-full-graph model nil))
  ([model names-replacer]
   (-> abd/init-context
       (add-elements (:elements model) names-replacer)
       (add-relations (:relations model)))))

(declare add-inner)
(defn add-child-element
  [model submodel item]
  (let [ref (get-in item [:attrs :archimateElement])
        element (get-in model [:maps :elements ref])]
    (reduce (partial add-inner model)
            (update submodel :elements conj element)
            (:content item))))

(defn add-child-relation
  [model submodel item]
  (let [ref (get-in item [:attrs :archimateRelationship])
        relation (get-in model [:maps :relations ref])]
    (update submodel :relations conj relation)))

(defn add-inner
  [model submodel item]
  (case (:tag item)
    :child (add-child-element model submodel item)
    :sourceConnection (add-child-relation model submodel item)
    :bounds submodel
    (do
      (log/warn "Incorrect item tag" item)
      submodel)))

(defn get-views-graph
  [model & view-names]
  (let [names-replacer (last view-names)
        names-replacer2 (when-not (string? names-replacer)
                          names-replacer)
        view-names (if names-replacer2
                     (drop-last view-names)
                     view-names)
        view-names2 (if (empty? view-names)
                      (keys (get-in model [:maps :views]))
                      view-names)
        submodel (reduce (fn [acc view-name]
                           (let [view (get-in model [:maps :views view-name])]
                             (reduce (partial add-child-element model)
                                     acc
                                     (:content view))))
                         {:elements []
                          :relations []}
                         view-names2)]
    (-> (get-full-graph submodel names-replacer2)
        (update :relations (partial mg/erase-transitive-relationships
                                    #{:aggregation :composition}))
        (assoc-in [:start :title] (s/join ", " view-names)))))

(defn get-component-names
  ([context]
   (get-component-names context []))
  ([context excess-regexps]
   (let [component-names (->> (:elements context)
                              (vals)
                              (filter (comp (partial = :application-component) :kind))
                              (map :name))]
     (reduce (fn [acc excess-regexp]
               (remove (partial re-find excess-regexp) acc))
             component-names
             excess-regexps))))
