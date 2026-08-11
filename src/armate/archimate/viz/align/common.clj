(ns armate.archimate.viz.align.common
  (:require [armate.archimate.multi-graph :as mg]
            [armate.utils :as u]))

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

(defn add-ud-hidden
  [context acc [from to]]
  (let [gf #(get-in context [:elements % :kind])
        relation {:from (gf from)
                  :to (gf to)
                  :raw "-[hidden]->"}]
    (update-in acc [from to] u/fnil-conj-set relation)))

(defn split-by-dirs
  [to-rels]
  (->> to-rels
       (mapcat (fn [[to rels]]
                 (map (partial vector to) rels)))
       (remove (comp (partial = :nesting)
                     :derivate
                     second))
       (group-by (comp :direction second))))

(defn get-owner
  ([elements alias]
   (get-owner elements alias #{}))
  ([elements alias visited]
   (let [visited2 (conj visited alias)
         element (elements alias)
         owner (:in element)]
     (if (and owner
              (not (visited2 owner)))
       (get-owner elements owner visited2)
       alias))))

(defn generalize-relations
  [context]
  (let [elements (:elements context)
        graph (:relations context)]
    (->> (mg/get-relationship-sets graph)
         (reduce (fn [acc [from to rels]]
                   (let [[f2 t2] (->> [from to]
                                      (mapv (partial get-owner elements)))]
                     (assoc-in acc [f2 t2] rels)))
                 graph))))

(defn get-groups
  [max-in-row context]
  (->> (vals (:elements context))
       (filter :in)
       (group-by :in)
       (vals)
       (filter (comp (partial < max-in-row) count))
       (map (partial map :alias))
       (map set)
       (set)))
