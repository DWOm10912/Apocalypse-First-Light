// Mesh Trees P3 (docs/rendering/mesh_trees_p3_species.md, C): the textures of the four new species, imported by
// build-tree-textures.mjs (which writes and --checks them); deterministic, fixed seeds. Species traits from USDA Silvics
// of North America and Virginia Tech dendrology:
//   northern red oak    dark grey ridged bark with long flat light stripes on the ridges ("ski tracks"); leaves with
//                       7-11 pointed, bristle-tipped lobes; light reddish-brown heartwood, oak rays
//   shagbark hickory    light grey bark coming off in long loose plates curling out at the ends; pinnately compound
//                       leaves of five leaflets (the terminal three larger); wide white sapwood, no rays
//   red maple           grey bark in long narrow scaly plates; small three-lobed toothed leaves on long red petioles,
//                       red twigs and buds; wide creamy sapwood, fine faint rings
//   eastern hemlock     cinnamon-brown bark, broad scaly ridges, deep purplish furrows; short flat needles in two
//                       ranks on flat lacy sprays, white bands beneath, tiny cones at the tips; pale wood, no resin
// Bark 16 x 16 RGB seamless both ways (vanilla density, like the P0 barks); leaf cards 32 x 32 grey values (the crown's
// tint colours them: the biome foliage for the broadleaves, hemlock its own deep green), with _mid / _far denser for
// LOD1 / LOD2 and a 16 x 16 _proxy cube face; item icons 16 x 16 coloured pixel art like felling-art's.
import {rngOf, canvas, put, getc, mul, mix, filled, outline, limb, seg} from './felling-art.mjs';

// ------------------------------------------------------------------------------------------------- palettes

export const RED_OAK = {
  barkLight: [0x86, 0x80, 0x7a], bark1: [0x6c, 0x66, 0x61], bark2: [0x62, 0x5c, 0x57], bark3: [0x58, 0x53, 0x4f],
  crack: [0x3e, 0x3a, 0x37], deep: [0x2f, 0x2c, 0x2a], rim: [0x4e, 0x48, 0x44],
  inner: [0x8e, 0x5a, 0x46], sap: [0xdc, 0xc4, 0xa0], sapLate: [0xcc, 0xb0, 0x88], heart: [0xbc, 0x8a, 0x66], late: [0x9c, 0x68, 0x4a],
  ray: [0xd2, 0xaa, 0x88], pith: [0x5e, 0x3c, 0x2a], check: [0x6a, 0x46, 0x32],
  cut: [0xd6, 0xb6, 0x92], cutGrain: [0xc0, 0x98, 0x74], cutLight: [0xe4, 0xc8, 0xa6], cutEdge: [0xae, 0x86, 0x62],
  leaf: [0x4c, 0x7e, 0x2e], leafLight: [0x6c, 0x9c, 0x40], leafHi: [0x88, 0xb4, 0x52], leafDark: [0x34, 0x5c, 0x22], vein: [0x96, 0xb4, 0x64],
  twig: [0x6e, 0x52, 0x46], twigLight: [0x8a, 0x6c, 0x5c], twigDark: [0x4e, 0x3a, 0x32], bud: [0x8a, 0x50, 0x34],
};
export const HICKORY = {
  barkLight: [0xa8, 0xa4, 0x9c], bark1: [0x8e, 0x8a, 0x83], bark2: [0x84, 0x80, 0x7a], bark3: [0x78, 0x74, 0x6e],
  crack: [0x4a, 0x46, 0x41], deep: [0x36, 0x33, 0x30], rim: [0x6a, 0x66, 0x60],
  inner: [0x8a, 0x76, 0x58], sap: [0xe6, 0xda, 0xc0], sapLate: [0xd6, 0xc8, 0xa8], heart: [0xb8, 0x92, 0x68], late: [0x9a, 0x76, 0x50],
  ray: [0xd8, 0xc6, 0xa4], pith: [0x6a, 0x52, 0x38], check: [0x7e, 0x64, 0x46],
  cut: [0xe4, 0xd6, 0xb8], cutGrain: [0xd0, 0xbe, 0x9a], cutLight: [0xf0, 0xe6, 0xcc], cutEdge: [0xbe, 0xaa, 0x86],
  leaf: [0x5e, 0x8c, 0x34], leafLight: [0x7e, 0xaa, 0x48], leafHi: [0x9c, 0xc2, 0x5c], leafDark: [0x40, 0x66, 0x24], vein: [0xaa, 0xc4, 0x70],
  twig: [0x7a, 0x68, 0x52], twigLight: [0x96, 0x84, 0x6a], twigDark: [0x56, 0x48, 0x38], bud: [0xa0, 0x84, 0x5c],
};
export const RED_MAPLE = {
  barkLight: [0x9a, 0x98, 0x94], bark1: [0x82, 0x80, 0x7c], bark2: [0x78, 0x76, 0x72], bark3: [0x6e, 0x6c, 0x69],
  crack: [0x4c, 0x4a, 0x48], deep: [0x3a, 0x39, 0x37], rim: [0x66, 0x63, 0x60],
  inner: [0x9a, 0x6e, 0x58], sap: [0xec, 0xe0, 0xc8], sapLate: [0xe0, 0xd2, 0xb6], heart: [0xc8, 0xae, 0x90], late: [0xb4, 0x98, 0x7a],
  ray: [0xe4, 0xd6, 0xbc], pith: [0x7a, 0x5c, 0x44], check: [0x8a, 0x6e, 0x54],
  cut: [0xee, 0xe2, 0xca], cutGrain: [0xdc, 0xcc, 0xb0], cutLight: [0xf6, 0xee, 0xda], cutEdge: [0xca, 0xb8, 0x98],
  leaf: [0x58, 0x8a, 0x36], leafLight: [0x76, 0xa6, 0x48], leafHi: [0x92, 0xbe, 0x5c], leafDark: [0x3c, 0x62, 0x26], vein: [0xa2, 0xbc, 0x6a],
  petiole: [0xb0, 0x34, 0x2c],
  twig: [0x8e, 0x3a, 0x30], twigLight: [0xb0, 0x52, 0x44], twigDark: [0x62, 0x28, 0x22], bud: [0xc4, 0x32, 0x28],
};
export const HEMLOCK = {
  barkLight: [0x8c, 0x6a, 0x58], bark1: [0x7a, 0x5a, 0x4a], bark2: [0x6e, 0x50, 0x42], bark3: [0x64, 0x48, 0x3c],
  crack: [0x42, 0x2e, 0x2a], deep: [0x30, 0x22, 0x20], rim: [0x5a, 0x40, 0x34], rust: [0x7c, 0x44, 0x40],
  inner: [0x7c, 0x44, 0x42], sap: [0xdc, 0xbe, 0x98], sapLate: [0xc8, 0xa4, 0x7c], heart: [0xd4, 0xb2, 0x8c], late: [0xb2, 0x88, 0x62],
  ray: [0xe0, 0xc6, 0xa2], pith: [0x7e, 0x58, 0x3e], check: [0x8a, 0x64, 0x48],
  cut: [0xe2, 0xc8, 0xa4], cutGrain: [0xcc, 0xae, 0x88], cutLight: [0xee, 0xd8, 0xb8], cutEdge: [0xba, 0x98, 0x72],
  needle: [0x3e, 0x66, 0x44], needleLight: [0x58, 0x84, 0x5a], needleHi: [0x9c, 0xbc, 0x9c], needleDark: [0x2a, 0x4a, 0x32],
  twig: [0x7a, 0x62, 0x4e], twigLight: [0x94, 0x7c, 0x66], twigDark: [0x56, 0x44, 0x36], cone: [0x8a, 0x6a, 0x48],
};

