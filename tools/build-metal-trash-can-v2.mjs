// Metal Trash Can V2: round city trash can with a hinged lid, 1 block, as Pure Mesh + 512 LabPBR atlas, rendered by the
// AFL Animated Block Mesh Runtime (bones 'body', 'lid' (channel 'open'), goods 'goods_bag_0..2') and in inventories by
// AflStaticMeshItemRenderer (lid shut, no goods). Replaced the V1 cube model (city_trash_can.bbmodel, 2026-10-02).
// A searchable 9-slot container (Progressive Container Search); with the lid open, black trash bags stack up inside as
// the contents grow (container goods, docs/gameplay/container_goods_v1.md): plain closed bags, nothing modelled in them.
//   node tools/build-metal-trash-can-v2.mjs                -> writes source, runtime geo / sidecar / profile / shapes / maps / item model
//   node tools/build-metal-trash-can-v2.mjs --check        -> verifies every output is up to date
//   node tools/build-metal-trash-can-v2.mjs --preview DIR  -> writes only geo / sidecar / maps into DIR (offline review)
// Frame (px): the block's bottom centre at the origin, front toward -Z, +X the viewer's LEFT. The whole can stays inside
// the block (top 15.95); the lid hinges at the back and opens 80 degrees (more would push it through the wall behind).
// Charcoal powder-coated steel (coated LabPBR, F0 20): tapered body with three pressed ribs, a rolled rim, two side
// handles, a domed lid with a bar handle. No print, dirt or rust.
import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {convert, serializeCompact} from './export-afl-mesh.mjs';
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
// lathe around a vertical axis through (cx, cz): profile [y, r] (r 0 on the axis); rf(i, k) scales ring k's vertex i
function latheY(part, cx, cz, profile, seg, rf = () => 1) {
  const rings = profile.map(([y, r], k) => r < 1e-6 ? part.vtx([cx, y, cz]) : Array.from({length: seg}, (_, i) => {
    const a = 2 * Math.PI * (i + 0.5) / seg, s = r * rf(i, k); return part.vtx([cx + s * Math.cos(a), y, cz + s * Math.sin(a)]); }));
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

// ---------------- dimensions (source px) ----------------
export const SEG = 24;
export const WALL = {y0: 0.5, y1: 13.2, r0: 5.15, r1: 5.7};             // tapered wall between the foot and the rim
export const RIM_TOP = 13.8, FLOOR = 1.2;
export const RIBS = [3.2, 6.9, 10.6];
export const HINGE = [0, 13.85, 6.42];                                   // lid pivot, at the back
export const OPEN_DEGREES = 80;
export const SIDE_HANDLE_Y = 11.9;
const wallR = y => WALL.r0 + (y - WALL.y0) / (WALL.y1 - WALL.y0) * (WALL.r1 - WALL.r0);
const innerR = y => 4.9 + (y - FLOOR) / (RIM_TOP - FLOOR) * 0.58;

{
  // body: outer skin (foot, tapered wall with pressed ribs, rolled rim) and the dark inside (wall, floor), one ring apart
  const outer = [[0, 0], [0, 4.9], [WALL.y0, WALL.r0]];
  for (const y of RIBS) outer.push([y - 0.45, wallR(y - 0.45)], [y - 0.2, wallR(y) + 0.22], [y + 0.2, wallR(y) + 0.22], [y + 0.45, wallR(y + 0.45)]);
  outer.push([WALL.y1, WALL.r1], [13.35, 5.92], [13.65, 5.95], [RIM_TOP, 5.75], [RIM_TOP, 5.48]);
  latheY(P('can', 'body', 'coat'), 0, 0, outer, SEG);
  latheY(P('can_inside', 'body', 'inside'), 0, 0, [[RIM_TOP, 5.48], [FLOOR, innerR(FLOOR)], [FLOOR, 0]], SEG);
  // side handles (left and right): two brackets and a bar each
  const fit = P('fittings', 'body', 'fitting'), y = SIDE_HANDLE_Y;
  for (const s of [-1, 1]) {
    for (const z of [-1.3, 1.3]) slab(fit, 'x', [s > 0 ? 5.35 : -6.3, y - 0.3, z - 0.3], [s > 0 ? 6.3 : -5.35, y + 0.3, z + 0.3], 0.05);
    slab(fit, 'z', [s > 0 ? 6.2 : -6.5, y - 0.2, -1.75], [s > 0 ? 6.5 : -6.2, y + 0.2, 1.75], 0.06);
  }
  // hinge: knuckle on the body and its pin
  slab(fit, 'z', [-1.3, 12.7, 5.4], [1.3, 13.5, 6.3], 0.08);
  slab(fit, 'x', [-1.4, 13.6, 6.28], [1.4, 13.9, 6.55], 0);
}
{
  // lid: domed top with a skirt over the rim, dark underside, bar handle, hinge tab; all in bone 'lid' (pivot HINGE)
  latheY(P('lid', 'lid', 'coat'), 0, 0, [[13.45, 6.03], [13.45, 6.25], [14.1, 6.25], [14.3, 6.02], [14.6, 5.0], [14.88, 3.0], [14.98, 0]], SEG);
  latheY(P('lid_underside', 'lid', 'inside'), 0, 0, [[13.85, 0], [13.85, 6.0], [13.45, 6.03]], SEG);
  const fit = P('lid_fittings', 'lid', 'fitting');
  for (const x of [-1.5, 1.5]) slab(fit, 'y', [x - 0.25, 14.85, -0.25], [x + 0.25, 15.65, 0.25], 0);
  slab(fit, 'x', [-1.9, 15.55, -0.3], [1.9, 15.95, 0.3], 0.08);
  slab(fit, 'z', [-1.0, 13.55, 6.15], [1.0, 14.0, 6.62], 0);
}
// goods: three closed black bags stacked from the floor up (bones goods_bag_0..2, shown by the block entity), slightly
// lumpy, a twisted neck and a tied knot with two ears; inside the inner wall, the top knot below the shut lid
// one big sack so that even a single bag shows from a standing view (it fills the can to about three quarters), then two
// smaller ones side by side on it, the last reaching the rim
export const BAGS = [{x: 0.2, z: 0.1, y0: FLOOR, h: 9.4, r: 4.3, seed: 1.3}, {x: 1.3, z: -0.6, y0: 8.6, h: 3.9, r: 3.3, seed: 3.1},
  {x: -1.4, z: 0.7, y0: 9.6, h: 3.4, r: 3.2, seed: 5.7}];
export const GOODS_BONES = BAGS.map((_, i) => 'goods_bag_' + i);
BAGS.forEach((b, i) => {
  const part = P(GOODS_BONES[i], GOODS_BONES[i], 'bag'), y = t => b.y0 + t * b.h;
  const body = [[y(0), 0], [y(0.04), 0.55 * b.r], [y(0.15), 0.88 * b.r], [y(0.35), b.r], [y(0.6), 0.94 * b.r], [y(0.8), 0.66 * b.r], [y(0.9), 0.3 * b.r], [y(0.96), 0.5], [y(1), 0.42], [y(1), 0]];
  const lump = (j, k) => k >= 7 ? 1 : 1 + 0.035 * Math.sin(2 * 2 * Math.PI * j / 8 + b.seed + k) + 0.015 * Math.sin(5 * 2 * Math.PI * j / 8 + 2 * b.seed);
  latheY(part, b.x, b.z, body, 8, lump);
  latheY(part, b.x, b.z, [[y(1) - 0.05, 0], [y(1) - 0.05, 0.62], [y(1) + 0.4, 0.55], [y(1) + 0.6, 0]], 6);
  slab(part, 'x', [b.x + 0.2, y(1) + 0.35, b.z - 0.16], [b.x + 1.05, y(1) + 0.62, b.z + 0.16], 0);
  slab(part, 'z', [b.x - 0.16, y(1) + 0.28, b.z - 0.95], [b.x + 0.16, y(1) + 0.55, b.z - 0.2], 0);
  for (const q of part.v) if (q[1] < y(1)) { const r = Math.hypot(q[0], q[2]); assert(r < innerR(q[1]) - 0.05, `bag ${i} through the wall at y ${q[1].toFixed(2)}`); }
  assert(y(1) + 0.62 < HINGE[1] - 0.2, `bag ${i} knot under the shut lid`);
});

export const MATS = {   // Base Color, bevel highlight, smoothness open / edge, F0 (coated / dielectric)
  coat:    {c: [44, 47, 52], hl: 8, sm: 100, se: 120, f0: 20},          // charcoal powder coat (the vending machine's)
  inside:  {c: [80, 82, 86], hl: 2, sm: 96, se: 104, f0: 20},          // bare grey steel inside (coated values): the black bags read against it
  fitting: {c: [60, 62, 66], hl: 10, sm: 112, se: 132, f0: 20},
  bag:     {c: [24, 24, 27], hl: 0, sm: 160, se: 160, f0: 20},          // black bin-bag film, a little gloss
};

const ATLAS = 512, PAD = 2;
const UV = unwrap(PARTS, {atlas: ATLAS, pad: PAD, startS: 16, stepS: 0.25});
for (const p of PARTS) assert(p.f.every(f => UV.faceUV.get(p)?.has(f)), 'unmapped face in ' + p.name);
const DUMMY = 'data:image/png;base64,' + png(Buffer.from([0, 0, 0, 255]), 1, 1).toString('base64');
const painted = paint({PARTS, islands: UV.islands, S: UV.S, uvOf: UV.uvOf, atlas: ATLAS, pad: PAD, MATS, ZONED: new Set(), groupInfo: new Map(),
  sourceGroups: () => [], refTexture: {source: DUMMY}, refUvWidth: 1, background: {c: [...MATS.coat.c, 255], s: [100, 20, 0, 255], n: [128, 128, 255, 255]}});
const PNGS = painted.PNG.map(b => { const r = readPng(b); assert(r.bpp === 4 && r.w === ATLAS, 'atlas format'); return png(r.px, ATLAS, ATLAS); });

// ---------------- source (Free Model) ----------------
const ID = 'metal_trash_can';
const uuid = s => { const h = createHash('sha256').update('afl-metal-trash-can-v2:' + s).digest('hex'); return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-8${h.slice(17, 20)}-${h.slice(20, 32)}`; };
const r12 = v => +v.toFixed(12) || 0, r6 = v => +v.toFixed(6) || 0, r3 = v => +v.toFixed(3) || 0;
const RIG = [['body', [0, 0, 0]], ['lid', HINGE], ...GOODS_BONES.map(b => [b, [0, 0, 0]])];
const NEVER_RENDER = new Set(GOODS_BONES);
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
      source: 'data:image/png;base64,' + PNGS[0].toString('base64')}],
    animations: []};
})();

// ---------------- runtime ----------------
const geo = {format_version: '1.12.0', 'minecraft:geometry': [{description: {identifier: 'geometry.' + ID, texture_width: ATLAS,
  texture_height: ATLAS, visible_bounds_width: 2, visible_bounds_height: 2.5, visible_bounds_offset: [0, 0.75, 0]},
  bones: RIG.map(([name, origin]) => NEVER_RENDER.has(name) ? {name, pivot: [-origin[0] || 0, origin[1], origin[2]], neverRender: true}
    : {name, pivot: [-origin[0] || 0, origin[1], origin[2]]})}]};
const sidecar = convert(source, geo, {}, ID + '.bbmodel', 2);
const meshText = serializeCompact(sidecar);

// the lid swung about the hinge (+X axis, right-handed: a positive angle lifts the front)
const swing = (q, deg) => { const a = deg * Math.PI / 180, y = q[1] - HINGE[1], z = q[2] - HINGE[2];
  return [q[0], HINGE[1] + y * Math.cos(a) - z * Math.sin(a), HINGE[2] + y * Math.sin(a) + z * Math.cos(a)]; };
const lidPoints = PARTS.filter(p => p.bone === 'lid').flatMap(p => p.v);
const aabbOf = pts => [0, 1, 2].map(k => Math.min(...pts.map(q => q[k]))).concat([0, 1, 2].map(k => Math.max(...pts.map(q => q[k])))).map(r3);
for (const q of lidPoints) assert(swing(q, OPEN_DEGREES)[2] < 7.9, 'the open lid stays inside the block');
// render bounds (NORTH, block-local corners): the can plus every lid vertex swept through the whole opening
const bounds = (() => {
  const pts = [...PARTS.filter(p => p.bone !== 'lid').flatMap(p => p.v)];
  for (let s = 0; s <= 16; s++) for (const q of lidPoints) pts.push(swing(q, OPEN_DEGREES * s / 16));
  const b = aabbOf(pts), m = 0.25;
  return [(b[0] - m + 8) / 16, (b[1] - m) / 16, (b[2] - m + 8) / 16, (b[3] + m + 8) / 16, (b[4] + m) / 16, (b[5] + m + 8) / 16].map(r6);
})();
const profile = {format_version: 1, geometry: `apocalypse_firstlight:geo/${ID}.geo.json`,
  texture: `apocalypse_firstlight:textures/block/${ID}.png`, origin: [0, 0, 0], scale: [1, 1, 1], facing: 'horizontal', bounds,
  parts: {body: {pivot: [0, 0, 0]}, lid: {pivot: HINGE.map(v => r6(v / 16))}, ...Object.fromEntries(GOODS_BONES.map(b => [b, {pivot: [0, 0, 0]}]))},
  animations: {open: {duration_ticks: 8, easing: 'ease_in_out', transforms: {lid: {rotation: [OPEN_DEGREES, 0, 0]}}}}};

// Mesh Shape profile (docs/rendering/mesh_shape_runtime_v1.md), one cell: shut, the whole can opens the lid; open, the
// mouth (aimed at from above) searches and the rest of the can shuts the lid. The open lid's box rises above the cell:
// physical / selection are cut to the cell by the runtime, the interaction regions are not.
const LID_OPEN = aabbOf(lidPoints.map(q => swing(q, OPEN_DEGREES)));
const BODY = [-6.0, 0, -6.0, 6.0, RIM_TOP, 6.0], HANDLES = [-6.5, SIDE_HANDLE_Y - 0.3, -1.75, 6.5, SIDE_HANDLE_Y + 0.3, 1.75];
const HINGE_BOX = [-1.4, 12.7, 5.4, 1.4, 13.9, 6.55];
const LID_SHUT = [-6.25, 13.45, -6.25, 6.25, 14.98, 6.25], LID_HANDLE = [-1.9, 14.85, -0.3, 1.9, 15.95, 0.3];
export const SHAPES = {format_version: 1, units: 'px', cells_y: 1, states: {
  closed: {physical: [BODY, LID_SHUT, LID_HANDLE], selection: [BODY, HANDLES, HINGE_BOX, LID_SHUT, LID_HANDLE],
    interaction: {lid: {boxes: [[-6.55, 0, -6.55, 6.55, 16.0, 6.7]], anchor: 'lid'}},
    anchors: {lid: [0, 14.3, -6.3]}},
  open: {physical: [BODY, LID_OPEN], selection: [BODY, HANDLES, HINGE_BOX, LID_OPEN],
    interaction: {mouth: {boxes: [[-5.4, 8.0, -5.4, 5.4, RIM_TOP + 0.8, 5.4]], anchor: 'mouth'},
      lid: {boxes: [[-6.55, 0, -6.55, 6.55, RIM_TOP, 6.55], LID_OPEN], anchor: 'lid'}},
    anchors: {mouth: [0, RIM_TOP + 0.1, -4.0], lid: [0, 19.0, 6.0]}},
}};

// item model: builtin/entity; every view centred on the can (lid shut, no goods)
const itemDisplay = (() => {
  const rot = ([ax, ay]) => q => { const a = ay * Math.PI / 180, b = ax * Math.PI / 180, x = q[0] * Math.cos(a) + q[2] * Math.sin(a), z = -q[0] * Math.sin(a) + q[2] * Math.cos(a);
    return [x, q[1] * Math.cos(b) - z * Math.sin(b), q[1] * Math.sin(b) + z * Math.cos(b)]; };
  const pts = PARTS.filter(p => !NEVER_RENDER.has(p.bone)).flatMap(p => p.v).map(q => [q[0], q[1] - 8, q[2]]), S3 = v => [v, v, v];
  const view = (r, fit, extra = [0, 0, 0]) => { const R = pts.map(rot(r)), xs = R.map(q => q[0]), ys = R.map(q => q[1]), zs = R.map(q => q[2]);
    const s = typeof fit === 'number' && fit > 1 ? r3(fit / Math.max(Math.max(...xs) - Math.min(...xs), Math.max(...ys) - Math.min(...ys))) : fit;
    const c = [(Math.max(...xs) + Math.min(...xs)) / 2, (Math.max(...ys) + Math.min(...ys)) / 2, (Math.max(...zs) + Math.min(...zs)) / 2];
    return {rotation: [r[0], r[1], 0], translation: [r3(-s * c[0] + extra[0]), r3(-s * c[1] + extra[1]), r3(-s * c[2] + extra[2])], scale: S3(s)}; };
  return {gui: view([30, 225], 13.5), ground: view([0, 0], 0.25, [0, 3, 0]), fixed: view([0, 0], 0.5),
    thirdperson_righthand: view([75, 225], 0.375, [0, 2.5, 0]), firstperson_righthand: view([0, 225], 0.4, [0, 1, 0])};
})();
const itemModel = {parent: 'builtin/entity', gui_light: 'side', textures: {particle: `apocalypse_firstlight:block/${ID}`}, display: itemDisplay};
const blockModel = {textures: {particle: `apocalypse_firstlight:block/${ID}`}};

const bb = path.join(ROOT, 'src/main/blockbench'), assets = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight');
const outputs = [[path.join(bb, ID + '.bbmodel'), JSON.stringify(source)],
  [path.join(assets, `geo/${ID}.geo.json`), JSON.stringify(geo, null, 2) + '\n'], [path.join(assets, `meshes/${ID}.aflmesh.json`), meshText],
  [path.join(assets, `block_mesh_profiles/${ID}.json`), JSON.stringify(profile, null, 2) + '\n'],
  [path.join(ROOT, `src/main/resources/data/apocalypse_firstlight/mesh_shapes/${ID}.json`), JSON.stringify(SHAPES, (k, v) => typeof v === 'number' ? r3(v) : v, 2) + '\n'],
  [path.join(assets, `models/item/${ID}.json`), JSON.stringify(itemModel, null, 2) + '\n'], [path.join(assets, `models/block/${ID}.json`), JSON.stringify(blockModel, null, 2) + '\n'],
  ...['', '_s', '_n'].flatMap((k, i) => [[path.join(bb, `textures/${ID}${k}.png`), PNGS[i]], [path.join(assets, `textures/block/${ID}${k}.png`), PNGS[i]]])];

// coplanar check: the can alone, then each bag with it (bags never overlap the can's faces; the stack shows bottom first)
const isGoods = p => p.bone.startsWith('goods_');
const zfs = [zFightLevels(PARTS, new Map(), {skip: isGoods}), ...GOODS_BONES.map(b => zFightLevels(PARTS, new Map(), {skip: p => isGoods(p) && p.bone !== b}))];
const zf = {unresolved: zfs.flatMap(z => z.unresolved)};
const trisOf = p => p.f.reduce((t, f) => t + f.ids.length - 2, 0);
export const stats = {triangles: sidecar.parts.flatMap(p => p.faces).reduce((s, q) => s + q.length - 2, 0), parts: sidecar.parts.length, texelsPerPx: UV.S,
  coplanarOverlaps: zf.unresolved.length, bounds, lidOpen: LID_OPEN, gui: itemDisplay.gui,
  byBone: Object.fromEntries(RIG.map(([b]) => [b, PARTS.filter(p => p.bone === b).reduce((s, p) => s + trisOf(p), 0)]))};
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
