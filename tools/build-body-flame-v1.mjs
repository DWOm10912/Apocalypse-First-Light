// Burning-body flames (client/EntityFlames, docs/gameplay/fuel_fire_v1.md "着火的生物"). V3 (2026-10-20): re-rendered from
// tools/blender/fuel_flame_body_v2.blend (scene AflBodyFire: the fuel fire's Mantaflow domain made 1.2 x 1.0 x 3.0 m, its
// fuel coming off the surface of a 1.76 m body of boxes through a patchy clouds texture scale 0.18; baked at res 160,
// vorticity 1.0, flame vorticity 3.0, burning rate 0.9, noise strength 0.7) with the fuel flame's shading (blackbody
// 900..2100 K from "flame", strength ((flame - 0.2) / 0.8)^2.5 x 7, Standard view); Cycles, orthographic, frames 60..119 at
// 256 x 640, crossfaded into a 48-frame loop by tools/blender-loop-crossfade.mjs 60 48 12 (tools/blender/fuel_flame_body_v2_loop).
// The render's own colour is kept (unpremultiplied), alpha follows its brightness; the bottom rows fade (no hard line at the
// feet). V1 (2026-10-05, 64 x 160 frames) glowed milky everywhere and was recoloured from brightness alone.
// Output: textures/effect/body_flame.png (768 x 1440: 8 x 6 frames of 96 x 240, drawn additively), _s emissive (254) where lit.
//   node tools/build-body-flame-v1.mjs [loopDir] [--check]
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {png, readPng} from './cube-slab-mesh-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const args = process.argv.slice(2).filter(a => !a.startsWith('--'));
const SRC = args[0] || path.join(ROOT, 'tools/blender/fuel_flame_body_v2_loop');
const OUT = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight/textures/effect');
const FRAMES = 48, COLS = 8, ROWS = 6, FW = 96, FH = 240, W = FW * COLS, H = FH * ROWS, FADE_ROWS = 8;
const smooth = (a, b, x) => { const t = Math.min(1, Math.max(0, (x - a) / (b - a))); return t * t * (3 - 2 * t); };

const base = Buffer.alloc(W * H * 4), spec = Buffer.alloc(W * H * 4);
for (let i = 0; i < FRAMES; i++) {
  const f = readPng(fs.readFileSync(path.join(SRC, `l${String(i).padStart(2, '0')}.png`)));
  const ox = (i % COLS) * FW, oy = Math.floor(i / COLS) * FH;
  for (let y = 0; y < FH; y++) for (let x = 0; x < FW; x++) {
    let r = 0, g = 0, b = 0, n = 0;   // box filter over the source pixels this texel covers
    for (let sy = Math.floor(y * f.h / FH); sy < Math.ceil((y + 1) * f.h / FH); sy++)
      for (let sx = Math.floor(x * f.w / FW); sx < Math.ceil((x + 1) * f.w / FW); sx++) {
        const p = (sy * f.w + sx) * f.bpp; r += f.px[p]; g += f.px[p + 1]; b += f.px[p + 2]; n++;
      }
    r /= n * 255; g /= n * 255; b /= n * 255;
    const m = Math.max(r, g, b), a = Math.min(1, m * 1.15) * smooth(0, FADE_ROWS, FH - 1 - y), s = m > 1e-3 ? 1 / m : 0;
    const o = ((oy + y) * W + ox + x) * 4;
    base[o] = Math.round(Math.min(1, r * s) * 255); base[o + 1] = Math.round(Math.min(1, g * s) * 255); base[o + 2] = Math.round(Math.min(1, b * s) * 255);
    base[o + 3] = Math.round(a * 255);
    spec[o] = 0; spec[o + 1] = 0; spec[o + 2] = 0; spec[o + 3] = a > 0.04 ? 254 : 255;
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
