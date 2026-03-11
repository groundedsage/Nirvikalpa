(ns nirvikalpa.native.wgpu-raw
  "Raw JNA bindings to wgpu-native C API

   This namespace provides direct FFI access to wgpu-native functions.
   Based on webgpu.h and wgpu.h headers from wgpu-native project.

   See: https://github.com/gfx-rs/wgpu-native"
  (:import [com.sun.jna Native Pointer Structure]
           [com.sun.jna.ptr PointerByReference]))

;;
;; Load Native Library
;;

(defonce wgpu-lib
  (try
    (Native/load "wgpu_native" Object)
    (catch UnsatisfiedLinkError e
      (println "Error loading wgpu_native library:" (.getMessage e))
      (println "Make sure libwgpu_native.dylib is in java.library.path")
      (throw e))))

;;
;; Type Definitions
;;

(defrecord WGPUInstance [ptr])
(defrecord WGPUAdapter [ptr])
(defrecord WGPUDevice [ptr])
(defrecord WGPUSurface [ptr])
(defrecord WGPUQueue [ptr])
(defrecord WGPUShaderModule [ptr])
(defrecord WGPURenderPipeline [ptr])
(defrecord WGPUCommandEncoder [ptr])
(defrecord WGPURenderPassEncoder [ptr])
(defrecord WGPUTextureView [ptr])

;;
;; Core Functions (will implement via JNA)
;;

(comment
  ;; These will be implemented using JNA's Native/invoke
  ;; For now, this is a stub showing the API we need

  (defn create-instance []
    "WGPUInstance wgpuCreateInstance(WGPUInstanceDescriptor const * descriptor)"
    (->WGPUInstance (Native/invoke wgpu-lib "wgpuCreateInstance" Pointer (into-array [nil]))))

  (defn request-adapter [instance surface]
    "void wgpuInstanceRequestAdapter(...)"
    ;; Async callback - need to handle
    )

  (defn request-device [adapter]
    "void wgpuAdapterRequestDevice(...)"
    ;; Async callback - need to handle
    )

  (defn create-surface-from-metal-layer [instance metal-layer]
    "WGPUSurface wgpuInstanceCreateSurface(...)"
    ;; Platform-specific surface creation
    ))

;; Placeholder - will flesh out as we implement
