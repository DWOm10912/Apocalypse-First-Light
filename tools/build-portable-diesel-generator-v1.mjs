// Portable diesel generator V1 (docs/machines/portable_diesel_generator_v1.md): a 5 kW open-frame, air-cooled,
// single-cylinder diesel generator with a recoil starter, one block, Pure Mesh + LabPBR (AFL Animated Block Mesh Runtime).
// The user approved concept round 1 on 2026-10-09 ("都按你的建议来，开始做吧"): outlets only, a cold engine catching on
// about 40 % of pulls, a yellow tank on a black frame, no brand; the pull animated ("最好是做一个拉动的动画").
//   frame      two side loops of 32 mm black tube with bent corners, cross tubes, rubber mounts under a cradle plate, rubber
//              feet; two wheels on an axle at the engine end, a U handle at the alternator end (all inside the block)
//   engine     crankcase, finned barrel, head and rocker cover, decompression lever, fan shroud with intake slots, the
//              recoil starter and its rope guide, the T handle (bone 'handle', channel 'pull': pulled out 0.55 m along the
//              pull line; the rope is drawn live by client/PortableGeneratorRenderer from the guide to the handle), air
//              cleaner, injection pump with the red STOP knob, speed lever, oil filler, fuel line
//   exhaust    header, muffler under a plain heat shield with two clamp bands (a perforated one could not be drawn clean at 15
//              texels a px: its holes came out as blocky specks), outlet down and back
//   alternator body, adaptor ring, end bell with vent slots
//   panel      printed face (tools/draw-portable-diesel-generator-panel-v1.mjs, in a reserved corner of the atlas), the
//              voltmeter (bezel, glass, needle on the follower channel 'volts'), the hour meter (bezel, glass, five drums
//              on wrapping channels 'h0'..'h4'), the oil lamp, three breaker buttons (channel 'trip': they pop out 5 mm and
//              show a white band), two NEMA 5-15R duplex bodies (their faces from the art; the game's one socket, as the
//              wall outlet's: user 2026-10-09, "目前游戏里面的不就一种通用插头？"), the ground post
//   tank       rounded 15 L tank on the top rails, float gauge (needle on the follower channel 'fuel'); the filler, built to be
//              looked into with its cap open (2026-10-09, user: "打开盖子，里面的细节你又没有做"): a real opening through the tank
//              top, its walls the tank's dark inside, a welded flange with four bolts, the neck, a strainer basket with its rim, and
//              under it the diesel's surface (bone 'fuel_level', on channel 'fuel': it rises with the fuel); the hinged cap
//              (channel 'cap') shows its rubber gasket and threaded spigot when it stands open
// Frames: built in concept metres (x along the set, the engine end at -x; y up; z toward the front, the panel side;
// origin at the footprint's centre on the ground), written to the rig's model frame (block bottom centre, drawn facing
// north): model = 16 * [-x, y, -z]. Java mirrors FACTS in the structure frame (px, north-facing: x from the west edge, y up,
// z 0 = the front face): structure = model + (8, 0, 8).
//   node tools/build-portable-diesel-generator-v1.mjs                -> writes models, blockstate, rig, atlas, sources
//   node tools/build-portable-diesel-generator-v1.mjs --check        -> verifies every output is up to date
//   node tools/build-portable-diesel-generator-v1.mjs --preview DIR  -> writes only OBJ (rest / pulled) + maps into DIR
import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {Part, extrude, revolve, unwrap, paint, png, readPng, zFightLevels, area2, add, sub, mul, dot, cross, norm, newell, M4} from './cube-slab-mesh-lib.mjs';
import {convert, serializeCompact} from './export-afl-mesh.mjs';
import {heldDisplay} from './item-held-display.mjs';
import {PANEL, OUT as PANEL_ART, STRIP, SCALE as ART_SCALE} from './draw-portable-diesel-generator-panel-v1.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
function assert(c, m) { if (!c) throw new Error(m); }
const K = 16, D2R = Math.PI / 180;
const r3 = v => +v.toFixed(3) || 0, r6 = v => +v.toFixed(6) || 0, r12 = v => +v.toFixed(12) || 0;
const NS = 'apocalypse_firstlight:block/', ID = 'portable_diesel_generator';
const json = v => JSON.stringify(v, null, 2) + '\n';

// =============================== dimensions (metres, concept frame) ===============================
export const G = {
  frame: {x: 0.35, z: 0.23, y0: 0.03, y1: 0.50, r: 0.016, bend: 0.035},
  crossBottom: [-0.30, -0.17, 0.19, 0.30], crossTop: [-0.22, 0.24],
  mounts: {x: [-0.17, 0.19], z: [-0.09, 0.09], y0: 0.046, y1: 0.077, r: 0.022},
  plate: {x0: -0.25, x1: 0.27, z: 0.15, y0: 0.077, y1: 0.089},
  wheel: {x: -0.27, y: 0.095, z: 0.28, r: 0.095, w: 0.05, hub: 0.045},
  tank: {x0: -0.30, x1: 0.28, z: 0.19, y0: 0.517, y1: 0.645, r: 0.05},
  cap: {x: -0.17, z: 0.06, r: 0.04, y0: 0.649, y1: 0.672, degrees: 110, ticks: 10},
  filler: {hole: 0.026, floor: 0.555, basket: 0.598, full: 0.585},   // the opening's radius; the empty level; the basket's bottom; the full level
  gauge: {x: 0.06, z: 0.08, r: 0.026},
  // the recoil starter on the fan shroud's outer face, its rope guide, the T handle at rest and the pull line
  shroudFace: -0.322,
  starter: {y: 0.265, z: 0.0, r: 0.088},
  guide: [-0.338, 0.335, 0.05],
  pull: {dir: [-1, 0.62, 0.22], length: 0.55, ticks: 5},
  muffler: {y: 0.375, z: -0.165, r: 0.055, x0: -0.22, x1: 0.06},
  alternator: {y: 0.215, r: 0.128, x0: -0.008, x1: 0.235},
  box: {x0: -0.004, x1: 0.304, y0: 0.211, y1: 0.459, z0: 0.105, z1: 0.204},
  panel: {x0: 0.0, y1: 0.455, front: 0.208, back: 0.203},
  stop: {x: -0.07, y: 0.29, z: 0.181, r: 0.016},
};
const PU = PANEL.metres / PANEL.w;   // metres per panel unit
const pan = (u, v) => [G.panel.x0 + u * PU, G.panel.y1 - v * PU];
const PZ = G.panel.front, u2m = u => u * PU;
const PULL = norm(G.pull.dir);
const HANDLE_REST = add(G.guide, mul(PULL, 0.022));   // the grip's centre at rest, just out of the guide

// =============================== builders (inputs in metres, geometry in px) ===============================
const PARTS = [], byName = new Map();
function P(name, bone, mat, opts = {}) { assert(!byName.has(name), 'duplicate part ' + name); const p = new Part(name, bone, mat); Object.assign(p, opts); PARTS.push(p); byName.set(name, p); return p; }
const m = v => v * K;
const orient = (L, ccw) => (area2(L) > 0) === ccw ? L : L.slice().reverse();
const shapeOf = (outer, holes = []) => ({outer: orient(outer, true), holes: holes.map(h => orient(h, false))});
const rect = (a0, b0, a1, b1) => [[a0, b0], [a1, b0], [a1, b1], [a0, b1]];
const circle = (ca, cb, r, seg, ph = Math.PI / seg) => Array.from({length: seg}, (_, i) => [ca + r * Math.cos(ph + 2 * Math.PI * i / seg), cb + r * Math.sin(ph + 2 * Math.PI * i / seg)]);
const roundRect = (a0, b0, a1, b1, r, seg = 5) => { const out = [], c = [[a1 - r, b0 + r, -90], [a1 - r, b1 - r, 0], [a0 + r, b1 - r, 90], [a0 + r, b0 + r, 180]];
  for (const [ca, cb, s] of c) for (let i = 0; i <= seg; i++) { const a = (s + 90 * i / seg) * D2R; out.push([ca + r * Math.cos(a), cb + r * Math.sin(a)]); } return out; };
