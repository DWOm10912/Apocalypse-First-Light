// Mesh Trees P0-A (docs/rendering/mesh_trees_p0a.md) .. P4: the trees' textures, written to
// src/main/resources/assets/apocalypse_firstlight/textures/block/tree/ (and ../../item/tree/ for the icons); --check
// compares instead of writing. Per species (white_oak, white_pine, red_oak, shagbark_hickory, red_maple, eastern_hemlock):
//   <id>_bark           32 x 32 pixel art + LabPBR _n / _s, from bark-art.mjs (2026-10-12, mesh_trees_p4_formal.md W;
//                       16 px before; a 240 px bark was rejected in game the same day)
//   <id>_log_end, <id>_end_grain, <id>_cut   32 x 32 + LabPBR _n / _s from the luminance, felling-art.mjs (P2.1 / P2.2,
//                       the four P3 species with their own palettes and profiles)
//   leaf cards          32 x 32 grey values for the crown's tint: LOD0 cards, _mid / _far denser for LOD1 / LOD2, a 16 x 16
//                       _proxy cube face (what Voxy and maps see); white oak and white pine drawn here (P0-A), the four P3
//                       species in species-art.mjs
//   item icons          16 x 16 pixel art: <id>_branch and the leaf material (felling-art.mjs, species-art.mjs)
// Leaves have no LabPBR maps: the shader pack's own leaf handling stays.
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {png} from '../cube-slab-mesh-lib.mjs';
import * as art from './felling-art.mjs';
import * as sp from './species-art.mjs';
import {barkMaps, M as BARK_PX} from './bark-art.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
// P4 (mesh_trees_p4_formal.md): the formal textures, apocalypse_firstlight:block/tree/... and item/tree/...
const OUT = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight/textures/block/tree');

function rng(seed) {
  let s = BigInt(seed) & 0xFFFFFFFFFFFFFFFFn;
  return () => {
    s = (s + 0x9E3779B97F4A7C15n) & 0xFFFFFFFFFFFFFFFFn;
    let z = s;
    z = ((z ^ (z >> 30n)) * 0xBF58476D1CE4E5B9n) & 0xFFFFFFFFFFFFFFFFn;
    z = ((z ^ (z >> 27n)) * 0x94D049BB133111EBn) & 0xFFFFFFFFFFFFFFFFn;
    z = z ^ (z >> 31n);
    return Number(z >> 11n) / 9007199254740992;
  };
}

function image(W, H) { return {W, H, v: new Float32Array(W * H), a: new Uint8Array(W * H), rgb: null}; }

/** A canvas ({W, H, v, a, rgb}) to PNG; a bark map ({png, W, H}) is a PNG already. */
function toPng(img) {
  if (img.png) return img.png;
  const buf = Buffer.alloc(img.W * img.H * 4);
  for (let i = 0; i < img.W * img.H; i++) {
    if (img.rgb) {
      buf[i * 4] = img.rgb[i * 3]; buf[i * 4 + 1] = img.rgb[i * 3 + 1]; buf[i * 4 + 2] = img.rgb[i * 3 + 2];
    } else {
      const g = Math.max(0, Math.min(255, Math.round(img.v[i] * 255)));
      buf[i * 4] = g; buf[i * 4 + 1] = g; buf[i * 4 + 2] = g;
    }
    buf[i * 4 + 3] = img.a[i] ? 255 : 0;
    if (!img.a[i]) { buf[i * 4] = 0; buf[i * 4 + 1] = 0; buf[i * 4 + 2] = 0; }
  }
  return png(buf, img.W, img.H);
}

// ---- leaf clusters: lobed obovate leaves on a twig, grey values for the foliage tint
function leaf(img, cx, cy, angle, len, wid, value, r) {
  const ca = Math.cos(angle), sa = Math.sin(angle);
  const lobes = 3 + Math.floor(r() * 2);
  for (let y = 0; y < img.H; y++) for (let x = 0; x < img.W; x++) {
    const dx = x + 0.5 - cx, dy = y + 0.5 - cy;
    const t = dx * ca + dy * sa;          // along the leaf, 0 at the base
    const s = -dx * sa + dy * ca;         // across
    if (t < 0 || t > len) continue;
    const u = t / len;
    // obovate (widest past the middle, rounded tip) with rounded lobes as shallow notches
    const half = wid * 0.5 * Math.pow(Math.sin(Math.PI * Math.min(1, 0.1 + 0.9 * u)), 0.6) * (0.7 + 0.3 * u);
    const notch = 0.78 + 0.22 * Math.abs(Math.cos(u * Math.PI * lobes));
    if (Math.abs(s) > half * notch) continue;
    const i = y * img.W + x;
    let v = value + (s < 0 ? 0.04 : -0.04) - 0.05 * (1 - u);   // one half catches more light
    if (Math.abs(s) < 0.5 && u > 0.15 && u < 0.8) v -= 0.07;   // midrib
    img.v[i] = v;
    img.a[i] = 1;
  }
}

