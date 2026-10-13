// ARCHIVED, NOT USED (2026-10-12): the M1.1 32 px pixel-art experiment for the cabin's logs, fieldstone and firewood.
// The user rejected it after the preview (it blurred the porch deck, posts, fieldstone and sheet roof); the 240 px set
// (tools/cabin/cabin-art.mjs) stays. Kept for reference only; its imports point at tools/ and it is not run by any build.

// Cabin Exterior V1, M1.1 (docs/models/cabin_exterior_v1.md, 2026-10-12): the natural materials of the forest start home
// as pixel art, in the language of the Mesh Trees' 32 px bark (tools/mesh-trees/bark-art.mjs). User 2026-10-12: the 240 px
// logs, fieldstone and firewood read "too real" next to the trees and the grass; AFL = real proportions + fine Mesh
// geometry + stylised materials. The mesh carries the form (log bulges, corrugation, deck boards); a texture is flat tone
// areas, 1 px lit edges on the light side (above / left), 1 px shade on the other, runs of 3+ px for grain, no lone
// pixels, no gradients, no wear speckle. Normals from the same classes, gentle (under 10 degrees), height alpha 255.
//   white_oak_log_wall   48 x 48 per block (three logs of 16 px: 32 px cannot split into the wall's thirds and mipmaps
//                        need a multiple of 16), a log 14 px + chinking 2 px, about 2 cm a pixel
//   white_oak_log_round  48 x 48, the corner log ends' skin (about 2.3 cm a pixel round the log)
//   white_oak_log_end    16 x 16, the end disc (15 px across a 34 cm log: about 2.3 cm a pixel)
//   fieldstone           96 x 96 over 3 x 3 blocks (32 px a metre): the Mesh fieldstone picks its third by the block
//                        position, so a wall has no per-block repeat and no seam (CabinBlocks.Fieldstone)
//   firewood_end(_2/_3)  32 x 32, three variants (the blockstate picks one per position); the pieces stay inside the
//   firewood_side(_2/_3) block (a 1 px gap on its west / north border), so any two variants sit side by side
import {png} from '../cube-slab-mesh-lib.mjs';
import {hash, clamp, mix, mul} from '../mesh-trees/hd-art.mjs';

/** A random 0..1, hashed twice (hd-art's hash alone is correlated over consecutive ids). */
const rnd = (a, b, s) => hash(Math.floor(hash(a, b, s) * 4294967295) | 0, b * 7 + 3, s + 77);

/**
 * The three LabPBR maps from per-pixel {col, h (mm), ao, s, f0, p}: colour, _s (smoothness, F0, porosity), _n (DirectX
 * normal from the wrapped height, AO, height alpha 255: no parallax). mm: millimetres per pixel.
 */
function maps(px, W, mm) {
  const col = Buffer.alloc(W * W * 4), spec = Buffer.alloc(W * W * 4), nrm = Buffer.alloc(W * W * 4);
  const wrap = i => ((i % W) + W) % W, H = (x, y) => px[wrap(y) * W + wrap(x)].h;
  for (let y = 0; y < W; y++) for (let x = 0; x < W; x++) {
    const i = y * W + x, o = i * 4, q = px[i];
    q.col.forEach((v, k) => { col[o + k] = clamp(Math.round(v), 0, 255); }); col[o + 3] = 255;
    spec[o] = clamp(Math.round(q.s), 0, 255); spec[o + 1] = q.f0 ?? 10; spec[o + 2] = q.p ?? 40; spec[o + 3] = 255;
    const hx = (H(x + 1, y) - H(x - 1, y)) / (2 * mm), hy = (H(x, y + 1) - H(x, y - 1)) / (2 * mm), l = Math.hypot(hx, hy, 1);
    nrm[o] = Math.round((-hx / l * 0.5 + 0.5) * 255); nrm[o + 1] = Math.round((-hy / l * 0.5 + 0.5) * 255);
    nrm[o + 2] = clamp(Math.round(255 * (q.ao ?? 1)), 0, 255); nrm[o + 3] = 255;
  }
  return [png(col, W, W), png(spec, W, W), png(nrm, W, W)];
}

