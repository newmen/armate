(ns armate.utils)

(defn dissoc-if-nil
  [hm & ks]
  (apply dissoc hm (filter #(nil? (hm %)) ks)))

(defn assoc-if-not-nil
  [hm k v]
  (if (nil? v)
    hm
    (assoc hm k v)))
