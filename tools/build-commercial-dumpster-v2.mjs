// Commercial Dumpster V2: front-load steel dumpster with two hinged plastic lids, 2 x 1 cells, as Pure Mesh + 512 LabPBR
// atlases, rendered by the AFL Animated Block Mesh Runtime from the master cell (bones 'body', 'lid_left', 'lid_right',
// channels 'left_open' / 'right_open'; goods 'goods_<side>_<k>') and in inventories by AflStaticMeshItemRenderer (lids
// shut, no goods). Replaced the V1 cube model (2026-10-02). One mesh, one atlas per body colour (COLOURS: same layout,
// only the body paint differs), each with its own block (CommercialDumpsterBlock) and mesh profile.
// An 18-slot searchable container (Progressive Container Search); with a lid open, black bags and a flattened carton fill
// that half as the contents grow (container goods): plain shapes, nothing modelled inside the bags.
//   node tools/build-commercial-dumpster-v2.mjs                -> writes source, runtime geo / sidecar / profiles / maps / item models
//   node tools/build-commercial-dumpster-v2.mjs --check        -> verifies every output is up to date
//   node tools/build-commercial-dumpster-v2.mjs --preview DIR  -> writes only geo / sidecar / maps into DIR (offline review)
// Frame (px): the MASTER cell's bottom centre at the origin, front toward -Z, +X the viewer's LEFT; the master is the
// right half (x -8..8), the secondary the left half (x 8..24). The whole body stays inside the two cells (the side fork
// pockets end flush with them); the lids hinge at their top back edge and open 95 degrees (more would put them through
// the wall behind). Coated steel body, black HDPE lids, no print, decals, dirt or rust.
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
// a side profile [z, y][] extruded along x between x0 and x1
const profileX = (part, pts, x0, x1, c = 0) => extrude(part, 'x', {outer: orient(pts, true), holes: []}, x0, x1, c);
// lathe around a vertical axis through (cx, cz): profile [y, r] (r 0 on the axis); rf(i, k) scales ring k's vertex i
function latheY(part, cx, cz, profile, seg, rf = () => 1) {
  const rings = profile.map(([y, r], k) => r < 1e-6 ? part.vtx([cx, y, cz]) : Array.from({length: seg}, (_, i) => {
    const a = 2 * Math.PI * (i + 0.5) / seg, s = r * rf(i, k); return part.vtx([cx + s * Math.cos(a), y, cz + s * Math.sin(a)]); }));
  for (let k = 0; k + 1 < profile.length; k++) {
    const [y0, r0] = profile[k], [y1, r1] = profile[k + 1], dr = r1 - r0, dy = y1 - y0;
    for (let i = 0; i < seg; i++) {
      const j = (i + 1) % seg, a = 2 * Math.PI * (i + 1) / seg, n = [dy * Math.cos(a), -dr, dy * Math.sin(a)];
      const tag = Math.abs(dr) > 4 * Math.abs(dy) ? 'cap' : 'side';
      if (r0 < 1e-6) part.face([rings[k], rings[k + 1][i], rings[k + 1][j]], n, tag);
      else if (r1 < 1e-6) part.face([rings[k][i], rings[k][j], rings[k + 1]], n, tag);
      else part.face([rings[k][i], rings[k][j], rings[k + 1][j], rings[k + 1][i]], n, tag);
    }
  }
}

// ---------------- dimensions (source px) ----------------
export const BODY = {x0: -7.0, x1: 23.0, z0: -6.5, z1: 7.4, floor: 1.6, wall: 0.6};
export const RIM = {front: 17.2, back: 20.8};                            // rim height at the front and back walls
export const rim = z => RIM.front + (z - BODY.z0) / (BODY.z1 - BODY.z0) * (RIM.back - RIM.front);
export const LID = {thick: 0.9, rib: 0.45, z0: BODY.z0 - 0.72, z1: BODY.z1 - 0.1};
export const HALVES = {right: {x0: -6.9, x1: 7.85, c: 0.5}, left: {x0: 8.15, x1: 22.9, c: 15.5}};   // lids; right = master cell
export const OPEN_DEGREES = 95;
export const POCKET = {y0: 6.0, y1: 9.6, z0: -5.4, z1: 5.4};
const T = BODY.wall, IN = {x0: BODY.x0 + T, x1: BODY.x1 - T, z0: BODY.z0 + T, z1: BODY.z1 - T, y: BODY.floor + T};
const lidTop = z => rim(z) + 0.05 + LID.thick;
export const hingeOf = side => [HALVES[side].c, lidTop(LID.z1), LID.z1];   // the lid's top back edge

