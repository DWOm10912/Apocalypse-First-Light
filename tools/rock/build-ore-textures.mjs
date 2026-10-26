// U3b batch 1 (docs/worldgen/underground_u3_rocks_ores_design.md 6, 14): the ore rock sprites, textures/block/ore/<name>.png
// with LabPBR _n / _s, 256 texels a block like the natural rocks. No CC0 photo of these ores exists as a tiling texture
// (Poly Haven and ambientCG searched 2026-10-24), so each is its host rock's CC0 photo sprite (the boulder sprites the rock
// blocks wear, for their grain) with the ore's minerals generated on it after real ore: colour, lustre, habit and how they
// sit in the rock. Every field wraps at 256 texels, so the sprites tile.
//   coal_seam                 bright (vitrain) / dull (durain) / charcoal (fusain) bands, face cleat; lean: shale partings
//   banded_iron_formation     red jasper, grey chert and steel-grey hematite bands, gently folded; rich: more hematite
//   quartz_vein_<gold|copper|lead>  milky quartz, fractures stained with limonite; gold: rusty boxwork and (rich) a few
//                             visible gold flecks; copper: brassy chalcopyrite blebs and green malachite; lead: galena cubes
//   lead_zinc_dolomite        the dolomite with open-space fillings: white dolomite spar rims, resinous brown sphalerite,
//                             lead-grey galena cubes
//   copper_breccia            angular clasts of several rocks in a dark matrix with chalcopyrite and green-blue chrysocolla
// Each in lean and rich grade (block state grade). The LabPBR maps: _n DirectX from the height field, B ambient occlusion,
// A 255 (Rock PBR R1: no parallax on the rock face mesh's 1/8 m pieces, docs/worldgen/rock_pbr_v1.md 3); _s R smoothness, G F0 (reflectance x 255: quartz 11, sphalerite 43, hematite 70, chalcopyrite 107, galena 110;
// gold flecks 231 = gold), B 48 (the rocks' porosity), A 255.
// Rock PBR R1 (rock_pbr_v1.md 6): the grains a lamp catches. Besides each ore's own masses, disseminated grains a texel each
// in the rock between them (pyrite in coal and in the quartz, chalcopyrite, galena, sphalerite, magnetite: DISSEM), the
// hematite bands' specular flakes, and every metallic grain (F0 40 and up) a crystal face: its normal leaned at random per
// 2 x 2 texels (FACET), so the grains flash one by one, not as one sheen. The height field's relief is made as strong as
// the natural rocks' (rock-pbr.mjs relief, 90% of the texels at most 22 degrees) with its crevice AO, before the facets.
//   node tools/rock/build-ore-textures.mjs            -> writes the sprites and the comparison sheet
//   node tools/rock/build-ore-textures.mjs --check    -> verifies the sprites are up to date
// The sheet (design/world/ore_u3b/ore_u3b_candidates.png, needs sharp from the wiki's node_modules) is what the user looks
// at before the sprites go into the game.
import fs from 'node:fs';
import path from 'node:path';
import {createRequire} from 'node:module';
import {fileURLToPath} from 'node:url';
import {readPng, png} from '../cube-slab-mesh-lib.mjs';
import {relief} from './rock-pbr.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const TEX = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight/textures/block');
const OUT = path.join(TEX, 'ore');
const SHEET = path.join(ROOT, 'design/world/ore_u3b/ore_u3b_candidates.png');
const RES = 256, TAU = Math.PI * 2;
const check = process.argv.includes('--check');

// ---------------------------------------------------------------- fields that wrap at RES

function hash(i, j, s) {
  let h = (Math.imul(i, 374761393) + Math.imul(j, 668265263) + Math.imul(s, 1442695041)) | 0;
  h = Math.imul(h ^ (h >>> 13), 1274126177);
  h ^= h >>> 16;
  return (h >>> 0) / 4294967296;
}
const wrap = (v, n) => ((v % n) + n) % n;
const sm3 = t => t * t * (3 - 2 * t);
const clamp = (v, a = 0, b = 1) => Math.max(a, Math.min(b, v));
const mix = (a, b, t) => a.map((v, k) => v + (b[k] - v) * t);
const sstep = (a, b, v) => sm3(clamp((v - a) / (b - a)));

