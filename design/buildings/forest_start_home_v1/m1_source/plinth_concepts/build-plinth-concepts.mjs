// Forest start home B, M1.2 concept round (user 2026-10-12): two candidate plinth stones and porch supports, after the
// M1.1 v3 fieldstone was rejected as river-cobble cladding ("too round, too smooth, a landscape wall, not the bearing
// foundation of an old log house"). Design use only: writes textures into ./A and ./B for cabin_preview.html?concept=A|B
// and swatches for the concept sheets; nothing goes into the game assets until the user picks one.
//   A  coursed ledgestone: flat split stones (8-22 cm thick, 30-60 cm long) in rough courses, thin dark joints, like the
//      chimneys and porch piers of the Cades Cove cabins (John Oliver Cabin). Porch: plain square concrete piers, 30 cm.
//   B  roughly squared rubble: blocky split stones (20-60 cm tall, 35-70 cm long) in broken courses, a few through two
//      courses, earthy mortar close to the stone's value, like the granite foundation of the Grogan House (HABS GA-53).
//      Porch: short oak posts on squared stone blocks (34 cm, 22 cm high).
// Both: low-saturation grey-brown, colour steps small, relief from the joints and the arrises, no fine noise.
//   "/c/Program Files/nodejs/node.exe" design/buildings/forest_start_home_v1/m1_source/plinth_concepts/build-plinth-concepts.mjs
import fs from 'node:fs';
import path from 'node:path';
import {pathToFileURL, fileURLToPath} from 'node:url';
const HERE = path.dirname(fileURLToPath(import.meta.url)), ROOT = path.resolve(HERE, '../../../../..');
const {N, hash, clamp, sstep, mul, mix} = await import(pathToFileURL(path.join(ROOT, 'tools/mesh-trees/hd-art.mjs')).href);
const {png} = await import(pathToFileURL(path.join(ROOT, 'tools/cube-slab-mesh-lib.mjs')).href);

const COLS = 3, ROWS = 2, FW = COLS * N, FH = ROWS * N, SS = 3, MM = 1000 / N;
const rng = seed => { let a = seed >>> 0; return () => { a = (a + 0x6D2B79F5) >>> 0; let t = a; t = Math.imul(t ^ (t >>> 15), t | 1); t ^= t + Math.imul(t ^ (t >>> 7), t | 61); return ((t ^ (t >>> 14)) >>> 0) / 4294967296; }; };
/** Periodic value noise over the macro texture (cells cx x cy texels dividing 720 x 480), -1..1; lin: angular (no smoothstep). */
function vnF(x, y, seed, cx, cy = cx, lin = false) {
  const nx = Math.round(FW / cx), ny = Math.round(FH / cy), xi = Math.floor(x / cx), yi = Math.floor(y / cy);
  const h = (i, j) => hash(((i % nx) + nx) % nx, ((j % ny) + ny) % ny, seed);
  const sm = lin ? t => t : t => t * t * (3 - 2 * t), tx = sm(x / cx - xi), ty = sm(y / cy - yi);
  const a = h(xi, yi) + (h(xi + 1, yi) - h(xi, yi)) * tx, b = h(xi, yi + 1) + (h(xi + 1, yi + 1) - h(xi, yi + 1)) * tx;
  return 2 * (a + (b - a) * ty) - 1;
}
/** Random lengths in [a, b] summing exactly to total (the last one dropped if it overshoots by more than half). */
function lengths(rnd, a, b, total) {
  const L = []; let s = 0;
  while (s < total) { const l = a + (b - a) * rnd(); L.push(l); s += l; }
  if (L.length > 1 && s - total > L[L.length - 1] / 2) { s -= L.pop(); }
  return L.map(l => l * total / s);
}
/** A periodic piecewise-linear wobble along x: knots spaced [a, b], offsets +-amp. */
function wobble(rnd, a, b, amp) {
  const xs = [0]; for (const l of lengths(rnd, a, b, FW)) xs.push(xs[xs.length - 1] + l);
  const ys = xs.map(() => (2 * rnd() - 1) * amp); ys[ys.length - 1] = ys[0];
  return x => { x = ((x % FW) + FW) % FW; let i = 0; while (xs[i + 1] < x) i++; const t = (x - xs[i]) / (xs[i + 1] - xs[i]); return ys[i] + (ys[i + 1] - ys[i]) * t; };
}

/**
 * Stone layout: horizontal bands (with straight-segment wobble), each cut into columns by slanted joints, each column
 * stacked 1..3 stones high (split lines with a small slope), each stone row in a column sometimes cut once more.
 */
