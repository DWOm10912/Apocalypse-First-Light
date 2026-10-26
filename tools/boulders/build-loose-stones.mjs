// Rock R1b (docs/gameplay/rock_r1_stones_boulders_design.md 2.2-2.4, 11.3): the stones of a loose_stones block as the
// game reads them, and the flint sprites.
//   node tools/boulders/build-loose-stones.mjs            -> writes boulders/loose_r1.bin and textures/block/boulder/flint*
//   node tools/boulders/build-loose-stones.mjs --check    -> closed meshes, uvs inside their tile, outputs up to date
//
// Stones (rock() of boulder-shapes.mjs, metres, base centre at the origin, base at y = 0; spec at the items' size, the
// world's WORLD times it):
//   pebble_field_*  10-14 cm, a few worn breaks (2.1)          pebble_river_*  flattened, no breaks, smoother (2.3)
//   cobble_field_*  20-22 cm field stone (the item's shape)   cobble_river_*  flattened river cobble
//   flint_0 a nodule in its chalky cortex, flint_1 a nodule broken open (the glossy flint in the cap), flint_2 a river
//   flint, the cortex worn away (2.4)
// Pebbles are icosphere level 1 (80 triangles: a few centimetres in the world), cobbles and flints level 2 (320).
// Sprites (2 m tiles, 128 texels per metre, the boulders' density): granite (the boulders' own), granite_worn (the same
// granite water-worn, for the river stones: its grain's contrast and relief softened, smoother), flint (the stone: grey
// brown, a faint cloudy banding, waxy, smooth) and flint_cortex (the white chalky rind: rough, porous). Both flint sprites
// are made from the rock_surface CC0 photo the boulders use (build/cc0-rock, build-boulders.mjs --fetch): its grain,
// regraded; no new photo.
//
// Binary (big-endian, gzip; boulder/LooseStoneData): 'AFLL', version 1, tile (m), sprites (utf), meshes: id, kind,
// height (m), quads (sprite, 4 corners: position / 8192 m, normal x 127, uv x 65535 in the tile); the fourth corner of
// each triangle repeats the third, as the boulders' quads.
import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';
import {createRequire} from 'node:module';
import {fileURLToPath} from 'node:url';
import {rock, clip, volume, bounds, hash, V} from './boulder-shapes.mjs';
import {ROCK_LOOKS} from './export.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const RES_DIR = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight');
const CACHE = path.join(ROOT, 'build/cc0-rock');
const TILE = 2, RES = 256;
export const SPRITES = ['granite', 'flint', 'flint_cortex', 'granite_worn'];
/** Kinds (LooseStoneData.Kind order). */
export const KINDS = ['pebble_field', 'pebble_river', 'cobble_field', 'cobble_river', 'flint_field', 'flint_river'];

/**
 * The size in the world of each kind, of its spec (user 2026-10-22: at real size the pebbles vanish into the ground's
 * 6 cm texels). Pebbles 2.5x (9-14 cm, about what the item looks in hand at its held scale 2.0-2.4), flints 2x (10-13 cm), field
 * and river stones 1.4x. The items (build-field-stone.mjs) keep the specs as they are.
 */
