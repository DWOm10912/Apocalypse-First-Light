// Vehicle Pure Mesh kit (2026-10-15, first used by the Halstead Tamarack T15 pickup, tools/build-pickup-t15.mjs): rounded
// hard-surface geometry, unfolded UV islands, and a texture baker with ray-traced ambient occlusion.
// Built on tools/cube-slab-mesh-lib.mjs (Part, triangulate, png). Reusable for later vehicles (docs/vehicles/).
//
// Geometry (any units; the caller converts at the end). A frame maps 2D outline coordinates (u, v) and a depth a to 3D:
//   frame(o, U, V)          o + U u + V v + W a, W = U x V
//   FX / FY / FZ            the axis frames: FZ outlines (x, y) extruded along z; FX outlines (z, y) along x; FY (x, z) along y
//   fillet(L, r, seg)       rounds the corners of a 2D loop (r: number or per-corner array)
//   offsetLoop(L, d)        mitred inset (d > 0 shrinks the material: CCW outer loops shrink, CW holes grow)
//   slab(p, F, outer, holes, a0, a1, {r0, r1, steps})   extrusion with quarter-round ends (radius r0 at a0, r1 at a1)
//   rbox(p, F, u0, u1, v0, v1, a0, a1, r)                rounded box (corners filleted, ends rounded)
//   loft(p, F, stations, {cap0, cap1, closed})          stations [{a, loop}] with equal point counts, caps triangulated
//   sweep(p, path, profile, {cap0, cap1, closed})       a 2D profile carried along a 3D path (parallel-transport frames)
//   tube(p, path, r, seg, opts)                          sweep of a circle
//   lathe(p, c, axis, profile, seg, {phase, a0, a1})     a closed (t, r) profile revolved about an axis
// Faces come out wound counter-clockwise from outside (every builder knows its outward side).
// Post-processing (on the final, runtime-frame parts):
//   unfoldUV(parts, opts)           islands grown across smooth edges by unfolding (strips around a rounded edge stay one
//                                   island), rejecting faces that would overlap or tear; min-area rotation; shelf packing
//   Bvh / ambientOcclusion           ray-traced occlusion from every occluding part, per texel
//   bake(opts)                       Base Color + LabPBR _s / _n per atlas: material shading, baked occlusion (part in the
//                                   colour for vanilla, the rest in _n blue for shader packs), height detail -> normals
// Smooth corner normals are not done here: they belong to Vextris (user 2026-10-15: shared mesh capabilities live in the
// Vextris workspace; AFL sends it the requirements). Until Vextris has them, every face renders with its flat normal.
import {Part, triangulate, area2, newell, norm, cross, add, sub, mul, dot, png} from './cube-slab-mesh-lib.mjs';
export {Part, triangulate, area2, newell, norm, cross, add, sub, mul, dot, png};

const TAU = Math.PI * 2, D2R = Math.PI / 180;
export const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
export const lerp = (a, b, t) => a + (b - a) * t;
export const smoothstep = (a, b, v) => { const t = clamp((v - a) / (b - a), 0, 1); return t * t * (3 - 2 * t); };

// ================================================================ 2D
const s2 = (a, b) => [a[0] - b[0], a[1] - b[1]], d2 = (a, b) => a[0] * b[0] + a[1] * b[1], l2 = a => Math.hypot(a[0], a[1]);
const n2 = a => { const l = l2(a) || 1; return [a[0] / l, a[1] / l]; };
export const ccw = L => area2(L) > 0 ? L : L.slice().reverse();
export const cw = L => area2(L) < 0 ? L : L.slice().reverse();
/** Points on a circle (cx, cy, r) from a0 to a1 (radians), n segments (n + 1 points). */
export const arc = (cx, cy, r, a0, a1, n) => Array.from({length: n + 1}, (_, i) => { const a = a0 + (a1 - a0) * i / n; return [cx + r * Math.cos(a), cy + r * Math.sin(a)]; });
/** Drops consecutive duplicate points (closed loop). */
export const dedupe = (L, eps = 1e-7) => L.filter((p, i) => l2(s2(p, L[(i + 1) % L.length])) > eps);
/**
 * Rounding detail follows the size: a quarter round of radius r gets the fewest segments (at most the caller's count) whose
 * chord stays within CHORD of the arc. In the units the geometry is built in (metres for the vehicles: 1.2 mm, a fiftieth
 * of a texel-sized runtime pixel), so a 3 mm edge roll is one chamfer and a 7 cm window corner keeps five segments.
 * The count depends on the requested radius only, so loops built from the same radii always loft together.
 */
let CHORD = 0.0012;
export const setChord = c => { CHORD = c; };
export const segsFor = (r, max) => r <= CHORD ? 1 : Math.max(1, Math.min(max, Math.ceil((Math.PI / 2) / (2 * Math.acos(1 - CHORD / r)))));
/** Rounds the corners of a closed loop (or an open polyline with closed = false: its ends stay). */
export function fillet(L, r, seg = 4, closed = true) {
  const n = L.length, out = [];
  for (let i = 0; i < n; i++) {
    const b = L[i], rr = Array.isArray(r) ? r[i] : r;
    if ((!closed && (i === 0 || i === n - 1)) || !(rr > 0)) { out.push(b); continue; }
    const a = L[(i + n - 1) % n], c = L[(i + 1) % n], da = n2(s2(a, b)), dc = n2(s2(c, b));
    const th = Math.acos(clamp(d2(da, dc), -1, 1));
    if (th > Math.PI - 1e-4 || th < 1e-4) { out.push(b); continue; }
    let t = rr / Math.tan(th / 2), r2 = rr;
    const lim = 0.5 * Math.min(l2(s2(a, b)), l2(s2(c, b)));
    if (t > lim) { t = lim; r2 = t * Math.tan(th / 2); }
    const p1 = [b[0] + da[0] * t, b[1] + da[1] * t], bis = n2([da[0] + dc[0], da[1] + dc[1]]), h = r2 / Math.sin(th / 2);
    const C = [b[0] + bis[0] * h, b[1] + bis[1] * h], p2 = [b[0] + dc[0] * t, b[1] + dc[1] * t];
    const a1 = Math.atan2(p1[1] - C[1], p1[0] - C[0]); let dA = Math.atan2(p2[1] - C[1], p2[0] - C[0]) - a1;
    while (dA > Math.PI) dA -= TAU; while (dA < -Math.PI) dA += TAU;
    const ns = segsFor(rr, seg);
    for (let k = 0; k <= ns; k++) out.push([C[0] + r2 * Math.cos(a1 + dA * k / ns), C[1] + r2 * Math.sin(a1 + dA * k / ns)]);
  }
  return closed ? dedupe(out) : out.filter((p, i) => i === 0 || l2(s2(p, out[i - 1])) > 1e-7);
}
/** Mitred inset of a closed loop: d > 0 moves every edge to its left (shrinks a CCW loop, grows a CW hole). */
export function offsetLoop(L, d) {
  if (!d) return L.slice();
  const n = L.length, out = [];
  for (let i = 0; i < n; i++) {
    const a = L[(i + n - 1) % n], b = L[i], c = L[(i + 1) % n];
    const e1 = n2(s2(b, a)), e2 = n2(s2(c, b)), m1 = [-e1[1], e1[0]], m2 = [-e2[1], e2[0]];
    const k = d / Math.max(0.3, 1 + d2(m1, m2));
    out.push([b[0] + k * (m1[0] + m2[0]), b[1] + k * (m1[1] + m2[1])]);
  }
  return out;
}
const segX = (a, b, c, d) => {
  const o = (p, q, r) => (q[0] - p[0]) * (r[1] - p[1]) - (q[1] - p[1]) * (r[0] - p[0]);
  const d1 = o(a, b, c), d3 = o(a, b, d), d4 = o(c, d, a), d5 = o(c, d, b);
  return d1 * d3 < -1e-18 && d4 * d5 < -1e-18;
};
export function simpleLoops(loops) {
  const E = loops.flatMap(L => L.map((p, i) => [p, L[(i + 1) % L.length]]));
  for (let i = 0; i < E.length; i++) for (let j = i + 1; j < E.length; j++) {
    const [a, b] = E[i], [c, d] = E[j];
    if (a === c || a === d || b === c || b === d) continue;
    if (segX(a, b, c, d)) return false;
  }
  return true;
}
/** Is the inset of every loop valid (simple, same orientation, shrunk material)? */
function insetOk(loops, inset) {
  for (let i = 0; i < loops.length; i++) {
    const A = area2(loops[i]), B = area2(inset[i]);
    if (Math.sign(A) !== Math.sign(B)) return false;
    if (i === 0 ? Math.abs(B) >= Math.abs(A) : Math.abs(B) <= Math.abs(A)) return false;
  }
  // a hole grown past the whole outer loop crosses no edge: every inset hole must stay inside the inset outer
  for (let i = 1; i < inset.length; i++) if (!inPoly(inset[i][0], inset[0])) return false;
  return simpleLoops(inset);
}
export const inPoly = (p, P) => { let c = false; for (let i = 0, j = P.length - 1; i < P.length; j = i++) if ((P[i][1] > p[1]) !== (P[j][1] > p[1]) && p[0] < (P[j][0] - P[i][0]) * (p[1] - P[i][1]) / (P[j][1] - P[i][1]) + P[i][0]) c = !c; return c; };

