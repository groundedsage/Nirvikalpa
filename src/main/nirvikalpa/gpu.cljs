(ns nirvikalpa.gpu
  (:require [shadow.cljs.modern :refer (js-await)]))

(defonce !gpu-config (atom {:gpu? false}))

(defn fetch-device [adapter store]
  ;; Currently does a little more than fetch the device
  (js-await [device ^js/GPUDevice (.requestDevice adapter)]
            (let [canvas-format (.getPreferredCanvasFormat (.-gpu js/navigator))]
              (swap! !gpu-config assoc
                     :device device
                     :queue ^js/GPUQueue (.-queue device)
                     :canvas-format canvas-format)
              (swap! store assoc :gpu-ready? true))
            (catch failure
                   (js/console.error "WebGPU device setup failed:" failure))))

(defn setup! [store]
  (do
    (when-not (.-gpu js/navigator)
      (swap! !gpu-config assoc :gpu? false))
    (when-let [gpu (.-gpu js/navigator)]
      (js-await [a (.requestAdapter gpu)]
                (swap! !gpu-config assoc :adapter a)
                (swap! !gpu-config assoc :gpu? true)
                (fetch-device a store)
                a
                (catch failure
                       (js/console.error "WebGPU adapter request failed:" failure))))))

(defn create-buffer
  "Creates a GPU buffer.

   Two calling conventions:
   1. Positional: (create-buffer device :storage \"label\" size-or-data mapped?)
   2. Descriptor: (create-buffer device {:label ... :size ... :usage ... :mappedAtCreation ...})"
  ([device type-or-descriptor]
   ;; Descriptor-based call: (create-buffer device {:label ... :size ... :usage ...})
   ^js/GPUBuffer
   (.createBuffer device (clj->js type-or-descriptor)))
  ([device type title obj & [mapped-at-creation]]
   ;; Positional call: (create-buffer device :storage "label" size true)
   ^js/GPUBuffer
   (.createBuffer
    device
    (clj->js {:label title
              :size (if (number? obj) obj (.-byteLength obj))
              :usage (bit-or
                      (case type
                        :uniform (.-UNIFORM ^js/GPUBufferUsage js/GPUBufferUsage)
                        :vertex (.-VERTEX ^js/GPUBufferUsage js/GPUBufferUsage)
                        :storage (.-STORAGE ^js/GPUBufferUsage js/GPUBufferUsage))
                      (.-COPY_DST ^js/GPUBufferUsage js/GPUBufferUsage))
              :mappedAtCreation (boolean mapped-at-creation)}))))

(defn get-texture
  "Gets the current texture from the GPU canvas context."
  [^js/GPUCanvasContext context]
  ^js/GPUTexture
  (.getCurrentTexture context))

(defn configure-canvas
  "Configures the GPU canvas context with the provided settings."
  [^js/GPUCanvasContext context obj]
  (.configure context (clj->js obj)))

(defn create-shader-module
  "Creates a GPU shader module from the provided code."
  [^js/GPUDevice device obj]
  ^js/GPUShaderModule (.createShaderModule device (clj->js obj)))

(defn create-render-pipeline
  "Creates a GPU render pipeline with the provided descriptor."
  [^js/GPUDevice device obj]
  ^js/GPURenderPipeline (.createRenderPipeline device (clj->js obj)))

(defn create-command-encoder
  "Creates a GPU command encoder."
  [^js/GPUDevice device]
  ^js/GPUCommandEncoder
  (.createCommandEncoder device))

(defn set-pipeline
  "Sets the render pipeline for the render pass encoder."
  [^js/GPURenderPassEncoder pass pipeline]
  (.setPipeline pass pipeline))

(defn create-view
  "Creates a view from the current texture of the GPU canvas context."
  [^js/GPUCanvasContext context]
  ^js/GPUTextureView
  (.createView (.getCurrentTexture context)))

;; This maybe also needs to be abstracted into the create-view function
(defn create-texture-view
  "Creates a texture view from the provided GPU texture."
  [^js/GPUTexture texture]
  ^js/GPUTextureView
  (.createView texture))

(defn begin-render-pass
  "Begins a render pass with the provided descriptor."
  [^js/GPUEncoder command-encoder render-pass-descripter]
  ^js/GPURenderPassEncoder
  (.beginRenderPass ^js/GPUEncoder command-encoder render-pass-descripter))

(defn draw
  "Issues a draw command on the render pass encoder."
  ([^js/GPURenderPassEncoder pass-encoder vertex-count]
   (.draw pass-encoder vertex-count))
  ([^js/GPURenderPassEncoder pass-encoder vertex-count instance-count first-vertex first-instance]
   (.draw pass-encoder vertex-count instance-count first-vertex first-instance)))

(defn create-texture
  "Creates a GPU texture.

   Calling conventions:
   1. Descriptor: (create-texture device {:label ... :size ... :format ... :usage ...})
   2. Render attachment: (create-texture device canvas-format node)
   3. MSAA render attachment: (create-texture device sample-count canvas-format node)"
  ([device descriptor-or-format]
   ;; Descriptor-based call: (create-texture device {:label ... :size ... :format ... :usage ...})
   ^js/GPUTexture
   (.createTexture device (clj->js descriptor-or-format)))
  ([device canvas-format node]
   ;; Convenience for render attachment: (create-texture device "bgra8unorm" canvas-element)
   ^js/GPUTexture
   (.createTexture device (clj->js {:size [(.-width node) (.-height node)]
                                    :format canvas-format
                                    :usage js/GPUTextureUsage.RENDER_ATTACHMENT})))
  ([device sample-count canvas-format node]
   ;; MSAA render attachment: (create-texture device 4 "bgra8unorm" canvas-element)
   ^js/GPUTexture
   (.createTexture device (clj->js {:size [(.-width node) (.-height node)]
                                    :sampleCount sample-count
                                    :format canvas-format
                                    :usage js/GPUTextureUsage.RENDER_ATTACHMENT}))))

(defn create-texture-2d
  "Creates a 2D GPU texture with custom descriptor (e.g., for cubemaps)."
  [^js/GPUDevice device descriptor]
  ^js/GPUTexture
  (.createTexture device (clj->js descriptor)))

(defn create-texture-view-with-descriptor
  "Creates a texture view with a custom descriptor (e.g., for cubemaps)."
  [^js/GPUTexture texture descriptor]
  ^js/GPUTextureView
  (.createView texture (clj->js descriptor)))

(defn copy-external-image-to-texture
  "Copies an external image (e.g., ImageBitmap) to a GPU texture."
  [^js/GPUQueue queue source destination copy-size]
  (.copyExternalImageToTexture queue
                               (clj->js source)
                               (clj->js destination)
                               (clj->js copy-size)))

(defn create-bind-group
  "Creates a GPU bind group with the provided descriptor."
  [^js/GPUDevice device obj]
  ^js/GPUBindGroup
  (.createBindGroup device obj))

(defn create-bind-group-layout
  "Creates a bind group layout from a render pipeline."
  [^js/GPURenderPipeline pipeline index]
  ^js/GPUBindGroupLayout
  (.getBindGroupLayout pipeline index))

(defn write-buffer
  "Writes data to a GPU buffer."
  [^js/GPUDevice device ^js/GPUBuffer buffer n obj & [source-offset]]
  (let [queue ^js/GPUQueue (.-queue device)]
    (.writeBuffer queue buffer n obj (or source-offset 0))))

(defn queue
  "Gets the GPU queue from the device."
  [^js/GPUDevice device]
  ^js/GPUQueue
  (.-queue device))

;; =============================================================================
;; MSDF Text Rendering Support Functions
;; =============================================================================

(defn create-sampler
  "Creates a GPU sampler with the provided descriptor."
  [^js/GPUDevice device descriptor]
  ^js/GPUSampler
  (.createSampler device (clj->js descriptor)))

(defn create-bind-group-layout-explicit
  "Creates a bind group layout directly from descriptor (not from pipeline)."
  [^js/GPUDevice device descriptor]
  ^js/GPUBindGroupLayout
  (.createBindGroupLayout device (clj->js descriptor)))

(defn create-pipeline-layout
  "Creates a pipeline layout from bind group layouts."
  [^js/GPUDevice device descriptor]
  ^js/GPUPipelineLayout
  (.createPipelineLayout device (clj->js descriptor)))

(defn create-render-pipeline-async
  "Creates a render pipeline asynchronously. Returns a Promise."
  [^js/GPUDevice device descriptor]
  (.createRenderPipelineAsync device (clj->js descriptor)))

(defn get-mapped-range
  "Gets the mapped range of a buffer for writing data."
  ([^js/GPUBuffer buffer]
   ^js/ArrayBuffer (.getMappedRange buffer))
  ([^js/GPUBuffer buffer offset size]
   ^js/ArrayBuffer (.getMappedRange buffer offset size)))

(defn unmap-buffer
  "Unmaps a previously mapped buffer."
  [^js/GPUBuffer buffer]
  (.unmap buffer))

(defn set-bind-group
  "Sets a bind group on a render pass encoder."
  [^js/GPURenderPassEncoder pass index ^js/GPUBindGroup bind-group]
  (.setBindGroup pass index bind-group))

;; =============================================================================
;; Render Pass Completion Functions
;; =============================================================================

(defn end-render-pass
  "Ends a render pass, finalizing all draw commands."
  [^js/GPURenderPassEncoder render-pass]
  (.end render-pass))

(defn finish-command-encoder
  "Finishes recording commands and returns a GPUCommandBuffer."
  [^js/GPUCommandEncoder encoder]
  ^js/GPUCommandBuffer
  (.finish encoder))

(defn submit-commands
  "Submits command buffers to the device's queue for execution."
  [^js/GPUDevice device command-buffers]
  (let [queue ^js/GPUQueue (.-queue device)]
    (.submit queue (clj->js command-buffers))))

;; =============================================================================
;; Convenience Functions
;; =============================================================================

(defn create-texture-view-from-context
  "Gets current texture from context and creates a view.
   Convenience for the common pattern of rendering to canvas."
  [^js/GPUCanvasContext context]
  ^js/GPUTextureView
  (create-texture-view (get-texture context)))

;; =============================================================================
;; Descriptor-Based API Overloads
;; =============================================================================

(defn create-buffer-from-descriptor
  "Creates a GPU buffer from a descriptor map.
   Descriptor keys: :label, :size, :usage, :mappedAtCreation"
  [^js/GPUDevice device descriptor]
  ^js/GPUBuffer
  (.createBuffer device (clj->js descriptor)))

(defn create-texture-from-descriptor
  "Creates a GPU texture from a descriptor map.
   Descriptor keys: :label, :size, :format, :usage, :sampleCount, etc."
  [^js/GPUDevice device descriptor]
  ^js/GPUTexture
  (.createTexture device (clj->js descriptor)))
