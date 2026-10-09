// Office Props V2: modern office desk, office chair, LCD monitor, keyboard and mouse as Pure Mesh + LabPBR atlases,
// exported as Forge OBJ block models (static props: chunk-baked, no block entity, no per-frame cost). The 3-in-1
// office computer station (kept for structure generation: one block state places a whole workstation) is composed from
// the same monitor / keyboard / mouse meshes and atlases. Replaces the V1 cube models (2026-10-03); block logic, registry
// ids, block states (facing, lowered, desk part), VoxelShapes, drops and mining rules are unchanged.
//   node tools/build-office-props-v2.mjs [id ...]                -> writes sources, OBJ / MTL / block + item models, atlases
//   node tools/build-office-props-v2.mjs [id ...] --check        -> verifies every output is up to date
//   node tools/build-office-props-v2.mjs [id ...] --preview DIR  -> writes only OBJ + maps into DIR (offline review)
// ids: desk, chair, monitor, keyboard, mouse (the station is written with monitor / keyboard / mouse).
// Frame (px): the block's bottom centre at the origin (x, z -8..8, y up), as the V1 models at facing=north: the monitor
// screen faces -Z; desk users sit on the -Z side (the modesty panel is at +Z); the chair's sitter faces -Z (backrest at
// +Z); the keyboard's space bar is toward -Z, its numpad at -X (the right hand of someone facing +Z); the mouse points
// +Z. OBJ in block units: x = px / 16 + 0.5, y = px / 16, z = px / 16 + 0.5.
// Desk: 3 cells (left x -24..-8, centre, right 8..24); the centre cell's model carries the whole desk, the left / right
// models are particle-only (one seamless top instead of three cut pieces).
// Chair (2026-10-03, sit and roll): the placed block is the whole chair as a baked OBJ. Sitting turns it into the
// OfficeChairEntity, drawn from an AFL mesh sidecar (geo / aflmesh, no block profile) by client/OfficeChairRenderer with
// bones 'base' (five-star base, hub, lift shroud), 'caster_0'..'caster_4' (fork + twin wheels, pivot on the caster stem) and
// 'swivel' (piston, mechanism, seat, back, arms; pivot on the column axis).
// Plain surfaces, no printed legends, logos or wear. Coated / dielectric materials (LabPBR F0 20-22), chrome only on the
// chair's gas-lift piston.
import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {convert, serializeCompact} from './export-afl-mesh.mjs';
import {Part, AX, extrude, revolve, add, sub, mul, dot, cross, norm, newell, area2, unwrap, paint, png, zFightLevels} from './cube-slab-mesh-lib.mjs';
import {heldDisplay} from './item-held-display.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
function assert(c, m) { if (!c) throw new Error(m); }
const D2R = Math.PI / 180;

// ---------------- primitives ----------------
const orient = (L, ccw) => (area2(L) > 0) === ccw ? L : L.slice().reverse();
const rect = (u0, v0, u1, v1) => [[u0, v0], [u1, v0], [u1, v1], [u0, v1]];
// rounded rectangle, counter-clockwise, seg segments per corner
function rrect(u0, v0, u1, v1, r, seg = 4) {
  r = Math.min(r, (u1 - u0) / 2 - 1e-3, (v1 - v0) / 2 - 1e-3);
  const out = [];
  for (const [cu, cv, a0] of [[u1 - r, v1 - r, 0], [u0 + r, v1 - r, 90], [u0 + r, v0 + r, 180], [u1 - r, v0 + r, 270]])
    for (let i = 0; i <= seg; i++) { const a = (a0 + 90 * i / seg) * D2R; out.push([cu + r * Math.cos(a), cv + r * Math.sin(a)]); }
  return out;
}
const shape = L => ({outer: orient(L, true), holes: []});
// axis-aligned box between corners a and b (model xyz), chamfer c
function slab(part, ax, a, b, c = 0) {
  const [ku, kv, ka] = ax === 'x' ? [2, 1, 0] : ax === 'y' ? [2, 0, 1] : [0, 1, 2];
  extrude(part, ax, shape(rect(a[ku], a[kv], b[ku], b[kv])), a[ka], b[ka], c);
}
// plan outline (x, z) extruded along y
const planY = (part, L, y0, y1, c) => extrude(part, 'y', shape(L.map(([x, z]) => [z, x])), y0, y1, c);
// front outline (x, y) extruded along z
const frontZ = (part, L, z0, z1, c) => extrude(part, 'z', shape(L), z0, z1, c);
// side outline (z, y) extruded along x
const sideX = (part, L, x0, x1, c) => extrude(part, 'x', shape(L), x0, x1, c);
function cyl(part, ax, cu, cv, r, a0, a1, seg, tag = 'side') {
  const A = AX[ax], ang = i => Math.PI / seg + 2 * Math.PI * i / seg;
  const rg = a => Array.from({length: seg}, (_, i) => part.vtx(A.to3(cu + r * Math.cos(ang(i)), cv + r * Math.sin(ang(i)), a)));
  const r0 = rg(a0), r1 = rg(a1);
  for (let i = 0; i < seg; i++) { const j = (i + 1) % seg, m = (ang(i) + ang(j)) / 2;
    part.face([r0[i], r0[j], r1[j], r1[i]], A.to3(Math.cos(m), Math.sin(m), 0), tag); }
  for (const [ring, a, s] of [[r0, a0, -1], [r1, a1, 1]]) { const c = part.vtx(A.to3(cu, cv, a));
    for (let i = 0; i < seg; i++) part.face([c, ring[i], ring[(i + 1) % seg]], mul(A.n, s), 'cap'); }
}
// lathe about a vertical axis at (x, z): profile [[y, r], ...] as a closed loop (either winding)
function lathe(part, x, z, prof, seg, tag = 'side') {
  const t = new Part('t', '', part.mat);
  revolve(t, x, -z, area2(prof) > 0 ? prof : prof.slice().reverse(), seg, Math.PI / seg, tag);
  const base = part.v.length;
  for (const q of t.v) part.v.push([q[0], q[2], -q[1]]);   // proper rotation: revolve's +Z axis -> +Y
  for (const f of t.f) part.f.push({...f, ids: f.ids.map(i => i + base)});
}
// loft through rings of equal length (closed loops), capped at both ends
function loft(part, rings, tag = 'side') {
  const n = rings[0].length, ids = rings.map(R => R.map(p => part.vtx(p)));
  const cen = rings.map(R => mul(R.reduce((a, p) => add(a, p), [0, 0, 0]), 1 / R.length));
  for (let k = 0; k + 1 < rings.length; k++) for (let i = 0; i < n; i++) {
    const j = (i + 1) % n, Q = [rings[k][i], rings[k][j], rings[k + 1][j], rings[k + 1][i]];
    const fc = mul(Q.reduce((a, p) => add(a, p), [0, 0, 0]), 0.25);
    part.face([ids[k][i], ids[k][j], ids[k + 1][j], ids[k + 1][i]], sub(fc, mul(add(cen[k], cen[k + 1]), 0.5)), tag);
  }
  for (const [k, o] of [[0, 1], [rings.length - 1, rings.length - 2]]) {
    const c = part.vtx(cen[k]);
    for (let i = 0; i < n; i++) part.face([c, ids[k][i], ids[k][(i + 1) % n]], sub(cen[k], cen[o]), 'cap');
  }
}
// upholstered block: loft of inset outlines through a rounded side profile [[inset, h], ...] (h increasing); outlineAt(i)
// returns the 2D outline inset by i (same point count for every i), to3(u, v, h) places it
const cushion = (part, outlineAt, profile, to3) => loft(part, profile.map(([i, h]) => outlineAt(i).map(([u, v]) => to3(u, v, h))));
// side profile of a block from h0 to h1 with quarter-round edges rb (at h0) and rt (at h1), n steps each
function roundProfile(h0, h1, rb, rt, n = 4) {
  const out = [];
  for (let k = 0; k <= n; k++) { const a = 90 * k / n * D2R; if (rb > 0 || k === n) out.push([rb * (1 - Math.sin(a)), h0 + rb * (1 - Math.cos(a))]); }
  for (let k = 0; k <= n; k++) { const a = 90 * k / n * D2R; if (rt > 0 || k === 0) out.push([rt * (1 - Math.cos(a)), h1 - rt + rt * Math.sin(a)]); }
  return out;
}
const rotY = (deg, cx = 0, cz = 0) => q => { const a = deg * D2R, c = Math.cos(a), s = Math.sin(a), x = q[0] - cx, z = q[2] - cz; return [cx + x * c + z * s, q[1], cz - x * s + z * c]; };
const rotX = (deg, cy = 0, cz = 0) => q => { const a = deg * D2R, c = Math.cos(a), s = Math.sin(a), y = q[1] - cy, z = q[2] - cz; return [q[0], cy + y * c - z * s, cz + y * s + z * c]; };
const xform = (part, fn, from = 0) => { for (let i = from; i < part.v.length; i++) part.v[i] = fn(part.v[i]); };
// smooth interpolation through [t, value] knots (Catmull-Rom, clamped ends)
function curve(knots) {
  return t => {
    let i = 0; while (i < knots.length - 2 && t > knots[i + 1][0]) i++;
    const [t1, p1] = knots[i], [t2, p2] = knots[i + 1], p0 = (knots[i - 1] || knots[i])[1], p3 = (knots[i + 2] || knots[i + 1])[1];
    const u = Math.max(0, Math.min(1, (t - t1) / (t2 - t1)));
    return 0.5 * (2 * p1 + (-p0 + p2) * u + (2 * p0 - 5 * p1 + 4 * p2 - p3) * u * u + (-p0 + 3 * p1 - 3 * p2 + p3) * u * u * u);
  };
}

