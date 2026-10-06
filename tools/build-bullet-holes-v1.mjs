// Bullet hole decals (client/BulletHoles, 2026-10-05, docs/native_guns/native_bullet_holes_v1.md): one sheet of 3 x 3
// holes, 32 x 32 each, drawn untinted over the hit face (about 0.1 block across; a real 9 mm hole would be invisible).
// Rows: steel, stone (concrete, brick, earth and anything else), wood. Columns: three variants.
// - steel: a black hole with a dark lip of metal pushed in, a ring of bare bright steel where the paint flaked off
//   (ragged, by noise), a faint scuff round it.
// - stone: a small dark hole in a darker chipped crater, a pale dust halo fading out, a few short dark cracks.
// - wood: a dark hole, torn pale fibres stretched along the grain (u, horizontal), a darker bruise round them.
// LabPBR _s: bare steel is metal (F0 230, smoothness 150); the rest dielectric and rough; never emissive (alpha 255).
//   node tools/build-bullet-holes-v1.mjs [--check]
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {png} from './cube-slab-mesh-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const OUT = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight/textures/effect');
const N = 32, COLS = 3, ROWS = 3, W = N * COLS, H = N * ROWS;

function rng(seed) { let s = seed >>> 0; return () => { s = Math.imul(s ^ (s >>> 15), 2246822519) + 0x9e3779b9 >>> 0; s ^= s >>> 13; return (s >>> 0) / 4294967296; }; }
const smooth = (a, b, x) => { const t = Math.min(1, Math.max(0, (x - a) / (b - a))); return t * t * (3 - 2 * t); };
// angular noise: a ragged radius round the hole (k lobes of random size, smoothly joined)
function ragged(seed, k) {
  const r = rng(seed), g = Array.from({length: k}, () => r() * 2 - 1);
  return a => { const u = (a / (2 * Math.PI) + 1) % 1 * k, i = Math.floor(u), f = smooth(0, 1, u - i); return g[i % k] * (1 - f) + g[(i + 1) % k] * f; };
}
function grain(seed) {   // value noise on a 6 x 6 lattice (fine mottling)
  const r = rng(seed), g = Array.from({length: 49}, r);
  return (x, y) => { const u = x / N * 6, v = y / N * 6, i = Math.min(5, Math.floor(u)), j = Math.min(5, Math.floor(v)), fu = smooth(0, 1, u - i), fv = smooth(0, 1, v - j);
    const a = g[j * 7 + i], b = g[j * 7 + i + 1], c = g[(j + 1) * 7 + i], d = g[(j + 1) * 7 + i + 1];
    return (a + (b - a) * fu) * (1 - fv) + (c + (d - c) * fu) * fv; };
}

// a layer: colour [r,g,b], alpha 0..1; composited over what is below (straight alpha)
function over(px, col, a) {
  const oa = px[3] + a * (1 - px[3]);
  if (oa <= 0) return;
  for (let c = 0; c < 3; c++) px[c] = (col[c] * a + px[c] * px[3] * (1 - a)) / oa;
  px[3] = oa;
}

// one hole: fn(x, y) -> {layers: [[colour, alpha], ...] bottom first, metal: 0..1}
function cell(fn) {
  const base = new Float64Array(N * N * 4), metal = new Float64Array(N * N);
  for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
    const acc = [0, 0, 0, 0];
    let m = 0;
    for (let sy = 0; sy < 3; sy++) for (let sx = 0; sx < 3; sx++) {   // 3 x 3 supersampling
      const px = [0, 0, 0, 0], s = fn(x + (sx + 0.5) / 3 - N / 2, y + (sy + 0.5) / 3 - N / 2, x, y);
      for (const [col, a] of s.layers) if (a > 0) over(px, col, Math.min(1, a));
      for (let c = 0; c < 3; c++) acc[c] += px[c] * px[3];
      acc[3] += px[3];
      m += s.metal || 0;
    }
    const k = (y * N + x) * 4;
    for (let c = 0; c < 3; c++) base[k + c] = acc[3] > 0 ? acc[c] / acc[3] : 0;   // unpremultiplied
    base[k + 3] = acc[3] / 9;
    metal[y * N + x] = m / 9;
  }
  return {base, metal};
}

function steel(seed) {
  const lip = ragged(seed, 7), bare = ragged(seed + 1, 11), scuff = ragged(seed + 2, 5), n = grain(seed + 3), r = rng(seed + 4);
  const core = 2.0 + r() * 0.4, ring = 5.6 + r() * 1.2;
  return cell((x, y, px, py) => {
    const d = Math.hypot(x, y), a = Math.atan2(y, x), t = n(px, py);
    const hole = 1 - smooth(core - 0.35 + 0.25 * lip(a), core + 0.35 + 0.25 * lip(a), d);
    const lipA = 1 - smooth(core + 0.9 + 0.4 * lip(a), core + 1.6 + 0.4 * lip(a), d);
    const bareA = 1 - smooth(ring - 0.5 + 1.5 * bare(a), ring + 0.5 + 1.5 * bare(a), d);
    const scuffA = (1 - smooth(ring + 1, ring + 4.5 + 1.5 * scuff(a), d)) * 0.35;
    const shine = 150 + 50 * t + 25 * Math.cos(a * 2 + seed);   // brushed: brighter along one direction
    return {layers: [[[118, 120, 124], scuffA], [[shine, shine + 2, shine + 5], 0.95 * bareA], [[52, 54, 58], lipA], [[8, 8, 9], hole]],
      metal: bareA * (1 - lipA)};
  });
}

