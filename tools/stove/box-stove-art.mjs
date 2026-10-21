// Box Stove V1 (docs/models/box_stove_v1.md, 2026-10-13): the textures of the cast-iron box stove, the stove heat shield
// and the flagstone (tools/build-box-stove-v1.mjs). Forest start home B (docs/worldgen/forest_start_home_b_v1_plan.md,
// M2 F1); user 2026-10-13: stove A (the old long box stove), the heat shield a block of its own, the hearth flat stone slabs.
// 240 texels = 1 m, LabPBR like cabin-art (renderX). AFL style: clean and quiet; the mesh carries the bevels and beads, the
// iron stays a dark satin stove black (dielectric F0: a metal F0 on big dark panels glares under shaders), relief only
// where the material has it (the flagstone joints, >= 5 texels wide, shallow), no per-texel noise.
import {png} from '../cube-slab-mesh-lib.mjs';
import {N, hash, clamp, sstep, mul, mix, vn, cells} from '../mesh-trees/hd-art.mjs';
import {base} from '../wood-photo/sample.mjs';

const SS = 3, MM = 1000 / N;

/** Cast iron in old stove black (graphite polish): near black, satin, a few soft warm-grey heat and ash patches. */
export function stoveIron() {
  return (x, y) => {
    let c = mul([40, 40, 41], 1 + 0.05 * vn(x, y, 7000, 40) + 0.025 * vn(x, y, 7001, 12));
    c = mix(c, [72, 66, 60], 0.16 * sstep(0.5, 1, vn(x, y, 7002, 60, 30)));
    return {c, s: 88, f0: 10, p: 0, h: 0, ao: 1};
  };
}
/** The raised cast parts (frames, corner beads, rims, the lid): the same iron worn smoother and a step lighter. */
export function stoveIronEdge() {
  return (x, y) => ({c: mul([66, 67, 69], 1 + 0.05 * vn(x, y, 7010, 30)), s: 128, f0: 10, p: 0, h: 0, ao: 1});
}
/** Soot black (inside the flue, the draft holes, the lid's gap): matte. */
export function soot() {
  return (x, y) => ({c: mul([20, 19, 18], 1 + 0.06 * vn(x, y, 7020, 30)), s: 18, f0: 10, p: 40, h: 0, ao: 1});
}
/** Nickel plate (the coil-spring handles): a bright metal; small parts only. */
export function nickel() {
  return (x, y) => ({c: mul([150, 147, 140], 1 + 0.03 * vn(x, y, 7030, 40)), s: 190, f0: 230, p: 0, h: 0, ao: 1});
}
/**
 * Firebrick lining (inside the firebox): buff splits laid in courses, 23 x 6.4 cm (55 x 15 texels), the thin refractory
 * joints as colour only (no relief: the lining is seen only through the door), soot over it in soft patches.
 */
export function firebrick() {
  const BW = N / 4.35, BH = N / 15.6, seed = 7040;
  return (x, y) => {
    const row = Math.floor(y / BH), tx = ((x + (row % 2) * BW / 2) % BW + BW) % BW, ty = y - row * BH;
    const d = Math.min(tx, BW - tx, ty, BH - ty), id = row * 16 + Math.floor((x + (row % 2) * BW / 2) / BW);
    let c = mul([186, 160, 120], 0.92 + 0.12 * hash(id, 1, seed));
    c = mix(c, [64, 56, 48], 0.45 * sstep(-0.2, 0.9, vn(x, y, seed + 1, 60, 40)));
    if (d < 1.2) c = mul(c, 0.78);
    return {c, s: 30, f0: 10, p: 45, h: 0, ao: 1};
  };
}
/** The ash bed: light grey wood ash, a few dark charcoal patches (soft, large: no specks). */
export function ash() {
  return (x, y) => {
    let c = mul([150, 146, 140], 1 + 0.06 * vn(x, y, 7050, 24));
    const cl = cells(x, y, 10, 10, 7051, 0.9), dark = hash(cl.i, cl.j, 7052) < 0.3;
    if (dark) c = mix(c, [58, 54, 50], 0.7 * sstep(2, 9, cl.edge));
    return {c, s: 16, f0: 10, p: 55, h: dark ? 0.6 * sstep(2, 9, cl.edge) : 0, ao: 1};
  };
}
/**
 * The stove's split wood (inside the firebox, u along the split): the red oak bark photo (tools/wood-photo, 2026-10-20;
 * its ridges run along v, so it is turned), a little char.
 */
