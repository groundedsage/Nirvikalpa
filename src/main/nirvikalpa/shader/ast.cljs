(ns nirvikalpa.shader.ast
  "Shader Abstract Syntax Tree - DATA layer

   Represents shaders as Clojure data structures (not strings).
   All functions are PURE - they create and return data, no side effects.

   This module provides the foundation for the Nirvikalpa shader DSL,
   allowing shaders to be represented, inspected, and manipulated as
   pure Clojure data before being compiled to WGSL.")

;;
;; Type System (Pure Data)
;;

(def scalar-types
  "WGSL scalar types"
  #{:f32 :i32 :u32 :bool})

(def vector-types
  "WGSL vector types"
  #{:vec2f :vec3f :vec4f
    :vec2i :vec3i :vec4i
    :vec2u :vec3u :vec4u})

(def matrix-types
  "WGSL matrix types"
  #{:mat2x2f :mat3x3f :mat4x4f
    :mat2x3f :mat3x2f
    :mat2x4f :mat4x2f
    :mat3x4f :mat4x3f})

(def texture-types
  "WGSL texture types"
  #{:texture-2d :texture-cube :texture-3d
    :texture-2d-array :texture-cube-array
    :depth-texture-2d :depth-texture-cube})

(def sampler-types
  "WGSL sampler types"
  #{:sampler :sampler-comparison})

(def all-types
  "All valid WGSL types"
  (into #{} (concat scalar-types vector-types matrix-types
                    texture-types sampler-types)))

(defn valid-type?
  "Check if type is a valid WGSL type.

   Example:
     (valid-type? :vec3f)  ;; => true
     (valid-type? :foo)    ;; => false"
  [t]
  (contains? all-types t))

;;
;; Expression Builders (Pure Functions → Data)
;;

(defn vec2f
  "Create vec2<f32> expression.

   Example:
     (vec2f 1.0 0.0)
     ;; => [:vec2f 1.0 0.0]"
  [x y]
  [:vec2f x y])

(defn vec3f
  "Create vec3<f32> expression.

   Example:
     (vec3f 1.0 0.0 0.0)
     ;; => [:vec3f 1.0 0.0 0.0]"
  [x y z]
  [:vec3f x y z])

(defn vec4f
  "Create vec4<f32> expression.

   Example:
     (vec4f 1.0 0.0 0.0 1.0)
     ;; => [:vec4f 1.0 0.0 0.0 1.0]"
  [x y z w]
  [:vec4f x y z w])

(defn vec2i
  "Create vec2<i32> expression"
  [x y]
  [:vec2i x y])

(defn vec3i
  "Create vec3<i32> expression"
  [x y z]
  [:vec3i x y z])

(defn vec4i
  "Create vec4<i32> expression"
  [x y z w]
  [:vec4i x y z w])

(defn vec2u
  "Create vec2<u32> expression"
  [x y]
  [:vec2u x y])

(defn vec3u
  "Create vec3<u32> expression"
  [x y z]
  [:vec3u x y z])

(defn vec4u
  "Create vec4<u32> expression"
  [x y z w]
  [:vec4u x y z w])

;;
;; Arithmetic Operations
;;

