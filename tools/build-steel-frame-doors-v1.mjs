// Steel-frame doors V1 (docs/models/steel_frame_doors_v1.md): the remade Steel Door (`steel_door`, the A1 rear doors,
// the bunker) and the new Commercial Wood Door (`commercial_wood_door`), as Pure Mesh + 1024 LabPBR atlases, rendered by
// the AFL Animated Block Mesh Runtime (bones 'frame', 'leaf'; channel 'open') and in inventories by
// AflStaticMeshItemRenderer. User 2026-10-07: concept option B, steel outside (charcoal hollow-metal frame and leaf),
// commercial wood inside (the same charcoal frame, a light veneer leaf); the wood door has three looks on one block
// (STYLE: plain / restroom / vision), shown through part visibility: child bones 'leaf_plain', 'leaf_lite', 'restroom'.
// Both stay vanilla doors (FACING = the way the placer looked, HINGE as seen by the placer, two halves). The leaf hangs at
// the face toward the placer:
//   steel door: swings out toward the placer, out of its cell, as an exit door does when placed from outside;
//   wood door:  swings away from the placer, inside its own cell, as a vanilla door does (flush with the wall face).
// One rig per hinge side (as the checkout counter gate): '<id>_right' is the authored one (hinge at -X), '<id>_left' its
// mirror; both share the door's atlas.
//   node tools/build-steel-frame-doors-v1.mjs                 -> writes sources, geo / sidecars / profiles / maps / models / blockstates
//   node tools/build-steel-frame-doors-v1.mjs --check         -> verifies every output is up to date
//   node tools/build-steel-frame-doors-v1.mjs --preview DIR   -> writes only geo / sidecars / maps into DIR
// Model space (px): the face toward the placer at z -8 (the runtime's front, -Z; the block entity turns it with
// FACING.getOpposite()), the cell x -8..8, z -8..8, the door 0..32 high (two blocks); origin = the bottom centre of the
// lower cell.
import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {convert, serializeCompact} from './export-afl-mesh.mjs';
import {Part, AX, extrude, area2, unwrap, paint, png, readPng, zFightLevels} from './cube-slab-mesh-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
function assert(c, m) { if (!c) throw new Error(m); }
const orient = (L, ccw) => (area2(L) > 0) === ccw ? L : L.slice().reverse();
const rect = (u0, v0, u1, v1) => [[u0, v0], [u1, v0], [u1, v1], [u0, v1]];
const r6 = v => +v.toFixed(6) || 0, r3 = v => +v.toFixed(3) || 0, r12 = v => +v.toFixed(12) || 0;

// ---------------- dimensions (px; 1 px = 6.25 cm) ----------------
// A 16-gauge hollow-metal frame wrapping the 1 m wall: a 0.4 px lining on the reveal, 1 px face casings 0.5 px proud on
// both faces (the wood door's front casing is 1.5 px wide: it is the stop the leaf closes against), a 0.6 px steel stop
// behind the steel leaf. Leaves 1 3/4" (0.71 px) thick, 0.87 x 1.92 m, 2 cm undercut. Hardware at the usual heights:
// lever 0.97 m, deadbolt / indicator 1.13 m, viewer 1.5 m, plaque centre 1.52 m; 2 3/4" backset.
export const D = {
  lining: 0.4, casingZ: 0.5, liningIn: 7.6,
  casingIn: {steel: 7.0, wood: 6.5}, headIn: {steel: 31.0, wood: 30.5}, backIn: 7.0, backHead: 31.0,
  stop: {x: 7.0, z: [-7.2, -6.6], y0: 0.2, head: 31.0},
  leaf: {x: 6.95, y: [0.3, 30.95], z: [-7.95, -7.24]},
  pivot: {steel: [-7.0, -8.4], wood: [-7.0, -7.0]},
  latch: {x: 6.95 - 1.12, y: 15.5}, deadbolt: 18.1, viewer: 24.0,
  lite: {x: [3.4, 5.0], y: [17.9, 28.0], bead: 0.2},
  plaque: {c: [0, 24.3], s: 3.2},
  kick: 4.06, threshold: [-8, -5.6], drip: 1.35,
  knuckles: [[3.6, 5.4], [14.7, 16.5], [25.9, 27.7]], knuckleR: 0.22,
};
export const OPEN_TICKS = 8;
// base rig (hinge at -X): the steel leaf swings toward -Z (out, +90), the wood leaf toward +Z (into the cell, -90)
export const OPEN_DEGREES = {steel: 90, wood: -90};

