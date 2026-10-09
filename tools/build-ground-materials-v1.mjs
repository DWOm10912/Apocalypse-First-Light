// Ground Materials V1 (docs/models/ground_materials_v1.md): pavement and floor finishes for Fuel Stop A1 and everything
// after it, at the facade-brick resolution (240 x 240 texels per block, LabPBR _s / _n, no per-texel noise).
//   reinforced_concrete    (redo of the 16 px block)  formed structural concrete: soft mottle, a few bug holes
//   asphalt                (redo of the 16 px block)  aged dense-graded asphalt: dark binder, exposed aggregate
//   concrete_sidewalk      (new)  broom finish across the walk, tooled joints every 2 m with a smooth trowelled margin
//   concrete_pavement      (new)  forecourt / tank pad / aprons: lighter broom, saw-cut joints every 4 m
//   sealed_concrete_floor  (new)  back of house: steel-trowelled, sealed (sheen, burnish clouds)
//   porcelain_floor_tile   (new)  sales floor: 0.5 m concrete-look porcelain, 4 tiles per block
//   restroom_floor_tile    (new)  restrooms: 0.25 m darker porcelain, 16 tiles per block
// Round 1 (2026-10-08): textures and previews; the user kept 240 texels per block (AFL's own man-made style) and had them
// put into the game the same day.
//   node tools/build-ground-materials-v1.mjs                 -> writes textures, block / item models, blockstates
//   node tools/build-ground-materials-v1.mjs --check         -> verifies every output is up to date
//   node tools/build-ground-materials-v1.mjs --preview DIR   -> writes only the texture sets into DIR (tools: refs/gm_view.html)
// reinforced_concrete and asphalt keep their texture paths (variant 0 at textures/block/<id>, so the slab and stairs models
// stay as they are), variants 1 and 2 beside them; the new blocks live under textures/block/ground/.
// Seams: a material that is one continuous surface (concrete, asphalt) shares everything that touches a block edge between
// its variants (the low-frequency mottle is periodic per block; variant-only detail is windowed to zero at the edges and
// stones / holes near an edge come from one shared set), so any variant sits next to any other without a seam. Tiles change
// only inside a tile (the grout hides it). Joints of the sidewalk / pavement lie on block edges, half on each side; the
// textures carry them on the west / north edges and the block picks mirror / transpose by world position (joint lines at
// world multiples of 2 or 4 m) and by its axis (broom lines run across the walk).
// Heights are millimetres (0 = the finished surface, negative = recessed); 1 texel = 4.17 mm. _n: DirectX (red right, green
// down) from the height slopes, B ambient occlusion, A height (1 - depth / 250 mm). _s: R smoothness, G F0 (dielectric),
// B porosity (0..64, wets in rain), A 255 (no emission).
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {png} from './cube-slab-mesh-lib.mjs';

const N = 240, SS = 3, MM = 1000 / N;
const hash = (a, b, c) => { let h = Math.imul(a | 0, 374761393) ^ Math.imul(b | 0, 668265263) ^ Math.imul(c | 0, 2147483647); h = Math.imul(h ^ (h >>> 13), 1274126177); return ((h ^ (h >>> 16)) >>> 0) / 4294967295; };
const smooth = t => t * t * (3 - 2 * t), clamp = (v, a, b) => Math.max(a, Math.min(b, v));
/** Periodic value noise (period N on both axes), cells cx x cy texels (both divide N), -1..1. */
function vn(x, y, seed, cx, cy = cx) {
  const nx = N / cx, ny = N / cy, xi = Math.floor(x / cx), yi = Math.floor(y / cy);
  const h = (i, j) => hash(((i % nx) + nx) % nx, ((j % ny) + ny) % ny, seed);
  const tx = smooth(x / cx - xi), ty = smooth(y / cy - yi);
  const a = h(xi, yi) + (h(xi + 1, yi) - h(xi, yi)) * tx, b = h(xi, yi + 1) + (h(xi + 1, yi + 1) - h(xi, yi + 1)) * tx;
  return 2 * (a + (b - a) * ty) - 1;
}
/** 1 in the middle of the block, 0 on its edges: variant-only detail never reaches a neighbour. */
const win = (x, y) => (Math.sin(Math.PI * x / N) * Math.sin(Math.PI * y / N)) ** 2;
const mul = (c, t) => c.map(v => v * t), mix = (a, b, t) => a.map((v, i) => v + (b[i] - v) * t);

