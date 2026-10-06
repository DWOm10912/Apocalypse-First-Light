// Fuel flame textures (docs/gameplay/fuel_fire_v1.md, 2026-10-05), from a Blender fire simulation rendered as a 48-frame
// loop (tools/blender/fuel_flame_v1.blend: Mantaflow gas domain 1.2 x 1.2 x 2.4 m, res 112 + noise x2, a 0.84 x 0.67 m
// fuel pool with a moving clouds texture on its fuel; "flame" -> colour ramp -> emission, Cycles, orthographic side view,
// frames 60-107 with the first 12 crossfaded from 108-119; 256 x 352 each, black background).
//   node tools/build-fuel-flame-v1.mjs tools/blender/fuel_flame_v1_loop [--check]      (l00.png .. l47.png, the kept render)
// Outputs:
// - textures/effect/fuel_flame.png (512 x 528: 8 x 6 frames of 64 x 88): the burning fuel flames (client/FuelFlames),
//   drawn additively; colour unpremultiplied, alpha from brightness, the bottom rows faded (no hard line on the ground).
//   _s: LabPBR, emissive (alpha 254) wherever the flame is.
// - assets/minecraft/textures/block/fire_0.png, fire_1.png (64 x 3072: 48 square frames, .mcmeta frametime 1): vanilla
//   fire replaced by the same flames (one fire style in the game, user 2026-10-05). Vanilla fire and burning entities draw
//   it cut out: alpha is hard (bright enough or nothing), colour unpremultiplied so no dark fringe. fire_1 runs half a
//   loop later. _s emissive.
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {png, readPng} from './cube-slab-mesh-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const SRC = process.argv[2];
if (!SRC || !fs.existsSync(SRC)) throw new Error('usage: node tools/build-fuel-flame-v1.mjs <loopFramesDir> [--check]');
const FRAMES = 48, FW = 64, FH = 88, COLS = 8, ROWS = 6, SQ = 64;
const A = path.join(ROOT, 'src/main/resources/assets');

const frames = [];
for (let i = 0; i < FRAMES; i++) frames.push(readPng(fs.readFileSync(path.join(SRC, `l${String(i).padStart(2, '0')}.png`))));
const W = frames[0].w, H = frames[0].h;

// box-filtered sample of a frame region (sx0..sx1, sy0..sy1 in source px) as linear-ish 0..1 rgb
function area(f, sx0, sy0, sx1, sy1) {
  let r = 0, g = 0, b = 0, n = 0;
  for (let y = Math.max(0, Math.floor(sy0)); y < Math.min(H, Math.ceil(sy1)); y++)
    for (let x = Math.max(0, Math.floor(sx0)); x < Math.min(W, Math.ceil(sx1)); x++) {
      const k = (y * W + x) * f.bpp; r += f.px[k]; g += f.px[k + 1]; b += f.px[k + 2]; n++;
    }
  return n ? [r / n / 255, g / n / 255, b / n / 255] : [0, 0, 0];
}
const smooth = (a, b, x) => { const t = Math.min(1, Math.max(0, (x - a) / (b - a))); return t * t * (3 - 2 * t); };

// ---- the sheet: soft alpha from brightness ----
const SW = FW * COLS, SH = FH * ROWS, sheet = Buffer.alloc(SW * SH * 4), sheetS = Buffer.alloc(SW * SH * 4);
for (let i = 0; i < FRAMES; i++) {
  const f = frames[i], ox = (i % COLS) * FW, oy = Math.floor(i / COLS) * FH;
  for (let y = 0; y < FH; y++) for (let x = 0; x < FW; x++) {
    const [r, g, b] = area(f, x * W / FW, y * H / FH, (x + 1) * W / FW, (y + 1) * H / FH);
    const m = Math.max(r, g, b), alpha = Math.min(1, m * 1.15) * smooth(0, 5, FH - 1 - y);   // fade the last rows
    const k = ((oy + y) * SW + ox + x) * 4, s = m > 1e-3 ? 1 / m : 0;
    sheet[k] = Math.round(Math.min(1, r * s) * 255); sheet[k + 1] = Math.round(Math.min(1, g * s) * 255); sheet[k + 2] = Math.round(Math.min(1, b * s) * 255);
    sheet[k + 3] = Math.round(alpha * 255);
    sheetS[k] = 0; sheetS[k + 1] = 0; sheetS[k + 2] = 0; sheetS[k + 3] = alpha > 0.04 ? 254 : 255;
  }
}

// ---- vanilla fire: square frames, hard alpha ----
function fireStrip(offset) {
  const strip = Buffer.alloc(SQ * SQ * FRAMES * 4), stripS = Buffer.alloc(SQ * SQ * FRAMES * 4);
  for (let i = 0; i < FRAMES; i++) {
    const f = frames[(i + offset) % FRAMES], side = H * 0.8;   // a square 0.8 of the frame height, from the bottom, flame centred
    for (let y = 0; y < SQ; y++) for (let x = 0; x < SQ; x++) {
      const sx0 = (W - side) / 2 + x * side / SQ, sy0 = H - side + y * side / SQ;
      const [r, g, b] = area(f, sx0, sy0, sx0 + side / SQ, sy0 + side / SQ);
      const m = Math.max(r, g, b), on = m > 0.22, k = ((i * SQ + y) * SQ + x) * 4, s = m > 1e-3 ? 1 / m : 0;
      strip[k] = Math.round(Math.min(1, r * s) * 255); strip[k + 1] = Math.round(Math.min(1, g * s * 0.95) * 255); strip[k + 2] = Math.round(Math.min(1, b * s * 0.9) * 255);
      strip[k + 3] = on ? 255 : 0;
      stripS[k] = 0; stripS[k + 1] = 0; stripS[k + 2] = 0; stripS[k + 3] = on ? 254 : 255;
    }
  }
  return [png(strip, SQ, SQ * FRAMES), png(stripS, SQ, SQ * FRAMES)];
}
const [f0, f0s] = fireStrip(0), [f1, f1s] = fireStrip(FRAMES / 2);
const mcmeta = JSON.stringify({animation: {frametime: 1}}, null, 2) + '\n';
const outputs = [
  [path.join(A, 'apocalypse_firstlight/textures/effect/fuel_flame.png'), png(sheet, SW, SH)],
  [path.join(A, 'apocalypse_firstlight/textures/effect/fuel_flame_s.png'), png(sheetS, SW, SH)],
  [path.join(A, 'minecraft/textures/block/fire_0.png'), f0], [path.join(A, 'minecraft/textures/block/fire_0_s.png'), f0s],
  [path.join(A, 'minecraft/textures/block/fire_0.png.mcmeta'), mcmeta],
  [path.join(A, 'minecraft/textures/block/fire_1.png'), f1], [path.join(A, 'minecraft/textures/block/fire_1_s.png'), f1s],
  [path.join(A, 'minecraft/textures/block/fire_1.png.mcmeta'), mcmeta],
];
if (process.argv.includes('--check')) {
  for (const [p, d] of outputs) if (!fs.existsSync(p) || !fs.readFileSync(p).equals(Buffer.isBuffer(d) ? d : Buffer.from(d))) throw new Error('stale ' + path.relative(ROOT, p));
  console.log('CHECK OK');
} else {
  for (const [p, d] of outputs) { fs.mkdirSync(path.dirname(p), {recursive: true}); fs.writeFileSync(p, d); }
  console.log('wrote ' + outputs.length + ' files');
}