// ---------------- primitives ----------------
function slab(part, ax, a, b, c = 0) {
  const [ku, kv, ka] = ax === 'x' ? [2, 1, 0] : ax === 'y' ? [2, 0, 1] : [0, 1, 2];
  extrude(part, ax, {outer: orient(rect(a[ku], a[kv], b[ku], b[kv]), true), holes: []}, a[ka], b[ka], c);
}
const poly = (part, ax, pts, a0, a1, c = 0, holes = []) => extrude(part, ax, {outer: orient(pts, true), holes: holes.map(h => orient(h, false))}, a0, a1, c);
function cyl(part, ax, cu, cv, r, a0, a1, seg) {
  const A = AX[ax], ang = i => Math.PI / seg + 2 * Math.PI * i / seg;
  const rg = a => Array.from({length: seg}, (_, i) => part.vtx(A.to3(cu + r * Math.cos(ang(i)), cv + r * Math.sin(ang(i)), a)));
  const r0 = rg(a0), r1 = rg(a1);
  for (let i = 0; i < seg; i++) { const j = (i + 1) % seg, m = (ang(i) + ang(j)) / 2;
    part.face([r0[i], r0[j], r1[j], r1[i]], A.to3(Math.cos(m), Math.sin(m), 0), 'side'); }
  for (const [ring_, a, s] of [[r0, a0, -1], [r1, a1, 1]]) { const c = part.vtx(A.to3(cu, cv, a));
    for (let i = 0; i < seg; i++) part.face([c, ring_[i], ring_[(i + 1) % seg]], A.n.map(v => v * s), 'cap'); }
}
const quad = (part, pts, n) => part.face(pts.map(q => part.vtx(q)), n, 'cap');
// a U outline open at the floor: outer x +-o up to height h, inner x +-i up to height hi
const U = (o, h, i, hi, y0 = 0) => [[-o, y0], [-i, y0], [-i, hi], [i, hi], [i, y0], [o, y0], [o, h], [-o, h]];

