// AFL Micro Pistol Red Dot V1 (item pistol_red_dot): original, unbranded, low and compact slide-mounted reflex optic for
// AFL pistols. Pure Mesh with one 256 atlas: Base Color plus LabPBR _s / _n painted in the same pass. Replaces the V1
// 21-cube optic (legacy source src/main/blockbench/pistol_red_dot.bbmodel is no longer exported).
//   node tools/build-pistol-red-dot.mjs            -> writes the editable source and the source atlas maps (asset stage)
//   node tools/build-pistol-red-dot.mjs --runtime  -> also writes geo, AFL mesh sidecar (V2), runtime maps and item model
//   add --check to verify instead of writing
// Runtime files are withheld until the integration round: the live item still mounts the legacy cube optic through
// baked item models, so it switches to the Geo path together with the collimated reticle runtime and the P9 /
// Blackridge sight_slot calibration (values in docs/native_guns/pistol_red_dot_v1.md).
// Pistol optic mounting interface (gun-agnostic, asset scale 1.0):
//   origin = centre of the mounting footprint's bottom face; +Y up; -Z = muzzle forward; symmetric in X;
//   footprint x +-0.55, z +-1.05 (fits a 1.10 wide slide flat); overall height 1.05.
//   bones: sight_root (housing meshes); optic_lens (the lens mesh only: own bone, material and UV island, so a later
//   translucent material can draw it apart from the opaque housing); lens_center (child of optic_lens: lens plane centre
//   = window opening centre, rotated with the lens tilt, local +Z = lens normal toward the shooter); lens_aperture
//   (child of lens_center: top-right corner of the effective window rectangle, i.e. its half extents in the lens frame).
//   A gun's sight_slot places the origin on its slide's optic pad (mount_offset) and aims ADS through the lens centre
//   (ads_center, manual in V1).
// Lens: one thin plane (20-triangle fan) inside the window tunnel, 0.15 behind the front face, outline = window opening
// grown 0.005 so its edge tucks into the tunnel walls (never coplanar with a frame face), tilted 4 deg (top forward).
// Glass material: faint blue-grey tint with alpha 24/255 (below the 0.1 cutout threshold, so the current cutout Hybrid
// Mesh runtime leaves the window open; a translucent runtime uses it as the tint strength), smooth dielectric _s, flat
// _n. No reticle geometry: the dot belongs to a first-person collimated reticle renderer, not to the model.
// Hard-surface shapes are convex rounded polygons swept along one axis with controlled chamfers; the window hood is a
// frame (outer loop + aperture loop).
import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {convert, serializeCompact} from './export-afl-mesh.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const assets = path.join(root, 'src/main/resources/assets/apocalypse_firstlight'), bb = path.join(root, 'src/main/blockbench');
const OUT = {
  src: path.join(bb, 'pistol_red_dot_mesh.bbmodel'), geo: path.join(assets, 'geo/pistol_red_dot.geo.json'),
  mesh: path.join(assets, 'meshes/pistol_red_dot.aflmesh.json'), item: path.join(assets, 'models/item/pistol_red_dot.json'),
  maps: ['', '_s', '_n'].map(s => [path.join(bb, `textures/pistol_red_dot${s}.png`), path.join(assets, `textures/item/pistol_red_dot${s}.png`)]),
};
const ATLAS = 256;
// window (aperture) and lens plane: shared by the geometry and the reticle bones
const WIN = {y0: 0.38, y1: 0.97, hwBottom: 0.465, hwTop: 0.40, lensZ: -0.85, tilt: -4};   // tilt: degrees about X, top forward
const WINDOW = {P: [[-WIN.hwBottom, WIN.y0], [WIN.hwBottom, WIN.y0], [WIN.hwTop, WIN.y1], [-WIN.hwTop, WIN.y1]], r: [0.06, 0.06, 0.13, 0.13], segs: [2, 2, 6, 6]};
// lens centre and the effective aperture corner (unrotated frame; lens_center's rotation tilts it onto the lens plane)
const LENS = [0, (WIN.y0 + WIN.y1) / 2, WIN.lensZ], APERTURE = [0.43, WIN.y1, WIN.lensZ];   // half extents 0.43 x 0.295

