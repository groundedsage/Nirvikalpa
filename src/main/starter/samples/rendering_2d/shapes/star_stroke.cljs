(ns starter.samples.rendering-2d.shapes.star-stroke
  "Star Stroke - 5-pointed star outline"
  (:require [nirvikalpa.api.renderer-2d :as r2d])
  (:require-macros [nirvikalpa.shader.dsl :refer [deffragment]]))

;; Uniforms: [center.x, center.y, r-outer, r-inner]
;;           [stroke-width, unused, unused, unused]
(deffragment star-stroke-fragment [uv :vec2f]
  :uniforms [[color :vec4f :group 0 :binding 0]
             [params1 :vec4f :group 0 :binding 1]
             [params2 :vec4f :group 0 :binding 2]]
  :output [:out_color :vec4f :location 0]
  (let [center (vec2f params1.x params1.y)
        r-outer params1.z
        r-inner params1.w
        stroke-width params2.x
        p (- uv center)
        pi 3.141593
        an (/ pi 5.0)
        en (/ (* 2.0 pi) 5.0)
        bn (- (modulo (atan2 p.y p.x) en) an)
        p-len (distance p (vec2f 0.0 0.0))
        r (mix r-inner r-outer (+ 0.5 (* 0.5 (cos (* bn 5.0)))))
        dist (- p-len r)
        dist-to-stroke (- (abs dist) stroke-width)
        ;; Skia-style fwidth-based AA for retina-perfect edges
        edge-width (fwidth dist-to-stroke)
        alpha (smoothstep (* 0.7 edge-width) (* (- 0.7) edge-width) dist-to-stroke)]
    (vec4f color.rgb (* color.a alpha))))

(defn Render2DStarStroke [{:keys [node !render-id]}]
  (r2d/render-static!
   node
   star-stroke-fragment
   [[1.0 0.85 0.2 1.0]        ; gold stroke
    [0.5 0.5 0.3 0.15]        ; center (0.5, 0.5), outer 0.3, inner 0.15
    [0.02 0.0 0.0 0.0]]))     ; stroke width: 0.02
