// Power Cable V2: bundled power cable (three black conductors) as baked Forge OBJ pieces + a 32 px LabPBR palette atlas.
// The block's model (client/PowerCableBakedModel) assembles one block from these pieces by its connections:
//   none -> box; one -> end_{cable|plug}_<dir>; two opposite -> half_{cable|plug}_<dir> x2 + band_<axis>;
//   two perpendicular cables -> bend_<a>_<b>; otherwise -> box + arm_{cable|plug}_<dir> per connection.
// "plug" = the side faces a machine's power port: the conductors end in a steel plug seated in the port's socket, its
// mating flange on the block boundary (the AFL power port standard, docs/models/power_cable_v2.md).
//   node tools/build-power-cable-v2.mjs            -> writes pieces (OBJ + model JSON), MTL, atlas, item model, blockstate, source
//   node tools/build-power-cable-v2.mjs --check    -> verifies every output is up to date
//   node tools/build-power-cable-v2.mjs --preview DIR -> writes a combined OBJ of all pieces laid out in a row into DIR
// Frame (px): block centre at the origin; OBJ in block units (x / 16 + 0.5 ...). Conductors keep one cross-section basis
// per axis (X: u=Z v=Y; Y: u=X v=Z; Z: u=X v=Y) at every block face, so neighbouring pieces always meet; bends roll the
// bundle gradually so both ends match it.
import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {png} from './cube-slab-mesh-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
function assert(c, m) { if (!c) throw new Error(m); }
const add = (a, b) => a.map((v, i) => v + b[i]), sub = (a, b) => a.map((v, i) => v - b[i]), mul = (a, k) => a.map(v => v * k);
const dot = (a, b) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
const cross = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
const norm = a => { const l = Math.hypot(...a) || 1; return a.map(v => v / l); };
const rotAbout = (v, n, a) => add(add(mul(v, Math.cos(a)), mul(cross(n, v), Math.sin(a))), mul(n, dot(n, v) * (1 - Math.cos(a))));

// ---------------- dimensions (px) ----------------
export const WIRE = {r: 0.85, sides: 8};
export const D_WIRE = WIRE.r / Math.cos(Math.PI / 6);                 // conductors touch: bundle radius 1.83
export const BAND = {r: 2.05, len: 0.9};
export const BOX = 2.6;                                               // junction box half size (5.2 px; 6.4 px until 2026-10-01)
export const GLAND = {r: 2.2, len: 0.8, at: BOX + 0.4};                // cable gland seated on the box face
export const PLUG = {start: 4.6, body: [4.6, 6.6, 2.15], nut: [5.55, 6.45, 2.5], flange: [6.5, 8.0, 1.9]};   // [from, to, radius]
export const PORT = {plate: 3.0, socket: 1.95};                       // machine side: 6x6 plate, socket radius (docs only)
export const CAP = {from: 1.6, to: -0.6, r: 2.1, cableEnd: 1.2};

const DIRS = {down: [0, -1, 0], up: [0, 1, 0], north: [0, 0, -1], south: [0, 0, 1], west: [-1, 0, 0], east: [1, 0, 0]};
const AXIS_OF = {down: 'y', up: 'y', north: 'z', south: 'z', west: 'x', east: 'x'};
const BASIS = {x: [[0, 0, 1], [0, 1, 0]], y: [[1, 0, 0], [0, 0, 1]], z: [[1, 0, 0], [0, 1, 0]]};
const ANGLES = [0, 1, 2].map(k => Math.PI / 2 + k * 2 * Math.PI / 3);