// ---------------- math ----------------
const add = (a, b) => a.map((v, i) => v + b[i]), sub = (a, b) => a.map((v, i) => v - b[i]), mul = (a, k) => a.map(v => v * k);
const dot = (a, b) => a.reduce((s, v, i) => s + v * b[i], 0);
const cross = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
const norm = a => { const l = Math.hypot(...a) || 1; return a.map(v => v / l); };
const newell = P => { let n = [0, 0, 0]; for (let i = 0; i < P.length; i++) { const a = P[i], b = P[(i + 1) % P.length]; n = add(n, [(a[1] - b[1]) * (a[2] + b[2]), (a[2] - b[2]) * (a[0] + b[0]), (a[0] - b[0]) * (a[1] + b[1])]); } return n; };

// ---------------- 2D loops: convex polygons, CCW in (u, v) ----------------
// Offset every edge by d (d > 0 outward); consecutive offset lines meet at the new corners (miter).
function offsetPoly(P, d) {
  return P.map((b, i) => {
    const a = P[(i + P.length - 1) % P.length], c = P[(i + 1) % P.length];
    const e1 = norm([b[0] - a[0], b[1] - a[1]]), e2 = norm([c[0] - b[0], c[1] - b[1]]), n1 = [e1[1], -e1[0]], n2 = [e2[1], -e2[0]];
    const k = d / (1 + n1[0] * n2[0] + n1[1] * n2[1]);
    return [b[0] + k * (n1[0] + n2[0]), b[1] + k * (n1[1] + n2[1])];
  });
}
// Fillet corner i with radius r[i] using segs[i] arc segments (segs 0 keeps one sharp point).
function rounded(P, r, segs) {
  const out = [];
  P.forEach((b, i) => {
    if (!segs[i]) { out.push(b); return; }
    if (!(r[i] > 1e-4)) throw new Error('fillet radius vanished at corner ' + i);
    const a = P[(i + P.length - 1) % P.length], c = P[(i + 1) % P.length];
    const u1 = norm([a[0] - b[0], a[1] - b[1]]), u2 = norm([c[0] - b[0], c[1] - b[1]]), phi = Math.acos(Math.max(-1, Math.min(1, u1[0] * u2[0] + u1[1] * u2[1])));
    const tl = r[i] / Math.tan(phi / 2), bis = norm([u1[0] + u2[0], u1[1] + u2[1]]), cd = r[i] / Math.sin(phi / 2);
    const C = [b[0] + bis[0] * cd, b[1] + bis[1] * cd], T1 = [b[0] + u1[0] * tl, b[1] + u1[1] * tl], T2 = [b[0] + u2[0] * tl, b[1] + u2[1] * tl];
    const a0 = Math.atan2(T1[1] - C[1], T1[0] - C[0]); let a1 = Math.atan2(T2[1] - C[1], T2[0] - C[0]);
    while (a1 < a0) a1 += 2 * Math.PI;
    for (let s = 0; s <= segs[i]; s++) { const t = a0 + (a1 - a0) * s / segs[i]; out.push([C[0] + r[i] * Math.cos(t), C[1] + r[i] * Math.sin(t)]); }
  });
  return out;
}
// shape = {P, r, segs}; the loop offset by d keeps its point count (radii grow / shrink with the offset)
const loop = (s, d = 0) => rounded(d ? offsetPoly(s.P, d) : s.P, s.r.map(x => x + d), s.segs);
const ngon = (cu, cv, R, n) => ({P: Array.from({length: n}, (_, k) => [cu + R * Math.cos(2 * Math.PI * k / n), cv + R * Math.sin(2 * Math.PI * k / n)]), r: Array(n).fill(0), segs: Array(n).fill(0)});

