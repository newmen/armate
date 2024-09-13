(ns armate.derivation.match
  (:require [armate.derivation.rules :as rs]))

(defn get-rel-wieght
  [rel]
  (let [dynamic-index (rs/get-dynamic-index rel)]
    (if-not (neg? dynamic-index)
      (inc dynamic-index)
      (let [dependency-index (rs/get-dependency-index rel)]
        (if-not (neg? dependency-index)
          (* 10 (inc dependency-index))
          (let [structural-index (rs/get-structural-index rel)]
            (if-not (neg? structural-index)
              (* 100 (inc structural-index))
              (if (= :specialization rel)
                1000
                (throw (ex-info "Unknown relation" {:rel rel}))))))))))

(defn make-rules-map
  [rules]
  (->> rules
       (group-by (comp first first))
       (map (juxt first
                  (fn [[_ group]]
                    (sort-by (comp - get-rel-wieght first second) group))))
       (into {})))

(defn reverse-graph
  [graph]
  (reduce-kv (fn [acc from nbrs]
               (reduce-kv (fn [a to rel]
                            (assoc-in a [to from] rel))
                          acc
                          nbrs))
             {}
             graph))

(defn get-weights
  [graph]
  (let [source-weights (->> graph
                            (map (juxt first
                                       (fn [[_ nbrs]]
                                         (->> (vals nbrs)
                                              (map :type)
                                              (map get-rel-wieght)
                                              (reduce +)))))
                            (into {}))]
    (->> graph
         (map (juxt first
                    (fn [[node nbrs]]
                      (let [wth #(- (source-weights % 0))]
                        [(wth node) (reduce + (map (comp wth first) nbrs))]))))
         (into {}))))

(defn get-relationships
  [graph]
  (let [weights (get-weights graph)]
    (->> graph
         (sort-by (comp weights first))
         (mapcat (fn [[from nbrs]]
                   (->> nbrs
                        (sort-by (comp #(weights % [0 0]) first))
                        (map (fn [[to rel]]
                               [from to (:type rel)]))))))))

(defn check-rule
  [rule]
  (let [[[_ f1 t1] [_ f2 t2] [_ fr tr]] rule]
    (assert (not= f1 t1))
    (assert (not= f2 t2))
    (assert (not= fr tr))
    (assert (or (and (= f1 f2) (not= t1 t2))
                (and (= f1 t2) (not= t1 f2))
                (and (= t1 f2) (not= f1 t2))
                (and (= t1 t2) (not= f1 f2))))
    (assert (or (and (= f1 fr) (not= t1 tr))
                (and (= f1 tr) (not= t1 fr))
                (and (= t1 fr) (not= f1 tr))
                (and (= t1 tr) (not= f1 fr))))
    (assert (or (and (= f2 fr) (not= t2 tr))
                (and (= f2 tr) (not= t2 fr))
                (and (= t2 fr) (not= f2 tr))
                (and (= t2 tr) (not= f2 fr))))
    rule))

(defn match-rule
  [from to
   iter-map
   [[_ f1 t1] [next-rel f2 _] [result-rel fr tr]]]
  (let [{forward-graph :forward-graph
         reverse-graph :reverse-graph} iter-map
        relation {:type result-rel}
        append (if (= f1 fr)
                 (fn [acc node3]
                   (-> acc
                       (assoc-in [:forward-graph from node3] relation)
                       (assoc-in [:reverse-graph node3 from] relation)
                       (assoc-in [:derivated-graph from node3] relation)
                       (update :derivated-relations conj [from node3 result-rel])))
                 (if (= f1 tr)
                   (fn [acc node3]
                     (-> acc
                         (assoc-in [:forward-graph node3 from] relation)
                         (assoc-in [:reverse-graph from node3] relation)
                         (assoc-in [:derivated-graph node3 from] relation)
                         (update :derivated-relations conj [node3 from result-rel])))
                   (if (= t1 fr)
                     (fn [acc node3]
                       (-> acc
                           (assoc-in [:forward-graph to node3] relation)
                           (assoc-in [:reverse-graph node3 to] relation)
                           (assoc-in [:derivated-graph to node3] relation)
                           (update :derivated-relations conj [to node3 result-rel])))
                     ; (= t1 tr)
                     (fn [acc node3]
                       (-> acc
                           (assoc-in [:forward-graph node3 to] relation)
                           (assoc-in [:reverse-graph to node3] relation)
                           (assoc-in [:derivated-graph node3 to] relation)
                           (update :derivated-relations conj [node3 to result-rel]))))))]
    (->> (if (= f1 f2)
           (-> (forward-graph from)
               (dissoc to))
           (-> (if (= t1 f2)
                 (forward-graph to)
                 (reverse-graph to))
               (dissoc from)))
         (filter (comp (partial = next-rel) :type second))
         (map first)
         (remove (fn [node3]
                   (or (get-in forward-graph [from node3])
                       (get-in reverse-graph [from node3]))))
         (reduce append iter-map))))

(defn derivate-rules-once
  [rules graph]
  (let [rules-map (make-rules-map rules)]
    (loop [forward-graph graph
           reverse-graph (reverse-graph graph)
           derivated-graph {}
           relations (get-relationships graph)]
      (if (empty? relations)
        derivated-graph
        (let [relation (first relations)
              [from to rel] relation
              iter-map (reduce (partial match-rule from to)
                               {:forward-graph forward-graph
                                :reverse-graph reverse-graph
                                :derivated-graph derivated-graph
                                :derivated-relations []}
                               (rules-map rel))]
          (recur (:forward-graph iter-map)
                 (:reverse-graph iter-map)
                 (:derivated-graph iter-map)
                 (concat (rest relations)
                         (:derivated-relations iter-map))))))))

(defn derivate-rules
  [rules source-graph]
  (doseq [rule rules] (check-rule rule))
  (loop [graph source-graph
         derivated-graph {}]
    (let [next-derivated-graph (derivate-rules-once rules graph)]
      (if (empty? next-derivated-graph)
        derivated-graph
        (recur (merge-with merge graph next-derivated-graph)
               (merge-with merge derivated-graph next-derivated-graph))))))
