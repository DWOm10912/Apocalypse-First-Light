// Silverwood 12 V3: side-by-side (SxS) boxlock double-barrel shotgun, Pure Mesh + 1024 LabPBR atlas + AFL Native Rig in
// its idle pose. Model only: every clip is a static placeholder (the gun never opens yet); animations come later.
//   node tools/build-silverwood-12-v3.mjs            -> writes source, runtime geo / sidecar / maps / animation
//   node tools/build-silverwood-12-v3.mjs --check    -> verifies every output is up to date
//   node tools/build-silverwood-12-v3.mjs --debug <out.bbmodel>  -> writes the source only (previews; reference arms exported)
// Design reference (proportions, layout, material split only; no geometry or texture taken from it): a Russian-style
// hammerless boxlock SxS the user supplied in Blender. Real scale, 1 unit ~ 22 mm: overall ~46 (1.0 m), 22" barrels
// (25.5), 12-bore (bore 0.84 = 18.5 mm), barrels converging toward the muzzle, flat top rib with a brass bead, splinter
// forend with a forend iron, half-pistol-grip walnut stock with a ribbed rubber pad, top lever with a right-hand thumb
// piece, tang safety, twin triggers, extractor between the chambers.
// Frame (Blockbench source): muzzle -Z, up +Y, +X = the shooter's right (the right barrel fires first). The top rib plane
// (bead top y 9.88) matches the existing ADS aim [0, 9.88, 3.5], so the ADS / hip data of the gun keeps working.
// Material split follows the reference: blued steel (barrels, monoblock, receiver, trigger guard), polished steel (top
// lever, triggers, safety, extractor), walnut (stock, forend), rubber (butt pad), brass (bead). Large steel surfaces are
// coatings in LabPBR terms (F0 ~24) with bare-metal worn edges, per the AFL black-steel rule for shader packs.
import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {convert, serializeCompact} from './export-afl-mesh.mjs';
import {Part, extrude, add, sub, mul, dot, cross, norm, newell, area2, unwrap, paint, png, zFightLevels} from './cube-slab-mesh-lib.mjs';
import {hash, mix} from './lathe-mesh-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
function assert(c, m) { if (!c) throw new Error(m); }
const PARTS = [], P = (name, bone, mat) => { const p = new Part(name, bone, mat); PARTS.push(p); return p; };

// ---------------- dimensions ----------------
export const BZ = 4.0, MZ = -21.5, RIB_Y = 9.80;                       // breech face, muzzle, top rib plane
export const BARREL = {breech: [0.72, 9.0, BZ], muzzle: [0.52, 9.18, MZ]};   // right barrel axis (left mirrored)
export const PIN = [0, 7.52, 1.20], PIN_R = 0.22;                     // hinge pin (x across)
const OUTER = [[3.02, 0.70], [5.0, 0.665], [9.0, 0.605], [15.0, 0.545], [21.0, 0.515], [25.43, 0.50]];   // barrel outer (s, r)
const BORE = 0.42, CHAMBER = 0.47, MUZZLE_DEPTH = 2.6;                 // visible bore depth behind the muzzle

