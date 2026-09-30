// Industrial Locker V2: steel single-door locker as Pure Mesh + 512 LabPBR atlas, rendered in the world by the AFL Animated
// Block Mesh Runtime (bones 'body' and 'door', channel 'open') and in inventories by AflStaticMeshItemRenderer.
//   node tools/build-industrial-locker-v2.mjs            -> writes source, runtime geo / sidecar / profile / maps / mesh shapes
//   node tools/build-industrial-locker-v2.mjs --check    -> verifies every output is up to date
// Frame (Blockbench source, px): block bottom centre at the origin, 2 blocks tall (y 0..32), the door faces -Z (NORTH at
// facing=north), +X is the viewer's LEFT when standing in front of it. Footprint = the unchanged NORTH collision box
// x 2..14 / z 1..14 px (-6..6 / -7..6 here). The door hinges on the viewer's left (+X), the handle and lock sit right.
// Materials: cool grey powder-coated steel (a coating in LabPBR terms: F0 24, semi-matte), darker interior, galvanized
// hardware (handle, hinges, lock, rods, hooks). Clean, no rust or painted marks (AFL low-noise rule).
import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {convert, serializeCompact} from './export-afl-mesh.mjs';
import {Part, AX, extrude, add, sub, mul, dot, norm, newell, area2, unwrap, paint, png, zFightLevels} from './cube-slab-mesh-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
function assert(c, m) { if (!c) throw new Error(m); }
const PARTS = [], P = (name, bone, mat) => { const p = new Part(name, bone, mat); PARTS.push(p); return p; };
const orient = (L, ccw) => (area2(L) > 0) === ccw ? L : L.slice().reverse();

// ---------------- dimensions (px) ----------------
export const W = 6, BACK = 6, FZ = -6.3, T = 0.5, TOP = 31.6, HEIGHT = 32, PLINTH = 2.2;
export const DOOR = {x0: -5.95, x1: 5.85, y0: 2.35, y1: 31.45, z0: -7.0, z1: -6.35};
export const HINGE = [6.05, 0, -6.62];                  // door pivot: axis of the three knuckles (vertical)
export const HANDLE = {x0: -4.85, x1: -4.35, y0: 15.8, y1: 19.4};
export const OPEN_DEGREES = -100;                        // about +Y: the free edge swings out toward -Z
const KNUCKLES = [[4.0, 5.6], [16.1, 17.7], [28.2, 29.8]], KNUCKLE_R = 0.3;

// ---------------- primitives ----------------
// axis-aligned slab extruded along ax (x: plane (z, y), y: plane (z, x), z: plane (x, y)) with chamfer c
function slab(part, ax, a, b, c = 0) {
  const [ku, kv, ka] = ax === 'x' ? [2, 1, 0] : ax === 'y' ? [2, 0, 1] : [0, 1, 2];
  const R = [[a[ku], a[kv]], [b[ku], a[kv]], [b[ku], b[kv]], [a[ku], b[kv]]];
  extrude(part, ax, {outer: orient(R, true), holes: []}, a[ka], b[ka], c);
}
// cylinder along ax around plane centre (cu, cv) of that axis' plane, fan caps
function cyl(part, ax, cu, cv, r, a0, a1, seg, caps = [true, true]) {
  const A = AX[ax], ang = i => Math.PI / seg + 2 * Math.PI * i / seg;
  const ring = a => Array.from({length: seg}, (_, i) => part.vtx(A.to3(cu + r * Math.cos(ang(i)), cv + r * Math.sin(ang(i)), a)));
  const r0 = ring(a0), r1 = ring(a1);
  for (let i = 0; i < seg; i++) { const j = (i + 1) % seg, m = (ang(i) + ang(j)) / 2;
    part.face([r0[i], r0[j], r1[j], r1[i]], A.to3(Math.cos(m), Math.sin(m), 0), 'side'); }
  [[r0, a0, -1, caps[0]], [r1, a1, 1, caps[1]]].forEach(([rg, a, s, on]) => { if (!on) return;
    const c = part.vtx(A.to3(cu, cv, a));
    for (let i = 0; i < seg; i++) part.face([c, rg[i], rg[(i + 1) % seg]], mul(A.n, s), 'cap'); });
}
// stamped louver fin: a wedge whose top edge lies on the door face and whose open underside (dark) faces down
function louver(part, x0, x1, yTop, drop, out) {
  const z = DOOR.z0, v = (x, y, zz) => part.vtx([x, y, zz]);
  const a0 = v(x0, yTop, z), b0 = v(x0, yTop - drop, z - out), c0 = v(x0, yTop - drop, z);
  const a1 = v(x1, yTop, z), b1 = v(x1, yTop - drop, z - out), c1 = v(x1, yTop - drop, z);
  part.face([a0, a1, b1, b0], [0, out, -drop], 'side');
  part.face([b0, b1, c1, c0], [0, -1, 0], 'wall', 'louverGap');
  part.face([a0, b0, c0], [-1, 0, 0], 'side');
  part.face([a1, c1, b1], [1, 0, 0], 'side');
}

