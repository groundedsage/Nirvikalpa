(ns test-extended-dsl
  "Quick validation of extended DSL features"
  (:require [nirvikalpa.shader.ast :as ast]
            [nirvikalpa.shader.codegen :as codegen]))

;; Test 1: var with type annotation
(def test-var-type
  (let [ast-node (ast/var-expr "output" :VertexOutput)
        wgsl (codegen/expr->wgsl ast-node)]
    (println "\n=== Test 1: var with type annotation ===")
    (println "AST:" ast-node)
    (println "WGSL:" wgsl)
    (assert (= ast-node [:var "output" :VertexOutput]))
    (assert (= wgsl "var output: VertexOutput;"))
    "✓ PASSED"))

;; Test 2: var with string type annotation
(def test-var-string-type
  (let [ast-node (ast/var-expr "count" "i32")
        wgsl (codegen/expr->wgsl ast-node)]
    (println "\n=== Test 2: var with string type ===")
    (println "AST:" ast-node)
    (println "WGSL:" wgsl)
    (assert (= ast-node [:var "count" "i32"]))
    (assert (= wgsl "var count: i32;"))
    "✓ PASSED"))

;; Test 3: do block
(def test-do-block
  (let [ast-node (ast/do-block
                  [(ast/var-expr "temp" 0.5)
                   (ast/assign-expr "color.r" "temp")])
        wgsl (codegen/expr->wgsl ast-node)]
    (println "\n=== Test 3: do block ===")
    (println "AST:" ast-node)
    (println "WGSL:")
    (println wgsl)
    (assert (clojure.string/includes? wgsl "var temp = 0.5;"))
    (assert (clojure.string/includes? wgsl "color.r = temp;"))
    "✓ PASSED"))

;; Test 4: if-else block with both branches
(def test-if-else-block
  (let [ast-node (ast/if-else-block
                  (ast/gt "x" 0.0)
                  [(ast/assign-expr "color" (ast/vec3f 1.0 0.0 0.0))]
                  [(ast/assign-expr "color" (ast/vec3f 0.0 0.0 1.0))])
        wgsl (codegen/expr->wgsl ast-node)]
    (println "\n=== Test 4: if-else block ===")
    (println "AST:" ast-node)
    (println "WGSL:")
    (println wgsl)
    (assert (clojure.string/includes? wgsl "if ("))
    (assert (clojure.string/includes? wgsl "} else {"))
    (assert (clojure.string/includes? wgsl "x > 0.0"))
    "✓ PASSED"))

;; Test 5: if-else block without else
(def test-if-no-else
  (let [ast-node (ast/if-else-block
                  (ast/lt "distance" 1.0)
                  [(ast/assign-expr "valid" "true")])
        wgsl (codegen/expr->wgsl ast-node)]
    (println "\n=== Test 5: if block (no else) ===")
    (println "AST:" ast-node)
    (println "WGSL:")
    (println wgsl)
    (assert (clojure.string/includes? wgsl "if ("))
    (assert (not (clojure.string/includes? wgsl "else")))
    "✓ PASSED"))

;; Test 6: Complex nested structure
(def test-complex-nested
  (let [ast-node (ast/do-block
                  [(ast/var-expr "output" :VertexOutput)
                   (ast/if-else-block
                    (ast/gt "index" 5)
                    [(ast/assign-expr "output.color" (ast/vec3f 1.0 0.0 0.0))]
                    [(ast/assign-expr "output.color" (ast/vec3f 0.0 1.0 0.0))])
                   (ast/assign-expr "output.position" (ast/vec4f "pos.x" "pos.y" 0.0 1.0))])
        wgsl (codegen/expr->wgsl ast-node)]
    (println "\n=== Test 6: Complex nested structure ===")
    (println "WGSL:")
    (println wgsl)
    (assert (clojure.string/includes? wgsl "var output: VertexOutput;"))
    (assert (clojure.string/includes? wgsl "if (index > 5)"))
    (assert (clojure.string/includes? wgsl "} else {"))
    "✓ PASSED"))

;; Test 7: Full shader with extended features
(def test-full-shader
  (let [shader-ast (ast/vertex-shader
                    {:inputs [(ast/input-attribute {:builtin :vertex-index} :u32 "vertex_index")
                              (ast/input-attribute 0 :vec3f "position")]
                     :outputs [(ast/output-attribute {:builtin :position} :vec4f)
                               (ast/output-attribute {:location 0} :vec3f "color")]
                     :structs [(ast/struct-def "VertexOutput"
                                               [(ast/struct-field "Position" :vec4f {:builtin :position})
                                                (ast/struct-field "color" :vec3f {:location 0})])]
                     :return-type "VertexOutput"
                     :body [[:do [[:var "output" :VertexOutput]
                                  [:if-else [:> "vertex_index" 1]
                                   [[:assign "output.color" [:vec3f 1.0 0.0 0.0]]]
                                   [[:assign "output.color" [:vec3f 0.0 0.0 1.0]]]]
                                  [:assign "output.Position" [:vec4f "position.x" "position.y" "position.z" 1.0]]]]
                            [:return "output"]]})
        wgsl (codegen/ast->wgsl shader-ast)]
    (println "\n=== Test 7: Full shader ===")
    (println "WGSL:")
    (println wgsl)
    (assert (clojure.string/includes? wgsl "@vertex"))
    (assert (clojure.string/includes? wgsl "struct VertexOutput"))
    (assert (clojure.string/includes? wgsl "var output: VertexOutput;"))
    (assert (clojure.string/includes? wgsl "if (vertex_index > 1)"))
    (assert (clojure.string/includes? wgsl "return output;"))
    "✓ PASSED"))

;; Run all tests
(println "\n" test-var-type)
(println test-var-string-type)
(println test-do-block)
(println test-if-else-block)
(println test-if-no-else)
(println test-complex-nested)
(println test-full-shader)

(println "\n========================================")
(println "ALL TESTS PASSED!")
(println "========================================")
