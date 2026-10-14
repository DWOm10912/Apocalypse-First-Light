// Building Lights V1 (Building Power V1 step 2b, docs/models/building_lights_v1.md): the lights on a building's lighting
// circuit and the emergency light. A: the square LED panel light (a 600 x 600 mm flat panel in a 64 mm surface kit), the
// new look of the existing industrial_utility_light; C: the linear LED light (80 x 70 mm, 1 m per block; sections in a row
// join and only the row's ends carry end caps); D: the two-head emergency light (1.5 x real size, like the outlets; battery);
// E (2026-10-13, forest start home F7, concept B): the porcelain pull-chain lampholder with a frosted A19 bulb, on a ceiling
// or a wall. A, C and E are real size. Pure Mesh + LabPBR atlases, exported as Forge OBJ block models (chunk-baked).
//   node tools/build-building-lights-v1.mjs                -> writes sources, OBJ / MTL / block + item models, blockstates, atlases
//   node tools/build-building-lights-v1.mjs --check        -> verifies every output is up to date
//   node tools/build-building-lights-v1.mjs --preview DIR  -> writes only OBJ + maps into DIR (offline review)
// Modelled in millimetres, converted to px at the end (1 mm = 0.016 px). Frames (px, block bottom centre at the origin):
// - A and C: mounted on the cell floor with the lens up (+Y); the blockstates turn them onto the ceiling (x 180) or a wall
//   (x 90 / 270), as the old light's blockstate did. C runs along X through the whole cell (-8..8 px).
// - D: facing north as the wall outlet: front toward -Z, the wall at z = +8; body centre 7.2 px up (2.45 m in a wall's
//   third cell). Heads on the top front, turned down and outward.
// - E: modelled as mounted, since its pull chain hangs straight down either way: the ceiling model hangs from the cell's
//   top centre; the wall model faces north as D, centred 420 mm up, the chain boss on the underside.
// Lit lenses, the emergency heads, the charge LED and the lit bulb are drawn full-bright (GLOW) and carry LabPBR emission
// (EMISSION). Plain surfaces: no printed legends or logos. Dielectric materials only (painted steel and aluminium, PMMA,
// ABS, porcelain), except E's nickel bead chain and brass pull.
import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {Part, AX, extrude, revolve, unwrap, paint, png, readPng, zFightLevels, area2, add, sub, mul, dot, cross, norm, newell} from './cube-slab-mesh-lib.mjs';
import {heldDisplay} from './item-held-display.mjs';
import {logTwin} from './afl-log-mount.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
function assert(c, m) { if (!c) throw new Error(m); }
const PX = 0.016;            // px per mm
const K = 1.5;               // the emergency light's scale over real size
const m = v => v * K;

// ---------------- outlines (mm, counter-clockwise) ----------------
const orient = (L, ccw) => (area2(L) > 0) === ccw ? L : L.slice().reverse();
function rrect(cu, cv, w, h, r, seg = 3) {
  const u0 = cu - w / 2, v0 = cv - h / 2, u1 = cu + w / 2, v1 = cv + h / 2, out = [];
  for (const [ccu, ccv, a0] of [[u1 - r, v1 - r, 0], [u0 + r, v1 - r, 90], [u0 + r, v0 + r, 180], [u1 - r, v0 + r, 270]])
    for (let i = 0; i <= seg; i++) { const a = (a0 + 90 * i / seg) * Math.PI / 180; out.push([ccu + r * Math.cos(a), ccv + r * Math.sin(a)]); }
  return out;
}
const shapeOf = (outer, holes = []) => ({outer: orient(outer, true), holes: holes.map(h => orient(h, false))});
// plan outline (x, z) along y: the library's y plane is (u, v) = (z, x)
const planY = (part, outer, holes, y0, y1, c) => extrude(part, 'y', shapeOf(outer.map(([x, z]) => [z, x]), holes.map(h => h.map(([x, z]) => [z, x]))), y0, y1, c);
// a profile (z, y) along x: the library's x plane is (u, v) = (z, y)
const alongX = (part, outline, x0, x1, c) => extrude(part, 'x', shapeOf(outline), x0, x1, c);
function cyl(part, ax, cu, cv, r, a0, a1, seg, tag = 'side') {
  const A = AX[ax], ang = i => Math.PI / seg + 2 * Math.PI * i / seg;
  const rg = a => Array.from({length: seg}, (_, i) => part.vtx(A.to3(cu + r * Math.cos(ang(i)), cv + r * Math.sin(ang(i)), a)));
  const r0 = rg(a0), r1 = rg(a1);
  for (let i = 0; i < seg; i++) { const j = (i + 1) % seg, mm = (ang(i) + ang(j)) / 2;
    part.face([r0[i], r0[j], r1[j], r1[i]], A.to3(Math.cos(mm), Math.sin(mm), 0), tag); }
  for (const [ring, a, s] of [[r0, a0, -1], [r1, a1, 1]]) { const c = part.vtx(A.to3(cu, cv, a));
    for (let i = 0; i < seg; i++) part.face([c, ring[i], ring[(i + 1) % seg]], mul(A.n, s), 'cap'); }
}
const slab = (part, ax, a, b, c = 0) => { const [ku, kv, ka] = ax === 'x' ? [2, 1, 0] : ax === 'y' ? [2, 0, 1] : [0, 1, 2];
  extrude(part, ax, shapeOf([[a[ku], a[kv]], [b[ku], a[kv]], [b[ku], b[kv]], [a[ku], b[kv]]]), a[ka], b[ka], c); };
