(ns armate.derivation.viz
  (:require [clojure.string :as s]))

(def ^:private suffixes (atom {}))

(defn- get-uniq-name
  [cmp]
  (let [suffix (cmp (swap! suffixes update cmp (fnil inc 0)))]
    (str (name cmp) suffix)))

(defn- get-cmp-puml
  [cmp]
  (let [uniq-name (get-uniq-name cmp)]
    [[cmp uniq-name]
     (str "rectangle \"" (name cmp) "\" as " uniq-name "")]))

(defn- get-rel-puml
  [names-map rel x1 x2 kind]
  (str "Rel_" (s/capitalize (name rel))
       "(" (names-map x1) ", " (names-map x2) ", \"" (name rel) "\\n" kind "\")"))

(defn- get-cmp-with-rels-3-puml
  [prefix rel-fs]
  (let [names-with-cmps-puml (map get-cmp-puml (set (mapcat rest rel-fs)))
        names-map (into {} (mapcat drop-last names-with-cmps-puml))
        cmps-puml (map last names-with-cmps-puml)
        last-kind (str (if prefix (str prefix "\\n") "") "derived")
        kinds (concat (take (dec (count rel-fs)) (repeat "original"))
                      [last-kind])]
    (str (s/join "\n" cmps-puml)
         "\n"
         (s/join "\n" (->> (map conj rel-fs kinds)
                           (map (partial apply get-rel-puml names-map)))))))

(defn- get-cmp-with-rels-puml
  [rel-fs]
  (if (= 2 (count rel-fs))
    (get-cmp-with-rels-3-puml (first rel-fs) (second rel-fs))
    (get-cmp-with-rels-3-puml nil rel-fs)))

(defn vizualize
  [rules]
  (str "@startuml
!include <archimate/Archimate>
"
       (->> (map get-cmp-with-rels-puml rules)
            (s/join "\n"))
"
@enduml"))
