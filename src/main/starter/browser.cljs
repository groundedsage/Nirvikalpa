(ns starter.browser
  (:require [nirvikalpa.hello-triangle :refer [render-triangle]]

            ;; Starter
            [starter.samples.basic-graphics.hello-triangle :refer [red-frag triangle-vert RenderTriangle]]
            [starter.samples.basic-graphics.triangle-dsl :refer [RenderTriangleDSL]]
            [starter.samples.basic-graphics.hello-triangleMSAA :refer [RenderTriangleMSAA]]
            [starter.samples.basic-graphics.rotating-cube :refer [RenderRotatingCube]]
            [starter.samples.basic-graphics.rotating-cube-ga :refer [RenderRotatingCubeGA]]
            [starter.samples.basic-graphics.two-cubes :refer [TwoCubes]]
            [starter.samples.basic-graphics.textured-cube :refer [TexturedCube]]
            [starter.samples.basic-graphics.instanced-cubes :refer [InstancedCubes]]
            [starter.samples.basic-graphics.fractal-cube :refer [FractalCube]]
            [starter.samples.basic-graphics.cubemap-cube :refer [CubemapCube]]

            ;; Text Rendering samples
            [starter.samples.text.msdf-text :refer [RenderMsdfText]]

            ;; 2D Rendering samples
            [starter.samples.rendering-2d.shapes.rectangle :refer [Render2DRectangle]]
            [starter.samples.rendering-2d.shapes.circle :refer [Render2DCircle]]
            [starter.samples.rendering-2d.shapes.ellipse :refer [Render2DEllipse]]
            [starter.samples.rendering-2d.shapes.line :refer [Render2DLine]]
            [starter.samples.rendering-2d.shapes.triangle :refer [Render2DTriangle]]
            [starter.samples.rendering-2d.shapes.rounded-rect :refer [Render2DRoundedRect]]
            [starter.samples.rendering-2d.shapes.point :refer [Render2DPoint]]
            [starter.samples.rendering-2d.shapes.polygon :refer [Render2DPolygon]]
            [starter.samples.rendering-2d.shapes.arc :refer [Render2DArc]]
            [starter.samples.rendering-2d.shapes.star :refer [Render2DStar]]
            [starter.samples.rendering-2d.shapes.ring :refer [Render2DRing]]
            [starter.samples.rendering-2d.shapes.circle-stroke :refer [Render2DCircleStroke]]
            [starter.samples.rendering-2d.shapes.rect-stroke :refer [Render2DRectStroke]]
            [starter.samples.rendering-2d.shapes.annular-sector :refer [Render2DAnnularSector]]
            [starter.samples.rendering-2d.shapes.dashed-line :refer [Render2DDashedLine]]
            [starter.samples.rendering-2d.shapes.ellipse-stroke :refer [Render2DEllipseStroke]]
            [starter.samples.rendering-2d.shapes.triangle-stroke :refer [Render2DTriangleStroke]]
            [starter.samples.rendering-2d.shapes.polygon-stroke :refer [Render2DPolygonStroke]]
            [starter.samples.rendering-2d.shapes.star-stroke :refer [Render2DStarStroke]]
            [starter.samples.rendering-2d.shapes.arc-stroke :refer [Render2DArcStroke]]
            [starter.samples.rendering-2d.shapes.quad :refer [Render2DQuad]]
            [starter.samples.rendering-2d.shapes.diamond :refer [Render2DDiamond]]
            [starter.samples.rendering-2d.shapes.cross :refer [Render2DCross]]
            [starter.samples.rendering-2d.shapes.x-cross :refer [Render2DXCross]]
            [starter.samples.rendering-2d.shapes.capsule :refer [Render2DCapsule]]
            [starter.samples.rendering-2d.shapes.dotted-line :refer [Render2DDottedLine]]
            [starter.samples.rendering-2d.curves.quadratic-bezier :refer [Render2DQuadraticBezier]]
            [starter.samples.rendering-2d.curves.cubic-bezier :refer [Render2DCubicBezier]]
            [starter.samples.rendering-2d.gradients.linear :refer [Render2DLinearGradient]]
            [starter.samples.rendering-2d.gradients.radial :refer [Render2DRadialGradient]]
            [starter.samples.rendering-2d.gradients.sweep :refer [Render2DSweepGradient]]

            ;; High-Level 2D API Examples
            [starter.samples.api-2d-example :refer [Render2DAPIExample1
                                                    Render2DAPIExample2
                                                    Render2DAPIExample3
                                                    Render2DAPIExample4
                                                    Render2DAPIExample5
                                                    Render2DAPIExample6]]

            [nirvikalpa.gpu :as gpu]

            ;; Shader DSL (for testing in browser console)
            [nirvikalpa.shader.ast :as shader-ast]
            [nirvikalpa.shader.codegen :as shader-gen]
            [nirvikalpa.shader.compose :as shader-compose]
            [nirvikalpa.shader.validate :as shader-validate]
            [nirvikalpa.shader.stdlib :as shader-stdlib]

            ;;  dependencies
            [replicant.dom :as r]))

;; ----------------------------------------------------------------------------
;; Global application state
;; ----------------------------------------------------------------------------

