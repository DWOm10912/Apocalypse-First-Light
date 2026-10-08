// Power Outlets V1 (Building Power V1 step 2a, docs/models/power_outlets_v1.md): the NA duplex wall outlet (NEMA 5-15R on
// a white nylon plate), the 3-outlet and the 2x3 (6-outlet) power strip in black ABS, and the NEMA 5-15P plug that the
// strips' cords end in. Everything is 1.5 x real size (user 2026-10-08: real size is barely visible at 1 block = 1 m), so
// the plug fits the sockets. Pure Mesh + LabPBR atlases, exported as Forge OBJ block models (chunk-baked); the plug is an
// extra model the strip's renderer draws (client/PowerStripRenderer) with its cord, painted from this atlas' cord swatch.
//   node tools/build-power-outlets-v1.mjs                -> writes sources, OBJ / MTL / block + item models, blockstates, atlases
//   node tools/build-power-outlets-v1.mjs --check        -> verifies every output is up to date
//   node tools/build-power-outlets-v1.mjs --preview DIR  -> writes only OBJ + maps into DIR (offline review)
// Modelled in millimetres (real x 1.5), converted to px at the end (1 mm = 0.016 px), so the small chamfers survive the
// library's minimum-size rules. Frames (px, block bottom centre at the origin, facing=north):
// - wall outlet: front toward -Z, the wall at z = +8; plate centre 5.6 px up (0.35 m: a floor-cell outlet; in the cell
//   above a counter it sits at 1.35 m). Ground hole down; the neutral (long) slot on the left seen from the room (+X).
// - power strips: long axis X, switch and cord at the +X end, flat on the cell floor (lowered: on the 13.5 px office desk).
//   A red indicator beside the reset button; it and the rocker glow at full brightness in the lit state (GLOW).
// - plug: its face (blade side) at z = 0 with the blades toward -Z, the head toward +Z, the cord leaving along +Z; centred
//   on the block centre in the OBJ, so the renderer turns it about (0.5, 0.5, 0.5).
// Plain surfaces: no printed legends or logos. Dielectric materials (LabPBR F0 20), nickel-plated brass only on the blades.
import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {iecOutline, INLET} from './afl-iec-inlet.mjs';
import {Part, AX, extrude, unwrap, paint, png, readPng, zFightLevels, area2, add, sub, mul, dot, cross, norm, newell} from './cube-slab-mesh-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
function assert(c, m) { if (!c) throw new Error(m); }
const PX = 0.016;            // px per mm
const K = 1.5;               // scale over real size
const m = v => v * K;        // real mm -> model mm

