(ns armate.archimate.metamodel.derivation.restrictions
  "Appendix B.4: Restrictions on Applying Derivation Rules
   https://pubs.opengroup.org/architecture/archimate3-doc/ch-relationships-Normative.html"
  (:require [armate.archimate.metamodel.meta :as mt]
            [armate.utils :as u]))

(def common-deps
  (merge-with into mt/hierarchy mt/domains))

(defn extend-types
  [rule]
  (mapv (fn [part]
          (->> (last part)
               (mapcat common-deps)
               (set)
               (u/replace-last part)))
        rule))

(def denies-abs
  (mapv extend-types
        [[[:a #{:implementation :core :strategy}]
          [:b #{:motivation}]
          [:s :not #{:assignment :realization :influence :association}]]
         [[:a #{:motivation}]
          [:b #{:implementation :core :strategy}]
          [:s :not #{:association}]]
         [[:a #{:implementation :core}]
          [:b #{:strategy}]
          [:s :not #{:realization :association}]]
         [[:a #{:strategy}]
          [:b #{:implementation :core}]
          [:s :not #{:association}]]
         [[:a #{:implementation}]
          [:b #{:core}]
          [:s :not #{:realization :association}]]
         [[:a #{:core}]
          [:b #{:implementation}]
          [:s :not #{:assignment :association}]]
         [[:a #{:grouping :location :implementation-plateau}]
          [:b #{:relationship}]
          [:s :not #{:composition :aggregation :association}]]
         [[:a :not #{:grouping :location :implementation-plateau}]
          [:b #{:relationship}]
          [:s :not #{:association}]]
         [[:a #{:relationship}]
          [:s :not #{:association}]]
         [[:b :not #{:motivation}]
          [:s #{:influence}]]
         [[:b :not #{:passive}]
          [:s #{:access :access_r :access_w :access_rw}]]
         [[:a :not #{:passive}]
          [:b #{:passive}]
          [:s :not #{:access :access_r :access_w :access_rw :assignment :association}]]
         [[:a #{:passive}]
          [:b #{:passive}]
          [:s :not #{:realization :association}]]
         [[:a #{:passive}]
          [:b :not #{:passive}]
          [:s :not #{:realization :influecne :association}]]]))

(defn same-domains?
  [a b c]
  (let [cd (mt/get-domain c)]
    (or (= (mt/get-domain a) cd)
        (= (mt/get-domain b) cd))))

(def allow-if-not-same-domains
  (extend-types
   [[:a #{:implementation}]
    [:b #{:motivation :strategy}]
    [:c #{:core}]]))

(def deny-abc
  (extend-types
   [[:a #{:implementation}]
    [:b #{:motivation :strategy}]
    [:c #{:grouping :location}]]))

; -----------------------------------------------------------------------------

(defn applying?
  [a b c s rule]
  (every? (fn [part]
            (let [target (case (first part)
                           :a a
                           :b b
                           :c c
                           :s s)
                  nf (if (= :not (second part)) not identity)
                  types (last part)]
              (nf (types target))))
          rule))

(defn restricted?
  [a b c s]
  (and (let [possible-types (get-in mt/general-relationships [a b])]
         (not (possible-types s)))
       (let [ar? (partial applying? a b c s)]
         (or (some ar? denies-abs)
             (not (or (same-domains? a b c)
                      (ar? allow-if-not-same-domains)))
             (ar? deny-abc)))))
