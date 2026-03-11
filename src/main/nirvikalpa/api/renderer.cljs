(ns nirvikalpa.api.renderer
  "GPU rendering implementation - ACTION layer

   Following the Three-Layer Taxonomy:
   - Actions: GPU I/O, buffer creation, render passes (THIS MODULE)
   - Calculations: Scene data → GPU data transformations (see transforms.cljs)
   - Data: Pure scene descriptions (see scene.cljs)

   This module handles all side effects (GPU operations).
   All functions here are IMPURE - they interact with GPU hardware."
  (:require [nirvikalpa.gpu :as gpu]
            [nirvikalpa.math.ga :as ga]
            [nirvikalpa.shader.codegen :as codegen]
            ["wgpu-matrix" :as wgpu-matrix]))

;;
;; Geometry Registry (Pure Data)
;;

(def geometry-registry
  "Registry of available geometries.

   Maps geometry keywords to vertex data.
   This is DATA, not actions - it's a lookup table."
  {:cube nil  ; Will be populated from kit
   :sphere nil
   :plane nil})

(defn register-geometry!
  "Register a geometry type with vertex data (ACTION - mutates registry).

   Args:
     geometry-key - Keyword identifying the geometry
     vertex-data  - Float32Array of vertex data

   Returns: nil

   Side effects: Mutates geometry-registry

   Example:
     (register-geometry! :cube cube-vertex-array)"
  [geometry-key vertex-data]
  (set! geometry-registry
        (assoc geometry-registry geometry-key vertex-data)))

;;
;; GPU Resource Management (Actions - I/O)
;;

(defn- create-vertex-buffer!
  "Create and upload vertex buffer to GPU (ACTION - GPU I/O).

   Args:
     device      - WebGPU device
     vertex-data - Float32Array of vertex data

   Returns: GPUBuffer

   Side effects: Allocates GPU memory, uploads data"
  [device vertex-data]
  (let [buffer (gpu/create-buffer device :vertex nil vertex-data true)]
    (.set (js/Float32Array. (.getMappedRange buffer)) vertex-data)
    (.unmap buffer)
    (gpu/write-buffer device buffer 0 vertex-data 0)
    buffer))

(defn- create-uniform-buffer!
  "Create uniform buffer for transformation matrices (ACTION - GPU I/O).

   Args:
     device - WebGPU device
     size   - Buffer size in bytes (default 64 for mat4x4)

   Returns: GPUBuffer

   Side effects: Allocates GPU memory"
  [device & {:keys [size] :or {size 64}}]
  (gpu/create-buffer device :uniform nil size))

(defn- create-depth-texture!
  "Create depth texture for depth testing (ACTION - GPU I/O).

   Args:
     device - WebGPU device
     canvas - Canvas element

   Returns: GPUTexture

   Side effects: Allocates GPU memory"
  [device canvas]
  (gpu/create-texture device "depth24plus" canvas))

;;
;; Transform Calculations (Pure Functions)
;;

(defn- rotor->mat4
  "Convert GA rotor to 4x4 matrix (CALCULATION - pure).

   Delegates to ga/rotor->mat4 for the actual conversion.

   Args:
     rotor - GA rotor from nirvikalpa.math.ga

   Returns: Float32Array (4x4 matrix in column-major order)"
  [rotor]
  (ga/rotor->mat4 rotor))

(defn- compute-mvp-matrix
  "Compute Model-View-Projection matrix (CALCULATION - pure).

   Args:
     projection-matrix - Projection matrix (from camera)
     view-matrix       - View matrix (camera transform)
     model-matrix      - Model matrix (object transform)

   Returns: Float32Array (MVP matrix)"
  [projection-matrix view-matrix model-matrix]
  (let [mv-matrix (.create wgpu-matrix/mat4)]
    (.multiply wgpu-matrix/mat4 view-matrix model-matrix mv-matrix)
    (.multiply wgpu-matrix/mat4 projection-matrix mv-matrix)))

(defn- camera->projection-matrix
  "Convert camera data to projection matrix (CALCULATION - pure).

   Args:
     camera - Camera data map
     aspect - Aspect ratio (width/height)

   Returns: Float32Array (projection matrix)"
  [{:keys [fov near far]} aspect]
  (.perspective wgpu-matrix/mat4 fov aspect near far))

(defn- camera->view-matrix
  "Convert camera position to view matrix (CALCULATION - pure).

   Args:
     camera - Camera data map

   Returns: Float32Array (view matrix)"
  [{:keys [position]}]
  (let [view-matrix (.identity wgpu-matrix/mat4)
        [x y z] position]
    (.translate wgpu-matrix/mat4 view-matrix
                (.fromValues wgpu-matrix/vec3 x y z)
                view-matrix)))

;;
;; Pipeline Creation (Actions - GPU I/O)
;;

(defn- create-pipeline!
  "Create render pipeline for an object (ACTION - GPU I/O).

   Args:
     device          - WebGPU device
     canvas-format   - Canvas format (from GPU config)
     vertex-code     - WGSL vertex shader code (string)
     fragment-code   - WGSL fragment shader code (string)
     vertex-layout   - Optional vertex buffer layout config

   Returns: Map with {:pipeline GPURenderPipeline
                      :layout   GPUPipelineLayout}

   Side effects: Compiles shaders, creates GPU pipeline"
  [device canvas-format vertex-code fragment-code & {:keys [vertex-layout]
                                                     :or {vertex-layout
                                                          {:arrayStride 40  ; 10 floats (default cube kit)
                                                           :attributes [{:shaderLocation 0 :offset 0 :format "float32x4"}
                                                                        {:shaderLocation 1 :offset 32 :format "float32x2"}
                                                                        {:shaderLocation 2 :offset 32 :format "float32x2"}]}}}]
  (let [vertex-module (gpu/create-shader-module device {:code vertex-code})
        fragment-module (gpu/create-shader-module device {:code fragment-code})
        pipeline (gpu/create-render-pipeline
                  device
                  (clj->js {:label "Scene object pipeline"
                            :layout "auto"
                            :vertex {:module vertex-module
                                     :entryPoint "main"
                                     :buffers [vertex-layout]}
                            :fragment {:module fragment-module
                                       :entryPoint "main"
                                       :targets [{:format canvas-format}]}
                            :primitive {:topology "triangle-list"
                                        :cullMode "back"}
                            :depthStencil {:depthWriteEnabled true
                                           :depthCompare "less"
                                           :format "depth24plus"}}))]
    {:pipeline pipeline
     :layout (gpu/create-bind-group-layout pipeline 0)}))

