// Generator Inlet Box V1 (docs/models/generator_inlet_v1.md; forest start home E1, user 2026-10-13 concept
// design/buildings/forest_start_home_v1/m2_sheets/m2_3_e1_inlet.png): the house's 30 A power inlet box
// (`generator_inlet_box`, 发电机进线盒) on an outside wall, with its 15 m 10/4 yellow cord. The box: grey powder-coated
// steel, a black nylon L14-30 inlet on an angled boss (facing 60 deg down), a hinged weather hood with a cord notch in its
// bottom, a J hook under it. The cord's L14-30 connector stays seated in the inlet; while its plug is in no socket and no
// hand the cord hangs coiled on the hook (bone coil). The L14-30P plug is an extra OBJ model (block/l14_30_plug) the cord
// renderer draws (client/PlugCordRenderer) with the cord, painted from this atlas' cord swatch.
// Real size (the concept's: box 17 x 24 x 16.5 cm). Pure Mesh + 512 LabPBR atlas for the AFL Animated Block Mesh Runtime.
// Bones / channels: body; hood ('open', +100 deg about X at the top hinge: lifted out like an awning); coil (shown only
// while the cord is stowed).
//   node tools/build-generator-inlet-v1.mjs                -> writes source, geo / sidecar / profile / maps / models / blockstate
//   node tools/build-generator-inlet-v1.mjs --check        -> verifies every output is up to date
//   node tools/build-generator-inlet-v1.mjs --preview DIR  -> writes only sidecar / profile / maps / plug OBJ into DIR
// Modelled in millimetres (the library's minimum sizes are absolute), converted to px at the end (1 mm = 0.016 px).
// Box frame (mm): x to the viewer's right, y up from the box centre, z out of the wall (the wall at z 0). It maps to the
// block frame (px, block bottom centre at the origin, front toward -Z at facing=north, the wall at z = +8, +X the
// viewer's left) by [-x, 8 + y, 8 - z] (a turn of 180 deg about Y): the box centre 8 px up in the cell.
// Plug frame (mm, bone plug, OBJ only): the face (blade side) at z 0 with the blades toward -Z, the tail toward +Z, the
// ground blade up (+Y); centred on the block centre in the OBJ, so the renderer turns it about (0.5, 0.5, 0.5).
// Plain surfaces: no printed legends.
import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {convert, serializeCompact} from './export-afl-mesh.mjs';
import {Part, AX, extrude, revolve, unwrap, paint, png, readPng, zFightLevels, area2, add, sub, mul, dot, cross, norm, newell} from './cube-slab-mesh-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
function assert(c, m) { if (!c) throw new Error(m); }
const PX = 0.016, D2R = Math.PI / 180;
const r6 = v => +v.toFixed(6) || 0, r3 = v => +v.toFixed(3) || 0, r12 = v => +v.toFixed(12) || 0;
const orient = (L, ccw) => (area2(L) > 0) === ccw ? L : L.slice().reverse();
const rect = (u0, v0, u1, v1) => [[u0, v0], [u1, v0], [u1, v1], [u0, v1]];
const shapeOf = (outer, holes = []) => ({outer: orient(outer, true), holes: holes.map(h => orient(h, false))});
const circ = (r, seg, cu = 0, cv = 0) => Array.from({length: seg}, (_, i) => { const a = Math.PI / seg + 2 * Math.PI * i / seg; return [cu + r * Math.cos(a), cv + r * Math.sin(a)]; });
function slab(part, ax, a, b, c = 0) {
  const [ku, kv, ka] = ax === 'x' ? [2, 1, 0] : ax === 'y' ? [2, 0, 1] : [0, 1, 2];
  extrude(part, ax, shapeOf(rect(a[ku], a[kv], b[ku], b[kv])), a[ka], b[ka], c);
}
function cyl(part, ax, cu, cv, r, a0, a1, seg) {
  const A = AX[ax], ang = i => Math.PI / seg + 2 * Math.PI * i / seg;
  const rg = a => Array.from({length: seg}, (_, i) => part.vtx(A.to3(cu + r * Math.cos(ang(i)), cv + r * Math.sin(ang(i)), a)));
  const r0 = rg(a0), r1 = rg(a1);
  for (let i = 0; i < seg; i++) { const j = (i + 1) % seg, m = (ang(i) + ang(j)) / 2;
    part.face([r0[i], r0[j], r1[j], r1[i]], A.to3(Math.cos(m), Math.sin(m), 0), 'side'); }
  for (const [ring, a, s] of [[r0, a0, -1], [r1, a1, 1]]) { const c = part.vtx(A.to3(cu, cv, a));
    for (let i = 0; i < seg; i++) part.face([c, ring[i], ring[(i + 1) % seg]], mul(A.n, s), 'cap'); }
}
// every vertex the builder adds (to existing or new parts) goes through fn afterwards (a proper rigid motion: windings kept)
function inFrame(PARTS, fn, build) {
  const n0 = PARTS.length, counts = PARTS.map(p => p.v.length);
  build();
  PARTS.forEach((p, k) => { for (let i = k < n0 ? counts[k] : 0; i < p.v.length; i++) p.v[i] = fn(p.v[i]); });
}
const frame = (o, X, Y, Z) => q => add(o, add(mul(X, q[0]), add(mul(Y, q[1]), mul(Z, q[2]))));
const rotZ = a => q => [q[0] * Math.cos(a) - q[1] * Math.sin(a), q[0] * Math.sin(a) + q[1] * Math.cos(a), q[2]];
// a round tube along the points (parallel-transport rings), both ends capped
function tube(part, pts, r, sides) {
  const T = pts.map((_, i) => norm(sub(pts[Math.min(i + 1, pts.length - 1)], pts[Math.max(i - 1, 0)])));
  let N = norm(Math.abs(T[0][1]) < 0.9 ? cross(T[0], [0, 1, 0]) : cross(T[0], [1, 0, 0]));
  const rings = pts.map((p, i) => {
    if (i) N = norm(sub(N, mul(T[i], dot(N, T[i]))));
    const B = cross(T[i], N);
    return Array.from({length: sides}, (_, k) => { const a = 2 * Math.PI * k / sides; return part.vtx(add(p, add(mul(N, r * Math.cos(a)), mul(B, r * Math.sin(a))))); });
  });
  for (let i = 0; i + 1 < pts.length; i++) for (let k = 0; k < sides; k++) {
    const k2 = (k + 1) % sides, q = [rings[i][k], rings[i][k2], rings[i + 1][k2], rings[i + 1][k]];
    part.face(q, sub(mul(q.reduce((s, id) => add(s, part.v[id]), [0, 0, 0]), 0.25), mul(add(pts[i], pts[i + 1]), 0.5)), 'side');
  }
  for (const [i, s] of [[0, -1], [pts.length - 1, 1]]) { const c = part.vtx(pts[i]);
    for (let k = 0; k < sides; k++) part.face([c, rings[i][k], rings[i][(k + 1) % sides]], mul(T[i], s), 'cap'); }
}
// Catmull-Rom through the control points, a span split every `step` mm
function spline(C, step) {
  const out = [C[0]];
  for (let i = 0; i + 1 < C.length; i++) {
    const p0 = C[Math.max(0, i - 1)], p1 = C[i], p2 = C[i + 1], p3 = C[Math.min(C.length - 1, i + 2)];
    const n = Math.max(1, Math.ceil(Math.hypot(...sub(p2, p1)) / step));
    for (let k = 1; k <= n; k++) { const t = k / n, t2 = t * t, t3 = t2 * t;
      out.push([0, 1, 2].map(j => 0.5 * (2 * p1[j] + (-p0[j] + p2[j]) * t + (2 * p0[j] - 5 * p1[j] + 4 * p2[j] - p3[j]) * t2 + (-p0[j] + 3 * p1[j] - 3 * p2[j] + p3[j]) * t3))); }
  }
  return out;
}

