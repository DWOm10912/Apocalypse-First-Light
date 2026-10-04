// Fuel Dispenser V1: a dual-sided, high-hose fuel dispenser on its own 2 x 1 island curb segment (reference: a dual-pillar
// dispenser with hoses dropping from the header, user 2026-10-04). Faded red header on two pillars; hose outlets under the
// header ends; a display head with one LCD and one keypad per face; a nozzle bay with two framed, slanted holster recesses
// per face (gasoline at each customer's left with a red cover, diesel at the right with a dark green cover); louvred
// hydraulic cabinet doors; a lamp box under the header. Pure Mesh + LabPBR atlas exported as Forge OBJ block models
// (static, chunk-baked). Each nozzle with its parked hose is its own model, so the blockstate hides it while the nozzle is
// out; the block entity renderer then draws a live hose from that nozzle's outlet to the holder's hand
// (client/FuelDispenserRenderer, which samples the reserved rubber texels this generator paints, HOSE_UV).
//   node tools/build-fuel-dispenser-v1.mjs                -> writes source, OBJ / MTL / block + item models, blockstate, atlas
//   node tools/build-fuel-dispenser-v1.mjs --check        -> verifies every output (and the renderer's HOSE_UV) is up to date
//   node tools/build-fuel-dispenser-v1.mjs --preview DIR  -> writes only OBJ + maps into DIR (offline review)
// Frame (px): the footprint centre at the origin, x -16..16 along the island (the master cell is x -16..0, the second cell
// 0..16 on the master's clockwise side), z -8..8, y up through three cells (0..48); customers on -Z (the facing side, the
// "front") and +Z (the "back"); the back face is the front face turned 180 deg about y. OBJ in block units relative to the
// master cell: x = (px + 16) / 16, y = px / 16, z = (px + 8) / 16.
// The nozzle items (fuel_nozzle_gasoline / _diesel) are the same nozzle mesh in its own frame (NOZZLE).
// The power port (the standard AFL port, tools/afl-power-port.mjs) sits on the bottom face of the master cell, where an
// underground cable would come up (docs/models/fuel_dispenser_v1.md); V1 does not declare it, there is no power yet.
// Plain surfaces: no printed legends, logos or decals. Base colours carry low-frequency texture only.
import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {Part, AX, extrude, add, sub, mul, dot, cross, norm, newell, area2, unwrap, paint, png, readPng, zFightLevels} from './cube-slab-mesh-lib.mjs';
import {addPowerPort, portHole} from './afl-power-port.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
function assert(c, m) { if (!c) throw new Error(m); }
const D2R = Math.PI / 180;

// ---------------- primitives ----------------
const orient = (L, ccw) => (area2(L) > 0) === ccw ? L : L.slice().reverse();
const rect = (u0, v0, u1, v1) => [[u0, v0], [u1, v0], [u1, v1], [u0, v1]];
const shape = (L, holes = []) => ({outer: orient(L, true), holes: holes.map(h => orient(h, false))});
function slab(part, ax, a, b, c = 0) {
  const [ku, kv, ka] = ax === 'x' ? [2, 1, 0] : ax === 'y' ? [2, 0, 1] : [0, 1, 2];
  extrude(part, ax, shape(rect(a[ku], a[kv], b[ku], b[kv])), a[ka], b[ka], c);
}
const zx = L => L.map(([x, z]) => [z, x]);
const sideX = (part, L, x0, x1, c) => extrude(part, 'x', shape(L), x0, x1, c);                                // side profile (z, y)
const planY = (part, L, y0, y1, c, holes = []) => extrude(part, 'y', shape(zx(L), holes.map(zx)), y0, y1, c);   // plan (x, z)
// a band of width w on one side of an open polyline (2D), as a closed outline: the polyline plus its mitred offset
function band(pts, w, side = 1) {
  const off = pts.map((p, i) => {
    const a = pts[Math.max(0, i - 1)], b = pts[Math.min(pts.length - 1, i + 1)], d = norm([b[0] - a[0], b[1] - a[1], 0]);
    let nx = -d[1] * side, ny = d[0] * side;
    if (i > 0 && i < pts.length - 1) {
      const d0 = norm([pts[i][0] - pts[i - 1][0], pts[i][1] - pts[i - 1][1], 0]), d1 = norm([pts[i + 1][0] - pts[i][0], pts[i + 1][1] - pts[i][1], 0]);
      const n0 = [-d0[1] * side, d0[0] * side], n1 = [-d1[1] * side, d1[0] * side], m = norm([n0[0] + n1[0], n0[1] + n1[1], 0]);
      const k = 1 / Math.max(0.3, m[0] * n0[0] + m[1] * n0[1]); nx = m[0] * k; ny = m[1] * k;
    }
    return [p[0] + nx * w, p[1] + ny * w];
  });
  return [...pts, ...off.reverse()];
}
// rounded rectangle, corner radii [(u1,v0), (u1,v1), (u0,v1), (u0,v0)], counter-clockwise
function rrect(u0, v0, u1, v1, r, n = 3) {
  const R = Array.isArray(r) ? r : [r, r, r, r], C = [[u1, v0, -90, -1, 1], [u1, v1, 0, -1, -1], [u0, v1, 90, 1, -1], [u0, v0, 180, 1, 1]], out = [];
  C.forEach(([u, v, a0, su, sv], i) => {
    const rr = R[i];
    if (rr <= 0) { out.push([u, v]); return; }
    for (let k = 0; k <= n; k++) { const a = (a0 + 90 * k / n) * D2R; out.push([u + su * rr + rr * Math.cos(a), v + sv * rr + rr * Math.sin(a)]); }
  });
  return out;
}
function cyl(part, ax, cu, cv, r, a0, a1, seg, tag = 'side') {
  const A = AX[ax], ang = i => Math.PI / seg + 2 * Math.PI * i / seg;
  const rg = a => Array.from({length: seg}, (_, i) => part.vtx(A.to3(cu + r * Math.cos(ang(i)), cv + r * Math.sin(ang(i)), a)));
  const r0 = rg(a0), r1 = rg(a1);
  for (let i = 0; i < seg; i++) { const j = (i + 1) % seg, m = (ang(i) + ang(j)) / 2;
    part.face([r0[i], r0[j], r1[j], r1[i]], A.to3(Math.cos(m), Math.sin(m), 0), tag); }
  for (const [ring, a, s] of [[r0, a0, -1], [r1, a1, 1]]) { const c = part.vtx(A.to3(cu, cv, a));
    for (let i = 0; i < seg; i++) part.face([c, ring[i], ring[(i + 1) % seg]], mul(A.n, s), 'cap'); }
}
// centripetal Catmull-Rom through ctrl with phantom end points (they set the end tangents), resampled so that segments
// stay short in the bends: a new ring when the run reaches maxLen or the direction turns by maxTurn degrees
function crPoint(p0, p1, p2, p3, t) {
  const tj = (ti, a, b) => ti + Math.sqrt(Math.max(1e-9, Math.hypot(...sub(b, a))));
  const t0 = 0, t1 = tj(t0, p0, p1), t2 = tj(t1, p1, p2), t3 = tj(t2, p2, p3), u = t1 + (t2 - t1) * t;
  const L = (a, b, ta, tb) => add(mul(a, (tb - u) / (tb - ta)), mul(b, (u - ta) / (tb - ta)));
  const A1 = L(p0, p1, t0, t1), A2 = L(p1, p2, t1, t2), A3 = L(p2, p3, t2, t3), B1 = L(A1, A2, t0, t2), B2 = L(A2, A3, t1, t3);
  return L(B1, B2, t1, t2);
}
function spline(ctrl, before, after, {per = 48, maxLen = 2.0, maxTurn = 10} = {}) {
  const P = [before, ...ctrl, after], dense = [];
  for (let i = 1; i + 2 < P.length; i++) for (let k = 0; k < per; k++) dense.push(crPoint(P[i - 1], P[i], P[i + 1], P[i + 2], k / per));
  dense.push(ctrl[ctrl.length - 1]);
  const out = [dense[0]]; let dir = norm(sub(dense[1], dense[0])), run = 0;
  for (let i = 1; i < dense.length - 1; i++) {
    run += Math.hypot(...sub(dense[i], dense[i - 1]));
    const d = norm(sub(dense[i + 1], dense[i]));
    if (run >= maxLen || Math.acos(Math.min(1, dot(d, dir))) > maxTurn * D2R) { out.push(dense[i]); dir = d; run = 0; }
  }
  out.push(dense[dense.length - 1]);
  return out;
}
const rotAxis = (v, k, a) => add(add(mul(v, Math.cos(a)), mul(cross(k, v), Math.sin(a))), mul(k, dot(k, v) * (1 - Math.cos(a))));
// a round tube along a polyline, rotation-minimising frames (parallel transport), closed ends
function tube(part, pts, r, sides = 8) {
  const n = pts.length, T = pts.map((p, i) => norm(sub(pts[Math.min(n - 1, i + 1)], pts[Math.max(0, i - 1)])));
  let N = norm(cross(T[0], Math.abs(T[0][1]) < 0.9 ? [0, 1, 0] : [1, 0, 0]));
  const rings = [];
  for (let i = 0; i < n; i++) {
    if (i) { const b = cross(T[i - 1], T[i]), s = Math.hypot(...b);
      if (s > 1e-9) N = rotAxis(N, mul(b, 1 / s), Math.atan2(s, dot(T[i - 1], T[i])));
      N = norm(sub(N, mul(T[i], dot(N, T[i])))); }
    const B = cross(T[i], N);
    rings.push(Array.from({length: sides}, (_, k) => { const a = 2 * Math.PI * (k + 0.5) / sides; return part.vtx(add(pts[i], add(mul(N, r * Math.cos(a)), mul(B, r * Math.sin(a))))); }));
  }
  for (let i = 0; i + 1 < n; i++) {
    const mid = mul(add(pts[i], pts[i + 1]), 0.5);
    for (let k = 0; k < sides; k++) {
      const ids = [rings[i][k], rings[i][(k + 1) % sides], rings[i + 1][(k + 1) % sides], rings[i + 1][k]];
      part.face(ids, sub(mul(ids.reduce((s, id) => add(s, part.v[id]), [0, 0, 0]), 0.25), mid));
    }
  }
  for (const [i, s] of [[0, -1], [n - 1, 1]]) { const c = part.vtx(pts[i]); for (let k = 0; k < sides; k++) part.face([c, rings[i][k], rings[i][(k + 1) % sides]], mul(T[i], s), 'cap'); }
}

