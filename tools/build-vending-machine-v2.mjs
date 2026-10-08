// Vending Machine V2: glass-front snack and drink machine, 1 x 2 blocks, as Pure Mesh + 512 LabPBR atlas, rendered by the
// AFL Animated Block Mesh Runtime (bones 'body', 'coil_0..11', 'glass', 'lights', 'lights_lit'; no animation) and in inventories by
// AflStaticMeshItemRenderer (a broken-glass item without the translucent layer). Replaced the V1 cube model (2026-10-01).
//   node tools/build-vending-machine-v2.mjs                -> writes source, runtime geo / sidecar / profile / maps / item model
//   node tools/build-vending-machine-v2.mjs --check        -> verifies every output is up to date
//   node tools/build-vending-machine-v2.mjs --preview DIR  -> writes only geo / sidecar / maps into DIR (offline review)
// Frame (px): the block's bottom centre at the origin, y 0..32 over both cells, front toward -Z, +X the viewer's LEFT.
// Layout from the front: the glass window and its lanes on the left, the payment column on the right, a blank lightbox
// header, the pickup bin under the window. Charcoal powder-coated steel (coated LabPBR, F0 20), no print or logos.
// The glass ('glass', translucent) is hidden once the glass is broken: an empty frame, no shards (the crowbar's glass
// particles carry the break). Lights (unlit set 'lights', lit set 'lights_lit': LabPBR emissive, neverRender in the geo):
// lightbox face, a LED strip under the window's ceiling, the payment display. Standard AFL power port on the back of the
// lower cell (tools/afl-power-port.mjs). Goods behind the glass are drawn by the renderer (shared goods library), on the
// lane trays below (LANES).
import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {convert, serializeCompact} from './export-afl-mesh.mjs';
import {addIecInlet} from './afl-iec-inlet.mjs';
import {Part, extrude, area2, unwrap, paint, png, zFightLevels} from './cube-slab-mesh-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
function assert(c, m) { if (!c) throw new Error(m); }
const PARTS = [], P = (name, bone, mat) => { const p = new Part(name, bone, mat); PARTS.push(p); return p; };
const orient = (L, ccw) => (area2(L) > 0) === ccw ? L : L.slice().reverse();
const rect = (u0, v0, u1, v1) => [[u0, v0], [u1, v0], [u1, v1], [u0, v1]];
// axis-aligned box [x0, y0, z0] .. [x1, y1, z1] extruded along ax (x: plane (z, y), y: plane (z, x), z: plane (x, y))
function slab(part, ax, a, b, c = 0) {
  const [ku, kv, ka] = ax === 'x' ? [2, 1, 0] : ax === 'y' ? [2, 0, 1] : [0, 1, 2];
  extrude(part, ax, {outer: orient(rect(a[ku], a[kv], b[ku], b[kv]), true), holes: []}, a[ka], b[ka], c);
}
// front frame (extruded along z): outer / holes as [x0, y0, x1, y1]
function zFrame(part, outer, holes, z0, z1, c) {
  extrude(part, 'z', {outer: orient(rect(...outer), true), holes: holes.map(h => orient(rect(...h), false))}, z0, z1, c);
}