function layout(P) {
  const rnd = rng(P.seed);
  const hs = lengths(rnd, P.band[0], P.band[1], FH), Y = [0];
  for (const h of hs) Y.push(Y[Y.length - 1] + h);
  const nb = hs.length, wob = Array.from({length: nb}, () => wobble(rnd, P.knot[0], P.knot[1], P.wobble));
  const bnd = (b, x) => Y[b] + wob[b % nb](x);
  const bands = [];
  for (let b = 0; b < nb; b++) {
    const yc = (Y[b] + Y[b + 1]) / 2, ws = lengths(rnd, P.cols[0], P.cols[1], FW), phase = rnd() * FW, X = [phase];
    for (const w of ws) X.push(X[X.length - 1] + w);
    const S = ws.map(() => (2 * rnd() - 1) * P.slant); S.push(S[0]);
    const cols = ws.map((w, k) => {
      let n = 1; const r = rnd(), tall = hs[b] >= P.splitMin;
      if (tall) { let acc = 0; for (let i = 0; i < P.split.length; i++) { acc += P.split[i]; if (r < acc) { n = i + 1; break; } } }
      const ts = n === 1 ? [] : n === 2 ? [0.36 + 0.28 * rnd()] : [0.26 + 0.12 * rnd(), 0.6 + 0.14 * rnd()];
      const rows = [];
      for (let j = 0; j < n; j++) rows.push(rnd() < P.subdiv && w > P.cols[0] * 1.15 ? {m: 0.32 + 0.36 * rnd(), s: (2 * rnd() - 1) * P.slant} : null);
      return {ts, ss: ts.map(() => (2 * rnd() - 1) * P.splitSlope), rows};
    });
    bands.push({yc, X, S, cols});
  }
  return {nb, bnd, bands, Y};
}
/** The stone under (x, y): its id, the distances to its four edges (texels), its relative position (-1..1). */
function query(L, x, y) {
  x = ((x % FW) + FW) % FW; y = ((y % FH) + FH) % FH;
  if (y < L.bnd(0, x)) y += FH; else if (y >= FH + L.bnd(0, x)) y -= FH;   // the band across the wrap
  let b = 0;
  while (b < L.nb - 1 && y >= L.bnd(b + 1, x)) b++;
  const top = L.bnd(b, x), bot = b + 1 === L.nb ? FH + L.bnd(0, x) : L.bnd(b + 1, x), B = L.bands[b];
  const jx = k => B.X[k] + B.S[k] * (y - B.yc);
  let xx = x;
  while (xx < jx(0)) xx += FW;
  while (xx >= jx(0) + FW) xx -= FW;
  let k = 0;
  while (xx >= jx(k + 1)) k++;
  const C = B.cols[k], x0 = jx(k), x1 = jx(k + 1), xc = (x0 + x1) / 2;
  let t0 = top, t1 = bot, j = 0;
  const sy = i => top + C.ts[i] * (bot - top) + C.ss[i] * (xx - xc);
  for (let i = 0; i < C.ts.length; i++) { const s = sy(i); if (y >= s) { t0 = s; j = i + 1; } }
  if (j < C.ts.length) t1 = sy(j);
  let l = x0, r = x1, side = 0;
  const R = C.rows[j];
  if (R) { const xm = x0 + R.m * (x1 - x0) + R.s * (y - (t0 + t1) / 2); if (xx < xm) r = xm; else { l = xm; side = 1; } }
  const cs = s => 1 / Math.sqrt(1 + s * s);
  const d = [y - t0, t1 - y, (xx - l) * cs(B.S[k]), (r - xx) * cs(B.S[k + 1])];
  return {id: ((b * 64 + k) * 4 + j) * 2 + side, d, u: (2 * xx - l - r) / Math.max(r - l, 1), v: (2 * y - t0 - t1) / Math.max(t1 - t0, 1), w: r - l, h: t1 - t0};
}

