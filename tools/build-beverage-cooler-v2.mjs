// Beverage Cooler V2: two-door glass merchandiser (2 wide x 2 tall) as Pure Mesh + 512 LabPBR atlas, rendered by the AFL
// Animated Block Mesh Runtime (bones 'body', 'left_door', 'right_door'; channels 'left_open' / 'right_open') and in
// inventories by AflStaticMeshItemRenderer. The door logic, cells and shapes stay in BeverageCoolerBlock (V1 runtime).
//   node tools/build-beverage-cooler-v2.mjs                 -> writes source, runtime geo / sidecar / profile / maps / item model
//   node tools/build-beverage-cooler-v2.mjs --check         -> verifies every output is up to date
//   node tools/build-beverage-cooler-v2.mjs --preview DIR   -> writes only geo / sidecar / maps into DIR (offline review)
// Frame: geometry is written in the V1 source units (px, X -8..24 across both columns, Y 0..32, front toward -Z, door
// hinges on the outer edges, left = the viewer's left = +X) and moved by X -16 on output, so the runtime origin is the
// bottom centre of the master (lower-left) cell; the second column lies toward -X (FACING's counter-clockwise side).
// Look kept from V1: charcoal cabinet, blue lightbox header, louvred base, white liner, five wire shelves, two
// aluminium-framed glass doors with pull handles on the meeting stiles. Wire decks are one cutout-textured quad pair each
// instead of 44 wire cubes; the glass is a translucent part (red dot lens recipe: smoothness 235, F0 10).
import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {convert, serializeCompact} from './export-afl-mesh.mjs';
import {Part, AX, extrude, mul, area2, unwrap, paint, png, readPng, zFightLevels} from './cube-slab-mesh-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
function assert(c, m) { if (!c) throw new Error(m); }
const PARTS = [], P = (name, bone, mat) => { const p = new Part(name, bone, mat); PARTS.push(p); return p; };
const orient = (L, ccw) => (area2(L) > 0) === ccw ? L : L.slice().reverse();
const rect = (u0, v0, u1, v1) => [[u0, v0], [u1, v0], [u1, v1], [u0, v1]];

// ---------------- dimensions (V1 source units) ----------------
export const SHIFT = -16;                                                     // source X -> master-centred X
export const SHELF_TOPS = [4.16, 9.01, 13.86, 18.71, 23.56];
export const DECK = {x0: -6.15, x1: 22.15, z0: -4.4, z1: 4.5};               // wire deck between the rails
export const HINGE = {right: [-6.87, 16, -7.495], left: [22.87, 16, -7.495]};
export const DOOR_DEGREES = {left: -95, right: 95}, DOOR_TICKS = 8;            // BeverageCoolerBlock.ANIMATION_TICKS

// ---------------- primitives ----------------
function slab(part, ax, a, b, c = 0) {
  const [ku, kv, ka] = ax === 'x' ? [2, 1, 0] : ax === 'y' ? [2, 0, 1] : [0, 1, 2];
  extrude(part, ax, {outer: orient(rect(a[ku], a[kv], b[ku], b[kv]), true), holes: []}, a[ka], b[ka], c);
}
function frame(part, outer, hole, z0, z1, c) {   // front-facing plate with a rectangular opening, extruded along z
  extrude(part, 'z', {outer: orient(rect(...outer), true), holes: [orient(rect(...hole), false)]}, z0, z1, c);
}
function cyl(part, ax, cu, cv, r, a0, a1, seg) {
  const A = AX[ax], ang = i => Math.PI / seg + 2 * Math.PI * i / seg;
  const rg = a => Array.from({length: seg}, (_, i) => part.vtx(A.to3(cu + r * Math.cos(ang(i)), cv + r * Math.sin(ang(i)), a)));
  const r0 = rg(a0), r1 = rg(a1);
  for (let i = 0; i < seg; i++) { const j = (i + 1) % seg, m = (ang(i) + ang(j)) / 2;
    part.face([r0[i], r0[j], r1[j], r1[i]], A.to3(Math.cos(m), Math.sin(m), 0), 'side'); }
  for (const [ring_, a, s] of [[r0, a0, -1], [r1, a1, 1]]) { const c = part.vtx(A.to3(cu, cv, a));
    for (let i = 0; i < seg; i++) part.face([c, ring_[i], ring_[(i + 1) % seg]], mul(A.n, s), 'cap'); }
}

