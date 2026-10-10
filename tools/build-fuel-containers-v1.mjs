// Fuel containers V1 (docs/models/fuel_containers_v1.md, 2026-10-05): what fuel is carried and kept in, and the hand pump
// that draws it without power. Modelled at real size (1 block = 1 m), then drawn larger than life (V1.1, see SCALE).
//   jerry_can        NATO 20 L steel can (470 x 345 x 165 mm): pressed X ribs on both faces, the weld bead round it, three
//                    handles on top, the spout with its cam-lever cap on one shoulder. Olive drab.
//   small_fuel_drum  60 L steel drum (390 mm across, 580 mm tall): rolled chimes, two rolling hoops, a 2" and a 3/4" bung. Red.
//   fuel_drum        200 L tight-head steel drum (585 mm across, 880 mm tall): the same, blue.
//   hand_fuel_pump   a rotary vane barrel pump: threaded adapter on the bung (or a long column down a fill riser), green cast
//                    housing with its end covers, the crank on the +X side (its own model: the renderer turns it), the
//                    discharge tube bending out over the front and down to the hose. Placed over a drum's 2" bung or an open
//                    fill cover; the hose itself is drawn live by client/HandFuelPumpRenderer.
// Pure Mesh + one LabPBR atlas exported as Forge OBJ block models (static, chunk-baked), as the fuel station sump set.
//   node tools/build-fuel-containers-v1.mjs            -> writes sources, OBJ / MTL / block + item models, blockstates, atlas
//   node tools/build-fuel-containers-v1.mjs --check    -> verifies every output is up to date (and the Java constants)
//   node tools/build-fuel-containers-v1.mjs --preview DIR
// Frame (px): the cell's centre is the origin, front (FACING) = -Z, y up. OBJ in block units: px / 16 + 0.5.
import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {Part, extrude, add, sub, mul, dot, cross, norm, newell, area2, unwrap, paint, png, zFightLevels, triangulate} from './cube-slab-mesh-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
function assert(c, m) { if (!c) throw new Error(m); }
const D2R = Math.PI / 180;

// ---------------- primitives ----------------
const orient = (L, ccw) => (area2(L) > 0) === ccw ? L : L.slice().reverse();
const rect = (u0, v0, u1, v1) => [[u0, v0], [u1, v0], [u1, v1], [u0, v1]];
const shape = (L, holes = []) => ({outer: orient(L, true), holes: holes.map(h => orient(h, false))});
const hex = (cu, cv, r) => Array.from({length: 6}, (_, i) => { const a = (30 + 60 * i) * D2R; return [cu + r * Math.cos(a), cv + r * Math.sin(a)]; });
// a rectangle with rounded corners (radius rr, n points a corner), counter-clockwise
function rounded(u0, v0, u1, v1, rr, n = 4) {
  const out = [], C = [[u1 - rr, v0 + rr, -90], [u1 - rr, v1 - rr, 0], [u0 + rr, v1 - rr, 90], [u0 + rr, v0 + rr, 180]];
  for (const [cu, cv, a0] of C) for (let k = 0; k <= n; k++) { const a = (a0 + 90 * k / n) * D2R; out.push([cu + rr * Math.cos(a), cv + rr * Math.sin(a)]); }
  return out;
}
// an X of two bars (half-width w) from corner to corner of the rectangle +-a, +-b, ends cut square to the bars; star-shaped
function xShape(a, b, w) {
  const len = Math.hypot(a, b), d1 = [a / len, b / len], d2 = [a / len, -b / len], n1 = [-d1[1], d1[0]], n2 = [-d2[1], d2[0]];
  const L = len - w * 0.9, pts = [];
  for (const [d, n] of [[d1, n1], [d2, n2]]) for (const s of [1, -1]) for (const t of [1, -1]) pts.push([s * L * d[0] + t * w * n[0], s * L * d[1] + t * w * n[1]]);
  // the inner corners: where an edge of one bar meets an edge of the other
  for (const s1 of [1, -1]) for (const s2 of [1, -1]) {
    const det = n1[0] * n2[1] - n1[1] * n2[0];
    pts.push([(s1 * w * n2[1] - s2 * w * n1[1]) / det, (n1[0] * s2 * w - n2[0] * s1 * w) / det]);
  }
  pts.sort((p, q) => Math.atan2(p[1], p[0]) - Math.atan2(q[1], q[0]));
  return pts;
}
const zx = L => L.map(([x, z]) => [z, x]);
const planY = (part, L, y0, y1, c = 0, holes = []) => extrude(part, 'y', shape(zx(L), holes.map(zx)), y0, y1, c);   // plan (x, z)
const frontZ = (part, L, z0, z1, c = 0, holes = []) => extrude(part, 'z', shape(L, holes), z0, z1, c);              // front (x, y)
const sideX = (part, L, x0, x1, c = 0) => extrude(part, 'x', shape(L), x0, x1, c);                                  // side (z, y)
function lathe(part, axis, c, profile, seg, caps = [true, true], phase = 0.5) {
  const at = (s, r, i) => { const a = 2 * Math.PI * (i + phase) / seg, u = r * Math.cos(a), v = r * Math.sin(a);
    return axis === 'z' ? [c[0] + u, c[1] + v, s] : axis === 'y' ? [c[0] + u, s, c[1] + v] : [s, c[0] + u, c[1] + v]; };
  const dir = (i, nr, na) => { const a = 2 * Math.PI * (i + phase) / seg, u = Math.cos(a) * nr, v = Math.sin(a) * nr;
    return axis === 'z' ? [u, v, na] : axis === 'y' ? [u, na, v] : [na, u, v]; };
  const rings = profile.map(([s, r]) => Array.from({length: seg}, (_, i) => part.vtx(at(s, r, i))));
  for (let k = 0; k + 1 < profile.length; k++) for (let i = 0; i < seg; i++) {
    const j = (i + 1) % seg, [s0, r0] = profile[k], [s1, r1] = profile[k + 1], nr = s1 - s0, na = r0 - r1;
    part.face([rings[k][i], rings[k][j], rings[k + 1][j], rings[k + 1][i]], dir(i + 0.5, Math.hypot(nr, na) > 1e-9 ? nr : 1, Math.hypot(nr, na) > 1e-9 ? na : 0));
  }
  [[0, -1], [profile.length - 1, 1]].forEach(([k, sgn], ci) => { if (!caps[ci]) return;
    const s = profile[k][0], m = part.vtx(axis === 'z' ? [c[0], c[1], s] : axis === 'y' ? [c[0], s, c[1]] : [s, c[0], c[1]]);
    for (let i = 0; i < seg; i++) part.face([m, rings[k][i], rings[k][(i + 1) % seg]], dir(0, 0, sgn), 'cap'); });
}
const cyl = (part, axis, c, s0, s1, r, seg, caps = [true, true]) => lathe(part, axis, c, [[s0, r], [s1, r]], seg, caps);
// a round tube of radius r along a path of points (rings turned to the path, parallel transported), capped at both ends
function tube(part, pathPts, r, seg, caps = [true, true]) {
  const T = pathPts.map((p, i) => norm(sub(pathPts[Math.min(i + 1, pathPts.length - 1)], pathPts[Math.max(i - 1, 0)])));
  let n = norm(cross(T[0], Math.abs(T[0][1]) < 0.9 ? [0, 1, 0] : [1, 0, 0]));
  const frames = T.map((t, i) => { if (i) n = norm(sub(n, mul(t, dot(n, t)))); return [n, cross(t, n)]; });
  const dirOf = (i, a) => add(mul(frames[i][0], Math.cos(a)), mul(frames[i][1], Math.sin(a)));
  const rings = pathPts.map((p, i) => Array.from({length: seg}, (_, k) => part.vtx(add(p, mul(dirOf(i, 2 * Math.PI * (k + 0.5) / seg), r)))));
  for (let i = 0; i + 1 < pathPts.length; i++) for (let k = 0; k < seg; k++)
    part.face([rings[i][k], rings[i][(k + 1) % seg], rings[i + 1][(k + 1) % seg], rings[i + 1][k]], dirOf(i, 2 * Math.PI * (k + 1) / seg));
  [[0, -1], [pathPts.length - 1, 1]].forEach(([i, sgn], ci) => { if (!caps[ci]) return;
    const m = part.vtx(pathPts[i]);
    for (let k = 0; k < seg; k++) part.face([m, rings[i][k], rings[i][(k + 1) % seg]], mul(T[i], sgn), 'cap'); });
}
// a quarter arc from a to b about centre c (points included at both ends)
const arc = (c, a, b, steps) => { const ua = sub(a, c), ub = sub(b, c);
  return Array.from({length: steps + 1}, (_, k) => { const t = (Math.PI / 2) * k / steps; return add(c, add(mul(ua, Math.cos(t)), mul(ub, Math.sin(t)))); }); };
