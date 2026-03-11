(ns starter.samples.basic-graphics.hello-triangle
  "Hello Triangle using refactored simple renderer API"
  (:require [nirvikalpa.api.renderer :as renderer]
            [nirvikalpa.shader.ast :as ast]
            [nirvikalpa.shader.codegen :as gen]))

;;
;; Shader Definitions (same as original - using AST)
;;

(def triangle-vertex-ast
  "Vertex shader that generates triangle positions from vertex index."
  (ast/vertex-shader
   {:inputs [(ast/input-attribute {:builtin :vertex-index} :u32 "VertexIndex")]
    :outputs [(ast/output-attribute {:builtin :position} :vec4f)]
    :body
    [(ast/var-expr "pos"
                   (ast/array-expr :vec2f 3
                                   [(ast/vec2f 0.0 0.5)
                                    (ast/vec2f -0.5 -0.5)
                                    (ast/vec2f 0.5 -0.5)]))
     (ast/return-expr
      [:vec4 (ast/index-expr "pos" "VertexIndex") 0.0 1.0])]}))

(def triangle-fragment-ast
  "Fragment shader that outputs solid red color."
  (ast/fragment-shader
   {:inputs []
    :outputs [(ast/output-attribute {:location 0 :name "out_color"} :vec4f)]
    :body
    [(ast/return-expr (ast/vec4f 1.0 0.0 0.0 1.0))]}))

;; Generate WGSL from AST (CALCULATION - pure)
(def triangle-vert (gen/ast->wgsl triangle-vertex-ast))
(def red-frag (gen/ast->wgsl triangle-fragment-ast))

;;
;; Render Function (ACTION layer)
;;

(defn RenderTriangle
  "Render triangle using refactored simple renderer.

"
  [{:keys [node]}]
  (let [;; Create simple renderer
        renderer (renderer/create-simple-renderer! node
                                                   triangle-vert
                                                   red-frag)]
    ;; Single render (no animation)
    (renderer/render-simple-frame! renderer 3)))  ; 3 vertices