/**
 * Splits a simple polygon at the horizontal line y = c: {lo: [polygons below], hi: [polygons above]}, each CCW. The
 * pieces share the cut points (a wheel arch rising through the line gives two separate pieces below it).
 */
export function splitAtY(P, c) {
  P = ccw(P).map(q => Math.abs(q[1] - c) < 1e-9 ? [q[0], c + 1e-9] : q);
  const n = P.length, B = [];
  for (let i = 0; i < n; i++) { const a = P[i], b = P[(i + 1) % n]; B.push({p: a});
    if ((a[1] - c) * (b[1] - c) < 0) { const t = (c - a[1]) / (b[1] - a[1]); B.push({p: [a[0] + t * (b[0] - a[0]), c], cut: true}); } }
  const cuts = B.map((e, i) => e.cut ? i : -1).filter(i => i >= 0);
  if (!cuts.length) return P[0][1] < c ? {lo: [P], hi: []} : {lo: [], hi: [P]};
  const sorted = cuts.slice().sort((i, j) => B[i].p[0] - B[j].p[0]), partner = new Map();
  for (let k = 0; k + 1 < sorted.length; k += 2) { partner.set(sorted[k], sorted[k + 1]); partner.set(sorted[k + 1], sorted[k]); }
  const pieces = below => { const used = new Set(), out = [];
    for (const s0 of cuts) {
      if (used.has(s0) || (B[(s0 + 1) % B.length].p[1] < c) !== below) continue;
      const poly = []; let i = s0, guard = 0;
      while (guard++ < 10000) {
        used.add(i); poly.push(B[i].p); i = (i + 1) % B.length;
        while (!B[i].cut) { poly.push(B[i].p); i = (i + 1) % B.length; }
        poly.push(B[i].p); used.add(i); const j = partner.get(i); if (j === s0 || j === undefined) break; i = j;
      }
      out.push(dedupe(poly));
    }
    return out; };
  return {lo: pieces(true), hi: pieces(false)};
}

// ================================================================ frames
/** A frame: o + U u + V v + W a (W = U x V unless given). */
export function frame(o, U, V, W = null) {
  U = norm(U); V = norm(V); W = W ? norm(W) : norm(cross(U, V));
  const hand = Math.sign(dot(cross(U, V), W)) || 1;
  return {o, U, V, W, hand, pt: (u, v, a = 0) => [o[0] + U[0] * u + V[0] * v + W[0] * a, o[1] + U[1] * u + V[1] * v + W[1] * a, o[2] + U[2] * u + V[2] * v + W[2] * a]};
}
export const FZ = frame([0, 0, 0], [1, 0, 0], [0, 1, 0]);                 // outline (x, y), depth z
export const FX = frame([0, 0, 0], [0, 0, 1], [0, 1, 0], [1, 0, 0]);      // outline (z, y), depth x
export const FY = frame([0, 0, 0], [1, 0, 0], [0, 0, 1], [0, 1, 0]);      // outline (x, z), depth y

// ================================================================ faces
/** A face with a known outward order (corrected for a left-handed frame). */
function face(p, ids, hand = 1, tag = 'side') {
  const o = hand < 0 ? ids.slice().reverse() : ids;
  const P = o.map(i => p.v[i]); p.face(o, newell(P), tag);
}
function cap(p, F, loops, ringIds, a, up, tag = 'cap') {
  // loops: [outer CCW, holes CW] in (u, v); ringIds: vertex ids of each loop at this station
  const {tris} = triangulate(loops[0], loops.slice(1)), flat = ringIds.flat();
  for (const t of tris) face(p, up ? t.map(i => flat[i]) : t.map(i => flat[i]).reverse(), F.hand, tag);
}

// ================================================================ solids
/**
 * Lofts stations [{a, loop: [[u, v]...]}] (every loop the same point count, CCW: outward) through frame F. closed: the loop
 * is closed (else an open polyline: a strip); cap0 / cap1: triangulated ends (ids shared with the rings). holes: optional,
 * per station an array of CW loops (each hole lofted the same way; caps then include them). Returns the ring ids.
 */
export function loft(p, F, stations, {closed = true, cap0 = false, cap1 = false, tag = 'side', holes = null} = {}) {
  const ring = (st, L) => L.map(([u, v]) => p.vtx(F.pt(u, v, st.a)));
  const loops = st => [st.loop, ...(holes ? holes(st) : st.holes || [])];
  const R = stations.map(st => loops(st).map(L => ring(st, L)));
  const sgn = Math.sign(stations[stations.length - 1].a - stations[0].a) || 1;
  for (let k = 0; k + 1 < stations.length; k++) for (let li = 0; li < R[k].length; li++) {
    const A = R[k][li], B = R[k + 1][li], n = A.length;
    for (let i = 0; i < (closed ? n : n - 1); i++) { const j = (i + 1) % n;
      face(p, [A[i], A[j], B[j], B[i]], F.hand * sgn, li ? 'wall' : tag); }
  }
  if (cap0) cap(p, F, loops(stations[0]), R[0], stations[0].a, sgn < 0);
  if (cap1) cap(p, F, loops(stations[stations.length - 1]), R[R.length - 1], stations[stations.length - 1].a, sgn > 0);
  return R;
}
/**
 * Lofts 3D rings (equal point counts) in order. The outward side: faces (ring k i, i + 1, ring k + 1 i + 1, ring k + 1 i)
 * keep that order (flip reverses it); a builder that knows its side passes the matching flip. cap0 / cap1 triangulate the
 * end rings in their best-fit plane, facing away from the neighbouring ring.
 */
