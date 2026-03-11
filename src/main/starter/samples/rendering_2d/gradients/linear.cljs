(ns starter.samples.rendering-2d.gradients.linear
  "Linear Gradient Rendering - REFACTORED

   Formula: Interpolate colors based on position along gradient vector"
  (:require [nirvikalpa.api.renderer-2d :as r2d])
  (:require-macros [nirvikalpa.shader.dsl :refer [deffragment]]))

;;
;; Shader
;;

;; Linear gradient: params = [start.x, start.y, end.x, end.y]
(deffragment linear-gradient-fragment [uv :vec2f]
  :uniforms [[color1 :vec4f :group 0 :binding 0]
             [color2 :vec4f :group 0 :binding 1]
             [params :vec4f :group 0 :binding 2]]
  :output [:out_color :vec4f :location 0]
  (let [start (vec2f params.x params.y)
        end (vec2f params.z params.w)
        ;; Linear gradient: project point onto gradient vector
        gradient-vec (- end start)
        uv-vec (- uv start)
        t (/ (dot uv-vec gradient-vec)
             (dot gradient-vec gradient-vec))
        t-clamped (clamp t 0.0 1.0)
        ;; Mix colors based on interpolation parameter
        gradient-color (mix color1 color2 t-clamped)]
    gradient-color))

;;
;; Render Function
;;

(defn Render2DLinearGradient [{:keys [node !render-id]}]
  (r2d/render-static!
   node
   linear-gradient-fragment
   [[1.0 0.2 0.3 1.0]        ; red
    [0.2 0.4 1.0 1.0]        ; blue
    [0.0 0.5 1.0 0.5]]))     ; left to right, horizontal
