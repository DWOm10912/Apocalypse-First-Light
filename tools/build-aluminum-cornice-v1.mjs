// Aluminum Cornice V1 (docs/models/aluminum_cornice_v1.md): the parapet cornice of the Fuel Stop A1 walls, the whole top
// wall cell (5..6 m, as the QuikTrip G3 cornice: masonry to about 16', cornice to 20'). User 2026-10-07: concept option A,
// a flat brushed aluminium fascia 1 px proud of the wall with a coping lip on top and a dark shadow reveal at its foot; the
// red band (a 10 cm red polycarbonate strip at the cornice's foot, unpowered) built in as a toggle.
// The roof side of the cell is the roof membrane turned up the parapet; the coping covers the whole wall top.
// Finish: brushed aluminium as a LabPBR metal (preset 232) at a medium smoothness (the first version, painted with F0 24,
// read as grey paint in game). Faint horizontal brush grain in the base colour only, no per-texel noise.
// Vanilla JSON element models (smooth lighting works), turned by the blockstate; outer corners wrap (facing / shape as the
// canopy fascia). 240 px textures with LabPBR _s / _n.
//   node tools/build-aluminum-cornice-v1.mjs           -> writes textures, block / item models, blockstate
//   node tools/build-aluminum-cornice-v1.mjs --check   -> verifies every output is up to date
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
// a flat material: colour function, LabPBR smoothness / F0 / porosity; normal flat
function flat(colour, sm, f0, po) {
  const col = Buffer.alloc(N * N * 4), sp = Buffer.alloc(N * N * 4), nrm = Buffer.alloc(N * N * 4);
  for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
    const o = (y * N + x) * 4, c = colour(x, y);
    c.forEach((v, i) => { col[o + i] = Math.max(0, Math.min(255, Math.round(v))); }); col[o + 3] = 255;
    sp.set([sm, f0, po, 255], o); nrm.set([128, 128, 255, 255], o);
  }
  return [png(col, N, N), png(sp, N, N), png(nrm, N, N)];
}
export const MAT = {
  // brushed aluminium, LabPBR hardcoded metal 232 (aluminium), smoothness 150: between satin and mirror. 2026-10-07 the user
  // asked why it did not reflect: the first version was painted aluminium (F0 24, smoothness 110), which read as grey paint
  alu: {c: [176, 180, 183], sm: 150, f0: 232, po: 0},
  membrane: {c: [196, 198, 196], sm: 60, f0: 14, po: 20},      // TPO roof membrane turned up the parapet
  reveal: {c: [40, 42, 45], sm: 60, f0: 14, po: 0},           // the shadow reveal at the cornice's foot
  band: {c: [168, 50, 42], sm: 170, f0: 16, po: 0},           // red polycarbonate band, unlit
};
// brush grain: each texel row a slightly different tone (long streaks), periodic; 3 % at most
const grain = Array.from({length: N}, (_, y) => (hash(y, 1, 7) - 0.5) * 0.03 + (hash(y >> 2, 3, 9) - 0.5) * 0.02);
const TEX = {
  aluminum_cornice: flat((x, y) => MAT.alu.c.map(v => v * (1 + grain[y])), MAT.alu.sm, MAT.alu.f0, MAT.alu.po),
  cornice_membrane: flat((x, y) => MAT.membrane.c.map(v => v * (0.98 + 0.04 * vnoise(x, y, 31, 60))), MAT.membrane.sm, MAT.membrane.f0, MAT.membrane.po),
  cornice_reveal: flat(() => MAT.reveal.c, MAT.reveal.sm, MAT.reveal.f0, MAT.reveal.po),
  cornice_band: flat((x, y) => MAT.band.c.map(v => v * (0.98 + 0.03 * vnoise(x, y, 41, 80))), MAT.band.sm, MAT.band.f0, MAT.band.po),
};

