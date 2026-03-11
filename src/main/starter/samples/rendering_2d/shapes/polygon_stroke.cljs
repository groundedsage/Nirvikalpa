(ns starter.samples.rendering-2d.shapes.polygon-stroke
  "Polygon Stroke - Regular N-sided polygon outline"
  (:require [nirvikalpa.api.renderer-2d :as r2d])
  (:require-macros [nirvikalpa.shader.dsl :refer [deffragment]]))

;; Uniforms: [center.x, center.y, radius, stroke-width]
(deffragment polygon-stroke-fragment [uv :vec2f]
  :uniforms [[color :vec4f :group 0 :binding 0]
             [params :vec4f :group 0 :binding 1]]
  :output [:out_color :vec4f :location 0]
  (let [center (vec2f params.x params.y)
        radius params.z
        stroke-width params.w
        k (vec3f (- 0.866025404) 0.5 0.577350269)
        p (abs (- uv center))
        p-reflected (- p (* (* 2.0 (min (dot (vec2f k.x k.y) p) 0.0)) (vec2f k.x k.y)))
        p-final (- p-reflected (vec2f (clamp p-reflected.x (* (- k.z) radius) (* k.z radius)) radius))
        dist (* (distance p-final (vec2f 0.0 0.0)) (sign p-final.y))
        dist-to-stroke (- (abs dist) stroke-width)
        ;; Skia-style fwidth-based AA for retina-perfect edges
        edge-width (fwidth dist-to-stroke)
        alpha (smoothstep (* 0.7 edge-width) (* (- 0.7) edge-width) dist-to-stroke)]
    (vec4f color.rgb (* color.a alpha))))

(defn Render2DPolygonStroke [{:keys [node !render-id]}]
  (r2d/render-static!
   node
   polygon-stroke-fragment
   [[0.3 1.0 0.9 1.0]         ; cyan stroke
    [0.5 0.5 0.3 0.02]]))     ; hexagon: center (0.5, 0.5), radius 0.3, stroke 0.02
