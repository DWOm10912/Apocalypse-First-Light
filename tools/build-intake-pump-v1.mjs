// Intake Pump V1 (docs/models/intake_pump_v1.md): a two-block bank-side pump set. The bank cell (the master, on the
// ground) carries a dark green close-coupled end-suction centrifugal pump on a black steel skid: the volute at the cell
// centre with its discharge straight up into a round-to-square reducer and the AFL fluid port on the top face centre
// (tools/afl-fluid-port.mjs), the TEFC motor behind it (axial fins, fan cowl with a grille), and a grey starter box on the
// right carrying the AFL power port at the right face centre (tools/afl-power-port.mjs), the rotary isolator switch and the
// status lamp. The front cell reaches over the liquid: the skid's cantilever and the suction line (a grooved coupling at
// the bank edge, a saddle with a strap, a 90 degree elbow at the cell centre), which drops into the block below the front
// cell and ends in a strainer. The switch handle (on / off) and the lamp lens (off / idle amber / running green) are their
// own models, picked by the blockstate (IntakePumpBlock ON, LAMP).
// Pure Mesh + LabPBR atlas exported as Forge OBJ block models (static, chunk-baked, drawn by the bank cell).
//   node tools/build-intake-pump-v1.mjs                -> writes source, OBJ / MTL / block + item models, blockstate, atlas
//   node tools/build-intake-pump-v1.mjs --check        -> verifies every output is up to date
//   node tools/build-intake-pump-v1.mjs --preview DIR  -> writes only OBJ + maps into DIR (offline review)
// Frame (px): the bank cell's centre is the origin; front (toward the liquid) = -Z, the front cell is z -24..-8; seen from
// the bank looking at the liquid, the player's right = +X (FACING.getClockWise()); y up, the ground at y -8. OBJ in block
// units relative to the bank cell: x = px / 16 + 0.5 etc. Plain surfaces: no printed legends, logos or decals.
import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {Part, extrude, add, sub, mul, dot, cross, norm, newell, area2, unwrap, paint, png, readPng, zFightLevels} from './cube-slab-mesh-lib.mjs';
import {addFluidPort} from './afl-fluid-port.mjs';
import {addPowerPort} from './afl-power-port.mjs';

// --heat-resistant: the Heat-Resistant Intake Pump (2026-10-05, docs/models/heat_resistant_fluid_set_v1.md): the same pump in
// heat-resistant silver paint, its suction drop and strainer of refractory ceramic
const HEAT = process.argv.includes('--heat-resistant');
const ID = HEAT ? 'heat_resistant_intake_pump' : 'intake_pump';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
function assert(c, m) { if (!c) throw new Error(m); }
const D2R = Math.PI / 180;

