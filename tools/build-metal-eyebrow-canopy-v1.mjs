// Metal Eyebrow Canopy V1 (docs/models/metal_eyebrow_canopy_v1.md): the 1 m storefront canopy of the Fuel Stop A1 at 3 m.
// User 2026-10-07: concept option B, a hanger-rod canopy: a 10 cm deck with the Metal Wall Panel's planks as its soffit, a
// 22 cm fascia dropping 3 cm below the soffit as a drip edge, and steel tie rods at 45 degrees from behind the fascia up to
// the wall at about 4.14 m, toggled per block (sneak + use) so a run gets a rod every few metres. Charcoal like the portals.
// Ends without a neighbouring canopy close with a fascia return (left / right). Inside an entry portal the run continues
// through the jamb cells: a Metal Panel Jamb next to a canopy shows the rest of its cell as canopy (jamb_canopy_*), and the
// canopy treats that jamb as a neighbour (no return).
// Vanilla JSON element models (smooth lighting works; the rod uses the vanilla 45 degree element rotation), turned by the
// blockstate. 240 px textures with LabPBR _s / _n; the soffit reuses facade_metal_panel/metal_wall_panel.
//   node tools/build-metal-eyebrow-canopy-v1.mjs           -> writes textures, block / item models, blockstate
//   node tools/build-metal-eyebrow-canopy-v1.mjs --check   -> verifies every output is up to date
// The jamb's blockstate (with its canopy pieces) is written by tools/build-metal-wall-panel-v1.mjs.
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {png} from './cube-slab-mesh-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const N = 240;
const hash = (a, b, c) => { let h = Math.imul(a | 0, 374761393) ^ Math.imul(b | 0, 668265263) ^ Math.imul(c | 0, 2147483647); h = Math.imul(h ^ (h >>> 13), 1274126177); return ((h ^ (h >>> 16)) >>> 0) / 4294967295; };
function vnoise(x, y, seed, cell) {   // periodic value noise on the block (period N)
  const n = N / cell, xi = Math.floor(x / cell), yi = Math.floor(y / cell), s = t => t * t * (3 - 2 * t);
  const h = (i, j) => hash(((i % n) + n) % n, ((j % n) + n) % n, seed), tx = s(x / cell - xi), ty = s(y / cell - yi);
  const a = h(xi, yi) + (h(xi + 1, yi) - h(xi, yi)) * tx, b = h(xi, yi + 1) + (h(xi + 1, yi + 1) - h(xi, yi + 1)) * tx;
  return a + (b - a) * ty;
}
function flat(c, sm, f0, po, seed) {   // a plain coated finish: a faint mottle (<= 1.5 %), flat normal
  const col = Buffer.alloc(N * N * 4), sp = Buffer.alloc(N * N * 4), nrm = Buffer.alloc(N * N * 4);
  for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
    const o = (y * N + x) * 4, k = 0.985 + 0.03 * vnoise(x, y, seed, 60);
    c.forEach((v, i) => { col[o + i] = Math.round(v * k); }); col[o + 3] = 255;
    sp.set([sm, f0, po, 255], o); nrm.set([128, 128, 255, 255], o);
  }
  return [png(col, N, N), png(sp, N, N), png(nrm, N, N)];
}
// coated aluminium as the Metal Wall Panel (same colour and PBR, so portal and canopy read as one material); painted steel rods
export const MAT = {coat: {c: [60, 62, 65], sm: 96, f0: 16, po: 0}, steel: {c: [42, 44, 47], sm: 120, f0: 20, po: 0}};

