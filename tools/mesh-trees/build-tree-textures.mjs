// Mesh Trees P0-A (docs/rendering/mesh_trees_p0a.md): the white oak technical sample's textures, development only.
// Writes src/dev/highway_mesh_resources/assets/afl_tree_demo/textures/block/:
//   white_oak_bark.png          16 x 16, seamless both ways (tube quads restart it every metre of length)
//   white_oak_leaves.png        32 x 32 leaf cluster for LOD0 cards (1.5 m card: about 21 px per metre)
//   white_oak_leaves_mid.png    32 x 32 denser clusters for LOD1 (two cards stand in for six)
//   white_oak_leaves_far.png    32 x 32 leaf mass for LOD2 (one or two cards per cluster)
//   white_oak_leaves_proxy.png  16 x 16 cube face for the volume proxy (what Voxy and maps see)
//   white_pine_bark.png         16 x 16, dark grey-brown, deep furrows between long flat ridges
//   white_pine_needles.png      32 x 32 needle spray (bundles of five on a twig) for LOD0 plates
//   white_pine_needles_mid.png / _far.png   denser sprays for LOD1 / LOD2
//   white_pine_needles_proxy.png  16 x 16 cube face for the pine's volume proxy
// Mesh Trees P2.1 (docs/rendering/mesh_trees_p2_felling.md, U), formal felling assets from felling-art.mjs:
//   white_oak_cut.png / white_pine_cut.png              32 x 32 fresh cut wood (the notch faces)
//   white_oak_end_grain.png / white_pine_end_grain.png  32 x 32 rings, sapwood, bark rim (stump top, falling log cut)
//   ../item/<wood>_branch, white_oak_leaves, white_pine_needles .png   16 x 16 item icons
//   LabPBR _n / _s for both barks, cut wood and end grain (leaves: none, the shader pack's leaf handling stays)
// Mesh Trees P2.2 (mesh_trees_p2_felling.md, V): the log blocks' ends (the logs' 2D item icons are gone, they are blocks)
//   white_oak_log_end.png / white_pine_log_end.png      32 x 32 rounded-square end with bark rim, rings; LabPBR _n / _s
// Mesh Trees P3 (mesh_trees_p3_species.md, C), from species-art.mjs, for red_oak, shagbark_hickory, red_maple and
// eastern_hemlock: <id>_bark (+ _n/_s), the leaf cards <leaves>, _mid, _far, _proxy, <id>_cut, _end_grain, _log_end
// (+ _n/_s), and ../item/<id>_branch, ../item/<leaves> icons
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {png} from '../cube-slab-mesh-lib.mjs';
import * as art from './felling-art.mjs';
import * as sp from './species-art.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const OUT = path.join(ROOT, 'src/dev/highway_mesh_resources/assets/afl_tree_demo/textures/block');

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