// ---------------- materials ----------------
const hash = (a, b, c) => { let h = Math.imul(a | 0, 374761393) ^ Math.imul(b | 0, 668265263) ^ Math.imul(c | 0, 2147483647); h = Math.imul(h ^ (h >>> 13), 1274126177); return ((h ^ (h >>> 16)) >>> 0) / 4294967295; };
function vn(x, y, z) {
  const xi = Math.floor(x), yi = Math.floor(y), zi = Math.floor(z), s = t => t * t * (3 - 2 * t), l = (a, b, t) => a + (b - a) * t;
  const sx = s(x - xi), sy = s(y - yi), sz = s(z - zi), h = (a, b, c) => hash(a, b, c * 7919 + 211);
  return l(l(l(h(xi, yi, zi), h(xi + 1, yi, zi), sx), l(h(xi, yi + 1, zi), h(xi + 1, yi + 1, zi), sx), sy),
    l(l(h(xi, yi, zi + 1), h(xi + 1, yi, zi + 1), sx), l(h(xi, yi + 1, zi + 1), h(xi + 1, yi + 1, zi + 1), sx), sy), sz);
}
const fbm = (x, y, z) => 0.6 * vn(x, y, z) + 0.3 * vn(x * 2.03 + 7.1, y * 2.03 + 3.3, z * 2.03 + 1.7) + 0.1 * vn(x * 4.1 + 2.9, y * 4.1 + 8.1, z * 4.1 + 5.3);
const tone = (c, k) => c.map(v => v * k), mix = (a, b, t) => a.map((v, i) => v + (b[i] - v) * t);
const sm = (a, b, x) => { const t = Math.max(0, Math.min(1, (x - a) / (b - a))); return t * t * (3 - 2 * t); };
// charcoal hollow-metal paint (as the eyebrow canopy's coat [60,62,65] sm 96 F0 16): faint low-frequency clouding (+-1.5 %)
const PAINT = [60, 62, 65];
const paintC = p => tone(PAINT, 1 + 0.03 * (fbm(p[0] * 0.22, p[1] * 0.22, p[2] * 0.22) - 0.5));
// light plain-sliced maple veneer under a satin lacquer: grain along the height, soft cathedral figure, low frequency only
const WOOD_LIGHT = [206, 181, 143], WOOD_DARK = [176, 145, 104];
const woodC = p => {
  const across = p[0] + p[2], fig = vn(across * 0.42 + 3, p[1] * 0.05, 1.3);
  const arch = 1.7 * Math.sin(p[1] * 0.075 + across * 0.12) * sm(0.2, 0.8, vn(across * 0.18, p[1] * 0.02, 5));
  const lines = 0.5 + 0.5 * Math.sin(across * 3.3 + arch * 2.2 + 3.0 * vn(across * 0.6, p[1] * 0.07, 9));
  return mix(WOOD_LIGHT, WOOD_DARK, 0.1 + 0.34 * lines * (0.6 + 0.4 * fig) + 0.28 * fig);
};
// brushed stainless kick plate: horizontal streaks (+-4 %)
const kickC = p => tone([160, 163, 167], 1 + 0.08 * (fbm(p[0] * 0.05, p[1] * 5.0, p[2] * 5.0) - 0.5));
// all-gender restroom plaque (single-user restrooms): two white figures and a divider on blue-grey, anti-aliased edges
const PLQ_BG = [43, 58, 74], PLQ_FG = [236, 235, 230];
function pictogram(u, v) {   // signed distance (plaque units, < 0 inside) to the figures
  const box = (x0, y0, x1, y1) => { const dx = Math.max(x0 - u, u - x1), dy = Math.max(y0 - v, v - y1); return dx > 0 && dy > 0 ? Math.hypot(dx, dy) : Math.max(dx, dy); };
  const circ = (cx, cy, r) => Math.hypot(u - cx, v - cy) - r;
  const trap = (cx, y0, y1, w0, w1) => { const t = (v - y0) / (y1 - y0), w = w0 + (w1 - w0) * Math.max(0, Math.min(1, t)); return Math.max(Math.abs(u - cx) - w / 2, y0 - v, v - y1); };
  const man = 0.3, woman = 0.7;
  return Math.min(circ(man, 0.765, 0.068), box(man - 0.085, 0.43, man + 0.085, 0.66), box(man - 0.085, 0.15, man - 0.012, 0.45), box(man + 0.012, 0.15, man + 0.085, 0.45),
    circ(woman, 0.765, 0.068), trap(woman, 0.37, 0.66, 0.30, 0.13), box(woman - 0.062, 0.15, woman - 0.012, 0.38), box(woman + 0.012, 0.15, woman + 0.062, 0.38),
    box(0.493, 0.12, 0.507, 0.88));
}
const plaqueC = (p, n) => {
  if (n[2] > -0.5) return PLQ_BG;   // only the front face carries the figures
  const u = (D.plaque.c[0] - p[0]) / D.plaque.s + 0.5, v = (p[1] - D.plaque.c[1]) / D.plaque.s + 0.5;
  return mix(PLQ_FG, PLQ_BG, sm(-0.009, 0.009, pictogram(u, v)));
};
const MATS = {
  paint:  {c: paintC, hl: 8, sm: 100, se: 118, f0: 16},
  metal:  {c: [168, 171, 175], hl: 14, sm: 150, se: 170, f0: 255},   // satin stainless levers, hinges, viewer, beads (as the glass door)
  kick:   {c: kickC, hl: 8, sm: 116, se: 134, f0: 255},     // satin: a large bare-metal plate glares under shaders when glossy
  alu:    {c: [150, 153, 156], hl: 4, sm: 110, se: 120, f0: 232},      // mill-finish aluminium threshold and drip cap
  closer: {c: [152, 154, 158], hl: 8, sm: 104, se: 120, f0: 24},       // aluminium-colour powder coat
  wood:   {c: woodC, hl: 8, sm: 112, se: 130, f0: 14},
  glass:  {c: [176, 204, 204], hl: 0, sm: 240, se: 240, f0: 10},       // as the Storefront Glazing lite; alpha below
  gasket: {c: [22, 23, 25], hl: 0, sm: 50, se: 50, f0: 14},
  green:  {c: [52, 158, 86], hl: 0, sm: 150, se: 150, f0: 14},         // the indicator's VACANT window
  plaque: {c: plaqueC, hl: 0, sm: 92, se: 92, f0: 14},
};
export const GLASS_ALPHA = 46;