// ---- scattered features (stones, bug holes): a jittered grid; near the edges from one shared seed, inside per variant ----
function scatter(count, seed, sharedSeed, margin, make) {
  const out = [], g = Math.round(N / Math.sqrt(count));
  for (const [s, edge] of [[sharedSeed, true], [seed, false]]) for (let j = 0; j < N / g; j++) for (let i = 0; i < N / g; i++) {
    if (hash(i, j, s + 1) > 0.92) continue;
    const x = (i + 0.15 + 0.7 * hash(i, j, s + 2)) * g, y = (j + 0.15 + 0.7 * hash(i, j, s + 3)) * g;
    const near = x < margin || x > N - margin || y < margin || y > N - margin;
    if (near !== edge) continue;
    out.push(make(x, y, (k) => hash(i, j, s + 10 + k)));
  }
  const bins = new Map(), B = 12, key = (a, b) => a + ',' + b;
  for (const f of out) for (const dx of [-N, 0, N]) for (const dy of [-N, 0, N]) {   // wrapped copies: features crossing an edge
    const fx = f.x + dx, fy = f.y + dy;
    if (fx < -8 || fx > N + 8 || fy < -8 || fy > N + 8) continue;
    const b = key(Math.floor(fx / B), Math.floor(fy / B));
    (bins.get(b) || bins.set(b, []).get(b)).push({...f, x: fx, y: fy});
  }
  return (x, y) => bins.get(key(Math.floor(x / B), Math.floor(y / B))) || [];
}
const blobR = (f, dx, dy) => { const a = Math.atan2(dy, dx); return f.r * (1 + f.h1 * Math.sin(2 * a + f.p1) + f.h2 * Math.sin(3 * a + f.p2) + f.h3 * Math.sin(5 * a + f.p3)); };
// a blob's footprint has to fit one bin neighbourhood: radius < B / 2
const blobsAt = (lookup, x, y) => { const B = 12, out = []; for (const dx of [-B, 0, B]) for (const dy of [-B, 0, B]) for (const f of lookup(x + dx, y + dy)) if (!out.includes(f)) out.push(f); return out; };

// ---- materials: f(x, y) at texel coordinates -> {c sRGB, s smoothness, p porosity, h mm, ao 0..1} ----
function reinforcedConcrete(v) {
  const base = [150, 149, 144];
  // bug holes: few, shallow, barely darker (2026-10-08: the first version read as dirt specks on a wall)
  const holes = scatter(6, 500 + v * 31, 499, 6, (x, y, r) => ({x, y, r: 0.7 + 0.9 * r(0), d: 1.5 + 1.5 * r(1), h1: 0.15 * r(2), p1: 6 * r(3), h2: 0.1 * r(4), p2: 6 * r(5), h3: 0, p3: 0}));
  return (x, y) => {
    let t = 1 + 0.025 * vn(x, y, 11, 48) + 0.010 * vn(x, y, 12, 20) + 0.02 * win(x, y) * vn(x, y, 13 + v, 60);
    // fine sand in the cement paste: mostly relief (normal map), the colour barely moves
    const sand = 0.6 * vn(x, y, 15, 4) + 0.4 * vn(x, y, 16, 6);
    let c = mul(base, t * (1 + 0.008 * vn(x, y, 17, 8))), s = 70, p = 38, h = 0.08 * vn(x, y, 14, 24) + 0.6 * sand, ao = 1;
    for (const f of blobsAt(holes, x, y)) {
      const dx = x - f.x, dy = y - f.y, d = Math.hypot(dx, dy), R = blobR(f, dx, dy);
      if (d < R) { const k = 1 - (d / R) ** 2; c = mul(c, 1 - 0.13 * Math.sqrt(k)); h -= f.d * Math.sqrt(k); s = 50; ao = Math.min(ao, 1 - 0.2 * k); }
    }
    return {c, s, p, h, ao};
  };
}
/**
 * Aged asphalt: aggregate packed edge to edge (periodic Voronoi cells, about 2 cm), the binder in the crevices and in the
 * cells without an exposed stone. Cells within two of a block edge come from one shared seed, so variants join seamlessly.
 */
