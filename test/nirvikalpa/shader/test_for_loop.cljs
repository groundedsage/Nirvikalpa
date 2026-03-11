(ns starter.samples.test-for-loop
  "Test for-loop v2 DSL feature"
  (:require [nirvikalpa.api.renderer-2d :as r2d])
  (:require-macros [nirvikalpa.shader.dsl :refer [deffragment]]
                   [nirvikalpa.shader.dsl-v2 :refer [for-loop var-block assign]]))

(deffragment test-for-fragment [uv :vec2f]
  :uniforms [[color :vec4f :group 0 :binding 0]]
  :output [:out_color :vec4f :location 0]
  (do
    (var-block sum :f32)
    (for-loop [i 0 (< i 10) (+ i 1)]
      (assign sum (+ sum 0.1)))
    (vec4f sum 0.0 0.0 1.0)))

(defn TestForLoop [{:keys [node !render-id]}]
  (r2d/render-static!
   node
   test-for-fragment
   [[1.0 0.0 0.0 1.0]]))
