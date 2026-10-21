// Wood Photo V1 (docs/models/wood_texture_rework_v1_plan.md, user 2026-10-20 "第1版" + furniture walnut "B"): the wood
// textures from CC0 photographs (Poly Haven, ambientCG; CC0, no attribution required, sources listed in SOURCES), replacing
// the procedural stripes the user rejected ("树有点糊，原木有点糊，家具的纹路很奇怪"). Per species (white_oak, white_pine,
// red_oak, shagbark_hickory, red_maple, eastern_hemlock), into textures/block/:
//   tree/<id>_bark        128 x 128, one tile ~1 m of bark at true scale (TreeShape / LogShape lay one tile per metre round
//                         the trunk), made seamless by a half-offset blend, the photo's large light removed, graded to the
//                         species' bark colour (no moss green). _n: the photo's normal x 0.35 (user: "之前的问题是法线感太强"),
//                         green as hd-art.mjs, B = mild AO; _s rough dielectric.
//   tree/<id>_log_end     128: the cross-section photo warped into the species' LogShape rounded square (radius 2.5..6/16),
//                         the bark rim along the outline; flat _n, _s like the old end grain
//   tree/<id>_end_grain   128: the round disc (TreeCut maps the ring radius to 1/2.1 of the texture), stumps and fallen logs
//   tree/<id>_cut         128: the notch faces, the species' planed wood a little darker and rougher
//   wood/<id>_timber_side 240: planed wood along v (a veneer photo, 1 m)
//   wood/<id>_planks      240 (2026-10-20, user: "现在各类木板没有换贴图"): four 25 cm boards across the block cut from the
//                         timber photo, grain along u, each board its own part of the photo, a slow run-out (so the grain
//                         breaks at the staggered butt joint) and its own tone and warmth (boards from different logs);
//                         the seams fine, only a little darker than the wood, a soft bevel in _n
//   wood/<id>_timber_end  240: the rounded timber's ends, a square from inside the cross-section photo (pith in the
//                         middle, no bark), flat _n
//   tools/wood-photo/*_240.png (2026-10-20, user: "老宅原木墙、柴火、柴垛、板条墙都换成新的木纹"): colour bases read by
//                         tools/cabin/cabin-art.mjs and tools/stove/box-stove-art.mjs: white_oak_log_end (the end disc out
//                         to the wood's edge, no bark, radius 0.47 of the sprite), firewood_end (red oak disc with its bark
//                         rim, radius 0.47), firewood_bark (red oak bark at 240 px per metre). Rerun those after this one.
//   ../../../../tools/furniture/walnut_finish_240.png   the furniture finish (walnut_veneer_02, lacquered walnut tone, grain
//                         along u), read by furniture/wood-furniture-art.mjs for every furniture wood texture
// build-tree-textures.mjs and build-wood-blocks-v1.mjs no longer write these outputs. tools/cabin/cabin-art.mjs reads the
// white oak timber_side for the cabin's planed oak (doors, trim, ceiling and deck boards): rerun the cabin textures after it.
// Photos are cached in build/cc0-wood/ (not in git). JPEG decoding uses sharp from wiki/node_modules (npm --prefix wiki ci).
//   node tools/build-wood-photo-v1.mjs --fetch   -> downloads the missing photos into the cache
//   node tools/build-wood-photo-v1.mjs           -> writes the textures
//   node tools/build-wood-photo-v1.mjs --check   -> verifies they are up to date (needs the cache)
//   node tools/build-wood-photo-v1.mjs --preview DIR -> also writes a contact sheet of the logs
import fs from 'node:fs';
import path from 'node:path';
import {execFileSync} from 'node:child_process';
import {createRequire} from 'node:module';
import {fileURLToPath} from 'node:url';
import {png} from './cube-slab-mesh-lib.mjs';
import {render, hash} from './mesh-trees/hd-art.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const TEX = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight/textures/block');
const FINISH = path.join(ROOT, 'tools/furniture/walnut_finish_240.png');
const BASES = path.join(ROOT, 'tools/wood-photo');
const CACHE = path.join(ROOT, 'build/cc0-wood');
const PH = 'https://dl.polyhaven.org/file/ph-assets/Textures/jpg/1k';

/** CC0 photos: cache file -> URL (Poly Haven 1k JPG diffuse / OpenGL normal; ambientCG 1K-JPG zip). */
const SOURCES = {};
for (const a of ['bark_willow_02', 'bark_willow', 'chinese_hackberry_bark', 'tree_bark_03', 'pine_bark', 'knotted_pine_bark'])
  for (const m of ['diff', 'nor_gl']) SOURCES[`${a}_${m}_1k.jpg`] = `${PH}/${a}/${a}_${m}_1k.jpg`;