function twig(img, x0, y0, x1, y1, value) {
  const n = Math.ceil(Math.hypot(x1 - x0, y1 - y0) * 2);
  for (let k = 0; k <= n; k++) {
    const x = Math.floor(x0 + (x1 - x0) * k / n), y = Math.floor(y0 + (y1 - y0) * k / n);
    if (x < 0 || y < 0 || x >= img.W || y >= img.H) continue;
    const i = y * img.W + x;
    if (!img.a[i]) { img.v[i] = value; img.a[i] = 1; }
  }
}

function cluster(seed, leaves, len, wid, spread, margin) {
  const W = 32, H = 32, r = rng(seed);
  const img = image(W, H);
  // leaf bases scattered over a disc (radius spread), each leaf pointing roughly away from the centre; a short twig
  // shows only between a few of them
  const bases = [];
  for (let k = 0; k < leaves; k++) {
    const a = (k / leaves) * Math.PI * 2 * 1.618 + r() * 0.8;
    const d = spread * Math.sqrt((k + 0.5) / leaves) * (0.75 + 0.25 * r());
    bases.push([16 + Math.cos(a) * d * 0.62, 16 + Math.sin(a) * d * 0.62, a + (r() - 0.5) * 0.9, d]);
  }
  twig(img, 7, 27, 15, 17, 0.3);
  for (let k = 0; k < bases.length; k += 4) twig(img, 15, 17, bases[k][0], bases[k][1], 0.34);
  // inner leaves first (darker), outer and lower ones last (lighter, in front)
  bases.sort((p, q) => p[3] - q[3]);
  for (const [x, y, a, d] of bases) {
    const l = len * (0.85 + 0.3 * r()), w = wid * (0.9 + 0.25 * r());
    leaf(img, x - Math.cos(a) * l * 0.25, y - Math.sin(a) * l * 0.25, a, l, w, 0.58 + 0.12 * (d / spread) + 0.14 * r(), r);
  }
  // keep the border transparent (no straight card edges) and drop lone pixels (mipmaps eat them anyway)
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
    if (x < margin || y < margin || x >= W - margin || y >= H - margin) img.a[y * W + x] = 0;
  }
  despeckle(img);
  return img;
}

function despeckle(img) {
  const keep = new Uint8Array(img.a);
  for (let y = 0; y < img.H; y++) for (let x = 0; x < img.W; x++) {
    const i = y * img.W + x;
    if (!img.a[i]) continue;
    let n = 0;
    for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
      const X = x + dx, Y = y + dy;
      if (X >= 0 && Y >= 0 && X < img.W && Y < img.H && img.a[Y * img.W + X]) n++;
    }
    if (n === 0) keep[i] = 0;
  }
  img.a.set(keep);
}

function coverage(img) { if (img.png) return 1; let n = 0; for (const a of img.a) n += a; return n / img.a.length; }

// ---- proxy cube face: vanilla-like leaves tile, seamless, dense (what a distant crown voxel shows)
function proxy() {
  const W = 16, H = 16, r = rng(4105);
  const img = image(W, H);
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
    const i = y * W + x;
    const hole = ((x * 5 + y * 3) % 7 === 0) || ((x * 3 + y * 11) % 13 === 0);
    img.a[i] = hole ? 0 : 1;
    img.v[i] = 0.6 + 0.2 * r() - ((x + y * 2) % 5 === 0 ? 0.12 : 0);
  }
  return img;
}

// ---- needle sprays: a twig across the card, bundles of five needles fanning forward along it
function line(img, x0, y0, x1, y1, value) {
  const n = Math.ceil(Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)) * 1.5) + 1;
  for (let k = 0; k <= n; k++) {
    const x = Math.round(x0 + (x1 - x0) * k / n), y = Math.round(y0 + (y1 - y0) * k / n);
    if (x < 1 || y < 1 || x >= img.W - 1 || y >= img.H - 1) continue;
    img.v[y * img.W + x] = value; img.a[y * img.W + x] = 1;
  }
}

