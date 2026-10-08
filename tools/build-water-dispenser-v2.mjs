// Water Dispenser V2: floor-standing office water cooler with a 19 L bottle on top, 1 x 2 blocks, as Pure Mesh + 512 LabPBR
// atlas, rendered by the AFL Animated Block Mesh Runtime (bones 'body', 'bottle', 'lights', 'lights_lit'; no animation) and
// in inventories by AflStaticMeshItemRenderer. Replaced the V1 cube model (2026-10-01). Decoration plus power (indicator
// lights only); no water, no water level (the bottle is an empty translucent shell, user 2026-10-01).
//   node tools/build-water-dispenser-v2.mjs                -> writes source, runtime geo / sidecar / profile / maps / item model
//   node tools/build-water-dispenser-v2.mjs --check        -> verifies every output is up to date
//   node tools/build-water-dispenser-v2.mjs --preview DIR  -> writes only geo / sidecar / maps into DIR (offline review)
// Frame (px): the block's bottom centre at the origin, y 0..32 over both cells, front toward -Z, +X the viewer's LEFT.
// Layout: an aged greige plastic cabinet with rounded vertical corners on a dark plinth, a lower door panel, a dark
// dispensing alcove with two push taps (hot: red button on the left, cold: blue on the right) over a grated drip tray, a
// dark faceplate above it with the two indicator LEDs (heating orange, cooling green: 'lights' / 'lights_lit'), a paper
// cup tube on the right side, the bottle seat on top. The cabinet stands at the back of its block, its back on the block
// boundary (against the wall, like the real thing), so nothing sticks out behind: the condenser grille and the standard
// AFL power port (tools/afl-power-port.mjs, at the lower cell's face centre, its face on the boundary) sit in pockets
// recessed into the back (user 2026-10-02: first a dark compressor box carried the port and looked like a loudspeaker; the
// port's shape stays the standard one, the way it sits in each block may differ). No print, labels or logos.
import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {convert, serializeCompact} from './export-afl-mesh.mjs';
import {addIecInlet} from './afl-iec-inlet.mjs';
import {Part, extrude, area2, unwrap, paint, png, readPng, zFightLevels} from './cube-slab-mesh-lib.mjs';

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
// a rounded rectangle in the (z, x) plane (extruded along y), corners of radius r; notches {x, z}: |x| <= x cut into the
// front edge back to z, or into the back edge forward to z
function roundRect(z0, x0, z1, x1, r, front = null, back = null, seg = 4) {
  const arc = (cz, cx, a0, a1) => Array.from({length: seg + 1}, (_, i) => { const a = (a0 + (a1 - a0) * i / seg) * Math.PI / 180; return [cz + r * Math.cos(a), cx + r * Math.sin(a)]; });
  const f = front ? [[z0, front.x], [front.z, front.x], [front.z, -front.x], [z0, -front.x]] : [];
  const b = back ? [[z1, -back.x], [back.z, -back.x], [back.z, back.x], [z1, back.x]] : [];
  return orient([...arc(z0 + r, x1 - r, 90, 180), ...f, ...arc(z0 + r, x0 + r, 180, 270), ...arc(z1 - r, x0 + r, 270, 360), ...b, ...arc(z1 - r, x1 - r, 0, 90)], true);
}
// lathe around a vertical axis through (cx, cz): profile [y, r] from the bottom (r 0 on the axis) to the top
function latheY(part, cx, cz, profile, seg) {
  const at = (y, r, i) => { const a = 2 * Math.PI * (i + 0.5) / seg; return part.vtx([cx + r * Math.cos(a), y, cz + r * Math.sin(a)]); };
  const rings = profile.map(([y, r]) => r < 1e-6 ? part.vtx([cx, y, cz]) : Array.from({length: seg}, (_, i) => at(y, r, i)));
  for (let k = 0; k + 1 < profile.length; k++) {
    const [y0, r0] = profile[k], [y1, r1] = profile[k + 1], dr = r1 - r0, dy = y1 - y0;
    for (let i = 0; i < seg; i++) {
      const j = (i + 1) % seg, a = 2 * Math.PI * (i + 1) / seg, n = [dy * Math.cos(a), -dr, dy * Math.sin(a)];   // outward
      const tag = Math.abs(dr) > 4 * Math.abs(dy) ? 'cap' : 'side';
      if (r0 < 1e-6) part.face([rings[k], rings[k + 1][i], rings[k + 1][j]], n, tag);
      else if (r1 < 1e-6) part.face([rings[k][i], rings[k][j], rings[k + 1]], n, tag);
      else part.face([rings[k][i], rings[k][j], rings[k + 1][j], rings[k + 1][i]], n, tag);
    }
  }
}
const cylY = (part, cx, cz, r, y0, y1, seg) => latheY(part, cx, cz, [[y0, 0], [y0, r], [y1, r], [y1, 0]], seg);

