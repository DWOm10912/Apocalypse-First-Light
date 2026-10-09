// Site Lighting V1 (docs/models/site_lighting_v1.md): parking-lot area lights on galvanized poles, the building's wall packs
// and the storefront canopy downlights. The user approved the plan on 2026-10-09 (galvanized grey poles, 8 m, a hidden-light
// grid under each lit pole) and asked for refined models. Real sizes (North American practice):
//   light_pole_base  a full-cell concrete pad (it covers the feed cable's cell below), a 600 mm cast pier 0.75 m tall
//                    (plain concrete, no drawn seam), four 24 mm anchor bolts with levelling nuts, washers and top nuts, a 300 mm
//                    base plate and the pole's foot with its weld
//   light_pole       one metre of 127 mm (5 in) square galvanized pole, 12 mm corner radius; the first one above the base
//                    carries the hand-hole cover (two screws)
//   area_light       the head cell: the pole's top and cap; per head a bolted pole plate, a 90 x 60 mm arm and a 560 x 400
//                    x 80 mm die-cast LED area light (top heat-sink fins, a driver cover, a lens with 6 x 4 LED optics);
//                    the facing head carries the twist-lock photocontrol
//   wall_pack        a 360 x 220 x 170 mm full-cutoff LED wall pack (wedge body, top fins, bottom lens with 2 x 6 optics)
//   canopy_downlight a 150 mm surface-mount cylinder downlight under the charcoal eyebrow canopy (reflector, lens)
// Pure Mesh + LabPBR atlases, exported as Forge OBJ block models (chunk-baked), as tools/build-building-lights-v1.mjs.
//   node tools/build-site-lighting-v1.mjs                -> writes sources, OBJ / MTL / block + item models, blockstates, atlases
//   node tools/build-site-lighting-v1.mjs --check        -> verifies every output is up to date
//   node tools/build-site-lighting-v1.mjs --preview DIR  -> writes only OBJ + maps into DIR (offline review)
// Modelled in millimetres (1 mm = 0.016 px), origin at the cell's bottom centre, x east, y up, z south. Pieces that point
// somewhere (arms and heads, the hand hole, the wall pack) are drawn facing north (-Z) and turned by the blockstates.
import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {Part, AX, extrude, unwrap, paint, png, readPng, zFightLevels, area2, add, sub, mul, dot, cross, norm, newell} from './cube-slab-mesh-lib.mjs';
import {heldDisplay} from './item-held-display.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
function assert(c, m) { if (!c) throw new Error(m); }
const PX = 0.016;

// ---------------- helpers ----------------
const orient = (L, ccw) => (area2(L) > 0) === ccw ? L : L.slice().reverse();
function rrect(cu, cv, w, h, r, seg = 3) {
  const u0 = cu - w / 2, v0 = cv - h / 2, u1 = cu + w / 2, v1 = cv + h / 2, out = [];
  if (r <= 0) return [[u0, v0], [u1, v0], [u1, v1], [u0, v1]];
  for (const [ccu, ccv, a0] of [[u1 - r, v1 - r, 0], [u0 + r, v1 - r, 90], [u0 + r, v0 + r, 180], [u1 - r, v0 + r, 270]])
    for (let i = 0; i <= seg; i++) { const a = (a0 + 90 * i / seg) * Math.PI / 180; out.push([ccu + r * Math.cos(a), ccv + r * Math.sin(a)]); }
  return out;
}
const circle = (cu, cv, r, seg) => Array.from({length: seg}, (_, i) => { const a = Math.PI / seg + 2 * Math.PI * i / seg; return [cu + r * Math.cos(a), cv + r * Math.sin(a)]; });
const shapeOf = (outer, holes = []) => ({outer: orient(outer, true), holes: holes.map(h => orient(h, false))});
// plan outline (x, z) along y: the library's y plane is (u, v) = (z, x)
const planY = (part, outer, holes, y0, y1, c) => extrude(part, 'y', shapeOf(outer.map(([x, z]) => [z, x]), holes.map(h => h.map(([x, z]) => [z, x]))), y0, y1, c);
// a profile (z, y) along x: the library's x plane is (u, v) = (z, y)
const alongX = (part, outline, x0, x1, c) => extrude(part, 'x', shapeOf(outline), x0, x1, c);
// a front outline (x, y) along z
const alongZ = (part, outline, z0, z1, c) => extrude(part, 'z', shapeOf(outline), z0, z1, c);
function cyl(part, ax, cu, cv, r, a0, a1, seg, tag = 'side') {
  const A = AX[ax], ang = i => Math.PI / seg + 2 * Math.PI * i / seg;
  const rg = a => Array.from({length: seg}, (_, i) => part.vtx(A.to3(cu + r * Math.cos(ang(i)), cv + r * Math.sin(ang(i)), a)));
  const r0 = rg(a0), r1 = rg(a1);
  for (let i = 0; i < seg; i++) { const j = (i + 1) % seg, mm = ang(i) + Math.PI / seg;   // the column's own middle (not the average across the wrap)
    part.face([r0[i], r0[j], r1[j], r1[i]], A.to3(Math.cos(mm), Math.sin(mm), 0), tag); }
  for (const [ring, a, s] of [[r0, a0, -1], [r1, a1, 1]]) { const c = part.vtx(A.to3(cu, cv, a));
    for (let i = 0; i < seg; i++) part.face([c, ring[i], ring[(i + 1) % seg]], mul(A.n, s), 'cap'); }
}
/**
 * A solid of revolution about a vertical axis through (cx, cz): profile [[r, y], ...] along the outer surface, starting at the
 * bottom (the material on the axis side; a step inward at the same height is a ceiling facing down if it comes first, a top
 * facing up if it comes after the side); r 0 closes the surface on the axis.
 */
