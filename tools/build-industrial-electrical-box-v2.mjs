// Industrial Electrical Box V2: wall-mounted electrical cabinet as Pure Mesh + 512 LabPBR atlas, rendered by the AFL
// Animated Block Mesh Runtime (bones 'body', 'door' with child 'latch'; channels 'open' (door) and 'unlock' (latch quarter
// turn)) and in inventories by AflStaticMeshItemRenderer;
// shapes / interaction regions / prompt anchors as an AFL Mesh Shape profile. Openable, 9-slot searchable container.
//   node tools/build-industrial-electrical-box-v2.mjs                 -> writes source, runtime geo / sidecar / profile / maps / shapes / item model
//   node tools/build-industrial-electrical-box-v2.mjs --check         -> verifies every output is up to date
//   node tools/build-industrial-electrical-box-v2.mjs --preview DIR   -> writes only geo / sidecar / maps into DIR (offline review)
// Frame (px): block bottom centre at the origin, front toward -Z (NORTH at facing=north), the supporting wall at z = +8,
// +X = the viewer's left. Style kept from V1: grey painted steel box, light grey front frame, charcoal door panel, black
// electrical warning sign with a yellow lightning bolt. New: 4 px deep body, hinged door (hinges on the viewer's left,
// quarter-turn latch on the right), interior with a galvanised mounting plate, a DIN rail with six breakers, a cable duct
// and a terminal row; one cable gland under the box. Plain surfaces, no painted wear.
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
export const BW = 5, Y0 = 2, Y1 = 14, Z_FRONT = 4.0, Z_BACK = 7.98, WALL = 0.4;   // body half width, height, depth
export const DOOR = {x: 4.75, y0: 2.25, y1: 13.75, z0: 3.45, z1: 3.95};           // door slab (0.05 gap to the frame)
export const HINGE = [4.95, 0, 3.7], DOOR_DEGREES = -100;                          // vertical axis on the viewer's left
export const LATCH = [-4.3, 7.6, 3.0], LATCH_DEGREES = 90;                         // quarter-turn latch axis (door normal)
export const SIGN = {cx: 0, cy: 10.2, h: 1.6};                                      // warning sign, half size

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

