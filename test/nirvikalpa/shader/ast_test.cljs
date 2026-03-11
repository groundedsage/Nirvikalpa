(ns nirvikalpa.shader.ast-test
  (:require [cljs.test :refer [deftest is testing]]
            [nirvikalpa.shader.ast :as ast]))

;;
;; Type Validation Tests
;;

(deftest type-validation-test
  (testing "Valid scalar types"
    (is (ast/valid-type? :f32))
    (is (ast/valid-type? :i32))
    (is (ast/valid-type? :u32))
    (is (ast/valid-type? :bool)))

  (testing "Valid vector types"
    (is (ast/valid-type? :vec2f))
    (is (ast/valid-type? :vec3f))
    (is (ast/valid-type? :vec4f))
    (is (ast/valid-type? :vec2i))
    (is (ast/valid-type? :vec3i))
    (is (ast/valid-type? :vec4i))
    (is (ast/valid-type? :vec2u))
    (is (ast/valid-type? :vec3u))
    (is (ast/valid-type? :vec4u)))

  (testing "Valid matrix types"
    (is (ast/valid-type? :mat2x2f))
    (is (ast/valid-type? :mat3x3f))
    (is (ast/valid-type? :mat4x4f))
    (is (ast/valid-type? :mat2x3f))
    (is (ast/valid-type? :mat3x2f))
    (is (ast/valid-type? :mat2x4f))
    (is (ast/valid-type? :mat4x2f))
    (is (ast/valid-type? :mat3x4f))
    (is (ast/valid-type? :mat4x3f)))

  (testing "Valid texture types"
    (is (ast/valid-type? :texture-2d))
    (is (ast/valid-type? :texture-cube))
    (is (ast/valid-type? :texture-3d))
    (is (ast/valid-type? :texture-2d-array))
    (is (ast/valid-type? :texture-cube-array))
    (is (ast/valid-type? :depth-texture-2d))
    (is (ast/valid-type? :depth-texture-cube)))

  (testing "Valid sampler types"
    (is (ast/valid-type? :sampler))
    (is (ast/valid-type? :sampler-comparison)))

  (testing "Invalid types"
    (is (not (ast/valid-type? :invalid)))
    (is (not (ast/valid-type? "vec3f")))
    (is (not (ast/valid-type? :foo)))
    (is (not (ast/valid-type? nil)))))

;;
;; Expression Builder Tests
;;

(deftest float-vector-constructors-test
  (testing "vec2f creates correct data"
    (is (= [:vec2f 1.0 0.0]
           (ast/vec2f 1.0 0.0))))

  (testing "vec3f creates correct data"
    (is (= [:vec3f 1.0 0.0 0.0]
           (ast/vec3f 1.0 0.0 0.0))))

  (testing "vec4f creates correct data"
    (is (= [:vec4f 1.0 0.0 0.0 1.0]
           (ast/vec4f 1.0 0.0 0.0 1.0)))))

(deftest integer-vector-constructors-test
  (testing "vec2i creates correct data"
    (is (= [:vec2i 1 0]
           (ast/vec2i 1 0))))

  (testing "vec3i creates correct data"
    (is (= [:vec3i 1 0 0]
           (ast/vec3i 1 0 0))))

  (testing "vec4i creates correct data"
    (is (= [:vec4i 1 0 0 1]
           (ast/vec4i 1 0 0 1)))))

(deftest unsigned-vector-constructors-test
  (testing "vec2u creates correct data"
    (is (= [:vec2u 1 0]
           (ast/vec2u 1 0))))

  (testing "vec3u creates correct data"
    (is (= [:vec3u 1 0 0]
           (ast/vec3u 1 0 0))))

  (testing "vec4u creates correct data"
    (is (= [:vec4u 1 0 0 1]
           (ast/vec4u 1 0 0 1)))))

(deftest arithmetic-operations-test
  (testing "add creates correct data"
    (is (= [:+ "a" "b"]
           (ast/add "a" "b")))
    (is (= [:+ "a" "b" "c"]
           (ast/add "a" "b" "c"))))

  (testing "sub creates correct data"
    (is (= [:- "x"]
           (ast/sub "x")))
    (is (= [:- "a" "b"]
           (ast/sub "a" "b"))))

  (testing "mul creates correct data"
    (is (= [:* "x" "y"]
           (ast/mul "x" "y")))
    (is (= [:* "a" "b" "c"]
           (ast/mul "a" "b" "c"))))

  (testing "div creates correct data"
    (is (= [:/ "x" "y"]
           (ast/div "x" "y")))))

