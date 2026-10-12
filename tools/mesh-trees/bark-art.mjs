// Mesh Trees bark, 32 x 32 pixel art (docs/rendering/mesh_trees_p4_formal.md W, 2026-10-12): the six species' bark with
// LabPBR _s / _n, imported by build-tree-textures.mjs (which writes and --checks them). Deterministic (fixed seeds).
// Why pixel art: a 240 px bark (clean plates outlined by thin dark furrows, strong normals) was tried the same day and
// rejected in game: next to the trees' own 32 px leaf cards and the 16 px grass it read as carved / vector-drawn. The
// trees and their raw products (bark, log blocks, stumps, notches) speak the leaves' pixel language; processed wood
// (planks, timber, beams: tools/build-wood-blocks-v1.mjs) stays at AFL's 240 px.
// Mapping (TreeShape#tube): a tile per metre along the trunk, round(2 pi r) tiles around, so a pixel is about 3 cm (twice
// vanilla's log density, about the leaf cards' 21-32 px per metre). Seamless both ways.
// Drawing: upright ridges between wandering furrows (bark is long, not cobbled: an even net of plates read as a stone
// wall), each furrow deep (2-3 px: the shadowed wall dark, the lit wall lighter) along most of its length and shallow (a
// 1 px line) where two ridges nearly meet; ridges broken into plates by 1 px cross cracks; a plate in one flat tone with
// a 1 px lit edge on the side the light comes from (upper left) and a 1 px shade on the other, upright streaks of 3+ px
// inside wide plates; no lone pixels, no gradients. Plate sizes are stylised up (about 15-25 cm) like the leaf cards.
// Species character from USDA Silvics / Virginia Tech dendrology (as the P0-P3 barks). Normals from the same classes,
// gentle (under 10 degrees: the colour already carries the form), height alpha 255 (no parallax on the curved trunk).
import {png} from '../cube-slab-mesh-lib.mjs';
import {hash, smooth, clamp, mix} from './hd-art.mjs';

/** Pixels per tile (per metre of trunk). */
export const M = 32;
const wrap = i => ((i % M) + M) % M;
/** A random 0..1, hashed twice (hd-art's hash alone is correlated over consecutive ids). */
const rnd = (a, b, s) => hash(Math.floor(hash(a, b, s) * 4294967295) | 0, b * 7 + 3, s + 77);
/** Periodic 1-D value noise over the tile height, knots every `knot` px (divides M), -1..1. */
function n1(y, seed, knot) {
  const n = M / knot, i = Math.floor(y / knot), t = smooth(y / knot - i), h = j => 2 * rnd(((j % n) + n) % n, 5, seed) - 1;
  return h(i) + (h(i + 1) - h(i)) * t;
}

const PLATE = 0, FURROW = 1, CRACK = 2;

/**
 * The ridge layout: o.K furrows, evenly spaced (jittered) and wandering o.A px (knots every o.knot px), o.wF px wide,
 * shallow (1 px, or none with o.shallow 0) over o.gaps[0..1] stretches of 4-9 px each; each ridge broken by
 * o.cracks[0..1] 1 px cross cracks (slant up to o.slant, a share o.full of them across the whole ridge, the rest from its
 * left furrow part-way). Returns per pixel {cls, deep (furrow at full depth), id (plate: ridge x 8 + segment), ridge,
 * e (px to the nearest furrow), u (0..1 across the ridge)}.
 */
function layout(o) {
  const {K, seed} = o, g = [];
  const base = [...Array(K).keys()].map(k => (k + 0.5 + (o.jit ?? 0.4) * (rnd(k, 1, seed) - 0.5)) * M / K);
  // shallow stretches per furrow: [start, length]
  const gaps = base.map((_, k) => {
    const n = o.gaps[0] + Math.floor((o.gaps[1] - o.gaps[0] + 1) * rnd(k, 2, seed));
    return [...Array(n).keys()].map(j => [Math.floor(M * (j + rnd(k, 3 + j, seed)) / n), 4 + Math.floor(6 * rnd(k, 9 + j, seed))]);
  });
  const furrowAt = (k, y) => {
    const c = base[k] + o.A * n1(y + 0.5, seed + 10 + k, o.knot);
    const sh = gaps[k].some(([s, l]) => wrap(y - s) < l), w = sh ? (o.shallow ?? 1) : o.wF;
    return {left: Math.round(c - w / 2), w, deep: !sh};
  };
  const cracks = base.map((_, k) => {
    const n = o.cracks[0] + Math.floor((o.cracks[1] - o.cracks[0] + 1) * rnd(k, 20, seed));
    return [...Array(n).keys()].map(j => ({
      y: M * (j + 0.15 + 0.7 * rnd(k, 21 + j, seed)) / n, s: o.slant * (2 * rnd(k, 31 + j, seed) - 1),
      reach: rnd(k, 41 + j, seed) < o.full ? 1 : 0.4 + 0.4 * rnd(k, 51 + j, seed)}));
  });
  for (let y = 0; y < M; y++) {
    const F = base.map((_, k) => furrowAt(k, y));
    for (let x = 0; x < M; x++) {
      // the furrow to the left (or under) this pixel, the next one to the right
      let k = 0, best = 1e9;
      F.forEach((f, i) => { const d = wrap(x - f.left); if (d < best) { best = d; k = i; } });
      const f = F[k], fn = F[(k + 1) % K], inF = best < f.w;
      const xr = best - f.w, width = wrap(fn.left - f.left - f.w) || M, toRight = wrap(fn.left - x);
      const e = inF ? 0 : Math.min(xr + 1, toRight);
      let cls = inF ? FURROW : PLATE, seg = 0, nFull = 0;
      if (!inF) for (const c of cracks[k]) {
        const yl = Math.floor(c.y + c.s * (xr - width / 2));
        if (c.reach === 1) { nFull++; if (wrap(yl) < y) seg++; }
        if (wrap(y - yl) === 0 && xr < c.reach * width) cls = CRACK;
      }
      g.push({cls, deep: inF && f.deep, id: k * 8 + (nFull ? seg % nFull : 0), ridge: k, e, u: (xr + 0.5) / width});
    }
  }
  return g;
}
const at = (g, x, y) => g[wrap(y) * M + wrap(x)];