(defn add
  "Create addition expression.

   Example:
     (add \"a\" \"b\" \"c\")
     ;; => [:+ \"a\" \"b\" \"c\"]"
  [& args]
  (into [:+] args))

(defn sub
  "Create subtraction expression.

   Can be used as unary negation with single argument:
     (sub \"x\")     ;; => [:- \"x\"]    (negation)
     (sub \"a\" \"b\") ;; => [:- \"a\" \"b\"] (subtraction)"
  [& args]
  (into [:-] args))

(defn mul
  "Create multiplication expression"
  [& args]
  (into [:*] args))

(defn div
  "Create division expression"
  [& args]
  (into [:/] args))

;;
;; Built-in Functions
;;

(defn dot
  "Create dot product expression"
  [a b]
  [:dot a b])

(defn cross
  "Create cross product expression"
  [a b]
  [:cross a b])

(defn normalize
  "Create normalize expression"
  [v]
  [:normalize v])

(defn length
  "Create length expression"
  [v]
  [:length v])

(defn max-expr
  "Create max expression"
  [a b]
  [:max a b])

(defn min-expr
  "Create min expression"
  [a b]
  [:min a b])

(defn clamp-expr
  "Create clamp expression"
  [value min-val max-val]
  [:clamp value min-val max-val])

(defn mix
  "Create mix (linear interpolation) expression"
  [a b factor]
  [:mix a b factor])

(defn pow
  "Create power expression"
  [base exponent]
  [:pow base exponent])

(defn sqrt
  "Create square root expression"
  [v]
  [:sqrt v])

(defn reflect
  "Create reflect expression (for specular lighting)"
  [incident normal]
  [:reflect incident normal])

(defn abs-expr
  "Create abs expression"
  [v]
  [:abs v])

(defn distance-expr
  "Create distance expression"
  [a b]
  [:distance a b])

;;
;; Derivative Functions (for anti-aliasing)
;;

(defn dFdx
  "Create dFdx expression (derivative in x direction).

   Used for analytical anti-aliasing and fwidth calculation.

   Example:
     (dFdx \"dist\")
     ;; => [:dFdx \"dist\"]"
  [expr]
  [:dFdx expr])

(defn dFdy
  "Create dFdy expression (derivative in y direction).

   Used for analytical anti-aliasing and fwidth calculation.

   Example:
     (dFdy \"dist\")
     ;; => [:dFdy \"dist\"]"
  [expr]
  [:dFdy expr])

(defn fwidth
  "Create fwidth expression (sum of absolute derivatives).

   fwidth(x) = abs(dFdx(x)) + abs(dFdy(x))

   This is the standard way to achieve device-independent anti-aliasing
   for SDF-based rendering. It automatically adapts to screen pixel density.

   Example:
     (fwidth \"dist\")
     ;; => [:fwidth \"dist\"]"
  [expr]
  [:fwidth expr])

;;
;; Comparison and Logical Operations
;;

(defn gt
  "Create greater-than expression"
  [a b]
  [:> a b])

(defn lt
  "Create less-than expression"
  [a b]
  [:< a b])

(defn gte
  "Create greater-than-or-equal expression"
  [a b]
  [:>= a b])

(defn lte
  "Create less-than-or-equal expression"
  [a b]
  [:<= a b])

(defn eq
  "Create equality expression"
  [a b]
  [:== a b])

(defn and-expr
  "Create logical AND expression"
  [& args]
  (into [:and] args))

(defn or-expr
  "Create logical OR expression"
  [& args]
  (into [:or] args))

(defn not-expr
  "Create logical NOT expression"
  [v]
  [:not v])

(defn if-expr
  "Create conditional expression (ternary).

   For single-expression conditionals.
   Use if-else-block for multi-statement conditionals."
  [condition then-expr else-expr]
  [:if condition then-expr else-expr])

(defn if-else-block
  "Create if/else statement block with multiple statements.

   Unlike if-expr (ternary), this supports multiple statements in each branch.

   Example:
     (if-else-block (gt \"x\" 0.0)
       [(assign-expr \"color\" (vec3f 1.0 0.0 0.0))
        (assign-expr \"intensity\" 1.0)]
       [(assign-expr \"color\" (vec3f 0.0 0.0 1.0))
        (assign-expr \"intensity\" 0.5)])
     ;; => [:if-else (gt \"x\" 0.0) [...then-stmts...] [...else-stmts...]]"
  ([condition then-block]
   [:if-else condition then-block []])
  ([condition then-block else-block]
   [:if-else condition then-block else-block]))

;;
;; Texture Operations
;;

(defn texture-sample
  "Create texture sampling expression.

   Example:
     (texture-sample \"my_texture\" \"my_sampler\" \"uv\")
     ;; => [:texture-sample \"my_texture\" \"my_sampler\" \"uv\"]"
  [texture sampler coords]
  [:texture-sample texture sampler coords])

;;
;; Array and Indexing
;;

(defn array-expr
  "Create array expression.

   Example:
     (array-expr :vec2f 3 [(vec2f 0.0 0.5) (vec2f -0.5 -0.5) (vec2f 0.5 -0.5)])
     ;; => [:array :vec2f 3 [[:vec2f 0.0 0.5] [:vec2f -0.5 -0.5] [:vec2f 0.5 -0.5]]]"
  [elem-type size elements]
  [:array elem-type size elements])

(defn index-expr
  "Create array indexing expression.

   Example:
     (index-expr \"pos\" \"VertexIndex\")
     ;; => [:index \"pos\" \"VertexIndex\"]"
  [array idx]
  [:index array idx])

;;
;; Statements
;;

(defn var-expr
  "Create var declaration (mutable variable).

   Can specify just type (no initialization) or provide initial value.

   Examples:
     (var-expr \"count\" 0)
     ;; => [:var \"count\" 0]

     (var-expr \"output\" :VertexOutput)
     ;; => [:var \"output\" :VertexOutput]"
  ([name value]
   [:var name value]))

(defn let-expr
  "Create let binding (immutable variable declaration).

   Example:
     (let-expr \"color\" (vec3f 1.0 0.0 0.0))
     ;; => [:let \"color\" [:vec3f 1.0 0.0 0.0]]"
  [name value]
  [:let name value])

(defn return-expr
  "Create return expression.

   Can return simple values or struct literals (maps).

   Examples:
     (return-expr \"x\")
     ;; => [:return \"x\"]

     (return-expr {:position \"pos\" :color \"col\"})
     ;; => [:return {:position \"pos\" :color \"col\"}]"
  [value]
  [:return value])

(defn assign-expr
  "Create assignment expression (for mutable variables).

   Example:
     (assign-expr \"output.Position\" \"pos\")
     ;; => [:assign \"output.Position\" \"pos\"]"
  [target value]
  [:assign target value])

(defn do-block
  "Create a sequence of statements (like progn/begin).

   Useful for grouping multiple statements together.

   Example:
     (do-block
       [(var-expr \"temp\" 0.5)
        (assign-expr \"color.r\" \"temp\")
        (assign-expr \"color.g\" \"temp\")])
     ;; => [:do [[:var \"temp\" 0.5]
     ;;          [:assign \"color.r\" \"temp\"]
     ;;          [:assign \"color.g\" \"temp\"]]]"
  [statements]
  [:do statements])

;;
;; Shader Attributes
;;

(defn input-attribute
  "Create input attribute (pure data).

   Can create location-based or builtin-based inputs.

   For location-based:
     (input-attribute 0 :vec3f \"position\")
     ;; => {:location 0 :type :vec3f :name \"position\"}

   For builtin-based:
     (input-attribute {:builtin :vertex-index} :u32 \"VertexIndex\")
     ;; => {:builtin :vertex-index :type :u32 :name \"VertexIndex\"}"
  [location type name]
  (if (map? location)
    ;; Builtin input - location is actually opts map
    (merge {:type type :name name} location)
    ;; Location input - location is number
    {:location location
     :type type
     :name name}))

(defn output-attribute
  "Create output attribute (pure data).

   Can have :location or :builtin (e.g., :position).

   Examples:
     (output-attribute {:builtin :position} :vec4f)
     (output-attribute {:location 0} :vec3f \"color\")"
  [opts type]
  (merge {:type type} opts))

(defn uniform-binding
  "Create uniform binding (pure data).

   Args:
     group   - Bind group index
     binding - Binding index within group
     type    - WGSL type
     name    - Variable name

   Example:
     (uniform-binding 0 0 :mat4x4f \"mvp\")
     ;; => {:group 0 :binding 0 :type :mat4x4f :name \"mvp\"}"
  [group binding type name]
  {:pre [(nat-int? group)
         (nat-int? binding)
         (valid-type? type)
         (string? name)]}
  {:group group
   :binding binding
   :type type
   :name name})

;;
;; Struct Definitions
;;

(defn struct-field
  "Create struct field definition (pure data).

   Example:
     (struct-field \"position\" :vec4f)
     ;; => {:name \"position\" :type :vec4f}

     (struct-field \"fragUV\" :vec2f {:location 0})
     ;; => {:name \"fragUV\" :type :vec2f :location 0}"
  ([name type]
   {:name name :type type})
  ([name type attrs]
   (merge {:name name :type type} attrs)))

(defn struct-def
  "Create struct definition (pure data).

   Example:
     (struct-def \"VertexOutput\"
       [(struct-field \"Position\" :vec4f {:builtin :position})
        (struct-field \"fragUV\" :vec2f {:location 0})
        (struct-field \"fragPosition\" :vec4f {:location 1})])
     ;; => {:type :struct
     ;;     :name \"VertexOutput\"
     ;;     :fields [{:name \"Position\" :type :vec4f :builtin :position}
     ;;              {:name \"fragUV\" :type :vec2f :location 0}
     ;;              {:name \"fragPosition\" :type :vec4f :location 1}]}"
  [name fields]
  {:pre [(string? name)
         (vector? fields)]}
  {:type :struct
   :name name
   :fields fields})

(defn uniform-struct-binding
  "Create uniform binding for a struct type (pure data).

   Example:
     (uniform-struct-binding 0 0 \"Uniforms\" \"uniforms\")
     ;; => {:group 0 :binding 0 :type \"Uniforms\" :name \"uniforms\" :struct? true}"
  [group binding struct-type-name var-name]
  {:pre [(nat-int? group)
         (nat-int? binding)
         (string? struct-type-name)
         (string? var-name)]}
  {:group group
   :binding binding
   :type struct-type-name
   :name var-name
   :struct? true})

;;
;; Shader Constructors
;;

(defn vertex-shader
  "Create vertex shader module (pure data).

   Takes a map with:
     :inputs   - Vector of input attributes
     :outputs  - Vector of output attributes
     :uniforms - Vector of uniform bindings (optional)
     :structs  - Vector of struct definitions (optional)
     :body     - Vector of expressions

   Returns: Shader AST as Clojure data

   Example:
     (vertex-shader
       {:inputs [(input-attribute 0 :vec3f \"position\")]
        :outputs [(output-attribute {:builtin :position} :vec4f)]
        :body [(let-expr \"pos\" (vec4f \"position.x\" \"position.y\" \"position.z\" 1.0))
               (return-expr {:position \"pos\"})]})"
  [{:keys [inputs outputs uniforms structs body return-type]}]
  {:pre [(vector? inputs)
         (vector? outputs)
         (vector? body)]}
  (cond-> {:type :shader-module
           :stage :vertex
           :entry-point "main"
           :inputs inputs
           :outputs outputs
           :uniforms (or uniforms [])
           :structs (or structs [])
           :body body}
    return-type (assoc :return-type return-type)))

(defn fragment-shader
  "Create fragment shader module (pure data).

   Similar to vertex-shader but for fragment stage.

   Example:
     (fragment-shader
       {:inputs [(input-attribute 0 :vec3f \"color\")]
        :outputs [(output-attribute {:location 0} :vec4f \"out_color\")]
        :body [(return-expr (vec4f \"color.r\" \"color.g\" \"color.b\" 1.0))]})"
  [{:keys [inputs outputs uniforms structs body]}]
  {:pre [(vector? inputs)
         (vector? outputs)
         (vector? body)]}
  {:type :shader-module
   :stage :fragment
   :entry-point "main"
   :inputs inputs
   :outputs outputs
   :uniforms (or uniforms [])
   :structs (or structs [])
   :body body})

(defn compute-shader
  "Create compute shader module (pure data).

   Args:
     :workgroup-size - [x y z] workgroup dimensions
     :uniforms       - Vector of uniform bindings
     :body           - Vector of expressions

   Example:
     (compute-shader
       {:workgroup-size [256 1 1]
        :uniforms [(uniform-binding 0 0 :f32 \"delta_time\")]
        :body [...]})"
  [{:keys [workgroup-size uniforms body]}]
  {:pre [(vector? workgroup-size)
         (= 3 (count workgroup-size))
         (vector? body)]}
  {:type :shader-module
   :stage :compute
   :entry-point "main"
   :workgroup-size workgroup-size
   :uniforms (or uniforms [])
   :body body})