function latheY(part, cx, cz, profile, seg, {capBottom = true, capTop = true, tag = 'side'} = {}) {
  const ang = i => Math.PI / seg + 2 * Math.PI * i / seg;
  const rings = profile.map(([r, y]) => r < 1e-6 ? null : Array.from({length: seg}, (_, i) => part.vtx([cx + r * Math.cos(ang(i)), y, cz + r * Math.sin(ang(i))])));
  for (let k = 0; k + 1 < profile.length; k++) {
    const [ra, ya] = profile[k], [rb, yb] = profile[k + 1], dr = rb - ra, dy = yb - ya;
    for (let i = 0; i < seg; i++) {
      const j = (i + 1) % seg, mm = ang(i) + Math.PI / seg, n = [dy * Math.cos(mm), -dr, dy * Math.sin(mm)];   // (dy, -dr) in (r, y); the column's middle
      const t = Math.abs(dy) < 1e-6 ? 'cap' : Math.abs(dr) > 0.3 * Math.abs(dy) ? 'bevel' : tag;
      if (!rings[k]) part.face([part.vtx([cx, ya, cz]), rings[k + 1][j], rings[k + 1][i]], n, t);
      else if (!rings[k + 1]) part.face([rings[k][i], rings[k][j], part.vtx([cx, yb, cz])], n, t);
      else part.face([rings[k][i], rings[k][j], rings[k + 1][j], rings[k + 1][i]], n, t);
    }
  }
  const ends = [[0, -1, capBottom], [profile.length - 1, 1, capTop]];
  for (const [k, s, on] of ends) { if (!on || !rings[k]) continue; const c = part.vtx([cx, profile[k][1], cz]);
    for (let i = 0; i < seg; i++) part.face([c, rings[k][i], rings[k][(i + 1) % seg]], [0, s, 0], 'cap'); }
}
const box = (part, a, b, c = 0) => planY(part, [[a[0], a[2]], [b[0], a[2]], [b[0], b[2]], [a[0], b[2]]], [], a[1], b[1], c);
/** A hex nut / bolt head (across flats af), vertical, from y0 to y1. */
const hex = (part, x, z, af, y0, y1) => latheY(part, x, z, [[af / Math.sqrt(3), y0], [af / Math.sqrt(3), y1]], 6);
// turn a finished part about the vertical axis (yaw, radians), then move (keeps the windings)
function turnY(part, yaw, t = [0, 0, 0]) { const c = Math.cos(yaw), s = Math.sin(yaw); part.v = part.v.map(([x, y, z]) => [x * c + z * s + t[0], y + t[1], -x * s + z * c + t[2]]); }
const MAT = (c, hl, sm, se, f0 = 20) => ({c, hl, sm, se, f0});

// ---- material patterns (low frequency only: no per-texel noise, no painted decoration) ----
const hash3 = (a, b, c) => { let h = Math.imul(a | 0, 374761393) ^ Math.imul(b | 0, 668265263) ^ Math.imul(c | 0, 2147483647); h = Math.imul(h ^ (h >>> 13), 1274126177); return ((h ^ (h >>> 16)) >>> 0) / 4294967295; };
const sm3 = t => t * t * (3 - 2 * t);
function vn3(x, y, z, seed) {
  const xi = Math.floor(x), yi = Math.floor(y), zi = Math.floor(z), tx = sm3(x - xi), ty = sm3(y - yi), tz = sm3(z - zi);
  const h = (i, j, k) => hash3(xi + i, yi + j + 977 * seed, zi + k);
  const l = (a, b, t) => a + (b - a) * t;
  return l(l(l(h(0, 0, 0), h(1, 0, 0), tx), l(h(0, 1, 0), h(1, 1, 0), tx), ty), l(l(h(0, 0, 1), h(1, 0, 1), tx), l(h(0, 1, 1), h(1, 1, 1), tx), ty), tz) * 2 - 1;
}
/** Hot-dip galvanized steel: a soft mottle of duller and brighter zinc (no spangle sparkle at 240 texels a block). */
const galvanized = base => pos => { const [x, y, z] = pos.map(v => v / PX); const m = 0.045 * vn3(x / 70, y / 110, z / 70, 1) + 0.02 * vn3(x / 22, y / 30, z / 22, 2);
  return {c: base.map(v => v * (1 + m)), sm: 92 - 60 * m}; };
/** Cast-in-place concrete: a soft mottle only (no form-tube seam or other drawn lines). */
const concrete = base => pos => { const [x, y, z] = pos.map(v => v / PX); const k = 1 + 0.03 * vn3(x / 90, y / 90, z / 90, 3) + 0.012 * vn3(x / 25, y / 25, z / 25, 4);
  return {c: base.map(v => v * k)}; };