// ---------------- dimensions (source px) ----------------
export const CAB = {x: 5.6, z0: -3.0, z1: 8.0, r: 1.0};                 // cabinet: half width, front, back (the block boundary), corner radius
export const Y = {plinth: 0.8, alcove: [9.6, 17.6], top: 20.6, cap: 21.2};
export const ALCOVE = {x: 4.0, z: CAB.z0 + 2.0};                         // the dispensing alcove cut into the front
export const PORT_POCKET = {x: 3.6, y: [4.4, 11.6], z: CAB.z1 - 1.2};    // recessed into the back; the power cord leaves from it
export const GRILLE_POCKET = {x: 4.4, y: [12.2, 20.0], z: CAB.z1 - 1.0}; // recessed into the back for the condenser grille
export const TAPS = [{x: 1.9, mat: 'hot'}, {x: -1.9, mat: 'cold'}];     // viewer's left: hot, right: cold
export const BOTTLE = {cx: 0, cz: (CAB.z0 + CAB.z1) / 2, seg: 16};
export const CUPS = {x: -6.6, z: CAB.z0 + 3.8, r: 0.95};                 // paper cup tube on the viewer's right side
// power inlet (px): its centre on the lower pocket's floor, low toward the viewer's left (WaterDispenserBlockEntity#cordGeometry)
export const CORD = {x: 2.3, y: 5.0, z: PORT_POCKET.z};
assert(PORT_POCKET.y[0] < CORD.y - 0.3 && PORT_POCKET.x > CORD.x + 0.4 && PORT_POCKET.y[1] < GRILLE_POCKET.y[0], 'pockets');

