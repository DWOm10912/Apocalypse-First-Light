// AFL Equipment Meshes V1: worn / carried equipment items as Pure Mesh in the world views (hand, ground, item frame, and
// worn on the body through their Curios renderer) and as a 16x16 2D icon in the inventory, by the same bake as the
// Material Meshes (tools/build-material-meshes-v1.mjs: one bone, one 256 LabPBR atlas Base Color / _s / _n, glass by
// material alpha). Add a new piece of equipment as one more entry in ITEMS.
//   node tools/build-equipment-meshes-v1.mjs [id ...]               -> writes source, atlas, geo, AFL mesh sidecar, icon, item model
//   node tools/build-equipment-meshes-v1.mjs [id ...] --check       -> verifies every output is up to date
//   node tools/build-equipment-meshes-v1.mjs [id ...] --preview DIR -> writes the outputs into DIR only (offline review)
// The printed stats give each item's renderer vertical offset (0.5 - height / 32, for AflStaticMeshItemClient) and, for
// worn items, where the body anchor ends up after bake re-centres the mesh (wornAnchor, px).
// Worn gear (spec.worn) is authored in its body part's frame (body, right leg or head; see TORSO / LEG / EAR below) and
// prints `origin`: the bake shift, which its renderer undoes (translate -origin / 16) to wear it in place. spec.hud also
// writes the HUD weapon-list silhouette (textures/gui/melee/<id>_hud.png).
// Items are authored at icon size (px), like the material meshes. Materials follow the AFL rules: plain surfaces, no
// painted marks or text, coated rather than bare metal on large faces, glass F0 10 with enough alpha to survive shaders.
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {Part, extrude, revolve, area2, newell, png, readPng, triangulate} from './cube-slab-mesh-lib.mjs';
import {bake} from './build-material-meshes-v1.mjs';
import {pineKeystone} from './halstead-badge.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const orient = (L, ccw) => (area2(L) > 0) === ccw ? L : L.slice().reverse();
const circle = (cu, cv, r, n, phase = Math.PI / n) => Array.from({length: n}, (_, i) => [cu + r * Math.cos(phase + 2 * Math.PI * i / n), cv + r * Math.sin(phase + 2 * Math.PI * i / n)]);
function roundRect(u0, v0, u1, v1, r, seg = 3) {
  const out = [];
  for (const [cu, cv, a0] of [[u1 - r, v0 + r, -90], [u1 - r, v1 - r, 0], [u0 + r, v1 - r, 90], [u0 + r, v0 + r, 180]])
    for (let i = 0; i <= seg; i++) { const a = (a0 + 90 * i / seg) * Math.PI / 180; out.push([cu + r * Math.cos(a), cv + r * Math.sin(a)]); }
  return out;
}
/** Rotate a part about the Y axis by 90 degrees: +Z becomes +X (revolve builds along Z). */
const zToX = part => { part.v = part.v.map(([x, y, z]) => [z, y, -x]); };

// shared materials (LabPBR via paint(): sm smoothness, f0 = 255 metal / 10 glass / 20-24 coated or dielectric)
const GLASS = {c: [206, 222, 228], hl: 6, sm: 235, se: 235, f0: 10, alpha: 110};   // soda-lime glass; alpha 110 stays visible in Sundial

/**
 * Clinical thermometer: a glass stem (8-sided, 1.3 px across) with a white enamel backing strip and a red spirit column
 * inside, the bulb at one end filled with the same spirit, the far end of the glass rounded off. Lies along X, bulb at -X.
 */
function clinicalThermometer() {
  const B = 'clinical_thermometer';
  const glass = new Part('glass', B, 'glass');
  revolve(glass, 0, 0, [[6.2, 0], [6.15, 0.35], [5.85, 0.62], [5.4, 0.66], [-4.4, 0.66], [-4.55, 0.5], [-4.6, 0]], 8);
  const bulb = new Part('bulb', B, 'spirit');
  revolve(bulb, 0, 0, [[-4.45, 0], [-4.5, 0.42], [-5.1, 0.5], [-5.75, 0.44], [-6.05, 0.24], [-6.15, 0]], 8);
  const column = new Part('column', B, 'spirit');
  revolve(column, 0, 0.1, [[1.6, 0], [1.6, 0.26], [-4.5, 0.26], [-4.5, 0]], 6);
  const backing = new Part('backing', B, 'enamel');
  extrude(backing, 'z', {outer: orient([[-0.34, -0.42], [0.34, -0.42], [0.34, -0.26], [-0.34, -0.26]], true), holes: []}, -4.3, 5.4, 0);
  for (const p of [glass, bulb, column, backing]) zToX(p);
  return {PARTS: [glass, bulb, column, backing], MATS: {
    glass: GLASS,
    spirit: {c: [168, 52, 44], hl: 6, sm: 150, se: 170, f0: 20},     // red-dyed spirit
    enamel: {c: [206, 203, 194], hl: 4, sm: 110, se: 120, f0: 20},   // white enamel strip behind the column
  },
  // held like a vanilla handheld (stick, sword), by the glass end with the bulb out: the vanilla transforms with Z - 135
  // degrees (a handheld sprite's tip points along +X+Y; this mesh runs along X with the bulb at -X)
  display: {
    thirdperson_righthand: {rotation: [0, -90, -80], translation: [0, 4, 0.5], scale: [0.85, 0.85, 0.85]},
    firstperson_righthand: {rotation: [0, -90, -110], translation: [1.13, 3.2, 1.13], scale: [0.68, 0.68, 0.68]},
  },
  icon: {view: [30, 45, 0], coverage: 0.3, xray: ['glass'], mat: {glass: [196, 214, 222], enamel: [222, 220, 212], spirit: [196, 50, 40]}}};
}

/**
 * Wrist thermometer: a dial thermometer on a strap. The strap is a flat loop around the arm (4 x 4 px cross-section, as
 * the player model's arm), 2 px wide along the arm (Y); the dial sits on the strap's +X side: a dark coated case ring,
 * an off-white recessed dial with a red needle, a glass crystal over it. Worn: +X points away from the body.
 */
export const WRIST = {arm: 2.1, strap: 0.45, width: 2.0, caseR: 1.75, dialR: 1.38};
function wristThermometer() {
  const B = 'wrist_thermometer', {arm, strap, width, caseR, dialR} = WRIST, cy = width / 2, out = arm + strap;
  const band = new Part('strap', B, 'strap');
  extrude(band, 'y', {outer: orient(roundRect(-out, -out, out, out, 0.7), true), holes: [orient(roundRect(-arm, -arm, arm, arm, 0.45), false)]}, 0, width, 0.12);
  const kase = new Part('case', B, 'case');
  // a lathed ring (an annulus cap would need a holed triangulation): bevelled top outer edge, open in the middle
  const z0 = out - 0.1, z1 = out + 1.15, bev = 0.22;
  revolve(kase, 0, cy, [[z1, dialR], [z1, caseR - bev], [z1 - bev, caseR], [z0, caseR], [z0, dialR]], 18);
  zToX(kase);
  const dial = new Part('dial', B, 'dial');
  extrude(dial, 'x', {outer: orient(circle(0, cy, dialR - 0.01, 18), true), holes: []}, out - 0.06, out + 0.78, 0);   // just inside the ring: no shared faces
  const needle = new Part('needle', B, 'needle');
  extrude(needle, 'x', {outer: orient([[-0.17, cy - 0.25], [0.17, cy - 0.25], [0.08, cy + 1.05], [-0.08, cy + 1.05]], true), holes: []}, out + 0.79, out + 0.85, 0);
  const crystal = new Part('crystal', B, 'glass');
  extrude(crystal, 'x', {outer: orient(circle(0, cy, dialR - 0.01, 18), true), holes: []}, out + 0.9, out + 1.05, 0);
  return {PARTS: [band, kase, dial, needle, crystal], MATS: {
    glass: {...GLASS, alpha: 70},
    strap: {c: [50, 52, 54], hl: 6, sm: 64, se: 80, f0: 20},          // dark woven nylon
    case: {c: [70, 72, 76], hl: 12, sm: 120, se: 150, f0: 24},        // coated black steel case, bevels catch the light
    dial: {c: [190, 186, 174], hl: 4, sm: 96, se: 100, f0: 20},       // off-white enamel dial, no printing
    needle: {c: [168, 52, 44], hl: 4, sm: 120, se: 120, f0: 20},      // red needle
  }, icon: {view: [8, -90, 0], xray: ['glass'], seamless: ['needle'], mat: {glass: [170, 190, 198], strap: [88, 90, 92], case: [62, 64, 68], dial: [255, 252, 240], needle: [214, 52, 40]}},   // dial facing the viewer, strap left / right
  // the arm axis before bake re-centres the mesh: x = 0, z = 0, middle of the strap's width
  anchor: [0, cy, 0]};
}

/**
 * Wiring wrench (Building Wiring V1, 2026-10-13): an electrician's insulated combination wrench (references: VDE 1000 V
 * dipped tools), open end at -X, box end at +X, both chrome-plated steel; the shank dipped in red insulation with yellow
 * collars at its ends, flattened like a real dipped handle. Lies flat in X / Z; the heads 0.6 px thick, the neck runs
 * 0.45 px into each end of the grip so the steel comes out of the dip's end face, never through its side.
 */
function wiringWrench() {
  const B = 'wiring_wrench', T = 0.3, hn = 0.36, grip = 3.2, collar = 2.55, enter = 2.75, flat = 0.72;
  const zx = L => L.map(([x, z]) => [z, x]), arc = (cx, R, a0, a1, n) => Array.from({length: n + 1}, (_, i) => {
    const a = (a0 + (a1 - a0) * i / n) * Math.PI / 180; return [cx + R * Math.cos(a), R * Math.sin(a)]; });
  // open end: head circle at -X, a straight jaw slot opening to -X with a round bottom
  const oc = -5.6, oR = 2.0, w = 0.72, slot = -5.3, aS = 180 - Math.asin(w / oR) * 180 / Math.PI, aN = Math.asin(hn / oR) * 180 / Math.PI;
  const openPlan = [[-enter, hn], ...arc(oc, oR, aN, aS, 12), [slot, w], ...arc(slot, w, 90, -90, 8).slice(1, -1), [slot, -w],
    ...arc(oc, oR, 360 - aS, 360 - aN, 12), [-enter, -hn]];
  const open = new Part('open_end', B, 'steel');
  extrude(open, 'y', {outer: orient(zx(openPlan), true), holes: []}, -T, T, 0.12);
  // box end: ring at +X with a 12-point hole
  const bc = 5.5, bR = 1.85, bN = Math.asin(hn / bR) * 180 / Math.PI;
  const boxPlan = [[enter, -hn], ...arc(bc, bR, 180 + bN, 540 - bN, 22), [enter, hn]];
  const box = new Part('box_end', B, 'steel');
  extrude(box, 'y', {outer: orient(zx(boxPlan), true), holes: [orient(zx(circle(bc, 0, 1.0, 12, 0)), false)]}, -T, T, 0.12);
  // dipped grip: lathed along Z, turned onto X, flattened; yellow collars beyond |x| = collar
  const dip = new Part('grip', B, 'dip');
  revolve(dip, 0, 0, [[grip, 0], [grip, 0.62], [grip - 0.2, 0.86], [collar, 0.86], [-collar, 0.86], [-(grip - 0.2), 0.86], [-grip, 0.62], [-grip, 0]], 8);
  zToX(dip);
  dip.v = dip.v.map(([x, y, z]) => [x, y * flat, z]);
  for (const f of dip.f) if (Math.abs(f.ids.reduce((s, id) => s + dip.v[id][0], 0) / f.ids.length) > collar) f.mat = 'collar';
  return {PARTS: [dip, open, box], MATS: {
    steel: {c: [150, 152, 156], hl: 12, sm: 128, se: 150, f0: 233},   // satin chrome plating (LabPBR chrome), not mirror
    dip: {c: [172, 40, 34], hl: 4, sm: 96, se: 110, f0: 20},          // red insulating dip
    collar: {c: [212, 168, 38], hl: 4, sm: 96, se: 110, f0: 20},      // yellow inner layer at the ends
  },
  // held like a vanilla handheld (the thermometer's transforms), working end out, but moved so the hand closes on the grip
  // in the middle (a sprite's handle sits about 5.7 px from its centre: that distance along the mesh's +X, after scale)
  display: {
    thirdperson_righthand: {rotation: [0, -90, -80], translation: [0, -0.9, 1.37], scale: [0.8, 0.8, 0.8]},
    firstperson_righthand: {rotation: [0, -90, -110], translation: [1.13, -0.46, 2.46], scale: [0.62, 0.62, 0.62]},
  },
  // flat face to the viewer, the open end top right like a vanilla tool
  icon: {view: [90, 225, 0], coverage: 0.4, mat: {steel: [186, 188, 194], dip: [206, 50, 42], collar: [240, 194, 48]}}};
}

// ---------------------------------------------------------------- swept bars and body-wrapped straps (2026-10-14)

const add3 = (a, b) => a.map((v, i) => v + b[i]), sub3 = (a, b) => a.map((v, i) => v - b[i]), mul3 = (a, k) => a.map(v => v * k);
const dot3 = (a, b) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
const cross3 = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
const unit = a => { const l = Math.hypot(...a) || 1; return a.map(v => v / l); };

/** Rings of vertex ids joined into a tube (quads, split by Part.face when twisted); open ends get a fan cap. */
function loft(part, rings, centres, {closed = false, mat = () => part.mat, capMat = null} = {}) {
  const n = rings.length, m = rings[0].length;
  for (let i = 0; i < (closed ? n : n - 1); i++) {
    const A = rings[i], B = rings[(i + 1) % n], axis = mul3(add3(centres[i], centres[(i + 1) % n]), 0.5);
    for (let j = 0; j < m; j++) {
      const k = (j + 1) % m, ids = [A[j], A[k], B[k], B[j]];
      const mid = mul3(ids.reduce((s, id) => add3(s, part.v[id]), [0, 0, 0]), 0.25);
      part.face(ids, sub3(mid, axis), 'side', mat(i));
    }
  }
  if (!closed) for (const [i, nb] of [[0, 1], [n - 1, n - 2]]) {
    const c = part.vtx(centres[i]), dir = sub3(centres[i], centres[nb]);
    for (let j = 0; j < m; j++) part.face([c, rings[i][j], rings[i][(j + 1) % m]], dir, 'cap', capMat ?? mat(Math.min(i, n - 2)));
  }
}
/** One ring: 2D section points (u along n, v along b) placed at p. */
const ringAt = (part, p, n, b, pts) => pts.map(([u, v]) => part.vtx(add3(p, add3(mul3(n, u), mul3(b, v)))));
/** A hexagonal bar section, corners on +-n, flats facing +-b: half extent t along n, half flat width w along b. */
const hexSection = (t, w) => Array.from({length: 6}, (_, k) => { const a = k * Math.PI / 3; return [t * Math.cos(a), w / 0.8660254 * Math.sin(a)]; });

/**
 * A body shell: an inner box and a radius, the surface every strap lies on (a rounded box around the body part with its
 * skin overlay layer, the corners rounded so a strap running over the box's edge never cuts into it).
 */
const clampBox = (p, s) => p.map((v, k) => Math.max(s.lo[k], Math.min(s.hi[k], v)));
const outward = (p, s) => { const d = sub3(p, clampBox(p, s)); return Math.hypot(...d) < 1e-9 ? [0, 0, 1] : unit(d); };
/** p moved onto the shell surface, lifted by `lift` (a strap's half thickness). */
const onShell = (p, s, lift = 0) => add3(clampBox(p, s), mul3(outward(p, s), s.r + lift));
/** Where the ray from c (inside the inner box) along u leaves the shell lifted by `lift`. */
function shellHit(c, u, s, lift) {
  const R = s.r + lift, f = t => Math.hypot(...sub3(add3(c, mul3(u, t)), clampBox(add3(c, mul3(u, t)), s))) - R;
  let a = 0, b = 1; while (f(b) < 0) b *= 2;
  for (let i = 0; i < 40; i++) { const m = (a + b) / 2; if (f(m) < 0) a = m; else b = m; }
  return add3(c, mul3(u, b));
}
/** Points around the shell in the plane through c spanned by e1, e2, from angle a0 to a1 (degrees). */
const shellArc = (c, e1, e2, a0, a1, step, s, lift) => {
  const n = Math.max(1, Math.round(Math.abs(a1 - a0) / step));
  return Array.from({length: n + 1}, (_, i) => { const a = (a0 + (a1 - a0) * i / n) * Math.PI / 180; return shellHit(c, add3(mul3(e1, Math.cos(a)), mul3(e2, Math.sin(a))), s, lift); });
};
/** Points along a polyline, about `step` apart, each pressed onto the shell. */
const shellLine = (pts, step, s, lift) => {
  const out = [pts[0]];
  for (let i = 0; i + 1 < pts.length; i++) { const n = Math.max(1, Math.round(Math.hypot(...sub3(pts[i + 1], pts[i])) / step));
    for (let k = 1; k <= n; k++) out.push(add3(pts[i], mul3(sub3(pts[i + 1], pts[i]), k / n))); }
  return out.map(p => onShell(p, s, lift));
};
/**
 * A flat strap (webbing) along points on a shell: rectangular section, `width` across the strap (tangent to the body),
 * `thick` outward. Closed straps (belts) join up; open ones get end caps.
 */
function strap(part, pts, s, {width, thick, closed = false, mat}) {
  const n = pts.length, rings = [], tw = [[thick / 2, width / 2], [thick / 2, -width / 2], [-thick / 2, -width / 2], [-thick / 2, width / 2]];
  for (let i = 0; i < n; i++) {
    const prev = pts[closed ? (i - 1 + n) % n : Math.max(i - 1, 0)], next = pts[closed ? (i + 1) % n : Math.min(i + 1, n - 1)];
    const T = unit(sub3(next, prev)), b = unit(cross3(T, outward(pts[i], s))), nn = cross3(b, T);
    rings.push(ringAt(part, pts[i], nn, b, tw));
  }
  loft(part, rings, pts, {closed, mat: () => mat ?? part.mat});
}

/**
 * Crowbar (2026-10-14, replaces the V1 cuboid model): a forged hexagonal wrecking bar, 0.87 px across flats, 27 px over
 * all. The lower end bends 22 degrees to +X into a flattened chisel; the upper end is a gooseneck (radius 1.75 px, 185
 * degrees) ending in a split nail claw whose V slot widens toward the tips. Red powder coat on the bar, bare forged steel
 * on the last 1.25 px of the chisel and from the gooseneck's lower half to the claw (real bars come painted, the working
 * ends worn bright). Authored in the V1 cuboid model's frame (shaft on x = 0 along +Y, the claw bent to -X, z across), so
 * the first-person rig (CrowbarFirstPerson: crowbar_model bone = (frame - [0, 3.8, 0]) x 0.65) keeps its grip.
 */
function crowbar() {
  const B = 'crowbar', bar = new Part('bar', B, 'coat'), Z = [0, 0, 1];
  // centreline in the XY plane: chisel tip -> lower bend (cubic) -> shaft -> gooseneck arc
  const bez = (P, t) => { const u = 1 - t; return [0, 1].map(k => u * u * u * P[0][k] + 3 * u * u * t * P[1][k] + 3 * u * t * t * P[2][k] + t * t * t * P[3][k]); };
  const lower = [[1.0, -5.0], [0.55, -3.9], [0, -2.9], [0, -1.9]];
  const C = [-1.75, 20.3], R = 1.75, ARC = 185;
  const path2 = [...Array.from({length: 11}, (_, i) => bez(lower, i / 10)),
    ...[2.5, 7, 11.5, 16, 18.8].map(y => [0, y]),
    ...Array.from({length: 19}, (_, i) => { const a = ARC * i / 18 * Math.PI / 180; return [C[0] + R * Math.cos(a), C[1] + R * Math.sin(a)]; })];
  const path = path2.map(([x, y]) => [x, y, 0]);
  const arc = [0]; for (let i = 1; i < path.length; i++) arc.push(arc[i - 1] + Math.hypot(...sub3(path[i], path[i - 1])));
  const arcStart = arc[16], total = arc[arc.length - 1], T = 0.5, W = T * 0.8660254;
  const size = s => s < 1.15 ? [0.07 + (T - 0.07) * Math.pow(s / 1.15, 0.8), 0.56 - (0.56 - W) * (s / 1.15)] : [T, W];
  const frame = i => { const t = unit(sub3(path[Math.min(i + 1, path.length - 1)], path[Math.max(i - 1, 0)])); return {n: [-t[1], t[0], 0], b: Z}; };
  const rings = path.map((p, i) => { const {n, b} = frame(i), [t, w] = size(arc[i]); return ringAt(bar, p, n, b, hexSection(t, w)); });
  const steelFrom = arcStart + (total - arcStart) * 0.55;
  loft(bar, rings, path, {mat: i => (arc[i] + arc[i + 1]) / 2 < 1.25 || (arc[i] + arc[i + 1]) / 2 > steelFrom ? 'steel' : 'coat'});
  // split claw: two prongs carrying on along the arc's end tangent, starting inside the bar's end
  const a1 = ARC * Math.PI / 180, end = path[path.length - 1], tan = [-Math.sin(a1), Math.cos(a1), 0], nrm = [-tan[1], tan[0], 0];
  const prongs = [];
  for (const side of [-1, 1]) {
    const prong = new Part('claw_' + (side < 0 ? 'a' : 'b'), B, 'steel'), steps = 6, len = 1.15, from = -0.18;
    const P = [], Rg = [];
    for (let k = 0; k <= steps; k++) {
      const f = k / steps, s = from + (len - from) * f, off = side * (0.235 + 0.09 * f);
      const p = add3(add3(end, mul3(tan, s)), mul3(Z, off)); P.push(p);
      Rg.push(ringAt(prong, p, nrm, Z, hexSection(0.42 - 0.34 * Math.pow(f, 1.2), 0.2 - 0.03 * f)));
    }
    loft(prong, Rg, P);
    prongs.push(prong);
  }
  return {PARTS: [bar, ...prongs], MATS: {
    coat: {c: [150, 38, 32], hl: 10, sm: 128, se: 140, f0: 22},     // red powder coat, satin
    steel: {c: [104, 106, 108], hl: 14, sm: 96, se: 120, f0: 230},  // bare forged steel, worn bright but rough
  },
  // the V1 model's transforms (gui is the 2D icon now)
  display: {
    firstperson_righthand: {rotation: [0, -18, -12], translation: [1.25, 1.5, -0.5], scale: [0.75, 0.75, 0.75]},
    firstperson_lefthand: {rotation: [0, 18, 12], translation: [1.25, 1.5, -0.5], scale: [0.75, 0.75, 0.75]},
    thirdperson_righthand: {rotation: [0, 0, -8], translation: [0, 2, 0], scale: [0.7, 0.7, 0.7]},
    thirdperson_lefthand: {rotation: [0, 0, 8], translation: [0, 2, 0], scale: [0.7, 0.7, 0.7]},
    ground: {rotation: [0, 0, 90], translation: [0, -2, 0], scale: [0.52, 0.52, 0.52]},
    fixed: {rotation: [0, -90, -30], translation: [0, 0, 0], scale: [0.55, 0.55, 0.55]},
  },
  icon: {view: [0, 0, -45], coverage: 0.3, mat: {coat: [176, 44, 36], steel: [150, 152, 156]}},
  // HUD weapon list (plan 10): side view, claw to the left curling up (screen x = -y, screen up = -x)
  hud: {screen: p => [-p[1], -p[0]], toward: [0, 0, -1], scale: 40},
  // where the V1 frame's origin lands in the baked mesh (the first-person rig and the item's old vertical place need it)
  worn: true};
}

