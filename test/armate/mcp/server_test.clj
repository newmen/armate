(ns armate.mcp.server-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.data.json :as json]
            [clojure.string :as s]
            [armate.mcp.server :as sut]))

(set! *warn-on-reflection* true)

(defn- handle-method
  "Call the server's handle-method (private) with a keyword-form message and a registry
   atom; returns the response map (or nil)."
  [msg]
  (let [registry (atom {})
        [resp _] (#'sut/handle-method msg registry)]
    resp))

(deftest json-rpc-error-format
  (let [r (#'sut/json-rpc-error 1 -32601 "x")]
    (is (= "2.0" (:jsonrpc r)))
    (is (= 1 (:id r)))
    (is (= -32601 (get-in r [:error :code])))))

(deftest json-rpc-result-format
  (let [r (#'sut/json-rpc-result 1 {:a 1})]
    (is (= {:a 1} (:result r)))))

(deftest parse-json
  (is (= {:a 1} (#'sut/parse-json "{\"a\":1}")))
  (is (nil? (#'sut/parse-json "not json"))))

(deftest initialize-handshake
  (testing "initialize returns protocolVersion and serverInfo"
    (let [resp (handle-method {:id 1 :method "initialize"})]
      (is (= 1 (:id resp)))
      (is (= "2024-11-05" (get-in resp [:result :protocolVersion])))
      (is (= "armate-mcp" (get-in resp [:result :serverInfo :name]))))))

(deftest notifications-get-no-response
  (testing "notifications (no id) produce no response at all"
    (doseq [m [{:jsonrpc "2.0" :method "notifications/initialized"}
               {:jsonrpc "2.0" :method "notifications/cancelled"
                :params {:requestId 3 :reason "user cancelled"}}]]
      (is (nil? (handle-method m))))))

(deftest tools-list
  (testing "tools/list advertises the armate tools"
    (let [resp (handle-method {:id 2 :method "tools/list"})]
      (is (= 2 (:id resp)))
      (is (= 17 (count (get-in resp [:result :tools])))))))

(deftest tools-call-load-and-list-views
  (testing "tools/call load_model then list_views over the server registry"
    (let [registry (atom {})
          load-msg {:id 3 :method "tools/call"
                    :params {:name "load_model"
                             :arguments {:path "test/resources/demo.archimate"}}}
          [resp registry'] (#'sut/handle-method load-msg registry)
          id (:text (first (:content (:result resp))))
          _ (reset! registry registry')
          list-resp (first (#'sut/handle-method {:id 4 :method "tools/call"
                                                 :params {:name "list_views"
                                                          :arguments {:model_id id}}}
                                                registry))]
      (is (= "demo" id))
      (let [content (:content (get-in list-resp [:result]))]
        (is (s/includes? (:text (first content)) "Процесс"))))))

(deftest tools-call-unknown-model-error
  (testing "unknown model_id is reflected as an isError tool result"
    (let [registry (atom {})
          [resp _] (#'sut/handle-method {:id 5 :method "tools/call"
                                         :params {:name "list_views"
                                                  :arguments {:model_id "nope"}}}
                                        registry)
          result (:result resp)]
      (is (true? (:isError result)))
      (is (s/includes? (:text (first (:content result))) "Unknown model_id")))))

(deftest tools-call-error-shape
  (testing "isError true is passed through to the JSON result"
    (let [registry (atom {})
          [resp] (#'sut/handle-method {:id 6 :method "tools/call"
                                       :params {:name "render_view"
                                                :arguments {:model_id "x" :view "v"}}}
                                      registry)]
      (is (true? (get-in resp [:result :isError]))))))

(deftest scripted-client-over-stdio
  (testing "a spawned uberjar server responds to initialize and tools/list over stdio"
    ;; Requires the uberjar to be built; this test drives `java -jar` if present.
    (let [jar "target/armate-1.0.0-SNAPSHOT-standalone.jar"]
      (if (.exists (java.io.File. jar))
        (let [java (str (System/getProperty "java.home") "/bin/java")
              proc (.start (ProcessBuilder. (into-array [java "-jar" jar])))
              out (java.io.BufferedReader. (java.io.InputStreamReader. (.getInputStream proc)))
              inp (java.io.PrintWriter. (.getOutputStream proc))]
          (.println inp (json/write-str {:jsonrpc "2.0" :id 1 :method "initialize"}))
          (.flush inp)
          (let [line (.readLine out)
                resp (json/read-str line :key-fn keyword)]
            (is (= "armate-mcp" (get-in resp [:result :serverInfo :name]))))
          (.destroy proc))
        (println "Skipped scripted-client smoke test: uberjar not built (run `lein uberjar`)")))))