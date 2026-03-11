(ns nirvikalpa.api.geometry
  "Geometric primitive constructors for 3D shapes.
  Provides vertex data builders for cubes, triangles, spheres, and other
  primitives suitable for use with the WebGPU rendering pipeline.")

;; Create a vertex with position, color, and optional attributes
(defn make-vertex
  ([position] (make-vertex position [1 1 1 1])) ;; Default white color
  ([position color & {:keys [uv normal] :as attrs}]
   (merge
    {:position position
     :color (or color [1 1 1 1])}
    (when uv {:uv uv})
    (when normal {:normal normal})
    attrs)))

(defn cube
  ([size] (cube size {}))
  ([size {:keys [color-fn] :as opts}]
   (let [s (/ size 2)
         ;; Default color function: per-face colors matching original
         default-color-fn (fn [face]
                            (case face
                              :front  [[1 0 1 1] [0 0 1 1] [0 1 1 1] [0 1 1 1] [1 1 1 1] [1 0 1 1]]
                              :back   [[1 0 0 1] [0 0 0 1] [0 1 0 1] [0 1 0 1] [1 1 0 1] [1 0 0 1]]
                              :left   [[0 0 1 1] [0 0 0 1] [0 1 0 1] [0 1 0 1] [0 1 1 1] [0 0 1 1]]
                              :right  [[1 0 1 1] [1 0 0 1] [1 1 0 1] [1 1 0 1] [1 1 1 1] [1 0 1 1]]
                              :top    [[1 1 1 1] [0 1 1 1] [0 1 0 1] [0 1 0 1] [1 1 0 1] [1 1 1 1]]
                              :bottom [[1 0 1 1] [0 0 1 1] [0 0 0 1] [0 0 0 1] [1 0 0 1] [1 0 1 1]]))
         color-fn (or color-fn default-color-fn)
         ;; Compute colors for all faces upfront
         front-colors (color-fn :front)
         back-colors (color-fn :back)
         left-colors (color-fn :left)
         right-colors (color-fn :right)
         top-colors (color-fn :top)
         bottom-colors (color-fn :bottom)]
     (flatten
      [;; Front face
       [(make-vertex [s  (- s)   s  1] (nth front-colors 0))
        (make-vertex [(- s)  (- s)   s  1] (nth front-colors 1))
        (make-vertex [(- s)   s   s  1] (nth front-colors 2))
        (make-vertex [(- s)   s   s  1] (nth front-colors 3))
        (make-vertex [s   s   s  1] (nth front-colors 4))
        (make-vertex [s  (- s)   s  1] (nth front-colors 5))]
       ;; Back face
       [(make-vertex [s  (- s)  (- s)  1] (nth back-colors 0))
        (make-vertex [(- s)  (- s)  (- s)  1] (nth back-colors 1))
        (make-vertex [(- s)   s  (- s)  1] (nth back-colors 2))
        (make-vertex [(- s)   s  (- s)  1] (nth back-colors 3))
        (make-vertex [s   s  (- s)  1] (nth back-colors 4))
        (make-vertex [s  (- s)  (- s)  1] (nth back-colors 5))]
       ;; Left face
       [(make-vertex [(- s)  (- s)   s  1] (nth left-colors 0))
        (make-vertex [(- s)  (- s)  (- s)  1] (nth left-colors 1))
        (make-vertex [(- s)   s  (- s)  1] (nth left-colors 2))
        (make-vertex [(- s)   s  (- s)  1] (nth left-colors 3))
        (make-vertex [(- s)   s   s  1] (nth left-colors 4))
        (make-vertex [(- s)  (- s)   s  1] (nth left-colors 5))]
       ;; Right face
       [(make-vertex [s  (- s)   s  1] (nth right-colors 0))
        (make-vertex [s  (- s)  (- s)  1] (nth right-colors 1))
        (make-vertex [s   s  (- s)  1] (nth right-colors 2))
        (make-vertex [s   s  (- s)  1] (nth right-colors 3))
        (make-vertex [s   s   s  1] (nth right-colors 4))
        (make-vertex [s  (- s)   s  1] (nth right-colors 5))]
       ;; Top face
       [(make-vertex [s   s   s  1] (nth top-colors 0))
        (make-vertex [(- s)   s   s  1] (nth top-colors 1))
        (make-vertex [(- s)   s  (- s)  1] (nth top-colors 2))
        (make-vertex [(- s)   s  (- s)  1] (nth top-colors 3))
        (make-vertex [s   s  (- s)  1] (nth top-colors 4))
        (make-vertex [s   s   s  1] (nth top-colors 5))]
       ;; Bottom face
       [(make-vertex [s  (- s)   s  1] (nth bottom-colors 0))
        (make-vertex [(- s)  (- s)   s  1] (nth bottom-colors 1))
        (make-vertex [(- s)  (- s)  (- s)  1] (nth bottom-colors 2))
        (make-vertex [(- s)  (- s)  (- s)  1] (nth bottom-colors 3))
        (make-vertex [s  (- s)  (- s)  1] (nth bottom-colors 4))
        (make-vertex [s  (- s)   s  1] (nth bottom-colors 5))]]))))

(defn triangle
  ([] (triangle {}))
  ([{:keys [color]}]
   (let [default-color (or color [1 0 0 1])]
     [(make-vertex [0.0  0.5  0.0  1.0] default-color)  ;; Top
      (make-vertex [-0.5 -0.5  0.0  1.0] default-color)  ;; Bottom-left
      (make-vertex [0.5 -0.5  0.0  1.0] default-color)]))) ;; Bottom-right

;; Generate vertices for a sphere (procedural example)
(defn sphere
  ([radius segments] (sphere radius segments {}))
  ([radius segments {:keys [color]}]
   (let [color (or color [1 1 1 1])
         phi-step (/ Math/PI segments)
         theta-step (/ (* 2 Math/PI) segments)
         vertices (for [i (range (inc segments))
                        j (range (inc segments))]
                    (let [phi (* i phi-step)
                          theta (* j theta-step)
                          x (* radius (Math/sin phi) (Math/cos theta))
                          y (* radius (Math/sin phi) (Math/sin theta))
                          z (* radius (Math/cos phi))
                          vertex (make-vertex [x y z 1] color)]
                      vertex))
         indices (for [i (range segments)
                       j (range segments)]
                   (let [idx (+ j (* i (inc segments)))]
                     [idx
                      (+ idx 1)
                      (+ idx (inc segments))
                      (+ idx (inc segments))
                      (+ idx 1)
                      (+ idx (inc segments) 1)]))]
     (mapcat (fn [idx-seq]
               (map #(nth vertices %) idx-seq))
             indices))))