// ---------------- body: painted steel tray, front frame, interior, hinges, gland ----------------
{
  const shell = P('body_shell', 'body', 'shell');
  // walls as a ring along z (open front), back plate slightly overlapping so no faces share a plane
  extrude(shell, 'z', {outer: orient(rect(-BW, Y0, BW, Y1), true), holes: [orient(rect(-BW + WALL, Y0 + WALL, BW - WALL, Y1 - WALL), false)]}, Z_FRONT, Z_BACK - 0.4, 0.08);
  slab(shell, 'z', [-BW, Y0, Z_BACK - 0.45], [BW, Y1, Z_BACK], 0.08);
  for (const f of shell.f) if (f.tag === 'cap' && Math.abs(shell.v[f.ids[0]][2] - Z_FRONT) < 1e-6) f.mat = 'frame';   // front rim
  // interior: galvanised mounting plate, DIN rail with six breakers (toggles), cable duct, terminal row
  slab(P('mounting_plate', 'body', 'plate'), 'z', [-4.2, 2.8, 7.3], [4.2, 13.2, Z_BACK - 0.42], 0.05);
  slab(P('din_rail', 'body', 'hardware'), 'z', [-3.8, 9.2, 6.95], [3.8, 10.0, 7.32], 0);
  const breakers = P('breakers', 'body', 'breaker'), toggles = P('breaker_toggles', 'body', 'dark');
  for (let i = 0; i < 6; i++) {
    const x0 = -3.6 + i * 1.22;
    slab(breakers, 'z', [x0, 8.3, 5.6], [x0 + 1.12, 10.9, 6.97], 0.06);
    slab(toggles, 'z', [x0 + 0.36, 9.85, 5.3], [x0 + 0.76, 10.45, 5.62], 0);
  }
  slab(P('cable_duct', 'body', 'duct'), 'z', [-3.8, 6.0, 6.1], [3.8, 7.3, 7.32], 0.06);
  slab(P('terminal_row', 'body', 'dark'), 'z', [-3.4, 4.2, 6.3], [3.4, 5.4, 7.32], 0.05);
  // barrel hinges on the hinge axis (the door rotates about the same axis), cable gland under the box
  const hinges = P('hinges', 'body', 'hardware');
  for (const [y0, y1] of [[3.2, 4.6], [11.4, 12.8]]) cyl(hinges, 'y', HINGE[2], HINGE[0], 0.28, y0, y1, 8);
  cyl(P('cable_gland', 'body', 'hardware'), 'y', 6.0, 2.5, 0.45, Y0 - 0.6, Y0 + 0.05, 10);
}
// ---------------- door: steel leaf, raised panel, warning sign, quarter-turn latch ----------------
{
  slab(P('door_leaf', 'door', 'frame'), 'z', [-DOOR.x, DOOR.y0, DOOR.z0], [DOOR.x, DOOR.y1, DOOR.z1], 0.1);
  slab(P('door_panel', 'door', 'panel'), 'z', [-3.9, 3.1, 3.3], [3.9, 12.9, DOOR.z0 + 0.02], 0.08);
  const sign = P('sign', 'door', 'sign');
  slab(sign, 'z', [SIGN.cx - SIGN.h, SIGN.cy - SIGN.h, 3.18], [SIGN.cx + SIGN.h, SIGN.cy + SIGN.h, 3.32], 0);
  // the sign's front face gets its own high-density UV island (crisp bolt)
  const isFront = f => f.tag === 'cap' && sign.v[f.ids[0]][2] < 3.19, fronts = sign.f.filter(isFront);
  sign.f = sign.f.filter(f => !isFront(f));
  const face = P('sign_face', 'door', 'sign'), moved = new Map();
  assert(fronts.length, 'sign front face missing');
  for (const f of fronts) face.face(f.ids.map(i => moved.get(i) ?? moved.set(i, face.vtx(sign.v[i])).get(i)), [0, 0, -1], 'cap');
  // quarter-turn latch on its own bone (child of the door): T-handle vertical = locked, horizontal = unlocked
  const latch = P('door_latch', 'latch', 'hardware');
  cyl(latch, 'z', -4.3, 7.6, 0.36, 3.0, DOOR.z0 + 0.02, 10);
  slab(latch, 'z', [-4.43, 7.05, 2.75], [-4.17, 8.15, 3.02], 0);     // T-handle bar
}

