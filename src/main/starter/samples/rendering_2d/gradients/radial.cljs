(ns starter.samples.rendering-2d.gradients.radial
  "Radial Gradient Rendering - REFACTORED

   Formula: Interpolate colors based on distance from center"
  (:require [nirvikalpa.api.renderer-2d :as r2d])
  (:require-macros [nirvikalpa.shader.dsl :refer [deffragment]]))

;;
;; Shader
;;

;; Radial gradient: params = [center.x, center.y, radius, unused]
(deffragment radial-gradient-fragment [uv :vec2f]
  :uniforms [[color1 :vec4f :group 0 :binding 0]
             [color2 :vec4f :group 0 :binding 1]
             [params :vec4f :group 0 :binding 2]]
  :output [:out_color :vec4f :location 0]
  (let [center (vec2f params.x params.y)
        radius params.z
        ;; Radial gradient: distance from center
        dist (distance uv center)
        t (/ dist radius)
        t-clamped (clamp t 0.0 1.0)
        ;; Mix colors based on normalized distance
        gradient-color (mix color1 color2 t-clamped)]
    gradient-color))

;;
;; Render Function
;;

(defn Render2DRadialGradient [{:keys [node !render-id]}]
  (r2d/render-static!
   node
   radial-gradient-fragment
   [[1.0 0.9 0.2 1.0]        ; yellow (center)
    [0.9 0.2 0.8 1.0]        ; magenta (outer)
    [0.5 0.5 0.5 0.0]]))     ; center (0.5, 0.5), radius 0.5
