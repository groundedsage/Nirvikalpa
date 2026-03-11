(ns starter.samples.rendering-2d.shapes.rounded-rect
  "2D Rounded Rectangle Rendering using Signed Distance Fields

   SDF Formula: Rectangle SDF with corner circle cutouts"
  (:require [nirvikalpa.api.renderer-2d :as r2d])
  (:require-macros [nirvikalpa.shader.dsl :refer [deffragment]]))

;; Uniforms: [center.x, center.y, size.x, size.y]
;;           [radius, unused, unused, unused]
(deffragment rounded-rect-fragment [uv :vec2f]
  :uniforms [[color :vec4f :group 0 :binding 0]
             [params1 :vec4f :group 0 :binding 1]
             [params2 :vec4f :group 0 :binding 2]]
  :output [:out_color :vec4f :location 0]
  (let [center (vec2f params1.x params1.y)
        size (vec2f params1.z params1.w)
        radius params2.x
        ;; Rounded rectangle SDF
        q (- (abs (- uv center)) (- size radius))
        dist (- (+ (distance (max q (vec2f 0.0 0.0)) (vec2f 0.0 0.0))
                   (min (max q.x q.y) 0.0))
                radius)
        ;; Skia-style fwidth-based AA
        edge-width (fwidth dist)
        alpha (smoothstep (* 0.7 edge-width) (* (- 0.7) edge-width) dist)]
    (vec4f color.rgb (* color.a alpha))))

(defn Render2DRoundedRect [{:keys [node !render-id]}]
  (r2d/render-static!
   node
   rounded-rect-fragment
   [[0.3 0.8 0.7 1.0]         ; teal color
    [0.5 0.5 0.3 0.2]         ; center (0.5, 0.5), size (0.3, 0.2)
    [0.05 0.0 0.0 0.0]]))     ; radius 0.05