// ---------------- textured base colours (low-frequency only) ----------------
const hash = (a, b, c) => { let h = Math.imul(a | 0, 374761393) ^ Math.imul(b | 0, 668265263) ^ Math.imul(c | 0, 2147483647); h = Math.imul(h ^ (h >>> 13), 1274126177); return ((h ^ (h >>> 16)) >>> 0) / 4294967295; };
function vn(x, y, z) {
  const xi = Math.floor(x), yi = Math.floor(y), zi = Math.floor(z), s = t => t * t * (3 - 2 * t), l = (a, b, t) => a + (b - a) * t;
  const sx = s(x - xi), sy = s(y - yi), sz = s(z - zi), h = (a, b, c) => hash(a, b, c * 7919 + 101);
  return l(l(l(h(xi, yi, zi), h(xi + 1, yi, zi), sx), l(h(xi, yi + 1, zi), h(xi + 1, yi + 1, zi), sx), sy),
    l(l(h(xi, yi, zi + 1), h(xi + 1, yi, zi + 1), sx), l(h(xi, yi + 1, zi + 1), h(xi + 1, yi + 1, zi + 1), sx), sy), sz);
}
const fbm = (x, y, z) => 0.6 * vn(x, y, z) + 0.3 * vn(x * 2.03 + 7.1, y * 2.03 + 3.3, z * 2.03 + 1.7) + 0.1 * vn(x * 4.1 + 2.9, y * 4.1 + 8.1, z * 4.1 + 5.3);
const tone = (c, k) => c.map(v => v * k);
const mix = (a, b, t) => a.map((v, i) => v + (b[i] - v) * t);
const sstep = (a, b, x) => { const t = Math.max(0, Math.min(1, (x - a) / (b - a))); return t * t * (3 - 2 * t); };
const mott = (p, s, k) => k * (fbm(p[0] * s, p[1] * s, p[2] * s) - 0.5);

