(ns starter.samples.rendering-2d.shapes.ring
  "Ring/Donut Shape Rendering

   SDF: abs(distance_to_center - radius) - thickness"
  (:require [nirvikalpa.api.renderer-2d :as r2d])
  (:require-macros [nirvikalpa.shader.dsl :refer [deffragment]]))

;; Uniforms: [center.x, center.y, radius, thickness]
(deffragment ring-fragment [uv :vec2f]
  :uniforms [[color :vec4f :group 0 :binding 0]
             [params :vec4f :group 0 :binding 1]]
  :output [:out_color :vec4f :location 0]
  (let [center (vec2f params.x params.y)
        radius params.z
        thickness params.w
        ;; Ring SDF: distance to ring centerline, then subtract thickness
        dist-from-center (distance uv center)
        dist-from-ring (- (abs (- dist-from-center radius)) thickness)
        ;; Skia-style fwidth-based AA for retina-perfect edges
        edge-width (fwidth dist-from-ring)
        alpha (smoothstep (* 0.7 edge-width) (* (- 0.7) edge-width) dist-from-ring)]
    (vec4f color.rgb (* color.a alpha))))

(defn Render2DRing [{:keys [node !render-id]}]
  (r2d/render-static!
   node
   ring-fragment
   [[0.5 0.2 0.8 1.0]         ; violet ring
    [0.5 0.5 0.25 0.08]]))    ; center (0.5, 0.5), radius 0.25, thickness 0.08
