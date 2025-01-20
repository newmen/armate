(ns armate.archimate.viz.combiner
  (:require [clojure.string :as s]
            [armate.archimate.metamodel.derivation.match :as mch]
            [armate.archimate.multi-graph :as mg]
            [armate.archimate.viz.common :as vcm]
            [armate.utils :as u]))

(def group-modes
  {:application-interface true
   :application-component true
   :application-collaboration true})

(def indent "  ")

(def sort-line-key
  #(:line % 999999))

(defn sort-by-lines
  ([items]
   (sort-by sort-line-key items))
  ([weights items]
   (let [groups (group-by #(contains? % :line) items)]
     (concat (sort-by-lines (groups true []))
             (sort-by (comp weights :alias)
                      (groups false []))))))

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
            (->> (sort-by-lines items)
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
           (fn [{:keys [shape title alias type skin layer color]}]
             (let [cut? (= alias title)
                   title2 (if cut? title (wrap-str title))]
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
           (:type element)) (get-shape element)
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

(defn sbl-map-with
  [sf mf hm]
  (->> (vals hm)
       (sf)
       (mapcat mf)))

(def sbl-map
  (partial sbl-map-with sort-by-lines))

(defn ebl-map
  [weights f hm]
  (sbl-map-with (partial sort-by-lines weights) f hm))

(defn- get-relations
  [grsf key context]
  (let [sf (if (= mg/get-relationships grsf)
             (partial sort-by (comp sort-line-key last))
             identity)]
    (->> (grsf (key context))
         (remove (comp (partial = :nesting) :derivate last))
         (sf)
         (mapcat get-relation))))

(defn generate-puml
  ([context]
   (generate-puml sbl-map
                  (partial get-relations mg/get-relationships)
                  context))
  ([elf relf context]
   (->> [(get-start (:start context))
         (sbl-map get-include (:includes context))
         (sbl-map get-skin (:skins context))
         (sbl-map get-type (:types context))
         (sbl-map get-connector (:connectors context))
         (elf get-element (nest-inside (:elements context)))
         (relf :relations context)
         (relf :hidden context)
         end]
        (remove empty?)
        (map (partial s/join "\n"))
        (s/join "\n\n"))))

(defn kind-index
  [kind]
  (.indexOf [:business-product
             :business-service
             :application-service
             :application-interface] kind))

(defn get-rels-based-weights
  [graph]
  (let [source-weights (->> graph
                            (map (juxt first
                                       (fn [[_ nbrs]]
                                         (->> (vals nbrs)
                                              (reduce concat)
                                              (map :type)
                                              (map mch/get-rel-wieght)
                                              (reduce +)))))
                            (into {}))]
    (->> graph
         (map (juxt first
                    (fn [[node nbrs]]
                      (let [wth #(- (source-weights % 0))]
                        [(wth node) (reduce + (map (comp wth first) nbrs))]))))
         (into {}))))

(defn get-out-weights
  [context]
  (->> (get-rels-based-weights (:relations context))
       (map (juxt first (fn [[alias [a b]]]
                          (let [kind (get-in context [:elements alias :kind])]
                            [(kind-index kind) (- a) (- b) alias]))))
       (into {})))

(defn get-total-weights
  [context]
  (let [out-weights (get-out-weights context)
        in-weights (->> (:elements context)
                        (remove (comp out-weights :alias second))
                        (map (fn [[alias element]]
                               [alias [(kind-index (:kind element)) 0 0 alias]]))
                        (into {}))]
    (merge in-weights out-weights)))

(defn- groupable?
  [element-kind rel]
  (and (group-modes element-kind)
       (= element-kind (:from rel))))

(defn make-nesting
  [triple]
  (let [[from to rel] triple
        gpbl? #(groupable? % rel)
        make-nesting #(assoc rel :derivate :nesting)]
    (cond
      (= :nesting (:derivate rel)) [false triple]
      (or (and (= :aggregation (:type rel))
               (gpbl? :application-collaboration))
          (and (= :composition (:type rel))
               (or (gpbl? :application-interface)
                   (gpbl? :application-component)))) [true [from to (make-nesting)]]
      :else [false triple])))

(defn make-grouped
  [context]
  (->> (:relations context)
       (mg/get-relationships)
       (map make-nesting)
       (reduce (fn [acc [nesting? [from to rel]]]
                 (let [ctx2 (update-in acc [:relations from to] u/fnil-conj-set rel)]
                   (if nesting?
                     (assoc-in ctx2 [:elements to :in] from)
                     ctx2)))
               (assoc context :relations {}))))

(defn on-fly-generate-puml
  [context]
  (let [grouped-ctx (make-grouped context)
        weights (get-total-weights grouped-ctx)
        grsf (comp (partial mg/get-relationships
                            (partial sort-by (fn [[from & _]] (weights from)))))]
    (generate-puml (partial ebl-map weights)
                   (partial get-relations grsf)
                   grouped-ctx)))
