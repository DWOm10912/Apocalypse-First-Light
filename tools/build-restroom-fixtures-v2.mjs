// Restroom Fixtures V2 (docs/models/restroom_fixtures_v2.md): the commercial toilet and the wall-hung lavatory redone at
// real size from the user's concept choice A (2026-10-08), and a new wall mirror. Dry by default (no water surface); the
// supply and waste connections sit on the cell's back (wall) face or floor, so AFL pipes can later run in the wall.
// - Toilet: floor-mounted elongated vitreous china bowl, exposed chrome flushometer (control stop from the wall, valve body,
//   handle to the wide side, vacuum breaker tube to the top spud), white open-front seat without a cover. Rim 420 mm,
//   720 mm from the wall.
// - Lavatory: wall-hung vitreous china, 520 x 460 mm, rim 840 mm (ADA max 865), 100 mm backsplash, single-lever faucet,
//   exposed chrome tailpiece, P-trap and trap arm into the wall, two angle stops with braided supplies.
// - Mirror: 18 x 30 in (457 x 762 mm) glass in a stainless channel frame, bottom edge 1.0 m above the floor when hung in
//   the cell above a lavatory (its own cell's floor). Mirror glass is LabPBR silver (237), smoothness 255: a shader
//   pack's reflections do the rest; without shaders it is a plain light silver grey.
// Pure Mesh + LabPBR atlases, exported as Forge OBJ block models (chunk-baked).
//   node tools/build-restroom-fixtures-v2.mjs                -> writes sources, OBJ / MTL / block + item models, blockstates, atlases
//   node tools/build-restroom-fixtures-v2.mjs --check        -> verifies every output is up to date
//   node tools/build-restroom-fixtures-v2.mjs --preview DIR  -> writes only OBJ + maps into DIR (offline review)
// Geometry is written in "concept" metres (the wall at z = 0, the fixture's front toward +z, y up from the floor, x across)
// and converted to the model frame: millimetres, then px (1 mm = 0.016 px), block bottom centre at the origin, front toward
// -Z (NORTH) and the wall at z = +8 px, as the V1 models and every AFL wall fixture.
import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {Part, extrude, unwrap, paint, png, readPng, zFightLevels, area2, add, sub, mul, dot, cross, norm, newell} from './cube-slab-mesh-lib.mjs';
import {heldDisplay} from './item-held-display.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
function assert(c, m) { if (!c) throw new Error(m); }
const PX = 0.016;   // px per mm
// concept metres (wall z = 0, front +z) -> model millimetres (wall z = +500, front -z)
const cv = ([x, y, z]) => [x * 1000, y * 1000, 500 - z * 1000];
const lerp = (a, b, t) => a + (b - a) * t, smooth = t => t * t * (3 - 2 * t);
const centroid = P => mul(P.reduce((a, p) => add(a, p), [0, 0, 0]), 1 / P.length);

