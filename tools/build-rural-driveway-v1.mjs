// Rural Driveway V1 (docs/models/rural_driveway_v1.md): the forest start home B's driveway pieces (M3a, F6; user 2026-10-14
// "可以，先出M3a概念图", then "先做你认为合适的东西进游戏" on the concept in design/buildings/forest_start_home_v1/m3_sheets/).
// Pure Mesh + LabPBR, AFL Animated Block Mesh Runtime, resting parts drawn by the chunk (static_mesh):
//   tube_farm_gate  a galvanised 5-bar tube farm gate, 4 x 2 cells: two 160 mm round treated wood posts (1.65 m), the leaf
//                   3.6 m (11 ft 10 in, the leaf of a 12 ft opening) x 1.27 m on two screw-hook hinges, a chain on the latch
//                   post. It swings 90 degrees either way: bones 'swing' (a pure pivot, channel 'open_front', +90: the leaf
//                   toward the front) and its child 'leaf' (channel 'open_back', -90); the block opens it away from the
//                   player. The PRIVATE DRIVE sign (450 x 300 mm aluminium, art src/main/blockbench/textures/
//                   private_drive_sign_art.png) is a pair of child bones of the leaf, 'sign_front' / 'sign_back', shown
//                   by the block's SIGN state.
//   rural_mailbox   a USPS #1 size rural mailbox (483 x 165 x 229 mm, black painted steel, red flag) on a 4 x 4 post and
//                   a 2 x 6 cradle, box bottom 1.07 m, 1 x 2 cells. Bones 'body' (still), 'door' (channel 'open', -95: the
//                   door drops open), 'flag' (channel 'flag', +90: the flag stands up). The body is a real shell: open,
//                   the door shows the hollow inside.
//   node tools/build-rural-driveway-v1.mjs                -> writes models, blockstates, rigs, atlases, sources
//   node tools/build-rural-driveway-v1.mjs --check        -> verifies every output is up to date
//   node tools/build-rural-driveway-v1.mjs --preview DIR  -> writes only OBJ + maps into DIR
// Frames: millimetres, origin at the master cell's bottom centre (the gate: c0r0, the first column; the mailbox: the bottom
// cell), x east, y up, z south; drawn facing north (the front at -z: the gate's sign side, the mailbox's door). The gate's
// columns run to +x (FACING's clockwise side), its hinge on the first column.
import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {Part, extrude, unwrap, paint, png, readPng, zFightLevels, area2, add, dot, cross, norm, newell, sub, mul} from './cube-slab-mesh-lib.mjs';
import {convert, serializeCompact} from './export-afl-mesh.mjs';
import {heldDisplay} from './item-held-display.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
function assert(c, m) { if (!c) throw new Error(m); }
const PX = 0.016, D2R = Math.PI / 180;
const r3 = v => +v.toFixed(3) || 0, r6 = v => +v.toFixed(6) || 0, r12 = v => +v.toFixed(12) || 0;
const NS = 'apocalypse_firstlight:block/';
const json = v => JSON.stringify(v, null, 2) + '\n';

