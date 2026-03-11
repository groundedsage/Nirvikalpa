(ns starter.samples.basic-graphics.rotating-cube
  "Rotating cube using refactored API - demonstrating DOP principles.

   This sample demonstrates:
   - Data-oriented scene description
   - Separation of concerns (Data → Calculations → Actions)
   - Geometric Algebra for rotations
   - Shader DSL integration"
  (:require [nirvikalpa.api.renderer :as renderer]
            [nirvikalpa.math.ga :as ga]
            [nirvikalpa.shader.ast :as ast]
            [starter.samples.basic-graphics.cube.kit :as kit])
  (:require-macros [nirvikalpa.shader.dsl :refer [defvertex deffragment]]))

;;
;; Shader Definitions (same as original)
;;

(def uniforms-struct
  (ast/struct-def "Uniforms"
                  [(ast/struct-field "modelViewProjectionMatrix" :mat4x4f)]))

(def vertex-output-struct
  (ast/struct-def "VertexOutput"
                  [(ast/struct-field "Position" :vec4f {:builtin :position})
                   (ast/struct-field "fragUV" :vec2f {:location 0})
                   (ast/struct-field "fragPosition" :vec4f {:location 1})]))

(defvertex basic-vert [position :vec4f uv :vec2f]
  :structs [uniforms-struct vertex-output-struct]
  :uniform [uniforms Uniforms :group 0 :binding 0]
  :output VertexOutput
  (var output VertexOutput)
  (set! output.Position (* uniforms.modelViewProjectionMatrix position))
  (set! output.fragUV uv)
  (set! output.fragPosition (* 0.5 (+ position (vec4f 1.0 1.0 1.0 1.0))))
  output)

(deffragment vertex-position-color-frag [fragUV :vec2f fragPosition :vec4f]
  :output [:out-color :vec4f :location 0]
  fragPosition)

;;
;; Transform Function (Pure - CALCULATION layer)
;;

(defn rotating-cube-transform
  "Pure function that computes rotation based on time.

   This is a CALCULATION - no side effects, deterministic.

   Args:
     time - Time in seconds

   Returns: GA rotor representing the rotation"
  [time]
  (let [rotation-axis (ga/vector-3d (.sin js/Math time)
                                    (.cos js/Math time)
                                    0)]
    (ga/rotor-from-axis-angle rotation-axis 1.0)))

;;
;; Main Render Function (ACTION layer - GPU I/O)
;;

(defn RenderRotatingCube
  "Render rotating cube using refactored API.

"
  [{:keys [node !render-id]}]
  (let [render-id @!render-id
        ;; Create renderer (handles all GPU setup)
        renderer (renderer/create-renderer! node
                                            basic-vert  ; Vertex shader WGSL
                                            vertex-position-color-frag  ; Fragment shader WGSL
                                            kit/cube-vertex-array)]  ; Geometry data
    ;; Start animation loop
    (renderer/start-animation-loop! renderer
                                    rotating-cube-transform  ; Transform function
                                    !render-id
                                    render-id)))

