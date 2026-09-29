// BR51-01 V2 Pure Mesh (Phase 2): rebuilds the approved BR51 cube rifle as AFL hard-surface Pure Mesh on the frozen
// Phase 1 Native Rig, with a 1024 atlas: Base Color + LabPBR _s / _n painted in one raster pass.
//   node tools/build-br51-01-v2-mesh.mjs            -> writes source, runtime geo / sidecar / maps
//   node tools/build-br51-01-v2-mesh.mjs --check    -> verifies every output is up to date
//   node tools/build-br51-01-v2-mesh.mjs --debug <out.bbmodel>  -> writes the source to <out> only (previews)
// Reference: the Phase 1 source at git REF (cube geometry = 3D design reference; groups, pivots and animations = rig).
// Method, per original geometry group:
//   slab  : the group's cubes are layered by their extent along the extrusion axis (x = side profile, y = top profile);
//           each layer's projected footprint is rasterised, traced, simplified (cube stair-steps become straight edges)
//           and extruded with a real chamfer. Layers fully enclosed by a wider layer are dropped (hidden geometry).
//           Cubes rotated about another axis (the cube modeller's 45 deg edge fillers) are dropped when a regular
//           layer already covers them, otherwise kept as oriented boxes.
//   lathe : octagon-built round parts (barrel, gas tube, collars, bolt face, sight drum) become true cylinders.
//   custom: flash hider (revolved tube + four prongs). No visible ammunition (see the ammo note below).
// Silhouette is inherited from the cube footprints by construction; see the verification printout.
import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';
import {createHash} from 'node:crypto';
import {execFileSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {convert, serializeCompact} from './export-afl-mesh.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
export const REF = '38b6c66';   // BR51 Native Rig V2 (Phase 1) commit
const gitShow = p => execFileSync('git', ['show', `${REF}:${p}`], {cwd: ROOT, encoding: 'utf8', maxBuffer: 1 << 28});
export const ref = JSON.parse(gitShow('src/main/blockbench/br51_01.bbmodel'));

// ---------------- math ----------------
const D = Math.PI / 180;
const add = (a, b) => a.map((v, i) => v + b[i]), sub = (a, b) => a.map((v, i) => v - b[i]), mul = (a, k) => a.map(v => v * k);
const dot = (a, b) => a.reduce((s, v, i) => s + v * b[i], 0);
const cross = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
const norm = a => { const l = Math.hypot(...a) || 1; return a.map(v => v / l); };
const newell = P => { let n = [0, 0, 0]; for (let i = 0; i < P.length; i++) { const a = P[i], b = P[(i + 1) % P.length]; n = add(n, [(a[1] - b[1]) * (a[2] + b[2]), (a[2] - b[2]) * (a[0] + b[0]), (a[0] - b[0]) * (a[1] + b[1])]); } return n; };
const M4 = {
  mul: (a, b) => { const r = new Array(16).fill(0); for (let i = 0; i < 4; i++) for (let j = 0; j < 4; j++) for (let k = 0; k < 4; k++) r[i * 4 + j] += a[i * 4 + k] * b[k * 4 + j]; return r; },
  T: v => [1, 0, 0, v[0], 0, 1, 0, v[1], 0, 0, 1, v[2], 0, 0, 0, 1],
  R: e => { const [x, y, z] = (e || [0, 0, 0]).map(d => d * D), c = Math.cos, s = Math.sin;
    const rx = [1, 0, 0, 0, 0, c(x), -s(x), 0, 0, s(x), c(x), 0, 0, 0, 0, 1], ry = [c(y), 0, s(y), 0, 0, 1, 0, 0, -s(y), 0, c(y), 0, 0, 0, 0, 1], rz = [c(z), -s(z), 0, 0, s(z), c(z), 0, 0, 0, 0, 1, 0, 0, 0, 0, 1];
    return M4.mul(rz, M4.mul(ry, rx)); },
  about: (o, r) => M4.mul(M4.T(o), M4.mul(M4.R(r), M4.T(o.map(v => -v)))),
  pt: (m, p) => [0, 1, 2].map(i => m[i * 4] * p[0] + m[i * 4 + 1] * p[1] + m[i * 4 + 2] * p[2] + m[i * 4 + 3]),
  dir: (m, p) => [0, 1, 2].map(i => m[i * 4] * p[0] + m[i * 4 + 1] * p[1] + m[i * 4 + 2] * p[2]),
  inv: m => { // rigid (rotation + translation) inverse
    const r = [m[0], m[4], m[8], m[1], m[5], m[9], m[2], m[6], m[10]], t = [m[3], m[7], m[11]];
    const it = [-(r[0] * t[0] + r[1] * t[1] + r[2] * t[2]), -(r[3] * t[0] + r[4] * t[1] + r[5] * t[2]), -(r[6] * t[0] + r[7] * t[1] + r[8] * t[2])];
    return [r[0], r[1], r[2], it[0], r[3], r[4], r[5], it[1], r[6], r[7], r[8], it[2], 0, 0, 0, 1]; },
  I: () => [1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1],
};

// ---------------- reference cubes (bind pose, model space) ----------------
const SOURCE_ONLY = new Set(['sight', 'grip_default', 'reload_magazine', 'empty_old_mag']);   // not rebuilt from (copies / source-only)
export const groupInfo = new Map();   // name -> {group, parent, static (bind matrix), cubes: []}
{
  const G = new Map(ref.groups.map(g => [g.uuid, g])), E = new Map(ref.elements.map(e => [e.uuid, e]));
  (function walk(nodes, parent, m, hidden) {
    for (const n of nodes) {
      if (typeof n === 'string') continue;
      const g = G.get(n.uuid), M = M4.mul(m, M4.about(g.origin, g.rotation)), h = hidden || g.export === false || SOURCE_ONLY.has(g.name);
      const info = {group: g, uuid: g.uuid, parent, static: M, hidden: h, cubes: []};
      groupInfo.set(g.name, info);
      for (const c of n.children) if (typeof c === 'string') {
        const e = E.get(c); if (h || e.export === false || e.visibility === false) continue;
        const inf = e.inflate || 0, a = e.from.map(v => v - inf), b = e.to.map(v => v + inf), EM = M4.mul(M, M4.about(e.origin || [0, 0, 0], e.rotation));
        const corners = []; for (let k = 0; k < 8; k++) corners.push(M4.pt(EM, [k & 1 ? b[0] : a[0], k & 2 ? b[1] : a[1], k & 4 ? b[2] : a[2]]));
        const rot = (e.rotation || [0, 0, 0]).map(v => Math.round(v * 1000) / 1000);
        info.cubes.push({e, corners, M: EM, rot, from: a, to: b});
      }
      walk(n.children, g.name, M, h);
    }
  })(ref.outliner, null, M4.I(), false);
}

// ---------------- 2D polygon toolkit ----------------
const area2 = P => { let s = 0; for (let i = 0; i < P.length; i++) { const a = P[i], b = P[(i + 1) % P.length]; s += a[0] * b[1] - b[0] * a[1]; } return s / 2; };
const segX = (a, b, c, d) => { // proper intersection of segments ab, cd (shared endpoints excluded)
  const o = (p, q, r) => Math.sign((q[0] - p[0]) * (r[1] - p[1]) - (q[1] - p[1]) * (r[0] - p[0]));
  const eq = (p, q) => Math.abs(p[0] - q[0]) < 1e-9 && Math.abs(p[1] - q[1]) < 1e-9;
  if (eq(a, c) || eq(a, d) || eq(b, c) || eq(b, d)) return false;
  return o(a, b, c) * o(a, b, d) < 0 && o(c, d, a) * o(c, d, b) < 0;
};
function simpleLoops(loops) {
  const E = loops.flatMap(L => L.map((p, i) => [p, L[(i + 1) % L.length]]));
  for (let i = 0; i < E.length; i++) for (let j = i + 1; j < E.length; j++) if (segX(E[i][0], E[i][1], E[j][0], E[j][1])) return false;
  return true;
}
// Douglas-Peucker on a closed loop
function simplify(L, eps) {
  if (L.length < 4) return L;
  const dp = (pts) => {
    if (pts.length < 3) return pts;
    const a = pts[0], b = pts[pts.length - 1], ab = sub(b, a), len = Math.hypot(...ab) || 1e-12;
    let best = -1, bi = 0;
    for (let i = 1; i < pts.length - 1; i++) { const d = Math.abs(ab[0] * (pts[i][1] - a[1]) - ab[1] * (pts[i][0] - a[0])) / len; if (d > best) { best = d; bi = i; } }
    if (best <= eps) return [a, b];
    const l = dp(pts.slice(0, bi + 1)), r = dp(pts.slice(bi));
    return l.slice(0, -1).concat(r);
  };
  let far = 0, fd = -1; for (let i = 1; i < L.length; i++) { const d = Math.hypot(L[i][0] - L[0][0], L[i][1] - L[0][1]); if (d > fd) { fd = d; far = i; } }
  const A = dp(L.slice(0, far + 1)), B = dp(L.slice(far).concat([L[0]]));
  return A.slice(0, -1).concat(B.slice(0, -1));
}
// Inset (d > 0 shrinks the material side; loop orientation: outer CCW, holes CW)
function offset(L, d) {
  const n = L.length, out = [];
  for (let i = 0; i < n; i++) {
    const a = L[(i + n - 1) % n], b = L[i], c = L[(i + 1) % n];
    const e1 = norm([b[0] - a[0], b[1] - a[1]]), e2 = norm([c[0] - b[0], c[1] - b[1]]), n1 = [e1[1], -e1[0]], n2 = [e2[1], -e2[0]];
    const k = -d / Math.max(0.35, 1 + n1[0] * n2[0] + n1[1] * n2[1]);
    out.push([b[0] + k * (n1[0] + n2[0]), b[1] + k * (n1[1] + n2[1])]);
  }
  return out;
}
// Ear clipping of a polygon with holes (holes bridged to the outer loop). Returns triangles of 2D points' indices into pts.
function triangulate(outer, holes) {
  const pts = [], idx = L => L.map(p => { pts.push(p); return pts.length - 1; });
  let poly = idx(outer);
  const hs = holes.map(h => idx(h)).sort((a, b) => Math.max(...b.map(i => pts[i][0])) - Math.max(...a.map(i => pts[i][0])));
  const allEdges = () => { const E = []; const add = L => L.forEach((v, i) => E.push([v, L[(i + 1) % L.length]])); add(poly); hs.forEach(add); return E; };
  for (const h of hs) {
    let hm = 0; h.forEach((v, i) => { if (pts[v][0] > pts[h[hm]][0]) hm = i; });
    const H = pts[h[hm]], E = allEdges();
    let best = -1, bd = Infinity;
    poly.forEach((v, i) => { const P = pts[v], d = Math.hypot(P[0] - H[0], P[1] - H[1]); if (d >= bd) return;
      if (E.some(([a, b]) => a !== v && b !== v && a !== h[hm] && b !== h[hm] && segX(H, P, pts[a], pts[b]))) return; bd = d; best = i; });
    if (best < 0) throw new Error('hole bridge failed');
    const ring = h.slice(hm).concat(h.slice(0, hm));
    poly = poly.slice(0, best + 1).concat(ring, [h[hm], poly[best]], poly.slice(best + 1));
  }
  const tris = [], V = poly.slice(), P = i => pts[i];
  const cr = (a, b, c) => (P(b)[0] - P(a)[0]) * (P(c)[1] - P(a)[1]) - (P(b)[1] - P(a)[1]) * (P(c)[0] - P(a)[0]);
  const inside = (p, a, b, c) => { const q = P(p); if ([a, b, c].some(x => Math.hypot(P(x)[0] - q[0], P(x)[1] - q[1]) < 1e-9)) return false;
    return cr(a, b, p) >= -1e-12 && cr(b, c, p) >= -1e-12 && cr(c, a, p) >= -1e-12; };
  let guard = 0;
  while (V.length > 3 && guard++ < 100000) {
    let cut = false;
    for (let t = 0; t < V.length; t++) {
      const a = V[(t + V.length - 1) % V.length], b = V[t], c = V[(t + 1) % V.length];
      if (cr(a, b, c) <= 1e-12) continue;
      if (V.some(p => p !== a && p !== b && p !== c && inside(p, a, b, c))) continue;
      tris.push([a, b, c]); V.splice(t, 1); cut = true; break;
    }
    if (!cut) { // drop a degenerate vertex and continue
      const t = V.findIndex((b, i) => Math.abs(cr(V[(i + V.length - 1) % V.length], b, V[(i + 1) % V.length])) <= 1e-12);
      if (t < 0) throw new Error('triangulation stuck'); V.splice(t, 1);
    }
  }
  if (V.length === 3 && cr(V[0], V[1], V[2]) > 1e-12) tris.push(V.slice());
  return {pts, tris};
}

// ---------------- raster footprints -> loops ----------------
const RES = 32;   // px per model unit
function footprint(polys, bounds) {
  const [u0, v0, u1, v1] = bounds, W = Math.ceil((u1 - u0) * RES) + 4, H = Math.ceil((v1 - v0) * RES) + 4, ox = u0 - 2 / RES, oy = v0 - 2 / RES;
  const m = new Uint8Array(W * H);
  for (const P of polys) {
    const xs = P.map(p => p[0]), ys = P.map(p => p[1]);
    const i0 = Math.max(0, Math.floor((Math.min(...xs) - ox) * RES)), i1 = Math.min(W - 1, Math.ceil((Math.max(...xs) - ox) * RES));
    const j0 = Math.max(0, Math.floor((Math.min(...ys) - oy) * RES)), j1 = Math.min(H - 1, Math.ceil((Math.max(...ys) - oy) * RES));
    const sgn = Math.sign(area2(P)) || 1;
    for (let j = j0; j <= j1; j++) for (let i = i0; i <= i1; i++) {
      const x = ox + (i + 0.5) / RES, y = oy + (j + 0.5) / RES; let ok = true;
      for (let k = 0; k < P.length && ok; k++) { const a = P[k], b = P[(k + 1) % P.length]; if (sgn * ((b[0] - a[0]) * (y - a[1]) - (b[1] - a[1]) * (x - a[0])) < -1e-9) ok = false; }
      if (ok) m[j * W + i] = 1;
    }
  }
  return {m, W, H, ox, oy};
}
function traceLoops({m, W, H, ox, oy}) {
  const at = (i, j) => i >= 0 && j >= 0 && i < W && j < H && m[j * W + i];
  const out = new Map(), key = (i, j) => i * 100003 + j;
  const addE = (a, b) => { const k = key(...a); (out.get(k) || out.set(k, []).get(k)).push({a, b, used: false}); };
  for (let j = 0; j < H; j++) for (let i = 0; i < W; i++) if (at(i, j)) {
    if (!at(i, j - 1)) addE([i, j], [i + 1, j]);
    if (!at(i + 1, j)) addE([i + 1, j], [i + 1, j + 1]);
    if (!at(i, j + 1)) addE([i + 1, j + 1], [i, j + 1]);
    if (!at(i - 1, j)) addE([i, j + 1], [i, j]);
  }
  const loops = [];
  for (const list of out.values()) for (const start of list) {
    if (start.used) continue;
    const L = []; let e = start;
    while (e && !e.used) {
      e.used = true; L.push(e.a);
      const cand = (out.get(key(...e.b)) || []).filter(x => !x.used);
      if (cand.length > 1) { // prefer the right turn: keeps diagonal-touching pixels apart
        const d = [e.b[0] - e.a[0], e.b[1] - e.a[1]];
        cand.sort((p, q) => { const t = x => { const dd = [x.b[0] - x.a[0], x.b[1] - x.a[1]], c = d[0] * dd[1] - d[1] * dd[0]; return c < 0 ? 0 : c === 0 ? 1 : 2; }; return t(p) - t(q); });
      }
      e = cand[0];
    }
    const pts = L.map(([i, j]) => [ox + i / RES, oy + j / RES]);
    const clean = pts.filter((p, i) => { const a = pts[(i + pts.length - 1) % pts.length], b = pts[(i + 1) % pts.length]; return Math.abs((p[0] - a[0]) * (b[1] - p[1]) - (p[1] - a[1]) * (b[0] - p[0])) > 1e-12; });
    if (clean.length >= 3) loops.push(clean);
  }
  return loops;
}
// Loops -> [{outer, holes}] simplified; eps grows the stair removal; tiny loops dropped
function shapes(loops, eps, minArea = 0.004) {
  const simp = loops.map(L => { for (let e = eps; e >= eps / 8; e /= 2) { const s = simplify(L, e); if (s.length >= 3 && simpleLoops([s]) && Math.sign(area2(s)) === Math.sign(area2(L))) return s; } return L; })
    .filter(L => Math.abs(area2(L)) >= minArea);
  const outers = simp.filter(L => area2(L) > 0), holes = simp.filter(L => area2(L) < 0);
  const inPoly = (p, P) => { let c = false; for (let i = 0, j = P.length - 1; i < P.length; j = i++) if ((P[i][1] > p[1]) !== (P[j][1] > p[1]) && p[0] < (P[j][0] - P[i][0]) * (p[1] - P[i][1]) / (P[j][1] - P[i][1]) + P[i][0]) c = !c; return c; };
  return outers.map(o => ({outer: o, holes: holes.filter(h => inPoly(h[0], o) && Math.abs(area2(h)) < Math.abs(area2(o)))}));
}

// ---------------- parts ----------------
export class Part {
  constructor(name, bone, mat) { Object.assign(this, {name, bone, mat, v: [], f: []}); }
  vtx(p) { this.v.push(p); return this.v.length - 1; }
  face(ids, hint, tag = 'side', mat = this.mat) {
    if (ids.length === 4) { const P = ids.map(i => this.v[i]), n = norm(newell(P)), ext = Math.max(...[0, 1, 2].map(k => Math.max(...P.map(q => q[k])) - Math.min(...P.map(q => q[k]))));
      if (Math.max(...P.map(q => Math.abs(dot(sub(q, P[0]), n)))) > ext * 0.02) { for (const t of [[0, 1, 2], [2, 3, 0]]) this.face(t.map(k => ids[k]), hint, tag, mat); return; } }
    const n = newell(ids.map(i => this.v[i])); if (Math.hypot(...n) < 1e-10) return;
    if (dot(n, hint) < 0) ids = ids.slice().reverse();
    this.f.push({ids, tag, mat});
  }
}
// axis x: plane (u, v) = (z, y); axis y: plane (u, v) = (z, x)
const AX = {
  x: {k: 0, to3: (u, v, a) => [a, v, u], uv: p => [p[2], p[1]], n: [1, 0, 0]},
  y: {k: 1, to3: (u, v, a) => [v, a, u], uv: p => [p[2], p[0]], n: [0, 1, 0]},
  z: {k: 2, to3: (u, v, a) => [u, v, a], uv: p => [p[0], p[1]], n: [0, 0, 1]},   // front section (x, y) along the bore
};
const OTHER = {x: [1, 2], y: [0, 2], z: [0, 1]};   // cube rotation axes that break each extrusion
// Extrude one shape between a0 and a1 along the axis with chamfer c (falls back to 0 when the inset is invalid)
function extrude(part, ax, shape, a0, a1, c, tag = 'side') {
  const A = AX[ax];
  // small features (rail teeth, pins, thin strips) stay crisp: a chamfer there only costs faces at first-person distance
  if (a1 - a0 < 0.3 || Math.abs(area2(shape.outer)) < 0.12) c = 0;
  c = Math.min(c, (a1 - a0) / 3);
  let loops = [shape.outer, ...shape.holes], inset = loops.map(L => offset(L, c));
  const shrinks = (L, i) => Math.sign(area2(L)) === Math.sign(area2(loops[i])) && (i === 0 ? Math.abs(area2(L)) < Math.abs(area2(loops[i])) : Math.abs(area2(L)) > Math.abs(area2(loops[i])));
  if (c > 0 && !(simpleLoops(inset) && inset.every(shrinks))) { c = 0; inset = loops; }
  const secs = c > 0 ? [[a0, 1], [a0 + c, 0], [a1 - c, 0], [a1, 1]] : [[a0, 0], [a1, 0]];
  for (let li = 0; li < loops.length; li++) {
    const L = loops[li], I = inset[li];
    const rings = secs.map(([a, s]) => (s ? I : L).map(([u, v]) => part.vtx(A.to3(u, v, a))));
    for (let k = 0; k + 1 < rings.length; k++) for (let i = 0; i < L.length; i++) {
      const j = (i + 1) % L.length, e = sub(L[j], L[i]), o = [e[1], -e[0]];   // outward from material in the plane
      const lean = secs[k][1] !== secs[k + 1][1] ? (secs[k][1] ? -1 : 1) : 0, n3 = add(A.to3(o[0], o[1], 0), mul(A.n, lean * Math.hypot(...o)));
      part.face([rings[k][i], rings[k][j], rings[k + 1][j], rings[k + 1][i]], n3, lean ? 'bevel' : li ? 'wall' : tag);   // hole side walls: painter darkens them
    }
  }
  for (const [a, sgn] of [[a0, -1], [a1, 1]]) {
    const outer = c > 0 ? inset[0] : loops[0], holes = (c > 0 ? inset : loops).slice(1);
    const {pts, tris} = triangulate(outer, holes), ids = pts.map(([u, v]) => part.vtx(A.to3(u, v, a)));
    for (const t of tris) part.face(t.map(i => ids[i]), mul(A.n, sgn), 'cap');
  }
}
// Revolve a closed (z, r) profile around an axis parallel to z through (cx, cy); r = 0 points collapse onto the axis
function revolve(part, cx, cy, profile, seg, phase = Math.PI / seg, tag = 'side') {
  const ring = ([z, r]) => r < 1e-6 ? null : Array.from({length: seg}, (_, i) => { const a = phase + 2 * Math.PI * i / seg; return part.vtx([cx + r * Math.cos(a), cy + r * Math.sin(a), z]); });
  const rings = profile.map(ring), axisV = profile.map(([z, r]) => r < 1e-6 ? part.vtx([cx, cy, z]) : null);
  for (let k = 0; k < profile.length; k++) {
    const k2 = (k + 1) % profile.length, [z0, r0] = profile[k], [z1, r1] = profile[k2];
    if (r0 < 1e-6 && r1 < 1e-6) continue;
    const e = [z1 - z0, r1 - r0], o = [e[1], -e[0]];   // outward in (z, r) for a CCW profile
    for (let i = 0; i < seg; i++) {
      const j = (i + 1) % seg, a = phase + 2 * Math.PI * (i + 0.5) / seg, n3 = [o[1] * Math.cos(a), o[1] * Math.sin(a), o[0]];
      const t = Math.abs(o[0]) > 0.9 * Math.hypot(...o) ? 'cap' : tag;
      if (r0 < 1e-6) part.face([axisV[k], rings[k2][j], rings[k2][i]], n3, t);
      else if (r1 < 1e-6) part.face([rings[k][i], rings[k][j], axisV[k2]], n3, t);
      else part.face([rings[k][i], rings[k][j], rings[k2][j], rings[k2][i]], n3, t);
    }
  }
}
// Oriented box from a reference cube (plain, for the few odd compound-rotated pieces)
function box(part, cube, tag = 'side') {
  const C = cube.corners, ids = C.map(p => part.vtx(p)), ctr = mul(C.reduce((a, p) => add(a, p), [0, 0, 0]), 1 / 8);
  for (const q of [[0, 2, 3, 1], [4, 5, 7, 6], [0, 1, 5, 4], [2, 6, 7, 3], [0, 4, 6, 2], [1, 3, 7, 5]]) {
    const fc = mul(q.reduce((a, i) => add(a, C[i]), [0, 0, 0]), 1 / 4); part.face(q.map(i => ids[i]), sub(fc, ctr), tag);
  }
}
export const PARTS = [];
const P = (name, bone, mat) => { const p = new Part(name, bone, mat); PARTS.push(p); return p; };

// ---------------- build rules ----------------
// material per original group (overridden per region below)
const MAT = {
  br51_01_default: 'receiver', bone5: 'receiver', octagon9: 'sight', bone6: 'lower', bone: 'rail', bone2: 'handguard', bone3: 'handguard',
  bone4: 'handguard', group49: 'rail', group2: 'rail', group3: 'rail', bone8: 'receiver', bone7: 'receiver', ar_stock_adapter_1: 'hinge',
  stock_default: 'stock', octagon: 'barrel', octagon8: 'barrel', octagon3: 'barrel', octagon5: 'barrel', octagon2: 'barrel', octagon6: 'barrel',
  bone13: 'rail', bone11: 'rail', bone12: 'rail', bone14: 'rail', bone20: 'rail', bone21: 'rail', afl_iron_risers: 'sight', muzzle_default: 'muzzle',
  bolt: 'bolt', octagon7: 'bolt', bone10: 'bolt', mag_standard: 'magazine', hu2: 'floorplate',
};
const AXIS = {};   // per-group override; otherwise the axis with the fewest non-conforming cubes (x preferred on ties)
// target bone for each group's mesh: statically rotated or unanimated helper groups attach to an identity-framed ancestor
const BONE = {group2: 'br51_01_default', group3: 'br51_01_default', '3': 'bullet', '7': 'bullet', bullet_in_barrel: 'bullet_in_barrel'};
const EPS = {bone6: 0.09, stock_default: 0.07, mag_standard: 0.07, hu2: 0.05, bone4: 0.07, bone5: 0.06, group49: 0.06, bone8: 0.06};
const CHAMFER = {receiver: 0.04, lower: 0.04, handguard: 0.04, rail: 0.02, sight: 0.025, bolt: 0.025, hinge: 0.03, stock: 0.04, magazine: 0.035, floorplate: 0.04, grip: 0.05};
// regions of the lower receiver (side profile z, y): pistol grip is polymer
const GRIP_REGION = [[8.0, 2.8], [15.6, 2.8], [15.6, 8.55], [8.0, 8.55]];   // whole grip incl. heel and trigger guard
const SPLIT = {bone6: {region: GRIP_REGION, name: 'bone6_grip', mat: 'grip'}};
const inRegion = (p, R) => { let c = false; for (let i = 0, j = R.length - 1; i < R.length; j = i++) if ((R[i][1] > p[1]) !== (R[j][1] > p[1]) && p[0] < (R[j][0] - R[i][0]) * (p[1] - R[i][1]) / (R[j][1] - R[i][1]) + R[i][0]) c = !c; return c; };

function slabGroup(name, info) {
  const mat = MAT[name] || 'receiver', bone = BONE[name] || name, split = SPLIT[name];
  const grot = info.group.rotation || [0, 0, 0];
  // per cube: the axis its own rotation keeps valid (unrotated -> side profile x); compound rotations -> oriented box
  const axisOf = c => { const r = c.rot, n = r.filter(v => v).length, ok = a => !OTHER[a].some(i => r[i] || grot[i]);
    if (AXIS[name] && ok(AXIS[name])) return AXIS[name];
    if (n === 0) return ['x', 'z', 'y'].find(ok); if (n > 1) return null;
    return ['x', 'y', 'z'][r.findIndex(v => v)] && ok(['x', 'y', 'z'][r.findIndex(v => v)]) ? ['x', 'y', 'z'][r.findIndex(v => v)] : null; };
  const byAxis = {x: [], y: [], z: []}, boxes = [];
  for (const c of info.cubes) { const a = axisOf(c); (a ? byAxis[a] : boxes).push(c); }
  const part = P(`${name}`, bone, mat), stats = {x: 0, y: 0, z: 0, dropped: 0, boxes: boxes.length};
  for (const ax of ['x', 'y', 'z']) {
    const cubes = byAxis[ax]; if (!cubes.length) continue;
    const A = AX[ax], layers = [];
    for (const c of cubes) {
      const vs = c.corners.map(p => p[A.k]), a0 = Math.min(...vs), a1 = Math.max(...vs);
      let L = layers.find(l => Math.abs(l.a0 - a0) < 0.02 && Math.abs(l.a1 - a1) < 0.02);
      if (!L) layers.push(L = {a0, a1, polys: []});
      L.polys.push(hull(c.corners.map(A.uv)));
    }
    const all = cubes.flatMap(c => c.corners.map(A.uv)), bounds = [Math.min(...all.map(p => p[0])), Math.min(...all.map(p => p[1])), Math.max(...all.map(p => p[0])), Math.max(...all.map(p => p[1]))];
    for (const L of layers) L.fp = footprint(L.polys, bounds);
    // drop layers enclosed by the union of wider layers that contain their extent (hidden geometry)
    const kept = layers.filter(L => {
      const cover = layers.filter(o => o !== L && o.a0 <= L.a0 + 1e-6 && o.a1 >= L.a1 - 1e-6 && (o.a1 - o.a0) > (L.a1 - L.a0) + 1e-6);
      if (!cover.length) return true;
      for (let i = 0; i < L.fp.m.length; i++) if (L.fp.m[i] && !cover.some(o => o.fp.m[i])) return true;
      return false;
    });
    stats[ax] = kept.length; stats.dropped += layers.length - kept.length;
    for (const L of kept) {
      const masks = [[L.fp, part, mat]];
      if (split && ax === 'x') { // region pixels become their own material part (straight internal cut)
        const a = {...L.fp, m: new Uint8Array(L.fp.m)}, b = {...L.fp, m: new Uint8Array(L.fp.m)};
        for (let j = 0; j < L.fp.H; j++) for (let i = 0; i < L.fp.W; i++) { const k = j * L.fp.W + i; if (!L.fp.m[k]) continue;
          (inRegion([L.fp.ox + (i + 0.5) / RES, L.fp.oy + (j + 0.5) / RES], split.region) ? a : b).m[k] = 0; }
        const sp = PARTS.find(p => p.name === split.name) || P(split.name, bone, split.mat);
        masks.splice(0, 1, [a, part, mat], [b, sp, split.mat]);
      }
      for (const [fp, target, m] of masks) for (const sh of shapes(traceLoops(fp), EPS[name] ?? 0.045)) extrude(target, ax, sh, L.a0, L.a1, CHAMFER[m] ?? 0.03);
    }
  }
  for (const c of boxes) box(part, c, 'bevel');
  return stats;
}
function hull(pts) {
  const P2 = pts.slice().sort((a, b) => a[0] - b[0] || a[1] - b[1]), cr = (o, a, b) => (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0]);
  const lo = [], hi = [];
  for (const p of P2) { while (lo.length >= 2 && cr(lo[lo.length - 2], lo[lo.length - 1], p) <= 1e-12) lo.pop(); lo.push(p); }
  for (const p of P2.slice().reverse()) { while (hi.length >= 2 && cr(hi[hi.length - 2], hi[hi.length - 1], p) <= 1e-12) hi.pop(); hi.push(p); }
  return lo.slice(0, -1).concat(hi.slice(0, -1));
}
function latheGroup(name, info, seg) {
  const pts = info.cubes.flatMap(c => c.corners), xs = pts.map(p => p[0]), ys = pts.map(p => p[1]), zs = pts.map(p => p[2]);
  const cx = (Math.min(...xs) + Math.max(...xs)) / 2, cy = (Math.min(...ys) + Math.max(...ys)) / 2, r = (Math.max(...ys) - Math.min(...ys)) / 2;
  const z0 = Math.min(...zs), z1 = Math.max(...zs), c = Math.min(0.04, (z1 - z0) / 4, r / 4);
  const part = P(name, BONE[name] || name, MAT[name] || 'barrel');
  revolve(part, cx, cy, [[z0, 0], [z1, 0], [z1, r - c], [z1 - c, r], [z0 + c, r], [z0, r - c]], seg);
  return {cx, cy, r, z0, z1};
}
const round = (v, k = 5) => +v.toFixed(k);

