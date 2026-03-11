(ns test-dsl-wgsl-output
  (:require [nirvikalpa.shader.dsl :refer [deffragment defshader-fn]]
            [nirvikalpa.shader.codegen :as codegen]))

(println "\n=== Testing WGSL Generation for Nested If-Else ===\n")

;; Define the shader
(deffragment test-bezier-like [h :f32 p :f32]
  :output [:color :vec4f :location 0]
  (let [res 0.0]
    (if (>= h 0.0)
      (let [h-sqrt (sqrt h)
            t (clamp h-sqrt 0.0 1.0)]
        (set! res (* t t)))
      (let [z (sqrt (- p))
            m (cos z)]
        (set! res m)))
    (vec4f res res res 1.0)))

(println "Generated WGSL:")
(println test-bezier-like)

(System/exit 0)
