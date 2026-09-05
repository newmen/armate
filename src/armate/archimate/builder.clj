(ns armate.archimate.builder
  (:require [clojure.string :as s]
            [armate.transliteration :as tl]
            [armate.utils :as u])
  (:import [java.time Instant]))

(def split-title? false)
(def max-alias-length 28)
(def title-max-length 12)

(defn title-separate
  ([string]
   (title-separate title-max-length string))
  ([max-length string]
   (->> (s/trim string)
        (re-seq #"\\n|[\/\#\[\(]?[A-Za-z0-9А-Яа-яЁё_\-]+[,:\]\)]?|\/?\{\w+\}|\s*[\=\+\*]\s*|\s*[\s\.~\?]")
        (mapcat (fn [part]
                  (if (< max-length (count part))
                    (re-seq #"[\/\#\[\(]?[A-Za-z0-9А-Яа-яЁё,]+[\]\)]?|[:_-]" part)
                    [part]))))))

(defn title-split-long-camel-part
  [string]
  (let [first-re #"^[\/#]?[a-zа-я0-9ё]*"
        first-word (re-find first-re string)
        tail-words (re-seq #"[A-ZА-ЯЁ][a-zа-я0-9ё]+"
                           (s/replace-first string first-re ""))]
    (if (empty? first-word)
      tail-words
      (cons first-word tail-words))))

(defn title-split-long-part
  [string]
  (let [camel-parts (title-split-long-camel-part string)]
    (if (seq camel-parts)
      camel-parts
      [string])))

(defn title-sqrt-length
  [string]
  (int (Math/ceil (* 2 (Math/sqrt (count string))))))

(defn subsplit
  [string]
  (let [mlh (max (title-sqrt-length string) title-max-length)]
    (loop [acc []
           parts (title-separate mlh string)]
      (if (empty? parts)
        (->> (map s/trim acc)
             (remove empty?)
             (map (fn [part]
                    (if (s/starts-with? part "#")
                      (str " " part)
                      part)))
             (s/join "\\n"))
        (let [part (first parts)
              tail (rest parts)
              prev (last acc)
              curr-length (count prev)
              part-length (count part)
              dsub-parts (delay (title-split-long-part part))]
          (if (= "\\n" part)
            (recur (conj acc "") tail)
            (if (> (+ curr-length part-length) mlh)
              (if (and (> part-length mlh)
                       (< 1 (count @dsub-parts)))
                (if (>= 2 curr-length)
                  (recur (u/replace-last acc (str prev (first @dsub-parts)))
                         (concat (rest @dsub-parts) tail))
                  (recur acc (concat @dsub-parts tail)))
                (recur (conj acc part) tail))
              (recur (u/replace-last acc (str prev part))
                     tail))))))))

(def title-generated-at-prefix
  "Generated at ")

(def kind-aliases
  {:motivation-assessment "ma"
   :motivation-constraint "mc"
   :motivation-driver "md"
   :motivation-goal "mg"
   :motivation-outcome "mo"
   :motivation-principle "mp"
   :motivation-requirement "mr"
   :motivation-stakeholder "ms"
   :business-actor "ba"
   :business-role "brl"
   :business-collaboration "bcb"
   :business-contract "bc"
   :business-object "bo"
   :business-interface "bif"
   :business-event "be"
   :business-function "bfn"
   :business-process "bpc"
   :business-interaction "bin"
   :business-service "bsv"
   :business-product "bpd"
   :application-component "acp"
   :application-collaboration "acb"
   :application-data-object "ado"
   :application-interface "aif"
   :application-event "ae"
   :application-function "afn"
   :application-process "apc"
   :application-interaction "ain"
   :application-service "asv"
   :technology-artifact "ta"
   :technology-collaboration "tcb"
   :technology-communication-network "tcn"
   :technology-event "te"
   :technology-node "tn"
   :technology-system-software "tss"
   :technology-function "tfn"
   :technology-process "tpc"
   :technology-service "tsv"
   :implementation-deliverable "idv"
   :implementation-workpackage "iwp"})

(defn get-sprite-name
  [kind]
  (when-let [alias (kind-aliases kind)]
    (str "$" alias)))

(defn escape-special-chars
  [raw-name]
  (s/replace raw-name #"[\"']" ""))

(defn patch-raw-name
  [raw-name]
  (-> raw-name
      (escape-special-chars)
      (s/replace #"=|-|~|:|#|&|%|\$|\+|\*|\s|\(|\)|\[|\]|\{|\}|\?" "_")
      (s/replace #"\.|," "__")
      (s/replace #"/" "___")))

(defn cut-too-long
  ([raw-name]
   (if (> (count raw-name) max-alias-length)
     (subs raw-name 0 max-alias-length)
     raw-name))
  ([max-alias-length raw-name]
   (if (> (count raw-name) max-alias-length)
     (subs raw-name 0 max-alias-length)
     raw-name)))

(defn alias-title
  [raw-name]
  (-> (s/lower-case raw-name)
      (cut-too-long)
      (tl/transliterate)
      (patch-raw-name)))

(defn only-int?
  [string]
  (when string
    (re-matches #"^\d+$" string)))

(defn check-cache
  [context misc-key alias]
  (get-in context [:misc misc-key alias]))

(defn get-element-alias
  [patch-f id title kind]
  (let [abrv (kind-aliases kind)]
    (if (only-int? id)
      (str abrv id)
      (str (patch-f title) "_" abrv))))

(defn add-rectangle
  ([context patch-f title kind-hm]
   (add-rectangle context patch-f nil title kind-hm))
  ([context patch-f id title kind-hm]
   (let [kind (:kind kind-hm)
         alias (get-element-alias patch-f id title kind)
         kind-parts (s/split (name kind) #"-")
         specie (keyword (s/join "-" (rest kind-parts)))
         layer (keyword (first kind-parts))
         default-params (assoc kind-hm :type (get-sprite-name kind))
         split-title (if split-title? (subsplit title) title)]
     (if-let [element (check-cache context kind alias)]
       [(update-in context [:elements alias] merge default-params)
        (merge element default-params)]
       (let [element (merge default-params
                            {:shape "rectangle"
                             :specie specie
                             :layer layer
                             :title (if (= "" split-title) " " split-title)
                             :name title
                             :alias alias})]
         [(-> context
              (assoc-in [:misc kind alias] element)
              (assoc-in [:elements alias] element))
          element])))))

(defn add-element
  [context kind id name]
  (add-rectangle context
                 alias-title
                 id
                 name
                 {:kind kind}))

(defn add-actor
  [context actor-name]
  (add-element context :business-actor nil actor-name))

(defn add-role
  [context role-name]
  (add-element context :business-role nil role-name))

(defn add-product
  ([context product-name]
   (add-product context nil product-name))
  ([context product-id product-name]
   (add-element context :business-product product-id product-name)))

(defn add-bus-service
  ([context service-name]
   (add-bus-service context nil service-name))
  ([context service-id service-name]
   (add-element context :business-service service-id service-name)))

(defn add-app-service
  ([context service-name]
   (add-app-service context nil service-name))
  ([context service-id service-name]
   (add-element context :application-service service-id service-name)))

(defn add-interface
  ([context interface-name]
   (add-interface context nil interface-name {}))
  ([context interface-name add-params]
   (add-interface context nil interface-name add-params))
  ([context alias interface-name add-params]
   (add-rectangle context
                  patch-raw-name
                  alias interface-name
                  (merge add-params
                         {:kind :application-interface}))))

(defn get-interface
  [context interface-name]
  (let [alias (get-element-alias patch-raw-name nil
                                 interface-name :application-interface)]
    (get-in context [:elements alias])))

(defn add-component
  ([context component-name]
   (add-component context nil component-name {}))
  ([context alias component-name add-params]
   (add-rectangle context
                  alias-title
                  alias component-name
                  (merge add-params
                         {:kind :application-component}))))

(defn add-app-collaboration
  ([context collaboration-name]
   (add-app-collaboration context collaboration-name {}))
  ([context collaboration-name add-params]
   (add-rectangle context
                  alias-title
                  collaboration-name
                  (merge {:skin "platform"}
                         add-params
                         {:kind :application-collaboration}))))

(defn add-software
  [context software-name]
  (add-rectangle context
                 patch-raw-name
                 software-name
                 {:kind :technology-system-software}))

(defn add-grouping
  ([context group-name]
   (let [title (escape-special-chars group-name)
         alias (str (alias-title title) "_g")]
     (add-grouping context alias group-name)))
  ([context alias-or-id group-name]
   (let [kind :grouping
         title (escape-special-chars group-name)
         alias (if (only-int? alias-or-id)
                 (str "g" alias-or-id)
                 alias-or-id)]
     (if-let [element (check-cache context kind alias)]
       [context element]
       (let [element (-> (merge {:kind kind
                                 :type kind
                                 :title title
                                 :name title
                                 :alias alias}))]
         [(-> context
              (assoc-in [:misc kind alias] element)
              (assoc-in [:elements alias] element))
          element])))))

(defn add-connector
  ([context type junction-name]
   (let [title (escape-special-chars junction-name)
         alias (str (alias-title title) "_jc")]
     (add-connector context type alias junction-name)))
  ([context type alias-or-id junction-name]
   (let [kind :connector
         title (escape-special-chars junction-name)
         alias (if (only-int? alias-or-id)
                 (str "jc" alias-or-id)
                 alias-or-id)]
     (if-let [connector (check-cache context kind alias)]
       [context connector]
       (let [connector (-> (merge {:kind kind
                                   :type type
                                   :title (subsplit title)
                                   :name title
                                   :alias alias}))]
         [(-> context
              (assoc-in [:misc kind alias] connector)
              (assoc-in [:connectors alias] connector))
          connector])))))

(def init-context
  {:start {:title (str title-generated-at-prefix (Instant/now))}
   :misc {} ; a cache of already created elements
   :includes {"archimate/Archimate" {:package "archimate/Archimate"}}
   :types (into {} (map (fn [[kind _]]
                          (let [type-name (get-sprite-name kind)]
                            [type-name {:alias type-name :kind kind}]))
                        kind-aliases))
   :skins {[:default] {:props [{:parts ["Shadowing" "false"]}]}
           ["rectangle"] {:shape "rectangle"
                          :props [{:parts ["BorderThickness" "1"]}]}
           ["folder" "grouping"] {:shape "folder"
                                  :alias "grouping"
                                  :props [{:parts ["Shadowing" "false"]}]}}
   :elements {}
   :connectors {}
   :relations {}
   :hidden {}})

(defn- get-relation
  [context from to type params]
  (let [key [:relations (:alias from) (:alias to)]
        relation (merge params
                        {:from (:kind from)
                         :to (:kind to)
                         :type type})]
    (if (contains? (get-in context key) relation)
      context
      (update-in context key u/fnil-conj-set relation))))

(defn add-relation
  ([context from to type direction]
   (add-relation context from to type direction nil))
  ([context from to type direction desc]
   (let [params (if direction {:direction direction} {})
         params2 (u/assoc-if-not-nil params :desc desc)]
     (get-relation context from to type params2))))

(defn add-nesting-relation
  [context from to type]
  (get-relation context from to type {:derivate :nesting}))

(def proto-names-map
  {:grpc "gRPC"
   :rest "REST"})

(def system-lang-names-map
  {:cql "Cassandra"
   :sql "PostgreSQL"})

(defn add-technology-element
  [context source-element in-keys names-map]
  (when-let [type (get-in source-element in-keys)]
    (when-let [software-name (names-map type)]
      (let [[ctx2 software] (add-software context software-name)
            rel-type (case (:kind source-element)
                       :application-component :realization
                       :application-interface :serving)]
        (add-relation ctx2 software source-element rel-type :up)))))

(defn add-proto-element
  [context element]
  (add-technology-element context element
                          [:attributes :proto] proto-names-map))

(defn add-system-element
  [context element]
  (add-technology-element context element
                          [:attributes :language] system-lang-names-map))

(defn add-software-elements
  [context]
  (->> (vals (:elements context))
       (reduce (fn [acc element]
                 (or (add-proto-element acc element)
                     (add-system-element acc element)
                     acc))
               context)))
