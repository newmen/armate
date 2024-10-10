(ns armate.archimate.collector)

(defn get-composed-aliases
  [context component-alias]
  (->> (get-in context [:relations component-alias])
       (filter (comp (partial some
                              (comp (partial = :composition)
                                    :type))
                     second))
       (map first)))

(defn collect-interfaces
  [context component-alias]
  (->> (get-composed-aliases context component-alias)
       (map #(get-in context [:elements %]))
       (mapcat (fn [element]
                 (case (:kind element)
                   :application-component (collect-interfaces context (:alias element))
                   :application-interface [element]
                   nil)))))

(defn collect-component-interfaces
  [context]
  (->> (vals (:elements context))
       (filter (comp (partial = :application-component) :kind))
       (map (fn [component]
              {:component component
               :interfaces (collect-interfaces context (:alias component))}))))