// ---------------- dimensions (mm, box frame) ----------------
export const BOX = {w: 85, h: 120, body: 50, back: 0.3, chamfer: 4};
// the inlet face: its centre o, tilted 60 deg down; the boss's half size, flange (r, height), shroud (outer, inner, top),
// the connector seated at `seat`
export const INLET = {o: [0, -32, 82], tilt: 60, boss: 30, flange: [36, 6], shroud: [30.5, 25.5, 26], seat: 6.6};
// the hood (hinged at the box's top front edge): half width W, depth D, height Hh, sheet t, cord notch half width N from NZ
export const HOOD = {W: 88, D: 115, Hh: 243, t: 3, N: 34, NZ: 18, deg: 100, ticks: 8};
export const HOOK = {y: -215, plate: [20, 30, 4], r: 4.2, path: [[0, 0, 3], [0, 0, 50], [0, -12, 75], [0, 12, 88], [0, 30, 84]]};
// the cord (10/4 SOOW, 21 mm) and its coil on the hook
export const CORD = {r: 10.5, sides: 8, coilR: 150, loops: 6, zFront: 64, zBack: 22};
// the L14-30 connector / plug body (lathe [z, r] from the face back to the tail), with four strain relief ribs
export const TAIL = 128;
export const END_PROFILE = (() => {
  const rb = z => 16.2 - 0.18 * (z - 104), pts = [[0, 24.4], [17, 24.4], [17.2, 28.5], [20, 29.2], [82, 28.8], [94, 25.5], [100, 17.5], [104, 16.2]];
  for (let i = 0; i < 4; i++) { const a = 106 + 4.5 * i, b = a + 1.5; pts.push([a, rb(a)], [a + 0.3, rb(a) + 1.2], [b - 0.3, rb(b) + 1.2], [b, rb(b)]); }
  return pts.concat([[124, 12.6], [TAIL, 11.2]]);
})();

