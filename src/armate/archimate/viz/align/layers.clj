(ns armate.archimate.viz.align.layers
  (:require [clojure.set :as o]
            [clojure.math.combinatorics :as combo]
            [armate.archimate.multi-graph :as mg]
            [armate.archimate.viz.align.common :as cmn]
            [armate.archimate.viz.align.grid :as grid]
            [armate.utils :as u]))

(def split-groups? false)
(def max-in-row 3)
(def too-many-rels 5)

(defn split-by-dirs
  [to-rels]
  (->> to-rels
       (mapcat (fn [[to rels]]
                 (map (partial vector to) rels)))
       (remove (comp (partial = :nesting)
                     :derivate
                     second))
       (group-by (comp :direction second))))

(defn calc-groups
  [groups]
  (->> (map (juxt first (comp count second)) groups)
       (into {})))

(defn get-uds-map
  [graph]
  (->> (map (juxt first (comp calc-groups split-by-dirs second)) graph)
       (into {})))

(defn get-grid
  [context]
  (let [elements (:elements context)
        aliases (->> (vals elements)
                     (remove :in)
                     (map :alias)
                     (set))
        graph (->> (mg/get-relationship-sets (:relations context))
                   (reduce (fn [acc [from to rels]]
                             (let [[f2 t2] (->> [from to]
                                                (mapv (partial cmn/get-owner elements)))]
                               (assoc-in acc [f2 t2] rels)))
                           {}))]
    (grid/split-into-layers graph aliases)))

(defn get-many-rels-elements
  [context uds-map]
  (->> uds-map
       (filter (fn [[_ uds]]
                 (->> (vals uds)
                      (some (partial < too-many-rels)))))
       (map first)
       (map (partial cmn/get-owner (:elements context)))
       (set)))

(defn split-group
  [udf group]
  (let [sg (sort-by (fn [alias]
                      (conj (udf alias) alias))
                    group)]
    (loop [row sg
           takes (cmn/get-align-matrix (count group))
           parts []]
      (if (empty? row)
        parts
        (let [t (first takes)]
          (recur (drop t row)
                 (rest takes)
                 (conj parts (take t row))))))))

(defn get-layers
  [context uds-map udf]
  (let [attractors (get-many-rels-elements context uds-map)]
    (->> (get-grid context)
         (mapcat (fn [group]
                   (let [intersect (o/intersection group attractors)]
                     (if (empty? intersect)
                       (list group)
                       (let [cut (o/difference group intersect)
                             sum-uds (->> (mapv udf cut)
                                          (u/transpose)
                                          (mapv (partial apply +)))]
                         (->> (map (juxt hash-set udf) intersect)
                              (cons [cut sum-uds])
                              (sort-by (fn [[sg uds]]
                                         (conj uds (- (count sg)))))
                              (map first)))))))
         (mapcat (fn [group]
                   (if (< max-in-row (count group))
                     (split-group udf group)
                     (list group)))))))

(defn transform-layers-into-pairs
  [layers]
  (->> (partition 2 1 layers)
       (mapcat (partial apply combo/cartesian-product))))

(defn get-groups-pairs
  [context udf]
  (if split-groups?
    (->> (cmn/get-groups max-in-row context)
         (mapcat (fn [group]
                   (->> (split-group udf group)
                        (transform-layers-into-pairs)))))
    []))

(defn get-hidden-pairs
  [context]
  (let [uds-map (get-uds-map (:relations context))
        g0f #(fn [ud] (% ud 0))
        udf (comp (juxt (comp - (g0f :up)) (g0f :down))
                  #(uds-map % {:up 0 :down 0}))]
    (->> (get-layers context uds-map udf)
         (transform-layers-into-pairs)
         (concat (get-groups-pairs context udf)))))

(defn calc-hiddens
  [context]
  (reduce (partial cmn/add-ud-hidden context)
          {}
          (get-hidden-pairs context)))

(defn append-hidden-aligns
  [context]
  (->> (calc-hiddens context)
       (assoc context :hidden)))
