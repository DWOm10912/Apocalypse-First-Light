// Highway R4 sample V1 (docs/worldgen/highway_r4_sample_v1.md, C/D): pavement textures of the mesh, development-only
// (src/dev/highway_mesh_resources, excluded from the jar). Same density and recipe as the A1 asphalt block
// (tools/build-ground-materials-v1.mjs: 240 texels per metre, packed 2 cm aggregate, LabPBR), but one tile covers 4 x 4 m,
// so the low-frequency tone wanders over metres instead of repeating every block.
//   r4_asphalt        960 x 960   4 m x 4 m, periodic both ways (the mesh maps 4 m cells measured from the median edge)
//   r4_paint_white    64 x 960    0.15 m across x 4 m along: thermoplastic over the same aggregate, worn on stone tops,
//   r4_paint_yellow   64 x 960    a ragged 1..3 mm margin where the asphalt shows (the solid shader has no alpha test)
// Widths are multiples of 64 so the block atlas keeps its mip levels.
//   node tools/highway-r4/build-r4-textures.mjs           -> writes the textures
//   node tools/highway-r4/build-r4-textures.mjs --check   -> verifies they are up to date
// _n: DirectX (red right, green down) from the height slopes, B ambient occlusion, A height (1 - depth / 250 mm).
// _s: R smoothness, G F0 (dielectric), B porosity (0..64, wets in rain), A 255 (no emission).
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {png} from '../cube-slab-mesh-lib.mjs';

const SS = 3, PERIOD_MM = 4000, CELL_MM = 1000 / 48;   // aggregate cells: 5 texels at 240 per metre, as on the A1 block
const hash = (a, b, c) => { let h = Math.imul(a | 0, 374761393) ^ Math.imul(b | 0, 668265263) ^ Math.imul(c | 0, 2147483647); h = Math.imul(h ^ (h >>> 13), 1274126177); return ((h ^ (h >>> 16)) >>> 0) / 4294967295; };
const smooth = t => t * t * (3 - 2 * t), clamp = (v, a, b) => Math.max(a, Math.min(b, v));
const mul = (c, t) => c.map(v => v * t), mix = (a, b, t) => a.map((v, i) => v + (b[i] - v) * t);
/** Value noise in millimetres, periodic over PERIOD_MM on both axes; cell sizes divide PERIOD_MM. -1..1. */
function vn(x, y, seed, cell) {
  const n = Math.round(PERIOD_MM / cell), xi = Math.floor(x / cell), yi = Math.floor(y / cell);
  const h = (i, j) => hash(((i % n) + n) % n, ((j % n) + n) % n, seed);
  const tx = smooth(x / cell - xi), ty = smooth(y / cell - yi);
  const a = h(xi, yi) + (h(xi + 1, yi) - h(xi, yi)) * tx, b = h(xi, yi + 1) + (h(xi + 1, yi + 1) - h(xi, yi + 1)) * tx;
  return 2 * (a + (b - a) * ty) - 1;
}

/** The aggregate at (x, y) mm: nearest cell, distance to its edge (mm), exposure, tone, height. */
const C = Math.round(PERIOD_MM / CELL_MM);
function aggregate(x, y) {
  const cell = (i, j) => {
    const ii = ((i % C) + C) % C, jj = ((j % C) + C) % C;
    return {x: (i + 0.1 + 0.8 * hash(ii, jj, 701)) * CELL_MM, y: (j + 0.1 + 0.8 * hash(ii, jj, 702)) * CELL_MM,
      exposed: hash(ii, jj, 703) < 0.7, tone: 0.86 + 0.28 * hash(ii, jj, 704), up: 0.35 + 0.6 * hash(ii, jj, 705)};
  };
  const ci = Math.floor(x / CELL_MM), cj = Math.floor(y / CELL_MM);
  let d1 = Infinity, d2 = Infinity, near = null;
  for (let dj = -1; dj <= 1; dj++) for (let di = -1; di <= 1; di++) {
    const f = cell(ci + di, cj + dj), d = Math.hypot(x - f.x, y - f.y);
    if (d < d1) { d2 = d1; d1 = d; near = f; } else if (d < d2) d2 = d;
  }
  return {near, border: (d2 - d1) / 2};
}

/** Large-scale tone of the carriageway: a few per cent over 0.2 .. 4 m (repaired patches, binder age), periodic over 4 m. */
const tone = (x, y) => 1 + 0.035 * vn(x, y, 24, 2000) + 0.025 * vn(x, y, 25, 1000) + 0.03 * vn(x, y, 21, 200) + 0.015 * vn(x, y, 22, 1000 / 12);

const BINDER = [54, 54, 56], STONE = [74, 73, 70], EDGE_MM = 1000 / 240 * 0.45, RISE_MM = 1000 / 240 * 1.6;
function asphalt(x, y) {
  const {near, border} = aggregate(x, y), t = tone(x, y);
  if (!near.exposed || border < EDGE_MM) return {c: mul(BINDER, t), s: 82, p: 58, h: near.exposed ? -0.3 : -0.1, ao: border < EDGE_MM ? 0.85 : 1};
  const k = Math.min(1, (border - EDGE_MM) / RISE_MM);
  return {c: mul(mix(BINDER, mul(STONE, near.tone), 0.45 + 0.55 * k), t), s: 58, p: 42, h: near.up * Math.sqrt(k), ao: 1};
}