// ---------------- geometry helpers (model-space points) ----------------
// A loft through rings of equal length; hint(c, k) gives the visible side for the quad around centroid c of band k.
function loft(part, rings, hint, {closed = true, tag = 'side'} = {}) {
  const ids = rings.map(r => r.map(p => part.vtx(p))), n = rings[0].length, last = closed ? n : n - 1;
  for (let k = 0; k + 1 < rings.length; k++) for (let i = 0; i < last; i++) {
    const j = (i + 1) % n, q = [ids[k][i], ids[k][j], ids[k + 1][j], ids[k + 1][i]];
    const P = q.map(id => part.v[id]);
    if (Math.hypot(...newell(P)) < 1e-9) continue;
    part.face(q, hint(centroid(P), k), tag);
  }
  return ids;
}
// fan a closed ring to its centre point
function cap(part, ring, normal, tag = 'cap') {
  const c = part.vtx(centroid(ring)), ids = ring.map(p => part.vtx(p));
  for (let i = 0; i < ring.length; i++) part.face([c, ids[i], ids[(i + 1) % ring.length]], normal, tag);
}
// cylinder between two points (closed ends)
function cyl(part, a, b, r0, r1 = r0, seg = 20, ends = true) {
  const ax = norm(sub(b, a)), t0 = Math.abs(ax[1]) < 0.9 ? [0, 1, 0] : [1, 0, 0], u = norm(cross(ax, t0)), w = cross(ax, u);
  const ring = (c, r) => Array.from({length: seg}, (_, i) => { const t = 2 * Math.PI * (i + 0.5) / seg; return add(c, add(mul(u, r * Math.cos(t)), mul(w, r * Math.sin(t)))); });
  const A = ring(a, r0), B = ring(b, r1);
  loft(part, [A, B], c => { const d = sub(c, a); return sub(d, mul(ax, dot(d, ax))); });
  if (ends) { cap(part, A, mul(ax, -1)); cap(part, B, ax); }
}
// a pipe swept along a path (concept metres), corners rounded by Catmull-Rom subdivision; open ends unless capped
function tube(part, pts, r, {seg = 14, sub: per = 6, capEnds = true} = {}) {
  const P = pts.map(cv), path0 = [];
  for (let i = 0; i + 1 < P.length; i++) {
    const p0 = P[Math.max(0, i - 1)], p1 = P[i], p2 = P[i + 1], p3 = P[Math.min(P.length - 1, i + 2)];
    for (let s = 0; s < per; s++) { const t = s / per, t2 = t * t, t3 = t2 * t;
      path0.push([0, 1, 2].map(k => 0.5 * ((2 * p1[k]) + (-p0[k] + p2[k]) * t + (2 * p0[k] - 5 * p1[k] + 4 * p2[k] - p3[k]) * t2 + (-p0[k] + 3 * p1[k] - 3 * p2[k] + p3[k]) * t3))); }
  }
  path0.push(P[P.length - 1]);
  const path1 = path0.filter((p, i) => i === 0 || Math.hypot(...sub(p, path0[i - 1])) > 0.05);
  // parallel-transport frames
  let T = norm(sub(path1[1], path1[0])), U = norm(cross(T, Math.abs(T[1]) < 0.9 ? [0, 1, 0] : [1, 0, 0]));
  const rings = [], R = r * 1000;
  for (let i = 0; i < path1.length; i++) {
    const Tn = i + 1 < path1.length ? norm(sub(path1[i + 1], path1[Math.max(0, i - 1)])) : T;
    const axis = cross(T, Tn), s = Math.hypot(...axis);
    if (s > 1e-9) { const ang = Math.asin(Math.min(1, s)), k = norm(axis); U = add(add(mul(U, Math.cos(ang)), mul(cross(k, U), Math.sin(ang))), mul(k, dot(k, U) * (1 - Math.cos(ang)))); }
    T = Tn; const W = cross(T, U);
    rings.push(Array.from({length: seg}, (_, j) => { const a = 2 * Math.PI * j / seg; return add(path1[i], add(mul(U, R * Math.cos(a)), mul(W, R * Math.sin(a)))); }));
  }
  loft(part, rings, (c, k) => { const p = path1[Math.min(k, path1.length - 1)]; return sub(c, p); });
  if (capEnds) { cap(part, rings[0], sub(path1[0], path1[1])); cap(part, rings[rings.length - 1], sub(path1[path1.length - 1], path1[path1.length - 2])); }
}
// solid of revolution around a vertical axis at (cx, cz) concept metres: profile [[r, y]...] metres, bottom to top
function lathe(part, cx, cz, profile, seg = 24, {capTop = true, capBottom = false} = {}) {
  const rings = profile.map(([r, y]) => Array.from({length: seg}, (_, i) => { const a = 2 * Math.PI * (i + 0.5) / seg; return cv([cx + r * Math.cos(a), y, cz + r * Math.sin(a)]); }));
  const axis = cv([cx, 0, cz]);
  loft(part, rings, c => [c[0] - axis[0], 0.15 * (c[1] > rings[rings.length - 1][0][1] - 1 ? 1 : 0), c[2] - axis[2]]);
  if (capTop) cap(part, rings[rings.length - 1], [0, 1, 0]);
  if (capBottom) cap(part, rings[0], [0, -1, 0]);
}
// rounded box: plan (x, z concept) rounded rectangle extruded from y0 to y1 (metres), chamfered
function rbox(part, cx, cz, w, d, y0, y1, r, ch) {
  const outline = []; const seg = 4, x0 = cx - w / 2, x1 = cx + w / 2, z0 = cz - d / 2, z1 = cz + d / 2;
  for (const [ccx, ccz, a0] of [[x1 - r, z1 - r, 0], [x0 + r, z1 - r, 90], [x0 + r, z0 + r, 180], [x1 - r, z0 + r, 270]])
    for (let i = 0; i <= seg; i++) { const a = (a0 + 90 * i / seg) * Math.PI / 180; outline.push([ccx + r * Math.cos(a), ccz + r * Math.sin(a)]); }
  // the library's y plane is (u, v) = (z, x), model millimetres
  const shape = outline.map(([x, z]) => { const m = cv([x, 0, z]); return [m[2], m[0]]; });
  extrude(part, 'y', {outer: area2(shape) > 0 ? shape : shape.slice().reverse(), holes: []}, y0 * 1000, y1 * 1000, ch * 1000);
}

// ---------------- materials ----------------
// MAT(albedo, bevel highlight, smoothness, bevel smoothness, F0): LabPBR _s R = perceptual smoothness, G = F0 (230..237
// predefined metals: 230 iron, 233 chrome, 237 silver; 255 = albedo is the F0), B = 0, A = 255 (no emission)
const MAT = (c, hl, sm, se, f0 = 20) => ({c, hl, sm, se, f0});
const MATS = {
  china:  MAT([243, 242, 237], 4, 222, 230),            // glazed vitreous china
  trap:   MAT([38, 38, 37], 0, 120, 120),               // the dry trapway / drain throat
  seat:   MAT([238, 237, 232], 3, 150, 160),            // solid plastic seat
  chrome: MAT([212, 215, 218], 8, 220, 228, 233),       // polished chrome
  satin:  MAT([190, 192, 194], 5, 168, 178, 255),       // brushed stainless (hinges, mirror frame)
  braid:  MAT([166, 169, 172], 0, 128, 128, 230),       // stainless braided supply hose
  glass:  MAT((pos) => { const k = 0.985 + 0.03 * Math.max(0, Math.min(1, pos[1] / 762)); return [204 * k, 210 * k, 214 * k]; }, 0, 255, 255, 237),   // mirror: a faint brighter top, nothing painted
  wallplate: MAT([226, 228, 229], 4, 196, 206, 233),    // chrome escutcheons
};

