// Rock R1 boulders (docs/gameplay/rock_r1_stones_boulders_design.md 3.1): the 12 forms of grey granite field stone, their
// fragments, the rock sprite made from CC0 photographs, and the preview sheets the user looks at before anything goes into
// the game. Round 1 (2026-10-21): previews only; the game resources (mesh JSON, block textures) come with R1a after the
// user has seen the sheets. R1a (user approved the sheets 2026-10-21): --write puts them into the game.
//   node tools/boulders/build-boulders.mjs --fetch          -> downloads the photos into build/cc0-rock (Poly Haven, CC0)
//   node tools/boulders/build-boulders.mjs --check          -> closed meshes, fragment volumes add up to the whole
//   node tools/boulders/build-boulders.mjs --preview DIR    -> writes the sprites and the preview sheets into DIR
//   node tools/boulders/build-boulders.mjs --write          -> the game's data (export.mjs) and block sprites (granite,
//                                                              granite_lichen, granite_fresh: a fresh break, no lichen)
//   node tools/boulders/build-boulders.mjs --write-rocks    -> R3: the same three sprites of the other rocks (ROCKS)
//   node tools/boulders/build-boulders.mjs --candidates DIR -> R3: a sheet of rendered boulders, one row per rock
// Sprite: one 2 x 2 m photo tile at 128 texels per metre (256 x 256), the same density as the tree bark. Albedo is the
// rock_surface photo's grain regraded to granite grey, with crustose lichen (pale grey-green) taken from the lichen_rock
// photo where a low-frequency patch mask allows it: little on the plain sprite, a lot on the weathered one. _n is the
// rock_surface normal map (OpenGL green flipped to DirectX for LabPBR), flattened under lichen.
import fs from 'node:fs';
import path from 'node:path';
import {execFileSync} from 'node:child_process';
import {createRequire} from 'node:module';
import {fileURLToPath} from 'node:url';
import {FORMS, form, fragments, volume, centroid, translate, level1Count, level2Count, strikes, budget, MAX_STRIKES, hash, V} from './boulder-shapes.mjs';
import {soup, addMesh, addGround, render} from './raster.mjs';
import {exportForms, encode, checkExport, SPRITES} from './export.mjs';
import {relief, encodeN} from '../rock/rock-pbr.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const CACHE = path.join(ROOT, 'build/cc0-rock');
const PH = 'https://dl.polyhaven.org/file/ph-assets/Textures/jpg/1k';
/** CC0 photos (Poly Haven, 2 x 2 m each, 1k JPG). */
const SOURCES = {};
for (const a of ['rock_surface', 'lichen_rock', 'rock_05', 'worn_rock_natural_01', 'dark_rock_02'])
  for (const m of ['diff', 'nor_gl', 'rough']) SOURCES[`${a}_${m}_1k.jpg`] = `${PH}/${a}/${a}_${m}_1k.jpg`;
const TILE = 2, RES = 256;

/** sharp lives in the wiki's node_modules (this checkout's, else the main checkout's beside it). */
function loadSharp() {
  for (const p of [path.join(ROOT, 'wiki/package.json'), path.resolve(ROOT, '../Apocalypse First Light/wiki/package.json')]) {
    try { return createRequire(p)('sharp'); } catch { /* next */ }
  }
  throw new Error('sharp not found: run npm --prefix wiki install');
}

/**
 * A photo averaged down to RES / rep (exact on a tiling image, no edge seam) and repeated rep x rep over the tile (a 1 m
 * photo twice across the 2 m tile), channels 0..1.
 */
async function photo(sharp, name, grey = false, rep = 1) {
  const s = sharp(path.join(CACHE, name));
  const {data, info} = await (grey ? s.greyscale() : s.removeAlpha()).raw().toBuffer({resolveWithObject: true});
  const c = grey ? 1 : 3, r = RES / rep, k = info.width / r, o = new Float32Array(RES * RES * c);
  for (let y = 0; y < r; y++) for (let x = 0; x < r; x++) for (let ch = 0; ch < c; ch++) {
    let s2 = 0;
    for (let j = 0; j < k; j++) for (let i = 0; i < k; i++) s2 += data[((y * k + j) * info.width + x * k + i) * info.channels + ch];
    for (let b = 0; b < rep; b++) for (let a = 0; a < rep; a++) o[((y + b * r) * RES + x + a * r) * c + ch] = s2 / (k * k * 255);
  }
  return o;
}

