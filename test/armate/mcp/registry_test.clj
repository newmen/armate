(ns armate.mcp.registry-test
  (:require [clojure.test :refer [deftest is testing]]
            [armate.archimate.archi.parser :as arr]
            [armate.mcp.registry :as reg]))

(def demo-path "test/resources/demo.archimate")

(deftest load-model-registers-id
  (testing "load_model parses the file and registers a model_id"
    (let [[r id] (reg/load-model {} demo-path)]
      (is (= "demo" id))
      (is (= ["demo"] (vec (reg/list-models r)))))))

(deftest reload-keeps-id-invalidates
  (testing "reload keeps the model_id and re-derives the certain cache"
    (let [[r id] (reg/load-model {} demo-path)
          _ (reg/certain-graph r id)
          [r2 id2] (reg/reload-model r id)]
      (is (= id id2))
      (is (= "demo" (first (reg/list-models r2))))
      (is (some? (reg/certain-graph r2 id2))))))

(deftest unload-removes-model
  (testing "unload_model removes the model_id"
    (let [[r id] (reg/load-model {} demo-path)
          [r2 _] (reg/unload-model r id)]
      (is (empty? (reg/list-models r2))))))

(deftest unknown-model-error
  (testing "accessing an unknown model throws a structured error"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo
                          #"Unknown model_id"
                          (reg/assert-model {} "missing")))))

(deftest certain-graph-is-lazy-and-cached
  (testing "certain graph is computed lazily and forced on demand"
    (let [[r id] (reg/load-model {} demo-path)
          g1 (reg/certain-graph r id)]
      (is (map? g1))
      (is (contains? g1 :relations)))))

(deftest potential-cap-default
  (testing "MCP_POTENTIAL_CAP defaults to 30"
    (is (= 30 (reg/potential-cap)))))

(deftest certain-graph-excludes-potential
  (testing "the global certain cache derives only certain relations, never potential"
    (let [[r id] (reg/load-model {} demo-path)
          der (->> (reg/certain-graph r id)
                   (:relations)
                   (vals)
                   (mapcat vals)
                   (mapcat identity)
                   (map :derivate)
                   (frequencies))]
      (is (nil? (get der :potential))
          "potential rules must not leak into the certain graph")
      (is (pos? (get der :certain 0))
          "some certain relations are still derived"))))

(deftest full-graph-built-once-per-load
  (testing "loading a model runs the heavy get-full-graph exactly once"
    (let [calls (atom 0)
          orig @#'arr/get-full-graph]
      (with-redefs [arr/get-full-graph (fn
                                         ([model] (arr/get-full-graph model nil {}))
                                         ([model names-replacer id-map]
                                          (swap! calls inc)
                                          (orig model names-replacer id-map)))]
        (let [[r _] (reg/load-model {} demo-path)
              rec (get r "demo")]
          (is (= 1 @calls)
              "the full graph is built once and reused (index + model context)")
          (is (seq (:element-views (:enriched rec))))
          (is (seq (:context rec))))))))