(ns starter.samples.rendering-2d.gradients.sweep
  "Sweep/Angular/Conic Gradient - REFACTORED

   Formula: Mix colors based on atan2 angle"
  (:require [nirvikalpa.api.renderer-2d :as r2d])
  (:require-macros [nirvikalpa.shader.dsl :refer [deffragment]]))

;;
;; Shader
;;

;; Sweep gradient: params = [center.x, center.y, unused, unused]
(deffragment sweep-gradient-fragment [uv :vec2f]
  :uniforms [[color1 :vec4f :group 0 :binding 0]
             [color2 :vec4f :group 0 :binding 1]
             [params :vec4f :group 0 :binding 2]]
  :output [:out_color :vec4f :location 0]
  (let [center (vec2f params.x params.y)
        p (- uv center)
        ;; Angular gradient: interpolate based on angle
        pi 3.141593
        angle (atan2 p.y p.x)
        ;; Normalize to [0, 1]
        t (* (/ (+ angle pi) (* 2.0 pi)) 1.0)
        gradient-color (mix color1 color2 t)]
    gradient-color))

;;
;; Render Function
;;

(defn Render2DSweepGradient [{:keys [node !render-id]}]
  (r2d/render-static!
   node
   sweep-gradient-fragment
   [[1.0 0.0 0.0 1.0]        ; red
    [0.0 1.0 1.0 1.0]        ; cyan (opposite on color wheel)
    [0.5 0.5 0.0 0.0]]))     ; center (0.5, 0.5)
