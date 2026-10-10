// Diesel standby generator V1 (docs/machines/diesel_standby_generator_v1.md): a canopy ("silent") standby genset of about
// 100 kW on a double-wall sub-base fuel tank, 3 x 1 x 2 cells, Pure Mesh + LabPBR (AFL Animated Block Mesh Runtime). The
// user approved concept v3 on 2026-10-09 ("就这样做吧"): warm white canopy, the analogue gauge panel with English legends,
// the standard steel power port at the master cell's back face centre, fuel poured into a lockable fill box on the tank
// step.
//   canopy     corner posts, belt rails, roof with drip lip, lifting eyes, radiator fill hatch, exhaust stack with its rain
//              flap (channel 'flap', open while running), a framed outlet louvre at the radiator end, a weather hood at the
//              intake end, acoustic foam on every inner face
//   doors      two front service doors (channel 'doors', 100 degrees out): embossed panel, three exposed hinges, a
//              compression latch with a T handle; the left one an intake hood, the right one the instrument panel and the
//              emergency stop, its control box on the inside
//   panel      printed face (tools/draw-diesel-generator-panel-v1.mjs, pasted at ~68 texels per px into a reserved corner
//              of the atlas), chrome bezels with gauge glasses, four needles (value channels 'load', 'fuel', 'temp',
//              'oil'), the hour meter's five drums (value channels 'h0'..'h4', a drum turns 36 degrees a digit), three
//              lamps (unlit and lit parts, the lit ones emissive), the key switch (value channel 'key': OFF 0, RUN 0.53,
//              START 1), START / STOP buttons
//   engine bay radiator with shroud and fan, engine (sump, block, head, valve cover, timing cover, pulleys and belt,
//              charging alternator, filters, dipstick, starter), exhaust manifold, turbo, bellows, muffler under the roof,
//              air filter and intake hose, coolant hoses, flywheel housing, the ribbed generator (alternator) with its end
//              bell and terminal box, the output cable to the connection box, battery, skid rails and rubber mounts
//   tank       fork pockets through the tank, the step at the intake end with the fill box (lid on channel 'fill'), vent
//              gooseneck and level sender, mechanical level gauge (its needle on 'fuel' too), leak check plug, drain plug,
//              earth bar
//   back       plain wall with two intake hoods and the connection box carrying the standard power port
// Frames. Built in concept px: x along the unit to the right as seen from the front, y up, z toward the front (the
// viewer), origin at the footprint's centre on the ground; written to the rig's model frame (as the other mesh blocks):
// origin the master cell's (c1r0, the middle column) bottom centre, x east, y up, z south, drawn facing north:
// model = [-(x - XC), y, -z] (a half turn about y). The power port is built in the model frame directly.
//   node tools/build-diesel-generator-v1.mjs                -> writes models, blockstate, rig, atlas, sources
//   node tools/build-diesel-generator-v1.mjs --check        -> verifies every output is up to date
//   node tools/build-diesel-generator-v1.mjs --preview DIR  -> writes only OBJ (doors open / closed) + maps into DIR
import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {Part, extrude, revolve, unwrap, paint, png, readPng, zFightLevels, area2, add, sub, mul, dot, cross, norm, newell, M4} from './cube-slab-mesh-lib.mjs';
import {convert, serializeCompact} from './export-afl-mesh.mjs';
import {heldDisplay} from './item-held-display.mjs';
import {addPowerPort} from './afl-power-port.mjs';
import {addFluidPort, FLUID_PORT} from './afl-fluid-port.mjs';
import {PANEL, OUT as PANEL_ART, STRIP, SCALE as ART_SCALE} from './draw-diesel-generator-panel-v1.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
function assert(c, m) { if (!c) throw new Error(m); }
const K = 16, D2R = Math.PI / 180;
const r3 = v => +v.toFixed(3) || 0, r6 = v => +v.toFixed(6) || 0, r12 = v => +v.toFixed(12) || 0;
const NS = 'apocalypse_firstlight:block/', ID = 'diesel_generator';
const json = v => JSON.stringify(v, null, 2) + '\n';

// =============================== dimensions (metres, concept frame) ===============================
export const XC = 0.125 * K;   // concept px x of the master cell's centre line (the port)
export const G = {
  T: 0.30,                                                    // the tank's top (the canopy's floor)
  tank: {x0: -1.2, x1: 1.45, z: 0.47, lip: 0.015, top: 0.28},
  pockets: {x: [-0.55, 0.55], w: 0.34, y0: 0.03, y1: 0.15},
  canopy: {x: 1.2, z: 0.44, top: 1.55, wall: 0.02, post: 0.06, postOut: 0.01},
  roof: {y1: 1.59, x: 1.235, z: 0.475},
  opening: {x0: 0.02, x1: 1.066, y0: 0.364, y1: 1.436},       // each door opening (right; the left one mirrored), 5 mm gaps
  door: {x0: 0.025, x1: 1.061, y0: 0.369, y1: 1.431, z0: 0.417, z1: 0.442, hingeZ: 0.452, hinges: [0.49, 0.90, 1.31], degrees: 100, ticks: 16},
  plate: {x0: 0.41, y1: 1.324, front: 0.449, back: 0.446},    // the instrument plate on the right door (its top left corner)
  estop: {x: 0.64, y: 0.80},
  fill: {x0: 1.235, x1: 1.415, z0: 0.03, z1: 0.33, y1: 0.42, lid: 0.012, degrees: 105, ticks: 10},
  stack: {x: -0.82, z: -0.25, r: 0.05, top: 1.80, flapDegrees: 60, ticks: 12},
  connection: {x0: -0.12, x1: 0.37, y0: 0.302, y1: 0.72, z: -0.47},
  // the fuel supply box on the radiator end, under the louvre: from the fluid port plate's back (model x 24 - 0.6 px) to
  // 1 cm into the end wall and the tank's end (2026-10-09 user: not at the step end, where it crowded the fill box)
  fuelBox: {x0: (2 - 24 + 0.6) / 16, x1: -1.185, y0: 0.125, y1: 0.86, z: 0.375},   // x1 clear of the tank lip's inner face (-1.19)
  intakeEnd: {z: 0.35, y0: 0.78, y1: 1.30, depth: 0.05},
  radiatorEnd: {z: 0.36, y0: 0.92, y1: 1.42},   // the outlet louvre; below it a solid panel with the fuel connection (2026-10-09)
};
const PU = PANEL.metres / PANEL.w;   // metres per panel unit
/** Panel units (x right, y down from the plate's top left) -> concept metres on the plate's front plane. */
const pan = (u, v) => [G.plate.x0 + u * PU, G.plate.y1 - v * PU];

// =============================== builders (inputs in metres, geometry in px) ===============================
const PARTS = [], byName = new Map();
function P(name, bone, mat, opts = {}) { assert(!byName.has(name), 'duplicate part ' + name); const p = new Part(name, bone, mat); Object.assign(p, opts); PARTS.push(p); byName.set(name, p); return p; }
const m = v => v * K;
const orient = (L, ccw) => (area2(L) > 0) === ccw ? L : L.slice().reverse();
const shapeOf = (outer, holes = []) => ({outer: orient(outer, true), holes: holes.map(h => orient(h, false))});
const rect = (a0, b0, a1, b1) => [[a0, b0], [a1, b0], [a1, b1], [a0, b1]];
const circle = (ca, cb, r, seg, ph = Math.PI / seg) => Array.from({length: seg}, (_, i) => [ca + r * Math.cos(ph + 2 * Math.PI * i / seg), cb + r * Math.sin(ph + 2 * Math.PI * i / seg)]);
/** Extrusion along y: outline [[x, z]], y0..y1. */
const slabY = (part, outline, y0, y1, ch = 0, holes = []) => extrude(part, 'y', shapeOf(outline.map(([x, z]) => [m(z), m(x)]), holes.map(h => h.map(([x, z]) => [m(z), m(x)]))), m(y0), m(y1), m(ch));
/** Extrusion along z: outline [[x, y]], z0..z1. */
const slabZ = (part, outline, z0, z1, ch = 0, holes = []) => extrude(part, 'z', shapeOf(outline.map(([x, y]) => [m(x), m(y)]), holes.map(h => h.map(([x, y]) => [m(x), m(y)]))), m(z0), m(z1), m(ch));
/** Extrusion along x: outline [[z, y]], x0..x1. */
const slabX = (part, outline, x0, x1, ch = 0, holes = []) => extrude(part, 'x', shapeOf(outline.map(([z, y]) => [m(z), m(y)]), holes.map(h => h.map(([z, y]) => [m(z), m(y)]))), m(x0), m(x1), m(ch));
// the chamfer stays under a third of the thinnest side (the extrusion only clamps it along its own axis)
const box = (part, a, b, ch = 0) => slabY(part, rect(a[0], a[2], b[0], b[2]), a[1], b[1], Math.min(ch, ...[0, 1, 2].map(k => Math.abs(b[k] - a[k]) / 3)));
/**
 * A rectangular frame as abutting bars: the full-width bottom and top, the sides between them, and optional mullions
 * ([a0, a1] spans along the first axis). slab: slabX / slabY / slabZ; outer and inner [a0, b0, a1, b1] in its outline
 * axes. A ring extruded in one piece packs as one UV island as big as the whole frame, nearly all of it empty.
 */
function frameBars(part, slab, outer, inner, s0, s1, ch = 0, mullions = []) {
  // the sides stand 0.3 mm back from both faces of the bands: bars that met them flush would share edges four ways
  const [a0, b0, a1, b1] = outer, [i0, j0, i1, j1] = inner, e = 0.0003;
  slab(part, rect(a0, b0, a1, j0), s0, s1, ch); slab(part, rect(a0, j1, a1, b1), s0, s1, ch);
  slab(part, rect(a0, j0, i0, j1), s0 + e, s1 - e, ch); slab(part, rect(i1, j0, a1, j1), s0 + e, s1 - e, ch);
  for (const [m0, m1] of mullions) slab(part, rect(m0, j0, m1, j1), s0 + e, s1 - e, ch);
}
/** Rigid transform of everything build() adds to part: f(q) on each new vertex (q in px). Proper rotations only. */
function placed(part, build, f) { placedAll([part], build, f); }
/** The same for several parts filled by one build(). */
function placedAll(parts, build, f) { const n0 = parts.map(p => p.v.length); build(); parts.forEach((p, k) => { for (let i = n0[k]; i < p.v.length; i++) p.v[i] = f(p.v[i]); }); }
const rotAbout = (o, e) => { const M = M4.about(o.map(m), e); return q => M4.pt(M, q); };
/**
 * Solid of revolution about an axis ('x' | 'y' | 'z') through c (metres): outline [[s, r]] along the axis (s from the
 * start cap to the end cap, absolute along the axis, r > 0), closed through the axis at both ends; or a ring when inner
 * radii are given ([[s, rOuter, rInner]]: the bore through).
 */
