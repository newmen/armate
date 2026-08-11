(defproject armate "1.0.0-SNAPSHOT"
  :description "Analysis ArchiMate diagrams"
  :license {:name "Private"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [org.clojure/core.cache "1.1.234"]
                 [org.clojure/data.csv "1.1.0"]
                 [org.clojure/math.combinatorics "0.3.0"]
                 [org.clojure/tools.logging "1.3.0"]
                 [com.clojure-goes-fast/clj-memory-meter "0.3.0"]
                 [org.clj-commons/claypoole "1.2.2"]
                 [ch.qos.logback/logback-classic "1.5.8"]
                 [camel-snake-kebab "0.4.3"]
                 [cheshire "5.13.0"]
                 [clj-commons/clj-yaml "1.0.28"]
                 [clj-http "3.13.0"]]
  :jvm-opts ["-Xmx8g"
             "-Djdk.attach.allowAttachSelf"
             "-Dcasc.yaml.max.aliases=\"100\""]
  :repl-options {:init-ns armate.core}
  :profiles {:dev {:source-paths ["dev"]
                   :resource-paths ["resources"]}})