for (const a of ['white_oak_veneer', 'red_oak_veneer', 'white_maple_veneer', 'ash_veneer', 'coated_pine', 'walnut_veneer_02'])
  SOURCES[`${a}_diff_1k.jpg`] = `${PH}/${a}/${a}_diff_1k.jpg`;
for (const a of ['TreeEnd002', 'TreeEnd003']) SOURCES[`${a}_1K-JPG.zip`] = `https://ambientcg.com/get?file=${a}_1K-JPG.zip`;
/** Physical size of a Poly Haven tile (m, its "dimensions"). */
const DIM = {bark_willow_02: 2.15, bark_willow: 1, chinese_hackberry_bark: 1.8, tree_bark_03: 1, pine_bark: 2, knotted_pine_bark: 1.7,
  white_oak_veneer: 0.5, red_oak_veneer: 1, white_maple_veneer: 1, ash_veneer: 1, coated_pine: 0.74, walnut_veneer_02: 1};

/**
 * Species in Species order. bark*: photo, mean colour, saturation, contrast (moss: pull green to brown; hi: soften
 * highlights such as lichen), barkOx: crop offset (source px). end*: disc photo, its turn (90 deg steps) and mirror, wood
 * colour and contrast; rim: the bark ring's colour. logR: LogShape.radius. timber*: the planed wood.
 */
const SPECIES = [
  {id: 'white_oak', logR: 3 / 16, bark: 'bark_willow_02', barkT: [152, 146, 136], barkSat: 0.55, barkCon: 1.0,
    end: 'TreeEnd002', endRot: 0, endT: [186, 154, 112], endCon: 1.0, rim: [118, 108, 96],
    timber: 'white_oak_veneer', timberT: [196, 170, 128]},
  {id: 'white_pine', logR: 5 / 16, bark: 'knotted_pine_bark', barkT: [86, 82, 78], barkSat: 0.45, barkCon: 1.05, barkOx: 300,
    end: 'TreeEnd003', endRot: 3, endT: [216, 190, 142], endCon: 1.3, rim: [70, 66, 62],
    timber: 'coated_pine', timberT: [224, 202, 160], timberSat: 0.35, timberCon: 0.8},
  {id: 'red_oak', logR: 3.5 / 16, bark: 'bark_willow', barkT: [100, 94, 88], barkSat: 0.45, barkCon: 1.1,
    end: 'TreeEnd002', endRot: 2, endFlip: true, endT: [188, 142, 110], endCon: 1.0, rim: [78, 72, 66],
    timber: 'red_oak_veneer', timberT: [198, 152, 118]},
  {id: 'shagbark_hickory', logR: 2.5 / 16, bark: 'tree_bark_03', barkT: [120, 112, 102], barkSat: 0.25, barkCon: 1.15, moss: true,
    end: 'TreeEnd002', endRot: 1, endT: [194, 164, 124], endCon: 0.95, rim: [92, 86, 78],
    timber: 'ash_veneer', timberT: [206, 178, 138]},
  {id: 'red_maple', logR: 4.5 / 16, bark: 'chinese_hackberry_bark', barkT: [140, 138, 132], barkSat: 0.25, barkCon: 0.9, moss: true, hi: 0.12,
    end: 'TreeEnd003', endRot: 0, endT: [214, 194, 160], endCon: 1.0, rim: [104, 100, 94],
    timber: 'white_maple_veneer', timberT: [222, 204, 172]},
  {id: 'eastern_hemlock', logR: 6 / 16, bark: 'pine_bark', barkT: [96, 76, 64], barkSat: 0.45, barkCon: 1.0,
    end: 'TreeEnd003', endRot: 1, endFlip: true, endT: [198, 162, 126], endCon: 1.25, rim: [92, 62, 48],
    timber: 'coated_pine', timberOx: 400, timberT: [208, 174, 142], timberSat: 0.45, timberCon: 0.8},
];
const BARK_N = 128, END_N = 128, CUT_N = 128, TIMBER_N = 240, N_STRENGTH = 0.35, DELIGHT = 0.6;
const FURN = {src: 'walnut_veneer_02', T: [92, 60, 42], sat: 0.85, con: 1.35, delight: 0.25};