function asphalt(v) {
  const binder = [54, 54, 56], stone = [74, 73, 70], G = 5, C = N / G;
  const cell = (i, j) => {
    const ii = ((i % C) + C) % C, jj = ((j % C) + C) % C, edge = ii < 2 || ii > C - 3 || jj < 2 || jj > C - 3, s = edge ? 699 : 700 + v * 31;
    return {x: (i + 0.1 + 0.8 * hash(ii, jj, s + 1)) * G, y: (j + 0.1 + 0.8 * hash(ii, jj, s + 2)) * G,
      exposed: hash(ii, jj, s + 3) < 0.7, tone: 0.86 + 0.28 * hash(ii, jj, s + 4), up: 0.35 + 0.6 * hash(ii, jj, s + 5)};
  };
  return (x, y) => {
    const ci = Math.floor(x / G), cj = Math.floor(y / G);
    let d1 = Infinity, d2 = Infinity, near = null;
    for (let dj = -1; dj <= 1; dj++) for (let di = -1; di <= 1; di++) {
      const f = cell(ci + di, cj + dj), d = Math.hypot(x - f.x, y - f.y);
      if (d < d1) { d2 = d1; d1 = d; near = f; } else if (d < d2) d2 = d;
    }
    const t = 1 + 0.03 * vn(x, y, 21, 48) + 0.015 * vn(x, y, 22, 20) + 0.025 * win(x, y) * vn(x, y, 23 + v, 60);
    const border = (d2 - d1) / 2;   // distance to the cell edge
    if (!near.exposed || border < 0.45) return {c: mul(binder, t), s: 82, p: 58, h: near.exposed ? -0.3 : -0.1, ao: border < 0.45 ? 0.85 : 1};
    const k = Math.min(1, (border - 0.45) / 1.6);
    return {c: mul(mix(binder, mul(stone, near.tone), 0.45 + 0.55 * k), t), s: 58, p: 42, h: near.up * Math.sqrt(k), ao: 1};
  };
}
/** Broom lines along x (the texture's u): long streaks a couple of texels apart, wandering slowly. */
function broom(x, y, amp) {
  const w1 = 0.45 * vn(x, 0.5, 31, 60, 240), w2 = 0.3 * vn(x, 0.5, 32, 40, 240);
  return amp * (0.6 * vn(x, y + w1, 33, 120, 2) + 0.4 * vn(x, y + w2, 34, 60, 3));
}
/** Distance (texels) from the joint edges this texture carries (w: x = 0, n: y = 0), Infinity when none. */
const jointDist = (x, y, j) => Math.min(j.w ? x : Infinity, j.n ? y : Infinity);
function concreteSidewalk(j) {
  const base = [174, 172, 166];
  return (x, y) => {
    const d = jointDist(x, y, j), t = 1 + 0.025 * vn(x, y, 41, 48) + 0.010 * vn(x, y, 42, 20);
    const b = broom(x, y, 0.55), fade = d < 13 ? 0 : d < 17 ? smooth((d - 13) / 4) : 1;
    let c = mul(base, t * (1 + 0.008 * b / 0.55 * fade) * (fade < 1 ? 1 - 0.012 * (1 - fade) : 1)), s = 48 + 37 * (1 - fade), p = 45, h = b * fade, ao = 1;
    if (d < 1.0) { c = mul(base, 0.5); h = -10; s = 30; ao = 0.55; }
    else if (d < 2.4) { const k = (d - 1.0) / 1.4; h = -6 * (1 - k) ** 2; c = mul(c, 1 - 0.08 * (1 - k)); ao = 1 - 0.2 * (1 - k); }
    return {c, s, p, h, ao};
  };
}
function concretePavement(j) {
  const base = [166, 164, 159];
  return (x, y) => {
    const d = jointDist(x, y, j), t = 1 + 0.025 * vn(x, y, 51, 48) + 0.010 * vn(x, y, 52, 20), b = broom(x, y, 0.42);
    let c = mul(base, t * (1 + 0.007 * b / 0.42)), s = 52, p = 45, h = b, ao = 1;
    if (d < 0.8) { c = mul(base, 0.42); h = -8; s = 30; ao = 0.5; }
    else if (d < 1.6) { const k = (d - 0.8) / 0.8; c = mul(c, 1 - 0.05 * (1 - k)); h = Math.min(h, -0.6 * (1 - k)); }
    return {c, s, p, h, ao};
  };
}
function sealedConcrete(v) {
  const base = [146, 144, 140];
  return (x, y) => {
    const cloud = 0.028 * vn(x, y, 61, 60) + 0.012 * vn(x, y, 62, 24) + 0.018 * win(x, y) * vn(x, y, 63 + v, 40);
    return {c: mul(base, 1 + cloud), s: 150 - 25 * cloud / 0.06, p: 8, h: 0, ao: 1};
  };
}
function tiles(T, tile, grout, sTile, v, seed) {
  const G = 1.0;
  return (x, y) => {
    const tx = ((x % T) + T) % T, ty = ((y % T) + T) % T, ix = Math.floor(x / T), iy = Math.floor(y / T);
    const d = Math.min(tx, T - tx, ty, T - ty);
    if (d < G) return {c: grout, s: 40, p: 35, h: -2, ao: 0.8};
    const id = seed + v * 101 + ix * 7 + iy * 13;
    const t = 1 + 0.025 * (2 * hash(ix, iy, seed + v * 53) - 1) + 0.018 * vn(x, y, id, 30) + 0.008 * vn(x, y, id + 1, 12);
    const h = d < G + 1 ? -0.5 * (1 - (d - G)) ** 2 : 0;
    return {c: mul(tile, t), s: sTile, p: 3, h, ao: 1};
  };
}

