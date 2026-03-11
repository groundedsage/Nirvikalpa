(ns test-dsl-macro
  "Quick test of DSL macro compilation"
  (:require [nirvikalpa.shader.dsl :as dsl]))

;; Test macro expansion of if with do blocks
(println "\n=== Test: Macro expansion of if with do blocks ===")
(def compiled-if
  (dsl/compile-simple-expr
   '(if (> x 0.5)
      (do
        (set! color.r 1.0)
        (set! color.g 0.0))
      (do
        (set! color.r 0.0)
        (set! color.g 1.0)))))

(println "Result:" compiled-if)
(println "Type:" (first compiled-if))

;; Test macro expansion of var with type
(println "\n=== Test: Macro expansion of var with type ===")
(def compiled-var
  (dsl/compile-simple-expr '(var output :VertexOutput)))

(println "Result:" compiled-var)

;; Test macro expansion of do block
(println "\n=== Test: Macro expansion of do block ===")
(def compiled-do
  (dsl/compile-simple-expr
   '(do
      (var temp 0.5)
      (set! color.r temp)
      (set! color.g temp))))

(println "Result:" compiled-do)

;; Test simple ternary if (should remain :if, not :if-else)
(println "\n=== Test: Simple ternary if ===")
(def compiled-ternary
  (dsl/compile-simple-expr '(if (> x 0.5) 1.0 0.0)))

(println "Result:" compiled-ternary)
(println "Type:" (first compiled-ternary))

(println "\n=== All macro compilation tests completed ===")
