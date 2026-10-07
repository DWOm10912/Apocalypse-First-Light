// Silverwood 12 V3: side-by-side (SxS) boxlock double-barrel shotgun, Pure Mesh + 1024 LabPBR atlas + AFL Native Rig in
// its idle pose. Model only: every clip is a static placeholder (the gun never opens yet); animations come later.
//   node tools/build-silverwood-12-v3.mjs            -> writes source, runtime geo / sidecar / maps / animation
//   node tools/build-silverwood-12-v3.mjs --check    -> verifies every output is up to date
//   node tools/build-silverwood-12-v3.mjs --debug <out.bbmodel>  -> writes the source only, reference arms exported (previews)
// Design reference (proportions, layout and material split only; no geometry or texture taken from it): a hammerless
// boxlock SxS the user supplied in Blender. Real scale, 1 unit ~ 22 mm: overall ~46 (1.0 m), 22" barrels (25.5), 12-bore
// (bore 0.84 = 18.5 mm), barrels converging toward the muzzle, flat matte top rib with a brass bead, splinter forend with a
// forend iron cupping the action knuckle, half-pistol-grip walnut stock with a ribbed rubber pad, top lever with a
// right-hand thumb piece, tang safety, a single selective trigger (right barrel, then left), extractor between the chambers.
// Frame (Blockbench source): muzzle -Z, up +Y, +X = the shooter's right. The bead top (y 9.885) sits on the existing ADS
// aim line [0, 9.88, 3.5], so the gun's ADS / hip data keeps working unchanged.
// Materials follow the reference split: blued steel (barrels, monoblock, action, trigger guard), polished steel (top lever,
// triggers, safety, extractor), walnut (stock, forend), rubber (butt pad), brass (bead), bright chambers / bore fading to
// dark with depth. Large steel surfaces are coatings in LabPBR terms (F0 24) with bare-metal chamfer edges, per the AFL
// black-steel rule for shader packs.
// Known V1 simplification: the action bar is solid (no lump slot yet), so the lumps only show once an opening animation
// swings them out; the slot is cut when the break-open animation is authored.
import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {convert, serializeCompact} from './export-afl-mesh.mjs';
import {Part, extrude, add, sub, mul, dot, cross, norm, newell, area2, unwrap, paint, png, zFightLevels} from './cube-slab-mesh-lib.mjs';
import {hash, mix, sm} from './lathe-mesh-lib.mjs';
import {silverwoodClips, bake, bakeHand, FPS} from './silverwood-12-v3-animations.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
function assert(c, m) { if (!c) throw new Error(m); }
const PARTS = [], P = (name, bone, mat) => { const p = new Part(name, bone, mat); PARTS.push(p); return p; };
const orient = (L, ccw) => (area2(L) > 0) === ccw ? L : L.slice().reverse();
const ext = (part, ax, outer, holes, a0, a1, c) => extrude(part, ax, {outer: orient(outer, true), holes: holes.map(h => orient(h, false))}, a0, a1, c);
const lerpTable = (T, z, k) => { if (z <= T[0][0]) return T[0][k]; for (let i = 1; i < T.length; i++) if (z <= T[i][0]) { const a = T[i - 1], b = T[i]; return a[k] + (b[k] - a[k]) * (z - a[0]) / (b[0] - a[0]); } return T.at(-1)[k]; };

// ---------------- dimensions ----------------
export const BZ = 4.0, MZ = -21.5, RIB_Y = 9.80;                      // breech face, muzzle, top rib plane
export const BARREL = {breech: [0.72, 9.0, BZ], muzzle: [0.52, 9.18, MZ]};   // right barrel axis (left mirrored)
export const PIN = [0, 7.52, 1.20], PIN_R = 0.22, KNUCKLE_R = 0.60, IRON_R = 0.62;   // hinge pin (across x), knuckle arcs
export const ACTION_W = 1.50, BAR_W = 1.44, CHAMBER = 0.47, BORE = 0.42, RIM = 0.53, RIM_DEPTH = 0.07;
const MUZZLE_DEPTH = 2.6, CHAMBER_DEPTH = 5.6, SEG = 32;