;;
;; Renderer Creation (Actions - Setup)
;;

(defrecord Renderer [device queue canvas-format context
                     vertex-buffer uniform-buffer depth-texture
                     pipeline bind-group projection-matrix])

(defn create-renderer!
  "Create a renderer for a canvas (ACTION - initializes GPU resources).

   Args:
     canvas         - Canvas DOM element
     vertex-code    - Compiled WGSL vertex shader code
     fragment-code  - Compiled WGSL fragment shader code
     vertex-data    - Geometry vertex data

   Returns: Renderer record

   Side effects: Allocates GPU resources (buffers, textures, pipeline)

   Example:
     (create-renderer! canvas vertex-shader fragment-shader cube-vertices)"
  [canvas vertex-code fragment-code vertex-data]
  (let [context (.getContext canvas "webgpu")
        {:keys [device canvas-format queue]} @gpu/!gpu-config
        _ (gpu/configure-canvas context {:device device
                                         :format canvas-format
                                         :alphaMode "premultiplied"})
        ;; Create GPU resources
        vertex-buffer (create-vertex-buffer! device vertex-data)
        uniform-buffer (create-uniform-buffer! device)
        depth-texture (create-depth-texture! device canvas)
        {:keys [pipeline layout]} (create-pipeline! device canvas-format vertex-code fragment-code)
        ;; Bind group for uniforms
        bind-group (gpu/create-bind-group
                    device
                    (clj->js {:layout layout
                              :entries [{:binding 0
                                         :resource {:buffer uniform-buffer}}]}))
        ;; Static projection matrix
        aspect (/ (.-width canvas) (.-height canvas))
        projection-matrix (.perspective wgpu-matrix/mat4
                                        (* 2 (/ js/Math.PI 5))
                                        aspect 1 100.0)]
    (->Renderer device queue canvas-format context
                vertex-buffer uniform-buffer depth-texture
                pipeline bind-group projection-matrix)))

;;
;; Frame Rendering (Actions - I/O)
;;

(defn render-frame!
  "Render a single frame (ACTION - GPU I/O).

   Args:
     renderer  - Renderer record
     time      - Current time in seconds
     rotor-fn  - Function (time -> rotor) for object transform

   Returns: nil

   Side effects: Submits GPU commands, updates screen"
  [{:keys [device queue context
           vertex-buffer uniform-buffer depth-texture
           pipeline bind-group projection-matrix]} time rotor-fn]
  (let [;; Compute transforms
        view-matrix (let [vm (.identity wgpu-matrix/mat4)]
                      (.translate wgpu-matrix/mat4 vm
                                  (.fromValues wgpu-matrix/vec3 0 0 -4)
                                  vm))
        rotor (rotor-fn time)
        rotation-matrix (rotor->mat4 rotor)
        _ (.multiply wgpu-matrix/mat4 view-matrix rotation-matrix view-matrix)
        mvp-matrix (compute-mvp-matrix projection-matrix view-matrix
                                       (.identity wgpu-matrix/mat4))]
    ;; Update uniform buffer
    (gpu/write-buffer device uniform-buffer 0 mvp-matrix 0)
    ;; Render pass
    (let [command-encoder (gpu/create-command-encoder device)
          render-pass-descriptor (clj->js
                                  {:colorAttachments
                                   [{:view (gpu/create-view context)
                                     :clearValue {:r 0.5 :g 0.5 :b 0.5 :a 1.0}
                                     :loadOp "clear"
                                     :storeOp "store"}]
                                   :depthStencilAttachment
                                   {:view (gpu/create-texture-view depth-texture)
                                    :depthClearValue 1.0
                                    :depthLoadOp "clear"
                                    :depthStoreOp "store"}})
          pass-encoder (gpu/begin-render-pass command-encoder
                                              render-pass-descriptor)]
      (doto pass-encoder
        (gpu/set-pipeline pipeline)
        (.setBindGroup 0 bind-group)
        (.setVertexBuffer 0 vertex-buffer)
        (gpu/draw 36)  ; Cube has 36 vertices
        (.end))
      (.submit queue (clj->js [(.finish command-encoder)])))))

;;
;; Animation Loop (Actions - Scheduling)
;;

(defn start-animation-loop!
  "Start animation loop for a renderer (ACTION - schedules GPU work).

   Args:
     renderer   - Renderer record
     rotor-fn   - Function (time -> rotor) for object transform
     !render-id - Atom with current render ID (for cancellation)
     render-id  - Current render ID value

   Returns: nil

   Side effects: Schedules requestAnimationFrame callbacks

   Example:
     (start-animation-loop! renderer my-rotor-fn !render-id @!render-id)"
  [renderer rotor-fn !render-id render-id]
  (letfn [(frame []
            (when (= render-id @!render-id)
              (let [time (/ (.now js/Date) 1000)]
                (render-frame! renderer time rotor-fn)
                (js/requestAnimationFrame frame))))]
    (js/requestAnimationFrame frame)))

;;
;; Multi-Object Rendering
;;

