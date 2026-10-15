// Emergency Radio V1 (docs/models/emergency_radio_v1.md, 2026-10-14): the hand-crank emergency radio of the forest start
// home B (docs/worldgen/forest_start_home_b_v1_plan.md, M2 F4), meshed from the user's paid cube model (54 cubes,
// src/main/blockbench/emergency_radio_cube_v1.bbmodel, concept sheet "地堡收音机" BR-10): its layout and proportions in the
// cube model's pixels, 1 px = 2.5 cm (the body 16 x 8 x 7 px = 40 x 20 x 17.5 cm, a big portable set; the first draft at 1.25 cm,
// 20 cm, the user found far too small in game, 2026-10-14), centred in the cell. User 2026-10-14: into the game, no broadcast reception yet.
//   The case: a chamfered steel box (the bevels worn to steel). Its front (north, -z) has, from the viewer's left: the speaker
//   (a black steel plate, 12 punched horizontal slots in a rounded-square field, cloth behind, a screw in each corner); the
//   dial window (a black bezel, the FM / AM scale on its own sprite, the red needle at FM 98); under it the big TUNING knob
//   (viewer's right), the small VOLUME knob, the red POWER lamp and the AM / FM / OFF slide switch between them. On top a
//   steel strap handle on two screwed feet and a chrome telescopic antenna at the back right; on the back a plain steel label
//   plate and the battery door. Added to the cube model (plan F4: crank charging): the folding crank on the left end (+x),
//   its arm drawn in four quarter turns.
// Every piece is drawn facing north (the front at z < 0.5); the block turns them and lowers them onto a table, counter or
// desk under it (CabinPieces.Sink dy).
// The crank turns as an AFL Animated Block Mesh (emergency_radio_crank, tools/furniture/furniture-mesh.mjs, 2026-10-15: the
// cabin pieces showed its four quarters, one frame each): only the arm, drawn by the block entity renderer, a wrapping
// channel 'crank' (the block's crank quarter / 4, a quarter in 4 ticks, linear, so a held crank turns on without a stop);
// one profile per surface the radio stands on (its origin lowered as the pieces sink). The rest stay cabin pieces;
// radio/crank_0 is the item's arm.
// Writes textures (block/radio/*.png + _s / _n), mesh pieces (cabin_mesh/radio/), the crank's mesh (geo, sidecar, profiles,
// atlas block/radio/emergency_radio_crank, src/main/blockbench/radio/), the block / item model, blockstate and loot
// table. Tags, item mass, lang and the stamina cost are edited by hand; --check audits them.
//   node tools/build-emergency-radio-v1.mjs            -> writes every output
//   node tools/build-emergency-radio-v1.mjs --check    -> verifies every output is up to date and the hand-edited data
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {renderX} from './cabin/cabin-art.mjs';
import {RADIO_TEXTURES, renderDial, NEEDLE_U} from './radio/emergency-radio-art.mjs';
import {Piece, solid, cylinder, serialise, newell, dot, sub, add, scl, norm} from './cabin/cabin-geo.mjs';
import {furnitureMesh} from './furniture/furniture-mesh.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const ASSETS = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight');
const DATA = path.join(ROOT, 'src/main/resources/data');
const NS = 'apocalypse_firstlight:', TEX = NS + 'block/';
const json = v => JSON.stringify(v, null, 2) + '\n';

/** The block: id, item mass (kg, a real 40 cm steel-cased portable radio), mining (AGENTS.md audit: a small appliance, any pickaxe, no tier, hand-breakable), names. */
export const BLOCK = {id: 'emergency_radio', mass: 3.0, noise: 'metal', en: 'Crank Emergency Radio', zh: '手摇应急收音机'};
/** The stamina one crank step costs (stamina_v1.json costs.crank; a step every 4 ticks of holding: 2 a second). */
export const CRANK_COST = 0.4;

const PAINT = 'radio/case_paint', EDGE = 'radio/case_edge', TRIM = 'radio/trim', CLOTH = 'radio/cloth', KNOB = 'radio/knob';
const POINTER = 'radio/pointer', STEEL = 'radio/steel', CHROME = 'radio/chrome', NEEDLE = 'radio/needle';

// the cube model's pixels -> block units (1 px = 2.5 cm), the body centred in the cell
const K = 0.025;
const X = x => 0.5 + (x - 8) * K, Y = y => y * K, Z = z => 0.5 + (z - 8.5) * K, L = v => v * K;
const P = (x, y, z) => [X(x), Y(y), Z(z)];