// ---------------- Base Color zoning ----------------
// warning sign: black plate, yellow lightning bolt (polygon in sign units, edges anti-aliased by distance)
const YELLOW = [188, 172, 72], BLACK = [30, 31, 34], AA = 0.05;
const BOLT = [[0.42, 1.28], [-0.62, -0.02], [-0.02, -0.02], [-0.44, -1.28], [0.66, 0.16], [0.06, 0.16]];
function segDist(p, a, b) { const ab = [b[0] - a[0], b[1] - a[1]], t = Math.max(0, Math.min(1, ((p[0] - a[0]) * ab[0] + (p[1] - a[1]) * ab[1]) / (ab[0] ** 2 + ab[1] ** 2))); return Math.hypot(p[0] - a[0] - ab[0] * t, p[1] - a[1] - ab[1] * t); }
function inPoly(p, L) { let c = false; for (let i = 0, j = L.length - 1; i < L.length; j = i++) if ((L[i][1] > p[1]) !== (L[j][1] > p[1]) && p[0] < (L[j][0] - L[i][0]) * (p[1] - L[i][1]) / (L[j][1] - L[i][1]) + L[i][0]) c = !c; return c; }
function signColor(pos, n) {
  if (n[2] > -0.5) return BLACK;
  const p = [-(pos[0] - SIGN.cx), pos[1] - SIGN.cy];          // viewer's right is -X
  const d = Math.min(...BOLT.map((a, i) => segDist(p, a, BOLT[(i + 1) % BOLT.length]))), s = inPoly(p, BOLT) ? d : -d;
  const k = Math.max(0, Math.min(1, 0.5 + s / AA));
  return BLACK.map((c, i) => c + (YELLOW[i] - c) * k);
}
export const MATS = {   // Base Color, bevel highlight, smoothness open / edge, F0 (LabPBR: < 230 dielectric, 230+ metal, 255 albedo metal)
  shell:    {c: [110, 115, 120], hl: 10, sm: 92, se: 116, f0: 20},    // powder-coated steel: dielectric coat, satin
  frame:    {c: [150, 155, 162], hl: 12, sm: 92, se: 116, f0: 20},    // light grey front frame / door leaf, same coat
  panel:    {c: [69, 70, 79], hl: 8, sm: 88, se: 110, f0: 20},        // charcoal door panel
  sign:     {c: signColor, hl: 4, sm: 104, se: 104, f0: 20},          // printed sign plate
  hardware: {c: [140, 143, 147], hl: 16, sm: 118, se: 150, f0: 255},  // zinc-plated hinges, latch, rail, gland
  plate:    {c: [126, 130, 134], hl: 10, sm: 86, se: 110, f0: 255},   // galvanised mounting plate
  breaker:  {c: [196, 196, 190], hl: 10, sm: 128, se: 140, f0: 20},   // moulded plastic breakers
  dark:     {c: [48, 49, 53], hl: 6, sm: 104, se: 120, f0: 20},       // toggles, terminal blocks
  duct:     {c: [118, 122, 126], hl: 8, sm: 96, se: 110, f0: 20},     // grey PVC cable duct
};
const ATLAS = 512, PAD = 2, BAND = 464;
// everything else unwraps into the left BAND x BAND, the sign face takes a 44 x 44 island in the right strip
const FACE = PARTS.find(p => p.name === 'sign_face'), REST = PARTS.filter(p => p !== FACE);
const UA = unwrap(REST, {atlas: BAND, pad: PAD, startS: 40, stepS: 0.25});
const FACE_ISLAND = (() => {
  const px = BAND + 2, py = 2, W = 44, tex = new Map(FACE.v.map((q, i) => [i, [px + 0.5 + (SIGN.cx + SIGN.h - q[0]) / (2 * SIGN.h) * (W - 1), py + 0.5 + (SIGN.cy + SIGN.h - q[1]) / (2 * SIGN.h) * (W - 1)]]));
  return {part: FACE, faces: FACE.f.map(f => ({f, n: [0, 0, -1]})), px, py, W, H: W, tex};
})();
const UV = {islands: [...UA.islands, FACE_ISLAND], S: UA.S, uvOf: (is, id) => is.tex ? is.tex.get(id) : UA.uvOf(is, id),
  faceUV: new Map([...UA.faceUV, [FACE, new Map(FACE.f.map(f => [f, f.ids.map(id => FACE_ISLAND.tex.get(id))]))]])};
for (const p of PARTS) assert(p.f.every(f => UV.faceUV.get(p)?.has(f)), 'unmapped face in ' + p.name);
const DUMMY = 'data:image/png;base64,' + png(Buffer.from([0, 0, 0, 255]), 1, 1).toString('base64');
const painted = paint({PARTS, islands: UV.islands, S: UV.S, uvOf: UV.uvOf, atlas: ATLAS, pad: PAD, MATS, ZONED: new Set(), groupInfo: new Map(),
  sourceGroups: () => [], refTexture: {source: DUMMY}, refUvWidth: 1, background: {c: [110, 115, 120, 255], s: [92, 20, 0, 255], n: [128, 128, 255, 255]}});

