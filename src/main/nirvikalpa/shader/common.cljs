(ns nirvikalpa.shader.common
  "Common reusable shaders

   This namespace contains shader components that are used across
   multiple samples to avoid duplication."
  (:require [nirvikalpa.shader.ast :as ast])
  (:require-macros [nirvikalpa.shader.dsl :refer [defvertex]]))

;;
;; Shared Vertex Output Struct (used by fullscreen quad)
;;

(def vertex-output-struct
  "Standard vertex output for 2D fullscreen rendering.

   Fields:
     position - Clip space position (builtin)
     uv       - UV coordinates [0-1] for fragment shader"
  (ast/struct-def "VertexOutput"
                  [(ast/struct-field "position" :vec4f {:builtin :position})
                   (ast/struct-field "uv" :vec2f {:location 0})]))

;;
;; Fullscreen Triangle Vertex Shader (2D)
;;

(defvertex fullscreen-triangle-vertex
  [vertex-index :u32]
  :builtin :vertex-index
  :structs [vertex-output-struct]
  :output VertexOutput
  (let [;; Big triangle positions in clip space
        positions (array :vec2f
                         (vec2f -1.0 -1.0)  ; Bottom-left
                         (vec2f 3.0 -1.0)   ; Bottom-right (extended beyond viewport)
                         (vec2f -1.0 3.0))  ; Top-left (extended beyond viewport)
        ;; UV coordinates [0,1] × [0,1]
        uvs (array :vec2f
                   (vec2f 0.0 0.0)  ; Bottom-left
                   (vec2f 2.0 0.0)  ; Bottom-right (extended)
                   (vec2f 0.0 2.0)) ; Top-left (extended)
        pos (get positions vertex-index)
        uv-coord (get uvs vertex-index)]
    (var output VertexOutput)
    (set! output.position (vec4f pos.x pos.y 0.0 1.0))
    (set! output.uv uv-coord)
    output))

;;
;; Documentation
;;

(comment
  "Usage in 2D samples:

   ;; Old way (duplicated in every sample):
   (defvertex my-vertex [vertex-index :u32]
     :builtin :vertex-index
     :structs [vertex-output-struct]
     :output VertexOutput
     (let [positions (array :vec2f ...)
           uvs (array :vec2f ...)
           ...]
       ...))

   ;; New way (reuse):
   (require '[nirvikalpa.shader.common :as common])

   ;; Just use common/fullscreen-triangle-vertex directly
   ;; in your render pipeline!")