/** A polygon whose corners are reordered so its Newell normal points along `out` (explicit UVs follow). */
function polyOut(pc, t, pts, out, opts = {}) {
  if (dot(newell(pts), out) < 0) { pts = [...pts].reverse(); if (opts.uv) opts = {...opts, uv: [...opts.uv].reverse()}; }
  return pc.poly(t, pts, opts);
}
/** A box with its edges chamfered r (as tools/build-box-stove-v1.mjs cbox), the faces in t, the bevels and corners in e. */
function cbox(pc, t, e, lo, hi, r) {
  const c = lo.map((v, i) => (v + hi[i]) / 2), h = lo.map((v, i) => (hi[i] - v) / 2);
  r = Math.min(r, ...h.map(v => v * 0.45));
  const Q = (s, m) => [0, 1, 2].map(a => c[a] + s[a] * (m.includes(a) ? h[a] : h[a] - r));
  const sg = [-1, 1];
  for (let a = 0; a < 3; a++) for (const s1 of sg) {
    const [b, d] = [0, 1, 2].filter(k => k !== a), pts = [];
    for (const [i, j] of [[-1, -1], [1, -1], [1, 1], [-1, 1]]) { const s = [0, 0, 0]; s[a] = s1; s[b] = i; s[d] = j; pts.push(Q(s, [a])); }
    const out = [0, 0, 0]; out[a] = s1;
    polyOut(pc, t, pts, out);
  }
  if (r <= 0) return pc;
  for (let a = 0; a < 3; a++) for (let b = a + 1; b < 3; b++) for (const sa of sg) for (const sb of sg) {
    const k = 3 - a - b, s = q => { const v = [0, 0, 0]; v[a] = sa; v[b] = sb; v[k] = q; return v; };
    const out = [0, 0, 0]; out[a] = sa; out[b] = sb;
    polyOut(pc, e, [Q(s(-1), [a]), Q(s(1), [a]), Q(s(1), [b]), Q(s(-1), [b])], out);
  }
  for (const sx of sg) for (const sy of sg) for (const sz of sg) polyOut(pc, e, [Q([sx, sy, sz], [0]), Q([sx, sy, sz], [1]), Q([sx, sy, sz], [2])], [sx, sy, sz]);
  return pc;
}
/** Boxes in cube pixels for solid(): [tex, lo, hi]. */
const B = (t, lo, hi) => [t, P(...lo), P(...hi)];

/**
 * A dome on a flat base facing `dir` ('-z' | '+z' | '+y' | '+x'), base centre c (block units), radius r, height h; smooth.
 * Screw heads (h about a third of r) and the power lamp's lens.
 */
function dome(pc, t, dir, c, r, h, seg = 12, rings = 3) {
  const axis = {'-z': [0, 0, -1], '+z': [0, 0, 1], '+y': [0, 1, 0], '+x': [1, 0, 0]}[dir];
  const u = Math.abs(axis[1]) > 0.5 ? [1, 0, 0] : [0, 1, 0], w = [axis[1] * u[2] - axis[2] * u[1], axis[2] * u[0] - axis[0] * u[2], axis[0] * u[1] - axis[1] * u[0]];
  const at = (k, i) => {   // ring k (0 base .. rings apex), corner i
    const phi = k / rings * Math.PI / 2, a = i / seg * Math.PI * 2, rr = r * Math.cos(phi), up = h * Math.sin(phi);
    const p = add(add(c, scl(axis, up)), add(scl(u, rr * Math.cos(a)), scl(w, rr * Math.sin(a))));
    const n = norm(add(scl(axis, Math.sin(phi) * r / h), scl(add(scl(u, Math.cos(a)), scl(w, Math.sin(a))), Math.cos(phi))));
    return {p, n};
  };
  for (let k = 0; k < rings - 1; k++) for (let i = 0; i < seg; i++) {
    const q = [at(k, i), at(k, i + 1), at(k + 1, i + 1), at(k + 1, i)], mid = scl(q.reduce((s, v) => add(s, v.n), [0, 0, 0]), 0.25);
    let pts = q.map(v => v.p), ns = q.map(v => v.n);
    if (dot(newell(pts), mid) < 0) { pts.reverse(); ns.reverse(); }
    pc.poly(t, pts, {n: ns});
  }
  const top = Array.from({length: seg}, (_, i) => at(rings - 1, i));
  let pts = top.map(v => v.p), ns = top.map(() => axis);
  if (dot(newell(pts), axis) < 0) { pts.reverse(); ns.reverse(); }
  pc.poly(t, pts, {n: ns});
  return pc;
}

