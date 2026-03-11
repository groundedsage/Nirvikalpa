(ns starter.samples.basic-graphics.triangle-dsl
  "Hello Triangle using refactored simple renderer API"
  (:require [nirvikalpa.api.renderer :as renderer])
  (:require-macros [nirvikalpa.shader.dsl :refer [defvertex deffragment]]))

;;
;; Shader Definitions (same as original)
;;

(defvertex triangle-vertex [VertexIndex :u32]
  :builtin :vertex-index
  :output [:position :vec4f :builtin]
  (let [pos (array :vec2f
                   (vec2f 0.0 0.5)
                   (vec2f -0.5 -0.5)
                   (vec2f 0.5 -0.5))]
    (vec4 (get pos VertexIndex) 0.0 1.0)))

(deffragment triangle-fragment []
  :output [:out-color :vec4f :location 0]
  (vec4f 1.0 0.0 0.0 1.0))

;;
;; Render Function (ACTION layer)
;;

(defn RenderTriangleDSL
  "Render triangle using refactored simple renderer.

"
  [{:keys [node]}]
  (let [;; Create simple renderer (no uniforms, no depth)
        renderer (renderer/create-simple-renderer! node
                                                   triangle-vertex
                                                   triangle-fragment)]
    ;; Single render (no animation)
    (renderer/render-simple-frame! renderer 3)))  ; 3 vertices