// ------------------------------------------------------------------ fetch
const args = process.argv.slice(2), check = args.includes('--check'), fetchMode = args.includes('--fetch');
const previewDir = args.includes('--preview') ? args[args.indexOf('--preview') + 1] : null;
if (fetchMode) {
  fs.mkdirSync(CACHE, {recursive: true});
  for (const [file, url] of Object.entries(SOURCES)) {
    const f = path.join(CACHE, file);
    if (!fs.existsSync(f)) {
      const r = await fetch(url);
      if (!r.ok) throw new Error(`${url}: ${r.status}`);
      fs.writeFileSync(f, Buffer.from(await r.arrayBuffer()));
      console.log('fetched', file);
    }
    if (file.endsWith('.zip')) {
      const dir = path.join(CACHE, file.replace('_1K-JPG.zip', ''));
      if (!fs.existsSync(dir)) { fs.mkdirSync(dir); execFileSync('tar', ['-xf', f, '-C', dir]); }
    }
  }
  console.log('cache ready:', CACHE);
  process.exit(0);
}
const missing = Object.keys(SOURCES).filter(f => !fs.existsSync(path.join(CACHE, f)));
if (missing.length) {
  console.log(`photo cache incomplete (${missing.length} missing, e.g. ${missing[0]}): run node tools/build-wood-photo-v1.mjs --fetch`);
  process.exit(check ? 1 : 2);
}
const sharp = createRequire(path.join(ROOT, 'wiki/package.json'))('sharp');

