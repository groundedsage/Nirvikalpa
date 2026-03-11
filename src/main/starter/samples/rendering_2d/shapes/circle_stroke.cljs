(ns starter.samples.rendering-2d.shapes.circle-stroke
  "Circle Stroke (Outline Only) Rendering

   SDF: abs(distance - radius) - stroke_width"
  (:require [nirvikalpa.api.renderer-2d :as r2d])
  (:require-macros [nirvikalpa.shader.dsl :refer [deffragment]]))

;; Uniforms: [center.x, center.y, radius, stroke-width]
(deffragment circle-stroke-fragment [uv :vec2f]
  :uniforms [[color :vec4f :group 0 :binding 0]
             [params :vec4f :group 0 :binding 1]]
  :output [:out_color :vec4f :location 0]
  (let [center (vec2f params.x params.y)
        radius params.z
        stroke-width params.w
        ;; Stroke SDF: abs(dist) - width (creates ring around circle)
        dist-to-surface (- (distance uv center) radius)
        dist-to-stroke (- (abs dist-to-surface) stroke-width)
        ;; Skia-style fwidth-based AA for retina-perfect edges
        edge-width (fwidth dist-to-stroke)
        alpha (smoothstep (* 0.7 edge-width) (* (- 0.7) edge-width) dist-to-stroke)]
    (vec4f color.rgb (* color.a alpha))))

(defn Render2DCircleStroke [{:keys [node !render-id]}]
  (r2d/render-static!
   node
   circle-stroke-fragment
   [[0.5 1.0 0.3 1.0]         ; lime green stroke
    [0.5 0.5 0.25 0.02]]))    ; circle: center (0.5, 0.5), radius 0.25, stroke 0.02
