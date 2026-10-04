// Checkout Counter V1: a modular, self-connecting store checkout counter (plain and impulse-display fronts, straight /
// outer corner / inner corner pieces, finished end caps, a pass-through gate) and the back-bar wall shelf behind the
// cashier, as Pure Mesh + LabPBR atlases exported as Forge OBJ block models (static props: chunk-baked; the goods on the
// display trays, in the cubbies and on the back bar are the shared goods library, drawn by the block entity renderers).
//   node tools/build-checkout-counter-v1.mjs                -> writes sources, OBJ / MTL / block + item models, blockstates, atlases
//   node tools/build-checkout-counter-v1.mjs --check        -> verifies every output is up to date
//   node tools/build-checkout-counter-v1.mjs --preview DIR  -> writes only OBJ + maps into DIR (offline review)
// Frame (px): the cell's bottom centre at the origin (x, z -8..8, y up), facing north: the customer side toward -Z, the
// cashier side toward +Z. OBJ in block units: x = px / 16 + 0.5, y = px / 16, z = px / 16 + 0.5.
// Counter pieces (CheckoutCounterBlock computes the shape and the four connection flags; the blockstates here place them):
//   straight  : customer panel at z -5, top overhanging to -5.6, open cubbies (two bays, one shelf) toward the cashier;
//   outer     : the convex corner, customer faces toward -Z and +X, the run continuing toward -X, the leg toward +Z;
//   inner     : the concave corner, customer faces toward -Z (for x < -5) and -X (for z < -5), the run continuing toward
//               -X, the leg toward -Z, closed panels toward the cashier (+Z, +X);
//   cap_left  : the finished end at x -8 of a run facing north (cap_right is its mirror at +8); corners use the same caps
//               turned to their line's facing;
//   rack      : three impulse trays on the customer face of a straight display piece;
//   gate      : the pass-through piece, hinge at -X: a lift-up flap (the top section, y 15..16) and a swing door below; open, the
//               flap lies folded over the neighbour on the hinge side and the door stands along the hinge side. Animated
//               (2026-10-04): an AFL Animated Block Mesh per hinge side (geo / sidecar / profile checkout_counter_gate_left /
//               _right, bones 'flap' and 'door', channels of the same names), drawn by the block entity renderer; the
//               block model is particle-only and the item model is the closed gate as an OBJ.
// Back bar: one cell wide, two tall (the lower half's model carries it): a closed base cabinet with a worktop, four shallow
// shelves with blank price channels, a dark blank price header.
// Plain surfaces: no printed legends, logos or wear. Base colours carry a low-frequency texture (laminate grain, solid-
// surface clouding, brushed aluminium), never per-pixel noise.
import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {Part, AX, extrude, add, sub, mul, dot, cross, norm, newell, area2, unwrap, paint, png, zFightLevels} from './cube-slab-mesh-lib.mjs';
import {convert, serializeCompact} from './export-afl-mesh.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
function assert(c, m) { if (!c) throw new Error(m); }
const D2R = Math.PI / 180;

// ---------------- primitives ----------------
const orient = (L, ccw) => (area2(L) > 0) === ccw ? L : L.slice().reverse();
const rect = (u0, v0, u1, v1) => [[u0, v0], [u1, v0], [u1, v1], [u0, v1]];
const shape = L => ({outer: orient(L, true), holes: []});
function slab(part, ax, a, b, c = 0) {
  const [ku, kv, ka] = ax === 'x' ? [2, 1, 0] : ax === 'y' ? [2, 0, 1] : [0, 1, 2];
  extrude(part, ax, shape(rect(a[ku], a[kv], b[ku], b[kv])), a[ka], b[ka], c);
}
const planY = (part, L, y0, y1, c) => extrude(part, 'y', shape(L.map(([x, z]) => [z, x])), y0, y1, c);   // plan (x, z)
const sideX = (part, L, x0, x1, c) => extrude(part, 'x', shape(L), x0, x1, c);                            // section (z, y)
const sideZ = (part, L, z0, z1, c) => extrude(part, 'z', shape(L), z0, z1, c);                            // section (x, y)
function cyl(part, ax, cu, cv, r, a0, a1, seg, tag = 'side') {
  const A = AX[ax], ang = i => Math.PI / seg + 2 * Math.PI * i / seg;
  const rg = a => Array.from({length: seg}, (_, i) => part.vtx(A.to3(cu + r * Math.cos(ang(i)), cv + r * Math.sin(ang(i)), a)));
  const r0 = rg(a0), r1 = rg(a1);
  for (let i = 0; i < seg; i++) { const j = (i + 1) % seg, m = (ang(i) + ang(j)) / 2;
    part.face([r0[i], r0[j], r1[j], r1[i]], A.to3(Math.cos(m), Math.sin(m), 0), tag); }
  for (const [ring, a, s] of [[r0, a0, -1], [r1, a1, 1]]) { const c = part.vtx(A.to3(cu, cv, a));
    for (let i = 0; i < seg; i++) part.face([c, ring[i], ring[(i + 1) % seg]], mul(A.n, s), 'cap'); }
}
// a band of width w on the outer side of an open polyline (plan x, z), as a closed outline: the polyline plus its offset
function band(pts, w, side = 1) {
  const off = pts.map((p, i) => {
    const a = pts[Math.max(0, i - 1)], b = pts[Math.min(pts.length - 1, i + 1)], d = norm([b[0] - a[0], 0, b[1] - a[1]]);
    let nx = d[2] * side, nz = -d[0] * side;   // left normal of the direction (x, z) for side 1
    if (i > 0 && i < pts.length - 1) {          // mitre at inner vertices
      const d0 = norm([pts[i][0] - pts[i - 1][0], 0, pts[i][1] - pts[i - 1][1]]), d1 = norm([pts[i + 1][0] - pts[i][0], 0, pts[i + 1][1] - pts[i][1]]);
      const n0 = [d0[2] * side, -d0[0] * side], n1 = [d1[2] * side, -d1[0] * side], m = norm([n0[0] + n1[0], 0, n0[1] + n1[1]]), k = 1 / Math.max(0.3, m[0] * n0[0] + m[2] * n0[1]);
      nx = m[0] * k; nz = m[2] * k;
    }
    return [p[0] + nx * w, p[1] + nz * w];
  });
  return [...pts, ...off.reverse()];
}
// quarter arc points from angle a0 to a1 (degrees, 0 = +x, 90 = +z) around (cx, cz)
const arc = (cx, cz, r, a0, a1, n = 5) => Array.from({length: n + 1}, (_, i) => { const a = (a0 + (a1 - a0) * i / n) * D2R; return [cx + r * Math.cos(a), cz + r * Math.sin(a)]; });

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
// the long horizontal axis of a part (0 = x, 2 = z), cached: grain and brushing run along it
const longAxis = part => part.__long ??= (() => { const e = k => Math.max(...part.v.map(q => q[k])) - Math.min(...part.v.map(q => q[k])); return e(2) > e(0) ? 2 : 0; })();
// along: coordinate along the grain, across: the two others
const grain = (pos, part, sAlong, sAcross) => { const a = longAxis(part), o = a === 0 ? 2 : 0; return fbm(pos[a] * sAlong, pos[1] * sAcross, pos[o] * sAcross); };

