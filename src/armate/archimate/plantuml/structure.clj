(ns armate.archimate.plantuml.structure
  (:require [clojure.string :as s]
            [armate.archimate.plantuml.lex :as lex]))

(defn left-to-right?
  [parts]
  (= ["left" "to" "right" "direction"]
     parts))

(defn get-blocks
  [content]
  (let [lines (s/split-lines content)
        indexes (map inc (range))]
    (loop [lines-is (map vector lines indexes)
           vars {}
           blocks []
           outer-blocks '()]
      (if (empty? lines-is)
        (if (seq outer-blocks)
          (throw (ex-info "Unclosed block" {:blocks (vec outer-blocks)}))
          blocks)
        (let [line-i (first lines-is)
              line (s/trim (first line-i))
              index (second line-i)
              rest-lines-is (rest lines-is)]
          (if (or (empty? line)
                  (s/starts-with? line "'"))
            (recur rest-lines-is vars blocks outer-blocks)
            (if-let [[name value] (lex/parse-variable line)]
              (recur rest-lines-is
                     (assoc vars name value)
                     blocks
                     outer-blocks)
              (let [full-line (lex/apply-variables vars line)
                    {parts :parts
                     block? :block?} (lex/get-parts full-line)]
                (if (left-to-right? parts)
                  (recur rest-lines-is vars blocks outer-blocks)
                  (if block?
                    (recur rest-lines-is
                           vars
                           blocks
                           (cons {:parts parts :line index} outer-blocks))
                    (if (seq outer-blocks)
                      (let [outer-block (first outer-blocks)]
                        (if (= full-line "}")
                          (if-let [prev-block (second outer-blocks)]
                            (recur rest-lines-is
                                   vars
                                   blocks
                                   (cons (update prev-block :props
                                                 (fnil conj [])
                                                 outer-block)
                                         (rest (rest outer-blocks))))
                            (recur rest-lines-is
                                   vars
                                   (conj blocks outer-block)
                                   (rest outer-blocks)))
                          (recur rest-lines-is
                                 vars
                                 blocks
                                 (cons (update outer-block :props
                                               (fnil conj [])
                                               {:parts parts :line index})
                                       (rest outer-blocks)))))
                      (recur rest-lines-is
                             vars
                             (conj blocks {:parts parts :line index})
                             outer-blocks))))))))))))