{
  const body = P('body', 'body', 'paint');
  // walls: front and back between the side walls; the side walls carry the slanted top
  slab(body, 'z', [IN.x0, BODY.floor, BODY.z0], [IN.x1, RIM.front, BODY.z0 + T], 0);
  slab(body, 'z', [IN.x0, BODY.floor, BODY.z1 - T], [IN.x1, RIM.back, BODY.z1], 0);
  for (const [x0, x1] of [[BODY.x0, IN.x0], [IN.x1, BODY.x1]])
    profileX(body, [[BODY.z0, BODY.floor], [BODY.z1, BODY.floor], [BODY.z1, RIM.back], [BODY.z0, RIM.front]], x0, x1, 0);
  slab(body, 'y', [IN.x0, BODY.floor, IN.z0], [IN.x1, IN.y, IN.z1], 0);
  // rolled rim around the opening (outside the walls), vertical stiffeners on the front
  const rimBar = P('rim', 'body', 'paint');
  slab(rimBar, 'z', [BODY.x0, RIM.front - 0.8, BODY.z0 - 0.4], [BODY.x1, RIM.front, BODY.z0], 0.06);
  slab(rimBar, 'z', [BODY.x0, RIM.back - 0.8, BODY.z1], [BODY.x1, RIM.back, BODY.z1 + 0.4], 0.06);
  for (const [x0, x1] of [[BODY.x0 - 0.4, BODY.x0], [BODY.x1, BODY.x1 + 0.4]])
    profileX(rimBar, [[BODY.z0, RIM.front - 0.8], [BODY.z1, RIM.back - 0.8], [BODY.z1, RIM.back], [BODY.z0, RIM.front]], x0, x1, 0);
  for (const c of [-1, 5, 11, 17]) slab(rimBar, 'z', [c - 0.5, BODY.floor + 0.6, BODY.z0 - 0.35], [c + 0.5, RIM.front - 1.1, BODY.z0], 0.08);
  // fork pockets on both sides (open sleeves front to back), skids under the floor
  const steel = P('fittings', 'body', 'steel');
  for (const [xo, xi] of [[BODY.x0 - 1.0, BODY.x0], [BODY.x1, BODY.x1 + 1.0]]) {
    const lo = Math.min(xo, xi), hi = Math.max(xo, xi), inLo = lo + (xo < xi ? 0.2 : 0.0), inHi = hi - (xo < xi ? 0.0 : 0.2);
    extrude(steel, 'z', {outer: orient(rect(lo, POCKET.y0, hi, POCKET.y1), true), holes: [orient(rect(lo + 0.2, POCKET.y0 + 0.25, hi - 0.2, POCKET.y1 - 0.25), false)]},
      POCKET.z0, POCKET.z1, 0);
    void inLo; void inHi;
  }
  for (const [z0, z1] of [[-6.2, -5.0], [-0.6, 0.6], [5.0, 6.2]]) slab(steel, 'x', [BODY.x0 + 0.3, 0, z0], [BODY.x1 - 0.3, BODY.floor, z1], 0.06);
  // hinge plates on the back rim, two per lid
  for (const side of Object.keys(HALVES)) for (const d of [-4.5, 4.5]) {
    const c = HALVES[side].c + d;
    slab(steel, 'z', [c - 0.7, RIM.back - 0.7, BODY.z1 + 0.4], [c + 0.7, RIM.back + 0.3, BODY.z1 + 0.5], 0);
  }
}
// lids: panel along the slant with a lip over the front rim, three ribs, a front grip on two standoffs
for (const [side, h] of Object.entries(HALVES)) {
  const bone = 'lid_' + side, lid = P(bone, bone, 'lid'), z0 = LID.z0, zl = BODY.z0 - 0.42;
  profileX(lid, [[z0, RIM.front - 1.0], [zl, RIM.front - 1.0], [zl, rim(zl) + 0.05], [LID.z1, rim(LID.z1) + 0.05], [LID.z1, lidTop(LID.z1)], [z0, lidTop(z0)]], h.x0, h.x1, 0.1);
  const span = h.x1 - h.x0;
  for (const f of [0.22, 0.5, 0.78]) {
    const x = h.x0 + f * span, za = z0 + 0.6, zb = LID.z1 - 0.6;
    profileX(lid, [[za, lidTop(za)], [zb, lidTop(zb)], [zb, lidTop(zb) + LID.rib], [za, lidTop(za) + LID.rib]], x - 0.3, x + 0.3, 0);
  }
  const g = P(bone + '_grip', bone, 'lid'), gy = RIM.front - 0.85;
  slab(g, 'x', [h.c - 2.2, gy, z0 - 0.55], [h.c + 2.2, gy + 0.45, z0 - 0.2], 0.06);
  for (const d of [-1.9, 1.9]) slab(g, 'z', [h.c + d - 0.25, gy, z0 - 0.2], [h.c + d + 0.25, gy + 0.45, z0], 0);
}
// goods: per half, two big bags on the floor, a flattened carton leaning on the back wall, a bag on top (bones
// goods_<side>_<k>, shown by the block entity); the bags are the Metal Trash Can's (lumpy sack, twisted neck, tied knot)
export const GOODS_SPOTS = [
  {k: 0, bag: {dx: -2.8, z: -0.3, y0: IN.y, h: 11.0, r: 3.6, seed: 1.3}},
  {k: 1, bag: {dx: 2.8, z: 0.8, y0: IN.y, h: 10.4, r: 3.5, seed: 3.1}},
  {k: 2, bag: {dx: 0.2, z: -0.6, y0: 9.6, h: 7.4, r: 3.9, seed: 5.7}},
  {k: 3, carton: {dx0: -5.0, dx1: 4.0}}];
