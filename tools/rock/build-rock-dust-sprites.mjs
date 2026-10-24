// Pick strike dust sheet (client/FireFx#rockDust; docs/gameplay/rock_strike_fx_v1.md 1). User 2026-10-24: the fire smoke's
// billowing puffs read as smoke (and came out black); dust off a struck face is a thin, pale, powdery haze that drops out
// in a second or two, the grains in it seen as specks (the fine fraction is not seen at all).
//   textures/effect/rock_dust (512 x 256: 4 x 2 sprites of 128 x 128): white with the haze in alpha (tinted to the rock's
//   powder when drawn, lit by the world). Each: an irregular, stretched cloud (a radius pushed in and out by noise), its
//   density a warped fractal noise raised to a power (wisps and gaps, no round billows), a pixel grain over it (powder,
//   not vapour), and 30-60 single or double pixel specks of grit; fully clear at the sprite's edge.
//   node tools/rock/build-rock-dust-sprites.mjs [--check]
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {png} from '../cube-slab-mesh-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const FILE = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight/textures/effect/rock_dust.png');
const S = 128, N = 8;

function rng(seed) { let s = seed >>> 0; return () => { s = Math.imul(s ^ (s >>> 15), 2246822519) + 0x9e3779b9 >>> 0; s ^= s >>> 13; return (s >>> 0) / 4294967296; }; }
function hash(x, y, seed) { let h = Math.imul(x, 374761393) ^ Math.imul(y, 668265263) ^ Math.imul(seed, 1442695041); h = Math.imul(h ^ (h >>> 13), 1274126177); return ((h ^ (h >>> 16)) >>> 0) / 4294967296; }
function value(x, y, seed) {
  const xi = Math.floor(x), yi = Math.floor(y), fx = x - xi, fy = y - yi, u = fx * fx * (3 - 2 * fx), v = fy * fy * (3 - 2 * fy);
  const a = hash(xi, yi, seed), b = hash(xi + 1, yi, seed), c = hash(xi, yi + 1, seed), d = hash(xi + 1, yi + 1, seed);
  return a + (b - a) * u + (c - a) * v + (a - b - c + d) * u * v;
}
function fbm(x, y, seed, oct = 5) { let s = 0, w = 0.5, f = 1, n = 0; for (let o = 0; o < oct; o++) { s += w * value(x * f, y * f, seed + o * 17); n += w; w *= 0.5; f *= 2.03; } return s / n; }
const smooth = (a, b, x) => { const t = Math.min(1, Math.max(0, (x - a) / (b - a))); return t * t * (3 - 2 * t); };

const sheet = Buffer.alloc(S * 4 * S * 2 * 4);
for (let k = 0; k < N; k++) {
  const r = rng(9100 + k * 31), seed = 500 + k * 7;
  const turn = r() * Math.PI, stretch = 1 + r() * 0.6, ox = (k % 4) * S, oy = Math.floor(k / 4) * S;
  const alpha = new Float64Array(S * S);
  for (let y = 0; y < S; y++) for (let x = 0; x < S; x++) {
    const px = (x + 0.5) / S - 0.5, py = (y + 0.5) / S - 0.5;
    const cx = (px * Math.cos(turn) + py * Math.sin(turn)) / stretch, cy = -px * Math.sin(turn) + py * Math.cos(turn);
    const ang = Math.atan2(cy, cx), rad = Math.hypot(cx, cy);
    const reach = 0.24 + 0.26 * fbm(Math.cos(ang) * 2.2 + 5, Math.sin(ang) * 2.2 + 5, seed + 3, 3);
    const mask = 1 - smooth(reach * 0.35, reach, rad);
    const wx = px * 5 + 2.2 * fbm(px * 3 + 11, py * 3, seed + 5), wy = py * 5 + 2.2 * fbm(px * 3, py * 3 + 7, seed + 9);
    const dens = Math.pow(fbm(wx, wy, seed), 2.8) * 2.4;
    const grain = 0.7 + 0.6 * hash(x + ox, y + oy, seed + 101);
    const edge = smooth(0, 0.08, Math.min(px + 0.5, 0.5 - px, py + 0.5, 0.5 - py));
    alpha[y * S + x] = Math.min(0.62, mask * dens * grain) * edge;
  }
  const specks = 30 + Math.floor(r() * 31);
  for (let i = 0; i < specks; i++) {   // grit: inside the cloud, denser toward its middle
    const a = r() * Math.PI * 2, d = Math.sqrt(r()) * 0.3, sx = Math.round(S / 2 + Math.cos(a) * d * S * 1.2), sy = Math.round(S / 2 + Math.sin(a) * d * S);
    const big = r() < 0.25, v = 0.55 + r() * 0.4;
    for (let dy = 0; dy <= (big ? 1 : 0); dy++) for (let dx = 0; dx <= (big ? 1 : 0); dx++) {
      const X = sx + dx, Y = sy + dy;
      if (X > 4 && Y > 4 && X < S - 4 && Y < S - 4) alpha[Y * S + X] = Math.max(alpha[Y * S + X], v);
    }
  }
  for (let y = 0; y < S; y++) for (let x = 0; x < S; x++) {
    const o = ((oy + y) * S * 4 + ox + x) * 4, shade = Math.round(236 + 19 * hash(x, y, seed + 202));
    sheet[o] = sheet[o + 1] = sheet[o + 2] = shade;
    sheet[o + 3] = Math.round(alpha[y * S + x] * 255);
  }
}
const buf = png(sheet, S * 4, S * 2);
if (process.argv.includes('--check')) {
  const same = fs.existsSync(FILE) && fs.readFileSync(FILE).equals(buf);
  console.log(same ? 'rock_dust.png up to date' : 'rock_dust.png differs');
  process.exit(same ? 0 : 1);
}
fs.writeFileSync(FILE, buf);
let cover = 0, sum = 0;
for (let i = 3; i < sheet.length; i += 4) { if (sheet[i] > 8) cover++; sum += sheet[i]; }
console.log(`rock_dust.png ${S * 4}x${S * 2}: ${(100 * cover / (sheet.length / 4)).toFixed(1)}% covered, mean alpha ${(sum / (sheet.length / 4) / 255).toFixed(3)}`);