export function loftRings(p, rings, {closed = true, cap0 = false, cap1 = false, flip = false, tag = 'side', wrap = false} = {}) {
  const R = rings.map(r => r.map(q => p.vtx(q))), n = rings[0].length;
  for (let k = 0; k < (wrap ? R.length : R.length - 1); k++) { const A = R[k], B = R[(k + 1) % R.length];
    for (let i = 0; i < (closed ? n : n - 1); i++) { const j = (i + 1) % n; face(p, [A[i], A[j], B[j], B[i]], flip ? -1 : 1, tag); } }
  const capRing = (k, other) => {
    const P = rings[k], c = mul(P.reduce((s, q) => add(s, q), [0, 0, 0]), 1 / n);
    let nn = norm(newell(P)); const away = sub(c, mul(rings[other].reduce((s, q) => add(s, q), [0, 0, 0]), 1 / n));
    const t = norm(Math.abs(nn[0]) < 0.9 ? cross(nn, [1, 0, 0]) : cross(nn, [0, 1, 0])), b = cross(nn, t);
    let L = P.map(q => [dot(sub(q, c), t), dot(sub(q, c), b)]), ids = R[k];
    if (area2(L) < 0) { L = L.slice().reverse(); ids = ids.slice().reverse(); }
    const {tris} = triangulate(L, []), up = dot(nn, away) > 0;
    for (const tr of tris) { const f = tr.map(i => ids[i]); p.face(up ? f : f.slice().reverse(), up ? nn : mul(nn, -1), 'cap'); }
  };
  if (cap0) capRing(0, 1);
  if (cap1) capRing(R.length - 1, R.length - 2);
  return R;
}
/** Makes the faces p.f[from..] (one closed solid) face outward: reversed when their signed volume is negative. */
export function orientSolid(p, from) {
  let vol = 0;
  for (let i = from; i < p.f.length; i++) { const P = p.f[i].ids.map(k => p.v[k]);
    for (let q = 1; q + 1 < P.length; q++) vol += dot(P[0], cross(P[q], P[q + 1])); }
  if (vol < 0) for (let i = from; i < p.f.length; i++) p.f[i].ids = p.f[i].ids.slice().reverse();
  return vol / 6;
}
/** loftRings for a closed solid (closed rings, capped or wrapped): orientation found from the volume. */
export function loftSolid(p, rings, o = {}) {
  const from = p.f.length; const R = loftRings(p, rings, {cap0: !o.wrap, cap1: !o.wrap, ...o}); orientSolid(p, from); return R;
}
/** Rotates point q about the axis through c with unit direction ax by angle a (radians). */
export function rotAbout(q, c, ax, a) {
  const v = sub(q, c), cs = Math.cos(a), sn = Math.sin(a), k = norm(ax);
  return add(c, add(add(mul(v, cs), mul(cross(k, v), sn)), mul(k, dot(k, v) * (1 - cs))));
}
/** A closed thick section from an open polyline: the line, then its offset by t to its left (reversed). */
export function thicken(line, t) {
  const n = line.length, inner = line.map((b, i) => {
    const a = line[Math.max(0, i - 1)], c = line[Math.min(n - 1, i + 1)];
    const e1 = i > 0 ? n2(s2(b, a)) : n2(s2(c, b)), e2 = i < n - 1 ? n2(s2(c, b)) : e1, m1 = [-e1[1], e1[0]], m2 = [-e2[1], e2[0]];
    const k = t / Math.max(0.3, 1 + d2(m1, m2)); return [b[0] + k * (m1[0] + m2[0]), b[1] + k * (m1[1] + m2[1])];
  });
  return [...line, ...inner.reverse()];
}
/**
 * Extrusion of outer (CCW) with holes (CW) from a0 to a1 along F's W, each end rounded by a quarter round (r0 at a0, r1 at
 * a1; steps segments; 1 step = a chamfer). A radius the outline cannot take is halved until it fits.
 */
export function slab(p, F, outer, holes, a0, a1, {r0 = 0, r1 = r0, steps = 3, tag = 'side', capTag = 'cap'} = {}) {
  outer = ccw(dedupe(outer)); holes = (holes || []).map(h => cw(dedupe(h)));
  const loops = [outer, ...holes];
  // an end rounding within the chord tolerance is not drawn at all (a 1 mm chamfer is a fiftieth of a runtime pixel)
  const fit = r => { if (r <= CHORD) return 0; r = Math.min(r, Math.abs(a1 - a0) / 2 - 1e-6); while (r > 1e-5 && !insetOk(loops, loops.map(L => offsetLoop(L, r)))) r /= 2; return r > 1e-5 ? r : 0; };
  r0 = fit(r0); r1 = fit(r1);
  const sg = Math.sign(a1 - a0) || 1, st = [];
  const ends = (r, a, dir) => { const S = []; if (!r) return [{a, d: 0}];
    const n = segsFor(r, steps);
    for (let k = 0; k <= n; k++) { const th = k / n * Math.PI / 2; S.push({a: a + dir * r * (1 - Math.cos(th)), d: r * (1 - Math.sin(th))}); }
    return S; };
  st.push(...ends(r0, a0, sg));
  const tail = ends(r1, a1, -sg).reverse();
  if (Math.abs(tail[0].a - st[st.length - 1].a) < 1e-9) tail.shift();
  st.push(...tail);
  const stations = st.map(s => ({a: s.a, loop: offsetLoop(outer, s.d), holes: holes.map(h => offsetLoop(h, s.d))}));
  return loft(p, F, stations, {cap0: true, cap1: true, tag});
}
/** A rounded box in frame F: u0..u1, v0..v1 (corners filleted by r), a0..a1 (ends rounded by r). */
export function rbox(p, F, u0, u1, v0, v1, a0, a1, r = 0, {steps = 3, seg = 3, rc = r} = {}) {
  const rr = Math.min(rc, (u1 - u0) / 2 - 1e-6, (v1 - v0) / 2 - 1e-6);
  const L = rr > 1e-5 ? fillet([[u0, v0], [u1, v0], [u1, v1], [u0, v1]], rr, seg) : [[u0, v0], [u1, v0], [u1, v1], [u0, v1]];
  return slab(p, F, L, [], a0, a1, {r0: r, steps});
}
/** Axis-aligned rounded box (x0..x1, y0..y1, z0..z1), r on every edge. */
export const abox = (p, x0, x1, y0, y1, z0, z1, r = 0, o = {}) => rbox(p, FZ, x0, x1, y0, y1, z0, z1, r, o);
/** An oriented rounded box: centre c, unit axes u, v (w = u x v), half sizes. */
export function obox(p, c, u, v, hu, hv, hw, r = 0, o = {}) {
  const F = frame(c, u, v);
  return rbox(p, F, -hu, hu, -hv, hv, -hw, hw, r, o);
}

