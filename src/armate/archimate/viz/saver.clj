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

(defn generate-save-puml
  "Generate the PlantUML string that `save-puml` writes to `file-path`.
   Public for testability; the flat :group-modes opt keeps save output
   backward-compatible with the pre-ticket-01 flat rendering."
  ([context file-path align?]
   (generate-save-puml context file-path align? nil))
  ([context file-path align? save-opts]
   (let [title (:title (model/start context))
         title (if (or (not title)
                       (s/starts-with? title abd/title-generated-at-prefix))
                 (cut-file-name file-path)
                 title)
         prepared (->> (model/set-start-title context title)
                       (align-elements align?)
                       (ccr/add-call-rates))]
     (cmb/on-fly-generate-puml prepared save-opts))))

(defn save-puml
  ([context file-path]
   (save-puml context file-path false))
  ([context file-path align?]
   (save-puml context file-path align? nil))
  ([context file-path align? save-opts]
   (spit file-path (generate-save-puml context file-path align? save-opts))))

(defn add-suffix
  [file-path name-suffix]
  (let [parts (s/split file-path #"\b\.\b")
        head (drop-last parts)
        ext (last parts)]
    (str (apply str head) name-suffix "." ext)))