function lathe(part, axis, c, outline, seg = 24) {
  const ring = outline[0].length === 3, s0 = outline[0][0], s1 = outline[outline.length - 1][0];
  // the (s, r) loop: a ring's bore side then its outside back; a solid's axis then its outline back
  const loop = ring ? [...outline.map(([s, , ri]) => [s, ri]), ...outline.slice().reverse().map(([s, ro]) => [s, ro])]
    : [[s0, 0], [s1, 0], ...outline.slice().reverse()];
  const prof = loop.map(([s, r]) => [m(s), m(r)]);
  if (area2(prof) < 0) prof.reverse();   // revolve wants it counter-clockwise
  const C = c.map(m);
  // revolve builds about local z; local (x, y, z) -> the axis (proper turns: x = (z, y, -x), y = (x, z, -y))
  placed(part, () => revolve(part, 0, 0, prof, seg), q => axis === 'z' ? [C[0] + q[0], C[1] + q[1], q[2]]
    : axis === 'x' ? [q[2], C[1] + q[1], C[2] - q[0]] : [C[0] + q[0], q[2], C[2] - q[1]]);
}
/** A ring from a raw (s, r) loop (any orientation). */
function latheLoop(part, axis, c, loop, seg) {
  const prof = loop.map(([s, r]) => [m(s), m(r)]); if (area2(prof) < 0) prof.reverse();
  const C = c.map(m);
  placed(part, () => revolve(part, 0, 0, prof, seg), q => axis === 'z' ? [C[0] + q[0], C[1] + q[1], q[2]]
    : axis === 'x' ? [q[2], C[1] + q[1], C[2] - q[0]] : [C[0] + q[0], q[2], C[2] - q[1]]);
}
/** A bezel ring on the panel: bore ri, outside ro, h high, its top falling to ro2. */
const bezel = (part, c, ri, ro, ro2, h, seg) => latheLoop(part, 'z', c, [[PZ, ri], [PZ + h, ri], [PZ + h, ro2], [PZ, ro]], seg);
/** Cylinder (with a small edge chamfer) along an axis through c, from s0 to s1 (absolute along the axis). */
function cyl(part, axis, c, r, s0, s1, seg = 20, ch = 0) {
  const c0 = Math.min(ch, r / 3, Math.abs(s1 - s0) / 3), d = Math.sign(s1 - s0);
  lathe(part, axis, c, c0 > 0 ? [[s0, r - c0], [s0 + d * c0, r], [s1 - d * c0, r], [s1, r - c0]] : [[s0, r], [s1, r]], seg);
}
/** Ring (annulus) along an axis through c: radii ri..ro from s0 to s1. */
const annulus = (part, axis, c, ri, ro, s0, s1, seg = 24) => lathe(part, axis, c, [[s0, ro, ri], [s1, ro, ri]], seg);
/** A tube of radius r along a path of points (metres); closed loops join their ends, open ones get flat caps. */
function sweep(part, pts, r, seg = 10, closed = false) {
  const P3 = pts.map(q => q.map(m)), R = m(r), n = P3.length;
  const tan = i => { const a = P3[closed ? (i - 1 + n) % n : Math.max(0, i - 1)], b = P3[closed ? (i + 1) % n : Math.min(n - 1, i + 1)]; return norm(sub(b, a)); };
  let ref = Math.abs(tan(0)[1]) < 0.9 ? [0, 1, 0] : [1, 0, 0];
  const rings = [];
  for (let i = 0; i < n; i++) {
    const t = tan(i), u = norm(sub(ref, mul(t, dot(ref, t)))), w = cross(t, u); ref = u;   // parallel transport
    rings.push(Array.from({length: seg}, (_, j) => { const a = 2 * Math.PI * j / seg; return part.vtx(add(P3[i], add(mul(u, R * Math.cos(a)), mul(w, R * Math.sin(a))))); }));
  }
  const segs = closed ? n : n - 1;
  for (let i = 0; i < segs; i++) { const A = rings[i], B = rings[(i + 1) % n], c = mul(add(P3[i], P3[(i + 1) % n]), 0.5);
    for (let j = 0; j < seg; j++) { const k = (j + 1) % seg, q = [A[j], A[k], B[k], B[j]], mid = q.map(id => part.v[id]).reduce((s, p) => add(s, mul(p, 0.25)), [0, 0, 0]);
      part.face(q, sub(mid, c), 'side'); } }
  if (!closed) for (const [i, s] of [[0, -1], [n - 1, 1]]) { const c = part.vtx(P3[i]), t = mul(tan(i), s);
    for (let j = 0; j < seg; j++) part.face([c, rings[i][j], rings[i][(j + 1) % seg]], t, 'cap'); }
}
/** Faces whose normal points along dir (> 0.9) take material mat (acoustic foam on inner faces). */
function retint(part, dir, mat, where = () => true) {
  dir = norm(dir);
  for (const f of part.f) { const P3 = f.ids.map(i => part.v[i]), n = norm(newell(P3)), c = P3.reduce((s, q) => add(s, mul(q, 1 / P3.length)), [0, 0, 0]);
    if (dot(n, dir) > 0.9 && where(c)) f.mat = mat; }
}
/** Mirrors x (x -> -x) of everything build() adds to these parts, keeping the faces' outsides out (the winding is reversed). */
function mirroredX(parts, build) { const st = parts.map(p => [p.v.length, p.f.length]); build();
  parts.forEach((p, k) => { for (let i = st[k][0]; i < p.v.length; i++) p.v[i] = [-p.v[i][0], p.v[i][1], p.v[i][2]];
    for (let i = st[k][1]; i < p.f.length; i++) p.f[i].ids = p.f[i].ids.slice().reverse(); }); }

// =============================== materials ===============================
const MAT = (c, hl, sm, se, f0 = 20) => ({c, hl, sm, se, f0});
const hash3 = (a, b, c) => { let h = Math.imul(a | 0, 374761393) ^ Math.imul(b | 0, 668265263) ^ Math.imul(c | 0, 2147483647); h = Math.imul(h ^ (h >>> 13), 1274126177); return ((h ^ (h >>> 16)) >>> 0) / 4294967295; };
const sm3 = t => t * t * (3 - 2 * t);
function vn3(x, y, z, seed) {
  const xi = Math.floor(x), yi = Math.floor(y), zi = Math.floor(z), tx = sm3(x - xi), ty = sm3(y - yi), tz = sm3(z - zi);
  const h = (i, j, k) => hash3(xi + i, yi + j + 977 * seed, zi + k), l = (a, b, t) => a + (b - a) * t;
  return l(l(l(h(0, 0, 0), h(1, 0, 0), tx), l(h(0, 1, 0), h(1, 1, 0), tx), ty), l(l(h(0, 0, 1), h(1, 0, 1), tx), l(h(0, 1, 1), h(1, 1, 1), tx), ty), tz) * 2 - 1;
}
// low-frequency tone drift only (no per-texel noise: memory, ground normal relief / texture artefacts)
const mottled = (base, k = 0.025, seed = 1) => pos => { const [x, y, z] = pos.map(v => v / K * 100); return {c: base.map(v => v * (1 + k * vn3(x / 60, y / 60, z / 60, seed) + k * 0.4 * vn3(x / 18, y / 18, z / 18, seed + 7)))}; };
export const MATS = {
  paint:      MAT(mottled([204, 199, 187], 0.02, 2), 10, 118, 140),   // warm white powder coat (RAL 9001-ish, kept below glare)
  trim:       MAT(mottled([186, 181, 169], 0.02, 3), 10, 112, 136),   // posts, rails, frames, hood shells
  foam:       MAT(mottled([50, 50, 52], 0.04, 4), 0, 34, 40),         // acoustic lining
  base:       MAT(mottled([52, 55, 58], 0.03, 5), 8, 104, 126),       // the tank and skid
  black:      MAT([28, 29, 30], 6, 92, 112),                          // gasket frame, rain hood, shroud, lock cylinder
  steel:      MAT(mottled([150, 152, 155], 0.02, 6), 14, 150, 176, 230),
  zinc:       MAT(mottled([158, 161, 158], 0.03, 7), 8, 96, 118, 230),
  chrome:     MAT([188, 190, 194], 18, 205, 220, 255),
  louvre:     MAT([34, 35, 37], 4, 70, 90),
  engine:     MAT(mottled([60, 80, 96], 0.03, 8), 10, 110, 132),
  engineDark: MAT(mottled([42, 54, 64], 0.03, 9), 8, 100, 120),
  castiron:   MAT(mottled([86, 84, 80], 0.05, 10), 8, 80, 100),
  alt:        MAT(mottled([58, 60, 63], 0.02, 11), 10, 96, 116),     // semi-gloss enamel
  rad:        MAT([24, 25, 26], 4, 70, 90),
  muffler:    MAT(mottled([118, 114, 106], 0.05, 12), 8, 80, 100),
  rubber:     MAT([22, 22, 23], 2, 40, 50),
  belt:       MAT([20, 20, 20], 2, 50, 60),
  copper:     MAT([176, 108, 62], 16, 160, 180, 234),
  battery:    MAT([32, 34, 36], 6, 100, 120),
  red:        MAT([178, 34, 28], 14, 150, 170),
  yellow:     MAT([222, 182, 40], 12, 140, 160),
  green:      MAT([40, 132, 58], 12, 150, 170),
  filter:     MAT([196, 190, 172], 10, 120, 140),
  blue:       MAT([44, 92, 150], 10, 130, 150),
  needle:     MAT([226, 92, 44], 0, 140, 140),
  brass:      MAT([186, 152, 84], 16, 170, 190, 255),
  paper:      MAT([226, 221, 208], 4, 110, 120),                      // the tank gauge's face
  lampG:      MAT([34, 64, 40], 6, 200, 210),
  lampA:      MAT([86, 62, 20], 6, 200, 210),
  lampR:      MAT([76, 26, 22], 6, 200, 210),
  lampG_lit:  MAT([140, 255, 150], 0, 200, 200),
  lampA_lit:  MAT([255, 200, 80], 0, 200, 200),
  lampR_lit:  MAT([255, 96, 72], 0, 200, 200),
  glass:      MAT([160, 180, 188], 0, 235, 235, 10),
  soot:       MAT([20, 19, 18], 0, 24, 30),                           // inside the exhaust stack
};
export const LIGHT_EMISSION = {lampG_lit: 220, lampA_lit: 220, lampR_lit: 230};   // LabPBR _s alpha
export const GLASS_ALPHA = 56;

// =============================== tank and step ===============================
const {T} = G, Tk = G.tank, GAUGE = [0.95, 0.17];   // the tank's level gauge (between the fork pocket and the earth bar)
{
  const holes = G.pockets.x.map(x => rect(x - G.pockets.w / 2, G.pockets.y0, x + G.pockets.w / 2, G.pockets.y1));
  slabZ(P('tank', 'body', 'base'), rect(Tk.x0, 0, Tk.x1, T), -Tk.z, Tk.z, 0.008, holes);
  // the top's welded lip: bars round the top edge, 1.5 cm out, 1 cm over the tank's bevelled edge, 2 mm above the top
  frameBars(P('tank_lip', 'body', 'base'), slabY, [Tk.x0 - Tk.lip, -Tk.z - Tk.lip, Tk.x1 + Tk.lip, Tk.z + Tk.lip], [Tk.x0 + 0.01, -Tk.z + 0.01, Tk.x1 - 0.01, Tk.z - 0.01], Tk.top - 0.01, T + 0.002);
  // fork pocket lips (galvanised channel ends) front and back
  G.pockets.x.forEach((x, i) => { for (const s of [1, -1]) {
    const o = rect(x - G.pockets.w / 2 - 0.02, G.pockets.y0 - 0.02, x + G.pockets.w / 2 + 0.02, G.pockets.y1 + 0.02), h = rect(x - G.pockets.w / 2, G.pockets.y0, x + G.pockets.w / 2, G.pockets.y1);
    slabZ(P(`pocket_lip_${i}_${s > 0 ? 'f' : 'b'}`, 'body', 'zinc'), o, s > 0 ? Tk.z : -Tk.z - 0.006, s > 0 ? Tk.z + 0.006 : -Tk.z, 0.002, [h]); } });
  // front: mechanical level gauge (its needle on the fuel channel), leak check plug, drain plug, earth bar
  annulus(P('tank_gauge_ring', 'body', 'steel'), 'z', [GAUGE[0], GAUGE[1], 0], 0.05, 0.06, Tk.z - 0.002, Tk.z + 0.016, 20);
  cyl(P('tank_gauge_face', 'body', 'paper'), 'z', [GAUGE[0], GAUGE[1], 0], 0.051, Tk.z - 0.003, Tk.z + 0.011, 20);
  cyl(P('leak_plug', 'body', 'steel'), 'z', [-0.9, 0.17, 0], 0.022, Tk.z - 0.002, Tk.z + 0.016, 6, 0.002);
  cyl(P('drain_plug', 'body', 'steel'), 'z', [-1.0, 0.045, 0], 0.016, Tk.z - 0.002, Tk.z + 0.014, 6, 0.002);
  const earth = P('earth_bar', 'body', 'copper'); box(earth, [1.15, 0.225, Tk.z - 0.002], [1.25, 0.25, Tk.z + 0.008], 0.002);
  for (const x of [1.17, 1.23]) cyl(P(`earth_bolt_${x}`, 'body', 'steel'), 'z', [x, 0.2375, 0], 0.007, Tk.z + 0.008, Tk.z + 0.016, 6);
  // the step: the fill box (open top, the filler neck inside), the lid's hasp staple, vent gooseneck, level sender
  const F = G.fill, wall = 0.006;
  slabY(P('fill_box', 'body', 'base'), rect(F.x0, F.z0, F.x1, F.z1), T, F.y1, 0.001, [rect(F.x0 + wall, F.z0 + wall, F.x1 - wall, F.z1 - wall)]);
  const nx = (F.x0 + F.x1) / 2, nz = (F.z0 + F.z1) / 2;
  annulus(P('filler_neck', 'body', 'steel'), 'y', [nx, 0, nz], 0.034, 0.042, T, F.y1 - 0.03, 18);
  cyl(P('filler_bore', 'body', 'black'), 'y', [nx, 0, nz], 0.0345, T - 0.002, F.y1 - 0.075, 18);
  box(P('hasp_staple', 'body', 'steel'), [nx - 0.012, F.y1 - 0.045, F.z1], [nx + 0.012, F.y1 - 0.02, F.z1 + 0.006], 0.001);
  sweep(P('vent_pipe', 'body', 'steel'), [[1.33, T - 0.005, -0.30], [1.33, 0.48, -0.30], ...Array.from({length: 7}, (_, i) => { const a = Math.PI * (i + 1) / 8;
    return [1.33 + 0.035 - 0.035 * Math.cos(a), 0.48 + 0.035 * Math.sin(a), -0.30]; }), [1.40, 0.48, -0.30], [1.40, 0.445, -0.30]], 0.016, 9);
  cyl(P('level_sender', 'body', 'zinc'), 'y', [1.30, 0, -0.08], 0.04, T, T + 0.04, 16, 0.003);
  cyl(P('level_gland', 'body', 'black'), 'y', [1.30, 0, -0.08], 0.012, T + 0.04, T + 0.06, 12);
}
// the tank gauge's needle (root part 'tank_needle', channel 'fuel': 180 degrees, E at the left)
{
  const n = P('tank_needle_mesh', 'tank_needle', 'red'), z0 = Tk.z + 0.012, z1 = Tk.z + 0.0135;
  const [gx, gy] = GAUGE;
  slabZ(n, [[gx + 0.008, gy - 0.003], [gx + 0.008, gy + 0.003], [gx - 0.042, gy + 0.0012], [gx - 0.042, gy - 0.0012]], z0, z1);
  cyl(P('tank_needle_hub', 'tank_needle', 'black'), 'z', [gx, gy, 0], 0.006, Tk.z + 0.011, Tk.z + 0.015, 12);
}

