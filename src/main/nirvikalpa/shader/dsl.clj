(ns nirvikalpa.shader.dsl
  "Shader DSL macros - User-facing syntax layer

   This namespace provides the defshader macro family for writing shaders
   with natural Clojure-like syntax that compiles to shader AST.

   Macros defined here are CLOJURE macros (not ClojureScript) that run
   at compile-time to generate ClojureScript code."
  (:require [clojure.walk :as walk]))

;;
;; Helpers for Macro Implementation (Pure Functions)
;;

(defn parse-params
  "Parse parameter vector into input attributes.

   Input: [position :vec4f uv :vec2f]
   Output: [(ast/input-attribute 0 :vec4f \"position\")
            (ast/input-attribute 1 :vec2f \"uv\")]"
  [params]
  (let [pairs (partition 2 params)]
    (vec (map-indexed
          (fn [idx [param-name param-type]]
            ;; Convert hyphens to underscores in parameter names
            `(nirvikalpa.shader.ast/input-attribute ~idx ~param-type
                                                    ~(clojure.string/replace (name param-name) "-" "_")))
          pairs))))

(defn extract-metadata-opts
  "Extract metadata options from body.

   Returns: {:opts {...} :body [...]}

   Recognizes:
   - :builtin keyword → input attribute modifier
   - :uniform [...] → uniform declarations
   - :output [...] → output declarations
   - :struct [...] → struct definitions"
  [body]
  (loop [remaining body
         opts {}]
    (if (keyword? (first remaining))
      ;; Found metadata keyword
      (let [k (first remaining)
            v (second remaining)]
        (recur (drop 2 remaining)
               (assoc opts k v)))
      ;; No more metadata, rest is body
      {:opts opts :body remaining})))

(declare compile-simple-expr)

(defn compile-let-form
  "Compile let form to shader let expressions.

   (let [a expr1 b expr2] body)
   →
   [[:let \"a\" compiled-expr1]
    [:let \"b\" compiled-expr2]
    ...compiled-body...]"
  [let-form]
  (let [[_ bindings & body] let-form
        binding-pairs (partition 2 bindings)
        let-exprs (mapv (fn [[name expr]]
                          ;; Convert binding name: hyphen to underscore
                          [:let (clojure.string/replace (clojure.core/name name) "-" "_")
                           (compile-simple-expr expr)])
                        binding-pairs)
        compiled-body (mapv compile-simple-expr body)]
    (vec (concat let-exprs compiled-body))))

(defn compile-simple-expr
  "Compile simple expression to AST form.

   Handles:
   - Numbers → stay as numbers
   - Symbols → convert to strings (variable references)
   - Lists → convert based on first element
     - (vec4f ...) → [:vec4f ...]
     - (+ ...) → [:+ ...]
     - (let ...) → compile let form
   - Vectors → stay as vectors
   - Maps → stay as maps (for struct returns)"
  [expr]
  (cond
    ;; Numbers pass through
    (number? expr)
    expr

    ;; Symbols become strings (handles field access like pos.x)
    ;; Convert hyphens to underscores (WGSL doesn't allow hyphens)
    (symbol? expr)
    (clojure.string/replace (str expr) "-" "_")

    ;; V2 DSL forms - MUST come before generic vector handler
    (and (vector? expr) (keyword? (first expr))
         (#{:if-block :let-block :do-block :var-block :assign} (first expr)))
    (let [op (first expr)]
      (case op
        :if-block (let [[_ test then else] expr]
                    [:if-block (compile-simple-expr test)
                     (compile-simple-expr then)
                     (compile-simple-expr else)])
        :let-block (let [[_ bindings & body] expr
                         binding-pairs (partition 2 bindings)
                         compiled-bindings (vec (apply concat
                                                       (for [[var init] binding-pairs]
                                                         [var (compile-simple-expr init)])))
                         compiled-body (mapv compile-simple-expr body)]
                     (into [:let-block compiled-bindings] compiled-body))
        :do-block (into [:do-block] (map compile-simple-expr (rest expr)))
        :var-block (let [[_ var-name type] expr]
                     [:var-block var-name type])
        :assign (let [[_ var-name value] expr]
                  [:assign var-name (compile-simple-expr value)])))

    ;; Vectors pass through (might contain expressions)
    (vector? expr)
    (mapv compile-simple-expr expr)

    ;; Maps pass through (for struct returns)
    (map? expr)
    (into {} (map (fn [[k v]] [k (compile-simple-expr v)]) expr))

    ;; Lists are function calls
    (list? expr)
    (let [[op & args] expr]
      (case op
        ;; Special forms
        let (compile-let-form expr)

        ;; Regular function calls → AST operations
        ;; Vector constructors
        vec2f (into [:vec2f] (map compile-simple-expr args))
        vec3f (into [:vec3f] (map compile-simple-expr args))
        vec4f (into [:vec4f] (map compile-simple-expr args))
        vec4  (into [:vec4] (map compile-simple-expr args))
        f32   (into [:f32] (map compile-simple-expr args))
        i32   (into [:i32] (map compile-simple-expr args))
        u32   (into [:u32] (map compile-simple-expr args))

        ;; Arithmetic
        + (into [:+] (map compile-simple-expr args))
        - (into [:-] (map compile-simple-expr args))
        * (into [:*] (map compile-simple-expr args))
        / (into [:/] (map compile-simple-expr args))

        ;; Builtins
        dot (into [:dot] (map compile-simple-expr args))
        normalize (into [:normalize] (map compile-simple-expr args))
        max (into [:max] (map compile-simple-expr args))
        min (into [:min] (map compile-simple-expr args))
        clamp (into [:clamp] (map compile-simple-expr args))
        abs (into [:abs] (map compile-simple-expr args))
        distance (into [:distance] (map compile-simple-expr args))
        smoothstep (into [:smoothstep] (map compile-simple-expr args))
        sqrt (into [:sqrt] (map compile-simple-expr args))
        sign (into [:sign] (map compile-simple-expr args))
        atan2 (into [:atan2] (map compile-simple-expr args))
        floor (into [:floor] (map compile-simple-expr args))
        ceil (into [:ceil] (map compile-simple-expr args))
        fract (into [:fract] (map compile-simple-expr args))
        mix (into [:mix] (map compile-simple-expr args))
        length (into [:length] (map compile-simple-expr args))
        modulo (into [:modulo] (map compile-simple-expr args))
        cos (into [:cos] (map compile-simple-expr args))
        sin (into [:sin] (map compile-simple-expr args))
        tan (into [:tan] (map compile-simple-expr args))
        atan (into [:atan] (map compile-simple-expr args))
        acos (into [:acos] (map compile-simple-expr args))
        asin (into [:asin] (map compile-simple-expr args))
        pow (into [:pow] (map compile-simple-expr args))

        ;; Derivative functions (for anti-aliasing)
        dFdx (into [:dFdx] (map compile-simple-expr args))
        dFdy (into [:dFdy] (map compile-simple-expr args))
        fwidth (into [:fwidth] (map compile-simple-expr args))

        ;; Comparison operators
        > (into [:>] (map compile-simple-expr args))
        < (into [:<] (map compile-simple-expr args))
        >= (into [:>=] (map compile-simple-expr args))
        <= (into [:<=] (map compile-simple-expr args))
        = (into [:==] (map compile-simple-expr args))

        ;; Logical operators
        and (into [:and] (map compile-simple-expr args))
        or (into [:or] (map compile-simple-expr args))
        not (into [:not] (map compile-simple-expr args))

        ;; Conditional - distinguish between expression (ternary) and block (if/else)
        if (let [[condition then-expr else-expr] args]
             ;; Compile branches to detect if they produce multiple statements
             (let [;; Check if expression is a do block
                   then-do? (and (list? then-expr) (= 'do (first then-expr)))
                   else-do? (and (list? else-expr) (= 'do (first else-expr)))

                   ;; Check if expression is a let form (will produce vector of statements)
                   then-let? (and (list? then-expr) (= 'let (first then-expr)))
                   else-let? (and (list? else-expr) (= 'let (first else-expr)))

                   ;; Use block form if either branch is do or let
                   use-block-form? (or then-do? else-do? then-let? else-let?)]

               (if use-block-form?
                 ;; if-else block statement
                 (let [then-stmts (cond
                                    ;; do block: compile each statement
                                    then-do? (mapv compile-simple-expr (rest then-expr))
                                    ;; let form: returns vector of statements
                                    then-let? (compile-let-form then-expr)
                                    ;; single expression: wrap in vector
                                    :else [(compile-simple-expr then-expr)])
                       else-stmts (cond
                                    ;; no else clause
                                    (nil? else-expr) []
                                    ;; do block: compile each statement
                                    else-do? (mapv compile-simple-expr (rest else-expr))
                                    ;; let form: returns vector of statements
                                    else-let? (compile-let-form else-expr)
                                    ;; single expression: wrap in vector
                                    :else [(compile-simple-expr else-expr)])]
                   [:if-else (compile-simple-expr condition) then-stmts else-stmts])

                 ;; Simple ternary expression
                 [:if (compile-simple-expr condition)
                  (compile-simple-expr then-expr)
                  (compile-simple-expr else-expr)])))

        ;; Do block - statement sequencing
        do (into [:do] (mapcat (fn [arg]
                                 (if (and (list? arg) (= 'let (first arg)))
                                   (compile-let-form arg)  ; Returns vector of statements - flatten
                                   [(compile-simple-expr arg)]))  ; Single statement - wrap
                               args))

        ;; Array operations
        array (let [[type & elements] args]
                [:array type (count elements) (mapv compile-simple-expr elements)])
        get (into [:index] (map compile-simple-expr args))

        ;; Variable declaration
        var (let [[var-name var-type] args]
              (cond
                ;; Keyword type annotation: (var output :VertexOutput) -> [:var "output" :VertexOutput]
                (keyword? var-type)
                [:var (clojure.core/name var-name) var-type]
                ;; Symbol type annotation: (var output VertexOutput) -> [:var "output" "VertexOutput"]
                (symbol? var-type)
                [:var (clojure.core/name var-name) (clojure.core/name var-type)]
                ;; String type annotation: (var output "VertexOutput") -> [:var "output" "VertexOutput"]
                (string? var-type)
                [:var (clojure.core/name var-name) var-type]
                ;; Value initialization: (var count 0) -> [:var "count" 0]
                :else
                [:var (clojure.core/name var-name) (compile-simple-expr var-type)]))

        ;; Assignment (for struct fields)
        set! (let [[target value] args]
               [:assign (compile-simple-expr target) (compile-simple-expr value)])

        ;; V2 DSL forms (as lists before macro expansion)
        if-block (let [[test then else] args]
                   [:if-block (compile-simple-expr test)
                    (compile-simple-expr then)
                    (compile-simple-expr else)])
        let-block (let [[bindings & body] args
                        binding-pairs (partition 2 bindings)
                        compiled-bindings (vec (apply concat
                                                      (for [[var init] binding-pairs]
                                                        [(clojure.string/replace (clojure.core/name var) "-" "_")
                                                         (compile-simple-expr init)])))
                        compiled-body (mapv compile-simple-expr body)]
                    (into [:let-block compiled-bindings] compiled-body))
        do-block (into [:do-block] (map compile-simple-expr args))
        var-block (let [[var-name type] args]
                    [:var-block (clojure.string/replace (clojure.core/name var-name) "-" "_") type])
        assign (let [[var-name value] args]
                 [:assign (clojure.string/replace (clojure.core/name var-name) "-" "_")
                  (compile-simple-expr value)])

        ;; For loop
        for-loop (let [[[var init condition update] & body] args
                       compiled-body (vec (mapcat (fn [expr]
                                                    (if (and (list? expr) (= 'let (first expr)))
                                                      (compile-let-form expr)
                                                      [(compile-simple-expr expr)]))
                                                  body))]
                   (into [:for-loop
                          [(clojure.string/replace (clojure.core/name var) "-" "_")
                           (compile-simple-expr init)
                           (compile-simple-expr condition)
                           (compile-simple-expr update)]]
                         compiled-body))

        ;; Default: preserve as-is with compiled args
        (into [(compile-simple-expr op)] (map compile-simple-expr args))))

    ;; Other forms pass through
    :else
    expr))

(defn compile-body
  "Compile shader body expressions to AST.

   The last expression becomes the return statement.
   Earlier expressions are let bindings or statements."
  [body-exprs]
  (if (= 1 (count body-exprs))
    ;; Single expression
    (let [expr (first body-exprs)]
      (cond
        ;; Let form - compile and extract return from inner body
        (and (list? expr) (= 'let (first expr)))
        (let [compiled-let (compile-let-form expr)
              ret-expr (last compiled-let)
              let-bindings (butlast compiled-let)
              ;; Check if return expression is a v2 statement form
              is-statement? (and (vector? ret-expr)
                                 (keyword? (first ret-expr))
                                 (#{:do :if-block :let-block :do-block :var-block} (first ret-expr)))]
          (if is-statement?
            compiled-let  ; Don't wrap statement forms in return
            (vec (concat let-bindings [[:return ret-expr]]))))

        ;; V2 statement-level form - don't wrap in return
        (and (list? expr) (#{'if-block 'let-block 'do-block 'do} (first expr)))
        [(compile-simple-expr expr)]

        ;; Simple expression - wrap in return
        :else
        [[:return (compile-simple-expr expr)]]))
    ;; Multiple expressions - all but last are statements, last is return
    (let [stmts (butlast body-exprs)
          ret (last body-exprs)
          compiled-stmts (mapcat (fn [expr]
                                   (if (and (list? expr) (= 'let (first expr)))
                                     (compile-let-form expr)
                                     [(compile-simple-expr expr)]))
                                 stmts)
          ;; Check if last expression is a v2 statement form
          ret-compiled (if (and (list? ret) (#{'if-block 'let-block 'do-block 'do} (first ret)))
                         [(compile-simple-expr ret)]
                         [[:return (compile-simple-expr ret)]])]
      (vec (concat compiled-stmts ret-compiled)))))

;;
;; Macro Implementations
;;

(defmacro defvertex
  "Define a vertex shader with natural Clojure-like syntax.

   Syntax:
     (defvertex name [param :type param :type ...]
       :builtin :builtin-name  ;; optional: if first param is builtin
       :uniform [name :type :group N :binding M]  ;; optional: uniforms
       :output [:name :type :location N]  ;; or :builtin
       body-expressions)

   Example:
     (defvertex triangle-vertex [vertex-index :u32]
       :builtin :vertex-index
       :output [:position :vec4f :builtin]
       (let [pos (array :vec2f
                   (vec2f 0.0 0.5)
                   (vec2f -0.5 -0.5)
                   (vec2f 0.5 -0.5))]
         (vec4 (get pos vertex-index) 0.0 1.0)))

   Generates:
     (def triangle-vertex-ast ...)
     (def triangle-vertex-wgsl ...)"
  [shader-name params & body]
  (let [{:keys [opts body]} (extract-metadata-opts body)

        ;; Parse params with builtin awareness
        param-pairs (partition 2 params)
        inputs (vec (map-indexed
                     (fn [idx [param-name param-type]]
                       (if (and (zero? idx) (:builtin opts))
                         ;; First param is builtin - convert hyphens to underscores
                         `(nirvikalpa.shader.ast/input-attribute {:builtin ~(:builtin opts)} ~param-type
                                                                 ~(clojure.string/replace (name param-name) "-" "_"))
                         ;; Regular location-based input - convert hyphens to underscores
                         `(nirvikalpa.shader.ast/input-attribute ~idx ~param-type
                                                                 ~(clojure.string/replace (name param-name) "-" "_"))))
                     param-pairs))

        ;; Parse structs
        structs (if-let [struct-specs (:structs opts)]
                  (vec struct-specs)  ; Pass through struct references
                  [])

        ;; Parse uniforms - can be :uniform [spec] or :uniforms [[spec1] [spec2] ...]
        uniforms (cond
                   ;; Multiple uniforms
                   (:uniforms opts)
                   (vec (map (fn [uniform-spec]
                               (let [[name type & {:keys [group binding]}] uniform-spec]
                                 (if (or (string? type) (symbol? type))
                                   `(nirvikalpa.shader.ast/uniform-struct-binding ~group ~binding ~(str type) ~(clojure.core/name name))
                                   `(nirvikalpa.shader.ast/uniform-binding ~group ~binding ~type ~(clojure.core/name name)))))
                             (:uniforms opts)))

                   ;; Single uniform
                   (:uniform opts)
                   (let [[name type & {:keys [group binding]}] (:uniform opts)]
                     [(if (or (string? type) (symbol? type))
                        `(nirvikalpa.shader.ast/uniform-struct-binding ~group ~binding ~(str type) ~(clojure.core/name name))
                        `(nirvikalpa.shader.ast/uniform-binding ~group ~binding ~type ~(clojure.core/name name)))])

                   ;; No uniforms
                   :else [])

        ;; Parse output
        ;; If output is a struct name (symbol or keyword), outputs is empty (struct defines them)
        outputs (cond
                  ;; No output spec
                  (nil? (:output opts))
                  []

                  ;; Struct return (single symbol/keyword)
                  (or (symbol? (:output opts)) (keyword? (:output opts)))
                  []  ; Struct-based return uses struct fields as outputs

                  ;; Multiple outputs (vector of vectors)
                  (and (vector? (:output opts))
                       (vector? (first (:output opts))))
                  ;; Each element is [name type attr-type attr-val]
                  (vec (map-indexed
                        (fn [idx output-spec]
                          (let [[name type attr-type & [attr-val]] output-spec
                                builtin-val (if (and (= attr-type :builtin) (nil? attr-val))
                                              name
                                              attr-val)]
                            (case attr-type
                              :builtin `(nirvikalpa.shader.ast/output-attribute {:builtin ~builtin-val} ~type)
                              :location `(nirvikalpa.shader.ast/output-attribute {:location ~(or attr-val idx) :name ~(clojure.core/name name)} ~type))))
                        (:output opts)))

                  ;; Single output spec [name type attr-type attr-val]
                  :else
                  (let [output-spec (:output opts)
                        [name type attr-type & [attr-val]] output-spec
                        ;; If attr-val is nil and attr-type is :builtin, use name as builtin keyword
                        builtin-val (if (and (= attr-type :builtin) (nil? attr-val))
                                      name
                                      attr-val)]
                    [(case attr-type
                       :builtin `(nirvikalpa.shader.ast/output-attribute {:builtin ~builtin-val} ~type)
                       :location `(nirvikalpa.shader.ast/output-attribute {:location ~attr-val :name ~(clojure.core/name name)} ~type))]))

        ;; Compile body
        compiled-body (compile-body body)

        ;; Determine return type (for struct returns)
        return-type (when (or (symbol? (:output opts)) (keyword? (:output opts)))
                      (str (:output opts)))

        ;; Generate defs
        ast-name (symbol (str shader-name "-ast"))
        wgsl-name shader-name]

    `(do
       (def ~ast-name
         (nirvikalpa.shader.ast/vertex-shader
          ~(cond-> {:inputs (vec inputs)
                    :outputs (vec outputs)
                    :uniforms (vec uniforms)
                    :structs (vec structs)
                    :body compiled-body}
             return-type (assoc :return-type return-type))))

       (def ~wgsl-name
         (nirvikalpa.shader.codegen/ast->wgsl ~ast-name)))))

(defmacro deffragment
  "Define a fragment shader with natural Clojure-like syntax.

   Syntax:
     (deffragment name [param :type param :type ...]
       :output [:name :type :location N]
       body-expressions)

   Example:
     (deffragment red-fragment []
       :output [:out-color :vec4f :location 0]
       (vec4f 1.0 0.0 0.0 1.0))

   Generates:
     (def red-fragment-ast ...)
     (def red-fragment-wgsl ...)"
  [shader-name params & body]
  (let [{:keys [opts body]} (extract-metadata-opts body)
        inputs (parse-params params)

        ;; Parse uniforms - same as defvertex
        uniforms (cond
                   (:uniforms opts)
                   (vec (map (fn [uniform-spec]
                               (let [[name type & {:keys [group binding]}] uniform-spec]
                                 (if (or (string? type) (symbol? type))
                                   `(nirvikalpa.shader.ast/uniform-struct-binding ~group ~binding ~(str type) ~(clojure.core/name name))
                                   `(nirvikalpa.shader.ast/uniform-binding ~group ~binding ~type ~(clojure.core/name name)))))
                             (:uniforms opts)))
                   (:uniform opts)
                   (let [[name type & {:keys [group binding]}] (:uniform opts)]
                     [(if (or (string? type) (symbol? type))
                        `(nirvikalpa.shader.ast/uniform-struct-binding ~group ~binding ~(str type) ~(clojure.core/name name))
                        `(nirvikalpa.shader.ast/uniform-binding ~group ~binding ~type ~(clojure.core/name name)))])
                   :else [])

        ;; Parse output
        outputs (if-let [output-spec (:output opts)]
                  (let [[name type attr-type & [attr-val]] output-spec]
                    [(case attr-type
                       :location `(nirvikalpa.shader.ast/output-attribute {:location ~attr-val :name ~(clojure.core/name name)} ~type))])
                  [])

        ;; Compile body
        compiled-body (compile-body body)

        ;; Parse preamble (helper functions to prepend)
        preamble-fns (:preamble opts)

        ;; Generate defs
        ast-name (symbol (str shader-name "-ast"))
        wgsl-name shader-name]

    `(do
       (def ~ast-name
         (nirvikalpa.shader.ast/fragment-shader
          {:inputs ~(vec inputs)
           :outputs ~(vec outputs)
           :uniforms ~(vec uniforms)
           :body ~compiled-body}))

       (def ~wgsl-name
         (if ~preamble-fns
           (str ~@(map (fn [fn-sym] `(str ~fn-sym "\n\n")) preamble-fns)
                (nirvikalpa.shader.codegen/ast->wgsl ~ast-name))
           (nirvikalpa.shader.codegen/ast->wgsl ~ast-name))))))

(defmacro defshader-fn
  "Define a WGSL helper function with natural Clojure-like syntax.

   Syntax:
     (defshader-fn name [param :type param :type ...] :return-type
       body-expressions)

   Example:
     (defshader-fn sdBox [p :vec2f b :vec2f] :f32
       (let [d (- (abs p) b)]
         (+ (distance (max d (vec2f 0.0)) (vec2f 0.0))
            (min (max d.x d.y) 0.0))))

   Generates:
     (def name-wgsl \"fn name(...) -> return_type { ... }\")"
  [fn-name params return-type & body]
  (let [;; Parse parameters: [p :vec2f b :vec2f] -> [{:name "p" :type :vec2f} {:name "b" :type :vec2f}]
        param-pairs (partition 2 params)
        param-list (mapv (fn [[name type]]
                           {:name (clojure.string/replace (clojure.core/name name) "-" "_")
                            :type type})
                         param-pairs)

        ;; Compile body
        compiled-body (compile-body body)

        ;; Generate WGSL name
        wgsl-fn-name (clojure.string/replace (name fn-name) "-" "_")
        wgsl-name fn-name]

    `(def ~wgsl-name
       (nirvikalpa.shader.codegen/function-def->wgsl
        {:name ~wgsl-fn-name
         :params ~param-list
         :return-type ~return-type
         :body ~compiled-body}))))

;;
;; Statement-Level Control Flow Macros
;; (Previously in separate dsl-v2 namespace, now integrated)
;;

(defmacro if-block
  "Block-level conditional that compiles to WGSL if-else statements.

   Use this when your if branches contain multiple statements or let bindings.
   For simple expressions, the regular `if` form (ternary) is sufficient.

   Example:
     (if-block (> x 0.5)
       (let [y (* x 2.0)]
         (+ y 1.0))
       0.0)"
  [test then else]
  `[:if-block ~test ~then ~else])

(defmacro let-block
  "Let binding that creates a WGSL block scope with variable declarations.

   Use this when you need block-scoped variables in statement context.

   Example:
     (let-block [x 1.0
                 y 2.0]
       (+ x y))"
  [bindings & body]
  `[:let-block ~bindings ~@body])

(defmacro do-block
  "Explicit do block for sequencing multiple statements.

   Use this when you need to execute multiple statements in sequence
   where a single expression is expected.

   Example:
     (do-block
       (assign counter (+ counter 1))
       (assign sum (+ sum value))
       sum)"
  [& body]
  `[:do-block ~@body])

(defmacro var-block
  "Declare a mutable variable in WGSL.

   Use this to declare variables that will be reassigned (not const).

   Example:
     (var-block min_dist :f32)  ; Declares: var min_dist: f32;"
  [var-name type]
  `[:var-block ~var-name ~type])

(defmacro assign
  "Assign a value to a mutable variable.

   Use this with variables declared via var-block.

   Example:
     (assign min_dist (min min_dist dist))"
  [var-name value]
  `[:assign ~var-name ~value])

(defmacro for-loop
  "For loop in WGSL.

   Generates a C-style for loop with initialization, condition, and update.

   Usage:
     (for-loop [i 0 (< i 10) (+ i 1)]
       body...)

   Compiles to:
     for (var i = 0; i < 10; i = i + 1) {
       body...
     }

   Example (iterate over tessellation points):
     (for-loop [i 0 (< i 50) (+ i 1)]
       (let [t (/ (f32 i) 49.0)
             point (eval-bezier t)]
         (assign min_dist (min min_dist (distance uv point)))))"
  [[var init condition update] & body]
  `[:for-loop [~var ~init ~condition ~update] ~@body])