/**
 * A knurled band round the z axis (the knobs' grip): `ridges` flat-topped ridges between r0 (root) and r1 (crest), from z
 * a (front, a < b) to b, the front face closed (a disc inside the roots plus each ridge's end).
 */
function knurl(pc, t, cx, cy, a, b, r0, r1, ridges) {
  const prof = [];
  for (let i = 0; i < ridges; i++) {
    const s = i / ridges * Math.PI * 2, st = Math.PI * 2 / ridges;
    prof.push([r0, s], [r1, s + 0.22 * st], [r1, s + 0.78 * st]);
  }
  const pt = ([r, ang], z) => [cx + r * Math.cos(ang), cy + r * Math.sin(ang), z];
  for (let i = 0; i < prof.length; i++) {
    const p0 = prof[i], p1 = prof[(i + 1) % prof.length], mid = (p0[1] + p1[1] + (i + 1 === prof.length ? Math.PI * 2 : 0)) / 2;
    polyOut(pc, t, [pt(p0, a), pt(p1, a), pt(p1, b), pt(p0, b)], [Math.cos(mid), Math.sin(mid), 0]);
  }
  polyOut(pc, t, Array.from({length: ridges}, (_, i) => pt([r0, i / ridges * Math.PI * 2], a)), [0, 0, -1]);
  for (let i = 0; i < ridges; i++) {
    const k = i * 3;
    polyOut(pc, t, [pt(prof[k], a), pt(prof[k + 1], a), pt(prof[k + 2], a), pt(prof[(k + 3) % prof.length], a)], [0, 0, -1]);
  }
  return pc;
}
/** A frustum round the z axis from (ra at za) to (rb at zb), smooth, no caps. */
function frustum(pc, t, cx, cy, za, ra, zb, rb, seg) {
  const slope = (rb - ra) / (zb - za);   // dr/dz: the normal leans against it
  for (let i = 0; i < seg; i++) {
    const a0 = i / seg * Math.PI * 2, a1 = (i + 1) / seg * Math.PI * 2;
    const p = (r, z, a) => [cx + r * Math.cos(a), cy + r * Math.sin(a), z], n = a => norm([Math.cos(a), Math.sin(a), -slope]);
    let pts = [p(ra, za, a0), p(ra, za, a1), p(rb, zb, a1), p(rb, zb, a0)], ns = [n(a0), n(a1), n(a1), n(a0)];
    if (dot(newell(pts), n((a0 + a1) / 2)) < 0) { pts.reverse(); ns.reverse(); }
    pc.poly(t, pts, {n: ns});
  }
  return pc;
}

/** A flat disc round the z axis at z, facing -z (a knob's face), projected UVs. */
function discFront(pc, t, cx, cy, z, r, seg) {
  return polyOut(pc, t, Array.from({length: seg}, (_, i) => [cx + r * Math.cos(i / seg * Math.PI * 2), cy + r * Math.sin(i / seg * Math.PI * 2), z]), [0, 0, -1]);
}

/**
 * A strap swept along a path in a plane z = zc (points in block units), its profile a chamfered rectangle: tn across the
 * path in the plane, tw along z. Smooth along the path, flat across it; open ends (they stand in the feet).
 */