const WORLD = [2.5, 2.5, 1.4, 1.4, 2, 2];
const PEBBLE = {planes: 2, pre: 2, round: 2, amp: 0.12, cut: [0.62, 0.86]};
const RIVER = {planes: 0, pre: 3, round: 3, amp: 0.05};
/** id, kind, rock() spec, seed, level, and for a broken flint the break plane (normal, share of the extent). */
export const STONES = [
  {id: 'pebble_field_0', kind: 0, spec: {...PEBBLE, L: 0.07, r: [1, 0.62, 0.8]}, seed: 5101, level: 1},
  {id: 'pebble_field_1', kind: 0, spec: {...PEBBLE, L: 0.08, r: [1, 0.7, 0.75]}, seed: 5102, level: 1},
  {id: 'pebble_field_2', kind: 0, spec: {...PEBBLE, L: 0.06, r: [1, 0.55, 0.85]}, seed: 5103, level: 1},
  {id: 'pebble_river_0', kind: 1, spec: {...RIVER, L: 0.075, r: [1, 0.4, 0.78]}, seed: 5201, level: 1},
  {id: 'pebble_river_1', kind: 1, spec: {...RIVER, L: 0.065, r: [1, 0.48, 0.7]}, seed: 5202, level: 1},
  {id: 'pebble_river_2', kind: 1, spec: {...RIVER, L: 0.085, r: [1, 0.36, 0.82]}, seed: 5203, level: 1},
  // the first is the field_stone item's cobble (build-field-stone.mjs)
  {id: 'cobble_field_0', kind: 2, spec: {L: 0.2, r: [1, 0.7, 0.8], planes: 3, pre: 2, round: 2, amp: 0.14, cut: [0.62, 0.86]}, seed: 4107, level: 2},
  {id: 'cobble_field_1', kind: 2, spec: {L: 0.16, r: [1, 0.62, 0.86], planes: 3, pre: 2, round: 2, amp: 0.14, cut: [0.62, 0.86]}, seed: 4133, level: 2},
  {id: 'cobble_river_0', kind: 3, spec: {...RIVER, L: 0.18, r: [1, 0.42, 0.8]}, seed: 5301, level: 2},
  {id: 'cobble_river_1', kind: 3, spec: {...RIVER, L: 0.15, r: [1, 0.5, 0.72]}, seed: 5302, level: 2},
  {id: 'flint_0', kind: 4, spec: {L: 0.065, r: [1, 0.62, 0.72], planes: 0, pre: 1, round: 1, amp: 0.3}, seed: 5401, level: 2},
  {id: 'flint_1', kind: 4, spec: {L: 0.075, r: [1, 0.66, 0.74], planes: 0, pre: 1, round: 1, amp: 0.28}, seed: 5402, level: 2, cut: {n: [0.35, 0.8, 0.25], f: 0.2}},
  {id: 'flint_2', kind: 5, spec: {...RIVER, L: 0.055, r: [1, 0.52, 0.7], amp: 0.1}, seed: 5403, level: 2},
];

/** A stone's mesh {P, T, F} (F: 1 on a fresh break), base centre at the origin, base at y = 0. */
export function stoneMesh(s) {
  let m = rock(s.spec, s.seed, s.level);
  m.F = m.T.map(() => 0);
  if (s.cut) {
    const n = V.norm(s.cut.n), ext = Math.max(...m.P.map(p => V.dot(p, n)));
    m = clip(m, n, ext * s.cut.f);
  }
  const b = bounds(m), c = [(b[0][0] + b[0][1]) / 2, b[1][0], (b[2][0] + b[2][1]) / 2];
  return {P: m.P.map(p => V.sub(p, c)), T: m.T, F: m.F};
}

/** The sprite of a triangle of stone s: granite, water-worn on a river stone; a flint's cortex, its break and a river flint the flint itself. */
const spriteOf = (s, f) => (s.kind < 4 ? (s.kind % 2 === 1 ? 3 : 0) : s.kind === 5 || f === 1 ? 1 : 2);

/** Smooth corner normals within 40 degrees (the boulders' rule). */
function cornerNormals(m) {
  const crease = Math.cos(40 * Math.PI / 180);
  const fn = m.T.map(([a, b, c]) => V.cross(V.sub(m.P[b], m.P[a]), V.sub(m.P[c], m.P[a])));
  const unit = fn.map(V.norm), at = m.P.map(() => []);
  m.T.forEach((t, ti) => t.forEach(v => at[v].push(ti)));
  return m.T.map((t, ti) => t.map(v => {
    let s = [0, 0, 0];
    for (const f of at[v]) if (V.dot(unit[f], unit[ti]) >= crease) s = V.add(s, fn[f]);
    return V.norm(s);
  }));
}

