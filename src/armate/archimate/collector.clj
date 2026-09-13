(ns armate.archimate.collector
  (:require [clojure.set :as o]
            [armate.archimate.model :as model]
            [armate.archimate.multi-graph :as mg]))

(defn- filter-aliases
  [context predicate]
  (->> (vals (:elements context))
       (filter predicate)
       (map :alias)
       (into #{})))

(defn select-just-elements
  [context predicate]
  (let [aliases (filter-aliases context predicate)
        frf (partial mg/filter-relationships
                     (fn [[from to _]]
                       (and (aliases from) (aliases to))))]
    (-> context
        (update :elements #(select-keys % aliases))
        (update :relations frf)
        (update :hidden frf))))

(defn ungroup
  [context]
  (->> (:relations context)
       (mg/get-relationships)
       (reduce (fn [acc [from to rel]]
                 (if (and (= :nesting (:derivate rel))
                          (not= :grouping (model/element-kind acc from)))
                   (-> (model/set-element-in acc to nil)
                       (model/set-relation from to (dissoc rel :derivate)))
                   (model/set-relation acc from to rel)))
               (assoc context :relations {}))))

(defn erase-unbinded-elements
  ([context]
   (erase-unbinded-elements context (constantly true)))
  ([context predicate]
   (let [all-aliases (into #{} (keys (:elements context)))
         related-aliases (->> (:relations context)
                              (mg/get-relationship-sets)
                              (mapcat (juxt first second))
                              (into #{}))
         unbinded-aliases (o/difference all-aliases related-aliases)]
     (select-just-elements context
                           (fn [element]
                             (not (and (predicate element)
                                       (unbinded-aliases (:alias element)))))))))

(defn erase-groups-wihtout-elements
  [context element-predicate]
  (let [gf (fn [[from to rel]]
             (and (= :grouping (model/element-kind context from))
                  (#{:aggregation :composition} (:type rel))
                  (element-predicate (model/element context to))))
        group-aliases (->> (:relations context)
                           (mg/filter-relationships gf)
                           (keys))
        element-aliases (->> (:elements context)
                             (filter (comp (partial not= :grouping) :kind second))
                             (map first))
        aliases (into #{} (concat group-aliases element-aliases))]
    (select-just-elements context (comp aliases :alias))))
