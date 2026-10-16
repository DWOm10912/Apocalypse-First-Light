// Coplanar overlap audit for the T15 meshes (2026-10-16, licence plate bracket flickering against the front bumper face).
// Two faces that lie in the same plane, point the same way and overlap z-fight where nothing is in front of them (a trim
// piece whose face is flush with the panel it sits on). Faces pointing at each other are two solids touching: each one is
// hidden behind the other solid's outer face, so they are not reported (entity cutout draws back faces, but those lie
// inside the other solid). Checks every pair of faces within one bone of a mesh, and the body bones of all
// seven meshes against each other (pivot 0: they share the truck's frame). Faces of one part that share an edge are a
// mesh's own surface and are skipped only when they do not overlap (adjacent faces touch, they do not overlap).
//   node tools/pickup-t15/audit-coplanar.mjs [--min <mm2>] [--tol <mm>] [--deg <deg>]
//     -> one line per part pair: overlap area (mm^2), where (blocks); faces closer than tol (1.5 mm) in planes within deg (4);
//        --detail: the largest spots per pair (10 cm cells, model frame: front is -z)
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const dir = path.join(root, 'src/main/resources/assets/apocalypse_firstlight/meshes');
const IDS = ['pickup_t15', 'pickup_t15_front', 'pickup_t15_wheel', 'pickup_t15_cab', 'pickup_t15_seat', 'pickup_t15_chassis', 'pickup_t15_engine'];
const mi = process.argv.indexOf('--min');
const MIN_MM2 = mi > 0 ? +process.argv[mi + 1] : 1;
const ti = process.argv.indexOf('--tol'), ci = process.argv.indexOf('--deg');
// blocks: 1.5 mm by default (2026-10-16: the plate bracket flickered 0.2-0.8 mm off a slightly curved bumper face)
const PLANE_TOL = (ti > 0 ? +process.argv[ti + 1] : 1.5) / 1000;
const ANGLE_TOL = Math.cos((ci > 0 ? +process.argv[ci + 1] : 4) * Math.PI / 180);   // normals within this angle (default 4 deg)

const sub = (a, b) => [a[0] - b[0], a[1] - b[1], a[2] - b[2]];
const cross = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
const dot = (a, b) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
const len = a => Math.hypot(a[0], a[1], a[2]);

// faces grouped by "group" (mesh + bone, or shared body)
const groups = new Map();
for (const id of IDS) {
  const m = JSON.parse(fs.readFileSync(path.join(dir, id + '.aflmesh.json'), 'utf8'));
  for (const p of m.parts) {
    if (p.render_layer === 'translucent') continue;   // glass draws in its own pass, sorted after the cutout
    const g = p.bone === 'body' ? 'body' : id + ':' + p.bone;
    if (!groups.has(g)) groups.set(g, []);
    const list = groups.get(g);
    for (const f of p.faces) {
      const pts = f.map(i => p.vertices[i].slice(0, 3));
      let n = [0, 0, 0];
      for (let i = 1; i + 1 < pts.length; i++) { const c = cross(sub(pts[i], pts[0]), sub(pts[i + 1], pts[0])); n = [n[0] + c[0], n[1] + c[1], n[2] + c[2]]; }
      const area2 = len(n);
      if (area2 < 1e-10) continue;
      n = n.map(x => x / area2);
      // canonical sign: opposite-facing faces in one plane also flicker (no culling)
      const k = Math.abs(n[0]) > Math.abs(n[1]) ? (Math.abs(n[0]) > Math.abs(n[2]) ? 0 : 2) : (Math.abs(n[1]) > Math.abs(n[2]) ? 1 : 2);
      const out = n;   // outward (the mesh winding)
      if (n[k] < 0) n = n.map(x => -x);
      // same corners = the other side of a two-sided plate (plate3): one surface, not an overlap
      const corners = pts.map(q => q.map(x => Math.round(x * 1e5)).join(',')).sort().join(';');
      list.push({ part: id + '/' + p.name, pts, n, out, d: dot(n, pts[0]), key: f.join(','), corners });
    }
  }
}

