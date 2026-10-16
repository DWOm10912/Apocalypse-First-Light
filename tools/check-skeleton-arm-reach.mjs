// Player skeleton V1 design measurement (docs/dev/player_skeleton_v1_design.md 4): read-only.
// For every native gun and every clip, samples the first-person hand anchors in camera space (blocks; +x right, +y up,
// -z forward; standing, looking level, no sway / recoil / ADS) and compares them with the shoulders of the body that will
// hold the gun in V1 (one skeleton for both views). Also prints the gun's size in first person, third person and real life.
// Bone chain as tools/audit-player-arm-presentation.mjs#aflScene (GeckoLib: pivot x and rotation x / y negated, ZYX).
// Run: node tools/check-skeleton-arm-reach.mjs
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const assets = path.join(root, 'src/main/resources/assets/apocalypse_firstlight');
const read = p => JSON.parse(fs.readFileSync(path.join(assets, p), 'utf8').replace(/^﻿/, ''));

// Vanilla player in blocks: model px * 0.9375 / 16. Eye 1.62 over the feet; shoulder pivot 2 px under the neck (1.407),
// 5 px to the side; the arm's distal cap (the hand anchor's frame, NativeHandBinding) is 10 px from the pivot.
const PX = 0.9375 / 16;
const SHOULDER = {x: 5 * PX, y: 1.501 * 0.9375 - 2 * PX - 1.62, z: 0.0};
const REACH = 10 * PX;
const RACK_LENGTH = {silverwood_12: 1.0, br51_01: 1.05, cat: 0.9, hr55: 1.4, p9_01_v2_native: 0.2, blackridge_50: 0.27};

const I = () => [1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1];
const mul = (a, b) => Array.from({length: 16}, (_, i) => { const r = i >> 2, c = i % 4; let n = 0; for (let k = 0; k < 4; k++) n += a[r * 4 + k] * b[k * 4 + c]; return n; });
const chain = (...ms) => ms.reduce(mul, I());
const T = v => { const a = I(); for (let i = 0; i < 3; i++) a[i * 4 + 3] = v[i]; return a; };
const S = v => { const a = I(); for (let i = 0; i < 3; i++) a[i * 4 + i] = v[i]; return a; };
const rot = (axis, deg) => { const a = I(), j = (axis + 1) % 3, k = (axis + 2) % 3, t = deg * Math.PI / 180, c = Math.cos(t), s = Math.sin(t); a[j * 4 + j] = a[k * 4 + k] = c; a[j * 4 + k] = -s; a[k * 4 + j] = s; return a; };
const R = v => chain(rot(2, v[2]), rot(1, v[1]), rot(0, v[0]));
const pt = (m, v) => [0, 1, 2].map(i => m[i * 4 + 3] + v.reduce((s, n, j) => s + m[i * 4 + j] * n, 0));
const div = v => v.map(n => n / 16);
const add = (a, b) => a.map((v, i) => v + b[i]);

function sample(channel, t, fallback) {
  if (channel == null) return fallback;
  if (Array.isArray(channel)) return channel.map(Number);
  if (channel.vector) return channel.vector.map(Number);
  const keys = Object.keys(channel).map(Number).sort((a, b) => a - b);
  const val = k => channel[Object.keys(channel).find(n => +n === k)];
  // Keyframes: [x, y, z], {vector}, {post, pre?} (pre is the value arriving, post the value leaving), nested either way.
  const vec = (v, arriving) => Array.isArray(v) ? v.map(Number)
      : vec(v.vector ?? (arriving ? v.pre ?? v.post : v.post ?? v.pre), arriving);
  const i = keys.findIndex(k => k >= t);
  if (i === 0) return vec(val(keys[0]), true);
  if (i < 0) return vec(val(keys.at(-1)), false);
  const a = keys[i - 1], b = keys[i], v = vec(val(a), false), w = vec(val(b), true), f = (t - a) / (b - a);
  return v.map((n, j) => n * (1 - f) + w[j] * f);
}

function scene(gun, clip, t, bare = false) {
  const bones = read(`geo/${gun}.geo.json`)['minecraft:geometry'][0].bones;
  const anim = read(`animations/${gun}.animation.json`).animations[clip]?.bones || {};
  const d = read(`models/item/${gun}_in_hand.json`).display.firstperson_righthand;
  const base = bare ? I() : chain(T(div(d.translation)), rot(0, d.rotation[0]), rot(1, d.rotation[1]), rot(2, d.rotation[2]), S(d.scale), T([0, 0.01, 0]));
  const by = new Map(bones.map(b => [b.name, b])), cache = new Map();
  const pivot = b => div([-b.pivot[0], b.pivot[1], b.pivot[2]]);
  function bm(name) {
    if (!name) return base;
    if (cache.has(name)) return cache.get(name);
    const b = by.get(name), a = anim[name] || {}, p = pivot(b);
    const r0 = b.rotation || [0, 0, 0], r1 = sample(a.rotation, t, [0, 0, 0]);
    const r = [-r0[0] - r1[0], -r0[1] - r1[1], r0[2] + r1[2]];
    const pos = sample(a.position, t, [0, 0, 0]); pos[0] *= -1;
    const m = chain(bm(b.parent), T(div(pos)), T(p), R(r), S(sample(a.scale, t, [1, 1, 1])), T(p.map(n => -n)));
    cache.set(name, m);
    return m;
  }
  const at = name => by.has(name) ? pt(bm(name), pivot(by.get(name))) : null;
  return {right: at('right_hand_anchor'), left: at('left_hand_anchor'), bones, scale: d.scale[0],
          matrix: bm, pivot: name => pivot(by.get(name))};
}