// the L14-30 body (connector or plug) in its face frame: lathe, ten grip ribs; a plug also gets its blades
function cordEnd(PARTS, P, bone, male, fn) {
  inFrame(PARTS, fn, () => {
    const loop = [[0, 0], ...END_PROFILE, [TAIL, 0]];
    if (area2(loop) < 0) loop.reverse();
    revolve(P(`${bone}_${male ? 'plug' : 'connector'}_body`, bone, 'rubber'), 0, 0, loop, 24);
    const ribs = P(`${bone}_${male ? 'plug' : 'connector'}_grips`, bone, 'rubber');
    for (let i = 0; i < 10; i++) inFrame(PARTS, rotZ((i + 0.5) * 36 * D2R), () => slab(ribs, 'z', [-2.2, 27.6, 28], [2.2, 30.4, 78], 0.8));
    if (!male) return;
    // blades on a 23 mm circle: curved at 3, 6 and 9 o'clock, the ground blade straight at 12 with its bent tip
    const arc = (a, span, r0 = 10.4, r1 = 12.6, seg = 6) => {
      const at = (r, i) => { const t = (a - span / 2 + span * i / seg) * D2R; return [r * Math.sin(t), r * Math.cos(t)]; };
      return [...Array.from({length: seg + 1}, (_, i) => at(r1, i)), ...Array.from({length: seg + 1}, (_, i) => at(r0, seg - i))];
    };
    const bl = P(`${bone}_plug_blades`, bone, 'brass');
    for (const a of [90, 180, 270]) extrude(bl, 'z', shapeOf(arc(a, 38)), -16, 0.5, 0);
    slab(bl, 'z', [-1.2, 8.5, -16], [1.2, 14.5, 0.5]);
    slab(bl, 'z', [1.0, 8.7, -15.8], [4.2, 10.9, -12]);
  });
}