const CONCEPTS = {
  A: {
    seed: 4100, band: [26, 52], cols: [70, 150], split: [0.62, 0.33, 0.05], splitMin: 40, subdiv: 0.35, knot: [40, 110], wobble: 4,
    slant: 0.35, splitSlope: 0.06, round: 1, chip: [0.5, 0.8], chipScale: [10, 30], joint: [1.2, 1.4], arris: 2.5, mix: 0.25, clip: [0.32, 8, 16], facet: [0, 0], vary: 0.12,
    pal: [[[116, 109, 98], 30], [[104, 99, 91], 25], [[124, 113, 98], 15], [[110, 107, 101], 20], [[94, 90, 84], 10]],
    mortar: [86, 81, 73], mortarH: -15, mortarAo: 0.6,
  },
  B: {
    seed: 4200, band: [96, 150], cols: [85, 165], split: [0.25, 0.6, 0.15], splitMin: 0, subdiv: 0.25, knot: [60, 140], wobble: 5,
    slant: 0.14, splitSlope: 0.05, round: 1.5, chip: [0.5, 0.9], chipScale: [16, 40], joint: [2.0, 1.6], arris: 3.5, mix: 0.4, clip: [0.4, 8, 18], facet: [3, 4], vary: 0.08,
    pal: [[[118, 116, 110], 35], [[120, 113, 102], 25], [[105, 100, 93], 20], [[129, 126, 119], 10], [[95, 92, 87], 10]],
    mortar: [97, 92, 83], mortarH: -11, mortarAo: 0.65,
  },
};

function stoneTexture(name) {
  const P = CONCEPTS[name], L = layout(P), seed = P.seed, memo = new Map();
  const stone = id => {
    if (!memo.has(id)) {
      const r = (i) => hash(id, i, seed + 7);
      let w = r(0) * 100, k = 0;
      while (w > P.pal[k][1]) { w -= P.pal[k][1]; k++; }
      const base = P.pal[k][0], g = (base[0] + base[1] + base[2]) / 3;
      // corners knocked off (TL, TR, BL, BR: the cut's size, 0 = square), and for B the split face's 3 facets
      const clip = [5, 6, 7, 8].map(i => r(i) < P.clip[0] ? [P.clip[1] + (P.clip[2] - P.clip[1]) * r(i + 10), 0.5 + r(i + 50), 0.5 + r(i + 60)] : null);   // size, slope
      const facets = [0, 1, 2].map(i => { const a = r(20 + i) * Math.PI * 2; return [Math.cos(a), Math.sin(a), P.facet[0] + P.facet[1] * r(30 + i), 0.6 * r(40 + i)]; });
      memo.set(id, {c: mul(mix(base, [g, g, g], P.mix), 1 - P.vary / 2 + P.vary * r(1)), set: name === 'A' ? -3 + 4.5 * r(2) : -1.5 + 2.5 * r(2),
        tu: (2 * r(3) - 1) * 2, tv: (2 * r(4) - 1) * 1.5, k: id % 3, clip, facets});
    }
    return memo.get(id);
  };
  return (x, y) => {
    const q = query(L, x, y), st = stone(q.id), [dt, db, dl, dr] = q.d, all = [...q.d], kr = P.round;
    const cmax = 0.35 * Math.min(q.w, q.h);   // a small stone keeps its corners (else it reads as a pebble)
    [[dt, dl], [dt, dr], [db, dl], [db, dr]].forEach(([a, b], i) => { const c = st.clip[i]; if (c) all.push((a * c[1] + b * c[2] - Math.min(c[0], cmax) * (c[1] + c[2]) / 2) / Math.hypot(c[1], c[2])); });
    const d = all.sort((a, b) => a - b);
    const hk = Math.max(kr - Math.abs(d[0] - d[1]), 0) / kr;
    let e = Math.min(d[0], d[1]) - hk * hk * kr / 4;
    e += P.chip[0] * vnF(x, y, seed + 1, P.chipScale[0], P.chipScale[0], true) + P.chip[1] * vnF(x, y, seed + 2, P.chipScale[1], P.chipScale[1], true);   // a few straight chips, not a torn edge
    const g = P.joint[0] + P.joint[1] * sstep(-0.5, 0.8, vnF(x, y, seed + 3, 48));
    const big = 1 + 0.03 * vnF(x, y, seed + 4, 240, 160);   // a metre-scale drift so no two blocks match
    if (e < g) {
      const into = g - e, hm = P.mortarH + 0.6 * vnF(x, y, seed + 9, 20);   // flat, recessed: no lip that would outline the stone
      return {c: mul(P.mortar, big * (1 + 0.04 * vnF(x, y, seed + 5, 24)) * (0.88 + 0.12 * sstep(0, 4, into))), s: 26, f0: 10, p: 50, h: hm, ao: P.mortarAo};
    }
    const ar = P.arris * (0.55 + 0.9 * sstep(-0.7, 0.7, vnF(x, y, seed + 8, 30)));   // the arris sharp in places, spalled wider in others
    const t = e - g, prof = Math.min(t / ar, 1);   // a narrow straight bevel: a crisp split arris, no rounded shoulder
    let h;
    if (name === 'A') h = -14 + 13 * prof + (st.set + st.tu * q.u + st.tv * q.v + 0.6 * vnF(x, y, seed + 6 + st.k, 30)) * prof;   // split face, a little proud or set back
    else {   // split face: the lowest of three tilted planes (a few flat facets meeting in low ridges), no pillow
      let f = 9;
      for (const [cx, cy, s, o] of st.facets) f = Math.min(f, 2.5 + o - s * (cx * q.u + cy * q.v));
      h = -14 + 12 * prof + (f + st.set + 0.5 * vnF(x, y, seed + 6 + st.k, 36)) * prof;
    }
    h = clamp(h, -16, 4);
    let c = mul(st.c, big * (1 + 0.045 * vnF(x, y, seed + 10 + st.k, 28) + 0.02 * vnF(x, y, seed + 20, 10)));
    if (name === 'A') c = mul(c, 1 + 0.025 * vnF(x, y, seed + 30 + st.k, 60, 6));   // faint bedding along the stone
    c = mix(c, [112, 108, 100], 0.15 * sstep(0.2, 0.9, vnF(x, y, seed + 40, 48)));   // weathered film
    c = mul(c, 0.9 + 0.1 * prof);
    return {c, s: 52 + 8 * vnF(x, y, seed + 50, 40), f0: 10, p: 20, h, ao: 0.66 + 0.34 * prof};
  };
}

