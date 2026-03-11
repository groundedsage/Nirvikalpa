(ns starter.samples.rendering-2d.shapes.diamond
  "Diamond/Rhombus shape"
  (:require [nirvikalpa.api.renderer-2d :as r2d])
  (:require-macros [nirvikalpa.shader.dsl :refer [deffragment]]))

;; Uniforms: [center.x, center.y, size.x, size.y]
(deffragment diamond-fragment [uv :vec2f]
  :uniforms [[color :vec4f :group 0 :binding 0]
             [params :vec4f :group 0 :binding 1]]
  :output [:out_color :vec4f :location 0]
  (let [center (vec2f params.x params.y)
        size (vec2f params.z params.w)
        p (abs (- uv center))
        dist (- (+ (/ p.x size.x) (/ p.y size.y)) 1.0)
        ;; Skia-style fwidth-based AA
        edge-width (fwidth dist)
        alpha (smoothstep (* 0.7 edge-width) (* (- 0.7) edge-width) dist)]
    (vec4f color.rgb (* color.a alpha))))

(defn Render2DDiamond [{:keys [node !render-id]}]
  (r2d/render-static!
   node
   diamond-fragment
   [[0.9 0.3 0.6 1.0]         ; pink diamond
    [0.5 0.5 0.25 0.25]]))    ; center (0.5, 0.5), size (0.25, 0.25)