// ---------------- build ----------------
export const FACTS = {};
function build() {
  const PARTS = [], P = (name, bone, mat) => { const p = new Part(name, bone, mat); PARTS.push(p); return p; };
  // the box, a little off the wall face
  slab(P('box', 'body', 'grey'), 'z', [-BOX.w, -BOX.h, BOX.back], [BOX.w, BOX.h, BOX.body], BOX.chamfer);
  // the inlet's face frame: z out of the inlet (down and out), y up the face, x across
  const t = INLET.tilt * D2R, Z = [0, -Math.sin(t), Math.cos(t)], Y = [0, Math.cos(t), Math.sin(t)], X = [1, 0, 0], face = frame(INLET.o, X, Y, Z);
  const lo = face([0, -INLET.boss, 0]), hi = face([0, INLET.boss, 0]);
  // the boss: a wedge out of the box front up to the face plane (axis x: (u, v) = (z, y))
  extrude(P('boss', 'body', 'greyEdge'), 'x', shapeOf([[BOX.body - 5, lo[1]], [lo[2], lo[1]], [hi[2], hi[1]], [BOX.body - 5, hi[1]]]), -INLET.boss, INLET.boss, 2);
  inFrame(PARTS, face, () => {
    cyl(P('inlet_flange', 'body', 'nylon'), 'z', 0, 0, INLET.flange[0], -1, INLET.flange[1], 32);
    const [ro, ri, top] = INLET.shroud;
    extrude(P('inlet_shroud', 'body', 'nylon'), 'z', shapeOf(circ(ro, 32), [circ(ri, 32)]), INLET.flange[1] - 0.5, top, 0.6);
    const sc = P('inlet_screws', 'body', 'zinc');
    for (const a of [45, 225]) cyl(sc, 'z', 32.5 * Math.cos(a * D2R), 32.5 * Math.sin(a * D2R), 3.5, INLET.flange[1] - 0.8, INLET.flange[1] + 1.5, 12);
  });
  // the cord's connector, seated in the inlet for good
  cordEnd(PARTS, P, 'body', false, q => face([q[0], q[1], q[2] + INLET.seat]));
  FACTS.exit = face([0, 0, INLET.seat + TAIL]); FACTS.exitDir = Z;
  // the hinge barrel on the box's top front edge (axis x: (u, v) = (z, y))
  cyl(P('hinge', 'body', 'greyEdge'), 'x', BOX.body, BOX.h, 4.5, -(HOOD.W - 6), HOOD.W - 6, 14);
  // the hood in its hinge frame (y down from the hinge to -Hh, z out to D): back ring, notched middle, front plate, lip
  const {W, D, Hh, t: s, N, NZ} = HOOD, hinge = [0, BOX.h, BOX.body];
  inFrame(PARTS, q => add(q, hinge), () => {
    const hood = P('hood_shell', 'hood', 'grey');
    extrude(hood, 'z', shapeOf(rect(-W, -Hh, W, 0), [rect(-W + s, -Hh + s, W - s, -s)]), 0, NZ, 0);
    extrude(hood, 'z', shapeOf([[-N, -Hh], [-W, -Hh], [-W, 0], [W, 0], [W, -Hh], [N, -Hh], [N, -Hh + s], [W - s, -Hh + s], [W - s, -s], [-W + s, -s], [-W + s, -Hh + s], [-N, -Hh + s]]),
      NZ, D - s, 0);
    extrude(hood, 'z', shapeOf(rect(-W, -Hh, W, 0)), D - s, D, 0);
    slab(P('hood_lip', 'hood', 'greyEdge'), 'z', [-W + 10, -Hh + 6, D - 0.5], [W - 10, -Hh + 13, D + 3.5], 1);
  });
  // the J hook under the box
  slab(P('hook_plate', 'body', 'steel'), 'z', [-HOOK.plate[0], HOOK.y - HOOK.plate[1], BOX.back], [HOOK.plate[0], HOOK.y + HOOK.plate[1], HOOK.plate[2]], 1);
  tube(P('hook_j', 'body', 'steel'), spline(HOOK.path.map(q => add(q, [0, HOOK.y, 0])), 6), HOOK.r, 8);
  // the coil: from the connector's tail down to the hook, six loops over it (front to back), the free end over the
  // bundle and down in front of it to the plug, hanging blades down
  const hookTop = z => HOOK.y + HOOK.r + (z <= 50 ? 0 : z <= 75 ? -12 * (z - 50) / 25 : -12 + 24 * Math.min(1, (z - 75) / 13));
  const top = z => hookTop(z) + CORD.r;
  // the lead comes down in front of the hook, beside its tip, and turns onto the hook top along the first loop (+x)
  const t0 = top(CORD.zFront), lead = [face([0, 0, INLET.seat + TAIL - 2]), face([0, 0, INLET.seat + TAIL + 25]), [-20, t0 + 16, 110], [-34, t0 + 4, 70]];
  const L = CORD.loops, n = 32 * L, spiral = [];
  for (let i = 0; i <= n; i++) {
    const a = 2 * Math.PI * i / 32, f = i / n, z = CORD.zFront + (CORD.zBack - CORD.zFront) * f + 2.5 * Math.sin(2.3 * a), R = CORD.coilR * (1 + 0.03 * Math.sin(1.7 * a + 0.5));
    spiral.push([R * Math.sin(a), top(z) - R + R * Math.cos(a), z]);
  }
  const hang = [52, -312, 110];
  const end = [[14, top(34) + 2 * CORD.r, 34], [34, -184, 76], [46, -214, 104], [51, -262, 110], [52, -298, 110], add(hang, [0, -2, 0])];
  const coil = P('coil_cord', 'coil', 'cord');
  coil.pointUV = true;   // one colour: every corner at the swatch's centre texel (no islands for the tube's many faces)
  tube(coil, spline([...lead, ...spiral, ...end], 30), CORD.r, CORD.sides);
  cordEnd(PARTS, P, 'coil', true, frame(sub(hang, [0, TAIL, 0]), [-1, 0, 0], [0, 0, 1], [0, 1, 0]));
  // the L14-30P for the cord renderer (OBJ only)
  cordEnd(PARTS, P, 'plug', true, q => q);
  // the cord's colour swatch (never exported): the coil's and the renderer's cord take its UV
  slab(P('cord_swatch', 'swatch', 'cord'), 'z', [600, 0, 0], [606, 6, 1]);
  return PARTS;
}

