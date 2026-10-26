// Natural ground N1a (docs/worldgen/natural_ground_n1_design.md 5, 7): the CC0 photo candidates for AFL's soils, one
// comparison sheet per material for the user to choose from (as rock R3 did).
//   node tools/ground/build-natural-ground.mjs --fetch         -> downloads every candidate's colour and OpenGL normal map
//                                                                into build/cc0-ground (Poly Haven and ambientCG, both CC0)
//   node tools/ground/build-natural-ground.mjs --preview DIR   -> DIR/n1a_<material>.png: per candidate, the photo at 128
//                                                                texels per metre over 3 x 3 m, flat, lit by a low sun, and
//                                                                lit beside AFL's natural stone (a pit's rim)
//   node tools/ground/build-natural-ground.mjs --write         -> N1b: the game's textures textures/block/ground/<look>_0..3
//                                                                (+ _n, _s), the block and item models and the blockstates
//   node tools/ground/build-natural-ground.mjs --tiles DIR     -> DIR/n1b_tiles.png: the game tiles, 3 x 3 m of random tiles
//                                                                per look, flat and lit
// Photos are used at their real size (Poly Haven's and ambientCG's dimensions), so the sheets show the grain a block will
// have. They are the raw photos: the game tiles (N1b) take 1 m windows, flatten the broad light, regrade the colour and
// add the PBR, like tools/rock/build-rock-face.mjs.
import fs from 'node:fs';
import path from 'node:path';
import {execFileSync} from 'node:child_process';
import {createRequire} from 'node:module';
import {fileURLToPath} from 'node:url';
import {hash, V} from '../boulders/boulder-shapes.mjs';
import {relief, encodeN} from '../rock/rock-pbr.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const CACHE = path.join(ROOT, 'build/cc0-ground');
const STONE = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight/textures/block/rock/stone_0.png');
const PH_API = 'https://api.polyhaven.com/files';
const ACG = 'https://ambientcg.com/get?file=';
/** Texels per metre (the rock's and the trees' density). */
const TPM = 128;

/** Per material: the sheet's name, its Chinese title, and its candidates [source, asset id, size in metres]. */
const MATERIALS = [
  ['bare', '表土：裸土（bare，也是表土的侧面）', [['ph', 'dirt', 2], ['ph', 'forest_ground_05', 2], ['ph', 'forest_ground_06', 2.11], ['ph', 'dirt_floor', 2.07]]],
  ['grass', '表土：草皮（grass，以后按生物群系着色）', [['ph', 'leafy_grass', 2], ['ph', 'sparse_grass', 2], ['acg', 'Grass004', 1.4], ['acg', 'Ground037', 2.1]]],
  ['litter', '表土：落叶层（litter）', [['ph', 'forest_leaves_03', 2.3], ['ph', 'forest_leaves_04', 1.5], ['ph', 'forrest_ground_03', 2], ['ph', 'forest_floor', 2.14]]],
  ['moss', '表土：苔藓（moss）', [['ph', 'forest_leaves_02', 3], ['ph', 'brown_mud_leaves_01', 1.3], ['acg', 'Moss001', 0.45]]],
  ['mud', '泥', [['ph', 'brown_mud', 1.3], ['ph', 'brown_mud_02', 1.3], ['ph', 'mud_forest', 2.35], ['ph', 'rocks_ground_08', 3]]],
  ['sand', '沙', [['ph', 'sand_02', 2.14], ['ph', 'sand_03', 2], ['ph', 'dense_sand', 1.8], ['ph', 'damp_sand', 2.04]]],
  ['red_sand', '红沙（阿尔塔梅萨）', [['ph', 'red_sand', 3], ['ph', 'cracked_red_ground', 2], ['ph', 'red_mud_stones', 1.5]]],
  ['gravel_round', '砾石：圆砾（河床）', [['ph', 'river_small_rocks', 2.9], ['ph', 'ganges_river_pebbles', 2.16], ['ph', 'rocks_ground_04', 2], ['ph', 'gravel_stones', 2]]],
  ['gravel_angular', '砾石：棱角砾（山地）', [['ph', 'rocks_ground_09', 3], ['ph', 'gravel_floor_02', 2], ['ph', 'rocks_ground_06', 3], ['ph', 'rocky_trail', 2]]],
  ['clay', '黏土（照片会调成灰蓝色）', [['ph', 'mud_cracked_dry_03', 1.5], ['ph', 'mud_cracked_dry_riverbed_002', 2], ['ph', 'sand_01', 1.5]]],
  ['snow', '积雪', [['ph', 'snow_01', 2], ['ph', 'snow_02', 2], ['ph', 'snow_04', 4]]],
  ['red_sandstone', '红砂岩外观（劣地红层）', [['ph', 'rock_pitted_mossy', 1], ['ph', 'rock_boulder_cracked', 1.39], ['ph', 'worn_rock_natural_01', 2]]],
];

