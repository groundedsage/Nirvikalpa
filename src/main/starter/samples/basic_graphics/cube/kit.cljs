(ns starter.samples.basic-graphics.cube.kit)


;; Byte offsets for vertex attributes
(def cube-vertex-size (* 4 10)) ;; Byte size: float4 position (16 bytes) + float4 color (16 bytes) + float2 uv (8 bytes)
(def cube-position-offset 0) ;; Position starts at byte 0
(def cube-color-offset (* 4 4)) ;; Color starts after float4 (16 bytes)
(def cube-uv-offset (* 4 8)) ;; UV starts after float4 + float4 (32 bytes)


;; Helper function to create a vertex (position, color, uv)
(defn make-vertex
  [[x y z w] [r g b a] [u v]]
  [x y z w r g b a u v])

;; Define cube faces (quads) with positions, colors, and UVs
(def cube-faces
  [{:name :front
    :vertices [[1 -1 1 1] [-1 -1 1 1] [-1 -1 -1 1] [1 -1 -1 1]]
    :colors [[1 0 1 1] [0 0 1 1] [0 0 0 1] [1 0 0 1]]
    :uvs [[0 1] [1 1] [1 0] [0 0]]}
   {:name :right
    :vertices [[1 1 1 1] [1 -1 1 1] [1 -1 -1 1] [1 1 -1 1]]
    :colors [[1 1 1 1] [1 0 1 1] [1 0 0 1] [1 1 0 1]]
    :uvs [[0 1] [1 1] [1 0] [0 0]]}
   {:name :back
    :vertices [[-1 1 1 1] [1 1 1 1] [1 1 -1 1] [-1 1 -1 1]]
    :colors [[0 1 1 1] [1 1 1 1] [1 1 0 1] [0 1 0 1]]
    :uvs [[0 1] [1 1] [1 0] [0 0]]}
   {:name :left
    :vertices [[-1 -1 1 1] [-1 1 1 1] [-1 1 -1 1] [-1 -1 -1 1]]
    :colors [[0 0 1 1] [0 1 1 1] [0 1 0 1] [0 0 0 1]]
    :uvs [[0 1] [1 1] [1 0] [0 0]]}
   {:name :top
    :vertices [[1 1 1 1] [-1 1 1 1] [-1 -1 1 1] [1 -1 1 1]]
    :colors [[1 1 1 1] [0 1 1 1] [0 0 1 1] [1 0 1 1]]
    :uvs [[0 1] [1 1] [1 0] [0 0]]}
   {:name :bottom
    :vertices [[1 -1 -1 1] [-1 -1 -1 1] [-1 1 -1 1] [1 1 -1 1]]
    :colors [[1 0 0 1] [0 0 0 1] [0 1 0 1] [1 1 0 1]]
    :uvs [[0 1] [1 1] [1 0] [0 0]]}])



;; Triangulate a quad into two triangles (6 vertices)
(defn triangulate-quad
  [{:keys [vertices colors uvs]}]
  (let [v0 (nth vertices 0) v1 (nth vertices 1) v2 (nth vertices 2) v3 (nth vertices 3)
        c0 (nth colors 0) c1 (nth colors 1) c2 (nth colors 2) c3 (nth colors 3)
        uv0 (nth uvs 0) uv1 (nth uvs 1) uv2 (nth uvs 2) uv3 (nth uvs 3)]
    [(make-vertex v0 c0 uv0)
     (make-vertex v1 c1 uv1)
     (make-vertex v2 c2 uv2)
     (make-vertex v2 c2 uv2)
     (make-vertex v3 c3 uv3)
     (make-vertex v0 c0 uv0)]))

(def cube-vertex-array
  (js/Float32Array.
   (clj->js
    (flatten (reduce
              (fn [acc face]
                (into acc (triangulate-quad face)))
              []
              cube-faces)))))