// ---------------- toilet (T1) ----------------
export const TOILET = {rim: 0.42, a: 0.18, b: 0.27, zc: 0.445, inset: 0.034, valveY: 0.74, stopX: -0.16};
function egg(th, a, b, zc) { const c = Math.cos(th), s = Math.sin(th); return [a * s * (1 - 0.13 * Math.max(0, c)), zc + b * c]; }
function prof(P, t) { for (let i = 1; i < P.length; i++) if (t <= P[i][0]) { const u = smooth((t - P[i - 1][0]) / (P[i][0] - P[i - 1][0])); return P[i].slice(1).map((v, j) => lerp(P[i - 1][j + 1], v, u)); } return P[P.length - 1].slice(1); }
function buildToilet() {
  const PARTS = [], P = (name, mat) => { const p = new Part(name, 'body', mat); PARTS.push(p); return p; };
  const T = TOILET, K = 48, ring = (y, a, b, zc) => Array.from({length: K}, (_, k) => { const [x, z] = egg(2 * Math.PI * k / K, a, b, zc); return cv([x, y, z]); });
  const axisOut = zc => c => { const ax = cv([0, 0, zc]); return [c[0] - ax[0], 0, c[2] - ax[2]]; };
  // outer shell: pedestal flaring into the bowl
  const outer = [[0, 0.0, 0.6, -0.08], [0.06, 0.03, 0.56, -0.08], [0.3, 0.2, 0.55, -0.06], [0.5, 0.28, 0.72, -0.02], [0.7, 0.34, 0.9, 0], [0.86, 0.39, 0.99, 0], [1, T.rim, 1.0, 0]];
  const NT = 16;
  const shell = P('bowl_outer', 'china'), outerRings = Array.from({length: NT + 1}, (_, i) => { const [y, s, dz] = prof(outer, i / NT); return ring(y, T.a * s, T.b * s, T.zc + dz); });
  loft(shell, outerRings, c => { const [y] = [c[1] / 1000]; const f = axisOut(T.zc - 0.04)(c); return [f[0], y < 0.24 ? 0 : 0.25 * Math.hypot(f[0], f[2]), f[2]]; });
  // the foot's underside: never seen on a floor, but held, dropped or in the inventory the open shell looked hollow and the
  // base see-through (user 2026-10-08). Every part is a closed surface now; the build fails on a new hole (OPEN_EDGES below).
  cap(shell, outerRings[0], [0, -1, 0]);
  // rim top and the inner bowl, dry: glazed down to the dark trapway opening
  loft(P('rim', 'china'), [ring(T.rim, T.a, T.b, T.zc), ring(T.rim, T.a - T.inset, T.b - T.inset, T.zc)], () => [0, 1, 0]);
  const inner = [[0, T.rim - 0.002, 1, 0], [0.12, T.rim - 0.022, 0.97, 0], [0.4, 0.35, 0.78, -0.01], [0.7, 0.27, 0.48, -0.05], [1, 0.205, 0.2, -0.07]];
  const a1 = T.a - T.inset, b1 = T.b - T.inset, NI = 14;
  const innerRings = Array.from({length: NI + 1}, (_, i) => { const [y, s, dz] = prof(inner, i / NI); return ring(y, a1 * s, b1 * s, T.zc + dz); });
  innerRings.unshift(ring(T.rim, a1, b1, T.zc));
  loft(P('bowl_inner', 'china'), innerRings, c => { const ax = cv([0, 0.5, T.zc - 0.03]); return sub(ax, c); });
  cap(P('trapway', 'trap'), innerRings[innerRings.length - 1].map(p => add(p, [0, -0.5, 0])), [0, 1, 0]);
  // two bolt caps on the foot
  for (const s of [-1, 1]) lathe(P('bolt_cap_' + (s > 0 ? 'r' : 'l'), 'china'), s * 0.098, T.zc - 0.08, [[0.019, 0.0005], [0.018, 0.006], [0.013, 0.013], [0.006, 0.016]], 12, {capBottom: true});   // 0.5 mm up: their bottoms would share the foot cap's plane
  // neck behind the bowl up to the top spud
  rbox(P('neck', 'china'), 0, 0.125, 0.17, 0.17, 0.30, 0.424, 0.04, 0.008);
  // open-front seat, no cover: a U on the rim
  const gap = 0.25, S0 = 0.012, S1 = 0.083, top = T.rim + 0.026, bot = T.rim + 0.004, KS = 44;
  const seatPt = (th, w, y) => { const [x, z] = egg(th, T.a - w, T.b - w, T.zc); return cv([x, y, z]); };
  const thAt = k => gap + (2 * Math.PI - 2 * gap) * k / KS;
  const sRing = (w, y) => Array.from({length: KS + 1}, (_, k) => seatPt(thAt(k), w, y));
  const seat = P('seat', 'seat');
  loft(seat, [sRing(S0, top), sRing(S1, top)], () => [0, 1, 0], {closed: false});
  loft(seat, [sRing(S0, bot), sRing(S1, bot)], () => [0, -1, 0], {closed: false});
  loft(seat, [sRing(S0, bot), sRing(S0, top)], axisOut(T.zc), {closed: false});
  loft(seat, [sRing(S1, bot), sRing(S1, top)], c => mul(axisOut(T.zc)(c), -1), {closed: false});
  for (const k of [0, KS]) { const th = thAt(k), q = [seatPt(th, S0, bot), seatPt(th, S1, bot), seatPt(th, S1, top), seatPt(th, S0, top)].map(p => seat.vtx(p));
    const mid = seatPt(th, (S0 + S1) / 2, top), tangent = sub(seatPt(th + (k ? 0.05 : -0.05), (S0 + S1) / 2, top), mid); seat.face(q, tangent, 'cap'); }
  for (const s of [-1, 1]) cyl(P('hinge_' + (s > 0 ? 'r' : 'l'), 'satin'), cv([s * 0.07 - 0.017, T.rim + 0.016, 0.205]), cv([s * 0.07 + 0.017, T.rim + 0.016, 0.205]), 11, 11, 14);
  // the flushometer: top spud, vacuum-breaker tube, valve body with cap and handle, control stop and supply from the wall
  const ch = P('flushometer', 'chrome'), VY = T.valveY, SX = T.stopX;
  lathe(ch, 0, 0.11, [[0.03, T.rim], [0.03, T.rim + 0.008], [0.026, T.rim + 0.018], [0.026, T.rim + 0.042], [0.02, T.rim + 0.046]], 20, {capBottom: true});
  tube(ch, [[0, T.rim + 0.04, 0.11], [0, 0.55, 0.108], [0, VY - 0.03, 0.105]], 0.017, {sub: 2});
  lathe(ch, 0, 0.107, [[0.024, 0.595], [0.024, 0.645], [0.018, 0.652]], 20, {capBottom: true});
  cyl(ch, cv([-0.075, VY, 0.105]), cv([0.075, VY, 0.105]), 30, 30, 22);
  lathe(ch, 0, 0.105, [[0.036, VY + 0.02], [0.034, VY + 0.075], [0.03, VY + 0.088], [0.018, VY + 0.098], [0.0, VY + 0.1]], 22, {capTop: false, capBottom: true});
  cyl(ch, cv([0.075, VY, 0.105]), cv([0.105, VY, 0.105]), 20, 20, 16);
  tube(ch, [[0.1, VY, 0.105], [0.2, VY - 0.025, 0.115]], 0.008, {sub: 1});
  tube(ch, [[SX, VY, 0.002], [SX, VY, 0.07], [SX + 0.05, VY, 0.105], [-0.075, VY, 0.105]], 0.013, {sub: 5});
  cyl(ch, cv([SX, VY, 0.05]), cv([SX, VY, 0.1]), 22, 22, 18);
  lathe(ch, SX, 0.075, [[0.017, VY + 0.018], [0.017, VY + 0.05], [0.012, VY + 0.056]], 16, {capBottom: true});
  cyl(P('stop_escutcheon', 'wallplate'), cv([SX, VY, 0.0005]), cv([SX, VY, 0.008]), 40, 38, 24);
  return {PARTS, atlas: 512, startS: 24};
}