const files = (src, id) => src === 'ph'
  ? {col: path.join(CACHE, `${id}_diff_1k.jpg`), nrm: path.join(CACHE, `${id}_nor_gl_1k.jpg`)}
  : {col: path.join(CACHE, `${id}_1K-JPG_Color.jpg`), nrm: path.join(CACHE, `${id}_1K-JPG_NormalGL.jpg`)};

function loadSharp() {
  for (const p of [path.join(ROOT, 'wiki/package.json'), path.resolve(ROOT, '../Apocalypse First Light/wiki/package.json')]) {
    try { return createRequire(p)('sharp'); } catch { /* next */ }
  }
  throw new Error('sharp not found: run npm --prefix wiki install');
}

async function fetchAll() {
  fs.mkdirSync(CACHE, {recursive: true});
  for (const [, , list] of MATERIALS) for (const [src, id] of list) {
    const f = files(src, id);
    if (fs.existsSync(f.col) && fs.existsSync(f.nrm)) continue;
    if (src === 'ph') {
      // the file names are not all alike (diff / diffuse): the files API gives each map's own URL
      const maps = await (await fetch(`${PH_API}/${id}`)).json();
      for (const [m, file] of [['Diffuse', f.col], ['nor_gl', f.nrm]]) {
        const url = maps[m]?.['1k']?.jpg?.url;
        if (!url) throw new Error(`${id}: no 1k ${m} jpg`);
        const r = await fetch(url);
        if (!r.ok) throw new Error(`${id} ${m}: HTTP ${r.status}`);
        fs.writeFileSync(file, Buffer.from(await r.arrayBuffer()));
      }
    } else {
      // ambientCG ships a zip per resolution: keep only its colour and OpenGL normal map
      const r = await fetch(`${ACG}${id}_1K-JPG.zip`);
      if (!r.ok) throw new Error(`${id}: HTTP ${r.status}`);
      const zip = path.join(CACHE, `${id}_1K-JPG.zip`);
      fs.writeFileSync(zip, Buffer.from(await r.arrayBuffer()));
      execFileSync('unzip', ['-o', '-j', zip, path.basename(f.col), path.basename(f.nrm), '-d', CACHE], {stdio: 'pipe'});
      fs.rmSync(zip);
    }
    console.log('fetched', src, id);
  }
}

/** A tiling photo at n x n texels (channels 0..1, RGB): resampled on a 3 x 3 tiled copy, cut from the middle (no edge seam). */
async function photo(sharp, file, n) {
  const one = await sharp(file).removeAlpha().raw().toBuffer({resolveWithObject: true}), W = one.info.width, H = one.info.height, c = one.info.channels;
  const big = Buffer.alloc(W * 3 * H * 3 * c);
  for (let ty = 0; ty < 3; ty++) for (let y = 0; y < H; y++) for (let tx = 0; tx < 3; tx++)
    one.data.copy(big, ((ty * H + y) * W * 3 + tx * W) * c, y * W * c, (y + 1) * W * c);
  const {data, info} = await sharp(big, {raw: {width: W * 3, height: H * 3, channels: c}}).resize(n * 3, n * 3, {kernel: 'lanczos3'})
    .extract({left: n, top: n, width: n, height: n}).raw().toBuffer({resolveWithObject: true});
  const o = new Float32Array(n * n * 3);
  for (let i = 0; i < n * n; i++) for (let ch = 0; ch < 3; ch++) o[i * 3 + ch] = data[i * info.channels + ch] / 255;
  return o;
}

