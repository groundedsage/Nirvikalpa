(ns starter.samples.rendering-2d.shapes.x-cross
  "X/Diagonal Cross shape"
  (:require [nirvikalpa.api.renderer-2d :as r2d])
  (:require-macros [nirvikalpa.shader.dsl :refer [deffragment defshader-fn]]))

;; Helper: distance from point to line segment
(defshader-fn sdSegment [p :vec2f a :vec2f b :vec2f] :f32
  (let [pa (- p a)
        ba (- b a)
        h (clamp (/ (dot pa ba) (dot ba ba)) 0.0 1.0)]
    (distance pa (* ba h))))

;; Uniforms: [center.x, center.y, size, thickness]
(deffragment x-cross-fragment [uv :vec2f]
  :uniforms [[color :vec4f :group 0 :binding 0]
             [params :vec4f :group 0 :binding 1]]
  :output [:out_color :vec4f :location 0]
  :preamble [sdSegment]
  (let [center (vec2f params.x params.y)
        size params.z
        thickness params.w
        p (- uv center)
        ;; Diagonal 1: top-left to bottom-right
        diag1-a (vec2f (- size) (- size))
        diag1-b (vec2f size size)
        dist1 (- (sdSegment p diag1-a diag1-b) (* thickness 0.5))
        ;; Diagonal 2: top-right to bottom-left
        diag2-a (vec2f (- size) size)
        diag2-b (vec2f size (- size))
        dist2 (- (sdSegment p diag2-a diag2-b) (* thickness 0.5))
        ;; Union (min of both distances)
        dist (min dist1 dist2)
        ;; Skia-style fwidth-based AA
        edge-width (fwidth dist)
        alpha (smoothstep (* 0.7 edge-width) (* (- 0.7) edge-width) dist)]
    (vec4f color.rgb (* color.a alpha))))

(defn Render2DXCross [{:keys [node !render-id]}]
  (r2d/render-static!
   node
   x-cross-fragment
   [[0.9 0.3 0.3 1.0]         ; red X cross
    [0.5 0.5 0.25 0.08]]))    ; center (0.5, 0.5), size 0.25, thickness 0.08
