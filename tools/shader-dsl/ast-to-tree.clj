#!/usr/bin/env clojure
;; Convert AST to readable tree visualization
;; Usage: clojure -M tools/shader-dsl/ast-to-tree.clj '<shader-form>'

(require '[nirvikalpa.shader.dsl :as dsl])
(require '[clojure.string :as str])

(defn ast->tree
  "Convert AST vector to indented tree visualization"
  [node indent]
  (cond
    ;; Keyword-prefixed vector (AST node)
    (and (vector? node) (keyword? (first node)))
    (let [op (first node)
          args (rest node)]
      (str (str/join "" (repeat indent "  "))
           "├─ " (name op) "\n"
           (str/join "" (map #(ast->tree % (inc indent)) args))))
    
    ;; Plain vector
    (vector? node)
    (str (str/join "" (repeat indent "  "))
         "├─ [" (str/join " " (map #(if (string? %) (str "\"" % "\"") %) node)) "]\n")
    
    ;; String
    (string? node)
    (str (str/join "" (repeat indent "  "))
         "├─ \"" node "\"\n")
    
    ;; Number or keyword
    :else
    (str (str/join "" (repeat indent "  "))
         "├─ " (pr-str node) "\n")))

(defn visualize-shader
  "Compile shader form and visualize AST as tree"
  [form]
  (println "Input form:")
  (println form)
  (println "\nCompiled AST tree:")
  (let [ast (dsl/compile-body [form])]
    (println (ast->tree ast 0))))

;; Example usage
(println "========================================")
(println "AST Tree Visualization")
(println "========================================\n")

(visualize-shader
  '(if-block (> "uv.x" 0.5)
     (let-block [r 1.0] (vec4f r 0.0 0.0 1.0))
     (let-block [g 1.0] (vec4f 0.0 g 0.0 1.0))))

(println "\n========================================")

(visualize-shader
  '(let [x 1.0]
     (do
       (var-block res :f32)
       (if-block (> x 0.5)
         (assign res 1.0)
         (assign res 0.0))
       res)))