{
  // plinth; cabinet stacked in bands along y, each with its notches (front: the alcove, back: the two pockets); top cap
  extrude(P('plinth', 'body', 'plinth'), 'y', {outer: roundRect(CAB.z0 + 0.3, -CAB.x + 0.3, CAB.z1 - 0.3, CAB.x - 0.3, 0.8), holes: []}, 0, Y.plinth, 0);
  const shell = P('cabinet', 'body', 'shell'), A = ALCOVE, PP = PORT_POCKET, GP = GRILLE_POCKET;
  const bands = [[Y.plinth, PP.y[0], null, null], [PP.y[0], Y.alcove[0], null, PP], [Y.alcove[0], PP.y[1], A, PP], [PP.y[1], GP.y[0], A, null],
    [GP.y[0], Y.alcove[1], A, GP], [Y.alcove[1], GP.y[1], null, GP], [GP.y[1], Y.top, null, null]];
  for (const [y0, y1, front, back] of bands) extrude(shell, 'y', {outer: roundRect(CAB.z0, -CAB.x, CAB.z1, CAB.x, CAB.r, front, back), holes: []}, y0, y1, 0);
  // the alcove's walls are dark (the faces of the front notch)
  for (const f of shell.f) if (f.ids.every(i => { const q = shell.v[i]; return Math.abs(q[0]) <= A.x + 1e-4 && q[2] <= A.z + 1e-4; })) f.mat = 'panel';
  extrude(P('top_cap', 'body', 'shell'), 'y', {outer: roundRect(CAB.z0 + 0.3, -CAB.x + 0.3, CAB.z1 - 0.3, CAB.x - 0.3, 0.8), holes: []}, Y.top, Y.cap, 0.15);
  // alcove: dark ceiling and floor liners, two push taps, the drip tray with its grate
  const panel = P('alcove_liner', 'body', 'panel');
  slab(panel, 'y', [-A.x, Y.alcove[1] - 0.3, CAB.z0], [A.x, Y.alcove[1], A.z], 0);
  slab(panel, 'y', [-A.x, Y.alcove[0], CAB.z0], [A.x, Y.alcove[0] + 0.15, A.z], 0);
  const tap = P('taps', 'body', 'tap'), top = Y.alcove[1] - 0.3, z0 = CAB.z0;
  for (const t of TAPS) {
    slab(tap, 'y', [t.x - 0.65, top - 1.3, z0 + 0.4], [t.x + 0.65, top, z0 + 1.6], 0.08);
    cylY(tap, t.x, z0 + 0.9, 0.32, top - 1.9, top - 1.3, 10);
    slab(P('tap_button_' + t.mat, 'body', t.mat), 'z', [t.x - 0.55, top - 1.1, z0 + 0.15], [t.x + 0.55, top - 0.2, z0 + 0.4], 0.08);
  }
  const trayY = Y.alcove[0] + 0.15, tray = P('drip_tray', 'body', 'tray');
  extrude(tray, 'y', {outer: orient(rect(z0 - 0.35, -3.6, z0 + 1.85, 3.6), true), holes: [orient(rect(z0 - 0.05, -3.3, z0 + 1.55, 3.3), false)]}, trayY, trayY + 0.6, 0.05);
  slab(tray, 'y', [-3.3, trayY, z0 - 0.05], [3.3, trayY + 0.15, z0 + 1.55], 0);
  const grate = P('drip_grate', 'body', 'grate');
  for (let k = 0; k < 5; k++) { const z = z0 + 0.1 + k * 0.32; slab(grate, 'y', [-3.3, trayY + 0.3, z], [3.3, trayY + 0.5, z + 0.14], 0); }
  // front: the faceplate above the alcove (LEDs below in 'lights'), the lower door with its finger pull
  slab(P('faceplate', 'body', 'panel'), 'z', [-4.2, Y.alcove[1] + 0.4, z0 - 0.15], [4.2, Y.top - 0.4, z0], 0.05);
  slab(P('lower_door', 'body', 'shell'), 'z', [-4.6, 1.6, z0 - 0.12], [4.6, 8.8, z0], 0.05);
  slab(P('door_pull', 'body', 'panel'), 'z', [-1.2, 8.1, z0 - 0.2], [1.2, 8.45, z0 - 0.12], 0);
  // bottle seat on the top cap
  cylY(P('bottle_seat', 'body', 'panel'), BOTTLE.cx, BOTTLE.cz, 2.4, Y.cap, Y.cap + 0.7, BOTTLE.seg);
  // paper cup tube on the viewer's right side: tube, end rings, the bottom cup showing, two clips to the cabinet
  const cups = P('cup_tube', 'body', 'tube');
  cylY(cups, CUPS.x, CUPS.z, CUPS.r, 11.6, 18.2, 12);
  const rings = P('cup_tube_rings', 'body', 'panel');
  cylY(rings, CUPS.x, CUPS.z, CUPS.r + 0.07, 11.5, 11.8, 12);
  cylY(rings, CUPS.x, CUPS.z, CUPS.r + 0.07, 17.9, 18.3, 12);
  cylY(P('cup', 'body', 'cup'), CUPS.x, CUPS.z, 0.88, 10.9, 11.5, 12);
  for (const y of [12.4, 16.6]) slab(rings, 'x', [-CAB.x - 0.2, y, CUPS.z - 0.6], [-CAB.x, y + 0.6, CUPS.z + 0.6], 0);
  // back: the power cord's grommet in the lower pocket; the condenser grille inside the upper pocket (side rails, cross
  // wires, upright tubes), below the boundary
  // power inlet: IEC C14 (tools/afl-iec-inlet.mjs; Power Outlets V1, 2026-10-08), the detachable cord's C13 connector plugs in here
  addIecInlet({housing: P('inlet_housing', 'body', 'inlet'), floor: P('inlet_floor', 'body', 'socket'), pin: P('inlet_pins', 'body', 'inlet_pin')}, CORD.x, CORD.y, CORD.z);
  const grille = P('condenser', 'body', 'grille');
  for (const s of [-1, 1]) slab(grille, 'z', [s > 0 ? GP.x - 0.3 : -GP.x, GP.y[0], GP.z], [s > 0 ? GP.x : -GP.x + 0.3, GP.y[1], GP.z + 0.8], 0);
  for (let i = 0; i < 10; i++) { const y = GP.y[0] + 0.6 + i * 0.75; slab(grille, 'z', [-GP.x + 0.3, y, GP.z + 0.25], [GP.x - 0.3, y + 0.16, GP.z + 0.43], 0); }
  for (let j = 0; j < 6; j++) { const x = -3.3 + j * 1.32; slab(grille, 'z', [x - 0.11, GP.y[0] + 0.3, GP.z + 0.43], [x + 0.11, GP.y[1] - 0.3, GP.z + 0.61], 0); }
}
// bottle: a 19 L bottle upside down in the seat (neck down), shoulder, body with two grip ribs, flat bottom on top
export const BOTTLE_PROFILE = [[21.5, 0], [21.5, 1.5], [22.6, 1.5], [23.0, 2.1], [23.6, 3.6], [24.2, 4.5], [24.6, 4.7], [26.0, 4.7], [26.25, 4.45],
  [26.75, 4.45], [27.0, 4.7], [28.6, 4.7], [28.85, 4.45], [29.35, 4.45], [29.6, 4.7], [30.7, 4.7], [31.2, 4.45], [31.5, 3.8], [31.6, 0]];