// ---------------- parts ----------------
// One Part = one Blockbench mesh element; faces carry a material and a tag and are oriented by an outward hint.
class Part {
  constructor(name, mat) { Object.assign(this, {name, mat, v: [], f: []}); }
  vtx(p) { this.v.push(p); return this.v.length - 1; }
  face(ids, hint, tag, mat = this.mat) {
    if (dot(newell(ids.map(i => this.v[i])), hint) < 0) ids = ids.slice().reverse();
    this.f.push({ids, tag, mat});
  }
}
// Loop coordinates (u, v) and sweep coordinate w -> model space (all proper axis permutations of the same kind)
const FRAME = {
  z: p => [p[0], p[1], p[2]],   // front view (x, y), swept along z
  y: p => [p[0], p[2], p[1]],   // top view (x, z), swept along y
  x: p => [p[2], p[1], p[0]],   // side view (z, y), swept along x
};
// Sweep one or two loops (outer / aperture) through sections; chamfer bands (offset changes) are tagged 'bevel'.
function sweep(part, F, shape, sections, {hole = false, tag = 'side', mat = part.mat} = {}) {
  const loops = sections.map(([d]) => loop(shape, d)), n = loops[0].length;
  const rings = sections.map(([, w], k) => loops[k].map(([u, v]) => part.vtx(F([u, v, w]))));
  const dirW = Math.sign(sections.at(-1)[1] - sections[0][1]), s = hole ? -1 : 1;
  for (let k = 0; k + 1 < sections.length; k++) {
    const d0 = sections[k][0], d1 = sections[k + 1][0], lean = Math.sign(d0 - d1) * s * dirW;
    for (let i = 0; i < n; i++) {
      const j = (i + 1) % n, L = loops[k], o = norm([s * (L[j][1] - L[i][1]), -s * (L[j][0] - L[i][0]), 0]);
      part.face([rings[k][i], rings[k][j], rings[k + 1][j], rings[k + 1][i]], add(F(o), F([0, 0, lean])), d0 !== d1 ? 'bevel' : tag, mat);
    }
  }
  return {rings, loops, dirW};
}
// Solid prism with optional end caps (fan around the loop centroid).
function prism(part, F, shape, sections, {cap0 = true, cap1 = true, tag = 'side', mat = part.mat} = {}) {
  const {rings, loops, dirW} = sweep(part, F, shape, sections, {tag, mat});
  const cap = (k, sgn) => {
    const L = loops[k], c = L.reduce((q, p) => [q[0] + p[0] / L.length, q[1] + p[1] / L.length], [0, 0]), ci = part.vtx(F([c[0], c[1], sections[k][1]]));
    rings[k].forEach((_, i) => part.face([rings[k][i], rings[k][(i + 1) % L.length], ci], F([0, 0, sgn]), 'cap', mat));
  };
  if (cap0) cap(0, -dirW);
  if (cap1) cap(sections.length - 1, dirW);
}
// Frame: outer loop and aperture loop swept together; flat annular end faces join them (quads, matched point counts).
function frameSweep(part, F, outer, aperture, sections) {
  const o = sweep(part, F, outer, sections.map(([d, , w]) => [d, w])), h = sweep(part, F, aperture, sections.map(([, d, w]) => [d, w]), {hole: true, tag: 'tunnel'});
  const n = o.loops[0].length;
  if (h.loops[0].length !== n) throw new Error('frame loops need matching point counts');
  for (const [k, sgn] of [[0, -o.dirW], [sections.length - 1, o.dirW]])
    for (let i = 0; i < n; i++) { const j = (i + 1) % n; part.face([o.rings[k][i], o.rings[k][j], h.rings[k][j], h.rings[k][i]], F([0, 0, sgn]), 'face'); }
}

