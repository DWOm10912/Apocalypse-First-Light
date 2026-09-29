// AFL Rifle Micro Red Dot V1 (item rifle_red_dot_01): original, unbranded closed-tube micro red dot on an integral riser
// for AFL rifles. Pure Mesh, one 512 atlas: Base Color plus LabPBR _s / _n painted in the same pass (V2 sidecar, lens part
// on the translucent layer). Replaces the V1 29-cube optic extracted from the BR51 source (legacy source
// src/main/blockbench/rifle_red_dot_01.bbmodel is kept for history and no longer exported).
//   node tools/build-rifle-red-dot-01.mjs          -> writes the editable source, the source maps and every runtime file
//   node tools/build-rifle-red-dot-01.mjs --check  -> verifies every output is up to date
// Mount interface rifle_optic_rail (gun-agnostic, asset scale 1.0, units = AFL rifle model units):
//   origin = the rail's top face, centred under the clamp; +Y up; -Z = muzzle forward; symmetric in X.
//   The clamp occupies x +-FOOT.x, z +-FOOT.z and hangs FOOT.jaw below the rail top (it hides the rail flanks); a gun
//   needs a rail top at the origin, at least 0.8 wide, over the clamp length. Optical axis = AXIS.y above the rail top.
//   bones: sight_root (housing meshes); optic_lens (the lens mesh only, translucent layer); lens_center (child: lens plane
//   centre on the optical axis, local +Z = lens normal toward the shooter); lens_aperture (child of lens_center: its
//   offset = the effective window's half extents; the window is an ellipse, optics/rifle_red_dot_01.json).
//   A gun's sight_slot puts the origin on its rail (mount_offset) and aims ADS through lens_center (ads_center).
// Structure: 32-sided tube (objective hood marked by a painted ring line, open rear with a flat eyepiece face, matte bore,
// LED emitter bump at the rear bottom of the bore) on an integral base, elevation (top) and windage (right) turrets with
// protective caps, brightness / battery knob (left), riser = rail clamp + web with a through lightening window + saddle,
// throw lever with pivot boss (right) and cross-bolt nut (left). One thin lens plane inside the hood. No markings.
// Lathe surfaces unwrap as strips / facet columns / discs (affine UV, so V2 keeps quads); their _n tilts every texel to the
// true surface normal, so the facets shade round under shader packs. Hard-surface pieces are rounded polygons swept along
// one axis with chamfers (same builder as tools/build-pistol-red-dot.mjs).
import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {convert, serializeCompact} from './export-afl-mesh.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const assets = path.join(root, 'src/main/resources/assets/apocalypse_firstlight'), bb = path.join(root, 'src/main/blockbench');
const OUT = {
  src: path.join(bb, 'rifle_red_dot_01_mesh.bbmodel'), geo: path.join(assets, 'geo/rifle_red_dot_01.geo.json'),
  mesh: path.join(assets, 'meshes/rifle_red_dot_01.aflmesh.json'), item: path.join(assets, 'models/item/rifle_red_dot_01.json'),
  maps: ['', '_s', '_n'].map(s => [path.join(bb, `textures/rifle_red_dot_01${s}.png`), path.join(assets, `textures/item/rifle_red_dot_01${s}.png`)]),
};
const ATLAS = 512;
export const AXIS = {y: 2.0};                                   // optical axis height above the rail top
export const TUBE = {ro: 0.66, ri: 0.53, front: -0.95, rear: 0.85};
export const LENS = [0, AXIS.y, -0.73];                         // lens plane centre, 0.22 behind the front face
export const APERTURE = 0.51;                                   // effective window radius in the lens plane (bore 0.53)
export const FOOT = {x: 0.66, z: 1.05, jaw: -0.22};
const N = 32, PHASE = -Math.PI / 2;                              // tube: a vertex (and the strip seam) at the very bottom
const LAYERS = {optic_lens: 'translucent'};

// ---------------- math ----------------
const add = (a, b) => a.map((v, i) => v + b[i]), sub = (a, b) => a.map((v, i) => v - b[i]), mul = (a, k) => a.map(v => v * k);
const dot = (a, b) => a.reduce((s, v, i) => s + v * b[i], 0);
const cross = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
const norm = a => { const l = Math.hypot(...a) || 1; return a.map(v => v / l); };
const newell = P => { let n = [0, 0, 0]; for (let i = 0; i < P.length; i++) { const a = P[i], b = P[(i + 1) % P.length]; n = add(n, [(a[1] - b[1]) * (a[2] + b[2]), (a[2] - b[2]) * (a[0] + b[0]), (a[0] - b[0]) * (a[1] + b[1])]); } return n; };
const smooth = (a, b, x) => { const t = Math.max(0, Math.min(1, (x - a) / (b - a))); return t * t * (3 - 2 * t); };