// ------------------------------------------------------------------ image helpers (float 0..1, c channels)
const img = (w, h, c = 3) => ({w, h, c, d: new Float32Array(w * h * c)});
const clamp = (v, a, b) => v < a ? a : v > b ? b : v;
const sstep = (a, b, x) => { const t = clamp((x - a) / (b - a), 0, 1); return t * t * (3 - 2 * t); };
const lum = (r, g, b) => 0.2126 * r + 0.7152 * g + 0.0722 * b;
async function load(file, grey = false) {
  const s = sharp(file);
  const {data, info} = await (grey ? s.greyscale() : s.removeAlpha()).raw().toBuffer({resolveWithObject: true});
  const c = grey ? 1 : 3, o = img(info.width, info.height, c);
  for (let i = 0; i < info.width * info.height; i++) for (let k = 0; k < c; k++) o.d[i * c + k] = data[i * info.channels + k] / 255;
  return o;
}
function sampleWrap(im, x, y, out) {
  const {w, h, d, c} = im;
  x = ((x % w) + w) % w; y = ((y % h) + h) % h;
  const x0 = Math.floor(x), y0 = Math.floor(y), fx = x - x0, fy = y - y0, x1 = (x0 + 1) % w, y1 = (y0 + 1) % h;
  for (let k = 0; k < c; k++) {
    const a = d[(y0 * w + x0) * c + k], b = d[(y0 * w + x1) * c + k], e = d[(y1 * w + x0) * c + k], f = d[(y1 * w + x1) * c + k];
    out[k] = (a * (1 - fx) + b * fx) * (1 - fy) + (e * (1 - fx) + f * fx) * fy;
  }
  return out;
}
function cropResample(src, ox, oy, C, M) {
  const o = img(M, M, src.c), t = new Float32Array(src.c);
  for (let y = 0; y < M; y++) for (let x = 0; x < M; x++) {
    sampleWrap(src, ox + (x + 0.5) * C / M - 0.5, oy + (y + 0.5) * C / M - 0.5, t);
    for (let k = 0; k < src.c; k++) o.d[(y * M + x) * src.c + k] = t[k];
  }
  return o;
}
/** Seamless: blend with a half-rolled copy, x then y, keeping the variance (no washed-out cross in the middle). */
function makePeriodic(im) {
  const {w, h, c} = im, mean = new Float32Array(c);
  for (let i = 0; i < w * h; i++) for (let k = 0; k < c; k++) mean[k] += im.d[i * c + k] / (w * h);
  let cur = im;
  for (const axis of [0, 1]) {
    const o = img(w, h, c);
    for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
      const u = axis === 0 ? Math.abs((x + 0.5) / w - 0.5) * 2 : Math.abs((y + 0.5) / h - 0.5) * 2, wt = sstep(0.45, 1, u);
      const xr = axis === 0 ? (x + (w >> 1)) % w : x, yr = axis === 1 ? (y + (h >> 1)) % h : y, norm = Math.hypot(1 - wt, wt);
      for (let k = 0; k < c; k++) {
        const a = cur.d[(y * w + x) * c + k] - mean[k], b = cur.d[(yr * w + xr) * c + k] - mean[k];
        o.d[(y * w + x) * c + k] = mean[k] + (a * (1 - wt) + b * wt) / norm;
      }
    }
    cur = o;
  }
  return cur;
}
function down(im, f) {
  const {c} = im, W = im.w / f, H = im.h / f, o = img(W, H, c);
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) for (let k = 0; k < c; k++) {
    let s = 0;
    for (let j = 0; j < f; j++) for (let i = 0; i < f; i++) s += im.d[((y * f + j) * im.w + x * f + i) * c + k];
    o.d[(y * W + x) * c + k] = s / (f * f);
  }
  return o;
}
function blurWrap(a, w, h, r, n = 3) {
  const cur = Float32Array.from(a), tmp = new Float32Array(a.length);
  for (let p = 0; p < n; p++) {
    for (let y = 0; y < h; y++) { let s = 0; for (let i = -r; i <= r; i++) s += cur[y * w + ((i + w) % w)];
      for (let x = 0; x < w; x++) { tmp[y * w + x] = s / (2 * r + 1); s += cur[y * w + (x + r + 1) % w] - cur[y * w + ((x - r) % w + w) % w]; } }
    for (let x = 0; x < w; x++) { let s = 0; for (let i = -r; i <= r; i++) s += tmp[((i + h) % h) * w + x];
      for (let y = 0; y < h; y++) { cur[y * w + x] = s / (2 * r + 1); s += tmp[((y + r + 1) % h) * w + x] - tmp[(((y - r) % h + h) % h) * w + x]; } }
  }
  return cur;
}
/** Remove the photo's large-scale light: luminance divided by its blur, strength s. */
function delight(im, radius, s) {
  const {w, h, d} = im, L = new Float32Array(w * h);
  for (let i = 0; i < w * h; i++) L[i] = lum(d[i * 3], d[i * 3 + 1], d[i * 3 + 2]);
  const B = blurWrap(L, w, h, radius);
  let m = 0; for (let i = 0; i < w * h; i++) m += L[i] / (w * h);
  for (let i = 0; i < w * h; i++) { const f = Math.pow(m / Math.max(1e-3, B[i]), s); for (let k = 0; k < 3; k++) d[i * 3 + k] *= f; }
}
/** Grade toward a mean colour (sRGB 0..255): contrast around the mean, chroma x sat, browns only; mask weights. */
function grade(im, {target, sat = 1, con = 1, moss = false, hi = 9}, mask) {
  const {w, h, d} = im, n = w * h, mean = [0, 0, 0]; let cnt = 0;
  for (let i = 0; i < n; i++) { if (mask && mask[i] < 0.5) continue; cnt++; for (let k = 0; k < 3; k++) mean[k] += d[i * 3 + k]; }
  for (let k = 0; k < 3; k++) mean[k] /= cnt;
  const ml = lum(...mean), tl = lum(...target.map(v => v / 255)), cf = Math.min(1, tl / ml);
  for (let i = 0; i < n; i++) {
    if (mask && mask[i] <= 0) continue;
    let r = d[i * 3], g = d[i * 3 + 1], b = d[i * 3 + 2];
    if (moss) { const l0 = lum(r, g, b); g = Math.min(g, (r + b) / 2 + 0.02); const k0 = l0 / Math.max(1e-3, lum(r, g, b)); r *= k0; g *= k0; b *= k0; }
    g = Math.min(g, r); b = Math.min(b, g);
    const l = lum(r, g, b);
    let rel = (l / ml - 1) * con + 1;
    if (rel > 1 + hi) rel = 1 + hi + (rel - 1 - hi) * 0.25;
    const ch = [r - l, g - l, b - l], a = mask ? clamp(mask[i], 0, 1) : 1;
    for (let k = 0; k < 3; k++) d[i * 3 + k] = d[i * 3 + k] * (1 - a) + clamp(target[k] / 255 * rel + ch[k] * sat * cf, 0, 1) * a;
  }
}
function rotate90(im) { const {c} = im, o = img(im.h, im.w, c); for (let y = 0; y < im.h; y++) for (let x = 0; x < im.w; x++) for (let k = 0; k < c; k++) o.d[(x * o.w + (im.h - 1 - y)) * c + k] = im.d[(y * im.w + x) * c + k]; return o; }
function flipX(im) { const {c} = im, o = img(im.w, im.h, c); for (let y = 0; y < im.h; y++) for (let x = 0; x < im.w; x++) for (let k = 0; k < c; k++) o.d[(y * im.w + im.w - 1 - x) * c + k] = im.d[(y * im.w + x) * c + k]; return o; }
/** Does the grain run along y (the colour changes mostly across x)? */
function grainVertical(im) {
  let gx = 0, gy = 0; const {w, h, d} = im;
  for (let y = 1; y < h; y++) for (let x = 1; x < w; x++) { const i = (y * w + x) * 3; gx += Math.abs(d[i] - d[i - 3]); gy += Math.abs(d[i] - d[i - w * 3]); }
  return gx > gy;
}
const rgba = (im, f) => { const n = im.w * im.h, b = Buffer.alloc(n * 4); for (let i = 0; i < n; i++) { const p = f(i); for (let k = 0; k < 4; k++) b[i * 4 + k] = clamp(Math.round(p[k]), 0, 255); } return b; };
const colourPng = im => png(rgba(im, i => [im.d[i * 3] * 255, im.d[i * 3 + 1] * 255, im.d[i * 3 + 2] * 255, 255]), im.w, im.h);
const flatN = n => png(rgba({w: n, h: n}, () => [128, 128, 255, 255]), n, n);
const specPng = (n, s, p) => png(rgba({w: n, h: n}, () => [s, 10, p, 255]), n, n);