// =============================== canopy shell ===============================
const C = G.canopy, top = C.top;
{
  for (const sx of [-1, 1]) for (const sz of [-1, 1]) {   // corner posts, 10 mm proud of both faces
    const x0 = sx > 0 ? C.x - C.post + C.postOut : -C.x - C.postOut, z0 = sz > 0 ? C.z - C.post + C.postOut : -C.z - C.postOut;
    // 2 mm into the tank plate and the roof: no end face in the walls' or rails' planes
    box(P(`post_${sx > 0 ? 'e' : 'w'}${sz > 0 ? 'f' : 'b'}`, 'body', 'trim'), [x0, T - 0.002, z0], [x0 + C.post, top + 0.002, z0 + C.post], 0.004);
  }
  const inX = C.x - C.post + C.postOut + 0.025, inZ = C.z - C.post + C.postOut + 0.025;   // walls end inside the posts
  // front frame: the two door openings and the centre mullion
  const O = G.opening, front = P('front_frame', 'body', 'paint');
  frameBars(front, slabZ, [-inX, T, inX, top], [-O.x1, O.y0, O.x1, O.y1], C.z - C.wall, C.z, 0, [[-O.x0, O.x0]]);
  retint(front, [0, 0, -K], 'foam');
  // the door seals: black rubber behind the gaps (seen through them, and round the opening when a door is open)
  for (const sx of [1, -1]) { const x0 = sx > 0 ? O.x0 : -O.x1, x1 = sx > 0 ? O.x1 : -O.x0;
    frameBars(P(`door_seal_${sx > 0 ? 'r' : 'l'}`, 'body', 'rubber'), slabZ, [x0 - 0.008, O.y0 - 0.008, x1 + 0.008, O.y1 + 0.008], [x0 + 0.012, O.y0 + 0.012, x1 - 0.012, O.y1 - 0.012], C.z - C.wall - 0.012, C.z - C.wall - 0.0035); }
  // back wall with two hood openings
  const back = P('back_wall', 'body', 'paint'), bh = [rect(-0.92, 0.95, -0.18, 1.23), rect(0.18, 0.95, 0.92, 1.23)];
  slabZ(back, rect(-inX, T, inX, top), -C.z, -C.z + C.wall, 0.002, bh);
  retint(back, [0, 0, K], 'foam');
  // end walls: the radiator louvre opening (-x), the intake hood opening (+x)
  const RE = G.radiatorEnd, IE = G.intakeEnd;
  const west = P('end_wall_radiator', 'body', 'paint'); frameBars(west, slabX, [-inZ, T, inZ, top], [-RE.z, RE.y0, RE.z, RE.y1], -C.x, -C.x + C.wall); retint(west, [K, 0, 0], 'foam');
  const east = P('end_wall_intake', 'body', 'paint'); frameBars(east, slabX, [-inZ, T, inZ, top], [-IE.z + 0.02, IE.y0 + 0.02, IE.z - 0.02, IE.y1 - 0.02], C.x - C.wall, C.x); retint(east, [-K, 0, 0], 'foam');
  // belt rails, bottom and top, 6 mm proud (die into the posts)
  for (const [y0, y1, nm] of [[T, 0.355, 'low'], [1.48, top, 'high']]) {
    box(P(`rail_${nm}_f`, 'body', 'trim'), [-C.x + 0.03, y0, C.z], [C.x - 0.03, y1, C.z + 0.006], 0.003);
    box(P(`rail_${nm}_b`, 'body', 'trim'), [-C.x + 0.03, y0, -C.z - 0.006], [C.x - 0.03, y1, -C.z], 0.003);
    box(P(`rail_${nm}_w`, 'body', 'trim'), [-C.x - 0.006, y0, -C.z + 0.03], [-C.x, y1, C.z - 0.03], 0.003);
    box(P(`rail_${nm}_e`, 'body', 'trim'), [C.x, y0, -C.z + 0.03], [C.x + 0.006, y1, C.z - 0.03], 0.003);
  }
  // roof, drip lip (a frame under the overhang), foam ceiling inside
  const R = G.roof;
  box(P('roof', 'body', 'paint'), [-R.x, top, -R.z], [R.x, R.y1, R.z], 0.006);
  frameBars(P('drip_lip', 'body', 'trim'), slabY, [-R.x - 0.007, -R.z - 0.007, R.x + 0.007, R.z + 0.007], [-C.x + 0.01, -C.z + 0.01, C.x - 0.01, C.z - 0.01], top - 0.005, top + 0.006);
  box(P('ceiling_foam', 'body', 'foam'), [-C.x + C.wall, top - 0.025, -C.z + C.wall], [C.x - C.wall, top + 0.0035, C.z - C.wall], 0);
  // lifting eyes, radiator fill hatch, exhaust collar
  for (const x of [-1.1, 1.1]) for (const z of [-0.38, 0.38]) {
    const nm = `${x > 0 ? 'e' : 'w'}${z > 0 ? 'f' : 'b'}`;
    box(P(`eye_base_${nm}`, 'body', 'trim'), [x - 0.045, R.y1, z - 0.02], [x + 0.045, R.y1 + 0.03, z + 0.02], 0.004);
    sweep(P(`eye_${nm}`, 'body', 'steel'), Array.from({length: 12}, (_, i) => { const a = 2 * Math.PI * i / 12; return [x + 0.032 * Math.cos(a), R.y1 + 0.06 + 0.032 * Math.sin(a), z]; }), 0.009, 6, true);
  }
  box(P('fill_hatch', 'body', 'trim'), [-1.13, R.y1, -0.05], [-0.89, R.y1 + 0.012, 0.25], 0.003);
  for (const [dx, dz] of [[-0.1, -0.12], [0.1, -0.12], [-0.1, 0.12], [0.1, 0.12]]) cyl(P(`hatch_screw_${dx}_${dz}`, 'body', 'steel'), 'y', [-1.01 + dx, 0, 0.10 + dz], 0.009, R.y1 + 0.012, R.y1 + 0.017, 8);
  const S = G.stack;
  annulus(P('stack_collar', 'body', 'zinc'), 'y', [S.x, 0, S.z], S.r + 0.002, S.r + 0.04, R.y1, R.y1 + 0.03, 20);
  // the stack: a 6 mm wall pipe, sooty inside and on its lip, a clamp band under the top, a soot-black plug 10 cm down so
  // the bore reads dark instead of seeing through (2026-10-09, user: "里面没有模型，只是一个圆柱")
  const stack = P('stack', 'body', 'muffler'), ri = S.r - 0.006;
  annulus(stack, 'y', [S.x, 0, S.z], ri, S.r, 1.50, S.top, 20);
  for (const f of stack.f) { const P3 = f.ids.map(i => stack.v[i]), n = norm(newell(P3)), c = P3.reduce((a, q) => add(a, mul(q, 1 / P3.length)), [0, 0, 0]);
    const rx = c[0] - m(S.x), rz = c[2] - m(S.z), rl = Math.hypot(rx, rz) || 1;
    if ((n[0] * rx + n[2] * rz) / rl < -0.5 || n[1] > 0.9 && c[1] > m(S.top) - 0.01) f.mat = 'soot'; }
  cyl(P('stack_plug', 'body', 'soot'), 'y', [S.x, 0, S.z], ri + 0.0005, S.top - 0.11, S.top - 0.10, 20);
  annulus(P('stack_band', 'body', 'steel'), 'y', [S.x, 0, S.z], S.r - 0.001, S.r + 0.006, S.top - 0.034, S.top - 0.02, 20);
}
// the stack's rain flap (root 'flap', channel 'flap': hinged at its back edge, lifts open while running)
{
  const S = G.stack, f = P('flap_plate', 'flap', 'castiron');
  slabY(f, circle(S.x, S.z + 0.002, S.r + 0.012, 20), S.top + 0.001, S.top + 0.007, 0.001);
  box(P('flap_knuckle', 'flap', 'castiron'), [S.x - 0.02, S.top + 0.0025, S.z - S.r - 0.016], [S.x + 0.02, S.top + 0.012, S.z - S.r - 0.004], 0.001);
  box(P('flap_weight', 'flap', 'castiron'), [S.x - 0.012, S.top + 0.003, S.z - S.r - 0.05], [S.x + 0.012, S.top + 0.02, S.z - S.r - 0.017], 0.002);
}

// =============================== end louvre (radiator) and intake hoods ===============================
{
  const RE = G.radiatorEnd, x0 = -C.x;
  // the frame's lip stands 3 mm into the wall's opening (its bore never lies in the opening's walls)
  frameBars(P('louvre_frame', 'body', 'trim'), slabX, [-RE.z - 0.02, RE.y0 - 0.02, RE.z + 0.02, RE.y1 + 0.02], [-RE.z + 0.003, RE.y0 + 0.003, RE.z - 0.003, RE.y1 - 0.003], x0 - 0.015, x0 + 0.002);
  slabX(P('louvre_mesh', 'body', 'louvre'), rect(-RE.z, RE.y0, RE.z, RE.y1), x0 + 0.005, x0 + 0.009);
  const n = 12, yLo = RE.y0 + 0.012, pitch = (RE.y1 - yLo) / (n - 1), sl = P('louvre_slats', 'body', 'trim');
  for (let i = 0; i < n; i++) { const y = yLo + pitch * i;   // each slat runs along z, its outer edge lower; the top one under the lip
    slabZ(sl, [[x0 + 0.004, y + 0.013], [x0 + 0.004, y + 0.018], [x0 - 0.013, y - 0.004], [x0 - 0.013, y - 0.009]], -RE.z, RE.z); }
}
/** A weather hood on a face whose outward normal is +z, built at the front and turned for the others. */
function hood(prefix, bone, cx, y0, y1, w, z0, depth, turn) {
  const x0 = cx - w / 2, x1 = cx + w / 2, d = depth, t = 0.006;
  const parts = [P(prefix + '_shell', bone, 'trim'), P(prefix + '_mesh', bone, 'louvre'), P(prefix + '_slats', bone, 'trim')];
  const build = () => {
    box(parts[0], [x0, y1 - t, z0], [x1, y1, z0 + d], 0.002);
    box(parts[0], [x0, y0, z0], [x0 + t, y1 - t / 2, z0 + d], 0.001); box(parts[0], [x1 - t, y0, z0], [x1, y1 - t / 2, z0 + d], 0.001);   // into the top, not touching it
    box(parts[1], [x0 + t, y0, z0], [x1 - t, y1 - t, z0 + 0.003]);
    const n = Math.max(3, Math.round((y1 - y0 - t) / 0.04)), p = (y1 - y0 - t) / n;
    for (let i = 0; i < n; i++) { const y = y0 + p * (i + 0.6);
      slabX(parts[2], [[z0 + 0.006, y + 0.016], [z0 + 0.006, y + 0.021], [z0 + d - 0.004, y - 0.002], [z0 + d - 0.004, y - 0.007]], x0 + t, x1 - t); }
  };
  placedAll(parts, build, turn);
}
const IE = G.intakeEnd;
// intake end (+x): the hood on the end wall (built facing +z, turned a quarter to face +x)
hood('intake_hood', 'body', 0, IE.y0, IE.y1, IE.z * 2, 0, IE.depth, q => [q[2] + m(C.x), q[1], -q[0]]);
// back wall hoods (built facing +z at the origin, turned a half about y to the back)
for (const [i, cx] of [[0, -0.55], [1, 0.55]]) {
  hood(`back_hood_${i}`, 'body', -cx, 0.95, 1.23, 0.74, 0, 0.045, q => [-q[0], q[1], -q[2] - m(C.z)]);
}