// ---------------- 2D loops: convex polygons, CCW in (u, v) ----------------
function offsetPoly(P, d) {
  return P.map((b, i) => {
    const a = P[(i + P.length - 1) % P.length], c = P[(i + 1) % P.length];
    const e1 = norm([b[0] - a[0], b[1] - a[1]]), e2 = norm([c[0] - b[0], c[1] - b[1]]), n1 = [e1[1], -e1[0]], n2 = [e2[1], -e2[0]];
    const k = d / (1 + n1[0] * n2[0] + n1[1] * n2[1]);
    return [b[0] + k * (n1[0] + n2[0]), b[1] + k * (n1[1] + n2[1])];
  });
}
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
const loop = (s, d = 0) => rounded(d ? offsetPoly(s.P, d) : s.P, s.r.map(x => x + d), s.segs);
const ngon = (cu, cv, R, n) => ({P: Array.from({length: n}, (_, k) => [cu + R * Math.cos(2 * Math.PI * k / n), cv + R * Math.sin(2 * Math.PI * k / n)]), r: Array(n).fill(0), segs: Array(n).fill(0)});

// ---------------- parts ----------------
// One Part = one Blockbench mesh element. Faces carry a material and a tag and are oriented by an outward hint. Lathe faces
// also carry an explicit UV island (island-local units per corner), the analytic surface normal per corner (painted into
// _n) and the radial share of the profile normal.
class Part {
  constructor(name, mat) { Object.assign(this, {name, mat, v: [], f: []}); }
  vtx(p) { this.v.push(p); return this.v.length - 1; }
  face(ids, hint, tag, mat = this.mat, extra = null) {
    let order = ids.map((_, i) => i);
    if (dot(newell(ids.map(i => this.v[i])), hint) < 0) order = order.reverse();
    const f = {ids: order.map(i => ids[i]), tag, mat};
    if (extra) Object.assign(f, {isl: extra.isl, uvl: order.map(i => extra.uvl[i]), nrm: extra.nrm && order.map(i => extra.nrm[i])});
    this.f.push(f);
  }
}
const FRAME = {
  z: p => [p[0], p[1], p[2]],   // front view (x, y), swept along z
  y: p => [p[0], p[2], p[1]],   // top view (x, z), swept along y
  x: p => [p[2], p[1], p[0]],   // side view (z, y), swept along x
};
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
function prism(part, F, shape, sections, {cap0 = true, cap1 = true, tag = 'side', mat = part.mat} = {}) {
  const {rings, loops, dirW} = sweep(part, F, shape, sections, {tag, mat});
  const cap = (k, sgn) => {
    const L = loops[k], c = L.reduce((q, p) => [q[0] + p[0] / L.length, q[1] + p[1] / L.length], [0, 0]), ci = part.vtx(F([c[0], c[1], sections[k][1]]));
    rings[k].forEach((_, i) => part.face([rings[k][i], rings[k][(i + 1) % L.length], ci], F([0, 0, sgn]), 'cap', mat));
  };
  if (cap0) cap(0, -dirW);
  if (cap1) cap(sections.length - 1, dirW);
}
function frameSweep(part, F, outer, aperture, sections, {mat = part.mat} = {}) {
  const o = sweep(part, F, outer, sections.map(([d, , w]) => [d, w]), {mat}), h = sweep(part, F, aperture, sections.map(([, d, w]) => [d, w]), {hole: true, tag: 'tunnel', mat});
  const n = o.loops[0].length;
  if (h.loops[0].length !== n) throw new Error('frame loops need matching point counts');
  for (const [k, sgn] of [[0, -o.dirW], [sections.length - 1, o.dirW]])
    for (let i = 0; i < n; i++) { const j = (i + 1) % n; part.face([o.rings[k][i], o.rings[k][j], h.rings[k][j], h.rings[k][i]], F([0, 0, sgn]), 'face', mat); }
}
// Lathe: point(r, theta, a) = C + a A + r (cos theta U + sin theta V). pts = [[r, a], [r, a, tag, mat?], ...]; the tag / mat
// of a point belong to the segment ending there. Outward side from the traversal direction: normal = (da, -dr) in (r, a).
// Consecutive constant-radius segments share one 'strip' island (angle -> u, arc length -> v), consecutive tapered ones one
// 'facets' island (every facet flat and congruent in its own column), and every flat annulus gets its own 'disc' island.
const ISLANDS = [];
function lathe(part, {C, A, U, V}, n, phase, pts, mat0 = part.mat) {
  const P = (r, th, a) => add(add(C, mul(A, a)), add(mul(U, r * Math.cos(th)), mul(V, r * Math.sin(th))));
  const ang = k => phase + 2 * Math.PI * k / n, HS = Math.sin(Math.PI / n), radial = th => add(mul(U, Math.cos(th)), mul(V, Math.sin(th)));
  const rings = pts.map(([r, a]) => r < 1e-9 ? [part.vtx(P(0, 0, a))] : Array.from({length: n}, (_, k) => part.vtx(P(r, ang(k), a))));
  const kind = i => { const dr = pts[i + 1][0] - pts[i][0], da = pts[i + 1][1] - pts[i][1]; return Math.abs(dr) < 1e-9 ? 'strip' : Math.abs(da) < 1e-9 ? 'disc' : 'facets'; };
  const groups = [];
  for (let i = 0; i + 1 < pts.length; i++) { const m = kind(i), g = groups.at(-1); if (g && g.map === m && m !== 'disc') g.segs.push(i); else groups.push({map: m, segs: [i]}); }
  for (const g of groups) {
    const rMax = Math.max(...g.segs.flatMap(i => [pts[i][0], pts[i + 1][0]]));
    const rise = i => { const [ra, aa] = pts[i], [rb, ab] = pts[i + 1]; return g.map === 'facets' ? Math.hypot((rb - ra) * Math.cos(Math.PI / n), ab - aa) : Math.abs(ab - aa); };
    const isl = {part, map: g.map, w: g.map === 'strip' ? 2 * Math.PI * rMax : g.map === 'facets' ? n * 2 * rMax * HS : 2 * rMax,
      h: g.map === 'disc' ? 2 * rMax : g.segs.reduce((s, i) => s + rise(i), 0)};
    ISLANDS.push(isl);
    let v0 = 0;
    for (const i of g.segs) {
      const [ra, aa] = pts[i], [rb, ab, tag = 'side', mat = mat0] = pts[i + 1], dr = rb - ra, da = ab - aa, seg = Math.hypot(dr, da);
      const nr = da / seg, na = -dr / seg, h = rise(i);
      for (let k = 0; k < n; k++) {
        const k1 = k + 1, A0 = rings[i], B0 = rings[i + 1];
        // corner list: [vertex id, on row A?, angle index] (poles collapse a row to one vertex)
        const corners = [];
        if (A0.length > 1) corners.push([A0[k], true, k], [A0[k1 % n], true, k1]); else corners.push([A0[0], true, k + 0.5]);
        if (B0.length > 1) corners.push([B0[k1 % n], false, k1], [B0[k], false, k]); else corners.push([B0[0], false, k + 0.5]);
        const uvl = corners.map(([, onA, kk]) => {
          const r = onA ? ra : rb, th = ang(kk);
          if (g.map === 'disc') return [rMax + r * Math.cos(th - phase), rMax + r * Math.sin(th - phase)];
          if (g.map === 'facets') return [(k + 0.5) * 2 * rMax * HS + (kk === k ? -1 : kk === k1 ? 1 : 0) * r * HS, v0 + (onA ? 0 : h)];
          return [rMax * 2 * Math.PI * kk / n, v0 + (onA ? 0 : h)];
        });
        // analytic outward normal of the surface of revolution at each corner (drives the painted _n tilt)
        const nrm = corners.map(([, , kk]) => norm(add(mul(radial(ang(kk)), nr), mul(A, na))));
        const want = add(mul(radial(ang(k + 0.5)), nr), mul(A, na));
        part.face(corners.map(c => c[0]), want, tag, mat, {isl, uvl, nrm});
      }
      v0 += h;
    }
  }
}

