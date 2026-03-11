(ns starter.samples.rendering-2d.shapes.ellipse-stroke
  "Ellipse Stroke (Outline Only) Rendering

   SDF: abs(ellipse_sdf) - stroke_width"
  (:require [nirvikalpa.api.renderer-2d :as r2d])
  (:require-macros [nirvikalpa.shader.dsl :refer [deffragment]]))

;; Uniforms: [center.x, center.y, radius-x, radius-y]
;;           [stroke-width, unused, unused, unused]
(deffragment ellipse-stroke-fragment [uv :vec2f]
  :uniforms [[color :vec4f :group 0 :binding 0]
             [params :vec4f :group 0 :binding 1]
             [stroke_params :vec4f :group 0 :binding 2]]
  :output [:out_color :vec4f :location 0]
  (let [center (vec2f params.x params.y)
        radii (vec2f params.z params.w)
        stroke-width stroke_params.x
        ;; Ellipse SDF
        q (/ (- uv center) radii)
        dist-to-surface (* (- (distance q (vec2f 0.0 0.0)) 1.0)
                           (min radii.x radii.y))
        ;; Stroke: abs(dist) - width
        dist-to-stroke (- (abs dist-to-surface) stroke-width)
        ;; Skia-style fwidth-based AA for retina-perfect edges
        edge-width (fwidth dist-to-stroke)
        alpha (smoothstep (* 0.7 edge-width) (* (- 0.7) edge-width) dist-to-stroke)]
    (vec4f color.rgb (* color.a alpha))))

(defn Render2DEllipseStroke [{:keys [node !render-id]}]
  (r2d/render-static!
   node
   ellipse-stroke-fragment
   [[0.2 0.9 0.9 1.0]         ; cyan stroke
    [0.5 0.5 0.35 0.2]        ; ellipse: center (0.5, 0.5), radius-x 0.35, radius-y 0.2
    [0.02 0.0 0.0 0.0]]))     ; stroke width: 0.02