// ---------------- geometry helpers (as tools/build-trash-enclosure-v1.mjs) ----------------
const orient = (L, ccw) => (area2(L) > 0) === ccw ? L : L.slice().reverse();
const shapeOf = (outer, holes = []) => ({outer: orient(outer, true), holes: holes.map(h => orient(h, false))});
const planY = (part, outer, y0, y1, c) => extrude(part, 'y', shapeOf(outer.map(([x, z]) => [z, x])), y0, y1, c);
const box = (part, a, b, c = 0) => planY(part, [[a[0], a[2]], [b[0], a[2]], [b[0], b[2]], [a[0], b[2]]], a[1], b[1], c);
function latheY(part, cx, cz, profile, seg) {
  const ang = i => Math.PI / seg + 2 * Math.PI * i / seg;
  const rings = profile.map(([r, y]) => Array.from({length: seg}, (_, i) => part.vtx([cx + r * Math.cos(ang(i)), y, cz + r * Math.sin(ang(i))])));
  for (let k = 0; k + 1 < profile.length; k++) {
    const [ra, ya] = profile[k], [rb, yb] = profile[k + 1], dr = rb - ra, dy = yb - ya;
    for (let i = 0; i < seg; i++) {
      const j = (i + 1) % seg, mm = ang(i) + Math.PI / seg;
      part.face([rings[k][i], rings[k][j], rings[k + 1][j], rings[k + 1][i]], [dy * Math.cos(mm), -dr, dy * Math.sin(mm)], Math.abs(dr) > 0.3 * Math.abs(dy) ? 'bevel' : 'side');
    }
  }
  for (const [k, s] of [[0, -1], [profile.length - 1, 1]]) { const c = part.vtx([cx, profile[k][1], cz]);
    for (let i = 0; i < seg; i++) part.face(s < 0 ? [c, rings[k][i], rings[k][(i + 1) % seg]] : [c, rings[k][(i + 1) % seg], rings[k][i]], [0, s, 0], 'cap'); }
}
const rod = (part, cx, cz, r, y0, y1, seg = 12) => latheY(part, cx, cz, [[r, y0], [r, y1]], seg);
/** A closed round tube from a to b (any direction). */
function tube(part, a, b, r, seg = 12) {
  const d = norm(sub(b, a)), ref = Math.abs(d[1]) < 0.9 ? [0, 1, 0] : [1, 0, 0], u = norm(cross(d, ref)), w = cross(d, u);
  const ang = i => Math.PI / seg + 2 * Math.PI * i / seg;
  const ring = c => Array.from({length: seg}, (_, i) => part.vtx(add(c, add(mul(u, r * Math.cos(ang(i))), mul(w, r * Math.sin(ang(i)))))));
  const A = ring(a), B = ring(b);
  for (let i = 0; i < seg; i++) { const j = (i + 1) % seg, m = ang(i) + Math.PI / seg;
    part.face([A[i], A[j], B[j], B[i]], add(mul(u, Math.cos(m)), mul(w, Math.sin(m))), 'side'); }
  const ca = part.vtx(a), cb = part.vtx(b);
  for (let i = 0; i < seg; i++) { const j = (i + 1) % seg; part.face([ca, A[j], A[i]], mul(d, -1), 'cap'); part.face([cb, B[i], B[j]], d, 'cap'); }
}
/** A flat box with one face (the sign's) on its own material: z0..z1, the art face at z0 (front: -1) or z1 (back: +1). */
function plate(part, [x0, y0, z0], [x1, y1, z1], artSide, artMat) {
  const V = [[x0, y0, z0], [x1, y0, z0], [x1, y1, z0], [x0, y1, z0], [x0, y0, z1], [x1, y0, z1], [x1, y1, z1], [x0, y1, z1]].map(q => part.vtx(q));
  part.face([V[0], V[1], V[2], V[3]], [0, 0, -1], 'side', artSide < 0 ? artMat : part.mat);
  part.face([V[4], V[5], V[6], V[7]], [0, 0, 1], 'side', artSide > 0 ? artMat : part.mat);
  part.face([V[0], V[1], V[5], V[4]], [0, -1, 0], 'cap'); part.face([V[3], V[2], V[6], V[7]], [0, 1, 0], 'cap');
  part.face([V[0], V[3], V[7], V[4]], [-1, 0, 0], 'cap'); part.face([V[1], V[2], V[6], V[5]], [1, 0, 0], 'cap');
}
/** One chain link: a 30 x 16 mm oval of 6 mm wire around c, long along y, in the x-y plane (flat) or the z-y plane. */
function link(part, c, flat) {
  const a = flat ? [1, 0, 0] : [0, 0, 1], P = (s, t) => add(c, add(mul(a, s), [0, t, 0]));
  tube(part, P(-5, -12), P(5, -12), 3, 6); tube(part, P(-5, 12), P(5, 12), 3, 6);
  tube(part, P(-5, -12), P(-5, 12), 3, 6); tube(part, P(5, -12), P(5, 12), 3, 6);
}
const MAT = (c, hl, sm, se, f0 = 20) => ({c, hl, sm, se, f0});
const hash3 = (a, b, c) => { let h = Math.imul(a | 0, 374761393) ^ Math.imul(b | 0, 668265263) ^ Math.imul(c | 0, 2147483647); h = Math.imul(h ^ (h >>> 13), 1274126177); return ((h ^ (h >>> 16)) >>> 0) / 4294967295; };
const sm3 = t => t * t * (3 - 2 * t);
function vn3(x, y, z, seed) {
  const xi = Math.floor(x), yi = Math.floor(y), zi = Math.floor(z), tx = sm3(x - xi), ty = sm3(y - yi), tz = sm3(z - zi);
  const h = (i, j, k) => hash3(xi + i, yi + j + 977 * seed, zi + k), l = (a, b, t) => a + (b - a) * t;
  return l(l(l(h(0, 0, 0), h(1, 0, 0), tx), l(h(0, 1, 0), h(1, 1, 0), tx), ty), l(l(h(0, 0, 1), h(1, 0, 1), tx), l(h(0, 1, 1), h(1, 1, 1), tx), ty), tz) * 2 - 1;
}
const mottled = (base, k = 0.03, seed = 1) => pos => { const [x, y, z] = pos.map(v => v / PX); return {c: base.map(v => v * (1 + k * vn3(x / 90, y / 90, z / 90, seed) + k * 0.4 * vn3(x / 25, y / 25, z / 25, seed + 7)))}; };
/** Weathered treated wood: the grain runs along y (posts) with a slow drift, no per-pixel noise. */
const woodGrain = (base, seed) => pos => { const [x, y, z] = pos.map(v => v / PX);
  const g = vn3(x / 14, y / 260, z / 14, seed), d = vn3(x / 120, y / 400, z / 120, seed + 3);
  return {c: base.map(v => v * (1 + 0.05 * g + 0.04 * d))}; };

// ---------------- the sign's art ----------------
const ART = readPng(fs.readFileSync(path.join(ROOT, 'src/main/blockbench/textures/private_drive_sign_art.png')));
function artAt(s, t) {   // bilinear, s right, t down, 0..1
  const X = Math.max(0, Math.min(ART.w - 1.001, s * ART.w - 0.5)), Y = Math.max(0, Math.min(ART.h - 1.001, t * ART.h - 0.5));
  const x0 = Math.floor(X), y0 = Math.floor(Y), fx = X - x0, fy = Y - y0, px = (x, y) => ART.px[(y * ART.w + x) * ART.bpp];
  const top = px(x0, y0) * (1 - fx) + px(x0 + 1, y0) * fx, bot = px(x0, y0 + 1) * (1 - fx) + px(x0 + 1, y0 + 1) * fx;
  return (top * (1 - fy) + bot * fy) / 255;
}
const INK = [22, 22, 23], SHEET = [226, 228, 224];

