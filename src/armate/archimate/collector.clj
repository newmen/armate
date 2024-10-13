(ns armate.archimate.collector
  (:require [clojure.string :as s]
            [armate.archimate.derivation.rules :as drs]
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

(defn- filter-aliases
  [context predicate]
  (->> (vals (:elements context))
       (filter predicate)
       (map :alias)
       (set)))

(def structural-rels
  (set drs/structural-rels))

(defn- collect-aliases
  [graph depth aliases]
  (loop [depth depth
         aliases aliases
         first-time? true]
    (prn depth aliases first-time?)
    (if (zero? depth)
      aliases
      (let [nals (->> (mg/get-relationships graph)
                      (filter (fn [[from to rel]]
                                (if first-time?
                                  (or (aliases to) (aliases from))
                                  (and (aliases to)
                                       (or (aliases from)
                                           (structural-rels (:type rel)))))))
                      (mapcat (juxt first second))
                      (set))]
        (recur (if (= aliases nals) 0 (dec depth))
               nals
               false)))))

(defn select-just-elements
  [context predicate]
  (let [aliases (filter-aliases context predicate)
        frf (partial mg/filter-relationships
                     (fn [[from to _]]
                       (and (aliases from) (aliases to))))]
    (-> context
        (update :elements #(select-keys % aliases))
        (update :relations frf)
        (update :hidden frf))))

(defn select-near-elements
  ([context predicate]
   (select-near-elements context predicate 1))
  ([context predicate depth]
   (let [aliases (->> (filter-aliases context predicate)
                      (collect-aliases (:relations context) depth))]
     (select-just-elements context (comp aliases :alias)))))

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
