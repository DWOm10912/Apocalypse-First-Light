// AFL vegetation P1 (docs/gameplay/vegetation_v1_design.md 10, 11, 13): the mesh plants' sheet, written to
// src/main/resources/assets/apocalypse_firstlight/textures/block/vegetation/mesh_plants.png and, the same pixels,
// mesh_plants_still.png (what lies or leans takes that copy: the shader moves only the first); --check compares instead
// of writing.
//
// One sprite, 4096 x 128 (P4, doc 20.6; P2b 2048, P1 1024): 128 columns of 32 x 128 (a part's foot at the bottom row, its tip at the top: the wind reads the
// height from v; client/vegetation/PlantShape and the species' shapes map them by column). Grey where the biome's grass
// colour tints it, real colours elsewhere.
//   0-3   Kentucky bluegrass leaf strip (LOD0 blades are geometry, drawn solid): darker edges, a faint lighter midrib and
//         veins, a warmer sheath, a paler tip; the blades take column 1, the same strip either side keeps the mip levels
//         from bleeding other colours into their edges
//   4-5   bluegrass middle cards (LOD1), 6 far card (LOD2), 7 the flowering culm with its open panicle (real colours)
//   8     empty
//   9     broadleaf cattail, last year's leaf: straw brown, parallel veins, a few dark spots (real colours)
//   10    cattail leaf: grey-green, cool, parallel veins, a pale sheath at the foot, a drier tip
//         (both leaf strips drawn in the middle half of the column, the leaves map only that: no mip bleeding)
//   11    cattail stem: round, faint lengthwise ribs
//   12    cattail spikes at the rows of their heights on the stem: the male spike (v 0-0.09: olive straw, fibrous), the
//         female (v 0.09-0.2: chocolate brown velvet); real colours
//   13    yarrow stem: grey-green, finely hairy
//   14    empty
//   15    cattail card (LOD1-2): a stand of long leaves, stems with dark spikes
//   16    yarrow basal leaf: a feathery leaf, petiole at the foot, twice divided pinnae (cut out)
//   17    yarrow foliage card: rosette, stems, stem leaves, the branching under the flower heads
//   18    yarrow flower card: the flat-topped corymbs at the stems' tops (real colours)
//   19    empty
//   20    yarrow parts: rows 0-8 the corymb from above (row 0 its centre, row 8 its rim: mapped round it), the rim's
//         rows its side; the five stem leaves laid across at the rows of their heights (0.2 to 0.8 of the stem)
//   21    empty
//   22    big bluestem leaf strip (middle half): a little cool, a pale midrib, purplish at the foot, a drier tip
//   23    big bluestem culm: round, faint ribs, darker purplish nodes, purplish low down
//   24    big bluestem parts: rows 0-9 four raceme slots (each raceme's foot at row 0, its tip at row 9: the turkey foot,
//         purple-bronze spikelets, yellow anthers; real colours); the two culm leaves laid across at the rows of their
//         heights (0.3, 0.55 of the culm; tinted)
//   25    big bluestem flowering card (LOD1-2): arching leaves, culms with culm leaves and turkey feet
//   26    big bluestem leaves card: the fountain of arching leaves alone
//   27    empty
//   28    little bluestem leaf strip (middle half): blue-green, folded along a keel (one half lighter), purplish foot
//   29    little bluestem culm card: a slender culm, green below and red-bronze above, short racemes with silvery hairs on
//         its upper half (real colours)
//   30    little bluestem bunch card: a vase of upright leaves, culms leaning out with their pale seed heads
//   31    empty
//   32    Pennsylvania sedge leaf bundle: three fine leaves from one foot, reddish at the foot (cut out)
//   33    Pennsylvania sedge mat card: fine leaves arching over and lying out
//   34    empty
//   35    bracken stipe: round, a groove down one side, dark brown at the foot
//   36    bracken blade in rows 0-47 (its foot at row 0, its tip at row 47): rachis, pinnae, pinnules, lobes (cut out)
//   37    broadleaf plantain leaf: a winged petiole, the broad ovate blade with arched parallel veins (cut out)
//   38    plantain spike card: a bare scape, the slender dense spike on its upper half (real colours)
//   39    dandelion leaf: deeply cut into backward-pointing lobes, a pale midrib (cut out)
//   40    dandelion scape: hollow, pale, a little pink at the foot
//   41    dandelion parts (real colours): the left half rows 0-8 the yellow head from above (row 0 its middle, the rim
//         ragged), rows 9-12 the green bracts; the right half rows 0-15 the seed clock
//   42    dandelion scape card: scapes with heads and a clock (real colours)
//   43    purple coneflower basal leaf: a long winged petiole, an ovate blade coarsely toothed, three veins (cut out)
//   44    coneflower stem: hairy, reddish streaks low down
//   45    coneflower parts: the left half rows 0-8 the spiny orange-brown cone (row 0 its tip), the right half rows 0-8
//         one pink-purple ray (its foot at row 0, its notched tip at row 8); real colours; the three stem leaves across
//         the rows of their heights (0.25, 0.45, 0.65; tinted)
//   46    coneflower foliage card, 47 its flower card (real colours)
//   48    black-eyed Susan basal leaf: lanceolate, hairy edged, three veins (cut out)
//   49    Susan stem: bristly
//   50    Susan parts: the left half rows 0-8 the dark brown-purple dome, the right half rows 0-8 one golden ray; real
//         colours; the stem leaves across the rows of their heights (0.2, 0.4, 0.6; tinted)
//   51    Susan foliage card, 52 its flower card (real colours)
//   53    Canada goldenrod stem: faint ribs, finely hairy above
//   54    goldenrod parts: rows 0-32 the raceme (a pattern repeating down the rows: a green axis, tiny yellow heads crowded
//         on it; real colours); the six leaf bands across the rows of their heights (0.2-0.7; lanceolate, tinted)
//   55    goldenrod foliage card, 56 its flower card (the yellow panicles; real colours)
//   57    common reed culm: pale, glaucous, darker nodes, the leaf sheaths' streaks
//   58    reed parts: rows 0-18 the plume (its foot at row 18, narrow, widening to the tip at row 0; purple-brown, silky;
//         real colours); the seven leaves across the rows of their heights (0.2-0.8; broad at the base, tapering to a long
//         point; tinted)
//   59    reed foliage card, 60 its plume card (real colours)
//   61    Allegheny blackberry cane (real colours): green flushed red-purple, ridged, hooked prickles
//   62    blackberry parts: twelve 9-row bands centred on rows 5 + 10.5 k, each a leaflet across its left three quarters
//         (ovate, long tip, toothed, pinnate veins; tinted) and a cluster of drupes in its right quarter (real colours)
//   63    bramble card: canes arching out of a mound of leaves, dark fruit
//   64    switchgrass leaf strip (middle half): a little glaucous, a pale midrib, a drier tip
//   65    switchgrass culm: smooth, darker nodes, a reddish flush under the panicle
//   66    switchgrass parts: rows 0-38 the open pyramidal panicle (its foot at row 38, full width; real colours); the three
//         culm leaves across the rows of their heights (0.2, 0.35, 0.5; tinted)
//   67    switchgrass foliage card, 68 its panicle card (real colours)
//   69    smooth cordgrass culm: stout, the leaf sheaths overlapping, tan at the foot
//   70    cordgrass parts: rows 0-23 the narrow head of appressed spikes (real colours); the five leaves across the rows of
//         their heights (0.12-0.6; tinted)
//   71    cordgrass stand card
//   72    Christmas fern frond (cut out): the scaly stipe up to row 100, the once-pinnate blade above it (eared pinnae)
//   73    fertile Christmas fern frond: the upper pinnae smaller and darker
//   74    Christmas fern card
//   75    cinnamon fern frond (cut out): woolly stipe up to row 100, the blade of deeply lobed pinnae above it
//   76    fertile cinnamon fern frond (real colours, cut out): a stalk, the cinnamon club of sporangia on its upper 55 %
//   77    cinnamon fern card
//   78    common mullein rosette leaf (real colours, cut out): obovate-oblong, a winged base, grey-green felt, pale midrib
//   79    mullein stalk (real colours): grey-green felt
//   80    mullein spike at the stalk's heights (rows 0-80; real colours): woolly buds at the tip, a band of open yellow
//         flowers, green-brown capsules below
//   81    mullein parts (real colours): the four stem leaf bands (0.08-0.38)
//   82    mullein stalk card (real colours)
//   83    wild mint stem: square, hairy, purplish low down
//   84    mint parts: rows 0-9 the pale lilac flower whorl (real colours); the five leaf bands (0.2-0.84; ovate, toothed)
//   85    mint card
//   86    common milkweed stem: stout, pale, downy
//   87    milkweed parts: rows 0-16 the drooping pink-purple umbel (left half) and the warty pod (right half), real
//         colours; the six leaf bands (0.22-0.72; broad oblong, pale midrib, straight side veins)
//   88    milkweed foliage card, 89 its flower card (umbels and a pod; real colours)
//   90    poison ivy petiole: reddish at the foot and the top
//   91    poison ivy parts: the leaflets in bands (end leaflets 0.25 and 0.85, side leaflets lobed outward 0.45 and 0.65;
//         glossy)
//   92    poison ivy card
//   93    lowbush blueberry spray (real colours, cut out): a red-brown twig, side twigs, small blue-green leaves
//   94    the same with clusters of blue berries
//   95    blueberry mat card (real colours)
//   96    American elderberry cane (real colours): grey-brown, warty lenticels, greener up the cane
//   97    elderberry compound leaf (cut out): the rachis, three pairs of toothed leaflets and the end one
//   98    elderberry parts (real colours): the left half rows 0-15 the flat-topped white cyme from above, the right half
//         rows 0-31 the hanging purple-black berry cluster
//   99    elderberry foliage card, 100 its flower card (cymes and berries; real colours)
//   101   big sagebrush bark (real colours): grey-brown, shredding
//   102   sagebrush foliage (real colours, cut out): rows 0-31 a silver-grey mass from the side, rows 32-63 from above
//   103   sagebrush card (real colours)
//   104   American beachgrass leaf strip: rolled, glaucous, a drier tip
//   105   beachgrass culm
//   106   beachgrass spike at the culm's heights (rows 0-36; real colours): pale straw
//   107   beachgrass card
//   108-127 empty (the water plants, the thistle, the mushrooms)
// Drawn 4 x 4 supersampled and cut at half coverage; every transparent pixel takes the colour of the nearest drawn one
// in its column (empty columns a neutral green), so the atlas mipmaps do not darken the edges. No LabPBR maps.
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {png} from '../cube-slab-mesh-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const OUT = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight/textures/block/vegetation');
const COL = 32, H = 128, COLS = 128, W = COL * COLS, SS = 4;