// ---- assemble ----
export const report = {};
for (const [name, info] of groupInfo) {
  if (info.hidden || !info.cubes.length) continue;
  if (['3', '7', 'bullet_in_barrel', 'muzzle_default'].includes(name)) continue;   // custom below
  if (/^octagon\d*$/.test(name)) {
    const pts = info.cubes.flatMap(c => c.corners), dx = Math.max(...pts.map(p => p[0])) - Math.min(...pts.map(p => p[0])), dy = Math.max(...pts.map(p => p[1])) - Math.min(...pts.map(p => p[1]));
    if (Math.abs(dx - dy) < 0.06) { report[name] = latheGroup(name, info, name === 'octagon' ? 20 : name === 'octagon9' || name === 'octagon7' ? 12 : 16); continue; }
  }
  report[name] = slabGroup(name, info);
}
// flash hider: revolved bored body + four prongs (reference cube radii)
{
  const part = P('muzzle_default', 'muzzle_default', 'muzzle'), cy = 11.4375;
  revolve(part, 0, cy, [[-23.219, 0.30], [-23.219, 0.5625], [-23.984, 0.5625], [-23.984, 0.469], [-25.75, 0.469], [-25.75, 0.438], [-25.813, 0.438],
    [-25.813, 0.469], [-26.375, 0.469], [-26.375, 0.281], [-23.219 - 0.02, 0.281]], 16);
  for (const c of groupInfo.get('muzzle_default').cubes.filter(c => Math.min(...c.corners.map(p => p[2])) < -27.9)) box(part, c, 'side');
}
// faces of the split groups built along other axes (front / top sections, boxes) follow the same region by centroid
for (const [name, sp] of Object.entries(SPLIT)) { const part = PARTS.find(p => p.name === name);
  for (const f of part.f) { const c = f.ids.reduce((a, i) => add(a, mul(part.v[i], 1 / f.ids.length)), [0, 0, 0]); if (inRegion([c[2], c[1]], sp.region)) f.mat = sp.mat; } }