// Body frame: origin at the body's pivot (neck, top centre of the torso), +X to the player's left, +Y up (torso y -12..0),
// +Z to the front (torso z -2..2). The renderers turn the body part's model space (+Y down, -Z front) into it with a
// 180 degree turn about X. The shell clears the skin's jacket layer (0.25) by 0.3 px on the flat faces.
const TORSO = {lo: [-3.6, -11.6, -1.6], hi: [3.6, -0.4, 1.6], r: 0.95};
// Right-leg frame (the holster): origin at the hip joint, +X outward (the player's right), +Y up (leg y -12..0), +Z to
// the back (leg z -2..2); the renderer turns the leg's model space into it with a 180 degree turn about Z.
const LEG = {lo: [-1.6, -11.6, -1.6], hi: [1.6, -0.4, 1.6], r: 0.95};
const NYLON = {hl: 6, sm: 64, se: 76, f0: 20};   // woven nylon / canvas: dielectric, matte
const rr = (u0, v0, u1, v1, r, seg = 3) => orient(roundRect(u0, v0, u1, v1, r, seg), true);
/** A box extruded along Z (front plate, pouch): outline in (x, y), z from z0 to z1. */
const slabZ = (part, x0, y0, x1, y1, z0, z1, r, c) => extrude(part, 'z', {outer: rr(x0, y0, x1, y1, r), holes: []}, z0, z1, c);

/**
 * Field chest rig (test gear, grid [1x2][1x2][1x2][2x2]): a front panel across the chest with three rifle magazine
 * pouches and one larger utility pouch, each with a closed flap; an H harness (shoulder straps over the shoulders and
 * down the back to the waist strap) and a waist strap around the body. Ranger-green nylon, dark plastic buckle tabs.
 */
function fieldChestRig() {
  const B = 'field_chest_rig', parts = [];
  const panel = new Part('panel', B, 'nylon'); slabZ(panel, -4.1, -9.4, 4.1, -4.0, 2.4, 2.85, 0.5, 0.12); parts.push(panel);
  const pouch = (name, x0, x1, depth) => {
    const body = new Part(name, B, 'nylon'); slabZ(body, x0, -8.9, x1, -5.6, 2.68, 2.85 + depth, 0.25, 0.14);
    const flap = new Part(name + '_flap', B, 'flap'); slabZ(flap, x0 - 0.06, -6.45, x1 + 0.06, -5.42, 2.8, 2.95 + depth, 0.22, 0.1);   // backs inside the panel, apart
    const tab = new Part(name + '_tab', B, 'buckle'); slabZ(tab, (x0 + x1) / 2 - 0.28, -6.75, (x0 + x1) / 2 + 0.28, -6.25, 2.95 + depth - 0.05, 3.1 + depth, 0.08, 0);
    parts.push(body, flap, tab);
  };
  pouch('mag_1', -3.95, -2.45, 0.95); pouch('mag_2', -2.3, -0.8, 0.95); pouch('mag_3', -0.65, 0.85, 0.95); pouch('utility', 1.05, 3.95, 1.15);
  // shoulder straps: over the shoulder in the plane x = +-2.9, front from the panel's top, down the back into the waist strap
  for (const x of [-2.9, 2.9]) {
    const sh = new Part('shoulder_' + (x < 0 ? 'r' : 'l'), B, 'nylon');
    // lifted 0.02 more than the waist strap where they overlap on the back: no shared surface
    strap(sh, shellArc([x, -5.0, 0], [0, 0, 1], [0, 1, 0], 6, 240, 4, TORSO, 0.13), TORSO, {width: 0.9, thick: 0.22});
    parts.push(sh);
  }
  const waist = new Part('waist', B, 'nylon');
  strap(waist, shellArc([0, -9.75, 0], [0, 0, 1], [1, 0, 0], 0, 354, 6, TORSO, 0.11), TORSO, {width: 0.85, thick: 0.22, closed: true});
  parts.push(waist);
  return {PARTS: parts, MATS: {
    nylon: {c: [76, 82, 62], ...NYLON},          // ranger green
    flap: {c: [70, 76, 57], ...NYLON},           // the same cloth, a shade darker so the flaps read
    buckle: {c: [38, 39, 40], hl: 8, sm: 120, se: 120, f0: 22},   // black acetal buckle tabs
  }, atlas: 512, icon: {view: [10, 20, 0], coverage: 0.45, hide: ['shoulder_r', 'shoulder_l', 'waist'], mat: {nylon: [90, 98, 74], flap: [58, 64, 48]}}, worn: true};
}

/** Shoulder straps for a backpack: from the bag's top at the back over the shoulder, down the front to y_end. */
function packStraps(B, x, yEnd, mat, width = 0.85) {
  const out = [];
  for (const sx of [-x, x]) {
    const s = new Part('strap_' + (sx < 0 ? 'r' : 'l'), B, mat), c = [sx, -4.0, 0];
    const a0 = Math.atan2(yEnd + 4.0, 2.6) * 180 / Math.PI;   // front end
    // lift 0.12: on the chest the strap stays inside the chest rig's panel (z 2.4..2.85) when both are worn
    strap(s, shellArc(c, [0, 0, 1], [0, 1, 0], a0, 180 + 35, 4, TORSO, 0.12), TORSO, {width, thick: 0.3});
    out.push(s);
  }
  return out;
}

/**
 * Canvas backpack (test gear, 5x6 grid): a small waxed-canvas rucksack. Main bag 5.8 x 7.3 x 2.65 px on the back with
 * rounded edges, a top flap over the top and the upper front, a front pocket, two leather closure straps with steel
 * buckles, shoulder straps down to the chest.
 */
function canvasBackpack() {
  const B = 'canvas_backpack', parts = [];
  const body = new Part('body', B, 'canvas');
  extrude(body, 'x', {outer: rr(-5.0, -9.3, -2.35, -2.0, 0.9, 4), holes: []}, -2.9, 2.9, 0.35); parts.push(body);
  // top flap: a band around the body's top-front rounded corner (corner centre z -4.1, y -2.9, r 0.9)
  const cz = -4.1, cy = -2.9, arcPts = (r, a0, a1) => Array.from({length: 7}, (_, i) => { const a = (a0 + (a1 - a0) * i / 6) * Math.PI / 180; return [cz + r * Math.cos(a), cy + r * Math.sin(a)]; });
  const flapOutline = [[-2.4, -1.73], ...arcPts(1.17, 90, 180), [-5.27, -5.2], [-5.03, -5.2], ...arcPts(0.93, 180, 90), [-2.4, -1.97]];
  const flap = new Part('flap', B, 'canvas_dark'); extrude(flap, 'x', {outer: orient(flapOutline, true), holes: []}, -3.05, 3.05, 0.08); parts.push(flap);
  const pocket = new Part('pocket', B, 'canvas');
  extrude(pocket, 'z', {outer: rr(-2.2, -8.6, 2.2, -5.6, 0.45), holes: []}, -5.85, -4.9, 0.25); parts.push(pocket);
  for (const x of [-1.45, 1.45]) {
    const upper = new Part('closure_' + (x < 0 ? 'r' : 'l'), B, 'leather');
    slabZ(upper, x - 0.27, -5.75, x + 0.27, -3.6, -5.42, -5.2, 0.06, 0); parts.push(upper);
    const buckle = new Part('buckle_' + (x < 0 ? 'r' : 'l'), B, 'steel');
    extrude(buckle, 'z', {outer: rr(x - 0.36, -6.35, x + 0.36, -5.75, 0.08), holes: [orient(roundRect(x - 0.2, -6.2, x + 0.2, -5.9, 0.04), false)]}, -5.98, -5.82, 0);
    parts.push(buckle);
  }
  parts.push(...packStraps(B, 1.95, -6.6, 'leather'));
  return {PARTS: parts, MATS: {
    canvas: {c: [122, 112, 84], ...NYLON},                          // waxed cotton canvas, khaki
    canvas_dark: {c: [112, 102, 76], ...NYLON},
    leather: {c: [98, 66, 44], hl: 8, sm: 92, se: 100, f0: 20},     // oiled leather straps
    steel: {c: [92, 94, 96], hl: 14, sm: 120, se: 140, f0: 230},    // blackened steel buckles
  }, atlas: 512, icon: {view: [15, 150, 0], coverage: 0.45, hide: ['strap_r', 'strap_l']}, worn: true};
}

/**
 * Hiking backpack (test gear, 6x10 grid): an internal-frame trekking pack, 6.8 x 9.4 x 4 px body up to shoulder height
 * (higher would run into the head), a lid on top, a front stretch pocket, two side compression straps per side, padded
 * shoulder straps with a sternum strap, and a padded hip belt around the waist.
 */
function hikingBackpack() {
  const B = 'hiking_backpack', parts = [];
  const body = new Part('body', B, 'shell');
  extrude(body, 'x', {outer: rr(-6.35, -10.8, -2.35, -1.4, 1.1, 4), holes: []}, -3.4, 3.4, 0.5); parts.push(body);
  const lid = new Part('lid', B, 'trim');
  extrude(lid, 'x', {outer: rr(-6.65, -1.75, -2.45, -0.25, 0.6, 4), holes: []}, -3.5, 3.5, 0.4); parts.push(lid);
  const pocket = new Part('pocket', B, 'mesh');
  extrude(pocket, 'z', {outer: rr(-2.5, -9.6, 2.5, -4.0, 0.8), holes: []}, -6.75, -6.15, 0.25); parts.push(pocket);
  for (const x of [-3.4, 3.4]) for (const y of [-4.2, -7.6]) {
    const c = new Part('compression_' + (x < 0 ? 'r' : 'l') + (y < -5 ? '2' : '1'), B, 'trim');
    const v0 = x - 0.12 * Math.sign(x), v1 = x + 0.16 * Math.sign(x);
    extrude(c, 'y', {outer: rr(-6.0, Math.min(v0, v1), -2.8, Math.max(v0, v1), 0.08), holes: []}, y - 0.28, y + 0.28, 0);
    parts.push(c);
  }
  parts.push(...packStraps(B, 1.95, -6.8, 'trim', 1.0));
  const sternum = new Part('sternum', B, 'trim');
  strap(sternum, shellLine([[-2.1, -3.6, 2.7], [2.1, -3.6, 2.7]], 0.5, TORSO, 0.42), TORSO, {width: 0.35, thick: 0.14}); parts.push(sternum);
  const hip = new Part('hip_belt', B, 'trim');
  strap(hip, shellArc([0, -11.0, 0], [0, 0, 1], [1, 0, 0], 0, 354, 6, TORSO, 0.2), TORSO, {width: 1.2, thick: 0.4, closed: true}); parts.push(hip);
  return {PARTS: parts, MATS: {
    shell: {c: [60, 70, 82], ...NYLON},                            // slate-blue ripstop
    trim: {c: [42, 44, 46], ...NYLON},                             // black nylon: lid, straps, belt
    mesh: {c: [52, 58, 66], hl: 4, sm: 50, se: 60, f0: 20},        // stretch pocket, darker and duller
  }, atlas: 512, icon: {view: [15, 150, 0], coverage: 0.45, hide: ['strap_r', 'strap_l', 'sternum', 'hip_belt'], mat: {shell: [78, 92, 110], trim: [60, 62, 66]}}, worn: true};
}

/**
 * Gun sling (item): a rolled two-point sling, four tight turns of olive webbing (0.65 px wide, about 1.3 m) with the
 * free end and its snap hook hanging off the roll.
 */
function gunSling() {
  const B = 'gun_sling', web = new Part('roll', B, 'web'), N = 96, P = [], rings = [];
  for (let i = 0; i <= N; i++) { const a = 4 * 2 * Math.PI * i / N, r = 0.6 + 0.6 * i / N; P.push([r * Math.cos(a), r * Math.sin(a), 0]); }
  P.push(add3(P[N], [0.04, 0.8, 0]), add3(P[N], [0.1, 1.45, 0]));   // the free end leaves along the roll's tangent (+Y at the last turn's end)
  for (let i = 0; i < P.length; i++) {
    const t = unit(sub3(P[Math.min(i + 1, P.length - 1)], P[Math.max(i - 1, 0)])), n = [t[1], -t[0], 0];
    rings.push(ringAt(web, P[i], n, [0, 0, 1], [[0.06, 0.32], [0.06, -0.32], [-0.06, -0.32], [-0.06, 0.32]]));
  }
  loft(web, rings, P);
  const tip = P[P.length - 1], hook = new Part('snap_hook', B, 'steel');
  extrude(hook, 'z', {outer: rr(tip[0] - 0.3, tip[1] - 0.1, tip[0] + 0.3, tip[1] + 1.0, 0.25), holes: [orient(roundRect(tip[0] - 0.13, tip[1] + 0.3, tip[0] + 0.13, tip[1] + 0.75, 0.1), false)]}, -0.12, 0.12, 0);
  return {PARTS: [web, hook], MATS: {
    web: {c: [84, 88, 66], ...NYLON},
    steel: {c: [70, 72, 74], hl: 14, sm: 120, se: 140, f0: 230},
  }, icon: {view: [25, 30, 0], coverage: 0.4}};
}

/**
 * Round wire swept along points (ring of `sides` corners, frames carried along the path so it never twists); closed
 * joins the last point back to the first.
 */
function sweptWire(part, pts, r, {sides = 8, closed = false} = {}) {
  const n = pts.length, tan = i => unit(sub3(pts[closed ? (i + 1) % n : Math.min(i + 1, n - 1)], pts[closed ? (i - 1 + n) % n : Math.max(i - 1, 0)]));
  let t = tan(0), a = Math.abs(t[0]) < 0.9 ? [1, 0, 0] : [0, 1, 0], u = unit(cross3(t, a));
  const ring = Array.from({length: sides}, (_, k) => [r * Math.cos(2 * Math.PI * k / sides), r * Math.sin(2 * Math.PI * k / sides)]), rings = [];
  for (let i = 0; i < n; i++) {
    const ti = tan(i);
    u = unit(sub3(u, mul3(ti, dot3(u, ti))));
    rings.push(ringAt(part, pts[i], u, cross3(ti, u), ring));
    t = ti;
  }
  loft(part, rings, pts, {closed});
}
/** Points of an arc in the y-z plane at x = 0: centre (y, z), radius, from angle a0 to a1 (degrees, 0 = +z, 90 = +y). */
const arcYZ = (cy, cz, r, a0, a1, step = 15) => {
  const n = Math.max(1, Math.round(Math.abs(a1 - a0) / step));
  return Array.from({length: n + 1}, (_, i) => { const a = (a0 + (a1 - a0) * i / n) * Math.PI / 180; return [0, cy + r * Math.sin(a), cz + r * Math.cos(a)]; });
};

// Sling strap physics (docs/native_guns/attachments/sling_physics_v1.md 5): the strap is drawn by code as a ribbon along
// the simulated chain, the hooks as this mesh at each end. Both in centimetres (1 px = 1 cm): real webbing and hardware
// sizes, whatever gun they hang on. The hook's printed points (pin 8.17 cm above the exit at the origin) are
// client/sling/SlingStrapRenderer HOOK_PIN and SlingStraps HOOK: change them together.
const SLING_WEB = {w: 3.2, t: 0.25}, SLING_WIRE = 0.2, SLING_HOOK_Y = 4.0, SLING_HOOK_R = 0.55, SLING_FOLD = 4.0;
const SLING_SWIVEL_WIRE = 0.18;   // the swivel bar the hook rests on (BR-51 / HR-55 / Silverwood swivels, 0.085-0.09 px at about 21 mm/px)

/**
 * Sling snap hook (no item, drawn by the strap renderer at both ends of a sling): a bent steel wire hook in the x-y-z
 * frame of its strap: x across the strap (the swivel bar's direction), y up towards the swivel. A rectangular eye the
 * webbing loops through (bottom bar at y 0), the stem rising behind it to a J that curls over the swivel bar (the bar
 * rests at the top inside of the J, the pin) and a sprung gate wire closing the J; the webbing end folded round the eye's
 * bottom bar, two layers sewn flat down to the strap's exit (y -4 cm, where the ribbon starts).
 */
function gunSlingHook() {
  const B = 'gun_sling_hook', steel = new Part('hook', B, 'steel'), gate = new Part('gate', B, 'steel'), web = new Part('fold', B, 'web');
  const R = 0.3, W = 1.9, H = 1.0, eye = [];
  for (const [cx, cy, a0] of [[W - R, R, -90], [W - R, H - R, 0], [-W + R, H - R, 90], [-W + R, R, 180]])
    for (let i = 0; i <= 3; i++) { const a = (a0 + 30 * i) * Math.PI / 180; eye.push([cx + R * Math.cos(a), cy + R * Math.sin(a), 0]); }
  sweptWire(steel, eye, SLING_WIRE, {closed: true});
  const zs = -SLING_HOOK_R, stem = [[0, H - 0.05, 0], [0, H + 0.3, zs * 0.55], [0, H + 0.75, zs], [0, 2.6, zs], [0, SLING_HOOK_Y, zs]];
  const j = arcYZ(SLING_HOOK_Y, 0, SLING_HOOK_R, 180, 0).slice(1), tip = [0, SLING_HOOK_Y - 0.7, SLING_HOOK_R];
  sweptWire(steel, [...stem, ...j, [0, SLING_HOOK_Y - 0.35, SLING_HOOK_R], tip], SLING_WIRE);
  // the gate: a thinner sprung wire from the stem up to the inside of the tip
  sweptWire(gate, [[0, 2.3, zs + 0.12], [0, 2.9, -0.1], [0, SLING_HOOK_Y - 0.62, SLING_HOOK_R - 0.16]], 0.09, {sides: 6});
  // the folded webbing round the eye's bottom bar: up the front, over the bar, down the back, both layers sewn flat below
  const g = SLING_WIRE + SLING_WEB.t / 2 + 0.01, flat = SLING_WEB.t / 2 + 0.005, path = [];
  path.push([0, -SLING_FOLD, flat], [0, -1.4, flat], [0, -0.6, g * 0.85]);
  path.push(...arcYZ(0, 0, g, -20, 200).slice(0));
  path.push([0, -0.6, -g * 0.85], [0, -1.4, -flat], [0, -SLING_FOLD + 0.35, -flat]);
  const rings = path.map((q, i) => {
    const t = unit(sub3(path[Math.min(i + 1, path.length - 1)], path[Math.max(i - 1, 0)])), nrm = unit(cross3(t, [1, 0, 0]));
    return ringAt(web, q, nrm, [1, 0, 0], [[SLING_WEB.t / 2, SLING_WEB.w / 2], [SLING_WEB.t / 2, -SLING_WEB.w / 2], [-SLING_WEB.t / 2, -SLING_WEB.w / 2], [-SLING_WEB.t / 2, SLING_WEB.w / 2]]);
  });
  loft(web, rings, path);
  const pin = [0, SLING_HOOK_Y + SLING_HOOK_R - SLING_WIRE - SLING_SWIVEL_WIRE, 0];
  return {PARTS: [steel, gate, web], MATS: {
    web: {c: [84, 88, 66], ...NYLON},
    steel: {c: [62, 64, 66], hl: 14, sm: 120, se: 140, f0: 230},   // phosphated spring steel
  }, meshOnly: true, texelsPerPx: 24, points: {pin, exit: [0, -SLING_FOLD, 0]}};
}

/**
 * Sling strap texture (no item): a straight 6 cm piece of the webbing, 32 mm x 2.5 mm. The strap renderer reads the UV
 * rectangles of its two faces (+z / -z) and two edges (+x / -x) and tiles them, mirrored, along the simulated ribbon.
 */
function gunSlingStrap() {
  const B = 'gun_sling_strap', web = new Part('web', B, 'web'), w = SLING_WEB.w / 2, t = SLING_WEB.t / 2;
  extrude(web, 'y', {outer: orient([[-t, -w], [t, -w], [t, w], [-t, w]], true), holes: []}, 0, 6, 0);
  return {PARTS: [web], MATS: {web: {c: [84, 88, 66], ...NYLON}}, meshOnly: true, texelsPerPx: 24};
}

/**
 * Gun sling worn (no item, drawn with a slung primary on the back): the strap across the body, over the left shoulder
 * (where the muzzle rides) and down across the chest to the right hip, round the back. One closed band in the plane
 * through the torso that holds that diagonal.
 */
function gunSlingWorn() {
  const B = 'gun_sling_worn', web = new Part('strap', B, 'web');
  const d = unit([-6.9, -10.5, 0]), c = [-0.6, -5.4, 0];
  // lift 0.4: over every other strap and 0.04 clear of the chest rig's panel (it passes behind the pouches)
  strap(web, shellArc(c, d, [0, 0, 1], 0, 354, 6, TORSO, 0.4), TORSO, {width: 0.55, thick: 0.12, closed: true});
  return {PARTS: [web], MATS: {web: {c: [84, 88, 66], ...NYLON}}, meshOnly: true, worn: true};
}

/**
 * Pistol holster: a drop-leg holster on the outside of the right thigh. A moulded black shell (open at the top, cut back
 * at the rear for the grip), a nylon platform behind it, two leg straps round the thigh. Leg frame; the holstered pistol
 * is placed by the renderer with its muzzle 0.25 px above the shell's bottom and its slide's top against the shell's
 * front wall (CarriedWeaponsLayer: HOLSTER_* constants). The holstered pistol is drawn as large as in the hand in third
 * person (P9 6.06 px long, 3.6 px from slide top to grip bottom, 0.92 px wide; 2026-10-15: at its real length, 3.2 px,
 * it was hardly visible), and the shell is cut for that size as on a real holster: the front edge covers the slide to
 * about a fifth of its length from the rear, the rear wall (z 1.0, deep enough for slide, frame and trigger guard) stops
 * just below the grip, which leaves the holster to the rear. 1.3 px wide, so the wider Blackridge .50 (1.28 px) fits.
 */
export const HOLSTER = {mid: 3.25, bottom: -7.4, front: -1.05};
function pistolHolster() {
  const B = 'pistol_holster', parts = [];
  const plate = new Part('platform', B, 'nylon');
  extrude(plate, 'x', {outer: rr(-1.15, -7.6, 1.15, -1.9, 0.45), holes: []}, 2.28, 2.56, 0.08); parts.push(plate);
  const {mid, bottom, front} = HOLSTER, hw = 0.65;
  const outline = orient([[front, bottom + 0.35], [front + 0.35, bottom], [0.6, bottom], [1.0, bottom + 0.4], [1.0, -2.85], [0.3, -2.5], [front, -2.4]], true);
  const shell = new Part('shell', B, 'shell'); extrude(shell, 'x', {outer: outline, holes: []}, mid - hw, mid + hw, 0.14); parts.push(shell);
  for (const y of [-2.9, -6.3]) {
    const s = new Part('leg_strap_' + (y > -4 ? '1' : '2'), B, 'nylon');
    strap(s, shellArc([0, y, 0], [1, 0, 0], [0, 0, 1], 0, 352, 8, LEG, 0.07), LEG, {width: 0.6, thick: 0.14, closed: true});
    parts.push(s);
  }
  const drop = new Part('drop_strap', B, 'nylon');
  extrude(drop, 'x', {outer: rr(-0.35, -2.0, 0.35, -0.2, 0.1), holes: []}, 2.3, 2.48, 0); parts.push(drop);
  return {PARTS: parts, MATS: {
    shell: {c: [42, 44, 46], hl: 10, sm: 150, se: 150, f0: 22},     // moulded black kydex, semi-gloss
    nylon: {c: [50, 52, 50], ...NYLON},                             // black nylon platform and straps
  }, icon: {view: [10, 55, 0], coverage: 0.4, hide: ['leg_strap_1', 'leg_strap_2'], mat: {shell: [66, 68, 72], nylon: [100, 102, 98]}}, worn: true};
}