// ---------------- outlines (mm, counter-clockwise) ----------------
const orient = (L, ccw) => (area2(L) > 0) === ccw ? L : L.slice().reverse();
const rect = (u0, v0, u1, v1) => [[u0, v0], [u1, v0], [u1, v1], [u0, v1]];
function rrect(cu, cv, w, h, r, seg = 3) {
  const u0 = cu - w / 2, v0 = cv - h / 2, u1 = cu + w / 2, v1 = cv + h / 2, out = [];
  for (const [ccu, ccv, a0] of [[u1 - r, v1 - r, 0], [u0 + r, v1 - r, 90], [u0 + r, v0 + r, 180], [u1 - r, v0 + r, 270]])
    for (let i = 0; i <= seg; i++) { const a = (a0 + 90 * i / seg) * Math.PI / 180; out.push([ccu + r * Math.cos(a), ccv + r * Math.sin(a)]); }
  return out;
}
// a receptacle face: a circle of radius R with flats at +-f, centred at (cu, cv)
function faceOutline(cu, cv, R, f, seg = 6) {
  const a = Math.asin(f / R), out = [];
  for (let i = 0; i <= seg; i++) { const t = -a + 2 * a * i / seg; out.push([cu + R * Math.cos(t), cv + R * Math.sin(t)]); }
  for (let i = 0; i <= seg; i++) { const t = Math.PI - a + 2 * a * i / seg; out.push([cu + R * Math.cos(t), cv + R * Math.sin(t)]); }
  return out;
}
// NEMA 5-15R openings in the face's own (u, v): ground hole down, neutral (long) slot at +u. tf maps (u, v) to the plane.
function nemaHoles(tf) {
  const slot = (cu, len) => rect(cu - m(0.95), m(3.5) - len / 2, cu + m(0.95), m(3.5) + len / 2);
  const r = m(2.6), gy = -m(6.6), d = [[r, gy]];
  for (let i = 1; i < 10; i++) { const t = -Math.PI * i / 10; d.push([r * Math.cos(t), gy + r * Math.sin(t)]); }
  d.push([-r, gy]);
  return [slot(m(6.35), m(8.3)), slot(-m(6.35), m(6.8)), d].map(L => L.map(tf));
}
const shapeOf = (outer, holes = []) => ({outer: orient(outer, true), holes: holes.map(h => orient(h, false))});
// plan outline (x, z) along y: the library's y plane is (u, v) = (z, x)
const planY = (part, outer, holes, y0, y1, c) => extrude(part, 'y', shapeOf(outer.map(([x, z]) => [z, x]), holes.map(h => h.map(([x, z]) => [z, x]))), y0, y1, c);
function cyl(part, ax, cu, cv, r, a0, a1, seg, tag = 'side') {
  const A = AX[ax], ang = i => Math.PI / seg + 2 * Math.PI * i / seg;
  const rg = a => Array.from({length: seg}, (_, i) => part.vtx(A.to3(cu + r * Math.cos(ang(i)), cv + r * Math.sin(ang(i)), a)));
  const r0 = rg(a0), r1 = rg(a1);
  for (let i = 0; i < seg; i++) { const j = (i + 1) % seg, mm = (ang(i) + ang(j)) / 2;
    part.face([r0[i], r0[j], r1[j], r1[i]], A.to3(Math.cos(mm), Math.sin(mm), 0), tag); }
  for (const [ring, a, s] of [[r0, a0, -1], [r1, a1, 1]]) { const c = part.vtx(A.to3(cu, cv, a));
    for (let i = 0; i < seg; i++) part.face([c, ring[i], ring[(i + 1) % seg]], mul(A.n, s), 'cap'); }
}
// a frustum along z between radius r0 at a0 and r1 at a1, capped
function cone(part, cu, cv, r0, a0, r1, a1, seg) {
  const ang = i => Math.PI / seg + 2 * Math.PI * i / seg;
  const ring = (r, a) => Array.from({length: seg}, (_, i) => part.vtx([cu + r * Math.cos(ang(i)), cv + r * Math.sin(ang(i)), a]));
  const A = ring(r0, a0), B = ring(r1, a1), slope = (r0 - r1) / (a1 - a0);
  for (let i = 0; i < seg; i++) { const j = (i + 1) % seg, mm = (ang(i) + ang(j)) / 2; part.face([A[i], A[j], B[j], B[i]], [Math.cos(mm), Math.sin(mm), slope], 'side'); }
  for (const [R, a, s] of [[A, a0, -1], [B, a1, 1]]) { const c = part.vtx([cu, cv, a]); for (let i = 0; i < seg; i++) part.face([c, R[i], R[(i + 1) % seg]], [0, 0, s], 'cap'); }
}
const slab = (part, ax, a, b, c = 0) => { const [ku, kv, ka] = ax === 'x' ? [2, 1, 0] : ax === 'y' ? [2, 0, 1] : [0, 1, 2];
  extrude(part, ax, shapeOf(rect(a[ku], a[kv], b[ku], b[kv])), a[ka], b[ka], c); };

