// C3.3b group 2 package B (2026-10-17): the interchange guide signs' sheet (docs/worldgen/federal_interchanges_c3_3b.md 10).
// The signs are built in code from their legend (worldgen/network/road/GuideSigns, client/road/TunnelParts); this sheet
// holds what they are drawn with, at 240 px/m:
//   - uniform tiles (64 x 64, faces map to their middle 32 x 32): the sign green, the border white, the panel's back
//     (aluminium), the posts (galvanised steel);
//   - one cell per character of the mixed-case sign font (tools/afl-sign-font.mjs anyGlyph): white on the sign green, the
//     cell as wide as the character's advance (its letter gap included) and as high as a line (descender to above the
//     cap height), so a legend is a row of cells laid edge to edge on the green panel;
//   - the route shields (user 2026-10-17: shield A, "Federal crest") on the sign green: FEDERAL F-40 / F-65 navy with a
//     red top band, ROUTE RT 10 ... RT 81 white with black numerals (the numbers of the naming draft);
//   - the guide arrows (up-right, up-left) on the sign green.
// Every cell and tile starts on a 16 px boundary with at least 16 px of the sign green to the next (mipmaps never mix in
// a neighbour's white). Also writes the Java table of the cells and tiles (GuideSignAtlas), so the server lays a legend
// out with the same advances the client draws.
//   node tools/build-guide-signs-v1.mjs            write the sheet (+ _s, _n) and GuideSignAtlas.java
//   node tools/build-guide-signs-v1.mjs --check    fail if any written file is stale
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {png} from './cube-slab-mesh-lib.mjs';
import {Raster} from './build-tunnel-v1.mjs';
import {anyGlyph, MIXED, CAP} from './afl-sign-font.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const TEX = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight/textures/block/road');
const JAVA = path.join(ROOT, 'src/main/java/com/antaurora/apofirstlight/worldgen/network/road/GuideSignAtlas.java');
const assert = (c, m) => { if (!c) throw new Error(m); };

const SW = 2048, SH = 1024, PX = 240;
/** A line's cell from CELL_BOTTOM under the baseline to CELL_TOP over it (font units, cap 750), CELL px high; letter gap and space. */
const CELL_BOTTOM = -260, CELL_TOP = 860, CELL = 144, PPU = CELL / (CELL_TOP - CELL_BOTTOM), GAP = 110, SPACE = 360;
const C = {green: [20, 98, 66], white: [226, 226, 220], black: [24, 24, 24], navy: [30, 58, 116], red: [168, 34, 38]};
const GREEN = {c: C.green, s: 110, f0: 14, h: 0}, WHITE = {c: C.white, s: 120, f0: 14, h: 0.3};
const MATS = {green: GREEN, white: {c: C.white, s: 120, f0: 14}, back: {c: [168, 170, 168], s: 120, f0: 40}, post: {c: [150, 153, 154], s: 105, f0: 40}};
/** The route shields drawn (the naming draft's numbers, docs 10): F interstates, R the ROUTE network. */
const SHIELDS = ['F40', 'F65', 'R10', 'R30', 'R31', 'R61', 'R80', 'R81'];
/** Shield height and its width (shield A: 1 : 1.08), the arrows' tile side, m. */
const SHIELD_H = 0.8, SHIELD_W = SHIELD_H / 1.08, ARROW = 0.7;

// ---------------------------------------------------------------- layout
/** Rects {x, y, w, h} px by key: tiles, then glyph cells (ch:<c>), packed left to right in rows. */
function layout() {
  const R = {};
  Object.keys(MATS).forEach((k, i) => { R[k] = [i * 64, 0, 64, 64]; });
  let x = 0, y = 80, rowH = CELL;
  const put = (key, w, h) => {
    if (x + w > SW) { x = 0; y += rowH + 16; rowH = h; }
    rowH = Math.max(rowH, h);
    R[key] = [x, y, w, h];
    x = Math.ceil((x + w + 16) / 16) * 16;
  };
  for (const ch of MIXED) put('ch:' + ch, Math.round((anyGlyph(ch).width + GAP) * PPU), CELL);
  // the tiles from a new row
  x = 0; y += rowH + 16; rowH = 0;
  const sh = Math.round(SHIELD_H * PX), sw = Math.round(SHIELD_W * PX), ar = Math.round(ARROW * PX);
  for (const s of SHIELDS) put(s, sw, sh);
  for (const a of ['arrow_ur', 'arrow_ul']) put(a, ar, ar);
  assert(y + rowH <= SH, 'the sheet is too small');
  return R;
}

// ---------------------------------------------------------------- painting
/** A text's sdf in px from its left end on the baseline (y up), cap capPx high, letter gap gap (font units). */
function text(str, capPx, gap = GAP) {
  const k = capPx / CAP, parts = [];
  let x = 0;
  for (const ch of str) { const g = anyGlyph(ch); parts.push([x, g]); x += g.width + gap; }
  const width = (x - gap) * k;
  return {width, sdf: (px, py) => { let d = 1e9; for (const [ox, g] of parts) d = Math.min(d, g.sdf(px / k - ox, py / k)); return d; }};
}