/**
 * Colour, height (mm), AO and smoothness per pixel. Furrow: its rightmost pixel is the lit wall (faces the light), the
 * rest the shadowed one; a shallow furrow is one crack-tone line. Plate: its tone, the lit edge after a furrow on its
 * left or a crack above, the shade before a furrow on its right or a crack below, streaks inside.
 */
function shade(g, p, o) {
  // no lone pixels: one whose four neighbours all share another class joins them
  for (let pass = 0; pass < 2; pass++) for (let y = 0; y < M; y++) for (let x = 0; x < M; x++) {
    const c = at(g, x, y), n = [at(g, x - 1, y), at(g, x + 1, y), at(g, x, y - 1), at(g, x, y + 1)];
    if (n.every(q => q.cls === n[0].cls && q.cls !== c.cls)) Object.assign(c, {cls: n[0].cls, deep: n[0].deep, id: n[0].id});
  }
  const deep = o.deep ? o.deep(p) : p.deep, wall = o.wall ? o.wall(p) : p.crack, crack = o.crack ? o.crack(p) : p.crack;
  const tones = o.tones ? o.tones(p) : [p.bark1, p.bark2, mix(p.bark1, p.bark3, 0.5)], out = [];
  for (let y = 0; y < M; y++) for (let x = 0; x < M; x++) {
    const c = at(g, x, y), L = at(g, x - 1, y), R = at(g, x + 1, y), U = at(g, x, y - 1), D = at(g, x, y + 1);
    let col, h = 0, ao = 1, s = o.smooth;
    if (c.cls === FURROW) {
      const lit = R.cls !== FURROW;
      if (!c.deep) { col = wall; h = -0.4 * o.depth; ao = 0.9; }
      else { col = lit ? wall : deep; h = lit ? -0.6 * o.depth : -o.depth; ao = lit ? 0.85 : 0.7; }
      s -= 12;
    } else if (c.cls === CRACK) {
      col = crack; h = -0.4 * o.depth; ao = 0.88; s -= 8;
    } else {
      col = tones[Math.floor(tones.length * rnd(c.id, 1, o.seed))];
      // upright streaks: runs of 3-5 px in one column, only away from the plate's edges
      const run = 3 + Math.floor(3 * rnd(x, 61, o.seed)), ph = Math.floor(run * rnd(x, 62, o.seed));
      if (o.streak && c.e >= 2 && rnd(x * 64 + Math.floor((y + ph) / run), 63, o.seed) < o.streak) col = mix(col, o.streakTo(p), 0.5);
      if (L.cls === FURROW || U.cls === CRACK) { col = mix(col, p.barkLight, o.light); h = 0.15 * o.depth; }
      else if (R.cls === FURROW || D.cls === CRACK) { col = mix(col, p.bark3, 0.55); h = -0.2 * o.depth; ao = 0.95; }
    }
    out.push({col, h, ao, s});
  }
  return out;
}

/** The three maps from per-pixel {col, h, ao, s}: colour, _s (smoothness, F0 10, porosity 45), _n (DirectX normal, AO, height 255). */
function maps(px) {
  const col = Buffer.alloc(M * M * 4), spec = Buffer.alloc(M * M * 4), nrm = Buffer.alloc(M * M * 4), mm = 1000 / M;
  const H = (x, y) => px[wrap(y) * M + wrap(x)].h;
  for (let y = 0; y < M; y++) for (let x = 0; x < M; x++) {
    const i = y * M + x, o = i * 4, q = px[i];
    q.col.forEach((v, k) => { col[o + k] = clamp(Math.round(v), 0, 255); }); col[o + 3] = 255;
    spec[o] = clamp(Math.round(q.s), 0, 255); spec[o + 1] = 10; spec[o + 2] = 45; spec[o + 3] = 255;
    const hx = (H(x + 1, y) - H(x - 1, y)) / (2 * mm), hy = (H(x, y + 1) - H(x, y - 1)) / (2 * mm), l = Math.hypot(hx, hy, 1);
    nrm[o] = Math.round((-hx / l * 0.5 + 0.5) * 255); nrm[o + 1] = Math.round((-hy / l * 0.5 + 0.5) * 255);
    nrm[o + 2] = clamp(Math.round(255 * q.ao), 0, 255); nrm[o + 3] = 255;
  }
  return [png(col, M, M), png(spec, M, M), png(nrm, M, M)];
}

