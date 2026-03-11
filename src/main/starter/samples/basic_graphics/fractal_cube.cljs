(ns starter.samples.basic-graphics.fractal-cube
  "Fractal cube using render-to-texture feedback"
  (:require [shadow.resource :as rc]
            [nirvikalpa.api.renderer :as renderer]
            [nirvikalpa.math.ga :as ga]
            [starter.samples.basic-graphics.cube.kit :as kit]))

;;
;; Shaders
;;

(def basic-vert (rc/inline "shaders/rotatingCube/basic.vert.wgsl"))
(def sample-self-frag (rc/inline "shaders/rotatingCube/sampleSelf.frag.wgsl"))

;;
;; Transform Function (Pure)
;;

(defn fractal-cube-transform [time]
  (let [rotation-axis (ga/vector-3d (.sin js/Math time)
                                    (.cos js/Math time)
                                    0)]
    (ga/rotor-from-axis-angle rotation-axis 1.0)))

;;
;; Main Render Function (ACTION)
;;

(defn FractalCube
  "Render fractal cube using feedback renderer.

"
  [{:keys [node !render-id]}]
  (let [render-id @!render-id
        renderer (renderer/create-feedback-renderer!
                  node
                  basic-vert
                  sample-self-frag
                  kit/cube-vertex-array)]
    (renderer/start-feedback-loop! renderer
                                   fractal-cube-transform
                                   !render-id
                                   render-id)))

;;
;; Summary
;;
;; BEFORE (fractal_cube.cljs - 128 lines):
;; - Manual canvas configure with COPY_SRC usage
;; - Manual feedback texture creation
;; - Manual copyTextureToTexture in render loop
;; - Manual swap chain texture access
;; - Complex feedback management
;;
;; AFTER (this file - ~55 lines):
;; - Call create-feedback-renderer!
;; - Call start-feedback-loop!
;; - Renderer handles all feedback complexity
;;
;; Reduction: 128 → ~55 lines (57% reduction)
;;
;; The "fractal" effect comes from the shader sampling
;; the previous frame, creating infinite recursion.
