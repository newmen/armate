(ns armate.utils)

(def fnil-conj-set
  (fnil conj #{}))

(defn dissoc-if-nil
  [hm & ks]
  (apply dissoc hm
         (filter (comp nil? hm) ks)))

(defn assoc-if-not-nil
  [hm k v]
  (if (nil? v)
    hm
    (assoc hm k v)))

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