// ------------------------------------------------------------------ makers
/** A periodic tile of tileM metres from a photo of dim metres, at N px (6x supersampled). */
async function photoTile(name, tileM, N, {normal = false, delightS = DELIGHT, ox = 0, oy = 0} = {}) {
  const col = await load(path.join(CACHE, name + '_diff_1k.jpg'));
  delight(col, 64, delightS);
  const C = col.w * tileM / DIM[name], rep = C / col.w, periodic = Math.abs(rep - Math.round(rep)) < 1e-3 && Math.round(rep) >= 1;
  const SS = 6, M = N * SS;
  let a = cropResample(col, ox, oy, C, M);
  if (!periodic) a = makePeriodic(a);
  const res = {col: down(a, SS)};
  if (normal) {
    const nm = await load(path.join(CACHE, name + '_nor_gl_1k.jpg'));
    for (let i = 0; i < nm.d.length; i++) nm.d[i] = nm.d[i] * 2 - 1;
    let b = cropResample(nm, ox, oy, C, M);
    if (!periodic) b = makePeriodic(b);
    res.nrm = down(b, SS);
  }
  return res;
}

async function bark(sp, BN = BARK_N) {
  const t = await photoTile(sp.bark, 1.0, BN, {normal: true, ox: sp.barkOx || 0});
  grade(t.col, {target: sp.barkT, sat: sp.barkSat, con: sp.barkCon, moss: !!sp.moss, hi: sp.hi ?? 9});
  const n = BN * BN, ao = new Float32Array(n);
  let m = 0; for (let i = 0; i < n; i++) m += lum(t.col.d[i * 3], t.col.d[i * 3 + 1], t.col.d[i * 3 + 2]) / n;
  for (let i = 0; i < n; i++) ao[i] = clamp(0.85 + 0.15 * lum(t.col.d[i * 3], t.col.d[i * 3 + 1], t.col.d[i * 3 + 2]) / m, 0.75, 1);
  const nrm = rgba({w: BN, h: BN}, i => {
    let x = t.nrm.d[i * 3] * N_STRENGTH, y = t.nrm.d[i * 3 + 1] * N_STRENGTH, z = Math.max(0.2, t.nrm.d[i * 3 + 2]);
    const l = Math.hypot(x, y, z); x /= l; y /= l;
    return [(x * 0.5 + 0.5) * 255, (-y * 0.5 + 0.5) * 255, 255 * ao[i], 255];
  });
  return {col: t.col, maps: [colourPng(t.col), png(rgba({w: BN, h: BN}, i => [18 + 22 * ao[i], 10, 48, 255]), BN, BN), png(nrm, BN, BN)]};
}