/** Box blur of a 1-channel RES x RES tile that wraps, radius r, twice (close to a Gaussian). */
function blurWrap(a, r) {
  let src = a;
  for (let pass = 0; pass < 4; pass++) {
    const dst = new Float32Array(src.length), hor = pass % 2 === 0;
    for (let y = 0; y < RES; y++) for (let x = 0; x < RES; x++) {
      let s = 0;
      for (let d = -r; d <= r; d++) {
        const xx = hor ? (x + d + RES) % RES : x, yy = hor ? y : (y + d + RES) % RES;
        s += src[yy * RES + xx];
      }
      dst[y * RES + x] = s / (2 * r + 1);
    }
    src = dst;
  }
  return src;
}

/** Periodic value noise over the tile (cells of c texels, c divides RES), 0..1. */
function pnoise(x, y, c, seed) {
  const n = RES / c, xi = Math.floor(x / c), yi = Math.floor(y / c), s = t => t * t * (3 - 2 * t);
  const h = (i, j) => hash(((i % n) + n) % n, ((j % n) + n) % n, seed);
  const tx = s(x / c - xi), ty = s(y / c - yi);
  const a = h(xi, yi) + (h(xi + 1, yi) - h(xi, yi)) * tx, b = h(xi, yi + 1) + (h(xi + 1, yi + 1) - h(xi, yi + 1)) * tx;
  return a + (b - a) * ty;
}
const sstep = (a, b, v) => { const t = Math.max(0, Math.min(1, (v - a) / (b - a))); return t * t * (3 - 2 * t); };
const lum = (d, i) => 0.2126 * d[i * 3] + 0.7152 * d[i * 3 + 1] + 0.0722 * d[i * 3 + 2];

/** Granite field stone (sRGB 0..1). */
const GRANITE = [0.53, 0.52, 0.5], LICHEN = [0.76, 0.78, 0.68];

/**
 * Rock R3: the rocks a boulder can be of, each from its own CC0 photo (Poly Haven, in build/cc0-rock). photo: its id; rep:
 * how often it repeats across the 2 m tile (a 1 m photo twice; other sizes are scaled to the tile); color: the weathered
 * rock's grey (sRGB 0..1); detail, mott: gains on the photo's fine grain and its broad mottle about that grey; chroma: how
 * much of the photo's own tint is added back, keep: how far the colour goes over to the photo's own (scaled to the grey's
 * brightness); speckle: granite's light and dark feldspar and biotite grains; nrm: the photo normal's strength; tilt: Rock
 * PBR R1 (docs/worldgen/rock_pbr_v1.md 4): the relief made stronger until 90% of the bare rock's texels lean at most this many
 * degrees (rock-pbr.mjs relief, with its crevice AO); lichen: the share of the crust on the plain and the weathered sprite.
 */
export const ROCKS = {
  granite: {photo: 'rock_surface', rep: 1, color: GRANITE, detail: 2.4, mott: 0.7, chroma: 0.35, keep: 0, speckle: true, nrm: 0.8, tilt: 24, lichen: [0.16, 0.62]},
  // user 2026-10-23 from design/world/rock_r3/boulders_r3_candidates.png: limestone row 3, sandstone row 4, shale row 6
  limestone: {photo: 'rock_05', rep: 1, color: [0.70, 0.69, 0.64], detail: 0.8, mott: 0.4, chroma: 0.06, keep: 0, nrm: 0.6, tilt: 22, lichen: [0.16, 0.55]},
  sandstone: {photo: 'worn_rock_natural_01', rep: 1, color: [0.63, 0.56, 0.45], detail: 1.4, mott: 0.5, chroma: 0.1, keep: 0.4, nrm: 0.8, tilt: 22, lichen: [0.12, 0.45]},
  // natural ground N1 (docs/worldgen/natural_ground_n1_design.md 9, user 2026-10-24): the badlands' red beds, sandstone's
  // photo in an iron-oxide red
  red_sandstone: {photo: 'worn_rock_natural_01', rep: 1, color: [0.62, 0.40, 0.29], detail: 1.4, mott: 0.5, chroma: 0.1, keep: 0.3, nrm: 0.8, tilt: 22, lichen: [0.08, 0.35]},
  shale: {photo: 'dark_rock_02', rep: 1, color: [0.37, 0.37, 0.37], detail: 1.6, mott: 0.5, chroma: 0.05, keep: 0.1, nrm: 0.8, tilt: 26, lichen: [0.08, 0.35]},
};

