(ns starter.samples.rendering-2d.shapes.rect-stroke
  "Rectangle Stroke (Outline Only)

   SDF: abs(dist_to_rect) - stroke_width"
  (:require [nirvikalpa.api.renderer-2d :as r2d])
  (:require-macros [nirvikalpa.shader.dsl :refer [deffragment]]))

;; Uniforms: [center.x, center.y, size.x, size.y]
;;           [stroke-width, unused, unused, unused]
(deffragment rect-stroke-fragment [uv :vec2f]
  :uniforms [[color :vec4f :group 0 :binding 0]
             [params1 :vec4f :group 0 :binding 1]
             [params2 :vec4f :group 0 :binding 2]]
  :output [:out_color :vec4f :location 0]
  (let [center (vec2f params1.x params1.y)
        size (vec2f params1.z params1.w)
        stroke-width params2.x
        ;; Rectangle SDF
        d (- (abs (- uv center)) size)
        rect-dist (+ (distance (max d (vec2f 0.0 0.0)) (vec2f 0.0 0.0))
                     (min (max d.x d.y) 0.0))
        ;; Stroke: abs(dist) - width
        stroke-dist (- (abs rect-dist) stroke-width)
        ;; Skia-style fwidth-based AA for retina-perfect edges
        edge-width (fwidth stroke-dist)
        alpha (smoothstep (* 0.7 edge-width) (* (- 0.7) edge-width) stroke-dist)]
    (vec4f color.rgb (* color.a alpha))))

(defn Render2DRectStroke [{:keys [node !render-id]}]
  (r2d/render-static!
   node
   rect-stroke-fragment
   [[1.0 1.0 1.0 1.0]         ; white stroke
    [0.5 0.5 0.3 0.2]         ; rect: center (0.5, 0.5), size (0.3, 0.2)
    [0.02 0.0 0.0 0.0]]))     ; stroke width: 0.02
