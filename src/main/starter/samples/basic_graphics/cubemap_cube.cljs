(ns starter.samples.basic-graphics.cubemap-cube
  "Cubemap-textured rotating cube using refactored API"
  (:require [shadow.resource :as rc]
            [nirvikalpa.api.renderer :as renderer]
            [nirvikalpa.math.ga :as ga]
            [nirvikalpa.gpu :as gpu]
            [starter.samples.basic-graphics.cube.kit :as kit]))

;;
;; Shaders
;;

(def basic-vert (rc/inline "shaders/rotatingCube/basic.vert.wgsl"))
(def sample-cubemap-frag (rc/inline "shaders/rotatingCube/sampleCubemap.frag.wgsl"))

;;
;; Cubemap Face URLs
;;

(def cubemap-urls
  ["https://webgpu.github.io/webgpu-samples/assets/img/cubemap/posx.jpg"  ; +X
   "https://webgpu.github.io/webgpu-samples/assets/img/cubemap/negx.jpg"  ; -X
   "https://webgpu.github.io/webgpu-samples/assets/img/cubemap/posy.jpg"  ; +Y
   "https://webgpu.github.io/webgpu-samples/assets/img/cubemap/negy.jpg"  ; -Y
   "https://webgpu.github.io/webgpu-samples/assets/img/cubemap/posz.jpg"  ; +Z
   "https://webgpu.github.io/webgpu-samples/assets/img/cubemap/negz.jpg"]) ; -Z

;;
;; Transform Function (Pure)
;;

(defn cubemap-cube-transform [time]
  (let [rotation-axis (ga/vector-3d (.sin js/Math time)
                                    (.cos js/Math time)
                                    0)]
    (ga/rotor-from-axis-angle rotation-axis 1.0)))

;;
;; Main Render Function (ACTION - async)
;;

(defn CubemapCube
  "Render cubemap-textured cube using refactored API.

"
  [{:keys [node !render-id]}]
  (let [render-id @!render-id
        {:keys [device queue]} @gpu/!gpu-config]
    ;; Async cubemap loading + renderer setup
    (-> (renderer/load-cubemap! device queue cubemap-urls)
        (.then (fn [cubemap]
                 (let [renderer (renderer/create-cubemap-renderer!
                                 node
                                 basic-vert
                                 sample-cubemap-frag
                                 kit/cube-vertex-array
                                 cubemap)]
                   (renderer/start-cubemap-loop! renderer
                                                 cubemap-cube-transform
                                                 !render-id
                                                 render-id))))
        (.catch (fn [err]
                  (js/console.error "Failed to load cubemap:" err))))))

;;
;; Summary
;;
;; BEFORE (cubemap_cube.cljs - 159 lines):
;; - Manual Promise.all for 6 face images
;; - Manual ImageBitmap creation for each
;; - Manual cubemap texture creation with size [w, h, 6]
;; - Manual loop copying 6 faces with origin [0, 0, i]
;; - Manual cubemap view with dimension="cube"
;; - All GPU setup boilerplate
;;
;; AFTER (this file - ~75 lines):
;; - Call load-cubemap! with URL array
;; - Call create-cubemap-renderer!
;; - Call start-cubemap-loop!
;; - Clean async/await pattern
;;
;; Reduction: 159 → ~75 lines (53% reduction)