// ---------------- wall outlet ----------------
export const OUTLET = {
  plate: [m(69.9), m(114.3)], plateT: m(5), faceProud: m(2.5), faceR: m(17.2), faceF: m(14.4), pitch: m(19.05),
  centreY: 350, wall: 500 - 0.2,   // mm in the block frame (5.6 px up; 0.2 mm off the wall face)
};
function buildOutlet() {
  const PARTS = [], P = (name, mat) => { const p = new Part(name, 'main', mat); PARTS.push(p); return p; };
  const O = OUTLET, zb = O.wall, zf = zb - O.plateT, zFace = zf - O.faceProud, cy = O.centreY;
  const faces = [cy + O.pitch, cy - O.pitch];
  // plate: rounded rectangle with the two face openings, chamfered front edge and opening rims (the back is on the wall)
  extrude(P('plate', 'plate'), 'z', shapeOf(rrect(0, cy, ...O.plate, m(3)), faces.map(v => faceOutline(0, v, O.faceR + m(0.7), O.faceF + m(0.7)))),
    zf, zb, m(1.5));
  for (const [i, v] of faces.entries()) {
    // the receptacle face, standing 2.5 mm proud of the plate, with the NEMA openings through it
    extrude(P('face_' + i, 'device'), 'z', shapeOf(faceOutline(0, v, O.faceR, O.faceF), nemaHoles(([u, w]) => [u, v + w])), zFace, zb - m(0.6), m(0.5));
    // dark contact cavity 5 mm behind the face (seen through the openings)
    extrude(P('cavity_' + i, 'cavity'), 'z', shapeOf(faceOutline(0, v, O.faceR - m(1.2), O.faceF - m(1.2))), zFace + m(5), zFace + m(5.5), 0);
  }
  // the device strap behind the plate, seen only through the gap around each face
  slab(P('strap', 'device'), 'z', [-m(16), cy - m(38), zb - m(0.55)], [m(16), cy + m(38), zb - m(0.05)]);
  // plate screw between the faces, its slot slightly proud
  cyl(P('screw', 'screw'), 'z', 0, cy, m(3.3), zf - m(0.9), zf + m(0.3), 14);
  slab(P('screw_slot', 'cavity'), 'z', [-m(2.4), cy - m(0.32), zf - m(1.05)], [m(2.4), cy + m(0.32), zf - m(0.85)]);
  const MATS = {
    plate:  {c: [233, 232, 225], hl: 6, sm: 150, se: 168, f0: 20},
    device: {c: [227, 226, 218], hl: 6, sm: 142, se: 160, f0: 20},
    cavity: {c: [20, 20, 22], hl: 0, sm: 40, se: 40, f0: 20},
    screw:  {c: [224, 223, 216], hl: 10, sm: 168, se: 186, f0: 20},
  };
  return {PARTS, MATS, atlas: 256, startS: 64};
}

// a closed loft along z through rounded-rectangle sections [z, w, h, r] (equal ring sizes), both ends capped
function loftSections(part, sections) {
  const rings = sections.map(([z, w, h, r]) => rrect(0, 0, w, h, r, 3).map(([x, y]) => part.vtx([x, y, z])));
  for (let k = 0; k + 1 < rings.length; k++) for (let i = 0; i < rings[k].length; i++) {
    const j = (i + 1) % rings[k].length, a = part.v[rings[k][i]], b = part.v[rings[k][j]];
    part.face([rings[k][i], rings[k][j], rings[k + 1][j], rings[k + 1][i]], [(a[0] + b[0]) / 2, (a[1] + b[1]) / 2, 0], 'side');
  }
  for (const [ring, z, sgn] of [[rings[0], sections[0][0], -1], [rings[rings.length - 1], sections[sections.length - 1][0], 1]]) {
    const c = part.vtx([0, 0, z]);
    for (let i = 0; i < ring.length; i++) part.face([c, ring[i], ring[(i + 1) % ring.length]], [0, 0, sgn], 'cap');
  }
}

// ---------------- power strips + plug (one atlas) ----------------
export const STRIP = {
  3: {L: m(200), W: m(58), xs: [m(-58), m(-8), m(42)], rows: [[0, -1]], rocker: [m(72), 0], button: [m(90.5), 0], led: [m(90.5), -m(12)]},
  6: {L: m(236), W: m(112), xs: [m(-60), m(-6), m(48)], rows: [[-m(24), -1], [m(24), 1]], rocker: [m(90), -m(20)], button: [m(90), m(24)], led: [m(90), m(12)]},
  H: m(36), r: m(12), chamfer: m(4), faceProud: m(2.2), grommetR: m(6), grommetY: m(18),
};
// plug (real mm, x 1.5 by m()): face 30 x 35 (fits the duplex: 35 < the 38.1 socket pitch), head 30 long, relief to 54
export const PLUG = {w: m(30), h: m(35), r: m(5), cordR: m(4), length: m(54),
  // lofted sections along z: [z, width, height, corner radius]; the round ones are circles (w = h = 2r)
  sections: [[0, 30, 35, 5], [3, 30, 35, 5], [14, 27.5, 30, 6], [24, 21, 21.5, 8], [30, 15, 15, 7.49],
    [31.5, 15, 15, 7.49], [32, 16.2, 16.2, 8.09], [34, 16.2, 16.2, 8.09], [34.5, 14.6, 14.6, 7.29], [36.5, 14.6, 14.6, 7.29],
    [37, 15.6, 15.6, 7.79], [39, 15.6, 15.6, 7.79], [39.5, 14, 14, 6.99], [41.5, 14, 14, 6.99], [42, 15, 15, 7.49], [44, 15, 15, 7.49],
    [50, 10.6, 10.6, 5.29], [54, 8.6, 8.6, 4.29]].map(([z, w, h, r]) => [m(z), m(w), m(h), m(r)])};