function strap(pc, t, path, zc, tn, tw, ch) {
  const prof = [[tn, tw - ch], [tn - ch, tw], [-tn + ch, tw], [-tn, tw - ch], [-tn, -tw + ch], [-tn + ch, -tw], [tn - ch, -tw], [tn, -tw + ch]];
  const tang = path.map((p, i) => norm(sub(path[Math.min(i + 1, path.length - 1)], path[Math.max(i - 1, 0)])));
  const nrm = tang.map(([tx, ty]) => [-ty, tx, 0]);   // the in-plane normal, out of the handle's loop
  const at = (i, [a, b]) => [path[i][0] + nrm[i][0] * a, path[i][1] + nrm[i][1] * a, zc + b];
  for (let j = 0; j < prof.length; j++) {
    const p0 = prof[j], p1 = prof[(j + 1) % prof.length], en = [p1[1] - p0[1], p0[0] - p1[0]];   // the profile edge's outward normal (CCW)
    const l = Math.hypot(...en), e2 = [en[0] / l, en[1] / l];
    const dir = i => norm([nrm[i][0] * e2[0], nrm[i][1] * e2[0], e2[1]]);
    for (let i = 0; i + 1 < path.length; i++) {
      let pts = [at(i, p0), at(i, p1), at(i + 1, p1), at(i + 1, p0)], ns = [dir(i), dir(i), dir(i + 1), dir(i + 1)];
      if (dot(newell(pts), add(ns[0], ns[2])) < 0) { pts.reverse(); ns.reverse(); }
      pc.poly(t, pts, {n: ns});
    }
  }
  return pc;
}

// =============================================================================================== the parts (cube pixels)

/** The speaker: plate x 8.4..15.6, y 0.4..7.6, 2.5 mm proud; 12 slots 3 mm high on a 6.5 mm pitch in a rounded square. */
const SPK = {x0: 8.4, x1: 15.6, y0: 0.4, y1: 7.6, front: 4.8, floor: 4.97, cx: 12, cy: 4, half: 3.1, r: 1.7, slots: 12, pitch: 0.52, h: 0.24};
function slotHalf(dy) {
  const a = Math.abs(dy), k = SPK.half - SPK.r;
  return a <= k ? SPK.half : k + Math.sqrt(Math.max(0, SPK.r ** 2 - (a - k) ** 2));
}
function speaker(pc) {
  const {x0, x1, y0, y1, front, floor, cx, cy} = SPK, boxes = [];
  let y = y0;
  for (let i = 0; i < SPK.slots; i++) {
    const c = cy + (i - (SPK.slots - 1) / 2) * SPK.pitch, s0 = c - SPK.h / 2, s1 = c + SPK.h / 2, hl = slotHalf(Math.abs(c - cy) + SPK.h / 2);
    boxes.push(B(TRIM, [x0, y, front], [x1, s0, 5]));
    boxes.push(B(TRIM, [x0, s0, front], [cx - hl, s1, 5]), B(TRIM, [cx + hl, s0, front], [x1, s1, 5]), B(CLOTH, [cx - hl, s0, floor], [cx + hl, s1, 5]));
    y = s1;
  }
  boxes.push(B(TRIM, [x0, y, front], [x1, y1, 5]));
  solid(pc, boxes);
  for (const [sx, sy] of [[8.85, 0.85], [15.15, 0.85], [8.85, 7.15], [15.15, 7.15]]) dome(pc, STEEL, '-z', P(sx, sy, front), L(0.2), L(0.08), 10, 2);
}

/** The dial window: bezel 0.6..7.92 x 4.9..7.42, 3.75 mm proud; the face (own sprite) 0.9..7.62 x 5.2..7.12 at z 4.95. */
export const DIAL = {x0: 0.9, x1: 7.62, y0: 5.2, y1: 7.12, face: 4.95};
function dialBezel(pc) {
  const {x0, x1, y0, y1} = DIAL, f = 4.7;
  solid(pc, [B(TRIM, [0.6, 4.9, f], [7.92, y0, 5]), B(TRIM, [0.6, y1, f], [7.92, 7.42, 5]), B(TRIM, [0.6, y0, f], [x0, y1, 5]), B(TRIM, [x1, y0, f], [7.92, y1, 5])]);
  const nx = x1 - NEEDLE_U * (x1 - x0);   // u runs from the viewer's left (+x) to the right
  solid(pc, [B(NEEDLE, [nx - 0.045, y0 + 0.06, 4.86], [nx + 0.045, y1 - 0.06, 4.89])]);
}
/** The dial face (off or lit) and the power lamp's lens: the parts that change with the power. */
function dialPiece(lit) {
  const pc = new Piece(lit ? 'radio/dial_lit' : 'radio/dial_off'), {x0, x1, y0, y1, face} = DIAL;
  polyOut(pc, lit ? 'radio/dial_glow' : 'radio/dial', [P(x1, y1, face), P(x0, y1, face), P(x0, y0, face), P(x1, y0, face)], [0, 0, -1],
    {uv: [[0, 0], [1, 0], [1, 1], [0, 1]]});
  dome(pc, lit ? 'radio/lamp_glow' : 'radio/lamp', '-z', P(LAMP.x, LAMP.y, LAMP.z), L(0.21), L(0.12), 12, 3);
  return pc;
}