latheY(P('bottle', 'bottle', 'bottle'), BOTTLE.cx, BOTTLE.cz, BOTTLE_PROFILE, BOTTLE.seg);
// lights: the two indicator LEDs on the faceplate, twice (unlit / lit)
function lights(bone, k) {
  for (const t of TAPS) slab(P('led_' + t.mat + k, bone, t.mat + '_led' + k), 'z', [t.x - 0.35, Y.alcove[1] + 1.3, CAB.z0 - 0.25], [t.x + 0.35, Y.alcove[1] + 2.0, CAB.z0 - 0.15], 0);
}
lights('lights', '');
lights('lights_lit', '_lit');

export const MATS = {   // Base Color, bevel highlight, smoothness open / edge, F0 (coated / dielectric; the bottle F0 10)
  shell:        {c: [150, 148, 140], hl: 6, sm: 92, se: 108, f0: 20},    // aged greige plastic, matte (no white, no glare)
  plinth:       {c: [36, 37, 40], hl: 2, sm: 60, se: 64, f0: 20},
  panel:        {c: [46, 48, 52], hl: 6, sm: 100, se: 118, f0: 20},     // alcove, faceplate, seat, rings, clips
  tap:          {c: [60, 62, 66], hl: 8, sm: 110, se: 128, f0: 20},
  hot:          {c: [150, 54, 44], hl: 8, sm: 120, se: 136, f0: 20},
  cold:         {c: [52, 86, 150], hl: 8, sm: 120, se: 136, f0: 20},
  tray:         {c: [72, 74, 78], hl: 6, sm: 104, se: 120, f0: 20},
  grate:        {c: [104, 106, 110], hl: 0, sm: 120, se: 120, f0: 20},
  tube:         {c: [164, 162, 154], hl: 4, sm: 120, se: 130, f0: 20},
  cup:          {c: [188, 184, 174], hl: 0, sm: 70, se: 70, f0: 20},    // paper
  grille:       {c: [24, 25, 27], hl: 0, sm: 90, se: 90, f0: 20},
  bottle:       {c: [104, 156, 200], hl: 0, sm: 165, se: 165, f0: 10},   // blue PC, translucent; not mirror-smooth: under shaders a smoother, thinner bottle only reflected the sky and vanished against it (Sundial, user 2026-10-02)
  hot_led:      {c: [70, 40, 32], hl: 0, sm: 180, se: 180, f0: 20},
  cold_led:     {c: [32, 60, 42], hl: 0, sm: 180, se: 180, f0: 20},
  hot_led_lit:  {c: [236, 132, 52], hl: 0, sm: 200, se: 200, f0: 20},
  cold_led_lit: {c: [84, 212, 112], hl: 0, sm: 200, se: 200, f0: 20},
  port:         {c: [134, 138, 144], hl: 12, sm: 112, se: 130, f0: 20},
  socket:       {c: [24, 25, 28], hl: 0, sm: 60, se: 60, f0: 20},
  port_pin:     {c: [40, 42, 46], hl: 10, sm: 110, se: 130, f0: 20},
  inlet:          {c: [22, 23, 25], hl: 8, sm: 112, se: 132, f0: 20},
  inlet_pin:      {c: [176, 172, 160], hl: 10, sm: 172, se: 188, f0: 255},
};
export const BOTTLE_ALPHA = 110;
export const EMISSION = {hot_led_lit: 210, cold_led_lit: 210};