// turn a finished part: R = Ry(yaw) * Rx(pitch), then move by t (a proper rotation keeps the face windings)
function turn(part, pitch, yaw, t) {
  const cp = Math.cos(pitch), sp = Math.sin(pitch), cy = Math.cos(yaw), sy = Math.sin(yaw);
  part.v = part.v.map(([x, y, z]) => { const y1 = y * cp - z * sp, z1 = y * sp + z * cp; return [x * cy + z1 * sy + t[0], y1 + t[1], -x * sy + z1 * cy + t[2]]; });
}
const MAT = (c, hl, sm, se) => ({c, hl, sm, se, f0: 20});

// ---------------- A: square LED panel light ----------------
export const PANEL = {size: 600, depth: 64, bezel: 20, r: 6, lensDrop: 3, seam: 19};
function buildPanel() {
  const PARTS = [], P = (name, bone, mat) => { const p = new Part(name, bone, mat); PARTS.push(p); return p; };
  const S = PANEL.size, D = PANEL.depth, inner = S - 2 * PANEL.bezel;
  // the surface kit: a rounded square ring, chamfered edges; the panel's opal lens 3 mm inside its bezel
  planY(P('frame', 'frame', 'frame'), rrect(0, 0, S, S, PANEL.r), [rrect(0, 0, inner, inner, 3)], 0, D, 3);
  for (const look of ['off', 'lit']) planY(P('lens_' + look, 'lens_' + look, 'lens_' + look), rrect(0, 0, inner - 0.4, inner - 0.4, 2.8), [], D - PANEL.lensDrop - 12, D - PANEL.lensDrop, 0);
  // the hairline between the kit and the panel it holds, 19 mm off the ceiling
  planY(P('seam', 'frame', 'seam'), rrect(0, 0, S + 0.5, S + 0.5, PANEL.r + 0.25), [rrect(0, 0, S - 4, S - 4, PANEL.r - 2)], PANEL.seam - 0.7, PANEL.seam + 0.7, 0);
  const MATS = {
    frame:    MAT([234, 233, 228], 8, 132, 150),
    seam:     MAT([96, 96, 94], 0, 70, 70),
    lens_off: MAT([226, 227, 223], 4, 172, 184),
    lens_lit: MAT([255, 251, 243], 2, 172, 184),
  };
  return {PARTS, MATS, atlas: 512, startS: 24};
}

// ---------------- C: linear LED light ----------------
export const LINEAR = {w: 80, h: 60, wall: 7, back: 12, dome: 66, half: 500, cap: 3.5};
function buildLinear() {
  const PARTS = [], P = (name, bone, mat) => { const p = new Part(name, bone, mat); PARTS.push(p); return p; };
  const L = LINEAR, hw = L.w / 2, iw = hw - L.wall, r = 5, arc = (cz, cy, a0, a1) => Array.from({length: 3}, (_, i) => {
    const a = (a0 + (a1 - a0) * i / 2) * Math.PI / 180; return [cz + r * Math.cos(a), cy + r * Math.sin(a)]; });
  // extruded housing: a U open toward the lens, rounded outer edges (two faces each, no thin slivers); the ends stay
  // square so a row reads as one continuous fixture
  const housing = [[-hw, 0], [hw, 0], ...arc(hw - r, L.h - r, 0, 90), [iw, L.h], [iw, L.back], [-iw, L.back], [-iw, L.h], ...arc(-hw + r, L.h - r, 90, 180)];
  alongX(P('housing', 'body', 'housing'), housing, -L.half, L.half, 0);
  // opal lens: a shallow dome between the walls
  const dome = Array.from({length: 9}, (_, i) => { const t = i / 8; return [iw - 0.2 - (2 * iw - 0.4) * t, L.h + (L.dome - L.h) * Math.sin(Math.PI * t)]; });
  for (const look of ['off', 'lit']) alongX(P('lens_' + look, 'lens_' + look, 'lens_' + look), [[-iw + 0.2, L.h - 6], [iw - 0.2, L.h - 6], ...dome], -L.half, L.half, 0);
  // end caps (only at a row's ends): plates over the housing's and the lens's end
  const capOutline = rrect(0, (0.3 + L.dome + 0.8) / 2, L.w + 1.2, L.dome + 0.5, 6);
  alongX(P('cap_neg', 'cap_neg', 'cap'), capOutline, -L.half - 0.4, -L.half + L.cap, 0.8);
  alongX(P('cap_pos', 'cap_pos', 'cap'), capOutline, L.half - L.cap, L.half + 0.4, 0.8);
  const MATS = {
    housing:  MAT([233, 233, 229], 0, 128, 140),
    cap:      MAT([226, 226, 222], 0, 124, 136),
    lens_off: MAT([226, 227, 223], 4, 172, 184),
    lens_lit: MAT([255, 251, 243], 2, 172, 184),
  };
  return {PARTS, MATS, atlas: 512, startS: 24};
}