// Head frame (the ear muffs): origin at the head's pivot (neck), +X to the player's left, +Y up (head y 0..8), +Z to the
// front (head z -4..4); the hat layer reaches 4.5. Same turn about X as the body frame.
const EAR = {y: 3.3, z: -0.2};
/**
 * Simple hearing protection (2026-10-14, replaces the V1 cuboid model; same look): two leather-covered cups with a
 * wool cushion ring against the head, on a spring-steel headband that runs in a rounded rectangle over the head (clear
 * of the hat layer's corners) into a small yoke on top of each cup. The cushions sit 0.05 px off the head's sides (user,
 * 2026-10-15: the first cut stood clear of the hat layer and looked loose); a skin's hat layer at the ears is hidden
 * inside the cups.
 */
function simpleHearingProtection() {
  const B = 'simple_hearing_protection', parts = [];
  for (const side of [-1, 1]) {
    const cup = new Part('cup_' + (side < 0 ? 'r' : 'l'), B, 'leather');
    const prof = [[4.22, 0], [4.22, 1.42], [4.38, 1.58], [4.82, 1.56], [5.14, 1.34], [5.32, 0.86], [5.38, 0]].map(([x, r]) => [side * x, r]);
    revolve(cup, -EAR.z, EAR.y, orient(prof, true), 16); zToX(cup); parts.push(cup);
    const pad = new Part('cushion_' + (side < 0 ? 'r' : 'l'), B, 'wool');
    const ring = [[4.05, 0.78], [4.05, 1.38], [4.12, 1.5], [4.3, 1.52], [4.3, 0.78]].map(([x, r]) => [side * x, r]);
    revolve(pad, -EAR.z, EAR.y, orient(ring, true), 16); zToX(pad); parts.push(pad);
    const yoke = new Part('yoke_' + (side < 0 ? 'r' : 'l'), B, 'steel');
    extrude(yoke, 'x', {outer: rr(EAR.z - 0.32, 4.55, EAR.z + 0.32, 5.1, 0.1), holes: []}, side < 0 ? -5.05 : 4.55, side < 0 ? -4.55 : 5.05, 0.06);
    parts.push(yoke);
  }
  const BAND = {lo: [-3.4, 4.9, EAR.z], hi: [3.4, 7.3, EAR.z], r: 1.4};   // sides at |x| 4.8, top y 8.7 (hat layer 8.5)
  const band = new Part('headband', B, 'steel');
  strap(band, shellLine([[4.8, 4.9, EAR.z], [4.8, 7.3, EAR.z], [4.8, 8.7, EAR.z], [-4.8, 8.7, EAR.z], [-4.8, 7.3, EAR.z], [-4.8, 4.9, EAR.z]], 0.3, BAND, 0),
    BAND, {width: 0.55, thick: 0.16});
  parts.push(band);
  return {PARTS: parts, MATS: {
    leather: {c: [72, 52, 44], hl: 8, sm: 96, se: 104, f0: 20},     // dark brown leather cups
    wool: {c: [178, 168, 148], hl: 4, sm: 40, se: 50, f0: 20},      // undyed wool felt cushions
    steel: {c: [118, 120, 122], hl: 14, sm: 130, se: 150, f0: 230}, // spring-steel band, satin
  }, icon: {view: [15, 35, 0], coverage: 0.15, mat: {steel: [170, 172, 176]}}, worn: true};
}

/** Rounded rectangle with its own radius per corner (order as roundRect: u1v0, u1v1, u0v1, u0v0); r 0 = a sharp corner. */
function roundRectCorners(u0, v0, u1, v1, radii, seg = 3) {
  const out = [];
  [[u1, v0, -1, 1, -90], [u1, v1, -1, -1, 0], [u0, v1, 1, -1, 90], [u0, v0, 1, 1, 180]].forEach(([cu, cv, su, sv, a0], k) => {
    const r = radii[k];
    if (r < 1e-6) { out.push([cu, cv]); return; }
    for (let i = 0; i <= seg; i++) { const a = (a0 + 90 * i / seg) * Math.PI / 180; out.push([cu + su * r + r * Math.cos(a), cv + sv * r + r * Math.sin(a)]); }
  });
  return orient(out, true);
}

/** Copy src's vertices (through T, a translation or a proper rotation: windings stay right) and faces into dst. */
function mergePart(dst, src, T = p => p) {
  const o = dst.v.length;
  for (const p of src.v) dst.v.push(T(p));
  for (const f of src.f) dst.f.push({...f, ids: f.ids.map(i => i + o)});
}
/**
 * A prism along Z through rings of the same vertex count: rings[k] = [z, loop in (x, y)], segMats[k] the material between
 * ring k and k + 1 (two rings at one z make a flat step), the end caps capMats[0] / [1]. Faces point away from the origin,
 * so the solid is authored round its own centre (convex enough: every face sees out from there).
 */
function prismZ(part, rings, segMats, capMats) {
  const ids = rings.map(([z, L]) => L.map(([x, y]) => part.vtx([x, y, z])));
  for (let k = 0; k + 1 < rings.length; k++) for (let i = 0; i < ids[k].length; i++) {
    const j = (i + 1) % ids[k].length, q = [ids[k][i], ids[k][j], ids[k + 1][j], ids[k + 1][i]];
    const mid = mul3(q.reduce((s, id) => add3(s, part.v[id]), [0, 0, 0]), 0.25);
    part.face(q, mid, 'side', segMats[k]);
  }
  for (const [k, sgn, mat] of [[0, -1, capMats[0]], [rings.length - 1, 1, capMats[1]]]) {
    const {pts, tris} = triangulate(rings[k][1], []), cap = pts.map(([x, y]) => part.vtx([x, y, rings[k][0]]));
    for (const t of tris) part.face(t.map(i => cap[i]), [0, 0, sgn], 'cap', mat);
  }
}
/** Catmull-Rom points through ctrl (3D), n steps per span. */
function catmull(ctrl, n) {
  const P = [ctrl[0], ...ctrl, ctrl[ctrl.length - 1]], out = [ctrl[0]];
  for (let s = 1; s + 2 < P.length; s++) for (let k = 1; k <= n; k++) {
    const t = k / n, [p0, p1, p2, p3] = [P[s - 1], P[s], P[s + 1], P[s + 2]];
    out.push(p1.map((_, i) => 0.5 * (2 * p1[i] + (-p0[i] + p2[i]) * t + (2 * p0[i] - 5 * p1[i] + 4 * p2[i] - p3[i]) * t * t + (-p0[i] + 3 * p1[i] - 3 * p2[i] + p3[i]) * t * t * t)));
  }
  return out;
}
/** A round wire (6 sides, radius r) along pts, both ends capped. */
function wire(part, pts, r, mat) {
  const rings = pts.map((p, i) => {
    const T = unit(sub3(pts[Math.min(i + 1, pts.length - 1)], pts[Math.max(i - 1, 0)]));
    const n = unit(cross3(T, Math.abs(T[0]) < 0.9 ? [1, 0, 0] : [0, 1, 0])), b = cross3(T, n);
    return ringAt(part, p, n, b, Array.from({length: 6}, (_, k) => [r * Math.cos(k * Math.PI / 3), r * Math.sin(k * Math.PI / 3)]));
  });
  loft(part, rings, pts, {mat: () => mat});
}

/**
 * 9 V battery (item inspect V1, 2026-10-22, docs/gameplay/item_inspect_v1_design.md section 4): IEC 6LR61 / 6LF22 size,
 * 48.5 x 26.5 x 17.5 mm at 1 px = 10 mm. Lies flat round its centre, the broad faces up / down (Y), the snap terminals
 * toward -Z: the round positive stud at x -0.635, the hex negative socket at x +0.635 (12.7 mm apart, as the standard).
 * A printed film wrap rolled over nickel end plates, a coloured band near the bottom end: alkaline dark grey with an amber
 * band, NiMH green with a light grey band. No printed text.
 */
export const BATTERY_9V = {W: 2.65, H: 1.75, L: 4.85, pitch: 0.635};
function battery9vPart(part, kind) {
  const {W, H, L, pitch} = BATTERY_9V, wrap = 'wrap_' + kind, band = 'band_' + kind, z = L / 2;
  const loop = d => rr(-W / 2 + d, -H / 2 + d, W / 2 - d, H / 2 - d, 0.22 - d);
  // the nickel top plate (terminals), the wrap's rolled edges, the band at the bottom end, the nickel bottom plate
  prismZ(part, [[-z, loop(0.06)], [-z + 0.095, loop(0.06)], [-z + 0.095, loop(0.01)], [-z + 0.145, loop(0)], [z - 1.05, loop(0)], [z - 0.55, loop(0)],
    [z - 0.145, loop(0)], [z - 0.095, loop(0.01)], [z - 0.095, loop(0.06)], [z, loop(0.06)]],
    ['nickel', wrap, wrap, wrap, band, wrap, wrap, wrap, 'nickel'], ['nickel', 'nickel']);
  const t = -z - 0.3, pos = new Part('pos', part.bone, 'nickel'), neg = new Part('neg', part.bone, 'nickel');
  // positive: a round stud with a waist; negative: a hex with a round socket (dark inside)
  revolve(pos, -pitch, 0, [[-z + 0.02, 0], [-z + 0.02, 0.25], [t + 0.22, 0.25], [t + 0.16, 0.22], [t + 0.1, 0.26], [t + 0.035, 0.24], [t, 0.17], [t, 0]], 12);
  revolve(neg, pitch, 0, [[-z + 0.02, 0], [-z + 0.02, 0.34], [t, 0.34], [t, 0.16], [t + 0.125, 0.16], [t + 0.125, 0]], 6);
  for (const f of neg.f) if (f.ids.every(i => Math.hypot(neg.v[i][0] - pitch, neg.v[i][1]) < 0.161)) f.mat = 'socket';
  mergePart(part, pos); mergePart(part, neg);
  return part;
}
const BATTERY_MATS = {
  wrap_alk: {c: [30, 32, 35], hl: 6, sm: 128, se: 138, f0: 20},     // printed film wrap, satin
  band_alk: {c: [192, 138, 46], hl: 6, sm: 128, se: 138, f0: 20},   // amber band
  wrap_nimh: {c: [47, 74, 60], hl: 6, sm: 128, se: 138, f0: 20},    // green wrap
  band_nimh: {c: [185, 188, 184], hl: 6, sm: 128, se: 138, f0: 20}, // light grey band
  nickel: {c: [166, 168, 171], hl: 10, sm: 160, se: 170, f0: 230},  // nickel-plated steel ends and terminals
  socket: {c: [42, 43, 45], hl: 2, sm: 80, se: 90, f0: 20},         // inside the negative socket
};
const battery9v = kind => () => ({PARTS: [battery9vPart(new Part('battery', 'battery_9v_' + (kind === 'alk' ? 'alkaline' : 'nimh'), 'wrap_' + kind), kind)],
  MATS: BATTERY_MATS,
  // pinched at the bottom end (baked (0, 0, 1.9)), landing where the compass's south rim lands (the wiring wrench's grip
  // points): translation = landing - scale * R * grip; ground flat; item frame broad face out, terminals up
  display: {
    thirdperson_righthand: {rotation: [90, 0, 0], translation: [0, -0.9 + 0.55 * 1.9, 1.37], scale: [0.55, 0.55, 0.55]},
    firstperson_righthand: {rotation: [40, -13, 12], translation: sub3(add3([1.616, 0.893, 0.848], mul3(rotXYZ([40, -13, 12], [0, 0, 3.0]), 0.72)), mul3(rotXYZ([40, -13, 12], [0, 0, 1.9]), 0.72)).map(v => +v.toFixed(3)), scale: [0.72, 0.72, 0.72]},
    ground: {rotation: [0, 0, 0], translation: [0, 2, 0], scale: [0.5, 0.5, 0.5]},
    fixed: {rotation: [-90, 180, 0], translation: [0, 0, 0], scale: [1.2, 1.2, 1.2]},
  },
  icon: {view: [35, 30, 0], coverage: 0.3, mat: kind === 'alk' ? {wrap_alk: [52, 55, 60], band_alk: [222, 160, 54], nickel: [196, 198, 202]} : {wrap_nimh: [62, 100, 80], band_nimh: [214, 217, 212], nickel: [196, 198, 202]}}});

/** Seven-segment layout of one LCD digit (a..g) and its decimal point, shared with the HUD (client/GeigerReadout). */
export const GEIGER_LCD = {cells: 4, x0: -2.62, pitch: 1.31, glyph: 0.98, zTop: -3.82, zBottom: -1.38, bar: 0.24, gap: 0.035, dp: 0.24};
/**
 * The counter's 9 V supply (item inspect V1, 2026-10-22): the bay opening in the back (x, z) and its floor height, the fitted
 * battery's centre, the LCD battery icon's frame (x, z on the LCD) and line width. The inspect screen's hotspots and the
 * cover's aside pose are worked out from these (client/inspect/InspectProfile).
 */
export const GEIGER_POWER = {BAY: {x0: -1.6, x1: 1.6, z0: -0.35, z1: 5.35, floor: 2.1}, bat: [0, 1.1, 2.75],
  ICON: {x0: 2.42, x1: 2.88, z0: -3.6, z1: -1.45, t: 0.11}};
/**
 * Geiger counter V2 (direction B, 2026-10-15; docs/models/geiger_counter_v2_design.md): a digital pocket monitor after the
 * Inspector class of thin-window counters, 150 x 80 x 30 mm at 1 px = 10 mm. A charcoal ABS shell between two yellow
 * rubber end boots; on the front a gloss black bezel round a 4-digit LCD (only the reading: seven-segment digits and
 * decimal points, each its own part, lit by the item renderer from the live reading; no printed text), the red count LED
 * above it, three rubber keys below; the power slider on the right side, a steel lanyard ring at the bottom boot; on the
 * back the stainless grille over the mica window of the pancake tube and two case screws. Lies flat, face up (+Y), top
 * end toward -Z; digits read left to right along +X with their tops toward -Z.
 */
function geigerCounter() {
  const B = 'geiger_counter', parts = [], add = p => { parts.push(p); return p; };
  const plan = (u0, v0, u1, v1, r) => rr(u0, v0, u1, v1, r);   // axis y: (u, v) = (z, x)
  const shellPlan = plan(-6.4, -3.85, 6.4, 3.85, 0.9), shell = add(new Part('shell', B, 'shell'));
  extrude(shell, 'y', {outer: shellPlan, holes: []}, 0.12, 2.88, 0.35);
  // the battery bay in the back (item inspect V1): the back cap rebuilt round the opening (the chamfered outline is the
  // extrusion's first ring), the bay a pocket BAY.floor deep with two moulded ribs on its floor
  const {BAY} = GEIGER_POWER, bayLoop = plan(BAY.z0, BAY.x0, BAY.z1, BAY.x1, 0.2), backY = 0.12;
  shell.f = shell.f.filter(f => !(f.tag === 'cap' && f.ids.every(i => Math.abs(shell.v[i][1] - backY) < 1e-9)));
  const backOuter = shell.v.slice(0, shellPlan.length).map(([x, , z]) => [z, x]);
  { const {pts, tris} = triangulate(backOuter, [orient(bayLoop, false)]), ids = pts.map(([u, v]) => shell.vtx([v, backY, u]));
    for (const t of tris) shell.face(t.map(i => ids[i]), [0, -1, 0], 'cap'); }
  const bay = add(new Part('bay', B, 'shell')), lip = bayLoop.map(([u, v]) => bay.vtx([v, backY, u])), deep = bayLoop.map(([u, v]) => bay.vtx([v, BAY.floor, u]));
  const bayMid = [0, 1, (BAY.z0 + BAY.z1) / 2];
  for (let i = 0; i < bayLoop.length; i++) { const j = (i + 1) % bayLoop.length, q = [lip[i], lip[j], deep[j], deep[i]];
    bay.face(q, sub3(bayMid, mul3(q.reduce((s, id) => add3(s, bay.v[id]), [0, 0, 0]), 0.25)), 'wall'); }
  { const {pts, tris} = triangulate(bayLoop, []), ids = pts.map(([u, v]) => bay.vtx([v, BAY.floor, u]));
    for (const t of tris) bay.face(t.map(i => ids[i]), [0, -1, 0], 'wall'); }
  for (const x of [-1.1, 1.1]) extrude(bay, 'z', {outer: rr(x - 0.09, BAY.floor - 0.12, x + 0.09, BAY.floor + 0.005, 0.03), holes: []}, 0.3, 4.9, 0, 'wall');
  // end boots: rounded outer corners, square where they meet the shell
  extrude(add(new Part('boot_top', B, 'rubber')), 'y', {outer: roundRectCorners(-7.5, -4.0, -5.6, 4.0, [0.12, 0.12, 1.4, 1.4], 4), holes: []}, 0, 3.0, 0.4);
  extrude(add(new Part('boot_bottom', B, 'rubber')), 'y', {outer: roundRectCorners(5.6, -4.0, 7.5, 4.0, [1.4, 1.4, 0.12, 0.12], 4), holes: []}, 0, 3.0, 0.4);
  // front: bezel with the LCD window, the LCD face 0.08 below the bezel, glass in the window
  extrude(add(new Part('bezel', B, 'bezel')), 'y', {outer: plan(-4.75, -3.3, -0.35, 3.3, 0.45), holes: [orient(roundRect(-4.35, -2.9, -0.75, 2.9, 0.25), false)]}, 2.78, 2.98, 0);
  extrude(add(new Part('lcd', B, 'lcd')), 'y', {outer: plan(-4.42, -2.97, -0.68, 2.97, 0.28), holes: []}, 2.8, 2.9, 0);
  extrude(add(new Part('lcd_glass', B, 'glass')), 'y', {outer: plan(-4.45, -3.0, -0.65, 3.0, 0.3), holes: []}, 2.935, 2.965, 0);
  // seven-segment digits: one flat face each, 0.02 px over the LCD face (the glass is 0.015 above them)
  const L = GEIGER_LCD, ySeg = 2.92, mid = (L.zTop + L.zBottom) / 2, h = L.bar / 2, g = L.gap;
  const quad = (name, pts) => { const p = add(new Part(name, B, 'segment')); const ids = pts.map(([x, z]) => p.vtx([x, ySeg, z])); p.face(ids, [0, 1, 0], 'cap'); };
  // a horizontal bar from x0 to x1 at z (pointed ends) and a vertical bar from z0 to z1 at x
  const hBar = (x0, x1, z) => [[x0 + g, z], [x0 + h + g, z - h], [x1 - h - g, z - h], [x1 - g, z], [x1 - h - g, z + h], [x0 + h + g, z + h]];
  const vBar = (x, z0, z1) => [[x, z0 + g], [x + h, z0 + h + g], [x + h, z1 - h - g], [x, z1 - g], [x - h, z1 - h - g], [x - h, z0 + h + g]];
  for (let d = 0; d < L.cells; d++) {
    const x0 = L.x0 + d * L.pitch + h, x1 = x0 + L.glyph - L.bar, zt = L.zTop + h, zb = L.zBottom - h;
    const seg = {a: hBar(x0, x1, zt), b: vBar(x1, zt, mid), c: vBar(x1, mid, zb), d: hBar(x0, x1, zb), e: vBar(x0, mid, zb), f: vBar(x0, zt, mid), g: hBar(x0, x1, mid)};
    for (const [k, pts] of Object.entries(seg)) {
      // hexagon as a quad and two end triangles (Blockbench meshes take faces of 3 or 4 corners)
      const p = add(new Part(`seg_${d}_${k}`, B, 'segment')), ids = pts.map(([x, z]) => p.vtx([x, ySeg, z]));
      for (const f of [[ids[0], ids[1], ids[5]], [ids[1], ids[2], ids[4], ids[5]], [ids[2], ids[3], ids[4]]]) p.face(f, [0, 1, 0], 'cap');
    }
    if (d < L.cells - 1) { const cx = L.x0 + d * L.pitch + (L.glyph + L.pitch) / 2, cz = L.zBottom - L.dp / 2;   // in the gap after the digit
      quad(`dp_${d}`, [[cx - L.dp / 2, cz - L.dp / 2], [cx + L.dp / 2, cz - L.dp / 2], [cx + L.dp / 2, cz + L.dp / 2], [cx - L.dp / 2, cz + L.dp / 2]]); }
  }
  // count LED (dark when idle; the renderer swaps in led_on, the same shape, full bright, for each count)
  for (const [name, mat] of [['led', 'led'], ['led_on', 'led_on']])
    extrude(add(new Part(name, B, mat)), 'y', {outer: orient(circle(-5.15, 2.6, 0.22, 10), true), holes: []}, 2.8, 3.02, 0);
  // three keys under the LCD
  for (const [i, x] of [-2.15, 0, 2.15].entries())
    extrude(add(new Part('key_' + i, B, 'key')), 'y', {outer: plan(1.05, x - 0.78, 2.0, x + 0.78, 0.3), holes: []}, 2.75, 3.12, 0.1);
  // power slider on the right side
  extrude(add(new Part('power_switch', B, 'key')), 'x', {outer: rr(-3.0, 1.1, -2.0, 1.9, 0.18), holes: []}, 3.72, 4.05, 0.06);
  // lanyard ring through the bottom boot
  extrude(add(new Part('lanyard_ring', B, 'steel')), 'y', {outer: plan(7.3, -3.35, 8.2, -2.15, 0.3), holes: [orient(roundRect(7.55, -3.08, 7.95, -2.42, 0.12), false)]}, 1.33, 1.67, 0);
  // back: the grille over the window (holes show the mica below), two case screws
  const holes = [];
  for (let j = -2; j <= 2; j++) for (let i = -2; i <= 2; i++) {
    const x = i * 0.62 + (j & 1 ? 0.31 : 0), z = -2.6 + j * 0.54;
    if (Math.hypot(x, z + 2.6) < 1.55) holes.push(orient(circle(z, x, 0.17, 8), false));
  }
  extrude(add(new Part('grille', B, 'grille')), 'y', {outer: orient(circle(-2.6, 0, 2.05, 24), true), holes}, 0.03, 0.14, 0);
  extrude(add(new Part('mica', B, 'mica')), 'y', {outer: orient(circle(-2.6, 0, 1.9, 24), true), holes: []}, 0.07, 0.11, 0);
  // one case screw at the top end (the bottom one gave way to the battery bay)
  extrude(add(new Part('screw_top', B, 'steel')), 'y', {outer: orient(circle(-4.9, 0, 0.2, 8), true), holes: []}, 0.06, 0.13, 0);
  // the slide cover over the bay: a plate a little larger than the opening, five thumb ribs at its bottom end, a lip at
  // its top end hooking under the case (drawn only while shut; the inspect screen lays it aside when open)
  const cover = add(new Part('cover', B, 'cover'));
  extrude(cover, 'y', {outer: plan(BAY.z0 - 0.1, BAY.x0 - 0.15, BAY.z1 + 0.1, BAY.x1 + 0.15, 0.3), holes: []}, 0.03, 0.115, 0);
  for (let i = 0; i < 5; i++) extrude(cover, 'y', {outer: plan(4.55 - i * 0.28, -1.2, 4.65 - i * 0.28, 1.2, 0.04), holes: []}, 0, 0.035, 0);
  extrude(cover, 'y', {outer: plan(-0.3, -1.5, -0.05, 1.5, 0.06), holes: []}, 0.11, 0.3, 0);
  // the battery snap and its two leads into the case wall, on the battery (snap_on) and loose in the empty bay (snap_off)
  const snap = new Part('snap', B, 'snap');
  extrude(snap, 'z', {outer: rr(-1.15, -0.525, 1.15, 0.525, 0.25), holes: []}, -0.25, 0.25, 0.06);
  // contacts on its face: the hex clip that takes the battery's round positive, the round stud into its hex negative
  const clip = new Part('clip', B, 'nickel'), stud = new Part('stud', B, 'nickel'), {pitch} = BATTERY_9V;
  revolve(clip, -pitch, 0, [[0.3, 0], [0.3, 0.2], [0.36, 0.2], [0.36, 0.3], [0.2, 0.3], [0.2, 0]], 6);
  revolve(stud, pitch, 0, [[0.38, 0], [0.38, 0.12], [0.34, 0.15], [0.2, 0.15], [0.2, 0]], 10);
  mergePart(snap, clip); mergePart(snap, stud);
  for (const [x, mat] of [[-0.45, 'lead_red'], [0.45, 'lead_black']]) {
    const s = Math.sign(x);
    wire(snap, catmull([[x, 0.15, -0.15], [x * 1.4, 0.55, -0.45], [s * 1.08, 0.9, -0.35], [s * 1.2, 0.95, -0.55]], 4), 0.11, mat);
  }
  const {bat} = GEIGER_POWER, snapOn = add(new Part('snap_on', B, 'snap')), snapOff = add(new Part('snap_off', B, 'snap'));
  mergePart(snapOn, snap, ([x, y, z]) => [x, y + bat[1], z]);
  { const c = Math.cos(-0.5), s = Math.sin(-0.5);   // tipped back against the bay floor
    mergePart(snapOff, snap, ([x, y, z]) => [x, y * c - z * s + bat[1] + 0.28, y * s + z * c + 0.35]); }
  snapOff.copyOf = snapOn;   // the same snap: one set of UVs
  // the fitted battery is the battery item's own mesh, drawn by the renderer at points.battery (its centre)
  // the LCD's battery icon in the blank right of the digits: frame with its + nub, three bars (lit like the segments)
  const {ICON} = GEIGER_POWER, t = ICON.t, frame = add(new Part('bat_frame', B, 'segment'));
  const flat = (p, x0, x1, z0, z1) => p.face([[x0, z0], [x1, z0], [x1, z1], [x0, z1]].map(([x, z]) => p.vtx([x, ySeg, z])), [0, 1, 0], 'cap');
  flat(frame, ICON.x0, ICON.x1, ICON.z0, ICON.z0 + t); flat(frame, ICON.x0, ICON.x1, ICON.z1 - t, ICON.z1);
  flat(frame, ICON.x0, ICON.x0 + t, ICON.z0 + t, ICON.z1 - t); flat(frame, ICON.x1 - t, ICON.x1, ICON.z0 + t, ICON.z1 - t);
  flat(frame, (ICON.x0 + ICON.x1) / 2 - 0.08, (ICON.x0 + ICON.x1) / 2 + 0.08, ICON.z0 - 0.14, ICON.z0);
  { const iz0 = ICON.z0 + t + 0.08, iz1 = ICON.z1 - t - 0.08, gap = 0.08, bh = (iz1 - iz0 - 2 * gap) / 3;
    for (let i = 0; i < 3; i++) flat(add(new Part('bat_bar_' + i, B, 'segment')), ICON.x0 + t + 0.07, ICON.x1 - t - 0.07, iz1 - (i + 1) * bh - i * gap, iz1 - i * bh - i * gap); }
  return {PARTS: parts, MATS: {
    nickel: BATTERY_MATS.nickel,
    cover: {c: [44, 46, 49], hl: 6, sm: 96, se: 110, f0: 22},        // the bay cover: the shell's ABS a shade darker, matte (reads as its own part)
    snap: {c: [24, 25, 27], hl: 6, sm: 110, se: 120, f0: 20},        // black PVC snap
    lead_red: {c: [176, 48, 42], hl: 4, sm: 120, se: 130, f0: 20},   // red lead
    lead_black: {c: [27, 28, 30], hl: 4, sm: 120, se: 130, f0: 20},  // black lead
    shell: {c: [54, 56, 59], hl: 8, sm: 112, se: 132, f0: 22},       // charcoal ABS, satin
    rubber: {c: [198, 152, 42], hl: 6, sm: 70, se: 82, f0: 20},      // yellow rubber boots
    bezel: {c: [24, 25, 27], hl: 6, sm: 176, se: 186, f0: 22},       // gloss black bezel
    lcd: {c: [152, 162, 140], hl: 2, sm: 124, se: 124, f0: 20},      // unlit LCD face
    segment: {c: [22, 25, 20], hl: 0, sm: 124, se: 124, f0: 20},     // lit segments
    glass: {...GLASS, c: [168, 178, 184], alpha: 45},                // clear acrylic window: faint, so the digits keep their contrast
    key: {c: [64, 66, 68], hl: 6, sm: 62, se: 72, f0: 20},           // dark silicone keys
    led: {c: [92, 24, 20], hl: 4, sm: 200, se: 210, f0: 22},         // red LED lens, dark
    led_on: {c: [255, 76, 54], hl: 0, sm: 200, se: 210, f0: 22},     // red LED lens, lit (drawn full bright)
    steel: {c: [118, 120, 124], hl: 12, sm: 130, se: 150, f0: 230},  // stainless ring and screws
    grille: {c: [128, 130, 134], hl: 10, sm: 112, se: 132, f0: 230}, // perforated stainless grille
    mica: {c: [86, 66, 40], hl: 2, sm: 214, se: 214, f0: 24},        // mica end window
  },
  // third person: in the fist, top end forward and a little up, face up; first person: face turned to the eye, top end
  // leaning out; ground: flat on its back; item frame: face out, top end up
  display: {
    thirdperson_righthand: {rotation: [110, 0, 0], translation: [0, 2, 1], scale: [0.4, 0.4, 0.4]},
    firstperson_righthand: {rotation: [55, -13, 21], translation: [-1, 3, 0.5], scale: [0.45, 0.45, 0.45]},
    fixed: {rotation: [-90, 180, 0], translation: [0, 0, 0], scale: [0.56, 0.56, 0.56]},
  },
  // the bay's inside and the snap are seen only with the cover open, close up in the inspect screen: half density
  islandScale: p => p.name === 'bay' || p.name.startsWith('snap_') ? 0.5 : 1, points: {battery: GEIGER_POWER.bat},
  atlas: 512, icon: {view: [60, 0, 0], coverage: 0.4, xray: ['lcd_glass'], hide: parts.filter(p => /^(seg_|dp_|led_on|bat_|snap_)/.test(p.name)).map(p => p.name),
    mat: {rubber: [226, 176, 52], shell: [70, 72, 76], lcd: [168, 178, 156]}}};
}

