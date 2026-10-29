// Offline first-person arm/gun diagnostic (untextured, depth-tested). NOT Minecraft visual acceptance.
// Rebuilds the in-game chain: [P9 composition offset] -> item Display -> GeckoLib bone matrices (Blockbench source
// values) -> NativePlayerArmRenderer (canonical anchor, presentation scale .62/.78/.62, Vanilla 4x12x4 arm).
//   node tools/preview-fp-arms.mjs <gun> <clip> <t1,t2,...> <out.png> [--gun2 <gun> <clip>]
//   gun = p9_01 (legacy rig) | p9_01_v2_native | p9_01_v3_native | blackridge_50 | blackridge_50_v2_native | br51_01_v3_native; <gun>@ads = fully aimed down the sights;
//   <gun>=<path.bbmodel> = the same rig read from a saved copy of its source (old / new comparisons)
//   (guns with a `def`: NativeGunAds#apply's correction ads() * hip()^-1 on the display, the FOV times ads.fov_multiplier)
// Colours: grey = gun meshes, amber = magazines, blue = right arm, green = left arm (darker: the elbow and upper arm; FP_UPPER=0 hides them).
import fs from 'node:fs';
import path from 'node:path';
import {execFileSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {bbChannel} from './bb-keyframes.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const assets = path.join(root, 'src/main/resources/assets/apocalypse_firstlight');
const read = p => JSON.parse(fs.readFileSync(p, 'utf8').replace(/^﻿/, ''));
const GUNS = {
  p9_01: {src: 'src/main/blockbench/p9_01.bbmodel', display: 'models/item/p9_01_in_hand.json', comp: [0.07, 0.045], right: 'right_hand_anchor', left: 'left_hand_anchor'},
  p9_01_v2_native: {src: 'src/main/blockbench/p9_01_v2_native.bbmodel', display: 'models/item/p9_01_v2_native_in_hand.json', comp: [0, 0], right: 'right_hand_anchor', left: 'left_hand_anchor'},
  p9_01_v3_native: {src: 'src/main/blockbench/p9_01_v3_native.bbmodel', display: 'models/item/p9_01_v3_native_in_hand.json', comp: [0, 0], right: 'right_hand_anchor', left: 'left_hand_anchor', kind: 'pistol',
    def: 'src/main/resources/data/apocalypse_firstlight/native_guns/p9_01.json'},
  blackridge_50: {src: 'src/main/blockbench/blackridge_50.bbmodel', display: 'models/item/blackridge_50_in_hand.json', comp: [0, 0], right: 'right_hand_anchor', left: 'left_hand_anchor'},
  blackridge_50_v2_native: {src: 'src/main/blockbench/blackridge_50_v2_native.bbmodel', display: 'models/item/blackridge_50_v2_native_in_hand.json', comp: [0, 0], right: 'right_hand_anchor', left: 'left_hand_anchor', kind: 'pistol',
    def: 'src/main/resources/data/apocalypse_firstlight/native_guns/blackridge_50.json'},
  br51_01_v3_native: {src: 'src/main/blockbench/br51_01_v3_native.bbmodel', display: 'models/item/br51_01_v3_native_in_hand.json', comp: [0, 0], right: 'right_hand_anchor', left: 'left_hand_anchor', kind: 'rifle',
    def: 'src/main/resources/data/apocalypse_firstlight/native_guns/br51_01.json'},
};

// ---- 4x4 row-major matrices ----
const I = () => [1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1];
const mul = (a, b) => Array.from({length: 16}, (_, i) => { let n = 0; for (let k = 0; k < 4; k++) n += a[(i >> 2) * 4 + k] * b[k * 4 + i % 4]; return n; });
const chain = (...ms) => ms.reduce(mul, I());
const T = v => { const m = I(); v.forEach((n, i) => m[i * 4 + 3] = n); return m; };
const S = v => { const m = I(); v.forEach((n, i) => m[i * 5] = n); return m; };
const R = (axis, deg) => { const m = I(), j = (axis + 1) % 3, k = (axis + 2) % 3, c = Math.cos(deg * Math.PI / 180), s = Math.sin(deg * Math.PI / 180); m[j * 4 + j] = m[k * 4 + k] = c; m[j * 4 + k] = -s; m[k * 4 + j] = s; return m; };
const pt = (m, v) => [0, 1, 2].map(i => m[i * 4 + 3] + v.reduce((n, x, j) => n + m[i * 4 + j] * x, 0));
const det3 = m => m[0] * (m[5] * m[10] - m[6] * m[9]) - m[1] * (m[4] * m[10] - m[6] * m[8]) + m[2] * (m[4] * m[9] - m[5] * m[8]);
const sc = (v, s) => v.map(n => n * s);

function sample(keys, ch, t, def) {
  const ks = keys.filter(k => k.channel === ch).sort((a, b) => a.time - b.time), vec = k => ['x', 'y', 'z'].map(a => +k.data_points[0][a]);
  if (!ks.length) return def;
  if (ks.some(k => k.interpolation === 'bezier')) return bbChannel(ks)(t);   // hand-keyed channel: Blockbench's own curve
  let i = ks.findIndex(k => k.time >= t);
  if (i === 0) return vec(ks[0]); if (i < 0) return vec(ks.at(-1));
  const a = ks[i - 1], b = ks[i], u = a.interpolation === 'step' && t < b.time ? 0 : (t - a.time) / (b.time - a.time);
  return vec(a).map((n, j) => n + (vec(b)[j] - n) * u);
}

/** NativeAdsProfile#ads (the display's own hip() cancels against the item display): sights at the eye, eye_relief ahead. */
function adsBase(def) {
  const a = def.ads.profile, r = a.ads_rotation || [0, 0, 0];
  return chain(T([0, 0, -a.eye_relief]), S([a.scale, a.scale, a.scale]), R(0, r[0]), R(1, r[1]), R(2, r[2]), T(sc(a.aim, -1 / 16)));
}

export function rig(name) {
  const [spec, srcOverride] = name.split('='), [gun, mode] = spec.split('@'), ads = mode === 'ads';   // <gun>=<path.bbmodel>: a saved copy of the source (old / new comparisons)
  const cfg = GUNS[gun], src = read(path.resolve(root, srcOverride ?? cfg.src)), disp = read(path.join(assets, cfg.display)).display.firstperson_righthand;
  const def = cfg.def ? read(path.join(root, cfg.def)) : null;
  if (ads && !def) throw new Error(gun + ': no gun definition for @ads');
  const fovHalf = +(process.env.FP_FOV || 70) / 2 * (ads ? def.ads.fov_multiplier : 1);   // vanilla FOV 70 (vertical; FP_FOV), ADS times fov_multiplier
  const groups = new Map(src.groups.map(g => [g.uuid, g])), by = new Map(src.groups.map(g => [g.name, g])), parents = new Map(), owner = new Map();
  (function walk(nodes, parent) { for (const n of nodes) { if (typeof n === 'string') owner.set(n, parent); else { const g = groups.get(n.uuid); parents.set(g.name, parent); walk(n.children || [], g.name); } } })(src.outliner, null);
  const r = disp.rotation || [0, 0, 0], tr = disp.translation.map(n => n / 16);
  const base = chain(slide(), ads ? adsBase(def) : chain(T([cfg.comp[0], cfg.comp[1], 0]), T(tr), R(0, r[0]), R(1, r[1]), R(2, r[2]), S(disp.scale), T([0, 0.01, 0])));
  function scene(clip, t) {
    const anim = src.animations.find(a => a.name === clip || a.name.endsWith('.' + clip)), cache = new Map();
    const matrix = name => { if (!name) return base; if (cache.has(name)) return cache.get(name);
      const g = by.get(name), keys = anim?.animators?.[g.uuid]?.keyframes || [], p = sc(g.origin || [0, 0, 0], 1 / 16);
      const rot = (g.rotation || [0, 0, 0]).map((n, i) => n + sample(keys, 'rotation', t, [0, 0, 0])[i]);
      const m = chain(matrix(parents.get(name)), T(sc(sample(keys, 'position', t, [0, 0, 0]), 1 / 16)), T(p), R(2, rot[2]), R(1, rot[1]), R(0, rot[0]), S(sample(keys, 'scale', t, [1, 1, 1])), T(sc(p, -1)));
      cache.set(name, m); return m; };
    const anchor = name => chain(matrix(name), T(sc(by.get(name).origin, 1 / 16)));
    return {matrix, anchor};
  }
  return {cfg, src, by, owner, scene, fovHalf, ads};
}

// Vanilla player arm (Classic) in the canonical anchor frame, exactly as NativePlayerArmRenderer + NativeHandBinding.
export function armMatrix(anchorM, right) {
  const s = Math.cbrt(Math.abs(det3(anchorM)));
  return chain(anchorM, S([0.62 / s, 0.78 / s, 0.62 / s]), T([(right ? 1 : -1) / 16, -10 / 16, 0]));
}
const ARM_BOX = right => right ? [[-3, -2, -2], [1, 10, 2]] : [[-1, -2, -2], [3, 10, 2]];
// The elbow and upper arm behind the forearm box (NativePlayerArmRenderer; docs/rendering/first_person_body_v1.md 9): the box's
// near end bends at an elbow toward the shoulder, as the third person's skin mesh bends (SkinMesh: one ring shared by both
// segments, turned half the bend), the upper arm the forearm's cross-section. FP_UPPER=0 leaves it out (the old picture);
// FP_ELBOW=round: a rounded elbow instead (rings at the elbow turned 0, 1/4 .. 1 of the bend).
// The shoulder: the third person's shoulder pivot off the eye (rig afl_humanoid_v1: pivot [±5, 22, 0], eye [0, 27.63, -2.5],
// chest pivot y 18), the gun kind's shrug and chest twist (GunStance.Kind), model px at the player's 0.9375; FP_SHOULDER_SCALE
// times that (the view model's own scale shrinks the body about the eye), or FP_SHOULDER=[x,y,z] for both arms mirrored.
// FP_SLIDE (0..1): the look-down slide out of view (FirstPersonBody#gunSlide, ConfiguredGunFirstPerson), camera space: down
// 0.7 blocks, and (FP_TILT deg, at most) tipped down about the eye over the second half; the shoulder slides with it
// (FP_SHOULDER_FIXED=1: it stays, as before 2026-10-28)
export function slide() {
  const s = +(process.env.FP_SLIDE || 0), u = Math.min(1, Math.max(0, (s - 0.5) / 0.5));
  return chain(R(0, -(+process.env.FP_TILT || 0) * u * u * (3 - 2 * u)), T([0, -0.7 * s, 0]));
}
const KIND = {rifle: {twist: 30, right: [1.5, 0, -3], left: [0, 0, 0]}, pistol: {twist: 0, right: [0.5, 0, -2], left: [-0.5, 0, -2]}};
export function shoulder(kind, right) {
  if (process.env.FP_SHOULDER) return JSON.parse(process.env.FP_SHOULDER).map((v, i) => i === 0 && !right ? -v : v);
  const k = KIND[kind] ?? {twist: 0, right: [0, 0, 0], left: [0, 0, 0]}, sh = right ? k.right : k.left, side = right ? 1 : -1;
  const x = side * (5 - sh[0]), y = 4 - sh[1], z = sh[2], a = k.twist * Math.PI / 180;
  const p = [x * Math.cos(a) - z * Math.sin(a), y, x * Math.sin(a) + z * Math.cos(a)];   // the twist: left shoulder forward
  const s = 0.9375 / 16 * +(process.env.FP_SHOULDER_SCALE || 1), at = [p[0] * s, (p[1] - 9.63) * s, (p[2] + 2.5) * s];
  return process.env.FP_SHOULDER_FIXED === '1' ? at : pt(slide(), at);
}
const dot = (a, b) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2], cross = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
const nrm = v => sc(v, 1 / Math.hypot(...v));
/** Rotate v about the unit axis by ang (rad). */
const rotAbout = (v, axis, ang) => { const c = Math.cos(ang), s = Math.sin(ang), k = cross(axis, v), d = dot(axis, v); return v.map((x, i) => x * c + k[i] * s + axis[i] * d * (1 - c)); };
/** Quads of the arm's tube: the forearm box (its near end on the elbow ring), the elbow, the upper arm; [points, upper?]. */
export function armTube(am, right, sh) {
  const [lo, hi] = ARM_BOX(right), cx = (lo[0] + hi[0]) / 32, col = j => [am[j], am[4 + j], am[8 + j]];
  const P = (x, y, z) => pt(am, [x / 16, y / 16, z / 16]);
  const ring = y => [[lo[0], lo[2]], [hi[0], lo[2]], [hi[0], hi[2]], [lo[0], hi[2]]].map(([x, z]) => P(x, y, z));
  const hand = ring(hi[1]), near = ring(lo[1]), E = pt(am, [cx, lo[1] / 16, 0]);
  const quads = [[hand.slice().reverse(), false]];
  const side = (A, B, up) => { for (let i = 0; i < 4; i++) quads.push([[A[i], A[(i + 1) % 4], B[(i + 1) % 4], B[i]], up]); };
  if (process.env.FP_UPPER === '0') { side(hand, near, false); quads.push([near, false]); return quads; }
  const xa = nrm(col(0)), ya = nrm(sc(col(1), -1)), za = nrm(col(2));            // ya: from the hand toward the elbow
  const toS = sh.map((v, i) => v - E[i]), u = nrm(toS);
  // the bend: the rotation taking ya onto u (shortest arc), so the cross-section turns with it as a bent joint does
  const axis0 = cross(ya, u), sinA = Math.hypot(...axis0), axis = sinA < 1e-6 ? xa : sc(axis0, 1 / sinA);
  // FP_BEND_SHARE / FP_BEND_MAX (deg): the upper arm turns only that share of the way to the shoulder, at most that far (it
  // keeps the shoulder's distance, so it still runs out of the picture)
  const full = Math.atan2(sinA, dot(ya, u)), ang = Math.min(full * +(process.env.FP_BEND_SHARE || 1), +(process.env.FP_BEND_MAX || 180) * Math.PI / 180);
  armTube.last = full * 180 / Math.PI;
  const turned = (f, at) => near.map(p => { const o = p.map((v, i) => v - E[i]); return rotAbout(o, axis, ang * f).map((v, i) => v + at[i]); });
  const end = E.map((v, i) => v + rotAbout(ya, axis, ang)[i] * Math.hypot(...toS)), top = turned(1, end);
  if (process.env.FP_ELBOW === 'round') {
    side(hand, near, false);
    let prev = near;
    for (const f of [0.25, 0.5, 0.75, 1]) { const r = turned(f, E); side(prev, r, true); prev = r; }
    side(prev, top, true);
  } else {
    const mid = turned(0.5, E);                                                    // the shared ring, half the bend
    side(hand, mid, false); side(mid, top, true);
  }
  quads.push([top, true]);
  return quads;
}