// ---------------- geometry ----------------
const tube = new Part('optic_tube', 'housing'), mount = new Part('optic_mount', 'mount'), controls = new Part('optic_controls', 'housing');
const hardware = new Part('optic_hardware', 'steel'), lens = new Part('optic_lens', 'glass');
const TUBE_FRAME = {C: [0, AXIS.y, 0], A: [0, 0, 1], U: [1, 0, 0], V: [0, 1, 0]};
{
  const {ro, ri, front: f, rear: r} = TUBE, b = 0.035;
  // tube shell: front face, objective hood + body (one strip; the hood's ring line is painted), eyepiece face, matte bore
  // (a = z, +z = rear)
  lathe(tube, TUBE_FRAME, N, PHASE, [
    [ri, f], [ro - b, f, 'face'], [ro, f + b, 'bevel'], [ro, r - b, 'side'], [ro - b, r, 'bevel'], [ri, r, 'face'], [ri, f, 'bore']]);
  // LED emitter bump on the bore floor near the eyepiece (outside the lens window as seen from the eye)
  prism(tube, FRAME.z, {P: [[-0.075, 1.45], [0.075, 1.45], [0.075, 1.525], [-0.075, 1.525]], r: [0, 0, 0.025, 0.025], segs: [0, 0, 2, 2]},
    [[-0.012, 0.55], [0, 0.562], [0, 0.80]], {mat: 'emitter'});
  // turrets: elevation (top), windage (right, +x); brightness / battery knob (left, -x); bases sink into the tube
  const turret = [[0.25, 0.58], [0.25, 0.74, 'side'], [0.215, 0.74, 'face', 'cap'], [0.215, 0.89, 'side', 'cap'], [0.19, 0.915, 'bevel', 'cap'], [0, 0.915, 'top', 'cap']];
  lathe(controls, {C: [0, AXIS.y, -0.05], A: [0, 1, 0], U: [1, 0, 0], V: [0, 0, 1]}, 14, Math.PI / 14, turret);
  lathe(controls, {C: [0, AXIS.y, -0.05], A: [1, 0, 0], U: [0, 0, 1], V: [0, 1, 0]}, 14, Math.PI / 14, turret);
  lathe(controls, {C: [0, AXIS.y, -0.05], A: [-1, 0, 0], U: [0, 0, 1], V: [0, 1, 0]}, 16, Math.PI / 16,
    [[0.28, 0.57], [0.28, 0.70, 'side'], [0.26, 0.72, 'bevel'], [0.26, 0.88, 'grip', 'grip'], [0.23, 0.91, 'bevel', 'cap'], [0, 0.91, 'top', 'cap']]);
  // riser: rail clamp (hangs over the rail flanks), web with a through lightening window, saddle = the tube's integral
  // base (its top sinks into the tube wall, below the bore)
  const c = 0.025;
  prism(mount, FRAME.z, {P: [[-FOOT.x, FOOT.jaw], [FOOT.x, FOOT.jaw], [FOOT.x, 0.22], [-FOOT.x, 0.22]], r: [0.03, 0.03, 0.08, 0.08], segs: [1, 1, 2, 2]},
    [[-c, -FOOT.z], [0, -FOOT.z + c], [0, FOOT.z - c], [-c, FOOT.z]]);
  frameSweep(mount, FRAME.x, {P: [[-0.84, 0.14], [0.84, 0.14], [0.70, 1.17], [-0.70, 1.17]], r: [0.03, 0.03, 0.10, 0.10], segs: [2, 2, 2, 2]},
    {P: [[-0.36, 0.44], [0.36, 0.44], [0.36, 0.90], [-0.36, 0.90]], r: [0.11, 0.11, 0.11, 0.11], segs: [2, 2, 2, 2]},
    [[-0.02, 0.02, -0.34], [0, 0, -0.32], [0, 0, 0.32], [-0.02, 0.02, 0.34]]);
  prism(mount, FRAME.z, {P: [[-0.36, 1.12], [0.36, 1.12], [0.30, 1.44], [-0.30, 1.44]], r: [0, 0, 0, 0], segs: [0, 0, 0, 0]},
    [[-0.02, -0.80], [0, -0.78], [0, 0.73], [-0.02, 0.75]]);
  // throw lever with pivot boss (right), cross-bolt nut (left)
  prism(hardware, FRAME.x, {P: [[-0.88, 0.02], [0.30, 0.02], [0.30, 0.18], [-0.88, 0.18]], r: [0.065, 0.075, 0.075, 0.065], segs: [2, 2, 2, 2]},
    [[0, 0.655], [0, 0.71], [-0.012, 0.722]], {cap0: false});
  lathe(hardware, {C: [0, 0.10, 0.30], A: [1, 0, 0], U: [0, 0, 1], V: [0, 1, 0]}, 12, 0, [[0.12, 0.64], [0.12, 0.775, 'side'], [0.10, 0.795, 'bevel'], [0, 0.795, 'top']]);
  prism(hardware, FRAME.x, ngon(0.30, 0.10, 0.11, 6), [[0, -0.655], [0, -0.73], [-0.012, -0.742]], {cap0: false});
  // optical lens: one thin plane across the bore (fan), its rim 0.005 into the bore wall, normal toward the shooter
  const ci = lens.vtx(LENS), ring = Array.from({length: N}, (_, k) => { const th = PHASE + 2 * Math.PI * k / N; return lens.vtx([(ri + 0.005) * Math.cos(th), AXIS.y + (ri + 0.005) * Math.sin(th), LENS[2]]); });
  ring.forEach((_, i) => lens.face([ring[i], ring[(i + 1) % N], ci], [0, 0, 1], 'lens'));
}
const PARTS = [tube, mount, controls, hardware, lens];

