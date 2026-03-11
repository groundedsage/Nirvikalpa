(ns nirvikalpa.shader.codegen
  "WGSL code generation from shader AST - CALCULATION layer

   All functions are PURE - they take data and return strings.
   No side effects, no GPU interaction.

   This module compiles shader ASTs (created by nirvikalpa.shader.ast)
   into WGSL source code that can be compiled by WebGPU."
  (:require [clojure.string :as str]
            [nirvikalpa.shader.ast :as ast]
            [nirvikalpa.shader.validate :as validate]))

;;
;; Type → WGSL String
;;

(defn type->wgsl
  "Convert type keyword to WGSL type string (pure).

   Example:
     (type->wgsl :vec3f)   ;; => \"vec3<f32>\"
     (type->wgsl :mat4x4f) ;; => \"mat4x4<f32>\""
  [type]
  (case type
    ;; Scalars
    :f32 "f32"
    :i32 "i32"
    :u32 "u32"
    :bool "bool"

    ;; Float vectors
    :vec2f "vec2<f32>"
    :vec3f "vec3<f32>"
    :vec4f "vec4<f32>"

    ;; Integer vectors
    :vec2i "vec2<i32>"
    :vec3i "vec3<i32>"
    :vec4i "vec4<i32>"

    ;; Unsigned vectors
    :vec2u "vec2<u32>"
    :vec3u "vec3<u32>"
    :vec4u "vec4<u32>"

    ;; Matrices
    :mat2x2f "mat2x2<f32>"
    :mat3x3f "mat3x3<f32>"
    :mat4x4f "mat4x4<f32>"
    :mat2x3f "mat2x3<f32>"
    :mat3x2f "mat3x2<f32>"
    :mat2x4f "mat2x4<f32>"
    :mat4x2f "mat4x2<f32>"
    :mat3x4f "mat3x4<f32>"
    :mat4x3f "mat4x3<f32>"

    ;; Textures
    :texture-2d "texture_2d<f32>"
    :texture-cube "texture_cube<f32>"
    :texture-3d "texture_3d<f32>"
    :texture-2d-array "texture_2d_array<f32>"
    :texture-cube-array "texture_cube_array<f32>"
    :depth-texture-2d "texture_depth_2d"
    :depth-texture-cube "texture_depth_cube"

    ;; Samplers
    :sampler "sampler"
    :sampler-comparison "sampler_comparison"

    ;; Default - throw error
    (throw (ex-info "Unknown WGSL type" {:type type}))))

;;
;; Helper Functions
;;

(declare expr->wgsl)

