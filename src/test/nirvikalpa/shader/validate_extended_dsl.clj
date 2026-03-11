(ns validate-extended-dsl
  "Direct validation of extended DSL codegen"
  (:require [nirvikalpa.shader.ast :as ast]
            [nirvikalpa.shader.codegen :as codegen]))

(println "\n========================================")
(println "EXTENDED DSL VALIDATION")
(println "========================================")

;; Test 1: var with keyword type
(println "\n[1] var with keyword type annotation")
(let [ast-node (ast/var-expr "output" :VertexOutput)
      wgsl (codegen/expr->wgsl ast-node)]
  (println "Input: (ast/var-expr \"output\" :VertexOutput)")
  (println "AST:  " ast-node)
  (println "WGSL: " wgsl)
  (assert (= wgsl "var output: VertexOutput;"))
  (println "✓ PASS"))

;; Test 2: var with string type
(println "\n[2] var with string type annotation")
(let [ast-node (ast/var-expr "count" "i32")
      wgsl (codegen/expr->wgsl ast-node)]
  (println "Input: (ast/var-expr \"count\" \"i32\")")
  (println "AST:  " ast-node)
  (println "WGSL: " wgsl)
  (assert (= wgsl "var count: i32;"))
  (println "✓ PASS"))

;; Test 3: var with value initialization
(println "\n[3] var with value initialization")
(let [ast-node (ast/var-expr "temp" 0.5)
      wgsl (codegen/expr->wgsl ast-node)]
  (println "Input: (ast/var-expr \"temp\" 0.5)")
  (println "AST:  " ast-node)
  (println "WGSL: " wgsl)
  (assert (= wgsl "var temp = 0.5;"))
  (println "✓ PASS"))

;; Test 4: do block
(println "\n[4] do block with multiple statements")
(let [ast-node (ast/do-block
                [(ast/var-expr "temp" 0.5)
                 (ast/assign-expr "color.r" "temp")
                 (ast/assign-expr "color.g" "temp")])
      wgsl (codegen/expr->wgsl ast-node)]
  (println "Input: (ast/do-block [...])")
  (println "WGSL:")
  (println wgsl)
  (assert (clojure.string/includes? wgsl "var temp = 0.5;"))
  (assert (clojure.string/includes? wgsl "color.r = temp;"))
  (assert (clojure.string/includes? wgsl "color.g = temp;"))
  (println "✓ PASS"))

;; Test 5: if-else block with both branches
(println "\n[5] if-else block with both branches")
(let [ast-node (ast/if-else-block
                (ast/gt "x" 0.0)
                [(ast/assign-expr "color" (ast/vec3f 1.0 0.0 0.0))]
                [(ast/assign-expr "color" (ast/vec3f 0.0 0.0 1.0))])
      wgsl (codegen/expr->wgsl ast-node)]
  (println "Input: (ast/if-else-block ...)")
  (println "WGSL:")
  (println wgsl)
  (assert (clojure.string/includes? wgsl "if (x > 0.0)"))
  (assert (clojure.string/includes? wgsl "} else {"))
  (println "✓ PASS"))

;; Test 6: if-else block without else
(println "\n[6] if-else block without else branch")
(let [ast-node (ast/if-else-block
                (ast/lt "distance" 1.0)
                [(ast/assign-expr "valid" "true")])
      wgsl (codegen/expr->wgsl ast-node)]
  (println "Input: (ast/if-else-block ... [no else])")
  (println "WGSL:")
  (println wgsl)
  (assert (clojure.string/includes? wgsl "if (distance < 1.0)"))
  (assert (not (clojure.string/includes? wgsl "else")))
  (println "✓ PASS"))

;; Test 7: Ternary if (backward compatibility)
(println "\n[7] Ternary if expression (backward compatibility)")
(let [ast-node (ast/if-expr (ast/gt "x" 0.0) 1.0 0.0)
      wgsl (codegen/expr->wgsl ast-node)]
  (println "Input: (ast/if-expr ...)")
  (println "WGSL: " wgsl)
  (assert (clojure.string/includes? wgsl "select("))
  (println "✓ PASS"))

;; Test 8: Complex nested structure
(println "\n[8] Complex nested structure")
(let [ast-node (ast/do-block
                [(ast/var-expr "output" :VertexOutput)
                 (ast/if-else-block
                  (ast/gt "index" 5)
                  [(ast/assign-expr "output.color" (ast/vec3f 1.0 0.0 0.0))]
                  [(ast/assign-expr "output.color" (ast/vec3f 0.0 1.0 0.0))])
                 (ast/assign-expr "output.position" (ast/vec4f "pos.x" "pos.y" 0.0 1.0))])
      wgsl (codegen/expr->wgsl ast-node)]
  (println "Input: Complex nested do/if-else/assign")
  (println "WGSL:")
  (println wgsl)
  (assert (clojure.string/includes? wgsl "var output: VertexOutput;"))
  (assert (clojure.string/includes? wgsl "if (index > 5)"))
  (assert (clojure.string/includes? wgsl "output.color = vec3<f32>(1.0, 0.0, 0.0);"))
  (assert (clojure.string/includes? wgsl "output.position = vec4<f32>(pos.x, pos.y, 0.0, 1.0);"))
  (println "✓ PASS"))

;; Test 9: Full shader
(println "\n[9] Complete shader with extended features")
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
  (println "Input: Complete vertex shader AST")
  (println "WGSL:")
  (println wgsl)
  (println)
  (assert (clojure.string/includes? wgsl "@vertex"))
  (assert (clojure.string/includes? wgsl "struct VertexOutput"))
  (assert (clojure.string/includes? wgsl "var output: VertexOutput;"))
  (assert (clojure.string/includes? wgsl "if (vertex_index > 1)"))
  (assert (clojure.string/includes? wgsl "return output;"))
  (println "✓ PASS"))

(println "\n========================================")
(println "ALL 9 TESTS PASSED!")
(println "========================================")
(println "\nExtended DSL features are working correctly:")
(println "  ✓ var with type annotations (keyword & string)")
(println "  ✓ var with value initialization")
(println "  ✓ do blocks (statement sequencing)")
(println "  ✓ if-else blocks (with and without else)")
(println "  ✓ Ternary if (backward compatibility)")
(println "  ✓ Complex nested structures")
(println "  ✓ Full shader generation")
(println "\nReady to convert raw WGSL shaders to DSL!")
(println "========================================\n")