// ---------------- dimensions (source px) ----------------
export const W = 7.8, FRONT = -7.8, BACK = 7.7, TOP = 31.8, PLINTH = 1.4, SKIN = 0.5;
export const WINDOW = {x0: -3.4, x1: 7.3, y0: 7.0, y1: 27.2};          // window door frame, outer
export const OPENING = {x0: -2.8, x1: 6.8, y0: 7.6, y1: 26.6};          // its opening (glass and cavity)
export const GLASS_Z = [-7.45, -7.35];
export const CAVITY_BACK = 6.6;
export const LANES = {columns: 3, rows: 4, trayDepth: [-6.6, 6.4], trayThick: 0.3};   // trays across the opening, one per row
export const LANE_W = (OPENING.x1 - OPENING.x0) / LANES.columns;
export const TRAY_TOPS = Array.from({length: LANES.rows}, (_, r) => +(OPENING.y0 + LANES.trayThick + r * (OPENING.y1 - OPENING.y0) / LANES.rows).toFixed(3));
export const LANE_X = Array.from({length: LANES.columns}, (_, c) => +(OPENING.x1 - (c + 0.5) * LANE_W).toFixed(3));   // column 0 on the viewer's left
export const GOODS_FRONT_Z = -6.4;                                        // products' front edge on a tray
export const GOODS_SCALE = 0.76;                                          // client/VendingMachineRenderer: the nominal 4 x 4.6 x 8 px goods cell
export const GOODS_BACK_Z = GOODS_FRONT_Z + 8 * GOODS_SCALE;              // the products' back
// the front of each lane's coil, where the products stand, is its own bone ('coil_<lane>', lane = row * 3 + column, row 0
// at the bottom, column 0 on the viewer's left): the products are wider than the coil, so the block entity hides it
// while the lane shows goods; the rest of the coil (behind the products) stays in 'body'
export const COIL_BONES = Array.from({length: LANES.rows * LANES.columns}, (_, lane) => 'coil_' + lane);
export const PAY = {x0: -7.3, x1: -3.4};                                  // payment column
export const BIN = {x0: -2.6, x1: 6.6, y0: 2.6, y1: 6.2, depth: -4.2};
export const HEADER = {y0: 27.2, face: [-7.0, 27.7, 7.0, 30.8]};
// power inlet (px): its centre on the back panel (z 7.7), low at the viewer's left corner (VendingMachineBlockEntity#cordGeometry)
export const CORD = {x: 6.0, y: 2.4, z: 7.7};

