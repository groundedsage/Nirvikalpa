(ns starter.samples.rendering-2d.shapes.cross
  "Cross/Plus shape"
  (:require [nirvikalpa.api.renderer-2d :as r2d])
  (:require-macros [nirvikalpa.shader.dsl :refer [deffragment defshader-fn]]))

;; Helper function: Box SDF
(defshader-fn sdBox [p :vec2f b :vec2f] :f32
  (let [d (- (abs p) b)]
    (+ (distance (max d (vec2f 0.0 0.0)) (vec2f 0.0 0.0))
       (min (max d.x d.y) 0.0))))

;; Uniforms: [center.x, center.y, size, thickness]
(deffragment cross-fragment [uv :vec2f]
  :uniforms [[color :vec4f :group 0 :binding 0]
             [params :vec4f :group 0 :binding 1]]
  :output [:out_color :vec4f :location 0]
  :preamble [sdBox]
  (let [center (vec2f params.x params.y)
        size params.z
        thickness params.w
        p (- uv center)
        ;;  Use helper function
        horiz-box (sdBox p (vec2f size (* thickness 0.5)))
        vert-box (sdBox p (vec2f (* thickness 0.5) size))
        ;; Union (min of both distances)
        dist (min horiz-box vert-box)
        ;; Skia-style fwidth-based AA
        edge-width (fwidth dist)
        alpha (smoothstep (* 0.7 edge-width) (* (- 0.7) edge-width) dist)]
    (vec4f color.rgb (* color.a alpha))))

(defn Render2DCross [{:keys [node !render-id]}]
  (r2d/render-static!
   node
   cross-fragment
   [[0.3 0.9 0.5 1.0]         ; green cross
    [0.5 0.5 0.25 0.08]]))    ; center (0.5, 0.5), size 0.25, thickness 0.08
