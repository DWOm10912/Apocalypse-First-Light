// Commercial Glass Double Door V2 (docs/models/commercial_glass_double_door_v2.md): the storefront pair door (2 wide x 2
// tall) as Pure Mesh + 1024 LabPBR atlases, rendered by the AFL Animated Block Mesh Runtime (bones 'frame', 'leaf_a',
// 'leaf_b'; channel 'open') and in inventories by AflStaticMeshItemRenderer. One mesh, two finishes: the silver door
// (clear anodised aluminium) and the black door (black anodised, the Storefront Glazing frame). User 2026-10-07: concept
// option A, a medium-stile storefront door (3 1/2" stiles and top rail, 10" bottom rail) with offset pulls on the meeting
// stiles, the two leaves filling the 2-block unit (V1 had a 1 m pair between two fixed lites).
// The leaves swing 90 degrees toward FACING, the side the door was placed from: placed from outside, out of the building
// like a real storefront door. The frame stands in the Storefront Glazing's plane (3..5 px behind the FACING face).
//   node tools/build-commercial-glass-double-door-v2.mjs                 -> writes source, geo / sidecar / profiles / maps / models
//   node tools/build-commercial-glass-double-door-v2.mjs --check         -> verifies every output is up to date
//   node tools/build-commercial-glass-double-door-v2.mjs --preview DIR   -> writes only geo / sidecar / maps into DIR
// Frame (px): facing north, the FACING face at z -8, X -8..24 (the master lower-left cell -8..8, the second column at
// facing.getClockWise() = +X), Y 0..32; runtime origin = the bottom centre of the master cell.
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

// ---------------- dimensions (px) ----------------
// frame members as the Storefront Glazing's (1 x 2 px, 3..5 px behind the outer face); leaves 1 3/4" thick on the frame's
// centre line; stiles / top rail 3 1/2", bottom rail 10"; 1" insulated glass; hinges on the outer jambs
export const D = {frameZ: [-5, -3], jamb: 1, head: 1, leafZ: [-4.36, -3.64], gap: 0.05, leafY: [0.25, 30.95], stile: 1.42, top: 1.42, bottom: 4.06,
  glassZ: [-4.2, -3.8], gasket: 0.14, threshold: {y: 0.2, z: [-5.6, -2.4]}, hingeZ: -4.58, hingeR: 0.22,
  pull: {y: [13.6, 20.0], r: 0.2, standoff: 1.0}, push: {y: 16.8, r: 0.2, standoff: 1.0}};
const X0 = -8 + D.jamb, X1 = 24 - D.jamb, MID = 8;                    // clear opening and the meeting line
export const HINGE = {leaf_a: [X0, 0, D.hingeZ], leaf_b: [X1, 0, D.hingeZ]};
export const OPEN_DEGREES = {leaf_a: 90, leaf_b: -90}, OPEN_TICKS = 6;   // toward -Z (FACING); V1 reached 90 degrees in about 5 ticks

