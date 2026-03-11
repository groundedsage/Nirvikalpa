(ns starter.samples.rendering-2d.curves.quadratic-bezier
  "Quadratic Bezier Curve using Shader DSL

   Full Inigo Quilez exact SDF implementation using shader DSL with
   block-level conditionals and nested let bindings."
  (:require [nirvikalpa.api.renderer-2d :as r2d])
  (:require-macros [nirvikalpa.shader.dsl :refer [deffragment if-block let-block var-block assign]]))

(deffragment bezier-fragment [uv :vec2f]
  :uniforms [[color :vec4f :group 0 :binding 0]
             [p0 :vec4f :group 0 :binding 1]
             [p1 :vec4f :group 0 :binding 2]
             [p2 :vec4f :group 0 :binding 3]]
  :output [:out_color :vec4f :location 0]
  (let [A (vec2f "p0.x" "p0.y")
        B (vec2f "p1.x" "p1.y")
        C (vec2f "p2.x" "p2.y")
        thickness "p2.z"

        a (- B A)
        b (+ (- A (* 2.0 B)) C)
        c (* a 2.0)
        d (- A uv)

        kk (/ 1.0 (dot b b))
        kx (* kk (dot a b))
        ky (/ (* kk (+ (* 2.0 (dot a a)) (dot d b))) 3.0)
        kz (* kk (dot d a))

        p (- ky (* kx kx))
        p3 (* p p p)
        q (+ (* kx (- (* 2.0 kx kx) (* 3.0 ky))) kz)
        h (+ (* q q) (* 4.0 p3))]

    (do
      (var-block res :f32)
      (if-block (>= h 0.0)
                (let-block [h_sqrt (sqrt h)
                            x (/ (- (vec2f h_sqrt (- h_sqrt)) q) 2.0)
                            uv_temp (* (sign x) (pow (abs x) (vec2f 0.33333333 0.33333333)))
                            t (clamp (+ (+ "uv_temp.x" "uv_temp.y") (- kx)) 0.0 1.0)
                            curve_point (+ d (* (+ c (* b t)) t))]
                           (assign res (dot curve_point curve_point)))
                (let-block [z (sqrt (- p))
                            v (/ (acos (/ q (* p z 2.0))) 3.0)
                            m (cos v)
                            n (* (sin v) 1.732050808)
                            t_vec (- (* (vec3f (+ m m) (- (+ n m)) (- n m)) z) kx)
                            t (clamp t_vec (vec3f 0.0 0.0 0.0) (vec3f 1.0 1.0 1.0))
                            cp0 (+ d (* (+ c (* b "t.x")) "t.x"))
                            cp1 (+ d (* (+ c (* b "t.y")) "t.y"))
                            d0 (dot cp0 cp0)
                            d1 (dot cp1 cp1)]
                           (assign res (min d0 d1))))
      (let [dist (- (sqrt res) thickness)
            edge_width (fwidth dist)
            alpha (smoothstep (* 0.7 edge_width) (* -0.7 edge_width) dist)]
        (vec4f "color.r" "color.g" "color.b" (* "color.a" alpha))))))

(defn Render2DQuadraticBezier [{:keys [node !render-id]}]
  (r2d/render-static!
   node
   bezier-fragment
   [[1.0 0.4 0.7 1.0]
    [0.2 0.2 0.0 0.0]
    [0.5 0.8 0.0 0.0]
    [0.8 0.2 0.015 0.0]]))
