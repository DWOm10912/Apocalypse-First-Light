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
// Mesh Trees P2 (docs/rendering/mesh_trees_p2_felling.md, D/J/K):
//   white_oak_cut.png / white_pine_cut.png              16 x 16 fresh cut wood with axe facets (the notch faces)
//   white_oak_end_grain.png / white_pine_end_grain.png  16 x 16 growth rings and a bark rim (stump top, falling log)
//   ../item/<wood>_log, _branch, white_oak_leaves, white_pine_needles .png   16 x 16 item icons
// Leaves are grey (biome foliage tint), alpha is 0 or 255 (cutout). Deterministic.
//   node tools/mesh-trees/build-tree-textures.mjs          write
//   node tools/mesh-trees/build-tree-textures.mjs --check  compare with the files on disk
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {png} from '../cube-slab-mesh-lib.mjs';

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

// ---- P2: fresh cut wood, axe facets across the fibres (the notch faces; u round the trunk, v across the cut)
function cutWood(seed, light, dark, line) {
  const W = 16, H = 16, r = rng(seed);
  const img = image(W, H);
  img.rgb = new Uint8Array(W * H * 3);
  // facets: bands 3-4 px high, each a little lighter or darker (an axe stroke each), fibre lines along u
  const bands = [];
  for (let y = 0; y < H;) { const h = 3 + Math.floor(r() * 2); bands.push([y, Math.min(H, y + h), r()]); y += h; }
  for (const [y0, y1, k] of bands) for (let y = y0; y < y1; y++) for (let x = 0; x < W; x++) {
    const t = 0.35 + 0.5 * k + (y === y0 ? 0.12 : 0) - (y === y1 - 1 ? 0.15 : 0);
    let c = light.map((v, i) => Math.round(dark[i] + (v - dark[i]) * Math.max(0, Math.min(1, t))));
    if ((x + Math.floor(y / 5) * 3) % 5 === 0 && y !== y0) c = line;          // fibre lines
    img.rgb.set(c, (y * W + x) * 3);
    img.a[y * W + x] = 1;
  }
  return img;
}

// ---- P2: end grain: growth rings round the pith, a few radial checks, the bark rim (UV centred, radius about 7.6 px)
function endGrain(seed, early, late, pith, bark) {
  const W = 16, H = 16, r = rng(seed);
  const img = image(W, H);
  img.rgb = new Uint8Array(W * H * 3);
  const checks = [r() * 6.283, r() * 6.283];
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
    const dx = x + 0.5 - 8, dy = y + 0.5 - 8, d = Math.hypot(dx, dy), a = Math.atan2(dy, dx);
    const wob = 0.25 * Math.sin(a * 3 + 1.3) + 0.15 * Math.sin(a * 5);
    const ring = Math.floor((d + wob) / 1.3);
    let c = ring % 2 === 0 ? early : late;
    if (d < 1.1) c = pith;
    for (const ch of checks) if (Math.abs(Math.atan2(Math.sin(a - ch), Math.cos(a - ch))) < 0.09 && d > 2 && d < 6.5) c = late.map(v => Math.round(v * 0.75));
    if (d > 6.9) c = bark;
    img.rgb.set(c, (y * W + x) * 3);
    img.a[y * W + x] = 1;
  }
  return img;
}

// ---- P2: item icons (alpha cutout, coloured, 16 x 16)
function icon() { const img = image(16, 16); img.rgb = new Uint8Array(16 * 16 * 3); return img; }
function dot(img, x, y, c) {
  if (x < 0 || y < 0 || x >= img.W || y >= img.H) return;
  img.rgb.set(c, (y * img.W + x) * 3); img.a[y * img.W + x] = 1;
}

function logIcon(barkA, barkB, early, late, bark) {
  const img = icon();
  // a short log lying from the lower left to the upper right, its cut end toward the viewer at the lower left
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
    const u = (x - 3 + (12 - y)) / 2, v = (x - 3 - (12 - y)) / 2;      // along / across the log
    if (u < 0 || u > 9.5 || Math.abs(v) > 3.2) continue;
    dot(img, x, y, (Math.floor(u * 1.3) + Math.round(v)) % 3 === 0 ? barkB : barkA);
  }
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
    const d = Math.hypot(x + 0.5 - 4.5, y + 0.5 - 11.5);
    if (d > 3.4) continue;
    dot(img, x, y, d > 2.7 ? bark : Math.floor(d / 0.9) % 2 === 0 ? early : late);
  }
  return img;
}

function branchIcon(seed, bark, tip, leafColour, needles) {
  const img = icon(), r = rng(seed);
  const seg = (x0, y0, x1, y1, c) => { const n = 24; for (let k = 0; k <= n; k++) dot(img, Math.round(x0 + (x1 - x0) * k / n), Math.round(y0 + (y1 - y0) * k / n), c); };
  seg(2, 14, 13, 3, bark);
  seg(3, 14, 13, 4, bark);
  seg(8, 8, 13, 9, bark);
  seg(6, 10, 4, 5, bark);
  dot(img, 13, 3, tip); dot(img, 13, 9, tip); dot(img, 4, 5, tip);
  if (needles) {
    for (const [cx, cy] of [[13, 3], [13, 9], [4, 5]]) for (let k = 0; k < 5; k++) {
      const a = -2.4 + k * 0.45 + r() * 0.2;
      for (let s = 1; s <= 2; s++) dot(img, Math.round(cx + Math.cos(a) * s), Math.round(cy + Math.sin(a) * s), leafColour);
    }
  } else {
    for (const [cx, cy] of [[12, 2], [14, 9]]) for (let y = -1; y <= 1; y++) for (let x = -1; x <= 1; x++) if (Math.abs(x) + Math.abs(y) < 2) dot(img, cx + x, cy + y, leafColour);
  }
  return img;
}