// ---------------- mesh building ----------------
class Mesh {
  constructor(name) { this.name = name; this.v = []; this.n = []; this.f = []; }
  vtx(p, n) { this.v.push(p); this.n.push(n); return this.v.length - 1; }
  face(ids, mat) { this.f.push({ids, mat}); }
}
// tube along points with frames [u, v] (rings in the u/v plane); radial vertex normals; optional flat caps
function tube(m, pts, frames, r, sides, mat, caps = [false, false], phase = Math.PI / sides) {
  const rings = pts.map((p, k) => Array.from({length: sides}, (_, i) => { const a = phase + 2 * Math.PI * i / sides, [u, v] = frames[k];
    const d = add(mul(u, Math.cos(a)), mul(v, Math.sin(a))); return m.vtx(add(p, mul(d, r)), d); }));
  const straight = pts.length === 2;
  for (let k = 0; k + 1 < rings.length; k++) for (let i = 0; i < sides; i++) { const j = (i + 1) % sides, a = rings[k][i], b = rings[k + 1][i], c = rings[k + 1][j], d = rings[k][j];
    if (straight) m.face([a, b, c, d], mat); else { m.face([a, b, c], mat); m.face([a, c, d], mat); } }
  caps.forEach((cap, e) => { if (!cap) return; const k = e ? rings.length - 1 : 0, t = norm(sub(pts[e ? k : 1], pts[e ? k - 1 : 0])), nn = e ? t : mul(t, -1);
    const ctr = m.vtx(pts[k], nn), ring = rings[k].map((id, i) => m.vtx(m.v[id], nn));
    for (let i = 0; i < sides; i++) { const j = (i + 1) % sides; m.face(e ? [ctr, ring[i], ring[j]] : [ctr, ring[j], ring[i]], mat); } });
}
const frameOf = axis => BASIS[axis];
// straight bundle along an axis between two points
function bundleStraight(m, a, b, axis, mat = 'jacket', caps = [false, false]) {
  const f = frameOf(axis);
  for (const ang of ANGLES) { const o = add(mul(f[0], D_WIRE * Math.cos(ang)), mul(f[1], D_WIRE * Math.sin(ang)));
    tube(m, [add(a, o), add(b, o)], [f, f], WIRE.r, WIRE.sides, mat, caps); }
}
// ring / collar coaxial with an axis direction t at centre c
function collar(m, c, t, r, len, mat, sides = 12) {
  const axis = Math.abs(t[0]) > 0.99 ? 'x' : Math.abs(t[1]) > 0.99 ? 'y' : Math.abs(t[2]) > 0.99 ? 'z' : null;
  const f = axis ? frameOf(axis) : (() => { const u = norm(cross(t, Math.abs(t[1]) < 0.9 ? [0, 1, 0] : [1, 0, 0])); return [u, cross(t, u)]; })();
  tube(m, [sub(c, mul(t, len / 2)), add(c, mul(t, len / 2))], [f, f], r, sides, mat, [true, true]);
}
function boxMesh(m, c, h, mat) {
  const P = k => add(c, [k & 1 ? h[0] : -h[0], k & 2 ? h[1] : -h[1], k & 4 ? h[2] : -h[2]]);
  for (const [q, n] of [[[0, 2, 3, 1], [0, 0, -1]], [[4, 5, 7, 6], [0, 0, 1]], [[0, 1, 5, 4], [0, -1, 0]], [[2, 6, 7, 3], [0, 1, 0]], [[0, 4, 6, 2], [-1, 0, 0]], [[1, 3, 7, 5], [1, 0, 0]]])
    m.face(q.map(i => m.vtx(P(i), n)), mat);
}
// chamfered box: the six faces inset by c plus the 12 edge strips and 8 corner triangles
function chamferBox(m, h, c, mat) {
  const s = [-1, 1], faces = [];
  for (let ax = 0; ax < 3; ax++) for (const sg of s) {   // main faces
    const n = [0, 0, 0]; n[ax] = sg; const [a1, a2] = [0, 1, 2].filter(i => i !== ax);
    const P = (u, v) => { const p = [0, 0, 0]; p[ax] = sg * h; p[a1] = u * (h - c); p[a2] = v * (h - c); return p; };
    let q = [P(-1, -1), P(1, -1), P(1, 1), P(-1, 1)];
    if (dot(cross(sub(q[1], q[0]), sub(q[2], q[0])), n) < 0) q = q.reverse();
    m.face(q.map(p => m.vtx(p, n)), mat);
  }
  const corner = (sx, sy, sz, ax) => { const p = [sx * (h - c), sy * (h - c), sz * (h - c)]; p[ax] = [sx, sy, sz][ax] * h; return p; };
  for (let a = 0; a < 3; a++) for (let b = a + 1; b < 3; b++) for (const sa of s) for (const sb of s) {   // edge strips
    const e = 3 - a - b, n = norm((() => { const v = [0, 0, 0]; v[a] = sa; v[b] = sb; return v; })());
    const pt = (se, onA) => { const sv = [0, 0, 0]; sv[a] = sa; sv[b] = sb; sv[e] = se; return corner(sv[0], sv[1], sv[2], onA ? a : b); };
    let q = [pt(-1, true), pt(1, true), pt(1, false), pt(-1, false)];
    if (dot(cross(sub(q[1], q[0]), sub(q[2], q[0])), n) < 0) q = q.reverse();
    m.face(q.map(p => m.vtx(p, n)), mat);
  }
  for (const sx of s) for (const sy of s) for (const sz of s) {   // corner triangles
    const n = norm([sx, sy, sz]); let t = [corner(sx, sy, sz, 0), corner(sx, sy, sz, 1), corner(sx, sy, sz, 2)];
    if (dot(cross(sub(t[1], t[0]), sub(t[2], t[0])), n) < 0) t = t.reverse();
    m.face(t.map(p => m.vtx(p, n)), mat);
  }
}