/** A candidate as {col, nrm (unit, OpenGL), res}: its photo at 128 texels per metre of its real size. */
async function candidate(sharp, src, id, size) {
  const f = files(src, id), res = Math.round(size * TPM);
  const col = await photo(sharp, f.col, res), n = await photo(sharp, f.nrm, res), nrm = new Float32Array(res * res * 3);
  for (let i = 0; i < res * res; i++) nrm.set(V.norm([n[i * 3] * 2 - 1, n[i * 3 + 1] * 2 - 1, n[i * 3 + 2] * 2 - 1]), i * 3);
  return {col, nrm, res};
}

/** AFL's natural stone tile (1 m, with its _n: RG the normal, y stored down as rock-pbr.mjs encodeN writes it), for the rim panel. */
async function stoneTile(sharp) {
  const c = await sharp(STONE).removeAlpha().raw().toBuffer({resolveWithObject: true}), r = c.info.width;
  const n = await sharp(STONE.replace(/\.png$/, '_n.png')).ensureAlpha().raw().toBuffer();
  const col = new Float32Array(r * r * 3), nrm = new Float32Array(r * r * 3);
  for (let i = 0; i < r * r; i++) {
    for (let ch = 0; ch < 3; ch++) col[i * 3 + ch] = c.data[i * c.info.channels + ch] / 255;
    const x = n[i * 4] / 127.5 - 1, y = -(n[i * 4 + 1] / 127.5 - 1);
    nrm.set([x, y, Math.sqrt(Math.max(0, 1 - x * x - y * y))], i * 3);
  }
  return {col, nrm, res: r};
}

const clamp01 = v => Math.max(0, Math.min(1, v));
/** A low sun from the north-west, as the rock sheets. */
const SUN = V.norm([-0.6, 0.55, 0.58]);

/** P x P texels (P / 128 metres) of tiles, flat or lit; the right half of the rim panel is the stone. */
function panel(P, tile, mode, stone) {
  const out = Buffer.alloc(P * P * 4);
  for (let y = 0; y < P; y++) for (let x = 0; x < P; x++) {
    const t = mode === 'rim' && x >= P / 2 ? stone : tile;
    const i = (y % t.res) * t.res + (x % t.res);
    const n = [t.nrm[i * 3], t.nrm[i * 3 + 1], t.nrm[i * 3 + 2]];
    const f = mode === 'flat' ? 1 : 0.3 + 0.95 * Math.max(0, V.dot(n, SUN));
    for (let ch = 0; ch < 3; ch++) out[(y * P + x) * 4 + ch] = Math.round(clamp01(t.col[i * 3 + ch] * f) * 255);
    out[(y * P + x) * 4 + 3] = 255;
  }
  return out;
}

async function sheet(sharp, dir, [name, title, list], stone) {
  const P = 384, gap = 16, head = 64, rowHead = 30;
  const rows = [['flat', '平光'], ['lit', '斜光'], ['rim', '斜光，右半是 AFL 岩石']];
  const W = list.length * P + (list.length + 1) * gap, H = head + rows.length * (P + rowHead + gap) + gap;
  const comp = [], text = [`<text x="${gap}" y="40" font-family="Microsoft YaHei, sans-serif" font-size="28" fill="#f0f0f0">${title}　每格 3 × 3 m，每米 128 像素</text>`];
  for (let c = 0; c < list.length; c++) {
    const [src, id, size] = list[c], tile = await candidate(sharp, src, id, size), left = gap + c * (P + gap);
    for (let r = 0; r < rows.length; r++) {
      const top = head + r * (P + rowHead + gap) + rowHead;
      comp.push({input: await sharp(panel(P, tile, rows[r][0], stone), {raw: {width: P, height: P, channels: 4}}).png().toBuffer(), left, top});
      text.push(`<text x="${left}" y="${top - 8}" font-family="Microsoft YaHei, sans-serif" font-size="19" fill="#d8d8d8">${String.fromCharCode(65 + c)}　${r ? rows[r][1] : `${id}（${size} m，${src === 'ph' ? 'Poly Haven' : 'ambientCG'}）`}</text>`);
    }
  }
  comp.push({input: Buffer.from(`<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}">${text.join('')}</svg>`), left: 0, top: 0});
  const file = path.join(dir, `n1a_${name}.png`);
  await sharp({create: {width: W, height: H, channels: 4, background: '#202224'}}).composite(comp).png().toFile(file);
  console.log('wrote', file);
}