export const MATS = {   // Base Color (sRGB or a function of the model position and normal), bevel highlight, smoothness open / edge, F0
  // weathered light grey enamel (pillars, doors, lamp box): soft mottling, faint vertical rain streaks, grime toward the curb
  paint:    {c: p => tone([168, 168, 162], (1 + mott(p, 0.22, 0.05) + 0.035 * (fbm(p[0] * 0.8 + p[2] * 0.8, p[1] * 0.06, 3.1) - 0.5)) * (1 - 0.10 * sstep(12, 4, p[1]))),
    hl: 8, sm: 92, se: 120, f0: 22},
  // faded red header: sun-bleached on the top, mottled
  red:      {c: (p, n) => tone(mix([142, 58, 50], [170, 106, 96], (n[1] > 0.5 ? 0.38 : 0.08) + 0.10 * (fbm(p[0] * 0.18, p[1] * 0.18, p[2] * 0.18) - 0.5)), 1 + mott(p, 0.35, 0.05)),
    hl: 8, sm: 96, se: 124, f0: 22},
  green:    {c: p => tone([66, 98, 76], 1 + mott(p, 0.3, 0.05)), hl: 6, sm: 92, se: 118, f0: 22},      // faded grade band
  head:     {c: p => tone([112, 116, 117], 1 + mott(p, 0.3, 0.04)), hl: 8, sm: 100, se: 126, f0: 22},  // display head
  black:    {c: p => tone([35, 36, 39], 1 + mott(p, 0.3, 0.04)), hl: 4, sm: 74, se: 90, f0: 20},       // cabinet, bay, plinth, bezels
  lcd:      {c: p => tone([90, 98, 90], 1 + mott(p, 0.9, 0.03)), hl: 2, sm: 196, se: 200, f0: 14},     // unpowered LCD behind glass
  screen:   {c: [20, 25, 27], hl: 2, sm: 210, se: 210, f0: 14},                                        // card reader screen
  keys:     {c: [146, 150, 154], hl: 8, sm: 150, se: 165, f0: 255},
  steel:    {c: [140, 144, 150], hl: 10, sm: 150, se: 168, f0: 255},
  alu:      {c: [150, 152, 154], hl: 9, sm: 118, se: 140, f0: 255},
  frame:    {c: [138, 142, 146], hl: 10, sm: 132, se: 150, f0: 255},   // holster frames and boots (brushed stainless)
  grip:     {c: p => tone([32, 32, 34], 1 + mott(p, 0.6, 0.04)), hl: 4, sm: 92, se: 104, f0: 20},     // nozzle handle, lever, guard
  rubber:   {c: p => tone([28, 28, 30], 1 + mott(p, 0.4, 0.04)), hl: 2, sm: 78, se: 86, f0: 18},      // hoses
  gas:      {c: p => tone([150, 54, 46], 1 + mott(p, 0.6, 0.04)), hl: 5, sm: 88, se: 104, f0: 20},     // gasoline nozzle cover
  diesel:   {c: p => tone([58, 98, 68], 1 + mott(p, 0.6, 0.04)), hl: 5, sm: 88, se: 104, f0: 20},     // diesel nozzle cover
  lens:     {c: [164, 166, 162], hl: 4, sm: 140, se: 150, f0: 16},
  // the lamp lens while powered: warm white; its _s alpha marks it LabPBR emissive (LIT_TEXELS) and its MTL's Ka makes
  // Forge bake it full bright (emissive_ambient)
  lensLit:  {c: [255, 244, 222], hl: 0, sm: 160, se: 160, f0: 16},
  concrete: {c: p => tone([146, 144, 138], 1 + mott(p, 0.22, 0.08) + 0.04 * (vn(p[0] * 0.9, p[1] * 0.9, p[2] * 0.9) - 0.5)), hl: 5, sm: 40, se: 52, f0: 20},
  nosing:   {c: [126, 130, 132], hl: 8, sm: 112, se: 140, f0: 255},
  // the standard power port (tools/afl-power-port.mjs): steel plate, dark socket cup, contact pin
  portPlate:  {c: [150, 154, 160], hl: 12, sm: 150, se: 168, f0: 255},
  portSocket: {c: [26, 27, 29], hl: 2, sm: 60, se: 70, f0: 20},
  portPin:    {c: [96, 100, 104], hl: 6, sm: 130, se: 140, f0: 255},
};
const RUBBER = {c: [28, 28, 30], s: [78, 18, 0, 255], n: [128, 128, 255, 255]};   // the live hose texels (HOSE_UV)

// ---------------- the nozzle ----------------
// Own frame (px): z forward along the spout, y up (the coloured hood on top, the lever and the hand guard below), x across;
// the hose swivel's front face at the origin, the hose leaving backward (-Z). The held items use this frame.
export const NOZZLE = {
  // black handle casting from the swivel to the hood (the palm rests on it)
  handle: [[0.0, -0.42], [1.9, -0.36], [2.1, 0.0], [2.0, 0.85], [0.3, 0.75], [0.0, 0.45]], handleW: 0.42,
  // the coloured hood: tall and round at the front where the spout leaves, lower toward the handle
  cover: [[1.65, -0.6], [2.9, -0.56], [3.42, -0.28], [3.68, 0.28], [3.74, 0.88], [3.52, 1.34], [3.02, 1.56], [2.22, 1.46], [1.62, 1.14]], coverW: 0.6,
  lever: [[0.6, -0.96], [1.85, -0.64], [1.92, -0.4], [0.55, -0.72]], leverW: 0.3,
  // the D-shaped hand guard: from under the hood down, back, and up to the swivel end
  guard: [[2.3, -0.5], [2.2, -1.36], [1.2, -1.62], [0.45, -1.46], [0.04, -0.52]], guardT: 0.22, guardW: 0.36,
  spout: [[0, 1.0, 3.55], [0, 0.8, 4.6], [0, 0.36, 5.6], [0, -0.2, 6.2]], spoutBefore: [0, 1.2, 2.5], spoutAfter: [0, -0.9, 6.6], spoutR: 0.22,
  collar: [1.0, 0.32, 3.45, 3.95],           // spout collar: y, r, z0, z1
  swivel: [0.42, -0.7, 0.3],                 // r, z0, z1
};
const spoutPath = () => spline(NOZZLE.spout, NOZZLE.spoutBefore, NOZZLE.spoutAfter, {maxLen: 0.6, maxTurn: 6});
function nozzle(P, name, bone, coverMat, xf) {
  const N = NOZZLE, parts = [], Q = (n, mat) => { const p = P(`${name}_${n}`, bone, mat); parts.push(p); return p; };
  sideX(Q('handle', 'grip'), N.handle, -N.handleW, N.handleW, 0.14);
  sideX(Q('cover', coverMat), N.cover, -N.coverW, N.coverW, 0.22);
  const grip = Q('guard', 'grip');
  sideX(grip, N.lever, -N.leverW, N.leverW, 0);
  sideX(grip, band(N.guard, N.guardT), -N.guardW, N.guardW, 0.06);
  const steel = Q('steel', 'steel');
  cyl(steel, 'z', 0, 0, N.swivel[0], N.swivel[1], N.swivel[2], 10);
  tube(steel, spoutPath(), N.spoutR, 8);
  cyl(Q('collar', 'alu'), 'z', 0, N.collar[0], N.collar[1], N.collar[2], N.collar[3], 10);
  for (const p of parts) p.v = p.v.map(xf);
}
// hung head-on on the front face (-Z): the spout up, the hood toward the customer, the lever and the hand guard toward the
// panel; tilted by tilt degrees about X, the top into the recess; origin: the swivel's front face
const hungPose = (o, tilt) => { const c = Math.cos(tilt * D2R), sn = Math.sin(tilt * D2R);
  return ([x, y, z]) => [o[0] + x, o[1] + z * c + y * sn, o[2] + z * sn - y * c]; };
export const HOLSTER = {
  open: [1.45, 10.8, 19.8],                  // recess opening: half width, bottom, top (on the bay face, z -3.9)
  wall: [[10.75, -2.05], [19.85, -0.95]],    // the slanted back wall's face: (y, z) at the bottom and at the top
  frame: 0.35,                               // stainless frame width around the opening
  tilt: 18, origin: [13.0, -4.45],           // nozzle swivel origin (y, z)
};

