(ns test-dsl-if-fix
  (:require [nirvikalpa.shader.dsl :as dsl]
            [clojure.pprint :refer [pprint]]))

(println "\n=== Testing DSL if-else with let forms ===\n")

;; Test 1: Simple ternary (should use :if)
(println "Test 1: Simple ternary")
(let [result (macroexpand-1 '(dsl/deffragment test-simple [x :f32]
                               :output [:color :vec4f :location 0]
                               (if (> x 0.5)
                                 (vec4f 1.0 0.0 0.0 1.0)
                                 (vec4f 0.0 1.0 0.0 1.0))))]
  (pprint result))

(println "\n---\n")

;; Test 2: let in then branch (should use :if-else block form)
(println "Test 2: let in then branch")
(let [result (macroexpand-1 '(dsl/deffragment test-let-then [x :f32]
                               :output [:color :vec4f :location 0]
                               (if (> x 0.5)
                                 (let [r 1.0
                                       g 0.0]
                                   (vec4f r g 0.0 1.0))
                                 (vec4f 0.0 1.0 0.0 1.0))))]
  (pprint result))

(println "\n---\n")

;; Test 3: Nested if-else with let forms (like bezier)
(println "Test 3: Nested if-else with multiple let bindings")
(let [result (macroexpand-1 '(dsl/deffragment test-bezier-like [h :f32 p :f32]
                               :output [:color :vec4f :location 0]
                               (let [res 0.0]
                                 (if (>= h 0.0)
                                   (let [h-sqrt (sqrt h)
                                         t (clamp h-sqrt 0.0 1.0)]
                                     (set! res (* t t)))
                                   (let [z (sqrt (- p))
                                         m (cos z)]
                                     (set! res m)))
                                 (vec4f res res res 1.0))))]
  (pprint result))

(println "\n=== Done ===\n")
(System/exit 0)