/** Supersamples a macro function over w x h texels into colour / spec / height / ao arrays. */
function raster(f, W, H) {
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
  return {W, H, col, sp, Hm, AO};
}
/** Cuts a raster into tiles of N: [colour, _s, _n (LabPBR), _pn (preview tangent normal, z = sqrt)] PNG buffers. */
function tiles(R, cols, rows) {
  const {W, H, col, sp, Hm, AO} = R, at = (i, j) => Hm[((j + H) % H) * W + ((i + W) % W)], out = [];
  for (let ty = 0; ty < rows; ty++) for (let tx = 0; tx < cols; tx++) {
    const c = Buffer.alloc(N * N * 4), s = Buffer.alloc(N * N * 4), n = Buffer.alloc(N * N * 4), pn = Buffer.alloc(N * N * 4);
    for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
      const X = tx * N + x, Y = ty * N + y, o = Y * W + X, d = (y * N + x) * 4;
      for (let i = 0; i < 3; i++) c[d + i] = clamp(Math.round(col[o * 3 + i]), 0, 255);
      c[d + 3] = 255;
      s[d] = clamp(Math.round(sp[o * 3]), 0, 255); s[d + 1] = clamp(Math.round(sp[o * 3 + 1]), 0, 255); s[d + 2] = clamp(Math.round(sp[o * 3 + 2]), 0, 64); s[d + 3] = 255;
      const hx = (at(X + 1, Y) - at(X - 1, Y)) / (2 * MM), hy = (at(X, Y + 1) - at(X, Y - 1)) / (2 * MM);
      let nx = -hx, ny = -hy; const l = Math.hypot(nx, ny, 1); nx /= l; ny /= l;
      n[d] = pn[d] = Math.round((nx * 0.5 + 0.5) * 255); n[d + 1] = pn[d + 1] = Math.round((ny * 0.5 + 0.5) * 255);
      n[d + 2] = clamp(Math.round(255 * AO[o]), 0, 255); n[d + 3] = clamp(Math.round(255 * (1 + Hm[o] / 250)), 0, 255);
      pn[d + 2] = Math.round((Math.sqrt(Math.max(0, 1 - nx * nx - ny * ny)) * 0.5 + 0.5) * 255); pn[d + 3] = 255;
    }
    out.push([png(c, N, N), png(s, N, N), png(n, N, N), png(pn, N, N)]);
  }
  return out;
}
/** Swatch: the macro texture flat (top) and lit by its normals from the upper left (bottom), with 1 m ticks. */
function swatch(R) {
  const {W, H, col, Hm, AO} = R, at = (i, j) => Hm[((j + H) % H) * W + ((i + W) % W)], SH = H * 2 + 8, out = Buffer.alloc(W * SH * 4, 255);
  const Lv = (() => { const v = [-0.5, -0.55, 0.67]; const l = Math.hypot(...v); return v.map(c => c / l); })();
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
    const o = y * W + x, hx = (at(x + 1, y) - at(x - 1, y)) / (2 * MM), hy = (at(x, y + 1) - at(x, y - 1)) / (2 * MM);
    let n = [-hx, -hy, 1]; const l = Math.hypot(...n); n = n.map(c => c / l);
    const shade = (0.35 + 0.75 * Math.max(0, n[0] * Lv[0] + n[1] * Lv[1] + n[2] * Lv[2])) * (0.55 + 0.45 * AO[o]);
    for (let i = 0; i < 3; i++) {
      out[(y * W + x) * 4 + i] = clamp(Math.round(col[o * 3 + i]), 0, 255);
      out[((y + H + 8) * W + x) * 4 + i] = clamp(Math.round(col[o * 3 + i] * shade), 0, 255);
    }
    out[(y * W + x) * 4 + 3] = out[((y + H + 8) * W + x) * 4 + 3] = 255;
  }
  return png(out, W, SH);
}

