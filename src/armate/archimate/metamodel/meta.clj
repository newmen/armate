(ns armate.archimate.metamodel.core
  (:require [armate.archimate.metamodel.solver :as slv]
            [armate.archimate.metamodel.derivation.rules :as drs]))

(def business-layer
  [:business-interface :business-internal-active
   :business-service :business-internal-behavior :business-event
   :business-passive])

(def application-layer
  [:application-interface :application-internal-active
   :application-service :application-internal-behavior :application-event
   :application-data-object])

(def technology-layer
  [:technology-interface :technology-internal-active
   :technology-service :technology-internal-behavior :technology-event
   :technology-passive])

(def hierarchy
  (slv/build-flat-hierarchy
   {:concept {:element {:behavior {:external-behavior {:service #{:business-service
                                                                  :application-service
                                                                  :technology-service}}
                                   :internal-behavior {:business-internal-behavior #{:business-process
                                                                                     :business-function
                                                                                     :business-interaction}
                                                       :application-internal-behavior #{:application-process
                                                                                        :application-function
                                                                                        :application-interaction}
                                                       :technology-internal-behavior #{:technology-process
                                                                                       :technology-function
                                                                                       :technology-interaction}
                                                       :implementation-workpackage #{}}
                                   :event #{:business-event
                                            :application-event
                                            :technology-event
                                            :implementation-event}
                                   :strategy-course-of-action #{}
                                   :strategy-behavior #{:strategy-value-stream :strategy-capability}}
                        :structure {:active {:external-active {:interface #{:business-interface
                                                                            :application-interface
                                                                            :technology-interface}}
                                             :internal-active {:business-internal-active #{:business-actor
                                                                                           :business-role
                                                                                           :business-collaboration}
                                                               :application-internal-active #{:application-component
                                                                                              :application-collaboration}
                                                               :technology-internal-active #{:technology-device
                                                                                             :technology-node
                                                                                             :technology-system-software
                                                                                             :technology-collaboration
                                                                                             :physical-equipment
                                                                                             :physical-facility}}
                                             :technology-active #{:technology-internal-active
                                                                  :technology-path
                                                                  :technology-communication-network
                                                                  :physical-distribution-network}}
                                    :passive {:business-passive {:business-representation #{}
                                                                 :business-object #{:business-contract}}
                                              :application-data-object #{}
                                              :technology-passive #{:technology-artifact
                                                                    :physical-material}
                                              :implementation-gap #{}
                                              :implementation-deriverable #{}}
                                    :strategy-resource #{}}
                        :motivation {:motivation-stakeholder #{}
                                     :motivation-meaning #{}
                                     :motivation-value #{}
                                     :motivation-driver #{}
                                     :motivation-assessment #{}
                                     :motivation-goal #{}
                                     :motivation-outcome #{}
                                     :motivation-principle #{}
                                     :motivation-requirement #{:motivation-constraint}}
                        :composite #{:grouping
                                     :location
                                     :business-product
                                     :implementation-plateau}}
              :relationship {:structural drs/structural-rels
                             :dependency drs/dependency-rels
                             :dynamic drs/dynamic-rels
                             :other drs/other-rels}
              :connector #{:and :or}}}))

(def layers
  (slv/build-flat-hierarchy
   hierarchy
   {:motivation #{:motivation}
    :strategy #{:strategy-course-of-action
                :strategy-behavior
                :strategy-resource}
    :core {:business (set business-layer)
           :application (set application-layer)
           :technology (->> (map #(vector % #{}) technology-layer)
                            (into {})
                            (merge {:physical #{:physical-material
                                                :technology-internal-behavior
                                                :technology-device
                                                :technology-node
                                                :physical-equipment
                                                :physical-facility
                                                :technology-path
                                                :physical-distribution-network}}))}
    :implementation #{:implementation-workpackage
                      :implementation-event
                      :implementation-deriverable
                      :implementation-gap
                      :implementation-plateau}}))

