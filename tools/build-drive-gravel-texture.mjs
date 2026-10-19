// County road G3 (docs/worldgen/county_road_g3.md 6): the forest home's driveway gravel, drawn on its mesh (DriveMesh) like
// the roads' asphalt: 240 texels per metre, one tile 4 m x 4 m (960 x 960), periodic both ways, LabPBR. Compacted crushed
// stone with fines (a rural crusher-run driveway): angular stones 8 .. 35 mm (two Voronoi layers, a planar facet each),
// mostly grey limestone, some tan and dark ones, dusty brown-grey fines between them; the tone wanders over metres.
// Vanilla has no normal maps, so the albedo carries a little of the relief (crevices darker, facets lit from above).
//   node tools/build-drive-gravel-texture.mjs           -> writes the textures
//   node tools/build-drive-gravel-texture.mjs --check   -> verifies they are up to date
// _n: DirectX (red right, green down) from the height slopes, B ambient occlusion, A height (1 - depth / 250 mm).
// _s: R smoothness, G F0 (dielectric), B porosity (0..64, wets in rain), A 255 (no emission).
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {png} from './cube-slab-mesh-lib.mjs';

const N = 960, SS = 3, PERIOD_MM = 4000;
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

/** One Voronoi layer of stones, cell mm (divides PERIOD_MM): the nearest stone and the distance to its edge (mm). */
function layer(cellMm, seed) {
  const C = Math.round(PERIOD_MM / cellMm);
  return (x, y) => {
    const ci = Math.floor(x / cellMm), cj = Math.floor(y / cellMm);
    let d1 = Infinity, d2 = Infinity, near = null;
    for (let dj = -1; dj <= 1; dj++) for (let di = -1; di <= 1; di++) {
      const i = ci + di, j = cj + dj, ii = ((i % C) + C) % C, jj = ((j % C) + C) % C;
      const fx = (i + 0.1 + 0.8 * hash(ii, jj, seed)) * cellMm, fy = (j + 0.1 + 0.8 * hash(ii, jj, seed + 1)) * cellMm;
      // stretched a little in a random direction: crushed stone is not round
      const a = 6.2832 * hash(ii, jj, seed + 2), e = 1 + 0.45 * hash(ii, jj, seed + 3), ca = Math.cos(a), sa = Math.sin(a);
      const dx = x - fx, dy = y - fy, u = dx * ca + dy * sa, v = (-dx * sa + dy * ca) * e, d = Math.hypot(u, v);
      if (d < d1) { d2 = d1; d1 = d; near = {ii, jj, fx, fy}; } else if (d < d2) d2 = d;
    }
    return {near, border: (d2 - d1) / 2, d1};
  };
}

const LARGE = layer(PERIOD_MM / 160, 801), SMALL = layer(PERIOD_MM / 320, 811);   // 25 mm and 12.5 mm cells
const LIME = [170, 167, 160], TAN = [166, 154, 134], DARK = [112, 110, 107], FINES = [140, 133, 122];
const RISE_MM = 3.5;

/**
 * A stone of a layer: colour, facet, height and how far inside it the point lies (mm, <= 0: in the fines around it);
 * null where the layer has no stone (a large layer's cell left to small ones). A stone fills its cell's middle, rounded
 * (radius 0.55 .. 0.8 of the cell) and kept off the cell's edge by 0.3 .. 1.6 mm, so the fines show between stones
 * unevenly, not as a grout line.
 */
function stone(L, cellMm, seed, x, y, keep) {
  const {near, border, d1} = L(x, y);
  if (hash(near.ii, near.jj, seed) >= keep) return null;
  const r = cellMm * (0.55 + 0.25 * hash(near.ii, near.jj, seed + 6)), gap = 0.3 + 1.3 * hash(near.ii, near.jj, seed + 7);
  const inside = Math.min(border - gap, (r - d1) * 0.8);
  const k = hash(near.ii, near.jj, seed + 1), kind = k < 0.73 ? LIME : k < 0.88 ? TAN : DARK;
  const tone = 0.82 + 0.3 * hash(near.ii, near.jj, seed + 2);
  // the facet: a plane tilted 0 .. 25 degrees
  const ta = 6.2832 * hash(near.ii, near.jj, seed + 3), tt = Math.tan(0.44 * hash(near.ii, near.jj, seed + 4));
  const gx = Math.cos(ta) * tt, gy = Math.sin(ta) * tt;
  const up = 1.5 + 4 * hash(near.ii, near.jj, seed + 5);
  return {inside, kind, tone, gx, gy, up, dx: x - near.fx, dy: y - near.fy};
}

/** Large-scale tone: dust and wear over 0.2 .. 4 m. */
const wander = (x, y) => 1 + 0.04 * vn(x, y, 31, 2000) + 0.03 * vn(x, y, 32, 1000) + 0.025 * vn(x, y, 33, 250);

function gravel(x, y) {
  const t = wander(x, y);
  // fines: dust and sand between the stones
  const fines = mul(FINES, t * (1 + 0.05 * vn(x, y, 41, PERIOD_MM / 800) + 0.04 * vn(x, y, 42, PERIOD_MM / 200)));
  let s = stone(LARGE, PERIOD_MM / 160, 821, x, y, 0.5);
  if (!s || s.inside <= 0) s = stone(SMALL, PERIOD_MM / 320, 831, x, y, 0.95) ?? s;
  if (!s || s.inside <= 0) {
    // the fines, shaded toward a stone close by (its crevice)
    const near = s ? clamp(1 + s.inside / 2.5, 0, 1) : 0;
    return {c: mul(fines, 0.97 - 0.12 * near), s: 38, p: 60, h: -1 - 0.5 * (vn(x, y, 43, PERIOD_MM / 400) + 1) - near, ao: 0.95 - 0.15 * near};
  }
  const k = Math.min(1, s.inside / RISE_MM);
  const facet = s.gx * s.dx + s.gy * s.dy;
  const h = s.up * Math.sqrt(k) + facet * 0.35;
  // a little relief in the albedo: the facet lit from the upper left, darker toward the crevice
  const lit = 1 + clamp(0.22 * (-s.gx - s.gy), -0.12, 0.12) - 0.12 * (1 - k);
  const c = mul(mix(fines, mul(s.kind, s.tone), 0.55 + 0.45 * k), t * lit);
  return {c, s: 66, p: 26, h, ao: 0.88 + 0.12 * k};
}

function render(f) {
  const W = N, H = N, sx = PERIOD_MM / W, sy = PERIOD_MM / H;
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
  const at = (i, j) => Hm[((j + H) % H) * W + ((i + W) % W)];
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
    const hx = (at(x + 1, y) - at(x - 1, y)) / (2 * sx), hy = (at(x, y + 1) - at(x, y - 1)) / (2 * sy);
    let nx = -hx, ny = -hy; const l = Math.hypot(nx, ny, 1); nx /= l; ny /= l;
    const o = (y * W + x) * 4;
    nrm[o] = Math.round((nx * 0.5 + 0.5) * 255); nrm[o + 1] = Math.round((ny * 0.5 + 0.5) * 255);
    nrm[o + 3] = clamp(Math.round(255 * (1 + Hm[y * W + x] / 250)), 0, 255);
  }
  return [png(col, W, H), png(spec, W, H), png(nrm, W, H)];
}

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const OUT = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight/textures/block/road');

export function outputs() {
  const maps = render(gravel);
  return ['', '_s', '_n'].map((k, i) => [path.join(OUT, 'gravel' + k + '.png'), maps[i]]);
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
