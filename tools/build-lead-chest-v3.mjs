// Lead Chest V3: stainless-clad lead shielding box as Pure Mesh + 512 LabPBR atlas, rendered in the world by the AFL Animated
// Block Mesh Runtime (bones 'body', 'lid', 'latch_left', 'latch_right', channel 'open') and in inventories by
// AflStaticMeshItemRenderer; shapes / interaction regions / prompt anchors as an AFL Mesh Shape profile.
//   node tools/build-lead-chest-v3.mjs                 -> writes source, runtime geo / sidecar / profile / maps / mesh shapes
//   node tools/build-lead-chest-v3.mjs --check         -> verifies every output is up to date
//   node tools/build-lead-chest-v3.mjs --preview DIR   -> writes only geo / sidecar / maps into DIR (offline review)
// Frame (px): block bottom centre at the origin, front toward -Z (NORTH at facing=north), +X = the viewer's left.
// Design (references: commercial lead shielding boxes): brushed stainless shell over a lead liner, so the open rim shows the
// steel / lead sandwich; stepped lid with a lead plug and a black rubber gasket; stainless plinth; full-length rear piano
// hinge; two front draw latches; a fold-flat handle on the lid and a drop handle on each side; a riveted radiation warning
// placard (yellow, black trefoil, no text) on the front. Plain surfaces otherwise, no painted wear.
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
function roundRect(u0, v0, u1, v1, r, seg = 3) {
  const out = [];
  for (const [cu, cv, a0] of [[u1 - r, v0 + r, -90], [u1 - r, v1 - r, 0], [u0 + r, v1 - r, 90], [u0 + r, v0 + r, 180]])
    for (let i = 0; i <= seg; i++) { const a = (a0 + 90 * i / seg) * Math.PI / 180; out.push([cu + r * Math.cos(a), cv + r * Math.sin(a)]); }
  return out;
}

// ---------------- dimensions (px) ----------------
export const BW = 6.5, BD = 5.5, R_OUT = 0.9;                  // shell half width / depth, vertical corner radius
export const SHELL = 0.4, LEAD = 1.7, PLINTH = 1.0;            // stainless skin, lead liner, plinth height
export const FLOOR_TOP = 3.0, RIM = 10.2;                       // cavity floor, rim (top of shell + liner)
export const GASKET = 0.3, LID = {y0: RIM + 0.02 + GASKET, y1: RIM + 0.02 + GASKET + 2.0};
export const IW = BW - SHELL - LEAD, ID = BD - SHELL - LEAD;   // cavity half extents (4.4, 3.4)
export const HINGE = [0, RIM + 0.17, BD + 0.45], LID_DEGREES = 100;
export const LATCH_X = [3.8, -3.8], LATCH_PIVOT = {y: 8.3, z: -BD - 0.35}, LATCH_DEGREES = -120;
export const PLACARD = {cx: 0, cy: 5.7, h: 2.1};                // half size