// ------------------------------------------------------------------------------------------------- N1b: the game tiles

/** One game tile: 1 m at 128 texels per metre. */
const RES = 128;
const GAME = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight');

/**
 * The game looks (user 2026-10-24 picked one photo per material, docs/worldgen/natural_ground_n1_design.md 9): photo
 * [source, id, metres]; color: the tile's mean (sRGB 0..1, North American ground; grass a grey the biome colour tints, as
 * vanilla's grass top); chroma: how much of the photo's own colour variation stays; detail: the fine luminance's gain;
 * procedural: periodic value noise in place of the photo (snow); tilt: the relief's 90th-percentile lean in degrees (rock-pbr.mjs
 * relief); smooth: LabPBR smoothness bytes [low, high] across the detail; b: _s blue (porosity 0..64, subsurface 65..255).
 */
const LOOKS = {
  soil: {photo: ['ph', 'dirt', 2], color: [0.37, 0.29, 0.21], chroma: 0.5, detail: 1.6, tilt: 14, smooth: [20, 40], b: 60},
  grass: {photo: ['acg', 'Grass004', 1.4], color: [0.50, 0.50, 0.50], chroma: 0, detail: 1.4, tilt: 10, smooth: [30, 60], b: 75},
  litter: {photo: ['ph', 'forest_leaves_03', 2.3], color: [0.33, 0.26, 0.17], chroma: 0.35, detail: 0.9, tilt: 14, smooth: [20, 40], b: 60},
  moss: {photo: ['acg', 'Moss001', 0.45], color: [0.32, 0.37, 0.17], chroma: 0.4, detail: 1.5, tilt: 10, smooth: [20, 40], b: 75},
  mud: {photo: ['ph', 'brown_mud', 1.3], color: [0.27, 0.22, 0.17], chroma: 0.4, detail: 1.5, tilt: 10, smooth: [90, 150], b: 20},
  sand: {photo: ['ph', 'dense_sand', 1.8], color: [0.76, 0.69, 0.54], chroma: 0.4, detail: 1.3, tilt: 12, smooth: [15, 30], b: 45},
  red_sand: {photo: ['ph', 'red_sand', 3], color: [0.62, 0.40, 0.28], chroma: 0.4, detail: 2.0, tilt: 12, smooth: [15, 30], b: 45},
  gravel_round: {photo: ['ph', 'ganges_river_pebbles', 2.16], color: [0.50, 0.48, 0.45], chroma: 0.8, detail: 1.1, tilt: 22, smooth: [40, 80], b: 40},
  gravel_angular: {photo: ['ph', 'rocky_trail', 2], color: [0.48, 0.46, 0.43], chroma: 0.25, detail: 1.5, tilt: 22, smooth: [40, 80], b: 40},
  clay: {photo: ['ph', 'sand_01', 1.5], color: [0.50, 0.52, 0.55], chroma: 0.15, detail: 0.8, tilt: 10, smooth: [50, 90], b: 30},
  // snow_02's marks are in its colour and relief and repeat block by block (round 1, 2026-10-24): fine procedural grain instead
  snow: {photo: ['ph', 'snow_02', 2], procedural: true, color: [0.93, 0.95, 0.98], chroma: 0, detail: 0.9, tilt: 7, smooth: [60, 110], b: 64},
};
/**
 * The four tiles of a look: where their window starts in the photo (share of its size), and a brightness each (small:
 * more and a field of them shows the block grid, round 1 2026-10-24).
 */
const WINDOWS = [[0, 0, 1], [0.27, 0.61, 0.992], [0.68, 0.19, 1.008], [0.43, 0.86, 0.996]];
/** The blocks of one look each (cube_all, 4 tiles, plain and mirrored at 0 and 180 degrees), and topsoil's covers. */
const CUBES = ['mud', 'sand', 'red_sand', 'clay', 'snow'];
const COVERS = {grass: 'grass', litter: 'litter', moss: 'moss', bare: 'soil'};

const lumOf = (d, i) => 0.2126 * d[i * 3] + 0.7152 * d[i * 3 + 1] + 0.0722 * d[i * 3 + 2];
const mod = (a, n) => ((a % n) + n) % n;

