(ns armate.archimate.multi-graph
  (:require [armate.utils :as u]))

(defn get-relationship-sets
  ([graph]
   (get-relationship-sets identity graph))
  ([prepare graph]
   (->> (prepare graph)
        (mapcat (fn [[from nbrs]]
                  (->> (prepare nbrs)
                       (map (fn [[to rels]]
                              [from to rels]))))))))

(defn get-relationships
  ([graph]
   (get-relationships identity graph))
  ([prepare graph]
   (->> (get-relationship-sets prepare graph)
        (mapcat (fn [[from to rels]]
                  (map (fn [rel]
                         [from to rel])
                       rels))))))

(defn filter-relationships
  [predicate graph]
  (->> (get-relationships graph)
       (filter predicate)
       (reduce (fn [acc [from to rel]]
                 (update-in acc [from to] u/fnil-conj-set rel))
               {})))

(defn process-relationship-sets
  [process graph]
  (->> (get-relationship-sets graph)
       (reduce (fn [acc [from to rels]]
                 (if-let [new-rels (process from to rels)]
                   (assoc-in acc [from to] new-rels)
                   acc))
               {})))
