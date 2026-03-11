#!/usr/bin/env clojure

(require '[nirvikalpa.shader.dsl :as dsl])
(require '[clojure.pprint :as pp])

(println "====================================")
(println "DSL Macro Testing (Pure Clojure)")
(println "====================================\n")

(defn test-compilation
  "Test DSL compilation and print results"
  [name form]
  (println "### Test:" name "###")
  (try
    (let [result (dsl/compile-body [form])]
      (println "✅ SUCCESS")
      (println "AST:")
      (pp/pprint result)
      (println))
    (catch Exception e
      (println "❌ FAILED")
      (println "Error:" (.getMessage e))
      (when (ex-data e)
        (println "Data:" (ex-data e)))
      (println))))

;; Test 1: Simple if-block
(test-compilation "Simple if-block"
  '(if-block (> "uv.x" 0.5)
     (vec4f 1.0 0.0 0.0 1.0)
     (vec4f 0.0 1.0 0.0 1.0)))

;; Test 2: if-block with let-block branches  
(test-compilation "if-block with let-block"
  '(if-block (> "uv.x" 0.5)
     (let-block [r 1.0] (vec4f r 0.0 0.0 1.0))
     (let-block [g 1.0] (vec4f 0.0 g 0.0 1.0))))

;; Test 3: var-block with assign
(test-compilation "var-block with assign"
  '(let [x 1.0]
     (do
       (var-block res :f32)
       (if-block (> x 0.5)
         (assign res 1.0)
         (assign res 0.0))
       res)))

;; Test 4: do flattens nested let
(test-compilation "do flattens nested let"
  '(let [x 1.0]
     (do
       (var-block res :f32)
       (let [y 2.0]
         (+ x y)))))

;; Test 5: Complex Bezier pattern
(test-compilation "Bezier pattern"
  '(let [a 1.0 b 2.0 h 3.0]
     (do
       (var-block res :f32)
       (if-block (>= h 0.0)
         (let-block [h_sqrt (sqrt h)
                     x (* h_sqrt 2.0)]
           (assign res x))
         (let-block [z (sqrt (- h))]
           (assign res z)))
       (let [final (* res 2.0)]
         (vec4f final 0.0 0.0 1.0)))))

(println "\n====================================")
(println "All DSL Tests Complete")
(println "====================================")