// ---------------- geometry ----------------
function build(kind) {
  const PARTS = [], P = (name, bone, mat) => { const p = new Part(name, bone, mat); PARTS.push(p); return p; };
  const steel = kind === 'steel', L = D.leaf, [zf, zb] = L.z, ci = D.casingIn[kind], hi = D.headIn[kind];
  // frame: lining U through the wall, front and back casings, the steel stop, threshold and drip cap (steel only)
  const f = P('frame_lining', 'frame', 'paint');
  poly(f, 'z', U(8, 32, D.liningIn, 32 - D.lining), -8, 8, 0);
  poly(P('frame_casing_front', 'frame', 'paint'), 'z', U(8, 32, ci, hi), -8 - D.casingZ, -8, 0.06);
  poly(P('frame_casing_back', 'frame', 'paint'), 'z', U(8, 32, D.backIn, D.backHead), 8, 8 + D.casingZ, 0.06);
  if (steel) {
    poly(P('frame_stop', 'frame', 'paint'), 'z', U(D.liningIn, 32 - D.lining, D.stop.x, D.stop.head, D.stop.y0), D.stop.z[0], D.stop.z[1], 0);
    // threshold: a bevelled aluminium saddle under the leaf, flush with the face (section in z, y), across the lining
    const [t0, t1] = D.threshold;
    poly(P('threshold', 'frame', 'alu'), 'x', [[t0, 0], [t1, 0], [t1, 0.05], [t1 - 0.4, 0.2], [t0 + 0.4, 0.2], [t0, 0.05]], -D.liningIn, D.liningIn, 0);
    // drip cap over the head casing, against the wall above, sloped to shed water
    poly(P('drip_cap', 'frame', 'alu'), 'x', [[-8, 32], [-8, 32.5], [-8 - D.drip, 32.12], [-8 - D.drip, 32]], -8, 8, 0);
  }
  // the leaf (hinge edge at -X, latch edge at +X); the wood door's slab is a child bone per look
  const bone = steel ? 'leaf' : 'leaf_plain';
  slab(P('leaf_slab', bone, steel ? 'paint' : 'wood'), 'z', [-L.x, L.y[0], zf], [L.x, L.y[1], zb], 0.05);
  const hw = P('hardware', 'leaf', 'metal'), {x: lx, y: ly} = D.latch;
  const lever = (s) => {   // s = -1 the front face, +1 the back face
    const z = s < 0 ? zf : zb, o = d => z + s * d;
    cyl(hw, 'z', lx, ly, 0.53, Math.min(z, o(0.19)), Math.max(z, o(0.19)), 14);
    cyl(hw, 'z', lx, ly, 0.17, Math.min(o(0.19), o(0.78)), Math.max(o(0.19), o(0.78)), 10);
    cyl(hw, 'x', o(0.86), ly, 0.19, lx - 2.0, lx + 0.17, 10);
  };
  lever(-1); lever(1);
  for (const [y0, y1] of D.knuckles) cyl(hw, 'y', D.pivot[kind][1], D.pivot[kind][0], D.knuckleR, y0, y1, 10);
  const closerSide = steel ? 1 : -1, cz = closerSide < 0 ? zf : zb, co = d => cz + closerSide * d;
  const cl = P('closer', 'leaf', 'closer');
  slab(cl, 'z', [-6.0, 29.4, Math.min(cz, co(0.95))], [-1.2, 30.5, Math.max(cz, co(0.95))], 0.1);
  slab(cl, 'z', [-1.2, 29.78, Math.min(co(0.62), co(0.86))], [3.0, 30.12, Math.max(co(0.62), co(0.86))], 0);   // the folded parallel arm
  if (steel) {
    cyl(hw, 'z', lx, D.deadbolt, 0.45, zf - 0.22, zf, 14);                                  // keyed cylinder outside
    cyl(hw, 'z', lx, D.deadbolt, 0.45, zb, zb + 0.15, 14);                                  // thumbturn rose inside
    slab(hw, 'z', [lx - 0.12, D.deadbolt - 0.36, zb + 0.15], [lx + 0.12, D.deadbolt + 0.36, zb + 0.5], 0);
    cyl(hw, 'z', 0, D.viewer, 0.22, zf - 0.12, zf, 12);
    cyl(hw, 'z', 0, D.viewer, 0.3, zb, zb + 0.18, 12);
  } else {
    slab(P('kick_plate', 'leaf', 'kick'), 'z', [-6.45, 0.55, zf - 0.06], [6.45, 0.55 + D.kick, zf], 0);
    // vision: the slab with a 4" x 25" lite on the latch side, glass and stainless beads on both faces
    const {x: [ax, bx], y: [ay, by], bead} = D.lite;
    poly(P('leaf_slab_lite', 'leaf_lite', 'wood'), 'z', rect(-L.x, L.y[0], L.x, L.y[1]), zf, zb, 0.05, [rect(ax, ay, bx, by)]);
    const g = P('lite_glass', 'leaf_lite', 'glass'), gz0 = zf + 0.28, gz1 = zb - 0.28;
    quad(g, [[ax, ay, gz0], [bx, ay, gz0], [bx, by, gz0], [ax, by, gz0]], [0, 0, -1]);
    quad(g, [[ax, ay, gz1], [bx, ay, gz1], [bx, by, gz1], [ax, by, gz1]], [0, 0, 1]);
    const bd = P('lite_beads', 'leaf_lite', 'metal');
    for (const [z0, z1] of [[zf - 0.12, zf], [zb, zb + 0.12]]) poly(bd, 'z', rect(ax - bead, ay - bead, bx + bead, by + bead), z0, z1, 0.03, [rect(ax, ay, bx, by)]);
    // the restroom set: occupancy indicator over the lever (VACANT window) and its thumbturn inside, the door plaque
    const rr = P('indicator', 'restroom', 'metal');
    slab(rr, 'z', [lx - 0.5, D.deadbolt - 0.65, zf - 0.1], [lx + 0.5, D.deadbolt + 0.65, zf], 0.03);
    slab(P('indicator_window', 'restroom', 'green'), 'z', [lx - 0.32, D.deadbolt - 0.12, zf - 0.11], [lx + 0.32, D.deadbolt + 0.2, zf - 0.1], 0);
    cyl(rr, 'z', lx, D.deadbolt, 0.45, zb, zb + 0.15, 14);
    slab(rr, 'z', [lx - 0.12, D.deadbolt - 0.36, zb + 0.15], [lx + 0.12, D.deadbolt + 0.36, zb + 0.5], 0);
    const [pcx, pcy] = D.plaque.c, h = D.plaque.s / 2;
    slab(P('plaque', 'restroom', 'plaque'), 'z', [pcx - h, pcy - h, zf - 0.07], [pcx + h, pcy + h, zf], 0.02);
  }
  return PARTS;
}

