(ns armate.utils
  (:require [clojure.set :as o]))

(def fnil-into-set
  (fnil into #{}))

(def fnil-conj-set
  (fnil conj #{}))

(def fnil-union-set
  (fnil o/union #{}))

(defn dissoc-if-nil
  [hm & ks]
  (apply dissoc hm
         (filter (comp nil? hm) ks)))

(defn dissoc-nils
  [hm]
  (reduce-kv (fn [acc k v]
               (if (nil? v)
                 (dissoc acc k)
                 acc))
             hm hm))

(defn assoc-if-not-nil
  [hm k v]
  (if (nil? v)
    hm
    (assoc hm k v)))

(defn update-if-not-nil
  [hm k f & args]
  (if (nil? (hm k))
    hm
    (apply update hm k f args)))

(defn replace-last
  [v value]
  (if (empty? v)
    [value]
    (conj (subvec v 0 (dec (count v)))
          value)))

(defn transpose
  [matrix]
  (apply mapv vector matrix))

(defn- lazy-distinct-by
  [f coll seen]
  (when (seq coll)
    (let [tail (rest coll)
          x (first coll)
          y (f x)]
      (if (contains? seen y)
        (lazy-distinct-by f tail seen)
        (cons x (lazy-seq (lazy-distinct-by f tail (conj seen y))))))))

(defn distinct-by
  [f coll]
  (lazy-distinct-by f coll #{}))

(defn make-keyword-keys
  [obj]
  (when obj
    (if (map? obj)
      (->> obj
           (map (juxt (comp keyword first)
                      (comp make-keyword-keys second)))
           (into {}))
      (if (vector? obj)
        (mapv make-keyword-keys obj)
        (if (set? obj)
          (set (map make-keyword-keys obj))
          (if (list? obj)
            (map make-keyword-keys obj)
            obj))))))

(defn make-int-or-float
  [number]
  (let [inum (int number)]
    (if (zero? (- number inum))
      inum
      (float number))))

(defn round
  "Round a double to the given precision (number of significant digits)"
  [precision number]
  (let [factor (Math/pow 10 precision)]
    (/ (Math/round (* number factor)) factor)))

(defn persentiles
  [ps data]
  {:pre [(every? (comp not neg?) ps)
         (seq data)]}
  (let [n (count data)
        sorted (sort data)]
    (->> (map (juxt identity #(int (* n %))) ps)
         (map (juxt first (comp #(nth sorted %)
                                second)))
         (into {}))))
