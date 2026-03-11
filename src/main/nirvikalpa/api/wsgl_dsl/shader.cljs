(ns nirvikalpa.api.wsgl-dsl.shader
  (:require [malli.core :as m]
            [malli.error :as me]
            [clojure.string :as str]))

;; Malli Schemas


(def scalar-literal [:enum :f32 :i32 :u32])
(def bool-literal   [:= :bool])

(def vec2-type [:tuple [:= :vec2] scalar-literal])
(def vec3-type [:tuple [:= :vec3] scalar-literal])
(def vec4-type [:tuple [:= :vec4] scalar-literal])

(def basic-type [:or scalar-literal
                 vec2-type vec3-type vec4-type
                 bool-literal])

(def array-type [:tuple [:= :array] basic-type :int])

(def wgsl-type [:or basic-type array-type :keyword])




(def attribute
  [:or
   [:tuple [:= :location] [:int {:min 0}]]
   [:tuple [:= :builtin] :keyword]])

(def fn-arg
  [:tuple attribute :keyword wgsl-type])

(def expression
  [:or
   :keyword
   :number
   [:map [:array [:tuple wgsl-type :int [:vector [:map [:vec2 [:vector :number]]]]]]]
   [:map [:vec2 [:vector :number]]]
   [:map [:vec4 [:tuple :number :number :number]]] ;; Fixed: Exactly 3 numbers
   [:map [:index [:tuple :keyword :keyword]]]
   [:map [:let [:tuple :keyword [:or :number [:map [:array :any]] [:map [:vec2 :any]] [:map [:vec4 :any]]]]]] ;; Fixed: Keyword and expression
   [:map [:set! [:tuple :keyword [:or :number [:map [:vec4 :any]]]]]] ;; Fixed: Keyword and value
   [:map [:return [:map [:vec4 [:tuple :any :number :number]]]]]]) ;; Added: Return expression

(def shader-fn
  [:map
   [:name :keyword]
   [:args [:vector fn-arg]]
   [:body [:vector expression]]])

(def shader-def
  [:map
   [:structs {:optional true} [:vector [:tuple :keyword [:vector [:tuple attribute :keyword wgsl-type]]]]]
   [:uniforms {:optional true} [:vector [:tuple :keyword wgsl-type]]]
   [:storage-buffers {:optional true} [:vector [:tuple [:map [:group :int] [:binding :int] [:access [:enum :read :write :read_write]]] :keyword wgsl-type]]]
   [:vertex {:optional true} [:map [:fn shader-fn]]]
   [:fragment {:optional true} [:map [:fn shader-fn]]]
   [:compute {:optional true} [:tuple [:= :compute] [:map [:workgroup-size [:vector [:int {:min 1}]]]] shader-fn]]])


;; Transformation Functions

(defn type->wgsl [t]
  (cond
    (keyword? t) (name t)                               ; simple alias types
    (vector?  t) (case (first t)
                   :vec2  (format "vec2<%s>"  (type->wgsl (second t)))
                   :vec3  (format "vec3<%s>"  (type->wgsl (second t)))
                   :vec4  (format "vec4<%s>"  (type->wgsl (second t)))
                   :array (format "array<%s, %s>"
                                  (type->wgsl (second t))
                                  (nth t 2)))
    :else        (case t
                   :f32 "f32"  :i32 "i32"  :u32 "u32"
                   (name t))))



(defn name->wgsl [kw]
  (subs (str kw) 1))

(defn expr->wgsl [expr]
  (cond
    (keyword? expr) (name->wgsl expr)
    (number? expr) (str expr (if (integer? expr) "" ".0"))
    (map? expr)
    (cond
      (:array expr)
      (let [[type size elements] (:array expr)]
        (format "array<%s, %s>(%s)"
                (type->wgsl type)
                size
                (str/join ", " (map expr->wgsl elements))))
      (:vec2 expr)
      (let [[x y] (:vec2 expr)]
        (format "vec2<f32>(%s, %s)" (expr->wgsl x) (expr->wgsl y)))
      (:vec4 expr)
      (let [[e1 e2 e3] (:vec4 expr)]
        (format "vec4<f32>(%s, %s, %s, 1.0)"
                (expr->wgsl e1)
                (expr->wgsl e2)
                (expr->wgsl e3)))
      (:index expr)
      (let [[arr idx] (:index expr)]
        (format "%s[%s]" (name->wgsl arr) (name->wgsl idx)))
      (:let expr)
      (let [[var val] (:let expr)]
        (format "var %s = %s;" (name->wgsl var) (expr->wgsl val)))
      (:set! expr)
      (let [[var val] (:set! expr)]
        (format "%s = %s;" (expr->wgsl var) (expr->wgsl val)))
      (:return expr)
      (let [[e1 e2 e3] (:vec4 (:return expr))]
        (format "vec4<f32>(%s, %s, %s)"
                (expr->wgsl e1)
                (expr->wgsl e2)
                (expr->wgsl e3))))
    :else (str expr)))

