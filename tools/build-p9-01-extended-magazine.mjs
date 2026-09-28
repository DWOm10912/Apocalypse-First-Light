// P9-01 28-round Extended Magazine V2 (item p9_01_extended_magazine): Pure Mesh rebuild for the P9-01 V2 Native rig.
// Replaces the retired-rig 23-cube magazine (legacy source src/main/blockbench/p9_01_extended_magazine.bbmodel is no
// longer exported). One 512 atlas: Base Color plus LabPBR _s / _n painted in the same raster pass.
//   node tools/build-p9-01-extended-magazine.mjs          -> writes the editable source, source maps, geo, V2 sidecar, runtime maps
//   node tools/build-p9-01-extended-magazine.mjs --check  -> verifies every output is up to date (plus the fit checks below)
// Design: extended stamped-steel double-stack body + short polymer grip-extension sleeve + thicker reinforced polymer
// floorplate with a small muted-tan identification insert (AFL special-magazine language, not the BR51 yellow plate).
// Frame: built in the standard P9 magazine frame (build-p9-01-v2-mesh.mjs: MAG_PIVOT, MAG_TILT -22 deg about X), so the
// part inside the grip and the feed lips are ring-for-ring identical to the standard 17-round magazine; the dynamic top
// round, the follower bone and every round anchor of the gun stay valid. Output vertices = rotX(p, MAG_TILT, MAG_PIVOT)
// - MAG_PIVOT: the asset origin is the gun's magazine / reload_magazine / empty_old_mag bone pivot, the grip tilt is baked
// in (as in the standard magazine). The standalone item un-tilts it (NativeMagazineItem itemTilt).
// Length: standard 4.51 (y -0.99 .. 3.52) -> 6.99 (y -3.47 .. 3.52), x1.55. Below the grip base (y -0.64): sleeve 0.845
// (30%), steel 1.645, floorplate 0.325 (standard 0.27, +20%; width +7% over the body). Follower: the inner walls end in a
// flat follower-tone floor at y 2.99, just under the gun's own follower (top 3.065), so the gun follower still shows in
// the gun and the reload / dropped copies (which carry no separate follower bone) show a follower top of their own.
import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {convert, serializeCompact} from './export-afl-mesh.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const assets = path.join(root, 'src/main/resources/assets/apocalypse_firstlight'), bb = path.join(root, 'src/main/blockbench');
const NAME = 'p9_01_extended_magazine';
const OUT = {
  src: path.join(bb, `${NAME}_mesh.bbmodel`), geo: path.join(assets, `geo/${NAME}.geo.json`), mesh: path.join(assets, `meshes/${NAME}.aflmesh.json`),
  maps: ['', '_s', '_n'].map(s => [path.join(bb, `textures/${NAME}${s}.png`), path.join(assets, `textures/item/${NAME}${s}.png`)]),
};
const ATLAS = 512;
const D = Math.PI / 180;
// standard magazine frame (keep in sync with build-p9-01-v2-mesh.mjs; x of the rig pivot is 0 in the runtime geo)
const MAG_PIVOT = [0, 3.5992, 1.647], MAG_TILT = -22;
const Y = {gripBase: -0.64, sleeveTop: -0.655, sleeveBottom: -1.50, plateTop: -3.145, bottom: -3.47, bodyBottom: -3.20, follower: 2.99};
const ROUND_PITCH = 0.215;   // standard: 17 rounds between follower top 3.065 and the body bottom -0.60

