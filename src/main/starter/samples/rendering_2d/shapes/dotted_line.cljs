(ns starter.samples.rendering-2d.shapes.dotted-line
  "Dotted Line"
  (:require [nirvikalpa.api.renderer-2d :as r2d])
  (:require-macros [nirvikalpa.shader.dsl :refer [deffragment]]))

;; Uniforms: [p1.x, p1.y, p2.x, p2.y]
(deffragment dotted-line-fragment [uv :vec2f]
  :uniforms [[color :vec4f :group 0 :binding 0]
             [params :vec4f :group 0 :binding 1]]
  :output [:out_color :vec4f :location 0]
  (let [p1 (vec2f params.x params.y)
        p2 (vec2f params.z params.w)
        line-vec (- p2 p1)
        line-len (distance line-vec (vec2f 0.0 0.0))
        line-dir (/ line-vec line-len)
        to-point (- uv p1)
        proj (dot to-point line-dir)
        clamped (clamp proj 0.0 line-len)
        closest (+ p1 (* line-dir clamped))
        ;; Dotted line: distance to nearest dot center
        dot-spacing 0.05
        dot-radius 0.01
        normalized-pos (/ clamped dot-spacing)
        fract-pos (fract normalized-pos)
        dist-to-dot-center (* (abs (- fract-pos 0.5)) dot-spacing)
        ;; Distance to nearest dot (combining radial and along-line distance)
        radial-dist (distance uv closest)
        dist (- (distance (vec2f radial-dist dist-to-dot-center) (vec2f 0.0 0.0)) dot-radius)
        ;; Skia-style fwidth-based AA
        edge-width (fwidth dist)
        alpha (smoothstep (* 0.7 edge-width) (* (- 0.7) edge-width) dist)]
    (vec4f color.rgb (* color.a alpha))))

(defn Render2DDottedLine [{:keys [node !render-id]}]
  (r2d/render-static!
   node
   dotted-line-fragment
   [[0.2 0.8 0.6 1.0]         ; teal dotted line
    [0.2 0.5 0.8 0.5]]))      ; line from (0.2, 0.5) to (0.8, 0.5)