// ---------------- body ----------------
{
  const s = P('side_left', 'body', 'coat'), r = P('side_right', 'body', 'coat');
  slab(s, 'x', [W - T, 0, FZ], [W, TOP, BACK], 0.08);
  slab(r, 'x', [-W, 0, FZ], [-W + T, TOP, BACK], 0.08);
  slab(P('back', 'body', 'coat'), 'z', [-W + T, 0, BACK - T], [W - T, TOP, BACK], 0.08);
  // top plate: folded flange overhanging the carcass a little on the front and sides
  slab(P('top', 'body', 'coat'), 'y', [-W - 0.15, TOP, FZ - 0.15], [W + 0.15, HEIGHT, BACK + 0.15], 0.1);
  slab(P('top_rail', 'body', 'coat'), 'z', [-W + T, 30.4, FZ], [W - T, TOP, FZ + T], 0);
  slab(P('floor_lip', 'body', 'coat'), 'z', [-W + T, PLINTH, FZ], [W - T, 3.0, FZ + T], 0);
  slab(P('floor', 'body', 'coat'), 'y', [-W + T, PLINTH, FZ + T], [W - T, 2.7, BACK - T], 0);
  slab(P('plinth', 'body', 'dark'), 'z', [-W + T, 0, -5.6], [W - T, PLINTH, -5.1], 0);       // recessed toe kick
  // interior: hat shelf with a folded front lip, hanging rod, two coat hooks on the back wall
  slab(P('shelf', 'body', 'coat'), 'y', [-W + T, 26.0, -5.6], [W - T, 26.35, BACK - T], 0);
  slab(P('shelf_lip', 'body', 'coat'), 'z', [-W + T, 25.6, -5.6], [W - T, 26.0, -5.3], 0);
  cyl(P('hanging_rod', 'body', 'zinc'), 'x', 0.8, 24.6, 0.2, -W + T, W - T, 8);
  for (const x of [-3, 3]) {
    const h = P(`hook_${x < 0 ? 'right' : 'left'}`, 'body', 'zinc');
    slab(h, 'z', [x - 0.12, 20.2, 4.5], [x + 0.12, 20.45, BACK - T], 0);
    slab(h, 'z', [x - 0.12, 20.45, 4.5], [x + 0.12, 20.95, 4.75], 0);
  }
}
// ---------------- door (bone 'door', pivot = HINGE) ----------------
{
  slab(P('door_panel', 'door', 'coat'), 'z', [DOOR.x0, DOOR.y0, DOOR.z0], [DOOR.x1, DOOR.y1, DOOR.z1], 0.12);
  const fins = P('door_louvers', 'door', 'coat');
  for (const top of [29.6, 29.0, 28.4, 27.8, 27.2, 6.8, 6.2, 5.6, 5.0, 4.4]) louver(fins, -3.3, 3.2, top, 0.42, 0.22);
  slab(P('lock_plate', 'door', 'zinc'), 'z', [-5.25, 14.8, -7.1], [-3.95, 20.4, DOOR.z0], 0);
  // D pull: side profile in (z, y) extruded across x
  const pull = [[-7.1, HANDLE.y0], [-7.1, 16.3], [-7.55, 16.3], [-7.55, 18.9], [-7.1, 18.9], [-7.1, HANDLE.y1], [-7.8, HANDLE.y1], [-7.8, HANDLE.y0]];
  extrude(P('handle', 'door', 'zinc'), 'x', {outer: orient(pull, true), holes: []}, HANDLE.x0, HANDLE.x1, 0.06);
  cyl(P('lock_cylinder', 'door', 'lock'), 'z', -4.6, 15.2, 0.3, -7.32, -7.1, 10, [true, false]);
  const hinge = P('hinges', 'door', 'zinc');
  for (const [y0, y1] of KNUCKLES) cyl(hinge, 'y', HINGE[2], HINGE[0], KNUCKLE_R, y0, y1, 8);
  // back of the door: vertical stiffener channel, latch cam behind the handle, lock rod in two guides
  slab(P('door_stiffener', 'door', 'coat'), 'z', [-0.75, 3.4, DOOR.z1], [0.65, 30.4, -5.9], 0.05);
  slab(P('latch_cam', 'door', 'zinc'), 'z', [-5.1, 16.4, DOOR.z1], [-4.1, 18.8, -5.95], 0);
  const rod = P('lock_rod', 'door', 'zinc');
  cyl(rod, 'y', -6.1, -4.6, 0.14, 3.6, 29.6, 6);
  for (const y of [6.0, 27.0]) slab(rod, 'z', [-4.95, y, DOOR.z1], [-4.25, y + 0.5, -5.88], 0);
}
// interior-facing faces of the carcass take the darker interior finish (and read as such when the door is open)
{
  const IN = {min: [-W + T - 1e-3, 2.7 - 1e-3, FZ - 1e-3], max: [W - T + 1e-3, TOP + 1e-3, BACK - T + 1e-3]}, mid = [0, 17, 0];
  for (const p of PARTS) if (p.bone === 'body' && p.mat === 'coat') for (const f of p.f) {
    const P3 = f.ids.map(i => p.v[i]), c = P3.reduce((a, q) => add(a, mul(q, 1 / P3.length)), [0, 0, 0]), n = norm(newell(P3));
    if (c.every((v, k) => v >= IN.min[k] && v <= IN.max[k]) && dot(n, sub(mid, c)) > 0 && Math.abs(c[2] - FZ) > 1e-3) f.mat = 'interior';
  }
}