// ---------------- the dispenser ----------------
export const DIM = {
  hx: 3.6,                                   // nozzle centres (x, each face)
  outlet: [10.7, 4.6],                       // hose outlets under the header (x, z)
  hoseR: 0.42, hoseStart: 39.3,              // hose radius; the live hose starts inside the outlet swivel (y)
  // the diesel hose of the front face (nozzle at x -3.6), from the outlet down the -X pillar, around the loop, up into the swivel
  hose: [[-10.7, 39.45, -4.6], [-10.75, 35.6, -4.65], [-11.2, 25.0, -4.85], [-11.5, 14.5, -5.2], [-10.7, 8.0, -5.6],
    [-8.6, 4.8, -6.1], [-5.9, 5.25, -6.15], [-4.2, 7.6, -5.6], [-3.65, 9.8, -4.95], [-3.6, 11.3, -4.75]],
};
// the four nozzles, in the order of FuelDispenserBlock.Nozzle: face, grade (cover material), x on the front-face frame
export const NOZZLES = [
  {id: 'front_gasoline', face: 'front', grade: 'gasoline', mat: 'gas', hx: DIM.hx},
  {id: 'front_diesel', face: 'front', grade: 'diesel', mat: 'diesel', hx: -DIM.hx},
  {id: 'back_gasoline', face: 'back', grade: 'gasoline', mat: 'gas', hx: DIM.hx},
  {id: 'back_diesel', face: 'back', grade: 'diesel', mat: 'diesel', hx: -DIM.hx},
];
const turn = ([x, y, z]) => [-x, y, -z];   // the front face to the back face
const facePoint = (n, q) => n.face === 'back' ? turn(q) : q;
export const NOZZLE_INFO = {};             // per nozzle: outlet, hint anchor (the hood), aim box on the face (model px)
function build() {
  const PARTS = [], P = (name, bone, mat) => { const p = new Part(name, bone, mat); PARTS.push(p); return p; };
  // island curb segment with steel nosing on the long top edges (the ends join the next island piece); its bottom layer
  // has the power port's pocket under the master cell
  const [hx0, hz0, hx1, hz1] = portHole(-8, 0);
  planY(P('curb_bottom', 'base', 'concrete'), rect(-16, -8, 16, 8), 0, 0.6, 0, [rect(hx0, hz0, hx1, hz1)]);
  slab(P('curb', 'base', 'concrete'), 'y', [-16, 0.6, -8], [16, 3.0, 8], 0);
  const nos = P('nosing', 'base', 'nosing');
  for (const s of [-1, 1]) {   // a steel angle on each long top edge: section (z, y), the leg on the face, the flange on the top
    const L = [[-8.13, 1.6], [-7.96, 1.6], [-7.96, 2.94], [-7.1, 2.94], [-7.1, 3.07], [-8.13, 3.07]].map(([z, y]) => [z * -s, y]);
    extrude(nos, 'x', shape(L), -15.99, 15.99, 0);
  }
  const port = {plate: P('port_plate', 'base', 'portPlate'), socket: P('port_socket', 'base', 'portSocket'), pin: P('port_pin', 'base', 'portPin')};
  addPowerPort(port, -8, 0, -0.6, 0);   // built facing +Z with the mating face at z 0, then turned to face down at y 0
  for (const p of Object.values(port)) p.v = p.v.map(([x, y, z]) => [x, -z, y]);
  // body
  slab(P('plinth', 'body', 'black'), 'y', [-10.4, 2.98, -4.6], [10.4, 4.2, 4.6], 0.12);
  for (const s of [-1, 1]) {
    const [x0, x1] = s < 0 ? [-10, -7.8] : [7.8, 10], R = s < 0 ? [0.25, 0.25, 0.7, 0.7] : [0.7, 0.7, 0.25, 0.25];
    planY(P(s < 0 ? 'pillar_l' : 'pillar_r', 'body', 'paint'), rrect(x0, -4.2, x1, 4.2, R), 4.18, 40.67, 0);
  }
  slab(P('cabinet', 'body', 'black'), 'y', [-7.9, 4.16, -3.75], [7.9, 10.2, 3.75], 0);
  slab(P('bay_core', 'body', 'black'), 'y', [-7.88, 10.22, -0.9], [7.88, 20.38, 0.9], 0);
  slab(P('band', 'body', 'green'), 'y', [-7.9, 20.4, -4.02], [7.9, 22.2, 4.02], 0.06);
  slab(P('head', 'body', 'head'), 'y', [-7.9, 22.2, -4.12], [7.9, 29.4, 4.12], 0.14);
  planY(P('header', 'body', 'red'), rrect(-11.6, -6.0, 11.6, 6.0, 0.9), 40.6, 44.2, 0.35);
  slab(P('lamp', 'body', 'paint'), 'y', [-4.6, 38.4, -2.2], [4.6, 40.65, 2.2], 0.1);
  // the lamp lens: its own models, unlit / lit (FuelDispenserBlock.LIT, powered through the port)
  slab(P('lens', 'lamp', 'lens'), 'y', [-4.2, 38.15, -1.8], [4.2, 38.45, 1.8], 0);
  slab(P('lens_lit', 'lamp_lit', 'lensLit'), 'y', [-4.2, 38.15, -1.8], [4.2, 38.45, 1.8], 0);
  // the two customer faces (the back is the front turned 180 deg)
  for (const faceName of ['front', 'back']) {
    const start = PARTS.length;
    face(P, faceName);
    if (faceName === 'back') for (const p of PARTS.slice(start)) p.v = p.v.map(turn);
  }
  // the held nozzles (own frame; their models are the items)
  for (const [grade, mat] of [['gasoline', 'gas'], ['diesel', 'diesel']]) nozzle(P, 'item_' + grade, 'item_' + grade, mat, q => q);
  return {PARTS, atlas: 2048, startS: 20};
}
/** One customer face, built for the front (-Z): diesel at x -hx (the customer's right), gasoline at +hx (left). */
function face(P, faceName) {
  const Q = (name, bone, mat) => P(`${name}_${faceName}`, bone, mat);
  // hydraulic cabinet doors, a seam between them; each has a louvred vent (an opening onto the black cabinet, three slats
  // set a little behind the door face) and a lock
  const doors = Q('doors', 'body', 'paint'), vent = Q('vent_slats', 'body', 'paint');
  for (const s of [-1, 1]) {
    const x0 = s < 0 ? -7.5 : 0.12, x1 = s < 0 ? -0.12 : 7.5, cx = (x0 + x1) / 2;
    extrude(doors, 'z', shape(rect(x0, 4.6, x1, 9.8), [rect(cx - 2.4, 5.1, cx + 2.4, 7.5)]), -3.9, -3.73, 0);
    for (const y of [5.38, 6.08, 6.78]) slab(vent, 'z', [cx - 2.38, y, -3.86], [cx + 2.38, y + 0.38, -3.74], 0);
    cyl(Q(s < 0 ? 'lock_l' : 'lock_r', 'body', 'steel'), 'z', cx, 9.0, 0.2, -3.99, -3.88, 8);
  }
  // display head: one LCD (blank: the dispenser is dead) and one keypad with a card reader screen; the keypad at the
  // customer's right (-X on this face), the pair centred on the head
  extrude(Q('bezel', 'body', 'black'), 'z', shape(rect(-1.8, 23.9, 5.4, 28.1), [rect(-1.5, 24.2, 5.1, 27.8)]), -4.3, -4.1, 0);
  slab(Q('lcd', 'body', 'lcd'), 'z', [-1.5, 24.2, -4.2], [5.1, 27.8, -4.1], 0);
  slab(Q('keypad', 'body', 'black'), 'z', [-5.6, 23.7, -4.28], [-3.0, 28.3, -4.1], 0);
  slab(Q('card', 'body', 'screen'), 'z', [-5.25, 27.45, -4.33], [-3.35, 27.95, -4.26], 0);
  const keys = Q('keys', 'body', 'keys');
  for (let r = 0; r < 4; r++) for (let c = 0; c < 3; c++) {
    const x0 = -5.295 + c * 0.72, y0 = 24.1 + r * 0.72;
    slab(keys, 'z', [x0, y0, -4.4], [x0 + 0.55, y0 + 0.55, -4.26], 0);
  }
  // lamp box louvres (this face of it)
  slab(Q('grille', 'body', 'black'), 'z', [-4.0, 38.8, -2.27], [4.0, 40.3, -2.18], 0);
  const slats = Q('slats', 'body', 'paint');
  for (let i = 0; i < 4; i++) slab(slats, 'z', [-3.98, 38.92 + i * 0.36, -2.36], [3.98, 39.12 + i * 0.36, -2.25], 0);
  // the bay's face shell with the two recess openings (the core sits behind the deepest point of the recesses)
  extrude(Q('bay', 'body', 'black'), 'z', shape(rect(-7.9, 10.2, 7.9, 20.4), [-DIM.hx, DIM.hx].map(hx => rect(hx - HOLSTER.open[0], HOLSTER.open[1], hx + HOLSTER.open[0], HOLSTER.open[2]))), -3.9, -0.88, 0);
  for (const n of NOZZLES.filter(n => n.face === faceName)) {
    const hx = n.hx, H = HOLSTER, [w, oy0, oy1] = H.open, [[wy0, wz0], [wy1, wz1]] = H.wall, wallZ = y => wz0 + (y - wy0) / (wy1 - wy0) * (wz1 - wz0);
    const k = n.grade, bone = 'nozzle_' + n.id;
    // the holster: a framed recess whose back wall slopes (deeper at the top)
    sideX(Q('recess_' + k, 'body', 'black'), [[wz0, wy0], [wz0 + 0.25, wy0], [wz1 + 0.25, wy1], [wz1, wy1]], hx - w - 0.02, hx + w + 0.02, 0);
    extrude(Q('frame_' + k, 'body', 'frame'), 'z', shape(rect(hx - w - H.frame, oy0 - H.frame, hx + w + H.frame, oy1 + H.frame), [rect(hx - w, oy0, hx + w, oy1)]), -4.05, -3.9, 0);
    // the nozzle, hung head-on and tilted into the recess
    const xf = hungPose([hx, H.origin[0], H.origin[1]], H.tilt);
    nozzle(Q, 'nozzle_' + k, bone, n.mat, xf);
    let minGap = Infinity;
    for (const lz of [-0.7, 0, 0.5, 1, 1.5, 2, 2.5, 3, 3.5]) for (const ly of [-1.84, -1.6, -1.3, -1.0, -0.6]) {
      const q = xf([0, ly, lz]); if (q[1] > oy0 && q[1] < oy1) minGap = Math.min(minGap, wallZ(q[1]) - q[2]);
    }
    assert(minGap > 0.1, `${n.id}: the nozzle touches the recess wall (${minGap})`);
    // the boot at the top of the recess: from the back wall forward around the spout's upper part, a pocket open below
    const inBoot = spoutPath().filter(p => p[2] >= 5.35).map(xf), r = NOZZLE.spoutR + 0.12;
    const px0 = Math.min(...inBoot.map(p => p[0])) - r, px1 = Math.max(...inBoot.map(p => p[0])) + r;
    const pz0 = Math.min(...inBoot.map(p => p[2])) - r, pz1 = Math.max(...inBoot.map(p => p[2])) + r;
    const by0 = Math.min(...inBoot.map(p => p[1])), top = Math.max(...inBoot.map(p => p[1])) + NOZZLE.spoutR + 0.08, by1 = Math.min(oy1 - 0.02, top + 0.5);
    const boot = Q('boot_' + k, 'body', 'frame'), backZ = wallZ(by1) + 0.1;
    planY(boot, rect(px0 - 0.28, pz0 - 0.28, px1 + 0.28, backZ), by0, by1, 0, [rect(px0, pz0, px1, pz1)]);
    slab(boot, 'y', [px0, top, pz0], [px1, by1, pz1], 0);
    // the parked hose: from the outlet down outside the pillar, around the loop, into the swivel along the nozzle's axis
    const end = xf([0, 0, -0.4]), down = norm(sub(xf([0, 0, -0.7]), xf([0, 0, 0.3])));
    const sx = Math.sign(hx), ctrl = DIM.hose.slice(0, -2).map(([x, y, z]) => [x * -sx, y, z]).concat([add(end, mul(down, 1.5)), end]);
    tube(Q('hose_' + k, bone, 'rubber'), spline(ctrl, add(ctrl[0], [0, 4, 0]), sub(end, mul(down, 4))), DIM.hoseR, 8);
    const ox = DIM.outlet[0] * sx, oz = -DIM.outlet[1], fit = Q('outlet_' + k, 'body', 'steel');
    cyl(fit, 'y', oz, ox, 0.7, 40.05, 40.65, 6);    // nut
    cyl(fit, 'y', oz, ox, 0.55, 39.15, 40.1, 10);   // swivel
    assert(Math.abs(ctrl[0][0] - ox) < 1e-9 && Math.abs(ctrl[0][2] - oz) < 1e-9, `${n.id}: the parked hose does not start at its outlet`);
    const r4 = q => q.map(v => +v.toFixed(4)), a = facePoint(n, [hx - w - H.frame, oy0 - H.frame, -6.2]), b = facePoint(n, [hx + w + H.frame, oy1 + H.frame, -3.0]);
    NOZZLE_INFO[n.id] = {outlet: r4(facePoint(n, [ox, DIM.hoseStart, oz])), hood: r4(facePoint(n, xf([0, 0.6, 2.8]))),
      aim: [r4([0, 1, 2].map(i => Math.min(a[i], b[i]))), r4([0, 1, 2].map(i => Math.max(a[i], b[i])))]};
  }
}