/** The quads of stone s: box-projected on each triangle's dominant axis into one place of the 2 m tile. */
function quadsOf(s, i) {
  const m0 = stoneMesh(s), k = WORLD[s.kind], m = {...m0, P: m0.P.map(p => V.mul(p, k))};
  const normals = cornerNormals(m), out = [];
  // a stone is a few centimetres: it lies wholly inside one tile, at a place of its own
  const off = [0.2 + hash(i, 1, 61) * 1.5, 0.2 + hash(i, 2, 61) * 1.5];
  m.T.forEach((t, ti) => {
    const P = t.map(k => m.P[k]), f = V.cross(V.sub(P[1], P[0]), V.sub(P[2], P[0])), ax = f.map(Math.abs);
    const axis = ax[0] >= ax[1] && ax[0] >= ax[2] ? 0 : ax[1] >= ax[2] ? 1 : 2;
    const proj = p => (axis === 0 ? [p[2], -p[1]] : axis === 1 ? [p[0], p[2]] : [p[0], -p[1]]).map((v, k) => (v + off[k]) / TILE);
    const v = P.map((p, k) => ({p, n: normals[ti][k], uv: proj(p)}));
    out.push({sprite: spriteOf(s, m.F[ti]), v: [v[0], v[1], v[2], v[2]]});
  });
  return {m, quads: out, height: bounds(m)[1][1]};
}

function closed(m) {
  const e = new Map();
  for (const [a, b, c] of m.T) for (const [u, v] of [[a, b], [b, c], [c, a]]) e.set(u + ',' + v, (e.get(u + ',' + v) || 0) + 1);
  for (const [k, n] of e) { const [u, v] = k.split(','); if (n !== 1 || e.get(v + ',' + u) !== 1) return false; }
  return true;
}

function encode(built) {
  const parts = [];
  const u8 = v => { const b = Buffer.alloc(1); b.writeInt8(v); parts.push(b); };
  const i16 = v => { const b = Buffer.alloc(2); b.writeInt16BE(v); parts.push(b); };
  const u16 = v => { const b = Buffer.alloc(2); b.writeUInt16BE(v); parts.push(b); };
  const i32 = v => { const b = Buffer.alloc(4); b.writeInt32BE(v); parts.push(b); };
  const f32 = v => { const b = Buffer.alloc(4); b.writeFloatBE(v); parts.push(b); };
  const utf = s => { const b = Buffer.from(s, 'utf8'); u16(b.length); parts.push(b); };
  i32(0x41464C4C); i16(1); f32(TILE);
  u8(SPRITES.length); SPRITES.forEach(utf);
  i16(built.length);
  for (const {s, quads, height} of built) {
    utf(s.id); u8(s.kind); f32(height); i32(quads.length);
    for (const q of quads) {
      u8(q.sprite);
      for (const v of q.v) {
        v.p.forEach(c => i16(Math.max(-32767, Math.min(32767, Math.round(c * 8192)))));
        v.n.forEach(c => u8(Math.max(-127, Math.min(127, Math.round(c * 127)))));
        v.uv.forEach(c => u16(Math.round(c * 65535)));
      }
    }
  }
  return zlib.gzipSync(Buffer.concat(parts), {level: 9});
}

// ---------------------------------------------------------------- flint sprites (the boulders' photo, regraded)

function loadSharp() {
  for (const p of [path.join(ROOT, 'wiki/package.json'), path.resolve(ROOT, '../Apocalypse First Light/wiki/package.json')]) {
    try { return createRequire(p)('sharp'); } catch { /* next */ }
  }
  throw new Error('sharp not found: run npm --prefix wiki install');
}

/** A photo averaged down to RES, channels 0..1 (as build-boulders.mjs reads it). */
async function photo(sharp, name, grey = false) {
  const s = sharp(path.join(CACHE, name));
  const {data, info} = await (grey ? s.greyscale() : s.removeAlpha()).raw().toBuffer({resolveWithObject: true});
  const c = grey ? 1 : 3, k = info.width / RES, o = new Float32Array(RES * RES * c);
  for (let y = 0; y < RES; y++) for (let x = 0; x < RES; x++) for (let ch = 0; ch < c; ch++) {
    let s2 = 0;
    for (let j = 0; j < k; j++) for (let i = 0; i < k; i++) s2 += data[((y * k + j) * info.width + x * k + i) * info.channels + ch];
    o[(y * RES + x) * c + ch] = s2 / (k * k * 255);
  }
  return o;
}