/** Parallel-transport frames along a polyline (tangent, normal, binormal per point). */
export function ptFrames(path, closed = false, up = null) {
  const n = path.length, T = path.map((q, i) => {
    const a = closed ? path[(i + n - 1) % n] : path[Math.max(0, i - 1)], b = closed ? path[(i + 1) % n] : path[Math.min(n - 1, i + 1)];
    return norm(sub(b, a));
  });
  let N = up ? norm(sub(up, mul(T[0], dot(up, T[0])))) : null;
  if (!N || !isFinite(N[0]) || Math.hypot(...N) < 0.5) { const t = Math.abs(T[0][1]) < 0.9 ? [0, 1, 0] : [1, 0, 0]; N = norm(sub(t, mul(T[0], dot(t, T[0])))); }
  const out = [];
  for (let i = 0; i < n; i++) {
    if (i) { const r = sub(N, mul(T[i], dot(N, T[i]))); N = Math.hypot(...r) > 1e-9 ? norm(r) : N; }
    out.push({T: T[i], N, B: cross(T[i], N)});
  }
  return out;
}
/**
 * Carries a 2D profile [[u, v]] (CCW for a closed solid: outward) along a 3D path: u along the frame normal N, v along
 * the binormal B. At path corners the profile is scaled by 1 / cos(half the bend) across the bend (mitre). up: the
 * initial N direction (optional).
 */
export function sweep(p, path, profile, {closed = false, cap0 = !closed, cap1 = !closed, up = null, tag = 'side', closedProfile = true, scale = null} = {}) {
  const Fr = ptFrames(path, closed, up), n = path.length, from = p.f.length;
  const rings = path.map((q, i) => {
    const {T, N, B} = Fr[i];
    let mN = 1, mB = 1;
    if (closed || (i > 0 && i < n - 1)) {   // mitre: stretch across the bend
      const a = norm(sub(q, path[(i + n - 1) % n])), b = norm(sub(path[(i + 1) % n], q)), c = clamp(dot(a, b), -1, 1);
      const bend = norm(sub(b, a)), k = 1 / Math.max(0.5, Math.cos(Math.acos(c) / 2));
      if (Math.hypot(...sub(b, a)) > 1e-9) { mN = 1 + (k - 1) * Math.abs(dot(bend, N)); mB = 1 + (k - 1) * Math.abs(dot(bend, B)); }
    }
    const s = scale ? scale(i) : 1;
    return profile.map(([u, v]) => p.vtx(add(q, add(mul(N, u * mN * s), mul(B, v * mB * s)))));
  });
  const m = profile.length;
  for (let k = 0; k < (closed ? n : n - 1); k++) { const A = rings[k], Bq = rings[(k + 1) % n];
    for (let i = 0; i < (closedProfile ? m : m - 1); i++) { const j = (i + 1) % m; face(p, [A[i], A[j], Bq[j], Bq[i]], 1, tag); } }
  const T0 = Fr[0], T1 = Fr[n - 1];
  if (cap0 && closedProfile) { const {tris} = triangulate(ccw(profile), []); const rev = area2(profile) < 0;
    for (const t of tris) { const ids = t.map(i => rings[0][rev ? m - 1 - i : i]); face(p, ids.slice().reverse(), 1, 'cap'); } }
  if (cap1 && closedProfile) { const {tris} = triangulate(ccw(profile), []); const rev = area2(profile) < 0;
    for (const t of tris) { const ids = t.map(i => rings[n - 1][rev ? m - 1 - i : i]); face(p, ids, 1, 'cap'); } }
  if (closedProfile && (closed || (cap0 && cap1))) orientSolid(p, from);   // a mirrored profile comes out inside-out
  return rings;
}
/** A circle profile (CCW), seg points. */
export const circle = (r, seg, phase = Math.PI / seg) => Array.from({length: seg}, (_, i) => { const a = phase + TAU * i / seg; return [r * Math.cos(a), r * Math.sin(a)]; });
export const tube = (p, path, r, seg = 8, o = {}) => sweep(p, path, circle(r, seg), o);
/** A path through points with rounded corners (radius r, seg points per corner): a 3D polyline fillet. */
export function roundPath(P, r, seg = 4) {
  const out = [P[0]];
  for (let i = 1; i + 1 < P.length; i++) {
    const a = P[i - 1], b = P[i], c = P[i + 1], da = norm(sub(a, b)), dc = norm(sub(c, b));
    const th = Math.acos(clamp(dot(da, dc), -1, 1));
    if (th > Math.PI - 1e-3) { out.push(b); continue; }
    let t = r / Math.tan(th / 2); t = Math.min(t, 0.5 * Math.hypot(...sub(a, b)), 0.5 * Math.hypot(...sub(c, b)));
    const p1 = add(b, mul(da, t)), p2 = add(b, mul(dc, t));
    for (let k = 0; k <= seg; k++) { const s = k / seg;   // quadratic Bezier through the corner (close to the arc)
      out.push(add(add(mul(p1, (1 - s) * (1 - s)), mul(b, 2 * s * (1 - s))), mul(p2, s * s))); }
  }
  out.push(P[P.length - 1]);
  return out.filter((q, i) => i === 0 || Math.hypot(...sub(q, out[i - 1])) > 1e-7);
}
/**
 * Revolves a closed profile [[t, r]] (t along the axis, r >= 0 the radius) about the axis through c. Points with r = 0
 * collapse onto the axis. a0..a1: a partial revolution (open ends left open). Returns nothing.
 */
export function lathe(p, c, axis, profile, seg, {phase = Math.PI / seg, a0 = 0, a1 = TAU, tag = 'side', e1 = null} = {}) {
  axis = norm(axis);
  let E1 = e1 ? norm(sub(e1, mul(axis, dot(e1, axis)))) : null;
  if (!E1) { const t = Math.abs(axis[1]) < 0.9 ? [0, 1, 0] : [1, 0, 0]; E1 = norm(cross(axis, t)); }
  const E2 = cross(axis, E1), full = Math.abs(a1 - a0 - TAU) < 1e-9, cnt = full ? seg : seg + 1;
  const P = area2(profile) > 0 ? profile : profile.slice().reverse();
  const ring = ([t, r]) => r < 1e-7 ? null : Array.from({length: cnt}, (_, i) => { const a = a0 + phase * (full ? 1 : 0) + (a1 - a0) * i / seg;
    return p.vtx(add(add(c, mul(axis, t)), add(mul(E1, r * Math.cos(a)), mul(E2, r * Math.sin(a))))); });
  const rings = P.map(ring), on = P.map(([t, r]) => r < 1e-7 ? p.vtx(add(c, mul(axis, t))) : null);
  for (let k = 0; k < P.length; k++) {
    const k2 = (k + 1) % P.length; if (P[k][1] < 1e-7 && P[k2][1] < 1e-7) continue;
    for (let i = 0; i < seg; i++) { const j = full ? (i + 1) % seg : i + 1;
      // CCW profile in (t, r): the order ring k i, ring k j, ring k2 j, ring k2 i faces inward (revolution E1 -> E2,
      // axis = E1 x E2), so it is reversed
      let ids;
      if (P[k][1] < 1e-7) ids = [on[k], rings[k2][j], rings[k2][i]];
      else if (P[k2][1] < 1e-7) ids = [rings[k][i], rings[k][j], on[k2]];
      else ids = [rings[k][i], rings[k][j], rings[k2][j], rings[k2][i]];
      face(p, ids, -1, Math.abs(P[k2][0] - P[k][0]) < 1e-9 ? 'cap' : tag);
    }
  }
}

