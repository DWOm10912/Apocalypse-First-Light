// PRAIRIE road map V1 (docs/ui/afl_map_v1_plan.md 5.4, user 2026-10-21): the start map spread on the forest home's table
// (com.antaurora.apofirstlight.block.PrairieRoadMapBlock). A fuel station's free state road map, unfolded to 60 x 45 cm
// (9.6 x 7.2 px), its print up: cream paper, pale land and forest, blue water, red and black highways, yellow towns, the
// PRAIRIE title block and a legend, 4 x 3 fold panels in the normal map; a town circled and the way to it traced in
// pencil, the pencil lying on the sheet. One 240-texel LabPBR texture (tools/cabin/cabin-art.mjs renderX, the man-made
// density): the sheet takes 144 x 108 texels (15 a pixel), the pencil a strip below.
//   models/block/map/prairie_road_map_<surface> x5   the sheet and pencil lying on each EmergencyRadio.Surface (sunk
//                                                    onto that top), print top to the north; blockstate turns by FACING
//   models/item/prairie_road_map                     the floor model, its displays
//   node tools/build-prairie-road-map-v1.mjs         -> writes every output
//   node tools/build-prairie-road-map-v1.mjs --check -> verifies every output is up to date
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {renderX} from './cabin/cabin-art.mjs';
import {N, hash, clamp, sstep, mix, mul, vn} from './mesh-trees/hd-art.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const ASSETS = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight');
const ID = 'prairie_road_map', TEX = 'apocalypse_firstlight:block/map/' + ID;
/** EmergencyRadio.Surface: how far each top lies below the cell (blocks). NIGHTSTAND = 1 - WoodFurnitureV2.Nightstand.TOP. */
const SURFACES = {floor: 0, table: 0.24, counter: 0.10, desk: 2.5 / 16, nightstand: 1 - 0.66};
/** The sheet (px, model frame, print top north) and its texels: 15 a pixel from the texture's corner. */
const SX0 = 3.2, SX1 = 12.8, SZ0 = 4.4, SZ1 = 11.6, MW = 144, MH = 108;
/** The pencil: 3 px long (19 cm), 0.14 px across, its strip at texel row PY. */
const PL = 3.0, PD = 0.14, PY = 200, PW = PL * 15;

// ------------------------------------------------------------------ the print (texels, sheet frame 0..144 x 0..108)

const PAPER = [238, 232, 214], LAND = [229, 231, 204], FOREST = [206, 222, 184], WATER = [150, 192, 218], RIVER = [96, 150, 196];
const RED = [196, 44, 38], BLACK = [44, 42, 44], TOWN = [242, 214, 122], WHITE = [250, 248, 240], GRAPHITE = [78, 78, 84];

// roads: the interstate (red, white-cased), a US highway through the circled town, state and county roads (black)
const INTERSTATE = [[4, 40], [26, 35], [50, 30], [72, 27], [90, 26], [118, 30], [140, 34]];
const US = [[96, 4], [95, 24], [96, 44], [102, 58], [110, 70], [118, 88], [126, 104]];
const STATE = [
  [[42, 84], [48, 76], [52, 70], [60, 62], [70, 54], [82, 49], [96, 44]],
  [[4, 60], [18, 61], [30, 64], [42, 67], [52, 70]],
  [[96, 44], [110, 47], [124, 52], [140, 58]],
  [[50, 30], [46, 18], [44, 4]],
];
const COUNTY = [[[4, 96], [14, 93], [24, 90], [34, 87], [42, 84]], [[42, 84], [54, 92], [62, 104]], [[110, 70], [124, 74], [140, 72]]];
const RIVER_LINE = (() => { const p = []; for (let t = 0; t <= 1.0001; t += 0.02) p.push([58 + 10 * Math.sin(t * 9) + 14 * t, 4 + 100 * t]); return p; })();
const TOWNS = [[96, 44, 5.5, 4.2], [50, 30, 4, 3], [126, 97, 3.2, 2.6], [20, 61, 2.6, 2], [122, 30, 3, 2.4], [52, 70, 2.2, 1.8]];
const LAKE = [114, 82, 9, 5];
// pencil: the way from the home (a cross) to the circled town
const HOME = [24, 90], ROUTE = [[24, 90], [34, 87], [42, 84], [48, 76], [52, 70], [60, 62], [70, 54], [82, 49], [88, 47]];
const CIRCLE = [96.5, 44, 11, 8.5];