// ------------------------------------------------------------------------------------------------- white oak logs

/** Aged, oiled white oak in honey brown (the 240 px set's colours, as flat tones). */
const LOG = {tones: [[146, 110, 75], [138, 104, 70], [151, 114, 78]], light: [170, 134, 94], knot: [98, 72, 48], core: [76, 56, 38],
  chink: [194, 184, 162]};

/**
 * Grain for a band of rows: runs of 4-12 px along u (one or two a row, darker or lighter than the tone), wrapping. Returns
 * a map row -> [{x0, len, dark}].
 */
function streaks(W, rows, seed) {
  const out = new Map();
  for (const r of rows) {
    const n = 1 + Math.floor(2 * rnd(r, 1, seed)), list = [];
    for (let j = 0; j < n; j++) list.push({x0: Math.floor(W * rnd(r, 10 + j, seed)), len: 4 + Math.floor(9 * rnd(r, 20 + j, seed)), dark: rnd(r, 30 + j, seed) < 0.7});
    out.set(r, list);
  }
  return out;
}
const inRun = (W, x, s) => ((x - s.x0) % W + W) % W < s.len;

/**
 * The log wall's face: chinking centred on v 0, 1/3, 2/3 (rows 47 + 0, 15 + 16, 31 + 32; the upper row in the shade of the
 * log above), three logs of 14 rows in their own tones: the top row lit, the bottom row in shade, grain runs. No knots or
 * checks: the texture repeats every block, a distinct mark would line up along the whole wall.
 */
export function logWallPx() {
  const W = 48, seed = 7100, px = [];
  const logs = [0, 1, 2].map(k => ({
    tone: LOG.tones[k], grain: streaks(W, [2, 3, 4, 5, 6, 7, 8, 9, 10, 11], seed + 10 * k)}));
  for (let y = 0; y < W; y++) for (let x = 0; x < W; x++) {
    const m = ((y % 16) + 16) % 16;
    if (m === 15 || m === 0) {   // chinking: 15 under the log above (shade), 0 over the log below
      px.push(m === 15 ? {col: mul(LOG.chink, 0.84), h: -2, ao: 0.75, s: 40, p: 50} : {col: LOG.chink, h: -1.6, ao: 0.85, s: 40, p: 50});
      continue;
    }
    const L = logs[Math.floor(y / 16)], r = m - 1;   // 0..13 down the log
    let col = L.tone, h = 0, ao = 1;
    for (const s of L.grain.get(r) || []) if (inRun(W, x, s)) col = s.dark ? mul(L.tone, 0.88) : mix(L.tone, LOG.light, 0.35);
    if (r === 0) { col = mix(col, LOG.light, 0.45); h = 0.3; }
    else if (r === 13) { col = mul(col, 0.78); h = -0.6; ao = 0.9; }
    px.push({col, h, ao, s: 70, p: 22});
  }
  return maps(px, W, 1000 / W);
}

/** The corner log ends' skin (v round the log, u along it) and the wall's cut tops: the logs' tone, grain runs, two knots. */
export function logRoundPx() {
  const W = 48, seed = 7200, tone = [144, 108, 74], px = [];
  const grain = streaks(W, [...Array(W).keys()], seed);
  const knots = [0, 1].map(k => ({x: Math.floor(W * rnd(k, 1, seed)), y: Math.floor(W * rnd(k, 2, seed))}));
  for (let y = 0; y < W; y++) for (let x = 0; x < W; x++) {
    let col = tone;
    for (const s of grain.get(y)) if (inRun(W, x, s)) col = s.dark ? mul(tone, 0.88) : mix(tone, LOG.light, 0.35);
    for (const kn of knots) {
      const dx = ((x - kn.x) % W + W) % W, dy = ((y - kn.y) % W + W) % W;
      if (dy < 2 && dx < 3 && !(dy === 1 && dx !== 1)) col = LOG.knot;
      if (dy === 0 && dx === 1) col = LOG.core;
    }
    px.push({col, h: 0, ao: 1, s: 70, p: 22});
  }
  return maps(px, W, 1000 / W);
}