// ---------------- assets ----------------
// Each builder returns {PARTS, MATS, atlas, startS}; all parts are in the asset's own block frame (px, see header).
function newAsset() { const PARTS = []; return {PARTS, P: (name, mat, bone = 'main') => { const p = new Part(name, bone, mat); PARTS.push(p); return p; }}; }

// Modern office desk: 48 x 16 px laminate top on a charcoal powder-coated steel frame (two end frames of square legs,
// top and foot rails, front / rear beams), perforation-free modesty panel and a cable tray at the back, two grommets.
// Leg, rail and beam positions follow the V1 VoxelShape (ModernOfficeDeskBlock).
export const DESK = {top: [12.45, 13.5], legX: 22.1, legZ: 6.1, leg: 0.85, railTop: 12.4, beamTop: 12.36};
function buildDesk() {
  const {PARTS, P} = newAsset(), T = DESK;
  slab(P('top', 'laminate'), 'y', [-24, T.top[0], -8], [24, T.top[1], 8], 0.18);
  const legs = P('legs', 'frame'), glides = P('glides', 'glide');
  for (const sx of [-1, 1]) for (const sz of [-1, 1]) {
    const x = sx * T.legX, z = sz * T.legZ;
    slab(legs, 'y', [x - T.leg, 0.35, z - T.leg], [x + T.leg, T.top[0], z + T.leg], 0.12);
    cyl(glides, 'y', z, x, 0.62, 0, 0.4, 14);
  }
  // end frames: top rail under the top and a foot rail, front leg to rear leg (ending inside the legs)
  const rails = P('end_rails', 'frame'), RZ = T.legZ - 0.4;
  for (const sx of [-1, 1]) {
    const x = sx * T.legX;
    slab(rails, 'z', [x - 0.65, 11.4, -RZ], [x + 0.65, T.railTop, RZ], 0.1);
    slab(rails, 'z', [x - 0.65, 0.75, -RZ], [x + 0.65, 1.75, RZ], 0.1);
  }
  // front and rear beams between the end frames
  const beams = P('beams', 'frame'), BX = T.legX - 0.5;
  slab(beams, 'x', [-BX, 11.25, -5.55], [BX, T.beamTop, -4.45], 0.1);
  slab(beams, 'x', [-BX, 11.25, 4.45], [BX, T.beamTop, 5.55], 0.1);
  // modesty panel under the rear beam, with a folded bottom edge
  const panel = P('modesty_panel', 'frame');
  slab(panel, 'x', [-20.5, 6.45, 4.62], [20.5, 11.25, 4.98], 0);
  slab(panel, 'x', [-20.45, 6.2, 4.55], [20.45, 6.5, 5.3], 0);
  // cable tray: U channel hanging from the top on two straps
  const tray = P('cable_tray', 'frame');
  sideX(tray, [[5.6, 10.3], [7.3, 10.3], [7.3, 10.95], [7.16, 10.95], [7.16, 10.45], [5.74, 10.45], [5.74, 11.35], [5.6, 11.35]], -17, 17, 0);
  for (const x of [-12, 12]) slab(tray, 'z', [x - 0.4, 11.3, 5.48], [x + 0.4, T.top[0], 5.62], 0);
  // cable grommets in the top, near the back
  const grommets = P('grommets', 'grommet');
  for (const x of [-14, 14]) cyl(grommets, 'y', 5.4, x, 0.95, T.top[1] - 0.02, T.top[1] + 0.06, 18);
  const MATS = {
    laminate: {c: [178, 177, 170], hl: 8, sm: 112, se: 128, f0: 20},   // light warm-grey melamine, satin
    frame:    {c: [54, 56, 60], hl: 10, sm: 104, se: 124, f0: 22},     // charcoal powder-coated steel
    glide:    {c: [28, 28, 30], hl: 4, sm: 70, se: 80, f0: 20},
    grommet:  {c: [32, 33, 35], hl: 6, sm: 92, se: 104, f0: 20},
  };
  return {PARTS, MATS, atlas: 512, startS: 24};
}

