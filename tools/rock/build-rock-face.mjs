// Rock R2 (docs/gameplay/rock_r2_rock_face_mining_design.md 4): the natural rock's look and the rubble a pick loosens.
//   node tools/rock/build-rock-face.mjs --fetch         -> downloads the rubble photo into build/cc0-rubble (Poly Haven, CC0)
//   node tools/rock/build-rock-face.mjs --preview DIR   -> the sprites and a comparison sheet (vanilla stone, the new stone,
//                                                         rubble, the boulders' granite; flat and lit) into DIR
//   node tools/rock/build-rock-face.mjs --write         -> the game's textures: textures/block/rock/stone_0..3 (+ _n, _s),
//                                                         rubble (+ _n, _s), and minecraft:block/stone (+ _n, _s) = stone_0
// Stone: four seamless 1 m tiles at 128 texels per metre (the trees' bark and the boulders' density) from the CC0
// rock_surface photo (Poly Haven, 2 x 2 m, the one the boulders use, cached in build/cc0-rock by
// tools/boulders/build-boulders.mjs --fetch). Each tile is a 1 m window of the 2 m photo made periodic by blending its four
// half-offset copies (cos^2 weights; every copy is continuous because the photo tiles at 2 m) with the contrast put back
// (the blend's sum of squared weights), at four different windows. Greyer and a little darker than the boulders'
// granite (bedrock under weather, no lichen); a broad mottle of its own per tile, so a face of them does not repeat.
// Rubble: the CC0 rock_ground photo (Poly Haven, 1.5 x 1.5 m: angular broken rock with its grit; build/cc0-rubble, --fetch)
// as a 1 m tile the same way, its brown regraded to the stone's grey, a little lighter (fresh breaks).
// (A procedural power-diagram rubble was tried first, 2026-10-22: it read as crazy paving, not a loose heap.)
// Rock PBR R1 (docs/worldgen/rock_pbr_v1.md 4): the photo's relief made stronger (rock-pbr.mjs relief: 90% of the texels
// lean at most 24 degrees on the stone, 26 on the rubble) with its crevice AO in _n blue.
import fs from 'node:fs';
import path from 'node:path';
import {createRequire} from 'node:module';
import {fileURLToPath} from 'node:url';
import {hash, V} from '../boulders/boulder-shapes.mjs';
import {relief, encodeN} from './rock-pbr.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const CACHE = path.join(ROOT, 'build/cc0-rock'), RUBBLE_CACHE = path.join(ROOT, 'build/cc0-rubble');
/** The rubble photo: rock_ground (Poly Haven, CC0, 1.5 x 1.5 m) at 128 texels per metre. */
const GRES = 192;
const PH = 'https://dl.polyhaven.org/file/ph-assets/Textures/jpg/1k';
/** The photo at 128 texels per metre (2 m), and one game tile (1 m). */
const PRES = 256, RES = 128;

function loadSharp() {
  for (const p of [path.join(ROOT, 'wiki/package.json'), path.resolve(ROOT, '../Apocalypse First Light/wiki/package.json')]) {
    try { return createRequire(p)('sharp'); } catch { /* next */ }
  }
  throw new Error('sharp not found: run npm --prefix wiki install');
}

/**
 * A tiling photo at n texels (its size in metres times 128), channels 0..1: averaged down in whole blocks where n divides
 * it (exact, no seam), else resampled on a 3 x 3 tiled copy and cut from the middle (no edge seam either).
 */
async function photo(sharp, file, n, grey = false) {
  const s = sharp(file), meta = await s.metadata(), c = grey ? 1 : 3, o = new Float32Array(n * n * c);
  const img = grey ? s.greyscale() : s.removeAlpha();
  if (meta.width % n === 0) {
    const {data, info} = await img.raw().toBuffer({resolveWithObject: true});
    const k = info.width / n;
    for (let y = 0; y < n; y++) for (let x = 0; x < n; x++) for (let ch = 0; ch < c; ch++) {
      let s2 = 0;
      for (let j = 0; j < k; j++) for (let i = 0; i < k; i++) s2 += data[((y * k + j) * info.width + x * k + i) * info.channels + ch];
      o[(y * n + x) * c + ch] = s2 / (k * k * 255);
    }
    return o;
  }
  const one = await img.raw().toBuffer({resolveWithObject: true}), W = one.info.width, ch0 = one.info.channels;
  const big = Buffer.alloc(W * 3 * W * 3 * ch0);
  for (let ty = 0; ty < 3; ty++) for (let y = 0; y < W; y++) for (let tx = 0; tx < 3; tx++)
    one.data.copy(big, ((ty * W + y) * W * 3 + tx * W) * ch0, y * W * ch0, (y + 1) * W * ch0);
  const {data, info} = await sharp(big, {raw: {width: W * 3, height: W * 3, channels: ch0}}).resize(n * 3, n * 3, {kernel: 'lanczos3'})
    .extract({left: n, top: n, width: n, height: n}).raw().toBuffer({resolveWithObject: true});
  for (let i = 0; i < n * n; i++) for (let ch = 0; ch < c; ch++) o[i * c + ch] = data[i * info.channels + ch] / 255;
  return o;
}

