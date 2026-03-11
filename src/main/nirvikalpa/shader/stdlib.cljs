(ns nirvikalpa.shader.stdlib
  "Standard library of reusable shader fragments - DATA layer

   This module provides common shader fragments as pure data that can be
   composed together using nirvikalpa.shader.compose functions.

   All fragments are PURE DATA - they are shader AST fragments (maps)
   that can be merged into complete shaders."
  (:require [nirvikalpa.shader.ast :as ast]
            [nirvikalpa.shader.compose :as comp]))

;;
;; Lighting Fragments (Pure Data)
;;

(def phong-diffuse
  "Phong diffuse lighting fragment.

   Requires:
     - Input: normal (vec3f) - must already exist in shader
     - Uniform: light_dir (vec3f) - added at group 1, binding 0

   Produces:
     - Local variable: diffuse (f32) - can be used in subsequent calculations

   Usage:
     (comp/merge-shaders base-shader phong-diffuse)"
  {:uniforms [(ast/uniform-binding 1 0 :vec3f "light_dir")]
   :inputs []
   :outputs []
   :body
   [(ast/let-expr "normal_norm" (ast/normalize "normal"))
    (ast/let-expr "light_dir_norm" (ast/normalize (ast/sub (ast/vec3f 0.0 0.0 0.0) "light_dir")))
    (ast/let-expr "diffuse" (ast/max-expr 0.0 (ast/dot "normal_norm" "light_dir_norm")))]})

(def phong-specular
  "Phong specular lighting fragment.

   Requires:
     - Input: normal (vec3f)
     - Input: view_dir (vec3f) - direction from surface to camera
     - Uniform: light_dir (vec3f) - added at group 1, binding 0
     - Uniform: shininess (f32) - added at group 1, binding 1

   Produces:
     - Local variable: specular (f32)

   Usage:
     (comp/merge-shaders base-shader phong-specular)"
  {:uniforms [(ast/uniform-binding 1 0 :vec3f "light_dir")
              (ast/uniform-binding 1 1 :f32 "shininess")]
   :inputs []
   :outputs []
   :body
   [(ast/let-expr "light_dir_norm" (ast/normalize (ast/sub (ast/vec3f 0.0 0.0 0.0) "light_dir")))
    (ast/let-expr "reflect_dir" (ast/reflect "light_dir_norm" "normal"))
    (ast/let-expr "spec" (ast/pow (ast/max-expr 0.0 (ast/dot "view_dir" "reflect_dir")) "shininess"))
    (ast/let-expr "specular" "spec")]})

(def linear-fog
  "Linear fog fragment.

   Requires:
     - Input: distance (f32) - distance from camera to fragment
     - Uniform: fog_near (f32) - added at group 2, binding 0
     - Uniform: fog_far (f32) - added at group 2, binding 1
     - Uniform: fog_color (vec3f) - added at group 2, binding 2

   Produces:
     - Local variable: fog_factor (f32) - blend factor between 0 and 1

   Usage:
     (comp/merge-shaders base-shader linear-fog)"
  {:uniforms [(ast/uniform-binding 2 0 :f32 "fog_near")
              (ast/uniform-binding 2 1 :f32 "fog_far")
              (ast/uniform-binding 2 2 :vec3f "fog_color")]
   :inputs []
   :outputs []
   :body
   [(ast/let-expr "fog_factor"
                  (ast/clamp-expr
                   (ast/div
                    (ast/sub "distance" "fog_near")
                    (ast/sub "fog_far" "fog_near"))
                   0.0 1.0))]})

;;
;; Composition Helpers (Pure Functions)
;;

(defn with-phong-lighting
  "Add Phong diffuse lighting to fragment shader (pure).

   Adds lighting calculation and multiplies base color by diffuse term.

   Requires:
     - Shader must have 'base_color' variable or input
     - Shader must have 'normal' input

   Example:
     (-> base-fragment-shader
         (with-phong-lighting))"
  [shader]
  (-> shader
      (comp/merge-shaders phong-diffuse)
      (comp/add-statement
       (ast/let-expr "lit_color"
                     (ast/mul "base_color" (ast/add "diffuse" 0.1))))))

(defn with-specular
  "Add Phong specular lighting to fragment shader (pure).

   Adds specular highlights to existing lighting.

   Requires:
     - Shader must have 'normal' input
     - Shader must have 'view_dir' input
     - Shader must already have diffuse lighting

   Example:
     (-> base-fragment-shader
         (with-phong-lighting)
         (with-specular))"
  [shader]
  (-> shader
      (comp/merge-shaders phong-specular)
      (comp/add-statement
       (ast/let-expr "lit_color_with_spec"
                     (ast/add "lit_color" (ast/mul "specular" (ast/vec3f 1.0 1.0 1.0)))))))

(defn with-fog
  "Add linear fog to fragment shader (pure).

   Blends lit color with fog color based on distance.

   Requires:
     - Shader must have 'distance' variable (fragment depth or calculated)
     - Shader must have 'lit_color' variable from lighting

   Example:
     (-> base-fragment-shader
         (with-phong-lighting)
         (with-fog))"
  [shader]
  (-> shader
      (comp/merge-shaders linear-fog)
      (comp/add-statement
       (ast/let-expr "final_color"
                     (ast/mix "lit_color" "fog_color" "fog_factor")))))

;;
;; Common Shader Patterns (Pure Data)
;;

(def passthrough-vertex
  "Simple passthrough vertex shader fragment.

   Just transforms position by MVP matrix and passes through color.

   Requires:
     - Input: position (vec4f)
     - Input: color (vec3f)
     - Uniform: mvp (mat4x4f)

   Outputs:
     - @builtin(position) - clip space position
     - @location(0) frag_color - passed to fragment shader"
  (ast/vertex-shader
   {:inputs [(ast/input-attribute 0 :vec4f "position")
             (ast/input-attribute 1 :vec3f "color")]
    :outputs [(ast/output-attribute {:builtin :position} :vec4f)
              (ast/output-attribute {:location 0 :name "frag_color"} :vec3f)]
    :uniforms [(ast/uniform-binding 0 0 :mat4x4f "mvp")]
    :body [(ast/let-expr "clip_pos" (ast/mul "mvp" "position"))
           (ast/return-expr {:position "clip_pos"
                             :frag_color "color"})]}))

(def constant-color-fragment
  "Simple constant color fragment shader.

   Just outputs a solid color.

   Outputs:
     - @location(0) out_color - RGBA color"
  (ast/fragment-shader
   {:inputs []
    :outputs [(ast/output-attribute {:location 0 :name "out_color"} :vec4f)]
    :uniforms [(ast/uniform-binding 0 0 :vec4f "color")]
    :body [(ast/return-expr "color")]}))

(def textured-fragment
  "Basic textured fragment shader.

   Samples texture at UV coordinates.

   Requires:
     - Input: uv (vec2f)
     - Uniform: tex (texture-2d) at group 0, binding 0
     - Uniform: samp (sampler) at group 0, binding 1

   Outputs:
     - @location(0) out_color - sampled color"
  (ast/fragment-shader
   {:inputs [(ast/input-attribute 0 :vec2f "uv")]
    :outputs [(ast/output-attribute {:location 0 :name "out_color"} :vec4f)]
    :uniforms [(ast/uniform-binding 0 0 :texture-2d "tex")
               (ast/uniform-binding 0 1 :sampler "samp")]
    :body [(ast/return-expr [:texture-sample "tex" "samp" "uv"])]}))
