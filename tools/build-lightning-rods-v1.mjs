// Lightning V2 rods (2026-10-22, docs/gameplay/lightning_v2_plan.md 4): the two lightning rods, real size, pure Mesh +
// LabPBR atlases, exported as Forge OBJ block models (chunk-baked). Model V2 (user 2026-10-22 "这些避雷针好像有点太细了",
// concept sheets design/items/lightning_rods_v2/, user "两张概念图都可以，按图改模型，杆保持真实粗细"):
// A: a modern copper air terminal (NFPA 780 Class II): a cast bronze base bolted to the roof with a hex socket and a cable
//    saddle clamp, a 5/8" x 36" solid copper rod with a blunt rounded tip, 976 mm overall, braced at half height by a
//    copper tripod (three flat straps screwed to the roof, as makers supply for terminals over 24"); the 1/2" stranded
//    down conductor leaves the clamp and runs along the roof to the cell edge under two cable holders. One cell.
// B: an old barn lightning rod (c. 1900-1920): a cast iron tripod stand, a twisted copper-skinned square rod (3/4") through
//    a 4-1/2" cobalt glass ball in copper sleeves, a sheet-copper arrow vane, a ball knob with a long spike and three
//    crow's-foot prongs, 1.80 m overall; its cable leaves a lug on the stand's collar, rides a glass knob insulator and runs
//    along the roof to the cell edge. Two cells (lower / upper half, split at the cell line).
//   node tools/build-lightning-rods-v1.mjs                -> writes sources, OBJ / MTL / block + item models, blockstates, atlases
//   node tools/build-lightning-rods-v1.mjs --check        -> verifies every output is up to date
//   node tools/build-lightning-rods-v1.mjs --preview DIR  -> writes only OBJ + maps into DIR (offline review)
// Modelled in millimetres (1 mm = 0.016 px). Frame: block bottom centre at the origin, +Y up; the cable runs toward -Z
// (north), so FACING (the way the placing player looked) turns it as the building lights' north-built models (YROT).
// Plain surfaces: no printed legends or marks. Metals (copper, bronze, iron, brass, stainless) are LabPBR f0 255.
import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {Part, AX, extrude, revolve, unwrap, paint, png, readPng, zFightLevels, area2, add, sub, mul, dot, cross, norm, newell} from './cube-slab-mesh-lib.mjs';
import {heldDisplay} from './item-held-display.mjs';
import {drawIcon, R3} from './build-material-meshes-v1.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
function assert(c, m) { if (!c) throw new Error(m); }
const PX = 0.016;            // px per mm