// No visible ammunition (2026-09-28): the 7.62 round rework comes first, then dynamic ammo display. The ammo bones
// (bullet -> 2 -> 3 / 7, bullet_in_barrel, reload_bullet*) stay in the rig with no geometry, so the Phase 1 animations
// keep every channel and the later dynamic-ammo logic has its mount points.

// ---------------- magazine states: identical copies on the reload / dropped-magazine bones ----------------
// (copy bones share the originals' bind pose; only one state is visible at a time, the others are zero-scaled)
function assert(c, m) { if (!c) throw new Error(m); }
for (const [src, dst] of [['mag_standard', 'reload_mag_standard'], ['hu2', 'reload_hu2'], ['mag_standard', 'empty_old_mag_standard'], ['hu2', 'empty_old_hu2'],
  ]) {
  const o = PARTS.find(q => q.name === src), c = new Part(dst, dst, o.mat);
  c.v = o.v.map(q => q.slice()); c.f = o.f.map(f => ({...f, ids: f.ids.slice()})); c.copyOf = o; PARTS.push(c);
}
for (const p of PARTS) assert(groupInfo.has(p.bone), 'bone ' + p.bone);

// ---------------- UV: planar islands (normal flood fill), shelf packing into 1024 ----------------
const ATLAS = 1024, PAD = 1;   // nearest-filtered entity texture (no mipmaps): 1 px gutter suffices
export const islands = [];
for (const part of PARTS) {
  if (part.copyOf) continue;   // copies reuse their original's UVs
  const F = part.f.map(f => ({f, n: norm(newell(f.ids.map(i => part.v[i])))}));
  const edge = new Map();
  F.forEach((o, i) => o.f.ids.forEach((a, k) => { const b = o.f.ids[(k + 1) % o.f.ids.length], e = a < b ? a + ',' + b : b + ',' + a; (edge.get(e) || edge.set(e, []).get(e)).push(i); }));
  const seen = new Uint8Array(F.length);
  for (let s0 = 0; s0 < F.length; s0++) {
    if (seen[s0]) continue; seen[s0] = 1; const q = [s0], mem = [], n0 = F[s0].n;
    while (q.length) {
      const i = q.pop(); mem.push(i);
      const ids = F[i].f.ids;
      for (let k = 0; k < ids.length; k++) {
        const a = ids[k], b = ids[(k + 1) % ids.length], e = a < b ? a + ',' + b : b + ',' + a;
        for (const j of edge.get(e)) if (!seen[j] && F[j].f.mat === F[s0].f.mat && dot(F[j].n, n0) > 0.94 && dot(F[j].n, F[i].n) > 0.97) { seen[j] = 1; q.push(j); }
      }
    }
    const n = norm(mem.reduce((acc, i) => add(acc, F[i].n), [0, 0, 0]));
    let t = sub([0, 0, 1], mul(n, n[2])); if (Math.hypot(...t) < 0.3) t = sub([0, 1, 0], mul(n, n[1])); t = norm(t); const bt = cross(n, t);
    const vs = [...new Set(mem.flatMap(i => F[i].f.ids))], uv = new Map(vs.map(i => [i, [dot(part.v[i], t), -dot(part.v[i], bt)]]));
    const us = [...uv.values()], u0 = Math.min(...us.map(a => a[0])), v0 = Math.min(...us.map(a => a[1]));
    islands.push({part, faces: mem.map(i => F[i]), uv, u0, v0, w: Math.max(...us.map(a => a[0])) - u0, h: Math.max(...us.map(a => a[1])) - v0});
  }
}
// long islands lie horizontally (a barrel facet is 25 units long)
for (const is of islands) if (is.h > is.w) { for (const [k, [u, v]] of is.uv) is.uv.set(k, [v, -u]); const us = [...is.uv.values()]; is.u0 = Math.min(...us.map(a => a[0])); is.v0 = Math.min(...us.map(a => a[1])); [is.w, is.h] = [is.h, is.w]; }
function pack(S) {
  let x = PAD, y = PAD, rowH = 0;
  for (const is of islands.slice().sort((a, b) => b.h - a.h || b.w - a.w)) {
    const W = Math.ceil(is.w * S) + 1, H = Math.ceil(is.h * S) + 1;
    if (W + 2 * PAD > ATLAS) return false;
    if (x + W + PAD > ATLAS) { x = PAD; y += rowH + PAD; rowH = 0; }
    if (y + H + PAD > ATLAS) return false;
    Object.assign(is, {px: x, py: y, W, H}); x += W + PAD; rowH = Math.max(rowH, H);
  }
  return true;
}
let S = 64; while (!pack(S)) S -= 0.5;
export const texelsPerUnit = S;
const uvOf = (is, vi) => { const [u, v] = is.uv.get(vi); return [(u - is.u0) * S + is.px + 0.5, (v - is.v0) * S + is.py + 0.5]; };
const faceUV = new Map();   // part -> face -> [[u, v] per corner]
for (const is of islands) for (const {f} of is.faces) { const m = faceUV.get(is.part) || faceUV.set(is.part, new Map()).get(is.part); m.set(f, f.ids.map(i => uvOf(is, i))); }

