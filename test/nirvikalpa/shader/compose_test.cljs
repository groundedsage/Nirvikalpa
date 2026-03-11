(ns nirvikalpa.shader.compose-test
  (:require [cljs.test :refer [deftest is testing]]
            [nirvikalpa.shader.compose :as comp]
            [nirvikalpa.shader.ast :as ast]))

;;
;; Component Addition Tests
;;

(deftest add-uniform-test
  (testing "adds uniform to empty shader"
    (let [shader (ast/vertex-shader {:inputs [] :outputs [] :body []})
          with-uniform (comp/add-uniform shader 0 0 "mvp" :mat4x4f)]
      (is (= 1 (count (:uniforms with-uniform))))
      (is (= "mvp" (-> with-uniform :uniforms first :name)))
      (is (= :mat4x4f (-> with-uniform :uniforms first :type)))
      (is (= 0 (-> with-uniform :uniforms first :group)))
      (is (= 0 (-> with-uniform :uniforms first :binding)))))

  (testing "adds uniform to shader with existing uniforms"
    (let [shader (ast/vertex-shader
                  {:inputs []
                   :outputs []
                   :uniforms [(ast/uniform-binding 0 0 "mvp" :mat4x4f)]
                   :body []})
          with-uniform (comp/add-uniform shader 0 1 "color" :vec4f)]
      (is (= 2 (count (:uniforms with-uniform)))))))

(deftest add-input-test
  (testing "adds input to shader"
    (let [shader (ast/vertex-shader {:inputs [] :outputs [] :body []})
          with-input (comp/add-input shader 0 "position" :vec3f)]
      (is (= 1 (count (:inputs with-input))))
      (is (= "position" (-> with-input :inputs first :name)))))

  (testing "adds multiple inputs"
    (let [shader (ast/vertex-shader {:inputs [] :outputs [] :body []})
          with-inputs (-> shader
                          (comp/add-input 0 "position" :vec3f)
                          (comp/add-input 1 "normal" :vec3f))]
      (is (= 2 (count (:inputs with-inputs)))))))

(deftest add-output-test
  (testing "adds builtin output"
    (let [shader (ast/vertex-shader {:inputs [] :outputs [] :body []})
          with-output (comp/add-output shader {:builtin :position} :vec4f)]
      (is (= 1 (count (:outputs with-output))))
      (is (= :position (-> with-output :outputs first :builtin)))))

  (testing "adds location output"
    (let [shader (ast/vertex-shader {:inputs [] :outputs [] :body []})
          with-output (comp/add-output shader {:location 0 :name "color"} :vec3f)]
      (is (= 1 (count (:outputs with-output))))
      (is (= 0 (-> with-output :outputs first :location)))
      (is (= "color" (-> with-output :outputs first :name))))))

(deftest add-statement-test
  (testing "adds statement to end of body"
    (let [shader (ast/vertex-shader
                  {:inputs []
                   :outputs []
                   :body [(ast/let-expr "a" 1.0)]})
          with-stmt (comp/add-statement shader (ast/let-expr "b" 2.0))]
      (is (= 2 (count (:body with-stmt))))
      (is (= [:let "b" 2.0] (last (:body with-stmt))))))

  (testing "preserves existing body"
    (let [shader (ast/vertex-shader
                  {:inputs []
                   :outputs []
                   :body [(ast/let-expr "a" 1.0)
                          (ast/return-expr "a")]})
          with-stmt (comp/add-statement shader (ast/let-expr "b" 2.0))]
      (is (= [:let "a" 1.0] (first (:body with-stmt))))
      (is (= [:return "a"] (second (:body with-stmt))))
      (is (= [:let "b" 2.0] (nth (:body with-stmt) 2))))))

(deftest prepend-statement-test
  (testing "adds statement to beginning of body"
    (let [shader (ast/vertex-shader
                  {:inputs []
                   :outputs []
                   :body [(ast/let-expr "a" 1.0)]})
          with-stmt (comp/prepend-statement shader (ast/let-expr "b" 2.0))]
      (is (= 2 (count (:body with-stmt))))
      (is (= [:let "b" 2.0] (first (:body with-stmt))))
      (is (= [:let "a" 1.0] (second (:body with-stmt)))))))