// =============================== doors ===============================
const D = G.door;
/** One door leaf with its emboss, hinges (door half) and latch, in the right door's place; side -1 mirrors it (left door). */
function door(side, bone) {
  const nm = side > 0 ? 'r' : 'l', build = fn => side > 0 ? fn() : null;
  const parts = {leaf: P(`door_${nm}_leaf`, bone, 'paint'), emboss: P(`door_${nm}_emboss`, bone, 'paint'), hw: P(`door_${nm}_hardware`, bone, 'steel')};
  const make = () => {
    box(parts.leaf, [D.x0, D.y0, D.z0], [D.x1, D.y1, D.z1], 0.003);
    retint(parts.leaf, [0, 0, -K], 'foam');
    box(parts.emboss, [D.x0 + 0.04, D.y0 + 0.04, D.z1 - 0.001], [D.x1 - 0.04, D.y1 - 0.04, D.z1 + 0.003], 0.002);
    for (const h of D.hinges) {
      cyl(parts.hw, 'y', [D.x1, 0, D.hingeZ], 0.0095, h - 0.045, h - 0.001, 10);
      box(parts.hw, [D.x1 - 0.06, h - 0.045, D.z1], [D.x1 - 0.004, h - 0.001, D.z1 + 0.005], 0.001);
    }
    // compression latch: base plate, shaft, T bar (near the free edge)
    const lx = D.x0 + 0.075, ly = 0.95;
    box(parts.hw, [lx - 0.025, ly - 0.055, D.z1 + 0.003], [lx + 0.025, ly + 0.055, D.z1 + 0.009], 0.002);
    cyl(parts.hw, 'z', [lx, ly, 0], 0.008, D.z1 + 0.009, D.z1 + 0.03, 10);
    box(parts.hw, [lx - 0.035, ly - 0.007, D.z1 + 0.024], [lx + 0.035, ly + 0.007, D.z1 + 0.036], 0.003);
  };
  if (side > 0) make(); else mirroredX(Object.values(parts), make);
  return parts;
}
door(1, 'door_r');
door(-1, 'door_l');
// frame halves of the hinges (still), on the jambs
{
  const hw = P('frame_hinges', 'body', 'steel');
  for (const s of [1, -1]) for (const h of D.hinges) {
    cyl(hw, 'y', [s * D.x1, 0, D.hingeZ], 0.0095, h + 0.001, h + 0.045, 10);
    box(hw, s > 0 ? [D.x1 + 0.004, h + 0.001, C.z] : [-D.x1 - 0.06, h + 0.001, C.z], s > 0 ? [D.x1 + 0.06, h + 0.045, C.z + 0.005] : [-D.x1 - 0.004, h + 0.045, C.z + 0.005], 0.001);
  }
}
// the left door's intake hood
hood('door_l_hood', 'door_l', -(D.x0 + D.x1) / 2, 0.47, 0.75, 0.86, D.z1 + 0.003, 0.045, q => q);
// the right door: e-stop, instrument housing (gasket frame, rain hood), control box inside
{
  const E = G.estop, z = D.z1 + 0.003;
  lathe(P('estop_base', 'door_r', 'yellow'), 'z', [E.x, E.y, 0], [[z, 0.05], [z + 0.012, 0.05], [z + 0.015, 0.046]], 24);
  cyl(P('estop_stem', 'door_r', 'red'), 'z', [E.x, E.y, 0], 0.018, z + 0.015, z + 0.03, 12);
  lathe(P('estop_head', 'door_r', 'red'), 'z', [E.x, E.y, 0], [[z + 0.03, 0.034], [z + 0.036, 0.036], [z + 0.043, 0.033], [z + 0.047, 0.022]], 24);
  const [px0, py1] = pan(0, 0), [px1, py0] = pan(PANEL.w, PANEL.h), gz = D.z1 + 0.003;
  slabZ(P('panel_gasket', 'door_r', 'black'), rect(px0 - 0.01, py0 - 0.01, px1 + 0.01, py1 + 0.01), gz, G.plate.front + 0.003, 0.002, [rect(px0, py0, px1, py1)]);
  box(P('panel_hood', 'door_r', 'black'), [px0 - 0.02, py1 + 0.012, gz], [px1 + 0.02, py1 + 0.026, gz + 0.045], 0.003);
  box(P('control_box', 'door_r', 'zinc'), [px0 + 0.01, py0 + 0.01, D.z0 - 0.08], [px1 - 0.01, py1 - 0.01, D.z0], 0.004);
}

// =============================== instrument panel ===============================
const PZ = G.plate.front, u2m = u => u * PU;
{
  // the printed plate (custom UVs: its front shows the art), the hour meter window cut through it
  const H = PANEL.hours, [wx0, wy1] = pan(H.window[0], H.window[1]), [wx1, wy0] = pan(H.window[2], H.window[3]);
  const [px0, py1] = pan(0, 0), [px1, py0] = pan(PANEL.w, PANEL.h);
  slabZ(P('panel_plate', 'door_r', 'black', {custom: 'plate'}), rect(px0, py0, px1, py1), G.plate.back, PZ, 0, [rect(wx0, wy0, wx1, wy1)]);
  box(P('hours_backing', 'door_r', 'black'), [wx0 - 0.003, wy0 - 0.003, D.z1 + 0.0005], [wx1 + 0.003, wy1 + 0.003, D.z1 + 0.0035]);
  const [bx0, by1] = pan(H.bezel[0], H.bezel[1]), [bx1, by0] = pan(H.bezel[2], H.bezel[3]);
  slabZ(P('hours_bezel', 'door_r', 'chrome'), rect(bx0, by0, bx1, by1), PZ, PZ + 0.005, 0.0015, [rect(wx0, wy0, wx1, wy1)]);
  box(P('hours_glass', 'door_r', 'glass'), [wx0 - 0.002, wy0 - 0.002, PZ + 0.0036], [wx1 + 0.002, wy1 + 0.002, PZ + 0.0041]);
  // gauges: chrome bezel, glass, needle (value channel) with its hub cap
  for (const g of PANEL.gauges) {
    const [cx, cy] = pan(...g.c), r = u2m(g.r);
    const seg = g.r > 65 ? 28 : 24;
    bezel(P(`bezel_${g.id}`, 'door_r', 'chrome'), [cx, cy, 0], r, r + u2m(7), r + u2m(4.5), 0.005, seg);
    cyl(P(`glass_${g.id}`, 'door_r', 'glass'), 'z', [cx, cy, 0], r + u2m(1.5), PZ + 0.0036, PZ + 0.0041, seg);
    const a = PANEL.sweep[0], dir = [Math.cos(a), -Math.sin(a)], nrm = [-dir[1], dir[0]];   // value 0, concept (y up)
    const at = (l, w) => [cx + dir[0] * l + nrm[0] * w, cy + dir[1] * l + nrm[1] * w], tip = r - u2m(8), tail = u2m(12);
    const nd = P(`needle_${g.id}_mesh`, `needle_${g.id}`, 'needle');
    slabZ(nd, [at(-tail, -u2m(1.6)), at(tip, -u2m(0.5)), at(tip + u2m(1.2), 0), at(tip, u2m(0.5)), at(-tail, u2m(1.6))], PZ + 0.0012, PZ + 0.002);
    cyl(P(`needle_${g.id}_hub`, `needle_${g.id}`, 'black'), 'z', [cx, cy, 0], u2m(5), PZ + 0.0004, PZ + 0.0031, 12);
  }
  // hour meter drums (custom UVs: digit cells), ten faces each, the digit d at the front when turned 36 d degrees
  const DR = H.drums, chord = u2m(DR.face), rr = chord / (2 * Math.sin(Math.PI / 10)), [, cyc] = pan(0, (H.window[1] + H.window[3]) / 2);
  const axisZ = PZ - 0.001 - rr * Math.cos(Math.PI / 10);
  for (let i = 0; i < DR.n; i++) {
    const [dx0] = pan(DR.x0 + i * (DR.w + DR.gap), 0), [dx1] = pan(DR.x0 + i * (DR.w + DR.gap) + DR.w, 0);
    // the decagon in (z, y) about the axis: a face centred straight ahead (+z), the next ones below it (they roll up)
    const poly = Array.from({length: 10}, (_, k) => { const a = Math.PI / 10 + 2 * Math.PI * k / 10; return [axisZ + rr * Math.cos(a), cyc + rr * Math.sin(a)]; });
    slabX(P(`drum_${i}_mesh`, `drum_${i}`, 'black', {custom: 'drum', drum: i, axis: [cyc, axisZ]}), poly, dx0, dx1);
  }
  // lamps: chrome bezel; unlit and lit lenses (two parts, one shown)
  const lampMat = {run: 'lampG', low: 'lampA', fault: 'lampR'};
  for (const L of PANEL.lamps) {
    const [cx, cy] = pan(...L.c), ri = u2m(PANEL.lamp.lens), ro = u2m(PANEL.lamp.bezel);
    bezel(P(`lamp_${L.id}_bezel`, 'door_r', 'chrome'), [cx, cy, 0], ri, ro, ro - u2m(2), 0.0045, 20);
    for (const lit of [false, true]) {
      const bone = `lamp_${L.id}${lit ? '_lit' : ''}`;
      lathe(P(bone + '_lens', bone, lampMat[L.id] + (lit ? '_lit' : '')), 'z', [cx, cy, 0], [[PZ - 0.001, ri - 0.0003], [PZ + 0.003, ri - 0.0003], [PZ + 0.0052, ri * 0.72], [PZ + 0.0062, ri * 0.3]], 16);
    }
  }
  // key switch: escutcheon, lock face, the key (value channel 'key'), built at RUN then turned to OFF
  const KY = PANEL.key, [kx, ky] = pan(...KY.c);
  bezel(P('key_escutcheon', 'door_r', 'chrome'), [kx, ky, 0], u2m(KY.lock), u2m(KY.bezel), u2m(KY.bezel - 2.5), 0.0055, 24);
  cyl(P('key_lock', 'door_r', 'black'), 'z', [kx, ky, 0], u2m(KY.lock) + 0.0002, PZ - 0.0005, PZ + 0.0035, 24);
  {
    const k = P('key_mesh', 'key', 'brass'), off = -KY.positions.OFF;   // OFF is 45 degrees anticlockwise of up, as seen
    placed(k, () => {
      cyl(k, 'z', [kx, ky, 0], 0.003, PZ + 0.0035, PZ + 0.008, 12);
      slabX(k, [[PZ + 0.007, ky - 0.008], [PZ + 0.007, ky + 0.008], [PZ + 0.024, ky + 0.012], [PZ + 0.03, ky + 0.007], [PZ + 0.03, ky - 0.007], [PZ + 0.024, ky - 0.012]], kx - 0.0013, kx + 0.0013, 0.0004);
    }, rotAbout([kx, ky, 0], [0, 0, off]));
  }
  // START / STOP buttons
  for (const B of PANEL.buttons) {
    const [cx, cy] = pan(...B.c), ri = u2m(PANEL.button.cap), ro = u2m(PANEL.button.bezel);
    bezel(P(`button_${B.id}_bezel`, 'door_r', 'chrome'), [cx, cy, 0], ri, ro, ro - u2m(2), 0.0045, 20);
    lathe(P(`button_${B.id}_cap`, 'door_r', B.id === 'start' ? 'green' : 'red'), 'z', [cx, cy, 0], [[PZ - 0.001, ri - 0.0003], [PZ + 0.006, ri - 0.0003], [PZ + 0.008, ri - 0.002]], 20);
  }
}