// wood profiles for felling-art's endGrain / logEnd / cutWood (log end R = LogShape.radius x 32)
export const RED_OAK_WOOD = {R: 7, rimR: 13.8, innerR: 13.1, sapR: 11.6, rimW: 1.3, innerW: 0.7, sapW: 2.4, spacing: 2.4, lateShare: 0.42, checks: 1, rays: true, resin: false};
export const HICKORY_WOOD = {R: 5, rimR: 14.2, innerR: 13.5, sapR: 10.2, rimW: 1.2, innerW: 0.6, sapW: 3.6, spacing: 2.3, lateShare: 0.38, checks: 2, rays: false, resin: false};
export const RED_MAPLE_WOOD = {R: 9, rimR: 14.3, innerR: 13.8, sapR: 7.5, rimW: 0.9, innerW: 0.5, sapW: 5.6, spacing: 1.9, lateShare: 0.25, checks: 1, rays: false, resin: false};
export const HEMLOCK_WOOD = {R: 12, rimR: 13.0, innerR: 12.2, sapR: 11.4, rimW: 2.0, innerW: 0.9, sapW: 0.8, spacing: 2.6, lateShare: 0.36, checks: 2, rays: false, resin: false};

const wrap = (v, n) => ((v % n) + n) % n;

/** Wandering column positions (one per row, wrapping), closed vertically: the last rows walk back to the first. */
function wander(r, start, H, W, every, step) {
  const row = [];
  let x = start;
  for (let y = 0; y < H; y++) {
    if (y > 0 && y % every === 0 && y < H - every) x += r() < 0.5 ? -step : step;
    row.push(wrap(x, W));
  }
  // walk back to the start over the last rows, one pixel a row at most
  for (let y = H - 1, k = 0; y > 0 && wrap(row[y] - row[0], W) !== 0 && k < 3; y--, k++) row[y] = row[0];
  return row;
}

// --------------------------------------------------------------------------------------------------- barks

