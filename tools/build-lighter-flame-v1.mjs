// Flip lighter flame (docs/gameplay/flip_lighter_v1.md, 2026-10-20): the small wick flame of a windproof lighter, drawn
// procedurally (a laminar flame is a smooth shape, unlike the pool fire's turbulence, so no simulation): a teardrop rising
// from the wick, rounded at the bottom, its shoulders a quarter of the way up, narrowing to a soft tip that sways; a faint
// blue rim round the base, a dimmer dark zone just over the wick, a yellow-white body going orange at its edges and dull
// red-orange at the tip. 16 frames loop by whole periods (height, sway and the tip's lick are sums of sin(k * phase)).
// Output: textures/effect/lighter_flame.png (128 x 256: 4 x 4 frames of 32 x 64, the wick at the bottom middle, 6 px up),
// drawn additively like the fuel flames (colour unpremultiplied, alpha from brightness); _s emissive (254) where lit.
//   node tools/build-lighter-flame-v1.mjs [--check]
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {png} from './cube-slab-mesh-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const OUT = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight/textures/effect');
const FRAMES = 16, COLS = 4, FW = 32, FH = 64, W = FW * COLS, H = FH * (FRAMES / COLS), SS = 4;
const BASE = FH - 6, LENGTH = 46, R = 8.6, SHOULDER = 0.22;
const smooth = (a, b, x) => { const t = Math.min(1, Math.max(0, (x - a) / (b - a))); return t * t * (3 - 2 * t); };
const mix = (a, b, t) => a.map((v, i) => v + (b[i] - v) * t);
const BLUE = [70, 120, 255], CORE = [255, 228, 150], BODY = [255, 182, 70], EDGE = [255, 118, 28], TIP = [206, 66, 16];

/** Half width (px) at t (0 wick .. 1 tip): a round bottom up to the shoulder, then a cosine taper. */
function halfWidth(t) {
  if (t <= 0 || t >= 1) return 0;
  if (t < SHOULDER) { const u = (SHOULDER - t) / SHOULDER; return R * Math.sqrt(Math.max(0, 1 - u * u)); }
  return R * Math.cos(Math.PI / 2 * ((t - SHOULDER) / (1 - SHOULDER)) ** 1.25);
}

/** Linear-light colour of one point of frame i (px in the frame, y down). */
function sample(i, x, y) {
  const th = 2 * Math.PI * i / FRAMES;
  const h = LENGTH * (1 + 0.06 * Math.sin(th) + 0.04 * Math.sin(2 * th + 1.3) + 0.03 * Math.sin(3 * th + 0.5));
  const t = (BASE + 2 - y) / h;
  if (t <= 0 || t >= 1) return [0, 0, 0];
  const sway = t ** 1.7 * (2.2 * Math.sin(th + 0.7) + 1.0 * Math.sin(2 * th + 2.1) + 0.5 * Math.sin(4 * th + 0.3 + 5 * t));
  const r = halfWidth(t) * (1 + 0.06 * t * Math.sin(3 * th + 7 * t));
  if (r < 0.05) return [0, 0, 0];
  const d = Math.abs(x - FW / 2 - sway) / r;
  const inside = smooth(1.0, 0.5, d);
  if (inside <= 0) return [0, 0, 0];
  // the luminous body: dim right over the wick, full from about a fifth up, fading toward the tip
  const lum = smooth(0.0, 0.16, t) * (1 - 0.55 * smooth(0.6, 1.0, t)) * (1 - 0.5 * smooth(0.45, 0.0, d) * smooth(0.2, 0.06, t));
  let c = mix(CORE, BODY, smooth(0.15, 0.75, d));
  c = mix(c, EDGE, smooth(0.55, 1.0, d) * 0.8 + smooth(0.55, 0.9, t) * 0.5);
  c = mix(c, TIP, smooth(0.72, 1.0, t));
  const k = inside * lum;
  let out = c.map(v => v / 255 * k);
  // the blue base: a thin rim round the bottom, strongest at the sides, gone by a quarter up
  const blue = smooth(0.26, 0.02, t) * smooth(0.35, 0.85, d) * inside * 0.55;
  out = out.map((v, j) => v + BLUE[j] / 255 * blue);
  return out;
}

const base = Buffer.alloc(W * H * 4), spec = Buffer.alloc(W * H * 4);
for (let i = 0; i < FRAMES; i++) {
  const ox = (i % COLS) * FW, oy = Math.floor(i / COLS) * FH;
  for (let y = 0; y < FH; y++) for (let x = 0; x < FW; x++) {
    let c = [0, 0, 0];
    for (let sy = 0; sy < SS; sy++) for (let sx = 0; sx < SS; sx++) {
      const s = sample(i, x + (sx + 0.5) / SS, y + (sy + 0.5) / SS);
      c = c.map((v, j) => v + s[j] / (SS * SS));
    }
    const m = Math.max(...c), a = Math.min(1, m * 1.1), s = m > 1e-3 ? 1 / m : 0, o = ((oy + y) * W + ox + x) * 4;
    base[o] = Math.round(Math.min(1, c[0] * s) * 255); base[o + 1] = Math.round(Math.min(1, c[1] * s) * 255); base[o + 2] = Math.round(Math.min(1, c[2] * s) * 255);
    base[o + 3] = Math.round(a * 255);
    spec[o] = 0; spec[o + 1] = 0; spec[o + 2] = 0; spec[o + 3] = a > 0.04 ? 254 : 255;
  }
}
const files = {'lighter_flame.png': png(base, W, H), 'lighter_flame_s.png': png(spec, W, H)};
if (process.argv.includes('--check')) {
  for (const [name, data] of Object.entries(files)) {
    const p = path.join(OUT, name);
    if (!fs.existsSync(p) || !fs.readFileSync(p).equals(data)) { console.error('STALE ' + name); process.exit(1); }
  }
  console.log('CHECK OK');
} else {
  for (const [name, data] of Object.entries(files)) fs.writeFileSync(path.join(OUT, name), data);
  console.log('wrote ' + Object.keys(files).join(', '));
}
