(ns armate.archimate.metamodel.derivation.viz
  (:require [clojure.string :as s]))

(def ^:private suffixes (atom {}))

(defn- get-uniq-name
  [cmp]
  (let [suffix (cmp (swap! suffixes update cmp (fnil inc 0)))]
    (str (s/replace (name cmp) "-" "_") suffix)))

(defn- get-cmp-puml
  [cmp]
  (let [uniq-name (get-uniq-name cmp)]
    [[cmp uniq-name]
     (str "rectangle \"" (name cmp) "\" as " uniq-name "")]))

(defn- get-rel-puml
  [names-map rel x1 x2 kind]
  (str "Rel_" (s/capitalize (name rel))
       "(" (names-map x1) ", " (names-map x2) ", \"" (name rel) "\\n" (name kind) "\")"))

(defn- get-cmp-with-rels-puml
  [rel-fs]
  (let [names-with-cmps-puml (map get-cmp-puml (set (mapcat rest rel-fs)))
        names-map (into {} (mapcat drop-last names-with-cmps-puml))
        cmps-puml (map last names-with-cmps-puml)
        kinds (concat (take (dec (count rel-fs)) (repeat :original))
                      [:derived])]
    (str (s/join "\n" cmps-puml)
         "\n"
         (s/join "\n" (->> (map conj rel-fs kinds)
                           (map (partial apply get-rel-puml names-map)))))))

(defn get-group-puml
  [title rules]
  (if (empty? rules)
    ""
    (let [content (->> (map get-cmp-with-rels-puml rules)
                       (s/join "\n"))]
      (if title
        (str "Group(" (get-uniq-name :uniq-rules-group) ", " (name title) ") {\n"
             content
             "\n}")
        content))))

(defn vizualize
  [rules]
  (str "@startuml\n"
       "!include <archimate/Archimate>\n"
       (if (map? rules)
         (->> (map #(get-group-puml (first %) (second %)) rules)
              (s/join "\n"))
         (if (coll? rules)
           (get-group-puml nil rules)
           (throw (ex-info "rules should be a map or a collection" {}))))
       "\n@enduml"))