{
  // carcass: sides, back, top, floor, plinth
  const coat = P('carcass', 'body', 'coat');
  slab(coat, 'x', [W - SKIN, PLINTH, FRONT], [W, TOP, BACK], 0.12);
  slab(coat, 'x', [-W, PLINTH, FRONT], [-W + SKIN, TOP, BACK], 0.12);
  extrude(coat, 'z', {outer: orient(rect(-W + SKIN, PLINTH, W - SKIN, TOP - SKIN), true), holes: []},
    BACK - SKIN, BACK, 0);
  slab(coat, 'y', [-W + SKIN, TOP - SKIN, FRONT], [W - SKIN, TOP, BACK - SKIN], 0.12);
  slab(coat, 'y', [-W + SKIN, PLINTH, FRONT + SKIN], [W - SKIN, PLINTH + 0.5, BACK - SKIN], 0);
  slab(P('plinth', 'body', 'plinth'), 'y', [-W + 0.4, 0, FRONT + 0.5], [W - 0.4, PLINTH, BACK - 0.3], 0);
  // front skin: header with the lightbox recess, payment column, bin panel (with the bin opening)
  const skin = P('front', 'body', 'coat');
  zFrame(skin, [-W + SKIN, HEADER.y0, W - SKIN, TOP - SKIN], [HEADER.face], FRONT, FRONT + SKIN, 0.08);
  slab(skin, 'z', [PAY.x0, PLINTH + 0.5, FRONT], [PAY.x1, HEADER.y0, FRONT + SKIN], 0.08);
  zFrame(skin, [PAY.x1, PLINTH + 0.5, W - SKIN, WINDOW.y0], [[BIN.x0, BIN.y0, BIN.x1, BIN.y1]], FRONT, FRONT + SKIN, 0.08);
  slab(P('lightbox_back', 'body', 'dark'), 'z', [HEADER.face[0], HEADER.face[1], FRONT + 0.3], [HEADER.face[2], HEADER.face[3], FRONT + SKIN + 0.2], 0);
  // window door: frame around the opening, a pull edge on the payment side
  zFrame(P('window_frame', 'body', 'trim'), [WINDOW.x0, WINDOW.y0, WINDOW.x1, WINDOW.y1], [[OPENING.x0, OPENING.y0, OPENING.x1, OPENING.y1]],
    FRONT - 0.05, FRONT + 0.65, 0.12);
  slab(P('window_lock', 'body', 'zinc'), 'z', [WINDOW.x0 + 0.15, 16.0, FRONT - 0.2], [WINDOW.x0 + 0.45, 18.4, FRONT - 0.05], 0);
  // cavity behind the glass: dark liner (back, sides, ceiling, floor)
  const liner = P('liner', 'body', 'liner');
  slab(liner, 'z', [OPENING.x0 - 0.6, OPENING.y0 - 0.6, CAVITY_BACK], [OPENING.x1 + 0.5, OPENING.y1 + 0.6, CAVITY_BACK + 0.3], 0);
  slab(liner, 'x', [OPENING.x1, OPENING.y0, FRONT + 0.65], [OPENING.x1 + 0.5, OPENING.y1, CAVITY_BACK], 0);
  slab(liner, 'x', [OPENING.x0 - 0.6, OPENING.y0, FRONT + 0.65], [OPENING.x0, OPENING.y1, CAVITY_BACK], 0);
  slab(liner, 'y', [OPENING.x0, OPENING.y1, FRONT + 0.65], [OPENING.x1, OPENING.y1 + 0.6, CAVITY_BACK], 0);
  slab(liner, 'y', [OPENING.x0, OPENING.y0 - 0.6, FRONT + 0.65], [OPENING.x1, OPENING.y0, CAVITY_BACK], 0);
  // lanes: one tray per row, a price strip along its front (no print), a spiral per lane (radial ribbons, both sides)
  const tray = P('trays', 'body', 'tray'), strip = P('price_strips', 'body', 'label'), rear = P('coils', 'body', 'wire');
  const R = 1.15, RIB = 0.18, PITCH = 2.0, SEG = 8, [cz0, cz1] = [-6.2, 5.8], steps = Math.round((cz1 - cz0) / PITCH * SEG);
  const split = Math.ceil((GOODS_BACK_Z - cz0) / (cz1 - cz0) * steps);   // the first step behind the products
  assert(split > 0 && split < steps && cz0 <= GOODS_FRONT_Z + 0.5, 'coil split');
  TRAY_TOPS.forEach((top, row) => {
    slab(tray, 'y', [OPENING.x0, top - LANES.trayThick, LANES.trayDepth[0]], [OPENING.x1, top, LANES.trayDepth[1]], 0);
    slab(strip, 'z', [OPENING.x0 + 0.05, top - 0.85, LANES.trayDepth[0] - 0.3], [OPENING.x1 - 0.05, top + 0.12, LANES.trayDepth[0]], 0);
    LANE_X.forEach((x, column) => {
      const bone = COIL_BONES[row * LANES.columns + column], front = P(bone, bone, 'wire'), cy = top + R + 0.02;
      const at = (part, i, r) => { const a = 2 * Math.PI * i / SEG; return part.vtx([x + r * Math.cos(a), cy + r * Math.sin(a), cz0 + (cz1 - cz0) * i / steps]); };
      for (let i = 0; i < steps; i++) {
        const coil = i < split ? front : rear, a = 2 * Math.PI * (i + 0.5) / SEG, n = [-Math.sin(a), Math.cos(a), 0];   // ribbon faces along the turn
        coil.face([at(coil, i, R - RIB / 2), at(coil, i, R + RIB / 2), at(coil, i + 1, R + RIB / 2), at(coil, i + 1, R - RIB / 2)], n, 'side');
      }
    });
  });
  // payment column: display window (lights), keypad, coin slot plate, card slot, coin return
  const keys = P('keypad', 'body', 'key');
  for (let r = 0; r < 4; r++) for (let c = 0; c < 3; c++) {
    const x = -6.4 + c * 1.0, y = 17.4 + r * 0.95;
    slab(keys, 'z', [x - 0.35, y, FRONT - 0.12], [x + 0.35, y + 0.6, FRONT], 0);
  }
  slab(P('coin_plate', 'body', 'zinc'), 'z', [-6.6, 14.9, FRONT - 0.08], [-4.2, 16.4, FRONT], 0);
  slab(P('coin_slot', 'body', 'slot'), 'z', [-5.55, 15.2, FRONT - 0.12], [-5.25, 16.1, FRONT - 0.07], 0);
  slab(P('card_reader', 'body', 'dark'), 'z', [-6.6, 12.8, FRONT - 0.1], [-4.2, 14.2, FRONT], 0.05);
  slab(P('card_slot', 'body', 'slot'), 'z', [-6.3, 13.9, FRONT - 0.14], [-4.5, 14.0, FRONT - 0.09], 0);
  zFrame(P('coin_return', 'body', 'zinc'), [-6.3, 9.6, -4.5, 11.2], [[-6.0, 9.9, -4.8, 10.9]], FRONT - 0.1, FRONT + 0.15, 0);
  slab(P('coin_return_cup', 'body', 'slot'), 'z', [-6.0, 9.9, FRONT + 0.15], [-4.8, 10.9, FRONT + 0.8], 0);
  // pickup bin: dark recess behind the opening, a push flap set back in it with a grip lip
  const bin = P('bin', 'body', 'slot');
  slab(bin, 'y', [BIN.x0, BIN.y0 - 0.3, FRONT + SKIN], [BIN.x1, BIN.y0, BIN.depth], 0);
  slab(bin, 'z', [BIN.x0, BIN.y0 - 0.3, BIN.depth], [BIN.x1, BIN.y1, BIN.depth + 0.3], 0);
  slab(bin, 'x', [BIN.x0 - 0.3, BIN.y0 - 0.3, FRONT + SKIN], [BIN.x0, BIN.y1, BIN.depth + 0.3], 0);
  slab(bin, 'x', [BIN.x1, BIN.y0 - 0.3, FRONT + SKIN], [BIN.x1 + 0.3, BIN.y1, BIN.depth + 0.3], 0);
  slab(bin, 'y', [BIN.x0, BIN.y1, FRONT + SKIN], [BIN.x1, BIN.y1 + 0.3, BIN.depth], 0);
  slab(P('bin_flap', 'body', 'flap'), 'z', [BIN.x0 + 0.1, BIN.y0 + 0.4, FRONT + 0.75], [BIN.x1 - 0.1, BIN.y1 - 0.1, FRONT + 0.95], 0.05);
  slab(P('bin_lip', 'body', 'zinc'), 'z', [BIN.x0 + 0.8, BIN.y0 + 0.4, FRONT + 0.55], [BIN.x1 - 0.8, BIN.y0 + 0.75, FRONT + 0.75], 0);
  // power inlet: IEC C14 (tools/afl-iec-inlet.mjs; Power Outlets V1, 2026-10-08), the detachable cord's C13 connector plugs in here
  addIecInlet({housing: P('inlet_housing', 'body', 'inlet'), floor: P('inlet_floor', 'body', 'socket'), pin: P('inlet_pins', 'body', 'inlet_pin')}, CORD.x, CORD.y, CORD.z);
}
// glass: one pane in the opening, its own bone (hidden when broken)
slab(P('glass_pane', 'glass', 'glass'), 'z', [OPENING.x0 - 0.05, OPENING.y0 - 0.05, GLASS_Z[0]], [OPENING.x1 + 0.05, OPENING.y1 + 0.05, GLASS_Z[1]], 0);
// lights: the same geometry twice (unlit / lit)
function lights(bone, k) {
  slab(P('lightbox' + k, bone, 'lightbox' + k), 'z', [HEADER.face[0] + 0.05, HEADER.face[1] + 0.05, FRONT + 0.15], [HEADER.face[2] - 0.05, HEADER.face[3] - 0.05, FRONT + 0.28], 0);
  slab(P('led_strip' + k, bone, 'led' + k), 'y', [OPENING.x0 + 0.3, OPENING.y1 - 0.35, FRONT + 0.9], [OPENING.x1 - 0.3, OPENING.y1 - 0.02, FRONT + 1.25], 0);
  slab(P('display' + k, bone, 'screen' + k), 'z', [-6.9, 22.6, FRONT - 0.06], [-3.9, 24.2, FRONT], 0);
}
lights('lights', '');
lights('lights_lit', '_lit');