// ---------------- base ----------------
export const BASE = {pad: 25, pierR: 300, pierTop: 750, chamfer: 20, plate: 300, plateY: [780, 805], bolt: 110, pole: 127, poleR: 12};
// the standard AFL power port (tools/afl-power-port.mjs PORT, docs/models/power_cable_v2.md), in mm: a 6 x 6 px steel plate
// whose mating face lies on the cell's bottom face, a round socket and a pin; the cable comes up from the cell below
const PORT_MM = {half: 3.0 / PX, socket: 1.95 / PX, gap: 0.05 / PX, depth: 0.6 / PX, chamfer: 0.12 / PX, pinR: 0.45 / PX};
function bottomPort(P, bone) {
  const Q = PORT_MM, d = Q.depth;
  // the plate from the boundary up (into the pad and the pier's foot), a socket hole down its middle
  planY(P('port_plate', bone, 'portPlate'), rrect(0, 0, 2 * Q.half, 2 * Q.half, 0), [circle(0, 0, Q.socket, 16)], 0, d + 0.02 / PX, Q.chamfer);
  // the socket cup inside the hole (its floor faces down, 0.24 px into the plate) and the pin standing down from it
  latheY(P('port_socket', bone, 'portSocket'), 0, 0, [[Q.socket - 0.05 / PX, d - 0.22 / PX], [Q.socket - 0.05 / PX, d + 0.02 / PX]], 16);
  latheY(P('port_pin', bone, 'portPin'), 0, 0, [[Q.pinR, d - 0.42 / PX], [Q.pinR, d - 0.22 / PX]], 8);
}
function buildBase() {
  const PARTS = [], P = (name, bone, mat) => { const p = new Part(name, bone, mat); PARTS.push(p); return p; };
  const B = BASE, hole = PORT_MM.half + PORT_MM.gap;
  // the pad, with the port's opening through it (the plate's sides stand 0.05 px clear of the hole's walls)
  planY(P('pad', 'body', 'pad'), rrect(0, 0, 999.6, 999.6, 8), [rrect(0, 0, 2 * hole, 2 * hole, 0)], 0, B.pad, 5);
  bottomPort(P, 'body');
  // the pier starts 1 mm inside the pad, so its closed bottom never shares the pad's top plane
  latheY(P('pier', 'body', 'pier'), 0, 0, [[B.pierR, B.pad - 1], [B.pierR, B.pierTop - B.chamfer], [B.pierR - B.chamfer, B.pierTop]], 40);
  for (const [sx, sz] of [[-1, -1], [1, -1], [1, 1], [-1, 1]]) {
    const x = sx * B.bolt, z = sz * B.bolt;
    latheY(P(`bolt_${sx}${sz}`, 'body', 'bolt'), x, z, [[12, B.pierTop - 1], [12, 838], [9, 850]], 10);
    hex(P(`level_nut_${sx}${sz}`, 'body', 'nut'), x, z, 36, 754, 774);
    latheY(P(`washer_lo_${sx}${sz}`, 'body', 'nut'), x, z, [[26, 774], [26, B.plateY[0]]], 16);
    latheY(P(`washer_hi_${sx}${sz}`, 'body', 'nut'), x, z, [[26, B.plateY[1]], [26, B.plateY[1] + 4]], 16);
    hex(P(`nut_${sx}${sz}`, 'body', 'nut'), x, z, 36, B.plateY[1] + 4, B.plateY[1] + 24);
  }
  planY(P('plate', 'body', 'steel'), rrect(0, 0, B.plate, B.plate, 28, 2), [], B.plateY[0], B.plateY[1], 2);
  planY(P('weld', 'body', 'weld'), rrect(0, 0, B.pole + 10, B.pole + 10, B.poleR + 5), [], B.plateY[1], B.plateY[1] + 7, 2.5);
  planY(P('foot', 'body', 'steel'), rrect(0, 0, B.pole, B.pole, B.poleR), [], B.plateY[1] + 7, 1000, 0);
  const MATS = {
    pad:   MAT(concrete([164, 162, 156]), 4, 52, 60),
    pier:  MAT(concrete([176, 173, 166]), 6, 58, 66),
    steel: MAT(galvanized([170, 174, 172]), 8, 92, 110, 230),
    weld:  MAT(galvanized([150, 153, 150]), 4, 70, 80, 230),
    bolt:  MAT(galvanized([160, 163, 161]), 6, 96, 110, 230),
    nut:   MAT(galvanized([150, 153, 151]), 8, 88, 104, 230),
    // the port's own materials (tools/build-fuel-canopy-v1.mjs MATS: the same standard port)
    portPlate:  MAT([150, 154, 160], 12, 150, 168, 255),
    portSocket: MAT([26, 27, 29], 2, 60, 70, 20),
    portPin:    MAT([96, 100, 104], 6, 130, 140, 255),
  };
  return {PARTS, MATS, atlas: 1024, startS: 24};
}

// ---------------- pole segment ----------------
function buildPole() {
  const PARTS = [], P = (name, bone, mat) => { const p = new Part(name, bone, mat); PARTS.push(p); return p; };
  const B = BASE, hp = B.pole / 2;
  planY(P('shaft', 'body', 'steel'), rrect(0, 0, B.pole, B.pole, B.poleR), [], 0, 1000, 0);
  // hand hole (first segment): a rounded cover plate on the north face, 4 mm proud, two screws
  alongZ(P('handhole', 'handhole', 'cover'), rrect(0, 330, 72, 150, 18, 4), -hp - 4, -hp + 0.6, 1.2);
  for (const y of [272, 388]) cyl(P(`screw_${y}`, 'handhole', 'screw'), 'z', 0, y, 5, -hp - 6.5, -hp - 3.5, 12);
  const MATS = {
    steel: MAT(galvanized([170, 174, 172]), 8, 92, 110, 230),
    cover: MAT(galvanized([158, 162, 160]), 10, 86, 108, 230),
    screw: MAT([120, 122, 122], 6, 120, 130, 230),
  };
  return {PARTS, MATS, atlas: 256, startS: 24};
}

