// Boulder shapes for Rock R1 (docs/gameplay/rock_r1_stones_boulders_design.md 3.1): 12 base forms of one rock (grey
// granite field stone), each a closed triangle mesh in metres (x east, y up, z south, origin at the base centre), plus
// the fragments a boulder breaks into when it is struck open (3.4): Voronoi pieces cut from the same mesh, two levels.
//
// A form starts as an icosphere pushed out by low-frequency 3D noise (star-shaped about its centre), stretched to the
// form's proportions, then flattened onto a few fracture planes (talus blocks keep them sharp, glacial erratics are
// rounded off by Laplacian smoothing), then given a little surface relief. Fragments clip that mesh by the bisector
// planes between seed points and cap every cut with a fan, so the pieces of one boulder put back together are the whole
// (checked by volume in build-boulders.mjs --check).
//
// Everything is deterministic: the seed of a form is its index, of a fragment its parent's seed and its own index.

/** Integer hash -> 0..1. */
export const hash = (a, b = 0, c = 0) => {
  let h = Math.imul(a | 0, 374761393) ^ Math.imul(b | 0, 668265263) ^ Math.imul(c | 0, 2147483647);
  h = Math.imul(h ^ (h >>> 13), 1274126177);
  return ((h ^ (h >>> 16)) >>> 0) / 4294967295;
};
const add = (a, b) => [a[0] + b[0], a[1] + b[1], a[2] + b[2]], sub = (a, b) => [a[0] - b[0], a[1] - b[1], a[2] - b[2]];
const mul = (a, k) => [a[0] * k, a[1] * k, a[2] * k], dot = (a, b) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
const cross = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
const norm = a => { const l = Math.hypot(a[0], a[1], a[2]) || 1; return [a[0] / l, a[1] / l, a[2] / l]; };
export const V = {add, sub, mul, dot, cross, norm};

/** Smooth 3D value noise, -1..1. */
function vnoise(x, y, z, seed) {
  const xi = Math.floor(x), yi = Math.floor(y), zi = Math.floor(z), s = t => t * t * (3 - 2 * t), l = (a, b, t) => a + (b - a) * t;
  const fx = s(x - xi), fy = s(y - yi), fz = s(z - zi), v = (i, j, k) => hash(xi + i + seed * 7919, yi + j, zi + k);
  return 2 * l(l(l(v(0, 0, 0), v(1, 0, 0), fx), l(v(0, 1, 0), v(1, 1, 0), fx), fy),
    l(l(v(0, 0, 1), v(1, 0, 1), fx), l(v(0, 1, 1), v(1, 1, 1), fx), fy), fz) - 1;
}
const fbm = (p, seed, oct) => oct.reduce((s, [f, a], i) => s + a * vnoise(p[0] * f, p[1] * f, p[2] * f, seed * 31 + i), 0);

/** Icosphere of the given subdivision level: {P, T} on the unit sphere, counter-clockwise from outside. */
export function icosphere(level) {
  const t = (1 + Math.sqrt(5)) / 2;
  let P = [[-1, t, 0], [1, t, 0], [-1, -t, 0], [1, -t, 0], [0, -1, t], [0, 1, t], [0, -1, -t], [0, 1, -t], [t, 0, -1], [t, 0, 1], [-t, 0, -1], [-t, 0, 1]].map(norm);
  let T = [[0, 11, 5], [0, 5, 1], [0, 1, 7], [0, 7, 10], [0, 10, 11], [1, 5, 9], [5, 11, 4], [11, 10, 2], [10, 7, 6], [7, 1, 8],
    [3, 9, 4], [3, 4, 2], [3, 2, 6], [3, 6, 8], [3, 8, 9], [4, 9, 5], [2, 4, 11], [6, 2, 10], [8, 6, 7], [9, 8, 1]];
  for (let l = 0; l < level; l++) {
    const mid = new Map(), m = (a, b) => {
      const k = a < b ? a * 100000 + b : b * 100000 + a;
      if (!mid.has(k)) { mid.set(k, P.length); P.push(norm(mul(add(P[a], P[b]), 0.5))); }
      return mid.get(k);
    };
    T = T.flatMap(([a, b, c]) => { const ab = m(a, b), bc = m(b, c), ca = m(c, a); return [[a, ab, ca], [b, bc, ab], [c, ca, bc], [ab, bc, ca]]; });
  }
  return {P, T};
}

/** Vertex neighbours of an indexed mesh. */
function neighbours(m) {
  const N = m.P.map(() => new Set());
  for (const [a, b, c] of m.T) { N[a].add(b).add(c); N[b].add(a).add(c); N[c].add(a).add(b); }
  return N.map(s => [...s]);
}

