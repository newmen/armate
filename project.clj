(defproject armate "0.1.1-SNAPSHOT"
  :description "The linter for PlantUML (ArchiMate) diagrams"
  :license {:name "Private"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [org.clojure/math.combinatorics "0.3.0"]
                 [org.clojure/tools.logging "1.3.0"]
                 [ch.qos.logback/logback-classic "1.5.8"]]
  :jvm-opts ["-Xmx8g"
             "-Djdk.attach.allowAttachSelf"
             "-Dcasc.yaml.max.aliases=\"100\""]
  :repl-options {:init-ns armate.core})
