(ns armate.archimate.multi-graph
  (:require [clojure.set :as o]
            [armate.utils :as u]))

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
                 (if-let [new-rels (seq (process from to rels))]
                   (assoc-in acc [from to] (set new-rels))
                   acc))
               {})))

(defn get-nbrs
  [rel-type graph from]
  (->> (graph from)
       (filter (comp (partial some (comp (partial = rel-type)
                                         :type))
                     second))
       (map first)
       (remove (partial = from))
       (set)))

(defn detect-transitive-relationships
  [rel-type graph]
  (let [gnf (partial get-nbrs rel-type graph)]
    (->> (keys graph)
         (mapcat (fn [from]
                   (let [nbrs (gnf from)]
                     (->> nbrs
                          (map (comp (partial o/intersection nbrs) gnf))
                          (apply o/union)
                          (map (partial vector from)))))))))

(defn erase-transitive-relationships
  [rel-type graph]
  (->> (detect-transitive-relationships rel-type graph)
       (reduce (fn [acc [from to]]
                 (update acc from
                         (fn [to-rels]
                           (let [rels (to-rels to)
                                 rels2 (remove (comp (partial = rel-type) :type) rels)]
                             (if (seq rels2)
                               (assoc to-rels to (set rels2))
                               (dissoc to-rels to))))))
               graph)))