// ---------------- builders ----------------
// generic lathe: closed (s, r[, mat]) profile revolved about the axis origin + s * w; r = 0 points collapse onto the axis
function lathe(part, origin, w, profile, seg, phase = 0) {
  if (area2(profile.map(p => [p[0], p[1]])) < 0) profile = profile.slice().reverse().map((p, i, a) => [p[0], p[1], a[(i + a.length - 1) % a.length][2]]);
  const up = Math.abs(w[1]) < 0.9 ? [0, 1, 0] : [1, 0, 0], u = norm(cross(up, w)), v = cross(w, u);
  const at = (s, r, a) => add(origin, add(mul(w, s), add(mul(u, r * Math.cos(a)), mul(v, r * Math.sin(a)))));
  const ang = i => phase + 2 * Math.PI * i / seg;
  const ring = ([s, r]) => r < 1e-6 ? null : Array.from({length: seg}, (_, i) => part.vtx(at(s, r, ang(i))));
  const rings = profile.map(ring), axis = profile.map(([s, r]) => r < 1e-6 ? part.vtx(at(s, 0, 0)) : null);
  for (let k = 0; k < profile.length; k++) {
    const k2 = (k + 1) % profile.length, [s0, r0] = profile[k], [s1, r1, mat] = profile[k2];
    if (r0 < 1e-6 && r1 < 1e-6) continue;
    const e = [s1 - s0, r1 - r0], o = [e[1], -e[0]];
    for (let i = 0; i < seg; i++) {
      const j = (i + 1) % seg, a = ang(i + 0.5), n3 = add(mul(w, o[0]), add(mul(u, o[1] * Math.cos(a)), mul(v, o[1] * Math.sin(a))));
      const tag = Math.abs(o[0]) > 0.9 * Math.hypot(...o) ? 'cap' : 'side', m = mat || part.mat;
      if (r0 < 1e-6) part.face([axis[k], rings[k2][j], rings[k2][i]], n3, tag, m);
      else if (r1 < 1e-6) part.face([rings[k][i], rings[k][j], axis[k2]], n3, tag, m);
      else part.face([rings[k][i], rings[k][j], rings[k2][j], rings[k2][i]], n3, tag, m);
    }
  }
}
// loft through rings (equal point counts, consistent order), capped; outward = away from each ring's centroid
function loft(part, rings, {caps = [true, true], mat} = {}) {
  const ids = rings.map(r => r.map(p => part.vtx(p))), cen = rings.map(r => mul(r.reduce((a, p) => add(a, p), [0, 0, 0]), 1 / r.length));
  for (let k = 0; k + 1 < rings.length; k++) for (let i = 0; i < rings[k].length; i++) {
    const j = (i + 1) % rings[k].length, q = [ids[k][i], ids[k][j], ids[k + 1][j], ids[k + 1][i]];
    const c = mul(q.reduce((a, id) => add(a, part.v[id]), [0, 0, 0]), 0.25), mid = mul(add(cen[k], cen[k + 1]), 0.5);
    part.face(q, sub(c, mid), 'side', mat || part.mat);
  }
  for (const [end, k, s] of [[0, 0, -1], [1, rings.length - 1, 1]]) {
    if (!caps[end]) continue;
    const dir = norm(sub(cen[Math.min(rings.length - 1, 1)], cen[0])), hint = mul(k === 0 ? dir : norm(sub(cen[k], cen[k - 1])), k === 0 ? -1 : 1);
    const c = part.vtx(cen[k]);
    for (let i = 0; i < ids[k].length; i++) part.face([c, ids[k][i], ids[k][(i + 1) % ids[k].length]], hint, 'cap', mat || part.mat);
  }
}
// rectangle swept along a path in the x = 0 plane ((z, y) points), width w across x, thickness t in the path plane
function sweep(part, path, w, t) {
  const rings = path.map(([z, y], i) => {
    const a = path[Math.max(0, i - 1)], b = path[Math.min(path.length - 1, i + 1)], T = norm([b[0] - a[0], b[1] - a[1]]), N = [-T[1], T[0]];
    return [[-w / 2, -t / 2], [w / 2, -t / 2], [w / 2, t / 2], [-w / 2, t / 2]].map(([x, d]) => [x, y + N[1] * d, z + N[0] * d]);
  });
  loft(part, rings);
}
const circle = (cx, cy, r, n, cw = false, a0 = 0) => Array.from({length: n}, (_, i) => { const a = a0 + (cw ? -1 : 1) * 2 * Math.PI * i / n; return [cx + r * Math.cos(a), cy + r * Math.sin(a)]; });
// superellipse ring in the plane z = zc: half widths hw (x) / hh (y) around y = yc, exponent p (2 = ellipse)
const superRing = (z, yc, hw, hh, n, p = 2.5) => Array.from({length: n}, (_, i) => { const a = 2 * Math.PI * i / n + Math.PI / n, c = Math.cos(a), s = Math.sin(a);
  return [hw * Math.sign(c) * Math.abs(c) ** (2 / p), yc + hh * Math.sign(s) * Math.abs(s) ** (2 / p), z]; });

// ---------------- barrels ----------------
const axisOf = side => { const b = [side * BARREL.breech[0], BARREL.breech[1], BARREL.breech[2]], m = [side * BARREL.muzzle[0], BARREL.muzzle[1], BARREL.muzzle[2]];
  return {o: b, w: norm(sub(m, b)), L: Math.hypot(...sub(m, b))}; };