// Longest side of the gun's rest bounding box in model blocks (meshes/<gun>.aflmesh.json: vertices are bone-pivot-local
// blocks), measured through the same bone chain without the display transform.
function extent(gun) {
  const lo = [Infinity, Infinity, Infinity], hi = [-Infinity, -Infinity, -Infinity];
  if (!fs.existsSync(path.join(assets, `meshes/${gun}.aflmesh.json`))) {   // cube geo (cat): absolute model px
    for (const b of read(`geo/${gun}.geo.json`)['minecraft:geometry'][0].bones) for (const c of b.cubes || []) for (let i = 0; i < 3; i++) {
      lo[i] = Math.min(lo[i], c.origin[i]); hi[i] = Math.max(hi[i], c.origin[i] + c.size[i]);
    }
    return Math.max(...hi.map((v, i) => v - lo[i])) / 16;
  }
  const mesh = read(`meshes/${gun}.aflmesh.json`), at = scene(gun, null, 0, true);
  for (const part of mesh.parts) {
    const m = at.matrix(part.bone), p = at.pivot(part.bone);
    for (const v of part.vertices) {
      const q = pt(m, [p[0] - v[0], p[1] + v[1], p[2] + v[2]]);
      for (let i = 0; i < 3; i++) { lo[i] = Math.min(lo[i], q[i]); hi[i] = Math.max(hi[i], q[i]); }
    }
  }
  return Math.max(...hi.map((v, i) => v - lo[i]));
}

const fmt = v => v.toFixed(3);
for (const gun of Object.keys(RACK_LENGTH)) {
  const clips = Object.keys(read(`animations/${gun}.animation.json`).animations);
  const tp = read(`models/item/${gun}_in_hand.json`).display.thirdperson_righthand.scale[0];
  const first = scene(gun, 'static_idle', 0), model = extent(gun) * 16;
  const bare = scene(gun, 'static_idle', 0, true), real = RACK_LENGTH[gun] / (model / 16);
  const spacing = Math.hypot(...bare.right.map((v, i) => v - bare.left[i]));
  console.log(`\n${gun}: longest model extent ${model.toFixed(1)} px -> first person ${fmt(model * first.scale / 16)} b, `
      + `third person ${fmt(model * tp / 16 * 0.9375)} b, rack (real) ${RACK_LENGTH[gun]} b; real-size scale ${fmt(real)} `
      + `(first person ${first.scale}); hand anchors ${fmt(spacing)} model b apart at rest = ${fmt(spacing * real)} b at real size`);
  for (const clip of clips) {
    const length = read(`animations/${gun}.animation.json`).animations[clip].animation_length || 0;
    const rows = {right: [], left: []};
    for (let t = 0; t <= length + 1e-9; t += 0.05) {
      const s = scene(gun, clip, t);
      for (const side of ['right', 'left']) {
        const p = s[side];
        if (!p) continue;
        const sh = [side === 'right' ? SHOULDER.x : -SHOULDER.x, SHOULDER.y, SHOULDER.z];
        rows[side].push({t, d: Math.hypot(p[0] - sh[0], p[1] - sh[1], p[2] - sh[2]), p});
      }
    }
    const line = side => {
      const r = rows[side];
      if (!r.length) return `${side} -`;
      const far = r.reduce((a, b) => (b.d > a.d ? b : a)), near = r.reduce((a, b) => (b.d < a.d ? b : a));
      const over = r.filter(x => x.d > REACH).length / r.length;
      return `${side} ${fmt(near.d)}..${fmt(far.d)} (reach ${fmt(REACH)}, ${(over * 100).toFixed(0)}% beyond; farthest at t ${far.t.toFixed(2)} `
          + `[${far.p.map(fmt).join(', ')}])`;
    };
    console.log(`  ${clip.padEnd(18)} ${line('right')} | ${line('left')}`);
  }
}
console.log(`\nshoulders (camera space, level view): right [${fmt(SHOULDER.x)}, ${fmt(SHOULDER.y)}, ${fmt(SHOULDER.z)}], left mirrored`);