// ---------------- builders ----------------
// lathe frame of an axis; points at (s, r, angle); rings at s = 0 are projected along the axis onto the plane z = planeZ, so
// the breech ends of the (converging) barrels lie exactly in the breech face and share their outline with the monoblock
function frame(o, w, planeZ) {
  const up = Math.abs(w[1]) < 0.9 ? [0, 1, 0] : [1, 0, 0], u = norm(cross(up, w)), v = cross(w, u);
  return {o, w, u, v, at(s, r, a) { const p = add(o, add(mul(w, s), add(mul(u, r * Math.cos(a)), mul(v, r * Math.sin(a)))));
    return planeZ !== undefined && s === 0 ? add(p, mul(w, (planeZ - p[2]) / w[2])) : p; }};
}
// UV charts: lathe and loft surfaces are unwrapped as whole developed strips (one chart per smooth run, own vertices, seam
// vertices duplicated) instead of one planar island per facet; hidden surfaces get charts at HIDDEN texel density.
// Chart = {part, faces, uv: Map(vertex -> [u, v] in model units), k: texel density factor}; extruded parts keep the shared
// planar unwrap. Everything is packed together (see pack below).
// GRADIENT: materials whose colour varies with position; their charts are rectangles so every border lands on texel
// centres (see uvOf) and no gutter colour shows along the seam. Other charts follow the true arc length (affine, V2 quads).
const CHARTS = [], HIDDEN = 0.125, GRADIENT = new Set(['chamber', 'wood']);
const newChart = (part, k = 1) => { const c = {part, faces: [], uv: new Map(), k}; CHARTS.push(c); return c; };
const cv = (c, p, uv) => { const id = c.part.vtx(p); c.uv.set(id, uv); return id; };
const cface = (c, ids, hint, tag, mat) => { const n0 = c.part.f.length; c.part.face(ids, hint, tag, mat); for (let i = n0; i < c.part.f.length; i++) c.faces.push(c.part.f[i]); };
// planar chart of a (near) flat fan: centre + ring, projected onto the plane of normal n
function capChart(part, center, ring, n, mat, k) {
  const c = newChart(part, k), t = norm(Math.abs(n[1]) < 0.9 ? cross(n, [0, 1, 0]) : cross(n, [1, 0, 0])), b = cross(n, t), uv = p => [dot(p, t), dot(p, b)];
  const ci = cv(c, center, uv(center)), ids = ring.map(p => cv(c, p, uv(p)));
  for (let i = 0; i < ids.length; i++) cface(c, [ci, ids[i], ids[(i + 1) % ids.length]], n, 'cap', mat);
}
// closed (s, r, material of the segment ending here) profile revolved about the frame axis; r = 0 points collapse onto it.
// Material names ending in '!' mark hidden segments (chart at HIDDEN density). Radial segments become planar disc charts;
// consecutive side segments of one material turning less than 30 degrees share one developed chart (u = arc, v = profile
// length; GRADIENT materials use the run's largest radius for u: a rectangle).
function lathe(part, F, profile, seg) {
  if (area2(profile) < 0) profile = profile.slice().reverse().map((p, i, a) => [p[0], p[1], a[(i + a.length - 1) % a.length][2]]);
  const n = profile.length, ang = i => 2 * Math.PI * (i % seg) / seg, spec = k => profile[(k + 1) % n][2] || part.mat;
  const seg2 = k => { const [s0, r0] = profile[k], [s1, r1] = profile[(k + 1) % n]; return {s0, r0, s1, r1, o: [r1 - r0, -(s1 - s0)], len: Math.hypot(s1 - s0, r1 - r0)}; };
  const normal = (o, a) => add(mul(F.w, o[0]), add(mul(F.u, o[1] * Math.cos(a)), mul(F.v, o[1] * Math.sin(a))));
  const isCap = k => { const {o, len} = seg2(k); return Math.abs(o[0]) > 0.9 * len; };
  const runs = [];
  for (let k = 0; k < n; k++) {
    const g = seg2(k); if (g.r0 < 1e-6 && g.r1 < 1e-6) continue;
    const last = runs.at(-1), prev = last && seg2(last.at(-1));
    const turn = prev ? Math.acos(Math.max(-1, Math.min(1, (prev.o[0] * g.o[0] + prev.o[1] * g.o[1]) / (prev.len * g.len)))) : Math.PI;
    if (last && !isCap(k) && !isCap(last.at(-1)) && last.at(-1) === k - 1 && spec(last.at(-1)) === spec(k) && turn < Math.PI / 6) last.push(k); else runs.push([k]);
  }
  for (const run of runs) {
    const m = spec(run[0]), mat = m.replace(/!$/, ''), kk = m.endsWith('!') ? HIDDEN : 1;
    if (isCap(run[0])) {   // disc / annulus: planar chart in its own plane
      const {s0, r0, s1, r1, o} = seg2(run[0]), c = newChart(part, kk), nrm = mul(F.w, Math.sign(o[0]));
      const t = F.u, b = F.v, uv = p => [dot(p, t), dot(p, b)];
      const ring = (s, r) => r < 1e-6 ? null : Array.from({length: seg}, (_, i) => { const p = F.at(s, r, ang(i)); return cv(c, p, uv(p)); });
      const A = ring(s0, r0), B = ring(s1, r1), ax = (s) => { const p = F.at(s, 0, 0); return cv(c, p, uv(p)); };
      const a0 = A || ax(s0), b0 = B || ax(s1);
      for (let i = 0; i < seg; i++) { const j = (i + 1) % seg;
        if (!A) cface(c, [a0, B[j], B[i]], nrm, 'cap', mat); else if (!B) cface(c, [A[i], A[j], b0], nrm, 'cap', mat); else cface(c, [A[i], A[j], B[j], B[i]], nrm, 'cap', mat); }
      continue;
    }
    const c = newChart(part, kk), pts = [run[0], ...run.map(k => (k + 1) % n)], rMax = Math.max(...pts.map(k => profile[k][1]));
    let v = 0;
    const rings = pts.map((k, j) => { if (j) v += seg2(run[j - 1]).len; const [s, r] = profile[k];
      return r < 1e-6 ? [cv(c, F.at(s, 0, 0), [0, v])] : Array.from({length: seg + 1}, (_, i) => cv(c, F.at(s, r, ang(i)), [(i - seg / 2) * 2 * Math.PI * (GRADIENT.has(mat) ? rMax : r) / seg, v])); });
    run.forEach((k, j) => { const A = rings[j], B = rings[j + 1], {o} = seg2(k);
      for (let i = 0; i < seg; i++) { const nn = normal(o, 2 * Math.PI * (i + 0.5) / seg);
        if (A.length === 1) cface(c, [A[0], B[i + 1], B[i]], nn, 'side', mat); else if (B.length === 1) cface(c, [A[i], A[i + 1], B[0]], nn, 'side', mat);
        else cface(c, [A[i], A[i + 1], B[i + 1], B[i]], nn, 'side', mat); } });
  }
}
// loft through rings (equal point counts, consistent order): one developed side chart (u = arc along each ring, centred;
// GRADIENT materials: arc fraction x the largest perimeter, a rectangle; v = distance between ring centres), planar cap charts. opts: caps [front, back] texel factors (0 = no cap), open (the
// closing ring segment n-1 -> 0 becomes its own hidden chart), mat.
function loft(part, rings, {mat = part.mat, caps = [1, 1], open = false, bands = null} = {}) {
  // bands: ring segment ranges [a, b) (indices wrap round the ring) that get their own chart and texel density k (a detail
  // panel that needs more texels), optionally a planar projection proj(point) -> [u, v] (straight lines of a pattern stay
  // on texel rows); by default one chart round the whole ring. Currently unused (the engraved action sides were removed).
  const N = rings[0].length, cen = rings.map(r => mul(r.reduce((a, p) => add(a, p), [0, 0, 0]), 1 / r.length));
  const quad = (ch, q, k) => { const P = q.map(id => part.v[id]), ctr = mul(P.reduce((a, p) => add(a, p), [0, 0, 0]), 0.25); cface(ch, q, sub(ctr, mul(add(cen[k], cen[k + 1]), 0.5)), 'side', mat); };
  for (const {a: s0, b: s1, k: kk = 1, proj} of bands || [{a: 0, b: open ? N - 1 : N}]) {
    const c = newChart(part, kk), P = (r, i) => r[i % N];
    const arcs = rings.map(r => { const a = [0]; for (let i = s0 + 1; i <= s1; i++) a.push(a.at(-1) + Math.hypot(...sub(P(r, i), P(r, i - 1)))); return a; });
    const width = Math.max(...arcs.map(a => a.at(-1)));
    let v = 0;
    const ids = rings.map((r, k) => { if (k) v += Math.hypot(...sub(cen[k], cen[k - 1]));
      return arcs[k].map((a, j) => cv(c, P(r, s0 + j), proj ? proj(P(r, s0 + j)) : [GRADIENT.has(mat) ? (a / arcs[k].at(-1) - 0.5) * width : a - arcs[k].at(-1) / 2, v])); });
    for (let k = 0; k + 1 < rings.length; k++) for (let j = 0; j < s1 - s0; j++) quad(c, [ids[k][j], ids[k][j + 1], ids[k + 1][j + 1], ids[k + 1][j]], k);
  }
  if (open) {   // closing faces (e.g. the forend top, tucked under the barrels)
    const h = newChart(part, HIDDEN); let w = 0;
    const cl = rings.map((r, k) => { if (k) w += Math.hypot(...sub(cen[k], cen[k - 1])); return [cv(h, r[N - 1], [0, w]), cv(h, r[0], [Math.hypot(...sub(r[0], r[N - 1])), w])]; });
    for (let k = 0; k + 1 < rings.length; k++) quad(h, [cl[k][0], cl[k][1], cl[k + 1][1], cl[k + 1][0]], k);
  }
  for (const [e, k, dir] of [[0, 0, sub(cen[0], cen[1])], [1, rings.length - 1, sub(cen.at(-1), cen.at(-2))]]) if (caps[e]) capChart(part, cen[k], rings[k], norm(dir), mat, caps[e]);
}
// rectangle swept along a path in the x = 0 plane ((z, y) points): width w across x, thickness t in the path plane; the
// ends are buried in the action / wood
function sweep(part, pts, w, t) {
  loft(part, pts.map(([z, y], i) => {
    const a = pts[Math.max(0, i - 1)], b = pts[Math.min(pts.length - 1, i + 1)], T = norm([b[0] - a[0], b[1] - a[1]]), N = [-T[1], T[0]];
    return [[-w / 2, -t / 2], [w / 2, -t / 2], [w / 2, t / 2], [-w / 2, t / 2]].map(([x, d]) => [x, y + N[1] * d, z + N[0] * d]);
  }), {caps: [HIDDEN, HIDDEN]});
}
// superellipse ring in the plane z: half widths hw (x) / hh (y) around y = yc, exponent p (2 = ellipse, larger = boxier);
// starts at the bottom centre line (the loft chart seam)
const superRing = (z, yc, hw, hh, n, p) => Array.from({length: n}, (_, i) => { const a = 2 * Math.PI * (i + 0.5) / n - Math.PI / 2, c = Math.cos(a), s = Math.sin(a);
  return [hw * Math.sign(c) * Math.abs(c) ** (2 / p), yc + hh * Math.sign(s) * Math.abs(s) ** (2 / p), z]; });