/** Periodic value noise, cells of c texels (c divides RES), 0..1. */
function pn(x, y, c, s) {
  const n = RES / c, xi = Math.floor(x / c), yi = Math.floor(y / c), tx = sm3(x / c - xi), ty = sm3(y / c - yi);
  const h = (i, j) => hash(wrap(i, n), wrap(j, n), s);
  const a = h(xi, yi) + (h(xi + 1, yi) - h(xi, yi)) * tx, b = h(xi, yi + 1) + (h(xi + 1, yi + 1) - h(xi, yi + 1)) * tx;
  return a + (b - a) * ty;
}
/** Fractal sum from cells of c texels down to 4, 0..1. */
function fbm(x, y, c, s) {
  let v = 0, a = 1, t = 0;
  for (let k = 0; c >= 4; k++, c /= 2, a *= 0.5) { v += a * pn(x, y, c, s + 17 * k); t += a; }
  return v / t;
}
/** Periodic 1D value noise over RES, cells of c, 0..1. */
function pn1(t, c, s) {
  const n = RES / c, i = Math.floor(t / c), f = sm3(t / c - i);
  const a = hash(wrap(i, n), 7, s), b = hash(wrap(i + 1, n), 7, s);
  return a + (b - a) * f;
}
/** Periodic Voronoi, n x n jittered points: nearest and second distance (texels), the nearest point's id and position. */
function vor(x, y, n, s) {
  const c = RES / n, xi = Math.floor(x / c), yi = Math.floor(y / c);
  let d1 = 1e9, d2 = 1e9, id = 0, fx = 0, fy = 0;
  for (let j = -2; j <= 2; j++) for (let i = -2; i <= 2; i++) {
    const cx = xi + i, cy = yi + j, wi = wrap(cx, n), wj = wrap(cy, n);
    const px = (cx + 0.1 + 0.8 * hash(wi, wj, s)) * c, py = (cy + 0.1 + 0.8 * hash(wi, wj, s + 1)) * c;
    const d = Math.hypot(px - x, py - y);
    if (d < d1) { d2 = d1; d1 = d; id = wi + wj * n; fx = px; fy = py; } else if (d < d2) d2 = d;
  }
  return {d1, d2, id, fx, fy};
}

// ---------------------------------------------------------------- host sprites (CC0 photo grain)

function host(name) {
  const im = readPng(fs.readFileSync(path.join(TEX, name + '.png')));
  if (im.w !== RES) throw new Error('host sprite size ' + name);
  const bpp = im.px.length / (im.w * im.h), col = new Float32Array(RES * RES * 3), L = new Float32Array(RES * RES);
  let mean = 0;
  for (let i = 0; i < RES * RES; i++) {
    for (let c = 0; c < 3; c++) col[i * 3 + c] = im.px[i * bpp + c] / 255;
    L[i] = 0.2126 * col[i * 3] + 0.7152 * col[i * 3 + 1] + 0.0722 * col[i * 3 + 2];
    mean += L[i] / (RES * RES);
  }
  // the photo's fine grain about its broad tone (a wrapping box blur of radius 6, twice each way)
  let low = L;
  for (let p = 0; p < 4; p++) {
    const dst = new Float32Array(L.length), hor = p % 2 === 0;
    for (let y = 0; y < RES; y++) for (let x = 0; x < RES; x++) {
      let s = 0;
      for (let d = -6; d <= 6; d++) s += low[hor ? y * RES + wrap(x + d, RES) : wrap(y + d, RES) * RES + x];
      dst[y * RES + x] = s / 13;
    }
    low = dst;
  }
  const at = (x, y) => wrap(Math.round(y), RES) * RES + wrap(Math.round(x), RES);
  return {
    color: (x, y) => { const i = at(x, y); return [col[i * 3], col[i * 3 + 1], col[i * 3 + 2]]; },
    grain: (x, y) => { const i = at(x, y); return (L[i] - low[i]) / mean; },
    tone: (x, y) => low[at(x, y)] / mean - 1,
  };
}
const SHALE = host('boulder/shale_fresh'), DOLOMITE = host('boulder/limestone'), MARBLE = host('boulder/limestone_fresh');
const CLASTS = [host('boulder/granite_fresh'), SHALE, host('boulder/sandstone_fresh'), DOLOMITE];

// ---------------------------------------------------------------- minerals: colour (sRGB 0..1), smoothness 0..1, F0 byte