// Office task chair: five-star nylon base on twin-wheel casters, gas lift (black telescopic shroud, chrome piston), tilt
// mechanism with a height lever on the sitter's right, moulded seat pan with a fabric cushion, a tapered fabric backrest
// with a black rear shell on a spine, T-arms. Envelope of the V1 VoxelShape (seat y 7.1..8.75, arm top 10.9, back top 19.2).
// Pivots mirrored in client/OfficeChairRenderer: casters on their stems (radius casterR, leg k at 72k degrees from +Z toward
// +X), the swivel on the column axis.
export const CHAIR = {seatTop: 8.75, backTilt: 8, backPivot: [9.6, 3.55], swivelPivotY: 4.6, casterR: 5.55, casterPivotY: 1.2};
// parts that stay on the floor with the base; casters have their own bones; everything else turns with the seat
const CHAIR_BASE = new Set(['hub', 'base_legs', 'lift_shroud']);
function buildChair() {
  const {PARTS, P} = newAsset();
  // base: hub + five tapered legs (one straight back under the backrest) + casters
  lathe(P('hub', 'nylon'), 0, 0, [[2.55, 0], [2.55, 1.05], [2.35, 1.25], [1.45, 1.25], [1.3, 1.05], [1.3, 0]], 18);
  const legs = P('base_legs', 'nylon');
  const sec = (r, w, yb, yt, ch) => [[w, yb], [w, yt - ch], [w - ch, yt], [-w + ch, yt], [-w, yt - ch], [-w, yb]].map(([x, y]) => [x, y, r]);
  for (let k = 0; k < 5; k++) {
    const th = 72 * k, v0 = legs.v.length;
    loft(legs, [sec(0.8, 0.62, 1.4, 2.4, 0.22), sec(3.2, 0.5, 1.3, 2.1, 0.2), sec(5.75, 0.4, 1.2, 1.78, 0.16)]);
    xform(legs, rotY(th), v0);
    const R = CHAIR.casterR, forks = P('caster_fork_' + k, 'nylon', 'caster_' + k), wheels = P('caster_wheels_' + k, 'wheel', 'caster_' + k);
    cyl(forks, 'y', R, 0, 0.2, 1.1, 1.3, 10);                                          // stem into the leg tip
    slab(forks, 'y', [-0.16, 0.55, R - 0.62], [0.16, 1.22, R + 0.5], 0.06);              // fork plate between the wheels
    for (const sx of [-1, 1]) cyl(wheels, 'x', R - 0.05, 0.6, 0.6, sx > 0 ? 0.17 : -0.47, sx > 0 ? 0.47 : -0.17, 14);
    xform(wheels, rotY(th)); xform(forks, rotY(th));
  }
  // gas lift: telescopic shroud + chrome piston
  lathe(P('lift_shroud', 'nylon'), 0, 0, [[4.75, 0], [4.75, 0.62], [4.6, 0.72], [3.1, 0.76], [2.95, 0.92], [2.3, 0.92], [2.3, 0]], 18);
  lathe(P('lift_piston', 'chrome'), 0, 0, [[6.55, 0], [6.55, 0.45], [4.6, 0.45], [4.6, 0]], 16);
  // tilt mechanism + height lever (sitter's right = +X)
  const mech = P('mechanism', 'steel');
  slab(mech, 'y', [-2.3, 6.35, -2.6], [2.3, 6.95, 2.2], 0.12);
  slab(mech, 'x', [2.2, 6.55, -1.15], [4.35, 6.72, -0.95], 0);
  slab(P('lever_paddle', 'nylon'), 'x', [4.3, 6.48, -1.45], [5.0, 6.8, -0.65], 0.08);
  // seat: moulded pan + cushion
  planY(P('seat_pan', 'shell'), rrect(-4.85, -4.9, 4.85, 4.1, 1.4), 6.95, 7.4, 0.12);
  cushion(P('seat_cushion', 'fabric'), i => rrect(-5.05 + i, -5.15 + i, 5.05 - i, 4.35 - i, 1.9 - i), roundProfile(7.3, CHAIR.seatTop, 0.15, 0.55), (u, v, h) => [u, h, v]);
  // backrest: tapered outline (narrow at the lumbar, wider shoulders, rounded top), cushion + rear shell, tilted back
  const [py, pz] = CHAIR.backPivot, Y0 = 0, Y1 = 9.3, RB = 1.1, RT = 2.1;
  const outline = (inset) => {
    const pts = [], arc = (cx, cy, r, a0, a1) => { for (let i = 0; i <= 6; i++) { const a = (a0 + (a1 - a0) * i / 6) * D2R; pts.push([cx + r * Math.cos(a), cy + r * Math.sin(a)]); } };
    const wb = 3.95 - inset, wt = 4.6 - inset, rb = RB - inset * 0.5, rt = RT - inset * 0.5, yb = Y0 + inset, yt = Y1 - inset;
    arc(wb - rb, yb + rb, rb, 270, 360); arc(wt - rt, yt - rt, rt, 0, 90); arc(-wt + rt, yt - rt, rt, 90, 180); arc(-wb + rb, yb + rb, rb, 180, 270);
    return pts;
  };
  const back = P('back_cushion', 'fabric'), shell = P('back_shell', 'shell'), spine = P('back_spine', 'steel');
  cushion(back, outline, roundProfile(0, 1.1, 0.45, 0.1), (u, v, h) => [u, v, h]);
  frontZ(shell, outline(0.3), 1.0, 1.38, 0.1);
  slab(spine, 'z', [-0.9, -3.15, 1.33], [0.9, 4.2, 1.85], 0.12);
  for (const p of [back, shell, spine]) xform(p, q => rotX(CHAIR.backTilt, py, pz)([q[0], q[1] + py, q[2] + pz]));
  slab(spine, 'z', [-0.9, 6.45, 1.9], [0.9, 7.0, 4.95], 0.12);   // spine foot: mechanism to the upright
  // T-arms: bracket under the pan, post, pad
  const arms = P('arm_frames', 'nylon'), pads = P('arm_pads', 'pad');
  for (const sx of [-1, 1]) {
    const xs = (a, b) => sx > 0 ? [a, b] : [-b, -a];
    { const [x0, x1] = xs(2.0, 6.3); slab(arms, 'x', [x0, 6.6, -0.8], [x1, 6.93, 0.2], 0.08); }
    { const [x0, x1] = xs(5.55, 6.35); slab(arms, 'y', [x0, 6.5, -0.9], [x1, 10.15, 0.3], 0.14); }
    { const [x0, x1] = xs(5.25, 6.65); planY(pads, rrect(x0, -4.0, x1, 2.4, 0.6), 10.1, 10.85, 0.24); }
  }
  const MATS = {
    fabric: {c: [52, 54, 58], hl: 6, sm: 38, se: 46, f0: 20},        // charcoal woven upholstery, rough
    shell:  {c: [33, 34, 37], hl: 8, sm: 86, se: 104, f0: 20},       // moulded black PP
    nylon:  {c: [36, 37, 40], hl: 8, sm: 90, se: 110, f0: 20},       // glass-filled nylon base / arm frames
    pad:    {c: [30, 31, 33], hl: 6, sm: 70, se: 86, f0: 20},        // soft PU arm pads
    steel:  {c: [44, 45, 48], hl: 10, sm: 98, se: 118, f0: 22},      // black powder-coated mechanism / spine
    chrome: {c: [150, 153, 158], hl: 14, sm: 168, se: 184, f0: 230}, // gas-lift piston
    wheel:  {c: [26, 26, 28], hl: 4, sm: 52, se: 60, f0: 20},
  };
  for (const p of PARTS) if (p.bone === 'main') p.bone = CHAIR_BASE.has(p.name) ? 'base' : 'swivel';
  const pivots = {base: [0, 0, 0], swivel: [0, CHAIR.swivelPivotY, 0]};
  for (let k = 0; k < 5; k++) pivots['caster_' + k] = [CHAIR.casterR * Math.sin(72 * k * D2R), CHAIR.casterPivotY, CHAIR.casterR * Math.cos(72 * k * D2R)].map(v => +v.toFixed(6) || 0);
  return {PARTS, MATS, atlas: 512, startS: 28, pivots};
}

