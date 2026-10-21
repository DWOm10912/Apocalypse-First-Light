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
import {Part, extrude, revolve, area2, newell, png, triangulate} from './cube-slab-mesh-lib.mjs';
import {bake} from './build-material-meshes-v1.mjs';

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

/** Seven-segment layout of one LCD digit (a..g) and its decimal point, shared with the HUD (client/GeigerReadout). */
export const GEIGER_LCD = {cells: 4, x0: -2.62, pitch: 1.31, glyph: 0.98, zTop: -3.82, zBottom: -1.38, bar: 0.24, gap: 0.035, dp: 0.24};
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
  extrude(add(new Part('shell', B, 'shell')), 'y', {outer: plan(-6.4, -3.85, 6.4, 3.85, 0.9), holes: []}, 0.12, 2.88, 0.35);
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
  for (const z of [-4.9, 4.9]) extrude(add(new Part('screw_' + (z < 0 ? 'top' : 'bottom'), B, 'steel')), 'y', {outer: orient(circle(z, 0, 0.2, 8), true), holes: []}, 0.06, 0.13, 0);
  return {PARTS: parts, MATS: {
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
  atlas: 512, icon: {view: [60, 0, 0], coverage: 0.4, xray: ['lcd_glass'], hide: parts.filter(p => /^(seg_|dp_|led_on)/.test(p.name)).map(p => p.name),
    mat: {rubber: [226, 176, 52], shell: [70, 72, 76], lcd: [168, 178, 156]}}};
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
const vn3 = (x, y, z) => {
  const xi = Math.floor(x), yi = Math.floor(y), zi = Math.floor(z), s = t => t * t * (3 - 2 * t), l = (a, b, t) => a + (b - a) * t;
  const sx = s(x - xi), sy = s(y - yi), sz = s(z - zi), h = (a, b, c) => hash3(a, b, c * 7919 + 17);
  return l(l(l(h(xi, yi, zi), h(xi + 1, yi, zi), sx), l(h(xi, yi + 1, zi), h(xi + 1, yi + 1, zi), sx), sy),
    l(l(h(xi, yi, zi + 1), h(xi + 1, yi, zi + 1), sx), l(h(xi, yi + 1, zi + 1), h(xi + 1, yi + 1, zi + 1), sx), sy), sz);
};
const fbm3 = (x, y, z) => 0.6 * vn3(x, y, z) + 0.3 * vn3(x * 2.03 + 7.1, y * 2.03 + 3.3, z * 2.03 + 1.7) + 0.1 * vn3(x * 4.1 + 2.9, y * 4.1 + 8.1, z * 4.1 + 5.3);

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
 * HUD weapon-list silhouette (plan 10, docs/ui/afl_inventory_grid_v0_plan.md): the baked mesh drawn orthographically in
 * the grey of the gun HUD silhouettes (textures/gui/gun/*_hud.png): flat grey lit from the top left, a darker rim on the
 * outline; 2x supersampled. spec.hud: screen(p) -> [x right, y up], toward = the direction to the viewer, scale px / unit.
 */
function drawHud(PARTS, {screen, toward, scale}) {
  const tris = [];
  for (const part of PARTS) for (const f of part.f) {
    const P = f.ids.map(i => part.v[i]), n = unit(newell(P));
    for (let k = 1; k + 1 < P.length; k++) tris.push({P: [P[0], P[k], P[k + 1]], n});
  }
  const S = scale * 2, all = tris.flatMap(t => t.P.map(screen));
  const lo = [0, 1].map(k => Math.min(...all.map(q => q[k]))), hi = [0, 1].map(k => Math.max(...all.map(q => q[k])));
  const pad = 2, W = Math.ceil((hi[0] - lo[0]) * scale) + 2 * pad, H = Math.ceil((hi[1] - lo[1]) * scale) + 2 * pad;
  const W2 = W * 2, H2 = H * 2, depth = new Float64Array(W2 * H2).fill(-Infinity), shade = new Float64Array(W2 * H2);
  const toPx = p => { const [x, y] = screen(p); return [(x - lo[0]) * S + pad * 2, (hi[1] - y) * S + pad * 2, dot3(p, toward)]; };
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

export const ITEMS = {
  clinical_thermometer: clinicalThermometer,
  wrist_thermometer: wristThermometer,
  wiring_wrench: wiringWrench,
  crowbar,
  field_chest_rig: fieldChestRig,
  canvas_backpack: canvasBackpack,
  hiking_backpack: hikingBackpack,
  gun_sling: gunSling,
  gun_sling_worn: gunSlingWorn,
  pistol_holster: pistolHolster,
  simple_hearing_protection: simpleHearingProtection,
  geiger_counter: geigerCounter,
  flip_lighter: flipLighter,
  felling_axe: fellingAxe,
  claw_hammer: clawHammer,
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