// ---------------- doors ----------------
const ATLAS = 1024, PAD = 2;
const DUMMY = 'data:image/png;base64,' + png(Buffer.from([0, 0, 0, 255]), 1, 1).toString('base64');
const RIGS = {steel: ['frame', 'leaf'], wood: ['frame', 'leaf', 'leaf_plain', 'leaf_lite', 'restroom']};
const CHILD = new Set(['leaf_plain', 'leaf_lite', 'restroom']);
const HIDDEN_IN_ITEM = new Set(['leaf_lite', 'restroom']);   // geo neverRender: the item shows the plain door
const DOORS = {steel: {id: 'steel_door', label: 'Steel Door'}, wood: {id: 'commercial_wood_door', label: 'Commercial Wood Door'}};

function door(kind) {
  const {id} = DOORS[kind], PARTS = build(kind);
  const UV = unwrap(PARTS, {atlas: ATLAS, pad: PAD, startS: 16, stepS: 0.5});
  for (const p of PARTS) assert(p.f.every(f => UV.faceUV.get(p)?.has(f)), 'unmapped face in ' + p.name);
  const painted = paint({PARTS, islands: UV.islands, S: UV.S, uvOf: UV.uvOf, atlas: ATLAS, pad: PAD, MATS, ZONED: new Set(), groupInfo: new Map(),
    sourceGroups: () => [], refTexture: {source: DUMMY}, refUvWidth: 1, background: {c: [...PAINT, 255], s: [MATS.paint.sm, MATS.paint.f0, 0, 255], n: [128, 128, 255, 255]}});
  const M = painted.PNG.map(b => { const r = readPng(b); assert(r.bpp === 4 && r.w === ATLAS, 'atlas format'); return r.px; });
  for (const is of UV.islands) if (is.part.mat === 'glass')
    for (let y = is.py - PAD; y < is.py + is.H + PAD; y++) for (let x = is.px - PAD; x < is.px + is.W + PAD; x++) M[0][(y * ATLAS + x) * 4 + 3] = GLASS_ALPHA;
  const PNGS = M.map(px => png(px, ATLAS, ATLAS));
  const [pvx, pvz] = D.pivot[kind];
  const rigs = ['right', 'left'].map(hinge => rig(kind, id, PARTS, UV, PNGS, hinge, [pvx, 0, pvz]));
  // the plain and the vision slab share one place and are never drawn together: check each look on its own
  const looks = kind === 'steel' ? [() => false] : [p => p.bone === 'leaf_lite', p => p.bone === 'leaf_plain'];
  const zf = {unresolved: looks.flatMap(skip => zFightLevels(PARTS, new Map(), {skip}).unresolved)};
  return {kind, id, PARTS, UV, PNGS, rigs, zf};
}