// ---------------- materials ----------------
export const MATS = {
  grey:     {c: [167, 171, 169], hl: 10, sm: 98, se: 118, f0: 20},     // grey powder coat
  greyEdge: {c: [142, 146, 144], hl: 10, sm: 98, se: 118, f0: 20},
  nylon:    {c: [29, 30, 32], hl: 6, sm: 110, se: 124, f0: 20},        // black nylon inlet
  rubber:   {c: [25, 25, 26], hl: 6, sm: 70, se: 82, f0: 20},          // the connector / plug bodies
  brass:    {c: [196, 170, 110], hl: 14, sm: 172, se: 188, f0: 255},
  steel:    {c: [150, 154, 158], hl: 10, sm: 124, se: 146, f0: 30},    // galvanised hook (coated, as the cable's steel)
  zinc:     {c: [176, 178, 176], hl: 12, sm: 150, se: 170, f0: 255},
  cord:     {c: [220, 174, 34], hl: 0, sm: 96, se: 96, f0: 20},        // yellow SOOW jacket
};

// ---------------- bake ----------------
const ATLAS = 512, PAD = 2;
const DUMMY = 'data:image/png;base64,' + png(Buffer.from([0, 0, 0, 255]), 1, 1).toString('base64');
const PARTS = build().filter(p => p.f.length);
const toBlock = q => [-q[0] * PX, 8 + q[1] * PX, 8 - q[2] * PX];
for (const p of PARTS) p.v = p.v.map(p.bone === 'plug' ? q => q.map(v => v * PX) : toBlock);
export const EXIT = toBlock(FACTS.exit).map(r6), EXIT_DIR = [-FACTS.exitDir[0], FACTS.exitDir[1], -FACTS.exitDir[2]].map(r6);
const mapped = PARTS.filter(p => !p.pointUV);
const UV = unwrap(mapped, {atlas: ATLAS, pad: PAD, startS: 48, stepS: 0.5});
for (const p of mapped) assert(p.f.every(f => UV.faceUV.get(p)?.has(f)), 'unmapped face in ' + p.name);
const swatch = UV.islands.filter(is => is.part.name === 'cord_swatch').sort((a, b) => b.W * b.H - a.W * a.H)[0];
const sw = [Math.floor(swatch.px + swatch.W / 2) + 0.5, Math.floor(swatch.py + swatch.H / 2) + 0.5];
// the cord swatch's UV (0..1 of the atlas, a texel centre; mirrored in PlugCordRenderer); the coil's tube is drawn there
export const CORD_UV = sw.map(v => +(v / ATLAS).toFixed(6));
for (const p of PARTS.filter(q => q.pointUV)) UV.faceUV.set(p, new Map(p.f.map(f => [f, f.ids.map(() => sw.slice())])));
const painted = paint({PARTS: mapped, islands: UV.islands, S: UV.S, uvOf: UV.uvOf, atlas: ATLAS, pad: PAD, MATS, ZONED: new Set(), groupInfo: new Map(),
  sourceGroups: () => [], refTexture: {source: DUMMY}, refUvWidth: 1, background: {c: [...MATS.grey.c, 255], s: [MATS.grey.sm, MATS.grey.f0, 0, 255], n: [128, 128, 255, 255]}});
const PNGS = painted.PNG.map(b => png(readPng(b).px, ATLAS, ATLAS));

