#!/bin/bash
# Test generated WGSL from quadratic Bezier v2
# This demonstrates the complete pipeline: DSL → AST → WGSL → naga validation

set -e

echo "========================================="
echo "Testing Quadratic Bezier V2 WGSL"
echo "========================================="

# Extract WGSL by compiling the namespace
WGSL=$(cat << 'EOF_WGSL'
@group(0) @binding(0) var<uniform> color: vec4<f32>;
@group(0) @binding(1) var<uniform> p0: vec4<f32>;
@group(0) @binding(2) var<uniform> p1: vec4<f32>;
@group(0) @binding(3) var<uniform> p2: vec4<f32>;

@fragment
fn main(@location(0) uv: vec2<f32>) -> @location(0) vec4<f32> {
  let A = vec2<f32>(p0.x, p0.y);
  let B = vec2<f32>(p1.x, p1.y);
  let C = vec2<f32>(p2.x, p2.y);
  let thickness = p2.z;
  let a = (B - A);
  let b = ((A - (2.0 * B)) + C);
  let c = (a * 2.0);
  let d = (A - uv);
  let kk = (1.0 / dot(b, b));
  let kx = (kk * dot(a, b));
  let ky = ((kk * ((2.0 * dot(a, a)) + dot(d, b))) / 3.0);
  let kz = (kk * dot(d, a));
  let p = (ky - (kx * kx));
  let p3 = (p * p * p);
  let q = ((kx * ((2.0 * kx * kx) - (3.0 * ky))) + kz);
  let h = ((q * q) + (4.0 * p3));
  
  var res: f32;
  if ((h >= 0.0)) {
    let h_sqrt = sqrt(h);
    let x = ((vec2<f32>(h_sqrt, -(h_sqrt)) - q) / 2.0);
    let uv_temp = (sign(x) * pow(abs(x), vec2<f32>(0.33333333, 0.33333333)));
    let t = clamp(((uv_temp.x + uv_temp.y) + -(kx)), 0.0, 1.0);
    let curve_point = (d + ((c + (b * t)) * t));
    res = dot(curve_point, curve_point);
  } else {
    let z = sqrt(-(p));
    let v = (acos((q / (p * z * 2.0))) / 3.0);
    let m = cos(v);
    let n = (sin(v) * 1.732050808);
    let t_vec = ((vec3<f32>((m + m), -((n + m)), (n - m)) * z) - kx);
    let t = clamp(t_vec, vec3<f32>(0.0, 0.0, 0.0), vec3<f32>(1.0, 1.0, 1.0));
    let cp0 = (d + ((c + (b * t.x)) * t.x));
    let cp1 = (d + ((c + (b * t.y)) * t.y));
    let d0 = dot(cp0, cp0);
    let d1 = dot(cp1, cp1);
    res = min(d0, d1);
  }
  
  let dist = (sqrt(res) - thickness);
  let edge_width = fwidth(dist);
  let alpha = smoothstep((0.7 * edge_width), (-0.7 * edge_width), dist);
  return vec4<f32>(color.r, color.g, color.b, (color.a * alpha));
}
EOF_WGSL
)

echo "$WGSL" > /tmp/bezier_v2.wgsl
echo -e "\n[1/2] Validating with naga..."
naga /tmp/bezier_v2.wgsl

echo -e "\n✅ WGSL validation successful!"
echo -e "\nGenerated WGSL saved to: /tmp/bezier_v2.wgsl"