// C13 connector (real mm): the keyed mating part (fits the C14 inlet's cavity, tools/afl-iec-inlet.mjs) 8.5 deep, a
// larger grip body behind its shoulder, a ribbed relief into the cord; earth toward +Y. Length 50 (the cord leaves there).
export const CONNECTOR = {mate: [24.0, 15.0, 4.0, 8.5], length: m(50),
  sections: [[8.5, 31, 23, 3.5], [10.5, 31, 23, 3.5], [24, 28.5, 21, 5], [30, 19, 17, 8.49], [31, 15, 15, 7.49],
    [31.5, 16.2, 16.2, 8.09], [33.5, 16.2, 16.2, 8.09], [34, 14.6, 14.6, 7.29], [36, 14.6, 14.6, 7.29], [36.5, 15.6, 15.6, 7.79],
    [38.5, 15.6, 15.6, 7.79], [39, 14, 14, 6.99], [41, 14, 14, 6.99], [46, 10.6, 10.6, 5.29], [50, 8.6, 8.6, 4.29]].map(([z, w, h, r]) => [m(z), m(w), m(h), m(r)])};
function buildStripsAndPlug() {
  const PARTS = [], P = (name, bone, mat) => { const p = new Part(name, bone, mat); PARTS.push(p); return p; };
  const H = STRIP.H;
  for (const n of [3, 6]) {
    const S = STRIP[n], body = 'body' + n;
    planY(P(`strip${n}_shell`, body, 'shell'), rrect(0, 0, S.L, S.W, STRIP.r), [], 0, H, STRIP.chamfer);
    // receptacle faces on top, turned so the ground hole points to the strip's long edge (both edges on the 2x3)
    for (const [zc, side] of S.rows) for (const [i, x] of S.xs.entries()) {
      // face up (slots) toward the middle, ground hole toward the long edge; not mirrored: seen from above with the slots up,
      // the neutral (long) slot is on the left, as on the wall outlet (PowerStripBlock SOCKETS_* mirrors these places)
      const tf = ([u, v]) => [x - u * side, zc - v * side];
      planY(P(`strip${n}_face_${side > 0 ? 'b' : 'a'}${i}`, body, 'device'), faceOutline(0, 0, m(17.2), m(14.4)).map(tf), nemaHoles(tf),
        H - m(1), H + STRIP.faceProud, m(0.5));
    }
    // lit rocker switch in a bezel, the breaker's reset button, the cord grommet at the +X end
    const [rx, rz] = S.rocker, rw = m(18), rd = m(14);
    planY(P(`strip${n}_bezel`, body, 'bezel'), rrect(rx, rz, rw + m(4), rd + m(4), m(2)), [rrect(rx, rz, rw + m(0.6), rd + m(0.6), m(1.2))], H - m(1), H + m(1.4), m(0.4));
    for (const [look, mat, tilt] of [['off', 'rocker', -1], ['on', 'rocker', 1], ['lit', 'rocker_lit', 1]]) {
      const lo = H + m(1.4), hi = H + m(4.6), yA = tilt > 0 ? hi : lo, yB = tilt > 0 ? lo : hi;   // on: the -X half pressed up
      extrude(P(`strip${n}_rocker_${look}`, `rocker${n}_${look}`, mat), 'z', shapeOf([[rx - rw / 2, H - m(0.8)], [rx + rw / 2, H - m(0.8)], [rx + rw / 2, yB], [rx, (yA + yB) / 2 + m(0.9)], [rx - rw / 2, yA]]),
        rz - rd / 2, rz + rd / 2, m(0.6));
    }
    cyl(P(`strip${n}_button`, body, 'bezel'), 'y', S.button[1], S.button[0], m(4.5), H - m(1), H + m(2.2), 14);
    // power indicator beside the reset button (user 2026-10-08): a lens in a black housing, red and glowing while the strip has power
    cyl(P(`strip${n}_led_housing`, body, 'bezel'), 'y', S.led[1], S.led[0], m(3.4), H - m(1), H + m(0.6), 12);
    for (const [look, mat] of [['off', 'led_off'], ['lit', 'led_lit']]) cyl(P(`strip${n}_led_${look}`, `led${n}_${look}`, mat), 'y', S.led[1], S.led[0], m(2.3), H + m(0.6), H + m(1.8), 12);
    cyl(P(`strip${n}_grommet`, body, 'grommet'), 'x', 0, STRIP.grommetY, STRIP.grommetR, S.L / 2 - m(3), S.L / 2 + m(9), 14);
  }
  // NEMA 5-15P plug: moulded head, strain relief tapering to the cord, two flat blades (neutral wider) and the ground pin.
  // Seen from its face the neutral blade is at -X, so it meets the outlet's neutral slot (+X) when turned to face the wall.
  // the moulded body: one loft through the sections (flat face band, taper, round neck, four relief ribs, into the cord)
  const Q = PLUG;
  loftSections(P('plug_body', 'plug', 'plug'), Q.sections);
  // blades (neutral wider) with their holes near the tip, 1.5 mm brass, standing 16 mm out of the face; the round ground pin
  for (const [name, x, w] of [['plug_blade_n', -m(6.35), m(7.9)], ['plug_blade_h', m(6.35), m(6.35)]])
    extrude(P(name, 'plug', 'blade'), 'x', shapeOf(rect(-m(16), m(3.5) - w / 2, m(1), m(3.5) + w / 2),
      [Array.from({length: 10}, (_, i) => [-m(11.5) + m(1.5) * Math.cos(Math.PI * i / 5), m(3.5) + m(1.5) * Math.sin(Math.PI * i / 5)])]),
      x - m(0.75), x + m(0.75), 0);
  cyl(P('plug_pin', 'plug', 'blade'), 'z', 0, -m(7.6), m(2.4), -m(17), m(1), 12);
  cone(P('plug_pin_tip', 'plug', 'blade'), 0, -m(7.6), m(1.4), -m(19), m(2.4), -m(17), 12);
  // IEC C13 connector, the cord's appliance end (always in the appliance's C14 inlet): mating part, grip body, relief
  const C = CONNECTOR, [mw, mh, mc, md] = C.mate;
  extrude(P('connector_mate', 'connector', 'plug'), 'z', shapeOf(iecOutline(0, 0, m(mw), m(mh), m(mc))), 0, m(md), 0);
  loftSections(P('connector_body', 'connector', 'plug'), C.sections);
  // the cord's colour swatch (never exported): the renderer's cord takes its UV
  slab(P('cord_swatch', 'swatch', 'cord'), 'z', [m(300), 0, 0], [m(306), m(6), m(1)]);
  const MATS = {
    shell:      {c: [30, 31, 33], hl: 10, sm: 122, se: 142, f0: 20},
    device:     {c: [37, 38, 41], hl: 8, sm: 128, se: 146, f0: 20},
    bezel:      {c: [22, 22, 24], hl: 6, sm: 104, se: 120, f0: 20},
    rocker:     {c: [118, 24, 18], hl: 8, sm: 168, se: 180, f0: 20},
    rocker_lit: {c: [246, 74, 48], hl: 6, sm: 176, se: 186, f0: 20},
    led_off:    {c: [74, 16, 14], hl: 6, sm: 176, se: 186, f0: 20},
    led_lit:    {c: [255, 58, 40], hl: 4, sm: 180, se: 190, f0: 20},
    grommet:    {c: [26, 26, 28], hl: 4, sm: 86, se: 96, f0: 20},
    plug:       {c: [24, 25, 27], hl: 10, sm: 92, se: 118, f0: 20},
    blade:      {c: [196, 190, 172], hl: 14, sm: 186, se: 196, f0: 255},
    cord:       {c: [27, 28, 30], hl: 0, sm: 92, se: 92, f0: 20},
  };
  return {PARTS, MATS, atlas: 512, startS: 48};
}
export const EMISSION = {rocker_lit: 200, led_lit: 220};
// materials drawn at full brightness (Forge OBJ emissive_ambient: their MTL material has Ka 1 1 1), so the lit rocker
// and the indicator glow without shaders too
export const GLOW = new Set(['rocker_lit', 'led_lit']);   // LabPBR _s alpha (0..254 = emission strength, 255 = none)