// ---------------- bake: UV, LabPBR maps (with the live hose's rubber texels) ----------------
const DUMMY = 'data:image/png;base64,' + png(Buffer.from([0, 0, 0, 255]), 1, 1).toString('base64');
const HOSE_TEXELS = 16;
function bake() {
  const {PARTS: all, atlas, startS} = build(), PARTS = all.filter(p => p.f.length);
  for (const p of PARTS) {   // Forge draws a quad as 0-1-2 / 2-3-0: split anything that is not a triangle or a strictly convex quad
    const out = [];
    for (const f of p.f) {
      const P3 = f.ids.map(i => p.v[i]), n = newell(P3);
      const convexQuad = f.ids.length === 4 && [0, 1, 2, 3].every(k => dot(cross(sub(P3[(k + 1) % 4], P3[k]), sub(P3[(k + 2) % 4], P3[(k + 1) % 4])), n) > 1e-12);
      if (f.ids.length === 3 || convexQuad) { out.push(f); continue; }
      assert(f.ids.length === 4, `${f.ids.length}-gon in ${p.name}`);
      const k = [0, 1, 2, 3].find(k => dot(cross(sub(P3[k], P3[(k + 3) % 4]), sub(P3[(k + 1) % 4], P3[k])), n) <= 1e-12) ?? 0;
      out.push({...f, ids: [f.ids[k], f.ids[(k + 1) % 4], f.ids[(k + 2) % 4]]}, {...f, ids: [f.ids[(k + 2) % 4], f.ids[(k + 3) % 4], f.ids[k]]});
    }
    p.f = out;
  }
  const UV = unwrap(PARTS, {atlas, pad: 2, startS, stepS: 0.25});
  for (const p of PARTS) assert(p.f.every(f => UV.faceUV.get(p)?.has(f)), `unmapped face in ${p.name}`);
  const painted = paint({PARTS, islands: UV.islands, S: UV.S, uvOf: UV.uvOf, atlas, pad: 2, MATS, ZONED: new Set(), groupInfo: new Map(),
    sourceGroups: () => [], refTexture: {source: DUMMY}, refUvWidth: 1, background: {c: [35, 36, 39, 255], s: [74, 20, 0, 255], n: [128, 128, 255, 255]}});
  // the live hose's texels: a free HOSE_TEXELS block (no island or gutter), searched from the bottom-right corner
  const used = (x, y) => UV.islands.some(is => x < is.px + is.W + 4 && x + HOSE_TEXELS > is.px - 4 && y < is.py + is.H + 4 && y + HOSE_TEXELS > is.py - 4);
  let spot = null;
  for (let y = atlas - HOSE_TEXELS; y >= 0 && !spot; y -= HOSE_TEXELS) for (let x = atlas - HOSE_TEXELS; x >= 0; x -= HOSE_TEXELS) if (!used(x, y)) { spot = [x, y]; break; }
  assert(spot, 'no free texels for the live hose');
  const maps = painted.PNG.map((buf, i) => {
    const img = readPng(buf), px = Buffer.alloc(img.w * img.h * 4), fill = [RUBBER.c.concat(255), RUBBER.s, RUBBER.n][i];
    for (let k = 0; k < img.w * img.h; k++) for (let c = 0; c < 4; c++) px[k * 4 + c] = img.bpp === 4 ? img.px[k * 4 + c] : (c < 3 ? img.px[k * 3 + c] : 255);
    for (let y = spot[1]; y < spot[1] + HOSE_TEXELS; y++) for (let x = spot[0]; x < spot[0] + HOSE_TEXELS; x++) px.set(fill, (y * img.w + x) * 4);
    if (i === 1) for (const is of UV.islands.filter(is => is.part.mat === 'lensLit'))   // LabPBR emission: _s alpha 254 = full
      for (let y = is.py - 2; y < is.py + is.H + 2; y++) for (let x = is.px - 2; x < is.px + is.W + 2; x++) px[(y * img.w + x) * 4 + 3] = 254;
    return png(px, img.w, img.h);
  });
  const hoseUV = [spot[0] + 4, spot[1] + 4, spot[0] + HOSE_TEXELS - 4, spot[1] + HOSE_TEXELS - 4].map(v => +(v / atlas).toFixed(6));
  // coplanar overlaps only matter inside one model (the body and each nozzle are separate models; base joins body)
  const groups = [['base', 'body', 'lamp'], ['lamp_lit'], ...NOZZLES.map(n => ['nozzle_' + n.id]), ['item_gasoline'], ['item_diesel']];
  const coplanar = groups.flatMap(g => zFightLevels(PARTS.filter(p => g.includes(p.bone)), new Map()).unresolved);
  return {id: 'fuel_dispenser', PARTS, atlas, UV, maps, coplanar, hoseUV};
}