const arc = (cz, cy, r, d0, d1, n) => Array.from({length: n + 1}, (_, i) => { const d = (d0 + (d1 - d0) * i / n) * Math.PI / 180; return [cz + r * Math.cos(d), cy + r * Math.sin(d)]; });

// ---------------- barrels ----------------
const barrelFrame = side => { const b = [side * BARREL.breech[0], BARREL.breech[1], BZ], m = [side * BARREL.muzzle[0], BARREL.muzzle[1], MZ];
  return {F: frame(b, norm(sub(m, b)), BZ), L: Math.hypot(...sub(m, b))}; };
for (const [side, name] of [[1, 'barrel_right'], [-1, 'barrel_left']]) {
  const {F, L} = barrelFrame(side);
  // breech annulus (chamber rim recess .. 0.60, the monoblock takes over outside), tube hidden in the monoblock, tapering
  // barrel, crown, then back through the bore (dark end disc 2.6 behind the muzzle) and the chamber (end disc 5.6 deep)
  lathe(P(name, 'barrels', 'blued'), F, [
    [0, RIM, 'chamber'], [0, 0.60, 'blued'], [0.03, 0.60, 'blued!'], [0.03, 0.70, 'blued!'], [3.02, 0.70, 'blued!'],
    [5.0, 0.665, 'blued'], [9.0, 0.605, 'blued'], [15.0, 0.545, 'blued'], [21.0, 0.515, 'blued'], [L - 0.07, 0.50, 'blued'], [L, 0.47, 'blued'], [L, BORE, 'blued'],
    [L - MUZZLE_DEPTH, BORE, 'chamber'], [L - MUZZLE_DEPTH, 0, 'boreCap'], [CHAMBER_DEPTH, 0], [CHAMBER_DEPTH, BORE, 'boreCap'],
    [3.6, BORE, 'chamber'], [3.25, CHAMBER, 'chamber'], [RIM_DEPTH, CHAMBER, 'chamber'], [RIM_DEPTH, RIM, 'chamber']], SEG);
}
// monoblock: stadium around both breech ends; its holes are the barrels' own breech rings (seamless breech face)
{
  const [cx, cy] = BARREL.breech, r = BAR_W - BARREL.breech[0];   // sides flush with the bar
  const outer = [...arc(cx, cy, r, -90, 90, 12), ...arc(-cx, cy, r, 90, 270, 12)];
  const holes = [1, -1].map(side => { const {F} = barrelFrame(side); return Array.from({length: SEG}, (_, i) => F.at(0, 0.60, 2 * Math.PI * i / SEG).slice(0, 2)); });
  ext(P('monoblock', 'barrels', 'blued'), 'z', outer, holes, 0.98, BZ, 0);
}
// lumps with the hinge hook (seat on the pin from above and behind; hidden in the solid bar while closed)
{
  const [, py, pz] = PIN;
  ext(P('lumps', 'barrels', 'blued'), 'x', [[3.6, 7.35], [3.9, 7.55], [3.9, 8.32], [1.16, 8.32], ...arc(pz, py, PIN_R + 0.02, 100, -45, 5)], [], -0.42, 0.42, 0.03);
}
// ribs: flat matte top rib (sight plane) and the lower joining rib
{
  const ribRing = (z, hw, y0, y1) => [[-hw, y0, z], [hw, y0, z], [hw, y1 - 0.03, z], [hw - 0.03, y1, z], [-hw + 0.03, y1, z], [-hw, y1 - 0.03, z]];
  loft(P('top_rib', 'barrels', 'rib'), [[3.95, 0.17], [0.98, 0.16], [-8, 0.135], [-21.35, 0.11]].map(([z, hw]) => ribRing(z, hw, 9.35, RIB_Y)), {caps: [1, 1]});
  const yOf = z => 8.55 + 0.25 * (0.98 - z) / (0.98 - MZ);   // recessed in the valley between the barrels
  loft(P('bottom_rib', 'barrels', 'blued'), [0.98, -8, -21.35].map(z => ribRing(z, 0.14, yOf(z), yOf(z) + 0.18)), {caps: [HIDDEN, 1]});   // top stays inside the barrel walls, clear of the bores
}
// brass bead on the rib (top at y 9.885 = the ADS aim height)
lathe(P('bead', 'barrels', 'brass'), frame([0, RIB_Y, -21.2], [0, 1, 0]), [[0, 0], [0, 0.085], [0.04, 0.078], [0.07, 0.052], [0.085, 0]], 10);
// extractor: plate between the chambers, its sides following the rim recesses, face 0.005 proud of the breech face
{
  const side = Array.from({length: 9}, (_, i) => { const y = 8.62 + 0.96 * i / 8, d = RIM + 0.005, dy = y - BARREL.breech[1];
    return [Math.min(0.34, Math.abs(dy) < d ? BARREL.breech[0] - Math.sqrt(d * d - dy * dy) : 0.34), y]; });
  ext(P('extractor', 'extractor', 'polished'), 'z', [...side, ...side.slice().reverse().map(([x, y]) => [-x, y])], [], 3.72, BZ + 0.005, 0.02);
}

