(ns starter.samples.rendering-2d.shapes.triangle-stroke
  "Triangle Stroke (Outline Only)

   SDF: abs(triangle_sdf) - stroke_width"
  (:require [nirvikalpa.api.renderer-2d :as r2d])
  (:require-macros [nirvikalpa.shader.dsl :refer [deffragment]]))

;; Uniforms: [a.x, a.y, b.x, b.y]
;;           [c.x, c.y, stroke-width, unused]
(deffragment triangle-stroke-fragment [uv :vec2f]
  :uniforms [[color :vec4f :group 0 :binding 0]
             [params1 :vec4f :group 0 :binding 1]
             [params2 :vec4f :group 0 :binding 2]]
  :output [:out_color :vec4f :location 0]
  (let [a (vec2f params1.x params1.y)
        b (vec2f params1.z params1.w)
        c (vec2f params2.x params2.y)
        stroke-width params2.z
        e0 (- b a) e1 (- c b) e2 (- a c)
        v0 (- uv a) v1 (- uv b) v2 (- uv c)
        pq0 (- v0 (* e0 (clamp (/ (dot v0 e0) (dot e0 e0)) 0.0 1.0)))
        pq1 (- v1 (* e1 (clamp (/ (dot v1 e1) (dot e1 e1)) 0.0 1.0)))
        pq2 (- v2 (* e2 (clamp (/ (dot v2 e2) (dot e2 e2)) 0.0 1.0)))
        s (sign (- (* e0.x e2.y) (* e0.y e2.x)))
        d (min (min (vec2f (dot pq0 pq0) (* s (- (* v0.x e0.y) (* v0.y e0.x))))
                    (vec2f (dot pq1 pq1) (* s (- (* v1.x e1.y) (* v1.y e1.x)))))
               (vec2f (dot pq2 pq2) (* s (- (* v2.x e2.y) (* v2.y e2.x)))))
        dist (* (- (sqrt d.x)) (sign d.y))
        dist-to-stroke (- (abs dist) stroke-width)
        ;; Skia-style fwidth-based AA for retina-perfect edges
        edge-width (fwidth dist-to-stroke)
        alpha (smoothstep (* 0.7 edge-width) (* (- 0.7) edge-width) dist-to-stroke)]
    (vec4f color.rgb (* color.a alpha))))

(defn Render2DTriangleStroke [{:keys [node !render-id]}]
  (r2d/render-static!
   node
   triangle-stroke-fragment
   [[1.0 0.8 0.2 1.0]         ; yellow stroke
    [0.5 0.7 0.3 0.3]         ; points: a(0.5, 0.7), b(0.3, 0.3)
    [0.7 0.3 0.02 0.0]]))     ; c(0.7, 0.3), stroke 0.02
