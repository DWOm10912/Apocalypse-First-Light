// AFL Equipment Meshes V1: worn / carried equipment items as Pure Mesh in the world views (hand, ground, item frame, and
// worn on the body through their Curios renderer) and as a 16x16 2D icon in the inventory, by the same bake as the
// Material Meshes (tools/build-material-meshes-v1.mjs: one bone, one 256 LabPBR atlas Base Color / _s / _n, glass by
// material alpha). Add a new piece of equipment as one more entry in ITEMS.
//   node tools/build-equipment-meshes-v1.mjs [id ...]               -> writes source, atlas, geo, AFL mesh sidecar, icon, item model
//   node tools/build-equipment-meshes-v1.mjs [id ...] --check       -> verifies every output is up to date
//   node tools/build-equipment-meshes-v1.mjs [id ...] --preview DIR -> writes the outputs into DIR only (offline review)
// The printed stats give each item's renderer vertical offset (0.5 - height / 32, for AflStaticMeshItemClient) and, for
// worn items, where the body anchor ends up after bake re-centres the mesh (wornAnchor, px).
// Items are authored at icon size (px), like the material meshes. Materials follow the AFL rules: plain surfaces, no
// painted marks or text, coated rather than bare metal on large faces, glass F0 10 with enough alpha to survive shaders.
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {Part, extrude, revolve, area2} from './cube-slab-mesh-lib.mjs';
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

export const ITEMS = {
  clinical_thermometer: clinicalThermometer,
  wrist_thermometer: wristThermometer,
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
