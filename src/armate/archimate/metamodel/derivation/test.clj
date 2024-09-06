(ns armate.derivation.test
  (:require [armate.derivation.rules :as rs]
            [armate.derivation.viz :as viz]))

(defn- select-rules
  [rules prefix rels]
  (->> rules
       (filter #(and (rels (first (first %)))
                     (rels (first (second %)))))
       (sort-by (partial mapv first))
       (map (partial vector prefix))))

(defn viz-between
  [& rels]
  (let [target (set rels)]
    (->> (concat (select-rules rs/certain-rules "certain" target)
                 (select-rules rs/potential-rules "potential" target))
         (viz/vizualize)
         (spit "deriviation.wsd"))))

(comment
  
  (viz-between :assignment :serving)

  (->> (viz/vizualize rs/certain-rules)
       (spit "deriviation.wsd"))
  
  )