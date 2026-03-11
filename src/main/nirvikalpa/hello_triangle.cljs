(ns nirvikalpa.hello-triangle
  (:require [nirvikalpa.api.core :as n]
            [nirvikalpa.api.geometry :as geo]))

;; Define the scene
(def scene
  {:camera (n/perspective-camera {:fov 45 :position [0 0 2] :look-at [0 0 0]})
   :objects [(n/mesh {:vertices (geo/triangle)
                      :material (n/constant-color {:color [0 1 0 1]})
                      :position [0 0 0]
                      :id :triangle})]
   :background {:color [0 0 0 1]}})

;; Start rendering (no update needed for static scene)
(defn render-triangle [{:keys [node !render-id]}]
  (when (= @!render-id (:id (meta node)))
    (n/start {:scene scene
              :canvas node})))