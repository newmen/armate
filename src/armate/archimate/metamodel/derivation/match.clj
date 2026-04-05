(ns armate.archimate.metamodel.derivation.match
  (:require [clojure.tools.logging :as log]
            [clojure.set :as o]
            [armate.archimate.metamodel.derivation.rules :as drs]
            [armate.archimate.metamodel.solver :as slv]
            [armate.archimate.multi-graph :as mg]
            [armate.utils :as u]))

(def log-each-step 100)
(def log-sub-step? false)

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

(defn count-influence
  [desc]
  (let [cf #(count (re-seq % desc))
        plus (cf #"\+")
        minus (- (cf #"[\-–—]"))]
    (+ plus minus)))

(defn num-to-desc
  [num]
  (let [char (if (pos? num) \+ \-)]
    (apply str (repeat (Math/abs num) char))))

(defn calc-influence
  [descs]
  (let [groups (->> (map count-influence descs)
                    (remove zero?)
                    (group-by pos?))
        mf #(if-let [xs (seq (groups %2))]
              (apply %1 xs)
              0)
        plus (mf max true)
        minus (mf min false)
        result (+ plus minus)]
    (when-not (zero? result)
      (num-to-desc result))))

(defn get-passing-rels
  [[[orig-rel f1 t1] [next-rel f2 t2] [result-rel fr tr]]
   iter-map f t c]
  (if (= :influence result-rel)
    (let [{forward-graph :forward-graph
           reverse-graph :reverse-graph} iter-map
          i? (partial = :influence)
          graph (cond
                  (or (= f1 fr)
                      (= t1 tr)) forward-graph
                  (or (= f1 tr)
                      (= t1 fr)) reverse-graph)
          fts (concat
               (when (i? orig-rel)
                 [(cond
                    (#{f2 t2} f1) [c t]
                    (#{f2 t2} t1) [f c])])
               (when (i? next-rel)
                 [(cond
                    (#{f2 t2} f1) [f c]
                    (#{f2 t2} t1) [c t])]))]
      (->> (mapcat (partial get-in graph) fts)
           (filter (comp (partial = :influence) :type))
           (set)))
    #{{:type result-rel}}))

(defn match-rule
  [restricted? from to iter-map rule]
  (let [[[_ f1 t1] [next-rel f2 t2] [result-rel fr tr]] rule
        {forward-graph :forward-graph
         reverse-graph :reverse-graph
         derivated-graph :derivated-graph} iter-map
        add-relations (fn [acc f t c]
                        (let [relations (get-passing-rels rule acc f t c)
                              fwd-set (get-in forward-graph [f t] #{})
                              der-set (get-in derivated-graph [f t] #{})
                              in-fwd? (o/subset? relations fwd-set)
                              in-der? (o/subset? relations der-set)]
                          (cond
                            ;; Relations already exist in both graphs - skip
                            (and in-fwd? in-der?) acc
                            ;; Relations exist in forward-graph but not in derivated-graph
                            in-fwd? (update-in acc [:derivated-graph f t] u/fnil-union-set relations)
                            ;; Relations don't exist - check restrictions and add
                            :else (if (restricted? f t c result-rel)
                                    acc
                                    (-> acc
                                        (update-in [:forward-graph f t] u/fnil-union-set relations)
                                        (update-in [:reverse-graph t f] u/fnil-union-set relations)
                                        (update-in [:derivated-graph f t] u/fnil-union-set relations)
                                        (update :derivated-relations conj [f t result-rel]))))))
        append (cond
                 (= f1 fr) #(add-relations %1 from %2 to)
                 (= f1 tr) #(add-relations %1 %2 from to)
                 (= t1 fr) #(add-relations %1 to %2 from)
                 (= t1 tr) #(add-relations %1 %2 to from))]
    (->> (cond
           (= f1 f2) (forward-graph from)
           (= f1 t2) (reverse-graph from)
           (= t1 f2) (forward-graph to)
           (= t1 t2) (reverse-graph to))
         (filter (comp (partial some (comp (partial = next-rel) :type)) second))
         (map first)
         (reduce append iter-map))))

(defn derivate-relationships-by-map
  [restricted? rules-map graph include-new-derivated?]
  (loop [forward-graph graph
         reverse-graph (mg/reverse-graph graph)
         derivated-graph {}
         follow-relations (->> (mg/get-relationships graph)
                               (map (juxt first second (comp :type last)))
                               (into clojure.lang.PersistentQueue/EMPTY))
         n 1]
    (if (empty? follow-relations)
      (do (when log-sub-step?
            (log/info (str "Derivation sub-step " n ", follow " (count follow-relations) " relations")))
          derivated-graph)
      (let [[from to rel] (first follow-relations)
            iter-map (reduce (partial match-rule restricted? from to)
                             {:forward-graph forward-graph
                              :reverse-graph reverse-graph
                              :derivated-graph derivated-graph
                              :derivated-relations []}
                             (rules-map rel))]
        (recur (:forward-graph iter-map)
               (:reverse-graph iter-map)
               (:derivated-graph iter-map)
               (into (pop follow-relations)
                     (when include-new-derivated?
                       (:derivated-relations iter-map)))
               (inc n))))))

(defn derivate-relationships-once
  ([restricted? rules graph]
   (derivate-relationships-once restricted? rules graph false))
  ([restricted? rules graph include-new-derivated?]
   (let [rules-map (make-rules-map rules)]
     (derivate-relationships-by-map restricted? rules-map graph include-new-derivated?))))

(defn merge-influence-relations
  "Merges multiple :influence relations with different :desc into a single one.
   For each pair of vertices, all :influence relations are combined using calc-influence.
   Returns a set of relations."
  [rels]
  (let [rels-set (if (set? rels) rels (set rels))
        influence-rels (filter #(= :influence (:type %)) rels-set)
        other-rels (filter #(not= :influence (:type %)) rels-set)
        influence-descs (map :desc influence-rels)
        merged-influence (when (seq influence-rels)
                           (let [merged-desc (calc-influence (filter identity influence-descs))]
                             (if merged-desc
                               {:type :influence :desc merged-desc}
                               {:type :influence})))]
    (if merged-influence
      (set (conj other-rels merged-influence))
      (set other-rels))))

(defn merge-graph-influence
  "Merges all :influence relations in the graph.
   Returns a graph with sets as leaf values."
  [graph]
  (->> graph
       (map (fn [[from tos]]
              [from (->> tos
                         (map (fn [[to rels]]
                                [to (merge-influence-relations rels)]))
                         (into {}))]))
       (into {})))

(defn derivate-relationships
  [restricted? rules source-graph]
  ;; {:pre (every? drs/valid? rules)} ; already checked by rules_test/check-invariants-test
  (let [rules-map (make-rules-map rules)]
    (loop [graph source-graph
           derivated-graph {}
           n 1]
      (when (zero? (mod n log-each-step))
        (log/info (str "Derivation step " n)))
      (let [next-derivated-graph (derivate-relationships-by-map restricted?
                                                                rules-map
                                                                graph
                                                                true)]
        (if (= next-derivated-graph derivated-graph)
          ;; Merge all :influence relations with different :desc before returning
          (merge-graph-influence derivated-graph)
          (recur (slv/merge-into graph next-derivated-graph)
                 (slv/merge-into derivated-graph next-derivated-graph)
                 (inc n)))))))