// ---------------- lavatory (S1) ----------------
export const LAV = {w: 0.52, d: 0.46, top: 0.84, bottom: 0.735, bw: 0.38, bd: 0.27, bz: 0.255, splash: 0.10, trapY: 0.52, armY: 0.50};
function buildLav() {
  const PARTS = [], P = (name, mat) => { const p = new Part(name, 'body', mat); PARTS.push(p); return p; };
  const L = LAV, K = 64;
  // radial rings around the basin axis: an ellipse (scale s) or the deck outline (inset w), sampled by angle
  const ell = (s, y) => Array.from({length: K}, (_, k) => { const a = 2 * Math.PI * k / K; return cv([Math.sin(a) * L.bw / 2 * s, y, L.bz + Math.cos(a) * L.bd / 2 * s]); });
  const inRect = (x, z, w) => { const hw = L.w / 2 - w, z0 = w, z1 = L.d - w, rf = 0.085 - w * 0.5, rb = 0.012; if (Math.abs(x) > hw || z < z0 || z > z1) return false;
    const r = z > L.d / 2 ? rf : rb, cx = hw - r, cz = z > L.d / 2 ? z1 - r : z0 + r; const dx = Math.abs(x) - cx, dz = z > L.d / 2 ? z - cz : cz - z; return !(dx > 0 && dz > 0 && Math.hypot(dx, dz) > r); };
  const rect = (w, y) => Array.from({length: K}, (_, k) => { const a = 2 * Math.PI * k / K, dx = Math.sin(a), dz = Math.cos(a); let lo = 0, hi = 1.0;
    for (let it = 0; it < 40; it++) { const m = (lo + hi) / 2; if (inRect(dx * m, L.bz + dz * m, w)) lo = m; else hi = m; } return cv([dx * lo, y, L.bz + dz * lo]); });
  const T = L.top, B = L.bottom, axis = cv([0, 0, L.bz]);
  const radial = c => [c[0] - axis[0], 0, c[2] - axis[2]];
  const rings = [ell(0.15, T - 0.16), ell(0.55, T - 0.152), ell(0.95, B - 0.006), ell(1.12, B), rect(0.008, B), rect(0.0022, B + 0.0022), rect(0, B + 0.008),
    rect(0, T - 0.008), rect(0.0022, T - 0.0022), rect(0.008, T), ell(1.07, T), ell(1.0, T - 0.004), ell(0.97, T - 0.012), ell(0.8, T - 0.075), ell(0.45, T - 0.118), ell(0.13, T - 0.127)];
  // the visible side per band: underside down / out, apron out, top up, basin in toward the axis
  const hints = [c => [radial(c)[0], -1000, radial(c)[2]], c => [radial(c)[0], -1000, radial(c)[2]], c => [0, -1, 0], () => [0, -1, 0], c => [radial(c)[0], -1000, radial(c)[2]], c => mul(radial(c), 1), radial, radial,
    c => [radial(c)[0], 1000, radial(c)[2]], () => [0, 1, 0], () => [0, 1, 0], c => [-radial(c)[0], 300, -radial(c)[2]], c => [-radial(c)[0], 300, -radial(c)[2]], c => [-radial(c)[0], 300, -radial(c)[2]], c => [-radial(c)[0], 300, -radial(c)[2]]];
  const basin = P('basin', 'china');
  loft(basin, rings, (c, k) => hints[k](c));
  cap(basin, rings[0], [0, -1, 0]);   // under the bowl, around the tailpiece (open until 2026-10-08)
  cap(P('drain_throat', 'trap'), ell(0.13, T - 0.128), [0, 1, 0]);
  // backsplash
  rbox(P('backsplash', 'china'), 0, 0.02, L.w, 0.04, T - 0.002, T + L.splash, 0.012, 0.006);
  // grid strainer: a chrome flange ring and a cross
  const dy = T - 0.1265, ch = P('fittings', 'chrome');
  loft(P('strainer', 'chrome'), [Array.from({length: 24}, (_, k) => { const a = 2 * Math.PI * k / 24; return cv([0.022 * Math.sin(a), dy + 0.002, L.bz + 0.022 * Math.cos(a)]); }),
    Array.from({length: 24}, (_, k) => { const a = 2 * Math.PI * k / 24; return cv([0.0165 * Math.sin(a), dy + 0.0025, L.bz + 0.0165 * Math.cos(a)]); })], () => [0, 1, 0]);
  for (const [dx, dz] of [[1, 0], [0, 1]]) cyl(ch, cv([-0.016 * dx, dy + 0.001, L.bz - 0.016 * dz]), cv([0.016 * dx, dy + 0.001, L.bz + 0.016 * dz]), 1.6, 1.6, 6);
  // single-lever faucet on the deck
  lathe(ch, 0, 0.075, [[0.027, T - 0.002], [0.027, T + 0.004], [0.024, T + 0.01], [0.024, T + 0.085], [0.0, T + 0.088]], 20, {capTop: false, capBottom: true});
  tube(ch, [[0, T + 0.065, 0.075], [0, T + 0.1, 0.105], [0, T + 0.1, 0.15], [0, T + 0.072, 0.18]], 0.011, {sub: 5});
  tube(ch, [[0, T + 0.086, 0.07], [0, T + 0.106, 0.035]], 0.0055, {sub: 1});
  // below: tailpiece, P-trap and trap arm into the wall, slip nut, escutcheon
  tube(ch, [[0, T - 0.158, L.bz], [0, L.trapY + 0.06, L.bz], [0, L.trapY, L.bz - 0.025], [0, L.trapY + 0.025, L.bz - 0.085], [0, L.armY, L.bz - 0.13], [0, L.armY, 0.004]], 0.016, {sub: 6});
  cyl(ch, cv([0, L.trapY + 0.07, L.bz]), cv([0, L.trapY + 0.092, L.bz]), 22, 22, 18);
  cyl(P('arm_escutcheon', 'wallplate'), cv([0, L.armY, 0.0005]), cv([0, L.armY, 0.007]), 34, 32, 22);
  // two angle stops and braided supplies up to the faucet's shanks
  for (const s of [-1, 1]) {
    const side = s > 0 ? 'r' : 'l', st = P('stop_' + side, 'chrome');
    cyl(P('stop_escutcheon_' + side, 'wallplate'), cv([s * 0.12, 0.54, 0.0005]), cv([s * 0.12, 0.54, 0.006]), 30, 28, 20);
    cyl(st, cv([s * 0.12, 0.54, 0.006]), cv([s * 0.12, 0.54, 0.056]), 16, 16, 16);
    lathe(st, s * 0.12, 0.034, [[0.012, 0.546], [0.012, 0.566], [0.016, 0.57], [0.016, 0.578], [0.0, 0.58]], 14, {capTop: false, capBottom: true});
    tube(P('supply_' + side, 'braid'), [[s * 0.12, 0.54, 0.056], [s * 0.12, 0.575, 0.064], [s * 0.07, 0.65, 0.072], [s * 0.025, L.bottom - 0.07, 0.076], [s * 0.02, L.bottom - 0.01, 0.076]], 0.0055, {sub: 5});
  }
  return {PARTS, atlas: 512, startS: 24};
}