// ---------------- math ----------------
const add = (a, b) => a.map((v, i) => v + b[i]), sub = (a, b) => a.map((v, i) => v - b[i]), mul = (a, k) => a.map(v => v * k);
const dot = (a, b) => a.reduce((s, v, i) => s + v * b[i], 0);
const cross = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
const norm = a => { const l = Math.hypot(...a) || 1; return a.map(v => v / l); };
const newell = P => { let n = [0, 0, 0]; for (let i = 0; i < P.length; i++) { const a = P[i], b = P[(i + 1) % P.length]; n = add(n, [(a[1] - b[1]) * (a[2] + b[2]), (a[2] - b[2]) * (a[0] + b[0]), (a[0] - b[0]) * (a[1] + b[1])]); } return n; };
const rotX = (p, deg, o) => { const c = Math.cos(deg * D), s = Math.sin(deg * D), y = p[1] - o[1], z = p[2] - o[2]; return [p[0], o[1] + y * c - z * s, o[2] + y * s + z * c]; };

// ---------------- 2D loops (red dot generator language) ----------------
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

// ---------------- parts ----------------
class Part {
  constructor(name, mat) { Object.assign(this, {name, mat, v: [], f: []}); }
  vtx(p) { this.v.push(p); return this.v.length - 1; }
  face(ids, hint, tag, mat = this.mat) {
    if (ids.length === 4) {   // clearly warped quads (lip front cut) become two triangles on the shorter diagonal
      const P = ids.map(i => this.v[i]), n = norm(newell(P));
      const ext = Math.max(...[0, 1, 2].map(k => Math.max(...P.map(q => q[k])) - Math.min(...P.map(q => q[k]))));
      if (Math.max(...P.map(q => Math.abs(dot(sub(q, P[0]), n)))) > ext * 0.02) {
        const d02 = Math.hypot(...sub(P[0], P[2])), d13 = Math.hypot(...sub(P[1], P[3]));
        const [a, b] = d02 <= d13 ? [[0, 1, 2], [2, 3, 0]] : [[1, 2, 3], [3, 0, 1]];
        for (const t of [a, b]) this.face(t.map(k => ids[k]), hint, tag, mat);
        return;
      }
    }
    if (dot(newell(ids.map(i => this.v[i])), hint) < 0) ids = ids.slice().reverse();
    this.f.push({ids, tag, mat});
  }
}
// Loft closed rings around the magazine axis (+Y). outward(k, i, j) returns the outward hint for a band face; caps fan
// around the ring centroid with hints -Y (first) / +Y (last) unless given.
function loftRings(part, rings, {tag = () => 'side', mat = () => part.mat, outward, cap0 = null, cap1 = null} = {}) {
  const ids = rings.map(r => r.map(p => part.vtx(p))), n = rings[0].length;
  for (let k = 0; k + 1 < rings.length; k++) for (let i = 0; i < n; i++) {
    const j = (i + 1) % n, q = [ids[k][i], ids[k][j], ids[k + 1][j], ids[k + 1][i]];
    part.face(q, outward(k, i, j), tag(k), mat(k));
  }
  const cap = (k, c) => {
    if (!c) return;
    const r = rings[k], m = r.reduce((a, p) => add(a, mul(p, 1 / r.length)), [0, 0, 0]), ci = part.vtx(m);
    for (let i = 0; i < n; i++) part.face([ids[k][i], ids[k][(i + 1) % n], ci], c.hint, c.tag, c.mat ?? part.mat);
  };
  cap(0, cap0); cap(rings.length - 1, cap1);
  return ids;
}
const radial = (p, zc) => norm([p[0], 0, p[2] - zc]);
// FRAME.x: loop coordinates (u, v) = (z, y), swept along x (red dot generator)
const FX = p => [p[2], p[1], p[0]];
function sweep(part, shape, sections, {tag = 'side', mat = part.mat} = {}) {
  const loops = sections.map(([d]) => loop(shape, d)), n = loops[0].length;
  const rings = sections.map(([, w], k) => loops[k].map(([u, v]) => part.vtx(FX([u, v, w]))));
  const dirW = Math.sign(sections.at(-1)[1] - sections[0][1]);
  for (let k = 0; k + 1 < sections.length; k++) {
    const d0 = sections[k][0], d1 = sections[k + 1][0], lean = Math.sign(d0 - d1) * dirW;
    for (let i = 0; i < n; i++) {
      const j = (i + 1) % n, L = loops[k], o = norm([L[j][1] - L[i][1], -(L[j][0] - L[i][0]), 0]);
      part.face([rings[k][i], rings[k][j], rings[k + 1][j], rings[k + 1][i]], add(FX(o), FX([0, 0, lean])), d0 !== d1 ? 'bevel' : tag, mat);
    }
  }
  const L = loops.at(-1), c = L.reduce((q, p) => [q[0] + p[0] / L.length, q[1] + p[1] / L.length], [0, 0]), ci = part.vtx(FX([c[0], c[1], sections.at(-1)[1]]));
  rings.at(-1).forEach((_, i) => part.face([rings.at(-1)[i], rings.at(-1)[(i + 1) % n], ci], FX([0, 0, dirW]), 'cap', mat));
}

