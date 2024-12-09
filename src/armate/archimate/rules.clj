(ns armate.archimate.rules)

(def possible-relation-types
  #{:access
    :access_r
    :access_rw
    :access_w
    :aggregation
    :assignment
    :association
    :composition
    :flow
    :influence
    :realization
    :serving
    :specialization
    :triggering})

(def base-relations
  {:application-collaboration {:application-component #{:aggregation}
                               :application-interface #{:aggregation :flow}
                               :business-actor #{:flow}
                               :business-role #{:flow}}
   :application-component {:application-component #{:aggregation :composition}
                           :application-interface #{:composition :flow :triggering}
                           :application-service #{:realization}
                           :technology-node #{:assignment}}
   :application-data-object {:application-data-object #{:aggregation :association :composition :specialization}}
   :application-interface {:application-data-object #{:access_r :access_w}
                           :application-service #{:assignment}
                           :application-component #{:flow}
                           :business-actor #{:flow}
                           :business-function #{:serving}
                           :business-process #{:serving}
                           :business-role #{:flow}}
   :application-service {:business-function #{:serving}
                         :business-process #{:serving}
                         :business-service #{:realization :serving}}
   :business-actor {:application-component #{:assignment}
                    :application-interface #{:triggering}
                    :business-actor #{:specialization}
                    :business-function #{:assignment}
                    :business-process #{:assignment}
                    :business-role #{:assignment}}
   :business-collaboration {:business-interaction #{:assignment}
                            :business-role #{:aggregation}}
   :business-event {:business-event #{:composition :triggering}
                    :business-function #{:triggering}
                    :business-interaction #{:triggering}
                    :business-process #{:triggering}}
   :business-function {:business-actor #{:serving}
                       :business-event #{:triggering}
                       :business-function #{:aggregation :composition :triggering :flow}
                       :business-interaction #{:aggregation :composition :triggering}
                       :business-process #{:aggregation :composition :triggering :flow}
                       :business-service #{:realization}}
   :business-interaction {:business-function #{:aggregation :composition :triggering}
                          :business-interaction #{:aggregation :composition :triggering}
                          :business-process #{:aggregation :composition :triggering}}
   :business-process {:business-actor #{:serving}
                      :business-event #{:triggering}
                      :business-function #{:aggregation :composition :triggering :flow}
                      :business-interaction #{:aggregation :composition :triggering}
                      :business-process #{:aggregation :composition :triggering :flow}
                      :business-service #{:realization}}
   :business-product {:application-service #{:aggregation :composition}
                      :business-product #{:aggregation :composition :specialization}
                      :business-role #{:serving}
                      :business-service #{:aggregation :composition}}
   :business-role {:application-interface #{:triggering}
                   :business-function #{:assignment}
                   :business-process #{:assignment}
                   :business-role #{:specialization}}
   :business-service {:business-actor #{:serving}
                      :business-role #{:serving}}
   :technology-artifact {:application-component #{:realization}}
   :technology-collaboration {:application-interface #{:realization}
                              :technology-collaboration #{:aggregation}
                              :technology-node #{:aggregation}
                              :technology-system-software #{:aggregation}}
   :technology-interaction {:technology-collaboration #{:access}
                            :technology-node #{:access}
                            :technology-system-software #{:access}}
   :technology-node {:application-component #{:assignment}
                     :technology-artifact #{:assignment}
                     :technology-system-software #{:composition}}
   :technology-path {:technology-node #{:aggregation}
                     :technology-collaboration #{:aggregation}}
   :technology-system-software {:application-interface #{:serving}
                                :application-component #{:realization}
                                :technology-artifact #{:assignment}
                                :technology-system-software #{:aggregation :composition}}})

(def possible-elements
  (set (concat (keys base-relations)
               (mapcat keys (vals base-relations)))))

(def possible-relations
  (let [ext-elements (conj possible-elements :group)
        pos-group-aggs (into {} (map #(vector % #{:aggregation}) ext-elements))
        pos-group-rels (update pos-group-aggs :group conj :association)]
    (-> base-relations
        (assoc :group pos-group-rels)
        (assoc-in [:business-actor :group] #{:assignment}))))
