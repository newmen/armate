(ns armate.archimate.collector
  (:require [clojure.string :as s]
            [clojure.set :as o]
            [armate.archimate.metamodel.meta :as mt]
            [armate.archimate.multi-graph :as mg]
            [armate.utils :as u]))

(defn get-nbrs
  [rels-graph rel-type-f rel-to-f alias]
  (->> (get rels-graph alias)
       (filter (fn [[_ rels]]
                 (some (fn [rel]
                         (and (rel-type-f (:type rel))
                              (rel-to-f (:to rel))))
                       rels)))
       (map first)))

(defn get-composed-aliases
  [context component-alias]
  (get-nbrs (:relations context)
            #{:aggregation :composition}
            constantly
            component-alias))

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
       (into #{})))

(defn- collect-aliases
  [graph depth aliases]
  (loop [depth depth
         aliases aliases
         first-time? true]
    (if (zero? depth)
      aliases
      (let [nals (->> (mg/get-relationships graph)
                      (filter (fn [[from to rel]]
                                (if first-time?
                                  (or (aliases to) (aliases from))
                                  (and (aliases to)
                                       (or (aliases from)
                                           (mt/structural? (:type rel)))))))
                      (mapcat (juxt first second))
                      (into #{}))]
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

(defn ungroup
  [context]
  (->> (:relations context)
       (mg/get-relationships)
       (reduce (fn [acc [from to rel]]
                 (if (and (= :nesting (:derivate rel))
                          (not= :grouping (get-in context [:elements from :kind])))
                   (-> (assoc-in acc [:elements to :in] nil)
                       (update-in [:relations from to] u/fnil-conj-set
                                  (dissoc rel :derivate)))
                   (update-in acc [:relations from to] u/fnil-conj-set rel)))
               (assoc context :relations {}))))

(defn erase-unbinded-elements
  ([context]
   (erase-unbinded-elements context (constantly true)))
  ([context predicate]
   (let [all-aliases (into #{} (keys (:elements context)))
         related-aliases (->> (:relations context)
                              (mg/get-relationship-sets)
                              (mapcat (juxt first second))
                              (into #{}))
         unbinded-aliases (o/difference all-aliases related-aliases)]
     (select-just-elements context
                           (fn [element]
                             (not (and (predicate element)
                                       (unbinded-aliases (:alias element)))))))))

(defn erase-groups-wihtout-elements
  [context element-predicate]
  (let [gf (fn [[from to rel]]
             (and (= :grouping (get-in context [:elements from :kind]))
                  (#{:aggregation :composition} (:type rel))
                  (element-predicate (get-in context [:elements to]))))
        group-aliases (->> (:relations context)
                           (mg/filter-relationships gf)
                           (keys))
        element-aliases (->> (:elements context)
                             (filter (comp (partial not= :grouping) :kind second))
                             (map first))
        aliases (into #{} (concat group-aliases element-aliases))]
    (select-just-elements context (comp aliases :alias))))
