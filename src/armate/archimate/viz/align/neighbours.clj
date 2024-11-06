(ns armate.archimate.viz.align
  (:require [armate.archimate.multi-graph :as mg]
            [armate.utils :as u]))

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

(defn get-align-matrix
  [total]
  (let [row (int (Math/round (Math/sqrt (double total))))
        rest (- total (* row row))
        matrix (repeat row row)]
    (if (pos? rest)
      (map +
           matrix
           (concat (repeat rest 1) (repeat 0)))
      (let [rest2 (+ row rest)]
        (if (< rest2 (/ row 2))
          (map +
               (drop-last matrix)
               (concat (repeat rest2 1) (repeat 0)))
          matrix)))))

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

(defn add-ud-hidden
  [context acc [from to]]
  (let [gf #(get-in context [:elements % :kind])
        relation {:from (gf from)
                  :to (gf to)
                  :raw "-[hidden]->"}]
    (update-in acc [from to] u/fnil-conj-set relation)))

(def max-in-row 3)
(def too-many-rels 5)

(defn get-groups
  [context]
  (let [only-large (comp (partial < max-in-row) count)
        groups (concat (->> (vals (:elements context))
                            (filter :in)
                            (group-by :in)
                            (vals)
                            (filter only-large)
                            (map (partial map :alias)))
                       (->> (vals (:relations context))
                            (mapcat (fn [hm]
                                      (->> hm
                                           (mapcat (fn [[to rels]]
                                                     (map (partial vector to) rels)))
                                           (remove (comp (partial = :nesting)
                                                         :derivate
                                                         second))
                                           (group-by (comp :direction second))
                                           (vals)
                                           (filter only-large))))
                            (map (partial map first))
                            (map (partial map (fn [alias]
                                                (or (get-in context [:elements alias :in])
                                                    alias))))))]
    (set (map set groups))))

(defn calc-hidden-groups
  [context up-down-map]
  (->> (get-groups context)
       (sort-by (comp - count))
       (reduce (fn [acc aliases]
                 (let [matrix (get-align-matrix (count aliases))]
                   (->> (sort-by up-down-map aliases)
                        (align-items-with matrix)
                        (get-hidden-pairs)
                        (reduce (partial add-ud-hidden context) acc))))
               {})))

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
         (reduce (partial add-ud-hidden context)
                 {}))))

(defn calc-hiddens
  [context]
  (let [up-down-map (build-weight-map context)]
    (merge-with (partial merge-with into)
                (calc-hidden-groups context up-down-map)
                (calc-hidden-sources context up-down-map))))

(defn append-hidden-aligns
  [context]
  (->> (calc-hiddens context)
       (assoc context :hidden)))