/**
 * The log end disc (radius 0.47 of the sprite round its middle, 7.5 px): weathered end grain, two 1 px rings, a darker
 * outer step and a 1 px rim, the pith, one radial drying check. Outside the disc: the rim colour (never drawn).
 */
export function logEndPx() {
  const W = 16, R = 0.47 * W, a0 = 0.8, px = [];
  for (let y = 0; y < W; y++) for (let x = 0; x < W; x++) {
    const dx = x + 0.5 - W / 2, dy = y + 0.5 - W / 2, d = Math.hypot(dx, dy), r = d / R;
    let col = [142, 114, 84], h = 0;
    if (r > 0.62) col = [130, 108, 84];
    if (Math.abs(r - 0.36) < 0.07 || Math.abs(r - 0.74) < 0.07) col = mul(col, 0.86);
    const ang = Math.abs(((Math.atan2(dy, dx) - a0 + 3 * Math.PI) % (2 * Math.PI)) - Math.PI);
    if (r > 0.15 && r < 0.86 && ang * d < 0.55) { col = [66, 50, 36]; h = -0.8; }
    if (r < 0.15) col = [86, 64, 44];
    if (r > 0.86) col = [104, 86, 66];
    px.push({col, h, ao: 1, s: 55, p: 40});
  }
  return maps(px, W, 340 / W);
}

// ------------------------------------------------------------------------------------------------- fieldstone

/**
 * Fieldstone over 3 x 3 m: weighted, slightly flattened Voronoi stones (8 x 9 sites, 25-45 cm), 1-2 px recessed joints
 * darker than the stones (the outlines read at a distance), corners rounded off; each stone one tone from six quiet greys
 * and browns, its top and left rim lit, its bottom and right rim in shade, a lit patch on the upper left of the bigger
 * ones; the joint under a stone in its shadow.
 */
export function fieldstonePx() {
  const W = 96, CX = 8, CY = 9, seed = 7400, cw = W / CX, ch = W / CY;
  const tones = [[132, 129, 122], [120, 120, 118], [142, 133, 118], [112, 109, 103], [150, 145, 135], [128, 117, 102]];
  const sites = [];
  for (let j = 0; j < CY; j++) for (let i = 0; i < CX; i++) sites.push({
    x: (i + 0.5 + 0.8 * (rnd(i, j, seed) - 0.5)) * cw, y: (j + 0.5 + 0.8 * (rnd(i, j, seed + 1) - 0.5)) * ch,
    w: -1.5 + 4 * rnd(i, j, seed + 2), tone: tones[Math.floor(6 * rnd(i, j, seed + 3))]});
  const wd = a => { const d = ((a % W) + W) % W; return d > W / 2 ? d - W : d; };
  const cls = [];   // per pixel: {id, e, dx, dy}
  for (let y = 0; y < W; y++) for (let x = 0; x < W; x++) {
    let f1 = 1e9, f2 = 1e9, id = 0, bx = 0, by = 0;
    sites.forEach((s, k) => {
      const dx = wd(x + 0.5 - s.x), dy = wd(y + 0.5 - s.y), d = Math.hypot(dx, dy * 1.2) - s.w;
      if (d < f1) { f2 = f1; f1 = d; id = k; bx = dx; by = dy; } else if (d < f2) f2 = d;
    });
    cls.push({id: f2 - f1 < 1.25 ? -1 : id, e: f2 - f1, dx: bx, dy: by});
  }
  const at = (x, y) => cls[(((y % W) + W) % W) * W + (((x % W) + W) % W)];
  // round the corners off (a stone pixel with three mortar sides), fill mortar pinholes inside one stone
  for (let pass = 0; pass < 2; pass++) for (let y = 0; y < W; y++) for (let x = 0; x < W; x++) {
    const c = at(x, y), n = [at(x - 1, y), at(x + 1, y), at(x, y - 1), at(x, y + 1)];
    if (c.id >= 0 && n.filter(q => q.id < 0).length >= 3) c.id = -1;
    else if (c.id < 0 && n.every(q => q.id >= 0 && q.id === n[0].id)) c.id = n[0].id;
  }
  const mortar = [104, 99, 91], light = [186, 180, 168], px = [];
  for (let y = 0; y < W; y++) for (let x = 0; x < W; x++) {
    const c = at(x, y), U = at(x, y - 1), D = at(x, y + 1), L = at(x - 1, y), R = at(x + 1, y);
    if (c.id < 0) {
      px.push(U.id >= 0 ? {col: mul(mortar, 0.82), h: -4, ao: 0.72, s: 30, p: 55} : {col: mortar, h: -3.5, ao: 0.8, s: 30, p: 55});
      continue;
    }
    const tone = sites[c.id].tone;
    let col = tone, h = 0, ao = 1;
    if (U.id < 0 || L.id < 0) { col = mix(tone, light, 0.3); h = -0.6; }
    else if (D.id < 0 || R.id < 0) { col = mul(tone, 0.8); h = -1.2; ao = 0.92; }
    else if (c.e > 7 && c.dx < -1 && c.dy < -1 && c.dx > -7 && c.dy > -6) col = mix(tone, light, 0.12);
    px.push({col, h, ao, s: 50, p: 15});
  }
  return maps(px, W, 1000 / 32);
}