const uuidOf = (ns, s) => { const h = createHash('sha256').update(`afl-fuel-dispenser-v1:${ns}:${s}`).digest('hex'); return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-8${h.slice(17, 20)}-${h.slice(20, 32)}`; };
const r12 = v => +v.toFixed(12) || 0;
// editable Free Model source (frame as the header, px): one group per model
function sourceOf(b) {
  const uuid = s => uuidOf(b.id, s), name = b.id + '_v1';
  const bones = [...new Set(b.PARTS.map(p => p.bone))];
  const elements = b.PARTS.map(p => {
    const key = i => i.toString(36).padStart(4, '0'), vertices = {}, faces = {}, uvs = b.UV.faceUV.get(p);
    p.v.forEach((q, i) => { vertices[key(i)] = q.map(r12); });
    p.f.forEach((f, fi) => { const uv = uvs.get(f); faces['f' + key(fi)] = {uv: Object.fromEntries(f.ids.map((id, j) => [key(id), uv[j].map(r12)])), vertices: f.ids.map(key), texture: 0}; });
    return {name: p.name, color: 0, origin: [0, 0, 0], rotation: [0, 0, 0], shading: 'flat', export: true, visibility: true, locked: false,
      render_order: 'default', scope: 0, allow_mirror_modeling: true, vertices, faces, type: 'mesh', uuid: uuid('mesh:' + p.name)};
  });
  return {meta: {format_version: '5.0', model_format: 'free', box_uv: false}, name, model_identifier: '', visible_box: [2, 3, 0],
    variable_placeholders: '', variable_placeholder_buttons: [], timeline_setups: [], unhandled_root_fields: {}, resolution: {width: b.atlas, height: b.atlas},
    elements, groups: bones.map(bone => ({name: bone, uuid: uuid('group:' + bone), export: true, locked: false, scope: 0,
      selected: false, visibility: true, _static: {properties: {}, temp_data: {}}, origin: [0, 0, 0], rotation: [0, 0, 0],
      color: 0, children: [], reset: false, shade: true, mirror_uv: false, autouv: 0, isOpen: true, primary_selected: false})),
    outliner: bones.map(bone => ({uuid: uuid('group:' + bone), isOpen: true, children: elements.filter((e, i) => b.PARTS[i].bone === bone).map(e => e.uuid)})),
    textures: [{name: name + '.png', relative_path: `textures/${name}.png`, folder: '', namespace: '', id: '0', group: '', scope: 0,
      width: b.atlas, height: b.atlas, uv_width: b.atlas, uv_height: b.atlas, particle: false, use_as_default: false, layers_enabled: false, sync_to_project: '',
      file_format: 'png', render_mode: 'default', render_sides: 'auto', wrap_mode: 'limited', pbr_channel: 'color', fps: 7, frame_time: 1,
      frame_order_type: 'loop', frame_order: '', frame_interpolate: false, visible: true, internal: true, saved: true, uuid: uuid('texture'),
      source: 'data:image/png;base64,' + b.maps[0].toString('base64')}],
    animations: []};
}

// ---------------- OBJ / MTL / models ----------------
const f6 = v => (+v.toFixed(6)).toString();
const SMOOTH = Math.cos(36 * D2R), SMOOTH_TUBE = Math.cos(50 * D2R);   // hoses and spouts (8-sided tubes) shade round
function cornerNormals(V, F, smooth) {
  const fn = F.map(f => newell(f.ids.map(i => V[i]))), byV = new Map();
  F.forEach((f, k) => f.ids.forEach(i => (byV.get(i) || byV.set(i, []).get(i)).push(k)));
  return F.map((f, k) => { const n0 = norm(fn[k]); return f.ids.map(i => norm(byV.get(i).reduce((a, j) => dot(norm(fn[j]), n0) >= smooth ? add(a, fn[j]) : a, [0, 0, 0]))); });
}
// models: bones -> one OBJ; frame: 'cell' (block units from the master cell) or 'item' (the nozzle, centred, spout toward -Z)
const toCell = q => [(q[0] + 16) / 16, q[1] / 16, (q[2] + 8) / 16];
const NOZZLE_MID = (NOZZLE.swivel[1] + 6.42) / 2;   // the nozzle's length from the swivel's back to the spout tip, centred
const toItem = q => [-q[0] / 16 + 0.5, q[1] / 16 + 0.5, -(q[2] - NOZZLE_MID) / 16 + 0.5];   // turned 180 deg: the spout toward -Z
function objOf(b, title, file, bones, frame) {
  const out = [`# AFL ${title}, generated by tools/build-fuel-dispenser-v1.mjs`, `mtllib ${file.split('/').pop()}.mtl`];
  let vBase = 1, tBase = 1, nBase = 1;
  for (const p of b.PARTS) {
    if (!bones.has(p.bone)) continue;
    const V = p.v.map(frame === 'item' ? toItem : toCell), F = p.f;
    out.push(`o ${p.name}`, `usemtl ${b.id}`);
    for (const q of V) out.push(`v ${f6(q[0])} ${f6(q[1])} ${f6(q[2])}`);
    const tubeLike = /hose|steel/.test(p.name), uvs = b.UV.faceUV.get(p), vt = [], vn = [], fl = [], cn = cornerNormals(V, F, tubeLike ? SMOOTH_TUBE : SMOOTH), nIndex = new Map();
    F.forEach((f, k) => {
      const uv = uvs.get(f);
      fl.push('f ' + f.ids.map((id, j) => {
        const n = cn[k][j], key = n.map(f6).join(' ');
        if (!nIndex.has(key)) { nIndex.set(key, nBase + vn.length); vn.push('vn ' + key); }
        vt.push(`vt ${f6(uv[j][0] / b.atlas)} ${f6(1 - uv[j][1] / b.atlas)}`); return `${vBase + id}/${tBase + vt.length - 1}/${nIndex.get(key)}`;
      }).join(' '));
    });
    out.push(...vt, ...vn, ...fl);
    vBase += p.v.length; tBase += vt.length; nBase += vn.length;
  }
  return out.join('\n') + '\n';
}
const mtlOf = (b, title, glow = false) => `# AFL ${title}\nnewmtl ${b.id}\nKd 1 1 1\n${glow ? 'Ka 1 1 1\n' : ''}map_Kd apocalypse_firstlight:block/${b.id}\n`;
const objModel = file => ({loader: 'forge:obj', model: `apocalypse_firstlight:models/block/${file}.obj`, automatic_culling: false,
  flip_v: true, shade_quads: true, ambientocclusion: false, textures: {particle: 'apocalypse_firstlight:block/fuel_dispenser'}});