// ---------------- body ----------------
{
  const cab = P('cabinet', 'body', 'cabinet');
  slab(cab, 'x', [-8, 0.4, -7.35], [-6.95, 32, 8], 0.25);                    // side walls (with the front posts)
  slab(cab, 'x', [22.95, 0.4, -7.35], [24, 32, 8], 0.25);
  slab(cab, 'z', [-6.95, 0.4, 6.8], [22.95, 32, 8], 0);                      // rear wall
  slab(cab, 'y', [-6.95, 31.7, -6.8], [22.95, 32, 6.8], 0);                  // roof over the lightbox housing
  slab(cab, 'y', [-6.95, 0.4, -6.8], [22.95, 0.7, 6.8], 0);                  // base plate
  frame(P('header_frame', 'body', 'cabinet'), [-6.95, 28.9, 22.95, 32], [-6, 29.55, 22, 31.35], -7.35, -6.8, 0.1);
  slab(P('lightbox', 'body', 'lightbox'), 'z', [-6, 29.55, -6.98], [22, 31.35, -6.88], 0);
  frame(P('grille_frame', 'body', 'cabinet'), [-6.95, 0.4, 22.95, 3.5], [-6.55, 0.7, 22.55, 3.18], -7.35, -6.8, 0.1);
  slab(P('grille_back', 'body', 'grille_dark'), 'z', [-6.55, 0.7, -7.0], [22.55, 3.18, -6.9], 0);
  const louvers = P('louvers', 'body', 'grille');
  for (let i = 0; i < 7; i++) slab(louvers, 'z', [-6.45, 0.86 + 0.31 * i, -7.31], [22.45, 1.01 + 0.31 * i, -7.06], 0);
  slab(P('thermostat', 'body', 'trim'), 'z', [19.75, 2.64, -7.52], [22.15, 3.15, -7.33], 0);
  slab(P('thermostat_display', 'body', 'display'), 'z', [20.0, 2.74, -7.55], [21.2, 3.03, -7.51], 0);
  const feet = P('feet', 'body', 'rubber');
  for (const x of [-6.55, 22.55]) for (const z of [-5.47, 6.42]) slab(feet, 'y', [x - 0.8, 0, z - 0.82], [x + 0.8, 0.4, z + 0.82], 0);
  // back: service panel with a louvred vent
  slab(P('service_panel', 'body', 'trim'), 'z', [-6, 1, 8.0], [22, 8, 8.06], 0);
  const rear = P('rear_louvers', 'body', 'grille');
  for (let i = 0; i < 6; i++) slab(rear, 'z', [-4.8, 2.25 + 0.58 * i, 8.06], [3.8, 2.5 + 0.58 * i, 8.1], 0);
  // white liner, rear air duct, shelf standards, corner LED strips, ceiling light
  const liner = P('liner', 'body', 'liner');
  slab(liner, 'x', [-6.95, 3.5, -6.8], [-6.7, 28.9, 6.8], 0);
  slab(liner, 'x', [22.7, 3.5, -6.8], [22.95, 28.9, 6.8], 0);
  slab(liner, 'y', [-6.7, 28.6, -6.8], [22.7, 28.9, 6.35], 0);
  slab(liner, 'y', [-6.7, 3.5, -6.8], [22.7, 3.65, 6.35], 0);
  slab(liner, 'z', [-6.7, 3.65, 6.35], [22.7, 28.6, 6.8], 0);
  const fittings = P('liner_fittings', 'body', 'fitting');
  slab(fittings, 'z', [7.15, 4.0, 6.14], [8.85, 28.3, 6.35], 0);
  for (const x of [-5.85, 21.85]) slab(fittings, 'z', [x - 0.15, 3.7, 6.06], [x + 0.15, 28.4, 6.35], 0);
  for (const [x0, x1] of [[-6.7, -6.28], [22.28, 22.7]]) slab(fittings, 'x', [x0, 4.05, -5.95], [x1, 28.35, -5.35], 0);
  slab(fittings, 'y', [-0.5, 28.38, -4.0], [16.5, 28.6, -1.9], 0);
  const leds = P('led_diffusers', 'body', 'led');
  slab(leds, 'x', [-6.28, 4.2, -5.9], [-6.22, 28.2, -5.4], 0);
  slab(leds, 'x', [22.22, 4.2, -5.9], [22.28, 28.2, -5.4], 0);
  slab(leds, 'y', [0, 28.32, -3.7], [16, 28.38, -2.2], 0);
  // shelves: rails, cross supports, label channel; the deck is a cutout-textured quad pair (own atlas strip)
  const rails = P('shelf_rails', 'body', 'rail'), labels = P('shelf_labels', 'body', 'label'), decks = P('wire_decks', 'body', 'wire');
  for (const T of SHELF_TOPS) {
    slab(rails, 'z', [-6.5, T - 0.36, -5.0], [22.5, T, -4.4], 0);
    slab(rails, 'z', [-6.5, T - 0.36, 4.5], [22.5, T, 5.1], 0);
    for (const [x0, x1] of [[-6.5, -6.15], [22.15, 22.5]]) slab(rails, 'x', [x0, T - 0.36, -4.4], [x1, T, 4.5], 0);
    for (const z of [-2.71, 0.19, 2.99]) slab(rails, 'z', [DECK.x0, T - 0.26, z - 0.09], [DECK.x1, T - 0.12, z + 0.09], 0);
    slab(labels, 'z', [-6.2, T - 0.41, -5.09], [22.2, T - 0.14, -5.0], 0);
    for (const [y, s] of [[T - 0.02, 1], [T - 0.1, -1]]) {
      const ids = [[DECK.x0, DECK.z0], [DECK.x1, DECK.z0], [DECK.x1, DECK.z1], [DECK.x0, DECK.z1]].map(([x, z]) => decks.vtx([x, y, z]));
      decks.face(ids, [0, s, 0], 'cap');
    }
  }
}
// ---------------- doors: aluminium frame, glass, hinge barrels, pull handle (left = right mirrored about X 8) ----------------
for (const side of ['right', 'left']) {
  const mx = side === 'right' ? x => x : x => 16 - x, X = (a, b) => [Math.min(mx(a), mx(b)), Math.max(mx(a), mx(b))];
  const bone = side + '_door', [fx0, fx1] = X(-6.92, 7.94), [hx0, hx1] = X(-6.28, 7.30);
  frame(P(`${side}_door_frame`, bone, 'door'), [fx0, 3.58, fx1, 28.75], [hx0, 4.18, hx1, 28.15], -7.38, -6.9, 0.08);
  const [gx0, gx1] = X(-6.26, 7.28);
  slab(P(`${side}_door_glass`, bone, 'glass'), 'z', [gx0, 4.2, -7.16], [gx1, 28.13, -7.08], 0);
  const hw = P(`${side}_door_hardware`, bone, 'metal');
  for (const [y0, y1] of [[4.6, 5.85], [26.6, 27.85]]) cyl(hw, 'y', HINGE[side][2], HINGE[side][0], 0.13, y0, y1, 8);
  const [mx0, mx1] = X(7.35, 7.93);
  for (const y of [13.1, 19.1]) slab(hw, 'z', [mx0, y, -7.75], [mx1, y + 0.45, -7.37], 0);
  cyl(hw, 'y', -7.895, mx(7.64), 0.17, 13.15, 19.5, 8);
}
for (const p of PARTS) p.v = p.v.map(q => [q[0] + SHIFT, q[1], q[2]]);

