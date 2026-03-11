(ns nirvikalpa.api.core
  (:require [nirvikalpa.gpu :as gpu]
            [shadow.resource :as rc]
            ["wgpu-matrix" :as wgpu-matrix]))

(def fragment-shader (rc/inline "shaders/helloTriangle/red.frag.wsgl"))
(def vertex-shader (rc/inline "shaders/helloTriangle/triangle.vert.wsgl"))

(defn constant-color [{:keys [color]}]
  {:type :constant-color
   :color (or color [1 0 0 1])})

(defn compute-view-matrix [{:keys [position look-at up]}]
  (.lookAt wgpu-matrix/mat4 (clj->js position) (clj->js look-at) (clj->js (or up [0 1 0]))))

(defn compute-model-matrix [{:keys [position rotation scale]}]
  (let [model (.identity wgpu-matrix/mat4)]
    (.translate wgpu-matrix/mat4 model (clj->js (or position [0 0 0])) model)
    (when rotation
      (let [[rx ry rz] rotation]
        (.rotateX wgpu-matrix/mat4 model rx model)
        (.rotateY wgpu-matrix/mat4 model ry model)
        (.rotateZ wgpu-matrix/mat4 model rz model)))
    (.scale wgpu-matrix/mat4 model (clj->js (or scale [1 1 1])) model)
    model))

(defn mesh [{:keys [vertices material position id]}]
  {:type :mesh
   :vertices vertices
   :material (or material {:type :constant-color :color [1 0 0 1]})
   :transform {:position (or position [0 0 0])
               :rotation [0 0 0]
               :scale [1 1 1]}
   :id id})

(defn vertices-to-array [vertices]
  (js/Float32Array.
   (clj->js (mapcat :position vertices))))

(defn compute-projection-matrix [{:keys [fov aspect near far]}]
  (.perspective wgpu-matrix/mat4 (* fov (/ Math/PI 180)) aspect near far))

(defn perspective-camera [{:keys [fov position look-at]}]
  {:fov (or fov 45)
   :position (or position [0 0 2])
   :look-at (or look-at [0 0 0])
   :aspect 1
   :near 0.1
   :far 100})

(defn start [{:keys [scene update canvas]}]
  (let [context (.getContext canvas "webgpu")
        {:keys [device canvas-format]} @gpu/!gpu-config
        _ (gpu/configure-canvas context {:device device :format canvas-format :alphaMode "premultiplied"})
        ;; Vertex buffers
        vertex-buffers (into {}
                             (for [obj (:objects scene)]
                               [(:id obj)
                                (let [buffer (gpu/create-buffer device :VERTEX nil (* 16 (count (:vertices obj))))
                                      vertices (vertices-to-array (:vertices obj))]
                                  (gpu/write-buffer device buffer 0 vertices)
                                  buffer)]))
        ;; Uniform buffers
        mvp-buffer (gpu/create-buffer device :UNIFORM nil (* 4 16)) ;; MVP matrix
        color-buffers (into {}
                            (for [obj (:objects scene)]
                              [(:id obj)
                               (let [buffer (gpu/create-buffer device :UNIFORM nil 16)]
                                 (when (= (:type (:material obj)) :constant-color)
                                   (gpu/write-buffer device buffer 0 (:color (:material obj))))
                                 buffer)]))
        ;; Shader modules
        vert-module (gpu/create-shader-module device {:code vertex-shader})
        frag-module (gpu/create-shader-module device {:code fragment-shader})
        ;; Render pipeline
        pipeline (gpu/create-render-pipeline
                  device
                  {:layout "auto"
                   :vertex {:module vert-module
                            :entryPoint "main"
                            :buffers [{:arrayStride 16
                                       :attributes [{:shaderLocation 0 :offset 0 :format "float32x4"}]}]}
                   :fragment {:module frag-module
                              :entryPoint "main"
                              :targets [{:format canvas-format}]}
                   :primitive {:topology "triangle-list"}})
        ;; Bind groups
        mvp-bind-group (gpu/create-bind-group
                        device
                        {:layout (.getBindGroupLayout pipeline 0)
                         :entries [{:binding 0 :resource {:buffer mvp-buffer}}]})
        color-bind-groups (into {}
                                (for [obj (:objects scene)]
                                  [(:id obj)
                                   (when (= (:type (:material obj)) :constant-color)
                                     (gpu/create-bind-group
                                      device
                                      {:layout (.getBindGroupLayout pipeline 1)
                                       :entries [{:binding 0 :resource {:buffer (get color-buffers (:id obj))}}]}))]))
        ;; Depth texture
        depth-texture (gpu/create-texture device "depth24plus" canvas)]
    ;; Animation loop (static for triangle, but supports updates)
    (letfn [(frame [t]
              (let [current-scene ((or update identity) scene (/ t 1000))
                    projection-matrix (compute-projection-matrix (:camera current-scene))
                    view-matrix (compute-view-matrix (:camera current-scene))
                    command-encoder (gpu/create-command-encoder device)
                    render-pass (gpu/begin-render-pass
                                 command-encoder
                                 {:colorAttachments [{:view (gpu/create-view context)
                                                      :clearValue (:color (:background current-scene) [0 0 0 1])
                                                      :loadOp "clear"
                                                      :storeOp "store"}]
                                  :depthStencilAttachment {:view (gpu/create-texture-view depth-texture)
                                                           :depthClearValue 1.0
                                                           :depthLoadOp "clear"
                                                           :depthStoreOp "store"}})]
                (doseq [obj (:objects current-scene)]
                  (let [mvp-matrix (.multiply wgpu-matrix/mat4
                                              projection-matrix
                                              (.multiply wgpu-matrix/mat4
                                                         view-matrix
                                                         (compute-model-matrix (:transform obj))))]
                    (gpu/write-buffer device mvp-buffer 0 mvp-matrix)
                    (.setPipeline render-pass pipeline)
                    (.setBindGroup render-pass 0 mvp-bind-group)
                    (when-let [color-bind-group (get color-bind-groups (:id obj))]
                      (.setBindGroup render-pass 1 color-bind-group))
                    (.setVertexBuffer render-pass 0 (get vertex-buffers (:id obj)))
                    (.draw render-pass (count (:vertices obj)))))
                (.end render-pass)
                (.submit (gpu/queue device) #js [(.finish command-encoder)]))
              (js/requestAnimationFrame frame))]
      (js/requestAnimationFrame frame))))