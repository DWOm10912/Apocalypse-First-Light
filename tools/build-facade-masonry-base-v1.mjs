// Facade Masonry Base V1 (docs/models/facade_masonry_base_v1.md): the ground-face (burnished) concrete masonry base course
// of the Fuel Stop A1 walls, 0..1 m all round the building (piers included), with a light cast-stone cap where brick sits on
// it (user 2026-10-06: concept option A, base under the piers too, light stone cap).
// Units 50 x 20 cm (a real block is 40 cm long, which does not tile in 1 m): 5 courses and 2 units per block, running
// bond, 1 cm joints tooled concave, mortar close to the block colour; a honed face with sparse, soft aggregate flecks.
// 240 x 240 texels per block (48 per course, 120 per unit) with LabPBR _s / _n, three tone variants picked at random.
// The cap: a 2 px band at the top of the outside face, 1 px proud, mitred round outer corners (blockstate multipart over
// the base cube; StorefrontGlazing-style vanilla element models, so smooth lighting works as for any vanilla block).
//   node tools/build-facade-masonry-base-v1.mjs           -> writes textures, block / item models, blockstate
//   node tools/build-facade-masonry-base-v1.mjs --check   -> verifies every output is up to date
// LabPBR: _s R smoothness, G F0 (dielectric), B porosity, A 255; _n DirectX (red right, green down) from the wrapped height
// field, B ambient occlusion, A height (1 = the face). No per-texel noise: flecks are a few texels across and low contrast.
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {png} from './cube-slab-mesh-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const N = 240, SS = 3, BUMP = 6, VARIANTS = 3;
const hash = (a, b, c) => { let h = Math.imul(a | 0, 374761393) ^ Math.imul(b | 0, 668265263) ^ Math.imul(c | 0, 2147483647); h = Math.imul(h ^ (h >>> 13), 1274126177); return ((h ^ (h >>> 16)) >>> 0) / 4294967295; };
function vnoise(x, y, seed, cell) {   // periodic value noise on the block (period N)
  const n = N / cell, xi = Math.floor(x / cell), yi = Math.floor(y / cell), s = t => t * t * (3 - 2 * t);
  const h = (i, j) => hash(((i % n) + n) % n, ((j % n) + n) % n, seed), tx = s(x / cell - xi), ty = s(y / cell - yi);
  const a = h(xi, yi) + (h(xi + 1, yi) - h(xi, yi)) * tx, b = h(xi, yi + 1) + (h(xi + 1, yi + 1) - h(xi, yi + 1)) * tx;
  return a + (b - a) * ty;
}
export const BASE = {courses: 5, units: 2, joint: 2.4, recess: 0.12, face: [96, 93, 88], mortar: [86, 83, 79], vary: 0.035};
export const CAP = {face: [184, 178, 168]};
// a masonry face: running bond; returns base colour, _s and _n maps
function masonry(def, seed, faceFn, spec) {
  const CH = N / def.courses, UW = N / def.units, J = def.joint;
  const col = Buffer.alloc(N * N * 4), sp = Buffer.alloc(N * N * 4), nrm = Buffer.alloc(N * N * 4), H = new Float64Array(N * N);
  const locate = (x, y) => { const yy = ((y % N) + N) % N, r = Math.floor(yy / CH), off = (r % 2) * UW / 2, xx = (((x + off) % N) + N) % N, u = Math.floor(xx / UW); return {r, u, fy: yy - r * CH, fx: xx - u * UW}; };
  for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
    let c = [0, 0, 0], h = 0, joint = 0;
    for (let sy = 0; sy < SS; sy++) for (let sx = 0; sx < SS; sx++) {
      const px = x + (sx + 0.5) / SS, py = y + (sy + 0.5) / SS, L = locate(px, py);
      if (L.fy < J || L.fx < J) {
        const t = Math.max(L.fy < J ? Math.sin(Math.PI * L.fy / J) : 0, L.fx < J ? Math.sin(Math.PI * L.fx / J) : 0);
        c = c.map((v, i) => v + def.mortar[i]); h += 1 - def.recess * (0.35 + 0.65 * t); joint++;
      } else { const k = faceFn(px, py, L); c = c.map((v, i) => v + def.face[i] * k); h += 1; }
    }
    const o = (y * N + x) * 4, jf = joint / (SS * SS);
    c.forEach((v, i) => { col[o + i] = Math.max(0, Math.min(255, Math.round(v / (SS * SS)))); }); col[o + 3] = 255;
    H[y * N + x] = h / (SS * SS);
    sp[o] = Math.round(spec.sm - (spec.sm - 30) * jf); sp[o + 1] = 14; sp[o + 2] = Math.round(spec.po + 15 * jf); sp[o + 3] = 255;
    nrm[o + 2] = Math.round(255 - 55 * jf);
  }
  for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {   // slopes, wrapped (DirectX: green points down)
    const at = (i, j) => H[((j + N) % N) * N + ((i + N) % N)], hx = (at(x + 1, y) - at(x - 1, y)) / 2, hy = (at(x, y + 1) - at(x, y - 1)) / 2;
    let nx = -hx * BUMP, ny = -hy * BUMP; const l = Math.hypot(nx, ny, 1); nx /= l; ny /= l;
    const o = (y * N + x) * 4;
    nrm[o] = Math.round((nx * 0.5 + 0.5) * 255); nrm[o + 1] = Math.round((ny * 0.5 + 0.5) * 255); nrm[o + 3] = Math.round(H[y * N + x] * 255);
  }
  return [png(col, N, N), png(sp, N, N), png(nrm, N, N)];
}
// honed face: one tone per unit, a soft mottle, sparse light / dark aggregate flecks a few texels across
const groundFace = seed => (x, y, L) => {
  const fleck = vnoise(x, y, seed + 7, 3), big = vnoise(x, y, seed + 19, 6);
  const k = fleck > 0.93 ? 1.09 : fleck < 0.06 ? 0.93 : 1;
  return (1 + BASE.vary * (hash(L.r, L.u, seed) * 2 - 1)) * k * (0.97 + 0.06 * big);
};
// the cap: smooth cast stone, no joints in the block (one unit per block run), a faint mottle
function capMaps() {
  const col = Buffer.alloc(N * N * 4), sp = Buffer.alloc(N * N * 4), nrm = Buffer.alloc(N * N * 4);
  for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
    const k = 0.97 + 0.06 * vnoise(x, y, 701, 40), o = (y * N + x) * 4;
    CAP.face.forEach((v, i) => { col[o + i] = Math.round(v * k); }); col[o + 3] = 255;
    sp.set([70, 14, 30, 255], o); nrm.set([128, 128, 255, 255], o);
  }
  return [png(col, N, N), png(sp, N, N), png(nrm, N, N)];
}

