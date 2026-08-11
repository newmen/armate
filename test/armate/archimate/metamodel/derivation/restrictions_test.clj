(ns armate.archimate.metamodel.derivation.restrictions-test
  (:require [clojure.test :refer [deftest is]]
            [armate.archimate.metamodel.derivation.restrictions :as rts]))

(deftest same-domains?-test
  (is (rts/same-domains? :business-product :business-product :business-service))
  (is (rts/same-domains? :application-service :business-actor :business-process))
  (is (rts/same-domains? :location :motivation-driver :grouping))
  (is (not (rts/same-domains? :business-role :location :implementation-workpackage))))

(deftest restricted?-test
  (is (rts/restricted? :business-role
                       :location
                       :implementation-workpackage
                       :realization))
  (is (rts/restricted? :business-role
                       :business-object
                       :business-service
                       :influence))
  (is (not (rts/restricted? :business-product
                            :business-product
                            :business-service
                            :triggering)))
  (is (not (rts/restricted? :business-role
                            :motivation-requirement
                            :business-service
                            :influence)))
  (is (not (rts/restricted? :application-service
                            :business-actor
                            :business-process
                            :triggering)))
  (is (not (rts/restricted? :business-role
                            :business-object
                            :business-service
                            :association)))
  (is (not (rts/restricted? :business-role
                            :business-object
                            :business-service
                            :assignment))))
