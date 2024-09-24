(ns armate.derivation.match-test
  (:require [clojure.test :refer :all]
            [armate.derivation.rules :as rs]
            [armate.derivation.match :as mch]))

(def graph0
  {"child_ba" {"client_br" #{{:type :assignment}}}
   "client_br" {"fillForm_bpc" #{{:type :assignment}}}
   "configureTurnstile_bpc" {"pass_bs" #{{:type :realization}}}
   "controlFood_bpc" {"food_bs" #{{:type :realization}}}
   "fillForm_bpc" {"getRegistry_bpc" #{{:type :flow}}}
   "food_bs" {"child_ba" #{{:type :serving}}}
   "getRegistry_bpc" {"registry_bs" #{{:type :realization}}}
   "pass_bs" {"child_ba" #{{:type :serving}}}
   "partner_br" {"configureTurnstile_bpc" #{{:type :assignment}}
                 "controlFood_bpc" #{{:type :assignment}}}
   "registry_bs" {"partner_br" #{{:type :serving}}}})

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
  (is (= {"child_ba" {"food_bs" #{{:type :serving}}
                      "pass_bs" #{{:type :serving}}}
          "client_br" {"child_ba" #{{:type :assignment}}}
          "configureTurnstile_bpc" {"partner_br" #{{:type :assignment}}}
          "controlFood_bpc" {"partner_br" #{{:type :assignment}}}
          "fillForm_bpc" {"client_br" #{{:type :assignment}}}
          "food_bs" {"controlFood_bpc" #{{:type :realization}}}
          "getRegistry_bpc" {"fillForm_bpc" #{{:type :flow}}}
          "partner_br" {"registry_bs" #{{:type :serving}}}
          "pass_bs" {"configureTurnstile_bpc" #{{:type :realization}}}
          "registry_bs" {"getRegistry_bpc" #{{:type :realization}}}}
         (mch/reverse-graph graph0))))

(deftest get-weights-test
  (is (= {"partner_br" [-400 -200]
          "child_ba" [-200 -200]
          "client_br" [-200 -2]
          "controlFood_bpc" [-100 -70]
          "configureTurnstile_bpc" [-100 -70]
          "getRegistry_bpc" [-100 -70]
          "registry_bs" [-70 -400]
          "food_bs" [-70 -200]
          "pass_bs" [-70 -200]
          "fillForm_bpc" [-2 -100]}
         (mch/get-weights graph0))))

(deftest get-relationships-test
  (is (= #{["partner_br" "configureTurnstile_bpc" :assignment]
           ["partner_br" "controlFood_bpc" :assignment]
           ["child_ba" "client_br" :assignment]
           ["client_br" "fillForm_bpc" :assignment]
           ["controlFood_bpc" "food_bs" :realization]
           ["configureTurnstile_bpc" "pass_bs" :realization]
           ["getRegistry_bpc" "registry_bs" :realization]
           ["registry_bs" "partner_br" :serving]
           ["food_bs" "child_ba" :serving]
           ["pass_bs" "child_ba" :serving]
           ["fillForm_bpc" "getRegistry_bpc" :flow]}
         (set (mch/get-relationships graph0)))))

(deftest get-prioritized-relationships-test
  (is (= [["partner_br" "configureTurnstile_bpc" :assignment]
          ["partner_br" "controlFood_bpc" :assignment]
          ["child_ba" "client_br" :assignment]
          ["client_br" "fillForm_bpc" :assignment]
          ["configureTurnstile_bpc" "pass_bs" :realization]
          ["controlFood_bpc" "food_bs" :realization]
          ["getRegistry_bpc" "registry_bs" :realization]
          ["registry_bs" "partner_br" :serving]
          ["food_bs" "child_ba" :serving]
          ["pass_bs" "child_ba" :serving]
          ["fillForm_bpc" "getRegistry_bpc" :flow]]
         (mch/get-prioritized-relationships graph0))))

(deftest match-rule
  (letfn [(im [fg]
            {:forward-graph fg
             :reverse-graph (mch/reverse-graph fg)
             :derivated-graph {}
             :derivated-relations []})]
    (let [forward-graph {"a" {"b" #{{:type :realization}}}
                         "b" {"c" #{{:type :serving}}}}]
      (doseq [rule [[[:realization :b :a] [:serving :b :c] [:access :a :c]]
                    [[:realization :b :a] [:serving :b :c] [:access :c :a]]
                    [[:realization :b :a] [:serving :c :a] [:access :b :c]]
                    [[:realization :b :a] [:serving :c :a] [:access :c :b]]
                    [[:realization :b :a] [:serving :c :b] [:access :a :c]]
                    [[:realization :b :a] [:serving :c :b] [:access :c :a]]]]
        (is (= (im forward-graph)
               (mch/match-rule "a" "b" (im forward-graph) rule)))))
    (let [forward-graph {"a" {"b" #{{:type :realization}}}
                         "b" {"c" #{{:type :serving}}}}]
      (doseq [rule [[[:realization :a :b] [:serving :b :c] [:access :a :c]]
                    [[:realization :b :a] [:serving :a :c] [:access :b :c]]
                    [[:realization :x :y] [:serving :y :z] [:access :x :z]]]]
        (is (= {:forward-graph {"a" {"b" #{{:type :realization}}
                                     "c" #{{:type :access}}}
                                "b" {"c" #{{:type :serving}}}}
                :reverse-graph {"b" {"a" #{{:type :realization}}}
                                "c" {"a" #{{:type :access}}
                                     "b" #{{:type :serving}}}}
                :derivated-graph {"a" {"c" #{{:type :access}}}}
                :derivated-relations [["a" "c" :access]]}
               (mch/match-rule "a" "b" (im forward-graph) rule))))
      (doseq [rule [[[:realization :a :b] [:serving :b :c] [:access :c :a]]
                    [[:realization :b :a] [:serving :a :c] [:access :c :b]]]]
        (is (= {:forward-graph {"a" {"b" #{{:type :realization}}}
                                "b" {"c" #{{:type :serving}}}
                                "c" {"a" #{{:type :access}}}}
                :reverse-graph {"a" {"c" #{{:type :access}}}
                                "b" {"a" #{{:type :realization}}}
                                "c" {"b" #{{:type :serving}}}}
                :derivated-graph {"c" {"a" #{{:type :access}}}}
                :derivated-relations [["c" "a" :access]]}
               (mch/match-rule "a" "b" (im forward-graph) rule)))))
    (let [forward-graph {"a" {"b" #{{:type :realization}}
                              "c" #{{:type :serving}}}}]
      (is (= {:forward-graph {"a" {"b" #{{:type :realization}}
                                   "c" #{{:type :serving}}}
                              "b" {"c" #{{:type :access}}}}
              :reverse-graph {"b" {"a" #{{:type :realization}}}
                              "c" {"a" #{{:type :serving}}
                                   "b" #{{:type :access}}}}
              :derivated-graph {"b" {"c" #{{:type :access}}}}
              :derivated-relations [["b" "c" :access]]}
             (mch/match-rule "a" "b" (im forward-graph)
                             [[:realization :a :b] [:serving :a :c] [:access :b :c]])))
      (is (= {:forward-graph {"a" {"b" #{{:type :realization}}
                                   "c" #{{:type :serving}}}
                              "c" {"b" #{{:type :access}}}}
              :reverse-graph {"b" {"a" #{{:type :realization}}
                                   "c" #{{:type :access}}}
                              "c" {"a" #{{:type :serving}}}}
              :derivated-graph {"c" {"b" #{{:type :access}}}}
              :derivated-relations [["c" "b" :access]]}
             (mch/match-rule "a" "b" (im forward-graph)
                             [[:realization :a :b] [:serving :a :c] [:access :c :b]]))))
    (let [forward-graph {"a" {"b" #{{:type :realization}}}
                         "c" {"a" #{{:type :serving}}}}]
      (is (= {:forward-graph {"a" {"b" #{{:type :realization}}}
                              "b" {"c" #{{:type :access}}}
                              "c" {"a" #{{:type :serving}}}}
              :reverse-graph {"a" {"c" #{{:type :serving}}}
                              "b" {"a" #{{:type :realization}}}
                              "c" {"b" #{{:type :access}}}}
              :derivated-graph {"b" {"c" #{{:type :access}}}}
              :derivated-relations [["b" "c" :access]]}
             (mch/match-rule "a" "b" (im forward-graph)
                             [[:realization :a :b] [:serving :c :a] [:access :b :c]])))
      (is (= {:forward-graph {"a" {"b" #{{:type :realization}}}
                              "c" {"a" #{{:type :serving}}
                                   "b" #{{:type :access}}}}
              :reverse-graph {"a" {"c" #{{:type :serving}}}
                              "b" {"a" #{{:type :realization}}
                                   "c" #{{:type :access}}}}
              :derivated-graph {"c" {"b" #{{:type :access}}}}
              :derivated-relations [["c" "b" :access]]}
             (mch/match-rule "a" "b" (im forward-graph)
                             [[:realization :a :b] [:serving :c :a] [:access :c :b]]))))
    (let [forward-graph {"a" {"b" #{{:type :realization}}}
                         "c" {"b" #{{:type :serving}}}}]
      (is (= {:forward-graph {"a" {"b" #{{:type :realization}}
                                   "c" #{{:type :access}}}
                              "c" {"b" #{{:type :serving}}}}
              :reverse-graph {"b" {"a" #{{:type :realization}}
                                   "c" #{{:type :serving}}}
                              "c" {"a" #{{:type :access}}}}
              :derivated-graph {"a" {"c" #{{:type :access}}}}
              :derivated-relations [["a" "c" :access]]}
             (mch/match-rule "a" "b" (im forward-graph)
                             [[:realization :a :b] [:serving :c :b] [:access :a :c]])))
      (is (= {:forward-graph {"a" {"b" #{{:type :realization}}}
                              "c" {"a" #{{:type :access}}
                                   "b" #{{:type :serving}}}}
              :reverse-graph {"a" {"c" #{{:type :access}}}
                              "b" {"a" #{{:type :realization}}
                                   "c" #{{:type :serving}}}}
              :derivated-graph {"c" {"a" #{{:type :access}}}}
              :derivated-relations [["c" "a" :access]]}
             (mch/match-rule "a" "b" (im forward-graph)
                             [[:realization :a :b] [:serving :c :b] [:access :c :a]]))))))

(deftest derivate-relationships-once-test
  (is (= {"child_ba" {"fillForm_bpc" #{{:type :assignment}}
                      "getRegistry_bpc" #{{:type :flow}}}
          "client_br" {"getRegistry_bpc" #{{:type :flow}}}
          "configureTurnstile_bpc" {"child_ba" #{{:type :serving}}}
          "controlFood_bpc" {"child_ba" #{{:type :serving}}}
          "getRegistry_bpc" {"partner_br" #{{:type :serving}}}
          "partner_br" {"child_ba" #{{:type :serving}}
                        "food_bs" #{{:type :realization}}
                        "pass_bs" #{{:type :realization}}}}
         (mch/derivate-relationships-once rs/certain-rules graph0)))
  (is (= {"fillForm_bpc" {"registry_bs" #{{:type :flow}}}
          "food_bs" {"client_br" #{{:type :serving}}
                     "fillForm_bpc" #{{:type :serving}}}
          "registry_bs" {"child_ba" #{{:type :serving}}
                         "client_br" #{{:type :serving}}
                         "configureTurnstile_bpc" #{{:type :serving}}
                         "controlFood_bpc" #{{:type :serving}}
                         "fillForm_bpc" #{{:type :serving}}
                         "food_bs" #{{:type :serving}}
                         "pass_bs" #{{:type :serving}}}
          "pass_bs" {"client_br" #{{:type :serving}}
                     "fillForm_bpc" #{{:type :serving}}}}
         (mch/derivate-relationships-once rs/potential-rules graph0))))

(deftest derivate-relationships-test
  (is (= {"child_ba" {"fillForm_bpc" #{{:type :assignment}}
                      "getRegistry_bpc" #{{:type :flow}}}
          "client_br" {"getRegistry_bpc" #{{:type :flow}}}
          "configureTurnstile_bpc" {"child_ba" #{{:type :serving}}}
          "controlFood_bpc" {"child_ba" #{{:type :serving}}}
          "getRegistry_bpc" {"partner_br" #{{:type :serving}}}
          "partner_br" {"child_ba" #{{:type :serving}}
                        "food_bs" #{{:type :realization}}
                        "pass_bs" #{{:type :realization}}}}
         (mch/derivate-relationships rs/certain-rules graph0)))
  (is (= (mch/derivate-relationships-once rs/certain-rules graph0)
         (mch/derivate-relationships rs/certain-rules graph0)))
  (is (= (mch/derivate-relationships-once rs/potential-rules graph0)
         (mch/derivate-relationships rs/potential-rules graph0))))
