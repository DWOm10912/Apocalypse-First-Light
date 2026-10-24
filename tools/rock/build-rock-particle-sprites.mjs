// Pick strike chip sprites (client/RockStrikeParticles; docs/gameplay/rock_strike_fx_v1.md; its dust and sparks are
// client/FireFx's). White / grey RGB with the shape in alpha (the particle tints by vertex colour).
//   rock_chip_{0..5}: 8x8 pixel shards (FX are pixel art, 16 px grid like the fire embers), an irregular polygon of 4-6
//     corners, hard edges, two facets (a lit one and a shaded one split by a line through it), so a tumbling chip
//     flickers between them like a real flake.
//   node tools/rock/build-rock-particle-sprites.mjs [--check]
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {png} from '../cube-slab-mesh-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const OUT = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight/textures/particle');
const check = process.argv.includes('--check');
const N = 8, CHIPS = 6;

function rng(seed) {
  let s = seed >>> 0;
  return () => (s = (Math.imul(s, 1664525) + 1013904223) >>> 0) / 4294967296;
}

function inside(poly, x, y) {
  let c = false;
  for (let i = 0, j = poly.length - 1; i < poly.length; j = i++) {
    const [xi, yi] = poly[i], [xj, yj] = poly[j];
    if ((yi > y) !== (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) c = !c;
  }
  return c;
}

function chip(seed) {
  const r = rng(seed * 7919 + 13);
  const corners = 4 + Math.floor(r() * 3), out = Buffer.alloc(N * N * 4);
  const cx = 3.5 + (r() - 0.5), cy = 3.5 + (r() - 0.5), poly = [];
  for (let k = 0; k < corners; k++) {
    const a = (k + r() * 0.6) / corners * Math.PI * 2, rad = 2.2 + r() * 1.7;
    poly.push([cx + Math.cos(a) * rad * (1 + (seed % 2) * 0.25), cy + Math.sin(a) * rad]);
  }
  // the facet split: a line through the centre at a random angle
  const a = r() * Math.PI, nx = Math.cos(a), ny = Math.sin(a);
  let filled = 0;
  for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
    const i = (y * N + x) * 4;
    if (!inside(poly, x + 0.5, y + 0.5)) continue;
    const lit = (x + 0.5 - cx) * nx + (y + 0.5 - cy) * ny < 0;
    const v = Math.round((lit ? 235 : 178) + (r() - 0.5) * 18);
    out[i] = out[i + 1] = out[i + 2] = Math.max(0, Math.min(255, v));
    out[i + 3] = 255;
    filled++;
  }
  if (filled < 9) throw new Error('chip ' + seed + ' too small');
  return png(out, N, N);
}

const files = [];
for (let k = 0; k < CHIPS; k++) files.push(['rock_chip_' + k + '.png', chip(k)]);
let bad = 0;
for (const [name, data] of files) {
  const dst = path.join(OUT, name);
  if (check) {
    if (!fs.existsSync(dst) || !fs.readFileSync(dst).equals(data)) { console.error('stale ' + name); bad++; }
  } else fs.writeFileSync(dst, data);
}
console.log((check ? 'checked ' : 'wrote ') + files.length + ' sprites' + (bad ? ', ' + bad + ' stale' : ''));
process.exit(bad ? 1 : 0);
