(ns starter.samples.rendering-2d.shapes.annular-sector
  "Annular Sector (Donut Chart Slice)

   Arc + Ring combined"
  (:require [nirvikalpa.api.renderer-2d :as r2d])
  (:require-macros [nirvikalpa.shader.dsl :refer [deffragment]]))

;; Uniforms: [center.x, center.y, inner_radius, outer_radius]
;;           [start_angle, end_angle, unused, unused]
(deffragment annular-sector-fragment [uv :vec2f]
  :uniforms [[color :vec4f :group 0 :binding 0]
             [params :vec4f :group 0 :binding 1]
             [angles :vec4f :group 0 :binding 2]]
  :output [:out_color :vec4f :location 0]
  (let [center (vec2f params.x params.y)
        inner-radius params.z
        outer-radius params.w
        start-angle angles.x
        end-angle angles.y
        p (- uv center)
        angle (atan2 p.y p.x)
        dist-from-center (distance p (vec2f 0.0 0.0))
        ;; Normalize angle to [0, 2π)
        pi 3.14159265359
        normalized-angle (if (< angle 0.0)
                           (+ angle (* 2.0 pi))
                           angle)
        ;; Check if in angular range
        in-arc (if (<= start-angle end-angle)
                 (and (>= normalized-angle start-angle)
                      (<= normalized-angle end-angle))
                 (or (>= normalized-angle start-angle)
                     (<= normalized-angle end-angle)))
        ;; Check if in radial range
        in-ring (and (>= dist-from-center inner-radius)
                     (<= dist-from-center outer-radius))
        ;; Combine: inside both arc and ring
        dist-to-inner (- dist-from-center inner-radius)
        dist-to-outer (- outer-radius dist-from-center)
        dist (if (and in-arc in-ring)
               (- (min dist-to-inner dist-to-outer))
               10.0)
        ;; Skia-style fwidth-based AA
        edge-width (fwidth dist)
        alpha (smoothstep (* 0.7 edge-width) (* (- 0.7) edge-width) dist)]
    (vec4f color.rgb (* color.a alpha))))

(defn Render2DAnnularSector [{:keys [node !render-id]}]
  (let [pi js/Math.PI]
    (r2d/render-static!
     node
     annular-sector-fragment
     [[1.0 0.5 0.4 1.0]       ; coral color
      [0.5 0.5 0.15 0.35]     ; center (0.5, 0.5), inner 0.15, outer 0.35
      [(/ pi 4) (* 3 (/ pi 4)) 0.0 0.0]]))) ; angles: 45° to 135°
