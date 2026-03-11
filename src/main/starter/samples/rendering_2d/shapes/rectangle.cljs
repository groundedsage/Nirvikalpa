(ns starter.samples.rendering-2d.shapes.rectangle
  "2D Rectangle Rendering using Signed Distance Fields - REFACTORED"
  (:require [nirvikalpa.api.renderer-2d :as r2d])
  (:require-macros [nirvikalpa.shader.dsl :refer [deffragment]]))

;;
;; Shader
;;

;; Rectangle SDF: x, y, width, height in UV space [0-1]
(deffragment rect-fragment [uv :vec2f]
  :uniforms [[color :vec4f :group 0 :binding 0]
             [rect :vec4f :group 0 :binding 1]]
  :output [:out_color :vec4f :location 0]
  (let [center (vec2f (+ rect.x (/ rect.z 2.0)) (+ rect.y (/ rect.w 2.0)))
        half_size (vec2f (/ rect.z 2.0) (/ rect.w 2.0))
        ;; Rectangle SDF: measure distance to nearest edge
        p (- uv center)
        q (- (abs p) half_size)
        ;; Distance is length of point outside rect, or max component if inside
        dist (+ (distance (max q (vec2f 0.0 0.0)) (vec2f 0.0 0.0))
                (min (max q.x q.y) 0.0))
        ;; Skia-style fwidth-based AA
        edge-width (fwidth dist)
        alpha (smoothstep (* 0.7 edge-width) (* (- 0.7) edge-width) dist)]
    (vec4f color.rgb (* color.a alpha))))

;;
;; Render Function
;;

(defn Render2DRectangle [{:keys [node !render-id]}]
  (r2d/render-static!
   node
   rect-fragment
   [[1.0 0.0 0.0 1.0]      ; red
    [0.25 0.25 0.5 0.5]]   ; centered, 50% size
   {:clear-color [0.0 0.0 0.0 1.0]}))