// LCD monitor: thin-bezel 16:9 panel (screen 15.2 x 8.55 px, dark glass, unlit), tapered rear housing, VESA hinge block,
// flat neck and a rounded base plate. Envelope of the V1 VoxelShape (screen x -8..8, y 3..12.8; base x -3.7..3.7).
export const MONITOR = {front: -1.15, screen: [-7.6, 3.8, 7.6, 12.35]};
function buildMonitor() {
  const {PARTS, P} = newAsset(), F = MONITOR.front, S = MONITOR.screen;
  frontZ(P('bezel', 'housing'), rrect(-8, 3.05, 8, 12.75, 0.3, 3), F, F + 0.55, 0.08);
  slab(P('screen', 'screen'), 'z', [S[0], S[1], F - 0.05], [S[2], S[3], F], 0);
  cushion(P('rear_housing', 'housing'), i => rrect(-7.0 + i, 4.1 + i, 7.0 - i, 11.7 - i, 1.2 - i * 0.5, 4), roundProfile(F + 0.5, 0.45, 0, 0.62), (u, v, h) => [u, v, h]);
  slab(P('hinge', 'stand'), 'z', [-1.4, 6.6, 0.4], [1.4, 9.4, 0.95], 0.1);
  slab(P('neck', 'stand'), 'y', [-1.25, 0.5, 0.85], [1.25, 8.9, 1.65], 0.2);
  planY(P('base', 'stand'), rrect(-3.65, -2.4, 3.65, 2.4, 1.2, 5), 0, 0.55, 0.2);
  const MATS = {
    housing: {c: [33, 34, 37], hl: 8, sm: 92, se: 112, f0: 20},     // satin black plastic
    stand:   {c: [44, 45, 49], hl: 10, sm: 100, se: 120, f0: 20},   // dark graphite stand
    screen:  {c: [12, 14, 17], hl: 2, sm: 214, se: 214, f0: 20},    // dark glass, off
  };
  return {PARTS, MATS, atlas: 512, startS: 30};
}