/** The knobs: TUNING (big, viewer's right) and VOLUME, knurled bakelite on a skirt, a cream pointer up; the lamp's bezel. */
const KNOBS = [
  {x: 2.4, y: 2.4, skirt: [1.37, 4.55], r: [1.12, 1.2], ridges: 24, band: 3.98, collar: [1.1, 3.86], cap: [0.98, 3.78], ptr: [0.07, 0.35, 0.92]},
  {x: 6.9, y: 2.55, skirt: [0.95, 4.72], r: [0.75, 0.8], ridges: 18, band: 4.2, collar: [0.74, 4.12], cap: [0.66, 4.06], ptr: [0.055, 0.25, 0.62]},
];
const LAMP = {x: 5.0, y: 2.1, z: 4.86};
function knobs(pc) {
  for (const k of KNOBS) {
    const c = [X(k.x), Y(k.y)];
    cylinder(pc, KNOB, KNOB, 'z', c, Z(k.skirt[1]), Z(5), L(k.skirt[0]), 32, 'a');
    knurl(pc, KNOB, c[0], c[1], Z(k.band), Z(k.skirt[1]), L(k.r[0]), L(k.r[1]), k.ridges);
    cylinder(pc, KNOB, KNOB, 'z', c, Z(k.collar[1]), Z(k.band), L(k.collar[0]), 24);
    frustum(pc, KNOB, c[0], c[1], Z(k.collar[1]), L(k.collar[0]), Z(k.cap[1]), L(k.cap[0]), 24);
    discFront(pc, KNOB, c[0], c[1], Z(k.cap[1]), L(k.cap[0]), 24);
    const [w, p0, p1] = k.ptr;
    solid(pc, [B(POINTER, [k.x - w, k.y + p0, k.cap[1] - 0.04], [k.x + w, k.y + p1, k.cap[1]])]);
  }
  cylinder(pc, CHROME, CHROME, 'z', [X(LAMP.x), Y(LAMP.y)], Z(LAMP.z), Z(5), L(0.3), 16, 'a');
}

/** The AM / FM / OFF slide switch: a black plate with a slot (its floor dark), the slider at OFF (viewer's right) or FM. */
const SWITCH = {x0: 4.1, x1: 5.9, y0: 0.72, y1: 1.48, sx0: 4.3, sx1: 5.7, sy0: 0.98, sy1: 1.22, front: 4.88, floor: 4.96};
export const SLIDER = {off: 4.55, fm: 5.0};
function switchPlate(pc) {
  const s = SWITCH;
  solid(pc, [B(TRIM, [s.x0, s.y0, s.front], [s.x1, s.sy0, 5]), B(TRIM, [s.x0, s.sy1, s.front], [s.x1, s.y1, 5]),
    B(TRIM, [s.x0, s.sy0, s.front], [s.sx0, s.sy1, 5]), B(TRIM, [s.sx1, s.sy0, s.front], [s.x1, s.sy1, 5]), B(CLOTH, [s.sx0, s.sy0, s.floor], [s.sx1, s.sy1, 5])]);
}
function sliderPiece(at) {
  const pc = new Piece('radio/slider_' + at), x = SLIDER[at];
  return cbox(pc, KNOB, KNOB, P(x - 0.17, 0.92, 4.6), P(x + 0.17, 1.28, SWITCH.floor), L(0.05));
}

/** The back: a plain steel label plate with two screws, the battery door with its finger lip, a screw in each corner. */
function back(pc) {
  cbox(pc, STEEL, STEEL, P(4.5, 4.3, 12), P(11.5, 6.9, 12.08), L(0.03));
  for (const x of [5.0, 11.0]) dome(pc, STEEL, '+z', P(x, 5.6, 12.08), L(0.16), L(0.06), 10, 2);
  cbox(pc, PAINT, EDGE, P(4.5, 0.9, 12), P(11.5, 3.5, 12.05), L(0.02));
  cbox(pc, TRIM, TRIM, P(7.4, 3.12, 12.05), P(8.6, 3.32, 12.11), L(0.02));
  for (const [x, y] of [[0.75, 0.75], [15.25, 0.75], [0.75, 7.25], [15.25, 7.25]]) dome(pc, STEEL, '+z', P(x, y, 12), L(0.22), L(0.08), 10, 2);
}

