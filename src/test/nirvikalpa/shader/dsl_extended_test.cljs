(ns nirvikalpa.shader.dsl-extended-test
  "Test suite for extended DSL features: if/else blocks, do blocks, and improved var support"
  (:require [cljs.test :refer-macros [deftest is testing]]
            [nirvikalpa.shader.ast :as ast]
            [nirvikalpa.shader.codegen :as codegen]))

(deftest test-var-with-type-annotation
  (testing "var with type annotation (no initialization)"
    (let [var-ast (ast/var-expr "output" :VertexOutput)
          wgsl (codegen/expr->wgsl var-ast)]
      (is (= var-ast [:var "output" :VertexOutput]))
      (is (= wgsl "var output: VertexOutput;")))))

(deftest test-var-with-string-type
  (testing "var with string type annotation"
    (let [var-ast (ast/var-expr "output" "VertexOutput")
          wgsl (codegen/expr->wgsl var-ast)]
      (is (= var-ast [:var "output" "VertexOutput"]))
      (is (= wgsl "var output: VertexOutput;")))))

(deftest test-var-with-value
  (testing "var with initial value"
    (let [var-ast (ast/var-expr "count" 0)
          wgsl (codegen/expr->wgsl var-ast)]
      (is (= var-ast [:var "count" 0]))
      (is (= wgsl "var count = 0;")))))

(deftest test-do-block
  (testing "do block with multiple statements"
    (let [do-ast (ast/do-block
                  [(ast/var-expr "temp" 0.5)
                   (ast/assign-expr "color.r" "temp")
                   (ast/assign-expr "color.g" "temp")])
          wgsl (codegen/expr->wgsl do-ast)]
      (is (= do-ast [:do [[:var "temp" 0.5]
                          [:assign "color.r" "temp"]
                          [:assign "color.g" "temp"]]]))
      (is (clojure.string/includes? wgsl "var temp = 0.5;"))
      (is (clojure.string/includes? wgsl "color.r = temp;"))
      (is (clojure.string/includes? wgsl "color.g = temp;")))))

(deftest test-if-else-block-with-else
  (testing "if/else block with both branches"
    (let [if-ast (ast/if-else-block
                  (ast/gt "x" 0.0)
                  [(ast/assign-expr "color" (ast/vec3f 1.0 0.0 0.0))
                   (ast/assign-expr "intensity" 1.0)]
                  [(ast/assign-expr "color" (ast/vec3f 0.0 0.0 1.0))
                   (ast/assign-expr "intensity" 0.5)])
          wgsl (codegen/expr->wgsl if-ast)]
      (is (= if-ast [:if-else
                     [:> "x" 0.0]
                     [[:assign "color" [:vec3f 1.0 0.0 0.0]]
                      [:assign "intensity" 1.0]]
                     [[:assign "color" [:vec3f 0.0 0.0 1.0]]
                      [:assign "intensity" 0.5]]]))
      (is (clojure.string/includes? wgsl "if ("))
      (is (clojure.string/includes? wgsl "} else {"))
      (is (clojure.string/includes? wgsl "x > 0.0"))
      (is (clojure.string/includes? wgsl "vec3<f32>(1.0, 0.0, 0.0)"))
      (is (clojure.string/includes? wgsl "vec3<f32>(0.0, 0.0, 1.0)")))))

(deftest test-if-else-block-no-else
  (testing "if block without else branch"
    (let [if-ast (ast/if-else-block
                  (ast/gt "distance" 1.0)
                  [(ast/assign-expr "valid" "false")])
          wgsl (codegen/expr->wgsl if-ast)]
      (is (= if-ast [:if-else
                     [:> "distance" 1.0]
                     [[:assign "valid" "false"]]
                     []]))
      (is (clojure.string/includes? wgsl "if ("))
      (is (not (clojure.string/includes? wgsl "else")))
      (is (clojure.string/includes? wgsl "distance > 1.0")))))

(deftest test-ternary-if-expr
  (testing "ternary if expression (not block)"
    (let [if-ast (ast/if-expr (ast/gt "x" 0.0) 1.0 0.0)
          wgsl (codegen/expr->wgsl if-ast)]
      (is (= if-ast [:if [:> "x" 0.0] 1.0 0.0]))
      ;; WGSL uses select for ternary
      (is (clojure.string/includes? wgsl "select("))
      (is (clojure.string/includes? wgsl "x > 0.0")))))

(deftest test-complex-nested-structure
  (testing "complex structure with nested if/else and do blocks"
    (let [complex-ast
          (ast/do-block
           [(ast/var-expr "output" :VertexOutput)
            (ast/if-else-block
             (ast/gt "index" 5)
             [(ast/assign-expr "output.color" (ast/vec3f 1.0 0.0 0.0))]
             [(ast/assign-expr "output.color" (ast/vec3f 0.0 1.0 0.0))])
            (ast/assign-expr "output.position" (ast/vec4f "pos.x" "pos.y" "pos.z" 1.0))])
          wgsl (codegen/expr->wgsl complex-ast)]

      ;; Check structure
      (is (= (first complex-ast) :do))

      ;; Check WGSL output contains expected elements
      (is (clojure.string/includes? wgsl "var output: VertexOutput;"))
      (is (clojure.string/includes? wgsl "if ("))
      (is (clojure.string/includes? wgsl "index > 5"))
      (is (clojure.string/includes? wgsl "} else {"))
      (is (clojure.string/includes? wgsl "output.color ="))
      (is (clojure.string/includes? wgsl "output.position ="))
      (is (clojure.string/includes? wgsl "vec3<f32>(1.0, 0.0, 0.0)"))
      (is (clojure.string/includes? wgsl "vec3<f32>(0.0, 1.0, 0.0)"))
      (is (clojure.string/includes? wgsl "vec4<f32>(pos.x, pos.y, pos.z, 1.0)")))))

(deftest test-full-shader-with-extended-features
  (testing "complete shader using extended DSL features"
    (let [shader-ast
          (ast/vertex-shader
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

      ;; Check that WGSL contains expected structure
      (is (clojure.string/includes? wgsl "@vertex"))
      (is (clojure.string/includes? wgsl "struct VertexOutput"))
      (is (clojure.string/includes? wgsl "var output: VertexOutput;"))
      (is (clojure.string/includes? wgsl "if (vertex_index > 1)"))
      (is (clojure.string/includes? wgsl "} else {"))
      (is (clojure.string/includes? wgsl "output.color ="))
      (is (clojure.string/includes? wgsl "output.Position ="))
      (is (clojure.string/includes? wgsl "return output;")))))