const M = {
  // coal reflects about 4-8% (linear), sRGB 0.17-0.27 (user 2026-10-24 in game: the first lumps, 0.06-0.1, were black blots)
  vitrain: {c: [0.17, 0.17, 0.18], sm: 0.85, f0: 11},
  durain: {c: [0.23, 0.225, 0.22], sm: 0.32, f0: 10},
  fusain: {c: [0.26, 0.25, 0.24], sm: 0.12, f0: 10},
  jasper: {c: [0.5, 0.17, 0.13], sm: 0.35, f0: 12},
  chert: {c: [0.57, 0.55, 0.53], sm: 0.4, f0: 12},
  hematite: {c: [0.25, 0.235, 0.25], sm: 0.55, f0: 70},
  magnetite: {c: [0.12, 0.12, 0.13], sm: 0.45, f0: 50},
  quartz: {c: [0.83, 0.82, 0.79], sm: 0.38, f0: 11},
  glassy: {c: [0.6, 0.61, 0.62], sm: 0.6, f0: 11},
  limonite: {c: [0.6, 0.38, 0.17], sm: 0.12, f0: 10},
  boxwork: {c: [0.27, 0.16, 0.09], sm: 0.08, f0: 10},
  gold: {c: [0.93, 0.74, 0.27], sm: 0.9, f0: 231},
  chalcopyrite: {c: [0.64, 0.57, 0.29], sm: 0.68, f0: 107},
  malachite: {c: [0.2, 0.52, 0.34], sm: 0.2, f0: 14},
  chrysocolla: {c: [0.26, 0.5, 0.46], sm: 0.25, f0: 14},
  galena: {c: [0.56, 0.57, 0.59], sm: 0.85, f0: 110},
  sphalerite: {c: [0.4, 0.24, 0.1], sm: 0.6, f0: 43},
  spar: {c: [0.86, 0.84, 0.79], sm: 0.45, f0: 11},
  matrix: {c: [0.26, 0.24, 0.22], sm: 0.15, f0: 10},
};
/** A texel: colour, height 0..1, smoothness 0..1, F0 byte. */
const px = (m, h, c = m.c) => ({c, h, sm: m.sm, f0: m.f0});
const shade = (c, k) => c.map(v => clamp(v * k));

// ---------------------------------------------------------------- the ores

/** A crack network that meanders: where a warped fractal field crosses its middle (width w texels about), 0..1 strength. */
function cracks(x, y, c, s, w) {
  const wx = x + 10 * (pn(x, y, 32, s + 1) - 0.5), wy = y + 10 * (pn(x, y, 32, s + 2) - 0.5);
  const f = fbm(wx, wy, c, s), d = Math.abs(f - 0.5);
  return clamp(1 - d / (w * 0.012));
}
/** A patchy mask: a warped fractal field over a threshold, eased over soft (0..1). */
function patches(x, y, c, s, thr, soft = 0.03) {
  const wx = x + 8 * (pn(x, y, 16, s + 3) - 0.5), wy = y + 8 * (pn(x, y, 16, s + 4) - 0.5);
  return sstep(thr, thr + soft, fbm(wx, wy, c, s));
}
/** Grains on a jittered grid of cs texels: rounded-angular, three faces each its own light (0..1), or -1 outside. */
function crystals(x, y, cs, s, p, size) {
  const ci = Math.floor(x / cs), cj = Math.floor(y / cs), n = RES / cs;
  for (let j = -1; j <= 1; j++) for (let i = -1; i <= 1; i++) {
    const wi = wrap(ci + i, n), wj = wrap(cj + j, n);
    if (hash(wi, wj, s) > p) continue;
    const fx = (ci + i + 0.5) * cs + cs * 0.5 * (hash(wi, wj, s + 1) - 0.5), fy = (cj + j + 0.5) * cs + cs * 0.5 * (hash(wi, wj, s + 2) - 0.5);
    const r = size[0] + (size[1] - size[0]) * hash(wi, wj, s + 3), dx = x - fx, dy = y - fy;
    // an octagon-ish outline (between a square and a circle), a little turned
    const a = hash(wi, wj, s + 5) * Math.PI / 2, u = dx * Math.cos(a) + dy * Math.sin(a), v = -dx * Math.sin(a) + dy * Math.cos(a);
    if (Math.max(Math.abs(u), Math.abs(v)) * 0.75 + Math.hypot(u, v) * 0.35 < r) {
      const face = u + v < 0 ? 0 : u > v ? 1 : 2;
      return hash(wi * 3 + face, wj, s + 4);
    }
  }
  return -1;
}

/** Coal: bright and dull bands that differ mostly in lustre, blocky fracture, cleat with a rare calcite film; lean: shale partings. */
function coal(x, y, rich) {
  const yb = y + 2 * Math.sin(TAU * x / RES + 1.3) + 5 * (fbm(x, y, 64, 11) - 0.5);
  const band = 0.6 * pn1(yb, 8, 12) + 0.4 * pn1(yb, 4, 13);
  const parting = pn1(yb, 16, 14) * 0.7 + pn1(yb, 8, 15) * 0.3 > (rich ? 0.86 : 0.62);
  const g = SHALE.grain(x, y);
  // blocky fracture: each block of the cleat sits a little proud or sunk
  const b = vor(x + 3 * (pn(x, y, 16, 7) - 0.5), yb, 9, 8), step = 0.12 * (hash(b.id, 1, 9) - 0.5), edge = (b.d2 - b.d1) / 2;
  if (parting) return px(M.durain, 0.5 + 0.1 * g + step, shade(SHALE.color(x, y), 0.5 + 0.1 * pn(x, y, 32, 10)));
  const m = band > 0.58 ? M.vitrain : band < 0.24 ? M.fusain : M.durain;
  let c = shade(m.c, 1 + 0.35 * g + 0.15 * (pn1(yb, 2, 19) - 0.5)), h = (m === M.vitrain ? 0.56 : m === M.fusain ? 0.46 : 0.5) + 0.04 * g + step;
  if (edge < 0.6 && hash(b.id, 2, 4) < 0.7) {
    // the cleat: a dark hairline, now and then a faint calcite or rusty film on it
    const film = pn(x, y, 64, 6);
    c = film > 0.9 ? [0.48, 0.46, 0.43] : film < 0.06 ? [0.4, 0.27, 0.14] : shade(c, 0.55);
    return {c, h: h - 0.1, sm: 0.2, f0: 10};
  }
  // bright coal's glassy fracture faces catch the light: some blocks lighter (the lustre in the colour for unshaded play)
  if (m === M.vitrain && hash(b.id, 3, 4) < 0.3) c = shade(c, 1.5 + 0.3 * hash(b.id, 5, 4));
  if (m === M.vitrain && pn(x, y, 8, 5) > 0.86) c = mix(c, [0.24, 0.21, 0.3], 0.5);   // a faint blue-violet tarnish
  return px(m, h, c);
}

