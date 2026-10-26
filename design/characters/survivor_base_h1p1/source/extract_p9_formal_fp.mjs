// H1-P2: the formal P9-01 V3 first-person clips (user PASS 2026-10-22, src/main/blockbench/p9_01_v3_native.bbmodel)
// sampled at 60 Hz for the Mesh body (docs/dev/mesh_body_p9_fp_clips_h1p2.md). Every part is a 4x4 relative to the
// gun's root (row-major, Blockbench model px: +x the gun's right, +y up, -z the muzzle), evaluated as the game and
// tools/preview-fp-arms.mjs do: T(position) T(origin) Rz Ry Rx S T(-origin), hand-keyed bezier channels through
// Blockbench's own curve (tools/bb-keyframes.mjs). The camera is the camera bone's author-space rotation (deg), which
// NativeCameraBoneConsumer turns into the view's pitch / yaw / roll by negating it.
//   node design/characters/survivor_base_h1p1/source/extract_p9_formal_fp.mjs <clip> [<clip> ...]
// writes p9_formal_fp_channels.json next to this file (all requested clips; earlier clips in the file are kept).
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {bbChannel} from '../../../../tools/bb-keyframes.mjs';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '../../../..');
const src = JSON.parse(fs.readFileSync(path.join(root, 'src/main/blockbench/p9_01_v3_native.bbmodel'), 'utf8'));
const OUT = path.join(here, 'p9_formal_fp_channels.json');
const FPS = 60;
const PARTS = ['handling', 'slide', 'magazine', 'follower', 'mag_out', 'reload_magazine', 'empty_old_mag'];
const SHOWN = ['magazine', 'mag_out', 'reload_magazine', 'empty_old_mag'];

const I = () => [1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1];
const mul = (a, b) => Array.from({length: 16}, (_, i) => { let n = 0; for (let k = 0; k < 4; k++) n += a[(i >> 2) * 4 + k] * b[k * 4 + i % 4]; return n; });
const chain = (...ms) => ms.reduce(mul, I());
const T = v => { const m = I(); v.forEach((n, i) => m[i * 4 + 3] = n); return m; };
const S = v => { const m = I(); v.forEach((n, i) => m[i * 5] = n); return m; };
const R = (axis, deg) => { const m = I(), j = (axis + 1) % 3, k = (axis + 2) % 3, c = Math.cos(deg * Math.PI / 180), s = Math.sin(deg * Math.PI / 180); m[j * 4 + j] = m[k * 4 + k] = c; m[j * 4 + k] = -s; m[k * 4 + j] = s; return m; };
const neg = v => v.map(n => -n);

function sample(keys, ch, t, def) {
  const ks = keys.filter(k => k.channel === ch).sort((a, b) => a.time - b.time), vec = k => ['x', 'y', 'z'].map(a => +k.data_points[0][a]);
  if (!ks.length) return def;
  if (ks.some(k => k.interpolation === 'bezier')) return bbChannel(ks)(t);
  let i = ks.findIndex(k => k.time >= t);
  if (i === 0) return vec(ks[0]); if (i < 0) return vec(ks.at(-1));
  const a = ks[i - 1], b = ks[i], u = a.interpolation === 'step' && t < b.time ? 0 : (t - a.time) / (b.time - a.time);
  return vec(a).map((n, j) => n + (vec(b)[j] - n) * u);
}

const groups = new Map(src.groups.map(g => [g.uuid, g])), by = new Map(src.groups.map(g => [g.name, g])), parents = new Map();
(function walk(nodes, parent) { for (const n of nodes) if (typeof n !== 'string') { const g = groups.get(n.uuid); parents.set(g.name, parent); walk(n.children || [], g.name); } })(src.outliner, null);

function clip(name) {
  const anim = src.animations.find(a => a.name === name || a.name.endsWith('.' + name));
  if (!anim) throw new Error('no clip ' + name);
  const keysOf = g => anim.animators?.[g.uuid]?.keyframes || [];
  const frames = [];
  const last = Math.round(anim.length * FPS);
  for (let f = 0; f <= last; f++) {
    const t = Math.min(anim.length, f / FPS), cache = new Map();
    const matrix = n => {
      if (!n || n === 'root') return I();
      if (cache.has(n)) return cache.get(n);
      const g = by.get(n), keys = keysOf(g), p = g.origin || [0, 0, 0];
      const rot = (g.rotation || [0, 0, 0]).map((x, i) => x + sample(keys, 'rotation', t, [0, 0, 0])[i]);
      const m = chain(matrix(parents.get(n)), T(sample(keys, 'position', t, [0, 0, 0])), T(p), R(2, rot[2]), R(1, rot[1]), R(0, rot[0]),
                      S(sample(keys, 'scale', t, [1, 1, 1])), T(neg(p)));
      cache.set(n, m);
      return m;
    };
    const parts = Object.fromEntries(PARTS.map(n => [n, matrix(n).map(x => +x.toFixed(6))]));
    // shown: the part's own scale key (a hidden part is keyed to 0); a child is hidden with its parent
    const shown = Object.fromEntries(SHOWN.map(n => { let s = 1, k = n; while (k && k !== 'root') { s *= sample(keysOf(by.get(k)), 'scale', t, [1, 1, 1])[1]; k = parents.get(k); } return [n, s > 0.5 ? 1 : 0]; }));
    frames.push({t: +t.toFixed(5), parts, shown, camera: sample(keysOf(by.get('camera')), 'rotation', t, [0, 0, 0]).map(x => +x.toFixed(4))});
  }
  const sounds = Object.values(anim.animators || {}).filter(a => a.type === 'effect').flatMap(a => a.keyframes.filter(k => k.channel === 'sound')
      .map(k => ({t: k.time, effect: k.data_points[0].effect}))).sort((a, b) => a.t - b.t);
  return {length: anim.length, fps: FPS, origins: Object.fromEntries(PARTS.map(n => [n, by.get(n).origin])), sounds, frames};
}

const out = fs.existsSync(OUT) ? JSON.parse(fs.readFileSync(OUT, 'utf8')) : {_doc: '', clips: {}};
out._doc = 'Generated by extract_p9_formal_fp.mjs from src/main/blockbench/p9_01_v3_native.bbmodel (formal P9-01 V3 FP clips, user PASS '
    + '2026-10-22). parts: 4x4 row-major relative to the gun root, Blockbench px (+x right, +y up, -z muzzle); shown: 1 / 0; camera: '
    + 'author-space rotation deg (the view turns by its negative).';
for (const name of process.argv.slice(2)) {
  out.clips[name] = clip(name);
  const c = out.clips[name];
  console.log(name, c.length + ' s', c.frames.length + ' frames', 'sounds ' + c.sounds.map(s => s.t + ' ' + s.effect.split(':').pop()).join(', '));
}
fs.writeFileSync(OUT, JSON.stringify(out));
