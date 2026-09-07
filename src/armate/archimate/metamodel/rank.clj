(ns armate.archimate.metamodel.rank
  "The one ranking seam across the metamodel.

  Every sorting/ranking decision in the project reads its data from here: the layer
  order used by the renderer, the element-kind weights used by the aligner, and the
  relation weights used both by the renderer (output order) and by the derivation
  engine (rule strength). Both adapters — derive and render — observe the same weights.")

(def layer-order
  {:motivation 0
   :strategy 1
   :business 2
   :application 3
   :technology 4
   :implementation 5})

(def default-layer-rank 6)

(defn element-rank
  "The layer rank of an element (default when the layer is unknown)."
  [element]
  (layer-order (:layer element) default-layer-rank))

(def element-kinds-order
  [:business-actor
   :business-role
   :business-interaction
   :business-product
   :business-service
   :business-event
   :business-function
   :business-process
   :business-collaboration
   :application-service
   :application-data-object
   :application-interface
   :technology-system-software
   :application-component
   :application-collaboration
   :technology-artifact
   :technology-node
   :technology-collaboration
   :technology-path
   :technology-interaction])

(def weight-step 5)

(def kind-weights
  (->> (range)
       (map (partial * weight-step))
       (map inc)
       (zipmap element-kinds-order)))

(def dynamic-rel-weights
  "Dynamic relations ordered by strength (weakest to strongest)."
  {:triggering 1
   :flow 2})

(def dependency-rel-weights
  "Dependency relations ordered by strength (weakest to strongest)."
  {:association 100
   :association_dir 200
   :influence 300
   :access 400
   :access_r 500
   :access_w 600
   :access_rw 700
   :serving 800})

(def structural-rel-weights
  "Structural relations ordered by strength (weakest to strongest)."
  {:realization 1000
   :assignment 2000
   :aggregation 3000
   :composition 4000})

(def relation-weight-table
  "One pinned weight per relation keyword; the single source read by the renderer
   (output ordering) and the derivation engine (rule strength)."
  (merge dynamic-rel-weights
         dependency-rel-weights
         structural-rel-weights
         {:specialization 10000}))

(defn relation-weight
  "The numeric weight of a relation keyword."
  [rel]
  (if-let [weight (relation-weight-table rel)]
    weight
    (throw (ex-info "Unknown relation" {:rel rel}))))