// ---------------- mirror ----------------
export const MIRROR = {w: 0.457, h: 0.762, face: 0.016, depth: 0.02, bottom: 0.0};
function buildMirror() {
  const PARTS = [], P = (name, mat) => { const p = new Part(name, 'body', mat); PARTS.push(p); return p; };
  const M = MIRROR, x0 = -M.w / 2 * 1000, x1 = M.w / 2 * 1000, y0 = M.bottom * 1000 + 1, y1 = (M.bottom + M.h) * 1000, f = M.face * 1000;
  const zb = 500 - 0.4, zf = 500 - M.depth * 1000, rect = (a0, b0, a1, b1) => [[a0, b0], [a1, b0], [a1, b1], [a0, b1]];
  // stainless channel frame (the z plane is (u, v) = (x, y)), chamfered front edges; the glass sits 5 mm behind its face
  extrude(P('frame', 'satin'), 'z', {outer: rect(x0, y0, x1, y1), holes: [rect(x0 + f, y0 + f, x1 - f, y1 - f).reverse()]}, zf, zb, 2.5);
  extrude(P('glass', 'glass'), 'z', {outer: rect(x0 + f - 1, y0 + f - 1, x1 - f + 1, y1 - f + 1), holes: []}, zf + 5, zb - 3, 0);
  return {PARTS, atlas: 256, startS: 24};
}

// ---------------- bake ----------------
const DUMMY = 'data:image/png;base64,' + png(Buffer.from([0, 0, 0, 255]), 1, 1).toString('base64');
function bake(key, id, source, build) {
  const {PARTS: all, atlas, startS} = build(), PARTS = all.filter(p => p.f.length);
  for (const p of PARTS) p.v = p.v.map(q => q.map(v => v * PX));   // mm -> px
  // the OBJ keeps faces as they are (Forge draws a quad as 0-1-2 / 2-3-0): split anything not a triangle / convex quad
  for (const p of PARTS) {
    const out = [];
    for (const f of p.f) {
      const P3 = f.ids.map(i => p.v[i]), n = newell(P3);
      const convexQuad = f.ids.length === 4 && [0, 1, 2, 3].every(k => dot(cross(sub(P3[(k + 1) % 4], P3[k]), sub(P3[(k + 2) % 4], P3[(k + 1) % 4])), n) > 1e-14);
      if (f.ids.length === 3 || convexQuad) { out.push(f); continue; }
      assert(f.ids.length === 4, `${key}: ${f.ids.length}-gon in ${p.name}`);
      const k = [0, 1, 2, 3].find(k => dot(cross(sub(P3[k], P3[(k + 3) % 4]), sub(P3[(k + 1) % 4], P3[k])), n) <= 1e-14) ?? 0;
      out.push({...f, ids: [f.ids[k], f.ids[(k + 1) % 4], f.ids[(k + 2) % 4]]}, {...f, ids: [f.ids[(k + 2) % 4], f.ids[(k + 3) % 4], f.ids[k]]});
    }
    p.f = out;
  }
  const UV = unwrap(PARTS, {atlas, pad: 2, startS, stepS: 0.5});
  for (const p of PARTS) assert(p.f.every(f => UV.faceUV.get(p)?.has(f)), `${key}: unmapped face in ${p.name}`);
  const used = Object.fromEntries([...new Set(PARTS.map(p => p.mat))].map(m => [m, MATS[m]])), first = used[PARTS[0].mat];
  const firstC = typeof first.c === 'function' ? first.c([0, 0, 0]) : first.c;
  const painted = paint({PARTS, islands: UV.islands, S: UV.S, uvOf: UV.uvOf, atlas, pad: 2, MATS: used, ZONED: new Set(), groupInfo: new Map(),
    sourceGroups: () => [], refTexture: {source: DUMMY}, refUvWidth: 1, background: {c: [...firstC, 255], s: [first.sm, first.f0, 0, 255], n: [128, 128, 255, 255]}});
  const maps = painted.PNG.map(b => { const r = readPng(b); assert(r.bpp === 4 && r.w === atlas, 'atlas format'); return r.px; });
  return {key, id, source, PARTS, atlas, UV, maps: maps.map(px => png(px, atlas, atlas))};
}
const B = {
  toilet: bake('toilet', 'commercial_flushometer_toilet', 'commercial_flushometer_toilet_v2', buildToilet),
  lav: bake('lav', 'commercial_wall_mounted_sink', 'commercial_wall_mounted_sink_v2', buildLav),
  mirror: bake('mirror', 'wall_mirror', 'wall_mirror_v1', buildMirror),
};
const coplanar = {};
for (const [k, b] of Object.entries(B)) { const zf = zFightLevels(b.PARTS, new Map()); if (zf.unresolved.length) coplanar[k] = zf.unresolved.slice(0, 6); }