function rig(kind, id, PARTS, UV, PNGS, hinge, pivot) {
  const s = hinge === 'right' ? 1 : -1, rid = `${id}_${hinge}`;
  const uuid = k => { const h = createHash('sha256').update('afl-steel-frame-doors-v1:' + rid + ':' + k).digest('hex'); return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-8${h.slice(17, 20)}-${h.slice(20, 32)}`; };
  const mir = q => [q[0] * s, q[1], q[2]];
  const origin = b => b === 'frame' ? [0, 0, 0] : mir(pivot);
  const BONES = RIGS[kind];
  const elements = [], nodes = new Map(BONES.map(b => [b, {uuid: uuid('group:' + b), isOpen: true, children: []}]));
  for (const p of PARTS) {
    const key = i => i.toString(36).padStart(4, '0'), vertices = {}, faces = {}, uvs = UV.faceUV.get(p);
    p.v.forEach((q, i) => { vertices[key(i)] = mir(q).map(r12); });
    // a mirror turns the winding inside out: reverse each face's vertex order (UVs stay keyed by vertex)
    p.f.forEach((f, fi) => { const uv = uvs.get(f), ids = s > 0 ? f.ids : f.ids.slice().reverse();
      faces['f' + key(fi)] = {uv: Object.fromEntries(f.ids.map((vid, j) => [key(vid), uv[j].map(r12)])), vertices: ids.map(key), texture: 0}; });
    const eid = uuid('mesh:' + p.name);
    elements.push({name: p.name, color: 0, origin: [0, 0, 0], rotation: [0, 0, 0], shading: 'flat', export: true, visibility: true, locked: false,
      render_order: 'default', scope: 0, allow_mirror_modeling: true, vertices, faces, type: 'mesh', uuid: eid});
    nodes.get(p.bone).children.push(eid);
  }
  const source = {meta: {format_version: '5.0', model_format: 'free', box_uv: false}, name: rid, model_identifier: '', visible_box: [1, 2, 0],
    variable_placeholders: '', variable_placeholder_buttons: [], timeline_setups: [], unhandled_root_fields: {}, resolution: {width: ATLAS, height: ATLAS},
    elements, groups: BONES.map(b => ({name: b, uuid: uuid('group:' + b), export: true, locked: false, scope: 0, selected: false, visibility: true,
      _static: {properties: {}, temp_data: {}}, origin: origin(b).slice(), rotation: [0, 0, 0], color: 0, children: [], reset: false, shade: true,
      mirror_uv: false, autouv: 0, isOpen: true, primary_selected: false})),
    outliner: BONES.map(b => nodes.get(b)),
    textures: [{name: id + '.png', relative_path: `textures/${id}.png`, folder: '', namespace: '', id: '0', group: '', scope: 0,
      width: ATLAS, height: ATLAS, uv_width: ATLAS, uv_height: ATLAS, particle: false, use_as_default: false, layers_enabled: false, sync_to_project: '',
      file_format: 'png', render_mode: 'default', render_sides: 'auto', wrap_mode: 'limited', pbr_channel: 'color', fps: 7, frame_time: 1,
      frame_order_type: 'loop', frame_order: '', frame_interpolate: false, visible: true, internal: true, saved: true, uuid: uuid('texture'),
      source: 'data:image/png;base64,' + PNGS[0].toString('base64')}],
    animations: []};
  const geo = {format_version: '1.12.0', 'minecraft:geometry': [{description: {identifier: 'geometry.' + rid, texture_width: ATLAS, texture_height: ATLAS,
    visible_bounds_width: 3, visible_bounds_height: 3, visible_bounds_offset: [0, 1, 0]},
    bones: BONES.map(b => { const o = origin(b); return {name: b, pivot: [-o[0] || 0, o[1], o[2]], ...(HIDDEN_IN_ITEM.has(b) ? {neverRender: true} : {})}; })}]};
  const LAYERS = Object.fromEntries(PARTS.filter(p => p.mat === 'glass').map(p => [p.name, 'translucent']));
  const sidecar = convert(source, geo, {}, rid + '.bbmodel', 2, null, LAYERS);
  const deg = OPEN_DEGREES[kind] * s, pv = mir(pivot);
  const aroundY = (q, t) => { const a = deg * t * Math.PI / 180, x = q[0] - pv[0], z = q[2] - pv[2];   // the runtime's Ry
    return [pv[0] + x * Math.cos(a) + z * Math.sin(a), q[1], pv[2] - x * Math.sin(a) + z * Math.cos(a)]; };
  const posed = (p, t) => p.bone === 'frame' ? p.v.map(mir) : p.v.map(q => aroundY(mir(q), t));
  const aabbOf = pts => [0, 1, 2].map(k => Math.min(...pts.map(q => q[k]))).concat([0, 1, 2].map(k => Math.max(...pts.map(q => q[k]))));
  const sweep = aabbOf(PARTS.flatMap(p => Array.from({length: 13}, (_, k) => posed(p, k / 12)).flat())), m = 0.25;
  const bounds = [(sweep[0] - m + 8) / 16, (sweep[1] - m) / 16, (sweep[2] - m + 8) / 16, (sweep[3] + m + 8) / 16, (sweep[4] + m) / 16, (sweep[5] + m + 8) / 16].map(r6);
  const profile = {format_version: 1, geometry: `apocalypse_firstlight:geo/${rid}.geo.json`, texture: `apocalypse_firstlight:textures/block/${id}.png`,
    origin: [0, 0, 0], scale: [1, 1, 1], facing: 'horizontal', bounds,
    parts: Object.fromEntries(BONES.map(b => [b, {pivot: origin(b).map(v => r6(v / 16)), ...(CHILD.has(b) ? {parent: 'leaf'} : {})}])),
    animations: {open: {duration_ticks: OPEN_TICKS, easing: 'ease_in_out', transforms: {leaf: {rotation: [0, deg, 0]}}}}};
  const leafParts = PARTS.filter(p => p.bone !== 'frame' && p.bone !== 'leaf_lite' && p.bone !== 'restroom');
  return {hinge, rid, source, geo, sidecar, meshText: serializeCompact(sidecar), profile, bounds, posed, aabbOf,
    openLeaf: aabbOf(leafParts.filter(p => p.name === 'leaf_slab').flatMap(p => posed(p, 1))).map(r3),
    closedLeaf: aabbOf(leafParts.filter(p => p.name === 'leaf_slab').flatMap(p => posed(p, 0))).map(r3)};
}

// item model: builtin/entity on the right-hinge rig, the leaf closed; every view centred on the mesh (two blocks tall)
function itemDisplay(PARTS) {
  const rot = ([ax, ay]) => q => { const a = ay * Math.PI / 180, b = ax * Math.PI / 180, x = q[0] * Math.cos(a) + q[2] * Math.sin(a), z = -q[0] * Math.sin(a) + q[2] * Math.cos(a);
    return [x, q[1] * Math.cos(b) - z * Math.sin(b), q[1] * Math.sin(b) + z * Math.cos(b)]; };
  const pts = PARTS.filter(p => !HIDDEN_IN_ITEM.has(p.bone)).flatMap(p => p.v).map(q => [q[0], q[1] - 8, q[2]]), S3 = v => [v, v, v];
  const view = (r, fit, extra = [0, 0, 0]) => { const R = pts.map(rot(r)), xs = R.map(q => q[0]), ys = R.map(q => q[1]), zs = R.map(q => q[2]);
    const s = typeof fit === 'number' && fit > 1 ? r3(fit / Math.max(Math.max(...xs) - Math.min(...xs), Math.max(...ys) - Math.min(...ys))) : fit;
    const c = [(Math.max(...xs) + Math.min(...xs)) / 2, (Math.max(...ys) + Math.min(...ys)) / 2, (Math.max(...zs) + Math.min(...zs)) / 2];
    return {rotation: [r[0], r[1], 0], translation: [r3(-s * c[0] + extra[0]), r3(-s * c[1] + extra[1]), r3(-s * c[2] + extra[2])], scale: S3(s)}; };
  return {gui: view([20, 205], 15), ground: view([0, 0], 0.25, [0, 3, 0]), fixed: view([0, 180], 0.45),
    thirdperson_righthand: view([75, 205], 0.28, [0, 2.5, 0]), firstperson_righthand: view([0, 205], 0.3, [0, 1, 0])};
}

// blockstate: every state draws nothing (the block entity on the lower half draws the door); particle only
const blockstate = id => ({variants: {'': {model: `apocalypse_firstlight:block/${id}`}}});

export const BUILT = Object.fromEntries(Object.keys(DOORS).map(k => [k, door(k)]));
const bb = path.join(ROOT, 'src/main/blockbench'), assets = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight');
const outputs = [];
for (const d of Object.values(BUILT)) {
  const {id} = d;
  outputs.push([path.join(bb, `${id}_v1.bbmodel`), JSON.stringify(d.rigs[0].source)]);   // the right rig; the left is its mirror
  for (const r of d.rigs) outputs.push([path.join(assets, `geo/${r.rid}.geo.json`), JSON.stringify(r.geo, null, 2) + '\n'],
    [path.join(assets, `meshes/${r.rid}.aflmesh.json`), r.meshText], [path.join(assets, `block_mesh_profiles/${r.rid}.json`), JSON.stringify(r.profile, null, 2) + '\n']);
  outputs.push([path.join(assets, `models/item/${id}.json`), JSON.stringify({parent: 'builtin/entity', gui_light: 'side', textures: {particle: `apocalypse_firstlight:block/${id}`}, display: itemDisplay(d.PARTS)}, null, 2) + '\n'],
    [path.join(assets, `models/block/${id}.json`), JSON.stringify({loader: 'apocalypse_firstlight:static_mesh', textures: {particle: `apocalypse_firstlight:block/${id}`}}, null, 2) + '\n'],
    [path.join(assets, `blockstates/${id}.json`), JSON.stringify(blockstate(id), null, 2) + '\n'],
    ...['', '_s', '_n'].flatMap((k, i) => [[path.join(bb, `textures/${id}${k}.png`), d.PNGS[i]], [path.join(assets, `textures/block/${id}${k}.png`), d.PNGS[i]]]));
}

export const stats = Object.fromEntries(Object.values(BUILT).map(d => [d.id, {
  triangles: d.rigs[0].sidecar.parts.flatMap(p => p.faces).reduce((s, q) => s + q.length - 2, 0), parts: d.rigs[0].sidecar.parts.length,
  texelsPerPx: d.UV.S, islands: d.UV.islands.length, coplanarOverlaps: d.zf.unresolved.length,
  rigs: Object.fromEntries(d.rigs.map(r => [r.hinge, {bounds: r.bounds, closedLeaf: r.closedLeaf, openLeaf: r.openLeaf}]))}]));
const isMain = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  console.log(JSON.stringify(stats, null, 1));
  for (const d of Object.values(BUILT)) if (d.zf.unresolved.length) console.log('COPLANAR ' + d.id, JSON.stringify(d.zf.unresolved.slice(0, 12)));
  const pi = process.argv.indexOf('--preview');
  if (pi > 0) {
    const dir = process.argv[pi + 1]; fs.mkdirSync(dir, {recursive: true});
    for (const d of Object.values(BUILT)) {
      for (const r of d.rigs) { fs.writeFileSync(path.join(dir, `${r.rid}.geo.json`), JSON.stringify(r.geo, null, 2) + '\n'); fs.writeFileSync(path.join(dir, `${r.rid}.aflmesh.json`), r.meshText); }
      ['', '_s', '_n'].forEach((k, i) => fs.writeFileSync(path.join(dir, `${d.id}${k}.png`), d.PNGS[i]));
    }
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
