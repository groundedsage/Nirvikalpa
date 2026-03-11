(ns starter.samples.rendering-2d.shapes.quad
  "Quadrilateral - 4-sided polygon with arbitrary vertices"
  (:require [nirvikalpa.api.renderer-2d :as r2d])
  (:require-macros [nirvikalpa.shader.dsl :refer [deffragment defshader-fn]]))

;; Helper: distance from point to line segment
(defshader-fn sdSegment [p :vec2f a :vec2f b :vec2f] :f32
  (let [pa (- p a)
        ba (- b a)
        h (clamp (/ (dot pa ba) (dot ba ba)) 0.0 1.0)]
    (distance pa (* ba h))))

;; Uniforms: [v1.x, v1.y, v2.x, v2.y]
;;           [v3.x, v3.y, v4.x, v4.y]
(deffragment quad-fragment [uv :vec2f]
  :uniforms [[color :vec4f :group 0 :binding 0]
             [v1v2 :vec4f :group 0 :binding 1]
             [v3v4 :vec4f :group 0 :binding 2]]
  :output [:out_color :vec4f :location 0]
  :preamble [sdSegment]
  (let [v1 (vec2f v1v2.x v1v2.y)
        v2 (vec2f v1v2.z v1v2.w)
        v3 (vec2f v3v4.x v3v4.y)
        v4 (vec2f v3v4.z v3v4.w)
        p uv
        ;; Check if inside using cross products
        e1 (- v2 v1)
        e2 (- v3 v2)
        e3 (- v4 v3)
        e4 (- v1 v4)
        c1 (- (* e1.x (- p.y v1.y)) (* e1.y (- p.x v1.x)))
        c2 (- (* e2.x (- p.y v2.y)) (* e2.y (- p.x v2.x)))
        c3 (- (* e3.x (- p.y v3.y)) (* e3.y (- p.x v3.x)))
        c4 (- (* e4.x (- p.y v4.y)) (* e4.y (- p.x v4.x)))
        inside (and (>= c1 0.0) (>= c2 0.0) (>= c3 0.0) (>= c4 0.0))
        ;; Distance to edges using helper
        d1 (sdSegment p v1 v2)
        d2 (sdSegment p v2 v3)
        d3 (sdSegment p v3 v4)
        d4 (sdSegment p v4 v1)
        edge-dist (min (min d1 d2) (min d3 d4))
        ;; Signed distance (negative inside, positive outside)
        dist (if inside (- edge-dist) edge-dist)
        ;; Skia-style fwidth-based AA
        edge-width (fwidth dist)
        alpha (smoothstep (* 0.7 edge-width) (* (- 0.7) edge-width) dist)]
    (vec4f color.rgb (* color.a alpha))))

(defn Render2DQuad [{:keys [node !render-id]}]
  (r2d/render-static!
   node
   quad-fragment
   [[0.4 0.7 0.9 1.0]         ; blue quad
    [0.3 0.3 0.7 0.35]        ; vertices: v1(0.3, 0.3), v2(0.7, 0.35)
    [0.65 0.7 0.35 0.65]]))   ; vertices: v3(0.65, 0.7), v4(0.35, 0.65)
