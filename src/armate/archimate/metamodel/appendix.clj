(ns armate.archimate.metamodel.appendix
  (:require [armate.archimate.metamodel.derivation.match :as mch]
            [armate.archimate.metamodel.derivation.restrictions :as rtr]
            [armate.archimate.metamodel.derivation.rules :as drs]
            [armate.archimate.metamodel.meta :as mt]
            [armate.archimate.metamodel.solver :as slv]
            [armate.utils :as u]))

(defn- derivate-relationships
  [general-relationships-mg rules]
  (mch/derivate-relationships rtr/restricted?
                              rules
                              general-relationships-mg))

(def derivated-relationships
  (let [rel-or-con? (fn [v] (or (mt/relationship? v) (mt/connector? v)))
        grs-mg (slv/rel-rules-to-mg mt/general-relationships rel-or-con?)
        certain-mg (derivate-relationships grs-mg drs/certain-rules)
        cgs-mg (slv/merge-into grs-mg certain-mg)
        potential-mg (derivate-relationships cgs-mg drs/potential-rules)]
    (slv/mg-to-rel-rules (slv/merge-into certain-mg potential-mg))))

(def ^:private almost-total-relationships
  (slv/merge-into mt/general-relationships derivated-relationships))

(defn- get-element-relationships
  [relationships]
  (->> (mt/hierarchy :element)
       (select-keys relationships)))

(defn- relations-reducer
  [acc [e rs]]
  (if (mt/element? e)
    (update acc e u/fnil-into-set rs)
    acc))

(defn- get-incoming-relationships
  [relationships]
  (->> (get-element-relationships relationships)
       (vals)
       (apply concat)
       (reduce relations-reducer {})))

(defn- get-outgoing-relationships
  [relationships]
  (->> (get-element-relationships relationships)
       (map (juxt first (comp set (partial apply concat) vals second)))
       (into {})))

(defn- get-outgoing-to-grouping-relationships
  [relationships]
  (->> (get-outgoing-relationships relationships)
       (map (juxt first (comp (partial hash-map :grouping) second)))
       (into {})))

(def total-relationships
  (-> (get-outgoing-to-grouping-relationships almost-total-relationships)
      (slv/merge-into almost-total-relationships)
      (update :grouping u/fnil-into-set
              (get-incoming-relationships almost-total-relationships))
      (update-in [:grouping :grouping] u/fnil-into-set (mt/hierarchy :relationship))))

(def incoming-relationships
  (get-incoming-relationships total-relationships))

(def outgoing-relationships
  (get-outgoing-relationships total-relationships))

(comment

  (:motivation-driver incoming-relationships)
  (:business-actor incoming-relationships)
  (:location outgoing-relationships)

  (->> (get-in total-relationships [:motivation-driver])
       (filter (comp :realization second))
       (into {}))
  
  (count (mt/hierarchy :concept))
  (count (mt/hierarchy :element))
  (count (mt/hierarchy :relationship))

  )
