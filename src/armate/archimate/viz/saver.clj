(ns armate.archimate.viz.saver
  (:require [armate.archimate.viz.align.neighbours :as aln]
            [armate.archimate.viz.call-counter :as ccr]
            [armate.archimate.viz.combiner :as cmb]))

(def align-elements
  aln/append-hidden-aligns)

(defn save-puml
  [context file-path]
  (->> (align-elements context)
       (ccr/add-call-rates)
       (cmb/on-fly-generate-puml)
       (spit file-path)))