function needles(seed, bundles, len, fan) {
  const W = 32, H = 32, r = rng(seed);
  const img = image(W, H);
  // the twig from the lower left to the upper right, bundles along it and on short side twigs
  const tx0 = 4, ty0 = 27, tx1 = 27, ty1 = 6;
  for (let k = 0; k < bundles; k++) {
    const t = 0.08 + 0.88 * (k + r() * 0.6) / bundles;
    const side = k % 2 === 0 ? 1 : -1;
    const bx = tx0 + (tx1 - tx0) * t + side * r() * fan * 0.6, by = ty0 + (ty1 - ty0) * t + side * r() * fan * 0.6;
    const dir = Math.atan2(ty1 - ty0, tx1 - tx0) + side * (0.5 + 0.5 * r());
    for (let n = 0; n < 5; n++) {
      const a = dir + (n - 2) * 0.17 + (r() - 0.5) * 0.1, l = len * (0.75 + 0.35 * r());
      line(img, bx, by, bx + Math.cos(a) * l, by + Math.sin(a) * l, 0.6 + 0.25 * r() + (n === 2 ? 0.05 : 0));
    }
  }
  despeckle(img);
  // the twig last, two pixels wide (a one-pixel diagonal would be eaten by the speck filter and by mipmaps)
  line(img, tx0, ty0, tx1, ty1, 0.32);
  line(img, tx0 + 1, ty0, tx1 + 1, ty1, 0.3);
  return img;
}

function pineProxy() {
  const W = 16, H = 16, r = rng(4205);
  const img = image(W, H);
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
    const i = y * W + x;
    const hole = ((x * 7 + y * 3) % 9 === 0) || ((x * 2 + y * 5) % 11 === 0);
    img.a[i] = hole ? 0 : 1;
    img.v[i] = 0.62 + 0.18 * r() - ((x + y) % 4 === 0 ? 0.12 : 0);
  }
  return img;
}

