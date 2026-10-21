// Halstead Motor Company badges as mesh outlines (2026-10-21, user choice from design/vehicles/halstead_logo/
// halstead_logo_concepts_r1.png): B, the three-bar pine keystone (grille badge), and A, the HALSTEAD wordmark (stamped
// into tailgates). Geometric capitals drawn as polygons, so nothing depends on a font. Every outline is (u, v) with v up;
// the callers extrude it with slab() in their own frame.
import {slab, fillet, offsetLoop} from './vehicle-mesh-kit.mjs';

/** Offsets an open polyline by d to its left (mitred joins). */
function offsetOpen(L, d) {
  const n = L.length;
  return L.map((b, i) => {
    const a = L[Math.max(0, i - 1)], c = L[Math.min(n - 1, i + 1)];
    const e = (p, q) => { const x = q[0] - p[0], y = q[1] - p[1], l = Math.hypot(x, y) || 1; return [x / l, y / l]; };
    const e1 = i > 0 ? e(a, b) : e(b, c), e2 = i < n - 1 ? e(b, c) : e1, m1 = [-e1[1], e1[0]], m2 = [-e2[1], e2[0]];
    const k = d / Math.max(0.3, 1 + m1[0] * m2[0] + m1[1] * m2[1]);
    return [b[0] + k * (m1[0] + m2[0]), b[1] + k * (m1[1] + m2[1])];
  });
}
/** A stroke of width t along an open centre line. */
const stroke = (L, t) => [...offsetOpen(L, -t / 2), ...offsetOpen(L, t / 2).reverse()];

// ---------------------------------------------------------------- B: three-bar pine keystone
/**
 * The keystone ring, the three tapered bars and the trunk (unit s = the badge's half height / 0.78), centred on (cu, cv).
 * Returns {ring: [outer, hole], solids: [outline...]}.
 */
export function pineKeystone(cu, cv, s) {
  const k = ([u, v]) => [cu + u * s, cv + v * s];
  // the keystone: wide at the top, narrow at the bottom (the concept's quadratic corners as fillets)
  const mid = fillet([[-0.50, -0.78], [0.50, -0.78], [0.70, 0.78], [-0.70, 0.78]], [0.12, 0.12, 0.08, 0.08], 4);
  const ring = [offsetLoop(mid, -0.0375), offsetLoop(mid, 0.0375).reverse()].map(L => L.map(k));
  // bars, top to bottom: [top v, half width at the bottom]; each tapers to 35 % at its top, 0.17 tall
  const bars = [[0.50, 0.16], [0.20, 0.30], [-0.10, 0.44]].map(([t, hw]) =>
    [[-hw, t - 0.17], [hw, t - 0.17], [hw * 0.35, t], [-hw * 0.35, t]].map(k));
  const trunk = [[-0.06, -0.52], [0.06, -0.52], [0.06, -0.29], [-0.06, -0.29]].map(k);
  return {ring, solids: [...bars, trunk]};
}
/** The B badge extruded in frame F from a0 to a1 (the face at a1 rounded by r). */
export function pineBadge(p, F, cu, cv, s, a0, a1, r = 0.0015) {
  const {ring, solids} = pineKeystone(cu, cv, s), o = {r0: 0, r1: r};
  slab(p, F, ring[0], [ring[1]], a0, a1, o);
  for (const L of solids) slab(p, F, L, [], a0, a1, o);
}

// ---------------------------------------------------------------- A: HALSTEAD wordmark
const T = 0.18;   // stroke, in letter heights
/** Glyph outlines in letter heights (origin bottom left): {w: advance width, loops: [[outer, ...holes]...]}. */
const GLYPHS = {
  H: (() => { const w = 0.92, a = 0.5 - T / 2, b = 0.5 + T / 2;
    return {w, loops: [[[[0, 0], [T, 0], [T, a], [w - T, a], [w - T, 0], [w, 0], [w, 1], [w - T, 1], [w - T, b], [T, b], [T, 1], [0, 1]]]]}; })(),
  A: (() => { const w = 0.98, k = w / 2 - 0.12, tl = T * 1.15, c0 = 0.22, c1 = c0 + T;
    const apex = (w - 2 * tl) / (2 * k);
    return {w, loops: [[
      [[0, 0], [tl, 0], [tl + k * c0, c0], [w - tl - k * c0, c0], [w - tl, 0], [w, 0], [w - k, 1], [k, 1]],
      [[tl + k * c1, c1], [w / 2, apex], [w - tl - k * c1, c1]]]]}; })(),
  L: (() => { const w = 0.80; return {w, loops: [[[[0, 0], [w, 0], [w, T], [T, T], [T, 1], [0, 1]]]]}; })(),
  S: (() => { const w = 0.92, h = T / 2;
    const line = fillet([[0, h], [w - h, h], [w - h, 0.5], [h, 0.5], [h, 1 - h], [w, 1 - h]], 0.17, 6, false);
    return {w, loops: [[stroke(line, T)]]}; })(),
  T: (() => { const w = 0.86, a = w / 2 - T / 2, b = w / 2 + T / 2;
    return {w, loops: [[[[a, 0], [b, 0], [b, 1 - T], [w, 1 - T], [w, 1], [0, 1], [0, 1 - T], [a, 1 - T]]]]}; })(),
  E: (() => { const w = 0.80, a = 0.5 - T / 2, b = 0.5 + T / 2;
    return {w, loops: [[[[0, 0], [w, 0], [w, T], [T, T], [T, a], [w * 0.9, a], [w * 0.9, b], [T, b], [T, 1 - T], [w, 1 - T], [w, 1], [0, 1]]]]}; })(),
  D: (() => { const w = 0.92, r = 0.34;
    return {w, loops: [[fillet([[0, 0], [w, 0], [w, 1], [0, 1]], [0, r, r, 0], 6),
      fillet([[T, T], [w - T, T], [w - T, 1 - T], [T, 1 - T]], [0, r - T, r - T, 0], 6).reverse()]]}; })(),
};
const GAP = 0.16;
/** Total width of a word in letter heights. */
export const wordWidth = text => [...text].reduce((s, c) => s + GLYPHS[c].w, 0) + GAP * (text.length - 1);
/**
 * The word as outlines, centred on (cu, cv), letters h tall, reading toward +u (pass dir -1 when +u runs right to left
 * for the viewer). Returns [[outer, ...holes]...] per solid.
 */
export function wordOutlines(text, cu, cv, h, dir = 1) {
  const out = [];
  let x = -wordWidth(text) / 2;
  for (const c of text) {
    const g = GLYPHS[c];
    if (!g) throw new Error('halstead-badge: no glyph ' + c);
    for (const loops of g.loops) out.push(loops.map(L => L.map(([u, v]) => [cu + dir * (x + u) * h, cv + (v - 0.5) * h])));
    x += g.w + GAP;
  }
  return out;
}
/** The word extruded in frame F from a0 to a1, the face at a0 rounded by r (stamped letters stand out toward a0). */
export function wordmark(p, F, text, cu, cv, h, a0, a1, {dir = 1, r = 0.0015} = {}) {
  for (const [outer, ...holes] of wordOutlines(text, cu, cv, h, dir)) slab(p, F, outer, holes, a0, a1, {r0: r, r1: 0});
}
