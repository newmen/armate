(ns armate.archimate.info.tables
  (:require [clojure.pprint :as pp]
            [clojure.string :as s]
            [armate.archimate.collector :as acl]
            [armate.archimate.multi-graph :as mg]
            [armate.archimate.viz.call-counter :as ccr]
            [armate.utils :as u]))

(defn get-common-rate
  [reversed-graph interface-alias]
  (->> (reversed-graph interface-alias)
       (vals)
       (mapcat identity)
       (filter (comp #{:triggering :flow} :type))
       (map :rate)
       (remove nil?)
       (apply +)
       (u/round 3)))

(defn get-components
  ([context]
   (get-components context (mg/reverse-graph (:relations context))))
  ([context reversed-graph]
   (let [gnf #(get-in context [:elements % :name] %)
         gpcf (partial acl/get-nbrs
                       reversed-graph
                       #{:aggregation}
                       #{:application-collaboration})]
     (->> (vals (:elements context))
          (filter (fn [component]
                    (and (= :application-component (:kind component))
                         (not (:skin component)))))
          (map (fn [component]
                 (let [tenant-alias (or (first (gpcf (:alias component))) "-")]
                   {:tenant (gnf tenant-alias)
                    :system (:name component)})))
          (sort-by (juxt :tenant :system))))))

(defn combine-iface-name
  [top-name inner-name]
  (let [inner-parts (s/split inner-name #"\s+")]
    (if (= 1 (count inner-parts))
      (str top-name inner-name)
      (str (first inner-parts) " "
           top-name (s/join (rest inner-parts))))))

(defn get-interfaces
  ([context]
   (let [reversed-graph (mg/reverse-graph (:relations context))]
     (->> (get-components context reversed-graph)
          (mapcat (fn [ts-pair]
                    (get-interfaces context reversed-graph (:system ts-pair)))))))
  ([context reversed-graph component-name]
   (let [lower-cmp-name (s/lower-case component-name)]
     (when-let [cmp-alias (->> (vals (:elements context))
                               (filter (comp (partial = lower-cmp-name) s/lower-case :name))
                               (sort-by :kind)
                               (first) ; TODO: there can be a few components with same name
                               (:alias))]
       (let [gnf #(get-in context [:elements % :name] %)
             graph (:relations context)
             gcisf (partial acl/get-nbrs graph #{:composition} #{:application-interface})
             gcisf2 (fn [alias]
                      (let [interfaces (gcisf alias)]
                        (if (seq interfaces)
                          interfaces
                          [nil])))
             top-ifc-aliases (gcisf2 cmp-alias)]
         (->> (map (juxt identity gcisf2) top-ifc-aliases)
              (mapcat (fn [[top-alias inner-aliases]]
                        (map (fn [inner-alias]
                               (let [ifc-alias (if (nil? inner-alias)
                                                 top-alias
                                                 inner-alias)]
                                 {:component component-name
                                  :interface (if (nil? inner-alias)
                                               (gnf top-alias)
                                               (combine-iface-name (gnf top-alias)
                                                                   (gnf inner-alias)))
                                  :rate (get-common-rate reversed-graph ifc-alias)}))
                             inner-aliases)))
              (sort-by (juxt :component :interface))))))))

(defn show-components
  [context]
  (->> (get-components context)
       (pp/print-table [:tenant :system])))

(defn show-interfaces
  [context]
  (->> (ccr/add-call-rates context)
       (get-interfaces)
       (pp/print-table [:component :interface :rate])))