// =============================== tube farm gate ===============================
export const GATE = {
  post: {r: 80, top: 1650, hingeX: -420, latchX: 3420},
  pivot: [-322, 0],                                   // the hinge pins' axis (x, z): the sleeves clear the post by 2 mm
  leaf: {x0: -280, x1: 3280, y0: 150, y1: 1420, r: 21, rMid: 16.5, rails: [0, 0.2, 0.42, 0.68, 1], stayR: 10, braceR: 12},
  hinges: [400, 1200],                                // the hooks' shank height; the leaf's sleeves ride 12..62 above
  chain: {x: 3330, y: 1250, links: 6},
  sign: {w: 450, h: 300, t: 2, mount: 21.5},          // between the third and fourth rails, 21.5 mm off the leaf's middle
  swing: {ticks: 24, easing: 'ease_in_out', degrees: 90},
};
const railY = f => GATE.leaf.y0 + f * (GATE.leaf.y1 - GATE.leaf.y0);
export const SIGN_CENTRE = [(GATE.leaf.x0 + GATE.leaf.x1) / 2, (railY(GATE.leaf.rails[2]) + railY(GATE.leaf.rails[3])) / 2];
function signArt(front) {
  const [cx, cy] = SIGN_CENTRE.map(v => v * PX), W = GATE.sign.w * PX, H = GATE.sign.h * PX;
  // seen from the front (-z) the viewer's right is -x; from the back, +x
  return pos => { const s = (front ? cx - pos[0] : pos[0] - cx) / W + 0.5, t = 0.5 - (pos[1] - cy) / H, a = artAt(s, t);
    return {c: SHEET.map((v, i) => INK[i] + (v - INK[i]) * a)}; };
}
function buildGate() {
  const PARTS = [], P = (name, bone, mat) => { const p = new Part(name, bone, mat); PARTS.push(p); return p; };
  const G = GATE, L = G.leaf, [kx, kz] = G.pivot;
  // ---- frame: the posts, the screw hooks (shank + pin), the chain on the latch post
  for (const [name, x] of [['post_hinge', G.post.hingeX], ['post_latch', G.post.latchX]])
    latheY(P(name, 'frame', 'wood'), x, 0, [[G.post.r, 0], [G.post.r, G.post.top - 14], [G.post.r - 10, G.post.top]], 16);
  G.hinges.forEach((h, i) => {
    tube(P(`hook_shank_${i}`, 'frame', 'hardware'), [G.post.hingeX + 20, h, kz], [kx + 9, h, kz], 9, 10);
    rod(P(`hook_pin_${i}`, 'frame', 'hardware'), kx, kz, 8, h - 9, h + 72, 10);
    // ---- the leaf's sleeve on the pin and its strap to the hinge upright
    rod(P(`sleeve_${i}`, 'leaf', 'galv'), kx, kz, 16, h + 12, h + 62, 12);
    tube(P(`sleeve_strap_${i}`, 'leaf', 'galv'), [kx + 10, h + 37, kz], [L.x0, h + 37, kz], 8, 8);
  });
  tube(P('chain_eye', 'frame', 'hardware'), [G.post.latchX - 40, G.chain.y, 0], [G.chain.x - 2, G.chain.y, 0], 6, 8);
  { const ch = P('chain', 'frame', 'galv');
    for (let i = 0; i < G.chain.links; i++) link(ch, [G.chain.x, G.chain.y - 14 - 22 * i, 0], i % 2 === 0); }
  // ---- the leaf: uprights, five rails, two stays, the brace from the hinge foot
  const lf = P('leaf_frame', 'leaf', 'galv');
  for (const x of [L.x0, L.x1]) tube(lf, [x, L.y0 - L.r, 0], [x, L.y1 + L.r, 0], L.r, 12);
  L.rails.forEach((f, i) => { const y = railY(f), r = i === 0 || i === L.rails.length - 1 ? L.r - 0.5 : L.rMid;   // 0.5 mm under the uprights: no shared facet planes
    tube(P(`rail_${i}`, 'leaf', 'galv'), [L.x0 + 4, y, 0], [L.x1 - 4, y, 0], r, 12); });
  for (const k of [1, 2]) { const x = L.x0 + (L.x1 - L.x0) * k / 3; tube(P(`stay_${k}`, 'leaf', 'galv'), [x, L.y0, 0], [x, L.y1, 0], L.stayR, 10); }
  tube(P('brace', 'leaf', 'galv'), [L.x0 + 14, L.y0 + 14, 0], [(L.x0 + L.x1) / 2, L.y1 - 10, 0], L.braceR, 10);
  // ---- the signs: a front one and a back one (the block's SIGN shows one or none), U-clamps to the rails, four bolts
  const S = G.sign, [cx, cy] = SIGN_CENTRE;
  for (const [side, s] of [['front', -1], ['back', 1]]) {
    const bone = 'sign_' + side, z0 = s < 0 ? -S.mount - S.t : S.mount, z1 = z0 + S.t, face = s < 0 ? z0 : z1;
    plate(P(`sign_${side}`, bone, 'alu'), [cx - S.w / 2, cy - S.h / 2, z0], [cx + S.w / 2, cy + S.h / 2, z1], s, 'sign_' + side);
    for (const dx of [-170, 170]) for (const [y0, y1] of [[cy - S.h / 2 + 2, cy - S.h / 2 + 16], [cy + S.h / 2 - 16, cy + S.h / 2 - 2]]) {
      const zn = s < 0 ? -S.mount + 0.5 : S.mount - 0.5, zf = s < 0 ? -9 : 9;
      box(P(`sign_${side}_clamp_${dx}_${y0 | 0}`, bone, 'hardware'), [cx + dx - 6, y0, Math.min(zn, zf)], [cx + dx + 6, y1, Math.max(zn, zf)], 0);
    }
    for (const dx of [-190, 190]) for (const dy of [-115, 115])
      tube(P(`sign_${side}_bolt_${dx}_${dy}`, bone, 'hardware'), [cx + dx, cy + dy, face + s * 0.1], [cx + dx, cy + dy, face + s * 3.5], 7, 8);
  }
  const MATS = {
    galv:       MAT(mottled([138, 142, 140], 0.03, 2), 8, 76, 96, 230),
    wood:       MAT(woodGrain([108, 96, 82], 5), 4, 36, 44),
    hardware:   MAT(mottled([84, 72, 62], 0.04, 4), 4, 44, 56),
    alu:        MAT(mottled([186, 188, 188], 0.015, 6), 6, 104, 120, 230),
    sign_front: MAT(signArt(true), 2, 118, 118),
    sign_back:  MAT(signArt(false), 2, 118, 118),
  };
  return {PARTS, MATS, atlas: 2048, startS: 15, islandScale: (part, mat) => mat.startsWith('sign_') ? 5 : 1};
}

