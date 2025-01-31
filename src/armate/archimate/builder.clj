(ns armate.archimate.builder
  (:require [clojure.string :as s]
            [armate.transliteration :as tl]
            [armate.utils :as u])
  (:import [java.time Instant]))

(def max-alias-length 28)

(def kind-aliases
  {:business-actor "ba"
   :business-role "brl"
   :business-service "bsv"
   :business-product "bpd"
   :application-interface "aif"
   :application-component "acp"
   :application-collaboration "acb"
   :application-service "asv"
   :technology-system-software "tss"})

(defn get-sprite-name
  [kind]
  (when-let [alias (kind-aliases kind)]
    (str "$" alias)))

(defn cc
  [id name]
  (if id
    (str id "\\n" name)
    name))

(defn patch-raw-name
  [raw-name]
  (-> raw-name
      (s/replace #"[\"']" "")
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
         specie (keyword (s/join (rest kind-parts)))
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

(defn add-actor
  [context actor-name]
  (add-rectangle context
                 alias-title actor-name
                 {:kind :business-actor}))

(defn add-role
  [context role-name]
  (add-rectangle context
                 alias-title role-name
                 {:kind :business-role}))

(defn add-product
  ([context product-name]
   (add-product context nil product-name))
  ([context product-id product-name]
   (add-rectangle context
                  alias-title
                  product-id product-name
                  {:kind :business-product})))

(defn add-bus-service
  ([context service-name]
   (add-bus-service context nil service-name))
  ([context service-id service-name]
   (add-rectangle context
                  alias-title
                  service-id service-name
                  {:kind :business-service})))

(defn add-app-service
  ([context service-name]
   (add-app-service context nil service-name))
  ([context service-id service-name]
   (add-rectangle context
                  alias-title
                  service-id service-name
                  {:kind :application-service})))

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
  [context group-name]
  (let [kind :grouping
        title (s/replace group-name #"[\"']" "")
        alias (str (alias-title title) "_g")]
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
         element]))))

(def init-context
  {:start {:title (str "Generated at " (Instant/now))}
   :misc {} ; a cache of already created elements
   :includes {"archimate/Archimate" {:package "archimate/Archimate"}}
   :types (into {} (map (fn [[kind _]]
                          (let [type-name (get-sprite-name kind)]
                            [type-name {:alias type-name :kind kind}]))
                        kind-aliases))
   :skins {[:default] {:props [{:parts ["RoundCorner" "8"]}
                               {:parts ["Shadowing" "false"]}]}
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
   (let [params (u/assoc-if-not-nil {:direction direction}
                                    :desc desc)]
     (get-relation context from to type params))))

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
