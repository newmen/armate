(ns armate.archimate.metamodel.derivation.core
  (:require [clojure.tools.logging :as log]
            [armate.archimate.metamodel.appendix :as adx]
            [armate.archimate.metamodel.derivation.match :as mch]
            [armate.archimate.metamodel.derivation.restrictions :as rtr]
            [armate.archimate.metamodel.derivation.rules :as drs]
            [armate.archimate.model :as model]
            [armate.archimate.multi-graph :as mg]))

(defn- get-relation
  [graph from to relation-type]
  (->> (get-in graph [from to])
       (some #(when (= relation-type (:type %))
                %))))

(defn- append-relation
  [context from to relation]
  (if-let [r (get-relation (:relations context) from to (:type relation))]
    (let [rs (model/relation context from to)
          rs2 (disj rs r)
          rs3 (conj rs2 (-> r
                            (assoc :derivate (:derivate relation))
                            (assoc :original? true)))]
      (log/info (str "Derivated relation " (:type relation) " between [" from " " to "] detected"))
      (model/assoc-relations context from to rs3))
    (model/set-relation context from to relation)))

(defn- append-relations
  [derivate-kind context relations]
  (reduce (fn [acc [from to rel]]
            (if (= from to)
              acc
              (let [from-kind (model/element-kind acc from)
                    to-kind (model/element-kind acc to)
                    rel-type (:type rel)
                    relation (-> (select-keys rel [:type :desc])
                                 (assoc :from from-kind)
                                 (assoc :to to-kind)
                                 (assoc :derivate derivate-kind))]
                (if (contains? (get-in adx/total-relationships [from-kind to-kind]) rel-type)
                  (append-relation acc from to relation)
                  (let [skf #(-> (model/element acc %)
                                 (select-keys [:name :alias :kind]))]
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
    (let [kf #(model/element-kind context %)
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
                             (let [from-kind (model/element-kind context from)
                                   to-kind (model/element-kind context to)
                                   rel-type (:type rel)]
                               (contains? (get-in adx/total-relationships
                                                  [from-kind to-kind])
                                          rel-type)))
                           (:relations context)))

(defn- derivate-by-rules
  "Run each [@derivate-kind @restricted-fn @rules] pass over @context, appending the
   derived relationships the rules yield for the current relations and re-deriving from
   the growing context."
  [context passes]
  (reduce (fn [acc [derivation rf? rules]]
            (log/info (str "Derivating " (name derivation) " rules"))
            (let [relations (filter-possible-relations acc)
                  derivated-relations (mch/derivate-relationships rf? rules relations)]
              (append-relations derivation acc derivated-relations)))
          context
          passes))

(defn derivate-certain-relations
  "Derive only the `certain` ArchiMate relationships in @context (per DR1-DR8). No
   `potential` rules run, so the result carries no `:potential`-derived relations. Used for
   `certain`/`derived_relations` where only guaranteed implications should surface."
  [context]
  (let [crd? (get-restricted-f mem-restricted? context)]
    (derivate-by-rules context [[:certain crd? drs/certain-rules]])))

(defn derivate-relations
  "Derive the `certain` then `potential` ArchiMate relationships in @context (per Appendix
   B normative rules). The result carries both `:certain` and `:potential`-derived
   relationships. Used for the `certain+potential` MCP mode."
  [context]
  (let [crd? (get-restricted-f mem-restricted? context)
        grd? (get-restricted-f (memoize grouping-restricted?) context)]
    (derivate-by-rules context
                       [[:certain crd? drs/certain-rules]
                        [:potential crd? drs/potential-rules]
                        [:potential grd? drs/potential-group-around-rules]])))
