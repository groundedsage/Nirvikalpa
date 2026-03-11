(ns starter.samples.rendering-2d.shapes.ellipse
  "2D Ellipse Rendering using Signed Distance Fields - REFACTORED

   SDF Formula: (length((p - center) / radii) - 1.0) * min(rx, ry)"
  (:require [nirvikalpa.api.renderer-2d :as r2d])
  (:require-macros [nirvikalpa.shader.dsl :refer [deffragment]]))

;;
;; Shader
;;

;; Ellipse: params = [center.x, center.y, radius-x, radius-y]
(deffragment ellipse-fragment [uv :vec2f]
  :uniforms [[color :vec4f :group 0 :binding 0]
             [params :vec4f :group 0 :binding 1]]
  :output [:out_color :vec4f :location 0]
  (let [center (vec2f params.x params.y)
        radii (vec2f params.z params.w)
        ;; Ellipse SDF: scale point by inverse radii, then treat as circle
        q (/ (- uv center) radii)
        dist (* (- (distance q (vec2f 0.0 0.0)) 1.0)
                (min radii.x radii.y))
        ;; Skia-style fwidth-based AA for device-independent crispness
        edge-width (fwidth dist)
        alpha (smoothstep (* 0.7 edge-width) (* (- 0.7) edge-width) dist)]
    (vec4f color.rgb (* color.a alpha))))

;;
;; Render Function
;;

(defn Render2DEllipse [{:keys [node !render-id]}]
  (r2d/render-static!
   node
   ellipse-fragment
   [[0.8 0.3 0.9 1.0]          ; purple
    [0.5 0.5 0.35 0.2]]))      ; center (0.5, 0.5), rx=0.35, ry=0.2 (wider than tall)