// chamber shells: 12 Gauge 2 3/4" shells at gun scale (rim r 0.50 x 0.06 sits in the rim recess, head r 0.455, hull r 0.45,
// length 3.12), coaxial with each barrel, base 0.008 inside the breech face. Live: closed star crimp; spent: open mouth
// with a dark inside. The renderer shows them by chamber state; the reload clips move them by hand.
export const SHELL = {base: 0.008, len: 3.12, rim: 0.50};
for (const [side, sname] of [[1, 'right'], [-1, 'left']]) {
  const {F} = barrelFrame(side), b = SHELL.base, e = SHELL.len;
  const head = [[b, 0], [b, 0.09, 'shellPrimer'], [b, SHELL.rim, 'shellBrass'], [b + 0.06, SHELL.rim, 'shellBrass'], [b + 0.06, 0.455, 'shellBrass'], [0.55, 0.455, 'shellBrass'],
    [0.55, 0.45, 'shellHull'], [e - 0.07, 0.45, 'shellHull']];
  lathe(P(`shell_live_${sname}`, `live_shell_${sname}`, 'shellHull'), F, [...head, [e - 0.02, 0.41, 'shellHull'], [e, 0.30, 'shellCrimp'], [e, 0, 'shellCrimp']], 16);
  lathe(P(`shell_spent_${sname}`, `spent_shell_${sname}`, 'shellHull'), F, [...head, [e, 0.455, 'shellHull'], [e, 0.40, 'shellHull'], [e - 0.35, 0.40, 'shellInner'], [e - 0.35, 0, 'shellInner']], 16);
}

// ---------------- forend (walnut) + forend iron ----------------
export const FOREND = [[0.30, 6.99, 1.36], [-0.5, 7.02, 1.35], [-2.5, 7.12, 1.28], [-5.0, 7.30, 1.16], [-6.6, 7.45, 1.05], [-7.2, 7.60, 0.95], [-7.45, 7.85, 0.80]];   // z, bottom y, half width
const FOREND_TOP = 8.75;   // tucked up between and against the barrels
const forendRing = (z, yb, hw, top = FOREND_TOP, n = 16) => Array.from({length: n}, (_, i) => { const a = Math.PI + Math.PI * i / (n - 1), c = Math.cos(a), s = Math.sin(a);
  return [hw * Math.sign(c) * Math.abs(c) ** 0.8, top + (top - yb) * Math.sign(s) * Math.abs(s) ** 0.8, z]; });
loft(P('forend_wood', 'forend', 'wood'), FOREND.slice().reverse().map(([z, yb, hw]) => forendRing(z, yb, hw)), {caps: [1, HIDDEN], open: true});
{
  // iron: rear face concentric with the knuckle around the hinge pin (clears it while the barrels swing), front lip over the wood
  const knuckleZ = y => PIN[2] - (y < PIN[1] ? Math.sqrt(IRON_R ** 2 - (y - PIN[1]) ** 2) : IRON_R);
  loft(P('forend_iron', 'forend', 'blued'), [forendRing(0, 6.98, 1.37, FOREND_TOP - 0.02).map(([x, y]) => [x, y, knuckleZ(y)]), forendRing(0.20, 6.985, 1.37, FOREND_TOP - 0.02)], {caps: [1, HIDDEN], open: true});
}

// ---------------- action ----------------
{
  // body: lofted cross-sections from the fences (top corners concentric with the barrels, 0.04 proud of the monoblock) back
  // to a rounded box on the top strap; the standing breech is its front face
  const body = ([z, R, top, hw]) => [[-hw + 0.08, 6.90, z], [hw - 0.08, 6.90, z], [hw, 6.98, z],
    ...arc(hw - R, top - R, R, 0, 90, 8).map(([x, y]) => [x, y, z]), ...arc(-(hw - R), top - R, R, 90, 180, 8).map(([x, y]) => [x, y, z]), [-hw, 6.98, z]];
  loft(P('action_body', 'receiver', 'silver'), [[BZ, 0.78, 9.78, ACTION_W], [4.3, 0.70, 9.77, ACTION_W], [4.8, 0.50, 9.75, ACTION_W], [5.6, 0.38, 9.72, ACTION_W],
    [6.6, 0.34, 9.63, 1.49], [9.0, 0.30, 9.54, 1.46]].map(body), {caps: [1, HIDDEN]});
  // bar: knuckle arc round the hinge pin, flats under the barrels; 0.06 narrower than the body and running 0.4 into it
  ext(P('action_bar', 'receiver', 'silverBar'), 'x', [...arc(PIN[2], PIN[1], KNUCKLE_R, 270, 180, 6), [PIN[2] - KNUCKLE_R, 8.28], [BZ + 0.4, 8.28], [BZ + 0.4, 6.92]], [],
    -BAR_W, BAR_W, 0.06);
  const pin = frame([-BAR_W - 0.02, PIN[1], PIN[2]], [1, 0, 0]), len = 2 * BAR_W + 0.04;
  lathe(P('hinge_pin', 'receiver', 'silver'), pin, [[0, 0], [0, PIN_R - 0.03], [0.03, PIN_R], [len - 0.03, PIN_R, 'silver!'], [len, PIN_R - 0.03], [len, 0]], 12);
  // top strap / tang along the wrist, inletted with its top 0.03 above the wood
  ext(P('tang', 'receiver', 'silver'), 'x', [[9.0, 9.54], [9.6, 9.52], [10.5, 9.43], [11.4, 9.33], [12.2, 9.18], [12.45, 9.13], [12.52, 9.07], [12.4, 9.0],
    [11.4, 9.13], [10.5, 9.23], [9.6, 9.30], [9.0, 9.32]], [], -0.42, 0.42, 0.03);
  ext(P('safety', 'receiver', 'polished'), 'x', [[11.6, 9.27], [12.0, 9.195], [12.0, 9.29], [11.93, 9.33], [11.65, 9.38], [11.6, 9.36]], [], -0.13, 0.13, 0);
}
// top lever: flat blade on the strap (bone pivot = its spindle), thumb piece to the right; the blade follows the strap slope
// and the thumb piece bends down toward the wrist (vertex deformation, the spindle stays vertical)
{
  const part = P('top_lever', 'top_lever', 'polished');
  ext(part, 'y', [...arc(9.45, 0, 0.30, 110, 250, 4), [9.75, -0.25], [10.35, -0.08], [10.95, 0.22], [11.2, 0.35], [11.32, 0.55], [11.28, 0.75], [11.12, 0.86],
    [10.92, 0.86], [10.72, 0.72], [10.35, 0.40], [9.85, 0.26]], [], 9.53, 9.69, 0.02);
  for (const q of part.v) q[1] += -0.075 * Math.max(0, q[2] - 9.3) - 0.10 * Math.max(0, (q[0] - 0.25) / 0.6) ** 2;
}
// single selective trigger (2026-10-06, was twin triggers): the gun fires one barrel per pull, right then left (SEMI), so one
// blade, centred in the guard bow (between the old front and rear blades); the bone pivot stays put
{
  const part = P('triggers', 'triggers', 'polished'), blade = [[6.95, 6.95], [6.92, 6.60], [7.02, 6.30], [7.25, 6.10], [7.38, 6.14], [7.18, 6.34], [7.12, 6.62], [7.20, 6.95]];
  ext(part, 'x', blade.map(([z, y]) => [z + 0.52, y]), [], -0.09, 0.09, 0);
}