export const MATS = {   // Base Color (sRGB or a function of the model position), bevel highlight, smoothness open / edge, F0
  // dark grey laminate: a horizontal linen grain in two scales (+-5 %, +-2.5 %) and soft clouding (+-2 %), satin
  laminate: {c: (pos, n, part) => tone([60, 62, 66], 1 + 0.10 * (grain(pos, part, 0.18, 1.2) - 0.5) + 0.05 * (grain(pos, part, 0.5, 3.2) - 0.5)
    + 0.04 * (fbm(pos[0] * 0.3, pos[1] * 0.3, pos[2] * 0.3) - 0.5)), hl: 9, sm: 98, se: 120, f0: 21},
  // solid-surface counter top: warm light grey, cloudy mottling (+-4.5 %) with softer small blotches (+-2.5 %, +-1.2 %), semi-gloss
  top:      {c: pos => tone([186, 183, 176], 1 + 0.09 * (fbm(pos[0] * 0.3, pos[1] * 0.3, pos[2] * 0.3) - 0.5) + 0.05 * (vn(pos[0] * 1.2, pos[1] * 1.2, pos[2] * 1.2) - 0.5)
    + 0.025 * (vn(pos[0] * 2.6 + 9, pos[1] * 2.6, pos[2] * 2.6) - 0.5)), hl: 8, sm: 134, se: 150, f0: 21},
  // brushed aluminium edge mouldings, pulls and price channels: streaks along the length (+-4.5 %)
  trim:     {c: (pos, n, part) => tone([164, 168, 174], 1 + 0.09 * (grain(pos, part, 0.06, 5.5) - 0.5)), hl: 14, sm: 150, se: 170, f0: 255},
  kick:     {c: [31, 32, 34], hl: 3, sm: 66, se: 74, f0: 20},          // black vinyl plinth
  shelf:    {c: pos => tone([122, 126, 132], 1 + 0.05 * (fbm(pos[0] * 0.5, pos[1] * 0.5, pos[2] * 0.5) - 0.5)), hl: 8, sm: 112, se: 132, f0: 24},   // powder-coated steel
  rack:     {c: pos => tone([86, 90, 96], 1 + 0.03 * (fbm(pos[0] * 0.6, pos[1] * 0.6, pos[2] * 0.6) - 0.5)), hl: 10, sm: 118, se: 138, f0: 24},
  header:   {c: pos => tone([38, 40, 44], 1 + 0.03 * (fbm(pos[0] * 0.25, pos[1] * 0.9, pos[2] * 0.25) - 0.5)), hl: 5, sm: 72, se: 84, f0: 20},     // blank price header, matte
  steel:    {c: [138, 142, 148], hl: 12, sm: 140, se: 160, f0: 255},   // hinge barrels
};