/** Northern red oak: four shallow dark furrows, flat ridges between them with a light stripe along the top. */
export function redOakBark(p) {
  const W = 16, H = 16, r = rngOf(4401), img = canvas(W, H);
  const furrows = [0, 4, 8, 12].map(c => wander(r, c + (r() < 0.5 ? 0 : 1), H, W, 4, 1));
  // per ridge: its own run of tone segments (3-7 rows) and two short breaks, nothing aligned across ridges
  const tones = furrows.map(() => segments(r, H, 3, 7));
  const breaks = furrows.map(() => [Math.floor(r() * H), Math.floor(r() * H)]);
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
    let best = 99, k = 0;
    for (let i = 0; i < furrows.length; i++) { const d = wrap(x - furrows[i][y], W); if (d < best) { best = d; k = i; } }
    const next = furrows[(k + 1) % furrows.length][y], width = wrap(next - furrows[k][y], W) || W;
    let c;
    if (best === 0) c = (x * 5 + y * 3) % 7 === 0 ? p.deep : p.crack;
    else if (best === width - 1) c = p.bark3;                                   // the ridge's shaded side
    else if (best === 1 || (best === 2 && width > 4)) c = p.barkLight;          // the flat, light top ("ski track")
    else c = tones[k][y] ? p.bark1 : p.bark2;
    if (best > 0 && breaks[k].includes(y) && best < width - 1) c = best === 1 ? p.bark3 : p.crack;
    put(img, x, y, c);
  }
  return img;
}

/** Alternating 0/1 runs of random lengths [lo, hi] over n rows, cyclic (the last run meets the first). */
function segments(r, n, lo, hi) {
  const out = new Array(n).fill(0);
  let y = 0, v = r() < 0.5 ? 0 : 1;
  while (y < n) { const len = lo + Math.floor(r() * (hi - lo + 1)); for (let k = 0; k < len && y < n; k++) out[y++] = v; v ^= 1; }
  return out;
}

/**
 * Shagbark hickory: long loose plates in six columns of uneven width, each plate 7-11 rows, its ends staggered from the
 * neighbours'; a plate's top end curls out (light row), its shadow falls on the plate below, the gaps between columns
 * are dark but broken where two plates press together.
 */
export function hickoryBark(p) {
  const W = 16, H = 16, r = rngOf(4501), img = canvas(W, H);
  const widths = [3, 2, 3, 3, 2, 3];
  let x0 = 0;
  for (let c = 0; c < widths.length; c++) {
    const w = widths[c], x1 = x0 + w;
    // two plates over the 16 rows: a start row and the second start 7-9 rows later (cyclic)
    const s0 = Math.floor(r() * H), s1 = wrap(s0 + 7 + Math.floor(r() * 3), H);
    const gapFrom = Math.floor(r() * H), gapLen = 9 + Math.floor(r() * 5);
    for (let y = 0; y < H; y++) {
      const fromStart0 = wrap(y - s0, H), fromStart1 = wrap(y - s1, H), along = Math.min(fromStart0, fromStart1);
      const second = fromStart1 < fromStart0;
      for (let x = x0; x < x1; x++) {
        let col = (c + (second ? 1 : 0)) % 2 ? p.bark1 : p.bark2;
        if (along >= 5 && x === x1 - 1) col = p.bark3;                       // the plate's lower part, its edge in shade
        if (along <= 1 && x > x0) col = along === 0 ? p.barkLight : mix(p.barkLight, p.bark1, 0.5);   // the curled top end
        if (along === H - 1 || (y === wrap((second ? s0 : s1) - 1, H) && x > x0)) col = p.deep;      // its shadow below
        if (x === x0 && wrap(y - gapFrom, H) < gapLen) col = wrap(y - gapFrom, H) % 5 === 0 ? p.deep : p.crack;
        put(img, x, y, col);
      }
    }
    x0 = x1;
  }
  return img;
}

/** Red maple: narrow ridges two or three pixels wide, thin dark lines between, frequent short scaly breaks. */
export function mapleBark(p) {
  const W = 16, H = 16, r = rngOf(4601), img = canvas(W, H);
  const lines = [0, 3, 5, 8, 11, 13].map(c => wander(r, c, H, W, 5, 1));
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
    let best = 99, k = 0;
    for (let i = 0; i < lines.length; i++) { const d = wrap(x - lines[i][y], W); if (d < best) { best = d; k = i; } }
    let c = (Math.floor((y + k * 3) / 4) % 3) === 0 ? p.bark2 : (Math.floor((y + k * 3) / 4) % 3) === 1 ? p.bark1 : p.barkLight;
    if (best === 0) c = (y + k) % 4 === 0 ? p.deep : p.crack;
    else if ((y + k * 3) % 4 === 0) c = p.bark3;                               // the scale breaks
    put(img, x, y, c);
  }
  return img;
}

/**
 * Eastern hemlock: three deep furrows that wander, broad ridges between them broken into scales by short slanting
 * cracks at uneven intervals (each ridge its own), the furrows' inner walls purplish.
 */