// ---------------- geometry (px; facing north: outside on -Z, the wall behind at z 16) ----------------
// fascia z 0..0.8 (5 cm), y 0..3.5 (22 cm); deck y 0.5..2.1 (10 cm), z 0.8..16; end return 0.8 px; rod 0.7 px square from a
// bracket behind the fascia (centre z 1.2, y 3.4) at 45 degrees to the wall (z 16, y 18.2 = 4.14 m), a 2 x 2 px wall plate
export const G = {fz: 0.8, fh: 3.5, deck: [0.5, 2.1], ret: 0.8, rod: 0.7, rodStart: [1.2, 3.4], plate: 2};
const assets = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight');
const T = 'apocalypse_firstlight:block/facade_eyebrow/', SOFFIT = 'apocalypse_firstlight:block/facade_metal_panel/metal_wall_panel';
const json = v => JSON.stringify(v, null, 2) + '\n';
const c16 = v => Math.max(0, Math.min(16, v));
// a box with the listed faces; UVs from the face's own extent (clamped to 0..16; the textures are plain), soffit on 'down'
function box(from, to, faces, cull = {}, tex = {}) {
  const [x0, y0, z0] = from, [x1, y1, z1] = to;
  const ext = {north: [x0, y0, x1, y1], south: [x0, y0, x1, y1], west: [z0, y0, z1, y1], east: [z0, y0, z1, y1], up: [x0, z0, x1, z1], down: [x0, z0, x1, z1]};
  return {from, to, faces: Object.fromEntries(faces.map(f => { const [a, b, c, d] = ext[f];
    return [f, {uv: [c16(a), c16(16 - d), c16(c), c16(16 - b)], texture: tex[f] || '#coat', ...(cull[f] ? {cullface: cull[f]} : {})}]; }))};
}
// a canopy run from x a to x b with optional returns at either end; 'open' ends are cut flat (culled against a neighbour)
function canopy(a, b, capW, capE, openW = true, openE = true) {
  const {fz, fh, deck: [d0, d1], ret} = G, el = [];
  const ends = (w, e) => [...(w ? ['west'] : []), ...(e ? ['east'] : [])];
  el.push(box([a, 0, 0], [b, fh, fz], ['north', 'south', 'up', 'down', ...ends(capW || openW, capE || openE)], {north: 'north', down: 'down', west: 'west', east: 'east'}));
  const da = capW ? a + ret : a, db = capE ? b - ret : b;
  el.push(box([da, d0, fz], [db, d1, 16], ['up', 'down', 'south', ...ends(!capW && openW, !capE && openE)], {south: 'south', west: 'west', east: 'east'}, {down: '#soffit'}));
  if (capW) el.push(box([a, 0, fz], [a + ret, fh, 16], ['west', 'east', 'up', 'down', 'south'], {west: 'west', down: 'down', south: 'south'}));
  if (capE) el.push(box([b - ret, 0, fz], [b, fh, 16], ['west', 'east', 'up', 'down', 'south'], {east: 'east', down: 'down', south: 'south'}));
  return {parent: 'minecraft:block/block', textures: {particle: `${T}eyebrow_coat`, coat: `${T}eyebrow_coat`, soffit: SOFFIT}, elements: el};
}
const rodModel = (() => {
  const {rod, rodStart: [sz, sy], plate} = G, h = rod / 2, len = (16 - sz) * Math.SQRT2, wy = sy + (16 - sz);
  const r = v => Math.round(v * 1000) / 1000;
  return {textures: {particle: `${T}eyebrow_steel`, steel: `${T}eyebrow_steel`}, elements: [
    // the clevis bracket on the deck, against the back of the fascia, holding the rod's lower end
    {...box([7.55, G.deck[1], G.fz], [8.45, 4.0, 1.6], ['up', 'south', 'west', 'east'], {}, {up: '#steel', south: '#steel', west: '#steel', east: '#steel'})},
    // the rod: a bar along +z rotated -45 degrees about x at its lower end, so it climbs to the wall
    {from: [8 - h, r(sy - h), sz], to: [8 + h, r(sy + h), r(sz + len)], rotation: {origin: [8, sy, sz], axis: 'x', angle: -45},
      faces: {up: {uv: [0, 0, rod, 16], texture: '#steel'}, down: {uv: [0, 0, rod, 16], texture: '#steel'},
        west: {uv: [0, 0, 16, rod], texture: '#steel'}, east: {uv: [0, 0, 16, rod], texture: '#steel'}}},
    // the wall plate it is bolted to
    box([8 - plate / 2, r(wy - plate / 2), 15.6], [8 + plate / 2, r(wy + plate / 2), 16], ['north', 'up', 'down', 'west', 'east'], {}, {north: '#steel', up: '#steel', down: '#steel', west: '#steel', east: '#steel'}),
  ]};
})();
const outputs = [];
const tex = {eyebrow_coat: flat(MAT.coat.c, MAT.coat.sm, MAT.coat.f0, MAT.coat.po, 911), eyebrow_steel: flat(MAT.steel.c, MAT.steel.sm, MAT.steel.f0, MAT.steel.po, 913)};
for (const [k, maps] of Object.entries(tex)) maps.forEach((m, i) => outputs.push([path.join(assets, `textures/block/facade_eyebrow/${k}${['', '_s', '_n'][i]}.png`), m]));
const models = {
  canopy: canopy(0, 16, false, false), canopy_cap_w: canopy(0, 16, true, false), canopy_cap_e: canopy(0, 16, false, true), canopy_cap_both: canopy(0, 16, true, true),
  canopy_rod: rodModel,
  // inside a jamb cell: the plate takes the first 5 px on its side (MetalPanelJambBlock.THICK); the cut end against it shows no face
  jamb_canopy_w: canopy(5, 16, false, false, false, true), jamb_canopy_e: canopy(0, 11, false, false, true, false),
};
for (const [k, v] of Object.entries(models)) outputs.push([path.join(assets, `models/block/facade_eyebrow/${k}.json`), json(v)]);
{   // MetalEyebrowCanopyBlock: facing; left / right = a canopy run continues on that side (left = facing's clockwise side,
    // the viewer's left looking at the facade from outside: +x for north); rod
  const ROT = {north: 0, east: 90, south: 180, west: 270}, ref = (m, y) => ({model: T + m, ...(y % 360 ? {y: y % 360} : {})});
  const parts = [];
  for (const [F, y] of Object.entries(ROT)) {
    parts.push({when: {facing: F, left: 'true', right: 'true'}, apply: ref('canopy', y)});
    parts.push({when: {facing: F, left: 'false', right: 'true'}, apply: ref('canopy_cap_e', y)});
    parts.push({when: {facing: F, left: 'true', right: 'false'}, apply: ref('canopy_cap_w', y)});
    parts.push({when: {facing: F, left: 'false', right: 'false'}, apply: ref('canopy_cap_both', y)});
    parts.push({when: {facing: F, rod: 'true'}, apply: ref('canopy_rod', y)});
  }
  outputs.push([path.join(assets, 'blockstates/metal_eyebrow_canopy.json'), json({multipart: parts})]);
}
outputs.push([path.join(assets, 'models/item/metal_eyebrow_canopy.json'), json({parent: `${T}canopy_cap_both`})]);

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