export const MATS = {   // Base Color, bevel highlight, smoothness open / edge, F0 (coated / dielectric; glass F0 10)
  coat:         {c: [44, 47, 52], hl: 8, sm: 100, se: 120, f0: 20},     // charcoal powder coat (the beverage cooler's)
  trim:         {c: [36, 38, 42], hl: 10, sm: 112, se: 132, f0: 20},
  plinth:       {c: [30, 31, 33], hl: 2, sm: 60, se: 64, f0: 20},
  dark:         {c: [24, 25, 28], hl: 2, sm: 80, se: 90, f0: 20},
  liner:        {c: [58, 62, 68], hl: 4, sm: 96, se: 110, f0: 20},
  tray:         {c: [112, 116, 120], hl: 8, sm: 120, se: 140, f0: 20},
  label:        {c: [150, 152, 148], hl: 4, sm: 90, se: 100, f0: 20},
  wire:         {c: [168, 170, 172], hl: 0, sm: 150, se: 150, f0: 20},
  key:          {c: [150, 154, 158], hl: 10, sm: 120, se: 136, f0: 20},
  zinc:         {c: [138, 142, 146], hl: 12, sm: 130, se: 150, f0: 20},
  slot:         {c: [18, 19, 21], hl: 0, sm: 60, se: 60, f0: 20},
  flap:         {c: [52, 55, 60], hl: 6, sm: 104, se: 120, f0: 20},
  glass:        {c: [150, 186, 204], hl: 0, sm: 235, se: 235, f0: 10},
  lightbox:     {c: [116, 120, 122], hl: 0, sm: 140, se: 140, f0: 20},  // blank light panel, off
  lightbox_lit: {c: [196, 200, 204], hl: 0, sm: 150, se: 150, f0: 20},
  led:          {c: [70, 72, 76], hl: 0, sm: 160, se: 160, f0: 20},
  led_lit:      {c: [220, 226, 232], hl: 0, sm: 180, se: 180, f0: 20},
  screen:       {c: [16, 20, 22], hl: 0, sm: 200, se: 200, f0: 20},
  screen_lit:   {c: [30, 70, 80], hl: 0, sm: 200, se: 200, f0: 20},
  port:         {c: [134, 138, 144], hl: 12, sm: 112, se: 130, f0: 20},
  socket:       {c: [24, 25, 28], hl: 0, sm: 60, se: 60, f0: 20},
  port_pin:     {c: [40, 42, 46], hl: 10, sm: 110, se: 130, f0: 20},
  inlet:          {c: [22, 23, 25], hl: 8, sm: 112, se: 132, f0: 20},
  inlet_pin:      {c: [176, 172, 160], hl: 10, sm: 172, se: 188, f0: 255},
};
export const GLASS_ALPHA = 56;
export const EMISSION = {lightbox_lit: 180, led_lit: 230, screen_lit: 140};