/**
 * The sprite of a rock (ROCKS): lichen 0..1 = how much of the tile the patch mask opens. Returns {res, tile, col, nrm
 * (OpenGL, -1..1, for the preview), png: {albedo, s, n} as RGBA buffers}.
 */
async function sprite(sharp, rock, lichen, gain = 1) {
  const ph = rock.photo, rep = rock.rep;
  const rs = await photo(sharp, ph + '_diff_1k.jpg', false, rep), lr = await photo(sharp, 'lichen_rock_diff_1k.jpg');
  const rn = await photo(sharp, ph + '_nor_gl_1k.jpg', false, rep), rough = await photo(sharp, ph + '_rough_1k.jpg', true, rep);
  const base = rock.color, baseLum = (base[0] + base[1] + base[2]) / 3;
  const L = new Float32Array(RES * RES).map((_, i) => lum(rs, i)), mean = L.reduce((a, v) => a + v, 0) / L.length;
  const low = blurWrap(L, 12);
  const col = new Float32Array(RES * RES * 3), nrm = new Float32Array(RES * RES * 3), bare = new Float32Array(RES * RES * 3);
  const albedo = Buffer.alloc(RES * RES * 4), sB = Buffer.alloc(RES * RES * 4);
  const th = 1 - lichen;
  for (let y = 0; y < RES; y++) for (let x = 0; x < RES; x++) {
    const i = y * RES + x;
    // grain from the photo (its fine detail, strengthened) on a granite grey, the photo's own broad mottle kept softly
    const detail = (L[i] - low[i]) / mean, mott = low[i] / mean - 1;
    const v = 1 + rock.detail * detail + rock.mott * mott;
    // granite's feldspar and biotite: sparse light and dark grains, a texel each, mild
    const g = hash(x, y, 77), grain = !rock.speckle ? 1 : g < 0.05 ? 0.82 : g > 0.965 ? 1.12 : 1;
    let c = base.map((b, k) => b * gain * v * grain + rock.chroma * (rs[i * 3 + k] - L[i]));
    if (rock.keep > 0) c = c.map((a, k) => a + (rs[i * 3 + k] * baseLum / mean * gain - a) * rock.keep);
    // lichen: bright crust of the lichen photo, where the patch mask (two octaves) is open
    const patch = 0.65 * pnoise(x, y, 64, 5) + 0.35 * pnoise(x, y, 16, 6);
    const crust = sstep(0.22, 0.34, lum(lr, i)) * sstep(th, th + 0.12, patch);
    const lc = LICHEN.map(b => b * (0.82 + 0.5 * (lum(lr, i) - 0.35)));
    c = c.map((a, k) => a + (lc[k] - a) * crust * 0.92);
    col.set(c.map(a => Math.max(0, Math.min(1, a))), i * 3);
    // normal: OpenGL from the photo, a little softer, flattened under the crust
    let n = [(rn[i * 3] * 2 - 1) * rock.nrm, (rn[i * 3 + 1] * 2 - 1) * rock.nrm, rn[i * 3 + 2] * 2 - 1];
    bare.set(V.norm(n), i * 3);
    n = V.norm(n.map((a, k) => a + ([0, 0, 1][k] - a) * crust * 0.5));
    nrm.set(n, i * 3);
    albedo.set([...col.slice(i * 3, i * 3 + 3)].map(a => Math.round(a * 255)).concat(255), i * 4);
    sB.set([Math.round((1 - rough[i]) * 170), 10, 48, 255], i * 4);
  }
  // the relief: the bare rock's scale, so the rock under a crust is as rough as without one
  const scale = relief(bare, RES, rock.tilt).gain, r = relief(nrm, RES, rock.tilt, {gain: scale});
  return {res: RES, tile: TILE, col, nrm: r.nrm, png: {albedo, s: sB, n: encodeN(r.nrm, r.ao, RES)}};
}