/** Box blur of a 1-channel n x n tile that wraps, radius r, four passes (close to a Gaussian). */
function blurWrap(a, n, r) {
  let src = a;
  for (let pass = 0; pass < 4; pass++) {
    const dst = new Float32Array(src.length), hor = pass % 2 === 0;
    for (let y = 0; y < n; y++) for (let x = 0; x < n; x++) {
      let s = 0;
      for (let d = -r; d <= r; d++) s += hor ? src[y * n + (x + d + n) % n] : src[((y + d + n) % n) * n + x];
      dst[y * n + x] = s / (2 * r + 1);
    }
    src = dst;
  }
  return src;
}

/** Periodic value noise over a RES tile (cells of c texels, c divides RES), 0..1. */
function pnoise(x, y, c, seed) {
  const n = RES / c, xi = Math.floor(x / c), yi = Math.floor(y / c), s = t => t * t * (3 - 2 * t);
  const h = (i, j) => hash(((i % n) + n) % n, ((j % n) + n) % n, seed);
  const tx = s(x / c - xi), ty = s(y / c - yi);
  const a = h(xi, yi) + (h(xi + 1, yi) - h(xi, yi)) * tx, b = h(xi, yi + 1) + (h(xi + 1, yi + 1) - h(xi, yi + 1)) * tx;
  return a + (b - a) * ty;
}
const lum = (d, i) => 0.2126 * d[i * 3] + 0.7152 * d[i * 3 + 1] + 0.0722 * d[i * 3 + 2];
const clamp01 = v => Math.max(0, Math.min(1, v));

/**
 * A RES tile, periodic, of an n x n tiling photo channel set (c channels) from the window at (ox, oy): four copies
 * blended by cos^2 / sin^2 in x and y, the second n - RES further on (so at the tile's far edge it is the first copy at its
 * near edge again), their deviations from the mean scaled back by 1 / sqrt(sum w^2).
 */
function window(src, c, ox, oy, mean, n = PRES) {
  const d = n - RES;
  const out = new Float32Array(RES * RES * c);
  for (let y = 0; y < RES; y++) for (let x = 0; x < RES; x++) {
    const cx = Math.cos(Math.PI * (x + 0.5) / (2 * RES)) ** 2, cy = Math.cos(Math.PI * (y + 0.5) / (2 * RES)) ** 2;
    const w = [[cx * cy, 0, 0], [(1 - cx) * cy, d, 0], [cx * (1 - cy), 0, d], [(1 - cx) * (1 - cy), d, d]];
    const norm = Math.sqrt(w.reduce((a, [k]) => a + k * k, 0));
    for (let ch = 0; ch < c; ch++) {
      let s = 0;
      for (const [k, dx, dy] of w) {
        const px = (ox + x + dx) % n, py = (oy + y + dy) % n;
        s += k * (src[(py * n + px) * c + ch] - mean[ch]);
      }
      out[(y * RES + x) * c + ch] = mean[ch] + s / norm;
    }
  }
  return out;
}

function means(a, c) {
  const m = new Array(c).fill(0);
  for (let i = 0; i < a.length; i++) m[i % c] += a[i];
  return m.map(v => v / (a.length / c));
}

/** Weathered bedrock grey (sRGB 0..1): greyer and a little darker than the boulders' granite (0.53, 0.52, 0.50). */
const STONE = [0.47, 0.47, 0.465];
/** The four tiles: photo window, brightness, mottle seed. */
const TILES = [[0, 0, 1.0, 11], [64, 160, 0.975, 12], [176, 48, 1.02, 13], [112, 208, 0.99, 14]];

