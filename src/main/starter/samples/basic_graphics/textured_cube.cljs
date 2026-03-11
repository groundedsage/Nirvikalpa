(ns starter.samples.basic-graphics.textured-cube
  "Textured rotating cube using refactored textured renderer API"
  (:require [shadow.resource :as rc]
            [nirvikalpa.api.renderer :as renderer]
            [nirvikalpa.math.ga :as ga]
            [nirvikalpa.gpu :as gpu]))

;;
;; Shaders and Geometry (same as original)
;;

(def basic-vert (rc/inline "shaders/rotatingCube/basic.vert.wgsl"))
(def sample-texture-mix-color-frag (rc/inline "shaders/rotatingCube/sampleTextureMixColor.frag.wgsl"))

;; Helper function to create a vertex (position, uv)
(defn make-vertex
  [[x y z w] [u v]]
  [x y z w u v])

;; Define cube faces (quads) with positions and UVs
(def cube-faces
  [{:name :front
    :vertices [[1 -1 1 1] [-1 -1 1 1] [-1 -1 -1 1] [1 -1 -1 1]]
    :uvs [[0 1] [1 1] [1 0] [0 0]]}
   {:name :right
    :vertices [[1 1 1 1] [1 -1 1 1] [1 -1 -1 1] [1 1 -1 1]]
    :uvs [[0 1] [1 1] [1 0] [0 0]]}
   {:name :back
    :vertices [[-1 1 1 1] [1 1 1 1] [1 1 -1 1] [-1 1 -1 1]]
    :uvs [[0 1] [1 1] [1 0] [0 0]]}
   {:name :left
    :vertices [[-1 -1 1 1] [-1 1 1 1] [-1 1 -1 1] [-1 -1 -1 1]]
    :uvs [[0 1] [1 1] [1 0] [0 0]]}
   {:name :top
    :vertices [[1 1 1 1] [-1 1 1 1] [-1 -1 1 1] [1 -1 1 1]]
    :uvs [[0 1] [1 1] [1 0] [0 0]]}
   {:name :bottom
    :vertices [[1 -1 -1 1] [-1 -1 -1 1] [-1 1 -1 1] [1 1 -1 1]]
    :uvs [[0 1] [1 1] [1 0] [0 0]]}])

;; Triangulate a quad into two triangles (6 vertices)
(defn triangulate-quad
  [{:keys [vertices uvs]}]
  (let [v0 (nth vertices 0) v1 (nth vertices 1) v2 (nth vertices 2) v3 (nth vertices 3)
        uv0 (nth uvs 0) uv1 (nth uvs 1) uv2 (nth uvs 2) uv3 (nth uvs 3)]
    [(make-vertex v0 uv0)
     (make-vertex v1 uv1)
     (make-vertex v2 uv2)
     (make-vertex v2 uv2)
     (make-vertex v3 uv3)
     (make-vertex v0 uv0)]))

(def cube-vertex-array
  (js/Float32Array.
   (clj->js
    (flatten (reduce
              (fn [acc face]
                (into acc (triangulate-quad face)))
              []
              cube-faces)))))

;;
;; Transform Function (Pure - CALCULATION layer)
;;

(defn textured-cube-transform
  "Pure function for rotating textured cube."
  [time]
  (let [rotation-axis (ga/vector-3d (.sin js/Math time)
                                    (.cos js/Math time)
                                    0)]
    (ga/rotor-from-axis-angle rotation-axis 1.0)))

;;
;; Main Render Function (ACTION layer - async)
;;

(defn TexturedCube
  "Render textured rotating cube using refactored API.

"
  [{:keys [node !render-id]}]
  (let [render-id @!render-id
        {:keys [device queue]} @gpu/!gpu-config]
    ;; Async texture loading + renderer setup
    (-> (renderer/load-texture! device queue "formless-vision.png")
        (.then (fn [texture]
                 ;; Create textured renderer
                 (let [renderer (renderer/create-textured-renderer!
                                 node
                                 basic-vert
                                 sample-texture-mix-color-frag
                                 cube-vertex-array
                                 texture)]
                   ;; Start animation loop
                   (renderer/start-textured-loop! renderer
                                                  textured-cube-transform
                                                  !render-id
                                                  render-id))))
        (.catch (fn [err]
                  (js/console.error "Failed to load texture:" err))))))

