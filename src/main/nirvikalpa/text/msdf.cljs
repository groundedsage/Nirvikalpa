(ns nirvikalpa.text.msdf
  "MSDF (Multi-channel Signed Distance Field) text rendering for WebGPU.

   Provides high-quality text rendering with resolution-independent antialiasing.
   Based on the WebGPU textRenderingMsdf sample."
  (:require [nirvikalpa.gpu :as gpu]))

;; =============================================================================
;; Constants
;; =============================================================================

(def ^:private char-codes
  "Character codes for special characters"
  {:newline 10
   :carriage-return 13
   :space 32})

(def ^:private msdf-shader-code
  "MSDF text rendering shader - embedded directly in code.

   The shader uses a multi-channel signed distance field approach for
   resolution-independent text rendering with high quality antialiasing."
  "// Positions for simple quad geometry
const pos = array(vec2f(0, -1), vec2f(1, -1), vec2f(0, 0), vec2f(1, 0));

struct VertexInput {
  @builtin(vertex_index) vertex : u32,
  @builtin(instance_index) instance : u32,
};

struct VertexOutput {
  @builtin(position) position : vec4f,
  @location(0) texcoord : vec2f,
};

struct Char {
  texOffset: vec2f,
  texExtent: vec2f,
  size: vec2f,
  offset: vec2f,
};

struct FormattedText {
  transform: mat4x4f,
  color: vec4f,
  scale: f32,
  chars: array<vec3f>,
};

struct Camera {
  projection: mat4x4f,
  view: mat4x4f,
};

// Font bindings
@group(0) @binding(0) var fontTexture: texture_2d<f32>;
@group(0) @binding(1) var fontSampler: sampler;
@group(0) @binding(2) var<storage> chars: array<Char>;

// Text bindings
@group(1) @binding(0) var<uniform> camera: Camera;
@group(1) @binding(1) var<storage> text: FormattedText;

@vertex
fn vertexMain(input : VertexInput) -> VertexOutput {
  let textElement = text.chars[input.instance];
  let char = chars[u32(textElement.z)];
  let charPos = (pos[input.vertex] * char.size + textElement.xy + char.offset) * text.scale;

  var output : VertexOutput;
  output.position = camera.projection * camera.view * text.transform * vec4f(charPos, 0, 1);

  output.texcoord = pos[input.vertex] * vec2f(1, -1);
  output.texcoord *= char.texExtent;
  output.texcoord += char.texOffset;
  return output;
}

fn sampleMsdf(texcoord: vec2f) -> f32 {
  let c = textureSample(fontTexture, fontSampler, texcoord);
  return max(min(c.r, c.g), min(max(c.r, c.g), c.b));
}

// Antialiasing technique from Paul Houx
// https://github.com/Chlumsky/msdfgen/issues/22#issuecomment-234958005
@fragment
fn fragmentMain(input : VertexOutput) -> @location(0) vec4f {
  // pxRange (AKA distanceRange) comes from the msdfgen tool. Don McCurdy's tool
  // uses the default which is 4.
  let pxRange = 4.0;
  let sz = vec2f(textureDimensions(fontTexture, 0));
  let dx = sz.x*length(vec2f(dpdxFine(input.texcoord.x), dpdyFine(input.texcoord.x)));
  let dy = sz.y*length(vec2f(dpdxFine(input.texcoord.y), dpdyFine(input.texcoord.y)));
  let toPixels = pxRange * inverseSqrt(dx * dx + dy * dy);
  let sigDist = sampleMsdf(input.texcoord) - 0.5;
  let pxDist = sigDist * toPixels;

  let edgeWidth = 0.5;

  let alpha = smoothstep(-edgeWidth, edgeWidth, pxDist);

  if (alpha < 0.001) {
    discard;
  }

  return vec4f(text.color.rgb, text.color.a * alpha);
}")

;; =============================================================================
;; Anchor Point System
;; =============================================================================

(def anchor-points
  "Predefined anchor points for text alignment.
   Anchor determines the origin point for positioning:
   - (0, 0) = top-left corner
   - (0.5, 0.5) = center
   - (1, 1) = bottom-right corner"
  {:left-top {:x 0 :y 0}
   :center-top {:x 0.5 :y 0}
   :right-top {:x 1 :y 0}
   :left-center {:x 0 :y 0.5}
   :center {:x 0.5 :y 0.5}
   :right-center {:x 1 :y 0.5}
   :left-bottom {:x 0 :y 1}
   :center-bottom {:x 0.5 :y 1}
   :right-bottom {:x 1 :y 1}
   ;; Aliases
   :left :left-center
   :right :right-center
   :top :center-top
   :bottom :center-bottom})

(defn- resolve-anchor
  "Convert anchor specification to {:x 0-1 :y 0-1} map.
   Accepts:
   - keyword: :center, :left-top, etc.
   - map: {:x 0.5 :y 0.5}
   - number: 0.5 (applies to both x and y)"
  [anchor]
  (cond
    (keyword? anchor)
    (let [resolved (get anchor-points anchor)]
      (if (keyword? resolved)
        (get anchor-points resolved)
        resolved))

    (map? anchor)
    anchor

    (number? anchor)
    {:x anchor :y anchor}

    :else
    {:x 0 :y 0}))

;; =============================================================================
;; Private Helpers
;; =============================================================================

(defn- create-msdf-sampler [device]
  (gpu/create-sampler device
    {:label "MSDF text sampler"
     :minFilter "linear"
     :magFilter "linear"
     :mipmapFilter "linear"
     :maxAnisotropy 16}))

(defn- load-texture [device queue url]
  (js/Promise.
    (fn [resolve reject]
      (-> (js/fetch url)
        (.then #(.blob %))
        (.then js/createImageBitmap)
        (.then (fn [bitmap]
                 (let [texture (gpu/create-texture-2d device
                                 {:label (str "MSDF font texture " url)
                                  :size #js [(.-width bitmap) (.-height bitmap) 1]
                                  :format "rgba8unorm"
                                  :usage (bit-or js/GPUTextureUsage.TEXTURE_BINDING
                                           js/GPUTextureUsage.COPY_DST
                                           js/GPUTextureUsage.RENDER_ATTACHMENT)})]
                   (gpu/copy-external-image-to-texture queue
                     {:source bitmap}
                     {:texture texture}
                     [(.-width bitmap) (.-height bitmap)])
                   (resolve texture))))
        (.catch reject)))))

(defn- create-char-buffer [device chars scaleW scaleH]
  (let [chars-data (js->clj chars :keywordize-keys true)
        char-count (count chars-data)
        buffer (gpu/create-buffer device :storage "MSDF character layout buffer"
                 (* char-count 8 4) ;; 8 floats * 4 bytes per float
                 true) ;; mappedAtCreation
        array (js/Float32Array. (gpu/get-mapped-range buffer))
        u (/ 1 scaleW)
        v (/ 1 scaleH)]

    (doseq [[i char] (map-indexed vector chars-data)]
      (let [offset (* i 8)
            {:keys [x y width height xoffset yoffset]} char]
        (aset array offset (* x u))              ; texOffset.x
        (aset array (+ offset 1) (* y v))        ; texOffset.y
        (aset array (+ offset 2) (* width u))    ; texExtent.x
        (aset array (+ offset 3) (* height v))   ; texExtent.y
        (aset array (+ offset 4) width)          ; size.x
        (aset array (+ offset 5) height)         ; size.y
        (aset array (+ offset 6) xoffset)        ; offset.x
        (aset array (+ offset 7) (- yoffset))))  ; offset.y (negated)
    
    (gpu/unmap-buffer buffer)
    buffer))

(defn- create-bind-group-layouts [device]
  (let [font-layout (gpu/create-bind-group-layout-explicit device
                      {:label "MSDF font group layout"
                       :entries [{:binding 0
                                  :visibility js/GPUShaderStage.FRAGMENT
                                  :texture {}}
                                 {:binding 1
                                  :visibility js/GPUShaderStage.FRAGMENT
                                  :sampler {}}
                                 {:binding 2
                                  :visibility js/GPUShaderStage.VERTEX
                                  :buffer {:type "read-only-storage"}}]})
        text-layout (gpu/create-bind-group-layout-explicit device
                      {:label "MSDF text group layout"
                       :entries [{:binding 0
                                  :visibility js/GPUShaderStage.VERTEX
                                  :buffer {}}
                                 {:binding 1
                                  :visibility (bit-or js/GPUShaderStage.VERTEX
                                                js/GPUShaderStage.FRAGMENT)
                                  :buffer {:type "read-only-storage"}}]})]
    {:font font-layout
     :text text-layout}))

(defn- create-pipeline [device font-bind-group-layout text-bind-group-layout color-format]
  (let [shader-module (gpu/create-shader-module device
                        {:label "MSDF text shader"
                         :code msdf-shader-code})
        layout (gpu/create-pipeline-layout device
                 {:bindGroupLayouts [font-bind-group-layout text-bind-group-layout]})]
    (gpu/create-render-pipeline-async device
      {:label "MSDF text pipeline"
       :layout layout
       :vertex {:module shader-module
                :entryPoint "vertexMain"}
       :fragment {:module shader-module
                  :entryPoint "fragmentMain"
                  :targets [{:format color-format
                             :blend {:color {:srcFactor "src-alpha"
                                             :dstFactor "one-minus-src-alpha"}
                                     :alpha {:srcFactor "one"
                                             :dstFactor "one"}}}]}
       :primitive {:topology "triangle-strip"
                   :stripIndexFormat "uint32"}})))

(defn- create-char-map [chars]
  (reduce (fn [m [idx char]]
            (assoc m (get char :id)
              (assoc char :charIndex idx)))
    {}
    (map-indexed vector chars)))

(defn- measure-text
  "Measure text dimensions and generate character positions"
  [font text]
  (let [chars (:chars font)
        line-height (:line-height font)
        default-char (first (vals chars))]

    (loop [i 0
           x 0.0
           y 0.0
           positions []
           max-width 0.0]
      (if (>= i (count text))
        {:width (max max-width x)
         :height (+ y line-height)
         :positions positions}
        (let [char-code (.charCodeAt text i)
              char-data (get chars char-code default-char)]
          (case char-code
            10 (recur (inc i) 0.0 (- y line-height) positions (max max-width x))  ; newline
            13 (recur (inc i) x y positions max-width)  ; carriage-return
            32 (recur (inc i) (+ x (get char-data :xadvance 12)) y positions max-width)  ; space
            (let [char-index (get char-data :charIndex 0)
                  xadvance (get char-data :xadvance 12)]
              (recur (inc i)
                (+ x xadvance)
                y
                (conj positions [x y char-index])
                max-width))))))))

;; =============================================================================
;; Public API
;; =============================================================================

(defn load-font!
  "Load an MSDF font from a JSON file. Returns a promise that resolves to a font object.

   Arguments:
   - device: GPU device
   - font-json-url: URL to the font JSON file (e.g., 'fonts/ya-hei-ascii-msdf.json')
   - color-format: Canvas color format (e.g., 'bgra8unorm')"
  [device queue font-json-url color-format]
  (js/Promise.
    (fn [resolve reject]
      (-> (js/fetch font-json-url)
        (.then #(.json %))
        (.then (fn [^js json]
                 (let [chars (js->clj (.-chars json) :keywordize-keys true)
                       common (.-common json)
                       pages (js->clj (.-pages json))
                       scaleW (.-scaleW common)
                       scaleH (.-scaleH common)
                       line-height (.-lineHeight common)
                       layouts (create-bind-group-layouts device)

                       ;; Load texture (assume single page for now)
                       base-url (let [i (.lastIndexOf font-json-url "/")]
                                  (if (not= i -1)
                                    (subs font-json-url 0 (inc i))
                                    ""))
                       texture-url (str base-url (first pages))]

                   (-> (load-texture device queue texture-url)
                     (.then (fn [texture]
                              (let [char-buffer (create-char-buffer device chars scaleW scaleH)
                                    sampler (create-msdf-sampler device)
                                    font-bind-group (gpu/create-bind-group device
                                                      (clj->js
                                                        {:label "MSDF font bind group"
                                                         :layout (:font layouts)
                                                         :entries [{:binding 0
                                                                    :resource (gpu/create-texture-view texture)}
                                                                   {:binding 1
                                                                    :resource sampler}
                                                                   {:binding 2
                                                                    :resource {:buffer char-buffer}}]}))
                                    char-map (create-char-map chars)]

                                (-> (create-pipeline device
                                      (:font layouts)
                                      (:text layouts)
                                      color-format)
                                  (.then (fn [pipeline]
                                           (resolve {:device device
                                                     :queue queue
                                                     :pipeline pipeline
                                                     :font-bind-group font-bind-group
                                                     :text-bind-group-layout (:text layouts)
                                                     :line-height line-height
                                                     :chars char-map
                                                     :sampler sampler})))))))
                     (.catch reject)))))
        (.catch reject)))))

(defn create-camera-buffer
  "Create a uniform buffer for camera matrices (projection + view).
   Returns a GPU buffer with 128 bytes (two 4x4 matrices)."
  [device]
  (let [buffer (gpu/create-buffer device :uniform "MSDF camera buffer" 128 true)
        array (js/Float32Array. (gpu/get-mapped-range buffer))]
    ;; Initialize with identity matrices
    (doseq [i (range 32)]
      (aset array i (if (or (= i 0) (= i 5) (= i 10) (= i 15)
                          (= i 16) (= i 21) (= i 26) (= i 31))
                      1.0 0.0)))
    (gpu/unmap-buffer buffer)
    buffer))

(defn update-camera!
  "Update camera buffer with new projection and view matrices.

   Arguments:
   - device: GPU device
   - buffer: Camera buffer created with create-camera-buffer
   - projection: 16-element array/Float32Array for projection matrix
   - view: 16-element array/Float32Array for view matrix"
  [device buffer projection view]
  (let [data (js/Float32Array. 32)]
    ;; Copy projection (first 16 floats)
    (doseq [i (range 16)]
      (aset data i (aget projection i)))
    ;; Copy view (next 16 floats)
    (doseq [i (range 16)]
      (aset data (+ i 16) (aget view i)))
    (gpu/write-buffer device buffer 0 data)))

(defn ortho-projection
  "Create orthographic projection matrix for 2D screen-space rendering.
   Maps [left, right] × [bottom, top] to normalized device coordinates.

   For screen-space text rendering:
     (ortho-projection 0 width 0 height)
   creates a Y-up coordinate system where (0,0) is bottom-left.

   Arguments:
   - left: Left edge of viewport
   - right: Right edge of viewport
   - bottom: Bottom edge of viewport
   - top: Top edge of viewport

   Returns Float32Array mat4x4."
  [left right bottom top]
  (let [rl (/ 1 (- right left))
        tb (/ 1 (- top bottom))]
    (js/Float32Array.
      #js [(* 2 rl) 0 0 0
           0 (* 2 tb) 0 0
           0 0 -1 0
           (* (- (+ right left)) rl)
           (* (- (+ top bottom)) tb)
           0 1])))

(defn identity-view
  "Return a 4x4 identity matrix as Float32Array (for use as view matrix)."
  []
  (js/Float32Array. #js [1 0 0 0  0 1 0 0  0 0 1 0  0 0 0 1]))

(defn screen-camera!
  "Set up camera for screen-space text rendering in CSS pixel coordinates.

   Creates an orthographic projection where 1 unit = 1 CSS pixel.
   Text rendered in this coordinate space is crisp at any device pixel ratio
   because MSDF rendering is resolution-independent and the canvas backing
   store provides full physical resolution.

   After calling this, provide all text positions, pixel-scale values, and
   offsets in CSS pixel terms. No DPR adjustment needed.

   Arguments:
   - device: WebGPU device
   - camera-buffer: Camera buffer from create-camera-buffer
   - css-width: Canvas CSS width (e.g. 700, NOT canvas.width which is DPR-scaled)
   - css-height: Canvas CSS height (e.g. 500, NOT canvas.height which is DPR-scaled)"
  [device camera-buffer css-width css-height]
  (let [projection (ortho-projection 0 css-width 0 css-height)
        view (identity-view)]
    (update-camera! device camera-buffer projection view)))

(defn format-text
  "Format text for rendering with anchor-based positioning.

   Arguments:
   - font: Font object from load-font!
   - camera-buffer: Camera buffer from create-camera-buffer
   - text: String to render
   - options: Map with keys:
     - :x, :y - Position in pixels (default 0, 0)
     - :anchor - Alignment point, keyword or {:x 0-1 :y 0-1}
                 :center, :left, :right, :top-left, etc. (default :left-top)
     - :color - [r g b a] (default [1 1 1 1])
     - :pixel-scale - Size multiplier (default 0.5)
     - :transform - mat4x4 transform matrix (optional)

   Returns a formatted text object that can be rendered."
  [font camera-buffer text options]
  (let [device (:device font)
        {:keys [width height positions]} (measure-text font text)

        ;; Extract and resolve options
        x (get options :x 0)
        y (get options :y 0)
        anchor (resolve-anchor (get options :anchor :left-top))
        color (get options :color [1 1 1 1])
        pixel-scale (get options :pixel-scale 0.5)
        transform (get options :transform nil)

        ;; Calculate anchor offset based on text dimensions
        anchor-offset-x (* width (:x anchor))
        anchor-offset-y (* height (:y anchor))

        ;; Create text buffer (16 floats for transform matrix + 4 for color + 1 for scale + 3 padding + positions)
        text-buffer-size (* (+ 24 (* 4 (count positions))) 4) ;; 4 bytes per float
        text-buffer (gpu/create-buffer device :storage "MSDF text buffer"
                      text-buffer-size true) ;; mappedAtCreation
        text-array (js/Float32Array. (gpu/get-mapped-range text-buffer))]

    ;; Set transform matrix (first 16 floats)
    (if transform
      ;; Use provided transform
      (doseq [i (range 16)]
        (aset text-array i (nth transform i)))
      ;; Use identity transform with translation for x,y position
      (doseq [i (range 16)]
        (aset text-array i (cond
                             (#{0 5 10 15} i) 1.0
                             (= i 12) x  ; translate x
                             (= i 13) y  ; translate y
                             :else 0.0))))

    ;; Set color (next 4 floats)
    (let [[r g b a] color]
      (aset text-array 16 r)
      (aset text-array 17 g)
      (aset text-array 18 b)
      (aset text-array 19 a))

    ;; Set pixel scale (next 1 float)
    (aset text-array 20 pixel-scale)

    ;; Padding for alignment (next 3 floats) - required for WGSL struct alignment
    (aset text-array 21 0)
    (aset text-array 22 0)
    (aset text-array 23 0)

    ;; Set character positions with anchor offset (remaining floats, 4 per char - x, y, charIndex, padding)
    (doseq [[idx [char-x char-y char-index]] (map-indexed vector positions)]
      (let [offset (+ 24 (* idx 4))]
        (aset text-array offset (- char-x anchor-offset-x))
        (aset text-array (+ offset 1) (- char-y anchor-offset-y))
        (aset text-array (+ offset 2) char-index)
        (aset text-array (+ offset 3) 0)))  ; padding
    
    (gpu/unmap-buffer text-buffer)

    ;; Create text bind group
    (let [text-bind-group (gpu/create-bind-group device
                            (clj->js
                              {:label "MSDF text bind group"
                               :layout (:text-bind-group-layout font)
                               :entries [{:binding 0
                                          :resource {:buffer camera-buffer}}
                                         {:binding 1
                                          :resource {:buffer text-buffer}}]}))]

      {:font font
       :text-buffer text-buffer
       :text-bind-group text-bind-group
       :char-count (count positions)})))

(defn render-text!
  "Render formatted text using a render pass.

   Arguments:
   - render-pass: Active render pass encoder
   - formatted-text: Text object from format-text"
  [render-pass formatted-text]
  (let [font (:font formatted-text)]
    (gpu/set-pipeline render-pass (:pipeline font))
    (gpu/set-bind-group render-pass 0 (:font-bind-group font))
    (gpu/set-bind-group render-pass 1 (:text-bind-group formatted-text))
    (gpu/draw render-pass 4 (:char-count formatted-text) 0 0)))

;; =============================================================================
;; Convenience API
;; =============================================================================

(defn text
  "Simple API for rendering a single text string with position and anchor.

   Example:
   (text font camera-buffer \"Hello World\"
     {:x 400 :y 300 :anchor :center :color [1 1 1 1]})

   Options: same as format-text"
  [font camera-buffer text-str options]
  (format-text font camera-buffer text-str options))

(defn texts
  "Batch format multiple text strings for efficient rendering.

   text-specs is a seq of {:text \"...\" :x ... :y ... :anchor ... etc}

   Example:
   (texts font camera-buffer
     [{:text \"Title\" :x 400 :y 50 :anchor :center-top}
      {:text \"$150\" :x 100 :y 200 :anchor :right-center :color [0 1 0 1]}])

   Returns a seq of formatted text objects ready to render."
  [font camera-buffer text-specs]
  (mapv (fn [spec]
          (let [text-str (:text spec)
                options (dissoc spec :text)]
            (format-text font camera-buffer text-str options)))
    text-specs))

(defn render-texts!
  "Render multiple formatted texts in sequence.

   Example:
   (let [formatted-texts (texts font camera-buffer specs)]
     (render-texts! render-pass formatted-texts))"
  [render-pass formatted-texts]
  (doseq [formatted-text formatted-texts]
    (render-text! render-pass formatted-text)))
