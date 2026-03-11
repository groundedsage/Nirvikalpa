// 2D Signed Distance Field (SDF) Primitives Library
// Reference: https://iquilezles.org/articles/distfunctions2d/
//
// Convention: All SDFs return:
//   - Negative distance if inside the shape
//   - Zero distance if on the boundary
//   - Positive distance if outside the shape

// ============================================================================
// Basic Primitives
// ============================================================================

// Circle SDF
// p: point to evaluate
// center: circle center
// radius: circle radius
fn sdf_circle(p: vec2f, center: vec2f, radius: f32) -> f32 {
  return length(p - center) - radius;
}

// Ellipse SDF (approximate)
// p: point to evaluate
// center: ellipse center
// radii: (rx, ry) - x and y radii
fn sdf_ellipse(p: vec2f, center: vec2f, radii: vec2f) -> f32 {
  let q = (p - center) / radii;
  return (length(q) - 1.0) * min(radii.x, radii.y);
}

// Rectangle SDF (axis-aligned)
// p: point to evaluate
// center: rectangle center
// size: (width/2, height/2) - half-extents
fn sdf_rect(p: vec2f, center: vec2f, size: vec2f) -> f32 {
  let d = abs(p - center) - size;
  return length(max(d, vec2(0.0))) + min(max(d.x, d.y), 0.0);
}

// Rounded Rectangle SDF
// p: point to evaluate
// center: rectangle center
// size: (width/2, height/2) - half-extents before rounding
// radius: corner radius
fn sdf_rounded_rect(p: vec2f, center: vec2f, size: vec2f, radius: f32) -> f32 {
  let q = abs(p - center) - size + radius;
  return length(max(q, vec2(0.0))) + min(max(q.x, q.y), 0.0) - radius;
}

// Line Segment SDF
// p: point to evaluate
// a: segment start point
// b: segment end point
// thickness: line thickness (half-width)
fn sdf_segment(p: vec2f, a: vec2f, b: vec2f, thickness: f32) -> f32 {
  let pa = p - a;
  let ba = b - a;
  let h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
  return length(pa - ba * h) - thickness;
}

// Triangle SDF (exact)
// p: point to evaluate
// a, b, c: triangle vertices
fn sdf_triangle(p: vec2f, a: vec2f, b: vec2f, c: vec2f) -> f32 {
  let e0 = b - a;
  let e1 = c - b;
  let e2 = a - c;
  let v0 = p - a;
  let v1 = p - b;
  let v2 = p - c;

  let pq0 = v0 - e0 * clamp(dot(v0, e0) / dot(e0, e0), 0.0, 1.0);
  let pq1 = v1 - e1 * clamp(dot(v1, e1) / dot(e1, e1), 0.0, 1.0);
  let pq2 = v2 - e2 * clamp(dot(v2, e2) / dot(e2, e2), 0.0, 1.0);

  let s = sign(e0.x * e2.y - e0.y * e2.x);
  let d = min(min(
    vec2(dot(pq0, pq0), s * (v0.x * e0.y - v0.y * e0.x)),
    vec2(dot(pq1, pq1), s * (v1.x * e1.y - v1.y * e1.x))),
    vec2(dot(pq2, pq2), s * (v2.x * e2.y - v2.y * e2.x)));

  return -sqrt(d.x) * sign(d.y);
}

// Regular Polygon SDF
// p: point to evaluate
// center: polygon center
// radius: circumradius (distance from center to vertex)
// n: number of sides
fn sdf_polygon(p: vec2f, center: vec2f, radius: f32, n: f32) -> f32 {
  let an = 3.141593 / n;
  let en = 6.283185 / n;
  let q = p - center;
  let bn = (atan2(q.y, q.x)) % en - an;
  let l = length(q);
  return l * cos(bn) - radius;
}

// ============================================================================
// Boolean Operations
// ============================================================================

// Union (combines two shapes)
fn sdf_union(d1: f32, d2: f32) -> f32 {
  return min(d1, d2);
}

// Intersection (keeps only overlapping region)
fn sdf_intersect(d1: f32, d2: f32) -> f32 {
  return max(d1, d2);
}

// Subtraction (subtracts d2 from d1)
fn sdf_subtract(d1: f32, d2: f32) -> f32 {
  return max(d1, -d2);
}

// Smooth Union (blends two shapes smoothly)
// k: blend factor (larger = smoother)
fn sdf_smooth_union(d1: f32, d2: f32, k: f32) -> f32 {
  let h = clamp(0.5 + 0.5 * (d2 - d1) / k, 0.0, 1.0);
  return mix(d2, d1, h) - k * h * (1.0 - h);
}

// ============================================================================
// Utility Functions
// ============================================================================

// Anti-aliased step function for SDF rendering
// Creates smooth edge based on distance
// dist: signed distance from SDF
// edge_width: width of anti-aliasing gradient (in UV space)
fn sdf_antialias(dist: f32, edge_width: f32) -> f32 {
  return smoothstep(edge_width, -edge_width, dist);
}

// Stroke rendering (outline only)
// dist: signed distance from SDF
// stroke_width: width of stroke (half on each side)
// edge_width: anti-aliasing width
fn sdf_stroke(dist: f32, stroke_width: f32, edge_width: f32) -> f32 {
  let d = abs(dist) - stroke_width;
  return smoothstep(edge_width, -edge_width, d);
}