// ---------------- geometry ----------------
const hood = new Part('optic_hood', 'anodize'), body = new Part('optic_body', 'anodize'), emitter = new Part('optic_emitter', 'emitter');
const plate = new Part('optic_plate', 'plate'), controls = new Part('optic_controls', 'cap'), lens = new Part('optic_lens', 'glass');
{
  // window hood: thin frame (sides ~0.083, crown 0.08) around a large rounded-trapezoid window, front z -1.00 .. rear -0.40
  const b = 0.025;
  const outer = {P: [[-0.555, 0.09], [0.555, 0.09], [0.49, 1.05], [-0.49, 1.05]], r: [0.03, 0.03, 0.20, 0.20], segs: [2, 2, 6, 6]};
  frameSweep(hood, FRAME.z, outer, WINDOW, [[-b, b, -1.00], [0, 0, -1.00 + b], [0, 0, -0.40 - b], [-b, b, -0.40]]);
  // optical lens: one thin tilted plane across the tunnel, a fan around the lens centre, normal toward the shooter
  const t = WIN.tilt * Math.PI / 180, c = lens.vtx(LENS), toward = [0, -Math.sin(t), Math.cos(t)];
  const ring = loop(WINDOW, 0.005).map(([x, y]) => lens.vtx([x, y, LENS[2] + (y - LENS[1]) * Math.tan(t)]));
  ring.forEach((_, i) => lens.face([ring[i], ring[(i + 1) % ring.length], c], toward, 'lens'));
  // low rear body (starts inside the hood's sill), chamfered rear
  prism(body, FRAME.z, {P: [[-0.54, 0.09], [0.54, 0.09], [0.53, 0.36], [-0.53, 0.36]], r: [0.04, 0.04, 0.07, 0.07], segs: [1, 1, 3, 3]},
    [[0, -0.42], [0, 1.00 - 0.035], [-0.035, 1.00]], {cap0: false});
  // emitter housing behind the window, its top just visible at the bottom of the window from behind
  prism(emitter, FRAME.z, {P: [[-0.10, 0.34], [0.10, 0.34], [0.10, 0.45], [-0.10, 0.45]], r: [0, 0, 0.03, 0.03], segs: [0, 0, 2, 2]},
    [[0, -0.43], [0, -0.25], [-0.012, -0.238]], {cap0: false});
  // mounting base plate (footprint), chamfered top edge
  prism(plate, FRAME.y, {P: [[-0.55, -1.05], [0.55, -1.05], [0.55, 1.05], [-0.55, 1.05]], r: [0.10, 0.10, 0.10, 0.10], segs: [3, 3, 3, 3]},
    [[0, 0], [0, 0.085], [-0.02, 0.105]]);
  // controls: two brightness buttons (left), top adjuster / battery cap, two mount screws, windage cap (right)
  for (const [z0, z1] of [[0.08, 0.30], [0.36, 0.58]])
    prism(controls, FRAME.x, {P: [[z0, 0.16], [z1, 0.16], [z1, 0.27], [z0, 0.27]], r: [0.035, 0.035, 0.035, 0.035], segs: [1, 1, 1, 1]},
      [[0, -0.52], [0, -0.548], [-0.01, -0.558]], {cap0: false, mat: 'rubber'});
  prism(controls, FRAME.y, ngon(0, 0.50, 0.19, 14), [[0, 0.34], [0, 0.378], [-0.012, 0.39]], {cap0: false});
  for (const x of [-0.36, 0.36]) prism(controls, FRAME.y, ngon(x, 0.86, 0.055, 8), [[0, 0.34], [0, 0.372]], {cap0: false, mat: 'steel'});
  prism(controls, FRAME.x, ngon(0.62, 0.22, 0.065, 10), [[0, 0.52], [0, 0.545], [-0.008, 0.553]], {cap0: false});
}
const PARTS = [hood, body, emitter, plate, controls, lens];