function renderFrame(g, clip, t, W, H, pixels, zbuf, ox, oy, stride) {
  const sc2 = g.scene(clip, t), f = H / 2 / Math.tan(g.fovHalf * Math.PI / 180);
  const drawPoly = (P, color) => {
    const clipped = []; for (let i = 0; i < P.length; i++) { const a = P[i], b = P[(i + 1) % P.length], ia = a[2] <= -0.025, ib = b[2] <= -0.025; if (ia) clipped.push(a); if (ia !== ib) { const q = (-0.025 - a[2]) / (b[2] - a[2]); clipped.push(a.map((v, j) => v + (b[j] - v) * q)); } }
    if (clipped.length < 3) return;
    const e1 = clipped[1].map((v, i) => v - clipped[0][i]), e2 = clipped[2].map((v, i) => v - clipped[0][i]);
    const n = [e1[1] * e2[2] - e1[2] * e2[1], e1[2] * e2[0] - e1[0] * e2[2], e1[0] * e2[1] - e1[1] * e2[0]], ln = Math.hypot(...n) || 1;
    const light = 0.42 + 0.3 * Math.abs(n[1] / ln) + 0.28 * Math.abs(n[2] / ln), c = color.map(v => Math.min(255, Math.round(v * light)));
    const pr = clipped.map(v => [W / 2 + v[0] / -v[2] * f, H / 2 - v[1] / -v[2] * f, -v[2]]);
    for (let j = 1; j < pr.length - 1; j++) { const [a, b, cc] = [pr[0], pr[j], pr[j + 1]], den = (b[1] - cc[1]) * (a[0] - cc[0]) + (cc[0] - b[0]) * (a[1] - cc[1]); if (Math.abs(den) < 1e-9) continue;
      for (let y = Math.max(0, Math.floor(Math.min(a[1], b[1], cc[1]))); y < Math.min(H, Math.ceil(Math.max(a[1], b[1], cc[1]))); y++)
        for (let x = Math.max(0, Math.floor(Math.min(a[0], b[0], cc[0]))); x < Math.min(W, Math.ceil(Math.max(a[0], b[0], cc[0]))); x++) {
          const u = ((b[1] - cc[1]) * (x + .5 - cc[0]) + (cc[0] - b[0]) * (y + .5 - cc[1])) / den, v = ((cc[1] - a[1]) * (x + .5 - cc[0]) + (a[0] - cc[0]) * (y + .5 - cc[1])) / den, l = 1 - u - v;
          if (Math.min(u, v, l) < 0) continue; const z = 1 / (u / a[2] + v / b[2] + l / cc[2]), i = (oy + y) * stride + ox + x; if (z >= zbuf[i]) continue; zbuf[i] = z; pixels.set(c, i * 3); } }
  };
  for (const el of g.src.elements) {
    if (el.type !== 'mesh' || el.visibility === false && false) continue;
    const bone = g.owner.get(el.uuid); if (!bone) continue;
    const bm = sc2.matrix(bone), o = sc(el.origin || [0, 0, 0], 1 / 16), r = el.rotation || [0, 0, 0];
    const m = chain(bm, T(o), R(2, r[2]), R(1, r[1]), R(0, r[0]));
    if (Math.abs(det3(m)) < 1e-9) continue;
    const V = Object.fromEntries(Object.entries(el.vertices).map(([k, v]) => [k, pt(m, sc(v, 1 / 16))]));
    const col = /magazine|mag_out|empty_old|reload_mag/.test(bone) || /magazine/.test(el.name) ? [205, 150, 60] : [120, 124, 130];
    for (const fc of Object.values(el.faces)) drawPoly(fc.vertices.map(k => V[k]), col);
  }
  for (const right of [true, false]) {
    const am = armMatrix(sc2.anchor(right ? g.cfg.right : g.cfg.left), right);
    for (const [q, up] of armTube(am, right, shoulder(g.cfg.kind, right))) drawPoly(q, right ? (up ? [60, 95, 160] : [90, 140, 220]) : (up ? [55, 130, 75] : [90, 190, 110]));
  }
}