// ---------------- helpers (mm) ----------------
const orient = (L, ccw) => (area2(L) > 0) === ccw ? L : L.slice().reverse();
function rrect(cu, cv, w, h, r, seg = 3) {
  const u0 = cu - w / 2, v0 = cv - h / 2, u1 = cu + w / 2, v1 = cv + h / 2, out = [];
  for (const [ccu, ccv, a0] of [[u1 - r, v1 - r, 0], [u0 + r, v1 - r, 90], [u0 + r, v0 + r, 180], [u1 - r, v0 + r, 270]])
    for (let i = 0; i <= seg; i++) { const a = (a0 + 90 * i / seg) * Math.PI / 180; out.push([ccu + r * Math.cos(a), ccv + r * Math.sin(a)]); }
  return out;
}
const shapeOf = (outer, holes = []) => ({outer: orient(outer, true), holes: holes.map(h => orient(h, false))});
// plan outline (x, z) along y: the library's y plane is (u, v) = (z, x)
const planY = (part, outer, y0, y1, c) => extrude(part, 'y', shapeOf(outer.map(([x, z]) => [z, x])), y0, y1, c);
// a lathe profile [[y, r], ...] (bottom to top) turned about the vertical axis through (x, z): revolve works about Z, then
// Rx(-90) (y' = z, z' = -y) stands it up, a proper rotation (windings kept)
function lathe(part, prof, seg, x = 0, z = 0) {
  const loop = [[prof[0][0], 0], ...prof, [prof[prof.length - 1][0], 0]];
  if (area2(loop) < 0) loop.reverse();
  const n0 = part.v.length;
  revolve(part, 0, 0, loop, seg);
  for (let i = n0; i < part.v.length; i++) { const [a, b, c] = part.v[i]; part.v[i] = [a + x, c, -b + z]; }
}
// a round bar along a polyline (mm), closed at both ends: rings at each point, mitred at the bends; r is one radius or one
// per point (a taper)
function bar(part, pts, r0, seg) {
  const rad = i => Array.isArray(r0) ? r0[i] : r0;
  const dirs = pts.map((p, i) => norm(i === 0 ? sub(pts[1], p) : i === pts.length - 1 ? sub(p, pts[i - 1]) : add(norm(sub(p, pts[i - 1])), norm(sub(pts[i + 1], p)))));
  let ref = Math.abs(dirs[0][1]) < 0.9 ? [0, 1, 0] : [1, 0, 0];
  const rings = pts.map((p, i) => {
    const d = dirs[i], u = norm(cross(d, ref)), w = cross(u, d); ref = w;   // carried along, so the ring does not twist
    let scale = 1;
    if (i > 0 && i < pts.length - 1) { const a = norm(sub(p, pts[i - 1])); scale = 1 / Math.max(0.5, dot(a, d)); }
    const r = rad(i);
    return Array.from({length: seg}, (_, k) => { const t = 2 * Math.PI * (k + 0.5) / seg;
      return part.vtx(add(p, add(mul(u, r * Math.cos(t)), mul(w, r * Math.sin(t) * scale)))); });
  });
  for (let i = 0; i + 1 < pts.length; i++) for (let k = 0; k < seg; k++) {
    const j = (k + 1) % seg, mid = mul(add(add(part.v[rings[i][k]], part.v[rings[i][j]]), add(part.v[rings[i + 1][j]], part.v[rings[i + 1][k]])), 0.25);
    const axis = mul(add(pts[i], pts[i + 1]), 0.5);
    part.face([rings[i][k], rings[i][j], rings[i + 1][j], rings[i + 1][k]], sub(mid, axis));
  }
  for (const [i, s] of [[0, -1], [pts.length - 1, 1]]) { const c = part.vtx(pts[i]);
    for (let k = 0; k < seg; k++) part.face([c, rings[i][k], rings[i][(k + 1) % seg]], mul(dirs[i], s), 'cap'); }
}
// a box from p to q (its length), w wide along `side` (made square to the length), t thick: flat straps and pads
function strap(part, p, q, w, t, side) {
  const d = norm(sub(q, p)), s = norm(sub(side, mul(d, dot(side, d)))), n = cross(d, s), c = mul(add(p, q), 0.5), L = Math.hypot(...sub(q, p)) / 2;
  const at = (a, b, e) => part.vtx(add(c, add(mul(d, a * L), add(mul(s, b * w / 2), mul(n, e * t / 2)))));
  const V = {}; for (const a of [-1, 1]) for (const b of [-1, 1]) for (const e of [-1, 1]) V[`${a}${b}${e}`] = at(a, b, e);
  const quad = (ax, sg) => {
    const ks = [[-1, -1], [1, -1], [1, 1], [-1, 1]].map(([x, y]) => ax === 0 ? `${sg}${x}${y}` : ax === 1 ? `${x}${sg}${y}` : `${x}${y}${sg}`);
    part.face(ks.map(k => V[k]), mul([d, s, n][ax], sg), ax === 0 ? 'cap' : 'side');
  };
  for (const ax of [0, 1, 2]) for (const sg of [-1, 1]) quad(ax, sg);
}
// a plate in the XY plane (a vertical sheet across the cable line), its outline (x, y) mm, t thick about z = 0
const sheetZ = (part, outline, t) => extrude(part, 'z', shapeOf(outline), -t / 2, t / 2, 0);
// a twisted square bar (side s) up the Y axis from y0 to y1, one turn per `pitch`, open ends (hidden in collars)
function twisted(part, s, y0, y1, pitch, step) {
  const n = Math.max(1, Math.round((y1 - y0) / step)), h = s / 2, rings = [];
  for (let i = 0; i <= n; i++) { const y = y0 + (y1 - y0) * i / n, a = 2 * Math.PI * y / pitch;
    rings.push([[h, h], [-h, h], [-h, -h], [h, -h]].map(([u, v]) => part.vtx([u * Math.cos(a) - v * Math.sin(a), y, u * Math.sin(a) + v * Math.cos(a)]))); }
  for (let i = 0; i < n; i++) for (let k = 0; k < 4; k++) { const j = (k + 1) % 4,
    mid = mul(add(add(part.v[rings[i][k]], part.v[rings[i][j]]), add(part.v[rings[i + 1][j]], part.v[rings[i + 1][k]])), 0.25);
    part.face([rings[i][k], rings[i][j], rings[i + 1][j], rings[i + 1][k]], [mid[0], 0, mid[2]]); }
}
const MAT = (c, hl, sm, se) => ({c, hl, sm, se, f0: 20});
const METAL = (c, hl, sm, se) => ({c, hl, sm, se, f0: 255});