function stone(seed) {
  const chip = ragged(seed, 9), halo = ragged(seed + 1, 6), n = grain(seed + 2), r = rng(seed + 3);
  const cracks = Array.from({length: 2 + Math.floor(r() * 2)}, () => ({a: r() * 2 * Math.PI, len: 2.5 + r() * 3, bend: (r() - 0.5) * 0.6}));
  const core = 1.6 + r() * 0.4, crater = 4.2 + r() * 0.8;
  return cell((x, y, px, py) => {
    const d = Math.hypot(x, y), a = Math.atan2(y, x), t = n(px, py);
    const hole = 1 - smooth(core - 0.3, core + 0.4 + 0.3 * chip(a), d);
    const craterA = 1 - smooth(crater - 0.6 + 1.2 * chip(a), crater + 0.4 + 1.2 * chip(a), d);
    const haloA = (1 - smooth(crater, crater + 5 + 2 * halo(a), d)) * (0.45 + 0.25 * t);
    let crack = 0;
    for (const c of cracks) {   // a thin dark line from the crater rim, slightly bent
      const along = x * Math.cos(c.a) + y * Math.sin(c.a), side = -x * Math.sin(c.a) + y * Math.cos(c.a) - c.bend * (along - crater) ** 2 / c.len;
      if (along > crater - 1 && along < crater + c.len) crack = Math.max(crack, (1 - smooth(0.12, 0.5, Math.abs(side))) * (1 - smooth(crater + c.len * 0.6, crater + c.len, along)));
    }
    const grey = 70 + 30 * t;
    return {layers: [[[208, 204, 196], haloA], [[54, 52, 50], 0.6 * crack], [[grey, grey - 2, grey - 4], 0.9 * craterA], [[16, 15, 14], hole]]};
  });
}

function wood(seed) {
  const n = grain(seed), r = rng(seed + 1), torn = ragged(seed + 2, 8);
  const fibres = Array.from({length: 6}, () => ({v: (r() - 0.5) * 5, len: 2 + r() * 3.5, side: r() < 0.5 ? -1 : 1, w: 0.3 + r() * 0.3}));
  const core = 1.8 + r() * 0.4;
  return cell((x, y, px, py) => {
    const d = Math.hypot(x, y), a = Math.atan2(y, x), t = n(px, py);
    const e = Math.hypot(x / 1.8, y);   // stretched along the grain (x)
    const hole = 1 - smooth(core - 0.3, core + 0.4, d);
    const bruise = (1 - smooth(3.5 + torn(a), 7 + 1.5 * torn(a), e)) * 0.55;
    let fibre = 0;
    for (const f of fibres) {   // pale torn fibres, out from the hole along the grain
      const along = x * f.side, off = Math.abs(y - f.v * smooth(0, 6, along));
      if (along > core - 1 && along < core + f.len) fibre = Math.max(fibre, (1 - smooth(f.w * 0.6, f.w * 1.4, off)) * (1 - smooth(core + f.len * 0.5, core + f.len, along)));
    }
    return {layers: [[[70, 52, 36], bruise], [[214, 186, 140], 0.75 * fibre * (0.8 + 0.2 * t)], [[22, 16, 12], hole]]};
  });
}

const cells = [];
for (let v = 0; v < 3; v++) cells.push([0, v, steel(101 + v * 17)]);
for (let v = 0; v < 3; v++) cells.push([1, v, stone(301 + v * 17)]);
for (let v = 0; v < 3; v++) cells.push([2, v, wood(501 + v * 17)]);

const base = Buffer.alloc(W * H * 4), spec = Buffer.alloc(W * H * 4);
for (const [row, col, c] of cells) {
  for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
    const s = (y * N + x) * 4, k = ((row * N + y) * W + col * N + x) * 4, m = c.metal[y * N + x];
    for (let i = 0; i < 4; i++) base[k + i] = Math.round(Math.min(255, Math.max(0, i < 3 ? c.base[s + i] : c.base[s + 3] * 255)));
    spec[k] = Math.round(40 + 110 * m);          // smoothness: rough, bare steel fairly smooth
    spec[k + 1] = m > 0.5 ? 230 : 10;           // F0: bare steel is metal (LabPBR >= 230)
    spec[k + 2] = 0;
    spec[k + 3] = 255;
  }
}
const files = {'bullet_holes.png': png(base, W, H), 'bullet_holes_s.png': png(spec, W, H)};
if (process.argv.includes('--check')) {
  for (const [name, data] of Object.entries(files)) {
    const p = path.join(OUT, name);
    if (!fs.existsSync(p) || !fs.readFileSync(p).equals(data)) { console.error('STALE ' + name); process.exit(1); }
  }
  console.log('CHECK OK');
} else {
  fs.mkdirSync(OUT, {recursive: true});
  for (const [name, data] of Object.entries(files)) fs.writeFileSync(path.join(OUT, name), data);
  console.log('wrote ' + Object.keys(files).join(', '));
}