const slabY = (part, outline, y0, y1, ch = 0, holes = []) => extrude(part, 'y', shapeOf(outline.map(([x, z]) => [m(z), m(x)]), holes.map(h => h.map(([x, z]) => [m(z), m(x)]))), m(y0), m(y1), m(ch));
const slabZ = (part, outline, z0, z1, ch = 0, holes = []) => extrude(part, 'z', shapeOf(outline.map(([x, y]) => [m(x), m(y)]), holes.map(h => h.map(([x, y]) => [m(x), m(y)]))), m(z0), m(z1), m(ch));
const slabX = (part, outline, x0, x1, ch = 0, holes = []) => extrude(part, 'x', shapeOf(outline.map(([z, y]) => [m(z), m(y)]), holes.map(h => h.map(([z, y]) => [m(z), m(y)]))), m(x0), m(x1), m(ch));
const box = (part, a, b, ch = 0) => slabY(part, rect(a[0], a[2], b[0], b[2]), a[1], b[1], Math.min(ch, ...[0, 1, 2].map(k => Math.abs(b[k] - a[k]) / 3)));
function placed(part, build, f) { const n0 = part.v.length; build(); for (let i = n0; i < part.v.length; i++) part.v[i] = f(part.v[i]); }
const rotAbout = (o, e) => { const M = M4.about(o.map(m), e); return q => M4.pt(M, q); };
function lathe(part, axis, c, outline, seg = 24) {
  const ring = outline[0].length === 3, s0 = outline[0][0], s1 = outline[outline.length - 1][0];
  const loop = ring ? [...outline.map(([s, , ri]) => [s, ri]), ...outline.slice().reverse().map(([s, ro]) => [s, ro])]
    : [[s0, 0], [s1, 0], ...outline.slice().reverse()];
  const prof = loop.map(([s, r]) => [m(s), m(r)]);
  if (area2(prof) < 0) prof.reverse();
  const C = c.map(m);
  placed(part, () => revolve(part, 0, 0, prof, seg), q => axis === 'z' ? [C[0] + q[0], C[1] + q[1], q[2]]
    : axis === 'x' ? [q[2], C[1] + q[1], C[2] - q[0]] : [C[0] + q[0], q[2], C[2] - q[1]]);
}
function latheLoop(part, axis, c, loop, seg) {
  const prof = loop.map(([s, r]) => [m(s), m(r)]); if (area2(prof) < 0) prof.reverse();
  const C = c.map(m);
  placed(part, () => revolve(part, 0, 0, prof, seg), q => axis === 'z' ? [C[0] + q[0], C[1] + q[1], q[2]]
    : axis === 'x' ? [q[2], C[1] + q[1], C[2] - q[0]] : [C[0] + q[0], q[2], C[2] - q[1]]);
}
const bezel = (part, c, ri, ro, ro2, h, seg) => latheLoop(part, 'z', c, [[PZ, ri], [PZ + h, ri], [PZ + h, ro2], [PZ, ro]], seg);
function cyl(part, axis, c, r, s0, s1, seg = 20, ch = 0) {
  const c0 = Math.min(ch, r / 3, Math.abs(s1 - s0) / 3), d = Math.sign(s1 - s0);
  lathe(part, axis, c, c0 > 0 ? [[s0, r - c0], [s0 + d * c0, r], [s1 - d * c0, r], [s1, r - c0]] : [[s0, r], [s1, r]], seg);
}
const annulus = (part, axis, c, ri, ro, s0, s1, seg = 24) => lathe(part, axis, c, [[s0, ro, ri], [s1, ro, ri]], seg);
function sweep(part, pts, r, seg = 10, closed = false) {
  const P3 = pts.map(q => q.map(m)), R = m(r), n = P3.length;
  const tan = i => { const a = P3[closed ? (i - 1 + n) % n : Math.max(0, i - 1)], b = P3[closed ? (i + 1) % n : Math.min(n - 1, i + 1)]; return norm(sub(b, a)); };
  let ref = Math.abs(tan(0)[1]) < 0.9 ? [0, 1, 0] : [1, 0, 0];
  const rings = [];
  for (let i = 0; i < n; i++) {
    const t = tan(i), u = norm(sub(ref, mul(t, dot(ref, t)))), w = cross(t, u); ref = u;
    rings.push(Array.from({length: seg}, (_, j) => { const a = 2 * Math.PI * j / seg; return part.vtx(add(P3[i], add(mul(u, R * Math.cos(a)), mul(w, R * Math.sin(a))))); }));
  }
  if (closed) {   // parallel transport round a loop does not come back to its start: re-seat the last ring's phase
    const first = rings[0].map(id => part.v[id]), last = rings[n - 1];
    let best = 0, bd = Infinity;
    for (let s = 0; s < seg; s++) { const d = Math.hypot(...sub(part.v[last[s]], first[0])); if (d < bd) { bd = d; best = s; } }
    rings[n - 1] = last.slice(best).concat(last.slice(0, best));
  }
  const segs = closed ? n : n - 1;
  for (let i = 0; i < segs; i++) { const A = rings[i], B = rings[(i + 1) % n], c = mul(add(P3[i], P3[(i + 1) % n]), 0.5);
    for (let j = 0; j < seg; j++) { const k = (j + 1) % seg, q = [A[j], A[k], B[k], B[j]], mid = q.map(id => part.v[id]).reduce((s, p) => add(s, mul(p, 0.25)), [0, 0, 0]);
      part.face(q, sub(mid, c), 'side'); } }
  if (!closed) for (const [i, s] of [[0, -1], [n - 1, 1]]) { const c = part.vtx(P3[i]), t = mul(tan(i), s);
    for (let j = 0; j < seg; j++) part.face([c, rings[i][j], rings[i][(j + 1) % seg]], t, 'cap'); }
}
/** Points of a bent tube's corner: an arc of radius r about centre c in the plane (axes a, b), from angle s0 to s1 (degrees). */
const arc = (c, a, b, r, s0, s1, n = 4) => Array.from({length: n + 1}, (_, i) => { const t = (s0 + (s1 - s0) * i / n) * D2R; return add(c, add(mul(a, r * Math.cos(t)), mul(b, r * Math.sin(t)))); });

// =============================== materials ===============================
const MAT = (c, hl, sm, se, f0 = 20) => ({c, hl, sm, se, f0});
const hash3 = (a, b, c) => { let h = Math.imul(a | 0, 374761393) ^ Math.imul(b | 0, 668265263) ^ Math.imul(c | 0, 2147483647); h = Math.imul(h ^ (h >>> 13), 1274126177); return ((h ^ (h >>> 16)) >>> 0) / 4294967295; };
const sm3 = t => t * t * (3 - 2 * t);
function vn3(x, y, z, seed) {
  const xi = Math.floor(x), yi = Math.floor(y), zi = Math.floor(z), tx = sm3(x - xi), ty = sm3(y - yi), tz = sm3(z - zi);
  const h = (i, j, k) => hash3(xi + i, yi + j + 977 * seed, zi + k), l = (a, b, t) => a + (b - a) * t;
  return l(l(l(h(0, 0, 0), h(1, 0, 0), tx), l(h(0, 1, 0), h(1, 1, 0), tx), ty), l(l(h(0, 0, 1), h(1, 0, 1), tx), l(h(0, 1, 1), h(1, 1, 1), tx), ty), tz) * 2 - 1;
}
const mottled = (base, k = 0.025, seed = 1) => pos => { const [x, y, z] = pos.map(v => v / K * 100); return {c: base.map(v => v * (1 + k * vn3(x / 60, y / 60, z / 60, seed) + k * 0.4 * vn3(x / 18, y / 18, z / 18, seed + 7)))}; };
export const MATS = {
  frame:    MAT(mottled([30, 31, 33], 0.02, 2), 10, 120, 140),       // black powder coat
  tank:     MAT(mottled([206, 156, 40], 0.02, 3), 10, 140, 160),     // safety yellow, kept below glare
  cap:      MAT([26, 27, 28], 6, 96, 116),
  shroud:   MAT(mottled([58, 61, 66], 0.02, 4), 10, 112, 132),       // dark grey enamel
  alu:      MAT(mottled([142, 145, 148], 0.03, 5), 10, 112, 132, 230),   // cast aluminium (crankcase, head, end bell)
  fins:     MAT(mottled([112, 115, 118], 0.03, 6), 8, 96, 118, 230),
  alt:      MAT(mottled([38, 40, 43], 0.02, 7), 10, 104, 124),       // the alternator's black enamel
  black:    MAT([24, 25, 26], 6, 90, 110),
  steel:    MAT(mottled([150, 152, 155], 0.02, 8), 14, 150, 176, 230),
  chrome:   MAT([188, 190, 194], 18, 205, 220, 255),
  muffler:  MAT(mottled([96, 88, 78], 0.05, 9), 8, 80, 100),          // heat-blued exhaust steel
  shield:   MAT(mottled([146, 147, 146], 0.03, 11), 10, 120, 150, 230),   // the heat shield's bright steel
  rubber:   MAT([22, 22, 23], 2, 40, 50),
  red:      MAT([178, 34, 28], 14, 150, 170),
  filter:   MAT(mottled([44, 46, 49], 0.02, 10), 8, 96, 116),
  brass:    MAT([186, 152, 84], 16, 170, 190, 255),
  paper:    MAT([226, 221, 208], 4, 110, 120),
  needle:   MAT([226, 92, 44], 0, 140, 140),
  lampA:    MAT([110, 72, 22], 6, 200, 210),
  white:    MAT([232, 230, 222], 6, 130, 150),                       // a tripped breaker's band
  tankInside: MAT([38, 36, 32], 0, 60, 70),                         // the tank's inner wall, seen down the filler
  strainer: MAT(mottled([84, 86, 88], 0.03, 12), 6, 120, 140, 230),   // the filler's strainer basket
  diesel:   MAT([58, 44, 16], 4, 226, 226),                          // the fuel's surface: dark amber, glossy
  glass:    MAT([160, 180, 188], 0, 235, 235, 10),
};
export const GLASS_ALPHA = 56;