// ---------------- models (vanilla JSON; facing north: outside on -Z; px, cell 0..16) ----------------
// fascia 1 px proud from y 0.5 to 15.5, reveal 0.25 px proud below it, coping 0.5 px on top over the whole wall top,
// 1.6 px proud; the band 0.05 px proud of the fascia, y 0.6..2.2 (10 cm)
export const C = {fascia: [0.5, 15.5], proud: 1, reveal: 0.25, copeY: 15.5, copeOut: 1.6, band: [0.6, 2.2], bandOut: 0.05};
const T = 'apocalypse_firstlight:block/facade_cornice/';
const w = (a, b) => Math.min(16, b - a);
const box = (from, to, tex, faces, cull = {}) => ({from, to, faces: Object.fromEntries(faces.map(f => {
  const [x0, y0, z0] = from, [x1, y1, z1] = to;
  const uv = f === 'north' || f === 'south' ? [0, 16 - y1, w(x0, x1), 16 - y1 + w(y0, y1)] : f === 'east' || f === 'west' ? [0, 16 - y1, w(z0, z1), 16 - y1 + w(y0, y1)] : [0, 0, w(x0, x1), w(z0, z1)];
  return [f, {uv: uv.map(v => Math.max(0, Math.min(16, +v.toFixed(4)))), texture: tex, ...(cull[f] ? {cullface: cull[f]} : {})}];
}))});
function cornice(corner) {
  const P = C.proud, x0 = corner ? -P : 0, el = [];
  // the wall core: roof-side face membrane, ends aluminium (culled by a neighbour), bottom on the masonry; its outside
  // face (dark, behind the fascia and the reveal) closes the cell: 2026-10-07 in game, seen from below, the reveal had no
  // underside and the core no outside face, and the sky showed through in thin streaks
  el.push(box([0, 0, 0], [16, C.copeY, 16], '#membrane', ['south'], {}));
  el.push(box([0, 0, 0], [16, C.copeY, 16], '#reveal', ['north'], {}));
  el.push(box([0, 0, 0], [16, C.copeY, 16], '#alu', corner ? ['east', 'down'] : ['east', 'west', 'down'], {east: 'east', west: 'west', down: 'down'}));   // a corner's west side is outside: closed below
  // fascia and reveal on the north face (and the west face round an outer corner)
  el.push(box([x0, C.fascia[0], -P], [16, C.fascia[1], 0], '#alu', ['north', 'down', 'east', 'west']));
  el.push(box([x0 + (corner ? P - C.reveal : 0), 0, -C.reveal], [16, C.fascia[0], 0], '#reveal', ['north', 'down', 'east', 'west']));
  if (corner) {
    el.push(box([-P, C.fascia[0], 0], [0, C.fascia[1], 16], '#alu', ['west', 'down', 'south']));
    el.push(box([0, 0, 0], [16, C.copeY, 16], '#reveal', ['west'], {}));
    el.push(box([-C.reveal, 0, 0], [0, C.fascia[0], 16], '#reveal', ['west', 'down', 'south']));
  }
  // coping over the whole wall top, its lip proud of the fascia
  const co = C.copeOut;
  el.push(box([corner ? -co : 0, C.copeY, -co], [16, 16, 16], '#alu', ['up', 'down', 'north', 'south', 'east', 'west'], {up: 'up'}));
  return {parent: 'minecraft:block/block', ambientocclusion: true, textures: {alu: T + 'aluminum_cornice', membrane: T + 'cornice_membrane', reveal: T + 'cornice_reveal', particle: T + 'aluminum_cornice'}, elements: el};
}
function band(corner) {
  const z = -C.proud - C.bandOut, el = [box([corner ? z : 0, C.band[0], z], [16, C.band[1], -C.proud], '#band', ['north', 'up', 'down', 'east', 'west'])];
  if (corner) el.push(box([z, C.band[0], -C.proud], [-C.proud, C.band[1], 16], '#band', ['west', 'up', 'down', 'south']));
  return {parent: 'minecraft:block/block', ambientocclusion: true, textures: {band: T + 'cornice_band', particle: T + 'cornice_band'}, elements: el};
}

const assets = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight');
const json = v => JSON.stringify(v, null, 2) + '\n';
const outputs = [];
for (const [name, maps] of Object.entries(TEX)) ['', '_s', '_n'].forEach((k, i) => outputs.push([path.join(assets, `textures/block/facade_cornice/${name}${k}.png`), maps[i]]));
outputs.push([path.join(assets, 'models/block/facade_cornice/cornice_straight.json'), json(cornice(false))],
  [path.join(assets, 'models/block/facade_cornice/cornice_outer.json'), json(cornice(true))],
  [path.join(assets, 'models/block/facade_cornice/band_straight.json'), json(band(false))],
  [path.join(assets, 'models/block/facade_cornice/band_outer.json'), json(band(true))]);
{   // blockstate (AluminumCorniceBlock: facing, shape straight / outer_left / outer_right, band)
  const ROT = {north: 0, east: 90, south: 180, west: 270}, ref = (m, y) => ({model: T + m, ...(y % 360 ? {y: y % 360} : {})}), parts = [];
  for (const [F, y] of Object.entries(ROT)) {
    parts.push({when: {facing: F, shape: 'straight'}, apply: ref('cornice_straight', y)});
    parts.push({when: {facing: F, shape: 'outer_left'}, apply: ref('cornice_outer', y)});          // corner on the facing's counter-clockwise side
    parts.push({when: {facing: F, shape: 'outer_right'}, apply: ref('cornice_outer', y + 90)});    // clockwise: the same corner turned a quarter
    parts.push({when: {band: 'true', facing: F, shape: 'straight'}, apply: ref('band_straight', y)});
    parts.push({when: {band: 'true', facing: F, shape: 'outer_left'}, apply: ref('band_outer', y)});
    parts.push({when: {band: 'true', facing: F, shape: 'outer_right'}, apply: ref('band_outer', y + 90)});
  }
  outputs.push([path.join(assets, 'blockstates/aluminum_cornice.json'), json({multipart: parts})]);
}
outputs.push([path.join(assets, 'models/item/aluminum_cornice.json'), json({parent: T + 'cornice_straight'})]);

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