for (const [side, name] of [[1, 'barrel_right'], [-1, 'barrel_left']]) {
  const {o, w, L} = axisOf(side), part = P(name, 'barrels', 'blued');
  lathe(part, o, w, [
    [3.02, CHAMBER, 'bore'], [3.02, OUTER[0][1], 'blued'], ...OUTER.slice(1).map(([s, r]) => [s, r, 'blued']), [L - 0.07, 0.50, 'blued'], [L, 0.47, 'blued'],
    [L, BORE, 'blued'], [L - MUZZLE_DEPTH, BORE, 'bore'], [L - MUZZLE_DEPTH, 0, 'bore'], [5.6, 0], [5.6, BORE, 'bore'], [3.6, BORE, 'bore'], [3.25, CHAMBER, 'bore']], 24);
}
// monoblock (breech block): stadium around both chambers, chambers open, from the tube joint to the breech face
{
  const part = P('monoblock', 'barrels', 'blued'), r = 0.74, [cx, cy] = BARREL.breech;
  const outer = [...Array.from({length: 13}, (_, i) => { const a = -Math.PI / 2 + Math.PI * i / 12; return [cx + r * Math.cos(a), cy + r * Math.sin(a)]; }),
    ...Array.from({length: 13}, (_, i) => { const a = Math.PI / 2 + Math.PI * i / 12; return [-cx + r * Math.cos(a), cy + r * Math.sin(a)]; })];
  extrude(part, 'z', {outer, holes: [circle(cx, cy, CHAMBER, 24, true), circle(-cx, cy, CHAMBER, 24, true)]}, 0.98, BZ, 0.04);
}
// lumps with the hinge hook (seats on the pin from above and behind)
{
  const part = P('lumps', 'barrels', 'blued'), [, py, pz] = PIN, rh = PIN_R + 0.02;
  const hook = [100, 70, 40, 10, -20, -45].map(d => [pz + rh * Math.cos(d * Math.PI / 180), py + rh * Math.sin(d * Math.PI / 180)]);
  extrude(part, 'x', {outer: [[3.6, 7.35], [3.9, 7.55], [3.9, 8.32], [1.16, 8.32], [1.16, hook[0][1]], ...hook, [1.9, 7.35]], holes: []}, -0.42, 0.42, 0.03);
}
// ribs: flat top rib (matte, sight plane) and the lower joining rib
{
  const top = P('top_rib', 'barrels', 'rib');
  const ribRing = (z, hw, y0, y1) => [[-hw, y0, z], [hw, y0, z], [hw, y1 - 0.03, z], [hw - 0.03, y1, z], [-hw + 0.03, y1, z], [-hw, y1 - 0.03, z]];
  loft(top, [[3.95, 0.17], [0.98, 0.16], [-8, 0.135], [-21.35, 0.11]].map(([z, hw]) => ribRing(z, hw, 9.25, RIB_Y)));
  const low = P('bottom_rib', 'barrels', 'blued');
  const yOf = z => 8.40 + (8.62 - 8.40) * (0.98 - z) / (0.98 - MZ);
  loft(low, [0.98, -8, -21.35].map(z => ribRing(z, 0.14, yOf(z), yOf(z) + 0.36)));
}
// brass bead on the rib (top at y 9.885 = the ADS aim height)
lathe(P('bead', 'barrels', 'brass'), [0, RIB_Y, -21.2], [0, 1, 0], [[0, 0], [0, 0.085], [0.04, 0.078], [0.07, 0.052], [0.085, 0]], 10);
// extractor plate between the chambers at the top of the breech face (slides back when the gun opens)
extrude(P('extractor', 'extractor', 'polished'), 'z', {outer: [[-0.22, 9.30], [0.22, 9.30], [0.22, 9.76], [-0.22, 9.76]], holes: []}, 3.70, BZ + 0.01, 0.02);

// ---------------- forend (walnut) + forend iron ----------------
const FOREND = [[0.30, 7.40, 1.28], [-1.0, 7.40, 1.26], [-3.5, 7.48, 1.20], [-6.0, 7.60, 1.10], [-7.1, 7.72, 1.00], [-7.45, 7.95, 0.85]];   // z, bottom y, half width
const FOREND_TOP = 8.75;   // tucked against the barrels' flanks
const forendRing = (z, yb, hw, n = 16) => { const hh = FOREND_TOP - yb;
  return Array.from({length: n}, (_, i) => { const a = Math.PI + Math.PI * i / (n - 1), c = Math.cos(a), s = Math.sin(a);
    return [hw * Math.sign(c) * Math.abs(c) ** 0.8, FOREND_TOP + hh * Math.sign(s) * Math.abs(s) ** 0.8, z]; }); };