const shift = (part, d) => { part.v = part.v.map(q => add(q, d)); };

// ---------------- materials ----------------
const hash = (a, b, c) => { let h = Math.imul(a | 0, 374761393) ^ Math.imul(b | 0, 668265263) ^ Math.imul(c | 0, 2147483647); h = Math.imul(h ^ (h >>> 13), 1274126177); return ((h ^ (h >>> 16)) >>> 0) / 4294967295; };
function vn(x, y, z) {
  const xi = Math.floor(x), yi = Math.floor(y), zi = Math.floor(z), s = t => t * t * (3 - 2 * t), l = (a, b, t) => a + (b - a) * t;
  const sx = s(x - xi), sy = s(y - yi), sz = s(z - zi), h = (a, b, c) => hash(a, b, c * 7919 + 101);
  return l(l(l(h(xi, yi, zi), h(xi + 1, yi, zi), sx), l(h(xi, yi + 1, zi), h(xi + 1, yi + 1, zi), sx), sy),
    l(l(h(xi, yi, zi + 1), h(xi + 1, yi, zi + 1), sx), l(h(xi, yi + 1, zi + 1), h(xi + 1, yi + 1, zi + 1), sx), sy), sz);
}
const tone = (c, k) => c.map(v => v * k), mott = (p, s, k) => k * (vn(p[0] * s, p[1] * s, p[2] * s) - 0.5);
export const MATS = {
  olive:  {c: p => tone([80, 84, 58], 1 + mott(p, 0.35, 0.05)), hl: 6, sm: 92, se: 118, f0: 22},      // military can paint, satin
  blue:   {c: p => tone([34, 72, 128], 1 + mott(p, 0.25, 0.04)), hl: 8, sm: 140, se: 158, f0: 24},     // drum enamel, semi-gloss
  red:    {c: p => tone([138, 38, 32], 1 + mott(p, 0.25, 0.04)), hl: 8, sm: 140, se: 158, f0: 24},
  green:  {c: p => tone([46, 74, 52], 1 + mott(p, 0.3, 0.05)), hl: 7, sm: 118, se: 140, f0: 24},       // the pump's cast housing
  zinc:   {c: [148, 152, 154], hl: 8, sm: 150, se: 170, f0: 255},                                      // plated caps and bung plugs
  galv:   {c: p => tone([118, 122, 124], 1 + mott(p, 0.4, 0.05)), hl: 6, sm: 120, se: 140, f0: 255},   // galvanised tubes
  grip:   {c: [30, 30, 32], hl: 3, sm: 96, se: 110, f0: 20},                                           // the crank's plastic grip
  bolt:   {c: [124, 128, 133], hl: 7, sm: 124, se: 140, f0: 30},
  rubber: {c: [28, 28, 30], hl: 0, sm: 64, se: 64, f0: 18},                                            // the hose (a swatch for the renderer)
  bore:   {c: [16, 16, 14], hl: 0, sm: 40, se: 40, f0: 18},                                            // down a filler: dark, fuel-wet
};