// Keyboard: full-size layout (main block, navigation cluster, numpad; 1 U = 0.5 px) of tapered keycaps on a low wedge
// body, typing slope ~5.8 degrees. No legends. Standalone frame: x -6.175..6.175, z -1.8..1.725.
export const KEYBOARD = {U: 0.5, x: 6.175, z0: -1.8, z1: 1.725, hFront: 0.42, hBack: 0.78};
function buildKeyboard() {
  const {PARTS, P} = newAsset(), K = KEYBOARD, U = K.U;
  sideX(P('body', 'body'), [[K.z0, 0], [K.z1, 0], [K.z1, K.hBack], [K.z0, K.hFront]], -K.x, K.x, 0.12);
  const slope = Math.atan2(K.hBack - K.hFront, K.z1 - K.z0), cs = Math.cos(slope), sn = Math.sin(slope);
  const toDeck = q => [q[0], K.hFront + q[1] * cs + q[2] * sn, K.z0 + q[2] * cs - q[1] * sn];
  // layout: [left edge in U from the user's left, row, width U, height rows]; user's left = +X
  const KEYS = [], row = (r, start, widths) => { let c = start; for (const w of widths) { if (w > 0) KEYS.push([c, r, w, 1]); c += Math.abs(w); } };
  row(0, 0, [1, -1, 1, 1, 1, 1, -0.5, 1, 1, 1, 1, -0.5, 1, 1, 1, 1]); row(0, 15.25, [1, 1, 1]);
  row(1, 0, [1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2]); row(1, 15.25, [1, 1, 1]); row(1, 18.5, [1, 1, 1, 1]);
  row(2, 0, [1.5, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1.5]); row(2, 15.25, [1, 1, 1]); row(2, 18.5, [1, 1, 1]);
  row(3, 0, [1.75, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2.25]); row(3, 18.5, [1, 1, 1]);
  row(4, 0, [2.25, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2.75]); row(4, 16.25, [1]); row(4, 18.5, [1, 1, 1]);
  row(5, 0, [1.25, 1.25, 1.25, 6.25, 1.25, 1.25, 1.25, 1.25]); row(5, 15.25, [1, 1, 1]); row(5, 18.5, [2, 1]);
  KEYS.push([21.5, 2, 1, 2], [21.5, 4, 1, 2]);   // numpad + and Enter (two rows high)
  const keys = P('keys', 'key'), GAP = 0.09, H = 0.2, INS = 0.055, XL = 22.5 * U / 2;
  const rowBack = r => 0.24 + (5 - r) * U + (r === 0 ? 0.25 * U : 0);   // deck z of a row's front edge (rows 0 back .. 5 front)
  for (const [c, r, w, h] of KEYS) {
    const xa = XL - c * U - GAP / 2, xb = XL - (c + w) * U + GAP / 2;   // xa > xb (toward -X)
    const za = rowBack(r + h - 1) + GAP / 2, zb = rowBack(r) + U - GAP / 2;
    const b = [[xb, za], [xa, za], [xa, zb], [xb, zb]].map(([x, z]) => keys.vtx(toDeck([x, -0.02, z])));
    const t = [[xb + INS, za + INS * 1.4], [xa - INS, za + INS * 1.4], [xa - INS, zb - INS * 0.6], [xb + INS, zb - INS * 0.6]].map(([x, z]) => keys.vtx(toDeck([x, H, z])));
    const up = toDeck([0, 1, 0]).map((v, i) => v - toDeck([0, 0, 0])[i]);
    keys.face([t[0], t[1], t[2], t[3]], up, 'cap');
    const out = [[0, 0.3, -1], [1, 0.3, 0], [0, 0.3, 1], [-1, 0.3, 0]];
    for (let i = 0; i < 4; i++) { const j = (i + 1) % 4; keys.face([b[i], b[j], t[j], t[i]], [i === 1 ? 1 : i === 3 ? -1 : 0, 0.3, i === 0 ? -1 : i === 2 ? 1 : 0], 'bevel'); }
  }
  const MATS = {
    body: {c: [31, 32, 35], hl: 8, sm: 92, se: 110, f0: 20},
    key:  {c: [47, 49, 53], hl: 12, sm: 104, se: 122, f0: 20},
  };
  return {PARTS, MATS, atlas: 512, startS: 40};
}

// Mouse: symmetric lofted shell (hump toward the back, flat sole), button split line, rubber scroll wheel. Points +Z.
// Standalone frame: x -1.05..1.05, z -1.45..1.95.
export const MOUSE = {z0: -1.45, z1: 1.95, w: 1.05, wheelT: 0.76};
function buildMouse() {
  const {PARTS, P} = newAsset(), M = MOUSE, L = M.z1 - M.z0;
  const halfW = t => { const s = t < 0.42 ? (0.42 - t) / 0.42 : (t - 0.42) / 0.58; return M.w * (1 - 0.1 * Math.max(0, (t - 0.42) / 0.58)) * Math.pow(Math.max(0, 1 - Math.pow(s, 2.6)), 1 / 2.6); };
  const height = curve([[0, 0.4], [0.18, 0.84], [0.36, 0.98], [0.58, 0.9], [0.8, 0.72], [1, 0.48]]);
  const YS = 0.16, N = 12;   // side wall height at the sole, arch segments
  const ring = t => {
    const w = Math.max(0.03, halfW(t)), h = height(t), z = M.z0 + t * L, pts = [[w * 0.94, 0, z]];
    for (let i = 0; i <= N; i++) { const ph = Math.PI * i / N, c = Math.cos(ph), s = Math.sin(ph);
      pts.push([w * Math.sign(c) * Math.pow(Math.abs(c), 2 / 2.6), YS + (h - YS) * Math.pow(s, 2 / 2.6), z]); }
    pts.push([-w * 0.94, 0, z]);
    return pts;
  };
  const TS = [0, 0.006, 0.02, 0.045, 0.09, 0.16, 0.25, 0.35, 0.45, 0.55, 0.65, 0.75, 0.84, 0.91, 0.955, 0.98, 0.994, 1];
  loft(P('shell', 'shell'), TS.map(ring));
  // button split: a narrow dark strip on the crown from the front end to behind the wheel
  const seam = P('button_split', 'seam'), strip = [];
  for (let t = 0.5; t <= 0.985; t += 0.035) strip.push(t);
  const sr = strip.map(t => { const y = height(t) + 0.008, z = M.z0 + t * L; return [[0.028, y, z], [0.028, y - 0.03, z], [-0.028, y - 0.03, z], [-0.028, y, z]]; });
  loft(seam, sr);
  const tw = M.wheelT, zw = M.z0 + tw * L;
  cyl(P('scroll_wheel', 'wheel'), 'x', zw, height(tw) - 0.08, 0.2, -0.075, 0.075, 14);
  const MATS = {
    shell: {c: [36, 37, 41], hl: 10, sm: 104, se: 124, f0: 20},
    seam:  {c: [14, 14, 16], hl: 0, sm: 60, se: 60, f0: 20},
    wheel: {c: [22, 22, 24], hl: 4, sm: 56, se: 64, f0: 20},
  };
  return {PARTS, MATS, atlas: 256, startS: 60};
}