// ---------------- UV: planar islands (normal flood fill), shelf packing ----------------
const islands = [];
for (const p of PARTS) {
  const F = p.f.map(f => ({f, n: norm(newell(f.ids.map(i => p.v[i])))}));
  const edge = new Map();
  F.forEach((o, i) => o.f.ids.forEach((a, k) => { const b = o.f.ids[(k + 1) % o.f.ids.length], e = a < b ? a + ',' + b : b + ',' + a; (edge.get(e) || edge.set(e, []).get(e)).push(i); }));
  const seen = new Uint8Array(F.length);
  for (let s = 0; s < F.length; s++) {
    if (seen[s]) continue; seen[s] = 1; const q = [s], mem = [], n0 = F[s].n;
    while (q.length) {
      const i = q.pop(); mem.push(i);
      const ids = F[i].f.ids;
      for (let k = 0; k < ids.length; k++) {
        const a = ids[k], b = ids[(k + 1) % ids.length], e = a < b ? a + ',' + b : b + ',' + a;
        for (const j of edge.get(e)) if (!seen[j] && F[j].f.mat === F[s].f.mat && F[j].f.tag === F[s].f.tag && dot(F[j].n, n0) > 0.9 && dot(F[j].n, F[i].n) > 0.96) { seen[j] = 1; q.push(j); }
      }
    }
    const n = norm(mem.reduce((acc, i) => add(acc, F[i].n), [0, 0, 0]));
    let t = sub([0, 0, 1], mul(n, n[2])); if (Math.hypot(...t) < 0.3) t = sub([1, 0, 0], mul(n, n[0])); t = norm(t); const bt = cross(n, t);
    const vs = [...new Set(mem.flatMap(i => F[i].f.ids))], uv = new Map(vs.map(i => [i, [dot(p.v[i], t), -dot(p.v[i], bt)]]));
    const us = [...uv.values()], u0 = Math.min(...us.map(a => a[0])), v0 = Math.min(...us.map(a => a[1]));
    islands.push({part: p, faces: mem.map(i => F[i]), uv, u0, v0, w: Math.max(...us.map(a => a[0])) - u0, h: Math.max(...us.map(a => a[1])) - v0});
  }
}
const PAD = 2;
function pack(S) {
  let x = PAD, y = PAD, rowH = 0;
  for (const is of islands.slice().sort((a, b) => b.h - a.h || b.w - a.w)) {
    const W = Math.ceil(is.w * S) + 1, H = Math.ceil(is.h * S) + 1;
    if (x + W + PAD > ATLAS) { x = PAD; y += rowH + PAD; rowH = 0; }
    if (y + H + PAD > ATLAS) return false;
    Object.assign(is, {px: x, py: y, W, H}); x += W + PAD; rowH = Math.max(rowH, H);
  }
  return true;
}
let S = 160; while (!pack(S)) S -= 1;
const uvOf = (is, vi) => { const [u, v] = is.uv.get(vi); return [(u - is.u0) * S + is.px + 0.5, (v - is.v0) * S + is.py + 0.5]; };

