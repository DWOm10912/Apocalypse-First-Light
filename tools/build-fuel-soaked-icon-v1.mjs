// Fuel-soaked effect icons (textures/mob_effect/{gasoline,diesel}_soaked.png, 18 x 18, 2026-10-05): a drop of the fuel's
// colour (gasoline pale yellow, diesel amber) with a dark outline and a highlight, in the style of the vanilla effect
// icons. The effects: fluid/FuelSoakedEffect.
//   node tools/build-fuel-soaked-icon-v1.mjs
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {png} from './cube-slab-mesh-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const N = 18;
// inside the drop: a circle (centre 9, 11.5, r 5) with a point up to (9, 2)
const inside = (x, y) => { const dx = x - 9, dy = y - 11.5; if (dx * dx + dy * dy <= 25) return true;
  if (y < 11.5 && y >= 2) { const half = 5 * (y - 2) / 9.5 * 0.82; return Math.abs(dx) <= half; } return false; };
const at = (x, y) => inside(x + 0.5, y + 0.5);
for (const [name, fill, rim] of [['gasoline', [236, 214, 140], [96, 74, 24]], ['diesel', [214, 150, 52], [74, 46, 12]]]) {
const buf = Buffer.alloc(N * N * 4);
for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
  const k = (y * N + x) * 4;
  if (at(x, y)) {
    const edge = !at(x - 1, y) || !at(x + 1, y) || !at(x, y - 1) || !at(x, y + 1);
    const shade = y > 13 ? 0.82 : 1, hi = (x === 6 || x === 7) && y >= 9 && y <= 11;
    const c = edge ? rim : hi ? [252, 240, 200] : fill.map(v => Math.round(v * shade));
    buf[k] = c[0]; buf[k + 1] = c[1]; buf[k + 2] = c[2]; buf[k + 3] = 255;
  }
}
const out = path.join(ROOT, `src/main/resources/assets/apocalypse_firstlight/textures/mob_effect/${name}_soaked.png`);
fs.writeFileSync(out, png(buf, N, N));
console.log('wrote ' + path.relative(ROOT, out));
}