// ---------------- pieces ----------------
const plug = (m, dir) => { const t = DIRS[dir];
  collar(m, mul(t, (PLUG.body[0] + PLUG.body[1]) / 2), t, PLUG.body[2], PLUG.body[1] - PLUG.body[0], 'plug');
  collar(m, mul(t, (PLUG.nut[0] + PLUG.nut[1]) / 2), t, PLUG.nut[2], PLUG.nut[1] - PLUG.nut[0], 'nut', 6);
  collar(m, mul(t, (PLUG.flange[0] + PLUG.flange[1]) / 2), t, PLUG.flange[2], PLUG.flange[1] - PLUG.flange[0], 'plug'); };
const PIECES = [];
const piece = (name, build) => { const m = new Mesh(name); build(m); PIECES.push(m); };
for (const dir of Object.keys(DIRS)) {
  const t = DIRS[dir], axis = AXIS_OF[dir];
  piece(`half_cable_${dir}`, m => bundleStraight(m, [0, 0, 0], mul(t, 8), axis));
  piece(`half_plug_${dir}`, m => { bundleStraight(m, [0, 0, 0], mul(t, PLUG.start + 0.3), axis); plug(m, dir); });
  piece(`arm_cable_${dir}`, m => { bundleStraight(m, mul(t, BOX - 0.2), mul(t, 8), axis); collar(m, mul(t, GLAND.at), t, GLAND.r, GLAND.len, 'gland'); });
  piece(`arm_plug_${dir}`, m => { bundleStraight(m, mul(t, BOX - 0.2), mul(t, PLUG.start + 0.3), axis); collar(m, mul(t, GLAND.at), t, GLAND.r, GLAND.len, 'gland'); plug(m, dir); });
  piece(`end_cable_${dir}`, m => { bundleStraight(m, mul(t, CAP.cableEnd), mul(t, 8), axis); collar(m, mul(t, (CAP.from + CAP.to) / 2), t, CAP.r, CAP.from - CAP.to, 'cap'); });
  piece(`end_plug_${dir}`, m => { bundleStraight(m, mul(t, CAP.cableEnd), mul(t, PLUG.start + 0.3), axis); plug(m, dir); collar(m, mul(t, (CAP.from + CAP.to) / 2), t, CAP.r, CAP.from - CAP.to, 'cap'); });
}
for (const axis of ['x', 'y', 'z']) piece(`band_${axis}`, m => collar(m, [0, 0, 0], axis === 'x' ? [1, 0, 0] : axis === 'y' ? [0, 1, 0] : [0, 0, 1], BAND.r, BAND.len, 'band'));
piece('box', m => { chamferBox(m, BOX, 0.3, 'box'); boxMesh(m, [0, BOX + 0.06, 0], [BOX - 0.55, 0.08, BOX - 0.55], 'band'); });
// bends: quarter arc between face centres a and b (radius 8 about the shared block edge), the bundle rolled linearly so
// it matches the per-axis basis at both faces (a three-conductor bundle is symmetric under 120 degree turns)
const NAMES = Object.keys(DIRS);
for (let i = 0; i < NAMES.length; i++) for (let j = i + 1; j < NAMES.length; j++) {
  const a = NAMES[i], b = NAMES[j], A = DIRS[a], B = DIRS[b]; if (Math.abs(dot(A, B)) > 0.5) continue;
  piece(`bend_${a}_${b}`, m => {
    const c = add(mul(A, 8), mul(B, 8)), N = 8, T0 = mul(A, -1);
    let n = norm(cross(T0, B)); if (dot(rotAbout(T0, n, Math.PI / 2), B) < 0.99) n = mul(n, -1);
    const [u0, v0] = frameOf(AXIS_OF[a]), [u1, v1] = frameOf(AXIS_OF[b]);
    const target = ANGLES.map(g => add(mul(u1, Math.cos(g)), mul(v1, Math.sin(g))));
    const endPts = roll => ANGLES.map(g => { const u = rotAbout(u0, n, Math.PI / 2), v = rotAbout(v0, n, Math.PI / 2);
      return rotAbout(add(mul(u, Math.cos(g)), mul(v, Math.sin(g))), B, roll); });
    let best = 0, bestErr = Infinity;
    for (let r = -Math.PI / 3; r <= Math.PI / 3 + 1e-9; r += Math.PI / 1800) {
      const e = Math.max(...endPts(r).map(p => Math.min(...target.map(q => Math.hypot(...sub(p, q))))));
      if (e < bestErr) { bestErr = e; best = r; }
    }
    assert(bestErr < 1e-3, `bend ${a}-${b} cannot meet the face basis (${bestErr})`);
    for (const g of ANGLES) {
      const pts = [], frames = [];
      for (let k = 0; k <= N; k++) {
        const t = k / N * Math.PI / 2, T = rotAbout(T0, n, t), u = rotAbout(u0, n, t), v = rotAbout(v0, n, t), roll = best * k / N;
        const ur = rotAbout(u, T, roll), vr = rotAbout(v, T, roll);
        const centre = add(c, add(mul(B, -8 * Math.cos(t)), mul(A, -8 * Math.sin(t))));
        pts.push(add(centre, mul(add(mul(ur, Math.cos(g)), mul(vr, Math.sin(g))), D_WIRE)));
        frames.push([ur, vr]);
      }
      tube(m, pts, frames, WIRE.r, WIRE.sides, 'jacket');
    }
    const tm = Math.PI / 4, Tm = rotAbout(T0, n, tm);
    collar(m, add(c, add(mul(B, -8 * Math.cos(tm)), mul(A, -8 * Math.sin(tm)))), Tm, BAND.r, BAND.len, 'band');
  });
}
// pipe clamps: a straight run next to a solid face (floor, wall, ceiling) gets a clamp instead of its band: the strap
// around the bundle, a leg down to that face and a foot plate with two screws (cosmetic; the run's shape is unchanged)
export const CLAMP = {strap: 2.12, len: 1.0, leg: [2.0, 7.6, 0.5, 0.45], foot: [7.6, 8.0, 1.0, 1.6], screw: {r: 0.38, h: 0.22, w: 1.05}};
const AXES = {x: [1, 0, 0], y: [0, 1, 0], z: [0, 0, 1]};
for (const [axis, A] of Object.entries(AXES)) for (const side of NAMES) {
  const S = DIRS[side]; if (Math.abs(dot(A, S)) > 0.5) continue;
  const W = cross(A, S).map(Math.abs);
  const slab = (s0, s1, ha, hw, mat) => { const c = mul(S, (s0 + s1) / 2), h = add(add(mul(S.map(Math.abs), (s1 - s0) / 2), mul(A, ha)), mul(W, hw)); boxMesh(m_, c, h, mat); };
  let m_;
  piece(`clamp_${axis}_${side}`, m => { m_ = m;
    collar(m, [0, 0, 0], A, CLAMP.strap, CLAMP.len, 'band');
    slab(CLAMP.leg[0], CLAMP.leg[1], CLAMP.leg[2], CLAMP.leg[3], 'band');
    slab(CLAMP.foot[0], CLAMP.foot[1], CLAMP.foot[2], CLAMP.foot[3], 'band');
    for (const s of [-1, 1]) collar(m, add(mul(S, CLAMP.foot[0] - CLAMP.screw.h / 2), mul(W, s * CLAMP.screw.w)), S, CLAMP.screw.r, CLAMP.screw.h, 'nut', 6);
  });
}
// a straight run with a band for the item (along X); its conductor ends are capped, nothing joins it there
piece('item', m => { bundleStraight(m, [-8, 0, 0], [8, 0, 0], 'x', 'jacket', [true, true]); collar(m, [0, 0, 0], [1, 0, 0], BAND.r, BAND.len, 'band'); });

