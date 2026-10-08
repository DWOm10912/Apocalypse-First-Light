// Cash Register V2: countertop POS terminal as Pure Mesh + 512 LabPBR atlas, rendered by the AFL Animated Block Mesh
// Runtime (bones 'body' and 'drawer'; channel 'open' slides the cash drawer out toward the operator) and in inventories by
// AflStaticMeshItemRenderer; shapes / interaction regions / prompt anchors as an AFL Mesh Shape profile. Openable, 9-slot
// searchable container (the cash drawer).
//   node tools/build-cash-register-v2.mjs                 -> writes source, runtime geo / sidecar / profile / maps / shapes / item model
//   node tools/build-cash-register-v2.mjs --check         -> verifies every output is up to date
//   node tools/build-cash-register-v2.mjs --preview DIR   -> writes only geo / sidecar / maps into DIR (offline review)
// Frame (px): block bottom centre at the origin, operator side (drawer, keypad) toward -Z (NORTH at facing=north),
// +X = the operator's left. Layout kept from the V1 cube model: charcoal drawer base, sloped keypad on the operator's right,
// receipt printer on the left, pole-mounted display at the back. New: sliding cash drawer with a till insert (five coin
// cups in front, four note compartments with spring clips behind), tapered keycaps on a 22.5 degree key bed, recessed screen.
// Plain surfaces, no printed legends or wear.
import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {convert, serializeCompact} from './export-afl-mesh.mjs';
import {Part, AX, extrude, mul, area2, unwrap, paint, png, zFightLevels} from './cube-slab-mesh-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
function assert(c, m) { if (!c) throw new Error(m); }
const PARTS = [], P = (name, bone, mat) => { const p = new Part(name, bone, mat); PARTS.push(p); return p; };
const orient = (L, ccw) => (area2(L) > 0) === ccw ? L : L.slice().reverse();
const rect = (u0, v0, u1, v1) => [[u0, v0], [u1, v0], [u1, v1], [u0, v1]];

// ---------------- dimensions (px) ----------------
export const BASE = {x: 7.0, y0: 0.45, y1: 3.25, z0: -6.0, z1: 6.0};            // drawer housing
export const OPENING = {x: 6.25, y0: 0.75, y1: 2.6};                             // drawer opening in the housing front
export const FRONT = {x: 6.4, y0: 0.55, y1: 2.95, z0: -6.37, z1: -6.02};         // drawer front panel
export const TRAY = {x: 6.1, y0: 0.8, y1: 2.5, z0: -6.0, z1: 5.3, wall: 0.2};    // drawer box behind the front
export const TRAVEL = 8.0, DRAWER_PIVOT = [0, 1.75, -6.2];                        // the drawer slides out 8 px toward -Z
export const WEDGE = {x0: -6.2, x1: 1.95, z0: -4.5, z1: 5.25, top: 6.9, slopeEnd: 3.6};   // keypad housing (operator's right)
export const SLOPE = 22.5 * Math.PI / 180, SLOPE_Y0 = WEDGE.top - (WEDGE.slopeEnd - WEDGE.z0) * Math.tan(SLOPE);
export const PRINTER = {x0: 2.15, x1: 6.35, z0: -4.9, z1: 4.9};
export const POLE = [-2.05, 4.4], HEAD = {x0: -5.4, x1: 1.3, y0: 8.6, y1: 11.9, z0: 3.65, z1: 5.0};

// ---------------- primitives ----------------
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
// tapered keycap in key-bed coordinates (x across, y up from the bed, z along the slope): base rectangle, top inset
function keycap(part, x0, z0, x1, z1, y0, h, ins) {
  const b = [[x0, z0], [x1, z0], [x1, z1], [x0, z1]].map(([x, z]) => part.vtx([x, y0, z]));
  const t = [[x0 + ins, z0 + ins], [x1 - ins, z0 + ins], [x1 - ins, z1 - ins], [x0 + ins, z1 - ins]].map(([x, z]) => part.vtx([x, y0 + h, z]));
  part.face([t[0], t[1], t[2], t[3]], [0, 1, 0], 'cap');
  const out = [[0, -0.3, -1], [1, 0.3, 0], [0, 0.3, 1], [-1, 0.3, 0]];   // front, +x, back, -x (hint normals)
  for (let i = 0; i < 4; i++) { const j = (i + 1) % 4; part.face([b[i], b[j], t[j], t[i]], out[i], 'bevel'); }
}
// key bed frame -> model: rotate about X so +y becomes the slope normal (tilted toward the operator), then move to the slope
const toSlope = q => { const c = Math.cos(SLOPE), s = Math.sin(SLOPE);
  return [q[0], SLOPE_Y0 + q[1] * c + q[2] * s, WEDGE.z0 - q[1] * s + q[2] * c]; };