/** Turn a lathed part (revolve builds along Z) to stand on Y: the profile's z becomes the height. */
const zToY = part => { part.v = part.v.map(([x, y, z]) => [x, z, -y]); };
/** A straight bar in the (z, x) plane of an axis-y extrusion, from a to b ([x, z] points), w wide. */
function barPlan([x0, z0], [x1, z1], w) {
  const d = unit([x1 - x0, 0, z1 - z0]), nx = -d[2] * w / 2, nz = d[0] * w / 2;
  return orient([[z0 + nz, x0 + nx], [z1 + nz, x1 + nx], [z1 - nz, x1 - nx], [z0 - nz, x0 - nx]], true);
}
/** Card letters as strokes in a 0..1 high glyph box (x right, y up), read with their tops toward the card's rim. */
const GLYPHS = {
  N: {w: 0.68, strokes: [[[0, 0], [0, 1]], [[0, 1], [0.68, 0]], [[0.68, 0], [0.68, 1]]]},
  E: {w: 0.56, strokes: [[[0, 0], [0, 1]], [[0, 1], [0.56, 1]], [[0, 0.5], [0.46, 0.5]], [[0, 0], [0.56, 0]]]},
  S: {w: 0.62, strokes: [[[0.6, 0.86], [0.44, 1], [0.16, 1], [0.02, 0.86], [0.02, 0.64], [0.16, 0.52], [0.46, 0.48], [0.6, 0.36], [0.6, 0.14], [0.46, 0], [0.16, 0], [0, 0.14]]]},
  W: {w: 0.8, strokes: [[[0, 1], [0.2, 0], [0.4, 0.62], [0.6, 0], [0.8, 1]]]},
};

/**
 * Pocket compass (clips, user 2026-10-21: "完善罗盘的模型……要一次性做精致", PBR, the dial really turning): a liquid-free
 * card compass after the common olive-drab marching compasses, 56 mm across at 1 px = 10 mm, lying face up (+Y), north
 * end (the bail) toward -Z. A lathed olive coated aluminium case (bevelled base, the rim a hair below the bezel: the seam
 * reads as a dark line), a black rotating bezel with 30 grip notches, white marks every 30 degrees and an orange luminous
 * index at north; under the glass the fixed red lubber line on the case wall at north and the floating card: a cream disc
 * with ticks every 10 degrees (long every 30), N E S W as raised print (N red) read toward the rim, a red north arrow and
 * a white south tail, a brass pivot cap. Every card part is named card_*: CompassItemRenderer turns them about the pivot
 * so the card's N points to the world's north (-Z). A lug on the north wall carries a stainless steel ring.
 */
export const COMPASS = {caseR: 2.8, cardR: 2.12, cardY: 0.62};
function compass() {
  const B = 'compass', parts = [], add = p => { parts.push(p); return p; };
  // case: a cup, outer wall bevelled at the base, the floor under the card at 0.35, the rim top at 0.98
  const kase = add(new Part('case', B, 'case'));
  revolve(kase, 0, 0, [[0.35, 0], [0.35, 2.45], [0.98, 2.45], [0.98, 2.78], [0.18, 2.8], [0, 2.58], [0, 0]], 40);
  zToY(kase);
  // bezel: notched outer edge (30 notches), its hole over the window
  const notch = [];
  for (let i = 0; i < 60; i++) { const a = 2 * Math.PI * i / 60, r = i % 2 ? 2.8 : 2.9; notch.push([r * Math.cos(a), r * Math.sin(a)]); }
  extrude(add(new Part('bezel', B, 'bezel')), 'y', {outer: orient(notch, true), holes: [orient(circle(0, 0, 2.18, 40), false)]}, 1.0, 1.36, 0.06);
  // bezel marks every 30 degrees (raised white print) and the luminous index at north
  for (let d = 30; d < 360; d += 30) {
    const a = d * Math.PI / 180, i0 = [2.3 * Math.sin(a), -2.3 * Math.cos(a)], i1 = [2.66 * Math.sin(a), -2.66 * Math.cos(a)];
    extrude(add(new Part('bezel_mark_' + d, B, 'mark')), 'y', {outer: barPlan(i0, i1, 0.09), holes: []}, 1.36, 1.38, 0);
  }
  extrude(add(new Part('bezel_index', B, 'lume')), 'y', {outer: orient([[-2.72, -0.2], [-2.72, 0.2], [-2.3, 0]], true), holes: []}, 1.36, 1.385, 0);
  // the glass under the bezel's lip
  extrude(add(new Part('glass', B, 'glass')), 'y', {outer: orient(circle(0, 0, 2.2, 40), true), holes: []}, 1.12, 1.2, 0);
  // fixed lubber line on the case wall at north, over the card's rim
  extrude(add(new Part('lubber', B, 'red')), 'y', {outer: orient(roundRect(-2.44, -0.045, -2.16, 0.045, 0.02, 1), true), holes: []}, 0.6, 0.97, 0);
  // the card: disc, ticks, letters, arrow, pivot cap (all turned together)
  const {cardR, cardY} = COMPASS, top = cardY + 0.06;
  extrude(add(new Part('card_disc', B, 'dial')), 'y', {outer: orient(circle(0, 0, cardR, 40), true), holes: []}, cardY, top, 0);
  for (let d = 0; d < 360; d += 10) {
    const a = d * Math.PI / 180, r0 = d % 30 ? 1.82 : 1.64, rr0 = [r0 * Math.sin(a), -r0 * Math.cos(a)], rr1 = [2.04 * Math.sin(a), -2.04 * Math.cos(a)];
    extrude(add(new Part('card_tick_' + d, B, 'ink')), 'y', {outer: barPlan(rr0, rr1, d % 30 ? 0.045 : 0.07), holes: []}, top, top + 0.012, 0);
  }
  const H = 0.46, RL = 1.2;
  for (const [ch, d] of [['N', 0], ['E', 90], ['S', 180], ['W', 270]]) {
    const g = GLYPHS[ch], a = d * Math.PI / 180, up = [Math.sin(a), -Math.cos(a)], right = [Math.cos(a), Math.sin(a)];
    const at = ([gx, gy]) => { const u = (gx - g.w / 2) * H, v = (gy - 0.5) * H; return [RL * up[0] + u * right[0] + v * up[0], RL * up[1] + u * right[1] + v * up[1]]; };
    // each stroke a little higher than the one before and sunk a little deeper into the card: where strokes cross,
    // neither their tops nor their buried bottoms share a plane
    let n = 0;
    g.strokes.forEach((line, si) => line.slice(1).forEach((q, k) => {
      extrude(add(new Part(`card_${ch.toLowerCase()}_${si}_${k}`, B, ch === 'N' ? 'red' : 'ink')), 'y', {outer: barPlan(at(line[k]), at(q), 0.075), holes: []}, top - 0.003 * (n + 1), top + 0.014 + 0.002 * n++, 0);
    }));
  }
  extrude(add(new Part('card_arrow', B, 'red')), 'y', {outer: orient([[-0.82, 0], [0, 0.17], [0, -0.17]], true), holes: []}, top, top + 0.02, 0);
  extrude(add(new Part('card_tail', B, 'needle')), 'y', {outer: orient([[0.62, 0], [0, -0.13], [0, 0.13]], true), holes: []}, top, top + 0.02, 0);
  const cap = add(new Part('card_pivot', B, 'brass'));
  revolve(cap, 0, 0, [[top + 0.008, 0], [top + 0.008, 0.17], [top + 0.09, 0.13], [top + 0.13, 0]], 10);  // seated into the needle
  zToY(cap);
  // lug on the north wall and the ring through it
  extrude(add(new Part('lug', B, 'case')), 'y', {outer: orient(roundRect(-3.2, -0.5, -2.55, 0.5, 0.18), true), holes: []}, 0.3, 0.9, 0.06);
  extrude(add(new Part('ring', B, 'steel')), 'y', {outer: orient(circle(-3.62, 0, 0.6, 16), true), holes: [orient(circle(-3.62, 0, 0.4, 16), false)]}, 0.5, 0.7, 0);
  // the same ring swung up on its lug (round wire, standing across the case): drawn instead of ring while the compass
  // hangs from a clip (ClipRenderer), so it hooks over the carabiner's bottom bar
  const RP = [];
  for (let i = 0; i <= 24; i++) { const a = 2 * Math.PI * i / 24; RP.push([0, 0.6 + 0.5 * Math.sin(a), -3.62 + 0.5 * Math.cos(a)]); }
  const ringUp = add(new Part('ring_up', B, 'steel')), rr2 = [];
  for (let i = 0; i < RP.length - 1; i++) {
    const t = unit(sub3(RP[(i + 1) % (RP.length - 1)], RP[(i - 1 + RP.length - 1) % (RP.length - 1)])), n = [0, t[2], -t[1]];
    rr2.push(ringAt(ringUp, RP[i], n, [1, 0, 0], hexSection(0.1, 0.1)));
  }
  loft(ringUp, rr2, RP.slice(0, -1), {closed: true});
  return {PARTS: parts, MATS: {
    case: {c: [76, 80, 62], hl: 10, sm: 118, se: 140, f0: 24},       // olive drab coated aluminium, satin
    bezel: {c: [34, 35, 36], hl: 12, sm: 150, se: 160, f0: 24},      // black anodised bezel
    mark: {c: [216, 212, 200], hl: 0, sm: 96, se: 100, f0: 20},      // white bezel print
    lume: {c: [226, 152, 62], hl: 0, sm: 110, se: 120, f0: 20},      // orange luminous index
    glass: {...GLASS, c: [190, 204, 210], alpha: 46},                // clear crystal: faint, the card keeps its contrast
    red: {c: [182, 44, 36], hl: 0, sm: 110, se: 120, f0: 20},        // red print: N, north arrow, lubber line
    dial: {c: [226, 220, 200], hl: 2, sm: 96, se: 100, f0: 20},      // cream card
    ink: {c: [26, 26, 24], hl: 0, sm: 96, se: 100, f0: 20},          // black print
    needle: {c: [236, 234, 226], hl: 0, sm: 110, se: 120, f0: 20},   // white south tail
    brass: {c: [176, 140, 70], hl: 10, sm: 150, se: 170, f0: 230},   // brass pivot cap
    steel: {c: [150, 152, 156], hl: 12, sm: 130, se: 150, f0: 230},  // stainless ring
  },
  // third person: flat, face up, north end (the bail) forward; first person: face turned up to the eye; both pinched at
  // the south rim (baked (0, 0, 3.0)), which lands on the wiring wrench's grip points (docs/models/equipment_meshes_v1.md
  // "手持适配规则"; user 2026-10-21: the first transform stood the compass up 2 px in front of the fist): translation =
  // landing - scale * R * grip. Ground: on its back; item frame: face out, north up; inventory: the live mesh
  // (CompassItemRenderer turns the card there too)
  display: {
    thirdperson_righthand: {rotation: [90, 0, 0], translation: [0, 0.6, 1.37], scale: [0.5, 0.5, 0.5]},
    firstperson_righthand: {rotation: [40, -13, 12], translation: [1.616, 0.893, 0.848], scale: [0.72, 0.72, 0.72]},
    ground: {rotation: [0, 0, 0], translation: [0, 2, 0], scale: [0.5, 0.5, 0.5]},
    fixed: {rotation: [-90, 180, 0], translation: [0, 0, 0], scale: [1.1, 1.1, 1.1]},
  },
  pivot: [0, 0, 0], atlas: 512, icon: {view: [60, 0, 0], coverage: 0.4, live: true, xray: ['glass'], hide: ['ring_up'], mat: {case: [94, 98, 78], bezel: [44, 45, 46], dial: [240, 234, 214]}}};
}

/**
 * A clip on the belt (worn, no item; ClipRenderer draws it at each clip): a black webbing tab sewn over the belt with a
 * steel wire-gate carabiner hanging from it, authored at body scale in the body frame (+X the player's left, +Y up, +Z out
 * of the body), the tab's top centre at the origin. The clipped thing hangs from the carabiner's bottom (y -1.15).
 */
function clipMount() {
  const B = 'clip_mount', parts = [];
  const tab = new Part('tab', B, 'web');
  extrude(tab, 'z', {outer: orient(roundRect(-0.38, -0.62, 0.38, 0, 0.08), true), holes: []}, 0, 0.12, 0);
  parts.push(tab);
  // carabiner: a D of round wire through the tab's bottom loop
  const P = [];
  for (let i = 0; i <= 28; i++) { const a = 2 * Math.PI * i / 28; P.push([0.2 * Math.sin(a), -0.89 + 0.27 * Math.cos(a), 0.16]); }
  const wire = new Part('carabiner', B, 'steel'), rings = [];
  for (let i = 0; i < P.length - 1; i++) {
    const t = unit(sub3(P[(i + 1) % (P.length - 1)], P[(i - 1 + P.length - 1) % (P.length - 1)])), n = [t[1], -t[0], 0];
    rings.push(ringAt(wire, P[i], n, [0, 0, 1], hexSection(0.045, 0.045)));
  }
  loft(wire, rings, P.slice(0, -1), {closed: true});
  parts.push(wire);
  return {PARTS: parts, MATS: {
    web: {c: [40, 42, 40], ...NYLON},
    steel: {c: [150, 152, 156], hl: 12, sm: 130, se: 150, f0: 230},
  }, meshOnly: true, worn: true};
}

/**
 * Flip lighter (2026-10-20, docs/gameplay/flip_lighter_v1.md): an old windproof flip-top lighter, silver plated, no maker's
 * marks; 38 x 13 x 57 mm at 1 px = 5 mm. Stands on y = 0, front face +Z, the hinge on the +X side. The case and the lid
 * are lathed-looking shells (rounded-rectangle rings, rounded bottom and lid top); inside the case top the steel insert:
 * the chimney (front and back walls with eight holes each, the hinge-side ends rising into ears that carry the flint
 * wheel's axle), the cam beside it, the cotton wick with a charred tip. The lid's parts are named lid* and the wheel
 * `wheel`: the item renderer (client/FlipLighterRenderer) turns them about the points printed as stats.points.
 */
