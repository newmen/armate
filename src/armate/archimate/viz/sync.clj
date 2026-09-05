(ns armate.archimate.viz.sync
  (:require [clojure.string :as s]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [armate.archimate.archi.parser :as arr]
            [armate.archimate.viz.saver :as svr]))

(defn dir-exists?
  [dir-path]
  (let [dir (io/file dir-path)]
    (and (.exists dir)
         (.isDirectory dir))))

(defn create-dir
  [dir-path]
  (or (dir-exists? dir-path)
      (.mkdirs (io/file dir-path))))

(defn sync-files
  "Синхронизирует .archimate файл с выходной директорией .puml.
   Для каждой View модели создаёт PlantUML диаграммой.
   Формат имени: {имя_archimate_модели}-{имя_view}.puml"
  [model-path out-dir]
  (if (create-dir out-dir)
    (let [model (arr/get-model model-path)
          path-prefix (-> (str out-dir "/")
                          (s/replace #"//" "/"))
          views (get-in model [:maps :views])]
      (doseq [[view-key _] views]
        (let [graph (arr/get-views-graph model view-key)
              view-name (s/replace view-key #"\s" "_")
              out-file-path (str path-prefix view-name ".puml")]
          (log/info (str "Creating " out-file-path " ..."))
          (svr/save-puml graph out-file-path)))
      (println (str (count views) " have been synchronized")))
    (throw (ex-info "Can't craete directory"
                    {:out-dir out-dir}))))