/** Banded iron: maroon jasper, blue-grey hematite and grey chert bands that pinch and swell, finely laminated, crossed by a rare quartz veinlet. */
function bif(x, y, rich) {
  const yb = y + 4 * Math.sin(TAU * x / RES + 0.4) + 7 * (fbm(x, y, 128, 21) - 0.5);
  const w = yb + 9 * (pn(x, yb, 64, 22) - 0.5) + 5 * (pn1(yb, 16, 23) - 0.5);
  const N = 30, u = w * N / RES, k = wrap(Math.floor(u), N), f = u - Math.floor(u);
  const r = hash(k, 3, 24), hem = rich ? 0.46 : 0.2, jas = rich ? 0.34 : 0.5;
  const pick = r < hem ? M.hematite : r < hem + jas ? M.jasper : M.chert;
  const g = SHALE.grain(x, y), tone = pn(x, y, 32, 27), lam = pn1(w, 2, 25);
  let c;
  if (pick === M.jasper) c = mix([0.38, 0.14, 0.15], [0.53, 0.2, 0.17], tone);
  else if (pick === M.chert) c = mix([0.5, 0.46, 0.46], [0.56, 0.4, 0.38], 0.6 * tone);
  else c = mix([0.29, 0.3, 0.34], [0.42, 0.42, 0.46], tone);
  // fine laminae: a grey hair-line in the red, a red one in the grey
  if (lam > 0.8) c = pick === M.jasper ? mix(c, [0.33, 0.32, 0.35], 0.6) : mix(c, [0.45, 0.17, 0.15], 0.45);
  c = shade(c, 1 + 0.14 * (lam - 0.5) + 0.22 * g);
  const edge = Math.min(f, 1 - f) * RES / N;
  if (edge < 0.6) c = shade(c, 0.85);
  const sm = pick === M.hematite ? 0.6 : pick === M.chert ? 0.42 : 0.32;
  return {c, h: (pick === M.hematite ? 0.56 : pick === M.chert ? 0.53 : 0.5) + 0.06 * g, sm, f0: pick === M.hematite ? 70 : 12};
}

