(ns armate.mcp.server
  "Hand-rolled stdio JSON-RPC 2.0 MCP server for armate, mirroring the in-house `mcp`
   reference (mcp.server): `initialize` handshake, `tools/list`, `tools/call` dispatch over a
   `BufferedReader`/`OutputStreamWriter` on stdio, launched as an uberjar under Leiningen.

   The server keeps an in-memory model registry (see armate.mcp.registry) held in an atom;
   tools that mutate it return a fresh registry that the server persists."
  (:gen-class)
  (:require [clojure.tools.logging :as log]
            [clojure.string :as str]
            [clojure.data.json :as json]
            [armate.mcp.tools :as tools])
  (:import (java.io BufferedReader InputStreamReader OutputStreamWriter)))

(set! *warn-on-reflection* true)

(def protocol-version "2024-11-05")

(defn- json-rpc-error
  [id code message]
  {:jsonrpc "2.0" :id id :error {:code code :message message}})

(defn- json-rpc-result
  [id result]
  {:jsonrpc "2.0" :id id :result result})

(defn- parse-json
  [^String s]
  (try (json/read-str s :key-fn keyword) (catch Exception _ nil)))

(defn- write-json
  [^OutputStreamWriter w data]
  (.write w (json/write-str data))
  (.write w "\n")
  (.flush w))

(defn- initialize-handler
  [msg]
  (json-rpc-result (:id msg)
                   {:protocolVersion protocol-version
                    :capabilities {:tools {:listChanged false}}
                    :serverInfo {:name "armate-mcp" :version "2.1.0"}}))

(defn- notification?
  "A JSON-RPC notification is a request without an id; the server must not respond to it."
  [msg]
  (or (nil? (:id msg))
      (str/starts-with? (str (:method msg)) "notifications/")))

(defn- handle-method
  [msg registry-atom]
  (let [method (:method msg)
        id (:id msg)
        params (:params msg)]
    (try
      (if (notification? msg)
        [(log/warn (format "ignoring notification (no response): %s" method)) @registry-atom]
        (case method
          "initialize" [(initialize-handler msg) @registry-atom]
          "tools/list"
          [(json-rpc-result id {:tools (tools/tool-list)}) @registry-atom]
          "tools/call"
          (let [tool-name (if params (get params :name) nil)
                args (if params (get params :arguments) {})]
            (if tool-name
              (let [start (System/nanoTime)
                    [registry' result] (tools/handle-tool tool-name @registry-atom args)
                    elapsed-ms (/ (- (System/nanoTime) start) 1e6)
                    content (:content result)
                    is-error? (:isError result)]
                (if is-error?
                  (log/warn (format "tools/call %s args=%s failed error=%s in %.1f ms"
                                    tool-name (pr-str args) content elapsed-ms))
                  (log/info (format "tools/call %s args=%s ok in %.1f ms"
                                    tool-name (pr-str args) elapsed-ms)))
                [(json-rpc-result id {:content content :isError is-error?})
                 registry'])
              (do (log/warn "tools/call missing name params=" (pr-str params))
                  [(json-rpc-error id -32602 "Missing tool name") @registry-atom])))
          [(do (log/warn (format "method not found: %s" method))
              (json-rpc-error id -32601 (str "Method not found: " method))) @registry-atom]))
      (catch Exception e
        (log/error e (str "handler threw for method=" method))
        [(json-rpc-error id -32603 (.getMessage e)) @registry-atom]))))

(defn start
  []
  (let [reader (BufferedReader. (InputStreamReader. System/in))
        writer (OutputStreamWriter. System/out)
        registry (atom {})]
    (loop [line (.readLine reader)]
      (when line
        (let [msg (parse-json line)]
          (when msg
            (let [[response new-registry] (handle-method msg registry)]
              (when response (write-json writer response))
              (when new-registry (reset! registry new-registry)))))
        (recur (.readLine reader))))))

(defn -main
  [& _]
  (start))