// ------------------------------------------------------------------------------------------------- firewood

/**
 * A firewood stack's end, variant v: round pieces stacked in four staggered rows (radius 4.1-4.8 px: 26-30 cm across), dark
 * gaps between them (the triangles a woodpile shows), the block's west and north border a gap too. A piece is a whole round
 * (a 1 px bark ring, a growth ring, the pith) or a split half (bark on its round side, the light split face, the pith on
 * the split line); a few grey older pieces; the face lit toward the upper left.
 */
export function firewoodEndPx(v) {
  const W = 32, seed = 7600 + 50 * v, pieces = [];
  for (let j = 0; j < 4; j++) for (let i = 0; i < 4; i++) {
    const off = j % 2 ? 2 : -2 + 2 * rnd(j, 9, seed);
    const x = 4.5 + 8 * i + off + 1.2 * (rnd(i, j, seed) - 0.5), y = 4.5 + 7.6 * j + 1.2 * (rnd(i, j, seed + 1) - 0.5);
    const a = 6.283 * rnd(i, j, seed + 2);
    pieces.push({x, y, r: 4.1 + 0.7 * rnd(i, j, seed + 3), half: rnd(i, j, seed + 4) < 0.25, ux: Math.cos(a), uy: Math.sin(a),
      old: rnd(i, j, seed + 5) < 0.15, tone: 0.93 + 0.12 * rnd(i, j, seed + 6)});
  }
  const own = [];
  for (let y = 0; y < W; y++) for (let x = 0; x < W; x++) {
    let best = -1, bq = 1;
    pieces.forEach((p, k) => { const q = Math.hypot(x + 0.5 - p.x, y + 0.5 - p.y) / p.r; if (q < bq) { bq = q; best = k; } });
    own.push(x === 0 || y === 0 ? -1 : best);
  }
  const at = (x, y) => (x < 0 || y < 0 || x >= W || y >= W) ? -1 : own[y * W + x];
  const px = [];
  for (let y = 0; y < W; y++) for (let x = 0; x < W; x++) {
    const id = at(x, y);
    if (id < 0) { px.push({col: [50, 40, 32], h: -6, ao: 0.6, s: 20, p: 30}); continue; }
    const p = pieces[id], dx = x + 0.5 - p.x, dy = y + 0.5 - p.y, d = Math.hypot(dx, dy);
    const face = p.old ? [158, 138, 114] : [184, 150, 106], ring = p.old ? [140, 122, 100] : [160, 126, 88];
    const rim = p.r - d < 1.05;                       // the outermost pixel ring of the round
    let col = face, h = 0, ao = 1, bark = false;
    if (p.half) {
      const s = dx * p.ux + dy * p.uy;                // across the split: s > 0 the round side, the split line at s 0
      if (s < -0.6) { px.push({col: [50, 40, 32], h: -6, ao: 0.6, s: 20, p: 30}); continue; }   // the other half: gone
      if (rim && s > 0.5) { col = [88, 76, 62]; bark = true; }
      else if (Math.abs(Math.hypot(dx, dy) - 0.55 * p.r) < 0.5 && s > 0.5) col = ring;
      else if (s < 0.5) col = mul(face, 1.06);        // the split face's edge catches the light
      if (d < 0.9) col = [124, 94, 62];
    } else {
      if (rim) { col = [88, 76, 62]; bark = true; }
      else if (Math.abs(d - 0.55 * p.r) < 0.5) col = ring;
      if (d < 0.8) col = [124, 94, 62];
    }
    col = mul(col, p.tone);
    if (!bark) {
      if (at(x, y - 1) < 0 || at(x - 1, y) < 0) col = mul(col, 1.07);
      else if (at(x, y + 1) < 0 || at(x + 1, y) < 0) { col = mul(col, 0.86); h = -1; ao = 0.9; }
    } else if (dx + dy < 0) col = mix(col, [120, 108, 92], 0.4);   // the bark ring lit on the upper left
    px.push({col, h: bark ? -0.5 : h, ao, s: 40, p: 40});
  }
  return maps(px, W, 1000 / W);
}

