(ns armate.archimate.derivation.core
  (:require [armate.archimate.derivation.match :as mch]
            [armate.archimate.derivation.rules :as drs]
            [armate.archimate.multi-graph :as mg]
            [armate.archimate.rules :as rls]
            [armate.utils :as u]))

(defn append-relations
  [derivate-kind context relations]
  (reduce (fn [acc [from to rel]]
            (let [from-kind (get-in context [:elements from :kind])
                  to-kind (get-in context [:elements to :kind])]
              (if (contains? (get-in rls/possible-relations [from-kind to-kind]) rel)
                (update-in acc
                           [:relations from to]
                           u/fnil-conj-set
                           {:type rel
                            :from from-kind :to to-kind
                            :derivate derivate-kind})
                acc)))
          context
          (mg/get-relationships relations)))

(defn derivate-relations
  [context]
  (reduce (fn [acc [derivate-kind rules]]
            (let [relations (:relations acc)
                  derivated-relations (mch/derivate-relationships rules relations)]
              (append-relations derivate-kind acc derivated-relations)))
          context
          [[:certain drs/certain-rules]
           [:potential drs/potential-rules]]))