// ---------------- Base Color zoning ----------------
// The Base Color alone has to read as a powder-coated steel locker with shaders off; PBR only adds on top. Structural
// zones get restrained tone and temperature steps, a low-frequency height / depth falloff stands in for ambient
// occlusion, and the only wear is where hands and feet touch. No per-pixel noise, rust or dirt. Materials (hence UV
// islands, _s and _n) are unchanged: these functions vary only the colour, per part and position.
const lerp3 = (a, b, t) => a.map((v, i) => v + (b[i] - v) * t), scale3 = (c, k) => c.map(v => v * k);
const smooth = (e0, e1, x) => { const t = Math.max(0, Math.min(1, (x - e0) / (e1 - e0))); return t * t * (3 - 2 * t); };
export const TONE = {
  door: [73, 79, 89],       // cooler blue-grey: the face that reads first
  doorBack: [63, 68, 77],
  louver: [64, 69, 78],     // stamped fins a step darker than the flat door
  side: [70, 73, 79],       // carcass sides: a touch warmer and duller than the door
  top: [80, 84, 90],        // top flange catches the light
  frame: [65, 69, 76],      // carcass front edges and rails seen around the door
  back: [61, 64, 69],
  interior: [48, 53, 61],   // inside: darker and colder
  shelf: [52, 57, 65],
  plinth: [35, 36, 38],
  gap: [8, 9, 11],
};
const WORN = [93, 96, 100];   // coating polished by hands: lighter and less blue, never bare metal
const HAND = [HANDLE.x0 / 2 + HANDLE.x1 / 2, (HANDLE.y0 + HANDLE.y1) / 2];
const heightFalloff = y => 0.93 + 0.07 * smooth(0, 30, y);   // sky occlusion: the foot of the locker a little darker
function coatColor(pos, n, part) {
  const [x, y, z] = pos, name = part.name;
  let c;
  if (name === 'door_panel') c = n[2] < -0.5 ? TONE.door : n[2] > 0.5 ? TONE.doorBack : scale3(TONE.door, 0.94);
  else if (name === 'door_louvers') c = TONE.louver;
  else if (name === 'door_stiffener') c = TONE.doorBack;
  else if (name === 'top') c = n[1] > 0.5 ? TONE.top : TONE.frame;
  else if (name === 'side_left' || name === 'side_right') c = Math.abs(n[0]) > 0.5 ? TONE.side : TONE.frame;
  else if (name === 'back') c = TONE.back;
  else if (name === 'shelf' || name === 'shelf_lip') return interiorColor(pos, n, part, TONE.shelf);
  else c = TONE.frame;
  c = scale3(c, heightFalloff(y));
  if (name === 'door_panel' && n[2] < -0.5) {
    // hand wear round the handle, and along the free edge at handle height; faint toe darkening at the door foot
    const d = Math.hypot((x - HAND[0]) / 2.4, (y - HAND[1]) / 3.0);
    c = lerp3(c, WORN, 0.22 * (1 - smooth(0.45, 1.0, d)));
    c = lerp3(c, WORN, 0.16 * (1 - smooth(-5.9, -5.2, x)) * (1 - smooth(1.5, 5.0, Math.abs(y - HAND[1]))));
    c = scale3(c, 1 - 0.07 * (1 - smooth(2.4, 5.5, y)));
  }
  if (name === 'door_panel' && n[0] < -0.5) c = lerp3(c, WORN, 0.25 * (1 - smooth(1.5, 5.0, Math.abs(y - HAND[1]))));
  if (name === 'floor_lip' && n[1] > 0.5) c = lerp3(c, WORN, 0.14);   // sill rubbed by things going in and out
  return c;
}
function interiorColor(pos, n, part, base = TONE.interior) {
  const [, y, z] = pos;
  const k = 1 - 0.12 * smooth(-6, 5.5, z) - 0.09 * (1 - smooth(2.7, 9, y)) - 0.07 * smooth(25.5, 31.6, y) - (n[1] < -0.5 ? 0.05 : 0);
  return scale3(base, k);
}
const HARDWARE = {handle: [160, 164, 168], lock_plate: [132, 136, 140], hinges: [98, 101, 106], hanging_rod: [168, 171, 174],
  hook_left: [138, 142, 146], hook_right: [138, 142, 146], latch_cam: [112, 115, 119], lock_rod: [112, 115, 119]};

