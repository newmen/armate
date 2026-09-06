(ns armate.archimate.viz.combiner
  (:require [clojure.string :as s]
            [clojure.set :as o]
            [armate.archimate.metamodel.derivation.match :as mch]
            [armate.archimate.multi-graph :as mg]
            [armate.archimate.viz.common :as vcm]
            [armate.utils :as u]))

(def do-grouping? false)
(def group-modes
  {:application-collaboration #{:composition :aggregation}
   :application-component #{:composition}
   :application-interface #{:composition}
   :application-function #{:aggregation :composition}
   :application-process #{:aggregation :composition}
   :application-service #{:composition}
   :grouping #{:aggregation :composition}})

(def escape-derivated
  #{:certain :potential})

(def layer-order
  {:motivation 0
   :strategy 1
   :business 2
   :application 3
   :technology 4
   :implementation 5})

(def default-layer-rank 6)

(defn element-layer-rank
  [element]
  (layer-order (:layer element) default-layer-rank))

(defn sort-elements
  "Elements with :line keep the original file order.
   Others are ordered by layer (Motivation..Implementation, composite last)
   and then lexicographically by alias."
  [items]
  (let [groups (group-by #(contains? % :line) items)]
    (concat (sort-by :line (groups true []))
            (sort-by (juxt element-layer-rank :alias) (groups false [])))))

(def indent "  ")

(def add-default-skins? false)

(def sort-line-key
  #(:line % 999999))

(defn wrap-str
  [text]
  (str "\"" text "\""))

(defn wrap-fur
  [text]
  (str "<<" text ">>"))

(defn get-start
  [start]
  [(str "@startuml" (if-let [title (:title start)]
                      (str " " (wrap-str title))
                      ""))])

(def end
  ["@enduml"])

(defn get-include
  [include]
  [(str "!include <" (:package include) ">")])

(defn nest-lines
  [title items item-f]
  (if (empty? items)
    [title]
    (concat [(str title " {")]
            (->> (sort-elements items)
                 (mapcat (comp (partial map (partial str indent)) item-f)))
            ["}"])))

(defn get-skin
  [{:keys [alias shape props]}]
  (let [target (when shape
                 (str shape (if alias
                              (wrap-fur alias)
                              "")))]
    (nest-lines (str "skinparam" (when target
                                   (str " " target)))
                props
                (comp vector (partial s/join " ") :parts))))

(defn get-type
  [type]
  [(str "sprite " (:alias type) " jar:archimate/" (name (:kind type)))])

(defn make-call
  [func args]
  (str func "(" (s/join ", " args) ")"))

(defn get-connector
  [{:keys [type alias title]}]
  (let [func (str "Junction_" (s/capitalize (name type)))
        args (cons alias
                   (when title
                     [(wrap-str title)]))]
    [(make-call func args)]))

(declare get-element)
(defn build-element
  [parts-f element]
  (let [parts (parts-f element)]
    (nest-lines (s/join " " parts)
                (:inside element)
                get-element)))

(def get-group
  (partial build-element
           (fn [{:keys [type alias title color]}]
             (let [func (s/capitalize (name type))
                   args (cons alias
                              (when title
                                [(wrap-str title)]))]
               (concat [(make-call func args)]
                       (when color
                         [color]))))))

(def get-shape
  (partial build-element
           (fn [{:keys [shape title alias type skin layer color] :as element}]
             (let [nm (:name element)
                   cut? (= alias title)
                   title2 (if cut?
                            title
                            (wrap-str (if (:inside element) nm title)))]
               (concat [shape title2]
                       (when-not cut?
                         [(str "as " alias)])
                       (->> [type skin]
                            (remove nil?)
                            (map wrap-fur)
                            (apply str)
                            (list))
                       (when-not skin
                         (when layer
                           [(str "#" (s/capitalize (name layer)))]))
                       (when color
                         [color]))))))

(def get-fn-element
  (partial build-element
           (fn [{:keys [kind alias title color]}]
             (let [func (vcm/get-fn-name (name kind))
                   args (cons alias
                              (when title
                                [(wrap-str title)]))]
               (concat [(make-call func args)]
                       (when color
                         [color]))))))

(defn get-element
  [element]
  (let [kind (:kind element)]
    (cond
      (= :grouping kind) (get-group element)
      (and (:shape element)
           (:type element)
           (or (:line element)
               (:inside element)
               (:skin element))) (get-shape element)
      :else (get-fn-element element))))

(defn- get-relation
  [[from to {:keys [type direction raw reverse? desc]
             :as relation}]]
  [(if raw
     (let [parts [from raw to]
           parts2 (if reverse? (reverse parts) parts)]
       (s/join " " parts2))
     (if type
       (let [func (apply str (concat ["Rel_" (s/capitalize (name type))]
                                     (when direction
                                       [(str "_" (s/capitalize (name direction)))])))
             args [from to]
             args2 (if desc (into args [(wrap-str desc)]) args)]
         (make-call func args2))
       (throw (ex-info "A relation without type" relation))))])

(defn reorder
  [elements]
  (loop [elements (vals elements)
         added #{}
         result []]
    (if (empty? elements)
      result
      (let [tail (rest elements)
            element (first elements)
            in (:in element)]
        (if (or (not in)
                (added in))
          (recur tail
                 (conj added (:alias element))
                 (conj result element))
          (recur (concat tail [element])
                 added
                 result))))))

(defn nest-inside
  [elements]
  (reduce (fn [acc element]
            (let [alias (:alias element)
                  in (:in element)]
              (if in
                (let [actual (acc alias)]
                  (-> acc
                      (dissoc alias)
                      (update-in [in :inside] (fnil conj []) actual)))
                acc)))
          elements
          (reverse (reorder elements))))

(defn global-map-with
  [sf mf hm]
  (->> (vals hm)
       (sort-by sf)
       (mapcat mf)))

(def global-map
  (partial global-map-with sort-line-key))

(defn element-map
  [f hm]
  (->> (vals hm)
       (sort-elements)
       (mapcat f)))

(defn relation-sort-key
  [[from to rel]]
  [(get rel :line 999999)
   (if-let [t (:type rel)]
     (- (mch/get-rel-wieght t))
     0)
   (or from "")
   (or to "")])

(defn- get-relations
  [grsf key context]
  (->> (grsf (key context))
       (remove (comp (o/union #{:nesting :connecting} escape-derivated)
                     :derivate
                     last))
       (sort-by relation-sort-key)
       (mapcat get-relation)))

(defn filter-skins
  [skins add-group-skin?]
  (if add-default-skins?
    (if add-group-skin?
      skins
      (dissoc skins ["folder" "grouping"]))
    (if add-group-skin?
      (dissoc skins [:default] ["rectangle"])
      {})))

(defn get-holder-types
  [context]
  (let [elements (:elements context)]
    (->> (vals elements)
         (map :in)
         (set)
         (map elements)
         (map :type)
         (set))))

(defn has-group-element?
  [context]
  (->> (:elements context)
       (vals)
       (map :kind)
       (some #{:grouping :group})))

(defn generate-puml
  ([context]
   (generate-puml (partial get-relations mg/get-relationships)
                  context))
  ([relf context]
   (let [holder-types (get-holder-types context)
         group? (has-group-element? context)]
     (->> [(get-start (:start context))
           (global-map get-include (:includes context))
           (global-map get-skin (filter-skins (:skins context) group?))
           (global-map get-type (select-keys (:types context) holder-types))
           (global-map get-connector (:connectors context))
           (element-map get-element (nest-inside (:elements context)))
           (relf :relations context)
           (relf :hidden context)
           end]
          (remove empty?)
          (map (partial s/join "\n"))
          (s/join "\n\n")))))

(defn make-nesting
  [context triple]
  (let [[from to rel] triple]
    (if (and do-grouping?
             (when-let [element (get-in context [:elements from])]
               (contains? (group-modes (:kind element)) (:type rel))))
      [true [from to (assoc rel :derivate :nesting)]]
      [false triple])))

(defn make-grouped
  [context]
  (->> (:relations context)
       (mg/get-relationships)
       (map (partial make-nesting context))
       (reduce (fn [acc [nesting? [from to rel]]]
                 (let [ctx2 (update-in acc [:relations from to] u/fnil-conj-set rel)]
                   (if nesting?
                     (assoc-in ctx2 [:elements to :in] from)
                     ctx2)))
               (assoc context :relations {}))))

(defn on-fly-generate-puml
  [context]
  (generate-puml (partial get-relations mg/get-relationships)
                 (make-grouped context)))