const ATLAS = 512, PAD = 2;
const UV = unwrap(PARTS, {atlas: ATLAS, pad: PAD, startS: 16, stepS: 0.25});
for (const p of PARTS) assert(p.f.every(f => UV.faceUV.get(p)?.has(f)), 'unmapped face in ' + p.name);
const DUMMY = 'data:image/png;base64,' + png(Buffer.from([0, 0, 0, 255]), 1, 1).toString('base64');
const painted = paint({PARTS, islands: UV.islands, S: UV.S, uvOf: UV.uvOf, atlas: ATLAS, pad: PAD, MATS, ZONED: new Set(), groupInfo: new Map(),
  sourceGroups: () => [], refTexture: {source: DUMMY}, refUvWidth: 1, background: {c: [...MATS.shell.c, 255], s: [92, 20, 0, 255], n: [128, 128, 255, 255]}});
const MAPS = painted.PNG.map(b => { const r = readPng(b); assert(r.bpp === 4 && r.w === ATLAS, 'atlas format'); return r.px; });
for (const is of UV.islands) {
  const glass = is.part.mat === 'bottle', emission = EMISSION[is.part.mat];
  if (!glass && emission === undefined) continue;
  for (let y = is.py - PAD; y < is.py + is.H + PAD; y++) for (let x = is.px - PAD; x < is.px + is.W + PAD; x++) {
    if (glass) MAPS[0][(y * ATLAS + x) * 4 + 3] = BOTTLE_ALPHA;
    else MAPS[1][(y * ATLAS + x) * 4 + 3] = emission;
  }
}
const PNGS = MAPS.map(px => png(px, ATLAS, ATLAS));

// ---------------- source (Free Model) ----------------
const ID = 'water_dispenser';
const uuid = s => { const h = createHash('sha256').update('afl-water-dispenser-v2:' + s).digest('hex'); return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-8${h.slice(17, 20)}-${h.slice(20, 32)}`; };
const r12 = v => +v.toFixed(12) || 0;
const RIG = ['body', 'bottle', 'lights', 'lights_lit'];
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
const geo = {format_version: '1.12.0', 'minecraft:geometry': [{description: {identifier: 'geometry.' + ID, texture_width: ATLAS,
  texture_height: ATLAS, visible_bounds_width: 2, visible_bounds_height: 3, visible_bounds_offset: [0, 1, 0]},
  bones: RIG.map(name => name === 'lights_lit' ? {name, pivot: [0, 0, 0], neverRender: true} : {name, pivot: [0, 0, 0]})}]};
const LAYERS = {bottle: 'translucent'};
const sidecar = convert(source, geo, {}, ID + '.bbmodel', 2, null, LAYERS);
const meshText = serializeCompact(sidecar);

const r3 = v => +v.toFixed(3) || 0, r6 = v => +v.toFixed(6) || 0;
const aabbOf = pts => [0, 1, 2].map(k => Math.min(...pts.map(q => q[k]))).concat([0, 1, 2].map(k => Math.max(...pts.map(q => q[k]))));
const bounds = (() => { const b = aabbOf(PARTS.flatMap(p => p.v)), m = 0.25;
  return [(b[0] - m + 8) / 16, (b[1] - m) / 16, (b[2] - m + 8) / 16, (b[3] + m + 8) / 16, (b[4] + m) / 16, (b[5] + m + 8) / 16].map(r6); })();
const profile = {format_version: 1, geometry: `apocalypse_firstlight:geo/${ID}.geo.json`,
  texture: `apocalypse_firstlight:textures/block/${ID}.png`, origin: [0, 0, 0], scale: [1, 1, 1], facing: 'horizontal', bounds,
  parts: Object.fromEntries(RIG.map(name => [name, {pivot: [0, 0, 0]}]))};

// item model: builtin/entity; every view centred on the dispenser (its middle sits 8 px above the item model's centre)
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
  byBone: Object.fromEntries(RIG.map(b => [b, PARTS.filter(p => p.bone === b).reduce((s, p) => s + trisOf(p), 0)]))};
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
