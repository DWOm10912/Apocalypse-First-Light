// Facade Brick V1 (docs/models/facade_brick_v1.md): the face brick of modern North American commercial buildings, the
// Fuel Stop A1 walls (warm gray field brick) and piers (charcoal). Utility-size units at real scale: 10 courses (10 cm)
// and 3 units (33 cm) per block, running bond, 1 cm mortar joints tooled concave, the mortar close to the brick colour
// (user 2026-10-06: option B of the concept, matched mortar, corners not bonded yet).
// 240 x 240 texels per block (24 per course, 80 per unit; 240 = 16 x 15 keeps mip level 4) with LabPBR _s / _n.
// Three tone variants per colour (same joints, different brick tones) so a long wall does not repeat every block; the
// blockstate picks one at random per position.
//   node tools/build-facade-brick-v1.mjs           -> writes textures, block / item models, blockstates
//   node tools/build-facade-brick-v1.mjs --check   -> verifies every output is up to date
// LabPBR: _s R smoothness, G F0 (dielectric), B porosity (0..64), A 255 (no emission); _n DirectX (red right, green
// down) from the height field's slopes (wrapped, so the block tiles), B ambient occlusion, A height (1 = the brick face).
// No per-texel noise: each brick has one tone plus a very low-frequency mottle; the joints carry the detail.
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {png} from './cube-slab-mesh-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const N = 240, COURSES = 10, UNITS = 3, CH = N / COURSES, UW = N / UNITS, JOINT = 2.4, SS = 3;
const RECESS = 0.12, ARRIS = 0.8, BUMP = 6;
export const BRICKS = {
  face_brick_warm_gray: {face: [156, 141, 126], mortar: [140, 132, 122], vary: 0.045, mottle: 0.025},
  face_brick_charcoal: {face: [72, 74, 78], mortar: [82, 83, 86], vary: 0.035, mottle: 0.02},
};
const VARIANTS = 3;