// ---------------- head cell ----------------
export const HEAD = {top: 245, arm: [90, 60], armY: 180, reach: 380, body: [400, 560, 80], bodyY: 128, fins: 11};
function buildHead() {
  const PARTS = [], P = (name, bone, mat) => { const p = new Part(name, bone, mat); PARTS.push(p); return p; };
  const B = BASE, H = HEAD, hp = B.pole / 2;
  // the pole's top and its cap
  planY(P('shaft', 'top', 'steel'), rrect(0, 0, B.pole, B.pole, B.poleR), [], 0, H.top, 0);
  planY(P('cap', 'top', 'steel'), rrect(0, 0, B.pole + 18, B.pole + 18, B.poleR + 6), [], H.top, H.top + 10, 3);
  latheY(P('cap_boss', 'top', 'steel'), 0, 0, [[18, H.top + 9], [18, H.top + 14], [10, H.top + 18]], 12);
  // one head, pointing north: pole plate with four bolts, the arm, the luminaire (frame ends at the arm's end)
  const [aw, ah] = H.arm, ay = H.armY, z0 = -hp, zr = -hp - H.reach;
  box(P('plate', 'arm', 'steel'), [-60, ay - 85, z0 - 10], [60, ay + 65, z0], 2);
  for (const x of [-38, 38]) for (const y of [ay - 62, ay + 48]) hex(P(`plate_bolt_${x}_${y}`, 'arm', 'nut'), x, 0, 17, 0, 9), turnBolt(PARTS[PARTS.length - 1], x, y, z0 - 10);
  alongZ(P('arm', 'arm', 'steel'), rrect(0, ay, aw, ah, 6, 2), zr, z0 - 10, 3);
  const [bw, bl, bh] = H.body, by = H.bodyY, bz = zr - bl / 2 + 20;   // the arm runs 20 mm into the head's slipfitter
  // die-cast housing: a rounded slab with chamfered edges; the slipfitter boss at the rear
  planY(P('housing', 'head', 'housing'), rrect(0, bz, bw, bl, 36, 4), [], by, by + bh, 10);
  box(P('slipfitter', 'head', 'housing'), [-aw / 2 - 18, ay - ah / 2 - 14, zr - 50], [aw / 2 + 18, by + bh - 8, zr + 22], 6);   // below the housing's top: no shared plane
  // heat-sink fins along the head (front two thirds), a smooth driver cover at the rear
  const finZ0 = bz - bl / 2 + 40, finZ1 = bz + bl / 2 - 210;
  for (let i = 0; i < H.fins; i++) { const x = -150 + 300 * i / (H.fins - 1); box(P(`fin_${i}`, 'head', 'housing'), [x - 2.5, by + bh - 2, finZ0], [x + 2.5, by + bh + 22, finZ1], 1); }
  planY(P('driver', 'head', 'housing'), rrect(0, bz + bl / 2 - 120, 300, 150, 30, 3), [], by + bh - 2, by + bh + 20, 6);
  // the lens and its 6 x 4 LED optics, under the housing (drawn off or lit)
  for (const look of ['off', 'lit']) {
    planY(P('lens_' + look, 'lens_' + look, 'lens_' + look), rrect(0, bz, bw - 70, bl - 90, 22, 3), [], by - 3, by + 0.6, 1);
    for (let i = 0; i < 6; i++) for (let j = 0; j < 4; j++) {
      const x = -125 + 50 * j * 5 / 3, z = bz - 175 + 70 * i;
      latheY(P(`led_${look}_${i}_${j}`, 'lens_' + look, 'lens_' + look), x * 0.72, z, [[0, by - 10], [11, by - 9], [17, by - 6], [17, by - 2.9]], 10);   // the top cap inside the lens
    }
  }
  // twist-lock photocontrol on the facing head's driver cover
  const pz = bz + bl / 2 - 120;
  latheY(P('pc_receptacle', 'photocell', 'housing'), 0, pz, [[46, by + bh + 19], [46, by + bh + 34]], 20);   // 1 mm into the driver cover
  latheY(P('pc_body', 'photocell', 'pcbody'), 0, pz, [[42, by + bh + 33], [42, by + bh + 80], [36, by + bh + 90], [22, by + bh + 95], [0, by + bh + 96]], 20);
  // the sensor window: a closed ring round the body, 0.6 mm proud; its inner wall lies inside the body
  latheY(P('pc_window', 'photocell', 'pcwindow'), 0, pz, [[41.6, by + bh + 58], [42.6, by + bh + 58], [42.6, by + bh + 72], [41.6, by + bh + 72], [41.6, by + bh + 58]], 20, {capBottom: false, capTop: false});
  const MATS = {
    steel:    MAT(galvanized([170, 174, 172]), 8, 92, 110, 230),
    nut:      MAT(galvanized([150, 153, 151]), 8, 88, 104, 230),
    housing:  MAT(pos => ({c: [124, 128, 131].map(v => v * (1 + 0.012 * vn3(pos[0] / PX / 60, pos[1] / PX / 60, pos[2] / PX / 60, 5)))}), 10, 104, 128),
    pcbody:   MAT([214, 213, 206], 6, 120, 136),
    pcwindow: MAT([48, 40, 32], 2, 170, 180),
    lens_off: MAT([198, 202, 205], 4, 176, 186),
    lens_lit: MAT([255, 247, 228], 2, 176, 186),
  };
  return {PARTS, MATS, atlas: 512, startS: 24};
}
// a bolt head made upright by hex(): lay it on its side against the plate (axis along z)
function turnBolt(part, x, y, zFace) { part.v = part.v.map(([px, py, pz]) => [px, y + pz, zFace - py]); }

// ---------------- wall pack ----------------
export const WALLPACK = {w: 360, top: 690, bottom: 470, back: 490, front: 330};
function buildWallPack() {
  const PARTS = [], P = (name, bone, mat) => { const p = new Part(name, bone, mat); PARTS.push(p); return p; };
  const W = WALLPACK, hw = W.w / 2;
  alongZ(P('backplate', 'body', 'housing'), rrect(0, (W.top + W.bottom) / 2, 300, 200, 16, 3), W.back, 499.6, 2);
  // wedge body (side profile z, y): deep at the top, a sloped front, the lens across the bottom
  alongX(P('body', 'body', 'housing'), [[W.back, W.bottom], [W.back, W.top], [W.front + 30, W.top], [W.front, W.top - 60], [W.front, W.bottom + 10], [W.front + 12, W.bottom]], -hw, hw, 7);
  for (let i = 0; i < 7; i++) { const x = -150 + 50 * i; box(P(`fin_${i}`, 'body', 'housing'), [x - 3, W.top - 2, W.front + 50], [x + 3, W.top + 14, W.back - 10], 1); }
  latheY(P('knockout', 'body', 'housing'), 25, (W.back + W.front) / 2 + 40, [[13, W.top - 1], [13, W.top + 10]], 12);   // between the fins at x 0 and 50, 1 mm into the housing
  for (const x of [-hw + 22, hw - 22]) cyl(P(`screw_${x}`, 'body', 'screw'), 'z', x, W.top - 40, 5, W.front - 2.5, W.front + 1, 12);
  for (const look of ['off', 'lit']) {
    planY(P('lens_' + look, 'lens_' + look, 'lens_' + look), rrect(0, (W.back + W.front) / 2 - 6, W.w - 60, W.back - W.front - 50, 14, 3), [], W.bottom - 3, W.bottom + 0.6, 1);
    for (let i = 0; i < 6; i++) for (let j = 0; j < 2; j++)
      latheY(P(`led_${look}_${i}_${j}`, 'lens_' + look, 'lens_' + look), -125 + 50 * i, (W.back + W.front) / 2 - 6 - 30 + 60 * j, [[0, W.bottom - 9], [9, W.bottom - 8], [15, W.bottom - 5.5], [15, W.bottom - 2.9]], 10);
  }
  const MATS = {
    housing:  MAT(pos => ({c: [124, 128, 131].map(v => v * (1 + 0.012 * vn3(pos[0] / PX / 60, pos[1] / PX / 60, pos[2] / PX / 60, 6)))}), 10, 104, 128),
    screw:    MAT([110, 112, 112], 6, 120, 130, 230),
    lens_off: MAT([198, 202, 205], 4, 176, 186),
    lens_lit: MAT([255, 247, 228], 2, 176, 186),
  };
  return {PARTS, MATS, atlas: 512, startS: 48};
}