function blurWrap(a, r) {
  let src = a;
  for (let pass = 0; pass < 4; pass++) {
    const dst = new Float32Array(src.length), hor = pass % 2 === 0;
    for (let y = 0; y < RES; y++) for (let x = 0; x < RES; x++) {
      let s = 0;
      for (let d = -r; d <= r; d++) s += src[(hor ? y : (y + d + RES) % RES) * RES + (hor ? (x + d + RES) % RES : x)];
      dst[y * RES + x] = s / (2 * r + 1);
    }
    src = dst;
  }
  return src;
}

/** Periodic value noise over the tile (cells of c texels), 0..1. */
function pnoise(x, y, c, seed) {
  const n = RES / c, xi = Math.floor(x / c), yi = Math.floor(y / c), sm = t => t * t * (3 - 2 * t);
  const h = (i, j) => hash(((i % n) + n) % n, ((j % n) + n) % n, seed);
  const tx = sm(x / c - xi), ty = sm(y / c - yi);
  const a = h(xi, yi) + (h(xi + 1, yi) - h(xi, yi)) * tx, b = h(xi, yi + 1) + (h(xi + 1, yi + 1) - h(xi, yi + 1)) * tx;
  return a + (b - a) * ty;
}

const FLINT = [0.33, 0.30, 0.26], CORTEX = [0.80, 0.77, 0.70];

/**
 * flint: grey brown with a faint cloudy banding (low-frequency noise) and the photo's mottle kept very softly, waxy
 * (LabPBR smoothness about 0.8), dense (porosity low), a weak normal; cortex: chalk white with the photo's grain strong,
 * rough, porous, its full normal. Returns {albedo, s, n} RGBA buffers.
 */
async function flintSprite(sharp, cortex) {
  const rs = await photo(sharp, 'rock_surface_diff_1k.jpg'), rn = await photo(sharp, 'rock_surface_nor_gl_1k.jpg');
  const rough = await photo(sharp, 'rock_surface_rough_1k.jpg', true);
  const L = new Float32Array(RES * RES).map((_, i) => 0.2126 * rs[i * 3] + 0.7152 * rs[i * 3 + 1] + 0.0722 * rs[i * 3 + 2]);
  const mean = L.reduce((a, v) => a + v, 0) / L.length, low = blurWrap(L, 12);
  const albedo = Buffer.alloc(RES * RES * 4), sB = Buffer.alloc(RES * RES * 4), nB = Buffer.alloc(RES * RES * 4);
  for (let y = 0; y < RES; y++) for (let x = 0; x < RES; x++) {
    const i = y * RES + x, detail = (L[i] - low[i]) / mean, mott = low[i] / mean - 1;
    let c, smooth, poro, ns;
    if (cortex) {
      const v = 1 + 1.6 * detail + 0.5 * mott;
      c = CORTEX.map(b => b * v);
      smooth = 20 + (1 - rough[i]) * 40; poro = 60; ns = 0.9;
    } else {
      const band = pnoise(x + 7 * Math.sin(y / 23), y, 32, 9) - 0.5, cloud = pnoise(x, y, 64, 10) - 0.5;
      const v = 1 + 0.35 * detail + 0.3 * mott + 0.22 * band + 0.25 * cloud;
      c = FLINT.map((b, k) => b * v * (k === 0 ? 1 + 0.1 * cloud : 1));
      smooth = 190 + (1 - rough[i]) * 30; poro = 6; ns = 0.3;
    }
    albedo.set(c.map(a => Math.round(Math.max(0, Math.min(1, a)) * 255)).concat(255), i * 4);
    sB.set([Math.round(smooth), 10, poro, 255], i * 4);
    const n = V.norm([(rn[i * 3] * 2 - 1) * ns, (rn[i * 3 + 1] * 2 - 1) * ns, rn[i * 3 + 2] * 2 - 1]);
    nB.set([Math.round((n[0] * 0.5 + 0.5) * 255), Math.round((-n[1] * 0.5 + 0.5) * 255), 255, 255], i * 4);
  }
  return {albedo, s: sB, n: nB};
}

/**
 * A rock's worn sprite (granite_worn, limestone_worn ...) from the boulders' sprite of that rock: grain contrast x 0.65 about
 * its local mean, normal relief x 0.5, smoothness + 45.
 */
