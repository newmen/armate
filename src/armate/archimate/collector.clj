(ns armate.archimate.collector
  (:require [clojure.string :as s]
            [armate.archimate.multi-graph :as mg]))

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

(defn- select-elements
  [binary-check
   staying-aliases
   context predicate]
  (let [elements (->> (vals (:elements context))
                      (filter predicate))
        aliases (set (map :alias elements))
        frf (partial mg/filter-relationships
                     (fn [[from to _]]
                       (binary-check (aliases from) (aliases to))))
        relations (frf (:relations context))
        aliases2 (staying-aliases aliases relations)]
    (-> context
        (update :elements #(select-keys % aliases2))
        (assoc :relations relations)
        (update :hidden frf))))

(def select-just-elements
  (partial select-elements
           #(and %1 %2)
           (fn [aliases _] aliases)))

(def select-near-elements
  (partial select-elements
           #(or %1 %2)
           (fn [_ relations]
             (->> (mg/get-relationships relations)
                  (mapcat (juxt first second))
                  (set)))))

(defn exclude-sub-titles
  [context excluding-names]
  (let [checking-nps (map s/lower-case excluding-names)]
    (select-just-elements context
                          (fn [element]
                            (not (some (partial s/includes?
                                                (s/lower-case (:name element)))
                                       checking-nps))))))

(defn select-services
  [context]
  (select-just-elements context
                        (comp #{:business-product
                                :business-service
                                :application-service}
                              :kind)))