loft(P('forend_wood', 'forend', 'wood'), FOREND.slice().reverse().map(([z, yb, hw]) => forendRing(z, yb, hw)));
loft(P('forend_iron', 'forend', 'blued'), [[0.28, 7.39, 1.29], [0.88, 7.44, 1.25]].map(([z, yb, hw]) => forendRing(z, yb, hw)));

// ---------------- receiver ----------------
{
  const body = P('action_body', 'receiver', 'receiver');
  extrude(body, 'x', {outer: [[4.0, 6.90], [9.0, 6.90], [9.0, 9.55], [4.35, 9.55], [4.12, 9.50], [4.02, 9.38], [4.0, 9.30]], holes: []}, -1.45, 1.45, 0.05);
  extrude(P('action_bar', 'receiver', 'receiver'), 'x', {outer: [[1.30, 6.90], [4.05, 6.90], [4.05, 7.30], [1.0, 7.30], [0.86, 7.24], [0.80, 7.10], [0.86, 6.97], [1.0, 6.91]], holes: []}, -1.45, 1.45, 0.04);
  const plate = P('side_plates', 'receiver', 'plate'), sp = [[5.25, 7.25], [8.55, 7.25], [8.70, 7.40], [8.70, 9.00], [8.55, 9.15], [5.25, 9.15], [5.10, 9.00], [5.10, 7.40]];
  extrude(plate, 'x', {outer: sp, holes: []}, 1.45, 1.49, 0);
  extrude(plate, 'x', {outer: sp, holes: []}, -1.49, -1.45, 0);
  lathe(P('hinge_pin', 'receiver', 'receiver'), [-1.47, PIN[1], PIN[2]], [1, 0, 0], [[0, 0], [0, PIN_R - 0.03], [0.03, PIN_R], [2.91, PIN_R], [2.94, PIN_R - 0.03], [2.94, 0]], 12);
  extrude(P('tang', 'receiver', 'receiver'), 'x', {outer: [[8.5, 9.45], [12.3, 9.14], [12.5, 9.22], [12.35, 9.31], [8.5, 9.62]], holes: []}, -0.42, 0.42, 0.03);
  extrude(P('safety', 'receiver', 'polished'), 'x', {outer: [[11.55, 9.37], [11.95, 9.33], [11.95, 9.44], [11.85, 9.48], [11.55, 9.50]], holes: []}, -0.14, 0.14, 0);
}
// top lever (pivot on the tang, thumb piece to the right)
extrude(P('top_lever', 'top_lever', 'polished'), 'y', {outer: [[9.15, 0.0], [9.24, -0.21], [9.45, -0.30], [9.70, -0.22], [10.3, 0.05], [10.85, 0.52], [11.15, 0.62], [11.27, 0.80],
  [11.17, 1.00], [10.95, 1.03], [10.75, 0.88], [10.2, 0.45], [9.70, 0.22], [9.45, 0.30], [9.24, 0.21]], holes: []}, 9.60, 9.76, 0.02);
// twin triggers (front = right barrel, rear = left barrel)
{
  const part = P('triggers', 'triggers', 'polished'), blade = [[6.95, 6.95], [6.92, 6.60], [7.02, 6.30], [7.25, 6.10], [7.38, 6.14], [7.18, 6.34], [7.12, 6.62], [7.20, 6.95]];
  for (const dz of [0, 1.05]) extrude(part, 'x', {outer: blade.map(([z, y]) => [z + dz, y]), holes: []}, -0.09, 0.09, 0);
}

// ---------------- stock (walnut) + butt pad ----------------
// z, top y, bottom y, half width: straight-hand wrist into a half pistol grip, drop at heel 2.9 below the rib plane
export const STOCK = [[8.95, 9.50, 6.90, 1.40], [9.6, 9.46, 6.78, 1.26], [10.5, 9.40, 6.42, 1.08], [11.4, 9.30, 6.10, 0.96], [12.2, 9.15, 5.78, 0.93],
  [13.0, 8.95, 5.28, 0.96], [13.8, 8.80, 4.75, 1.00], [14.8, 8.62, 4.25, 1.02], [16.5, 8.35, 3.55, 1.03], [18.5, 8.02, 2.85, 1.02], [20.5, 7.65, 2.20, 1.00],
  [22.5, 7.28, 1.62, 0.98], [24.2, 6.95, 1.10, 0.96]];
