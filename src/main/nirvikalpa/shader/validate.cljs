(ns nirvikalpa.shader.validate
  "Shader validation using Malli schemas - CALCULATION layer

   All functions are PURE - they validate data and return validation results.
   No side effects, no mutations.

   This module provides compile-time validation of shader ASTs to catch
   errors before WGSL generation."
  (:require [malli.core :as m]
            [malli.error :as me]
            [nirvikalpa.shader.ast :as ast]))

;;
;; Malli Schemas for Shader AST
;;

(def shader-type-schema
  "Valid WGSL type keywords"
  (into [:enum] ast/all-types))

(def input-attribute-schema
  "Input attribute validation"
  [:map
   [:type shader-type-schema]
   [:name :string]
   [:location {:optional true} :int]
   [:builtin {:optional true} :keyword]])

(def output-attribute-schema
  "Output attribute validation"
  [:map
   [:type shader-type-schema]
   [:location {:optional true} :int]
   [:builtin {:optional true} :keyword]
   [:name {:optional true} :string]])

(def uniform-binding-schema
  "Uniform binding validation"
  [:map
   [:group :int]
   [:binding :int]
   [:type shader-type-schema]
   [:name :string]])

(def expression-schema
  "Expression validation (recursive)"
  [:or
   :string                    ;; Variable reference
   :number                    ;; Literal
   [:vector :any]             ;; Expression vector (recursive)
   [:map [:return :any]]      ;; Return with map
   :keyword])                 ;; Type keywords

(def shader-body-schema
  "Shader body (vector of expressions)"
  [:vector expression-schema])

(def vertex-shader-schema
  "Vertex shader validation"
  [:map
   [:type [:= :shader-module]]
   [:stage [:= :vertex]]
   [:entry-point :string]
   [:inputs [:vector input-attribute-schema]]
   [:outputs [:vector output-attribute-schema]]
   [:uniforms [:vector uniform-binding-schema]]
   [:body shader-body-schema]])

(def fragment-shader-schema
  "Fragment shader validation"
  [:map
   [:type [:= :shader-module]]
   [:stage [:= :fragment]]
   [:entry-point :string]
   [:inputs [:vector input-attribute-schema]]
   [:outputs [:vector output-attribute-schema]]
   [:uniforms [:vector uniform-binding-schema]]
   [:body shader-body-schema]])

(def compute-shader-schema
  "Compute shader validation"
  [:map
   [:type [:= :shader-module]]
   [:stage [:= :compute]]
   [:entry-point :string]
   [:workgroup-size [:tuple :int :int :int]]
   [:uniforms [:vector uniform-binding-schema]]
   [:body shader-body-schema]])

(def shader-module-schema
  "Any shader module"
  [:or
   vertex-shader-schema
   fragment-shader-schema
   compute-shader-schema])

;;
;; Validation Functions
;;

(defn valid-shader?
  "Check if shader AST is valid (pure).

   Returns true if valid, false otherwise.

   Example:
     (valid-shader? my-shader-ast)
     ;; => true"
  [shader]
  (m/validate shader-module-schema shader))

(defn explain-shader
  "Explain why shader is invalid (pure).

   Returns nil if valid, explanation map if invalid.

   Example:
     (explain-shader invalid-shader)
     ;; => {:type :invalid-stage :message \"...\"}"
  [shader]
  (when-not (valid-shader? shader)
    (-> (m/explain shader-module-schema shader)
        (me/humanize))))

(defn validate-shader
  "Validate shader and return detailed result (pure).

   Returns map with :valid? and either :shader or :errors.

   Example:
     (validate-shader my-shader)
     ;; => {:valid? true :shader my-shader}

     (validate-shader bad-shader)
     ;; => {:valid? false :errors {...}}"
  [shader]
  (if (valid-shader? shader)
    {:valid? true :shader shader}
    {:valid? false
     :errors (explain-shader shader)}))

(defn validate!
  "Validate shader and throw on error (not pure - throws).

   Returns shader if valid, throws ex-info if invalid.

   Use this at boundaries where you want to fail fast.

   Example:
     (validate! my-shader)
     ;; => my-shader (if valid)
     ;; throws ex-info if invalid"
  [shader]
  (if (valid-shader? shader)
    shader
    (throw (ex-info "Invalid shader AST"
                    {:errors (explain-shader shader)
                     :shader shader}))))

;;
;; Specific Validation Rules
;;

(defn- has-duplicate-locations?
  "Check if shader has duplicate input/output locations (pure)"
  [shader]
  (let [input-locs (keep :location (:inputs shader))
        output-locs (keep :location (:outputs shader))]
    (or (not= (count input-locs) (count (distinct input-locs)))
        (not= (count output-locs) (count (distinct output-locs))))))

(defn- has-duplicate-bindings?
  "Check if shader has duplicate uniform bindings (pure)"
  [shader]
  (let [bindings (map (juxt :group :binding) (:uniforms shader))]
    (not= (count bindings) (count (distinct bindings)))))

(defn check-duplicates
  "Check for duplicate locations/bindings (pure).

   Returns nil if no duplicates, error map if duplicates found."
  [shader]
  (cond
    (has-duplicate-locations? shader)
    {:error :duplicate-locations
     :message "Shader has duplicate input or output locations"}

    (has-duplicate-bindings? shader)
    {:error :duplicate-bindings
     :message "Shader has duplicate uniform bindings"}

    :else nil))

(defn validate-with-rules
  "Validate shader with additional business rules (pure).

   Combines Malli schema validation with custom rules.

   Example:
     (validate-with-rules my-shader)
     ;; => {:valid? true :shader my-shader}

     (validate-with-rules shader-with-dup-bindings)
     ;; => {:valid? false :errors {:duplicate-bindings \"...\"}}"
  [shader]
  (let [schema-validation (validate-shader shader)]
    (if-not (:valid? schema-validation)
      schema-validation
      (if-let [dup-error (check-duplicates shader)]
        {:valid? false :errors dup-error}
        {:valid? true :shader shader}))))
