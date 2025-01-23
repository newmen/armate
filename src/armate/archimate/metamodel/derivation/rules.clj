(ns armate.archimate.metamodel.derivation.rules
  "Appendix B: Relationships (Normative)
   https://pubs.opengroup.org/architecture/archimate3-doc/ch-relationships-Normative.html
   The restriction rules declared in a particular file"
  (:require [clojure.math.combinatorics :as combo]))

(def structural-rels
  "Ordered by strength from weakest to strongest"
  [:realization
   :assignment
   :aggregation
   :composition])

(def dependency-rels
  "Ordered by strength from weakest to strongest"
  [:association
   :association_dir
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

(def other-rels
  [:specialization])

(def ^:private transitive-rels
  "According with DR1 and DR8"
  [:specialization
   :triggering])

(defn- make-transitive
  [rel]
  [[rel :a :b] [rel :b :c] [rel :a :c]])

(defn- make-strength-rules
  [prepare rels]
  (let [index-f #(.indexOf rels %)]
    (->> (combo/permuted-combinations rels 2)
         (prepare)
         (map (fn [[rel1 rel2]]
                [[rel1 :a :b] [rel2 :b :c] [(min-key index-f rel1 rel2) :a :c]]))
         (concat (map make-transitive rels)))))

(def ^:private structural-rels-strength-rules
  "According with DR2"
  (make-strength-rules identity structural-rels))

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
  (make-strength-rules (partial remove #(= #{:access_r :access_w} (set %)))
                       dependency-rels))

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
              [[crel :g :a] [rrel :g :c] [rrel :a :c]]))))

(defn valid?
  [rule]
  (let [[[_ f1 t1] [_ f2 t2] [_ fr tr]] rule]
    (and (not= f1 t1)
         (not= f2 t2)
         (not= fr tr)
         (or (and (= f1 f2) (not= t1 t2))
             (and (= f1 t2) (not= t1 f2))
             (and (= t1 f2) (not= f1 t2))
             (and (= t1 t2) (not= f1 f2)))
         (or (and (= f1 fr) (not= t1 tr))
             (and (= f1 tr) (not= t1 fr))
             (and (= t1 fr) (not= f1 tr))
             (and (= t1 tr) (not= f1 fr)))
         (or (and (= f2 fr) (not= t2 tr))
             (and (= f2 tr) (not= t2 fr))
             (and (= t2 fr) (not= f2 tr))
             (and (= t2 tr) (not= f2 fr))))))

(defn normalize
  [rule]
  {:pre [(valid? rule)]
   :post [(valid? %)]}
  (let [[[r1 f1 t1] [r2 f2 t2] [r3 f3 t3]] rule
        f1' :a
        t1' :b
        match2 #(if (= f1 %) f1'
                    (if (= t1 %) t1' :c))
        f2' (match2 f2)
        t2' (match2 t2)
        f3' (match2 f3)
        t3' (match2 t3)]
    [[r1 f1' t1'] [r2 f2' t2'] [r3 f3' t3']]))

(defn check-invariants
  [rules]
  (:invariants
   (reduce (fn [acc rule]
             (let [norm (normalize rule)]
               (if-let [invariant (get-in acc [:checked norm])]
                 (update-in acc [:invariants invariant] (fnil conj []) rule)
                 (assoc-in acc [:checked norm] rule))))
           {:checked {}
            :invariants {}}
           rules)))

(comment

  (count certain-rules)
  (count potential-rules)
  (count potential-group-around-rules)

  (filter (comp #{:influence} first last) certain-rules)
  (filter (comp #{:influence} first last) potential-rules)

  )