// ---------------- the animated mesh ----------------
const ID = 'generator_inlet_box', HINGE = toBlock([0, BOX.h, BOX.body]).map(r6);
const RIG = [['body', [0, 0, 0]], ['hood', HINGE], ['coil', [0, 0, 0]]];
const ANIM = {open: {ticks: HOOD.ticks, transforms: {hood: {rotation: [HOOD.deg, 0, 0]}}}};
const MESH = PARTS.filter(p => RIG.some(([b]) => b === p.bone));
const uuid = k => { const h = createHash('sha256').update('afl-generator-inlet-v1:' + ID + ':' + k).digest('hex'); return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-8${h.slice(17, 20)}-${h.slice(20, 32)}`; };
const nodes = new Map(RIG.map(([b]) => [b, {uuid: uuid('group:' + b), isOpen: true, children: []}])), elements = [];
for (const p of MESH) {
  const key = i => i.toString(36).padStart(4, '0'), vertices = {}, faces = {}, uvs = UV.faceUV.get(p);
  p.v.forEach((q, i) => { vertices[key(i)] = q.map(r12); });
  p.f.forEach((f, fi) => { const uv = uvs.get(f); faces['f' + key(fi)] = {uv: Object.fromEntries(f.ids.map((vid, j) => [key(vid), uv[j].map(r12)])), vertices: f.ids.map(key), texture: 0}; });
  const eid = uuid('mesh:' + p.name);
  elements.push({name: p.name, color: 0, origin: [0, 0, 0], rotation: [0, 0, 0], shading: 'flat', export: true, visibility: true, locked: false,
    render_order: 'default', scope: 0, allow_mirror_modeling: true, vertices, faces, type: 'mesh', uuid: eid});
  nodes.get(p.bone).children.push(eid);
}
const source = {meta: {format_version: '5.0', model_format: 'free', box_uv: false}, name: ID, model_identifier: '', visible_box: [1, 1, 0],
  variable_placeholders: '', variable_placeholder_buttons: [], timeline_setups: [], unhandled_root_fields: {}, resolution: {width: ATLAS, height: ATLAS},
  elements, groups: RIG.map(([b, origin]) => ({name: b, uuid: uuid('group:' + b), export: true, locked: false, scope: 0, selected: false, visibility: true,
    _static: {properties: {}, temp_data: {}}, origin: origin.slice(), rotation: [0, 0, 0], color: 0, children: [], reset: false, shade: true,
    mirror_uv: false, autouv: 0, isOpen: true, primary_selected: false})),
  outliner: RIG.map(([b]) => nodes.get(b)),
  textures: [{name: ID + '.png', relative_path: `textures/${ID}.png`, folder: '', namespace: '', id: '0', group: '', scope: 0,
    width: ATLAS, height: ATLAS, uv_width: ATLAS, uv_height: ATLAS, particle: false, use_as_default: false, layers_enabled: false, sync_to_project: '',
    file_format: 'png', render_mode: 'default', render_sides: 'auto', wrap_mode: 'limited', pbr_channel: 'color', fps: 7, frame_time: 1,
    frame_order_type: 'loop', frame_order: '', frame_interpolate: false, visible: true, internal: true, saved: true, uuid: uuid('texture'),
    source: 'data:image/png;base64,' + PNGS[0].toString('base64')}],
  animations: []};
const geo = {format_version: '1.12.0', 'minecraft:geometry': [{description: {identifier: 'geometry.' + ID, texture_width: ATLAS, texture_height: ATLAS,
  visible_bounds_width: 2, visible_bounds_height: 2, visible_bounds_offset: [0, 0.5, 0]},
  bones: RIG.map(([b, origin]) => ({name: b, pivot: [-origin[0] || 0, origin[1], origin[2]]}))}]};
const sidecar = convert(source, geo, {}, ID + '.bbmodel', 2, null, {});
// bounds: every vertex through the hood's motion (Euler XYZ as the runtime: Rx about the pivot)
const rotX = (q, pv, deg) => { const a = deg * D2R, y = q[1] - pv[1], z = q[2] - pv[2]; return [q[0], pv[1] + y * Math.cos(a) - z * Math.sin(a), pv[2] + y * Math.sin(a) + z * Math.cos(a)]; };
const pts = MESH.flatMap(p => p.bone !== 'hood' ? p.v : Array.from({length: 11}, (_, k) => p.v.map(q => rotX(q, HINGE, HOOD.deg * k / 10))).flat());
const lo3 = [0, 1, 2].map(k => Math.min(...pts.map(q => q[k]))), hi3 = [0, 1, 2].map(k => Math.max(...pts.map(q => q[k]))), mg = 0.25;
const bounds = [(lo3[0] - mg + 8) / 16, (lo3[1] - mg) / 16, (lo3[2] - mg + 8) / 16, (hi3[0] + mg + 8) / 16, (hi3[1] + mg) / 16, (hi3[2] + mg + 8) / 16].map(r6);
const profile = {format_version: 1, geometry: `apocalypse_firstlight:geo/${ID}.geo.json`, texture: `apocalypse_firstlight:textures/block/${ID}.png`,
  origin: [0, 0, 0], scale: [1, 1, 1], facing: 'horizontal', bounds,
  parts: Object.fromEntries(RIG.map(([b, origin]) => [b, {pivot: origin.map(v => r6(v / 16))}])),
  animations: Object.fromEntries(Object.entries(ANIM).map(([ch, a]) => [ch, {duration_ticks: a.ticks, easing: 'ease_in_out', transforms: a.transforms}]))};
const zf = zFightLevels(MESH, new Map());

// selection boxes (block px, facing=north: x 0..16, the wall at z 16), mirrored in GeneratorInletBoxBlock
const cell = q => [q[0] + 8, q[1], q[2] + 8];
const aabb = ps => [0, 1, 2].map(k => r3(Math.min(...ps.map(q => q[k])))).concat([0, 1, 2].map(k => r3(Math.max(...ps.map(q => q[k])))));
const vs = (f, xf = q => q) => MESH.filter(f).flatMap(p => p.v.map(q => cell(xf(q))));
export const SELECTION = {
  box: aabb(vs(p => p.name === 'box' || p.bone === 'hood')),
  connector: aabb(vs(p => p.name.startsWith('body_connector'))),
  hook: aabb(vs(p => p.name.startsWith('hook'))),
  hoodOpen: aabb(vs(p => p.bone === 'hood', q => rotX(q, HINGE, HOOD.deg))),
  coil: aabb(vs(p => p.bone === 'coil')),
};

// ---------------- the plug OBJ (block/l14_30_plug) ----------------
const f6 = v => (+v.toFixed(6)).toString(), SMOOTH = Math.cos(36 * D2R);
const PLUG = PARTS.filter(p => p.bone === 'plug');
for (const p of PLUG) {   // Forge draws a quad as 0-1-2 / 2-3-0: split anything not a triangle / convex quad
  const out = [];
  for (const f of p.f) {
    const P3 = f.ids.map(i => p.v[i]), n = newell(P3);
    const convexQuad = f.ids.length === 4 && [0, 1, 2, 3].every(k => dot(cross(sub(P3[(k + 1) % 4], P3[k]), sub(P3[(k + 2) % 4], P3[(k + 1) % 4])), n) > 1e-14);
    if (f.ids.length === 3 || convexQuad) { out.push(f); continue; }
    assert(f.ids.length === 4, `${f.ids.length}-gon in ${p.name}`);
    const k = [0, 1, 2, 3].find(k => dot(cross(sub(P3[k], P3[(k + 3) % 4]), sub(P3[(k + 1) % 4], P3[k])), n) <= 1e-14) ?? 0;
    const uv = UV.faceUV.get(p).get(f), a = {...f, ids: [f.ids[k], f.ids[(k + 1) % 4], f.ids[(k + 2) % 4]]}, b = {...f, ids: [f.ids[(k + 2) % 4], f.ids[(k + 3) % 4], f.ids[k]]};
    UV.faceUV.get(p).set(a, [uv[k], uv[(k + 1) % 4], uv[(k + 2) % 4]]); UV.faceUV.get(p).set(b, [uv[(k + 2) % 4], uv[(k + 3) % 4], uv[k]]);
    out.push(a, b);
  }
  p.f = out;
}
function cornerNormals(p) {
  const fn = p.f.map(f => newell(f.ids.map(i => p.v[i]))), byV = new Map();
  p.f.forEach((f, k) => f.ids.forEach(i => (byV.get(i) || byV.set(i, []).get(i)).push(k)));
  return p.f.map((f, k) => { const n0 = norm(fn[k]); return f.ids.map(i => norm(byV.get(i).reduce((a, j) => dot(norm(fn[j]), n0) >= SMOOTH ? add(a, fn[j]) : a, [0, 0, 0]))); });
}
function objOf(file) {
  const out = [`# AFL Generator Inlet V1 L14-30P plug, generated by tools/build-generator-inlet-v1.mjs`, `mtllib ${file}.mtl`];
  let vBase = 1, tBase = 1, nBase = 1;
  for (const p of PLUG) {
    out.push(`o ${p.name}`, `usemtl generator_inlet`);
    for (const q of p.v) out.push(`v ${f6(q[0] / 16 + 0.5)} ${f6(q[1] / 16 + 0.5)} ${f6(q[2] / 16 + 0.5)}`);
    const uvs = UV.faceUV.get(p), vt = [], vn = [], fl = [], cn = cornerNormals(p), nIndex = new Map();
    p.f.forEach((f, k) => {
      const uv = uvs.get(f);
      fl.push('f ' + f.ids.map((id, j) => {
        const key = cn[k][j].map(f6).join(' ');
        if (!nIndex.has(key)) { nIndex.set(key, nBase + vn.length); vn.push('vn ' + key); }
        vt.push(`vt ${f6(uv[j][0] / ATLAS)} ${f6(1 - uv[j][1] / ATLAS)}`); return `${vBase + id}/${tBase + vt.length - 1}/${nIndex.get(key)}`;
      }).join(' '));
    });
    out.push(...vt, ...vn, ...fl);
    vBase += p.v.length; tBase += vt.length; nBase += vn.length;
  }
  return out.join('\n') + '\n';
}
const PLUG_FILE = 'l14_30_plug', plugObj = objOf(PLUG_FILE);
const plugMtl = `# AFL Generator Inlet V1 L14-30P plug\nnewmtl generator_inlet\nKd 1 1 1\nmap_Kd apocalypse_firstlight:block/${ID}\n`;
const plugModel = {loader: 'forge:obj', model: `apocalypse_firstlight:models/block/${PLUG_FILE}.obj`, automatic_culling: false, flip_v: true, shade_quads: true,
  emissive_ambient: true, ambientocclusion: false, textures: {particle: `apocalypse_firstlight:block/${ID}`}};