// ---------------- models (vanilla JSON, facing north = outside on -Z) ----------------
const assets = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight');
const ID = 'ground_face_block', T = `apocalypse_firstlight:block/facade_masonry/`;
const json = v => JSON.stringify(v, null, 2) + '\n';
const outputs = [];
for (let v = 0; v < VARIANTS; v++) {
  const maps = masonry(BASE, 2000 + v * 37, groundFace(2000 + v * 37), {sm: 92, po: 35});
  ['', '_s', '_n'].forEach((k, i) => outputs.push([path.join(assets, `textures/block/facade_masonry/${ID}_${v}${k}.png`), maps[i]]));
  outputs.push([path.join(assets, `models/block/facade_masonry/${ID}_${v}.json`), json({parent: 'minecraft:block/cube_all', textures: {all: `${T}${ID}_${v}`}})]);
}
capMaps().forEach((m, i) => outputs.push([path.join(assets, `textures/block/facade_masonry/cast_stone_cap${['', '_s', '_n'][i]}.png`), m]));
// cap band: y 14..16, 1 px proud of the north face; the outer corner adds the west face's band and the corner square
// UVs stay inside 0..16 (a 17 px band samples the whole cap texture, which is near uniform); no cullface: the band sticks
// out of the cell, so a full block above or beside does not hide it
const face = uv => ({uv, texture: '#cap'});
const band = (from, to) => { const w = (a, b) => Math.min(16, b - a), wx = w(from[0], to[0]), wz = w(from[2], to[2]);
  return {from, to, faces: {north: face([0, 0, wx, 2]), south: face([0, 0, wx, 2]), west: face([0, 0, wz, 2]), east: face([0, 0, wz, 2]),
    up: face([0, 0, wx, wz]), down: face([0, 0, wx, wz])}}; };
const capModel = corner => ({textures: {cap: `${T}cast_stone_cap`, particle: `${T}cast_stone_cap`}, elements: corner
  ? [band([-1, 14, -1], [16, 16, 0]), band([-1, 14, 0], [0, 16, 16])]
  : [band([0, 14, -1], [16, 16, 0])]});
outputs.push([path.join(assets, 'models/block/facade_masonry/cap_straight.json'), json(capModel(false))],
  [path.join(assets, 'models/block/facade_masonry/cap_outer.json'), json(capModel(true))]);
{   // blockstate (MasonryBaseBlock: facing, shape straight / outer_left / outer_right, cap)
  const ROT = {north: 0, east: 90, south: 180, west: 270}, ref = (m, y) => ({model: T + m, ...(y % 360 ? {y: y % 360} : {})});
  const parts = [{apply: [0, 1, 2].map(v => ({model: `${T}${ID}_${v}`}))}];
  for (const [F, y] of Object.entries(ROT)) {
    parts.push({when: {cap: 'true', facing: F, shape: 'straight'}, apply: ref('cap_straight', y)});
    parts.push({when: {cap: 'true', facing: F, shape: 'outer_left'}, apply: ref('cap_outer', y)});        // the corner on the facing's counter-clockwise side
    parts.push({when: {cap: 'true', facing: F, shape: 'outer_right'}, apply: ref('cap_outer', y + 90)});  // clockwise: the same corner turned a quarter
  }
  outputs.push([path.join(assets, `blockstates/${ID}.json`), json({multipart: parts})]);
}
outputs.push([path.join(assets, `models/item/${ID}.json`), json({parent: `${T}${ID}_0`})]);

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
