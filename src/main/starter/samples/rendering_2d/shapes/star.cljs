(ns starter.samples.rendering-2d.shapes.star
  "5-Pointed Star Rendering using SDF

   Common in icons, ratings, decorative elements"
  (:require [nirvikalpa.api.renderer-2d :as r2d])
  (:require-macros [nirvikalpa.shader.dsl :refer [deffragment]]))

;; Uniforms: [center.x, center.y, outer-radius, inner-radius]
(deffragment star-fragment [uv :vec2f]
  :uniforms [[color :vec4f :group 0 :binding 0]
             [params :vec4f :group 0 :binding 1]]
  :output [:out_color :vec4f :location 0]
  (let [center (vec2f params.x params.y)
        r-outer params.z
        r-inner params.w
        ;; 5-pointed star SDF
        p (- uv center)
        pi 3.141593
        an (/ pi 5.0)
        en (/ (* 2.0 pi) 5.0)
        bn (- (modulo (atan2 p.y p.x) en) an)
        p-len (distance p (vec2f 0.0 0.0))
        ;; Alternate between inner and outer radius
        r (mix r-inner r-outer (+ 0.5 (* 0.5 (cos (* bn 5.0)))))
        dist (- p-len r)
        ;; Skia-style fwidth-based AA
        edge-width (fwidth dist)
        alpha (smoothstep (* 0.7 edge-width) (* (- 0.7) edge-width) dist)]
    (vec4f color.rgb (* color.a alpha))))

(defn Render2DStar [{:keys [node !render-id]}]
  (r2d/render-static!
   node
   star-fragment
   [[1.0 0.84 0.0 1.0]      ; gold star
    [0.5 0.5 0.3 0.12]]))   ; center (0.5, 0.5), outer radius 0.3, inner radius 0.12