// ---------------- item display (as the panels: the whole box with its coil) ----------------
function itemDisplay() {
  const rot = ([ax, ay]) => q => { const a = ay * D2R, b = ax * D2R, x = q[0] * Math.cos(a) + q[2] * Math.sin(a), z = -q[0] * Math.sin(a) + q[2] * Math.cos(a);
    return [x, q[1] * Math.cos(b) - z * Math.sin(b), q[1] * Math.sin(b) + z * Math.cos(b)]; };
  const all = MESH.flatMap(p => p.v).map(q => [q[0], q[1] - 8, q[2]]), S3 = v => [v, v, v];
  const view = (r, fit, extra = [0, 0, 0]) => { const R = all.map(rot(r)), xs = R.map(q => q[0]), ys = R.map(q => q[1]), zs = R.map(q => q[2]);
    const s = typeof fit === 'number' && fit > 1 ? r3(fit / Math.max(Math.max(...xs) - Math.min(...xs), Math.max(...ys) - Math.min(...ys))) : fit;
    const c = [(Math.max(...xs) + Math.min(...xs)) / 2, (Math.max(...ys) + Math.min(...ys)) / 2, (Math.max(...zs) + Math.min(...zs)) / 2];
    return {rotation: [r[0], r[1], 0], translation: [r3(-s * c[0] + extra[0]), r3(-s * c[1] + extra[1]), r3(-s * c[2] + extra[2])], scale: S3(s)}; };
  return {gui: view([20, 205], 14), ground: view([0, 0], 0.5, [0, 3, 0]), fixed: view([0, 180], 1.0),
    thirdperson_righthand: view([75, 205], 0.6, [0, 2.5, 0]), firstperson_righthand: view([0, 205], 0.7, [0, 1, 0])};
}