export const LIGHTER = {hinge: [4.0, 7.3, 0], wheel: [2.05, 10.45, 0], wick: [0.9, 10.42, 0], lidTop: 11.4};
function flipLighter() {
  const B = 'flip_lighter', parts = [], add = p => { parts.push(p); return p; };
  const HX = 3.8, HZ = 1.3, R = 0.75, SEG = 4;
  // plan of the shells at an inset d (same vertex count at every inset, so rings join quad to quad); axis y: (u, v) = (z, x)
  const plan = d => rr(-HZ + d, -HX + d, HZ - d, HX - d, Math.max(0.05, R - d), SEG);
  const at = (L, y) => L.map(([z, x]) => [x, y, z]);
  // rings [y, inset] joined in order. The profile's normal is dy outward plus d(inset) up (a ring stepping in as it rises
  // faces up and out); sgn -1 turns it round for the lid's inside
  const rings = (part, list, sgn, mat) => {
    const ids = list.map(([y, d]) => at(plan(d), y).map(q => part.vtx(q)));
    for (let k = 0; k + 1 < ids.length; k++) {
      const dy = list[k + 1][0] - list[k][0], dd = list[k + 1][1] - list[k][1];
      for (let i = 0; i < ids[k].length; i++) {
        const j = (i + 1) % ids[k].length, P = part.v[ids[k][i]], Q = part.v[ids[k][j]], h = Math.hypot(P[0] + Q[0], P[2] + Q[2]) || 1;
        part.face([ids[k][i], ids[k][j], ids[k + 1][j], ids[k + 1][i]], [sgn * dy * (P[0] + Q[0]) / h, sgn * dd, sgn * dy * (P[2] + Q[2]) / h], 'side', mat);
      }
    }
  };
  const cap = (part, y, d, up, mat) => {
    const L = plan(d), {pts, tris} = triangulate(L, []), ids = pts.map(([z, x]) => part.vtx([x, y, z]));
    for (const t of tris) part.face(t.map(i => ids[i]), [0, up, 0], 'cap', mat);
  };
  // case: rounded bottom edge, a small round at the seam, the rim round the insert's top plate
  const kase = add(new Part('case', B, 'silver'));
  rings(kase, [[0, 0.32], [0.04, 0.16], [0.12, 0.06], [0.25, 0], [7.2, 0], [7.27, 0.03], [7.3, 0.09]], 1);
  rings(kase, [[7.3, 0.09], [7.3, 0.18]], 1);          // rim (flat, facing up)
  rings(kase, [[7.3, 0.18], [7.25, 0.18]], 1, 'insert');   // a 0.25 mm step down to the insert (faces in)
  cap(kase, 0, 0.32, -1);
  cap(kase, 7.25, 0.18, 1, 'insert');
  // lid: outer shell with a rounded top, the inner wall 0.5 mm in, the ceiling 0.6 mm under the top
  const lid = add(new Part('lid', B, 'silver'));
  rings(lid, [[7.32, 0], [11.05, 0], [11.2, 0.05], [11.32, 0.14], [11.4, 0.3]], 1);
  cap(lid, 11.4, 0.3, 1);
  rings(lid, [[7.32, 0], [7.32, 0.1]], -1);           // bottom edge of the shell
  rings(lid, [[7.32, 0.1], [11.28, 0.1]], -1);        // inner wall, facing in
  cap(lid, 11.28, 0.1, -1);
  // hinge: five knuckles on the +X side at the seam (case 1, 3, 5; lid 2, 4), each on a leaf sunk into its shell
  const [hx, hy] = LIGHTER.hinge;
  for (let k = 0; k < 5; k++) {
    const z0 = -1.0 + k * 0.4 + 0.01, z1 = z0 + 0.38, onLid = k % 2 === 1;
    const p = add(new Part(onLid ? 'lid_hinge_' + k : 'hinge_' + k, B, 'silver'));
    revolve(p, hx, hy, [[z1, 0], [z1, 0.24], [z0, 0.24], [z0, 0]], 8);
    extrude(p, 'z', {outer: orient(onLid ? [[3.62, hy + 0.05], [hx, hy + 0.05], [hx, hy + 0.5], [3.62, hy + 0.5]] : [[3.5, hy - 0.5], [hx, hy - 0.5], [hx, hy - 0.08], [3.5, hy - 0.08]], true), holes: []}, z0 + 0.02, z1 - 0.02, 0);   // the leaf just inside the knuckle's ends: no shared faces
  }
  // chimney: front / back walls (holes, ears round the wheel axle) and two end walls; top at 10.5
  const [wx, wy] = LIGHTER.wheel, top = 10.5, T = 0.08, cx = 2.95, cz = 1.0;
  const tab = Array.from({length: 9}, (_, i) => { const a = (7 + (173 - 7) * i / 8) * Math.PI / 180; return [wx + 0.42 * Math.cos(a), wy + 0.42 * Math.sin(a)]; });
  const wall = orient([[-cx, 7.25], [cx, 7.25], [cx, top], ...tab, [-cx, top]], true);
  const holes = [];
  for (const y of [8.35, 9.3]) for (const x of [-2.1, -0.8, 0.5, 1.8]) holes.push(orient(circle(x, y, 0.28, 8), false));
  for (const [name, z0, z1] of [['chimney_front', cz - T, cz], ['chimney_back', -cz, -cz + T]])
    extrude(add(new Part(name, B, 'insert')), 'z', {outer: wall, holes}, z0, z1, 0);
  for (const [name, x0, x1] of [['chimney_left', -cx, -cx + T], ['chimney_right', cx - T, cx]])
    extrude(add(new Part(name, B, 'insert')), 'x', {outer: orient([[-cz + T, 7.25], [cz - T, 7.25], [cz - T, top], [-cz + T, top]], true), holes: []}, x0, x1, 0);
  // axle rivets on the ears' outer faces
  for (const [name, z0, z1] of [['rivet_front', cz, cz + 0.06], ['rivet_back', -cz - 0.06, -cz]])
    extrude(add(new Part(name, B, 'insert')), 'z', {outer: orient(circle(wx, wy, 0.2, 8), true), holes: []}, z0, z1, 0);
  // flint wheel between the walls: 16 flats read as the knurl
  revolve(add(new Part('wheel', B, 'wheel')), wx, wy, [[cz - T - 0.04, 0], [cz - T - 0.04, 0.78], [-cz + T + 0.04, 0.78], [-cz + T + 0.04, 0]], 16);
  // cam: the spring lever outside the hinge-side end wall that holds the lid open or shut
  extrude(add(new Part('cam', B, 'insert')), 'z', {outer: orient([[cx + 0.05, 7.25], [3.35, 7.25], [3.35, 8.6], [3.15, 8.9], [cx + 0.05, 8.9]], true), holes: []}, -0.25, 0.25, 0);
  // wick: cotton, charred tip
  const [kx, ky] = LIGHTER.wick;
  extrude(add(new Part('wick', B, 'wick')), 'y', {outer: orient(circle(0, kx, 0.17, 8), true), holes: []}, 7.25, ky - 0.12, 0);
  extrude(add(new Part('wick_tip', B, 'char')), 'y', {outer: orient(circle(0, kx, 0.16, 8), true), holes: []}, ky - 0.12, ky, 0);
  return {PARTS: parts, MATS: {
    silver: {c: [170, 167, 160], hl: 14, sm: 150, se: 168, f0: 237},   // worn silver plate, satin (LabPBR silver)
    insert: {c: [136, 138, 140], hl: 10, sm: 116, se: 136, f0: 230},   // stainless insert
    wheel: {c: [88, 90, 92], hl: 8, sm: 80, se: 100, f0: 230},          // knurled steel wheel
    wick: {c: [196, 184, 160], hl: 2, sm: 40, se: 50, f0: 20},          // cotton wick
    char: {c: [40, 36, 32], hl: 2, sm: 30, se: 40, f0: 20},             // its burnt tip
  },
  // held standing in the fist: third person face to the side like a vanilla sprite, first person face to the eye turned a
  // little, the hinge side (where the lid opens) to the right
  display: {
    thirdperson_righthand: {rotation: [0, 0, 0], translation: [0, 1.5, 0.5], scale: [0.3, 0.3, 0.3]},
    firstperson_righthand: {rotation: [0, -25, 0], translation: [0, 2.5, 0], scale: [0.32, 0.32, 0.32]},
  },
  atlas: 512, icon: {view: [20, 200, 0], coverage: 0.35, mat: {silver: [196, 194, 188]}},
  points: {hinge: LIGHTER.hinge, wheel: LIGHTER.wheel, wick: LIGHTER.wick}};
}

// low-frequency value noise for wood grain (the material-mesh lib keeps its own private copy)
const hash3 = (a, b, c) => { let h = Math.imul(a | 0, 374761393) ^ Math.imul(b | 0, 668265263) ^ Math.imul(c | 0, 2147483647); h = Math.imul(h ^ (h >>> 13), 1274126177); return ((h ^ (h >>> 16)) >>> 0) / 4294967295; };
export const vn3 = (x, y, z) => {
  const xi = Math.floor(x), yi = Math.floor(y), zi = Math.floor(z), s = t => t * t * (3 - 2 * t), l = (a, b, t) => a + (b - a) * t;
  const sx = s(x - xi), sy = s(y - yi), sz = s(z - zi), h = (a, b, c) => hash3(a, b, c * 7919 + 17);
  return l(l(l(h(xi, yi, zi), h(xi + 1, yi, zi), sx), l(h(xi, yi + 1, zi), h(xi + 1, yi + 1, zi), sx), sy),
    l(l(h(xi, yi, zi + 1), h(xi + 1, yi, zi + 1), sx), l(h(xi, yi + 1, zi + 1), h(xi + 1, yi + 1, zi + 1), sx), sy), sz);
};
export const fbm3 = (x, y, z) => 0.6 * vn3(x, y, z) + 0.3 * vn3(x * 2.03 + 7.1, y * 2.03 + 3.3, z * 2.03 + 1.7) + 0.1 * vn3(x * 4.1 + 2.9, y * 4.1 + 8.1, z * 4.1 + 5.3);

/**
 * Felling axe (2026-10-20): an American Michigan-pattern felling axe, a 3.5 lb forged head on a 32 inch curved hickory
 * handle. Authored at 1 px = 1.25 inch in the crowbar's frame (handle along +Y, the bit toward -X, z across) so the
 * crowbar's two-handed hold carries over. The handle: elliptical sections (wide along the bit), a swell and a knob (fawn's
 * foot) hooking toward the bit at the bottom, a slight bow toward the poll in the middle, through the eye and 0.18 px out
 * of the top, its end grain with the wedge. The head: vertical lens sections lofted from the flat poll to the edge, the
 * height flaring from the eye toward the bit (the beard drops further than the top rises), convex cheeks thinning to a
 * 0.58 px ground bevel, the edge curved out. Black enamel on the head as sold; the bevel ground bright and the poll face
 * worn to bare steel.
 */
export const AXE = {eye: 24.4, top: 25.5, edge: -4.08, bulge: 0.14};
function fellingAxe() {
  const B = 'felling_axe', {eye: EYE, top: TOP, edge: XE, bulge: BULGE} = AXE, X = [1, 0, 0], Z = [0, 0, 1];
  // handle: [y, half width along x, half thickness along z], linear between
  const SEC = [[0, 0.72, 0.5], [0.6, 0.72, 0.5], [1.5, 0.62, 0.43], [3, 0.5, 0.36], [12, 0.5, 0.36], [20, 0.48, 0.34], [22.5, 0.62, 0.33], [23.4, 0.7, 0.3], [TOP, 0.7, 0.3]];
  const sec = y => { let i = 0; while (i < SEC.length - 2 && y > SEC[i + 1][0]) i++;
    const [y0, a0, b0] = SEC[i], [y1, a1, b1] = SEC[i + 1], t = Math.max(0, Math.min(1, (y - y0) / (y1 - y0))); return [a0 + (a1 - a0) * t, b0 + (b1 - b0) * t]; };
  // centreline: bowed 0.25 toward the poll mid-length, the knob hooking 0.3 toward the bit; straight through the eye
  const mid = y => 0.25 * Math.sin(Math.PI * Math.max(0, Math.min(21, y - 2)) / 21) - (y < 3 ? 0.3 * (1 - y / 3) ** 2 : 0);
  const handle = new Part('handle', B, 'hickory');
  const HY = [[0, 0.8], [0.12, 0.95], [0.3, 1], 0.6, 1.0, 1.5, 2.2, 3, 4.5, 6, 8, 10, 12, 14, 16, 18, 20, 21.3, 22.5, 23.4, EYE, 25.0, [TOP - 0.08, 1], [TOP, 0.86]]
    .map(q => Array.isArray(q) ? q : [q, 1]);
  const hc = HY.map(([y]) => [mid(y), y, 0]);
  const hr = HY.map(([y, s], i) => { const [a, b] = sec(y); return ringAt(handle, hc[i], X, Z, Array.from({length: 12}, (_, k) => [a * s * Math.cos(k * Math.PI / 6), b * s * Math.sin(k * Math.PI / 6)])); });
  loft(handle, hr, hc);
  for (const f of handle.f.slice(-12)) f.mat = 'endgrain';   // the top cap (loft adds it last)

  // head: vertical sections at x stations, each 8 points down the +z cheek and back up the -z cheek
  const head = new Part('head', B, 'enamel');
  const XS = [1.52, 1.4, 0.75, 0.6, 0, -0.6, -0.7, -1.2, -1.8, -2.4, -3.0, -3.5, -3.8, -4.0], X0 = -0.7, BEV = -3.5;
  const tOf = x => Math.max(0, Math.min(1, (X0 - x) / (X0 - XE)));
  const T = t => 0.92 + 0.6 * t ** 1.6, Bt = t => -(0.92 + 1.16 * t ** 1.4);
  const half = x => x > 0.75 ? 0.5 : x >= X0 ? 0.52 : x >= BEV ? 0.09 + 0.43 * (1 - ((X0 - x) / (X0 - BEV)) ** 1.6) : 0.09 - 0.082 * (BEV - x) / (BEV - XE);
  const levels = (t, c) => { const top = T(t), bot = Bt(t), ys = [top, top - c]; for (let j = 1; j <= 4; j++) ys.push(top - c - (top - bot - 2 * c) * j / 5); ys.push(bot + c, bot); return ys; };
  const bend = (y, t) => -BULGE * Math.sin(Math.PI * (y - Bt(t)) / (T(t) - Bt(t))) * t ** 4;   // the curved edge, fading out toward the eye
  const ring = (x, sy, sz) => { const t = tOf(x), h = half(x), ys = levels(t, 0.1), zs = ys.map((_, k) => (k === 0 || k === 7 ? 0.78 : 1) * h);
    const side = ys.map((y, k) => [x + bend(y, t), EYE + y * sy, zs[k] * sz]);
    return [...side, ...side.slice().reverse().map(([a, b, c]) => [a, b, -c])].map(p => head.vtx(p)); };
  const rings = XS.map((x, i) => i === 0 ? ring(x, 0.86, 0.8) : ring(x, 1, 1));   // the poll face chamfered
  const edgeIds = levels(1, 0.1).map(y => head.vtx([XE + bend(y, 1), EYE + y, 0]));
  const centre = (x, t) => [x, EYE + (T(t) + Bt(t)) / 2, 0];
  const quad = (ids, c, mat) => head.face(ids, sub3(mul3(ids.reduce((s, id) => add3(s, head.v[id]), [0, 0, 0]), 1 / ids.length), c), 'side', mat);
  for (let i = 0; i + 1 < XS.length; i++) {
    const mat = i === 0 ? 'poll' : XS[i + 1] <= BEV ? 'bit' : 'enamel', c = centre((XS[i] + XS[i + 1]) / 2, tOf((XS[i] + XS[i + 1]) / 2));
    for (let j = 0; j < 16; j++) quad([rings[i][j], rings[i][(j + 1) % 16], rings[i + 1][(j + 1) % 16], rings[i + 1][j]], c, mat);
  }
  // last section to the edge line: both cheeks close onto it (the top and bottom ends as triangles)
  const last = rings[rings.length - 1], m = j => j < 8 ? j : 15 - j, ce = centre((XS[XS.length - 1] + XE) / 2, 1);
  for (let j = 0; j < 16; j++) { const k = (j + 1) % 16;
    quad(m(j) === m(k) ? [last[j], last[k], edgeIds[m(j)]] : [last[j], last[k], edgeIds[m(k)], edgeIds[m(j)]], ce, 'bit'); }
  const pc = head.vtx([XS[0], EYE, 0]);
  for (let j = 0; j < 16; j++) head.face([pc, rings[0][j], rings[0][(j + 1) % 16]], X, 'cap', 'poll');

  // hickory: long streaks along the handle (+-8 %), a finer second scale (+-3 %); oiled, satin
  const hickory = pos => tone3([158, 122, 82], 1 + 0.16 * (fbm3(pos[1] * 0.22, pos[0] * 2.6, pos[2] * 2.6) - 0.5) + 0.06 * (vn3(pos[1] * 0.9 + 3.1, pos[0] * 7, pos[2] * 7) - 0.5));
  // end grain: darker, growth rings running parallel to the bit (lines along x), the wedge a lighter strip across the middle
  const endgrain = pos => Math.abs(pos[2]) < 0.06 ? tone3([150, 116, 78], 1)
    : tone3([118, 84, 52], 1 + 0.05 * Math.sin(pos[2] * 2 * Math.PI / 0.16) + 0.04 * (vn3(pos[0] * 3, pos[2] * 3, 1) - 0.5));
  const yEdge = levels(1, 0.1)[3];
  return {PARTS: [handle, head], bg: 'enamel', MATS: {
    enamel: {c: [46, 46, 48], hl: 10, sm: 112, se: 132, f0: 22},     // black enamel over the forged head, satin
    bit: {c: [150, 152, 156], hl: 14, sm: 140, se: 160, f0: 230},     // the ground bevel, bright steel
    poll: {c: [108, 110, 112], hl: 12, sm: 96, se: 118, f0: 230},     // poll face, the enamel worn off
    hickory: {c: hickory, hl: 6, sm: 108, se: 120, f0: 20},
    endgrain: {c: endgrain, hl: 4, sm: 70, se: 80, f0: 20},
  },
  // the crowbar's transforms, the hands' ones 1.48 times (the skeleton prop's scale 0.75 vs a real axe's 0.508, user
  // 2026-10-20: too small in the hand); they show only without the skeleton (gui is the 2D icon)
  display: {
    firstperson_righthand: {rotation: [0, -18, -12], translation: [1.25, 1.5, -0.5], scale: [1.1, 1.1, 1.1]},
    firstperson_lefthand: {rotation: [0, 18, 12], translation: [1.25, 1.5, -0.5], scale: [1.1, 1.1, 1.1]},
    thirdperson_righthand: {rotation: [0, 0, -8], translation: [0, 2, 0], scale: [1.03, 1.03, 1.03]},
    thirdperson_lefthand: {rotation: [0, 0, 8], translation: [0, 2, 0], scale: [1.03, 1.03, 1.03]},
    ground: {rotation: [0, 0, 90], translation: [0, -2, 0], scale: [0.52, 0.52, 0.52]},
    fixed: {rotation: [0, -90, -30], translation: [0, 0, 0], scale: [0.55, 0.55, 0.55]},
  },
  atlas: 512, icon: {view: [0, 0, -45], coverage: 0.3, mat: {enamel: [70, 70, 74], bit: [190, 192, 196], poll: [150, 152, 156], hickory: [196, 152, 104], endgrain: [150, 110, 70]}},
  // HUD weapon list: side view, the head to the left, the bit up (as the crowbar's claw)
  hud: {screen: p => [-p[1], -p[0]], toward: [0, 0, -1], scale: 40},
  // skeleton prop points (px; player_props/felling_axe.json is written by tools/author-felling-axe-actions.mjs, keep in
  // step): the left hand just above the knob, the right next to it or up the handle (4 px hands at prop scale 0.75)
  points: {grip_end: [mid(2.8), 2.8, 0], grip: [mid(8.3), 8.3, 0], grip_high: [mid(13.1), 13.1, 0], end: [mid(0), 0, 0],
    top: [0, TOP, 0], eye: [0, EYE, 0], edge: [XE - BULGE, EYE + yEdge, 0], poll: [XS[0], EYE, 0]}};
}
const tone3 = (c, k) => c.map(v => v * k);

/**
 * Claw hammer (2026-10-21, the AFL workbench's first hung tool, a test item): an American 16 oz curved claw hammer, 13 inch
 * overall, a forged head on a hickory handle. Authored at the felling axe's scale (1 px = 1.25 inch, so the two sit right
 * together on a rack) in its frame: the handle along +Y, the striking face toward -X, the claw curving down toward +X, z
 * across. The head: rings lofted from the domed round face (1.125 inch) over the bell and the narrow neck into the eye
 * block, on along the claw's 55 degree curve; from a third of the way down the claw splits into two prongs whose V slot
 * widens to the tips. The handle: oval sections, the grip swelling to a flared butt, thinning at the shank and flaring into
 * the eye; its end grain shows 1.3 mm proud of the head's top with the wooden wedge across. Black enamel on the head as
 * sold, the face and its chamfer ground bright, the slot's faces bare steel; the handle clear-lacquered hickory.
 */
export const HAMMER = {eye: 9.96, top: 10.4, pegs: {bench: 1.1, rack: 1.575}};
function clawHammer() {
  const B = 'claw_hammer', {eye: EYE, top: TOP, pegs: PEGS} = HAMMER, X = [1, 0, 0], Y = [0, 1, 0], Z = [0, 0, 1];
  // a superellipse ring of n points (u along the ring's n, v along its b, centred on vc): p 2 a circle, larger p squarer
  const sring = (h, w, p, n = 16, vc = 0) => Array.from({length: n}, (_, k) => { const a = 2 * Math.PI * k / n, c = Math.cos(a), s = Math.sin(a);
    return [h * Math.sign(c) * Math.abs(c) ** (2 / p), vc + w * Math.sign(s) * Math.abs(s) ** (2 / p)]; });

  // handle: [y, half width along x, half thickness along z]
  const handle = new Part('handle', B, 'hickory');
  const HY = [[0, 0.5, 0.37], [0.08, 0.58, 0.44], [0.22, 0.61, 0.46], [0.6, 0.58, 0.43], [1.5, 0.53, 0.39], [4, 0.5, 0.37],
    [6.5, 0.44, 0.31], [8.2, 0.37, 0.26], [9.05, 0.41, 0.28], [9.5, 0.4, 0.26], [TOP + 0.04, 0.4, 0.26]];
  const hc = HY.map(([y]) => [0, y, 0]);
  loft(handle, HY.map(([y, a, b], i) => ringAt(handle, hc[i], X, Z, sring(a, b, 2, 12))), hc);
  for (const f of handle.f) if (f.tag === 'cap') f.mat = 'endgrain';

  // head: rings along +X from the face to the claw root, then along the claw's arc (its centre below the root)
  const head = new Part('head', B, 'enamel');
  // [x, half height (y), half width (z), squareness]
  const HS = [[-1.95, 0.36, 0.36, 2], [-1.9, 0.45, 0.45, 2], [-1.7, 0.46, 0.46, 2], [-1.45, 0.4, 0.4, 2.2], [-1.1, 0.34, 0.33, 2.6],
    [-0.8, 0.37, 0.33, 3.5], [-0.55, 0.44, 0.36, 5], [0.55, 0.44, 0.36, 5], [0.8, 0.4, 0.33, 4.5]];
  const centres = HS.map(([x]) => [x, EYE, 0]);
  const rings = HS.map(([, h, w, p], i) => ringAt(head, centres[i], Y, Z, sring(h, w, p)));
  const R = 1.9, ARC = 55 * Math.PI / 180, ROOT = 0.8, cc = [ROOT, EYE - R, 0];
  const onArc = f => { const a = ARC * f; return {p: [cc[0] + R * Math.sin(a), cc[1] + R * Math.cos(a), 0], n: [Math.sin(a), Math.cos(a), 0]}; };
  const clawH = f => 0.4 - 0.31 * f ** 1.15, clawW = f => 0.33 - 0.03 * f;
  const SPLIT = 0.35;
  for (const f of [0.12, 0.24, SPLIT + 0.05]) {
    const {p, n} = onArc(f);
    centres.push(p); rings.push(ringAt(head, p, n, Z, sring(clawH(f), clawW(f), 4.5)));
  }
  loft(head, rings, centres, {mat: i => i === 0 ? 'face' : 'enamel', capMat: 'steel'});
  // the face: a shallow dome (its cap's centre pushed 0.035 out), ground bright
  const faceCentre = head.v.findIndex(q => q[0] === HS[0][0] && q[1] === EYE && q[2] === 0);
  head.v[faceCentre] = [HS[0][0] - 0.035, EYE, 0];
  for (const f of head.f) if (f.tag === 'cap' && f.ids.includes(faceCentre)) f.mat = 'face';

  // the two prongs: from just inside the split along the rest of the arc, the V slot widening toward the tips
  const prongs = [];
  for (const side of [-1, 1]) {
    const prong = new Part('claw_' + (side < 0 ? 'a' : 'b'), B, 'enamel'), P = [], Rg = [];
    for (let k = 0; k <= 7; k++) {
      const f = SPLIT + (1 - SPLIT) * k / 7, {p, n} = onArc(f), g = 0.035 + 0.1 * ((f - SPLIT) / (1 - SPLIT)) ** 1.2, w = clawW(f);
      const half = (w - g) / 2, vc = side * (g + half);
      P.push(add3(p, mul3(Z, vc))); Rg.push(ringAt(prong, p, n, Z, sring(clawH(f), half, 3.5, 12, vc)));
    }
    loft(prong, Rg, P, {capMat: 'steel'});
    // the slot's faces (toward the other prong) are bare steel
    for (const f of prong.f) if (f.tag === 'side' && dot3(unit(newell(f.ids.map(i => prong.v[i]))), mul3(Z, -side)) > 0.6) f.mat = 'steel';
    prongs.push(prong);
  }

  // where the head rests on two pegs either side of the handle (workbench 3.5 cm, tool rack 5 cm out): its underside there
  const under = x => Math.min(...[head, ...prongs].flatMap(pt => pt.v.filter(q => Math.abs(q[0] - x) < 0.15).map(q => q[1])));
  const hickory = pos => tone3([176, 134, 88], 1 + 0.14 * (fbm3(pos[1] * 0.3, pos[0] * 3, pos[2] * 3) - 0.5) + 0.05 * (vn3(pos[1] * 1.1 + 3.1, pos[0] * 8, pos[2] * 8) - 0.5));
  // end grain: growth rings across the handle's width, the hardwood wedge a lighter strip along the head's line
  const endgrain = pos => Math.abs(pos[2]) < 0.045 ? tone3([168, 132, 90], 1)
    : tone3([128, 92, 58], 1 + 0.05 * Math.sin(pos[0] * 2 * Math.PI / 0.14) + 0.04 * (vn3(pos[0] * 3, pos[2] * 3, 2) - 0.5));
  return {PARTS: [handle, head, ...prongs], bg: 'enamel', MATS: {
    enamel: {c: [40, 40, 42], hl: 10, sm: 118, se: 136, f0: 22},     // black enamel over the forged head, satin
    face: {c: [168, 170, 174], hl: 16, sm: 168, se: 180, f0: 230},    // the ground striking face and its chamfer
    steel: {c: [128, 130, 134], hl: 12, sm: 120, se: 140, f0: 230},   // bare steel in the slot and on the cut ends
    hickory: {c: hickory, hl: 6, sm: 150, se: 160, f0: 20},           // clear-lacquered hickory, a soft sheen
    endgrain: {c: endgrain, hl: 4, sm: 70, se: 80, f0: 20},
  },
  // first person (user 2026-10-21: the face down): ready to drive a nail, the handle pitched 50 deg forward and 15 deg
  // toward the screen's middle, the striking face down and forward (rotation = Ry(15) Rx(-50) Ry(-90) as JOML XYZ); the
  // grip a third up the handle kept where the upright hold had it. Third person (user 2026-10-21: the face down there too):
  // the hand's frame has the handle forward, x to the right, z up; turned -90 deg about the handle the face points down
  // and the claw up, the handle dipped 15 deg against the arm's 18 deg lift (about level), rotation = Ry(-90) Rz(15)
  display: {
    firstperson_righthand: {rotation: [0, -75, 50], translation: [0.5, 2.4, -2.3], scale: [1.4, 1.4, 1.4]},
    firstperson_lefthand: {rotation: [0, 75, -50], translation: [0.5, 2.4, -2.3], scale: [1.4, 1.4, 1.4]},
    thirdperson_righthand: {rotation: [0, -90, 15], translation: [0, 2.5, 0.5], scale: [1.05, 1.05, 1.05]},
    thirdperson_lefthand: {rotation: [0, 90, -15], translation: [0, 2.5, 0.5], scale: [1.05, 1.05, 1.05]},
    ground: {rotation: [0, 0, 90], translation: [0, -1, 0], scale: [0.8, 0.8, 0.8]},
    fixed: {rotation: [0, 0, -45], translation: [0, 0, 0], scale: [1.1, 1.1, 1.1]},
  },
  icon: {view: [0, 0, -45], coverage: 0.3, mat: {enamel: [66, 66, 70], face: [200, 202, 206], steel: [160, 162, 166], hickory: [210, 166, 114], endgrain: [160, 120, 78]}},
  // the hung hold (client HungToolMeshes): the handle's axis, its top, and the head's underside over each peg
  points: {axis: [0, 0, 0], top: [0, TOP, 0], eye: [0, EYE, 0],
    ...Object.fromEntries(Object.entries(PEGS).flatMap(([k, d]) => [['rest_face_' + k, [-d, under(-d), 0]], ['rest_claw_' + k, [d, under(d), 0]]]))}};
}

