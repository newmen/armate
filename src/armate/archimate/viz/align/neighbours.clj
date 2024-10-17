(ns armate.archimate.viz.align
  (:require [armate.archimate.multi-graph :as mg]
            [armate.utils :as u]))

(defn build-up-down-map
  [graph]
  (->> (mg/get-relationships graph)
       (reduce (fn [acc [from to relation]]
                 (let [[fu fd] (acc from [0 0])
                       [tu td] (acc to [0 0])]
                   (if (= :up (:direction relation))
                     (-> acc
                         (assoc from [(dec fu) fd])
                         (assoc to [tu (inc td)]))
                     (-> acc
                         (assoc from [fu (inc fd)])
                         (assoc to [(dec tu) td])))))
               {})))

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
   :application-component
   :application-collaboration
   :technology-artifact
   :technology-system-software
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
            (let [weight (kind-weights (:kind element) 1)]
              (update acc
                      (:alias element)
                      (fnil (partial mapv (partial + weight)) [0 0]))))
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

(def max-in-row 2)
(def too-many-rels 4)

(defn calc-hidden-groups
  [context up-down-map]
  (->> (vals (:relations context))
       (filter (comp (partial <= max-in-row) count))
       (sort-by (comp - count))
       (reduce (fn [acc group]
                 (let [items (->> (keys group)
                                  (remove (:processed acc)))]
                   (if (<= max-in-row (count items))
                     (let [matrix (get-align-matrix (count items))]
                       (-> acc
                           (update :processed into items)
                           (assoc :hidden
                                  (->> (sort-by up-down-map items)
                                       (align-items-with matrix)
                                       (get-hidden-pairs)
                                       (reduce (partial add-ud-hidden context)
                                               (:hidden acc))))))
                     acc)))
               {:hidden {}
                :processed #{}})
       :hidden))

(defn calc-hidden-sources
  [context up-down-map]
  (let [graph (:relations context)
        items (->> (keys graph)
                   (filter (fn [item]
                             (let [[u d] (up-down-map item)]
                               (> (- d u) too-many-rels)))))
        ;; matrix (get-align-pyramid (count items))
        matrix (repeat (count items) 1)]
    (->> (sort-by up-down-map items)
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
  (assoc context :hidden
         (calc-hiddens context)))
