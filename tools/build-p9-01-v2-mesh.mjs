// P9-01 V2 Pure Mesh generator.
// Builds the new pistol inside the frozen P9 rig cage (bind-pose Blockbench coordinates, muzzle toward -Z,
// ejection side +X) and writes Blockbench mesh elements keyed by the existing bone they replace, plus a packed
// 1024 UV atlas, the Base Color texture (P9_DIAG=1: flat identification colours instead) and the LabPBR _s / _n maps
// painted in the same raster pass (same UVs by construction).
//   node tools/build-p9-01-v2-mesh.mjs <out.json> <base.rgba> [<spec_s.rgba> <normal_n.rgba>]
// Design language: modern striker-fired duty pistol (squared slide with top chamfers, railed dust cover,
// square-front guard with undercut, high beavertail, modular grip). Original geometry, no brand features.
import fs from 'node:fs';

const D = Math.PI / 180;
const add = (a, b) => a.map((v, i) => v + b[i]), sub = (a, b) => a.map((v, i) => v - b[i]), mul = (a, k) => a.map(v => v * k);
const dot = (a, b) => a.reduce((s, v, i) => s + v * b[i], 0);
const cross = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
const norm = a => { const l = Math.hypot(...a) || 1; return a.map(v => v / l); };
// Rotation about X by deg around pivot (Blockbench bone convention for a pure X rotation).
const rotX = (p, deg, o) => { const c = Math.cos(deg * D), s = Math.sin(deg * D), y = p[1] - o[1], z = p[2] - o[2]; return [p[0], o[1] + y * c - z * s, o[2] + y * s + z * c]; };

// ---------- rig cage (frozen, from p9_01.bbmodel) ----------
const MAG_PIVOT = [-0.0037, 3.5992, 1.647], MAG_TILT = -22;   // group "2"/"1" transform
const AXIS_Y = 5.3752, MUZZLE_Z = -7.233;                       // muzzle_anchor
const SLIDE = {w: 0.86, yb: 4.80, yt: 5.95, zf: -7.11, zr: 3.58, portF: -1.62, portR: 0.05};

// ---------- mesh builder ----------
class Part {
  constructor(name, bone, kind) { this.name = name; this.bone = bone; this.kind = kind; this.v = []; this.f = []; this.t = []; this.tag = null; this.local = null; }
  vtx(p) { this.v.push(p); return this.v.length - 1; }
  face(...ids) { this.f.push(ids); this.t.push(this.tag); }   // t: per-face paint tag (e.g. 'panel' = textured grip panel)
  // polygon (list of vertex ids) -> triangles/quads; ear clipping in the polygon plane
  poly(ids) {
    if (ids.length === 3 || ids.length === 4 && convexQuad(ids.map(i => this.v[i]))) { this.face(...ids); return; }
    const pts = ids.map(i => this.v[i]); let n = [0, 0, 0];   // Newell normal: always matches the polygon's own winding
    for (let i = 0; i < pts.length; i++) { const a = pts[i], b = pts[(i + 1) % pts.length]; n = add(n, [(a[1] - b[1]) * (a[2] + b[2]), (a[2] - b[2]) * (a[0] + b[0]), (a[0] - b[0]) * (a[1] + b[1])]); }
    for (const t of earClip(pts, n)) this.face(ids[t[0]], ids[t[1]], ids[t[2]]);
  }
}
function convexQuad(p) { const n = norm(cross(sub(p[1], p[0]), sub(p[2], p[0]))); for (let i = 0; i < 4; i++) { const c = cross(sub(p[(i + 1) % 4], p[i]), sub(p[(i + 2) % 4], p[(i + 1) % 4])); if (dot(c, n) <= 1e-9) return false; } return true; }
function earClip(P, n) {
  const ax = Math.abs(n[0]) > Math.abs(n[1]) ? (Math.abs(n[0]) > Math.abs(n[2]) ? 0 : 2) : (Math.abs(n[1]) > Math.abs(n[2]) ? 1 : 2);
  const [a, b] = [[1, 2], [2, 0], [0, 1]][ax], sgn = Math.sign(n[ax]) || 1;
  const q = P.map(p => [p[a], p[b]]); let idx = q.map((_, i) => i); const out = [];
  const ar = (i, j, k) => ((q[j][0] - q[i][0]) * (q[k][1] - q[i][1]) - (q[j][1] - q[i][1]) * (q[k][0] - q[i][0])) * sgn;
  const inside = (p, i, j, k) => ar(i, j, p) >= -1e-12 && ar(j, k, p) >= -1e-12 && ar(k, i, p) >= -1e-12;
  let guard = 0;
  while (idx.length > 3 && guard++ < 5000) {
    let cut = false;
    for (let t = 0; t < idx.length; t++) {
      const i = idx[(t + idx.length - 1) % idx.length], j = idx[t], k = idx[(t + 1) % idx.length];
      if (ar(i, j, k) <= 1e-12) continue;
      if (idx.some(p => p !== i && p !== j && p !== k && inside(p, i, j, k))) continue;
      out.push([i, j, k]); idx.splice(t, 1); cut = true; break;
    }
    if (!cut) break;
  }
  if (idx.length === 3) out.push(idx);
  return out;
}
// Loft closed sections (arrays of 3D points, same count, consistent winding) into quads.
function loft(part, sections, {capStart = true, capEnd = true, capN = null} = {}) {
  const ring = sections.map(s => s.map(p => part.vtx(p)));
  const n = ring[0].length;
  for (let r = 0; r + 1 < ring.length; r++) for (let i = 0; i < n; i++) { const j = (i + 1) % n; part.face(ring[r][i], ring[r][j], ring[r + 1][j], ring[r + 1][i]); }
  const axis = norm(sub(centroid(sections.at(-1)), centroid(sections[0])));
  if (capStart) part.poly(ring[0].slice().reverse(), capN ? capN[0] : mul(axis, -1));
  if (capEnd) part.poly(ring.at(-1), capN ? capN[1] : axis);
  return ring;
}
const centroid = s => mul(s.reduce((a, p) => add(a, p), [0, 0, 0]), 1 / s.length);
// Offset a 2D polygon (CCW) inward by d using mitred bisectors.
function inset2(poly, d) {
  const n = poly.length, out = [];
  for (let i = 0; i < n; i++) {
    const p = poly[i], a = poly[(i + n - 1) % n], b = poly[(i + 1) % n];
    const e1 = norm2(sub2(p, a)), e2 = norm2(sub2(b, p));
    const n1 = [-e1[1], e1[0]], n2 = [-e2[1], e2[0]];              // inward normals for CCW
    const m = norm2(add2(n1, n2)), k = d / Math.max(0.25, dot2(m, n1));
    out.push(add2(p, mul2(m, k)));
  }
  return out;
}
const sub2 = (a, b) => [a[0] - b[0], a[1] - b[1]], add2 = (a, b) => [a[0] + b[0], a[1] + b[1]], mul2 = (a, k) => [a[0] * k, a[1] * k];
const dot2 = (a, b) => a[0] * b[0] + a[1] * b[1], norm2 = a => { const l = Math.hypot(a[0], a[1]) || 1; return [a[0] / l, a[1] / l]; };
const area2 = p => p.reduce((s, q, i) => s + q[0] * p[(i + 1) % p.length][1] - p[(i + 1) % p.length][0] * q[1], 0) / 2;
const ccw = p => area2(p) < 0 ? p.slice().reverse() : p;
// Side-profile prism: profile in (z,y), extruded along X between -hw..hw with chamfer c on both silhouettes.
function prismX(part, profile, hw, c = 0.05, x0 = null) {
  const P = ccw(profile), I = c > 0 ? inset2(P, c) : P;
  const lo = x0 === null ? -hw : x0[0], hi = x0 === null ? hw : x0[1];
  const at = (poly, x) => poly.map(([z, y]) => [x, y, z]);
  const secs = c > 0 ? [at(I, lo), at(P, lo + c), at(P, hi - c), at(I, hi)] : [at(P, lo), at(P, hi)];
  return loft(part, secs, {capN: [[-1, 0, 0], [1, 0, 0]]});
}
// Sweep a 2D cross-section (u across path in the YZ plane, x) along a (z,y) polyline.
function sweepYZ(part, path, section, {caps = true} = {}) {
  const secs = path.map((p, i) => {
    const a = path[Math.max(0, i - 1)], b = path[Math.min(path.length - 1, i + 1)];
    const t = norm2(sub2(b, a)), nrm = [-t[1], t[0]];
    let s = 1; if (i > 0 && i < path.length - 1) { const t1 = norm2(sub2(p, a)), t2 = norm2(sub2(b, p)); s = 1 / Math.max(0.5, dot2([-t1[1], t1[0]], nrm)); }
    return section.map(([u, x]) => { const q = add2(p, mul2(nrm, u * s)); return [x, q[1], q[0]]; });
  });
  return loft(part, secs, {capStart: caps, capEnd: caps});
}
const rect = (hu, hx, c) => [[-hu + c, -hx], [hu - c, -hx], [hu, -hx + c], [hu, hx - c], [hu - c, hx], [-hu + c, hx], [-hu, hx - c], [-hu, -hx + c]];
// Circle / rounded shapes in the XY plane at depth z.
const circle = (cx, cy, r, n, z, phase = Math.PI / n) => Array.from({length: n}, (_, i) => { const a = phase + 2 * Math.PI * i / n; return [cx + r * Math.cos(a), cy + r * Math.sin(a), z]; });

const parts = [];
const WELL_LINES = [];
const P = (name, bone, kind) => { const p = new Part(name, bone, kind); parts.push(p); return p; };

