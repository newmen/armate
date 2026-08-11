(ns armate.archimate.metamodel.derivation.core
  (:require [clojure.tools.logging :as log]
            [armate.archimate.metamodel.appendix :as adx]
            [armate.archimate.metamodel.derivation.match :as mch]
            [armate.archimate.metamodel.derivation.restrictions :as rtr]
            [armate.archimate.metamodel.derivation.rules :as drs]
            [armate.archimate.multi-graph :as mg]
            [armate.utils :as u]))

(defn- get-relation
  [graph from to relation-type]
  (->> (get-in graph [from to])
       (some #(when (= relation-type (:type %))
                %))))

(defn- append-relation
  [context from to relation]
  (if-let [r (get-relation (:relations context) from to (:type relation))]
    (let [rs (get-in context [:relations from to])
          rs2 (disj rs r)
          rs3 (conj rs2 (-> r
                            (assoc :derivate (:derivate relation))
                            (assoc :original? true)))]
      (log/info (str "Derivated relation " (:type relation) " between [" from " " to "] detected"))
      (assoc-in context [:relations from to] rs3))
    (update-in context [:relations from to] u/fnil-conj-set relation)))

(defn- append-relations
  [derivate-kind context relations]
  (reduce (fn [acc [from to rel]]
            (if (= from to)
              acc
              (let [from-kind (get-in context [:elements from :kind])
                    to-kind (get-in context [:elements to :kind])
                    rel-type (:type rel)
                    relation (-> (select-keys rel [:type :desc])
                                 (assoc :from from-kind)
                                 (assoc :to to-kind)
                                 (assoc :derivate derivate-kind))]
                (if (contains? (get-in adx/total-relationships [from-kind to-kind]) rel-type)
                  (append-relation acc from to relation)
                  (let [skf #(-> (get-in context [:elements %])
                                 (select-keys [:name :alias :kind]))]
                    ;; (throw (ex-info "Unexpected relation has been derived"
                    ;;                 {:from (skf from)
                    ;;                  :to (skf to)
                    ;;                  :relation (select-keys relation [:type :derivate])}))
                    (log/warn "Unexpected relation has been derived"
                              {:from (skf from)
                               :to (skf to)
                               :relation (select-keys relation [:type :derivate])})
                    acc)))))
          context
          (mg/get-relationships relations)))

(defn- get-restricted-f
  [rf? context]
  (fn [a b c s]
    (let [kf #(get-in context [:elements % :kind])
          ak (kf a)
          bk (kf b)
          ck (kf c)]
      (rf? ak bk ck s))))

(def mem-restricted?
  (memoize rtr/restricted?))

(defn- grouping-restricted?
  [a b c s]
  (or (not= :grouping c)
      (not (s (get-in adx/total-relationships [a b])))
      ; the last condition may be excessive here
      (mem-restricted? a b c s)))

(defn filter-possible-relations
  [context]
  (mg/filter-relationships (fn [[from to rel]]
                             (let [from-kind (get-in context [:elements from :kind])
                                   to-kind (get-in context [:elements to :kind])
                                   rel-type (:type rel)]
                               (contains? (get-in adx/total-relationships
                                                  [from-kind to-kind])
                                          rel-type)))
                           (:relations context)))

(defn derivate-relations
  [context]
  (let [crd? (get-restricted-f mem-restricted? context)
        grd? (get-restricted-f (memoize grouping-restricted?) context)]
    (reduce (fn [acc [derivate-kind rf? rules]]
              (log/info (str "Derivating " (name derivate-kind) " rules"))
              (let [relations (filter-possible-relations acc)
                    derivated-relations (mch/derivate-relationships rf? rules relations)]
                (append-relations derivate-kind acc derivated-relations)))
            context
            [[:certain crd? drs/certain-rules]
             [:potential crd? drs/potential-rules]
             [:potential grd? drs/potential-group-around-rules]])))