// ---------------- Base Color + LabPBR (same raster pass) ----------------
// Hard-anodised black aluminium housing (dielectric satin), phosphated steel plate, rubber buttons, steel screws.
// Chamfers are one clean lighter tone per face (no per-pixel rims); the aperture tunnel is matte anti-glare with AO.
const MAT = {   // base colour, smoothness, F0, AO; bevel: [colour, smoothness]
  anodize: {c: [40, 41, 43], sm: 95, f0: 10, bevel: [[54, 55, 58], 120]},
  plate:   {c: [35, 35, 36], sm: 85, f0: 255, bevel: [[50, 50, 52], 110]},
  emitter: {c: [22, 23, 26], sm: 170, f0: 10, ao: 230, bevel: [[34, 35, 38], 170]},
  rubber:  {c: [30, 30, 31], sm: 35, f0: 10, bevel: [[38, 38, 39], 40]},
  cap:     {c: [46, 47, 49], sm: 100, f0: 10, bevel: [[60, 61, 64], 125]},
  steel:   {c: [70, 71, 74], sm: 140, f0: 255, bevel: [[80, 81, 84], 150]},
  glass:   {c: [168, 186, 194], alpha: 24, sm: 235, f0: 10, bevel: [[168, 186, 194], 235]},   // optical tint, see header
};
function shade(f, n) {
  const m = MAT[f.mat]; let c = m.c, sm = m.sm, ao = m.ao ?? 255;
  if (f.tag === 'bevel') [c, sm] = m.bevel;
  if (f.tag === 'tunnel') { c = [28, 29, 31]; sm = 55; ao = 215; }
  const k = 1 + 0.03 * n[1] - 0.02 * Math.max(0, -n[1]);   // faint top light, same term as the P9 painter
  return {c: c.map(v => v * k), a: m.alpha ?? 255, s: [sm, m.f0, 0, 255], n: [128, 128, ao, 255]};
}
const img = Buffer.alloc(ATLAS * ATLAS * 4), spec = Buffer.alloc(ATLAS * ATLAS * 4), nrm = Buffer.alloc(ATLAS * ATLAS * 4);
for (let i = 0; i < ATLAS * ATLAS; i++) { img.set([32, 33, 35, 255], i * 4); spec.set([90, 10, 0, 255], i * 4); nrm.set([128, 128, 255, 255], i * 4); }
const clamp8 = a => a.map(v => Math.max(0, Math.min(255, Math.round(v))));
const put = (x, y, t) => { if (x < 0 || y < 0 || x >= ATLAS || y >= ATLAS) return; const k = (y * ATLAS + x) * 4; img.set([...clamp8(t.c), t.a], k); spec.set(clamp8(t.s), k); nrm.set(clamp8(t.n), k); };
const covered = new Uint8Array(ATLAS * ATLAS);
// island padding in the tone of its first face; the glass island first, so opaque neighbours own the shared gap texels
for (const is of islands.slice().sort((a, b) => (b.faces[0].f.mat === 'glass') - (a.faces[0].f.mat === 'glass'))) {
  const t = shade(is.faces[0].f, is.faces[0].n);
  for (let y = is.py - PAD; y < is.py + is.H + PAD; y++) for (let x = is.px - PAD; x < is.px + is.W + PAD; x++) put(x, y, t);
}
for (const pass of ['inside', 'fill']) for (const is of islands) for (const {f, n} of is.faces) {
  const t = shade(f, n), A = f.ids.map(i => uvOf(is, i));
  for (let q = 1; q + 1 < A.length; q++) {
    const T = [A[0], A[q], A[q + 1]], den = (T[1][1] - T[2][1]) * (T[0][0] - T[2][0]) + (T[2][0] - T[1][0]) * (T[0][1] - T[2][1]);
    if (Math.abs(den) < 1e-9) continue;
    const tol = pass === 'inside' ? 0 : -0.75 / Math.max(1, Math.sqrt(Math.abs(den)));
    const x0 = Math.floor(Math.min(...T.map(a => a[0])) - 1), x1 = Math.ceil(Math.max(...T.map(a => a[0])) + 1);
    const y0 = Math.floor(Math.min(...T.map(a => a[1])) - 1), y1 = Math.ceil(Math.max(...T.map(a => a[1])) + 1);
    for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) {
      const X = x + 0.5, Y = y + 0.5;
      const l1 = ((T[1][1] - T[2][1]) * (X - T[2][0]) + (T[2][0] - T[1][0]) * (Y - T[2][1])) / den;
      const l2 = ((T[2][1] - T[0][1]) * (X - T[2][0]) + (T[0][0] - T[2][0]) * (Y - T[2][1])) / den, l3 = 1 - l1 - l2;
      if (l1 < tol || l2 < tol || l3 < tol || x < 0 || y < 0 || x >= ATLAS || y >= ATLAS) continue;
      const i = y * ATLAS + x; if (pass === 'fill' && covered[i]) continue; covered[i] = 1; put(x, y, t);
    }
  }
}