/** Closed mesh: every directed edge has its reverse exactly once. */
function closed(m) {
  const e = new Map();
  for (const [a, b, c] of m.T) for (const [u, v] of [[a, b], [b, c], [c, a]]) e.set(u + ',' + v, (e.get(u + ',' + v) || 0) + 1);
  for (const [k, n] of e) { const [u, v] = k.split(','); if (n !== 1 || e.get(v + ',' + u) !== 1) return false; }
  return true;
}

/** Every form with its pieces: [{f, parts, l1: [[piece, l2 pieces or null]]}] (the largest part of a form is split). */
function build() {
  return FORMS.map((f, i) => {
    const parts = form(f, i);
    const l1 = parts.map((p, j) => {
      const v = volume(p), seed = 7000 + i * 50 + j * 7;
      return fragments(p, level1Count(v), seed).map((q, k) => [q, volume(q) > 0.25 ? fragments(q, level2Count(volume(q)), seed * 3 + k) : null]);
    });
    return {f, parts, l1};
  });
}

function check(all) {
  let bad = 0;
  for (const {f, parts, l1} of all) {
    const tris = parts.reduce((s, p) => s + p.T.length, 0), vol = parts.map(volume);
    let line = `${f.id.padEnd(14)} L ${f.L.toFixed(1)} m  ${tris} tris  ${vol.map(v => v.toFixed(3)).join(' + ')} m3 (${(vol.reduce((a, b) => a + b, 0) * 2.7).toFixed(2)} t)`;
    // stages in order: a part of 0.25 m3 or less shatters at once; a larger one opens, and so does each piece over 0.25 m3
    const raw = parts.flatMap((p, j) => (vol[j] <= 0.25 ? [strikes(vol[j])] : [strikes(vol[j]),
      ...l1[j].flatMap(([q, l2]) => (l2 ? [strikes(volume(q)), ...l2.map(r => strikes(volume(r)))] : [strikes(volume(q))]))]));
    const plan = budget(raw), sum = plan.reduce((a, b) => a + b, 0);
    if (sum > MAX_STRIKES || plan.some(s => s < 1)) { bad++; line += '  BUDGET'; }
    line += `  [${raw.length} stages, ${raw.reduce((a, b) => a + b, 0)} -> ${sum} strikes]`;
    parts.forEach((p, j) => {
      if (!closed(p)) { bad++; line += `  part ${j} OPEN`; }
      const sum = l1[j].reduce((s, [q]) => s + volume(q), 0);
      if (Math.abs(sum - vol[j]) > vol[j] * 0.005) { bad++; line += `  part ${j} pieces ${sum.toFixed(4)} != ${vol[j].toFixed(4)}`; }
      for (const [q, l2] of l1[j]) {
        if (!closed(q)) { bad++; line += '  piece OPEN'; }
        if (l2) {
          const s2 = l2.reduce((s, r) => s + volume(r), 0);
          if (Math.abs(s2 - volume(q)) > volume(q) * 0.005) { bad++; line += `  level 2 ${s2.toFixed(4)} != ${volume(q).toFixed(4)}`; }
          if (!l2.every(closed)) { bad++; line += '  level 2 OPEN'; }
        }
      }
    });
    console.log(line);
  }
  console.log(bad ? `${bad} problem(s)` : 'all closed, all pieces add up');
  return bad === 0;
}

/** Ground for the previews: dry soil with a faint 1 m grid (the scale). */
function ground(x, z) {
  const n = 0.5 + 0.5 * Math.sin(x * 1.7 + Math.sin(z * 1.3) * 2) * Math.cos(z * 1.1 - x * 0.4);
  const g = hash(Math.floor(x * 40), Math.floor(z * 40), 9) * 0.08;
  const line = Math.min(Math.abs(x - Math.round(x)), Math.abs(z - Math.round(z))) < 0.012 ? 0.86 : 1;
  return [0.42, 0.38, 0.3].map((c, k) => c * (0.9 + 0.12 * n + g) * line + (k === 1 ? 0.02 * n : 0));
}