// ---------------- body: drawer housing, plinth, feet, keypad, printer, display, rear ports ----------------
{
  // housing: a sleeve around the drawer opening (hole walls painted darker by the painter), back plate overlapping it by
  // less than two chamfers so their side faces never share a plane
  const housing = P('housing', 'body', 'casing');
  extrude(housing, 'z', {outer: orient(rect(-BASE.x, BASE.y0, BASE.x, BASE.y1), true),
    holes: [orient(rect(-OPENING.x, OPENING.y0, OPENING.x, OPENING.y1), false)]}, BASE.z0, BASE.z1 - 0.35, 0.2);
  slab(housing, 'z', [-BASE.x, BASE.y0, BASE.z1 - 0.45], [BASE.x, BASE.y1, BASE.z1], 0.2);
  slab(P('cavity_back', 'body', 'cavity'), 'z', [-OPENING.x, OPENING.y0, BASE.z1 - 0.5], [OPENING.x, OPENING.y1, BASE.z1 - 0.44], 0);
  slab(P('plinth', 'body', 'rubber'), 'y', [-6.75, 0.18, -5.35], [6.75, 0.5, 5.4], 0);
  const feet = P('feet', 'body', 'rubber');
  for (const x of [-5.55, 5.55]) for (const z of [-4.3, 4.5]) slab(feet, 'y', [x - 0.8, 0, z - 0.6], [x + 0.8, 0.2, z + 0.6], 0);
  // keypad housing: side profile (z, y) extruded across x; the slope rises toward the back at 22.5 degrees
  extrude(P('keypad_housing', 'body', 'casing'), 'x', {outer: orient([[WEDGE.z0, BASE.y1 - 0.05], [WEDGE.z1, BASE.y1 - 0.05], [WEDGE.z1, WEDGE.top],
    [WEDGE.slopeEnd, WEDGE.top], [WEDGE.z0, SLOPE_Y0]], true), holes: []}, WEDGE.x0, WEDGE.x1, 0.15);
  // key bed and keys, built flat in the key-bed frame then laid onto the slope
  const bed = P('key_bed', 'body', 'keybed'), keys = P('keys', 'body', 'key'), enter = P('key_enter', 'body', 'enter');
  slab(bed, 'y', [-5.75, -0.05, 0.45], [1.6, 0.12, 6.95], 0);
  const K = 0.95, PITCH = 1.18, H = 0.3, INS = 0.1, rowZ = i => 1.1 + i * PITCH;
  for (let r = 0; r < 5; r++) for (let c = 0; c < 4; c++) { const x = 0.85 - c * PITCH; keycap(keys, x - K / 2, rowZ(r) - K / 2, x + K / 2, rowZ(r) + K / 2, 0.12, H, INS); }
  for (let r = 2; r < 5; r++) keycap(keys, -5.25, rowZ(r) - K / 2, -3.65, rowZ(r) + K / 2, 0.12, H, INS);
  keycap(enter, -5.25, rowZ(0) - K / 2, -3.65, rowZ(1) + K / 2, 0.12, H, INS);
  for (const p of [bed, keys, enter]) p.v = p.v.map(toSlope);
  // receipt printer: base, paper cover, raised rear module (the gap between them is the paper exit)
  slab(P('printer_base', 'body', 'casing'), 'y', [PRINTER.x0, BASE.y1 - 0.05, PRINTER.z0], [PRINTER.x1, 4.75, PRINTER.z1], 0.2);
  slab(P('printer_cover', 'body', 'trim'), 'y', [2.35, 4.7, -3.4], [6.15, 5.05, 2.4], 0.1);
  slab(P('printer_rear', 'body', 'casing'), 'y', [2.3, 4.7, 2.6], [6.2, 6.2, 4.9], 0.2);
  // pole-mounted operator display on the keypad housing's flat rear top
  slab(P('display_pedestal', 'body', 'trim'), 'y', [POLE[0] - 1.3, WEDGE.top - 0.05, POLE[1] - 0.65], [POLE[0] + 1.3, WEDGE.top + 0.25, POLE[1] + 0.65], 0);
  cyl(P('display_stem', 'body', 'trim'), 'y', POLE[1], POLE[0], 0.5, WEDGE.top + 0.2, HEAD.y0 + 0.2, 10);
  const head = P('display_head', 'body', 'casing'), win = [HEAD.x0 + 0.65, HEAD.y0 + 0.65, HEAD.x1 - 0.65, HEAD.y1 - 0.65];
  extrude(head, 'z', {outer: orient(rect(HEAD.x0, HEAD.y0, HEAD.x1, HEAD.y1), true), holes: [orient(rect(...win), false)]}, HEAD.z0, HEAD.z0 + 0.4, 0.12);
  slab(head, 'z', [HEAD.x0, HEAD.y0, HEAD.z0 + 0.3], [HEAD.x1, HEAD.y1, HEAD.z1], 0.25);
  slab(P('screen', 'body', 'screen'), 'z', [win[0], win[1], HEAD.z0 + 0.22], [win[2], win[3], HEAD.z0 + 0.31], 0);
  // rear: power and network sockets
  const ports = P('rear_ports', 'body', 'port');
  for (const x of [-5.2, -3.5]) slab(ports, 'z', [x - 0.5, 1.15, BASE.z1 - 0.1], [x + 0.5, 2.25, BASE.z1 + 0.06], 0);
}
// ---------------- drawer: front panel, grip ledge, lock, steel box, till insert, note clips ----------------
{
  slab(P('drawer_front', 'drawer', 'drawer'), 'z', [-FRONT.x, FRONT.y0, FRONT.z0], [FRONT.x, FRONT.y1, FRONT.z1], 0.12);
  slab(P('drawer_grip', 'drawer', 'drawer'), 'z', [-2.4, FRONT.y0, FRONT.z0 - 0.25], [2.4, FRONT.y0 + 0.3, FRONT.z0 + 0.05], 0);
  const lock = P('drawer_lock', 'drawer', 'metal');
  cyl(lock, 'z', 0, 2.0, 0.32, FRONT.z0 - 0.2, FRONT.z0 + 0.05, 12);
  slab(P('lock_slot', 'drawer', 'port'), 'z', [-0.05, 1.8, FRONT.z0 - 0.23], [0.05, 2.2, FRONT.z0 - 0.15], 0);
  // steel drawer box: wall ring + floor (inset so the side faces stay apart)
  const box = P('drawer_box', 'drawer', 'tray'), W = TRAY.wall;
  extrude(box, 'y', {outer: orient(rect(TRAY.z0, -TRAY.x, TRAY.z1, TRAY.x), true), holes: [orient(rect(TRAY.z0 + W, -TRAY.x + W, TRAY.z1 - W, TRAY.x - W), false)]},
    TRAY.y0 + 0.15, TRAY.y1, 0);
  slab(box, 'y', [-TRAY.x + 0.03, TRAY.y0, TRAY.z0 + 0.03], [TRAY.x - 0.03, TRAY.y0 + 0.18, TRAY.z1 - 0.03], 0);
  // till insert: coin / note divider, five coin cups, four note compartments
  const insert = P('till_insert', 'drawer', 'insert'), IX = TRAY.x - W, FLOOR = TRAY.y0 + 0.18, SPLIT = -1.6;
  slab(insert, 'y', [-IX + 0.02, FLOOR - 0.02, TRAY.z0 + W + 0.02], [IX - 0.02, FLOOR + 0.06, TRAY.z1 - W - 0.02], 0);   // insert floor
  slab(insert, 'x', [-IX + 0.02, FLOOR, SPLIT - 0.1], [IX - 0.02, 2.05, SPLIT + 0.1], 0);
  for (let i = 1; i < 5; i++) { const x = -IX + i * (2 * IX / 5); slab(insert, 'x', [x - 0.07, FLOOR, TRAY.z0 + W + 0.02], [x + 0.07, 1.7, SPLIT - 0.1], 0); }
  for (let i = 1; i < 4; i++) { const x = -IX + i * (2 * IX / 4); slab(insert, 'x', [x - 0.08, FLOOR, SPLIT + 0.1], [x + 0.08, 2.15, TRAY.z1 - W - 0.02], 0); }
  // spring clips: a flat arm over each note compartment, hinged on a block at the back
  const clips = P('note_clips', 'drawer', 'metal');
  for (let i = 0; i < 4; i++) {
    const x = -IX + (i + 0.5) * (2 * IX / 4);
    slab(clips, 'z', [x - 0.3, 2.12, -0.9], [x + 0.3, 2.24, 4.3], 0);
    slab(clips, 'z', [x - 0.45, FLOOR, 4.2], [x + 0.45, 2.3, 4.85], 0);
  }
}

