(ns starter.samples.rendering-2d.shapes.circle
  "2D Circle Rendering using Signed Distance Fields

   SDF Formula: distance(p, center) - radius"
  (:require [nirvikalpa.api.renderer-2d :as r2d])
  (:require-macros [nirvikalpa.shader.dsl :refer [deffragment]]))

;;
;; Shader (only part that matters)
;;

(deffragment circle-fragment [uv :vec2f]
  :uniforms [[color :vec4f :group 0 :binding 0]
             [params :vec4f :group 0 :binding 1]]
  :output [:out_color :vec4f :location 0]
  (let [center (vec2f params.x params.y)
        radius params.z
        ;; SDF: distance from point to circle surface
        dist (- (distance uv center) radius)
        ;; Skia-style analytical anti-aliasing using fwidth
        ;; fwidth computes gradient of distance field → actual pixel size
        ;; This adapts automatically to display density (retina, 4K, etc.)
        ;; and zoom level, ensuring perfect crisp edges everywhere
        edge-width (fwidth dist)
        ;; smoothstep with derivative-based width: industry standard
        ;; Multiply by 0.7 to tighten for maximum crispness while keeping smooth AA
        alpha (smoothstep (* 0.7 edge-width) (* (- 0.7) edge-width) dist)]
    (vec4f color.rgb (* color.a alpha))))

;;
;; Render Function (just data!)
;;

(defn Render2DCircle
  "Render a blue circle using the 2D renderer API."
  [{:keys [node !render-id]}]
  (r2d/render-static!
   node
   circle-fragment
   [[0.2 0.5 1.0 1.0]         ; color: blue
    [0.5 0.5 0.3 0.0]]))