/** Thermoplastic line paint 150 mm wide (x 0..150 mm), lying on the asphalt: thin on the stone tops, pooled in the crevices. */
function paint(colour) {
  return (x, y) => {
    const a = asphalt(x, y);
    // ragged margin: the paint edge wanders 1..3 mm in from the geometry edge
    const m0 = 2 + 1 * vn(y, 0, 41, 1000 / 24) + 0.5 * vn(y, 0, 42, 1000 / 96), m1 = 2 + 1 * vn(y, 0, 43, 1000 / 24) + 0.5 * vn(y, 0, 44, 1000 / 96);
    if (x < m0 || x > 150 - m1) return a;
    const {near, border} = aggregate(x, y);
    const top = near.exposed && border > EDGE_MM ? Math.min(1, (border - EDGE_MM) / RISE_MM) * near.up : 0;
    const wear = clamp(0.5 + 0.5 * vn(x, y, 45, 1000 / 6) + 0.25 * vn(x, y, 46, 200), 0, 1);   // traffic wear in patches
    const show = clamp(0.35 * top * (0.4 + wear), 0, 0.45);                                       // stone through the paint
    const t = 1 + 0.03 * vn(x, y, 47, 500) + 0.015 * vn(x, y, 48, 1000 / 24);
    const c = mix(mul(colour, t * (border < EDGE_MM ? 0.95 : 1)), a.c, show);
    return {c, s: 95 - 30 * show, p: 18, h: 1.2 + 0.7 * a.h, ao: border < EDGE_MM ? 0.9 : 1};
  };
}

/** Rasterise over W x H texels covering wMm x hMm; x wraps when wrapX, y always wraps (the 4 m period along the road). */
function render(f, W, H, wMm, hMm, wrapX) {
  const sx = wMm / W, sy = hMm / H;
  const col = Buffer.alloc(W * H * 4), spec = Buffer.alloc(W * H * 4), nrm = Buffer.alloc(W * H * 4), Hm = new Float64Array(W * H);
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
    let c = [0, 0, 0], s = 0, p = 0, h = 0, ao = 0;
    for (let j = 0; j < SS; j++) for (let i = 0; i < SS; i++) {
      const r = f((x + (i + 0.5) / SS) * sx, (y + (j + 0.5) / SS) * sy);
      c = c.map((v, k) => v + r.c[k]); s += r.s; p += r.p; h += r.h; ao += r.ao;
    }
    const k = SS * SS, o = (y * W + x) * 4;
    c.forEach((v, q) => { col[o + q] = clamp(Math.round(v / k), 0, 255); }); col[o + 3] = 255;
    spec[o] = clamp(Math.round(s / k), 0, 255); spec[o + 1] = 14; spec[o + 2] = clamp(Math.round(p / k), 0, 64); spec[o + 3] = 255;
    Hm[y * W + x] = h / k; nrm[o + 2] = clamp(Math.round(255 * ao / k), 0, 255);
  }
  const ix = i => i < 0 ? (wrapX ? i + W : 0) : i >= W ? (wrapX ? i - W : W - 1) : i, iy = j => (j + H) % H;
  const at = (i, j) => Hm[iy(j) * W + ix(i)];
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
    const hx = (at(x + 1, y) - at(x - 1, y)) / (2 * sx), hy = (at(x, y + 1) - at(x, y - 1)) / (2 * sy);
    let nx = -hx, ny = -hy; const l = Math.hypot(nx, ny, 1); nx /= l; ny /= l;
    const o = (y * W + x) * 4;
    nrm[o] = Math.round((nx * 0.5 + 0.5) * 255); nrm[o + 1] = Math.round((ny * 0.5 + 0.5) * 255);
    nrm[o + 3] = clamp(Math.round(255 * (1 + Hm[y * W + x] / 250)), 0, 255);
  }
  return [png(col, W, H), png(spec, W, H), png(nrm, W, H)];
}

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const OUT = path.join(ROOT, 'src/dev/highway_mesh_resources/assets/afl_highway_demo/textures/block');

export function outputs() {
  const sets = [
    ['r4_asphalt', render(asphalt, 960, 960, PERIOD_MM, PERIOD_MM, true)],
    ['r4_paint_white', render(paint([224, 223, 214]), 64, 960, 150, PERIOD_MM, false)],
    ['r4_paint_yellow', render(paint([216, 168, 44]), 64, 960, 150, PERIOD_MM, false)],
  ];
  const out = [];
  for (const [name, maps] of sets) ['', '_s', '_n'].forEach((k, i) => out.push([path.join(OUT, name + k + '.png'), maps[i]]));
  return out;
}

const isMain = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  const list = outputs();
  if (process.argv.includes('--check')) {
    for (const [file, data] of list) { const cur = fs.existsSync(file) ? fs.readFileSync(file) : null; if (!cur || !cur.equals(data)) throw new Error('stale ' + path.relative(ROOT, file)); }
    console.log('CHECK OK');
  } else {
    fs.mkdirSync(OUT, {recursive: true});
    for (const [file, data] of list) fs.writeFileSync(file, data);
    console.log('wrote ' + list.length + ' files to ' + path.relative(ROOT, OUT));
  }
}
