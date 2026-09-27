// Offline first-person arm/gun diagnostic (untextured, depth-tested). NOT Minecraft visual acceptance.
// Rebuilds the in-game chain: [P9 composition offset] -> item Display -> GeckoLib bone matrices (Blockbench source
// values) -> NativePlayerArmRenderer (canonical anchor, presentation scale .62/.78/.62, Vanilla 4x12x4 arm).
//   node tools/preview-fp-arms.mjs <gun> <clip> <t1,t2,...> <out.png> [--gun2 <gun> <clip>]
//   gun = p9_01 (legacy rig) | p9_01_v2_native | blackridge_50
// Colours: grey = gun meshes, amber = magazines, blue = right arm, green = left arm.
import fs from 'node:fs';
import path from 'node:path';
import {execFileSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const assets = path.join(root, 'src/main/resources/assets/apocalypse_firstlight');
const read = p => JSON.parse(fs.readFileSync(p, 'utf8').replace(/^﻿/, ''));
const GUNS = {
  p9_01: {src: 'src/main/blockbench/p9_01.bbmodel', display: 'models/item/p9_01_in_hand.json', comp: [0.07, 0.045], right: 'right_hand_anchor', left: 'left_hand_anchor'},
  p9_01_v2_native: {src: 'src/main/blockbench/p9_01_v2_native.bbmodel', display: 'models/item/p9_01_v2_native_in_hand.json', comp: [0, 0], right: 'right_hand_anchor', left: 'left_hand_anchor'},
  blackridge_50: {src: 'src/main/blockbench/blackridge_50.bbmodel', display: 'models/item/blackridge_50_in_hand.json', comp: [0, 0], right: 'right_hand_anchor', left: 'left_hand_anchor'},
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
  let i = ks.findIndex(k => k.time >= t);
  if (i === 0) return vec(ks[0]); if (i < 0) return vec(ks.at(-1));
  const a = ks[i - 1], b = ks[i], u = a.interpolation === 'step' && t < b.time ? 0 : (t - a.time) / (b.time - a.time);
  return vec(a).map((n, j) => n + (vec(b)[j] - n) * u);
}

export function rig(gun) {
  const cfg = GUNS[gun], src = read(path.join(root, cfg.src)), disp = read(path.join(assets, cfg.display)).display.firstperson_righthand;
  const groups = new Map(src.groups.map(g => [g.uuid, g])), by = new Map(src.groups.map(g => [g.name, g])), parents = new Map(), owner = new Map();
  (function walk(nodes, parent) { for (const n of nodes) { if (typeof n === 'string') owner.set(n, parent); else { const g = groups.get(n.uuid); parents.set(g.name, parent); walk(n.children || [], g.name); } } })(src.outliner, null);
  const r = disp.rotation || [0, 0, 0], tr = disp.translation.map(n => n / 16);
  const base = chain(T([cfg.comp[0], cfg.comp[1], 0]), T(tr), R(0, r[0]), R(1, r[1]), R(2, r[2]), S(disp.scale), T([0, 0.01, 0]));
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
  return {cfg, src, by, owner, scene};
}

// Vanilla player arm (Classic) in the canonical anchor frame, exactly as NativePlayerArmRenderer + NativeHandBinding.
export function armMatrix(anchorM, right) {
  const s = Math.cbrt(Math.abs(det3(anchorM)));
  return chain(anchorM, S([0.62 / s, 0.78 / s, 0.62 / s]), T([(right ? 1 : -1) / 16, -10 / 16, 0]));
}
const ARM_BOX = right => right ? [[-3, -2, -2], [1, 10, 2]] : [[-1, -2, -2], [3, 10, 2]];

function renderFrame(g, clip, t, W, H, pixels, zbuf, ox, oy, stride) {
  const sc2 = g.scene(clip, t), f = H / 2 / Math.tan(35 * Math.PI / 180);
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
    const am = armMatrix(sc2.anchor(right ? g.cfg.right : g.cfg.left), right), [lo, hi] = ARM_BOX(right);
    const v = Array.from({length: 8}, (_, i) => pt(am, [0, 1, 2].map(j => (i & (1 << j) ? hi[j] : lo[j]) / 16)));
    for (const face of [[0, 2, 3, 1], [4, 5, 7, 6], [0, 1, 5, 4], [2, 6, 7, 3], [0, 4, 6, 2], [1, 3, 7, 5]]) drawPoly(face.map(i => v[i]), right ? [90, 140, 220] : [90, 190, 110]);
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