// ---------------- canopy downlight ----------------
export const DOWNLIGHT = {top: 1031, r: 75, h: 75};
function buildDownlight() {
  const PARTS = [], P = (name, bone, mat) => { const p = new Part(name, bone, mat); PARTS.push(p); return p; };
  const D = DOWNLIGHT, y1 = D.top - 8, y0 = y1 - D.h;
  // the bottom up: the lens recessed 15 mm, the reflector cone out to the trim ring, the chamfered can, its ceiling plate
  for (const look of ['off', 'lit']) latheY(P('lens_' + look, 'lens_' + look, 'lens_' + look), 0, 0, [[0, y0 + 15], [48.5, y0 + 15]], 32, {capBottom: false, capTop: false});
  latheY(P('reflector', 'body', 'reflector'), 0, 0, [[48.5, y0 + 15], [60, y0]], 32, {capBottom: false, capTop: false});
  latheY(P('can', 'body', 'body'), 0, 0, [[60, y0], [D.r - 3, y0], [D.r, y0 + 3], [D.r, y1], [65, y1], [65, D.top]], 32, {capBottom: false});   // lens + reflector + can: one closed shell
  const MATS = {
    body:      MAT([58, 60, 63], 10, 100, 128),
    reflector: MAT([214, 214, 210], 6, 170, 186),
    lens_off:  MAT([198, 202, 205], 4, 176, 186),
    lens_lit:  MAT([255, 247, 228], 2, 176, 186),
  };
  return {PARTS, MATS, atlas: 256, startS: 48};
}

export const EMISSION = {lens_lit: 240};
export const GLOW = new Set(['lens_lit']);

// ---------------- bake ----------------
const DUMMY = 'data:image/png;base64,' + png(Buffer.from([0, 0, 0, 255]), 1, 1).toString('base64');
function bake(key, id, source, build) {
  const {PARTS: all, MATS, atlas, startS} = build(), PARTS = all.filter(p => p.f.length);
  for (const p of PARTS) p.v = p.v.map(q => q.map(v => v * PX));
  for (const p of PARTS) {
    const out = [];
    for (const f of p.f) {
      const P3 = f.ids.map(i => p.v[i]), n = newell(P3);
      const convexQuad = f.ids.length === 4 && [0, 1, 2, 3].every(k => dot(cross(sub(P3[(k + 1) % 4], P3[k]), sub(P3[(k + 2) % 4], P3[(k + 1) % 4])), n) > 1e-14);
      if (f.ids.length === 3 || convexQuad) { out.push(f); continue; }
      assert(f.ids.length === 4, `${key}: ${f.ids.length}-gon in ${p.name}`);
      const k = [0, 1, 2, 3].find(k => dot(cross(sub(P3[k], P3[(k + 3) % 4]), sub(P3[(k + 1) % 4], P3[k])), n) <= 1e-14) ?? 0;
      out.push({...f, ids: [f.ids[k], f.ids[(k + 1) % 4], f.ids[(k + 2) % 4]]}, {...f, ids: [f.ids[(k + 2) % 4], f.ids[(k + 3) % 4], f.ids[k]]});
    }
    p.f = out;
  }
  const UV = unwrap(PARTS, {atlas, pad: 2, startS, stepS: 0.5});
  for (const p of PARTS) assert(p.f.every(f => UV.faceUV.get(p)?.has(f)), `${key}: unmapped face in ${p.name}`);
  const first = Object.values(MATS)[0], bg = typeof first.c === 'function' ? [160, 160, 160] : first.c;
  const painted = paint({PARTS, islands: UV.islands, S: UV.S, uvOf: UV.uvOf, atlas, pad: 2, MATS, ZONED: new Set(), groupInfo: new Map(),
    sourceGroups: () => [], refTexture: {source: DUMMY}, refUvWidth: 1, background: {c: [...bg, 255], s: [first.sm, first.f0, 0, 255], n: [128, 128, 255, 255]}});
  const maps = painted.PNG.map(b => { const r = readPng(b); assert(r.bpp === 4 && r.w === atlas, 'atlas format'); return r.px; });
  for (const is of UV.islands) {
    const e = EMISSION[is.part.mat]; if (e === undefined) continue;
    for (let y = is.py - 2; y < is.py + is.H + 2; y++) for (let x = is.px - 2; x < is.px + is.W + 2; x++) maps[1][(y * atlas + x) * 4 + 3] = e;
  }
  return {key, id, source, PARTS, MATS, atlas, UV, maps: maps.map(px => png(px, atlas, atlas))};
}
const B = {
  base: bake('base', 'light_pole_base', 'light_pole_base_v1', buildBase),
  pole: bake('pole', 'light_pole', 'light_pole_v1', buildPole),
  head: bake('head', 'area_light', 'area_light_v1', buildHead),
  wallpack: bake('wallpack', 'wall_pack', 'wall_pack_v1', buildWallPack),
  downlight: bake('downlight', 'canopy_downlight', 'canopy_downlight_v1', buildDownlight),
};
const LOOKS = {
  base: [B.base, new Set(['body'])],
  pole_plain: [B.pole, new Set(['body'])], pole_handhole: [B.pole, new Set(['body', 'handhole'])],
  head_top: [B.head, new Set(['top'])], head_off: [B.head, new Set(['arm', 'head', 'lens_off'])], head_lit: [B.head, new Set(['arm', 'head', 'lens_lit'])], head_photocell: [B.head, new Set(['photocell'])],
  wallpack_off: [B.wallpack, new Set(['body', 'lens_off'])], wallpack_lit: [B.wallpack, new Set(['body', 'lens_lit'])],
  downlight_off: [B.downlight, new Set(['body', 'lens_off'])], downlight_lit: [B.downlight, new Set(['body', 'lens_lit'])],
};
const coplanar = {};
for (const [k, [b, bones]] of Object.entries(LOOKS)) { const zf = zFightLevels(b.PARTS.filter(p => bones.has(p.bone)), new Map()); if (zf.unresolved.length) coplanar[k] = zf.unresolved.slice(0, 6); }
// closed surfaces (2026-10-09, user: "还有透明面"): a held, dropped or framed item is seen from every side, so every part must
// be a closed surface (no open edge), wound one way (every shared edge run once each way) and facing out (positive volume)
function solidity(p) {
  const key = q => q.map(v => Math.round(v * 1e5)).join(','), dir = new Map();
  let vol = 0;
  for (const f of p.f) {
    const P3 = f.ids.map(i => p.v[i]);
    for (let j = 1; j + 1 < P3.length; j++) vol += dot(P3[0], cross(P3[j], P3[j + 1])) / 6;
    for (let j = 0; j < f.ids.length; j++) { const a = key(P3[j]), b = key(P3[(j + 1) % P3.length]); if (a !== b) dir.set(a + '>' + b, (dir.get(a + '>' + b) || 0) + 1); }
  }
  let open = 0, flipped = 0;
  for (const [e, n] of dir) { const [a, b] = e.split('>'), back = dir.get(b + '>' + a) || 0; if (!back) open++; if (n > 1) flipped++; }
  return {open, flipped, vol};
}
// the downlight's lens, reflector and can are one shell split by material: checked together, each look
const SHELLS = {downlight: [['lens_off', 'reflector', 'can'], ['lens_lit', 'reflector', 'can']]};
export const solids = {};
for (const b of Object.values(B)) {
  const shells = SHELLS[b.key] || [], inShell = new Set(shells.flat());
  for (const p of b.PARTS.filter(p => !inShell.has(p.name))) { const s = solidity(p); if (s.open || s.flipped || s.vol <= 0) solids[b.key + '/' + p.name] = s; }
  for (const g of shells) { const m = {v: [], f: []}; for (const p of b.PARTS.filter(p => g.includes(p.name))) { const o = m.v.length; m.v.push(...p.v); m.f.push(...p.f.map(f => ({ids: f.ids.map(i => i + o)}))); }
    const s = solidity(m); if (s.open || s.flipped || s.vol <= 0) solids[b.key + '/' + g.join('+')] = s; }
}
assert(!Object.keys(solids).length, 'not closed / wound out: ' + JSON.stringify(solids));

