(ns armate.archimate.multi-graph
  (:require [clojure.set :as o]
            [armate.utils :as u]))

(defn get-nodes
  [graph]
  (set (mapcat (comp (partial apply concat)
                     (juxt (comp vector first)
                           (comp keys second)))
               graph)))

(defn reverse-graph
  [graph]
  (reduce-kv
   (fn [acc from nbrs]
     (reduce-kv
      (fn [a to rels]
        (reduce (fn [a2 rel]
                  (let [rd (case (:direction rel)
                             :up :down
                             :down :up
                             nil)
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
  [rel-types graph from]
  (get-nbrs (comp rel-types :type)
            graph
            from))

(defn detect-transitive-relationships
  [rel-types graph]
  (let [gnf (partial get-type-nbrs rel-types graph)]
    (->> (keys graph)
         (mapcat (fn [from]
                   (let [nbrs (gnf from)]
                     (->> nbrs
                          (map (comp (partial o/intersection nbrs) gnf))
                          (apply o/union)
                          (map (partial vector from)))))))))

(defn mark-transitive-relationships
  [rel-type graph]
  (->> (detect-transitive-relationships #{rel-type} graph)
       (reduce (fn [acc [from to]]
                 (update acc from
                         (fn [to-rels]
                           (update to-rels to
                                   (fn [rels]
                                     (->> rels
                                          (map (fn [rel]
                                                 (if (= rel-type (:type rel))
                                                   (assoc rel :desc "transitive")
                                                   rel)))
                                          (set)))))))
               graph)))

(defn erase-transitive-relationships
  ([rel-types graph]
   (erase-transitive-relationships (constantly false) rel-types graph))
  ([skip-pred rel-types graph]
   (->> (detect-transitive-relationships rel-types graph)
        (reduce (fn [acc [from to]]
                  (update acc from
                          (fn [to-rels]
                            (let [rels (to-rels to)
                                  rels2 (remove (fn [rel]
                                                  (and (not (skip-pred rel))
                                                       (rel-types (:type rel))))
                                                rels)]
                              (if (seq rels2)
                                (assoc to-rels to (set rels2))
                                (dissoc to-rels to))))))
                graph))))

(defn detect-cyclic1-relationships
  [rel-type graph]
  (let [gnf (partial get-type-nbrs #{rel-type} graph)]
    (->> (keys graph)
         (mapcat (fn [from]
                   (let [nbrs (gnf from)]
                     (->> nbrs
                          (remove (comp empty? (partial o/intersection #{from}) gnf))
                          (map (partial hash-set from))))))
         (set))))

(defn get-vertex-weight
  [rel-type forward-graph reversed-graph vertex]
  (let [rel-types #{rel-type}
        forward-nbrs (get-type-nbrs rel-types forward-graph vertex)
        reversed-nbrs (get-type-nbrs rel-types reversed-graph vertex)]
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

(defn bfs-shortest-path
  [graph start goal rel-pred?]
  (when (and start goal)
    (let [get-neighbors (fn [node]
                          (->> (for [[to rels] (get graph node)
                                     rel rels]
                                 {:node to :rel rel})
                               (filter (comp rel-pred? :rel))))
          queue (into clojure.lang.PersistentQueue/EMPTY [])]
      (loop [queue (conj queue [start []])
             visited #{start}]
        (when-let [[current path] (peek queue)]
          (cond
            (= current goal) (conj path current)
            :else (let [neighbors (remove #(visited (:node %)) (get-neighbors current))
                        new-visited (into visited (map :node neighbors))
                        new-queue (into (pop queue)
                                        (map (fn [{:keys [node rel]}]
                                               [node (conj path [current rel])])
                                             neighbors))]
                    (recur new-queue new-visited))))))))

(defn all-paths-under-len
  "Возвращает вектор всех простых направленных путей из start в goal,
   где число рёбер в пути строго меньше max-len.
   Формат пути: [[n0 rel0] [n1 rel1] ... goal]"
  ([graph start goal rel-pred?]
   [(bfs-shortest-path graph start goal rel-pred?)])
  ([graph start goal max-len rel-pred?]
   (when (and start goal)
     (letfn [(neighbors [node]
               (->> (for [[to rels] (get graph node)
                          rel rels]
                      {:node to :rel rel})
                    (filter (comp rel-pred? :rel))))
             (dfs [current depth visited path acc]
               ;; Если дошли до goal и глубина (число рёбер) < max-len — фиксируем путь.
               (let [acc1 (if (and (= current goal) (< depth max-len))
                            (conj acc (conj path current))
                            acc)]
                 ;; Если следующий шаг дал бы длину >= max-len — останавливаем углубление.
                 (if (> depth max-len)
                   acc1
                   (reduce (fn [ain {:keys [node rel]}]
                             (if (visited node)
                               ain
                               (dfs node
                                    (inc depth)
                                    (conj visited node)
                                    (conj path [current rel])
                                    ain)))
                           acc1
                           (neighbors current)))))]
       (if (pos? max-len)
         (sort-by count (dfs start 0 #{start} [] []))
         [])))))

(defn adsorb-graph
  [base graph rel-kind]
  (->> (get-relationships graph)
       (reduce (fn [acc [from to rel]]
                 (update-in acc [from to] u/fnil-conj-set (assoc rel :kind rel-kind)))
               base)))

(defn get-undirected-graph
  [graph]
  (-> {}
      (adsorb-graph graph :forward)
      (adsorb-graph (reverse-graph graph) :reverse)))
