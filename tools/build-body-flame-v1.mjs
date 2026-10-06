// Burning-body flames (client/EntityFlames, docs/gameplay/fuel_fire_v1.md "着火的生物", 2026-10-05): a fire simulation of a
// burning person rendered in Blender (tools/blender/fuel_flame_body_v1.blend, scene AflBodyFire: the fuel fire's Mantaflow
// domain made 1.2 x 1.0 x 3.0 m, its fuel coming off the surface of a 1.76 m body of boxes (legs and torso, arms, head)
// through a patchy clouds texture; Cycles, orthographic, frames 60..107 with the first 12 crossfaded from 108..119, kept at
// 128 x 320 in tools/blender/fuel_flame_body_v1_loop). Its own colours came out milky (the whole body glows, layers add up),
// so only the render's brightness is kept and coloured here: deep red-orange where faint, through orange and yellow to a
// pale yellow core. Alpha follows the brightness; the bottom rows fade (no hard line at the feet).
// Output: textures/effect/body_flame.png (512 x 960: 8 x 6 frames of 64 x 160, drawn additively), _s emissive (254) where lit.
//   node tools/build-body-flame-v1.mjs [loopDir] [--check]
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {png, readPng} from './cube-slab-mesh-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const args = process.argv.slice(2).filter(a => !a.startsWith('--'));
const SRC = args[0] || path.join(ROOT, 'tools/blender/fuel_flame_body_v1_loop');
const OUT = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight/textures/effect');
const FRAMES = 48, COLS = 8, ROWS = 6, FW = 64, FH = 160, W = FW * COLS, H = FH * ROWS, FADE_ROWS = 6;
// brightness -> colour (0..255) at these stops
const RAMP = [[0.0, [110, 18, 0]], [0.3, [225, 70, 8]], [0.55, [255, 135, 25]], [0.8, [255, 205, 95]], [1.0, [255, 240, 190]]];
function ramp(t) {
  for (let i = 1; i < RAMP.length; i++) {
    if (t <= RAMP[i][0]) {
      const [t0, c0] = RAMP[i - 1], [t1, c1] = RAMP[i], f = (t - t0) / (t1 - t0);
      return c0.map((c, k) => c + (c1[k] - c) * f);
    }
  }
  return RAMP[RAMP.length - 1][1];
}

const base = Buffer.alloc(W * H * 4), spec = Buffer.alloc(W * H * 4);
for (let i = 0; i < FRAMES; i++) {
  const f = readPng(fs.readFileSync(path.join(SRC, `l${String(i).padStart(2, '0')}.png`)));
  const kx = f.w / FW, ky = f.h / FH, ox = (i % COLS) * FW, oy = Math.floor(i / COLS) * FH;
  for (let y = 0; y < FH; y++) for (let x = 0; x < FW; x++) {
    let l = 0;   // box-filtered brightness (max channel)
    for (let sy = 0; sy < ky; sy++) for (let sx = 0; sx < kx; sx++) {
      const p = ((y * ky + sy) * f.w + x * kx + sx) * f.bpp;
      l += Math.max(f.px[p], f.px[p + 1], f.px[p + 2]) / 255;
    }
    l /= kx * ky;
    const t = Math.min(1, Math.max(0, (l - 0.05) / 0.8)) ** 1.1;
    const fade = Math.min(1, (FH - 1 - y) / FADE_ROWS);
    const a = Math.min(1, t * 1.25) * fade, c = ramp(t), o = ((oy + y) * W + ox + x) * 4;
    base[o] = Math.round(c[0]); base[o + 1] = Math.round(c[1]); base[o + 2] = Math.round(c[2]); base[o + 3] = Math.round(a * 255);
    spec[o] = 0; spec[o + 1] = 0; spec[o + 2] = 0; spec[o + 3] = a > 0.02 ? 254 : 255;
  }
}
const files = {'body_flame.png': png(base, W, H), 'body_flame_s.png': png(spec, W, H)};
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
