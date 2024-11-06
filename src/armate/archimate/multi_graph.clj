(ns armate.archimate.multi-graph
  (:require [clojure.set :as o]
            [armate.utils :as u]))

(defn reverse-graph
  [graph]
  (reduce-kv (fn [acc from nbrs]
               (reduce-kv (fn [a to rels]
                            (reduce (fn [a2 rel]
                                      (let [rd (case (:direction rel)
                                                 :up :down
                                                 :down :up)
                                            rel2 (-> (u/assoc-if-not-nil rel :direction rd)
                                                     (u/assoc-if-not-nil :from (:to rel))
                                                     (u/assoc-if-not-nil :to (:from rel)))]
                                        (update-in a2 [to from] u/fnil-conj-set rel2)))
                                    a
                                    rels))
                          acc
                          nbrs))
             {}
             graph))

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
  [predicate graph from]
  (->> (graph from)
       (filter (comp (partial some predicate) second))
       (map first)
       (remove (partial = from))
       (set)))

(defn get-type-nbrs
  [rel-type graph from]
  (get-nbrs (comp (partial = rel-type) :type)
            graph
            from))

(defn detect-transitive-relationships
  [rel-type graph]
  (let [gnf (partial get-type-nbrs rel-type graph)]
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

(defn detect-cyclic1-relationships
  [rel-type graph]
  (let [gnf (partial get-type-nbrs rel-type graph)]
    (->> (keys graph)
         (mapcat (fn [from]
                   (let [nbrs (gnf from)]
                     (->> nbrs
                          (remove (comp empty? (partial o/intersection #{from}) gnf))
                          (map (partial hash-set from))))))
         (set))))

(defn get-vertex-weight
  [rel-type forward-graph reversed-graph vertex]
  (let [forward-nbrs (get-type-nbrs rel-type forward-graph vertex)
        reversed-nbrs (get-type-nbrs rel-type reversed-graph vertex)]
    [(count reversed-nbrs)
     (count forward-nbrs)
     vertex]))

(defn range-vertices
  [rel-type forward-graph reversed-graph vertices]
  (sort-by (partial get-vertex-weight rel-type forward-graph reversed-graph)
           vertices))

(defn erase-cyclic1-relationships
  [rel-type forward-graph]
  (let [cyclic-rels (detect-cyclic1-relationships rel-type forward-graph)]
    (if (empty? cyclic-rels)
      forward-graph
      (let [reversed-graph (reverse-graph forward-graph)]
        (:forward
         (reduce (fn [acc pair]
                   (let [{fg :forward rg :reversed} acc
                         [from to] (range-vertices rel-type fg rg pair)
                         xf (fn [a in]
                              (let [rels (->> (get-in a in)
                                              (remove (comp (partial = rel-type) :type)))]
                                (if (seq rels)
                                  (assoc a in (set rels))
                                  (update-in a (drop-last in) dissoc (last in)))))]
                     (-> acc
                         (xf [:forward from to])
                         (xf [:reversed to from]))))
                 {:forward forward-graph
                  :reversed reversed-graph}
                 cyclic-rels))))))