// =============================== frame ===============================
const F = G.frame;
{
  const b = F.bend, xs = F.x, X = [1, 0, 0], Y = [0, 1, 0];
  for (const zs of [1, -1]) {
    const z = zs * F.z, c = (x, y) => [x, y, z];
    const pts = [c(-xs + b, F.y0), c(xs - b, F.y0), ...arc(c(xs - b, F.y0 + b), X, Y, b, -90, 0).slice(1, -1), c(xs, F.y0 + b), c(xs, F.y1 - b),
      ...arc(c(xs - b, F.y1 - b), X, Y, b, 0, 90).slice(1, -1), c(xs - b, F.y1), c(-xs + b, F.y1), ...arc(c(-xs + b, F.y1 - b), X, Y, b, 90, 180).slice(1, -1),
      c(-xs, F.y1 - b), c(-xs, F.y0 + b), ...arc(c(-xs + b, F.y0 + b), X, Y, b, 180, 270).slice(1, -1)];
    sweep(P(`frame_loop_${zs > 0 ? 'f' : 'b'}`, 'body', 'frame'), pts, F.r, 12, true);
  }
  for (const x of G.crossBottom) sweep(P(`cross_bottom_${x}`, 'body', 'frame'), [[x, F.y0, -F.z + 0.006], [x, F.y0, F.z - 0.006]], F.r * 0.9, 12);
  for (const x of G.crossTop) sweep(P(`cross_top_${x}`, 'body', 'frame'), [[x, F.y1, -F.z + 0.006], [x, F.y1, F.z - 0.006]], F.r * 0.85, 12);
  const M = G.mounts;
  for (const x of M.x) for (const z of M.z) cyl(P(`mount_${x}_${z}`, 'body', 'rubber'), 'y', [x, 0, z], M.r, M.y0, M.y1, 12, 0.003);
  const PL = G.plate; box(P('cradle_plate', 'body', 'frame'), [PL.x0, PL.y0, -PL.z], [PL.x1, PL.y1, PL.z], 0.003);
  // rubber feet under the bottom rails at the handle end (the wheels carry the engine end)
  for (const z of [F.z, -F.z]) cyl(P(`foot_${z}`, 'body', 'rubber'), 'y', [0.30, 0, z], 0.021, 0.0, 0.018, 12, 0.003);
  // wheel kit: axle, two brackets off the bottom rails, two wheels with hubs
  const W = G.wheel;
  sweep(P('axle', 'body', 'steel'), [[W.x, W.y, -W.z - W.w / 2 - 0.004], [W.x, W.y, W.z + W.w / 2 + 0.004]], 0.009, 10);
  for (const zs of [1, -1]) {
    const zb = zs * (F.z + F.r + 0.0045);
    box(P(`wheel_bracket_${zs}`, 'body', 'frame'), [W.x - 0.025, F.y0 - 0.008, zb - 0.003], [W.x + 0.025, W.y + 0.022, zb + 0.003], 0.002);
    const zw = zs * W.z, half = W.w / 2;
    lathe(P(`wheel_tyre_${zs}`, 'body', 'rubber'), 'z', [W.x, W.y, 0], [[zw - half, W.r - 0.012, W.hub + 0.004], [zw - half + 0.008, W.r, W.hub + 0.004], [zw + half - 0.008, W.r, W.hub + 0.004], [zw + half, W.r - 0.012, W.hub + 0.004]], 24);
    lathe(P(`wheel_hub_${zs}`, 'body', 'steel'), 'z', [W.x, W.y, 0], [[zw - half + 0.004, W.hub + 0.0045, 0.0095], [zw + half - 0.004, W.hub + 0.0045, 0.0095]], 18);
  }
  // U handle at the alternator end, rubber grips
  const hz = F.z, hp = [[F.x - 0.006, 0.44, hz], [0.42, 0.505, hz], [0.44, 0.515, hz - 0.018], [0.44, 0.515, -hz + 0.018], [0.42, 0.505, -hz], [F.x - 0.006, 0.44, -hz]];
  sweep(P('u_handle', 'body', 'frame'), hp, F.r * 0.9, 12);
  for (const s of [1, -1]) annulus(P(`grip_${s}`, 'body', 'rubber'), 'z', [0.44, 0.515, 0], F.r * 0.9 + 0.0005, 0.021, s > 0 ? 0.06 : -0.17, s > 0 ? 0.17 : -0.06, 14);
}

// =============================== engine ===============================
{
  const B = 'body';
  box(P('crankcase', B, 'alu'), [-0.27, G.plate.y1, -0.12], [-0.07, 0.29, 0.12], 0.012);
  cyl(P('barrel', B, 'fins'), 'y', [-0.16, 0, 0], 0.058, 0.288, 0.422, 20);
  for (let i = 0; i < 8; i++) box(P(`barrel_fin_${i}`, B, 'fins'), [-0.235, 0.298 + i * 0.0145, -0.075], [-0.085, 0.304 + i * 0.0145, 0.075], 0.001);
  box(P('head', B, 'alu'), [-0.235, 0.42, -0.075], [-0.085, 0.475, 0.075], 0.008);
  box(P('rocker_cover', B, 'shroud'), [-0.225, 0.474, -0.062], [-0.095, 0.50, 0.062], 0.006);
  sweep(P('decomp_lever', B, 'red'), [[-0.13, 0.49, 0.04], [-0.13, 0.515, 0.04], [-0.095, 0.524, 0.068]], 0.0045, 8);
  cyl(P('injector', B, 'steel'), 'z', [-0.16, 0.45, 0], 0.01, 0.074, 0.094, 10);
  sweep(P('fuel_line', B, 'black'), [[-0.05, G.tank.y0 + 0.004, 0.10], [-0.05, 0.44, 0.13], [-0.06, 0.33, 0.14], [-0.07, 0.30, 0.138]], 0.004, 8);
  // fan shroud round the flywheel fan and over the barrel, intake slots on its face
  box(P('fan_shroud', B, 'shroud'), [G.shroudFace, 0.115, -0.15], [-0.268, 0.445, 0.15], 0.015);
  box(P('shroud_hood', B, 'shroud'), [-0.30, 0.38, -0.13], [-0.18, 0.455, 0.13], 0.012);
  for (const [y0, y1] of [[0.135, 0.147], [0.155, 0.167], [0.375, 0.387], [0.395, 0.407]])
    box(P(`shroud_slot_${y0}`, B, 'black'), [G.shroudFace - 0.0015, y0, -0.11], [G.shroudFace + 0.002, y1, 0.11]);
  // the recoil starter: a dished cover with ribs on the shroud face, the rope guide (an eyelet) at its top front
  const S = G.starter, f0 = G.shroudFace;
  lathe(P('starter_cover', B, 'shroud'), 'x', [0, S.y, S.z], [[f0 + 0.002, S.r], [f0 - 0.012, S.r], [f0 - 0.016, S.r - 0.012], [f0 - 0.021, 0.05], [f0 - 0.023, 0.02]], 32);
  for (let i = 0; i < 8; i++) { const a = i * 45 * D2R, rib = P(`starter_rib_${i}`, B, 'shroud');
    placed(rib, () => box(rib, [f0 - 0.02, S.y + 0.052, S.z - 0.003], [f0 - 0.012, S.y + 0.078, S.z + 0.003], 0.001), rotAbout([0, S.y, S.z], [a / D2R, 0, 0])); }
  annulus(P('rope_guide', B, 'chrome'), 'x', [0, G.guide[1], G.guide[2]], 0.004, 0.009, f0 - 0.004, G.guide[0] - 0.002, 12);
  // air cleaner and its elbow, injection pump with the STOP knob, speed lever, oil filler
  cyl(P('air_cleaner', B, 'filter'), 'y', [-0.20, 0, 0.15], 0.052, 0.36, 0.46, 20, 0.004);
  cyl(P('air_cleaner_lid', B, 'shroud'), 'y', [-0.20, 0, 0.15], 0.055, 0.46, 0.472, 20, 0.003);
  box(P('air_cleaner_nut', B, 'black'), [-0.212, 0.472, 0.142], [-0.188, 0.480, 0.158], 0.001);
  sweep(P('intake_elbow', B, 'filter'), [[-0.20, 0.40, 0.11], [-0.20, 0.43, 0.07]], 0.016, 10);
  box(P('injection_pump', B, 'alu'), [-0.10, 0.23, 0.115], [-0.04, 0.30, 0.155], 0.004);
  const T = G.stop;
  cyl(P('stop_stem', B, 'steel'), 'z', [T.x, T.y, 0], 0.004, 0.15, T.z - 0.01, 8);
  lathe(P('stop_knob', B, 'red'), 'z', [T.x, T.y, 0], [[T.z - 0.016, 0.006], [T.z - 0.012, 0.013], [T.z - 0.004, T.r], [T.z + 0.006, 0.013], [T.z + 0.012, 0.004]], 16);
  sweep(P('speed_lever', B, 'steel'), [[-0.035, 0.28, 0.14], [-0.02, 0.33, 0.16]], 0.004, 8);
  cyl(P('speed_knob', B, 'black'), 'z', [-0.02, 0.33, 0], 0.008, 0.156, 0.172, 10);
  cyl(P('oil_filler', B, 'black'), 'y', [-0.09, 0, 0.10], 0.013, 0.288, 0.312, 12, 0.002);
}

// =============================== exhaust ===============================
{
  const B = 'body', M = G.muffler;
  lathe(P('muffler', B, 'muffler'), 'x', [0, M.y, M.z], [[M.x0, M.r - 0.012], [M.x0 + 0.01, M.r], [M.x1 - 0.01, M.r], [M.x1, M.r - 0.012]], 24);
  annulus(P('heat_shield', B, 'shield'), 'x', [0, M.y, M.z], M.r + 0.006, M.r + 0.010, M.x0 + 0.02, M.x1 - 0.02, 28);
  for (const x of [M.x0 + 0.05, M.x1 - 0.05]) annulus(P(`shield_band_${x}`, B, 'steel'), 'x', [0, M.y, M.z], M.r + 0.0095, M.r + 0.0125, x - 0.007, x + 0.007, 28);
  sweep(P('exhaust_header', B, 'muffler'), [[-0.16, 0.445, -0.07], [-0.16, 0.445, -0.11], [-0.15, 0.42, -0.15], [-0.13, 0.40, -0.165]], 0.016, 10);
  sweep(P('exhaust_outlet', B, 'muffler'), [[M.x1 - 0.005, M.y, M.z], [M.x1 + 0.03, M.y, M.z], [M.x1 + 0.06, M.y - 0.03, M.z - 0.025], [M.x1 + 0.07, M.y - 0.045, M.z - 0.04]], 0.013, 10);
}
export const EXHAUST = [G.muffler.x1 + 0.07, G.muffler.y - 0.045, G.muffler.z - 0.04];