function segD(x, y, a, b) {
  const dx = b[0] - a[0], dy = b[1] - a[1], l2 = dx * dx + dy * dy;
  const t = l2 ? clamp(((x - a[0]) * dx + (y - a[1]) * dy) / l2, 0, 1) : 0;
  return Math.hypot(x - a[0] - t * dx, y - a[1] - t * dy);
}
const lineD = (x, y, pts) => { let d = 1e9; for (let i = 0; i + 1 < pts.length; i++) d = Math.min(d, segD(x, y, pts[i], pts[i + 1])); return d; };
const ell = (x, y, e) => Math.hypot((x - e[0]) / e[2], (y - e[1]) / e[3]);
/** Coverage of a stroke of half width w at distance d (soft over half a texel). */
const ink = (d, w) => 1 - sstep(w - 0.35, w + 0.35, d);

// 3 x 5 capitals
const GLYPHS = {
  P: ['111', '101', '111', '100', '100'], R: ['110', '101', '110', '101', '101'], A: ['010', '101', '111', '101', '101'],
  I: ['111', '010', '010', '010', '111'], E: ['111', '100', '110', '100', '111'], O: ['111', '101', '101', '101', '111'],
  D: ['110', '101', '101', '101', '110'], M: ['101', '111', '111', '101', '101'], ' ': ['000', '000', '000', '000', '000'],
};
function text(x, y, str, x0, y0, k) {
  const i = Math.floor((x - x0) / k), j = Math.floor((y - y0) / k);
  if (j < 0 || j > 4 || i < 0) return false;
  const c = Math.floor(i / 4), col = i % 4;
  if (c >= str.length || col === 3) return false;
  return GLYPHS[str[c]][j][col] === '1';
}

