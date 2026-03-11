(ns starter.samples.basic-graphics.instanced-cubes
  "Instanced cubes (4x4 grid) using refactored instancing renderer.

   This sample demonstrates:
   - GPU instancing (16 cubes in single draw call!)
   - Array of transforms in uniform buffer
   - Programmatic instance generation
   - Performance benefits of instancing"
  (:require [shadow.resource :as rc]
            [nirvikalpa.api.renderer :as renderer]
            [nirvikalpa.math.ga :as ga]
            [starter.samples.basic-graphics.cube.kit :as kit]))

;;
;; Shaders (from resources - uses instance_index builtin)
;;

(def instanced-vert (rc/inline "shaders/rotatingCube/instanced.vert.wgsl"))
(def vertex-position-color-frag (rc/inline "shaders/rotatingCube/vertexPositionColor.frag.wgsl"))

;;
;; Instance Configuration (Pure Data - CALCULATION layer)
;;

(defn generate-instance-configs
  "Generate instance configurations in a grid (pure function).

   Args:
     x-count - Number of instances in X direction
     y-count - Number of instances in Y direction
     step    - Spacing between instances

   Returns: Vector of instance configs"
  [x-count y-count step]
  (for [x (range x-count)
        y (range y-count)]
    (let [;; Position in grid
          x-pos (- (* x step) (* (/ (dec x-count) 2) step))
          y-pos (- (* y step) (* (/ (dec y-count) 2) step))
          ;; Unique rotation for each instance (based on grid position)
          phase (* (+ x (* y x-count)) 0.5)]
      {:position [x-pos y-pos 0]
       :rotor-fn (fn [time]
                   (let [t (+ time phase)
                         rotation-axis (ga/vector-3d (.sin js/Math t)
                                                     (.cos js/Math t)
                                                     0)]
                     (ga/rotor-from-axis-angle rotation-axis 1.0)))})))

;;
;; Main Render Function (ACTION layer)
;;

(defn InstancedCubes
  "Render 4x4 grid of rotating cubes using GPU instancing.

"
  [{:keys [node !render-id]}]
  (let [render-id @!render-id
        x-count 4
        y-count 4
        num-instances (* x-count y-count)
        ;; Generate instance configurations (pure data)
        instances (generate-instance-configs x-count y-count 4.0)
        ;; Create instanced renderer
        renderer (renderer/create-instanced-renderer!
                  node
                  instanced-vert
                  vertex-position-color-frag
                  kit/cube-vertex-array
                  num-instances)]
    ;; Start animation loop
    (renderer/start-instanced-loop! renderer
                                    instances
                                    !render-id
                                    render-id)))
