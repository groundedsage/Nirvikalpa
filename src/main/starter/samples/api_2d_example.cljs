(ns starter.samples.api-2d-example
  "High-Level 2D API Example - Dream API Demonstration

   This demonstrates the new high-level API from nirvikalpa.api.2d

   BEFORE (low-level renderer API):
   --------------------------------
   (r2d/render-static! node
     circle-fragment-shader
     [[0.2 0.5 1.0 1.0]      ; color (obscure)
      [0.5 0.5 0.3 0.0]])    ; params (what do these mean?)

   AFTER (high-level data API):
   ----------------------------
   (render! canvas
     (circle {:cx 0.5 :cy 0.5 :radius 0.3 :color :blue}))

   Benefits:
   - Self-documenting (keys reveal intent)
   - Pure data (inspect, transform, serialize)
   - REPL-friendly (modify and re-render)
   - No shader knowledge required
   - Composable (just data structures)"
  (:require [nirvikalpa.api.render-2d-api :as api2d]))

;;
;; Example 1: Single Shape
;;

(defn Render2DAPIExample1
  "Render a single blue circle using the high-level API"
  [{:keys [node !render-id]}]
  (api2d/render! node
                 (api2d/circle {:cx 0.5 :cy 0.5 :radius 0.3 :color :blue})))

;;
;; Example 2: Multiple Shapes (Composition)
;;

(defn Render2DAPIExample2
  "Render multiple shapes composed together"
  [{:keys [node !render-id]}]
  (api2d/render! node
                 [(api2d/rect {:x 0 :y 0 :w 1 :h 1 :color :white})
                  (api2d/circle {:cx 0.5 :cy 0.5 :radius 0.35 :color :red})
                  (api2d/circle {:cx 0.5 :cy 0.5 :radius 0.25 :color :white})
                  (api2d/circle {:cx 0.5 :cy 0.5 :radius 0.15 :color :blue})]))

;;
;; Example 3: Data-Driven Scene
;;

(def my-scene
  "A scene defined as pure data"
  {:background (api2d/rect {:x 0 :y 0 :w 1 :h 1 :color [0.1 0.1 0.1 1.0]})
   :shapes [(api2d/circle {:cx 0.3 :cy 0.3 :radius 0.2 :color [1.0 0.3 0.3 1.0]})
            (api2d/circle {:cx 0.7 :cy 0.3 :radius 0.2 :color [0.3 1.0 0.3 1.0]})
            (api2d/circle {:cx 0.5 :cy 0.7 :radius 0.2 :color [0.3 0.3 1.0 1.0]})]})

(defn Render2DAPIExample3
  "Render a data-driven scene"
  [{:keys [node !render-id]}]
  (api2d/render! node
                 (cons (:background my-scene) (:shapes my-scene))))

;;
;; Example 4: All Basic Shapes
;;

(defn Render2DAPIExample4
  "Showcase all basic shape constructors"
  [{:keys [node !render-id]}]
  (api2d/render! node
                 [(api2d/rect {:x 0 :y 0 :w 1 :h 1 :color :white})
     ;; Circle
                  (api2d/circle {:cx 0.2 :cy 0.2 :radius 0.1 :color :red})
     ;; Rectangle
                  (api2d/rect {:x 0.4 :y 0.1 :w 0.2 :h 0.15 :color :green})
     ;; Ellipse
                  (api2d/ellipse {:cx 0.8 :cy 0.2 :rx 0.12 :ry 0.08 :color :blue})
     ;; Line
                  (api2d/line {:x1 0.1 :y1 0.5 :x2 0.4 :y2 0.5 :color :black :width 0.01})
     ;; Triangle
                  (api2d/triangle {:p1 [0.7 0.4] :p2 [0.6 0.6] :p3 [0.8 0.6] :color [1.0 0.5 0.0 1.0]})]))

;;
;; Example 5: New Shapes (rounded-rect, polygon, star, ring, point)
;;

(defn Render2DAPIExample5
  "Test the newly added shape types"
  [{:keys [node !render-id]}]
  (api2d/render! node
                 [(api2d/rect {:x 0 :y 0 :w 1 :h 1 :color :white})
                  ;; Rounded rect
                  (api2d/rounded-rect {:x 0.1 :y 0.1 :w 0.3 :h 0.2 :radius 0.03 :color :red})
                  ;; Hexagon
                  (api2d/polygon {:cx 0.6 :cy 0.2 :radius 0.15 :sides 6 :color :green})
                  ;; Star
                  (api2d/star {:cx 0.3 :cy 0.6 :outer-radius 0.2 :inner-radius 0.08 :color :gold})
                  ;; Ring
                  (api2d/ring {:cx 0.7 :cy 0.7 :radius 0.15 :thickness 0.05 :color :blue})
                  ;; Point
                  (api2d/point {:x 0.5 :y 0.5 :size 0.02 :color :black})]))

;;
;; Example 6: Scene Graph with Transforms
;;

(defn Render2DAPIExample6
  "Demonstrate scene graph with nested groups and transforms"
  [{:keys [node !render-id]}]
  (api2d/render! node
                 [(api2d/rect {:x 0 :y 0 :w 1 :h 1 :color [0.95 0.95 0.95 1.0]})
                  ;; Group 1: Translated circles
                  (api2d/group
                   {:transform {:translate [0.25 0.25]}
                    :children [(api2d/circle {:cx 0 :cy 0 :radius 0.1 :color :red})
                               (api2d/circle {:cx 0.15 :cy 0 :radius 0.08 :color :blue})]})
                  ;; Group 2: Translated star
                  (api2d/group
                   {:transform {:translate [0.7 0.3]}
                    :children [(api2d/star {:cx 0 :cy 0 :outer-radius 0.15 :inner-radius 0.06 :color :gold})]})
                  ;; Standalone shape (no group)
                  (api2d/ring {:cx 0.5 :cy 0.7 :radius 0.15 :thickness 0.04 :color :green})]))

;;
;; REPL Usage Examples
;;

(comment
  "REPL Interaction with the High-Level API
   =========================================

   ;; Define a shape as data
   (def my-circle
     (api2d/circle {:cx 0.5 :cy 0.5 :radius 0.3 :color :blue}))

   ;; Inspect it
   my-circle
   ;; => {:type :circle :cx 0.5 :cy 0.5 :radius 0.3 :color [0.2 0.5 1.0 1.0]}

   ;; Transform it (pure data manipulation)
   (def bigger-circle
     (update my-circle :radius * 1.5))

   bigger-circle
   ;; => {:type :circle :cx 0.5 :cy 0.5 :radius 0.45 :color [0.2 0.5 1.0 1.0]}

   ;; Change color
   (def red-circle
     (assoc my-circle :color :red))

   ;; Create a scene
   (def my-scene
     [(api2d/rect {:x 0 :y 0 :w 1 :h 1 :color :black})
      (api2d/circle {:cx 0.5 :cy 0.5 :radius 0.3 :color :red})
      (api2d/circle {:cx 0.5 :cy 0.5 :radius 0.2 :color :white})])

   ;; Modify the scene (add a new shape)
   (def updated-scene
     (conj my-scene
       (api2d/circle {:cx 0.5 :cy 0.5 :radius 0.1 :color :blue})))

   ;; Serialize to EDN
   (pr-str my-scene)
   ;; Can save to file, send over network, etc.

   ;; All of this is just data manipulation
   ;; No GPU knowledge required!")
