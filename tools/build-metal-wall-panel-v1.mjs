// Metal Wall Panel V1 (docs/models/metal_wall_panel_v1.md): the charcoal metal cladding of the Fuel Stop A1 entry portals
// (4 m wide, 7 m high, 1 m proud) and its 0.3 m jamb plate. User 2026-10-07: concept option C, horizontal linear planks,
// a shadow groove every 25 cm and no vertical joints, so the cladding shows no block grid and the grooves wrap round the thin
// jamb plates without a break.
// Finish: coated (PVDF) aluminium, a dielectric paint layer (F0 16) at a satin smoothness; full-metal F0 on a large dark
// panel glares under shaders. The coping on top is the cornice's brushed aluminium (facade_cornice/aluminum_cornice).
// Vanilla JSON models (smooth lighting works). 240 px texture with LabPBR _s / _n.
//   node tools/build-metal-wall-panel-v1.mjs           -> writes the texture, block / item models, blockstates
//   node tools/build-metal-wall-panel-v1.mjs --check   -> verifies every output is up to date
// LabPBR: _s R smoothness, G F0, B porosity, A 255; _n DirectX (red right, green down), B ambient occlusion, A height.
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {png} from './cube-slab-mesh-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const N = 240, SS = 4;
const hash = (a, b, c) => { let h = Math.imul(a | 0, 374761393) ^ Math.imul(b | 0, 668265263) ^ Math.imul(c | 0, 2147483647); h = Math.imul(h ^ (h >>> 13), 1274126177); return ((h ^ (h >>> 16)) >>> 0) / 4294967295; };
function vnoise(x, y, seed, cell) {   // periodic value noise on the block (period N)
  const n = N / cell, xi = Math.floor(x / cell), yi = Math.floor(y / cell), s = t => t * t * (3 - 2 * t);
  const h = (i, j) => hash(((i % n) + n) % n, ((j % n) + n) % n, seed), tx = s(x / cell - xi), ty = s(y / cell - yi);
  const a = h(xi, yi) + (h(xi + 1, yi) - h(xi, yi)) * tx, b = h(xi, yi + 1) + (h(xi + 1, yi + 1) - h(xi, yi + 1)) * tx;
  return a + (b - a) * ty;
}
// planks 60 texels (25 cm) high; the groove at the top of each plank (texel rows 0..3 of each period, so one falls on every
// block edge and the stack shows one even rhythm): a 3-texel floor 2 texels deep, a rounded 2-texel lip on the plank below
// and a 3-texel rounded edge on the plank above
export const PANEL = {c: [60, 62, 65], pitch: 60, floor: 3, lip: 2, edge: 3, depth: 2, sm: 96, smGroove: 50, f0: 16, po: 0};
const smooth = t => { t = Math.max(0, Math.min(1, t)); return t * t * (3 - 2 * t); };
function height(fy) {   // fy: texel row within a plank period (0 = groove top); 0 = panel face, -depth = groove floor
  const P = PANEL;
  if (fy < P.floor) return -P.depth;
  if (fy < P.floor + P.lip) return -P.depth * (1 - smooth((fy - P.floor) / P.lip));
  if (fy > P.pitch - P.edge) return -P.depth * smooth((fy - (P.pitch - P.edge)) / P.edge);
  return 0;
}
function panelMaps() {
  const col = Buffer.alloc(N * N * 4), sp = Buffer.alloc(N * N * 4), nrm = Buffer.alloc(N * N * 4);
  const H = new Float64Array(N);   // the height only depends on the row
  for (let y = 0; y < N; y++) { let h = 0; for (let s = 0; s < SS; s++) h += height((y + (s + 0.5) / SS) % PANEL.pitch); H[y] = h / SS; }
  for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
    const o = (y * N + x) * 4, deep = -H[y] / PANEL.depth;   // 0 on the face, 1 on the groove floor
    const k = (1 - 0.6 * deep) * (0.985 + 0.03 * vnoise(x, y, 811, 60));
    PANEL.c.forEach((v, i) => { col[o + i] = Math.round(v * k); }); col[o + 3] = 255;
    sp.set([Math.round(PANEL.sm - (PANEL.sm - PANEL.smGroove) * deep), PANEL.f0, PANEL.po, 255], o);
    const at = j => H[((j % N) + N) % N], hy = (at(y + 1) - at(y - 1)) / 2;
    let ny = -hy; const l = Math.hypot(ny, 1); ny /= l;
    nrm.set([128, Math.round((ny * 0.5 + 0.5) * 255), Math.round(255 - 105 * deep), Math.round(255 + H[y] * 24)], o);
  }
  return [png(col, N, N), png(sp, N, N), png(nrm, N, N)];
}