// ================================================================ UV: unfolded islands
/**
 * Islands per part (and material): faces join an island across an edge they share (vertex ids) when the dihedral is
 * below maxDihedral and the face stays within maxTurn of the island's seed normal; the face is unfolded about the shared
 * edge (rigid), and refused when a corner would land elsewhere than where the island already put it (a tear) or when it
 * would overlap the island. Island coordinates are in model units times density(part, mat); islands are turned to their
 * minimum-area rectangle and shelf-packed, the scale S (texels per model unit) searched down from startS.
 * Returns {islands, S, faceUV: Map part -> Map face -> [[u, v] texels]}.
 */
export function unfoldUV(PARTS, {atlas, pad = 3, startS, stepS, density = () => 1, maxDihedral = 32, maxTurn = 100, reserve = null}) {
  const islands = [], cD = Math.cos(maxDihedral * D2R), cT = Math.cos(maxTurn * D2R);
  for (const part of PARTS) {
    const F = part.f.map(f => ({f, n: norm(newell(f.ids.map(i => part.v[i]))), a: Math.hypot(...newell(f.ids.map(i => part.v[i]))) / 2}));
    const edge = new Map();
    F.forEach((o, i) => o.f.ids.forEach((a, k) => { const b = o.f.ids[(k + 1) % o.f.ids.length], e = a < b ? a + ',' + b : b + ',' + a; (edge.get(e) || edge.set(e, []).get(e)).push(i); }));
    const done = new Uint8Array(F.length), order = F.map((_, i) => i).sort((a, b) => F[b].a - F[a].a);
    // a face's corners in its own plane (any orientation-preserving basis)
    const local = o => { const P = o.f.ids.map(i => part.v[i]); let t = sub(P[1], P[0]); t = norm(sub(t, mul(o.n, dot(t, o.n)))); const b = cross(o.n, t);
      return P.map(q => [dot(sub(q, P[0]), t), dot(sub(q, P[0]), b)]); };
    for (const s0 of order) {
      if (done[s0]) continue;
      const seed = F[s0], uv = new Map(), faces = [], tris = [], grid = new Map(), G = 0;
      const cell = Math.max(1e-3, Math.sqrt(seed.a) / 2);
      const triBox = T => [Math.min(T[0][0], T[1][0], T[2][0]), Math.min(T[0][1], T[1][1], T[2][1]), Math.max(T[0][0], T[1][0], T[2][0]), Math.max(T[0][1], T[1][1], T[2][1])];
      const addTri = T => { const b = triBox(T); tris.push(T); const id = tris.length - 1;
        for (let x = Math.floor(b[0] / cell); x <= Math.floor(b[2] / cell); x++) for (let y = Math.floor(b[1] / cell); y <= Math.floor(b[3] / cell); y++) { const k = x + ',' + y; (grid.get(k) || grid.set(k, []).get(k)).push(id); } };
      const overlaps = T => { const b = triBox(T), seen = new Set();
        for (let x = Math.floor(b[0] / cell); x <= Math.floor(b[2] / cell); x++) for (let y = Math.floor(b[1] / cell); y <= Math.floor(b[3] / cell); y++)
          for (const id of grid.get(x + ',' + y) || []) { if (seen.has(id)) continue; seen.add(id); if (triOverlap(T, tris[id])) return true; }
        return false; };
      const place = (i, pts) => { const o = F[i]; o.f.ids.forEach((v, k) => uv.set(v, pts[k])); faces.push(o);
        for (let q = 1; q + 1 < pts.length; q++) addTri([pts[0], pts[q], pts[q + 1]]); done[i] = 1; };
      place(s0, local(seed));
      const queue = [s0];
      while (queue.length) {
        const i = queue.shift(), o = F[i];
        o.f.ids.forEach((a, k) => {
          const b = o.f.ids[(k + 1) % o.f.ids.length], e = a < b ? a + ',' + b : b + ',' + a;
          for (const j of edge.get(e)) {
            if (done[j]) continue; const g = F[j];
            if (g.f.mat !== seed.f.mat || dot(g.n, o.n) < cD || dot(g.n, seed.n) < cT) continue;
            const L = local(g), ia = g.f.ids.indexOf(a), ib = g.f.ids.indexOf(b);
            if (ia < 0 || ib < 0) continue;
            const A = uv.get(a), B = uv.get(b), la = L[ia], lb = L[ib];
            const ang = Math.atan2(B[1] - A[1], B[0] - A[0]) - Math.atan2(lb[1] - la[1], lb[0] - la[0]), c = Math.cos(ang), s = Math.sin(ang);
            const pts = L.map(q => { const x = q[0] - la[0], y = q[1] - la[1]; return [A[0] + c * x - s * y, A[1] + s * x + c * y]; });
            const scale = Math.max(1e-6, Math.hypot(B[0] - A[0], B[1] - A[1]));
            if (g.f.ids.some((v, kk) => uv.has(v) && Math.hypot(uv.get(v)[0] - pts[kk][0], uv.get(v)[1] - pts[kk][1]) > 1e-3 * scale + 1e-6)) continue;
            let bad = false; for (let q = 1; q + 1 < pts.length && !bad; q++) bad = overlaps([pts[0], pts[q], pts[q + 1]]);
            if (bad) continue;
            place(j, pts); queue.push(j);
          }
        });
      }
      const ks = density(part, seed.f.mat);
      // minimum-area rectangle over the island's hull, then long side horizontal
      const all = faces.flatMap(o => o.f.ids.map(v => uv.get(v)));
      const H = hull2(all); let best = null;
      for (let q = 0; q < H.length; q++) { const e = s2(H[(q + 1) % H.length], H[q]), ang = -Math.atan2(e[1], e[0]), c = Math.cos(ang), s = Math.sin(ang);
        const R = H.map(([x, y]) => [c * x - s * y, s * x + c * y]), w = Math.max(...R.map(r => r[0])) - Math.min(...R.map(r => r[0])), h = Math.max(...R.map(r => r[1])) - Math.min(...R.map(r => r[1]));
        if (!best || w * h < best.area - 1e-12) best = {area: w * h, ang: w >= h ? ang : ang + Math.PI / 2}; }
      const c = Math.cos(best?.ang || 0), s = Math.sin(best?.ang || 0), U = new Map();
      for (const [v, [x, y]] of uv) U.set(v, [ks * (c * x - s * y), ks * (s * x + c * y)]);
      const us = [...U.values()], u0 = Math.min(...us.map(q => q[0])), v0 = Math.min(...us.map(q => q[1]));
      islands.push({part, mat: seed.f.mat, faces, uv: U, u0, v0, w: Math.max(...us.map(q => q[0])) - u0, h: Math.max(...us.map(q => q[1])) - v0});
    }
  }
  // packing on a coarse occupancy grid by each island's real footprint (its triangles, not its rectangle): small islands
  // drop into the holes of ring-shaped ones (window mouldings, lamp bezels, panels with openings). Cells of C texels, at
  // least one free cell between islands (>= pad texels when C >= pad); biggest islands first, first fit in row order.
  const C = Math.max(2, pad), G = Math.floor(atlas / C);
  function footprint(is, S) {
    const w = Math.ceil((is.w * S + 1) / C) + 1, h = Math.ceil((is.h * S + 1) / C) + 1, m = new Uint8Array(w * h);
    for (const o of is.faces) {
      const T = o.f.ids.map(v => { const [u, q] = is.uv.get(v); return [((u - is.u0) * S + 0.5) / C, ((q - is.v0) * S + 0.5) / C]; });
      for (let k = 1; k + 1 < T.length; k++) {
        const t = [T[0], T[k], T[k + 1]], x0 = Math.floor(Math.min(t[0][0], t[1][0], t[2][0])), x1 = Math.floor(Math.max(t[0][0], t[1][0], t[2][0]));
        const y0 = Math.floor(Math.min(t[0][1], t[1][1], t[2][1])), y1 = Math.floor(Math.max(t[0][1], t[1][1], t[2][1]));
        for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) if (x >= 0 && y >= 0 && x < w && y < h && cellHit(t, x, y)) m[y * w + x] = 1;
      }
    }
    const cells = [], grown = [];
    for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) if (m[y * w + x]) cells.push(x + 1, y + 1);
    const seen = new Set();
    for (let i = 0; i < cells.length; i += 2) for (let dy = -1; dy <= 1; dy++) for (let dx = -1; dx <= 1; dx++) { const k = (cells[i] + dx) + ',' + (cells[i + 1] + dy); if (!seen.has(k)) { seen.add(k); grown.push(cells[i] + dx, cells[i + 1] + dy); } }
    return {cells, grown, w: w + 2, h: h + 2};
  }
  function pack(S) {
    const occ = new Uint8Array(G * G), rowFree = new Int32Array(G).fill(G);
    const list = islands.map(is => ({is, fp: footprint(is, S)})).sort((a, b) => b.fp.cells.length - a.fp.cells.length || b.fp.w - a.fp.w);
    let firstRow = 0;
    for (const {is, fp} of list) {
      if (fp.w > G || fp.h > G) return false;
      let placed = false;
      for (let y = firstRow; y + fp.h <= G && !placed; y++) {
        if (rowFree[y + (fp.h >> 1)] < fp.cells.length / fp.h * 0.5) continue;
        for (let x = 0; x + fp.w <= G; x++) {
          let ok = true; const g = fp.grown;
          for (let i = 0; i < g.length; i += 2) { const X = x + g[i], Y = y + g[i + 1]; if (X >= 0 && Y >= 0 && X < G && Y < G && occ[Y * G + X]) { ok = false; break; } }
          if (!ok) continue;
          for (let i = 0; i < fp.cells.length; i += 2) { const k = (y + fp.cells[i + 1]) * G + x + fp.cells[i]; occ[k] = 1; rowFree[y + fp.cells[i + 1]]--; }
          Object.assign(is, {px: (x + 1) * C, py: (y + 1) * C}); placed = true; break;
        }
      }
      if (!placed) return false;
      while (firstRow < G && rowFree[firstRow] <= 0) firstRow++;
    }
    return true;
  }
  // the largest scale that packs: bisection between 1 and startS (stepS is the resolution)
  let lo = 0, hi = startS;
  while (hi - lo > stepS) { const mid = (lo + hi) / 2; if (pack(mid)) lo = mid; else hi = mid; }
  let S = Math.floor(lo / stepS) * stepS; while (!pack(S)) S -= stepS;
  const uvOf = (is, v) => { const [u, w] = is.uv.get(v); return [(u - is.u0) * S + is.px + 0.5, (w - is.v0) * S + is.py + 0.5]; };
  const faceUV = new Map();
  for (const is of islands) for (const o of is.faces) { const m = faceUV.get(is.part) || faceUV.set(is.part, new Map()).get(is.part); m.set(o.f, o.f.ids.map(v => uvOf(is, v))); o.f.island = is; }
  return {islands, S, faceUV};
}
/** Does the triangle t touch the unit cell (x, y)? (separating axes: the cell's two and the triangle's three) */
function cellHit(t, x, y) {
  const box = [[x, y], [x + 1, y], [x + 1, y + 1], [x, y + 1]];
  for (let i = 0; i < 3; i++) {
    const a = t[i], b = t[(i + 1) % 3], c = t[(i + 2) % 3], nx = a[1] - b[1], ny = b[0] - a[0], side = nx * (c[0] - a[0]) + ny * (c[1] - a[1]);
    if (box.every(q => (nx * (q[0] - a[0]) + ny * (q[1] - a[1])) * side < -1e-12)) return false;
  }
  return true;
}
function hull2(pts) {
  const P = pts.slice().sort((a, b) => a[0] - b[0] || a[1] - b[1]), cr = (o, a, b) => (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0]);
  const lo = [], hi = [];
  for (const p of P) { while (lo.length >= 2 && cr(lo[lo.length - 2], lo[lo.length - 1], p) <= 1e-12) lo.pop(); lo.push(p); }
  for (const p of P.slice().reverse()) { while (hi.length >= 2 && cr(hi[hi.length - 2], hi[hi.length - 1], p) <= 1e-12) hi.pop(); hi.push(p); }
  return lo.slice(0, -1).concat(hi.slice(0, -1));
}
/** Interior overlap of two 2D triangles (touching edges and shared corners do not count). */
function triOverlap(A, B) {
  const scale = Math.max(...[...A, ...B].map(p => Math.max(Math.abs(p[0]), Math.abs(p[1]))), 1e-9), eps = 1e-7 * scale;
  for (const [P, Q] of [[A, B], [B, A]]) for (let i = 0; i < 3; i++) {
    const a = P[i], b = P[(i + 1) % 3], nx = -(b[1] - a[1]), ny = b[0] - a[0], l = Math.hypot(nx, ny) || 1;
    const pr = T => T.map(p => (nx * p[0] + ny * p[1]) / l), pa = pr(P), pb = pr(Q);
    if (Math.max(...pa) <= Math.min(...pb) + eps || Math.max(...pb) <= Math.min(...pa) + eps) return false;
  }
  return true;
}

