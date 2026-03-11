(ns starter.samples.rendering-2d.gallery
  "2D Primitives Gallery - All Shapes in One View

   Layout:
   ┌──────────┬──────────┬──────────┐
   │ Rectangle│  Circle  │ Ellipse  │
   ├──────────┼──────────┼──────────┤
   │   Line   │ Triangle │  Rounded │
   └──────────┴──────────┴──────────┘"
  (:require [nirvikalpa.gpu :as gpu]
            [nirvikalpa.shader.ast :as ast])
  (:require-macros [nirvikalpa.shader.dsl :refer [defvertex deffragment]]))

;;
;; Shared Vertex Shader
;;

(def vertex-output-struct
  (ast/struct-def "VertexOutput"
                  [(ast/struct-field "position" :vec4f {:builtin :position})
                   (ast/struct-field "uv" :vec2f {:location 0})]))

(defvertex gallery-vertex [vertex-index :u32]
  :builtin :vertex-index
  :structs [vertex-output-struct]
  :output VertexOutput
  (let [positions (array :vec2f
                         (vec2f -1.0 -1.0)
                         (vec2f 3.0 -1.0)
                         (vec2f -1.0 3.0))
        uvs (array :vec2f
                   (vec2f 0.0 0.0)
                   (vec2f 2.0 0.0)
                   (vec2f 0.0 2.0))
        pos (get positions vertex-index)
        uv-coord (get uvs vertex-index)]
    (var output VertexOutput)
    (set! output.position (vec4f pos.x pos.y 0.0 1.0))
    (set! output.uv uv-coord)
    output))

;;
;; Gallery Fragment Shader - Renders all primitives
;;

(deffragment gallery-fragment [uv :vec2f]
  :output [:out_color :vec4f :location 0]
  (let [;; Grid layout: 3 columns, 2 rows
        ;; Each cell is 1/3 x 1/2 of UV space
        col (clamp (* uv.x 3.0) 0.0 2.999)
        row (clamp (* uv.y 2.0) 0.0 1.999)
        cell-x (- col (* (floor col) 1.0))
        cell-y (- row (* (floor row) 1.0))
        ;; Scale to [0-1] within cell
        local-uv (vec2f cell-x cell-y)
        cell-index (+ (* (floor row) 3.0) (floor col))

        ;; Cell centers and sizes (in local UV [0-1])
        center (vec2f 0.5 0.5)

        ;; Colors for each primitive
        red (vec4f 1.0 0.0 0.0 1.0)
        blue (vec4f 0.2 0.5 1.0 1.0)
        purple (vec4f 0.8 0.3 0.9 1.0)
        green (vec4f 0.2 0.9 0.4 1.0)
        orange (vec4f 1.0 0.6 0.2 1.0)
        teal (vec4f 0.3 0.8 0.7 1.0)

        ;; SDFs for each primitive (inline for now)
        ;; Cell 0: Rectangle (top-left)
        rect-min (vec2f 0.25 0.25)
        rect-max (vec2f 0.75 0.75)
        rect-inside (and (and (>= local-uv.x rect-min.x) (<= local-uv.x rect-max.x))
                         (and (>= local-uv.y rect-min.y) (<= local-uv.y rect-max.y)))
        rect-dist (if rect-inside (- 0.001) 0.01)

        ;; Cell 1: Circle (top-center)
        circle-dist (- (distance local-uv center) 0.3)

        ;; Cell 2: Ellipse (top-right)
        ellipse-q (/ (- local-uv center) (vec2f 0.35 0.2))
        ellipse-dist (* (- (distance ellipse-q (vec2f 0.0 0.0)) 1.0)
                        (min 0.35 0.2))

        ;; Cell 3: Line (bottom-left)
        line-a (vec2f 0.2 0.3)
        line-b (vec2f 0.8 0.7)
        line-pa (- local-uv line-a)
        line-ba (- line-b line-a)
        line-h (clamp (/ (dot line-pa line-ba) (dot line-ba line-ba)) 0.0 1.0)
        line-dist (- (distance line-pa (* line-ba line-h)) 0.02)

        ;; Cell 4: Triangle (bottom-center)
        tri-a (vec2f 0.5 0.7)
        tri-b (vec2f 0.3 0.3)
        tri-c (vec2f 0.7 0.3)
        tri-e0 (- tri-b tri-a)
        tri-e1 (- tri-c tri-b)
        tri-e2 (- tri-a tri-c)
        tri-v0 (- local-uv tri-a)
        tri-v1 (- local-uv tri-b)
        tri-v2 (- local-uv tri-c)
        tri-pq0 (- tri-v0 (* tri-e0 (clamp (/ (dot tri-v0 tri-e0) (dot tri-e0 tri-e0)) 0.0 1.0)))
        tri-pq1 (- tri-v1 (* tri-e1 (clamp (/ (dot tri-v1 tri-e1) (dot tri-e1 tri-e1)) 0.0 1.0)))
        tri-pq2 (- tri-v2 (* tri-e2 (clamp (/ (dot tri-v2 tri-e2) (dot tri-e2 tri-e2)) 0.0 1.0)))
        tri-s (sign (- (* tri-e0.x tri-e2.y) (* tri-e0.y tri-e2.x)))
        tri-d (min (min
                    (vec2f (dot tri-pq0 tri-pq0) (* tri-s (- (* tri-v0.x tri-e0.y) (* tri-v0.y tri-e0.x))))
                    (vec2f (dot tri-pq1 tri-pq1) (* tri-s (- (* tri-v1.x tri-e1.y) (* tri-v1.y tri-e1.x)))))
                   (vec2f (dot tri-pq2 tri-pq2) (* tri-s (- (* tri-v2.x tri-e2.y) (* tri-v2.y tri-e2.x)))))
        tri-dist (* (- (sqrt tri-d.x)) (sign tri-d.y))

        ;; Cell 5: Rounded Rectangle (bottom-right)
        rr-size (vec2f 0.3 0.2)
        rr-radius 0.05
        rr-q (- (abs (- local-uv center)) (- rr-size rr-radius))
        rr-dist (- (+ (distance (max rr-q (vec2f 0.0 0.0)) (vec2f 0.0 0.0))
                      (min (max rr-q.x rr-q.y) 0.0))
                   rr-radius)

        ;; Select color and distance based on cell
        result (if (< cell-index 1.0)
                 ;; Cell 0: Rectangle
                 (let [alpha (smoothstep 0.005 (- 0.005) rect-dist)]
                   (vec4f red.rgb (* red.a alpha)))
                 (if (< cell-index 2.0)
                   ;; Cell 1: Circle
                   (let [alpha (smoothstep 0.005 (- 0.005) circle-dist)]
                     (vec4f blue.rgb (* blue.a alpha)))
                   (if (< cell-index 3.0)
                     ;; Cell 2: Ellipse
                     (let [alpha (smoothstep 0.005 (- 0.005) ellipse-dist)]
                       (vec4f purple.rgb (* purple.a alpha)))
                     (if (< cell-index 4.0)
                       ;; Cell 3: Line
                       (let [alpha (smoothstep 0.005 (- 0.005) line-dist)]
                         (vec4f green.rgb (* green.a alpha)))
                       (if (< cell-index 5.0)
                         ;; Cell 4: Triangle
                         (let [alpha (smoothstep 0.005 (- 0.005) tri-dist)]
                           (vec4f orange.rgb (* orange.a alpha)))
                         ;; Cell 5: Rounded Rectangle
                         (let [alpha (smoothstep 0.005 (- 0.005) rr-dist)]
                           (vec4f teal.rgb (* teal.a alpha))))))))]
    result))