// 2D convex clip (faces are triangles or planar quads)
function project(pts, n) {
  const a = Math.abs(n[0]) > 0.5 ? [0, 1, 0] : [1, 0, 0];
  const u = cross(n, a), ul = len(u), uu = u.map(x => x / ul), vv = cross(n, uu);
  return pts.map(p => [dot(p, uu), dot(p, vv)]);
}
function area(poly) { let s = 0; for (let i = 0; i < poly.length; i++) { const a = poly[i], b = poly[(i + 1) % poly.length]; s += a[0] * b[1] - a[1] * b[0]; } return s / 2; }
function clip(subject, clipper) {
  if (area(clipper) < 0) clipper = clipper.slice().reverse();
  let out = area(subject) < 0 ? subject.slice().reverse() : subject.slice();
  for (let i = 0; i < clipper.length && out.length; i++) {
    const a = clipper[i], b = clipper[(i + 1) % clipper.length];
    const side = p => (b[0] - a[0]) * (p[1] - a[1]) - (b[1] - a[1]) * (p[0] - a[0]);
    const input = out; out = [];
    for (let j = 0; j < input.length; j++) {
      const p = input[j], q = input[(j + 1) % input.length], sp = side(p), sq = side(q);
      if (sp >= 0) out.push(p);
      if ((sp >= 0) !== (sq >= 0)) { const t = sp / (sp - sq); out.push([p[0] + (q[0] - p[0]) * t, p[1] + (q[1] - p[1]) * t]); }
    }
  }
  return out.length >= 3 ? Math.abs(area(out)) : 0;
}

// h lies on f's plane: every corner of h within PLANE_TOL of it (a slightly tilted strip next to a flat face counts)
const onPlane = (f, h) => h.pts.every(q => Math.abs(dot(f.n, q) - f.d) <= PLANE_TOL);
const CELL = 0.05;   // blocks: spatial hash of face boxes
const hits = new Map();
for (const [g, faces] of groups) {
  const grid = new Map();
  faces.forEach((f, i) => {
    f.i = i;
    const lo = [0, 1, 2].map(k => Math.floor((Math.min(...f.pts.map(q => q[k])) - PLANE_TOL) / CELL));
    const hi = [0, 1, 2].map(k => Math.floor((Math.max(...f.pts.map(q => q[k])) + PLANE_TOL) / CELL));
    f.cells = [];
    for (let x = lo[0]; x <= hi[0]; x++) for (let y = lo[1]; y <= hi[1]; y++) for (let z = lo[2]; z <= hi[2]; z++) {
      const k = x + ',' + y + ',' + z;
      if (!grid.has(k)) grid.set(k, []);
      grid.get(k).push(f);
      f.cells.push(k);
    }
  });
  for (const f of faces) {
    const seen = new Set();
    for (const k of f.cells) for (const h of grid.get(k)) {
      if (h.i <= f.i || seen.has(h.i)) continue;
      seen.add(h.i);
      // same way only: faces pointing at each other are two solids touching, each buried behind the other's outside
      if (dot(f.out, h.out) < ANGLE_TOL || f.corners === h.corners) continue;
      const ref = onPlane(f, h) ? f : onPlane(h, f) ? h : null;   // the face whose plane the other lies on
      if (!ref) continue;
      const a = clip(project(f.pts, ref.n), project(h.pts, ref.n));
      if (a <= 1e-9) continue;
      const pair = [f.part, h.part].sort().join('  x  ');
      const e = hits.get(pair) || { area: 0, at: f.pts[0], spots: new Map() };
      e.area += a; hits.set(pair, e);
      // where: overlap area per 10 cm cell of the overlapping face's centre (--detail)
      const c = [0, 1, 2].map(k => Math.round(f.pts.reduce((s, q) => s + q[k], 0) / f.pts.length * 10) / 10).join(' ');
      e.spots.set(c, (e.spots.get(c) || 0) + a);
    }
  }
}
const rows = [...hits].map(([k, e]) => [k, e.area * 1e6, e.at]).filter(r => r[1] >= MIN_MM2).sort((a, b) => b[1] - a[1]);
const DETAIL = process.argv.includes('--detail');
for (const [k, mm2, at] of rows) {
  console.log(mm2.toFixed(0).padStart(8), 'mm2 ', k, ' at', at.map(x => x.toFixed(3)).join(' '));
  if (DETAIL) for (const [c, a] of [...hits.get(k).spots].sort((x, y) => y[1] - x[1]).slice(0, 6)) console.log('           ', (a * 1e6).toFixed(0).padStart(7), 'mm2 near', c);
}
console.log(rows.length ? rows.length + ' coplanar overlaps' : 'no coplanar overlaps');
process.exitCode = rows.length ? 1 : 0;