const stockBottom = z => { for (let i = 1; i < STOCK.length; i++) if (z <= STOCK[i][0]) { const [z0, , b0] = STOCK[i - 1], [z1, , b1] = STOCK[i]; return b0 + (b1 - b0) * (z - z0) / (z1 - z0); } return STOCK.at(-1)[2]; };
const stockRing = ([z, t, b, hw], k = 1) => superRing(z, (t + b) / 2, hw * k, (t - b) / 2 * k, 20, 2.6);
loft(P('stock', 'stock', 'wood'), STOCK.map(s => stockRing(s)));
{
  const e = STOCK.at(-1), at = (z, k) => stockRing([z, e[1], e[2], e[3]], k);
  loft(P('butt_pad', 'stock', 'rubber'), [at(24.18, 1.012), at(24.36, 1.012), at(24.40, 0.985), at(24.48, 0.985), at(24.52, 1.012), at(24.64, 1.012), at(24.68, 0.985),
    at(24.76, 0.985), at(24.80, 1.0), at(24.88, 0.97)]);
}
// trigger guard: loop from the receiver floor round the triggers, tang along the wrist
{
  const tang = [10.0, 10.6, 11.2, 11.8, 12.3].map(z => [z, stockBottom(z) + 0.02]);
  sweep(P('trigger_guard', 'receiver', 'receiver'), [[5.95, 6.95], [6.15, 6.55], [6.5, 6.08], [7.1, 5.80], [7.8, 5.74], [8.5, 5.82], [9.1, 6.06], [9.55, 6.38], ...tang], 0.40, 0.13);
}

// ---------------- rig (Blockbench source frame) ----------------
// Hands: the previous (accepted) Silverwood idle, evaluated in the Blockbench convention, moved onto the new grip and
// forend with the same orientation: right hand keeps its offset to the (front) trigger, which moves 2.38 forward and 0.32
// up; left hand keeps its drop under the forend and sits at the middle of the shorter forend.
const eulerZYX = R => { const ey = Math.asin(Math.max(-1, Math.min(1, -R[2][0]))); return [Math.atan2(R[2][1], R[2][2]), ey, Math.atan2(R[1][0], R[0][0])].map(a => +(a * 180 / Math.PI).toFixed(3)); };
export const HANDS = {
  right: {pos: [3.696, 6.785 + 0.32, 15.615 - 2.38], rot: eulerZYX([[0.0767, -0.1825, -0.9802], [0.3834, 0.9129, -0.1400], [0.9204, -0.3651, 0.1400]])},
  left: {pos: [-1.431, 4.006 + (7.48 - 6.50), -3.6], rot: eulerZYX([[-0.3324, 0.3748, 0.8654], [-0.8730, 0.2249, -0.4327], [-0.3568, -0.8994, 0.2525]])},
};
const [bx, by] = BARREL.breech, [mx, my] = BARREL.muzzle;
// name, parent, origin, rotation
export const RIG = [
  ['camera', null, [-4, 14, 33]],
  ['root', null, [0, 0, 0]],
  ['handling', 'root', [0, 7.1, 13.2]],
  ['gun_body', 'handling', [0, 0, 0]],
  ['receiver', 'gun_body', [0, 0, 0]],
  ['top_lever', 'receiver', [0, 9.60, 9.45]],
  ['triggers', 'receiver', [0, 6.90, 7.10]],
  ['stock', 'gun_body', [0, 0, 0]],
  ['barrels', 'gun_body', PIN.slice()],
  ['forend', 'barrels', PIN.slice()],
  ['extractor', 'barrels', [0, 9.53, BZ]],
  ['chamber_right', 'extractor', [bx, by, BZ]],
  ['chamber_left', 'extractor', [-bx, by, BZ]],
  ['right_chamber_fx', 'barrels', [bx, by, BZ + 0.2]],
  ['left_chamber_fx', 'barrels', [-bx, by, BZ + 0.2]],
  ['muzzle_right_anchor', 'barrels', [mx, my, MZ]],
  ['muzzle_left_anchor', 'barrels', [-mx, my, MZ]],
  ['righthand', 'handling', HANDS.right.pos],
  ['righthand_pos', 'righthand', HANDS.right.pos],
  ['right_hand_anchor', 'righthand_pos', HANDS.right.pos, HANDS.right.rot],
  ['lefthand', 'root', HANDS.left.pos],
  ['lefthand_pos', 'lefthand', HANDS.left.pos],
  ['left_hand_anchor', 'lefthand_pos', HANDS.left.pos, HANDS.left.rot],
];
for (const p of PARTS) assert(RIG.some(r => r[0] === p.bone), 'bone ' + p.bone);