const hash = (a, b, c) => { let h = Math.imul(a | 0, 374761393) ^ Math.imul(b | 0, 668265263) ^ Math.imul(c | 0, 2147483647); h = Math.imul(h ^ (h >>> 13), 1274126177); return ((h ^ (h >>> 16)) >>> 0) / 4294967295; };
// periodic value noise on the block (period N), for a soft mottle a few bricks across
function mottle(x, y, seed, cell = 60) {
  const n = N / cell, xi = Math.floor(x / cell), yi = Math.floor(y / cell), s = t => t * t * (3 - 2 * t);
  const h = (i, j) => hash(((i % n) + n) % n, ((j % n) + n) % n, seed);
  const tx = s(x / cell - xi), ty = s(y / cell - yi);
  const a = h(xi, yi) + (h(xi + 1, yi) - h(xi, yi)) * tx, b = h(xi, yi + 1) + (h(xi + 1, yi + 1) - h(xi, yi + 1)) * tx;
  return a + (b - a) * ty - 0.5;
}
// where a point (texel coordinates, any real) falls: course, unit, position in the joint pattern
function locate(x, y) {
  const yy = ((y % N) + N) % N, r = Math.floor(yy / CH), off = (r % 2) * UW / 2;
  const xx = (((x + off) % N) + N) % N, u = Math.floor(xx / UW);
  const fy = yy - r * CH, fx = xx - u * UW;   // the joint is the first JOINT texels of each course / unit
  return {r, u, fy, fx};
}
function heightAt(x, y) {
  const {fy, fx} = locate(x, y);
  const jy = fy < JOINT ? fy / JOINT : -1, jx = fx < JOINT ? fx / JOINT : -1;
  if (jy >= 0 || jx >= 0) {   // tooled concave joint, deepest in the middle
    const t = Math.max(jy >= 0 ? Math.sin(Math.PI * jy) : 0, jx >= 0 ? Math.sin(Math.PI * jx) : 0);
    return 1 - RECESS * (0.35 + 0.65 * t);
  }
  const d = Math.min(fy - JOINT, CH - fy, fx - JOINT, UW - fx);   // distance to the nearest joint edge
  return d < ARRIS ? 1 - 0.04 * (1 - d / ARRIS) ** 2 : 1;
}
function build(def, seed) {
  const col = Buffer.alloc(N * N * 4), spec = Buffer.alloc(N * N * 4), nrm = Buffer.alloc(N * N * 4), H = new Float64Array(N * N);
  for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
    let c = [0, 0, 0], joint = 0;
    for (let sy = 0; sy < SS; sy++) for (let sx = 0; sx < SS; sx++) {
      const px = x + (sx + 0.5) / SS, py = y + (sy + 0.5) / SS, {r, u, fy, fx} = locate(px, py);
      let k;
      if (fy < JOINT || fx < JOINT) { k = def.mortar; joint++; }
      else { const t = 1 + def.vary * (hash(r, u, seed) * 2 - 1) + def.mottle * 2 * mottle(px, py, seed + 11); k = def.face.map(v => v * t); }
      c = c.map((v, i) => v + k[i]);
    }
    const o = (y * N + x) * 4, jf = joint / (SS * SS);
    c.forEach((v, i) => { col[o + i] = Math.max(0, Math.min(255, Math.round(v / (SS * SS)))); }); col[o + 3] = 255;
    spec[o] = Math.round(46 - 16 * jf); spec[o + 1] = 14; spec[o + 2] = Math.round(45 + 10 * jf); spec[o + 3] = 255;
    H[y * N + x] = heightAt(x + 0.5, y + 0.5);
    nrm[o + 2] = Math.round(255 - 55 * jf);   // occlusion in the joints
  }
  for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {   // slopes, wrapped so the face tiles (DirectX: green points down)
    const at = (i, j) => H[((j + N) % N) * N + ((i + N) % N)];
    const hx = (at(x + 1, y) - at(x - 1, y)) / 2, hy = (at(x, y + 1) - at(x, y - 1)) / 2;
    let nx = -hx * BUMP, ny = -hy * BUMP; const l = Math.hypot(nx, ny, 1); nx /= l; ny /= l;
    const o = (y * N + x) * 4;
    nrm[o] = Math.round((nx * 0.5 + 0.5) * 255); nrm[o + 1] = Math.round((ny * 0.5 + 0.5) * 255); nrm[o + 3] = Math.round(H[y * N + x] * 255);
  }
  return [png(col, N, N), png(spec, N, N), png(nrm, N, N)];
}

const assets = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight');
const json = v => JSON.stringify(v, null, 2) + '\n';
const outputs = [];
for (const [id, def] of Object.entries(BRICKS)) {
  const variants = [];
  for (let v = 0; v < VARIANTS; v++) {
    const name = `${id}_${v}`, maps = build(def, 1000 * (Object.keys(BRICKS).indexOf(id) + 1) + v * 37);
    ['', '_s', '_n'].forEach((k, i) => outputs.push([path.join(assets, `textures/block/facade_brick/${name}${k}.png`), maps[i]]));
    outputs.push([path.join(assets, `models/block/facade_brick/${name}.json`), json({parent: 'minecraft:block/cube_all', textures: {all: `apocalypse_firstlight:block/facade_brick/${name}`}})]);
    variants.push({model: `apocalypse_firstlight:block/facade_brick/${name}`});
  }
  outputs.push([path.join(assets, `blockstates/${id}.json`), json({variants: {'': variants}})]);
  outputs.push([path.join(assets, `models/item/${id}.json`), json({parent: `apocalypse_firstlight:block/facade_brick/${id}_0`})]);
}
const isMain = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  if (process.argv.includes('--check')) {
    for (const [file, data] of outputs) { const cur = fs.existsSync(file) ? fs.readFileSync(file) : null; if (!cur || !cur.equals(Buffer.isBuffer(data) ? data : Buffer.from(data))) throw new Error('stale ' + path.relative(ROOT, file)); }
    console.log('CHECK OK');
  } else {
    for (const [file] of outputs) fs.mkdirSync(path.dirname(file), {recursive: true});
    for (const [file, data] of outputs) fs.writeFileSync(file, data);
    console.log('wrote ' + outputs.length + ' files');
  }
}
