(ns armate.archimate.archi.parser
  (:require [clojure.string :as s]
            [clojure.xml :as xml]
            [armate.archimate.builder :as abd]
            [armate.archimate.metamodel.meta :as mt])
  (:import [java.io ByteArrayInputStream]))

(def idx-value (atom 0))

(defn get-idx
  []
  (str (swap! idx-value inc)))

(def elemenet-folder-types
  #{"strategy"
    "business"
    "application"
    "technology"
    "motivation"
    "implementation_migration"
    "other"})

(defn element-folder?
  [item]
  (and (= :folder (:tag item))
       (elemenet-folder-types (get-in item [:attrs :type]))))

(defn relation-folder?
  [item]
  (and (= :folder (:tag item))
       (= "relations" (get-in item [:attrs :type]))))

(defn view-folder?
  [item]
  (and (= :folder (:tag item))
       (= "diagrams" (get-in item [:attrs :type]))))

(defn element?
  [item]
  (= :element (:tag item)))

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

(defn get-type
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

(defn get-model
  [model-path]
  (->> (read-archi-file model-path)
       (parse-model)))

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
   "BusinessActor" :business-actor
   "BusinessCollaboration" :business-collaboration
   "BusinessObject" :business-object
   "BusinessRole" :business-role
   "BusinessService" :business-service
   "DataObject" :application-data-object
   "Node" :technology-node
   "SystemSoftware" :technology-system-software
   "TechnologyFunction" :technology-function
   "TechnologyProcess" :technology-process
   "TechnologyService" :technology-service})

(defn rel-types-map
  [type]
  (-> (s/replace-first type #"Relationship$" "")
      (s/lower-case)
      (keyword)))

(defn add-element
  [context item]
  (let [id (get-id item)
        type (get-type item)
        name (get-in item [:attrs :name])]
    (cons id
          (case type
            "Grouping" (abd/add-grouping context name)
            "Junction" (let [jt (keyword (get-in item [:attrs :type] "and"))]
                         (abd/add-connector context jt name))
            (abd/add-element context (element-kinds-map type) (get-idx) name)))))

(defn add-elements
  [context elements]
  (reduce (fn [acc item]
            (let [[id ctx element] (add-element acc item)]
              (assoc-in ctx [:misc :archi id] element)))
          context
          elements))

(defn add-relations
  [context relations]
  (reduce (fn [acc item]
            (let [gef #(get-in acc [:misc :archi (get-in item [:attrs %])])
                  source (gef :source)
                  target (gef :target)
                  type (rel-types-map (get-type item))
                  type2 (if (= :access type)
                          (case (get-in item [:attrs :accessType])
                            nil :access_w
                            "1" :access_r
                            "2" :access
                            "3" :access_rw)
                          type)
                  desc (get-in item [:attrs :name])
                  dir (if (mt/structural? type) :down :up)]
              (abd/add-relation acc source target type2 dir desc)))
          context
          relations))

(defn get-full-graph
  [model]
  (-> abd/init-context
      (add-elements (:elements model))
      (add-relations (:relations model))))