// ---------------- outputs ----------------
const crcTable = Array.from({length: 256}, (_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xEDB88320 ^ (c >>> 1) : c >>> 1; return c >>> 0; });
const crc32 = buf => { let c = 0xFFFFFFFF; for (const b of buf) c = crcTable[(c ^ b) & 0xFF] ^ (c >>> 8); return (c ^ 0xFFFFFFFF) >>> 0; };
const png = rgba => {
  const chunk = (type, data) => { const len = Buffer.alloc(4); len.writeUInt32BE(data.length); const td = Buffer.concat([Buffer.from(type), data]), crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(td)); return Buffer.concat([len, td, crc]); };
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(ATLAS, 0); ihdr.writeUInt32BE(ATLAS, 4); ihdr[8] = 8; ihdr[9] = 6;
  const raw = Buffer.alloc(ATLAS * (ATLAS * 4 + 1)); for (let y = 0; y < ATLAS; y++) rgba.copy(raw, y * (ATLAS * 4 + 1) + 1, y * ATLAS * 4, (y + 1) * ATLAS * 4);
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw, {level: 9})), chunk('IEND', Buffer.alloc(0))]);
};
const PNG = [png(img), png(spec), png(nrm)];
const uuid = s => { const h = createHash('sha256').update('afl-micro-pistol-red-dot-v1:' + s).digest('hex'); return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-8${h.slice(17, 20)}-${h.slice(20, 32)}`; };
const r8 = v => +v.toFixed(8) || 0;
const elements = PARTS.map(p => {
  const key = i => i.toString(36).padStart(4, '0'), vertices = {}, faces = {};
  p.v.forEach((q, i) => { vertices[key(i)] = q.map(r8); });
  let fi = 0;
  for (const is of islands.filter(i => i.part === p)) for (const {f} of is.faces)
    faces['f' + key(fi++)] = {uv: Object.fromEntries(f.ids.map(i => [key(i), uvOf(is, i).map(r8)])), vertices: f.ids.map(key), texture: 0};
  return {name: p.name, color: 0, origin: [0, 0, 0], rotation: [0, 0, 0], shading: 'flat', export: true, visibility: true, locked: false,
    render_order: 'default', scope: 0, allow_mirror_modeling: true, vertices, faces, type: 'mesh', uuid: uuid(p.name)};
});
const group = (name, origin, rotation = [0, 0, 0]) => ({name, uuid: uuid('group:' + name), export: true, locked: false, scope: 0, selected: false, visibility: true,
  _static: {properties: {}, temp_data: {}}, origin, rotation, color: 0, children: [], reset: false, shade: true, mirror_uv: false,
  autouv: 0, isOpen: true, primary_selected: false});
// sight_root > optic_lens > lens_center (lens tilt) > lens_aperture; the lens mesh is the only element under optic_lens
const groups = [group('sight_root', [0, 0, 0]), group('optic_lens', LENS), group('lens_center', LENS, [WIN.tilt, 0, 0]), group('lens_aperture', APERTURE)];
const PARENT = {optic_lens: 'sight_root', lens_center: 'optic_lens', lens_aperture: 'lens_center'};
const lensElement = elements.find(e => e.name === 'optic_lens');
const source = {meta: {format_version: '5.0', model_format: 'free', box_uv: false}, name: 'pistol_red_dot', model_identifier: '', visible_box: [1, 1, 0],
  variable_placeholders: '', variable_placeholder_buttons: [], timeline_setups: [], unhandled_root_fields: {}, resolution: {width: ATLAS, height: ATLAS},
  elements, groups,
  outliner: [{uuid: groups[0].uuid, isOpen: true, children: [...elements.filter(e => e !== lensElement).map(e => e.uuid),
    {uuid: groups[1].uuid, isOpen: true, children: [lensElement.uuid, {uuid: groups[2].uuid, isOpen: true, children: [{uuid: groups[3].uuid, isOpen: true, children: []}]}]}]}],
  textures: [{name: 'pistol_red_dot.png', relative_path: 'textures/pistol_red_dot.png', folder: '', namespace: '', id: '0', group: '', scope: 0,
    width: ATLAS, height: ATLAS, uv_width: ATLAS, uv_height: ATLAS, particle: false, use_as_default: false, layers_enabled: false, sync_to_project: '',
    file_format: 'png', render_mode: 'default', render_sides: 'auto', wrap_mode: 'limited', pbr_channel: 'color', fps: 7, frame_time: 1,
    frame_order_type: 'loop', frame_order: '', frame_interpolate: false, visible: true, internal: true, saved: true, uuid: uuid('texture'),
    source: 'data:image/png;base64,' + PNG[0].toString('base64')}],
  animations: []};
// geo pivots use the native exporter sign convention [-x, y, z]
const geo = {format_version: '1.12.0', 'minecraft:geometry': [{description: {identifier: 'geometry.pistol_red_dot', texture_width: ATLAS, texture_height: ATLAS,
  visible_bounds_width: 1, visible_bounds_height: 1, visible_bounds_offset: [0, 0, 0]}, bones: [{name: 'sight_root', pivot: [0, 0, 0]},
  ...groups.slice(1).map(g => ({name: g.name, parent: PARENT[g.name], pivot: [-g.origin[0] || 0, g.origin[1], g.origin[2]],
    ...(g.rotation.some(v => v) ? {rotation: [-g.rotation[0] || 0, -g.rotation[1] || 0, g.rotation[2]]} : {})}))]}]};
const sidecar = convert(source, geo, {}, 'pistol_red_dot_mesh.bbmodel', 2);
// Inventory presentation: builtin/entity through NativeMuzzleRendering.ItemRenderer, which centres the mesh bounds.
const item = {parent: 'builtin/entity', gui_light: 'side', textures: {particle: 'apocalypse_firstlight:item/pistol_red_dot'}, display: {
  gui: {rotation: [20, 140, 0], scale: [6.2, 6.2, 6.2]},
  ground: {translation: [0, 2, 0], scale: [0.9, 0.9, 0.9]},
  fixed: {rotation: [0, 180, 0], scale: [5.6, 5.6, 5.6]},
  firstperson_righthand: {rotation: [0, -35, 0], translation: [0, 1, 0], scale: [0.9, 0.9, 0.9]},
  firstperson_lefthand: {rotation: [0, -35, 0], translation: [0, 1, 0], scale: [0.9, 0.9, 0.9]},
  thirdperson_righthand: {rotation: [0, 0, 0], translation: [0, 1, 0], scale: [0.9, 0.9, 0.9]},
  thirdperson_lefthand: {rotation: [0, 0, 0], translation: [0, 1, 0], scale: [0.9, 0.9, 0.9]}}};

const sources = [[OUT.src, JSON.stringify(source)], ...OUT.maps.map(([s], k) => [s, PNG[k]])];
const runtime = [[OUT.geo, JSON.stringify(geo, null, 2) + '\n'], [OUT.mesh, serializeCompact(sidecar)], [OUT.item, JSON.stringify(item, null, 2) + '\n'],
  ...OUT.maps.map(([, r], k) => [r, PNG[k]])];
const outputs = process.argv.includes('--runtime') ? [...sources, ...runtime] : sources;
const pivotOf = Object.fromEntries(geo['minecraft:geometry'][0].bones.map(b => [b.name, [-b.pivot[0], b.pivot[1], b.pivot[2]]]));
const faces = sidecar.parts.flatMap(p => p.faces), all = sidecar.parts.flatMap(p => p.vertices.map(v => v.slice(0, 3).map((x, i) => x * 16 + pivotOf[p.bone][i])));
const ext = i => [Math.min(...all.map(q => q[i])), Math.max(...all.map(q => q[i]))].map(v => +v.toFixed(3));
console.log(JSON.stringify({parts: sidecar.parts.map(p => `${p.name}:${p.faces.reduce((s, f) => s + f.length - 2, 0)}`),
  triangles: faces.reduce((s, f) => s + f.length - 2, 0), quads: faces.filter(f => f.length === 4).length, faces: faces.length,
  bounds: {x: ext(0), y: ext(1), z: ext(2)}, texelPerUnit: S, islands: islands.length}));
if (process.argv.includes('--check')) {
  for (const [file, data] of outputs) { const cur = fs.existsSync(file) ? fs.readFileSync(file) : null; if (!cur || !cur.equals(Buffer.isBuffer(data) ? data : Buffer.from(data))) throw new Error('stale ' + path.relative(root, file)); }
  console.log('CHECK OK');
} else {
  for (const [file, data] of outputs) fs.writeFileSync(file, data);
  console.log('wrote ' + outputs.map(([f]) => path.relative(root, f)).join(', '));
}
