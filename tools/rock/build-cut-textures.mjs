// U3a-3 round 2 (docs/worldgen/underground_u3_rocks_ores_design.md 13): the freshly broken sprite of every natural rock
// block's texture, textures/block/rock/cut/<name>.png: a face the pick made shows unweathered rock, lighter and duller
// than the face around it (fine dust, no patina). Each pixel from the block's own sprite, so it tiles the same way; its
// _n / _s companions are copied (LabPBR: the relief and the gloss stay the rock's), except that a fresh break of a crystalline
// rock shows its mineral grains (Rock PBR R1, docs/worldgen/rock_pbr_v1.md 5): cleavage faces of feldspar, quartz and mica in
// the granites, calcite spar in the limestones, quartz grains in the sandstone, mica along the schist's foliation; each a
// texel or 2 x 2, smooth (LabPBR 0.75-0.96), dielectric (F0 11-14), its normal leaned at random (rock-pbr.mjs glints), so
// they flash one by one as the light or the eye moves. The ores' companions stay copies (their minerals are in their own).
// node tools/rock/build-cut-textures.mjs [--check]
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {readPng, png} from '../cube-slab-mesh-lib.mjs';
import {glints} from './rock-pbr.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const ASSETS = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight');
// the natural rocks' models and the ore rocks' (U3b)
const MODELS = [path.join(ASSETS, 'models/block/rock'), path.join(ASSETS, 'models/block/ore')];
const OUT = path.join(ASSETS, 'textures/block/rock/cut');
const check = process.argv.includes('--check');
/** how far toward grey (saturation kept), toward the mean (contrast kept), toward white (lift) */
const SAT = 0.75, CONTRAST = 0.82, LIFT = 0.15;

/**
 * The grains of a fresh break by sprite name (rock-pbr.mjs glints: share of texels, smoothness bytes, F0 byte, lean in
 * degrees, share of 2 x 2 grains). Granite (stone_0..3, granite_fresh: gneiss): feldspar cleavage, glassy quartz, mica
 * flakes, about 9% of the face; limestone (also the dolomite's and the rock salt's sprite): calcite spar 3%; sandstone:
 * quartz grains 2%; schist (shale_worn): mica 8%, its flakes leaning one way (the foliation).
 */
const GRANITE = [
  {share: 0.05, smooth: [205, 225], f0: 11, tilt: 30, big: 0.4},
  {share: 0.025, smooth: [215, 235], f0: 11, tilt: 35, big: 0},
  {share: 0.015, smooth: [232, 245], f0: 14, tilt: 20, big: 0.3},
];
const SPAR = [{share: 0.03, smooth: [200, 225], f0: 11, tilt: 30, big: 0.3}];
const GLINTS = {
  stone_0: [GRANITE], stone_1: [GRANITE], stone_2: [GRANITE], stone_3: [GRANITE], granite_fresh: [GRANITE],
  limestone_fresh: [SPAR], limestone: [SPAR],
  sandstone_fresh: [[{share: 0.02, smooth: [190, 215], f0: 11, tilt: 35, big: 0}]],
  red_sandstone_fresh: [[{share: 0.02, smooth: [190, 215], f0: 11, tilt: 35, big: 0}]],   // natural ground N1: the badlands' red beds
  shale_worn: [[{share: 0.08, smooth: [225, 242], f0: 13, tilt: 15, big: 0.5}], 0.6],
};

/** The _s and _n of a fresh break with its grains (both PNG buffers), or null where the rock has none. */
function grained(name, sFile, nFile) {
  const g = GLINTS[name];
  if (!g || !fs.existsSync(sFile) || !fs.existsSync(nFile)) return null;
  const si = readPng(fs.readFileSync(sFile)), ni = readPng(fs.readFileSync(nFile)), res = si.w;
  const rgba = im => { const bpp = im.px.length / (im.w * im.h), b = Buffer.alloc(im.w * im.h * 4, 255); for (let i = 0; i < im.w * im.h; i++) for (let c = 0; c < bpp; c++) b[i * 4 + c] = im.px[i * bpp + c]; return b; };
  const s = rgba(si), n = rgba(ni);
  // the seed by name: the four stone tiles and the granite each their own grains
  let seed = 0;
  for (const ch of name) seed = (seed * 31 + ch.charCodeAt(0)) | 0;
  glints(s, n, res, g[0], seed, g[1] ?? null);
  return {s: png(s, res, res), n: png(n, res, res)};
}

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
  const comp = sfx => path.join(ASSETS, 'textures', rel + sfx + '.png'), gr = grained(name, comp('_s'), comp('_n'));
  for (const sfx of ['_n', '_s']) {
    if (fs.existsSync(comp(sfx))) files.push([path.join(OUT, name + sfx + '.png'), () => (gr ? gr[sfx.slice(1)] : fs.readFileSync(comp(sfx)))]);
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