async function wornSprite(sharp, id) {
  const dir = path.join(RES_DIR, 'textures/block/boulder'), out = new Map();
  const raw = async f => (await sharp(path.join(dir, f)).ensureAlpha().raw().toBuffer({resolveWithObject: true}));
  const a = await raw(id + '.png'), s = await raw(id + '_s.png'), n = await raw(id + '_n.png');
  if (a.info.width !== RES || s.info.width !== RES || n.info.width !== RES) throw new Error(id + ' sprite size');
  const L = new Float32Array(RES * RES).map((_, i) => (a.data[i * 4] + a.data[i * 4 + 1] + a.data[i * 4 + 2]) / 765);
  const low = blurWrap(L, 6), A = Buffer.from(a.data), S = Buffer.from(s.data), N = Buffer.from(n.data);
  for (let i = 0; i < RES * RES; i++) {
    const k = L[i] > 1e-3 ? (low[i] + (L[i] - low[i]) * 0.65) / L[i] : 1;
    for (let c = 0; c < 3; c++) A[i * 4 + c] = Math.max(0, Math.min(255, Math.round(a.data[i * 4 + c] * k)));
    S[i * 4] = Math.min(235, s.data[i * 4] + 45);
    const nx = (n.data[i * 4] / 255 * 2 - 1) * 0.5, ny = (n.data[i * 4 + 1] / 255 * 2 - 1) * 0.5;
    N[i * 4] = Math.round((nx * 0.5 + 0.5) * 255); N[i * 4 + 1] = Math.round((ny * 0.5 + 0.5) * 255);
  }
  for (const [k, b] of [['', A], ['_s', S], ['_n', N]])
    out.set(path.join(dir, id + '_worn' + k + '.png'), await sharp(b, {raw: {width: RES, height: RES, channels: 4}}).png().toBuffer());
  return out;
}

export async function flintSprites() {
  const sharp = loadSharp(), out = new Map();
  for (const id of ROCK_LOOKS) for (const [f, b] of await wornSprite(sharp, id)) out.set(f, b);
  for (const [name, cortex] of [['flint', false], ['flint_cortex', true]]) {
    const sp = await flintSprite(sharp, cortex);
    for (const [k, b] of Object.entries(sp))
      out.set(path.join(RES_DIR, 'textures/block/boulder', name + (k === 'albedo' ? '' : '_' + k) + '.png'),
        await sharp(b, {raw: {width: RES, height: RES, channels: 4}}).png().toBuffer());
  }
  return out;
}

const isMain = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  const check = process.argv.includes('--check');
  let bad = 0;
  const built = STONES.map((s, i) => ({s, ...quadsOf(s, i)}));
  for (const {s, m, quads, height} of built) {
    const b = bounds(m), size = [0, 1, 2].map(k => (b[k][1] - b[k][0]) * 100);
    const uvOk = quads.every(q => q.v.every(v => v.uv.every(c => c >= 0 && c <= 1)));
    const ok = closed(m) && uvOk && volume(m) > 0;
    if (!ok) bad++;
    console.log(`${s.id.padEnd(15)} ${size.map(v => v.toFixed(1)).join(' x ')} cm  ${m.T.length} tris  ${(volume(m) * 2700).toFixed(3)} kg granite${ok ? '' : '  BAD (' + (closed(m) ? '' : 'open ') + (uvOk ? '' : 'uv') + ')'}  h ${height.toFixed(3)}`);
  }
  const outputs = new Map([[path.join(RES_DIR, 'boulders/loose_r1.bin'), encode(built)], ...(await flintSprites())]);
  let stale = 0;
  for (const [file, buf] of outputs) {
    if (check) { if (!fs.existsSync(file) || !fs.readFileSync(file).equals(buf)) { console.log('stale ' + path.relative(ROOT, file)); stale++; } }
    else { fs.mkdirSync(path.dirname(file), {recursive: true}); fs.writeFileSync(file, buf); }
  }
  if (bad || (check && stale)) process.exit(1);
  console.log(check ? 'CHECK OK' : 'written');
}