/** The print at sheet texel (x, y): colour, smoothness, porosity, height (mm). */
function sheet(x, y) {
  let c = PAPER, s = 38, p = 44;
  const inMap = x > 4 && x < MW - 4 && y > 4 && y < MH - 4;
  if (inMap) {
    c = LAND;
    const f = vn(x * 1.6, y * 1.6, 11, 24) * 0.7 + vn(x * 1.6, y * 1.6, 12, 8) * 0.3;
    c = mix(c, FOREST, sstep(0.08, 0.3, f));
    if (ell(x, y, LAKE) < 1) c = WATER;
    c = mix(c, WATER, sstep(1.4, 1.0, ell(x, y, LAKE)));
    c = mix(c, RIVER, ink(lineD(x, y, RIVER_LINE), 0.7));
    for (const t of TOWNS) { const e = ell(x, y, t); c = mix(c, TOWN, sstep(1.05, 0.95, e)); c = mix(c, mul(TOWN, 0.72), ink(Math.abs(e - 1) * Math.min(t[2], t[3]), 0.3)); }
    for (const r of COUNTY) c = mix(c, mix(BLACK, LAND, 0.35), ink(lineD(x, y, r), 0.42));
    for (const r of STATE) c = mix(c, BLACK, ink(lineD(x, y, r), 0.62));
    c = mix(c, RED, ink(lineD(x, y, US), 0.85));
    const di = lineD(x, y, INTERSTATE);
    c = mix(c, BLACK, ink(di, 1.9)); c = mix(c, WHITE, ink(di, 1.35)); c = mix(c, RED, ink(di, 0.8));
    for (const t of TOWNS) c = mix(c, BLACK, ink(Math.hypot(x - t[0], y - t[1]), 0.55));
    // title block and legend
    if (x > 6 && x < 64 && y > 6 && y < 30) {
      c = WHITE;
      if (y > 7 && y < 20 && x > 7 && x < 63) c = RED;
      if (text(x, y, 'PRAIRIE', 8.5, 8.5, 2)) c = WHITE;
      if (text(x, y, 'ROAD MAP', 8.5, 22, 1)) c = BLACK;
      c = mix(c, BLACK, ink(Math.min(Math.abs(x - 6.5), Math.abs(x - 63.5), Math.abs(y - 6.5), Math.abs(y - 29.5)), 0.3)
        * (x > 6 && x < 64 && y > 6 && y < 30 ? 1 : 0));
    }
    if (x > 112 && x < 138 && y > 88 && y < 102) {
      c = WHITE;
      const row = (yy, col, w) => { if (Math.abs(y - yy) < w && x > 114 && x < 124) c = col; };
      row(91.5, RED, 1); row(95, RED, 0.5); row(98.5, BLACK, 0.4);
      for (let k = 0; k < 3; k++) if (x > 126 && x < 136 && Math.abs(y - (91.5 + 3.5 * k)) < 0.4) c = mix(BLACK, WHITE, 0.45);
    }
  }
  // the frame and its index ticks every 12 texels
  const fd = Math.min(Math.abs(x - 4), Math.abs(x - (MW - 4)), Math.abs(y - 4), Math.abs(y - (MH - 4)));
  if (x > 3 && x < MW - 3 && y > 3 && y < MH - 3) c = mix(c, BLACK, ink(fd, 0.4));
  const tick = (y < 4 || y > MH - 4) && x > 4 && x < MW - 4 && (x % 12) < 0.6 || (x < 4 || x > MW - 4) && y > 4 && y < MH - 4 && (y % 12) < 0.6;
  if (tick && Math.min(Math.abs(y - 4), Math.abs(y - MH + 4), Math.abs(x - 4), Math.abs(x - MW + 4)) < 2) c = mix(c, BLACK, 0.7);
  if (inMap && c !== PAPER) { s = 52; p = 30; }
  // pencil: graphite with a little pressure in it, a cross at the home, the circle drawn round once and past its start
  let g = 0;
  const pr = 0.75 + 0.25 * vn(x * 2, y * 2, 21, 12);
  g = Math.max(g, ink(lineD(x, y, ROUTE), 0.55) * pr);
  g = Math.max(g, ink(Math.min(segD(x, y, [HOME[0] - 2.4, HOME[1] - 2.4], [HOME[0] + 2.4, HOME[1] + 2.4]), segD(x, y, [HOME[0] - 2.4, HOME[1] + 2.4], [HOME[0] + 2.4, HOME[1] - 2.4])), 0.5));
  {
    const dx = (x - CIRCLE[0]) / CIRCLE[2], dy = (y - CIRCLE[1]) / CIRCLE[3], a = Math.atan2(dy, dx), r = Math.hypot(dx, dy);
    for (let turn = 0; turn < 2; turn++) {
      let t = ((a + 2.2) / (2 * Math.PI) + 1) % 1 + turn;
      if (t > 1.07) continue;
      const k = 1 + 0.025 * Math.sin(3 * a + 1.3) + 0.012 * Math.sin(7 * a + 0.4) + 0.05 * t;
      g = Math.max(g, ink(Math.abs(r - k) * Math.min(CIRCLE[2], CIRCLE[3]), 0.5) * pr);
    }
  }
  c = mix(c, GRAPHITE, g * 0.72); s = s + (120 - s) * g;
  // folds: 4 x 3 panels, mountain and valley in turn, worn pale along the crease
  let h = 0, wear = 0;
  for (let k = 1; k < 4; k++) { const d = Math.abs(x - k * MW / 4); h += (k % 2 ? 0.45 : -0.45) * Math.max(0, 1 - d / 3); wear = Math.max(wear, 1 - d / 1.6); }
  for (let k = 1; k < 3; k++) { const d = Math.abs(y - k * MH / 3); h += (k % 2 ? -0.4 : 0.4) * Math.max(0, 1 - d / 3); wear = Math.max(wear, 1 - d / 1.6); }
  c = mix(c, mul(PAPER, 1.02), clamp(wear, 0, 1) * 0.45);
  // age: a faint even yellowing, darker toward the edges
  const edge = Math.min(x, y, MW - x, MH - y);
  c = mul(c, 0.985 - 0.035 * sstep(6, 0, edge) + 0.012 * vn(x, y, 31, 30));
  return {c, s, p, h};
}

/** The pencil strip (texel row PY..PY+3): graphite tip, sharpened cedar, yellow lacquer, ferrule, eraser. */
function pencil(x, y) {
  const v = (y - PY) / 3, shade = 0.86 + 0.14 * Math.sin(v * Math.PI);
  if (x < 2) return {c: mul(GRAPHITE, shade), s: 120, p: 0};
  if (x < 6) return {c: mul(mix([214, 182, 142], [190, 150, 110], vn(x * 8, y * 8, 41, 6) * 0.5 + 0.5), shade), s: 30, p: 30};
  if (x < 38) return {c: mul([232, 182, 44], shade * (v < 0.34 || v > 0.66 ? 0.92 : 1)), s: 175, p: 0};
  if (x < 41) return {c: mul([186, 182, 168], shade * (Math.floor(x * 2) % 2 ? 0.86 : 1)), s: 165, f0: 230, p: 0};
  return {c: mul([222, 132, 128], shade), s: 28, p: 20};
}