/** The cross-section photo: the round disc (stumps) and the log block's rounded square end. */
async function ends(sp) {
  const dir = path.join(CACHE, sp.end);
  let col = await load(path.join(dir, sp.end + '_1K-JPG_Color.jpg')), op = await load(path.join(dir, sp.end + '_1K-JPG_Opacity.jpg'), true);
  for (let r = 0; r < sp.endRot; r++) { col = rotate90(col); op = rotate90(op); }
  if (sp.endFlip) { col = flipX(col); op = flipX(op); }
  const {w, h} = col;
  let sx = 0, sy = 0, sn = 0;
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) if (op.d[y * w + x] > 0.5) { sx += x; sy += y; sn++; }
  const cx = sx / sn, cy = sy / sn, A = 360, rOut = new Float32Array(A), rs = new Float32Array(A);
  for (let a = 0; a < A; a++) {
    const th = a / A * 2 * Math.PI; let r = 0;
    for (let t = 0; t < w; t += 0.5) { const x = Math.round(cx + Math.cos(th) * t), y = Math.round(cy + Math.sin(th) * t); if (x < 0 || y < 0 || x >= w || y >= h || op.d[y * w + x] < 0.5) break; r = t; }
    rOut[a] = r;
  }
  for (let a = 0; a < A; a++) { let s = 0; for (let k = -4; k <= 4; k++) s += rOut[(a + k + A) % A]; rs[a] = s / 9; }
  const R = rs.reduce((m, v) => Math.max(m, v), 0), rimW = R * 0.07, wood = new Float32Array(w * h), rim = new Float32Array(w * h);
  const ang = (dx, dy) => ((Math.round(Math.atan2(dy, dx) / (2 * Math.PI) * A) % A) + A) % A;
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
    const edge = rs[ang(x - cx, y - cy)], inner = 1 - sstep(edge - rimW * 1.4, edge - rimW * 0.6, Math.hypot(x - cx, y - cy));
    wood[y * w + x] = inner; rim[y * w + x] = op.d[y * w + x] > 0.5 ? 1 - inner : 0;
  }
  { // the photo's light on the wood only (TreeEnd003 has a bright patch)
    const L = new Float32Array(w * h), Wm = new Float32Array(w * h);
    for (let i = 0; i < w * h; i++) { Wm[i] = wood[i] > 0.9 ? 1 : 0; L[i] = Wm[i] * lum(col.d[i * 3], col.d[i * 3 + 1], col.d[i * 3 + 2]); }
    const BL = blurWrap(L, w, h, 60), BW = blurWrap(Wm, w, h, 60);
    let mL = 0, n = 0; for (let i = 0; i < w * h; i++) if (Wm[i]) { mL += L[i]; n++; } mL /= n;
    for (let i = 0; i < w * h; i++) { if (BW[i] < 0.05) continue; const f = Math.pow(mL / Math.max(1e-3, BL[i] / BW[i]), 0.8 * wood[i]); for (let k = 0; k < 3; k++) col.d[i * 3 + k] *= f; }
  }
  grade(col, {target: sp.endT, sat: 0.5, con: sp.endCon, hi: 0.25}, wood);
  grade(col, {target: sp.rim, sat: 0.4, con: 1.0}, rim);
  for (let i = 0; i < w * h; i++) { const o = op.d[i]; for (let k = 0; k < 3; k++) col.d[i * 3 + k] = col.d[i * 3 + k] * o + sp.rim[k] / 255 * 0.75 * (1 - o); }
  const SS = 6, M = END_N * SS, disc = img(M, M), block = img(M, M), t = new Float32Array(3);
  const at = (px, py, o, i) => { sampleWrap(col, clamp(px, 0, w - 1.001), clamp(py, 0, h - 1.001), t); for (let k = 0; k < 3; k++) o.d[i * 3 + k] = t[k]; };
  const Rs = rs.reduce((m, v) => m + v, 0) / A * 1.05;
  const r = sp.logR, sd = (px, py) => { const qx = Math.abs(px) - (0.5 - r), qy = Math.abs(py) - (0.5 - r);
    return Math.hypot(Math.max(qx, 0), Math.max(qy, 0)) + Math.min(Math.max(qx, qy), 0) - r; };
  for (let y = 0; y < M; y++) for (let x = 0; x < M; x++) {
    at(cx - Rs + (x + 0.5) / M * 2 * Rs, cy - Rs + (y + 0.5) / M * 2 * Rs, disc, y * M + x);
    const px = (x + 0.5) / M - 0.5, py = (y + 0.5) / M - 0.5, th = Math.atan2(py, px);
    let lo = 0, hi = 0.75; for (let i = 0; i < 24; i++) { const m = (lo + hi) / 2; if (sd(Math.cos(th) * m, Math.sin(th) * m) < 0) lo = m; else hi = m; }
    const rho = Math.min(1.08, Math.hypot(px, py) / lo), edge = rs[ang(Math.cos(th), Math.sin(th))] * 0.99;
    at(cx + Math.cos(th) * rho * edge, cy + Math.sin(th) * rho * edge, block, y * M + x);
  }
  // the timber's end: a square from inside the wood (its rounded corners at 0.84 of the narrowest wood radius)
  const Rmin = rs.reduce((m, v) => Math.min(m, v), Infinity) * 0.62, M2 = TIMBER_N * 4, square = img(M2, M2);
  for (let y = 0; y < M2; y++) for (let x = 0; x < M2; x++)
    at(cx + ((x + 0.5) / M2 * 2 - 1) * Rmin, cy + ((y + 0.5) / M2 * 2 - 1) * Rmin, square, y * M2 + x);
  // 240 px discs at radius 0.47 of the sprite: with the bark rim (firewood), and the wood alone out to its edge (cabin logs)
  const M3 = TIMBER_N * 4, rimmed = img(M3, M3), bare = img(M3, M3);
  for (let y = 0; y < M3; y++) for (let x = 0; x < M3; x++) {
    const px = (x + 0.5) / M3 - 0.5, py = (y + 0.5) / M3 - 0.5, th = Math.atan2(py, px), q = Math.hypot(px, py) / 0.47;
    at(cx + px / 0.47 * 0.5 * 2 * Rs, cy + py / 0.47 * 0.5 * 2 * Rs, rimmed, y * M3 + x);
    const edge = rs[ang(Math.cos(th), Math.sin(th))] - rimW * 1.5;
    at(cx + Math.cos(th) * Math.min(q, 1.04) * edge, cy + Math.sin(th) * Math.min(q, 1.04) * edge, bare, y * M3 + x);
  }
  return {disc: down(disc, SS), block: down(block, SS), square: down(square, 4), rimmed: down(rimmed, 4), bare: down(bare, 4)};
}