// =============================== alternator and control box ===============================
{
  const B = 'body', A = G.alternator;
  cyl(P('adaptor_ring', B, 'alu'), 'x', [0, A.y, 0], 0.135, -0.075, -0.004, 28, 0.004);
  cyl(P('alternator', B, 'alt'), 'x', [0, A.y, 0], A.r, A.x0, A.x1, 32, 0.008);
  annulus(P('alternator_band', B, 'alu'), 'x', [0, A.y, 0], A.r - 0.002, A.r + 0.004, 0.10, 0.12, 32);
  lathe(P('end_bell', B, 'alu'), 'x', [0, A.y, 0], [[A.x1 - 0.004, 0.12], [A.x1 + 0.022, 0.118], [A.x1 + 0.034, 0.10], [A.x1 + 0.04, 0.06], [A.x1 + 0.041, 0.02]], 32);
  for (let i = 0; i < 12; i++) { const a = i * 30, s = P(`bell_slot_${i}`, B, 'black');
    placed(s, () => box(s, [A.x1 + 0.031, A.y + 0.074, -0.0035], [A.x1 + 0.0395, A.y + 0.096, 0.0035]), rotAbout([0, A.y, 0], [a, 0, 0])); }
  const X = G.box; box(P('control_box', B, 'black'), [X.x0, X.y0, X.z0], [X.x1, X.y1, X.z1], 0.004);
}

// =============================== panel ===============================
{
  const H = PANEL.hours, [wx0, wy1] = pan(H.window[0], H.window[1]), [wx1, wy0] = pan(H.window[2], H.window[3]);
  const [px0, py1] = pan(0, 0), [px1, py0] = pan(PANEL.w, PANEL.h);
  slabZ(P('panel_plate', 'body', 'black', {custom: 'plate'}), rect(px0, py0, px1, py1), G.panel.back, PZ, 0, [rect(wx0, wy0, wx1, wy1)]);
  const [bx0, by1] = pan(H.bezel[0], H.bezel[1]), [bx1, by0] = pan(H.bezel[2], H.bezel[3]);
  slabZ(P('hours_bezel', 'body', 'chrome'), rect(bx0, by0, bx1, by1), PZ, PZ + 0.004, 0.0012, [rect(wx0, wy0, wx1, wy1)]);
  box(P('hours_glass', 'body', 'glass'), [wx0 - 0.002, wy0 - 0.002, PZ + 0.0026], [wx1 + 0.002, wy1 + 0.002, PZ + 0.0031]);
  // voltmeter: bezel, glass, needle (follower channel 'volts') and hub
  { const V = PANEL.volt, [cx, cy] = pan(...V.c), r = u2m(V.r);
    bezel(P('volt_bezel', 'body', 'chrome'), [cx, cy, 0], r, r + u2m(6), r + u2m(4), 0.004, 28);
    cyl(P('volt_glass', 'body', 'glass'), 'z', [cx, cy, 0], r + u2m(1.5), PZ + 0.0026, PZ + 0.0031, 28);
    const a = PANEL.sweep[0], dir = [Math.cos(a), -Math.sin(a)], nrm = [-dir[1], dir[0]];
    const at = (l, w) => [cx + dir[0] * l + nrm[0] * w, cy + dir[1] * l + nrm[1] * w], tip = r - u2m(6), tail = u2m(9);
    slabZ(P('needle_volts_mesh', 'needle_volts', 'needle'), [at(-tail, -u2m(1.5)), at(tip, -u2m(0.5)), at(tip + u2m(1.2), 0), at(tip, u2m(0.5)), at(-tail, u2m(1.5))], PZ + 0.0010, PZ + 0.0017);
    cyl(P('needle_volts_hub', 'needle_volts', 'black'), 'z', [cx, cy, 0], u2m(4.5), PZ + 0.0003, PZ + 0.0022, 12); }
  // hour meter drums (custom UVs: digit cells)
  const DR = H.drums, chord = u2m(DR.face), rr = chord / (2 * Math.sin(Math.PI / 10)), [, cyc] = pan(0, (H.window[1] + H.window[3]) / 2);
  const axisZ = PZ - 0.001 - rr * Math.cos(Math.PI / 10);
  for (let i = 0; i < DR.n; i++) {
    const [dx0] = pan(DR.x0 + i * (DR.w + DR.gap), 0), [dx1] = pan(DR.x0 + i * (DR.w + DR.gap) + DR.w, 0);
    const poly = Array.from({length: 10}, (_, k) => { const a = Math.PI / 10 + 2 * Math.PI * k / 10; return [axisZ + rr * Math.cos(a), cyc + rr * Math.sin(a)]; });
    slabX(P(`drum_${i}_mesh`, `drum_${i}`, 'black', {custom: 'drum', drum: i, axis: [cyc, axisZ]}), poly, dx0, dx1);
  }
  // oil lamp: bezel and amber lens
  { const L = PANEL.lamp, [cx, cy] = pan(...L.c), ri = u2m(L.lens), ro = u2m(L.bezel);
    bezel(P('oil_lamp_bezel', 'body', 'chrome'), [cx, cy, 0], ri, ro, ro - u2m(2), 0.004, 20);
    lathe(P('oil_lamp_lens', 'body', 'lampA'), 'z', [cx, cy, 0], [[PZ - 0.001, ri - 0.0003], [PZ + 0.003, ri - 0.0003], [PZ + 0.0048, ri * 0.72], [PZ + 0.0057, ri * 0.3]], 16); }
  // breakers: a collar on the plate, the button on its own bone (channel 'trip'): a white band under a black cap, the band
  // inside the collar until the button pops out
  PANEL.breakers.forEach((Bk, i) => {
    const [cx, cy] = pan(...Bk.c), rc = u2m(PANEL.breaker.cap), ro = u2m(PANEL.breaker.ring);
    bezel(P(`breaker_${i}_collar`, 'body', 'black'), [cx, cy, 0], rc + 0.0004, ro, ro - u2m(1.5), 0.004, 18);
    cyl(P(`breaker_${i}_band`, `breaker_${i}`, 'white'), 'z', [cx, cy, 0], rc, PZ - 0.002, PZ + 0.0038, 16);
    cyl(P(`breaker_${i}_cap`, `breaker_${i}`, 'black'), 'z', [cx, cy, 0], rc, PZ + 0.0038, PZ + 0.0075, 16, 0.0012);
  });
  // receptacle bodies: the two duplexes, their fronts from the art (custom UVs), the rest plain nylon
  PANEL.duplex.forEach((D, i) => { const [x0, y1] = pan(D.rect[0], D.rect[1]), [x1, y0] = pan(D.rect[2], D.rect[3]);
    slabZ(P(`duplex_${i}`, 'body', 'black', {custom: 'art'}), rect(x0, y0, x1, y1), PZ - 0.001, PZ + 0.006); });
  // the ground post: a brass body and nut
  { const [cx, cy] = pan(...PANEL.ground.c); cyl(P('ground_post', 'body', 'brass'), 'z', [cx, cy, 0], u2m(7), PZ - 0.001, PZ + 0.008, 16, 0.001);
    cyl(P('ground_post_nut', 'body', 'brass'), 'z', [cx, cy, 0], u2m(4.2), PZ + 0.008, PZ + 0.0125, 6); }
}

