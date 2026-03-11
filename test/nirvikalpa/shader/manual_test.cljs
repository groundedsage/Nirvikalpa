(ns nirvikalpa.shader.manual-test
  "Manual testing namespace for shader DSL

   Load this in the REPL to test shader AST and codegen interactively."
  (:require [nirvikalpa.shader.ast :as ast]
            [nirvikalpa.shader.codegen :as gen]
            [clojure.pprint :refer [pprint]]))

;;
;; Test AST Construction
;;

(defn test-simple-vertex-shader []
  (println "\n=== Simple Vertex Shader AST ===")
  (let [shader (ast/vertex-shader
                {:inputs [(ast/input-attribute 0 :vec3f "position")]
                 :outputs [(ast/output-attribute {:builtin :position} :vec4f)]
                 :body [(ast/let-expr "pos" (ast/vec4f "position.x" "position.y" "position.z" 1.0))
                        (ast/return-expr {:position "pos"})]})]
    (pprint shader)
    shader))

(defn test-vertex-with-uniforms []
  (println "\n=== Vertex Shader with Uniforms AST ===")
  (let [shader (ast/vertex-shader
                {:inputs [(ast/input-attribute 0 :vec4f "position")
                          (ast/input-attribute 1 :vec3f "color")]
                 :outputs [(ast/output-attribute {:builtin :position} :vec4f)
                           (ast/output-attribute {:location 0 :name "frag_color"} :vec3f)]
                 :uniforms [(ast/uniform-binding 0 0 :mat4x4f "mvp")]
                 :body [(ast/let-expr "clip_pos" (ast/mul "mvp" "position"))
                        (ast/return-expr {:position "clip_pos"
                                          :frag_color "color"})]})]
    (pprint shader)
    shader))

(defn test-fragment-shader []
  (println "\n=== Fragment Shader AST ===")
  (let [shader (ast/fragment-shader
                {:inputs [(ast/input-attribute 0 :vec3f "color")]
                 :outputs [(ast/output-attribute {:location 0 :name "out_color"} :vec4f)]
                 :body [(ast/return-expr (ast/vec4f "color.r" "color.g" "color.b" 1.0))]})]
    (pprint shader)
    shader))

(defn test-lighting-fragment []
  (println "\n=== Fragment Shader with Lighting AST ===")
  (let [shader (ast/fragment-shader
                {:inputs [(ast/input-attribute 0 :vec3f "normal")
                          (ast/input-attribute 1 :vec3f "color")]
                 :outputs [(ast/output-attribute {:location 0 :name "out_color"} :vec4f)]
                 :uniforms [(ast/uniform-binding 0 1 :vec3f "light_dir")]
                 :body [(ast/let-expr "normal_norm" (ast/normalize "normal"))
                        (ast/let-expr "light_dir_norm" (ast/normalize "light_dir"))
                        (ast/let-expr "diffuse" (ast/max-expr 0.0 (ast/dot "normal_norm" "light_dir_norm")))
                        (ast/let-expr "lit_color" (ast/mul "color" "diffuse"))
                        (ast/return-expr (ast/vec4f "lit_color.r" "lit_color.g" "lit_color.b" 1.0))]})]
    (pprint shader)
    shader))

;;
;; Test Code Generation
;;

(defn test-codegen-simple []
  (println "\n=== Code Generation: Simple Vertex Shader ===")
  (let [shader (test-simple-vertex-shader)
        wgsl (gen/ast->wgsl shader)]
    (println "\n--- Generated WGSL ---")
    (println wgsl)
    (println "\n--- End Generated WGSL ---")
    wgsl))

(defn test-codegen-uniforms []
  (println "\n=== Code Generation: Vertex with Uniforms ===")
  (let [shader (test-vertex-with-uniforms)
        wgsl (gen/ast->wgsl shader)]
    (println "\n--- Generated WGSL ---")
    (println wgsl)
    (println "\n--- End Generated WGSL ---")
    wgsl))

(defn test-codegen-fragment []
  (println "\n=== Code Generation: Fragment Shader ===")
  (let [shader (test-fragment-shader)
        wgsl (gen/ast->wgsl shader)]
    (println "\n--- Generated WGSL ---")
    (println wgsl)
    (println "\n--- End Generated WGSL ---")
    wgsl))

(defn test-codegen-lighting []
  (println "\n=== Code Generation: Fragment with Lighting ===")
  (let [shader (test-lighting-fragment)
        wgsl (gen/ast->wgsl shader)]
    (println "\n--- Generated WGSL ---")
    (println wgsl)
    (println "\n--- End Generated WGSL ---")
    wgsl))

;;
;; Run All Tests
;;

(defn run-all-tests []
  (println "\n==================================================")
  (println "   NIRVIKALPA SHADER DSL MANUAL TESTS")
  (println "==================================================")

  (test-codegen-simple)
  (test-codegen-uniforms)
  (test-codegen-fragment)
  (test-codegen-lighting)

  (println "\n==================================================")
  (println "   ALL TESTS COMPLETE")
  (println "=================================================="))

(comment
  ;; Run individual tests in REPL:
  (test-simple-vertex-shader)
  (test-vertex-with-uniforms)
  (test-fragment-shader)
  (test-lighting-fragment)

  (test-codegen-simple)
  (test-codegen-uniforms)
  (test-codegen-fragment)
  (test-codegen-lighting)

  ;; Run all tests:
  (run-all-tests)

  ;; Test individual functions:
  (ast/vec4f 1.0 0.0 0.0 1.0)
  (ast/valid-type? :vec3f)
  (gen/type->wgsl :mat4x4f)
  (gen/expr->wgsl (ast/vec4f 1.0 0.0 0.0 1.0)))