// ---------------- materials / atlas ----------------
export const MATS = {   // Base Color (sRGB or zoning function), bevel highlight, smoothness open / edge, F0 (24 coating, 255 metal)
  coat:      {c: coatColor, hl: 10, sm: 112, se: 138, f0: 24},
  interior:  {c: (pos, n, part) => interiorColor(pos, n, part), hl: 4, sm: 100, se: 112, f0: 24},
  dark:      {c: pos => scale3(TONE.plinth, 0.94 + 0.06 * smooth(0, 2.2, pos[1])), hl: 0, sm: 60, se: 60, f0: 24},
  louverGap: {c: TONE.gap, hl: 0, sm: 40, se: 40, f0: 24},
  zinc:      {c: (pos, n, part) => HARDWARE[part.name] || [146, 150, 154], hl: 16, sm: 118, se: 150, f0: 255},
  lock:      {c: [146, 140, 128], hl: 14, sm: 130, se: 150, f0: 255},   // warm nickel cylinder, apart from the zinc
};
const ATLAS = 512, PAD = 2;   // entity textures have no mipmaps; 2 px gutters are enough
const UV = unwrap(PARTS, {atlas: ATLAS, pad: PAD, startS: 16, stepS: 0.25});
for (const p of PARTS) assert(p.f.every(f => UV.faceUV.get(p)?.has(f)), 'unmapped face in ' + p.name);
const DUMMY = 'data:image/png;base64,' + png(Buffer.from([0, 0, 0, 255]), 1, 1).toString('base64');
const painted = paint({PARTS, islands: UV.islands, S: UV.S, uvOf: UV.uvOf, atlas: ATLAS, pad: PAD, MATS, ZONED: new Set(), groupInfo: new Map(),
  sourceGroups: () => [], refTexture: {source: DUMMY}, refUvWidth: 1, background: {c: [60, 64, 70, 255], s: [110, 24, 0, 255], n: [128, 128, 255, 255]}});