export function sheet(rows, out, W = 480, H = 270) {
  const cols = Math.max(...rows.map(r => r.times.length)), SW = W * cols, SH = H * rows.length;
  const pixels = Buffer.alloc(SW * SH * 3, 34), zbuf = new Float64Array(SW * SH).fill(Infinity);
  rows.forEach((row, ri) => row.times.forEach((t, ci) => renderFrame(row.rig, row.clip, t, W, H, pixels, zbuf, ci * W, ri * H, SW)));
  for (let y = 0; y < SH; y++) for (let x = 0; x < SW; x++) if (x % W === 0 || y % H === 0) pixels.set([255, 255, 255], (y * SW + x) * 3);
  const ppm = out.replace(/\.png$/, '.ppm');
  fs.writeFileSync(ppm, Buffer.concat([Buffer.from(`P6\n${SW} ${SH}\n255\n`), pixels]));
  execFileSync('ffmpeg', ['-v', 'error', '-y', '-i', ppm, out]); fs.unlinkSync(ppm);
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const a = process.argv.slice(2), rows = [];
  for (let i = 0; i + 2 < a.length && !a[i].endsWith('.png'); i += 3) rows.push({rig: rig(a[i]), clip: a[i + 1], times: a[i + 2].split(',').map(Number)});
  const out = a.find(x => x.endsWith('.png'));
  sheet(rows, out, +(process.env.FP_W || 480), +(process.env.FP_H || 270));
  console.log('wrote', out);
}
