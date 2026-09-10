(ns armate.archimate.viz.support
  "Shared helpers for constructing minimal on-the-fly render contexts and
   relation maps in viz tests."
  (:require [clojure.set :as set]))

(defn ctx
  "A minimal renderable context from a sequence of :alias-keyed @elements and a
   @relations map `{from {to #{rel}}}`, with a placeholder :start title."
  [elements relations]
  {:start {:title "t"}
   :elements (into {} (map (juxt :alias identity)) elements)
   :relations relations})

(defn- rels
  ([from to type]
   (rels from to type nil))
  ([from to type derivate]
   {from {to #{{:type type :derivate derivate}}}}))

(defn edge
  "A single relationship map `{from {to #{rel}}}` between two aliases."
  [from to type]
  (rels from to type))

(defn derived-edge
  "A single :derivate-carrying relationship `{from {to #{rel}}}`."
  [from to type derivate]
  (rels from to type derivate))

(defn combine-rels
  "Merge any number of relation maps, unioning the relation sets on a shared edge."
  [& ms]
  (reduce (fn [acc m]
            (merge-with (fn [a b] (merge-with set/union a b)) acc m))
          {} ms))