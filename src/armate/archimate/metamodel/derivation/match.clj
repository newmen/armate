(ns armate.archimate.metamodel.derivation.match
  (:require [clojure.tools.logging :as log]
            [armate.archimate.metamodel.derivation.rules :as drs]
            [armate.archimate.metamodel.solver :as slv]
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

(defn count-influence
  [desc]
  (let [cf #(count (re-seq % desc))
        plus (cf #"\+")
        minus (- (cf #"[\-–—]"))]
    (+ plus minus)))

(defn num-to-desc
  [num]
  (let [char (if (pos? num) \+ \—)]
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

(defn get-passing-desc
  [[[orig-rel f1 t1] [next-rel f2 t2] [result-rel fr tr]]
   iter-map f t c]
  (when (= :influence result-rel)
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
           (filter :desc)
           (map :desc)
           (calc-influence)))))

(defn get-relation
  [graph from to relation-type]
  (->> (get-in graph [from to])
       (some #(when (= relation-type (:type %))
                %))))

(defn match-rule
  [restricted? from to iter-map rule]
  (let [[[_ f1 t1] [next-rel f2 t2] [result-rel fr tr]] rule
        {forward-graph :forward-graph
         reverse-graph :reverse-graph
         derivated-graph :derivated-graph} iter-map
        gpdf (partial get-passing-desc rule)
        has-relation? (fn [graph f t] (get-relation graph f t result-rel))
        add-relation (fn [acc f t c]
                       (let [grf (fn []
                                   (let [relation {:type result-rel}
                                         passing-desc (gpdf acc f t c)]
                                     (if passing-desc
                                       (assoc relation :desc passing-desc)
                                       relation)))]
                         (if (has-relation? forward-graph f t)
                           (if (has-relation? derivated-graph f t)
                             acc
                             (update-in acc [:derivated-graph f t] u/fnil-conj-set (grf)))
                           (if (restricted? f t c result-rel)
                             acc
                             (let [relation (grf)]
                               (-> acc
                                   (update-in [:forward-graph f t] u/fnil-conj-set relation)
                                   (update-in [:reverse-graph t f] u/fnil-conj-set relation)
                                   (update-in [:derivated-graph f t] u/fnil-conj-set relation)
                                   (update :derivated-relations conj [f t result-rel])))))))
        append (cond
                 (= f1 fr) #(add-relation %1 from %2 to)
                 (= f1 tr) #(add-relation %1 %2 from to)
                 (= t1 fr) #(add-relation %1 to %2 from)
                 (= t1 tr) #(add-relation %1 %2 to from))]
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
    (when (zero? (mod n 1000))
      (log/info (str "Derivation sub-step " n ", follow " (count follow-relations) " relations")))
    (if (empty? follow-relations)
      derivated-graph
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

(defn derivate-relationships
  [restricted? rules source-graph]
  ;; {:pre (every? drs/valid? rules)} ; already checked by rules_test/check-invariants-test
  (let [rules-map (make-rules-map rules)]
    (loop [graph source-graph
           derivated-graph {}
           n 1]
      (log/info (str "Derivation step " n))
      (let [next-derivated-graph (derivate-relationships-by-map restricted?
                                                                rules-map
                                                                graph
                                                                true)]
        (if (= derivated-graph next-derivated-graph)
          derivated-graph
          (recur (slv/merge-into graph next-derivated-graph)
                 (slv/merge-into derivated-graph next-derivated-graph)
                 (inc n)))))))