/** Quartz vein: sugary milky quartz with dark wall-rock ribbons, a few rusty fractures; the metal by district. */
function quartzVein(metal) {
  return (x, y, rich) => {
    const g = MARBLE.grain(x, y), clear = fbm(x, y, 64, 31), sugar = pn(x, y, 2, 30) * 0.6 + pn(x, y, 4, 29) * 0.4;
    let c = shade(mix(M.quartz.c, M.glassy.c, sstep(0.5, 0.72, clear) * 0.75), 0.94 + 0.12 * sugar + 0.08 * g);
    let h = 0.55 + 0.03 * g + 0.03 * sugar, sm = clear > 0.6 ? M.glassy.sm : M.quartz.sm, f0 = 11;
    // ribbons: slivers of the dark wall rock along the vein, wavy and broken
    const yb = y + 6 * (fbm(x, y, 64, 32) - 0.5) + 3 * Math.sin(TAU * 2 * x / RES + 0.7);
    const rb = pn1(yb, 16, 33), ribT = 0.8 + 0.2 * pn(x, yb, 32, 39), rib = rb > ribT;
    const nearRib = rb > 0.7;
    const crack = cracks(x, y, 128, 34, 1.1), near = cracks(x, y, 128, 34, 7);
    const stain = (metal === 'gold' ? (rich ? 0.75 : 0.5) : 0.35) * near * patches(x, y, 32, 35, 0.4, 0.25);
    c = mix(c, mix(M.limonite.c, [0.5, 0.3, 0.14], pn(x, y, 8, 36)), clamp(stain));
    if (stain > 0.35) sm = M.limonite.sm;
    if (crack > 0.5) return {c: mix(c, [0.32, 0.25, 0.18], 0.75), h: 0.38, sm: 0.1, f0: 10};
    if (rib) {
      // a dark ribbon, sprinkled with grey sulphide (pyrite, arsenopyrite)
      if (pn(x, y, 2, 37) > 0.8) return {c: [0.5, 0.49, 0.45], h: 0.5, sm: 0.7, f0: 90};
      const core = sstep(ribT, ribT + 0.06, rb);
      return px(M.matrix, 0.48, mix(c, shade([0.36, 0.38, 0.36], 0.85 + 0.35 * pn(x, y, 4, 38) + 0.2 * g), 0.45 + 0.55 * core));
    }
    if (metal === 'gold') {
      // visible gold only in rich ore: small clusters of flecks along the ribbons
      if (rich && nearRib && patches(x, y, 16, 40, 0.62, 0.02) > 0.5 && pn(x, y, 2, 41) > 0.55) return px(M.gold, 0.6, shade(M.gold.c, 0.85 + 0.25 * pn(x, y, 2, 42)));
    } else if (metal === 'copper') {
      // olive-brass chalcopyrite in masses along the ribbons and fractures, a little tarnish and a thin green film on the cracks
      const mass = patches(x, y, 32, 43, rich ? 0.6 : 0.69, 0.02) * (0.3 + 0.6 * (nearRib ? 1 : 0) + 0.5 * near);
      if (mass > 0.5) {
        let cc = shade(mix(M.chalcopyrite.c, [0.52, 0.48, 0.24], pn(x, y, 4, 44)), 0.9 + 0.2 * sugar);
        if (pn(x, y, 8, 45) > 0.82) cc = mix(cc, [0.35, 0.3, 0.45], 0.45);
        return px(M.chalcopyrite, 0.6, cc);
      }
      if (near > 0.5 && pn(x, y, 16, 46) > (rich ? 0.6 : 0.75)) { c = mix(c, M.malachite.c, 0.55); sm = M.malachite.sm; }
    } else {
      // galena masses: silver lead-grey, its cleavage faces each catching the light differently
      const mass = patches(x, y, 32, 47, rich ? 0.6 : 0.7, 0.01) * (0.4 + 0.7 * (nearRib ? 1 : 0) + 0.4 * near);
      if (mass > 0.5) {
        const facet = hash(vor(x, y, 36, 48).id, 1, 49);
        return px(M.galena, 0.6, shade(M.galena.c, 0.62 + 0.62 * facet));
      }
    }
    return {c, h, sm, f0};
  };
}

/** Lead-zinc dolomite: galena and sphalerite grains strung along the bedding in the dolomite, a little white spar in vugs. */
function leadZinc(x, y, rich) {
  const g = DOLOMITE.grain(x, y);
  const yb = y + 4 * (fbm(x, y, 64, 60) - 0.5) + 2 * Math.sin(TAU * x / RES + 2);
  const bed = pn1(yb, 16, 61) * 0.7 + pn1(yb, 8, 62) * 0.3, thr = rich ? 0.55 : 0.72, inBed = bed > thr;
  const dens = inBed ? (rich ? 0.75 : 0.5) : (rich ? 0.1 : 0.04);
  // a vug with white dolomite spar
  if (inBed && patches(x, y, 16, 63, 0.72, 0.02) > 0.5) return px(M.spar, 0.53, shade(M.spar.c, 0.95 + 0.1 * pn(x, y, 2, 64)));
  const gal = crystals(x, y, 12, 65, dens * 0.55, [2, 4.5]);
  if (gal >= 0) return px(M.galena, 0.58, shade(M.galena.c, 0.6 + 0.65 * gal));
  const sph = crystals(x + 5, y + 5, 10, 70, dens * 0.5, [1.8, 3.6]);
  if (sph >= 0) return px(M.sphalerite, 0.55, shade(mix([0.26, 0.15, 0.08], [0.45, 0.28, 0.12], sph * sph), 1));
  return {c: shade(DOLOMITE.color(x, y), inBed ? 0.9 : 1), h: 0.5 + 0.06 * g, sm: 0.3, f0: 10};
}

