(ns armate.archimate.plantuml.lint
  "ArchiMate-aware linting of a generated PlantUML document.

   Wraps `armate.archimate.plantuml.parser/analyze-content` and turns its raw `:lints`
   into agent-readable report entries and a formatted summary. Pure: no MCP or registry
   dependency."
  (:require [clojure.string :as s]
            [armate.archimate.plantuml.parser :as prr]))

(def ^:private level-rank
  {:error 0
   :warn 1})

(defn- lint-line
  "The source line of a raw @lint, or nil when the lint carries no line."
  [lint]
  (or (get-in lint [:body :line])
      (get-in lint [:connector :line])
      (:line lint)))

(defn- element-ref
  [body]
  (or (when-let [alias (:alias body)]
        (if-let [title (:title body)]
          (str alias " (\"" title "\")")
          (str alias)))
      (when (:from body)
        (str (:type body) " " (:from body) " -> " (:to body)))))

(defn- duplicate-text
  [body]
  (if-let [ref (element-ref body)]
    (str "duplicate definition: " ref)
    "duplicate definition"))

(defn- connector-text
  "Explain one `:incorrect-connector-using` sub-problem @item."
  [{:keys [connector error relations]}]
  (let [alias (:alias connector)]
    (case error
      :different-using-relations
      (str "connector " alias " mixes different relationship types ("
           (s/join ", " (map name relations)) ")")
      :no-outgoing-relations (str "connector " alias " has no outgoing relationships")
      :no-incoming-relations (str "connector " alias " has no incoming relationships")
      :excess-connector
      (str "connector " alias " connects exactly one incoming and one outgoing "
           "relationship, so it is redundant")
      (str "connector " alias " is used incorrectly (" (name error) ")"))))

(def ^:private fixed-explanations
  {:missing-start "no @startuml directive"
   :missing-end "no @enduml directive"
   :missing-archimate-include "no !include <archimate/Archimate> directive"})

(defn- nm
  "Render a value as a bare name: a keyword's name, anything else via `str` (the parser
   stores raw alias strings for unresolved relation endpoints)."
  [x]
  (if (keyword? x) (name x) (str x)))

(defn- explain
  "A human explanation for a raw @lint's @kind, including concrete data when available."
  [kind body]
  (or (fixed-explanations kind)
      (case kind
        :unknown (str "line matches no known ArchiMate construct: "
                      (s/join " " (map str (:parts body))))
        :duplicate (duplicate-text body)
        :undefined-element-type (str "element " (or (:alias body) (:title body))
                                     " has no recognized ArchiMate type")
        :undefined-relation-type (str "relation \"" (:raw body)
                                      "\" is not an ArchiMate relationship")
        :undefined-relation-from (str "relationship source \"" (:from body)
                                      "\" is not defined")
        :undefined-relation-to (str "relationship target \"" (:to body)
                                    "\" is not defined")
        :unsupporting-element-type (str "element type \"" (nm (:kind body))
                                        "\" is not an ArchiMate element type")
        :unsupporting-element-layer (str "layer \"" (nm (:layer body))
                                         "\" is not an ArchiMate layer")
        :undefined-element-skin (str "skin \"" (:skin body)
                                     "\" is not defined"
                                     (when-let [a (:alias body)] (str " (element " a ")")))
        :unspecified-relation-type (str "relationship \"" (nm (:type body))
                                        "\" is not allowed between "
                                        (nm (:from body)) " and " (nm (:to body)))
        :relation-between-elements-already-present
        (str "the reverse relationship already exists between "
             (nm (:from body)) " and " (nm (:to body)))
        (str "problem of kind " (name kind)))))

(defn- entry
  "Normalize one display problem into `{:level :kind :line :in :text}`."
  [lint kind line text]
  {:level (:level lint)
   :kind kind
   :line line
   :in (:in lint)
   :text text})

(defn- lint->entries
  "Expand one raw @lint into one or more normalized entries (connector sub-problems
   become one entry each). Never drops a lint: a connector lint without sub-problem data
   still yields one generic entry."
  [lint]
  (let [kind (:kind lint)]
    (if (= :incorrect-connector-using kind)
      (if (seq (:data lint))
        (mapv (fn [item]
                (entry lint kind (:line (:connector item)) (connector-text item)))
              (:data lint))
        [(entry lint kind (lint-line lint) "connector is used incorrectly")])
      [(entry lint kind (lint-line lint) (explain kind (:body lint)))])))

(defn- entry-cmp
  "Sort errors before warnings; within a level by line; line-less entries last."
  [a b]
  (let [la (level-rank (:level a) 99)
        lb (level-rank (:level b) 99)]
    (if (not= la lb)
      (compare la lb)
      (let [na (some? (:line a))
            nb (some? (:line b))]
        (cond
          (not= na nb) (if na -1 1)
          (not na) 0
          :else (compare (:line a) (:line b)))))))

(defn- format-lint
  "Render one normalized @entry as a report line:
   `<LEVEL> <location> <context> <kind>: <text>`."
  [{:keys [level line in kind text]}]
  (let [level-str (s/upper-case (name level))
        location (if line (str "line " line) "[document]")
        ctx (when-let [c (when (seq in) (str "[in " (s/join " " (map name in)) "]"))]
              (str c " "))]
    (str level-str " " location " " (or ctx "") (name kind) ": " text)))

(defn- lint-error
  "Convert a PlantUML parse defect exception @e into a normalized entry, or nil when the
   exception is not a recognized document defect. Detection uses the parser's machine
   signal (`:armate/error`), not its human-readable message."
  [e]
  (when (= :unclosed-block (:armate/error (ex-data e)))
    (let [line (:line (first (:blocks (ex-data e))))]
      {:level :error
       :kind :unclosed-block
       :line line
       :in [:parse]
       :text (str "unclosed { block"
                  (when line (str " starting at line " line)))})))

(defn lint-content
  "Lint PlantUML @content and return `{:lints [...] :errors n :warnings n}`.

   Each lint is a normalized map `{:level :kind :line :in :text}`, sorted errors-first
   then by line (line-less last). A PlantUML parse defect (an unclosed block) becomes a
   single `:unclosed-block` error rather than an exception. Other exceptions propagate."
  [content]
  (let [entries (try
                  (->> (:lints (prr/analyze-content content))
                       (mapcat lint->entries)
                       (vec))
                  (catch clojure.lang.ExceptionInfo e
                    (if-let [le (lint-error e)]
                      [le]
                      (throw e))))
        sorted (sort entry-cmp entries)]
    {:lints sorted
     :errors (count (filter #(= :error (:level %)) sorted))
     :warnings (count (filter #(= :warn (:level %)) sorted))}))

(defn format-summary
  "Render a @summary (as returned by `lint-content`) as the report text: a verdict line,
   one line per problem, and a closing instruction when any problem exists."
  [{:keys [lints errors warnings]}]
  (let [verdict (if (empty? lints)
                  "OK: no problems"
                  (str errors " errors, " warnings " warnings"))
        lines (map format-lint lints)
        body (s/join "\n" (cons verdict lines))]
    (if (empty? lints)
      body
      (str body "\n\nFix the errors and warnings above and lint again"))))