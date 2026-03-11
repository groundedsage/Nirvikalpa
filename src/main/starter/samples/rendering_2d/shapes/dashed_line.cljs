(ns starter.samples.rendering-2d.shapes.dashed-line
  "Dashed Line Pattern

   Pattern: Repeating dash/gap using distance along line"
  (:require [nirvikalpa.api.renderer-2d :as r2d])
  (:require-macros [nirvikalpa.shader.dsl :refer [deffragment]]))

;; Uniforms: [p0.x, p0.y, unused, unused]           - start point
;;           [p1.x, p1.y, thickness, unused]        - end point + thickness
;;           [dash_length, gap_length, unused, unused]
(deffragment dashed-line-fragment [uv :vec2f]
  :uniforms [[color :vec4f :group 0 :binding 0]
             [p0 :vec4f :group 0 :binding 1]
             [p1 :vec4f :group 0 :binding 2]
             [dash :vec4f :group 0 :binding 3]]
  :output [:out_color :vec4f :location 0]
  (let [a (vec2f p0.x p0.y)
        b (vec2f p1.x p1.y)
        thickness p1.z
        dash-length dash.x
        gap-length dash.y
        pattern-length (+ dash-length gap-length)
        ;; Line segment SDF
        pa (- uv a)
        ba (- b a)
        h (clamp (/ (dot pa ba) (dot ba ba)) 0.0 1.0)
        dist-to-line (- (distance pa (* ba h)) thickness)
        ;; Distance along line (for dash pattern)
        along (* h (distance ba (vec2f 0.0 0.0)))
        pattern-pos (modulo along pattern-length)
        ;; Check if in dash or gap
        in-dash (< pattern-pos dash-length)
        dist (if in-dash dist-to-line 10.0)
        ;; Skia-style fwidth-based AA
        edge-width (fwidth dist)
        alpha (smoothstep (* 0.7 edge-width) (* (- 0.7) edge-width) dist)]
    (vec4f color.rgb (* color.a alpha))))

(defn Render2DDashedLine [{:keys [node !render-id]}]
  (r2d/render-static!
   node
   dashed-line-fragment
   [[1.0 1.0 0.0 1.0]         ; yellow dashed line
    [0.1 0.5 0.0 0.0]         ; line start: (0.1, 0.5)
    [0.9 0.5 0.015 0.0]       ; line end: (0.9, 0.5), thickness 0.015
    [0.05 0.03 0.0 0.0]]))    ; dash pattern: 0.05 dash, 0.03 gap