// ---------------- Base Color ----------------
export const MATS = {   // Base Color, bevel highlight, smoothness open / edge, F0 (LabPBR: < 230 dielectric, 230+ metal, 255 albedo metal)
  casing: {c: [58, 60, 65], hl: 10, sm: 96, se: 118, f0: 20},       // charcoal ABS, satin
  trim:   {c: [74, 77, 83], hl: 10, sm: 100, se: 120, f0: 20},      // printer cover, display pole: a shade lighter
  drawer: {c: [66, 68, 74], hl: 10, sm: 98, se: 120, f0: 20},       // drawer front
  keybed: {c: [36, 37, 41], hl: 6, sm: 92, se: 104, f0: 20},
  key:    {c: [112, 115, 121], hl: 12, sm: 122, se: 140, f0: 20},   // moulded keycaps, slightly glossier
  enter:  {c: [158, 160, 164], hl: 12, sm: 122, se: 140, f0: 20},
  screen: {c: [18, 26, 34], hl: 4, sm: 200, se: 200, f0: 20},       // dark glass, glossy
  tray:   {c: [56, 58, 62], hl: 8, sm: 90, se: 108, f0: 20},        // painted steel drawer box
  insert: {c: [40, 41, 45], hl: 6, sm: 84, se: 100, f0: 20},        // black plastic till insert
  metal:  {c: [160, 163, 168], hl: 16, sm: 140, se: 165, f0: 255},  // chrome lock face, note clips
  rubber: {c: [26, 26, 28], hl: 4, sm: 40, se: 48, f0: 20},
  cavity: {c: [20, 20, 22], hl: 0, sm: 60, se: 60, f0: 20},
  port:   {c: [22, 22, 24], hl: 2, sm: 70, se: 76, f0: 20},
};
const ATLAS = 512, PAD = 2;
const UV = unwrap(PARTS, {atlas: ATLAS, pad: PAD, startS: 40, stepS: 0.25});
for (const p of PARTS) assert(p.f.every(f => UV.faceUV.get(p)?.has(f)), 'unmapped face in ' + p.name);
const DUMMY = 'data:image/png;base64,' + png(Buffer.from([0, 0, 0, 255]), 1, 1).toString('base64');
const painted = paint({PARTS, islands: UV.islands, S: UV.S, uvOf: UV.uvOf, atlas: ATLAS, pad: PAD, MATS, ZONED: new Set(), groupInfo: new Map(),
  sourceGroups: () => [], refTexture: {source: DUMMY}, refUvWidth: 1, background: {c: [...MATS.casing.c, 255], s: [96, 20, 0, 255], n: [128, 128, 255, 255]}});

