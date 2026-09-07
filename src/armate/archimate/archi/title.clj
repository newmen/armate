(ns armate.archimate.archi.title
  "Thin re-exporting adapter over `armate.archimate.name`.

  Kept for caller compatibility; normalization rules now live once in the name module."
  (:require [armate.archimate.name :as name]))

(defn normalize-title
  "Title -> name; delegates to `name/normalize-name`."
  [string]
  (name/normalize-name string))

(defn remove-wrap-hyphens
  "Wrap-hyphen merge; delegates to `name/remove-wrap-hyphens`."
  [string]
  (name/remove-wrap-hyphens string))