// ---------------- Base Color ----------------
export const MATS = {   // Base Color, bevel highlight, smoothness open / edge, F0 (LabPBR: < 230 dielectric, 230+ metal, 255 albedo metal)
  cabinet:     {c: [44, 47, 52], hl: 10, sm: 96, se: 118, f0: 20},      // charcoal powder-coated steel
  trim:        {c: [60, 64, 70], hl: 8, sm: 98, se: 116, f0: 20},
  lightbox:    {c: [64, 104, 138], hl: 4, sm: 118, se: 118, f0: 20},    // blank blue diffuser (unlit)
  grille:      {c: [34, 36, 40], hl: 6, sm: 86, se: 100, f0: 20},
  grille_dark: {c: [16, 17, 19], hl: 0, sm: 60, se: 60, f0: 20},
  display:     {c: [14, 16, 18], hl: 2, sm: 200, se: 200, f0: 20},
  rubber:      {c: [26, 26, 28], hl: 4, sm: 40, se: 48, f0: 20},
  liner:       {c: [184, 190, 194], hl: 6, sm: 122, se: 134, f0: 20},   // white enamel liner
  fitting:     {c: [150, 156, 160], hl: 8, sm: 118, se: 132, f0: 20},
  led:         {c: [222, 226, 228], hl: 4, sm: 150, se: 150, f0: 20},   // opal diffusers (unlit)
  rail:        {c: [204, 208, 210], hl: 8, sm: 142, se: 156, f0: 20},   // epoxy-coated shelf frame
  label:       {c: [196, 200, 202], hl: 4, sm: 104, se: 110, f0: 20},
  wire:        {c: [208, 212, 214], hl: 0, sm: 150, se: 150, f0: 20},   // painted by hand below (cutout pattern)
  door:        {c: [40, 43, 48], hl: 10, sm: 100, se: 124, f0: 20},
  glass:       {c: [150, 186, 204], hl: 0, sm: 235, se: 235, f0: 10},   // alpha set below
  metal:       {c: [168, 171, 175], hl: 16, sm: 150, se: 170, f0: 255}, // stainless handles, hinges
};
export const GLASS_ALPHA = 56;                    // > 26 (0.1): survives the shader packs' translucent alpha test
export const WIRE = {pitch: 0.65, width: 0.18};   // px, across X

