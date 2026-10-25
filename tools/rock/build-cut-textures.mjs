// U3a-3 round 2 (docs/worldgen/underground_u3_rocks_ores_design.md 13): the freshly broken sprite of every natural rock
// block's texture, textures/block/rock/cut/<name>.png: a face the pick made shows unweathered rock, lighter and duller
// than the face around it (fine dust, no patina). Each pixel from the block's own sprite, so it tiles the same way; its
// _n / _s companions are copied (LabPBR: the relief and the gloss stay the rock's).
// node tools/rock/build-cut-textures.mjs [--check]
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {readPng, png} from '../cube-slab-mesh-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const ASSETS = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight');
// the natural rocks' models and the ore rocks' (U3b)
const MODELS = [path.join(ASSETS, 'models/block/rock'), path.join(ASSETS, 'models/block/ore')];
const OUT = path.join(ASSETS, 'textures/block/rock/cut');
const check = process.argv.includes('--check');
/** how far toward grey (saturation kept), toward the mean (contrast kept), toward white (lift) */
const SAT = 0.75, CONTRAST = 0.82, LIFT = 0.15;

const sprites = new Set();
for (const dir of MODELS) for (const f of fs.readdirSync(dir)) {
  if (!f.endsWith('.json')) continue;
  const all = JSON.parse(fs.readFileSync(path.join(dir, f), 'utf8')).textures?.all;
  if (all) sprites.add(all);
}

function cut(file) {
  const img = readPng(fs.readFileSync(file));
  const {w, h, px} = img, bpp = px.length / (w * h);
  let mean = 0;
  for (let i = 0; i < w * h; i++) mean += 0.299 * px[i * bpp] + 0.587 * px[i * bpp + 1] + 0.114 * px[i * bpp + 2];
  mean /= w * h;
  const out = Buffer.alloc(w * h * 4);
  for (let i = 0; i < w * h; i++) {
    const r = px[i * bpp], g = px[i * bpp + 1], b = px[i * bpp + 2];
    const lum = 0.299 * r + 0.587 * g + 0.114 * b;
    for (let c = 0; c < 3; c++) {
      let v = lum + ([r, g, b][c] - lum) * SAT;
      v = mean + (v - mean) * CONTRAST;
      v = v + (255 - v) * LIFT;
      out[i * 4 + c] = Math.max(0, Math.min(255, Math.round(v)));
    }
    out[i * 4 + 3] = bpp === 4 ? px[i * bpp + 3] : 255;
  }
  return png(out, w, h);
}

let n = 0, bad = 0;
if (!check) fs.mkdirSync(OUT, {recursive: true});
for (const s of [...sprites].sort()) {
  const rel = s.split(':')[1];
  const src = path.join(ASSETS, 'textures', rel + '.png');
  const name = path.basename(rel);
  if (!fs.existsSync(src)) { console.error('missing ' + src); bad++; continue; }
  const files = [[path.join(OUT, name + '.png'), () => cut(src)]];
  for (const sfx of ['_n', '_s']) {
    const comp = path.join(ASSETS, 'textures', rel + sfx + '.png');
    if (fs.existsSync(comp)) files.push([path.join(OUT, name + sfx + '.png'), () => fs.readFileSync(comp)]);
  }
  for (const [dst, make] of files) {
    const data = make();
    if (check) {
      if (!fs.existsSync(dst) || !fs.readFileSync(dst).equals(data)) { console.error('stale ' + path.relative(ROOT, dst)); bad++; }
    } else fs.writeFileSync(dst, data);
    n++;
  }
}
console.log((check ? 'checked ' : 'wrote ') + n + ' files for ' + sprites.size + ' sprites' + (bad ? ', ' + bad + ' problems' : ''));
process.exit(bad ? 1 : 0);