/**
 * Building hammer (2026-10-21, building hammer B0, docs/gameplay/building_hammer_b0.md): the tool of the building mode, a
 * traditional double-faced hammer (a 2.5 lb club hammer: a forged steel head 4.6 inch long and 1.6 inch square, both faces
 * the same, on a 12 inch hickory handle). Authored in the claw hammer's frame and scale (1 px = 1.25 inch, the handle along
 * +Y, the head along X, z across) with the claw hammer's handle and eye height, so the claw hammer's tested hold carries
 * over (its grip lands within 0.25 px, checked offline). The head: rings lofted face to face, each end a domed round-
 * cornered face with a ground chamfer, the body a rounded square that narrows a little either side of the eye block. The
 * head is dark forged steel with a heat-blued tone (not the claw hammer's black enamel), the faces ground bright; the
 * handle oiled hickory, darker than the claw hammer's lacquer, its end grain with the wedge proud of the top.
 */
export const CLUB = {eye: 9.96, top: 10.4, half: 1.85};
function buildingHammer() {
  const B = 'building_hammer', {eye: EYE, top: TOP, half: L} = CLUB, X = [1, 0, 0], Y = [0, 1, 0], Z = [0, 0, 1];
  const sring = (h, w, p, n = 16) => Array.from({length: n}, (_, k) => { const a = 2 * Math.PI * k / n, c = Math.cos(a), s = Math.sin(a);
    return [h * Math.sign(c) * Math.abs(c) ** (2 / p), w * Math.sign(s) * Math.abs(s) ** (2 / p)]; });

  // handle: the claw hammer's sections, a touch fuller through the shank (a heavier head)
  const handle = new Part('handle', B, 'hickory');
  const HY = [[0, 0.5, 0.37], [0.08, 0.58, 0.44], [0.22, 0.61, 0.46], [0.6, 0.58, 0.43], [1.5, 0.53, 0.39], [4, 0.5, 0.37],
    [6.5, 0.46, 0.33], [8.2, 0.41, 0.29], [9.05, 0.44, 0.3], [9.4, 0.42, 0.28], [TOP + 0.04, 0.42, 0.28]];
  const hc = HY.map(([y]) => [0, y, 0]);
  loft(handle, HY.map(([y, a, b], i) => ringAt(handle, hc[i], X, Z, sring(a, b, 2, 12))), hc);
  for (const f of handle.f) if (f.tag === 'cap') f.mat = 'endgrain';

  // head: [x, half height (y), half width (z), squareness], face to face along X; symmetric about the eye
  const side = [[L, 0.5, 0.5, 2.4], [L - 0.06, 0.6, 0.6, 3.2], [L - 0.24, 0.63, 0.63, 5.5], [0.95, 0.6, 0.58, 5.5], [0.55, 0.63, 0.6, 6]];
  const HS = [...side.map(([x, ...r]) => [-x, ...r]), ...side.slice().reverse().map(([x, ...r]) => [x, ...r])];
  const head = new Part('head', B, 'forged');
  const centres = HS.map(([x]) => [x, EYE, 0]);
  const rings = HS.map(([, h, w, p], i) => ringAt(head, centres[i], Y, Z, sring(h, w, p)));
  const n = HS.length;
  loft(head, rings, centres, {mat: i => i === 0 || i === n - 2 ? 'face' : 'forged', capMat: 'face'});
  // both faces a shallow dome: each cap's centre pushed 0.04 out
  for (const sgn of [-1, 1]) {
    const c = head.v.findIndex(q => q[0] === sgn * L && q[1] === EYE && q[2] === 0);
    if (c >= 0) head.v[c] = [sgn * (L + 0.04), EYE, 0];
  }

  const hickory = pos => tone3([150, 110, 70], 1 + 0.14 * (fbm3(pos[1] * 0.3, pos[0] * 3, pos[2] * 3) - 0.5) + 0.05 * (vn3(pos[1] * 1.1 + 5.3, pos[0] * 8, pos[2] * 8) - 0.5));
  const endgrain = pos => Math.abs(pos[2]) < 0.045 ? tone3([160, 124, 84], 1)
    : tone3([112, 80, 50], 1 + 0.05 * Math.sin(pos[0] * 2 * Math.PI / 0.14) + 0.04 * (vn3(pos[0] * 3, pos[2] * 3, 4) - 0.5));
  // forged steel: dark with a faint blue-grey temper, low-frequency mottling from the forge scale (+-6 %)
  const forged = pos => tone3([58, 62, 70], 1 + 0.12 * (fbm3(pos[0] * 1.6 + 2.2, pos[1] * 1.6, pos[2] * 1.6) - 0.5));
  return {PARTS: [handle, head], bg: 'ground', MATS: {
    ground: {c: [58, 62, 70], hl: 10, sm: 104, se: 124, f0: 230},    // the atlas background: the forged tone, flat
    forged: {c: forged, hl: 10, sm: 104, se: 124, f0: 230},          // bare forged steel, oiled: metal, satin-rough
    face: {c: [170, 172, 176], hl: 16, sm: 168, se: 180, f0: 230},    // the ground striking faces and their chamfers
    hickory: {c: hickory, hl: 6, sm: 118, se: 128, f0: 20},           // oiled hickory, less sheen than lacquer
    endgrain: {c: endgrain, hl: 4, sm: 70, se: 80, f0: 20},
  },
  // the claw hammer's holds (user PASS 2026-10-21; one face down in first and third person), the translations moved so
  // the grip (y 3.5 on the handle) lands where the claw hammer's does: the bake centres this head on X (the claw shifted
  // the claw hammer 0.22 px) and the head is 0.15 px taller (checked offline 2026-10-21: the grip moved 0.33 px in first
  // person, 0.25 px in third; the faces 9.4 / 7.1 px from the grip)
  display: {
    firstperson_righthand: {rotation: [0, -75, 50], translation: [0.427, 2.229, -2.571], scale: [1.4, 1.4, 1.4]},
    firstperson_lefthand: {rotation: [0, 75, -50], translation: [0.427, 2.229, -2.571], scale: [1.4, 1.4, 1.4]},
    thirdperson_righthand: {rotation: [0, -90, 15], translation: [0, 2.516, 0.254], scale: [1.05, 1.05, 1.05]},
    thirdperson_lefthand: {rotation: [0, 90, -15], translation: [0, 2.516, 0.254], scale: [1.05, 1.05, 1.05]},
    ground: {rotation: [0, 0, 90], translation: [0, -1, 0], scale: [0.8, 0.8, 0.8]},
    fixed: {rotation: [0, 0, -45], translation: [0, 0, 0], scale: [1.1, 1.1, 1.1]},
  },
  icon: {view: [0, 0, -45], coverage: 0.3, mat: {forged: [92, 98, 108], face: [204, 206, 210], hickory: [190, 146, 98], endgrain: [150, 110, 72]}},
  points: {axis: [0, 0, 0], top: [0, TOP, 0], eye: [0, EYE, 0], grip: [0, 3.5, 0]}};
}

/**
 * Held-display fit (docs/models/equipment_meshes_v1.md, 手持适配规则): the translation that puts an item's grip point
 * (authored px) where the claw hammer's grip sits in that view (the reference, user PASS 2026-10-21), for the item's own
 * rotation and scale. translation = reference - scale * R * grip, the grip taken about the baked mesh's centre (bake
 * centres it), R = Rx Ry Rz as the item model's rotation (JOML XYZ, z first).
 */
const HELD_REF = {
  firstperson: {t: [0.5, 2.4, -2.3], s: 1.4, r: [0, -75, 50]},
  thirdperson: {t: [0, 2.5, 0.5], s: 1.05, r: [0, -90, 15]},
};
const HAMMER_GRIP = [-0.223, -1.72, 0];   // the claw hammer's grip, about its baked centre
export function rotXYZ([a, b, c], [x, y, z]) {
  const r = d => d * Math.PI / 180, ca = Math.cos(r(a)), sa = Math.sin(r(a)), cb = Math.cos(r(b)), sb = Math.sin(r(b)), cc = Math.cos(r(c)), sc = Math.sin(r(c));
  [x, y] = [x * cc - y * sc, x * sc + y * cc];   // Rz
  [x, z] = [x * cb + z * sb, -x * sb + z * cb];   // Ry
  [y, z] = [y * ca - z * sa, y * sa + z * ca];   // Rx
  return [x, y, z];
}
export const meshCentre = PARTS => { const all = PARTS.flatMap(p => p.v);
  return [0, 1, 2].map(k => (Math.min(...all.map(q => q[k])) + Math.max(...all.map(q => q[k]))) / 2); };
export function fitHeld(PARTS, grip, view, rotation, scale) {
  const ref = HELD_REF[view], g = sub3(grip, meshCentre(PARTS));
  const at = add3(ref.t, mul3(rotXYZ(ref.r, HAMMER_GRIP), ref.s));
  const t = sub3(at, mul3(rotXYZ(rotation, g), scale)).map(v => +v.toFixed(3));
  const [rx, ry, rz] = rotation, s = [scale, scale, scale];
  return {[view + '_righthand']: {rotation, translation: t, scale: s}, [view + '_lefthand']: {rotation: [rx, -ry, -rz], translation: t, scale: s}};
}

/**
 * Hand saw (2026-10-21, the workbench's test beam recipe, docs/gameplay/afl_workbench_v1_design.md): an American 26 inch
 * rip saw of the classic pattern (5 1/2 points per inch, a straight-backed taper-ground blade, a closed apple-wood handle
 * with three brass saw nuts; plain, no medallion art or etch). Authored at the felling axe's scale (1 px = 1.25 inch) in
 * the plane of the blade: the blade along +Y (the toe at +Y), the tooth line on x = 0 with the teeth toward -X and the back
 * toward +X, z across. The blade 0.035 inch thick, 7 1/4 inch wide at the heel, 1.9 at the toe; the teeth a separate strip
 * (rip teeth: an upright face toward the toe, the back sloping) ground bright; the handle a closed loop around a slanted
 * hand hole, its edges chamfered, clear-lacquered; the nuts domed both sides.
 */
export const SAW = {K: 0.8, heel: 0.4, toe: 26.2, heelWidth: 7.25, toeWidth: 1.9, teethFrom: 1.0, teethTo: 25.8, root: 0.2, tooth: 0.11,
  hole: {c: [-1.25, 4.6], a: [-0.414, -0.91], length: 3.9, width: 1.35}, grip: [-2.37, 5.1]};
function handSaw() {
  const B = 'hand_saw', {K} = SAW, xy = ([s, h]) => [h * K, s * K];
  const back = s => SAW.heelWidth + (SAW.toeWidth - SAW.heelWidth) * (s - SAW.heel) / (SAW.toe - SAW.heel);
  const BT = 0.035 * K / 2, HT = 0.95 * K / 2;   // half thickness: the blade, the handle

  // blade: heel (inside the handle) to toe, the back a straight taper, its lower edge where the tooth strip begins
  const blade = new Part('blade', B, 'steel');
  const bladeOutline = [[SAW.heel, SAW.root], [SAW.toe, SAW.root], [SAW.toe, SAW.toeWidth], [SAW.heel, SAW.heelWidth]].map(xy);
  extrude(blade, 'z', {outer: orient(bladeOutline, true), holes: []}, -BT, BT, 0);
  // teeth: a gullet, the back sloping down to the point, the face back up to the next gullet (rip teeth cut toward the toe)
  const teeth = new Part('teeth', B, 'teeth'), N = Math.round((SAW.teethTo - SAW.teethFrom) / 0.182), P = (SAW.teethTo - SAW.teethFrom) / N;
  // the strip: from the blade's lower edge (h = root) down to the gullets (h = tooth) and the points on the tooth line (h = 0)
  const zig = [[SAW.teethFrom, SAW.root], [SAW.teethFrom, SAW.tooth]];
  for (let k = 0; k < N; k++) { const s0 = SAW.teethFrom + k * P; zig.push([s0 + P * 0.88, 0], [s0 + P, SAW.tooth]); }
  zig.push([SAW.teethTo, SAW.root]);
  extrude(teeth, 'z', {outer: orient(zig.map(xy), true), holes: []}, -BT, BT, 0);

  // handle: [along the blade, up from the tooth line] (inch), the closed loop from the top of the cheek back over the upper
  // horn (it overhangs the web of the thumb by 0.7 inch), the throat, down the grip's back (parallel to the hand hole, a
  // little full), out to the lower horn, along the bottom and up the front edge over the blade's heel; 7.75 inch tall
  const H = [[2.6, back(2.6) + 0.1], [1.8, 7.55], [0.9, 8.0], [0, 8.25], [-0.9, 8.35], [-1.7, 8.3], [-2.4, 8.1], [-2.85, 7.78], [-3.0, 7.45],
    [-2.85, 7.18], [-2.5, 7.05], [-2.2, 6.79], [-2.55, 6.1], [-2.96, 5.38], [-3.27, 4.65], [-3.53, 3.88], [-3.85, 3.2], [-4.3, 2.5],
    [-4.65, 1.95], [-4.75, 1.45], [-4.5, 1.05], [-4.1, 0.9], [-3.3, 0.7], [-2.4, 0.6], [-1.5, 0.62], [-0.6, 0.72], [0.2, 0.85],
    [0.7, 1.05], [1.1, 1.5], [1.45, 2.45], [1.8, 3.6], [2.1, 4.8], [2.35, 5.9]];
  const {c: hc, a: ha, length: hl, width: hw} = SAW.hole, hr = hw / 2, hs = hl / 2 - hr, hn = [-ha[1], ha[0]];
  const hole = [];
  for (const [sgn, a0] of [[1, 0], [-1, Math.PI]]) for (let i = 0; i <= 8; i++) {
    const a = a0 - Math.PI / 2 + Math.PI * i / 8, e = [Math.cos(a), Math.sin(a)];
    hole.push([hc[0] + sgn * hs * ha[0] + hr * (e[0] * ha[0] + e[1] * hn[0]), hc[1] + sgn * hs * ha[1] + hr * (e[0] * ha[1] + e[1] * hn[1])]);
  }
  const handle = new Part('handle', B, 'apple');
  extrude(handle, 'z', {outer: orient(H.map(xy), true), holes: [orient(hole.map(xy), false)]}, -HT, HT, 0.18 * K);
  // three brass saw nuts through the cheeks and the blade, domed on both faces
  const nuts = [[1.6, 5.6], [1.15, 3.6], [0.7, 2.3]].map(([s, h], i) => {
    const nut = new Part('nut_' + i, B, 'brass'), r = 0.3 * K, z1 = HT + 0.07 * K, [x, y] = xy([s, h]);
    revolve(nut, x, y, [[z1, 0], [z1, r * 0.5], [z1 - 0.012, r * 0.8], [z1 - 0.03, r * 0.96], [HT - 0.01, r], [-(HT - 0.01), r],
      [-(z1 - 0.03), r * 0.96], [-(z1 - 0.012), r * 0.8], [-z1, r * 0.5], [-z1, 0]], 12);
    return nut;
  });

  const PARTS = [blade, teeth, handle, ...nuts], grip = [...xy(SAW.grip), 0];
  // apple wood: close, even grain along the handle's length (+-7 %), a finer second scale; clear lacquer
  const apple = pos => tone3([138, 82, 50], 1 + 0.14 * (fbm3(pos[1] * 0.4 + 1.3, pos[0] * 2.2, pos[2] * 2.2) - 0.5) + 0.05 * (vn3(pos[1] * 1.3 + 6.1, pos[0] * 7, pos[2] * 7) - 0.5));
  // blade steel: a faint low-frequency mottle from the taper grinding and age (+-5 %), no marks
  const steel = pos => tone3([128, 131, 136], 1 + 0.1 * (fbm3(pos[0] * 0.5 + 4.1, pos[1] * 0.25, pos[2] * 0.5) - 0.5));
  return {PARTS, bg: 'plate', MATS: {
    plate: {c: [128, 131, 136], hl: 10, sm: 150, se: 166, f0: 230},   // the atlas background: the blade's tone, flat
    steel: {c: steel, hl: 10, sm: 150, se: 166, f0: 230},              // the blade: bright spring steel, satin from use
    teeth: {c: [170, 172, 176], hl: 14, sm: 168, se: 180, f0: 230},    // the teeth, freshly filed
    apple: {c: apple, hl: 6, sm: 150, se: 160, f0: 20},
    brass: {c: [176, 138, 72], hl: 14, sm: 156, se: 170, f0: 230},
  },
  // held by the grip like a pistol, the blade forward from the index finger, the teeth down; the grip lands on the claw
  // hammer's (fitHeld, checked offline 2026-10-21: within 0.001 px). Third person: Ry(-90) puts the back up, Rz(35) dips
  // the blade 35 deg in the hand's frame (about 17 below level against the arm's 18 deg lift; 45 put the toe at the
  // feet), the grip bar 31 deg off the fist's axis. First person: Rz(110) the toe forward and 20 deg down, 14 deg toward
  // the screen's middle, the teeth down. Scaled about the felling axe's size (the skeleton prop's scale 0.7).
  display: {
    ...fitHeld(PARTS, grip, 'firstperson', [0, -75, 110], 0.85),
    ...fitHeld(PARTS, grip, 'thirdperson', [0, -90, 35], 0.7),
    ground: {rotation: [90, 0, 0], translation: [0, 1, 0], scale: [0.45, 0.45, 0.45]},
    fixed: {rotation: [0, 0, -45], translation: [0, 0, 0], scale: [0.62, 0.62, 0.62]},
  },
  atlas: 512, icon: {view: [0, 0, -45], coverage: 0.3, mat: {plate: [160, 164, 170], steel: [160, 164, 170], teeth: [196, 198, 202], apple: [168, 104, 64], brass: [210, 170, 92]}},
  // skeleton prop and hung points (px): the grip (the bar behind the hand hole), the toe and heel on the tooth line, the
  // middle of the tooth line, the top of the hand hole's far end (where it hangs from a peg)
  points: {grip, toe: xy([SAW.toe, 0]).concat(0), heel: xy([SAW.heel, 0]).concat(0), teeth_mid: xy([(SAW.teethFrom + SAW.teethTo) / 2, 0]).concat(0),
    hole_c: xy(hc).concat(0)}};
}

/**
 * Folding knife (2026-10-22, docs/gameplay/folding_knife_v1.md, version A): a traditional American lockback, 12.6 cm
 * closed and 21.1 cm open, a 9.5 cm clip-point blade of satin stainless (flat ground from the spine's lower third, a
 * bright secondary bevel at the edge, the ricasso's lower edge eased, the nail nick on the mark side +z), walnut scales
 * over brass liners, brass front and rear bolsters, brass pins (the pivot, the rear pin, two rivets), the steel backspring
 * along the back between the liners with the lock release standing 0.2 mm proud of the handle at the middle, a steel spacer
 * filling the butt. Authored in cm (1 px = 1 cm) from the concept (design/items/folding_knife_v1/fk_build.py: x along the
 * knife from the pivot, z up the spine) turned into the tools' frame: the handle along +Y from the butt (y = 0), the blade
 * out of the front bolster toward +Y, the spine and the backspring toward +X, the edge toward -X, z across. The blade
 * (the part `blade`) is drawn open; the item renderer (client/FoldingKnifeRenderer) turns it about the pivot pin (the z
 * axis through KNIFE.pivot, printed as stats.points.pivot) up to 180 deg, closed into the channel between the liners with
 * its spine at the belly.
 */