// =============================== rural mailbox ===============================
export const MAILBOX = {
  L: 483, W: 165, H: 229, bottom: 1070, wall: 3,
  post: {w: 89, z: 60, top: 1035}, cradle: {w: 140, t: 38, l: 420},
  door: {t: 3, gap: 0.5, grow: 1.5, degrees: -95, ticks: 8},
  flag: {x0: -92.5, x1: -88.5, y: 1140, z: 170, degrees: 90, ticks: 10},
};
/** The arched profile (x, y): flat bottom at y0, straight sides, a half round top; inset shrinks it. */
function arch(w, h, y0, inset = 0, seg = 16) {
  const r = w / 2 - inset, wallTop = y0 + h - w / 2, pts = [[-r, y0 + inset], [r, y0 + inset]];
  for (let i = 0; i <= seg; i++) { const a = Math.PI * i / seg; pts.push([r * Math.cos(a), wallTop + r * Math.sin(a)]); }
  return pts;
}
function buildMailbox() {
  const PARTS = [], P = (name, bone, mat) => { const p = new Part(name, bone, mat); PARTS.push(p); return p; };
  const M = MAILBOX, z0 = -M.L / 2, z1 = M.L / 2, F = M.flag, D = M.door;
  // ---- body: the post, the cradle, the steel shell (real wall, open front), the back
  const pw = M.post.w / 2;
  box(P('post', 'body', 'wood'), [-pw, 0, M.post.z - pw], [pw, M.post.top, M.post.z + pw], 3);
  box(P('cradle', 'body', 'wood'), [-M.cradle.w / 2, M.post.top - 5, -M.cradle.l / 2], [M.cradle.w / 2, M.bottom - 0.5, M.cradle.l / 2], 2);
  extrude(P('shell', 'body', 'paint'), 'z', shapeOf(arch(M.W, M.H, M.bottom), [arch(M.W, M.H, M.bottom, M.wall)]), z0, z1, 0);
  extrude(P('back', 'body', 'paint'), 'z', shapeOf(arch(M.W, M.H, M.bottom, M.wall + 0.5)), z1 - 6, z1 - 0.5, 0);
  // the flag's pivot bolt and its bracket on the right side (seen from the front: -x)
  box(P('flag_bracket', 'body', 'hardware'), [-M.W / 2 - 5.5, F.y - 14, F.z - 14], [-M.W / 2 + 1, F.y + 14, F.z + 14], 0);
  tube(P('flag_bolt', 'body', 'hardware'), [-M.W / 2 - 1, F.y, F.z], [F.x0 - 3, F.y, F.z], 5, 8);
  // ---- door: the outline 1.5 mm proud, 3 mm thick, in front of the open end; its latch tab at the top
  const door = P('door', 'door', 'paint'), dz0 = z0 - D.gap - D.t, dz1 = z0 - D.gap;
  extrude(door, 'z', shapeOf(arch(M.W + 2 * D.grow, M.H + 2 * D.grow, M.bottom - D.grow)), dz0, dz1, 0.6);
  box(P('door_latch', 'door', 'hardware'), [-12, M.bottom + M.H - 30, dz0 - 4], [12, M.bottom + M.H - 10, dz0 + 0.5], 1);
  // ---- flag: arm + plate in one outline (z, y), 4 mm thick, lying along the side toward the front (down)
  const fy = F.y, fz = F.z, outline = [[fz + 12, fy - 10], [fz - 140, fy - 10], [fz - 140, fy - 30], [fz - 232, fy - 30], [fz - 232, fy + 42],
    [fz - 140, fy + 42], [fz - 140, fy + 10], [fz + 12, fy + 10]];
  extrude(P('flag', 'flag', 'flag'), 'x', shapeOf(outline), F.x0, F.x1, 0);
  const MATS = {
    paint:    MAT(mottled([33, 34, 36], 0.02, 2), 6, 116, 136),
    wood:     MAT(woodGrain([108, 96, 82], 5), 4, 36, 44),
    hardware: MAT(mottled([96, 98, 96], 0.03, 4), 4, 70, 84, 230),
    flag:     MAT(mottled([158, 34, 28], 0.02, 3), 6, 104, 120),
  };
  return {PARTS, MATS, atlas: 1024, startS: 30, islandScale: null};
}