async function sources(sharp) {
  const f = name => path.join(CACHE, name), g = name => path.join(RUBBLE_CACHE, name);
  const rs = await photo(sharp, f('rock_surface_diff_1k.jpg'), PRES), rn = await photo(sharp, f('rock_surface_nor_gl_1k.jpg'), PRES);
  const rough = await photo(sharp, f('rock_surface_rough_1k.jpg'), PRES, true);
  const gs = await photo(sharp, g('rock_ground_diff_1k.jpg'), GRES), gn = await photo(sharp, g('rock_ground_nor_gl_1k.jpg'), GRES);
  const grough = await photo(sharp, g('rock_ground_rough_1k.jpg'), GRES, true);
  return {rs, rn, rough, mrs: means(rs, 3), mrn: means(rn, 3), mrough: means(rough, 1),
    gs, gn, grough, mgs: means(gs, 3), mgn: means(gn, 3), mgrough: means(grough, 1)};
}

/** One stone tile: {col (RES*RES*3, 0..1), nrm (OpenGL unit), png: {albedo, s, n}}. */
function stone(src, [ox, oy, gain, seed]) {
  const rs = window(src.rs, 3, ox, oy, src.mrs), rn = window(src.rn, 3, ox, oy, src.mrn), rough = window(src.rough, 1, ox, oy, src.mrough);
  const L = new Float32Array(RES * RES).map((_, i) => lum(rs, i)), mean = L.reduce((a, v) => a + v, 0) / L.length;
  const low = blurWrap(L, RES, 6);
  const col = new Float32Array(RES * RES * 3), nrm = new Float32Array(RES * RES * 3);
  const albedo = Buffer.alloc(RES * RES * 4), sB = Buffer.alloc(RES * RES * 4);
  for (let y = 0; y < RES; y++) for (let x = 0; x < RES; x++) {
    const i = y * RES + x;
    const detail = (L[i] - low[i]) / mean, mott = low[i] / mean - 1;
    // the tile's own broad weathering (two octaves, periodic): a face of tiles reads as one rock
    const broad = 0.6 * pnoise(x, y, 64, seed) + 0.4 * pnoise(x, y, 32, seed + 50) - 0.5;
    const v = 1 + 2.2 * detail + 0.5 * mott + 0.16 * broad;
    const g = hash(x, y, seed + 90), grain = g < 0.04 ? 0.84 : g > 0.97 ? 1.1 : 1;
    const c = STONE.map((b, k) => clamp01(b * gain * v * grain + 0.1 * (rs[i * 3 + k] - L[i])));
    col.set(c, i * 3);
    const n = V.norm([(rn[i * 3] * 2 - 1) * 0.9, (rn[i * 3 + 1] * 2 - 1) * 0.9, rn[i * 3 + 2] * 2 - 1]);
    nrm.set(n, i * 3);
    albedo.set(c.map(a => Math.round(a * 255)).concat(255), i * 4);
    sB.set([Math.round((1 - clamp01(rough[i])) * 150), 10, 48, 255], i * 4);
  }
  const r = relief(nrm, RES, 24);
  return {col, nrm: r.nrm, png: {albedo, s: sB, n: encodeN(r.nrm, r.ao, RES)}};
}