(deftest builtin-functions-test
  (testing "dot creates correct data"
    (is (= [:dot "a" "b"]
           (ast/dot "a" "b"))))

  (testing "cross creates correct data"
    (is (= [:cross "a" "b"]
           (ast/cross "a" "b"))))

  (testing "normalize creates correct data"
    (is (= [:normalize "v"]
           (ast/normalize "v"))))

  (testing "length creates correct data"
    (is (= [:length "v"]
           (ast/length "v"))))

  (testing "max-expr creates correct data"
    (is (= [:max 0.0 "x"]
           (ast/max-expr 0.0 "x"))))

  (testing "min-expr creates correct data"
    (is (= [:min "a" "b"]
           (ast/min-expr "a" "b"))))

  (testing "clamp-expr creates correct data"
    (is (= [:clamp "value" 0.0 1.0]
           (ast/clamp-expr "value" 0.0 1.0))))

  (testing "mix creates correct data"
    (is (= [:mix "a" "b" 0.5]
           (ast/mix "a" "b" 0.5))))

  (testing "pow creates correct data"
    (is (= [:pow "base" 2.0]
           (ast/pow "base" 2.0))))

  (testing "sqrt creates correct data"
    (is (= [:sqrt "x"]
           (ast/sqrt "x"))))

  (testing "reflect creates correct data"
    (is (= [:reflect "incident" "normal"]
           (ast/reflect "incident" "normal")))))

(deftest statement-builders-test
  (testing "let-expr creates correct data"
    (is (= [:let "x" 1.0]
           (ast/let-expr "x" 1.0)))
    (is (= [:let "color" [:vec3f 1.0 0.0 0.0]]
           (ast/let-expr "color" (ast/vec3f 1.0 0.0 0.0)))))

  (testing "return-expr creates correct data"
    (is (= [:return "x"]
           (ast/return-expr "x")))
    (is (= [:return {:position "pos" :color "col"}]
           (ast/return-expr {:position "pos" :color "col"})))))

;;
;; Shader Attribute Constructor Tests
;;

(deftest input-attribute-test
  (testing "creates valid input attribute"
    (let [attr (ast/input-attribute 0 :vec3f "position")]
      (is (= 0 (:location attr)))
      (is (= :vec3f (:type attr)))
      (is (= "position" (:name attr)))))

  (testing "validates location is nat-int"
    (is (thrown? js/Error
                 (ast/input-attribute -1 :vec3f "position"))))

  (testing "validates type is valid"
    (is (thrown? js/Error
                 (ast/input-attribute 0 :invalid "position"))))

  (testing "validates name is string"
    (is (thrown? js/Error
                 (ast/input-attribute 0 :vec3f :position)))))

(deftest output-attribute-test
  (testing "creates builtin output"
    (let [attr (ast/output-attribute {:builtin :position} :vec4f)]
      (is (= :position (:builtin attr)))
      (is (= :vec4f (:type attr)))))

  (testing "creates location output"
    (let [attr (ast/output-attribute {:location 0} :vec3f "color")]
      (is (= 0 (:location attr)))
      (is (= :vec3f (:type attr)))
      (is (= "color" (:name attr))))))

(deftest uniform-binding-test
  (testing "creates valid uniform binding"
    (let [binding (ast/uniform-binding 0 0 :mat4x4f "mvp")]
      (is (= 0 (:group binding)))
      (is (= 0 (:binding binding)))
      (is (= :mat4x4f (:type binding)))
      (is (= "mvp" (:name binding)))))

  (testing "validates group is nat-int"
    (is (thrown? js/Error
                 (ast/uniform-binding -1 0 :mat4x4f "mvp"))))

  (testing "validates binding is nat-int"
    (is (thrown? js/Error
                 (ast/uniform-binding 0 -1 :mat4x4f "mvp"))))

  (testing "validates type is valid"
    (is (thrown? js/Error
                 (ast/uniform-binding 0 0 :invalid "mvp"))))

  (testing "validates name is string"
    (is (thrown? js/Error
                 (ast/uniform-binding 0 0 :mat4x4f :mvp)))))

;;
;; Shader Constructor Tests
;;