const ATLAS = 512, PAD = 2, BAND = 448;
// everything else unwraps into the top-left BAND x BAND; the five wire decks share one strip across the bottom
const DECKS = PARTS.find(p => p.name === 'wire_decks'), REST = PARTS.filter(p => p !== DECKS);
const UA = unwrap(REST, {atlas: BAND, pad: PAD, startS: 24, stepS: 0.25});
const STRIP = {x: 2, y: BAND + 8, w: ATLAS - 4, h: 16};
const deckUV = q => [STRIP.x + (q[0] - SHIFT - DECK.x0) / (DECK.x1 - DECK.x0) * STRIP.w, STRIP.y + (q[2] - DECK.z0) / (DECK.z1 - DECK.z0) * STRIP.h];
const UV = {islands: UA.islands, S: UA.S, uvOf: UA.uvOf,
  faceUV: new Map([...UA.faceUV, [DECKS, new Map(DECKS.f.map(f => [f, f.ids.map(id => deckUV(DECKS.v[id]))]))]])};
for (const p of PARTS) assert(p.f.every(f => UV.faceUV.get(p)?.has(f)), 'unmapped face in ' + p.name);
const DUMMY = 'data:image/png;base64,' + png(Buffer.from([0, 0, 0, 255]), 1, 1).toString('base64');
const painted = paint({PARTS: REST, islands: UV.islands, S: UV.S, uvOf: UV.uvOf, atlas: ATLAS, pad: PAD, MATS, ZONED: new Set(), groupInfo: new Map(),
  sourceGroups: () => [], refTexture: {source: DUMMY}, refUvWidth: 1, background: {c: [...MATS.cabinet.c, 255], s: [96, 20, 0, 255], n: [128, 128, 255, 255]}});
// alpha: glass islands translucent, the deck strip a cutout wire pattern (wires along Z, spaced along X)
const MAPS = painted.PNG.map(b => { const r = readPng(b); assert(r.bpp === 4 && r.w === ATLAS, 'atlas format'); return r.px; });
const setPx = (i, x, y, rgba) => MAPS[i].set(rgba, (y * ATLAS + x) * 4);
for (const is of UV.islands) if (is.part.mat === 'glass')
  for (let y = is.py - PAD; y < is.py + is.H + PAD; y++) for (let x = is.px - PAD; x < is.px + is.W + PAD; x++) MAPS[0][(y * ATLAS + x) * 4 + 3] = GLASS_ALPHA;
for (let x = STRIP.x - PAD; x < STRIP.x + STRIP.w + PAD; x++) {
  const u = DECK.x0 + (x + 0.5 - STRIP.x) / STRIP.w * (DECK.x1 - DECK.x0), d = Math.abs(((u - DECK.x0 + WIRE.pitch / 2) % WIRE.pitch) - WIRE.pitch / 2);
  const on = d <= WIRE.width / 2, edge = d > WIRE.width / 2 - 0.05 ? 0.9 : 1;   // a touch darker at the wire sides
  for (let y = STRIP.y - PAD; y < STRIP.y + STRIP.h + PAD; y++) {
    setPx(0, x, y, on ? [...MATS.wire.c.map(v => Math.round(v * edge)), 255] : [...MATS.wire.c, 0]);
    setPx(1, x, y, [MATS.wire.sm, MATS.wire.f0, 0, 255]);
    setPx(2, x, y, [128, 128, 255, 255]);
  }
}
const PNGS = MAPS.map(px => png(px, ATLAS, ATLAS));