/** Rubble from the rock_ground photo: its luminance (contrast kept) on the stone's grey, a trace of its own colour. */
function rubble(src) {
  const gs = window(src.gs, 3, 0, 0, src.mgs, GRES), gn = window(src.gn, 3, 0, 0, src.mgn, GRES), rough = window(src.grough, 1, 0, 0, src.mgrough, GRES);
  const L = new Float32Array(RES * RES).map((_, i) => lum(gs, i)), mean = L.reduce((a, v) => a + v, 0) / L.length;
  // the photo's broad light and dark (a footpath's wear) would repeat block by block: only the pieces' own detail stays,
  // its darkest holes softened
  const low = blurWrap(L, RES, 8);
  // the same for the relief: the footpath's broad dents go, the pieces' edges stay
  const nx = blurWrap(new Float32Array(RES * RES).map((_, i) => gn[i * 3]), RES, 8), ny = blurWrap(new Float32Array(RES * RES).map((_, i) => gn[i * 3 + 1]), RES, 8);
  const col = new Float32Array(RES * RES * 3), nrm = new Float32Array(RES * RES * 3);
  const albedo = Buffer.alloc(RES * RES * 4), sB = Buffer.alloc(RES * RES * 4);
  for (let i = 0; i < RES * RES; i++) {
    const d = 1.5 * (L[i] - low[i]) / mean, v = 1 + (d < 0 ? -0.45 * Math.tanh(-d / 0.45) : d);
    const c = STONE.map((b, k) => clamp01(b * 1.04 * v + 0.08 * (gs[i * 3 + k] - L[i])));
    col.set(c, i * 3);
    const n = V.norm([(gn[i * 3] - nx[i]) * 1.4, (gn[i * 3 + 1] - ny[i]) * 1.4, gn[i * 3 + 2] * 2 - 1]);
    nrm.set(n, i * 3);
    albedo.set(c.map(a => Math.round(a * 255)).concat(255), i * 4);
    sB.set([Math.round((1 - clamp01(rough[i])) * 150), 10, 48, 255], i * 4);
  }
  const r = relief(nrm, RES, 26);
  return {col, nrm: r.nrm, png: {albedo, s: sB, n: encodeN(r.nrm, r.ao, RES)}};
}

/** RGB float tile of a game PNG (scaled to `to` texels, nearest), with flat normals unless an _n exists beside it. */
async function loadTile(sharp, file, to) {
  const {data, info} = await sharp(file).removeAlpha().resize(to, to, {kernel: 'nearest'}).raw().toBuffer({resolveWithObject: true});
  const col = new Float32Array(to * to * 3).map((_, i) => data[i] / 255);
  const nf = file.replace(/\.png$/, '_n.png'), nrm = new Float32Array(to * to * 3);
  if (fs.existsSync(nf)) {
    const n = (await sharp(nf).removeAlpha().resize(to, to, {kernel: 'nearest'}).raw().toBuffer());
    for (let i = 0; i < to * to; i++) nrm.set(V.norm([n[i * 3] / 127.5 - 1, -(n[i * 3 + 1] / 127.5 - 1), 1]), i * 3);
  } else for (let i = 0; i < to * to; i++) nrm.set([0, 0, 1], i * 3);
  return {col, nrm, res: to};
}

/**
 * A panel: P x P texels of blocks of tiles (pick(bx, by) -> {tile, rot, mirror}), flat or lit by a low sun from the
 * north-west (the normal map's relief), as RGBA.
 */
function panel(P, block, pick, lit) {
  const out = Buffer.alloc(P * P * 4), sun = V.norm([-0.6, 0.55, 0.58]);
  for (let y = 0; y < P; y++) for (let x = 0; x < P; x++) {
    const bx = Math.floor(x / block), by = Math.floor(y / block), {tile, rot = 0, mirror = false} = pick(bx, by);
    const r = tile.res ?? RES;
    let u = (x % block) * r / block, v = (y % block) * r / block;
    if (mirror) u = r - 1 - u;
    for (let k = 0; k < rot; k++) [u, v] = [r - 1 - v, u];
    const i = Math.floor(v) * r + Math.floor(u) % r;
    let n = [tile.nrm[i * 3], tile.nrm[i * 3 + 1], tile.nrm[i * 3 + 2]];
    if (mirror) n[0] = -n[0];
    for (let k = 0; k < rot; k++) n = [-n[1], n[0], n[2]];
    const f = lit ? 0.3 + 0.95 * Math.max(0, V.dot(n, sun)) : 1;
    for (let ch = 0; ch < 3; ch++) out[(y * P + x) * 4 + ch] = Math.round(clamp01(tile.col[i * 3 + ch] * f) * 255);
    out[(y * P + x) * 4 + 3] = 255;
  }
  return out;
}

