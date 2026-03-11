(ns starter.samples.rendering-2d.shapes.capsule
  "Capsule/Stadium - rounded rectangle with semicircle caps"
  (:require [nirvikalpa.api.renderer-2d :as r2d])
  (:require-macros [nirvikalpa.shader.dsl :refer [deffragment]]))

;; Uniforms: [center.x, center.y, half-length, radius]
(deffragment capsule-fragment [uv :vec2f]
  :uniforms [[color :vec4f :group 0 :binding 0]
             [params :vec4f :group 0 :binding 1]]
  :output [:out_color :vec4f :location 0]
  (let [center (vec2f params.x params.y)
        half-length params.z
        radius params.w
        p (- uv center)
        clamped-x (clamp p.x (- half-length) half-length)
        dx (- p.x clamped-x)
        dist (- (sqrt (+ (* dx dx) (* p.y p.y))) radius)
        ;; Skia-style fwidth-based AA
        edge-width (fwidth dist)
        alpha (smoothstep (* 0.7 edge-width) (* (- 0.7) edge-width) dist)]
    (vec4f color.rgb (* color.a alpha))))

(defn Render2DCapsule [{:keys [node !render-id]}]
  (r2d/render-static!
   node
   capsule-fragment
   [[0.4 0.6 0.9 1.0]         ; blue capsule
    [0.5 0.5 0.2 0.1]]))      ; center (0.5, 0.5), half-length 0.2, radius 0.1