const r3 = v => +v.toFixed(3) || 0;
const S3 = v => [v, v, v];
// GUI framing: centre the model's projection under the GUI rotation (model units, block-model space)
function guiCentred(points, rotation, scale) {
  const [ax, ay] = rotation.map(v => v * D2R);
  const rot = q => { const x = q[0] * Math.cos(ay) + q[2] * Math.sin(ay), z = -q[0] * Math.sin(ay) + q[2] * Math.cos(ay); return [x, q[1] * Math.cos(ax) - z * Math.sin(ax)]; };
  const pts = points.map(q => rot([q[0] - 8, q[1] - 8, q[2] - 8]));
  const cx = (Math.max(...pts.map(q => q[0])) + Math.min(...pts.map(q => q[0]))) / 2, cy = (Math.max(...pts.map(q => q[1])) + Math.min(...pts.map(q => q[1]))) / 2;
  return {rotation, translation: [r3(-scale * cx), r3(-scale * cy), 0], scale: S3(scale)};
}
function dispenserDisplay(b, bones) {
  const pts = b.PARTS.filter(p => bones.has(p.bone)).flatMap(p => p.v.map(q => toCell(q).map(v => v * 16)));
  return {gui: guiCentred(pts, [30, 225, 0], 0.27), ground: {translation: [0, 2, 0], scale: S3(0.16)}, fixed: {rotation: [0, 180, 0], translation: [0, -2, 0], scale: S3(0.28)},
    thirdperson_righthand: {rotation: [75, 45, 0], translation: [0, 2.5, 0], scale: S3(0.18)}, thirdperson_lefthand: {rotation: [75, 45, 0], translation: [0, 2.5, 0], scale: S3(0.18)},
    firstperson_righthand: {rotation: [0, 45, 0], translation: [0, 1.5, 0], scale: S3(0.22)}, firstperson_lefthand: {rotation: [0, 225, 0], translation: [0, 1.5, 0], scale: S3(0.22)}};
}
// the nozzle in the hand: the spout forward (into the screen) and a little down, the hose leaving the grip's back end
function nozzleDisplay(b, bone) {
  const pts = b.PARTS.filter(p => p.bone === bone).flatMap(p => p.v.map(q => toItem(q).map(v => v * 16)));
  return {gui: guiCentred(pts, [20, 120, 0], 1.55), ground: {translation: [0, 2, 0], scale: S3(0.8)}, fixed: {rotation: [0, 90, 0], scale: S3(1.2)},
    // fitted 2026-10-04 through the vanilla held-item chains (ItemInHandLayer / ItemInHandRenderer): third person the
    // spout points ahead, a little down, the grip in the fist; first person the whole nozzle shows at the lower right
    thirdperson_righthand: {rotation: [65, 0, 0], translation: [0, 1.0, 0.5], scale: S3(0.8)}, thirdperson_lefthand: {rotation: [65, 0, 0], translation: [0, 1.0, 0.5], scale: S3(0.8)},
    firstperson_righthand: {rotation: [0, 6, 0], translation: [0.5, 4.2, -1.0], scale: S3(0.85)}, firstperson_lefthand: {rotation: [0, -6, 0], translation: [0.5, 4.2, -1.0], scale: S3(0.85)}};
}

