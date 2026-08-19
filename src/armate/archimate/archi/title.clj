(ns armate.archimate.archi.title
  (:require [clojure.string :as s]))

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

(defn mergeable-pair?
  [ch-before ch-after]
  (or (and (vowel? ch-before) (consonant? ch-after))
      (and (vowel? ch-before) (vowel? ch-after))
      (and (consonant? ch-before) (consonant? ch-after))))

(defn mergeable-hyphen?
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

(defn normalize-title
  [string]
  (-> string
      (remove-wrap-hyphens)
      (s/replace #"_ | _" "_")
      (s/replace #"/ | /" "/")))