// ---------------- Base Color + LabPBR (same raster pass) ----------------
// modern dark battle rifle: graphite coated receiver, cooler anodised handguard, darker rails, near-black nitrided barrel,
// brighter machined bolt, dark treated sights, stamped-steel magazine, black polymer stock / grip / floorplate.
// Separation through value + smoothness + metal; bevel highlights, hole-wall occlusion and the original artist's
// panel zoning (below) keep the surfaces from reading as an untextured clay model.
export const MATS = {   // base colour, bevel highlight (added), smoothness open / edge, F0 open (255 metal, else linear F0), F0 of bevels
  // Texture pass (2026-09-28): black-steel rifle one step darker; every large surface is a COATING (phosphate /
  // ceramic-coated steel, hard-anodised aluminium: F0 ~0.09, matte) so shader packs no longer mirror the sky off the
  // side panels; only the machined bolt group is bare metal, and the bevel strips read as worn bare edges (metal F0).
  receiver:   {c: [40, 43, 46], hl: 22, sm: 86,  se: 140, f0: 24, edgeF0: 255},
  lower:      {c: [38, 40, 43], hl: 20, sm: 80,  se: 132, f0: 24, edgeF0: 255},
  handguard:  {c: [43, 46, 49], hl: 22, sm: 96,  se: 145, f0: 24, edgeF0: 255},
  rail:       {c: [33, 35, 38], hl: 18, sm: 90,  se: 138, f0: 24, edgeF0: 255},
  barrel:     {c: [28, 29, 31], hl: 14, sm: 92,  se: 140, f0: 24, edgeF0: 255},
  muzzle:     {c: [32, 33, 35], hl: 16, sm: 84,  se: 132, f0: 24, edgeF0: 255},
  bolt:       {c: [76, 78, 81], hl: 24, sm: 165, se: 190, f0: 255},
  hinge:      {c: [41, 43, 46], hl: 20, sm: 86,  se: 136, f0: 24, edgeF0: 255},
  sight:      {c: [35, 37, 40], hl: 18, sm: 76,  se: 128, f0: 24, edgeF0: 255},
  magazine:   {c: [39, 41, 43], hl: 20, sm: 82,  se: 132, f0: 24, edgeF0: 255},
  floorplate: {c: [25, 26, 27], hl: 5, sm: 62, se: 72, f0: 10},
  stock:      {c: [31, 32, 34], hl: 7, sm: 58, se: 70, f0: 10},
  grip:       {c: [28, 29, 30], hl: 6, sm: 48, se: 60, f0: 10},
};
// ---- tone zoning from the original artist's texture ----
// Every reference cube face carries one mean tone (its UV rect in the Phase 1 atlas). A texel on the new mesh takes the
// tone of the reference face it lies on, relative to its material's median, as a gentle value factor (0.90..1.10).
// Hue is never transferred (the old yellow floorplate stays black polymer); pixel-level noise of the old atlas is
// averaged away, so each panel stays one clean tone and the zoning follows the original cube panel layout.
function readPng(b) {
  let p = 8, w, h, ct, id = [];
  while (p < b.length) { const l = b.readUInt32BE(p), t = b.toString('ascii', p + 4, p + 8), d = b.subarray(p + 8, p + 8 + l); if (t === 'IHDR') { w = d.readUInt32BE(0); h = d.readUInt32BE(4); ct = d[9]; } if (t === 'IDAT') id.push(d); p += 12 + l; }
  const bpp = ct === 6 ? 4 : 3, raw = zlib.inflateSync(Buffer.concat(id)), st = w * bpp, px = Buffer.alloc(h * st);
  for (let y = 0; y < h; y++) {
    const f = raw[y * (st + 1)], r = raw.subarray(y * (st + 1) + 1, (y + 1) * (st + 1));
    for (let i = 0; i < st; i++) {
      const a = i >= bpp ? px[y * st + i - bpp] : 0, bb = y ? px[(y - 1) * st + i] : 0, c = i >= bpp && y ? px[(y - 1) * st + i - bpp] : 0; let v = r[i];
      if (f === 1) v += a; else if (f === 2) v += bb; else if (f === 3) v += (a + bb) >> 1;
      else if (f === 4) { const pp = a + bb - c, pa = Math.abs(pp - a), pb = Math.abs(pp - bb), pc = Math.abs(pp - c); v += pa <= pb && pa <= pc ? a : pb <= pc ? bb : c; }
      px[y * st + i] = v & 255;
    }
  }
  return {w, h, bpp, px};
}
const OLD = readPng(Buffer.from(ref.textures[0].source.split(',')[1], 'base64')), OLD_UV = OLD.w / (ref.textures[0].uv_width || ref.resolution.width);
const FACE_DIR = {east: [0, 1], west: [0, -1], up: [1, 1], down: [1, -1], south: [2, 1], north: [2, -1]};
const lum = c => 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2];
for (const info of groupInfo.values()) for (const c of info.cubes) {
  c.inv = M4.inv(c.M); c.tone = {};
  for (const [dir, [ax, sg]] of Object.entries(FACE_DIR)) {
    const fc = c.e.faces?.[dir]; if (!fc || fc.texture === null || fc.texture === undefined) continue;
    const [u0, v0, u1, v1] = fc.uv.map(v => v * OLD_UV), xa = Math.floor(Math.min(u0, u1)), xb = Math.max(xa + 1, Math.ceil(Math.max(u0, u1))), ya = Math.floor(Math.min(v0, v1)), yb = Math.max(ya + 1, Math.ceil(Math.max(v0, v1)));
    let s = 0, n = 0;
    for (let y = ya; y < yb; y++) for (let x = xa; x < xb; x++) { if (x < 0 || y < 0 || x >= OLD.w || y >= OLD.h) continue; const k = (y * OLD.w + x) * OLD.bpp; if (OLD.bpp === 4 && OLD.px[k + 3] < 20) continue; s += lum([OLD.px[k], OLD.px[k + 1], OLD.px[k + 2]]); n++; }
    if (n) c.tone[dir] = {L: s / n, n: norm(M4.dir(c.M, [0, 1, 2].map(i => i === ax ? sg : 0)))};
  }
}
const sourceGroups = p => { const b = p.copyOf || p, nm = b.name === 'bone6_grip' ? 'bone6' : b.name; return groupInfo.has(nm) && groupInfo.get(nm).cubes.length ? [nm] : []; };
function toneL(part, pos, n) {   // luminance of the reference face under this surface point, or null
  let best = null, bd = 0.13;
  for (const g of sourceGroups(part)) for (const c of groupInfo.get(g).cubes) {
    const l = M4.pt(c.inv, pos);
    if (l.some((v, i) => v < c.from[i] - 0.13 || v > c.to[i] + 0.13)) continue;
    for (const [dir, t] of Object.entries(c.tone)) {
      if (dot(t.n, n) < 0.6) continue;
      const [ax, sg] = FACE_DIR[dir], d = Math.abs(l[ax] - (sg > 0 ? c.to[ax] : c.from[ax]));
      if (d < bd) { bd = d; best = t.L; }
    }
  }
  return best;
}
// material medians from the reference faces (area weighted), the neutral point of the zoning
export const MAT_REF = {};
{
  const acc = {};
  for (const part of PARTS) { if (part.copyOf) continue; for (const g of sourceGroups(part)) for (const c of groupInfo.get(g).cubes) for (const [dir, t] of Object.entries(c.tone)) {
    const ax = FACE_DIR[dir][0], o = [0, 1, 2].filter(i => i !== ax), a = (c.to[o[0]] - c.from[o[0]]) * (c.to[o[1]] - c.from[o[1]]);
    (acc[part.mat] ||= []).push([t.L, a]); } }
  for (const [m, L] of Object.entries(acc)) { L.sort((a, b) => a[0] - b[0]); const tot = L.reduce((s, q) => s + q[1], 0); let s = 0; for (const [l, a] of L) { s += a; if (s >= tot / 2) { MAT_REF[m] = l; break; } } }
}
// metals only: molded polymer (stock, grip, floorplate) is one uniform tone
const ZONED = new Set(['receiver', 'lower', 'handguard', 'rail', 'barrel', 'muzzle', 'hinge', 'sight', 'magazine', 'bolt']);
const zone = (part, mat, pos, n) => { if (!ZONED.has(mat) || !MAT_REF[mat]) return 1; const L = toneL(part, pos, n); return L == null ? null : Math.max(0.9, Math.min(1.1, (L / MAT_REF[mat]) ** 0.5)); };
const hash = (a, b, c = 0) => { let h = Math.imul(a | 0, 374761393) ^ Math.imul(b | 0, 668265263) ^ Math.imul(c | 0, 2147483647); h = Math.imul(h ^ (h >>> 13), 1274126177); return ((h ^ (h >>> 16)) >>> 0) / 4294967295; };
const vn3 = (x, y, z) => {   // smooth 3D value noise in [0, 1]: low-frequency variation only, never per-pixel
  const xi = Math.floor(x), yi = Math.floor(y), zi = Math.floor(z), s = t => t * t * (3 - 2 * t), l = (a, b, t) => a + (b - a) * t;
  const sx = s(x - xi), sy = s(y - yi), sz = s(z - zi), h = (a, b, c) => hash(a, b, c * 7919 + 17);
  return l(l(l(h(xi, yi, zi), h(xi + 1, yi, zi), sx), l(h(xi, yi + 1, zi), h(xi + 1, yi + 1, zi), sx), sy),
    l(l(h(xi, yi, zi + 1), h(xi + 1, yi, zi + 1), sx), l(h(xi, yi + 1, zi + 1), h(xi + 1, yi + 1, zi + 1), sx), sy), sz);
};
// One texel: material base x faint top light x zoning x (wall / underside occlusion), bevel strips carry the edge
// highlight as one uniform tone (no per-pixel rims, no staircases); metals get a faint low-frequency value / smoothness drift.
// Bevel strips shorter than BEVEL_FULL fade their highlight out: short bright dashes alias into sparkle at distance.
const BEVEL_FULL = 0.35;
const faceLen = (part, f) => f.__len ??= Math.max(...f.ids.map((id, k) => Math.hypot(...sub(part.v[f.ids[(k + 1) % f.ids.length]], part.v[id]))));
function shade(part, f, n, pos, z, e = 0, cc = 0) {
  const m = MATS[f.mat], metal = m.f0 === 255 || !!m.edgeF0, drift = metal ? 2 * vn3(pos[0] * 1.4, pos[1] * 1.4, pos[2] * 1.4) - 1 : 0;
  let k = (1 + 0.03 * n[1] - 0.02 * Math.max(0, -n[1])) * z * (1 + 0.012 * drift);
  if (f.tag === 'wall') k *= 0.74;
  k *= 1 - 0.28 * cc;
  const hl = f.tag === 'bevel' ? m.hl * Math.min(1, faceLen(part, f) / BEVEL_FULL) : m.hl * 0.8 * e;
  const ao = f.tag === 'wall' ? 195 : f.tag === 'cap' && n[1] < -0.5 ? 225 : 255;
  const sm = (f.tag === 'bevel' ? m.se : f.tag === 'wall' ? m.sm - 20 : m.sm + (m.se - m.sm) * e) + (metal ? 5 * drift : 0);
  return {c: m.c.map(v => v * k + hl), s: [sm, f.tag === 'bevel' && m.edgeF0 ? m.edgeF0 : m.f0, 0, 255], n: [128, 128, Math.round(ao * (1 - 0.18 * cc)), 255]};
}
const img = Buffer.alloc(ATLAS * ATLAS * 4), spec = Buffer.alloc(ATLAS * ATLAS * 4), nrm = Buffer.alloc(ATLAS * ATLAS * 4);
for (let i = 0; i < ATLAS * ATLAS; i++) { img.set([40, 42, 44, 255], i * 4); spec.set([100, 255, 0, 255], i * 4); nrm.set([128, 128, 255, 255], i * 4); }
const clamp8 = a => a.map(v => Math.max(0, Math.min(255, Math.round(v))));
const put = (x, y, t) => { if (x < 0 || y < 0 || x >= ATLAS || y >= ATLAS) return; const k = (y * ATLAS + x) * 4; img.set([...clamp8(t.c), 255], k); spec.set(clamp8(t.s), k); nrm.set(clamp8(t.n), k); };
const covered = new Uint8Array(ATLAS * ATLAS);
export const zoneStats = {hit: 0, miss: 0};
for (const is of islands) {
  // island zoning fallback (texels that miss every reference face) and the gutter tone: area-weighted face samples
  let zs = 0, za = 0;
  for (const {f, n} of is.faces) { const P3 = f.ids.map(i => is.part.v[i]); for (let q = 1; q + 1 < P3.length; q++) {
    const a = Math.hypot(...cross(sub(P3[q], P3[0]), sub(P3[q + 1], P3[0]))) / 2, c = mul(add(add(P3[0], P3[q]), P3[q + 1]), 1 / 3), z = zone(is.part, f.mat, c, n);
    if (z != null) { zs += z * a; za += a; } } }
  is.zone = za ? zs / za : 1;
  const f0 = is.faces[0], c0 = f0.f.ids.map(i => is.part.v[i]).reduce((a, p) => add(a, mul(p, 1 / f0.f.ids.length)), [0, 0, 0]);
  const t = shade(is.part, f0.f, f0.n, c0, is.zone);
  for (let y = is.py - PAD; y < is.py + is.H + PAD; y++) for (let x = is.px - PAD; x < is.px + is.W + PAD; x++) put(x, y, t);
}
// Edge terms per island (as P9): hard edges (> ~25 deg) on the island border. Convex -> narrow highlight rim (2.2 px),
// concave -> soft darkening (3 px). Only edges running along a texel axis get a rim: a slanted rim would stair-step.
const partEdges = new Map();
for (const part of PARTS) { if (part.copyOf) continue; const m = new Map();
  part.f.forEach(f => f.ids.forEach((a, k) => { const b = f.ids[(k + 1) % f.ids.length], key = a < b ? a + ',' + b : b + ',' + a; (m.get(key) || m.set(key, []).get(key)).push(f); }));
  partEdges.set(part, m); }