async function timber(sp) {
  let t = (await photoTile(sp.timber, 1.0, TIMBER_N, {delightS: DELIGHT * 0.5, ox: sp.timberOx || 0})).col;
  if (!grainVertical(t)) t = rotate90(t);
  grade(t, {target: sp.timberT, sat: sp.timberSat ?? 0.6, con: 1.5 * (sp.timberCon ?? 1)});
  return t;
}
/**
 * Planks from the timber photo t (grain along v): four boards (one per 60 texels) across v, grain along u. Each board its
 * own strip of the photo (offset across and along), a slow run-out across the grain (5..14 texels per block: the grain
 * breaks at the butt joint), its own tone (0.88..1.10) and warmth (0..+5 % red over blue, never cooler: a cool board
 * reads green); staggered butt joints as before. The seam: a 1.2 texel gap a little darker than the wood and a 2.6 texel
 * soft bevel (relief in _n, never a black line).
 */
function planks(sp, t, seed) {
  const B = TIMBER_N / 4, G = 1.2, BEV = 2.6, px = new Float32Array(3);
  const boards = [36, 156, 96, 216].map((j, k) => ({joint: j + 20 * (hash(k, 5, seed) - 0.5), offA: TIMBER_N * hash(k, 1, seed), offL: TIMBER_N * hash(k, 2, seed),
    run: (hash(k, 3, seed) < 0.5 ? -1 : 1) * (5 + 9 * hash(k, 4, seed)) / TIMBER_N, tone: 0.88 + 0.22 * hash(k, 6, seed), warm: 0.05 * hash(k, 7, seed)}));
  return (x, y) => {
    const row = Math.floor(y / B), k = ((row % 4) + 4) % 4, b = boards[k], ty = y - row * B, xb = ((x - b.joint) % TIMBER_N + TIMBER_N) % TIMBER_N;
    sampleWrap(t, ty + b.offA + b.run * xb, xb + b.offL, px);
    px[1] = Math.min(px[1], px[0] - 0.25 * (px[0] - px[2]));     // brown, never yellow-green (coated_pine has stains)
    let c = [px[0] * (1 + b.warm), px[1], px[2] * (1 - b.warm)].map(v => v * 255 * b.tone);
    const d = Math.min(Math.min(ty, B - ty), Math.min(Math.abs(x - b.joint), TIMBER_N - Math.abs(x - b.joint)));
    let h = 0, ao = 1, sm = 108, p = 30;
    if (d < G) { c = c.map(v => v * 0.72); h = -1.0; ao = 0.8; sm = 60; p = 45; }
    else if (d < G + BEV) { const q = 1 - (d - G) / BEV; h = -0.8 * q * q; c = c.map(v => v * (1 - 0.08 * q * q)); ao = 1 - 0.1 * q; }
    return {c, s: sm, p, h, ao};
  };
}
const rgbaImg = (buf, n) => { const o = img(n, n); for (let i = 0; i < n * n; i++) for (let k = 0; k < 3; k++) o.d[i * 3 + k] = buf[i * 4 + k] / 255; return o; };

/** The notch faces: the planed wood, 128, a step darker (fresh axe cut, not planed). */
function cut(t) {
  const o = down(cropResample(t, 0, 0, t.w * 0.6, CUT_N * 3), 3);
  for (let i = 0; i < o.d.length; i++) o.d[i] *= 0.9;
  return o;
}