;;
;; Shader Merging Tests
;;

(deftest merge-shader-bodies-test
  (testing "merges bodies and removes first return"
    (let [body1 [[:let "a" 1.0] [:return "a"]]
          body2 [[:let "b" 2.0] [:return "b"]]
          merged (comp/merge-shader-bodies body1 body2)]
      (is (= 3 (count merged)))
      (is (= [:let "a" 1.0] (first merged)))
      (is (= [:let "b" 2.0] (second merged)))
      (is (= [:return "b"] (last merged)))))

  (testing "handles bodies without returns"
    (let [body1 [[:let "a" 1.0]]
          body2 [[:let "b" 2.0]]
          merged (comp/merge-shader-bodies body1 body2)]
      (is (= 2 (count merged)))
      (is (= [:let "a" 1.0] (first merged)))
      (is (= [:let "b" 2.0] (second merged))))))

(deftest merge-shaders-test
  (testing "merges two vertex shaders"
    (let [shader1 (ast/vertex-shader
                   {:inputs [(ast/input-attribute 0 :vec3f "position")]
                    :outputs [(ast/output-attribute {:builtin :position} :vec4f)]
                    :body [(ast/let-expr "a" 1.0)
                           (ast/return-expr {:position "pos"})]})
          shader2 {:inputs [(ast/input-attribute 1 :vec3f "color")]
                   :outputs [(ast/output-attribute {:location 0 :name "frag_color"} :vec3f)]
                   :uniforms [(ast/uniform-binding 0 0 "mvp" :mat4x4f)]
                   :body [(ast/let-expr "b" 2.0)
                          (ast/return-expr {:position "pos" :frag_color "color"})]}
          merged (comp/merge-shaders shader1 shader2)]
      (is (= :vertex (:stage merged)))
      (is (= 2 (count (:inputs merged))))
      (is (= 2 (count (:outputs merged))))
      (is (= 1 (count (:uniforms merged))))
      (is (= 3 (count (:body merged))))
      ;; Verify first return was removed, second kept
      (is (= [:return {:position "pos" :frag_color "color"}]
             (last (:body merged))))))

  (testing "validates shaders have same stage"
    (let [vert (ast/vertex-shader {:inputs [] :outputs [] :body []})
          frag (ast/fragment-shader {:inputs [] :outputs [] :body []})]
      (is (thrown? js/Error
                   (comp/merge-shaders vert frag))))))

;;
;; Higher-Order Composition Tests
;;

(deftest with-transform-test
  (testing "applies transformation to body"
    (let [shader (ast/vertex-shader
                  {:inputs []
                   :outputs []
                   :body [(ast/let-expr "a" 1.0)]})
          transformed (comp/with-transform shader
                        (fn [body] (conj body (ast/let-expr "b" 2.0))))]
      (is (= 2 (count (:body transformed))))
      (is (= [:let "b" 2.0] (last (:body transformed)))))))

(deftest map-expressions-test
  (testing "maps function over all expressions"
    (let [shader (ast/vertex-shader
                  {:inputs []
                   :outputs []
                   :body [[:let "a" 1.0]
                          [:let "b" 2.0]]})
          mapped (comp/map-expressions shader
                                       (fn [expr]
                                         (if (and (vector? expr) (= :let (first expr)))
                                           (update expr 2 inc)
                                           expr)))]
      (is (= [:let "a" 2.0] (first (:body mapped))))
      (is (= [:let "b" 3.0] (second (:body mapped)))))))

;;
;; Batch Addition Tests
;;

(deftest add-uniforms-test
  (testing "adds multiple uniforms at once"
    (let [shader (ast/vertex-shader {:inputs [] :outputs [] :body []})
          with-uniforms (comp/add-uniforms shader
                                           [[0 0 "mvp" :mat4x4f]
                                            [0 1 "color" :vec4f]
                                            [1 0 "light_dir" :vec3f]])]
      (is (= 3 (count (:uniforms with-uniforms))))
      (is (= "mvp" (-> with-uniforms :uniforms (nth 0) :name)))
      (is (= "color" (-> with-uniforms :uniforms (nth 1) :name)))
      (is (= "light_dir" (-> with-uniforms :uniforms (nth 2) :name))))))