// standard P9 magazine rounded rectangle (build-p9-01-v2-mesh.mjs roundedRectXZ, NM 16): front radius rf (z0 side)
function magRing(hx, z0, z1, rf, rb, y) {
  const pts = [], per = 4;
  const corners = [[hx - rf, z0 + rf, rf, -90], [hx - rb, z1 - rb, rb, 0], [-hx + rb, z1 - rb, rb, 90], [-hx + rf, z0 + rf, rf, 180]];
  for (const [cx, cz, r, a0] of corners) for (let i = 0; i < per; i++) { const a = (a0 + 90 * i / (per - 1)) * D; pts.push([cx + r * Math.cos(a), y, cz + r * Math.sin(a)]); }
  return pts.reverse();
}
// grip-style superellipse outline (build-p9-01-v2-mesh.mjs gripPt): centre z 1.775, front half nF-power, back nB-power
const GZC = 1.775, NS = 28;
const sleeveRing = (hx, zf, zb, nF, nB, y) => Array.from({length: NS}, (_, i) => {
  const a = 2 * Math.PI * (i + 0.5) / NS, c = Math.cos(a), s = Math.sin(a), n = s < 0 ? nF : nB, hz = s < 0 ? GZC - zf : zb - GZC;
  return [Math.sign(c) * hx * Math.pow(Math.abs(c), 2 / n), y, GZC + Math.sign(s) * hz * Math.pow(Math.abs(s), 2 / n)];
});
const insideSleeve = ([hx, zf, zb, nF, nB], p) => { const front = p[2] < GZC, n = front ? nF : nB, hz = front ? GZC - zf : zb - GZC;
  return 1 - (Math.pow(Math.abs(p[0]) / hx, n) + Math.pow(Math.abs(p[2] - GZC) / hz, n)); };

// ---------------- geometry (standard magazine frame) ----------------
const body = new Part('magazine_body', 'steel'), sleeve = new Part('magazine_sleeve', 'sleeve');
const plate = new Part('magazine_floorplate', 'plate'), ribs = new Part('magazine_ribs', 'steel'), accent = new Part('magazine_accent', 'accent');
// 1) steel body: the standard rings (feed lips, rolled upper taper, inner walls) with the tube carried down into the plate
const BODY = [0.56, 0.92, 2.61, 0.13, 0.06];
const R = [[...BODY, Y.bodyBottom], [...BODY, 3.24],
  [0.525, 0.945, 2.60, 0.12, 0.058, 3.37], [0.44, 0.99, 2.588, 0.10, 0.05, 3.46], [0.29, 1.05, 2.57, 0.09, 0.05, 3.52],
  [0.222, 1.09, 2.56, 0.08, 0.05, 3.51], [0.207, 1.10, 2.56, 0.08, 0.05, 3.482],
  [0.232, 1.09, 2.56, 0.08, 0.05, 3.438], [0.247, 1.08, 2.56, 0.08, 0.05, 3.396], [0.50, 0.98, 2.55, 0.10, 0.04, 3.34], [0.50, 0.98, 2.55, 0.10, 0.04, Y.follower]];
