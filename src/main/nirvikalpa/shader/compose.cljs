(ns nirvikalpa.shader.compose
  "Shader composition - CALCULATIONS layer

   All functions are PURE - they transform shader AST data without side effects.

   This module provides utilities for composing shader fragments together,
   allowing modular shader construction through data transformation."
  (:require [nirvikalpa.shader.ast :as ast]))

;;
;; Adding Components to Shaders (Pure Transformations)
;;

(defn add-uniform
  "Add uniform binding to shader (pure).

   Returns new shader with uniform appended to :uniforms vector.

   Example:
     (add-uniform shader 0 0 \"mvp\" :mat4x4f)
     ;; Adds {:group 0 :binding 0 :type :mat4x4f :name \"mvp\"}"
  [shader group binding name type]
  (update shader :uniforms conj
          (ast/uniform-binding group binding type name)))

(defn add-input
  "Add input attribute to shader (pure).

   Returns new shader with input appended to :inputs vector.

   Example:
     (add-input shader 1 \"color\" :vec3f)"
  [shader location name type]
  (update shader :inputs conj
          (ast/input-attribute location type name)))

(defn add-output
  "Add output attribute to shader (pure).

   Returns new shader with output appended to :outputs vector.

   Example:
     (add-output shader {:location 0 :name \"color\"} :vec3f)"
  [shader opts type]
  (update shader :outputs conj
          (ast/output-attribute opts type)))

(defn add-statement
  "Add statement to end of shader body (pure).

   Returns new shader with statement appended to :body vector.

   Example:
     (add-statement shader (ast/let-expr \"x\" 1.0))"
  [shader stmt]
  (update shader :body conj stmt))

(defn prepend-statement
  "Add statement to beginning of shader body (pure).

   Returns new shader with statement prepended to :body vector.

   Example:
     (prepend-statement shader (ast/let-expr \"x\" 1.0))"
  [shader stmt]
  (update shader :body #(into [stmt] %)))

;;
;; Merging Shaders (Pure Transformations)
;;

(defn- remove-return-statements
  "Remove return statements from shader body (pure helper)."
  [body]
  (vec (filter #(not= :return (first %)) body)))

(defn merge-shader-bodies
  "Merge two shader bodies, removing duplicate returns (pure).

   Only the return from the second body is kept.

   Example:
     (merge-shader-bodies
       [[:let \"a\" 1.0] [:return \"a\"]]
       [[:let \"b\" 2.0] [:return \"b\"]])
     ;; => [[:let \"a\" 1.0] [:let \"b\" 2.0] [:return \"b\"]]"
  [body1 body2]
  (vec (concat (remove-return-statements body1)
               body2)))

(defn merge-shaders
  "Merge two shader ASTs (pure).

   Combines inputs, outputs, uniforms, and bodies.
   Shaders must have same :type and :stage.

   Example:
     (merge-shaders base-shader lighting-fragment)
     ;; Returns combined shader with all components merged"
  [shader1 shader2]
  {:pre [(= (:type shader1) (:type shader2))
         (= (:stage shader1) (:stage shader2))]}
  {:type (:type shader1)
   :stage (:stage shader1)
   :entry-point (:entry-point shader1)
   :inputs (vec (concat (:inputs shader1) (:inputs shader2)))
   :outputs (vec (concat (:outputs shader1) (:outputs shader2)))
   :uniforms (vec (concat (:uniforms shader1) (:uniforms shader2)))
   :body (merge-shader-bodies (:body shader1) (:body shader2))})

;;
;; Higher-Order Composition (Pure Transformations)
;;

(defn with-transform
  "Apply transformation function to shader body (pure).

   Example:
     (with-transform shader
       (fn [body] (conj body (ast/let-expr \"x\" 1.0))))"
  [shader transform-fn]
  (update shader :body transform-fn))

(defn map-expressions
  "Map function over all expressions in shader body (pure).

   Example:
     (map-expressions shader
       (fn [expr] (if (vector? expr) expr [:comment expr])))"
  [shader f]
  (update shader :body #(mapv f %)))

;;
;; Composition Patterns (Pure Transformations)
;;

(defn add-uniforms
  "Add multiple uniforms to shader at once (pure).

   Takes vector of [group binding name type] tuples.

   Example:
     (add-uniforms shader
       [[0 0 \"mvp\" :mat4x4f]
        [0 1 \"color\" :vec4f]])"
  [shader uniforms]
  (reduce (fn [s [group binding name type]]
            (add-uniform s group binding name type))
          shader
          uniforms))

(defn add-inputs
  "Add multiple inputs to shader at once (pure).

   Takes vector of [location name type] tuples.

   Example:
     (add-inputs shader
       [[0 \"position\" :vec3f]
        [1 \"normal\" :vec3f]])"
  [shader inputs]
  (reduce (fn [s [location name type]]
            (add-input s location name type))
          shader
          inputs))

(defn add-outputs
  "Add multiple outputs to shader at once (pure).

   Takes vector of [opts type] tuples.

   Example:
     (add-outputs shader
       [[{:location 0 :name \"color\"} :vec3f]
        [{:location 1 :name \"normal\"} :vec3f]])"
  [shader outputs]
  (reduce (fn [s [opts type]]
            (add-output s opts type))
          shader
          outputs))

(defn add-statements
  "Add multiple statements to shader body (pure).

   Example:
     (add-statements shader
       [(ast/let-expr \"x\" 1.0)
        (ast/let-expr \"y\" 2.0)])"
  [shader stmts]
  (reduce add-statement shader stmts))

;;
;; Threading Macro Helpers
;;

(defn compose
  "Compose shader with multiple transformations via threading (pure).

   Example:
     (compose base-shader
       (add-uniform 0 0 \"mvp\" :mat4x4f)
       (add-input 0 \"position\" :vec3f)
       (add-statement (ast/let-expr \"x\" 1.0)))"
  [shader & transforms]
  (reduce (fn [s transform-fn] (transform-fn s)) shader transforms))