// ---------------- OBJ / MTL / models ----------------
const D2R = Math.PI / 180, f6 = v => (+v.toFixed(6)).toString(), r3 = v => +v.toFixed(3) || 0;
const SMOOTH = Math.cos(36 * D2R);
function cornerNormals(p) {
  const fn = p.f.map(f => newell(f.ids.map(i => p.v[i]))), byV = new Map();
  p.f.forEach((f, k) => f.ids.forEach(i => (byV.get(i) || byV.set(i, []).get(i)).push(k)));
  return p.f.map((f, k) => { const n0 = norm(fn[k]); return f.ids.map(i => norm(byV.get(i).reduce((a, j) => dot(norm(fn[j]), n0) >= SMOOTH ? add(a, fn[j]) : a, [0, 0, 0]))); });
}
function objOf(title, file, b, bones) {
  const out = [`# AFL ${title}, generated by tools/build-site-lighting-v1.mjs`, `mtllib ${file.split('/').pop()}.mtl`];
  let vBase = 1, tBase = 1, nBase = 1;
  for (const p of b.PARTS) {
    if (!bones.has(p.bone)) continue;
    out.push(`o ${p.name}`, `usemtl ${b.key}${GLOW.has(p.mat) ? '_glow' : ''}`);
    for (const q of p.v) out.push(`v ${f6(q[0] / 16 + 0.5)} ${f6(q[1] / 16)} ${f6(q[2] / 16 + 0.5)}`);
    const uvs = b.UV.faceUV.get(p), vt = [], vn = [], fl = [], cn = cornerNormals(p), nIndex = new Map();
    p.f.forEach((f, k) => {
      const uv = uvs.get(f);
      fl.push('f ' + f.ids.map((id, j) => {
        const key = cn[k][j].map(f6).join(' ');
        if (!nIndex.has(key)) { nIndex.set(key, nBase + vn.length); vn.push('vn ' + key); }
        vt.push(`vt ${f6(uv[j][0] / b.atlas)} ${f6(1 - uv[j][1] / b.atlas)}`); return `${vBase + id}/${tBase + vt.length - 1}/${nIndex.get(key)}`;
      }).join(' '));
    });
    out.push(...vt, ...vn, ...fl);
    vBase += p.v.length; tBase += vt.length; nBase += vn.length;
  }
  return out.join('\n') + '\n';
}
const mtlOf = (title, b, bones) => `# AFL ${title}\nnewmtl ${b.key}\nKd 1 1 1\nmap_Kd apocalypse_firstlight:block/${b.id}\n` +
  (b.PARTS.some(p => bones.has(p.bone) && GLOW.has(p.mat)) ? `newmtl ${b.key}_glow\nKa 1 1 1\nKd 1 1 1\nmap_Kd apocalypse_firstlight:block/${b.id}\n` : '');
const objModel = (file, particle) => ({loader: 'forge:obj', model: `apocalypse_firstlight:models/block/${file}.obj`, automatic_culling: false,
  flip_v: true, shade_quads: true, emissive_ambient: true, ambientocclusion: false, textures: {particle: `apocalypse_firstlight:block/${particle}`}});