const FW_BARK = base('firewood_bark_240'), FW_END = base('firewood_end_240');
export function firewoodBark() {
  return (x, y) => {
    let c = FW_BARK(y + 57, x);
    c = mix(c, [34, 30, 28], 0.35 * sstep(0.2, 1, vn(x, y, 7061, 80, 40)));
    return {c, s: 30, f0: 10, p: 40, h: 0, ao: 1};
  };
}
/** The split wood's end grain (disc radius 0.47 of the sprite): the red oak end photo with its bark rim. */
export function firewoodEnd() {
  return (x, y) => ({c: FW_END(x, y), s: 36, f0: 10, p: 40, h: 0, ao: 1});
}
/** The heat shield: plain galvanised sheet, a quiet grey satin with a faint large mottle; dielectric (see the header). */
export function shieldSheet() {
  return (x, y) => ({c: mul([116, 120, 122], 1 + 0.035 * vn(x, y, 7080, 120, 60) + 0.02 * vn(x, y, 7081, 30)), s: 96, f0: 10, p: 0, h: 0, ao: 1});
}
/** The shield's ceramic standoff spools: cream porcelain, glossy. */
export function ceramic() {
  return (x, y) => ({c: mul([222, 216, 202], 1 + 0.012 * vn(x, y, 7090, 40)), s: 200, f0: 10, p: 0, h: 0, ao: 1});
}

// ------------------------------------------------------------------------------------------------- flagstone

/**
 * The flagstone's top (user 2026-10-13: the hearth in flat stone slabs): coursed random rectangular bluestone over a 3 x 3
 * block texture (MacroTileModel's top group, tiles top0..8), so a floor of it repeats every 3 m. Courses 30..60 cm, the
 * stones in a course 30..75 cm long, some cut once more across (two smaller stones); 2.5 cm mortar joints (6 texels), a
 * soft 4-texel arris, flat faces with only a broad, low cleft (the flat-ground rule: no fine relief lines, nothing under
 * 5 texels in the normal map); grey and blue-grey tones, a step apart.
 */
export const FL_COLS = 3, FL_ROWS = 3;
const FW = FL_COLS * N, FH = FL_ROWS * N, FL_SEED = 7100;
const flRng = seed => { let a = seed >>> 0; return () => { a = (a + 0x6D2B79F5) >>> 0; let t = a; t = Math.imul(t ^ (t >>> 15), t | 1); t ^= t + Math.imul(t ^ (t >>> 7), t | 61); return ((t ^ (t >>> 14)) >>> 0) / 4294967296; }; };
function lengths(rnd, a, b, total) {
  const L = []; let s = 0;
  while (s < total) { const l = a + (b - a) * rnd(); L.push(l); s += l; }
  if (L.length > 1 && s - total > L[L.length - 1] / 2) s -= L.pop();
  return L.map(l => l * total / s);
}
/** Periodic value noise over the whole 720 x 720 texture (cells dividing 720), -1..1. */
function vnF(x, y, seed, cx, cy = cx) {
  const nx = Math.round(FW / cx), ny = Math.round(FH / cy), xi = Math.floor(x / cx), yi = Math.floor(y / cy);
  const h = (i, j) => hash(((i % nx) + nx) % nx, ((j % ny) + ny) % ny, seed);
  const sm = t => t * t * (3 - 2 * t), tx = sm(x / cx - xi), ty = sm(y / cy - yi);
  const a = h(xi, yi) + (h(xi + 1, yi) - h(xi, yi)) * tx, b = h(xi, yi + 1) + (h(xi + 1, yi + 1) - h(xi, yi + 1)) * tx;
  return 2 * (a + (b - a) * ty) - 1;
}
const FL = {course: [72, 144], stone: [72, 180], cut: 0.3, joint: 3, arris: 4, mortar: [104, 100, 92], mortarH: -3,
  pal: [[[112, 116, 120], 30], [[120, 119, 114], 25], [[104, 106, 108], 20], [[128, 127, 121], 15], [[96, 98, 101], 10]]};
