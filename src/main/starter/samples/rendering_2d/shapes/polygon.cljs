(ns starter.samples.rendering-2d.shapes.polygon
  "2D Regular Polygon Rendering

   SDF Formula: Uses polar coordinates and angular modulo"
  (:require [nirvikalpa.api.renderer-2d :as r2d])
  (:require-macros [nirvikalpa.shader.dsl :refer [deffragment]]))

;; Uniforms: [center.x, center.y, radius, n-sides]
(deffragment polygon-fragment [uv :vec2f]
  :uniforms [[color :vec4f :group 0 :binding 0]
             [params :vec4f :group 0 :binding 1]]
  :output [:out_color :vec4f :location 0]
  (let [center (vec2f params.x params.y)
        radius params.z
        ;; Hexagon SDF - exact formula from Inigo Quilez
        ;; Reference: https://iquilezles.org/articles/distfunctions2d/
        k (vec3f (- 0.866025404) 0.5 0.577350269)
        p (abs (- uv center))
        ;; Symmetry reflection
        p-reflected (- p (* (* 2.0 (min (dot (vec2f k.x k.y) p) 0.0))
                            (vec2f k.x k.y)))
        ;; Clamping and offsetting
        p-final (- p-reflected
                   (vec2f (clamp p-reflected.x (* (- k.z) radius) (* k.z radius))
                          radius))
        ;; Signed distance
        dist (* (distance p-final (vec2f 0.0 0.0))
                (sign p-final.y))
        ;; Skia-style fwidth-based AA
        edge-width (fwidth dist)
        alpha (smoothstep (* 0.7 edge-width) (* (- 0.7) edge-width) dist)]
    (vec4f color.rgb (* color.a alpha))))

(defn Render2DPolygon [{:keys [node !render-id]}]
  (r2d/render-static!
   node
   polygon-fragment
   [[0.3 0.9 0.9 1.0]      ; cyan color
    [0.5 0.5 0.3 6.0]]))   ; hexagon: center (0.5, 0.5), radius 0.3, 6 sides
