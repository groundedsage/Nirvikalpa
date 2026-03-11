(ns nirvikalpa.shader.validate-test
  (:require [cljs.test :refer [deftest is testing]]
            [nirvikalpa.shader.validate :as validate]
            [nirvikalpa.shader.ast :as ast]))

;;
;; Schema Validation Tests
;;

(deftest valid-vertex-shader-test
  (testing "validates correct vertex shader"
    (let [shader (ast/vertex-shader
                  {:inputs [(ast/input-attribute 0 :vec3f "position")]
                   :outputs [(ast/output-attribute {:builtin :position} :vec4f)]
                   :body [(ast/return-expr "position")]})]
      (is (validate/valid-shader? shader))
      (is (= {:valid? true :shader shader}
             (validate/validate-shader shader))))))

(deftest valid-fragment-shader-test
  (testing "validates correct fragment shader"
    (let [shader (ast/fragment-shader
                  {:inputs [(ast/input-attribute 0 :vec3f "color")]
                   :outputs [(ast/output-attribute {:location 0 :name "out_color"} :vec4f)]
                   :body [(ast/return-expr (ast/vec4f "color.r" "color.g" "color.b" 1.0))]})]
      (is (validate/valid-shader? shader)))))

(deftest valid-compute-shader-test
  (testing "validates correct compute shader"
    (let [shader (ast/compute-shader
                  {:workgroup-size [256 1 1]
                   :uniforms [(ast/uniform-binding 0 0 :f32 "delta_time")]
                   :body [(ast/let-expr "x" 1.0)]})]
      (is (validate/valid-shader? shader)))))

(deftest invalid-shader-type-test
  (testing "rejects shader with wrong :type"
    (let [shader {:type :invalid
                  :stage :vertex
                  :entry-point "main"
                  :inputs []
                  :outputs []
                  :uniforms []
                  :body []}]
      (is (not (validate/valid-shader? shader)))
      (is (some? (validate/explain-shader shader))))))

(deftest invalid-stage-test
  (testing "rejects shader with invalid stage"
    (let [shader {:type :shader-module
                  :stage :invalid
                  :entry-point "main"
                  :inputs []
                  :outputs []
                  :uniforms []
                  :body []}]
      (is (not (validate/valid-shader? shader))))))

(deftest missing-required-fields-test
  (testing "rejects shader missing required fields"
    (let [shader {:type :shader-module
                  :stage :vertex
                  ;; Missing :entry-point, :inputs, :outputs, :body
                  :uniforms []}]
      (is (not (validate/valid-shader? shader))))))

;;
;; Duplicate Detection Tests
;;

(deftest duplicate-input-locations-test
  (testing "detects duplicate input locations"
    (let [shader (ast/vertex-shader
                  {:inputs [(ast/input-attribute 0 :vec3f "position")
                            (ast/input-attribute 0 :vec3f "normal")]  ;; Duplicate location 0!
                   :outputs [(ast/output-attribute {:builtin :position} :vec4f)]
                   :body [(ast/return-expr "position")]})]
      (is (some? (validate/check-duplicates shader)))
      (is (= :duplicate-locations
             (:error (validate/check-duplicates shader)))))))

(deftest duplicate-output-locations-test
  (testing "detects duplicate output locations"
    (let [shader (ast/fragment-shader
                  {:inputs []
                   :outputs [(ast/output-attribute {:location 0 :name "color1"} :vec4f)
                             (ast/output-attribute {:location 0 :name "color2"} :vec4f)]  ;; Duplicate!
                   :body [(ast/return-expr (ast/vec4f 1.0 0.0 0.0 1.0))]})]
      (is (some? (validate/check-duplicates shader)))
      (is (= :duplicate-locations
             (:error (validate/check-duplicates shader)))))))

(deftest duplicate-uniform-bindings-test
  (testing "detects duplicate uniform bindings"
    (let [shader (ast/vertex-shader
                  {:inputs []
                   :outputs [(ast/output-attribute {:builtin :position} :vec4f)]
                   :uniforms [(ast/uniform-binding 0 0 :mat4x4f "mvp")
                              (ast/uniform-binding 0 0 :vec4f "color")]  ;; Duplicate group 0, binding 0!
                   :body [(ast/return-expr "position")]})]
      (is (some? (validate/check-duplicates shader)))
      (is (= :duplicate-bindings
             (:error (validate/check-duplicates shader)))))))

(deftest no-duplicates-test
  (testing "passes shader with no duplicates"
    (let [shader (ast/vertex-shader
                  {:inputs [(ast/input-attribute 0 :vec3f "position")
                            (ast/input-attribute 1 :vec3f "normal")]
                   :outputs [(ast/output-attribute {:builtin :position} :vec4f)
                             (ast/output-attribute {:location 0 :name "color"} :vec3f)]
                   :uniforms [(ast/uniform-binding 0 0 :mat4x4f "mvp")
                              (ast/uniform-binding 0 1 :vec4f "color")]
                   :body [(ast/return-expr {:position "position" :color "normal"})]})]
      (is (nil? (validate/check-duplicates shader))))))

;;
;; Validate with Rules Tests
;;

(deftest validate-with-rules-schema-error-test
  (testing "catches schema errors"
    (let [shader {:type :invalid
                  :stage :vertex
                  :entry-point "main"
                  :inputs []
                  :outputs []
                  :uniforms []
                  :body []}
          result (validate/validate-with-rules shader)]
      (is (not (:valid? result)))
      (is (some? (:errors result))))))

(deftest validate-with-rules-duplicate-error-test
  (testing "catches duplicate binding errors"
    (let [shader (ast/vertex-shader
                  {:inputs []
                   :outputs [(ast/output-attribute {:builtin :position} :vec4f)]
                   :uniforms [(ast/uniform-binding 0 0 :mat4x4f "mvp")
                              (ast/uniform-binding 0 0 :vec4f "color")]
                   :body [(ast/return-expr "position")]})
          result (validate/validate-with-rules shader)]
      (is (not (:valid? result)))
      (is (= :duplicate-bindings (:error (:errors result)))))))

(deftest validate-with-rules-success-test
  (testing "passes valid shader with rules"
    (let [shader (ast/vertex-shader
                  {:inputs [(ast/input-attribute 0 :vec3f "position")]
                   :outputs [(ast/output-attribute {:builtin :position} :vec4f)]
                   :uniforms [(ast/uniform-binding 0 0 :mat4x4f "mvp")]
                   :body [(ast/return-expr "position")]})
          result (validate/validate-with-rules shader)]
      (is (:valid? result))
      (is (= shader (:shader result))))))

;;
;; Validate! (throwing) Tests
;;

(deftest validate!-success-test
  (testing "returns shader when valid"
    (let [shader (ast/vertex-shader
                  {:inputs []
                   :outputs [(ast/output-attribute {:builtin :position} :vec4f)]
                   :body [(ast/return-expr "position")]})]
      (is (= shader (validate/validate! shader))))))

(deftest validate!-throws-test
  (testing "throws on invalid shader"
    (let [shader {:type :invalid}]
      (is (thrown? js/Error
                   (validate/validate! shader))))))
