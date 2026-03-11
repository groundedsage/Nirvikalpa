(ns nirvikalpa.projection-test
  (:require [cljs.test :refer [deftest testing is]]
            [nirvikalpa.projection :as projection]))

;; =============================================================================
;; Test Helpers
;; =============================================================================

(defn- approx=
  "Compare two coordinate maps within floating-point tolerance.

  Returns true if both :x and :y components differ by less than tolerance."
  [expected actual tolerance]
  (and (< (js/Math.abs (- (:x expected) (:x actual))) tolerance)
       (< (js/Math.abs (- (:y expected) (:y actual))) tolerance)))

;; =============================================================================
;; Core Projection Tests
;; =============================================================================

(deftest round-trip-test
  (testing "Round-trip guarantee: project then unproject returns original point"
    (let [ctx (projection/orthographic-2d
               {:domain-x [0 100]
                :domain-y [0 500]
                :viewport [700 500]})
          test-points [{:x 0.0 :y 0.0}        ; bottom-left corner
                       {:x 100.0 :y 500.0}    ; top-right corner
                       {:x 50.0 :y 250.0}     ; center
                       {:x 25.0 :y 125.0}     ; interior point
                       {:x 75.0 :y 375.0}]]   ; interior point
      (doseq [point test-points]
        (let [projected (projection/project ctx point)
              unprojected (projection/unproject ctx projected)]
          (is (approx= point unprojected 1e-10)
              (str "Round-trip failed for point " point
                   " -> " projected " -> " unprojected)))))))

(deftest round-trip-with-view-transform-test
  (testing "Round-trip with zoom and pan"
    (let [ctx (projection/orthographic-2d
               {:domain-x [0 100]
                :domain-y [0 500]
                :viewport [700 500]
                :view-transform {:scale [1.5 2.0] :offset [0.1 -0.2]}})
          test-points [{:x 30.0 :y 175.0}
                       {:x 60.0 :y 350.0}
                       {:x 10.0 :y 50.0}]]
      (doseq [point test-points]
        (let [projected (projection/project ctx point)
              unprojected (projection/unproject ctx projected)]
          (is (approx= point unprojected 1e-10)
              (str "Round-trip with view transform failed for " point)))))))

;; =============================================================================
;; Y-Convention Tests
;; =============================================================================

(deftest y-up-convention-test
  (testing "Y-up convention: domain-y [bottom top] → higher domain Y = higher pixel Y"
    (let [ctx (projection/orthographic-2d
               {:domain-x [0 10]
                :domain-y [100 200]  ; 100 at viewport bottom, 200 at viewport top
                :viewport [700 500]})
          low (projection/project ctx {:x 5 :y 100})   ; low domain Y
          high (projection/project ctx {:x 5 :y 200})] ; high domain Y
      (is (> (:y high) (:y low))
          (str "Y-up failed: expected higher domain Y to produce higher pixel Y, "
               "but got low=" (:y low) " high=" (:y high))))))

(deftest y-down-convention-test
  (testing "Y-down convention: domain-y [top bottom] → higher domain Y = lower pixel Y"
    (let [ctx (projection/orthographic-2d
               {:domain-x [0 10]
                :domain-y [500 0]  ; 500 at viewport bottom, 0 at viewport top
                :viewport [700 500]})
          top (projection/project ctx {:x 5 :y 0})      ; y=0 maps to top
          bottom (projection/project ctx {:x 5 :y 500})] ; y=500 maps to bottom
      ;; In Y-up rendering space: top of screen = high pixel-y, bottom = low pixel-y
      (is (> (:y top) (:y bottom))
          (str "Y-down failed: domain y=0 should map to viewport top (high pixel-y), "
               "domain y=500 should map to viewport bottom (low pixel-y), "
               "but got top=" (:y top) " bottom=" (:y bottom))))))

;; =============================================================================
;; View Transform Tests
;; =============================================================================

(deftest zoom-in-test
  (testing "Zoom in (scale > 1.0) shows less of the domain"
    (let [ctx (projection/orthographic-2d
               {:domain-x [0 100]
                :domain-y [0 100]
                :viewport [400 400]
                :view-transform {:scale [2.0 2.0] :offset [0.0 0.0]}})
          visible (projection/visible-domain-range ctx)
          x-visible (- (second (:x visible)) (first (:x visible)))
          y-visible (- (second (:y visible)) (first (:y visible)))]
      (is (< x-visible 100) "Zoomed 2x should show less than full X range")
      (is (< y-visible 100) "Zoomed 2x should show less than full Y range"))))