// Minecraft culls back faces of solid block quads: every face is wound counter-clockwise seen from outside, i.e. its
// geometric normal agrees with its vertex normals (radial for the conductors, the face normal elsewhere)
export function fixWinding(m) {
  let flipped = 0;
  for (const f of m.f) {
    const P = f.ids.map(i => m.v[i]); let g = [0, 0, 0];
    for (let i = 0; i < P.length; i++) { const a = P[i], b = P[(i + 1) % P.length]; g = add(g, [(a[1] - b[1]) * (a[2] + b[2]), (a[2] - b[2]) * (a[0] + b[0]), (a[0] - b[0]) * (a[1] + b[1])]); }
    const ref = f.ids.reduce((s, i) => add(s, m.n[i]), [0, 0, 0]);
    if (dot(g, ref) < 0) { f.ids = f.ids.slice().reverse(); flipped++; }
  }
  return flipped;
}
export const FLIPPED = PIECES.reduce((s, m) => s + fixWinding(m), 0);

// ---------------- palette atlas (LabPBR) ----------------
export const MATS = {   // base, smoothness, F0
  jacket: {c: [36, 37, 40], sm: 72, f0: 20},       // black rubber insulation
  band:   {c: [146, 149, 154], sm: 120, f0: 30},    // galvanised steel band / clamp / box lid, read as coated (dielectric)
  box:    {c: [84, 88, 94], sm: 100, f0: 20},       // painted steel junction box
  gland:  {c: [58, 60, 64], sm: 90, f0: 20},        // cable gland (dark nylon)
  plug:   {c: [150, 153, 158], sm: 128, f0: 30},    // plug body and flange, coated
  nut:    {c: [118, 121, 126], sm: 116, f0: 30},    // locking nut, coated
  cap:    {c: [28, 28, 30], sm: 50, f0: 20},        // rubber end cap
};
const MAT_NAMES = Object.keys(MATS), CELL = 4, AT = 32;
const swatch = mat => { const i = MAT_NAMES.indexOf(mat); assert(i >= 0, 'material ' + mat); return [((i % 8) * CELL + CELL / 2) / AT, (Math.floor(i / 8) * CELL + CELL / 2) / AT]; };
const maps = [Buffer.alloc(AT * AT * 4), Buffer.alloc(AT * AT * 4), Buffer.alloc(AT * AT * 4)];
for (let y = 0; y < AT; y++) for (let x = 0; x < AT; x++) {
  const i = Math.floor(y / CELL) * 8 + Math.floor(x / CELL), m = MATS[MAT_NAMES[Math.min(i, MAT_NAMES.length - 1)]], k = (y * AT + x) * 4;
  maps[0].set([...m.c, 255], k); maps[1].set([m.sm, m.f0, 0, 255], k); maps[2].set([128, 128, 255, 255], k);
}
const PNGS = maps.map(b => png(b, AT, AT));

