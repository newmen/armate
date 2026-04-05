(ns armate.archimate.builder
  (:require [clojure.string :as s]
            [armate.transliteration :as tl]
            [armate.utils :as u])
  (:import [java.time Instant]))

(def split-title? false)

(def max-alias-length 28)

(def title-generated-at-prefix
  "Generated at ")

(def kind-aliases
  {:motivation-assessment "ma"
   :motivation-constraint "mc"
   :motivation-driver "md"
   :motivation-goal "mg"
   :motivation-principle "mp"
   :motivation-requirement "mr"
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

(defn cc
  [id name]
  (if (and split-title? id)
    (str id "\\n" name)
    name))

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

(defn- convert-id
  [id]
  (try
    (Integer/parseInt id)
    (catch Exception _
      nil)))

(defn- with-id
  [type-hm id]
  (if (empty? id)
    type-hm
    (u/assoc-if-not-nil type-hm
                        :id
                        (convert-id id))))

(defn check-cache
  [context misc-key alias]
  (get-in context [:misc misc-key alias]))

(defn get-element-alias
  [patch-f id title kind]
  (let [abrv (kind-aliases kind)]
    (if id
      (str abrv (if (only-int? id)
                  id
                  (cut-too-long (* 2 max-alias-length) (patch-f id))))
      (str (patch-f title) "_" abrv))))

(defn add-rectangle
  ([context patch-f title type-hm]
   (add-rectangle context patch-f nil title type-hm))
  ([context patch-f id title kind-hm]
   (let [kind (:kind kind-hm)
         alias (get-element-alias patch-f id title kind)
         kind-parts (s/split (name kind) #"-")
         specie (keyword (s/join "-" (rest kind-parts)))
         layer (keyword (first kind-parts))
         default-params (assoc kind-hm :type (get-sprite-name kind))
         split-title (if (only-int? id)
                       (cc id (subsplit title))
                       (subsplit title))]
     (if-let [element (check-cache context kind alias)]
       [(update-in context [:elements alias] merge default-params)
        (merge element default-params)]
       (let [element (-> (with-id default-params id)
                         (merge {:shape "rectangle"
                                 :specie specie
                                 :layer layer
                                 :title (if (= "" split-title) " " split-title)
                                 :name title
                                 :alias alias}))]
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
           ["rectangle" "sub"] {:shape "rectangle"
                                :alias "sub"
                                :props [{:parts ["BackgroundColor" "#99d6ff"]}]}
           ["rectangle" "db"] {:shape "rectangle"
                               :alias "db"
                               :props [{:parts ["BackgroundColor" "#85c2ff"]}]}
           ["rectangle" "platform"] {:shape "rectangle"
                                     :alias "platform"
                                     :props [{:parts ["BackgroundColor" "#a6b2b5"]}
                                             {:parts ["FontColor" "#f1f3f1"]}]}
           ["rectangle" "deleting"] {:shape "rectangle"
                                     :alias "deleting"
                                     :props [{:parts ["BorderColor" "red"]}
                                             {:parts ["BorderThickness" "3"]}]}
           ["rectangle" "deprecated"] {:shape "rectangle"
                                       :alias "deprecated"
                                       :props [{:parts ["BorderColor" "orange"]}
                                               {:parts ["BorderThickness" "3"]}]}
           ["rectangle" "hold"] {:shape "rectangle"
                                 :alias "hold"
                                 :props [{:parts ["BorderColor" "green"]}
                                         {:parts ["BorderThickness" "2"]}]}
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