// ---------------- bake ----------------
const DUMMY = 'data:image/png;base64,' + png(Buffer.from([0, 0, 0, 255]), 1, 1).toString('base64');
function bake(key, id, build) {
  const {PARTS: all, MATS, atlas, startS} = build(), PARTS = all.filter(p => p.f.length);
  for (const p of PARTS) p.v = p.v.map(q => q.map(v => v * PX));   // mm -> px
  // the OBJ keeps faces as they are (Forge draws a quad as 0-1-2 / 2-3-0): split anything not a triangle / convex quad
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
  const first = Object.values(MATS)[0];
  const painted = paint({PARTS, islands: UV.islands, S: UV.S, uvOf: UV.uvOf, atlas, pad: 2, MATS, ZONED: new Set(), groupInfo: new Map(),
    sourceGroups: () => [], refTexture: {source: DUMMY}, refUvWidth: 1, background: {c: [...first.c, 255], s: [first.sm, first.f0, 0, 255], n: [128, 128, 255, 255]}});
  const maps = painted.PNG.map(b => { const r = readPng(b); assert(r.bpp === 4 && r.w === atlas, 'atlas format'); return r.px; });
  for (const is of UV.islands) {
    const e = EMISSION[is.part.mat]; if (e === undefined) continue;
    for (let y = is.py - 2; y < is.py + is.H + 2; y++) for (let x = is.px - 2; x < is.px + is.W + 2; x++) maps[1][(y * atlas + x) * 4 + 3] = e;
  }
  return {key, id, PARTS, MATS, atlas, UV, maps: maps.map(px => png(px, atlas, atlas))};
}
const B = {outlet: bake('outlet', 'wall_outlet', buildOutlet), strip: bake('strip', 'power_strip', buildStripsAndPlug)};
// coplanar faces, checked per exported look (the strips' alternative rockers share their place on purpose)
const LOOKS = {
  wall_outlet: [B.outlet, null],
  ...Object.fromEntries([3, 6].flatMap(n => ['off', 'on', 'lit'].map(l => [`power_strip_${n}_${l}`, [B.strip, new Set(['body' + n, `rocker${n}_${l}`, `led${n}_${l === 'lit' ? 'lit' : 'off'}`])]]))),
  power_plug: [B.strip, new Set(['plug'])],
  power_connector: [B.strip, new Set(['connector'])],
};
const coplanar = {};
for (const [k, [b, bones]] of Object.entries(LOOKS)) { const zf = zFightLevels(b.PARTS.filter(p => !bones || bones.has(p.bone)), new Map()); if (zf.unresolved.length) coplanar[k] = zf.unresolved.slice(0, 6); }