const ATLAS = 512, PAD = 2;
const UV = unwrap(PARTS, {atlas: ATLAS, pad: PAD, startS: 16, stepS: 0.25});
for (const p of PARTS) assert(p.f.every(f => UV.faceUV.get(p)?.has(f)), 'unmapped face in ' + p.name);
const DUMMY = 'data:image/png;base64,' + png(Buffer.from([0, 0, 0, 255]), 1, 1).toString('base64');
const painted = paint({PARTS, islands: UV.islands, S: UV.S, uvOf: UV.uvOf, atlas: ATLAS, pad: PAD, MATS, ZONED: new Set(), groupInfo: new Map(),
  sourceGroups: () => [], refTexture: {source: DUMMY}, refUvWidth: 1, background: {c: [...MATS.coat.c, 255], s: [100, 20, 0, 255], n: [128, 128, 255, 255]}});
import {readPng} from './cube-slab-mesh-lib.mjs';
const MAPS = painted.PNG.map(b => { const r = readPng(b); assert(r.bpp === 4 && r.w === ATLAS, 'atlas format'); return r.px; });
for (const is of UV.islands) {
  const glass = is.part.mat === 'glass', emission = EMISSION[is.part.mat];
  if (!glass && emission === undefined) continue;
  for (let y = is.py - PAD; y < is.py + is.H + PAD; y++) for (let x = is.px - PAD; x < is.px + is.W + PAD; x++) {
    if (glass) MAPS[0][(y * ATLAS + x) * 4 + 3] = GLASS_ALPHA;
    else MAPS[1][(y * ATLAS + x) * 4 + 3] = emission;
  }
}
const PNGS = MAPS.map(px => png(px, ATLAS, ATLAS));

