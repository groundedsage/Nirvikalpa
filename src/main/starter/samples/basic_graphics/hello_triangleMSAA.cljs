(ns starter.samples.basic-graphics.hello-triangleMSAA
  "Hello Triangle with MSAA using refactored simple renderer"
  (:require [shadow.resource :as rc]
            [nirvikalpa.api.renderer :as renderer]))

;;
;; Shaders (from resources)
;;

(def red-frag (rc/inline "shaders/helloTriangle/red.frag.wsgl"))
(def triangle-vert (rc/inline "shaders/helloTriangle/triangle.vert.wsgl"))

;;
;; Render Function (ACTION layer)
;;

(defn RenderTriangleMSAA
  "Render triangle with 4x MSAA using refactored renderer.

"
  [{:keys [node]}]
  (let [;; Create simple renderer with MSAA enabled
        renderer (renderer/create-simple-renderer! node
                                                   triangle-vert
                                                   red-frag
                                                   :msaa-count 4)]  ; 4x MSAA
    ;; Single render
    (renderer/render-simple-frame! renderer 3)))