// =============================== tank, filler cap, float gauge ===============================
{
  const Tk = G.tank;
  const C = G.cap, HR = G.filler.hole;
  // the tank with the filler opening through it (an extrusion's hole goes through: a plug closes it low down), the opening's
  // walls the tank's dark inside
  const tank = P('tank', 'body', 'tank');
  slabY(tank, roundRect(Tk.x0, -Tk.z, Tk.x1, Tk.z, Tk.r), Tk.y0, Tk.y1, 0.012, [circle(C.x, C.z, HR, 20)]);
  for (const f of tank.f) { const P3 = f.ids.map(i => tank.v[i]), n = norm(newell(P3)), c = P3.reduce((a, q) => add(a, mul(q, 1 / P3.length)), [0, 0, 0]);
    const dx = m(C.x) - c[0], dz = m(C.z) - c[2], d = Math.hypot(dx, dz); if (d < m(HR + 0.015) && (n[0] * dx + n[2] * dz) / (d || 1) > 0.3) f.mat = 'tankInside'; }
  cyl(P('filler_plug', 'body', 'tankInside'), 'y', [C.x, 0, C.z], HR + 0.0004, Tk.y0 + 0.001, G.filler.floor, 20);
  // the diesel's surface (bone 'fuel_level'): at the plug when empty, raised by channel 'fuel' to near the strainer when full
  cyl(P('fuel_surface', 'fuel_level', 'diesel'), 'y', [C.x, 0, C.z], HR + 0.0003, G.filler.floor + 0.002, G.filler.floor + 0.004, 20);
  // the welded flange (it hides the opening's chamfer) and its four bolts, the neck, the strainer basket and its rim
  annulus(P('filler_flange', 'body', 'steel'), 'y', [C.x, 0, C.z], HR, 0.041, Tk.y1 - 0.006, Tk.y1 + 0.0025, 24);
  for (let i = 0; i < 4; i++) { const a = (45 + 90 * i) * D2R; cyl(P(`flange_bolt_${i}`, 'body', 'steel'), 'y', [C.x + 0.0355 * Math.cos(a), 0, C.z + 0.0355 * Math.sin(a)], 0.0035, Tk.y1 + 0.002, Tk.y1 + 0.0045, 6); }
  annulus(P('filler_neck', 'body', 'steel'), 'y', [C.x, 0, C.z], HR, 0.031, Tk.y1 + 0.002, C.y0, 20);
  annulus(P('strainer', 'body', 'strainer'), 'y', [C.x, 0, C.z], 0.0215, 0.0232, G.filler.basket, C.y0 - 0.0006, 20);
  annulus(P('strainer_rim', 'body', 'chrome'), 'y', [C.x, 0, C.z], 0.0215, HR + 0.0003, C.y0 - 0.0005, C.y0 + 0.0012, 20);
  // the hinged cap (bone 'cap', channel 'cap'): knurled, its hinge at its back edge (the -z side)
  lathe(P('cap_body', 'cap', 'cap'), 'y', [C.x, 0, C.z], [[C.y0, C.r - 0.004], [C.y0 + 0.003, C.r], [C.y1 - 0.004, C.r], [C.y1, C.r - 0.006], [C.y1 + 0.002, 0.012]], 24);
  // under the cap (seen when it stands open): the rubber gasket and the threaded spigot that goes into the strainer
  annulus(P('cap_gasket', 'cap', 'rubber'), 'y', [C.x, 0, C.z], 0.0235, 0.0345, C.y0 - 0.0016, C.y0 + 0.0006, 24);
  cyl(P('cap_spigot', 'cap', 'steel'), 'y', [C.x, 0, C.z], 0.0195, C.y0 - 0.011, C.y0 + 0.0008, 20, 0.0015);
  for (let i = 0; i < 3; i++) annulus(P(`cap_thread_${i}`, 'cap', 'steel'), 'y', [C.x, 0, C.z], 0.019, 0.0206, C.y0 - 0.0095 + i * 0.003, C.y0 - 0.0082 + i * 0.003, 20);
  for (let i = 0; i < 12; i++) { const a = i * 30 + 15, k = P(`cap_knurl_${i}`, 'cap', 'cap');
    placed(k, () => box(k, [C.x + C.r - 0.002, C.y0 + 0.004, C.z - 0.004], [C.x + C.r + 0.003, C.y1 - 0.005, C.z + 0.004], 0.001), rotAbout([C.x, 0, C.z], [0, a, 0])); }
  box(P('cap_hinge', 'body', 'steel'), [C.x - 0.012, Tk.y1 - 0.002, C.z - C.r - 0.012], [C.x + 0.012, C.y0 + 0.006, C.z - C.r + 0.002], 0.001);
  // float gauge on the top: bezel, paper face, needle (follower channel 'fuel', E toward the engine end), glass
  const Gg = G.gauge;
  annulus(P('gauge_bezel', 'body', 'chrome'), 'y', [Gg.x, 0, Gg.z], Gg.r, Gg.r + 0.006, Tk.y1 - 0.003, Tk.y1 + 0.006, 20);
  cyl(P('gauge_face', 'body', 'paper'), 'y', [Gg.x, 0, Gg.z], Gg.r + 0.0003, Tk.y1 - 0.004, Tk.y1 + 0.0015, 20);
  slabY(P('fuel_needle_mesh', 'fuel_needle', 'red'), [[Gg.x + 0.004, Gg.z - 0.0025], [Gg.x + 0.004, Gg.z + 0.0025], [Gg.x - 0.021, Gg.z + 0.0009], [Gg.x - 0.021, Gg.z - 0.0009]], Tk.y1 + 0.0018, Tk.y1 + 0.0028);
  cyl(P('fuel_needle_hub', 'fuel_needle', 'black'), 'y', [Gg.x, 0, Gg.z], 0.004, Tk.y1 + 0.0015, Tk.y1 + 0.0036, 10);
  cyl(P('gauge_glass', 'body', 'glass'), 'y', [Gg.x, 0, Gg.z], Gg.r + 0.001, Tk.y1 + 0.0045, Tk.y1 + 0.0052, 20);
}

// =============================== the T handle (bone 'handle', channel 'pull') ===============================
{
  const [hx, hy, hz] = HANDLE_REST;
  cyl(P('handle_grip', 'handle', 'rubber'), 'z', [hx, hy, 0], 0.013, hz - 0.048, hz + 0.048, 14, 0.004);
  cyl(P('handle_core', 'handle', 'black'), 'z', [hx, hy, 0], 0.0075, hz - 0.052, hz + 0.052, 10);
}

// =============================== concept -> model ===============================
const toModel = q => [-q[0], q[1], -q[2]];
for (const p of PARTS) p.v = p.v.map(toModel);
for (const p of PARTS) assert(p.f.length, 'empty part ' + p.name);
// mirrored axes (x and z both flipped is a half turn: proper, windings stay)
for (const p of PARTS) {
  const out = [];
  for (const f of p.f) {
    const P3 = f.ids.map(i => p.v[i]), n = newell(P3);
    const convexQuad = f.ids.length === 4 && [0, 1, 2, 3].every(k => dot(cross(sub(P3[(k + 1) % 4], P3[k]), sub(P3[(k + 2) % 4], P3[(k + 1) % 4])), n) > 1e-14);
    if (f.ids.length === 3 || convexQuad) { out.push(f); continue; }
    assert(f.ids.length === 4, `${f.ids.length}-gon in ${p.name}`);
    const k = [0, 1, 2, 3].find(k => dot(cross(sub(P3[k], P3[(k + 3) % 4]), sub(P3[(k + 1) % 4], P3[k])), n) <= 1e-14) ?? 0;
    out.push({...f, ids: [f.ids[k], f.ids[(k + 1) % 4], f.ids[(k + 2) % 4]]}, {...f, ids: [f.ids[(k + 2) % 4], f.ids[(k + 3) % 4], f.ids[k]]});
  }
  p.f = out;
}

// =============================== closed meshes, coplanar overlaps, boundary faces ===============================
function solidity(p) {
  const key = q => q.map(v => Math.round(v * 1e5)).join(','), dir = new Map(); let vol = 0;
  for (const f of p.f) {
    const P3 = f.ids.map(i => p.v[i]);
    for (let j = 1; j + 1 < P3.length; j++) vol += dot(P3[0], cross(P3[j], P3[j + 1])) / 6;
    for (let j = 0; j < f.ids.length; j++) { const a = key(P3[j]), b = key(P3[(j + 1) % P3.length]); if (a !== b) dir.set(a + '>' + b, (dir.get(a + '>' + b) || 0) + 1); }
  }
  let open = 0, flipped = 0;
  for (const [e, n] of dir) { const [a, b] = e.split('>'); if (!dir.get(b + '>' + a)) open++; if (n > 1) flipped++; }
  return {open, flipped, vol};
}
export const solids = {};
for (const p of PARTS) { const s = solidity(p); if (s.open || s.flipped || s.vol <= 0) solids[p.name] = s; }
assert(!Object.keys(solids).length, 'not closed / wound out: ' + JSON.stringify(solids));
const zf = zFightLevels(PARTS, new Map());
export const coplanar = zf.unresolved.map(u => [u.a, u.b, +u.area.toFixed(4)]);
export const boundaryRisk = [];
const boundaryCheck = () => { for (const p of PARTS) for (const f of p.f) {
  if (f.buried) continue;
  const P3 = f.ids.map(i => p.v[i]), n = norm(newell(P3));
  for (const [k, planes] of [[0, [-8, 8]], [1, [0, 16]], [2, [-8, 8]]]) for (const w of planes)
    if (P3.every(q => Math.abs(q[k] - w) < 1e-4) && n[k] * Math.sign(w || 1) * (w === 0 ? -1 : 1) < -0.99) boundaryRisk.push(p.name + ' @' + 'xyz'[k] + '=' + w);
}
assert(!boundaryRisk.length, 'faces on the block faces facing in: ' + boundaryRisk.slice(0, 6).join(', ')); };

// =============================== bones and their pivots (model px) ===============================
const C = G.cap, capPivot = toModel([m(C.x), m(C.y0), m(C.z - C.r)]);
const BONES = [['body', null, [0, 0, 0]], ['handle', null, toModel(HANDLE_REST.map(m))], ['cap', null, capPivot], ['fuel_level', null, toModel([m(C.x), m(G.filler.floor), m(C.z)])],
  ['fuel_needle', null, toModel([m(G.gauge.x), 0, m(G.gauge.z)])]];
{ const [x, y] = pan(...PANEL.volt.c); BONES.push(['needle_volts', null, toModel([m(x), m(y), 0])]); }
PANEL.breakers.forEach((_, i) => BONES.push([`breaker_${i}`, null, [0, 0, 0]]));
for (let i = 0; i < PANEL.hours.drums.n; i++) { const d = byName.get(`drum_${i}_mesh`), [ay, az] = d.axis; BONES.push([`drum_${i}`, null, toModel([0, m(ay), m(az)])]); }
const boneOf = new Map(BONES.map(b => [b[0], b]));
for (const p of PARTS) assert(boneOf.has(p.bone), 'unknown bone ' + p.bone + ' of ' + p.name);
for (let i = 0; i < PANEL.hours.drums.n; i++) { const d = byName.get(`drum_${i}_mesh`), xs = d.v.map(q => q[0]); boneOf.get(`drum_${i}`)[2][0] = (Math.min(...xs) + Math.max(...xs)) / 2; }
const SWEEP = (PANEL.sweep[1] - PANEL.sweep[0]) / D2R;
const PULL_PX = toModel(mul(PULL, m(G.pull.length)));
export const CHANNELS = {
  // the T handle along the pull line (a fast yank out, ease out; back on the recoil spring the same way)
  pull: {ticks: G.pull.ticks, easing: 'ease_out', transforms: {handle: {translation: PULL_PX}}},
  cap: {ticks: C.ticks, easing: 'ease_in_out', transforms: {cap: {rotation: [C.degrees, 0, 0]}}},
  volts: {ticks: 20, easing: 'ease_in_out', follow: 5, transforms: {needle_volts: {rotation: [0, 0, SWEEP]}}},
  fuel: {ticks: 40, easing: 'ease_in_out', follow: 5, transforms: {fuel_needle: {rotation: [0, 180, 0]}, fuel_level: {translation: [0, m(G.filler.full - G.filler.floor), 0]}}},
  trip: {ticks: 3, easing: 'ease_out', transforms: Object.fromEntries(PANEL.breakers.map((_, i) => [`breaker_${i}`, {translation: [0, 0, -m(0.005)]}]))},
  ...Object.fromEntries(Array.from({length: PANEL.hours.drums.n}, (_, i) => [`h${i}`, {ticks: 4, easing: 'ease_in_out', wrap: true, transforms: {[`drum_${i}`]: {rotation: [360, 0, 0]}}}])),
};