// ---------------- bake: UV, LabPBR maps, editable source ----------------
const ASSETS = {
  desk:     {id: 'modern_office_desk', build: buildDesk},
  chair:    {id: 'modern_office_chair', build: buildChair},
  monitor:  {id: 'modern_lcd_monitor', build: buildMonitor},
  keyboard: {id: 'office_keyboard', build: buildKeyboard},
  mouse:    {id: 'office_mouse', build: buildMouse},
};
const DUMMY = 'data:image/png;base64,' + png(Buffer.from([0, 0, 0, 255]), 1, 1).toString('base64');
function bake(key) {
  const {id, build} = ASSETS[key], {PARTS: all, MATS, atlas, startS, pivots = {}} = build(), PARTS = all.filter(p => p.f.length);
  // the OBJ keeps faces as they are (Forge draws a quad as 0-1-2 / 2-3-0): split anything that is not a triangle or a
  // strictly convex quad
  for (const p of PARTS) {
    const out = [];
    for (const f of p.f) {
      const P3 = f.ids.map(i => p.v[i]), n = newell(P3);
      const convexQuad = f.ids.length === 4 && [0, 1, 2, 3].every(k => dot(cross(sub(P3[(k + 1) % 4], P3[k]), sub(P3[(k + 2) % 4], P3[(k + 1) % 4])), n) > 1e-12);
      if (f.ids.length === 3 || convexQuad) { out.push(f); continue; }
      assert(f.ids.length === 4, `${key}: ${f.ids.length}-gon in ${p.name}`);
      const k = [0, 1, 2, 3].find(k => dot(cross(sub(P3[k], P3[(k + 3) % 4]), sub(P3[(k + 1) % 4], P3[k])), n) <= 1e-12) ?? 0;   // reflex corner
      out.push({...f, ids: [f.ids[k], f.ids[(k + 1) % 4], f.ids[(k + 2) % 4]]}, {...f, ids: [f.ids[(k + 2) % 4], f.ids[(k + 3) % 4], f.ids[k]]});
    }
    p.f = out;
  }
  const UV = unwrap(PARTS, {atlas, pad: 2, startS, stepS: 0.25});
  for (const p of PARTS) assert(p.f.every(f => UV.faceUV.get(p)?.has(f)), `${key}: unmapped face in ${p.name}`);
  const first = Object.values(MATS)[0];
  const painted = paint({PARTS, islands: UV.islands, S: UV.S, uvOf: UV.uvOf, atlas, pad: 2, MATS, ZONED: new Set(), groupInfo: new Map(),
    sourceGroups: () => [], refTexture: {source: DUMMY}, refUvWidth: 1, background: {c: [...first.c, 255], s: [first.sm, first.f0, 0, 255], n: [128, 128, 255, 255]}});
  const zf = zFightLevels(PARTS, new Map());
  return {key, id, PARTS, MATS, atlas, UV, pivots, maps: painted.PNG, coplanar: zf.unresolved};
}