function guiFit(b, bones, rotation, size = 15.2) {
  const [ax, ay] = rotation.map(v => v * D2R);
  const rot = q => { const x = q[0] * Math.cos(ay) + q[2] * Math.sin(ay), z = -q[0] * Math.sin(ay) + q[2] * Math.cos(ay); return [x, q[1] * Math.cos(ax) - z * Math.sin(ax)]; };
  const pts = b.PARTS.filter(p => bones.has(p.bone)).flatMap(p => p.v.map(q => rot([q[0], q[1] - 8, q[2]])));
  const xs = pts.map(q => q[0]), ys = pts.map(q => q[1]), w = Math.max(...xs) - Math.min(...xs), h = Math.max(...ys) - Math.min(...ys);
  // vanilla clamps a display scale to 4 (ItemTransform): a small piece (the downlight) wanted 5.4, was drawn at 4 with the
  // translation of 5.4 and sat half a slot low (user 2026-10-09); centre it at the scale actually used
  const scale = r3(Math.min(4, size / Math.max(w, h))), cx = (Math.max(...xs) + Math.min(...xs)) / 2, cy = (Math.max(...ys) + Math.min(...ys)) / 2;
  return {rotation, translation: [r3(-scale * cx), r3(-scale * cy), 0], scale: [scale, scale, scale]};
}
const S3 = v => [v, v, v];
// held, dropped and framed: fitted to the model's own bounds (2026-10-09: the fixed translations of the first version put the
// downlight, which hangs at the top of its cell, above the hand: "手持物品都飞上天了"); size = times a held block
const cellPoints = (b, bones) => b.PARTS.filter(p => bones.has(p.bone)).flatMap(p => p.v.map(([x, y, z]) => [x + 8, y, z + 8]));
const display = (gui, b, bones, size) => ({...heldDisplay(cellPoints(b, bones), {size, rotations: {fixed: [0, 180, 0]}}), gui});