export const KNIFE = {butt: 11.6, pivot: [-0.15, 11.6, 0], grip: [0, 5.6, 0], tip: [-0.05, 21.1, 0], nick: [0.5, 13.1, 0.15], half: 0.15};
function foldingKnife() {
  const B = 'folding_knife', PY = KNIFE.butt, HALF = KNIFE.half, parts = [], add = p => { parts.push(p); return p; };
  // the concept's (x, z) to the mesh's (x, y): x = z (up the spine), y = x + the butt's distance behind the pivot
  const uv = ([x, z]) => [z, x + PY];
  const catmull = (P, closed, k = 5) => {
    const out = [], n = P.length;
    for (let i = 0; i < (closed ? n : n - 1); i++) {
      const p0 = P[closed || i > 0 ? (i - 1 + n) % n : 0], p1 = P[i], p2 = P[(i + 1) % n], p3 = P[closed || i + 2 < n ? (i + 2) % n : n - 1];
      for (let s = 0; s < k; s++) { const t = s / k;
        out.push([0, 1].map(j => 0.5 * (2 * p1[j] + (-p0[j] + p2[j]) * t + (2 * p0[j] - 5 * p1[j] + 4 * p2[j] - p3[j]) * t * t + (-p0[j] + 3 * p1[j] - 3 * p2[j] + p3[j]) * t ** 3))); }
    }
    if (!closed) out.push(P[n - 1]);
    return out;
  };
  const interp = (pl, x) => { if (x <= pl[0][0]) return pl[0][1];
    for (let i = 0; i + 1 < pl.length; i++) { const [x0, z0] = pl[i], [x1, z1] = pl[i + 1]; if (x <= x1) return x1 === x0 ? z0 : z0 + (z1 - z0) * (x - x0) / (x1 - x0); }
    return pl[pl.length - 1][1]; };
  // a closed loop clipped to lo <= concept x <= hi
  const clipx = (P, lo, hi) => {
    const clip = (P, keep, v) => { const out = [];
      for (let i = 0; i < P.length; i++) { const a = P[(i - 1 + P.length) % P.length], b = P[i], cut = () => [v, a[1] + (b[1] - a[1]) * (v - a[0]) / (b[0] - a[0])];
        if (keep(b)) { if (!keep(a)) out.push(cut()); out.push(b); } else if (keep(a)) out.push(cut()); }
      return out; };
    return clip(clip(P, p => p[0] >= lo, lo), p => p[0] <= hi, hi);
  };
  // a closed loop moved d inward (mitred; d small against the loop's curvature)
  const inset = (L, d) => { const s = area2(L) > 0 ? 1 : -1, n = L.length;
    const en = (a, b) => { const ex = b[0] - a[0], ey = b[1] - a[1], l = Math.hypot(ex, ey) || 1; return [-s * ey / l, s * ex / l]; };
    return L.map((p, i) => { const n1 = en(L[(i - 1 + n) % n], p), n2 = en(p, L[(i + 1) % n]), m = [n1[0] + n2[0], n1[1] + n2[1]], ml = Math.hypot(...m) || 1;
      const k = d / Math.max(0.3, (m[0] * n1[0] + m[1] * n1[1]) / ml); return [p[0] + m[0] / ml * k, p[1] + m[1] / ml * k]; }); };
  const slab = (part, loop, z0, z1, c) => { for (const sg of [-1, 1]) extrude(part, 'z', {outer: orient(loop.map(uv), true), holes: []}, sg > 0 ? z0 : -z1, sg > 0 ? z1 : -z0, c); };

  // the handle's side outline (the concept's), the top line the backspring follows
  const OUTLINE = catmull([[0.9, -0.6], [0.95, 0.3], [0.6, 0.95], [-0.5, 1.2], [-3, 1.28], [-6, 1.22], [-9, 1.15], [-10.9, 1.05], [-11.5, 0.6],
    [-11.6, -0.1], [-11.4, -0.8], [-10.8, -1.15], [-9, -1.22], [-6, -1.1], [-3.5, -1.2], [-1.5, -1.32], [0.2, -1.15]], true);
  const TOP = [[-10.9, 1.05], [-9, 1.15], [-6, 1.22], [-3, 1.28], [-0.5, 1.2], [0.6, 0.95]];
  // brass liners the full outline (z 1.55-2.05 mm); bolsters over them at the ends, the walnut scales between, a little
  // inside the outline (the liners' edge shows as a thin brass line) and running 0.5 mm under the bolsters
  slab(add(new Part('liners', B, 'brass')), OUTLINE, 0.155, 0.205, 0);
  slab(add(new Part('bolster_front', B, 'brass')), clipx(OUTLINE, -1.6, 2), 0.21, 0.82, 0.12);
  slab(add(new Part('bolster_rear', B, 'brass')), clipx(OUTLINE, -12, -10.1), 0.21, 0.82, 0.12);
  slab(add(new Part('scales', B, 'walnut')), clipx(inset(OUTLINE, 0.012), -10.15, -1.55), 0.21, 0.76, 0.14);
  // backspring: 4.5 mm deep under the top line, 0.4 mm below the liners' edge; the lock release a bar on it, proud of the
  // handle; the spacer fills the butt between the liners
  const XS = Array.from({length: 23}, (_, i) => 0.2 - 0.5 * i), top = x => interp(TOP, x);
  const spring = [...XS.map(x => [x, top(x) - 0.04]), ...XS.slice().reverse().map(x => [x, top(x) - 0.49])];
  extrude(add(new Part('backspring', B, 'spring')), 'z', {outer: orient(spring.map(uv), true), holes: []}, -HALF, HALF, 0);
  const rel = Math.max(top(-6.9), top(-5.5));
  extrude(add(new Part('lock_release', B, 'spring')), 'z', {outer: orient([[-6.9, rel - 0.05], [-5.5, rel - 0.05], [-5.5, rel + 0.02], [-6.9, rel + 0.02]].map(uv), true), holes: []}, -HALF + 0.002, HALF - 0.002, 0);
  extrude(add(new Part('spacer', B, 'spring')), 'z', {outer: orient(clipx(inset(OUTLINE, 0.02), -12, -10.3).map(uv), true), holes: []}, -HALF + 0.002, HALF - 0.002, 0);
  // brass pins, domed 0.25 mm proud: the pivot, the rear pin (both through the bolsters), two rivets through the scales
  for (const [name, x, z, r, z1] of [['pivot_pin', 0, KNIFE.pivot[0], 0.32, 0.845], ['rear_pin', -10.75, 0, 0.26, 0.845], ['rivet_front', -4.2, 0.15, 0.17, 0.785], ['rivet_rear', -8.2, 0.1, 0.17, 0.785]]) {
    const [cx, cy] = uv([x, z]);
    revolve(add(new Part(name, B, 'brass')), cx, cy, [[z1, 0], [z1, r * 0.55], [z1 - 0.015, r * 0.85], [z1 - 0.035, r], [-(z1 - 0.035), r], [-(z1 - 0.015), r * 0.85], [-z1, r * 0.55], [-z1, 0]], 12);
  }

  // blade: an 8-point section every 1.5 mm along the concept's spine (top) and edge (bottom) lines, from the tang's end to
  // the tip: [spine, grind line, secondary bevel, edge] down the +z face and back up the -z face; the grind comes in over
  // 5 mm from 9 mm ahead of the pivot (the ricasso before that, only its lower edge eased); a distal taper to
  // 55 % of the spine's thickness at the tip
  const BT = catmull([[-0.75, 0.7], [-0.2, 0.95], [5.4, 0.95], [6.4, 0.84], [7.4, 0.58], [8.5, 0.22], [9.5, -0.05]], false, 4);
  const BB = catmull([[-0.75, -0.9], [0.55, -0.92], [0.75, -1.33], [3.0, -1.35], [5.0, -1.27], [7.0, -1.0], [8.3, -0.6], [9.1, -0.26], [9.5, -0.05]], false, 4);
  const X0 = -0.75, TIP = 9.5, GRIND = 0.9, blade = add(new Part('blade', B, 'steel'));
  const lerp = (a, b, f) => a + (b - a) * f;
  const stations = Array.from({length: 69}, (_, i) => X0 + (TIP - X0) * i / 69);
  const rings = stations.map(x => {
    const t = interp(BT, x), b = interp(BB, x), f = Math.max(0, Math.min(1, (x - GRIND) / 0.5));
    const h = HALF * (1 - 0.45 * (x - X0) / (TIP - X0));
    const g = lerp(b + 0.1, b + (t - b) * 0.72, f), s = b + Math.min(lerp(0.04, 0.1, f), 0.35 * (g - b));
    const e2 = lerp(h * 0.8, Math.min(0.045, h * 0.6), f), e = lerp(h * 0.55, 0.012, f);
    const sec = [[t, h], [g, h], [s, e2], [b, e], [b, -e], [s, -e2], [g, -h], [t, -h]];
    return {c: [(t + b) / 2, x + PY, 0], ids: sec.map(([X, Z]) => blade.vtx([X, x + PY, Z]))};
  });
  const SEC_MAT = ['steel', 'steel', 'edge', 'edge', 'edge', 'steel', 'steel', 'steel'];
  const centroid = ids => mul3(ids.reduce((q, i) => add3(q, blade.v[i]), [0, 0, 0]), 1 / ids.length);
  for (let i = 0; i + 1 < rings.length; i++) { const A = rings[i], C = rings[i + 1], mid = mul3(add3(A.c, C.c), 0.5);
    for (let j = 0; j < 8; j++) { const k = (j + 1) % 8, ids = [A.ids[j], A.ids[k], C.ids[k], C.ids[j]]; blade.face(ids, sub3(centroid(ids), mid), 'side', SEC_MAT[j]); } }
  const tipId = blade.vtx([interp(BT, TIP), TIP + PY, 0]), last = rings[rings.length - 1];
  for (let j = 0; j < 8; j++) { const ids = [last.ids[j], last.ids[(j + 1) % 8], tipId]; blade.face(ids, sub3(centroid(ids), last.c), 'side', SEC_MAT[j]); }
  const first = rings[0], fc = blade.vtx(first.c);
  for (let j = 0; j < 8; j++) blade.face([fc, first.ids[j], first.ids[(j + 1) % 8]], [0, -1, 0], 'cap', 'steel');

  // satin stainless: a faint grain along the blade (+-3 %); the nail nick a dark crescent groove on the mark side
  const [NX, NY] = KNIFE.nick;
  const steel = pos => { const k = 1 + 0.06 * (vn3(pos[1] * 0.6, pos[0] * 14, 3.3) - 0.5);
    const u = (pos[1] - NY) / 0.55, w = (pos[0] - NX) / 0.1, d = u * u + w * w;
    return pos[2] > HALF * 0.5 && d < 1 ? tone3([152, 154, 158], k * (0.42 + 0.4 * d)) : tone3([152, 154, 158], k); };
  // walnut: long streaks along the handle (+-11 %), a finer figure (+-4 %); oiled
  const walnut = pos => tone3([98, 64, 42], 1 + 0.22 * (fbm3(pos[1] * 0.35 + 2.1, pos[0] * 3.2, pos[2] * 3.2) - 0.5) + 0.08 * (vn3(pos[1] * 1.4 + 6.3, pos[0] * 9, pos[2] * 9) - 0.5));
  // brass: aged, a soft low-frequency mottle (+-5 %)
  const brass = pos => tone3([178, 140, 76], 1 + 0.1 * (vn3(pos[0] * 1.3 + 4.4, pos[1] * 0.9, pos[2] * 1.3) - 0.5));
  const PARTS = parts, grip = KNIFE.grip;
  return {PARTS, bg: 'plain', MATS: {
    plain: {c: [152, 154, 158], hl: 10, sm: 158, se: 172, f0: 230},   // the atlas background: the blade's tone, flat
    steel: {c: steel, hl: 10, sm: 158, se: 172, f0: 230},             // satin stainless (LabPBR iron)
    edge: {c: [188, 190, 194], hl: 14, sm: 196, se: 205, f0: 230},    // the honed secondary bevel, polished
    spring: {c: [132, 134, 138], hl: 10, sm: 140, se: 156, f0: 230},  // the backspring, release and spacer
    brass: {c: brass, hl: 14, sm: 150, se: 168, f0: 255},             // brass: the albedo is its F0
    walnut: {c: walnut, hl: 6, sm: 132, se: 142, f0: 20},
  },
  // held by the middle of the handle in a hammer grip, the blade forward, the edge down; the grip lands on the claw
  // hammer's (fitHeld). First person: Rz(85) the blade forward and 5 deg up, 15 deg toward the screen's middle. Third
  // person: Ry(-90) the spine up, Rz(10) the blade 8 deg above level against the arm's lift. Real size to the fist (a hand
  // about 9.5 cm across, the model's 4 px): 0.42 model px a cm, a little more in first person as the hammer.
  display: {
    ...fitHeld(PARTS, grip, 'firstperson', [0, -75, 85], 0.5),
    ...fitHeld(PARTS, grip, 'thirdperson', [0, -90, 10], 0.42),
    ground: {rotation: [90, 0, 0], translation: [0, 1, 0], scale: [0.36, 0.36, 0.36]},
    fixed: {rotation: [0, 0, -45], translation: [0, 0, 0], scale: [0.62, 0.62, 0.62]},
  },
  // the inventory draws the mesh (open or closed, client/FoldingKnifeRenderer re-centres it closed)
  atlas: 512, texelsPerPx: 24, icon: {view: [0, 0, -45], coverage: 0.3, live: true, mat: {plain: [190, 192, 196], steel: [190, 192, 196], edge: [220, 222, 226], spring: [170, 172, 176], brass: [214, 172, 96], walnut: [140, 92, 60]}},
  // HUD weapon list: side view, the blade to the left, the spine up; drawn about as long as the P9-01's (36 x 22, aspect
  // 1.64): a 21 cm knife beside a 19 cm pistol
  hud: {screen: p => [-p[1], p[0]], toward: [0, 0, 1], scale: 48, aspect: 1.8},
  // the turning blade's pivot; skeleton prop points (player_props/folding_knife.json, tools/author-folding-knife-actions.mjs)
  points: {pivot: KNIFE.pivot, grip, tip: KNIFE.tip, nick: KNIFE.nick, butt: uv([-11.6, -0.1]).concat(0), release: [rel, -6.2 + PY, 0]}};
}

/**
 * Field Survival Guide (2026-10-22, docs/gameplay/field_survival_guide_v1_design.md): a perfect-bound pocket paperback,
 * 11.5 x 18 cm and 6 mm thick (48 pages and a card cover), trimmed flush on three sides like any paperback, so it is
 * one block: the cover (front, spine, back) printed from the English cover wrap, the three trimmed edges showing the
 * stacked sheets. The fore-edge corners rounded by wear, the spine corners nearly square, the cover's edges softened.
 * Authored in cm at the felling axe's scale (1 px = 1.25 inch), already centred on X / Z and resting on y = 0 (so the
 * bake does not move it): the spine at -X, the front cover facing +Z, the top at +Y. The two images come from
 * design/items/field_survival_guide_v1 (fsg_game_assets.py): field_survival_guide_cover_src.png is the flat wrap
 * (back | 6 mm spine | front, outside up), field_survival_guide_edges_src.png the sheets seen edge-on (rows = sheets).
 */
export const BOOK = {W: 11.5, H: 18, T: 0.6, corner: 0.22, spineCorner: 0.05, edge: 0.04, K: 0.8 / 2.54};
function fieldSurvivalGuide() {
  const B = 'field_survival_guide', {W, H, T, K} = BOOK, cm = v => v * K;
  const tex = name => readPng(fs.readFileSync(path.join(ROOT, 'src/main/blockbench/textures', name)));
  const wrap = tex('field_survival_guide_cover_src.png'), sheets = tex('field_survival_guide_edges_src.png');
  // bilinear sample at (u, v) in 0..1, clamped
  const sample = (img, u, v) => {
    const x = Math.max(0, Math.min(img.w - 1.001, u * img.w - 0.5)), y = Math.max(0, Math.min(img.h - 1.001, v * img.h - 0.5));
    const x0 = Math.floor(x), y0 = Math.floor(y), fx = x - x0, fy = y - y0, at = (X, Y) => { const o = (Y * img.w + X) * img.bpp; return [img.px[o], img.px[o + 1], img.px[o + 2]]; };
    const a = at(x0, y0), b = at(x0 + 1, y0), c = at(x0, y0 + 1), d = at(x0 + 1, y0 + 1);
    return a.map((_, k) => (a[k] * (1 - fx) + b[k] * fx) * (1 - fy) + (c[k] * (1 - fx) + d[k] * fx) * fy);
  };
  // the wrap is 1150 | 60 | 1150 design units across (fsg_pages.py: two 11.5 cm boards, the 6 mm spine)
  const PW = 1150, SW = 60, WW = 2 * PW + SW;
  const cover = (pos, n) => {
    const x = pos[0] / K, y = pos[1] / K, z = pos[2] / K, v = 1 - y / H;
    if (n[0] < -0.6 && Math.abs(n[0]) > Math.abs(n[2])) return sample(wrap, (PW + (z + T / 2) / T * SW) / WW, v);   // the spine
    if (z >= 0) return sample(wrap, (PW + SW + (x + W / 2) / W * PW) / WW, v);                                  // the front
    return sample(wrap, ((W / 2 - x) / W * PW) / WW, v);                                                         // the back
  };
  // the trimmed edges: along the edge on u, across the sheets on v (the front cover at the top row)
  const edges = (pos, n) => {
    const x = pos[0] / K, y = pos[1] / K, z = pos[2] / K, v = (T / 2 - z) / T;
    return sample(sheets, Math.abs(n[0]) > Math.abs(n[1]) ? y / H : (x + W / 2) / W, v);
  };

  // the outline in the cover's plane (x, y): the spine side with tight corners, the fore-edge with worn round ones
  const r = cm(BOOK.corner), rs = cm(BOOK.spineCorner), x0 = cm(-W / 2), x1 = cm(W / 2), y1 = cm(H), arc = (cx, cy, rr, a0) =>
    Array.from({length: 5}, (_, i) => { const a = (a0 + 90 * i / 4) * Math.PI / 180; return [cx + rr * Math.cos(a), cy + rr * Math.sin(a)]; });
  const outline = [...arc(x1 - r, r, r, -90), ...arc(x1 - r, y1 - r, r, 0), ...arc(x0 + rs, y1 - rs, rs, 90), ...arc(x0 + rs, rs, rs, 180)];
  const book = new Part('book', B, 'cover');
  extrude(book, 'z', {outer: orient(outline, true), holes: []}, cm(-T / 2), cm(T / 2), cm(BOOK.edge));
  // the cover takes the caps (front and back), the spine wall and every softened edge; the trimmed sides show the sheets
  for (const f of book.f) {
    if (f.tag !== 'side') continue;
    const n = newell(f.ids.map(i => book.v[i])), L = Math.hypot(...n);
    if (n[0] / L > -0.6) f.mat = 'edges';
  }

  const PARTS = [book];
  // First person: held up in the right hand by the fore-edge, thumb on the front cover a third of the way up, the book
  // reaching toward the screen's middle; Ry(-30) turns the front cover toward the camera and the middle, Rx(-20) leans
  // the top away, at the claw hammer's scale (the reference). Third person: carried at the side by the spine, the spine
  // along the fist (forward), the book hanging below it, the front cover facing out (Ry 90), at the hand saw's scale.
  // Checked offline 2026-10-22: the grip lands on the hammer's; first person the book spans 4.4 px left of the grip and
  // its cover normal is (-0.50, 0.30, 0.81); third person the fore-edge hangs 2.4 px below the spine.
  const grip = [x1 - cm(0.6), cm(H * 0.33), 0], carry = [x0 + cm(0.3), cm(H * 0.45), 0];
  return {PARTS, bg: 'plate', MATS: {
    plate: {c: [46, 71, 52], hl: 3, sm: 96, se: 110, f0: 20},   // the atlas background: the cover's green, flat
    // the cover: printed coated card, low gloss
    cover: {c: cover, hl: 3, sm: 96, se: 110, f0: 20},
    // the page edges: uncoated paper, matte
    edges: {c: edges, hl: 2, sm: 40, se: 48, f0: 20},
  },
  display: {
    ...fitHeld(PARTS, grip, 'firstperson', [-20, -30, 0], 1.4),
    ...fitHeld(PARTS, carry, 'thirdperson', [0, 90, 0], 0.7),
    ground: {rotation: [-90, 0, 0], translation: [0, 1, 0], scale: [0.5, 0.5, 0.5]},
    fixed: {rotation: [0, 180, 0], translation: [0, 0, 0], scale: [1.6, 1.6, 1.6]},
  },
  // the icon shows the front cover, turned a little to show the spine and the top edge
  atlas: 512, texelsPerPx: 72, icon: {view: [10, 25, 0]},
  points: {grip, carry, spine_mid: [x0, cm(H / 2), 0], centre: [0, cm(H / 2), 0]}};
}

/**
 * HUD weapon-list silhouette (plan 10, docs/ui/afl_inventory_grid_v0_plan.md): the baked mesh drawn orthographically in
 * the grey of the gun HUD silhouettes (textures/gui/gun/*_hud.png): flat grey lit from the top left, a darker rim on the
 * outline; 2x supersampled. spec.hud: screen(p) -> [x right, y up], toward = the direction to the viewer, scale px / unit;
 * aspect (optional): the image's widest width / height, empty rows added above and below a long thin weapon so the weapon
 * list (client/inventory/LoadoutHud, drawn to its row's height) draws it no longer than a pistol's silhouette.
 */
function drawHud(PARTS, {screen, toward, scale, aspect}) {
  const tris = [];
  for (const part of PARTS) for (const f of part.f) {
    const P = f.ids.map(i => part.v[i]), n = unit(newell(P));
    for (let k = 1; k + 1 < P.length; k++) tris.push({P: [P[0], P[k], P[k + 1]], n});
  }
  const S = scale * 2, all = tris.flatMap(t => t.P.map(screen));
  const lo = [0, 1].map(k => Math.min(...all.map(q => q[k]))), hi = [0, 1].map(k => Math.max(...all.map(q => q[k])));
  const pad = 2, W = Math.ceil((hi[0] - lo[0]) * scale) + 2 * pad, H0 = Math.ceil((hi[1] - lo[1]) * scale) + 2 * pad;
  const H = aspect ? Math.max(H0, Math.ceil(W / aspect)) : H0, padY = pad + Math.floor((H - H0) / 2);
  const W2 = W * 2, H2 = H * 2, depth = new Float64Array(W2 * H2).fill(-Infinity), shade = new Float64Array(W2 * H2);
  const toPx = p => { const [x, y] = screen(p); return [(x - lo[0]) * S + pad * 2, (hi[1] - y) * S + padY * 2, dot3(p, toward)]; };
  // light from the top left, toward the viewer
  const sx = screen([1, 0, 0]), sy = screen([0, 1, 0]), sz = screen([0, 0, 1]);
  const fromScreen = (x, y) => [0, 1, 2].map(k => x * [sx, sy, sz][k][0] + y * [sx, sy, sz][k][1]);
  const L = unit(add3(mul3(unit(fromScreen(-1, 1)), 0.55), toward));
  for (const {P, n} of tris) {
    if (dot3(n, toward) <= 0) continue;
    const Q = P.map(toPx), x0 = Math.max(0, Math.floor(Math.min(...Q.map(q => q[0])))), x1 = Math.min(W2 - 1, Math.ceil(Math.max(...Q.map(q => q[0]))));
    const y0 = Math.max(0, Math.floor(Math.min(...Q.map(q => q[1])))), y1 = Math.min(H2 - 1, Math.ceil(Math.max(...Q.map(q => q[1]))));
    const area = (Q[1][0] - Q[0][0]) * (Q[2][1] - Q[0][1]) - (Q[2][0] - Q[0][0]) * (Q[1][1] - Q[0][1]);
    if (Math.abs(area) < 1e-9) continue;
    const lum = 150 + 52 * Math.max(0, dot3(n, L));
    for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) {
      const px = x + 0.5, py = y + 0.5;
      const w0 = ((Q[1][0] - px) * (Q[2][1] - py) - (Q[2][0] - px) * (Q[1][1] - py)) / area;
      const w1 = ((Q[2][0] - px) * (Q[0][1] - py) - (Q[0][0] - px) * (Q[2][1] - py)) / area, w2 = 1 - w0 - w1;
      if (w0 < -1e-6 || w1 < -1e-6 || w2 < -1e-6) continue;
      const z = w0 * Q[0][2] + w1 * Q[1][2] + w2 * Q[2][2], i = y * W2 + x;
      if (z > depth[i]) { depth[i] = z; shade[i] = lum; }
    }
  }
  const filled = i => depth[i] > -Infinity, rgba = Buffer.alloc(W * H * 4);
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
    let a = 0, g = 0;
    for (const [dx, dy] of [[0, 0], [1, 0], [0, 1], [1, 1]]) {
      const X = x * 2 + dx, Y = y * 2 + dy, i = Y * W2 + X;
      if (!filled(i)) continue;
      // outline: a sample within 2 supersamples of empty space reads darker
      let rim = false;
      for (let oy = -2; oy <= 2 && !rim; oy++) for (let ox = -2; ox <= 2; ox++) {
        const XX = X + ox, YY = Y + oy;
        if (XX < 0 || YY < 0 || XX >= W2 || YY >= H2 || !filled(YY * W2 + XX)) { rim = true; break; }
      }
      a++; g += rim ? shade[i] * 0.72 : shade[i];
    }
    if (!a) continue;
    const v = Math.round(g / a), o = (y * W + x) * 4;
    rgba[o] = rgba[o + 1] = rgba[o + 2] = v; rgba[o + 3] = Math.round(255 * a / 4);
  }
  return {buf: png(rgba, W, H), W, H};
}