function flagLayout() {
  const rnd = flRng(FL_SEED), hs = lengths(rnd, FL.course[0], FL.course[1], FH), Y = [0];
  for (const h of hs) Y.push(Y[Y.length - 1] + h);
  const rows = hs.map(() => {
    const ws = lengths(rnd, FL.stone[0], FL.stone[1], FW), X = [rnd() * FW];
    for (const w of ws) X.push(X[X.length - 1] + w);
    return {X, cuts: ws.map(w => (rnd() < FL.cut ? 0.35 + 0.3 * rnd() : null))};
  });
  return {Y, rows};
}
/** The stone under (x, y): its id and distance (texels) to its nearest joint line. */
function flagAt(L, x, y) {
  x = ((x % FW) + FW) % FW; y = ((y % FH) + FH) % FH;
  let b = 0;
  while (y >= L.Y[b + 1]) b++;
  const R = L.rows[b], y0 = L.Y[b], y1 = L.Y[b + 1];
  let xx = x;
  while (xx < R.X[0]) xx += FW;
  while (xx >= R.X[0] + FW) xx -= FW;
  let k = 0;
  while (xx >= R.X[k + 1]) k++;
  let t0 = y0, t1 = y1, half = 0;
  const cut = R.cuts[k];
  if (cut != null) { const ym = y0 + cut * (y1 - y0); if (y < ym) t1 = ym; else { t0 = ym; half = 1; } }
  return {id: (b * 64 + k) * 2 + half, d: Math.min(y - t0, t1 - y, xx - R.X[k], R.X[k + 1] - xx)};
}
export function flagstoneTop() {
  const L = flagLayout(), memo = new Map();
  const stone = id => {
    if (!memo.has(id)) {
      let w = hash(id, 1, FL_SEED) * 100, k = 0;
      while (w > FL.pal[k][1]) { w -= FL.pal[k][1]; k++; }
      memo.set(id, {c: mul(FL.pal[k][0], 0.96 + 0.08 * hash(id, 2, FL_SEED)), set: hash(id, 3, FL_SEED), k: id % 3});
    }
    return memo.get(id);
  };
  return (x, y) => {
    const q = flagAt(L, x, y), st = stone(q.id), g = FL.joint + 0.5 * vnF(x, y, FL_SEED + 1, 40);
    if (q.d < g) return {c: mul(FL.mortar, 1 + 0.04 * vnF(x, y, FL_SEED + 2, 24)), s: 30, f0: 10, p: 50, h: FL.mortarH, ao: 0.8};
    const prof = Math.min((q.d - g) / FL.arris, 1), sm = prof * prof * (3 - 2 * prof);
    const cleft = 0.5 * vnF(x, y, FL_SEED + 10 + st.k, 60) + 0.3 * vnF(x, y, FL_SEED + 20, 120);
    let c = mul(st.c, 1 + 0.035 * vnF(x, y, FL_SEED + 30 + st.k, 40) + 0.03 * cleft);
    c = mix(c, [112, 108, 100], 0.12 * sstep(0.2, 0.9, vnF(x, y, FL_SEED + 40, 80)));   // a worn film
    return {c: mul(c, 0.9 + 0.1 * sm), s: 70 + 10 * vnF(x, y, FL_SEED + 50, 60), f0: 10, p: 18, h: FL.mortarH * (1 - sm) + sm * cleft, ao: 0.85 + 0.15 * sm};
  };
}
/** A macro texture W x H texels cut into its 240 tiles, row-major (row 0 at the top): [[colour, _s, _n] PNGs]. */
function macroTiles(f, cols, rows) {
  const W = cols * N, H = rows * N;
  const col = new Float64Array(W * H * 3), sp = new Float64Array(W * H * 3), Hm = new Float64Array(W * H), AO = new Float64Array(W * H);
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
    let c = [0, 0, 0], s = 0, f0 = 0, p = 0, h = 0, ao = 0;
    for (let sy = 0; sy < SS; sy++) for (let sx = 0; sx < SS; sx++) {
      const r = f(x + (sx + 0.5) / SS, y + (sy + 0.5) / SS);
      c = c.map((v, i) => v + r.c[i]); s += r.s; f0 += r.f0; p += r.p; h += r.h; ao += r.ao;
    }
    const k = SS * SS, o = y * W + x;
    for (let i = 0; i < 3; i++) col[o * 3 + i] = c[i] / k;
    sp[o * 3] = s / k; sp[o * 3 + 1] = f0 / k; sp[o * 3 + 2] = p / k; Hm[o] = h / k; AO[o] = ao / k;
  }
  const at = (i, j) => Hm[((j + H) % H) * W + ((i + W) % W)], tiles = [];
  for (let ty = 0; ty < rows; ty++) for (let tx = 0; tx < cols; tx++) {
    const c = Buffer.alloc(N * N * 4), s = Buffer.alloc(N * N * 4), n = Buffer.alloc(N * N * 4);
    for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
      const X = tx * N + x, Y = ty * N + y, o = Y * W + X, d = (y * N + x) * 4;
      for (let i = 0; i < 3; i++) c[d + i] = clamp(Math.round(col[o * 3 + i]), 0, 255);
      c[d + 3] = 255;
      s[d] = clamp(Math.round(sp[o * 3]), 0, 255); s[d + 1] = clamp(Math.round(sp[o * 3 + 1]), 0, 255); s[d + 2] = clamp(Math.round(sp[o * 3 + 2]), 0, 64); s[d + 3] = 255;
      const hx = (at(X + 1, Y) - at(X - 1, Y)) / (2 * MM), hy = (at(X, Y + 1) - at(X, Y - 1)) / (2 * MM);
      let nx = -hx, ny = -hy; const l = Math.hypot(nx, ny, 1); nx /= l; ny /= l;
      n[d] = Math.round((nx * 0.5 + 0.5) * 255); n[d + 1] = Math.round((ny * 0.5 + 0.5) * 255);
      n[d + 2] = clamp(Math.round(255 * AO[o]), 0, 255); n[d + 3] = clamp(Math.round(255 * (1 + Hm[o] / 250)), 0, 255);
    }
    tiles.push([png(c, N, N), png(s, N, N), png(n, N, N)]);
  }
  return tiles;
}
/** The flagstone top's tiles: flagstone_top_0 .. 8. */
export function flagstoneTopTiles() { return macroTiles(flagstoneTop(), FL_COLS, FL_ROWS); }

/** Every stove texture: name -> [maker, height?]. Paths: textures/block/stove/<name>.png (+ _s, _n). */
export const STOVE_TEXTURES = {
  cast_iron: [stoveIron, false], cast_iron_edge: [stoveIronEdge, false], soot: [soot, false], nickel: [nickel, false],
  firebrick: [firebrick, false], ash: [ash, false], firewood_bark: [firewoodBark, false], firewood_end: [firewoodEnd, false],
  heat_shield: [shieldSheet, false], ceramic: [ceramic, false],
};
