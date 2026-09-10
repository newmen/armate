(ns armate.archimate.archi.parser
  (:require [clojure.tools.logging :as log]
            [clojure.string :as s]
            [clojure.xml :as xml]
            [armate.archimate.builder :as abd]
            [armate.archimate.model :as model]
            [armate.archimate.name :as name]
            [armate.archimate.multi-graph :as mg]
            [armate.utils :as u])
  (:import [java.io ByteArrayInputStream]))

(defn get-idx
  "Pure id → ordinal-string allocation. Returns [idx id-map']. Same id always maps to the
  same ordinal for a given id-map; a fresh (empty) id-map seeds indexing at 1."
  ([id-map id]
   (if-let [idx (get id-map id)]
     [idx id-map]
     (let [idx (str (inc (count id-map)))]
       [idx (assoc id-map id idx)]))))

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

(declare get-full-graph)
(declare rel-types-map)

(defn get-model
  [model-path]
  (let [model (->> (read-archi-file model-path)
                   (parse-model)
                   (enrich-model))]
    (-> model
        (assoc :path model-path)
        (assoc :name (get-file-name model-path)))))

(defn view-placed-ids
  "For a view's content, the sets of depicted element-ids and relationship-ids
   (from `:child/@archimateElement` and `:child/:sourceConnection/@archimateRelationship`).
   Walks the whole diagram tree, so elements nested inside a container (e.g. a
   grouping's `<child>` DiagramObjects) and their relationships count as placed."
  [view-content]
  (reduce
   (fn [acc item]
     (if (= :child (:tag item))
       (let [rels (reduce (fn [a conn]
                            (if (= :sourceConnection (:tag conn))
                              (conj a (get-in conn [:attrs :archimateRelationship]))
                              a))
                          #{}
                          (:content item))
             nested (view-placed-ids (:content item))]
         (-> acc
             (update :elements conj (get-in item [:attrs :archimateElement]))
             (update :elements into (:elements nested))
             (update :relations into rels)
             (update :relations into (:relations nested))))
       acc))
   {:elements #{} :relations #{}}
   view-content))

(defn build-view-indexes
  "Build the view-membership metadata for an enriched @model (as returned by @get-model):
   `:element-views {element-alias #{view-names}}` and
   `:relation-views {[from-alias to-alias] {rel-type #{view-names}}}` keyed by alias.

   @archi-alias is the id→element map (see @enrich-with-graph); it must come from the same
   full-graph build so aliases match the graph. Pure: threads an accumulator, no mutable
   state."
  [model archi-alias]
  (let [id->alias (fn [id] (get-in archi-alias [id :alias]))
        find-rel (fn [rid] (get-in model [:maps :relations rid]))]
    (reduce (fn [acc [view-name view]]
              (let [{:keys [elements relations]} (view-placed-ids (:content view))]
                (reduce (fn [a1 eid]
                          (if-let [alias (id->alias eid)]
                            (update-in a1 [:element-views alias] u/fnil-conj-set view-name)
                            a1))
                        (reduce (fn [a2 rid]
                                  (if-let [rel (find-rel rid)]
                                    (let [src (id->alias (get-in rel [:attrs :source]))
                                          tgt (id->alias (get-in rel [:attrs :target]))
                                          rtype (rel-types-map (get-xtype rel))]
                                      (if (and src tgt)
                                        (update-in a2 [:relation-views [src tgt] rtype]
                                                   u/fnil-conj-set view-name)
                                        a2))
                                    a2))
                                acc
                                relations)
                        elements)))
            {:element-views {} :relation-views {}}
            (get-in model [:maps :views]))))

(defn enrich-with-graph
  "Run the parser's full-graph alias allocation exactly once over the enriched @model
   (from @get-model) and derive both the `:archi-alias` id→element map and the
   `:element-views`/`:relation-views` indexes from that single graph. Returns
   `[enriched' full-context]` where @enriched' carries :archi-alias, :element-views and
   :relation-views, and @full-context is the same graph used to build them."
  [enriched]
  (let [[graph _] (get-full-graph (:source enriched))
        archi-alias (get-in graph [:misc :archi])
        enriched (assoc enriched :archi-alias archi-alias)
        enriched (merge enriched (build-view-indexes enriched archi-alias))]
    [enriched graph]))

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
   "Capability" :strategy-capability
   "CourseOfAction" :strategy-course-of-action
   "ValueStream" :strategy-value-stream
   "Resource" :strategy-resource
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
  "Return [id id-map' context element] for @item, allocating a fresh immutable alias via
  @id-map (threaded, returned as id-map')."
  ([context item]
   (add-element context item nil {}))
  ([context item names-replacer]
   (add-element context item names-replacer {}))
  ([context item names-replacer id-map]
   (let [id (get-id item)
         xtype (get-xtype item)
         name (get-in item [:attrs :name])
         name (name/normalize-name name)
         name (if names-replacer
                (names-replacer name)
                name)
         [idx id-map'] (get-idx id-map id)
         [ctx element] (case xtype
                         "Grouping" (abd/add-grouping context idx name)
                         "Junction" (let [jt (keyword (get-type item "and"))]
                                      (abd/add-connector context jt idx name))
                         (let [kind (element-kinds-map xtype)]
                           (if kind
                             (abd/add-element context kind idx name)
                             (throw (ex-info (str "Undefined " xtype)
                                             {:type xtype :id id :name name})))))]
     [id id-map' ctx element])))

(defn add-elements
  "Return [context id-map'] with all @elements added, threading a pure id-map alias allocator.
  Each element registers its id -> alias mapping through @model/cache-alias (the seam owns the
  @:misc :archi@ bookkeeping), and a fresh id-map seed keeps output identical to the legacy
  global-counter behaviour."
  ([context elements]
   (add-elements context elements nil {}))
  ([context elements names-replacer]
   (add-elements context elements names-replacer {}))
  ([context elements names-replacer id-map]
   (reduce (fn [[ctx id-map] item]
             (if item
               (let [[id id-map' ctx element] (add-element ctx item names-replacer id-map)]
                 [(model/cache-alias ctx id element) id-map'])
               [ctx id-map]))
           [context id-map]
           elements)))

(defn add-relations
  [context relations]
  (reduce (fn [acc item]
            (if item
              (let [gef #(model/alias-for-id acc (get-in item [:attrs %]))
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
  "Build the full graph context for @model. Pure w.r.t. aliases: with no id-map, indexing is
  deterministic (1,2,3,…) for a given element order. Returns [context id-map']."
  ([model]
   (get-full-graph model nil {}))
  ([model names-replacer]
   (get-full-graph model names-replacer {}))
  ([model names-replacer id-map]
   (let [[context id-map] (add-elements abd/init-context
                                        (:elements model)
                                        names-replacer
                                        id-map)]
     [(add-relations context (:relations model)) id-map])))

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

(defn build-views-graph
  "Core: build a graph for @view-names (all views when empty) from @model, threading a pure
  id-map alias allocator and an optional names-replacer fn. Returns [context id-map']."
  [model view-names names-replacer id-map]
  (let [view-names2 (if (empty? view-names)
                      (keys (get-in model [:maps :views]))
                      view-names)
        submodel (reduce (fn [acc view-name]
                           (let [view (get-in model [:maps :views view-name])]
                             (reduce (partial add-child-element model)
                                     acc
                                     (:content view))))
                         {:elements []
                          :relations []}
                         view-names2)
        [graph id-map] (get-full-graph submodel names-replacer id-map)]
    [(-> graph
         (update :relations (partial mg/erase-transitive-relationships
                                     #{:aggregation :composition}))
         (model/set-start-title (s/join ", " view-names)))
     id-map]))

(defn get-views-graph
  "Build a graph for the view(s) named in @view-names, or every view when none are given.
  Optional trailing arg is a names-replacer fn (kept for legacy callers). Returns [graph id-map']."
  [model & view-names]
  (let [names-replacer (last view-names)
        names-replacer2 (when-not (string? names-replacer)
                          names-replacer)
        view-names (if names-replacer2
                     (drop-last view-names)
                     view-names)]
    (build-views-graph model view-names names-replacer2 {})))

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