// ---------------- primitives ----------------
function slab(part, ax, a, b, c = 0) {
  const [ku, kv, ka] = ax === 'x' ? [2, 1, 0] : ax === 'y' ? [2, 0, 1] : [0, 1, 2];
  extrude(part, ax, {outer: orient(rect(a[ku], a[kv], b[ku], b[kv]), true), holes: []}, a[ka], b[ka], c);
}
function frame(part, outer, hole, z0, z1, c) {   // a front-facing plate with a rectangular opening, extruded along z
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
function quad(part, pts, n) { part.face(pts.map(q => part.vtx(q)), n, 'cap'); }

// ---------------- frame (fixed): jambs, head, threshold ----------------
{
  const f = P('door_frame', 'frame', 'frame'), [z0, z1] = D.frameZ;
  slab(f, 'z', [-8, 0, z0], [X0, 32, z1], 0);
  slab(f, 'z', [X1, 0, z0], [24, 32, z1], 0);
  slab(f, 'z', [X0, 32 - D.head, z0], [X1, 32, z1], 0);
  slab(P('threshold', 'frame', 'threshold'), 'y', [X0, 0, D.threshold.z[0]], [X1, D.threshold.y, D.threshold.z[1]], 0);
}
// ---------------- leaves (leaf_b = leaf_a mirrored about the meeting line) ----------------
for (const leaf of ['leaf_a', 'leaf_b']) {
  const mx = leaf === 'leaf_a' ? x => x : x => 2 * MID - x, X = (a, b) => [Math.min(mx(a), mx(b)), Math.max(mx(a), mx(b))];
  const [y0, y1] = D.leafY, [lz0, lz1] = D.leafZ, xa = X0 + D.gap, xb = MID - D.gap;   // leaf_a frame: hinge edge .. meeting edge
  const [fx0, fx1] = X(xa, xb), [hx0, hx1] = X(xa + D.stile, xb - D.stile), hy0 = y0 + D.bottom, hy1 = y1 - D.top;
  frame(P(`${leaf}_frame`, leaf, 'frame'), [fx0, y0, fx1, y1], [hx0, hy0, hx1, hy1], lz0, lz1, 0.06);
  // the lite: its two broad faces only (its edges sit against the leaf frame's opening)
  const g = P(`${leaf}_glass`, leaf, 'glass'), [gz0, gz1] = D.glassZ;
  quad(g, [[hx0, hy0, gz0], [hx1, hy0, gz0], [hx1, hy1, gz0], [hx0, hy1, gz0]], [0, 0, -1]);
  quad(g, [[hx0, hy0, gz1], [hx1, hy0, gz1], [hx1, hy1, gz1], [hx0, hy1, gz1]], [0, 0, 1]);
  // black glazing gaskets round the lite on both faces, just proud of the glass
  const gk = P(`${leaf}_gaskets`, leaf, 'gasket'), e = 0.003, w = D.gasket;
  frame(gk, [hx0 + e, hy0 + e, hx1 - e, hy1 - e], [hx0 + w, hy0 + w, hx1 - w, hy1 - w], gz0 - 0.05, gz0, 0);
  frame(gk, [hx0 + e, hy0 + e, hx1 - e, hy1 - e], [hx0 + w, hy0 + w, hx1 - w, hy1 - w], gz1, gz1 + 0.05, 0);
  const hw = P(`${leaf}_hardware`, leaf, 'metal');
  // offset pull on the FACING (pull) side, on the meeting stile: a 1" bar 0.40 m long on two standoffs
  const px = mx(xb - D.stile / 2), pz = lz0 - D.pull.standoff;
  cyl(hw, 'y', pz, px, D.pull.r, D.pull.y[0], D.pull.y[1], 10);
  for (const y of [D.pull.y[0] + 0.6, D.pull.y[1] - 0.6]) cyl(hw, 'z', px, y, 0.14, pz, lz0, 8);
  // push bar on the other side, across the leaf between the stiles
  const bz = lz1 + D.push.standoff, [bx0, bx1] = X(xa + D.stile / 2, xb - D.stile / 2);
  cyl(hw, 'x', bz, D.push.y, D.push.r, bx0, bx1, 10);
  for (const x of [bx0 + 0.3, bx1 - 0.3]) cyl(hw, 'z', x, D.push.y, 0.14, lz1, bz, 8);
  // three butt-hinge knuckles on the pull side of the hinge edge (the swing axis)
  const [hgx] = HINGE[leaf];
  for (const y of [3.2, 15.2, 27.2]) cyl(hw, 'y', D.hingeZ, hgx, D.hingeR, y, y + 2, 10);
  // overhead closer on the push side of the top rail, near the hinge
  const [cx0, cx1] = X(xa + 1.0, xa + 5.0);
  slab(P(`${leaf}_closer`, leaf, 'frame'), 'z', [cx0, y1 - D.top + 0.2, lz1], [cx1, y1 - 0.2, lz1 + 0.7], 0.08);
}

// ---------------- materials (two finishes, one layout) ----------------
const COMMON = {
  glass:     {c: [176, 204, 204], hl: 0, sm: 240, se: 240, f0: 10},   // as the Storefront Glazing lite; alpha below
  gasket:    {c: [22, 23, 25], hl: 0, sm: 50, se: 50, f0: 14},          // EPDM glazing gasket
  metal:     {c: [168, 171, 175], hl: 14, sm: 150, se: 170, f0: 255},   // satin stainless pulls, push bars, hinges
  threshold: {c: [150, 153, 156], hl: 4, sm: 110, se: 120, f0: 232},    // mill-finish aluminium threshold
};
export const FINISHES = {
  // black anodised: the Storefront Glazing frame (sm 76 / F0 14; brighter read light grey under shaders, 2026-10-06)
  black: {...COMMON, frame: {c: [36, 38, 41], hl: 4, sm: 76, se: 90, f0: 14}},
  // clear anodised aluminium: the aluminium metal preset at a satin smoothness
  silver: {...COMMON, frame: {c: [172, 176, 180], hl: 8, sm: 120, se: 136, f0: 232}},
};
export const GLASS_ALPHA = 46;   // as the Storefront Glazing (> 26 survives the shader packs' translucent alpha test)

const ATLAS = 1024, PAD = 2;
const UV = unwrap(PARTS, {atlas: ATLAS, pad: PAD, startS: 24, stepS: 0.25});
for (const p of PARTS) assert(p.f.every(f => UV.faceUV.get(p)?.has(f)), 'unmapped face in ' + p.name);
const DUMMY = 'data:image/png;base64,' + png(Buffer.from([0, 0, 0, 255]), 1, 1).toString('base64');
function maps(MATS) {
  const painted = paint({PARTS, islands: UV.islands, S: UV.S, uvOf: UV.uvOf, atlas: ATLAS, pad: PAD, MATS, ZONED: new Set(), groupInfo: new Map(),
    sourceGroups: () => [], refTexture: {source: DUMMY}, refUvWidth: 1, background: {c: [...MATS.frame.c, 255], s: [MATS.frame.sm, MATS.frame.f0, 0, 255], n: [128, 128, 255, 255]}});
  const M = painted.PNG.map(b => { const r = readPng(b); assert(r.bpp === 4 && r.w === ATLAS, 'atlas format'); return r.px; });
  for (const is of UV.islands) if (is.part.mat === 'glass')
    for (let y = is.py - PAD; y < is.py + is.H + PAD; y++) for (let x = is.px - PAD; x < is.px + is.W + PAD; x++) M[0][(y * ATLAS + x) * 4 + 3] = GLASS_ALPHA;
  return M.map(px => png(px, ATLAS, ATLAS));
}
const PNGS = {silver: maps(FINISHES.silver), black: maps(FINISHES.black)};

// ---------------- source (Free Model) ----------------
const ID = 'commercial_glass_double_door';
const uuid = s => { const h = createHash('sha256').update('afl-commercial-glass-double-door-v2:' + s).digest('hex'); return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-8${h.slice(17, 20)}-${h.slice(20, 32)}`; };
const r12 = v => +v.toFixed(12) || 0;
const RIG = [['frame', [0, 0, 0]], ['leaf_a', HINGE.leaf_a], ['leaf_b', HINGE.leaf_b]];
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
      source: 'data:image/png;base64,' + PNGS.silver[0].toString('base64')}],
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
const aroundY = (q, pv, deg) => { const a = deg * Math.PI / 180, x = q[0] - pv[0], z = q[2] - pv[2];   // the runtime's Ry, as the cooler doors
  return [pv[0] + x * Math.cos(a) + z * Math.sin(a), q[1], pv[2] - x * Math.sin(a) + z * Math.cos(a)]; };
export const posed = (p, t) => p.bone === 'frame' ? p.v : p.v.map(q => aroundY(q, HINGE[p.bone], OPEN_DEGREES[p.bone] * t));
const aabbOf = pts => [0, 1, 2].map(k => Math.min(...pts.map(q => q[k]))).concat([0, 1, 2].map(k => Math.max(...pts.map(q => q[k]))));
const bounds = (() => {
  const b = aabbOf(PARTS.flatMap(p => Array.from({length: 11}, (_, s) => posed(p, s / 10)).flat())), m = 0.25;
  return [(b[0] - m + 8) / 16, (b[1] - m) / 16, (b[2] - m + 8) / 16, (b[3] + m + 8) / 16, (b[4] + m) / 16, (b[5] + m + 8) / 16].map(r6);
})();
const profile = finish => ({format_version: 1, geometry: `apocalypse_firstlight:geo/${ID}.geo.json`,
  texture: `apocalypse_firstlight:textures/block/${finish === 'black' ? ID + '_black' : ID}.png`, origin: [0, 0, 0], scale: [1, 1, 1], facing: 'horizontal', bounds,
  parts: Object.fromEntries(RIG.map(([name, origin]) => [name, {pivot: origin.map(v => r6(v / 16))}])),
  // both leaves on one channel, following the block's OPEN (CommercialGlassDoubleDoorBlock toggles all four parts)
  animations: {open: {duration_ticks: OPEN_TICKS, easing: 'ease_in_out', transforms: {leaf_a: {rotation: [0, OPEN_DEGREES.leaf_a, 0]}, leaf_b: {rotation: [0, OPEN_DEGREES.leaf_b, 0]}}}}});

// item model: builtin/entity; every view centred on the closed mesh (it spans the master cell and the next one, +X)
const itemDisplay = (() => {
  const rot = ([ax, ay]) => q => { const a = ay * Math.PI / 180, b = ax * Math.PI / 180, x = q[0] * Math.cos(a) + q[2] * Math.sin(a), z = -q[0] * Math.sin(a) + q[2] * Math.cos(a);
    return [x, q[1] * Math.cos(b) - z * Math.sin(b), q[1] * Math.sin(b) + z * Math.cos(b)]; };
  const pts = PARTS.flatMap(p => p.v).map(q => [q[0], q[1] - 8, q[2]]), S3 = v => [v, v, v];
  const view = (r, fit, extra = [0, 0, 0]) => { const R = pts.map(rot(r)), xs = R.map(q => q[0]), ys = R.map(q => q[1]), zs = R.map(q => q[2]);
    const s = typeof fit === 'number' && fit > 1 ? r3(fit / Math.max(Math.max(...xs) - Math.min(...xs), Math.max(...ys) - Math.min(...ys))) : fit;
    const c = [(Math.max(...xs) + Math.min(...xs)) / 2, (Math.max(...ys) + Math.min(...ys)) / 2, (Math.max(...zs) + Math.min(...zs)) / 2];
    return {rotation: [r[0], r[1], 0], translation: [r3(-s * c[0] + extra[0]), r3(-s * c[1] + extra[1]), r3(-s * c[2] + extra[2])], scale: S3(s)}; };
  return {gui: view([25, 225], 14), ground: view([0, 0], 0.22, [0, 3, 0]), fixed: view([0, 0], 0.4),
    thirdperson_righthand: view([75, 225], 0.25, [0, 2.5, 0]), firstperson_righthand: view([0, 225], 0.28, [0, 1, 0])};
})();

const bb = path.join(ROOT, 'src/main/blockbench'), assets = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight');
const outputs = [[path.join(bb, ID + '.bbmodel'), JSON.stringify(source)], [path.join(assets, `geo/${ID}.geo.json`), JSON.stringify(geo, null, 2) + '\n'],
  [path.join(assets, `meshes/${ID}.aflmesh.json`), meshText]];
for (const finish of ['silver', 'black']) {
  const id = finish === 'black' ? ID + '_black' : ID;
  outputs.push([path.join(assets, `block_mesh_profiles/${id}.json`), JSON.stringify(profile(finish), null, 2) + '\n'],
    [path.join(assets, `models/item/${id}.json`), JSON.stringify({parent: 'builtin/entity', gui_light: 'side', textures: {particle: `apocalypse_firstlight:block/${id}`}, display: itemDisplay}, null, 2) + '\n'],
    [path.join(assets, `models/block/${id}.json`), JSON.stringify({textures: {particle: `apocalypse_firstlight:block/${id}`}}, null, 2) + '\n'],
    ...['', '_s', '_n'].flatMap((k, i) => [[path.join(bb, `textures/${id}${k}.png`), PNGS[finish][i]], [path.join(assets, `textures/block/${id}${k}.png`), PNGS[finish][i]]]));
}

const faces = sidecar.parts.flatMap(p => p.faces), zf = zFightLevels(PARTS, new Map());
export const stats = {triangles: faces.reduce((s, q) => s + q.length - 2, 0), parts: sidecar.parts.length, texelsPerPx: UV.S, islands: UV.islands.length,
  translucent: Object.keys(LAYERS), coplanarOverlaps: zf.unresolved.length, bounds, closed: aabbOf(PARTS.flatMap(p => p.v)).map(r3), gui: itemDisplay.gui,
  open: Object.fromEntries(['leaf_a', 'leaf_b'].map(b => [b, aabbOf(PARTS.filter(p => p.bone === b).flatMap(p => posed(p, 1))).map(r3)])),
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
    for (const finish of ['silver', 'black']) ['', '_s', '_n'].forEach((k, i) => fs.writeFileSync(path.join(dir, `${finish === 'black' ? ID + '_black' : ID}${k}.png`), PNGS[finish][i]));
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
