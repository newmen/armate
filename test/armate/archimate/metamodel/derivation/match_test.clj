(ns armate.derivation.match-test
  (:require [clojure.test :refer :all]
            [armate.derivation.rules :as rs]
            [armate.derivation.match :as mch]))

(def graph0
  {"food_bs" {"child_ba" {:type :serving}}
   "pass_bs" {"child_ba" {:type :serving}}
   "partner_br"
   {"configureTurnstile_bp" {:type :assignment}
    "controlFood_bp" {:type :assignment}}
   "child_ba" {"client_br" {:type :assignment}}
   "controlFood_bp" {"food_bs" {:type :realization}}
   "registry_bs" {"partner_br" {:type :serving}}
   "client_br" {"fillForm_bp" {:type :assignment}}
   "configureTurnstile_bp" {"pass_bs" {:type :realization}}
   "getRegistry_bf" {"registry_bs" {:type :realization}}
   "fillForm_bp" {"getRegistry_bf" {:type :flow}}})

(deftest get-rel-wieght-test
  (is (= 1000 (mch/get-rel-wieght :specialization)))
  (is (= 300 (mch/get-rel-wieght :aggregation)))
  (is (= 40 (mch/get-rel-wieght :access_r)))
  (is (= 2 (mch/get-rel-wieght :flow))))

(deftest make-rules-map-test
  (is (= {:serving [[[:serving :c :a] [:realization :b :a] [:serving :c :b]]]
          :flow [[[:flow :a :c] [:assignment :b :c] [:flow :a :b]]
                 [[:flow :c :b] [:realization :a :b] [:flow :c :a]]]
          :assignment [[[:assignment :a :b] [:composition :b :c] [:assignment :a :c]]]}
         (mch/make-rules-map [[[:serving :c :a] [:realization :b :a] [:serving :c :b]]
                              [[:flow :c :b] [:realization :a :b] [:flow :c :a]]
                              [[:flow :a :c] [:assignment :b :c] [:flow :a :b]]
                              [[:assignment :a :b] [:composition :b :c] [:assignment :a :c]]]))))

(deftest reverse-graph-test
  (is (= {"partner_br" {"registry_bs" {:type :serving}}
          "child_ba" {"food_bs" {:type :serving} "pass_bs" {:type :serving}}
          "client_br" {"child_ba" {:type :assignment}}
          "controlFood_bp" {"partner_br" {:type :assignment}}
          "configureTurnstile_bp" {"partner_br" {:type :assignment}}
          "getRegistry_bf" {"fillForm_bp" {:type :flow}}
          "registry_bs" {"getRegistry_bf" {:type :realization}}
          "food_bs" {"controlFood_bp" {:type :realization}}
          "pass_bs" {"configureTurnstile_bp" {:type :realization}}
          "fillForm_bp" {"client_br" {:type :assignment}}}
         (mch/reverse-graph graph0))))

(deftest get-weights-test
  (is (= {"partner_br" [-400 -200]
          "child_ba" [-200 -200]
          "client_br" [-200 -2]
          "controlFood_bp" [-100 -70]
          "configureTurnstile_bp" [-100 -70]
          "getRegistry_bf" [-100 -70]
          "registry_bs" [-70 -400]
          "food_bs" [-70 -200]
          "pass_bs" [-70 -200]
          "fillForm_bp" [-2 -100]}
         (mch/get-weights graph0))))

(deftest get-relationships-test
  (is (= [["partner_br" "configureTurnstile_bp" :assignment]
          ["partner_br" "controlFood_bp" :assignment]
          ["child_ba" "client_br" :assignment]
          ["client_br" "fillForm_bp" :assignment]
          ["controlFood_bp" "food_bs" :realization]
          ["configureTurnstile_bp" "pass_bs" :realization]
          ["getRegistry_bf" "registry_bs" :realization]
          ["registry_bs" "partner_br" :serving]
          ["food_bs" "child_ba" :serving]
          ["pass_bs" "child_ba" :serving]
          ["fillForm_bp" "getRegistry_bf" :flow]]
         (mch/get-relationships graph0))))

(deftest derivate-rules-once-test
  (is (= {"partner_br" {"pass_bs" {:type :realization}
                        "food_bs" {:type :realization}
                        "child_ba" {:type :serving}}
          "client_br" {"getRegistry_bf" {:type :flow}}
          "controlFood_bp" {"child_ba" {:type :serving}}
          "configureTurnstile_bp" {"child_ba" {:type :serving}}
          "getRegistry_bf" {"partner_br" {:type :serving}}}
         (mch/derivate-rules-once rs/certain-rules graph0)))
  (is (= {"registry_bs" {"configureTurnstile_bp" {:type :serving}
                         "controlFood_bp" {:type :serving}
                         "pass_bs" {:type :serving}
                         "food_bs" {:type :serving}}
          "food_bs" {"client_br" {:type :serving}
                     "fillForm_bp" {:type :serving}}
          "pass_bs" {"client_br" {:type :serving}
                     "fillForm_bp" {:type :serving}}
          "fillForm_bp" {"registry_bs" {:type :flow}}}
         (mch/derivate-rules-once rs/potential-rules graph0))))

(deftest derivate-rules-test
  (is (= {"partner_br" {"pass_bs" {:type :realization}
                        "food_bs" {:type :realization}
                        "child_ba" {:type :serving}}
          "client_br" {"getRegistry_bf" {:type :flow}}
          "controlFood_bp" {"child_ba" {:type :serving}}
          "configureTurnstile_bp" {"child_ba" {:type :serving}}
          "getRegistry_bf" {"partner_br" {:type :serving}}
          "child_ba" {"getRegistry_bf" {:type :flow}}}
         (mch/derivate-rules rs/certain-rules graph0)))
  (is (not= (mch/derivate-rules-once rs/certain-rules graph0)
            (mch/derivate-rules rs/certain-rules graph0)))
  (is (= (mch/derivate-rules-once rs/potential-rules graph0)
         (mch/derivate-rules rs/potential-rules graph0))))