async function finish() {
  let t = (await photoTile(FURN.src, 1.0, 240, {delightS: FURN.delight})).col;
  if (grainVertical(t)) t = rotate90(t);           // grain along u like furniture/wood
  grade(t, {target: FURN.T, sat: FURN.sat, con: FURN.con});
  return t;
}

// ------------------------------------------------------------------ outputs
const outputs = [];
const put = (rel, buf) => outputs.push([rel, buf]);
const previews = [];
for (const sp of SPECIES) {
  const b = await bark(sp);
  ['', '_s', '_n'].forEach((k, i) => put(`tree/${sp.id}_bark${k}.png`, b.maps[i]));
  const e = await ends(sp);
  put(`tree/${sp.id}_log_end.png`, colourPng(e.block)); put(`tree/${sp.id}_log_end_s.png`, specPng(END_N, 43, 50)); put(`tree/${sp.id}_log_end_n.png`, flatN(END_N));
  put(`tree/${sp.id}_end_grain.png`, colourPng(e.disc)); put(`tree/${sp.id}_end_grain_s.png`, specPng(END_N, 44, 50)); put(`tree/${sp.id}_end_grain_n.png`, flatN(END_N));
  const t = await timber(sp), c = cut(t);
  put(`wood/${sp.id}_timber_side.png`, colourPng(t)); put(`wood/${sp.id}_timber_side_s.png`, specPng(TIMBER_N, 112, 30)); put(`wood/${sp.id}_timber_side_n.png`, flatN(TIMBER_N));
  put(`tree/${sp.id}_cut.png`, colourPng(c)); put(`tree/${sp.id}_cut_s.png`, specPng(CUT_N, 50, 80)); put(`tree/${sp.id}_cut_n.png`, flatN(CUT_N));
  const pl = render(planks(sp, t, 1100 + 100 * SPECIES.indexOf(sp)));
  ['', '_s', '_n'].forEach((k, i) => put(`wood/${sp.id}_planks${k}.png`, pl.maps[i]));
  put(`wood/${sp.id}_timber_end.png`, colourPng(e.square)); put(`wood/${sp.id}_timber_end_s.png`, specPng(TIMBER_N, 72, 50)); put(`wood/${sp.id}_timber_end_n.png`, flatN(TIMBER_N));
  previews.push({sp, bark: b.col, end: e.block, disc: e.disc, timber: t, planks: rgbaImg(pl.col, TIMBER_N), tend: e.square});
  if (sp.id === 'white_oak') put(path.join(BASES, 'white_oak_log_end_240.png'), colourPng(e.bare));
  if (sp.id === 'red_oak') {
    put(path.join(BASES, 'firewood_end_240.png'), colourPng(e.rimmed));
    put(path.join(BASES, 'firewood_bark_240.png'), colourPng((await bark(sp, TIMBER_N)).col));
  }
}
const fin = await finish();
put(FINISH, colourPng(fin));

let bad = 0;
for (const [rel, buf] of outputs) {
  const file = path.isAbsolute(rel) ? rel : path.join(TEX, rel);
  if (check) {
    const same = fs.existsSync(file) && fs.readFileSync(file).equals(buf);
    if (!same) { bad++; console.log('DIFF', path.relative(ROOT, file)); }
  } else { fs.mkdirSync(path.dirname(file), {recursive: true}); fs.writeFileSync(file, buf); }
}
if (check) { console.log(bad === 0 ? `CHECK OK (${outputs.length} outputs)` : `CHECK FAILED (${bad})`); process.exitCode = bad ? 1 : 0; }
else console.log(`wrote ${outputs.length} outputs`);

if (previewDir) {
  fs.mkdirSync(previewDir, {recursive: true});
  const S = 256, W = 6 * (S + 8) + 8, H = previews.length * (S + 8) + 8, cv = Buffer.alloc(W * H * 4, 34);
  const blit = (im, X, Y) => { for (let y = 0; y < S; y++) for (let x = 0; x < S; x++) { const sx = Math.floor(x / S * im.w), sy = Math.floor(y / S * im.h), o = ((Y + y) * W + X + x) * 4, i = (sy * im.w + sx) * 3;
    for (let k = 0; k < 3; k++) cv[o + k] = Math.round(im.d[i + k] * 255); cv[o + 3] = 255; } };
  previews.forEach((p, j) => [p.bark, p.end, p.disc, p.timber, p.planks, p.tend].forEach((im, i) => blit(im, 8 + i * (S + 8), 8 + j * (S + 8))));
  fs.writeFileSync(path.join(previewDir, 'wood_photo_v1.png'), png(cv, W, H));
  console.log('preview', path.join(previewDir, 'wood_photo_v1.png'));
}
