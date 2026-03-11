(ns nirvikalpa.math.ga
  "Core Geometric Algebra operations

   Geometric Algebra (Clifford Algebra) provides a unified framework for
   geometric operations. This namespace implements the fundamental operations
   on multivectors.

   A multivector is the general element of geometric algebra, containing:
   - Scalar (grade 0): magnitude
   - Vector (grade 1): direction
   - Bivector (grade 2): oriented plane/area
   - Trivector (grade 3): oriented volume

   In 3D, we have 8 basis elements:
   - 1 scalar: 1
   - 3 vectors: e1, e2, e3
   - 3 bivectors: e12, e23, e31
   - 1 trivector: e123")

;;
;; Data Structures (Pure Data)
;;

(defn multivector
  "Create a multivector from components.

   In 3D:
   - scalar: single number
   - vector: {:e1 x :e2 y :e3 z}
   - bivector: {:e12 xy :e23 yz :e31 zx}
   - trivector: single number (e123 component)

   Example:
   (multivector {:scalar 1.0
                 :vector {:e1 1.0 :e2 0.0 :e3 0.0}
                 :bivector {:e12 0.0 :e23 0.0 :e31 0.0}
                 :trivector 0.0})"
  [{:keys [scalar vector bivector trivector]
    :or {scalar 0.0
         vector {:e1 0.0 :e2 0.0 :e3 0.0}
         bivector {:e12 0.0 :e23 0.0 :e31 0.0}
         trivector 0.0}}]
  {:scalar scalar
   :vector vector
   :bivector bivector
   :trivector trivector})

(defn scalar
  "Create a scalar (grade 0 multivector).

   Example: (scalar 5.0) => {:scalar 5.0 ...}"
  [s]
  (multivector {:scalar s}))

(defn vector-3d
  "Create a vector (grade 1 multivector) in 3D.

   Example: (vector-3d 1 2 3) => {:vector {:e1 1 :e2 2 :e3 3} ...}"
  [x y z]
  (multivector {:vector {:e1 x :e2 y :e3 z}}))

(defn bivector-3d
  "Create a bivector (grade 2 multivector) in 3D.

   A bivector represents an oriented plane. In 3D there are 3 basis bivectors:
   - e12: XY plane
   - e23: YZ plane
   - e31: ZX plane

   Example: (bivector-3d {:e12 1.0}) => rotation in XY plane"
  [{:keys [e12 e23 e31]
    :or {e12 0.0 e23 0.0 e31 0.0}}]
  (multivector {:bivector {:e12 e12 :e23 e23 :e31 e31}}))

;;
;; Basic Operations (Pure Functions)
;;

(defn add
  "Add two multivectors (component-wise addition).

   Example:
   (add (scalar 1.0) (scalar 2.0)) => (scalar 3.0)"
  [a b]
  (multivector
   {:scalar (+ (:scalar a) (:scalar b))
    :vector {:e1 (+ (get-in a [:vector :e1]) (get-in b [:vector :e1]))
             :e2 (+ (get-in a [:vector :e2]) (get-in b [:vector :e2]))
             :e3 (+ (get-in a [:vector :e3]) (get-in b [:vector :e3]))}
    :bivector {:e12 (+ (get-in a [:bivector :e12]) (get-in b [:bivector :e12]))
               :e23 (+ (get-in a [:bivector :e23]) (get-in b [:bivector :e23]))
               :e31 (+ (get-in a [:bivector :e31]) (get-in b [:bivector :e31]))}
    :trivector (+ (:trivector a) (:trivector b))}))

(defn scale
  "Multiply multivector by scalar.

   Example: (scale 2.0 (vector-3d 1 0 0)) => (vector-3d 2 0 0)"
  [s mv]
  (multivector
   {:scalar (* s (:scalar mv))
    :vector {:e1 (* s (get-in mv [:vector :e1]))
             :e2 (* s (get-in mv [:vector :e2]))
             :e3 (* s (get-in mv [:vector :e3]))}
    :bivector {:e12 (* s (get-in mv [:bivector :e12]))
               :e23 (* s (get-in mv [:bivector :e23]))
               :e31 (* s (get-in mv [:bivector :e31]))}
    :trivector (* s (:trivector mv))}))

(defn ga-reverse
  "Reverse of a multivector (conjugation).

   Reverses the order of basis vector multiplication:
   - Grade 0 (scalar): unchanged
   - Grade 1 (vector): unchanged
   - Grade 2 (bivector): negated
   - Grade 3 (trivector): negated

   This is crucial for rotations: v' = RvR̃ where R̃ is reverse of R

   Example:
   (ga-reverse (bivector-3d {:e12 1.0})) => (bivector-3d {:e12 -1.0})"
  [mv]
  (multivector
   {:scalar (:scalar mv)
    :vector (:vector mv)
    :bivector {:e12 (- (get-in mv [:bivector :e12]))
               :e23 (- (get-in mv [:bivector :e23]))
               :e31 (- (get-in mv [:bivector :e31]))}
    :trivector (- (:trivector mv))}))

(defn inner-product
  "Inner product (contraction) of two vectors.

   For vectors in 3D: a·b = a1*b1 + a2*b2 + a3*b3 (standard dot product)

   Example:
   (inner-product (vector-3d 1 0 0) (vector-3d 0 1 0)) => 0.0"
  [a b]
  (let [a-vec (:vector a)
        b-vec (:vector b)]
    (+ (* (:e1 a-vec) (:e1 b-vec))
       (* (:e2 a-vec) (:e2 b-vec))
       (* (:e3 a-vec) (:e3 b-vec)))))

(defn wedge-product
  "Wedge (exterior) product of two vectors -> bivector.

   Creates an oriented area (bivector) from two vectors.
   In 3D: a∧b is dual to a×b (cross product)

   Properties:
   - a∧a = 0 (can't make area with yourself)
   - a∧b = -b∧a (antisymmetric)

   Example:
   (wedge-product (vector-3d 1 0 0) (vector-3d 0 1 0))
   => bivector in XY plane"
  [a b]
  (let [a-vec (:vector a)
        b-vec (:vector b)
        a1 (:e1 a-vec)
        a2 (:e2 a-vec)
        a3 (:e3 a-vec)
        b1 (:e1 b-vec)
        b2 (:e2 b-vec)
        b3 (:e3 b-vec)]
    (bivector-3d
     {:e12 (- (* a1 b2) (* a2 b1))
      :e23 (- (* a2 b3) (* a3 b2))
      :e31 (- (* a3 b1) (* a1 b3))})))

(defn geometric-product
  "Geometric product of two multivectors: ab = a·b + a∧b

   This is the fundamental operation of geometric algebra.
   For vectors: ab = a·b + a∧b (scalar + bivector)

   Simplified version for common cases (can be expanded as needed)."
  [a b]
  ;; For now, implement vector * vector case
  ;; Can be extended to full multivector product as needed
  (let [dot (inner-product a b)
        wedge (wedge-product a b)]
    (multivector
     {:scalar dot
      :bivector (:bivector wedge)})))

;;
;; Rotor Operations (Rotations)
;;

(defn rotor-from-bivector
  "Create a rotor (rotation) from a bivector and angle.

   Rotor R = e^(-Bθ/2) = cos(θ/2) - sin(θ/2)B

   Where:
   - B is a unit bivector (oriented plane of rotation)
   - θ is the rotation angle in radians

   Apply with sandwich product: v' = RvR̃

   Example:
   ;; 90° rotation in XY plane
   (rotor-from-bivector (bivector-3d {:e12 1.0}) (/ Math/PI 2))"
  [bivector-mv angle]
  (let [half-angle (/ angle 2.0)
        cos-half (Math/cos half-angle)
        sin-half (Math/sin half-angle)
        biv (:bivector bivector-mv)]
    (multivector
     {:scalar cos-half
      :bivector {:e12 (- (* sin-half (:e12 biv)))
                 :e23 (- (* sin-half (:e23 biv)))
                 :e31 (- (* sin-half (:e31 biv)))}})))

(defn rotor-from-axis-angle
  "Create a rotor from axis vector and angle (traditional interface).

   Converts axis-angle representation to rotor.
   The axis defines the plane of rotation (perpendicular to axis in 3D).

   Example:
   ;; Rotate 90° around Z axis
   (rotor-from-axis-angle (vector-3d 0 0 1) (/ Math/PI 2))"
  [axis-vec angle]
  (let [;; In 3D, axis is dual to bivector (plane perpendicular to axis)
        ;; Z-axis [0,0,1] -> XY plane (e12)
        ;; Y-axis [0,1,0] -> ZX plane (e31)
        ;; X-axis [1,0,0] -> YZ plane (e23)
        ax (:vector axis-vec)
        x (:e1 ax)
        y (:e2 ax)
        z (:e3 ax)
        ;; Normalize axis
        mag (Math/sqrt (+ (* x x) (* y y) (* z z)))
        nx (/ x mag)
        ny (/ y mag)
        nz (/ z mag)
        ;; Convert to bivector (Hodge dual in 3D)
        bivector (bivector-3d {:e23 nx :e31 ny :e12 nz})]
    (rotor-from-bivector bivector angle)))

(defn apply-rotor
  "Apply rotor to vector using sandwich product: v' = RvR̃

   This is how rotations work in geometric algebra.

   Example:
   (let [R (rotor-from-axis-angle (vector-3d 0 0 1) (/ Math/PI 2))
         v (vector-3d 1 0 0)]
     (apply-rotor R v))
   ;; => rotated 90° to [0 1 0]"
  [rotor vec]
  (let [;; Extract components for optimized sandwich product
        s (:scalar rotor)
        biv (:bivector rotor)
        b12 (:e12 biv)
        b23 (:e23 biv)
        b31 (:e31 biv)
        v (:vector vec)
        vx (:e1 v)
        vy (:e2 v)
        vz (:e3 v)

        ;; Optimized sandwich product RvR̃ for 3D
        ;; Based on Rodrigues-like formula: v' = v + 2s(b×v) + 2(b×(b×v))
        ;; where b is bivector, s is scalar part

        ;; First: b×v (bivector-vector product gives vector)
        bxv-x (- (* b12 vy) (* b31 vz))
        bxv-y (- (* b23 vz) (* b12 vx))
        bxv-z (- (* b31 vx) (* b23 vy))

        ;; Second: b×(b×v)
        bxbxv-x (- (* b12 bxv-y) (* b31 bxv-z))
        bxbxv-y (- (* b23 bxv-z) (* b12 bxv-x))
        bxbxv-z (- (* b31 bxv-x) (* b23 bxv-y))

        ;; Final: v' = v + 2s(b×v) + 2(b×(b×v))
        vx' (+ vx (* 2.0 s bxv-x) (* 2.0 bxbxv-x))
        vy' (+ vy (* 2.0 s bxv-y) (* 2.0 bxbxv-y))
        vz' (+ vz (* 2.0 s bxv-z) (* 2.0 bxbxv-z))]

    (vector-3d vx' vy' vz')))

(defn compose-rotors
  "Compose two rotors via geometric product: R = R1 * R2

   Composition allows building complex rotations from simple ones.

   Example:
   (let [R1 (rotor-from-axis-angle (vector-3d 0 0 1) (/ Math/PI 4))
         R2 (rotor-from-axis-angle (vector-3d 0 1 0) (/ Math/PI 4))]
     (compose-rotors R1 R2))"
  [r1 r2]
  (let [;; Extract components
        s1 (:scalar r1)
        b1 (:bivector r1)
        s2 (:scalar r2)
        b2 (:bivector r2)

        ;; Geometric product of rotors
        ;; (s1 + B1)(s2 + B2) = s1*s2 + s1*B2 + B1*s2 + B1*B2
        ;; where B1*B2 involves bivector-bivector product

        ;; Scalar part: s1*s2 - (B1·B2)
        ;; For bivectors: B1·B2 = sum of component products
        b1-dot-b2 (+ (* (:e12 b1) (:e12 b2))
                     (* (:e23 b1) (:e23 b2))
                     (* (:e31 b1) (:e31 b2)))

        new-scalar (- (* s1 s2) b1-dot-b2)

        ;; Bivector part: s1*B2 + s2*B1 (simplified, ignoring bivector-bivector wedge)
        new-e12 (+ (* s1 (:e12 b2)) (* s2 (:e12 b1)))
        new-e23 (+ (* s1 (:e23 b2)) (* s2 (:e23 b1)))
        new-e31 (+ (* s1 (:e31 b2)) (* s2 (:e31 b1)))]

    (multivector
     {:scalar new-scalar
      :bivector {:e12 new-e12 :e23 new-e23 :e31 new-e31}})))

(defn slerp-rotors
  "Spherical linear interpolation between two rotors.

   Provides smooth interpolation for animation.
   t = 0 => r1
   t = 1 => r2

   Example:
   (slerp-rotors rotor-start rotor-end 0.5) ;; halfway between"
  [r1 r2 t]
  ;; Simplified version: linear interpolation + normalization
  ;; Full slerp would use logarithms of rotors
  (let [s1 (:scalar r1)
        b1 (:bivector r1)
        s2 (:scalar r2)
        b2 (:bivector r2)

        ;; Lerp components
        s-lerp (+ (* (- 1.0 t) s1) (* t s2))
        b12-lerp (+ (* (- 1.0 t) (:e12 b1)) (* t (:e12 b2)))
        b23-lerp (+ (* (- 1.0 t) (:e23 b1)) (* t (:e23 b2)))
        b31-lerp (+ (* (- 1.0 t) (:e31 b1)) (* t (:e31 b2)))

        ;; Normalize (rotor should have unit magnitude)
        mag-sq (+ (* s-lerp s-lerp)
                  (* b12-lerp b12-lerp)
                  (* b23-lerp b23-lerp)
                  (* b31-lerp b31-lerp))
        mag (Math/sqrt mag-sq)

        s-norm (/ s-lerp mag)
        b12-norm (/ b12-lerp mag)
        b23-norm (/ b23-lerp mag)
        b31-norm (/ b31-lerp mag)]

    (multivector
     {:scalar s-norm
      :bivector {:e12 b12-norm :e23 b23-norm :e31 b31-norm}})))

;;
;; Utility Functions
;;

(defn vec3->multivector
  "Convert standard [x y z] vector to multivector.

   Example: (vec3->multivector [1 2 3]) => (vector-3d 1 2 3)"
  [[x y z]]
  (vector-3d x y z))

(defn multivector->vec3
  "Extract [x y z] from multivector vector part.

   Example: (multivector->vec3 (vector-3d 1 2 3)) => [1 2 3]"
  [mv]
  (let [v (:vector mv)]
    [(:e1 v) (:e2 v) (:e3 v)]))

(defn rotor->components
  "Extract rotor components for GPU uniform upload.

   Returns {:scalar s :bivector [b23 b31 b12]}

   This format is optimized for shader uniforms."
  [rotor]
  (let [biv (:bivector rotor)]
    {:scalar (:scalar rotor)
     :bivector [(:e23 biv) (:e31 biv) (:e12 biv)]}))

;;
;; Interop: GA ↔ Matrix Conversions
;;

(defn rotor->mat4
  "Convert a GA rotor to a 4x4 rotation matrix.

   Uses the standard approach: apply rotor to basis vectors to get matrix columns.
   This is simpler and guaranteed correct.

   Returns a Float32Array in column-major order (WebGPU standard)."
  [rotor]
  (let [;; Define basis vectors
        e1 (vector-3d 1 0 0)
        e2 (vector-3d 0 1 0)
        e3 (vector-3d 0 0 1)

        ;; Apply rotor to each basis vector: RvR̃
        col1 (apply-rotor rotor e1)
        col2 (apply-rotor rotor e2)
        col3 (apply-rotor rotor e3)

        ;; Extract vector components
        [c1x c1y c1z] (multivector->vec3 col1)
        [c2x c2y c2z] (multivector->vec3 col2)
        [c3x c3y c3z] (multivector->vec3 col3)]

    ;; Create 4x4 matrix with rotated basis vectors as columns
    ;; Column-major order for WebGPU
    (js/Float32Array.
     #js [c1x c1y c1z 0.0
          c2x c2y c2z 0.0
          c3x c3y c3z 0.0
          0.0 0.0 0.0 1.0])))
