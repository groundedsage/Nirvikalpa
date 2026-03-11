(ns starter.samples.text.msdf-text
  "MSDF Text Rendering sample - WebGPU textRenderingMsdf implementation.

   Demonstrates high-quality text rendering using Multi-channel Signed Distance Fields
   with resolution-independent antialiasing and camera animation."
  (:require [nirvikalpa.gpu :as gpu]
            [nirvikalpa.text.msdf :as msdf]))

;; =============================================================================
;; Matrix Utilities
;; =============================================================================

(defn rotation-z-matrix
  "Create Z-axis rotation matrix from angle in radians."
  [angle]
  (let [c (js/Math.cos angle)
        s (js/Math.sin angle)]
    (js/Float32Array.
     #js [c (- s) 0 0
          s c 0 0
          0 0 1 0
          0 0 0 1])))

;; =============================================================================
;; Render Component
;; =============================================================================

(defn RenderMsdfText
  "Render MSDF text with rotating camera animation.

   Demonstrates:
   - Loading MSDF font from JSON + PNG atlas
   - Multiple text strings with different colors and scales
   - Camera animation with orthographic projection
   - High-quality antialiased text rendering"
  [{:keys [node !render-id]}]
  (let [render-id @!render-id
        {:keys [device queue canvas-format]} @gpu/!gpu-config
        context (.getContext node "webgpu")
        canvas-width (.-width node)
        canvas-height (.-height node)]

    ;; Configure canvas
    (gpu/configure-canvas context
                          {:device device
                           :format canvas-format
                           :alphaMode "premultiplied"})

    ;; Load font and set up rendering
    (-> (msdf/load-font! device queue "fonts/ya-hei-ascii-msdf.json" canvas-format)
        (.then
         (fn [font]
           (let [;; Create camera buffer
                 camera-buffer (msdf/create-camera-buffer device)

                  ;; Calculate canvas center for positioning
                 half-width (/ canvas-width 2)
                 half-height (/ canvas-height 2)

                  ;; Format multiple text strings at different positions/colors/scales
                 formatted-texts
                 (msdf/texts font camera-buffer
                             [{:text "Nirvikalpa"
                               :x 0 :y 80
                               :anchor :center
                               :pixel-scale 1.0
                               :color [1.0 0.8 0.0 1.0]}  ; Gold

                              {:text "MSDF Text Rendering"
                               :x 0 :y 0
                               :anchor :center
                               :pixel-scale 0.6
                               :color [1.0 1.0 1.0 1.0]}  ; White

                              {:text "WebGPU + ClojureScript"
                               :x 0 :y -60
                               :anchor :center
                               :pixel-scale 0.4
                               :color [0.5 0.8 1.0 1.0]}])]  ; Light blue

              ;; Animation loop
             (letfn [(render-frame [time]
                       (when (= render-id @!render-id)
                          ;; Calculate rotation angle (slow rotation)
                         (let [angle (* time 0.0003)

                                ;; Create orthographic projection centered at origin
                               projection (msdf/ortho-projection
                                           (- half-width) half-width
                                           (- half-height) half-height)

                                ;; Create rotation view matrix
                               view (rotation-z-matrix angle)]

                            ;; Update camera
                           (msdf/update-camera! device camera-buffer projection view)

                            ;; Create command encoder and render pass
                           (let [encoder (gpu/create-command-encoder device)
                                 pass (gpu/begin-render-pass encoder
                                                             (clj->js
                                                              {:colorAttachments
                                                               [{:view (gpu/create-view context)
                                                                 :clearValue {:r 0.1 :g 0.1 :b 0.15 :a 1.0}
                                                                 :loadOp "clear"
                                                                 :storeOp "store"}]}))]

                              ;; Render all text strings
                             (msdf/render-texts! pass formatted-texts)

                              ;; End pass and submit
                             (.end pass)
                             (.submit queue #js [(.finish encoder)])))

                          ;; Request next frame
                         (js/requestAnimationFrame render-frame)))]
               (js/requestAnimationFrame render-frame)))))
        (.catch (fn [err]
                  (js/console.error "Failed to load MSDF font:" err))))))