// ---------------- OBJ / MTL / models ----------------
const D2R = Math.PI / 180, f6 = v => (+v.toFixed(6)).toString(), r3 = v => +v.toFixed(3) || 0;
const SMOOTH = Math.cos(36 * D2R);
function cornerNormals(p) {
  const fn = p.f.map(f => newell(f.ids.map(i => p.v[i]))), byV = new Map();
  p.f.forEach((f, k) => f.ids.forEach(i => (byV.get(i) || byV.set(i, []).get(i)).push(k)));
  return p.f.map((f, k) => { const n0 = norm(fn[k]); return f.ids.map(i => norm(byV.get(i).reduce((a, j) => dot(norm(fn[j]), n0) >= SMOOTH ? add(a, fn[j]) : a, [0, 0, 0]))); });
}
function objOf(title, b) {
  const out = [`# AFL ${title}, generated by tools/build-restroom-fixtures-v2.mjs`, `mtllib ${b.id}.mtl`];
  let vBase = 1, tBase = 1, nBase = 1;
  for (const p of b.PARTS) {
    out.push(`o ${p.name}`, `usemtl ${b.key}`);
    for (const q of p.v) out.push(`v ${f6(q[0] / 16 + 0.5)} ${f6(q[1] / 16)} ${f6(q[2] / 16 + 0.5)}`);
    const uvs = b.UV.faceUV.get(p), vt = [], vn = [], fl = [], cn = cornerNormals(p), nIndex = new Map();
    p.f.forEach((f, k) => {
      const uv = uvs.get(f);
      fl.push('f ' + f.ids.map((id, j) => {
        const key = cn[k][j].map(f6).join(' ');
        if (!nIndex.has(key)) { nIndex.set(key, nBase + vn.length); vn.push('vn ' + key); }
        vt.push(`vt ${f6(uv[j][0] / b.atlas)} ${f6(1 - uv[j][1] / b.atlas)}`); return `${vBase + id}/${tBase + vt.length - 1}/${nIndex.get(key)}`;
      }).join(' '));
    });
    out.push(...vt, ...vn, ...fl);
    vBase += p.v.length; tBase += vt.length; nBase += vn.length;
  }
  return out.join('\n') + '\n';
}
const mtlOf = (title, b) => `# AFL ${title}\nnewmtl ${b.key}\nKd 1 1 1\nmap_Kd apocalypse_firstlight:block/${b.id}\n`;
const objModel = id => ({loader: 'forge:obj', model: `apocalypse_firstlight:models/block/${id}.obj`, automatic_culling: false,
  flip_v: true, shade_quads: true, ambientocclusion: false, textures: {particle: `apocalypse_firstlight:block/${id}`}});
function guiCentred(b, rotation, scale) {
  const [ax, ay] = rotation.map(v => v * D2R);
  const rot = q => { const x = q[0] * Math.cos(ay) + q[2] * Math.sin(ay), z = -q[0] * Math.sin(ay) + q[2] * Math.cos(ay); return [x, q[1] * Math.cos(ax) - z * Math.sin(ax)]; };
  const pts = b.PARTS.flatMap(p => p.v.map(q => rot([q[0], q[1] - 8, q[2]])));
  const cx = (Math.max(...pts.map(q => q[0])) + Math.min(...pts.map(q => q[0]))) / 2, cy = (Math.max(...pts.map(q => q[1])) + Math.min(...pts.map(q => q[1]))) / 2;
  return {rotation, translation: [r3(-scale * cx), r3(-scale * cy), 0], scale: [scale, scale, scale]};
}
const S3 = v => [v, v, v];
const display = (b, gui, guiScale, s, lift = 0) => ({
  thirdperson_righthand: {rotation: [75, 45, 0], translation: [0, 1.5, 0], scale: S3(s)}, thirdperson_lefthand: {rotation: [75, 45, 0], translation: [0, 1.5, 0], scale: S3(s)},
  firstperson_righthand: {rotation: [0, 45, 0], translation: [0, lift, 0], scale: S3(s * 0.8)}, firstperson_lefthand: {rotation: [0, 225, 0], translation: [0, lift, 0], scale: S3(s * 0.8)},
  gui: guiCentred(b, gui, guiScale), ground: {translation: [0, 2, 0], scale: S3(s * 0.6)}, fixed: {rotation: [0, 180, 0], translation: [0, 0, 0], scale: S3(s)}});