/** Box blur of a 1-channel RES tile that wraps, radius r, four passes (close to a Gaussian). */
function blurWrap(a, r) {
  let src = a;
  for (let pass = 0; pass < 4; pass++) {
    const dst = new Float32Array(src.length), hor = pass % 2 === 0;
    for (let y = 0; y < RES; y++) for (let x = 0; x < RES; x++) {
      let s = 0;
      for (let d = -r; d <= r; d++) s += hor ? src[y * RES + mod(x + d, RES)] : src[mod(y + d, RES) * RES + x];
      dst[y * RES + x] = s / (2 * r + 1);
    }
    src = dst;
  }
  return src;
}

function means(a, c) {
  const m = new Array(c).fill(0);
  for (let i = 0; i < a.length; i++) m[i % c] += a[i];
  return m.map(v => v / (a.length / c));
}

/**
 * A RES tile, periodic, cut from an n x n tiling photo (c channels) at (ox, oy): four copies blended by cos^2 in x and y,
 * the second n - RES further on (modulo n, so a photo smaller than a tile works too: the moss), their deviations from the
 * mean scaled back by 1 / sqrt(sum w^2) (as build-rock-face.mjs window).
 */
function window(src, c, n, ox, oy, mean) {
  const d = n - RES, out = new Float32Array(RES * RES * c);
  for (let y = 0; y < RES; y++) for (let x = 0; x < RES; x++) {
    const cx = Math.cos(Math.PI * (x + 0.5) / (2 * RES)) ** 2, cy = Math.cos(Math.PI * (y + 0.5) / (2 * RES)) ** 2;
    const w = [[cx * cy, 0, 0], [(1 - cx) * cy, d, 0], [cx * (1 - cy), 0, d], [(1 - cx) * (1 - cy), d, d]];
    const norm = Math.sqrt(w.reduce((a, [k]) => a + k * k, 0));
    for (let ch = 0; ch < c; ch++) {
      let s = 0;
      for (const [k, dx, dy] of w) s += k * (src[(mod(oy + y + dy, n) * n + mod(ox + x + dx, n)) * c + ch] - mean[ch]);
      out[(y * RES + x) * c + ch] = mean[ch] + s / norm;
    }
  }
  return out;
}

/** A look's photo at 128 texels per metre of its real size, and its channel means. */
async function lookSource(sharp, look) {
  const [src, id, size] = look.photo, f = files(src, id), n = Math.round(size * TPM);
  const col = await photo(sharp, f.col, n), nrm = await photo(sharp, f.nrm, n);
  return {n, col, nrm, mcol: means(col, 3), mnrm: means(nrm, 3)};
}

/**
 * One game tile of a look: the photo's fine luminance (its broad light and dark flattened: it would repeat block by
 * block) on the look's colour, a share of the photo's own colour variation, the relief made to the look's lean, the
 * smoothness following the detail. Returns {col, nrm, res, png: {albedo, s, n}}.
 */
function gameTile(src, look, k) {
  if (look.procedural) return noiseTile(look, k);
  const [fx, fy, gain] = WINDOWS[k], n = src.n;
  const col = window(src.col, 3, n, Math.floor(fx * n), Math.floor(fy * n), src.mcol);
  const pn = window(src.nrm, 3, n, Math.floor(fx * n), Math.floor(fy * n), src.mnrm);
  const L = new Float32Array(RES * RES).map((_, i) => lumOf(col, i)), mean = L.reduce((a, v) => a + v, 0) / L.length;
  const low = blurWrap(L, 10), target = lumOf(look.color, 0);
  const out = new Float32Array(RES * RES * 3), nrm = new Float32Array(RES * RES * 3), smooth = new Uint8Array(RES * RES);
  for (let i = 0; i < RES * RES; i++) {
    const d = look.detail * (L[i] - low[i]) / mean, v = Math.max(0.2, 1 + d) * gain;
    for (let ch = 0; ch < 3; ch++) out[i * 3 + ch] = clamp01(look.color[ch] * v + look.chroma * (col[i * 3 + ch] - L[i]) * target / mean);
    nrm.set(V.norm([pn[i * 3] * 2 - 1, pn[i * 3 + 1] * 2 - 1, pn[i * 3 + 2] * 2 - 1]), i * 3);
    smooth[i] = Math.round(look.smooth[0] + (look.smooth[1] - look.smooth[0]) * clamp01(0.5 + 2 * d));
  }
  const r = relief(nrm, RES, look.tilt);
  const albedo = Buffer.alloc(RES * RES * 4), s = Buffer.alloc(RES * RES * 4);
  for (let i = 0; i < RES * RES; i++) {
    albedo.set([...[0, 1, 2].map(ch => Math.round(out[i * 3 + ch] * 255)), 255], i * 4);
    s.set([smooth[i], 10, look.b, 255], i * 4);
  }
  return {col: out, nrm: r.nrm, res: RES, png: {albedo, s, n: encodeN(r.nrm, r.ao, RES)}};
}