/**
 * The 12 forms (3.1): id, longest horizontal extent L (m), proportions [x, y (height), z], fracture planes and how deep
 * they cut (cut: fraction of the extent where the plane lies), smoothing of the body before the planes (pre) and of
 * their edges after (round; 0 keeps a talus block's faces sharp), noise amplitude, and for the composite ones how they
 * are put together (split: one rock cut in two with a gap; cluster: several rocks side by side); upright: its planes stand
 * near vertical (a column-like block). Every rock also rests
 * on a flattish bottom (it settled; the bottom is buried anyway). In the game (R1a): bury, the share of its height in the
 * ground (3.3: 20-60%, a test value); lichen, an old exposed boulder (the weathered sprite).
 */
export const FORMS = [
  {id: 'erratic_round', bury: 0.3, lichen: true, L: 1.6, r: [1, 0.72, 0.86], planes: 5, pre: 1, round: 1, amp: 0.2, cut: [0.6, 0.86]},
  {id: 'erratic_long', bury: 0.28, L: 2.4, r: [1, 0.55, 0.62], planes: 5, pre: 1, round: 1, amp: 0.18, cut: [0.6, 0.86]},
  {id: 'erratic_big', bury: 0.35, lichen: true, L: 3.0, r: [1, 0.66, 0.82], planes: 6, pre: 1, round: 1, amp: 0.18, cut: [0.62, 0.86]},
  {id: 'slab_flat', bury: 0.3, lichen: true, L: 2.0, r: [1, 0.33, 0.8], planes: 5, pre: 1, round: 1, amp: 0.1, top: true},
  {id: 'slab_tilted', bury: 0.25, L: 1.7, r: [1, 0.38, 0.74], planes: 5, pre: 1, round: 1, amp: 0.11, top: true, tilt: 0.28},
  {id: 'talus_block', bury: 0.18, L: 1.2, r: [1, 0.78, 0.82], planes: 8, pre: 0, round: 0, amp: 0.08, cut: [0.5, 0.74]},
  {id: 'talus_wedge', bury: 0.2, L: 1.4, r: [1, 0.66, 0.6], planes: 7, pre: 0, round: 0, amp: 0.08, cut: [0.5, 0.74], tilt: 0.22},
  {id: 'split_pair', bury: 0.3, lichen: true, L: 2.2, r: [1, 0.68, 0.9], planes: 5, pre: 1, round: 1, amp: 0.18, split: {gap: 0.07, at: 0.08}, cut: [0.62, 0.86]},
  {id: 'tall_block', bury: 0.15, L: 1.0, r: [1, 1.25, 0.8], planes: 8, pre: 0, round: 0, amp: 0.08, cut: [0.5, 0.72], upright: true},
  {id: 'small_round', bury: 0.3, L: 0.7, r: [1, 0.74, 0.9], planes: 2, pre: 3, round: 1, amp: 0.16},
  {id: 'cluster_three', bury: 0.25, L: 0.9, r: [1, 0.7, 0.85], planes: 4, pre: 1, round: 1, amp: 0.14,
    cluster: [[0, 0, 0, 1], [0.78, 0, 0.35, 0.72], [-0.2, 0, 0.8, 0.62]]},
  {id: 'cluster_two', bury: 0.3, lichen: true, L: 1.3, r: [1, 0.62, 0.8], planes: 4, pre: 2, round: 1, amp: 0.14, cluster: [[0, 0, 0, 1], [0.95, 0, -0.45, 0.6]]},
];

