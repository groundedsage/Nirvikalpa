#!/usr/bin/env clojure
;; Comprehensive shader DSL test suite
;; Tests all v2 DSL features systematically

(require '[nirvikalpa.shader.dsl :as dsl])
(require '[clojure.test :refer [deftest is testing run-tests]])

(defn compiles-without-error? [form]
  (try
    (dsl/compile-body [form])
    true
    (catch Exception e
      (println "Error:" (.getMessage e))
      false)))

(deftest test-if-block
  (testing "if-block compiles to :if-block"
    (let [result (dsl/compile-simple-expr '(if-block test then else))]
      (is (vector? result))
      (is (= :if-block (first result))))))

(deftest test-let-block
  (testing "let-block compiles to :let-block"
    (let [result (dsl/compile-simple-expr '(let-block [x 1.0] x))]
      (is (vector? result))
      (is (= :let-block (first result))))))

(deftest test-var-block
  (testing "var-block compiles to :var-block"
    (let [result (dsl/compile-simple-expr '(var-block res :f32))]
      (is (vector? result))
      (is (= :var-block (first result))))))

(deftest test-assign
  (testing "assign compiles to :assign"
    (let [result (dsl/compile-simple-expr '(assign res 1.0))]
      (is (vector? result))
      (is (= :assign (first result))))))

(deftest test-do-flattens-let
  (testing "do flattens nested let forms"
    (let [result (dsl/compile-body
                   ['(let [x 1.0]
                       (do
                         (var-block res :f32)
                         (let [y 2.0] y)))])]
      ;; Should flatten let bindings into do
      (is (some #(= :let (first %)) result))
      (is (some #(= :do (first %)) result))
      ;; The do should contain flattened let
      (let [do-node (first (filter #(= :do (first %)) result))]
        (is (some #(= :let (first %)) (rest do-node)))))))

(deftest test-if-block-not-wrapped-in-return
  (testing "if-block as last expr not wrapped in :return"
    (let [result (dsl/compile-body ['(if-block test then else)])]
      ;; First element should be if-block, NOT [:return [:if-block ...]]
      (is (= :if-block (first (first result)))))))

(deftest test-complex-bezier-pattern
  (testing "Complex Bezier pattern compiles"
    (is (compiles-without-error?
          '(let [a 1.0 b 2.0 h 3.0]
             (do
               (var-block res :f32)
               (if-block (>= h 0.0)
                 (let-block [x (sqrt h)]
                   (assign res x))
                 (let-block [z (sqrt (- h))]
                   (assign res z)))
               (let [final res]
                 (vec4f final 0.0 0.0 1.0))))))))

(println "========================================")
(println "Running Shader DSL Test Suite")
(println "========================================\n")

(run-tests)

(println "\n========================================")
(println "Test Suite Complete")
(println "========================================")