// ================================================================ ray tracing (occlusion)
/** A bounding volume hierarchy over triangles [[a, b, c]...] (3D points). */
export class Bvh {
  constructor(tris) {
    const n = tris.length; this.n = n;
    this.t = new Float64Array(n * 9); tris.forEach((T, i) => T.forEach((p, k) => { this.t[i * 9 + k * 3] = p[0]; this.t[i * 9 + k * 3 + 1] = p[1]; this.t[i * 9 + k * 3 + 2] = p[2]; }));
    const idx = new Int32Array(n).map((_, i) => i), cen = new Float64Array(n * 3);
    for (let i = 0; i < n; i++) for (let a = 0; a < 3; a++) cen[i * 3 + a] = (this.t[i * 9 + a] + this.t[i * 9 + 3 + a] + this.t[i * 9 + 6 + a]) / 3;
    const nodes = []; // {min, max, left, right, start, count}
    const build = (s, e) => {
      const min = [Infinity, Infinity, Infinity], max = [-Infinity, -Infinity, -Infinity], cmin = [...min], cmax = [...max];
      for (let q = s; q < e; q++) { const i = idx[q]; for (let k = 0; k < 3; k++) for (let a = 0; a < 3; a++) { const v = this.t[i * 9 + k * 3 + a]; if (v < min[a]) min[a] = v; if (v > max[a]) max[a] = v; }
        for (let a = 0; a < 3; a++) { const c = cen[i * 3 + a]; if (c < cmin[a]) cmin[a] = c; if (c > cmax[a]) cmax[a] = c; } }
      const id = nodes.length; nodes.push({min, max, left: -1, right: -1, start: s, count: e - s});
      if (e - s <= 4) return id;
      let ax = 0; for (let a = 1; a < 3; a++) if (cmax[a] - cmin[a] > cmax[ax] - cmin[ax]) ax = a;
      const mid = (cmin[ax] + cmax[ax]) / 2; let m = s;
      for (let q = s; q < e; q++) if (cen[idx[q] * 3 + ax] < mid) { const t = idx[q]; idx[q] = idx[m]; idx[m] = t; m++; }
      if (m === s || m === e) m = (s + e) >> 1;
      nodes[id].left = build(s, m); nodes[id].right = build(m, e); nodes[id].count = 0;
      return id;
    };
    if (n) build(0, n);
    this.nodes = nodes; this.idx = idx;
  }
  /** Distance to the nearest hit along the unit direction d from o, up to tmax (Infinity: none). */
  hit(o, d, tmax) {
    if (!this.n) return Infinity;
    const inv = [1 / d[0], 1 / d[1], 1 / d[2]], st = [0], T = this.t; let best = tmax;
    while (st.length) {
      const nd = this.nodes[st.pop()];
      let t0 = 0, t1 = best;
      for (let a = 0; a < 3; a++) { let ta = (nd.min[a] - o[a]) * inv[a], tb = (nd.max[a] - o[a]) * inv[a]; if (ta > tb) { const x = ta; ta = tb; tb = x; } if (ta > t0) t0 = ta; if (tb < t1) t1 = tb; if (t0 > t1) break; }
      if (t0 > t1) continue;
      if (nd.left < 0) {
        for (let q = nd.start; q < nd.start + nd.count; q++) { const b = this.idx[q] * 9;
          const e1x = T[b + 3] - T[b], e1y = T[b + 4] - T[b + 1], e1z = T[b + 5] - T[b + 2], e2x = T[b + 6] - T[b], e2y = T[b + 7] - T[b + 1], e2z = T[b + 8] - T[b + 2];
          const px = d[1] * e2z - d[2] * e2y, py = d[2] * e2x - d[0] * e2z, pz = d[0] * e2y - d[1] * e2x, det = e1x * px + e1y * py + e1z * pz;
          if (Math.abs(det) < 1e-12) continue; const id = 1 / det, sx = o[0] - T[b], sy = o[1] - T[b + 1], sz = o[2] - T[b + 2];
          const u = (sx * px + sy * py + sz * pz) * id; if (u < 0 || u > 1) continue;
          const qx = sy * e1z - sz * e1y, qy = sz * e1x - sx * e1z, qz = sx * e1y - sy * e1x, v = (d[0] * qx + d[1] * qy + d[2] * qz) * id; if (v < 0 || u + v > 1) continue;
          const t = (e2x * qx + e2y * qy + e2z * qz) * id; if (t > 1e-9 && t < best) best = t; }
      } else { st.push(nd.left, nd.right); }
    }
    return best;
  }
}
/** A fixed set of cosine-weighted hemisphere directions (around +z), n of them (Fibonacci spiral). */
export function hemisphere(n) {
  return Array.from({length: n}, (_, i) => { const r = Math.sqrt((i + 0.5) / n), a = i * 2.399963229728653; return [r * Math.cos(a), r * Math.sin(a), Math.sqrt(1 - r * r)]; });
}
/** Occlusion 0..1 (1 = open) at a point with normal n: rays within maxD; hits weigh 1 up to half of maxD, fading to 0. */
export function occlusion(bvh, pos, n, dirs, maxD, rot = 0) {
  const t = norm(Math.abs(n[0]) < 0.8 ? cross(n, [1, 0, 0]) : cross(n, [0, 1, 0])), b = cross(n, t), c = Math.cos(rot), s = Math.sin(rot);
  const o = add(pos, mul(n, maxD * 0.004)); let occ = 0;
  for (const [x0, y0, z] of dirs) { const x = c * x0 - s * y0, y = s * x0 + c * y0;
    const d = [t[0] * x + b[0] * y + n[0] * z, t[1] * x + b[1] * y + n[1] * z, t[2] * x + b[2] * y + n[2] * z];
    const h = bvh.hit(o, d, maxD); if (h < maxD) occ += h < maxD / 2 ? 1 : 2 - 2 * h / maxD; }
  return 1 - occ / dirs.length;
}

