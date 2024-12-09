(ns armate.archimate.metamodel.solver 
  (:require [clojure.set :as o]
            [clojure.math.combinatorics :as combo]))

(defn assoc-if-not-same
  [flat-hierarchy key value]
  (let [kv #{key}
        pv (flat-hierarchy key)]
    (if (or (nil? pv)
            (and (= kv pv)
                 (not= kv value)))
      (assoc flat-hierarchy key value)
      flat-hierarchy)))

(defn init-flat-hierarchy
  [hierarchy-tree]
  (reduce-kv (fn [acc k v]
               (cond
                 (empty? v) (assoc acc k #{k})
                 (map? v) (let [sub-tree (init-flat-hierarchy v)]
                            (-> (merge acc sub-tree)
                                (assoc k (set (apply concat (vals sub-tree))))))
                 (or (vector? v)
                     (set? v)) (-> (reduce #(assoc-if-not-same %1 %2 #{%2}) acc v)
                                   (assoc k (set v)))
                 :else (throw (ex-info "Unknown hierarchy value" {:key k :value v}))))
             {}
             hierarchy-tree))

(defn resolve-leafs
  ([flat-hierarchy key]
   (resolve-leafs flat-hierarchy key #{key}))
  ([flat-hierarchy key visited]
   (let [values (flat-hierarchy key)
         groups (group-by (comp boolean visited) values)
         visited2 (into visited values)]
     (->> (groups false)
          (mapcat #(resolve-leafs flat-hierarchy % visited2))
          (concat (groups true))
          (set)))))

(defn build-flat-hierarchy
  ([hierarchy-tree]
   (let [fh1 (init-flat-hierarchy hierarchy-tree)]
     (reduce (fn [acc k]
               (->> (resolve-leafs fh1 k)
                    (assoc acc k)))
             {}
             (keys fh1))))
  ([base-flat-hierarchy hierarchy-tree]
   (reduce-kv (fn [acc k vs]
                (->> (mapcat base-flat-hierarchy vs)
                     (set)
                     (assoc acc k)))
              {}
              (build-flat-hierarchy hierarchy-tree))))

(defn resolve-elements
  [flat-hierarchy flat-layers rv]
  (cond
    (keyword? rv) (flat-hierarchy rv)
    (vector? rv) (let [[kind only] rv
                       all-elements (flat-hierarchy kind)
                       only-mask (set (mapcat flat-layers only))]
                   (o/intersection all-elements only-mask))
    :else (throw (ex-info "Unknown relationship vertex" {:vertex rv}))))

(defn multiply-relationships
  [flat-hierarchy flat-layers general-relationships]
  (let [ref (partial resolve-elements flat-hierarchy flat-layers)]
    (reduce-kv (fn [acc k vs]
                 (let [froms (ref k)]
                   (reduce-kv (fn [a v rs]
                                (let [tos (ref v)]
                                  (reduce (fn [a2 [f t]]
                                            (update-in a2 [f t] (fnil into #{}) rs))
                                          a
                                          (combo/cartesian-product froms tos))))
                              acc
                              vs)))
               {}
               general-relationships)))
