(ns armate.transliteration
  (:require [clojure.string :as s]))

(def mapping
  (let [lower-map {"а" "a"
                   "б" "b"
                   "в" "v"
                   "г" "g"
                   "д" "d"
                   "е" "e"
                   "ё" "e"
                   "ж" "zh"
                   "з" "z"
                   "и" "i"
                   "й" "i"
                   "к" "k"
                   "л" "l"
                   "м" "m"
                   "н" "n"
                   "о" "o"
                   "п" "p"
                   "р" "r"
                   "с" "s"
                   "т" "t"
                   "у" "u"
                   "ф" "f"
                   "х" "h"
                   "ц" "c"
                   "ч" "ch"
                   "ш" "sh"
                   "щ" "sch"
                   "ъ" ""
                   "ы" "y"
                   "ь" ""
                   "э" "e"
                   "ю" "yu"
                   "я" "ya"}]
    (->> lower-map
         (map (juxt (comp s/upper-case first)
                    (comp s/upper-case second)))
         (into {})
         (merge lower-map))))

(defn transliterate
  [text]
  (->> text
       (map (fn [char]
              (mapping (str char) char)))
       (s/join "")))