// ---------------- the counter ----------------
export const COUNTER = {
  // the top is flush with the block top (16 px): things placed on the counter (a cash register) stand on it
  top: [15.0, 16.0], topFront: -5.6, back: 7.6, trim: [14.85, 16.0], trimOut: 0.25,
  face: -5.0, panel: 0.8, kick: [0, 1.2], kickFront: -4.4, kickBack: 7.0,
  end: 7.2, deck: [1.2, 2.0], shelf: [7.0, 7.4], rail: [13.6, 15.0], divider: 0.4, lowTrim: [2.0, 2.35],
  cap: [8.0, 8.35],
  // display trays: [floor y, depth]
  trays: [[2.6, 2.8], [6.2, 2.6], [9.8, 2.4]], trayFloor: 0.35, trayLip: 1.0,
  // gate pivots: the flap folds over the neighbour about the line x -8, y 16; the door swings about x -7.6, z -4.6
  flapAxis: [-8, 16.0], doorAxis: [-7.6, -4.6], door: [-7.6, 7.6, 1.2, 14.8],
};
const K = COUNTER;
// the counter section profile (z, y) shared by straight, gate and the cap: top with chamfered long edges, flat ends
const topProfile = (z0, z1, y0, y1, c = 0.18) => [[z0, y0], [z1, y0], [z1, y1 - c], [z1 - c, y1], [z0 + c, y1], [z0, y1 - c]];
function counterTop(p, x0, x1) { sideX(p, topProfile(K.topFront, K.back, K.top[0], K.top[1]), x0, x1, 0); }
function frontTrim(p, x0, x1) { sideX(p, rect(K.topFront - K.trimOut, K.trim[0], K.topFront, K.trim[1]), x0, x1, 0); }
function buildCounter() {
  const PARTS = [], P = (name, mat, bone) => { const p = new Part(name, bone, mat); PARTS.push(p); return p; };
  // ---- straight ----
  {
    const B = 'straight';
    counterTop(P('top', 'top', B), -8, 8);
    frontTrim(P('top_trim', 'trim', B), -8, 8);
    sideX(P('panel', 'laminate', B), rect(K.face, K.kick[1], K.face + K.panel, K.top[0]), -8, 8, 0);
    sideX(P('low_trim', 'trim', B), rect(K.face - 0.1, K.lowTrim[0], K.face, K.lowTrim[1]), -8, 8, 0);
    sideX(P('kick', 'kick', B), rect(K.kickFront, K.kick[0], K.kickBack, K.kick[1]), -8, 8, 0);
    const ends = P('ends', 'laminate', B);
    for (const s of [-1, 1]) slab(ends, 'x', [s < 0 ? -8 : K.end, K.kick[1], K.face + K.panel], [s < 0 ? -K.end : 8, K.top[0], K.back], 0.1);
    slab(P('deck', 'shelf', B), 'y', [-K.end, K.deck[0], K.face + K.panel], [K.end, K.deck[1], K.back], 0.08);
    slab(P('divider', 'laminate', B), 'x', [-K.divider, K.deck[1], K.face + K.panel], [K.divider, K.rail[0], K.back], 0.08);
    const sh = P('shelf', 'shelf', B);
    for (const s of [-1, 1]) slab(sh, 'y', [s < 0 ? -K.end : K.divider, K.shelf[0], K.face + K.panel], [s < 0 ? -K.divider : K.end, K.shelf[1], K.back - 0.2], 0.06);
    slab(P('rail', 'laminate', B), 'z', [-K.end, K.rail[0], K.back - 0.6], [K.end, K.rail[1], K.back], 0.08);
  }
  // ---- display trays (straight display pieces) ----
  {
    const B = 'rack', trays = P('trays', 'rack', B);
    for (const [y, d] of K.trays) {
      const z0 = K.face - d;
      slab(trays, 'y', [-7.2, y, z0], [7.2, y + K.trayFloor, K.face], 0.06);
      slab(trays, 'z', [-7.2, y + K.trayFloor, z0], [7.2, y + K.trayLip, z0 + 0.3], 0.06);
      for (const s of [-1, 1]) slab(trays, 'x', [s < 0 ? -7.2 : 6.9, y + K.trayFloor, z0 + 0.3], [s < 0 ? -6.9 : 7.2, y + K.trayLip - 0.1, K.face], 0);
    }
    const posts = P('tray_posts', 'rack', B);
    for (const s of [-1, 1]) slab(posts, 'y', [s < 0 ? -7.6 : 7.2, K.trays[0][0] - 0.4, K.face - 0.55], [s < 0 ? -7.2 : 7.6, K.trays[2][0] + K.trayLip + 0.6, K.face], 0.08);
  }
  // ---- outer corner: customer faces -Z and +X ----
  {
    const B = 'outer', F = K.face, T = K.topFront, r = 1.6;
    const outline = [[-8, T], ...arc(-T - r, T + r, r, 270, 360, 6), [-T, 8], [-K.back, 8], [-K.back, K.back], [-8, K.back]];
    planY(P('o_top', 'top', B), outline, K.top[0], K.top[1], 0.18);
    const edge = [[-8, T], ...arc(-T - r, T + r, r, 270, 360, 6), [-T, 8]];
    planY(P('o_top_trim', 'trim', B), band(edge, K.trimOut, 1), K.trim[0], K.trim[1], 0);
    const panel = P('o_panel', 'laminate', B);
    slab(panel, 'x', [-8, K.kick[1], F], [-F - K.panel, K.top[0], F + K.panel], 0);
    slab(panel, 'z', [-F - K.panel, K.kick[1], F], [-F, K.top[0], 8], 0);
    planY(P('o_low_trim', 'trim', B), band([[-8, F], [-F, F], [-F, 8]], 0.1, 1), K.lowTrim[0], K.lowTrim[1], 0);
    planY(P('o_kick', 'kick', B), [[-8, K.kickFront], [-K.kickFront, K.kickFront], [-K.kickFront, 8], [-8, 8]], K.kick[0], K.kick[1], 0);
    slab(P('o_post', 'laminate', B), 'y', [-8, K.kick[1], K.back - 0.4], [-K.back + 0.4, K.top[0], 8], 0);   // closes the inner corner slit
  }
  // ---- inner corner: customer faces -Z (x < -5) and -X (z < -5), cashier panels toward +Z and +X ----
  {
    const B = 'inner', F = K.face, T = K.topFront, Bk = K.back;
    planY(P('i_top', 'top', B), [[-8, T], [T, T], [T, -8], [Bk, -8], [Bk, Bk], [-8, Bk]], K.top[0], K.top[1], 0.18);
    planY(P('i_top_trim', 'trim', B), band([[-8, T], [T, T], [T, -8]], K.trimOut, 1), K.trim[0], K.trim[1], 0);
    const panel = P('i_panel', 'laminate', B);
    slab(panel, 'x', [-8, K.kick[1], F], [F + K.panel, K.top[0], F + K.panel], 0);
    slab(panel, 'z', [F, K.kick[1], -8], [F + K.panel, K.top[0], F], 0);
    const backs = P('i_back', 'laminate', B);
    slab(backs, 'x', [-8, K.kick[1], Bk - K.panel], [Bk, K.top[0], Bk], 0.1);
    slab(backs, 'z', [Bk - K.panel, K.kick[1], -8], [Bk, K.top[0], Bk - K.panel], 0);
    planY(P('i_low_trim', 'trim', B), band([[-8, F], [F, F], [F, -8]], 0.1, 1), K.lowTrim[0], K.lowTrim[1], 0);
    planY(P('i_kick', 'kick', B), [[-8, K.kickFront], [K.kickFront, K.kickFront], [K.kickFront, -8], [K.kickBack, -8], [K.kickBack, K.kickBack], [-8, K.kickBack]], K.kick[0], K.kick[1], 0);
  }
  // ---- end cap at x -8 (mirrored for +8) ----
  slab(P('cap', 'laminate', 'cap'), 'x', [-K.cap[1], 0, K.topFront - K.trimOut], [-K.cap[0], K.trim[1], K.back], 0.12);
  // ---- gate, hinge at -X, closed: the flap and the door are separate bones (pivots GATE_PIVOTS) ----
  {
    const F = 'gate_flap', D = 'gate_door';
    counterTop(P('g_flap', 'top', F), -8, 8);
    frontTrim(P('g_flap_trim', 'trim', F), -8, 8);
    const [dx0, dx1, dy0, dy1] = K.door;
    slab(P('g_door', 'laminate', D), 'z', [dx0, dy0, K.face], [dx1, dy1, K.face + K.panel], 0.12);
    sideZ(P('g_door_trim', 'trim', D), rect(dx0 + 0.2, K.lowTrim[0], dx1 - 0.2, K.lowTrim[1]), K.face - 0.1, K.face, 0);
    slab(P('g_door_pull', 'trim', D), 'y', [5.0, 8.4, K.face + K.panel], [5.4, 10.6, K.face + K.panel + 0.5], 0.08);
    const hinges = P('g_hinges', 'steel', D);
    for (const y of [3.0, 10.2]) cyl(hinges, 'y', K.doorAxis[1], K.doorAxis[0] - 0.15, 0.32, y, y + 1.4, 10);
  }
  return {PARTS, atlas: 1024, startS: 24};
}

