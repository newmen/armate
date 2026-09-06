(ns armate.archimate.core
  (:require [clojure.string :as s]
            [armate.archimate.collector :as acl]
            [armate.archimate.metamodel.derivation.core :as dcr]
            [armate.archimate.metamodel.meta :as mt]
            [armate.archimate.viz.saver :as svr]
            [armate.archimate.multi-graph :as mg]
            [armate.archimate.plantuml.parser :as prr]))

(defn layer?
  [layer kind]
  (s/starts-with? (name kind)
                  (name layer)))

(defn get-total-map
  [item-f group-f group]
  {:total (count group)
   :groups (frequencies (map group-f group))
   :inside (frequencies (map item-f group))})

(defn get-element-group
  [element]
  (let [k (:kind element)]
    (cond
      (mt/subject? k) :subject
      (mt/behavior? k) :behavior
      (mt/object? k) :object
      (mt/motivation? k) :motivation
      (mt/composite? k) :composite)))

(get-element-group {:kind :application-event})

(defn get-element-layer
  [element]
  (-> (:kind element)
      (name)
      (s/split #"-")
      (first)
      (keyword)))

(defn get-rel-group
  [relation]
  (let [t (:type (last relation))]
    (cond
      (mt/structural? t) :structural
      (mt/dependency? t) :dependency
      (mt/dynamic? t) :dynamic
      :else :other)))

(defn get-stats
  [context]
  (let [elements (vals (:elements context))
        relations (mg/get-relationships (:relations context))
        groups (group-by (comp :derivate last) relations)
        gf (comp :type last)]
    {:types (count (:types context))
     :elements (assoc (get-total-map :kind get-element-group elements)
                      :layer (frequencies (map get-element-layer elements)))
     :relations {:original (get-total-map gf get-rel-group (groups nil []))
                 :nesting (get-total-map gf get-rel-group (groups :nesting []))
                 :certain (get-total-map gf get-rel-group (groups :certain []))
                 :potential (get-total-map gf get-rel-group (groups :potential []))}
     :lints (count (:lints context))}))

(defn analyze-file
  [file-path]
  (let [content (slurp file-path)]
    (if (empty? content)
      (throw (ex-info "File is empty" {:file-path file-path}))
      (prr/analyze-content content))))

(defn stat-file
  [file-path]
  (try
    (-> file-path
        analyze-file
        dcr/derivate-relations
        get-stats)
    (catch clojure.lang.ExceptionInfo e
      {:exception {:message (ex-message e)
                   :data (ex-data e)}})))

(defn find-alias
  [context name]
  (some #(when (= name (:name (second %)))
           (first %))
        (:elements context)))

(defn convert-path
  [context path]
  (reduce (fn [acc item]
            (if (vector? item)
              (-> acc
                  (conj (get-in context [:elements (first item) :name]))
                  (conj (select-keys (second item) [:type :kind])))
              (conj acc (get-in context [:elements item :name]))))
          []
          path))

(defn filter-relationships
  [context key predicate]
  (update context key (partial mg/filter-relationships predicate)))

(defn filter-elements
  [context predicate]
  (let [elem-pred (comp predicate #(get-in context [:elements %]))
        rel-pred (fn [[from to _]]
                   (and (elem-pred from) (elem-pred to)))]
    (-> context
        (update :elements
                (partial reduce-kv
                         (fn [acc alias element]
                           (if (predicate element)
                             (assoc acc alias element)
                             acc))
                         {}))
        (filter-relationships :relations rel-pred)
        (filter-relationships :hidden rel-pred))))

(defn erase-transitive-rels
  ([context rel-types]
   (update context :relations (partial mg/erase-transitive-relationships rel-types)))
  ([context rel-types skip-pred]
   (update context :relations (partial mg/erase-transitive-relationships skip-pred rel-types))))

(defn erase-excess-derivated-rels
  [context]
  (update context :relations (partial mg/process-relationship-sets
                                      (fn [_ _ rels]
                                        (if (< 1 (count rels))
                                          (remove :derivate rels)
                                          rels)))))

(defn generate-filtered-context
  ([context predicate out-path]
   (generate-filtered-context context identity predicate out-path))
  ([context element-predicate relation-predicate out-path]
   (try
     (-> context
         (acl/ungroup)
         (dissoc :connectors)
         (dissoc :misc)
         (filter-elements element-predicate)
         (filter-relationships :relations relation-predicate)
         (acl/erase-groups-wihtout-elements element-predicate)
         (acl/erase-unbinded-elements)
         (svr/save-puml out-path))
     (catch clojure.lang.ExceptionInfo e
       {:msg (ex-message e)
        :data (ex-data e)}))))

(defn generate-derivate-context
  ([context predicate out-path]
   (generate-derivate-context context identity predicate out-path))
  ([context element-predicate relation-predicate out-path]
   (try
     (-> context
         (acl/ungroup)
         (dissoc :connectors)
         (dissoc :misc)
         (dcr/derivate-relations)
         (filter-elements element-predicate)
         (filter-relationships :relations relation-predicate)
         (acl/erase-groups-wihtout-elements element-predicate)
         (acl/erase-unbinded-elements (comp (partial = :grouping) :kind))
        ;;  (acl/erase-unbinded-elements)
         (erase-excess-derivated-rels)
         (erase-transitive-rels #{:realization :assignment :aggregation :composition})
         (erase-transitive-rels #{:specialization})
         (erase-transitive-rels #{:association :association_dir})
         (erase-transitive-rels #{:association :association_dir :composition :aggregation}
                                (comp nil? :derivate))
         (erase-transitive-rels #{:access :access_r :access_w :access_rw})
         (erase-transitive-rels #{:serving})
         (svr/save-puml out-path))
     (catch clojure.lang.ExceptionInfo e
       {:msg (ex-message e)
        :data (ex-data e)}))))

(defn generate-derivate-file
  [predicate file-path name-suffix]
  (generate-derivate-context (analyze-file file-path)
                             predicate
                             (svr/add-suffix file-path name-suffix)))
