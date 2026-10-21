// Wood Photo V1 (docs/models/wood_texture_rework_v1_plan.md 4.3): bilinear samplers over the CC0 photo wood written by
// tools/build-wood-photo-v1.mjs, for the generators that paint their own layouts over it (cabin-art.mjs, box-stove-art.mjs).
// A sampler takes texel coordinates (x right, y down; 240 = 1 m on the 240 textures), wraps, returns sRGB 0..255.
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {readPng} from '../cube-slab-mesh-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const TEX = 'src/main/resources/assets/apocalypse_firstlight/textures/block/';

/** A sampler over a PNG (path from the repository root); .mean is its mean luminance (0..255). */
export function photo(rel) {
  const {w, h, bpp, px} = readPng(fs.readFileSync(path.join(ROOT, rel)));
  const at = (x, y, k) => px[(y * w + x) * bpp + k];
  let mean = 0;
  for (let i = 0; i < w * h; i++) mean += (0.2126 * px[i * bpp] + 0.7152 * px[i * bpp + 1] + 0.0722 * px[i * bpp + 2]) / (w * h);
  const f = (x, y) => {
    const u = ((x - 0.5) % w + w) % w, v = ((y - 0.5) % h + h) % h;
    const x0 = Math.floor(u), y0 = Math.floor(v), fx = u - x0, fy = v - y0, x1 = (x0 + 1) % w, y1 = (y0 + 1) % h;
    return [0, 1, 2].map(k => (at(x0, y0, k) * (1 - fx) + at(x1, y0, k) * fx) * (1 - fy) + (at(x0, y1, k) * (1 - fx) + at(x1, y1, k) * fx) * fy);
  };
  f.mean = mean;
  return f;
}
/** A game texture under textures/block/ (e.g. 'wood/white_oak_timber_side'). */
export const texture = name => photo(TEX + name + '.png');
/** A base image in tools/wood-photo/ (e.g. 'firewood_bark_240'). */
export const base = name => photo('tools/wood-photo/' + name + '.png');
/** Luminance (sRGB 0..255). */
export const lum = c => 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2];