(deftest zoom-out-test
  (testing "Zoom out (scale < 1.0) shows more of the domain"
    (let [ctx (projection/orthographic-2d
               {:domain-x [0 100]
                :domain-y [0 100]
                :viewport [400 400]
                :view-transform {:scale [0.5 0.5] :offset [0.0 0.0]}})
          visible (projection/visible-domain-range ctx)
          x-visible (- (second (:x visible)) (first (:x visible)))
          y-visible (- (second (:y visible)) (first (:y visible)))]
      (is (> x-visible 100) "Zoomed 0.5x should show more than full X range")
      (is (> y-visible 100) "Zoomed 0.5x should show more than full Y range"))))

(deftest pan-offset-test
  (testing "Pan offset shifts the visible range"
    (let [ctx-no-pan (projection/orthographic-2d
                      {:domain-x [0 100]
                       :domain-y [0 100]
                       :viewport [400 400]
                       :view-transform {:scale [1.0 1.0] :offset [0.0 0.0]}})
          ctx-pan-right (projection/orthographic-2d
                         {:domain-x [0 100]
                          :domain-y [0 100]
                          :viewport [400 400]
                          :view-transform {:scale [1.0 1.0] :offset [0.5 0.0]}})
          visible-no-pan (projection/visible-domain-range ctx-no-pan)
          visible-pan (projection/visible-domain-range ctx-pan-right)]
      ;; Positive X offset pans right, shifting visible range left in domain space
      (is (< (first (:x visible-pan)) (first (:x visible-no-pan)))
          "Pan right should shift visible domain range leftward"))))

;; =============================================================================
;; Semantic Direction Tests
;; =============================================================================

(deftest direction-resolution-test
  (testing "Semantic directions resolve consistently in rendering space"
    (let [ctx-yup (projection/orthographic-2d
                   {:domain-x [0 1]
                    :domain-y [0 1]      ; Y-up
                    :viewport [100 100]})
          ctx-ydown (projection/orthographic-2d
                     {:domain-x [0 1]
                      :domain-y [1 0]    ; Y-down
                      :viewport [100 100]})]
      ;; :up always means +Y in rendering space, regardless of domain convention
      (is (= {:dx 0 :dy 10} (projection/resolve-offset ctx-yup :up 10)))
      (is (= {:dx 0 :dy 10} (projection/resolve-offset ctx-ydown :up 10)))
      ;; :down always means -Y in rendering space
      (is (= {:dx 0 :dy -10} (projection/resolve-offset ctx-yup :down 10)))
      (is (= {:dx 0 :dy -10} (projection/resolve-offset ctx-ydown :down 10)))
      ;; :left and :right
      (is (= {:dx -10 :dy 0} (projection/resolve-offset ctx-yup :left 10)))
      (is (= {:dx 10 :dy 0} (projection/resolve-offset ctx-yup :right 10)))
      ;; nil direction = no offset
      (is (= {:dx 0 :dy 0} (projection/resolve-offset ctx-yup nil 10))))))

(deftest project-with-offset-test
  (testing "project-with-offset applies semantic offset correctly"
    (let [ctx (projection/orthographic-2d
               {:domain-x [0 100]
                :domain-y [0 500]
                :viewport [700 500]})
          base (projection/project ctx {:x 50 :y 250})
          offset-up (projection/project-with-offset ctx {:x 50 :y 250} :up 10)
          offset-down (projection/project-with-offset ctx {:x 50 :y 250} :down 10)
          offset-left (projection/project-with-offset ctx {:x 50 :y 250} :left 10)
          offset-right (projection/project-with-offset ctx {:x 50 :y 250} :right 10)]
      (is (= (:x base) (:x offset-up)) "Up offset should not change X")
      (is (= (+ (:y base) 10) (:y offset-up)) "Up offset should increase Y by 10")
      (is (= (- (:y base) 10) (:y offset-down)) "Down offset should decrease Y by 10")
      (is (= (- (:x base) 10) (:x offset-left)) "Left offset should decrease X by 10")
      (is (= (+ (:x base) 10) (:x offset-right)) "Right offset should increase X by 10"))))