// ---------------- A: modern copper air terminal ----------------
export const TERMINAL = {base: 115, plate: 9, bolt: 42, rod: 15.9 / 2, socket: 62, height: 976, collar: 500, foot: 305, cable: 6.8, clamp: -40, cableY: 9.5};
// the brace's three legs, degrees round from the cable's line (-Z): the cable runs between two of them
const TERMINAL_LEGS = [60, 180, 300];
const radial = a => [Math.sin(a * Math.PI / 180), 0, -Math.cos(a * Math.PI / 180)];
const hexAt = (x, z, r, phase = Math.PI / 6) => Array.from({length: 6}, (_, i) => { const a = phase + i * Math.PI / 3; return [x + r * Math.cos(a), z + r * Math.sin(a)]; });
function buildTerminal() {
  const PARTS = [], P = (name, bone, mat) => { const p = new Part(name, bone, mat); PARTS.push(p); return p; };
  const T = TERMINAL;
  // the cast base plate, chamfered, its four stainless bolt heads (hex, 13 mm across flats) with washers
  planY(P('plate', 'body', 'bronze'), rrect(0, 0, T.base, T.base, 8), 0, T.plate, 2);
  for (const sx of [-1, 1]) for (const sz of [-1, 1]) {
    lathe(P(`washer_${sx}_${sz}`, 'body', 'steel'), [[T.plate, 11], [T.plate + 2.5, 11]], 16, sx * T.bolt, sz * T.bolt);
    planY(P(`bolt_${sx}_${sz}`, 'body', 'steel'), hexAt(sx * T.bolt, sz * T.bolt, 7.5), T.plate + 2.5, T.plate + 8, 0.6);
  }
  // the boss filleted onto the plate, the hex socket the rod screws into and its lock nut
  lathe(P('boss', 'body', 'bronze'), [[T.plate - 0.5, 27], [T.plate + 3, 25], [T.plate + 9, 22], [T.plate + 16, 20.5], [30, 20], [31, 17]], 24);
  planY(P('socket', 'body', 'bronze'), hexAt(0, 0, 17), 30, T.socket, 1.2);
  planY(P('lock', 'body', 'bronze'), hexAt(0, 0, 10.5), T.socket, T.socket + 6, 0.6);
  // the cable saddle clamp on the north side: a block, its cap and two cap screws
  planY(P('clamp', 'body', 'bronze'), rrect(0, T.clamp, 30, 34, 3), T.plate - 0.9, 29, 1.2);
  planY(P('clamp_cap', 'body', 'bronze'), rrect(0, T.clamp, 34, 30, 3), 29, 37, 1.2);
  for (const sx of [-1, 1]) planY(P(`clamp_screw_${sx}`, 'body', 'steel'), hexAt(sx * 11, T.clamp, 5), 37, 42, 0.4);
  // the copper rod: a 5/8" bar from the socket, the last 30 mm rounding into a blunt tip (NFPA 780: blunt tips)
  const tip = T.height, r = T.rod;
  lathe(P('rod', 'body', 'copper'), [[T.socket + 4, r], [tip - 30, r], [tip - 18, r * 0.97], [tip - 10, r * 0.86], [tip - 5, r * 0.66], [tip - 1.5, r * 0.36], [tip, 0.4]], 16);
  // the brace: a bronze collar clamped round the rod at half height, three 19 x 3.5 mm copper straps down to screwed feet
  lathe(P('collar', 'body', 'bronze'), [[T.collar - 16, 15], [T.collar - 13, 17], [T.collar + 13, 17], [T.collar + 16, 15]], 20);
  bar(P('collar_screw', 'body', 'steel'), [[0, T.collar, 16], [0, T.collar, 22]], 4.5, 6);
  TERMINAL_LEGS.forEach((a, k) => {
    const u = radial(a), side = [-u[2], 0, u[0]];
    const top = add(mul(u, 18), [0, T.collar - 10, 0]), knee = add(mul(u, T.foot - 25), [0, 4, 0]);
    strap(P(`leg_${k}`, 'body', 'copper'), top, knee, 19, 3.5, side);
    strap(P(`foot_${k}`, 'body', 'copper'), add(mul(u, T.foot - 34), [0, 1.75, 0]), add(mul(u, T.foot + 20), [0, 1.75, 0]), 20, 3.5, side);
    const f = mul(u, T.foot);
    lathe(P(`foot_screw_${k}`, 'body', 'steel'), [[3.5, 5.5], [5.5, 5], [7, 3]], 12, f[0], f[2]);
  });
  // the down conductor: out of the clamp, down onto the roof and along it to the cell edge, under two cable holders
  bar(P('cable', 'run', 'cable'), [[0, 20, T.clamp - 10], [0, 20, -62], [0, 16, -72], [0, 11, -82], [0, T.cableY, -96], [0, T.cableY, -499]], T.cable, 10);
  for (const [k, z] of [[0, -260], [1, -420]]) {
    planY(P(`holder_${k}`, 'run', 'bronze'), rrect(0, z, 40, 22, 2), 0, 2.5, 0.5);
    const arch = Array.from({length: 7}, (_, i) => { const a = Math.PI * i / 6; return [-10 * Math.cos(a), T.cableY + 9.5 * Math.sin(a), z]; });
    arch[0][1] = arch[6][1] = 2.5;
    bar(P(`holder_strap_${k}`, 'run', 'bronze'), arch, 2.2, 6);
  }
  const MATS = {
    bronze: METAL([128, 94, 62], 4, 96, 116),
    copper: METAL([186, 112, 74], 6, 150, 170),
    cable:  METAL([150, 90, 58], 4, 110, 128),
    steel:  METAL([168, 168, 164], 6, 150, 168),
  };
  return {PARTS, MATS, atlas: 512, startS: 32};
}