(defn struct-literal->wgsl
  "Convert struct literal (map) to WGSL constructor call (pure).

   Example:
     (struct-literal->wgsl {:position \"pos\" :color \"col\"})
     ;; => \"OutputStruct(position: pos, color: col)\""
  [m]
  (let [fields (for [[k v] m]
                 (str (name k) ": " (expr->wgsl v)))]
    (str "OutputStruct(" (str/join ", " fields) ")")))

;;
;; Expression → WGSL String
;;

(defn expr->wgsl
  "Convert expression to WGSL string (pure, recursive).

   Handles:
   - Literals (strings, numbers)
   - Vector constructors (:vec2f, :vec3f, :vec4f, etc.)
   - Arithmetic operations (:+, :-, :*, :/)
   - Built-in functions (:dot, :normalize, :max, etc.)
   - Statements (:let, :return)
   - Struct literals (maps)

   Example:
     (expr->wgsl [:vec4f 1.0 0.0 0.0 1.0])
     ;; => \"vec4<f32>(1.0, 0.0, 0.0, 1.0)\""
  [expr]
  (cond
    ;; String literal (variable reference)
    (string? expr)
    expr

    ;; Number literal
    (number? expr)
    (if (integer? expr)
      (str expr ".0")  ; Add .0 to integers for f32 inference in WGSL
      (str expr))  ; Floats already have decimal

    ;; User-defined function call (vector with string first element)
    ;; E.g., ["sdBox" "p" "b"] -> "sdBox(p, b)"
    (and (vector? expr) (string? (first expr)))
    (let [[fn-name & args] expr]
      (str fn-name "(" (str/join ", " (map expr->wgsl args)) ")"))

    ;; Vector expression (built-in operations)
    (and (vector? expr) (keyword? (first expr)))
    (let [[op & args] expr]
      (case op
        ;; Float vector constructors
        :vec2 (str "vec2(" (str/join ", " (map expr->wgsl args)) ")")
        :vec3 (str "vec3(" (str/join ", " (map expr->wgsl args)) ")")
        :vec4 (str "vec4(" (str/join ", " (map expr->wgsl args)) ")")
        :vec2f (str "vec2<f32>(" (str/join ", " (map expr->wgsl args)) ")")
        :vec3f (str "vec3<f32>(" (str/join ", " (map expr->wgsl args)) ")")
        :vec4f (str "vec4<f32>(" (str/join ", " (map expr->wgsl args)) ")")

        ;; Type conversions
        :f32 (str "f32(" (expr->wgsl (first args)) ")")
        :i32 (str "i32(" (expr->wgsl (first args)) ")")
        :u32 (str "u32(" (expr->wgsl (first args)) ")")

        ;; Integer vector constructors
        :vec2i (str "vec2<i32>(" (str/join ", " (map expr->wgsl args)) ")")
        :vec3i (str "vec3<i32>(" (str/join ", " (map expr->wgsl args)) ")")
        :vec4i (str "vec4<i32>(" (str/join ", " (map expr->wgsl args)) ")")

        ;; Unsigned vector constructors
        :vec2u (str "vec2<u32>(" (str/join ", " (map expr->wgsl args)) ")")
        :vec3u (str "vec3<u32>(" (str/join ", " (map expr->wgsl args)) ")")
        :vec4u (str "vec4<u32>(" (str/join ", " (map expr->wgsl args)) ")")

        ;; Arithmetic operations
        :+ (str "(" (str/join " + " (map expr->wgsl args)) ")")
        :- (if (= 1 (count args))
             ;; Unary negation
             (str "-(" (expr->wgsl (first args)) ")")
             ;; Binary subtraction
             (str "(" (str/join " - " (map expr->wgsl args)) ")"))
        :* (str "(" (str/join " * " (map expr->wgsl args)) ")")
        :/ (str "(" (str/join " / " (map expr->wgsl args)) ")")

        ;; Built-in functions - vector operations
        :dot (str "dot(" (str/join ", " (map expr->wgsl args)) ")")
        :cross (str "cross(" (str/join ", " (map expr->wgsl args)) ")")
        :normalize (str "normalize(" (expr->wgsl (first args)) ")")
        :length (str "length(" (expr->wgsl (first args)) ")")
        :distance (str "distance(" (str/join ", " (map expr->wgsl args)) ")")

        ;; Built-in functions - math operations
        :max (str "max(" (str/join ", " (map expr->wgsl args)) ")")
        :min (str "min(" (str/join ", " (map expr->wgsl args)) ")")
        :clamp (str "clamp(" (str/join ", " (map expr->wgsl args)) ")")
        :mix (str "mix(" (str/join ", " (map expr->wgsl args)) ")")
        :pow (str "pow(" (str/join ", " (map expr->wgsl args)) ")")
        :sqrt (str "sqrt(" (expr->wgsl (first args)) ")")
        :abs (str "abs(" (expr->wgsl (first args)) ")")
        :reflect (str "reflect(" (str/join ", " (map expr->wgsl args)) ")")
        :smoothstep (str "smoothstep(" (str/join ", " (map expr->wgsl args)) ")")
        :step (str "step(" (str/join ", " (map expr->wgsl args)) ")")
        :sign (str "sign(" (expr->wgsl (first args)) ")")
        :atan2 (str "atan2(" (str/join ", " (map expr->wgsl args)) ")")
        :floor (str "floor(" (expr->wgsl (first args)) ")")
        :ceil (str "ceil(" (expr->wgsl (first args)) ")")
        :fract (str "fract(" (expr->wgsl (first args)) ")")
        :mod (str "(" (expr->wgsl (first args)) " % " (expr->wgsl (second args)) ")")
        :modulo (str "(" (expr->wgsl (first args)) " % " (expr->wgsl (second args)) ")")
        :cos (str "cos(" (expr->wgsl (first args)) ")")
        :sin (str "sin(" (expr->wgsl (first args)) ")")
        :tan (str "tan(" (expr->wgsl (first args)) ")")
        :atan (str "atan(" (expr->wgsl (first args)) ")")
        :acos (str "acos(" (expr->wgsl (first args)) ")")
        :asin (str "asin(" (expr->wgsl (first args)) ")")

        ;; Derivative functions (for anti-aliasing)
        :dFdx (str "dpdx(" (expr->wgsl (first args)) ")")
        :dFdy (str "dpdy(" (expr->wgsl (first args)) ")")
        :fwidth (str "fwidth(" (expr->wgsl (first args)) ")")

        ;; Comparison operators
        :> (str "(" (str/join " > " (map expr->wgsl args)) ")")
        :< (str "(" (str/join " < " (map expr->wgsl args)) ")")
        :>= (str "(" (str/join " >= " (map expr->wgsl args)) ")")
        :<= (str "(" (str/join " <= " (map expr->wgsl args)) ")")
        :== (str "(" (str/join " == " (map expr->wgsl args)) ")")

        ;; Logical operators
        :and (str "(" (str/join " && " (map expr->wgsl args)) ")")
        :or (str "(" (str/join " || " (map expr->wgsl args)) ")")
        :not (str "!(" (expr->wgsl (first args)) ")")

        ;; Conditional (WGSL uses select, not ternary)
        :if (let [[cond then else] args]
              (str "select(" (expr->wgsl else) ", " (expr->wgsl then) ", " (expr->wgsl cond) ")"))

        ;; If/else statement block (not expression)
        :if-else (let [[cond then-block else-block] args
                       then-stmts (map expr->wgsl then-block)
                       else-stmts (map expr->wgsl else-block)
                       cond-str (expr->wgsl cond)]
                   (if (seq else-stmts)
                     (str "if (" cond-str ") {\n"
                          "    " (str/join "\n    " then-stmts) "\n"
                          "  } else {\n"
                          "    " (str/join "\n    " else-stmts) "\n"
                          "  }")
                     (str "if (" cond-str ") {\n"
                          "    " (str/join "\n    " then-stmts) "\n"
                          "  }")))

        ;; Do block (statement sequence)
        :do (let [stmts (butlast args)
                  last-stmt (last args)
                  stmt-strs (map expr->wgsl stmts)
                  ;; Check if last statement is assign (already complete)
                  last-is-assign? (and (vector? last-stmt) (= :assign (first last-stmt)))
                  last-str (expr->wgsl last-stmt)]
              (str/join "\n  " (concat stmt-strs
                                       [(if last-is-assign?
                                          last-str
                                          (str "return " last-str ";"))])))

        ;; V2 DSL: if-block - block-level conditional with nested let support
        :if-block (let [[test then else] args]
                    (str "if (" (expr->wgsl test) ") {\n"
                         "  " (expr->wgsl then) "\n"
                         "} else {\n"
                         "  " (expr->wgsl else) "\n"
                         "}"))

        ;; V2 DSL: let-block - scoped let bindings
        :let-block (let [[bindings & body] args
                         binding-pairs (partition 2 bindings)
                         var-decls (for [[var init] binding-pairs]
                                     (str "let " var " = " (expr->wgsl init) ";"))
                         body-stmts (map expr->wgsl (butlast body))
                         last-expr (last body)
                         is-assign? (and (vector? last-expr) (= :assign (first last-expr)))
                         ret-expr (expr->wgsl last-expr)]
                     (str "{\n"
                          "  " (str/join "\n  " var-decls) "\n"
                          (when (seq body-stmts)
                            (str "  " (str/join "\n  " body-stmts) "\n"))
                          (if is-assign?
                            (str "  " ret-expr "\n")  ; assign already has semicolon
                            (str "  return " ret-expr ";\n"))
                          "}"))

        ;; V2 DSL: do-block - explicit block for multiple statements
        :do-block (let [stmt-strs (map expr->wgsl args)]
                    (str "{\n"
                         "  " (str/join "\n  " stmt-strs) "\n"
                         "}"))

        ;; V2 DSL: var-block - mutable variable declaration
        :var-block (let [[var-name type] args]
                     (str "var " var-name ": " (type->wgsl type) ";"))

        ;; V2 DSL: assign - assignment to mutable variable
        ;; (already exists as :assign above, but keeping for clarity)

        ;; V2 DSL: for-loop
        :for-loop (let [[[var init condition update] & body] args
                        init-str (expr->wgsl init)
                        cond-str (expr->wgsl condition)
                        update-str (expr->wgsl update)
                        body-stmts (map expr->wgsl body)]
                    (str "for (var " var " = " init-str "; "
                         cond-str "; "
                         var " = " update-str ") {\n"
                         "  " (str/join "\n  " body-stmts) "\n"
                         "}"))

        ;; Texture operations
        :texture-sample (str "textureSample(" (str/join ", " (map expr->wgsl args)) ")")

        ;; Array and indexing
        :array (let [[elem-type size elements] args]
                 (str "array<" (type->wgsl elem-type) ", " size ">("
                      (str/join ", " (map expr->wgsl elements)) ")"))
        :index (let [[array idx] args]
                 (str (expr->wgsl array) "[" (expr->wgsl idx) "]"))

        ;; Statements
        :var (let [var-name (second expr)
                   var-init (nth expr 2)]
               (cond
                 ;; Keyword type annotation: var output: VertexOutput;
                 (keyword? var-init)
                 (str "var " var-name ": " (name var-init) ";")
                 ;; String type annotation: var output: VertexOutput;
                 (string? var-init)
                 (str "var " var-name ": " var-init ";")
                 ;; Value initialization: var output = expr;
                 :else
                 (str "var " var-name " = " (expr->wgsl var-init) ";")))
        :let (str "let " (second expr) " = " (expr->wgsl (nth expr 2)) ";")
        :assign (str (expr->wgsl (second expr)) " = " (expr->wgsl (nth expr 2)) ";")
        :return (str "return " (expr->wgsl (second expr)) ";")

        ;; Default: treat as user-defined function call
        ;; E.g., ["sdBox" "p" "b"] -> "sdBox(p, b)"
        (if (string? op)
          (str op "(" (str/join ", " (map expr->wgsl args)) ")")
          ;; Still unknown - throw error
          (throw (ex-info "Unknown expression operation" {:expr expr :op op})))))

    ;; Map (struct literal)
    (map? expr)
    (struct-literal->wgsl expr)

    ;; Unknown expression type
    :else
    (throw (ex-info "Cannot convert expression to WGSL"
                    {:expr expr
                     :type (type expr)
                     :keys (when (map? expr) (keys expr))
                     :first (when (coll? expr) (first expr))}))))

;;
;; Shader Components → WGSL
;;

(defn input->wgsl
  "Convert input attribute to WGSL parameter (pure).

   Handles both location and builtin inputs.

   Examples:
     (input->wgsl {:location 0 :type :vec3f :name \"position\"})
     ;; => \"@location(0) position: vec3<f32>\"

     (input->wgsl {:builtin :vertex-index :type :u32 :name \"VertexIndex\"})
     ;; => \"@builtin(vertex_index) VertexIndex: u32\""
  [{:keys [location builtin type name]}]
  (cond
    builtin (str "@builtin(" (clojure.string/replace (clojure.core/name builtin) "-" "_") ") "
                 name ": " (type->wgsl type))
    location (str "@location(" location ") "
                  name ": " (type->wgsl type))))

(defn output->wgsl
  "Convert output attribute to WGSL struct field (pure).

   Handles both :builtin and :location outputs.

   Examples:
     (output->wgsl {:builtin :position :type :vec4f})
     ;; => \"@builtin(position) position: vec4<f32>\"

     (output->wgsl {:location 0 :type :vec3f :name \"color\"})
     ;; => \"@location(0) color: vec3<f32>\""
  [{:keys [location builtin type name]}]
  (cond
    builtin (str "@builtin(" (clojure.string/replace (clojure.core/name builtin) "-" "_") ") "
                 (or name (clojure.string/replace (clojure.core/name builtin) "-" "_")) ": " (type->wgsl type))
    location (str "@location(" location ") "
                  name ": " (type->wgsl type))))

(defn output->return-type
  "Convert output attribute to WGSL return type annotation (pure).

   For function return types, we don't include the name.

   Examples:
     (output->return-type {:builtin :position :type :vec4f})
     ;; => \"@builtin(position) vec4<f32>\"

     (output->return-type {:location 0 :type :vec4f})
     ;; => \"@location(0) vec4<f32>\""
  [{:keys [location builtin type]}]
  (cond
    builtin (str "@builtin(" (clojure.string/replace (clojure.core/name builtin) "-" "_") ") "
                 (type->wgsl type))
    location (str "@location(" location ") "
                  (type->wgsl type))))

(defn uniform->wgsl
  "Convert uniform binding to WGSL binding (pure).

   Example:
     (uniform->wgsl {:group 0 :binding 0 :type :mat4x4f :name \"mvp\"})
     ;; => \"@group(0) @binding(0) var<uniform> mvp: mat4x4<f32>;\""
  [{:keys [group binding type name]}]
  (str "@group(" group ") @binding(" binding ") "
       "var<uniform> " name ": " (type->wgsl type) ";"))

(defn inputs->params
  "Convert inputs to function parameters (pure).

   Example:
     (inputs->params shader)
     ;; => \"@location(0) position: vec3<f32>, @location(1) color: vec3<f32>\""
  [shader]
  (->> (:inputs shader)
       (map input->wgsl)
       (str/join ", ")))

(defn outputs->struct
  "Generate output struct definition (pure).

   Example:
     (outputs->struct shader)
     ;; => \"struct OutputStruct {\\n  @builtin(position) position: vec4<f32>,\\n  ...\n}\""
  [shader]
  (let [outputs (:outputs shader)
        fields (map output->wgsl outputs)]
    (str "struct OutputStruct {\n"
         "  " (str/join ",\n  " fields) "\n"
         "}")))

(defn uniforms->bindings
  "Generate uniform bindings (pure).

   Example:
     (uniforms->bindings shader)
     ;; => \"@group(0) @binding(0) var<uniform> mvp: mat4x4<f32>;\\n...\""
  [shader]
  (->> (:uniforms shader)
       (map (fn [uniform]
              (if (:struct? uniform)
                ;; Struct uniform binding
                (str "@group(" (:group uniform) ") @binding(" (:binding uniform) ") "
                     "var<uniform> " (:name uniform) ": " (:type uniform) ";")
                ;; Regular uniform binding
                (uniform->wgsl uniform))))
       (str/join "\n")))

;;
;; Struct Definitions → WGSL
;;

(defn struct-field->wgsl
  "Convert struct field to WGSL field declaration (pure).

   Example:
     (struct-field->wgsl {:name \"position\" :type :vec4f :builtin :position})
     ;; => \"@builtin(position) position: vec4<f32>\""
  [{:keys [name type builtin location]}]
  (let [type-str (if (keyword? type) (type->wgsl type) type)
        attr-str (cond
                   builtin (str "@builtin(" (clojure.string/replace (clojure.core/name builtin) "-" "_") ") ")
                   (some? location) (str "@location(" location ") ")
                   :else "")]
    (str attr-str name ": " type-str)))

(defn struct->wgsl
  "Convert struct definition to WGSL (pure).

   Example:
     (struct->wgsl {:type :struct
                    :name \"VertexOutput\"
                    :fields [{:name \"Position\" :type :vec4f :builtin :position}
                             {:name \"fragUV\" :type :vec2f :location 0}]})
     ;; => \"struct VertexOutput {\\n  @builtin(position) Position: vec4<f32>,\\n  @location(0) fragUV: vec2<f32>\\n}\""
  [{:keys [name fields]}]
  (let [field-strs (map struct-field->wgsl fields)]
    (str "struct " name " {\n"
         "  " (str/join ",\n  " field-strs) "\n"
         "}")))

(defn structs->wgsl
  "Convert all struct definitions to WGSL (pure).

   Example:
     (structs->wgsl shader)
     ;; => \"struct Uniforms { ... }\\n\\nstruct VertexOutput { ... }\""
  [shader]
  (when-let [structs (seq (:structs shader))]
    (->> structs
         (map struct->wgsl)
         (str/join "\n\n"))))

;;
;; Full Shader → WGSL
;;

(defn ast->wgsl
  "Convert complete shader AST to WGSL code (pure).

   This is the MAIN ENTRY POINT for code generation.

   Takes a shader AST (from nirvikalpa.shader.ast) and generates
   complete, valid WGSL source code.

   Example:
     (ast->wgsl my-shader-ast)
     ;; => \"// Generated WGSL shader\\n\\n@vertex\\nfn main(...) { ... }\""
  [shader]
  {:pre [(= :shader-module (:type shader))]}

  (let [stage (:stage shader)
        entry-point (:entry-point shader)
        outputs (:outputs shader)

        ;; Determine if we need OutputStruct or can use simple return
        needs-struct? (and (= :vertex stage)
                           (or (> (count outputs) 1)
                               (and (= 1 (count outputs))
                                    (:location (first outputs)))))

        ;; Generate components
        struct-section (when-let [s (structs->wgsl shader)]
                         (str s "\n\n"))
        uniform-section (when (seq (:uniforms shader))
                          (str (uniforms->bindings shader) "\n\n"))
        output-struct (when needs-struct?
                        (str (outputs->struct shader) "\n\n"))
        stage-attr (case stage
                     :vertex "@vertex"
                     :fragment "@fragment"
                     :compute (str "@compute @workgroup_size("
                                   (str/join ", " (:workgroup-size shader))
                                   ")"))
        params (inputs->params shader)
        return-type (cond
                      ;; Explicit return type (for struct returns from macro)
                      (:return-type shader) (:return-type shader)
                      ;; Auto-generated OutputStruct
                      (and (= :vertex stage) needs-struct?) "OutputStruct"
                      ;; Single output
                      (and (= :vertex stage) (seq outputs)) (output->return-type (first outputs))
                      (and (= :fragment stage) (seq outputs)) (output->return-type (first outputs))
                      ;; No outputs
                      :else "void")
        body-stmts (->> (:body shader)
                        (map expr->wgsl)
                        (map #(str "  " %))
                        (str/join "\n"))]

    (str "// Generated WGSL shader\n\n"
         (or struct-section "")
         (or uniform-section "")
         (or output-struct "")
         stage-attr "\n"
         "fn " entry-point "(" params ") -> " return-type " {\n"
         body-stmts "\n"
         "}")))

(defn function-def->wgsl
  "Convert function definition to WGSL (pure).

   Input: {:name \"sdBox\"
           :params [{:name \"p\" :type :vec2f} {:name \"b\" :type :vec2f}]
           :return-type :f32
           :body [...compiled-expressions...]}

   Output: \"fn sdBox(p: vec2<f32>, b: vec2<f32>) -> f32 { ... }\""
  [{:keys [name params return-type body]}]
  (let [;; Generate parameter list
        param-strs (map (fn [{:keys [name type]}]
                          (str name ": " (type->wgsl type)))
                        params)
        params-str (str/join ", " param-strs)

        ;; Convert body expressions to WGSL statements
        body-stmts (->> body
                        (map expr->wgsl)
                        (map #(str "  " %))
                        (str/join "\n"))

        ;; Generate function
        return-type-str (type->wgsl return-type)]

    (str "fn " name "(" params-str ") -> " return-type-str " {\n"
         body-stmts "\n"
         "}")))
