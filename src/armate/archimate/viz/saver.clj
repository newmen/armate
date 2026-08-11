(ns armate.archimate.viz.saver
  (:require [clojure.string :as s]
            [armate.archimate.builder :as abd]
            [armate.archimate.viz.align.neighbours :as aln]
            [armate.archimate.viz.call-counter :as ccr]
            [armate.archimate.viz.combiner :as cmb]))

(defn align-elements
  [align? context]
  (if align?
    (aln/append-hidden-aligns context)
    context))

(defn cut-file-name
  [file-path]
  (->> (s/replace file-path #"\.[^.]+?$" "")
       (re-find #"([^/]+)$")
       (last)))

(defn save-puml
  ([context file-path]
   (save-puml context file-path false))
  ([context file-path align?]
   (->> (update-in context [:start :title]
                   (fn [title]
                     (if (or (not title)
                             (s/starts-with? title abd/title-generated-at-prefix))
                       (cut-file-name file-path)
                       title)))
        (align-elements align?)
        (ccr/add-call-rates)
        (cmb/on-fly-generate-puml)
        (spit file-path))))

(defn add-suffix
  [file-path name-suffix]
  (let [parts (s/split file-path #"\b\.\b")
        head (drop-last parts)
        ext (last parts)]
    (str (apply str head) name-suffix "." ext)))
