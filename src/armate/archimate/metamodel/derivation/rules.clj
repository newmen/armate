(ns armate.derivation.rules
  "Appendix B: Relationships (Normative)
   https://pubs.opengroup.org/architecture/archimate3-doc/ch-relationships-Normative.html
   There are no any restriction rules here, due to this project uses only a subset of ArchiMate 3.2."
  (:require [clojure.math.combinatorics :as combo]))

(def dependency-rels
  "Ordered by strength from weakest to strongest"
  [:association
   :influence
   :access
   :access_r
   :access_w
   :access_rw
   :serving])

(def dynamic-rels
  "Ordered by strength from weakest to strongest"
  [:triggering
   :flow])

(def structural-rels
  "Ordered by strength from weakest to strongest"
  [:realization
   :assignment
   :aggregation
   :composition])

(def ^:private transitive-rels
  "According with DR1 and DR8"
  [:specialization
   :triggering])

(def ^:private structural-rels-strength-rules
  "According with DR2"
  (->> (combo/permuted-combinations structural-rels 2)
       (map (fn [[rel1 rel2]]
              [[rel1 :a :b] [rel2 :b :c] [(min-key #(.indexOf structural-rels %) rel1 rel2) :a :c]]))))

(defn- make-front-structural-other-rels-rules
  [other-rels]
  (->> (combo/cartesian-product structural-rels other-rels)
       (map (fn [[srel orel]]
              [[srel :a :b] [orel :b :c] [orel :a :c]]))))

(defn- make-back-structural-other-rels-rules
  [other-rels]
  (->> (combo/cartesian-product structural-rels other-rels)
       (map (fn [[srel orel]]
              [[orel :c :b] [srel :a :b] [orel :c :a]]))))

(def ^:private structural-dependency-rels-rules
  "According with DR3 and DR4"
  (concat (make-front-structural-other-rels-rules dependency-rels)
          (make-back-structural-other-rels-rules dependency-rels)))

(def ^:private structural-dynamic-rels-rules
  "According with DR5, DR6 and DR7"
  (concat (make-front-structural-other-rels-rules dynamic-rels)
          (make-back-structural-other-rels-rules [:flow])
          (map (fn [structural-rel]
                 [[:triggering :a :b] [structural-rel :b :c] [:triggering :a :c]])
               structural-rels)))

(defn- make-transitive
  [rel]
  [[rel :a :b] [rel :b :c] [rel :a :c]])

(def certain-rules
  (concat (map make-transitive transitive-rels)
          structural-rels-strength-rules
          structural-dependency-rels-rules
          structural-dynamic-rels-rules))
  
(defn- make-specialization-other-rels-rules
  [other-rels]
  (mapcat (fn [orel]
            [[[:specialization :a :b] [orel :b :c] [orel :a :c]]
             [[:specialization :a :b] [orel :c :b] [orel :c :a]]
             [[:specialization :a :b] [orel :a :c] [orel :b :c]]
             [[:specialization :a :b] [orel :c :a] [orel :c :b]]])
          other-rels))

(def ^:private potential-specialization-rules
  "According with PDR1, PDR2, PDR3 and PDR4"
  (mapcat make-specialization-other-rels-rules
          [structural-rels
           dependency-rels
           dynamic-rels]))

(def ^:private potential-structural-dependency-rels-rules
  "According with PDR5 and PDR6"
  (->> (combo/cartesian-product structural-rels dependency-rels)
       (mapcat (fn [[srel drel]]
                 [[[drel :c :a] [srel :a :b] [drel :c :b]]
                  [[drel :a :c] [srel :a :b] [drel :b :c]]]))))

(def ^:private dependency-rels-strength-rules
  "According with PDR7"
  (->> (combo/permuted-combinations dependency-rels 2)
       (remove #(= #{:access_r :access_w} (set %)))
       (map (fn [[rel1 rel2]]
              [[rel1 :a :b] [rel2 :b :c] [(min-key #(.indexOf dependency-rels %) rel1 rel2) :a :c]]))))

(def ^:private potential-dynamic-rels-rules
  "According with PDR8, PDR9, PDR10 and PDR11"
  (concat (map (fn [srel]
                 [[:flow :a :b] [srel :b :c] [:flow :a :c]])
               structural-rels)
          (map (fn [[srel drel]]
                 [[drel :a :c] [srel :a :b] [drel :b :c]])
               (combo/cartesian-product structural-rels dynamic-rels))
          [(make-transitive :flow)]
          (map (fn [srel]
                 [[:triggering :a :b] [srel :c :b] [:triggering :a :c]])
               structural-rels)))

(def potential-rules
  (concat potential-specialization-rules
          potential-structural-dependency-rels-rules
          dependency-rels-strength-rules
          potential-dynamic-rels-rules))

(def potential-group-around-rules
  "According with PDR12"
  (->> (combo/cartesian-product [:aggregation :composition] [:realization :assignment])
       (map (fn [[crel rrel]]
              [[crel :group :a] [rrel :group :c] [rrel :a :c]]))))

(comment

  (count certain-rules)
  (count potential-rules)
  (count potential-group-around-rules)

  )