// ---- the pose the renderer gives a part (bone pivot, translation then rotation), at channel values t (model px) ----
function poseMatrix(bone, t) {
  const pv = boneOf.get(bone)[2]; let rot = [0, 0, 0], tr = [0, 0, 0];
  for (const [ch, def] of Object.entries(CHANNELS)) { const d = def.transforms[bone]; if (!d) continue;
    if (d.rotation) rot = d.rotation.map(v => v * (t[ch] || 0)); if (d.translation) tr = d.translation.map(v => v * (t[ch] || 0)); }
  return M4.mul(M4.mul(M4.T(add(pv, tr)), M4.R(rot)), M4.T(mul(pv, -1)));
}
const posed = (p, t) => { const M = poseMatrix(p.bone, t); return p.v.map(q => M4.pt(M, q)); };
// numeric checks: the needle clockwise as seen from the front, the fuel needle E -> F through the front, the drums' digits
// at the front, the cap lifting backward, the breakers popping out toward the viewer, the handle out along the pull line
{
  const tip = (part, bone, t) => { const q = posed(byName.get(part), t), c = boneOf.get(bone)[2]; let far = q[0];
    for (const v of q) if (Math.hypot(v[0] - c[0], v[1] - c[1], v[2] - c[2]) > Math.hypot(far[0] - c[0], far[1] - c[1], far[2] - c[2])) far = v; return sub(far, c); };
  const a = tip('needle_volts_mesh', 'needle_volts', {}), b = tip('needle_volts_mesh', 'needle_volts', {volts: 0.5}), c = tip('needle_volts_mesh', 'needle_volts', {volts: 1});
  // as seen from the front (model -z): right = -x
  assert(-a[0] < 0 && a[1] < 0 && Math.abs(b[0]) < 0.05 && b[1] > 0 && -c[0] > 0 && c[1] < 0, 'voltmeter needle sweep');
  const e = tip('fuel_needle_mesh', 'fuel_needle', {}), h = tip('fuel_needle_mesh', 'fuel_needle', {fuel: 0.5}), fu = tip('fuel_needle_mesh', 'fuel_needle', {fuel: 1});
  assert(e[0] > 0 && h[2] < 0 && fu[0] < 0, 'fuel needle: E toward the engine end (model +x), half toward the front (model -z), F toward the handle end');
  for (let d = 0; d < 10; d++) { const p = byName.get('drum_0_mesh'), M = poseMatrix(p.bone, {h0: d / 10}); let bz = Infinity;
    for (const f of p.f) { const n = norm(newell(f.ids.map(i => M4.pt(M, p.v[i])))); bz = Math.min(bz, n[2]); }
    assert(bz < -0.99, 'drum: a face square to the front at every digit'); }
  const capUp = posed(byName.get('cap_body'), {cap: 1});
  assert(Math.max(...capUp.map(q => q[1])) > m(C.y1 + 0.02) && Math.max(...capUp.map(q => q[2])) > m(-C.z + C.r + 0.005), 'the cap must lift toward the back');
  const out = posed(byName.get('breaker_0_cap'), {trip: 1}), rest = byName.get('breaker_0_cap').v;
  assert(Math.min(...out.map(q => q[2])) < Math.min(...rest.map(q => q[2])) - 0.07, 'the breakers pop out toward the front (model -z)');
  const pulled = posed(byName.get('handle_grip'), {pull: 1});
  assert(Math.max(...pulled.map(q => q[0])) > m(0.70), 'the handle comes out past the engine end (model +x)');
}

// =============================== buried faces ===============================
{
  const RAY = norm([1, 0.0137, 0.0071]);
  const SOL = PARTS.map(p => { const tris = []; for (const f of p.f) for (let j = 1; j + 1 < f.ids.length; j++) tris.push([p.v[f.ids[0]], p.v[f.ids[j]], p.v[f.ids[j + 1]]]);
    return {p, tris, lo: [0, 1, 2].map(k => Math.min(...p.v.map(q => q[k]))), hi: [0, 1, 2].map(k => Math.max(...p.v.map(q => q[k])))}; });
  const inside = (S, q) => {
    if (q.some((v, k) => v < S.lo[k] || v > S.hi[k])) return false;
    let n = 0;
    for (const [a, b, c] of S.tris) {
      const e1 = sub(b, a), e2 = sub(c, a), h = cross(RAY, e2), det = dot(e1, h); if (Math.abs(det) < 1e-12) continue;
      const sv = sub(q, a), u = dot(sv, h) / det; if (u < 0 || u > 1) continue;
      const qv = cross(sv, e1), v = dot(RAY, qv) / det; if (v < 0 || u + v > 1) continue;
      if (dot(e2, qv) / det > 1e-9) n++;
    }
    return n % 2 === 1;
  };
  for (const p of PARTS) { if (p.custom || p.mat === 'glass') continue;
    for (const f of p.f) {
      const P3 = f.ids.map(i => p.v[i]), n = norm(newell(P3)), c = P3.reduce((a, q) => add(a, mul(q, 1 / P3.length)), [0, 0, 0]);
      if (n[1] < -0.99 && P3.every(q => q[1] < 0.01)) { f.buried = true; continue; }
      const pull = q => { const d = sub(c, q), l = Math.hypot(...d); return l < 1e-9 ? q : add(q, mul(d, Math.min(0.5, m(0.001) / l))); };
      const edge = P3.flatMap((q, k) => [0.25, 0.5, 0.75].map(t => add(q, mul(sub(P3[(k + 1) % P3.length], q), t))));
      const samples = [c, ...P3, ...edge].map(pull).map(q => add(q, mul(n, 0.006)));
      // moving parts are never buried in the body (the handle leaves the guide, the breaker band shows when it pops out)
      f.buried = p.bone === 'body' && SOL.some(S => S.p !== p && S.p.bone === 'body' && samples.every(q => inside(S, q)));
    }
  }
}
export const buried = PARTS.reduce((a, p) => a + p.f.filter(f => f.buried).length, 0);
boundaryCheck();
// texel density: the dark inner works (crankcase underside, shroud backs) get less
const DENSITY = {rubber: 0.8, black: 0.9, filter: 0.8};
const islandScale = (part, mat) => DENSITY[mat] ?? 1;

// =============================== UVs: unwrap + the reserved panel region ===============================
const ATLAS = 2048, PAD = 2;
const ART = readPng(fs.readFileSync(PANEL_ART)), AW = ART.w / ART_SCALE, AH = ART.h / ART_SCALE;
const REGION = {x: ATLAS - AW - PAD, y: ATLAS - AH - PAD, w: AW, h: AH};
const REGULAR = PARTS.filter(p => !p.custom);
const VIEWS = REGULAR.map(p => { const q = new Part(p.name, p.bone, p.mat); q.v = p.v; q.f = p.f.filter(f => !f.buried); q.of = p; return q; }).filter(q => q.f.length);
const UA = unwrap(VIEWS, {atlas: ATLAS, pad: PAD, startS: 15, stepS: 0.25, reserve: [REGION.x - PAD, REGION.y - PAD, ATLAS, ATLAS], islandScale});
const faceUV = new Map([...UA.faceUV].map(([q, map]) => [q.of, map]));
if (process.env.PACK) {
  const used = UA.islands.reduce((a, is) => a + is.W * is.H, 0), cap = ATLAS * ATLAS - (REGION.w + PAD) * (REGION.h + PAD);
  console.log('S', UA.S, 'islands', UA.islands.length, 'used', used, 'cap', cap, 'fill', (used / cap).toFixed(3));
  process.exit(0);
}
{
  const cellOf = (row, col) => [REGION.x + col * STRIP.cell / ART_SCALE, REGION.y + (STRIP.y + row * STRIP.cell) / ART_SCALE, STRIP.cell / ART_SCALE];
  const plain = (row, col, P3) => {
    const [cx, cy, s] = cellOf(row, col), n = norm(newell(P3)), t = norm(Math.abs(n[1]) < 0.9 ? cross(n, [0, 1, 0]) : cross(n, [1, 0, 0])), b = cross(n, t);
    const uv = P3.map(q => [dot(q, t), dot(q, b)]), u0 = Math.min(...uv.map(a => a[0])), v0 = Math.min(...uv.map(a => a[1])), ext = Math.max(1e-6, ...uv.map(a => Math.max(a[0] - u0, a[1] - v0)));
    return uv.map(([u, v]) => [cx + s * 0.3 + (u - u0) / ext * s * 0.4, cy + s * 0.3 + (v - v0) / ext * s * 0.4]); };
  for (const p of REGULAR) for (const f of p.f) if (f.buried) { const map = faceUV.get(p) || faceUV.set(p, new Map()).get(p); map.set(f, plain(0, 10, f.ids.map(i => p.v[i]))); }
  // the panel's front plane -> the art (model x runs right-to-left as seen from the front)
  const [px0, py1] = pan(0, 0), mx0 = toModel([m(px0), 0, 0])[0], my1 = m(py1);
  const artUV = P3 => P3.map(q => [REGION.x + (mx0 - q[0]) / K / PU, REGION.y + (my1 - q[1]) / K / PU]);
  const plate = byName.get('panel_plate');
  faceUV.set(plate, new Map(plate.f.map(f => { const P3 = f.ids.map(i => plate.v[i]), n = norm(newell(P3)); return [f, n[2] < -0.99 ? artUV(P3) : plain(1, 10, P3)]; })));
  for (const p of PARTS.filter(q => q.custom === 'art'))
    faceUV.set(p, new Map(p.f.map(f => { const P3 = f.ids.map(i => p.v[i]), n = norm(newell(P3)); return [f, n[2] < -0.99 ? artUV(P3) : plain(0, 11, P3)]; })));
  for (let i = 0; i < PANEL.hours.drums.n; i++) {
    const p = byName.get(`drum_${i}_mesh`), row = i === PANEL.hours.drums.n - 1 ? 1 : 0, map = new Map();
    for (const f of p.f) {
      const P3 = f.ids.map(q => p.v[q]), n = norm(newell(P3));
      if (Math.abs(n[0]) > 0.9) { map.set(f, plain(0, 10, P3)); continue; }
      let digit = -1;
      for (let d = 0; d < 10; d++) { const M = poseMatrix(p.bone, {[`h${i}`]: d / 10}), nn = norm(newell(P3.map(q => M4.pt(M, q)))); if (nn[2] < -0.99) digit = d; }
      assert(digit >= 0, 'drum face without a digit');
      const M = poseMatrix(p.bone, {[`h${i}`]: digit / 10}), Q = P3.map(q => M4.pt(M, q));
      const xs = Q.map(q => q[0]), ys = Q.map(q => q[1]), X0 = Math.max(...xs), Y1 = Math.max(...ys), W = X0 - Math.min(...xs), H = Y1 - Math.min(...ys);
      const [cx, cy, s] = cellOf(row, digit), inset = 1.5;
      map.set(f, Q.map(q => [cx + inset + (X0 - q[0]) / W * (s - 2 * inset), cy + inset + (Y1 - q[1]) / H * (s - 2 * inset)]));
    }
    faceUV.set(p, map);
  }
}
for (const p of PARTS) assert(p.f.every(f => faceUV.get(p)?.has(f)), 'unmapped face in ' + p.name);