(deftest vertex-shader-constructor-test
  (testing "creates valid vertex shader"
    (let [shader (ast/vertex-shader
                  {:inputs [(ast/input-attribute 0 :vec3f "position")]
                   :outputs [(ast/output-attribute {:builtin :position} :vec4f)]
                   :body [(ast/return-expr "position")]})]
      (is (= :shader-module (:type shader)))
      (is (= :vertex (:stage shader)))
      (is (= "main" (:entry-point shader)))
      (is (= 1 (count (:inputs shader))))
      (is (= 1 (count (:outputs shader))))
      (is (= 1 (count (:body shader))))
      (is (= [] (:uniforms shader)))))

  (testing "creates vertex shader with uniforms"
    (let [shader (ast/vertex-shader
                  {:inputs [(ast/input-attribute 0 :vec3f "position")]
                   :outputs [(ast/output-attribute {:builtin :position} :vec4f)]
                   :uniforms [(ast/uniform-binding 0 0 :mat4x4f "mvp")]
                   :body [(ast/return-expr "position")]})]
      (is (= 1 (count (:uniforms shader))))))

  (testing "validates inputs is vector"
    (is (thrown? js/Error
                 (ast/vertex-shader
                  {:inputs nil
                   :outputs []
                   :body []}))))

  (testing "validates outputs is vector"
    (is (thrown? js/Error
                 (ast/vertex-shader
                  {:inputs []
                   :outputs nil
                   :body []}))))

  (testing "validates body is vector"
    (is (thrown? js/Error
                 (ast/vertex-shader
                  {:inputs []
                   :outputs []
                   :body nil})))))

(deftest fragment-shader-constructor-test
  (testing "creates valid fragment shader"
    (let [shader (ast/fragment-shader
                  {:inputs [(ast/input-attribute 0 :vec3f "color")]
                   :outputs [(ast/output-attribute {:location 0 :name "out_color"} :vec4f)]
                   :body [(ast/return-expr (ast/vec4f "color.r" "color.g" "color.b" 1.0))]})]
      (is (= :shader-module (:type shader)))
      (is (= :fragment (:stage shader)))
      (is (= "main" (:entry-point shader)))
      (is (= 1 (count (:inputs shader))))
      (is (= 1 (count (:outputs shader))))
      (is (= 1 (count (:body shader)))))))

(deftest compute-shader-constructor-test
  (testing "creates valid compute shader"
    (let [shader (ast/compute-shader
                  {:workgroup-size [256 1 1]
                   :uniforms [(ast/uniform-binding 0 0 :f32 "delta_time")]
                   :body [(ast/let-expr "x" 1.0)]})]
      (is (= :shader-module (:type shader)))
      (is (= :compute (:stage shader)))
      (is (= "main" (:entry-point shader)))
      (is (= [256 1 1] (:workgroup-size shader)))
      (is (= 1 (count (:uniforms shader))))
      (is (= 1 (count (:body shader))))))

  (testing "validates workgroup-size is vector of 3"
    (is (thrown? js/Error
                 (ast/compute-shader
                  {:workgroup-size [256 1]
                   :body []})))
    (is (thrown? js/Error
                 (ast/compute-shader
                  {:workgroup-size nil
                   :body []})))))

;;
;; Integration Tests - Complex Shader ASTs
;;

(deftest complex-vertex-shader-test
  (testing "builds complete vertex shader AST"
    (let [shader (ast/vertex-shader
                  {:inputs [(ast/input-attribute 0 :vec4f "position")
                            (ast/input-attribute 1 :vec3f "color")]
                   :outputs [(ast/output-attribute {:builtin :position} :vec4f)
                             (ast/output-attribute {:location 0 :name "frag_color"} :vec3f)]
                   :uniforms [(ast/uniform-binding 0 0 :mat4x4f "mvp")]
                   :body [(ast/let-expr "clip_pos" (ast/mul "mvp" "position"))
                          (ast/return-expr {:position "clip_pos"
                                            :frag_color "color"})]})]
      (is (= :vertex (:stage shader)))
      (is (= 2 (count (:inputs shader))))
      (is (= 2 (count (:outputs shader))))
      (is (= 1 (count (:uniforms shader))))
      (is (= 2 (count (:body shader)))))))

(deftest complex-fragment-shader-test
  (testing "builds complete fragment shader with lighting"
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
      (is (= :fragment (:stage shader)))
      (is (= 2 (count (:inputs shader))))
      (is (= 1 (count (:outputs shader))))
      (is (= 1 (count (:uniforms shader))))
      (is (= 5 (count (:body shader)))))))