/** The handle: a 4 x 11 mm steel strap bent up from two screwed feet (x 3.0..4.6 and 13.4..15.0), its top 12.7 cm up. */
const HANDLE = {left: 4.15, right: 13.85, base: 8.3, top: 10.14, bend: 0.75, z: 8.5};
function handle(pc) {
  const {left, right, base, top, bend: R} = HANDLE, pts = [[left, base], [left, top - R]];
  for (let i = 1; i <= 6; i++) { const a = Math.PI - i / 6 * Math.PI / 2; pts.push([left + R + R * Math.cos(a), top - R + R * Math.sin(a)]); }
  pts.push([right - R, top]);
  for (let i = 1; i <= 6; i++) { const a = Math.PI / 2 - i / 6 * Math.PI / 2; pts.push([right - R + R * Math.cos(a), top - R + R * Math.sin(a)]); }
  pts.push([right, base]);
  strap(pc, TRIM, pts.map(([x, y]) => [X(x), Y(y)]), Z(HANDLE.z), L(0.16), L(0.45), L(0.05));
  for (const [x0, x1, sx] of [[3.0, 4.6, 3.55], [13.4, 15.0, 14.45]]) {
    cbox(pc, TRIM, TRIM, P(x0, 8, 7.9), P(x1, 8.42, 9.1), L(0.06));
    dome(pc, STEEL, '+y', P(sx, 8.42, 8.5), L(0.2), L(0.07), 10, 2);
  }
}

/** The telescopic antenna at the back right (x 1.5, z 8.5): a black swivel base, a chrome ball joint, three tubes, a tip. */
function antenna(pc) {
  const c = [Z(8.5), X(1.5)];   // cylinder 'y' takes (z, x)
  cylinder(pc, TRIM, TRIM, 'y', c, Y(8), Y(8.3), L(0.55), 16, 'b');
  cylinder(pc, CHROME, CHROME, 'y', c, Y(8.3), Y(8.62), L(0.28), 12, 'b');
  for (const [r, a, b] of [[0.17, 8.62, 11.2], [0.2, 11.1, 11.2], [0.13, 11.2, 12.2], [0.16, 12.1, 12.2], [0.09, 12.2, 12.75], [0.17, 12.75, 13.05]])
    cylinder(pc, CHROME, CHROME, 'y', c, Y(a), Y(b), L(r), 10, 'b');
}

/** The crank on the left end (+x): a black hub with a steel cap (on the body), the arm and its knob (four quarter turns). */
export const CRANK = {y: 4.0, z: 8.5, arm: 2.5};
function crankHub(pc) {
  const c = [Y(CRANK.y), Z(CRANK.z)];
  cylinder(pc, TRIM, TRIM, 'x', c, X(16), X(16.22), L(0.75), 20, 'b');
  cylinder(pc, STEEL, STEEL, 'x', c, X(16.22), X(16.3), L(0.32), 12, 'b');
}
/** The crank's arm at quarter q: 0 down, 1 to the back, 2 up, 3 to the front (clockwise seen from its end). */
function crankPiece(q) {
  const arm = new Piece('tmp'), {y, z, arm: len} = CRANK;
  cbox(arm, STEEL, STEEL, P(16.3, y - len - 0.28, z - 0.28), P(16.5, y + 0.28, z + 0.28), L(0.05));
  dome(arm, STEEL, '+x', P(16.5, y, z), L(0.18), L(0.06), 10, 2);
  cylinder(arm, KNOB, KNOB, 'x', [Y(y - len), Z(z)], X(16.5), X(17.5), L(0.33), 14, 'b');
  const cy = Y(y), cz = Z(z), turn = ([a, b]) => { for (let k = 0; k < q; k++) [a, b] = [b, -a]; return [a, b]; };
  return new Piece('radio/crank_' + q).merge(arm, ([px, py, pz]) => { const [a, b] = turn([py - cy, pz - cz]); return [px, cy + a, cz + b]; },
    ([nx, ny, nz]) => { const [a, b] = turn([ny, nz]); return [nx, a, b]; });
}

function bodyPiece() {
  const pc = new Piece('radio/body');
  cbox(pc, PAINT, EDGE, P(0, 0, 5), P(16, 8, 12), L(0.45));
  speaker(pc);
  dialBezel(pc);
  knobs(pc);
  switchPlate(pc);
  back(pc);
  handle(pc);
  antenna(pc);
  crankHub(pc);
  return pc;
}

