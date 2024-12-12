(ns armate.archimate.metamodel.appendix-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.set :as o]
            [clojure.string :as s]
            [armate.archimate.metamodel.meta :as mt]
            [armate.archimate.metamodel.appendix :as adx]))

(def letters
  "scgirvantfo")

(def relationships-order
  [:specialization
   :composition
   :aggregation
   :assignment
   :realization
   :serving
   :access
   :influence
   :triggering
   :flow
   :association])

(def rels-letters-map
  (->> (map str letters)
       (zipmap relationships-order)))

(defn get-letters
  [from to]
  (let [general (get-in mt/general-relationships [from to])
        total (get-in adx/total-relationships [from to])]
    (->> (o/difference total #{:access_r :access_w :access_rw})
         (sort-by #(.indexOf relationships-order %))
         (map (fn [relationship]
                (let [letter (rels-letters-map relationship)]
                  (if (general relationship)
                    (s/upper-case letter)
                    letter))))
         (s/join))))

(deftest total-relationships-test
  (testing "ArchiMate 3.2. Appendix B.5. Relationship Tables"
    (is (= "NO" (get-letters :motivation-stakeholder :motivation-constraint)))
    (is (= "vtfO" (get-letters :strategy-course-of-action :strategy-resource)))
    (is (= "scgrvntfO" (get-letters :strategy-course-of-action :grouping)))
    (is (= "vtfO" (get-letters :business-role :location)))
    (is (= "scgirvantfO" (get-letters :business-role :grouping)))
    (is (= "VtfO" (get-letters :business-service :business-process)))
    (is (= "SCGRvtfO" (get-letters :application-component :application-component)))
    (is (= "irvtfO" (get-letters :application-component :application-service)))
    (is (= "VtfO" (get-letters :application-interface :application-component)))
    (is (= "SCGvtfO" (get-letters :application-interface :application-interface)))
    (is (= "IvtfO" (get-letters :application-interface :application-service)))
    (is (= "RNO" (get-letters :application-service :motivation-constraint)))
    (is (= "nO" (get-letters :application-service :motivation-driver)))
    (is (= "rnO" (get-letters :application-service :motivation-goal)))
    (is (= "VtfO" (get-letters :application-service :business-actor)))
    (is (= "VtfO" (get-letters :application-service :business-process)))
    (is (= "VtfO" (get-letters :application-service :application-component)))
    (is (= "vtfO" (get-letters :application-service :application-interface)))
    (is (= "SCGvTFO" (get-letters :application-service :application-service)))
    (is (= "RO" (get-letters :technology-artifact :application-component)))
    (is (= "rO" (get-letters :technology-artifact :application-interface)))
    (is (= "RO" (get-letters :technology-artifact :technology-system-software)))
    (is (= "O" (get-letters :technology-service :strategy-resource)))
    (is (= "rvtfO" (get-letters :technology-system-software :application-interface)))
    (is (= "rvtfO" (get-letters :technology-system-software :application-component)))
    (is (= "IaO" (get-letters :technology-system-software :technology-artifact)))
    (is (= "CgirvtfO" (get-letters :technology-system-software :technology-interface)))
    (is (= "girvtfO" (get-letters :physical-distribution-network :technology-interface)))
    (is (= "IaO" (get-letters :physical-equipment :physical-material)))
    (is (= "iaO" (get-letters :physical-facility :physical-material)))
    (is (= "rO" (get-letters :physical-material :technology-device)))
    (is (= "AO" (get-letters :implementation-event :implementation-deriverable)))
    (is (= "TfO" (get-letters :implementation-event :implementation-plateau)))
    (is (= "CGnO" (get-letters :location :motivation-driver)))
    (is (= "CGaO" (get-letters :location :business-representation)))
    (is (= "CGvtfO" (get-letters :location :technology-communication-network)))
    (is (= "CGrvtfO" (get-letters :location :technology-path)))
    (is (= "CGirvtfO" (get-letters :location :technology-system-software)))
    (is (= "CGvtfO" (get-letters :location :physical-distribution-network)))
    (is (= "SCGvtfO" (get-letters :location :location)))
    (is (= "sCGnO" (get-letters :grouping :motivation-driver)))
    (is (= "sCGirnO" (get-letters :grouping :motivation-stakeholder)))
    (is (= "sCGirvtfO" (get-letters :grouping :business-actor)))
    (is (= "sCGrvtfO" (get-letters :grouping :location)))
    (is (= "SCGirvantfO" (get-letters :grouping :grouping)))))