// ---------- refinement helpers (bevel language) ----------
const cross2 = (a, b) => a[0] * b[1] - a[1] * b[0];
// Fillet every corner of a 2D polygon with an arc of radius radii[i] (k segments). Corners whose reference polygon
// is straight, or whose radius is 0, keep a single point, so offsets of one reference keep equal vertex counts.
function roundPoly(poly, radii, k, ref = poly) {
  const n = poly.length, out = [];
  for (let i = 0; i < n; i++) {
    const p = poly[i], a = poly[(i + n - 1) % n], b = poly[(i + 1) % n];
    const e1 = sub2(ref[i], ref[(i + n - 1) % n]), e2 = sub2(ref[(i + 1) % n], ref[i]);
    const turn = Math.abs(Math.atan2(cross2(e1, e2), dot2(e1, e2)));
    if (!radii[i] || turn < 0.03) { out.push(p); continue; }
    const d1 = norm2(sub2(a, p)), d2 = norm2(sub2(b, p));
    const half = Math.acos(Math.max(-1, Math.min(1, dot2(d1, d2)))) / 2;
    let rr = radii[i], t = rr / Math.tan(half);
    const lim = 0.48 * Math.min(Math.hypot(...sub2(a, p)), Math.hypot(...sub2(b, p)));
    if (t > lim) { t = lim; rr = t * Math.tan(half); }
    const t1 = add2(p, mul2(d1, t)), t2 = add2(p, mul2(d2, t)), c = add2(p, mul2(norm2(add2(d1, d2)), rr / Math.sin(half)));
    const a1 = Math.atan2(t1[1] - c[1], t1[0] - c[0]); let da = Math.atan2(t2[1] - c[1], t2[0] - c[0]) - a1;
    while (da > Math.PI) da -= 2 * Math.PI; while (da < -Math.PI) da += 2 * Math.PI;
    for (let s = 0; s <= k; s++) { const g = a1 + da * s / k; out.push([c[0] + rr * Math.cos(g), c[1] + rr * Math.sin(g)]); }
  }
  return out;
}
// Inset a sharp polygon by d, then fillet: convex radii shrink by d, concave grow by d, so every offset of the same
// sharp polygon is a parallel rounded outline with the same vertex count (used for rolled bevels).
function offRound(sharp, d, fil, k = 2) {
  let P0 = sharp, R = Array.isArray(fil) ? fil : sharp.map(() => fil);
  if (area2(P0) < 0) { P0 = P0.slice().reverse(); R = R.slice().reverse(); }
  const n = P0.length, I = d ? inset2(P0, d) : P0;
  const conv = P0.map((p, i) => cross2(sub2(p, P0[(i + n - 1) % n]), sub2(P0[(i + 1) % n], p)) > 0);
  return roundPoly(I, R.map((r, i) => r ? Math.max(0.004, conv[i] ? r - d : r + d) : 0), k, P0);
}
// Side-profile prism with filleted silhouette and rolled (quarter-round) bevels on the X ends.
// holesLo / holesHi: (z, y) outlines cut as closed pockets into the X end caps (options `pocketOpt`, see pocket()).
function prismXR(part, profile, lo, hi, {bev = 0.03, k = 2, fil = 0.03, fk = 2, bevLo = true, bevHi = true, holesLo = [], holesHi = [], pocketOpt} = {}) {
  const at = (poly, x) => poly.map(([z, y]) => [x, y, z]), ring = d => offRound(profile, d, fil, fk);
  const arc = s => { const ph = Math.PI / 2 * s / k; return [bev * (1 - Math.sin(ph)), bev * (1 - Math.cos(ph))]; };
  const secs = [];
  if (bev && bevLo) for (let s = 0; s <= k; s++) { const [d, o] = arc(s); secs.push(at(ring(d), lo + o)); } else secs.push(at(ring(0), lo));
  if (bev && bevHi) for (let s = k; s >= 0; s--) { const [d, o] = arc(s); secs.push(at(ring(d), hi - o)); } else secs.push(at(ring(0), hi));
  const r = loft(part, secs, {capStart: !holesLo.length, capEnd: !holesHi.length});
  if (holesLo.length) polyHoles(part, r[0], holesLo.map(o => pocket(part, o, (z, y, d) => [lo + d, y, z], pocketOpt)));
  if (holesHi.length) polyHoles(part, r.at(-1), holesHi.map(o => pocket(part, o, (z, y, d) => [hi - d, y, z], pocketOpt)));
  return r;
}
// Cast rays from c at the given angles onto a star-shaped polygon (exact points on its outline).
function rayPoly(poly, c, angs) {
  return angs.map(a => {
    const dir = [Math.cos(a), Math.sin(a)]; let best = Infinity;
    for (let k = 0; k < poly.length; k++) {
      const p = poly[k], q = poly[(k + 1) % poly.length], e = sub2(q, p), den = dir[0] * e[1] - dir[1] * e[0];
      if (Math.abs(den) < 1e-12) continue;
      const w = sub2(p, c), t = (w[0] * e[1] - w[1] * e[0]) / den, u = (w[0] * dir[1] - w[1] * dir[0]) / den;
      if (t > 0 && u >= -1e-9 && u <= 1 + 1e-9 && t < best) best = t;
    }
    return add2(c, mul2(dir, best));
  });
}
// Angle set = uniform n plus the corner angles of a reference outline (keeps fillets crisp under ray sampling).
// Samples closer than ~2 deg are merged (near-duplicate angles made sub-pixel sliver faces = texture noise); `must`
// angles always survive. Built on the +X half and mirrored, so both sides of a symmetric section sample identically.
function angleSet(n, ref, c, must = []) {
  const wrap = a => Math.atan2(Math.sin(a), Math.cos(a)), H = Math.PI / 2 + 1e-9;
  const cand = [...Array.from({length: n}, (_, i) => Math.PI / n + 2 * Math.PI * i / n), ...(ref || []).map(p => Math.atan2(p[1] - c[1], p[0] - c[0]))]
    .map(wrap).filter(a => Math.abs(a) <= H).sort((a, b) => a - b);
  const keep = must.map(wrap).filter(a => Math.abs(a) <= H);
  for (const a of cand) if (keep.every(b => Math.abs(a - b) > 0.035) && !(Math.abs(Math.PI / 2 - Math.abs(a)) < 0.0175 && Math.abs(Math.PI / 2 - Math.abs(a)) > 1e-9)) keep.push(a);
  const all = [...keep, ...keep.map(a => Math.PI - a)].map(a => ((a % (2 * Math.PI)) + 2 * Math.PI) % (2 * Math.PI)).sort((a, b) => a - b);
  return all.filter((a, i) => i === 0 || a - all[i - 1] > 1e-9);
}
const circleAt = (r, z, angs, cy = AXIS_Y) => angs.map(a => [r * Math.cos(a), cy + r * Math.sin(a), z]);
// Uniform arc-length resampling of a closed 3D polyline, optionally starting at the point nearest `start`.
function resampleClosed(pts, N, start = null) {
  let P = pts;
  if (start) {
    let bi = 0, bt = 0, bd = Infinity;
    for (let i = 0; i < P.length; i++) { const a = P[i], b = P[(i + 1) % P.length], e = sub(b, a), L2 = dot(e, e) || 1;
      const t = Math.max(0, Math.min(1, dot(sub(start, a), e) / L2)), d = Math.hypot(...sub(add(a, mul(e, t)), start)); if (d < bd) { bd = d; bi = i; bt = t; } }
    const s0 = add(P[bi], mul(sub(P[(bi + 1) % P.length], P[bi]), bt));
    P = [s0, ...P.slice(bi + 1), ...P.slice(0, bi + 1)];
  }
  const L = [0]; for (let i = 0; i < P.length; i++) L.push(L[i] + Math.hypot(...sub(P[(i + 1) % P.length], P[i])));
  const out = []; let j = 0;
  for (let k = 0; k < N; k++) { const s = L.at(-1) * k / N; while (L[j + 1] < s) j++; const t = (s - L[j]) / ((L[j + 1] - L[j]) || 1);
    out.push(add(P[j], mul(sub(P[(j + 1) % P.length], P[j]), t))); }
  return out;
}
// Chaikin corner cutting with fixed end points.
function chaikin(path, it = 2) {
  let p = path;
  for (let n = 0; n < it; n++) { const q = [p[0]]; for (let i = 0; i + 1 < p.length; i++) { const a = p[i], b = p[i + 1]; if (i > 0) q.push(add2(mul2(a, 0.75), mul2(b, 0.25))); if (i + 2 < p.length) q.push(add2(mul2(a, 0.25), mul2(b, 0.75))); } q.push(p.at(-1)); p = q; }
  return p;
}
// Cylinder along an arbitrary axis from a list of [offset, radius] stations (chamfered pins and heads).
function lathe(part, p0, axis, stations, n, {capStart = true, capEnd = true} = {}) {
  const ax = norm(axis), u = norm(cross(ax, Math.abs(ax[1]) < 0.9 ? [0, 1, 0] : [1, 0, 0])), v = cross(ax, u);
  loft(part, stations.map(([o, r]) => Array.from({length: n}, (_, i) => { const a = 2 * Math.PI * i / n; return add(add(p0, mul(ax, o)), add(mul(u, r * Math.cos(a)), mul(v, r * Math.sin(a)))); })), {capStart, capEnd});
}
const rectS = (x0, x1, y0, y1) => [[x0, y0], [x1, y0], [x1, y1], [x0, y1]];
// Replace the polygon vertex nearest q with a list of points (used to cut ports / rails into rounded sections).
function replaceAt(poly, q, pts) { let bi = 0; poly.forEach((p, i) => { if (Math.hypot(...sub2(p, q)) < Math.hypot(...sub2(poly[bi], q))) bi = i; }); return [...poly.slice(0, bi), ...pts, ...poly.slice(bi + 1)]; }
// Planar face with holes (outer loop + hole loops of vertex ids, any winding): every hole is bridged into the outer
// loop through its shortest visible diagonal, then the merged loop is ear-clipped. Only loop vertices are used, so the
// face meets its neighbours without T-junctions. Throws if the triangulated area does not match (catches bad input).
function polyHoles(part, outer, holes) {
  let n = [0, 0, 0];
  for (let i = 0; i < outer.length; i++) { const a = part.v[outer[i]], b = part.v[outer[(i + 1) % outer.length]]; n = add(n, [(a[1] - b[1]) * (a[2] + b[2]), (a[2] - b[2]) * (a[0] + b[0]), (a[0] - b[0]) * (a[1] + b[1])]); }
  const ax = Math.abs(n[0]) > Math.abs(n[1]) ? (Math.abs(n[0]) > Math.abs(n[2]) ? 0 : 2) : (Math.abs(n[1]) > Math.abs(n[2]) ? 1 : 2);
  const [ia, ib] = [[1, 2], [2, 0], [0, 1]][ax], q = i => [part.v[i][ia], part.v[i][ib]];
  const ar = L => area2(L.map(q));
  const tri = (a, b, c) => (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]);
  const inTri = (p, a, b, c) => tri(a, b, p) >= -1e-12 && tri(b, c, p) >= -1e-12 && tri(c, a, p) >= -1e-12;
  const same = (a, b) => Math.abs(a[0] - b[0]) < 1e-9 && Math.abs(a[1] - b[1]) < 1e-9;
  const onSeg = (p, a, b) => Math.abs(tri(a, b, p)) < 1e-12 && dot2(sub2(p, a), sub2(p, b)) < -1e-18;
  const cross = (a, b, c, d) => { const d1 = tri(c, d, a), d2 = tri(c, d, b), d3 = tri(a, b, c), d4 = tri(a, b, d); return (d1 > 1e-12 && d2 < -1e-12 || d1 < -1e-12 && d2 > 1e-12) && (d3 > 1e-12 && d4 < -1e-12 || d3 < -1e-12 && d4 > 1e-12); };
  const inside = (p, L) => { let c = false; for (let i = 0; i < L.length; i++) { const a = q(L[i]), b = q(L[(i + 1) % L.length]); if ((a[1] > p[1]) !== (b[1] > p[1]) && p[0] < a[0] + (p[1] - a[1]) * (b[0] - a[0]) / (b[1] - a[1])) c = !c; } return c; };
  let poly = ar(outer) > 0 ? outer.slice() : outer.slice().reverse();
  const HS = holes.map(h => ar(h) < 0 ? h.slice() : h.slice().reverse());
  const pending = HS.slice();
  while (pending.length) {
    let best = null;
    for (const h of pending) for (let m = 0; m < h.length; m++) for (let k = 0; k < poly.length; k++) {
      const M = q(h[m]), V = q(poly[k]), d = Math.hypot(...sub2(V, M));
      if (best && d >= best.d) continue;
      const loops = [poly, ...pending]; let ok = true;
      for (const L of loops) { for (let e = 0; e < L.length && ok; e++) { const a = q(L[e]), b = q(L[(e + 1) % L.length]);
        if (cross(M, V, a, b) || (!same(a, M) && !same(a, V) && onSeg(a, M, V))) ok = false; } if (!ok) break; }
      if (!ok) continue;
      const mid = mul2(add2(M, V), 0.5);
      if (!inside(mid, poly) || pending.some(L => inside(mid, L))) continue;
      best = {h, m, k, d};
    }
    if (!best) throw new Error(part.name + ': no visible bridge for a hole');
    const {h, m, k} = best;
    poly = [...poly.slice(0, k + 1), ...h.slice(m), ...h.slice(0, m), h[m], poly[k], ...poly.slice(k + 1)];
    pending.splice(pending.indexOf(h), 1);
  }
  const P2 = poly.map(q);
  let idx = poly.map((_, i) => i), made = 0, guard = 0;
  while (idx.length > 3 && guard++ < 100000) {
    let cut = false;
    for (let t = 0; t < idx.length; t++) {
      const i = idx[(t + idx.length - 1) % idx.length], j = idx[t], k = idx[(t + 1) % idx.length];
      if (tri(P2[i], P2[j], P2[k]) <= 1e-12) continue;
      if (idx.some(p => p !== i && p !== j && p !== k && !same(P2[p], P2[i]) && !same(P2[p], P2[j]) && !same(P2[p], P2[k]) && inTri(P2[p], P2[i], P2[j], P2[k]))) continue;
      part.face(poly[i], poly[j], poly[k]); made += tri(P2[i], P2[j], P2[k]) / 2; idx.splice(t, 1); cut = true; break;
    }
    if (!cut) { if (process.env.P9_DEBUG) console.error(JSON.stringify(idx.map(i => P2[i].map(v => +v.toFixed(4))))); break; }
  }
  if (idx.length === 3 && tri(P2[idx[0]], P2[idx[1]], P2[idx[2]]) > 1e-12) { part.face(...idx.map(i => poly[i])); made += tri(P2[idx[0]], P2[idx[1]], P2[idx[2]]) / 2; }
  const want = Math.abs(ar(outer)) - HS.reduce((s, h) => s + Math.abs(ar(h)), 0);
  if (Math.abs(made - want) > 1e-6 * Math.max(1, want)) throw new Error(`${part.name}: holed face area ${made} != ${want}`);
}
// Closed pocket cut into a planar face: rolled-off mouth chamfer c, straight walls with a slight draft, flat floor at
// depth D. outline: 2D polygon in the face coordinates; at(u, v, depth) maps to 3D. Returns the mouth loop ids.
function pocket(part, outline, at, {c = 0.012, D = 0.04, draft = 0.005} = {}) {
  const O = ccw(outline), mouth = inset2(O, -c), floor = inset2(O, draft), prev = part.tag;
  part.tag = 'pocket';   // painter: recess faces carry the edge shading themselves; the surrounding face stays clean
  const r = loft(part, [mouth.map(([u, v]) => at(u, v, 0)), O.map(([u, v]) => at(u, v, c)), floor.map(([u, v]) => at(u, v, D))], {capStart: false, capEnd: true});
  part.tag = prev; return r[0];
}
// Slanted closed serration groove (parallelogram): bottom centre zb at y0, top centre zt at y1, width W along Z.
const grooveOutline = (zb, zt, y0, y1, W) => [[zb - W / 2, y0], [zb + W / 2, y0], [zt + W / 2, y1], [zt - W / 2, y1]];
// Replace the flat flank strip (x = X) of one loft band (rings A -> B) with a holed face and cut the given pockets.
function flankPockets(part, A, B, X, outlines, opt) {
  const onF = i => Math.abs(part.v[i][0] - X) < 1e-6;
  const a = A.filter(onF).sort((i, j) => part.v[i][1] - part.v[j][1]), b = B.filter(onF).sort((i, j) => part.v[j][1] - part.v[i][1]);
  if (a.length < 2 || b.length < 2) throw new Error(part.name + ': flank strip not found');
  const S = new Set([...a, ...b]), before = part.f.length;
  const keep = part.f.map(f => !(f.length === 4 && f.every(i => S.has(i))));
  part.f = part.f.filter((_, i) => keep[i]); part.t = part.t.filter((_, i) => keep[i]);
  if (before - part.f.length !== a.length - 1) throw new Error(part.name + ': unexpected flank faces');
  const side = Math.sign(X);
  const holes = outlines.map(o => pocket(part, o, (z, y, d) => [X - side * d, y, z], opt));
  polyHoles(part, [...a, ...b], holes);
}
// ================= SLIDE (bone slide2) =================
// Bevel language: primary rolled bevels 0.08-0.10 (slide nose / rear), secondary 0.02-0.03 (frame, controls, sights),
// fillets 0.02-0.06 on silhouettes; large flats stay single faces.
{
  const {w, yb, yt, zf, zr, portF, portR} = SLIDE;
  // Layered duty-pistol section: flat top, narrow secondary facet, main top chamfer, vertical flank, lower break.
  const secSharp = [[-w + 0.07, yb], [w - 0.07, yb], [w, yb + 0.07], [w, 5.54], [0.66, 5.86], [0.55, yt], [-0.55, yt], [-0.66, 5.86], [-w, 5.54], [-w, yb + 0.07]];
  const secR = [0.02, 0.02, 0.02, 0.035, 0.03, 0.025, 0.025, 0.03, 0.035, 0.02];
  const sec = d => offRound(secSharp, d, secR, 2), S0 = sec(0), C = [0, AXIS_Y];
  const at = (poly, z) => poly.map(([x, y]) => [x, y, z]);
  // front bevel: ahead of ZS the lower break grows into a large chamfer (0.12 x 0.17), ending in a crisp step at ZS
  const ZS = -6.25, frontSharp = [[-w + 0.12, yb], [w - 0.12, yb], [w, yb + 0.17], ...secSharp.slice(3, 9), [-w, yb + 0.17]];
  const secF = d => offRound(frontSharp, d, secR, 2);
  // silhouette comes from the outline corners; uniform rays only add points on straight edges (Performance Pass: 24 -> 16)
  const angs = angleSet(16, [...S0, ...secF(0)], C, S0.filter(p => Math.abs(p[0] - w) < 1e-9).map(p => Math.atan2(p[1] - C[1], p[0])));   // exact flank ends
  const ring = (d, z) => at(rayPoly(sec(d), C, angs), z), ringF = (d, z) => at(rayPoly(secF(d), C, angs), z);
  // front segment: rolled nose bevel (r 0.10, 3 rings) + barrel opening; hole points share the ring angles
  const s = P('slide_body', 'slide2', 'slide');
  const Rn = 0.10, nose = [];
  for (let k = 0; k <= 2; k++) { const ph = Math.PI / 4 * k; nose.push(ringF(Rn * (1 - Math.sin(ph)), zf + Rn * (1 - Math.cos(ph)))); }
  const CAV = 0.60;   // hollow channel ahead of the port: exposes the chamber when the slide is locked back (travel 1.88)
  // ringF(ZS) -> ring(ZS) share Z: welding collapses the common outline, the rest is the step face
  const rings = loft(s, [...nose, ringF(0, ZS), ring(0, ZS), ring(0, portF - CAV)], {capStart: false, capEnd: true});
  const stepV = new Set([...rings[3], ...rings[4]]);   // step face: painted like a recess wall (no texture rim around it)
  s.f.forEach((f, i) => { if (f.every(v => stepV.has(v))) s.t[i] = 'step'; });
  // serrations: closed grooves cut into both flanks (chamfered mouth, depth 0.045), 4 front leaning forward, 7 rear leaning back
  const GR = {y0: 4.94, y1: 5.47, W: 0.085, opt: {c: 0.012, D: 0.045, draft: 0.006}};
  const frontGrooves = Array.from({length: 4}, (_, i) => { const zb = -5.89 + i * 0.24; return grooveOutline(zb, zb - 0.135, GR.y0, GR.y1, GR.W); });
  const rearGrooves = Array.from({length: 7}, (_, i) => { const zb = 1.715 + i * 0.24; return grooveOutline(zb, zb + 0.153, GR.y0, GR.y1, GR.W); });
  for (const side of [-1, 1]) flankPockets(s, rings[4], rings[5], side * w, frontGrooves, GR.opt);
  const hole = angs.map(a => s.vtx([0.40 * Math.cos(a), AXIS_Y + 0.40 * Math.sin(a), zf]));
  const sleeve = angs.map(a => s.vtx([0.40 * Math.cos(a), AXIS_Y + 0.40 * Math.sin(a), zf + 0.35]));
  for (let i = 0; i < angs.length; i++) { const j = (i + 1) % angs.length; s.face(rings[0][i], rings[0][j], hole[j], hole[i]); s.face(hole[i], hole[j], sleeve[j], sleeve[i]); }
  // ejection port: rails (inner lips 0.56..0.62) run along the open bottom of the port and chamber cavity
  const LIP = 0.08;
  const notch = [[-0.56, yb], [-0.56, yb + LIP], [-0.62, yb + LIP], [-0.62, 5.64], [0.62, 5.64], [0.62, yb + LIP], [0.56, yb + LIP], [0.56, yb]];
  const bi = S0.findIndex((p, i) => Math.abs(p[1] - yb) < 1e-6 && p[0] < -0.62 && Math.abs(S0[(i + 1) % S0.length][1] - yb) < 1e-6 && S0[(i + 1) % S0.length][0] > 0.62);
  const cav = [...S0.slice(0, bi + 1), ...notch, ...S0.slice(bi + 1)];
  const sc = P('slide_chamber_cavity', 'slide2', 'slide');
  loft(sc, [at(cav, portF - CAV), at(cav, portF)], {capStart: false, capEnd: false});
  // port walls: left wall + roof strip (edge-broken roof edge), right wall with chamfered top edge
  let portL = clipHalf(S0, q => -0.30 - q[0]);
  portL = replaceAt(portL, [-0.30, yb], [[-0.56, yb], [-0.56, yb + LIP], [-0.62, yb + LIP], [-0.62, 5.64], [-0.32, 5.64], [-0.30, 5.66]]);
  portL = replaceAt(portL, [-0.30, yt], [[-0.30, yt - 0.03], [-0.33, yt]]);
  let portRt = clipHalf(clipHalf(S0, q => q[0] - 0.62), q => 5.20 - q[1]);
  portRt = replaceAt(portRt, [0.62, yb], [[0.62, yb + LIP], [0.56, yb + LIP], [0.56, yb]]);
  portRt = replaceAt(portRt, [w, 5.20], [[w, 5.16], [w - 0.04, 5.20]]);
  portRt = replaceAt(portRt, [0.62, 5.20], [[0.64, 5.20], [0.62, 5.18]]);
  const sp = P('slide_port', 'slide2', 'slide');
  for (const poly of [portL, portRt]) loft(sp, [at(poly, portF), at(poly, portR)], {capStart: false, capEnd: false});
  // port front wall (faces the breech): section minus the port walls, following their edge breaks
  const pf = P('slide_port_front', 'slide2', 'slide'); pf.inward = 'none';
  const n0 = S0.length; let st = 0; while (!(S0[st][1] > 5.16 && S0[st][0] > -0.33) || (S0[(st + n0 - 1) % n0][1] > 5.16 && S0[(st + n0 - 1) % n0][0] > -0.33)) st++;
  const chain = []; for (let i = st; S0[i % n0][1] > 5.16 && S0[i % n0][0] > -0.33; i++) chain.push(S0[i % n0]);
  const front = [[w, 5.16], ...chain, [-0.33, yt], [-0.30, yt - 0.03], [-0.30, 5.66], [-0.32, 5.64], [0.62, 5.64], [0.62, 5.18], [0.64, 5.20], [w - 0.04, 5.20]];
  pf.poly(front.map(([x, y]) => pf.vtx([x, y, portF])));
  // rear segment: rolled rear bevel (r 0.08, 3 rings); its front cap (z = portR) is the breech face
  const sr = P('slide_rear', 'slide2', 'slide'), Rr = 0.08, rear = [];
  for (let k = 2; k >= 0; k--) { const ph = Math.PI / 4 * k; rear.push(at(sec(Rr * (1 - Math.sin(ph))), zr - Rr * (1 - Math.cos(ph)))); }
  const rr = loft(sr, [at(S0, portR), ...rear]);
  for (const side of [-1, 1]) flankPockets(sr, rr[0], rr[1], side * w, rearGrooves, GR.opt);
  // breech face: raised rounded plate with a firing-pin opening
  const bp = P('slide_breech_plate', 'slide2', 'chamber'), bang = angleSet(20, null, C);
  const plate = rayPoly(offRound(rectS(-0.36, 0.36, 5.02, 5.74), 0, 0.08, 3), C, bang);
  const pz = portR - 0.014, pF = plate.map(([x, y]) => bp.vtx([x, y, pz])), pB = plate.map(([x, y]) => bp.vtx([x, y, portR + 0.002]));
  const ph = circleAt(0.06, pz, bang).map(q => bp.vtx(q));
  for (let i = 0; i < bang.length; i++) { const j = (i + 1) % bang.length; bp.face(pF[i], pF[j], ph[j], ph[i]); bp.face(pB[i], pB[j], pF[j], pF[i]); }
  const bf = P('slide_firing_pin_hole', 'slide2', 'internal'); bf.inward = 'x0';
  loft(bf, [circleAt(0.06, pz, bang), circleAt(0.06, portR + 0.03, bang)], {capStart: false, capEnd: true});
  // extractor: external claw on the ejection side, running back from the port
  const ex = P('slide_extractor', 'slide2', 'ctrl');
  prismXR(ex, [[portR - 0.04, 5.30], [0.92, 5.30], [0.98, 5.40], [0.92, 5.52], [portR - 0.04, 5.52]], w - 0.01, w + 0.04, {bev: 0.015, fil: 0.03, bevLo: false, fk: 1});
  // rear striker cap plate
  const cap = P('slide_striker_cap', 'slide2', 'ctrl');
  prismXR(cap, [[zr - 0.06, 4.92], [zr + 0.03, 4.92], [zr + 0.03, 5.46], [zr - 0.06, 5.46]], -0.34, 0.34, {bev: 0.025, k: 1, fil: 0.025, fk: 1});
}

