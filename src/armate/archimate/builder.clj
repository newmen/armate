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
      (s/replace #"-|#|\+|\*|\s|\(|\)|\[|\]|\?" "_")
      (s/replace #"\." "__")
      (s/replace #"/" "___")))

(defn alias-title
  [raw-name]
  (-> (s/lower-case raw-name)
      (tl/transliterate)
      (patch-raw-name)))

(defn get-rectangle
  ([context misc-key patch-f name type-hm]
   (get-rectangle context misc-key patch-f nil name type-hm))
  ([context misc-key patch-f id name type-hm]
   (let [abrv (s/join (rest (:type type-hm)))
         alias (if id
                 (str abrv id)
                 (str (patch-f name) "_" abrv))]
     (if-let [service (get-in context [:misc misc-key alias])]
       [context service]
       (let [service (merge type-hm
                            (if id
                              {:id (try
                                           (Integer/parseInt id)
                                           (catch Exception _
                                             id))}
                              {})
                            {:shape "rectangle"
                             :title (cc id (subsplit name))
                             :name name
                             :alias alias})]
         [(-> context
              (assoc-in [:misc misc-key alias] service)
              (assoc-in [:elements alias] service))
          service])))))

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
  [context interface-name]
  (get-rectangle context :interfaces
                 patch-raw-name
                 interface-name
                 {:type "$ai"
                  :kind :application-interface
                  :specie :interface
                  :layer :application}))

(defn get-component
  [context component-name]
  (get-rectangle context :components
                 patch-raw-name
                 component-name
                 {:type "$acp"
                  :kind :application-component
                  :specie :component
                  :layer :application}))

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
           "$tss" {:alias "$tss" :kind :technical-system-software}}
   :skins {[:default] {:props [{:parts ["RoundCorner" "8"]}
                               {:parts ["Shadowing" "false"]}]}
           ["rectangle"] {:shape "rectangle"
                          :props [{:parts ["BorderThickness" "1"]}]}}
   :elements {}
   :relations {}
   :hidden {}})

(defn add-relation
  ([context from to type direction]
   (add-relation context from to type direction nil))
  ([context from to type direction desc]
   (let [key [:relations (:alias from) (:alias to)]
         relation {:from (:kind from)
                   :to (:kind to)
                   :type type
                   :direction direction}
         relation2 (if desc
                     (assoc relation :desc desc)
                     relation)]
     (if (contains? (get-in context key) relation2)
       context
       (update-in context key u/fnil-conj-set relation2)))))