// ---------------- the back bar ----------------
export const BACK_BAR = {shelves: [13.5, 17.8, 22.1, 26.4], shelf: 0.4, shelfFront: 2.0, back: 7.2, header: [30, 32], worktop: [10.0, 10.8], cabinetFront: -0.4};
const BB = BACK_BAR;
function buildBackBar() {
  const PARTS = [], P = (name, mat) => { const p = new Part(name, 'back_bar', mat); PARTS.push(p); return p; };
  slab(P('backboard', 'laminate'), 'z', [-8, 0, BB.back], [8, 32, 8], 0.1);
  slab(P('carcass', 'laminate'), 'y', [-8, 1.2, BB.cabinetFront], [8, BB.worktop[0], BB.back], 0.1);
  const doors = P('doors', 'laminate');
  for (const s of [-1, 1]) slab(doors, 'z', [s < 0 ? -7.8 : 0.12, 1.4, BB.cabinetFront - 0.6], [s < 0 ? -0.12 : 7.8, BB.worktop[0] - 0.2, BB.cabinetFront], 0.12);
  const pulls = P('pulls', 'trim');
  for (const s of [-1, 1]) slab(pulls, 'y', [s < 0 ? -1.3 : 0.9, 5.8, BB.cabinetFront - 1.1], [s < 0 ? -0.9 : 1.3, 8.6, BB.cabinetFront - 0.6], 0.08);
  slab(P('kick', 'kick'), 'y', [-7.6, 0, BB.cabinetFront + 0.4], [7.6, 1.2, BB.back], 0);
  sideX(P('worktop', 'top'), topProfile(BB.cabinetFront - 0.85, BB.back, BB.worktop[0], BB.worktop[1], 0.12), -8, 8, 0);
  sideX(P('worktop_trim', 'trim'), rect(BB.cabinetFront - 1.1, BB.worktop[0] - 0.15, BB.cabinetFront - 0.85, BB.worktop[1] + 0.1), -8, 8, 0);
  const up = P('uprights', 'laminate');
  for (const s of [-1, 1]) slab(up, 'y', [s < 0 ? -8 : 7.4, BB.worktop[1], 1.4], [s < 0 ? -7.4 : 8, BB.header[0], BB.back], 0.1);
  const shelves = P('shelves', 'shelf'), rails = P('price_channels', 'trim');
  for (const y of BB.shelves) {
    slab(shelves, 'y', [-7.4, y, BB.shelfFront], [7.4, y + BB.shelf, BB.back], 0.06);
    slab(rails, 'z', [-7.4, y - 0.5, BB.shelfFront - 0.4], [7.4, y + 0.6, BB.shelfFront], 0.06);
  }
  slab(P('header', 'header'), 'z', [-8, BB.header[0], 1.2], [8, BB.header[1], BB.back], 0.1);
  return {PARTS, atlas: 1024, startS: 24};
}

// ---------------- bake: UV, LabPBR maps, editable source ----------------
const DUMMY = 'data:image/png;base64,' + png(Buffer.from([0, 0, 0, 255]), 1, 1).toString('base64');
function bake(key, id, build) {
  const {PARTS: all, atlas, startS} = build(), PARTS = all.filter(p => p.f.length);
  // Forge draws a quad as 0-1-2 / 2-3-0: split anything that is not a triangle or a strictly convex quad
  for (const p of PARTS) {
    const out = [];
    for (const f of p.f) {
      const P3 = f.ids.map(i => p.v[i]), n = newell(P3);
      const convexQuad = f.ids.length === 4 && [0, 1, 2, 3].every(k => dot(cross(sub(P3[(k + 1) % 4], P3[k]), sub(P3[(k + 2) % 4], P3[(k + 1) % 4])), n) > 1e-12);
      if (f.ids.length === 3 || convexQuad) { out.push(f); continue; }
      assert(f.ids.length === 4, `${key}: ${f.ids.length}-gon in ${p.name}`);
      const k = [0, 1, 2, 3].find(k => dot(cross(sub(P3[k], P3[(k + 3) % 4]), sub(P3[(k + 1) % 4], P3[k])), n) <= 1e-12) ?? 0;
      out.push({...f, ids: [f.ids[k], f.ids[(k + 1) % 4], f.ids[(k + 2) % 4]]}, {...f, ids: [f.ids[(k + 2) % 4], f.ids[(k + 3) % 4], f.ids[k]]});
    }
    p.f = out;
  }
  const UV = unwrap(PARTS, {atlas, pad: 2, startS, stepS: 0.25});
  for (const p of PARTS) assert(p.f.every(f => UV.faceUV.get(p)?.has(f)), `${key}: unmapped face in ${p.name}`);
  const painted = paint({PARTS, islands: UV.islands, S: UV.S, uvOf: UV.uvOf, atlas, pad: 2, MATS, ZONED: new Set(), groupInfo: new Map(),
    sourceGroups: () => [], refTexture: {source: DUMMY}, refUvWidth: 1, background: {c: [60, 62, 66, 255], s: [98, 21, 0, 255], n: [128, 128, 255, 255]}});
  // coplanar overlaps only matter inside one piece (pieces are separate models)
  const coplanar = [...new Set(PARTS.map(p => p.bone))].flatMap(bone => zFightLevels(PARTS.filter(p => p.bone === bone), new Map()).unresolved);
  return {key, id, PARTS, atlas, UV, maps: painted.PNG, coplanar};
}