// ---------------- models (vanilla JSON; px, cell 0..16) ----------------
// coping: the capped panel's top is aluminium; on every side without a panel neighbour a lip 1.6 px (10 cm) high, 0.75 px
// proud, at the top of the face; a corner square where two lips meet. Jamb plate: 5 px (0.31 m) thick on the north edge.
export const COPING = {lip: [14.4, 16], out: 0.75};
export const JAMB = {thick: 5};
const assets = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight');
const T = 'apocalypse_firstlight:block/facade_metal_panel/', ALU = 'apocalypse_firstlight:block/facade_cornice/aluminum_cornice';
const json = v => JSON.stringify(v, null, 2) + '\n';
const f = (uv, texture, cullface) => ({uv, texture, ...(cullface ? {cullface} : {})});
const [L0, L1] = COPING.lip, LH = L1 - L0, O = COPING.out;
const models = {
  metal_wall_panel: {parent: 'minecraft:block/cube_all', textures: {all: `${T}metal_wall_panel`}},
  metal_wall_panel_capped: {parent: 'minecraft:block/block', textures: {particle: `${T}metal_wall_panel`, side: `${T}metal_wall_panel`, top: ALU},
    elements: [{from: [0, 0, 0], to: [16, 16, 16], faces: {
      down: f([0, 0, 16, 16], '#side', 'down'), up: f([0, 0, 16, 16], '#top', 'up'),
      north: f([0, 0, 16, 16], '#side', 'north'), south: f([0, 0, 16, 16], '#side', 'south'),
      west: f([0, 0, 16, 16], '#side', 'west'), east: f([0, 0, 16, 16], '#side', 'east')}}]},
  // the north lip; the ends are hidden by a neighbouring panel's lip (cullface) or by the corner square
  coping_lip: {textures: {particle: ALU, alu: ALU}, elements: [{from: [0, L0, -O], to: [16, L1, 0], faces: {
    north: f([0, 0, 16, LH], '#alu'), up: f([0, 0, 16, O], '#alu'), down: f([0, 0, 16, O], '#alu'),
    west: f([0, 0, O, LH], '#alu', 'west'), east: f([0, 0, O, LH], '#alu', 'east')}}]},
  // the north-west corner square, present when both the north and the west lips are
  coping_corner: {textures: {particle: ALU, alu: ALU}, elements: [{from: [-O, L0, -O], to: [0, L1, 0], faces: {
    north: f([0, 0, O, LH], '#alu'), west: f([0, 0, O, LH], '#alu'), up: f([0, 0, O, O], '#alu'), down: f([0, 0, O, O], '#alu')}}]},
  // jamb plate on the north edge; grooves line up with the panel (rows follow the block height on every side face). The
  // top and bottom (a plate end) sample one plank's plain face, stretched to the 5 px width: a 0..5 px strip crossed a
  // groove and a plank edge, two dark lines along a free-standing plate's top (in game 2026-10-07)
  metal_panel_jamb: {parent: 'minecraft:block/block', textures: {particle: `${T}metal_wall_panel`, panel: `${T}metal_wall_panel`},
    elements: [{from: [0, 0, 0], to: [16, 16, JAMB.thick], faces: {
      north: f([0, 0, 16, 16], '#panel', 'north'), south: f([0, 0, 16, 16], '#panel'),
      west: f([16 - JAMB.thick, 0, 16, 16], '#panel'), east: f([0, 0, JAMB.thick, 16], '#panel'),
      up: f([0, 1, 16, 3.5], '#panel', 'up'), down: f([0, 1, 16, 3.5], '#panel', 'down')}}]},
};
const outputs = [];
panelMaps().forEach((m, i) => outputs.push([path.join(assets, `textures/block/facade_metal_panel/metal_wall_panel${['', '_s', '_n'][i]}.png`), m]));
for (const [k, v] of Object.entries(models)) outputs.push([path.join(assets, `models/block/facade_metal_panel/${k}.json`), json(v)]);
const ROT = {north: 0, east: 90, south: 180, west: 270};
const ref = (m, y) => ({model: T + m, ...(y % 360 ? {y: y % 360} : {})});
{   // MetalWallPanelBlock: cap (nothing of its own above), north / east / south / west (a panel on that side)
  const parts = [{when: {cap: 'false'}, apply: ref('metal_wall_panel', 0)}, {when: {cap: 'true'}, apply: ref('metal_wall_panel_capped', 0)}];
  for (const [F, y] of Object.entries(ROT)) parts.push({when: {cap: 'true', [F]: 'false'}, apply: ref('coping_lip', y)});
  // corner squares: the model is the north-west one; a quarter turn clockwise carries it to north-east, and so on
  for (const [a, b, y] of [['north', 'west', 0], ['north', 'east', 90], ['east', 'south', 180], ['south', 'west', 270]])
    parts.push({when: {cap: 'true', [a]: 'false', [b]: 'false'}, apply: ref('coping_corner', y)});
  outputs.push([path.join(assets, 'blockstates/metal_wall_panel.json'), json({multipart: parts})]);
}
{   // MetalPanelJambBlock: facing (the edge it hugs); eyebrow = none / cw / ccw: a Metal Eyebrow Canopy next to it on its
    // inner side faces the facing's clockwise / counter-clockwise neighbour, and the rest of the cell shows as canopy
    // (models from tools/build-metal-eyebrow-canopy-v1.mjs, in the canopy's frame: plate on the west or the east)
  const CW = {north: 'east', east: 'south', south: 'west', west: 'north'}, CCW = {north: 'west', east: 'north', south: 'east', west: 'south'};
  const EB = 'apocalypse_firstlight:block/facade_eyebrow/', eb = (m, F) => ({model: EB + m, ...(ROT[F] ? {y: ROT[F]} : {})});
  const parts = [];
  for (const [F, y] of Object.entries(ROT)) {
    parts.push({when: {facing: F}, apply: ref('metal_panel_jamb', y)});
    parts.push({when: {facing: F, eyebrow: 'cw'}, apply: eb('jamb_canopy_w', CW[F])});    // the plate is on the canopy's west (its counter-clockwise side)
    parts.push({when: {facing: F, eyebrow: 'ccw'}, apply: eb('jamb_canopy_e', CCW[F])});
  }
  outputs.push([path.join(assets, 'blockstates/metal_panel_jamb.json'), json({multipart: parts})]);
}
outputs.push([path.join(assets, 'models/item/metal_wall_panel.json'), json({parent: `${T}metal_wall_panel`})],
  [path.join(assets, 'models/item/metal_panel_jamb.json'), json({parent: `${T}metal_panel_jamb`})]);

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