// ---------------- UV: lathe islands as authored, other faces as planar islands (normal flood fill), shelf packing ----------------
for (const p of PARTS) {
  const F = p.f.map(f => ({f, n: norm(newell(f.ids.map(i => p.v[i])))})).filter(o => !o.f.isl);
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
    const isl = {part: p, map: 'planar', w: Math.max(...us.map(a => a[0])) - u0, h: Math.max(...us.map(a => a[1])) - v0};
    ISLANDS.push(isl);
    for (const i of mem) Object.assign(F[i].f, {isl, uvl: F[i].f.ids.map(id => { const [u, v] = uv.get(id); return [u - u0, v - v0]; })});
  }
}
const PAD = 3;
function pack(S) {
  let x = PAD, y = PAD, rowH = 0;
  for (const is of ISLANDS.slice().sort((a, b) => b.h - a.h || b.w - a.w)) {
    const W = Math.ceil(is.w * S) + 1, H = Math.ceil(is.h * S) + 1;
    if (x + W + PAD > ATLAS) { x = PAD; y += rowH + PAD; rowH = 0; }
    if (y + H + PAD > ATLAS) return false;
    Object.assign(is, {px: x, py: y, W, H}); x += W + PAD; rowH = Math.max(rowH, H);
  }
  return true;
}
let S = 200; while (!pack(S)) S -= 1;
const uvPx = (f, c) => [f.isl.px + f.uvl[c][0] * S + 0.5, f.isl.py + f.uvl[c][1] * S + 0.5];