// ---- rasterise: supersampled colour / smoothness / porosity / height / AO, slopes with per-edge continuation ----
function render(f, edges = {w: 'wrap', e: 'wrap', n: 'wrap', s: 'wrap'}) {
  const col = Buffer.alloc(N * N * 4), spec = Buffer.alloc(N * N * 4), nrm = Buffer.alloc(N * N * 4), H = new Float64Array(N * N);
  for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
    let c = [0, 0, 0], s = 0, p = 0, h = 0, ao = 0;
    for (let sy = 0; sy < SS; sy++) for (let sx = 0; sx < SS; sx++) {
      const r = f(x + (sx + 0.5) / SS, y + (sy + 0.5) / SS);
      c = c.map((v, i) => v + r.c[i]); s += r.s; p += r.p; h += r.h; ao += r.ao;
    }
    const k = SS * SS, o = (y * N + x) * 4;
    c.forEach((v, i) => { col[o + i] = clamp(Math.round(v / k), 0, 255); }); col[o + 3] = 255;
    spec[o] = clamp(Math.round(s / k), 0, 255); spec[o + 1] = 14; spec[o + 2] = clamp(Math.round(p / k), 0, 64); spec[o + 3] = 255;
    H[y * N + x] = h / k; nrm[o + 2] = clamp(Math.round(255 * ao / k), 0, 255);
  }
  const idx = (i, lo, hi) => i < 0 ? (lo === 'wrap' ? i + N : -i - 1) : i >= N ? (hi === 'wrap' ? i - N : 2 * N - 1 - i) : i;
  const at = (i, j) => H[idx(j, edges.n, edges.s) * N + idx(i, edges.w, edges.e)];
  for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
    const hx = (at(x + 1, y) - at(x - 1, y)) / (2 * MM), hy = (at(x, y + 1) - at(x, y - 1)) / (2 * MM);
    let nx = -hx, ny = -hy; const l = Math.hypot(nx, ny, 1); nx /= l; ny /= l;
    const o = (y * N + x) * 4;
    nrm[o] = Math.round((nx * 0.5 + 0.5) * 255); nrm[o + 1] = Math.round((ny * 0.5 + 0.5) * 255);
    nrm[o + 3] = clamp(Math.round(255 * (1 + H[y * N + x] / 250)), 0, 255);
  }
  return [png(col, N, N), png(spec, N, N), png(nrm, N, N)];
}

/** Every texture set: [name, maps]. */
export function textures() {
  const out = [];
  for (let v = 0; v < 3; v++) {
    out.push([`reinforced_concrete_${v}`, render(reinforcedConcrete(v))]);
    out.push([`asphalt_${v}`, render(asphalt(v))]);
    out.push([`sealed_concrete_floor_${v}`, render(sealedConcrete(v))]);
    out.push([`porcelain_floor_tile_${v}`, render(tiles(120, [186, 182, 174], [158, 153, 145], 150, v, 800))]);
    out.push([`restroom_floor_tile_${v}`, render(tiles(60, [112, 110, 106], [88, 86, 83], 130, v, 900))]);
  }
  // joints on the west / north edges continue mirrored across that edge (the neighbour carries the other half)
  // every sidewalk block has one x and one z joint (2 m), so each neighbour is this texture mirrored: all edges continue mirrored
  out.push(['concrete_sidewalk_corner', render(concreteSidewalk({w: true, n: true}), {w: 'mirror', e: 'mirror', n: 'mirror', s: 'mirror'})]);
  out.push(['concrete_sidewalk_plain', render(concreteSidewalk({}))]);   // sides and bottom
  for (const [k, j] of [['plain', {}], ['edge_w', {w: true}], ['edge_n', {n: true}], ['corner', {w: true, n: true}]])
    out.push([`concrete_pavement_${k}`, render(concretePavement(j), {w: j.w ? 'mirror' : 'wrap', e: 'wrap', n: j.n ? 'mirror' : 'wrap', s: 'wrap'})]);
  return out;
}