// ---------------- bake (as tools/build-trash-enclosure-v1.mjs) ----------------
const DUMMY = 'data:image/png;base64,' + png(Buffer.from([0, 0, 0, 255]), 1, 1).toString('base64');
function bake(key, id, build) {
  const {PARTS: all, MATS, atlas, startS, islandScale} = build(), PARTS = all.filter(p => p.f.length);
  for (const p of PARTS) p.v = p.v.map(q => q.map(v => v * PX));
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
  const UV = unwrap(PARTS, {atlas, pad: 2, startS, stepS: 0.5, islandScale});
  for (const p of PARTS) assert(p.f.every(f => UV.faceUV.get(p)?.has(f)), `${key}: unmapped face in ${p.name}`);
  const first = Object.values(MATS)[0], bg = [60, 62, 64];
  const painted = paint({PARTS, islands: UV.islands, S: UV.S, uvOf: UV.uvOf, atlas, pad: 2, MATS, ZONED: new Set(), groupInfo: new Map(),
    sourceGroups: () => [], refTexture: {source: DUMMY}, refUvWidth: 1, background: {c: [...bg, 255], s: [first.sm, first.f0, 0, 255], n: [128, 128, 255, 255]}});
  const maps = painted.PNG.map(b => { const r = readPng(b); assert(r.bpp === 4 && r.w === atlas, 'atlas format'); return r.px; });
  return {key, id, PARTS, MATS, atlas, UV, maps: maps.map(px => png(px, atlas, atlas))};
}
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