(defonce store (atom {:active-item :triangle
                      :gpu-ready? false
                      ;; Sections with implemented items start expanded
                      :expanded-sections #{:basic-graphics :2d-rendering :high-level-api}}))

(def sources {;; ═══ Basic Graphics ═══
              :hello-triangle-sample ["https://webgpu.github.io/webgpu-samples/?sample=helloTriangle"]
              :triangle ["https://webgpu.github.io/webgpu-samples/?sample=helloTriangle"]
              :triangle-msaa ["https://webgpu.github.io/webgpu-samples/?sample=helloTriangleMSAA"]
              :rotating-cube ["https://webgpu.github.io/webgpu-samples/?sample=rotatingCube"]
              :two-cubes ["https://webgpu.github.io/webgpu-samples/?sample=twoCubes"]
              :textured-cube ["https://webgpu.github.io/webgpu-samples/?sample=texturedCube"]
              :instanced-cube ["https://webgpu.github.io/webgpu-samples/?sample=instancedCube"]
              :fractal-cube ["https://webgpu.github.io/webgpu-samples/?sample=fractalCube"]
              :cubemap ["https://webgpu.github.io/webgpu-samples/?sample=cubemap"]
              ;; ═══ WebGPU Features ═══
              :reversed-z ["https://webgpu.github.io/webgpu-samples/?sample=reversedZ"]
              :render-bundles ["https://webgpu.github.io/webgpu-samples/?sample=renderBundles"]
              :occlusion-query ["https://webgpu.github.io/webgpu-samples/?sample=occlusionQuery"]
              :sampler-parameters ["https://webgpu.github.io/webgpu-samples/?sample=samplerParameters"]
              :timestamp-query ["https://webgpu.github.io/webgpu-samples/?sample=timestampQuery"]
              :blending ["https://webgpu.github.io/webgpu-samples/?sample=blending"]
              ;; ═══ GPGPU Demos ═══
              :compute-boids ["https://webgpu.github.io/webgpu-samples/?sample=computeBoids"]
              :game-of-life ["https://webgpu.github.io/webgpu-samples/?sample=gameOfLife"]
              :bitonic-sort ["https://webgpu.github.io/webgpu-samples/?sample=bitonicSort"]
              ;; ═══ Graphics Techniques ═══
              :cameras ["https://webgpu.github.io/webgpu-samples/?sample=cameras"]
              :normal-map ["https://webgpu.github.io/webgpu-samples/?sample=normalMap"]
              :shadow-mapping ["https://webgpu.github.io/webgpu-samples/?sample=shadowMapping"]
              :deferred-rendering ["https://webgpu.github.io/webgpu-samples/?sample=deferredRendering"]
              :particles ["https://webgpu.github.io/webgpu-samples/?sample=particles"]
              :points ["https://webgpu.github.io/webgpu-samples/?sample=points"]
              :primitive-picking ["https://webgpu.github.io/webgpu-samples/?sample=primitivePicking"]
              :image-blur ["https://webgpu.github.io/webgpu-samples/?sample=imageBlur"]
              :generate-mipmap ["https://webgpu.github.io/webgpu-samples/?sample=generateMipmap"]
              :cornell ["https://webgpu.github.io/webgpu-samples/?sample=cornell"]
              :a-buffer ["https://webgpu.github.io/webgpu-samples/?sample=a-buffer"]
              :skinned-mesh ["https://webgpu.github.io/webgpu-samples/?sample=skinnedMesh"]
              :stencil-mask ["https://webgpu.github.io/webgpu-samples/?sample=stencilMask"]
              :text-rendering-msdf ["https://webgpu.github.io/webgpu-samples/?sample=textRenderingMsdf"]
              :volume-rendering ["https://webgpu.github.io/webgpu-samples/?sample=volumeRenderingTexture3D"]
              :wireframe ["https://webgpu.github.io/webgpu-samples/?sample=wireframe"]
              ;; ═══ Web Platform Integration ═══
              :resize-canvas ["https://webgpu.github.io/webgpu-samples/?sample=resizeCanvas"]
              :resize-observer-hddpi ["https://webgpu.github.io/webgpu-samples/?sample=resizeObserverHDDPI"]
              :transparent-canvas ["https://webgpu.github.io/webgpu-samples/?sample=transparentCanvas"]
              :multiple-canvases ["https://webgpu.github.io/webgpu-samples/?sample=multipleCanvases"]
              :video-uploading ["https://webgpu.github.io/webgpu-samples/?sample=videoUploading"]
              :worker ["https://webgpu.github.io/webgpu-samples/?sample=worker"]
              ;; ═══ Benchmarks ═══
              :animometer ["https://webgpu.github.io/webgpu-samples/?sample=animometer"]
              :workload-simulator ["https://webgpu.github.io/webgpu-samples/?sample=workloadSimulator"]
              ;; ═══ External Samples ═══
              :bundle-culling ["https://toji.github.io/webgpu-bundle-culling/"]
              :metaballs ["https://toji.github.io/webgpu-metaballs/"]
              :pristine-grid ["https://toji.github.io/pristine-grid-webgpu/"]
              :clustered-shading ["https://toji.github.io/webgpu-clustered-shading/"]
              :spookyball ["https://spookyball.com"]
              :marching-cubes ["https://tcoppex.github.io/webgpu-marchingcubes/"]
              :alpha-to-coverage ["https://kai.graphics/alpha-to-coverage-emulator/"]
              :particle-life ["https://gpu-life.silverspace.io?sample"]
              ;; 2D Primitives with Skia + Quil references
              :2d-rect ["https://fiddle.skia.org/c/@Canvas_drawRect"
                        "http://quil.info/api/shape/2d-primitives#rect"]
              :2d-circle ["https://fiddle.skia.org/c/@Canvas_drawCircle"
                          "http://quil.info/api/shape/2d-primitives#ellipse"]
              :2d-ellipse ["https://fiddle.skia.org/c/@Canvas_drawOval"
                           "http://quil.info/api/shape/2d-primitives#ellipse"]
              :2d-line ["https://fiddle.skia.org/c/@Canvas_drawLine"
                        "http://quil.info/api/shape/2d-primitives#line"]
              :2d-triangle ["https://fiddle.skia.org/c/@Canvas_drawPath"
                            "http://quil.info/api/shape/2d-primitives#triangle"]
              :2d-rounded ["https://fiddle.skia.org/c/@Canvas_drawRoundRect"
                           "http://quil.info/api/shape/2d-primitives#rect"]
              :2d-point ["https://fiddle.skia.org/c/@Canvas_drawPoints"
                         "http://quil.info/api/shape/2d-primitives#point"]
              :2d-polygon ["https://fiddle.skia.org/c/@Canvas_drawPath"]
              :2d-arc ["https://fiddle.skia.org/c/@Canvas_drawArc"
                       "http://quil.info/api/shape/2d-primitives#arc"]
              :2d-star ["https://fiddle.skia.org/c/@Canvas_drawPath"
                        "http://quil.info/api/shape/vertex#begin-shape"]
              :2d-ring ["https://fiddle.skia.org/c/@Canvas_drawCircle"
                        "http://quil.info/api/shape/2d-primitives#ellipse"]
              :2d-circle-stroke ["https://fiddle.skia.org/c/@Canvas_drawCircle"
                                 "http://quil.info/api/color/setting#no-fill"]
              :2d-rect-stroke ["https://fiddle.skia.org/c/@Canvas_drawRect"
                               "http://quil.info/api/color/setting#stroke"]
              :2d-annular ["https://fiddle.skia.org/c/@Canvas_drawArc"
                           "http://quil.info/api/shape/2d-primitives#arc"]
              :2d-dashed ["https://fiddle.skia.org/c/@Canvas_drawLine"
                          "http://quil.info/api/shape/2d-primitives#line"]
              :2d-ellipse-stroke ["https://fiddle.skia.org/c/@Canvas_drawOval"]
              :2d-triangle-stroke ["https://fiddle.skia.org/c/@Canvas_drawPath"]
              :2d-polygon-stroke ["https://fiddle.skia.org/c/@Canvas_drawPath"]
              :2d-star-stroke ["https://fiddle.skia.org/c/@Canvas_drawPath"]
              :2d-arc-stroke ["https://fiddle.skia.org/c/@Canvas_drawArc"]
              :2d-quad ["http://quil.info/api/shape/2d-primitives#quad"]
              :2d-diamond ["https://fiddle.skia.org/c/@Canvas_drawPath"]
              :2d-cross ["https://fiddle.skia.org/c/@Canvas_drawPath"]
              :2d-x-cross ["https://fiddle.skia.org/c/@Canvas_drawPath"]
              :2d-capsule ["https://fiddle.skia.org/c/@Canvas_drawRoundRect"]
              :2d-dotted ["https://fiddle.skia.org/c/@Canvas_drawLine"]
              :2d-bezier-quad ["https://fiddle.skia.org/c/@SkPath_quadTo_example"
                               "http://quil.info/api/shape/curves#bezier-vertex"
                               "https://iquilezles.org/articles/distfunctions2d/"]
              :2d-bezier-cubic ["https://fiddle.skia.org/c/@SkPath_cubicTo"
                                "http://quil.info/api/shape/curves#bezier"
                                "https://github.com/linebender/vello"
                                "https://github.com/google/forma"]
              :2d-linear-gradient ["https://fiddle.skia.org/c/@GradientShader_MakeLinear"]
              :2d-radial-gradient ["https://fiddle.skia.org/c/@skpaint_radial"]
              :2d-sweep-gradient ["https://api.skia.org/classSkGradientShader.html"]})

(def sample-sources
  "Mapping from sample keywords to their source file paths for code display"
  {:triangle "src/main/starter/samples/basic_graphics/hello_triangle.cljs"
   :triangle-dsl "src/main/starter/samples/basic_graphics/triangle_dsl.cljs"
   :hello-triangle "src/main/nirvikalpa/hello_triangle.cljs"
   :triangle-msaa "src/main/starter/samples/basic_graphics/hello_triangleMSAA.cljs"
   :rotating-cube "src/main/starter/samples/basic_graphics/rotating_cube.cljs"
   :rotating-cube-ga "src/main/starter/samples/basic_graphics/rotating_cube_ga.cljs"
   :two-cubes "src/main/starter/samples/basic_graphics/two_cubes.cljs"
   :textured-cube "src/main/starter/samples/basic_graphics/textured_cube.cljs"
   :instanced-cube "src/main/starter/samples/basic_graphics/instanced_cubes.cljs"
   :fractal-cube "src/main/starter/samples/basic_graphics/fractal_cube.cljs"
   :cubemap "src/main/starter/samples/basic_graphics/cubemap_cube.cljs"
   :2d-rect "src/main/starter/samples/rendering_2d/shapes/rectangle.cljs"
   :2d-circle "src/main/starter/samples/rendering_2d/shapes/circle.cljs"
   :2d-ellipse "src/main/starter/samples/rendering_2d/shapes/ellipse.cljs"
   :2d-line "src/main/starter/samples/rendering_2d/shapes/line.cljs"
   :2d-triangle "src/main/starter/samples/rendering_2d/shapes/triangle.cljs"
   :2d-rounded "src/main/starter/samples/rendering_2d/shapes/rounded_rect.cljs"
   :2d-point "src/main/starter/samples/rendering_2d/shapes/point.cljs"
   :2d-polygon "src/main/starter/samples/rendering_2d/shapes/polygon.cljs"
   :2d-arc "src/main/starter/samples/rendering_2d/shapes/arc.cljs"
   :2d-star "src/main/starter/samples/rendering_2d/shapes/star.cljs"
   :2d-ring "src/main/starter/samples/rendering_2d/shapes/ring.cljs"
   :2d-circle-stroke "src/main/starter/samples/rendering_2d/shapes/circle_stroke.cljs"
   :2d-rect-stroke "src/main/starter/samples/rendering_2d/shapes/rect_stroke.cljs"
   :2d-annular "src/main/starter/samples/rendering_2d/shapes/annular_sector.cljs"
   :2d-dashed "src/main/starter/samples/rendering_2d/shapes/dashed_line.cljs"
   :2d-ellipse-stroke "src/main/starter/samples/rendering_2d/shapes/ellipse_stroke.cljs"
   :2d-triangle-stroke "src/main/starter/samples/rendering_2d/shapes/triangle_stroke.cljs"
   :2d-polygon-stroke "src/main/starter/samples/rendering_2d/shapes/polygon_stroke.cljs"
   :2d-star-stroke "src/main/starter/samples/rendering_2d/shapes/star_stroke.cljs"
   :2d-arc-stroke "src/main/starter/samples/rendering_2d/shapes/arc_stroke.cljs"
   :2d-quad "src/main/starter/samples/rendering_2d/shapes/quad.cljs"
   :2d-diamond "src/main/starter/samples/rendering_2d/shapes/diamond.cljs"
   :2d-cross "src/main/starter/samples/rendering_2d/shapes/cross.cljs"
   :2d-x-cross "src/main/starter/samples/rendering_2d/shapes/x_cross.cljs"
   :2d-capsule "src/main/starter/samples/rendering_2d/shapes/capsule.cljs"
   :2d-dotted "src/main/starter/samples/rendering_2d/shapes/dotted_line.cljs"
   :2d-bezier-quad "src/main/starter/samples/rendering_2d/curves/quadratic_bezier.cljs"
   :2d-bezier-cubic "src/main/starter/samples/rendering_2d/curves/cubic_bezier.cljs"
   :2d-bezier-cubic-improved "src/main/starter/samples/rendering_2d/curves/cubic_bezier_improved.cljs"
   :2d-bezier-cubic-flatten "src/main/starter/samples/rendering_2d/curves/cubic_bezier_flatten.cljs"
   :2d-bezier-cubic-loopblinn "src/main/starter/samples/rendering_2d/curves/cubic_bezier_loop_blinn.cljs"
   :2d-bezier-cubic-vello "src/main/starter/samples/rendering_2d/curves/cubic_bezier_vello.cljs"
   :2d-bezier-cubic-vello-prod "src/main/starter/samples/rendering_2d/curves/cubic_bezier_vello_production.cljs"
   :2d-bezier-cubic-vello-euler "src/main/starter/samples/rendering_2d/curves/cubic_bezier_vello_euler.cljs"
   :2d-linear-gradient "src/main/starter/samples/rendering_2d/gradients/linear.cljs"
   :2d-radial-gradient "src/main/starter/samples/rendering_2d/gradients/radial.cljs"
   :2d-sweep-gradient "src/main/starter/samples/rendering_2d/gradients/sweep.cljs"
   :api-example-1 "src/main/starter/samples/api_2d_example.cljs"
   :api-example-2 "src/main/starter/samples/api_2d_example.cljs"
   :api-example-3 "src/main/starter/samples/api_2d_example.cljs"
   :api-example-4 "src/main/starter/samples/api_2d_example.cljs"
   :api-example-5 "src/main/starter/samples/api_2d_example.cljs"
   :api-example-6 "src/main/starter/samples/api_2d_example.cljs"
   :text-rendering-msdf "src/main/starter/samples/text/msdf_text.cljs"})

;; ----------------------------------------------------------------------------
;; Navigation Data Structure - Sections with items and status
;; Status: :done, :in-progress, :todo
;; ----------------------------------------------------------------------------

(def nav-sections
  "Navigation organized into collapsible sections with progress tracking.
   Sections are grouped by :category for visual separation."
  [;; ══════════════════════════════════════════════════════════════════════════
   ;; WEBGPU SAMPLES - Official WebGPU sample implementations
   ;; ══════════════════════════════════════════════════════════════════════════
   {:id :basic-graphics
    :category "WebGPU Samples"
    :title "Basic Graphics"
    :items [{:id :triangle :label "helloTriangle" :status :done}
            {:id :triangle-dsl :label "helloTriangle (DSL)" :status :done}
            {:id :hello-triangle :label "helloTriangle (Alt)" :status :done}
            {:id :triangle-msaa :label "helloTriangleMSAA" :status :done}
            {:id :rotating-cube :label "rotatingCube" :status :done}
            {:id :rotating-cube-ga :label "rotatingCube (GA)" :status :done}
            {:id :two-cubes :label "twoCubes" :status :done}
            {:id :textured-cube :label "texturedCube" :status :done}
            {:id :instanced-cube :label "instancedCube" :status :done}
            {:id :fractal-cube :label "fractalCube" :status :done}
            {:id :cubemap :label "cubemap" :status :done}]}
   {:id :webgpu-features
    :category "WebGPU Samples"
    :title "WebGPU Features"
    :items [{:id :reversed-z :label "reversedZ" :status :todo}
            {:id :render-bundles :label "renderBundles" :status :todo}
            {:id :occlusion-query :label "occlusionQuery" :status :todo}
            {:id :sampler-parameters :label "samplerParameters" :status :todo}
            {:id :timestamp-query :label "timestampQuery" :status :todo}
            {:id :blending :label "blending" :status :todo}]}
   {:id :gpgpu
    :category "WebGPU Samples"
    :title "GPGPU Demos"
    :items [{:id :compute-boids :label "computeBoids" :status :todo}
            {:id :game-of-life :label "gameOfLife" :status :todo}
            {:id :bitonic-sort :label "bitonicSort" :status :todo}]}
   {:id :graphics-techniques
    :category "WebGPU Samples"
    :title "Graphics Techniques"
    :items [{:id :cameras :label "cameras" :status :todo}
            {:id :normal-map :label "normalMap" :status :todo}
            {:id :shadow-mapping :label "shadowMapping" :status :todo}
            {:id :deferred-rendering :label "deferredRendering" :status :todo}
            {:id :particles :label "particles (HDR)" :status :todo}
            {:id :points :label "points" :status :todo}
            {:id :primitive-picking :label "primitivePicking" :status :todo}
            {:id :image-blur :label "imageBlur" :status :todo}
            {:id :generate-mipmap :label "generateMipmap" :status :todo}
            {:id :cornell :label "cornell" :status :todo}
            {:id :a-buffer :label "a-buffer" :status :todo}
            {:id :skinned-mesh :label "skinnedMesh" :status :todo}
            {:id :stencil-mask :label "stencilMask" :status :todo}
            {:id :text-rendering-msdf :label "textRenderingMsdf" :status :done}
            {:id :volume-rendering :label "volumeRendering3D" :status :todo}
            {:id :wireframe :label "wireframe" :status :todo}]}
   {:id :web-platform
    :category "WebGPU Samples"
    :title "Web Platform"
    :items [{:id :resize-canvas :label "resizeCanvas" :status :todo}
            {:id :resize-observer-hddpi :label "resizeObserverHDDPI" :status :todo}
            {:id :transparent-canvas :label "transparentCanvas" :status :todo}
            {:id :multiple-canvases :label "multipleCanvases" :status :todo}
            {:id :video-uploading :label "videoUploading" :status :todo}
            {:id :worker :label "worker" :status :todo}]}
   {:id :benchmarks
    :category "WebGPU Samples"
    :title "Benchmarks"
    :items [{:id :animometer :label "animometer" :status :todo}
            {:id :workload-simulator :label "workloadSimulator" :status :todo}]}
   {:id :external
    :category "WebGPU Samples"
    :title "External Samples"
    :external? true
    :items [{:id :bundle-culling :label "bundleCulling" :status :todo}
            {:id :metaballs :label "metaballs" :status :todo}
            {:id :pristine-grid :label "pristineGrid" :status :todo}
            {:id :clustered-shading :label "clusteredShading" :status :todo}
            {:id :spookyball :label "spookyball" :status :todo}
            {:id :marching-cubes :label "marchingCubes" :status :todo}
            {:id :alpha-to-coverage :label "alphaToCoverage" :status :todo}
            {:id :particle-life :label "particleLife" :status :todo}]}
   ;; ══════════════════════════════════════════════════════════════════════════
   ;; 2D RENDERING - Nirvikalpa's SDF-based primitives
   ;; ══════════════════════════════════════════════════════════════════════════
   {:id :2d-rendering
    :category "2D Rendering"
    :title "Shapes & Primitives"
    :items [{:id :2d-rect :label "Rectangle" :status :done}
            {:id :2d-circle :label "Circle" :status :done}
            {:id :2d-ellipse :label "Ellipse" :status :done}
            {:id :2d-line :label "Line" :status :done}
            {:id :2d-triangle :label "Triangle" :status :done}
            {:id :2d-rounded :label "Rounded Rect" :status :done}
            {:id :2d-point :label "Point" :status :done}
            {:id :2d-polygon :label "Polygon" :status :done}
            {:id :2d-arc :label "Arc" :status :done}
            {:id :2d-star :label "Star" :status :done}
            {:id :2d-ring :label "Ring" :status :done}
            {:id :2d-circle-stroke :label "Circle Stroke" :status :done}
            {:id :2d-rect-stroke :label "Rect Stroke" :status :done}
            {:id :2d-annular :label "Annular Sector" :status :done}
            {:id :2d-dashed :label "Dashed Line" :status :done}
            {:id :2d-ellipse-stroke :label "Ellipse Stroke" :status :done}
            {:id :2d-triangle-stroke :label "Triangle Stroke" :status :done}
            {:id :2d-polygon-stroke :label "Polygon Stroke" :status :done}
            {:id :2d-star-stroke :label "Star Stroke" :status :done}
            {:id :2d-arc-stroke :label "Arc Stroke" :status :done}
            {:id :2d-quad :label "Quadrilateral" :status :done}
            {:id :2d-diamond :label "Diamond" :status :done}
            {:id :2d-cross :label "Cross/Plus" :status :done}
            {:id :2d-x-cross :label "X Cross" :status :done}
            {:id :2d-capsule :label "Capsule" :status :done}
            {:id :2d-dotted :label "Dotted Line" :status :done}
            {:id :2d-bezier-quad :label "Quadratic Bézier" :status :done}
            {:id :2d-bezier-cubic :label "Cubic Bézier" :status :done}
            {:id :2d-linear-gradient :label "Linear Gradient" :status :done}
            {:id :2d-radial-gradient :label "Radial Gradient" :status :done}
            {:id :2d-sweep-gradient :label "Sweep Gradient" :status :done}]}
   ;; ══════════════════════════════════════════════════════════════════════════
   ;; HIGH-LEVEL API - Declarative 2D rendering API
   ;; ══════════════════════════════════════════════════════════════════════════
   {:id :high-level-api
    :category "High-Level API"
    :title "Examples"
    :items [{:id :api-example-1 :label "Single Shape" :status :done}
            {:id :api-example-2 :label "Composition" :status :done}
            {:id :api-example-3 :label "Data-Driven" :status :done}
            {:id :api-example-4 :label "All Shapes" :status :done}
            {:id :api-example-5 :label "New Shapes" :status :done}
            {:id :api-example-6 :label "Scene Graph" :status :done}]}])

;; Build labels map from nav-sections for backward compatibility
(def labels
  (into {}
        (for [section nav-sections
              item (:items section)]
          [(:id item) (:label item)])))

;; ----------------------------------------------------------------------------
;; View functions – pure Hiccup
;; ----------------------------------------------------------------------------

(defn section-progress
  "Calculate progress for a section: {:done n :total m}"
  [section]
  (let [items (:items section)
        done-count (count (filter #(= :done (:status %)) items))
        total-count (count items)]
    {:done done-count :total total-count}))

(defn category-header
  "Render a top-level category header (WebGPU Samples, 2D Rendering, etc)"
  [title]
  [:li {:class ["mt-4" "first:mt-0"]}
   [:div {:class ["text-xs" "font-bold" "uppercase" "tracking-wider"
                  "text-primary" "opacity-70" "px-2" "py-2"
                  "border-b" "border-base-content/10"]}
    title]])

(defn section-header
  "Render a collapsible section header with progress indicator"
  [{:keys [id title external?]} expanded? progress]
  (let [{:keys [done total]} progress
        all-done? (= done total)
        none-done? (zero? done)]
    [:li {:class ["menu-title" "cursor-pointer" "select-none"
                  "hover:bg-base-300/50" "rounded-lg" "mx-1" "my-0.5"
                  "transition-colors" "duration-150"]
          :on {:click [:toggle-section id]}}
     [:div {:class ["flex" "items-center" "justify-between" "w-full" "py-1"]}
      [:div {:class ["flex" "items-center" "gap-3"]}
       ;; Expand/collapse indicator - clean chevron with rotation
       [:span {:class ["text-[10px]" "text-base-content/60"
                       "transition-transform" "duration-200" "inline-block"
                       "w-3" "text-center"]
               :style {:transform (if expanded? "rotate(90deg)" "rotate(0deg)")}}
        "▶"]
       ;; Section title
       [:span {:class ["text-sm" "font-medium"]} title]
       ;; External indicator
       (when external?
         [:span {:class ["text-[10px]" "opacity-40"]} "↗"])]
      ;; Progress indicator - compact pill style
      [:div {:class ["flex" "items-center"]}
       (cond
         all-done?
         [:span {:class ["text-success" "text-xs" "bg-success/10"
                         "px-1.5" "py-0.5" "rounded-full"]} "✓"]
         none-done?
         [:span {:class ["text-[10px]" "opacity-40" "bg-base-content/5"
                         "px-1.5" "py-0.5" "rounded-full"]}
          (str "0/" total)]
         :else
         [:span {:class ["text-[10px]" "bg-base-content/5"
                         "px-1.5" "py-0.5" "rounded-full"]}
          [:span {:class ["text-success" "font-medium"]} done]
          [:span {:class ["opacity-50"]} (str "/" total)]])]]]))

(defn nav-item
  "Render a single navigation item"
  [{:keys [id label status]} active-item external?]
  (let [is-todo? (= status :todo)
        is-active? (= active-item id)]
    [:li {:class ["ml-4"]}  ; Indent items under section headers
     [:a {:class [(when is-active? "menu-active")
                  (when is-todo? "opacity-40")
                  "text-sm" "py-1.5" "min-h-0" "h-auto"
                  "transition-all" "duration-150"]
          :on {:click [:select-item id]}}
      ;; Item label (without emoji - status shown by opacity)
      [:span label]
      ;; External link indicator
      (when external?
        [:span {:class ["text-[10px]" "opacity-40" "ml-1"]} "↗"])]]))

(defn sidebar [{:keys [active-item expanded-sections]}]
  [:div {:class ["flex" "flex-col" "gap-4"]}
   [:h1 {:class ["text-lg" "font-bold" "text-center" "mb-4"]}
    [:span {:class ["bg-gradient-to-r" "from-primary" "to-secondary"
                    "bg-clip-text" "text-transparent"]}
     "Nirvikalpa"]]
   [:ul {:class ["menu" "bg-base-200" "rounded-box" "w-64" "text-sm" "gap-0"]}
    (let [sections-with-idx (map-indexed vector nav-sections)]
      (for [[idx section] sections-with-idx]
        (let [section-id (:id section)
              expanded? (contains? expanded-sections section-id)
              progress (section-progress section)
              current-category (:category section)
              prev-section (when (pos? idx) (nth nav-sections (dec idx)))
              prev-category (:category prev-section)
              show-category-header? (not= current-category prev-category)]
          (list
           ;; Category header (only when category changes)
           (when show-category-header?
             ^{:key (str "cat-" current-category)}
             (category-header current-category))
           ;; Section header (always visible)
           ^{:key (str "header-" (name section-id))}
           (section-header section expanded? progress)
           ;; Section items (only when expanded)
           (when expanded?
             (for [item (:items section)]
               ^{:key (str "item-" (name (:id item)))}
               (nav-item item active-item (:external? section))))))))]])

(defonce !render-id (atom nil))

(defn CanvasWrapper [active-item gpu-ready? RenderTriangle]
  (reset! !render-id active-item)
  (let [dpr (or (.-devicePixelRatio js/window) 1)
        css-size 512
        physical-size (* css-size dpr)]
    [:div {:class ["flex" "flex-col" "gap-4"]}
     (if-not gpu-ready?
       [:p "Initializing GPU..."]
       [:canvas {:id "gpu-canvas"
                 :height physical-size
                 :width physical-size
                 :style {:width (str css-size "px")
                         :height (str css-size "px")
                         :background-color "blue"}
                 :replicant/on-render (fn [e]
                                        (RenderTriangle {:node (:replicant/node e)
                                                         :!render-id !render-id}))}])]))

(defn main-view [{:keys [active-item gpu-ready?]}]
  [:div {:class ["flex" "flex-col" "gap-4"]}
   ;; Header with title and source links
   [:div {:class ["flex" "gap-4"]}
    [:h1 {:class "font-bold"} (get labels active-item (str active-item))]
    (let [source-links (get sources active-item)]
      (if (vector? source-links)
        ;; Multiple sources - show as "Sources: [link1] [link2]"
        [:div {:class ["flex" "gap-2" "items-center"]}
         [:span "Sources:"]
         (for [[idx url] (map-indexed vector source-links)]
           [:a {:key idx
                :class "underline"
                :href url
                :target "_blank"}
            (cond
              (clojure.string/includes? url "fiddle.skia.org") "Skia"
              (clojure.string/includes? url "quil.info") "Quil"
              (clojure.string/includes? url "iquilezles.org") "IQ"
              (clojure.string/includes? url "webgpu") "WebGPU"
              :else (str "Ref" (inc idx)))])]
        ;; Single source or no source
        (when source-links
          [:a {:class "underline"
               :href source-links}
           "Source"])))]

   [:div {:class ["flex" "items-center" "justify-center"]
          :style {:min-height "512px"
                  :min-width "512px"}}
    (case active-item
      :triangle (CanvasWrapper active-item gpu-ready? RenderTriangle)
      :triangle-dsl (CanvasWrapper active-item gpu-ready? RenderTriangleDSL)
      :hello-triangle (CanvasWrapper active-item gpu-ready? render-triangle)
      :triangle-msaa (CanvasWrapper active-item gpu-ready? RenderTriangleMSAA)
      :rotating-cube (CanvasWrapper active-item gpu-ready? RenderRotatingCube)
      :rotating-cube-ga (CanvasWrapper active-item gpu-ready? RenderRotatingCubeGA)
      :two-cubes (CanvasWrapper active-item gpu-ready? TwoCubes)
      :textured-cube (CanvasWrapper active-item gpu-ready? TexturedCube)
      :instanced-cube (CanvasWrapper active-item gpu-ready? InstancedCubes)
      :fractal-cube (CanvasWrapper active-item gpu-ready? FractalCube)
      :cubemap (CanvasWrapper active-item gpu-ready? CubemapCube)
      :2d-rect (CanvasWrapper active-item gpu-ready? Render2DRectangle)
      :2d-circle (CanvasWrapper active-item gpu-ready? Render2DCircle)
      :2d-ellipse (CanvasWrapper active-item gpu-ready? Render2DEllipse)
      :2d-line (CanvasWrapper active-item gpu-ready? Render2DLine)
      :2d-triangle (CanvasWrapper active-item gpu-ready? Render2DTriangle)
      :2d-rounded (CanvasWrapper active-item gpu-ready? Render2DRoundedRect)
      :2d-point (CanvasWrapper active-item gpu-ready? Render2DPoint)
      :2d-polygon (CanvasWrapper active-item gpu-ready? Render2DPolygon)
      :2d-arc (CanvasWrapper active-item gpu-ready? Render2DArc)
      :2d-star (CanvasWrapper active-item gpu-ready? Render2DStar)
      :2d-ring (CanvasWrapper active-item gpu-ready? Render2DRing)
      :2d-circle-stroke (CanvasWrapper active-item gpu-ready? Render2DCircleStroke)
      :2d-rect-stroke (CanvasWrapper active-item gpu-ready? Render2DRectStroke)
      :2d-annular (CanvasWrapper active-item gpu-ready? Render2DAnnularSector)
      :2d-dashed (CanvasWrapper active-item gpu-ready? Render2DDashedLine)
      :2d-ellipse-stroke (CanvasWrapper active-item gpu-ready? Render2DEllipseStroke)
      :2d-triangle-stroke (CanvasWrapper active-item gpu-ready? Render2DTriangleStroke)
      :2d-polygon-stroke (CanvasWrapper active-item gpu-ready? Render2DPolygonStroke)
      :2d-star-stroke (CanvasWrapper active-item gpu-ready? Render2DStarStroke)
      :2d-arc-stroke (CanvasWrapper active-item gpu-ready? Render2DArcStroke)
      :2d-quad (CanvasWrapper active-item gpu-ready? Render2DQuad)
      :2d-diamond (CanvasWrapper active-item gpu-ready? Render2DDiamond)
      :2d-cross (CanvasWrapper active-item gpu-ready? Render2DCross)
      :2d-x-cross (CanvasWrapper active-item gpu-ready? Render2DXCross)
      :2d-capsule (CanvasWrapper active-item gpu-ready? Render2DCapsule)
      :2d-dotted (CanvasWrapper active-item gpu-ready? Render2DDottedLine)
      :2d-bezier-quad (CanvasWrapper active-item gpu-ready? Render2DQuadraticBezier)
      :2d-bezier-cubic (CanvasWrapper active-item gpu-ready? Render2DCubicBezier)
      :2d-linear-gradient (CanvasWrapper active-item gpu-ready? Render2DLinearGradient)
      :2d-radial-gradient (CanvasWrapper active-item gpu-ready? Render2DRadialGradient)
      :2d-sweep-gradient (CanvasWrapper active-item gpu-ready? Render2DSweepGradient)
      :api-example-1 (CanvasWrapper active-item gpu-ready? Render2DAPIExample1)
      :api-example-2 (CanvasWrapper active-item gpu-ready? Render2DAPIExample2)
      :api-example-3 (CanvasWrapper active-item gpu-ready? Render2DAPIExample3)
      :api-example-4 (CanvasWrapper active-item gpu-ready? Render2DAPIExample4)
      :api-example-5 (CanvasWrapper active-item gpu-ready? Render2DAPIExample5)
      :api-example-6 (CanvasWrapper active-item gpu-ready? Render2DAPIExample6)
      :text-rendering-msdf (CanvasWrapper active-item gpu-ready? RenderMsdfText)
      nil)]])

(defn app [state]
  [:div {:class ["p-4" "bg-base-300"]}

   [:div {:class ["flex" "gap-8"]}
    (sidebar state)
    (main-view state)]])

;; ----------------------------------------------------------------------------
;; Rendering & event dispatch
;; ----------------------------------------------------------------------------

(defonce root-el (js/document.getElementById "app"))

(defn render! [state]
  (r/render root-el (app state)))

(defn ^:dev/after-load start []
  ;; 1. render *every* time the store changes
  (gpu/setup! store)
  (add-watch store ::render
             (fn [_ _ _ new-state] (render! new-state)))

  ;; 2. global handler for data-driven events
  (r/set-dispatch!
   (fn [e [action & args]]
     (case action
       :select-item (swap! store assoc :active-item (first args))
       :toggle-section (let [section-id (first args)]
                         (swap! store update :expanded-sections
                                (fn [sections]
                                  (if (contains? sections section-id)
                                    (disj sections section-id)
                                    (conj sections section-id)))))
       nil)))

  ;; 3. initial render
  (render! @store))

;; Called once from <script> or shadow-cljs hot-reload hook
(defn init [] (start))
