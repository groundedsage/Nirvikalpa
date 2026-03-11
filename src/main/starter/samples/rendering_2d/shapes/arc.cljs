(ns starter.samples.rendering-2d.shapes.arc
  "Arc/Pie Slice Rendering - CONVERTED TO DSL

   SDF: Circular segment with angular bounds"
  (:require [nirvikalpa.api.renderer-2d :as r2d])
  (:require-macros [nirvikalpa.shader.dsl :refer [deffragment]]))

;;
;; Shader (now using DSL instead of raw WGSL!)
;;

;; Arc: params = [center.x, center.y, radius, unused]
;;      angles = [start_angle, end_angle, thickness, unused]
(deffragment arc-fragment [uv :vec2f]
  :uniforms [[color :vec4f :group 0 :binding 0]
             [params :vec4f :group 0 :binding 1]
             [angles :vec4f :group 0 :binding 2]]
  :output [:out_color :vec4f :location 0]
  (let [center (vec2f params.x params.y)
        radius params.z
        start-angle angles.x
        end-angle angles.y
        thickness angles.z
        ;; Convert to polar coordinates
        p (- uv center)
        angle (atan2 p.y p.x)
        dist-from-center (distance p (vec2f 0.0 0.0))
        ;; Normalize angle to [0, 2π)
        pi 3.14159265359
        normalized-angle (if (< angle 0.0)
                           (+ angle (* 2.0 pi))
                           angle)
        ;; Check if angle is within arc range
        angle-in-range (if (<= start-angle end-angle)
                         (and (>= normalized-angle start-angle)
                              (<= normalized-angle end-angle))
                         (or (>= normalized-angle start-angle)
                             (<= normalized-angle end-angle)))
        ;; Distance from arc ring
        dist-from-ring (- (abs (- dist-from-center radius)) thickness)
        dist (if angle-in-range dist-from-ring 10.0)
        ;; Skia-style fwidth-based AA
        edge-width (fwidth dist)
        alpha (smoothstep (* 0.7 edge-width) (* (- 0.7) edge-width) dist)]
    (vec4f color.rgb (* color.a alpha))))

;;
;; Render Function
;;

(defn Render2DArc [{:keys [node !render-id]}]
  (r2d/render-static!
   node
   arc-fragment
   [[1.0 0.5 0.2 1.0]                    ; orange arc
    [0.5 0.5 0.3 0.0]                    ; center (0.5, 0.5), radius 0.3
    [0.0 js/Math.PI 0.05 0.0]]))         ; start 0°, end 180°, thickness 0.05
