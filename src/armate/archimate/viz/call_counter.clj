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
  (str (u/round 2 (* (/ n max-calls-n) 100)) "%"))

(defn rate-relation
  [counters max-calls-n fa ta relation]
  (let [rt (:type relation)]
    (if-let [n (get-in counters [rt fa ta])]
      (-> relation
          (assoc :rate (/ n max-calls-n))
          (update :desc
                  #(if (nil? %1) %2 (str %1 "\\n" %2))
                  (get-percent max-calls-n n)))
      relation)))

(defn rate-relations
  [counters max-calls-n graph]
  (->> (mg/get-relationships graph)
       (reduce (fn [acc [from to rel]]
                 (let [rel2 (rate-relation counters max-calls-n from to rel)]
                   (update-in acc [from to] u/fnil-conj-set rel2)))
               {})))

(defn rerate-buckets
  [buckets]
  (if (= 1 (count buckets))
    (let [fb (first buckets)]
      [(:tn fb) (:calls fb)])
    (let [totals (map :tn buckets)
          max-calls-n (apply + totals)]
      [max-calls-n
       (reduce
        (fn [acc bucket]
          (let [tn (:tn bucket)
                x (/ tn (- max-calls-n tn))]
            (reduce-kv
             (fn [a1 rel-type ftc]
               (reduce-kv
                (fn [a2 fa tc]
                  (reduce-kv
                   (fn [a3 ta c]
                     (update-in a3 [rel-type fa ta] (fnil + 0)
                                (Math/round (float (+ c (/ c x))))))
                   a2
                   tc))
                a1
                ftc))
             acc
             (:calls bucket))))
        {}
        buckets)])))

(defn rate-calls
  [context]
  (let [buckets (vals (get-in context [:misc :counters :buckets]))]
    (when (< 1 (apply + (map :tn buckets)))
      (let [[tn counters] (rerate-buckets buckets)]
        (update context :relations (partial rate-relations counters tn))))))

(defn add-call-rates
  [context]
  (or (rate-calls context)
      context))