// ================= SIGHTS =================
{
  const f = P('front_sight_post', 'front_sight', 'sight');
  prismXR(f, [[-6.44, 5.90], [-5.96, 5.90], [-6.04, 6.29], [-6.34, 6.29]], -0.12, 0.12, {bev: 0.025, k: 1, fil: 0.03, fk: 1});
  const r = P('rear_sight_body', 'rear_sight', 'sight');
  const prof = [[2.72, 5.86], [3.36, 5.86], [3.36, 6.22], [2.90, 6.29], [2.76, 6.29]];
  prismXR(r, prof, -0.57, -0.11, {bev: 0.02, k: 1, fil: 0.03, fk: 1}); prismXR(r, prof, 0.11, 0.57, {bev: 0.02, k: 1, fil: 0.03, fk: 1});
  prismXR(r, [[2.74, 5.86], [3.34, 5.86], [3.34, 6.05], [2.74, 6.05]], -0.12, 0.12, {bev: 0, fil: 0.015});
}

// ================= BARREL (bone barrel4) =================
{
  const C = [0, AXIS_Y], M = MUZZLE_Z;
  const b = P('barrel_tube', 'barrel4', 'barrel'); const N = 24;   // Performance Pass: 32 -> 24 (the crown silhouette still reads round in muzzle close-ups)
  // 9 mm scale (1 unit ~ 18.8 mm): bore r 0.22, barrel r 0.35; recessed rounded crown
  const cr = (r, z) => circle(0, AXIS_Y, r, N, z);
  loft(b, [cr(0.22, M + 0.035), cr(0.26, M + 0.006), cr(0.31, M), cr(0.345, M + 0.012), cr(0.35, M + 0.04), cr(0.35, -1.60)], {capStart: false, capEnd: false});
  const bi = P('barrel_bore', 'barrel4', 'internal'); bi.inward = true;   // dark bore opening with depth; rifling left to texture / normal map
  loft(bi, [cr(0.22, M + 0.035), cr(0.22, M + 1.10)], {capStart: false, capEnd: true});
  // chamber hood / breech block: Z-lofted rounded section, rolled front bevel, rear face opened by the chamber
  // bottom at 4.98 (hidden in the slide / over the ramp block): the rear face keeps a rim around the r 0.335 chamber opening
  const hSharp = rectS(-0.47, 0.47, 4.98, 5.90), hsec = d => offRound(hSharp, d, 0.06, 2);
  const hang = angleSet(16, hsec(0), C), hr = (d, z) => rayPoly(hsec(d), C, hang).map(([x, y]) => [x, y, z]);
  const h = P('barrel_hood', 'barrel4', 'chamber'), hs = [];
  for (let k = 0; k <= 1; k++) { const a = Math.PI / 2 * k; hs.push(hr(0.07 * (1 - Math.sin(a)), -1.60 + 0.07 * (1 - Math.cos(a)))); }   // front bevel hidden in the slide: one chamfer
  for (let k = 1; k >= 0; k--) { const a = Math.PI / 2 * k; hs.push(hr(0.03 * (1 - Math.sin(a)), 0.02 - 0.03 * (1 - Math.cos(a)))); }   // rear edge: one chamfer
  const hl = loft(h, hs, {capStart: true, capEnd: false});
  const hh = circleAt(0.335, 0.02, hang).map(q => h.vtx(q));
  for (let i = 0; i < hang.length; i++) { const j = (i + 1) % hang.length; h.face(hl.at(-1)[i], hl.at(-1)[j], hh[j], hh[i]); }
  const cm = P('barrel_chamber_mouth', 'barrel4', 'chamber'); cm.inward = 'none';   // rounded chamber-mouth ring, proud of the hood face
  loft(cm, [circleAt(0.345, 0.016, hang), circleAt(0.315, 0.035, hang), circleAt(0.28, 0.033, hang)], {capStart: false, capEnd: false});
  const cb = P('barrel_chamber', 'barrel4', 'internal'); cb.inward = true;   // chamber (case r 0.265), cone down to the bore
  loft(cb, [circleAt(0.28, 0.033, hang), circleAt(0.28, -0.82, hang), circleAt(0.22, -0.95, hang)], {capStart: false, capEnd: true});
  // feed ramp: block under the chamber + curved, slightly troughed polished ramp from the chamber floor into the well
  const fr = P('barrel_feed_ramp', 'barrel4', 'ramp');
  prismXR(fr, [[-0.40, 4.62], [0.035, 4.62], [0.035, 5.02], [-0.40, 5.02]], -0.24, 0.24, {bev: 0.02, k: 1, fil: 0.02, fk: 1});
  const base = 4.62, top0 = 5.098, ys = z => base + (top0 - base) * Math.pow(1 - (z - 0.035) / 0.405, 1.5);
  const rampSec = z => { const hgt = ys(z) - base, f = Math.min(1, hgt / 0.1), y = ys(z), dp = 0.022 * f, e = 0.02 * f;
    const pts = [[0.24, base], [0.24, y - e], [0.225, y]];
    for (let i = 1; i <= 3; i++) { const x = 0.225 - 0.45 * i / 4; pts.push([x, y - dp * (1 - (x / 0.225) ** 2)]); }
    pts.push([-0.225, y], [-0.24, y - e], [-0.24, base]); return pts.map(([x, yy]) => [x, yy, z]); };
  loft(fr, Array.from({length: 5}, (_, i) => rampSec(0.035 + 0.40 * i / 4)));
}

