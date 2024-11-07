(ns armate.archimate.viz.align.common
  (:require [armate.utils :as u]))

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
