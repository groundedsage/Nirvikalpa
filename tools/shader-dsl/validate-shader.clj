#!/usr/bin/env clojure

(require '[nirvikalpa.shader.dsl :as dsl])
(require '[nirvikalpa.shader.codegen :as gen])
(require '[nirvikalpa.shader.ast :as ast])
(require '[clojure.pprint :as pp])

(defn validate-shader
  "Validate a shader form in pure Clojure (no browser needed).
   
   Returns map with:
   - :valid? - boolean
   - :ast - compiled AST
   - :wgsl - generated WGSL string
   - :errors - any errors encountered"
  [shader-form]
  (try
    (let [compiled-ast (dsl/compile-body [shader-form])
          _ (println "\n=== AST ===")
          _ (pp/pprint compiled-ast)
          
          ;; For fragment shader, wrap in fragment-shader AST
          fragment-ast (ast/fragment-shader
                         {:inputs [(ast/input-attribute 0 :vec2f "uv")]
                          :outputs []
                          :uniforms []
                          :body compiled-ast})
          
          wgsl (gen/ast->wgsl fragment-ast)
          _ (println "\n=== WGSL ===")
          _ (println wgsl)]
      
      {:valid? true
       :ast compiled-ast
       :wgsl wgsl
       :errors nil})
    
    (catch Exception e
      (println "\n=== ERROR ===")
      (println "Message:" (.getMessage e))
      (println "Data:" (ex-data e))
      (.printStackTrace e)
      
      {:valid? false
       :ast nil
       :wgsl nil
       :errors {:message (.getMessage e)
                :data (ex-data e)}})))

;; Test cases
(println "====================================")
(println "Testing V2 DSL Shader Validation")
(println "====================================")

;; Test 1: Simple if-block
(println "\n### Test 1: Simple if-block ###")
(validate-shader
  '(if-block (> "uv.x" 0.5)
     (let-block [r 1.0] (vec4f r 0.0 0.0 1.0))
     (let-block [g 1.0] (vec4f 0.0 g 0.0 1.0))))

;; Test 2: Complex do with var-block
(println "\n### Test 2: Complex do with var-block ###")
(validate-shader
  '(let [x 1.0]
     (do
       (var-block res :f32)
       (if-block (> x 0.5)
         (assign res 1.0)
         (assign res 0.0))
       (vec4f res res res 1.0))))

;; Test 3: Nested let in do (Bezier pattern)
(println "\n### Test 3: Bezier pattern ###")
(validate-shader
  '(let [a 1.0
         b 2.0]
     (do
       (var-block res :f32)
       (if-block (> a b)
         (let-block [x (sqrt a)]
           (assign res x))
         (let-block [y (sqrt b)]
           (assign res y)))
       (let [final (* res 2.0)]
         (vec4f final 0.0 0.0 1.0)))))

(println "\n====================================")
(println "Validation Complete")
(println "====================================")
