(ns nirvikalpa.projection
  "Coordinate-system-agnostic projection context for 2D rendering.

  This namespace provides pure data structures and pure functions for mapping
  between domain coordinates and rendering pixels. It decouples the consumer's
  coordinate system from nirvikalpa's internal Y-up rendering space.

  See docs/architecture/ADR-001-projection-context.md for design rationale.")

;; =============================================================================
;; Pure Data Constructors
;; =============================================================================

(defn orthographic-2d
  "Create an orthographic 2D projection context.

  Parameters (map with keys):
    :domain-x    - [min max] domain X range (consumer's coordinate space)
    :domain-y    - [bottom top] domain Y range. Ordering encodes Y-convention:
                   [0 500] = Y-up (0 at bottom, 500 at top)
                   [500 0] = Y-down (500 at bottom, 0 at top)
    :viewport    - [width height] in CSS pixels
    :view-transform - (optional) {:scale [sx sy] :offset [ox oy]}
                      Defaults to identity transform

  Returns a projection context (plain map) that can be used with project/unproject.

  Example:
    (orthographic-2d {:domain-x [0 100]
                      :domain-y [0 500]
                      :viewport [700 500]
                      :view-transform {:scale [1.0 1.0] :offset [0.0 0.0]}})"
  [{:keys [domain-x domain-y viewport view-transform]
    :or {view-transform {:scale [1.0 1.0] :offset [0.0 0.0]}}}]
  {:type :orthographic-2d
   :domain-x (vec domain-x)
   :domain-y (vec domain-y)
   :viewport (vec viewport)
   :view-transform view-transform})

;; =============================================================================
;; Core Projection Functions (Pure)
;; =============================================================================

(defn project
  "Project domain coordinates to rendering pixel coordinates.

  Implements the normalization pipeline:
    Domain → Normalized [0,1] → NDC [-1,1] → View Transform → Rendering Pixels

  The domain-y range ordering handles Y-convention automatically:
  - domain-y [0 500]: Y-up, higher domain Y → higher pixel Y
  - domain-y [500 0]: Y-down, higher domain Y → lower pixel Y

  Division-by-zero guard: if domain range is zero-width, returns viewport center.

  Parameters:
    ctx   - Projection context from orthographic-2d
    point - {:x x :y y} in domain coordinates

  Returns:
    {:x pixel-x :y pixel-y} in rendering coordinates (Y-up, (0,0) at bottom-left)

  Example:
    (project ctx {:x 50 :y 250})
    ;; => {:x 350.0 :y 250.0}"
  [ctx {:keys [x y]}]
  (let [[domain-x-min domain-x-max] (:domain-x ctx)
        [domain-y-first domain-y-second] (:domain-y ctx)
        [vw vh] (:viewport ctx)
        {:keys [scale offset]} (:view-transform ctx)
        [scale-x scale-y] scale
        [offset-x offset-y] offset

        ;; Step 1: Domain → Normalized [0, 1]
        ;; Division-by-zero guard: zero-width domain returns 0.5 (viewport center)
        x-norm (if (== domain-x-min domain-x-max)
                 0.5
                 (/ (- x domain-x-min) (- domain-x-max domain-x-min)))
        y-norm (if (== domain-y-first domain-y-second)
                 0.5
                 (/ (- y domain-y-first) (- domain-y-second domain-y-first)))

        ;; Step 2: Normalized → NDC [-1, 1]
        x-ndc (- (* x-norm 2.0) 1.0)
        y-ndc (- (* y-norm 2.0) 1.0)

        ;; Step 3: View Transform (zoom/pan)
        x-ndc' (+ (* x-ndc scale-x) offset-x)
        y-ndc' (+ (* y-ndc scale-y) offset-y)

        ;; Step 4: NDC → Rendering Pixels (Y-up, (0,0) at bottom-left)
        pixel-x (* (/ (+ x-ndc' 1.0) 2.0) vw)
        pixel-y (* (/ (+ y-ndc' 1.0) 2.0) vh)]

    {:x pixel-x :y pixel-y}))

(defn unproject
  "Unproject rendering pixel coordinates back to domain coordinates.

  Exact inverse of project. Reverses the normalization pipeline:
    Rendering Pixels → View Transform → NDC [-1,1] → Normalized [0,1] → Domain

  Division-by-zero guard: if domain range is zero-width, returns domain min/first.

  Parameters:
    ctx   - Projection context from orthographic-2d
    point - {:x pixel-x :y pixel-y} in rendering coordinates

  Returns:
    {:x x :y y} in domain coordinates

  Round-trip guarantee (within floating-point tolerance):
    (≈ point (unproject ctx (project ctx point)))

  Example:
    (unproject ctx {:x 350.0 :y 250.0})
    ;; => {:x 50.0 :y 250.0}"
  [ctx {:keys [x y]}]
  (let [[domain-x-min domain-x-max] (:domain-x ctx)
        [domain-y-first domain-y-second] (:domain-y ctx)
        [vw vh] (:viewport ctx)
        {:keys [scale offset]} (:view-transform ctx)
        [scale-x scale-y] scale
        [offset-x offset-y] offset

        ;; Step 4 (inverse): Rendering Pixels → NDC
        x-ndc' (- (/ (* x 2.0) vw) 1.0)
        y-ndc' (- (/ (* y 2.0) vh) 1.0)

        ;; Step 3 (inverse): Undo view transform
        x-ndc (if (zero? scale-x) 0.0 (/ (- x-ndc' offset-x) scale-x))
        y-ndc (if (zero? scale-y) 0.0 (/ (- y-ndc' offset-y) scale-y))

        ;; Step 2 (inverse): NDC → Normalized [0, 1]
        x-norm (/ (+ x-ndc 1.0) 2.0)
        y-norm (/ (+ y-ndc 1.0) 2.0)

        ;; Step 1 (inverse): Normalized → Domain
        ;; Division-by-zero guard: zero-width domain returns domain min/first
        domain-x (if (== domain-x-min domain-x-max)
                   domain-x-min
                   (+ domain-x-min (* x-norm (- domain-x-max domain-x-min))))
        domain-y (if (== domain-y-first domain-y-second)
                   domain-y-first
                   (+ domain-y-first (* y-norm (- domain-y-second domain-y-first))))]

    {:x domain-x :y domain-y}))

;; =============================================================================
;; Semantic Directions (Pure)
;; =============================================================================

(defn resolve-offset
  "Resolve a semantic direction keyword to a pixel offset vector.

  Directions are resolved in rendering space (Y-up), independent of domain convention:
    :up    → {:dx 0 :dy distance}   (always +Y)
    :down  → {:dx 0 :dy (- distance)} (always -Y)
    :left  → {:dx (- distance) :dy 0} (always -X)
    :right → {:dx distance :dy 0}   (always +X)
    nil    → {:dx 0 :dy 0}

  The distance is in CSS pixels, not zoom-adjusted. This is intentional:
  a 10px offset means 10 actual screen pixels, regardless of zoom level.

  Parameters:
    ctx       - Projection context (not used, kept for API consistency)
    direction - Keyword (:up, :down, :left, :right) or nil
    distance  - Pixel distance (default 0)

  Returns:
    {:dx dx :dy dy} pixel offset vector

  Example:
    (resolve-offset ctx :up 10)
    ;; => {:dx 0 :dy 10}"
  ([ctx direction]
   (resolve-offset ctx direction 0))
  ([_ctx direction distance]
   (case direction
     :up    {:dx 0 :dy distance}
     :down  {:dx 0 :dy (- distance)}
     :left  {:dx (- distance) :dy 0}
     :right {:dx distance :dy 0}
     nil    {:dx 0 :dy 0}
     ;; Default: unknown direction = no offset
     {:dx 0 :dy 0})))

(defn project-with-offset
  "Project domain coordinates and apply a semantic directional offset.

  Convenience function combining project + resolve-offset.

  Parameters:
    ctx       - Projection context
    point     - {:x x :y y} in domain coordinates
    direction - Keyword (:up, :down, :left, :right) or nil
    distance  - Pixel offset distance (default 0)

  Returns:
    {:x pixel-x :y pixel-y} in rendering coordinates, offset applied

  Example:
    (project-with-offset ctx {:x 50 :y 250} :up 10)
    ;; => {:x 350.0 :y 260.0}"
  ([ctx point direction]
   (project-with-offset ctx point direction 0))
  ([ctx point direction distance]
   (let [{:keys [x y]} (project ctx point)
         {:keys [dx dy]} (resolve-offset ctx direction distance)]
     {:x (+ x dx) :y (+ y dy)})))

;; =============================================================================
;; View Introspection (Pure)
;; =============================================================================

(defn visible-domain-range
  "Calculate the visible domain range given the current view transform.

  Unprojects the viewport corners to find what portion of the domain is visible.
  Useful for understanding how zoom/pan affects the displayed data.

  When zoomed in (scale > 1.0), the visible range is smaller than the full domain.
  When zoomed out (scale < 1.0), the visible range may extend beyond the domain.

  Parameters:
    ctx - Projection context with view transform

  Returns:
    {:x [min-x max-x] :y [min-y max-y]} in domain coordinates

  Example:
    (visible-domain-range ctx)
    ;; => {:x [25.0 75.0] :y [125.0 375.0]} when zoomed 2x"
  [ctx]
  (let [bottom-left (unproject ctx {:x 0.0 :y 0.0})
        top-right (unproject ctx {:x (first (:viewport ctx))
                                  :y (second (:viewport ctx))})]
    {:x [(:x bottom-left) (:x top-right)]
     :y [(:y bottom-left) (:y top-right)]}))

;; =============================================================================
;; Context Updates (Pure)
;; =============================================================================

(defn update-view-transform
  "Create a new context with an updated view transform.

  Pure function - returns a new context, does not modify the input.

  Parameters:
    ctx            - Existing projection context
    view-transform - New {:scale [sx sy] :offset [ox oy]} map

  Returns:
    New projection context with updated view transform

  Example:
    (update-view-transform ctx {:scale [2.0 2.0] :offset [0.0 0.0]})"
  [ctx view-transform]
  (assoc ctx :view-transform view-transform))

(defn update-domain
  "Create a new context with updated domain ranges.

  Pure function - returns a new context, does not modify the input.

  Parameters:
    ctx      - Existing projection context
    domain-x - New [min max] X domain range (optional)
    domain-y - New [bottom top] Y domain range (optional)

  Returns:
    New projection context with updated domain ranges

  Example:
    (update-domain ctx [0 200] [0 1000])"
  ([ctx domain-x]
   (assoc ctx :domain-x (vec domain-x)))
  ([ctx domain-x domain-y]
   (assoc ctx :domain-x (vec domain-x) :domain-y (vec domain-y))))