// ================= FRAME (bone frame) =================
{
  // Receiver: outer side rails (full profile, rolled outer bevel) + centre core cut by the feed well. The well walls
  // continue the magazine axis upward (magwell inner planes offset 0.02 so they tuck behind the grip's magwell faces).
  // beavertail: long, upswept tail (tip z 4.22, y ~4.53); its top stays below the slide (y 4.80) over the full recoil stroke
  const RX = [[-6.94, 4.24], [-6.86, 4.78], [3.28, 4.78], [3.72, 4.73], [4.06, 4.68], [4.22, 4.62], [4.24, 4.52], [4.14, 4.44], [3.92, 4.38],
              [3.55, 4.27], [3.18, 4.10], [2.96, 3.98], [2.62, 3.80], [0.70, 3.80], [0.45, 4.02], [-6.72, 4.02]];
  const RXF = RX.map(([z]) => z > 4.2 ? 0.05 : 0.06), RXr = offRound(RX, 0, RXF, 1);
  const wellLine = (zLocal, off) => {
    const a = rotX([0, 3.0, zLocal], MAG_TILT, MAG_PIVOT), b = rotX([0, 5.0, zLocal], MAG_TILT, MAG_PIVOT);
    return [[a[2] + off, a[1]], [b[2] + off, b[1]]];
  };
  const keepSide = (poly, [[z0, y0], [z1, y1]], sign) => clipHalf(poly, q => sign * ((z1 - z0) * (q[1] - y0) - (y1 - y0) * (q[0] - z0)));
  const wf = wellLine(0.855, -0.02), wb = wellLine(2.695, 0.02);
  const r = P('frame_receiver', 'frame', 'frame');
  // side pockets: long dust-cover channel + support-finger index recess above the trigger guard
  const sidePockets = [roundPoly(rectS(-6.40, -3.30, 4.175, 4.245), [0.025, 0.025, 0.025, 0.025], 1), roundPoly(rectS(-1.78, -0.84, 4.12, 4.34), [0.06, 0.06, 0.06, 0.06], 1)];
  const pocketOpt = {c: 0.01, D: 0.025, draft: 0.004};
  prismXR(r, RX, -0.80, -0.45, {bev: 0.045, k: 1, fil: RXF, fk: 1, bevHi: false, holesLo: sidePockets, pocketOpt});
  prismXR(r, RX, 0.45, 0.80, {bev: 0.045, k: 1, fil: RXF, fk: 1, bevLo: false, holesHi: sidePockets, pocketOpt});
  // upper band: slide-frame interface stands 0.04 proud of the dust cover (second hard-surface layer)
  const band = clipHalf(RXr, q => q[1] - 4.40);
  prismXR(r, band, -0.84, -0.76, {bev: 0.02, k: 1, fil: 0, bevHi: false}); prismXR(r, band, 0.76, 0.84, {bev: 0.02, k: 1, fil: 0, bevLo: false});
  const rc = P('frame_receiver_core', 'frame', 'frame');
  const coreF = keepSide(RXr, wf, 1), coreB = keepSide(RXr, wb, -1);
  prismX(rc, coreF, 0, 0, [-0.45, 0.45]); prismX(rc, coreB, 0, 0, [-0.45, 0.45]);
  WELL_LINES.push(wf, wb);
  // accessory rail: rounded spine + 4 bevelled lands (slots between)
  const rail = P('frame_rail', 'frame', 'frame');
  prismXR(rail, [[-6.60, 3.88], [-3.20, 3.88], [-3.12, 4.04], [-6.66, 4.04]], -0.56, 0.56, {bev: 0.025, k: 1, fil: 0.03, fk: 1});
  for (let i = 0; i < 4; i++) { const z = -6.50 + i * 0.86; prismXR(rail, [[z, 3.72], [z + 0.52, 3.72], [z + 0.52, 3.90], [z, 3.90]], -0.62, 0.62, {bev: 0.03, k: 1, fil: 0}); }
  // trigger guard: square front, flat bottom, rising undercut; smoothed path, rounded lighter section
  const g = P('frame_trigger_guard', 'frame', 'frame');
  const path = chaikin([[-2.46, 4.06], [-2.52, 3.30], [-2.56, 2.66], [-2.40, 2.44], [-2.05, 2.40], [-0.40, 2.40], [0.05, 2.48], [0.40, 2.78], [0.72, 3.30], [0.92, 3.70]], 1);
  sweepYZ(g, path, offRound(rectS(-0.085, 0.085, -0.37, 0.37), 0, 0.07, 1));
  // grip module (magazine-aligned frame, rotated into the rig): superellipse outline, flat front strap,
  // rounder backstrap, palm swell on the flanks, flared magwell base with a rolled bottom edge
  const G = P('frame_grip', 'frame', 'grip');
  const NG = 28, GZ0 = 0.64, GZ1 = 2.98, GZC = 1.775;   // Performance Pass: 40 -> 28 columns
  const gripPt = (a, y, flare, swell = 0) => {
    const c = Math.cos(a), sn = Math.sin(a), fr = sn < 0, n = fr ? 5 : 4, hz0 = fr ? GZC - GZ0 : GZ1 - GZC;
    const z = GZC + Math.sign(sn) * (hz0 + flare) * Math.pow(Math.abs(sn), 2 / n), u = (z - GZC) / hz0;
    return [Math.sign(c) * (0.745 + flare + swell * Math.max(0, 1 - u * u)) * Math.pow(Math.abs(c), 2 / n), y, z]; };
  const wellPt = (a, y) => { const c = Math.cos(a), sn = Math.sin(a); return [Math.sign(c) * 0.615 * Math.pow(Math.abs(c), 0.2), y, 1.775 + Math.sign(sn) * 0.92 * Math.pow(Math.abs(sn), 0.2)]; };
  // 'top': lift each outline point in the magazine frame until it reaches world y = 4.40 (buried in the receiver)
  const toTop = q => { const c = Math.cos(MAG_TILT * D), sn = Math.sin(MAG_TILT * D); return [q[0], MAG_PIVOT[1] + (4.40 - MAG_PIVOT[1] + (q[2] - MAG_PIVOT[2]) * sn) / c, q[2]]; };
  // Textured side panels: both flanks between the straps, sunk 0.025 with a crisp step on all four sides; front strap,
  // backstrap, grip top and flared base stay smooth. Boundary rows / columns are duplicated (outer copy + sunk copy):
  // welding collapses the copies outside the panel, the rest become the step walls.
  const PANEL = {aF: -24.2 * D, aB: 29.5 * D, y0: -0.36, y1: 2.72, depth: 0.025};
  const TAU = 2 * Math.PI, wrapA = a => ((a % TAU) + TAU) % TAU;
  const inA = a => { const w = wrapA(a); return w > wrapA(PANEL.aF) || w < PANEL.aB || (w > Math.PI - PANEL.aB && w < Math.PI - PANEL.aF); };
  const PB = [PANEL.aF, PANEL.aB, Math.PI - PANEL.aB, Math.PI - PANEL.aF].map(wrapA), angGap = (x, y) => Math.abs(Math.atan2(Math.sin(x - y), Math.cos(x - y)));
  // uniform columns closer than ~3 deg to a panel boundary would make sliver strips next to the boundary columns
  const cols = Array.from({length: NG}, (_, i) => ({a: TAU * i / NG, in: inA(TAU * i / NG), k: 0})).filter(c => PB.every(b => angGap(c.a, b) > 0.05));
  for (const [a, enter] of [[PANEL.aF, true], [PANEL.aB, false], [Math.PI - PANEL.aB, true], [Math.PI - PANEL.aF, false]])
    cols.push({a: wrapA(a), in: !enter, k: 0}, {a: wrapA(a), in: enter, k: 1});
  cols.sort((p, q) => p.a - q.a || p.k - q.k);
  const lv = [[-0.64, 0.008], [-0.615, 0.036], [-0.46, 0.02], [0.2, 0, 0.012], [1.0, 0, 0.03], [1.8, 0, 0.03], [2.6, 0, 0.01], [2.9, 0, 0]];
  const lvAt = y => { const k = lv.findIndex((l, i) => i + 1 < lv.length && y >= l[0] && y <= lv[i + 1][0]), A = lv[k], B = lv[k + 1], t = (y - A[0]) / (B[0] - A[0]);
    return a => add(mul(gripPt(a, A[0], A[1], A[2] || 0), 1 - t), mul(gripPt(a, B[0], B[1], B[2] || 0), t)); };
  const rows = [...lv.map(([y, fl, sw]) => ({y, k: 0, pt: a => gripPt(a, y, fl, sw || 0), in: y > PANEL.y0 && y < PANEL.y1})),
    {y: PANEL.y0, k: 0, pt: lvAt(PANEL.y0), in: false}, {y: PANEL.y0, k: 1, pt: lvAt(PANEL.y0), in: true},
    {y: PANEL.y1, k: 0, pt: lvAt(PANEL.y1), in: true}, {y: PANEL.y1, k: 1, pt: lvAt(PANEL.y1), in: false}].sort((p, q) => p.y - q.y || p.k - q.k);
  rows.push({pt: a => toTop(gripPt(a, 0, 0)), in: false});
  const sink = (row, a) => { const p = row.pt(a), t = sub(row.pt(a + 1e-4), row.pt(a - 1e-4));
    let nx = t[2], nz = -t[0]; const l = Math.hypot(nx, nz); nx /= l; nz /= l; if (nx * p[0] + nz * (p[2] - GZC) < 0) { nx = -nx; nz = -nz; }
    return [p[0] - nx * PANEL.depth, p[1], p[2] - nz * PANEL.depth]; };
  const outer = rows.map(row => cols.map(col => G.vtx(rotX(row.in && col.in ? sink(row, col.a) : row.pt(col.a), MAG_TILT, MAG_PIVOT))));
  for (let k = 0; k + 1 < outer.length; k++) for (let i = 0; i < cols.length; i++) { const j = (i + 1) % cols.length;
    const sunk = [rows[k].in && cols[i].in, rows[k].in && cols[j].in, rows[k + 1].in && cols[j].in, rows[k + 1].in && cols[i].in];
    G.tag = sunk.every(Boolean) ? 'panel' : sunk.some(Boolean) ? 'step' : null;   // floor / step wall / outer surface
    G.face(outer[k][i], outer[k][j], outer[k + 1][j], outer[k + 1][i]); }
  G.tag = null;
  const well = cols.map(col => G.vtx(rotX(wellPt(col.a, -0.64), MAG_TILT, MAG_PIVOT)));
  for (let i = 0; i < cols.length; i++) { const j = (i + 1) % cols.length; G.face(outer[0][j], outer[0][i], well[i], well[j]); }
  const MW = P('frame_magwell', 'frame', 'internal'); MW.inward = 'mag';   // magwell walls face the magazine axis
  const mw = [cols.map(col => wellPt(col.a, -0.64)), cols.map(col => toTop(wellPt(col.a, 0)))].map(rg => rg.map(q => MW.vtx(rotX(q, MAG_TILT, MAG_PIVOT))));
  for (let i = 0; i < cols.length; i++) { const j = (i + 1) % cols.length; MW.face(mw[0][j], mw[0][i], mw[1][i], mw[1][j]); }
  // controls on the left (-X) side: slide stop lever with raised thumb pad, takedown lever, magazine catch
  const c = P('frame_controls', 'frame', 'ctrl');
  prismXR(c, [[-0.95, 4.42], [0.70, 4.42], [0.92, 4.50], [0.98, 4.70], [0.62, 4.66], [-0.95, 4.58]], -0.905, -0.80, {bev: 0.02, k: 1, fil: 0.035, bevHi: false, fk: 1});
  prismXR(c, [[0.60, 4.50], [0.90, 4.53], [0.945, 4.67], [0.63, 4.645]], -0.935, -0.88, {bev: 0.014, k: 1, fil: 0.03, bevHi: false, fk: 1});
  prismXR(c, [[-2.40, 4.30], [-1.92, 4.30], [-1.86, 4.46], [-2.00, 4.62], [-2.36, 4.58]], -0.89, -0.78, {bev: 0.02, k: 1, fil: 0.05, bevHi: false, fk: 1});
  prismXR(c, [[0.72, 3.70], [1.02, 3.70], [1.02, 3.96], [0.72, 3.96]], -0.88, -0.66, {bev: 0.025, k: 1, fil: 0.07, bevHi: false, fk: 1});
  for (const z of [0.68, 0.76, 0.84]) prismXR(c, [[z - 0.018, 4.55], [z + 0.018, 4.555], [z + 0.018, 4.645], [z - 0.018, 4.64]], -0.95, -0.925, {bev: 0.007, k: 1, fil: 0.008, fk: 1, bevHi: false});
  // magazine catch bezel: raised polymer ring around the button (0.02 clearance), 0.02 proud of the receiver side
  const bz = P('frame_mag_catch_bezel', 'frame', 'frame'), bO = rectS(0.64, 1.10, 3.62, 4.04), bI = rectS(0.70, 1.04, 3.68, 3.98);
  const bR = (sharp, d, x) => offRound(sharp, d, 0.07, 1).map(([z, y]) => [x, y, z]);
  loft(bz, [bR(bO, 0, -0.70), bR(bO, 0, -0.808), bR(bO, 0.012, -0.82), bR(bI, -0.012, -0.82), bR(bI, 0, -0.808), bR(bI, 0, -0.70), bR(bO, 0, -0.70)], {capStart: false, capEnd: false});
  // receiver pins with chamfered heads
  for (const [z, y] of [[-0.35, 4.28], [2.05, 4.28]]) for (const sx of [-1, 1]) lathe(c, [sx * 0.76, y, z], [sx, 0, 0], [[0, 0.07], [0.085, 0.07], [0.10, 0.055]], 8);
}