async function sheet(sharp, file, tiles, vanilla, granite) {
  const P = 512, block = 128, gap = 16, head = 40;
  const random = (bx, by) => { const h = hash(bx, by, 900); return {tile: tiles[Math.floor(h * 4)], rot: (hash(bx, by, 901) < 0.5 ? 0 : 2), mirror: hash(bx, by, 902) < 0.5}; };
  const cols = [
    ['原版石头', () => ({tile: vanilla})],
    ['新石头（4 张，随机旋转、镜像）', random],
    ['碎石', () => ({tile: tiles.rubble})],
    ['巨石花岗岩（对照）', (bx, by) => ({tile: granite, part: [bx, by]})],
  ];
  const W = cols.length * P + (cols.length + 1) * gap, Hh = 2 * (P + head) + 3 * gap;
  const comp = [];
  for (let c = 0; c < cols.length; c++) for (let row = 0; row < 2; row++) {
    const [, pick] = cols[c];
    let buf;
    if (c === 3) {
      // the boulders' sprite is 2 m: four blocks of it per tile
      const g = granite;
      buf = panel(P, block * 2, () => ({tile: g}), row === 1);
    } else buf = panel(P, block, pick, row === 1);
    comp.push({input: await sharp(buf, {raw: {width: P, height: P, channels: 4}}).png().toBuffer(), left: gap + c * (P + gap), top: gap + head + row * (P + head + gap)});
  }
  const text = [];
  for (let c = 0; c < cols.length; c++) for (let row = 0; row < 2; row++)
    text.push(`<text x="${gap + c * (P + gap)}" y="${gap + 28 + row * (P + head + gap)}" font-family="Microsoft YaHei, sans-serif" font-size="22" fill="#e8e8e8">${cols[c][0]}${row ? '：斜光照' : '：平光'}</text>`);
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${Hh}">${text.join('')}</svg>`;
  comp.push({input: Buffer.from(svg), left: 0, top: 0});
  await sharp({create: {width: W, height: Hh, channels: 4, background: '#202224'}}).composite(comp).png().toFile(file);
}

const args = process.argv.slice(2);
if (args.includes('--fetch')) {
  fs.mkdirSync(RUBBLE_CACHE, {recursive: true});
  for (const m of ['diff', 'nor_gl', 'rough']) {
    const name = 'rock_ground_' + m + '_1k.jpg', r = await fetch(PH + '/rock_ground/' + name);
    if (!r.ok) throw new Error(name + ': HTTP ' + r.status);
    fs.writeFileSync(path.join(RUBBLE_CACHE, name), Buffer.from(await r.arrayBuffer()));
  }
  console.log('fetched rock_ground into ' + RUBBLE_CACHE + ' (rock_surface: tools/boulders/build-boulders.mjs --fetch)');
}
const sharp = loadSharp();
const src = await sources(sharp);
const tiles = TILES.map(t => stone(src, t));
tiles.rubble = rubble(src);
const write = async (dir, name, t) => {
  fs.mkdirSync(dir, {recursive: true});
  for (const [k, b] of Object.entries(t.png)) await sharp(b, {raw: {width: RES, height: RES, channels: 4}}).png().toFile(path.join(dir, `${name}${k === 'albedo' ? '' : '_' + k}.png`));
};
for (let i = 0; i < 4; i++) {
  const m = means(tiles[i].col, 3);
  console.log(`stone_${i}: mean ${m.map(v => v.toFixed(3)).join(' ')}`);
}
console.log(`rubble: mean ${means(tiles.rubble.col, 3).map(v => v.toFixed(3)).join(' ')}`);
const pi = args.indexOf('--preview');
if (pi >= 0) {
  const dir = path.resolve(args[pi + 1]);
  for (let i = 0; i < 4; i++) await write(dir, `stone_${i}`, tiles[i]);
  await write(dir, 'rubble', tiles.rubble);
  const res = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight/textures/block/boulder');
  const vanilla = await loadTile(sharp, path.join(ROOT, 'build/vanilla-ref/assets/minecraft/textures/block/stone.png'), 16);
  const granite = await loadTile(sharp, path.join(res, 'granite_worn.png'), 256);
  await sheet(sharp, path.join(dir, 'rock_r2_compare.png'), tiles, vanilla, granite);
  console.log(`preview in ${dir}`);
}
if (args.includes('--write')) {
  const res = path.join(ROOT, 'src/main/resources/assets');
  const rock = path.join(res, 'apocalypse_firstlight/textures/block/rock');
  for (let i = 0; i < 4; i++) await write(rock, `stone_${i}`, tiles[i]);
  await write(rock, 'rubble', tiles.rubble);
  await write(path.join(res, 'minecraft/textures/block'), 'stone', tiles[0]);
  console.log('wrote textures/block/rock/stone_0..3, rubble and minecraft:block/stone');
}