// ---------------- source (Free Model) ----------------
const uuid = s => { const h = createHash('sha256').update('afl-industrial-locker-v2:' + s).digest('hex'); return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-8${h.slice(17, 20)}-${h.slice(20, 32)}`; };
const r12 = v => +v.toFixed(12) || 0;
const RIG = [['body', [0, 0, 0]], ['door', HINGE]];
function buildSource() {
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
  return {meta: {format_version: '5.0', model_format: 'free', box_uv: false}, name: 'industrial_locker', model_identifier: '', visible_box: [1, 2, 0],
    variable_placeholders: '', variable_placeholder_buttons: [], timeline_setups: [], unhandled_root_fields: {}, resolution: {width: ATLAS, height: ATLAS},
    elements, groups, outliner: RIG.map(([name]) => nodes.get(name)),
    textures: [{name: 'industrial_locker.png', relative_path: 'textures/industrial_locker.png', folder: '', namespace: '', id: '0', group: '', scope: 0,
      width: ATLAS, height: ATLAS, uv_width: ATLAS, uv_height: ATLAS, particle: false, use_as_default: false, layers_enabled: false, sync_to_project: '',
      file_format: 'png', render_mode: 'default', render_sides: 'auto', wrap_mode: 'limited', pbr_channel: 'color', fps: 7, frame_time: 1,
      frame_order_type: 'loop', frame_order: '', frame_interpolate: false, visible: true, internal: true, saved: true, uuid: uuid('texture'),
      source: 'data:image/png;base64,' + painted.PNG[0].toString('base64')}],
    animations: []};
}
const source = buildSource();

// ---------------- runtime ----------------
const geo = {format_version: '1.12.0', 'minecraft:geometry': [{description: {identifier: 'geometry.industrial_locker', texture_width: ATLAS, texture_height: ATLAS,
  visible_bounds_width: 3, visible_bounds_height: 3, visible_bounds_offset: [0, 1, 0]},
  bones: RIG.map(([name, origin]) => ({name, pivot: [-origin[0] || 0, origin[1], origin[2]]}))}]};
const sidecar = convert(source, geo, {}, 'industrial_locker.bbmodel', 2);
const meshText = serializeCompact(sidecar);

// render bounds (NORTH, block-local corners): the carcass plus every door vertex swept through the whole opening
const r6 = v => +v.toFixed(6);
const bounds = (() => {
  const lo = [Infinity, Infinity, Infinity], hi = [-Infinity, -Infinity, -Infinity], take = q => q.forEach((v, k) => { lo[k] = Math.min(lo[k], v); hi[k] = Math.max(hi[k], v); });
  for (const p of PARTS) for (const q of p.v) {
    if (p.bone === 'body') { take(q); continue; }
    for (let s = 0; s <= 20; s++) { const a = OPEN_DEGREES * s / 20 * Math.PI / 180, x = q[0] - HINGE[0], z = q[2] - HINGE[2];
      take([HINGE[0] + x * Math.cos(a) + z * Math.sin(a), q[1], HINGE[2] - x * Math.sin(a) + z * Math.cos(a)]); }
  }
  const m = 0.25;   // px margin
  return [(lo[0] - m + 8) / 16, (lo[1] - m) / 16, (lo[2] - m + 8) / 16, (hi[0] + m + 8) / 16, (hi[1] + m) / 16, (hi[2] + m + 8) / 16].map(r6);
})();
const profile = {format_version: 1, geometry: 'apocalypse_firstlight:geo/industrial_locker.geo.json',
  texture: 'apocalypse_firstlight:textures/block/industrial_locker.png', origin: [0, 0, 0], scale: [1, 1, 1], facing: 'horizontal', bounds,
  parts: {body: {pivot: [0, 0, 0]}, door: {pivot: HINGE.map(v => r6(v / 16))}},
  animations: {open: {duration_ticks: 10, easing: 'ease_in_out', transforms: {door: {rotation: [0, OPEN_DEGREES, 0]}}}}};

// Mesh Shape profile (docs/rendering/mesh_shape_runtime_v1.md): simplified physical / selection boxes, named interaction
// regions and prompt anchors, in the same px model frame as the mesh. Derived from the mesh constants, never per triangle.
const r3 = v => +v.toFixed(3) || 0;
const swing = (q, deg) => { const a = deg * Math.PI / 180, x = q[0] - HINGE[0], z = q[2] - HINGE[2];
  return [HINGE[0] + x * Math.cos(a) + z * Math.sin(a), q[1], HINGE[2] - x * Math.sin(a) + z * Math.cos(a)]; };
const aabbOf = pts => [0, 1, 2].map(k => Math.min(...pts.map(q => q[k]))).concat([0, 1, 2].map(k => Math.max(...pts.map(q => q[k])))).map(r3);
const DOOR_OPEN = aabbOf(PARTS.filter(p => p.bone === 'door').flatMap(p => p.v.map(q => swing(q, OPEN_DEGREES))));
const LOCK = [(HANDLE.x0 + HANDLE.x1) / 2, (HANDLE.y0 + HANDLE.y1) / 2, -7.8];   // front of the D pull
const BODY = [-W, 0, FZ, W, HEIGHT, BACK], BODY_SELECT = [-W - 0.15, 0, FZ - 0.15, W + 0.15, HEIGHT, BACK + 0.15];
const DOOR_CLOSED = [DOOR.x0, DOOR.y0, DOOR.z0, DOOR.x1, DOOR.y1, FZ];
const PULL = [HANDLE.x0 - 0.05, HANDLE.y0, -7.8, HANDLE.x1 + 0.05, HANDLE.y1, DOOR.z0];
export const SHAPES = {format_version: 1, units: 'px', cells_y: 2, states: {
  closed: {physical: [BODY, DOOR_CLOSED], selection: [BODY_SELECT, DOOR_CLOSED, PULL],
    interaction: {door: {boxes: [[DOOR.x0 - 0.05, DOOR.y0 - 0.05, -7.9, DOOR.x1 + 0.05, DOOR.y1 + 0.05, FZ]], anchor: 'door_lock'}},
    anchors: {door_lock: LOCK}},
  // open: the door keeps one simplified box at its final pose (no per-frame shapes); the whole front becomes 'interior'
  open: {physical: [BODY, DOOR_OPEN], selection: [BODY_SELECT, DOOR_OPEN],
    interaction: {interior: {boxes: [[-W, 0, FZ - 0.6, W, HEIGHT, BACK - T]], anchor: 'interior'}, door: {boxes: [DOOR_OPEN], anchor: 'door_lock'}},
    anchors: {interior: [0, 18, FZ - 0.2], door_lock: swing(LOCK, OPEN_DEGREES).map(r3)}},
}};

const bb = path.join(ROOT, 'src/main/blockbench'), assets = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight');
const shapeFile = path.join(ROOT, 'src/main/resources/data/apocalypse_firstlight/mesh_shapes/industrial_locker.json');
const outputs = [[path.join(bb, 'industrial_locker.bbmodel'), JSON.stringify(source)], [path.join(assets, 'geo/industrial_locker.geo.json'), JSON.stringify(geo, null, 2) + '\n'],
  [path.join(assets, 'meshes/industrial_locker.aflmesh.json'), meshText], [path.join(assets, 'block_mesh_profiles/industrial_locker.json'), JSON.stringify(profile, null, 2) + '\n'],
  [shapeFile, JSON.stringify(SHAPES, null, 2) + '\n'],
  ...['', '_s', '_n'].flatMap((k, i) => [[path.join(bb, `textures/industrial_locker${k}.png`), painted.PNG[i]], [path.join(assets, `textures/block/industrial_locker${k}.png`), painted.PNG[i]]])];

const faces = sidecar.parts.flatMap(p => p.faces), zf = zFightLevels(PARTS, new Map());
export const stats = {triangles: faces.reduce((s, q) => s + q.length - 2, 0), quads: faces.filter(q => q.length === 4).length, tris: faces.filter(q => q.length === 3).length,
  parts: sidecar.parts.length, texelsPerPx: UV.S, islands: UV.islands.length, coplanarOverlaps: zf.unresolved.length, bounds,
  byBone: Object.fromEntries(['body', 'door'].map(b => [b, PARTS.filter(p => p.bone === b).reduce((s, p) => s + p.f.reduce((t, f) => t + f.ids.length - 2, 0), 0)]))};
const isMain = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  console.log(JSON.stringify(stats));
  if (zf.unresolved.length) console.log('COPLANAR', JSON.stringify(zf.unresolved.slice(0, 12)));
  if (process.argv.includes('--check')) {
    for (const [file, data] of outputs) { const cur = fs.existsSync(file) ? fs.readFileSync(file) : null; if (!cur || !cur.equals(Buffer.isBuffer(data) ? data : Buffer.from(data))) throw new Error('stale ' + path.relative(ROOT, file)); }
    console.log('CHECK OK');
  } else {
    fs.mkdirSync(path.join(assets, 'block_mesh_profiles'), {recursive: true});
    fs.mkdirSync(path.dirname(shapeFile), {recursive: true});
    for (const [file, data] of outputs) fs.writeFileSync(file, data);
    console.log('wrote ' + outputs.map(([f]) => path.relative(ROOT, f)).join(', '));
  }
}
