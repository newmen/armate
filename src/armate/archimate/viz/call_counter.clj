(ns armate.archimate.viz.call-counter
  (:require [armate.archimate.multi-graph :as mg]
            [armate.utils :as u]))

(defn get-max-number
  [counters]
  (->> (vals counters) ; rel-type
       (mapcat vals) ; from
       (mapcat vals) ; to
       (apply max))) ; counter

(defn get-percent
  [max-calls-n n]
  (str (/ (Math/round (* 10000 (float (/ n max-calls-n)))) 100.0) "%"))

(defn rate-relation
  [counters max-calls-n fa ta relation]
  (let [rt (:type relation)]
    (if-let [n (get-in counters [rt fa ta])]
      (assoc relation :desc (get-percent max-calls-n n))
      relation)))

(defn rate-relations
  [counters max-calls-n graph]
  (->> (mg/get-relationships graph)
       (reduce (fn [acc [from to rel]]
                 (let [rel2 (rate-relation counters max-calls-n from to rel)]
                   (update-in acc [from to] u/fnil-conj-set rel2)))
               {})))

(defn rate-calls
  [context]
  (when-let [tn (get-in context [:misc :counters :counter])]
    (when (< 1 tn)
      (when-let [counters (get-in context [:misc :counters :calls])]
        (let [max-calls-n (get-max-number counters)]
          (update context :relations (partial rate-relations counters max-calls-n)))))))

(defn add-call-rates
  [context]
  (or (rate-calls context)
      context))
