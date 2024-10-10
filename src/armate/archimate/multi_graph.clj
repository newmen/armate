(ns armate.archimate.multi-graph)

(defn get-relationships
  ([graph]
   (get-relationships identity graph))
  ([prepare graph]
   (->> (prepare graph)
        (mapcat (fn [[from nbrs]]
                  (->> (prepare nbrs)
                       (mapcat (fn [[to rels]]
                                 (map (fn [rel]
                                        [from to rel])
                                      rels)))))))))