/** One rock: L, proportions, planes, smoothing, noise; seed. Mesh in metres, centred, base at y = 0. */
export function rock({L, r, planes, pre, round, amp, top = false, tilt = 0, cut = [0.66, 0.86], upright = false}, seed, level = 3) {
  const m = icosphere(level), N = neighbours(m);
  const half = [L / 2 * r[0], L / 2 * r[1], L / 2 * r[2]];
  const relax = (w, n) => { for (let s = 0; s < n; s++) m.P = m.P.map((p, i) => { const c = mul(N[i].reduce((a, j) => add(a, m.P[j]), [0, 0, 0]), 1 / N[i].length); return add(mul(p, 1 - w), mul(c, w)); }); };
  // star-shaped body: radius pushed by three octaves of noise on the direction, a lean so it is not symmetric
  const lean = [hash(seed, 1, 9) - 0.5, hash(seed, 2, 9) - 0.5];
  m.P = m.P.map(d => {
    const k = 1 + amp * fbm(d, seed, [[0.9, 1], [1.9, 0.5], [3.8, 0.22]]);
    const y = d[1] * half[1] * k;
    return [d[0] * half[0] * k + lean[0] * 0.35 * y, y, d[2] * half[2] * k + lean[1] * 0.35 * y];
  });
  relax(0.5, pre);
  // fracture planes: random directions (a slab's first plane is its top), then the settled bottom
  const cutAt = (n, f) => { const ext = Math.max(...m.P.map(p => dot(p, n))), o = ext * f; m.P = m.P.map(p => { const d = dot(p, n) - o; return d > 0 ? sub(p, mul(n, d)) : p; }); };
  for (let i = 0; i < planes; i++) {
    let n;
    if (top && i === 0) n = norm([hash(seed, i, 14) * 0.2 - 0.1, 1, hash(seed, i, 15) * 0.2 - 0.1]);
    else {
      const a = hash(seed, i, 11) * Math.PI * 2, e = (hash(seed, i, 12) - 0.3) * (upright ? 0.35 : 1.2);
      n = norm([Math.cos(a) * Math.cos(e), Math.sin(e), Math.sin(a) * Math.cos(e)]);
    }
    cutAt(n, cut[0] + (cut[1] - cut[0]) * hash(seed, i, 13));
  }
  cutAt(norm([hash(seed, 3, 9) * 0.3 - 0.15, -1, hash(seed, 4, 9) * 0.3 - 0.15]), 0.72);
  // edges worn round (glacial transport), then fine relief
  relax(0.35, round);
  m.P = m.P.map(p => { const d = norm(p); return add(p, mul(d, L * (round ? 0.01 : 0.014) * fbm(mul(p, 1 / L), seed + 500, [[7, 1], [15, 0.5]]))); });
  if (tilt) { const c = Math.cos(tilt), s = Math.sin(tilt); m.P = m.P.map(([x, y, z]) => [x * c - y * s, x * s + y * c, z]); }
  const y0 = Math.min(...m.P.map(p => p[1]));
  m.P = m.P.map(p => [p[0], p[1] - y0, p[2]]);
  return m;
}

/**
 * Keeps the part of mesh m on the side dot(p, n) <= o and caps each cut with a fan from its loop's centroid (the cut is
 * planar, so the fan's signed area is the loop's even where the loop is not convex). Returns a new compact mesh; F marks
 * each triangle 0 (the rock's skin) or the cap flag (1: a fresh break, drawn with the fresh sprite).
 */
export function clip(m, n, o, cap = 1) {
  const d = m.P.map(p => { const v = dot(p, n) - o; return Math.abs(v) < 1e-9 ? 1e-9 : v; });
  const P = m.P.slice(), T = [], F = [], cut = new Map(), next = new Map();
  const at = (i, j) => {
    const k = i < j ? i * 1e6 + j : j * 1e6 + i;
    if (!cut.has(k)) { cut.set(k, P.length); P.push(add(m.P[i], mul(sub(m.P[j], m.P[i]), d[i] / (d[i] - d[j])))); }
    return cut.get(k);
  };
  m.T.forEach((t, ti) => {
    const f = m.F ? m.F[ti] : 0, inside = t.map(i => d[i] <= 0), c = inside.filter(Boolean).length;
    if (c === 3) { T.push(t); F.push(f); return; }
    if (c === 0) return;
    // rotate so the pattern starts where it should, keeping the winding
    let r = 0;
    if (c === 1) r = inside.indexOf(true); else r = (inside.indexOf(false) + 1) % 3;
    const [a, b, cc] = [t[r], t[(r + 1) % 3], t[(r + 2) % 3]];
    if (c === 1) { const ab = at(a, b), ac = at(a, cc); T.push([a, ab, ac]); F.push(f); next.set(ab, ac); }
    else { const bc = at(b, cc), ca = at(cc, a); T.push([a, b, bc], [a, bc, ca]); F.push(f, f); next.set(bc, ca); }
  });
  // caps: walk each loop of directed cut edges, fan it from its centroid with the opposite winding
  const seen = new Set();
  for (const s of next.keys()) {
    if (seen.has(s)) continue;
    const loop = [];
    for (let u = s; u !== undefined && !seen.has(u); u = next.get(u)) { seen.add(u); loop.push(u); }
    if (loop.length < 3) continue;
    const c = P.length; P.push(mul(loop.reduce((a, i) => add(a, P[i]), [0, 0, 0]), 1 / loop.length));
    for (let i = 0; i < loop.length; i++) { T.push([c, loop[(i + 1) % loop.length], loop[i]]); F.push(cap); }
  }
  return compact({P, T, F});
}