export function hemlockBark(p) {
  const W = 16, H = 16, r = rngOf(4701), img = canvas(W, H);
  const furrows = [1, 6, 11].map(c => wander(r, c + Math.floor(r() * 2), H, W, 3, 1));
  // scale breaks per ridge: rows 2-5 apart; a break slants down across the ridge by one row
  const cracks = furrows.map(() => { const rows = []; let y = Math.floor(r() * 3); while (y < H) { rows.push(y); y += 2 + Math.floor(r() * 4); } return rows; });
  const tones = furrows.map(() => segments(r, H, 2, 5));
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
    let best = 99, k = 0;
    for (let i = 0; i < furrows.length; i++) { const d = wrap(x - furrows[i][y], W); if (d < best) { best = d; k = i; } }
    const next = furrows[(k + 1) % furrows.length][y], width = wrap(next - furrows[k][y], W) || W;
    let c;
    if (best === 0) c = p.deep;
    else if (best === 1) c = (x + y) % 3 === 0 ? p.deep : p.rust;               // the furrow's purplish inner wall
    else if (best === width - 1) c = p.bark3;
    else c = tones[k][y] ? p.bark1 : p.bark2;
    if (best > 1 && best < width - 1) {
      const slant = best > width / 2 ? 1 : 0;
      if (cracks[k].includes(wrap(y - slant, H))) c = p.crack;                  // a scale edge, slanting
      else if (cracks[k].includes(wrap(y - slant - 1, H)) && best === 2) c = p.barkLight;
    }
    put(img, x, y, c);
  }
  return img;
}

// ---------------------------------------------------------------------------------------------- leaf cards

/** A grey-value card: v, a per pixel (build-tree-textures' image form). */
function card(W) { return {W, H: W, v: new Float32Array(W * W), a: new Uint8Array(W * W), rgb: null}; }

function dot(img, x, y, v) {
  x = Math.floor(x); y = Math.floor(y);
  if (x < 1 || y < 1 || x >= img.W - 1 || y >= img.H - 1) return;
  img.v[y * img.W + x] = v; img.a[y * img.W + x] = 1;
}

function stroke(img, x0, y0, x1, y1, v) {
  const n = Math.ceil(Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)) * 1.5) + 1;
  for (let k = 0; k <= n; k++) dot(img, x0 + (x1 - x0) * k / n, y0 + (y1 - y0) * k / n, v);
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

/** Fills a leaf-local shape: inside(t, s) over the leaf's own frame (t along from the base, s across). */
function blade(img, cx, cy, angle, len, half, inside, value) {
  const ca = Math.cos(angle), sa = Math.sin(angle);
  for (let y = 1; y < img.H - 1; y++) for (let x = 1; x < img.W - 1; x++) {
    const dx = x + 0.5 - cx, dy = y + 0.5 - cy, t = dx * ca + dy * sa, s = -dx * sa + dy * ca;
    if (t < 0 || t > len || Math.abs(s) > half) continue;
    const u = t / len;
    if (!inside(u, s / half)) continue;
    let v = value + (s < 0 ? 0.04 : -0.04) - 0.05 * (1 - u);
    if (Math.abs(s) < 0.5 && u > 0.1 && u < 0.85) v -= 0.07;                   // midrib
    img.v[y * img.W + x] = v; img.a[y * img.W + x] = 1;
  }
}

/** Red oak leaf: an elliptic outline cut by deep sinuses into pointed lobes (a triangle wave, not white oak's round one). */
function redOakLeafShape(lobes) {
  return (u, s) => {
    const env = Math.pow(Math.sin(Math.PI * Math.min(1, 0.06 + 0.94 * u)), 0.7);
    const tri = 1 - 2 * Math.abs((u * lobes + 0.25) % 1 - 0.5);
    const notch = 0.36 + 0.64 * Math.pow(tri, 0.75);
    return Math.abs(s) <= env * notch;
  };
}

export function redOakCluster(seed, leaves, len, spread) {
  const W = 32, r = rngOf(seed), img = card(W);
  const bases = [];
  for (let k = 0; k < leaves; k++) {
    const a = (k / leaves) * Math.PI * 2 * 1.618 + r() * 0.8, d = spread * Math.sqrt((k + 0.5) / leaves) * (0.75 + 0.25 * r());
    bases.push([16 + Math.cos(a) * d * 0.6, 16 + Math.sin(a) * d * 0.6, a + (r() - 0.5) * 0.9, d]);
  }
  stroke(img, 6, 27, 15, 17, 0.28);
  for (let k = 0; k < bases.length; k += 4) stroke(img, 15, 17, bases[k][0], bases[k][1], 0.3);
  bases.sort((p, q) => p[3] - q[3]);
  for (const [x, y, a, d] of bases) {
    const l = len * (0.85 + 0.3 * r());
    blade(img, x - Math.cos(a) * l * 0.2, y - Math.sin(a) * l * 0.2, a, l, l * 0.36, redOakLeafShape(3 + (r() < 0.5 ? 0.5 : 0)),
        0.5 + 0.12 * (d / spread) + 0.12 * r());
  }
  for (let i = 0; i < W * W; i++) if (i % W === 0 || i % W === W - 1 || i < W || i >= W * (W - 1)) img.a[i] = 0;
  despeckle(img);
  return img;
}