// =============================== engine bay ===============================
const BAY_FROM = PARTS.length;
{
  const B = 'body';
  for (const z of [-0.28, 0.28]) box(P(`skid_${z > 0 ? 'f' : 'b'}`, B, 'base'), [-1.0, T, z - 0.04], [1.0, T + 0.07, z + 0.04], 0.004);
  for (const x of [-0.62, 0.05, 0.72]) for (const z of [-0.28, 0.28]) cyl(P(`mount_${x}_${z}`, B, 'rubber'), 'y', [x, 0, z], 0.035, T + 0.07, T + 0.12, 12);
  // radiator: core, tanks, cap; shroud with its round opening, fan on its spacer
  box(P('radiator_core', B, 'rad'), [-1.12, 0.40, -0.36], [-1.04, 1.42, 0.36], 0.003);
  box(P('radiator_tank_top', B, 'engineDark'), [-1.13, 1.42, -0.37], [-1.03, 1.48, 0.37], 0.006);
  box(P('radiator_tank_low', B, 'engineDark'), [-1.13, 0.36, -0.37], [-1.03, 0.40, 0.37], 0.005);
  cyl(P('radiator_cap', B, 'steel'), 'y', [-1.08, 0, 0.10], 0.025, 1.48, 1.50, 12);
  // the fan turns on the water pump's shaft: shroud opening, hub, blades and spacer all on the fan pulley's axis (y FAN_Y);
  // 2026-10-09 the fan sat 4 cm under it (user screenshot: "风扇貌似歪了")
  const FAN_Y = 0.92;
  slabX(P('fan_shroud', B, 'black'), rect(-0.34, 0.42, 0.34, 1.38), -1.03, -1.0, 0.002, [circle(0, FAN_Y, 0.30, 24)]);
  cyl(P('fan_hub', 'fan', 'engineDark'), 'x', [0, FAN_Y, 0], 0.06, -0.98, -0.92, 14);
  { const fb = P('fan_blades', 'fan', 'engineDark');
    for (let i = 0; i < 6; i++) placed(fb, () => box(fb, [-0.97, FAN_Y + 0.05, -0.045], [-0.955, FAN_Y + 0.28, 0.045], 0.002), rotAbout([-0.9625, FAN_Y, 0], [i * 60, 0, 0])); }
  cyl(P('fan_spacer', B, 'steel'), 'x', [0, FAN_Y, 0], 0.035, -0.92, -0.81, 10);
  // engine
  box(P('engine_sump', B, 'engineDark'), [-0.70, T + 0.12, -0.20], [0.20, 0.56, 0.20], 0.01);
  box(P('engine_block', B, 'engine'), [-0.72, 0.56, -0.23], [0.22, 0.98, 0.23], 0.012);
  box(P('engine_head', B, 'engine'), [-0.70, 0.98, -0.21], [0.20, 1.09, 0.21], 0.008);
  box(P('valve_cover', B, 'engineDark'), [-0.67, 1.09, -0.14], [0.17, 1.17, 0.16], 0.02);
  for (let i = 0; i < 6; i++) cyl(P(`cover_bolt_${i}`, B, 'steel'), 'y', [-0.60 + i * 0.14, 0, 0.01], 0.012, 1.17, 1.178, 6);
  cyl(P('oil_filler_cap', B, 'yellow'), 'y', [-0.45, 0, 0.07], 0.028, 1.17, 1.19, 14);
  box(P('timing_cover', B, 'engineDark'), [-0.76, 0.52, -0.18], [-0.72, 1.05, 0.18], 0.008);
  const pulleys = [[0.66, 0, 0.12], [FAN_Y, 0, 0.07], [0.86, 0.17, 0.045]];
  pulleys.forEach(([y, z, r], i) => cyl(P(`pulley_${i}`, B, 'steel'), 'x', [0, y, z], r, -0.81, -0.76, 20));
  cyl(P('charge_alternator', B, 'alt'), 'x', [0, 0.86, 0.17], 0.06, -0.75, -0.58, 14);
  { // the belt round the three pulleys (hull of the circles, swept)
    const pts = []; for (const [y, z, r] of pulleys) for (let k = 0; k < 16; k++) { const a = 2 * Math.PI * k / 16; pts.push([z + (r + 0.004) * Math.cos(a), y + (r + 0.004) * Math.sin(a)]); }
    pts.sort((a, b) => a[0] - b[0] || a[1] - b[1]); const cr = (o, a, b) => (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0]), lo = [], hi = [];
    for (const p of pts) { while (lo.length >= 2 && cr(lo[lo.length - 2], lo[lo.length - 1], p) <= 0) lo.pop(); lo.push(p); }
    for (const p of pts.slice().reverse()) { while (hi.length >= 2 && cr(hi[hi.length - 2], hi[hi.length - 1], p) <= 0) hi.pop(); hi.push(p); }
    const hull = lo.slice(0, -1).concat(hi.slice(0, -1));
    sweep(P('belt', B, 'belt'), hull.map(([z, y]) => [-0.785, y, z]), 0.006, 6, true);
  }
  box(P('exhaust_manifold', B, 'muffler'), [-0.62, 0.88, -0.31], [0.10, 0.96, -0.23], 0.008);
  cyl(P('turbo_turbine', B, 'castiron'), 'z', [0.18, 1.0, 0], 0.085, -0.36, -0.25, 20);
  cyl(P('turbo_compressor', B, 'zinc'), 'z', [0.18, 1.0, 0], 0.075, -0.25, -0.15, 20);
  cyl(P('bellows_core', B, 'zinc'), 'y', [0.18, 0, -0.30], 0.042, 1.07, 1.26, 14);
  for (let i = 0; i < 7; i++) cyl(P(`bellows_rib_${i}`, B, 'zinc'), 'y', [0.18, 0, -0.30], 0.05, 1.085 + i * 0.024, 1.093 + i * 0.024, 14);
  cyl(P('muffler', B, 'muffler'), 'x', [0, 1.38, -0.22], 0.135, -0.95, 0.30, 24, 0.02);
  cyl(P('air_filter', B, 'filter'), 'x', [0, 1.20, 0.18], 0.10, 0.30, 0.68, 20);
  cyl(P('air_filter_cap', B, 'black'), 'x', [0, 1.20, 0.18], 0.104, 0.68, 0.70, 20);
  sweep(P('intake_hose', B, 'rubber'), [[0.31, 1.20, 0.18], [0.24, 1.20, 0.17], [0.19, 1.17, 0.10], [0.18, 1.08, -0.02], [0.18, 1.0, -0.14]], 0.035, 10);
  cyl(P('oil_filter', B, 'filter'), 'z', [-0.30, 0.70, 0], 0.05, 0.23, 0.33, 16);
  cyl(P('fuel_filter', B, 'blue'), 'z', [-0.12, 0.70, 0], 0.04, 0.23, 0.31, 14);
  cyl(P('dipstick', B, 'steel'), 'y', [-0.50, 0, 0.245], 0.004, 0.75, 1.02, 8);
  sweep(P('dipstick_handle', B, 'yellow'), Array.from({length: 8}, (_, i) => { const a = 2 * Math.PI * i / 8; return [-0.50 + 0.016 * Math.cos(a), 1.04 + 0.016 * Math.sin(a), 0.245]; }), 0.004, 6, true);
  cyl(P('starter', B, 'black'), 'x', [0, 0.55, -0.26], 0.05, 0.04, 0.20, 14);
  sweep(P('hose_top', B, 'rubber'), [[-1.03, 1.40, 0.12], [-0.92, 1.36, 0.12], [-0.80, 1.10, 0.12], [-0.71, 1.05, 0.12]], 0.03, 10);
  sweep(P('hose_low', B, 'rubber'), [[-1.03, 0.38, -0.15], [-0.95, 0.40, -0.15], [-0.82, 0.60, -0.14], [-0.74, 0.70, -0.12]], 0.03, 10);
  sweep(P('fuel_line', B, 'rubber'), [[-0.80, T - 0.005, -0.36], [-0.80, 0.55, -0.36], [-0.62, 0.70, -0.26]], 0.006, 8);
  // flywheel housing, the generator (ribbed drum, end bell with vents, terminal box), output cable to the connection box
  cyl(P('flywheel_housing', B, 'engineDark'), 'x', [0, 0.75, 0], 0.30, 0.22, 0.34, 28, 0.01);
  slabX(P('generator_drum', B, 'alt'), Array.from({length: 40}, (_, i) => { const a = 2 * Math.PI * i / 40, r = i % 2 ? 0.29 : 0.302; return [r * Math.cos(a), 0.75 + r * Math.sin(a)]; }), 0.34, 0.86, 0);
  cyl(P('generator_bell', B, 'engineDark'), 'x', [0, 0.75, 0], 0.26, 0.86, 0.92, 24, 0.01);
  slabX(P('generator_vents', B, 'black'), circle(0, 0.75, 0.2, 24), 0.92, 0.93, 0.002,
    Array.from({length: 8}, (_, i) => { const a = 2 * Math.PI * i / 8; return [[0.06, -0.012], [0.17, -0.02], [0.17, 0.02], [0.06, 0.012]].map(([r, w]) => [r * Math.cos(a) - w * Math.sin(a), 0.75 + r * Math.sin(a) + w * Math.cos(a)]); }));
  box(P('terminal_box', B, 'alt'), [0.46, 1.03, -0.15], [0.76, 1.20, 0.15], 0.006);
  box(P('terminal_lid', B, 'engineDark'), [0.455, 1.20, -0.155], [0.765, 1.215, 0.155], 0.003);
  sweep(P('output_cable', B, 'rubber'), [[0.62, 1.10, -0.15], [0.62, 1.07, -0.33], [0.45, 0.85, -0.37], [0.25, 0.62, -0.39], [0.125, 0.52, -C.z + C.wall + 0.004]], 0.022, 10);
  // battery with terminals
  box(P('battery', B, 'battery'), [0.30, T + 0.07, 0.26], [0.62, 0.56, 0.40], 0.006);   // between the mounts (x 0.05, 0.72)
  cyl(P('battery_pos', B, 'red'), 'y', [0.35, 0, 0.33], 0.012, 0.56, 0.585, 12);
  cyl(P('battery_neg', B, 'black'), 'y', [0.57, 0, 0.33], 0.012, 0.56, 0.585, 12);
}

for (let i = BAY_FROM; i < PARTS.length; i++) PARTS[i].bay = true;
// =============================== fill lid (root 'fill_lid', channel 'fill') ===============================
{
  const F = G.fill, lid = P('fill_lid_plate', 'fill_lid', 'base');
  box(lid, [F.x0 - 0.006, F.y1, F.z0 - 0.006], [F.x1 + 0.006, F.y1 + F.lid, F.z1 + 0.006], 0.003);
  box(P('fill_lid_hasp', 'fill_lid', 'steel'), [(F.x0 + F.x1) / 2 - 0.016, F.y1 - 0.03, F.z1 + 0.006], [(F.x0 + F.x1) / 2 + 0.016, F.y1 + F.lid, F.z1 + 0.011], 0.001);
}

// =============================== connection box and the standard power port (model frame) ===============================
{
  const CB = G.connection;
  box(P('connection_box', 'body', 'zinc'), [CB.x0, CB.y0, CB.z], [CB.x1, CB.y1, -C.z + 0.002], 0.005);   // 2 mm into the wall
  for (const [x, y] of [[CB.x0 + 0.02, CB.y0 + 0.02], [CB.x1 - 0.02, CB.y0 + 0.02], [CB.x0 + 0.02, CB.y1 - 0.02], [CB.x1 - 0.02, CB.y1 - 0.02]])
    cyl(P(`connection_screw_${x}_${y}`, 'body', 'steel'), 'z', [x, y, 0], 0.008, CB.z, CB.z - 0.004, 8);
}
// the port parts are built in the model frame (px) and flagged so the concept -> model turn skips them
const PORT_PARTS = {plate: P('port_plate', 'body', 'steel', {model: true}), socket: P('port_socket', 'body', 'black', {model: true}), pin: P('port_pin', 'body', 'steel', {model: true})};
addPowerPort(PORT_PARTS, 0, 8, -m(G.connection.z), 8);   // on the connection box's face, its mating face on the cell boundary
// the fuel supply connection (2026-10-09, user: a standard fluid pipe port on the side, then "not on the same end as the fill
// box"): a galvanised box on the radiator end, under the louvre, carrying the standard fluid port (tools/afl-fluid-port.mjs)
// at the face centre of c2r0's end face (model x +24); a pipe in the cell beyond feeds diesel into the sub-base tank. Built
// facing +z, turned a quarter onto +x: (x, y, z) -> (z + 24, y, -x)
{
  const FB = G.fuelBox;
  box(P('fuel_connection_box', 'body', 'zinc'), [FB.x0, FB.y0, -FB.z], [FB.x1, FB.y1, FB.z], 0.006);
  const FP = {plate: P('fluid_port_plate', 'body', 'steel', {model: true}), throat: P('fluid_port_throat', 'body', 'black', {model: true}), studs: P('fluid_port_studs', 'body', 'steel', {model: true})};
  placedAll(Object.values(FP), () => addFluidPort(FP, 0, 8, -FLUID_PORT.depth, 0), q => [q[2] + 24, q[1], -q[0]]);
}

// =============================== concept -> model ===============================
const toModel = q => [-(q[0] - XC), q[1], -q[2]];
for (const p of PARTS) if (!p.model) p.v = p.v.map(toModel);
// drop parts that ended up without faces (none expected)
for (const p of PARTS) assert(p.f.length, 'empty part ' + p.name);

// triangulate non-planar / concave quads (as tools/build-trash-enclosure-v1.mjs)
for (const p of PARTS) {
  const out = [];
  for (const f of p.f) {
    const P3 = f.ids.map(i => p.v[i]), n = newell(P3);
    const convexQuad = f.ids.length === 4 && [0, 1, 2, 3].every(k => dot(cross(sub(P3[(k + 1) % 4], P3[k]), sub(P3[(k + 2) % 4], P3[(k + 1) % 4])), n) > 1e-14);
    if (f.ids.length === 3 || convexQuad) { out.push(f); continue; }
    assert(f.ids.length === 4, `${f.ids.length}-gon in ${p.name}`);
    const k = [0, 1, 2, 3].find(k => dot(cross(sub(P3[k], P3[(k + 3) % 4]), sub(P3[(k + 1) % 4], P3[k])), n) <= 1e-14) ?? 0;
    out.push({...f, ids: [f.ids[k], f.ids[(k + 1) % 4], f.ids[(k + 2) % 4]]}, {...f, ids: [f.ids[(k + 2) % 4], f.ids[(k + 3) % 4], f.ids[k]]});
  }
  p.f = out;
}

