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
