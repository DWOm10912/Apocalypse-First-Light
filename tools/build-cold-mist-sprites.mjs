// Cold mist particle sprites (ColdMistParticle, particles/cold_mist.json): four soft pale blobs, 64x64, white RGB with
// the shape in alpha (the particle tints by vertex colour). Smooth value noise only (quintic interpolation, lattice
// periods of 8 px and more), so no per-pixel grain; alpha reaches 0 well inside the border. Slightly wider than tall
// (squash), like mist lying in layers.
//   node tools/build-cold-mist-sprites.mjs                -> writes textures/particle/cold_mist_{0..3}.png
//   node tools/build-cold-mist-sprites.mjs --check        -> verifies they are up to date
//   node tools/build-cold-mist-sprites.mjs --preview FILE -> the four sprites over a dark checker, x4 (review only)
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {png} from './cube-slab-mesh-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const OUT = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight/textures/particle');
export const N = 64, VARIANTS = 4;
export const SQUASH = [1.4, 1.25, 1.32, 1.18];   // vertical squeeze of each variant
export const PEAK_ALPHA = 230;

function rng(seed) {
  let s = seed >>> 0;
  return () => (s = (Math.imul(s, 1664525) + 1013904223) >>> 0) / 4294967296;
}
// value noise over [0,1]^2 on a (cells + 1)^2 lattice, quintic fade (C2)
function noise(seed, cells) {
  const r = rng(seed), L = cells + 1, v = Array.from({length: L * L}, r);
  const fade = t => t * t * t * (t * (t * 6 - 15) + 10);
  return (u, w) => {
    const x = u * cells, y = w * cells, i = Math.min(cells - 1, Math.floor(x)), j = Math.min(cells - 1, Math.floor(y));
    const fx = fade(x - i), fy = fade(y - j);
    const a = v[j * L + i], b = v[j * L + i + 1], c = v[(j + 1) * L + i], d = v[(j + 1) * L + i + 1];
    return a + (b - a) * fx + (c - a) * fy + (a - b - c + d) * fx * fy;
  };
}
const smooth = (e0, e1, x) => { const t = Math.max(0, Math.min(1, (x - e0) / (e1 - e0))); return t * t * (3 - 2 * t); };

export function sprite(k) {
  const outline = [noise(101 + k * 7, 3), noise(211 + k * 7, 6)], body = [noise(307 + k * 7, 4), noise(401 + k * 7, 8)];
  const px = Buffer.alloc(N * N * 4);
  for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
    const u = (x + 0.5) / N, w = (y + 0.5) / N, cu = u * 2 - 1, cw = (w * 2 - 1) * SQUASH[k];
    const o = outline[0](u, w) * 0.7 + outline[1](u, w) * 0.3;          // irregular, soft outline
    const rr = Math.hypot(cu, cw) / (0.62 + 0.5 * o);
    const density = Math.pow(1 - smooth(0.05, 1.0, rr), 1.6);
    const b = body[0](u, w) * 0.65 + body[1](u, w) * 0.35;              // gentle internal variation
    const edge = smooth(1.0, 0.84, Math.max(Math.abs(cu), Math.abs(w * 2 - 1)));
    px[(y * N + x) * 4] = px[(y * N + x) * 4 + 1] = px[(y * N + x) * 4 + 2] = 255;
    px[(y * N + x) * 4 + 3] = Math.round(PEAK_ALPHA * density * (0.62 + 0.38 * b) * edge);
  }
  return {px, png: png(px, N, N)};
}

const SPRITES = Array.from({length: VARIANTS}, (_, k) => sprite(k));
const outputs = SPRITES.map((s, k) => [path.join(OUT, `cold_mist_${k}.png`), s.png]);
const isMain = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  const stats = SPRITES.map(s => { let max = 0, sum = 0, border = 0;
    for (let i = 0; i < N * N; i++) { const a = s.px[i * 4 + 3], x = i % N, y = i / N | 0; max = Math.max(max, a); sum += a;
      if (x === 0 || y === 0 || x === N - 1 || y === N - 1) border = Math.max(border, a); }
    return {max, mean: +(sum / N / N).toFixed(1), border}; });
  console.log(JSON.stringify(stats));
  const pi = process.argv.indexOf('--preview');
  if (pi > 0) {
    const S = 4, W = N * S * VARIANTS, H = N * S, o = Buffer.alloc(W * H * 4);
    for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
      const k = x / (N * S) | 0, sx = (x % (N * S)) / S | 0, sy = y / S | 0, i = (sy * N + sx) * 4, a = SPRITES[k].px[i + 3] / 255;
      const bg = ((x >> 4) + (y >> 4)) & 1 ? 34 : 52;
      for (let c = 0; c < 3; c++) o[(y * W + x) * 4 + c] = Math.round(255 * a + bg * (1 - a));
      o[(y * W + x) * 4 + 3] = 255;
    }
    fs.writeFileSync(process.argv[pi + 1], png(o, W, H));
    console.log('preview ' + process.argv[pi + 1]);
  } else if (process.argv.includes('--check')) {
    for (const [file, data] of outputs) if (!fs.existsSync(file) || !fs.readFileSync(file).equals(data)) throw new Error('stale ' + path.relative(ROOT, file));
    console.log('CHECK OK');
  } else {
    for (const [file, data] of outputs) fs.writeFileSync(file, data);
    console.log('wrote ' + outputs.map(([f]) => path.relative(ROOT, f)).join(', '));
  }
}