// ---------------- stock (walnut) + butt pad ----------------
// z, top y, bottom y, half width, superellipse exponent: head flush with the action, slim wrist, half pistol grip, straight
// comb to the heel (drop at heel 2.9 below the sight line)
export const STOCK = [[8.95, 9.52, 6.90, 1.46, 5.0], [9.6, 9.49, 6.92, 1.26, 4.0], [10.5, 9.40, 6.95, 1.02, 3.0], [11.4, 9.30, 6.85, 0.92, 2.6],
  [12.2, 9.15, 6.45, 0.90, 2.6], [13.0, 8.97, 5.80, 0.93, 2.6], [13.7, 8.84, 5.35, 0.96, 2.6], [14.3, 8.74, 5.30, 0.99, 2.6], [15.2, 8.58, 5.05, 1.01, 2.6],
  [16.5, 8.35, 4.45, 1.03, 2.6], [18.5, 8.00, 3.45, 1.02, 2.6], [20.5, 7.64, 2.50, 1.00, 2.6], [22.5, 7.28, 1.70, 0.98, 2.6], [24.2, 6.95, 1.10, 0.96, 2.6]];
const stockRing = ([z, t, b, hw, p], k = 1) => superRing(z, (t + b) / 2, hw * k, (t - b) / 2 * k, 28, p);
loft(P('stock', 'stock', 'wood'), STOCK.map(s => stockRing(s)), {caps: [HIDDEN, HIDDEN]});
{
  const e = STOCK.at(-1), at = (z, k) => stockRing([z, ...e.slice(1)], k);
  loft(P('butt_pad', 'stock', 'rubber'), [at(24.18, 1.012), at(24.36, 1.012), at(24.40, 0.985), at(24.48, 0.985), at(24.52, 1.012), at(24.64, 1.012), at(24.68, 0.985),
    at(24.76, 0.985), at(24.80, 1.0), at(24.88, 0.97)], {caps: [HIDDEN, 1]});
}
// trigger guard: bow from the action floor round the trigger, tang along the wrist
sweep(P('trigger_guard', 'receiver', 'silver'), [[5.95, 6.95], [6.15, 6.55], [6.5, 6.08], [7.1, 5.80], [7.8, 5.74], [8.5, 5.82], [9.1, 6.06], [9.55, 6.45], [9.85, 6.85],
  ...[10.2, 10.8, 11.4, 12.0, 12.5].map(z => [z, lerpTable(STOCK, z, 2) + 0.02])], 0.40, 0.13);