const cut = q => { const f = Math.max(0, Math.min(1, (1.12 - q[2]) / 0.20)); return q[1] > 3.26 ? [q[0], 3.26 + (q[1] - 3.26) * (1 - 0.8 * f), q[2]] : q; };
const bodyRings = R.map(([hx, z0, z1, rf, rb, y]) => magRing(hx, z0, z1, rf, rb, y).map(cut));
// outward: out of the tube for the outer wall and the lip crest going up, into the tube once the rings come back down
const crest = 4;   // ring index of the highest lip ring
loftRings(body, bodyRings, {
  tag: k => k === 0 ? 'side' : k === R.length - 2 ? 'inner' : 'lip',
  outward: (k, i) => { const p = bodyRings[k][i], r = radial(p, 1.765); return k < crest ? add(r, [0, 0.3, 0]) : k === crest ? [0, 1, 0] : mul(r, -1); },
  cap0: {hint: [0, -1, 0], tag: 'cap'}, cap1: {hint: [0, 1, 0], tag: 'follower', mat: 'follower'},
});
// 2) stamped longitudinal rib on each flank of the exposed steel, fading into the wall at both ends
for (const sx of [1, -1]) {
  const secs = [[Y.plateTop + 0.125, 0.001], [Y.plateTop + 0.225, 0.018], [Y.sleeveBottom - 0.24, 0.018], [Y.sleeveBottom - 0.14, 0.001]];
  const prof = h => [[0.556, 1.66], [0.56 + h, 1.70], [0.56 + h, 1.83], [0.556, 1.87]];
  const ids = secs.map(([y, h]) => prof(h).map(([x, z]) => ribs.vtx([sx * x, y, z])));
  for (let k = 0; k + 1 < ids.length; k++) for (let i = 0; i < 3; i++)
    ribs.face([ids[k][i], ids[k][i + 1], ids[k + 1][i + 1], ids[k + 1][i]], [sx, 0, 0], k === 1 ? 'rib' : 'ribEnd');
}
// 3) polymer grip-extension sleeve: continues the grip outline (grip gripPt without the base flare), then tapers onto the tube
const SLEEVE = [   // hx, zf, zb, nF, nB, y
  [0.725, 0.66, 2.96, 5, 4, Y.sleeveTop], [0.745, 0.64, 2.98, 5, 4, Y.sleeveTop - 0.02], [0.745, 0.64, 2.98, 5, 4, -1.02],
  [0.715, 0.70, 2.92, 6, 5, -1.22], [0.655, 0.80, 2.78, 8, 7, -1.38], [0.625, 0.845, 2.715, 10, 9, -1.46], [0.60, 0.87, 2.685, 10, 9, Y.sleeveBottom]];
const sleeveRings = SLEEVE.map(s => sleeveRing(...s));
loftRings(sleeve, sleeveRings, {
  tag: k => k === 0 || k === SLEEVE.length - 2 ? 'bevel' : k === 1 ? 'side' : 'taper',
  outward: (k, i) => radial(sleeveRings[k][i], GZC),
  cap0: {hint: [0, 1, 0], tag: 'cap'}, cap1: {hint: [0, -1, 0], tag: 'step'},
});
// 4) reinforced polymer floorplate: chamfered top, shallow grip groove, front / rear flare, chamfered bottom
const PLATE = [   // hx, z0, z1, rf, rb, y, tag of the band below this ring
  [0.57, 0.89, 2.64, 0.10, 0.08, Y.plateTop, 'bevel'], [0.60, 0.86, 2.67, 0.12, 0.10, Y.plateTop - 0.03, 'side'],
  [0.60, 0.86, 2.67, 0.12, 0.10, -3.215, 'groove'], [0.585, 0.875, 2.655, 0.105, 0.085, -3.225, 'groove'],
  [0.585, 0.875, 2.655, 0.105, 0.085, -3.245, 'groove'], [0.60, 0.86, 2.67, 0.12, 0.10, -3.255, 'flare'],
  [0.60, 0.83, 2.70, 0.13, 0.11, -3.30, 'side'], [0.60, 0.83, 2.70, 0.13, 0.11, Y.bottom + 0.03, 'bevel'], [0.57, 0.86, 2.67, 0.10, 0.08, Y.bottom, null]];