export const GOODS_BONES = Object.keys(HALVES).flatMap(side => GOODS_SPOTS.map(s => `goods_${side}_${s.k}`));
function bag(part, b, cx) {
  const y = t => b.y0 + t * b.h;
  const body = [[y(0), 0], [y(0.04), 0.55 * b.r], [y(0.15), 0.88 * b.r], [y(0.35), b.r], [y(0.6), 0.94 * b.r], [y(0.8), 0.66 * b.r], [y(0.9), 0.3 * b.r], [y(0.96), 0.5], [y(1), 0.42], [y(1), 0]];
  const lump = (j, k) => k >= 7 ? 1 : 1 + 0.035 * Math.sin(2 * 2 * Math.PI * j / 8 + b.seed + k) + 0.015 * Math.sin(5 * 2 * Math.PI * j / 8 + 2 * b.seed);
  latheY(part, cx, b.z, body, 8, lump);
  latheY(part, cx, b.z, [[y(1) - 0.05, 0], [y(1) - 0.05, 0.62], [y(1) + 0.4, 0.55], [y(1) + 0.6, 0]], 6);
  slab(part, 'x', [cx + 0.2, y(1) + 0.35, b.z - 0.16], [cx + 1.05, y(1) + 0.62, b.z + 0.16], 0);
  slab(part, 'z', [cx - 0.16, y(1) + 0.28, b.z - 0.95], [cx + 0.16, y(1) + 0.55, b.z - 0.2], 0);
  for (const q of part.v) {
    assert(q[0] > IN.x0 + 0.05 && q[0] < IN.x1 - 0.05 && q[2] > IN.z0 + 0.05 && q[2] < IN.z1 - 0.05, `${part.name} through a wall`);
    assert(q[1] < rim(q[2]) - 0.3, `${part.name} under the shut lid`);
  }
}
for (const [side, h] of Object.entries(HALVES)) for (const s of GOODS_SPOTS) {
  const bone = `goods_${side}_${s.k}`;
  if (s.bag) bag(P(bone, bone, 'bag'), s.bag, h.c + s.bag.dx);
  else profileX(P(bone, bone, 'carton'), [[IN.z1 - 2.2, IN.y], [IN.z1 - 1.5, IN.y], [IN.z1 - 0.05, 15.0], [IN.z1 - 0.75, 15.0]], h.c + s.carton.dx0, h.c + s.carton.dx1, 0.05);
}