// ---------------- B: old barn lightning rod ----------------
export const BARN = {collar: [200, 310], rod: 19, twist: 158, ball: {y: 1150, r: 57, ry: 60}, vane: 1470, knob: 1642, tip: 1800, cable: 5.5, lug: [215, 240], insulator: -330};
// the stand's three legs, degrees round from the cable's line (-Z): the cable runs between two of them
const BARN_LEGS = [60, 180, 300];
function buildBarn() {
  const PARTS = [], P = (name, bone, mat) => { const p = new Part(name, bone, mat); PARTS.push(p); return p; };
  const B = BARN, [c0, c1] = B.collar;
  // the cast iron stand: a tapered collar with a ring at its foot, three curved round legs to flat feet
  lathe(P('collar', 'lower', 'iron_cast'), [[c0, 32], [c0 + 15, 32], [c0 + 15, 28], [c1 - 8, 22.5], [c1, 22], [c1 + 3, 15]], 24);
  BARN_LEGS.forEach((a, k) => {
    const u = radial(a), side = [-u[2], 0, u[0]], pts = [];
    for (let i = 0; i <= 12; i++) { const t = i / 12; pts.push(add(mul(u, 26 + 200 * t + 21 * Math.sin(t * Math.PI)), [0, (c0 + 5) * (1 - t) ** 1.35 + 7, 0])); }
    bar(P(`leg_${k}`, 'lower', 'iron_cast'), pts, 8.5, 8);
    strap(P(`foot_${k}`, 'lower', 'iron_cast'), add(mul(u, 210), [0, 3, 0]), add(mul(u, 260), [0, 3, 0]), 30, 6, side);
  });
  // the cable lug on the collar's north side; the cable down to the roof, over the glass knob insulator, on to the cell edge
  lathe(P('lug', 'lower', 'copper'), [[B.lug[0], 12], [B.lug[1], 12]], 12, 0, -36);
  bar(P('cable', 'run', 'cable'), [[0, B.lug[0] + 4, -44], [0, 170, -58], [0, 120, -72], [0, 82, -100], [0, 62, -140], [0, 57, -200],
    [0, 56, B.insulator], [0, 52, B.insulator - 30], [0, 36, -400], [0, 14, -460], [0, 7, -480], [0, 6, -499]], B.cable, 8);
  planY(P('insulator_pin', 'run', 'iron_cast'), rrect(0, B.insulator, 12, 12, 2), 0, 31, 0.6);
  lathe(P('insulator', 'run', 'insulator'), [[30, 21], [44, 20], [46, 17], [50, 16.5], [55, 15], [59, 11], [62, 5], [63, 1]], 20, 0, B.insulator);
  // the twisted square rod (copper over iron), split at the cell line so each half's model stays in its cell; it ends in the
  // ball's sleeves
  twisted(P('rod_lower', 'lower', 'copper'), B.rod, c1 - 4, 1000, B.twist, 16);
  twisted(P('rod_mid', 'upper', 'copper'), B.rod, 1000, B.ball.y - B.ball.ry + 2, B.twist, 16);
  twisted(P('rod_upper', 'upper', 'copper'), B.rod, B.ball.y + B.ball.ry - 2, B.knob - 18, B.twist, 16);
  // the cobalt glass ball, 4-1/2", a little taller than wide as the moulded ones; flared copper sleeves at both poles
  const ball = Array.from({length: 15}, (_, i) => { const a = -Math.PI / 2 + Math.PI * i / 14; return [B.ball.y + B.ball.ry * Math.sin(a), Math.max(0.01, B.ball.r * Math.cos(a))]; });
  lathe(P('ball', 'upper', 'glass'), ball.slice(1, -1), 24);
  for (const sg of [-1, 1]) {
    const y = B.ball.y + sg * (B.ball.ry - 8), far = B.ball.y + sg * (B.ball.ry + 32);
    const prof = [[y, 21], [y + sg * 10, 15], [far - sg * 4, 14.5], [far, 14.5], [far + sg * 2, 12]];
    lathe(P(`sleeve_${sg}`, 'upper', 'copper'), sg < 0 ? prof.slice().reverse() : prof, 20);
  }
  // the arrow vane across the cable line: a collar, a round shaft, a sheet head and a feathered sheet tail (2 mm copper)
  const V = B.vane;
  lathe(P('vane_collar', 'upper', 'copper'), [[V - 30, 15], [V - 26, 17], [V + 26, 17], [V + 30, 15]], 20);
  bar(P('arrow_shaft', 'upper', 'copper'), [[-300, V, 0], [272, V, 0]], 5.5, 10);
  sheetZ(P('arrow_head', 'upper', 'sheet'), [[260, V + 50], [360, V], [260, V - 50], [285, V]], 2);
  sheetZ(P('arrow_tail', 'upper', 'sheet'), [[-330, V + 75], [-200, V + 75], [-130, V + 8], [-130, V - 8], [-200, V - 75], [-330, V - 75], [-270, V]], 2);
  // the top: a ball knob, a necked base, a long spike and three crow's-foot prongs round it
  const K = B.knob;
  lathe(P('knob', 'upper', 'copper'), Array.from({length: 11}, (_, i) => { const a = -Math.PI / 2 + Math.PI * (i + 0.5) / 11; return [K + 24 * Math.sin(a), 24 * Math.cos(a)]; }), 20);
  lathe(P('neck', 'upper', 'copper'), [[K + 18, 11], [K + 48, 7], [K + 52, 8]], 16);
  lathe(P('spike', 'upper', 'copper'), [[K + 50, 6.5], [K + 80, 5], [B.tip - 25, 2], [B.tip - 6, 0.9], [B.tip, 0.3]], 12);
  [0, 120, 240].forEach((a, k) => {
    const u = radial(a + 60), base = [0, K + 40, 0], mid = add(add(base, mul(u, 35)), [0, 30, 0]);
    bar(P(`prong_${k}`, 'upper', 'copper'), [base, mid, add(add(mid, mul(u, 8)), [0, 55, 0])], [3.5, 3.5, 0.6], 8);
  });
  const MATS = {
    iron_cast: METAL([56, 53, 50], 4, 62, 80),
    copper:    METAL([150, 92, 62], 5, 104, 124),
    sheet:     METAL([156, 98, 66], 4, 110, 130),
    cable:     METAL([138, 86, 58], 4, 98, 116),
    glass:     MAT([26, 58, 152], 10, 232, 246),
    insulator: MAT([120, 176, 168], 10, 220, 240),
  };
  return {PARTS, MATS, atlas: 512, startS: 32};
}

