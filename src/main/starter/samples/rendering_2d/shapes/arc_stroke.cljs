(ns starter.samples.rendering-2d.shapes.arc-stroke
  "Arc Stroke - pie slice outline"
  (:require [nirvikalpa.api.renderer-2d :as r2d])
  (:require-macros [nirvikalpa.shader.dsl :refer [deffragment]]))

;; Uniforms: [center.x, center.y, radius, unused]
;;           [start_angle, end_angle, stroke_width, unused]
(deffragment arc-stroke-fragment [uv :vec2f]
  :uniforms [[color :vec4f :group 0 :binding 0]
             [params :vec4f :group 0 :binding 1]
             [angles :vec4f :group 0 :binding 2]]
  :output [:out_color :vec4f :location 0]
  (let [center (vec2f params.x params.y)
        radius params.z
        start-angle angles.x
        end-angle angles.y
        stroke-width angles.z
        p (- uv center)
        angle (atan2 p.y p.x)
        dist-from-center (distance p (vec2f 0.0 0.0))
        pi 3.14159265359
        normalized-angle (if (< angle 0.0)
                           (+ angle (* 2.0 pi))
                           angle)
        in-arc (if (<= start-angle end-angle)
                 (and (>= normalized-angle start-angle)
                      (<= normalized-angle end-angle))
                 (or (>= normalized-angle start-angle)
                     (<= normalized-angle end-angle)))
        radial-dist (abs (- dist-from-center radius))
        dist-to-stroke (- radial-dist stroke-width)
        ;; Skia-style fwidth-based AA
        edge-width (fwidth dist-to-stroke)
        alpha (if in-arc
                (smoothstep (* 0.7 edge-width) (* (- 0.7) edge-width) dist-to-stroke)
                0.0)]
    (vec4f color.rgb (* color.a alpha))))

(defn Render2DArcStroke [{:keys [node !render-id]}]
  (r2d/render-static!
   node
   arc-stroke-fragment
   [[0.95 0.55 0.2 1.0]       ; orange stroke
    [0.5 0.5 0.3 0.0]         ; center (0.5, 0.5), radius 0.3
    [0.0 4.0 0.02 0.0]]))     ; angles: start 0.0, end 4.0, stroke 0.02
