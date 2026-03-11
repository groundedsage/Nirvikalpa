(ns nirvikalpa.shader.dsl-macro-extended-demo
  "Demonstration of extended DSL macro features"
  (:require-macros [nirvikalpa.shader.dsl :refer [defvertex deffragment]])
  (:require [nirvikalpa.shader.ast :as ast]))

;;
;; Example 1: Vertex shader with var type annotation and statement sequencing
;;

(defvertex conditional-vertex [vertex-index :u32 position :vec3f]
  :builtin :vertex-index
  :structs [(ast/struct-def "VertexOutput"
                            [(ast/struct-field "Position" :vec4f {:builtin :position})
                             (ast/struct-field "color" :vec3f {:location 0})])]
  :output VertexOutput
  (do
    (var output VertexOutput)
    (set! output.Position (vec4f position.x position.y position.z 1.0))
    (if (> vertex-index 1)
      (do
        (set! output.color (vec3f 1.0 0.0 0.0)))
      (do
        (set! output.color (vec3f 0.0 0.0 1.0))))
    output))

;;
;; Example 2: Fragment shader with complex if/else logic
;;

(deffragment conditional-fragment [fragUV :vec2f]
  :output [:out-color :vec4f :location 0]
  (do
    (var color :vec3f)
    (if (> fragUV.x 0.5)
      (do
        (set! color (vec3f 1.0 fragUV.y 0.0))
        (var intensity :f32)
        (set! intensity 0.8))
      (do
        (set! color (vec3f 0.0 fragUV.y 1.0))
        (var intensity :f32)
        (set! intensity 0.5)))
    (vec4f color.r color.g color.b 1.0)))

;;
;; Example 3: Simple ternary (expression-level if) still works
;;

(deffragment ternary-fragment [fragUV :vec2f]
  :output [:out-color :vec4f :location 0]
  (let [red (if (> fragUV.x 0.5) 1.0 0.0)]
    (vec4f red 0.0 0.0 1.0)))

;;
;; Example 4: Nested conditionals with do blocks
;;

(deffragment nested-conditional-fragment [fragUV :vec2f]
  :output [:out-color :vec4f :location 0]
  (do
    (var final-color :vec3f)
    (if (> fragUV.x 0.5)
      (do
        (if (> fragUV.y 0.5)
          (do
            (set! final-color (vec3f 1.0 1.0 0.0)))
          (do
            (set! final-color (vec3f 1.0 0.0 0.0)))))
      (do
        (if (> fragUV.y 0.5)
          (do
            (set! final-color (vec3f 0.0 1.0 0.0)))
          (do
            (set! final-color (vec3f 0.0 0.0 1.0))))))
    (vec4f final-color.r final-color.g final-color.b 1.0)))

;;
;; Example 5: Multiple var declarations and assignments
;;

(deffragment multi-var-fragment [fragUV :vec2f time :f32]
  :uniform [time :f32 :group 0 :binding 0]
  :output [:out-color :vec4f :location 0]
  (do
    (var r :f32)
    (var g :f32)
    (var b :f32)
    (set! r (* fragUV.x (sin time)))
    (set! g (* fragUV.y (cos time)))
    (set! b (* 0.5 (+ (sin time) (cos time))))
    (vec4f r g b 1.0)))