// ---------------- source (Free Model) ----------------
const ID = 'vending_machine';
const uuid = s => { const h = createHash('sha256').update('afl-vending-machine-v2:' + s).digest('hex'); return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-8${h.slice(17, 20)}-${h.slice(20, 32)}`; };
const r12 = v => +v.toFixed(12) || 0;
const RIG = ['body', ...COIL_BONES, 'glass', 'lights', 'lights_lit'];
const source = (() => {
  const groups = RIG.map(name => ({name, uuid: uuid('group:' + name), export: true, locked: false, scope: 0, selected: false, visibility: true,
    _static: {properties: {}, temp_data: {}}, origin: [0, 0, 0], rotation: [0, 0, 0], color: 0, children: [], reset: false, shade: true,
    mirror_uv: false, autouv: 0, isOpen: true, primary_selected: false}));
  const nodes = new Map(RIG.map(name => [name, {uuid: uuid('group:' + name), isOpen: true, children: []}]));
  const elements = [];
  for (const p of PARTS) {
    const key = i => i.toString(36).padStart(4, '0'), vertices = {}, faces = {}, uvs = UV.faceUV.get(p);
    p.v.forEach((q, i) => { vertices[key(i)] = q.map(r12); });
    p.f.forEach((f, fi) => { const uv = uvs.get(f); faces['f' + key(fi)] = {uv: Object.fromEntries(f.ids.map((id, j) => [key(id), uv[j].map(r12)])), vertices: f.ids.map(key), texture: 0}; });
    const id = uuid('mesh:' + p.name);
    elements.push({name: p.name, color: 0, origin: [0, 0, 0], rotation: [0, 0, 0], shading: 'flat', export: true, visibility: true, locked: false,
      render_order: 'default', scope: 0, allow_mirror_modeling: true, vertices, faces, type: 'mesh', uuid: id});
    nodes.get(p.bone).children.push(id);
  }
  return {meta: {format_version: '5.0', model_format: 'free', box_uv: false}, name: ID, model_identifier: '', visible_box: [1, 2, 0],
    variable_placeholders: '', variable_placeholder_buttons: [], timeline_setups: [], unhandled_root_fields: {}, resolution: {width: ATLAS, height: ATLAS},
    elements, groups, outliner: RIG.map(name => nodes.get(name)),
    textures: [{name: ID + '.png', relative_path: `textures/${ID}.png`, folder: '', namespace: '', id: '0', group: '', scope: 0,
      width: ATLAS, height: ATLAS, uv_width: ATLAS, uv_height: ATLAS, particle: false, use_as_default: false, layers_enabled: false, sync_to_project: '',
      file_format: 'png', render_mode: 'default', render_sides: 'auto', wrap_mode: 'limited', pbr_channel: 'color', fps: 7, frame_time: 1,
      frame_order_type: 'loop', frame_order: '', frame_interpolate: false, visible: true, internal: true, saved: true, uuid: uuid('texture'),
      source: 'data:image/png;base64,' + PNGS[0].toString('base64')}],
    animations: []};
})();

// ---------------- runtime ----------------
// one geo for the block and both items: the broken-glass item skips the translucent layer (the glass) in AflStaticMeshItemRenderer
const geo = {format_version: '1.12.0', 'minecraft:geometry': [{description: {identifier: 'geometry.' + ID, texture_width: ATLAS,
  texture_height: ATLAS, visible_bounds_width: 2, visible_bounds_height: 3, visible_bounds_offset: [0, 1, 0]},
  bones: RIG.map(name => name === 'lights_lit' ? {name, pivot: [0, 0, 0], neverRender: true} : {name, pivot: [0, 0, 0]})}]};
const LAYERS = {glass_pane: 'translucent'};
const sidecar = convert(source, geo, {}, ID + '.bbmodel', 2, null, LAYERS);
const meshText = serializeCompact(sidecar);

const r3 = v => +v.toFixed(3) || 0, r6 = v => +v.toFixed(6) || 0;
const aabbOf = pts => [0, 1, 2].map(k => Math.min(...pts.map(q => q[k]))).concat([0, 1, 2].map(k => Math.max(...pts.map(q => q[k]))));
const bounds = (() => { const b = aabbOf(PARTS.flatMap(p => p.v)), m = 0.25;
  return [(b[0] - m + 8) / 16, (b[1] - m) / 16, (b[2] - m + 8) / 16, (b[3] + m + 8) / 16, (b[4] + m) / 16, (b[5] + m + 8) / 16].map(r6); })();
const profile = {format_version: 1, geometry: `apocalypse_firstlight:geo/${ID}.geo.json`,
  texture: `apocalypse_firstlight:textures/block/${ID}.png`, origin: [0, 0, 0], scale: [1, 1, 1], facing: 'horizontal', bounds,
  parts: Object.fromEntries(RIG.map(name => [name, {pivot: [0, 0, 0]}]))};

// item model: builtin/entity; every view centred on the machine (its middle sits 8 px above the item model's centre)
const itemDisplay = (() => {
  const rot = ([ax, ay]) => q => { const a = ay * Math.PI / 180, b = ax * Math.PI / 180, x = q[0] * Math.cos(a) + q[2] * Math.sin(a), z = -q[0] * Math.sin(a) + q[2] * Math.cos(a);
    return [x, q[1] * Math.cos(b) - z * Math.sin(b), q[1] * Math.sin(b) + z * Math.cos(b)]; };
  const pts = PARTS.filter(p => p.bone !== 'lights_lit').flatMap(p => p.v).map(q => [q[0], q[1] - 8, q[2]]), S3 = v => [v, v, v];
  const view = (r, fit, extra = [0, 0, 0]) => { const R = pts.map(rot(r)), xs = R.map(q => q[0]), ys = R.map(q => q[1]), zs = R.map(q => q[2]);
    const s = typeof fit === 'number' && fit > 1 ? r3(fit / Math.max(Math.max(...xs) - Math.min(...xs), Math.max(...ys) - Math.min(...ys))) : fit;
    const c = [(Math.max(...xs) + Math.min(...xs)) / 2, (Math.max(...ys) + Math.min(...ys)) / 2, (Math.max(...zs) + Math.min(...zs)) / 2];
    return {rotation: [r[0], r[1], 0], translation: [r3(-s * c[0] + extra[0]), r3(-s * c[1] + extra[1]), r3(-s * c[2] + extra[2])], scale: S3(s)}; };
  // the front (-Z) toward the viewer like a vanilla block item: gui / hands at 225 deg, item frame at 0
  return {gui: view([25, 225], 15), ground: view([0, 0], 0.25, [0, 3, 0]), fixed: view([0, 0], 0.45),
    thirdperson_righthand: view([75, 225], 0.28, [0, 2.5, 0]), firstperson_righthand: view([0, 225], 0.32, [0, 1, 0])};
})();
const itemModel = {parent: 'builtin/entity', gui_light: 'side', textures: {particle: `apocalypse_firstlight:block/${ID}`}, display: itemDisplay};
const blockModel = {loader: 'apocalypse_firstlight:static_mesh', textures: {particle: `apocalypse_firstlight:block/${ID}`}};

const bb = path.join(ROOT, 'src/main/blockbench'), assets = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight');
const outputs = [[path.join(bb, ID + '.bbmodel'), JSON.stringify(source)],
  [path.join(assets, `geo/${ID}.geo.json`), JSON.stringify(geo, null, 2) + '\n'], [path.join(assets, `meshes/${ID}.aflmesh.json`), meshText],
  [path.join(assets, `block_mesh_profiles/${ID}.json`), JSON.stringify(profile, null, 2) + '\n'],
  [path.join(assets, `models/item/${ID}.json`), JSON.stringify(itemModel, null, 2) + '\n'], [path.join(assets, `models/block/${ID}.json`), JSON.stringify(blockModel, null, 2) + '\n'],
  ...['', '_s', '_n'].flatMap((k, i) => [[path.join(bb, `textures/${ID}${k}.png`), PNGS[i]], [path.join(assets, `textures/block/${ID}${k}.png`), PNGS[i]]])];

const zf = zFightLevels(PARTS, new Map(), {skip: p => p.bone === 'lights_lit'});
const trisOf = p => p.f.reduce((t, f) => t + f.ids.length - 2, 0);
export const stats = {triangles: sidecar.parts.flatMap(p => p.faces).reduce((s, q) => s + q.length - 2, 0), parts: sidecar.parts.length, texelsPerPx: UV.S,
  coplanarOverlaps: zf.unresolved.length, bounds, closed: aabbOf(PARTS.flatMap(p => p.v)).map(r3), gui: itemDisplay.gui,
  byBone: Object.fromEntries(RIG.map(b => [b, PARTS.filter(p => p.bone === b).reduce((s, p) => s + trisOf(p), 0)])),
  coils: PARTS.filter(p => p.mat === 'wire').reduce((s, p) => s + trisOf(p), 0), lanes: {x: LANE_X, trayTops: TRAY_TOPS, width: +LANE_W.toFixed(3)}};
const isMain = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  console.log(JSON.stringify(stats));
  if (zf.unresolved.length) console.log('COPLANAR', JSON.stringify(zf.unresolved.slice(0, 12)));
  const pi = process.argv.indexOf('--preview');
  if (pi > 0) {
    const dir = process.argv[pi + 1]; fs.mkdirSync(dir, {recursive: true});
    fs.writeFileSync(path.join(dir, `${ID}.geo.json`), JSON.stringify(geo, null, 2) + '\n');
    fs.writeFileSync(path.join(dir, `${ID}.aflmesh.json`), meshText);
    ['', '_s', '_n'].forEach((k, i) => fs.writeFileSync(path.join(dir, `${ID}${k}.png`), PNGS[i]));
    console.log('preview written to ' + dir);
  } else if (process.argv.includes('--check')) {
    for (const [file, data] of outputs) { const cur = fs.existsSync(file) ? fs.readFileSync(file) : null; if (!cur || !cur.equals(Buffer.isBuffer(data) ? data : Buffer.from(data))) throw new Error('stale ' + path.relative(ROOT, file)); }
    console.log('CHECK OK');
  } else {
    for (const [file] of outputs) fs.mkdirSync(path.dirname(file), {recursive: true});
    for (const [file, data] of outputs) fs.writeFileSync(file, data);
    console.log('wrote ' + outputs.map(([f]) => path.relative(ROOT, f)).join(', '));
  }
}