// ---------------- source (Free Model) ----------------
const ID = 'industrial_electrical_box';
const uuid = s => { const h = createHash('sha256').update('afl-electrical-box-v2:' + s).digest('hex'); return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-8${h.slice(17, 20)}-${h.slice(20, 32)}`; };
const r12 = v => +v.toFixed(12) || 0;
const RIG = [['body', [0, 0, 0]], ['door', HINGE], ['latch', LATCH, 'door']];   // [bone, pivot, parent]
const source = (() => {
  const groups = RIG.map(([name, origin]) => ({name, uuid: uuid('group:' + name), export: true, locked: false, scope: 0, selected: false, visibility: true,
    _static: {properties: {}, temp_data: {}}, origin: origin.slice(), rotation: [0, 0, 0], color: 0, children: [], reset: false, shade: true,
    mirror_uv: false, autouv: 0, isOpen: true, primary_selected: false}));
  const nodes = new Map(RIG.map(([name]) => [name, {uuid: uuid('group:' + name), isOpen: true, children: []}]));
  for (const [name, , parent] of RIG) if (parent) nodes.get(parent).children.push(nodes.get(name));   // nested outliner = bone hierarchy
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
    elements, groups, outliner: RIG.filter(([, , parent]) => !parent).map(([name]) => nodes.get(name)),
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
  bones: RIG.map(([name, origin, parent]) => ({name, ...(parent ? {parent} : {}), pivot: [-origin[0] || 0, origin[1], origin[2]]}))}]};
const sidecar = convert(source, geo, {}, ID + '.bbmodel', 2);
const meshText = serializeCompact(sidecar);

const r3 = v => +v.toFixed(3) || 0, r6 = v => +v.toFixed(6) || 0;
const aroundY = (q, pivot, deg) => { const a = deg * Math.PI / 180, x = q[0] - pivot[0], z = q[2] - pivot[2];   // same convention as the locker door
  return [pivot[0] + x * Math.cos(a) + z * Math.sin(a), q[1], pivot[2] - x * Math.sin(a) + z * Math.cos(a)]; };
const aroundZ = (q, pivot, deg) => { const a = deg * Math.PI / 180, x = q[0] - pivot[0], y = q[1] - pivot[1];
  return [pivot[0] + x * Math.cos(a) - y * Math.sin(a), pivot[1] + x * Math.sin(a) + y * Math.cos(a), q[2]]; };
// door swing t, latch turn u (the latch turns about its own axis first, then swings with the door)
const posed = (p, t, u = 0) => p.bone === 'body' ? p.v : p.v.map(q => aroundY(p.bone === 'latch' ? aroundZ(q, LATCH, LATCH_DEGREES * u) : q, HINGE, DOOR_DEGREES * t));
const aabbOf = pts => [0, 1, 2].map(k => Math.min(...pts.map(q => q[k]))).concat([0, 1, 2].map(k => Math.max(...pts.map(q => q[k]))));
const bounds = (() => {
  const all = PARTS.flatMap(p => Array.from({length: 21}, (_, s) => [...posed(p, s / 20, 0), ...posed(p, s / 20, 1)]).flat()), b = aabbOf(all), m = 0.25;
  return [(b[0] - m + 8) / 16, (b[1] - m) / 16, (b[2] - m + 8) / 16, (b[3] + m + 8) / 16, (b[4] + m) / 16, (b[5] + m + 8) / 16].map(r6);
})();
const profile = {format_version: 1, geometry: `apocalypse_firstlight:geo/${ID}.geo.json`,
  texture: `apocalypse_firstlight:textures/block/${ID}.png`, origin: [0, 0, 0], scale: [1, 1, 1], facing: 'horizontal', bounds,
  parts: Object.fromEntries(RIG.map(([name, origin, parent]) => [name, {...(parent ? {parent} : {}), pivot: origin.map(v => r6(v / 16))}])),
  animations: {open: {duration_ticks: 10, easing: 'ease_in_out', transforms: {door: {rotation: [0, DOOR_DEGREES, 0]}}},
    unlock: {duration_ticks: 4, easing: 'ease_in_out', transforms: {latch: {rotation: [0, 0, LATCH_DEGREES]}}}}};

// Mesh Shape profile: closed = the whole box; 'latch' = the quarter-turn handle (lock / unlock), 'door' = the whole front
// (open when unlocked; while locked the block maps every region to unlock). The runtime only accepts a region hit within
// 0.03 block (0.48 px) behind the selection surface, whose front is the latch handle (z = 2.7), so both region boxes must
// start in front of it: the door box at z = 2.6, the latch box 0.05 px further out so the nearer hit picks it when aiming
// at the handle. Open = the body + the open door; the body becomes 'interior', the open door closes it again
const CLOSED = aabbOf(PARTS.flatMap(p => p.v)).map(r3);
const BODY = aabbOf(PARTS.filter(p => p.bone === 'body').flatMap(p => p.v)).map(r3);
const DOOR_OPEN = aabbOf(PARTS.filter(p => p.bone !== 'body').flatMap(p => posed(p, 1, 1))).map(r3);
const BOX = [-BW, Y0, DOOR.z0 - 0.75, BW, Y1, Z_BACK].map(r3);
export const SHAPES = {format_version: 1, units: 'px', cells_y: 1, states: {
  closed: {physical: [BOX], selection: [BOX],
    interaction: {latch: {boxes: [[-4.85, 6.85, DOOR.z0 - 0.9, -3.75, 8.35, DOOR.z0 + 0.05].map(r3)], anchor: 'latch'},
      door: {boxes: [[-BW, Y0, DOOR.z0 - 0.85, BW, Y1, Z_BACK].map(r3)], anchor: 'latch'}},
    anchors: {latch: [-4.3, 7.6, 2.7].map(r3)}},
  open: {physical: [[-BW, Y0, Z_FRONT, BW, Y1, Z_BACK].map(r3), DOOR_OPEN], selection: [[-BW, Y0, Z_FRONT, BW, Y1, Z_BACK].map(r3), DOOR_OPEN],
    interaction: {interior: {boxes: [[-BW, Y0, Z_FRONT - 0.1, BW, Y1, Z_BACK].map(r3)], anchor: 'opening'}, door: {boxes: [DOOR_OPEN], anchor: 'door_edge'}},
    anchors: {opening: [0, 8, Z_FRONT - 0.2].map(r3), door_edge: aroundY([-4.3, 7.6, 2.7], HINGE, DOOR_DEGREES).map(r3)}},
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

const bb = path.join(ROOT, 'src/main/blockbench'), assets = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight');
const shapeFile = path.join(ROOT, `src/main/resources/data/apocalypse_firstlight/mesh_shapes/${ID}.json`);
const outputs = [[path.join(bb, ID + '.bbmodel'), JSON.stringify(source)], [path.join(assets, `geo/${ID}.geo.json`), JSON.stringify(geo, null, 2) + '\n'],
  [path.join(assets, `meshes/${ID}.aflmesh.json`), meshText], [path.join(assets, `block_mesh_profiles/${ID}.json`), JSON.stringify(profile, null, 2) + '\n'],
  [shapeFile, JSON.stringify(SHAPES, null, 2) + '\n'], [path.join(assets, `models/item/${ID}.json`), JSON.stringify(itemModel, null, 2) + '\n'],
  ...['', '_s', '_n'].flatMap((k, i) => [[path.join(bb, `textures/${ID}${k}.png`), painted.PNG[i]], [path.join(assets, `textures/block/${ID}${k}.png`), painted.PNG[i]]])];

const faces = sidecar.parts.flatMap(p => p.faces), zf = zFightLevels(PARTS, new Map());
export const stats = {triangles: faces.reduce((s, q) => s + q.length - 2, 0), parts: sidecar.parts.length, texelsPerPx: UV.S, islands: UV.islands.length,
  coplanarOverlaps: zf.unresolved.length, bounds, closed: CLOSED, body: BODY, doorOpen: DOOR_OPEN, gui: itemDisplay.gui,
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
