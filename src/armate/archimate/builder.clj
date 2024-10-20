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
      (s/replace #"=|-|~|:|#|\+|\*|\s|\(|\)|\[|\]|\{|\}|\?" "_")
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
  ([context misc-key patch-f name type-hm]
   (get-rectangle context misc-key patch-f nil name type-hm))
  ([context misc-key patch-f id name type-hm]
   (let [abrv (s/join (rest (:type type-hm)))
         alias (if id
                 (str abrv id)
                 (str (patch-f name) "_" abrv))]
     (if-let [element (check-cache context misc-key alias)]
       [context element]
       (let [element (-> (with-id type-hm id)
                         (merge {:shape "rectangle"
                                 :title (if (only-int? id)
                                          (cc id (subsplit name))
                                          (subsplit name))
                                 :name name
                                 :alias alias}))]
         [(-> context
              (assoc-in [:misc misc-key alias] element)
              (assoc-in [:elements alias] element))
          element])))))

(defn get-product
  ([context product-name]
   (get-product context nil product-name))
  ([context product-id product-name]
   (get-rectangle context :products
                  alias-title
                  product-id product-name
                  {:type "$bpd"
                   :kind :business-product
                   :specie :product
                   :layer :business})))

(defn get-bus-service
  ([context service-name]
   (get-bus-service context nil service-name))
  ([context service-id service-name]
   (get-rectangle context :bus-services
                  alias-title
                  service-id service-name
                  {:type "$bsv"
                   :kind :business-service
                   :specie :service
                   :layer :business})))

(defn get-app-service
  ([context service-name]
   (get-app-service context nil service-name))
  ([context service-id service-name]
   (get-rectangle context :app-services
                  alias-title
                  service-id service-name
                  {:type "$asv"
                   :kind :application-service
                   :specie :service
                   :layer :application})))

(defn get-interface
  ([context interface-name]
   (get-interface context nil interface-name {}))
  ([context interface-name add-params]
   (get-interface context nil interface-name add-params))
  ([context alias interface-name add-params]
   (get-rectangle context :interfaces
                  patch-raw-name
                  alias interface-name
                  (merge add-params
                         {:type "$ai"
                          :kind :application-interface
                          :specie :interface
                          :layer :application}))))

(defn get-component
  ([context component-name]
   (get-component context nil component-name {}))
  ([context alias component-name add-params]
   (get-rectangle context :components
                  patch-raw-name
                  alias component-name
                  (merge add-params
                         {:type "$acp"
                          :kind :application-component
                          :specie :component
                          :layer :application}))))

(defn get-app-collaboration
  [context collaboration-name]
  (get-rectangle context :app-collaborations
                 patch-raw-name
                 collaboration-name
                 {:type "$acb"
                  :kind :application-collaboration
                  :specie :collaboration
                  :layer :application}))

(defn get-software
  [context software-name]
  (get-rectangle context :software
                 patch-raw-name
                 software-name
                 {:type "$tss"
                  :kind :technology-system-software
                  :specie :system-software
                  :layer :technology}))

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
   :types {"$bpd" {:alias "$bpd" :kind :business-product}
           "$bsv" {:alias "$bsv" :kind :business-service}
           "$asv" {:alias "$asv" :kind :application-service}
           "$ai" {:alias "$ai" :kind :application-interface}
           "$acp" {:alias "$acp" :kind :application-component}
           "$acb" {:alias "$acb" :kind :application-collaboration}
           "$tss" {:alias "$tss" :kind :technology-system-software}}
   :skins {[:default] {:props [{:parts ["RoundCorner" "8"]}
                               {:parts ["Shadowing" "false"]}]}
           ["rectangle"] {:shape "rectangle"
                          :props [{:parts ["BorderThickness" "1"]}]}}
   :elements {}
   :relations {}
   :hidden {}})

(defn get-relation
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