/** Copper breccia: grey angular clasts of the country rock cemented by brassy sulphide (chalcopyrite, pyrite) and dark gangue. */
function breccia(x, y, rich) {
  const jx = x + 2 * (pn(x, y, 8, 80) - 0.5), jy = y + 2 * (pn(x, y, 8, 79) - 0.5);
  const big = vor(jx, jy, 5, 81), small = vor(jx, jy, 13, 91);
  const wm = (rich ? 6.5 : 4) * (0.6 + 0.8 * fbm(x, y, 32, 82));
  const eb = (big.d2 - big.d1) / 2, es = (small.d2 - small.d1) / 2;
  let v = null, e = 0;
  if (eb > wm) { v = big; e = eb - wm; } else if (es > wm * 0.55 && hash(small.id, 5, 92) < 0.7) { v = small; e = es - wm * 0.55; }
  if (!v) {
    const sul = pn(x, y, 8, 84) * 0.4 + pn(x, y, 16, 85) * 0.6;
    if (sul > (rich ? 0.45 : 0.68)) {
      const pyrite = pn(x, y, 16, 93) > 0.62;
      return px(M.chalcopyrite, 0.47, shade(pyrite ? [0.62, 0.57, 0.37] : [0.55, 0.49, 0.26], 0.85 + 0.25 * pn(x, y, 4, 94)));
    }
    let c = shade(M.matrix.c, 0.8 + 0.35 * pn(x, y, 4, 86));
    if (rich && pn(x, y, 16, 87) > 0.85) c = mix(c, M.chrysocolla.c, 0.35);
    return px(M.matrix, 0.4, c);
  }
  const grey = hash(v.id, 1, 95) < 0.7, rock = grey ? SHALE : CLASTS[0];
  const ox = hash(v.id, 2, 88) * RES, oy = hash(v.id, 3, 89) * RES;
  let c = rock.color(x + ox, y + oy);
  const l = (c[0] + c[1] + c[2]) / 3;
  c = shade(mix([l, l, l], c, 0.35), (grey ? 1.15 : 0.95) * (0.85 + 0.25 * hash(v.id, 4, 90)));
  // the clasts' rims bleached by the fluids
  if (e < 1.5) c = mix(c, [0.6, 0.58, 0.54], 0.35);
  return {c, h: 0.55 + 0.06 * rock.grain(x + ox, y + oy) - 0.05 * clamp(1 - e / 2), sm: 0.3, f0: 10};
}

/** The sprites: name, generator; each in both grades. */
const ORES = [
  ['coal_seam', coal], ['banded_iron_formation', bif],
  ['quartz_vein_gold', quartzVein('gold')], ['quartz_vein_copper', quartzVein('copper')], ['quartz_vein_lead', quartzVein('lead')],
  ['lead_zinc_dolomite', leadZinc], ['copper_breccia', breccia],
];
const BUMP = 6;

/**
 * Disseminated grains (rock_pbr_v1.md 6): a mineral, its share of the texels in the lean and the rich grade, on the texels
 * of rock or gangue (F0 under 20) of the sprite; or a sheen: on the texels of F0 `on`, a share of them as smooth as `sm`.
 */
const PYRITE = {c: [0.66, 0.6, 0.38], sm: 0.85, f0: 138};
const DISSEM = {
  coal_seam: [{m: PYRITE, lean: 0.006, rich: 0.01}],
  banded_iron_formation: [{on: 70, sm: 0.88, lean: 0.25, rich: 0.3}, {m: M.magnetite, lean: 0.02, rich: 0.03}],
  quartz_vein_gold: [{m: PYRITE, lean: 0.015, rich: 0.025}],
  quartz_vein_copper: [{m: PYRITE, lean: 0.01, rich: 0.015}, {m: M.chalcopyrite, lean: 0.015, rich: 0.025}],
  quartz_vein_lead: [{m: PYRITE, lean: 0.01, rich: 0.015}, {m: M.galena, lean: 0.015, rich: 0.025}],
  lead_zinc_dolomite: [{m: M.galena, lean: 0.015, rich: 0.025}, {m: M.sphalerite, lean: 0.015, rich: 0.02}],
  copper_breccia: [{m: M.chalcopyrite, lean: 0.02, rich: 0.03}, {m: PYRITE, lean: 0.01, rich: 0.015}],
};
/** The largest lean of a metallic grain's face (degrees). */
const FACET = 25;