// =============================== closed meshes ===============================
function solidity(p) {
  const key = q => q.map(v => Math.round(v * 1e5)).join(','), dir = new Map(); let vol = 0;
  for (const f of p.f) {
    const P3 = f.ids.map(i => p.v[i]);
    for (let j = 1; j + 1 < P3.length; j++) vol += dot(P3[0], cross(P3[j], P3[j + 1])) / 6;
    for (let j = 0; j < f.ids.length; j++) { const a = key(P3[j]), b = key(P3[(j + 1) % P3.length]); if (a !== b) dir.set(a + '>' + b, (dir.get(a + '>' + b) || 0) + 1); }
  }
  let open = 0, flipped = 0;
  for (const [e, n] of dir) { const [a, b] = e.split('>'); if (!dir.get(b + '>' + a)) open++; if (n > 1) flipped++; }
  return {open, flipped, vol};
}
export const solids = {};
for (const p of PARTS) { const s = solidity(p); if (s.open || s.flipped || s.vol <= 0) solids[p.name] = s; }
assert(!Object.keys(solids).length, 'not closed / wound out: ' + JSON.stringify(solids));
// coplanar overlaps between different parts (the lit lenses are twins of the unlit ones, only one is ever shown)
const twin = (a, b) => a.replace('_lit', '') === b.replace('_lit', '');
const zf = zFightLevels(PARTS, new Map());
export const coplanar = zf.unresolved.filter(u => !twin(u.a.replace('part:', ''), u.b.replace('part:', ''))).map(u => [u.a, u.b, +u.area.toFixed(4)]);

// boundary faces (tools/audit-boundary-faces.mjs, which reads JSON / OBJ block models only): a face on the structure's
// outer grid planes (model x +-24, y 0 / 32, z +-8) facing inward would coincide with a full neighbour's face
export const boundaryRisk = [];
const boundaryCheck = () => { for (const p of PARTS) for (const f of p.f) {
  if (f.buried) continue;
  const P3 = f.ids.map(i => p.v[i]), n = norm(newell(P3));
  for (const [k, planes] of [[0, [-24, 24]], [1, [0, 32]], [2, [-8, 8]]]) for (const w of planes)
    if (P3.every(q => Math.abs(q[k] - w) < 1e-4) && n[k] * Math.sign(w || 1) * (w === 0 ? -1 : 1) < -0.99) boundaryRisk.push(p.name + ' @' + 'xyz'[k] + '=' + w);
}
assert(!boundaryRisk.length, 'faces on the outer grid planes facing in: ' + boundaryRisk.slice(0, 6).join(', ')); };

// =============================== bones and their pivots (model px) ===============================
const hingeR = toModel([m(D.x1), 0, m(D.hingeZ)]), hingeL = toModel([-m(D.x1), 0, m(D.hingeZ)]);
const F = G.fill, lidPivot = toModel([m((F.x0 + F.x1) / 2), m(F.y1 + F.lid), m(F.z0 - 0.006)]);
const flapPivot = toModel([m(G.stack.x), m(G.stack.top + 0.001), m(G.stack.z - G.stack.r - 0.01)]);
const BONES = [['body', null, [0, 0, 0]], ['door_l', null, hingeL], ['door_r', null, hingeR], ['fill_lid', null, lidPivot], ['flap', null, flapPivot],
  ['fan', null, toModel([m(-0.95), m(0.92), 0])],   // the fan pulley's axis (FAN_Y)
  ['tank_needle', null, toModel([m(GAUGE[0]), m(GAUGE[1]), 0])]];
for (const g of PANEL.gauges) { const [x, y] = pan(...g.c); BONES.push([`needle_${g.id}`, 'door_r', toModel([m(x), m(y), 0])]); }
{ const [x, y] = pan(...PANEL.key.c); BONES.push(['key', 'door_r', toModel([m(x), m(y), 0])]); }
for (let i = 0; i < PANEL.hours.drums.n; i++) { const d = byName.get(`drum_${i}_mesh`), [ay, az] = d.axis; BONES.push([`drum_${i}`, 'door_r', toModel([0, m(ay), m(az)])]); }
for (const L of PANEL.lamps) for (const lit of [false, true]) BONES.push([`lamp_${L.id}${lit ? '_lit' : ''}`, 'door_r', [0, 0, 0]]);
const boneOf = new Map(BONES.map(b => [b[0], b]));
for (const p of PARTS) assert(boneOf.has(p.bone), 'unknown bone ' + p.bone + ' of ' + p.name);
// value channels: needles and drums turn about the panel's normal / the drum axis; the drum pivots sit on their own drum's x
for (let i = 0; i < PANEL.hours.drums.n; i++) { const d = byName.get(`drum_${i}_mesh`), xs = d.v.map(q => q[0]); boneOf.get(`drum_${i}`)[2][0] = (Math.min(...xs) + Math.max(...xs)) / 2; }
// sweep 270 degrees clockwise as seen: about the model's +z (the front is -z) that is a positive angle
const SWEEP = (PANEL.sweep[1] - PANEL.sweep[0]) / D2R;
export const CHANNELS = {
  doors: {ticks: D.ticks, easing: 'ease_in_out', transforms: {door_r: {rotation: [0, D.degrees, 0]}, door_l: {rotation: [0, -D.degrees, 0]}}},
  fill: {ticks: F.ticks, easing: 'ease_in_out', transforms: {fill_lid: {rotation: [F.degrees, 0, 0]}}},
  flap: {ticks: G.stack.ticks, easing: 'ease_out', transforms: {flap: {rotation: [G.stack.flapDegrees, 0, 0]}}},
  // the needles follow their readings as damped gauge movements (time constant 5 ticks, 2026-10-09, user: "仪表盘指针还是有点
  // 卡卡的"): an eased transition per sync (every 10 ticks) lasted ticks x a 1-2 % change, under a tick, so they stepped
  load: {ticks: 20, easing: 'ease_in_out', follow: 5, transforms: {needle_load: {rotation: [0, 0, SWEEP]}}},
  fuel: {ticks: 40, easing: 'ease_in_out', follow: 5, transforms: {needle_fuel: {rotation: [0, 0, SWEEP]}, tank_needle: {rotation: [0, 0, 180]}}},
  // a loop channel (2026-10-09): one turn every 8 ticks at speed (2.5 a second: reads as turning, a true 1800 rpm would strobe),
  // 1.5 s to spin up / down
  fan: {ticks: 30, easing: 'linear', loop: 8, transforms: {fan: {rotation: [360, 0, 0]}}},
  temp: {ticks: 40, easing: 'ease_in_out', follow: 5, transforms: {needle_temp: {rotation: [0, 0, SWEEP]}}},
  oil: {ticks: 16, easing: 'ease_in_out', follow: 5, transforms: {needle_oil: {rotation: [0, 0, SWEEP]}}},
  key: {ticks: 6, easing: 'ease_in_out', transforms: {key: {rotation: [0, 0, PANEL.key.positions.START - PANEL.key.positions.OFF]}}},
  // the drums wrap (0 and 1 are the same face): 9 -> 0 rolls on a tenth instead of spinning back through every digit
  ...Object.fromEntries(Array.from({length: PANEL.hours.drums.n}, (_, i) => [`h${i}`, {ticks: 4, easing: 'ease_in_out', wrap: true, transforms: {[`drum_${i}`]: {rotation: [360, 0, 0]}}}])),
};
export const KEY_RUN = (PANEL.key.positions.RUN - PANEL.key.positions.OFF) / (PANEL.key.positions.START - PANEL.key.positions.OFF);

// ---- the pose the renderer gives a part (its bone chain), at channel values t (model px) ----
const chainOf = bone => { const out = []; for (let b = bone; b; b = boneOf.get(b)[1]) out.unshift(b); return out; };
function poseMatrix(bone, t) {
  let M = M4.I(), parentPivot = [0, 0, 0];
  for (const b of chainOf(bone)) {
    const pv = boneOf.get(b)[2]; let rot = [0, 0, 0];
    for (const [ch, def] of Object.entries(CHANNELS)) if (def.transforms[b]) rot = def.transforms[b].rotation.map(v => v * (t[ch] || 0));
    M = M4.mul(M, M4.mul(M4.T(sub(pv, parentPivot)), M4.R(rot)));
    parentPivot = pv;
  }
  return M4.mul(M, M4.T(mul(parentPivot, -1)));
}
const posed = (p, t) => { const M = poseMatrix(p.bone, t); return p.v.map(q => M4.pt(M, q)); };
// numeric checks: needles clockwise as seen from the front, the key from OFF through RUN to START, drum digits at the front,
// the lid and the flap lift, the doors swing out
{
  const tip = (id, t) => { const p = byName.get(`needle_${id}_mesh`), q = posed(p, t), c = boneOf.get(`needle_${id}`)[2];
    let far = q[0]; for (const v of q) if (Math.hypot(v[0] - c[0], v[1] - c[1]) > Math.hypot(far[0] - c[0], far[1] - c[1])) far = v; return [-(far[0] - c[0]), far[1] - c[1]]; };   // as seen: right = -x
  for (const g of PANEL.gauges) {
    const a = tip(g.id, {}), b = tip(g.id, {[g.id]: 0.5}), c = tip(g.id, {[g.id]: 1});
    assert(a[0] < 0 && a[1] < 0, `${g.id}: needle at 0 should point lower left`); assert(Math.abs(b[0]) < 1e-6 * K + 0.02 && b[1] > 0, `${g.id}: needle at 0.5 should point up`);
    assert(c[0] > 0 && c[1] < 0, `${g.id}: needle at 1 should point lower right`);
  }
  // the bow's direction: the centroid of its far end (the corners straddle the centre line), degrees clockwise from up as
  // seen (the bow is symmetric about the axis: folded to -90..90)
  const keyDir = t => { const q = posed(byName.get('key_mesh'), {key: t}), c = boneOf.get('key')[2], d = v => [v[0] - c[0], v[1] - c[1]], L = v => Math.hypot(...d(v));
    let far = q[0]; for (const v of q) if (L(v) > L(far)) far = v;
    const end = q.filter(v => L(v) > 0.8 * L(far) && d(v)[0] * d(far)[0] + d(v)[1] * d(far)[1] > 0), mid = end.reduce((a, v) => [a[0] + d(v)[0] / end.length, a[1] + d(v)[1] / end.length], [0, 0]);
    return Math.atan2(-mid[0], mid[1]) / D2R; };
  const fold = a => ((a + 270) % 180 + 180) % 180 - 90;
  assert(Math.abs(fold(keyDir(0)) - PANEL.key.positions.OFF) < 2 && Math.abs(fold(keyDir(KEY_RUN)) - PANEL.key.positions.RUN) < 2 && Math.abs(fold(keyDir(1)) - PANEL.key.positions.START) < 2, 'key positions');
  // a drum's front face (normal -z model) carries digit d at value d / 10: the face whose centre is frontmost
  for (let d = 0; d < 10; d++) { const p = byName.get('drum_0_mesh'), M = poseMatrix(p.bone, {h0: d / 10}); let bz = Infinity;
    for (const f of p.f) { const n = norm(newell(f.ids.map(i => M4.pt(M, p.v[i])))); bz = Math.min(bz, n[2]); }
    assert(bz < -0.99, 'drum: a face square to the front at every digit'); }
  const lidUp = posed(byName.get('fill_lid_plate'), {fill: 1}), flapUp = posed(byName.get('flap_plate'), {flap: 1});
  assert(Math.max(...lidUp.map(q => q[1])) > m(F.y1 + 0.25), 'the fill lid must lift'); assert(Math.max(...flapUp.map(q => q[1])) > m(G.stack.top + 0.05), 'the flap must lift');
  const doorOut = posed(byName.get('door_r_leaf'), {doors: 1}), doorOutL = posed(byName.get('door_l_leaf'), {doors: 1});
  assert(Math.min(...doorOut.map(q => q[2])) < -m(1.2) && Math.min(...doorOutL.map(q => q[2])) < -m(1.2), 'the doors must swing out (model -z)');
}

// =============================== buried faces ===============================
// A face lying inside another part's solid all over (a door leaf under its emboss, the tank's top under its plate), or on
// the ground facing down, is never seen: it keeps its place in the closed mesh but gets no atlas space and no paint.
// Samples: the face's centre, its corners and points every quarter along its edges, each drawn 1 mm toward the centre, all
// 0.375 mm out along its normal; all of them inside ONE other part (2026-10-09 preview: samples spread over several parts
// let a door leaf's visible border and a sliver of the radiator end wall count as buried, drawn black). Inside = odd
// crossings of a slightly skewed ray (parts that overlap themselves only miss burials, never invent them).
{
  const RAY = norm([1, 0.0137, 0.0071]);
  const SOL = PARTS.map(p => { const tris = []; for (const f of p.f) for (let j = 1; j + 1 < f.ids.length; j++) tris.push([p.v[f.ids[0]], p.v[f.ids[j]], p.v[f.ids[j + 1]]]);
    return {p, tris, lo: [0, 1, 2].map(k => Math.min(...p.v.map(q => q[k]))), hi: [0, 1, 2].map(k => Math.max(...p.v.map(q => q[k])))}; });
  const inside = (S, q) => {
    if (q.some((v, k) => v < S.lo[k] || v > S.hi[k])) return false;
    let n = 0;
    for (const [a, b, c] of S.tris) {   // Moller-Trumbore
      const e1 = sub(b, a), e2 = sub(c, a), h = cross(RAY, e2), det = dot(e1, h); if (Math.abs(det) < 1e-12) continue;
      const sv = sub(q, a), u = dot(sv, h) / det; if (u < 0 || u > 1) continue;
      const qv = cross(sv, e1), v = dot(RAY, qv) / det; if (v < 0 || u + v > 1) continue;
      if (dot(e2, qv) / det > 1e-9) n++;
    }
    return n % 2 === 1;
  };
  for (const p of PARTS) { if (p.custom || p.mat === 'glass') continue;
    for (const f of p.f) {
      const P3 = f.ids.map(i => p.v[i]), n = norm(newell(P3)), c = P3.reduce((a, q) => add(a, mul(q, 1 / P3.length)), [0, 0, 0]);
      if (n[1] < -0.99 && P3.every(q => q[1] < 0.01)) { f.buried = true; continue; }
      const pull = q => { const d = sub(c, q), l = Math.hypot(...d); return l < 1e-9 ? q : add(q, mul(d, Math.min(0.5, m(0.001) / l))); };
      const edge = P3.flatMap((q, k) => [0.25, 0.5, 0.75].map(t => add(q, mul(sub(P3[(k + 1) % P3.length], q), t))));
      const samples = [c, ...P3, ...edge].map(pull).map(q => add(q, mul(n, 0.006)));
      f.buried = SOL.some(S => S.p !== p && samples.every(q => inside(S, q)));
    }
  }
}
export const buried = PARTS.reduce((a, p) => a + p.f.filter(f => f.buried).length, 0);
boundaryCheck();
// texel density by island, relative to the 15 per px of the outside: the dark inside (engine bay, foam, louvre mesh, the
// control box) is seen only through an open door, the tank's charcoal paint takes a little less
const DENSITY = {foam: 0.5, louvre: 0.5, rubber: 0.7, base: 0.9, bay: 0.6};
const islandScale = (part, mat) => DENSITY[mat] ?? (part.bay || part.name === 'control_box' || part.name === 'hours_backing' ? DENSITY.bay : 1);