// ---------------- dimensions (px) ----------------
// V1.1 (2026-10-05): at real size they looked too small beside the player (user), so they are drawn larger than life: the
// jerry can 1.4 times (built at real size, then scaled about its foot), the 60 L drum 1.3 times, the 200 L drum one block
// tall (1.14 times tall, 1.2 times across; its details 1.2 times), the hand pump 1.25 times (about its foot).
export const CAN = {w: 2.76, d: 1.32, y0: -8, y1: -1.45, round: 0.45, spout: [1.75, 0], handles: [-1.95, -0.85, 0.25], scale: 1.4};
// r, top (px about the cell centre), s: the details' scale, the 2" bung's and the 3/4" vent's z, the rolling hoops' y
export const DRUM = {r: 5.6, top: 8.0, s: 1.2, bung: -3.8, vent: 3.9, hoops: [-2.67, 2.67]};          // 200 L: 585 mm x 880 mm, drawn 1.2 / 1.14 times
export const SMALL = {r: 4.06, top: 4.06, s: 1.3, bung: -2.6, vent: 2.7, hoops: [-4.0, 0.0]};          // 60 L: 390 mm x 580 mm, drawn 1.3 times
for (const D of [DRUM, SMALL]) D.head = D.top - 0.28 * D.s;
export const PUMP = {housing: 3.3, r: 1.15, crankX: 1.0, scale: 1.25};

function jerryCan(P) {
  const body = P('body', 'olive'), C = CAN;
  frontZ(body, rounded(-C.w, C.y0, C.w, C.y1, C.round), -C.d, C.d, 0.18);
  // the pressed X on both broad faces, a little proud of them
  const ribs = P('x_ribs', 'olive'), X = xShape(2.15, 2.55, 0.3).map(([u, v]) => [u, v + (C.y0 + C.y1) / 2]);
  frontZ(ribs, X, C.d - 0.02, C.d + 0.1, 0.03);
  frontZ(ribs, X, -C.d - 0.1, -C.d + 0.02, 0.03);
  // the weld bead round the middle
  const bead = P('bead', 'olive'), out = rounded(-C.w - 0.07, C.y0 + 0.02, C.w + 0.07, C.y1 + 0.07, C.round + 0.07);
  frontZ(bead, out, -0.07, 0.07, 0, [rounded(-C.w + 0.05, C.y0 + 0.1, C.w - 0.05, C.y1 - 0.05, C.round - 0.05)]);
  // three handles across the top, each a round bar bent into an inverted U across the depth (seen close in first person:
  // square-cut blocks looked cheap, 2026-10-05); the bar's middle at y -0.65, where the hand grips (author-jerry-can-first-person)
  const handles = P('handles', 'olive'), HR = 0.2, HZ = 0.92, HB = -0.65, HK = 0.22;
  for (const x of C.handles) tube(handles, [[x, C.y1 - 0.05, -HZ], ...arc([x, HB - HK, -HZ + HK], [x, HB - HK, -HZ], [x, HB, -HZ + HK], 3),
    ...arc([x, HB - HK, HZ - HK], [x, HB, HZ - HK], [x, HB - HK, HZ], 3), [x, C.y1 - 0.05, HZ]], HR, 8, [false, false]);
  // the spout on the +X shoulder: a welded boss (over the bead), the neck with its rolled lip and the bore going dark down
  // it, the plated cap (hollow: seen from below once lifted off) and the cam lever lying over it. Seen close in first person
  // with the cap off (2026-10-05: the open neck showed the flat top through it, the bead running across it)
  const [sx, sz] = C.spout;
  cyl(P('spout_boss', 'olive'), 'y', [sx, sz], C.y1 - 0.06, C.y1 + 0.13, 0.68, 20, [false, true]);
  lathe(P('spout_neck', 'olive'), 'y', [sx, sz], [[C.y1 + 0.13, 0.48], [-0.8, 0.48], [-0.77, 0.52], [-0.72, 0.52], [-0.7, 0.46], [-0.7, 0.41]], 20, [false, false]);
  lathe(P('spout_bore', 'bore'), 'y', [sx, sz], [[-0.7, 0.41], [-1.02, 0.41]], 20, [false, true]);
  lathe(P('spout_cap', 'zinc'), 'y', [sx, sz], [[-0.66, 0.54], [-1.05, 0.54], [-1.05, 0.62], [-0.6, 0.62], [-0.5, 0.52]], 20, [true, true]);
  const lever = P('cam_lever', 'zinc');
  extrude(lever, 'y', shape(zx(rect(sx - 0.95, sz - 0.12, sx, sz + 0.12))), -0.62, -0.47, 0);
  extrude(lever, 'y', shape(zx(rect(sx - 1.0, sz - 0.16, sx - 0.82, sz + 0.16))), -0.9, -0.47, 0);
}

// a steel drum: the shell (chimes, rolling hoops, the heads) and the bungs on its top head
function drum(P, D, mat, seg) {
  const r = D.r, b = -8, t = D.top, h = D.head, k = D.s;
  const prof = [[b + 0.28 * k, r - 0.38 * k], [b + 0.05 * k, r - 0.23 * k], [b, r - 0.06 * k], [b + 0.08 * k, r + 0.08 * k], [b + 0.38 * k, r + 0.10 * k], [b + 0.6 * k, r]];
  for (const y of D.hoops) prof.push([y - 0.2 * k, r], [y - 0.05 * k, r + 0.16 * k], [y + 0.25 * k, r + 0.16 * k], [y + 0.4 * k, r]);
  prof.push([t - 0.56 * k, r], [t - 0.34 * k, r + 0.10 * k], [t - 0.08 * k, r + 0.08 * k], [t, r - 0.06 * k], [t - 0.06 * k, r - 0.23 * k], [h, r - 0.38 * k]);
  const shell = P('shell', mat);
  lathe(shell, 'y', [0, 0], prof, seg, [true, false]);
  topHead(shell, D, r - 0.38 * k, seg);
  const bungs = P('bungs', 'zinc'), plug = P('bung_plug', 'zinc'), big = [0.6 * k, 0.45 * k], small = [0.38 * k, 0.27 * k];
  cyl(P('bung_flanges', mat), 'y', [0, D.bung], h - 0.04, h + 0.12 * k, big[0], 20, [false, true]);
  cyl(plug, 'y', [0, D.bung], h + 0.1 * k, h + 0.32 * k, big[1], 20, [false, true]);
  extrude(plug, 'y', shape(zx(rect(-big[1] * 0.8, D.bung - 0.06 * k, big[1] * 0.8, D.bung + 0.06 * k))), h + 0.3 * k, h + 0.44 * k, 0);
  cyl(P('vent_flange', mat), 'y', [0, D.vent], h - 0.04, h + 0.1 * k, small[0], 16, [false, true]);
  cyl(bungs, 'y', [0, D.vent], h + 0.08 * k, h + 0.26 * k, small[1], 16, [false, true]);
}
const bungTop = D => D.head + 0.12 * D.s;   // the 2" flange's top: the pump's adapter sits on it
// the top head, facing up at D.head from the shell's last ring (the lathe's own points, so no crack) to a hole for the
// 2" bung (its bore's points). A closed drum's flange covers the hole; an open drum shows its bore through it (2026-10-10:
// the head used to be the lathe's whole cap, so it shut the bore off just under the flange and the open bung looked flat)
function topHead(part, D, r, seg) {
  const ring = (cz, R, n) => Array.from({length: n}, (_, i) => { const a = 2 * Math.PI * (i + 0.5) / n; return [R * Math.cos(a), cz + R * Math.sin(a)]; });
  const {pts, tris} = triangulate(orient(ring(0, r, seg), true), [orient(ring(D.bung, BUNG_IN * D.s, 20), false)]);
  const ids = pts.map(([x, z]) => part.vtx([x, D.head, z]));
  for (const tri of tris) part.face(tri.map(i => ids[i]), [0, 1, 0], 'cap');
}