const fNorm = f => f.__n || (f.__n = norm(newell(f.ids.map(i => f.__p.v[i]))));
for (const part of PARTS) for (const f of part.f) f.__p = part;
for (const is of islands) {
  const inIs = new Set(is.faces.map(o => o.f)), em = partEdges.get(is.part), segs = [];
  for (const {f, n} of is.faces) f.ids.forEach((a, k) => {
    const b = f.ids[(k + 1) % f.ids.length], key = a < b ? a + ',' + b : b + ',' + a;
    for (const g of em.get(key)) { if (g === f || inIs.has(g)) continue; const d = dot(n, fNorm(g)); if (d > 0.9) continue;
      const cg = g.ids.reduce((s, i) => add(s, mul(is.part.v[i], 1 / g.ids.length)), [0, 0, 0]);
      const A = uvOf(is, a), B = uvOf(is, b), du = Math.abs(B[0] - A[0]), dv = Math.abs(B[1] - A[1]);
      if (!(du < 0.07 * dv || dv < 0.07 * du) || Math.hypot(du, dv) < BEVEL_FULL * S) continue;   // no rims on short edges
      segs.push({a: A, b: B, cvx: dot(sub(cg, is.part.v[a]), n) < 0, s: Math.min(1, (1 - d) / 0.35)}); }
  });
  is.segs = segs;
}
const edgeAt = (is, X, Y) => { let e = 0, cc = 0;
  for (const sg of is.segs) { const ab = [sg.b[0] - sg.a[0], sg.b[1] - sg.a[1]], L2 = ab[0] * ab[0] + ab[1] * ab[1], t = Math.max(0, Math.min(1, ((X - sg.a[0]) * ab[0] + (Y - sg.a[1]) * ab[1]) / L2));
    const dd = Math.hypot(X - sg.a[0] - ab[0] * t, Y - sg.a[1] - ab[1] * t);
    if (sg.cvx) e = Math.max(e, sg.s * Math.max(0, 1 - dd / 2.2)); else cc = Math.max(cc, sg.s * Math.max(0, 1 - dd / 3)); }
  return [e, cc]; };