// ---- assets ----
const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const ASSETS = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight');
const json = v => JSON.stringify(v, null, 2) + '\n';
const NS = 'apocalypse_firstlight:block/';
/** Texture path (under textures/block/, no extension) of a generated set. */
function texturePath(name) {
  const m = /^(reinforced_concrete|asphalt)_(\d)$/.exec(name);
  if (m) return m[2] === '0' ? m[1] : name;
  return 'ground/' + name;
}
export function outputs() {
  const out = [], tex = textures();
  for (const [name, maps] of tex) ['', '_s', '_n'].forEach((k, i) => out.push([path.join(ASSETS, 'textures/block', texturePath(name) + k + '.png'), maps[i]]));
  for (const id of ['reinforced_concrete', 'asphalt']) {   // variant 0 keeps the old model (slab / stairs share its texture)
    const models = [0, 1, 2].map(v => v === 0 ? id : id + '_' + v);
    for (const m of models) out.push([path.join(ASSETS, 'models/block', m + '.json'), json({parent: 'minecraft:block/cube_all', textures: {all: NS + m}})]);
    out.push([path.join(ASSETS, 'blockstates', id + '.json'), json({variants: {'': models.map(m => ({model: 'apocalypse_firstlight:block/' + m}))}})]);
  }
  for (const id of ['sealed_concrete_floor', 'porcelain_floor_tile', 'restroom_floor_tile']) {
    const models = [0, 1, 2].map(v => 'ground/' + id + '_' + v);
    for (const m of models) out.push([path.join(ASSETS, 'models/block', m + '.json'), json({parent: 'minecraft:block/cube_all', textures: {all: NS + m}})]);
    out.push([path.join(ASSETS, 'blockstates', id + '.json'), json({variants: {'': models.map(m => ({model: 'apocalypse_firstlight:block/' + m}))}})]);
    out.push([path.join(ASSETS, 'models/item', id + '.json'), json({parent: 'apocalypse_firstlight:block/' + models[0]})]);
  }
  // jointed flatwork: the block model picks the top by world position (client/GroundJointModel); the item shows a corner
  const jointed = {
    concrete_sidewalk: {spacing: 2, plain: 'concrete_sidewalk_plain', edge_w: 'concrete_sidewalk_corner', edge_n: 'concrete_sidewalk_corner', corner: 'concrete_sidewalk_corner'},
    concrete_pavement: {spacing: 4, plain: 'concrete_pavement_plain', edge_w: 'concrete_pavement_edge_w', edge_n: 'concrete_pavement_edge_n', corner: 'concrete_pavement_corner'},
  };
  for (const [id, j] of Object.entries(jointed)) {
    const t = k => NS + 'ground/' + j[k];
    out.push([path.join(ASSETS, 'models/block/ground', id + '.json'), json({loader: 'apocalypse_firstlight:ground_joints', spacing: j.spacing,
      textures: {particle: t('plain'), plain: t('plain'), edge_w: t('edge_w'), edge_n: t('edge_n'), corner: t('corner'), side: t('plain')}})]);
    out.push([path.join(ASSETS, 'models/block/ground', id + '_item.json'), json({parent: 'minecraft:block/cube',
      textures: {particle: t('plain'), up: t('corner'), down: t('plain'), north: t('plain'), south: t('plain'), east: t('plain'), west: t('plain')}})]);
    out.push([path.join(ASSETS, 'blockstates', id + '.json'), json({variants: {'axis=x': {model: 'apocalypse_firstlight:block/ground/' + id}, 'axis=z': {model: 'apocalypse_firstlight:block/ground/' + id}}})]);
    out.push([path.join(ASSETS, 'models/item', id + '.json'), json({parent: 'apocalypse_firstlight:block/ground/' + id + '_item'})]);
  }
  return out;
}

const isMain = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  const at = process.argv.indexOf('--preview');
  if (at >= 0) {
    const dir = path.resolve(process.argv[at + 1]);
    fs.mkdirSync(dir, {recursive: true});
    let n = 0;
    for (const [name, maps] of textures()) ['', '_s', '_n'].forEach((k, i) => { fs.writeFileSync(path.join(dir, `${name}${k}.png`), maps[i]); n++; });
    console.log('wrote ' + n + ' textures to ' + dir);
  } else if (process.argv.includes('--check')) {
    for (const [file, data] of outputs()) { const cur = fs.existsSync(file) ? fs.readFileSync(file) : null; if (!cur || !cur.equals(Buffer.isBuffer(data) ? data : Buffer.from(data))) throw new Error('stale ' + path.relative(ROOT, file)); }
    console.log('CHECK OK');
  } else {
    const list = outputs();
    for (const [file] of list) fs.mkdirSync(path.dirname(file), {recursive: true});
    for (const [file, data] of list) fs.writeFileSync(file, data);
    console.log('wrote ' + list.length + ' files');
  }
}