(defrecord MultiObjectRenderer [device queue canvas-format context
                                vertex-buffer uniform-buffer depth-texture
                                pipeline bind-groups projection-matrix
                                object-count])

(defn create-multi-object-renderer!
  "Create a renderer for multiple objects (ACTION - initializes GPU resources).

   Args:
     canvas         - Canvas DOM element
     vertex-code    - Compiled WGSL vertex shader code
     fragment-code  - Compiled WGSL fragment shader code
     vertex-data    - Geometry vertex data
     object-count   - Number of objects to render

   Returns: MultiObjectRenderer record

   Side effects: Allocates GPU resources (buffers, textures, pipeline)

   Example:
     (create-multi-object-renderer! canvas vertex-shader fragment-shader cube-vertices 2)"
  [canvas vertex-code fragment-code vertex-data object-count]
  (let [context (.getContext canvas "webgpu")
        {:keys [device canvas-format queue]} @gpu/!gpu-config
        _ (gpu/configure-canvas context {:device device
                                         :format canvas-format
                                         :alphaMode "premultiplied"})
        ;; Create GPU resources
        vertex-buffer (create-vertex-buffer! device vertex-data)
        ;; Uniform buffer: space for N MVP matrices
        matrix-size (* 4 16)  ; 4x4 matrix = 64 bytes
        offset 256  ; WebGPU uniform buffer offset alignment
        uniform-buffer-size (* object-count offset)
        uniform-buffer (create-uniform-buffer! device :size uniform-buffer-size)
        depth-texture (create-depth-texture! device canvas)
        {:keys [pipeline layout]} (create-pipeline! device canvas-format vertex-code fragment-code)
        ;; Create bind groups for each object (pointing to different buffer offsets)
        bind-groups (vec (for [i (range object-count)]
                           (gpu/create-bind-group
                            device
                            (clj->js {:layout layout
                                      :entries [{:binding 0
                                                 :resource {:buffer uniform-buffer
                                                            :offset (* i offset)
                                                            :size matrix-size}}]}))))
        ;; Static projection matrix
        aspect (/ (.-width canvas) (.-height canvas))
        projection-matrix (.perspective wgpu-matrix/mat4
                                        (* 2 (/ js/Math.PI 5))
                                        aspect 1 100.0)]
    (->MultiObjectRenderer device queue canvas-format context
                           vertex-buffer uniform-buffer depth-texture
                           pipeline bind-groups projection-matrix
                           object-count)))

(defn render-multi-object-frame!
  "Render a frame with multiple objects (ACTION - GPU I/O).

   Args:
     renderer       - MultiObjectRenderer record
     time           - Current time in seconds
     object-configs - Vector of {:position [x y z] :rotor-fn (fn [time] -> rotor)}

   Returns: nil

   Side effects: Submits GPU commands, updates screen"
  [{:keys [device queue context
           vertex-buffer uniform-buffer depth-texture
           pipeline bind-groups projection-matrix
           object-count]} time object-configs]
  (let [;; Compute transforms for each object
        view-matrix (let [vm (.identity wgpu-matrix/mat4)]
                      (.translate wgpu-matrix/mat4 vm
                                  (.fromValues wgpu-matrix/vec3 0 0 -7)
                                  vm))

        ;; Compute MVP for each object
        offset 256
        mvp-matrices (for [{:keys [position rotor-fn]} object-configs]
                       (let [[x y z] position
                             model-matrix (.translation wgpu-matrix/mat4
                                                        (.fromValues wgpu-matrix/vec3 x y z))
                             rotor (rotor-fn time)
                             rotation-matrix (rotor->mat4 rotor)
                             _ (.multiply wgpu-matrix/mat4 model-matrix rotation-matrix model-matrix)
                             mv-matrix (.create wgpu-matrix/mat4)
                             _ (.multiply wgpu-matrix/mat4 view-matrix model-matrix mv-matrix)]
                         (.multiply wgpu-matrix/mat4 projection-matrix mv-matrix)))]

    ;; Update uniform buffer for all objects
    (doseq [[i mvp-matrix] (map-indexed vector mvp-matrices)]
      (gpu/write-buffer device uniform-buffer (* i offset) mvp-matrix 0))

    ;; Render pass
    (let [command-encoder (gpu/create-command-encoder device)
          render-pass-descriptor (clj->js
                                  {:colorAttachments
                                   [{:view (gpu/create-view context)
                                     :clearValue {:r 0.5 :g 0.5 :b 0.5 :a 1.0}
                                     :loadOp "clear"
                                     :storeOp "store"}]
                                   :depthStencilAttachment
                                   {:view (gpu/create-texture-view depth-texture)
                                    :depthClearValue 1.0
                                    :depthLoadOp "clear"
                                    :depthStoreOp "store"}})
          pass-encoder (gpu/begin-render-pass command-encoder
                                              render-pass-descriptor)]
      ;; Set pipeline and vertex buffer once
      (doto pass-encoder
        (gpu/set-pipeline pipeline)
        (.setVertexBuffer 0 vertex-buffer))

      ;; Draw each object with its own bind group
      (doseq [i (range object-count)]
        (doto pass-encoder
          (.setBindGroup 0 (nth bind-groups i))
          (gpu/draw 36)))  ; Cube has 36 vertices

      (.end pass-encoder)
      (.submit queue (clj->js [(.finish command-encoder)])))))

