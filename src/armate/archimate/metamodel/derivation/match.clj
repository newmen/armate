(ns armate.archimate.metamodel.derivation.match
  (:require [clojure.set :as o]
            [armate.archimate.metamodel.derivation.rules :as drs]
            [armate.archimate.multi-graph :as mg]
            [armate.utils :as u]))

(defn get-rel-wieght
  [rel]
  (let [dynamic-index (.indexOf drs/dynamic-rels rel)]
    (if-not (neg? dynamic-index)
      (inc dynamic-index)
      (let [dependency-index (.indexOf drs/dependency-rels rel)]
        (if-not (neg? dependency-index)
          (* 100 (inc dependency-index))
          (let [structural-index (.indexOf drs/structural-rels rel)]
            (if-not (neg? structural-index)
              (* 1000 (inc structural-index))
              (if (= :specialization rel)
                10000
                (throw (ex-info "Unknown relation" {:rel rel}))))))))))

(defn make-rules-map
  [rules]
  (->> rules
       (group-by (comp first first))
       (map (juxt first
                  (fn [[_ group]]
                    (sort-by (comp - get-rel-wieght first second) group))))
       (into {})))

(defn get-weights
  [graph]
  (let [source-weights (->> graph
                            (map (juxt first
                                       (fn [[_ nbrs]]
                                         (->> (vals nbrs)
                                              (reduce concat)
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

(defn get-prioritized-relationships
  [graph]
  (let [weights (get-weights graph)]
    (mg/get-relationships (partial sort-by
                                   (fn [[from & _]]
                                     (conj (weights from [0 0]) from)))
                          graph)))

(def rel-kin-map
  (reduce (fn [acc group]
            (reduce (fn [a rel]
                      (assoc a rel (set group)))
                    acc
                    group))
          {}
          [drs/structural-rels
           drs/dependency-rels
           drs/dynamic-rels
           drs/other-rels]))

(defn match-rule
  [from to
   iter-map
   [[_ f1 t1] [next-rel f2 t2] [result-rel fr tr]]]
  (let [{forward-graph :forward-graph
         reverse-graph :reverse-graph} iter-map
        relation {:type result-rel}
        kin (rel-kin-map result-rel)
        checking-graph (if (#{f1 t1} fr)
                         forward-graph
                         reverse-graph)
        have-relation? (fn [f t]
                         (->> (get-in checking-graph [f t])
                              (map :type)
                              (into #{})
                              (o/intersection kin)
                              (seq)))
        add-relation (fn [acc f t]
                       (-> acc
                           (update-in [:forward-graph f t] u/fnil-conj-set relation)
                           (update-in [:reverse-graph t f] u/fnil-conj-set relation)
                           (update-in [:derivated-graph f t] u/fnil-conj-set relation)
                           (update :derivated-relations conj [f t result-rel])))
        exists? (if (#{f2 t2} f1)
                  (partial have-relation? to)
                  (partial have-relation? from))
        append (if (= f1 fr)
                 #(add-relation %1 from %2)
                 (if (= f1 tr)
                   #(add-relation %1 %2 from)
                   (if (= t1 fr)
                     #(add-relation %1 to %2)
                     ; (= t1 tr)
                     #(add-relation %1 %2 to))))]
    (->> (if (= f1 f2)
           (-> (forward-graph from)
               (dissoc to))
           (if (= f1 t2)
             (-> (reverse-graph from)
                 (dissoc to))
             (-> (if (= t1 f2)
                   (forward-graph to)
                   ; (= t1 t2)
                   (reverse-graph to))
                 (dissoc from))))
         (filter (comp (partial some (comp (partial = next-rel) :type)) second))
         (map first)
         (remove exists?)
         (reduce append iter-map))))

(defn derivate-relationships-once
  [rules graph]
  (let [rules-map (make-rules-map rules)]
    (loop [forward-graph graph
           reverse-graph (mg/reverse-graph graph)
           derivated-graph {}
           follow-relations (->> (get-prioritized-relationships graph)
                                 (map (juxt first second (comp :type last))))]
      (if (empty? follow-relations)
        derivated-graph
        (let [relation (first follow-relations)
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
                 (concat (rest follow-relations)
                         (:derivated-relations iter-map))))))))

(defn derivate-relationships
  [rules source-graph]
  ;; {:pre (every? rs/valid? rules)} ; already checked by rules_test/check-invariants-test
  (let [deep-merge (partial merge-with into)]
    (loop [graph source-graph
           derivated-graph {}]
      (let [next-derivated-graph (derivate-relationships-once rules graph)]
        (if (empty? next-derivated-graph)
          derivated-graph
          (recur (merge-with deep-merge graph next-derivated-graph)
                 (merge-with deep-merge derivated-graph next-derivated-graph)))))))