// ---------------- export ----------------
const ID = 'power_cable', f6 = v => (+v.toFixed(6)).toString();
function objOf(m) {
  const out = [`# AFL Power Cable V2 piece ${m.name}, generated by tools/build-power-cable-v2.mjs`, `mtllib ${ID}.mtl`, `o ${m.name}`, 'usemtl cable'];
  for (const p of m.v) out.push(`v ${f6(p[0] / 16 + 0.5)} ${f6(p[1] / 16 + 0.5)} ${f6(p[2] / 16 + 0.5)}`);
  for (const n of m.n) out.push(`vn ${f6(n[0])} ${f6(n[1])} ${f6(n[2])}`);
  MAT_NAMES.forEach(mat => { const [u, v] = swatch(mat); out.push(`vt ${f6(u)} ${f6(1 - v)}`); });
  for (const f of m.f) { assert(f.ids.length === 3 || f.ids.length === 4, 'face size');
    const t = MAT_NAMES.indexOf(f.mat) + 1; out.push('f ' + f.ids.map(id => `${id + 1}/${t}/${id + 1}`).join(' ')); }
  return out.join('\n') + '\n';
}
const modelJson = name => JSON.stringify({loader: 'forge:obj', model: `apocalypse_firstlight:models/block/${ID}/${name}.obj`, automatic_culling: false,
  flip_v: true, shade_quads: true, ambientocclusion: false, textures: {particle: `apocalypse_firstlight:block/${ID}`}}, null, 2) + '\n';
