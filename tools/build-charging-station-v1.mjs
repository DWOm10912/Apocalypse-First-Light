// Charging Station V1 (first Pure Mesh machine): a 2 x 1 x 1 floor-standing steel charging bench. One item at a time lies
// on a recessed rubber tray (a battery or, later, a long charge weapon, always in the middle); the front control strip
// carries one long charge bar window and a readout screen (their contents are drawn by client/ChargingStationRenderer),
// a rotary knob, and a rack of ten cell bays with orange status lamps (lit while the station has power). The back of each
// cell carries a standard AFL power port (6 x 6 px steel plate flush with the block boundary, round socket r 1.95; see
// docs/models/power_cable_v2.md). Style of the existing AFL machines: medium grey steel casing, dark charcoal frame and
// legs, recessed panels, orange accents. Plain surfaces, no labels.
//   node tools/build-charging-station-v1.mjs                 -> writes source, runtime geo / sidecar / profile / maps / item model
//   node tools/build-charging-station-v1.mjs --check         -> fails if any written output is stale
//   node tools/build-charging-station-v1.mjs --preview DIR   -> writes only geo / sidecar / maps into DIR (offline review)
// Frame (px): the beverage cooler's convention: x -8..24 across both cells (master = the left cell, x 8..24, as the viewer
// faces the front), y 0..16, front toward -Z (z -8..8). Output is master-centred (x - 16).
// Bones: body; lamps (unlit lenses); lamps_lit (the same lenses, bright, LabPBR emissive; neverRender in the geo so the
// item model shows the unlit set). The block entity shows one of the two (AflAnimatedMeshHost#meshPartVisible) and draws
// lamps_lit at full brightness (meshPartEmissive). A reserved white emissive texel block (GLOW) is what the renderer's
// charge bar quads sample, tinted by vertex colour.
import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {convert, serializeCompact} from './export-afl-mesh.mjs';
import {Part, AX, extrude, mul, area2, unwrap, paint, png, readPng, zFightLevels} from './cube-slab-mesh-lib.mjs';
import {addPowerPort} from './afl-power-port.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
function assert(c, m) { if (!c) throw new Error(m); }
const PARTS = [], P = (name, mat, bone = 'body') => { const p = new Part(name, bone, mat); PARTS.push(p); return p; };
const orient = (L, ccw) => (area2(L) > 0) === ccw ? L : L.slice().reverse();
const rect = (u0, v0, u1, v1) => [[u0, v0], [u1, v0], [u1, v1], [u0, v1]];
const circle = (cu, cv, r, seg) => Array.from({length: seg}, (_, i) => { const a = Math.PI / seg + 2 * Math.PI * i / seg; return [cu + r * Math.cos(a), cv + r * Math.sin(a)]; });
function slab(part, ax, a, b, c = 0) {
  const [ku, kv, ka] = ax === 'x' ? [2, 1, 0] : ax === 'y' ? [2, 0, 1] : [0, 1, 2];
  extrude(part, ax, {outer: orient(rect(a[ku], a[kv], b[ku], b[kv]), true), holes: []}, a[ka], b[ka], c);
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

// ---------------- dimensions (source px) ----------------
export const X0 = -8, X1 = 24, LEG = 1.6;                           // overall width, end frame thickness
export const DESK = {y0: 5.0, y1: 12.6, z0: -6.6, z1: 7.4};         // cabinet under the tray
export const TRAY = {x0: -5.4, x1: 21.4, z0: -5.2, z1: 5.3, y: 13.0}; // rubber charging bed (top surface)
export const PANEL = {x0: -5.8, x1: 21.8, y0: 9.7, y1: 12.0};       // control strip on the cabinet front
export const RACK = {y0: 5.4, y1: 9.2};                              // cell bays
export const FRONT = DESK.z0 - 0.12;                                 // front plane of the screen glass
export const BAR = {x0: 2.0, x1: 20.9, y0: 10.2, y1: 11.5};         // charge bar window
export const READOUT = {x0: -3.3, x1: 1.3, y0: 10.0, y1: 11.7};     // % readout screen
export const KNOB = {x: -4.6, y: 10.85, r: 0.62};
export const PORT = {y: 8, cells: [0, 16]};                         // AFL power port, one per cell (back face centre)
export const ITEM = {x: 8, y: TRAY.y, z: (TRAY.z0 + TRAY.z1) / 2};  // the tray item's resting point

{
  // end frames (legs + side cheeks), dark: full height at the back, sloping down toward the front above the tray
  const frame = P('end_frames', 'frame');
  const cheek = [[-8, 0.3], [-5.6, 0.3], [-5.6, 4.2], [6.4, 4.2], [6.4, 0.3], [8, 0.3], [8, 16], [6.6, 16], [-6.4, 14.4], [-8, 13.6]];
  for (const [a, b] of [[X0, X0 + LEG], [X1 - LEG, X1]]) extrude(frame, 'x', {outer: orient(cheek, true), holes: []}, a, b, 0.2);
  // cabinet: grey steel box between the frames; raised back wall; a low stretcher at the back between the legs
  const casing = P('casing', 'casing');
  slab(casing, 'z', [X0 + LEG - 0.05, DESK.y0, DESK.z0], [X1 - LEG + 0.05, DESK.y1, DESK.z1], 0.25);
  slab(casing, 'z', [X0 + LEG - 0.05, DESK.y1 - 0.1, 5.6], [X1 - LEG + 0.05, 15.4, 7.6], 0.25);
  slab(P('stretcher', 'frame'), 'z', [X0 + LEG - 0.1, 1.2, 5.2], [X1 - LEG + 0.1, 2.4, 6.4], 0.15);
  // tray: steel rim around the bed (its top 0.45 above the rubber), the bed sunk into it, dark front bumper rail
  extrude(P('tray_rim', 'casing'), 'y', {outer: orient(rect(DESK.z0 - 0.2, X0 + LEG, 5.7, X1 - LEG), true), holes: [orient(rect(TRAY.z0, TRAY.x0, TRAY.z1, TRAY.x1), false)]},
    DESK.y1 - 0.05, TRAY.y + 0.45, 0.12);
  slab(P('tray_pad', 'pad'), 'y', [TRAY.x0 + 0.02, DESK.y1 - 0.02, TRAY.z0 + 0.02], [TRAY.x1 - 0.02, TRAY.y, TRAY.z1 - 0.02], 0.08);
  cyl(P('bumper', 'frame'), 'x', DESK.z0 - 0.3, DESK.y1 + 0.25, 0.75, X0 + LEG + 0.05, X1 - LEG - 0.05, 10);
  // control strip: recessed panel, one long charge bar window, one readout screen, knob
  slab(P('panel_recess', 'inset'), 'z', [PANEL.x0, PANEL.y0, DESK.z0 - 0.06], [PANEL.x1, PANEL.y1, DESK.z0 + 0.2], 0);
  const glass = P('readouts', 'screen');
  slab(glass, 'z', [BAR.x0, BAR.y0, FRONT], [BAR.x1, BAR.y1, DESK.z0 - 0.04], 0);
  slab(glass, 'z', [READOUT.x0, READOUT.y0, FRONT], [READOUT.x1, READOUT.y1, DESK.z0 - 0.04], 0);
  cyl(P('knob', 'knob'), 'z', KNOB.x, KNOB.y, KNOB.r, DESK.z0 - 0.7, DESK.z0 - 0.04, 12);
  // cell rack: ten bays in a dark recess, each with a lamp lens (two lens sets: unlit / lit)
  slab(P('rack_recess', 'rack'), 'z', [PANEL.x0, RACK.y0, DESK.z0 - 0.06], [PANEL.x1, RACK.y1, DESK.z0 + 0.4], 0);
  const cells = P('rack_cells', 'cell'), lamps = P('rack_lamps', 'lamp', 'lamps'), lit = P('rack_lamps_lit', 'lamp_lit', 'lamps_lit');
  for (let i = 0; i < 10; i++) {
    const x0 = X0 + LEG + 1.0 + i * 2.66;
    slab(cells, 'z', [x0, RACK.y0 + 0.35, DESK.z0 - 0.12], [x0 + 2.36, RACK.y1 - 0.35, DESK.z0 - 0.02], 0);
    for (const p of [lamps, lit]) cyl(p, 'z', x0 + 1.18, RACK.y0 + 1.15, 0.36, DESK.z0 - 0.3, DESK.z0 - 0.1, 8);
  }
  // power ports on the back of each cell (tools/afl-power-port.mjs): the plate stands out from the casing back to the
  // block boundary (z 8), round socket with a contact pin
  const port = {plate: P('port_plates', 'port'), socket: P('port_sockets', 'socket'), pin: P('port_pins', 'knob')};
  for (const cx of PORT.cells) addPowerPort(port, cx, PORT.y, DESK.z1);
  // feet pads under the legs
  const feet = P('feet', 'rubber');
  for (const x of [X0 + LEG / 2, X1 - LEG / 2]) for (const [z0, z1] of [[-8, -5.6], [6.4, 8]]) slab(feet, 'y', [x - 0.9, 0, z0 + 0.1], [x + 0.9, 0.36, z1 - 0.1], 0);
}

export const MATS = {   // Base Color, bevel highlight, smoothness open / edge, F0 (all coated / dielectric)
  casing:   {c: [118, 122, 128], hl: 10, sm: 96, se: 118, f0: 20},    // painted steel, AFL machine grey
  frame:    {c: [52, 55, 60], hl: 10, sm: 92, se: 116, f0: 20},       // charcoal frames, legs, bumper
  inset:    {c: [92, 96, 102], hl: 6, sm: 90, se: 104, f0: 20},
  rack:     {c: [46, 49, 54], hl: 4, sm: 84, se: 96, f0: 20},
  cell:     {c: [78, 82, 88], hl: 8, sm: 96, se: 112, f0: 20},
  lamp:     {c: [122, 74, 38], hl: 4, sm: 170, se: 170, f0: 20},     // orange lenses, unlit
  lamp_lit: {c: [255, 168, 72], hl: 0, sm: 190, se: 190, f0: 20},     // lit (emissive, see below)
  screen:   {c: [16, 20, 22], hl: 2, sm: 200, se: 200, f0: 20},
  knob:     {c: [40, 42, 46], hl: 10, sm: 110, se: 130, f0: 20},
  port:     {c: [134, 138, 144], hl: 12, sm: 112, se: 130, f0: 20},   // port plate: bare-looking but coated steel
  socket:   {c: [24, 25, 28], hl: 0, sm: 60, se: 60, f0: 20},
  pad:      {c: [30, 31, 34], hl: 2, sm: 48, se: 56, f0: 20},         // rubber charging bed
  rubber:   {c: [26, 26, 28], hl: 2, sm: 40, se: 48, f0: 20},
};
export const LAMP_EMISSION = 200, GLOW_EMISSION = 230;   // LabPBR _s alpha (0..254 = emission strength, 255 = none)

const ATLAS = 512, PAD = 2, BAND = 496;
// everything unwraps into the top-left BAND x BAND; GLOW is a reserved white emissive block in the free corner
export const GLOW = {x: 500, y: 500, size: 8};
const UV = unwrap(PARTS, {atlas: BAND, pad: PAD, startS: 24, stepS: 0.25});
for (const p of PARTS) assert(p.f.every(f => UV.faceUV.get(p)?.has(f)), 'unmapped face in ' + p.name);
const DUMMY = 'data:image/png;base64,' + png(Buffer.from([0, 0, 0, 255]), 1, 1).toString('base64');
const painted = paint({PARTS, islands: UV.islands, S: UV.S, uvOf: UV.uvOf, atlas: ATLAS, pad: PAD, MATS, ZONED: new Set(), groupInfo: new Map(),
  sourceGroups: () => [], refTexture: {source: DUMMY}, refUvWidth: 1, background: {c: [...MATS.casing.c, 255], s: [96, 20, 0, 255], n: [128, 128, 255, 255]}});
const MAPS = painted.PNG.map(b => { const r = readPng(b); assert(r.bpp === 4 && r.w === ATLAS, 'atlas format'); return r.px; });
for (const is of UV.islands) if (is.part.mat === 'lamp_lit')
  for (let y = is.py - PAD; y < is.py + is.H + PAD; y++) for (let x = is.px - PAD; x < is.px + is.W + PAD; x++) MAPS[1][(y * ATLAS + x) * 4 + 3] = LAMP_EMISSION;
for (let y = GLOW.y - PAD; y < GLOW.y + GLOW.size + PAD; y++) for (let x = GLOW.x - PAD; x < GLOW.x + GLOW.size + PAD; x++) {
  const k = (y * ATLAS + x) * 4;
  MAPS[0].set([255, 255, 255, 255], k); MAPS[1].set([200, 20, 0, GLOW_EMISSION], k); MAPS[2].set([128, 128, 255, 255], k);
}
const PNGS = MAPS.map(px => png(px, ATLAS, ATLAS));

// ---------------- source (Free Model) ----------------
const ID = 'charging_station';
const uuid = s => { const h = createHash('sha256').update('afl-charging-station-v1:' + s).digest('hex'); return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-8${h.slice(17, 20)}-${h.slice(20, 32)}`; };
const r12 = v => +v.toFixed(12) || 0;
const SHIFT = -16;   // master-centred output, like the cooler
for (const p of PARTS) p.v = p.v.map(q => [q[0] + SHIFT, q[1], q[2]]);
const RIG = ['body', 'lamps', 'lamps_lit'];
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
  return {meta: {format_version: '5.0', model_format: 'free', box_uv: false}, name: ID, model_identifier: '', visible_box: [2, 1, 0],
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
const geo = {format_version: '1.12.0', 'minecraft:geometry': [{description: {identifier: 'geometry.' + ID, texture_width: ATLAS, texture_height: ATLAS,
  visible_bounds_width: 3, visible_bounds_height: 2, visible_bounds_offset: [0, 0.5, 0]},
  bones: RIG.map(name => name === 'lamps_lit' ? {name, pivot: [0, 0, 0], neverRender: true} : {name, pivot: [0, 0, 0]})}]};
const sidecar = convert(source, geo, {}, ID + '.bbmodel', 2);
const meshText = serializeCompact(sidecar);

const r3 = v => +v.toFixed(3) || 0, r6 = v => +v.toFixed(6) || 0;
const aabbOf = pts => [0, 1, 2].map(k => Math.min(...pts.map(q => q[k]))).concat([0, 1, 2].map(k => Math.max(...pts.map(q => q[k]))));
const bounds = (() => {
  const b = aabbOf(PARTS.flatMap(p => p.v)), m = 0.25;
  return [(b[0] - m + 8) / 16, (b[1] - m) / 16, (b[2] - m + 8) / 16, (b[3] + m + 8) / 16, (b[4] + m) / 16, (b[5] + m + 8) / 16].map(r6);
})();
const profile = {format_version: 1, geometry: `apocalypse_firstlight:geo/${ID}.geo.json`,
  texture: `apocalypse_firstlight:textures/block/${ID}.png`, origin: [0, 0, 0], scale: [1, 1, 1], facing: 'horizontal', bounds,
  parts: Object.fromEntries(RIG.map(name => [name, {pivot: [0, 0, 0]}]))};

// item model: builtin/entity; every view centred on the mesh (its middle sits 8 px toward -X from the master cell)
const itemDisplay = (() => {
  const rot = ([ax, ay]) => q => { const a = ay * Math.PI / 180, b = ax * Math.PI / 180, x = q[0] * Math.cos(a) + q[2] * Math.sin(a), z = -q[0] * Math.sin(a) + q[2] * Math.cos(a);
    return [x, q[1] * Math.cos(b) - z * Math.sin(b), q[1] * Math.sin(b) + z * Math.cos(b)]; };
  const pts = PARTS.filter(p => p.bone !== 'lamps_lit').flatMap(p => p.v).map(q => [q[0], q[1] - 8, q[2]]), S3 = v => [v, v, v];
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
const blockstate = {variants: {'': {model: `apocalypse_firstlight:block/${ID}`}}};

const bb = path.join(ROOT, 'src/main/blockbench'), assets = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight');
const outputs = [[path.join(bb, ID + '.bbmodel'), JSON.stringify(source)], [path.join(assets, `geo/${ID}.geo.json`), JSON.stringify(geo, null, 2) + '\n'],
  [path.join(assets, `meshes/${ID}.aflmesh.json`), meshText], [path.join(assets, `block_mesh_profiles/${ID}.json`), JSON.stringify(profile, null, 2) + '\n'],
  [path.join(assets, `models/item/${ID}.json`), JSON.stringify(itemModel, null, 2) + '\n'], [path.join(assets, `models/block/${ID}.json`), JSON.stringify(blockModel, null, 2) + '\n'],
  [path.join(assets, `blockstates/${ID}.json`), JSON.stringify(blockstate, null, 2) + '\n'],
  ...['', '_s', '_n'].flatMap((k, i) => [[path.join(bb, `textures/${ID}${k}.png`), PNGS[i]], [path.join(assets, `textures/block/${ID}${k}.png`), PNGS[i]]])];

const zf = zFightLevels(PARTS, new Map(), {skip: p => p.bone === 'lamps_lit'});
export const stats = {triangles: sidecar.parts.flatMap(p => p.faces).reduce((s, q) => s + q.length - 2, 0), parts: sidecar.parts.length, texelsPerPx: UV.S,
  islands: UV.islands.length, coplanarOverlaps: zf.unresolved.length, bounds, closed: aabbOf(PARTS.flatMap(p => p.v)).map(r3), gui: itemDisplay.gui,
  glowUV: [(GLOW.x + GLOW.size / 2) / ATLAS, (GLOW.y + GLOW.size / 2) / ATLAS],
  byBone: Object.fromEntries(RIG.map(b => [b, PARTS.filter(p => p.bone === b).reduce((s, p) => s + p.f.reduce((t, f) => t + f.ids.length - 2, 0), 0)]))};

const isMain = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  console.log(JSON.stringify(stats));
  if (zf.unresolved.length) console.log('COPLANAR', JSON.stringify(zf.unresolved.slice(0, 12)));
  const pi = process.argv.indexOf('--preview');
  if (pi > 0) {
    const dir = process.argv[pi + 1]; fs.mkdirSync(dir, {recursive: true});
    fs.writeFileSync(path.join(dir, `${ID}.geo.json`), JSON.stringify(geo, null, 2) + '\n');
    fs.writeFileSync(path.join(dir, `${ID}.aflmesh.json`), meshText);
    fs.writeFileSync(path.join(dir, `${ID}.bbmodel`), JSON.stringify(source));
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
