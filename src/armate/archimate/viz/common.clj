(ns armate.archimate.viz.common
   (:require [clojure.string :as s]))

(defn- patch-tail
  [tail]
  (case tail
    "Workpackage" "WorkPackage"
    tail))

(defn- combine-fn-name
  [parts]
  (let [parts2 (map s/capitalize parts)]
    (if (= 1 (count parts))
      (str "Other_" (first parts2))
      (str (first parts2) "_" (patch-tail (s/join (rest parts2)))))))

(defn get-fn-name
  [kind]
  (-> kind
      (s/split #"-")
      (combine-fn-name)))