// ================= TRIGGER (bone trigger2) =================
{
  // blade hangs from the hinge; finger face (-Z) is concave and the tip curls toward the muzzle
  const t = P('trigger_blade', 'trigger2', 'ctrl');
  sweepYZ(t, chaikin([[-0.52, 4.02], [-0.50, 3.62], [-0.54, 3.24], [-0.63, 2.98], [-0.76, 2.82]], 2), offRound(rectS(-0.085, 0.085, -0.12, 0.12), 0, 0.05, 1));
}

// ================= MAGAZINE (bones magazine / empty_old_magazine; mag-local frame) =================
const NM = 16;   // Performance Pass: 24 -> 16 (4 points per rounded corner)
const magRing = (hx, z0, z1, rf, rb, y) => roundedRectXZ(hx, z0, z1, rf, rb, NM, y);
function buildMagazine(bone, suffix) {
  const loc = {origin: MAG_PIVOT, rotation: [MAG_TILT, 0, 0]};
  const m = P('magazine_body' + suffix, bone, 'mag'); m.local = loc;
  // one closed cup (every ring wider than +-0.44 stays below the receiver rails at world y 3.80): body -> rolled upper taper -> thick feed lips curling over the top round (lip underside follows
  // a 0.255 circle around the round axis) -> inner walls down past the follower
  // Performance Pass: 16 -> 11 rings; the lip curl keeps its outer shoulder, crest, inner hook and round-hugging underside
  const R = [[0.56, 0.92, 2.61, 0.13, 0.06, -0.70], [0.56, 0.92, 2.61, 0.13, 0.06, 3.24],
    [0.525, 0.945, 2.60, 0.12, 0.058, 3.37], [0.44, 0.99, 2.588, 0.10, 0.05, 3.46], [0.29, 1.05, 2.57, 0.09, 0.05, 3.52],
    [0.222, 1.09, 2.56, 0.08, 0.05, 3.51], [0.207, 1.10, 2.56, 0.08, 0.05, 3.482],
    [0.232, 1.09, 2.56, 0.08, 0.05, 3.438], [0.247, 1.08, 2.56, 0.08, 0.05, 3.396], [0.50, 0.98, 2.55, 0.10, 0.04, 3.34], [0.50, 0.98, 2.55, 0.10, 0.04, 2.80]];
  const cut = q => { const f = Math.max(0, Math.min(1, (1.12 - q[2]) / 0.20)); return q[1] > 3.26 ? [q[0], 3.26 + (q[1] - 3.26) * (1 - 0.8 * f), q[2]] : q; };
  loft(m, R.map(([hx, z0, z1, rf, rb, y]) => magRing(hx, z0, z1, rf, rb, y).map(cut)));
  // baseplate: rolled bottom edge, thick plate, bevelled top edge stepping into the body, slight front finger lip
  const bp = P('magazine_baseplate' + suffix, bone, 'mag'); bp.local = loc;
  // lower lip flares 0.16 forward / 0.16 back (front finger hook, rear heel) and steps into the plate through a small shelf
  loft(bp, [[0.70, 0.60, 2.96, 0.08, 0.08, -0.99], [0.74, 0.56, 3.00, 0.11, 0.10, -0.96],
    [0.74, 0.56, 3.00, 0.11, 0.10, -0.905], [0.74, 0.72, 2.84, 0.10, 0.10, -0.875],
    [0.74, 0.72, 2.84, 0.10, 0.10, -0.83], [0.70, 0.77, 2.80, 0.09, 0.09, -0.79],
    [0.60, 0.88, 2.65, 0.13, 0.06, -0.72]].map(([hx, z0, z1, rf, rb, y]) => magRing(hx, z0, z1, rf, rb, y)));
  // follower: rolled top edge, flat round-support top at 3.065, side clearance 0.04 to the inner walls
  const fo = P('magazine_follower' + suffix, bone, 'follower'); fo.local = loc;
  loft(fo, [[0.46, 1.02, 2.50, 0.08, 0.04, 2.84], [0.46, 1.02, 2.50, 0.08, 0.04, 2.99], [0.43, 1.05, 2.475, 0.072, 0.04, 3.05],
    [0.37, 1.11, 2.44, 0.06, 0.04, 3.065]].map(([hx, z0, z1, rf, rb, y]) => magRing(hx, z0, z1, rf, rb, y)));
}
buildMagazine('magazine', '');
buildMagazine('empty_old_magazine', '_reload');
if (process.env.P9_CHECK) {
  // top-round clearance: lip points in the round's length vs the round axis (x 0, y 3.33 in the magazine frame)
  const m = parts.find(p => p.name === 'magazine_body');
  const lip = m.v.filter(q => q[1] > 3.30 && q[2] > 1.15 && q[2] < 2.45);
  console.error('lip-to-round-axis min', Math.min(...lip.map(q => Math.hypot(q[0], q[1] - 3.33))).toFixed(4), 'mag top y', Math.max(...m.v.map(q => q[1])).toFixed(3));
}

// ---------- geometry helpers used above ----------
function roundedRectXZ(hx, z0, z1, rf, rb, n, y) {
  // rounded rectangle in the XZ plane at height y; front radius rf (z0 side), back radius rb (z1 side); CCW seen from +Y
  const pts = [];
  const corners = [[hx - rf, z0 + rf, rf, -90], [hx - rb, z1 - rb, rb, 0], [-hx + rb, z1 - rb, rb, 90], [-hx + rf, z0 + rf, rf, 180]];
  const per = n / 4;
  for (const [cx, cz, r, a0] of corners) for (let i = 0; i < per; i++) { const a = (a0 + 90 * i / (per - 1)) * D; pts.push([cx + r * Math.cos(a), y, cz + r * Math.sin(a)]); }
  return pts.reverse();
}
function clipHalf(poly, f) {
  // Sutherland-Hodgman: keep the part of a 2D polygon where f(p) >= 0
  const out = [];
  for (let i = 0; i < poly.length; i++) {
    const a = poly[i], b = poly[(i + 1) % poly.length], fa = f(a), fb = f(b);
    if (fa >= 0) out.push(a);
    if ((fa >= 0) !== (fb >= 0)) { const t = fa / (fa - fb); out.push([a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t]); }
  }
  return out;
}
function resamplePoly(poly, n, c) {
  // sample polygon outline at n equal angles around centre c (polygon must be star-shaped around c)
  const out = [];
  for (let i = 0; i < n; i++) {
    const a = Math.PI / n + 2 * Math.PI * i / n, dir = [Math.cos(a), Math.sin(a)]; let best = null;
    for (let k = 0; k < poly.length; k++) {
      const p = poly[k], q = poly[(k + 1) % poly.length], e = sub2(q, p), den = dir[0] * e[1] - dir[1] * e[0];
      if (Math.abs(den) < 1e-12) continue;
      const w = sub2(p, c), t = (w[0] * e[1] - w[1] * e[0]) / den, u = (w[0] * dir[1] - w[1] * dir[0]) / den;
      if (t > 0 && u >= -1e-9 && u <= 1 + 1e-9 && (!best || t < best)) best = t;
    }
    out.push(add2(c, mul2(dir, best)));
  }
  return out;
}
function bridgeOutline(part, a, b) {
  // a and b trace the same planar outline (different sampling). Weld b onto a's edges by fanning:
  // for every b vertex, connect it to the nearest a edge endpoint — produces thin triangles along the outline.
  const A = a.map(i => part.v[i]), B = b.map(i => part.v[i]);
  let j = 0; const near = p => A.reduce((m, q, k) => Math.hypot(...sub(q, p)) < Math.hypot(...sub(A[m], p)) ? k : m, 0);
  for (let i = 0; i < B.length; i++) {
    const k0 = near(B[i]), k1 = near(B[(i + 1) % B.length]);
    if (k0 !== k1) { let k = k0; while (k !== k1) { const kn = (k + 1) % A.length; part.face(b[(i + 1) % B.length], b[i], a[k], a[kn]) ; k = kn; if (k === k1) break; } }
  }
}
function loftCyl(part, p0, p1, r, n) {
  const ax = norm(sub(p1, p0)), u = norm(cross(ax, Math.abs(ax[1]) < 0.9 ? [0, 1, 0] : [1, 0, 0])), v = cross(ax, u);
  const ring = p => Array.from({length: n}, (_, i) => { const a = 2 * Math.PI * i / n; return add(p, add(mul(u, r * Math.cos(a)), mul(v, r * Math.sin(a)))); });
  loft(part, [ring(p0), ring(p1)]);
}