// ---------------- the caps off (FuelCanBlock OPEN, 2026-10-10) ----------------
// The user: unscrew the cap before filling and set it down beside the container. The open models are the closed ones' own
// parts (their atlas islands too) with the cap moved, plus, for a drum, its open bung: no second copy of a body in the atlas.
// The jerry can's cap with its cam lever stands on the ground beside the can on the spout's side, its rim a hair up (no
// face on the floor's plane); the neck and its dark bore show (built to be seen with the cap off). A drum's 2" plug stands
// on the head toward its middle (clear of a mounted pump's discharge, inside the 60 L head too), the bung a flange ring
// with a dark bore going down through the head's hole (topHead), as the can's neck. 2026-10-10 (the user: no inside):
// dark from just under the lip (a standing player sees the inner wall, the painted one read as a flat ring) and 1.2 x the
// detail scale under the head.
const CAN_CAP_DOWN = {x: CAN.w + 1.15, z: 0.35, lift: 0.015};
const BUNG_IN = 0.36, PLUG_DOWN = {toward: 1.6, lift: 0.015};
function openBung(P, D, mat) {
  const h = D.head, k = D.s, big = 0.6 * k;   // up the flange's outside, across its top, down its inside; then the bore
  lathe(P('flange_ring', mat), 'y', [0, D.bung], [[h - 0.04, big], [h + 0.12 * k, big], [h + 0.12 * k, BUNG_IN * k], [h + 0.06 * k, BUNG_IN * k]], 20, [false, false]);
  lathe(P('bore', 'bore'), 'y', [0, D.bung], [[h + 0.06 * k, BUNG_IN * k], [h - 1.2 * k, BUNG_IN * k]], 20, [false, true]);
}
// each open model: the pieces it takes, the parts it leaves out, the parts it moves (px, after the larger-than-life scale)
const canCapMove = () => { const [sx, sz] = CAN.spout; return mul([CAN_CAP_DOWN.x - sx, CAN.y0 + CAN_CAP_DOWN.lift / CAN.scale + 1.05, CAN_CAP_DOWN.z - sz], CAN.scale); };
const plugMove = D => [0, PLUG_DOWN.lift - 0.1 * D.s, PLUG_DOWN.toward * D.s];
export const OPEN = {
  'jerry_can/open': {bones: ['jerry_can/body'], skip: [], move: {spout_cap: canCapMove(), cam_lever: canCapMove()}},
  'small_fuel_drum/open': {bones: ['small_fuel_drum/body', 'small_fuel_drum/open_bung'], skip: ['bung_flanges'], move: {bung_plug: plugMove(SMALL)}},
  'fuel_drum/open': {bones: ['fuel_drum/body', 'fuel_drum/open_bung'], skip: ['bung_flanges'], move: {bung_plug: plugMove(DRUM)}},
};
const partKey = p => p.name.split('__').pop();
/** The parts of an open model as drawn: the skipped ones out, the moved ones as moved copies (same faces, so the same UVs). */
const openParts = (parts, spec) => parts.filter(p => spec.bones.includes(p.bone) && !spec.skip.includes(partKey(p)))
  .map(p => spec.move[partKey(p)] ? {...p, v: p.v.map(q => add(q, spec.move[partKey(p)]))} : p);