// ---------------- bake ----------------
const DUMMY = 'data:image/png;base64,' + png(Buffer.from([0, 0, 0, 255]), 1, 1).toString('base64');
function bake(key, id, source, build) {
  const {PARTS: all, MATS, atlas, startS} = build(), PARTS = all.filter(p => p.f.length);
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
  const first = Object.values(MATS)[0];
  const painted = paint({PARTS, islands: UV.islands, S: UV.S, uvOf: UV.uvOf, atlas, pad: 2, MATS, ZONED: new Set(), groupInfo: new Map(),
    sourceGroups: () => [], refTexture: {source: DUMMY}, refUvWidth: 1, background: {c: [...first.c, 255], s: [first.sm, first.f0, 0, 255], n: [128, 128, 255, 255]}});
  const maps = painted.PNG.map(b => { const r = readPng(b); assert(r.bpp === 4 && r.w === atlas, 'atlas format'); return r.px; });
  return {key, id, source, PARTS, MATS, atlas, UV, maps: maps.map(px => png(px, atlas, atlas))};
}
const B = {
  terminal: bake('terminal', 'copper_air_terminal', 'copper_air_terminal_v1', buildTerminal),
  barn: bake('barn', 'barn_lightning_rod', 'barn_lightning_rod_v1', buildBarn),
};
// 'run': the cable laid along the roof (with its holders / insulator), drawn with the placed block only, not in the item
const bonesOf = {terminal: () => new Set(['body', 'run']), barn: half => new Set(half === 'lower' ? ['lower', 'run'] : [half])};
const coplanar = {};
for (const [k, b, bones] of [['copper_air_terminal', B.terminal, bonesOf.terminal()], ['barn_lightning_rod_lower', B.barn, bonesOf.barn('lower')], ['barn_lightning_rod_upper', B.barn, bonesOf.barn('upper')]]) {
  const zf = zFightLevels(b.PARTS.filter(p => bones.has(p.bone)), new Map()); if (zf.unresolved.length) coplanar[k] = zf.unresolved.slice(0, 6); }