(defn start-multi-object-loop!
  "Start animation loop for multi-object renderer (ACTION - schedules GPU work).

   Args:
     renderer       - MultiObjectRenderer record
     object-configs - Vector of {:position [x y z] :rotor-fn (fn [time] -> rotor)}
     !render-id     - Atom with current render ID (for cancellation)
     render-id      - Current render ID value

   Returns: nil

   Side effects: Schedules requestAnimationFrame callbacks

   Example:
     (start-multi-object-loop! renderer
                               [{:position [-2 0 0] :rotor-fn rotor-1-fn}
                                {:position [2 0 0] :rotor-fn rotor-2-fn}]
                               !render-id
                               @!render-id)"
  [renderer object-configs !render-id render-id]
  (letfn [(frame []
            (when (= render-id @!render-id)
              (let [time (/ (.now js/Date) 1000)]
                (render-multi-object-frame! renderer time object-configs)
                (js/requestAnimationFrame frame))))]
    (js/requestAnimationFrame frame)))

;;
;; Simple Renderer (No Uniforms, No Depth)
;;

(defrecord SimpleRenderer [device queue canvas-format context pipeline msaa-texture msaa-count])

(defn create-simple-renderer!
  "Create a simple renderer (no uniforms, no depth testing).

   For basic samples like triangles that don't need transforms or depth.

   Args:
     canvas         - Canvas DOM element
     vertex-code    - WGSL vertex shader code
     fragment-code  - WGSL fragment shader code
     & options      - Optional {:msaa-count N} for MSAA

   Returns: SimpleRenderer record

   Side effects: Allocates GPU resources

   Example:
     (create-simple-renderer! canvas vertex-shader fragment-shader)
     (create-simple-renderer! canvas vertex-shader fragment-shader :msaa-count 4)"
  [canvas vertex-code fragment-code & {:keys [msaa-count] :or {msaa-count 1}}]
  (let [context (.getContext canvas "webgpu")
        {:keys [device canvas-format queue]} @gpu/!gpu-config
        _ (gpu/configure-canvas context {:device device
                                         :format canvas-format
                                         :alphaMode "premultiplied"})
        ;; Create MSAA texture if needed
        msaa-texture (when (> msaa-count 1)
                       (gpu/create-texture device msaa-count canvas-format canvas))
        ;; Create simple pipeline (no vertex buffers, no depth, no uniforms)
        vertex-module (gpu/create-shader-module device {:code vertex-code})
        fragment-module (gpu/create-shader-module device {:code fragment-code})
        pipeline-config (cond-> {:label "Simple pipeline"
                                 :layout "auto"
                                 :vertex {:module vertex-module
                                          :entryPoint "main"}
                                 :fragment {:module fragment-module
                                            :entryPoint "main"
                                            :targets [{:format canvas-format}]}
                                 :primitive {:topology "triangle-list"}}
                          (> msaa-count 1) (assoc :multisample {:count msaa-count}))
        pipeline (gpu/create-render-pipeline device (clj->js pipeline-config))]
    (->SimpleRenderer device queue canvas-format context pipeline msaa-texture msaa-count)))

(defn render-simple-frame!
  "Render a single frame with simple renderer (ACTION - GPU I/O).

   Args:
     renderer    - SimpleRenderer record
     vertex-count - Number of vertices to draw (e.g., 3 for triangle)

   Returns: nil

   Side effects: Submits GPU commands"
  [{:keys [device queue context pipeline msaa-texture msaa-count]} vertex-count]
  (let [command-encoder (gpu/create-command-encoder device)
        ;; Configure color attachment based on MSAA
        color-attachment (if (and msaa-texture (> msaa-count 1))
                           ;; MSAA: render to MSAA texture, resolve to canvas
                           {:view (gpu/create-texture-view msaa-texture)
                            :resolveTarget (gpu/create-view context)
                            :clearValue {:r 0.0 :g 0.0 :b 0.0 :a 1.0}
                            :loadOp "clear"
                            :storeOp "discard"}  ; Discard MSAA, keep resolved
                           ;; No MSAA: render directly to canvas
                           {:view (gpu/create-view context)
                            :clearValue {:r 0.0 :g 0.0 :b 0.0 :a 1.0}
                            :loadOp "clear"
                            :storeOp "store"})
        render-pass-descriptor (clj->js {:colorAttachments [color-attachment]})
        pass-encoder (gpu/begin-render-pass command-encoder
                                            render-pass-descriptor)]
    (doto pass-encoder
      (gpu/set-pipeline pipeline)
      (gpu/draw vertex-count)
      (.end))
    (.submit queue (clj->js [(.finish command-encoder)]))))

;;
;; Texture Support
;;

(defn load-texture!
  "Load image from URL into GPU texture (ACTION - async I/O).

   Args:
     device - WebGPU device
     queue  - WebGPU queue
     url    - Image URL to load

   Returns: Promise<GPUTexture>

   Side effects: Fetches image, allocates GPU texture memory"
  [device queue url]
  (js/Promise.
   (fn [resolve reject]
     (-> (js/fetch url)
         (.then (fn [response]
                  (if (.-ok response)
                    (.blob response)
                    (reject (js/Error. (str "Fetch failed: " (.-status response)))))))
         (.then js/createImageBitmap)
         (.then (fn [image-bitmap]
                  (let [;; Create texture using gpu API
                        texture (gpu/create-texture-2d
                                 device
                                 {:size [(.-width image-bitmap)
                                         (.-height image-bitmap)
                                         1]
                                  :format "rgba8unorm"
                                  :usage (bit-or js/GPUTextureUsage.TEXTURE_BINDING
                                                 js/GPUTextureUsage.COPY_DST
                                                 js/GPUTextureUsage.RENDER_ATTACHMENT)})]
                    ;; Copy image to texture using gpu API
                    (gpu/copy-external-image-to-texture
                     queue
                     {:source image-bitmap}
                     {:texture texture}
                     [(.-width image-bitmap) (.-height image-bitmap)])
                    (resolve texture))))
         (.catch reject)))))
;;
;; Textured Renderer (With Textures + Uniforms)
;;

(defrecord TexturedRenderer [device queue canvas-format context
                             vertex-buffer uniform-buffer depth-texture
                             pipeline bind-group projection-matrix])