// ---------------- primitives ----------------
function slab(part, ax, a, b, c = 0) {
  const [ku, kv, ka] = ax === 'x' ? [2, 1, 0] : ax === 'y' ? [2, 0, 1] : [0, 1, 2];
  extrude(part, ax, {outer: orient(rect(a[ku], a[kv], b[ku], b[kv]), true), holes: []}, a[ka], b[ka], c);
}
// rounded rectangle around Y (plane (z, x)), optionally with a rounded rectangular hole
function roundSlab(part, hx, hz, r, y0, y1, c = 0, hole = null) {
  extrude(part, 'y', {outer: orient(roundRect(-hz, -hx, hz, hx, r), true),
    holes: hole ? [orient(roundRect(-hole.hz, -hole.hx, hole.hz, hole.hx, hole.r), false)] : []}, y0, y1, c);
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

// ---------------- body: plinth, stainless shell, lead liner and floor, hardware ----------------
{
  roundSlab(P('plinth', 'body', 'plinth'), BW + 0.3, BD + 0.3, R_OUT + 0.2, 0, PLINTH, 0.15);
  // shell sinks 0.05 into the plinth; the liner keeps a 0.02 gap to the shell so no two faces share a plane
  roundSlab(P('body_shell', 'body', 'stainless'), BW, BD, R_OUT, PLINTH - 0.05, RIM, 0.08,
    {hx: BW - SHELL, hz: BD - SHELL, r: R_OUT - SHELL});
  const lead = P('body_lead', 'body', 'lead');
  roundSlab(lead, BW - SHELL - 0.02, BD - SHELL - 0.02, R_OUT - SHELL - 0.02, PLINTH, RIM, 0.08, {hx: IW, hz: ID, r: 0.2});
  slab(lead, 'y', [-IW - 0.1, PLINTH, -ID - 0.1], [IW + 0.1, FLOOR_TOP, ID + 0.1], 0);   // floor, sides buried in the liner
  // rear piano hinge: body leaf and the even knuckles (the odd ones ride on the lid)
  slab(P('hinge_leaf_body', 'body', 'hardware'), 'z', [-5.6, 8.9, BD - 0.03], [5.6, HINGE[1], BD + 0.25], 0);
  const knB = P('hinge_knuckles_body', 'body', 'hardware'), knL = P('hinge_knuckles_lid', 'lid', 'hardware');
  for (let i = 0; i < 7; i++) { const x0 = -5.6 + i * 1.6 + 0.03, x1 = x0 + 1.54; cyl(i % 2 ? knL : knB, 'x', HINGE[2], HINGE[1], 0.45, x0, x1, 8); }
  // draw latch base plates
  const plates = P('latch_plates', 'body', 'hardware');
  for (const x of LATCH_X) slab(plates, 'z', [x - 0.8, 7.4, -BD - 0.25], [x + 0.8, 9.2, -BD + 0.05], 0.05);
  // drop handles on both sides: pivot brackets and a hanging U bar (one extrusion, so the corners share no faces)
  const brackets = P('side_brackets', 'body', 'hardware'), handles = P('side_handles', 'body', 'hardware');
  const U = [[-2.0, 5.3], [2.0, 5.3], [2.0, 8.0], [1.6, 8.0], [1.6, 5.75], [-1.6, 5.75], [-1.6, 8.0], [-2.0, 8.0]];
  for (const s of [-1, 1]) {
    for (const z of [-1.8, 1.8]) slab(brackets, 'x', [s > 0 ? BW - 0.05 : -BW - 0.5, 7.6, z - 0.35], [s > 0 ? BW + 0.5 : -BW + 0.05, 8.4, z + 0.35], 0.05);
    extrude(handles, 'x', {outer: orient(U, true), holes: []}, s > 0 ? BW + 0.1 : -BW - 0.45, s > 0 ? BW + 0.45 : -BW - 0.1, 0);
  }
  // radiation warning placard (yellow, black trefoil painted from its own UV) and four rivets
  const plate = P('placard', 'body', 'placard');
  slab(plate, 'z', [PLACARD.cx - PLACARD.h, PLACARD.cy - PLACARD.h, -BD - 0.17], [PLACARD.cx + PLACARD.h, PLACARD.cy + PLACARD.h, -BD + 0.02], 0);
  const isFront = f => f.tag === 'cap' && plate.v[f.ids[0]][2] < -BD - 0.1, fronts = plate.f.filter(isFront);   // the cap's triangles
  plate.f = plate.f.filter(f => !isFront(f));
  const face = P('placard_face', 'body', 'placard'), moved = new Map();
  if (!fronts.length) throw new Error('placard front face missing');
  for (const f of fronts) face.face(f.ids.map(i => moved.get(i) ?? moved.set(i, face.vtx(plate.v[i])).get(i)), [0, 0, -1], 'cap');
  const rivets = P('placard_rivets', 'body', 'hardware');
  for (const dx of [-1.65, 1.65]) for (const dy of [-1.65, 1.65]) cyl(rivets, 'z', PLACARD.cx + dx, PLACARD.cy + dy, 0.17, -BD - 0.27, -BD - 0.14, 8);
}
// ---------------- lid: stainless cap, lead plug, rubber gasket, hinge leaf, latch keepers, fold-flat handle ----------------
{
  roundSlab(P('lid_shell', 'lid', 'stainless'), BW + 0.05, BD + 0.05, R_OUT + 0.05, LID.y0, LID.y1, 0.2);
  // lead plug seats into the opening (0.15 clearance); its top is buried 0.1 into the lid
  slab(P('lid_plug', 'lid', 'lead'), 'y', [-IW + 0.15, RIM - 1.0, -ID + 0.15], [IW - 0.15, LID.y0 + 0.1, ID - 0.15], 0.1);
  roundSlab(P('lid_gasket', 'lid', 'rubber'), BW - 0.5, BD - 0.5, R_OUT - 0.5, RIM + 0.02, LID.y0 + 0.05, 0, {hx: BW - 1.1, hz: BD - 1.1, r: 0.2});
  slab(P('hinge_leaf_lid', 'lid', 'hardware'), 'z', [-5.6, HINGE[1], BD + 0.02], [5.6, LID.y1 - 0.4, BD + 0.3], 0);
  const keepers = P('latch_keepers', 'lid', 'hardware');
  for (const x of LATCH_X) slab(keepers, 'z', [x - 0.65, LID.y0 + 0.5, -BD - 0.35], [x + 0.65, LID.y0 + 1.3, -BD - 0.02], 0.05);
  // fold-flat lid handle: U bar lying on the lid (opening toward the back) and two pivot blocks
  const handle = P('lid_handle', 'lid', 'hardware');
  extrude(handle, 'y', {outer: orient([[-1.4, -2.8], [0.2, -2.8], [0.2, -2.3], [-0.9, -2.3], [-0.9, 2.3], [0.2, 2.3], [0.2, 2.8], [-1.4, 2.8]], true), holes: []},
    LID.y1 - 0.02, LID.y1 + 0.4, 0);
  for (const x of [-2.55, 2.55]) slab(P('lid_handle_pivot_' + (x > 0 ? 'l' : 'r'), 'lid', 'hardware'), 'y', [x - 0.4, LID.y1 - 0.06, 0], [x + 0.4, LID.y1 + 0.65, 0.7], 0.06);
}
// ---------------- draw latch levers (one bone each, pivot at the lower end) ----------------
for (const [i, x] of LATCH_X.entries()) {
  const lever = P(`latch_${i ? 'right' : 'left'}`, `latch_${i ? 'right' : 'left'}`, 'hardware');
  slab(lever, 'z', [x - 0.5, 8.0, -BD - 0.55], [x + 0.5, LID.y0 + 1.3, -BD - 0.3], 0.05);
  slab(lever, 'z', [x - 0.5, LID.y0 + 1.32, -BD - 0.55], [x + 0.5, LID.y0 + 1.7, -BD - 0.05], 0);     // hook over the keeper
  cyl(lever, 'x', LATCH_PIVOT.z, LATCH_PIVOT.y, 0.3, x - 0.65, x + 0.65, 8);
}

// ---------------- Base Color zoning (PBR maps are per material) ----------------
const scale3 = (c, k) => c.map(v => v * k);
const smooth = (e0, e1, x) => { const t = Math.max(0, Math.min(1, (x - e0) / (e1 - e0))); return t * t * (3 - 2 * t); };
export const TONE = {lead: [104, 107, 113], leadCut: [122, 125, 131], leadInside: [86, 89, 95], stainless: [152, 155, 159]};
function leadColor(pos, n, part) {
  const [x, y, z] = pos;
  if (part.name === 'body_lead' && Math.abs(y - RIM) < 0.01 && n[1] > 0.5) return TONE.leadCut;            // sawn rim
  if (part.name === 'lid_plug') return n[1] < -0.5 ? scale3(TONE.leadCut, 0.95) : TONE.lead;               // plug face / sides
  return scale3(TONE.leadInside, 0.9 + 0.1 * smooth(FLOOR_TOP, RIM, y));                                    // liner walls, floor
}
// ISO 361 trefoil (centre disc r, blades 1.5 r .. 5 r, 60 degrees wide, one pointing down) on the placard's front face,
// edges anti-aliased by signed distance; thin black border
const YELLOW = [212, 168, 44], BLACK = [26, 26, 28], TR = 0.34, AA = 0.06;
function placardColor(pos, n) {
  if (n[2] > -0.5) return YELLOW;
  const u = pos[0] - PLACARD.cx, v = pos[1] - PLACARD.cy, r = Math.hypot(u, v), a = Math.atan2(v, u);
  const da = Math.min(...[30, 150, 270].map(c => { let d = Math.abs(a - c * Math.PI / 180) % (2 * Math.PI); return Math.min(d, 2 * Math.PI - d); }));
  const blade = Math.min(r - 1.5 * TR, 5 * TR - r, (Math.PI / 6 - da) * r), disc = TR - r;
  const border = 0.16 - (PLACARD.h - Math.max(Math.abs(u), Math.abs(v)));
  const k = Math.max(0, Math.min(1, 0.5 + Math.max(blade, disc, border) / AA));
  return YELLOW.map((c, i) => c + (BLACK[i] - c) * k);
}
export const MATS = {   // Base Color, bevel highlight, smoothness open / edge, F0 (LabPBR: 230+ preset metals, 255 = albedo metal)
  stainless: {c: TONE.stainless, hl: 14, sm: 100, se: 132, f0: 255},  // brushed stainless: metal, kept rough enough not to glare under shaders
  plinth:    {c: [112, 115, 119], hl: 10, sm: 88, se: 110, f0: 255},
  hardware:  {c: [140, 143, 147], hl: 16, sm: 120, se: 150, f0: 255},
  lead:      {c: leadColor, hl: 8, sm: 58, se: 86, f0: 235},          // LabPBR lead, rough
  rubber:    {c: [30, 30, 32], hl: 2, sm: 40, se: 46, f0: 20},          // matte rubber gasket
  placard:   {c: placardColor, hl: 4, sm: 110, se: 110, f0: 24},      // painted sign plate: dielectric
};
const ATLAS = 512, PAD = 2;
const FACE = PARTS.find(p => p.name === 'placard_face'), REST = PARTS.filter(p => p !== FACE);
const UA = unwrap(REST, {atlas: 464, pad: PAD, startS: 24, stepS: 0.25});
const FACE_ISLAND = (() => {   // placard front: (x, y) -> texels, 44 texels across the 4.2 px plate (about 10.5 texels / px)
  const px = 466, py = 2, W = 44, tex = new Map(FACE.v.map((q, i) => [i, [px + 0.5 + (PLACARD.cx + PLACARD.h - q[0]) / (2 * PLACARD.h) * (W - 1), py + 0.5 + (PLACARD.cy + PLACARD.h - q[1]) / (2 * PLACARD.h) * (W - 1)]]));
  return {part: FACE, faces: FACE.f.map(f => ({f, n: [0, 0, -1]})), px, py, W, H: W, tex};
})();
const UV = {islands: [...UA.islands, FACE_ISLAND], S: UA.S, uvOf: (is, id) => is.tex ? is.tex.get(id) : UA.uvOf(is, id),
  faceUV: new Map([...UA.faceUV, [FACE, new Map(FACE.f.map(f => [f, f.ids.map(id => FACE_ISLAND.tex.get(id))]))]])};
for (const p of PARTS) assert(p.f.every(f => UV.faceUV.get(p)?.has(f)), 'unmapped face in ' + p.name);
const DUMMY = 'data:image/png;base64,' + png(Buffer.from([0, 0, 0, 255]), 1, 1).toString('base64');
const painted = paint({PARTS, islands: UV.islands, S: UV.S, uvOf: UV.uvOf, atlas: ATLAS, pad: PAD, MATS, ZONED: new Set(), groupInfo: new Map(),
  sourceGroups: () => [], refTexture: {source: DUMMY}, refUvWidth: 1, background: {c: [...TONE.stainless, 255], s: [100, 255, 0, 255], n: [128, 128, 255, 255]}});

// ---------------- source (Free Model) ----------------
const uuid = s => { const h = createHash('sha256').update('afl-lead-chest-v3:' + s).digest('hex'); return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-8${h.slice(17, 20)}-${h.slice(20, 32)}`; };
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

// Mesh Shape profile (docs/rendering/mesh_shape_runtime_v1.md): closed = the whole box (+ lid handle for selection), the
// whole box is the 'lid' region; open = body + the standing lid, the body becomes 'interior', the standing lid closes it again
const CLOSED = aabbOf(PARTS.flatMap(p => p.v)).map(r3);
const BODY = aabbOf(PARTS.filter(p => p.bone === 'body').flatMap(p => p.v)).map(r3);
const LID_OPEN = aabbOf(PARTS.filter(p => p.bone === 'lid').flatMap(p => posed(p, 1))).map(r3);
const BOX = [CLOSED[0], 0, CLOSED[2], CLOSED[3], LID.y1, CLOSED[5]].map(r3);
export const SHAPES = {format_version: 1, units: 'px', cells_y: 1, states: {
  closed: {physical: [BOX], selection: [BOX, [-2.95, LID.y1, -1.4, 2.95, CLOSED[4], 0.7].map(r3)],
    interaction: {lid: {boxes: [[CLOSED[0], 0, CLOSED[2] - 0.1, CLOSED[3], CLOSED[4] + 0.1, CLOSED[5]].map(r3)], anchor: 'latch'}},
    anchors: {latch: [0, LID.y0 + 0.9, -BD - 0.4].map(r3)}},
  open: {physical: [[BODY[0], 0, BODY[2], BODY[3], RIM, BODY[5]].map(r3), LID_OPEN], selection: [[BODY[0], 0, BODY[2], BODY[3], RIM, BODY[5]].map(r3), LID_OPEN],
    interaction: {interior: {boxes: [[BODY[0], 0, BODY[2] - 0.1, BODY[3], RIM + 0.6, BD].map(r3)], anchor: 'opening'}, lid: {boxes: [LID_OPEN], anchor: 'lid_edge'}},
    anchors: {opening: [0, RIM + 0.3, -ID + 0.4].map(r3), lid_edge: aroundX([0, LID.y0 + 1.0, -BD - 0.05], HINGE, LID_DEGREES).map(r3)}},
}};

const bb = path.join(ROOT, 'src/main/blockbench'), assets = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight');
const shapeFile = path.join(ROOT, 'src/main/resources/data/apocalypse_firstlight/mesh_shapes/lead_chest.json');
const outputs = [[path.join(bb, 'lead_chest.bbmodel'), JSON.stringify(source)], [path.join(assets, 'geo/lead_chest.geo.json'), JSON.stringify(geo, null, 2) + '\n'],
  [path.join(assets, 'meshes/lead_chest.aflmesh.json'), meshText], [path.join(assets, 'block_mesh_profiles/lead_chest.json'), JSON.stringify(profile, null, 2) + '\n'],
  [shapeFile, JSON.stringify(SHAPES, null, 2) + '\n'],
  ...['', '_s', '_n'].flatMap((k, i) => [[path.join(bb, `textures/lead_chest${k}.png`), painted.PNG[i]], [path.join(assets, `textures/block/lead_chest${k}.png`), painted.PNG[i]]])];

const faces = sidecar.parts.flatMap(p => p.faces), zf = zFightLevels(PARTS, new Map());
export const stats = {triangles: faces.reduce((s, q) => s + q.length - 2, 0), parts: sidecar.parts.length, texelsPerPx: UV.S, islands: UV.islands.length,
  coplanarOverlaps: zf.unresolved.length, bounds, closed: CLOSED, lidOpen: LID_OPEN,
  byBone: Object.fromEntries(RIG.map(([b]) => [b, PARTS.filter(p => p.bone === b).reduce((s, p) => s + p.f.reduce((t, f) => t + f.ids.length - 2, 0), 0)]))};
const isMain = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  console.log(JSON.stringify(stats));
  if (zf.unresolved.length) console.log('COPLANAR', JSON.stringify(zf.unresolved.slice(0, 12)));
  const pi = process.argv.indexOf('--preview');
  if (pi > 0) {
    const dir = process.argv[pi + 1]; fs.mkdirSync(dir, {recursive: true});
    fs.writeFileSync(path.join(dir, 'lead_chest.geo.json'), JSON.stringify(geo, null, 2) + '\n');
    fs.writeFileSync(path.join(dir, 'lead_chest.aflmesh.json'), meshText);
    ['', '_s', '_n'].forEach((k, i) => fs.writeFileSync(path.join(dir, `lead_chest${k}.png`), painted.PNG[i]));
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