function build(fn, rich, dissem = []) {
  const S = 2, n = RES * RES, col = new Float32Array(n * 3), H = new Float32Array(n), SM = new Float32Array(n), F0 = new Float32Array(n);
  for (let y = 0; y < RES; y++) for (let x = 0; x < RES; x++) {
    // 2 x 2 supersampled (the small cubes and flecks keep clean edges)
    const i = y * RES + x;
    let f = 0, preset = 0;
    for (let sy = 0; sy < S; sy++) for (let sx = 0; sx < S; sx++) {
      const t = fn(x + (sx + 0.5) / S, y + (sy + 0.5) / S, rich);
      for (let c = 0; c < 3; c++) col[i * 3 + c] += t.c[c] / (S * S);
      H[i] += t.h / (S * S); SM[i] += t.sm / (S * S);
      if (t.f0 >= 230) preset = t.f0; else f += t.f0 / (S * S);
    }
    // F0: a metal preset wins the texel; otherwise the mean reflectance
    F0[i] = preset || f;
  }
  dissem.forEach((d, di) => {
    const share = rich ? d.rich : d.lean;
    for (let i = 0; i < n; i++) {
      const x = i % RES, y = (i - x) / RES;
      if (hash(x, y, 500 + di) >= share) continue;
      if (d.on != null) { if (Math.round(F0[i]) === d.on) SM[i] = Math.max(SM[i], d.sm); continue; }
      if (F0[i] >= 20) continue;
      const k = 0.85 + 0.3 * hash(x, y, 520 + di);
      for (let c = 0; c < 3; c++) col[i * 3 + c] = clamp(d.m.c[c] * k);
      SM[i] = d.m.sm; F0[i] = d.m.f0; H[i] += 0.02;
    }
  });
  const albedo = Buffer.alloc(n * 4), sB = Buffer.alloc(n * 4), nB = Buffer.alloc(n * 4), nrm = new Float32Array(n * 3);
  // the height field's normals (OpenGL: y up the image), then their relief as strong as the rocks'
  const base = new Float32Array(n * 3);
  for (let y = 0; y < RES; y++) for (let x = 0; x < RES; x++) {
    const i = y * RES + x;
    const hx = (H[y * RES + wrap(x + 1, RES)] - H[y * RES + wrap(x - 1, RES)]) / 2, hy = (H[wrap(y + 1, RES) * RES + x] - H[wrap(y - 1, RES) * RES + x]) / 2;
    const l = Math.hypot(hx * BUMP, hy * BUMP, 1);
    base[i * 3] = -hx * BUMP / l; base[i * 3 + 1] = hy * BUMP / l; base[i * 3 + 2] = 1 / l;
  }
  const rel = relief(base, RES, 22);
  for (let y = 0; y < RES; y++) for (let x = 0; x < RES; x++) {
    const i = y * RES + x, rz = rel.nrm[i * 3 + 2];
    let nx = rel.nrm[i * 3] / rz, ny = -rel.nrm[i * 3 + 1] / rz, nz = 1;
    if (F0[i] >= 40) {
      // a crystal face: leaned at random, one lean per 2 x 2 texels
      const gx = x >> 1, gy = y >> 1, lean = Math.tan(FACET * Math.PI / 180 * Math.sqrt(hash(gx, gy, 540))), az = hash(gx, gy, 541) * TAU;
      nx += lean * Math.cos(az); ny += lean * Math.sin(az);
    }
    const l = Math.hypot(nx, ny, nz);
    nx /= l; ny /= l; nz /= l;
    nrm[i * 3] = nx; nrm[i * 3 + 1] = ny; nrm[i * 3 + 2] = nz;
    // ambient occlusion: lower than the neighbourhood is darker
    let around = 0;
    for (const [dx, dy] of [[-2, 0], [2, 0], [0, -2], [0, 2]]) around += H[wrap(y + dy, RES) * RES + wrap(x + dx, RES)] / 4;
    const ao = Math.min(rel.ao[i], clamp(1 - Math.max(0, around - H[i]) * 3, 0.6, 1));
    for (let c = 0; c < 3; c++) albedo[i * 4 + c] = Math.round(clamp(col[i * 3 + c]) * 255);
    albedo[i * 4 + 3] = 255;
    nB[i * 4] = Math.round((nx * 0.5 + 0.5) * 255); nB[i * 4 + 1] = Math.round((ny * 0.5 + 0.5) * 255);
    nB[i * 4 + 2] = Math.round(ao * 255); nB[i * 4 + 3] = 255;
    sB[i * 4] = Math.round(clamp(SM[i]) * 255); sB[i * 4 + 1] = Math.round(F0[i]); sB[i * 4 + 2] = 48; sB[i * 4 + 3] = 255;
  }
  return {albedo, s: sB, n: nB, col, nrm, sm: SM, f0: F0};
}

// ---------------------------------------------------------------- write / check

const sprites = [];
for (const [name, fn] of ORES) for (const grade of ['lean', 'rich']) sprites.push({name: name + '_' + grade, ore: name, grade, ...build(fn, grade === 'rich', DISSEM[name])});
let bad = 0, n = 0;
if (!check) fs.mkdirSync(OUT, {recursive: true});
for (const sp of sprites) for (const [sfx, buf] of [['', sp.albedo], ['_s', sp.s], ['_n', sp.n]]) {
  const file = path.join(OUT, sp.name + sfx + '.png'), data = png(buf, RES, RES);
  if (check) { if (!fs.existsSync(file) || !fs.readFileSync(file).equals(data)) { console.error('stale ' + path.relative(ROOT, file)); bad++; } }
  else fs.writeFileSync(file, data);
  n++;
}
console.log((check ? 'checked ' : 'wrote ') + n + ' files' + (bad ? ', ' + bad + ' stale' : ''));
if (check) process.exit(bad ? 1 : 0);