(defn create-textured-renderer!
  "Create a renderer with texture support (ACTION - initializes GPU resources).

   Args:
     canvas         - Canvas DOM element
     vertex-code    - WGSL vertex shader code
     fragment-code  - WGSL fragment shader code
     vertex-data    - Geometry vertex data
     texture        - GPUTexture (from load-texture!)

   Returns: TexturedRenderer record

   Side effects: Allocates GPU resources

   Example:
     (let [texture (js-await (load-texture! device queue \"image.png\"))]
       (create-textured-renderer! canvas vertex fragment geometry texture))"
  [canvas vertex-code fragment-code vertex-data texture]
  (let [context (.getContext canvas "webgpu")
        {:keys [device canvas-format queue]} @gpu/!gpu-config
        _ (gpu/configure-canvas context {:device device
                                         :format canvas-format
                                         :alphaMode "premultiplied"})
        ;; Create GPU resources
        vertex-buffer (create-vertex-buffer! device vertex-data)
        uniform-buffer (create-uniform-buffer! device)
        depth-texture (create-depth-texture! device canvas)
        ;; Textured cube vertex layout: position (vec4) + uv (vec2) = 6 floats * 4 bytes = 24 bytes
        vertex-layout {:arrayStride 24
                       :attributes [{:shaderLocation 0 :offset 0 :format "float32x4"}   ; position
                                    {:shaderLocation 1 :offset 16 :format "float32x2"}]}  ; uv
        {:keys [pipeline layout]} (create-pipeline! device canvas-format vertex-code fragment-code
                                                    :vertex-layout vertex-layout)

        ;; Create sampler
        sampler (.createSampler device #js {:magFilter "linear"
                                            :minFilter "linear"})

        ;; Bind group 0: Uniforms + Texture + Sampler (all in same group)
        ;; Matches WGSL: @group(0) @binding(0) uniforms
        ;;               @group(0) @binding(1) sampler
        ;;               @group(0) @binding(2) texture
        combined-bind-group (gpu/create-bind-group
                             device
                             (clj->js {:layout (gpu/create-bind-group-layout pipeline 0)
                                       :entries [{:binding 0
                                                  :resource {:buffer uniform-buffer}}
                                                 {:binding 1
                                                  :resource sampler}
                                                 {:binding 2
                                                  :resource (gpu/create-texture-view texture)}]}))

        ;; Static projection matrix
        aspect (/ (.-width canvas) (.-height canvas))
        projection-matrix (.perspective wgpu-matrix/mat4
                                        (* 2 (/ js/Math.PI 5))
                                        aspect 1 100.0)]
    (->TexturedRenderer device queue canvas-format context
                        vertex-buffer uniform-buffer depth-texture
                        pipeline combined-bind-group projection-matrix)))

(defn render-textured-frame!
  "Render a frame with textured renderer (ACTION - GPU I/O).

   Args:
     renderer  - TexturedRenderer record
     time      - Current time in seconds
     rotor-fn  - Function (time -> rotor) for object transform

   Returns: nil

   Side effects: Submits GPU commands"
  [{:keys [device queue context
           vertex-buffer uniform-buffer depth-texture
           pipeline bind-group projection-matrix]} time rotor-fn]
  (let [;; Compute transforms
        view-matrix (let [vm (.identity wgpu-matrix/mat4)]
                      (.translate wgpu-matrix/mat4 vm
                                  (.fromValues wgpu-matrix/vec3 0 0 -4)
                                  vm))
        rotor (rotor-fn time)
        rotation-matrix (rotor->mat4 rotor)
        _ (.multiply wgpu-matrix/mat4 view-matrix rotation-matrix view-matrix)
        mvp-matrix (compute-mvp-matrix projection-matrix view-matrix
                                       (.identity wgpu-matrix/mat4))]
    ;; Update uniform buffer
    (gpu/write-buffer device uniform-buffer 0 mvp-matrix 0)
    ;; Render pass
    (let [command-encoder (gpu/create-command-encoder device)
          render-pass-descriptor (clj->js
                                  {:colorAttachments
                                   [{:view (gpu/create-view context)
                                     :clearValue {:r 0.5 :g 0.5 :b 0.5 :a 1.0}
                                     :loadOp "clear"
                                     :storeOp "store"}]
                                   :depthStencilAttachment
                                   {:view (gpu/create-texture-view depth-texture)
                                    :depthClearValue 1.0
                                    :depthLoadOp "clear"
                                    :depthStoreOp "store"}})
          pass-encoder (gpu/begin-render-pass command-encoder
                                              render-pass-descriptor)]
      (doto pass-encoder
        (gpu/set-pipeline pipeline)
        (.setBindGroup 0 bind-group)  ; Uniforms + sampler + texture all in group 0
        (.setVertexBuffer 0 vertex-buffer)
        (gpu/draw 36)
        (.end))
      (.submit queue (clj->js [(.finish command-encoder)])))))

(defn start-textured-loop!
  "Start animation loop for textured renderer (ACTION - schedules GPU work).

   Args:
     renderer   - TexturedRenderer record
     rotor-fn   - Function (time -> rotor) for object transform
     !render-id - Atom with current render ID (for cancellation)
     render-id  - Current render ID value

   Returns: nil

   Side effects: Schedules requestAnimationFrame callbacks"
  [renderer rotor-fn !render-id render-id]
  (letfn [(frame []
            (when (= render-id @!render-id)
              (let [time (/ (.now js/Date) 1000)]
                (render-textured-frame! renderer time rotor-fn)
                (js/requestAnimationFrame frame))))]
    (js/requestAnimationFrame frame)))

;;
;; Instanced Renderer (GPU Instancing)
;;

(defrecord InstancedRenderer [device queue canvas-format context
                              vertex-buffer uniform-buffer depth-texture
                              pipeline bind-group projection-matrix
                              instance-count])

(defn create-instanced-renderer!
  "Create a renderer with GPU instancing (ACTION - initializes GPU resources).

   GPU instancing renders many copies of the same geometry in a single draw call.
   Uses @builtin(instance_index) in shader to index into uniform array.

   Args:
     canvas         - Canvas DOM element
     vertex-code    - WGSL vertex shader (must use instance_index)
     fragment-code  - WGSL fragment shader
     vertex-data    - Geometry vertex data
     instance-count - Number of instances to render

   Returns: InstancedRenderer record

   Side effects: Allocates GPU resources

   Example:
     (create-instanced-renderer! canvas vertex fragment geometry 16)"
  [canvas vertex-code fragment-code vertex-data instance-count]
  (let [context (.getContext canvas "webgpu")
        {:keys [device canvas-format queue]} @gpu/!gpu-config
        _ (gpu/configure-canvas context {:device device
                                         :format canvas-format
                                         :alphaMode "premultiplied"})
        ;; Create GPU resources
        vertex-buffer (create-vertex-buffer! device vertex-data)
        ;; Uniform buffer: array of matrices for all instances
        matrix-size (* 4 16)  ; 4x4 matrix = 64 bytes
        uniform-buffer-size (* instance-count matrix-size)
        uniform-buffer (create-uniform-buffer! device :size uniform-buffer-size)
        depth-texture (create-depth-texture! device canvas)
        {:keys [pipeline layout]} (create-pipeline! device canvas-format vertex-code fragment-code)
        ;; Single bind group with array of matrices
        bind-group (gpu/create-bind-group
                    device
                    (clj->js {:layout layout
                              :entries [{:binding 0
                                         :resource {:buffer uniform-buffer}}]}))
        ;; Static projection matrix
        aspect (/ (.-width canvas) (.-height canvas))
        projection-matrix (.perspective wgpu-matrix/mat4
                                        (* 2 (/ js/Math.PI 5))
                                        aspect 1 100.0)]
    (->InstancedRenderer device queue canvas-format context
                         vertex-buffer uniform-buffer depth-texture
                         pipeline bind-group projection-matrix
                         instance-count)))

(defn render-instanced-frame!
  "Render a frame with instanced renderer (ACTION - GPU I/O).

   Args:
     renderer         - InstancedRenderer record
     time             - Current time in seconds
     instance-configs - Vector of {:position [x y z] :rotor-fn (fn [time] -> rotor)}

   Returns: nil

   Side effects: Submits GPU commands, single draw call for all instances"
  [{:keys [device queue context
           vertex-buffer uniform-buffer depth-texture
           pipeline bind-group projection-matrix
           instance-count]} time instance-configs]
  (let [;; Compute view matrix
        view-matrix (let [vm (.identity wgpu-matrix/mat4)]
                      (.translate wgpu-matrix/mat4 vm
                                  (.fromValues wgpu-matrix/vec3 0 0 -12)
                                  vm))
        ;; Compute MVP for each instance
        matrix-size (* 4 16)
        mvp-matrices (for [{:keys [position rotor-fn]} instance-configs]
                       (let [[x y z] position
                             model-matrix (.translation wgpu-matrix/mat4
                                                        (.fromValues wgpu-matrix/vec3 x y z))
                             rotor (rotor-fn time)
                             rotation-matrix (rotor->mat4 rotor)
                             _ (.multiply wgpu-matrix/mat4 model-matrix rotation-matrix model-matrix)
                             mv-matrix (.create wgpu-matrix/mat4)
                             _ (.multiply wgpu-matrix/mat4 view-matrix model-matrix mv-matrix)]
                         (.multiply wgpu-matrix/mat4 projection-matrix mv-matrix)))]

    ;; Update uniform buffer with all instance matrices
    (doseq [[i mvp-matrix] (map-indexed vector mvp-matrices)]
      (gpu/write-buffer device uniform-buffer (* i matrix-size) mvp-matrix 0))

    ;; Render pass - single draw call for all instances!
    (let [command-encoder (gpu/create-command-encoder device)
          render-pass-descriptor (clj->js
                                  {:colorAttachments
                                   [{:view (gpu/create-view context)
                                     :clearValue {:r 0.5 :g 0.5 :b 0.5 :a 1.0}
                                     :loadOp "clear"
                                     :storeOp "store"}]
                                   :depthStencilAttachment
                                   {:view (gpu/create-texture-view depth-texture)
                                    :depthClearValue 1.0
                                    :depthLoadOp "clear"
                                    :depthStoreOp "store"}})
          pass-encoder (gpu/begin-render-pass command-encoder
                                              render-pass-descriptor)]
      (doto pass-encoder
        (gpu/set-pipeline pipeline)
        (.setBindGroup 0 bind-group)
        (.setVertexBuffer 0 vertex-buffer)
        (.draw 36 instance-count))  ; Single draw call with instanceCount!
      (.end pass-encoder)
      (.submit queue (clj->js [(.finish command-encoder)])))))

(defn start-instanced-loop!
  "Start animation loop for instanced renderer (ACTION - schedules GPU work).

   Args:
     renderer         - InstancedRenderer record
     instance-configs - Vector of {:position [x y z] :rotor-fn (fn [time] -> rotor)}
     !render-id       - Atom with current render ID
     render-id        - Current render ID value

   Returns: nil

   Side effects: Schedules requestAnimationFrame callbacks"
  [renderer instance-configs !render-id render-id]
  (letfn [(frame []
            (when (= render-id @!render-id)
              (let [time (/ (.now js/Date) 1000)]
                (render-instanced-frame! renderer time instance-configs)
                (js/requestAnimationFrame frame))))]
    (js/requestAnimationFrame frame)))

;;
;; Cubemap Texture Support
;;

(defn load-cubemap!
  "Load cubemap texture from 6 face URLs (ACTION - async I/O).

   Args:
     device - WebGPU device
     queue  - WebGPU queue  
     urls   - Vector of 6 URLs [+X, -X, +Y, -Y, +Z, -Z]

   Returns: Promise<GPUTexture>

   Side effects: Fetches 6 images, allocates cubemap texture"
  [device queue urls]
  (js/Promise.
   (fn [resolve reject]
     (-> (js/Promise.all
          (clj->js (map (fn [url]
                          (-> (js/fetch url #js {:mode "cors"})
                              (.then #(.blob %))
                              (.then js/createImageBitmap)))
                        urls)))
         (.then (fn [image-bitmaps]
                  (let [size (.-width (first image-bitmaps))
                        ;; Create cubemap texture
                        cubemap (gpu/create-texture-2d
                                 device
                                 {:dimension "2d"
                                  :size [size size 6]  ; 6 faces
                                  :format "rgba8unorm"
                                  :usage (bit-or js/GPUTextureUsage.TEXTURE_BINDING
                                                 js/GPUTextureUsage.COPY_DST
                                                 js/GPUTextureUsage.RENDER_ATTACHMENT)})]
                    ;; Copy each face to the cubemap
                    (doseq [[i bitmap] (map-indexed vector image-bitmaps)]
                      (gpu/copy-external-image-to-texture
                       queue
                       {:source bitmap}
                       {:texture cubemap :origin [0 0 i]}
                       [(.-width bitmap) (.-height bitmap)]))
                    (resolve cubemap))))
         (.catch reject)))))

(defrecord CubemapRenderer [device queue canvas-format context
                            vertex-buffer uniform-buffer depth-texture
                            pipeline bind-group projection-matrix])

(defn create-cubemap-renderer!
  "Create renderer with cubemap texture (ACTION - initializes GPU resources).

   Args:
     canvas         - Canvas DOM element
     vertex-code    - WGSL vertex shader
     fragment-code  - WGSL fragment shader (must sample texture_cube)
     vertex-data    - Geometry vertex data
     cubemap        - GPUTexture with dimension=cube

   Returns: CubemapRenderer record

   Side effects: Allocates GPU resources"
  [canvas vertex-code fragment-code vertex-data cubemap]
  (let [context (.getContext canvas "webgpu")
        {:keys [device canvas-format queue]} @gpu/!gpu-config
        _ (gpu/configure-canvas context {:device device
                                         :format canvas-format
                                         :alphaMode "premultiplied"})
        vertex-buffer (create-vertex-buffer! device vertex-data)
        uniform-buffer (create-uniform-buffer! device)
        depth-texture (create-depth-texture! device canvas)
        {:keys [pipeline layout]} (create-pipeline! device canvas-format vertex-code fragment-code)

        ;; Create sampler
        sampler (.createSampler device #js {:magFilter "linear"
                                            :minFilter "linear"})

        ;; Create cubemap view
        cubemap-view (gpu/create-texture-view-with-descriptor
                      cubemap
                      {:dimension "cube"})

        ;; Bind group: uniforms + sampler + cubemap
        bind-group (gpu/create-bind-group
                    device
                    (clj->js {:layout layout
                              :entries [{:binding 0
                                         :resource {:buffer uniform-buffer}}
                                        {:binding 1
                                         :resource sampler}
                                        {:binding 2
                                         :resource cubemap-view}]}))

        aspect (/ (.-width canvas) (.-height canvas))
        projection-matrix (.perspective wgpu-matrix/mat4
                                        (* 2 (/ js/Math.PI 5))
                                        aspect 1 100.0)]
    (->CubemapRenderer device queue canvas-format context
                       vertex-buffer uniform-buffer depth-texture
                       pipeline bind-group projection-matrix)))

(defn render-cubemap-frame!
  "Render frame with cubemap (ACTION - GPU I/O)."
  [{:keys [device queue context
           vertex-buffer uniform-buffer depth-texture
           pipeline bind-group projection-matrix]} time rotor-fn]
  (let [view-matrix (let [vm (.identity wgpu-matrix/mat4)]
                      (.translate wgpu-matrix/mat4 vm
                                  (.fromValues wgpu-matrix/vec3 0 0 -4)
                                  vm))
        rotor (rotor-fn time)
        rotation-matrix (rotor->mat4 rotor)
        _ (.multiply wgpu-matrix/mat4 view-matrix rotation-matrix view-matrix)
        mvp-matrix (compute-mvp-matrix projection-matrix view-matrix
                                       (.identity wgpu-matrix/mat4))]
    (gpu/write-buffer device uniform-buffer 0 mvp-matrix 0)
    (let [command-encoder (gpu/create-command-encoder device)
          render-pass-descriptor (clj->js
                                  {:colorAttachments
                                   [{:view (gpu/create-view context)
                                     :clearValue {:r 0.5 :g 0.5 :b 0.5 :a 1.0}
                                     :loadOp "clear"
                                     :storeOp "store"}]
                                   :depthStencilAttachment
                                   {:view (gpu/create-texture-view depth-texture)
                                    :depthClearValue 1.0
                                    :depthLoadOp "clear"
                                    :depthStoreOp "store"}})
          pass-encoder (gpu/begin-render-pass command-encoder
                                              render-pass-descriptor)]
      (doto pass-encoder
        (gpu/set-pipeline pipeline)
        (.setBindGroup 0 bind-group)
        (.setVertexBuffer 0 vertex-buffer)
        (gpu/draw 36)
        (.end))
      (.submit queue (clj->js [(.finish command-encoder)])))))

(defn start-cubemap-loop!
  "Start animation loop for cubemap renderer."
  [renderer rotor-fn !render-id render-id]
  (letfn [(frame []
            (when (= render-id @!render-id)
              (let [time (/ (.now js/Date) 1000)]
                (render-cubemap-frame! renderer time rotor-fn)
                (js/requestAnimationFrame frame))))]
    (js/requestAnimationFrame frame)))

;;
;; Feedback Renderer (Render-to-Texture)
;;

(defrecord FeedbackRenderer [device queue canvas-format context
                             vertex-buffer uniform-buffer depth-texture
                             feedback-texture pipeline bind-group projection-matrix])

(defn create-feedback-renderer!
  "Create renderer with render-to-texture feedback (ACTION - initializes GPU resources).

   Feedback renderer renders to canvas, then copies result to a texture that can be
   sampled in the next frame, creating recursive/fractal effects.

   Args:
     canvas         - Canvas DOM element
     vertex-code    - WGSL vertex shader
     fragment-code  - WGSL fragment shader (samples previous frame)
     vertex-data    - Geometry vertex data

   Returns: FeedbackRenderer record

   Side effects: Allocates GPU resources including feedback texture"
  [canvas vertex-code fragment-code vertex-data]
  (let [context (.getContext canvas "webgpu")
        {:keys [device canvas-format queue]} @gpu/!gpu-config
        _ (gpu/configure-canvas context {:device device
                                         :format canvas-format
                                         :alphaMode "premultiplied"
                                         :usage (bit-or js/GPUTextureUsage.RENDER_ATTACHMENT
                                                        js/GPUTextureUsage.COPY_SRC)})
        vertex-buffer (create-vertex-buffer! device vertex-data)
        uniform-buffer (create-uniform-buffer! device)
        depth-texture (create-depth-texture! device canvas)
        ;; Feedback texture (stores previous frame)
        feedback-texture (gpu/create-texture-2d
                          device
                          {:size [(.-width canvas) (.-height canvas)]
                           :format canvas-format
                           :usage (bit-or js/GPUTextureUsage.TEXTURE_BINDING
                                          js/GPUTextureUsage.COPY_DST)})
        {:keys [pipeline layout]} (create-pipeline! device canvas-format vertex-code fragment-code)

        ;; Create sampler
        sampler (.createSampler device #js {:magFilter "linear"
                                            :minFilter "linear"})

        ;; Bind group: uniforms + sampler + feedback texture
        bind-group (gpu/create-bind-group
                    device
                    (clj->js {:layout layout
                              :entries [{:binding 0
                                         :resource {:buffer uniform-buffer}}
                                        {:binding 1
                                         :resource sampler}
                                        {:binding 2
                                         :resource (gpu/create-texture-view feedback-texture)}]}))

        aspect (/ (.-width canvas) (.-height canvas))
        projection-matrix (.perspective wgpu-matrix/mat4
                                        (* 2 (/ js/Math.PI 5))
                                        aspect 1 100.0)]
    (->FeedbackRenderer device queue canvas-format context
                        vertex-buffer uniform-buffer depth-texture
                        feedback-texture pipeline bind-group projection-matrix)))

(defn render-feedback-frame!
  "Render frame with feedback (ACTION - GPU I/O).
   
   Renders to canvas, then copies result to feedback texture for next frame."
  [{:keys [device queue context
           vertex-buffer uniform-buffer depth-texture feedback-texture
           pipeline bind-group projection-matrix]} time rotor-fn]
  (let [view-matrix (let [vm (.identity wgpu-matrix/mat4)]
                      (.translate wgpu-matrix/mat4 vm
                                  (.fromValues wgpu-matrix/vec3 0 0 -4)
                                  vm))
        rotor (rotor-fn time)
        rotation-matrix (rotor->mat4 rotor)
        _ (.multiply wgpu-matrix/mat4 view-matrix rotation-matrix view-matrix)
        mvp-matrix (compute-mvp-matrix projection-matrix view-matrix
                                       (.identity wgpu-matrix/mat4))
        swap-chain-texture (gpu/get-texture context)
        command-encoder (gpu/create-command-encoder device)]

    ;; Update uniform buffer
    (gpu/write-buffer device uniform-buffer 0 mvp-matrix 0)

    ;; Render pass
    (let [render-pass-descriptor (clj->js
                                  {:colorAttachments
                                   [{:view (gpu/create-texture-view swap-chain-texture)
                                     :clearValue {:r 0.5 :g 0.5 :b 0.5 :a 1.0}
                                     :loadOp "clear"
                                     :storeOp "store"}]
                                   :depthStencilAttachment
                                   {:view (gpu/create-texture-view depth-texture)
                                    :depthClearValue 1.0
                                    :depthLoadOp "clear"
                                    :depthStoreOp "store"}})
          pass-encoder (gpu/begin-render-pass command-encoder
                                              render-pass-descriptor)]
      (doto pass-encoder
        (gpu/set-pipeline pipeline)
        (.setBindGroup 0 bind-group)
        (.setVertexBuffer 0 vertex-buffer)
        (gpu/draw 36)
        (.end))

      ;; Copy rendered frame to feedback texture
      (.copyTextureToTexture command-encoder
                             (clj->js {:texture swap-chain-texture})
                             (clj->js {:texture feedback-texture})
                             (clj->js [(.-width (.-canvas context))
                                       (.-height (.-canvas context))]))

      (.submit queue (clj->js [(.finish command-encoder)])))))

(defn start-feedback-loop!
  "Start animation loop for feedback renderer."
  [renderer rotor-fn !render-id render-id]
  (letfn [(frame []
            (when (= render-id @!render-id)
              (let [time (/ (.now js/Date) 1000)]
                (render-feedback-frame! renderer time rotor-fn)
                (js/requestAnimationFrame frame))))]
    (js/requestAnimationFrame frame)))