// ---------------- D: two-head emergency light (1.5 x) ----------------
// real mm: body 300 x 100 x 72 (a common thermoplastic twin-head unit), heads 60 mm across
export const EMERGENCY = {centreY: 450, wall: 500 - 0.2, body: [m(300), m(100), m(72)], headX: m(100), headR: m(29), headL: m(44)};
function buildEmergency() {
  const PARTS = [], P = (name, bone, mat) => { const p = new Part(name, bone, mat); PARTS.push(p); return p; };
  const E = EMERGENCY, [bw, bh, bd] = E.body, cy = E.centreY, zb = E.wall, zf = zb - bd;
  extrude(P('body', 'body', 'body'), 'z', shapeOf(rrect(0, cy, bw, bh, m(10))), zf, zb, m(4));
  // battery-compartment vent slots on the front left (+X, seen from the room), the test button and the charge LED on the right
  for (let i = 0; i < 6; i++) slab(P('vent_' + i, 'body', 'slot'), 'z', [m(119) - i * m(13), cy - m(24), zf - 0.6], [m(125) - i * m(13), cy + m(24), zf + 0.4]);
  cyl(P('test', 'body', 'test'), 'z', -m(105), cy - m(12), m(7), zf - m(3), zf + 1, 16);
  for (const look of ['off', 'on']) cyl(P('led_' + look, 'led_' + look, 'led_' + look), 'z', -m(105), cy + m(14), m(3.6), zf - m(1), zf + 1, 12);
  // two lamp heads on yokes at the top front, aimed down 31 deg and 22 deg outward
  for (const s of [-1, 1]) {
    const x = s * E.headX, hy = cy + bh / 2 + m(26), hz = zf + m(26);
    slab(P(`yoke_${s > 0 ? 'r' : 'l'}`, 'body', 'body'), 'y', [x - m(8), cy + bh / 2 - 1, hz - m(12)], [x + m(8), hy, hz + m(12)]);
    const shell = P(`head_${s > 0 ? 'r' : 'l'}`, 'body', 'body');
    cyl(shell, 'z', 0, 0, E.headR, -E.headL / 2, E.headL / 2, 20);
    turn(shell, -0.55, -s * 0.38, [x, hy, hz]);
    for (const look of ['off', 'on']) {
      const lens = P(`lens_${s > 0 ? 'r' : 'l'}_${look}`, 'head_' + look, 'head_' + look);
      cyl(lens, 'z', 0, 0, E.headR - m(3.5), -E.headL / 2 - m(1.2), -E.headL / 2 + m(1), 20);
      turn(lens, -0.55, -s * 0.38, [x, hy, hz]);
    }
  }
  const MATS = {
    body:     MAT([237, 236, 229], 6, 140, 160),
    slot:     MAT([36, 36, 38], 0, 40, 40),
    test:     MAT([170, 32, 26], 6, 150, 166),
    led_off:  MAT([30, 62, 38], 4, 170, 180),
    led_on:   MAT([110, 255, 140], 2, 176, 186),
    head_off: MAT([214, 217, 219], 4, 196, 206),
    head_on:  MAT([255, 249, 238], 2, 196, 206),
  };
  return {PARTS, MATS, atlas: 256, startS: 48};
}
// ---------------- E: porcelain pull-chain lampholder (real size) ----------------
// mm, the lamp frame: +Z out of the mounting (depth from the ceiling or wall), the chain boss toward +X. Profiles [depth, r].
export const LAMPHOLDER = {
  base: [[0, 52], [3, 52], [7, 51.5], [11, 50], [14.5, 48], [18.5, 44.5], [24, 38], [29, 31], [33, 26.5], [36, 26], [50, 26], [53, 21.5], [56, 20.5], [73, 20.5], [75, 18.5], [75.5, 14]],
  shell: [73.5, 80, 13.6],   // the bulb's screw shell: depth range, radius
  bulb: [[0, 13.3], [4, 13.3], [6, 13.7], [17, 16.5], [27, 21], [39, 26.5], [49, 29.5], [59, 30], [70, 27.5], [79, 21], [84, 11], [86, 0.1]],   // A19, from the shell's end
  boss: {x: [26, 34], d: 43, r: 5.5}, stem: {x: [34, 37.5], r: 2.2},
  chain: {r: 1.6, ceiling: 300, wall: 240}, pull: [[0, 1.6], [4, 3.5], [14, 5.8], [18, 6]],
  wallY: 420, seg: 32,
};
function buildLampholder() {
  const PARTS = [], P = (name, bone, mat) => { const p = new Part(name, bone, mat); PARTS.push(p); return p; };
  const L = LAMPHOLDER;
  // a closed lathe profile (depth, r) from the axis round to the axis, turned about the lamp frame's Z
  const turned = (part, prof, seg) => { const loop = [[prof[0][0], 0], ...prof, [prof[prof.length - 1][0], 0]];
    if (area2(loop) < 0) loop.reverse(); revolve(part, 0, 0, loop, seg); };
  // the lamp frame into the cell (mm, block bottom centre at the origin): both are proper rotations (windings kept)
  const MOUNT = {ceiling: ([x, y, z]) => [x, 1000 - z, y], wall: ([x, y, z]) => [-y, L.wallY - x, 500 - z]};
  const at = (part, mount, build) => { const n0 = part.v.length; build(); for (let i = n0; i < part.v.length; i++) part.v[i] = MOUNT[mount](part.v[i]); };
  for (const mount of ['ceiling', 'wall']) {
    const body = P(mount + '_porcelain', mount + '_body', 'porcelain');
    at(body, mount, () => turned(body, L.base, L.seg));
    const boss = P(mount + '_boss', mount + '_body', 'porcelain');
    at(boss, mount, () => cyl(boss, 'x', L.boss.d, 0, L.boss.r, L.boss.x[0], L.boss.x[1], 16));
    const stem = P(mount + '_stem', mount + '_body', 'nickel');
    at(stem, mount, () => cyl(stem, 'x', L.boss.d, 0, L.stem.r, L.stem.x[0], L.stem.x[1], 12));
    const shell = P(mount + '_shell', mount + '_body', 'shell');
    at(shell, mount, () => turned(shell, [[L.shell[0], L.shell[2]], [L.shell[1], L.shell[2]]], 24));
    for (const look of ['off', 'lit']) {
      const glass = P(mount + '_bulb_' + look, mount + '_bulb_' + look, 'bulb_' + look);
      at(glass, mount, () => turned(glass, L.bulb.map(([d, r]) => [L.shell[1] + d, r]), L.seg));
    }
    // the bead chain (a thin rod) straight down from the stem's tip, the brass pull at its end
    const top = MOUNT[mount]([L.stem.x[1] - 1.5, 0, L.boss.d]), len = L.chain[mount];
    const chain = P(mount + '_chain', mount + '_chain', 'nickel');
    cyl(chain, 'y', top[2], top[0], L.chain.r, top[1] - len, top[1] + 1, 6);
    const pull = P(mount + '_pull', mount + '_chain', 'brass'), y0 = top[1] - len, n0 = pull.v.length;
    turned(pull, L.pull, 16);
    for (let i = n0; i < pull.v.length; i++) { const [x, y, z] = pull.v[i]; pull.v[i] = [top[0] + x, y0 - z, top[2] + y]; }
  }
  const MATS = {
    porcelain: MAT([241, 237, 228], 6, 206, 220),
    shell:     MAT([196, 196, 190], 6, 150, 160),
    nickel:    {c: [192, 190, 184], hl: 8, sm: 186, se: 200, f0: 255},
    brass:     {c: [200, 170, 104], hl: 8, sm: 170, se: 186, f0: 255},
    bulb_off:  MAT([236, 233, 226], 2, 150, 160),
    bulb_lit:  MAT([255, 238, 206], 2, 150, 160),
  };
  return {PARTS, MATS, atlas: 256, startS: 48};
}
export const EMISSION = {lens_lit: 240, led_on: 220, head_on: 240, bulb_lit: 240};
// materials drawn at full brightness (Forge OBJ emissive_ambient: their MTL material has Ka 1 1 1), so the lit parts glow
// without shaders too
export const GLOW = new Set(['lens_lit', 'led_on', 'head_on', 'bulb_lit']);   // LabPBR _s alpha (0..254 = emission strength, 255 = none)

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
  for (const is of UV.islands) {
    const e = EMISSION[is.part.mat]; if (e === undefined) continue;
    for (let y = is.py - 2; y < is.py + is.H + 2; y++) for (let x = is.px - 2; x < is.px + is.W + 2; x++) maps[1][(y * atlas + x) * 4 + 3] = e;
  }
  return {key, id, source, PARTS, MATS, atlas, UV, maps: maps.map(px => png(px, atlas, atlas))};
}
const B = {
  panel: bake('panel', 'industrial_utility_light', 'square_panel_light_v1', buildPanel),
  linear: bake('linear', 'linear_light', 'linear_light_v1', buildLinear),
  emergency: bake('emergency', 'emergency_light', 'emergency_light_v1', buildEmergency),
  lampholder: bake('lampholder', 'porcelain_lampholder', 'porcelain_lampholder_v1', buildLampholder),
};
const CAPS = {both: ['cap_neg', 'cap_pos'], neg: ['cap_neg'], pos: ['cap_pos'], none: []};
const bonesOf = {
  panel: look => new Set(['frame', 'lens_' + look]),
  linear: (look, caps) => new Set(['body', 'lens_' + look, ...CAPS[caps]]),
  emergency: look => new Set(['body', look === 'charging' ? 'led_on' : 'led_off', look === 'on' ? 'head_on' : 'head_off']),
  lampholder: (look, mount) => new Set([mount + '_body', mount + '_chain', mount + '_bulb_' + look]),
};
// coplanar faces, checked per exported look
const LOOKS = {
  ...Object.fromEntries(['off', 'lit'].map(l => [`industrial_utility_light_${l}`, [B.panel, bonesOf.panel(l)]])),
  ...Object.fromEntries(['off', 'lit'].flatMap(l => Object.keys(CAPS).map(c => [`linear_light_${l}_${c}`, [B.linear, bonesOf.linear(l, c)]]))),
  ...Object.fromEntries(['off', 'charging', 'on'].map(l => [`emergency_light_${l}`, [B.emergency, bonesOf.emergency(l)]])),
  ...Object.fromEntries(['off', 'lit'].flatMap(l => ['ceiling', 'wall'].map(mt => [`porcelain_lampholder_${mt}_${l}`, [B.lampholder, bonesOf.lampholder(l, mt)]]))),
};
const coplanar = {};
for (const [k, [b, bones]] of Object.entries(LOOKS)) { const zf = zFightLevels(b.PARTS.filter(p => bones.has(p.bone)), new Map()); if (zf.unresolved.length) coplanar[k] = zf.unresolved.slice(0, 6); }

