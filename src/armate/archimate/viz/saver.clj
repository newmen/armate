(ns armate.archimate.viz.saver
  (:require [clojure.string :as s]
            [armate.archimate.builder :as abd]
            [armate.archimate.model :as model]
            [armate.archimate.viz.align :as align]
            [armate.archimate.viz.call-counter :as ccr]
            [armate.archimate.viz.combiner :as cmb]))

(defn align-elements
  [align? context]
  (if align?
    (align/append-hidden-aligns context)
    context))

(defn cut-file-name
  [file-path]
  (->> (s/replace file-path #"\.[^.]+?$" "")
       (re-find #"([^/]+)$")
       (last)))

(def save-opts
  "Flat rendering for the save path. Signalling :nesting / :connecting
   derivations are always suppressed; :render-derivable defaults to #{} so
   no derived edges render. Explicit empty :group-modes disables the default
   nesting so the saved output stays flat as before ticket 01."
  {:group-modes {}})

(defn generate-save-puml
  "Generate the PlantUML string that `save-puml` writes to `file-path`.
   Public for testability; the flat :group-modes opt keeps save output
   backward-compatible with the pre-ticket-01 flat rendering."
  [context file-path align?]
  (let [title (:title (model/start context))
        title (if (or (not title)
                      (s/starts-with? title abd/title-generated-at-prefix))
                (cut-file-name file-path)
                title)
        prepared (->> (model/set-start-title context title)
                      (align-elements align?)
                      (ccr/add-call-rates))]
    (cmb/on-fly-generate-puml prepared save-opts)))

(defn save-puml
  ([context file-path]
   (save-puml context file-path false))
  ([context file-path align?]
   (spit file-path (generate-save-puml context file-path align?))))

(defn add-suffix
  [file-path name-suffix]
  (let [parts (s/split file-path #"\b\.\b")
        head (drop-last parts)
        ext (last parts)]
    (str (apply str head) name-suffix "." ext)))
