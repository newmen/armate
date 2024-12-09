(ns armate.archimate.metamodel.derivation.test
  (:require [armate.archimate.metamodel.derivation.rules :as rs]
            [armate.archimate.metamodel.derivation.viz :as viz]))

(defn- select-rules
  [rules rels]
  (->> rules
       (filter #(and (rels (first (first %)))
                     (rels (first (second %)))))
       (sort-by (partial mapv first))))

(defn viz-between
  [& rels]
  (let [target (set rels)]
    (->> (viz/vizualize {:certain (select-rules rs/certain-rules target)
                         :potential (select-rules rs/potential-rules target)})
         (spit "deriviation.wsd"))))

(comment

  (select-rules rs/certain-rules #{:serving :assignment :realization :flow})

  (viz-between :access_w :assignment)

  (->> (viz/vizualize rs/certain-rules)
       (spit "deriviation.wsd"))

  )