// =============================== UVs: unwrap + the reserved panel region ===============================
const ATLAS = 2048, PAD = 2;
const ART = readPng(fs.readFileSync(PANEL_ART)), AW = ART.w / ART_SCALE, AH = ART.h / ART_SCALE;   // the art at 1 texel a panel unit
const REGION = {x: ATLAS - AW - PAD, y: ATLAS - AH - PAD, w: AW, h: AH};
// unwrap and paint see only the faces that can be seen (views sharing the parts' vertices and faces)
const REGULAR = PARTS.filter(p => !p.custom);
const VIEWS = REGULAR.map(p => { const q = new Part(p.name, p.bone, p.mat); Object.assign(q, {bay: p.bay}); q.v = p.v; q.f = p.f.filter(f => !f.buried); q.of = p; return q; }).filter(q => q.f.length);
const UA = unwrap(VIEWS, {atlas: ATLAS, pad: PAD, startS: 15, stepS: 0.25, reserve: [REGION.x - PAD, REGION.y - PAD, ATLAS, ATLAS], islandScale});
const faceUV = new Map([...UA.faceUV].map(([q, map]) => [q.of, map]));
if (process.env.PACK) {
  const used = UA.islands.reduce((a, is) => a + is.W * is.H, 0), cap = ATLAS * ATLAS - (REGION.w + PAD) * (REGION.h + PAD);
  const byMat = new Map(); for (const is of UA.islands) { const k = is.faces[0].f.mat + (is.part.bay ? ' bay' : ''); byMat.set(k, (byMat.get(k) || 0) + is.W * is.H); }
  console.log('S', UA.S, 'islands', UA.islands.length, 'used', used, 'cap', cap, 'fill', (used / cap).toFixed(3));
  console.log([...byMat].sort((a, b) => b[1] - a[1]).slice(0, 25).map(([k, v]) => k + ' ' + v + ' (' + (v / used * 100).toFixed(1) + '%)').join('\n'));
  const big = UA.islands.slice().sort((a, b) => b.W * b.H - a.W * a.H).slice(0, 15).map(is => is.part.name + ' ' + is.faces[0].f.mat + ' ' + is.W + 'x' + is.H);
  console.log(big.join('\n'));
  process.exit(0);
}
{
  const cellOf = (row, col) => [REGION.x + col * STRIP.cell / ART_SCALE, REGION.y + (STRIP.y + row * STRIP.cell) / ART_SCALE, STRIP.cell / ART_SCALE];
  const plain = (row, col, P3) => {   // a small planar patch inside a plain cell
    const [cx, cy, s] = cellOf(row, col), n = norm(newell(P3)), t = norm(Math.abs(n[1]) < 0.9 ? cross(n, [0, 1, 0]) : cross(n, [1, 0, 0])), b = cross(n, t);
    const uv = P3.map(q => [dot(q, t), dot(q, b)]), u0 = Math.min(...uv.map(a => a[0])), v0 = Math.min(...uv.map(a => a[1])), ext = Math.max(1e-6, ...uv.map(a => Math.max(a[0] - u0, a[1] - v0)));
    return uv.map(([u, v]) => [cx + s * 0.3 + (u - u0) / ext * s * 0.4, cy + s * 0.3 + (v - v0) / ext * s * 0.4]); };
  // buried faces: a patch of the plain black cell
  for (const p of REGULAR) for (const f of p.f) if (f.buried) { const map = faceUV.get(p) || faceUV.set(p, new Map()).get(p); map.set(f, plain(0, 10, f.ids.map(i => p.v[i]))); }
  // the plate: its front shows the printed panel (model x runs right-to-left as seen), the rest plate grey
  const plate = byName.get('panel_plate'), [px0, py1] = pan(0, 0), mx0 = toModel([m(px0), 0, 0])[0], my1 = m(py1);
  faceUV.set(plate, new Map(plate.f.map(f => { const P3 = f.ids.map(i => plate.v[i]), n = norm(newell(P3));
    return [f, n[2] < -0.99 ? P3.map(q => [REGION.x + (mx0 - q[0]) / K / PU, REGION.y + (my1 - q[1]) / K / PU]) : plain(1, 10, P3)]; })));
  // drums: each side face the digit it shows at the front, upright there; ends black
  for (let i = 0; i < PANEL.hours.drums.n; i++) {
    const p = byName.get(`drum_${i}_mesh`), row = i === PANEL.hours.drums.n - 1 ? 1 : 0, map = new Map();
    for (const f of p.f) {
      const P3 = f.ids.map(q => p.v[q]), n = norm(newell(P3));
      if (Math.abs(n[0]) > 0.9) { map.set(f, plain(0, 10, P3)); continue; }
      // the digit whose turn brings this face to the front
      let digit = -1;
      for (let d = 0; d < 10; d++) { const M = poseMatrix(p.bone, {[`h${i}`]: d / 10}), nn = norm(newell(P3.map(q => M4.pt(M, q)))); if (nn[2] < -0.99) digit = d; }
      assert(digit >= 0, 'drum face without a digit');
      const M = poseMatrix(p.bone, {[`h${i}`]: digit / 10}), Q = P3.map(q => M4.pt(M, q));
      const xs = Q.map(q => q[0]), ys = Q.map(q => q[1]), X0 = Math.max(...xs), Y1 = Math.max(...ys), W = X0 - Math.min(...xs), H = Y1 - Math.min(...ys);
      const [cx, cy, s] = cellOf(row, digit), inset = 1.5;
      map.set(f, Q.map(q => [cx + inset + (X0 - q[0]) / W * (s - 2 * inset), cy + inset + (Y1 - q[1]) / H * (s - 2 * inset)]));
    }
    faceUV.set(p, map);
  }
}
for (const p of PARTS) assert(p.f.every(f => faceUV.get(p)?.has(f)), 'unmapped face in ' + p.name);

// =============================== paint, the panel art, glass and lamp emission ===============================
const DUMMY = 'data:image/png;base64,' + png(Buffer.from([0, 0, 0, 255]), 1, 1).toString('base64');
const painted = paint({PARTS: VIEWS, islands: UA.islands, S: UA.S, uvOf: UA.uvOf, atlas: ATLAS, pad: PAD, MATS, ZONED: new Set(), groupInfo: new Map(),
  sourceGroups: () => [], refTexture: {source: DUMMY}, refUvWidth: 1, background: {c: [60, 62, 64, 255], s: [100, 20, 0, 255], n: [128, 128, 255, 255]}});
const MAPS = painted.PNG.map(b => { const r = readPng(b); assert(r.bpp === 4 && r.w === ATLAS, 'atlas format'); return r.px; });
{
  // the art, box-filtered 2:1 into the region; the panel satin, the digit cells semi-gloss
  for (let y = 0; y < AH; y++) for (let x = 0; x < AW; x++) {
    const acc = [0, 0, 0, 0];
    for (let dy = 0; dy < ART_SCALE; dy++) for (let dx = 0; dx < ART_SCALE; dx++) { const k = ((y * ART_SCALE + dy) * ART.w + x * ART_SCALE + dx) * ART.bpp; for (let c = 0; c < 4; c++) acc[c] += c < ART.bpp ? ART.px[k + c] : 255; }
    const a = acc[3] / ART_SCALE ** 2, rgb = acc.slice(0, 3).map(v => Math.round(v / ART_SCALE ** 2 * (a / 255)));   // transparent corners -> black
    const k = ((REGION.y + y) * ATLAS + REGION.x + x) * 4, digits = y * ART_SCALE >= STRIP.y;
    MAPS[0].set([...rgb, 255], k); MAPS[1].set([digits ? 125 : 112, 20, 0, 255], k); MAPS[2].set([128, 128, 255, 255], k);
  }
  for (const is of UA.islands) {
    const glass = is.part.mat === 'glass', em = LIGHT_EMISSION[is.faces[0].f.mat];
    if (!glass && em === undefined) continue;
    for (let y = is.py - PAD; y < is.py + is.H + PAD; y++) for (let x = is.px - PAD; x < is.px + is.W + PAD; x++) {
      const k = (y * ATLAS + x) * 4; if (glass) MAPS[0][k + 3] = GLASS_ALPHA; else MAPS[1][k + 3] = em; }
  }
}
const PNGS = MAPS.map(px => png(px, ATLAS, ATLAS));