function toPng(img) {
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

// ---- bark: shallow fissures between irregular vertical plates, light ash grey (white oak)
function bark() {
  const W = 16, H = 16, r = rng(4101);
  const img = image(W, H);
  img.rgb = new Uint8Array(W * H * 3);
  const plate = [[0x9d, 0x97, 0x8c], [0x93, 0x8d, 0x83], [0x8a, 0x85, 0x7b]];
  const crack = [0x63, 0x5e, 0x56], deep = [0x50, 0x4c, 0x45], lit = [0xa8, 0xa2, 0x97];
  // fissure x per row: four wandering fissures, wrapping both ways
  const base = [1, 5, 9, 13];
  const fx = [];
  for (let k = 0; k < 4; k++) {
    const row = [];
    let x = base[k] + (r() < 0.5 ? 0 : 1);
    for (let y = 0; y < H; y++) { if (y % 4 === 0 && y > 0) x += r() < 0.33 ? -1 : r() < 0.5 ? 1 : 0; row.push(((x % W) + W) % W); }
    // close the loop vertically: last row back to the first one's column
    row[H - 1] = row[0];
    fx.push(row);
  }
  // plate shade: per plate (between fissures) and per 5-6 px band of height
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
    let c = plate[(Math.floor(x / 4) + Math.floor((y + (x >> 2) * 3) / 6)) % 3];
    let onCrack = false, nearCrack = false;
    for (const row of fx) { const d = ((x - row[y]) % W + W) % W; if (d === 0) onCrack = true; if (d === 1) nearCrack = true; }
    if (onCrack) c = (y * 7 + x * 3) % 5 === 0 ? deep : crack;
    else if (nearCrack && (y + x) % 3 === 0) c = lit;
    // short horizontal breaks between plates
    if (!onCrack && (y === 3 || y === 10) && (x % 4 === 2)) c = crack;
    img.rgb.set(c, (y * W + x) * 3);
    img.a[y * W + x] = 1;
  }
  return img;
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

function coverage(img) { let n = 0; for (const a of img.a) n += a; return n / img.a.length; }

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

// ---- white pine bark: deep dark furrows between long flat ridges, short scaly breaks on the ridges
function pineBark() {
  const W = 16, H = 16, r = rng(4201);
  const img = image(W, H);
  img.rgb = new Uint8Array(W * H * 3);
  const ridge = [[0x74, 0x67, 0x5d], [0x6a, 0x5e, 0x55], [0x7e, 0x71, 0x66]];
  const furrow = [0x41, 0x39, 0x34], deep = [0x30, 0x2a, 0x27];
  const cols = [0, 5, 10];   // furrow columns (two pixels wide), wandering slightly
  const fx = cols.map(c => { const row = []; let x = c; for (let y = 0; y < H; y++) { if (y % 5 === 4) x += r() < 0.5 ? 1 : 0; row.push(x % W); } row[H - 1] = row[0]; return row; });
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
    let c = ridge[(Math.floor(x / 5) + Math.floor((y + x) / 7)) % 3];
    for (const row of fx) {
      const d = ((x - row[y]) % W + W) % W;
      if (d === 0) c = deep; else if (d === 1) c = furrow;
    }
    if ((y === 4 || y === 12) && c !== deep && c !== furrow && x % 5 === 3) c = furrow;   // scale breaks
    img.rgb.set(c, (y * W + x) * 3);
    img.a[y * W + x] = 1;
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

const oakBark = bark(), whitePineBark = pineBark();
const outputs = {
  'white_oak_bark.png': oakBark,
  'white_oak_leaves.png': cluster(4102, 15, 9, 6, 17, 1),
  'white_oak_leaves_mid.png': cluster(4103, 26, 9.5, 7, 20, 1),
  'white_oak_leaves_far.png': cluster(4104, 40, 10, 7.5, 22, 1),
  'white_oak_leaves_proxy.png': proxy(),
  'white_pine_bark.png': whitePineBark,
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
  '../item/white_oak_branch.png': art.branchIcon(art.OAK, false),
  '../item/white_pine_branch.png': art.branchIcon(art.PINE, true),
  '../item/white_oak_leaves.png': art.oakLeafIcon(art.OAK),
  '../item/white_pine_needles.png': art.needleIcon(art.PINE),
};
// P3: the four new species. [id, leaves id, palette, wood, bark, LOD0/mid/far cards, proxy, icons, seed base, bark normal strength]
const P3 = [
  ['red_oak', 'red_oak_leaves', sp.RED_OAK, sp.RED_OAK_WOOD, sp.redOakBark,
    [s => sp.redOakCluster(s, 13, 11, 17), s => sp.redOakCluster(s, 22, 11.5, 20), s => sp.redOakCluster(s, 34, 12, 22)],
    s => sp.proxyTile(s, [5, 3, 7], [3, 11, 13], 0.55, 0.2), sp.redOakLeafIcon, sp.redOakBranchIcon, 4400, 0.9],
  ['shagbark_hickory', 'shagbark_hickory_leaves', sp.HICKORY, sp.HICKORY_WOOD, sp.hickoryBark,
    [s => sp.hickoryCluster(s, 7, 13, 17), s => sp.hickoryCluster(s, 14, 13, 19), s => sp.hickoryCluster(s, 32, 14, 23)],
    s => sp.proxyTile(s, [7, 3, 9], [2, 5, 11], 0.64, 0.18), sp.hickoryLeafIcon, sp.hickoryBranchIcon, 4500, 1.1],
  ['red_maple', 'red_maple_leaves', sp.RED_MAPLE, sp.RED_MAPLE_WOOD, sp.mapleBark,
    [s => sp.mapleCluster(s, 18, 4.2, 16), s => sp.mapleCluster(s, 36, 4.5, 19), s => sp.mapleCluster(s, 60, 4.9, 22)],
    s => sp.proxyTile(s, [5, 3, 7], [3, 7, 11], 0.6, 0.2), sp.mapleLeafIcon, sp.mapleBranchIcon, 4600, 0.7],
  ['eastern_hemlock', 'eastern_hemlock_needles', sp.HEMLOCK, sp.HEMLOCK_WOOD, sp.hemlockBark,
    [s => sp.hemlockSprays(s, 5, 16), s => sp.hemlockSprays(s, 7, 19), s => sp.hemlockSprays(s, 9, 22)],
    s => sp.proxyTile(s, [7, 3, 9], [2, 5, 11], 0.5, 0.16), sp.hemlockIcon, sp.hemlockBranchIcon, 4700, 1.0],
];
for (const [id, leaves, pal, wood, barkFn, cards, proxyFn, leafIcon, branchIcon, seed, strength] of P3) {
  const b = barkFn(pal);
  outputs[id + '_bark.png'] = b;
  outputs[id + '_bark_n.png'] = art.normalMap(b, strength, true, 45);
  outputs[id + '_bark_s.png'] = art.specularMap(b, 14, 40, 40, true);
  outputs[leaves + '.png'] = cards[0](seed + 2);
  outputs[leaves + '_mid.png'] = cards[1](seed + 3);
  outputs[leaves + '_far.png'] = cards[2](seed + 4);
  outputs[leaves + '_proxy.png'] = proxyFn(seed + 5);
  outputs[id + '_cut.png'] = art.cutWood(pal, wood, seed + 6);
  outputs[id + '_end_grain.png'] = art.endGrain(pal, wood, seed + 7);
  outputs[id + '_log_end.png'] = art.logEnd(pal, wood, seed + 8);
  outputs['../item/' + id + '_branch.png'] = branchIcon(pal);
  outputs['../item/' + leaves + '.png'] = leafIcon(pal);
}
// LabPBR (normals gentle and smoothed, height alpha flat, dielectric rough wood)
Object.assign(outputs, {
  'white_oak_bark_n.png': art.normalMap(oakBark, 0.7, true, 40),
  'white_oak_bark_s.png': art.specularMap(oakBark, 18, 42, 40, true),
  'white_pine_bark_n.png': art.normalMap(whitePineBark, 1.0, true, 50),
  'white_pine_bark_s.png': art.specularMap(whitePineBark, 14, 36, 40, true),
});
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