;;
;; Render Function
;;

(defn Render2DGallery [{:keys [node !render-id]}]
  (let [context (.getContext node "webgpu")
        {:keys [device canvas-format queue]} @gpu/!gpu-config
        _ (gpu/configure-canvas context {:device device
                                         :format canvas-format
                                         :alphaMode "premultiplied"})

        vert-shader-module (gpu/create-shader-module device {:code gallery-vertex})
        frag-shader-module (gpu/create-shader-module device {:code gallery-fragment})

        pipeline (gpu/create-render-pipeline device
                                             {:label "2D Gallery pipeline"
                                              :layout "auto"
                                              :vertex {:module vert-shader-module}
                                              :fragment {:module frag-shader-module
                                                         :targets [{:format canvas-format
                                                                    :blend {:color {:srcFactor "src-alpha"
                                                                                    :dstFactor "one-minus-src-alpha"
                                                                                    :operation "add"}
                                                                            :alpha {:srcFactor "one"
                                                                                    :dstFactor "one-minus-src-alpha"
                                                                                    :operation "add"}}}]}
                                              :primitive {:topology "triangle-list"}})

        command-encoder (gpu/create-command-encoder device)
        render-pass-descriptor (clj->js {:colorAttachments
                                         [{:view (gpu/create-view context)
                                           :loadOp "clear"
                                           :clearValue {:r 0.05 :g 0.05 :b 0.05 :a 1.0}
                                           :storeOp "store"}]})
        pass-encoder (gpu/begin-render-pass command-encoder render-pass-descriptor)]

    (doto pass-encoder
      (gpu/set-pipeline pipeline)
      (gpu/draw 3)
      (.end))
    (.submit queue (clj->js [(.finish command-encoder)]))))