/** Periodic value noise over a RES tile (cells of c texels, c divides RES), 0..1 (as build-rock-face.mjs pnoise). */
function pnoise(x, y, c, seed) {
  const n = RES / c, xi = Math.floor(x / c), yi = Math.floor(y / c), s = t => t * t * (3 - 2 * t);
  const h = (i, j) => hash(mod(i, n), mod(j, n), seed);
  const tx = s(x / c - xi), ty = s(y / c - yi);
  const a = h(xi, yi) + (h(xi + 1, yi) - h(xi, yi)) * tx, b = h(xi, yi + 1) + (h(xi + 1, yi + 1) - h(xi, yi + 1)) * tx;
  return a + (b - a) * ty;
}

/**
 * A tile with no photo (snow): a height of periodic value noise (16, 8 and 4 texel cells: wind-packed grain, no line
 * finer than 4 texels), its normal by central differences, the colour a breath lighter on the crests.
 */
function noiseTile(look, k) {
  const seed = 700 + k * 13, H = new Float32Array(RES * RES);
  for (let y = 0; y < RES; y++) for (let x = 0; x < RES; x++)
    H[y * RES + x] = 0.5 * pnoise(x, y, 16, seed) + 0.3 * pnoise(x, y, 8, seed + 1) + 0.2 * pnoise(x, y, 4, seed + 2);
  const at = (x, y) => H[mod(y, RES) * RES + mod(x, RES)];
  const out = new Float32Array(RES * RES * 3), nrm = new Float32Array(RES * RES * 3), smooth = new Uint8Array(RES * RES);
  for (let y = 0; y < RES; y++) for (let x = 0; x < RES; x++) {
    const i = y * RES + x, d = H[i] - 0.5;
    nrm.set(V.norm([-(at(x + 1, y) - at(x - 1, y)) * 4, (at(x, y + 1) - at(x, y - 1)) * 4, 1]), i * 3);
    for (let ch = 0; ch < 3; ch++) out[i * 3 + ch] = clamp01(look.color[ch] * (1 + 0.04 * d));
    smooth[i] = Math.round(look.smooth[0] + (look.smooth[1] - look.smooth[0]) * clamp01(0.5 + 2 * d));
  }
  const r = relief(nrm, RES, look.tilt);
  const albedo = Buffer.alloc(RES * RES * 4), s = Buffer.alloc(RES * RES * 4);
  for (let i = 0; i < RES * RES; i++) {
    albedo.set([...[0, 1, 2].map(ch => Math.round(out[i * 3 + ch] * 255)), 255], i * 4);
    s.set([smooth[i], 10, look.b, 255], i * 4);
  }
  return {col: out, nrm: r.nrm, res: RES, png: {albedo, s, n: encodeN(r.nrm, r.ao, RES)}};
}

async function lookTiles(sharp, name) {
  const look = LOOKS[name], src = look.procedural ? null : await lookSource(sharp, look);
  return [0, 1, 2, 3].map(k => gameTile(src, look, k));
}

const json = (file, o) => { fs.mkdirSync(path.dirname(file), {recursive: true}); fs.writeFileSync(file, JSON.stringify(o, null, 2) + '\n'); };
const ns = p => 'apocalypse_firstlight:' + p;