const plateRings = PLATE.map(([hx, z0, z1, rf, rb, y]) => magRing(hx, z0, z1, rf, rb, y));
loftRings(plate, plateRings, {
  tag: k => PLATE[k][6], outward: (k, i) => radial(plateRings[k][i], 1.765),
  cap0: {hint: [0, 1, 0], tag: 'top'}, cap1: {hint: [0, -1, 0], tag: 'bottom'},
});
// 5) muted-tan identification insert on both flanks of the floorplate (0.016 proud, chamfered)
const INSERT = {P: [[1.45, -3.415], [2.25, -3.415], [2.25, -3.325], [1.45, -3.325]], r: [0.03, 0.03, 0.03, 0.03], segs: [1, 1, 1, 1]};
for (const sx of [1, -1]) sweep(accent, INSERT, [[0, sx * 0.595], [0, sx * 0.604], [-0.007, sx * 0.611]]);
const PARTS = [body, ribs, sleeve, plate, accent];

// painted details (standard magazine frame): witness holes on the exposed rear spine (about 22 / 25 / 28 rounds),
// takedown hole in the floorplate bottom
const WITNESS = [22, 25, 28].map(n => 3.065 - n * ROUND_PITCH), HOLE_R = 0.045, TAKEDOWN = {z: 2.28, r: 0.055};