// the hand pump, in its own frame (y = 0 on the bung / riser it stands on), raised by `housing` to the housing's centre
function pumpBody(P, {housing, column, tube: suction}) {
  const H = housing;
  cyl(P('adapter', 'galv'), 'y', [0, 0], 0, 0.3, 0.66, 20, [true, true]);
  extrude(P('nut', 'galv'), 'y', shape(zx(hex(0, 0, 0.58))), 0.3, 0.55, 0.05);
  if (suction > 0) cyl(P('suction', 'galv'), 'y', [0, 0], -suction, 0.05, 0.3, 14, [true, false]);
  cyl(P('column', 'galv'), 'y', [0, 0], 0.5, H - 0.9, column, 14, [false, false]);
  lathe(P('foot', 'green'), 'y', [0, 0], [[H - 1.45, 0.48], [H - 1.15, 0.58], [H - 0.7, 0.58]], 20, [true, false]);
  const housingPart = P('housing', 'green');
  cyl(housingPart, 'x', [H, 0], -0.7, 0.7, PUMP.r, 28);
  const covers = P('covers', 'green');
  cyl(covers, 'x', [H, 0], -0.78, -0.62, PUMP.r + 0.11, 28);
  cyl(covers, 'x', [H, 0], 0.62, 0.78, PUMP.r + 0.11, 28);
  cyl(covers, 'x', [H, 0], 0.78, PUMP.crankX, 0.4, 16, [false, true]);   // the bearing boss under the crank
  const bolts = P('cover_bolts', 'bolt');
  for (const s of [-1, 1]) for (let k = 0; k < 4; k++) { const a = (45 + 90 * k) * D2R;
    cyl(bolts, 'x', [H + 0.95 * Math.cos(a), 0.95 * Math.sin(a)], s > 0 ? 0.78 : -0.86, s > 0 ? 0.86 : -0.78, 0.12, 6, [s < 0, s > 0]); }
  cyl(P('outlet_boss', 'green'), 'y', [0, 0], H + 0.9, H + 1.25, 0.42, 16, [false, true]);
  // the discharge: up, over toward the front, down to the hose barb
  const Yh = H + 1.6, bend = 0.5, bend2 = 0.45, run = -2.4;
  const pathPts = [[0, H + 0.95, 0], ...arc([0, Yh - bend, -bend], [0, Yh - bend, 0], [0, Yh, -bend], 6),
    ...arc([0, Yh - bend2, run], [0, Yh, run], [0, Yh - bend2, run - bend2], 6), [0, Yh - 0.9, run - bend2]];
  tube(P('discharge', 'galv'), pathPts.filter((p, i, a) => !i || Math.hypot(...sub(p, a[i - 1])) > 1e-6), 0.28, 14);
  cyl(P('barb', 'galv'), 'y', [0, run - bend2], Yh - 1.15, Yh - 0.85, 0.34, 14, [true, true]);
}
export const SPOUT = H => [0, H + 1.6 - 1.15, -2.4 - 0.45];   // the hose leaves the barb's lower end
// the crank, about its pivot (the origin), arm up: hub, arm, grip with its knob
function crank(P) {
  cyl(P('hub', 'green'), 'x', [0, 0], 0, 0.26, 0.36, 16);
  extrude(P('arm', 'green'), 'x', shape(rect(-0.16, -0.2, 0.16, 2.0)), 0.06, 0.32, 0.05);
  cyl(P('grip', 'grip'), 'x', [1.9, 0], 0.3, 1.15, 0.22, 14, [false, false]);
  lathe(P('knob', 'grip'), 'x', [1.9, 0], [[1.15, 0.22], [1.22, 0.26], [1.3, 0.2]], 14, [false, true]);
}

// pump placements: the pump frame's origin in the pump block (px), its housing height and column, its suction tube (the
// frame's own units: the pump is drawn PUMP.scale times about its foot, so the housing's centre ends up at.y + scale * housing)
export const MOUNTS = {
  fill: {at: [0, -15.0, 0], housing: 7.84, column: 0.4, tube: 0},                        // on the riser cap, up out of the cover
  drum: {at: [0, bungTop(DRUM) - 16, DRUM.bung], housing: PUMP.housing, column: 0.36, tube: 2.0},
  small_drum: {at: [0, bungTop(SMALL) - 16, SMALL.bung], housing: PUMP.housing, column: 0.36, tube: 2.0},
};
const ITEM_PUMP = {at: [0, -5.8, 1.5], housing: PUMP.housing, column: 0.36, tube: 1.6};

export const PIECES = {
  'jerry_can/body': P => jerryCan(P),
  'small_fuel_drum/body': P => drum(P, SMALL, 'red', 36),
  'fuel_drum/body': P => drum(P, DRUM, 'blue', 44),
  // a drum's open bung (drawn only in its open model, OPEN)
  'small_fuel_drum/open_bung': P => openBung(P, SMALL, 'red'),
  'fuel_drum/open_bung': P => openBung(P, DRUM, 'blue'),
  ...Object.fromEntries(Object.entries(MOUNTS).map(([k, m]) => [`hand_fuel_pump/body_${k}`, P => pumpBody(P, m)])),
  'hand_fuel_pump/crank': P => crank(P),
  'hand_fuel_pump/item': P => { pumpBody(P, ITEM_PUMP); },
  'hand_fuel_pump/item_crank': P => crank(P),
  'swatch/hose': P => frontZ(P('rubber', 'rubber'), rect(0, 0, 1, 1), 0, 0.01, 0),
};
// pieces drawn larger than life, about a point (built at real size): the can about its foot, the pump about its own
const SCALE = {
  'jerry_can/body': [[0, -8, 0], CAN.scale],
  ...Object.fromEntries([...Object.keys(MOUNTS).map(k => `hand_fuel_pump/body_${k}`), 'hand_fuel_pump/crank', 'hand_fuel_pump/item', 'hand_fuel_pump/item_crank']
    .map(k => [k, [[0, 0, 0], PUMP.scale]])),
};
// where a mount puts the crank's pivot and the hose barb's lower end, px about the pump cell's centre (discharge north)
export const pivotOf = m => add(m.at, mul([PUMP.crankX, m.housing, 0], PUMP.scale));
export const barbOf = m => add(m.at, mul(SPOUT(m.housing), PUMP.scale));
// pieces whose parts are moved after building (and scaling): the pump bodies to their mounts, the item's crank to its pivot
const MOVE = {
  ...Object.fromEntries(Object.entries(MOUNTS).map(([k, m]) => [`hand_fuel_pump/body_${k}`, m.at])),
  'hand_fuel_pump/item': ITEM_PUMP.at,
  'hand_fuel_pump/item_crank': pivotOf(ITEM_PUMP),
};