// ---------------- source (Free Model) ----------------
const ID = 'beverage_cooler';
const uuid = s => { const h = createHash('sha256').update('afl-beverage-cooler-v2:' + s).digest('hex'); return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-8${h.slice(17, 20)}-${h.slice(20, 32)}`; };
const r12 = v => +v.toFixed(12) || 0;
const pivot = side => [HINGE[side][0] + SHIFT, HINGE[side][1], HINGE[side][2]];
const RIG = [['body', [0, 0, 0]], ['left_door', pivot('left')], ['right_door', pivot('right')]];
const source = (() => {
  const groups = RIG.map(([name, origin]) => ({name, uuid: uuid('group:' + name), export: true, locked: false, scope: 0, selected: false, visibility: true,
    _static: {properties: {}, temp_data: {}}, origin: origin.slice(), rotation: [0, 0, 0], color: 0, children: [], reset: false, shade: true,
    mirror_uv: false, autouv: 0, isOpen: true, primary_selected: false}));
  const nodes = new Map(RIG.map(([name]) => [name, {uuid: uuid('group:' + name), isOpen: true, children: []}]));
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
  return {meta: {format_version: '5.0', model_format: 'free', box_uv: false}, name: ID, model_identifier: '', visible_box: [2, 2, 0],
    variable_placeholders: '', variable_placeholder_buttons: [], timeline_setups: [], unhandled_root_fields: {}, resolution: {width: ATLAS, height: ATLAS},
    elements, groups, outliner: RIG.map(([name]) => nodes.get(name)),
    textures: [{name: ID + '.png', relative_path: `textures/${ID}.png`, folder: '', namespace: '', id: '0', group: '', scope: 0,
      width: ATLAS, height: ATLAS, uv_width: ATLAS, uv_height: ATLAS, particle: false, use_as_default: false, layers_enabled: false, sync_to_project: '',
      file_format: 'png', render_mode: 'default', render_sides: 'auto', wrap_mode: 'limited', pbr_channel: 'color', fps: 7, frame_time: 1,
      frame_order_type: 'loop', frame_order: '', frame_interpolate: false, visible: true, internal: true, saved: true, uuid: uuid('texture'),
      source: 'data:image/png;base64,' + PNGS[0].toString('base64')}],
    animations: []};
})();

// ---------------- runtime ----------------
const geo = {format_version: '1.12.0', 'minecraft:geometry': [{description: {identifier: 'geometry.' + ID, texture_width: ATLAS, texture_height: ATLAS,
  visible_bounds_width: 4, visible_bounds_height: 3, visible_bounds_offset: [0, 1, 0]},
  bones: RIG.map(([name, origin]) => ({name, pivot: [-origin[0] || 0, origin[1], origin[2]]}))}]};
const LAYERS = Object.fromEntries(PARTS.filter(p => p.mat === 'glass').map(p => [p.name, 'translucent']));
const sidecar = convert(source, geo, {}, ID + '.bbmodel', 2, null, LAYERS);
const meshText = serializeCompact(sidecar);

const r3 = v => +v.toFixed(3) || 0, r6 = v => +v.toFixed(6) || 0;
const aroundY = (q, pv, deg) => { const a = deg * Math.PI / 180, x = q[0] - pv[0], z = q[2] - pv[2];   // same convention as the locker / electrical box doors
  return [pv[0] + x * Math.cos(a) + z * Math.sin(a), q[1], pv[2] - x * Math.sin(a) + z * Math.cos(a)]; };
const posed = (p, t) => p.bone === 'body' ? p.v : p.v.map(q => aroundY(q, pivot(p.bone.split('_')[0]), DOOR_DEGREES[p.bone.split('_')[0]] * t));
const aabbOf = pts => [0, 1, 2].map(k => Math.min(...pts.map(q => q[k]))).concat([0, 1, 2].map(k => Math.max(...pts.map(q => q[k]))));
const bounds = (() => {
  const b = aabbOf(PARTS.flatMap(p => Array.from({length: 11}, (_, s) => posed(p, s / 10)).flat())), m = 0.25;
  return [(b[0] - m + 8) / 16, (b[1] - m) / 16, (b[2] - m + 8) / 16, (b[3] + m + 8) / 16, (b[4] + m) / 16, (b[5] + m + 8) / 16].map(r6);
})();
const profile = {format_version: 1, geometry: `apocalypse_firstlight:geo/${ID}.geo.json`,
  texture: `apocalypse_firstlight:textures/block/${ID}.png`, origin: [0, 0, 0], scale: [1, 1, 1], facing: 'horizontal', bounds,
  parts: Object.fromEntries(RIG.map(([name, origin]) => [name, {pivot: origin.map(v => r6(v / 16))}])),
  // same 0.40 s swing as V1 (BeverageCoolerBlock commits the state after ANIMATION_TICKS)
  animations: Object.fromEntries(['left', 'right'].map(s => [`${s}_open`, {duration_ticks: DOOR_TICKS, easing: 'ease_in_out',
    transforms: {[`${s}_door`]: {rotation: [0, DOOR_DEGREES[s], 0]}}}]))};

// item model: builtin/entity; every view centred on the closed mesh (its middle sits 8 px toward -X from the master cell)
const itemDisplay = (() => {
  const rot = ([ax, ay]) => q => { const a = ay * Math.PI / 180, b = ax * Math.PI / 180, x = q[0] * Math.cos(a) + q[2] * Math.sin(a), z = -q[0] * Math.sin(a) + q[2] * Math.cos(a);
    return [x, q[1] * Math.cos(b) - z * Math.sin(b), q[1] * Math.sin(b) + z * Math.cos(b)]; };
  const pts = PARTS.flatMap(p => p.v).map(q => [q[0], q[1] - 8, q[2]]), S3 = v => [v, v, v];
  const view = (r, fit, extra = [0, 0, 0]) => { const R = pts.map(rot(r)), xs = R.map(q => q[0]), ys = R.map(q => q[1]), zs = R.map(q => q[2]);
    const s = typeof fit === 'number' && fit > 1 ? r3(fit / Math.max(Math.max(...xs) - Math.min(...xs), Math.max(...ys) - Math.min(...ys))) : fit;
    const c = [(Math.max(...xs) + Math.min(...xs)) / 2, (Math.max(...ys) + Math.min(...ys)) / 2, (Math.max(...zs) + Math.min(...zs)) / 2];
    return {rotation: [r[0], r[1], 0], translation: [r3(-s * c[0] + extra[0]), r3(-s * c[1] + extra[1]), r3(-s * c[2] + extra[2])], scale: S3(s)}; };
  return {gui: view([25, 45], 14), ground: view([0, 0], 0.22, [0, 3, 0]), fixed: view([0, 180], 0.38),
    thirdperson_righthand: view([75, 45], 0.25, [0, 2.5, 0]), firstperson_righthand: view([0, 45], 0.28, [0, 1, 0])};
})();
const itemModel = {parent: 'builtin/entity', gui_light: 'side', textures: {particle: `apocalypse_firstlight:block/${ID}`}, display: itemDisplay};
const blockModel = {textures: {particle: `apocalypse_firstlight:block/${ID}`}};

const bb = path.join(ROOT, 'src/main/blockbench'), assets = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight');
const outputs = [[path.join(bb, ID + '.bbmodel'), JSON.stringify(source)], [path.join(assets, `geo/${ID}.geo.json`), JSON.stringify(geo, null, 2) + '\n'],
  [path.join(assets, `meshes/${ID}.aflmesh.json`), meshText], [path.join(assets, `block_mesh_profiles/${ID}.json`), JSON.stringify(profile, null, 2) + '\n'],
  [path.join(assets, `models/item/${ID}.json`), JSON.stringify(itemModel, null, 2) + '\n'], [path.join(assets, `models/block/${ID}.json`), JSON.stringify(blockModel, null, 2) + '\n'],
  ...['', '_s', '_n'].flatMap((k, i) => [[path.join(bb, `textures/${ID}${k}.png`), PNGS[i]], [path.join(assets, `textures/block/${ID}${k}.png`), PNGS[i]]])];

const faces = sidecar.parts.flatMap(p => p.faces), zf = zFightLevels(PARTS, new Map());
export const stats = {triangles: faces.reduce((s, q) => s + q.length - 2, 0), parts: sidecar.parts.length, texelsPerPx: UV.S, islands: UV.islands.length + 1,
  translucent: Object.keys(LAYERS), coplanarOverlaps: zf.unresolved.length, bounds, closed: aabbOf(PARTS.flatMap(p => p.v)).map(r3), gui: itemDisplay.gui,
  byBone: Object.fromEntries(RIG.map(([b]) => [b, PARTS.filter(p => p.bone === b).reduce((s, p) => s + p.f.reduce((t, f) => t + f.ids.length - 2, 0), 0)]))};
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