const uuidOf = (ns, s) => { const h = createHash('sha256').update(`afl-checkout-counter-v1:${ns}:${s}`).digest('hex'); return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-8${h.slice(17, 20)}-${h.slice(20, 32)}`; };
const r12 = v => +v.toFixed(12) || 0;
// editable Free Model source (frame as the header, px): one group per piece
// bone pivots (px, the asset frame): the flap turns about the top edge on the hinge side (a line along z), the door about
// its vertical hinge axis
export const GATE_PIVOTS = {gate_flap: [K.flapAxis[0], K.flapAxis[1], 0], gate_door: [K.doorAxis[0], 0, K.doorAxis[1]]};
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
  return {meta: {format_version: '5.0', model_format: 'free', box_uv: false}, name, model_identifier: '', visible_box: [1, 1, 0],
    variable_placeholders: '', variable_placeholder_buttons: [], timeline_setups: [], unhandled_root_fields: {}, resolution: {width: b.atlas, height: b.atlas},
    elements, groups: bones.map(bone => ({name: bone, uuid: uuid('group:' + bone), export: true, locked: false, scope: 0,
      selected: false, visibility: true, _static: {properties: {}, temp_data: {}}, origin: (GATE_PIVOTS[bone] || [0, 0, 0]).slice(), rotation: [0, 0, 0],
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
// transforms for the posed / mirrored copies (UVs stay the source faces')
const mirrorX = q => [-q[0], q[1], q[2]];
// pieces: [{b, bones (Set), xf (part -> vertex fn or null), mirror}]; one material per baked asset
function objOf(title, file, pieces) {
  const out = [`# AFL ${title}, generated by tools/build-checkout-counter-v1.mjs`, `mtllib ${file.split('/').pop()}.mtl`];
  let vBase = 1, tBase = 1, nBase = 1;
  for (const {b, bones, xf, mirror} of pieces) for (const p of b.PARTS) {
    if (bones && !bones.has(p.bone)) continue;
    const fx = xf ? xf(p) : null, V = p.v.map(q => { let r = fx ? fx(q) : q; return mirror ? mirrorX(r) : r; });
    const F = mirror ? p.f.map(f => ({...f, ids: f.ids.slice().reverse(), src: f})) : p.f.map(f => ({...f, src: f}));
    out.push(`o ${b.key}_${p.name}${mirror ? '_m' : ''}`, `usemtl ${b.key}`);
    for (const q of V) out.push(`v ${f6(q[0] / 16 + 0.5)} ${f6(q[1] / 16)} ${f6(q[2] / 16 + 0.5)}`);
    const uvs = b.UV.faceUV.get(p), vt = [], vn = [], fl = [], cn = cornerNormals(V, F), nIndex = new Map();
    F.forEach((f, k) => {
      const uvSrc = uvs.get(f.src), uv = mirror ? uvSrc.slice().reverse() : uvSrc;
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
const mtlOf = (title, pieces) => `# AFL ${title}\n` + [...new Map(pieces.map(({b}) => [b.key, b.id])).entries()]
  .map(([k, id]) => `newmtl ${k}\nKd 1 1 1\nmap_Kd apocalypse_firstlight:block/${id}\n`).join('');
const objModel = (file, particle) => ({loader: 'forge:obj', model: `apocalypse_firstlight:models/block/${file}.obj`, automatic_culling: false,
  flip_v: true, shade_quads: true, ambientocclusion: false, textures: {particle: `apocalypse_firstlight:block/${particle}`}});
const r3 = v => +v.toFixed(3) || 0;
function guiCentred(pieces, rotation, scale) {
  const [ax, ay] = rotation.map(v => v * D2R);
  const rot = q => { const x = q[0] * Math.cos(ay) + q[2] * Math.sin(ay), z = -q[0] * Math.sin(ay) + q[2] * Math.cos(ay); return [x, q[1] * Math.cos(ax) - z * Math.sin(ax)]; };
  const pts = pieces.flatMap(({b, bones}) => b.PARTS.filter(p => !bones || bones.has(p.bone)).flatMap(p => p.v.map(q => rot([q[0], q[1] - 8, q[2]]))));
  const cx = (Math.max(...pts.map(q => q[0])) + Math.min(...pts.map(q => q[0]))) / 2, cy = (Math.max(...pts.map(q => q[1])) + Math.min(...pts.map(q => q[1]))) / 2;
  return {rotation, translation: [r3(-scale * cx), r3(-scale * cy), 0], scale: [scale, scale, scale]};
}
const S3 = v => [v, v, v];
function display(pieces, tall) {
  const s = tall ? 0.36 : 0.55, g = tall ? 0.5 : 0.62;
  return {thirdperson_righthand: {rotation: [75, 45, 0], translation: [0, 2.5, 0], scale: S3(s * 0.7)}, thirdperson_lefthand: {rotation: [75, 45, 0], translation: [0, 2.5, 0], scale: S3(s * 0.7)},
    firstperson_righthand: {rotation: [0, 45, 0], translation: [0, 1.5, 0], scale: S3(s * 0.8)}, firstperson_lefthand: {rotation: [0, 225, 0], translation: [0, 1.5, 0], scale: S3(s * 0.8)},
    gui: guiCentred(pieces, [30, 225, 0], g), ground: {translation: [0, 2, 0], scale: S3(s * 0.6)}, fixed: {rotation: [0, 180, 0], translation: [0, tall ? -4 : -1, 0], scale: S3(s)}};
}

// ---------------- blockstates ----------------
const DIRS = ['north', 'east', 'south', 'west'], ROT = {north: 0, east: 90, south: 180, west: 270};
const cw = d => DIRS[(DIRS.indexOf(d) + 1) % 4], ccw = d => DIRS[(DIRS.indexOf(d) + 3) % 4], opp = d => DIRS[(DIRS.indexOf(d) + 2) % 4];
const ref = (model, facing) => ({model: `apocalypse_firstlight:block/${model}`, ...(ROT[facing] ? {y: ROT[facing]} : {})});
/**
 * The counter's multipart: the piece for (facing, shape), the trays on straight display pieces, and the end caps on every
 * side a line ends without a neighbouring counter. A line facing L has its caps at L.ccw (cap_left turned to L) and L.cw
 * (cap_right turned to L). Shapes and their canonical frames (CheckoutCounterBlock):
 *   straight     : faces F; line F, caps at F.ccw and F.cw;
 *   outer_right  : faces F and F.cw (the outer model turned to F): run line F continuing toward F.ccw, leg line F.cw
 *                  continuing toward F.opposite; free sides: F.ccw (left cap of F) and F.opposite (right cap of F.cw);
 *   outer_left   : faces F.ccw and F (the outer model turned to G = F.ccw): free sides G.ccw (left cap of G) and G.opposite (right cap of G.cw);
 *   inner_left   : faces F and F.ccw (the inner model turned to F): run line F continuing toward F.ccw, leg line F.ccw
 *                  continuing toward F; free sides F.ccw (left cap of F) and F (right cap of F.ccw);
 *   inner_right  : faces F.cw and F (the inner model turned to G = F.cw): free sides G.ccw (left cap of G) and G (right cap of G.ccw).
 */
function counterBlockstate(display) {
  const parts = [];
  for (const F of DIRS) {
    parts.push({when: {facing: F, shape: 'straight'}, apply: ref('checkout_counter/straight', F)});
    if (display) parts.push({when: {facing: F, shape: 'straight'}, apply: ref('checkout_counter/rack', F)});
    parts.push({when: {facing: F, shape: 'straight', [ccw(F)]: 'false'}, apply: ref('checkout_counter/cap_left', F)});
    parts.push({when: {facing: F, shape: 'straight', [cw(F)]: 'false'}, apply: ref('checkout_counter/cap_right', F)});
    for (const [shape, model, G] of [['outer_right', 'outer', F], ['outer_left', 'outer', ccw(F)], ['inner_left', 'inner', F], ['inner_right', 'inner', cw(F)]]) {
      parts.push({when: {facing: F, shape}, apply: ref('checkout_counter/' + model, G)});
      if (model === 'outer') {
        parts.push({when: {facing: F, shape, [ccw(G)]: 'false'}, apply: ref('checkout_counter/cap_left', G)});
        parts.push({when: {facing: F, shape, [opp(G)]: 'false'}, apply: ref('checkout_counter/cap_right', cw(G))});
      } else {
        parts.push({when: {facing: F, shape, [ccw(G)]: 'false'}, apply: ref('checkout_counter/cap_left', G)});
        parts.push({when: {facing: F, shape, [G]: 'false'}, apply: ref('checkout_counter/cap_right', ccw(G))});
      }
    }
  }
  return {multipart: parts};
}
function gateBlockstate() {
  return {variants: {'': {model: 'apocalypse_firstlight:block/checkout_counter/gate'}}};
}

// ---------------- the animated gate (AFL Animated Block Mesh Runtime) ----------------
// Channels 'flap' and 'door', both following the block's OPEN: they start together, so the order is set by their lengths
// and easings: opening, the door swings away fast and settles (ease_out, 0.4 s) while the flap rises, turns over and
// lands on the neighbour (ease_in_out, 0.8 s); shutting, the door picks up speed and claps against its stop at 0.4 s,
// then the flap comes down onto the frame at 0.8 s. The sounds sit on these moments (tools/build-checkout-counter-gate-sounds-v1.mjs).
export const GATE_ANIMATION = {flap: {ticks: 16, easing: 'ease_in_out', degrees: 180}, door: {ticks: 8, easing: 'ease_out', degrees: -90}};
const r6 = v => +v.toFixed(6) || 0;
function gateRig(b, hinge) {
  const s = hinge === 'left' ? 1 : -1, id = 'checkout_counter_gate_' + hinge, uuid = k => uuidOf(id, k);
  const RIG = [['flap', 'gate_flap'], ['door', 'gate_door']].map(([name, bone]) => ({name, bone, origin: [GATE_PIVOTS[bone][0] * s, GATE_PIVOTS[bone][1], GATE_PIVOTS[bone][2]]}));
  const mir = q => [q[0] * s, q[1], q[2]];
  const elements = [], nodes = new Map(RIG.map(r => [r.bone, {uuid: uuid('group:' + r.name), isOpen: true, children: []}]));
  for (const p of b.PARTS.filter(p => nodes.has(p.bone))) {
    const key = i => i.toString(36).padStart(4, '0'), vertices = {}, faces = {}, uvs = b.UV.faceUV.get(p);
    p.v.forEach((q, i) => { vertices[key(i)] = mir(q).map(r12); });
    // a mirror turns the winding inside out: reverse each face's vertex order (UVs stay keyed by vertex)
    p.f.forEach((f, fi) => { const uv = uvs.get(f), ids = s > 0 ? f.ids : f.ids.slice().reverse();
      faces['f' + key(fi)] = {uv: Object.fromEntries(f.ids.map((vid, j) => [key(vid), uv[j].map(r12)])), vertices: ids.map(key), texture: 0}; });
    const eid = uuid('mesh:' + p.name);
    elements.push({name: p.name, color: 0, origin: [0, 0, 0], rotation: [0, 0, 0], shading: 'flat', export: true, visibility: true, locked: false,
      render_order: 'default', scope: 0, allow_mirror_modeling: true, vertices, faces, type: 'mesh', uuid: eid});
    nodes.get(p.bone).children.push(eid);
  }
  const source = {meta: {format_version: '5.0', model_format: 'free', box_uv: false}, name: id, model_identifier: '', visible_box: [1, 1, 0],
    variable_placeholders: '', variable_placeholder_buttons: [], timeline_setups: [], unhandled_root_fields: {}, resolution: {width: b.atlas, height: b.atlas},
    elements, groups: RIG.map(r => ({name: r.name, uuid: uuid('group:' + r.name), export: true, locked: false, scope: 0, selected: false, visibility: true,
      _static: {properties: {}, temp_data: {}}, origin: r.origin.slice(), rotation: [0, 0, 0], color: 0, children: [], reset: false, shade: true,
      mirror_uv: false, autouv: 0, isOpen: true, primary_selected: false})),
    outliner: RIG.map(r => nodes.get(r.bone)),
    textures: [{name: b.id + '.png', relative_path: `textures/${b.id}_v1.png`, folder: '', namespace: '', id: '0', group: '', scope: 0,
      width: b.atlas, height: b.atlas, uv_width: b.atlas, uv_height: b.atlas, particle: false, use_as_default: false, layers_enabled: false, sync_to_project: '',
      file_format: 'png', render_mode: 'default', render_sides: 'auto', wrap_mode: 'limited', pbr_channel: 'color', fps: 7, frame_time: 1,
      frame_order_type: 'loop', frame_order: '', frame_interpolate: false, visible: true, internal: true, saved: true, uuid: uuid('texture'),
      source: 'data:image/png;base64,' + b.maps[0].toString('base64')}],
    animations: []};
  const geo = {format_version: '1.12.0', 'minecraft:geometry': [{description: {identifier: 'geometry.' + id, texture_width: b.atlas,
    texture_height: b.atlas, visible_bounds_width: 3, visible_bounds_height: 2.5, visible_bounds_offset: [0, 0.75, 0]},
    bones: RIG.map(r => ({name: r.name, pivot: [-r.origin[0] || 0, r.origin[1], r.origin[2]]}))}]};
  const sidecar = convert(source, geo, {}, id + '.bbmodel', 2);
  // the motions as the runtime applies them (right-handed Rz / Ry about each pivot)
  const A = GATE_ANIMATION, [fx, fy] = RIG[0].origin, [dx, , dz] = RIG[1].origin;
  const flapAt = (q, deg) => { const a = deg * D2R, x = q[0] - fx, y = q[1] - fy; return [fx + x * Math.cos(a) - y * Math.sin(a), fy + x * Math.sin(a) + y * Math.cos(a), q[2]]; };
  const doorAt = (q, deg) => { const a = deg * D2R, x = q[0] - dx, z = q[2] - dz; return [dx + x * Math.cos(a) + z * Math.sin(a), q[1], dz - x * Math.sin(a) + z * Math.cos(a)]; };
  // render bounds (NORTH, block-local): every vertex swept through its whole motion, plus a margin
  const pts = [];
  for (const p of b.PARTS) for (const q0 of p.v) {
    const q = mir(q0);
    for (let k = 0; k <= 24; k++) pts.push(p.bone === 'gate_flap' ? flapAt(q, A.flap.degrees * s * k / 24) : p.bone === 'gate_door' ? doorAt(q, A.door.degrees * s * k / 24) : null);
  }
  const P = pts.filter(Boolean), m = 0.25, lo = [0, 1, 2].map(k => Math.min(...P.map(q => q[k]))), hi = [0, 1, 2].map(k => Math.max(...P.map(q => q[k])));
  const bounds = [(lo[0] - m + 8) / 16, (lo[1] - m) / 16, (lo[2] - m + 8) / 16, (hi[0] + m + 8) / 16, (hi[1] + m) / 16, (hi[2] + m + 8) / 16].map(r6);
  // closed and open, the flap and the door stay clear of each other (the flap above the top plane, the door below it)
  const flapMinY = Math.min(...b.PARTS.filter(p => p.bone === 'gate_flap').flatMap(p => p.v.map(q => q[1])));
  const doorMaxY = Math.max(...b.PARTS.filter(p => p.bone === 'gate_door').flatMap(p => p.v.map(q => q[1])));
  assert(doorMaxY < flapMinY, 'the gate door reaches into the flap');
  const profile = {format_version: 1, geometry: `apocalypse_firstlight:geo/${id}.geo.json`, texture: `apocalypse_firstlight:textures/block/${b.id}.png`,
    origin: [0, 0, 0], scale: [1, 1, 1], facing: 'horizontal', bounds,
    parts: Object.fromEntries(RIG.map(r => [r.name, {pivot: r.origin.map(v => r6(v / 16))}])),
    animations: {
      flap: {duration_ticks: A.flap.ticks, easing: A.flap.easing, transforms: {flap: {rotation: [0, 0, A.flap.degrees * s]}}},
      door: {duration_ticks: A.door.ticks, easing: A.door.easing, transforms: {door: {rotation: [0, A.door.degrees * s, 0]}}}}};
  return {id, source, geo, sidecar, profile, bounds};
}
function backBarBlockstate() {
  const variants = {};
  for (const F of DIRS) { variants[`facing=${F},half=lower`] = ref('back_bar_shelf/lower', F); variants[`facing=${F},half=upper`] = {model: 'apocalypse_firstlight:block/back_bar_shelf/upper'}; }
  return {variants};
}

// ---------------- write ----------------
const C = bake('counter', 'checkout_counter', buildCounter), BB_ = bake('back_bar', 'back_bar_shelf', buildBackBar);
const bbDir = path.join(ROOT, 'src/main/blockbench'), assets = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight');
const json = v => JSON.stringify(v, null, 2) + '\n';
const outputs = [], objs = [];
const model = (file, title, pieces, particle) => {
  const obj = objOf(title, file, pieces), mtl = mtlOf(title, pieces);
  outputs.push([path.join(assets, `models/block/${file}.obj`), obj], [path.join(assets, `models/block/${file}.mtl`), mtl],
    [path.join(assets, `models/block/${file}.json`), json(objModel(file, particle))]);
  objs.push([file, obj]);
};
for (const b of [C, BB_]) outputs.push([path.join(bbDir, `${b.id}_v1.bbmodel`), JSON.stringify(sourceOf(b))],
  ...['', '_s', '_n'].flatMap((k, i) => [[path.join(bbDir, `textures/${b.id}_v1${k}.png`), b.maps[i]], [path.join(assets, `textures/block/${b.id}${k}.png`), b.maps[i]]]));
const only = (...bones) => new Set(bones);
const T = 'Checkout Counter V1';
model('checkout_counter/straight', T + ' straight', [{b: C, bones: only('straight')}], C.id);
model('checkout_counter/rack', T + ' display trays', [{b: C, bones: only('rack')}], C.id);
model('checkout_counter/outer', T + ' outer corner', [{b: C, bones: only('outer')}], C.id);
model('checkout_counter/inner', T + ' inner corner', [{b: C, bones: only('inner')}], C.id);
model('checkout_counter/cap_left', T + ' end cap (left)', [{b: C, bones: only('cap')}], C.id);
model('checkout_counter/cap_right', T + ' end cap (right)', [{b: C, bones: only('cap'), mirror: true}], C.id);
model('checkout_counter/gate_item', T + ' gate (closed, item)', [{b: C, bones: only('gate_flap', 'gate_door')}], C.id);
outputs.push([path.join(assets, 'models/block/checkout_counter/gate.json'), json({textures: {particle: `apocalypse_firstlight:block/${C.id}`}})]);
const RIGS = ['left', 'right'].map(hinge => gateRig(C, hinge));
for (const r of RIGS) outputs.push([path.join(assets, `geo/${r.id}.geo.json`), json(r.geo)], [path.join(assets, `meshes/${r.id}.aflmesh.json`), serializeCompact(r.sidecar)],
  [path.join(assets, `block_mesh_profiles/${r.id}.json`), json(r.profile)]);
outputs.push([path.join(bbDir, 'checkout_counter_gate_v1.bbmodel'), JSON.stringify(RIGS[0].source)]);   // the left rig (the right one is its mirror)
const itemPieces = disp => [{b: C, bones: only('straight', ...(disp ? ['rack'] : []))}, {b: C, bones: only('cap')}, {b: C, bones: only('cap'), mirror: true}];
model('checkout_counter/item', T + ' item', itemPieces(false), C.id);
model('checkout_counter/item_display', T + ' item (display)', itemPieces(true), C.id);
model('back_bar_shelf/lower', 'Back Bar Shelf V1', [{b: BB_}], BB_.id);
outputs.push([path.join(assets, 'models/block/back_bar_shelf/upper.json'), json({textures: {particle: `apocalypse_firstlight:block/${BB_.id}`}})]);
const item = (id, parent, pieces, tall) => outputs.push([path.join(assets, `models/item/${id}.json`), json({parent: `apocalypse_firstlight:block/${parent}`, gui_light: 'side', display: display(pieces, tall)})]);
item('checkout_counter', 'checkout_counter/item', itemPieces(false));
item('checkout_counter_display', 'checkout_counter/item_display', itemPieces(true));
item('checkout_counter_gate', 'checkout_counter/gate_item', [{b: C, bones: only('gate_flap', 'gate_door')}]);
item('back_bar_shelf', 'back_bar_shelf/lower', [{b: BB_}], true);
outputs.push([path.join(assets, 'blockstates/checkout_counter.json'), json(counterBlockstate(false))],
  [path.join(assets, 'blockstates/checkout_counter_display.json'), json(counterBlockstate(true))],
  [path.join(assets, 'blockstates/checkout_counter_gate.json'), json(gateBlockstate())],
  [path.join(assets, 'blockstates/back_bar_shelf.json'), json(backBarBlockstate())]);

const tris = (b, bones) => b.PARTS.filter(p => !bones || bones.has(p.bone)).reduce((s, p) => s + p.f.reduce((t, f) => t + f.ids.length - 2, 0), 0);
const boundsOf = (b, bones) => { const all = b.PARTS.filter(p => !bones || bones.has(p.bone)).flatMap(p => p.v); return [0, 1, 2].map(k => [r3(Math.min(...all.map(q => q[k]))), r3(Math.max(...all.map(q => q[k])))]); };
export const stats = {
  counter: {...Object.fromEntries(['straight', 'rack', 'outer', 'inner', 'cap'].map(k => [k, {triangles: tris(C, only(k)), bounds: boundsOf(C, only(k))}])),
    gate: {triangles: tris(C, only('gate_flap', 'gate_door')), bounds: boundsOf(C, only('gate_flap', 'gate_door')), renderBounds: Object.fromEntries(RIGS.map(r => [r.id, r.bounds]))}},
  counterAtlas: {texelsPerPx: C.UV.S, islands: C.UV.islands.length, coplanar: C.coplanar.length},
  backBar: {triangles: tris(BB_), bounds: boundsOf(BB_), texelsPerPx: BB_.UV.S, islands: BB_.UV.islands.length, coplanar: BB_.coplanar.length},
};
const isMain = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  console.log(JSON.stringify(stats));
  for (const b of [C, BB_]) if (b.coplanar.length) console.log('COPLANAR ' + b.key, JSON.stringify(b.coplanar.slice(0, 8)));
  const pi = process.argv.indexOf('--preview');
  if (pi > 0) {
    const dir = process.argv[pi + 1]; fs.mkdirSync(dir, {recursive: true});
    for (const [file, obj] of objs) fs.writeFileSync(path.join(dir, file.replace('/', '_') + '.obj'), obj);
    for (const b of [C, BB_]) ['', '_s', '_n'].forEach((k, i) => fs.writeFileSync(path.join(dir, `${b.id}${k}.png`), b.maps[i]));
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