/** The 16 variants of a cube look: 4 tiles, plain and mirrored, at 0 and 180 degrees (as the natural rocks). */
const cubeVariants = look => [0, 1, 2, 3].flatMap(k => [
  {model: ns(`block/ground/${look}_${k}`)}, {model: ns(`block/ground/${look}_${k}`), y: 180},
  {model: ns(`block/ground/${look}_${k}_mirrored`)}, {model: ns(`block/ground/${look}_${k}_mirrored`), y: 180}]);

function cubeModels(look) {
  for (let k = 0; k < 4; k++) {
    json(path.join(GAME, `models/block/ground/${look}_${k}.json`), {parent: 'minecraft:block/cube_all', textures: {all: ns(`block/ground/${look}_${k}`)}});
    json(path.join(GAME, `models/block/ground/${look}_${k}_mirrored.json`), {parent: 'minecraft:block/cube_mirrored_all', textures: {all: ns(`block/ground/${look}_${k}`)}});
  }
}

/** A full cube whose top is tinted (index 0: the grass colour): topsoil's grass cover. */
function tintedParent() {
  const face = (tex, cull, tint) => ({uv: [0, 0, 16, 16], texture: tex, cullface: cull, ...(tint ? {tintindex: 0} : {})});
  json(path.join(GAME, 'models/block/ground/topsoil_tinted.json'), {
    parent: 'minecraft:block/block', textures: {particle: '#side'},
    elements: [{from: [0, 0, 0], to: [16, 16, 16], faces: {
      down: face('#bottom', 'down'), up: face('#top', 'up', true),
      north: face('#side', 'north'), south: face('#side', 'south'), west: face('#side', 'west'), east: face('#side', 'east')}}],
  });
}

async function writeGame(sharp) {
  const tex = path.join(GAME, 'textures/block/ground');
  fs.mkdirSync(tex, {recursive: true});
  for (const name of Object.keys(LOOKS)) {
    const tiles = await lookTiles(sharp, name);
    for (let k = 0; k < 4; k++) for (const [key, b] of Object.entries(tiles[k].png))
      await sharp(b, {raw: {width: RES, height: RES, channels: 4}}).png().toFile(path.join(tex, `${name}_${k}${key === 'albedo' ? '' : '_' + key}.png`));
    const m = means(tiles.flatMap(t => [...t.col]), 3);
    console.log(`${name}: mean ${m.map(v => v.toFixed(3)).join(' ')}`);
  }
  for (const look of [...CUBES, 'gravel_round', 'gravel_angular']) cubeModels(look);
  for (const look of CUBES) {
    json(path.join(GAME, `blockstates/${look}.json`), {variants: {'': cubeVariants(look)}});
    json(path.join(GAME, `models/item/${look}.json`), {parent: ns(`block/ground/${look}_0`)});
  }
  json(path.join(GAME, 'blockstates/gravel.json'), {variants: {'kind=round': cubeVariants('gravel_round'), 'kind=angular': cubeVariants('gravel_angular')}});
  json(path.join(GAME, 'models/item/gravel.json'), {parent: ns('block/ground/gravel_round_0')});
  // topsoil: the sides and bottom always soil, the top its cover; 4 tiles at the four turns (the sides are all alike)
  tintedParent();
  const variants = {};
  for (const [cover, look] of Object.entries(COVERS)) {
    for (let k = 0; k < 4; k++)
      json(path.join(GAME, `models/block/ground/topsoil_${cover}_${k}.json`), {
        parent: cover === 'grass' ? ns('block/ground/topsoil_tinted') : 'minecraft:block/cube_bottom_top',
        textures: {top: ns(`block/ground/${look}_${k}`), side: ns(`block/ground/soil_${k}`), bottom: ns(`block/ground/soil_${k}`), particle: ns(`block/ground/soil_${k}`)},
      });
    variants[`cover=${cover}`] = [0, 1, 2, 3].flatMap(k => [0, 90, 180, 270].map(y => ({model: ns(`block/ground/topsoil_${cover}_${k}`), ...(y ? {y} : {})})));
  }
  json(path.join(GAME, 'blockstates/topsoil.json'), {variants});
  json(path.join(GAME, 'models/item/topsoil.json'), {parent: ns('block/ground/topsoil_grass_0')});
  console.log('wrote textures/block/ground, models/block/ground, the blockstates and item models');
}