// ---------------- outputs ----------------
const bb = path.join(ROOT, 'src/main/blockbench'), assets = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight');
const json = v => JSON.stringify(v, null, 2) + '\n', meshText = serializeCompact(sidecar);
const outputs = [
  [path.join(bb, `${ID}_v1.bbmodel`), JSON.stringify(source)], [path.join(assets, `geo/${ID}.geo.json`), json(geo)],
  [path.join(assets, `meshes/${ID}.aflmesh.json`), meshText], [path.join(assets, `block_mesh_profiles/${ID}.json`), json(profile)],
  [path.join(assets, `models/item/${ID}.json`), json({parent: 'builtin/entity', gui_light: 'side', textures: {particle: `apocalypse_firstlight:block/${ID}`}, display: itemDisplay()})],
  [path.join(assets, `models/block/${ID}.json`), json({loader: 'apocalypse_firstlight:static_mesh', textures: {particle: `apocalypse_firstlight:block/${ID}`}})],
  [path.join(assets, `blockstates/${ID}.json`), json({variants: {'': {model: `apocalypse_firstlight:block/${ID}`}}})],
  [path.join(assets, `models/block/${PLUG_FILE}.obj`), plugObj], [path.join(assets, `models/block/${PLUG_FILE}.mtl`), plugMtl],
  [path.join(assets, `models/block/${PLUG_FILE}.json`), json(plugModel)],
  ...['', '_s', '_n'].flatMap((k, i) => [[path.join(bb, `textures/${ID}_v1${k}.png`), PNGS[i]], [path.join(assets, `textures/block/${ID}${k}.png`), PNGS[i]]]),
];
const tris = ps => ps.reduce((s, p) => s + p.f.reduce((t, f) => t + f.ids.length - 2, 0), 0);
export const stats = {triangles: sidecar.parts.flatMap(p => p.faces).reduce((s, q) => s + q.length - 2, 0), coil: tris(MESH.filter(p => p.bone === 'coil')),
  plug: tris(PLUG), parts: sidecar.parts.length, texelsPerPx: UV.S, islands: UV.islands.length, coplanarOverlaps: zf.unresolved.length, bounds,
  exit: EXIT, exitDir: EXIT_DIR, hinge: HINGE, cordUV: CORD_UV, selection: SELECTION};
const isMain = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  console.log(JSON.stringify(stats, null, 1));
  if (zf.unresolved.length) console.log('COPLANAR', JSON.stringify(zf.unresolved.slice(0, 12)));
  const pi = process.argv.indexOf('--preview');
  if (pi > 0) {
    const dir = process.argv[pi + 1]; fs.mkdirSync(dir, {recursive: true});
    fs.writeFileSync(path.join(dir, `${ID}.aflmesh.json`), meshText); fs.writeFileSync(path.join(dir, `${ID}.profile.json`), JSON.stringify(profile));
    fs.writeFileSync(path.join(dir, `${PLUG_FILE}.obj`), plugObj);
    ['', '_s', '_n'].forEach((k, i) => fs.writeFileSync(path.join(dir, `${ID}${k}.png`), PNGS[i]));
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