// =============================== paint, the panel art, glass ===============================
const DUMMY = 'data:image/png;base64,' + png(Buffer.from([0, 0, 0, 255]), 1, 1).toString('base64');
const painted = paint({PARTS: VIEWS, islands: UA.islands, S: UA.S, uvOf: UA.uvOf, atlas: ATLAS, pad: PAD, MATS, ZONED: new Set(), groupInfo: new Map(),
  sourceGroups: () => [], refTexture: {source: DUMMY}, refUvWidth: 1, background: {c: [60, 62, 64, 255], s: [100, 20, 0, 255], n: [128, 128, 255, 255]}});
const MAPS = painted.PNG.map(b => { const r = readPng(b); assert(r.bpp === 4 && r.w === ATLAS, 'atlas format'); return r.px; });
{
  for (let y = 0; y < AH; y++) for (let x = 0; x < AW; x++) {
    const acc = [0, 0, 0, 0];
    for (let dy = 0; dy < ART_SCALE; dy++) for (let dx = 0; dx < ART_SCALE; dx++) { const k = ((y * ART_SCALE + dy) * ART.w + x * ART_SCALE + dx) * ART.bpp; for (let c = 0; c < 4; c++) acc[c] += c < ART.bpp ? ART.px[k + c] : 255; }
    const a = acc[3] / ART_SCALE ** 2, rgb = acc.slice(0, 3).map(v => Math.round(v / ART_SCALE ** 2 * (a / 255)));
    const k = ((REGION.y + y) * ATLAS + REGION.x + x) * 4, digits = y * ART_SCALE >= STRIP.y;
    MAPS[0].set([...rgb, 255], k); MAPS[1].set([digits ? 125 : 112, 20, 0, 255], k); MAPS[2].set([128, 128, 255, 255], k);
  }
  for (const is of UA.islands) {
    if (is.part.mat !== 'glass') continue;
    for (let y = is.py - PAD; y < is.py + is.H + PAD; y++) for (let x = is.px - PAD; x < is.px + is.W + PAD; x++) MAPS[0][(y * ATLAS + x) * 4 + 3] = GLASS_ALPHA;
  }
}
const PNGS = MAPS.map(px => png(px, ATLAS, ATLAS));

// =============================== rig: source, geo, sidecar, profile ===============================
const uuidOf = s => { const h = createHash('sha256').update('afl-portable-diesel-generator-v1:' + s).digest('hex'); return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-8${h.slice(17, 20)}-${h.slice(20, 32)}`; };
const ELEMENTS = (() => {
  const groups = new Map();
  for (const p of PARTS) { const k = p.bone + (p.mat === 'glass' ? '_glass' : ''); (groups.get(k) || groups.set(k, []).get(k)).push(p); }
  return [...groups].map(([name, ps]) => {
    const e = {name, bone: ps[0].bone, glass: ps[0].mat === 'glass', v: [], f: []}, uv = new Map();
    for (const p of ps) { const o = e.v.length, map = faceUV.get(p); e.v.push(...p.v); for (const f of p.f) { const g = {...f, ids: f.ids.map(i => i + o)}; e.f.push(g); uv.set(g, map.get(f)); } }
    faceUV.set(e, uv); return e;
  });
})();
if (process.env.TRI) { const t = PARTS.map(p => [p.name, p.f.reduce((a, f) => a + f.ids.length - 2, 0)]).sort((a, b) => b[1] - a[1]); console.log('TOTAL', t.reduce((a, x) => a + x[1], 0)); console.log(t.slice(0, 40).map(x => x.join(' ')).join('\n')); process.exit(0); }
assert(ELEMENTS.length <= 128, 'too many mesh elements: ' + ELEMENTS.length);
const source = (() => {
  const elements = [], nodes = new Map(BONES.map(([b]) => [b, {uuid: uuidOf('group:' + b), isOpen: true, children: []}]));
  for (const p of ELEMENTS) {
    const key = i => i.toString(36).padStart(4, '0'), vertices = {}, faces = {}, uvs = faceUV.get(p);
    p.v.forEach((q, i) => { vertices[key(i)] = q.map(r12); });
    p.f.forEach((f, fi) => { const uv = uvs.get(f); faces['f' + key(fi)] = {uv: Object.fromEntries(f.ids.map((vid, j) => [key(vid), uv[j].map(r12)])), vertices: f.ids.map(key), texture: 0}; });
    const eid = uuidOf('mesh:' + p.name);
    elements.push({name: p.name, color: 0, origin: [0, 0, 0], rotation: [0, 0, 0], shading: 'flat', export: true, visibility: true, locked: false,
      render_order: 'default', scope: 0, allow_mirror_modeling: true, vertices, faces, type: 'mesh', uuid: eid});
    nodes.get(p.bone).children.push(eid);
  }
  return {meta: {format_version: '5.0', model_format: 'free', box_uv: false}, name: ID, model_identifier: '', visible_box: [1, 1, 0],
    variable_placeholders: '', variable_placeholder_buttons: [], timeline_setups: [], unhandled_root_fields: {}, resolution: {width: ATLAS, height: ATLAS},
    elements, groups: BONES.map(([b, , o]) => ({name: b, uuid: uuidOf('group:' + b), export: true, locked: false, scope: 0, selected: false, visibility: true,
      _static: {properties: {}, temp_data: {}}, origin: o.map(r12), rotation: [0, 0, 0], color: 0, children: [], reset: false, shade: true,
      mirror_uv: false, autouv: 0, isOpen: true, primary_selected: false})),
    outliner: BONES.map(([b]) => nodes.get(b)),
    textures: [{name: ID + '.png', relative_path: `textures/${ID}_v1.png`, folder: '', namespace: '', id: '0', group: '', scope: 0,
      width: ATLAS, height: ATLAS, uv_width: ATLAS, uv_height: ATLAS, particle: false, use_as_default: false, layers_enabled: false, sync_to_project: '',
      file_format: 'png', render_mode: 'default', render_sides: 'auto', wrap_mode: 'limited', pbr_channel: 'color', fps: 7, frame_time: 1,
      frame_order_type: 'loop', frame_order: '', frame_interpolate: false, visible: true, internal: true, saved: true, uuid: uuidOf('texture'),
      source: 'data:image/png;base64,' + PNGS[0].toString('base64')}],
    animations: []};
})();
const geo = {format_version: '1.12.0', 'minecraft:geometry': [{description: {identifier: 'geometry.' + ID, texture_width: ATLAS, texture_height: ATLAS,
  visible_bounds_width: 3, visible_bounds_height: 2, visible_bounds_offset: [0, 0.5, 0]},
  bones: BONES.map(([b, , o]) => ({name: b, pivot: [-o[0] || 0, o[1], o[2]].map(r12)}))}]};
const LAYERS = Object.fromEntries(ELEMENTS.filter(e => e.glass).map(e => [e.name, 'translucent']));
const sidecar = convert(source, geo, {}, ID + '.bbmodel', 2, null, LAYERS);
const aabbOf = pts => [0, 1, 2].map(k => Math.min(...pts.map(q => q[k]))).concat([0, 1, 2].map(k => Math.max(...pts.map(q => q[k]))));
const sweepPts = PARTS.flatMap(p => [0, 0.5, 1].flatMap(t => posed(p, {pull: t, cap: t, trip: t})));
const SW = aabbOf(sweepPts), MG = 0.25;
const bounds = [(SW[0] - MG + 8) / 16, (SW[1] - MG) / 16, (SW[2] - MG + 8) / 16, (SW[3] + MG + 8) / 16, (SW[4] + MG) / 16, (SW[5] + MG + 8) / 16].map(r6);
const tr6 = d => ({...(d.rotation ? {rotation: d.rotation.map(r6)} : {}), ...(d.translation ? {translation: d.translation.map(v => r6(v / 16))} : {})});
const profile = {format_version: 1, geometry: `apocalypse_firstlight:geo/${ID}.geo.json`, texture: `apocalypse_firstlight:textures/block/${ID}.png`,
  origin: [0, 0, 0], scale: [1, 1, 1], facing: 'horizontal', bounds,
  parts: Object.fromEntries(BONES.map(([b, parent, o]) => [b, {...(parent ? {parent} : {}), pivot: o.map(v => r6(v / 16))}])),
  animations: Object.fromEntries(Object.entries(CHANNELS).map(([ch, d]) => [ch, {duration_ticks: d.ticks, easing: d.easing, ...(d.follow ? {follow_ticks: d.follow} : {}), ...(d.wrap ? {wrap: true} : {}),
    transforms: Object.fromEntries(Object.entries(d.transforms).map(([b, t]) => [b, tr6(t)]))}]))};

// =============================== the item (OBJ, at rest, no glass) ===============================
const f6 = v => (+v.toFixed(6)).toString(), SMOOTH = Math.cos(36 * D2R);
const ITEM_PARTS = PARTS.filter(p => p.mat !== 'glass');
function cornerNormals(p, V) {
  const fn = p.f.map(f => newell(f.ids.map(i => V[i]))), byV = new Map();
  p.f.forEach((f, k) => f.ids.forEach(i => (byV.get(i) || byV.set(i, []).get(i)).push(k)));
  return p.f.map((f, k) => { const n0 = norm(fn[k]); return f.ids.map(i => norm(byV.get(i).reduce((a, j) => dot(norm(fn[j]), n0) >= SMOOTH ? add(a, fn[j]) : a, [0, 0, 0]))); });
}
function objOf(title, parts, t = {}) {
  const out = [`# AFL ${title}, generated by tools/build-portable-diesel-generator-v1.mjs`, `mtllib item.mtl`];
  let vBase = 1, tBase = 1, nBase = 1;
  for (const p of parts) {
    const V = posed(p, t);
    out.push(`o ${p.name}`, `usemtl ${ID}`);
    for (const q of V) out.push(`v ${f6(q[0] / 16 + 0.5)} ${f6(q[1] / 16)} ${f6(q[2] / 16 + 0.5)}`);
    const uvs = faceUV.get(p), vt = [], vn = [], fl = [], cn = cornerNormals(p, V), nIndex = new Map();
    p.f.forEach((f, k) => {
      const uv = uvs.get(f);
      fl.push('f ' + f.ids.map((id, j) => {
        const key = cn[k][j].map(f6).join(' ');
        if (!nIndex.has(key)) { nIndex.set(key, nBase + vn.length); vn.push('vn ' + key); }
        vt.push(`vt ${f6(uv[j][0] / ATLAS)} ${f6(1 - uv[j][1] / ATLAS)}`); return `${vBase + id}/${tBase + vt.length - 1}/${nIndex.get(key)}`;
      }).join(' '));
    });
    out.push(...vt, ...vn, ...fl);
    vBase += p.v.length; tBase += vt.length; nBase += vn.length;
  }
  return out.join('\n') + '\n';
}
const MTL = `# AFL Portable diesel generator V1\nnewmtl ${ID}\nKd 1 1 1\nmap_Kd apocalypse_firstlight:block/${ID}\n`;
function guiFit(parts, rotation, size = 15.2) {
  const [ax, ay] = rotation.map(v => v * D2R);
  const rot = q => { const x = q[0] * Math.cos(ay) + q[2] * Math.sin(ay), z = -q[0] * Math.sin(ay) + q[2] * Math.cos(ay); return [x, q[1] * Math.cos(ax) - z * Math.sin(ax)]; };
  const pts = parts.flatMap(p => p.v.map(q => rot([q[0], q[1] - 8, q[2]])));
  const xs = pts.map(q => q[0]), ys = pts.map(q => q[1]), w = Math.max(...xs) - Math.min(...xs), h = Math.max(...ys) - Math.min(...ys);
  const scale = r3(Math.min(4, size / Math.max(w, h))), cx = (Math.max(...xs) + Math.min(...xs)) / 2, cy = (Math.max(...ys) + Math.min(...ys)) / 2;
  return {rotation, translation: [r3(-scale * cx), r3(-scale * cy), 0], scale: [scale, scale, scale]};
}
const cellPoints = parts => parts.flatMap(p => p.v.map(([x, y, z]) => [x + 8, y, z + 8]));