/** P x P texels of the look's tiles, one at random per block (tile, half turn, mirror), flat or lit. */
function tilePanel(P, tiles, lit, seed, tint = null) {
  const out = Buffer.alloc(P * P * 4);
  for (let y = 0; y < P; y++) for (let x = 0; x < P; x++) {
    const bx = Math.floor(x / RES), by = Math.floor(y / RES);
    const t = tiles[Math.floor(hash(bx, by, seed) * 4)], half = hash(bx, by, seed + 1) < 0.5, mirror = hash(bx, by, seed + 2) < 0.5;
    let u = x % RES, v = y % RES;
    if (mirror) u = RES - 1 - u;
    if (half) [u, v] = [RES - 1 - u, RES - 1 - v];
    const i = v * RES + u;
    let n = [t.nrm[i * 3], t.nrm[i * 3 + 1], t.nrm[i * 3 + 2]];
    if (mirror) n[0] = -n[0];
    if (half) n = [-n[0], -n[1], n[2]];
    const f = lit ? 0.3 + 0.95 * Math.max(0, V.dot(n, SUN)) : 1;
    for (let ch = 0; ch < 3; ch++) out[(y * P + x) * 4 + ch] = Math.round(clamp01(t.col[i * 3 + ch] * (tint ? tint[ch] : 1) * f) * 255);
    out[(y * P + x) * 4 + 3] = 255;
  }
  return out;
}

/** The game tiles of every look, 3 x 3 m each, flat over lit; grass under a plains green (vanilla's 0x91BD59). */
async function tilesSheet(sharp, dir) {
  fs.mkdirSync(dir, {recursive: true});
  const P = 384, gap = 16, head = 30, cols = 6, names = Object.keys(LOOKS);
  const rows = Math.ceil(names.length / cols), W = cols * (P + gap) + gap, H = rows * (2 * (P + head) + gap) + gap;
  const comp = [], text = [];
  const label = {soil: '裸土 / 表土侧面', grass: '草皮（平原绿）', litter: '落叶层', moss: '苔藓', mud: '泥', sand: '沙', red_sand: '红沙',
    gravel_round: '圆砾', gravel_angular: '棱角砾', clay: '黏土', snow: '积雪'};
  for (let k = 0; k < names.length; k++) {
    const name = names[k], tiles = await lookTiles(sharp, name), tint = name === 'grass' ? [0x91 / 255, 0xBD / 255, 0x59 / 255] : null;
    const left = gap + (k % cols) * (P + gap), top0 = gap + Math.floor(k / cols) * (2 * (P + head) + gap);
    for (let r = 0; r < 2; r++) {
      const top = top0 + r * (P + head) + head;
      comp.push({input: await sharp(tilePanel(P, tiles, r === 1, 500 + k, tint), {raw: {width: P, height: P, channels: 4}}).png().toBuffer(), left, top});
      text.push(`<text x="${left}" y="${top - 8}" font-family="Microsoft YaHei, sans-serif" font-size="19" fill="#d8d8d8">${label[name]}${r ? '　斜光' : '　平光，每格 1 m'}</text>`);
    }
  }
  comp.push({input: Buffer.from(`<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}">${text.join('')}</svg>`), left: 0, top: 0});
  const file = path.join(dir, 'n1b_tiles.png');
  await sharp({create: {width: W, height: H, channels: 4, background: '#202224'}}).composite(comp).png().toFile(file);
  console.log('wrote', file);
}

const args = process.argv.slice(2);
if (args.includes('--fetch')) await fetchAll();
const at = args.indexOf('--preview');
if (at >= 0) {
  const dir = path.resolve(args[at + 1] ?? path.join(ROOT, 'build/n1a-preview'));
  fs.mkdirSync(dir, {recursive: true});
  const sharp = loadSharp(), stone = await stoneTile(sharp);
  const only = args.indexOf('--only') >= 0 ? args[args.indexOf('--only') + 1].split(',') : null;
  for (const m of MATERIALS) if (!only || only.includes(m[0])) await sheet(sharp, dir, m, stone);
}
if (args.includes('--write')) await writeGame(loadSharp());
const ti = args.indexOf('--tiles');
if (ti >= 0) await tilesSheet(loadSharp(), path.resolve(args[ti + 1] ?? path.join(ROOT, 'build/n1b-preview')));