(defn dsl->wgsl [shader]
  (if-not (m/validate shader-def shader)
    (throw (ex-info "Invalid shader definition" (me/humanize (m/explain shader-def shader))))
    (let [{:keys [structs uniforms storage-buffers vertex fragment compute]} shader]
      (str
       ;; Structs
       (for [[name fields] structs]
         (format "struct %s {\n%s\n};\n"
                 (name->wgsl name)
                 (str/join "\n"
                           (for [[[_ attr] fname ftype] fields]
                             (format "    %s%s: %s,"
                                     (case attr
                                       :location (format "@location(%d) " (second fname))
                                       :builtin (format "@builtin(%s) " (name->wgsl (second fname)))
                                       "")
                                     (name->wgsl fname)
                                     (type->wgsl ftype))))))
       ;; Uniforms
       (for [[name type] uniforms]
         (format "@group(0) @binding(%d) var<uniform> %s: %s;\n"
                 (count uniforms) (name->wgsl name) (type->wgsl type)))
       ;; Storage Buffers
       (for [[attrs name type] storage-buffers]
         (format "@group(%d) @binding(%d) var<%s, %s> %s: %s;\n"
                 (:group attrs) (:binding attrs)
                 (if (:access attrs) "storage" "uniform") (name (:access attrs))
                 (name->wgsl name) (type->wgsl type)))
       ;; Vertex Shader
       (when vertex
         (let [{:keys [name args body]} (:fn vertex)]
           (format "@vertex\nfn %s(%s) -> @builtin(position) %s {\n%s\n    return %s;\n}"
                   (name->wgsl name)
                   (str/join ", "
                             (for [[[_ attr] arg-name arg-type] args]
                               (format "@builtin(%s) %s: %s"
                                       (name->wgsl (second attr))
                                       (name->wgsl arg-name)
                                       (type->wgsl arg-type))))
                   (type->wgsl :vec4)
                   (str/join "\n    "
                             (map expr->wgsl body))
                   (expr->wgsl (last body)))))
       ;; Fragment Shader
       (when fragment
         (let [{:keys [name args body]} (:fn fragment)]
           (format "@fragment\nfn %s(%s) -> @location(0) %s {\n%s\n    return %s;\n}"
                   (name->wgsl name)
                   (str/join ", "
                             (for [[_ arg-name arg-type] args]
                               (format "%s: %s" (name->wgsl arg-name) (type->wgsl arg-type))))
                   (type->wgsl :vec4)
                   (str/join "\n    "
                             (map expr->wgsl body))
                   (expr->wgsl (last body)))))
       ;; Compute Shader
       (when compute
         (let [[_ {:keys [workgroup-size]} {:keys [name args body]}] compute]
           (format "@compute @workgroup_size(%s)\nfn %s(%s) {\n%s\n}"
                   (str/join ", " workgroup-size)
                   (name->wgsl name)
                   (str/join ", "
                             (for [[attrs arg-name arg-type] args]
                               (format "@builtin(%s) %s: %s" (name->wgsl (:builtin attrs)) (name->wgsl arg-name) (type->wgsl arg-type))))
                   (str/join "\n    " (map expr->wgsl body)))))))))

;; DSL Definition
(def triangle-vertex
  {:vertex
   {:fn {:name :main
         :args [[[:builtin :vertex_index] :VertexIndex :u32]]
         :body [{:let [:pos {:array [:vec2 3
                                     [{:vec2 [0.0 0.5]}
                                      {:vec2 [-0.5 -0.5]}
                                      {:vec2 [0.5 -0.5]}]]}]}
                {:return {:vec4 [{:index [:pos :VertexIndex]} 0.0 1.0]}}]}}})

;; Run the transformation
(dsl->wgsl triangle-vertex)