(deftest add-inputs-test
  (testing "adds multiple inputs at once"
    (let [shader (ast/vertex-shader {:inputs [] :outputs [] :body []})
          with-inputs (comp/add-inputs shader
                                       [[0 "position" :vec3f]
                                        [1 "normal" :vec3f]
                                        [2 "uv" :vec2f]])]
      (is (= 3 (count (:inputs with-inputs))))
      (is (= "position" (-> with-inputs :inputs (nth 0) :name)))
      (is (= "normal" (-> with-inputs :inputs (nth 1) :name)))
      (is (= "uv" (-> with-inputs :inputs (nth 2) :name))))))

(deftest add-outputs-test
  (testing "adds multiple outputs at once"
    (let [shader (ast/vertex-shader {:inputs [] :outputs [] :body []})
          with-outputs (comp/add-outputs shader
                                         [[{:builtin :position} :vec4f]
                                          [{:location 0 :name "color"} :vec3f]
                                          [{:location 1 :name "normal"} :vec3f]])]
      (is (= 3 (count (:outputs with-outputs))))
      (is (= :position (-> with-outputs :outputs (nth 0) :builtin)))
      (is (= 0 (-> with-outputs :outputs (nth 1) :location)))
      (is (= 1 (-> with-outputs :outputs (nth 2) :location))))))

(deftest add-statements-test
  (testing "adds multiple statements at once"
    (let [shader (ast/vertex-shader {:inputs [] :outputs [] :body []})
          with-stmts (comp/add-statements shader
                                          [(ast/let-expr "a" 1.0)
                                           (ast/let-expr "b" 2.0)
                                           (ast/let-expr "c" 3.0)])]
      (is (= 3 (count (:body with-stmts))))
      (is (= [:let "a" 1.0] (nth (:body with-stmts) 0)))
      (is (= [:let "b" 2.0] (nth (:body with-stmts) 1)))
      (is (= [:let "c" 3.0] (nth (:body with-stmts) 2))))))

;;
;; Integration Tests - Complex Composition
;;

(deftest complex-composition-test
  (testing "builds shader via composition pipeline"
    (let [base-shader (ast/vertex-shader {:inputs [] :outputs [] :body []})
          composed (-> base-shader
                       (comp/add-input 0 "position" :vec4f)
                       (comp/add-input 1 "color" :vec3f)
                       (comp/add-output {:builtin :position} :vec4f)
                       (comp/add-output {:location 0 :name "frag_color"} :vec3f)
                       (comp/add-uniform 0 0 "mvp" :mat4x4f)
                       (comp/add-statement (ast/let-expr "clip_pos" (ast/mul "mvp" "position")))
                       (comp/add-statement (ast/return-expr {:position "clip_pos" :frag_color "color"})))]
      (is (= 2 (count (:inputs composed))))
      (is (= 2 (count (:outputs composed))))
      (is (= 1 (count (:uniforms composed))))
      (is (= 2 (count (:body composed)))))))

(deftest fragment-composition-test
  (testing "composes lighting fragment shader"
    (let [lighting-fragment {:inputs [(ast/input-attribute 0 :vec3f "normal")]
                             :outputs []
                             :uniforms [(ast/uniform-binding 0 1 "light_dir" :vec3f)]
                             :body [(ast/let-expr "n" (ast/normalize "normal"))
                                    (ast/let-expr "l" (ast/normalize "light_dir"))
                                    (ast/let-expr "diffuse" (ast/max-expr 0.0 (ast/dot "n" "l")))]}
          base-fragment (ast/fragment-shader
                         {:inputs [(ast/input-attribute 1 :vec3f "color")]
                          :outputs [(ast/output-attribute {:location 0 :name "out_color"} :vec4f)]
                          :body [(ast/return-expr (ast/vec4f "color.r" "color.g" "color.b" 1.0))]})
          composed (comp/merge-shaders base-fragment lighting-fragment)]
      (is (= 2 (count (:inputs composed))))
      (is (= 1 (count (:outputs composed))))
      (is (= 1 (count (:uniforms composed))))
      ;; Should have lighting calculations plus return (base return removed)
      (is (= 4 (count (:body composed)))))))