// editable Free Model sources (AGENTS.md): one group per bone
const uuidOf = (ns, s) => { const h = createHash('sha256').update(`afl-site-lighting-v1:${ns}:${s}`).digest('hex'); return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-8${h.slice(17, 20)}-${h.slice(20, 32)}`; };
const r12 = v => +v.toFixed(12) || 0;
function sourceOf(b) {
  const uuid = s => uuidOf(b.id, s), name = b.source;
  const bones = [...new Set(b.PARTS.map(p => p.bone))], gid = bone => uuid('group:' + bone);
  const elements = b.PARTS.map(p => {
    const key = i => i.toString(36).padStart(4, '0'), vertices = {}, faces = {}, uvs = b.UV.faceUV.get(p);
    p.v.forEach((q, i) => { vertices[key(i)] = q.map(r12); });
    p.f.forEach((f, fi) => { const uv = uvs.get(f); faces['f' + key(fi)] = {uv: Object.fromEntries(f.ids.map((id, j) => [key(id), uv[j].map(r12)])), vertices: f.ids.map(key), texture: 0}; });
    return {name: p.name, color: 0, origin: [0, 0, 0], rotation: [0, 0, 0], shading: 'flat', export: true, visibility: true, locked: false,
      render_order: 'default', scope: 0, allow_mirror_modeling: true, vertices, faces, type: 'mesh', uuid: uuid('mesh:' + p.name)};
  });
  return {meta: {format_version: '5.0', model_format: 'free', box_uv: false}, name, model_identifier: '', visible_box: [1, 1, 0],
    variable_placeholders: '', variable_placeholder_buttons: [], timeline_setups: [], unhandled_root_fields: {}, resolution: {width: b.atlas, height: b.atlas},
    elements, groups: bones.map(bone => ({name: bone, uuid: gid(bone), export: true, locked: false, scope: 0,
      selected: false, visibility: true, _static: {properties: {}, temp_data: {}}, origin: [0, 0, 0], rotation: [0, 0, 0],
      color: 0, children: [], reset: false, shade: true, mirror_uv: false, autouv: 0, isOpen: true, primary_selected: false})),
    outliner: bones.map(bone => ({uuid: gid(bone), isOpen: true, children: elements.filter((e, i) => b.PARTS[i].bone === bone).map(e => e.uuid)})),
    textures: [{name: name + '.png', relative_path: `textures/${name}.png`, folder: '', namespace: '', id: '0', group: '', scope: 0,
      width: b.atlas, height: b.atlas, uv_width: b.atlas, uv_height: b.atlas, particle: false, use_as_default: false, layers_enabled: false, sync_to_project: '',
      file_format: 'png', render_mode: 'default', render_sides: 'auto', wrap_mode: 'limited', pbr_channel: 'color', fps: 7, frame_time: 1,
      frame_order_type: 'loop', frame_order: '', frame_interpolate: false, visible: true, internal: true, saved: true, uuid: uuid('texture'),
      source: 'data:image/png;base64,' + b.maps[0].toString('base64')}],
    animations: []};
}

const bb = path.join(ROOT, 'src/main/blockbench'), assets = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight');
const json = v => JSON.stringify(v, null, 2) + '\n';
const outputs = [], objs = [];
const NS = 'apocalypse_firstlight:block/';
const model = (file, title, b, bones) => {
  const obj = objOf(title, file, b, bones);
  outputs.push([path.join(assets, `models/block/${file}.obj`), obj], [path.join(assets, `models/block/${file}.mtl`), mtlOf(title, b, bones)],
    [path.join(assets, `models/block/${file}.json`), json(objModel(file, b.id))]);
  objs.push([file, obj]);
};
for (const b of Object.values(B)) outputs.push([path.join(bb, `${b.source}.bbmodel`), JSON.stringify(sourceOf(b))],
  ...['', '_s', '_n'].flatMap((k, i) => [[path.join(bb, `textures/${b.source}${k}.png`), b.maps[i]], [path.join(assets, `textures/block/${b.id}${k}.png`), b.maps[i]]]));
const YROT = {north: 0, east: 90, south: 180, west: 270};
const yr = y => (y % 360 ? {y: y % 360} : {});

// base: one model (round pier), FACING only orients the first segment's hand hole
model('light_pole_base/base', 'Site Lighting V1 light pole base', ...LOOKS.base);
outputs.push([path.join(assets, 'blockstates/light_pole_base.json'), json({variants: Object.fromEntries(Object.keys(YROT).map(f => [`facing=${f}`, {model: NS + 'light_pole_base/base'}]))})]);
outputs.push([path.join(assets, 'models/item/light_pole_base.json'), json({parent: NS + 'light_pole_base/base', gui_light: 'side', display: display(guiFit(...LOOKS.base, [30, 225, 0]), ...LOOKS.base, 1)})]);
// pole: plain, or the hand hole on the side HANDHOLE names
model('light_pole/plain', 'Site Lighting V1 light pole segment', ...LOOKS.pole_plain);
model('light_pole/handhole', 'Site Lighting V1 light pole segment with the hand hole', ...LOOKS.pole_handhole);
outputs.push([path.join(assets, 'blockstates/light_pole.json'), json({variants: {'handhole=none': {model: NS + 'light_pole/plain'},
  ...Object.fromEntries(Object.entries(YROT).map(([f, y]) => [`handhole=${f}`, {model: NS + 'light_pole/handhole', ...yr(y)}]))}})]);
outputs.push([path.join(assets, 'models/item/light_pole.json'), json({parent: NS + 'light_pole/plain', gui_light: 'side', display: display(guiFit(...LOOKS.pole_plain, [30, 225, 0]), ...LOOKS.pole_plain, 1)})]);
// head cell: the top always; a head (off / lit) on each side HEADS names, turned from FACING; the photocontrol on the facing head
model('area_light/top', 'Site Lighting V1 pole top', ...LOOKS.head_top);
model('area_light/head_off', 'Site Lighting V1 area light head (off)', ...LOOKS.head_off);
model('area_light/head_lit', 'Site Lighting V1 area light head (lit)', ...LOOKS.head_lit);
model('area_light/photocell', 'Site Lighting V1 photocontrol', ...LOOKS.head_photocell);
export const HEADS = {single: [0], twin: [0, 2], twin_corner: [0, 1], triple: [0, 1, 3], quad: [0, 1, 2, 3]};   // quarter turns clockwise from FACING
{
  const multipart = [{apply: {model: NS + 'area_light/top'}}];
  for (const [f, y] of Object.entries(YROT)) {
    multipart.push({when: {facing: f}, apply: {model: NS + 'area_light/photocell', ...yr(y)}});
    for (let o = 0; o < 4; o++) {
      const with_ = Object.entries(HEADS).filter(([, os]) => os.includes(o)).map(([k]) => k).join('|');
      for (const lit of [false, true]) multipart.push({when: {facing: f, heads: with_, lit: String(lit)}, apply: {model: NS + `area_light/head_${lit ? 'lit' : 'off'}`, ...yr(y + 90 * o)}});
    }
  }
  outputs.push([path.join(assets, 'blockstates/area_light.json'), json({multipart})]);
}
// the item: a pole top with one head (a composite of the three)
outputs.push([path.join(assets, 'models/block/area_light/item.json'), json({loader: 'forge:composite', textures: {particle: NS + 'area_light'},
  children: {top: {parent: NS + 'area_light/top'}, head: {parent: NS + 'area_light/head_off'}, photocell: {parent: NS + 'area_light/photocell'}}})]);
outputs.push([path.join(assets, 'models/item/area_light.json'), json({parent: NS + 'area_light/item', gui_light: 'side',
  display: display(guiFit(B.head, new Set(['top', 'arm', 'head', 'lens_off', 'photocell']), [30, 135, 0]), B.head, new Set(['top', 'arm', 'head', 'lens_off', 'photocell']), 1)})]);
// wall pack and downlight: off / lit
for (const look of ['off', 'lit']) {
  model(`wall_pack/${look}`, `Site Lighting V1 wall pack (${look})`, ...LOOKS['wallpack_' + look]);
  model(`canopy_downlight/${look}`, `Site Lighting V1 canopy downlight (${look})`, ...LOOKS['downlight_' + look]);
}
outputs.push([path.join(assets, 'blockstates/wall_pack.json'), json({variants: Object.fromEntries(Object.entries(YROT).flatMap(([f, y]) => [false, true].map(lit =>
  [`facing=${f},lit=${lit}`, {model: NS + `wall_pack/${lit ? 'lit' : 'off'}`, ...yr(y)}])))})]);
outputs.push([path.join(assets, 'models/item/wall_pack.json'), json({parent: NS + 'wall_pack/off', gui_light: 'side', display: display(guiFit(...LOOKS.wallpack_off, [20, 205, 0]), ...LOOKS.wallpack_off, 0.75)})]);
outputs.push([path.join(assets, 'blockstates/canopy_downlight.json'), json({variants: {'lit=false': {model: NS + 'canopy_downlight/off'}, 'lit=true': {model: NS + 'canopy_downlight/lit'}}})]);
outputs.push([path.join(assets, 'models/item/canopy_downlight.json'), json({parent: NS + 'canopy_downlight/off', gui_light: 'side', display: display(guiFit(...LOOKS.downlight_off, [30, 225, 0], 13), ...LOOKS.downlight_off, 0.6)})]);
// the hidden light point: no model of its own (invisible); a particle for the block's breaking effects
outputs.push([path.join(assets, 'models/block/lamp_glow.json'), json({textures: {particle: NS + 'area_light'}, elements: []})]);
outputs.push([path.join(assets, 'blockstates/lamp_glow.json'), json({variants: {'': {model: NS + 'lamp_glow'}}})]);

const tris = (b, bones) => b.PARTS.filter(p => bones.has(p.bone)).reduce((s, p) => s + p.f.reduce((t, f) => t + f.ids.length - 2, 0), 0);
export const stats = Object.fromEntries(Object.entries(LOOKS).map(([k, [b, bones]]) => [k, tris(b, bones)]));
const isMain = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  console.log(JSON.stringify({triangles: stats, texelsPerPx: Object.fromEntries(Object.entries(B).map(([k, b]) => [k, b.UV.S])), coplanar}));
  const pi = process.argv.indexOf('--preview');
  if (pi > 0) {
    const dir = process.argv[pi + 1]; fs.mkdirSync(dir, {recursive: true});
    for (const [file, obj] of objs) fs.writeFileSync(path.join(dir, file.replace('/', '_') + '.obj'), obj);
    for (const b of Object.values(B)) ['', '_s', '_n'].forEach((k, i) => fs.writeFileSync(path.join(dir, `${b.id}${k}.png`), b.maps[i]));
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