(defn metamodel
  [interface internal-active
   service internal-behavior event
   passive]
  {interface {service #{:assignment}
              internal-active #{:serving}}
   internal-active {interface #{:composition}
                    internal-behavior #{:assignment}
                    event #{:assignment}}
   service {service #{:triggering :flow}
            internal-active #{:serving}
            internal-behavior #{:serving}
            event #{:triggering :flow}
            passive #{:access_r :access_w :access_rw}}
   internal-behavior {service #{:realization}
                      internal-behavior #{:aggregation :composition :triggering :flow}
                      event #{:triggering :flow}
                      passive #{:access_r :access_w :access_rw}}
   event {service #{:triggering :flow}
          internal-behavior #{:triggering :flow}
          event #{:triggering :flow}
          passive #{:access_r :access_w :access_rw}}})

(def relationships
  (slv/multiply-relationships
   hierarchy layers
   (merge-with (partial merge-with into)
               {:grouping {:concept #{:aggregation :composition}}
                :location {:concept #{:aggregation :composition}
                           :strategy-resource #{:realization}
                           :implementation-gap #{:association}}
                :motivation {:motivation #{:influence}}
                :motivation-stakeholder {:motivation-meaning #{:association}
                                         :motivation-value #{:association}
                                         :motivation-driver #{:association}}
                :motivation-meaning {:motivation-stakeholder #{:association}
                                     [:structure #{:strategy :core}] #{:association}
                                     [:behavior #{:strategy :core}] #{:association}
                                     :composite #{:association}}
                :motivation-value {:motivation-stakeholder #{:association}
                                   :motivation-outcome #{:association}
                                   [:structure #{:strategy :core}] #{:association}
                                   [:behavior #{:strategy :core}] #{:association}
                                   :composite #{:association}}
                :motivation-driver {:motivation-stakeholder #{:association}
                                    :motivation-assessment #{:association}
                                    :motivation-goal #{:association}}
                :motivation-assessment {:motivation-driver #{:association}
                                        :motivation-goal #{:association}}
                :motivation-goal {:motivation-driver #{:association}
                                  :motivation-assessment #{:association}}
                :motivation-outcome {:motivation-value #{:association}
                                     :motivation-goal #{:realization}}
                :motivation-principle {:motivation-outcome #{:realization}}
                :motivation-requirement {:motivation-outcome #{:realization}
                                         :motivation-principle #{:realization}}
                :business-internal-active {:motivation-stakeholder #{:assignment}
                                           :implementation-workpackage #{:assignment}
                                           :implementation-event #{:assignment}}
                [:structure #{:strategy :core}] {:motivation-requirement #{:influence :realization}
                                                 :motivation-meaning #{:association}
                                                 :motivation-value #{:association}
                                                 :implementation-gap #{:association}}
                [:behavior #{:strategy :core}] {:motivation-requirement #{:influence :realization}
                                                :motivation-meaning #{:association}
                                                :motivation-value #{:association}
                                                :implementation-gap #{:association}}
                :composite {:motivation-requirement #{:influence :realization}
                            :motivation-meaning #{:association}
                            :motivation-value #{:association}}
                :strategy-course-of-action {:strategy-course-of-action #{:triggering :flow :serving}
                                            :motivation-outcome #{:influence :realization}
                                            :motivation-requirement #{:influence :realization}}
                :strategy-behavior {:strategy-behavior #{:triggering :flow :serving}
                                    :strategy-course-of-action #{:serving :realization}
                                    :motivation-requirement #{:influence :realization}}
                :strategy-resource {:strategy-behavior #{:assignment}
                                    :motivation-requirement #{:influence :realization}}
                [:internal-behavior #{:core}] {:strategy-behavior #{:realization}}
                [:external-behavior #{:core}] {:strategy-behavior #{:realization}}
                [:active #{:core}] {:strategy-resource #{:realization}}
                [:passive #{:core}] {:strategy-resource #{:realization}}
                :business-actor {:business-role #{:assignment}}
                :business-collaboration {:business-internal-active #{:aggregation}}
                :business-representation {:business-object #{:realization}}
                :business-product {:business-service #{:aggregation :composition}
                                   :business-contract #{:aggregation :composition}
                                   :business-passive #{:aggregation :composition}
                                   :application-service #{:aggregation :composition}
                                   :application-data-object #{:aggregation :composition}
                                   :technology-service #{:aggregation :composition}
                                   :technology-passive #{:aggregation :composition}
                                   :implementation-gap #{:association}}
                :application-component {:application-component #{:realization}}
                :application-collaboration {:application-internal-active #{:aggregation}}
                :technology-internal-active {:technology-path #{:association}}
                :technology-path {:technology-internal-active #{:aggregation :association}}
                :technology-communication-network {:technology-path #{:realization}
                                                   :technology-device #{:aggregation :association}
                                                   :technology-system-software #{:aggregation :association}}
                :technology-device {:technology-communication-network #{:association}
                                    :technology-system-software #{:aggregation :composition :assignment}
                                    :technology-artifact #{:assignment}}
                :technology-node {:technology-device #{:aggregation :composition}
                                  :technology-system-software #{:aggregation :composition}
                                  :physical-equipment #{:aggregation :composition}
                                  :physical-facility #{:aggregation :composition}}
                :technology-system-software {:technology-communication-network #{:association}
                                             :technology-system-software #{:assignment}
                                             :technology-artifact #{:assignment}}
                :technology-collaboration {:technology-internal-active #{:aggregation}}
                :technology-artifact {:technology-system-software #{:realization}
                                      :application-data-object #{:realization}
                                      :application-internal-active #{:realization}}
                :physical-material {:physical-equipment #{:realization}
                                    :physical-distribution-network #{:association}}
                :technology-internal-behavior {:physical-material #{:access_r :access_w :access_rw}
                                               :business-internal-behavior #{:realization}
                                               :application-internal-behavior #{:realization}}
                :physical-equipment {:physical-material #{:assignment}
                                     :technology-internal-behavior #{:assignment}
                                     :technology-device #{:aggregation :composition}}
                :physical-facility {:business-internal-active #{:assignment}
                                    :technology-node #{:aggregation :composition :assignment}
                                    :physical-equipment #{:aggregation :composition}
                                    :physical-distribution-network #{:association}}
                :physical-distribution-network {:technology-path #{:realization}
                                                :physical-equipment #{:aggregation}
                                                :physical-facility #{:aggregation :association}}
                :business-service {:application-internal-behavior #{:serving}
                                   :application-internal-active #{:serving}
                                   :technology-internal-behavior #{:serving}
                                   :technology-internal-active #{:serving}}
                :business-interface {:application-internal-active #{:serving}
                                     :technology-internal-active #{:serving}}
                :application-data-object {:business-object #{:realization}}
                :application-event {:business-event #{:realization}}
                :application-internal-behavior {:business-internal-behavior #{:realization}}
                :application-service {:business-internal-behavior #{:serving}
                                      :business-service #{:realization}
                                      :business-internal-active #{:serving}
                                      :technology-internal-behavior #{:serving}
                                      :technology-internal-active #{:serving}}
                :application-interface {:business-internal-active #{:serving}
                                        :business-interface #{:realization}
                                        :technology-internal-active #{:serving}}
                :technology-passive {:business-object #{:realization}}
                :technology-event {:business-event #{:realization}
                                   :application-event #{:realization}}
                :technology-service {:business-internal-behavior #{:serving}
                                     :business-service #{:realization}
                                     :business-internal-active #{:serving}
                                     :application-internal-behavior #{:serving}
                                     :application-service #{:realization}
                                     :application-internal-active #{:serving}}
                :technology-interface {:business-internal-active #{:serving}
                                       :business-interface #{:realization}
                                       :application-internal-active #{:serving}
                                       :application-interface #{:realization}}
                :implementation-workpackage {:implementation-workpackage #{:triggering :flow}
                                             :implementation-event #{:triggering :flow}
                                             :implementation-deriverable #{:access_r :access_w :access_rw :realization}
                                             [:structure #{:strategy :core}] #{:realization}
                                             [:behavior #{:strategy :core}] #{:realization}
                                             :business-product #{:realization}
                                             :location #{:realization}
                                             :motivation-requirement #{:influence :realization}}
                :implementation-event {:implementation-workpackage #{:triggering :flow}
                                       :implementation-event #{:triggering :flow}
                                       :implementation-deriverable #{:access_r :access_w :access_rw}
                                       :implementation-plateau #{:triggering}}
                :implementation-deriverable {:implementation-plateau #{:realization}
                                             [:structure #{:strategy :core}] #{:realization}
                                             [:behavior #{:strategy :core}] #{:realization}
                                             :business-product #{:realization}
                                             :location #{:realization}
                                             :motivation-requirement #{:influence :realization}}
                :implementation-gap {:implementation-plateau #{:association}
                                     [:structure #{:strategy :core}] #{:association}
                                     [:behavior #{:strategy :core}] #{:association}
                                     :business-product #{:association}
                                     :location #{:association}}
                :implementation-plateau {:implementation-gap #{:association}
                                         :implementation-event #{:triggering}
                                         :implementation-plateau #{:triggering}
                                         :relationship #{:aggregation :composition}
                                         :connector #{:aggregation :composition}
                                         [:structure #{:strategy :core}] #{:aggregation :composition :realization}
                                         [:behavior #{:strategy :core}] #{:aggregation :composition :realization}
                                         :business-product #{:aggregation :composition :realization}
                                         :location #{:aggregation :composition :realization}
                                         :motivation-outcome #{:aggregation :composition}
                                         :motivation-goal #{:aggregation :composition}
                                         :motivation-requirement #{:aggregation :composition}}}
               (apply metamodel business-layer)
               (apply metamodel application-layer)
               (apply metamodel technology-layer))))
