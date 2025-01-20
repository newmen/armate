(ns armate.archimate.builder
  (:require [clojure.string :as s]
            [armate.transliteration :as tl]
            [armate.utils :as u])
  (:import [java.time Instant]))

(defn cc
  [id name]
  (if id
    (str id "\\n" name)
    name))

(defn patch-raw-name
  [raw-name]
  (-> raw-name
      (s/replace #"=|-|~|:|#|&|\+|\*|\s|\(|\)|\[|\]|\{|\}|\?" "_")
      (s/replace #"\.|," "__")
      (s/replace #"/" "___")))

(defn alias-title
  [raw-name]
  (-> (s/lower-case raw-name)
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

(defn get-rectangle
  ([context patch-f title type-hm]
   (get-rectangle context patch-f nil title type-hm))
  ([context patch-f id title type-hm]
   (let [type-name (:type type-hm)
         abrv (s/join (rest type-name))
         alias (if id
                 (str abrv id)
                 (str (patch-f title) "_" abrv))
         kind (get-in context [:types type-name :kind])
         kind-parts (s/split (name kind) #"-")
         specie (keyword (s/join (rest kind-parts)))
         layer (keyword (first kind-parts))]
     (if-let [element (check-cache context kind alias)]
       [context element]
       (let [element (-> (with-id type-hm id)
                         (merge {:shape "rectangle"
                                 :specie specie
                                 :kind kind
                                 :layer layer
                                 :title (if (only-int? id)
                                          (cc id (subsplit title))
                                          (subsplit title))
                                 :name title
                                 :alias alias}))]
         [(-> context
              (assoc-in [:misc kind alias] element)
              (assoc-in [:elements alias] element))
          element])))))

(defn get-actor
  [context actor-name]
  (get-rectangle context
                 alias-title actor-name
                 {:type "$ba"}))

(defn get-role
  [context role-name]
  (get-rectangle context
                 alias-title role-name
                 {:type "$br"}))

(defn get-product
  ([context product-name]
   (get-product context nil product-name))
  ([context product-id product-name]
   (get-rectangle context
                  alias-title
                  product-id product-name
                  {:type "$bpd"})))

(defn get-bus-service
  ([context service-name]
   (get-bus-service context nil service-name))
  ([context service-id service-name]
   (get-rectangle context
                  alias-title
                  service-id service-name
                  {:type "$bsv"})))

(defn get-app-service
  ([context service-name]
   (get-app-service context nil service-name))
  ([context service-id service-name]
   (get-rectangle context
                  alias-title
                  service-id service-name
                  {:type "$asv"})))

(defn get-interface
  ([context interface-name]
   (get-interface context nil interface-name {}))
  ([context interface-name add-params]
   (get-interface context nil interface-name add-params))
  ([context alias interface-name add-params]
   (get-rectangle context
                  patch-raw-name
                  alias interface-name
                  (merge add-params
                         {:type "$ai"}))))

(defn get-component
  ([context component-name]
   (get-component context nil component-name {}))
  ([context alias component-name add-params]
   (get-rectangle context
                  patch-raw-name
                  alias component-name
                  (merge add-params
                         {:type "$acp"}))))

(defn get-app-collaboration
  ([context collaboration-name]
   (get-app-collaboration context collaboration-name {}))
  ([context collaboration-name add-params]
   (get-rectangle context
                  patch-raw-name
                  collaboration-name
                  (merge {:skin "platform"}
                         add-params
                         {:type "$acb"}))))

(defn get-software
  [context software-name]
  (get-rectangle context
                 patch-raw-name
                 software-name
                 {:type "$tss"}))

(def init-context
  {:start {:title (str "Generated at " (Instant/now))}
   :misc {:products {}
          :bus-services {}
          :app-services {}
          :interfaces {}
          :components {}
          :app-collaborations {}
          :software {}} ; a cache of already created elements
   :includes {"archimate/Archimate" {:package "archimate/Archimate"}}
   :types {"$ba" {:alias "$ba" :kind :business-actor}
           "$br" {:alias "$br" :kind :business-role}
           "$bpd" {:alias "$bpd" :kind :business-product}
           "$bsv" {:alias "$bsv" :kind :business-service}
           "$asv" {:alias "$asv" :kind :application-service}
           "$ai" {:alias "$ai" :kind :application-interface}
           "$acp" {:alias "$acp" :kind :application-component}
           "$acb" {:alias "$acb" :kind :application-collaboration}
           "$tss" {:alias "$tss" :kind :technology-system-software}}
   :skins {[:default] {:props [{:parts ["RoundCorner" "8"]}
                               {:parts ["Shadowing" "false"]}]}
           ["rectangle"] {:shape "rectangle"
                          :props [{:parts ["BorderThickness" "1"]}]}
           ["rectangle" "sub"] {:shape "rectangle"
                                :alias "sub"
                                :props [{:parts ["backgroundColor" "#99d6ff"]}]}
           ["rectangle" "db"] {:shape "rectangle"
                               :alias "db"
                               :props [{:parts ["backgroundColor" "#85c2ff"]}]}
           ["rectangle" "platform"] {:shape "rectangle"
                                     :alias "platform"
                                     :props [{:parts ["backgroundColor" "#a6b2b5"]}
                                             {:parts ["fontColor" "#f1f3f1"]}]}}
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
      (let [[ctx2 software] (get-software context software-name)
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