// ---------------- materials, UV, paint ----------------
const vn3 = (x, y, z) => {   // smooth value noise in [0, 1]
  const xi = Math.floor(x), yi = Math.floor(y), zi = Math.floor(z), s = t => t * t * (3 - 2 * t), l = (a, b, t) => a + (b - a) * t;
  const sx = s(x - xi), sy = s(y - yi), sz = s(z - zi), h = (a, b, c) => hash(a * 7919 + c * 104729, b + 31 * c);
  return l(l(l(h(xi, yi, zi), h(xi + 1, yi, zi), sx), l(h(xi, yi + 1, zi), h(xi + 1, yi + 1, zi), sx), sy),
    l(l(h(xi, yi, zi + 1), h(xi + 1, yi, zi + 1), sx), l(h(xi, yi + 1, zi + 1), h(xi + 1, yi + 1, zi + 1), sx), sy), sz);
};
// walnut: grain along the length, gently wavy, figure a few units long; low frequency only (no per-pixel noise)
export const walnut = p => {
  const fig = vn3(p[0] * 0.8, p[1] * 0.8 + 0.4 * Math.sin(p[2] * 0.45), p[2] * 0.16);
  const lines = 0.5 + 0.5 * Math.sin(p[1] * 5.0 + p[0] * 1.2 + 3.2 * fig);
  return mix([72, 43, 25], [122, 79, 47], 0.30 * lines + 0.50 * fig + 0.08);
};
export const MATS = {   // Base Color (sRGB) or function, bevel highlight, smoothness open / edge, F0 (24 coating, 10 dielectric, 255 metal)
  blued:    {c: [31, 34, 39], hl: 20, sm: 150, se: 178, f0: 24, edgeF0: 255},
  receiver: {c: [35, 37, 41], hl: 22, sm: 142, se: 172, f0: 24, edgeF0: 255},
  plate:    {c: [43, 45, 49], hl: 20, sm: 150, se: 175, f0: 24, edgeF0: 255},
  rib:      {c: [26, 27, 30], hl: 10, sm: 70, se: 110, f0: 24, edgeF0: 255},
  polished: {c: [60, 62, 66], hl: 24, sm: 185, se: 200, f0: 255},
  brass:    {c: [182, 142, 72], hl: 20, sm: 150, se: 170, f0: 255},
  bore:     {c: [15, 15, 17], hl: 0, sm: 55, se: 55, f0: 24},
  wood:     {c: walnut, hl: 8, sm: 120, se: 132, f0: 10},
  rubber:   {c: [22, 22, 23], hl: 2, sm: 38, se: 42, f0: 10},
};
const ATLAS = 1024, PAD = 1;
const unwrapped = unwrap(PARTS, {atlas: ATLAS, pad: PAD, startS: 64, stepS: 0.5});
const DUMMY = 'data:image/png;base64,' + png(Buffer.from([0, 0, 0, 255]), 1, 1).toString('base64');
const painted = paint({PARTS, islands: unwrapped.islands, S: unwrapped.S, uvOf: unwrapped.uvOf, atlas: ATLAS, pad: PAD, MATS, ZONED: new Set(), groupInfo: new Map(),
  sourceGroups: () => [], refTexture: {source: DUMMY}, refUvWidth: 1, background: {c: [34, 36, 40, 255], s: [140, 24, 0, 255], n: [128, 128, 255, 255]}});
export const PNG = painted.PNG, texelsPerUnit = unwrapped.S;