// ---------------------------------------------------------------- the comparison sheet

function loadSharp() {
  for (const p of [path.join(ROOT, 'wiki/package.json'), path.resolve(ROOT, '../Apocalypse First Light/wiki/package.json')]) {
    try { return createRequire(p)('sharp'); } catch { /* next */ }
  }
  return null;
}
const sharp = loadSharp();
if (!sharp) { console.log('sharp not found: no sheet'); process.exit(0); }

/** The sprite lit from the upper left (its normal and gloss: what the LabPBR maps do), linear-ish, sRGB out. */
function lit(sp, x, y) {
  const i = y * RES + x, nx = sp.nrm[i * 3], ny = -sp.nrm[i * 3 + 1], nz = sp.nrm[i * 3 + 2];
  const L = [-0.5, 0.55, 0.67], Hh = [L[0], L[1], L[2] + 1], hl = Math.hypot(...Hh);
  const ndl = Math.max(0, nx * L[0] + ny * L[1] + nz * L[2]), ndh = Math.max(0, (nx * Hh[0] + ny * Hh[1] + nz * Hh[2]) / hl);
  const sm = sp.sm[i], f0 = sp.f0[i], metal = f0 >= 230, refl = metal ? 0.9 : f0 / 255;
  const spec = refl * Math.pow(ndh, 4 + sm * sm * 160) * sm * 2.2;
  return [0, 1, 2].map(c => {
    const a = sp.col[i * 3 + c];
    return clamp(a * (0.32 + 0.85 * ndl) + spec * (metal ? a : 1));
  });
}

const LABEL = 170, TILE = 300, GAP = 12, ROWH = TILE + 2 * GAP, W = LABEL + 3 * (TILE + GAP) + GAP, HEAD = 56;
const NAMES = {
  coal_seam: ['煤层', 'Coal Seam'], banded_iron_formation: ['条带状铁矿', 'Banded Iron Formation'],
  quartz_vein_gold: ['含矿石英脉 · 金', 'Quartz Vein · gold'], quartz_vein_copper: ['含矿石英脉 · 铜', 'Quartz Vein · copper'],
  quartz_vein_lead: ['含矿石英脉 · 铅', 'Quartz Vein · lead'], lead_zinc_dolomite: ['含铅锌白云岩', 'Lead-Zinc Dolomite'],
  copper_breccia: ['含铜角砾岩', 'Copper Breccia'],
};
const Hh = HEAD + ORES.length * ROWH, img = Buffer.alloc(W * Hh * 3, 30);
const put = (ox, oy, w, h, f) => { for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) { const c = f(x, y), o = ((oy + y) * W + ox + x) * 3; for (let k = 0; k < 3; k++) img[o + k] = Math.round(c[k] * 255); } };
let svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${Hh}"><style>text{font-family:'Microsoft YaHei','Segoe UI',sans-serif;fill:#e6e6e6}</style>`;
const cols = ['贫矿 2 × 2 块', '富矿 2 × 2 块', '富矿 1 块，左上打光（法线 + 光泽）'];
cols.forEach((t, k) => { svg += `<text x="${LABEL + GAP + k * (TILE + GAP)}" y="36" font-size="18">${t}</text>`; });
ORES.forEach(([name], r) => {
  const oy = HEAD + r * ROWH + GAP, lean = sprites.find(s => s.name === name + '_lean'), rich = sprites.find(s => s.name === name + '_rich');
  // 2 x 2 blocks scaled to the tile (each block TILE / 2), then one block lit at 1:1 cropped to the tile
  const tiled = sp => (x, y) => { const sx = Math.floor(x * 2 * RES / TILE) % RES, sy = Math.floor(y * 2 * RES / TILE) % RES, i = sy * RES + sx; return [sp.col[i * 3], sp.col[i * 3 + 1], sp.col[i * 3 + 2]].map(v => clamp(v)); };
  put(LABEL + GAP, oy, TILE, TILE, tiled(lean));
  put(LABEL + 2 * GAP + TILE, oy, TILE, TILE, tiled(rich));
  put(LABEL + 3 * GAP + 2 * TILE, oy, TILE, TILE, (x, y) => lit(rich, Math.floor(x * RES / TILE), Math.floor(y * RES / TILE)));
  svg += `<text x="12" y="${oy + 30}" font-size="19">${NAMES[name][0]}</text><text x="12" y="${oy + 54}" font-size="13">${NAMES[name][1]}</text>`;
});
svg += '</svg>';
fs.mkdirSync(path.dirname(SHEET), {recursive: true});
await sharp(img, {raw: {width: W, height: Hh, channels: 3}}).composite([{input: Buffer.from(svg), top: 0, left: 0}]).png().toFile(SHEET);
console.log('sheet ' + path.relative(ROOT, SHEET));
