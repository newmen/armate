(ns armate.archimate.viz.align.neighbours
  (:require [armate.archimate.metamodel.solver :as slv]
            [armate.archimate.multi-graph :as mg]
            [armate.archimate.viz.align.common :as cmn]
            [armate.archimate.viz.align.grid :as grid]
            [armate.utils :as u]))

(def allow-align-twice?
  false)

(def groups-as-columns?
  false)

(defn build-up-down-map
  [graph]
  (let [data (->> (mg/get-relationships graph)
                  (reduce (fn [acc [from to relation]]
                            (if (= :nesting (:derivate relation))
                              (update acc :nesting conj [from to])
                              (let [[fu fd] (get-in acc [:udmap from] [0 0])
                                    [tu td] (get-in acc [:udmap to] [0 0])]
                                (if (= :up (:direction relation))
                                  (-> acc
                                      (assoc-in [:udmap from] [(dec fu) fd])
                                      (assoc-in [:udmap to] [tu (inc td)]))
                                  (-> acc
                                      (assoc-in [:udmap from] [fu (inc fd)])
                                      (assoc-in [:udmap to] [(dec tu) td]))))))
                          {:udmap {}
                           :nesting []}))]
    (reduce (fn [acc [from to]]
              (let [[fu fd] (acc from [0 0])
                    [tu td] (acc to [0 0])]
                (assoc acc from [(+ fu tu) (- fd td)])))
            (:udmap data)
            (:nesting data))))

(def element-kinds-order
  [:business-actor
   :business-role
   :business-interaction
   :business-product
   :business-service
   :business-event
   :business-function
   :business-process
   :business-collaboration
   :application-service
   :application-data-object
   :application-interface
   :technology-system-software
   :application-component
   :application-collaboration
   :technology-artifact
   :technology-node
   :technology-collaboration
   :technology-path
   :technology-interaction])

(def weight-step 5)

(def kind-weights
  (->> (range)
       (map (partial * weight-step))
       (map inc)
       (zipmap element-kinds-order)))

(defn build-weight-map
  [context]
  (reduce (fn [acc element]
            (let [weight (kind-weights (:kind element) 1)
                  alias (:alias element)]
              (update acc
                      alias
                      (fn [base]
                        (if base
                          (let [[u d] base]
                            [(+ u weight) (+ d weight) alias])
                          [weight weight alias])))))
          (build-up-down-map (:relations context))
          (vals (:elements context))))

(defn get-align-pyramid
  [total]
  (loop [remaining total
         current-size 1
         result []]
    (if (<= remaining 0)
      (sort-by - result)
      (let [next-size (min current-size remaining)]
        (recur (- remaining next-size)
               (inc current-size)
               (conj result next-size))))))

(defn align-items-with
  [matrix items]
  (when (seq items)
    (let [mn (first matrix)]
      (loop [matrix matrix
             items items
             result []]
        (if (empty? matrix)
          (u/transpose result)
          (let [n (first matrix)
                row (take n items)
                delta (- mn (count row))
                row2 (if (pos? delta)
                       (concat row (repeat delta (last row)))
                       row)]
            (recur (rest matrix)
                   (drop n items)
                   (conj result row2))))))))

(defn get-hidden-pairs
  [separation]
  (->> (mapcat (partial partition 2 1) separation)
       (map vec)
       (into (sorted-set))))

(def max-in-row 3)
(def too-many-rels 5)

(defn get-many-nbrs
  [context]
  (->> (vals (:relations context))
       (mapcat (fn [to-rels]
                 (->> (cmn/split-by-dirs to-rels)
                      (vals)
                      (filter (comp (partial < max-in-row) count)))))
       (map (partial map first))
       (map (partial map (partial cmn/get-owner (:elements context))))
       (map set)
       (set)))

(defn calc-hidden-groups
  [context up-down-map]
  (let [general-grels (cmn/generalize-relations context)
        group-matrix (when groups-as-columns?
                       (->> (cmn/get-groups 2 context)
                            (map (fn [group]
                                   (->> (grid/split-into-layers general-grels group)
                                        (mapcat (partial sort-by up-down-map))
                                        (vector))))))
        many-matrix (->> (get-many-nbrs context)
                         (mapcat (partial grid/split-into-layers general-grels))
                         (map (fn [aliases]
                                (let [matrix (cmn/get-align-matrix (count aliases))]
                                  (->> (sort-by up-down-map aliases)
                                       (align-items-with matrix))))))]
    (:hidden
     (reduce (fn [acc columns]
               (let [acf #(->> (get-hidden-pairs %)
                               (reduce (partial cmn/add-ud-hidden context)
                                       (:hidden acc)))]
                 (if allow-align-twice?
                   (assoc acc :hidden (acf columns))
                   (let [clmn2 (->> columns
                                    (map (partial remove (:visited acc)))
                                    (filter (comp (partial < 1) count)))]
                     (if (empty? clmn2)
                       acc
                       (-> acc
                           (assoc :hidden (acf clmn2))
                           (update :visited into (apply concat clmn2))))))))
             {:hidden {}
              :visited #{}}
             (concat group-matrix many-matrix)))))

(defn calc-hidden-sources
  [context up-down-map]
  (let [graph (:relations context)
        aliases (->> (keys graph)
                     (filter (fn [alias]
                               (let [[u d _] (up-down-map alias)]
                                 (> (- d u) too-many-rels)))))
        ;; matrix (get-align-pyramid (count aliases))
        matrix (repeat (count aliases) 1)]
    (->> (sort-by up-down-map aliases)
         (reverse)
         (align-items-with matrix)
         (get-hidden-pairs)
         (reduce (partial cmn/add-ud-hidden context)
                 {}))))

(defn calc-hiddens
  [context]
  (let [up-down-map (build-weight-map context)]
    (slv/merge-into (calc-hidden-groups context up-down-map)
                    (calc-hidden-sources context up-down-map))))

(defn append-hidden-aligns
  [context]
  (->> (calc-hiddens context)
       (assoc context :hidden)))