// ---------------- source (Free Model) ----------------
const uuid = s => { const h = createHash('sha256').update('afl-silverwood-12-v3:' + s).digest('hex'); return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-8${h.slice(17, 20)}-${h.slice(20, 32)}`; };
const r12 = v => +v.toFixed(12) || 0;
const OLD_ANIM = JSON.parse(fs.readFileSync(path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight/animations/silverwood_12.animation.json'), 'utf8').replace(/^﻿/, ''));
// placeholder clips: the previous clip lengths, loop modes and mechanical sound cues, no motion (V3 is model-only)
const CLIPS = ['static_idle', 'reload_empty', 'reload_tactical', 'draw', 'put_away', 'shoot', 'inspect'];
const clipSpec = n => { const c = OLD_ANIM.animations?.[n] ?? OLD_ANIM_BASE[n]; return {length: c.animation_length, loop: c.loop ?? false, sounds: c.sound_effects || {}}; };
let OLD_ANIM_BASE = {};
export function buildSource({referenceArmsExported = false} = {}) {
  const groups = RIG.map(([name, parent, origin, rot]) => ({name, uuid: uuid('group:' + name), export: true, locked: false, scope: 0, selected: false, visibility: true,
    _static: {properties: {}, temp_data: {}}, origin: origin.slice(), rotation: (rot || [0, 0, 0]).slice(), color: 0, children: [], reset: false, shade: true,
    mirror_uv: false, autouv: 0, isOpen: true, primary_selected: false}));
  const nodes = new Map(RIG.map(([name]) => [name, {uuid: uuid('group:' + name), isOpen: true, children: []}]));
  const outliner = [];
  for (const [name, parent] of RIG) (parent ? nodes.get(parent).children : outliner).push(nodes.get(name));
  const elements = [];
  for (const p of PARTS) {
    const key = i => i.toString(36).padStart(4, '0'), vertices = {}, faces = {}, uvs = unwrapped.faceUV.get(p);
    p.v.forEach((q, i) => { vertices[key(i)] = q.map(r12); });
    p.f.forEach((f, fi) => { const uv = uvs.get(f); faces['f' + key(fi)] = {uv: Object.fromEntries(f.ids.map((id, j) => [key(id), uv[j].map(r12)])), vertices: f.ids.map(key), texture: 0}; });
    const id = uuid('mesh:' + p.name);
    elements.push({name: p.name, color: 0, origin: [0, 0, 0], rotation: [0, 0, 0], shading: 'flat', export: true, visibility: true, locked: false,
      render_order: 'default', scope: 0, allow_mirror_modeling: true, vertices, faces, type: 'mesh', uuid: id});
    nodes.get(p.bone).children.push(id);
  }
  // source-only reference arms: the Vanilla arm as NativePlayerArmRenderer draws it at this gun's first-person scale 0.45
  // (half width 2 x .62 / .45, length 12 x .78 / .45), hanging from the hand cap along the anchor's -Y
  for (const side of ['right', 'left']) {
    const [x, y, z] = HANDS[side].pos, hw = 2 * 0.62 / 0.45, len = 12 * 0.78 / 0.45, id = uuid('arm:' + side);
    elements.push({name: `${side}_arm_reference`, box_uv: false, render_order: 'default', locked: false, export: referenceArmsExported, scope: 0, allow_mirror_modeling: true,
      from: [x - hw, y - len, z - hw], to: [x + hw, y, z + hw], autouv: 0, color: side === 'right' ? 4 : 1, visibility: true, origin: [0, 0, 0],
      faces: Object.fromEntries(['north', 'east', 'south', 'west', 'up', 'down'].map(d => [d, {uv: [0, 0, 1, 1], texture: 0}])), type: 'cube', uuid: id});
    nodes.get(`${side}_hand_anchor`).children.push(id);
  }
  const effects = spec => ({effects: {name: 'effects', type: 'effect', keyframes: Object.entries(spec.sounds).map(([t, s]) => ({channel: 'sound',
    data_points: [{effect: s.effect, locator: '', file: ''}], uuid: uuid(`sound:${spec.name}:${t}`), time: +t, color: -1, interpolation: 'linear'}))}});
  const animations = CLIPS.map(name => { const spec = {...clipSpec(name), name};
    return {uuid: uuid('anim:' + name), name, loop: spec.loop === 'hold_on_last_frame' ? 'hold' : spec.loop === true ? 'loop' : 'once', override: false, length: spec.length,
      snapping: 24, selected: false, saved: false, path: '', scope: 0, anim_time_update: '', blend_weight: '', start_delay: '', loop_delay: '', animators: Object.keys(spec.sounds).length ? effects(spec) : {}}; });
  return {meta: {format_version: '5.0', model_format: 'free', box_uv: false}, name: 'silverwood_12', model_identifier: '', visible_box: [6, 2.5, 0.75],
    variable_placeholders: '', variable_placeholder_buttons: [], timeline_setups: [], unhandled_root_fields: {}, resolution: {width: ATLAS, height: ATLAS},
    elements, groups, outliner,
    textures: [{path: '', name: 'silverwood_12.png', folder: '', namespace: '', id: '0', group: '', width: ATLAS, height: ATLAS, uv_width: ATLAS, uv_height: ATLAS,
      particle: false, use_as_default: false, layers_enabled: false, sync_to_project: '', render_mode: 'default', render_sides: 'auto', pbr_channel: 'color',
      frame_time: 1, frame_order_type: 'loop', frame_order: '', frame_interpolate: false, visible: true, internal: true, saved: true,
      uuid: uuid('texture'), relative_path: 'textures/silverwood_12.png', source: 'data:image/png;base64,' + PNG[0].toString('base64')}],
    animations};
}
// the previous runtime clips are read from git history once the new animation file replaces them
{
  const ANIM_REF = 'e184568';
  try { OLD_ANIM_BASE = JSON.parse((await import('node:child_process')).execFileSync('git', ['show', `${ANIM_REF}:src/main/resources/assets/apocalypse_firstlight/animations/silverwood_12.animation.json`],
    {cwd: ROOT, encoding: 'utf8', maxBuffer: 1 << 26})).animations; } catch { OLD_ANIM_BASE = {}; }
  OLD_ANIM.animations = OLD_ANIM_BASE;   // always derive the placeholders from the pre-V3 clips
}
const source = buildSource();

// ---------------- runtime ----------------
const geo = {format_version: '1.12.0', 'minecraft:geometry': [{description: {identifier: 'geometry.silverwood_12', texture_width: ATLAS, texture_height: ATLAS,
  visible_bounds_width: 6, visible_bounds_height: 2.5, visible_bounds_offset: [0, 0.75, 0]},
  bones: RIG.map(([name, parent, origin, rot]) => ({name, ...(parent ? {parent} : {}), pivot: [-origin[0] || 0, origin[1], origin[2]],
    ...(rot ? {rotation: [-rot[0] || 0, -rot[1] || 0, rot[2]]} : {})}))}]};
const sidecar = convert(source, geo, {}, 'silverwood_12.bbmodel', 2);
const meshText = serializeCompact(sidecar);
assert(meshText.length < 4 * 1024 * 1024, 'sidecar exceeds runtime 4 MiB limit');
const animJson = {format_version: '1.8.0', animations: Object.fromEntries(CLIPS.map(name => { const s = clipSpec(name);
  return [name, {...(s.loop ? {loop: s.loop} : {}), animation_length: s.length, ...(Object.keys(s.sounds).length ? {sound_effects: s.sounds} : {}), bones: {}}]; })),
  geckolib_format_version: 2};

const bb = path.join(ROOT, 'src/main/blockbench'), assets = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight');
const outputs = [[path.join(bb, 'silverwood_12.bbmodel'), JSON.stringify(source)], [path.join(assets, 'geo/silverwood_12.geo.json'), JSON.stringify(geo, null, 2)],
  [path.join(assets, 'meshes/silverwood_12.aflmesh.json'), meshText], [path.join(assets, 'animations/silverwood_12.animation.json'), JSON.stringify(animJson, null, 2)],
  ...['', '_s', '_n'].flatMap((k, i) => [[path.join(bb, `textures/silverwood_12${k}.png`), PNG[i]], [path.join(assets, `textures/item/silverwood_12${k}.png`), PNG[i]]])];

const count = parts => { const f = parts.flatMap(p => p.faces); return {triangleEquivalent: f.reduce((s, q) => s + q.length - 2, 0), quads: f.filter(q => q.length === 4).length, triangles: f.filter(q => q.length === 3).length}; };
const zf = zFightLevels(PARTS.map(p => ({...p, f: p.f.map((f, i) => ({...f, slab: p.name + ':' + i}))})), new Map());
export const stats = {...count(sidecar.parts), parts: sidecar.parts.length, texelsPerUnit: unwrapped.S, islands: unwrapped.islands.length,
  coplanarOverlaps: zf.unresolved.length, byPart: Object.fromEntries(sidecar.parts.map(p => [p.name, p.faces.reduce((s, q) => s + q.length - 2, 0)]))};
const isMain = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  console.log(JSON.stringify(stats));
  if (process.argv.includes('--debug')) fs.writeFileSync(process.argv[process.argv.indexOf('--debug') + 1], JSON.stringify(buildSource({referenceArmsExported: true})));
  else if (process.argv.includes('--check')) {
    for (const [file, data] of outputs) { const cur = fs.existsSync(file) ? fs.readFileSync(file) : null; if (!cur || !cur.equals(Buffer.isBuffer(data) ? data : Buffer.from(data))) throw new Error('stale ' + path.relative(ROOT, file)); }
    console.log('CHECK OK');
  } else { for (const [file, data] of outputs) fs.writeFileSync(file, data); console.log('wrote ' + outputs.map(([f]) => path.relative(ROOT, f)).join(', ')); }
}
