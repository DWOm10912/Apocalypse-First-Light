// Lead Chest V2: squat radiation-shielding cask as Pure Mesh + 512 LabPBR atlas, rendered in the world by the AFL Animated
// Block Mesh Runtime (bones 'body', 'lid', 'latch_left', 'latch_right', channel 'open') and in inventories by
// AflStaticMeshItemRenderer; shapes / interaction regions / prompt anchors as an AFL Mesh Shape profile.
//   node tools/build-lead-chest-v2.mjs            -> writes source, runtime geo / sidecar / profile / maps / mesh shapes
//   node tools/build-lead-chest-v2.mjs --check    -> verifies every output is up to date
// Frame (px): block bottom centre at the origin, front toward -Z (NORTH at facing=north), +X = the viewer's left.
// Design: thick cast-lead body and lid (2 px walls, the lid's stepped plug seats into the opening: the shielding reads at a
// glance when open), wrapped in a dark painted steel frame (corner guards, two body bands, one lid band, forklift skids),
// two over-centre latch clamps on the front, two lift bails on the lid, rear hinge. Lead keeps the purple-grey of the old
// lead_block texture (average [75,72,104]; that block was removed by Material System V1); clean surfaces, no painted marks.
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
export const BW = 6.5, BD = 5.5, WALL = 2, SKID = 1.5, FLOOR_TOP = 3.5, RIM = 10.5;   // body half width / depth, wall
export const LID = {y0: 10.6, y1: 13.0};                                              // 0.1 px seam above the rim
export const PLUG = {hw: BW - WALL - 0.15, hd: BD - WALL - 0.15, y0: 9.3};            // stepped plug, 0.15 px clearance
export const HINGE = [0, 10.55, 5.95], LID_DEGREES = 100;                              // rear hinge axis along X
export const LATCH_X = [3.6, -3.6], LATCH_PIVOT = {y: 8.3, z: -5.95}, LATCH_DEGREES = -120;
const BAND = 0.25, POST = 0.4;