// ---------------- OBJ / MTL / models ----------------
const D2R = Math.PI / 180, f6 = v => (+v.toFixed(6)).toString(), r3 = v => +v.toFixed(3) || 0;
const SMOOTH = Math.cos(36 * D2R);
function cornerNormals(p) {
  const fn = p.f.map(f => newell(f.ids.map(i => p.v[i]))), byV = new Map();
  p.f.forEach((f, k) => f.ids.forEach(i => (byV.get(i) || byV.set(i, []).get(i)).push(k)));
  return p.f.map((f, k) => { const n0 = norm(fn[k]); return f.ids.map(i => norm(byV.get(i).reduce((a, j) => dot(norm(fn[j]), n0) >= SMOOTH ? add(a, fn[j]) : a, [0, 0, 0]))); });
}
// dy: px the model is lowered by (the barn's upper half, drawn in its own cell)
function objOf(title, file, b, bones, dy = 0) {
  const out = [`# AFL ${title}, generated by tools/build-lightning-rods-v1.mjs`, `mtllib ${file.split('/').pop()}.mtl`];
  let vBase = 1, tBase = 1, nBase = 1;
  for (const p of b.PARTS) {
    if (!bones.has(p.bone)) continue;
    out.push(`o ${p.name}`, `usemtl ${b.key}`);
    for (const q of p.v) out.push(`v ${f6(q[0] / 16 + 0.5)} ${f6((q[1] - dy) / 16)} ${f6(q[2] / 16 + 0.5)}`);
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
// ---------------------------------------------------------------- the item in hand and in the inventory

const rot3 = ([ax, ay, az]) => {
  const [a, b, c] = [ax, ay, az].map(v => v * D2R);
  const Rx = [[1, 0, 0], [0, Math.cos(a), -Math.sin(a)], [0, Math.sin(a), Math.cos(a)]];
  const Ry = [[Math.cos(b), 0, Math.sin(b)], [0, 1, 0], [-Math.sin(b), 0, Math.cos(b)]];
  const Rz = [[Math.cos(c), -Math.sin(c), 0], [Math.sin(c), Math.cos(c), 0], [0, 0, 1]];
  return mmul(mmul(Rx, Ry), Rz);
};
const mmul = (P, Q) => P.map(r => [0, 1, 2].map(j => r[0] * Q[0][j] + r[1] * Q[1][j] + r[2] * Q[2][j]));
const apply = (M, v) => M.map(r => r[0] * v[0] + r[1] * v[1] + r[2] * v[2]);
const r4 = v => +v.toFixed(4) || 0;
const euler = M => [Math.atan2(-M[1][2], M[2][2]), Math.asin(Math.max(-1, Math.min(1, M[0][2]))), Math.atan2(-M[0][1], M[0][0])].map(v => r4(v / D2R));
const HAND = [['thirdperson_righthand', [0, -90, -80], [0, -0.9, 1.37], 1.0], ['firstperson_righthand', [0, -90, -110], [1.13, -0.46, 2.46], 0.85]];
/**
 * docs/models/equipment_meshes_v1.md "手持适配规则" (as tools/build-pick-mattock-v1.mjs): the wiring wrench's turn (its mesh
 * along +X, the tip out) with the rod first turned a quarter about Z, its foot (-Y) to the wrench's tip, so the rod stands
 * up past the fist and the base hangs under it; the hand closes on the rod at `grip` mm. translation = the wrench grip's
 * place in the hand - scale x turn x the rod's own grip (px, centred as the item model draws it: the cell centre at 8).
 * Real size in both views. Left hands fall back to the right (no off hand).
 */
function held(grip) {
  const toX = rot3([0, 0, 90]), c = [0, grip * PX - 8, 0], out = {}, M = {};
  for (const [view, wrench, at, scale] of HAND) {
    M[view] = mmul(rot3(wrench), toX);
    const q = apply(M[view], c);
    out[view] = {rotation: euler(M[view]), translation: at.map((v, i) => r4(v - scale * q[i])), scale: [scale, scale, scale]};
  }
  return {display: out, M};
}
/** Offline: where the rod's foot, grip and tip land against the wrench's grip point in each view (px). */
function heldReport(name, hold, pts, grip) {
  const out = {};
  for (const [view, , at, scale] of HAND) {
    const t = hold.display[view].translation, land = q => apply(hold.M[view], [q[0] - 8, q[1] - 8, q[2] - 8]).map((v, i) => r3(scale * v + t[i] - at[i]));
    const ys = pts.map(q => q[1]), foot = pts.find(q => q[1] === Math.min(...ys)), tip = pts.find(q => q[1] === Math.max(...ys));
    out[view] = {foot: land(foot), grip: land([8, grip * PX, 8]), tip: land(tip)};
  }
  return {[name]: out};
}
/**
 * The inventory icon: the textured rod rendered flat (tools/build-material-meshes-v1.mjs drawIcon, 64 px, user 2026-10-21
 * "以后游戏内的所有物品都用其3D模型的平面图"), seen from above the front as a placed block (X 30, Y 225) and leaned about Z
 * until the rod runs corner to corner, the foot at the lower left: a thin rod stood upright fills a slot's height only and
 * its 16 mm stays under a pixel. 14 of 16 slot pixels, centred on the projected mesh.
 */
function iconOf(b, bones, height) {
  const parts = b.PARTS.filter(p => bones.has(p.bone));
  const angle = az => { const d = R3([30, 225, az])([0, 1, 0]); return Math.abs(Math.atan2(d[1], d[0]) - Math.PI / 4); };
  let lean = 0;
  for (let az = -180; az <= 180; az += 0.25) if (angle(az) < angle(lean)) lean = az;
  const rotation = [30, 225, lean], R = R3(rotation), pr = parts.flatMap(p => p.v).map(q => R([q[0], q[1] - height / 2, q[2]]));
  const px = pr.map(q => q[0]), py = pr.map(q => q[1]);
  const s = r3(14 / Math.max(Math.max(...px) - Math.min(...px), Math.max(...py) - Math.min(...py)));
  const cx = (Math.max(...px) + Math.min(...px)) / 2, cy = (Math.max(...py) + Math.min(...py)) / 2;
  const view = {rotation, translation: [r3(-s * cx), r3(-s * cy), 0], scale: [s, s, s]};
  return {view, png: drawIcon(parts, b.UV, b.maps[0], view, height / 2)};
}
/** The item model: the mesh in hand, on the ground and in a frame; the flat icon in the inventory. */
function itemModel(id, parent, hold, pts) {
  const {thirdperson_righthand, firstperson_righthand, ground, fixed} = {...heldDisplay(pts, {size: 0.7, rotations: {fixed: [0, 180, 0]}}), ...hold.display};
  const icon = `apocalypse_firstlight:item/${id}`;
  return {loader: 'forge:separate_transforms', gui_light: 'front', textures: {particle: icon},
    base: {parent, textures: {particle: `apocalypse_firstlight:block/${id}`}, display: {thirdperson_righthand, firstperson_righthand, ground, fixed}},
    perspectives: {gui: {parent: 'minecraft:item/generated', textures: {layer0: icon}}}};
}

// outline boxes (cell px), mirrored in CopperAirTerminalBlock / BarnLightningRodBlock: the thin rods widened to 1 px for aiming,
// the brace and the stand's legs as one thin pad over their feet
export const SELECTION = {
  terminalBase: [7.04, 0, 7.04, 8.96, 1.1, 8.96], terminalRod: [7.5, 0, 7.5, 8.5, 15.62, 8.5], terminalCollar: [7.7, 7.74, 7.7, 8.3, 8.26, 8.3],
  terminalFeet: [2.7, 0, 2.7, 13.3, 0.25, 13.3], terminalCable: [7.6, 0, 0, 8.4, 0.7, 7.5],
  barnStand: [7.4, 0, 7.4, 8.6, 5.02, 8.6], barnFeet: [3.6, 0, 3.6, 12.4, 0.5, 12.4], barnRod: [7.5, 0, 7.5, 8.5, 16, 8.5], barnCable: [7.6, 0, 0, 8.4, 1.05, 7.6], barnLead: [7.6, 0, 6.8, 8.4, 3.9, 7.7],
  barnUpperRod: [7.5, 0, 7.5, 8.5, 12.8, 8.5], barnBall: [7.08, 0.85, 7.08, 8.92, 3.95, 8.92], barnVane: [2.7, 6.3, 7.6, 13.76, 8.72, 8.4],
  barnTop: [7.2, 9.8, 7.2, 8.8, 12.8, 8.8],
};
const fitsIn = (parts, box, dy = 0) => parts.every(p => p.v.every(q => [0, 1, 2].every(k => {
  const v = q[k] + (k === 1 ? -dy : 8); return v >= box[k] - 0.05 && v <= box[k + 3] + 0.05; })));
const named = (b, re) => b.PARTS.filter(p => re.test(p.name));
assert(fitsIn(named(B.terminal, /^(plate|washer|bolt|boss|socket|lock|clamp)/), SELECTION.terminalBase), 'terminal base leaves its box');
assert(fitsIn(named(B.terminal, /^rod$/), [7.2, 0, 7.2, 8.8, 15.62, 8.8]), 'terminal rod leaves its box');
assert(fitsIn(named(B.terminal, /^(cable|holder)/), [7.2, -0.1, 0, 8.8, 0.8, 8]), 'terminal cable leaves its box');
assert(fitsIn(B.terminal.PARTS, [0, -0.1, 0, 16, 15.62, 16]), 'terminal leaves its cell');
assert(fitsIn(named(B.barn, /^collar/), SELECTION.barnStand), 'barn stand leaves its box');
assert(fitsIn(named(B.barn, /^(lug|cable|insulator)/), [7.6, -0.1, 0, 8.4, 3.9, 7.7]), 'barn cable leaves its box');
assert(fitsIn(named(B.barn, /^(leg|foot)/), [3.6, 0, 3.6, 12.4, 5.02, 12.4]), 'barn legs leave their box');
assert(fitsIn(named(B.barn, /^(ball|sleeve)/), SELECTION.barnBall, 16), 'barn ball leaves its box');
assert(fitsIn(named(B.barn, /^(vane_collar|arrow)/), SELECTION.barnVane, 16), 'barn vane leaves its box');
assert(fitsIn(named(B.barn, /^(knob|neck|spike|prong)/), SELECTION.barnTop, 16), 'barn top leaves its box');
assert(fitsIn(B.barn.PARTS.filter(p => p.bone !== 'upper'), [0, -0.1, 0, 16, 16.01, 16]), 'barn lower half leaves its cell');
assert(fitsIn(B.barn.PARTS.filter(p => p.bone === 'upper'), [0, 0, 0, 16, 12.8, 16], 16), 'barn upper half leaves its cell');

// editable Free Model sources (px, frames as the header): one group per bone
const uuidOf = (ns, s) => { const h = createHash('sha256').update(`afl-lightning-rods-v1:${ns}:${s}`).digest('hex'); return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-8${h.slice(17, 20)}-${h.slice(20, 32)}`; };
const r12 = v => +v.toFixed(12) || 0;
function sourceOf(b) {
  const uuid = s => uuidOf(b.id, s), name = b.source;
  const bones = [...new Set(b.PARTS.map(p => p.bone))], gid = bone => uuid('group:' + bone);
  const elements = b.PARTS.map(p => {
    const key = i => i.toString(36).padStart(4, '0'), vertices = {}, faces = {}, uvs = b.UV.faceUV.get(p);
    p.v.forEach((q, i) => { vertices[key(i)] = q.map(r12); });
    p.f.forEach((f, fi) => { const uv = uvs.get(f); faces['f' + key(fi)] = {uv: Object.fromEntries(f.ids.map((id, j) => [key(id), uv[j].map(r12)])), vertices: f.ids.map(key), texture: 0}; });
    return {name: p.name, color: 0, origin: [0, 0, 0], rotation: [0, 0, 0], shading: 'flat', export: true, visibility: true, locked: false,
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
const model = (file, title, b, bones, dy) => {
  const obj = objOf(title, file, b, bones, dy);
  outputs.push([path.join(assets, `models/block/${file}.obj`), obj], [path.join(assets, `models/block/${file}.mtl`), mtlOf(title, b)],
    [path.join(assets, `models/block/${file}.json`), json(objModel(file, b.id))]);
  objs.push([file, obj]);
};
for (const b of Object.values(B)) outputs.push([path.join(bb, `${b.source}.bbmodel`), JSON.stringify(sourceOf(b))],
  ...['', '_s', '_n'].flatMap((k, i) => [[path.join(bb, `textures/${b.source}${k}.png`), b.maps[i]], [path.join(assets, `textures/block/${b.id}${k}.png`), b.maps[i]]]));
const YROT = {north: 0, east: 90, south: 180, west: 270};
const FACINGS = Object.keys(YROT);
const itemPoints = (b, bones) => b.PARTS.filter(p => bones.has(p.bone)).flatMap(p => p.v.map(([x, y, z]) => [x + 8, y, z + 8]));

// A: one model, turned by FACING
model('copper_air_terminal/terminal', 'Lightning V2 copper air terminal', B.terminal, bonesOf.terminal(), 0);
outputs.push([path.join(assets, 'blockstates/copper_air_terminal.json'), json({variants: Object.fromEntries(FACINGS.map(f =>
  [`facing=${f}`, {model: 'apocalypse_firstlight:block/copper_air_terminal/terminal', ...(YROT[f] ? {y: YROT[f]} : {})}]))})]);
const heldAt = {};
{ // the item: the terminal without the laid cable; held on the rod above the brace's collar, the brace and the base under the fist
  const bones = new Set(['body']);
  model('copper_air_terminal/item', 'Lightning V2 copper air terminal (item)', B.terminal, bones, 0);
  const pts = itemPoints(B.terminal, bones), hold = held(640), icon = iconOf(B.terminal, bones, TERMINAL.height * PX);
  Object.assign(heldAt, heldReport('terminal', hold, pts, 640));
  outputs.push([path.join(assets, 'models/item/copper_air_terminal.json'), json(itemModel('copper_air_terminal', 'apocalypse_firstlight:block/copper_air_terminal/item', hold, pts))],
    [path.join(assets, 'textures/item/copper_air_terminal.png'), icon.png]); }

// B: the lower half carries the stand, the cable and the rod up to the cell line; the upper half the rest, lowered a cell
model('barn_lightning_rod/lower', 'Lightning V2 barn lightning rod (lower half)', B.barn, bonesOf.barn('lower'), 0);
model('barn_lightning_rod/upper', 'Lightning V2 barn lightning rod (upper half)', B.barn, bonesOf.barn('upper'), 16);
outputs.push([path.join(assets, 'blockstates/barn_lightning_rod.json'), json({variants: Object.fromEntries(FACINGS.flatMap(f => ['lower', 'upper'].map(h =>
  [`facing=${f},half=${h}`, {model: `apocalypse_firstlight:block/barn_lightning_rod/${h}`, ...(YROT[f] ? {y: YROT[f]} : {})}])))})]);
{ // the item: the whole rod (both halves, without the laid cable) in one model, so its icon and held view show all of it
  const bones = new Set(['lower', 'upper']);
  model('barn_lightning_rod/item', 'Lightning V2 barn lightning rod (item)', B.barn, bones, 0);
  // held on the rod 450 mm up, over the stand
  const pts = itemPoints(B.barn, bones), hold = held(450), icon = iconOf(B.barn, bones, BARN.tip * PX);
  Object.assign(heldAt, heldReport('barn', hold, pts, 450));
  outputs.push([path.join(assets, 'models/item/barn_lightning_rod.json'), json(itemModel('barn_lightning_rod', 'apocalypse_firstlight:block/barn_lightning_rod/item', hold, pts))],
    [path.join(assets, 'textures/item/barn_lightning_rod.png'), icon.png]); }

const tris = (b, bones) => b.PARTS.filter(p => bones.has(p.bone)).reduce((s, p) => s + p.f.reduce((t, f) => t + f.ids.length - 2, 0), 0);
export const stats = {
  terminal: {triangles: tris(B.terminal, bonesOf.terminal()), texelsPerPx: B.terminal.UV.S},
  barn: {lower: tris(B.barn, bonesOf.barn('lower')), upper: tris(B.barn, bonesOf.barn('upper')), texelsPerPx: B.barn.UV.S}, coplanar, heldAt};
const isMain = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  console.log(JSON.stringify(stats));
  const pi = process.argv.indexOf('--preview');
  if (pi > 0) {
    const dir = process.argv[pi + 1]; fs.mkdirSync(dir, {recursive: true});
    for (const [file, obj] of objs) fs.writeFileSync(path.join(dir, file.replace('/', '_') + '.obj'), obj);
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