// ---------------- OBJ / MTL / models ----------------
const D2R = Math.PI / 180, f6 = v => (+v.toFixed(6)).toString(), r3 = v => +v.toFixed(3) || 0;
const SMOOTH = Math.cos(36 * D2R);
function cornerNormals(p) {
  const fn = p.f.map(f => newell(f.ids.map(i => p.v[i]))), byV = new Map();
  p.f.forEach((f, k) => f.ids.forEach(i => (byV.get(i) || byV.set(i, []).get(i)).push(k)));
  return p.f.map((f, k) => { const n0 = norm(fn[k]); return f.ids.map(i => norm(byV.get(i).reduce((a, j) => dot(norm(fn[j]), n0) >= SMOOTH ? add(a, fn[j]) : a, [0, 0, 0]))); });
}
function objOf(title, file, b, bones) {
  const out = [`# AFL ${title}, generated by tools/build-building-lights-v1.mjs`, `mtllib ${file.split('/').pop()}.mtl`];
  let vBase = 1, tBase = 1, nBase = 1;
  for (const p of b.PARTS) {
    if (!bones.has(p.bone)) continue;
    out.push(`o ${p.name}`, `usemtl ${b.key}${GLOW.has(p.mat) ? '_glow' : ''}`);
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
const mtlOf = (title, b, bones) => `# AFL ${title}\nnewmtl ${b.key}\nKd 1 1 1\nmap_Kd apocalypse_firstlight:block/${b.id}\n` +
  (b.PARTS.some(p => bones.has(p.bone) && GLOW.has(p.mat)) ? `newmtl ${b.key}_glow\nKa 1 1 1\nKd 1 1 1\nmap_Kd apocalypse_firstlight:block/${b.id}\n` : '');
const objModel = (file, particle) => ({loader: 'forge:obj', model: `apocalypse_firstlight:models/block/${file}.obj`, automatic_culling: false,
  flip_v: true, shade_quads: true, emissive_ambient: true, ambientocclusion: false, textures: {particle: `apocalypse_firstlight:block/${particle}`}});
function guiCentred(b, bones, rotation, scale) {
  const [ax, ay] = rotation.map(v => v * D2R);
  const rot = q => { const x = q[0] * Math.cos(ay) + q[2] * Math.sin(ay), z = -q[0] * Math.sin(ay) + q[2] * Math.cos(ay); return [x, q[1] * Math.cos(ax) - z * Math.sin(ax)]; };
  const pts = b.PARTS.filter(p => bones.has(p.bone)).flatMap(p => p.v.map(q => rot([q[0], q[1] - 8, q[2]])));
  const cx = (Math.max(...pts.map(q => q[0])) + Math.min(...pts.map(q => q[0]))) / 2, cy = (Math.max(...pts.map(q => q[1])) + Math.min(...pts.map(q => q[1]))) / 2;
  return {rotation, translation: [r3(-scale * cx), r3(-scale * cy), 0], scale: [scale, scale, scale]};
}
const S3 = v => [v, v, v];
const display = (b, bones, gui, guiScale, s) => ({
  thirdperson_righthand: {rotation: [75, 45, 0], translation: [0, 2.5, 0], scale: S3(s)}, thirdperson_lefthand: {rotation: [75, 45, 0], translation: [0, 2.5, 0], scale: S3(s)},
  firstperson_righthand: {rotation: [0, 45, 0], translation: [0, 2.5, 0], scale: S3(s * 1.1)}, firstperson_lefthand: {rotation: [0, 225, 0], translation: [0, 2.5, 0], scale: S3(s * 1.1)},
  gui: guiCentred(b, bones, gui, guiScale), ground: {translation: [0, 2, 0], scale: S3(s)}, fixed: {rotation: [0, 180, 0], translation: [0, 0, 0], scale: S3(s * 1.1)}});

// outline boxes (cell px, the model frames above), mirrored in IndustrialUtilityLightBlock / LinearLightBlock / EmergencyLightBlock
// (their selection boxes are these, thickened toward the room so a thin fitting is easy to aim at)
export const SELECTION = {panel: [3.2, 0, 3.2, 12.8, 1.05, 12.8], linear: [0, 0, 7.35, 16, 1.07, 8.65], emergency: [4.3, 5.9, 14.0, 11.7, 10.0, 16],
  // E (PorcelainLampholderBlock): the porcelain and bulb, and the chain (widened to 0.6 px for aiming)
  lampholderCeiling: [7.16, 13.34, 7.16, 8.84, 16, 8.84], lampholderCeilingChain: [8.26, 10.1, 7.7, 8.86, 15.4, 8.3],
  lampholderWall: [7.16, 5.88, 13.34, 8.84, 7.56, 16], lampholderWallChain: [7.7, 1.9, 14.95, 8.3, 6.2, 15.6]};
const fitsIn = (b, box) => b.PARTS.every(p => p.v.every(q => [0, 1, 2].every(k => {
  const v = q[k] + (k === 1 ? 0 : 8); return v >= box[k] - 0.02 && v <= box[k + 3] + 0.02; })));
assert(fitsIn(B.panel, SELECTION.panel), 'panel leaves its box');
assert(fitsIn(B.linear, [SELECTION.linear[0] - 0.01, ...SELECTION.linear.slice(1, 3), SELECTION.linear[3] + 0.01, ...SELECTION.linear.slice(4)]), 'linear light leaves its box');
assert(fitsIn(B.emergency, SELECTION.emergency), 'emergency light leaves its box');
{ const parts = re => ({PARTS: B.lampholder.PARTS.filter(p => re.test(p.name))});
  assert(fitsIn(parts(/^ceiling_(porcelain|boss|stem|shell|bulb)/), SELECTION.lampholderCeiling), 'ceiling lampholder leaves its box');
  assert(fitsIn(parts(/^ceiling_(chain|pull)/), SELECTION.lampholderCeilingChain), 'ceiling chain leaves its box');
  assert(fitsIn(parts(/^wall_(porcelain|boss|stem|shell|bulb)/), SELECTION.lampholderWall), 'wall lampholder leaves its box');
  assert(fitsIn(parts(/^wall_(chain|pull)/), SELECTION.lampholderWallChain), 'wall chain leaves its box'); }

// editable Free Model sources (px, frames as the header): one group per bone
const uuidOf = (ns, s) => { const h = createHash('sha256').update(`afl-building-lights-v1:${ns}:${s}`).digest('hex'); return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-8${h.slice(17, 20)}-${h.slice(20, 32)}`; };
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
// log: also the `_log` twin (the same OBJ out on a log wall's pad, tools/afl-log-mount.mjs): 'front' for models built facing
// north, 'lens' for the lens-up models turned onto a wall
const model = (file, title, b, bones, log) => {
  const obj = objOf(title, file, b, bones);
  outputs.push([path.join(assets, `models/block/${file}.obj`), obj], [path.join(assets, `models/block/${file}.mtl`), mtlOf(title, b, bones)],
    [path.join(assets, `models/block/${file}.json`), json(objModel(file, b.id))]);
  if (log) outputs.push([path.join(assets, `models/block/${file}_log.json`), json(logTwin(objModel(file, b.id), log === 'lens'))]);
  objs.push([file, obj]);
};
for (const b of Object.values(B)) outputs.push([path.join(bb, `${b.source}.bbmodel`), JSON.stringify(sourceOf(b))],
  ...['', '_s', '_n'].flatMap((k, i) => [[path.join(bb, `textures/${b.source}${k}.png`), b.maps[i]], [path.join(assets, `textures/block/${b.id}${k}.png`), b.maps[i]]]));
// A and C: the floor-mounted model turned onto the ceiling or a wall (FACING = the side the light faces, away from its support)
const MOUNT = {down: {x: 180}, north: {x: 90}, south: {x: 270}, east: {x: 90, y: 90}, west: {x: 90, y: 270}};
const YROT = {north: 0, east: 90, south: 180, west: 270};
const rot = r => ({...(r.x ? {x: r.x} : {}), ...(r.y ? {y: r.y} : {})});
// A: square panel, off / lit
for (const look of ['off', 'lit']) model(`industrial_utility_light/${look}`, `Building Lights V1 square panel light (${look})`, B.panel, bonesOf.panel(look), 'lens');
{
  // log_mount (LogMount): on a log wall's pad; never set on the ceiling (that state shares the plain model)
  const variants = {}, log = (f, lm) => lm && f !== 'down' ? '_log' : '';
  for (const [f, r] of Object.entries(MOUNT)) for (const lit of [false, true]) for (const lm of [false, true])
    variants[`facing=${f},lit=${lit},log_mount=${lm}`] = {model: `apocalypse_firstlight:block/industrial_utility_light/${lit ? 'lit' : 'off'}${log(f, lm)}`, ...rot(r)};
  outputs.push([path.join(assets, 'blockstates/industrial_utility_light.json'), json({variants})]);
  outputs.push([path.join(assets, 'models/item/industrial_utility_light.json'), json({parent: 'apocalypse_firstlight:block/industrial_utility_light/off', gui_light: 'side',
    display: display(B.panel, bonesOf.panel('off'), [30, 225, 0], 1.15, 0.55)})]);
}
// C: linear light, off / lit x which model ends carry caps. The row runs along AXIS (on a wall: along the wall); JOINED_NEG /
// JOINED_POS = a section of the same row on the world's negative / positive side. Where the model's +X ends up decides
// which cap is which: y 90 turns +X to +Z, y 270 to -Z (x turns keep X).
for (const look of ['off', 'lit']) for (const caps of Object.keys(CAPS)) model(`linear_light/${look}_${caps}`, `Building Lights V1 linear light (${look}, caps ${caps})`, B.linear, bonesOf.linear(look, caps), 'lens');
{
  const variants = {}, log = (f, lm) => lm && f !== 'down' ? '_log' : '';
  for (const [f, r0] of Object.entries(MOUNT)) for (const axis of ['x', 'z']) {
    const r = f === 'down' && axis === 'z' ? {x: 180, y: 90} : r0;
    const flip = (r.y || 0) === 270;   // the model's +X faces the world's negative side
    for (const lit of [false, true]) for (const jn of [false, true]) for (const jp of [false, true]) for (const lm of [false, true]) {
      const capNeg = flip ? !jp : !jn, capPos = flip ? !jn : !jp;
      const caps = capNeg && capPos ? 'both' : capNeg ? 'neg' : capPos ? 'pos' : 'none';
      variants[`axis=${axis},facing=${f},joined_neg=${jn},joined_pos=${jp},lit=${lit},log_mount=${lm}`] = {model: `apocalypse_firstlight:block/linear_light/${lit ? 'lit' : 'off'}_${caps}${log(f, lm)}`, ...rot(r)};
    }
  }
  outputs.push([path.join(assets, 'blockstates/linear_light.json'), json({variants})]);
  outputs.push([path.join(assets, 'models/item/linear_light.json'), json({parent: 'apocalypse_firstlight:block/linear_light/off_both', gui_light: 'side',
    display: display(B.linear, bonesOf.linear('off', 'both'), [30, 135, 0], 0.75, 0.45)})]);
}
// D: emergency light, off / charging / on
for (const look of ['off', 'charging', 'on']) model(`emergency_light/${look}`, `Building Lights V1 emergency light (${look})`, B.emergency, bonesOf.emergency(look), 'front');
{
  const variants = {};
  for (const [f, y] of Object.entries(YROT)) for (const lm of [false, true]) for (const look of ['off', 'charging', 'on'])
    variants[`facing=${f},log_mount=${lm},mode=${look}`] = {model: `apocalypse_firstlight:block/emergency_light/${look}${lm ? '_log' : ''}`, ...(y ? {y} : {})};
  outputs.push([path.join(assets, 'blockstates/emergency_light.json'), json({variants})]);
  outputs.push([path.join(assets, 'models/item/emergency_light.json'), json({parent: 'apocalypse_firstlight:block/emergency_light/off', gui_light: 'side',
    display: {...heldDisplay(B.emergency.PARTS.filter(p => bonesOf.emergency('off').has(p.bone)).flatMap(p => p.v.map(([x, y, z]) => [x + 8, y, z + 8])), {size: 0.7, rotations: {fixed: [0, 180, 0]}}),
      gui: guiCentred(B.emergency, bonesOf.emergency('off'), [10, 200, 0], 1.6)}})]);   // held, dropped and framed fitted to the model's bounds (tools/item-held-display.mjs, 2026-10-09): the fixed contexts put this off-centre model beside or above the hand
}

// E: porcelain lampholder, ceiling / wall x off / lit; FACING down = the ceiling model, a horizontal FACING = the wall model
// turned (it faces north as built); ON (the pull chain) does not change the look
for (const mt of ['ceiling', 'wall']) for (const look of ['off', 'lit'])
  model(`porcelain_lampholder/${mt}_${look}`, `Building Lights V1 porcelain lampholder (${mt}, ${look})`, B.lampholder, bonesOf.lampholder(look, mt), mt === 'wall' && 'front');
{
  const variants = {};
  for (const f of ['down', 'north', 'east', 'south', 'west']) for (const lit of [false, true]) for (const lm of [false, true]) for (const on of [false, true])
    variants[`facing=${f},lit=${lit},log_mount=${lm},on=${on}`] = {model: `apocalypse_firstlight:block/porcelain_lampholder/${f === 'down' ? 'ceiling' : 'wall'}_${lit ? 'lit' : 'off'}${lm && f !== 'down' ? '_log' : ''}`,
      ...(f !== 'down' && YROT[f] ? {y: YROT[f]} : {})};
  outputs.push([path.join(assets, 'blockstates/porcelain_lampholder.json'), json({variants})]);
  const off = bonesOf.lampholder('off', 'ceiling');
  outputs.push([path.join(assets, 'models/item/porcelain_lampholder.json'), json({parent: 'apocalypse_firstlight:block/porcelain_lampholder/ceiling_off', gui_light: 'side',
    display: {...heldDisplay(B.lampholder.PARTS.filter(p => off.has(p.bone)).flatMap(p => p.v.map(([x, y, z]) => [x + 8, y, z + 8])), {size: 0.5, rotations: {fixed: [0, 180, 0]}}),
      gui: guiCentred(B.lampholder, off, [20, 225, 0], 2.6)}})]);
}

const tris = (b, bones) => b.PARTS.filter(p => bones.has(p.bone)).reduce((s, p) => s + p.f.reduce((t, f) => t + f.ids.length - 2, 0), 0);
export const stats = {
  panel: {triangles: tris(B.panel, bonesOf.panel('lit')), texelsPerPx: B.panel.UV.S},
  linear: {triangles: tris(B.linear, bonesOf.linear('lit', 'both')), texelsPerPx: B.linear.UV.S},
  emergency: {triangles: tris(B.emergency, bonesOf.emergency('on')), texelsPerPx: B.emergency.UV.S},
  lampholder: {triangles: tris(B.lampholder, bonesOf.lampholder('lit', 'ceiling')), texelsPerPx: B.lampholder.UV.S}, coplanar};
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
