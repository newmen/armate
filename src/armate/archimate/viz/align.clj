(ns armate.archimate.viz.align
  (:require [armate.archimate.viz.align.neighbours :as neighbours]))

(defn append-hidden-aligns
  [context]
  (neighbours/append-hidden-aligns context))