// ================= cleanup: weld, drop degenerate faces, fix winding outward =================
for (const p of parts) {
  // weld identical vertices
  const key = q => q.map(x => x.toFixed(5)).join(','), map = new Map(), nv = [], remap = [];
  p.v.forEach((q, i) => { const k = key(q); if (!map.has(k)) { map.set(k, nv.length); nv.push(q); } remap[i] = map.get(k); });
  const ft = p.f.map((f, k) => ({f: f.map(i => remap[i]).filter((x, i, a) => a.indexOf(x) === i), t: p.t[k]}))
    .filter(o => o.f.length >= 3 && Math.hypot(...faceNormalRaw(nv, o.f)) > 1e-9);
  p.f = ft.map(o => o.f); p.t = ft.map(o => o.t); p.v = nv;
}
function faceNormalRaw(V, f) { let n = [0, 0, 0]; for (let i = 1; i + 1 < f.length; i++) n = add(n, cross(sub(V[f[i]], V[f[0]]), sub(V[f[i + 1]], V[f[0]]))); return n; }
// Orient faces: make winding consistent across shared edges per connected component, then flip the component
// so the area-weighted majority of faces points away from the component centroid (outward).
for (const p of parts) {
  const F = p.f, edges = new Map();
  F.forEach((f, i) => f.forEach((a, k) => { const b = f[(k + 1) % f.length], e = a < b ? a + ',' + b : b + ',' + a; (edges.get(e) || edges.set(e, []).get(e)).push(i); }));
  const dir = (f, a, b) => { const k = f.indexOf(a); return f[(k + 1) % f.length] === b; };
  const comp = new Int32Array(F.length).fill(-1); let nc = 0;
  for (let s = 0; s < F.length; s++) {
    if (comp[s] >= 0) continue; comp[s] = nc; const q = [s];
    while (q.length) { const i = q.pop(), f = F[i];
      for (let k = 0; k < f.length; k++) { const a = f[k], b = f[(k + 1) % f.length], e = a < b ? a + ',' + b : b + ',' + a;
        for (const j of edges.get(e)) { if (j === i || comp[j] >= 0) continue; if (dir(F[j], a, b)) F[j].reverse(); comp[j] = nc; q.push(j); } } }
    nc++;
  }
  for (let c = 0; c < nc; c++) {
    const ids = [...F.keys()].filter(i => comp[i] === c); let cen = [0, 0, 0], A = 0;
    for (const i of ids) { const n = faceNormalRaw(p.v, F[i]), a = Math.hypot(...n); cen = add(cen, mul(centroid(F[i].map(k => p.v[k])), a)); A += a; }
    cen = mul(cen, 1 / A); let vote = 0;
    for (const i of ids) { const fc = centroid(F[i].map(k => p.v[k]));
      // inward parts (bore): judge against the barrel axis line instead of the centroid
      let ref = sub(fc, cen);
      if (p.inward === true) ref = [fc[0], fc[1] - AXIS_Y, 0];
      else if (p.inward === 'mag') { const ax = [0, Math.cos(MAG_TILT * D), Math.sin(MAG_TILT * D)], rv = sub(fc, MAG_PIVOT); ref = sub(rv, mul(ax, dot(rv, ax))); }
      else if (p.inward === 'x0') ref = [fc[0], fc[1] - AXIS_Y, 0];
      else if (p.inward === 'none') ref = [0, 0, 1];
      vote += dot(faceNormalRaw(p.v, F[i]), ref); }
    const wantIn = p.inward === true || p.inward === 'mag' || p.inward === 'x0';
    if ((vote < 0) !== wantIn) for (const i of ids) F[i].reverse();
  }
}

// ================= UV unwrap + pack =================
const ATLAS = 1024, PAD = 3;
const islands = [];
for (const p of parts) {
  const F = p.f.map(f => ({f, n: norm(faceNormalRaw(p.v, f))}));
  const edge = new Map(); F.forEach((o, i) => o.f.forEach((a, k) => { const b = o.f[(k + 1) % o.f.length]; const e = a < b ? a + ',' + b : b + ',' + a; (edge.get(e) || edge.set(e, []).get(e)).push(i); }));
  const seen = new Uint8Array(F.length);
  for (let s = 0; s < F.length; s++) {
    if (seen[s]) continue; const q = [s], mem = []; seen[s] = 1; const n0 = F[s].n;
    while (q.length) { const i = q.pop(); mem.push(i); const f = F[i].f;
      for (let k = 0; k < f.length; k++) { const a = f[k], b = f[(k + 1) % f.length], e = a < b ? a + ',' + b : b + ',' + a;
        for (const j of edge.get(e)) if (!seen[j] && dot(F[j].n, n0) > 0.94 && dot(F[j].n, F[i].n) > 0.97) { seen[j] = 1; q.push(j); } } }
    const n = norm(mem.reduce((acc, i) => add(acc, F[i].n), [0, 0, 0]));
    // tangent follows the gun's long axis when possible, else X
    let t = sub([0, 0, 1], mul(n, n[2])); if (Math.hypot(...t) < 0.3) t = sub([1, 0, 0], mul(n, n[0])); t = norm(t); const bt = cross(n, t);
    const verts = [...new Set(mem.flatMap(i => F[i].f))], uv = new Map();
    for (const vi of verts) uv.set(vi, [dot(p.v[vi], t), -dot(p.v[vi], bt)]);
    const us = [...uv.values()], u0 = Math.min(...us.map(a => a[0])), v0 = Math.min(...us.map(a => a[1]));
    const w = Math.max(...us.map(a => a[0])) - u0, h = Math.max(...us.map(a => a[1])) - v0;
    islands.push({part: p, faces: mem.map(i => F[i].f), uv, u0, v0, w, h, t, bt});   // t / bt: +u / +v-up directions (normal map frame)
  }
}
function pack(scale) {
  const order = islands.slice().sort((a, b) => b.h - a.h);
  let x = PAD, y = PAD, rowH = 0;
  for (const is of order) {
    const W = Math.ceil(is.w * scale) + 1, H = Math.ceil(is.h * scale) + 1;
    if (x + W + PAD > ATLAS) { x = PAD; y += rowH + PAD; rowH = 0; }
    if (y + H + PAD > ATLAS) return false;
    is.px = x; is.py = y; x += W + PAD; rowH = Math.max(rowH, H);
  }
  return true;
}
let scale = 80; while (!pack(scale)) scale -= 1;