// =============================== rig: source, geo, sidecar, profile ===============================
const uuidOf = s => { const h = createHash('sha256').update('afl-diesel-generator-v1:' + s).digest('hex'); return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-8${h.slice(17, 20)}-${h.slice(20, 32)}`; };
const LIT = new Set(BONES.map(b => b[0]).filter(b => b.endsWith('_lit')));
// the runtime takes at most 128 mesh elements: one element per bone (its glass a second one, the translucent layer)
const ELEMENTS = (() => {
  const groups = new Map();
  for (const p of PARTS) { const k = p.bone + (p.mat === 'glass' ? '_glass' : ''); (groups.get(k) || groups.set(k, []).get(k)).push(p); }
  return [...groups].map(([name, ps]) => {
    const e = {name, bone: ps[0].bone, glass: ps[0].mat === 'glass', v: [], f: []}, uv = new Map();
    for (const p of ps) { const o = e.v.length, map = faceUV.get(p); e.v.push(...p.v); for (const f of p.f) { const g = {...f, ids: f.ids.map(i => i + o)}; e.f.push(g); uv.set(g, map.get(f)); } }
    faceUV.set(e, uv); return e;
  });
})();
if (process.env.AREA) {
  const area = new Map(), byPart = [];
  for (const p of PARTS) { let tot = 0; for (const f of p.f) { const P3 = f.ids.map(i => p.v[i]); let a = 0; for (let j = 1; j + 1 < P3.length; j++) a += Math.hypot(...cross(sub(P3[j], P3[0]), sub(P3[j + 1], P3[0]))) / 2; a /= K * K; tot += a;
    const k = (p.custom ? 'custom' : f.mat) + (p.bone === 'body' ? '' : ' @' + p.bone.split('_')[0]); area.set(k, (area.get(k) || 0) + a); } byPart.push([p.name, +tot.toFixed(3)]); }
  console.log('TOTAL m2', [...area.values()].reduce((a, b) => a + b, 0).toFixed(2));
  console.log([...area].sort((a, b) => b[1] - a[1]).map(([k, v]) => k + ' ' + v.toFixed(2)).join('\n'));
  console.log(byPart.sort((a, b) => b[1] - a[1]).slice(0, 40).map(x => x.join(' ')).join('\n'));
  process.exit(0);
}
if (process.env.TRI) { const t = PARTS.map(p => [p.name, p.f.reduce((a, f) => a + f.ids.length - 2, 0)]).sort((a, b) => b[1] - a[1]); console.log('TOTAL', t.reduce((a, x) => a + x[1], 0)); console.log(t.slice(0, 50).map(x => x.join(' ')).join('\n')); process.exit(0); }
assert(ELEMENTS.length <= 128, 'too many mesh elements: ' + ELEMENTS.length);
const source = (() => {
  const elements = [], nodes = new Map(BONES.map(([b]) => [b, {uuid: uuidOf('group:' + b), isOpen: true, children: []}]));
  for (const p of ELEMENTS) {
    const key = i => i.toString(36).padStart(4, '0'), vertices = {}, faces = {}, uvs = faceUV.get(p);
    p.v.forEach((q, i) => { vertices[key(i)] = q.map(r12); });
    p.f.forEach((f, fi) => { const uv = uvs.get(f); faces['f' + key(fi)] = {uv: Object.fromEntries(f.ids.map((vid, j) => [key(vid), uv[j].map(r12)])), vertices: f.ids.map(key), texture: 0}; });
    const eid = uuidOf('mesh:' + p.name);
    elements.push({name: p.name, color: 0, origin: [0, 0, 0], rotation: [0, 0, 0], shading: 'flat', export: true, visibility: true, locked: false,
      render_order: 'default', scope: 0, allow_mirror_modeling: true, vertices, faces, type: 'mesh', uuid: eid});
    nodes.get(p.bone).children.push(eid);
  }
  return {meta: {format_version: '5.0', model_format: 'free', box_uv: false}, name: ID, model_identifier: '', visible_box: [4, 3, 0],
    variable_placeholders: '', variable_placeholder_buttons: [], timeline_setups: [], unhandled_root_fields: {}, resolution: {width: ATLAS, height: ATLAS},
    elements, groups: BONES.map(([b, , o]) => ({name: b, uuid: uuidOf('group:' + b), export: true, locked: false, scope: 0, selected: false, visibility: true,
      _static: {properties: {}, temp_data: {}}, origin: o.map(r12), rotation: [0, 0, 0], color: 0, children: [], reset: false, shade: true,
      mirror_uv: false, autouv: 0, isOpen: true, primary_selected: false})),
    outliner: BONES.map(([b]) => nodes.get(b)),
    textures: [{name: ID + '.png', relative_path: `textures/${ID}_v1.png`, folder: '', namespace: '', id: '0', group: '', scope: 0,
      width: ATLAS, height: ATLAS, uv_width: ATLAS, uv_height: ATLAS, particle: false, use_as_default: false, layers_enabled: false, sync_to_project: '',
      file_format: 'png', render_mode: 'default', render_sides: 'auto', wrap_mode: 'limited', pbr_channel: 'color', fps: 7, frame_time: 1,
      frame_order_type: 'loop', frame_order: '', frame_interpolate: false, visible: true, internal: true, saved: true, uuid: uuidOf('texture'),
      source: 'data:image/png;base64,' + PNGS[0].toString('base64')}],
    animations: []};
})();
const geo = {format_version: '1.12.0', 'minecraft:geometry': [{description: {identifier: 'geometry.' + ID, texture_width: ATLAS, texture_height: ATLAS,
  visible_bounds_width: 5, visible_bounds_height: 3, visible_bounds_offset: [0, 1, 0]},
  bones: BONES.map(([b, , o]) => ({name: b, pivot: [-o[0] || 0, o[1], o[2]].map(r12), ...(LIT.has(b) ? {neverRender: true} : {})}))}]};
const LAYERS = Object.fromEntries(ELEMENTS.filter(e => e.glass).map(e => [e.name, 'translucent']));
const sidecar = convert(source, geo, {}, ID + '.bbmodel', 2, null, LAYERS);
// render bounds: every part at rest and fully moved (doors, lid, flap)
const aabbOf = pts => [0, 1, 2].map(k => Math.min(...pts.map(q => q[k]))).concat([0, 1, 2].map(k => Math.max(...pts.map(q => q[k]))));
const sweepPts = PARTS.flatMap(p => [0, 0.25, 0.5, 0.75, 1].flatMap(t => posed(p, {doors: t, fill: t, flap: t})));
const SW = aabbOf(sweepPts), MG = 0.25;
const bounds = [(SW[0] - MG + 8) / 16, (SW[1] - MG) / 16, (SW[2] - MG + 8) / 16, (SW[3] + MG + 8) / 16, (SW[4] + MG) / 16, (SW[5] + MG + 8) / 16].map(r6);
const profile = {format_version: 1, geometry: `apocalypse_firstlight:geo/${ID}.geo.json`, texture: `apocalypse_firstlight:textures/block/${ID}.png`,
  origin: [0, 0, 0], scale: [1, 1, 1], facing: 'horizontal', bounds,
  parts: Object.fromEntries(BONES.map(([b, parent, o]) => [b, {...(parent ? {parent} : {}), pivot: o.map(v => r6(v / 16))}])),
  animations: Object.fromEntries(Object.entries(CHANNELS).map(([ch, d]) => [ch, {duration_ticks: d.ticks, easing: d.easing, ...(d.loop ? {loop_ticks: d.loop} : {}), ...(d.follow ? {follow_ticks: d.follow} : {}), ...(d.wrap ? {wrap: true} : {}),
    transforms: Object.fromEntries(Object.entries(d.transforms).map(([b, tr]) => [b, {rotation: tr.rotation.map(r6)}]))}]))};

// =============================== the item (OBJ, closed, unlit, no glass) ===============================
const f6 = v => (+v.toFixed(6)).toString(), SMOOTH = Math.cos(36 * D2R);
const ITEM_PARTS = PARTS.filter(p => !LIT.has(p.bone) && p.mat !== 'glass');
function cornerNormals(p, V) {
  const fn = p.f.map(f => newell(f.ids.map(i => V[i]))), byV = new Map();
  p.f.forEach((f, k) => f.ids.forEach(i => (byV.get(i) || byV.set(i, []).get(i)).push(k)));
  return p.f.map((f, k) => { const n0 = norm(fn[k]); return f.ids.map(i => norm(byV.get(i).reduce((a, j) => dot(norm(fn[j]), n0) >= SMOOTH ? add(a, fn[j]) : a, [0, 0, 0]))); });
}
function objOf(title, parts, t = {}) {
  const out = [`# AFL ${title}, generated by tools/build-diesel-generator-v1.mjs`, `mtllib item.mtl`];
  let vBase = 1, tBase = 1, nBase = 1;
  for (const p of parts) {
    const V = posed(p, t);
    out.push(`o ${p.name}`, `usemtl ${ID}`);
    for (const q of V) out.push(`v ${f6(q[0] / 16 + 0.5)} ${f6(q[1] / 16)} ${f6(q[2] / 16 + 0.5)}`);
    const uvs = faceUV.get(p), vt = [], vn = [], fl = [], cn = cornerNormals(p, V), nIndex = new Map();
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
const MTL = `# AFL Diesel standby generator V1\nnewmtl ${ID}\nKd 1 1 1\nmap_Kd apocalypse_firstlight:block/${ID}\n`;
function guiFit(parts, rotation, size = 15.2) {
  const [ax, ay] = rotation.map(v => v * D2R);
  const rot = q => { const x = q[0] * Math.cos(ay) + q[2] * Math.sin(ay), z = -q[0] * Math.sin(ay) + q[2] * Math.cos(ay); return [x, q[1] * Math.cos(ax) - z * Math.sin(ax)]; };
  const pts = parts.flatMap(p => p.v.map(q => rot([q[0], q[1] - 8, q[2]])));
  const xs = pts.map(q => q[0]), ys = pts.map(q => q[1]), w = Math.max(...xs) - Math.min(...xs), h = Math.max(...ys) - Math.min(...ys);
  const scale = r3(Math.min(4, size / Math.max(w, h))), cx = (Math.max(...xs) + Math.min(...xs)) / 2, cy = (Math.max(...ys) + Math.min(...ys)) / 2;
  return {rotation, translation: [r3(-scale * cx), r3(-scale * cy), 0], scale: [scale, scale, scale]};
}
const cellPoints = parts => parts.flatMap(p => p.v.map(([x, y, z]) => [x + 8, y, z + 8]));

// =============================== outputs ===============================
const bb = path.join(ROOT, 'src/main/blockbench'), assets = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight');
const ITEM_OBJ = objOf('Diesel standby generator V1 (closed, item)', ITEM_PARTS);
const outputs = [
  [path.join(assets, `geo/${ID}.geo.json`), json(geo)], [path.join(assets, `meshes/${ID}.aflmesh.json`), serializeCompact(sidecar)],
  [path.join(assets, `block_mesh_profiles/${ID}.json`), json(profile)],
  [path.join(bb, `${ID}_v1.bbmodel`), JSON.stringify(source)],
  [path.join(assets, `models/block/${ID}/item.obj`), ITEM_OBJ], [path.join(assets, `models/block/${ID}/item.mtl`), MTL],
  [path.join(assets, `models/block/${ID}/item.json`), json({loader: 'forge:obj', model: `apocalypse_firstlight:models/block/${ID}/item.obj`, automatic_culling: false,
    flip_v: true, shade_quads: true, emissive_ambient: true, ambientocclusion: false, textures: {particle: NS + ID}})],
  [path.join(assets, `models/block/${ID}/generator.json`), json({loader: 'apocalypse_firstlight:static_mesh', textures: {particle: NS + ID}})],
  [path.join(assets, `models/block/${ID}/cell.json`), json({textures: {particle: NS + ID}, elements: []})],
  [path.join(assets, `blockstates/${ID}.json`), json({variants: Object.fromEntries(['c0r0', 'c1r0', 'c2r0', 'c0r1', 'c1r1', 'c2r1'].map(c => ['cell=' + c, {model: NS + ID + (c === 'c1r0' ? '/generator' : '/cell')}]))})],
  [path.join(assets, `models/item/${ID}.json`), json({parent: NS + ID + '/item', gui_light: 'side',
    display: {...heldDisplay(cellPoints(ITEM_PARTS), {size: 1, rotations: {fixed: [0, 180, 0]}}), gui: guiFit(ITEM_PARTS, [20, 200, 0])}})],
];
['', '_s', '_n'].forEach((k, i) => outputs.push([path.join(bb, `textures/${ID}_v1${k}.png`), PNGS[i]], [path.join(assets, `textures/block/${ID}${k}.png`), PNGS[i]]));

// facts the Java side mirrors (structure frame px: x from the first column's west edge, y up, z 0 = the front)
const toStructure = q => [r3(q[0] + 24), r3(q[1]), r3(q[2] + 8)];
const conceptBox = (a, b) => { const p = toStructure(toModel(a.map(m))), q = toStructure(toModel(b.map(m))); return [0, 1, 2].map(k => Math.min(p[k], q[k])).concat([0, 1, 2].map(k => Math.max(p[k], q[k]))); };
const [ppx0, ppy1] = pan(0, 0), [ppx1, ppy0] = pan(PANEL.w, PANEL.h);
export const FACTS = {
  tank: conceptBox([Tk.x0 - Tk.lip, 0, -Tk.z - Tk.lip], [Tk.x1 + Tk.lip, T + 0.002, Tk.z + Tk.lip]),
  canopy: conceptBox([-G.roof.x, T, -G.roof.z], [G.roof.x, G.roof.y1, G.roof.z]),
  fillBox: conceptBox([F.x0 - 0.006, T, F.z0 - 0.006], [F.x1 + 0.006, F.y1 + F.lid, F.z1 + 0.011]),
  fillOpening: toStructure(toModel([m((F.x0 + F.x1) / 2), m(F.y1 - 0.03), m((F.z0 + F.z1) / 2)])),
  panel: conceptBox([ppx0 - 0.02, ppy0 - 0.01, D.z1], [ppx1 + 0.02, ppy1 + 0.026, 0.5]),
  estop: conceptBox([G.estop.x - 0.05, G.estop.y - 0.05, D.z1], [G.estop.x + 0.05, G.estop.y + 0.05, 0.5]),
  doors: conceptBox([-D.x1, D.y0, D.z0], [D.x1, D.y1, 0.5]),
  stackTop: toStructure(toModel([m(G.stack.x), m(G.stack.top), m(G.stack.z)])),
  port: toStructure([0, 8, 8]),
  fluidPort: conceptBox([(2 - 24) / 16, G.fuelBox.y0, -G.fuelBox.z], [G.fuelBox.x1, G.fuelBox.y1, G.fuelBox.z]),
  keyRun: r6(KEY_RUN),
};
const tris = parts => parts.reduce((s, p) => s + p.f.reduce((t, f) => t + f.ids.length - 2, 0), 0);
export const stats = {parts: PARTS.length, triangles: tris(PARTS), buriedFaces: buried, texelsPerPx: UA.S, density: DENSITY, region: REGION, coplanar: coplanar.slice(0, 12), coplanarCount: coplanar.length, bounds, facts: FACTS};

const isMain = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  console.log(JSON.stringify(stats, null, 1));
  const pi = process.argv.indexOf('--preview');
  if (pi > 0) {
    const dir = process.argv[pi + 1]; fs.mkdirSync(dir, {recursive: true});
    fs.writeFileSync(path.join(dir, 'genset_closed.obj'), objOf('closed', PARTS.filter(p => !LIT.has(p.bone)), {load: 0.42, fuel: 0.78, temp: 0.58, oil: 0.42, key: KEY_RUN, h0: 0.1, h1: 0.2, h2: 0.8, h3: 0.7, h4: 0.4}));
    fs.writeFileSync(path.join(dir, 'genset_open.obj'), objOf('open', PARTS.filter(p => !LIT.has(p.bone) || p.bone === 'lamp_run_lit').filter(p => p.bone !== 'lamp_run'), {doors: 1, fill: 1, flap: 1, load: 0.42, fuel: 0.78, temp: 0.58, oil: 0.42, key: KEY_RUN}));
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
