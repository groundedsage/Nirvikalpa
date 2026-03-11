(ns nirvikalpa.shader.dsl-test
  "Test the defshader macro in ClojureScript"
  (:require [nirvikalpa.shader.ast :as ast]
            [nirvikalpa.shader.codegen :as gen])
  (:require-macros [nirvikalpa.shader.dsl :refer [defvertex deffragment]]))

;; Test simple fragment shader
(deffragment test-red-fragment []
  :output [:out-color :vec4f :location 0]
  (vec4f 1.0 0.0 0.0 1.0))

;; Test simple vertex shader
(defvertex test-triangle-vertex [vertex-index :u32]
  :builtin :vertex-index
  :output [:position :vec4f :builtin]
  (let [pos (array :vec2f
                   (vec2f 0.0 0.5)
                   (vec2f -0.5 -0.5)
                   (vec2f 0.5 -0.5))]
    (vec4 (get pos vertex-index) 0.0 1.0)))

;; Test struct-based vertex shader
(def vertex-output-struct
  (ast/struct-def "VertexOutput"
                  [(ast/struct-field "Position" :vec4f {:builtin :position})
                   (ast/struct-field "fragUV" :vec2f {:location 0})]))

(defvertex test-struct-vertex [position :vec4f uv :vec2f]
  :structs [vertex-output-struct]
  :output VertexOutput
  (var output VertexOutput)
  (set! output.Position position)
  (set! output.fragUV uv)
  output)

;; Log the generated code
(defn test-macros []
  (js/console.log "=== Fragment Shader AST ===")
  (js/console.log test-red-fragment-ast)
  (js/console.log "\n=== Fragment Shader WGSL ===")
  (js/console.log test-red-fragment)
  (js/console.log "\n=== Vertex Shader AST ===")
  (js/console.log test-triangle-vertex-ast)
  (js/console.log "\n=== Vertex Shader WGSL ===")
  (js/console.log test-triangle-vertex)
  (js/console.log "\n=== Struct Vertex Shader AST ===")
  (js/console.log test-struct-vertex-ast)
  (js/console.log "\n=== Struct Vertex Shader WGSL ===")
  (js/console.log test-struct-vertex))