/** Shield A's outline (heater: flat top, straight sides to 0.42 h, curving to the point) as a polygon, w x h px, y down. */
function heater(w, h) {
  const pts = [[0, 0], [w, 0], [w, 0.42 * h]];
  const cubic = (p0, p1, p2, p3) => { for (let i = 1; i <= 48; i++) { const t = i / 48, u = 1 - t;
    pts.push([0, 1].map(j => u * u * u * p0[j] + 3 * u * u * t * p1[j] + 3 * u * t * t * p2[j] + t * t * t * p3[j])); } };
  cubic([w, 0.42 * h], [w, 0.78 * h], [0.62 * w, 0.93 * h], [w / 2, h]);
  cubic([w / 2, h], [0.38 * w, 0.93 * h], [0, 0.78 * h], [0, 0.42 * h]);
  return pts;
}
function inside(pts, x, y) {
  let c = false;
  for (let i = 0, j = pts.length - 1; i < pts.length; j = i++) {
    const [xi, yi] = pts[i], [xj, yj] = pts[j];
    if ((yi > y) !== (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) c = !c;
  }
  return c;
}

/** A shield on the sign green (the naming sheet's shield A, design/worldgen/federal_interchanges/source/shields.mjs). */
function shield(key, w, h) {
  const fed = key[0] === 'F', num = key.slice(1), poly = heater(w, h);
  // inside the outline scaled by k about the centre
  const within = (x, y, k) => inside(poly, w / 2 + (x - w / 2) / k, h / 2 + (y - h / 2) / k);
  const numCap = (fed ? 0.322 : 0.35) * h;
  let digits = text(num, numCap);
  if (digits.width > 0.66 * w) digits = text(num, numCap * 0.66 * w / digits.width);
  const nx = (w - digits.width) / 2, nb = (fed ? 0.73 : 0.66) * h;
  const band = text('FEDERAL', 0.095 * h, 160), bx = (w - band.width) / 2, bb = 0.245 * h;
  return (x, y) => {
    if (!within(x, y, 1)) return GREEN;
    if (fed) {
      if (!within(x, y, 0.955)) return {c: C.black, s: 110, f0: 14, h: 0.3};
      if (!within(x, y, 0.89)) return {c: C.white, s: 120, f0: 14, h: 0.3};
      if (y < 0.3 * h) return band.sdf(x - bx, bb - y) < 0 ? WHITE : {c: C.red, s: 110, f0: 14, h: 0.3};
      return digits.sdf(x - nx, nb - y) < 0 ? WHITE : {c: C.navy, s: 110, f0: 14, h: 0.3};
    }
    if (!within(x, y, 0.93)) return {c: C.black, s: 110, f0: 14, h: 0.3};
    return digits.sdf(x - nx, nb - y) < 0 ? {c: C.black, s: 110, f0: 14, h: 0.5} : {c: C.white, s: 120, f0: 14, h: 0.3};
  };
}

/**
 * The guide arrow (MUTCD up arrow proportions: shaft 0.2 of its length, head 0.6 wide and 0.42 long) along the
 * diagonal of a side x side tile, pointing up and right (ur) or up and left (ul).
 */
function arrow(dir, side) {
  const L = side * 1.2, sw = 0.2 * L, hw = 0.6 * L, hl = 0.42 * L, c = side / 2, s = dir === 'ur' ? 1 : -1;
  return (x, y) => {
    // the arrow's frame: v along it (tip at +L/2), u across; raster y down
    const dx = x - c, dy = c - y, v = (s * dx + dy) / Math.SQRT2, u = (dx - s * dy) / Math.SQRT2 * s;
    if (v < -L / 2 || v > L / 2) return GREEN;
    if (v <= L / 2 - hl) return Math.abs(u) <= sw / 2 ? WHITE : GREEN;
    return Math.abs(u) <= hw / 2 * (L / 2 - v) / hl ? WHITE : GREEN;
  };
}

function sheet(R) {
  const Rs = new Raster(SW, SH);
  // the whole sheet the sign green first: every gap between cells is the green a mipmap mixes in
  Rs.paint(0, 0, SW, SH, () => GREEN, 1);
  for (const k of Object.keys(MATS)) { const [x0, y0, w, h] = R[k]; Rs.paint(x0, y0, w, h, () => MATS[k]); }
  for (const ch of MIXED) {
    const [x0, y0, w, h] = R['ch:' + ch], g = anyGlyph(ch);
    // font units: x from half a gap left of the glyph, y down from CELL_TOP
    Rs.paint(x0, y0, w, h, (x, y) => g.sdf(x / PPU - GAP / 2, CELL_TOP - y / PPU) < 0 ? WHITE : GREEN, 3);
  }
  for (const s of SHIELDS) { const [x0, y0, w, h] = R[s]; Rs.paint(x0, y0, w, h, shield(s, w, h), 3); }
  for (const a of ['arrow_ur', 'arrow_ul']) { const [x0, y0, w] = R[a]; Rs.paint(x0, y0, w, w, arrow(a.slice(6), w), 3); }
  return Rs.finish(false);
}

// ---------------------------------------------------------------- Java table
function java(R) {
  const f = v => (Math.round(v * 1e5) / 1e5).toString();
  // non-ASCII characters as Java escapes (the sources' encoding is not assumed)
  const chars = MIXED.map(ch => ch.charCodeAt(0) < 128 ? ch : '\\u' + ch.charCodeAt(0).toString(16).padStart(4, '0')).join('');
  const adv = MIXED.map(ch => f((anyGlyph(ch).width + GAP) / CAP));
  const cells = MIXED.map(ch => R['ch:' + ch].join(', '));
  const tiles = [...Object.keys(MATS), ...SHIELDS, 'arrow_ur', 'arrow_ul'];
  const size = k => SHIELDS.includes(k) ? [SHIELD_W, SHIELD_H] : k.startsWith('arrow') ? [ARROW, ARROW] : [0, 0];
  const wrap = (items, per) => { const rows = []; for (let i = 0; i < items.length; i += per) rows.push('            ' + items.slice(i, i + per).join(', ')); return rows.join(',\n'); };
  return `package com.antaurora.apofirstlight.worldgen.network.road;

/**
 * GENERATED by tools/build-guide-signs-v1.mjs (node tools/build-guide-signs-v1.mjs); do not edit by hand. The interchange
 * guide signs' sheet block/road/guide_sign (C3.3b group 2 package B, docs/worldgen/federal_interchanges_c3_3b.md 10): the
 * mixed-case sign font's cells (white on the sign green, as wide as a character's advance with its letter gap, as high as
 * a line) and the tiles (uniform materials, route shields, arrows). The server lays legends out with the advances
 * (GuideSigns), the client draws the cells and tiles (client/road/TunnelParts).
 */
public final class GuideSignAtlas {
    /** The sheet, px. */
    public static final int WIDTH = ${SW}, HEIGHT = ${SH};
    /** A cell's bottom and top over the baseline, in cap heights; a space's advance, cap heights. */
    public static final double CELL_BOTTOM = ${f(CELL_BOTTOM / CAP)}, CELL_TOP = ${f(CELL_TOP / CAP)}, SPACE = ${f(SPACE / CAP)};
    /** The characters that have cells. */
    public static final String CHARS = "${chars}";
    /** Each character's advance (its letter gap included), cap heights. */
    static final double[] ADVANCE = {
${wrap(adv, 12)}
    };
    /** Each character's cell: x, y, w, h px. */
    static final int[] CELL = {
${wrap(cells, 6)}
    };
    /** The tiles: the uniform materials, the route shields (F interstate, R route; their number), the arrows. */
    public static final String[] TILES = {${tiles.map(t => '"' + t + '"').join(', ')}};
    /** Each tile: x, y, w, h px. */
    static final int[] TILE = {
${wrap(tiles.map(t => R[t].join(', ')), 6)}
    };
    /** Each tile's size on a sign (0: a material), m. */
    static final double[] TILE_SIZE = {
${wrap(tiles.map(t => size(t).map(f).join(', ')), 6)}
    };

    private GuideSignAtlas() {}

    /** The index of character c in CHARS, -1: none. */
    public static int index(char c) { return CHARS.indexOf(c); }

    /** Character i's advance, cap heights. */
    public static double advance(int i) { return ADVANCE[i]; }

    /** Character i's cell {x, y, w, h} px. */
    public static int[] cell(int i) { return new int[]{CELL[4 * i], CELL[4 * i + 1], CELL[4 * i + 2], CELL[4 * i + 3]}; }

    /** The index of a tile, -1: none. */
    public static int tile(String name) {
        for (int i = 0; i < TILES.length; i++) if (TILES[i].equals(name)) return i;
        return -1;
    }

    /** Tile i's rect {x, y, w, h} px. */
    public static int[] tileRect(int i) { return new int[]{TILE[4 * i], TILE[4 * i + 1], TILE[4 * i + 2], TILE[4 * i + 3]}; }

    /** Tile i's width and height on a sign, m. */
    public static double[] tileSize(int i) { return new double[]{TILE_SIZE[2 * i], TILE_SIZE[2 * i + 1]}; }
}
`;
}

export function outputs() {
  const R = layout(), maps = sheet(R);
  const out = ['', '_s', '_n'].map((k, i) => [path.join(TEX, 'guide_sign' + k + '.png'), maps[i]]);
  out.push([JAVA, java(R)]);
  return {out, R, colour: maps[0]};
}

const isMain = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  const {out} = outputs();
  if (process.argv.includes('--check')) {
    for (const [file, data] of out) { const cur = fs.existsSync(file) ? fs.readFileSync(file) : null; if (!cur || !cur.equals(Buffer.isBuffer(data) ? data : Buffer.from(data))) throw new Error('stale ' + path.relative(ROOT, file)); }
    console.log('CHECK OK');
  } else {
    for (const [file, data] of out) { fs.mkdirSync(path.dirname(file), {recursive: true}); fs.writeFileSync(file, data); }
    console.log('wrote ' + out.length + ' files');
  }
}
