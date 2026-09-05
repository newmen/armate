(ns armate.archimate.metamodel.solver
  (:require [clojure.set :as o]
            [clojure.math.combinatorics :as combo]
            [armate.utils :as u]))

(defn merge-into
  [& hms]
  (apply merge-with (partial merge-with into) hms))

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
  [non-abstract-elements hierarchy-tree]
  (reduce-kv (fn [acc k v]
               (cond
                 (empty? v) (assoc acc k #{k})
                 (map? v) (let [sub-tree (init-flat-hierarchy non-abstract-elements v)]
                            (-> (merge acc sub-tree)
                                (assoc k (set (apply concat (vals sub-tree))))))
                 (or (vector? v)
                     (set? v)) (let [non-abstract? (non-abstract-elements k)
                                     v2 (if non-abstract? (conj v k) v)]
                                 (-> (reduce (fn [a v2]
                                               (let [vs #{v2}
                                                     vs2 (if non-abstract? (conj vs k) vs)]
                                                 (assoc-if-not-same a v2 vs2)))
                                             acc
                                             v)
                                     (assoc k (set v2))))
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
   (build-flat-hierarchy #{} hierarchy-tree))
  ([non-abstract-elements hierarchy-tree]
   (let [fh1 (init-flat-hierarchy non-abstract-elements hierarchy-tree)]
     (reduce (fn [acc k]
               (->> (resolve-leafs fh1 k)
                    (assoc acc k)))
             {}
             (keys fh1)))))

(defn extend-hierarchy
  [base-flat-hierarchy hierarchy-tree]
  (reduce-kv (fn [acc k vs]
               (->> (mapcat base-flat-hierarchy vs)
                    (set)
                    (assoc acc k)))
             {}
             (build-flat-hierarchy hierarchy-tree)))

(defn resolve-elements
  [flat-hierarchy flat-domains rv]
  (cond
    (keyword? rv) (flat-hierarchy rv)
    (vector? rv) (let [[kind only] rv
                       all-elements (flat-hierarchy kind)
                       only-mask (set (mapcat flat-domains only))]
                   (o/intersection all-elements only-mask))
    :else (throw (ex-info "Unknown relationship vertex" {:vertex rv}))))

(defn multiply-relationships
  [flat-hierarchy flat-domains general-relationships]
  (let [ref (partial resolve-elements flat-hierarchy flat-domains)]
    (reduce-kv (fn [acc k vs]
                 (let [froms (ref k)]
                   (reduce-kv (fn [a v rs]
                                (let [tos (ref v)]
                                  (reduce (fn [a2 [f t]]
                                            (update-in a2 [f t] u/fnil-into-set rs))
                                          a
                                          (combo/cartesian-product froms tos))))
                              acc
                              vs)))
               {}
               general-relationships)))

(defn get-all-vertices
  [relationships]
  (set (concat (keys relationships)
               (mapcat keys (vals relationships)))))

(defn get-ext-each-self
  [hierarcy relationships except? & types]
  (let [tps (set types)]
    (reduce (fn [acc v]
              (if (except? v)
                acc
                (reduce (fn [a v2]
                          (let [ets (get-in relationships [v v2] #{})
                                diff (o/difference tps ets)]
                            (if (empty? diff)
                              a
                              (update-in a [v v2] u/fnil-into-set diff))))
                        acc
                        (hierarcy v))))
            {}
            (get-all-vertices relationships))))

(defn get-ext-each-other
  [relationships except? & types]
  (let [tps (set types)
        vertices (get-all-vertices relationships)]
    (reduce (fn [acc v1]
              (if (except? v1)
                acc
                (reduce (fn [a v2]
                          (if (or (except? v2) (except? v1 v2))
                            a
                            (let [ets (get-in relationships [v1 v2] #{})
                                  diff (o/difference tps ets)]
                              (if (empty? diff)
                                a
                                (update-in a [v1 v2] u/fnil-into-set diff)))))
                        acc
                        vertices)))
            {}
            vertices)))

(defn translate-rmg
  [f rmg except?]
  (reduce-kv (fn [acc from tos]
               (if (except? from)
                 acc
                 (reduce-kv (fn [a to rls]
                              (if (except? to)
                                a
                                (reduce (fn [a2 r]
                                          (update-in a2 [from to] u/fnil-conj-set (f r)))
                                        a
                                        rls)))
                            acc
                            tos)))
             {}
             rmg))

(defn rel-rules-to-mg
  [relationships except?]
  (translate-rmg (partial hash-map :type) relationships except?))

(defn mg-to-rel-rules
  [graph]
  (translate-rmg :type graph (constantly false)))
