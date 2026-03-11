(ns starter.samples.rendering-2d.shapes.point
  "2D Point Rendering

   SDF Formula: Circle with small radius"
  (:require [nirvikalpa.api.renderer-2d :as r2d])
  (:require-macros [nirvikalpa.shader.dsl :refer [deffragment]]))

;; Uniforms: [pos.x, pos.y, size, unused]
(deffragment point-fragment [uv :vec2f]
  :uniforms [[color :vec4f :group 0 :binding 0]
             [params :vec4f :group 0 :binding 1]]
  :output [:out_color :vec4f :location 0]
  (let [pos (vec2f params.x params.y)
        size params.z
        ;; Point is just a small circle
        dist (- (distance uv pos) size)
        ;; Skia-style fwidth-based AA
        edge-width (fwidth dist)
        alpha (smoothstep (* 0.7 edge-width) (* (- 0.7) edge-width) dist)]
    (vec4f color.rgb (* color.a alpha))))

(defn Render2DPoint [{:keys [node !render-id]}]
  (r2d/render-static!
   node
   point-fragment
   [[1.0 1.0 1.0 1.0]         ; white point
    [0.5 0.5 0.01 0.0]]))     ; point at (0.5, 0.5), size 0.01 (small)