const uuidOf = (ns, s) => { const h = createHash('sha256').update(`afl-office-props-v2:${ns}:${s}`).digest('hex'); return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-8${h.slice(17, 20)}-${h.slice(20, 32)}`; };
const r12 = v => +v.toFixed(12) || 0;
// editable Free Model source (frame as the header, px): one group per bone; hidden bones are written with export false
// (the runtime conversion of the chair's swivel)
function sourceOf(b, hidden = new Set()) {
  const uuid = s => uuidOf(b.id, s), name = b.id + '_v2';
  const bones = [...new Set(b.PARTS.map(p => p.bone))], gid = bone => uuid(bone === 'main' ? 'group' : 'group:' + bone);
  const elements = b.PARTS.map(p => {
    const key = i => i.toString(36).padStart(4, '0'), vertices = {}, faces = {}, uvs = b.UV.faceUV.get(p);
    p.v.forEach((q, i) => { vertices[key(i)] = q.map(r12); });
    p.f.forEach((f, fi) => { const uv = uvs.get(f); faces['f' + key(fi)] = {uv: Object.fromEntries(f.ids.map((id, j) => [key(id), uv[j].map(r12)])), vertices: f.ids.map(key), texture: 0}; });
    return {name: p.name, color: 0, origin: [0, 0, 0], rotation: [0, 0, 0], shading: 'flat', export: true, visibility: true, locked: false,
      render_order: 'default', scope: 0, allow_mirror_modeling: true, vertices, faces, type: 'mesh', uuid: uuid('mesh:' + p.name)};
  });
  return {meta: {format_version: '5.0', model_format: 'free', box_uv: false}, name, model_identifier: '', visible_box: [1, 1, 0],
    variable_placeholders: '', variable_placeholder_buttons: [], timeline_setups: [], unhandled_root_fields: {}, resolution: {width: b.atlas, height: b.atlas},
    elements, groups: bones.map(bone => ({name: bone === 'main' ? b.key : bone, uuid: gid(bone), export: !hidden.has(bone), locked: false, scope: 0,
      selected: false, visibility: true, _static: {properties: {}, temp_data: {}}, origin: (b.pivots[bone] || [0, 0, 0]).slice(), rotation: [0, 0, 0],
      color: 0, children: [], reset: false, shade: true, mirror_uv: false, autouv: 0, isOpen: true, primary_selected: false})),
    outliner: bones.map(bone => ({uuid: gid(bone), isOpen: true, children: elements.filter((e, i) => b.PARTS[i].bone === bone).map(e => e.uuid)})),
    textures: [{name: name + '.png', relative_path: `textures/${name}.png`, folder: '', namespace: '', id: '0', group: '', scope: 0,
      width: b.atlas, height: b.atlas, uv_width: b.atlas, uv_height: b.atlas, particle: false, use_as_default: false, layers_enabled: false, sync_to_project: '',
      file_format: 'png', render_mode: 'default', render_sides: 'auto', wrap_mode: 'limited', pbr_channel: 'color', fps: 7, frame_time: 1,
      frame_order_type: 'loop', frame_order: '', frame_interpolate: false, visible: true, internal: true, saved: true, uuid: uuid('texture'),
      source: 'data:image/png;base64,' + b.maps[0].toString('base64')}],
    animations: []};
}

// ---------------- OBJ / MTL / models ----------------
const f6 = v => (+v.toFixed(6)).toString();
// corner normals: the face normal averaged (area weighted) with the faces sharing that vertex within SMOOTH degrees, so
// lofted / lathed surfaces shade smoothly under shaders while box edges and chamfers stay crisp (vanilla shading uses the
// quad direction either way)
const SMOOTH = Math.cos(36 * D2R);
function cornerNormals(p) {
  const fn = p.f.map(f => newell(f.ids.map(i => p.v[i]))), byV = new Map();
  p.f.forEach((f, k) => f.ids.forEach(i => (byV.get(i) || byV.set(i, []).get(i)).push(k)));
  return p.f.map((f, k) => { const n0 = norm(fn[k]); return f.ids.map(i => norm(byV.get(i).reduce((a, j) => dot(norm(fn[j]), n0) >= SMOOTH ? add(a, fn[j]) : a, [0, 0, 0]))); });
}
// pieces: [{b (baked asset), offset [x, y, z] px}]; one material per asset (its atlas)
function objOf(title, file, pieces) {
  const out = [`# AFL ${title}, generated by tools/build-office-props-v2.mjs`, `mtllib ${file.split("/").pop()}.mtl`];   // resolved next to the OBJ
  let vBase = 1, tBase = 1, nBase = 1;
  for (const {b, offset = [0, 0, 0], bones} of pieces) for (const p of b.PARTS) {
    if (bones && !bones.has(p.bone)) continue;
    out.push(`o ${b.key}_${p.name}`, `usemtl ${b.key}`);
    for (const q of p.v) out.push(`v ${f6((q[0] + offset[0]) / 16 + 0.5)} ${f6((q[1] + offset[1]) / 16)} ${f6((q[2] + offset[2]) / 16 + 0.5)}`);
    const uvs = b.UV.faceUV.get(p), vt = [], vn = [], fl = [];
    const cn = cornerNormals(p), nIndex = new Map();
    p.f.forEach((f, k) => {
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
const mtlOf = (title, pieces) => `# AFL ${title}\n` + [...new Map(pieces.map(({b}) => [b.key, b.id])).entries()]
  .map(([k, id]) => `newmtl ${k}\nKd 1 1 1\nmap_Kd apocalypse_firstlight:block/${id}\n`).join('');
const objModel = (file, particle) => ({loader: 'forge:obj', model: `apocalypse_firstlight:models/block/${file}.obj`, automatic_culling: false,
  flip_v: true, shade_quads: true, ambientocclusion: false, textures: {particle: `apocalypse_firstlight:block/${particle}`}});
// item display: the V1 contexts unchanged; the GUI translation re-centres the projected mesh bounds (as the V1 framing pass)
const r3 = v => +v.toFixed(3) || 0;
function guiCentred(pieces, rotation, scale) {
  const [ax, ay] = rotation.map(v => v * D2R);
  const rot = q => { const x = q[0] * Math.cos(ay) + q[2] * Math.sin(ay), z = -q[0] * Math.sin(ay) + q[2] * Math.cos(ay); return [x, q[1] * Math.cos(ax) - z * Math.sin(ax)]; };
  const pts = pieces.flatMap(({b, offset = [0, 0, 0]}) => b.PARTS.flatMap(p => p.v.map(q => rot([q[0] + offset[0], q[1] + offset[1] - 8, q[2] + offset[2]]))));
  const cx = (Math.max(...pts.map(q => q[0])) + Math.min(...pts.map(q => q[0]))) / 2, cy = (Math.max(...pts.map(q => q[1])) + Math.min(...pts.map(q => q[1]))) / 2;
  return {rotation, translation: [r3(-scale * cx), r3(-scale * cy), 0], scale: [scale, scale, scale]};
}
const S3 = v => [v, v, v];
const cellPoints = pieces => pieces.flatMap(({b, offset = [0, 0, 0]}) => b.PARTS.flatMap(p => p.v.map(q => [q[0] + offset[0] + 8, q[1] + offset[1], q[2] + offset[2] + 8])));
function display(kind, pieces) {
  const hand = (s, t) => ({
    thirdperson_righthand: {rotation: [75, 45, 0], translation: [0, t, 0], scale: S3(s)}, thirdperson_lefthand: {rotation: [75, 45, 0], translation: [0, t, 0], scale: S3(s)}});
  switch (kind) {
    case 'desk': return {...hand(0.2, 2.5), firstperson_righthand: {rotation: [0, 45, 0], translation: [0, 2.5, 0], scale: S3(0.25)},
      firstperson_lefthand: {rotation: [0, 225, 0], translation: [0, 2.5, 0], scale: S3(0.25)}, gui: guiCentred(pieces, [30, 135, 0], 0.28),
      ground: {translation: [0, 2, 0], scale: S3(0.22)}, fixed: {rotation: [0, 180, 0], translation: [0, -2, 0], scale: S3(0.25)}};
    case 'chair': return {...hand(0.5, 1.5), firstperson_righthand: {rotation: [0, 45, 0], translation: [0, 1, 0], scale: S3(0.55)},
      firstperson_lefthand: {rotation: [0, 225, 0], translation: [0, 1, 0], scale: S3(0.55)}, gui: guiCentred(pieces, [25, 135, 0], 0.62),
      ground: {translation: [0, 2, 0], scale: S3(0.5)}, fixed: {rotation: [0, 180, 0], translation: [0, -1, 0], scale: S3(0.55)}};
    case 'monitor': case 'station': return {...hand(0.65, 2), firstperson_righthand: {rotation: [0, 45, 0], translation: [0, 2, 0], scale: S3(0.7)},
      firstperson_lefthand: {rotation: [0, 225, 0], translation: [0, 2, 0], scale: S3(0.7)}, gui: guiCentred(pieces, [25, 135, 0], 0.72),
      ground: {translation: [0, 2, 0], scale: S3(0.65)}, fixed: {rotation: [0, 180, 0], translation: [0, 0, 0], scale: S3(0.7)}};
    // keyboard and mouse lie on the cell's floor: held, dropped and framed fitted to their bounds (tools/item-held-display.mjs,
    // 2026-10-09; the fixed contexts put the mouse a block below the hand and 1.4 blocks low in a frame)
    case 'keyboard': return {...heldDisplay(cellPoints(pieces), {size: 1.0, rotations: {fixed: [0, 180, 0]}}), gui: guiCentred(pieces, [30, 135, 0], 0.95)};
    case 'mouse': return {...heldDisplay(cellPoints(pieces), {size: 0.5, rotations: {fixed: [0, 180, 0]}}), gui: guiCentred(pieces, [25, 152, 0], 1.8)};
  }
}
// Selection outlines (cell px 0..16, facing=north, before the lowered sink): one clean box per block (the desk: per cell, its
// full height), mirrored in Java (ModernOfficeDeskBlock, ModernOfficeChairBlock, ModernLcdMonitorBlock,
// OfficeDesktopDecorationBlock). Collision there follows the mesh with a few boxes. The build fails if a mesh leaves its box.
export const SELECTION = {desk: [0, 0, 0, 16, 13.5, 16], chair: [1.35, 0, 2.8, 14.65, 18.75, 14.2], monitor: [0, 0, 5.6, 16, 12.75, 10.4],
  keyboard: [1.8, 0, 6.2, 14.2, 0.95, 9.75], mouse: [6.95, 0, 6.55, 9.05, 1.0, 9.95], station: [0, 0, 4.3, 16, 12.75, 13.6]};
export const LOWERED = -2.5;   // px, on the 13.5 px desk (OfficeDesktopDecorationBlock / ModernLcdMonitorBlock DESK_SINK)
// station layout (V1): monitor at the back, keyboard in front (shifted toward the user's left), mouse on the user's right
export const STATION = {monitor: [0, 0, 3.2], keyboard: [1.2, 0, -1.9], mouse: [-6.7, 0, -2.0]};

// The rolling chair entity's mesh (client/OfficeChairRenderer): geo bones in part order with the pivots above, sidecar. No
// block profile: the placed chair is the baked OBJ, the entity renderer poses the bones itself.
function chairMesh(b) {
  const bones = [...new Set(b.PARTS.map(p => p.bone))];
  const geo = {format_version: '1.12.0', 'minecraft:geometry': [{description: {identifier: 'geometry.' + b.id, texture_width: b.atlas,
    texture_height: b.atlas, visible_bounds_width: 2, visible_bounds_height: 2, visible_bounds_offset: [0, 0.5, 0]},
    bones: bones.map(name => { const p = b.pivots[name]; return {name, pivot: [-p[0] || 0, p[1], p[2]]}; })}]};
  const sidecar = convert(sourceOf(b), geo, {}, b.id + '_v2.bbmodel', 2);
  const trisOf = bone => sidecar.parts.filter(p => p.bone === bone).flatMap(p => p.faces).reduce((n, q) => n + q.length - 2, 0);
  chairStats = {byBone: Object.fromEntries(bones.map(n => [n, trisOf(n)]))};
  return [[path.join(assets, `geo/${b.id}.geo.json`), json(geo)], [path.join(assets, `meshes/${b.id}.aflmesh.json`), serializeCompact(sidecar)]];
}
let chairStats = null;

const keysArg = process.argv.slice(2).filter(a => !a.startsWith('--') && !(process.argv[process.argv.indexOf(a) - 1] === '--preview'));
const SELECTED = keysArg.length ? keysArg : Object.keys(ASSETS);
for (const k of SELECTED) assert(ASSETS[k], 'unknown id ' + k);
const B = Object.fromEntries(SELECTED.map(k => [k, bake(k)]));
const bb = path.join(ROOT, 'src/main/blockbench'), assets = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight');
const json = v => JSON.stringify(v, null, 2) + '\n';
const outputs = [], objs = [];   // objs: [file, text, mtl, pieces] for previews
const model = (file, title, pieces, particle) => {
  const obj = objOf(title, file, pieces), mtl = mtlOf(title, pieces);
  outputs.push([path.join(assets, `models/block/${file}.obj`), obj], [path.join(assets, `models/block/${file}.mtl`), mtl],
    [path.join(assets, `models/block/${file}.json`), json(objModel(file, particle))]);
  objs.push([file, obj, pieces]);
};
for (const b of Object.values(B)) {
  outputs.push([path.join(bb, `${b.id}_v2.bbmodel`), JSON.stringify(sourceOf(b))],
    ...['', '_s', '_n'].flatMap((k, i) => [[path.join(bb, `textures/${b.id}_v2${k}.png`), b.maps[i]], [path.join(assets, `textures/block/${b.id}${k}.png`), b.maps[i]]]));
  const one = [{b}], title = `Office Props V2 ${b.key}`;
  if (b.key === 'desk') {
    model('modern_office_desk/center', title, one, b.id);
    for (const part of ['left', 'right']) outputs.push([path.join(assets, `models/block/modern_office_desk/${part}.json`), json({textures: {particle: `apocalypse_firstlight:block/${b.id}`}})]);
  } else if (b.key === 'chair') {
    model(b.id, title, one, b.id);
    outputs.push(...chairMesh(b));
  }
  else { model(b.id, title, one, b.id); model(b.id + '_lowered', title + ' (on desk)', [{b, offset: [0, LOWERED, 0]}], b.id); }
  const parent = b.key === 'desk' ? 'modern_office_desk/center' : b.id;
  outputs.push([path.join(assets, `models/item/${b.id}.json`), json({parent: `apocalypse_firstlight:block/${parent}`, gui_light: 'side', display: display(b.key, one)})]);
}
if (B.monitor && B.keyboard && B.mouse) {
  const pieces = lowered => ['monitor', 'keyboard', 'mouse'].map(k => ({b: B[k], offset: add(STATION[k], [0, lowered, 0])}));
  model('office_computer_station', 'Office Props V2 computer station', pieces(0), B.monitor.id);
  model('office_computer_station_lowered', 'Office Props V2 computer station (on desk)', pieces(LOWERED), B.monitor.id);
  outputs.push([path.join(assets, 'models/item/office_computer_station.json'),
    json({parent: 'apocalypse_firstlight:block/office_computer_station', gui_light: 'side', display: display('station', pieces(0))})]);
}

const fitsIn = (pieces, box, cells = 1) => pieces.every(({b, offset = [0, 0, 0]}) => b.PARTS.every(p => p.v.every(q => [0, 1, 2].every(k => {
  const v = q[k] + offset[k] + (k === 1 ? 0 : 8), lo = k === 0 ? box[0] - 16 * (cells - 1) / 2 : box[k], hi = k === 0 ? box[3] + 16 * (cells - 1) / 2 : box[k + 3];
  return v >= lo - 0.07 && v <= hi + 0.07; }))));   // 0.07 px: the desk grommets stand 0.06 proud of the top
for (const b of Object.values(B)) assert(fitsIn([{b}], SELECTION[b.key], b.key === 'desk' ? 3 : 1), b.key + ' mesh leaves its selection box');
if (B.monitor && B.keyboard && B.mouse) assert(fitsIn(['monitor', 'keyboard', 'mouse'].map(k => ({b: B[k], offset: STATION[k]})), SELECTION.station), 'station mesh leaves its selection box');
const tris = b => b.PARTS.reduce((s, p) => s + p.f.reduce((t, f) => t + f.ids.length - 2, 0), 0);
const boundsOf = b => { const all = b.PARTS.flatMap(p => p.v); return [0, 1, 2].map(k => [r3(Math.min(...all.map(q => q[k]))), r3(Math.max(...all.map(q => q[k])))]); };
export const stats = Object.fromEntries(Object.values(B).map(b => [b.key, {triangles: tris(b), parts: b.PARTS.length, texelsPerPx: b.UV.S,
  islands: b.UV.islands.length, coplanar: b.coplanar.length, bounds: boundsOf(b), ...(b.key === 'chair' ? chairStats : {})}]));
const isMain = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  console.log(JSON.stringify(stats));
  for (const b of Object.values(B)) if (b.coplanar.length) console.log('COPLANAR ' + b.key, JSON.stringify(b.coplanar.slice(0, 8)));
  const pi = process.argv.indexOf('--preview');
  if (pi > 0) {
    const dir = process.argv[pi + 1]; fs.mkdirSync(dir, {recursive: true});
    for (const [file, obj] of objs) fs.writeFileSync(path.join(dir, file.replace('/', '_') + '.obj'), obj);
    for (const b of Object.values(B)) ['', '_s', '_n'].forEach((k, i) => fs.writeFileSync(path.join(dir, `${b.key}${k}.png`), b.maps[i]));
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