export function pieces() {
  return [bodyPiece(), dialPiece(false), dialPiece(true), sliderPiece('off'), sliderPiece('fm'), crankPiece(0)];
}

/** How far the radio sinks into the cell under it on each surface (EmergencyRadio.Surface#sink; the nightstand top 0.66). */
export const SURFACE_SINK = {floor: 0, table: 0.24, counter: 0.10, desk: 2.5 / 16, nightstand: 1 - 0.66};
/**
 * The crank as an animated mesh: the arm (quarter 0, down) on a bone at the hub's axis (x along the crank's shaft);
 * 'crank' wraps a whole turn, -360 degrees about x (down, to the back, up, to the front, as the old quarters), 16 ticks a
 * turn. Profiles emergency_radio_crank_<surface>.
 */
export function crankMesh() {
  return furnitureMesh(ROOT, {id: 'emergency_radio_crank', dir: 'radio', texPrefix: TEX, particle: TEX + STEEL, part: true,
    bones: [{name: 'crank', pivot: [X(16.3), Y(CRANK.y), Z(CRANK.z)], pieces: [crankPiece(0)]}],
    animations: {crank: {duration_ticks: 16, easing: 'linear', wrap: true, transforms: {crank: {rotation: [-360, 0, 0]}}}},
    lifts: Object.fromEntries(Object.entries(SURFACE_SINK).map(([k, v]) => [k, -v]))});
}

// =============================================================================================== models, states, loot
/** The item view: the radio off, lifted to the cell's middle (its middle is about 6.5 cm up). */
const LIFT = 0.36;
const ITEM = ['radio/body', 'radio/dial_off', 'radio/slider_off', 'radio/crank_0'].map(p => [p, 0, 0, LIFT, 0]);
const DISPLAY = {
  gui: {rotation: [25, 210, 0], translation: [0, 0, 0], scale: [1.7, 1.7, 1.7]},
  ground: {rotation: [0, 0, 0], translation: [0, 3, 0], scale: [0.5, 0.5, 0.5]},
  fixed: {rotation: [0, 0, 0], translation: [0, 0, 0], scale: [1.2, 1.2, 1.2]},
  head: {rotation: [0, 180, 0], translation: [0, 0, 0], scale: [1, 1, 1]},
  thirdperson_righthand: {rotation: [75, 45, 0], translation: [0, 2.5, 0], scale: [0.55, 0.55, 0.55]},
  thirdperson_lefthand: {rotation: [75, 45, 0], translation: [0, 2.5, 0], scale: [0.55, 0.55, 0.55]},
  firstperson_righthand: {rotation: [0, 45, 0], translation: [0, 0, 0], scale: [0.75, 0.75, 0.75]},
  firstperson_lefthand: {rotation: [0, 225, 0], translation: [0, 0, 0], scale: [0.75, 0.75, 0.75]},
};
const loot = id => json({type: 'minecraft:block', pools: [{rolls: 1, bonus_rolls: 0, entries: [{type: 'minecraft:item', name: NS + id,
  functions: [{function: 'minecraft:copy_nbt', source: 'block_entity', ops: [{source: 'Charge', target: 'BlockEntityTag.Charge', op: 'replace'}]}]}],
  conditions: [{condition: 'minecraft:survives_explosion'}]}], random_sequence: `${NS}blocks/${id}`});

export function outputs() {
  const out = [], id = BLOCK.id;
  const texOut = (name, maps) => maps.forEach((m, i) => out.push([path.join(ASSETS, 'textures/block/radio', name + ['', '_s', '_n'][i] + '.png'), m]));
  for (const [name, make] of Object.entries(RADIO_TEXTURES)) texOut(name, renderX(make(), {height: false}));
  texOut('dial', renderDial(false));
  texOut('dial_glow', renderDial(true));
  for (const pc of pieces()) out.push([path.join(ASSETS, 'cabin_mesh', pc.name + '.json'), JSON.stringify(serialise(pc, TEX)) + '\n']);
  out.push(...crankMesh().outputs);
  const model = `${NS}block/radio/${id}`;
  out.push([path.join(ASSETS, 'models/block/radio', id + '.json'), json({parent: 'minecraft:block/block', loader: NS + 'cabin',
    textures: {particle: TEX + PAINT}, item: ITEM, display: DISPLAY})]);
  out.push([path.join(ASSETS, 'blockstates', id + '.json'), json({variants: {'': {model}}})]);
  out.push([path.join(ASSETS, 'models/item', id + '.json'), json({parent: model})]);
  out.push([path.join(DATA, 'apocalypse_firstlight/loot_tables/blocks', id + '.json'), loot(id)]);
  return out;
}