for (const pass of ['inside', 'fill']) for (const is of islands) for (const {f, n} of is.faces) {
  const A = f.ids.map(i => uvOf(is, i)), P3 = f.ids.map(i => is.part.v[i]);
  // narrow faces (bevel strips, pins, teeth) take one tone: sampled at the face centre
  const narrowFace = f.tag === 'bevel' || A.length === 0;
  const cF = P3.reduce((a, p) => add(a, mul(p, 1 / P3.length)), [0, 0, 0]), zF = zone(is.part, f.mat, cF, n) ?? is.zone, tF = shade(is.part, f, n, cF, zF);
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
      const i = y * ATLAS + x; if (pass === 'fill' && covered[i]) continue; covered[i] = 1;
      if (narrowFace) { put(x, y, tF); continue; }
      const pos = add(add(mul(P3[0], l1), mul(P3[q], l2)), mul(P3[q + 1], l3)), z = zone(is.part, f.mat, pos, n);
      if (z == null) zoneStats.miss++; else zoneStats.hit++;
      const [e, cc] = f.tag === 'wall' ? [0, 0] : edgeAt(is, X, Y);
      put(x, y, shade(is.part, f, n, pos, z ?? is.zone, e, cc));
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
export const PNG = [png(img), png(spec), png(nrm)];
const uuid = s => { const h = createHash('sha256').update('afl-br51-01-v2-native:' + s).digest('hex'); return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-8${h.slice(17, 20)}-${h.slice(20, 32)}`; };
const r6 = v => +v.toFixed(12) || 0;   // full bake precision: V2 keeps a planar quad only when the source proves it
// source: the Phase 1 rig (groups, pivots, uuids, animations) with the reference cubes replaced by the new meshes;
// the source-only reference arms (export false) stay for authoring
export function buildSource() {
  const src = structuredClone(ref), byName = new Map(src.groups.map(g => [g.name, g]));
  const find = (ns, id) => { for (const n of ns) { if (typeof n === 'string') continue; if (n.uuid === id) return n; const r = find(n.children, id); if (r) return r; } };
  const keep = new Set(src.elements.filter(e => e.export === false).map(e => e.uuid));
  (function strip(ns) { for (const n of ns) if (typeof n !== 'string') { n.children = n.children.filter(c => typeof c !== 'string' || keep.has(c)); strip(n.children); } })(src.outliner);
  src.elements = src.elements.filter(e => keep.has(e.uuid));
  for (const p of PARTS) {
    const g = byName.get(p.bone), inv = M4.inv(groupInfo.get(p.bone).static), node = find(src.outliner, g.uuid), uvs = faceUV.get(p.copyOf || p);
    const key = i => i.toString(36).padStart(4, '0'), vertices = {}, faces = {};
    p.v.forEach((q, i) => { vertices[key(i)] = M4.pt(inv, q).map(r6); });
    (p.copyOf || p).f.forEach((f, fi) => { const uv = uvs.get(f); faces['f' + key(fi)] = {uv: Object.fromEntries(f.ids.map((id, j) => [key(id), uv[j].map(r6)])), vertices: f.ids.map(key), texture: 0}; });
    const id = uuid('mesh:' + p.name);
    src.elements.push({name: p.name, color: 0, origin: [0, 0, 0], rotation: [0, 0, 0], shading: 'flat', export: true, visibility: true, locked: false,
      render_order: 'default', scope: 0, allow_mirror_modeling: true, vertices, faces, type: 'mesh', uuid: id});
    node.children.push(id);
  }
  src.meta = {...src.meta, model_format: 'free'};
  src.resolution = {width: ATLAS, height: ATLAS};
  const t0 = src.textures[0];
  src.textures = [{...t0, name: 'br51_01.png', width: ATLAS, height: ATLAS, uv_width: ATLAS, uv_height: ATLAS, source: 'data:image/png;base64,' + PNG[0].toString('base64')}, ...src.textures.slice(1)];
  return src;
}

const source = buildSource();
// Phase 2 retires the source-only TaCZ leftovers: the old cube red dot / laser (`sight`) and the cube foregrip
// (`grip_default`). Neither is exported; their animators carry no keyframes. Both stay recoverable from git REF.
{
  const drop = new Set(), dropEl = new Set(), G = new Map(source.groups.map(g => [g.uuid, g]));
  (function walk(ns, gone) { for (const n of ns) { if (typeof n === 'string') { if (gone) dropEl.add(n); continue; }
    const g = gone || ['sight', 'grip_default'].includes(G.get(n.uuid).name); if (g) drop.add(n.uuid); walk(n.children, g); } })(source.outliner, false);
  (function prune(ns) { for (const n of ns) if (typeof n !== 'string') { n.children = n.children.filter(c => typeof c === 'string' ? !dropEl.has(c) : !drop.has(c.uuid)); prune(n.children); } })(source.outliner);
  source.groups = source.groups.filter(g => !drop.has(g.uuid)); source.elements = source.elements.filter(e => !dropEl.has(e.uuid));
  for (const a of source.animations) for (const id of Object.keys(a.animators || {})) if (drop.has(id)) { assert(!a.animators[id].keyframes?.length, 'keyed ' + id); delete a.animators[id]; }
  console.error(`retired ${drop.size} groups / ${dropEl.size} cubes`);
}
for (const g of source.groups) delete g.bedrock_binding;   // Bedrock-only field; the Free Model source has no binding
Object.assign(source.textures[0], {relative_path: 'textures/br51_01.png', folder: '', namespace: ''});
// Inspect rack sounds (2026-09-28): the author's inspect pulls the bolt back (3.458 -> 3.75 s) with 'draw' standing in
// for the pull and lets it forward (4.208 -> 4.292 s) silently. Two dedicated user-made sounds replace that:
// inspect_slide_back (its rear-stop transient 0.115 s into the file lands on the bolt reaching the rear, 3.75 s) and
// inspect_slide_release (impact 0.065 s in lands 0.033 s after the bolt is home, the reload_empty timing).
export const INSPECT_RACK = [{time: 3.635, effect: 'apocalypse_firstlight:br51_01_inspect_slide_back', seed: 'inspect-slide-back-sound'},
  {time: 4.26, effect: 'apocalypse_firstlight:br51_01_inspect_slide_release', seed: 'inspect-slide-release-sound'}];
{
  const inspect = source.animations.find(a => a.name === 'inspect');
  const fx = Object.values(inspect.animators).find(a => a.type === 'effect');
  fx.keyframes = fx.keyframes.filter(k => !(k.time === 3.5 && k.data_points[0].effect === 'apocalypse_firstlight:br51_01_draw'));
  for (const k of INSPECT_RACK) fx.keyframes.push({channel: 'sound', data_points: [{effect: k.effect, locator: '', file: ''}],
    uuid: uuid(k.seed), time: k.time, color: -1, interpolation: 'linear'});
}
// Rifle Suppressor V1 (2026-09-28): muzzle_anchor = the muzzle-device mounting shoulder (front face of the flash hider's rear
// collar, where a QD suppressor seats), no longer mid-hider at z -26.2. Empty locator; nothing else in the rig moves.
export const MUZZLE_ANCHOR = [0, 11.4375, -23.98437];
source.groups.find(g => g.name === 'muzzle_anchor').origin = MUZZLE_ANCHOR.slice();
// runtime geo: the Phase 1 bones unchanged (names, parents, pivots, rotations), cubes removed, 1024 atlas
const geo = JSON.parse(gitShow(`src/main/resources/assets/apocalypse_firstlight/geo/br51_01.geo.json`));
Object.assign(geo['minecraft:geometry'][0].description, {texture_width: ATLAS, texture_height: ATLAS});
for (const b of geo['minecraft:geometry'][0].bones) delete b.cubes;
geo['minecraft:geometry'][0].bones.find(b => b.name === 'muzzle_anchor').pivot = [-MUZZLE_ANCHOR[0] || 0, MUZZLE_ANCHOR[1], MUZZLE_ANCHOR[2]];
const sidecar = convert(source, geo, {}, 'br51_01.bbmodel', 2);
const meshText = serializeCompact(sidecar);
assert(meshText.length < 4 * 1024 * 1024, 'sidecar exceeds runtime 4 MiB limit');
assert(JSON.stringify(JSON.parse(meshText)) === JSON.stringify(sidecar), 'compact sidecar changed data');

const root = ROOT, bb = path.join(root, 'src/main/blockbench'), assets = path.join(root, 'src/main/resources/assets/apocalypse_firstlight');
const OUT = {src: path.join(bb, 'br51_01.bbmodel'), geo: path.join(assets, 'geo/br51_01.geo.json'), mesh: path.join(assets, 'meshes/br51_01.aflmesh.json'),
  maps: ['', '_s', '_n'].map(k => [path.join(bb, `textures/br51_01${k}.png`), path.join(assets, `textures/item/br51_01${k}.png`)])};
const outputs = [[OUT.src, JSON.stringify(source)], [OUT.geo, JSON.stringify(geo, null, 2)], [OUT.mesh, meshText],
  ...OUT.maps.flatMap(([s, r], k) => [[s, PNG[k]], [r, PNG[k]]])];

// counts: whole sidecar, and the idle state (one magazine: reload / dropped copies are zero-scaled outside reloads)
const COPIES = new Set(PARTS.filter(p => p.copyOf).map(p => p.name));
const count = parts => { const f = parts.flatMap(p => p.faces); return {triangleEquivalent: f.reduce((s, q) => s + q.length - 2, 0), faces: f.length,
  quadFaces: f.filter(q => q.length === 4).length, triangleFaces: f.filter(q => q.length === 3).length, vertexSubmissions: f.length * 4}; };
export const stats = {all: count(sidecar.parts), idle: count(sidecar.parts.filter(p => !COPIES.has(p.name))), parts: sidecar.parts.length,
  texelsPerUnit: S, islands: islands.length,
  byPart: Object.fromEntries(sidecar.parts.filter(p => !COPIES.has(p.name)).map(p => [p.name, p.faces.reduce((s, q) => s + q.length - 2, 0)]))};
const isMain = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  console.log(JSON.stringify(stats));
  if (process.argv.includes('--debug')) fs.writeFileSync(process.argv[process.argv.indexOf('--debug') + 1], JSON.stringify(source));
  else if (process.argv.includes('--check')) {
    for (const [file, data] of outputs) { const cur = fs.existsSync(file) ? fs.readFileSync(file) : null; if (!cur || !cur.equals(Buffer.isBuffer(data) ? data : Buffer.from(data))) throw new Error('stale ' + path.relative(root, file)); }
    console.log('CHECK OK');
  } else {
    for (const [file, data] of outputs) fs.writeFileSync(file, data);
    console.log('wrote ' + outputs.map(([f]) => path.relative(root, f)).join(', '));
  }
}