// ---------------- primitives ----------------
const orient = (L, ccw) => (area2(L) > 0) === ccw ? L : L.slice().reverse();
const rect = (u0, v0, u1, v1) => [[u0, v0], [u1, v0], [u1, v1], [u0, v1]];
const shape = (L, holes = []) => ({outer: orient(L, true), holes: holes.map(h => orient(h, false))});
const circle = (cu, cv, r, n, ph = 0.5) => Array.from({length: n}, (_, i) => { const a = 2 * Math.PI * (i + ph) / n; return [cu + r * Math.cos(a), cv + r * Math.sin(a)]; });
const hex = (cu, cv, r) => Array.from({length: 6}, (_, i) => { const a = (30 + 60 * i) * D2R; return [cu + r * Math.cos(a), cv + r * Math.sin(a)]; });
// an axis-aligned box from corner a to corner b (px), chamfer c
function slab(part, ax, a, b, c = 0) {
  const [ku, kv, ka] = ax === 'x' ? [2, 1, 0] : ax === 'y' ? [2, 0, 1] : [0, 1, 2];
  extrude(part, ax, shape(rect(Math.min(a[ku], b[ku]), Math.min(a[kv], b[kv]), Math.max(a[ku], b[ku]), Math.max(a[kv], b[kv]))), Math.min(a[ka], b[ka]), Math.max(a[ka], b[ka]), c);
}
// hex nuts / bolt heads: along y at (x, z); along z at (x, y); along x at (y, z)
const boltY = (part, x, z, r, y0, y1) => extrude(part, 'y', shape(hex(z, x, r)), y0, y1, 0);
const boltZ = (part, x, y, r, z0, z1) => extrude(part, 'z', shape(hex(x, y, r)), z0, z1, 0);
const boltX = (part, y, z, r, x0, x1) => extrude(part, 'x', shape(hex(z, y, r)), x0, x1, 0);
// a surface of revolution: profile [[s, r]] along the axis; axis 'z' about c = (x, y), 'y' about (x, z), 'x' about (y, z)
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
// rings of 3D points (equal length) joined into a skin; hint: the outward direction of a face from its centroid
function skin(part, rings, hint) {
  const ids = rings.map(R => R.map(p => part.vtx(p))), n = rings[0].length;
  for (let r = 0; r + 1 < rings.length; r++) for (let k = 0; k < n; k++) {
    const q = [ids[r][k], ids[r][(k + 1) % n], ids[r + 1][(k + 1) % n], ids[r + 1][k]];
    const ctr = mul(q.reduce((s, id) => add(s, part.v[id]), [0, 0, 0]), 0.25);
    part.face(q, hint(ctr, r));
  }
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
const tone = (c, k) => c.map(v => v * k), mott = (p, s, k) => k * (fbm(p[0] * s, p[1] * s, p[2] * s) - 0.5);
export const MATS = {   // Base Color (sRGB or a function of the model position), bevel highlight, smoothness open / edge, F0
  paint:     {c: p => tone([46, 86, 60], 1 + mott(p, 0.25, 0.05)), hl: 7, sm: 118, se: 140, f0: 24},   // dark green machine enamel
  paintD:    {c: p => tone([38, 72, 50], 1 + mott(p, 0.3, 0.05)), hl: 6, sm: 112, se: 136, f0: 24},    // covers, flanges, coupling
  skid:      {c: p => tone([40, 42, 46], 1 + mott(p, 0.3, 0.04)), hl: 5, sm: 84, se: 104, f0: 22},     // black coated steel
  pipe:      {c: p => tone([60, 64, 70], 1 + mott(p, 0.3, 0.04)), hl: 8, sm: 110, se: 134, f0: 24},    // coated steel suction pipe
  cowl:      {c: p => tone([34, 35, 38], 1 + mott(p, 0.4, 0.04)), hl: 4, sm: 80, se: 96, f0: 20},      // fan cowl, conduit, glands, bezel
  grille:    {c: [16, 17, 18], hl: 0, sm: 50, se: 50, f0: 20},                                        // behind the cowl's grille rings
  bolt:      {c: [124, 128, 133], hl: 7, sm: 124, se: 140, f0: 30},
  box:       {c: p => tone([118, 122, 120], 1 + mott(p, 0.3, 0.04)), hl: 8, sm: 104, se: 128, f0: 22}, // starter box, grey enamel
  yellow:    {c: [196, 154, 36], hl: 6, sm: 140, se: 150, f0: 22},                                    // isolator escutcheon
  red:       {c: [164, 40, 32], hl: 6, sm: 150, se: 160, f0: 22},                                     // isolator handle
  lens:      {c: [64, 72, 66], hl: 3, sm: 196, se: 204, f0: 16},                                      // the lamp, dark
  // the lamp while lit: its _s alpha marks it LabPBR emissive and its MTL's Ka makes Forge bake it full bright
  lensRun:   {c: [120, 250, 150], hl: 0, sm: 190, se: 190, f0: 16},
  lensIdle:  {c: [255, 186, 60], hl: 0, sm: 190, se: 190, f0: 16},
  // the AFL fluid port (Fluid Pipe V2's flange steel), as the underground fuel tank's
  fportPlate:  {c: p => tone([62, 66, 72], 1 + mott(p, 0.3, 0.04)), hl: 10, sm: 120, se: 144, f0: 26},
  fportThroat: {c: [24, 25, 27], hl: 0, sm: 60, se: 60, f0: 20},
  fportStud:   {c: [124, 128, 133], hl: 7, sm: 124, se: 140, f0: 30},
  // the standard power port (tools/afl-power-port.mjs): steel plate, dark socket cup, contact pin
  pportPlate:  {c: [150, 154, 160], hl: 12, sm: 150, se: 168, f0: 255},
  pportSocket: {c: [26, 27, 29], hl: 2, sm: 60, se: 70, f0: 20},
  pportPin:    {c: [96, 100, 104], hl: 6, sm: 130, se: 140, f0: 255},
  ...(HEAT ? {
    paint:   {c: p => tone([168, 170, 172], 1 + mott(p, 0.25, 0.04)), hl: 8, sm: 120, se: 142, f0: 40},  // heat-resistant silver (aluminium) paint
    paintD:  {c: p => tone([146, 148, 151], 1 + mott(p, 0.3, 0.04)), hl: 7, sm: 114, se: 138, f0: 40},
    ceramic: {c: [194, 183, 160], hl: 6, sm: 46, se: 70, f0: 20},                                        // refractory ceramic (refractory_ceramic)
  } : {}),
};
const GLOW = new Set(['lensRun', 'lensIdle']);

// ---------------- dimensions (px) ----------------
export const Y = -1.6;                       // the pump shaft's height (axis along z)
export const SKID = {chan: [4.4, 6.0], y: [-8, -6.6], z: [-22.4, 7.4], cross: [[-1.6, 1.6], [3.2, 6.2], [-12.0, -10.4], [-22.4, -21.0], [6.6, 7.4]]};
export const VOLUTE = {z: [-1.7, 1.5], r0: 3.5, grow: 1.45, start: 111.6, cover: [-2.1, -1.7, 3.3]};
export const DISCHARGE = {neck: [Y + 3.2, 4.3, 2.0], flange: [4.3, 4.8, 3.0], reducer: [4.8, 6.6, 2.4, 4.2], square: [6.6, 7.4, 4.2]};
export const SUCTION = {nozzle: [-3.2, -2.1, 1.9], flange: [-3.7, -3.2, 2.8], r: 1.9, coupling: [-8.6, -7.4, 2.45], saddle: [-11.6, -10.8],
  bend: 2.2, elbowZ: -13.8, drop: -19.0, strainer: [-23.0, -19.6, 2.5]};
export const MOTOR = {bracket: [1.5, 2.1, 2.3], flange: [2.1, 2.6, 3.9], body: [2.6, 6.7, 3.4], fins: [2.9, 6.4, 4.0], cowl: [6.7, 7.6, 3.75]};
export const STARTER = {x: [4.2, 6.8], y: [-6.6, 3.6], z: [-3.4, 7.0], switch: [0.6, 5.0], lamp: [-2.6, 5.0]};
export const PORTS = {fluid: 'top face centre (bank cell)', power: '+X face centre (bank cell), on the starter box'};

function skidAndCasing(P) {
  // two C-channels the length of both cells (the front half cantilevered over the liquid), open toward the inside
  const chan = P('channels', 'body', 'skid'), [c0, c1] = SKID.chan;
  for (const s of [-1, 1]) {
    const L = [[c0, -8], [c1, -8], [c1, -6.6], [c0, -6.6], [c0, -7.0], [c1 - 0.4, -7.0], [c1 - 0.4, -7.6], [c0, -7.6]].map(([x, y]) => [x * s, y]);
    extrude(chan, 'z', shape(L), SKID.z[0], SKID.z[1], 0);
  }
  const cross = P('cross', 'body', 'skid');
  for (const [z0, z1] of SKID.cross) slab(cross, 'y', [-c0, -7.6, z0], [c0, -6.6, z1], 0.1);
  const nuts = P('anchor_nuts', 'body', 'bolt');
  for (const s of [-1, 1]) for (const z of [-21.7, -6.8, 6.9]) if (!(s > 0 && z > STARTER.z[0])) boltY(nuts, s * 5.2, z, 0.42, -6.6, -6.32);   // not under the starter box
  // volute casing: a spiral prism (axis z) growing toward the top, where the discharge leaves; its tongue at the step
  const N = 56, prof = Array.from({length: N + 1}, (_, i) => { const t = i / N, a = (VOLUTE.start + 360 * t) * D2R, r = VOLUTE.r0 + VOLUTE.grow * t;
    return [-r * Math.cos(a), Y + r * Math.sin(a)]; });   // mirrored: the full side on -X, clear of the starter box
  extrude(P('volute', 'body', 'paint'), 'z', shape(prof), VOLUTE.z[0], VOLUTE.z[1], 0.3);
  const [cz0, cz1, cr] = VOLUTE.cover;
  lathe(P('cover', 'body', 'paintD'), 'z', [0, Y], [[cz0, cr - 0.14], [cz0 + 0.12, cr], [cz1, cr]], 32, [true, false]);
  const cb = P('cover_bolts', 'body', 'bolt');
  for (let k = 0; k < 8; k++) { const a = (22.5 + 45 * k) * D2R; boltZ(cb, 2.85 * Math.cos(a), Y + 2.85 * Math.sin(a), 0.32, cz0 - 0.24, cz0 + 0.02); }
  // casing foot: base plate bolted to the cross member, an upright into the volute
  const foot = P('casing_foot', 'body', 'paint');
  slab(foot, 'y', [-2.8, -6.6, -1.2], [2.8, -6.2, 1.2], 0.1);
  slab(foot, 'y', [-1.6, -6.2, -0.8], [1.6, Y - 3.0, 0.8], 0.1);
  for (const s of [-1, 1]) boltY(nuts, s * 2.25, 0, 0.36, -6.2, -5.96);
}

function discharge(P) {
  const D = DISCHARGE, body = P('discharge', 'body', 'paint');
  cyl(body, 'y', [0, 0], D.neck[0], D.neck[1], D.neck[2], 24, [false, true]);
  lathe(P('discharge_flange', 'body', 'paintD'), 'y', [0, 0], [[D.flange[0], D.flange[2] - 0.15], [D.flange[0] + 0.12, D.flange[2]], [D.flange[1] - 0.12, D.flange[2]], [D.flange[1], D.flange[2] - 0.15]], 32);
  const fb = P('discharge_bolts', 'body', 'bolt');
  for (let k = 0; k < 4; k++) { const a = (45 + 90 * k) * D2R; boltY(fb, 2.6 * Math.cos(a), 2.6 * Math.sin(a), 0.3, D.flange[1], D.flange[1] + 0.25); }
  // round-to-square reducer: 32 points round the bore, each slid from the circle to the square
  const [y0, y1, r0, h] = D.reducer, seg = 32, rings = [];
  for (let i = 0; i <= 8; i++) { const t = i / 8, e = t * t * (3 - 2 * t), y = y0 + (y1 - y0) * t;
    rings.push(Array.from({length: seg}, (_, k) => { const a = 2 * Math.PI * (k + 0.5) / seg, c = Math.cos(a), s = Math.sin(a), m = h / Math.max(Math.abs(c), Math.abs(s));
      return [(r0 * c) * (1 - e) + c * m * e, y, (r0 * s) * (1 - e) + s * m * e]; })); }
  skin(P('reducer', 'body', 'paint'), rings, ctr => [ctr[0], 0, ctr[2]]);
  const sq = P('reducer_neck', 'body', 'paint');
  slab(sq, 'y', [-h, D.square[0], -h], [h, D.square[1], h], 0);
  // the AFL fluid port: built facing +Z with the mating face at z 0, turned to face up at y 8
  const port = {plate: P('fport_plate', 'body', 'fportPlate'), throat: P('fport_throat', 'body', 'fportThroat'), studs: P('fport_studs', 'body', 'fportStud')};
  addFluidPort(port, 0, 0, D.square[1] - 8, 0);
  for (const p of Object.values(port)) p.v = p.v.map(([x, y, z]) => [x, z + 8, -y]);
}

function suction(P) {
  const S = SUCTION;
  cyl(P('suction_nozzle', 'body', 'paint'), 'z', [0, Y], S.nozzle[0], S.nozzle[1], S.nozzle[2], 24, [false, false]);
  lathe(P('suction_flange', 'body', 'paintD'), 'z', [0, Y], [[S.flange[0], S.flange[2] - 0.15], [S.flange[0] + 0.12, S.flange[2]], [S.flange[1] - 0.12, S.flange[2]], [S.flange[1], S.flange[2] - 0.15]], 32);
  const sb = P('suction_bolts', 'body', 'bolt');
  for (let k = 0; k < 4; k++) { const a = (45 + 90 * k) * D2R; boltZ(sb, 2.4 * Math.cos(a), Y + 2.4 * Math.sin(a), 0.3, S.flange[0] - 0.25, S.flange[0]); }
  const pipe = P('suction_pipe', 'body', 'pipe');
  cyl(pipe, 'z', [0, Y], S.elbowZ, S.flange[0], S.r, 24, [false, false]);
  // grooved coupling at the bank edge: a ring with two bolted lugs at the sides
  const cp = P('coupling', 'body', 'paintD'), [k0, k1, kr] = S.coupling;
  lathe(cp, 'z', [0, Y], [[k0, S.r], [k0, kr - 0.2], [k0 + 0.2, kr], [k1 - 0.2, kr], [k1, kr - 0.2], [k1, S.r]], 32, [false, false]);
  for (const s of [-1, 1]) slab(cp, 'x', [s * (kr - 0.3), Y - 0.45, k0 + 0.25], [s * (kr + 0.75), Y + 0.45, k1 - 0.25], 0.08);
  for (const s of [-1, 1]) boltY(sb, s * (kr + 0.32), (k0 + k1) / 2, 0.3, Y + 0.45, Y + 0.72);
  // saddle on the front cross member, a strap round the pipe
  const sd = P('saddle', 'body', 'skid'), [s0, s1] = S.saddle;
  slab(sd, 'y', [-1.5, -6.6, s0], [1.5, Y - S.r + 0.05, s1], 0.08);
  lathe(P('strap', 'body', 'pipe'), 'z', [0, Y], [[s0 + 0.1, S.r], [s0 + 0.1, S.r + 0.32], [s1 - 0.1, S.r + 0.32], [s1 - 0.1, S.r]], 32, [false, false]);
  // 90 degree elbow at the front cell's centre: from along -Z to straight down
  const R = S.bend, ez = S.elbowZ, rings = [];
  for (let i = 0; i <= 12; i++) { const th = Math.PI / 2 * i / 12, c = Math.cos(th), s = Math.sin(th), p = [0, Y - R + R * c, ez - R * s];
    rings.push(Array.from({length: 24}, (_, k) => { const a = 2 * Math.PI * (k + 0.5) / 24, u = Math.cos(a) * S.r, v = Math.sin(a) * S.r; return [p[0] + u, p[1] - v * c, p[2] + v * s]; })); }
  skin(P('elbow', 'body', 'pipe'), rings, (ctr, r) => { const th = Math.PI / 2 * (r + 0.5) / 12; return sub(ctr, [0, Y - R + R * Math.cos(th), ez - R * Math.sin(th)]); });
  const pz = ez - R, py = Y - R;
  lathe(P('drop_flange', 'body', 'paintD'), 'y', [0, pz], [[py - 0.5, 2.65], [py - 0.38, 2.8], [py - 0.12, 2.8], [py, 2.65]], 32);
  for (let k = 0; k < 4; k++) { const a = (45 + 90 * k) * D2R; boltY(sb, 2.4 * Math.cos(a), pz + 2.4 * Math.sin(a), 0.3, py, py + 0.25); }
  cyl(HEAT ? P('suction_drop', 'body', 'ceramic') : pipe, 'y', [0, pz], S.drop, py - 0.5, HEAT ? S.r + 0.1 : S.r, 24, [false, false]);
  // strainer: a flange, a dark perforated basket with steel bands, a bottom cap
  const [b0, b1, br] = S.strainer;
  lathe(P('strainer_flange', 'body', 'paintD'), 'y', [0, pz], [[b1, 2.65], [b1 + 0.12, 2.8], [S.drop - 0.12, 2.8], [S.drop, 2.65]], 32);
  cyl(P('strainer', 'body', HEAT ? 'ceramic' : 'grille'), 'y', [0, pz], b0 + 0.3, b1, br - 0.12, 24, [false, false]);
  const bands = P('strainer_bands', 'body', 'pipe');
  for (let y = b0 + 0.3; y < b1 - 0.2; y += 0.75) lathe(bands, 'y', [0, pz], [[y, br - 0.1], [y, br], [y + 0.32, br], [y + 0.32, br - 0.1]], 24, [false, false]);
  for (let k = 0; k < 8; k++) { const a = (k * 45 + 22.5) * D2R; slab(bands, 'y', [br * 0.97 * Math.cos(a) - 0.16, b0 + 0.3, pz + br * 0.97 * Math.sin(a) - 0.16], [br * 0.97 * Math.cos(a) + 0.16, b1, pz + br * 0.97 * Math.sin(a) + 0.16], 0); }
  lathe(P('strainer_cap', 'body', 'pipe'), 'y', [0, pz], [[b0, br - 0.25], [b0 + 0.08, br], [b0 + 0.3, br]], 24, [true, true]);
}

function motor(P) {
  const M = MOTOR;
  cyl(P('bracket', 'body', 'paintD'), 'z', [0, Y], M.bracket[0], M.bracket[1], M.bracket[2], 24, [false, false]);
  lathe(P('motor_flange', 'body', 'paint'), 'z', [0, Y], [[M.flange[0], M.flange[2] - 0.15], [M.flange[0] + 0.12, M.flange[2]], [M.flange[1] - 0.12, M.flange[2]], [M.flange[1], M.flange[2] - 0.15]], 32);
  cyl(P('motor', 'body', 'paint'), 'z', [0, Y], M.body[0], M.body[1], M.body[2], 32, [false, true]);
  // axial cooling fins all round, except under the motor (its feet) and over it (the terminal box)
  const fins = P('fins', 'body', 'paint');
  for (let k = 0; k < 20; k++) {
    const deg = (k + 0.5) * 18; if ((deg > 235 && deg < 305) || (deg > 68 && deg < 112)) continue;
    const a = deg * D2R, c = Math.cos(a), s = Math.sin(a), w = 0.17, r0 = M.body[2] - 0.1, r1 = M.fins[2];
    const L = [[r0, -w], [r1, -w], [r1, w], [r0, w]].map(([r, t]) => [r * c - t * s, Y + r * s + t * c]);
    extrude(fins, 'z', shape(L), M.fins[0], M.fins[1], 0);
  }
  // fan cowl with a grille of rings at its back
  const [w0, w1, wr] = M.cowl, cowl = P('fan_cowl', 'body', 'cowl');
  lathe(cowl, 'z', [0, Y], [[w0, M.body[2]], [w0, wr - 0.15], [w0 + 0.15, wr], [w1 - 0.18, wr], [w1, wr - 0.18], [w1, wr - 0.45]], 32, [false, false]);
  const grille = P('grille', 'body', 'grille');
  cyl(grille, 'z', [0, Y], w1 - 0.3, w1 - 0.25, wr - 0.45, 32, [false, true]);
  const rings = P('grille_rings', 'body', 'cowl');
  for (const r of [0.75, 1.55, 2.35, 3.05]) extrude(rings, 'z', shape(circle(0, Y, r + 0.3, 32), [circle(0, Y, r, 32)]), w1 - 0.27, w1 - 0.02, 0);
  extrude(rings, 'z', shape(circle(0, Y, 0.45, 16)), w1 - 0.27, w1 - 0.02, 0);
  for (let k = 0; k < 4; k++) { const a = (45 + 90 * k) * D2R, c = Math.cos(a), s = Math.sin(a), w = 0.16;   // spokes
    extrude(rings, 'z', shape([[0.45, -w], [wr - 0.45, -w], [wr - 0.45, w], [0.45, w]].map(([r, t]) => [r * c - t * s, Y + r * s + t * c])), w1 - 0.26, w1 - 0.03, 0); }
  // feet bolted to the rear cross member
  const feet = P('motor_feet', 'body', 'paint');
  for (const s of [-1, 1]) slab(feet, 'y', [s * 1.4, -6.6, 3.6], [s * 3.2, Y - 2.4, 5.8], 0.1);
  const nuts = P('motor_feet_bolts', 'body', 'bolt');
  for (const s of [-1, 1]) for (const z of [4.1, 5.3]) boltY(nuts, s * 2.75, z, 0.3, Y - 2.4, Y - 2.15);
  // terminal box on top, a cable gland on its right, a conduit across to the starter box
  const tb = P('terminal_box', 'body', 'paint');
  slab(tb, 'y', [-1.6, Y + 3.15, 3.4], [1.6, Y + 5.0, 5.8], 0.15);
  slab(tb, 'y', [-1.75, Y + 5.0, 3.25], [1.75, Y + 5.35, 5.95], 0.1);
  const cond = P('conduit', 'body', 'cowl');
  cyl(cond, 'x', [Y + 4.1, 4.6], 1.6, 2.3, 0.62, 6);
  cyl(cond, 'x', [Y + 4.1, 4.6], 2.3, STARTER.x[0] + 0.02, 0.42, 12, [false, false]);
  cyl(cond, 'x', [Y + 4.1, 4.6], STARTER.x[0] - 0.55, STARTER.x[0], 0.62, 6);
}

function starter(P) {
  const S = STARTER, box = P('starter_box', 'body', 'box');
  slab(box, 'x', [S.x[0], S.y[0], S.z[0]], [S.x[1], S.y[1], S.z[1]], 0.18);
  slab(box, 'y', [S.x[0] - 0.2, S.y[1], S.z[0] - 0.2], [S.x[1] + 0.2, S.y[1] + 0.45, S.z[1] + 0.2], 0.1);   // rain hood
  // the AFL power port at the +X face centre: built facing +Z on the box face, turned to face +X
  const port = {plate: P('pport_plate', 'body', 'pportPlate'), socket: P('pport_socket', 'body', 'pportSocket'), pin: P('pport_pin', 'body', 'pportPin')};
  addPowerPort(port, 0, 0, S.x[1], 8);
  for (const p of Object.values(port)) p.v = p.v.map(([x, y, z]) => [z, y, -x]);
  // rotary isolator: yellow escutcheon, red knob hub (the handle bar is the switch model); lamp: black bezel
  const [sy, sz] = S.switch, x1 = S.x[1];
  slab(P('escutcheon', 'body', 'yellow'), 'x', [x1 - 0.02, sy - 1.3, sz - 1.3], [x1 + 0.28, sy + 1.3, sz + 1.3], 0.08);
  lathe(P('knob', 'body', 'red'), 'x', [sy, sz], [[x1 + 0.28, 0.95], [x1 + 0.62, 0.95], [x1 + 0.72, 0.82]], 24, [false, true]);
  const [ly, lz] = S.lamp;
  lathe(P('lamp_bezel', 'body', 'cowl'), 'x', [ly, lz], [[x1 - 0.02, 0.88], [x1 + 0.3, 0.88], [x1 + 0.36, 0.8], [x1 + 0.36, 0.62]], 24, [false, false]);
}
function switchHandle(P, on) {   // the isolator's handle bar on the knob: vertical = on, horizontal = off
  const [sy, sz] = STARTER.switch, x = STARTER.x[1] + 0.72, h = 1.25, w = 0.36;
  const part = P('handle', on ? 'switch_on' : 'switch_off', 'red');
  if (on) slab(part, 'x', [x - 0.02, sy - h, sz - w], [x + 0.42, sy + h, sz + w], 0.1);
  else slab(part, 'x', [x - 0.02, sy - w, sz - h], [x + 0.42, sy + w, sz + h], 0.1);
}
function lampLens(P, bone, mat) {
  const [ly, lz] = STARTER.lamp, x = STARTER.x[1] + 0.3;
  lathe(P('lens', bone, mat), 'x', [ly, lz], [[x, 0.62], [x + 0.16, 0.62], [x + 0.26, 0.5], [x + 0.32, 0.3], [x + 0.34, 0]], 16, [true, false]);
}

export const PIECES = {
  body: P => { skidAndCasing(P); discharge(P); suction(P); motor(P); starter(P); },
  switch_on: P => switchHandle(P, true), switch_off: P => switchHandle(P, false),
  lamp_off: P => lampLens(P, 'lamp_off', 'lens'), lamp_idle: P => lampLens(P, 'lamp_idle', 'lensIdle'), lamp_run: P => lampLens(P, 'lamp_run', 'lensRun'),
};

// ---------------- bake: UV, LabPBR maps ----------------
const DUMMY = 'data:image/png;base64,' + png(Buffer.from([0, 0, 0, 255]), 1, 1).toString('base64');
function bake() {
  const PARTS = [];
  for (const build of Object.values(PIECES)) build((name, bone, mat) => { const p = new Part(`${bone}_${name}`, bone, mat); PARTS.push(p); return p; });
  const live = PARTS.filter(p => p.f.length);
  for (const p of live) {   // Forge draws a quad as 0-1-2 / 2-3-0: split anything that is not a triangle or a strictly convex quad
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
    sourceGroups: () => [], refTexture: {source: DUMMY}, refUvWidth: 1, background: {c: [46, 86, 60, 255], s: [118, 24, 0, 255], n: [128, 128, 255, 255]}});
  const maps = painted.PNG.map((buf, i) => {
    if (i !== 1) return buf;
    const img = readPng(buf), px = Buffer.from(img.px);
    for (const is of UV.islands.filter(is => GLOW.has(is.part.mat)))   // LabPBR emission: _s alpha 254 = full
      for (let y = is.py - 2; y < is.py + is.H + 2; y++) for (let x = is.px - 2; x < is.px + is.W + 2; x++) px[(y * img.w + x) * img.bpp + 3] = 254;
    return png(px, img.w, img.h);
  });
  const groups = [['body', 'switch_on', 'lamp_off'], ['body', 'switch_off', 'lamp_run']];
  const coplanar = groups.flatMap(g => zFightLevels(live.filter(p => g.includes(p.bone)), new Map()).unresolved);
  return {id: ID, PARTS: live, atlas, UV, maps, coplanar};
}