// ================================================================ bake
/**
 * Paints one atlas. opts: {parts (the atlas' parts, runtime frame), uv (unfoldUV result), atlas, pad, mats (name -> {c,
 * sm, f0, shade?(ctx) -> {c?, sm?, f0?, h?, ao?, alpha?}, alpha?, aoK?}), toModel(pos) -> the caller's frame for shade
 * patterns, bvh, aoDirs, aoMax (runtime units), aoStride, bump (normal-map strength), background}.
 * shade ctx: {p (caller frame), n (normal, caller frame), part, face, mat}. h: height in runtime units.
 * Returns {PNG: [base, _s, _n], coverage}.
 */
export function bake({parts, uv, atlas: A, pad, mats, toModel = q => q, toModelDir = q => q, bvh = null, aoDirs = null, aoMax = 8, aoStride = 2, bump = 1, background = {c: [40, 40, 40, 255], s: [60, 20, 0, 255], n: [128, 128, 255, 255]}}) {
  const N = A * A, col = new Float32Array(N * 3), sm = new Uint8Array(N), f0 = new Uint8Array(N), alpha = new Uint8Array(N).fill(255);
  const hgt = new Float32Array(N), isl = new Int32Array(N).fill(-1), aoMul = new Float32Array(N).fill(1), aoK = new Float32Array(N).fill(1);
  const P = new Float32Array(N * 3), NN = new Float32Array(N * 3), mask = new Uint8Array(N);
  const islIndex = new Map(uv.islands.map((is, i) => [is, i]));
  const raster = (pass) => {
    for (const part of parts) {
      const fu = uv.faceUV.get(part); if (!fu) continue;
      for (const f of part.f) {
        const T = fu.get(f); if (!T) continue; const is = f.island, ii = islIndex.get(is), m = mats[f.mat], V = f.ids.map(i => part.v[i]), VN = f.vn || f.ids.map(() => norm(newell(V)));
        for (let q = 1; q + 1 < T.length; q++) {
          const t = [T[0], T[q], T[q + 1]], v = [V[0], V[q], V[q + 1]], vn = [VN[0], VN[q], VN[q + 1]];
          const den = (t[1][1] - t[2][1]) * (t[0][0] - t[2][0]) + (t[2][0] - t[1][0]) * (t[0][1] - t[2][1]); if (Math.abs(den) < 1e-12) continue;
          const tol = pass ? -0.9 / Math.max(1, Math.sqrt(Math.abs(den))) : 0;
          const x0 = Math.max(0, Math.floor(Math.min(t[0][0], t[1][0], t[2][0]) - 1)), x1 = Math.min(A - 1, Math.ceil(Math.max(t[0][0], t[1][0], t[2][0]) + 1));
          const y0 = Math.max(0, Math.floor(Math.min(t[0][1], t[1][1], t[2][1]) - 1)), y1 = Math.min(A - 1, Math.ceil(Math.max(t[0][1], t[1][1], t[2][1]) + 1));
          for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) {
            const X = x + 0.5, Y = y + 0.5, k = y * A + x;
            let l1 = ((t[1][1] - t[2][1]) * (X - t[2][0]) + (t[2][0] - t[1][0]) * (Y - t[2][1])) / den, l2v = ((t[2][1] - t[0][1]) * (X - t[2][0]) + (t[0][0] - t[2][0]) * (Y - t[2][1])) / den, l3 = 1 - l1 - l2v;
            if (l1 < tol || l2v < tol || l3 < tol) continue;
            if (pass ? mask[k] : mask[k] === 1) continue;
            if (pass) { l1 = Math.max(0, l1); l2v = Math.max(0, l2v); l3 = Math.max(0, l3); const s = l1 + l2v + l3; l1 /= s; l2v /= s; l3 /= s; }
            mask[k] = pass ? 2 : 1;
            const pos = [0, 1, 2].map(a => v[0][a] * l1 + v[1][a] * l2v + v[2][a] * l3), nrm = norm([0, 1, 2].map(a => vn[0][a] * l1 + vn[1][a] * l2v + vn[2][a] * l3));
            const r = m.shade ? m.shade({p: toModel(pos), n: toModelDir(nrm), part, face: f, mat: f.mat}) || {} : {};
            const c = r.c || m.c;
            col.set(c, k * 3); sm[k] = Math.round(clamp(r.sm ?? m.sm, 0, 255)); f0[k] = Math.round(clamp(r.f0 ?? m.f0, 0, 255));
            alpha[k] = Math.round(r.alpha ?? m.alpha ?? 255); hgt[k] = r.h || 0; aoMul[k] = r.ao ?? 1; aoK[k] = m.aoK ?? 1; isl[k] = ii;
            P.set(pos, k * 3); NN.set(nrm, k * 3);
          }
        }
      }
    }
  };
  raster(0); raster(1);
  // occlusion: on a stride grid, then every texel from the samples of its own island nearby
  const ao = new Float32Array(N).fill(1);
  if (bvh && aoDirs) {
    const S = aoStride, gw = Math.ceil(A / S), samp = new Float32Array(gw * gw).fill(-1), sIsl = new Int32Array(gw * gw).fill(-1);
    for (let gy = 0; gy < gw; gy++) for (let gx = 0; gx < gw; gx++) {
      // the sample: the block's first covered texel
      let k = -1; for (let dy = 0; dy < S && k < 0; dy++) for (let dx = 0; dx < S && k < 0; dx++) { const x = gx * S + dx, y = gy * S + dy; if (x < A && y < A && mask[y * A + x] && aoK[y * A + x] > 0) k = y * A + x; }
      if (k < 0) continue;
      const pos = [P[k * 3], P[k * 3 + 1], P[k * 3 + 2]], n = [NN[k * 3], NN[k * 3 + 1], NN[k * 3 + 2]];
      samp[gy * gw + gx] = occlusion(bvh, pos, n, aoDirs, aoMax, ((gx * 73856093) ^ (gy * 19349663)) % 628 / 100); sIsl[gy * gw + gx] = isl[k];
    }
    for (let y = 0; y < A; y++) for (let x = 0; x < A; x++) {
      const k = y * A + x; if (!mask[k] || aoK[k] <= 0) continue;
      const gx = (x + 0.5) / S - 0.5, gy = (y + 0.5) / S - 0.5, bx = Math.floor(gx), by = Math.floor(gy);
      let s = 0, w = 0;
      for (let dy = -1; dy <= 2; dy++) for (let dx = -1; dx <= 2; dx++) { const X = bx + dx, Y = by + dy; if (X < 0 || Y < 0 || X >= gw || Y >= gw) continue;
        const g = Y * gw + X; if (samp[g] < 0 || sIsl[g] !== isl[k]) continue; const dd = Math.hypot(X - gx, Y - gy), ww = Math.max(0, 1.6 - dd); if (ww <= 0) continue; s += samp[g] * ww; w += ww; }
      ao[k] = w > 0 ? s / w : 1;
    }
  }
  // outputs
  const base = Buffer.alloc(N * 4), spec = Buffer.alloc(N * 4), nm = Buffer.alloc(N * 4);
  for (let k = 0; k < N; k++) { base.set(background.c, k * 4); spec.set(background.s, k * 4); nm.set(background.n, k * 4); }
  const S = uv.S;
  for (let k = 0; k < N; k++) {
    if (!mask[k]) continue;
    const a = clamp(1 - (1 - ao[k]) * aoK[k], 0, 1) * aoMul[k];   // occlusion this texel keeps
    const inCol = 1 - 0.6 * (1 - a), inN = 1 - 0.45 * (1 - a);
    base[k * 4] = clamp(Math.round(col[k * 3] * inCol), 0, 255); base[k * 4 + 1] = clamp(Math.round(col[k * 3 + 1] * inCol), 0, 255); base[k * 4 + 2] = clamp(Math.round(col[k * 3 + 2] * inCol), 0, 255); base[k * 4 + 3] = alpha[k];
    spec[k * 4] = sm[k]; spec[k * 4 + 1] = f0[k]; spec[k * 4 + 2] = 0; spec[k * 4 + 3] = 255;
    const x = k % A, y = (k - x) / A, h = i => (i >= 0 && i < N && isl[i] === isl[k]) ? hgt[i] : hgt[k];
    const hx = (h(x + 1 < A ? k + 1 : k) - h(x > 0 ? k - 1 : k)) / 2 * S, hy = (h(k + A) - h(k - A)) / 2 * S;
    let nx = -hx * bump, ny = -hy * bump; const l = Math.hypot(nx, ny, 1); nx /= l; ny /= l;
    nm[k * 4] = Math.round((nx * 0.5 + 0.5) * 255); nm[k * 4 + 1] = Math.round((ny * 0.5 + 0.5) * 255); nm[k * 4 + 2] = Math.round(255 * clamp(inN, 0, 1)); nm[k * 4 + 3] = 255;
  }
  // gutters: each empty texel within pad of a covered one copies its nearest covered neighbour (no dark seams when filtered)
  for (let it = 0; it < pad; it++) {
    const add1 = [];
    for (let k = 0; k < N; k++) { if (mask[k]) continue; const x = k % A;
      for (const j of [k - 1, k + 1, k - A, k + A]) { if (j < 0 || j >= N || !mask[j] || (j === k - 1 && x === 0) || (j === k + 1 && x === A - 1)) continue; add1.push([k, j]); break; } }
    for (const [k, j] of add1) { base.copy(base, k * 4, j * 4, j * 4 + 4); spec.copy(spec, k * 4, j * 4, j * 4 + 4); nm.copy(nm, k * 4, j * 4, j * 4 + 4); mask[k] = 3; }
  }
  let cov = 0; for (let k = 0; k < N; k++) if (mask[k] === 1 || mask[k] === 2) cov++;
  return {PNG: [png(base, A, A), png(spec, A, A), png(nm, A, A)], raw: [base, spec, nm], coverage: cov / N, ao, mask, isl, P};
}