// ---------------- rigs: bones (pivot mm, parent), channels; bounds over every channel's sweep ----------------
const uuidOf = (ns, s) => { const h = createHash('sha256').update(`afl-rural-driveway-v1:${ns}:${s}`).digest('hex'); return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-8${h.slice(17, 20)}-${h.slice(20, 32)}`; };
/** The runtime's rotation about a pivot (JOML right-handed: Rz * Ry * Rx, so x first). */
function rotAbout(q, pivot, [ax, ay, az]) {
  let [x, y, z] = sub(q, pivot);
  if (ax) { const a = ax * D2R; [y, z] = [y * Math.cos(a) - z * Math.sin(a), y * Math.sin(a) + z * Math.cos(a)]; }
  if (ay) { const a = ay * D2R; [x, z] = [x * Math.cos(a) + z * Math.sin(a), -x * Math.sin(a) + z * Math.cos(a)]; }
  if (az) { const a = az * D2R; [x, y] = [x * Math.cos(a) - y * Math.sin(a), x * Math.sin(a) + y * Math.cos(a)]; }
  return add([x, y, z], pivot);
}
function rig(b, {bones, channels, hiddenInItem = new Set(), box: [bw, bh]}) {
  const id = b.id, uuid = k => uuidOf(id, k), names = bones.map(x => x.name), byName = new Map(bones.map(x => [x.name, x]));
  const origin = bn => byName.get(bn).pivot.map(v => v * PX);   // px
  const elements = [], nodes = new Map(names.map(bn => [bn, {uuid: uuid('group:' + bn), isOpen: true, children: []}]));
  for (const p of b.PARTS) {
    assert(byName.has(p.bone), `${id}: unknown bone ${p.bone}`);
    const key = i => i.toString(36).padStart(4, '0'), vertices = {}, faces = {}, uvs = b.UV.faceUV.get(p);
    p.v.forEach((q, i) => { vertices[key(i)] = q.map(r12); });
    p.f.forEach((f, fi) => { const uv = uvs.get(f);
      faces['f' + key(fi)] = {uv: Object.fromEntries(f.ids.map((vid, j) => [key(vid), uv[j].map(r12)])), vertices: f.ids.map(key), texture: 0}; });
    const eid = uuid('mesh:' + p.name);
    elements.push({name: p.name, color: 0, origin: [0, 0, 0], rotation: [0, 0, 0], shading: 'flat', export: true, visibility: true, locked: false,
      render_order: 'default', scope: 0, allow_mirror_modeling: true, vertices, faces, type: 'mesh', uuid: eid});
    nodes.get(p.bone).children.push(eid);
  }
  const source = {meta: {format_version: '5.0', model_format: 'free', box_uv: false}, name: id, model_identifier: '', visible_box: [bw, bh, 0],
    variable_placeholders: '', variable_placeholder_buttons: [], timeline_setups: [], unhandled_root_fields: {}, resolution: {width: b.atlas, height: b.atlas},
    elements, groups: names.map(bn => ({name: bn, uuid: uuid('group:' + bn), export: true, locked: false, scope: 0, selected: false, visibility: true,
      _static: {properties: {}, temp_data: {}}, origin: origin(bn).slice(), rotation: [0, 0, 0], color: 0, children: [], reset: false, shade: true,
      mirror_uv: false, autouv: 0, isOpen: true, primary_selected: false})),
    outliner: names.map(bn => nodes.get(bn)),
    textures: [{name: id + '.png', relative_path: `textures/${id}_v1.png`, folder: '', namespace: '', id: '0', group: '', scope: 0,
      width: b.atlas, height: b.atlas, uv_width: b.atlas, uv_height: b.atlas, particle: false, use_as_default: false, layers_enabled: false, sync_to_project: '',
      file_format: 'png', render_mode: 'default', render_sides: 'auto', wrap_mode: 'limited', pbr_channel: 'color', fps: 7, frame_time: 1,
      frame_order_type: 'loop', frame_order: '', frame_interpolate: false, visible: true, internal: true, saved: true, uuid: uuid('texture'),
      source: 'data:image/png;base64,' + b.maps[0].toString('base64')}],
    animations: []};
  const geo = {format_version: '1.12.0', 'minecraft:geometry': [{description: {identifier: 'geometry.' + id, texture_width: b.atlas,
    texture_height: b.atlas, visible_bounds_width: bw + 1, visible_bounds_height: bh + 1, visible_bounds_offset: [0, bh / 2, 0]},
    bones: names.map(bn => { const o = origin(bn); return {name: bn, pivot: [-o[0] || 0, o[1], o[2]], ...(hiddenInItem.has(bn) ? {neverRender: true} : {})}; })}]};
  const sidecar = convert(source, geo, {}, id + '.bbmodel', 2);
  // a part's vertices posed with each channel at its own progress (the child's turn first, then its parents')
  const chain = bn => { const out = []; for (let x = bn; x; x = byName.get(x).parent) out.push(x); return out; };
  const posed = (p, at) => p.v.map(q => chain(p.bone).reduce((v, bn) => {
    for (const [ch, c] of Object.entries(channels)) { const r = c.transforms[bn]?.rotation; if (r && at[ch]) v = rotAbout(v, origin(bn), r.map(a => a * at[ch])); }
    return v; }, q));
  const aabbOf = pts => [0, 1, 2].map(k => Math.min(...pts.map(q => q[k]))).concat([0, 1, 2].map(k => Math.max(...pts.map(q => q[k]))));
  const poses = [{}];
  for (const ch of Object.keys(channels)) for (let k = 1; k <= 12; k++) poses.push({[ch]: k / 12});
  const sweep = aabbOf(b.PARTS.flatMap(p => poses.flatMap(at => posed(p, at)))), m = 0.25;
  const bounds = [(sweep[0] - m + 8) / 16, (sweep[1] - m) / 16, (sweep[2] - m + 8) / 16, (sweep[3] + m + 8) / 16, (sweep[4] + m) / 16, (sweep[5] + m + 8) / 16].map(r6);
  const profile = {format_version: 1, geometry: `apocalypse_firstlight:geo/${id}.geo.json`, texture: `apocalypse_firstlight:textures/block/${id}.png`,
    origin: [0, 0, 0], scale: [1, 1, 1], facing: 'horizontal', bounds,
    parts: Object.fromEntries(bones.map(x => [x.name, {pivot: origin(x.name).map(v => r6(v / 16)), ...(x.parent ? {parent: x.parent} : {})}])),
    animations: Object.fromEntries(Object.entries(channels).map(([ch, c]) => [ch, {duration_ticks: c.ticks, easing: c.easing, transforms: c.transforms}]))};
  return {id, source, geo, sidecar, profile, bounds, posed, aabbOf};
}

const GB = bake('gate', 'tube_farm_gate', buildGate), MB = bake('mailbox', 'rural_mailbox', buildMailbox);
export const solids = {};
for (const b of [GB, MB]) for (const p of b.PARTS) { const s = solidity(p); if (s.open || s.flipped || s.vol <= 0) solids[b.id + '/' + p.name] = s; }
assert(!Object.keys(solids).length, 'not closed / wound out: ' + JSON.stringify(solids));
// the front and the back sign share a leaf but are never both shown: check each look on its own
const coplanar = {
  gate: [p => p.bone === 'sign_back', p => p.bone === 'sign_front'].flatMap(skip => zFightLevels(GB.PARTS, new Map(), {skip}).unresolved).slice(0, 6),
  mailbox: zFightLevels(MB.PARTS, new Map()).unresolved.slice(0, 6),
};
const S = GATE.swing;
const GATE_RIG = rig(GB, {
  bones: [{name: 'frame', pivot: [0, 0, 0]}, {name: 'swing', pivot: [GATE.pivot[0], 0, GATE.pivot[1]]},
    {name: 'leaf', pivot: [GATE.pivot[0], 0, GATE.pivot[1]], parent: 'swing'},
    {name: 'sign_front', pivot: [GATE.pivot[0], 0, GATE.pivot[1]], parent: 'leaf'}, {name: 'sign_back', pivot: [GATE.pivot[0], 0, GATE.pivot[1]], parent: 'leaf'}],
  channels: {open_front: {ticks: S.ticks, easing: S.easing, transforms: {swing: {rotation: [0, S.degrees, 0]}}},
    open_back: {ticks: S.ticks, easing: S.easing, transforms: {leaf: {rotation: [0, -S.degrees, 0]}}}},
  hiddenInItem: new Set(['sign_front', 'sign_back']), box: [4, 2]});
const F = MAILBOX.flag, D = MAILBOX.door;
const MAILBOX_RIG = rig(MB, {
  bones: [{name: 'body', pivot: [0, 0, 0]}, {name: 'door', pivot: [0, MAILBOX.bottom - D.grow, -MAILBOX.L / 2 - D.gap - D.t / 2]},
    {name: 'flag', pivot: [(F.x0 + F.x1) / 2, F.y, F.z]}],
  channels: {open: {ticks: D.ticks, easing: 'ease_in_out', transforms: {door: {rotation: [D.degrees, 0, 0]}}},
    flag: {ticks: F.ticks, easing: 'ease_in_out', transforms: {flag: {rotation: [F.degrees, 0, 0]}}}}, box: [1, 2]});

// the poses the block's shapes are drawn from (px, structure frame: x from the first column's west edge)
const leafParts = b => b.PARTS.filter(p => p.bone === 'leaf' && (p.name.startsWith('rail_') || p.name === 'leaf_frame'));
const gateLeaf = at => { const a = GATE_RIG.aabbOf(leafParts(GB).flatMap(p => GATE_RIG.posed(p, at))); return [a[0] + 8, a[1], a[2] + 8, a[3] + 8, a[4], a[5] + 8].map(r3); };
const doorOpen = MAILBOX_RIG.aabbOf(MB.PARTS.filter(p => p.bone === 'door').flatMap(p => MAILBOX_RIG.posed(p, {open: 1})));
const flagUp = MAILBOX_RIG.aabbOf(MB.PARTS.filter(p => p.bone === 'flag').flatMap(p => MAILBOX_RIG.posed(p, {flag: 1})));
// the door drops in front of the box, down and out; the flag stands above its pivot, clear of the body's side
assert(doorOpen[1] < MAILBOX.bottom * PX && doorOpen[2] < -MAILBOX.L / 2 * PX - 3, 'mailbox door does not drop forward: ' + doorOpen.map(r3));
assert(flagUp[4] > (F.y + 200) * PX && flagUp[3] <= -MAILBOX.W / 2 * PX, 'mailbox flag does not stand up: ' + flagUp.map(r3));
// open either way, the leaf runs along z from the hinge, clear of the hinge post
for (const [ch, sign] of [['open_front', -1], ['open_back', 1]]) {
  const a = gateLeaf({[ch]: 1});
  assert(sign < 0 ? a[2] < -40 && a[5] < 9 : a[5] > 56 && a[2] > 7, `gate ${ch}: leaf not swung that way ` + a);
}
// no leaf vertex inside the hinge post (a round post: by the distance from its axis), anywhere along either swing
for (const p of GB.PARTS.filter(p => p.bone !== 'frame')) for (const ch of ['open_front', 'open_back']) for (const t of [0, 0.25, 0.5, 0.75, 1])
  for (const q of GATE_RIG.posed(p, {[ch]: t}))
    assert(Math.hypot(q[0] - GATE.post.hingeX * PX, q[2]) >= GATE.post.r * PX - 1e-6 || q[1] > GATE.post.top * PX, `gate ${ch} ${t}: ${p.name} in the hinge post`);

// ---------------- OBJ (items: the gate closed without a sign, the mailbox shut, flag down) ----------------
const f6 = v => (+v.toFixed(6)).toString(), SMOOTH = Math.cos(36 * D2R);
function cornerNormals(p) {
  const fn = p.f.map(f => newell(f.ids.map(i => p.v[i]))), byV = new Map();
  p.f.forEach((f, k) => f.ids.forEach(i => (byV.get(i) || byV.set(i, []).get(i)).push(k)));
  return p.f.map((f, k) => { const n0 = norm(fn[k]); return f.ids.map(i => norm(byV.get(i).reduce((a, j) => dot(norm(fn[j]), n0) >= SMOOTH ? add(a, fn[j]) : a, [0, 0, 0]))); });
}
function objOf(title, file, b, parts, pose = p => p.v) {
  const out = [`# AFL ${title}, generated by tools/build-rural-driveway-v1.mjs`, `mtllib ${file.split('/').pop()}.mtl`];
  let vBase = 1, tBase = 1, nBase = 1;
  for (const p of parts) {
    out.push(`o ${p.name}`, `usemtl ${b.key}`);
    for (const q of pose(p)) out.push(`v ${f6(q[0] / 16 + 0.5)} ${f6(q[1] / 16)} ${f6(q[2] / 16 + 0.5)}`);
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
const objModel = (file, particle) => ({loader: 'forge:obj', model: `apocalypse_firstlight:models/block/${file}.obj`, automatic_culling: false,
  flip_v: true, shade_quads: true, emissive_ambient: true, ambientocclusion: false, textures: {particle: `apocalypse_firstlight:block/${particle}`}});
function guiFit(parts, rotation, size = 15.2) {
  const [ax, ay] = rotation.map(v => v * D2R);
  const rot = q => { const x = q[0] * Math.cos(ay) + q[2] * Math.sin(ay), z = -q[0] * Math.sin(ay) + q[2] * Math.cos(ay); return [x, q[1] * Math.cos(ax) - z * Math.sin(ax)]; };
  const pts = parts.flatMap(p => p.v.map(q => rot([q[0], q[1] - 8, q[2]])));
  const xs = pts.map(q => q[0]), ys = pts.map(q => q[1]), w = Math.max(...xs) - Math.min(...xs), h = Math.max(...ys) - Math.min(...ys);
  const scale = r3(Math.min(4, size / Math.max(w, h))), cx = (Math.max(...xs) + Math.min(...xs)) / 2, cy = (Math.max(...ys) + Math.min(...ys)) / 2;
  return {rotation, translation: [r3(-scale * cx), r3(-scale * cy), 0], scale: [scale, scale, scale]};
}
const cellPoints = parts => parts.flatMap(p => p.v.map(([x, y, z]) => [x + 8, y, z + 8]));

// ---------------- outputs ----------------
const bb = path.join(ROOT, 'src/main/blockbench'), assets = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight');
const outputs = [], objs = [];
const CELLS = {tube_farm_gate: ['c0r0', 'c1r0', 'c2r0', 'c3r0', 'c0r1', 'c1r1', 'c2r1', 'c3r1'], rural_mailbox: ['r0', 'r1']};
for (const [b, r, title, itemParts, guiRot] of [
  [GB, GATE_RIG, 'Rural Driveway V1 tube farm gate (closed, no sign, item)', GB.PARTS.filter(p => !p.bone.startsWith('sign_')), [20, 200, 0]],
  [MB, MAILBOX_RIG, 'Rural Driveway V1 rural mailbox (shut, flag down, item)', MB.PARTS, [20, 225, 0]]]) {
  const id = b.id, file = `${id}/item`, obj = objOf(title, file, b, itemParts);
  outputs.push([path.join(assets, `models/block/${file}.obj`), obj], [path.join(assets, `models/block/${file}.mtl`), mtlOf(title, b)],
    [path.join(assets, `models/block/${file}.json`), json(objModel(file, id))]);
  objs.push([file, obj]);
  outputs.push([path.join(assets, `geo/${id}.geo.json`), json(r.geo)], [path.join(assets, `meshes/${id}.aflmesh.json`), serializeCompact(r.sidecar)],
    [path.join(assets, `block_mesh_profiles/${id}.json`), json(r.profile)]);
  outputs.push([path.join(bb, `${id}_v1.bbmodel`), JSON.stringify(r.source)]);
  ['', '_s', '_n'].forEach((k, i) => outputs.push([path.join(bb, `textures/${id}_v1${k}.png`), b.maps[i]], [path.join(assets, `textures/block/${id}${k}.png`), b.maps[i]]));
  outputs.push([path.join(assets, `models/block/${id}/mesh.json`), json({loader: 'apocalypse_firstlight:static_mesh', textures: {particle: NS + id}})],
    [path.join(assets, `models/block/${id}/cell.json`), json({textures: {particle: NS + id}, elements: []})]);
  const variants = {};
  CELLS[id].forEach((cell, i) => { variants['cell=' + cell] = {model: NS + (i === 0 ? `${id}/mesh` : `${id}/cell`)}; });
  outputs.push([path.join(assets, `blockstates/${id}.json`), json({variants})]);
  outputs.push([path.join(assets, `models/item/${id}.json`), json({parent: NS + file, gui_light: 'side',
    display: {...heldDisplay(cellPoints(itemParts), {size: 1, rotations: {fixed: [0, 180, 0]}}), gui: guiFit(itemParts, guiRot)}})]);
}
const tris = parts => parts.reduce((s, p) => s + p.f.reduce((t, f) => t + f.ids.length - 2, 0), 0);
export const stats = {
  gate: {triangles: tris(GB.PARTS), texelsPerPx: GB.UV.S, bounds: GATE_RIG.bounds, closed: gateLeaf({}), front: gateLeaf({open_front: 1}), back: gateLeaf({open_back: 1}),
    coplanar: coplanar.gate},
  mailbox: {triangles: tris(MB.PARTS), texelsPerPx: MB.UV.S, bounds: MAILBOX_RIG.bounds, doorOpen: doorOpen.map(r3), flagUp: flagUp.map(r3), coplanar: coplanar.mailbox},
};
const isMain = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  console.log(JSON.stringify(stats));
  const pi = process.argv.indexOf('--preview');
  if (pi > 0) {
    const dir = process.argv[pi + 1]; fs.mkdirSync(dir, {recursive: true});
    for (const [file, obj] of objs) fs.writeFileSync(path.join(dir, file.replace('/', '_') + '.obj'), obj);
    // posed previews: the gate with its front sign shut and open either way, the mailbox open with the flag up
    const noBack = GB.PARTS.filter(p => p.bone !== 'sign_back');
    for (const [name, at] of [['gate_shut', {}], ['gate_front', {open_front: 1}], ['gate_back', {open_back: 1}]])
      fs.writeFileSync(path.join(dir, name + '.obj'), objOf(name, name, GB, noBack, p => GATE_RIG.posed(p, at)));
    fs.writeFileSync(path.join(dir, 'mailbox_open.obj'), objOf('mailbox_open', 'mailbox_open', MB, MB.PARTS, p => MAILBOX_RIG.posed(p, {open: 1, flag: 1})));
    for (const b of [GB, MB]) ['', '_s', '_n'].forEach((k, i) => fs.writeFileSync(path.join(dir, `${b.id}${k}.png`), b.maps[i]));
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
