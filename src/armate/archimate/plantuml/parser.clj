(ns armate.archimate
  (:require [clojure.string :as s]))

(def call-re
  #"^([A-Za-z_]+)\s*\(([A-Za-z0-9_]+)\s*,\s*([A-Za-z0-9_]+)(?:\s*,\s*(.+?))?\)$")

(def quoted-split-re
  #"(\"[^\"]+\"|[^\"\s]+)")

(defn quoted-split
  [line]
  (map second (re-seq quoted-split-re line)))

(defn get-parts
  [line]
  (let [parts (quoted-split line)]
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

(defn get-blocks
  [content]
  (let [lines (s/split-lines content)
        indexes (map inc (range))]
    (loop [lines-is (map vector lines indexes)
           blocks []
           outer-block nil]
      (if (empty? lines-is)
        (if outer-block
          (throw (ex-info "Unclosed block" {:block outer-block}))
          blocks)
        (let [line-i (first lines-is)
              line (s/trim (first line-i))
              index (second line-i)
              rest-lines-is (rest lines-is)]
          (if (empty? line)
            (recur rest-lines-is blocks outer-block)
            (let [{parts :parts
                   block? :block?} (get-parts line)]
              (if outer-block
                (do (when block?
                      (throw (ex-info "Unsupporting block nesting: "
                                      {:block outer-block
                                       :inner parts
                                       :line index})))
                    (if (= line "}")
                      (recur rest-lines-is
                             (conj blocks outer-block)
                             nil)
                      (recur rest-lines-is
                             blocks
                             (update outer-block :props
                                     (fnil conj []) {:parts parts
                                                     :line index}))))
                (if block?
                  (recur rest-lines-is
                         blocks
                         {:parts parts
                          :line index})
                  (recur rest-lines-is
                         (conj blocks {:parts parts
                                       :line index})
                         nil))))))))))

(defn cut1
  [string]
  (subs string 1 (dec (count string))))

(defn cut2
  [string]
  (cut1 (cut1 string)))

(def possible-relation-types
  #{:access
    :access_r
    :access_rw
    :access_w
    :aggregation
    :assignment
    :association
    :composition
    :flow
    :influence
    :realization
    :serving
    :specialization
    :triggering})

(def rel-f-re
  #"(?i)^Rel_(.+?)(?:_(?:Up|Down|Left|Right))?$")

(def rel-b-res
  [#"(?i)(.*?\.+)(?:up|down|left|right)(\.+.*)"
   #"(?i)(.*?-+)(?:up|down|left|right)(-+.*)"
   #"(.*?[\.\-]+)(.*)"])

(defn match-rel-b
  [dir-bond]
  (some #(re-matches % dir-bond) rel-b-res))

(def pin-re
  #"([^\.\-]*)?([\.\-]+)([^\.\-]*)?")

(defn match-pos-bond
  [mbond]
  (case mbond
    (["o" \- ""]
     ["O" \- ""]) {:type :aggregation}
    (["" \- "o"]
     ["" \- "O"]) {:type :aggregation :reverse? true}
    ["" \- ""] {:type :association}
    ["*" \- ""] {:type :composition}
    ["" \- "*"] {:type :composition :reverse? true}
    ["" \. ">>"] {:type :flow}
    ["<<" \. ""] {:type :flow :reverse? true}
    ["" \. ">"] {:type :influence}
    ["<" \. ""] {:type :influence :reverse? true}
    ["" \- ">"] {:type :serving}
    ["<" \- ""] {:type :serving :reverse? true}
    (["" \- "|>"]
     ["" \- "^"]) {:type :specialization}
    (["<|" \- ""]
     ["^" \- ""]) {:type :specialization :reverse? true}
    ["" \- ">>"] {:type :triggering}
    ["<<" \- ""] {:type :triggering :reverse? true}
    {:type :unknown}))

(defn b-matches
  [dir-bond]
  (when-let [matches (match-rel-b dir-bond)]
    (let [bond (str (second matches) (last matches))
          [_ left body right] (re-matches pin-re bond)
          bc (first body)
          mbond [left bc right]]
      (assoc (match-pos-bond mbond)
             :raw dir-bond
             :cut (apply str mbond)))))

(defn match-rel
  [parts]
  (when (> (count parts) 2)
    (let [[f s to desc] parts
          f-matches (re-matches rel-f-re f)]
      (if f-matches
        (let [kind (keyword (s/lower-case (second f-matches)))
              possible? (possible-relation-types kind)
              type (if possible? kind :unknown)
              result {:type type
                      :from s
                      :to to
                      :desc desc}]
          (if possible?
            result
            (assoc result :raw f :cut kind)))
        (when-let [s-matches (b-matches s)]
          (merge s-matches
                 {:desc desc}
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

(def match-component?
  (partial match-f? "rectangle"))

(defn fur?
  [part]
  (when part
    (and (s/starts-with? part "<<")
         (s/ends-with? part ">>"))))

(def fur-re
  #"(<<[^>]+>>|[^<>]+)")

(defn cut-fur
  [part]
  (->> (re-seq fur-re part)
       (map second)
       (map #(if (fur? %) (cut2 %) %))))

(defn get-skinparam-target
  [parts]
  (let [tail (rest parts)]
    (if (empty? tail)
      [:default]
      (cut-fur (first tail)))))

(defn get-type-kind
  [parts]
  (keyword (s/replace-first (last parts) "jar:archimate/" "")))

(defn get-type-block
  [parts]
  {:kind (get-type-kind parts)})

(defn get-component-block
  [parts]
  (let [[_ title _as _alias type skin meta] parts
        [type3 skin2] (cut-fur type)
        skin3 (if skin2 skin2 (when (fur? skin) (cut2 skin)))
        meta3 (if meta meta (when-not (fur? skin) skin))]
    {:title title
     :type type3
     :skin skin3
     :meta meta3}))

(defn match-block
  [block]
  (let [parts (:parts block)
        line (:line block)
        body {:line line}
        result {:body body}
        rf (fn [& chain] (assoc result :in chain))
        bf (fn [chain extra-body] (-> (apply rf chain)
                                      (update :body merge extra-body)))]
    (cond
      (match-start? parts) (rf :start)
      (match-end? parts) (rf :end)
      (match-include? parts) (rf :includes (cut1 (second parts)))
      (match-skinparam? parts) (bf (cons :skins (get-skinparam-target parts))
                                   {:props (:props block)})
      (match-type? parts) (bf [:types (second parts)]
                              (get-type-block parts))
      (match-component? parts) (bf [:components (nth parts 3)]
                                   (get-component-block parts))
      :else (if-let [rel (match-rel parts)]
              (bf [:relations (:from rel) (:to rel)] rel)
              (bf [:unknowns] {:parts parts})))))

(def possible-connections
  {:application-collaboration {:application-component #{:aggregation}
                               :application-interface #{:composition :flow}}
   :application-component {:application-component #{:composition}
                           :application-data-object #{:access :access_r :access_rw :access_w}
                           :application-function #{:assignment}
                           :application-interface #{:composition :flow :triggering}
                           :application-service #{:realization}}
   :application-data-object {:application-data-object #{:aggregation :association :composition :specialization}}
   :application-function {:application-service #{:realization}}
   :application-interface {:application-service #{:assignment}
                           :application-component #{:flow}}
   :application-service {:business-function #{:serving}
                         :business-process #{:serving}
                         :business-service #{:realization :serving}}
   :business-actor {:business-actor #{:composition}
                    :business-role #{:assignment}}
   :business-collaboration {:business-interaction #{:assignment}
                            :business-role #{:aggregation}}
   :business-event {:business-event #{:composition :triggering}
                    :business-function #{:triggering}
                    :business-interaction #{:triggering}
                    :business-process #{:triggering}}
   :business-function {:business-event #{:triggering}
                       :business-function #{:composition :triggering}
                       :business-interaction #{:composition :triggering}
                       :business-process #{:composition :triggering}
                       :business-service #{:realization}}
   :business-process {:business-event #{:triggering}
                      :business-function #{:composition :triggering}
                      :business-interaction #{:composition :triggering}
                      :business-process #{:composition :triggering}
                      :business-service #{:realization}}
   :business-role {:business-function #{:assignment}
                   :business-process #{:assignment}
                   :business-role #{:specialization}}})

(def possible-components
  (set (concat (keys possible-connections)
               (mapcat keys (vals possible-connections)))))

(defn dissoc-if-nil
  [hm & ks]
  (apply dissoc hm (filter #(nil? (hm %)) ks)))

(defn append-block
  [context block]
  (let [matched-block (match-block block)
        body (:body matched-block)
        in (:in matched-block)
        context-key (first in)
        err (fn [level kind bd]
              {:level level
               :kind kind
               :in in
               :body bd})
        add-err (fn [ctx level kind bd]
                  (update ctx :lints conj (err level kind bd)))
        add-ctx (fn [ctx bd]
                  (if (get-in ctx in)
                    (-> ctx
                        (add-err :error :duplicate bd)
                        (update-in in merge bd))
                    (assoc-in ctx in bd)))
        lint-ctx (fn [ctx bd checks]
                   (reduce (fn [cx [check [level kind]]]
                             (if (check) (add-err cx level kind bd) cx))
                           ctx
                           checks))]
    (case context-key
      :unknown (add-err context :error :unknown body)
      (:start :end :includes :skins) (add-ctx context body)
      :types (if (possible-components (:kind body))
               (add-ctx context body)
               (-> context
                   (add-err :warn :unsupporting-component-type body)
                   (add-ctx body)))
      :components (let [{type :type
                         skin :skin
                         meta :meta} body
                        kind (get-in context [:types type :kind])
                        body2 (assoc body :kind kind)
                        checks (remove (comp nil? first)
                                       [[(when meta
                                           #(not (#{"#Application" "#Business"} meta)))
                                         [:warn :unsupporting-component-meta]]
                                        [(when skin
                                           (let [first-word (first (:parts block))]
                                             #(not (get-in context
                                                           [:skins first-word skin]))))
                                         [:warn :undefined-component-skin]]
                                        [#(nil? kind)
                                         [:error :undefined-component-type]]])
                        linted-ctx (lint-ctx context body2 checks)]
                    (add-ctx linted-ctx body2))
      :relations (let [{from :from
                        to :to
                        type :type} body
                       from-c (get-in context [:components from])
                       to-c (get-in context [:components to])
                       from-kind (:kind from-c)
                       to-kind (:kind to-c)
                       body2 (-> body
                                 (assoc :from (or from-kind from))
                                 (assoc :to (or to-kind to))
                                 (dissoc :cut)
                                 (dissoc-if-nil :desc))
                       checks [[#(not (possible-relation-types type))
                                [:error :undefined-relation-type]]
                               [#(nil? from-c)
                                [:error :undefined-relation-from]]
                               [#(nil? to-c)
                                [:error :undefined-relation-to]]
                               [#(and (not (= :unknown type))
                                      (not (contains? (get-in possible-connections
                                                              [from-kind to-kind])
                                                      type)))
                                [:warn :unspecified-relation-type]]
                               [#(get-in context [:relations to from])
                                [:warn :relation-between-components-already-present]]]
                       linted-ctx (lint-ctx context body2 checks)]
                   (add-ctx linted-ctx body2)))))

(defn finalize
  [context]
  (let [checks [[#(get-in context [:includes "archimate/Archimate"])
                 [:warn :missing-archimate-include]]
                [#(:end context)
                 [:warn :missing-end]]
                [#(:start context)
                 [:warn :missing-start]]]]
    (reduce (fn [ctx [check [level kind]]]
              (if (not (check))
                (update ctx :lints (partial concat [{:level level :kind kind}]))
                ctx))
            context
            checks)))

(defn analyze
  [blocks]
  (finalize (reduce append-block
                    {:start nil
                     :includes {}
                     :skins {}
                     :types {}
                     :components {}
                     :relations {}
                     :lints []
                     :end nil}
                    blocks)))

(defn lint-content
  [content]
  (let [blocks (get-blocks content)]
    (:lints (analyze blocks))))

(defn lint
  [file-path]
  (let [content (slurp file-path)]
    (if (empty? content)
      (throw (ex-info "File is empty" {:file-path file-path}))
      (lint-content content))))

(comment
  
  (lint "../operations/operations.wsd")
  (lint "../test-archimate.wsd")

  )