/** Hickory: compound leaves, a rachis with two pairs of leaflets and a larger terminal one (the upper three larger). */
export function hickoryCluster(seed, count, len, spread) {
  const W = 32, r = rngOf(seed), img = card(W);
  const ellipse = (u, s) => Math.abs(s) <= Math.pow(Math.sin(Math.PI * u), 0.8);
  const leaves = [];
  for (let k = 0; k < count; k++) {
    const a = (k / count) * Math.PI * 2 * 1.618 + r() * 0.7, d = spread * Math.sqrt((k + 0.5) / count) * (0.7 + 0.3 * r());
    leaves.push([16 + Math.cos(a) * d * 0.45, 16 + Math.sin(a) * d * 0.45, a + (r() - 0.5) * 0.7, d]);
  }
  leaves.sort((p, q) => p[3] - q[3]);
  for (const [bx, by, a, d] of leaves) {
    const L = len * (0.85 + 0.25 * r()), ca = Math.cos(a), sa = Math.sin(a), base = 0.62 + 0.1 * (d / spread) + 0.1 * r();
    // leaflets: pairs at 40 % and 70 % of the rachis, the terminal at its end
    for (const [t, side, size] of [[0.4, 1, 0.5], [0.4, -1, 0.5], [0.7, 1, 0.62], [0.7, -1, 0.62]]) {
      const px = bx + ca * L * t, py = by + sa * L * t, la = a + side * 0.95;
      blade(img, px, py, la, L * size, L * size * 0.22, ellipse, base + (side > 0 ? 0.03 : -0.02));
    }
    blade(img, bx + ca * L * 0.78, by + sa * L * 0.78, a, L * 0.7, L * 0.16, ellipse, base + 0.04);
    stroke(img, bx, by, bx + ca * L * 0.85, by + sa * L * 0.85, base - 0.2);
  }
  despeckle(img);
  return img;
}

/** Red maple: many small three-lobed leaves on long petioles (the petiole a darker line; the tint greens it). */
export function mapleCluster(seed, leaves, size, spread) {
  const W = 32, r = rngOf(seed), img = card(W);
  // a palmate blade in polar form round its base: three lobes (the middle longest), small teeth on the edge
  const lobed = (cx, cy, a, s, v) => {
    for (let y = 1; y < W - 1; y++) for (let x = 1; x < W - 1; x++) {
      const dx = x + 0.5 - cx, dy = y + 0.5 - cy, d = Math.hypot(dx, dy);
      if (d > s * 1.05) continue;
      const th = Math.atan2(dy, dx) - a, rel = Math.atan2(Math.sin(th), Math.cos(th));
      let reach = 0;
      for (const [off, l] of [[0, 1], [0.95, 0.78], [-0.95, 0.78]]) reach = Math.max(reach, l * Math.max(0, Math.cos((rel - off) * 1.6)));
      reach = s * (0.3 + 0.7 * reach) * (0.94 + 0.06 * Math.cos(rel * 14));
      if (d > reach) continue;
      img.v[y * W + x] = v + (Math.sin(rel) > 0 ? 0.03 : -0.03) - (d < 1 ? 0.06 : 0);
      img.a[y * W + x] = 1;
    }
  };
  const list = [];
  for (let k = 0; k < leaves; k++) {
    const a = (k / leaves) * Math.PI * 2 * 1.618 + r() * 0.9, d = spread * Math.sqrt((k + 0.5) / leaves) * (0.75 + 0.25 * r());
    list.push([16 + Math.cos(a) * d * 0.6, 16 + Math.sin(a) * d * 0.6, a + (r() - 0.5) * 0.8, d]);
  }
  stroke(img, 6, 26, 15, 16, 0.3);
  list.sort((p, q) => p[3] - q[3]);
  for (const [x, y, a, d] of list) {
    const pl = 2 + r() * 2, bx = x + Math.cos(a) * pl, by = y + Math.sin(a) * pl;
    stroke(img, x, y, bx, by, 0.36);                                            // the long petiole
    lobed(bx + Math.cos(a) * size * 0.45, by + Math.sin(a) * size * 0.45, a, size * (0.85 + 0.3 * r()), 0.56 + 0.12 * (d / spread) + 0.12 * r());
  }
  despeckle(img);
  return img;
}

