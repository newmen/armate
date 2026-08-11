(ns armate.archimate.viz.align.grid
  (:require [armate.archimate.multi-graph :as mg]
            [armate.utils :as u]))

(defn get-deps
  [graph]
  (reduce-kv
   (fn [acc from to-rels]
     (reduce-kv (fn [acc to rels]
                  (let [rel (first (filter :direction rels))]
                    (case (:direction rel)
                      :up (-> acc
                              (update-in [:ud to] u/fnil-conj-set from)
                              (update-in [:du from] u/fnil-conj-set to))
                      :down (-> acc
                                (update-in [:ud from] u/fnil-conj-set to)
                                (update-in [:du to] u/fnil-conj-set from))
                      acc)))
                acc
                to-rels))
   {:ud {} :du {}}
   graph))

(defn find-layers
  ([du-deps aliases]
   (reduce (partial find-layers du-deps) {} aliases))
  ([du-deps visited from]
   (let [data (reduce (fn [acc to]
                        (let [ivds (:visited acc)]
                          (if-let [layer (ivds to)]
                            (update acc :near-layers conj layer)
                            (let [ext-layers (find-layers du-deps ivds to)]
                              (-> (update acc :near-layers conj (ext-layers to))
                                  (assoc :visited ext-layers))))))
                      {:near-layers [-1]
                       :visited (assoc visited from -1)}
                      (du-deps from))]
     (-> (:visited data)
         (assoc from (inc (apply max (:near-layers data))))))))

(defn align-layers
  [deps align-path layers]
  (reduce (fn [acc slice]
            (reduce (fn [a node]
                      (if-let [layer (a node)]
                        (assoc a node
                               (->> (get-in deps [:ud node])
                                    (map a)
                                    (remove nil?)
                                    (map dec)
                                    (cons layer)
                                    (apply max)))
                        a))
                    acc
                    slice))
          layers
          align-path))

(defn group-by-layers
  [layers]
  (->> (group-by second layers)
       (sort-by first)
       (mapv (comp set (partial map first) second))))

(defn assign-layers
  [graph target-aliases]
  (let [deps (get-deps graph)
        all-aliases (mg/get-nodes graph)
        ud-layers (find-layers (:du deps) all-aliases)
        du-layers (find-layers (:ud deps) all-aliases)
        du-gls (group-by-layers du-layers)]
    (-> (align-layers deps du-gls ud-layers)
        (select-keys target-aliases))))

(defn split-into-layers
  [graph aliases]
  (->> (assign-layers graph aliases)
       (group-by-layers)))

(defn get-layers
  [context]
  (split-into-layers (:relations context)
                     (keys (:elements context))))
