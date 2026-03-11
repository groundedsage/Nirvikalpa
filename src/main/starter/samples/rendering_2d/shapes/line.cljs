(ns starter.samples.rendering-2d.shapes.line
  "2D Line Segment Rendering - REFACTORED"
  (:require [nirvikalpa.api.renderer-2d :as r2d])
  (:require-macros [nirvikalpa.shader.dsl :refer [deffragment]]))

;;
;; Shader
;;

;; Line: params1 = [a.x, a.y, b.x, b.y], params2 = [thickness, unused, unused, unused]
(deffragment line-fragment [uv :vec2f]
  :uniforms [[color :vec4f :group 0 :binding 0]
             [params1 :vec4f :group 0 :binding 1]
             [params2 :vec4f :group 0 :binding 2]]
  :output [:out_color :vec4f :location 0]
  (let [a (vec2f params1.x params1.y)
        b (vec2f params1.z params1.w)
        thickness params2.x
        ;; Line segment SDF
        pa (- uv a)
        ba (- b a)
        h (clamp (/ (dot pa ba) (dot ba ba)) 0.0 1.0)
        dist (- (distance pa (* ba h)) thickness)
        ;; Skia-style fwidth-based AA
        edge-width (fwidth dist)
        alpha (smoothstep (* 0.7 edge-width) (* (- 0.7) edge-width) dist)]
    (vec4f color.rgb (* color.a alpha))))

;;
;; Render Function
;;

(defn Render2DLine [{:keys [node !render-id]}]
  (r2d/render-static!
   node
   line-fragment
   [[0.2 0.9 0.4 1.0]          ; green
    [0.2 0.3 0.8 0.7]          ; from (0.2, 0.3) to (0.8, 0.7) - diagonal
    [0.02 0.0 0.0 0.0]]))      ; thickness 0.02