// ------------------------------------------------------------------ the porch supports' own textures (one block each)
const vn1 = (x, y, seed, c) => {   // tiling 240 value noise
  const n = Math.round(N / c), xi = Math.floor(x / c), yi = Math.floor(y / c), h = (i, j) => hash(((i % n) + n) % n, ((j % n) + n) % n, seed);
  const sm = t => t * t * (3 - 2 * t), tx = sm(x / c - xi), ty = sm(y / c - yi);
  const a = h(xi, yi) + (h(xi + 1, yi) - h(xi, yi)) * tx, b = h(xi, yi + 1) + (h(xi + 1, yi + 1) - h(xi, yi + 1)) * tx;
  return 2 * (a + (b - a) * ty) - 1;
};
/** A: old cast concrete, board-formed in 15 cm lifts (faint), a few small blow holes, soil-darkened in its lowest 15 cm. v = 1 - y. */
function concrete(x, y) {
  const lift = Math.floor(y / 36), band = 1 + 0.018 * (2 * hash(lift, 0, 4300) - 1);
  const low = sstep(N * 0.8, N, y);   // the pier's foot (y = 0 at v = 1)
  let c = mul([117, 115, 109], band * (1 + 0.04 * vn1(x, y, 4301, 60) + 0.015 * vn1(x, y, 4302, 12)));
  c = mix(c, [108, 105, 98], 0.3 * sstep(0.1, 0.9, vn1(x, y, 4308, 80)));   // old weathering, patchy
  c = mix(c, [96, 91, 82], 0.5 * low);
  let h = 0, ao = 1;
  const cx = Math.floor(x / 20), cy = Math.floor(y / 20);
  if (hash(cx, cy, 4303) < 0.12) {
    const px = cx * 20 + 4 + 12 * hash(cx, cy, 4304), py = cy * 20 + 4 + 12 * hash(cx, cy, 4305), r = 1.2 + 1.2 * hash(cx, cy, 4306), dd = Math.hypot(x - px, y - py);
    if (dd < r) { c = mul(c, 0.82); h = -0.8; ao = 0.7; }
  }
  return {c, s: 70 + 6 * vn1(x, y, 4307, 40), f0: 10, p: 36, h, ao};
}
/** B: one squared stone block (the footing): grey granite, mottled, a little earth on its lower part. */
function footing(x, y) {
  const low = sstep(N * 0.85, N, y);
  let c = mul(mix([118, 116, 110], [116, 116, 116], 0.3), 1 + 0.05 * vn1(x, y, 4401, 30) + 0.02 * vn1(x, y, 4402, 8));
  c = mix(c, [112, 108, 100], 0.15 * sstep(0.2, 0.9, vn1(x, y, 4403, 48)));
  c = mix(c, [100, 94, 84], 0.4 * low);
  return {c, s: 56, f0: 10, p: 20, h: 0.8 * vn1(x, y, 4404, 40), ao: 1};
}

const write = (dir, name, bufs) => { fs.mkdirSync(dir, {recursive: true}); ['', '_s', '_n', '_pn'].forEach((suf, i) => fs.writeFileSync(path.join(dir, name + suf + '.png'), bufs[i])); };
for (const name of Object.keys(CONCEPTS)) {
  const t0 = Date.now(), R = raster(stoneTexture(name), FW, FH), dir = path.join(HERE, name);
  tiles(R, COLS, ROWS).forEach((bufs, t) => write(dir, 'fieldstone_' + t, bufs));
  fs.writeFileSync(path.join(HERE, 'swatch_' + name + '.png'), swatch(R));
  console.log(name, 'stone', ((Date.now() - t0) / 1000).toFixed(1) + ' s');
}
write(path.join(HERE, 'A'), 'pier_concrete', tiles(raster(concrete, N, N), 1, 1)[0]);
write(path.join(HERE, 'B'), 'pier_stone', tiles(raster(footing, N, N), 1, 1)[0]);
console.log('wrote', HERE);