// =============================== outputs ===============================
const bb = path.join(ROOT, 'src/main/blockbench'), assets = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight');
const ITEM_OBJ = objOf('Portable diesel generator V1 (item)', ITEM_PARTS);
const outputs = [
  [path.join(assets, `geo/${ID}.geo.json`), json(geo)], [path.join(assets, `meshes/${ID}.aflmesh.json`), serializeCompact(sidecar)],
  [path.join(assets, `block_mesh_profiles/${ID}.json`), json(profile)],
  [path.join(bb, `${ID}_v1.bbmodel`), JSON.stringify(source)],
  [path.join(assets, `models/block/${ID}/item.obj`), ITEM_OBJ], [path.join(assets, `models/block/${ID}/item.mtl`), MTL],
  [path.join(assets, `models/block/${ID}/item.json`), json({loader: 'forge:obj', model: `apocalypse_firstlight:models/block/${ID}/item.obj`, automatic_culling: false,
    flip_v: true, shade_quads: true, emissive_ambient: true, ambientocclusion: false, textures: {particle: NS + ID}})],
  [path.join(assets, `models/block/${ID}/generator.json`), json({loader: 'apocalypse_firstlight:static_mesh', textures: {particle: NS + ID}})],
  [path.join(assets, `blockstates/${ID}.json`), json({variants: {'': {model: NS + ID + '/generator'}}})],
  [path.join(assets, `models/item/${ID}.json`), json({parent: NS + ID + '/item', gui_light: 'side',
    display: {...heldDisplay(cellPoints(ITEM_PARTS), {size: 1, rotations: {fixed: [0, 180, 0]}}), gui: guiFit(ITEM_PARTS, [25, 215, 0])}})],
];
['', '_s', '_n'].forEach((k, i) => outputs.push([path.join(bb, `textures/${ID}_v1${k}.png`), PNGS[i]], [path.join(assets, `textures/block/${ID}${k}.png`), PNGS[i]]));

// facts the Java side mirrors (structure frame px: x from the west edge facing north, y up, z 0 = the front face)
const toStructure = q => [r3(q[0] + 8), r3(q[1]), r3(q[2] + 8)];
const pt = q => toStructure(toModel(q.map(m)));
const boxOf = (a, b) => { const p = pt(a), q = pt(b); return [0, 1, 2].map(k => Math.min(p[k], q[k])).concat([0, 1, 2].map(k => Math.max(p[k], q[k]))); };
const restPts = PARTS.flatMap(p => p.v), REST = aabbOf(restPts);
const sockets = PANEL.duplex.flatMap(D => D.sockets.map(s => { const [x, y] = pan(...s); return pt([x, y, PZ + 0.006]); }));
const [bx0, by1] = pan(PANEL.breakers[0].c[0] - 14, PANEL.breakers[0].c[1] - 14), [bx1, by0] = pan(PANEL.breakers[2].c[0] + 14, PANEL.breakers[2].c[1] + 14);
const [dx0, dy1] = pan(PANEL.duplex[0].rect[0] - 4, PANEL.duplex[0].rect[1] - 4), [dx1, dy0] = pan(PANEL.duplex[1].rect[2] + 4, PANEL.duplex[1].rect[3] + 4);
export const FACTS = {
  shape: [r3(REST[0] + 8), 0, r3(REST[2] + 8), r3(REST[3] + 8), r3(REST[4]), r3(REST[5] + 8)],
  handle: boxOf(sub(HANDLE_REST, [0.03, 0.03, 0.065]), add(HANDLE_REST, [0.03, 0.035, 0.065])),
  handleRest: pt(HANDLE_REST), pullPx: toStructure(add(PULL_PX, [-8, 0, -8])).map((v, k) => r3(v - [0, 0, 0][k])), guide: pt(G.guide),
  stop: boxOf([G.stop.x - 0.03, G.stop.y - 0.03, G.stop.z - 0.03], [G.stop.x + 0.03, G.stop.y + 0.03, G.stop.z + 0.02]),
  cap: boxOf([C.x - C.r - 0.015, G.tank.y1 - 0.01, C.z - C.r - 0.02], [C.x + C.r + 0.015, C.y1 + 0.03, C.z + C.r + 0.015]),
  pourOpening: pt([C.x, C.y0 - 0.005, C.z]),
  breakers: boxOf([bx0, by0, PZ - 0.002], [bx1, by1, PZ + 0.02]),
  outlets: boxOf([dx0, dy0, PZ - 0.002], [dx1, dy1, PZ + 0.02]), sockets,
  panelMiddle: pt([0.15, 0.335, PZ]), engine: pt([-0.16, 0.30, 0]), exhaust: pt(EXHAUST),
};
const tris = parts => parts.reduce((s, p) => s + p.f.reduce((t, f) => t + f.ids.length - 2, 0), 0);
export const stats = {parts: PARTS.length, elements: ELEMENTS.length, triangles: tris(PARTS), buriedFaces: buried, texelsPerPx: UA.S, density: DENSITY, region: REGION, coplanar: coplanar.slice(0, 12), coplanarCount: coplanar.length, bounds, facts: FACTS};

const isMain = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  console.log(JSON.stringify(stats, null, 1));
  const pi = process.argv.indexOf('--preview');
  if (pi > 0) {
    const dir = process.argv[pi + 1]; fs.mkdirSync(dir, {recursive: true});
    fs.writeFileSync(path.join(dir, 'pg_rest.obj'), objOf('rest', PARTS, {volts: 0.8, fuel: 0.6, h0: 0, h1: 0.4, h2: 0.2, h3: 0.8, h4: 0.6}));
    fs.writeFileSync(path.join(dir, 'pg_pulled.obj'), objOf('pulled', PARTS, {pull: 1, cap: 1, trip: 1, fuel: 0.6}));
    ['', '_s', '_n'].forEach((k, i) => fs.writeFileSync(path.join(dir, `${ID}${k}.png`), PNGS[i]));
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