const mtl = `# AFL Power Cable V2\nnewmtl cable\nKd 1 1 1\nmap_Kd apocalypse_firstlight:block/${ID}\n`;
const itemModel = {parent: `apocalypse_firstlight:block/${ID}/item`, display: {
  gui: {rotation: [30, 225, 0], translation: [0, 0, 0], scale: [0.9, 0.9, 0.9]},
  ground: {rotation: [0, 0, 0], translation: [0, 3, 0], scale: [0.5, 0.5, 0.5]},
  fixed: {rotation: [0, 90, 0], translation: [0, 0, 0], scale: [0.8, 0.8, 0.8]},
  thirdperson_righthand: {rotation: [75, 45, 0], translation: [0, 2.5, 0], scale: [0.5, 0.5, 0.5]},
  firstperson_righthand: {rotation: [0, 45, 0], translation: [0, 0, 0], scale: [0.6, 0.6, 0.6]}}};
// every state maps to the box model; client/PowerCableModels swaps in the assembled model for all of them
const blockstate = {variants: {'': {model: `apocalypse_firstlight:block/${ID}/box`}}};

const uuid = s => { const h = createHash('sha256').update('afl-power-cable-v2:' + s).digest('hex'); return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-8${h.slice(17, 20)}-${h.slice(20, 32)}`; };
const r12 = v => +v.toFixed(12) || 0;
const source = (() => {   // editable source: one hidden-by-default group per piece (only 'item' visible), palette texture
  const groups = [], outliner = [], elements = [];
  for (const m of PIECES) {
    const key = i => i.toString(36).padStart(4, '0'), vertices = {}, faces = {};
    m.v.forEach((q, i) => { vertices[key(i)] = q.map(r12); });
    m.f.forEach((f, fi) => { const s = swatch(f.mat).map(v => v * AT); faces['f' + key(fi)] = {uv: Object.fromEntries(f.ids.map(id => [key(id), s])), vertices: f.ids.map(key), texture: 0}; });
    const id = uuid('mesh:' + m.name), gid = uuid('group:' + m.name), visible = m.name === 'item';
    elements.push({name: m.name, color: 0, origin: [0, 0, 0], rotation: [0, 0, 0], shading: 'smooth', export: true, visibility: visible, locked: false,
      render_order: 'default', scope: 0, allow_mirror_modeling: true, vertices, faces, type: 'mesh', uuid: id});
    groups.push({name: m.name, uuid: gid, export: true, locked: false, scope: 0, selected: false, visibility: visible, _static: {properties: {}, temp_data: {}},
      origin: [0, 0, 0], rotation: [0, 0, 0], color: 0, children: [], reset: false, shade: true, mirror_uv: false, autouv: 0, isOpen: false, primary_selected: false});
    outliner.push({uuid: gid, isOpen: false, children: [id]});
  }
  return {meta: {format_version: '5.0', model_format: 'free', box_uv: false}, name: ID, model_identifier: '', visible_box: [1, 1, 0],
    variable_placeholders: '', variable_placeholder_buttons: [], timeline_setups: [], unhandled_root_fields: {}, resolution: {width: AT, height: AT},
    elements, groups, outliner,
    textures: [{name: ID + '.png', relative_path: `textures/${ID}.png`, folder: '', namespace: '', id: '0', group: '', scope: 0,
      width: AT, height: AT, uv_width: AT, uv_height: AT, particle: false, use_as_default: false, layers_enabled: false, sync_to_project: '',
      file_format: 'png', render_mode: 'default', render_sides: 'auto', wrap_mode: 'limited', pbr_channel: 'color', fps: 7, frame_time: 1,
      frame_order_type: 'loop', frame_order: '', frame_interpolate: false, visible: true, internal: true, saved: true, uuid: uuid('texture'),
      source: 'data:image/png;base64,' + PNGS[0].toString('base64')}],
    animations: []};
})();

const assets = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight'), bb = path.join(ROOT, 'src/main/blockbench');
const outputs = [
  ...PIECES.flatMap(m => [[path.join(assets, `models/block/${ID}/${m.name}.obj`), objOf(m)], [path.join(assets, `models/block/${ID}/${m.name}.json`), modelJson(m.name)]]),
  [path.join(assets, `models/block/${ID}/${ID}.mtl`), mtl],
  [path.join(assets, `models/item/${ID}.json`), JSON.stringify(itemModel, null, 2) + '\n'],
  [path.join(assets, `blockstates/${ID}.json`), JSON.stringify(blockstate, null, 2) + '\n'],
  [path.join(bb, `${ID}.bbmodel`), JSON.stringify(source)],
  ...['', '_s', '_n'].flatMap((k, i) => [[path.join(assets, `textures/block/${ID}${k}.png`), PNGS[i]], [path.join(bb, `textures/${ID}${k}.png`), PNGS[i]]]),
];
export const PIECE_NAMES = PIECES.map(m => m.name);
export const PIECE_MESHES = PIECES;
const tris = m => m.f.reduce((s, f) => s + f.ids.length - 2, 0);
export const stats = {pieces: PIECES.length, windingFixed: FLIPPED, triangles: Object.fromEntries(['half_cable_north', 'half_plug_north', 'band_z', 'bend_north_east', 'box', 'arm_cable_north', 'arm_plug_north', 'end_cable_north', 'end_plug_north', 'item'].map(n => [n, tris(PIECES.find(m => m.name === n))]))};

const isMain = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  console.log(JSON.stringify(stats));
  const pi = process.argv.indexOf('--preview');
  if (pi > 0) {   // all pieces in a row, 1.5 blocks apart, as one OBJ for an offline viewer
    const dir = process.argv[pi + 1]; fs.mkdirSync(dir, {recursive: true});
    const out = [], uvs = []; let base = 0;
    MAT_NAMES.forEach(mat => { const [u, v] = swatch(mat); uvs.push(`vt ${u} ${1 - v}`); });
    out.push(...uvs);
    PIECES.forEach((m, k) => { const off = [(k % 10) * 24, 0, Math.floor(k / 10) * 24];
      for (const p of m.v) out.push(`v ${f6((p[0] + off[0]) / 16 + 0.5)} ${f6((p[1] + off[1]) / 16 + 0.5)} ${f6((p[2] + off[2]) / 16 + 0.5)}`);
      for (const f of m.f) out.push('f ' + f.ids.map(id => `${base + id + 1}/${MAT_NAMES.indexOf(f.mat) + 1}`).join(' '));
      base += m.v.length; });
    fs.writeFileSync(path.join(dir, `${ID}.obj`), out.join('\n')); fs.writeFileSync(path.join(dir, `${ID}.png`), PNGS[0]);
    console.log('preview written to ' + dir);
  } else if (process.argv.includes('--check')) {
    for (const [file, data] of outputs) { const cur = fs.existsSync(file) ? fs.readFileSync(file) : null; if (!cur || !cur.equals(Buffer.isBuffer(data) ? data : Buffer.from(data))) throw new Error('stale ' + path.relative(ROOT, file)); }
    console.log('CHECK OK');
  } else {
    for (const [file] of outputs) fs.mkdirSync(path.dirname(file), {recursive: true});
    for (const [file, data] of outputs) fs.writeFileSync(file, data);
    console.log(`wrote ${outputs.length} files`);
  }
}