// ---------------- rig (Blockbench source frame) ----------------
// Hands (idle = bind pose; every clip starts from these). Anchor = distal cap of the Vanilla arm box, forearm along local
// -Y, box half width 2*.62/.45 = 2.76 at this gun's first-person scale. Built from the forearm direction (hand -> elbow)
// and the palm normal:
//   right: palm up under the wrist just behind the trigger guard, the box's top face 0.15 into the wood (the grip rests
//          on the fist instead of passing through it); forearm back, down and right, a diagonal from the lower-right
//          corner of the view;
//   left:  palm up under the middle of the forend, the box's top face 0.2 into the wood so the forend visibly rests on
//          the hand; forearm back, down and a little left.
const eulerZYX = R => [Math.atan2(R[2][1], R[2][2]), Math.asin(Math.max(-1, Math.min(1, -R[2][0]))), Math.atan2(R[1][0], R[0][0])].map(a => +(a * 180 / Math.PI).toFixed(3));
const r4 = v => +v.toFixed(4);
function armRot(forearm, palm) {
  const ey = norm(forearm).map(v => -v), pn = norm(palm), d = pn.reduce((s, v, i) => s + v * ey[i], 0), ez = norm(pn.map((v, i) => v - ey[i] * d)), ex = cross(ey, ez);
  return [0, 1, 2].map(r => [ex[r], ey[r], ez[r]]);
}
export const ARM_HW = 2 * 0.62 / 0.45;
const LEFT_ARM = armRot([-0.55, -0.42, 0.72], [0.2, 1, 0]), FOREND_MID = -3.4;
const RIGHT_ARM = armRot([0.30, -0.62, 0.72], [-0.1, 1, 0]), WRIST_Z = 11.6;
export const HANDS = {
  right: {pos: [0.15, lerpTable(STOCK, WRIST_Z, 2) + 0.15, WRIST_Z].map((v, i) => r4(v - RIGHT_ARM[i][2] * ARM_HW)), rot: eulerZYX(RIGHT_ARM)},
  left: {pos: [0, lerpTable(FOREND.slice().reverse(), FOREND_MID, 1) + 0.2, FOREND_MID].map((v, i) => r4(v - LEFT_ARM[i][2] * ARM_HW)), rot: eulerZYX(LEFT_ARM)},
};
const [bx, by] = BARREL.breech, [mx, my] = BARREL.muzzle, GRIP = [0, 7.7, 11.4];
// name, parent, origin, rotation
export const RIG = [
  ['camera', null, [-4, 14, 33]],
  ['root', null, GRIP],
  ['handling', 'root', GRIP],
  ['gun_body', 'handling', GRIP],
  ['receiver', 'gun_body', [0, 8.3, 6.5]],
  ['top_lever', 'receiver', [0, 9.53, 9.45]],
  ['triggers', 'receiver', [0, 6.90, 7.10]],
  ['stock', 'gun_body', GRIP],
  ['barrels', 'gun_body', PIN],
  ['forend', 'barrels', PIN],
  ['extractor', 'barrels', [0, 9.1, BZ]],
  ['chamber_right', 'extractor', [bx, by, BZ]],
  ['live_shell_right', 'chamber_right', [bx, by, BZ]],
  ['spent_shell_right', 'chamber_right', [bx, by, BZ]],
  ['chamber_left', 'extractor', [-bx, by, BZ]],
  ['live_shell_left', 'chamber_left', [-bx, by, BZ]],
  ['spent_shell_left', 'chamber_left', [-bx, by, BZ]],
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
// walnut: dark oiled walnut, grain along the length with a wavy figure and a few soft near-black streaks; low frequency only
// (no per-pixel noise). No checkering: a painted panel read as a stain in game (2026-09-29), the wood stays plain.
const WOOD_LIGHT = [108, 70, 43], WOOD_DARK = [52, 33, 21], WOOD_STREAK = [34, 21, 14];
export const walnut = p => {
  const fig = vn3(p[0] * 0.8, p[1] * 0.8 + 0.4 * Math.sin(p[2] * 0.45), p[2] * 0.16), fig2 = vn3(p[0] * 0.6 + 7, p[1] * 0.5, p[2] * 0.11 + 3);
  const lines = 0.5 + 0.5 * Math.sin(p[1] * 5.0 + p[0] * 1.2 + 3.2 * fig);
  const streak = sm(0.84, 0.97, 0.5 + 0.5 * Math.sin(p[1] * 7.0 + p[0] * 1.1 + 6.5 * fig2 + p[2] * 0.08)) * sm(0.2, 0.55, fig2);
  return mix(mix(WOOD_LIGHT, WOOD_DARK, 0.15 + 0.55 * fig + 0.22 * lines), WOOD_STREAK, 0.55 * streak);
};
// chambers and bore: bright machined steel at the openings, fading to dark with depth (breech face z 4 / muzzle z -21.5)
export const bore = p => mix([80, 83, 88], [14, 14, 16], sm(0.1, 2.4, Math.min(BZ - p[2], p[2] - MZ)));

// satin silver (coin finish) action: plain, the look comes from the PBR response (metal F0, satin smoothness)
const SILVER = [128, 130, 134];
// the bar is solid; its top carries the lump slot as a dark opening (only visible with the gun open), so the lumps swing
// up out of a slot rather than out of bare metal
export const barSlot = (p, n) => {
  const k = n[1] > 0.9 ? sm(-0.02, 0.02, Math.min(0.44 - Math.abs(p[0]), p[2] - 0.94, 3.96 - p[2])) : 0;
  return {c: mix(SILVER, [12, 12, 13], k), sm: 135 - 95 * k, ao: 1 - 0.5 * k};
};
export const MATS = {   // Base Color (sRGB) or function, bevel highlight, smoothness open / edge, F0 (24 coating, 10 dielectric, 255 metal)
  blued:    {c: [31, 34, 39], hl: 20, sm: 150, se: 178, f0: 24, edgeF0: 255},
  silver:   {c: SILVER, hl: 18, sm: 135, se: 165, f0: 255},   // satin coin finish, not a mirror: large metal panels glare under shaders
  silverBar: {c: barSlot, hl: 18, sm: 135, se: 165, f0: 255},
  shellHull:   {c: [127, 35, 42], hl: 0, sm: 100, se: 100, f0: 10},    // 12 Gauge colours / LabPBR from the ammo assets
  shellCrimp:  {c: [112, 30, 37], hl: 0, sm: 90, se: 90, f0: 10},
  shellInner:  {c: [34, 12, 14], hl: 0, sm: 60, se: 60, f0: 10},
  shellBrass:  {c: [169, 132, 68], hl: 0, sm: 150, se: 150, f0: 255},
  shellPrimer: {c: [178, 187, 181], hl: 0, sm: 125, se: 125, f0: 255},
  rib:      {c: [26, 27, 30], hl: 10, sm: 70, se: 110, f0: 24, edgeF0: 255},
  polished: {c: [74, 76, 80], hl: 24, sm: 180, se: 195, f0: 255},
  brass:    {c: [182, 142, 72], hl: 20, sm: 150, se: 170, f0: 255},
  chamber:  {c: bore, hl: 0, sm: 185, se: 185, f0: 255},
  boreCap:  {c: [10, 10, 11], hl: 0, sm: 40, se: 40, f0: 24},
  wood:     {c: walnut, hl: 0, sm: 120, se: 120, f0: 10},
  rubber:   {c: [22, 22, 23], hl: 0, sm: 38, se: 38, f0: 10},
};
const ATLAS = 1024, PAD = 1;
// planar islands of the extruded parts (shared unwrap; the monoblock chamber walls are buried in the barrel tubes) + the
// developed lathe / loft charts, packed together at the largest uniform density (long side horizontal)
const charted = new Set(CHARTS.map(c => c.part));
const planar = unwrap(PARTS.filter(p => !charted.has(p)), {atlas: 1 << 20, pad: PAD, startS: 1, stepS: 1}).islands;
for (const is of planar) is.k = is.part.name === 'monoblock' && is.faces.every(({f}) => f.tag === 'wall') ? HIDDEN : 1;
const islands = [...planar, ...CHARTS.filter(c => c.faces.length).map(c => {
  const used = new Set(c.faces.flatMap(f => f.ids)), uv = new Map([...c.uv].filter(([id]) => used.has(id)));
  return {part: c.part, k: c.k, uv, faces: c.faces.map(f => ({f, n: norm(newell(f.ids.map(i => c.part.v[i])))}))};
})];
for (const is of islands.slice(planar.length)) {
  const box = () => { const us = [...is.uv.values()]; is.u0 = Math.min(...us.map(a => a[0])); is.v0 = Math.min(...us.map(a => a[1]));
    is.w = Math.max(...us.map(a => a[0])) - is.u0; is.h = Math.max(...us.map(a => a[1])) - is.v0; };
  box(); if (is.h > is.w) { for (const [k, [u, v]] of is.uv) is.uv.set(k, [v, -u]); box(); }
}
// skyline bottom-left packing (tallest first): each island takes a cell W + PAD wide / H + PAD tall, PAD margin round the atlas
function pack(S) {
  let sky = [{x: PAD, y: PAD, w: ATLAS - PAD}];
  const order = islands.map(is => ({is, W: Math.ceil(is.w * S * is.k) + 1, H: Math.ceil(is.h * S * is.k) + 1})).sort((p, q) => q.H - p.H || q.W - p.W);
  for (const {is, W, H} of order) {
    const cw = W + PAD, ch = H + PAD; let best = null;
    for (let i = 0; i < sky.length; i++) {
      const x = sky[i].x; if (x + cw > ATLAS) break;
      let y = 0; for (let j = i; j < sky.length && sky[j].x < x + cw; j++) y = Math.max(y, sky[j].y);
      if (y + ch <= ATLAS && (!best || y + ch < best.top || y + ch === best.top && x < best.x)) best = {x, y, top: y + ch};
    }
    if (!best) return false;
    Object.assign(is, {px: best.x, py: best.y, W, H});
    const next = [];
    for (const g of sky) {
      if (g.x + g.w <= best.x || g.x >= best.x + cw) { next.push(g); continue; }
      if (g.x < best.x) next.push({x: g.x, y: g.y, w: best.x - g.x});
      if (g.x + g.w > best.x + cw) next.push({x: best.x + cw, y: g.y, w: g.x + g.w - best.x - cw});
    }
    next.push({x: best.x, y: best.top, w: cw}); next.sort((p, q) => p.x - q.x);
    sky = next.reduce((m, g) => { const l = m.at(-1); if (l && l.y === g.y && l.x + l.w === g.x) l.w += g.w; else m.push({...g}); return m; }, []);
  }
  return true;
}
let S = 64; while (!pack(S)) S -= 0.25;
// island extents map exactly onto the centres of their first and last texel columns / rows (a hair above S): border texels are
// always painted by the faces themselves, so no gutter colour shows along chart seams of gradient materials (bore, walnut)
for (const is of islands) { is.sx = is.w > 0 ? (is.W - 1) / is.w : 0; is.sy = is.h > 0 ? (is.H - 1) / is.h : 0; }
const uvOf = (is, vi) => { const [u, v] = is.uv.get(vi); return [(u - is.u0) * is.sx + is.px + 0.5, (v - is.v0) * is.sy + is.py + 0.5]; };
const faceUV = new Map(PARTS.map(p => [p, new Map()]));
for (const is of islands) for (const {f} of is.faces) faceUV.get(is.part).set(f, f.ids.map(i => uvOf(is, i)));
for (const p of PARTS) assert(p.f.every(f => faceUV.get(p).has(f)), 'unmapped face in ' + p.name);
const DUMMY = 'data:image/png;base64,' + png(Buffer.from([0, 0, 0, 255]), 1, 1).toString('base64');
const painted = paint({PARTS, islands, S, uvOf, atlas: ATLAS, pad: PAD, MATS, ZONED: new Set(), groupInfo: new Map(),
  sourceGroups: () => [], refTexture: {source: DUMMY}, refUvWidth: 1, background: {c: [34, 36, 40, 255], s: [140, 24, 0, 255], n: [128, 128, 255, 255]}});
export const PNG = painted.PNG, UV = {islands, S}, ALL_PARTS = PARTS;

// ---------------- source (Free Model) ----------------
const uuid = s => { const h = createHash('sha256').update('afl-silverwood-12-v3:' + s).digest('hex'); return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-8${h.slice(17, 20)}-${h.slice(20, 32)}`; };
const r12 = v => +v.toFixed(12) || 0;
// ---------------- animations (tools/silverwood-12-v3-animations.mjs), baked at 60 Hz ----------------
// Runtime sign convention: rotation x / y and position x flip; step (visibility) scale keys hold with the 'afl_hold' easing.
const ANIM_PATH = 'src/main/resources/assets/apocalypse_firstlight/animations/silverwood_12.animation.json', NS = 'apocalypse_firstlight:silverwood_12_';
const CLIPS = silverwoodClips({rig: RIG, OUT: norm(sub(BARREL.breech.map((v, i) => i ? v : 0), BARREL.muzzle.map((v, i) => i ? v : 0))), BZ});
const round4 = v => +v.toFixed(4) || 0;
const BAKED = CLIPS.map(c => {
  const tracks = [];   // [bone, channel, keys, step]
  for (const [bone, chs] of Object.entries(c.tracks)) for (const ch of ['rotation', 'position', 'scale']) if (chs[ch])
    tracks.push([bone, ch, bake(chs[ch], c.length, {step: ch === 'scale' && !!chs.step, rotRef: ch === 'rotation' ? chs[ch](0) : null}), ch === 'scale' && !!chs.step]);
  const handInfo = {};
  for (const [bone, cands] of Object.entries(c.hands)) {
    const h = bakeHand(cands, c.length);
    tracks.push([bone, 'rotation', h.rotation, false], [bone, 'position', h.position, false]);
    if (h.pre.some(v => v)) tracks.push([bone + '_pos', 'rotation', [[0, h.pre]], false]);
    handInfo[bone] = {pre: h.pre, worstStep: +h.worst.toFixed(2)};
  }
  return {...c, baked: tracks, handInfo};
});
const runtimeClip = c => {
  const out = {...(c.loop === 'hold' ? {loop: 'hold_on_last_frame'} : c.loop === 'loop' ? {loop: true} : {}), animation_length: c.length, bones: {}};
  for (const [bone, ch, keys, step] of c.baked) {
    const sign = ch === 'rotation' ? [-1, -1, 1] : ch === 'position' ? [-1, 1, 1] : [1, 1, 1];
    (out.bones[bone] ||= {})[ch] = Object.fromEntries(keys.map(([t, v], i) => [t > 0 && Number.isInteger(t) ? t.toFixed(1) : String(t),
      {vector: v.map((x, k) => round4(x * sign[k])), ...(step && i ? {easing: 'afl_hold'} : {})}]));
  }
  if (c.sounds.length) out.sound_effects = Object.fromEntries(c.sounds.slice().sort((a, b) => a[0] - b[0]).map(([t, e]) => [String(round4(t)), {effect: NS + e}]));
  if (c.timeline.length) out.timeline = Object.fromEntries(c.timeline.map(([t, cue]) => [String(round4(t)), cue]));
  return out;
};
export function buildSource({referenceArmsExported = false} = {}) {
  const groups = RIG.map(([name, , origin, rot]) => ({name, uuid: uuid('group:' + name), export: true, locked: false, scope: 0, selected: false, visibility: true,
    _static: {properties: {}, temp_data: {}}, origin: origin.slice(), rotation: (rot || [0, 0, 0]).slice(), color: 0, children: [], reset: false, shade: true,
    mirror_uv: false, autouv: 0, isOpen: true, primary_selected: false}));
  const nodes = new Map(RIG.map(([name]) => [name, {uuid: uuid('group:' + name), isOpen: true, children: []}]));
  const outliner = [];
  for (const [name, parent] of RIG) (parent ? nodes.get(parent).children : outliner).push(nodes.get(name));
  const elements = [];
  for (const p of PARTS) {
    const key = i => i.toString(36).padStart(4, '0'), vertices = {}, faces = {}, uvs = faceUV.get(p);
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
      from: [x - hw, y - len, z - hw].map(r4), to: [x + hw, y, z + hw].map(r4), autouv: 0, color: side === 'right' ? 4 : 1, visibility: true, origin: [0, 0, 0],
      faces: Object.fromEntries(['north', 'east', 'south', 'west', 'up', 'down'].map(d => [d, {uv: [0, 0, 1, 1], texture: 0}])), type: 'cube', uuid: id});
    nodes.get(`${side}_hand_anchor`).children.push(id);
  }
  const groupId = name => uuid('group:' + name);
  const animations = BAKED.map(c => {
    const animators = {};
    for (const [bone, ch, keys, step] of c.baked) (animators[groupId(bone)] ||= {name: bone, type: 'bone', keyframes: []}).keyframes.push(...keys.map(([t, v]) => ({channel: ch,
      data_points: [{x: String(v[0]), y: String(v[1]), z: String(v[2])}], uuid: uuid(`${c.name}:${bone}:${ch}:${t}`), time: t, color: -1, interpolation: step ? 'step' : 'linear'})));
    const fx = [...c.sounds.map(([t, e]) => ({channel: 'sound', data_points: [{effect: NS + e, locator: '', file: ''}], uuid: uuid(`sound:${c.name}:${t}`), time: round4(t), color: -1, interpolation: 'linear'})),
      ...c.timeline.map(([t, cue]) => ({channel: 'timeline', data_points: [{script: cue}], uuid: uuid(`timeline:${c.name}:${t}`), time: round4(t), color: -1, interpolation: 'linear'}))];
    if (fx.length) animators.effects = {name: 'effects', type: 'effect', keyframes: fx};
    return {uuid: uuid('anim:' + c.name), name: c.name, loop: c.loop, override: false, length: c.length, snapping: FPS, selected: false, group_name: '', scope: 0,
      anim_time_update: '', blend_weight: '', start_delay: '', loop_delay: '', animators};
  });
  return {meta: {format_version: '5.0', model_format: 'free', box_uv: false}, name: 'silverwood_12', model_identifier: '', visible_box: [6, 2.5, 0.75],
    variable_placeholders: '', variable_placeholder_buttons: [], timeline_setups: [], unhandled_root_fields: {}, resolution: {width: ATLAS, height: ATLAS},
    elements, groups, outliner,
    textures: [{name: 'silverwood_12.png', relative_path: 'textures/silverwood_12.png', folder: '', namespace: '', id: '0', group: '', scope: 0,
      width: ATLAS, height: ATLAS, uv_width: ATLAS, uv_height: ATLAS, particle: false, use_as_default: false, layers_enabled: false, sync_to_project: '',
      file_format: 'png', render_mode: 'default', render_sides: 'auto', wrap_mode: 'limited', pbr_channel: 'color', fps: 7, frame_time: 1,
      frame_order_type: 'loop', frame_order: '', frame_interpolate: false, visible: true, internal: true, saved: true, uuid: uuid('texture'),
      source: 'data:image/png;base64,' + PNG[0].toString('base64')}],
    animations};
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
const animJson = {format_version: '1.8.0', animations: Object.fromEntries(BAKED.map(c => [c.name, runtimeClip(c)]))};

const bb = path.join(ROOT, 'src/main/blockbench'), assets = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight');
const outputs = [[path.join(bb, 'silverwood_12.bbmodel'), JSON.stringify(source)], [path.join(assets, 'geo/silverwood_12.geo.json'), JSON.stringify(geo, null, 2) + '\n'],
  [path.join(assets, 'meshes/silverwood_12.aflmesh.json'), meshText], [path.join(ROOT, ANIM_PATH), JSON.stringify(animJson, null, 2) + '\n'],
  ...['', '_s', '_n'].flatMap((k, i) => [[path.join(bb, `textures/silverwood_12${k}.png`), PNG[i]], [path.join(assets, `textures/item/silverwood_12${k}.png`), PNG[i]]])];

// coplanar audit: a chamber holds either its live or its spent shell (never both), so the gun is audited with each set
const faces = sidecar.parts.flatMap(p => p.faces), zfPairs = new Map();
for (const other of [/^shell_spent_/, /^shell_live_/]) for (const u of zFightLevels(PARTS, new Map(), {skip: p => other.test(p.name)}).unresolved) zfPairs.set(`${u.a}|${u.b}|${u.area.toFixed(6)}`, u);
const zf = {unresolved: [...zfPairs.values()]};
export const stats = {triangleEquivalent: faces.reduce((s, q) => s + q.length - 2, 0), quads: faces.filter(q => q.length === 4).length, triangles: faces.filter(q => q.length === 3).length,
  parts: sidecar.parts.length, texelsPerUnit: S, islands: islands.length, coplanarOverlaps: zf.unresolved.length,
  byPart: Object.fromEntries(sidecar.parts.map(p => [p.name, p.faces.reduce((s, q) => s + q.length - 2, 0)])), hands: HANDS,
  clips: Object.fromEntries(BAKED.map(c => [c.name, {length: c.length, keys: c.baked.reduce((n, t) => n + t[2].length, 0), hands: c.handInfo,
    sounds: c.sounds.map(([t, e]) => `${round4(t)} ${e}`), timeline: c.timeline.map(([t, e]) => `${round4(t)} ${e}`)}]))};
// the reload clips are the gameplay timeline: their lengths must equal the gun data's reload seconds, and the rounds are
// committed (presentation mag_in_tick) only after the authored thumb push has seated them
{
  const data = JSON.parse(fs.readFileSync(path.join(ROOT, 'src/main/resources/data/apocalypse_firstlight/native_guns/silverwood_12.json'), 'utf8'));
  const len = n => CLIPS.find(c => c.name === n).length;
  assert(data.reload.empty_seconds === len('reload_empty') && data.reload.tactical_seconds === len('reload_tactical'), 'reload seconds differ from the clip lengths');
  for (const [n, tick] of [['reload_empty', data.presentation.empty_mag_in_tick], ['reload_tactical', data.presentation.mag_in_tick]]) {
    const seat = CLIPS.find(c => c.name === n).sounds.find(s => s[1] === 'shell_insert')[0];
    assert(tick / 20 >= seat && tick / 20 <= seat + 0.2, `${n}: mag_in_tick ${tick} is not just after the shell push (${seat} s)`);
  }
}
const isMain = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  console.log(JSON.stringify(stats));
  if (zf.unresolved.length) console.log('COPLANAR', JSON.stringify(zf.unresolved.slice(0, 12)));
  if (process.argv.includes('--debug')) fs.writeFileSync(process.argv[process.argv.indexOf('--debug') + 1], JSON.stringify(buildSource({referenceArmsExported: true})));
  else if (process.argv.includes('--check')) {
    for (const [file, data] of outputs) { const cur = fs.existsSync(file) ? fs.readFileSync(file) : null; if (!cur || !cur.equals(Buffer.isBuffer(data) ? data : Buffer.from(data))) throw new Error('stale ' + path.relative(ROOT, file)); }
    console.log('CHECK OK');
  } else { for (const [file, data] of outputs) fs.writeFileSync(file, data); console.log('wrote ' + outputs.map(([f]) => path.relative(ROOT, f)).join(', ')); }
}
