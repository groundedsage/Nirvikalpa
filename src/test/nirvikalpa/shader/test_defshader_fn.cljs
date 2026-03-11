(ns nirvikalpa.shader.test-defshader-fn
  "Test defshader-fn macro"
  (:require-macros [nirvikalpa.shader.dsl :refer [defshader-fn]]))

;; Test: Simple helper function
(defshader-fn sdBox [p :vec2f b :vec2f] :f32
  (let [d (- (abs p) b)]
    (+ (distance (max d (vec2f 0.0 0.0)) (vec2f 0.0 0.0))
       (min (max d.x d.y) 0.0))))

;; Test: Function with single expression
(defshader-fn square [x :f32] :f32
  (* x x))

;; Print generated WGSL
(defn test-defshader-fn []
  (js/console.log "=== sdBox WGSL ===")
  (js/console.log sdBox)
  (js/console.log "\n=== square WGSL ===")
  (js/console.log square))

;; Auto-run on load
(test-defshader-fn)
