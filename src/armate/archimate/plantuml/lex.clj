(ns armate.archimate.plantuml.lex
  (:require [clojure.string :as s]))

(def call-re
  #"^([A-Za-z_]+)\s*\(([A-Za-z0-9_]+)\s*,\s*([A-Za-z0-9_]+)(?:\s*,\s*(.+?))?\)$")

(def full-line-re
  #"^([A-Za-z_]+)\s*\(([A-Za-z0-9_]+)\s*,\s*(\"[^\"]+\"|[^\"\s]+)(?:\s*,\s*(.+?))?\)\s*([^\s]+)?\s*(\{)?$")

(def quoted-split-re
  #"(?:\"[^\"]+\"|[^\"\s]+)")

(defn quoted-brackets-split
  [line]
  (if-let [full-match (re-matches full-line-re line)]
    (remove nil? (rest full-match))
    (re-seq quoted-split-re line)))

(defn get-parts
  [line]
  (let [parts (quoted-brackets-split line)]
    (if (= (last parts) "{")
      {:parts (vec (drop-last parts))
       :block? true}
      (let [lp (last parts)]
        (if (s/ends-with? lp "{")
          (let [cut-last (subs lp 0 (dec (count lp)))]
            {:parts (conj (vec (drop-last parts)) cut-last)
             :block? true})
          (if-let [matches (re-matches call-re line)]
            {:parts (vec (rest matches))
             :block? false}
            {:parts parts
             :block? false}))))))

(defn cut1
  [string]
  (subs string 1 (dec (count string))))

(defn cut2
  [string]
  (cut1 (cut1 string)))

(defn wrapped?
  [start end part]
  (when part
    (and (s/starts-with? part start)
         (s/ends-with? part end))))

(def fur?
  (partial wrapped? "<<" ">>"))

(def fur-re
  #"(<<[^>]+>>|[^<>]+)")

(defn cut-furs
  [part]
  (->> (re-seq fur-re part)
       (map second)
       (map #(if (fur? %) (cut2 %) %))))

(def quoted?
  (partial wrapped? "\"" "\""))

(defn cut-quotes
  [part]
  (if (quoted? part)
    (cut1 part)
    part))

(defn variable?
  [line]
  (and (s/starts-with? line "!")
       (not (s/starts-with? line "!include"))
       (s/includes? line "=")))

(def variable-re
  #"\!(.+?)\s*=\s*(.+)")

(defn parse-variable
  [line]
  (when (variable? line)
    (when-let [matches (re-matches variable-re line)]
      [(second matches) (cut-quotes (last matches))])))

(defn mask-specials
  [name]
  (reduce (fn [acc c]
            (s/replace acc (str c) (str "\\" c)))
          name
          [\^ \. \+ \* \? \[ \] \( \) \{ \} \- \$]))

(defn mask-variable
  [name]
  (let [checks [[(partial re-seq #"^[A-Za-z0-9]+")
                 #(str "\\b" %)]
                [(partial re-seq #"[A-Za-z0-9]+$")
                 #(str % "\\b")]]]
    (reduce (fn [acc [check replacement]]
              (if (check name) (replacement acc) acc))
            (mask-specials name)
            checks)))

(defn apply-variables
  [variables line]
  (reduce-kv (fn [full k v]
               (let [re (re-pattern (mask-variable k))]
                 (s/replace full re v)))
             line
             variables))