;; =============================================================================
;; Edge Case Tests
;; =============================================================================

(deftest viewport-corners-test
  (testing "Domain extremes map to viewport extremes"
    (let [ctx (projection/orthographic-2d
               {:domain-x [0 100]
                :domain-y [0 500]
                :viewport [700 500]})
          bottom-left (projection/project ctx {:x 0 :y 0})
          top-right (projection/project ctx {:x 100 :y 500})]
      (is (approx= {:x 0.0 :y 0.0} bottom-left 1e-10)
          "Domain (0,0) should map to viewport (0,0)")
      (is (approx= {:x 700.0 :y 500.0} top-right 1e-10)
          "Domain (100,500) should map to viewport (700,500)"))))

(deftest identity-transform-test
  (testing "Identity transform (scale 1, offset 0) produces linear mapping"
    (let [ctx (projection/orthographic-2d
               {:domain-x [0 100]
                :domain-y [0 100]
                :viewport [100 100]
                :view-transform {:scale [1.0 1.0] :offset [0.0 0.0]}})
          center (projection/project ctx {:x 50 :y 50})]
      (is (approx= {:x 50.0 :y 50.0} center 1e-10)
          "Identity transform: domain center should map to viewport center"))))

(deftest zero-width-domain-test
  (testing "Division-by-zero guard: zero-width domain returns viewport center"
    (let [ctx (projection/orthographic-2d
               {:domain-x [50 50]
                :domain-y [100 100]
                :viewport [700 500]})
          result (projection/project ctx {:x 50 :y 100})]
      (is (approx= {:x 350.0 :y 250.0} result 1e-10)
          "Zero-width domain should project to viewport center"))))

(deftest zero-width-domain-unproject-test
  (testing "Unproject with zero-width domain returns domain min/first"
    (let [ctx (projection/orthographic-2d
               {:domain-x [50 50]
                :domain-y [100 100]
                :viewport [700 500]})
          result (projection/unproject ctx {:x 123.0 :y 456.0})]
      (is (approx= {:x 50.0 :y 100.0} result 1e-10)
          "Unproject with zero-width domain should return domain min/first"))))

;; =============================================================================
;; Context Update Tests
;; =============================================================================

(deftest update-view-transform-test
  (testing "update-view-transform creates new context with updated transform"
    (let [ctx-original (projection/orthographic-2d
                        {:domain-x [0 100]
                         :domain-y [0 500]
                         :viewport [700 500]
                         :view-transform {:scale [1.0 1.0] :offset [0.0 0.0]}})
          ctx-zoomed (projection/update-view-transform
                      ctx-original
                      {:scale [2.0 2.0] :offset [0.0 0.0]})
          point {:x 50 :y 250}
          original-proj (projection/project ctx-original point)
          zoomed-proj (projection/project ctx-zoomed point)]
      ;; Original context unchanged (purity)
      (is (= [1.0 1.0] (get-in ctx-original [:view-transform :scale])))
      ;; New context has updated transform
      (is (= [2.0 2.0] (get-in ctx-zoomed [:view-transform :scale])))
      ;; Projections differ (zoom affects output)
      (is (not= original-proj zoomed-proj)))))

(deftest update-domain-test
  (testing "update-domain creates new context with updated domain ranges"
    (let [ctx-original (projection/orthographic-2d
                        {:domain-x [0 100]
                         :domain-y [0 500]
                         :viewport [700 500]})
          ctx-new-x (projection/update-domain ctx-original [0 200])
          ctx-new-both (projection/update-domain ctx-original [0 200] [0 1000])
          point {:x 100 :y 500}]
      ;; Original unchanged
      (is (= [0 100] (:domain-x ctx-original)))
      (is (= [0 500] (:domain-y ctx-original)))
      ;; X-only update
      (is (= [0 200] (:domain-x ctx-new-x)))
      (is (= [0 500] (:domain-y ctx-new-x)))  ; Y unchanged
      ;; Both updated
      (is (= [0 200] (:domain-x ctx-new-both)))
      (is (= [0 1000] (:domain-y ctx-new-both))))))