/**
 * A firewood stack's side, variant v: columns of split pieces lying along v (4-6 px wide with a 1 px gap on their left),
 * two pieces per column (a joint on row 0 and one at 12..19), each bark (grey-brown, darker runs along it) or split face
 * (light, one grain run); the left column of a piece lit, the right in shade.
 */
const SIDE_COLS = [[5, 4, 5, 6, 4, 4, 4], [4, 6, 5, 4, 5, 4, 4], [6, 4, 4, 5, 5, 4, 4]];
export function firewoodSidePx(v) {
  const W = 32, seed = 7800 + 50 * v, cols = SIDE_COLS[v], px = new Array(W * W);
  let x0 = 0;
  cols.forEach((w, c) => {
    const joint = 12 + Math.floor(8 * rnd(c, 1, seed));
    for (let x = x0; x < x0 + w; x++) for (let y = 0; y < W; y++) {
      const piece = y < joint ? 0 : 1, gap = x === x0 || y === 0 || y === joint;
      if (gap) { px[y * W + x] = {col: [52, 42, 34], h: -6, ao: 0.6, s: 20, p: 30}; continue; }
      const bark = rnd(c, 10 + piece, seed) < 0.55, tone = 0.92 + 0.14 * rnd(c, 20 + piece, seed), lx = x - x0;
      let col = bark ? [104, 92, 78] : [172, 136, 94];
      if (bark) { const run = 3 + Math.floor(3 * rnd(x, 30 + piece, seed)), ph = Math.floor(run * rnd(x, 31, seed));
        if (lx > 1 && rnd(x * 64 + Math.floor((y + ph) / run), 32, seed) < 0.3) col = [86, 76, 64]; }
      else if (lx === 2 + Math.floor((w - 3) * rnd(c, 40 + piece, seed)) && (y + 3) % 11 > 3) col = [152, 118, 80];
      col = mul(col, tone);
      let h = 0, ao = 1;
      if (lx === 1) col = mul(col, 1.07);
      else if (lx === w - 1) { col = mul(col, 0.84); h = -1; ao = 0.9; }
      px[y * W + x] = {col, h, ao, s: bark ? 25 : 40, p: 40};
    }
    x0 += w;
  });
  return maps(px, W, 1000 / W);
}

/** Every pixel-art texture: name -> () => [colour, _s, _n] PNG buffers. */
export const PIXEL_TEXTURES = {
  white_oak_log_wall: logWallPx, white_oak_log_round: logRoundPx, white_oak_log_end: logEndPx, fieldstone: fieldstonePx,
  firewood_end: () => firewoodEndPx(0), firewood_end_2: () => firewoodEndPx(1), firewood_end_3: () => firewoodEndPx(2),
  firewood_side: () => firewoodSidePx(0), firewood_side_2: () => firewoodSidePx(1), firewood_side_3: () => firewoodSidePx(2),
};