const outputs = {
  'white_oak_leaves.png': cluster(4102, 15, 9, 6, 17, 1),
  'white_oak_leaves_mid.png': cluster(4103, 26, 9.5, 7, 20, 1),
  'white_oak_leaves_far.png': cluster(4104, 40, 10, 7.5, 22, 1),
  'white_oak_leaves_proxy.png': proxy(),
  'white_pine_needles.png': needles(4202, 12, 9, 6),
  'white_pine_needles_mid.png': needles(4203, 17, 10, 8),
  'white_pine_needles_far.png': needles(4204, 24, 11, 10),
  'white_pine_needles_proxy.png': pineProxy(),
  // P2.1 formal felling assets (felling-art.mjs)
  'white_oak_cut.png': art.cutWood(art.OAK, art.OAK_WOOD, 4301),
  'white_pine_cut.png': art.cutWood(art.PINE, art.PINE_WOOD, 4302),
  'white_oak_end_grain.png': art.endGrain(art.OAK, art.OAK_WOOD, 4303),
  'white_pine_end_grain.png': art.endGrain(art.PINE, art.PINE_WOOD, 4304),
  // P2.2 log block ends
  'white_oak_log_end.png': art.logEnd(art.OAK, art.OAK_WOOD, 4305),
  'white_pine_log_end.png': art.logEnd(art.PINE, art.PINE_WOOD, 4306),
  '../../item/tree/white_oak_branch.png': art.branchIcon(art.OAK, false),
  '../../item/tree/white_pine_branch.png': art.branchIcon(art.PINE, true),
  '../../item/tree/white_oak_leaves.png': art.oakLeafIcon(art.OAK),
  '../../item/tree/white_pine_needles.png': art.needleIcon(art.PINE),
};
// P3: the four new species. [id, leaves id, palette, wood, LOD0/mid/far cards, proxy, icons, seed base]
const P3 = [
  ['red_oak', 'red_oak_leaves', sp.RED_OAK, sp.RED_OAK_WOOD,
    [s => sp.redOakCluster(s, 13, 11, 17), s => sp.redOakCluster(s, 22, 11.5, 20), s => sp.redOakCluster(s, 34, 12, 22)],
    s => sp.proxyTile(s, [5, 3, 7], [3, 11, 13], 0.55, 0.2), sp.redOakLeafIcon, sp.redOakBranchIcon, 4400],
  ['shagbark_hickory', 'shagbark_hickory_leaves', sp.HICKORY, sp.HICKORY_WOOD,
    [s => sp.hickoryCluster(s, 7, 13, 17), s => sp.hickoryCluster(s, 14, 13, 19), s => sp.hickoryCluster(s, 32, 14, 23)],
    s => sp.proxyTile(s, [7, 3, 9], [2, 5, 11], 0.64, 0.18), sp.hickoryLeafIcon, sp.hickoryBranchIcon, 4500],
  ['red_maple', 'red_maple_leaves', sp.RED_MAPLE, sp.RED_MAPLE_WOOD,
    [s => sp.mapleCluster(s, 18, 4.2, 16), s => sp.mapleCluster(s, 36, 4.5, 19), s => sp.mapleCluster(s, 60, 4.9, 22)],
    s => sp.proxyTile(s, [5, 3, 7], [3, 7, 11], 0.6, 0.2), sp.mapleLeafIcon, sp.mapleBranchIcon, 4600],
  ['eastern_hemlock', 'eastern_hemlock_needles', sp.HEMLOCK, sp.HEMLOCK_WOOD,
    [s => sp.hemlockSprays(s, 5, 16), s => sp.hemlockSprays(s, 7, 19), s => sp.hemlockSprays(s, 9, 22)],
    s => sp.proxyTile(s, [7, 3, 9], [2, 5, 11], 0.5, 0.16), sp.hemlockIcon, sp.hemlockBranchIcon, 4700],
];
for (const [id, leaves, pal, wood, cards, proxyFn, leafIcon, branchIcon, seed] of P3) {
  outputs[leaves + '.png'] = cards[0](seed + 2);
  outputs[leaves + '_mid.png'] = cards[1](seed + 3);
  outputs[leaves + '_far.png'] = cards[2](seed + 4);
  outputs[leaves + '_proxy.png'] = proxyFn(seed + 5);
  outputs[id + '_cut.png'] = art.cutWood(pal, wood, seed + 6);
  outputs[id + '_end_grain.png'] = art.endGrain(pal, wood, seed + 7);
  outputs[id + '_log_end.png'] = art.logEnd(pal, wood, seed + 8);
  outputs['../../item/tree/' + id + '_branch.png'] = branchIcon(pal);
  outputs['../../item/tree/' + leaves + '.png'] = leafIcon(pal);
}
// the six barks: colour, _s, _n drawn together (bark-art.mjs, fixed seeds of its own)
for (const [id, pal] of [['white_oak', art.OAK], ['white_pine', art.PINE], ...P3.map(([id, , pal]) => [id, pal])]) {
  barkMaps(id, pal).forEach((buf, k) => { outputs[id + '_bark' + ['', '_s', '_n'][k] + '.png'] = {png: buf, W: BARK_PX, H: BARK_PX}; });
}
// LabPBR of the ends and the cut (normals gentle and smoothed, height alpha flat, dielectric rough wood)
for (const name of ['white_oak_cut', 'white_pine_cut', 'white_oak_end_grain', 'white_pine_end_grain', 'white_oak_log_end', 'white_pine_log_end',
  ...P3.flatMap(([id]) => [id + '_cut', id + '_end_grain', id + '_log_end'])]) {
  const base = outputs[name + '.png'], cut = name.endsWith('_cut');
  outputs[name + '_n.png'] = art.normalMap(base, cut ? 0.6 : 0.5, false, cut ? 25 : 20);
  outputs[name + '_s.png'] = art.specularMap(base, cut ? 50 : 40, cut ? 80 : 70, 50, false);
}

const check = process.argv.includes('--check');
let bad = 0;
fs.mkdirSync(OUT, {recursive: true});
for (const [name, img] of Object.entries(outputs)) {
  const data = toPng(img);
  const file = path.join(OUT, name);
  fs.mkdirSync(path.dirname(file), {recursive: true});
  const cov = (coverage(img) * 100).toFixed(0);
  if (check) {
    const same = fs.existsSync(file) && fs.readFileSync(file).equals(data);
    if (!same) bad++;
    console.log(`${same ? 'OK  ' : 'DIFF'} ${name} (${img.W}x${img.H}, coverage ${cov}%)`);
  } else {
    fs.writeFileSync(file, data);
    console.log(`wrote ${name} (${img.W}x${img.H}, coverage ${cov}%)`);
  }
}
if (check) { console.log(bad === 0 ? 'CHECK OK' : `CHECK FAILED (${bad})`); process.exitCode = bad === 0 ? 0 : 1; }