// the cord swatch's UV (0..1 of the atlas): the largest island of the swatch part, its centre (mirrored in PowerStripRenderer)
const swatch = B.strip.UV.islands.filter(is => is.part.name === 'cord_swatch').sort((a, b) => b.W * b.H - a.W * a.H)[0];
export const CORD_UV = [+((swatch.px + swatch.W / 2) / B.strip.atlas).toFixed(6), +((swatch.py + swatch.H / 2) / B.strip.atlas).toFixed(6)];

// ---------------- OBJ / MTL / models ----------------
const D2R = Math.PI / 180, f6 = v => (+v.toFixed(6)).toString(), r3 = v => +v.toFixed(3) || 0;
const SMOOTH = Math.cos(36 * D2R);
function cornerNormals(p) {
  const fn = p.f.map(f => newell(f.ids.map(i => p.v[i]))), byV = new Map();
  p.f.forEach((f, k) => f.ids.forEach(i => (byV.get(i) || byV.set(i, []).get(i)).push(k)));
  return p.f.map((f, k) => { const n0 = norm(fn[k]); return f.ids.map(i => norm(byV.get(i).reduce((a, j) => dot(norm(fn[j]), n0) >= SMOOTH ? add(a, fn[j]) : a, [0, 0, 0]))); });
}
function objOf(title, file, b, bones, offset = [0, 0, 0]) {
  const out = [`# AFL ${title}, generated by tools/build-power-outlets-v1.mjs`, `mtllib ${file.split('/').pop()}.mtl`];
  let vBase = 1, tBase = 1, nBase = 1;
  for (const p of b.PARTS) {
    if (bones && !bones.has(p.bone)) continue;
    out.push(`o ${p.name}`, `usemtl ${b.key}${GLOW.has(p.mat) ? '_glow' : ''}`);
    for (const q of p.v) out.push(`v ${f6((q[0] + offset[0]) / 16 + 0.5)} ${f6((q[1] + offset[1]) / 16)} ${f6((q[2] + offset[2]) / 16 + 0.5)}`);
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
const mtlOf = (title, b) => `# AFL ${title}\nnewmtl ${b.key}\nKd 1 1 1\nmap_Kd apocalypse_firstlight:block/${b.id}\n` +
  (b.PARTS.some(p => GLOW.has(p.mat)) ? `newmtl ${b.key}_glow\nKa 1 1 1\nKd 1 1 1\nmap_Kd apocalypse_firstlight:block/${b.id}\n` : '');
const objModel = (file, particle) => ({loader: 'forge:obj', model: `apocalypse_firstlight:models/block/${file}.obj`, automatic_culling: false,
  flip_v: true, shade_quads: true, emissive_ambient: true, ambientocclusion: false, textures: {particle: `apocalypse_firstlight:block/${particle}`}});
function guiCentred(b, bones, rotation, scale) {
  const [ax, ay] = rotation.map(v => v * D2R);
  const rot = q => { const x = q[0] * Math.cos(ay) + q[2] * Math.sin(ay), z = -q[0] * Math.sin(ay) + q[2] * Math.cos(ay); return [x, q[1] * Math.cos(ax) - z * Math.sin(ax)]; };
  const pts = b.PARTS.filter(p => !bones || bones.has(p.bone)).flatMap(p => p.v.map(q => rot([q[0], q[1] - 8, q[2]])));
  const cx = (Math.max(...pts.map(q => q[0])) + Math.min(...pts.map(q => q[0]))) / 2, cy = (Math.max(...pts.map(q => q[1])) + Math.min(...pts.map(q => q[1]))) / 2;
  return {rotation, translation: [r3(-scale * cx), r3(-scale * cy), 0], scale: [scale, scale, scale]};
}
const S3 = v => [v, v, v];
const display = (b, bones, gui, guiScale, s) => ({
  thirdperson_righthand: {rotation: [75, 45, 0], translation: [0, 2.5, 0], scale: S3(s)}, thirdperson_lefthand: {rotation: [75, 45, 0], translation: [0, 2.5, 0], scale: S3(s)},
  firstperson_righthand: {rotation: [0, 45, 0], translation: [0, 2.5, 0], scale: S3(s * 1.1)}, firstperson_lefthand: {rotation: [0, 225, 0], translation: [0, 2.5, 0], scale: S3(s * 1.1)},
  gui: guiCentred(b, bones, gui, guiScale), ground: {translation: [0, 2, 0], scale: S3(s)}, fixed: {rotation: [0, 180, 0], translation: [0, 0, 0], scale: S3(s * 1.1)}});

// selection boxes (cell px, facing=north, before the desk sink), mirrored in WallOutletBlock / PowerStripBlock
export const SELECTION = {wall_outlet: [6.9, 3.4, 15.5, 9.1, 7.8, 16], power_strip_3: [5.3, 0, 7.15, 10.7, 1.25, 8.85], power_strip_6: [4.9, 0, 6.5, 11.1, 1.25, 9.5]};
export const LOWERED = -2.5;   // px, on the 13.5 px office desk (as OfficeDesktopDecorationBlock)
const fitsIn = (b, bones, box) => b.PARTS.filter(p => !bones || bones.has(p.bone)).every(p => p.v.every(q => [0, 1, 2].every(k => {
  const v = q[k] + (k === 1 ? 0 : 8); return v >= box[k] - 0.02 && v <= box[k + 3] + 0.02; })));
assert(fitsIn(B.outlet, null, SELECTION.wall_outlet), 'outlet leaves its selection box');
for (const n of [3, 6]) assert(fitsIn(B.strip, new Set(['body' + n, `rocker${n}_on`, `rocker${n}_off`]), SELECTION['power_strip_' + n]), n + '-outlet strip leaves its selection box');

// editable Free Model sources (px, frames as the header): one group per bone
const uuidOf = (ns, s) => { const h = createHash('sha256').update(`afl-power-outlets-v1:${ns}:${s}`).digest('hex'); return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-8${h.slice(17, 20)}-${h.slice(20, 32)}`; };
const r12 = v => +v.toFixed(12) || 0;
function sourceOf(b) {
  const uuid = s => uuidOf(b.id, s), name = b.id + '_v1';
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
    elements, groups: bones.map(bone => ({name: bone === 'main' ? b.key : bone, uuid: gid(bone), export: bone !== 'swatch', locked: false, scope: 0,
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
const model = (file, title, b, bones, offset) => {
  const obj = objOf(title, file, b, bones, offset);
  outputs.push([path.join(assets, `models/block/${file}.obj`), obj], [path.join(assets, `models/block/${file}.mtl`), mtlOf(title, b)],
    [path.join(assets, `models/block/${file}.json`), json(objModel(file, b.id))]);
  objs.push([file, obj]);
};
for (const b of Object.values(B)) outputs.push([path.join(bb, `${b.id}_v1.bbmodel`), JSON.stringify(sourceOf(b))],
  ...['', '_s', '_n'].flatMap((k, i) => [[path.join(bb, `textures/${b.id}_v1${k}.png`), b.maps[i]], [path.join(assets, `textures/block/${b.id}${k}.png`), b.maps[i]]]));
const YROT = {north: 0, east: 90, south: 180, west: 270};
// wall outlet: one model; the socket flags only record what is plugged in
model('wall_outlet', 'Power Outlets V1 wall outlet', B.outlet, null);
{
  const variants = {};
  for (const [f, y] of Object.entries(YROT)) for (const up of [false, true]) for (const lo of [false, true])
    variants[`facing=${f},lower=${lo},upper=${up}`] = {model: 'apocalypse_firstlight:block/wall_outlet', ...(y ? {y} : {})};
  outputs.push([path.join(assets, 'blockstates/wall_outlet.json'), json({variants})]);
  outputs.push([path.join(assets, 'models/item/wall_outlet.json'), json({parent: 'apocalypse_firstlight:block/wall_outlet', gui_light: 'side',
    display: display(B.outlet, null, [10, 200, 0], 4.2, 1.6)})]);
}
// strips: off / on (unlit) / lit (on with power), each also on the desk
for (const n of [3, 6]) {
  const id = 'power_strip_' + n;
  for (const look of ['off', 'on', 'lit']) {
    const bones = new Set(['body' + n, `rocker${n}_${look}`, `led${n}_${look === 'lit' ? 'lit' : 'off'}`]);
    model(`${id}/${look}`, `Power Outlets V1 ${n}-outlet strip (${look})`, B.strip, bones);
    model(`${id}/${look}_lowered`, `Power Outlets V1 ${n}-outlet strip (${look}, on desk)`, B.strip, bones, [0, LOWERED, 0]);
  }
  const variants = {};
  for (const [f, y] of Object.entries(YROT)) for (const low of [false, true]) for (const on of [false, true]) for (const lit of [false, true])
    variants[`facing=${f},lit=${lit},lowered=${low},on=${on}`] = {model: `apocalypse_firstlight:block/${id}/${on ? (lit ? 'lit' : 'on') : 'off'}${low ? '_lowered' : ''}`, ...(y ? {y} : {})};
  outputs.push([path.join(assets, `blockstates/${id}.json`), json({variants})]);
  outputs.push([path.join(assets, `models/item/${id}.json`), json({parent: `apocalypse_firstlight:block/${id}/on`, gui_light: 'side',
    display: display(B.strip, new Set(['body' + n, `rocker${n}_on`, `led${n}_off`]), [30, 135, 0], n === 3 ? 2.6 : 2.3, n === 3 ? 1.1 : 0.95)})]);
}
// the plug, centred on the block centre (renderer turns it about (0.5, 0.5, 0.5))
model('power_plug', 'Power Outlets V1 plug', B.strip, new Set(['plug']), [0, 8, 0]);
// the C13 connector, centred the same way (its mating face at the block centre, the cord leaving along +Z)
model('power_connector', 'Power Outlets V1 C13 connector', B.strip, new Set(['connector']), [0, 8, 0]);
assert(m(CONNECTOR.mate[3]) * PX < INLET.proud, 'the connector seats in the inlet cavity');

const tris = (b, bones) => b.PARTS.filter(p => !bones || bones.has(p.bone)).reduce((s, p) => s + p.f.reduce((t, f) => t + f.ids.length - 2, 0), 0);
export const stats = {
  outlet: {triangles: tris(B.outlet), texelsPerPx: B.outlet.UV.S, islands: B.outlet.UV.islands.length},
  strip3: {triangles: tris(B.strip, new Set(['body3', 'rocker3_on'])), strip6: tris(B.strip, new Set(['body6', 'rocker6_on'])), plug: tris(B.strip, new Set(['plug'])), connector: tris(B.strip, new Set(['connector'])),
    texelsPerPx: B.strip.UV.S, islands: B.strip.UV.islands.length}, cordUV: CORD_UV, coplanar};
const isMain = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  console.log(JSON.stringify(stats));
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