/** Hemlock: flat lacy sprays, an axis with alternate side twiglets, short needles in two ranks along each. */
export function hemlockSprays(seed, sprays, len) {
  const W = 32, r = rngOf(seed), img = card(W);
  const twigs = [];
  for (let k = 0; k < sprays; k++) {
    const a = -0.9 + (k / Math.max(1, sprays - 1)) * 1.8 + (r() - 0.5) * 0.3 - Math.PI / 4;
    const bx = 5 + r() * 6 + k * 2.2, by = 26 - r() * 6 - k * 1.4;
    twigs.push([bx, by, a, len * (0.75 + 0.35 * r())]);
  }
  for (const [bx, by, a, L] of twigs) {
    const ca = Math.cos(a), sa = Math.sin(a), nx = -sa, ny = ca;
    // side twiglets alternate, shortening to the tip; needles both sides of axis and twiglets
    const needle = (x, y, dx, dy, n) => {
      for (let i = 0; i <= n; i++) {
        const px = x + dx * i, py = y + dy * i;
        for (const side of [1, -1]) {
          const v = 0.48 + 0.12 * r() + (side < 0 && r() < 0.3 ? 0.22 : 0);   // a white band shows now and then
          dot(img, px + (-dy) * 1.2 * side, py + dx * 1.2 * side, v);
        }
      }
    };
    needle(bx, by, ca, sa, Math.floor(L));
    for (let t = 2; t < L - 1; t += 2.2) {
      const side = (Math.round(t / 2.2) % 2) ? 1 : -1, sl = (L - t) * 0.42 + 1.5;
      const tx = bx + ca * t, ty = by + sa * t, da = a + side * 0.75, dx = Math.cos(da), dy = Math.sin(da);
      needle(tx, ty, dx, dy, Math.floor(sl));
      stroke(img, tx, ty, tx + dx * sl, ty + dy * sl, 0.34);
    }
    stroke(img, bx, by, bx + ca * L, by + sa * L, 0.32);
  }
  despeckle(img);
  return img;
}

/** A 16 x 16 proxy cube face: a dense leafy tile with a few holes, seamless (what a voxel view of the crown shows). */
export function proxyTile(seed, holeA, holeB, base, spread) {
  const W = 16, r = rngOf(seed), img = card(W);
  for (let y = 0; y < W; y++) for (let x = 0; x < W; x++) {
    const i = y * W + x;
    const hole = ((x * holeA[0] + y * holeA[1]) % holeA[2] === 0) || ((x * holeB[0] + y * holeB[1]) % holeB[2] === 0);
    img.a[i] = hole ? 0 : 1;
    img.v[i] = base + spread * r() - ((x + y * 2) % 5 === 0 ? 0.12 : 0);
  }
  return img;
}

// ---------------------------------------------------------------------------------------------- item icons

const shade3 = p => (x, y, cx, cy) => (x + 0.5 - cx) + (y + 0.5 - cy) < -0.3 ? p.twigLight : (x + 0.5 - cx) + (y + 0.5 - cy) > 0.5 ? p.twigDark : p.twig;

/** Red oak leaf icon: tip up, three pointed lobes a side and the tip lobe, deep sinuses, bristle tips lighter. */
export function redOakLeafIcon(p) {
  const img = canvas(16, 16);
  // lobes: from a point on the midrib (y) out to a tip; a narrow triangle each, the midrib's own narrow blade
  const lobes = [[7.5, 2.6, 7.5, 0.6, 1.5], [10.6, 4.4, 7.5, 5.0, 1.3], [4.4, 4.4, 7.5, 5.0, 1.3], [13.4, 6.4, 7.5, 7.8, 1.5],
    [1.6, 6.4, 7.5, 7.8, 1.5], [12.4, 9.8, 7.5, 10.6, 1.3], [2.6, 9.8, 7.5, 10.6, 1.3]];
  const inLobe = (px, py, [tx, ty, bx, by, half]) => {
    const lx = tx - bx, ly = ty - by, len = Math.hypot(lx, ly), t = ((px - bx) * lx + (py - by) * ly) / (len * len);
    if (t < 0 || t > 1) return false;
    const off = Math.abs((px - bx) * ly - (py - by) * lx) / len;
    return off <= half * (1 - t) + 0.35;
  };
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
    const px = x + 0.5, py = y + 0.5;
    const body = Math.abs(px - 7.5) <= 1.7 * Math.sin(Math.PI * Math.min(1, Math.max(0, (py - 1.5) / 11.8))) + 0.2 && py > 1.5 && py < 13.4;
    if (!body && !lobes.some(l => inLobe(px, py, l))) continue;
    put(img, x, y, px < 7.5 ? (py < 8 ? p.leafHi : p.leafLight) : p.leaf);
  }
  for (let y = 1; y <= 13; y++) if (filled(img, 7, y)) put(img, 7, y, p.vein);
  for (const [tx, ty, bx, by] of lobes.slice(1)) for (let k = 2; k < 6; k++) {
    const x = Math.floor(bx + (tx - bx) * k / 7), y = Math.floor(by + (ty - by) * k / 7);
    if (filled(img, x, y) && x !== 7) put(img, x, y, mix(getc(img, x, y), p.vein, 0.55));
  }
  for (const [tx, ty] of lobes) put(img, Math.floor(tx), Math.floor(ty), p.leafHi);   // the bristle tips
  outline(img);
  put(img, 7, 14, p.twig); put(img, 7, 15, p.twigDark);
  return img;
}

