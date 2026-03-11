(ns starter.samples.rendering-2d.shapes.triangle
  "2D Triangle Rendering using Signed Distance Fields - REFACTORED

   SDF Formula: Complex - computes distance to closest edge, signs based on winding"
  (:require [nirvikalpa.api.renderer-2d :as r2d])
  (:require-macros [nirvikalpa.shader.dsl :refer [deffragment]]))

;;
;; Shader
;;

;; Triangle SDF: params1 = [a.x, a.y, b.x, b.y], params2 = [c.x, c.y, unused, unused]
(deffragment triangle-fragment [uv :vec2f]
  :uniforms [[color :vec4f :group 0 :binding 0]
             [params1 :vec4f :group 0 :binding 1]
             [params2 :vec4f :group 0 :binding 2]]
  :output [:out_color :vec4f :location 0]
  (let [a (vec2f params1.x params1.y)
        b (vec2f params1.z params1.w)
        c (vec2f params2.x params2.y)
        ;; Triangle SDF (exact formula from Inigo Quilez)
        e0 (- b a)
        e1 (- c b)
        e2 (- a c)
        v0 (- uv a)
        v1 (- uv b)
        v2 (- uv c)

        pq0 (- v0 (* e0 (clamp (/ (dot v0 e0) (dot e0 e0)) 0.0 1.0)))
        pq1 (- v1 (* e1 (clamp (/ (dot v1 e1) (dot e1 e1)) 0.0 1.0)))
        pq2 (- v2 (* e2 (clamp (/ (dot v2 e2) (dot e2 e2)) 0.0 1.0)))

        s (sign (- (* e0.x e2.y) (* e0.y e2.x)))
        d (min (min
                (vec2f (dot pq0 pq0) (* s (- (* v0.x e0.y) (* v0.y e0.x))))
                (vec2f (dot pq1 pq1) (* s (- (* v1.x e1.y) (* v1.y e1.x)))))
               (vec2f (dot pq2 pq2) (* s (- (* v2.x e2.y) (* v2.y e2.x)))))

        dist (* (- (sqrt d.x)) (sign d.y))
        ;; Skia-style fwidth-based AA
        edge-width (fwidth dist)
        alpha (smoothstep (* 0.7 edge-width) (* (- 0.7) edge-width) dist)]
    (vec4f color.rgb (* color.a alpha))))

;;
;; Render Function
;;

(defn Render2DTriangle [{:keys [node !render-id]}]
  (r2d/render-static!
   node
   triangle-fragment
   [[1.0 0.6 0.2 1.0]           ; orange
    [0.5 0.7 0.3 0.3]           ; a (top), b (bottom-left)
    [0.7 0.3 0.0 0.0]]))        ; c (bottom-right)