// closed surfaces (2026-10-08): an open edge is a hole when the item is held, dropped or drawn in the inventory. Allowed:
// the trap / drain ring of the bowl and basin, each covered by its dark disc 0.5 mm below (trapway, drain_throat), and the
// flat strainer flange on the basin floor (seen from above only). Everything else must be closed.
const OPEN_EDGES = {toilet: [[['bowl_outer', 'rim', 'bowl_inner'], 48], [['trapway'], 48]], lav: [[['basin'], 64], [['drain_throat'], 64], [['strainer'], 48]], mirror: []};
function openEdges(parts) {
  const key = q => q.map(v => Math.round(v * 1000)).join(','), edges = new Map();
  for (const p of parts) for (const f of p.f) for (let j = 0; j < f.ids.length; j++) {
    const a = key(p.v[f.ids[j]]), b = key(p.v[f.ids[(j + 1) % f.ids.length]]); if (a === b) continue;
    const k = a < b ? a + '|' + b : b + '|' + a; edges.set(k, (edges.get(k) || 0) + 1);
  }
  return [...edges.values()].filter(n => n === 1).length;
}
for (const [k, groups] of Object.entries(OPEN_EDGES)) {
  const named = new Set(groups.flatMap(([g]) => g));
  for (const [g, expected] of groups) { const n = openEdges(B[k].PARTS.filter(p => g.includes(p.name))); assert(n === expected, `${k}: ${g.join(' + ')} has ${n} open edges, expected ${expected}`); }
  for (const p of B[k].PARTS.filter(p => !named.has(p.name))) { const n = openEdges([p]); assert(!n, `${k}: part ${p.name} has ${n} open edges (a hole when held, dropped or in the inventory)`); }
}

// collision / selection boxes (cell px, NORTH frame), mirrored in the block classes; every vertex must lie inside one
export const SHAPES = {
  toilet: [[6.05, 0, 7.4, 9.95, 3.4, 12.85], [5.75, 3.3, 5.85, 10.25, 4.75, 12.5], [5.0, 4.6, 4.4, 11.0, 7.25, 13.35], [6.5, 4.75, 12.5, 9.5, 6.95, 15.5], [7.4, 6.9, 13.7, 8.6, 11.0, 14.8], [4.7, 10.7, 13.5, 11.4, 13.85, 16.0]],
  lav: [[3.75, 11.7, 8.5, 12.25, 13.5, 16.0], [4.8, 10.8, 9.4, 11.2, 11.8, 15.0], [3.75, 13.4, 15.3, 12.25, 15.15, 16.0], [7.35, 13.4, 12.6, 8.65, 15.4, 15.65], [7.4, 7.4, 11.3, 8.6, 11.0, 16.0], [5.55, 8.1, 14.6, 10.45, 11.9, 16.0]],
  mirror: [[4.3, 0, 15.6, 11.7, 12.25, 16.0]],
};
for (const [k, boxes] of Object.entries(SHAPES)) {
  const inside = q => boxes.some(b => q[0] + 8 >= b[0] - 0.05 && q[0] + 8 <= b[3] + 0.05 && q[1] >= b[1] - 0.05 && q[1] <= b[4] + 0.05 && q[2] + 8 >= b[2] - 0.05 && q[2] + 8 <= b[5] + 0.05);
  const out = B[k].PARTS.flatMap(p => p.v.filter(q => !inside(q)).map(q => p.name + ' ' + [q[0] + 8, q[1], q[2] + 8].map(v => v.toFixed(2)).join(',')));
  assert(!out.length, `${k}: vertices outside its shape boxes: ${out.slice(0, 4).join(' | ')} (${out.length})`);
}