/**
 * A flat strip of cloth along a polyline in the XY plane (as revolve's cross-section, before zToX / zToY): pts the strip's
 * middle line, half its width along Z, its thickness across.
 */
function ribbon(part, pts, halfW, thick) {
  const n = pts.length, rings = [];
  for (let i = 0; i < n; i++) {
    const a = pts[Math.max(0, i - 1)], b = pts[Math.min(n - 1, i + 1)], t = unit([b[0] - a[0], b[1] - a[1], 0]), m = [-t[1], t[0], 0];
    rings.push([[1, 1], [1, -1], [-1, -1], [-1, 1]].map(([s, z]) => part.vtx([pts[i][0] + m[0] * s * thick / 2, pts[i][1] + m[1] * s * thick / 2, z * halfW])));
  }
  for (let i = 0; i + 1 < n; i++) for (let k = 0; k < 4; k++) {
    const k2 = (k + 1) % 4, p = rings[i][k], mid = mul3(add3(part.v[p], part.v[rings[i + 1][k2]]), 0.5), c = [(pts[i][0] + pts[i + 1][0]) / 2, (pts[i][1] + pts[i + 1][1]) / 2, 0];
    part.face([rings[i][k], rings[i][k2], rings[i + 1][k2], rings[i + 1][k]], sub3(mid, c), k % 2 ? 'cap' : 'side');
  }
  for (const [i, s] of [[0, -1], [n - 1, 1]]) { const t = unit([pts[Math.min(n - 1, i + 1)][0] - pts[Math.max(0, i - 1)][0], pts[Math.min(n - 1, i + 1)][1] - pts[Math.max(0, i - 1)][1], 0]);
    part.face(rings[i], mul3(t, s), 'cap'); }
}

/**
 * Gauze bandage (health V1a, docs/gameplay/survival_health_v1a.md 7.3): a roll of loose-woven cotton gauze about 7.5 cm
 * wide and 5 cm across, wound on itself (a small hole down the middle, no core), its loose end peeled off the bottom and
 * lying out flat. Plain off-white, matte. Lies along X (the roll's axis), the tail toward -Z.
 */
export const BANDAGE = {r: 2.0, hole: 0.55, half: 2.8, tail: 2.6};
function bandage() {
  const B = 'bandage', {r, hole, half} = BANDAGE;
  const roll = new Part('roll', B, 'gauze');
  revolve(roll, 0, 0, [[half, r - 0.12], [half - 0.1, r], [-half + 0.1, r], [-half, r - 0.12], [-half, hole], [half, hole]], 16);
  // the loose end: round the roll's bottom from behind, off it at the bottom and out flat (its own thickness off the roll)
  const tail = new Part('tail', B, 'gauze'), tr = r + 0.09, pts = [];
  for (let a = 200; a <= 270; a += 10) pts.push([tr * Math.cos(a * Math.PI / 180), tr * Math.sin(a * Math.PI / 180)]);
  for (const d of [0.6, 1.3, 2.0, BANDAGE.tail]) pts.push([d, -tr - 0.02 * d]);
  ribbon(tail, pts, half - 0.08, 0.09);
  for (const p of [roll, tail]) zToX(p);
  return {PARTS: [roll, tail], MATS: {
    gauze: {c: [226, 222, 210], hl: 4, sm: 34, se: 40, f0: 20},     // bleached cotton gauze: matte, a little warm
  },
  icon: {view: [25, -60, 0], coverage: 0.35, mat: {gauze: [236, 232, 222]}}};
}

/**
 * Medical alcohol (health V1a): a 16 fl oz bottle of rubbing alcohol, white HDPE, round, a blue wrap-around label with no
 * printing, a dark blue screw cap with grip ribs on its own part (cap) so the player action can unscrew it (the props
 * player_props/medical_alcohol, medical_alcohol_cap draw the parts apart, skeleton/action/PlayerProps mesh). The neck has an open mouth under the cap and a tamper ring below it. Stands on +Y.
 */
export const BOTTLE = {r: 1.7, mouth: 9.0, capFoot: 7.7, capTop: 9.25, capR: 0.8};
function medicalAlcohol() {
  const B = 'medical_alcohol', SEG = 20;
  const bottle = new Part('bottle', B, 'hdpe');
  revolve(bottle, 0, 0, [[8.3, 0], [8.3, 0.42], [9.0, 0.42], [9.0, 0.6], [8.92, 0.68], [7.6, 0.68], [7.6, 0.82], [7.4, 0.82], [7.4, 0.7], [7.2, 0.7],
    [6.9, 1.0], [6.5, 1.4], [6.0, 1.62], [5.6, 1.7], [0.25, 1.7], [0, 1.5], [0, 1.15], [0.18, 0.8], [0.2, 0]], SEG);
  const label = new Part('label', B, 'label');
  revolve(label, 0, 0, [[5.2, 1.76], [0.9, 1.76], [0.9, 1.66], [5.2, 1.66]], SEG);
  const cap = new Part('cap', B, 'cap'), CS = 24;
  revolve(cap, 0, 0, [[9.25, 0], [9.25, 0.72], [9.17, 0.8], [7.7, 0.8], [7.7, 0.72], [9.1, 0.72], [9.1, 0]], CS);
  // grip ribs: every other vertex round the cap's outside stands out
  cap.v = cap.v.map(([x, y, z]) => { const rr = Math.hypot(x, y);
    if (Math.abs(rr - 0.8) > 1e-3) return [x, y, z];
    const i = Math.round((Math.atan2(y, x) - Math.PI / CS) / (2 * Math.PI / CS));
    return i % 2 ? [x * 1.07, y * 1.07, z] : [x, y, z]; });
  for (const p of [bottle, label, cap]) zToY(p);
  return {PARTS: [bottle, label, cap], MATS: {
    hdpe: {c: [222, 224, 220], hl: 5, sm: 118, se: 128, f0: 20},    // white HDPE: a soft sheen
    label: {c: [52, 102, 168], hl: 4, sm: 92, se: 100, f0: 20},     // plain blue film label
    cap: {c: [34, 66, 128], hl: 5, sm: 110, se: 120, f0: 20},       // dark blue polypropylene
  },
  // held upright in the fist (user 2026-10-20: "酒精离手有点远"): the shared [75, 45, 0] / 2.5 px left the bottle (r 0.93 px
  // at this scale) 1-2 px off the hand; now along the forearm, its middle 0.6 px out from the hand's face (gripped 0.3 px)
  display: {
    thirdperson_righthand: {rotation: [90, 45, 0], translation: [0, 0.6, 0], scale: [0.545, 0.545, 0.545]},
  },
  icon: {view: [20, 35, 0], coverage: 0.35, mat: {hdpe: [236, 238, 234], label: [60, 112, 182], cap: [40, 76, 146]}}};
}

/**
 * Splint (fractures and splints V1, docs/gameplay/fracture_splint_v1_plan.md 5.1): two planed softwood slats about 10 px long
 * side by side, bound in the middle with a band of the bandage's gauze. No recipe before the recipe rework (user 2026-10-20).
 * Plain wood, no paint, matte. Lies along X, flat (the slats' broad faces up). The band's inside stays 0.02 px off the
 * slats: no shared faces.
 */
export const SPLINT = {len: 5.2, half: 0.72, gap: 1.0, thick: 0.45, band: 1.1};
function splint() {
  const B = 'splint', {len, half, gap, thick, band} = SPLINT;
  const slats = [-1, 1].map((s, i) => {
    const p = new Part('slat_' + i, B, 'wood');
    extrude(p, 'x', {outer: orient(roundRect(s * gap - half, 0, s * gap + half, thick, 0.12), true), holes: []}, -len, len, 0.12);
    return p;
  });
  const out = gap + half, wrap = new Part('band', B, 'gauze');
  extrude(wrap, 'x', {outer: orient(roundRect(-out - 0.16, -0.16, out + 0.16, thick + 0.16, 0.3), true),
    holes: [orient(roundRect(-out - 0.02, -0.02, out + 0.02, thick + 0.02, 0.14), false)]}, -band, band, 0);
  return {PARTS: [...slats, wrap], MATS: {
    wood: {c: [186, 152, 106], hl: 6, sm: 58, se: 66, f0: 20},      // planed pine, bare: matte, a little warm
    gauze: {c: [226, 222, 210], hl: 4, sm: 34, se: 40, f0: 20},     // the bandage's gauze
  },
  icon: {view: [30, -50, 0], coverage: 0.4, mat: {wood: [196, 162, 116], gauze: [236, 232, 222]}}};
}

/**
 * Tamarack T15 key (docs/vehicles/t15_hotwire_v1_design.md section 9, 2026-10-21): a 1990s car key, the nickel-plated steel
 * blade with its cuts on one edge, the black plastic head with a ring hole, a split ring through it. Every vehicle model has
 * its own key, its maker's badge on the head as on a real one (user 2026-10-21): this one carries Halstead's chrome
 * three-bar pine keystone (tools/halstead-badge.mjs, the grille badge B) on both faces, its top toward the ring. The same
 * key as the T15 mesh's key bone (tools/pickup-t15/cab.mjs key()), at icon size. Lies flat along X, tip at -X.
 */
function pickupT15Key() {
  const B = 'pickup_t15_key';
  const blade = new Part('blade', B, 'nickel');
  extrude(blade, 'y', {outer: orient([[-0.55, 0.8], [-0.55, -4.4], [-0.15, -5.0], [0.55, -4.7], [0.55, -4.1], [0.22, -3.6], [0.55, -3.0], [0.22, -2.4],
    [0.55, -1.8], [0.22, -1.3], [0.55, -0.8], [0.55, 0.8]], true), holes: []}, -0.14, 0.14, 0);
  const head = new Part('head', B, 'plastic');
  extrude(head, 'y', {outer: orient(roundRect(-1.5, 0.5, 1.5, 4.5, 0.7), true), holes: [orient(circle(0, 3.55, 0.48, 10), false)]}, -0.38, 0.38, 0.12);
  // split ring through the hole: a torus in the X-Y plane, its inner side through the hole
  const ring = new Part('ring', B, 'nickel'), R = 1.5, t = 0.16;
  revolve(ring, 3.55 + R, 0, Array.from({length: 8}, (_, i) => { const a = 2 * Math.PI * i / 8; return [t * Math.cos(a), R + t * Math.sin(a)]; }), 18);
  // the badge between the blade and the ring hole, set 0.04 px into the head and standing 0.05 px proud of each face
  const badge = new Part('badge', B, 'chrome'), {ring: kr, solids} = pineKeystone(0, 1.98, 1.3);
  for (const [a0, a1] of [[0.34, 0.43], [-0.43, -0.34]]) {
    extrude(badge, 'y', {outer: orient(kr[0], true), holes: [orient(kr[1], false)]}, a0, a1, 0);
    for (const L of solids) extrude(badge, 'y', {outer: orient(L, true), holes: []}, a0, a1, 0);
  }
  return {PARTS: [blade, head, ring, badge], MATS: {
    nickel: {c: [184, 182, 174], hl: 6, sm: 176, se: 186, f0: 255},   // nickel-plated steel, worn soft
    plastic: {c: [30, 30, 32], hl: 4, sm: 96, se: 104, f0: 20},       // black moulded head
    chrome: {c: [204, 208, 212], hl: 4, sm: 232, se: 240, f0: 255},   // the badge: bright chrome on the black head
  },
  // held like the wiring wrench (its rotations, user-accepted in hand), pinched at the head 2 px from its blade end, tip
  // out: translation = the wrench grip's landing point - scale * R * grip (user 2026-10-21: the block-style default
  // floated above the hand in first and third person)
  display: {
    thirdperson_righthand: {rotation: [0, -90, -80], translation: [0, -0.372, 1.277], scale: [0.55, 0.55, 0.55]},
    firstperson_righthand: {rotation: [0, -90, -110], translation: [1.13, 0.011, 2.631], scale: [0.5, 0.5, 0.5]},
  },
  icon: {view: [35, -50, 0]}};
}

/**
 * Screwdriver (2026-10-22, docs/gameplay/furniture_bench_work_v1.md 3.5.1: the workbench's third tool, for the cabinet
 * hardware's screws): an old 6 inch flat-blade cabinet screwdriver, 26 cm overall. Authored in cm along +Y from the
 * handle's butt and scaled to the felling axe's px (1 px = 1.25 inch), so it hangs and is held at the claw hammer's scale:
 * a fluted beech handle (3 cm across, eight shallow flutes, a domed butt) worn to amber varnish, a brass ferrule, a round
 * 6 mm shaft of satin tool steel flaring into a ground flat tip 6.5 mm wide, 1.2 mm thick.
 */
export const DRIVER = {handle: 10, ferrule: 11.3, length: 26, grip: 5};
function screwdriver() {
  const B = 'screwdriver', S = 1 / 3.175, X = [1, 0, 0], Z = [0, 0, 1];
  const ring = (r, n, flutes = 0, depth = 0) => Array.from({length: n}, (_, k) => { const a = 2 * Math.PI * k / n, f = flutes ? 1 - depth * (0.5 + 0.5 * Math.cos(flutes * a)) : 1;
    return [r * f * Math.cos(a) * S, r * f * Math.sin(a) * S]; });
  const blade = (w, t, n) => Array.from({length: n}, (_, k) => { const a = 2 * Math.PI * k / n, c = Math.cos(a), s = Math.sin(a);
    return [w * Math.sign(c) * Math.abs(c) ** 0.4 * S, t * Math.sign(s) * Math.abs(s) ** 0.4 * S]; });
  // handle: [y cm, radius cm], fluted between the butt's dome and the neck
  const handle = new Part('handle', B, 'beech');
  const HY = [[0, 0.55], [0.12, 1.05], [0.4, 1.38], [0.9, 1.5], [3.5, 1.52], [6.5, 1.46], [8.4, 1.3], [9.4, 1.0], [DRIVER.handle, 0.86]];
  const hc = HY.map(([y]) => [0, y * S, 0]);
  loft(handle, HY.map(([, r], i) => ringAt(handle, hc[i], X, Z, ring(r, 24, i > 1 && i < HY.length - 2 ? 8 : 0, 0.07))), hc);
  const ferrule = new Part('ferrule', B, 'brass');
  const FY = [[DRIVER.handle - 0.1, 0.84], [DRIVER.handle + 0.05, 0.86], [DRIVER.ferrule - 0.15, 0.86], [DRIVER.ferrule, 0.7]];
  const fc = FY.map(([y]) => [0, y * S, 0]);
  loft(ferrule, FY.map(([, r], i) => ringAt(ferrule, fc[i], X, Z, ring(r, 16))), fc);
  // shaft: round, then flared and ground flat at the tip (the flats facing +-z)
  const shaft = new Part('shaft', B, 'steel');
  const L = DRIVER.length, SY = [[DRIVER.ferrule - 0.1, 0.3, 0.3], [L - 2.6, 0.3, 0.3], [L - 1.2, 0.34, 0.22], [L - 0.35, 0.33, 0.08], [L, 0.32, 0.06]];
  const sc = SY.map(([y]) => [0, y * S, 0]);
  loft(shaft, SY.map(([, w, t], i) => ringAt(shaft, sc[i], X, Z, i < 2 ? ring(w, 12) : blade(w, t, 12))), sc, {mat: i => i >= 2 ? 'tip' : 'steel', capMat: 'tip'});
  // beech worn to amber under old varnish: long grain streaks, the grip a little darker where hands held it
  const beech = pos => tone3([178, 120, 64], (1 - 0.1 * Math.exp(-((pos[1] / S - 5) ** 2) / 6)) * (1 + 0.14 * (fbm3(pos[1] * 0.25, pos[0] * 6, pos[2] * 6) - 0.5) + 0.05 * (vn3(pos[1] * 1.3 + 2.2, pos[0] * 12, pos[2] * 12) - 0.5)));
  const PARTS = [handle, ferrule, shaft];
  const grip = [0, DRIVER.grip * S, 0];
  return {PARTS, bg: 'plate', MATS: {
    plate: {c: [178, 120, 64], hl: 6, sm: 150, se: 160, f0: 20},
    beech: {c: beech, hl: 6, sm: 150, se: 160, f0: 20},              // varnished beech, worn satin
    brass: {c: [176, 140, 74], hl: 10, sm: 150, se: 170, f0: 230},   // the ferrule, dulled brass
    steel: {c: [150, 152, 156], hl: 12, sm: 150, se: 166, f0: 230},  // satin tool steel
    tip: {c: [120, 122, 126], hl: 10, sm: 128, se: 140, f0: 230},    // the ground tip, darker
  },
  // held as the claw hammer (the handle in the fist, the tip where the hammer's head is: forward in first person, ahead
  // in third), its grip on the hammer's
  display: {
    ...fitHeld(PARTS, grip, 'firstperson', [0, -75, 50], 1.4),
    ...fitHeld(PARTS, grip, 'thirdperson', [0, -90, 15], 1.05),
    ground: {rotation: [0, 0, 90], translation: [0, -1, 0], scale: [0.8, 0.8, 0.8]},
    fixed: {rotation: [0, 0, -45], translation: [0, 0, 0], scale: [1.1, 1.1, 1.1]},
  },
  atlas: 512, icon: {view: [0, 0, -45], coverage: 0.3, mat: {beech: [178, 120, 64], brass: [176, 140, 74], steel: [150, 152, 156], tip: [120, 122, 126]}},
  points: {axis: [0, 0, 0], grip, tip: [0, L * S, 0], butt: [0, 0, 0]}};
}

export const ITEMS = {
  pickup_t15_key: pickupT15Key,
  clinical_thermometer: clinicalThermometer,
  wrist_thermometer: wristThermometer,
  wiring_wrench: wiringWrench,
  crowbar,
  field_chest_rig: fieldChestRig,
  canvas_backpack: canvasBackpack,
  hiking_backpack: hikingBackpack,
  gun_sling: gunSling,
  gun_sling_worn: gunSlingWorn,
  gun_sling_hook: gunSlingHook,
  gun_sling_strap: gunSlingStrap,
  pistol_holster: pistolHolster,
  simple_hearing_protection: simpleHearingProtection,
  geiger_counter: geigerCounter,
  battery_9v_alkaline: battery9v('alk'),
  battery_9v_nimh: battery9v('nimh'),
  flip_lighter: flipLighter,
  bandage,
  medical_alcohol: medicalAlcohol,
  splint,
  compass,
  clip_mount: clipMount,
  felling_axe: fellingAxe,
  claw_hammer: clawHammer,
  building_hammer: buildingHammer,
  hand_saw: handSaw,
  screwdriver,
  field_survival_guide: fieldSurvivalGuide,
  folding_knife: foldingKnife,
};

const isMain = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  const args = process.argv.slice(2), pi = args.indexOf('--preview'), check = args.includes('--check');
  const preview = pi >= 0 ? args[pi + 1] : null, ids = args.filter((a, i) => !a.startsWith('--') && (pi < 0 || i !== pi + 1));
  for (const id of ids) if (!ITEMS[id]) throw new Error('unknown equipment ' + id);
  let stale = 0;
  for (const id of ids.length ? ids : Object.keys(ITEMS)) {
    const spec = ITEMS[id]();
    // where the body anchor lands after bake centres the mesh on X / Z and rests it on y = 0
    const all = spec.PARTS.flatMap(p => p.v), lo = [0, 1, 2].map(k => Math.min(...all.map(q => q[k]))), hi = [0, 1, 2].map(k => Math.max(...all.map(q => q[k])));
    const shift = [-(lo[0] + hi[0]) / 2, -lo[1], -(lo[2] + hi[2]) / 2];
    const {outputs, stats} = bake(id, spec);
    if (spec.anchor) stats.wornAnchor = spec.anchor.map((v, k) => +(v + shift[k] - (k === 1 ? (hi[1] - lo[1]) / 2 : 0)).toFixed(4));
    // worn gear is authored in a body-part frame: its renderer translates by -origin / 16 to put it back there
    if (spec.worn) stats.origin = shift.map(v => +v.toFixed(4));
    // pivots a renderer turns parts about (px, after the bake shift)
    if (spec.points) stats.points = Object.fromEntries(Object.entries(spec.points).map(([k, q]) => [k, q.map((v, i) => +(v + shift[i]).toFixed(4))]));
    // a part turned by code (the compass card): its pivot in the baked frame
    if (spec.pivot) stats.pivot = spec.pivot.map((v, k) => +(v + shift[k]).toFixed(4));
    if (spec.hud) {
      const {buf, W, H} = drawHud(spec.PARTS, spec.hud);
      outputs.push([path.join(ROOT, `src/main/resources/assets/apocalypse_firstlight/textures/gui/melee/${id}_hud.png`), buf]);
      stats.hud = [W, H];
    }
    console.log(JSON.stringify(stats));
    for (const [file, data] of outputs) {
      const buf = Buffer.isBuffer(data) ? data : Buffer.from(data);
      if (preview) { const out = path.join(preview, path.basename(file)); fs.mkdirSync(path.dirname(out), {recursive: true}); fs.writeFileSync(out, buf); }
      else if (check) { if (!fs.existsSync(file) || !fs.readFileSync(file).equals(buf)) { console.log('stale ' + path.relative(ROOT, file)); stale++; } }
      else { fs.mkdirSync(path.dirname(file), {recursive: true}); fs.writeFileSync(file, buf); }
    }
  }
  if (check) { if (stale) process.exit(1); console.log('CHECK OK'); }
  else console.log(preview ? 'preview written to ' + preview : 'written');
}