// ================= output =================
const UVS = 1;   // Blockbench normalises mesh UVs by the texture pixel size (1024), not the project resolution
let uid = 0; const key4 = () => (uid++).toString(36).padStart(4, '0');
const elements = parts.map(p => {
  const origin = p.local ? p.local.origin : [0, 0, 0], rotation = p.local ? p.local.rotation : [0, 0, 0];
  const vk = p.v.map(() => key4()), vertices = {};
  p.v.forEach((q, i) => { vertices[vk[i]] = sub(q, origin).map(x => +x.toFixed(5)); });
  const faces = {};
  for (const is of islands.filter(i => i.part === p)) for (const f of is.faces) {
    const uv = {}; for (const vi of f) { const [u, v] = is.uv.get(vi); uv[vk[vi]] = [+(((u - is.u0) * scale + is.px) * UVS).toFixed(4), +(((v - is.v0) * scale + is.py) * UVS).toFixed(4)]; }
    faces[key4()] = {uv, vertices: f.map(i => vk[i])};
  }
  return {bone: p.bone, kind: p.kind, element: {name: p.name, origin, rotation, vertices, faces, type: 'mesh'}};
});
// flat identification fill (kept for P9_DIAG and as padding default); the V3 Base Color painter below replaces it
const COL = {slide: [58, 60, 64], barrel: [96, 98, 102], internal: [18, 18, 20], frame: [34, 35, 37], grip: [28, 28, 30], ctrl: [44, 45, 48], sight: [30, 30, 32], mag: [52, 54, 58], brass: [176, 138, 74], chamber: [120, 122, 126], ramp: [170, 172, 176], follower: [150, 110, 50]};
// P9_DIAG=1: high-contrast identification colours for structure review renders
if (process.env.P9_DIAG) Object.assign(COL, {slide: [80, 110, 170], barrel: [200, 150, 60], internal: [8, 8, 8], frame: [120, 120, 120], grip: [40, 90, 60], ctrl: [170, 60, 170], sight: [0, 190, 190], mag: [60, 170, 70], chamber: [215, 215, 220], ramp: [255, 255, 255], follower: [240, 130, 20], brass: [220, 170, 60]});
const img = Buffer.alloc(ATLAS * ATLAS * 4); for (let i = 0; i < ATLAS * ATLAS; i++) img.set([60, 60, 60, 255], i * 4);
for (const is of islands) {
  const n = norm(is.faces.reduce((a, f) => add(a, faceNormalRaw(is.part.v, f)), [0, 0, 0]));
  const k = 0.78 + 0.3 * Math.max(-0.4, n[1]) + 0.06 * Math.abs(n[0]);
  const c = COL[is.part.kind].map(v => Math.max(0, Math.min(255, Math.round(v * k))));
  const W = Math.ceil(is.w * scale) + 1, H = Math.ceil(is.h * scale) + 1;
  for (let y = is.py - 2; y < is.py + H + 2; y++) for (let x = is.px - 2; x < is.px + W + 2; x++) if (x >= 0 && y >= 0 && x < ATLAS && y < ATLAS) img.set([...c, 255], (y * ATLAS + x) * 4);
}
// LabPBR 1.3 companions (P9-01 PBR V1), written in the same raster pass so they always share the Base Color UVs:
//   _s: R perceptual smoothness, G F0 (255 = metal, F0 from Base Color; 10 = dielectric ~0.04), B 0, A 255 (no emission)
//   _n: RG tangent-space normal XY (OpenGL, +v up, same convention as Blackridge), B material AO, A 255 (no parallax)
// Defaults outside islands: neutral dielectric, flat normal, no occlusion.
const spec = Buffer.alloc(ATLAS * ATLAS * 4), nrm = Buffer.alloc(ATLAS * ATLAS * 4);
for (let i = 0; i < ATLAS * ATLAS; i++) { spec.set([90, 10, 0, 255], i * 4); nrm.set([128, 128, 255, 255], i * 4); }
// ================= Base Color painter (replaces the flat temp fill unless P9_DIAG) =================
// Every face is rasterised into its UV island; colour comes from the part's material, a faint top-light facing
// term, edge highlights on convex feature edges, soft darkening on concave edges (both only along texel-axis
// aligned edges on large islands), and very light surface variation (brushed slide, grip stipple). No lighting is
// baked beyond the faint facing term, no fake reflections. Deterministic (hash noise, no Math.random).
if (!process.env.P9_DIAG) {
  const hash = (a, b, c = 0) => { let h = Math.imul(a | 0, 374761393) ^ Math.imul(b | 0, 668265263) ^ Math.imul(c | 0, 2147483647); h = Math.imul(h ^ (h >>> 13), 1274126177); return ((h ^ (h >>> 16)) >>> 0) / 4294967295; };
  const vnoise = x => { const i = Math.floor(x), f = x - i, s = f * f * (3 - 2 * f); return hash(i, 71) * (1 - s) + hash(i + 1, 71) * s; };
  const smooth = (e0, e1, x) => { const t = Math.max(0, Math.min(1, (x - e0) / (e1 - e0))); return t * t * (3 - 2 * t); };
  // materials: base sRGB, convex-edge highlight, concave darkening, surface variation style
  // V4 Base Color A (near-black, after a black duty-pistol reference): parts separate by surface character and a
  // 1-2 level warm / cool shift, not by value. Edge highlight hl ~ 30 % of the base value. Steel stays neutral (PBR).
  const M = {
    slide:    {c: [48, 49, 51], hl: 15, cv: 0.30, st: 'brushed'},    // black nitrided steel, neutral-cool
    recess:   {c: [26, 26, 27], hl: 5, cv: 0.35, st: 'flat'},        // port interior, slide inner walls
    breech:   {c: [56, 57, 58], hl: 16, cv: 0.30, st: 'turned'},     // breech face plate
    barrel:   {c: [74, 75, 76], hl: 20, cv: 0.30, st: 'turned'},     // separate steel part, one step lighter than the slide
    hood:     {c: [72, 73, 74], hl: 20, cv: 0.30, st: 'brushed'},
    ramp:     {c: [118, 119, 120], hl: 26, cv: 0.25, st: 'turned'},  // polished feed ramp
    bore:     {c: [14, 14, 15], hl: 3, cv: 0.2, st: 'depth'},
    frame:    {c: [42, 41, 41], hl: 12, cv: 0.30, st: 'polymer'},    // black polymer, slightly warm
    rail:     {c: [42, 41, 41], hl: 0, cv: 0, st: 'flat', uni: true},     // accessory rail: frame polymer
    guard:    {c: [42, 41, 41], hl: 12, cv: 0.30, st: 'polymer'},
    grip:     {c: [40, 39, 39], hl: 10, cv: 0.30, st: 'stipple'},    // smooth border darker; stippled panels read lighter
    magwell:  {c: [18, 18, 18], hl: 4, cv: 0.2, st: 'flat'},
    control:  {c: [52, 52, 54], hl: 16, cv: 0.30, st: 'turned'},     // black steel controls, a hair above the slide
    trigger:  {c: [38, 37, 37], hl: 11, cv: 0.30, st: 'polymer'},
    pin:      {c: [70, 70, 71], hl: 18, cv: 0.2, st: 'turned'},
    sight:    {c: [30, 30, 31], hl: 10, cv: 0.35, st: 'flat'},       // deepest black on the gun
    mag:      {c: [44, 44, 45], hl: 13, cv: 0.30, st: 'matte'},      // black phosphated steel tube
    lip:      {c: [56, 56, 57], hl: 16, cv: 0.30, st: 'matte'},
    base:     {c: [40, 39, 39], hl: 12, cv: 0.30, st: 'polymer'},    // polymer baseplate
    follower: {c: [56, 54, 50], hl: 14, cv: 0.25, st: 'polymer'},    // warm dark polymer follower
  };
  // ---------------- P9-01 PBR V1 material table (LabPBR _s / _n) ----------------
  // sm: smoothness 0-255 on open faces; edge: narrow convex bevel strips and the axis-aligned rim next to hard edges
  // (restrained wear: polished edges, no paint loss); metal: F0 from Base Color, else dielectric F0 0.04; ao: material
  // occlusion for interiors only (the Base Color already darkens concave areas, so it stays mild).
  const PBR = new Map([
    [M.slide,    {sm: 125, edge: 160, metal: true}],             // black nitride steel slide: satin
    [M.recess,   {sm: 80,  edge: 90,  metal: true, ao: 175}],    // port interior, slide inner walls
    [M.breech,   {sm: 140, edge: 150, metal: true, ao: 200}],    // breech face seen through the port
    [M.barrel,   {sm: 165, edge: 180, metal: true}],             // machined barrel (crown 180)
    [M.hood,     {sm: 165, edge: 175, metal: true, ao: 215}],    // machined hood, under the slide
    [M.ramp,     {sm: 205, edge: 210, metal: true, ao: 215}],    // polished feed ramp
    [M.bore,     {sm: 70,  edge: 70,  metal: true, ao: 150}],    // bore, chamber, firing-pin hole
    [M.frame,    {sm: 90,  edge: 100, metal: false}],            // dark molded polymer frame
    [M.rail,     {sm: 90,  edge: 90,  metal: false}],
    [M.guard,    {sm: 90,  edge: 100, metal: false}],
    [M.grip,     {sm: 100, edge: 105, metal: false}],            // smooth grip border; stippled panels 35
    [M.magwell,  {sm: 60,  edge: 60,  metal: false, ao: 150}],
    [M.control,  {sm: 115, edge: 150, metal: true}],             // dark gunmetal controls, extractor, striker cap
    [M.trigger,  {sm: 110, edge: 120, metal: false}],
    [M.pin,      {sm: 150, edge: 165, metal: true}],
    [M.sight,    {sm: 55,  edge: 70,  metal: true}],             // matte anti-glare sights
    [M.mag,      {sm: 100, edge: 125, metal: true}],             // phosphated magazine tube
    [M.lip,      {sm: 135, edge: 150, metal: true}],             // feed lips, polished by the rounds
    [M.base,     {sm: 85,  edge: 95,  metal: false}],
    [M.follower, {sm: 70,  edge: 70,  metal: false, ao: 190}],
  ]);
  const matOf = (p, pos, n) => {
    const nm = p.name.replace(/_reload$/, '');
    if (nm === 'slide_breech_plate') return M.breech;
    if (nm === 'slide_extractor' || nm === 'slide_striker_cap') return M.control;
    if (nm === 'slide_firing_pin_hole' || nm === 'barrel_bore' || nm === 'barrel_chamber') return M.bore;
    if (nm === 'slide_port' || nm === 'slide_chamber_cavity') {
      // walls facing the slide centre line or down from the roof are interior surfaces
      const inner = (n[0] * pos[0] < -0.3 && Math.abs(pos[0]) < 0.84) || n[1] < -0.7 || (pos[1] < 5.0 && Math.abs(pos[0]) < 0.63);
      return inner ? M.recess : M.slide;
    }
    if (nm === 'slide_port_front') return M.recess;
    if (nm.startsWith('slide')) return M.slide;
    if (nm === 'barrel_tube' || nm === 'barrel_chamber_mouth') return M.barrel;
    if (nm === 'barrel_hood') return M.hood;
    if (nm === 'barrel_feed_ramp') return M.ramp;
    if (nm.includes('sight')) return M.sight;
    if (nm === 'frame_rail') return M.rail;
    if (nm === 'frame_trigger_guard') return M.guard;
    if (nm === 'frame_grip') return M.grip;
    if (nm === 'frame_magwell') return M.magwell;
    if (nm === 'frame_controls') return pos[1] > 4.2 && pos[1] < 4.36 && Math.abs(pos[0]) > 0.755 && (Math.abs(pos[2] + 0.35) < 0.09 || Math.abs(pos[2] - 2.05) < 0.09) ? M.pin : M.control;
    if (nm === 'trigger_blade') return M.trigger;
    if (nm === 'magazine_body') return pos[1] > 3.24 ? M.lip : M.mag;
    if (nm === 'magazine_baseplate') return M.base;
    if (nm === 'magazine_follower') return M.follower;
    return M.frame;
  };
  const toWorldN = (p, n) => p.local ? (() => { const c = Math.cos(MAG_TILT * D), s = Math.sin(MAG_TILT * D); return [n[0], n[1] * c - n[2] * s, n[1] * s + n[2] * c]; })() : n;
  const colour = (p, m, pos, n, px, py, e, cc, tag = null) => {
    const nw = toWorldN(p, n);
    let k = 1 + 0.03 * nw[1] - 0.02 * Math.max(0, -nw[1]);                     // faint top light, underside falloff
    const q = [Math.round(pos[0] * 60), Math.round(pos[1] * 60), Math.round(pos[2] * 60)];
    switch (m.st) {
      case 'brushed': k *= Math.abs(n[2]) < 0.5 ? 1 + 0.008 * (hash(q[0], q[1], 7) - 0.5) + 0.008 * (vnoise(pos[2] * 1.3 + pos[1] * 9) - 0.5)   // faint strokes along the slide
                                            : 1; break;                                                                                         // end faces: clean
      case 'turned':  k *= 1 + 0.008 * (hash(q[0], q[1], q[2]) - 0.5); break;
      case 'polymer': break;                                                   // clean: grain on near-black polymer reads as dirt
      case 'matte':   k *= 1 + 0.006 * (hash(px, py, 5) - 0.5) + 0.004 * (vnoise(pos[1] * 3 + pos[0] * 2) - 0.5); break;
      case 'stipple':   // stipple only inside the sunk panels; straps, top and base stay smooth
        if (tag === 'panel') k *= 1.25 + 0.10 * (hash(px, py, 11) - 0.5) + 0.06 * (hash(px >> 1, py >> 1, 13) - 0.5);   // scattering stipple: ~50 +-4
        break;
      case 'depth': k *= Math.max(0.55, 1 - 0.35 * Math.min(1, Math.abs(pos[2] - MUZZLE_Z) < 1.2 ? (pos[2] - MUZZLE_Z) / 1.1 : 1)); break;
    }
    if (p.kind === 'frame' && Math.abs(nw[0]) > 0.8) k *= 1 - 0.14 * smooth(4.62, 4.78, pos[1]);   // occlusion under the slide
    // narrow parts (serration lands, rail): one clean tone per face; axis-aligned flats brighter, slanted flanks darker
    if (m.uni) k *= 0.80 + 0.20 * Math.max(Math.abs(nw[0]), Math.abs(nw[1]), Math.abs(nw[2])) ** 4;
    k *= 1 - m.cv * Math.min(1, cc);
    return m.c.map(v => Math.max(0, Math.min(255, Math.round(v * k + m.hl * Math.min(1, e)))));
  };
  // slide segment ends at the cavity / port / rear junctions are internal except where the breech face shows in the port
  const JZ = [SLIDE.portF - 0.60, SLIDE.portF, SLIDE.portR];
  const hiddenJunction = (p, A, B, nj) => p.bone === 'slide2' && Math.abs(nj[2]) > 0.95 && JZ.some(z => Math.abs(A[2] - z) < 1e-4 && Math.abs(B[2] - z) < 1e-4)
    && !(Math.abs(A[2] - SLIDE.portR) < 1e-4 && (A[0] + B[0]) / 2 > -0.31 && (A[1] + B[1]) / 2 > 5.19 && (A[1] + B[1]) / 2 < 5.80);
  const recess = t => t === 'pocket' || t === 'step';
  // ---------------- PBR helpers ----------------
  const vn3 = (x, y, z) => {   // smooth 3D value noise in [0, 1]: low-frequency smoothness variation, never per-pixel
    const xi = Math.floor(x), yi = Math.floor(y), zi = Math.floor(z), s = t => t * t * (3 - 2 * t), l = (a, b, t) => a + (b - a) * t;
    const sx = s(x - xi), sy = s(y - yi), sz = s(z - zi), h = (a, b, c) => hash(a, b, c * 7919 + 17);
    return l(l(l(h(xi, yi, zi), h(xi + 1, yi, zi), sx), l(h(xi, yi + 1, zi), h(xi + 1, yi + 1, zi), sx), sy),
      l(l(h(xi, yi, zi + 1), h(xi + 1, yi, zi + 1), sx), l(h(xi, yi + 1, zi + 1), h(xi + 1, yi + 1, zi + 1), sx), sy), sz);
  };
  // high-contact areas polish slightly: racking serration zones, slide-stop thumb pad, magazine catch face, trigger face
  const contact = (m, pos, n) => {
    if (m === M.slide && Math.abs(n[0]) > 0.9 && pos[1] > 4.9 && pos[1] < 5.5 && ((pos[2] > 1.55 && pos[2] < 3.45) || (pos[2] > -6.15 && pos[2] < -5.05))) return 12;
    if (m === M.control && ((pos[0] < -0.924 && pos[2] > 0.56 && pos[2] < 0.97 && pos[1] > 4.49) || (pos[0] < -0.86 && pos[2] > 0.70 && pos[2] < 1.04 && pos[1] > 3.68 && pos[1] < 3.98))) return 15;
    if (m === M.trigger && n[2] < -0.6) return 10;
    return 0;
  };
  // ef: 0..1 edge factor (narrow convex bevel strip, or the axis-aligned rim beside a hard edge on a large face)
  const specOf = (m, pos, n, ef, tag) => {
    const P = PBR.get(m); let sm;
    if (tag === 'panel') sm = 35;                                   // stippled grip panels: rough, the normal map carries the grain
    else if (tag === 'pocket') sm = P.metal ? 95 : 80;              // serration grooves, frame channel / index recess
    else if (tag === 'step') sm = P.metal ? 110 : 70;               // slide front step, grip panel step walls
    else sm = P.sm + (P.edge - P.sm) * ef + contact(m, pos, n);
    if (m === M.barrel && pos[2] < MUZZLE_Z + 0.05) sm = Math.max(sm, 180);   // muzzle crown
    sm += (P.metal ? 5 : 3) * (2 * vn3(pos[0] * 3.2, pos[1] * 3.2, pos[2] * 3.2) - 1);
    return [Math.max(0, Math.min(255, Math.round(sm))), P.metal ? 255 : 10, 0, 255];
  };
  const aoOf = (m, tag) => tag === 'pocket' ? (m === M.slide ? 205 : 215) : tag === 'step' ? 230 : (PBR.get(m).ao ?? 255);
  const enc = v => Math.max(0, Math.min(255, Math.round((v * 0.5 + 0.5) * 255)));
  const stippleH = (x, y) => 0.6 * hash(x >> 1, y >> 1, 13) + 0.4 * hash(x, y, 11);   // same cells as the Base Color stipple
  // Tangent-space XY. frame = [+u, +v-up] of the face (island tangent projected on the face). Only two areas carry
  // normal detail: the stippled grip panels and a rifling hint in the bore; everything else stays flat (no shimmer).
  const normalOf = (p, m, pos, n, frame, px, py, tag) => {
    if (tag === 'panel' && m === M.grip) {
      const du = (stippleH(px + 1, py) - stippleH(px - 1, py)) / 2, dvUp = -(stippleH(px, py + 1) - stippleH(px, py - 1)) / 2;
      const v = norm([-du * 0.9, -dvUp * 0.9, 1]); return [enc(v[0]), enc(v[1])];
    }
    if (p.name === 'barrel_bore') {   // six grooves, slight twist; slope only on the soft groove walls
      const th = Math.atan2(pos[1] - AXIS_Y, pos[0]), ph = th / (2 * Math.PI) * 6 + (pos[2] - MUZZLE_Z) * 0.25, f = ph - Math.floor(ph);
      const ds = (a, b, x) => { const t = (x - a) / (b - a); return t <= 0 || t >= 1 ? 0 : 6 * t * (1 - t) / (b - a); };
      const v = norm(sub(n, mul([-Math.sin(th), Math.cos(th), 0], 0.04 * (ds(0, 0.2, f) - ds(0.45, 0.65, f)))));
      return [enc(dot(v, frame[0])), enc(dot(v, frame[1]))];
    }
    return [128, 128];
  };
  const pbrStats = process.env.P9_PBR_STATS ? new Map() : null;
  globalThis.__p9PbrStats = pbrStats;   // P9_PBR_STATS=1: per part / tag summary of the written _s / _n values
  img.fill(0); for (let i = 0; i < ATLAS * ATLAS; i++) img[i * 4 + 3] = 255;
  for (const p of parts) {
    const fid = new Map(p.f.map((f, i) => [f, i])), fn = p.f.map(f => norm(faceNormalRaw(p.v, f))), fc = p.f.map(f => centroid(f.map(i => p.v[i])));
    const em = new Map(); p.f.forEach((f, i) => f.forEach((a, k) => { const b = f[(k + 1) % f.length], key = a < b ? a + ',' + b : b + ',' + a; (em.get(key) || em.set(key, []).get(key)).push(i); }));
    for (const is of islands.filter(x => x.part === p)) {
      const pix = vi => { const [u, v] = is.uv.get(vi); return [(u - is.u0) * scale + is.px, (v - is.v0) * scale + is.py]; };
      // padding: island bbox + 2 px in the face-average material colour (limits mip / filtering seams)
      const f0 = is.faces[0], m0 = matOf(p, fc[fid.get(f0)], fn[fid.get(f0)]), pc = colour(p, m0, fc[fid.get(f0)], fn[fid.get(f0)], 0, 0, 0, 0);
      const W = Math.ceil(is.w * scale) + 1, H = Math.ceil(is.h * scale) + 1;
      for (let y = is.py - 2; y < is.py + H + 2; y++) for (let x = is.px - 2; x < is.px + W + 2; x++) if (x >= 0 && y >= 0 && x < ATLAS && y < ATLAS) img.set(pc, (y * ATLAS + x) * 4);
      { // PBR padding in the same bbox: the first face's open-surface values, flat normal
        const t0 = p.t[fid.get(f0)], ps = specOf(m0, fc[fid.get(f0)], fn[fid.get(f0)], 0, t0), pn = [128, 128, aoOf(m0, t0), 255];
        for (let y = is.py - 2; y < is.py + H + 2; y++) for (let x = is.px - 2; x < is.px + W + 2; x++) if (x >= 0 && y >= 0 && x < ATLAS && y < ATLAS) { spec.set(ps, (y * ATLAS + x) * 4); nrm.set(pn, (y * ATLAS + x) * 4); }
      }
      // Edge terms per island, hard edges only (> ~25 deg): distance is measured to the island's own borders, so
      // triangulation seams inside a flat face never show and sliver triangles shade exactly like their neighbours.
      // Rolled bevels (15-22 deg steps) read through their face normals instead of drawn lines.
      const HARD = 0.9, inIsland = new Set(is.faces.map(f => fid.get(f))), segs = [];
      let area = 0;
      for (const f of is.faces) {
        const i = fid.get(f), n = fn[i], P2 = f.map(pix);
        for (let t = 1; t + 1 < f.length; t++) area += Math.abs(cross2(sub2(P2[t], P2[0]), sub2(P2[t + 1], P2[0]))) / 2;
        f.forEach((a, k) => { const b = f[(k + 1) % f.length], key = a < b ? a + ',' + b : b + ',' + a;
          for (const j of em.get(key)) { if (j === i || inIsland.has(j)) continue; const d = dot(n, fn[j]); if (d > HARD) continue;
            if (hiddenJunction(p, p.v[a], p.v[b], fn[j])) continue;
            // recess outlines (pocket mouths, grip panel steps) run diagonally through the texel grid: a texture-space rim
            // there stair-steps, so the surrounding face / panel floor gets none. The narrow chamfer, wall and floor
            // islands keep their uniform edge tone, so the recess still reads, with geometry-sharp outlines.
            if (recess(p.t[j]) && !recess(p.t[i])) continue;
            // texel-axis aligned edges (long bevels along the gun) can carry a per-pixel rim; slanted ones would stair-step
            const du = Math.abs(P2[(k + 1) % f.length][0] - P2[k][0]), dv = Math.abs(P2[(k + 1) % f.length][1] - P2[k][1]);
            segs.push({a: P2[k], b: P2[(k + 1) % f.length], cvx: dot(sub(fc[j], fc[i]), n) < 0, s: Math.min(1, (1 - d) / 0.35), axis: du < 0.07 * dv || dv < 0.07 * du}); } });
      }
      // islands narrower than ~2.5 px (bevel strips, lands) get one uniform edge value instead of a broken 1 px line
      const narrow = area / Math.max(1, Math.max(W, H)) < 2.5;
      let eU = 0, cU = 0;
      if (narrow) for (const sg of segs) { if (sg.cvx) eU = Math.max(eU, 0.6 * sg.s); else cU = Math.max(cU, 0.5 * sg.s); }
      for (const f of is.faces) {
        const i = fid.get(f), n = fn[i], P2 = f.map(pix), P3 = f.map(k => p.v[k]), fm = matOf(p, fc[i], n);   // one material per face
        const fcol = fm.uni ? colour(p, fm, fc[i], n, 0, 0, 0, 0) : null;
        const tf = norm(sub(is.t, mul(n, dot(is.t, n)))), frame = [tf, cross(n, tf)];   // normal-map frame of this face
        for (let t = 1; t + 1 < f.length; t++) {
          const A = P2[0], B = P2[t], C = P2[t + 1], a3 = P3[0], b3 = P3[t], c3 = P3[t + 1];
          const den = (B[1] - C[1]) * (A[0] - C[0]) + (C[0] - B[0]) * (A[1] - C[1]); if (Math.abs(den) < 1e-9) continue;
          const x0 = Math.floor(Math.min(A[0], B[0], C[0]) - 1), x1 = Math.ceil(Math.max(A[0], B[0], C[0]) + 1), y0 = Math.floor(Math.min(A[1], B[1], C[1]) - 1), y1 = Math.ceil(Math.max(A[1], B[1], C[1]) + 1);
          for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) {
            if (x < 0 || y < 0 || x >= ATLAS || y >= ATLAS) continue;
            const X = x + 0.5, Y = y + 0.5, l1 = ((B[1] - C[1]) * (X - C[0]) + (C[0] - B[0]) * (Y - C[1])) / den, l2 = ((C[1] - A[1]) * (X - C[0]) + (A[0] - C[0]) * (Y - C[1])) / den, l3 = 1 - l1 - l2;
            const tol = -0.75 / Math.max(1, Math.sqrt(Math.abs(den)));   // about half a pixel of overdraw so faces meet without gaps
            if (l1 < tol || l2 < tol || l3 < tol) continue;
            const pos = add(add(mul(a3, l1), mul(b3, l2)), mul(c3, l3));
            let e = eU, cc = cU;
            if (!narrow) for (const sg of segs) { if (!sg.axis) continue; const ab = sub2(sg.b, sg.a), L2 = dot2(ab, ab) || 1, tt = Math.max(0, Math.min(1, dot2(sub2([X, Y], sg.a), ab) / L2)), dd = Math.hypot(...sub2([X, Y], add2(sg.a, mul2(ab, tt))));
              if (sg.cvx) e = Math.max(e, sg.s * Math.max(0, 1 - dd / 1.5)); else cc = Math.max(cc, sg.s * Math.max(0, 1 - dd / 2.5)); }
            img.set(fm.uni ? fcol : colour(p, fm, pos, n, x, y, e, cc, p.t[i]), (y * ATLAS + x) * 4);
            const k4 = (y * ATLAS + x) * 4, nv = normalOf(p, fm, pos, n, frame, x, y, p.t[i]);
            const sv = specOf(fm, pos, n, narrow ? Math.min(1, eU / 0.6) : Math.min(1, e), p.t[i]), ao = aoOf(fm, p.t[i]);
            spec.set(sv, k4); nrm.set([nv[0], nv[1], ao, 255], k4);
            if (pbrStats) { const key = `${p.name.replace(/_reload$/, '')}|${p.t[i] || '-'}`, st = pbrStats.get(key) || pbrStats.set(key, {n: 0, sum: 0, min: 255, max: 0, g: new Set(), ao: new Set(), nrm: 0}).get(key);
              st.n++; st.sum += sv[0]; st.min = Math.min(st.min, sv[0]); st.max = Math.max(st.max, sv[0]); st.g.add(sv[1]); st.ao.add(ao); if (nv[0] !== 128 || nv[1] !== 128) st.nrm++; }
          }
        }
      }
    }
  }
}
if (globalThis.__p9PbrStats) for (const [k, v] of globalThis.__p9PbrStats) console.error(k.padEnd(34), 'px', String(v.n).padStart(6), 'R', (v.sum / v.n).toFixed(1).padStart(6), `[${v.min}-${v.max}]`.padStart(10), 'G', [...v.g].join('/').padEnd(7), 'AO', [...v.ao].join('/').padEnd(8), 'nrm px', v.nrm);
fs.writeFileSync(process.argv[2], JSON.stringify({scale, elements}));
if (process.argv[3]) fs.writeFileSync(process.argv[3], img);
if (process.argv[4]) fs.writeFileSync(process.argv[4], spec);   // LabPBR _s (RGBA)
if (process.argv[5]) fs.writeFileSync(process.argv[5], nrm);    // LabPBR _n (RGBA)
const tri = elements.reduce((s, e) => s + Object.values(e.element.faces).reduce((a, f) => a + f.vertices.length - 2, 0), 0);
console.log(JSON.stringify({parts: parts.length, islands: islands.length, texelPerUnit: scale, triangles: tri,
  perBone: elements.reduce((m, e) => (m[e.bone] = (m[e.bone] || 0) + 1, m), {})}));