/** Renders parts (already placed) in a tile: camera from the south-east above, framing their bounds. */
function tile(parts, sp, {w, h, bury = 0.25, yaw = 0.5}) {
  const S = soup();
  let lo = [1e9, 1e9, 1e9], hi = [-1e9, -1e9, -1e9];
  const height = Math.max(...parts.flatMap(p => p.P.map(q => q[1])));
  for (const p of parts) {
    const pos = [0, -bury * height, 0];
    addMesh(S, p, {yaw, pos, sprite: sp});
    const c = Math.cos(yaw), s = Math.sin(yaw);
    for (const [x, y, z] of p.P) { const q = [x * c + z * s, y + pos[1], -x * s + z * c]; lo = lo.map((v, k) => Math.min(v, q[k])); hi = hi.map((v, k) => Math.max(v, q[k])); }
  }
  const ctr = lo.map((v, k) => (v + hi[k]) / 2), R = Math.max(1.2, Math.hypot(hi[0] - lo[0], Math.max(0, hi[1]), hi[2] - lo[2]) / 2);
  addGround(S, [ctr[0], 0, ctr[2]], 40, ground);
  const dir = V.norm([0.62, 0.42, 0.66]), target = [ctr[0], Math.max(0, hi[1]) * 0.35, ctr[2]];
  return render(S, {w, h, eye: V.add(target, V.mul(dir, R / Math.sin(15 * Math.PI / 180) * 0.98)), target, fov: 30});
}

/** Pieces moved away from the whole's centre by gap metres (to show the cracks). */
const spread = (pieces, gap) => { const c = centroid({P: pieces.flatMap(p => p.P)}); return pieces.map(p => translate(p, V.mul(V.norm(V.sub(centroid(p), c)), gap))); };

async function sheet(file, cols, tiles, sharp, tw = 420, th = 300) {
  const rows = Math.ceil(tiles.length / cols), W = cols * tw + (cols + 1) * 6, H = rows * th + (rows + 1) * 6, buf = Buffer.alloc(W * H * 3, 34);
  tiles.forEach((t, i) => {
    const img = t(), x0 = 6 + (i % cols) * (tw + 6), y0 = 6 + Math.floor(i / cols) * (th + 6);
    for (let y = 0; y < th; y++) img.copy(buf, ((y0 + y) * W + x0) * 3, y * tw * 3, (y + 1) * tw * 3);
  });
  await sharp(buf, {raw: {width: W, height: H, channels: 3}}).png().toFile(file);
}