// ---------------- source (Free Model) ----------------
const ID = 'cash_register';
const uuid = s => { const h = createHash('sha256').update('afl-cash-register-v2:' + s).digest('hex'); return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-8${h.slice(17, 20)}-${h.slice(20, 32)}`; };
const r12 = v => +v.toFixed(12) || 0;
const RIG = [['body', [0, 0, 0]], ['drawer', DRAWER_PIVOT]];   // [bone, pivot, parent]
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
  return {meta: {format_version: '5.0', model_format: 'free', box_uv: false}, name: ID, model_identifier: '', visible_box: [1, 1, 0],
    variable_placeholders: '', variable_placeholder_buttons: [], timeline_setups: [], unhandled_root_fields: {}, resolution: {width: ATLAS, height: ATLAS},
    elements, groups, outliner: RIG.map(([name]) => nodes.get(name)),
    textures: [{name: ID + '.png', relative_path: `textures/${ID}.png`, folder: '', namespace: '', id: '0', group: '', scope: 0,
      width: ATLAS, height: ATLAS, uv_width: ATLAS, uv_height: ATLAS, particle: false, use_as_default: false, layers_enabled: false, sync_to_project: '',
      file_format: 'png', render_mode: 'default', render_sides: 'auto', wrap_mode: 'limited', pbr_channel: 'color', fps: 7, frame_time: 1,
      frame_order_type: 'loop', frame_order: '', frame_interpolate: false, visible: true, internal: true, saved: true, uuid: uuid('texture'),
      source: 'data:image/png;base64,' + painted.PNG[0].toString('base64')}],
    animations: []};
})();

// ---------------- runtime ----------------
const geo = {format_version: '1.12.0', 'minecraft:geometry': [{description: {identifier: 'geometry.' + ID, texture_width: ATLAS, texture_height: ATLAS,
  visible_bounds_width: 2, visible_bounds_height: 2, visible_bounds_offset: [0, 0.5, 0]},
  bones: RIG.map(([name, origin]) => ({name, pivot: [-origin[0] || 0, origin[1], origin[2]]}))}]};
const sidecar = convert(source, geo, {}, ID + '.bbmodel', 2);
const meshText = serializeCompact(sidecar);

const r3 = v => +v.toFixed(3) || 0, r6 = v => +v.toFixed(6) || 0;
const posed = (p, t) => p.bone === 'drawer' ? p.v.map(q => [q[0], q[1], q[2] - TRAVEL * t]) : p.v;
const aabbOf = pts => [0, 1, 2].map(k => Math.min(...pts.map(q => q[k]))).concat([0, 1, 2].map(k => Math.max(...pts.map(q => q[k]))));
const bounds = (() => {
  const b = aabbOf(PARTS.flatMap(p => [...posed(p, 0), ...posed(p, 1)])), m = 0.25;
  return [(b[0] - m + 8) / 16, (b[1] - m) / 16, (b[2] - m + 8) / 16, (b[3] + m + 8) / 16, (b[4] + m) / 16, (b[5] + m + 8) / 16].map(r6);
})();
const profile = {format_version: 1, geometry: `apocalypse_firstlight:geo/${ID}.geo.json`,
  texture: `apocalypse_firstlight:textures/block/${ID}.png`, origin: [0, 0, 0], scale: [1, 1, 1], facing: 'horizontal', bounds,
  parts: Object.fromEntries(RIG.map(([name, origin]) => [name, {pivot: origin.map(v => r6(v / 16))}])),
  // linear both ways: the spring-loaded drawer runs into its stop and the pushed drawer into its catch at full speed, so
  // both end on a hard stop at the last frame (0.30 s), where tools/build-cash-register-sounds-v1.mjs puts the impacts
  animations: {open: {duration_ticks: 6, easing: 'linear', transforms: {drawer: {translation: [0, 0, r6(-TRAVEL / 16)]}}}}};

// Mesh Shape profile. Closed: a few boxes around the housing, keypad (two steps along the slope), printer and display; the
// whole register is the 'drawer' region (any aim opens the drawer), the prompt sits on the drawer front. Open: the same
// body plus the drawn-out drawer for selection only (no collision, so it never shoves the operator); 'interior' = the tray
// outside the housing, 'drawer' = the drawer front (closes it). Region boxes coincide with selection boxes (the runtime only
// accepts region hits within 0.03 block behind the selection surface).
const box = (a, b) => [...a, ...b].map(r3);
const BODY_BOXES = [
  box([-BASE.x, 0, FRONT.z0 - 0.25], [BASE.x, BASE.y1, BASE.z1 + 0.06]),
  box([WEDGE.x0, BASE.y1, WEDGE.z0], [WEDGE.x1, 5.0, WEDGE.z1]), box([WEDGE.x0, 5.0, -1.5], [WEDGE.x1, WEDGE.top + 0.3, WEDGE.z1]),
  box([PRINTER.x0, BASE.y1, PRINTER.z0], [PRINTER.x1, 6.2, PRINTER.z1]),
  box([POLE[0] - 0.55, WEDGE.top, POLE[1] - 0.55], [POLE[0] + 0.55, HEAD.y0, POLE[1] + 0.55]),
  box([HEAD.x0, HEAD.y0, HEAD.z0], [HEAD.x1, HEAD.y1, HEAD.z1])];
const OPEN_FRONT = box([-FRONT.x, FRONT.y0, FRONT.z0 - 0.25 - TRAVEL], [FRONT.x, FRONT.y1, FRONT.z1 - TRAVEL]);
const OPEN_TRAY = box([-TRAY.x, TRAY.y0, TRAY.z0 - TRAVEL], [TRAY.x, TRAY.y1, BASE.z0]);
export const SHAPES = {format_version: 1, units: 'px', cells_y: 1, states: {
  closed: {physical: BODY_BOXES, selection: BODY_BOXES, interaction: {drawer: {boxes: BODY_BOXES, anchor: 'drawer_front'}},
    anchors: {drawer_front: [0, 1.75, FRONT.z0 - 0.3].map(r3)}},
  open: {physical: BODY_BOXES, selection: [...BODY_BOXES, OPEN_FRONT, OPEN_TRAY],
    interaction: {interior: {boxes: [OPEN_TRAY], anchor: 'tray'}, drawer: {boxes: [OPEN_FRONT], anchor: 'drawer_front'}},
    anchors: {tray: [0, TRAY.y1, (TRAY.z0 - TRAVEL + BASE.z0) / 2].map(r3), drawer_front: [0, 1.75, FRONT.z0 - 0.3 - TRAVEL].map(r3)}},
}};

// item model: builtin/entity, block-item style views; the GUI view is fitted from the projected closed mesh
const itemDisplay = (() => {
  const rot = ([ax, ay]) => q => { const a = ay * Math.PI / 180, b = ax * Math.PI / 180, x = q[0] * Math.cos(a) + q[2] * Math.sin(a), z = -q[0] * Math.sin(a) + q[2] * Math.cos(a);
    return [x, q[1] * Math.cos(b) - z * Math.sin(b), q[1] * Math.sin(b) + z * Math.cos(b)]; };
  const pts = PARTS.flatMap(p => p.v).map(q => [q[0], q[1] - 8, q[2]]).map(rot([30, 225]));
  const xs = pts.map(q => q[0]), ys = pts.map(q => q[1]), s = r3(13 / Math.max(Math.max(...xs) - Math.min(...xs), Math.max(...ys) - Math.min(...ys)));
  const cx = (Math.max(...xs) + Math.min(...xs)) / 2, cy = (Math.max(...ys) + Math.min(...ys)) / 2, S3 = v => [v, v, v];
  return {gui: {rotation: [30, 225, 0], translation: [r3(-s * cx), r3(-s * cy), 0], scale: S3(s)},
    ground: {rotation: [0, 0, 0], translation: [0, 3, 0], scale: S3(0.3)},
    fixed: {rotation: [0, 180, 0], translation: [0, 0, 0], scale: S3(0.6)},
    thirdperson_righthand: {rotation: [75, 315, 0], translation: [0, 2.5, 0], scale: S3(0.4)},
    firstperson_righthand: {rotation: [0, 315, 0], translation: [0, 0, 0], scale: S3(0.45)}};
})();
const itemModel = {parent: 'builtin/entity', textures: {particle: `apocalypse_firstlight:block/${ID}`}, display: itemDisplay};
const blockModel = {loader: 'apocalypse_firstlight:static_mesh', textures: {particle: `apocalypse_firstlight:block/${ID}`}};
const blockstate = {variants: {'': {model: `apocalypse_firstlight:block/${ID}`}}};

const bb = path.join(ROOT, 'src/main/blockbench'), assets = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight');
const shapeFile = path.join(ROOT, `src/main/resources/data/apocalypse_firstlight/mesh_shapes/${ID}.json`);
const outputs = [[path.join(bb, ID + '.bbmodel'), JSON.stringify(source)], [path.join(assets, `geo/${ID}.geo.json`), JSON.stringify(geo, null, 2) + '\n'],
  [path.join(assets, `meshes/${ID}.aflmesh.json`), meshText], [path.join(assets, `block_mesh_profiles/${ID}.json`), JSON.stringify(profile, null, 2) + '\n'],
  [shapeFile, JSON.stringify(SHAPES, null, 2) + '\n'], [path.join(assets, `models/item/${ID}.json`), JSON.stringify(itemModel, null, 2) + '\n'],
  [path.join(assets, `models/block/${ID}.json`), JSON.stringify(blockModel, null, 2) + '\n'], [path.join(assets, `blockstates/${ID}.json`), JSON.stringify(blockstate, null, 2) + '\n'],
  ...['', '_s', '_n'].flatMap((k, i) => [[path.join(bb, `textures/${ID}${k}.png`), painted.PNG[i]], [path.join(assets, `textures/block/${ID}${k}.png`), painted.PNG[i]]])];

const faces = sidecar.parts.flatMap(p => p.faces), zf = zFightLevels(PARTS, new Map());
export const stats = {triangles: faces.reduce((s, q) => s + q.length - 2, 0), parts: sidecar.parts.length, texelsPerPx: UV.S, islands: UV.islands.length,
  coplanarOverlaps: zf.unresolved.length, bounds, closed: aabbOf(PARTS.flatMap(p => p.v)).map(r3), gui: itemDisplay.gui,
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
    ['', '_s', '_n'].forEach((k, i) => fs.writeFileSync(path.join(dir, `${ID}${k}.png`), painted.PNG[i]));
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