function compact(m) {
  const map = new Map(), P = [];
  const T = m.T.map(t => t.map(i => { if (!map.has(i)) { map.set(i, P.length); P.push(m.P[i]); } return map.get(i); }));
  return {P, T, F: m.F};
}

export const volume = m => m.T.reduce((s, [a, b, c]) => s + dot(m.P[a], cross(m.P[b], m.P[c])), 0) / 6;
export const merge = ms => { const P = [], T = [], F = []; for (const m of ms) { const o = P.length; P.push(...m.P); T.push(...m.T.map(t => t.map(i => i + o))); F.push(...(m.F || m.T.map(() => 0))); } return {P, T, F}; };
export const translate = (m, v) => ({P: m.P.map(p => add(p, v)), T: m.T, F: m.F});
export const centroid = m => mul(m.P.reduce((a, p) => add(a, p), [0, 0, 0]), 1 / m.P.length);
export const bounds = m => [0, 1, 2].map(k => [Math.min(...m.P.map(p => p[k])), Math.max(...m.P.map(p => p[k]))]);

/** A form as its parts (one mesh per separate rock: a split pair is two, a cluster two or three). */
export function form(f, index) {
  const seed = 1000 + index * 17;
  if (f.cluster) return f.cluster.map(([x, y, z, k], i) => translate(rock({...f, L: f.L * k}, seed + i * 101), [x * f.L, y, z * f.L]));
  const m = rock(f, seed);
  if (!f.split) return [m];
  const n = norm([1, 0, 0.25]), o = f.split.at * f.L;
  // an old crack: its faces weathered like the rest (cap 0), not a fresh break
  return [translate(clip(m, n, o, 0), mul(n, -f.split.gap / 2)), translate(clip(m, mul(n, -1), -o, 0), mul(n, f.split.gap / 2))];
}

/**
 * The pieces rock m breaks into (3.4): count seeds spread through its body, each piece the mesh clipped to its seed's
 * Voronoi cell. Seeds sit 35% of the way to the surface in evenly turned directions, so the pieces are of a size.
 */
export function fragments(m, count, seed) {
  const c = centroid(m), b = bounds(m), ext = b.map(([lo, hi]) => (hi - lo) / 2);
  const a0 = hash(seed, 1, 3) * Math.PI * 2;
  const S = [];
  for (let i = 0; i < count; i++) {
    const a = a0 + i * Math.PI * 2 / count + (hash(seed, i, 4) - 0.5) * 0.6, e = (hash(seed, i, 5) - 0.5) * 0.8;
    S.push(add(c, [Math.cos(a) * ext[0] * 0.38, Math.sin(e) * ext[1] * 0.35, Math.sin(a) * ext[2] * 0.38]));
  }
  return S.map((s, k) => {
    let p = m;
    S.forEach((t, j) => { if (j !== k) { const n = norm(sub(t, s)); p = clip(p, n, dot(n, mul(add(s, t), 0.5))); } });
    return p;
  });
}

/** Pieces of the first level (by volume: 2..4) and of the second (each first-level piece over 0.25 m^3 in 2..3). */
export const level1Count = v => Math.max(2, Math.min(4, Math.round(v / 0.18)));
export const level2Count = v => (v > 0.25 ? 3 : 2);

/** Strikes a piece takes (3.4): ceil(volume x 40), 3..30. */
export const strikes = v => Math.max(3, Math.min(30, Math.ceil(v * 40)));

/** The most strikes a whole boulder takes, every part and piece together (user 2026-10-21: "大石头也不要超过50下"). */
export const MAX_STRIKES = 50;

/**
 * The strikes of each stage of one boulder (raw: strikes() of each stage, in order), scaled down in proportion when they
 * add up to more than MAX_STRIKES: each stage keeps at least 1, the rest goes by largest remainder.
 */
export function budget(raw) {
  const total = raw.reduce((a, b) => a + b, 0);
  if (total <= MAX_STRIKES) return raw.slice();
  const want = raw.map(r => r * MAX_STRIKES / total), out = want.map(w => Math.max(1, Math.floor(w)));
  let left = MAX_STRIKES - out.reduce((a, b) => a + b, 0);
  const order = want.map((w, i) => [w - out[i], i]).sort((a, b) => b[0] - a[0]);
  for (let k = 0; left > 0; k = (k + 1) % order.length, left--) out[order[k][1]]++;
  return out;
}