// editable Free Model sources (px, the model frame): one group per bone
const uuidOf = (ns, s) => { const h = createHash('sha256').update(`afl-restroom-fixtures-v2:${ns}:${s}`).digest('hex'); return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-8${h.slice(17, 20)}-${h.slice(20, 32)}`; };
const r12 = v => +v.toFixed(12) || 0;
function sourceOf(b) {
  const uuid = s => uuidOf(b.id, s), name = b.source;
  const bones = [...new Set(b.PARTS.map(p => p.bone))], gid = bone => uuid('group:' + bone);
  const elements = b.PARTS.map(p => {
    const key = i => i.toString(36).padStart(4, '0'), vertices = {}, faces = {}, uvs = b.UV.faceUV.get(p);
    p.v.forEach((q, i) => { vertices[key(i)] = q.map(r12); });
    p.f.forEach((f, fi) => { const uv = uvs.get(f); faces['f' + key(fi)] = {uv: Object.fromEntries(f.ids.map((id, j) => [key(id), uv[j].map(r12)])), vertices: f.ids.map(key), texture: 0}; });
    return {name: p.name, color: 0, origin: [0, 0, 0], rotation: [0, 0, 0], shading: 'smooth', export: true, visibility: true, locked: false,
      render_order: 'default', scope: 0, allow_mirror_modeling: true, vertices, faces, type: 'mesh', uuid: uuid('mesh:' + p.name)};
  });
  return {meta: {format_version: '5.0', model_format: 'free', box_uv: false}, name, model_identifier: '', visible_box: [1, 1, 0],
    variable_placeholders: '', variable_placeholder_buttons: [], timeline_setups: [], unhandled_root_fields: {}, resolution: {width: b.atlas, height: b.atlas},
    elements, groups: bones.map(bone => ({name: bone, uuid: gid(bone), export: true, locked: false, scope: 0,
      selected: false, visibility: true, _static: {properties: {}, temp_data: {}}, origin: [0, 0, 0], rotation: [0, 0, 0],
      color: 0, children: [], reset: false, shade: true, mirror_uv: false, autouv: 0, isOpen: true, primary_selected: false})),
    outliner: bones.map(bone => ({uuid: gid(bone), isOpen: true, children: elements.filter((e, i) => b.PARTS[i].bone === bone).map(e => e.uuid)})),
    textures: [{name: name + '.png', relative_path: `textures/${name}.png`, folder: '', namespace: '', id: '0', group: '', scope: 0,
      width: b.atlas, height: b.atlas, uv_width: b.atlas, uv_height: b.atlas, particle: false, use_as_default: false, layers_enabled: false, sync_to_project: '',
      file_format: 'png', render_mode: 'default', render_sides: 'auto', wrap_mode: 'limited', pbr_channel: 'color', fps: 7, frame_time: 1,
      frame_order_type: 'loop', frame_order: '', frame_interpolate: false, visible: true, internal: true, saved: true, uuid: uuid('texture'),
      source: 'data:image/png;base64,' + b.maps[0].toString('base64')}],
    animations: []};
}

const bb = path.join(ROOT, 'src/main/blockbench'), assets = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight');
const json = v => JSON.stringify(v, null, 2) + '\n';
const outputs = [], objs = [];
const TITLES = {toilet: 'Restroom Fixtures V2 commercial toilet (dry)', lav: 'Restroom Fixtures V2 wall-hung lavatory (dry)', mirror: 'Restroom Fixtures V2 wall mirror'};
for (const [k, b] of Object.entries(B)) {
  const obj = objOf(TITLES[k], b);
  objs.push([b.id, obj]);
  outputs.push([path.join(assets, `models/block/${b.id}.obj`), obj], [path.join(assets, `models/block/${b.id}.mtl`), mtlOf(TITLES[k], b)],
    [path.join(assets, `models/block/${b.id}.json`), json(objModel(b.id))], [path.join(bb, `${b.source}.bbmodel`), JSON.stringify(sourceOf(b))],
    ...['', '_s', '_n'].flatMap((s, i) => [[path.join(bb, `textures/${b.source}${s}.png`), b.maps[i]], [path.join(assets, `textures/block/${b.id}${s}.png`), b.maps[i]]]));
}
const YROT = {north: 0, east: 90, south: 180, west: 270};
// toilet and mirror: one model per facing; the lavatory keeps its V1 half property, the upper half is an obsolete leftover
outputs.push([path.join(assets, 'blockstates/commercial_flushometer_toilet.json'), json({variants: Object.fromEntries(Object.entries(YROT).map(([f, y]) =>
  [`facing=${f}`, {model: 'apocalypse_firstlight:block/commercial_flushometer_toilet', ...(y ? {y} : {})}]))})]);
outputs.push([path.join(assets, 'blockstates/wall_mirror.json'), json({variants: Object.fromEntries(Object.entries(YROT).map(([f, y]) =>
  [`facing=${f}`, {model: 'apocalypse_firstlight:block/wall_mirror', ...(y ? {y} : {})}]))})]);
outputs.push([path.join(assets, 'blockstates/commercial_wall_mounted_sink.json'), json({variants: Object.fromEntries(Object.entries(YROT).flatMap(([f, y]) => [
  [`facing=${f},half=lower`, {model: 'apocalypse_firstlight:block/commercial_wall_mounted_sink', ...(y ? {y} : {})}],
  [`facing=${f},half=upper`, {model: 'apocalypse_firstlight:block/commercial_wall_mounted_sink_upper'}]]))})]);
outputs.push([path.join(assets, 'models/item/commercial_flushometer_toilet.json'), json({parent: 'apocalypse_firstlight:block/commercial_flushometer_toilet', gui_light: 'side', display: display(B.toilet, [30, 200, 0], 0.95, 0.5)})]);   // gui scale 0.95: at 1.25 the icon was 19.1 px tall and stuck out of the 16 px slot (user 2026-10-08); now 14.5 px
// held, dropped and framed fitted to the basin's bounds (tools/item-held-display.mjs, 2026-10-09: the fixed lift left it half a block low)
outputs.push([path.join(assets, 'models/item/commercial_wall_mounted_sink.json'), json({parent: 'apocalypse_firstlight:block/commercial_wall_mounted_sink', gui_light: 'side',
  display: {...heldDisplay(B.lav.PARTS.flatMap(p => p.v.map(([x, y, z]) => [x + 8, y, z + 8])), {size: 0.6, rotations: {fixed: [0, 180, 0]}}), gui: guiCentred(B.lav, [30, 225, 0], 1.2)}})]);
outputs.push([path.join(assets, 'models/item/wall_mirror.json'), json({parent: 'apocalypse_firstlight:block/wall_mirror', gui_light: 'side',
  display: {...heldDisplay(B.mirror.PARTS.flatMap(p => p.v.map(([x, y, z]) => [x + 8, y, z + 8])), {size: 0.6, rotations: {fixed: [0, 180, 0]}}), gui: guiCentred(B.mirror, [0, 180, 0], 1.25)}})]);   // fitted as the lavatory

const tris = b => b.PARTS.reduce((s, p) => s + p.f.reduce((t, f) => t + f.ids.length - 2, 0), 0);
export const stats = Object.fromEntries(Object.entries(B).map(([k, b]) => [k, {triangles: tris(b), texelsPerPx: b.UV.S, islands: b.UV.islands.length}]));
stats.coplanar = coplanar;
const isMain = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  console.log(JSON.stringify(stats));
  const pi = process.argv.indexOf('--preview');
  if (pi > 0) {
    const dir = process.argv[pi + 1]; fs.mkdirSync(dir, {recursive: true});
    for (const [file, obj] of objs) fs.writeFileSync(path.join(dir, file + '.obj'), obj);
    for (const b of Object.values(B)) ['', '_s', '_n'].forEach((k, i) => fs.writeFileSync(path.join(dir, `${b.id}${k}.png`), b.maps[i]));
    console.log('preview written to ' + dir);
  } else if (process.argv.includes('--check')) {
    for (const [file, data] of outputs) { const cur = fs.existsSync(file) ? fs.readFileSync(file) : null; if (!cur || !cur.equals(Buffer.isBuffer(data) ? data : Buffer.from(data))) throw new Error('stale ' + path.relative(ROOT, file)); }
    console.log('CHECK OK');
  } else {
    for (const [file] of outputs) fs.mkdirSync(path.dirname(file), {recursive: true});
    for (const [file, data] of outputs) fs.writeFileSync(file, data);
    console.log('wrote ' + outputs.length + ' files');
  }
}