// =============================================================================================== audit
const readJson = p => JSON.parse(fs.readFileSync(path.join(ROOT, p), 'utf8'));
function audit() {
  const errs = [], tags = 'src/main/resources/data/minecraft/tags/blocks/', afl = 'src/main/resources/data/apocalypse_firstlight/', b = BLOCK;
  const tagHas = (file, id) => { try { return readJson(file).values.some(v => (typeof v === 'string' ? v : v.id) === (id.includes(':') ? id : NS + id)); } catch { return false; } };
  if (!tagHas(tags + 'mineable/pickaxe.json', b.id)) errs.push(`${b.id}: missing from minecraft:mineable/pickaxe`);
  for (const t of ['mineable/axe', 'needs_stone_tool', 'needs_iron_tool', 'needs_diamond_tool']) if (tagHas(tags + t + '.json', b.id)) errs.push(`${b.id}: unexpected in ${t}`);
  if (!tagHas(`${afl}tags/blocks/noise_${b.noise}_blocks.json`, b.id)) errs.push(`${b.id}: missing from noise_${b.noise}_blocks`);
  const mass = readJson(afl + 'item_mass/afl_content_v1.json').items ?? {};
  if (mass[NS + b.id]?.unit_mass_kg !== b.mass) errs.push(`${b.id}: item mass ${mass[NS + b.id]?.unit_mass_kg} (want ${b.mass})`);
  for (const c of ['bulky', 'oversized']) if (tagHas(`${afl}tags/items/carry/${c}.json`, b.id)) errs.push(`${b.id}: unexpected carry/${c}`);
  for (const lang of ['en_us', 'zh_cn']) {
    const l = readJson(`src/main/resources/assets/apocalypse_firstlight/lang/${lang}.json`), v = l['block.apocalypse_firstlight.' + b.id];
    if (v !== (lang === 'en_us' ? b.en : b.zh)) errs.push(`${b.id}: lang ${lang} = ${v}`);
    for (const k of ['jade.apocalypse_firstlight.emergency_radio.on', 'jade.apocalypse_firstlight.emergency_radio.off', 'jade.apocalypse_firstlight.emergency_radio.empty', 'jade.apocalypse_firstlight.emergency_radio.charge',
      'hint.apocalypse_firstlight.emergency_radio.on', 'hint.apocalypse_firstlight.emergency_radio.off', 'hint.apocalypse_firstlight.emergency_radio.crank',
      'config.jade.plugin_apocalypse_firstlight.emergency_radio']) if (!l[k]) errs.push(`lang ${lang}: ${k} missing`);
  }
  if (readJson(afl + 'stamina/stamina_v1.json').costs?.crank !== CRANK_COST) errs.push(`stamina_v1.json costs.crank != ${CRANK_COST}`);
  return errs;
}

const isMain = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  const out = outputs();
  if (process.argv.includes('--check')) {
    const stale = out.filter(([file, data]) => { const cur = fs.existsSync(file) ? fs.readFileSync(file) : null; return !cur || !cur.equals(Buffer.isBuffer(data) ? data : Buffer.from(data)); });
    for (const [file] of stale) console.log('stale ' + path.relative(ROOT, file));
    const errs = audit();
    for (const e of errs) console.log('audit: ' + e);
    if (stale.length || errs.length) process.exit(1);
    console.log(`CHECK OK (${out.length} outputs)`);
  } else {
    for (const [file] of out) fs.mkdirSync(path.dirname(file), {recursive: true});
    for (const [file, data] of out) fs.writeFileSync(file, data);
    const pcs = pieces();
    for (const p of pcs) console.log('  ' + p.name, serialise(p, TEX).quads.length);
    console.log(`wrote ${out.length} files (${pcs.length} mesh pieces)`);
    console.log('  mesh ' + JSON.stringify(crankMesh().stats));
    for (const e of audit()) console.log('audit: ' + e);
  }
}
