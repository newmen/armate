(ns armate.core
  (:require [armate.archimate.core :as acr]))

(defn lint-file
  [file-path]
  (:lints (acr/analyze-file file-path)))
