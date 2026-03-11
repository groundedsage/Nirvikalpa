(ns nirvikalpa.api.render-2d-api
  "High-Level 2D Graphics API - Dream API for Nirvikalpa

   Philosophy (Data-Oriented Programming):
   ========================================

   This namespace provides the dream API from NIRVIKALPA-CANONICAL.md:

   Instead of:
     (r2d/render-static! node fragment-shader
       [[0.2 0.5 1.0 1.0]      ; color
        [0.5 0.5 0.3 0.0]])    ; params

   You write:
     (render! canvas
       (circle {:cx 0.5 :cy 0.5 :radius 0.3 :color [0.2 0.5 1.0 1.0]}))

   Benefits:
   - Pure data representation of shapes
   - Declarative scene descriptions
   - Easy composition (just data structures)
   - No shader knowledge required
   - REPL-friendly (inspect, transform, serialize)

   Architecture:
   - Shapes are plain Clojure maps
   - render! translates shape data → renderer-2d calls
   - Separation: API layer (data) → Renderer layer (GPU effects)

   WebGPU Command Buffer Architecture:
   ====================================

   Multi-shape rendering uses a single command encoder for efficiency:

   Command Encoder (create once)
     └─> Render Pass (clear canvas once)
         ├─> Draw call 1 (first shape, set pipeline + bind group)
         ├─> Draw call 2 (second shape, set pipeline + bind group)
         └─> Draw call 3 (third shape, set pipeline + bind group)
     └─> Finish encoder
     └─> Submit to queue (once for all shapes)

   Benefits:
   - Efficient: One submission for N shapes
   - Composable: Alpha blending combines shapes
   - Correct: Shapes render in order (painter's algorithm)

   Usage:
     See examples at bottom of file"
  (:require [nirvikalpa.api.renderer-2d :as r2d]
            [nirvikalpa.gpu :as gpu]
            [nirvikalpa.shader.common :as common])
  (:require-macros [nirvikalpa.shader.dsl :refer [deffragment]]))

;;
;; Shape Constructors (CALCULATIONS - Pure Functions Returning Data)
;;

(defn circle
  "Circle: {:cx :cy :radius :color}

   Returns pure data representing a circle.

   Args:
     opts - Map with:
            :cx     - Center x (0.0 to 1.0, default 0.5)
            :cy     - Center y (0.0 to 1.0, default 0.5)
            :radius - Radius (0.0 to 1.0, default 0.3)
            :color  - RGBA color vector or keyword (default :blue)

   Examples:
     (circle {:cx 0.5 :cy 0.5 :radius 0.3 :color [1 0 0 1]})
     (circle {:cx 0.3 :cy 0.7 :radius 0.2 :color :green})"
  [{:keys [cx cy radius color]
    :or {cx 0.5 cy 0.5 radius 0.3 color :blue}}]
  {:type :circle
   :cx cx
   :cy cy
   :radius radius
   :color (if (keyword? color)
            (get {:red [1 0 0 1]
                  :green [0 1 0 1]
                  :blue [0.2 0.5 1.0 1.0]
                  :black [0 0 0 1]
                  :white [1 1 1 1]}
                 color
                 [1 1 1 1])
            color)})

(defn rect
  "Rectangle: {:x :y :w :h :color}

   Returns pure data representing a rectangle.

   Args:
     opts - Map with:
            :x     - Top-left x (0.0 to 1.0, default 0.25)
            :y     - Top-left y (0.0 to 1.0, default 0.25)
            :w     - Width (0.0 to 1.0, default 0.5)
            :h     - Height (0.0 to 1.0, default 0.5)
            :color - RGBA color vector or keyword (default :blue)

   Examples:
     (rect {:x 0.2 :y 0.3 :w 0.4 :h 0.3 :color [0 1 0 1]})
     (rect {:x 0 :y 0 :w 1 :h 1 :color :black})"
  [{:keys [x y w h color]
    :or {x 0.25 y 0.25 w 0.5 h 0.5 color :blue}}]
  {:type :rect
   :x x
   :y y
   :w w
   :h h
   :color (if (keyword? color)
            (get {:red [1 0 0 1]
                  :green [0 1 0 1]
                  :blue [0.2 0.5 1.0 1.0]
                  :black [0 0 0 1]
                  :white [1 1 1 1]}
                 color
                 [1 1 1 1])
            color)})

(defn ellipse
  "Ellipse: {:cx :cy :rx :ry :color}

   Returns pure data representing an ellipse.

   Args:
     opts - Map with:
            :cx    - Center x (0.0 to 1.0, default 0.5)
            :cy    - Center y (0.0 to 1.0, default 0.5)
            :rx    - Radius x (0.0 to 1.0, default 0.3)
            :ry    - Radius y (0.0 to 1.0, default 0.2)
            :color - RGBA color vector or keyword (default :blue)

   Examples:
     (ellipse {:cx 0.5 :cy 0.5 :rx 0.4 :ry 0.2 :color :red})"
  [{:keys [cx cy rx ry color]
    :or {cx 0.5 cy 0.5 rx 0.3 ry 0.2 color :blue}}]
  {:type :ellipse
   :cx cx
   :cy cy
   :rx rx
   :ry ry
   :color (if (keyword? color)
            (get {:red [1 0 0 1]
                  :green [0 1 0 1]
                  :blue [0.2 0.5 1.0 1.0]
                  :black [0 0 0 1]
                  :white [1 1 1 1]}
                 color
                 [1 1 1 1])
            color)})

(defn line
  "Line: {:x1 :y1 :x2 :y2 :color :width}

   Returns pure data representing a line segment.

   Args:
     opts - Map with:
            :x1    - Start x (0.0 to 1.0, default 0.2)
            :y1    - Start y (0.0 to 1.0, default 0.5)
            :x2    - End x (0.0 to 1.0, default 0.8)
            :y2    - End y (0.0 to 1.0, default 0.5)
            :width - Line thickness (default 0.01)
            :color - RGBA color vector or keyword (default :blue)

   Examples:
     (line {:x1 0 :y1 0 :x2 1 :y2 1 :color :red :width 0.02})"
  [{:keys [x1 y1 x2 y2 width color]
    :or {x1 0.2 y1 0.5 x2 0.8 y2 0.5 width 0.01 color :blue}}]
  {:type :line
   :x1 x1
   :y1 y1
   :x2 x2
   :y2 y2
   :width width
   :color (if (keyword? color)
            (get {:red [1 0 0 1]
                  :green [0 1 0 1]
                  :blue [0.2 0.5 1.0 1.0]
                  :black [0 0 0 1]
                  :white [1 1 1 1]}
                 color
                 [1 1 1 1])
            color)})

(defn triangle
  "Triangle: {:p1 :p2 :p3 :color}

   Returns pure data representing a triangle.

   Args:
     opts - Map with:
            :p1    - First vertex [x y] (default [0.5 0.2])
            :p2    - Second vertex [x y] (default [0.2 0.8])
            :p3    - Third vertex [x y] (default [0.8 0.8])
            :color - RGBA color vector or keyword (default :blue)

   Examples:
     (triangle {:p1 [0.5 0.1] :p2 [0.1 0.9] :p3 [0.9 0.9] :color :green})"
  [{:keys [p1 p2 p3 color]
    :or {p1 [0.5 0.2] p2 [0.2 0.8] p3 [0.8 0.8] color :blue}}]
  {:type :triangle
   :p1 p1
   :p2 p2
   :p3 p3
   :color (if (keyword? color)
            (get {:red [1 0 0 1]
                  :green [0 1 0 1]
                  :blue [0.2 0.5 1.0 1.0]
                  :black [0 0 0 1]
                  :white [1 1 1 1]}
                 color
                 [1 1 1 1])
            color)})

(defn rounded-rect
  "Rounded Rectangle: {:x :y :w :h :radius :color}

   Returns pure data representing a rectangle with rounded corners.

   Args:
     opts - Map with:
            :x      - Top-left x (0.0 to 1.0, default 0.25)
            :y      - Top-left y (0.0 to 1.0, default 0.25)
            :w      - Width (0.0 to 1.0, default 0.5)
            :h      - Height (0.0 to 1.0, default 0.5)
            :radius - Corner radius (default 0.05)
            :color  - RGBA color vector or keyword (default :blue)"
  [{:keys [x y w h radius color]
    :or {x 0.25 y 0.25 w 0.5 h 0.5 radius 0.05 color :blue}}]
  {:type :rounded-rect
   :x x :y y :w w :h h :radius radius
   :color (if (keyword? color)
            (get {:red [1 0 0 1] :green [0 1 0 1] :blue [0.2 0.5 1.0 1.0]
                  :black [0 0 0 1] :white [1 1 1 1]} color [1 1 1 1])
            color)})

(defn polygon
  "Regular Polygon: {:cx :cy :radius :sides :color}

   Returns pure data representing a regular n-sided polygon.

   Args:
     opts - Map with:
            :cx     - Center x (default 0.5)
            :cy     - Center y (default 0.5)
            :radius - Radius (default 0.3)
            :sides  - Number of sides (default 6 for hexagon)
            :color  - RGBA color vector or keyword (default :blue)"
  [{:keys [cx cy radius sides color]
    :or {cx 0.5 cy 0.5 radius 0.3 sides 6 color :blue}}]
  {:type :polygon
   :cx cx :cy cy :radius radius :sides sides
   :color (if (keyword? color)
            (get {:red [1 0 0 1] :green [0 1 0 1] :blue [0.2 0.5 1.0 1.0]
                  :black [0 0 0 1] :white [1 1 1 1]} color [1 1 1 1])
            color)})

(defn star
  "Star: {:cx :cy :outer-radius :inner-radius :points :color}

   Returns pure data representing a star shape.

   Args:
     opts - Map with:
            :cx           - Center x (default 0.5)
            :cy           - Center y (default 0.5)
            :outer-radius - Outer radius (default 0.3)
            :inner-radius - Inner radius (default 0.12)
            :points       - Number of points (default 5)
            :color        - RGBA color vector or keyword (default :blue)"
  [{:keys [cx cy outer-radius inner-radius points color]
    :or {cx 0.5 cy 0.5 outer-radius 0.3 inner-radius 0.12 points 5 color :blue}}]
  {:type :star
   :cx cx :cy cy :outer-radius outer-radius :inner-radius inner-radius :points points
   :color (if (keyword? color)
            (get {:red [1 0 0 1] :green [0 1 0 1] :blue [0.2 0.5 1.0 1.0]
                  :black [0 0 0 1] :white [1 1 1 1] :gold [1.0 0.84 0.0 1.0]} color [1 1 1 1])
            color)})

(defn ring
  "Ring/Donut: {:cx :cy :radius :thickness :color}

   Returns pure data representing a ring (circle with hole).

   Args:
     opts - Map with:
            :cx        - Center x (default 0.5)
            :cy        - Center y (default 0.5)
            :radius    - Ring radius (default 0.25)
            :thickness - Ring thickness (default 0.08)
            :color     - RGBA color vector or keyword (default :blue)"
  [{:keys [cx cy radius thickness color]
    :or {cx 0.5 cy 0.5 radius 0.25 thickness 0.08 color :blue}}]
  {:type :ring
   :cx cx :cy cy :radius radius :thickness thickness
   :color (if (keyword? color)
            (get {:red [1 0 0 1] :green [0 1 0 1] :blue [0.2 0.5 1.0 1.0]
                  :black [0 0 0 1] :white [1 1 1 1]} color [1 1 1 1])
            color)})

(defn point
  "Point: {:x :y :size :color}

   Returns pure data representing a point (small circle).

   Args:
     opts - Map with:
            :x     - Position x (default 0.5)
            :y     - Position y (default 0.5)
            :size  - Point size (default 0.01)
            :color - RGBA color vector or keyword (default :white)"
  [{:keys [x y size color]
    :or {x 0.5 y 0.5 size 0.01 color :white}}]
  {:type :point
   :x x :y y :size size
   :color (if (keyword? color)
            (get {:red [1 0 0 1] :green [0 1 0 1] :blue [0.2 0.5 1.0 1.0]
                  :black [0 0 0 1] :white [1 1 1 1]} color [1 1 1 1])
            color)})

;;
;; Scene Graph Nodes
;;

(defn group
  "Group: {:transform :children}

   Returns pure data representing a group of shapes with shared transform.

   Args:
     opts - Map with:
            :transform - Optional transform map with:
                         :translate [x y] - Translation offset
                         :rotate angle    - Rotation in degrees
                         :scale [sx sy]   - Scale factors
            :children  - Vector of child shapes or groups

   Examples:
     ;; Group with translation
     (group {:transform {:translate [0.2 0.3]}
             :children [(circle {...}) (rect {...})]})

     ;; Nested groups
     (group {:children
             [(group {:transform {:rotate 45}
                      :children [(rect {:x 0 :y 0 :w 0.2 :h 0.2 :color :red})]})
              (circle {:cx 0.7 :cy 0.7 :radius 0.2 :color :blue})]})"
  [{:keys [transform children]
    :or {transform {} children []}}]
  {:type :group
   :transform transform
   :children (vec children)})

;;
;; Transform Utilities (Pure Functions)
;;

(defn translate
  "Apply translation to a shape or group.

   Pure function: returns new shape with updated position.

   Args:
     shape - Shape or group
     dx    - Delta x
     dy    - Delta y

   Examples:
     (translate (circle {:cx 0.5 :cy 0.5 :radius 0.3}) 0.2 0.1)
     ;; => {:type :circle :cx 0.7 :cy 0.6 :radius 0.3 ...}"
  [shape dx dy]
  (case (:type shape)
    :circle (-> shape
                (update :cx + dx)
                (update :cy + dy))
    :rect (-> shape
              (update :x + dx)
              (update :y + dy))
    :ellipse (-> shape
                 (update :cx + dx)
                 (update :cy + dy))
    :line (-> shape
              (update :x1 + dx)
              (update :y1 + dy)
              (update :x2 + dx)
              (update :y2 + dy))
    :triangle (-> shape
                  (update :p1 (fn [[x y]] [(+ x dx) (+ y dy)]))
                  (update :p2 (fn [[x y]] [(+ x dx) (+ y dy)]))
                  (update :p3 (fn [[x y]] [(+ x dx) (+ y dy)])))
    :rounded-rect (-> shape
                      (update :x + dx)
                      (update :y + dy))
    :polygon (-> shape
                 (update :cx + dx)
                 (update :cy + dy))
    :star (-> shape
              (update :cx + dx)
              (update :cy + dy))
    :ring (-> shape
              (update :cx + dx)
              (update :cy + dy))
    :point (-> shape
               (update :x + dx)
               (update :y + dy))
    :group (update shape :transform
                   (fn [t]
                     (update t :translate
                             (fn [pos]
                               (let [[x y] (or pos [0 0])]
                                 [(+ x dx) (+ y dy)])))))
    shape))  ; Unknown type, return unchanged

;;
;; Shaders (one per shape type)
;;

(deffragment circle-shader [uv :vec2f]
  :uniforms [[color :vec4f :group 0 :binding 0]
             [params :vec4f :group 0 :binding 1]]
  :output [:out_color :vec4f :location 0]
  (let [center (vec2f params.x params.y)
        radius params.z
        dist (- (distance uv center) radius)
        edge-width (fwidth dist)
        alpha (smoothstep (* 0.7 edge-width) (* (- 0.7) edge-width) dist)]
    (vec4f color.rgb (* color.a alpha))))

(deffragment rect-shader [uv :vec2f]
  :uniforms [[color :vec4f :group 0 :binding 0]
             [params :vec4f :group 0 :binding 1]]
  :output [:out_color :vec4f :location 0]
  (let [center (vec2f params.x params.y)
        size (vec2f params.z params.w)
        q (- (abs (- uv center)) size)
        dist (+ (length (max q (vec2f 0.0 0.0)))
                (min (max q.x q.y) 0.0))
        edge-width (fwidth dist)
        alpha (smoothstep (* 0.7 edge-width) (* (- 0.7) edge-width) dist)]
    (vec4f color.rgb (* color.a alpha))))

(deffragment ellipse-shader [uv :vec2f]
  :uniforms [[color :vec4f :group 0 :binding 0]
             [params :vec4f :group 0 :binding 1]]
  :output [:out_color :vec4f :location 0]
  (let [center (vec2f params.x params.y)
        radius (vec2f params.z params.w)
        p (- uv center)
        k (/ (abs p) radius)
        dist (- (* (length k) (- (length k) 1.0))
                (/ 1.0 (length k)))
        edge-width (fwidth dist)
        alpha (smoothstep (* 0.7 edge-width) (* (- 0.7) edge-width) dist)]
    (vec4f color.rgb (* color.a alpha))))

(deffragment line-shader [uv :vec2f]
  :uniforms [[color :vec4f :group 0 :binding 0]
             [params :vec4f :group 0 :binding 1]]
  :output [:out_color :vec4f :location 0]
  (let [p1 (vec2f params.x params.y)
        p2 (vec2f params.z params.w)
        ;; Line segment SDF - flatten nested lets into sequential bindings
        pa (- uv p1)
        ba (- p2 p1)
        h (clamp (/ (dot pa ba) (dot ba ba)) 0.0 1.0)
        dist (length (- pa (* ba h)))
        edge-width (fwidth dist)
        width 0.01
        alpha (smoothstep (* 0.7 (+ width edge-width))
                          (* 0.7 (- width edge-width))
                          dist)]
    (vec4f color.rgb (* color.a alpha))))

(deffragment triangle-shader [uv :vec2f]
  :uniforms [[color :vec4f :group 0 :binding 0]
             [p0 :vec4f :group 0 :binding 1]
             [p1 :vec4f :group 0 :binding 2]
             [p2 :vec4f :group 0 :binding 3]]
  :output [:out_color :vec4f :location 0]
  (let [v0 (vec2f p0.x p0.y)
        v1 (vec2f p1.x p1.y)
        v2 (vec2f p2.x p2.y)
        ;; Triangle SDF - flatten all nested lets
        e0 (- v1 v0)
        e1 (- v2 v1)
        e2 (- v0 v2)
        v0_uv (- uv v0)
        v1_uv (- uv v1)
        v2_uv (- uv v2)
        pq0 (- v0_uv (* e0 (clamp (/ (dot v0_uv e0) (dot e0 e0)) 0.0 1.0)))
        pq1 (- v1_uv (* e1 (clamp (/ (dot v1_uv e1) (dot e1 e1)) 0.0 1.0)))
        pq2 (- v2_uv (* e2 (clamp (/ (dot v2_uv e2) (dot e2 e2)) 0.0 1.0)))
        d (min (min (dot pq0 pq0) (dot pq1 pq1)) (dot pq2 pq2))
        s (* (sign (- (* (- v0.x v2.x) (- uv.y v0.y))
                      (* (- v0.y v2.y) (- uv.x v0.x))))
             (sign (- (* (- v1.x v0.x) (- uv.y v1.y))
                      (* (- v1.y v0.y) (- uv.x v1.x))))
             (sign (- (* (- v2.x v1.x) (- uv.y v2.y))
                      (* (- v2.y v1.y) (- uv.x v2.x)))))
        dist (* s (sqrt d))
        edge-width (fwidth dist)
        alpha (smoothstep (* 0.7 edge-width) (* (- 0.7) edge-width) dist)]
    (vec4f color.rgb (* color.a alpha))))

(deffragment rounded-rect-shader [uv :vec2f]
  :uniforms [[color :vec4f :group 0 :binding 0]
             [params1 :vec4f :group 0 :binding 1]
             [params2 :vec4f :group 0 :binding 2]]
  :output [:out_color :vec4f :location 0]
  (let [center-x (+ params1.x (/ params1.z 2.0))
        center-y (+ params1.y (/ params1.w 2.0))
        center (vec2f center-x center-y)
        size (vec2f (/ params1.z 2.0) (/ params1.w 2.0))
        radius params2.x
        q (- (abs (- uv center)) (- size radius))
        dist (- (+ (distance (max q (vec2f 0.0 0.0)) (vec2f 0.0 0.0))
                   (min (max q.x q.y) 0.0))
                radius)
        edge-width (fwidth dist)
        alpha (smoothstep (* 0.7 edge-width) (* (- 0.7) edge-width) dist)]
    (vec4f color.rgb (* color.a alpha))))

(deffragment polygon-shader [uv :vec2f]
  :uniforms [[color :vec4f :group 0 :binding 0]
             [params :vec4f :group 0 :binding 1]]
  :output [:out_color :vec4f :location 0]
  (let [center (vec2f params.x params.y)
        radius params.z
        ;; Hexagon SDF (hardcoded for now, TODO: parameterize sides)
        k (vec3f (- 0.866025404) 0.5 0.577350269)
        p (abs (- uv center))
        p-reflected (- p (* (* 2.0 (min (dot (vec2f k.x k.y) p) 0.0))
                            (vec2f k.x k.y)))
        p-final (- p-reflected
                   (vec2f (clamp p-reflected.x (* (- k.z) radius) (* k.z radius))
                          radius))
        dist (* (distance p-final (vec2f 0.0 0.0))
                (sign p-final.y))
        edge-width (fwidth dist)
        alpha (smoothstep (* 0.7 edge-width) (* (- 0.7) edge-width) dist)]
    (vec4f color.rgb (* color.a alpha))))

(deffragment star-shader [uv :vec2f]
  :uniforms [[color :vec4f :group 0 :binding 0]
             [params :vec4f :group 0 :binding 1]]
  :output [:out_color :vec4f :location 0]
  (let [center (vec2f params.x params.y)
        r-outer params.z
        r-inner params.w
        p (- uv center)
        pi 3.141593
        an (/ pi 5.0)
        en (/ (* 2.0 pi) 5.0)
        bn (- (modulo (atan2 p.y p.x) en) an)
        p-len (distance p (vec2f 0.0 0.0))
        r (mix r-inner r-outer (+ 0.5 (* 0.5 (cos (* bn 5.0)))))
        dist (- p-len r)
        edge-width (fwidth dist)
        alpha (smoothstep (* 0.7 edge-width) (* (- 0.7) edge-width) dist)]
    (vec4f color.rgb (* color.a alpha))))

(deffragment ring-shader [uv :vec2f]
  :uniforms [[color :vec4f :group 0 :binding 0]
             [params :vec4f :group 0 :binding 1]]
  :output [:out_color :vec4f :location 0]
  (let [center (vec2f params.x params.y)
        radius params.z
        thickness params.w
        dist-from-center (distance uv center)
        dist-from-ring (- (abs (- dist-from-center radius)) thickness)
        edge-width (fwidth dist-from-ring)
        alpha (smoothstep (* 0.7 edge-width) (* (- 0.7) edge-width) dist-from-ring)]
    (vec4f color.rgb (* color.a alpha))))

(deffragment point-shader [uv :vec2f]
  :uniforms [[color :vec4f :group 0 :binding 0]
             [params :vec4f :group 0 :binding 1]]
  :output [:out_color :vec4f :location 0]
  (let [pos (vec2f params.x params.y)
        size params.z
        dist (- (distance uv pos) size)
        edge-width (fwidth dist)
        alpha (smoothstep (* 0.7 edge-width) (* (- 0.7) edge-width) dist)]
    (vec4f color.rgb (* color.a alpha))))

;;
;; Rendering (ACTION layer - translates data to GPU commands)
;;

(defn- create-shape-pipeline!
  "Create render pipeline for a specific shape type.

   Pure GPU resource creation - no rendering.

   Args:
     device        - WebGPU device
     canvas-format - Canvas format string
     shader        - Compiled WGSL shader string
     msaa-count    - MSAA sample count

   Returns: GPURenderPipeline"
  [device canvas-format shader msaa-count]
  (let [vert-module (gpu/create-shader-module device {:code common/fullscreen-triangle-vertex})
        frag-module (gpu/create-shader-module device {:code shader})
        pipeline-config (cond-> {:label "2D Shape Pipeline"
                                 :layout "auto"
                                 :vertex {:module vert-module
                                          :entryPoint "main"}
                                 :fragment {:module frag-module
                                            :entryPoint "main"
                                            :targets [{:format canvas-format
                                                       :blend {:color {:srcFactor "src-alpha"
                                                                       :dstFactor "one-minus-src-alpha"
                                                                       :operation "add"}
                                                               :alpha {:srcFactor "one"
                                                                       :dstFactor "one-minus-src-alpha"
                                                                       :operation "add"}}}]}
                                 :primitive {:topology "triangle-list"}}
                          (> msaa-count 1) (assoc :multisample {:count msaa-count}))]
    (gpu/create-render-pipeline device (clj->js pipeline-config))))

(defn- create-uniform-buffers!
  "Create GPU uniform buffers from data vectors.

   Args:
     device   - WebGPU device
     uniforms - Vector of vectors [[uniform0...] [uniform1...] ...]

   Returns: Vector of GPUBuffer objects"
  [device uniforms]
  (vec
   (for [uniform-data uniforms]
     (let [buffer-size (* 4 (count uniform-data))
           buffer (gpu/create-buffer device :uniform nil buffer-size)]
       (gpu/write-buffer device buffer 0
                         (js/Float32Array. (clj->js uniform-data))
                         0)
       buffer))))

(defn- create-bind-group!
  "Create bind group from pipeline and uniform buffers.

   Args:
     device          - WebGPU device
     pipeline        - GPURenderPipeline
     uniform-buffers - Vector of GPUBuffer objects

   Returns: GPUBindGroup"
  [device pipeline uniform-buffers]
  (let [layout (gpu/create-bind-group-layout pipeline 0)
        entries (map-indexed
                 (fn [idx buffer]
                   {:binding idx
                    :resource {:buffer buffer}})
                 uniform-buffers)]
    (gpu/create-bind-group device
                           (clj->js {:layout layout
                                     :entries (clj->js entries)}))))

(defn- shape->uniforms
  "Convert shape data to uniform vectors for renderer-2d.

   Pure function: data → data transformation

   Each shape type has its own uniform layout matching its shader."
  [shape]
  (case (:type shape)
    :circle
    [(:color shape)
     [(:cx shape) (:cy shape) (:radius shape) 0.0]]

    :rect
    (let [center-x (+ (:x shape) (/ (:w shape) 2))
          center-y (+ (:y shape) (/ (:h shape) 2))
          half-w (/ (:w shape) 2)
          half-h (/ (:h shape) 2)]
      [(:color shape)
       [center-x center-y half-w half-h]])

    :ellipse
    [(:color shape)
     [(:cx shape) (:cy shape) (:rx shape) (:ry shape)]]

    :line
    [(:color shape)
     [(:x1 shape) (:y1 shape) (:x2 shape) (:y2 shape)]]

    :triangle
    (let [[x1 y1] (:p1 shape)
          [x2 y2] (:p2 shape)
          [x3 y3] (:p3 shape)]
      [(:color shape)
       [x1 y1 0.0 0.0]
       [x2 y2 0.0 0.0]
       [x3 y3 0.0 0.0]])

    :rounded-rect
    [(:color shape)
     [(:x shape) (:y shape) (:w shape) (:h shape)]
     [(:radius shape) 0.0 0.0 0.0]]

    :polygon
    [(:color shape)
     [(:cx shape) (:cy shape) (:radius shape) (:sides shape)]]

    :star
    [(:color shape)
     [(:cx shape) (:cy shape) (:outer-radius shape) (:inner-radius shape)]]

    :ring
    [(:color shape)
     [(:cx shape) (:cy shape) (:radius shape) (:thickness shape)]]

    :point
    [(:color shape)
     [(:x shape) (:y shape) (:size shape) 0.0]]

    ;; Default fallback
    [[1 0 1 1] [0 0 0 0]]))

(defn- shape->shader
  "Get the shader for a shape type.

   Pure function: shape type → shader reference"
  [shape]
  (case (:type shape)
    :circle circle-shader
    :rect rect-shader
    :ellipse ellipse-shader
    :line line-shader
    :triangle triangle-shader
    :rounded-rect rounded-rect-shader
    :polygon polygon-shader
    :star star-shader
    :ring ring-shader
    :point point-shader
    circle-shader))  ; fallback

;;
;; Scene Graph Flattening (CALCULATION - Pure)
;;

(defn- flatten-scene-graph
  "Flatten scene graph into a list of transformed shapes.

   Traverses the hierarchy, accumulating transforms, and returns
   a flat list of shapes with transforms applied.

   Pure function: scene graph → [shape1 shape2 ...]

   Args:
     node      - Shape or group node
     transform - Accumulated transform from parent groups

   Returns: Vector of leaf shapes with transforms applied

   Example:
     (flatten-scene-graph
       (group {:transform {:translate [0.5 0.5]}
               :children [(circle {:cx 0 :cy 0 :radius 0.2})]})
       {})
     ;; => [{:type :circle :cx 0.5 :cy 0.5 :radius 0.2}]"
  ([node] (flatten-scene-graph node {}))
  ([node parent-transform]
   (if (= (:type node) :group)
     ;; Group node - traverse children with accumulated transform
     (let [group-transform (:transform node {})
           ;; Combine parent transform with this group's transform
           combined-transform (merge parent-transform group-transform)
           children (:children node [])]
       ;; Recursively flatten each child
       (vec (mapcat #(flatten-scene-graph % combined-transform) children)))
     ;; Leaf shape - apply accumulated transform
     (let [{translate-vec :translate
            rotate-angle :rotate
            scale-vec :scale} parent-transform]
       ;; For now, only implement translate (Phase 1: Make it work)
       ;; TODO: Add rotate and scale support
       (if translate-vec
         (let [[dx dy] translate-vec]
           ;; Call the translate function
           [(translate node dx dy)])
         [node])))))

(defn render!
  "Render one or more shapes to canvas.

   This is the dream API - just pass shapes as data!

   Architecture:
   - Single command encoder for all shapes
   - Single render pass with multiple draw calls
   - Each shape gets its own pipeline + bind group
   - Proper alpha blending for composition

   Args:
     canvas  - Canvas DOM element
     shapes  - One or more shape data structures
               Can be a single shape or a vector of shapes
     options - Optional map:
               :clear-color - Background [r g b a] (default: dark gray)
               :msaa-count  - Anti-aliasing samples: 1, 4, 8 (default: 4)

   Returns: nil

   Side effects: Renders shapes to canvas using WebGPU

   Examples:
     ;; Single shape
     (render! canvas
       (circle {:cx 0.5 :cy 0.5 :radius 0.3 :color :blue}))

     ;; Multiple shapes (composited via alpha blending)
     (render! canvas
       [(rect {:x 0.2 :y 0.2 :w 0.3 :h 0.4 :color :red})
        (circle {:cx 0.7 :cy 0.7 :radius 0.2 :color :green})])

     ;; With options
     (render! canvas
       (circle {:cx 0.5 :cy 0.5 :radius 0.3 :color :blue})
       {:msaa-count 8 :clear-color [1 1 1 1]})"
  ([canvas shapes]
   (render! canvas shapes {}))

  ([canvas shapes options]
   (let [;; Normalize to vector
         shape-list (cond
                      (vector? shapes) shapes
                      (seq? shapes) (vec shapes)
                      :else [shapes])
         ;; Flatten scene graph (handles groups with transforms)
         flattened-shapes (vec (mapcat flatten-scene-graph shape-list))
         {:keys [clear-color msaa-count]
          :or {clear-color [0.1 0.1 0.1 1.0]
               msaa-count 4}} options
         [r g b a] clear-color
         context (.getContext canvas "webgpu")
         {:keys [device canvas-format queue]} @gpu/!gpu-config]

     (when (seq flattened-shapes)
       ;; Configure canvas
       (gpu/configure-canvas context {:device device
                                      :format canvas-format
                                      :alphaMode "premultiplied"})

       ;; Create MSAA texture if needed
       (let [msaa-texture (when (> msaa-count 1)
                            (gpu/create-texture device msaa-count canvas-format canvas))

             ;; Create ONE command encoder for all shapes
             command-encoder (gpu/create-command-encoder device)

             ;; Configure color attachment
             color-attachment (if (and msaa-texture (> msaa-count 1))
                                {:view (gpu/create-texture-view msaa-texture)
                                 :resolveTarget (gpu/create-view context)
                                 :loadOp "clear"
                                 :clearValue {:r r :g g :b b :a a}
                                 :storeOp "discard"}
                                {:view (gpu/create-view context)
                                 :loadOp "clear"
                                 :clearValue {:r r :g g :b b :a a}
                                 :storeOp "store"})

             ;; Begin ONE render pass
             render-pass-descriptor (clj->js {:colorAttachments [color-attachment]})
             pass-encoder (gpu/begin-render-pass command-encoder render-pass-descriptor)]

         ;; Draw each shape (multiple draw calls in same pass)
         (doseq [shape flattened-shapes]
           (let [shader (shape->shader shape)
                 uniforms (shape->uniforms shape)
                 ;; Create pipeline for this shape type
                 pipeline (create-shape-pipeline! device canvas-format shader msaa-count)
                 ;; Create uniform buffers for this shape
                 uniform-buffers (create-uniform-buffers! device uniforms)
                 ;; Create bind group for this shape
                 bind-group (create-bind-group! device pipeline uniform-buffers)]
             ;; Draw this shape
             (doto pass-encoder
               (gpu/set-pipeline pipeline)
               (.setBindGroup 0 bind-group)
               (gpu/draw 3))))  ; Fullscreen triangle

         ;; End pass and submit (once for all shapes)
         (.end pass-encoder)
         (.submit queue (clj->js [(.finish command-encoder)])))))))

;;
;; Examples
;;

(comment
  "Usage Examples
   ==============

   Basic shapes:
   -------------
   (render! canvas
     (circle {:cx 0.5 :cy 0.5 :radius 0.3 :color :blue}))

   (render! canvas
     (rect {:x 0.2 :y 0.3 :w 0.4 :h 0.3 :color :red}))

   (render! canvas
     (ellipse {:cx 0.5 :cy 0.5 :rx 0.4 :ry 0.2 :color :green}))

   (render! canvas
     (line {:x1 0.2 :y1 0.2 :x2 0.8 :y2 0.8 :color :black}))

   (render! canvas
     (triangle {:p1 [0.5 0.2] :p2 [0.2 0.8] :p3 [0.8 0.8] :color :purple}))

   Multiple shapes (composition):
   ------------------------------
   (render! canvas
     [(rect {:x 0 :y 0 :w 1 :h 1 :color :white})
      (circle {:cx 0.5 :cy 0.5 :radius 0.3 :color :red})
      (circle {:cx 0.5 :cy 0.5 :radius 0.2 :color :white})
      (circle {:cx 0.5 :cy 0.5 :radius 0.1 :color :blue})])

   Data-driven scenes:
   -------------------
   (def my-scene
     {:background (rect {:x 0 :y 0 :w 1 :h 1 :color :black})
      :sun (circle {:cx 0.8 :cy 0.2 :radius 0.1 :color [1.0 0.9 0.0 1.0]})
      :ground (rect {:x 0 :y 0.6 :w 1 :h 0.4 :color [0.2 0.6 0.2 1.0]})})

   (render! canvas (vals my-scene))

   REPL interaction:
   -----------------
   ;; Define shape
   (def my-circle (circle {:cx 0.5 :cy 0.5 :radius 0.3 :color :blue}))

   ;; Inspect it
   my-circle
   ;; => {:type :circle :cx 0.5 :cy 0.5 :radius 0.3 :color [0.2 0.5 1.0 1.0]}

   ;; Transform it
   (def bigger-circle (update my-circle :radius * 1.5))

   ;; Render it
   (render! canvas bigger-circle)")