// ---------------- blockstate ----------------
const DIRS = ['north', 'east', 'south', 'west'], ROT = {north: 0, east: 90, south: 180, west: 270};
const ref = (model, facing) => ({model: `apocalypse_firstlight:block/${model}`, ...(ROT[facing] ? {y: ROT[facing]} : {})});
export const CELLS = ['a0', 'b0', 'a1', 'b1', 'a2', 'b2'];   // FuelDispenserBlock.Cell: a = the master column, b its clockwise neighbour
function blockstate() {
  const parts = [];
  for (const F of DIRS) {
    parts.push({when: {facing: F, cell: 'a0'}, apply: ref('fuel_dispenser/body', F)});
    parts.push({when: {facing: F, cell: 'a0', lit: 'false'}, apply: ref('fuel_dispenser/lamp', F)});
    parts.push({when: {facing: F, cell: 'a0', lit: 'true'}, apply: ref('fuel_dispenser/lamp_lit', F)});
    for (const n of NOZZLES) parts.push({when: {facing: F, cell: 'a0', [n.id]: 'true'}, apply: ref('fuel_dispenser/nozzle_' + n.id, F)});
  }
  parts.push({when: {cell: CELLS.slice(1).join('|')}, apply: {model: 'apocalypse_firstlight:block/fuel_dispenser/cell'}});
  return {multipart: parts};
}

// ---------------- write ----------------
const B = bake();
const bbDir = path.join(ROOT, 'src/main/blockbench'), assets = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight');
const json = v => JSON.stringify(v, null, 2) + '\n';
const outputs = [], objs = [];
const T = 'Fuel Dispenser V1';
const model = (file, title, bones, frame = 'cell', glow = false) => {
  const obj = objOf(B, title, file, bones, frame);
  outputs.push([path.join(assets, `models/block/${file}.obj`), obj], [path.join(assets, `models/block/${file}.mtl`), mtlOf(B, title, glow)],
    [path.join(assets, `models/block/${file}.json`), json(objModel(file))]);
  objs.push([file, obj]);
};
outputs.push([path.join(bbDir, 'fuel_dispenser_v1.bbmodel'), JSON.stringify(sourceOf(B))],
  ...['', '_s', '_n'].flatMap((k, i) => [[path.join(bbDir, `textures/fuel_dispenser_v1${k}.png`), B.maps[i]], [path.join(assets, `textures/block/fuel_dispenser${k}.png`), B.maps[i]]]));
const only = (...bones) => new Set(bones), DISPENSER = only('base', 'body', 'lamp', ...NOZZLES.map(n => 'nozzle_' + n.id));
model('fuel_dispenser/body', T + ' body', only('base', 'body'));
model('fuel_dispenser/lamp', T + ' lamp lens (unlit)', only('lamp'));
model('fuel_dispenser/lamp_lit', T + ' lamp lens (lit)', only('lamp_lit'), 'cell', true);
for (const n of NOZZLES) model('fuel_dispenser/nozzle_' + n.id, `${T} ${n.face} ${n.grade} nozzle and parked hose`, only('nozzle_' + n.id));
model('fuel_dispenser/item', T + ' item', DISPENSER);
for (const g of ['gasoline', 'diesel']) model('fuel_dispenser/held_' + g, `${T} ${g} nozzle (held)`, only('item_' + g), 'item');
outputs.push([path.join(assets, 'models/block/fuel_dispenser/cell.json'), json({textures: {particle: 'apocalypse_firstlight:block/fuel_dispenser'}})]);
outputs.push([path.join(assets, 'models/item/fuel_dispenser.json'), json({parent: 'apocalypse_firstlight:block/fuel_dispenser/item', gui_light: 'side', display: dispenserDisplay(B, DISPENSER)})]);
for (const g of ['gasoline', 'diesel']) outputs.push([path.join(assets, `models/item/fuel_nozzle_${g}.json`),
  json({parent: `apocalypse_firstlight:block/fuel_dispenser/held_${g}`, gui_light: 'side', display: nozzleDisplay(B, 'item_' + g)})]);
outputs.push([path.join(assets, 'blockstates/fuel_dispenser.json'), json(blockstate())]);

const tris = bones => B.PARTS.filter(p => bones.has(p.bone)).reduce((s, p) => s + p.f.reduce((t, f) => t + f.ids.length - 2, 0), 0);
const boundsOf = bones => { const all = B.PARTS.filter(p => bones.has(p.bone)).flatMap(p => p.v); return [0, 1, 2].map(k => [r3(Math.min(...all.map(q => q[k]))), r3(Math.max(...all.map(q => q[k])))]); };
export const stats = {
  triangles: {body: tris(only('base', 'body')), ...Object.fromEntries(NOZZLES.map(n => [n.id, tris(only('nozzle_' + n.id))])), held: tris(only('item_gasoline'))},
  bounds: boundsOf(DISPENSER), heldBounds: boundsOf(only('item_gasoline')),
  atlas: {size: B.atlas, texelsPerPx: B.UV.S, islands: B.UV.islands.length, coplanar: B.coplanar.length, hoseUV: B.hoseUV},
  heldSwivel: [0.5, 0.5, r3((0.4 + NOZZLE_MID) / 16 + 0.5)], nozzles: NOZZLE_INFO, itemGui: dispenserDisplay(B, DISPENSER).gui,
};
// the renderer samples the live hose's rubber from these texels: keep its constant in step
const RENDERER = path.join(ROOT, 'src/main/java/com/antaurora/apofirstlight/client/FuelDispenserRenderer.java');
const hoseUvLine = `HOSE_UV = {${B.hoseUV.map(v => v + 'F').join(', ')}}`;
const isMain = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  console.log(JSON.stringify(stats));
  if (B.coplanar.length) console.log('COPLANAR', JSON.stringify(B.coplanar.slice(0, 8)));
  const pi = process.argv.indexOf('--preview');
  if (pi > 0) {
    const dir = process.argv[pi + 1]; fs.mkdirSync(dir, {recursive: true});
    for (const [file, obj] of objs) fs.writeFileSync(path.join(dir, file.replace('/', '_') + '.obj'), obj);
    ['', '_s', '_n'].forEach((k, i) => fs.writeFileSync(path.join(dir, `${B.id}${k}.png`), B.maps[i]));
    console.log('preview written to ' + dir);
  } else if (process.argv.includes('--check')) {
    for (const [file, data] of outputs) { const cur = fs.existsSync(file) ? fs.readFileSync(file) : null; if (!cur || !cur.equals(Buffer.isBuffer(data) ? data : Buffer.from(data))) throw new Error('stale ' + path.relative(ROOT, file)); }
    if (!fs.readFileSync(RENDERER, 'utf8').includes(hoseUvLine)) throw new Error('FuelDispenserRenderer: expected ' + hoseUvLine);
    console.log('CHECK OK');
  } else {
    for (const [file] of outputs) fs.mkdirSync(path.dirname(file), {recursive: true});
    for (const [file, data] of outputs) fs.writeFileSync(file, data);
    console.log('wrote ' + outputs.length + ' files; renderer constant: ' + hoseUvLine);
  }
}