// ---------------- bake ----------------
const DUMMY = 'data:image/png;base64,' + png(Buffer.from([0, 0, 0, 255]), 1, 1).toString('base64');
function bake() {
  const PARTS = [];
  for (const [piece, build] of Object.entries(PIECES)) {
    const mine = [];
    const P = (name, mat) => { const p = new Part(`${piece.replace('/', '__')}__${name}`, piece, mat); PARTS.push(p); mine.push(p); return p; };
    build(P);
    if (SCALE[piece]) { const [o, k] = SCALE[piece]; for (const p of mine) p.v = p.v.map(q => add(o, mul(sub(q, o), k))); }
    if (MOVE[piece]) for (const p of mine) shift(p, MOVE[piece]);
  }
  const live = PARTS.filter(p => p.f.length);
  for (const p of live) {
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
  const atlas = 1024, UV = unwrap(live, {atlas, pad: 2, startS: 16, stepS: 0.25});
  for (const p of live) assert(p.f.every(f => UV.faceUV.get(p)?.has(f)), `unmapped face in ${p.name}`);
  const painted = paint({PARTS: live, islands: UV.islands, S: UV.S, uvOf: UV.uvOf, atlas, pad: 2, MATS, ZONED: new Set(), groupInfo: new Map(),
    sourceGroups: () => [], refTexture: {source: DUMMY}, refUvWidth: 1, background: {c: [60, 64, 52, 255], s: [100, 22, 0, 255], n: [128, 128, 255, 255]}});
  const groups = Object.keys(PIECES).filter(k => !k.startsWith('swatch') && !k.endsWith('/open_bung')).map(k => [k]);
  const coplanar = [...groups.map(g => live.filter(p => g.includes(p.bone))), ...Object.values(OPEN).map(spec => openParts(live, spec))]
    .flatMap(ps => zFightLevels(ps, new Map()).unresolved);
  // the hose swatch: the middle of its island, as 0..1 of the texture (client/HandFuelPumpRenderer HOSE_U / HOSE_V)
  const sw = UV.islands.filter(is => is.part.bone === 'swatch/hose').sort((a, b) => b.W * b.H - a.W * a.H)[0];
  const hose = [(sw.px + sw.W / 2) / atlas, (sw.py + sw.H / 2) / atlas];
  return {id: 'fuel_containers', PARTS: live, atlas, UV, maps: painted.PNG, coplanar, hose};
}

const uuidOf = (ns, s) => { const h = createHash('sha256').update(`afl-fuel-containers-v1:${ns}:${s}`).digest('hex'); return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-8${h.slice(17, 20)}-${h.slice(20, 32)}`; };
const r12 = v => +v.toFixed(12) || 0;
const SHOWN = new Set(['jerry_can/body', 'small_fuel_drum/body', 'fuel_drum/body', 'hand_fuel_pump/body_drum', 'hand_fuel_pump/crank']);
function sourceOf(b) {
  const uuid = s => uuidOf(b.id, s), name = b.id + '_v1', bones = [...new Set(b.PARTS.map(p => p.bone))];
  const elements = b.PARTS.map(p => {
    const key = i => i.toString(36).padStart(4, '0'), vertices = {}, faces = {}, uvs = b.UV.faceUV.get(p);
    p.v.forEach((q, i) => { vertices[key(i)] = q.map(r12); });
    p.f.forEach((f, fi) => { const uv = uvs.get(f); faces['f' + key(fi)] = {uv: Object.fromEntries(f.ids.map((id, j) => [key(id), uv[j].map(r12)])), vertices: f.ids.map(key), texture: 0}; });
    return {name: p.name, color: 0, origin: [0, 0, 0], rotation: [0, 0, 0], shading: 'flat', export: true, visibility: SHOWN.has(p.bone), locked: false,
      render_order: 'default', scope: 0, allow_mirror_modeling: true, vertices, faces, type: 'mesh', uuid: uuid('mesh:' + p.name)};
  });
  return {meta: {format_version: '5.0', model_format: 'free', box_uv: false}, name, model_identifier: '', visible_box: [1, 1, 0],
    variable_placeholders: '', variable_placeholder_buttons: [], timeline_setups: [], unhandled_root_fields: {}, resolution: {width: b.atlas, height: b.atlas},
    elements, groups: bones.map(bone => ({name: bone, uuid: uuid('group:' + bone), export: true, locked: false, scope: 0,
      selected: false, visibility: SHOWN.has(bone), _static: {properties: {}, temp_data: {}}, origin: [0, 0, 0], rotation: [0, 0, 0],
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
const SMOOTH = Math.cos(36 * D2R);
function cornerNormals(V, F) {
  const fn = F.map(f => newell(f.ids.map(i => V[i]))), byV = new Map();
  F.forEach((f, k) => f.ids.forEach(i => (byV.get(i) || byV.set(i, []).get(i)).push(k)));
  return F.map((f, k) => { const n0 = norm(fn[k]); return f.ids.map(i => norm(byV.get(i).reduce((a, j) => dot(norm(fn[j]), n0) >= SMOOTH ? add(a, fn[j]) : a, [0, 0, 0]))); });
}
const toCell = q => q.map(v => v / 16 + 0.5);
function objOf(b, title, file, bones, parts = null) {
  const out = [`# AFL ${title}, generated by tools/build-fuel-containers-v1.mjs`, `mtllib ${file.split('/').pop()}.mtl`];
  let vBase = 1, tBase = 1, nBase = 1;
  const original = new Map(b.PARTS.map(p => [p.name, p]));
  for (const p of parts ?? b.PARTS.filter(q => bones.includes(q.bone))) {
    const V = p.v.map(toCell), F = p.f;
    out.push(`o ${p.name}`, `usemtl ${b.id}`);
    for (const q of V) out.push(`v ${f6(q[0])} ${f6(q[1])} ${f6(q[2])}`);
    const uvs = b.UV.faceUV.get(original.get(p.name)), vt = [], vn = [], fl = [], cn = cornerNormals(V, F), nIndex = new Map();
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
const mtlOf = (b, title) => `# AFL ${title}\nnewmtl ${b.id}\nKd 1 1 1\nmap_Kd apocalypse_firstlight:block/${b.id}\n`;
const objModel = file => ({loader: 'forge:obj', model: `apocalypse_firstlight:models/block/${file}.obj`, automatic_culling: false,
  flip_v: true, shade_quads: true, ambientocclusion: false, textures: {particle: 'apocalypse_firstlight:block/fuel_containers'}});
const DIRS = ['north', 'east', 'south', 'west'], ROT = {north: 0, east: 90, south: 180, west: 270};
const ref = (model, facing) => ({model: `apocalypse_firstlight:block/${model}`, ...(ROT[facing] ? {y: ROT[facing]} : {})});
// item display: the object's centre (px from the block centre) on the slot's, at a given GUI scale. Item transforms are
// T + R(rotationXYZ) * S * p, so T = -R * s * c
function rotXYZ([ax, ay, az], v) {
  const [x, y, z] = [ax, ay, az].map(a => a * D2R);
  const rz = ([a, b, c]) => [a * Math.cos(z) - b * Math.sin(z), a * Math.sin(z) + b * Math.cos(z), c];
  const ry = ([a, b, c]) => [a * Math.cos(y) + c * Math.sin(y), b, -a * Math.sin(y) + c * Math.cos(y)];
  const rx = ([a, b, c]) => [a, b * Math.cos(x) - c * Math.sin(x), b * Math.sin(x) + c * Math.cos(x)];
  return rx(ry(rz(v)));
}
const centred = (rotation, s, c) => ({rotation, translation: rotXYZ(rotation, mul(c, s)).map(v => +(-v).toFixed(3) || 0), scale: [s, s, s]});
function display(c, size) {   // c: the object's centre (px), size: its largest extent (px)
  const g = Math.min(1.6, 0.625 * 13 / size), h = Math.min(0.9, 0.4 * 12 / size);
  return {gui: centred([30, 225, 0], +g.toFixed(3), c), fixed: centred([0, 0, 0], +(g * 0.8).toFixed(3), c),
    ground: {translation: [0, 2, 0], scale: [0.5, 0.5, 0.5]},
    thirdperson_righthand: {rotation: [0, 180, 0], translation: [0, 2.5, -1], scale: [+h.toFixed(3), +h.toFixed(3), +h.toFixed(3)]},
    thirdperson_lefthand: {rotation: [0, 180, 0], translation: [0, 2.5, -1], scale: [+h.toFixed(3), +h.toFixed(3), +h.toFixed(3)]},
    firstperson_righthand: {rotation: [0, 200, 0], translation: [2, 3, 0], scale: [+h.toFixed(3), +h.toFixed(3), +h.toFixed(3)]},
    firstperson_lefthand: {rotation: [0, 160, 0], translation: [-2, 3, 0], scale: [+h.toFixed(3), +h.toFixed(3), +h.toFixed(3)]}};
}

// ---------------- write ----------------
export const B = bake();
const bbDir = path.join(ROOT, 'src/main/blockbench'), assets = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight');
const json = v => JSON.stringify(v, null, 2) + '\n';
const outputs = [], objs = [];
const model = (file, bones, parts = null) => { const obj = objOf(B, file, file, bones, parts);
  outputs.push([path.join(assets, `models/block/${file}.obj`), obj], [path.join(assets, `models/block/${file}.mtl`), mtlOf(B, file)], [path.join(assets, `models/block/${file}.json`), json(objModel(file))]);
  objs.push([file, obj]); };
outputs.push([path.join(bbDir, 'fuel_containers_v1.bbmodel'), JSON.stringify(sourceOf(B))],
  ...['', '_s', '_n'].flatMap((k, i) => [[path.join(bbDir, `textures/fuel_containers_v1${k}.png`), B.maps[i]], [path.join(assets, `textures/block/fuel_containers${k}.png`), B.maps[i]]]));
for (const piece of Object.keys(PIECES)) if (!piece.startsWith('swatch') && piece !== 'hand_fuel_pump/item_crank' && !piece.endsWith('/open_bung'))
  model(piece, piece === 'hand_fuel_pump/item' ? [piece, 'hand_fuel_pump/item_crank'] : [piece]);
for (const [file, spec] of Object.entries(OPEN)) model(file, spec.bones, openParts(B.PARTS, spec));
// blockstates: FuelCanBlock (facing: the jerry can's broad face, the drums' 2" bung toward the front), HandFuelPumpBlock
// (facing: the discharge, mount: what it stands on)
// (open: the cap off, FuelCanBlock OPEN)
for (const id of ['jerry_can', 'small_fuel_drum', 'fuel_drum'])
  outputs.push([path.join(assets, `blockstates/${id}.json`), json({variants: Object.fromEntries(DIRS.flatMap(F => [false, true].map(o =>
    [`facing=${F},open=${o}`, ref(`${id}/${o ? 'open' : 'body'}`, F)])))})]);
outputs.push([path.join(assets, 'blockstates/hand_fuel_pump.json'), json({variants: Object.fromEntries(DIRS.flatMap(F => Object.keys(MOUNTS).map(m =>
  [`facing=${F},mount=${m}`, ref(`hand_fuel_pump/body_${m}`, F)])))})]);
const C = CAN;
// the drawn bounds of pieces (px about the cell centre): [min xyz, max xyz]
const boundsOf = bones => { const V = B.PARTS.filter(p => bones.includes(p.bone)).flatMap(p => p.v);
  return [[0, 1, 2].map(i => Math.min(...V.map(q => q[i]))), [0, 1, 2].map(i => Math.max(...V.map(q => q[i])))]; };
const itemDisplay = bones => { const [lo, hi] = boundsOf(bones); return display(lo.map((v, i) => (v + hi[i]) / 2), Math.max(...hi.map((v, i) => v - lo[i]))); };
outputs.push([path.join(assets, 'models/item/jerry_can.json'), json({parent: 'apocalypse_firstlight:block/jerry_can/body', gui_light: 'side', display: itemDisplay(['jerry_can/body'])})],
  [path.join(assets, 'models/item/small_fuel_drum.json'), json({parent: 'apocalypse_firstlight:block/small_fuel_drum/body', gui_light: 'side', display: itemDisplay(['small_fuel_drum/body'])})],
  [path.join(assets, 'models/item/fuel_drum.json'), json({parent: 'apocalypse_firstlight:block/fuel_drum/body', gui_light: 'side', display: itemDisplay(['fuel_drum/body'])})],
  [path.join(assets, 'models/item/hand_fuel_pump.json'), json({parent: 'apocalypse_firstlight:block/hand_fuel_pump/item', gui_light: 'side',
    display: itemDisplay(['hand_fuel_pump/item', 'hand_fuel_pump/item_crank'])})]);

// the Java side reads these (--check finds each line in its file): the renderer's hose swatch, crank pivots and hose barbs;
// the containers' boxes (outline, fuel) and openings; the pump's outline per mount (the part inside its own cell)
const JAVA = 'src/main/java/com/antaurora/apofirstlight/';
const n2 = v => { const r = +v.toFixed(2); return Number.isInteger(r) ? r.toFixed(1) : String(r); };
const arr = a => '{' + a.map(n2).join(', ') + '}';
const cellBox = ([lo, hi], pad = 0) => [lo[0] + 8 - pad, Math.max(0, lo[1] + 8), lo[2] + 8 - pad, hi[0] + 8 + pad, Math.min(16, hi[1] + 8), hi[2] + 8 + pad];
const SIZES = {JERRY_CAN: ['jerry_can/body', 20, [C.spout[0] * C.scale, -8 + (-0.5 + 8) * C.scale, 0], [-C.w * C.scale + 0.15, -8 + 0.15, -C.d * C.scale + 0.15, C.w * C.scale - 0.15, -8 + (C.y1 + 8) * C.scale - 0.1, C.d * C.scale - 0.15]],
  SMALL_DRUM: ['small_fuel_drum/body', 60, [0, SMALL.head + 0.44 * SMALL.s, SMALL.bung], [-SMALL.r + 0.3, -7.6, -SMALL.r + 0.3, SMALL.r - 0.3, SMALL.head - 0.1, SMALL.r - 0.3]],
  DRUM: ['fuel_drum/body', 200, [0, DRUM.head + 0.44 * DRUM.s, DRUM.bung], [-DRUM.r + 0.3, -7.6, -DRUM.r + 0.3, DRUM.r - 0.3, DRUM.head - 0.1, DRUM.r - 0.3]]};
const sizeLine = ([name, [bone, cap, open, fuel]]) => `${name}(${cap}, new double[]${arr(cellBox(boundsOf([bone])))}, new double[]${arr(fuel.map(v => v + 8))}, new Vec3(${open.map(n2).join(', ')}))`;
const pumpBox = k => { const m = MOUNTS[k], [lo, hi] = boundsOf([`hand_fuel_pump/body_${k}`]), pv = pivotOf(m), reach = (2.0 + 0.26) * PUMP.scale;
  const L = [Math.min(lo[0], pv[0]), Math.min(lo[1], pv[1] - reach), Math.min(lo[2], pv[2] - reach)], H = [Math.max(hi[0], pv[0] + 1.3 * PUMP.scale), Math.max(hi[1], pv[1] + reach), Math.max(hi[2], pv[2] + reach)];
  return H[1] <= -8 + 0.5 ? null : cellBox([L, H]); };
const pumpLine = `FILL_BOX = ${arr(pumpBox('fill'))}, DRUM_BOX = ${arr(pumpBox('drum'))}, SMALL_DRUM_BOX = ${pumpBox('small_drum') ? arr(pumpBox('small_drum')) : 'null'}`;
const triple = f => '{' + Object.values(MOUNTS).map(m => arr(f(m))).join(', ') + '}';
// (the jerry can's first-person rig is tools/author-jerry-can-first-person.mjs, from this can's mesh and atlas)
const java = [
  ['client/HandFuelPumpRenderer.java', `HOSE_U = ${B.hose[0].toFixed(6)}F`], ['client/HandFuelPumpRenderer.java', `HOSE_V = ${B.hose[1].toFixed(6)}F`],
  ['client/HandFuelPumpRenderer.java', `PIVOT = ${triple(pivotOf)}`], ['block/HandFuelPumpBlock.java', `BARB = ${triple(barbOf)}`],
  ...Object.entries(SIZES).map(e => ['block/FuelCanBlock.java', sizeLine(e)]),
  ['block/HandFuelPumpBlock.java', pumpLine]];
const tris = bone => B.PARTS.filter(p => p.bone === bone).reduce((s, p) => s + p.f.reduce((t, f) => t + f.ids.length - 2, 0), 0);
export const stats = {triangles: {...Object.fromEntries(Object.keys(PIECES).map(k => [k, tris(k)])),
  ...Object.fromEntries(Object.entries(OPEN).map(([k, spec]) => [k, openParts(B.PARTS, spec).reduce((s2, p) => s2 + p.f.reduce((t, f) => t + f.ids.length - 2, 0), 0)]))}, atlas: {texelsPerPx: B.UV.S, islands: B.UV.islands.length, coplanar: B.coplanar.length}};
const isMain = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  console.log(JSON.stringify(stats));
  for (const [file, line] of java) console.log(file + ': ' + line);
  if (B.coplanar.length) console.log('COPLANAR', JSON.stringify([...new Set(B.coplanar.map(c => c.a + ' | ' + c.b))].slice(0, 12)));
  const pi = process.argv.indexOf('--preview');
  if (pi > 0) {
    const dir = process.argv[pi + 1]; fs.mkdirSync(dir, {recursive: true});
    for (const [file, obj] of objs) fs.writeFileSync(path.join(dir, file.replace('/', '_') + '.obj'), obj);
    ['', '_s', '_n'].forEach((k, i) => fs.writeFileSync(path.join(dir, `${B.id}${k}.png`), B.maps[i]));
    console.log('preview written to ' + dir);
  } else if (process.argv.includes('--check')) {
    for (const [file, data] of outputs) { const cur = fs.existsSync(file) ? fs.readFileSync(file) : null; if (!cur || !cur.equals(Buffer.isBuffer(data) ? data : Buffer.from(data))) throw new Error('stale ' + path.relative(ROOT, file)); }
    for (const [file, line] of java) assert(fs.readFileSync(path.join(ROOT, JAVA, file), 'utf8').includes(line), file + ' lacks ' + line);
    console.log('CHECK OK');
  } else {
    for (const [file] of outputs) fs.mkdirSync(path.dirname(file), {recursive: true});
    for (const [file, data] of outputs) fs.writeFileSync(file, data);
    console.log('wrote ' + outputs.length + ' files');
  }
}
