(ns starter.samples.basic-graphics.two-cubes
  "Two rotating cubes using refactored multi-object API.

   This sample demonstrates:
   - Multi-object rendering with shared geometry
   - Independent transforms per object
   - Clean data-oriented API"
  (:require [nirvikalpa.api.renderer :as renderer]
            [nirvikalpa.math.ga :as ga]
            [nirvikalpa.shader.ast :as ast]
            [starter.samples.basic-graphics.cube.kit :as kit])
  (:require-macros [nirvikalpa.shader.dsl :refer [defvertex deffragment]]))

;;
;; Shader Definitions (same as rotating-cube)
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
;; Transform Functions (Pure - CALCULATION layer)
;;

(defn cube-1-transform
  "Transform for first cube (left side, sin/cos rotation axis).

   Pure function - no side effects."
  [time]
  (let [rotation-axis (ga/vector-3d (.sin js/Math time)
                                    (.cos js/Math time)
                                    0)]
    (ga/rotor-from-axis-angle rotation-axis 1.0)))

(defn cube-2-transform
  "Transform for second cube (right side, cos/sin rotation axis).

   Pure function - no side effects."
  [time]
  (let [rotation-axis (ga/vector-3d (.cos js/Math time)
                                    (.sin js/Math time)
                                    0)]
    (ga/rotor-from-axis-angle rotation-axis 1.0)))

;;
;; Main Render Function (ACTION layer)
;;

(defn TwoCubes
  "Render two rotating cubes using refactored multi-object API.

"
  [{:keys [node !render-id]}]
  (let [render-id @!render-id
        ;; Create multi-object renderer (handles all GPU setup)
        renderer (renderer/create-multi-object-renderer!
                  node
                  basic-vert
                  vertex-position-color-frag
                  kit/cube-vertex-array
                  2)  ; 2 objects

        ;; Scene description (pure data)
        objects [{:position [-2 0 0]
                  :rotor-fn cube-1-transform}
                 {:position [2 0 0]
                  :rotor-fn cube-2-transform}]]

    ;; Start animation loop
    (renderer/start-multi-object-loop! renderer
                                       objects
                                       !render-id
                                       render-id)))