// ---------------- fit checks (standard magazine frame) ----------------
const checks = {};
{
  // every tube ring point inside every sleeve ring and the plate top
  const tube = magRing(...BODY, 0);
  checks.sleeveOverTube = Math.min(...SLEEVE.flatMap(s => tube.map(p => insideSleeve(s, p))));
  checks.plateOverTube = Math.min(...tube.map(p => 0.57 - Math.abs(p[0])));
  const gun = JSON.parse(fs.readFileSync(path.join(assets, 'meshes/p9_01_v2_native.aflmesh.json'), 'utf8'));
  const g = JSON.parse(fs.readFileSync(path.join(assets, 'geo/p9_01_v2_native.geo.json'), 'utf8'))['minecraft:geometry'][0];
  const piv = Object.fromEntries(g.bones.map(b => [b.name, [-b.pivot[0], b.pivot[1], b.pivot[2]]]));
  const toMag = (p, bone) => rotX(add(mul(p.slice(0, 3), 16), piv[bone]), -MAG_TILT, MAG_PIVOT);
  const grip = gun.parts.find(p => p.name === 'frame_grip');
  checks.gripLowestY = Math.min(...grip.vertices.map(v => toMag(v, grip.bone)[1]));
  // feed lips identical to the standard magazine: nearest standard vertex for every new lip vertex
  const std = gun.parts.find(p => p.name === 'magazine_body'), stdV = std.vertices.map(v => toMag(v, std.bone));
  const lips = body.v.filter(q => q[1] > 3.25);
  checks.lipDeviation = Math.max(...lips.map(q => Math.min(...stdV.map(s => Math.hypot(...sub(s, q))))));
}

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
    let t = sub([0, 1, 0], mul(n, n[1])); if (Math.hypot(...t) < 0.3) t = sub([0, 0, 1], mul(n, n[2])); t = norm(t); const bt = cross(n, t);
    const vs = [...new Set(mem.flatMap(i => F[i].f.ids))], uv = new Map(vs.map(i => [i, [dot(p.v[i], bt), -dot(p.v[i], t)]]));
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
// dark nitrided steel tube (metal, medium smoothness), matte polymer sleeve, slightly smoother reinforced polymer plate,
// muted tan insert, follower-tone floor. One clean tone per face; no wear, no noise; AO only in the groove / recesses.
const MAT = {   // base colour, smoothness, F0 (255 metal / 10 dielectric), AO; per-tag overrides below
  steel:    {c: [60, 61, 64], sm: 118, f0: 255},
  sleeve:   {c: [33, 34, 36], sm: 52, f0: 10},
  plate:    {c: [29, 30, 32], sm: 74, f0: 10},
  accent:   {c: [124, 106, 74], sm: 62, f0: 10},
  follower: {c: [96, 78, 52], sm: 70, f0: 10},
};
const TAG = {   // c / sm absolute, or k (tone factor) / dsm (smoothness offset); ao multiplies nothing, it replaces 255
  lip: {c: [70, 71, 74], sm: 135}, rib: {c: [66, 67, 70], sm: 124}, ribEnd: {c: [64, 65, 68], sm: 122},
  inner: {c: [30, 30, 32], sm: 80, ao: 175}, cap: {ao: 200},
  bevel: {k: 1.22, dsm: 10}, taper: {k: 1.06}, step: {k: 0.85, ao: 215}, flare: {k: 1.08},
  groove: {k: 0.72, dsm: -10, ao: 185}, top: {k: 0.95, ao: 205}, bottom: {k: 0.92},
};
const DARK = {c: [13, 13, 14], sm: 22, f0: 10, ao: 120};
function shade(f, n, pos) {
  const m = MAT[f.mat], t = TAG[f.tag] || {};
  let c = t.c ?? m.c.map(v => v * (t.k ?? 1)), sm = t.sm ?? m.sm + (t.dsm ?? 0), ao = t.ao ?? 255;
  if (f.mat === 'accent' && f.tag === 'bevel') c = [132, 114, 80];   // insert chamfer: one step lighter, same hue
  const k = 1 + 0.03 * n[1] - 0.02 * Math.max(0, -n[1]);   // faint top light, same term as the P9 painter
  let out = {c: c.map(v => v * k), s: [sm, m.f0, 0, 255], n: [128, 128, ao, 255]};
  // painted holes, antialiased over one texel
  let cov = 0;
  if (f.mat === 'steel' && f.tag === 'side' && n[2] > 0.95 && Math.abs(pos[0]) < 0.5)
    for (const y of WITNESS) cov = Math.max(cov, (HOLE_R - Math.hypot(pos[0], pos[1] - y)) * S + 0.5);
  if (f.mat === 'plate' && f.tag === 'bottom') cov = (TAKEDOWN.r - Math.hypot(pos[0], pos[2] - TAKEDOWN.z)) * S + 0.5;
  cov = Math.max(0, Math.min(1, cov));
  if (cov > 0) out = {c: out.c.map((v, i) => v + (DARK.c[i] - v) * cov), s: [sm + (DARK.sm - sm) * cov, cov > 0.5 ? DARK.f0 : m.f0, 0, 255],
    n: [128, 128, ao + (DARK.ao - ao) * cov, 255]};
  return out;
}
const img = Buffer.alloc(ATLAS * ATLAS * 4), spec = Buffer.alloc(ATLAS * ATLAS * 4), nrm = Buffer.alloc(ATLAS * ATLAS * 4);
for (let i = 0; i < ATLAS * ATLAS; i++) { img.set([32, 33, 35, 255], i * 4); spec.set([80, 10, 0, 255], i * 4); nrm.set([128, 128, 255, 255], i * 4); }
const clamp8 = a => a.map(v => Math.max(0, Math.min(255, Math.round(v))));
const put = (x, y, t) => { if (x < 0 || y < 0 || x >= ATLAS || y >= ATLAS) return; const k = (y * ATLAS + x) * 4; img.set([...clamp8(t.c), 255], k); spec.set(clamp8(t.s), k); nrm.set(clamp8(t.n), k); };
const covered = new Uint8Array(ATLAS * ATLAS);
for (const is of islands) {   // island padding in the tone of its first face
  const {f, n} = is.faces[0], t = shade(f, n, is.part.v[f.ids[0]]);
  for (let y = is.py - PAD; y < is.py + is.H + PAD; y++) for (let x = is.px - PAD; x < is.px + is.W + PAD; x++) put(x, y, t);
}
for (const pass of ['inside', 'fill']) for (const is of islands) for (const {f, n} of is.faces) {
  const A = f.ids.map(i => uvOf(is, i)), P3 = f.ids.map(i => is.part.v[i]);
  for (let q = 1; q + 1 < A.length; q++) {
    const T = [A[0], A[q], A[q + 1]], Q = [P3[0], P3[q], P3[q + 1]], den = (T[1][1] - T[2][1]) * (T[0][0] - T[2][0]) + (T[2][0] - T[1][0]) * (T[0][1] - T[2][1]);
    if (Math.abs(den) < 1e-9) continue;
    const tol = pass === 'inside' ? 0 : -0.75 / Math.max(1, Math.sqrt(Math.abs(den)));
    const x0 = Math.floor(Math.min(...T.map(a => a[0])) - 1), x1 = Math.ceil(Math.max(...T.map(a => a[0])) + 1);
    const y0 = Math.floor(Math.min(...T.map(a => a[1])) - 1), y1 = Math.ceil(Math.max(...T.map(a => a[1])) + 1);
    for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) {
      const X = x + 0.5, Yp = y + 0.5;
      const l1 = ((T[1][1] - T[2][1]) * (X - T[2][0]) + (T[2][0] - T[1][0]) * (Yp - T[2][1])) / den;
      const l2 = ((T[2][1] - T[0][1]) * (X - T[2][0]) + (T[0][0] - T[2][0]) * (Yp - T[2][1])) / den, l3 = 1 - l1 - l2;
      if (l1 < tol || l2 < tol || l3 < tol || x < 0 || y < 0 || x >= ATLAS || y >= ATLAS) continue;
      const i = y * ATLAS + x; if (pass === 'fill' && covered[i]) continue; covered[i] = 1;
      put(x, y, shade(f, n, [0, 1, 2].map(k => Q[0][k] * l1 + Q[1][k] * l2 + Q[2][k] * l3)));
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
const uuid = s => { const h = createHash('sha256').update('afl-p9-01-extended-magazine-v2:' + s).digest('hex'); return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-8${h.slice(17, 20)}-${h.slice(20, 32)}`; };
const r8 = v => +v.toFixed(8) || 0;
const toAsset = q => sub(rotX(q, MAG_TILT, MAG_PIVOT), MAG_PIVOT);   // standard magazine frame -> rig pivot frame
const elements = PARTS.map(p => {
  const key = i => i.toString(36).padStart(4, '0'), vertices = {}, faces = {};
  p.v.forEach((q, i) => { vertices[key(i)] = toAsset(q).map(r8); });
  let fi = 0;
  for (const is of islands.filter(i => i.part === p)) for (const {f} of is.faces)
    faces['f' + key(fi++)] = {uv: Object.fromEntries(f.ids.map(i => [key(i), uvOf(is, i).map(r8)])), vertices: f.ids.map(key), texture: 0};
  return {name: p.name, color: 0, origin: [0, 0, 0], rotation: [0, 0, 0], shading: 'flat', export: true, visibility: true, locked: false,
    render_order: 'default', scope: 0, allow_mirror_modeling: true, vertices, faces, type: 'mesh', uuid: uuid(p.name)};
});
const group = {name: 'magazine_root', uuid: uuid('group:magazine_root'), export: true, locked: false, scope: 0, selected: false, visibility: true,
  _static: {properties: {}, temp_data: {}}, origin: [0, 0, 0], rotation: [0, 0, 0], color: 0, children: [], reset: false, shade: true, mirror_uv: false,
  autouv: 0, isOpen: true, primary_selected: false};
const source = {meta: {format_version: '5.0', model_format: 'free', box_uv: false}, name: NAME, model_identifier: '', visible_box: [1, 1, 0],
  variable_placeholders: '', variable_placeholder_buttons: [], timeline_setups: [], unhandled_root_fields: {}, resolution: {width: ATLAS, height: ATLAS},
  elements, groups: [group], outliner: [{uuid: group.uuid, isOpen: true, children: elements.map(e => e.uuid)}],
  textures: [{name: `${NAME}.png`, relative_path: `textures/${NAME}.png`, folder: '', namespace: '', id: '0', group: '', scope: 0,
    width: ATLAS, height: ATLAS, uv_width: ATLAS, uv_height: ATLAS, particle: false, use_as_default: false, layers_enabled: false, sync_to_project: '',
    file_format: 'png', render_mode: 'default', render_sides: 'auto', wrap_mode: 'limited', pbr_channel: 'color', fps: 7, frame_time: 1,
    frame_order_type: 'loop', frame_order: '', frame_interpolate: false, visible: true, internal: true, saved: true, uuid: uuid('texture'),
    source: 'data:image/png;base64,' + PNG[0].toString('base64')}],
  animations: []};
const geo = {format_version: '1.12.0', 'minecraft:geometry': [{description: {identifier: `geometry.${NAME}`, texture_width: ATLAS, texture_height: ATLAS,
  visible_bounds_width: 2, visible_bounds_height: 2, visible_bounds_offset: [0, -0.25, 0]}, bones: [{name: 'magazine_root', pivot: [0, 0, 0]}]}]};
const sidecar = convert(source, geo, {}, `${NAME}_mesh.bbmodel`, 2);

const outputs = [[OUT.src, JSON.stringify(source)], [OUT.geo, JSON.stringify(geo, null, 2) + '\n'], [OUT.mesh, serializeCompact(sidecar)],
  ...OUT.maps.flatMap(([s, r], k) => [[s, PNG[k]], [r, PNG[k]]])];
const faces = sidecar.parts.flatMap(p => p.faces), all = sidecar.parts.flatMap(p => p.vertices.map(v => v.slice(0, 3).map(x => x * 16)));
const ext = i => [Math.min(...all.map(q => q[i])), Math.max(...all.map(q => q[i]))].map(v => +v.toFixed(3));
const upright = PARTS.flatMap(p => p.v), yr = [Math.min(...upright.map(q => q[1])), Math.max(...upright.map(q => q[1]))];
console.log(JSON.stringify({parts: sidecar.parts.map(p => `${p.name}:${p.faces.reduce((s, f) => s + f.length - 2, 0)}`),
  triangles: faces.reduce((s, f) => s + f.length - 2, 0), quads: faces.filter(f => f.length === 4).length, triFaces: faces.filter(f => f.length === 3).length,
  vertexSubmissions: faces.length * 4, bounds: {x: ext(0), y: ext(1), z: ext(2)}, uprightY: yr.map(v => +v.toFixed(3)),
  lengthVsStandard: +((yr[1] - yr[0]) / 4.51).toFixed(3), texelPerUnit: S, islands: islands.length,
  checks: Object.fromEntries(Object.entries(checks).map(([k, v]) => [k, +v.toFixed(5)]))}));
if (!(checks.sleeveOverTube > 0.005 && checks.plateOverTube > 0.005 && checks.gripLowestY >= Y.sleeveTop + 0.005 && checks.lipDeviation < 2e-3))
  throw new Error('fit check failed: ' + JSON.stringify(checks));
if (process.argv.includes('--check')) {
  for (const [file, data] of outputs) { const cur = fs.existsSync(file) ? fs.readFileSync(file) : null; if (!cur || !cur.equals(Buffer.isBuffer(data) ? data : Buffer.from(data))) throw new Error('stale ' + path.relative(root, file)); }
  console.log('CHECK OK');
} else {
  for (const [file, data] of outputs) fs.writeFileSync(file, data);
  console.log('wrote ' + outputs.map(([f]) => path.relative(root, f)).join(', '));
}