/** Hickory leaf icon: a rachis from the lower left, two pairs of leaflets and the large terminal one at the upper right. */
export function hickoryLeafIcon(p) {
  const img = canvas(16, 16);
  const leaflet = (cx, cy, a, len, wid, light) => {
    const ca = Math.cos(a), sa = Math.sin(a);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      const dx = x + 0.5 - cx, dy = y + 0.5 - cy, t = dx * ca + dy * sa, s = -dx * sa + dy * ca;
      if (t < 0 || t > len) continue;
      if (Math.abs(s) > wid * Math.pow(Math.sin(Math.PI * t / len), 0.8) + 0.1) continue;
      put(img, x, y, s < 0 ? (light ? p.leafHi : p.leafLight) : p.leaf);
    }
  };
  const a = -Math.PI / 4;
  leaflet(4.6, 11.4, a - 1.25, 4.4, 1.25, true);
  leaflet(4.6, 11.4, a + 1.25, 4.4, 1.25, false);
  leaflet(7.3, 8.7, a - 1.2, 5.2, 1.45, true);
  leaflet(7.3, 8.7, a + 1.2, 5.2, 1.45, false);
  leaflet(9.0, 7.0, a, 6.6, 1.6, true);
  limb(img, [[1.2, 14.8], [9.2, 6.8]], 0.55, 0.45, p.vein);
  outline(img);
  put(img, 1, 14, p.twig); put(img, 0, 15, p.twigDark);
  return img;
}

/** Red maple leaf icon: three lobes (the middle the longest), toothed edges, green blade, the long red petiole. */
export function mapleLeafIcon(p) {
  const img = canvas(16, 16);
  // the left half of the outline (x, y), mirrored for the right: centre lobe tip, its teeth, the sinus, the side lobe
  const half = [[7.5, 0.6], [6.7, 2.2], [6.2, 2.0], [6.0, 3.6], [5.5, 3.5], [5.3, 5.6], [4.4, 4.8], [3.6, 4.5], [3.5, 3.9],
    [1.0, 4.6], [2.0, 6.4], [1.4, 7.0], [2.6, 8.2], [2.4, 8.8], [4.2, 9.8], [5.4, 11.0], [6.6, 10.6], [7.5, 10.2]];
  const poly = [...half, ...half.slice(0, -1).reverse().map(([x, y]) => [15 - x, y])];
  const inside = (px, py) => {
    let c = false;
    for (let i = 0, j = poly.length - 1; i < poly.length; j = i++) {
      const [xi, yi] = poly[i], [xj, yj] = poly[j];
      if ((yi > py) !== (yj > py) && px < (xj - xi) * (py - yi) / (yj - yi) + xi) c = !c;
    }
    return c;
  };
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
    if (!inside(x + 0.5, y + 0.5)) continue;
    put(img, x, y, x < 7 ? (y < 6 ? p.leafHi : p.leafLight) : p.leaf);
  }
  for (const [x0, y0, x1, y1] of [[7.5, 9.8, 7.5, 1.8], [7.5, 9.8, 2.2, 5.2], [7.5, 9.8, 12.8, 5.2]])
    for (let k = 1; k < 7; k++) {
      const x = Math.floor(x0 + (x1 - x0) * k / 7), y = Math.floor(y0 + (y1 - y0) * k / 7);
      if (filled(img, x, y)) put(img, x, y, mix(getc(img, x, y), p.vein, 0.55));
    }
  outline(img);
  for (let y = 11; y <= 15; y++) put(img, 7, y, y > 13 ? p.twigDark : p.petiole);
  return img;
}

/** Hemlock sprig icon: a flat spray, short dark needles in two ranks, light bands beneath, a small cone at the tip. */
export function hemlockIcon(p) {
  const img = canvas(16, 16);
  const rank = (x0, y0, x1, y1) => {
    const n = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)) * 1.4, dx = (x1 - x0) / n, dy = (y1 - y0) / n;
    for (let k = 0; k <= n; k++) {
      const x = x0 + dx * k, y = y0 + dy * k;
      for (const side of [1, -1]) {
        const nx = x - dy * 1.6 * side, ny = y + dx * 1.6 * side;
        put(img, Math.floor(nx), Math.floor(ny), side > 0 ? (k % 3 ? p.needle : p.needleDark) : (k % 2 ? p.needleLight : p.needleHi));
      }
    }
  };
  rank(2.2, 13.6, 12.6, 3.2);
  rank(5.4, 10.4, 9.6, 12.6);
  rank(8.4, 7.4, 4.6, 4.2);
  rank(10.2, 5.6, 13.6, 8.0);
  limb(img, [[1.2, 14.8], [12.8, 3.0]], 0.5, 0.4, p.twig);
  outline(img);
  for (const [x, y] of [[13, 1], [14, 1], [13, 2], [14, 2]]) put(img, x, y, (x + y) % 2 ? p.cone : mul(p.cone, 0.8));
  return img;
}

