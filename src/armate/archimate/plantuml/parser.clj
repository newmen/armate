(ns armate.archimate.plantuml.parser
  (:require [clojure.string :as s]
            [clojure.math.combinatorics :as combo]
            [camel-snake-kebab.core :as csk]
            [armate.archimate.metamodel.meta :as mt]
            [armate.archimate.metamodel.appendix :as adx]
            [armate.archimate.multi-graph :as mg]
            [armate.archimate.viz.common :as vcm]
            [armate.utils :as u]))

(def call-re
  #"^([A-Za-z_]+)\s*\(([A-Za-z0-9_]+)\s*,\s*([A-Za-z0-9_]+)(?:\s*,\s*(.+?))?\)$")

(def full-line-re
  #"^([A-Za-z_]+)\s*\(([A-Za-z0-9_]+)\s*,\s*(\"[^\"]+\"|[^\"\s]+)(?:\s*,\s*(.+?))?\)\s*([^\s]+)?\s*(\{)?$")

(def quoted-split-re
  #"(?:\"[^\"]+\"|[^\"\s]+)")

(defn quoted-brackets-split
  [line]
  (if-let [full-match (re-matches full-line-re line)]
    (remove nil? (rest full-match))
    (re-seq quoted-split-re line)))

(defn get-parts
  [line]
  (let [parts (quoted-brackets-split line)]
    (if (= (last parts) "{")
      {:parts (vec (drop-last parts))
       :block? true}
      (let [lp (last parts)]
        (if (s/ends-with? lp "{")
          (let [cut-last (subs lp 0 (dec (count lp)))]
            {:parts (conj (vec (drop-last parts)) cut-last)
             :block? true})
          (if-let [matches (re-matches call-re line)]
            {:parts (vec (rest matches))
             :block? false}
            {:parts parts
             :block? false}))))))

(defn cut1
  [string]
  (subs string 1 (dec (count string))))

(defn cut2
  [string]
  (cut1 (cut1 string)))

(defn wrapped?
  [start end part]
  (when part
    (and (s/starts-with? part start)
         (s/ends-with? part end))))

(def fur?
  (partial wrapped? "<<" ">>"))

(def fur-re
  #"(<<[^>]+>>|[^<>]+)")

(defn cut-furs
  [part]
  (->> (re-seq fur-re part)
       (map second)
       (map #(if (fur? %) (cut2 %) %))))

(def quoted?
  (partial wrapped? "\"" "\""))

(defn cut-quotes
  [part]
  (if (quoted? part)
    (cut1 part)
    part))

(defn color?
  [part]
  (when part
    (re-matches #"#[0-9a-fA-F]{6}" part)))

(defn variable?
  [line]
  (and (s/starts-with? line "!")
       (not (s/starts-with? line "!include"))
       (s/includes? line "=")))

(def variable-re
  #"\!(.+?)\s*=\s*(.+)")

(defn parse-variable
  [line]
  (when (variable? line)
    (when-let [matches (re-matches variable-re line)]
      [(second matches) (cut-quotes (last matches))])))

(defn mask-specials
  [name]
  (reduce (fn [acc c]
            (s/replace acc (str c) (str "\\" c)))
          name
          [\^ \. \+ \* \? \[ \] \( \) \{ \} \- \$]))

(defn mask-variable
  [name]
  (let [checks [[(partial re-seq #"^[A-Za-z0-9]+")
                 #(str "\\b" %)]
                [(partial re-seq #"[A-Za-z0-9]+$")
                 #(str % "\\b")]]]
    (reduce (fn [acc [check replacement]]
              (if (check name) (replacement acc) acc))
            (mask-specials name)
            checks)))

(defn apply-variables
  [variables line]
  (reduce-kv (fn [full k v]
               (let [re (re-pattern (mask-variable k))]
                 (s/replace full re v)))
             line
             variables))

(defn left-to-right?
  [parts]
  (= ["left" "to" "right" "direction"]
     parts))

(defn get-blocks
  [content]
  (let [lines (s/split-lines content)
        indexes (map inc (range))]
    (loop [lines-is (map vector lines indexes)
           vars {}
           blocks []
           outer-blocks '()]
      (if (empty? lines-is)
        (if (seq outer-blocks)
          (throw (ex-info "Unclosed block" {:blocks (vec outer-blocks)}))
          blocks)
        (let [line-i (first lines-is)
              line (s/trim (first line-i))
              index (second line-i)
              rest-lines-is (rest lines-is)]
          (if (or (empty? line)
                  (s/starts-with? line "'"))
            (recur rest-lines-is vars blocks outer-blocks)
            (if-let [[name value] (parse-variable line)]
              (recur rest-lines-is
                     (assoc vars name value)
                     blocks
                     outer-blocks)
              (let [full-line (apply-variables vars line)
                    {parts :parts
                     block? :block?} (get-parts full-line)]
                (if (left-to-right? parts)
                  (recur rest-lines-is vars blocks outer-blocks)
                  (if block?
                    (recur rest-lines-is
                           vars
                           blocks
                           (cons {:parts parts :line index} outer-blocks))
                    (if (seq outer-blocks)
                      (let [outer-block (first outer-blocks)]
                        (if (= full-line "}")
                          (if-let [prev-block (second outer-blocks)]
                            (recur rest-lines-is
                                   vars
                                   blocks
                                   (cons (update prev-block :props
                                                 (fnil conj [])
                                                 outer-block)
                                         (rest (rest outer-blocks))))
                            (recur rest-lines-is
                                   vars
                                   (conj blocks outer-block)
                                   (rest outer-blocks)))
                          (recur rest-lines-is
                                 vars
                                 blocks
                                 (cons (update outer-block :props
                                               (fnil conj [])
                                               {:parts parts :line index})
                                       (rest outer-blocks)))))
                      (recur rest-lines-is
                             vars
                             (conj blocks {:parts parts :line index})
                             outer-blocks))))))))))))

(def rel-f-re
  #"(?i)^Rel_(.+?)(?:_(Up|Down|Left|Right))?$")

(def rel-b-res
  [#"(?i)(.*?\.+)(up|down|left|right|u|d|l|r)(\.+.*)"
   #"(?i)(.*?-+)(up|down|left|right|u|d|l|r)(-+.*)"
   #"(?i)(.*?~+)(up|down|left|right|u|d|l|r)(~+.*)"
   #"(.*?[\.\-\~]+)(.*)"])

(defn match-rel-b
  [dir-bond]
  (some #(re-matches % dir-bond) rel-b-res))

(def pin-re
  #"([^\.\-\~]*)?([\.\-\~]+)([^\.\-\~]*)?")

(defn match-pos-bond
  [mbond]
  (case mbond
    ["*" \- ""] {:type :composition}
    ["" \- "*"] {:type :composition :reverse? true}
    (["o" \- ""]
     ["O" \- ""]) {:type :aggregation}
    (["" \- "o"]
     ["" \- "O"]) {:type :aggregation :reverse? true}
    ["@" \- ">>"] {:type :assignment}
    ["<<" \- "@"] {:type :assignment :reverse? true}
    ["" \~ "|>"] {:type :realization}
    ["<|" \~ ""] {:type :realization :reverse? true}
    ["" \- ">"] {:type :serving}
    ["<" \- ""] {:type :serving :reverse? true}
    ["<" \~ ">"] {:type :access_rw}
    ["<-" \~ ""] {:type :access_r}
    ["" \~ "->"] {:type :access_r :reverse? true}
    ["" \~ ""] {:type :access}
    ["" \. ">"] {:type :influence}
    ["<" \. ""] {:type :influence :reverse? true}
    (["" \- ""]
     ["" \= ""]) {:type :association}
    ["" \. ">>"] {:type :flow}
    ["<<" \. ""] {:type :flow :reverse? true}
    ["" \- ">>"] {:type :triggering}
    ["<<" \- ""] {:type :triggering :reverse? true}
    (["" \- "|>"]
     ["" \- "^"]) {:type :specialization}
    (["<|" \- ""]
     ["^" \- ""]) {:type :specialization :reverse? true}
    {:type :unknown}))

(defn b-matches
  [dir-bond]
  (when-let [matches (match-rel-b dir-bond)]
    (let [bond (str (second matches) (last matches))
          [_ left body right] (re-matches pin-re bond)
          bc (first body)
          mbond [left bc right]
          direction (when (= (count matches) 4)
                      (keyword (s/lower-case (nth matches 2))))]
      (assoc (match-pos-bond mbond)
             :direction direction
             :raw dir-bond
             :cut (apply str mbond)))))

(defn match-rel
  [parts]
  (when (> (count parts) 2)
    (let [[f s to desc] parts
          f-matches (re-matches rel-f-re f)]
      (if f-matches
        (let [kind (keyword (s/lower-case (second f-matches)))
              possible? (mt/relationship? kind)
              type (if possible? kind :unknown)
              direction (when-let [d (last f-matches)]
                          (keyword (s/lower-case d)))
              desc2 (cut-quotes desc)
              result {:type type
                      :from s
                      :to to
                      :direction direction
                      :desc desc2}]
          (if possible?
            result
            (assoc result :raw f :cut kind)))
        (when-let [s-matches (b-matches s)]
          (merge s-matches
                 (if (:reverse? s-matches)
                   {:from to
                    :to f}
                   {:from f
                    :to to})))))))

(defn match-f?
  [first-word parts]
  (= (first parts) first-word))

(def match-start?
  (partial match-f? "@startuml"))

(def match-end?
  (partial match-f? "@enduml"))

(def match-include?
  (partial match-f? "!include"))

(def match-skinparam?
  (partial match-f? "skinparam"))

(def match-type?
  (partial match-f? "sprite"))

(def match-rectangle?
  (partial match-f? "rectangle"))

(def element-fn-names
  (->> (mt/hierarchy :element)
       (remove (partial = :grouping))
       (map name)
       (map vcm/get-fn-name)
       (set)))

(defn match-element?
  [parts]
  (element-fn-names (first parts)))

(defn match-group?
  [parts]
  (or (match-f? "Group" parts)
      (match-f? "Grouping" parts)))

(defn match-connector?
  [parts]
  (or (match-f? "Junction_Or" parts)
      (match-f? "Junction_And" parts)))

(defn get-skinparam-target
  [parts]
  (let [tail (rest parts)]
    (if (empty? tail)
      [:default]
      (vec (cut-furs (first tail))))))

(defn get-type-kind
  [parts]
  (keyword (s/replace-first (last parts) "jar:archimate/" "")))

(defn get-type-block
  [parts]
  {:alias (second parts)
   :kind (get-type-kind parts)})

(defn strait-string
  [title]
  (s/replace title #"(?s)(\s|\\n)+" " "))

(defn get-rectangle-block
  [parts]
  (let [shape (first parts)
        title (cut-quotes (second parts))
        alias (nth parts 3 nil)
        type (nth parts 4 nil)
        skin (nth parts 5 nil)
        layer (last parts)
        [type3 skin2] (when type (cut-furs type))
        skin3 (if skin2 skin2 (when (fur? skin) (cut2 skin)))
        layer2 (if (or (= 2 (count parts))
                       (fur? layer))
                 nil
                 layer)
        color (when layer2
                (if (color? layer2) layer2 nil))
        layer3 (if color nil layer2)]
    {:shape shape
     :title title
     :name (strait-string title)
     :alias alias
     :type type3
     :skin skin3
     :layer layer3
     :color color}))

(defn get-layer-kind
  [fn-name]
  (let [parts (s/split fn-name #"_")
        layer (first parts)
        specie (csk/->kebab-case (last parts))]
    {:layer (str "#" layer)
     :kind (keyword (str (s/lower-case layer) "-" specie))}))

(defn get-element-block
  [parts]
  (let [[fn-name alias title color] parts
        title2 (cut-quotes title)]
    (merge (get-layer-kind fn-name)
           {:title title2
            :name (strait-string title2)
            :alias alias
            :type nil
            :color color})))

(defn get-group-block
  [parts]
  (let [[cap-type alias title color] parts
        title2 (cut-quotes title)]
    {:title title2
     :name (strait-string title2)
     :alias alias
     :type (keyword (s/lower-case cap-type))
     :kind :grouping
     :color color}))

(defn get-connector-block
  [parts]
  (let [[con-type alias title] parts
        title2 (cut-quotes title)
        type (-> con-type
                 (s/split #"_")
                 (second)
                 (s/lower-case)
                 (keyword))]
    {:title title2
     :name (strait-string title2)
     :alias alias
     :type type
     :kind :connector}))

(defn hidden-rel?
  [parts]
  (when-let [rel (second parts)]
    (s/includes? rel "[hidden]")))

(defn match-block
  [block]
  (let [parts (:parts block)
        line (:line block)
        body {:line line}
        result {:body body}
        rf (fn [& chain] (assoc result :in chain))
        bf (fn [chain extra-body] (-> (apply rf chain)
                                      (update :body merge extra-body)))
        match-inside #(assoc (% parts)
                             :inside
                             (map match-block (:props block)))]
    (cond
      (match-start? parts) (let [result2 (rf :start)]
                             (if (> (count parts) 1)
                               (assoc-in result2 [:body :title] (cut-quotes (second parts)))
                               result2))
      (match-end? parts) (rf :end)
      (match-include? parts) (let [package (cut1 (second parts))]
                               (bf [:includes package]
                                   {:package package}))
      (match-skinparam? parts) (let [targets (get-skinparam-target parts)
                                     shape (first targets)]
                                 (bf [:skins targets]
                                     {:shape (if (= :default shape) nil shape)
                                      :alias (second targets)
                                      :props (:props block)}))
      (match-type? parts) (let [type (get-type-block parts)]
                            (bf [:types (:alias type)] type))
      (match-rectangle? parts) (let [element (match-inside get-rectangle-block)]
                                 (bf [:elements (:alias element)] element))
      (match-element? parts) (let [element (match-inside get-element-block)]
                               (bf [:elements (:alias element)] element))
      (match-group? parts) (let [group (match-inside get-group-block)]
                             (bf [:elements (:alias group)] group))
      (match-connector? parts) (let [connector (get-connector-block parts)]
                                 (bf [:connectors (:alias connector)] connector))
      :else (if (hidden-rel? parts)
              (let [from (first parts)
                    raw (second parts)
                    to (last parts)]
                (bf [:hidden from to]
                    {:from from :to to :raw raw}))
              (if-let [rel (match-rel parts)]
                (bf [:relations (:from rel) (:to rel)] rel)
                (bf [:unknowns] {:parts parts}))))))

(defn append-matched-block
  [context block]
  (let [body (:body block)
        in (:in block)
        context-key (first in)
        err (fn [level kind bd]
              {:level level
               :kind kind
               :in in
               :body bd})
        add-err (fn [ctx level kind bd]
                  (update ctx :lints conj (err level kind bd)))
        add-ctx (fn [ctx bd]
                  (if-let [prev-bd (get-in ctx in)]
                    (if (and (set? prev-bd)
                             (set? bd)
                             (= 1 (count bd)))
                      (let [bd1 (first bd)
                            type (:type bd1)
                            ctx2 (if (and type
                                          (or (mt/relationship? type)
                                              (neg? (.indexOf (mapv :type prev-bd) type))))
                                   ctx
                                   (add-err ctx :error :duplicate bd1))]
                        (update-in ctx2 in conj bd1))
                      (-> ctx
                          (add-err :error :duplicate bd)
                          (update-in in merge bd)))
                    (assoc-in ctx in bd)))
        lint-ctx (fn [ctx bd checks]
                   (reduce (fn [cx [check [level kind] ctx-extra-check]]
                             (if (and (or (not ctx-extra-check)
                                          (ctx-extra-check cx))
                                      (check))
                               (add-err cx level kind bd)
                               cx))
                           ctx
                           checks))]
    (case context-key
      :unknowns (add-err context :error :unknown body)
      (:start :end :includes :skins :connectors) (add-ctx context body)
      :types (if (mt/element? (:kind body))
               (add-ctx context body)
               (-> context
                   (add-err :warn :unsupporting-element-type body)
                   (add-ctx body)))
      :elements (let [{type :type
                       kind :kind
                       skin :skin
                       layer :layer
                       inside :inside} body
                      alias (last in)
                      tkf #(get-in context [:types % :kind])
                      group? (= :grouping kind)
                      kind2 (or kind
                                (tkf type))
                      inside-comps? (some (comp #(or (:kind %) (tkf (:type %))) :body)
                                          inside)
                      specie-parts (when-not group?
                                     (when kind2
                                       (-> (name kind2)
                                           (s/split #"-"))))
                      specie (when (seq specie-parts)
                               (keyword (if (= 1 (count specie-parts))
                                          (first specie-parts)
                                          (s/join "-" (rest specie-parts)))))
                      inner-rel (if (or group?
                                        (= specie :collaboration))
                                  :aggregation
                                  :composition)
                      layer2 (when-not group?
                               (or (when layer
                                     (s/lower-case (s/join (rest layer))))
                                   (first specie-parts)))
                      layer3 (when (and layer2 (not= layer2 "location"))
                               (keyword layer2))
                      body2 (-> body
                                (u/assoc-if-not-nil :layer layer3)
                                (u/assoc-if-not-nil :specie specie)
                                (assoc :kind kind2)
                                (dissoc :inside))
                      archimate? (or group?
                                     (and kind2 inside-comps?))
                      checks (remove (comp nil? first)
                                     [[(when layer3
                                         #(not (mt/layer? layer3)))
                                       [:warn :unsupporting-element-layer]]
                                      [(when skin
                                         (let [shape (:shape body)]
                                           #(not (get-in context [:skins [shape skin]]))))
                                       [:warn :undefined-element-skin]]
                                      [#(and (not kind2) (not inside-comps?))
                                       [:error :undefined-element-type]]])
                      linted-ctx (lint-ctx context body2 checks)]
                  (reduce (fn [ctx inner-block]
                            (let [inner-alias (last (:in inner-block))
                                  ib (assoc-in inner-block [:body :in] alias)
                                  ext-ctx (append-matched-block ctx ib)]
                              (if archimate?
                                (append-matched-block ext-ctx
                                                      {:in [:relations alias inner-alias]
                                                       :body {:type inner-rel
                                                              :line (:line body)
                                                              :derivate :nesting
                                                              :from alias
                                                              :to inner-alias}})
                                ext-ctx)))
                          (add-ctx linted-ctx body2)
                          inside))
      (:relations
       :hidden) (let [{from :from
                       to :to
                       type :type} body
                      cf #(or (get-in context [:elements %])
                              (get-in context [:connectors %]))
                      from-c (cf from)
                      to-c (cf to)
                      from-kind (:kind from-c)
                      to-kind (:kind to-c)
                      body2 (-> body
                                (assoc :from (or from-kind from))
                                (assoc :to (or to-kind to))
                                (dissoc :cut)
                                (u/dissoc-if-nil :desc))
                      checks [[#(not (mt/relationship? type))
                               [:error :undefined-relation-type]
                               (fn [_] (not= :hidden context-key))]
                              [#(nil? from-c)
                               [:error :undefined-relation-from]]
                              [#(nil? to-c)
                               [:error :undefined-relation-to]]
                              [#(and (not (= :unknown type))
                                     (not (or (= :connector from-kind)
                                              (= :connector to-kind)))
                                     (not (contains? (get-in adx/total-relationships
                                                             [from-kind to-kind])
                                                     type)))
                               [:warn :unspecified-relation-type]
                               (fn [ctx]
                                 (and (not= :hidden context-key)
                                      (let [ll (last (:lints ctx))]
                                        (not
                                         (and (= in (:in ll))
                                              (#{:undefined-relation-from
                                                 :undefined-relation-to} (:kind ll)))))))]
                              [#(get-in context [context-key to from])
                               [:warn :relation-between-elements-already-present]]]
                      linted-ctx (lint-ctx context body2 checks)]
                  (add-ctx linted-ctx #{body2})))))

(defn append-block
  [context block]
  (if-let [matched-block (match-block block)]
    (append-matched-block context matched-block)
    context))

(defn process-blocks
  [blocks]
  (let [base (reduce append-block
                     {:start nil
                      :includes {}
                      :skins {}
                      :types {}
                      :elements {}
                      :connectors {}
                      :relations {}
                      :hidden {}
                      :lints []
                      :end nil}
                     blocks)
        connectors (:connectors base)]
    (if (empty? connectors)
      base
      (let [forward-graph (:relations base)
            reverse-graph (mg/reverse-graph forward-graph)]
        (->> (keys connectors)
             (map (juxt (comp keys reverse-graph)
                        (comp keys forward-graph)
                        (comp set
                              (partial map #(dissoc % :line :desc))
                              (partial apply concat)
                              vals
                              forward-graph)))
             (mapcat (partial apply combo/cartesian-product))
             (reduce (fn [acc [from to rel]]
                       (update-in acc [:relations from to] u/fnil-conj-set
                                  (assoc rel :derivate :connecting)))
                     base))))))

(defn get-connectors-diff-rels-info
  [context]
  (let [forward-graph (:relations context)
        reverse-graph (mg/reverse-graph forward-graph)
        grf (fn [graph alias]
              (->> (graph alias)
                   (vals)
                   (apply concat)
                   (map :type)))]
    (->> (:connectors context)
         (vals)
         (reduce (fn [acc connector]
                   (let [alias (:alias connector)
                         frs (grf forward-graph alias)
                         rrs (grf reverse-graph alias)
                         trs (set (concat frs rrs))
                         con2 (select-keys connector [:line :alias :type])
                         cfs (count frs)
                         crs (count rrs)]
                     (cond
                       (< 1 (count trs)) (conj acc {:connector con2
                                                    :error :different-using-relations
                                                    :relations trs})
                       (zero? cfs) (conj acc {:connector con2
                                              :error :no-outgoing-relations})
                       (zero? crs) (conj acc {:connector con2
                                              :error :no-incoming-relations})
                       (and (= 1 cfs)
                            (= 1 crs)) (conj acc {:connector con2
                                                  :error :excess-connector})
                       :else acc)))
                 [])
         (seq))))

(defn finalize
  [context]
  (let [checks [[#(not (:start %)) [:warn :missing-start]]
                [#(not (:end %)) [:warn :missing-end]]
                [#(not (get-in % [:includes "archimate/Archimate"]))
                 [:warn :missing-archimate-include]]
                [get-connectors-diff-rels-info
                 [:error :incorrect-connector-using true]]]]
    (reduce (fn [ctx [check [level kind append-info?]]]
              (if-let [result (check ctx)]
                (let [info {:level level :kind kind}
                      info2 (if append-info? (assoc info :data result) info)]
                  (update ctx :lints (partial concat [info2])))
                ctx))
            context
            checks)))

(defn analyze
  [blocks]
  (finalize (process-blocks blocks)))

(defn analyze-content
  [content]
  (analyze (get-blocks content)))
