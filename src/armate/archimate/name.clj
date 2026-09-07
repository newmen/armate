(ns armate.archimate.name
  "The single home for the title -> name -> alias rules.

  Every caller of name handling (the archi intake, the .puml intake and the builder) crossed
  the model seam in task 01; this module concentrates the string rules they used to
  hand-roll, so hyphen/slash/camel/length behaviour lives in one place.
  "
  (:require [clojure.string :as s]
            [armate.transliteration :as tl]
            [armate.utils :as u]))

(def max-alias-length 28)
(def title-max-length 12)

;; ---------------------------------------------------------------------------
;; Normalization: title -> name (moved from armate.archimate.archi.title)

(def cyrillic-re #"[а-яё]")
(def vowels-re #"[аеёиоуыэюя]")
(def consonants-re #"[бвгджзклмнпрстфхцчшщ]")

(defn cyrillic-letter?
  [ch]
  (re-matches cyrillic-re (str ch)))

(defn vowel?
  [ch]
  (re-matches vowels-re (str ch)))

(defn consonant?
  [ch]
  (re-matches consonants-re (str ch)))

(def compound-word-hyphens
  #{"бизнес" "интернет" "онлайн" "евро" "гос" "процесс"})

(defn- mergeable-pair?
  [ch-before ch-after]
  (or (and (vowel? ch-before) (consonant? ch-after))
      (and (vowel? ch-before) (vowel? ch-after))
      (and (consonant? ch-before) (consonant? ch-after))))

(defn- mergeable-hyphen?
  [string hyphen-idx]
  (let [left (subs string 0 hyphen-idx)
        right (subs string (inc hyphen-idx))
        left-word (last (s/split left #"-"))
        right-word (first (s/split right #"-"))
        last-ch (last left-word)
        first-ch (first right-word)]
    (and (cyrillic-letter? last-ch)
         (cyrillic-letter? first-ch)
         (or (vowel? last-ch) (consonant? last-ch))
         (or (vowel? first-ch) (consonant? first-ch))
         (<= 2 (count left-word))
         (<= 2 (count right-word))
         (not (compound-word-hyphens left-word))
         (not (compound-word-hyphens right-word))
         (mergeable-pair? last-ch first-ch))))

(defn remove-wrap-hyphens
  [string]
  (loop [s string
         acc-start 0
         out []]
    (if-let [i (s/index-of s "-")]
      (let [before (subs s 0 i)
            rest (subs s (inc i))]
        (if (mergeable-hyphen? string (+ acc-start i))
          (recur rest (+ acc-start i 1) (conj out before))
          (recur rest (+ acc-start i 1) (conj out (str before "-")))))
      (s/join (conj out s)))))

(defn- uppercase-latin-word?
  [word]
  (re-matches #"[A-Z]+" word))

(defn- collapse-slash-spaces
  [string]
  (-> string
      ;; "/" with spaces on both sides is left untouched
      (s/replace #" +/ +" (fn [_] "\u0001"))
      ;; collapse spaces after "/"
      (s/replace #"/ +" "/")
      ;; collapse spaces before "/", except when preceded by an uppercase latin word
      (s/replace #"([A-Za-z0-9]+) +/"
                 (fn [[_ word]]
                   (if (uppercase-latin-word? word)
                     (str word " /")
                     (str word "/"))))
      ;; restore untouched both-sides slashes
      (s/replace "\u0001" " / ")))

(defn normalize-name
  "Title -> name: cyrillic wrap-hyphen merge, underscore/slash spacing."
  [string]
  (-> string
      (remove-wrap-hyphens)
      (s/replace #"_ | _" "_")
      (collapse-slash-spaces)))

(def normalize-title
  "Legacy alias of `normalize-name` (was armate.archimate.archi.title/normalize-title)."
  normalize-name)

;; ---------------------------------------------------------------------------
;; Alias build: name -> alias (moved from armate.archimate.builder)

(defn escape-special-chars
  "Strip double/single quotes from a raw title."
  [raw-name]
  (s/replace raw-name #"[\"']" ""))

(defn patch-alias
  "Rewrite a raw name into an alias-safe string."
  [raw-name]
  (-> raw-name
      (escape-special-chars)
      (s/replace #"=|-|~|:|#|&|%|\$|\+|\*|\s|\(|\)|\[|\]|\{|\}|\?" "_")
      (s/replace #"\.|," "__")
      (s/replace #"/" "___")))

(defn length-cap
  ([raw-name]
   (length-cap max-alias-length raw-name))
  ([max-length raw-name]
   (if (> (count raw-name) max-length)
     (subs raw-name 0 max-length)
     raw-name)))

(defn alias-name
  "Name -> alias: lower-case, length-cap, transliterate, patch."
  [raw-name]
  (-> (s/lower-case raw-name)
      (length-cap)
      (tl/transliterate)
      (patch-alias)))

(def alias-title
  "Legacy alias of `alias-name` (was armate.archimate.builder/alias-title)."
  alias-name)

(def patch-raw-name
  "Legacy alias of `patch-alias` (was armate.archimate.builder/patch-raw-name)."
  patch-alias)

(def cut-too-long
  "Legacy alias of `length-cap` (was armate.archimate.builder/cut-too-long)."
  length-cap)

;; ---------------------------------------------------------------------------
;; Name flattening, .puml intake (moved from armate.archimate.plantuml.parser)

(defn strait-name
  "Collapse runs of whitespace / literal \\n to a single space."
  [title]
  (s/replace title #"(?s)(\s|\\n)+" " "))

(def strait-string
  "Legacy alias of `strait-name` (was armate.archimate.plantuml.parser/strait-string)."
  strait-name)

;; ---------------------------------------------------------------------------
;; Splitting / length (moved from armate.archimate.builder)

(defn title-separate
  ([string]
   (title-separate title-max-length string))
  ([max-length string]
   (->> (s/trim string)
        (re-seq #"\\n|[\/\#\[\(]?[A-Za-z0-9А-Яа-яЁё_\-]+[,:\]\)]?|\/?\{\w+\}|\s*[\=\+\*]\s*|\s*[\s\.~\?]")
        (mapcat (fn [part]
                  (if (< max-length (count part))
                    (re-seq #"[\/\#\[\(]?[A-Za-z0-9А-Яа-яЁё,]+[\]\)]?|[:_-]" part)
                    [part]))))))

(defn- title-split-long-camel-part
  [string]
  (let [first-re #"^[\/#]?[a-zа-я0-9ё]*"
        first-word (re-find first-re string)
        tail-words (re-seq #"[A-ZА-ЯЁ][a-zа-я0-9ё]+"
                           (s/replace-first string first-re ""))]
    (if (empty? first-word)
      tail-words
      (cons first-word tail-words))))

(defn- title-split-long-part
  [string]
  (let [camel-parts (title-split-long-camel-part string)]
    (if (seq camel-parts)
      camel-parts
      [string])))

(defn- title-sqrt-length
  [string]
  (int (Math/ceil (* 2 (Math/sqrt (count string))))))

(defn lex-name
  "Split a name into display lines (\\n-joined), applying the length/camel rules."
  [string]
  (let [mlh (max (title-sqrt-length string) title-max-length)]
    (loop [acc []
           parts (title-separate mlh string)]
      (if (empty? parts)
        (->> (map s/trim acc)
             (remove empty?)
             (map (fn [part]
                    (if (s/starts-with? part "#")
                      (str " " part)
                      part)))
             (s/join "\\n"))
        (let [part (first parts)
              tail (rest parts)
              prev (last acc)
              curr-length (count prev)
              part-length (count part)
              dsub-parts (delay (title-split-long-part part))]
          (if (= "\\n" part)
            (recur (conj acc "") tail)
            (if (> (+ curr-length part-length) mlh)
              (if (and (> part-length mlh)
                       (< 1 (count @dsub-parts)))
                (if (>= 2 curr-length)
                  (recur (u/replace-last acc (str prev (first @dsub-parts)))
                         (concat (rest @dsub-parts) tail))
                  (recur acc (concat @dsub-parts tail)))
                (recur (conj acc part) tail))
              (recur (u/replace-last acc (str prev part))
                     tail))))))))

(def subsplit
  "Legacy alias of `lex-name` (was armate.archimate.builder/subsplit)."
  lex-name)