/** A broadleaf twig icon: the species' twig colours, its buds, two of its own small leaves (drawn by leafAt). */
function broadleafBranch(p, leafAt, buds, slender) {
  const img = canvas(16, 16);
  leafAt(img, 12.2, 9.8, 0.4);
  leafAt(img, 3.9, 5.1, -2.2);
  const w = slender ? 0.8 : 1.05;
  limb(img, [[1.5, 14.5], [5.2, 11.4], [7.4, 9.9], [9.4, 6.6], [12.6, 3.6], [13.6, 1.8]], w, 0.45, shade3(p));
  limb(img, [[7.4, 9.9], [10.2, 9.6]], w * 0.58, 0.4, shade3(p));
  limb(img, [[5.2, 11.4], [4.6, 7.6]], w * 0.58, 0.4, shade3(p));
  for (const [x, y] of buds) put(img, x, y, p.bud);
  outline(img);
  return img;
}

/** A small leaf of a shape inside(u, s) (u along 0..1, s across -1..1), centred at (cx, cy), along angle a. */
function smallBlade(img, p, cx, cy, a, len, half, inside) {
  const ca = Math.cos(a), sa = Math.sin(a);
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
    const dx = x + 0.5 - cx, dy = y + 0.5 - cy, t = dx * ca + dy * sa + len / 2, s = -dx * sa + dy * ca;
    if (t < 0 || t > len || Math.abs(s) > half + 0.2) continue;
    if (!inside(t / len, s / half)) continue;
    put(img, x, y, Math.abs(s) < 0.45 && t > 0.4 ? p.vein : s < 0 ? p.leafLight : p.leaf);
  }
}

export function redOakBranchIcon(p) {
  return broadleafBranch(p, (img, x, y, a) => smallBlade(img, p, x, y, a, 6.0, 2.2, redOakLeafShape(2.5)), [[13, 1], [14, 2], [13, 2], [12, 2]], false);
}

export function hickoryBranchIcon(p) {
  const ellipse = (u, s) => Math.abs(s) <= Math.pow(Math.sin(Math.PI * u), 0.8);
  const img = broadleafBranch(p, (im, x, y, a) => smallBlade(im, p, x, y, a, 5.6, 1.5, ellipse), [], false);
  // the big tan terminal bud of a hickory, its loose scales
  for (const [x, y, c] of [[13, 0, p.bud], [14, 0, p.bud], [13, 1, p.bud], [14, 1, mul(p.bud, 0.85)], [12, 1, mul(p.bud, 0.85)], [13, 2, mul(p.bud, 0.7)]]) put(img, x, y, c);
  return img;
}

export function mapleBranchIcon(p) {
  const lobed = (u, s) => {
    const k = Math.abs(s);
    return k <= 1 - Math.max(0, 0.75 - u) * 0.6 - (u > 0.55 ? (u - 0.55) * 1.4 * (k > 0.35 ? 1 : 0.2) : 0);
  };
  return broadleafBranch(p, (img, x, y, a) => smallBlade(img, p, x, y, a, 5.4, 2.4, lobed), [[13, 1], [14, 2], [10, 9], [9, 10]], true);
}

export function hemlockBranchIcon(p) {
  const img = canvas(16, 16);
  const rank = (x0, y0, x1, y1) => {
    const n = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)) * 1.4, dx = (x1 - x0) / n, dy = (y1 - y0) / n;
    for (let k = 0; k <= n; k++) for (const side of [1, -1]) {
      const x = x0 + dx * k - dy * 1.4 * side, y = y0 + dy * k + dx * 1.4 * side;
      put(img, Math.floor(x), Math.floor(y), side > 0 ? p.needle : p.needleLight);
    }
  };
  rank(6.5, 9.5, 12.8, 3.2);
  rank(9.4, 6.4, 13.2, 9.2);
  rank(4.6, 11.4, 2.4, 6.4);
  limb(img, [[1.5, 14.5], [6.5, 9.5], [13.0, 3.0]], 0.75, 0.4, shade3(p));
  limb(img, [[9.4, 6.4], [13.0, 9.0]], 0.45, 0.4, shade3(p));
  limb(img, [[4.6, 11.4], [2.6, 6.6]], 0.45, 0.4, shade3(p));
  outline(img);
  for (const [x, y] of [[13, 10], [14, 10], [13, 11], [14, 11]]) put(img, x, y, (x + y) % 2 ? p.cone : mul(p.cone, 0.8));
  return img;
}
