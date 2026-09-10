(defproject armate "2.1.0-SNAPSHOT"
  :description "Analysis ArchiMate diagrams"
  :license {:name "Private"}
  :main armate.mcp.server
  :aot [armate.mcp.server
        armate.archimate.metamodel.meta
        armate.archimate.metamodel.appendix]
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [org.clojure/data.json "2.5.2"]
                 [org.clojure/math.combinatorics "0.3.0"]
                 [org.clojure/tools.logging "1.3.0"]
                 [ch.qos.logback/logback-classic "1.5.8"]
                 [camel-snake-kebab "0.4.3"]
                 [clj-fuzzy "0.4.1"]]
  :jvm-opts ["-Xmx8g"
             "-Djdk.attach.allowAttachSelf"
             "-Dcasc.yaml.max.aliases=\"100\""]
  :repl-options {:init-ns armate.core}
  :profiles {:dev {:source-paths ["dev"]
                   :resource-paths ["resources"]}})