// ---------------- primitives ----------------
function slab(part, ax, a, b, c = 0) {
  const [ku, kv, ka] = ax === 'x' ? [2, 1, 0] : ax === 'y' ? [2, 0, 1] : [0, 1, 2];
  extrude(part, ax, {outer: orient(rect(a[ku], a[kv], b[ku], b[kv]), true), holes: []}, a[ka], b[ka], c);
}
// rectangular ring around Y (plane (z, x)): outer half extents (hx, hz), inner (ix, iz)
function ring(part, hx, hz, ix, iz, y0, y1, c = 0) {
  extrude(part, 'y', {outer: orient(rect(-hz, -hx, hz, hx), true), holes: [orient(rect(-iz, -ix, iz, ix), false)]}, y0, y1, c);
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

// ---------------- body (lead) + steel frame ----------------
{
  const lead = P('body_lead', 'body', 'lead');
  slab(lead, 'y', [-BW, SKID, -BD], [BW, FLOOR_TOP, BD], 0.15);                            // cast floor
  ring(lead, BW, BD, BW - WALL, BD - WALL, FLOOR_TOP, RIM, 0.15);                          // 2 px walls, cut rim on top
  const frame = P('body_frame', 'body', 'steel');
  for (const [y0, y1] of [[2.9, 4.1], [8.6, 9.8]]) ring(frame, BW + BAND, BD + BAND, BW, BD, y0, y1, 0);
  const posts = P('corner_guards', 'body', 'steel');
  for (const sx of [-1, 1]) for (const sz of [-1, 1]) {
    const x0 = sx > 0 ? BW - 0.6 : -BW - POST, x1 = sx > 0 ? BW + POST : -BW + 0.6;
    const z0 = sz > 0 ? BD - 0.6 : -BD - POST, z1 = sz > 0 ? BD + POST : -BD + 0.6;
    slab(posts, 'y', [x0, SKID - 0.1, z0], [x1, RIM - 0.1, z1], 0.08);
  }
  const skids = P('skids', 'body', 'steelDark');
  for (const x of [-4.8, 4.8]) slab(skids, 'x', [x - 1, 0, -BD - 0.3], [x + 1, SKID, BD + 0.3], 0.1);
  const leaves = P('hinge_leaves_body', 'body', 'zinc');
  for (const s of [-1, 1]) slab(leaves, 'z', [s > 0 ? 2.2 : -4.4, 8.8, BD - 0.05], [s > 0 ? 4.4 : -2.2, 10.3, BD + 0.3], 0);
  const plates = P('latch_plates', 'body', 'zinc');
  for (const x of LATCH_X) slab(plates, 'z', [x - 0.7, 7.5, -BD - BAND - 0.3], [x + 0.7, 9.1, -BD - 0.05], 0);
}
// ---------------- lid (lead) + band, bails, hinge knuckles, keepers ----------------
{
  const lid = P('lid_lead', 'lid', 'lead');
  slab(lid, 'y', [-BW, LID.y0, -BD], [BW, LID.y1, BD], 0.2);
  slab(lid, 'y', [-PLUG.hw, PLUG.y0, -PLUG.hd], [PLUG.hw, LID.y0, PLUG.hd], 0.1);
  ring(P('lid_band', 'lid', 'steel'), BW + BAND, BD + BAND, BW, BD, 11.3, 12.3, 0);
  const bails = P('lift_bails', 'lid', 'zinc');
  const bail = [[-3.1, 13.0], [-2.6, 13.0], [-2.6, 13.7], [2.6, 13.7], [2.6, 13.0], [3.1, 13.0], [3.1, 14.1], [-3.1, 14.1]];
  for (const z of [-2.8, 2.8]) extrude(bails, 'z', {outer: orient(bail, true), holes: []}, z - 0.22, z + 0.22, 0);
  const knuckles = P('hinge_knuckles', 'lid', 'zincDark');
  for (const [x0, x1] of [[2.05, 4.55], [-4.55, -2.05]]) cyl(knuckles, 'x', HINGE[2], HINGE[1], 0.45, x0, x1, 8);
  const lidLeaves = P('hinge_leaves_lid', 'lid', 'zinc');
  for (const s of [-1, 1]) slab(lidLeaves, 'z', [s > 0 ? 2.2 : -4.4, 10.8, BD - 0.05], [s > 0 ? 4.4 : -2.2, 12.4, BD + 0.3], 0);
  const keepers = P('latch_keepers', 'lid', 'zinc');
  for (const x of LATCH_X) slab(keepers, 'z', [x - 0.6, 11.4, -BD - BAND - 0.25], [x + 0.6, 12.2, -BD - BAND], 0);
}
// ---------------- latch levers (one bone each, pivot at the lower end) ----------------
for (const [i, x] of LATCH_X.entries()) {
  const lever = P(`latch_${i ? 'right' : 'left'}`, `latch_${i ? 'right' : 'left'}`, 'zinc');
  slab(lever, 'z', [x - 0.45, 8.0, -6.25], [x + 0.45, 11.9, -5.85], 0.05);
  slab(lever, 'z', [x - 0.45, 11.9, -6.25], [x + 0.45, 12.4, -5.55 - BAND], 0);          // hook over the keeper
  cyl(lever, 'x', LATCH_PIVOT.z, LATCH_PIVOT.y, 0.3, x - 0.6, x + 0.6, 8);
}

// ---------------- Base Color zoning (PBR maps are per material, colour varies per part / position) ----------------
const scale3 = (c, k) => c.map(v => v * k);
const smooth = (e0, e1, x) => { const t = Math.max(0, Math.min(1, (x - e0) / (e1 - e0))); return t * t * (3 - 2 * t); };
export const TONE = {lead: [80, 75, 106], leadTop: [88, 83, 114], leadCut: [110, 106, 124], leadInside: [56, 52, 76]};
function leadColor(pos, n, part, f) {
  const [x, y, z] = pos;
  const inCavity = Math.abs(x) < BW - WALL + 0.01 && Math.abs(z) < BD - WALL + 0.01 && y <= RIM + 0.01 && y >= FLOOR_TOP - 0.01;
  if (part.name === 'body_lead' && Math.abs(y - RIM) < 0.01 && n[1] > 0.5) return TONE.leadCut;           // sawn rim
  if (part.name === 'lid_lead' && y < LID.y0 + 0.01 && n[1] < -0.5) return scale3(TONE.leadCut, 0.9);     // lid underside / plug face
  if (part.name === 'lid_lead' && y < LID.y0 && Math.abs(n[1]) < 0.5) return TONE.leadCut;                 // plug sides
  if (part.name === 'body_lead' && (inCavity || f.tag === 'wall')) return scale3(TONE.leadInside, 0.9 + 0.1 * smooth(FLOOR_TOP, RIM, y));
  if (n[1] > 0.5) return TONE.leadTop;
  return scale3(TONE.lead, 0.93 + 0.07 * smooth(SKID, LID.y1, y));
}
export const MATS = {   // Base Color, bevel highlight, smoothness open / edge, F0 (24 coating, 235 = LabPBR lead, 255 metal)
  lead:      {c: leadColor, hl: 8, sm: 62, se: 96, f0: 235},
  steel:     {c: [46, 49, 54], hl: 10, sm: 104, se: 132, f0: 24},
  steelDark: {c: [34, 36, 40], hl: 6, sm: 80, se: 100, f0: 24},
  zinc:      {c: [142, 146, 150], hl: 16, sm: 118, se: 150, f0: 255},
  zincDark:  {c: [100, 103, 108], hl: 14, sm: 110, se: 140, f0: 255},
};
const ATLAS = 512, PAD = 2;
const UV = unwrap(PARTS, {atlas: ATLAS, pad: PAD, startS: 24, stepS: 0.25});
for (const p of PARTS) assert(p.f.every(f => UV.faceUV.get(p)?.has(f)), 'unmapped face in ' + p.name);
const DUMMY = 'data:image/png;base64,' + png(Buffer.from([0, 0, 0, 255]), 1, 1).toString('base64');
const painted = paint({PARTS, islands: UV.islands, S: UV.S, uvOf: UV.uvOf, atlas: ATLAS, pad: PAD, MATS, ZONED: new Set(), groupInfo: new Map(),
  sourceGroups: () => [], refTexture: {source: DUMMY}, refUvWidth: 1, background: {c: [60, 58, 76, 255], s: [80, 24, 0, 255], n: [128, 128, 255, 255]}});

// ---------------- source (Free Model) ----------------
const uuid = s => { const h = createHash('sha256').update('afl-lead-chest-v2:' + s).digest('hex'); return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-8${h.slice(17, 20)}-${h.slice(20, 32)}`; };
const r12 = v => +v.toFixed(12) || 0;
const RIG = [['body', [0, 0, 0]], ['lid', HINGE], ...LATCH_X.map((x, i) => [`latch_${i ? 'right' : 'left'}`, [x, LATCH_PIVOT.y, LATCH_PIVOT.z]])];
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
  return {meta: {format_version: '5.0', model_format: 'free', box_uv: false}, name: 'lead_chest', model_identifier: '', visible_box: [1, 1, 0],
    variable_placeholders: '', variable_placeholder_buttons: [], timeline_setups: [], unhandled_root_fields: {}, resolution: {width: ATLAS, height: ATLAS},
    elements, groups, outliner: RIG.map(([name]) => nodes.get(name)),
    textures: [{name: 'lead_chest.png', relative_path: 'textures/lead_chest.png', folder: '', namespace: '', id: '0', group: '', scope: 0,
      width: ATLAS, height: ATLAS, uv_width: ATLAS, uv_height: ATLAS, particle: false, use_as_default: false, layers_enabled: false, sync_to_project: '',
      file_format: 'png', render_mode: 'default', render_sides: 'auto', wrap_mode: 'limited', pbr_channel: 'color', fps: 7, frame_time: 1,
      frame_order_type: 'loop', frame_order: '', frame_interpolate: false, visible: true, internal: true, saved: true, uuid: uuid('texture'),
      source: 'data:image/png;base64,' + painted.PNG[0].toString('base64')}],
    animations: []};
})();

// ---------------- runtime ----------------
const geo = {format_version: '1.12.0', 'minecraft:geometry': [{description: {identifier: 'geometry.lead_chest', texture_width: ATLAS, texture_height: ATLAS,
  visible_bounds_width: 2, visible_bounds_height: 2, visible_bounds_offset: [0, 0.5, 0]},
  bones: RIG.map(([name, origin]) => ({name, pivot: [-origin[0] || 0, origin[1], origin[2]]}))}]};
const sidecar = convert(source, geo, {}, 'lead_chest.bbmodel', 2);
const meshText = serializeCompact(sidecar);

const r3 = v => +v.toFixed(3) || 0, r6 = v => +v.toFixed(6) || 0;
const aroundX = (q, pivot, deg) => { const a = deg * Math.PI / 180, y = q[1] - pivot[1], z = q[2] - pivot[2];
  return [q[0], pivot[1] + y * Math.cos(a) - z * Math.sin(a), pivot[2] + y * Math.sin(a) + z * Math.cos(a)]; };
const pivotOf = bone => RIG.find(([n]) => n === bone)[1], degOf = bone => bone === 'lid' ? LID_DEGREES : bone === 'body' ? 0 : LATCH_DEGREES;
const posed = (p, t) => p.v.map(q => aroundX(q, pivotOf(p.bone), degOf(p.bone) * t));
const aabbOf = pts => [0, 1, 2].map(k => Math.min(...pts.map(q => q[k]))).concat([0, 1, 2].map(k => Math.max(...pts.map(q => q[k]))));
// render bounds (NORTH, block-local corners): every vertex swept through the whole opening
const bounds = (() => {
  const all = PARTS.flatMap(p => Array.from({length: 21}, (_, s) => posed(p, s / 20)).flat()), b = aabbOf(all), m = 0.25;
  return [(b[0] - m + 8) / 16, (b[1] - m) / 16, (b[2] - m + 8) / 16, (b[3] + m + 8) / 16, (b[4] + m) / 16, (b[5] + m + 8) / 16].map(r6);
})();
const profile = {format_version: 1, geometry: 'apocalypse_firstlight:geo/lead_chest.geo.json',
  texture: 'apocalypse_firstlight:textures/block/lead_chest.png', origin: [0, 0, 0], scale: [1, 1, 1], facing: 'horizontal', bounds,
  parts: Object.fromEntries(RIG.map(([name, origin]) => [name, {pivot: origin.map(v => r6(v / 16))}])),
  animations: {open: {duration_ticks: 14, easing: 'ease_in_out', transforms: {
    lid: {rotation: [LID_DEGREES, 0, 0]}, latch_left: {rotation: [LATCH_DEGREES, 0, 0]}, latch_right: {rotation: [LATCH_DEGREES, 0, 0]}}}}};

// Mesh Shape profile (docs/rendering/mesh_shape_runtime_v1.md): closed = one cask box (+ bails for selection), the whole cask
// is the 'lid' region; open = body + the standing lid, the body becomes 'interior', the standing lid closes it again
const LID_OPEN = aabbOf(PARTS.filter(p => p.bone === 'lid').flatMap(p => posed(p, 1))).map(r3);
const CASK = [-BW - POST, 0, -6.3, BW + POST, LID.y1, BD + 0.9];
export const SHAPES = {format_version: 1, units: 'px', cells_y: 1, states: {
  closed: {physical: [CASK], selection: [CASK, [-3.1, LID.y1, -3.05, 3.1, 14.1, 3.05]],
    interaction: {lid: {boxes: [[-BW - POST, 0, -6.4, BW + POST, 14.2, BD + 0.9]], anchor: 'latch'}},
    anchors: {latch: [0, 11.8, -6.3]}},
  open: {physical: [[-BW - POST, 0, -6.3, BW + POST, RIM, BD + 0.9], LID_OPEN], selection: [[-BW - POST, 0, -6.3, BW + POST, RIM, BD + 0.9], LID_OPEN],
    interaction: {interior: {boxes: [[-BW - POST, 0, -6.4, BW + POST, RIM + 0.6, BD]], anchor: 'opening'}, lid: {boxes: [LID_OPEN], anchor: 'lid_edge'}},
    anchors: {opening: [0, RIM + 0.3, -3.0], lid_edge: aroundX([0, 12.4, -BD - BAND], HINGE, LID_DEGREES).map(r3)}},
}};

const bb = path.join(ROOT, 'src/main/blockbench'), assets = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight');
const shapeFile = path.join(ROOT, 'src/main/resources/data/apocalypse_firstlight/mesh_shapes/lead_chest.json');
const outputs = [[path.join(bb, 'lead_chest.bbmodel'), JSON.stringify(source)], [path.join(assets, 'geo/lead_chest.geo.json'), JSON.stringify(geo, null, 2) + '\n'],
  [path.join(assets, 'meshes/lead_chest.aflmesh.json'), meshText], [path.join(assets, 'block_mesh_profiles/lead_chest.json'), JSON.stringify(profile, null, 2) + '\n'],
  [shapeFile, JSON.stringify(SHAPES, null, 2) + '\n'],
  ...['', '_s', '_n'].flatMap((k, i) => [[path.join(bb, `textures/lead_chest${k}.png`), painted.PNG[i]], [path.join(assets, `textures/block/lead_chest${k}.png`), painted.PNG[i]]])];

const faces = sidecar.parts.flatMap(p => p.faces), zf = zFightLevels(PARTS, new Map());
export const stats = {triangles: faces.reduce((s, q) => s + q.length - 2, 0), parts: sidecar.parts.length, texelsPerPx: UV.S, islands: UV.islands.length,
  coplanarOverlaps: zf.unresolved.length, bounds, lidOpen: LID_OPEN,
  byBone: Object.fromEntries(RIG.map(([b]) => [b, PARTS.filter(p => p.bone === b).reduce((s, p) => s + p.f.reduce((t, f) => t + f.ids.length - 2, 0), 0)]))};
const isMain = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  console.log(JSON.stringify(stats));
  if (zf.unresolved.length) console.log('COPLANAR', JSON.stringify(zf.unresolved.slice(0, 12)));
  if (process.argv.includes('--check')) {
    for (const [file, data] of outputs) { const cur = fs.existsSync(file) ? fs.readFileSync(file) : null; if (!cur || !cur.equals(Buffer.isBuffer(data) ? data : Buffer.from(data))) throw new Error('stale ' + path.relative(ROOT, file)); }
    console.log('CHECK OK');
  } else {
    for (const [file] of outputs) fs.mkdirSync(path.dirname(file), {recursive: true});
    for (const [file, data] of outputs) fs.writeFileSync(file, data);
    console.log('wrote ' + outputs.map(([f]) => path.relative(ROOT, f)).join(', '));
  }
}
