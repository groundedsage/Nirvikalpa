(ns nirvikalpa.api.scene
  "Pure data structures for scene description - DATA layer

   Following Data-Oriented Programming principles:
   1. Separate code from data
   2. Represent data with generic data structures (maps, vectors)
   3. Data is immutable
   4. Schema is separate from representation

   A scene is just data - no behavior, no classes, no side effects.
   It can be inspected, transformed, serialized, and versioned.")

;;
;; Camera (Pure Data)
;;

(defn camera
  "Create a camera specification (pure data).

   Args:
     :position   - [x y z] world position (default [0 0 -4])
     :look-at    - [x y z] target point (default [0 0 0])
     :up         - [x y z] up vector (default [0 1 0])
     :fov        - Field of view in radians (default Math/PI * 2/5)
     :near       - Near clipping plane (default 1)
     :far        - Far clipping plane (default 100)

   Returns: Camera data map

   Example:
     (camera {:position [0 0 -4]
              :fov (/ Math/PI 2.5)})"
  [{:keys [position look-at up fov near far]
    :or {position [0 0 -4]
         look-at [0 0 0]
         up [0 1 0]
         fov (* js/Math.PI (/ 2 5))
         near 1.0
         far 100.0}}]
  {:type :camera
   :position position
   :look-at look-at
   :up up
   :fov fov
   :near near
   :far far})

;;
;; Transform (Pure Data)
;;

(defn static-transform
  "Create a static transform (pure data).

   Args:
     :rotor - GA rotor for rotation (from nirvikalpa.math.ga)

   Returns: Static transform data

   Example:
     (static-transform {:rotor my-rotor})"
  [{:keys [rotor] :or {rotor nil}}]
  {:type :static-transform
   :rotor rotor})

(defn dynamic-transform
  "Create a time-dependent transform (pure data).

   Args:
     :rotor-fn - Function (time -> rotor) that computes rotor based on time

   Returns: Dynamic transform data

   Example:
     (dynamic-transform
       {:rotor-fn (fn [t]
                    (ga/rotor-from-axis-angle
                      (ga/vector-3d (Math/sin t) (Math/cos t) 0)
                      1.0))})"
  [{:keys [rotor-fn]}]
  {:pre [(fn? rotor-fn)]}
  {:type :dynamic-transform
   :rotor-fn rotor-fn})

;;
;; Object (Pure Data)
;;

(defn object
  "Create a renderable object (pure data).

   Args:
     :geometry  - Geometry type keyword (:cube, :sphere, etc.)
     :shader    - Shader code (from defvertex/deffragment)
     :transform - Transform data (from static-transform or dynamic-transform)

   Returns: Object data map

   Example:
     (object {:geometry :cube
              :shader my-shader
              :transform (dynamic-transform {...})})"
  [{:keys [geometry shader transform]
    :or {transform (static-transform {})}}]
  {:type :object
   :geometry geometry
   :shader shader
   :transform transform})

;;
;; Scene (Pure Data)
;;

(defn scene
  "Create a complete scene specification (pure data).

   Args:
     :camera  - Camera data (from camera fn)
     :objects - Vector of object data (from object fn)

   Returns: Scene data map

   Example:
     (scene {:camera (camera {:position [0 0 -4]})
             :objects [(object {:geometry :cube
                                :shader my-shader
                                :transform ...})]})"
  [{:keys [camera objects]
    :or {camera (camera {})
         objects []}}]
  {:pre [(vector? objects)]}
  {:type :scene
   :camera camera
   :objects objects})