function leafIcon(colour, vein, edge) {
  const img = icon();
  // a lobed white oak leaf, stalk at the lower left
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
    const u = (x - 3 + (12 - y)) / 13, v = (x - 3 - (12 - y)) / 2;
    if (u < 0.08 || u > 1) continue;
    const half = 3.6 * Math.pow(Math.sin(Math.PI * Math.min(1, u * 1.05)), 0.7) * (0.55 + 0.45 * Math.abs(Math.cos(u * Math.PI * 3.5)));
    if (Math.abs(v) > half) continue;
    dot(img, x, y, Math.abs(v) < 0.5 ? vein : Math.abs(v) > half - 0.6 ? edge : colour);
  }
  for (let k = 0; k < 3; k++) dot(img, 2 + k, 13 - k, vein);
  return img;
}

function needleIcon(seed, colour, light, twig) {
  const img = icon(), r = rng(seed);
  // a twig from the lower left to the upper right, needles fanning forward on both sides of it
  const ta = Math.atan2(4 - 13, 12 - 3);
  for (let k = 0; k < 7; k++) {
    const t = 0.1 + 0.85 * k / 6, bx = 3 + 9 * t, by = 13 - 9 * t;
    for (const side of [-1, 1]) {
      const a = ta + side * (0.65 + 0.15 * r()), len = 3.5 + r() * 1.5;
      for (let s = 1; s <= len; s++) dot(img, Math.round(bx + Math.cos(a) * s), Math.round(by + Math.sin(a) * s), s > len - 1 ? light : colour);
    }
  }
  for (let k = 0; k <= 18; k++) dot(img, Math.round(3 + 9 * k / 18), Math.round(13 - 9 * k / 18), twig);
  return img;
}

const outputs = {
  'white_oak_bark.png': bark(),
  'white_oak_leaves.png': cluster(4102, 15, 9, 6, 17, 1),
  'white_oak_leaves_mid.png': cluster(4103, 26, 9.5, 7, 20, 1),
  'white_oak_leaves_far.png': cluster(4104, 40, 10, 7.5, 22, 1),
  'white_oak_leaves_proxy.png': proxy(),
  'white_pine_bark.png': pineBark(),
  'white_pine_needles.png': needles(4202, 12, 9, 6),
  'white_pine_needles_mid.png': needles(4203, 17, 10, 8),
  'white_pine_needles_far.png': needles(4204, 24, 11, 10),
  'white_pine_needles_proxy.png': pineProxy(),
  // P2
  'white_oak_cut.png': cutWood(4301, [0xd9, 0xc4, 0x98], [0xa8, 0x8a, 0x5c], [0xb0, 0x93, 0x66]),
  'white_pine_cut.png': cutWood(4302, [0xe8, 0xd9, 0xae], [0xc2, 0xa8, 0x76], [0xcc, 0xb4, 0x84]),
  'white_oak_end_grain.png': endGrain(4303, [0xc9, 0xa9, 0x74], [0x9c, 0x7a, 0x4b], [0x6e, 0x52, 0x33], [0x6a, 0x64, 0x5a]),
  'white_pine_end_grain.png': endGrain(4304, [0xe2, 0xcd, 0x98], [0xbf, 0x9b, 0x62], [0x8c, 0x6a, 0x40], [0x4f, 0x45, 0x3e]),
  '../item/white_oak_log.png': logIcon([0x93, 0x8d, 0x83], [0x6e, 0x69, 0x61], [0xc9, 0xa9, 0x74], [0x9c, 0x7a, 0x4b], [0x5a, 0x55, 0x4e]),
  '../item/white_pine_log.png': logIcon([0x6a, 0x5e, 0x55], [0x45, 0x3c, 0x36], [0xe2, 0xcd, 0x98], [0xbf, 0x9b, 0x62], [0x3e, 0x36, 0x31]),
  '../item/white_oak_branch.png': branchIcon(4305, [0x7a, 0x6f, 0x62], [0xa3, 0x96, 0x84], [0x5c, 0x8a, 0x3a], false),
  '../item/white_pine_branch.png': branchIcon(4306, [0x5e, 0x51, 0x48], [0x80, 0x71, 0x64], [0x4e, 0x7e, 0x5e], true),
  '../item/white_oak_leaves.png': leafIcon([0x5e, 0x8c, 0x3a], [0x7c, 0xa4, 0x52], [0x45, 0x6b, 0x29]),
  '../item/white_pine_needles.png': needleIcon(4307, [0x4e, 0x7e, 0x5e], [0x6e, 0x9e, 0x7c], [0x5e, 0x51, 0x48]),
};

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