export function texture() {
  return renderX((x, y) => {
    if (x < MW && y < MH) return sheet(x, y);
    if (y >= PY && y < PY + 3 && x < PW) return pencil(x, y);
    return {c: PAPER, s: 38, p: 44};
  });
}

// ------------------------------------------------------------------ models

const r4 = v => Math.round(v * 1e4) / 1e4;
function model(sink) {
  const b = -16 * sink, y0 = r4(b + 0.01), y1 = r4(b + 0.06), py = r4(y1 + PD);
  const v0 = r4(PY / 15), v1 = r4((PY + 2.1) / 15), u1 = r4(PW / 15), tip = r4(1.9 / 15);
  const side = {uv: [0, v0, u1, v1], texture: '#map'}, north = {uv: [u1, v0, 0, v1], texture: '#map'};
  const px0 = 8.6, pz0 = 9.9;
  return {
    ambientocclusion: false,
    textures: {map: TEX, particle: TEX},
    elements: [
      {from: [SX0, y0, SZ0], to: [SX1, y1, SZ1], faces: {up: {uv: [0, 0, MW / 15, MH / 15], texture: '#map'}}},
      {
        from: [px0, y1, pz0], to: [r4(px0 + PL), py, r4(pz0 + PD)],
        rotation: {angle: 22.5, axis: 'y', origin: [r4(px0 + PL / 2), y1, r4(pz0 + PD / 2)]},
        faces: {
          north, south: side, up: side, down: side,
          west: {uv: [0, v0, tip, v1], texture: '#map'}, east: {uv: [r4(u1 - tip), v0, u1, v1], texture: '#map'},
        },
      },
    ],
  };
}

const ITEM = {
  parent: 'apocalypse_firstlight:block/map/' + ID + '_floor',
  display: {
    gui: {rotation: [62, 0, 0], translation: [0, 5.6, 0], scale: [1.5, 1.5, 1.5]},
    ground: {rotation: [0, 0, 0], translation: [0, 2, 0], scale: [0.6, 0.6, 0.6]},
    fixed: {rotation: [90, 0, 0], translation: [0, 0, -7.5], scale: [1.4, 1.4, 1.4]},
    firstperson_righthand: {rotation: [20, -10, 0], translation: [0, 4, 0], scale: [0.6, 0.6, 0.6]},
    thirdperson_righthand: {rotation: [60, 0, 0], translation: [0, 3, 1], scale: [0.5, 0.5, 0.5]},
  },
};

function blockstate() {
  const variants = {};
  const rot = {north: 0, east: 90, south: 180, west: 270};
  for (const f of Object.keys(rot)) for (const s of Object.keys(SURFACES)) {
    const v = {model: 'apocalypse_firstlight:block/map/' + ID + '_' + s};
    if (rot[f]) v.y = rot[f];
    variants[`facing=${f},surface=${s}`] = v;
  }
  return {variants};
}

const json = o => JSON.stringify(o, null, 2) + '\n';
function outputs() {
  const [col, spec, nrm] = texture();
  const t = path.join(ASSETS, 'textures/block/map/' + ID);
  const out = [[t + '.png', col], [t + '_s.png', spec], [t + '_n.png', nrm]];
  for (const [s, k] of Object.entries(SURFACES)) out.push([path.join(ASSETS, `models/block/map/${ID}_${s}.json`), json(model(k))]);
  out.push([path.join(ASSETS, `models/item/${ID}.json`), json(ITEM)]);
  out.push([path.join(ASSETS, `blockstates/${ID}.json`), json(blockstate())]);
  return out;
}

const isMain = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  const out = outputs();
  if (process.argv.includes('--check')) {
    const stale = out.filter(([file, data]) => { const cur = fs.existsSync(file) ? fs.readFileSync(file) : null; return !cur || !cur.equals(Buffer.isBuffer(data) ? data : Buffer.from(data)); });
    for (const [file] of stale) console.log('stale ' + path.relative(ROOT, file));
    if (stale.length) process.exit(1);
    console.log(`CHECK OK (${out.length} outputs)`);
  } else {
    for (const [file] of out) fs.mkdirSync(path.dirname(file), {recursive: true});
    for (const [file, data] of out) fs.writeFileSync(file, data);
    console.log(`wrote ${out.length} files`);
  }
}