// body colours: one atlas each (the block, its mesh profile and its item pick theirs), the rest shared
export const COLOURS = {green: [50, 74, 58], blue: [44, 64, 92], brown: [84, 66, 50], gray: [86, 90, 94]};
const MATS_FOR = paint => ({   // Base Color, bevel highlight, smoothness open / edge, F0 (coated / dielectric)
  paint:  {c: paint, hl: 8, sm: 96, se: 116, f0: 20},                  // powder-coated steel, low saturation
  steel:  {c: [56, 58, 62], hl: 10, sm: 104, se: 124, f0: 20},          // fork pockets, skids, hinges (dark coated)
  lid:    {c: [30, 31, 34], hl: 6, sm: 112, se: 128, f0: 20},          // black HDPE
  bag:    {c: [24, 24, 27], hl: 0, sm: 160, se: 160, f0: 20},          // black bin-bag film (the trash can's)
  carton: {c: [138, 110, 78], hl: 0, sm: 70, se: 74, f0: 20},          // kraft card, flattened
});

const ATLAS = 512, PAD = 2;
const UV = unwrap(PARTS, {atlas: ATLAS, pad: PAD, startS: 16, stepS: 0.25});
for (const p of PARTS) assert(p.f.every(f => UV.faceUV.get(p)?.has(f)), 'unmapped face in ' + p.name);
const DUMMY = 'data:image/png;base64,' + png(Buffer.from([0, 0, 0, 255]), 1, 1).toString('base64');
const atlasFor = colour => {
  const MATS = MATS_FOR(COLOURS[colour]);
  const painted = paint({PARTS, islands: UV.islands, S: UV.S, uvOf: UV.uvOf, atlas: ATLAS, pad: PAD, MATS, ZONED: new Set(), groupInfo: new Map(),
    sourceGroups: () => [], refTexture: {source: DUMMY}, refUvWidth: 1, background: {c: [...MATS.paint.c, 255], s: [96, 20, 0, 255], n: [128, 128, 255, 255]}});
  return painted.PNG.map(b => { const r = readPng(b); assert(r.bpp === 4 && r.w === ATLAS, 'atlas format'); return png(r.px, ATLAS, ATLAS); });
};
const PNGS = Object.fromEntries(Object.keys(COLOURS).map(c => [c, atlasFor(c)]));