const uuidOf = (ns, s) => { const h = createHash('sha256').update(`${HEAT ? 'afl-heat-resistant-intake-pump' : 'afl-intake-pump-v1'}:${ns}:${s}`).digest('hex'); return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-8${h.slice(17, 20)}-${h.slice(20, 32)}`; };
const r12 = v => +v.toFixed(12) || 0;
const HIDDEN = new Set(['switch_on', 'lamp_idle', 'lamp_run']);   // the source shows the item's state: off, lamp dark
function sourceOf(b) {   // editable Free Model source (frame as the header, px): one group per piece
  const uuid = s => uuidOf(b.id, s), name = b.id + '_v1', bones = [...new Set(b.PARTS.map(p => p.bone))];
  const elements = b.PARTS.map(p => {
    const key = i => i.toString(36).padStart(4, '0'), vertices = {}, faces = {}, uvs = b.UV.faceUV.get(p);
    p.v.forEach((q, i) => { vertices[key(i)] = q.map(r12); });
    p.f.forEach((f, fi) => { const uv = uvs.get(f); faces['f' + key(fi)] = {uv: Object.fromEntries(f.ids.map((id, j) => [key(id), uv[j].map(r12)])), vertices: f.ids.map(key), texture: 0}; });
    return {name: p.name, color: 0, origin: [0, 0, 0], rotation: [0, 0, 0], shading: 'flat', export: true, visibility: !HIDDEN.has(p.bone), locked: false,
      render_order: 'default', scope: 0, allow_mirror_modeling: true, vertices, faces, type: 'mesh', uuid: uuid('mesh:' + p.name)};
  });
  return {meta: {format_version: '5.0', model_format: 'free', box_uv: false}, name, model_identifier: '', visible_box: [1, 2, 0],
    variable_placeholders: '', variable_placeholder_buttons: [], timeline_setups: [], unhandled_root_fields: {}, resolution: {width: b.atlas, height: b.atlas},
    elements, groups: bones.map(bone => ({name: bone, uuid: uuid('group:' + bone), export: true, locked: false, scope: 0,
      selected: false, visibility: !HIDDEN.has(bone), _static: {properties: {}, temp_data: {}}, origin: [0, 0, 0], rotation: [0, 0, 0],
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
const SMOOTH = Math.cos(36 * D2R);   // the 24- and 32-sided round parts shade round; boxes, fins and flange edges stay crisp
function cornerNormals(V, F) {
  const fn = F.map(f => newell(f.ids.map(i => V[i]))), byV = new Map();
  F.forEach((f, k) => f.ids.forEach(i => (byV.get(i) || byV.set(i, []).get(i)).push(k)));
  return F.map((f, k) => { const n0 = norm(fn[k]); return f.ids.map(i => norm(byV.get(i).reduce((a, j) => dot(norm(fn[j]), n0) >= SMOOTH ? add(a, fn[j]) : a, [0, 0, 0]))); });
}
const toCell = q => q.map(v => v / 16 + 0.5);
function objOf(b, title, file, bones) {
  const out = [`# AFL ${title}, generated by tools/build-intake-pump-v1.mjs`, `mtllib ${file.split('/').pop()}.mtl`];
  let vBase = 1, tBase = 1, nBase = 1;
  for (const p of b.PARTS) {
    if (!bones.has(p.bone)) continue;
    const V = p.v.map(toCell), F = p.f;
    out.push(`o ${p.name}`, `usemtl ${b.id}`);
    for (const q of V) out.push(`v ${f6(q[0])} ${f6(q[1])} ${f6(q[2])}`);
    const uvs = b.UV.faceUV.get(p), vt = [], vn = [], fl = [], cn = cornerNormals(V, F), nIndex = new Map();
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
const mtlOf = (b, title, glow) => `# AFL ${title}\nnewmtl ${b.id}\nKd 1 1 1\n${glow ? 'Ka 1 1 1\n' : ''}map_Kd apocalypse_firstlight:block/${b.id}\n`;
const objModel = file => ({loader: 'forge:obj', model: `apocalypse_firstlight:models/block/${file}.obj`, automatic_culling: false,
  flip_v: true, shade_quads: true, ambientocclusion: false, textures: {particle: `apocalypse_firstlight:block/${ID}`}});
const r3 = v => +v.toFixed(3) || 0;
const S3 = v => [v, v, v];
// GUI framing: centre the model's projection under the GUI rotation, scaled to fit 15 px (block-model space)
function guiFit(points, rotation) {
  const [ax, ay] = rotation.map(v => v * D2R);
  const rot = q => { const x = q[0] * Math.cos(ay) + q[2] * Math.sin(ay), z = -q[0] * Math.sin(ay) + q[2] * Math.cos(ay); return [x, q[1] * Math.cos(ax) - z * Math.sin(ax)]; };
  const pts = points.map(q => rot([q[0] - 8, q[1] - 8, q[2] - 8]));
  const xs = pts.map(q => q[0]), ys = pts.map(q => q[1]), ext = Math.max(Math.max(...xs) - Math.min(...xs), Math.max(...ys) - Math.min(...ys));
  const scale = r3(15 / ext), cx = (Math.max(...xs) + Math.min(...xs)) / 2, cy = (Math.max(...ys) + Math.min(...ys)) / 2;
  return {rotation, translation: [r3(-scale * cx), r3(-scale * cy), 0], scale: S3(scale)};
}
function itemDisplay(b, bones) {
  const pts = b.PARTS.filter(p => bones.has(p.bone)).flatMap(p => p.v.map(q => toCell(q).map(v => v * 16)));
  const gui = guiFit(pts, [30, 225, 0]), s = gui.scale[0];
  return {gui, ground: {translation: [0, 2, 0], scale: S3(r3(s * 0.6))}, fixed: {rotation: [0, 90, 0], scale: S3(r3(s * 0.9))},
    thirdperson_righthand: {rotation: [75, 45, 0], translation: [0, 2.5, 0], scale: S3(r3(s * 0.6))}, thirdperson_lefthand: {rotation: [75, 45, 0], translation: [0, 2.5, 0], scale: S3(r3(s * 0.6))},
    firstperson_righthand: {rotation: [0, 45, 0], scale: S3(r3(s * 0.7))}, firstperson_lefthand: {rotation: [0, 225, 0], scale: S3(r3(s * 0.7))}};
}

// ---------------- blockstate ----------------
const DIRS = ['north', 'east', 'south', 'west'], ROT = {north: 0, east: 90, south: 180, west: 270};
const ref = (model, facing) => ({model: `apocalypse_firstlight:block/${ID}/${model}`, ...(ROT[facing] ? {y: ROT[facing]} : {})});
function blockstate() {   // IntakePumpBlock: facing, part (bank | front), on, lamp (off | idle | run); the bank cell draws it all
  const parts = [];
  for (const F of DIRS) {
    parts.push({when: {facing: F, part: 'bank'}, apply: ref('body', F)});
    for (const on of ['true', 'false']) parts.push({when: {facing: F, part: 'bank', on}, apply: ref(on === 'true' ? 'switch_on' : 'switch_off', F)});
    for (const lamp of ['off', 'idle', 'run']) parts.push({when: {facing: F, part: 'bank', lamp}, apply: ref('lamp_' + lamp, F)});
  }
  parts.push({when: {part: 'front'}, apply: {model: `apocalypse_firstlight:block/${ID}/cell`}});
  return {multipart: parts};
}

// ---------------- write ----------------
const B = bake();
const bbDir = path.join(ROOT, 'src/main/blockbench'), assets = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight');
const json = v => JSON.stringify(v, null, 2) + '\n';
const outputs = [], objs = [];
const T = HEAT ? 'Heat-Resistant Intake Pump' : 'Intake Pump V1';
const only = (...bones) => new Set(bones), ITEM = only('body', 'switch_off', 'lamp_off');
const model = (file, title, bones, glow = false) => {
  const obj = objOf(B, title, file, bones);
  outputs.push([path.join(assets, `models/block/${file}.obj`), obj], [path.join(assets, `models/block/${file}.mtl`), mtlOf(B, title, glow)],
    [path.join(assets, `models/block/${file}.json`), json(objModel(file))]);
  objs.push([file, obj]);
};
outputs.push([path.join(bbDir, `${ID}_v1.bbmodel`), JSON.stringify(sourceOf(B))],
  ...['', '_s', '_n'].flatMap((k, i) => [[path.join(bbDir, `textures/${ID}_v1${k}.png`), B.maps[i]], [path.join(assets, `textures/block/${ID}${k}.png`), B.maps[i]]]));
model(`${ID}/body`, T + ' body', only('body'));
model(`${ID}/switch_on`, T + ' isolator handle (on)', only('switch_on'));
model(`${ID}/switch_off`, T + ' isolator handle (off)', only('switch_off'));
model(`${ID}/lamp_off`, T + ' status lamp (dark)', only('lamp_off'));
model(`${ID}/lamp_idle`, T + ' status lamp (amber: on, not pumping)', only('lamp_idle'), true);
model(`${ID}/lamp_run`, T + ' status lamp (green: pumping)', only('lamp_run'), true);
model(`${ID}/item`, T + ' item', ITEM);
outputs.push([path.join(assets, `models/block/${ID}/cell.json`), json({textures: {particle: `apocalypse_firstlight:block/${ID}`}})]);
outputs.push([path.join(assets, `models/item/${ID}.json`), json({parent: `apocalypse_firstlight:block/${ID}/item`, gui_light: 'side', display: itemDisplay(B, ITEM)})]);
outputs.push([path.join(assets, `blockstates/${ID}.json`), json(blockstate())]);

const tris = bones => B.PARTS.filter(p => bones.has(p.bone)).reduce((s, p) => s + p.f.reduce((t, f) => t + f.ids.length - 2, 0), 0);
const boundsOf = bones => { const all = B.PARTS.filter(p => bones.has(p.bone)).flatMap(p => p.v); return [0, 1, 2].map(k => [r3(Math.min(...all.map(q => q[k]))), r3(Math.max(...all.map(q => q[k])))]); };
export const stats = {triangles: {body: tris(only('body')), switch: tris(only('switch_on')), lamp: tris(only('lamp_run'))}, bounds: boundsOf(only('body')),
  atlas: {size: B.atlas, texelsPerPx: B.UV.S, islands: B.UV.islands.length, coplanar: B.coplanar.length}, itemGui: itemDisplay(B, ITEM).gui,
  anchors: {switch: [r3(STARTER.x[1] + 0.9), STARTER.switch[0], STARTER.switch[1]], lamp: [r3(STARTER.x[1] + 0.6), ...STARTER.lamp]}};
const isMain = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  console.log(JSON.stringify(stats));
  if (B.coplanar.length) console.log('COPLANAR', JSON.stringify([...new Set(B.coplanar.map(c => c.a + ' | ' + c.b))].slice(0, 12)));
  const pi = process.argv.indexOf('--preview');
  if (pi > 0) {
    const dir = process.argv[pi + 1]; fs.mkdirSync(dir, {recursive: true});
    for (const [file, obj] of objs) fs.writeFileSync(path.join(dir, file.replace('/', '_') + '.obj'), obj);
    ['', '_s', '_n'].forEach((k, i) => fs.writeFileSync(path.join(dir, `${B.id}${k}.png`), B.maps[i]));
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