// ---------------- Base Color + LabPBR (same raster pass) ----------------
// Hard-anodised black aluminium housing and riser: coated (F0 24) like the BR51 receiver, chamfers one clean lighter tone
// with bare-metal F0 (no per-pixel rims); dark steel hardware (metal); matte anti-glare bore with depth falloff; faint
// violet-magenta lens coating.
const MAT = {   // c sRGB base, hl bevel highlight (added), sm / se smoothness open / bevel, f0 (255 metal, else linear F0), edgeF0 of bevels
  housing: {c: [38, 40, 43], hl: 20, sm: 92, se: 142, f0: 24, edgeF0: 255},
  mount:   {c: [35, 37, 40], hl: 18, sm: 86, se: 136, f0: 24, edgeF0: 255},
  cap:     {c: [41, 43, 46], hl: 20, sm: 100, se: 148, f0: 24, edgeF0: 255},
  grip:    {c: [30, 31, 33], hl: 8, sm: 58, se: 70, f0: 10},
  steel:   {c: [54, 56, 59], hl: 16, sm: 145, se: 170, f0: 255},
  emitter: {c: [16, 17, 19], hl: 6, sm: 150, se: 150, f0: 10, ao: 225},
  glass:   {c: [178, 160, 208], alpha: 40, hl: 0, sm: 235, se: 235, f0: 10},
};
const BEVEL_FULL = 0.35;   // hard-surface bevel faces shorter than this fade their highlight (short bright dashes sparkle)
function shade(f, pos, n, fade) {
  const m = MAT[f.mat], bevel = f.tag === 'bevel';
  let sm = bevel ? m.se : m.sm, ao = m.ao ?? 255, k = 1 + 0.03 * n[1] - 0.02 * Math.max(0, -n[1]);   // faint top light (P9 / BR51 term)
  // objective hood ring line: a painted band along the tube strip's texel rows (axis aligned, no geometry)
  if (f.mat === 'housing' && f.tag === 'side' && Math.hypot(pos[0], pos[1] - AXIS.y) > TUBE.ro - 0.01 && pos[2] > -0.70 && pos[2] < -0.665) { k *= 0.78; sm -= 12; ao = 215; }
  if (f.tag === 'tunnel') { k *= 0.85; ao = 220; }
  let c = m.c.map(v => v * k + (bevel ? m.hl * fade : 0));
  if (f.tag === 'bore') {   // matte black anti-glare bore, darker toward the lens
    const t = smooth(TUBE.rear, LENS[2], pos[2]);
    c = [19, 20, 22].map(v => v * (1 - 0.3 * t)); sm = 40; ao = Math.round(205 - 45 * t);
    return {c, a: 255, s: [sm, 10, 0, 255], ao};
  }
  return {c, a: m.alpha ?? 255, s: [sm, bevel && m.edgeF0 ? m.edgeF0 : m.f0, 0, 255], ao};
}
const img = Buffer.alloc(ATLAS * ATLAS * 4), spec = Buffer.alloc(ATLAS * ATLAS * 4), nrmMap = Buffer.alloc(ATLAS * ATLAS * 4);
for (let i = 0; i < ATLAS * ATLAS; i++) { img.set([32, 33, 35, 255], i * 4); spec.set([90, 24, 0, 255], i * 4); nrmMap.set([128, 128, 255, 255], i * 4); }
const clamp8 = a => a.map(v => Math.max(0, Math.min(255, Math.round(v))));
function put(x, y, t, nx = 0, ny = 0) {
  if (x < 0 || y < 0 || x >= ATLAS || y >= ATLAS) return;
  const k = (y * ATLAS + x) * 4, l = Math.hypot(nx, ny, 1);
  img.set([...clamp8(t.c), t.a], k); spec.set(clamp8(t.s), k); nrmMap.set(clamp8([128 + 127 * nx / l, 128 + 127 * ny / l, t.ao, 255]), k);
}
const covered = new Uint8Array(ATLAS * ATLAS);
const faceNormal = (p, f) => norm(newell(f.ids.map(i => p.v[i])));
const longest = (p, f) => Math.max(...f.ids.map((a, i) => Math.hypot(...sub(p.v[a], p.v[f.ids[(i + 1) % f.ids.length]]))));
// island padding in the tone of its first face; the glass island first, so opaque neighbours own the shared gap texels
const byIsland = new Map();
for (const p of PARTS) for (const f of p.f) { if (!byIsland.has(f.isl)) byIsland.set(f.isl, {p, f}); }
for (const [is, {p, f}] of [...byIsland].sort((a, b) => (b[1].f.mat === 'glass') - (a[1].f.mat === 'glass'))) {
  const t = shade(f, p.v[f.ids[0]], f.nrm ? f.nrm[0] : faceNormal(p, f), 1);
  for (let y = is.py - PAD; y < is.py + is.H + PAD; y++) for (let x = is.px - PAD; x < is.px + is.W + PAD; x++) put(x, y, t);
}
for (const pass of ['inside', 'fill']) for (const p of PARTS) for (const f of p.f) {
  const A = f.ids.map((_, c) => uvPx(f, c)), fn = faceNormal(p, f), fade = f.tag === 'bevel' && !f.nrm ? Math.min(1, longest(p, f) / BEVEL_FULL) : 1;
  for (let q = 1; q + 1 < A.length; q++) {
    const I = [0, q, q + 1], T = I.map(i => A[i]), den = (T[1][1] - T[2][1]) * (T[0][0] - T[2][0]) + (T[2][0] - T[1][0]) * (T[0][1] - T[2][1]);
    if (Math.abs(den) < 1e-9) continue;
    const tol = pass === 'inside' ? 0 : -0.75 / Math.max(1, Math.sqrt(Math.abs(den)));
    const x0 = Math.floor(Math.min(...T.map(a => a[0])) - 1), x1 = Math.ceil(Math.max(...T.map(a => a[0])) + 1);
    const y0 = Math.floor(Math.min(...T.map(a => a[1])) - 1), y1 = Math.ceil(Math.max(...T.map(a => a[1])) + 1);
    for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) {
      const X = x + 0.5, Y = y + 0.5;
      const l1 = ((T[1][1] - T[2][1]) * (X - T[2][0]) + (T[2][0] - T[1][0]) * (Y - T[2][1])) / den;
      const l2 = ((T[2][1] - T[0][1]) * (X - T[2][0]) + (T[0][0] - T[2][0]) * (Y - T[2][1])) / den, l3 = 1 - l1 - l2;
      if (l1 < tol || l2 < tol || l3 < tol || x < 0 || y < 0 || x >= ATLAS || y >= ATLAS) continue;
      const i = y * ATLAS + x; if (pass === 'fill' && covered[i]) continue; covered[i] = 1;
      const L = [l1, l2, l3], pos = [0, 1, 2].map(a => I.reduce((s, c, j) => s + p.v[f.ids[c]][a] * L[j], 0));
      // lathe texels: tilt toward the analytic normal; tangent-space x = +u = increasing lathe angle, y = +v
      let n = fn, nx = 0, ny = 0;
      if (f.nrm) {
        n = norm([0, 1, 2].map(a => I.reduce((s, c, j) => s + f.nrm[c][a] * L[j], 0)));
        const P0 = p.v[f.ids[0]], P1 = p.v[f.ids[1]], P3 = p.v[f.ids[f.ids.length - 1]];
        // face tangent frame from the UV gradient (u along the angle, v along the profile)
        const e1 = sub(P1, P0), e2 = sub(P3, P0), d1 = sub(A[1], A[0]), d2 = sub(A[f.ids.length - 1], A[0]), r = d1[0] * d2[1] - d2[0] * d1[1];
        if (Math.abs(r) > 1e-9) {
          const tu = norm(mul(sub(mul(e1, d2[1]), mul(e2, d1[1])), 1 / r)), tv = norm(mul(sub(mul(e2, d1[0]), mul(e1, d2[0])), 1 / r));
          const along = dot(n, fn) || 1; nx = dot(n, tu) / along; ny = dot(n, tv) / along;
        }
      }
      put(x, y, shade(f, pos, n, fade), nx, ny);
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
const PNG = [png(img), png(spec), png(nrmMap)];
const uuid = s => { const h = createHash('sha256').update('afl-rifle-red-dot-01-v1:' + s).digest('hex'); return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-8${h.slice(17, 20)}-${h.slice(20, 32)}`; };
const r10 = v => +v.toFixed(10) || 0;
const elements = PARTS.map(p => {
  const key = i => i.toString(36).padStart(4, '0'), vertices = {}, faces = {};
  p.v.forEach((q, i) => { vertices[key(i)] = q.map(r10); });
  p.f.forEach((f, fi) => { faces['f' + key(fi)] = {uv: Object.fromEntries(f.ids.map((id, c) => [key(id), uvPx(f, c).map(r10)])), vertices: f.ids.map(key), texture: 0}; });
  return {name: p.name, color: 0, origin: [0, 0, 0], rotation: [0, 0, 0], shading: 'flat', export: true, visibility: true, locked: false,
    render_order: 'default', scope: 0, allow_mirror_modeling: true, vertices, faces, type: 'mesh', uuid: uuid(p.name)};
});
const group = (name, origin, rotation = [0, 0, 0]) => ({name, uuid: uuid('group:' + name), export: true, locked: false, scope: 0, selected: false, visibility: true,
  _static: {properties: {}, temp_data: {}}, origin, rotation, color: 0, children: [], reset: false, shade: true, mirror_uv: false,
  autouv: 0, isOpen: true, primary_selected: false});
// sight_root > optic_lens > lens_center > lens_aperture; the lens mesh is the only element under optic_lens
const APERTURE_CORNER = [APERTURE, LENS[1] + APERTURE, LENS[2]];
const groups = [group('sight_root', [0, 0, 0]), group('optic_lens', LENS), group('lens_center', LENS), group('lens_aperture', APERTURE_CORNER)];
const PARENT = {optic_lens: 'sight_root', lens_center: 'optic_lens', lens_aperture: 'lens_center'};
const lensElement = elements.find(e => e.name === 'optic_lens');
const source = {meta: {format_version: '5.0', model_format: 'free', box_uv: false}, name: 'rifle_red_dot_01', model_identifier: '', visible_box: [1, 1, 0],
  variable_placeholders: '', variable_placeholder_buttons: [], timeline_setups: [], unhandled_root_fields: {}, resolution: {width: ATLAS, height: ATLAS},
  elements, groups,
  outliner: [{uuid: groups[0].uuid, isOpen: true, children: [...elements.filter(e => e !== lensElement).map(e => e.uuid),
    {uuid: groups[1].uuid, isOpen: true, children: [lensElement.uuid, {uuid: groups[2].uuid, isOpen: true, children: [{uuid: groups[3].uuid, isOpen: true, children: []}]}]}]}],
  textures: [{name: 'rifle_red_dot_01.png', relative_path: 'textures/rifle_red_dot_01.png', folder: '', namespace: '', id: '0', group: '', scope: 0,
    width: ATLAS, height: ATLAS, uv_width: ATLAS, uv_height: ATLAS, particle: false, use_as_default: false, layers_enabled: false, sync_to_project: '',
    file_format: 'png', render_mode: 'default', render_sides: 'auto', wrap_mode: 'limited', pbr_channel: 'color', fps: 7, frame_time: 1,
    frame_order_type: 'loop', frame_order: '', frame_interpolate: false, visible: true, internal: true, saved: true, uuid: uuid('texture'),
    source: 'data:image/png;base64,' + PNG[0].toString('base64')}],
  animations: []};
// geo pivots use the native exporter sign convention [-x, y, z]
const geo = {format_version: '1.12.0', 'minecraft:geometry': [{description: {identifier: 'geometry.rifle_red_dot_01', texture_width: ATLAS, texture_height: ATLAS,
  visible_bounds_width: 1, visible_bounds_height: 1, visible_bounds_offset: [0, 0.1, 0]}, bones: [{name: 'sight_root', pivot: [0, 0, 0]},
  ...groups.slice(1).map(g => ({name: g.name, parent: PARENT[g.name], pivot: [-g.origin[0] || 0, g.origin[1], g.origin[2]]}))]}]};
const sidecar = convert(source, geo, {}, 'rifle_red_dot_01_mesh.bbmodel', 2, null, LAYERS);

// Inventory / hand / ground presentation: builtin/entity through NativeMuzzleRendering.ItemRenderer, which puts the model
// origin (rail top) at the item centre. Each transform's translation centres the rotated, scaled model's bounds (the
// projected ones, not the rotated 3D box centre; vanilla ItemTransform: translate, rotate XYZ, scale).
const all = PARTS.flatMap(p => p.v);
const ext = i => [Math.min(...all.map(q => q[i])), Math.max(...all.map(q => q[i]))];
const rotXYZ = ([rx, ry, rz], p) => {   // R = Rx * Ry * Rz applied to p
  const [a, b, c] = [rx, ry, rz].map(d => d * Math.PI / 180);
  let q = [p[0] * Math.cos(c) - p[1] * Math.sin(c), p[0] * Math.sin(c) + p[1] * Math.cos(c), p[2]];
  q = [q[0] * Math.cos(b) + q[2] * Math.sin(b), q[1], -q[0] * Math.sin(b) + q[2] * Math.cos(b)];
  return [q[0], q[1] * Math.cos(a) - q[2] * Math.sin(a), q[1] * Math.sin(a) + q[2] * Math.cos(a)];
};
const display = (rotation, s) => {
  const Q = all.map(p => rotXYZ(rotation, p.map(v => v * s)));
  return {rotation, translation: [0, 1, 2].map(i => +(-(Math.min(...Q.map(q => q[i])) + Math.max(...Q.map(q => q[i]))) / 2).toFixed(3) || 0), scale: [s, s, s]};
};
const item = {parent: 'builtin/entity', gui_light: 'side', textures: {particle: 'apocalypse_firstlight:item/rifle_red_dot_01'}, display: {
  // inventory: the shared attachment view (rotation only); NativeAttachmentGuiFit centres and sizes every attachment alike
  gui: {rotation: [20, 135, 0], translation: [0, 0, 0], scale: [1, 1, 1]},
  ground: display([0, 0, 0], 0.7),
  fixed: display([0, 180, 0], 4.0),
  firstperson_righthand: display([0, -35, 0], 0.7),
  firstperson_lefthand: display([0, -35, 0], 0.7),
  thirdperson_righthand: display([0, 0, 0], 0.7),
  thirdperson_lefthand: display([0, 0, 0], 0.7)}};

const outputs = [[OUT.src, JSON.stringify(source)], [OUT.geo, JSON.stringify(geo, null, 2) + '\n'], [OUT.mesh, serializeCompact(sidecar)],
  [OUT.item, JSON.stringify(item, null, 2) + '\n'], ...OUT.maps.flatMap(([s, r], k) => [[s, PNG[k]], [r, PNG[k]]])];
const faces = sidecar.parts.flatMap(p => p.faces);
console.log(JSON.stringify({parts: sidecar.parts.map(p => `${p.name}:${p.faces.reduce((s, f) => s + f.length - 2, 0)}${p.render_layer ? '/' + p.render_layer : ''}`),
  triangles: faces.reduce((s, f) => s + f.length - 2, 0), quads: faces.filter(f => f.length === 4).length, faces: faces.length,
  bounds: {x: ext(0).map(v => +v.toFixed(3)), y: ext(1).map(v => +v.toFixed(3)), z: ext(2).map(v => +v.toFixed(3))}, texelPerUnit: S, islands: ISLANDS.length}));
if (process.argv.includes('--check')) {
  for (const [file, data] of outputs) { const cur = fs.existsSync(file) ? fs.readFileSync(file) : null; if (!cur || !cur.equals(Buffer.isBuffer(data) ? data : Buffer.from(data))) throw new Error('stale ' + path.relative(root, file)); }
  console.log('CHECK OK');
} else if (path.resolve(process.argv[1] || '') === fileURLToPath(import.meta.url)) {
  for (const [file, data] of outputs) fs.writeFileSync(file, data);
  console.log('wrote ' + outputs.map(([f]) => path.relative(root, f)).join(', '));
}
