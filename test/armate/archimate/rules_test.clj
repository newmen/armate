(ns armate.archimate.rules-test
  (:require [clojure.test :refer [deftest is]]
            [armate.archimate.rules :as rls]))

(deftest possible-elements-test
  (is (= #{:application-collaboration
           :application-component
           :application-data-object
           :application-interface
           :application-service
           :business-actor
           :business-collaboration
           :business-event
           :business-function
           :business-interaction
           :business-process
           :business-product
           :business-role
           :business-service
           :technology-artifact
           :technology-collaboration
           :technology-interaction
           :technology-node
           :technology-path
           :technology-system-software}
         rls/possible-elements)))