// ---------------- source (Free Model, green) ----------------
const ID = 'commercial_dumpster';
const uuid = s => { const h = createHash('sha256').update('afl-commercial-dumpster-v2:' + s).digest('hex'); return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-8${h.slice(17, 20)}-${h.slice(20, 32)}`; };
const r12 = v => +v.toFixed(12) || 0, r6 = v => +v.toFixed(6) || 0, r3 = v => +v.toFixed(3) || 0;
const RIG = [['body', [0, 0, 0]], ['lid_left', hingeOf('left')], ['lid_right', hingeOf('right')], ...GOODS_BONES.map(b => [b, [0, 0, 0]])];
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
  return {meta: {format_version: '5.0', model_format: 'free', box_uv: false}, name: ID, model_identifier: '', visible_box: [2, 2, 0],
    variable_placeholders: '', variable_placeholder_buttons: [], timeline_setups: [], unhandled_root_fields: {}, resolution: {width: ATLAS, height: ATLAS},
    elements, groups, outliner: RIG.map(([name]) => nodes.get(name)),
    textures: [{name: ID + '_green.png', relative_path: `textures/${ID}_green.png`, folder: '', namespace: '', id: '0', group: '', scope: 0,
      width: ATLAS, height: ATLAS, uv_width: ATLAS, uv_height: ATLAS, particle: false, use_as_default: false, layers_enabled: false, sync_to_project: '',
      file_format: 'png', render_mode: 'default', render_sides: 'auto', wrap_mode: 'limited', pbr_channel: 'color', fps: 7, frame_time: 1,
      frame_order_type: 'loop', frame_order: '', frame_interpolate: false, visible: true, internal: true, saved: true, uuid: uuid('texture'),
      source: 'data:image/png;base64,' + PNGS.green[0].toString('base64')}],
    animations: []};
})();

// ---------------- runtime ----------------
const geo = {format_version: '1.12.0', 'minecraft:geometry': [{description: {identifier: 'geometry.' + ID, texture_width: ATLAS,
  texture_height: ATLAS, visible_bounds_width: 3, visible_bounds_height: 3, visible_bounds_offset: [0.5, 1, 0]},
  bones: RIG.map(([name, origin]) => NEVER_RENDER.has(name) ? {name, pivot: [-origin[0] || 0, origin[1], origin[2]], neverRender: true}
    : {name, pivot: [-origin[0] || 0, origin[1], origin[2]]})}]};
const sidecar = convert(source, geo, {}, ID + '.bbmodel', 2);
const meshText = serializeCompact(sidecar);

// a lid swung about its hinge (+X axis, right-handed: a positive angle lifts the front)
const swing = (q, H, deg) => { const a = deg * Math.PI / 180, y = q[1] - H[1], z = q[2] - H[2];
  return [q[0], H[1] + y * Math.cos(a) - z * Math.sin(a), H[2] + y * Math.sin(a) + z * Math.cos(a)]; };
const lidPoints = side => PARTS.filter(p => p.bone === 'lid_' + side).flatMap(p => p.v);
for (const side of Object.keys(HALVES)) for (const q of lidPoints(side)) assert(swing(q, hingeOf(side), OPEN_DEGREES)[2] < 7.95, 'the open lids stay inside the cells');
const aabbOf = pts => [0, 1, 2].map(k => Math.min(...pts.map(q => q[k]))).concat([0, 1, 2].map(k => Math.max(...pts.map(q => q[k])))).map(r3);
// render bounds (NORTH, master block-local corners): the body plus every lid vertex swept through the opening
const bounds = (() => {
  const pts = PARTS.filter(p => !p.bone.startsWith('lid_')).flatMap(p => p.v);
  for (const side of Object.keys(HALVES)) for (let s = 0; s <= 16; s++) for (const q of lidPoints(side)) pts.push(swing(q, hingeOf(side), OPEN_DEGREES * s / 16));
  const b = aabbOf(pts), m = 0.25;
  return [(b[0] - m + 8) / 16, (b[1] - m) / 16, (b[2] - m + 8) / 16, (b[3] + m + 8) / 16, (b[4] + m) / 16, (b[5] + m + 8) / 16].map(r6);
})();
const profileFor = colour => ({format_version: 1, geometry: `apocalypse_firstlight:geo/${ID}.geo.json`,
  texture: `apocalypse_firstlight:textures/block/${ID}_${colour}.png`, origin: [0, 0, 0], scale: [1, 1, 1], facing: 'horizontal', bounds,
  parts: {body: {pivot: [0, 0, 0]}, ...Object.fromEntries(['left', 'right'].map(s => ['lid_' + s, {pivot: hingeOf(s).map(v => r6(v / 16))}])),
    ...Object.fromEntries(GOODS_BONES.map(b => [b, {pivot: [0, 0, 0]}]))},
  animations: Object.fromEntries(['left', 'right'].map(s => [s + '_open', {duration_ticks: 10, easing: 'ease_in_out', transforms: {['lid_' + s]: {rotation: [OPEN_DEGREES, 0, 0]}}}]))});

// item model: builtin/entity; every view centred on the dumpster (lids shut, no goods)
const itemDisplay = (() => {
  const rot = ([ax, ay]) => q => { const a = ay * Math.PI / 180, b = ax * Math.PI / 180, x = q[0] * Math.cos(a) + q[2] * Math.sin(a), z = -q[0] * Math.sin(a) + q[2] * Math.cos(a);
    return [x, q[1] * Math.cos(b) - z * Math.sin(b), q[1] * Math.sin(b) + z * Math.cos(b)]; };
  const pts = PARTS.filter(p => !NEVER_RENDER.has(p.bone)).flatMap(p => p.v).map(q => [q[0], q[1] - 8, q[2]]), S3 = v => [v, v, v];
  const view = (r, fit, extra = [0, 0, 0]) => { const R = pts.map(rot(r)), xs = R.map(q => q[0]), ys = R.map(q => q[1]), zs = R.map(q => q[2]);
    const s = typeof fit === 'number' && fit > 1 ? r3(fit / Math.max(Math.max(...xs) - Math.min(...xs), Math.max(...ys) - Math.min(...ys))) : fit;
    const c = [(Math.max(...xs) + Math.min(...xs)) / 2, (Math.max(...ys) + Math.min(...ys)) / 2, (Math.max(...zs) + Math.min(...zs)) / 2];
    return {rotation: [r[0], r[1], 0], translation: [r3(-s * c[0] + extra[0]), r3(-s * c[1] + extra[1]), r3(-s * c[2] + extra[2])], scale: S3(s)}; };
  return {gui: view([25, 225], 15.5), ground: view([0, 0], 0.22, [0, 3, 0]), fixed: view([0, 0], 0.4),
    thirdperson_righthand: view([75, 225], 0.25, [0, 2.5, 0]), firstperson_righthand: view([0, 225], 0.28, [0, 1, 0])};
})();
const blockId = colour => colour === 'green' ? ID : `${ID}_${colour}`;
const bb = path.join(ROOT, 'src/main/blockbench'), assets = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight');
const outputs = [[path.join(bb, ID + '.bbmodel'), JSON.stringify(source)],
  [path.join(assets, `geo/${ID}.geo.json`), JSON.stringify(geo, null, 2) + '\n'], [path.join(assets, `meshes/${ID}.aflmesh.json`), meshText],
  ...Object.keys(COLOURS).flatMap(colour => [
    [path.join(assets, `block_mesh_profiles/${ID}_${colour}.json`), JSON.stringify(profileFor(colour), null, 2) + '\n'],
    [path.join(assets, `models/item/${blockId(colour)}.json`), JSON.stringify({parent: 'builtin/entity', gui_light: 'side',
      textures: {particle: `apocalypse_firstlight:block/${ID}_${colour}`}, display: itemDisplay}, null, 2) + '\n'],
    [path.join(assets, `models/block/${blockId(colour)}.json`), JSON.stringify({textures: {particle: `apocalypse_firstlight:block/${ID}_${colour}`}}, null, 2) + '\n'],
    [path.join(assets, `blockstates/${blockId(colour)}.json`), JSON.stringify({variants: {'': {model: `apocalypse_firstlight:block/${blockId(colour)}`}}}, null, 2) + '\n'],
    ...['', '_s', '_n'].flatMap((k, i) => [[path.join(bb, `textures/${ID}_${colour}${k}.png`), PNGS[colour][i]], [path.join(assets, `textures/block/${ID}_${colour}${k}.png`), PNGS[colour][i]]])])];

// coplanar check: the dumpster alone, then each goods bone with it
const isGoods = p => p.bone.startsWith('goods_');
const zfs = [zFightLevels(PARTS, new Map(), {skip: isGoods}), ...GOODS_BONES.map(b => zFightLevels(PARTS, new Map(), {skip: p => isGoods(p) && p.bone !== b}))];
const zf = {unresolved: zfs.flatMap(z => z.unresolved)};
const trisOf = p => p.f.reduce((t, f) => t + f.ids.length - 2, 0);
export const stats = {triangles: sidecar.parts.flatMap(p => p.faces).reduce((s, q) => s + q.length - 2, 0), parts: sidecar.parts.length, texelsPerPx: UV.S,
  coplanarOverlaps: zf.unresolved.length, bounds, gui: itemDisplay.gui, hinges: {left: hingeOf('left').map(r3), right: hingeOf('right').map(r3)},
  lidOpen: Object.fromEntries(Object.keys(HALVES).map(s => [s, aabbOf(lidPoints(s).map(q => swing(q, hingeOf(s), OPEN_DEGREES)))])),
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
    for (const colour of Object.keys(COLOURS)) ['', '_s', '_n'].forEach((k, i) => fs.writeFileSync(path.join(dir, `${ID}_${colour}${k}.png`), PNGS[colour][i]));
    console.log('preview written to ' + dir);
  } else if (process.argv.includes('--check')) {
    for (const [file, data] of outputs) { const cur = fs.existsSync(file) ? fs.readFileSync(file) : null; if (!cur || !cur.equals(Buffer.isBuffer(data) ? data : Buffer.from(data))) throw new Error('stale ' + path.relative(ROOT, file)); }
    console.log('CHECK OK');
  } else {
    for (const [file] of outputs) fs.mkdirSync(path.dirname(file), {recursive: true});
    for (const [file, data] of outputs) fs.writeFileSync(file, data);
    console.log('wrote ' + outputs.length + ' files');
  }
}