const args = process.argv.slice(2);
if (args.includes('--fetch')) {
  fs.mkdirSync(CACHE, {recursive: true});
  for (const [f, u] of Object.entries(SOURCES)) if (!fs.existsSync(path.join(CACHE, f))) execFileSync('curl', ['-sSfL', '-o', path.join(CACHE, f), u], {stdio: 'inherit'});
}
const all = build();
let ok = check(all);
const exported = exportForms(all, TILE);
ok = checkExport(exported) && ok;
const pi = args.indexOf('--preview');
if (pi >= 0) {
  const dir = path.resolve(args[pi + 1]);
  fs.mkdirSync(dir, {recursive: true});
  const sharp = loadSharp();
  const plain = await sprite(sharp, ROCKS.granite, 0.16), weathered = await sprite(sharp, ROCKS.granite, 0.62);
  for (const [n, sp] of [['granite', plain], ['granite_lichen', weathered]]) {
    for (const [k, b] of Object.entries(sp.png)) await sharp(b, {raw: {width: RES, height: RES, channels: 4}}).png().toFile(path.join(dir, `${n}${k === 'albedo' ? '' : '_' + k}.png`));
  }
  // 1: the 12 forms, a quarter buried, plain sprite
  await sheet(path.join(dir, 'boulders_r1_forms.png'), 4, all.map(({parts}, i) => () => tile(parts, plain, {w: 420, h: 300, yaw: 0.4 + i * 0.7})), sharp);
  // 2: weathered sprite on three forms; then the big erratic whole, struck open, and its largest piece struck open again
  const big = all[2], pieces = big.l1[0].map(([q]) => q);
  const li = pieces.reduce((b, q, k) => (volume(q) > volume(pieces[b]) ? k : b), 0), l2 = big.l1[0][li][1];
  await sheet(path.join(dir, 'boulders_r1_lichen_and_pieces.png'), 3, [
    () => tile(all[0].parts, weathered, {w: 420, h: 300, yaw: 0.9}),
    () => tile(all[3].parts, weathered, {w: 420, h: 300, yaw: 2.2}),
    () => tile(all[11].parts, weathered, {w: 420, h: 300, yaw: 4.0}),
    () => tile(big.parts, plain, {w: 420, h: 300, bury: 0, yaw: 0.6}),
    () => tile(spread(pieces, 0.07), plain, {w: 420, h: 300, bury: 0, yaw: 0.6}),
    () => tile(spread(pieces.flatMap((q, k) => (k === li && l2 ? spread(l2, 0.06) : [q])), 0.07), plain, {w: 420, h: 300, bury: 0, yaw: 0.6}),
  ], sharp);
  console.log('previews in', dir);
}
const ci = args.indexOf('--candidates');
if (ci >= 0) {
  // R3: one row per rock: a breakdown slab, a block and a cluster, plain sprite; the weathered sprite on a split pair
  const dir = path.resolve(args[ci + 1]), sharp = loadSharp(), rows = [];
  fs.mkdirSync(dir, {recursive: true});
  for (const [id, rock] of Object.entries(ROCKS)) {
    const plain = await sprite(sharp, rock, rock.lichen[0]), weathered = await sprite(sharp, rock, rock.lichen[1]);
    await sharp(plain.png.albedo, {raw: {width: RES, height: RES, channels: 4}}).png().toFile(path.join(dir, id + '.png'));
    rows.push(() => tile(all[3].parts, plain, {w: 420, h: 300, yaw: 2.2}), () => tile(all[5].parts, plain, {w: 420, h: 300, yaw: 0.8}),
      () => tile(all[10].parts, plain, {w: 420, h: 300, yaw: 4.0}), () => tile(all[7].parts, weathered, {w: 420, h: 300, yaw: 1.4}));
    console.log('row', rows.length / 4, id);
  }
  await sheet(path.join(dir, 'boulders_r3_candidates.png'), 4, rows, sharp);
}
if (args.includes('--write-rocks')) {
  // R3: the sprites of the rocks but granite (granite's come with --write): plain, weathered, a fresh break
  const sharp = loadSharp(), tex = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight/textures/block/boulder');
  for (const [id, rock] of Object.entries(ROCKS)) {
    if (id === 'granite') continue;
    const sprites = [await sprite(sharp, rock, rock.lichen[0]), await sprite(sharp, rock, rock.lichen[1]), await sprite(sharp, rock, 0, 1.1)];
    for (let i = 0; i < SPRITES.length; i++)
      for (const [k, b] of Object.entries(sprites[i].png))
        await sharp(b, {raw: {width: RES, height: RES, channels: 4}}).png().toFile(path.join(tex, SPRITES[i].replace('granite', id) + (k === 'albedo' ? '' : '_' + k) + '.png'));
    console.log('wrote the sprites of', id);
  }
}
if (args.includes('--write') && ok) {
  const res = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight');
  fs.mkdirSync(path.join(res, 'boulders'), {recursive: true});
  const bin = encode(exported, TILE);
  fs.writeFileSync(path.join(res, 'boulders/granite_r1.bin'), bin);
  const sharp = loadSharp(), tex = path.join(res, 'textures/block/boulder');
  fs.mkdirSync(tex, {recursive: true});
  // a fresh break: no lichen, a little lighter (unweathered feldspar)
  const g = ROCKS.granite, sprites = [await sprite(sharp, g, g.lichen[0]), await sprite(sharp, g, g.lichen[1]), await sprite(sharp, g, 0, 1.1)];
  for (let i = 0; i < SPRITES.length; i++)
    for (const [k, b] of Object.entries(sprites[i].png)) await sharp(b, {raw: {width: RES, height: RES, channels: 4}}).png().toFile(path.join(tex, `${SPRITES[i]}${k === 'albedo' ? '' : '_' + k}.png`));
  console.log(`wrote boulders/granite_r1.bin (${(bin.length / 1024).toFixed(0)} KB) and ${SPRITES.length} sprites`);
}
process.exit(ok ? 0 : 1);