function rng(seed) {
  let s = seed >>> 0;
  return () => {
    s = (s + 0x6D2B79F5) >>> 0;
    let t = s;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

const clamp255 = v => Math.max(0, Math.min(255, v));

// coverage (supersampled) and colour per pixel
const cover = new Float32Array(W * H), rgb = new Float32Array(W * H * 3);

/** Adds a sample's colour to a pixel (the colour is the coverage-weighted mean). */
function add(px, py, r, g, b, w = 1 / (SS * SS)) {
  if (px < 0 || py < 0 || px >= W || py >= H) return;
  const i = py * W + px;
  cover[i] += w;
  rgb[i * 3] += r * w; rgb[i * 3 + 1] += g * w; rgb[i * 3 + 2] += b * w;
}

/** Every pixel of a column, opaque: colour(px, py) (local px 0..31, py 0..127 from the top) -> [r, g, b]. */
function fill(c, colour) {
  for (let py = 0; py < H; py++) for (let px = 0; px < COL; px++) {
    const [r, g, b] = colour(px, py);
    add(c * COL + px, py, clamp255(r), clamp255(g), clamp255(b), 1);
  }
}

/**
 * A thick segment in column c from (x0, y0) to (x1, y1) (local px, y down), width w0 at the start to w1 at the end,
 * colour col (or a function of the share along it); supersampled, kept inside the column.
 */
function seg(c, x0, y0, x1, y1, w0, w1, col) {
  const minX = Math.max(0, Math.floor(Math.min(x0, x1) - Math.max(w0, w1))), maxX = Math.min(COL - 1, Math.ceil(Math.max(x0, x1) + Math.max(w0, w1)));
  const minY = Math.max(0, Math.floor(Math.min(y0, y1) - Math.max(w0, w1))), maxY = Math.min(H - 1, Math.ceil(Math.max(y0, y1) + Math.max(w0, w1)));
  const dx = x1 - x0, dy = y1 - y0, l2 = dx * dx + dy * dy || 1e-9;
  for (let py = minY; py <= maxY; py++) for (let px = minX; px <= maxX; px++)
    for (let sy = 0; sy < SS; sy++) for (let sx = 0; sx < SS; sx++) {
      const x = px + (sx + 0.5) / SS, y = py + (sy + 0.5) / SS;
      const t = Math.max(0, Math.min(1, ((x - x0) * dx + (y - y0) * dy) / l2));
      const qx = x0 + dx * t - x, qy = y0 + dy * t - y, w = w0 + (w1 - w0) * t;
      if (qx * qx + qy * qy > w * w / 4) continue;
      const k = typeof col === 'function' ? col(t) : col;
      add(c * COL + px, py, k[0], k[1], k[2]);
    }
}

/** A curve through points [[x, y], ...] in column c, widths from w0 to w1, colour col (or a function of the share). */
function curve(c, pts, w0, w1, col) {
  let total = 0;
  const lens = [];
  for (let i = 1; i < pts.length; i++) { lens.push(Math.hypot(pts[i][0] - pts[i - 1][0], pts[i][1] - pts[i - 1][1])); total += lens[i - 1]; }
  let run = 0;
  for (let i = 1; i < pts.length; i++) {
    const a = run / total, b = (run + lens[i - 1]) / total;
    run += lens[i - 1];
    seg(c, pts[i - 1][0], pts[i - 1][1], pts[i][0], pts[i][1], w0 + (w1 - w0) * a, w0 + (w1 - w0) * b,
        typeof col === 'function' ? t => col(a + (b - a) * t) : col);
  }
}

/** An ellipse in column c (local px), radii rx, ry, colour. */
function blob(c, cx, cy, rx, ry, col) {
  for (let py = Math.floor(cy - ry - 1); py <= Math.ceil(cy + ry + 1); py++) for (let px = Math.floor(cx - rx - 1); px <= Math.ceil(cx + rx + 1); px++) {
    if (px < 0 || px >= COL || py < 0 || py >= H) continue;
    for (let sy = 0; sy < SS; sy++) for (let sx = 0; sx < SS; sx++) {
      const x = (px + (sx + 0.5) / SS - cx) / rx, y = (py + (sy + 0.5) / SS - cy) / ry;
      if (x * x + y * y <= 1) add(c * COL + px, py, col[0], col[1], col[2]);
    }
  }
}

// ------------------------------------------------------------------------------------------- bluegrass (0-7)

/**
 * A leaf blade in column c: base at (bx, bottom), tip at height h (px), leaning `lean` px sideways at the tip (the arch
 * grows with the height), base width w0 px tapering to the tip; grey value v at mid length, darker at the foot.
 */
function blade(c, bx, h, lean, w0, v, r) {
  const x0 = c * COL;
  const fold = 0.85 + r() * 0.1;   // the blade narrows quickly near its boat-shaped tip
  for (let sy = 0; sy < H * SS; sy++) {
    const y = H - (sy + 0.5) / SS;   // height above the card's foot, px
    if (y > h) continue;
    const t = y / h;
    const cx = bx + lean * Math.pow(t, 1.6);
    const half = 0.5 * w0 * (t < fold ? 1 - 0.35 * t : (1 - 0.35 * fold) * (1 - (t - fold) / (1 - fold)));
    const shade = v * (0.62 + 0.38 * Math.min(1, t * 1.6)) * (1 - 0.06 * t);
    for (let sx = 0; sx < COL * SS; sx++) {
      const x = (sx + 0.5) / SS;
      if (Math.abs(x - cx) > half) continue;
      // a faint lighter midrib
      const mid = Math.abs(x - cx) < half * 0.35 ? 1.05 : 1;
      const g = Math.min(1, shade * mid) * 255;
      add(x0 + Math.floor(x), H - 1 - Math.floor((H * SS - 1 - sy) / SS), g, g, g);
    }
  }
}

function tuftColumn(c, seed, blades, wMin, wMax, hMin) {
  const r = rng(seed);
  for (let k = 0; k < blades; k++) {
    const bx = COL / 2 + (r() - 0.5) * COL * 0.32;
    const h = H * (hMin + (1 - hMin) * r()) - 1;
    const side = bx < COL / 2 ? -1 : 1;
    const lean = side * (2 + r() * (COL * 0.32)) * (r() < 0.2 ? -0.5 : 1);
    blade(c, bx, h, lean, wMin + r() * (wMax - wMin), 0.74 + r() * 0.2, r);
  }
}

/** Column 7: the flowering culm and its panicle (real colours, untinted). */
function culmColumn(c) {
  const r = rng(0x5eed7);
  const x0 = c * COL, cx = COL / 2;
  const top = 3;
  const stem = [118, 148, 82];
  for (let sy = top * SS; sy < H * SS; sy++) {
    const y = sy / SS;
    const t = (H - y) / (H - top);
    const x = cx + 0.8 * Math.sin(t * 2.1);
    const half = 0.55 + 0.25 * (1 - t);
    for (let sx = 0; sx < COL * SS; sx++) {
      const px = (sx + 0.5) / SS;
      if (Math.abs(px - x) <= half) add(x0 + Math.floor(px), Math.floor(y), stem[0], stem[1], stem[2]);
    }
  }
  // the panicle: whorls of slender branches spreading up and out, spikelets near their ends
  const start = H * 0.36;
  for (let w = 0; w < 5; w++) {
    const y = top + 4 + w * (start - top - 4) / 4;
    const reach = (COL * 0.42) * (0.45 + 0.55 * w / 4);
    for (let b = 0; b < 3; b++) {
      const dir = b % 2 === 0 ? 1 : -1;
      const len = reach * (0.6 + r() * 0.4);
      const rise = 6 + r() * 6;
      const steps = Math.ceil(len * SS * 2);
      for (let s = 0; s <= steps; s++) {
        const u = s / steps;
        const px = cx + dir * len * u, py = y - rise * u + 2 * u * u;
        const g = [136 - 20 * u, 140 - 10 * u, 92];
        for (let o = 0; o < SS; o++) add(x0 + Math.floor(px), Math.floor(py), g[0], g[1], g[2]);
        if (u > 0.45 && s % Math.max(1, Math.floor(steps / 4)) === 0) {
          // a spikelet: purple-tinged straw, 2 x 3 px
          const purple = r() < 0.5;
          const col = purple ? [142, 110, 128] : [176, 160, 110];
          for (let dy = 0; dy < 3; dy++) for (let dx = 0; dx < 2; dx++)
            for (let q = 0; q < SS * SS; q++) add(x0 + Math.floor(px) + dx - (dir < 0 ? 1 : 0), Math.floor(py) - dy, col[0], col[1], col[2]);
        }
      }
    }
  }
}

/** The bluegrass leaf strip, every pixel drawn (no cutout on the blades). */
function leafStrip(c) {
  fill(c, (px, py) => {
    const f = 1 - (py + 0.5) / H;   // share of the blade's length from the foot
    const along = 0.8 + 0.2 * Math.min(1, f * 1.5);
    const a = Math.abs(px + 0.5 - COL / 2) / (COL / 2);   // 0 the midrib, 1 the edge
    let g = along * (a > 0.85 ? 0.9 : 1) * (a < 0.08 ? 1.06 : 1);
    for (const vein of [0.32, 0.56, 0.78]) if (Math.abs(a - vein) < 0.035) g *= 1.025;
    // the sheath a little warmer, the tip a little paler and drier
    const warm = Math.max(0, 1 - f / 0.15), dry = Math.max(0, (f - 0.9) / 0.1);
    return [Math.min(1, g * (1 + 0.05 * warm + 0.04 * dry)) * 255, Math.min(1, g * (1 + 0.02 * dry)) * 255, Math.min(1, g * (1 - 0.15 * warm - 0.08 * dry)) * 255];
  });
}

for (let c = 0; c < 4; c++) leafStrip(c);
tuftColumn(4, 0xE1E1, 11, 2.4, 3.0, 0.55);
tuftColumn(5, 0xF00D, 12, 2.4, 3.0, 0.5);
tuftColumn(6, 0x1234, 16, 2.8, 3.4, 0.45);
// LOD2's foot: a band of short blades so a far card reads as turf
{
  const r = rng(0x777);
  for (let k = 0; k < 14; k++) blade(6, 3 + r() * (COL - 6), H * (0.18 + 0.15 * r()), (r() - 0.5) * 6, 3, 0.7 + r() * 0.15, r);
}
culmColumn(7);

// ------------------------------------------------------------------------------------------ cattail (9-12, 15)

/** Across a strip mapped to the middle half of its column: 0 the middle .. 1 its edge (1 also outside it). */
const across = px => Math.min(1, Math.abs(px + 0.5 - COL / 2) / (COL / 4));

/** Parallel veins across a cattail leaf: a fine lighter line every few pixels, the edge a little darker. */
function veins(a, px) {
  let k = (px % 3 === 1) ? 1.03 : 1;
  if (a > 0.86) k *= 0.88;
  return k;
}

// 9: last year's leaf, straw brown, real colours
{
  const r = rng(0xDEAD1);
  const streak = Array.from({length: COL}, () => 0.95 + r() * 0.1);
  const spots = Array.from({length: 9}, () => ({f: 0.15 + r() * 0.8, a: (r() - 0.5) * 1.4, l: 0.012 + r() * 0.02, w: 0.1 + r() * 0.12}));
  fill(9, (px, py) => {
    const f = 1 - (py + 0.5) / H, a = across(px), side = (px + 0.5 - COL / 2) / (COL / 4);
    const base = f < 0.12 ? [118, 102, 78] : f > 0.85 ? [158, 146, 118] : [182, 160, 114];
    const t = f < 0.12 ? f / 0.12 : f > 0.85 ? 1 - (f - 0.85) / 0.15 : 1;
    let col = base.map((v, i) => (f < 0.12 ? v + ([182, 160, 114][i] - v) * t : f > 0.85 ? v + ([182, 160, 114][i] - v) * t : v));
    let k = veins(a, px) * streak[px];
    // small soft spots of mould, longer than wide (the veins run lengthwise)
    for (const s of spots) {
      const d = ((f - s.f) / s.l) ** 2 + ((side - s.a) / s.w) ** 2;
      if (d < 1) k *= 0.78 + 0.22 * d;
    }
    return col.map(v => v * k);
  });
}

// 10: the leaf, grey for the tint: cool (glaucous), a pale sheath at the foot, a drier tip
{
  const r = rng(0x7E4F);
  const streak = Array.from({length: COL}, () => 0.97 + r() * 0.06);
  fill(10, (px, py) => {
    const f = 1 - (py + 0.5) / H, a = across(px);
    let g = (0.84 + 0.1 * Math.min(1, f * 1.6)) * veins(a, px) * streak[px];
    const sheath = Math.max(0, 1 - f / 0.07), dry = Math.max(0, (f - 0.93) / 0.07);
    g *= 1 + 0.12 * sheath - 0.06 * dry;
    return [Math.min(1, g * (0.92 + 0.1 * sheath + 0.12 * dry)) * 255, Math.min(1, g) * 255, Math.min(1, g * (1.06 - 0.02 * sheath - 0.2 * dry)) * 255];
  });
}

// 11: the stem, round: faint ribs round it, paler at the foot
fill(11, (px, py) => {
  const f = 1 - (py + 0.5) / H;
  const g = (0.86 + 0.08 * Math.max(0, 1 - f / 0.08)) * (1 + 0.035 * Math.sin(px / COL * Math.PI * 2 * 6));
  return [Math.min(1, g * 0.95) * 255, Math.min(1, g) * 255, Math.min(1, g * 1.02) * 255];
});

// 12: the spikes at the rows of their heights: the male (v 0-0.09) above the female (v 0.09-0.2); the female's brown below
{
  const r = rng(0x5B1E);
  const fibre = Array.from({length: COL}, () => 0.86 + r() * 0.28);
  const male = 0.09 * H;
  fill(12, (px, py) => {
    if (py < male - 0.5) {
      let k = fibre[px] * (r() < 0.08 ? 1.3 : 1);
      return [118 * k, 98 * k, 64 * k];
    }
    // velvet: fine grain, the edge to the male spike a little darker
    const k = (0.92 + r() * 0.16) * (py < male + 1.5 ? 0.85 : 1);
    return [90 * k, 57 * k, 34 * k];
  });
}

// 15: the card: one shoot seen from the side (h about 2 m over 128 px, w about 0.4 m over 32: pixels wider than tall):
// a fan of long leaves from a narrow foot, spreading and arching out to the card's edges, a few tips broken over, last
// year's leaf bent over; the stem with its spikes in the middle
{
  const r = rng(0xCA4D);
  const leafCol = g => [g * 0.92 * 255, g * 255, g * 1.05 * 255];
  for (let k = 0; k < 10; k++) {
    const side = k % 2 === 0 ? -1 : 1, inner = k / 9;
    const bx = 16 + side * (0.5 + r() * 1.8), top = 3 + (1 - inner) * 34 + r() * 10;
    const out = side * (3 + (1 - inner) * 10 + r() * 3), broken = r() < 0.25;
    const pts = [];
    for (let i = 0; i <= 12; i++) {
      const t = i / 12;
      let x = bx + out * (0.25 * t + 0.75 * t * t), y = H - 1 - (H - 1 - top) * t;
      if (broken && t > 0.75) { x += side * (t - 0.75) * 14; y += (t - 0.75) * 70; }
      pts.push([x, y]);
    }
    const g = 0.76 + r() * 0.16;
    curve(15, pts, 1.9, 0.45, t => leafCol(g * (0.86 + 0.14 * Math.min(1, t * 2))));
  }
  // last year's leaf bent over half way up (straw, a little warmer than the tint makes the green)
  curve(15, [[15, 127], [14.5, 100], [14, 74], [9, 66], [5, 72], [3, 80]], 1.8, 0.7, [158, 152, 124]);
  const top = 14, len = H - 1 - top, sx = 17;
  seg(15, sx, H - 1, sx + 0.5, top, 1.3, 1.0, [0.86 * 0.95 * 255, 0.86 * 255, 0.86 * 255]);
  // the female spike (dark under the tint: reads dark brown) and the thin male spike above it
  seg(15, sx + 0.4, top + len * 0.2, sx + 0.45, top + len * 0.09, 2.8, 2.6, [70, 54, 40]);
  seg(15, sx + 0.45, top + len * 0.09, sx + 0.5, top, 1.1, 0.6, [110, 96, 70]);
}

// -------------------------------------------------------------------------------------------- yarrow (13, 16-20)

// 13: the stem, grey-green, finely hairy
{
  const r = rng(0x5713);
  fill(13, (px, py) => {
    const g = (0.8 + 0.04 * Math.sin(px / COL * Math.PI * 2 * 4)) * (r() < 0.06 ? 1.12 : 1);
    return [g * 0.96 * 255, g * 255, g * 0.94 * 255];
  });
}

/**
 * A feathery yarrow leaf in column c: its rachis along pts (from the foot), pinnae either side at shares from `from` to
 * the tip, each envelope(t) long at angle `angle` from the rachis towards the tip, every pinna with small lobes; widths
 * in px, grey g.
 */
function feather(c, pts, from, envelope, angle, g, rr, width = 1.2, density = 1.4) {
  const col = [g * 0.95 * 255, g * 255, g * 0.92 * 255];
  curve(c, pts, width * 1.3, width * 0.7, col);
  const at = t => {
    const s = t * (pts.length - 1), i = Math.min(pts.length - 2, Math.floor(s)), u = s - i;
    return [pts[i][0] + (pts[i + 1][0] - pts[i][0]) * u, pts[i][1] + (pts[i + 1][1] - pts[i][1]) * u,
      Math.atan2(pts[i + 1][1] - pts[i][1], pts[i + 1][0] - pts[i][0])];
  };
  const pairs = Math.max(4, Math.round(envelope(0.5) * density));
  for (let k = 0; k < pairs * 2; k++) {
    const t = from + (1 - from) * (k + 0.5) / (pairs * 2);
    const [x, y, dir] = at(t), len = envelope(t) * (0.85 + rr() * 0.25);
    if (len < 0.8) continue;
    const side = k % 2 === 0 ? 1 : -1, a = dir + side * angle;
    const ex = x + Math.cos(a) * len, ey = y + Math.sin(a) * len;
    seg(c, x, y, ex, ey, width, width * 0.7, col);
    // lobes: short spurs off the pinna, alternately
    const lobes = Math.min(3, Math.floor(len / 2.2));
    for (let l = 1; l <= lobes; l++) {
      const s = l / (lobes + 1), lx = x + (ex - x) * s, ly = y + (ey - y) * s, ls = (l % 2 === 0 ? 1 : -1);
      const la = a + ls * 0.8, ll = len * 0.32 * (1 - s * 0.5);
      seg(c, lx, ly, lx + Math.cos(la) * ll, ly + Math.sin(la) * ll, width * 0.8, width * 0.6, col);
    }
  }
}

// 16: the basal leaf, foot at the bottom: petiole, then a lanceolate feathery blade
{
  const r = rng(0xBA5A);
  const pts = [];
  for (let i = 0; i <= 12; i++) pts.push([16 + Math.sin(i / 12 * 2.2) * 1.2, H - 1 - (H - 3) * i / 12]);
  feather(16, pts, 0.12, t => t < 0.12 ? 0 : 14 * Math.pow(Math.sin(Math.PI * Math.min(1, (t - 0.12) / 0.9)), 0.75) * (t > 0.5 ? 1 - (t - 0.5) * 0.6 : 1),
      Math.PI * 0.27, 0.82, r, 1.3, 1.25);
}

// 17 / 18: a flowering yarrow seen from the side (about 0.55 m over 128 px): foliage (tinted) and flowers (real colours)
{
  const r = rng(0xF0A5);
  // the rosette
  for (let k = 0; k < 9; k++) {
    const side = k % 2 === 0 ? 1 : -1, reach = 5 + r() * 9, rise = 10 + r() * 22;
    const pts = [];
    for (let i = 0; i <= 6; i++) {
      const t = i / 6;
      pts.push([16 + side * reach * t * (0.4 + 0.6 * t), H - 1 - rise * Math.sin(t * Math.PI * 0.75)]);
    }
    feather(17, pts, 0.15, t => 3.2 * Math.sin(Math.PI * Math.min(1, t)), Math.PI * 0.32, 0.76 + r() * 0.1, r, 0.9);
  }
  // the stems, their leaves, the branching under the corymbs; the corymbs on the flower card
  const stems = [[9.5, 9], [22.5, 13], [16, 24]];
  for (const [sx, top] of stems) {
    const pts = [[16 + (sx - 16) * 0.2, H - 1], [16 + (sx - 16) * 0.6, H * 0.6], [sx, top + 8]];
    curve(17, pts, 1.15, 0.85, [0.8 * 0.95 * 255, 0.8 * 255, 0.8 * 0.92 * 255]);
    for (const [t, len] of [[0.3, 7], [0.55, 5.5], [0.8, 4]]) {
      const y = H - 1 - (H - 1 - top - 8) * t, x = 16 + (sx - 16) * (0.2 + 0.8 * t), side = t === 0.55 ? -1 : 1;
      feather(17, [[x, y], [x + side * len * 0.6, y - len * 0.5], [x + side * len, y - len * 0.6]], 0.1, () => 1.6, Math.PI * 0.3, 0.8, r, 0.8);
    }
    // peduncles: an umbrella of fine branches up to the corymb's underside
    for (let b = 0; b < 7; b++) {
      const ex = sx + (b - 3) * 1.6 + (r() - 0.5);
      seg(17, sx, top + 8, ex, top + 2.5, 0.75, 0.6, [0.82 * 0.95 * 255, 0.82 * 255, 0.82 * 0.9 * 255]);
    }
    // the corymb: a flat-topped dome of small white heads with cream centres, grey shade beneath
    const half = 5 + r() * 1.5;
    for (let h = 0; h < 34; h++) {
      const u = (r() - 0.5) * 2, x = sx + u * half, dome = 1 - u * u;
      const y = top + 3 - dome * 2.2 - r() * 2.4;
      blob(18, x, y + 1.1, 1.2, 0.8, [150, 152, 138]);
      blob(18, x, y, 1.25, 0.9, [238, 236, 224]);
      if (r() < 0.6) blob(18, x + (r() - 0.5) * 0.6, y - 0.2, 0.45, 0.4, [214, 200, 150]);
    }
  }
}

// 20: the parts: the corymb from above in rows 0-8 (round it: u is the angle, v the distance from the centre), then
// the stem leaves laid across at the rows of their heights
{
  const r = rng(0x2C0F);
  // the ground of the head: creamy white, a little shadow between the heads
  for (let py = 0; py <= 8; py++) for (let px = 0; px < COL; px++) {
    const k = 0.9 + r() * 0.08;
    if (py >= 7 && r() < (py === 8 ? 0.55 : 0.3)) continue;   // the rim is ragged: heads and gaps
    add(20 * COL + px, py, 214 * k, 212 * k, 196 * k, 1);
  }
  for (let h = 0; h < 70; h++) {
    const x = r() * COL, y = 0.8 + r() * 7;
    blob(20, x, y, 1.6, 0.8 + y * 0.05, [242, 240, 230]);
    if (r() < 0.7) blob(20, x + (r() - 0.5) * 0.5, y, 0.5, 0.35, [222, 206, 150]);
  }
  for (const f of [0.2, 0.35, 0.5, 0.65, 0.8]) {
    const cy = H * (1 - f), len = 29;
    const pts = [];
    for (let i = 0; i <= 6; i++) pts.push([1.2 + len * i / 6, cy + Math.sin(i / 6 * 2.5) * 0.4]);
    feather(20, pts, 0.08, t => 4 * Math.pow(Math.sin(Math.PI * Math.min(1, t * 1.05)), 0.7), Math.PI * 0.3, 0.8, r, 0.85);
  }
}

// ------------------------------------------------------------------------------- big bluestem (22-26, P2b)

// 22: the leaf, grey for the tint: a little cool, a pale midrib, fine veins, purplish at the foot, a drier tip
{
  const r = rng(0xB1E1);
  const streak = Array.from({length: COL}, () => 0.97 + r() * 0.06);
  fill(22, (px, py) => {
    const f = 1 - (py + 0.5) / H, a = across(px);
    let g = (0.8 + 0.12 * Math.min(1, f * 1.8)) * streak[px];
    if (a < 0.1) g *= 1.1;
    else if (px % 3 === 0) g *= 1.025;
    if (a > 0.88) g *= 0.9;
    const foot = Math.max(0, 1 - f / 0.1), dry = Math.max(0, (f - 0.9) / 0.1);
    return [Math.min(1, g * (0.94 + 0.16 * foot + 0.1 * dry)) * 255, Math.min(1, g * (1 - 0.14 * foot)) * 255,
      Math.min(1, g * (1.05 - 0.02 * foot - 0.18 * dry)) * 255];
  });
}

// 23: the culm: faint ribs round it, darker purplish nodes, purplish low down
{
  const nodes = [0.22, 0.42, 0.6, 0.76, 0.88];
  fill(23, (px, py) => {
    const f = 1 - (py + 0.5) / H;
    let g = 0.8 * (1 + 0.03 * Math.sin(px / COL * Math.PI * 2 * 5)), purple = Math.max(0, 1 - f / 0.25) * 0.6;
    for (const n of nodes) {
      const d = Math.abs(f - n) * H;
      if (d < 1.2) { g *= 0.78; purple = Math.max(purple, 0.9); } else if (d < 2.5) g *= 1.04;
    }
    return [Math.min(1, g * (0.95 + 0.14 * purple)) * 255, Math.min(1, g * (1 - 0.16 * purple)) * 255, Math.min(1, g * (1.02 + 0.02 * purple)) * 255];
  });
}

/** A grass leaf in column c along pts from its foot: even to 60 %, then drawn out to a fine point; grey g, a pale midrib. */
function grassLeaf(c, pts, w, g) {
  const col = [g * 0.95 * 255, g * 255, g * 0.93 * 255], rib = [Math.min(255, g * 1.08 * 0.95 * 255), Math.min(255, g * 1.08 * 255), Math.min(255, g * 1.08 * 0.93 * 255)];
  const at = t => {
    const s = t * (pts.length - 1), i = Math.min(pts.length - 2, Math.floor(s)), u = s - i;
    return [pts[i][0] + (pts[i + 1][0] - pts[i][0]) * u, pts[i][1] + (pts[i + 1][1] - pts[i][1]) * u];
  };
  const steps = 14;
  for (let k = 0; k < steps; k++) {
    const t0 = k / steps, t1 = (k + 1) / steps, wa = t0 < 0.6 ? w * (1 - 0.15 * t0) : w * 0.91 * (1 - (t0 - 0.6) / 0.4 * 0.92),
      wb = t1 < 0.6 ? w * (1 - 0.15 * t1) : w * 0.91 * (1 - (t1 - 0.6) / 0.4 * 0.92);
    const [x0, y0] = at(t0), [x1, y1] = at(t1);
    seg(c, x0, y0, x1, y1, wa, wb, col);
    if (w > 3) seg(c, x0, y0, x1, y1, wa * 0.18, wb * 0.18, rib);
  }
}

// 24: the parts. Rows 0-9: four raceme slots, each raceme's foot at row 0 and tip at row 9 (real colours): a dark rachis,
// alternate purple-bronze spikelets, yellow anthers hanging out. Then the culm leaves across the rows of their heights.
{
  const r = rng(0x7A4C);
  for (let slot = 0; slot < 4; slot++) {
    const cx = slot * 8 + 4 + (r() - 0.5) * 0.6, sway = (r() - 0.5) * 1.2;
    seg(24, cx, 0.2, cx + sway, 9.6, 0.9, 0.6, [92, 60, 68]);
    for (let k = 0; k < 9; k++) {
      const y = 0.7 + k * 1.0, side = k % 2 === 0 ? 1 : -1, x = cx + sway * y / 9.6 + side * 1.25;
      blob(24, x, y, 1.25, 0.7, r() < 0.5 ? [126, 80, 96] : [146, 104, 90]);
      if (r() < 0.45) blob(24, x + side * 1.3, y + 0.4, 0.5, 0.45, [222, 172, 66]);
    }
  }
  for (const f of [0.3, 0.55]) {
    const cy = H * (1 - f), pts = [];
    for (let i = 0; i <= 8; i++) pts.push([1 + 29.5 * i / 8, cy + Math.sin(i / 8 * 2.4) * 0.5]);
    grassLeaf(24, pts, 7, 0.82);
  }
}

// 25: a flowering clump seen from the side (about 1.6 m over 128 px, 0.4 m over 32: square pixels): arching leaves to
// about a third of the height, three culms with two leaves each and a dark turkey foot at the top
{
  const r = rng(0xB16C);
  for (let k = 0; k < 14; k++) {
    const side = k % 2 === 0 ? -1 : 1, reach = 3 + r() * 11, rise = 24 + r() * 24, pts = [];
    for (let i = 0; i <= 8; i++) {
      const t = i / 8;
      pts.push([16 + side * (0.5 + reach * Math.pow(t, 1.4)), H - 1 - rise * Math.sin(t * Math.PI * 0.62) + Math.max(0, t - 0.7) * 18]);
    }
    curve(25, pts, 1.3, 0.3, [0.8 * 0.95 * 255, (0.8 + r() * 0.1) * 255, 0.8 * 0.93 * 255]);
  }
  for (const [sx, top] of [[10.5, 9], [21, 5], [15.5, 14]]) {
    const foot = 16 + (sx - 16) * 0.15;
    curve(25, [[foot, H - 1], [16 + (sx - 16) * 0.6, H * 0.5], [sx, top + 6]], 1.0, 0.75, [0.78 * 0.95 * 255, 0.78 * 255, 0.78 * 255]);
    for (const [t, dir] of [[0.32, -1], [0.55, 1]]) {
      const y = H - 1 - (H - 1 - top - 6) * t, x = foot + (sx - foot) * t;
      curve(25, [[x, y], [x + dir * 4, y - 4], [x + dir * 9, y + 1], [x + dir * 12, y + 6]], 1.2, 0.3, [0.8 * 0.95 * 255, 0.8 * 255, 0.8 * 0.93 * 255]);
    }
    for (const a of [-0.45, 0, 0.42]) seg(25, sx, top + 6, sx + Math.sin(a) * 6, top + 6 - Math.cos(a) * 6.2, 1.1, 0.8, [84, 64, 78]);
  }
}

// 26: the leaves alone (about 0.55 m over 128 px, 0.22 m over 32: pixels wider than tall): a fountain arching out to the edges
{
  const r = rng(0xB16D);
  for (let k = 0; k < 18; k++) {
    const side = k % 2 === 0 ? -1 : 1, reach = 4 + r() * 11.5, rise = 70 + r() * 52, pts = [];
    for (let i = 0; i <= 10; i++) {
      const t = i / 10;
      pts.push([16 + side * (0.5 + reach * Math.pow(t, 1.5)), H - 1 - rise * Math.sin(t * Math.PI * 0.6) + Math.max(0, t - 0.65) * 60]);
    }
    curve(26, pts, 1.7, 0.4, [0.8 * 0.95 * 255, (0.78 + r() * 0.12) * 255, 0.8 * 0.93 * 255]);
  }
}

// ----------------------------------------------------------------------------- little bluestem (28-30, P2b)

// 28: the leaf, grey for the tint: blue-green, folded along a keel (one half a little lighter), purplish foot, dry tip
{
  const r = rng(0x5C28);
  const streak = Array.from({length: COL}, () => 0.97 + r() * 0.06);
  fill(28, (px, py) => {
    const f = 1 - (py + 0.5) / H, a = across(px), side = px + 0.5 < COL / 2 ? -1 : 1;
    let g = (0.82 + 0.1 * Math.min(1, f * 1.6)) * streak[px] * (side < 0 ? 1.05 : 0.95);
    if (a < 0.07) g *= 0.86;
    if (a > 0.9) g *= 0.92;
    const foot = Math.max(0, 1 - f / 0.12), dry = Math.max(0, (f - 0.88) / 0.12);
    return [Math.min(1, g * (0.9 + 0.2 * foot + 0.12 * dry)) * 255, Math.min(1, g * (1 - 0.12 * foot)) * 255,
      Math.min(1, g * (1.1 - 0.06 * foot - 0.2 * dry)) * 255];
  });
}

// 29: a culm card (about 0.6 m over 128 px, square pixels; real colours): a slender culm, blue-green below and red-bronze
// above, darker nodes; on its upper half short racemes on fine stalks, each with a pale sheath at its foot and silvery
// hairs along it; one raceme ends the culm
{
  const r = rng(0x5C29);
  const lowC = [122, 140, 116], highC = [152, 108, 84];
  const cx = t => 16 + 1.4 * Math.sin(t * 2.2);
  const top = 3;
  for (let i = 0; i < 40; i++) {
    const t0 = i / 40, t1 = (i + 1) / 40, y0 = H - 1 - (H - 1 - top) * t0, y1 = H - 1 - (H - 1 - top) * t1;
    const k = Math.min(1, Math.max(0, (t0 - 0.3) / 0.5)), node = [0.25, 0.48, 0.68].some(n => Math.abs(t0 - n) < 0.013) ? 0.72 : 1;
    seg(29, cx(t0), y0, cx(t1), y1, 1.0, 1.0, lowC.map((v, j) => (v + (highC[j] - v) * k) * node));
  }
  const ys = [];
  for (let k = 0; k < 7; k++) ys.push(10 + k * 7.5 + r() * 2);
  ys.push(top + 0.5);
  ys.forEach((y, k) => {
    const t = (H - 1 - y) / (H - 1 - top), x = cx(t), last = k === ys.length - 1, side = k % 2 === 0 ? 1 : -1;
    const a = last ? 0.05 : side * (0.3 + r() * 0.3), len = 7 + r() * 3;
    const sx = x + Math.sin(a) * 1.5, sy = y - Math.cos(a) * 1.5, ex = x + Math.sin(a) * len, ey = y - Math.cos(a) * len;
    if (!last) seg(29, x, y, sx, sy, 0.6, 0.6, [140, 112, 86]);
    blob(29, sx, sy + 0.6, 0.7, 1.6, [150, 132, 96]);
    seg(29, sx, sy, ex, ey, 0.7, 0.5, [158, 110, 82]);
    for (let h = 0; h < 5; h++) {
      const u = (h + 0.5) / 5, hx = sx + (ex - sx) * u + side * 0.5, hy = sy + (ey - sy) * u;
      blob(29, hx, hy, 0.9 + r() * 0.35, 0.8, [228 + r() * 10, 226 + r() * 8, 216 + r() * 8]);
    }
  });
}

// 30: a bunch seen from the side (about 0.65 m over 128 px, 0.21 m over 32): a vase of upright leaves, culms leaning out
// with pale seed heads (tinted: the hairs read as the palest green)
{
  const r = rng(0x5C30);
  for (let k = 0; k < 22; k++) {
    const side = k % 2 === 0 ? -1 : 1, reach = 1 + r() * 8.5, rise = 32 + r() * 30, pts = [];
    for (let i = 0; i <= 6; i++) {
      const t = i / 6;
      pts.push([16 + side * (0.3 + reach * Math.pow(t, 1.6)), H - 1 - rise * Math.sin(t * Math.PI * 0.5) + Math.max(0, t - 0.8) * 10]);
    }
    curve(30, pts, 1.3, 0.3, [0.8 * 0.92 * 255, (0.8 + r() * 0.1) * 255, 0.86 * 255]);
  }
  for (let k = 0; k < 7; k++) {
    const side = k % 2 === 0 ? -1 : 1, tx = 16 + side * (1 + r() * 11), ty = 3 + r() * 24;
    curve(30, [[16 + side * 0.5, H - 1], [16 + (tx - 16) * 0.35, H * 0.55], [tx, ty]], 0.8, 0.6, [0.78 * 0.95 * 255, 0.78 * 255, 0.8 * 255]);
    for (let h = 0; h < 6; h++) {
      const u = h / 6, y = ty + u * 36, x = tx + (16 - tx) * u * 0.25 + (h % 2 === 0 ? 0.9 : -0.9);
      blob(30, x, y, 0.95, 1.3, [236, 236, 230]);
    }
  }
}

// ------------------------------------------------------------------------- Pennsylvania sedge (32-33, P2b)

// 32: a leaf bundle (the strip spans the middle 84 %): three fine leaves from one foot, diverging, the middle one longest;
// reddish at the foot (cut out)
{
  const r = rng(0xCA32);
  for (const i of [-1, 0, 1]) {
    const g = 0.84 + r() * 0.08, tipX = 16 + i * 8.5 + (r() - 0.5) * 2, tipY = 2 + Math.abs(i) * 11 + r() * 6;
    const pts = [];
    for (let k = 0; k <= 10; k++) {
      const t = k / 10;
      pts.push([16 + i * 1.1 + (tipX - 16 - i * 1.1) * Math.pow(t, 1.3), H - 1 - (H - 1 - tipY) * t]);
    }
    curve(32, pts, 2.6, 0.5, t => {
      const foot = Math.max(0, 1 - t / 0.1);
      return [g * (0.95 + 0.17 * foot) * 255, g * (1 - 0.16 * foot) * 255, g * (0.9 - 0.02 * foot) * 255];
    });
  }
}

// 33: the mat card (about 0.18 m over 128 px, 0.11 m over 32: pixels wider than tall): fine leaves from the crown arching
// right over, the outer ones lying out to the card's edges
{
  const r = rng(0xCA33);
  for (let k = 0; k < 30; k++) {
    const side = k % 2 === 0 ? -1 : 1, reach = 5 + r() * 10.5, rise = 28 + r() * 62, pts = [];
    for (let i = 0; i <= 10; i++) {
      const t = i / 10;
      pts.push([16 + side * (0.4 + reach * Math.pow(t, 0.9)), H - 1 - rise * Math.sin(t * Math.PI * 0.75) + Math.max(0, t - 0.5) * rise * 1.1]);
    }
    curve(33, pts, 1.3, 0.35, [0.86 * 0.95 * 255, (0.82 + r() * 0.1) * 255, 0.82 * 255]);
  }
}

// ------------------------------------------------------------------------------------- bracken (35-36, P2b)

// 35: the stipe, grey for the tint: a groove down one side, a little warm, dark brown at the foot
fill(35, (px, py) => {
  const f = 1 - (py + 0.5) / H, foot = Math.max(0, 1 - f / 0.1);
  let g = 0.84 * (px >= 7 && px <= 9 ? 0.86 : 1) * (1 + 0.02 * Math.sin(px / COL * Math.PI * 2 * 3));
  const warm = [g * 1.02, g, g * 0.9], dark = [0.42, 0.33, 0.25];
  return warm.map((v, i) => Math.min(1, v + (dark[i] - v) * foot) * 255);
});

// 36: the blade in rows 0-47 (foot at row 0, tip at row 47; about 0.5 m over 48 px and 0.45 m over 32): the rachis down
// the middle, pinnae in near-opposite pairs angled towards the tip, the lowest the largest (a broad triangle), each
// pinna divided into pinnules with small lobes (cut out)
{
  const r = rng(0xF336), g = 0.84;
  const col = [g * 1.0 * 255, g * 255, g * 0.86 * 255];
  seg(36, 16, 0.2, 16, 47.4, 1.1, 0.5, col);
  const pairs = 9;
  for (let k = 0; k < pairs; k++) {
    const t = 0.02 + 0.92 * k / pairs, y = 0.3 + t * 47, L = 17.5 * Math.pow(1 - t, 0.9);
    if (L < 1.2) continue;
    for (const s of [-1, 1]) {
      const th = (55 - 12 * t + (r() - 0.5) * 6) * Math.PI / 180, yy = y + (s > 0 ? 0.6 : 0);
      const ex = 16 + s * L * Math.sin(th), ey = yy + L * Math.cos(th);
      const pts = [];
      for (let i = 0; i <= 6; i++) {
        const u = i / 6;
        pts.push([16 + (ex - 16) * u, yy + (ey - yy) * u + Math.sin(u * Math.PI) * 0.6]);
      }
      // pinnules short and spaced, so each pinna reads on its own with daylight between the pairs
      feather(36, pts, 0.1, u => L * 0.2 * Math.pow(Math.sin(Math.PI * Math.min(1, u * 0.95 + 0.05)), 0.8) * (1 - 0.45 * u),
        Math.PI * 0.32, g * (0.96 + r() * 0.08), r, 0.75, 0.9);
    }
  }
}

// ------------------------------------------------------------------------------ P2b batch 2 (37-52)

/** A filled polygon in column c (local px), supersampled: pts [[x, y], ...], colour col (or a function of (x, y)). */
function poly(c, pts, col) {
  const xs = pts.map(q => q[0]), ys = pts.map(q => q[1]);
  for (let py = Math.max(0, Math.floor(Math.min(...ys))); py <= Math.min(H - 1, Math.ceil(Math.max(...ys))); py++)
    for (let px = Math.max(0, Math.floor(Math.min(...xs))); px <= Math.min(COL - 1, Math.ceil(Math.max(...xs))); px++)
      for (let sy = 0; sy < SS; sy++) for (let sx = 0; sx < SS; sx++) {
        const x = px + (sx + 0.5) / SS, y = py + (sy + 0.5) / SS;
        let inside = false;
        for (let i = 0, j = pts.length - 1; i < pts.length; j = i++) {
          const [xi, yi] = pts[i], [xj, yj] = pts[j];
          if ((yi > y) !== (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside;
        }
        if (!inside) continue;
        const k = typeof col === 'function' ? col(x, y) : col;
        add(c * COL + px, py, k[0], k[1], k[2]);
      }
}

/**
 * A broad leaf in column c along the v axis: the blade from row `top` (its tip) down to row `base`, half width
 * half(t) px (t 0 at the blade's base .. 1 at its tip), a petiole below it to the bottom row `petiole` px wide; grey g,
 * lighter veins (veinAt(a, t): 0..1 how much of a vein at a = across share), the edge given teeth(t, side) px of notch.
 */
function broadLeaf(c, top, base, half, petiole, g, veinAt, teeth = () => 0) {
  const col = (x, y) => {
    const t = (base - y) / (base - top), w = Math.max(0.5, half(Math.max(0, Math.min(1, t)))), a = (x - COL / 2) / w;
    const k = 1 + 0.1 * veinAt(Math.abs(a), t) - (Math.abs(a) > 0.9 ? 0.06 : 0);
    return [g * 0.96 * k * 255, g * k * 255, g * 0.9 * k * 255];
  };
  const pts = [];
  const N = 48;
  for (let i = 0; i <= N; i++) { const t = i / N; pts.push([COL / 2 - Math.max(0, half(t) - teeth(t, -1)), base - (base - top) * t]); }
  for (let i = N; i >= 0; i--) { const t = i / N; pts.push([COL / 2 + Math.max(0, half(t) - teeth(t, 1)), base - (base - top) * t]); }
  poly(c, pts, col);
  if (petiole > 0) poly(c, [[COL / 2 - petiole / 2, H], [COL / 2 + petiole / 2, H], [COL / 2 + petiole * 0.7, base + 1], [COL / 2 - petiole * 0.7, base + 1]],
    (x, y) => [g * 1.05 * 0.95 * 255, g * 1.05 * 255, g * 1.05 * 0.86 * 255]);
}

// 37: plantain leaf: winged petiole up to row 88, the ovate blade above it, five arched veins meeting at the tip
broadLeaf(37, 3, 88, t => 15.2 * Math.pow(Math.sin(Math.PI * Math.min(1, Math.pow(t, 0.8) * 0.98 + 0.02)), 0.62), 5, 0.74,
  (a, t) => [0, 0.33, 0.62, 0.86].some(v => Math.abs(a - v * (1 - 0.2 * t)) < 0.05) ? 1 : 0, (t, side) => 0.4 * Math.abs(Math.sin(t * 40 + side)));

// 38: plantain spike card (about 0.2 m over 128 px, 0.025 m over 32: pixels narrower than tall; real colours): a bare
// scape below, the dense spike above: green-brown capsules, pale anthers here and there
{
  const r = rng(0x9138);
  seg(38, 16, H - 1, 16.6, 62, 1.6, 1.4, [128, 142, 96]);
  for (let y = 4; y < 64; y += 0.7) {
    const x = 16.6 + Math.sin(y * 0.08) * 0.6, w = 5.6 - (y < 12 ? (12 - y) * 0.35 : 0);
    blob(38, x + (r() - 0.5) * 1.2, y, w / 2, 0.7, r() < 0.5 ? [118, 116, 70] : [98, 86, 54]);
    if (r() < 0.12) blob(38, x + (r() < 0.5 ? -1 : 1) * w * 0.55, y, 0.9, 0.5, [206, 196, 206]);
  }
}

// 39: dandelion leaf: oblanceolate, deeply cut into triangular lobes pointing back to the foot, a broad end lobe; a pale
// midrib, reddish at the foot (grey for the tint)
{
  const g = 0.8, col = [g * 0.95 * 255, g * 255, g * 0.88 * 255];
  const half = t => 14.5 * (0.18 + 0.82 * Math.pow(t, 0.7));
  poly(39, [[14.6, H], [17.4, H], [16 + half(0.8) * 0.32, 26], [16, 6], [16 - half(0.8) * 0.32, 26]], col);
  for (let k = 0; k < 6; k++) {
    const t = 0.12 + k * 0.12, y = H - 1 - (H - 6) * t, w = half(t) * (0.85 + 0.15 * Math.sin(k * 2.1));
    for (const side of [-1, 1]) poly(39, [[16, y - 9], [16 + side * w, y + 2.5], [16 + side * w * 0.35, y + 4], [16, y + 6]], col);
  }
  // the end lobe: a broad triangle
  poly(39, [[16, 2], [16 + 13, 22], [16 + 5, 28], [16 - 5, 28], [16 - 13, 22]], col);
  seg(39, 16, H - 1, 16, 8, 2.2, 0.8, [g * 1.1 * 255, g * 1.06 * 255, g * 0.95 * 255]);
  seg(39, 16, H - 1, 16, 100, 2.2, 2.0, [g * 1.12 * 255, g * 0.86 * 255, g * 0.86 * 255]);
}

// 40: dandelion scape: hollow, pale and smooth, a little pink at the foot
fill(40, (px, py) => {
  const f = 1 - (py + 0.5) / H, pink = Math.max(0, 1 - f / 0.15), g = 0.88 * (1 + 0.03 * Math.sin(px / COL * Math.PI * 2 * 3));
  return [Math.min(1, g * (0.98 + 0.12 * pink)) * 255, Math.min(1, g * (1 - 0.12 * pink)) * 255, Math.min(1, g * (0.84 + 0.04 * pink)) * 255];
});

// 41: dandelion parts (real colours). Left half rows 0-8: the head from above (row 0 its middle, deep yellow; the rim
// ragged, fine lines between the rays); rows 9-12 the bracts, green, ragged at row 12. Right half rows 0-15: the seed
// clock, a disc of fine pappus rays from a brown middle, wispy at the edge.
{
  const r = rng(0xDA41);
  for (let py = 0; py <= 8; py++) for (let px = 0; px < 16; px++) {
    if (py >= 7 && (px % 2 === (py === 8 ? 0 : 1)) && r() < 0.8) continue;
    const t = py / 8, line = px % 2 === 0 && py > 2 ? 0.93 : 1;
    add(41 * COL + px, py, (236 + 14 * t) * line, (164 + 52 * t) * line, (24 + 16 * t) * line, 1);
  }
  for (let py = 9; py <= 12; py++) for (let px = 0; px < 16; px++) {
    if (py === 12 && r() < 0.5) continue;
    const k = 0.9 + r() * 0.15;
    add(41 * COL + px, py, 92 * k, 122 * k, 58 * k, 1);
  }
  const cx = 24, cy = 7.8;
  for (let k = 0; k < 90; k++) {
    const a = (k / 90) * Math.PI * 2 + r() * 0.05, len = 6.2 + r() * 1.6;
    seg(41, cx + Math.cos(a) * 1.2, cy + Math.sin(a) * 1.2, cx + Math.cos(a) * len, cy + Math.sin(a) * len, 0.85, 0.7, [236 + r() * 12, 236 + r() * 12, 230 + r() * 10]);
  }
  blob(41, cx, cy, 2.6, 2.6, [228, 226, 218]);
  blob(41, cx, cy, 1.3, 1.3, [128, 104, 74]);
}

// 42: dandelion scape card (about 0.25 m over 128 px, 0.09 m over 32; real colours): pale scapes curving up from the
// middle, three yellow heads, one seed clock
{
  const r = rng(0xDA42);
  const tops = [[8, 30, 0], [22, 18, 0], [14, 46, 0], [25, 40, 1]];
  for (const [tx, ty, clock] of tops) {
    curve(42, [[16, H - 1], [16 + (tx - 16) * 0.3, H * 0.7], [tx, ty + 3]], 1.3, 1.1, [168, 186, 128]);
    if (clock) {
      for (let k = 0; k < 40; k++) {
        const a = k / 40 * Math.PI * 2;
        seg(42, tx, ty - 3, tx + Math.cos(a) * 3.6, ty - 3 + Math.sin(a) * 9, 0.8, 0.6, [238, 238, 232]);
      }
    } else {
      blob(42, tx, ty + 2, 2.6, 2.4, [104, 128, 62]);
      blob(42, tx, ty, 4.6, 2.8, [246, 204, 36]);
      blob(42, tx, ty - 0.6, 2.2, 1.2, [234, 168, 28]);
    }
  }
}

/** A lanceolate leaf laid across a parts column at the rows of its height (0..1 of the stem): base at the left. */
function bandLeaf(c, at, w, g, toothed) {
  const cy = H * (1 - at);
  const half = u => w / 2 * Math.pow(Math.sin(Math.PI * Math.min(1, u * 0.95 + 0.05)), 0.7) * (1 - 0.25 * u);
  const pts = [];
  const N = 30;
  for (let i = 0; i <= N; i++) { const u = i / N, n = toothed && i % 3 === 1 ? 0.7 : 0; pts.push([1 + 29.5 * u, cy - Math.max(0, half(u) - n)]); }
  for (let i = N; i >= 0; i--) { const u = i / N, n = toothed && i % 3 === 1 ? 0.7 : 0; pts.push([1 + 29.5 * u, cy + Math.max(0, half(u) - n)]); }
  poly(c, pts, [g * 0.95 * 255, g * 255, g * 0.9 * 255]);
  seg(c, 1, cy, 30, cy, 0.9, 0.5, [g * 1.08 * 0.95 * 255, g * 1.08 * 255, g * 1.08 * 0.9 * 255]);
}

/** A hairy stem (grey for the tint): faint ribs, light hairs, `streak` (f) -> 0..1 reddish streaks. */
function hairyStem(c, seed, g, streak) {
  const r = rng(seed);
  fill(c, (px, py) => {
    const f = 1 - (py + 0.5) / H, red = streak(f) * (Math.sin(px * 1.7 + py * 0.3) > 0.4 ? 1 : 0.3);
    const k = g * (1 + 0.03 * Math.sin(px / COL * Math.PI * 2 * 4)) * (r() < 0.07 ? 1.14 : 1);
    return [Math.min(1, k * (0.95 + 0.25 * red)) * 255, Math.min(1, k * (1 - 0.2 * red)) * 255, Math.min(1, k * (0.9 - 0.05 * red)) * 255];
  });
}

/** A ray floret in the right half of a parts column's rows 0-8: its foot at row 0 (narrow), its tip at row 8 (notched). */
function ray(c, col, vein, notch) {
  for (let py = 0; py <= 8; py++) for (let px = 16; px < 32; px++) {
    const t = py / 8, w = 4 + 3.4 * Math.min(1, t * 2.2), d = Math.abs(px + 0.5 - 24);
    if (d > w) continue;
    if (py === 8 && notch && d < 1.5) continue;
    const k = (px % 3 === 0 ? vein : 1) * (0.92 + 0.08 * t);
    add(c * COL + px, py, col[0] * k, col[1] * k, col[2] * k, 1);
  }
}

// 43: coneflower basal leaf: a long winged petiole to row 76, the ovate blade, coarse teeth, three veins from its base
broadLeaf(43, 3, 76, t => 15 * Math.pow(Math.sin(Math.PI * Math.min(1, Math.pow(t, 0.75) * 0.97 + 0.03)), 0.7), 4, 0.7,
  (a, t) => a < 0.07 || Math.abs(a - 0.45 * (1 - t)) < 0.05 ? 1 : 0, (t, side) => (Math.floor(t * 18 + (side > 0 ? 0.5 : 0)) % 2) * 1.2 * (t > 0.1 ? 1 : 0));

// 44: coneflower stem: hairy, reddish streaks low down
hairyStem(44, 0xC044, 0.78, f => Math.max(0, 1 - f / 0.4));

// 45: coneflower parts: the cone (left half rows 0-8: orange-brown, its tip at row 0 a little greener, stiff spiny
// bracts as dots), one ray (right half: pink-purple, darker veins, the foot narrow), the stem leaves (tinted)
{
  const r = rng(0xC045);
  for (let py = 0; py <= 8; py++) for (let px = 0; px < 16; px++) {
    const t = py / 8, k = 0.88 + r() * 0.2, spine = (px + py) % 3 === 0 ? 1.25 : 1;
    add(45 * COL + px, py, (150 + 40 * t) * k * spine, (100 - 20 * t) * k * spine, (40 - 10 * t) * k, 1);
  }
  ray(45, [212, 116, 172], 0.88, true);
  for (const at of [0.25, 0.45, 0.65]) bandLeaf(45, at, 8, 0.72, true);
}

// 46 / 47: a coneflower seen from the side (about 0.95 m over 128 px, 0.26 m over 32): basal leaves arching out low, two
// stems with their leaves (tinted); on the flower card the cones and their hanging pink rays (real colours)
{
  const r = rng(0xC046);
  for (let k = 0; k < 7; k++) {
    const side = k % 2 === 0 ? -1 : 1, reach = 5 + r() * 9, rise = 14 + r() * 16, pts = [];
    for (let i = 0; i <= 6; i++) { const t = i / 6; pts.push([16 + side * reach * t, H - 1 - rise * Math.sin(t * Math.PI * 0.7)]); }
    curve(46, pts, 1.2, 3.2, [0.72 * 0.95 * 255, 0.72 * 255, 0.72 * 0.9 * 255]);
  }
  for (const [sx, top] of [[11, 12], [21, 18]]) {
    curve(46, [[16, H - 1], [16 + (sx - 16) * 0.5, H * 0.55], [sx, top + 3]], 1.3, 1.0, [0.78 * 0.95 * 255, 0.78 * 255, 0.78 * 0.9 * 255]);
    for (const [t, dir] of [[0.3, -1], [0.5, 1], [0.7, -1]]) {
      const y = H - 1 - (H - 1 - top - 3) * t, x = 16 + (sx - 16) * t;
      curve(46, [[x, y], [x + dir * 3, y - 2], [x + dir * 5.5, y]], 1.8, 0.6, [0.74 * 0.95 * 255, 0.74 * 255, 0.74 * 0.9 * 255]);
    }
    blob(47, sx, top, 1.8, 2.2, [168, 92, 36]);
    for (let k = 0; k < 6; k++) {
      const a = -1.2 + k * 0.48;
      seg(47, sx + Math.sin(a) * 1.4, top + 2, sx + Math.sin(a) * 5.2, top + 2 + 3.5 + Math.abs(Math.cos(a)) * 1.5, 1.1, 0.8, [212, 116, 172]);
    }
  }
}

// 48: Susan basal leaf: lanceolate, widest a third of the way up, a short winged foot, hairy (ragged) edge, three veins
broadLeaf(48, 4, 120, t => 14.8 * Math.pow(Math.sin(Math.PI * Math.min(1, Math.pow(t, 0.62) * 0.97 + 0.03)), 0.8), 6, 0.76,
  (a, t) => a < 0.07 || Math.abs(a - 0.5 * (1 - 0.5 * t)) < 0.04 ? 1 : 0, (t, side) => (Math.sin(t * 90 + side * 2) > 0.6 ? 0.6 : 0));

// 49: Susan stem: bristly
hairyStem(49, 0x5A49, 0.8, f => 0.15 * Math.max(0, 1 - f / 0.3));

// 50: Susan parts: the dome (left half rows 0-8: dark brown-purple, tiny florets, a ring of yellow pollen at the rim),
// one golden ray (right half), the stem leaves (tinted)
{
  const r = rng(0x5A50);
  for (let py = 0; py <= 8; py++) for (let px = 0; px < 16; px++) {
    const k = 0.85 + r() * 0.3, pollen = py >= 6 && r() < 0.25;
    if (pollen) add(50 * COL + px, py, 214, 168, 46, 1);
    else add(50 * COL + px, py, 66 * k, 40 * k, 30 * k, 1);
  }
  ray(50, [246, 186, 26], 0.92, true);
  for (const at of [0.2, 0.4, 0.6]) bandLeaf(50, at, 7, 0.76, false);
}

// 51 / 52: Susans seen from the side (about 0.6 m over 128 px, 0.17 m over 32): a low rosette, three stems with leaves
// (tinted); on the flower card the dark domes and their golden rays spreading level (real colours)
{
  const r = rng(0x5A51);
  for (let k = 0; k < 8; k++) {
    const side = k % 2 === 0 ? -1 : 1, reach = 4 + r() * 10, rise = 8 + r() * 10, pts = [];
    for (let i = 0; i <= 5; i++) { const t = i / 5; pts.push([16 + side * reach * t, H - 1 - rise * Math.sin(t * Math.PI * 0.7)]); }
    curve(51, pts, 1.2, 2.6, [0.76 * 0.95 * 255, 0.76 * 255, 0.76 * 0.9 * 255]);
  }
  for (const [sx, top] of [[9, 14], [22, 10], [16, 28]]) {
    curve(51, [[16, H - 1], [16 + (sx - 16) * 0.5, H * 0.55], [sx, top + 1]], 1.0, 0.8, [0.8 * 0.95 * 255, 0.8 * 255, 0.8 * 0.9 * 255]);
    for (const [t, dir] of [[0.3, 1], [0.55, -1]]) {
      const y = H - 1 - (H - 1 - top - 1) * t, x = 16 + (sx - 16) * t;
      curve(51, [[x, y], [x + dir * 2.5, y - 2], [x + dir * 4.5, y - 1]], 1.6, 0.5, [0.76 * 0.95 * 255, 0.76 * 255, 0.76 * 0.9 * 255]);
    }
    for (const side of [-1, 1]) seg(52, sx + side * 1, top + 0.6, sx + side * 5.2, top + 2.4, 1.6, 1.0, [246, 186, 26]);
    seg(52, sx - 3, top + 1.5, sx + 3, top + 1.5, 1.4, 1.4, [240, 176, 24]);
    blob(52, sx, top, 1.5, 1.7, [62, 38, 30]);
  }
}

// ------------------------------------------------------------------------------ P2b batch 3 (53-63)

// 53: goldenrod stem: faint ribs, finely hairy (lighter flecks) above the middle
hairyStem(53, 0x601D53, 0.8, f => 0);

// 54: goldenrod parts. Rows 0-32: the raceme pattern (real colours): a thin green axis down the middle, the tiny heads
// crowded over most of the strip's width, some just opening (paler), a few gaps showing the green bracts. The leaves at
// 0.2-0.7: lanceolate (the teeth are too fine to draw at this size), tinted.
{
  const r = rng(0x601D54);
  for (let py = 0; py <= 32; py++) for (let px = 0; px < COL; px++) {
    const d = Math.abs(px + 0.5 - 16);
    if (d < 1.2) { add(54 * COL + px, py, 108, 126, 58, 1); continue; }
    if (d > 13.5 || r() < 0.08) continue;
    const k = 0.86 + r() * 0.22, pale = r() < 0.15;
    if (r() < 0.12) add(54 * COL + px, py, 112 * k, 130 * k, 60 * k, 1);
    else add(54 * COL + px, py, (pale ? 242 : 236) * k, (pale ? 214 : 186) * k, (pale ? 84 : 30) * k, 1);
  }
  for (const at of [0.2, 0.3, 0.4, 0.5, 0.6, 0.7]) bandLeaf(54, at, 8, 0.74, false);
}

// 55 / 56: goldenrod seen from the side (about 1.3 m over 128 px, 0.25 m over 32): three stems, their leaves ascending
// thickly up to three quarters of the height (tinted); on the flower card the panicles: arching yellow racemes crowded
// at the stems' tops, the lower ones longer (real colours)
{
  const r = rng(0x601D55);
  const stems = [[9, 4], [16, 0], [23, 7]];
  for (const [sx, top] of stems) {
    curve(55, [[16 + (sx - 16) * 0.3, H - 1], [sx, top + 34]], 0.8, 0.6, [0.8 * 0.95 * 255, 0.8 * 255, 0.8 * 0.9 * 255]);
    for (let y = H - 30; y > top + 34; y -= 4.5) {
      const dir = (Math.floor(y) % 2 === 0) ? 1 : -1, x = 16 + (sx - 16) * (0.3 + 0.7 * (H - 1 - y) / (H - 1 - top - 34));
      seg(55, x, y, x + dir * (3 + r() * 2), y - 4 - r() * 2, 0.9, 0.4, [0.74 * 0.95 * 255, 0.74 * 255, 0.74 * 0.9 * 255]);
    }
    curve(56, [[sx, top + 34], [sx, top]], 0.6, 0.5, [108, 126, 58]);
    for (let k = 0; k < 7; k++) {
      const y0 = top + 30 - k * 4, len = 9 - k, dir = k % 2 === 0 ? 1 : -1;
      curve(56, [[sx, y0], [sx + dir * len * 0.6, y0 - 3], [sx + dir * len, y0 + 1]], 1.3, 0.8, [236, 186, 30]);
    }
    blob(56, sx, top + 2, 1.2, 3, [236, 186, 30]);
  }
}

// 57: reed culm: pale and glaucous (grey for the tint), a darker node every 16 rows with the sheath's streaks above it
fill(57, (px, py) => {
  const node = (py % 16), dark = node < 1 ? 0.8 : node < 6 ? 0.94 : 1, g = 0.86 * dark * (1 + 0.04 * Math.sin(px / COL * Math.PI * 2 * 5));
  return [g * 0.95 * 255, g * 255, g * 0.92 * 255];
});

// 58: reed parts. Rows 0-18: the plume (real colours): from a narrow foot at row 18 out to the full width at row 0,
// branches spraying up and out, purple-brown with silver-buff silky hairs. The leaves at 0.2-0.8: broad at the base (left),
// tapering evenly to a long point (right), a pale midrib (tinted).
{
  const r = rng(0x9EED58);
  for (let k = 0; k < 70; k++) {
    const t = r(), y0 = 18 - t * 6, x0 = 16 + (r() - 0.5) * 4 * (1 - t);
    const a = (r() - 0.5) * 1.5, len = 8 + r() * 12;
    const x1 = x0 + Math.sin(a) * len * 1.1, y1 = Math.max(0.2, y0 - Math.cos(a) * len);
    const silk = r() < 0.35;
    seg(58, x0, y0, x1, y1, 0.75, 0.55, silk ? [196, 178, 164] : [118 + r() * 30, 78 + r() * 20, 92 + r() * 20]);
  }
  for (const at of [0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8]) {
    const cy = H * (1 - at), g = 0.78;
    poly(58, [[0.5, cy - 3.6], [6, cy - 3.4], [31.5, cy], [6, cy + 3.4], [0.5, cy + 3.6]], [g * 0.95 * 255, g * 255, g * 0.88 * 255]);
    seg(58, 0.5, cy, 29, cy, 0.6, 0.3, [g * 1.12 * 0.95 * 255, g * 1.12 * 255, g * 1.12 * 0.9 * 255]);
  }
}

// 59 / 60: a group of reeds from the side (about 2.7 m over 128 px, 0.6 m over 32): four culms, their leaves standing out
// alternately and drooping (tinted); on the plume card the nodding plumes at their tops (real colours)
{
  const r = rng(0x9EED59);
  for (const [bx, tx, top] of [[13, 9, 6], [15, 16, 2], [18, 22, 9], [17, 26, 14]]) {
    curve(59, [[bx, H - 1], [tx, top + 14]], 0.7, 0.55, [0.86 * 0.95 * 255, 0.86 * 255, 0.86 * 0.92 * 255]);
    for (let k = 0; k < 7; k++) {
      const f = 0.2 + k * 0.1, y = H - 1 - (H - 1 - top - 14) * f, x = bx + (tx - bx) * f, dir = k % 2 === 0 ? 1 : -1, l = 7 + r() * 4;
      curve(59, [[x, y], [x + dir * l * 0.5, y - 3], [x + dir * l, y + 1.5]], 1.4, 0.3, [0.78 * 0.95 * 255, 0.78 * 255, 0.78 * 0.88 * 255]);
    }
    for (let k = 0; k < 16; k++) {
      const a = (r() - 0.5) * 1.2 + 0.4;
      seg(60, tx, top + 14, tx + Math.sin(a) * 6, top + 14 - Math.cos(a) * 13, 0.8, 0.5, r() < 0.3 ? [196, 178, 164] : [124, 82, 96]);
    }
  }
}

// 61: blackberry cane (real colours): green flushed red-purple, more so on one side, faint ridges, hooked prickles
{
  const r = rng(0xB1AC61);
  fill(61, (px, py) => {
    const red = 0.45 + 0.4 * Math.sin(px / COL * Math.PI * 2), ridge = px % 8 === 0 ? 0.88 : 1;
    return [(96 + 60 * red) * ridge, (108 - 50 * red) * ridge, (56 + 10 * red) * ridge];
  });
  for (let k = 0; k < 60; k++) blob(61, r() * COL, r() * H, 0.7, 0.9, [176, 92, 84]);
}

// 62: blackberry parts: twelve bands, each a leaflet (foot at the left, ovate, a drawn-out tip, toothed, pinnate veins;
// grey for the tint) and a cluster of drupes in the right quarter (real colours: black and glossy, a few red; a green
// stalk at the band's top)
{
  const r = rng(0xB1AC62);
  for (let k = 0; k < 12; k++) {
    const cy = 5 + 10.5 * k, g = 0.68, L = 22;
    const pts = [];
    const half = u => 4.3 * Math.pow(Math.sin(Math.PI * Math.min(1, Math.pow(u, 0.8) * 0.97 + 0.03)), 0.85) * (u > 0.75 ? 1 - (u - 0.75) * 1.6 : 1);
    for (let i = 0; i <= 30; i++) { const u = i / 30, n = i % 2 === 1 && i > 3 ? 0.5 : 0; pts.push([0.5 + L * u, cy - Math.max(0, half(u) - n)]); }
    for (let i = 30; i >= 0; i--) { const u = i / 30, n = i % 2 === 1 && i > 3 ? 0.5 : 0; pts.push([0.5 + L * u, cy + Math.max(0, half(u) - n)]); }
    poly(62, pts, (x, y) => {
      const u = (x - 0.5) / L, d = (y - cy) / Math.max(0.5, half(u)), vein = Math.abs(((u * 7 + Math.abs(d) * 0.8) % 1) - 0.5) < 0.08 ? 1.08 : 1;
      return [g * 0.95 * vein * 255, g * vein * 255, g * 0.88 * vein * 255];
    });
    seg(62, 0.5, cy, L, cy, 0.5, 0.25, [g * 1.12 * 0.95 * 255, g * 1.12 * 255, g * 1.12 * 0.9 * 255]);
    const bx = 27.5;
    seg(62, bx, cy - 4.4, bx, cy - 2, 0.4, 0.4, [96, 118, 56]);
    for (let d = 0; d < 8; d++) {
      const x = bx + (r() - 0.5) * 6, y = cy - 1 + r() * 5, red = r() < 0.2;
      blob(62, x, y, 1.2, 1.2, red ? [168, 34, 44] : [34, 24, 38]);
      add(62 * COL + Math.floor(x - 0.4), Math.floor(y - 0.4), red ? 220 : 120, red ? 120 : 110, red ? 120 : 140, 0.5);
    }
  }
}

// 63: a bramble from the side (about 1.3 m over 128 px, 0.85 m over 32): canes rising and arching out over a mound of
// leaves (tinted: the canes darker), dark fruit hanging on the arches
{
  const r = rng(0xB1AC63);
  for (let k = 0; k < 6; k++) {
    const dir = k % 2 === 0 ? 1 : -1, reach = 8 + r() * 7, top = 10 + r() * 30;
    const pts = [[16, H - 1], [16 + dir * reach * 0.2, top + 10], [16 + dir * reach * 0.7, top], [16 + dir * reach, top + 25 + r() * 25]];
    curve(63, pts, 0.8, 0.5, [0.5 * 255, 0.42 * 255, 0.4 * 255]);
    for (let i = 1; i < 9; i++) {
      const t = i / 9, a = pts[Math.min(3, Math.floor(t * 3))], b = pts[Math.min(3, Math.floor(t * 3) + 1)], f = t * 3 - Math.floor(t * 3);
      const x = a[0] + (b[0] - a[0]) * f, y = a[1] + (b[1] - a[1]) * f;
      for (let l = 0; l < 3; l++) blob(63, x + (r() - 0.5) * 5, y + (r() - 0.5) * 4, 1.6, 1.1, [0.7 * 0.95 * 255, 0.7 * 255, 0.7 * 0.88 * 255]);
      if (t > 0.6 && r() < 0.4) blob(63, x, y + 2, 1, 1, [40, 30, 40]);
    }
  }
  for (let k = 0; k < 40; k++) blob(63, 16 + (r() - 0.5) * 22, H - 1 - r() * 50, 1.8, 1.2, [0.66 * 0.95 * 255, 0.66 * 255, 0.66 * 0.88 * 255]);
}

// ------------------------------------------------------------------------------ P4a batch 1 (64-77)

/** A point on a polyline at share t, with its direction (radians). */
function along(pts, t) {
  const s = Math.max(0, Math.min(0.9999, t)) * (pts.length - 1), i = Math.floor(s), u = s - i;
  return [pts[i][0] + (pts[i + 1][0] - pts[i][0]) * u, pts[i][1] + (pts[i + 1][1] - pts[i][1]) * u,
    Math.atan2(pts[i + 1][1] - pts[i][1], pts[i + 1][0] - pts[i][0])];
}

/** A long grass leaf across a band of a parts column: its foot at the left, tapering evenly to a point at the right. */
function grassBand(c, at, half, g, foot = 5) {
  const cy = H * (1 - at);
  poly(c, [[0.5, cy - half], [foot, cy - half], [31.5, cy], [foot, cy + half], [0.5, cy + half]], [g * 0.95 * 255, g * 255, g * 0.9 * 255]);
  seg(c, 0.5, cy, 29, cy, 0.6, 0.3, [Math.min(255, g * 1.1 * 0.95 * 255), Math.min(255, g * 1.1 * 255), Math.min(255, g * 1.1 * 0.9 * 255)]);
}

/**
 * An open grass panicle in column c from its foot (x, y0) up to y1 (rows, y down): branches from the axis spreading up and
 * out, longest low down (a pyramid `spread` px either side at the foot), each with fine branchlets and spikelets at their
 * ends (real colours).
 */
function panicle(c, x, y0, y1, spread, r, colours) {
  const axis = [150, 128, 104];
  seg(c, x, y0, x, y1, 0.9, 0.7, axis);
  const whorls = Math.round((y0 - y1) / 3.2);
  for (let k = 0; k < whorls; k++) {
    const t = (k + 0.5) / whorls, y = y0 - (y0 - y1) * t, reach = spread * Math.pow(1 - t, 0.85) + 1.2;
    for (const side of [-1, 1]) {
      if (r() < 0.25) continue;
      const a = (0.45 + r() * 0.5) * side, len = reach / Math.max(0.35, Math.abs(Math.sin(a))) * (0.75 + r() * 0.3);
      const ex = x + Math.sin(a) * len, ey = y - Math.cos(a) * len;
      seg(c, x, y, ex, ey, 0.8, 0.7, axis);
      for (let j = 1; j <= 3; j++) {
        const s = 0.35 + 0.22 * j, bx = x + (ex - x) * s, by = y + (ey - y) * s, b = a + (r() - 0.5) * 0.9, bl = 2 + r() * 3;
        const fx = bx + Math.sin(b) * bl, fy = by - Math.cos(b) * bl;
        seg(c, bx, by, fx, fy, 0.75, 0.7, axis);
        blob(c, fx, fy, 0.8, 1.1, colours[Math.floor(r() * colours.length)]);
      }
      blob(c, ex, ey, 0.8, 1.1, colours[Math.floor(r() * colours.length)]);
    }
  }
}

// 64: switchgrass leaf, grey for the tint: a little glaucous (blue), a pale midrib, fine veins, a drier tip
{
  const r = rng(0x5A6A64);
  const streak = Array.from({length: COL}, () => 0.97 + r() * 0.06);
  fill(64, (px, py) => {
    const f = 1 - (py + 0.5) / H, a = across(px);
    let g = (0.8 + 0.1 * Math.min(1, f * 1.6)) * streak[px];
    if (a < 0.09) g *= 1.1;
    else if (px % 3 === 0) g *= 1.02;
    if (a > 0.88) g *= 0.9;
    const foot = Math.max(0, 1 - f / 0.08), dry = Math.max(0, (f - 0.9) / 0.1);
    return [Math.min(1, g * (0.9 + 0.08 * foot + 0.14 * dry)) * 255, Math.min(1, g * (1 - 0.04 * foot)) * 255,
      Math.min(1, g * (1.08 - 0.06 * foot - 0.2 * dry)) * 255];
  });
}

// 65: switchgrass culm, grey for the tint: smooth, faint ribs, darker nodes, a reddish flush under the panicle
{
  const nodes = [0.18, 0.36, 0.52, 0.66];
  fill(65, (px, py) => {
    const f = 1 - (py + 0.5) / H;
    let g = 0.82 * (1 + 0.03 * Math.sin(px / COL * Math.PI * 2 * 5));
    const red = Math.max(0, (f - 0.55) / 0.15) * 0.3;
    for (const n of nodes) {
      const d = Math.abs(f - n) * H;
      if (d < 1.2) g *= 0.8; else if (d < 2.5) g *= 1.04;
    }
    return [Math.min(1, g * (0.92 + 0.14 * Math.min(0.3, red))) * 255, Math.min(1, g * (1 - 0.08 * Math.min(0.3, red))) * 255,
      Math.min(1, g * 1.04) * 255];
  });
}

// 66: switchgrass parts. Rows 0-38: the panicle (real colours), its foot at row 38 (0.7 of the culm) across the full width
// and its tip at row 0 narrow: a loose pyramid of hair-fine branches with tiny purple-red and tan spikelets at their ends
// (the panicle quads are trapezoids mapped the same way). The three culm leaves across the rows of their heights (0.2,
// 0.35, 0.5; tinted).
{
  const r = rng(0x5A6A66);
  panicle(66, 16, 38, 0.8, 14.5, r, [[138, 86, 98], [160, 104, 108], [176, 150, 118], [150, 132, 104]]);
  for (const at of [0.2, 0.35, 0.5]) grassBand(66, at, 3.2, 0.8, 6);
}

// 67 / 68: a switchgrass clump from the side (about 1.5 m over 128 px, 0.4 m over 32): a vase of upright leaves arching
// at their tips to about half the height, four culms with their leaves (tinted); on the panicle card the airy panicles
// at their tops (real colours)
{
  const r = rng(0x5A6A67);
  for (let k = 0; k < 24; k++) {
    const side = k % 2 === 0 ? -1 : 1, reach = 2 + r() * 13, rise = 44 + r() * 34, pts = [];
    for (let i = 0; i <= 8; i++) {
      const t = i / 8;
      pts.push([16 + side * (0.5 + reach * Math.pow(t, 1.6)), H - 1 - rise * Math.sin(t * Math.PI * 0.55) + Math.max(0, t - 0.75) * 14]);
    }
    curve(67, pts, 2.0, 0.4, [0.8 * 0.92 * 255, (0.78 + r() * 0.1) * 255, 0.8 * 1.0 * 255]);
  }
  for (const [sx, top] of [[9, 12], [14, 4], [19, 8], [24, 16]]) {
    const foot = 16 + (sx - 16) * 0.2;
    curve(67, [[foot, H - 1], [16 + (sx - 16) * 0.6, H * 0.55], [sx, top + 34]], 0.9, 0.7, [0.82 * 0.92 * 255, 0.82 * 255, 0.82 * 255]);
    for (const [t, dir] of [[0.25, -1], [0.45, 1], [0.62, -1]]) {
      const y = H - 1 - (H - 1 - top - 34) * t, x = foot + (sx - foot) * t;
      curve(67, [[x, y], [x + dir * 3, y - 6], [x + dir * 6, y - 8], [x + dir * 8, y - 5]], 1.1, 0.3, [0.8 * 0.92 * 255, 0.8 * 255, 0.8 * 255]);
    }
    panicle(68, sx, top + 34, top, 5.5, r, [[138, 86, 98], [176, 150, 118], [150, 132, 104]]);
  }
}

// 69: smooth cordgrass culm, grey for the tint: stout and smooth, the leaf sheaths overlapping (a fine line every 14 rows,
// lighter above it), tan to reddish at the foot
fill(69, (px, py) => {
  const f = 1 - (py + 0.5) / H, s = (py % 14) / 14;
  let g = 0.86 * (0.94 + 0.08 * s) * (1 + 0.025 * Math.sin(px / COL * Math.PI * 2 * 4));
  if (py % 14 < 1) g *= 0.84;
  const foot = Math.max(0, 1 - f / 0.12);
  return [Math.min(1, g * (0.95 + 0.2 * foot)) * 255, Math.min(1, g * (1 - 0.1 * foot)) * 255, Math.min(1, g * (0.88 - 0.08 * foot)) * 255];
});

// 70: cordgrass parts. Rows 0-23: the flowering head (real colours): from its foot at row 23 (0.82 of the culm) a narrow
// spike-like panicle of 9-12 spikes pressed close to the axis, each a double row of overlapping straw-green spikelets
// with yellow anthers. The five leaves across the rows of their heights (0.12-0.6; flat, broad at the base, drawn out to
// an inrolled point; tinted).
{
  const r = rng(0x5BA770);
  seg(70, 16, 23.4, 16, 0.6, 1.0, 0.7, [150, 150, 100]);
  const spikes = 9 + Math.floor(r() * 4);
  for (let k = 0; k < spikes; k++) {
    const t = k / spikes, y = 22 - 20 * t, side = k % 2 === 0 ? 1 : -1, len = 7 + r() * 5;
    const a = side * (0.1 + r() * 0.15), x0 = 16 + side * 0.8, ex = x0 + Math.sin(a) * len + side * (3 + r() * 6), ey = Math.max(0.5, y - Math.cos(a) * len);
    seg(70, x0, y, ex, ey, 1.0, 0.8, [140, 146, 92]);
    for (let j = 0; j < 7; j++) {
      const s = (j + 0.5) / 7, bx = x0 + (ex - x0) * s, by = y + (ey - y) * s;
      blob(70, bx + (j % 2 === 0 ? 1 : -1) * 0.9, by, 1.1, 0.9, r() < 0.5 ? [178, 176, 112] : [154, 164, 98]);
      if (r() < 0.3) blob(70, bx + (j % 2 === 0 ? 2 : -2), by, 0.6, 0.6, [222, 196, 96]);
    }
  }
  for (const at of [0.12, 0.24, 0.36, 0.48, 0.6]) grassBand(70, at, 3.4, 0.82, 4);
}

// 71: a stand of cordgrass from the side (about 1.4 m over 128 px, 0.6 m over 32): upright shoots, stiff leaves rising
// alternately off them, the narrow heads at their tops (tinted, the heads lighter)
{
  const r = rng(0x5BA771);
  for (let k = 0; k < 11; k++) {
    const bx = 2.5 + k * 2.7 + (r() - 0.5) * 1.5, top = 6 + r() * 40, tx = bx + (r() - 0.5) * 3;
    curve(71, [[bx, H - 1], [tx, top]], 1.0, 0.7, [0.86 * 0.95 * 255, 0.86 * 255, 0.86 * 0.88 * 255]);
    for (let l = 0; l < 5; l++) {
      const f = 0.12 + l * 0.12, y = H - 1 - (H - 1 - top) * f, x = bx + (tx - bx) * f, dir = l % 2 === 0 ? 1 : -1, len = 9 + r() * 6;
      curve(71, [[x, y], [x + dir * len * 0.35, y - len * 0.7], [x + dir * len * 0.6, y - len * 0.95]], 1.3, 0.3, [0.8 * 0.95 * 255, 0.8 * 255, 0.8 * 0.88 * 255]);
    }
    if (r() < 0.6) seg(71, tx, top, tx + (r() - 0.5) * 2, top - 14, 1.6, 0.9, [0.96 * 0.98 * 255, 0.96 * 255, 0.96 * 0.8 * 255]);
  }
}

/**
 * A once-pinnate fern frond in column c (cut out): the stipe from the bottom row up to row `blade`, then the blade to row
 * 1 along the rachis in the middle, `pairs` pairs of alternate pinnae nearly at right angles, each an oblong pinna len(t)
 * px long curving a little towards the tip, with an ear on its upper side at the base (Polystichum); grey g, the pinnae
 * from share `small` up smaller and darker (a fertile frond's sori), scales on the stipe.
 */
function oncePinnate(c, blade, pairs, len, g, r, small = 2) {
  const col = [g * 0.92 * 255, g * 255, g * 0.84 * 255], dark = [g * 0.8 * 255, g * 0.82 * 255, g * 0.62 * 255];
  seg(c, 16, H - 0.5, 16, blade, 1.9, 1.5, [g * 0.84 * 255, g * 0.82 * 255, g * 0.64 * 255]);
  for (let y = H - 2; y > blade; y -= 3 + r() * 3) blob(c, 16 + (r() - 0.5) * 1.6, y, 0.7, 1.2, [g * 0.7 * 255, g * 0.58 * 255, g * 0.42 * 255]);
  seg(c, 16, blade + 0.5, 16, 1, 1.4, 0.6, col);
  for (let k = 0; k < pairs * 2; k++) {
    const side = k % 2 === 0 ? -1 : 1, t = (k + 0.5) / (pairs * 2), y = blade - (blade - 1.5) * t;
    const fertile = t >= small, L = len(t) * (fertile ? 0.6 : 1) * (0.92 + r() * 0.16);
    if (L < 1) continue;
    const th = (78 - 14 * t) * Math.PI / 180, dx = side * Math.sin(th), dy = -Math.cos(th);
    const nx = side > 0 ? dy : -dy, ny = side > 0 ? -dx : dx;   // across the pinna, towards the frond's tip (up)
    const w = (fertile ? 1.0 : 1.5) * Math.min(1, 0.6 + L / 10), P = (s, q) => [16 + dx * s + nx * q + side * 0.6, y + dy * s + ny * q - s * s * 0.012];
    const pts = [P(0, -w * 0.9), P(L * 0.6, -w * 0.85), P(L * 0.92, -w * 0.5), P(L, 0), P(L * 0.92, w * 0.6), P(L * 0.4, w),
      P(fertile ? L * 0.15 : L * 0.2, fertile ? w * 1.3 : w * 1.9), P(L * 0.06, w * 1.2), P(0, w * 0.9)];
    poly(c, pts, fertile ? dark : col);
    seg(c, ...P(0, 0), ...P(L * 0.85, 0), 0.5, 0.4, [Math.min(255, g * 1.08 * 0.92 * 255), Math.min(255, g * 1.08 * 255), Math.min(255, g * 1.08 * 0.84 * 255)]);
  }
}

/** A Christmas fern's pinna length along the blade: a little shorter at the foot, even, tapering over the last third. */
const christmasPinna = L => t => L * (0.82 + 0.18 * Math.min(1, t / 0.2)) * (t < 0.62 ? 1 : Math.pow((1 - t) / 0.38, 0.75));

// 72: Christmas fern frond (tinted, cut out): the scaly stipe up to row 100, the blade above it: 24 pairs of oblong pinnae,
// each with its ear (the "stocking"), narrowing to the tip; dark, leathery
{
  const r = rng(0xC4F72);
  oncePinnate(72, 100, 24, christmasPinna(13.5), 0.62, r);
}

// 73: a fertile Christmas fern frond: as 72, the pinnae of its upper part smaller, narrower and darker (the sori beneath)
{
  const r = rng(0xC4F73);
  oncePinnate(73, 96, 24, christmasPinna(13), 0.6, r, 0.58);
}

// 74: a Christmas fern from the side (about 0.45 m over 128 px, 0.27 m over 32): a fountain of fronds arching out from a
// crown, a few lying out flat at the foot (tinted, dark)
{
  const r = rng(0xC4F74);
  for (let k = 0; k < 12; k++) {
    const side = k % 2 === 0 ? -1 : 1, reach = 4 + r() * 11, rise = k < 2 ? 10 + r() * 10 : 50 + r() * 70, pts = [];
    for (let i = 0; i <= 10; i++) {
      const t = i / 10;
      pts.push([16 + side * (0.5 + reach * Math.pow(t, 1.3)), H - 1 - rise * Math.sin(t * Math.PI * 0.6) + Math.max(0, t - 0.7) * rise * 0.5]);
    }
    const g = 0.6 + r() * 0.06, col = [g * 0.92 * 255, g * 255, g * 0.84 * 255];
    curve(74, pts, 0.9, 0.5, col);
    for (let j = 2; j < 18; j++) {
      const [x, y, d] = along(pts, j / 18), l = 2.6 * Math.sin(Math.PI * j / 18) + 0.6;
      for (const s of [-1, 1]) seg(74, x, y, x + Math.cos(d + s * 1.35) * l, y + Math.sin(d + s * 1.35) * l, 1.1, 0.7, col);
    }
  }
}

// 75: cinnamon fern frond (tinted, cut out): the stipe up to row 100, woolly at the foot; the blade above it lanceolate
// (widest a little below the middle), 20 pairs of pinnae nearly opposite, each deeply cut into rounded lobes, a tuft of
// cinnamon wool at each pinna's base; fresh yellow-green
{
  const r = rng(0xC1A75), g = 0.84, col = [g * 0.95 * 255, g * 255, g * 0.82 * 255];
  seg(75, 16, H - 0.5, 16, 100, 1.9, 1.4, [g * 0.92 * 255, g * 0.96 * 255, g * 0.8 * 255]);
  for (let y = H - 2; y > 108; y -= 1.5 + r() * 1.5) blob(75, 16 + (r() - 0.5) * 2.4, y, 0.9, 0.9, [g * 1.05 * 255, g * 0.88 * 255, g * 0.62 * 255]);
  seg(75, 16, 100.5, 16, 1, 1.3, 0.6, col);
  const pairs = 20;
  for (let k = 0; k < pairs; k++) {
    const t = (k + 0.5) / pairs, y = 100 - 98.5 * t, L = 15 * Math.pow(Math.sin(Math.PI * Math.min(1, t * 0.95 + 0.06)), 0.65) * (0.92 + r() * 0.12);
    for (const side of [-1, 1]) {
      if (L < 1.2) continue;
      const yy = y + (side > 0 ? 0.8 : 0), th = (70 - 10 * t) * Math.PI / 180, dx = side * Math.sin(th), dy = -Math.cos(th);
      seg(75, 16, yy, 16 + dx * L, yy + dy * L, 1.1, 0.6, col);
      const lobes = Math.max(2, Math.round(L / 1.7));
      for (let l = 0; l < lobes; l++) {
        const s = (l + 0.6) / (lobes + 0.4), bx = 16 + dx * L * s, by = yy + dy * L * s, rw = 0.85 * (1 - 0.5 * s) + 0.3;
        // a lobe either side of the pinna's axis, rounded
        for (const q of [-1, 1]) blob(75, bx - dy * q * rw * 0.75, by + dx * q * rw * 0.75, rw, rw * 0.85, col);
      }
      blob(75, 16 + dx * 1.2, yy + dy * 1.2, 0.8, 0.6, [g * 1.08 * 255, g * 0.84 * 255, g * 0.58 * 255]);
    }
  }
}

// 76: a fertile cinnamon fern frond (real colours, cut out): a stiff stalk, green low down turning cinnamon; on its upper
// 55 % short erect fertile pinnae packed with round sporangia, a narrow cinnamon-brown club
{
  const r = rng(0xC1A76);
  seg(76, 16, H - 0.5, 16, 1, 1.8, 1.0, t => t < 0.4 ? [110, 118, 70] : [138, 98, 56]);
  for (let k = 0; k < 26; k++) {
    const t = k / 26, y = 70 - 68 * t, half = 6 * Math.pow(1 - t, 0.5) + 1.5;
    for (const side of [-1, 1]) {
      const ex = 16 + side * half, ey = y - 2.2;
      seg(76, 16, y, ex, ey, 0.9, 0.7, [124, 84, 48]);
      for (let j = 0; j < 3; j++) {
        const s = (j + 0.6) / 3, bx = 16 + (ex - 16) * s, by = y + (ey - y) * s;
        blob(76, bx, by - 0.5, 1.1 + r() * 0.3, 1.1, r() < 0.5 ? [152, 90, 48] : [178, 112, 60]);
      }
    }
  }
}

// 77: a cinnamon fern from the side (about 1.0 m over 128 px, 0.4 m over 32): a vase of large fronds rising and arching out
// at their tips (tinted)
{
  const r = rng(0xC1A77);
  for (let k = 0; k < 10; k++) {
    const side = k % 2 === 0 ? -1 : 1, reach = 4 + r() * 11, rise = 80 + r() * 44, pts = [];
    for (let i = 0; i <= 10; i++) {
      const t = i / 10;
      pts.push([16 + side * (0.8 + reach * Math.pow(t, 1.4)), H - 1 - rise * Math.sin(t * Math.PI * 0.55) + Math.max(0, t - 0.75) * 30]);
    }
    const g = 0.82 + r() * 0.06, col = [g * 0.95 * 255, g * 255, g * 0.82 * 255];
    curve(77, pts, 1.0, 0.5, col);
    for (let j = 3; j < 20; j++) {
      const [x, y, d] = along(pts, j / 20), l = 4.2 * Math.pow(Math.sin(Math.PI * Math.min(1, (j / 20) * 0.95 + 0.05)), 0.7) + 0.6;
      for (const s of [-1, 1]) seg(77, x, y, x + Math.cos(d + s * 1.25) * l, y + Math.sin(d + s * 1.25) * l, 1.6, 0.9, col);
    }
  }
}

// ------------------------------------------------------------------------------ P4a batch 2 (78-92)

/** Speckle: a deterministic 0..1 per pixel (the felt, the gloss). */
const speck = (seed, x, y) => { const s = Math.sin(x * 12.9898 + y * 78.233 + seed * 37.719) * 43758.5453; return s - Math.floor(s); };

/**
 * A leaf across a parts column at the rows of its height (0..1 of the stem), its base at the left: half(u) the half width
 * in rows (u 0 the base .. 1 the tip), notch(u, side) rows cut off its edge (side -1 the upper edge, 1 the lower), colour
 * (u, a, x, y) -> [r, g, b] (a: -1 the upper edge .. 1 the lower).
 */
function leafBand(c, at, half, notch, colour) {
  const cy = H * (1 - at), pts = [], N = 60;
  for (let i = 0; i <= N; i++) { const u = i / N; pts.push([1 + 29.5 * u, cy - Math.max(0, half(u) - notch(u, -1))]); }
  for (let i = N; i >= 0; i--) { const u = i / N; pts.push([1 + 29.5 * u, cy + Math.max(0, half(u) - notch(u, 1))]); }
  poly(c, pts, (x, y) => {
    const u = Math.max(0, Math.min(1, (x - 1) / 29.5)), w = Math.max(0.3, half(u));
    return colour(u, (y - cy) / w, x, y);
  });
}

/** Pinnate veins on a band leaf: 1 on the midrib, 0.6 on the side veins (angled out towards the tip), else 0. */
const pinnate = (u, a, spacing, slope) => Math.abs(a) < 0.12 ? 1 : (((u * 30 - Math.abs(a) * slope) / spacing) % 1 + 1) % 1 < 0.13 && u > 0.05 ? 0.6 : 0;

// 78: mullein rosette leaf (real colours, cut out): obovate-oblong, broadest a little above the middle, narrowing to a
// winged base at the foot, the tip blunt; grey-green felt (speckled), a broad pale midrib, a net of faint veins
{
  const half = t => 15 * Math.pow(Math.sin(Math.PI * Math.min(1, 0.06 + 0.94 * Math.pow(t, 0.85))), 0.55) * (0.32 + 0.68 * Math.min(1, t / 0.55));
  const top = 4, base = H - 1, pts = [], N = 60;
  for (let i = 0; i <= N; i++) { const t = i / N; pts.push([16 - half(t), base - (base - top) * t]); }
  for (let i = N; i >= 0; i--) { const t = i / N; pts.push([16 + half(t), base - (base - top) * t]); }
  poly(78, pts, (x, y) => {
    const t = (base - y) / (base - top), a = (x - 16) / Math.max(0.5, half(Math.max(0, Math.min(1, t))));
    const rib = Math.abs(a) < 0.1 ? 1 : 0, vein = (((t * 9 - Math.abs(a) * 0.9) % 1) + 1) % 1 < 0.08 && t > 0.08 ? 1 : 0;
    const k = (0.94 + 0.12 * speck(78, x, y)) * (1 + 0.1 * rib + 0.04 * vein) * (Math.abs(a) > 0.92 ? 1.05 : 1);
    return [150 * k, 160 * k, 118 * k];
  });
}

// 79: mullein stalk (real colours): grey-green felt, faint lengthwise wings where the leaf bases run down it
fill(79, (px, py) => {
  const k = (0.94 + 0.12 * speck(79, px, py)) * (1 + 0.05 * Math.sin(px / COL * Math.PI * 2 * 3));
  return [146 * k, 158 * k, 116 * k];
});

// 80: mullein spike at the stalk's heights (rows 0-80; real colours): the tip tight woolly buds (rows 0-14), a band of
// open yellow flowers (14-42, thinning below), then green-brown capsules among the felt; below row 80 the stalk's felt
fill(80, (px, py) => {
  const s = speck(80, px, py), k = 0.92 + 0.14 * s;
  if (py > 80) return [146 * k, 158 * k, 116 * k];
  if (py >= 42) return s < 0.45 ? [122 * k, 118 * k, 78 * k] : [140 * k, 150 * k, 106 * k];
  return [156 * k, 166 * k, 122 * k];
});
{
  const r = rng(0x3E80);
  for (let k = 0; k < 150; k++) {
    const y = 13 + Math.pow(r(), 1.4) * 34, x = r() * COL;
    if (y > 30 && r() < (y - 30) / 20) continue;
    blob(80, x, y, 1.5, 1.1, [240, 204, 58]);
    blob(80, x, y, 0.5, 0.5, [214, 150, 40]);
  }
  for (let k = 0; k < 40; k++) blob(80, r() * COL, 2 + r() * 12, 1.0, 0.8, [128, 146, 92]);
}

// 81: mullein parts (real colours): the four stem leaf bands (0.08-0.38), oblong, pointed, felted
for (const at of [0.08, 0.18, 0.28, 0.38])
  leafBand(81, at, u => 4.4 * Math.pow(Math.sin(Math.PI * Math.min(1, 0.08 + 0.92 * u)), 0.6), () => 0,
    (u, a, x, y) => { const k = (0.94 + 0.12 * speck(81, x, y)) * (Math.abs(a) < 0.15 ? 1.1 : 1); return [150 * k, 160 * k, 118 * k]; });

// 82: mullein stalk card (about 1.8 m over 128 px, 0.4 m over 32; real colours): the felted stalk, its stem leaves rising
// out, smaller up it, and the thick spike on its upper part with the yellow band
{
  const r = rng(0x3E82), felt = [146, 158, 116];
  seg(82, 16, H - 1, 16.4, 4, 2.6, 1.8, felt);
  for (let k = 0; k < 8; k++) {
    const t = k / 7, y = H - 6 - t * 50, side = k % 2 ? 1 : -1, len = 13 - 8 * t;
    curve(82, [[16, y], [16 + side * len * 0.5, y - len * 0.45], [16 + side * len, y - len * 0.5]], 3.4 - 1.4 * t, 0.9, felt);
  }
  for (let y = 4; y < 62; y += 0.6) {
    const w = 3.6 * (y < 8 ? 0.6 + 0.4 * (y - 4) / 4 : 1);
    blob(82, 16.3 + (r() - 0.5) * 1.2, y, w, 0.8, y > 40 ? (r() < 0.5 ? [122, 118, 78] : [140, 150, 106]) : [154, 164, 120]);
    if (y > 12 && y < 40 && r() < 0.55) blob(82, 16.3 + (r() - 0.5) * 6, y, 1.2, 0.8, [240, 204, 58]);
  }
}

// 83: wild mint stem (tinted): square, finely hairy, flushed purple low down
hairyStem(83, 0x3183, 0.78, f => 0.25 * Math.max(0, 1 - f / 0.5));

// 84: mint parts. Rows 0-9 the flower whorl (real colours): a ring of tiny pale lilac flowers round the stem, green calyx
// specks between them. The five leaf bands (tinted): ovate, toothed, the veins sunk (darker), short-stalked.
{
  const r = rng(0x3184);
  for (let k = 0; k < 90; k++) {
    const x = 2 + r() * 28, y = 1 + r() * 8, d = Math.abs(x - 16) / 14;
    if (r() < d * 0.6) continue;
    blob(84, x, y, 1.1, 0.9, r() < 0.2 ? [110, 132, 80] : [218 + r() * 14, 188 + r() * 14, 230]);
  }
  for (const at of [0.2, 0.36, 0.52, 0.68, 0.84])
    leafBand(84, at, u => u < 0.1 ? 1.2 : 4.4 * Math.pow(Math.sin(Math.PI * Math.min(1, 0.04 + 0.96 * Math.pow((u - 0.1) / 0.9, 0.75))), 0.75),
      (u, side) => u > 0.2 && u < 0.95 && Math.floor(u * 26) % 2 ? 0.7 : 0,
      (u, a, x, y) => { const g = 0.74 * (1 - 0.12 * pinnate(u, a, 5, 3)); return [g * 0.95 * 255, g * 255, g * 0.9 * 255]; });
}

// 85: mint card (about 0.6 m over 128 px, 0.3 m over 32; tinted): stems leaning out of the patch, pairs of leaves
{
  const r = rng(0x3185), g = 0.74, col = [g * 0.95 * 255, g * 255, g * 0.9 * 255];
  for (let k = 0; k < 6; k++) {
    const x0 = 4 + r() * 24, lean = (r() - 0.5) * 10, top = 6 + r() * 50;
    seg(85, x0, H - 1, x0 + lean, top, 1.1, 0.7, col);
    for (let n = 0; n < 6; n++) {
      const t = 0.18 + n * 0.13, y = H - 1 - (H - 1 - top) * t, x = x0 + lean * t, len = 3.6 + 1.2 * Math.sin(Math.PI * t);
      for (const side of [-1, 1]) curve(85, [[x, y], [x + side * len * 0.5, y - 1.4], [x + side * len, y - 0.6]], 2.6, 0.6, col);
    }
  }
}

// 86: milkweed stem (tinted): stout, pale, finely downy
hairyStem(86, 0xA586, 0.84, () => 0);

// 87: milkweed parts. Left half rows 0-16: an umbel (real colours): pedicels from its stalk at the top, a drooping ball of
// pink-purple flowers, darker hoods at their middles. Right half rows 0-16: a seed pod, grey-green, warty, pointed at the
// top, a short stalk below. The six leaf bands (tinted): broad oblong, a broad pale midrib, straight side veins.
{
  const r = rng(0xA587);
  for (let k = 0; k < 14; k++) {
    const a = Math.PI * (0.15 + 0.7 * k / 13), x = 8 + Math.cos(a) * 6, y = 9 + Math.sin(a) * 3.5 - 2;
    seg(87, 8, 0.5, x, y, 0.5, 0.4, [120, 140, 82]);
  }
  for (let k = 0; k < 46; k++) {
    const a = r() * Math.PI * 2, d = Math.sqrt(r()), x = 8 + Math.cos(a) * d * 6.6, y = 9.5 + Math.sin(a) * d * 6;
    if (y < 3) continue;
    blob(87, x, y, 1.15, 1.0, [204 + r() * 20, 116 + r() * 20, 156 + r() * 16]);
    blob(87, x, y, 0.45, 0.45, [150, 70, 108]);
  }
  for (let py = 0; py <= 16; py++) {
    const t = py / 16, w = py < 2 ? 0 : 4.6 * Math.pow(Math.sin(Math.PI * Math.min(1, (t - 0.1) / 0.85)), 0.8);
    for (let px = 16; px < 32; px++) {
      const d = Math.abs(px + 0.5 - 24);
      if (t > 0.92) { if (d < 0.7) add(87 * COL + px, py, 112, 132, 84, 1); continue; }
      if (d > w) continue;
      const wart = speck(87, px, py) < 0.18 ? 1.18 : 1, k = (0.92 + 0.1 * (1 - d / Math.max(1, w))) * wart;
      add(87 * COL + px, py, 136 * k, 150 * k, 104 * k, 1);
    }
  }
  for (const at of [0.22, 0.32, 0.42, 0.52, 0.62, 0.72])
    leafBand(87, at, u => 4.5 * Math.pow(Math.sin(Math.PI * Math.min(1, 0.07 + 0.93 * u)), 0.42), () => 0,
      (u, a, x, y) => { const v = Math.abs(a) < 0.16 ? 1.14 : pinnate(u, a, 3.2, 0.6) ? 1.05 : 1, g = 0.7 * v; return [g * 0.95 * 255, g * 255, g * 0.9 * 255]; });
}

// 88 / 89: milkweed seen from the side (about 1.4 m over 128 px, 0.5 m over 32): the stout stem, broad leaves held out in
// pairs (tinted); on the flower card the drooping pink umbels in the upper axils and a pod (real colours)
{
  const r = rng(0xA588), g = 0.72, col = [g * 0.95 * 255, g * 255, g * 0.9 * 255];
  seg(88, 16, H - 1, 16.5, 4, 1.8, 1.1, [0.84 * 0.95 * 255, 0.84 * 255, 0.84 * 0.9 * 255]);
  for (let n = 0; n < 7; n++) {
    const t = 0.22 + n * 0.1, y = H - 1 - (H - 5) * Math.min(t, 0.86), len = n === 6 ? 7 : 10.5 - Math.abs(t - 0.4) * 6;
    for (const side of [-1, 1]) {
      const s = n % 2 ? side * 0.55 : side;
      curve(88, [[16, y], [16 + s * len * 0.5, y - 2.4], [16 + s * len, y - 1.2]], 4.2, 1.2, col);
    }
  }
  for (const [x, y] of [[10, 22], [22, 16], [12, 10]]) {
    seg(89, 16, y - 4, x, y - 1, 0.6, 0.6, [120, 140, 82]);
    for (let k = 0; k < 14; k++) blob(89, x + (r() - 0.5) * 5, y + 1 + (r() - 0.5) * 4, 1.1, 1.0, [208, 118, 158]);
  }
  for (let py = 26; py < 40; py++) { const w = 1.6 * Math.sin(Math.PI * (py - 26) / 14); seg(89, 21 - w, py, 21 + w, py, 1, 1, [136, 150, 104]); }
}

// 90: poison ivy petiole (tinted): smooth, reddish at the foot and at the top where the leaflets join
fill(90, (px, py) => {
  const f = 1 - (py + 0.5) / H, red = Math.max(0, 1 - f / 0.25) + Math.max(0, (f - 0.9) / 0.1) * 0.6;
  const k = 0.76 * (1 + 0.03 * Math.sin(px / COL * Math.PI * 2 * 3));
  return [Math.min(1, k * (0.95 + 0.35 * red)) * 255, k * (1 - 0.22 * red) * 255, k * (0.88 - 0.1 * red) * 255];
});

// 91: poison ivy parts (tinted): the leaflets in their bands, base at the left. End leaflets (0.25, 0.85): ovate, a long
// point, two or three coarse teeth a side (fewer on the second). Side leaflets (0.45, 0.65): lopsided, a lobe on the outer
// side (the upper edge on 0.45, the lower on 0.65). Glossy: a lighter sheen along one half, the veins pale.
{
  const ovate = u => 4.5 * Math.pow(Math.sin(Math.PI * Math.min(1, 0.05 + 0.95 * Math.pow(u, 0.72))), 0.7) * (u > 0.8 ? 1 - (u - 0.8) * 1.6 : 1);
  const tooth = (u, centres) => centres.reduce((m, c) => Math.max(m, 1.5 * Math.max(0, 1 - Math.abs(u - c) / 0.05)), 0);
  const gloss = (u, a, x, y) => {
    const sheen = a < 0 ? 0.1 * Math.max(0, 1 - Math.abs(a + 0.45) / 0.4) : 0, v = pinnate(u, a, 4, 1.2) ? 0.08 : 0;
    const g = 0.66 * (1 + sheen + v) * (0.97 + 0.06 * speck(91, x, y));
    return [g * 0.93 * 255, g * 255, g * 0.86 * 255];
  };
  leafBand(91, 0.25, ovate, (u, side) => tooth(u, side < 0 ? [0.42, 0.6] : [0.5, 0.68]), gloss);
  leafBand(91, 0.85, ovate, (u, side) => tooth(u, side < 0 ? [0.55] : [0.47]), gloss);
  const notchSide = lobeSide => (u, side) => side === lobeSide ? tooth(u, [0.36]) * 1.6 : tooth(u, [0.62]) * 0.6;
  // the lobe: the outer edge wider below the middle, a deep notch above it
  leafBand(91, 0.45, u => ovate(u) * (1 + 0.12 * Math.max(0, 1 - Math.abs(u - 0.3) / 0.2)), notchSide(-1), gloss);
  leafBand(91, 0.65, u => ovate(u) * (1 + 0.12 * Math.max(0, 1 - Math.abs(u - 0.3) / 0.2)), notchSide(1), gloss);
}

// 92: poison ivy card (about 0.5 m over 128 px, 0.4 m over 32; tinted): petioles arching up and out, each holding three
// leaflets spread level, the end one longer
{
  const r = rng(0x7A92), g = 0.68, col = [g * 0.93 * 255, g * 255, g * 0.86 * 255];
  for (let k = 0; k < 7; k++) {
    const side = k % 2 ? 1 : -1, reach = 3 + r() * 9, top = 20 + r() * 80, tx = 16 + side * reach, ty = top;
    curve(92, [[16, H - 1], [16 + side * reach * 0.4, (H + top) / 2], [tx, ty]], 1.0, 0.8, [0.76 * 255, 0.66 * 255, 0.6 * 255]);
    const len = 6 + r() * 3;
    curve(92, [[tx, ty], [tx + side * len * 0.5, ty - 1.5], [tx + side * len, ty - 0.5]], 3.4, 0.6, col);
    curve(92, [[tx, ty], [tx - side * len * 0.35, ty - 1.2], [tx - side * len * 0.75, ty - 0.2]], 3.0, 0.6, col);
    curve(92, [[tx, ty], [tx + side * 0.6, ty - len * 0.4], [tx + side * 1.4, ty - len * 0.85]], 3.2, 0.6, col);
  }
}

// ------------------------------------------------------------------------------ P4a batch 3 (93-107)

/** A small pointed-elliptic leaf in column c from (x, y) along angle a (radians, 0 = up, positive = to the right), len long, wid wide. */
function smallLeaf(c, x, y, a, len, wid, col) {
  const dx = Math.sin(a), dy = -Math.cos(a), nx = -dy, ny = dx, pts = [];
  for (let i = 0; i <= 10; i++) { const t = i / 10, w = wid / 2 * Math.pow(Math.sin(Math.PI * t), 0.8); pts.push([x + dx * len * t + nx * w, y + dy * len * t + ny * w]); }
  for (let i = 10; i >= 0; i--) { const t = i / 10, w = wid / 2 * Math.pow(Math.sin(Math.PI * t), 0.8); pts.push([x + dx * len * t - nx * w, y + dy * len * t - ny * w]); }
  poly(c, pts, col);
}

/**
 * 93 / 94: a lowbush blueberry spray (real colours, cut out; about 0.3 m over 128 px, 0.09 m over 32): the twig from the
 * foot, red-brown low down and green-red above, four side twigs branching up and out, small alternate elliptic leaves
 * along all of them (blue-green, now and then one reddening); on 94 clusters of blue berries with their pale bloom
 * hanging under the twigs.
 */
function blueberrySpray(c, seed, berries) {
  const r = rng(seed), twig = t => [130 - 30 * t, 72 + 40 * t, 52 + 4 * t];
  const leaf = () => r() < 0.04 ? [136 + r() * 16, 92, 66] : [80 + r() * 18, 118 + r() * 16, 84 + r() * 14];
  const main = [[16, H - 1], [15, 96], [17.5, 60], [16, 30], [16.5, 3]];
  curve(c, main, 1.6, 0.8, t => twig(t));
  const tips = [];
  for (const [y0, side, len] of [[92, -1, 13], [72, 1, 12], [52, -1, 11], [34, 1, 9]]) {
    const pts = [[16, y0], [16 + side * len * 0.5, y0 - len * 0.9], [16 + side * len, y0 - len * 1.6]];
    curve(c, pts, 1.0, 0.6, t => twig(0.5 + 0.5 * t));
    tips.push(pts);
  }
  // leaves: along the main twig above row 100 and along each side twig
  for (let y = 100; y > 4; y -= 6.5) {
    const side = Math.round((100 - y) / 6.5) % 2 ? 1 : -1, a = side * (0.7 + r() * 0.3);
    smallLeaf(c, 16 + side * 0.6, y, a, 8 + r() * 3, 3.6 + r() * 0.8, leaf());
  }
  for (const pts of tips)
    for (let t = 0.3; t <= 1.0; t += 0.23) {
      const x = pts[0][0] + (pts[2][0] - pts[0][0]) * t, y = pts[0][1] + (pts[2][1] - pts[0][1]) * t, side = Math.round(t * 4.3) % 2 ? 1 : -1;
      smallLeaf(c, x, y, Math.atan2(pts[2][0] - pts[0][0], pts[0][1] - pts[2][1]) + side * 0.8, 7 + r() * 3, 3.4, leaf());
    }
  if (!berries) return;
  for (const [x, y] of [[9, 82], [23, 62], [10, 44], [20, 26]]) {
    const n = 3 + Math.floor(r() * 4);
    for (let k = 0; k < n; k++) {
      const bx = x + (r() - 0.5) * 5, by = y + r() * 6;
      blob(c, bx, by, 1.5, 1.3, [72 + r() * 14, 88 + r() * 14, 146 + r() * 18]);
      blob(c, bx - 0.4, by - 0.4, 0.6, 0.5, [150, 166, 200]);
    }
  }
}
blueberrySpray(93, 0xB1B93, false);
blueberrySpray(94, 0xB1B94, true);

// 95: a blueberry mat from the side (about 0.4 m over 128 px, 0.5 m over 32; real colours): many short sprays in a low
// mound, a few berry clusters
{
  const r = rng(0xB1B95);
  for (let k = 0; k < 22; k++) {
    const x0 = 2 + r() * 28, lean = (r() - 0.5) * 16, top = 30 + r() * 70;
    seg(95, x0, H - 1, x0 + lean, top, 0.9, 0.6, [118, 90, 56]);
    for (let n = 0; n < 6; n++) {
      const t = 0.3 + n * 0.12, x = x0 + lean * t, y = H - 1 - (H - 1 - top) * t;
      blob(95, x + (n % 2 ? 1.2 : -1.2), y, 1.4, 2.6, [80 + r() * 18, 118 + r() * 16, 84 + r() * 14]);
    }
  }
  for (let k = 0; k < 9; k++) blob(95, 3 + r() * 26, 60 + r() * 50, 1.0, 0.8, [74, 90, 150]);
}

// 96: elderberry cane (real colours): grey-brown bark with pale warty lenticels low down, smoother and greener up the cane
fill(96, (px, py) => {
  const f = 1 - (py + 0.5) / H, green = Math.max(0, Math.min(1, (f - 0.45) / 0.35)), s = speck(96, px, py);
  const lent = f < 0.6 && s < 0.06 ? 1.35 : 1, k = (0.92 + 0.1 * speck(97, px, py)) * lent;
  return [(124 - 20 * green) * k, (112 + 12 * green) * k, (92 - 14 * green) * k];
});

// 97: elderberry compound leaf (tinted, cut out; about 25 cm over 128 px, 19 cm over 32): the petiole and rachis up the
// middle, three pairs of lanceolate, finely toothed leaflets angled out and up, the end leaflet on the tip
{
  const g = 0.72, col = [g * 0.94 * 255, g * 255, g * 0.86 * 255], rib = [g * 1.06 * 0.94 * 255, g * 1.06 * 255, g * 1.06 * 0.86 * 255];
  const X = cm => 16 + cm / 0.6, Y = cm => H - 0.5 - cm / 0.2;
  seg(97, 16, H - 0.5, 16, Y(21), 1.6, 1.0, rib);
  const leaflet = (bx, by, ang, len, wid) => {
    const dx = Math.sin(ang), dy = Math.cos(ang), nx = dy, ny = -dx, pts = [];
    for (let i = 0; i <= 24; i++) {
      const t = i / 24, w = wid / 2 * Math.pow(Math.sin(Math.PI * Math.min(1, 0.04 + 0.96 * Math.pow(t, 0.8))), 0.8) * (i % 2 && t > 0.15 && t < 0.92 ? 0.9 : 1);
      pts.push([X(bx + dx * len * t + nx * w), Y(by + dy * len * t + ny * w)]);
    }
    for (let i = 24; i >= 0; i--) {
      const t = i / 24, w = wid / 2 * Math.pow(Math.sin(Math.PI * Math.min(1, 0.04 + 0.96 * Math.pow(t, 0.8))), 0.8) * (i % 2 && t > 0.15 && t < 0.92 ? 0.9 : 1);
      pts.push([X(bx + dx * len * t - nx * w), Y(by + dy * len * t - ny * w)]);
    }
    poly(97, pts, col);
    seg(97, X(bx), Y(by), X(bx + dx * len * 0.9), Y(by + dy * len * 0.9), 0.7, 0.4, rib);
  };
  for (const [y, len] of [[6, 7.5], [11, 8], [16, 7.5]])
    for (const side of [-1, 1]) leaflet(0, y, side * 1.15, len, 3.8);
  leaflet(0, 19.5, 0, 6, 3.6);
}

// 98: elderberry parts (real colours). Left half rows 0-15: a flat-topped cyme from above, a disc of tiny cream-white
// flowers on fine green branches. Right half rows 0-31: a hanging berry cluster, red stalks branching down from the top,
// the purple-black berries with a little shine.
{
  const r = rng(0xE1D98);
  for (let k = 0; k < 6; k++) { const a = k / 6 * Math.PI * 2; seg(98, 8, 8, 8 + Math.cos(a) * 6.5, 8 + Math.sin(a) * 6.5, 0.6, 0.4, [120, 140, 84]); }
  for (let k = 0; k < 120; k++) {
    const a = r() * Math.PI * 2, d = Math.sqrt(r()) * 7.2, x = 8 + Math.cos(a) * d, y = 8 + Math.sin(a) * d;
    blob(98, x, y, 0.75, 0.75, r() < 0.12 ? [226, 214, 150] : [242, 238, 220]);
  }
  for (let k = 0; k < 5; k++) {
    const x = 18.5 + k * 2.8;
    curve(98, [[24, 0.5], [24 + (x - 24) * 0.4, 5], [x, 11 + r() * 6]], 0.45, 0.35, [150, 44, 54]);
  }
  for (let k = 0; k < 60; k++) {
    const x = 18 + r() * 12, y = 10 + r() * 20 - Math.abs(x - 24) * 0.7;
    if (y < 8) continue;
    blob(98, x, y, 1.1, 1.0, [44 + r() * 14, 24 + r() * 10, 52 + r() * 14]);
    if (r() < 0.3) blob(98, x - 0.3, y - 0.3, 0.35, 0.35, [120, 96, 140]);
  }
}

// 99 / 100: an elderberry from the side (about 2.5 m over 128 px, 1.25 m over 32): canes fanning out of the crown and
// arching over, their compound leaves in masses along the upper two thirds (tinted); on the flower card the flat white
// cymes on the cane tips and two dark berry clusters (real colours)
{
  const r = rng(0xE1D99), g = 0.72, col = [g * 0.94 * 255, g * 255, g * 0.86 * 255];
  const tips = [];
  for (let k = 0; k < 6; k++) {
    // canes fanning out from the crown and arching over to the sides: the shrub as wide as the card
    const side = (k / 5) * 2 - 1, tx = 16 + side * 15, ty = 14 + Math.abs(side) * 36 + r() * 8, mx = 16 + side * 8, my = 30 + Math.abs(side) * 10;
    const pts = [[16, H - 1], [16 + side * 3, 80], [mx, my], [tx, ty]];
    curve(99, pts, 1.6, 0.9, [0.6 * 255, 0.58 * 255, 0.5 * 255]);
    for (let t = 0.3; t <= 1.0; t += 0.07) {
      const i = Math.min(2, Math.floor(t * 3)), u = t * 3 - i, x = pts[i][0] + (pts[i + 1][0] - pts[i][0]) * u, y = pts[i][1] + (pts[i + 1][1] - pts[i][1]) * u;
      blob(99, x + (r() - 0.5) * 5, y + (r() - 0.5) * 4, 3.0, 3.6, col);
    }
    tips.push([tx, ty]);
  }
  tips.forEach(([x, y], k) => {
    if (k % 3 === 2) for (let n = 0; n < 8; n++) blob(100, x + (r() - 0.5) * 3, y + 3 + r() * 4, 1.0, 1.0, [48, 26, 54]);
    else { blob(100, x, y - 1, 4.2, 1.6, [240, 236, 218]); blob(100, x, y - 1.6, 2.6, 0.8, [248, 246, 232]); }
  });
}

// 101: sagebrush bark (real colours): grey-brown, shredding in long strips (lighter and darker streaks down the trunk)
{
  const r = rng(0x5A101), strip = Array.from({length: COL}, () => r());
  fill(101, (px, py) => {
    const s = strip[px], wave = Math.sin(py * 0.15 + s * 9) * 0.5 + 0.5, k = 0.9 + 0.12 * speck(101, px, py);
    const tone = s < 0.3 ? 0.78 : s > 0.75 ? 1.22 : 1;
    return [118 * tone * k * (0.95 + 0.1 * wave), 106 * tone * k, 90 * tone * k * (0.97 + 0.05 * wave)];
  });
}

// 102: sagebrush foliage (real colours, cut out). Rows 0-31: a mass from the side, the small grey three-toothed leaves
// crowded on the twigs, ragged at the edge, darker in its gaps, twig ends showing below. Rows 32-63: the same from above.
{
  const r = rng(0x5A102), leafCol = () => { const k = 0.88 + r() * 0.2; return [160 * k, 168 * k, 146 * k]; };
  for (let k = 0; k < 8; k++) seg(102, 16 + (r() - 0.5) * 6, 31, 6 + r() * 20, 14 + r() * 10, 0.9, 0.5, [112, 98, 82]);
  for (let k = 0; k < 420; k++) {
    const a = r() * Math.PI * 2, d = Math.sqrt(r()), x = 16 + Math.cos(a) * d * 15, y = 16 + Math.sin(a) * d * 13 - 2;
    if (y > 29 || y < 1) continue;
    const dark = r() < 0.12 && d < 0.75;
    blob(102, x, y, 1.1, 0.75, dark ? [104, 112, 96] : leafCol());
  }
  for (let k = 0; k < 520; k++) {
    const a = r() * Math.PI * 2, d = Math.sqrt(r()), x = 16 + Math.cos(a) * d * 15, y = 48 + Math.sin(a) * d * 15;
    const dark = r() < 0.1 && d < 0.8;
    blob(102, x, y, 1.0, 1.0, dark ? [104, 112, 96] : leafCol());
  }
}

// 103: a sagebrush from the side (about 1.2 m over 128 px, 1.25 m over 32; real colours): gnarled trunks spreading from
// the base, the ragged silver crown over them in several rounded masses
{
  const r = rng(0x5A103);
  for (let k = 0; k < 4; k++) curve(103, [[16 + (r() - 0.5) * 3, H - 1], [16 + (r() - 0.5) * 12, 96], [4 + r() * 24, 60]], 1.6, 0.8, [108, 96, 80]);
  for (const [cx, cy, rx, ry] of [[10, 52, 9, 22], [22, 46, 9, 26], [16, 34, 10, 24], [6, 70, 6, 14], [26, 68, 6, 14]])
    for (let k = 0; k < 160; k++) {
      const a = r() * Math.PI * 2, d = Math.sqrt(r()), x = cx + Math.cos(a) * d * rx, y = cy + Math.sin(a) * d * ry;
      const kk = 0.86 + r() * 0.22;
      blob(103, x, y, 1.0, 1.4, r() < 0.1 ? [104, 112, 96] : [160 * kk, 168 * kk, 146 * kk]);
    }
}

// 104: American beachgrass leaf, grey for the tint: rolled in (the upper face hidden: fine light lines along it), a
// little glaucous, stiff, a drier tip
{
  const r = rng(0xA3B104);
  const streak = Array.from({length: COL}, () => 0.97 + r() * 0.06);
  fill(104, (px, py) => {
    const f = 1 - (py + 0.5) / H, a = across(px);
    let g = (0.78 + 0.08 * Math.min(1, f * 1.6)) * streak[px];
    if (px % 2 === 0) g *= 1.04;
    if (a > 0.85) g *= 0.9;
    const dry = Math.max(0, (f - 0.85) / 0.15);
    return [Math.min(1, g * (0.9 + 0.2 * dry)) * 255, Math.min(1, g * (1 - 0.02 * dry)) * 255, Math.min(1, g * (1.06 - 0.24 * dry)) * 255];
  });
}

// 105: beachgrass culm, grey for the tint: smooth, two darker nodes
fill(105, (px, py) => {
  const f = 1 - (py + 0.5) / H;
  let g = 0.84 * (1 + 0.03 * Math.sin(px / COL * Math.PI * 2 * 4));
  for (const n of [0.25, 0.5]) if (Math.abs(f - n) * H < 1.2) g *= 0.82;
  return [g * 0.95 * 255, g * 255, g * 0.9 * 255];
});

// 106: beachgrass spike at the culm's heights (rows 0-36; real colours): the dense spike-like panicle, pale straw, the
// spikelets pressed close (short darker lines); below row 36 the culm's pale green
fill(106, (px, py) => {
  const k = 0.92 + 0.1 * speck(106, px, py);
  if (py > 36) return [150 * k, 164 * k, 112 * k];
  const line = (px + Math.floor(py / 3)) % 4 === 0 ? 0.86 : 1;
  return [214 * k * line, 198 * k * line, 150 * k * line];
});

// 107: beachgrass from the side (about 0.9 m over 128 px, 0.35 m over 32; tinted): a fountain of narrow rolled leaves
// arching out, two culms standing above them with their spikes
{
  const r = rng(0xA3B107), g = 0.8, col = [g * 0.92 * 255, g * 255, g * 1.02 * 255];
  for (let k = 0; k < 16; k++) {
    const side = k % 2 ? 1 : -1, reach = 6 + r() * 10, rise = 45 + r() * 60, pts = [];
    for (let i = 0; i <= 6; i++) { const t = i / 6; pts.push([16 + side * reach * Math.pow(t, 1.3), H - 1 - rise * Math.sin(t * Math.PI * 0.75) / Math.sin(Math.PI * 0.5)]); }
    curve(107, pts, 1.3, 0.5, col);
  }
  for (const x of [13, 19.5]) { seg(107, 16, H - 1, x, 8, 0.9, 0.8, col); seg(107, x, 8, x + (x - 16) * 0.05, 34, 2.2, 2.4, [0.94 * 255, 0.92 * 255, 0.8 * 255]); }
}

// --------------------------------------------------------------------------------------------- finish

// cut at half coverage; pad transparent pixels with the nearest drawn colour (per column), empty columns a neutral green
const out = Buffer.alloc(W * H * 4);
const filled = new Float32Array(W * H * 3), have = new Uint8Array(W * H);
for (let i = 0; i < W * H; i++) {
  if (cover[i] <= 0) continue;
  filled[i * 3] = rgb[i * 3] / cover[i]; filled[i * 3 + 1] = rgb[i * 3 + 1] / cover[i]; filled[i * 3 + 2] = rgb[i * 3 + 2] / cover[i];
  have[i] = 1;
}
for (let c = 0; c < COLS; c++) {
  // breadth-first from the drawn pixels, inside the column only
  let frontier = [];
  for (let y = 0; y < H; y++) for (let x = c * COL; x < (c + 1) * COL; x++) if (have[y * W + x]) frontier.push(y * W + x);
  if (!frontier.length) {
    for (let y = 0; y < H; y++) for (let x = c * COL; x < (c + 1) * COL; x++) {
      const i = y * W + x;
      filled[i * 3] = 110; filled[i * 3 + 1] = 128; filled[i * 3 + 2] = 84;
    }
    continue;
  }
  while (frontier.length) {
    const next = [];
    for (const i of frontier) {
      const x = i % W, y = (i - x) / W;
      for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
        const nx = x + dx, ny = y + dy;
        if (nx < c * COL || nx >= (c + 1) * COL || ny < 0 || ny >= H) continue;
        const j = ny * W + nx;
        if (have[j]) continue;
        have[j] = 1;
        filled[j * 3] = filled[i * 3]; filled[j * 3 + 1] = filled[i * 3 + 1]; filled[j * 3 + 2] = filled[i * 3 + 2];
        next.push(j);
      }
    }
    frontier = next;
  }
}
for (let i = 0; i < W * H; i++) {
  for (let k = 0; k < 3; k++) out[i * 4 + k] = Math.max(0, Math.min(255, Math.round(filled[i * 3 + k])));
  out[i * 4 + 3] = cover[i] >= 0.5 ? 255 : 0;
}

const data = png(out, W, H);
const files = ['mesh_plants.png', 'mesh_plants_still.png'].map(n => path.join(OUT, n));
if (process.argv.includes('--check')) {
  let stale = 0;
  for (const file of files) {
    const same = fs.existsSync(file) && Buffer.compare(fs.readFileSync(file), data) === 0;
    console.log(path.basename(file) + (same ? ' up to date' : ' STALE (run without --check)'));
    if (!same) stale++;
  }
  process.exit(stale ? 1 : 0);
}
fs.mkdirSync(OUT, {recursive: true});
let opaque = 0;
for (let i = 0; i < W * H; i++) if (out[i * 4 + 3]) opaque++;
for (const file of files) {
  fs.writeFileSync(file, data);
  console.log(`wrote ${path.relative(ROOT, file)} (${W} x ${H}, ${opaque} opaque px)`);
}