/** A bark from its options: layout, shade, then the species' own touches (o.post) on the shaded pixels. */
const draw = o => p => { const g = layout(o), px = shade(g, p, o); return maps(o.post ? o.post(px, g, p, o) : px); };

/** White oak: light ash-grey, shallow furrows, ridges broken into short scaly blocks. */
const whiteOak = draw({seed: 6100, K: 6, A: 1.2, knot: 8, wF: 2, gaps: [1, 2], cracks: [1, 3], full: 0.7, slant: 0.25,
  depth: 4, light: 0.55, smooth: 34, deep: p => mix(p.crack, p.deep, 0.4), wall: p => mix(p.crack, p.bark3, 0.45),
  crack: p => mix(p.crack, p.bark3, 0.3), streak: 0.25, streakTo: p => p.bark3});

/** White pine: dark grey-brown broad ridges (about 25 cm), deep furrows with rusty walls, long blocky plates. */
const whitePine = draw({seed: 6200, K: 4, A: 1.5, knot: 8, wF: 3, gaps: [0, 1], cracks: [1, 2], full: 0.8, slant: 0.3,
  depth: 7, light: 0.45, smooth: 30, wall: p => mix(p.crack, p.rust, 0.5), streak: 0.3, streakTo: p => p.bark3});

/** Northern red oak: dark ridges with a light flat stripe 2 px wide down the middle ("ski tracks"), broken now and then. */
const redOak = draw({seed: 6300, K: 5, A: 0.8, knot: 8, wF: 2, gaps: [0, 1], cracks: [0, 1], full: 0.4, slant: 0.2,
  depth: 5, light: 0.25, smooth: 26, tones: p => [p.bark2, p.bark3, mix(p.bark2, p.bark3, 0.5)],
  post: (px, g, p, o) => {
    g.forEach((c, i) => {
      if (c.cls !== PLATE || c.e < 2 || Math.abs(c.u - 0.5) > 0.22) return;
      const y = Math.floor(i / M), L = 16, ph = Math.floor(L * rnd(c.ridge, 12, o.seed)), gap = rnd(c.ridge, 13, o.seed) < 0.4 ? 0 : 3;
      if (gap && wrap(y + ph) % L >= L - gap) return;
      Object.assign(px[i], {col: mix(p.barkLight, p.bark1, 0.2), s: 60, h: 0.3 * o.depth});
    });
    return px;
  }});

/** Shagbark hickory: long narrow strips (about 14 cm wide, one end per metre), dark 2 px gaps, each strip's upper end curling out (lit rows under a dark end). */
const hickory = draw({seed: 6400, K: 7, A: 0.6, knot: 16, wF: 2, gaps: [0, 1], cracks: [1, 1], full: 1, slant: 0,
  depth: 6, light: 0.45, smooth: 32, crack: p => mix(p.deep, p.crack, 0.4),
  post: (px, g, p, o) => {
    for (let y = 0; y < M; y++) for (let x = 0; x < M; x++) {
      const c = at(g, x, y), i = y * M + x;
      if (c.cls !== PLATE) continue;
      if (at(g, x, y - 1).cls === CRACK) Object.assign(px[i], {col: p.barkLight, h: 0.5 * o.depth});
      else if (at(g, x, y - 2).cls === CRACK) Object.assign(px[i], {col: mix(p.barkLight, p.bark1, 0.5), h: 0.3 * o.depth});
    }
    return px;
  }});

/** Red maple: smooth light grey, thin shallow upright fissures of varied length (no cross cracks), faint light streaks. */
const redMaple = draw({seed: 6500, K: 6, A: 1, knot: 8, wF: 1, shallow: 0, gaps: [1, 2], cracks: [0, 0], full: 0, slant: 0,
  depth: 2.5, light: 0.35, smooth: 40, deep: p => p.crack, wall: p => p.crack, crack: p => mix(p.crack, p.bark3, 0.5),
  tones: p => [p.bark1, p.bark2], streak: 0.2, streakTo: p => p.barkLight});

/** Eastern hemlock: cinnamon-brown broad scaly ridges, deep furrows with purplish walls. */
const hemlock = draw({seed: 6600, K: 4, A: 1.5, knot: 8, wF: 3, gaps: [0, 1], cracks: [2, 3], full: 0.6, slant: 0.3,
  depth: 7, light: 0.4, smooth: 28, wall: p => mix(p.crack, p.rust, 0.65), streak: 0.3, streakTo: p => p.bark3});

const BARKS = {white_oak: whiteOak, white_pine: whitePine, red_oak: redOak, shagbark_hickory: hickory, red_maple: redMaple, eastern_hemlock: hemlock};

/** A species' bark: [colour, _s, _n] PNG buffers, M x M. */
export const barkMaps = (id, p) => BARKS[id](p);
