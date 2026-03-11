(ns nirvikalpa.api.renderer-2d
  "2D Rendering API - Ultra-minimal abstraction for 2D graphics

   Philosophy (Data-Oriented Programming):
   ===========================================

   **The Problem:**
   Every 2D sample had 120+ lines of identical GPU boilerplate:
   - Context setup
   - Pipeline creation with alpha blending
   - Buffer management
   - Render pass construction

   Only 2 things changed between samples:
   1. Fragment shader (shape/gradient/curve logic)
   2. Uniform data (colors, positions, parameters)

   **The Solution:**
   Extract all boilerplate into pure functions (DATA → CALCULATIONS → ACTIONS).

   API surface area: 2 functions
   - render-static!: One-time render (shapes, gradients, curves)
   - render-animated!: Time-based animation (rotating, pulsing, etc)

   **Benefits:**
   - 126 lines → 15 lines (88% reduction)
   - No GPU knowledge required to create shapes
   - Fragment shader is the only 'code' you write
   - Uniforms are just data (vectors of floats)

   **Usage:**
   See examples in refactored 2D samples."
  (:require [nirvikalpa.gpu :as gpu]
            [nirvikalpa.shader.common :as common]))

;;
;; Private: GPU Resource Creation (ACTION layer - impure)
;;

(defn- create-uniform-buffers!
  "Create GPU uniform buffers from data vectors.

   Args:
     device   - WebGPU device
     uniforms - Vector of vectors [[uniform0...] [uniform1...] ...]

   Returns: Vector of GPUBuffer objects

   Side effects: Allocates GPU memory, writes data

   Example:
     (create-uniform-buffers! device
                              [[1.0 0.0 0.0 1.0]      ; color
                               [0.5 0.5 0.3 0.0]])    ; params"
  [device uniforms]
  (vec
   (for [uniform-data uniforms]
     (let [buffer-size (* 4 (count uniform-data))  ; 4 bytes per float
           buffer (gpu/create-buffer device :uniform nil buffer-size)]
       (gpu/write-buffer device buffer 0
                         (js/Float32Array. (clj->js uniform-data))
                         0)
       buffer))))

