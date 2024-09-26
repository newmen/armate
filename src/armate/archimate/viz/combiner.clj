(ns armate.viz.combiner
  (:require [clojure.string :as s]
            [armate.derivation.match :as mch]))

(def indent "  ")

(def sort-line-key
  #(:line % 999999))

(defn sort-by-lines
  [items]
  (sort-by sort-line-key items))

(defn sbl-map
  [f items]
  (->> (sort-by-lines items)
       (map f)))

(defn wrap-str
  [text]
  (str "\"" text "\""))

(defn wrap-fur
  [text]
  (str "<<" text ">>"))

(defn get-start
  [start]
  (str "@startuml" (if-let [title (:title start)]
                     (str " " (wrap-str title))
                     "")))

(def end
  "@enduml")

(defn get-include
  [include]
  (str "!include <" (:package include) ">"))

(defn shift
  [lines]
  (->> (s/split lines #"\n")
       (map (partial str indent))
       (s/join "\n")))

(defn nest
  [title items item-f]
  (if (empty? items)
    title
    (s/join "\n" (concat [(str title " {")]
                         (->> (sort-by-lines items)
                              (map (comp shift item-f)))
                         ["}"]))))

(defn get-skin
  [skin]
  (let [shape (:shape skin)
        alias (:alias skin)
        target (if shape
                 (str shape (if alias
                              (wrap-fur alias)
                              ""))
                 "")]
    (nest (str "skinparam " target)
          (:props skin)
          (comp (partial s/join " ") :parts))))

(defn get-type
  [type]
  (str "sprite " (:alias type) " jar:archimate/" (name (:kind type))))

(defn make-call
  [func args]
  (str func "(" (s/join ", " args) ")"))

(declare get-element)
(defn build-element
  [parts-f element]
  (let [parts (parts-f element)]
    (nest (s/join " " parts)
          (:inside element)
          get-element)))

(def get-group
  (partial build-element
           (fn [{:keys [type alias title color]}]
             (let [func (s/capitalize (name type))
                   args (cons alias
                              (when title
                                [(wrap-str title)]))]
               (concat [(make-call func args)]
                       (when color
                         [color]))))))

(def get-shape
  (partial build-element
           (fn [{:keys [shape title alias type skin layer color]}]
             (let [cut? (= alias title)
                   title2 (if cut? title (wrap-str title))]
               (concat [shape title2]
                       (when-not cut?
                         [(str "as " alias)])
                       (->> [type skin]
                            (remove nil?)
                            (map wrap-fur)
                            (s/join "")
                            (list))
                       (when-not skin
                         (when layer
                           [(str "#" (s/capitalize (name layer)))]))
                       (when color
                         [color]))))))

(defn get-element
  [element]
  (if (= :group (:kind element))
    (get-group element)
    (get-shape element)))

(defn get-relation
  [[from to {:keys [type direction raw reverse? desc]}]]
  (if raw
    (let [parts [from raw to]
          parts2 (if reverse? (reverse parts) parts)
          parts3 (if desc (into parts2 [desc]) parts2)]
      (s/join " " parts3))
    (let [func (s/join "" (concat ["Rel_" (s/capitalize (name type))]
                                  (when direction
                                    [(str "_" (s/capitalize (name direction)))])))
          args [from to]
          args2 (if desc (into args [desc]) args)]
      (make-call func args2))))

(defn resort
  [elements]
  (loop [elements (vals elements)
         added #{}
         result []]
    (println (first elements))
    (if (empty? elements)
      result
      (let [tail (rest elements)
            element (first elements)
            in (:in element)]
        (if (or (not in)
                (added in))
          (recur tail
                 (conj added (:alias element))
                 (conj result element))
          (recur (concat tail [element])
                 added
                 result))))))

(defn reorganize
  [elements]
  (vals (reduce (fn [acc element]
                  (let [alias (:alias element)
                        in (:in element)]
                    (if in
                      (-> acc
                          (dissoc alias)
                          (update-in [in :inside] (fnil conj []) element))
                      acc)))
                elements
                (reverse (resort elements)))))

(defn get-relations
  [key context]
  (->> (mch/get-relationships identity identity (key context))
       (remove (comp (partial = :nesting) :derivate last))
       (sort-by (comp sort-line-key last))
       (map get-relation)))

(defn generate-puml
  [context]
  (->> [[(get-start (:start context))]
        (sbl-map get-include (vals (:includes context)))
        (sbl-map get-skin (vals (:skins context)))
        (sbl-map get-type (vals (:types context)))
        (sbl-map get-element (reorganize (:elements context)))
        (get-relations :relations context)
        (get-relations :hidden context)
        [end]]
       (remove empty?)
       (map (partial s/join "\n"))
       (s/join "\n\n")))
