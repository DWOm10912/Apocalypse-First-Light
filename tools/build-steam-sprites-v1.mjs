// Steam sheet (client/FireFx#steam; Cooking V1 K1b, docs/gameplay/cooking_v1_plan.md 7.3). User 2026-10-27: the steam off
// the water pail had been the wood smoke's billowing puffs made white ("水蒸气贴图你是不是复用了之前的烟雾，重新写一套").
// Steam off hot water is not a cloud: thin, pale, upright wisps that leave the surface narrow, curl and fray as they rise
// and are gone within a hand or two (the droplets evaporate again).
//   textures/effect/steam (256 x 256: 4 x 2 sprites of 64 x 128, taller than wide; drawn upright, client/FireFx): white
//   with the vapour in alpha (lit by the world when drawn). Each: a wisp along a curling centre line (a sine whose
//   amplitude grows upward, pushed by noise), narrow and faint at its foot (fading in over its lowest fifth), widening upward; inside it strands (ridged noise
//   stretched along the rise, its domain warped sideways so they twist), thinning toward the top; fully clear at the edge.
//   node tools/build-steam-sprites-v1.mjs [--check]
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {png} from './cube-slab-mesh-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const FILE = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight/textures/effect/steam.png');
const W = 64, H = 128, N = 8, SW = W * 4, SH = H * 2;

function rng(seed) { let s = seed >>> 0; return () => { s = Math.imul(s ^ (s >>> 15), 2246822519) + 0x9e3779b9 >>> 0; s ^= s >>> 13; return (s >>> 0) / 4294967296; }; }
function hash(x, y, seed) { let h = Math.imul(x, 374761393) ^ Math.imul(y, 668265263) ^ Math.imul(seed, 1442695041); h = Math.imul(h ^ (h >>> 13), 1274126177); return ((h ^ (h >>> 16)) >>> 0) / 4294967296; }
function value(x, y, seed) {
  const xi = Math.floor(x), yi = Math.floor(y), fx = x - xi, fy = y - yi, u = fx * fx * (3 - 2 * fx), v = fy * fy * (3 - 2 * fy);
  const a = hash(xi, yi, seed), b = hash(xi + 1, yi, seed), c = hash(xi, yi + 1, seed), d = hash(xi + 1, yi + 1, seed);
  return a + (b - a) * u + (c - a) * v + (a - b - c + d) * u * v;
}
function fbm(x, y, seed, oct = 4) { let s = 0, w = 0.5, f = 1, n = 0; for (let o = 0; o < oct; o++) { s += w * value(x * f, y * f, seed + o * 17); n += w; w *= 0.5; f *= 2.03; } return s / n; }
// strands: ridged noise (bright along the noise's mid line), sharpened
function ridged(x, y, seed) { let s = 0, w = 0.6, f = 1, n = 0; for (let o = 0; o < 3; o++) { s += w * (1 - Math.abs(2 * value(x * f, y * f, seed + o * 29) - 1)); n += w; w *= 0.5; f *= 2.1; } return s / n; }
const smooth = (a, b, x) => { const t = Math.min(1, Math.max(0, (x - a) / (b - a))); return t * t * (3 - 2 * t); };

const sheet = Buffer.alloc(SW * SH * 4);
for (let k = 0; k < N; k++) {
  const r = rng(7300 + k * 37), seed = 900 + k * 11;
  const ox = (k % 4) * W, oy = Math.floor(k / 4) * H;
  const turns = 0.7 + r() * 0.9, phase = r() * Math.PI * 2, sway = 0.08 + r() * 0.08, lean = (r() - 0.5) * 0.12, spread = 0.9 + r() * 0.5;
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
    const px = (x + 0.5) / W - 0.5, up = 1 - (y + 0.5) / H;   // up: 0 at the foot (the image's bottom), 1 at the top
    // the curling centre line, and the wisp's half width there (both in sprite widths)
    const centre = lean * up + sway * up * Math.sin(phase + up * turns * Math.PI * 2) + 0.12 * up * (fbm(up * 3, 4.3, seed + 3, 3) - 0.5);
    const half = (0.09 + 0.28 * Math.pow(up, 0.8)) * spread;
    const across = (px - centre) / half;
    const body = Math.exp(-across * across * 1.6) * smooth(0, 0.22, up) * (1 - smooth(0.45, 1, up));
    // strands along the rise, twisted by a sideways warp
    const warp = 1.4 * (fbm(px * 3 + 7, up * 2.5, seed + 5) - 0.5);
    const strands = Math.pow(ridged((across + warp) * 1.7 + 3, up * 2.2 - k, seed), 2.4);
    const haze = fbm(px * 6 + 1, up * 4, seed + 9);
    const thin = 1 - 0.55 * up;   // frays as it rises
    const edge = smooth(0, 0.1, Math.min(px + 0.5, 0.5 - px)) * smooth(0, 0.04, Math.min(up, 1 - up));
    const a = Math.min(0.7, body * (0.25 * haze + 0.85 * strands) * thin * 1.25) * edge;
    const o = ((oy + y) * SW + ox + x) * 4, shade = Math.round(244 + 11 * hash(x, y, seed + 202));
    sheet[o] = sheet[o + 1] = sheet[o + 2] = shade;
    sheet[o + 3] = Math.round(a * 255);
  }
}
const buf = png(sheet, SW, SH);
if (process.argv.includes('--check')) {
  const same = fs.existsSync(FILE) && fs.readFileSync(FILE).equals(buf);
  console.log(same ? 'steam.png up to date' : 'steam.png differs');
  process.exit(same ? 0 : 1);
}
fs.writeFileSync(FILE, buf);
let cover = 0, sum = 0;
for (let i = 3; i < sheet.length; i += 4) { if (sheet[i] > 8) cover++; sum += sheet[i]; }
console.log(`steam.png ${SW}x${SH}: ${(100 * cover / (sheet.length / 4)).toFixed(1)}% covered, mean alpha ${(sum / (sheet.length / 4) / 255).toFixed(3)}`);