(defn- create-2d-pipeline!
  "Create render pipeline for 2D rendering with alpha blending and optional MSAA.

   Args:
     device          - WebGPU device
     canvas-format   - Canvas format string
     vertex-code     - WGSL vertex shader code
     fragment-code   - WGSL fragment shader code
     msaa-count      - MSAA sample count (1, 2, 4, or 8)

   Returns: GPURenderPipeline

   Side effects: Compiles shaders, creates pipeline

   All 2D pipelines use:
   - No vertex buffers (fullscreen triangle in shader)
   - Alpha blending (premultiplied)
   - Triangle list topology
   - No depth testing
   - Optional MSAA for crisp edges"
  [device canvas-format vertex-code fragment-code msaa-count]
  (let [vert-module (gpu/create-shader-module device {:code vertex-code})
        frag-module (gpu/create-shader-module device {:code fragment-code})
        pipeline-config (cond-> {:label "2D Renderer Pipeline"
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
                          ;; Add MSAA if count > 1
                          (> msaa-count 1) (assoc :multisample {:count msaa-count}))]
    (gpu/create-render-pipeline device (clj->js pipeline-config))))

(defn- create-bind-group!
  "Create bind group from pipeline and uniform buffers.

   Args:
     device          - WebGPU device
     pipeline        - GPURenderPipeline
     uniform-buffers - Vector of GPUBuffer objects

   Returns: GPUBindGroup

   Side effects: Creates GPU bind group

   Bindings are created in order:
   - Buffer 0 → @group(0) @binding(0)
   - Buffer 1 → @group(0) @binding(1)
   - etc."
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

(defn- render-2d-frame!
  "Execute a single 2D render pass with optional MSAA.

   Args:
     device          - WebGPU device
     queue           - WebGPU queue
     context         - Canvas context
     pipeline        - GPURenderPipeline
     bind-group      - GPUBindGroup
     clear-color     - Background color [r g b a]
     msaa-texture    - Optional MSAA texture (nil if no MSAA)
     msaa-count      - MSAA sample count

   Returns: nil

   Side effects: Submits GPU commands, renders to canvas"
  [device queue context pipeline bind-group clear-color msaa-texture msaa-count]
  (let [[r g b a] clear-color
        command-encoder (gpu/create-command-encoder device)
        ;; Configure color attachment based on MSAA
        color-attachment (if (and msaa-texture (> msaa-count 1))
                           ;; MSAA: render to MSAA texture, resolve to canvas
                           {:view (gpu/create-texture-view msaa-texture)
                            :resolveTarget (gpu/create-view context)
                            :loadOp "clear"
                            :clearValue {:r r :g g :b b :a a}
                            :storeOp "discard"}  ; Discard MSAA, keep resolved
                           ;; No MSAA: render directly to canvas
                           {:view (gpu/create-view context)
                            :loadOp "clear"
                            :clearValue {:r r :g g :b b :a a}
                            :storeOp "store"})
        render-pass-descriptor (clj->js {:colorAttachments [color-attachment]})
        pass-encoder (gpu/begin-render-pass command-encoder
                                            render-pass-descriptor)]
    (doto pass-encoder
      (gpu/set-pipeline pipeline)
      (.setBindGroup 0 bind-group)
      (gpu/draw 3)  ; Fullscreen triangle = 3 vertices
      (.end))
    (.submit queue (clj->js [(.finish command-encoder)]))))

;;
;; Public API (ACTION layer - impure)
;;

(defn render-static!
  "Render a static 2D scene (shapes, gradients, curves).

   This is the simplest way to render 2D graphics. Just provide:
   1. Canvas node
   2. Fragment shader (contains your shape/gradient logic)
   3. Uniform data (colors, positions, parameters)
   4. Optional configuration map

   Args:
     node            - Canvas DOM element
     fragment-shader - WGSL fragment shader code (string)
     uniforms        - Vector of uniform data vectors
                       [[color-r color-g color-b color-a]
                        [param1 param2 param3 param4]
                        ...]
                       Each inner vector becomes one uniform buffer
                       Order matches @binding(N) in fragment shader
     options         - Optional map with:
                       :clear-color - Background [r g b a] (default: dark gray)
                       :msaa-count  - Anti-aliasing samples: 1, 4, 8 (default: 4)

   Returns: nil

   Side effects: Configures canvas, creates GPU resources, renders once

   Example (simple):
     (render-static! node
                     circle-fragment-shader
                     [[0.2 0.5 1.0 1.0]      ; color (blue)
                      [0.5 0.5 0.3 0.0]])    ; center.x center.y radius unused

   Example (with options):
     (render-static! node
                     circle-fragment-shader
                     [[0.2 0.5 1.0 1.0] [0.5 0.5 0.3 0.0]]
                     {:msaa-count 8           ; 8x MSAA for ultra-crisp edges
                      :clear-color [0 0 0 1]}) ; black background"
  ([node fragment-shader uniforms]
   (render-static! node fragment-shader uniforms {}))

  ([node fragment-shader uniforms options]
   (let [{:keys [clear-color msaa-count]
          :or {clear-color [0.1 0.1 0.1 1.0]
               msaa-count 4}} options
         context (.getContext node "webgpu")
         {:keys [device canvas-format queue]} @gpu/!gpu-config

         ;; Configure canvas
         _ (gpu/configure-canvas context {:device device
                                          :format canvas-format
                                          :alphaMode "premultiplied"})

         ;; Create MSAA texture if needed
         msaa-texture (when (> msaa-count 1)
                        (gpu/create-texture device msaa-count canvas-format node))

         ;; Create GPU resources
         pipeline (create-2d-pipeline! device canvas-format
                                       common/fullscreen-triangle-vertex
                                       fragment-shader
                                       msaa-count)
         uniform-buffers (create-uniform-buffers! device uniforms)
         bind-group (create-bind-group! device pipeline uniform-buffers)]

     ;; Render once
     (render-2d-frame! device queue context pipeline bind-group clear-color msaa-texture msaa-count))))

(defn render-animated!
  "Render an animated 2D scene with time-varying uniforms.

   For animations, pulsing, rotating, or any time-based effects.

   Args:
     node            - Canvas DOM element
     fragment-shader - WGSL fragment shader code (string)
     uniform-fn      - Function (time-seconds -> uniforms-vector)
                       Called each frame with current time
                       Returns uniform data in same format as render-static!
     !render-id      - Atom holding current render ID (for cancellation)
     render-id       - Current render ID value
     & options       - Optional {:clear-color [r g b a]}

   Returns: nil

   Side effects: Starts requestAnimationFrame loop, updates uniforms each frame

   The animation continues until render-id changes (component unmount).

   Example (pulsing circle):
     (render-animated!
       node
       circle-fragment-shader
       (fn [time]
         (let [radius (+ 0.2 (* 0.1 (Math/sin time)))]
           [[0.2 0.5 1.0 1.0]       ; color
            [0.5 0.5 radius 0.0]])) ; pulsing radius
       !render-id
       @!render-id)

   Example (rotating gradient):
     (render-animated!
       node
       linear-gradient-shader
       (fn [time]
         (let [angle time
               start-x (+ 0.5 (* 0.3 (Math/cos angle)))
               start-y (+ 0.5 (* 0.3 (Math/sin angle)))
               end-x (+ 0.5 (* 0.3 (Math/cos (+ angle Math/PI))))
               end-y (+ 0.5 (* 0.3 (Math/sin (+ angle Math/PI))))]
           [[1.0 0.2 0.3 1.0]                    ; color1
            [0.2 0.4 1.0 1.0]                    ; color2
            [start-x start-y end-x end-y]]))    ; rotating gradient
       !render-id
       @!render-id)"
  ([node fragment-shader uniform-fn !render-id render-id]
   (render-animated! node fragment-shader uniform-fn !render-id render-id {}))

  ([node fragment-shader uniform-fn !render-id render-id options]
   (let [{:keys [clear-color msaa-count]
          :or {clear-color [0.1 0.1 0.1 1.0]
               msaa-count 4}} options
         context (.getContext node "webgpu")
         {:keys [device canvas-format queue]} @gpu/!gpu-config

         ;; Configure canvas
         _ (gpu/configure-canvas context {:device device
                                          :format canvas-format
                                          :alphaMode "premultiplied"})

         ;; Create MSAA texture if needed
         msaa-texture (when (> msaa-count 1)
                        (gpu/create-texture device msaa-count canvas-format node))

         ;; Create pipeline (static, reused each frame)
         pipeline (create-2d-pipeline! device canvas-format
                                       common/fullscreen-triangle-vertex
                                       fragment-shader
                                       msaa-count)

         ;; Get initial uniforms to determine buffer count
         initial-uniforms (uniform-fn 0.0)
         uniform-buffers (create-uniform-buffers! device initial-uniforms)
         bind-group (create-bind-group! device pipeline uniform-buffers)]

     ;; Animation loop
     (letfn [(frame []
               (when (= render-id @!render-id)
                 (let [time (/ (.now js/Date) 1000.0)
                       current-uniforms (uniform-fn time)]
                   ;; Update uniform buffers
                   (doseq [[idx uniform-data] (map-indexed vector current-uniforms)]
                     (gpu/write-buffer device
                                       (nth uniform-buffers idx)
                                       0
                                       (js/Float32Array. (clj->js uniform-data))
                                       0))
                   ;; Render frame
                   (render-2d-frame! device queue context pipeline bind-group clear-color msaa-texture msaa-count)
                   ;; Schedule next frame
                   (js/requestAnimationFrame frame))))]
       (js/requestAnimationFrame frame)))))

;;
;; Raw WGSL Support (for complex shaders not yet supported by DSL)
;;

(defn render-static-raw!
  "Render a static 2D scene using raw WGSL shader strings.

   For complex shaders that the DSL doesn't support yet (nested if/let, etc).
   Same as render-static! but takes raw WGSL strings instead of compiled shaders.

   Args:
     node            - Canvas DOM element
     vertex-shader   - Raw WGSL vertex shader string
     fragment-shader - Raw WGSL fragment shader string
     uniforms        - Vector of uniform data vectors
     & options       - Optional {:clear-color [r g b a]}

   Returns: nil

   Side effects: Configures canvas, creates GPU resources, renders once

   Example (quadratic bezier):
     (render-static-raw! node
                         vertex-wgsl-string
                         fragment-wgsl-string
                         [[1.0 0.4 0.7 1.0]      ; color
                          [0.2 0.2 0.0 0.0]      ; p0
                          [0.5 0.8 0.0 0.0]      ; p1
                          [0.8 0.2 0.015 0.0]])  ; p2 + thickness"
  ([node vertex-shader fragment-shader uniforms]
   (render-static-raw! node vertex-shader fragment-shader uniforms {}))

  ([node vertex-shader fragment-shader uniforms options]
   (let [{:keys [clear-color msaa-count]
          :or {clear-color [0.1 0.1 0.1 1.0]
               msaa-count 4}} options
         context (.getContext node "webgpu")
         {:keys [device canvas-format queue]} @gpu/!gpu-config

         ;; Configure canvas
         _ (gpu/configure-canvas context {:device device
                                          :format canvas-format
                                          :alphaMode "premultiplied"})

         ;; Create MSAA texture if needed
         msaa-texture (when (> msaa-count 1)
                        (gpu/create-texture device msaa-count canvas-format node))

         ;; Create GPU resources (using raw WGSL strings)
         pipeline (create-2d-pipeline! device canvas-format
                                       vertex-shader
                                       fragment-shader
                                       msaa-count)
         uniform-buffers (create-uniform-buffers! device uniforms)
         bind-group (create-bind-group! device pipeline uniform-buffers)]

     ;; Render once
     (render-2d-frame! device queue context pipeline bind-group clear-color msaa-texture msaa-count))))

;;
;; Summary
;;

(comment
  "This namespace eliminates 100+ lines of boilerplate from every 2D sample.

   BEFORE (circle.cljs - 126 lines):
   - Manual context setup
   - Manual shader module creation
   - Manual pipeline configuration
   - Manual buffer creation and upload
   - Manual bind group creation
   - Manual render pass construction

   AFTER (circle.cljs - ~15 lines):
   - Just the fragment shader (shape logic)
   - Call render-static! with uniform data

   Benefits:
   - 88% code reduction
   - Clearer intent (what to render, not how)
   - Easier to test (fragment shader is pure WGSL)
   - Follows DOP principles:
     * Data: Uniform vectors
     * Calculations: Fragment shader
     * Actions: render-static!/render-animated!")
