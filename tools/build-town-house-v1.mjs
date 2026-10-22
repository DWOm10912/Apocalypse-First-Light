// Town House A V1 (docs/worldgen/town_buildings_blocks_v1.md, batch 1, 2026-10-21): the blocks of the small town house
// ("住宅 A", minimal traditional, docs/worldgen/town_buildings_concept_v1.md). The same Mesh kit and density as the forest
// home's exterior (tools/build-cabin-exterior-v1.mjs): 240-texel LabPBR textures (tools/town/town-art.mjs), pieces drawn
// for the north side and turned in game (CabinMeshModel, com.antaurora.apofirstlight.townhouse.TownHouseBlocks).
//   JSON blocks
//     lap_siding_<colour> x5     painted lap siding outside, plaster inside: per face a boolean (true = outside), white
//     red_brick_common           corner boards where two outside faces meet; the brick the same with no corner boards
//     parged_foundation          grey cement over the foundation block
//     linoleum_floor             marbleised linoleum on the red oak floor boards
//     plaster_ceiling            2 px plaster at the top of the cell (14..16), the attic side shows joists and wool
//     plaster_partition          4 px (6..10) plaster partition joining like a pane, white skirting where it stands on
//                                a floor, its own ceiling slab in the top storey (ceiling = true)
//     limestone_coping           the chimney's 2 px cap stone, 1 px overhang, ends, a clay flue
//     smooth_concrete            smooth residential concrete as a full block (the stoop's landing; before 2026-10-21
//                                stoop_concrete, blowholed)
//   Mesh blocks
//     asphalt_shingle_roof_<c> x3  the cabin's 1:2 roof cells (lower / upper / ridge) in 3-tab shingles: white fascia and
//                                drip edge, a starter course over it, rake shingles over a white barge board, a cap on the
//                                ridge; closed-cut valleys where a front gable meets the main roof (valley neg / pos /
//                                ridge); under a lap, brick or plaster wall the gable infill in that wall's faces
//     steep_asphalt_shingle_roof_<c> x3  Town House B's 1:1 roof cell (one shape, y = z): the same edges, half the ridge cap
//                                on each of the two cells meeting at the ridge, twelve shingle courses a (1.41 m) tile
//     white_double_hung_window   1-over-1 or 6-over-6 sashes, wood casings, sill, stool and apron, white; the head left
//                                out under an eave; louvred shutters either side on request (white, green, wine, black)
//     white_picture_window       fixed glass, joined to the neighbours (no frame between cells), framed at the edges
//     house_front_door           panelled with three small lights, painted (white, green, wine, black), brass knob; a
//                                colonial surround (pilasters, frieze, cornice, pediment) on request
//     house_back_door            nine lights over two panels, white, a wood screen door outside on request
//     interior_panel_door        five-panel door in a partition (frame centred in the cell), glass knob
//     wrought_iron_railing       black square-bar railing hugging a cell edge, straight or down a stair, posts at ends
//     concrete_stoop_steps       a two-cell flight of six 1/6 risers (upper / lower), cast concrete, sides left out beside
//                                a step of the same flight
// Writes textures (block/town/*.png + _s / _n), mesh pieces (cabin_mesh/roof/shingle_*, roof/under_valley_*, town/*),
// models, blockstates, loot tables and TownShapeData.java. Tags, item mass and lang are edited by hand; --check audits them.
//   node tools/build-town-house-v1.mjs            -> writes every output
//   node tools/build-town-house-v1.mjs --check    -> verifies every output is up to date and the hand-edited data
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {renderX, TEXTURES, LAP, SHINGLE, PAINT} from './town/town-art.mjs';
import {Piece, box, solid, cylinder, turned, mirroredX, clipped, baked, serialise} from './cabin/cabin-geo.mjs';
import {polyOut, ys as cabinYs, RT, SHAPES} from './build-cabin-exterior-v1.mjs';

/**
 * The cabin's roof surfaces plus Town House B's 1:1 cell (2026-10-21): 'steep' rises a block per block, y = z (the cabin's
 * lower / upper / ridge rise half a block). slopeStrip and slopeBandX as the cabin's, on these surfaces.
 */
const ys = (shape, z) => shape === 'steep' ? z : cabinYs(shape, z);
const zsOf = shape => shape === 'ridge' ? [0, 0.5, 1] : [0, 1];
function slopeStrip(pc, t, shape, x0, x1, za, zb, dy, up = true) {
  const zs = [za, ...zsOf(shape).filter(z => z > za + 1e-9 && z < zb - 1e-9), zb];
  for (let i = 0; i + 1 < zs.length; i++) {
    const a = zs[i], b = zs[i + 1];
    polyOut(pc, t, [[x0, ys(shape, a) + dy, a], [x1, ys(shape, a) + dy, a], [x1, ys(shape, b) + dy, b], [x0, ys(shape, b) + dy, b]], [0, up ? 1 : -1, 0], {proj: 'y'});
  }
}
function slopeBandX(pc, t, shape, x, out, za, zb, lo, hi) {
  const zs = [za, ...zsOf(shape).filter(z => z > za + 1e-9 && z < zb - 1e-9), zb];
  for (let i = 0; i + 1 < zs.length; i++) {
    const a = zs[i], b = zs[i + 1];
    polyOut(pc, t, [[x, ys(shape, a) + lo, a], [x, ys(shape, b) + lo, b], [x, ys(shape, b) + hi, b], [x, ys(shape, a) + hi, a]], [out, 0, 0], {proj: 'x'});
  }
}

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const ASSETS = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight');
const DATA = path.join(ROOT, 'src/main/resources/data');
const NS = 'apocalypse_firstlight:', TEX = NS + 'block/town/', CAB = NS + 'block/cabin/';
const json = v => JSON.stringify(v, null, 2) + '\n';
/** Textures of the cabin set used as they are: glass, the roof boards under a roof, galvanised flashing, the pipe, oak. */
const GLASS = CAB + 'cabin_glass', BOARDS = CAB + 'oak_roof_boards', GALV = CAB + 'roof_galvanized', PIPE = CAB + 'stove_pipe', OAK = CAB + 'oak_trim';
const RED_OAK = NS + 'block/wood/red_oak_planks';
const TRIM = 'trim_white', TRIM_V = 'trim_white_v', DRIP = 'drip_white', IRON = 'iron_black';

const LAPS = Object.keys(LAP), SHINGLES = Object.keys(SHINGLE), PAINTS = ['white', ...Object.keys(PAINT)];
const LAP_NAMES = {white: ['White', '白色'], cream: ['Cream', '奶油色'], sage: ['Sage', '灰绿色'], blue_gray: ['Blue Gray', '灰蓝色'], tan: ['Tan', '浅褐色']};
const SH_NAMES = {charcoal: ['Charcoal', '深灰'], weathered_brown: ['Weathered Brown', '旧褐色'], slate: ['Slate', '石板灰']};

/** Every block of batch 1: id, kind (faced / json / mesh), mining (axe / pickaxe, tier), item mass (kg), names. */
export const BLOCKS = [
  ...LAPS.map(c => ({id: 'lap_siding_' + c, kind: 'faced', tex: 'lap_' + c, corners: true, tool: 'axe', mass: 0.12, en: LAP_NAMES[c][0] + ' Lap Siding', zh: LAP_NAMES[c][1] + '搭接木板'})),
  ...SHINGLES.map(c => ({id: 'asphalt_shingle_roof_' + c, kind: 'mesh', tool: 'axe', mass: 0.1, en: SH_NAMES[c][0] + ' Asphalt Shingle Roof', zh: SH_NAMES[c][1] + '沥青瓦屋面'})),
  ...SHINGLES.map(c => ({id: 'steep_asphalt_shingle_roof_' + c, kind: 'mesh', tool: 'axe', mass: 0.12, en: SH_NAMES[c][0] + ' Steep Asphalt Shingle Roof', zh: SH_NAMES[c][1] + '沥青瓦陡屋面'})),
  {id: 'white_double_hung_window', kind: 'mesh', tool: 'axe', mass: 0.4, en: 'White Double-Hung Window', zh: '白色双悬窗'},
  {id: 'white_picture_window', kind: 'mesh', tool: 'axe', mass: 0.5, en: 'White Picture Window', zh: '白色大玻璃窗'},
  {id: 'house_front_door', kind: 'mesh', tool: 'axe', mass: 1.5, en: 'House Front Door', zh: '住宅前门'},
  {id: 'house_back_door', kind: 'mesh', tool: 'axe', mass: 1.5, en: 'House Back Door', zh: '住宅后门'},
  {id: 'interior_panel_door', kind: 'mesh', tool: 'axe', mass: 1.5, en: 'Interior Panel Door', zh: '室内门'},
  {id: 'wrought_iron_railing', kind: 'mesh', tool: 'pickaxe', tier: 'stone', mass: 0.8, en: 'Wrought Iron Railing', zh: '铁艺栏杆'},
  {id: 'parged_foundation', kind: 'json', tool: 'pickaxe', mass: 0.25, en: 'Parged Foundation', zh: '抹灰基础墙'},
  {id: 'plaster_partition', kind: 'json', tool: 'pickaxe', mass: 0.15, en: 'Plaster Partition', zh: '灰泥隔墙'},
  {id: 'plaster_ceiling', kind: 'json', tool: 'pickaxe', mass: 0.15, en: 'Plaster Ceiling', zh: '灰泥顶棚'},
  {id: 'linoleum_floor', kind: 'json', tool: 'axe', mass: 0.15, en: 'Linoleum Floor', zh: '油毡地面'},
  {id: 'red_brick_common', kind: 'faced', tex: 'red_brick_common', corners: false, tool: 'pickaxe', mass: 0.3, en: 'Common Red Brick', zh: '普通红砖'},
  {id: 'limestone_coping', kind: 'json', tool: 'pickaxe', mass: 0.25, en: 'Limestone Coping', zh: '石灰石压顶'},
  {id: 'smooth_concrete', kind: 'json', tool: 'pickaxe', mass: 0.25, en: 'Smooth Concrete', zh: '平滑混凝土'},
  {id: 'concrete_stoop_steps', kind: 'mesh', tool: 'pickaxe', mass: 0.4, en: 'Concrete Stoop Steps', zh: '混凝土门廊台阶'},
];

// =============================================================================================== shingle roofs
// The cabin's roof cells (north frame: the eave at z 0, the slab RT thick under the surface ys). The shingles' relief is
// in the textures (v down the slope from the eave, the butts at each course's eave side); the geometry adds the edges:
// at the eave a white fascia, a white drip edge in front of it and the starter course running 6 cm out over it (its top
// a ramp from the field down to the edge, no step), at a gable the barge board, the rake drip and the shingles 5.5 cm out.
const SH_LIFT = 0.008, EAVE_OUT = 0.06, RAKE_OUT = 0.055, CAP_HALF = 0.15, CAP_UP = 0.022;
/** The shingle field of a cell; on a ridge the front half drawn and turned round for the back (its butts face down too). */
function shingleField(pc, t, shape) {
  if (shape !== 'ridge') { slopeStrip(pc, t, shape, 0, 1, 0, 1, 0, true); return pc; }
  const half = new Piece('h');
  slopeStrip(half, t, shape, 0, 1, 0, 0.5, 0, true);
  const b = baked(half);
  return pc.merge(b).merge(turned(b, 2));
}
/** The ridge cap: cap shingles 15 cm down each side, 2.2 cm thick (x a..b), lapping along x. */
function ridgeCap(pc, t, x0 = 0, x1 = 1) {
  const a = 0.5 - CAP_HALF, b = 0.5 + CAP_HALF, sh = 'ridge';
  slopeStrip(pc, t, sh, x0, x1, a, b, CAP_UP, true);
  for (const [z, out] of [[a, -1], [b, 1]]) polyOut(pc, t, [[x0, ys(sh, z) - 0.004, z], [x1, ys(sh, z) - 0.004, z], [x1, ys(sh, z) + CAP_UP, z], [x0, ys(sh, z) + CAP_UP, z]], [0, 0, out], {proj: 'z'});
  return pc;
}
/**
 * The fascia's and the drip edge's tops under the starter course: just under its underside where they stand (z -0.035,
 * -0.047), so no gap shows between them; the cabin's slopes keep their old constants.
 */
const eaveTops = shape => shape === 'steep' ? [ys(shape, -0.035) - 0.0075, ys(shape, -0.047) - 0.0065] : [ys(shape, 0) - 0.025, ys(shape, 0) - 0.03];
/** The field's texture: the steep roof's has twelve courses a tile (its tile runs 1.41 m down the slope). */
const fieldTex = (c, shape) => shape === 'steep' ? `shingle_${c}_steep` : 'shingle_' + c;
const fieldPiece = (c, shape) => { const pc = shingleField(new Piece('f'), 'shingle_' + c, shape); if (shape === 'ridge') ridgeCap(pc, `shingle_${c}_ridge`); return pc; };
const underPiece = shape => { const pc = new Piece('u'); slopeStrip(pc, BOARDS, shape, 0, 1, 0, 1, -RT, false); return pc; };

function eavePiece(name, t, shape) {
  const pc = new Piece(name), base = ys(shape, 0), y6 = ys(shape, -EAVE_OUT), [fa, dr] = eaveTops(shape);
  box(pc, TRIM, [0, base - RT - 0.06, -0.035], [1, fa, 0], 'nudswe');   // fascia; its back below the boards
  box(pc, DRIP, [0, dr - 0.03, -0.047], [1, dr, -0.035], 'nudwe');   // drip edge
  polyOut(pc, t, [[0, y6 + SH_LIFT, -EAVE_OUT], [1, y6 + SH_LIFT, -EAVE_OUT], [1, base, 0], [0, base, 0]], [0, 1, 0], {proj: 'y'});
  polyOut(pc, t, [[0, y6 - 0.006, -EAVE_OUT], [1, y6 - 0.006, -EAVE_OUT], [1, y6 + SH_LIFT, -EAVE_OUT], [0, y6 + SH_LIFT, -EAVE_OUT]], [0, 0, -1], {proj: 'z'});
  slopeStrip(pc, t, shape, 0, 1, -EAVE_OUT, 0, -0.006, false);
  return pc;
}
/** The gable edge at x 0 (neg): barge board, rake drip, the shingle edge (a ramp in x up to the edge), the cap's end on a ridge. */
function rakePiece(name, c, shape) {
  const pc = new Piece(name), t = fieldTex(c, shape), zs = shape === 'ridge' ? [0, 0.5, 1] : [0, 1];
  slopeBandX(pc, TRIM, shape, -0.035, -1, 0, 1, -RT - 0.08, -0.006);
  slopeBandX(pc, TRIM, shape, 0, 1, 0, 1, -RT - 0.08, -RT);
  slopeStrip(pc, TRIM, shape, -0.035, 0, 0, 1, -RT - 0.08, false);
  slopeBandX(pc, DRIP, shape, -0.047, -1, 0, 1, -0.04, -0.012);
  slopeStrip(pc, DRIP, shape, -0.047, -0.035, 0, 1, -0.04, false);
  slopeStrip(pc, DRIP, shape, -0.047, -0.035, 0, 1, -0.012, true);
  for (let i = 0; i + 1 < zs.length; i++) {
    const a = zs[i], b = zs[i + 1];
    polyOut(pc, t, [[-RAKE_OUT, ys(shape, a) + SH_LIFT, a], [0, ys(shape, a), a], [0, ys(shape, b), b], [-RAKE_OUT, ys(shape, b) + SH_LIFT, b]], [0, 1, 0], {proj: 'y'});
  }
  slopeBandX(pc, t, shape, -RAKE_OUT, -1, 0, 1, -0.006, SH_LIFT);
  slopeStrip(pc, t, shape, -RAKE_OUT, -0.035, 0, 1, -0.006, false);
  if (shape === 'ridge') {   // the cap runs out over the rake shingles, its end closed
    const a = 0.5 - CAP_HALF, b = 0.5 + CAP_HALF, ct = `shingle_${c}_ridge`;
    ridgeCap(pc, ct, -RAKE_OUT, 0);
    for (const [z0, z1] of [[a, 0.5], [0.5, b]])
      polyOut(pc, ct, [[-RAKE_OUT, ys(shape, z0) + SH_LIFT, z0], [-RAKE_OUT, ys(shape, z1) + SH_LIFT, z1], [-RAKE_OUT, ys(shape, z1) + CAP_UP, z1], [-RAKE_OUT, ys(shape, z0) + CAP_UP, z0]], [-1, 0, 0], {proj: 'x'});
  }
  return pc;
}
/** Where the eave meets a gable (x 0): the fascia's and barge board's corner, the drip's, the shingle corner over both. */
function eaveCornerPiece(name, t, shape) {
  const pc = new Piece(name), base = ys(shape, 0), y6 = ys(shape, -EAVE_OUT), [fa, dr] = eaveTops(shape);
  box(pc, TRIM, [-0.035, base - RT - 0.08, -0.035], [0, fa, 0], 'nwud');
  box(pc, DRIP, [-0.047, dr - 0.03, -0.047], [0, dr, -0.035], 'nwud');
  box(pc, DRIP, [-0.047, dr - 0.03, -0.035], [-0.035, dr, 0], 'wud');
  const A = [-RAKE_OUT, y6 + SH_LIFT, -EAVE_OUT], B = [0, y6 + SH_LIFT, -EAVE_OUT], C = [0, base, 0], D = [-RAKE_OUT, base + SH_LIFT, 0];
  polyOut(pc, t, [A, B, C], [0, 1, 0], {proj: 'y'});
  polyOut(pc, t, [A, C, D], [0, 1, 0], {proj: 'y'});
  polyOut(pc, t, [[-RAKE_OUT, y6 - 0.006, -EAVE_OUT], [0, y6 - 0.006, -EAVE_OUT], B, A], [0, 0, -1], {proj: 'z'});
  polyOut(pc, t, [[-RAKE_OUT, y6 - 0.006, -EAVE_OUT], [-RAKE_OUT, base - 0.006, 0], D, A], [-1, 0, 0], {proj: 'x'});
  slopeStrip(pc, t, shape, -RAKE_OUT, 0, -EAVE_OUT, 0, -0.006, false);
  return pc;
}
/** The high edge against a wall: a closure under it, step flashing over the last course and 10 cm up the wall. */
function topPiece(name, shape) {
  const pc = new Piece(name), hi = ys(shape, 1), y9 = ys(shape, 0.9);
  polyOut(pc, TRIM, [[0, hi - RT, 1], [1, hi - RT, 1], [1, hi, 1], [0, hi, 1]], [0, 0, 1], {proj: 'z'});
  slopeStrip(pc, GALV, shape, 0, 1, 0.9, 0.996, 0.012, true);
  polyOut(pc, GALV, [[0, y9, 0.9], [1, y9, 0.9], [1, y9 + 0.012, 0.9], [0, y9 + 0.012, 0.9]], [0, 0, -1], {proj: 'z'});
  polyOut(pc, GALV, [[0, ys(shape, 0.996) + 0.012, 0.996], [1, ys(shape, 0.996) + 0.012, 0.996], [1, hi + 0.1, 0.996], [0, hi + 0.1, 0.996]], [0, 0, -1], {proj: 'z'});
  return pc;
}
/** A pipe through the roof (the cabin's stove pipe, a vent): the pipe, a galvanised boot and plate on the shingles. */
function pipePiece(name, shape) {
  const pc = new Piece(name);
  cylinder(pc, PIPE, PIPE, 'y', [0.5, 0.5], 0, 1, 0.075, 16);
  slopeStrip(pc, GALV, shape, 0.25, 0.75, 0.25, 0.75, 0.006, true);
  const ring = (r, dy) => Array.from({length: 16}, (_, i) => { const a = i / 16 * Math.PI * 2, x = 0.5 + Math.cos(a) * r, z = 0.5 + Math.sin(a) * r; return [x, ys(shape, z) + dy, z]; });
  const outer = ring(0.13, 0.01), inner = ring(0.077, 0.09);
  for (let i = 0; i < 16; i++) {
    const j = (i + 1) % 16, mid = [Math.cos((i + 0.5) / 16 * Math.PI * 2), 0.6, Math.sin((i + 0.5) / 16 * Math.PI * 2)];
    polyOut(pc, GALV, [outer[i], outer[j], inner[j], inner[i]], mid, {uv: [[i / 16, 1], [(i + 1) / 16, 1], [(i + 1) / 16, 0.8], [i / 16, 0.8]]});
  }
  return pc;
}
/**
 * A valley cell (Roof VALLEY): the main roof (north frame) meets a front gable's slope, turned a quarter, along the
 * diagonal where the two surfaces are the same height. neg: the gable's west-facing slope (turned 3) toward x 1, the main
 * roof toward x 0, the valley from (0, 0) to (1, 1); pos: mirrored, the gable's east-facing slope (turned 1), the valley
 * from (1, 0) to (0, 1); ridge: the gable's ridge (turned 1) runs into an upper cell of the main roof, a valley each
 * side of x 0.5, the cap running on 5 cm into the main roof (under its shingles). Closed cut: no metal shows.
 */
const VALLEYS = ['neg', 'pos'];
function valleyPiece(name, make, side, shape, cap) {
  const pc = new Piece(name), main = make(shape), front = turned(baked(make(shape)), side === 'neg' ? 3 : 1);
  if (side === 'neg') pc.merge(clipped(main, [[[1, 0, -1], 0]])).merge(clipped(front, [[[-1, 0, 1], 0]]));
  else pc.merge(clipped(main, [[[-1, 0, -1], -1]])).merge(clipped(front, [[[1, 0, 1], 1]]));
  return pc;
}
function valleyRidgePiece(name, make, capOf) {
  const pc = new Piece(name), main = make('upper'), front = turned(baked(make('ridge')), 1);
  const L = [[1, 0, 0], 0.5], R = [[-1, 0, 0], -0.5];
  pc.merge(clipped(main, [L, [[1, 0, -1], 0]])).merge(clipped(main, [R, [[-1, 0, -1], -1]]));
  pc.merge(clipped(front, [L, [[-1, 0, 1], 0]])).merge(clipped(front, [R, [[1, 0, 1], 1]]));
  if (capOf) {
    const cap = turned(baked(capOf()), 1);
    pc.merge(clipped(cap, [L, [[-1, 0, 1], 0.05]])).merge(clipped(cap, [R, [[1, 0, 1], 1.05]]));
  }
  return pc;
}
/** Gable infill: one face of the cell under a roof cell (cut by the boards' plane) per material and side of the north frame. */
export const INFILL_MATS = [...LAPS.map(c => ['lap_' + c, 'lap_' + c]), ['brick_red', 'red_brick_common'], ['plaster', 'plaster']];
const SIDES = ['n', 'e', 's', 'w'];
const infillPlanes = sh => sh === 'ridge' ? [[[0, 1, -0.5], 0.5 - RT], [[0, 1, 0.5], 1.0 - RT]] : [[[0, 1, -0.5], ys(sh, 0) - RT]];

/**
 * Town House B's 1:1 roof (SteepShingleRoof, 2026-10-21): one cell shape, y = z, the eave at z 0 and the high edge at z 1;
 * the ridge on the cell edge between two cells facing apart, each carrying its half of the cap (15 cm down its side, its
 * lower edge closed) and, at a rake, the cap's run out over the rake shingles with its end closed. Field, boards, eave,
 * rakes, corners, the top against a wall and the gable infill as the 1:2 cells.
 */
function steepCapPiece(name, c) {
  const pc = new Piece(name), t = `shingle_${c}_ridge`, sh = 'steep', a = 1 - CAP_HALF;
  slopeStrip(pc, t, sh, 0, 1, a, 1, CAP_UP, true);
  polyOut(pc, t, [[0, ys(sh, a) - 0.004, a], [1, ys(sh, a) - 0.004, a], [1, ys(sh, a) + CAP_UP, a], [0, ys(sh, a) + CAP_UP, a]], [0, 0, -1], {proj: 'z'});
  return pc;
}
function steepCapRakePiece(name, c) {
  const pc = new Piece(name), t = `shingle_${c}_ridge`, sh = 'steep', a = 1 - CAP_HALF;
  slopeStrip(pc, t, sh, -RAKE_OUT, 0, a, 1, CAP_UP, true);
  polyOut(pc, t, [[-RAKE_OUT, ys(sh, a) + SH_LIFT, a], [0, ys(sh, a), a], [0, ys(sh, a) + CAP_UP, a], [-RAKE_OUT, ys(sh, a) + CAP_UP, a]], [0, 0, -1], {proj: 'z'});
  polyOut(pc, t, [[-RAKE_OUT, ys(sh, a) + SH_LIFT, a], [-RAKE_OUT, ys(sh, 1) + SH_LIFT, 1], [-RAKE_OUT, ys(sh, 1) + CAP_UP, 1], [-RAKE_OUT, ys(sh, a) + CAP_UP, a]], [-1, 0, 0], {proj: 'x'});
  return pc;
}
const STEEP_INFILL = [[[0, 1, -1], -RT]];
function steepRoofPieces() {
  const P = [], sh = 'steep';
  for (const c of SHINGLES) {
    const m = 'shingle_' + c, t = fieldTex(c, sh);
    const field = new Piece(`roof/${m}_steep`);
    slopeStrip(field, t, sh, 0, 1, 0, 1, 0, true);
    P.push(field);
    const rake = rakePiece(`roof/${m}_rake_neg_steep`, c, sh);
    P.push(rake, mirroredX(rake, `roof/${m}_rake_pos_steep`));
    P.push(eavePiece(`roof/${m}_eave_steep`, t, sh));
    const corner = eaveCornerPiece(`roof/${m}_eave_corner_neg_steep`, t, sh);
    P.push(corner, mirroredX(corner, `roof/${m}_eave_corner_pos_steep`));
    P.push(topPiece(`roof/${m}_top_steep`, sh), steepCapPiece(`roof/${m}_steep_cap`, c));
    const capRake = steepCapRakePiece(`roof/${m}_steep_cap_rake_neg`, c);
    P.push(capRake, mirroredX(capRake, `roof/${m}_steep_cap_rake_pos`));
  }
  const under = new Piece('roof/under_steep');
  slopeStrip(under, BOARDS, sh, 0, 1, 0, 1, -RT, false);
  P.push(under);
  for (const [m, t] of INFILL_MATS) SIDES.forEach(s => P.push(clipped(box(new Piece('x'), t, [0, 0, 0], [1, 1, 1], s), STEEP_INFILL, `town/infill_${m}_steep_${s}`)));
  return P.filter(p => p.polys.length);   // the eave side (n) lies wholly above the boards: no piece
}

function roofPieces() {
  const P = [];
  for (const c of SHINGLES) {
    const t = 'shingle_' + c, m = 'shingle_' + c;
    for (const sh of SHAPES) {
      P.push(Object.assign(fieldPiece(c, sh), {name: `roof/${m}_${sh}`}));
      const rake = rakePiece(`roof/${m}_rake_neg_${sh}`, c, sh);
      P.push(rake, mirroredX(rake, `roof/${m}_rake_pos_${sh}`));
      if (sh === 'ridge') continue;
      P.push(eavePiece(`roof/${m}_eave_${sh}`, t, sh));
      const corner = eaveCornerPiece(`roof/${m}_eave_corner_neg_${sh}`, t, sh);
      P.push(corner, mirroredX(corner, `roof/${m}_eave_corner_pos_${sh}`));
      P.push(topPiece(`roof/${m}_top_${sh}`, sh), pipePiece(`roof/${m}_pipe_${sh}`, sh));
      for (const side of VALLEYS) P.push(valleyPiece(`roof/${m}_valley_${side}_${sh}`, s => shingleField(new Piece('f'), t, s), side, sh));
    }
    P.push(valleyRidgePiece(`roof/${m}_valley_ridge`, s => shingleField(new Piece('f'), t, s), () => ridgeCap(new Piece('c'), `shingle_${c}_ridge`)));
  }
  for (const sh of ['lower', 'upper']) for (const side of VALLEYS) P.push(valleyPiece(`roof/under_valley_${side}_${sh}`, underPiece, side, sh));
  P.push(valleyRidgePiece('roof/under_valley_ridge', underPiece, null));
  for (const sh of SHAPES) for (const [m, t] of INFILL_MATS) SIDES.forEach(s =>
    P.push(clipped(box(new Piece('x'), t, [0, 0, 0], [1, 1, 1], s), infillPlanes(sh), `town/infill_${m}_${sh}_${s}`)));
  return P.filter(p => p.polys.length);   // a lower cell's eave side lies wholly above its boards: no piece (ShingleRoof skips it)
}

// =============================================================================================== windows
// North frame: the outside at z < 0 (Window facing = the outside). A flat wall (the siding's relief is in its texture):
// casings 2.2 cm proud of the face outside, 1.8 cm inside, linings through the 1 m opening, the sashes near the outside
// face (upper sash in the outer track), the sill outside, stool and apron inside. Every frame is one closed solid().
const LIN = 0.04, ST = 0.05, SASH_U = [0.05, 0.095], SASH_L = [0.10, 0.145];
/**
 * A window part's frame boxes (single, bottom, top). eave: a roof's eave over the wall above it (HouseWindow, 2026-10-21):
 * the outside head casing and drip cap would stand out through the eave's starter course, so the side casings stop at
 * 0.95, inside the fascia that comes down over the window's head (from 0.74), and no head shows outside.
 */
function windowFrame(part, eave = false) {
  const sill = part !== 'top', head = part !== 'bottom', top = head ? (eave ? 0.95 : 0.94) : 1, bot = sill ? 0.04 : 0, ibot = sill ? 0.035 : 0;
  const S = [];
  if (sill) S.push([TRIM, [-0.07, 0, -0.06], [1.07, 0.04, 0.1]], [TRIM, [-0.07, 0, 0.9], [1.07, 0.035, 1.06]], [TRIM, [LIN, 0, 0.1], [1 - LIN, 0.02, 0.9]],
    [TRIM, [-0.05, -0.07, 1], [1.05, 0, 1.016]]);
  if (head && !eave) S.push([TRIM, [-0.07, 0.94, -0.026], [1.07, 1.06, 0]], [TRIM, [-0.08, 1.06, -0.04], [1.08, 1.08, 0]]);
  if (head) S.push([TRIM, [0, 0.96, 0], [1, 1, 1]], [TRIM, [-0.07, 0.94, 1], [1.07, 1.06, 1.022]]);
  S.push([TRIM_V, [0, 0, 0], [LIN, 1, 1]], [TRIM_V, [1 - LIN, 0, 0], [1, 1, 1]],
    [TRIM_V, [-0.05, bot, -0.022], [LIN, top, 0]], [TRIM_V, [1 - LIN, bot, -0.022], [1.05, top, 0]],
    [TRIM_V, [-0.05, ibot, 1], [LIN, top, 1.018]], [TRIM_V, [1 - LIN, ibot, 1], [1.05, top, 1.018]]);
  return S;
}
/** The sashes of a part: [z0, z1, y0, y1, bottom rail, top rail]. */
function windowSashes(part) {
  if (part === 'single') return [[...SASH_L, 0.02, 0.52, [0.02, 0.1], [0.48, 0.52]], [...SASH_U, 0.48, 0.96, [0.48, 0.52], [0.9, 0.96]]];
  if (part === 'bottom') return [[...SASH_L, 0.02, 1.03, [0.02, 0.1], [0.97, 1.03]]];
  return [[...SASH_U, -0.03, 0.96, [-0.03, 0.03], [0.9, 0.96]]];
}
/** One sash into S (stiles, rails, muntins for six lights) and its glass into pc. */
function sash(pc, S, [z0, z1, y0, y1, br, tr], six) {
  const a = LIN, b = 1 - LIN;
  S.push([TRIM_V, [a, y0, z0], [a + ST, y1, z1]], [TRIM_V, [b - ST, y0, z0], [b, y1, z1]], [TRIM, [a + ST, br[0], z0], [b - ST, br[1], z1]], [TRIM, [a + ST, tr[0], z0], [b - ST, tr[1], z1]]);
  const gx0 = a + ST, gx1 = b - ST, gy0 = br[1], gy1 = tr[0], m = 0.009;
  if (six) {
    for (const k of [1, 2]) { const x = gx0 + (gx1 - gx0) * k / 3; S.push([TRIM_V, [x - m, gy0, z0 + 0.008], [x + m, gy1, z1 - 0.008]]); }
    const y = (gy0 + gy1) / 2;
    S.push([TRIM, [gx0, y - m, z0 + 0.008], [gx1, y + m, z1 - 0.008]]);
  }
  const zm = (z0 + z1) / 2;
  box(pc, GLASS, [gx0, gy0, zm], [gx1, gy1, zm], 'ns');
}
const LITES = ['one_over_one', 'six_over_six'], PARTS = ['single', 'bottom', 'top'];
/** The parts drawn: the three, and single / top under an eave. */
const WINDOW_VIEWS = [['single', false], ['bottom', false], ['top', false], ['single', true], ['top', true]];
function windowPieces() {
  const P = [];
  for (const lites of LITES) for (const [part, eave] of WINDOW_VIEWS) {
    const pc = new Piece(`town/window_${lites}_${part}${eave ? '_eave' : ''}`), S = windowFrame(part, eave);
    for (const s of windowSashes(part)) sash(pc, S, s, lites === 'six_over_six');
    P.push(solid(pc, S));
  }
  return [...P, ...shutterPieces()];
}

/**
 * Louvred shutters either side of a double-hung window (HouseWindow shutters, Town House B, 2026-10-21): painted pine,
 * 40 cm wide (narrower than half the sash, as the fixed shutters of the 1940s were), 2.5 cm thick, 6 mm off the wall
 * outside the casings. Stiles 5 cm, rails top and bottom and at the meeting rail, fixed louvres between them sloping down
 * to the outside (3.5 cm pitch, each lapping the next), two black strap hinges on the window side, a shutter dog holding
 * the outer stile's foot. Over the window's full height (single 0.04 .. 0.94, two cells 0.04 .. 1.94), cut per cell; under
 * an eave they end at 0.72 / 1.72, below the fascia. North frame as the window (outside at z < 0); right shutter x 1.06 ..
 * 1.46, the left one mirrored.
 */
const SHUTTER_PAINTS = PAINTS, SHUT_X = [1.06, 1.46], SHUT_Z = [-0.031, -0.006], SHUT_ST = 0.05, LOUVRE = 0.035;
function shutterParts(single, eave) {
  const y0 = 0.04, y1 = single ? (eave ? 0.72 : 0.94) : (eave ? 1.72 : 1.94);
  const rails = single ? [[y0, y0 + 0.07], [y1 - 0.06, y1]] : [[y0, y0 + 0.08], [0.96, 1.04], [y1 - 0.06, y1]];
  return {y0, y1, rails};
}
function shutterRight(paint, single, eave) {
  const [xa, xb] = SHUT_X, [z0, z1] = SHUT_Z, {y0, y1, rails} = shutterParts(single, eave), S = [], slats = [];
  S.push([paint, [xa, y0, z0], [xa + SHUT_ST, y1, z1]], [paint, [xb - SHUT_ST, y0, z0], [xb, y1, z1]]);
  for (const [a, b] of rails) S.push([paint, [xa + SHUT_ST, a, z0], [xb - SHUT_ST, b, z1]]);
  for (const Y of [y0 + 0.12, y1 - 0.14]) S.push([IRON, [xa, Y, z0 - 0.003], [xa + 0.09, Y + 0.016, z0]]);   // strap hinges
  // the shutter dog: a stem on the wall past the outer stile, its tongue over the stile's face
  S.push([IRON, [xb + 0.006, y0 + 0.06, -0.012], [xb + 0.018, y0 + 0.14, 0]], [IRON, [xb + 0.006, y0 + 0.095, z0], [xb + 0.018, y0 + 0.107, -0.012]],
    [IRON, [xb - 0.03, y0 + 0.095, z0 - 0.009], [xb + 0.018, y0 + 0.107, z0]]);
  for (let i = 0; i + 1 < rails.length; i++) {
    const a = rails[i][1], b = rails[i + 1][0], n = Math.floor((b - a) / LOUVRE), pitch = (b - a) / n;
    for (let k = 0; k < n; k++) slats.push(a + pitch * (k + 0.5));
  }
  return {S, slats};
}
/** One louvre at yc between the stiles: a 6 mm board 1.7 cm deep, its outside edge 3.2 cm lower than its inside edge. */
function louvre(pc, t, yc) {
  const x0 = SHUT_X[0] + SHUT_ST, x1 = SHUT_X[1] - SHUT_ST, zf = -0.027, zb = -0.010;
  const fb = [yc - 0.019, zf], ft = [yc - 0.013, zf], bt = [yc + 0.019, zb], bb = [yc + 0.013, zb];
  const q = (p, r) => [[x0, p[0], p[1]], [x1, p[0], p[1]], [x1, r[0], r[1]], [x0, r[0], r[1]]];
  polyOut(pc, t, q(ft, bt), [0, 1, -1]);
  polyOut(pc, t, q(fb, bb), [0, -1, 1]);
  polyOut(pc, t, q(fb, ft), [0, 0, -1]);
  polyOut(pc, t, q(bb, bt), [0, 0, 1]);
}
function shutterPieces() {
  const P = [];
  for (const p of SHUTTER_PAINTS) {
    const t = p === 'white' ? TRIM : 'paint_' + p;
    for (const [part, eave] of WINDOW_VIEWS) {
      const single = part === 'single', h = part === 'top' ? 1 : 0, {S, slats} = shutterRight(t, single, eave);
      const pc = solid(new Piece('x'), single ? S : cut(S, h));
      for (const yc of slats) if (single || yc >= h && yc < h + 1) louvre(pc, t, yc - (single ? 0 : h));
      const right = baked(pc), name = `town/shutters_${p}_${part}${eave ? '_eave' : ''}`;
      P.push(new Piece(name).merge(right).merge(mirroredX(right, 'x')));
    }
  }
  return P;
}
/**
 * The picture window (PictureWindow): per cell the glass (z 0.1, the whole cell, no frame between neighbours) and, at
 * the run's edges, the head / sill (ends: where the run ends left / right, the casings' horns) and the jambs. The jambs'
 * frame bars are 2 mm fuller than the head's and sill's, so where they cross no faces lie in one plane.
 */
const ENDS = ['mid', 'neg', 'pos', 'both'];
function pictureBoxes(kind, ends = 'mid') {
  const neg = ends === 'neg' || ends === 'both', pos = ends === 'pos' || ends === 'both';
  const e0 = d => neg ? -d : 0, e1 = d => pos ? 1 + d : 1;
  if (kind === 'head') return [[TRIM, [0, 0.96, 0], [1, 1, 1]], [TRIM, [0, 0.9, 0.07], [1, 0.96, 0.13]], [TRIM, [e0(0.07), 0.94, -0.026], [e1(0.07), 1.06, 0]],
    [TRIM, [e0(0.08), 1.06, -0.04], [e1(0.08), 1.08, 0]], [TRIM, [e0(0.07), 0.94, 1], [e1(0.07), 1.06, 1.022]]];
  if (kind === 'sill') return [[TRIM, [e0(0.07), 0, -0.06], [e1(0.07), 0.04, 0.1]], [TRIM, [0, 0.02, 0.07], [1, 0.08, 0.13]], [TRIM, [0, 0, 0.1], [1, 0.02, 0.9]],
    [TRIM, [e0(0.07), 0, 0.9], [e1(0.07), 0.035, 1.06]], [TRIM, [e0(0.05), -0.07, 1], [e1(0.05), 0, 1.016]]];
  // jamb_neg (x 0 side)
  return [[TRIM_V, [0, 0, 0], [LIN, 1, 1]], [TRIM_V, [LIN, 0, 0.068], [0.1, 1, 0.132]], [TRIM_V, [-0.05, 0, -0.022], [LIN, 1, 0]], [TRIM_V, [-0.05, 0, 1], [LIN, 1, 1.018]]];
}
function picturePieces() {
  const P = [box(new Piece('town/picture_glass'), GLASS, [0, 0, 0.1], [1, 1, 0.1], 'ns')];
  for (const e of ENDS) for (const k of ['head', 'sill']) P.push(solid(new Piece(`town/picture_${k}_${e}`), pictureBoxes(k, e)));
  const jamb = solid(new Piece('town/picture_jamb_neg'), pictureBoxes('jamb'));
  P.push(jamb, mirroredX(jamb, 'town/picture_jamb_pos'));
  return P;
}

// =============================================================================================== doors
// As the cabin's doors (CabinDoor): facing north draws the side the player stood on at +z (the outside of an outer door),
// hinge right = the x 1 jamb, the panel closed at the outside face (z 0.92..0.965) and swung a quarter into the opening
// against the hinge jamb. Doors are laid out over their full height (Y 0..1.95, the head lining above) and split into the
// lower and upper cells. Outer frame: linings through the opening, an oak threshold, casings 2.2 cm proud outside (a drip
// cap over the head) and 1.8 cm inside. The interior door's frame is centred in the cell (the partition, 6..10 px), its
// panel in the middle swinging toward +z (only its part inside the cell collides).
const J = 0.05, PZ0 = 0.92, PZ1 = 0.965, IZ0 = 0.4825, IZ1 = 0.5175, DOOR_TOP = 1.95, SZ0 = 1.026, SZ1 = 1.052;
/** Boxes over the door's full height cut to one cell (half 0 lower, 1 upper), y relative to that cell. */
const cut = (S, h) => S.flatMap(([t, a, b, o]) => {
  const y0 = Math.max(a[1], h), y1 = Math.min(b[1], h + 1);
  return y1 - y0 > 1e-9 ? [[t, [a[0], y0 - h, a[2]], [b[0], y1 - h, b[2]], ...(o ? [o] : [])]] : [];
});
function doorFrameBoxes(h, interior) {
  const top = h ? 0.95 : 1, S = [];
  if (interior) {
    const [z0, z1] = [0.375, 0.625];
    S.push([TRIM_V, [0, 0, z0], [J, top, z1]], [TRIM_V, [1 - J, 0, z0], [1, top, z1]]);
    if (h) S.push([TRIM, [0, 0.95, z0], [1, 1, z1]]);
    for (const [c0, c1] of [[0.36, z0], [z1, 0.64]]) {
      S.push([TRIM_V, [-0.07, 0, c0], [J, h ? 0.94 : 1, c1]], [TRIM_V, [1 - J, 0, c0], [1.07, h ? 0.94 : 1, c1]]);
      if (h) S.push([TRIM, [-0.08, 0.94, c0 < 0.5 ? c0 - 0.002 : c0], [1.08, 1.05, c0 < 0.5 ? c1 : c1 + 0.002]]);
    }
    return S;
  }
  S.push([TRIM_V, [0, 0, 0], [J, top, 1]], [TRIM_V, [1 - J, 0, 0], [1, top, 1]]);
  if (h) S.push([TRIM, [0, 0.95, 0], [1, 1, 1]], [TRIM, [-0.08, 0.94, 1], [1.08, 1.07, 1.026]], [TRIM, [-0.09, 1.07, 1], [1.09, 1.09, 1.04]], [TRIM, [-0.08, 0.94, -0.022], [1.08, 1.06, 0]]);
  else S.push([OAK, [J, 0, 0], [1 - J, 0.022, 1.04]]);
  const ct = h ? 0.94 : 1;
  S.push([TRIM_V, [-0.06, 0, 1], [J, ct, 1.022]], [TRIM_V, [1 - J, 0, 1], [1.06, ct, 1.022]], [TRIM_V, [-0.06, 0, -0.018], [J, ct, 0]], [TRIM_V, [1 - J, 0, -0.018], [1.06, ct, 0]]);
  return S;
}
/**
 * The colonial surround of a front door (FrontDoor surround, Town House B, 2026-10-21), over the door's full height in
 * the door's north frame (the outside at z > 1, the wall's face at z 1): plain pilasters 18 cm wide on plinths with
 * capitals either side of the casings, a frieze and a cornice over the head, a pediment on the cornice (a recessed
 * tympanum under two raking cornices, 26 cm rise). Its boxes join the frame's in one solid; the pediment is faces over the
 * upper cell, up to 1.63 over it (under an eave's fascia at 1.74).
 */
const PED = {x0: -0.33, x1: 1.33, y: 2.30, rise: 0.26, depth: 0.065, z: 1.10, tz: 1.03};
function surroundBoxes() {
  const S = [];
  for (const m of [x => x, x => 1 - x]) {
    const X = (a, b) => [Math.min(m(a), m(b)), Math.max(m(a), m(b))];
    const [p0, p1] = X(-0.25, -0.07), [b0, b1] = X(-0.27, -0.05);
    S.push([TRIM_V, [p0, 0.18, 1], [p1, 1.94, 1.045]], [TRIM, [b0, 0, 1], [b1, 0.18, 1.06]], [TRIM, [b0, 1.94, 1], [b1, 2.09, 1.06]]);
  }
  S.push([TRIM, [-0.27, 2.09, 1], [1.27, 2.24, 1.05]], [TRIM, [PED.x0, 2.24, 1], [PED.x1, PED.y, PED.z]]);
  return S;
}
/** The pediment's faces (y relative to the upper cell): the tympanum and the raking cornices, mitred at the peak. */
function pediment(pc) {
  const {x0, x1, rise, depth, z, tz} = PED, y = PED.y - 1, xm = 0.5;
  polyOut(pc, TRIM, [[x0, y, tz], [x1, y, tz], [xm, y + rise, tz]], [0, 0, 1]);
  for (const side of [0, 1]) {
    const X = x => side ? 1 - x : x, out = side ? 1 : -1;
    const P0 = [X(x0), y], P1 = [xm, y + rise], Q0 = [X(x0), y + depth], Q1 = [xm, y + rise + depth];
    const at = ([px, py], zz) => [px, py, zz];
    polyOut(pc, TRIM, [at(P0, z), at(P1, z), at(Q1, z), at(Q0, z)], [0, 0, 1]);
    polyOut(pc, TRIM, [at(Q0, 1), at(Q1, 1), at(Q1, z), at(Q0, z)], [0, 1, 0]);
    polyOut(pc, TRIM, [at(P0, tz), at(P1, tz), at(P1, z), at(P0, z)], [0, -1, 0]);
    polyOut(pc, TRIM, [at(P0, 1), at(Q0, 1), at(Q0, z), at(P0, z)], [out, 0, 0]);
  }
  return pc;
}

/**
 * A door leaf's boxes and glass (full height): stiles and rails at full thickness, panels recessed 1.2 cm a side with a
 * raised field 6 mm (the panel moulding's step), glass lights in the openings. z0..z1 the leaf, x J..1 - J.
 */
function leaf(kind, P, z0, z1) {
  const S = [], G = [], x0 = J, x1 = 1 - J, st = 0.11, a = x0 + st, b = x1 - st;
  const V = P === TRIM ? TRIM_V : P, frame = (xa, ya, xb, yb, t = P) => S.push([t, [xa, ya, z0], [xb, yb, z1]]);
  const panel = (xa, ya, xb, yb) => { S.push([P, [xa + 0.035, ya + 0.035, z0 + 0.006], [xb - 0.035, yb - 0.035, z1 - 0.006]], [P, [xa, ya, z0 + 0.012], [xb, yb, z1 - 0.012]]); };
  const lights = (xa, ya, xb, yb, nx, ny, m) => {
    const w = (xb - xa - (nx - 1) * m) / nx, hh = (yb - ya - (ny - 1) * m) / ny;
    for (let i = 1; i < nx; i++) frame(xa + i * w + (i - 1) * m, ya, xa + i * (w + m), yb, V);
    for (let j = 1; j < ny; j++) frame(xa, ya + j * hh + (j - 1) * m, xb, ya + j * (hh + m), P);
    G.push([xa, ya, xb, yb]);
  };
  frame(x0, 0.025, a, DOOR_TOP, V); frame(b, 0.025, x1, DOOR_TOP, V);
  if (kind === 'front') {
    frame(a, 0.025, b, 0.25); frame(a, 0.85, b, 1.1); frame(a, 1.42, b, 1.5); frame(a, 1.74, b, DOOR_TOP);
    frame(0.47, 0.25, 0.53, 0.85, V); frame(0.47, 1.1, 0.53, 1.42, V);
    for (const [ya, yb] of [[0.25, 0.85], [1.1, 1.42]]) { panel(a, ya, 0.47, yb); panel(0.53, ya, b, yb); }
    lights(a, 1.5, b, 1.74, 3, 1, 0.03);
  } else if (kind === 'back') {
    frame(a, 0.025, b, 0.25); frame(a, 0.85, b, 1.0); frame(a, 1.8, b, DOOR_TOP); frame(0.47, 0.25, 0.53, 0.85, V);
    panel(a, 0.25, 0.47, 0.85); panel(0.53, 0.25, b, 0.85);
    lights(a, 1.0, b, 1.8, 3, 3, 0.022);
  } else {   // interior: five horizontal panels
    const rails = [[0, 0.22], [0.486, 0.556], [0.822, 0.892], [1.158, 1.228], [1.494, 1.564], [1.83, DOOR_TOP]];
    rails.forEach(([ya, yb]) => frame(a, Math.max(ya, 0.025), b, yb));
    for (let k = 0; k + 1 < rails.length; k++) panel(a, rails[k][1], b, rails[k + 1][0]);
  }
  return {S, G};
}
/** The screen door outside the back door (x 0..1 over the casings): white frame, a kick panel, two screens, a pull. */
function screenLeaf() {
  const S = [], G = [], z0 = SZ0, z1 = SZ1, P = TRIM;
  const frame = (xa, ya, xb, yb) => S.push([P, [xa, ya, z0], [xb, yb, z1]]);
  frame(0, 0, 0.06, 1.94); frame(0.94, 0, 1, 1.94);
  frame(0.06, 0, 0.94, 0.2); frame(0.06, 0.42, 0.94, 0.5); frame(0.06, 0.95, 0.94, 1.03); frame(0.06, 1.82, 0.94, 1.94);
  S.push([P, [0.06, 0.2, z0 + 0.006], [0.94, 0.42, z1 - 0.006]]);
  S.push([IRON, [0.065, 0.94, z1], [0.08, 0.96, z1 + 0.014]], [IRON, [0.065, 1.02, z1], [0.08, 1.04, z1 + 0.014]], [IRON, [0.065, 0.94, z1 + 0.014], [0.08, 1.04, z1 + 0.022]]);
  G.push([0.06, 0.5, 0.94, 0.95], [0.06, 1.03, 0.94, 1.82]);
  return {S, G, glass: 'screen_glass', zm: (z0 + z1) / 2};
}
/** A knob on one face (out +1 toward +z): rose plate, neck, knob, centred at (x, Y) of the full height. */
function knob(pc, x, Y, zFace, out, ballTex) {
  const span = (z, d) => out > 0 ? [z, z + d] : [z - d, z];
  const [r0, r1] = span(zFace, 0.006), [n0, n1] = span(out > 0 ? r1 : r0, 0.02), [b0, b1] = span(out > 0 ? n1 : n0, 0.03);
  cylinder(pc, 'brass', 'brass', 'z', [x, Y], r0, r1, 0.03, 12, out > 0 ? 'b' : 'a');
  cylinder(pc, 'brass', 'brass', 'z', [x, Y], n0, n1, 0.01, 8);
  cylinder(pc, ballTex, ballTex, 'z', [x, Y], b0, b1, 0.027, 12, 'ab');
}
/** One cell of a leaf (and its knobs, when they fall in that cell), baked so its texture stays on it when it swings. */
function leafPiece(name, L, h, knobs = []) {
  const pc = new Piece(name), zm = L.zm;
  solid(pc, cut(L.S, h));
  for (const [xa, ya, xb, yb] of L.G) {
    const y0 = Math.max(ya, h), y1 = Math.min(yb, h + 1);
    if (y1 > y0) box(pc, L.glass, [xa, y0 - h, zm], [xb, y1 - h, zm], 'ns');
  }
  for (const [x, Y, z, out, t] of knobs) if (Y >= h && Y < h + 1) knob(pc, x, Y - h, z, out, t);
  return baked(pc);
}
/** Swung a quarter about the vertical line (hx, hz): inward (toward -z) or outward (+z). */
function swing(pc, hx, hz, outward) {
  const f = outward ? ([x, y, z]) => [hx + (z - hz), y, hz - (x - hx)] : ([x, y, z]) => [hx - (z - hz), y, hz + (x - hx)];
  const fn = outward ? ([nx, ny, nz]) => [nz, ny, -nx] : ([nx, ny, nz]) => [-nz, ny, nx];
  return new Piece(pc.name).merge(pc, f, fn);
}
export const DOOR_KINDS = {front: PAINTS, back: [null], interior: [null]};
function doorPieces() {
  const P = [];
  for (const h of [0, 1]) {
    const half = h ? 'upper' : 'lower';
    P.push(solid(new Piece('town/door_frame_' + half), doorFrameBoxes(h, false)), solid(new Piece('town/door_interior_frame_' + half), doorFrameBoxes(h, true)));
    // the surround joined to the frame; over the upper cell it runs on above the cell's top
    const sur = surroundBoxes().flatMap(([t, a, b]) => { const y0 = Math.max(a[1], h), y1 = h ? b[1] : Math.min(b[1], 1);
      return y1 - y0 > 1e-9 ? [[t, [a[0], y0 - h, a[2]], [b[0], y1 - h, b[2]]]] : []; });
    const framed = solid(new Piece('town/door_frame_surround_' + half), [...doorFrameBoxes(h, false), ...sur]);
    P.push(h ? pediment(framed) : framed);
    const variants = [...PAINTS.map(p => ['front_' + p, 'front', p === 'white' ? TRIM : 'paint_' + p]), ['back', 'back', TRIM], ['interior', 'interior', TRIM], ['screen', 'screen', TRIM]];
    for (const [name, kind, paint] of variants) {
      const interior = kind === 'interior';
      const L = kind === 'screen' ? screenLeaf() : {...leaf(kind, paint, interior ? IZ0 : PZ0, interior ? IZ1 : PZ1), glass: GLASS, zm: interior ? (IZ0 + IZ1) / 2 : (PZ0 + PZ1) / 2};
      const ball = interior ? 'knob_glass' : 'brass';
      const knobs = kind === 'screen' ? [] : interior ? [[0.115, 0.92, IZ1, 1, ball], [0.115, 0.92, IZ0, -1, ball]] : [[0.115, 0.92, PZ1, 1, ball], [0.115, 0.92, PZ0, -1, ball]];
      const closed = leafPiece('x', L, h, knobs);
      const open = kind === 'screen' ? swing(closed, 1, SZ0, true) : interior ? swing(closed, 1 - J, IZ1, true) : swing(closed, 1 - J, PZ0, false);
      const nm = (hinge, o) => `town/door_${name}_${half}_${hinge}_${o}`;
      P.push(turned(closed, 0, nm('right', 'closed')), turned(open, 0, nm('right', 'open')), mirroredX(closed, nm('left', 'closed')), mirroredX(open, nm('left', 'open')));
    }
  }
  return P;
}

// =============================================================================================== railing
// North frame: along x on the cell's north edge (facing north = the edge it hugs). Black square bar: a 3 x 2.5 cm top rail
// (top at 0.9), a 2 x 2.5 cm bottom rail 0.795 under it, 1.2 cm pickets every 12.5 cm between them, 4 cm posts with a cap
// at the ends. Down a stair (a stair under it climbing along x: the stoop steps' upper cell or a vanilla bottom stair,
// both 0.5 a cell) both rails fall half a block over the cell, from 0.9 at the high end, the pickets between them. Over
// the stoop steps' lower cell (foot) they go on down to the newel post on the last tread, 0.45 / 0.55 along the cell.
// Distances d run from the flight's top (the high end of the upper cell): rails 0.9 - d / 2 over the railing's cell.
const RZ = 0.065, TOP_Y = 0.9, POST = 0.02, PICKET = 0.006, LOW = 0.795, FOOT = 0.55;
export const RAIL_SHAPES = ['straight', 'stair_neg', 'stair_pos', 'foot_neg', 'foot_pos'];
/** d along the flight at x (null: straight); neg shapes are low at x 0. */
const flight = (shape, x) => ({straight: null, stair_neg: 1 - x, stair_pos: x, foot_neg: 2 - x, foot_pos: 1 + x})[shape];
/** The top rail's top at x for a shape. */
const railTop = (shape, x) => shape === 'straight' ? TOP_Y : TOP_Y - 0.5 * flight(shape, x);
/** The stoop tread under the railing at x (relative to the railing's cell: a step cell below it). */
const tread = (shape, x) => { const d = flight(shape, x); return d == null ? 0 : d >= 5 / 3 ? -1 : (5 - Math.floor(3 * d + 1e-9)) / 6 - 1; };
/** The rails' x range: the foot ends at its newel post. */
const railRange = shape => shape === 'foot_neg' ? [1 - FOOT, 1] : shape === 'foot_pos' ? [0, FOOT] : [0, 1];
/** The posts' x at each end (neg / pos). */
const postX = (shape, side) => side === 'neg' ? (shape === 'foot_neg' ? 1 - FOOT : 0.02) : (shape === 'foot_pos' ? FOOT : 0.98);
/** A bar along x whose top follows top(x) (straight or sloped), hh tall, hw half wide about RZ, ends not drawn. */
function bar(pc, t, top, x0, x1, hh, hw) {
  const P = (x, dy, z) => [x, top(x) + dy, z], a = RZ - hw, b = RZ + hw;
  polyOut(pc, t, [P(x0, 0, a), P(x1, 0, a), P(x1, 0, b), P(x0, 0, b)], [0, 1, 0], {proj: 'y'});
  polyOut(pc, t, [P(x0, -hh, a), P(x1, -hh, a), P(x1, -hh, b), P(x0, -hh, b)], [0, -1, 0], {proj: 'y'});
  for (const [z, out] of [[a, -1], [b, 1]]) polyOut(pc, t, [P(x0, -hh, z), P(x1, -hh, z), P(x1, 0, z), P(x0, 0, z)], [0, 0, out], {proj: 'z'});
}
function railingPieces() {
  const P = [];
  for (const shape of RAIL_SHAPES) {
    const pc = new Piece('town/railing_' + shape), top = x => railTop(shape, x), [xa, xb] = railRange(shape), S = [];
    bar(pc, IRON, top, xa, xb, 0.025, 0.015);
    bar(pc, IRON, x => top(x) - LOW, xa, xb, 0.025, 0.01);
    for (let k = 0; k < 8; k++) {
      const x = 0.0625 + 0.125 * k;
      if (x < xa + 0.04 || x > xb - 0.04) continue;
      S.push([IRON, [x - PICKET, top(x) - LOW - 0.008, RZ - PICKET], [x + PICKET, top(x) - 0.025 + 0.008, RZ + PICKET]]);
    }
    P.push(solid(pc, S));
    for (const side of ['neg', 'pos']) {   // a post at that end, on the tread under it
      const x = postX(shape, side), y0 = tread(shape, x + (side === 'neg' ? 0.01 : -0.01)), yt = top(x) + 0.02;
      P.push(solid(new Piece(`town/railing_post_${side}_${shape}`), [[IRON, [x - POST, y0, RZ - POST], [x + POST, yt, RZ + POST]], [IRON, [x - POST - 0.006, yt, RZ - POST - 0.006], [x + POST + 0.006, yt + 0.015, RZ + POST + 0.006]]]));
    }
  }
  return P;
}

// =============================================================================================== stoop steps
// North frame, facing north = the high side (vanilla stairs' facing): a two-cell flight of cast concrete, upper then lower
// toward +z. Six risers of 1/6 (16.7 cm) from the landing at 1 to the ground at 0, five 1/3 (33 cm) treads with a
// 1.5 cm rounded-off nosing; the lower cell's last third is ground. Built as one solid over z 0..2 (no faces between the
// two cells) and cut at z 1. The sides at x 0 / 16 are their own pieces, left out beside a step of the same flight.
const CONC = 'smooth_concrete';
export const STEP_TREADS = [0, 1, 2, 3, 4].map(i => ({z0: i / 3, z1: (i + 1) / 3, top: (5 - i) / 6}));
function stepPieces() {
  const all = new Piece('steps');
  const B = STEP_TREADS.flatMap(({z0, z1, top}) => [[CONC, [0, 0, z0], [1, top, z1]], [CONC, [0, top - 0.03, z1], [1, top, z1 + 0.015]]]);
  solid(all, B);
  const P = [];
  for (const [part, planes, dz] of [['upper', [[[0, 0, 1], 1]], 0], ['lower', [[[0, 0, -1], -1]], 1]]) {
    let pc = clipped(all, planes);
    // the riser in the plane z 1 (the upper cell's last) belongs to the upper cell only
    if (part === 'lower') pc.polys = pc.polys.filter(r => !r.v.every(v => Math.abs(v[2] - 1) < 1e-9));
    const moved = new Piece('x').merge(pc, ([x, y, z]) => [x, y, z - dz]);
    const side = sg => r => Math.abs(r.fn[0] - sg) < 1e-6 && r.v.every(v => Math.abs(v[0] - (sg > 0 ? 1 : 0)) < 1e-9);
    const body = new Piece('town/steps_' + part), neg = new Piece(`town/steps_${part}_side_neg`), pos = new Piece(`town/steps_${part}_side_pos`);
    for (const r of moved.polys) (side(-1)(r) ? neg : side(1)(r) ? pos : body).polys.push(r);
    P.push(body, neg, pos);
  }
  return P;
}

export function pieces() {
  return [...roofPieces(), ...steepRoofPieces(), ...windowPieces(), ...picturePieces(), ...doorPieces(), ...railingPieces(), ...stepPieces()];
}

// =============================================================================================== shapes
// Outline and collision boxes (pixels, north frame) for TownShapeData.java, from the same boxes as the pieces. Roofs use the
// cabin's (CabinShapeData.ROOF: the shingles lie within the steel's slab).
const SHAPE_FILE = path.join(ROOT, 'src/main/java/com/antaurora/apofirstlight/townhouse/TownShapeData.java');
const px = v => Math.round(v * 16 * 10000) / 10000 + 0;
const pxBox = ([x0, y0, z0], [x1, y1, z1]) => [x0, y0, z0, x1, y1, z1].map(px);
const pxBoxes = list => list.map(([, a, b]) => pxBox(a, b));
const swingBox = ([a, b], hx, hz, outward) => {
  const f = outward ? ([x, y, z]) => [hx + (z - hz), y, hz - (x - hx)] : ([x, y, z]) => [hx - (z - hz), y, hz + (x - hx)];
  const p = f(a), q = f(b);
  return [[0, 1, 2].map(i => Math.min(p[i], q[i])), [0, 1, 2].map(i => Math.max(p[i], q[i]))];
};
const clampZ = ([a, b]) => [[a[0], a[1], Math.max(a[2], 0)], [b[0], b[1], Math.min(b[2], 1)]];
export function shapeData() {
  const halves = [0, 1], top = h => h ? 0.95 : 1;
  const sashBoxes = part => windowSashes(part).map(([z0, z1, y0, y1]) => pxBox([LIN, y0, z0], [1 - LIN, y1, z1]));
  const panel = h => [[J, h ? 0 : 0.025, PZ0], [1 - J, top(h), PZ1]], ipanel = h => [[J, h ? 0 : 0.025, IZ0], [1 - J, top(h), IZ1]];
  const screen = h => [[0, 0, SZ0], [1, h ? 0.94 : 1, SZ1]];
  const railOutline = shape => { const [xa, xb] = railRange(shape);
    if (shape === 'straight') return [pxBox([0, TOP_Y - LOW - 0.025, RZ - 0.015], [1, TOP_Y, RZ + 0.015])];
    return [0, 1, 2, 3].map(k => { const a = xa + (xb - xa) * k / 4, b = xa + (xb - xa) * (k + 1) / 4, ta = railTop(shape, a), tb = railTop(shape, b);
      return pxBox([a, Math.max(0, Math.min(ta, tb) - LOW - 0.025), RZ - 0.015], [b, Math.max(ta, tb), RZ + 0.015]); }); };
  const D = [
    ['The double-hung window, single / bottom / top: frame (sill, stool, apron, linings, casings) and each sash as a slab', [
      ['HOUSE_WINDOW', PARTS.map(p => [...pxBoxes(windowFrame(p)), ...sashBoxes(p)])]]],
    ['The picture window: the glass and its bars\' slab; the head, the sill (both ends), the jamb at x 0 (mirrored for x 16)', [
      ['PICTURE_CORE', [pxBox([0, 0, 0.068], [1, 1, 0.132])]], ['PICTURE_HEAD', pxBoxes(pictureBoxes('head', 'both'))],
      ['PICTURE_SILL', pxBoxes(pictureBoxes('sill', 'both'))], ['PICTURE_JAMB', pxBoxes(pictureBoxes('jamb'))]]],
    ['Outer doors, lower / upper: the frame; the leaf closed / open (hinge right); the back door\'s screen door closed', [
      ['DOOR_FRAME', halves.map(h => pxBoxes(doorFrameBoxes(h, false)))], ['DOOR_PANEL_CLOSED', halves.map(h => [pxBox(...panel(h))])],
      ['DOOR_PANEL_OPEN', halves.map(h => [pxBox(...swingBox(panel(h), 1 - J, PZ0, false))])], ['SCREEN_CLOSED', halves.map(h => [pxBox(...screen(h))])]]],
    ['The interior door, lower / upper: the centred frame; the leaf closed / open (its part inside the cell)', [
      ['INTERIOR_FRAME', halves.map(h => pxBoxes(doorFrameBoxes(h, true)))], ['INTERIOR_PANEL_CLOSED', halves.map(h => [pxBox(...ipanel(h))])],
      ['INTERIOR_PANEL_OPEN', halves.map(h => [pxBox(...clampZ(swingBox(ipanel(h), 1 - J, IZ1, true)))])]]],
    ['The railing, straight / stair_neg / stair_pos / foot_neg / foot_pos: outline of the rails and pickets; the posts at the neg / pos ends (on the tread under them); collision 1.5 high along the rails', [
      ['RAILING', RAIL_SHAPES.map(railOutline)],
      ['RAILING_POST', RAIL_SHAPES.map(s => ['neg', 'pos'].map(side => { const x = postX(s, side), y0 = tread(s, x + (side === 'neg' ? 0.01 : -0.01));
        return pxBox([x - POST - 0.006, Math.max(0, y0), RZ - POST - 0.006], [x + POST + 0.006, Math.max(railTop(s, x) + 0.035, 0.05), RZ + POST + 0.006]); }))],
      ['RAILING_COLLISION', RAIL_SHAPES.map(s => { const [xa, xb] = railRange(s); return [pxBox([xa, 0, RZ - 0.025], [xb, 1.5, RZ + 0.025])]; })]]],
    ['The stoop steps, upper / lower: the treads (outline and collision, the nosings left out)', [
      ['STEPS', [[0, 1], [1, 2]].map(([c0, c1]) => STEP_TREADS.filter(t => t.z0 >= c0 - 1e-9 && t.z1 <= c1 + 1e-9).map(t => pxBox([0, 0, t.z0 - c0], [1, t.top, t.z1 - c0])))]]],
    ['The steep (1:1) roof cell, bare / filled down to a wall: the slab in eight steps along the slope (as CabinShapeData.ROOF)', [
      ['STEEP_ROOF', [false, true].map(fill => Array.from({length: 8}, (_, k) => { const a = k / 8, b = (k + 1) / 8, under = a - RT;
        return pxBox([0, fill ? Math.min(0, under) : under, a], [1, b, b]); }))]]],
  ];
  const boxes = list => '{' + list.map(b => '{' + b.join(', ') + '}').join(', ') + '}';
  const out = ['// Generated by tools/build-town-house-v1.mjs (shapeData): do not edit, run the script (its --check compares).',
    'package com.antaurora.apofirstlight.townhouse;', '',
    '/**',
    ' * The outline and collision boxes of the town house mesh blocks (docs/worldgen/town_buildings_blocks_v1.md): in',
    ' * pixels of the mesh pieces\' north frame, built from the same boxes as the pieces; TownHouseBlocks turns them per state.',
    ' */',
    'final class TownShapeData {'];
  for (const [doc, fields] of D) {
    out.push(`    /** ${doc}. */`);
    for (const [name, v] of fields) {
      const depth = Array.isArray(v[0][0]) ? (Array.isArray(v[0][0][0]) ? 3 : 2) : 1;
      const body = depth === 1 ? boxes(v) : depth === 2 ? '{' + v.map(boxes).join(',\n            ') + '}' : '{' + v.map(w => '{' + w.map(boxes).join(', ') + '}').join(',\n            ') + '}';
      out.push(`    static final double[]${'[]'.repeat(depth)} ${name} = ${body};`);
    }
    out.push('');
  }
  out.push('    private TownShapeData() {}', '}', '');
  return out.join('\n');
}

// =============================================================================================== JSON models
const DIRS = ['north', 'east', 'south', 'west'], ROT = {north: 0, east: 90, south: 180, west: 270};
const face = (tex, uv, cull) => ({uv, texture: tex, ...(cull ? {cullface: cull} : {})});
/** A full-cell element with only the given faces. */
const cellFaces = faces => ({from: [0, 0, 0], to: [16, 16, 16], faces});
function jsonModels() {
  const M = {}, T = n => TEX + n;
  // faced walls: the north face outside / inside (turned by the blockstate), the plaster top and bottom, the lap corner board
  M['town/plaster_side'] = {parent: 'minecraft:block/block', textures: {particle: T('plaster'), t: T('plaster')}, elements: [cellFaces({north: face('#t', [0, 0, 16, 16], 'north')})]};
  M['town/plaster_caps'] = {parent: 'minecraft:block/block', textures: {particle: T('plaster'), t: T('plaster')},
    elements: [cellFaces({up: face('#t', [0, 0, 16, 16], 'up'), down: face('#t', [0, 0, 16, 16], 'down')})]};
  // the corner board at the north-west edge: 1.6 px (10 cm) wide each way, 0.5 px (3 cm) proud of both faces
  const W = 1.6, O = 0.5, cb = (u, w) => [u, 0, u + w, 16];
  M['town/lap_corner'] = {parent: 'minecraft:block/block', textures: {particle: T('trim_white_v'), t: T('trim_white_v')}, elements: [
    {from: [-O, 0, -O], to: [W, 16, 0], faces: {north: face('#t', cb(2, W + O)), west: face('#t', cb(5, O)), east: face('#t', cb(7, O)), up: face('#t', [2, 7, 2 + W + O, 7 + O], 'up'), down: face('#t', [2, 7, 2 + W + O, 7 + O], 'down')}},
    {from: [-O, 0, 0], to: [0, 16, W], faces: {west: face('#t', cb(9, W)), south: face('#t', cb(12, O)), up: face('#t', [9, 3, 9 + O, 3 + W], 'up'), down: face('#t', [9, 3, 9 + O, 3 + W], 'down')}}]};
  for (const b of BLOCKS.filter(b => b.kind === 'faced')) {
    M['town/' + b.id + '_side'] = {parent: 'minecraft:block/block', textures: {particle: T(b.tex), t: T(b.tex)}, elements: [cellFaces({north: face('#t', [0, 0, 16, 16], 'north')})]};
    M['town/' + b.id + '_inventory'] = {parent: 'minecraft:block/cube_bottom_top', textures: {side: T(b.tex), top: T('plaster'), bottom: T('plaster')}};
  }
  M['town/parged_foundation'] = {parent: 'minecraft:block/cube_all', textures: {all: T('parged_foundation')}};
  M['town/smooth_concrete'] = {parent: 'minecraft:block/cube_all', textures: {all: T(CONC)}};
  M['town/linoleum_floor'] = {parent: 'minecraft:block/cube_bottom_top', textures: {top: T('linoleum'), side: RED_OAK, bottom: RED_OAK}};
  const ceiling = (y0, extra = {}) => ({from: [0, y0, 0], to: [16, 16, 16], faces: {down: face('#p', [0, 0, 16, 16]), up: face('#c', [0, 0, 16, 16], 'up'),
    ...Object.fromEntries(DIRS.map(d => [d, face('#p', [0, 0, 16, 16 - y0], d)])), ...extra}});
  M['town/plaster_ceiling'] = {parent: 'minecraft:block/block', textures: {particle: T('plaster'), p: T('plaster'), c: T('ceiling_top')}, elements: [ceiling(14)]};
  // partition: post and the north arm at full height (16) and under its own ceiling (14); the skirting (1.4 px, 0.4 px proud)
  const P = {particle: T('plaster'), p: T('plaster'), s: T('trim_white')};
  const el = (from, to, tex, faces, cull = {}) => ({from, to, faces: Object.fromEntries(faces.map(f => [f, face(tex, null, cull[f])]).map(([f, v]) => { delete v.uv; return [f, v]; }))});
  for (const H of [16, 14]) {
    const cu = H === 16 ? {up: 'up'} : {};
    M[`town/partition_post_${H}`] = {parent: 'minecraft:block/block', textures: P, elements: [el([6, 0, 6], [10, H, 10], '#p', ['north', 'east', 'south', 'west', 'up', 'down'], {down: 'down', ...cu})]};
    M[`town/partition_side_${H}`] = {parent: 'minecraft:block/block', textures: P, elements: [el([6, 0, 0], [10, H, 6], '#p', ['north', 'east', 'west', 'up', 'down'], {north: 'north', down: 'down', ...cu})]};
  }
  M['town/partition_ceiling'] = {parent: 'minecraft:block/block', textures: {particle: T('plaster'), p: T('plaster'), c: T('ceiling_top')}, elements: [ceiling(14)]};
  M['town/partition_skirting_post'] = {parent: 'minecraft:block/block', textures: P, elements: [el([6, 0, 5.6], [10, 1.4, 6], '#s', ['north', 'up'])]};
  M['town/partition_skirting_side'] = {parent: 'minecraft:block/block', textures: P, elements: [el([5.6, 0, 0], [6, 1.4, 5.6], '#s', ['west', 'up', 'north']), el([10, 0, 0], [10.4, 1.4, 5.6], '#s', ['east', 'up', 'north'])]};
  M['town/partition_skirting_corner'] = {parent: 'minecraft:block/block', textures: P, elements: [el([10, 0, 5.6], [10.4, 1.4, 6], '#s', ['north', 'east', 'up'])]};
  M['town/plaster_partition_inventory'] = {parent: 'minecraft:block/block', textures: P, display: {gui: {rotation: [30, 225, 0], translation: [0, 0, 0], scale: [0.625, 0.625, 0.625]}},
    elements: [el([6, 0, 0], [10, 16, 16], '#p', ['north', 'east', 'south', 'west', 'up', 'down']), el([5.6, 0, 0], [6, 1.4, 16], '#s', ['west', 'up']), el([10, 0, 0], [10.4, 1.4, 16], '#s', ['east', 'up'])]};
  // coping (along x; the overhang 1 px over z), its two end caps, the flue
  const L = {particle: T('limestone'), l: T('limestone'), t: T('terracotta'), f: T('flue_top')};
  M['town/coping_base'] = {parent: 'minecraft:block/block', textures: L, elements: [{from: [0, 0, -1], to: [16, 2, 17], faces: {
    north: face('#l', [0, 14, 16, 16]), south: face('#l', [0, 14, 16, 16]), up: face('#l', [0, 0, 16, 16]), down: face('#l', [0, 0, 16, 16]),
    west: face('#l', [0, 14, 16, 16], 'west'), east: face('#l', [0, 14, 16, 16], 'east')}}]};
  M['town/coping_end'] = {parent: 'minecraft:block/block', textures: L, elements: [{from: [-1, 0, -1], to: [0, 2, 17], faces: {
    north: face('#l', [0, 14, 1, 16]), south: face('#l', [15, 14, 16, 16]), west: face('#l', [0, 14, 16, 16]), up: face('#l', [0, 0, 1, 16]), down: face('#l', [0, 0, 1, 16])}}]};
  M['town/coping_flue'] = {parent: 'minecraft:block/block', textures: L, elements: [{from: [4.5, 2, 4.5], to: [11.5, 5, 11.5], faces: {
    ...Object.fromEntries(DIRS.map(d => [d, face('#t', [4.5, 11, 11.5, 14])])), up: face('#f', [4.5, 4.5, 11.5, 11.5])}}]};
  M['town/limestone_coping_inventory'] = {parent: 'minecraft:block/block', textures: L, elements: [...M['town/coping_base'].elements, ...M['town/coping_end'].elements,
    {from: [16, 0, -1], to: [17, 2, 17], faces: M['town/coping_end'].elements[0].faces}, ...M['town/coping_flue'].elements]};
  return M;
}
function jsonStates() {
  const S = {}, m = n => NS + 'block/' + n;
  for (const b of BLOCKS.filter(b => b.kind === 'faced')) {
    const parts = [{apply: {model: m('town/plaster_caps')}}];
    for (const d of DIRS) for (const v of ['true', 'false'])
      parts.push({when: {[d]: v}, apply: {model: m(v === 'true' ? `town/${b.id}_side` : 'town/plaster_side'), ...(ROT[d] ? {y: ROT[d]} : {})}});
    if (b.corners) for (const [a, c, y] of [['north', 'west', 0], ['north', 'east', 90], ['south', 'east', 180], ['south', 'west', 270]])
      parts.push({when: {[a]: 'true', [c]: 'true'}, apply: {model: m('town/lap_corner'), ...(y ? {y} : {})}});
    S[b.id] = {multipart: parts};
  }
  S.parged_foundation = {variants: {'': {model: m('town/parged_foundation')}}};
  S.smooth_concrete = {variants: {'': {model: m('town/smooth_concrete')}}};
  S.linoleum_floor = {variants: {'': {model: m('town/linoleum_floor')}}};
  S.plaster_ceiling = {variants: {'': {model: m('town/plaster_ceiling')}}};
  const pp = [];
  for (const [c, H] of [['false', 16], ['true', 14]]) {
    pp.push({when: {ceiling: c}, apply: {model: m('town/partition_post_' + H)}});
    for (const d of DIRS) pp.push({when: {ceiling: c, [d]: 'true'}, apply: {model: m('town/partition_side_' + H), ...(ROT[d] ? {y: ROT[d]} : {})}});
  }
  pp.push({when: {ceiling: 'true'}, apply: {model: m('town/partition_ceiling')}});
  for (const d of DIRS) {
    const y = ROT[d] ? {y: ROT[d]} : {};
    pp.push({when: {base: 'true', [d]: 'false'}, apply: {model: m('town/partition_skirting_post'), ...y}});
    pp.push({when: {base: 'true', [d]: 'true'}, apply: {model: m('town/partition_skirting_side'), ...y}});
    pp.push({when: {base: 'true'}, apply: {model: m('town/partition_skirting_corner'), ...y}});
  }
  S.plaster_partition = {multipart: pp};
  const cp = [];
  for (const [axis, y] of [['x', 0], ['z', 90]]) {
    const r = y ? {y} : {};
    cp.push({when: {axis}, apply: {model: m('town/coping_base'), ...r}});
    cp.push({when: {axis, left: 'true'}, apply: {model: m('town/coping_end'), ...r}});
    cp.push({when: {axis, right: 'true'}, apply: {model: m('town/coping_end'), y: y + 180}});
  }
  cp.push({when: {flue: 'true'}, apply: {model: m('town/coping_flue')}});
  S.limestone_coping = {multipart: cp};
  return S;
}

// =============================================================================================== mesh models, loot
/** Item views of the mesh blocks: [piece, turn, dx, dy, dz]. */
const ITEMS = {
  ...Object.fromEntries(SHINGLES.map(c => ['asphalt_shingle_roof_' + c, ['', 'eave_', 'rake_neg_', 'rake_pos_', 'eave_corner_neg_', 'eave_corner_pos_'].map(k => [`roof/shingle_${c}_${k}lower`, 0]).concat([['roof/under_lower', 0]])])),
  ...Object.fromEntries(SHINGLES.map(c => ['steep_asphalt_shingle_roof_' + c, ['steep', 'eave_steep', 'rake_neg_steep', 'rake_pos_steep', 'eave_corner_neg_steep', 'eave_corner_pos_steep'].map(k => [`roof/shingle_${c}_${k}`, 0]).concat([['roof/under_steep', 0]])])),
  white_double_hung_window: [['town/window_one_over_one_bottom', 0], ['town/window_one_over_one_top', 0, 0, 1, 0]],
  white_picture_window: [['town/picture_glass', 0], ['town/picture_glass', 0, 0, 1, 0], ['town/picture_sill_both', 0], ['town/picture_head_both', 0, 0, 1, 0],
    ['town/picture_jamb_neg', 0], ['town/picture_jamb_pos', 0], ['town/picture_jamb_neg', 0, 0, 1, 0], ['town/picture_jamb_pos', 0, 0, 1, 0]],
  house_front_door: [['town/door_frame_lower', 0], ['town/door_front_white_lower_right_closed', 0], ['town/door_frame_upper', 0, 0, 1, 0], ['town/door_front_white_upper_right_closed', 0, 0, 1, 0]],
  house_back_door: [['town/door_frame_lower', 0], ['town/door_back_lower_right_closed', 0], ['town/door_frame_upper', 0, 0, 1, 0], ['town/door_back_upper_right_closed', 0, 0, 1, 0]],
  interior_panel_door: [['town/door_interior_frame_lower', 0], ['town/door_interior_lower_right_closed', 0], ['town/door_interior_frame_upper', 0, 0, 1, 0], ['town/door_interior_upper_right_closed', 0, 0, 1, 0]],
  wrought_iron_railing: [['town/railing_straight', 0], ['town/railing_post_neg_straight', 0], ['town/railing_post_pos_straight', 0]],
  concrete_stoop_steps: ['upper', 'lower'].flatMap((p, i) => ['', '_side_neg', '_side_pos'].map(k => [`town/steps_${p}${k}`, 0, 0, 0, i - 0.5])),
};
const PARTICLE = {...Object.fromEntries(SHINGLES.flatMap(c => [['asphalt_shingle_roof_' + c, 'shingle_' + c], ['steep_asphalt_shingle_roof_' + c, `shingle_${c}_steep`]])), white_double_hung_window: TRIM, white_picture_window: TRIM,
  house_front_door: TRIM, house_back_door: TRIM, interior_panel_door: TRIM, wrought_iron_railing: IRON, concrete_stoop_steps: CONC};
const TALL_DISPLAY = {gui: {rotation: [30, 225, 0], translation: [0, -2.5, 0], scale: [0.38, 0.38, 0.38]}, ground: {rotation: [0, 0, 0], translation: [0, 3, 0], scale: [0.2, 0.2, 0.2]},
  fixed: {rotation: [0, 0, 0], translation: [0, -4, 0], scale: [0.4, 0.4, 0.4]}, thirdperson_righthand: {rotation: [75, 45, 0], translation: [0, 1, 0], scale: [0.25, 0.25, 0.25]},
  firstperson_righthand: {rotation: [0, 45, 0], translation: [0, -2, 0], scale: [0.3, 0.3, 0.3]}, firstperson_lefthand: {rotation: [0, 225, 0], translation: [0, -2, 0], scale: [0.3, 0.3, 0.3]}};
/** The two-cell steps: the block view smaller, the flight centred on the cell. */
const LONG_DISPLAY = {gui: {rotation: [30, 225, 0], translation: [0, 0, 0], scale: [0.42, 0.42, 0.42]}, ground: {rotation: [0, 0, 0], translation: [0, 3, 0], scale: [0.2, 0.2, 0.2]},
  fixed: {rotation: [0, 0, 0], translation: [0, 0, 0], scale: [0.4, 0.4, 0.4]}, thirdperson_righthand: {rotation: [75, 45, 0], translation: [0, 2.5, 0], scale: [0.25, 0.25, 0.25]},
  firstperson_righthand: {rotation: [0, 45, 0], translation: [0, 0, 0], scale: [0.3, 0.3, 0.3]}, firstperson_lefthand: {rotation: [0, 225, 0], translation: [0, 0, 0], scale: [0.3, 0.3, 0.3]}};
const TALL = new Set(['white_double_hung_window', 'white_picture_window', 'house_front_door', 'house_back_door', 'interior_panel_door']);
const loot = (id, extra = {}) => json({type: 'minecraft:block', pools: [{rolls: 1, bonus_rolls: 0, entries: [{type: 'minecraft:item', name: NS + id}],
  conditions: [{condition: 'minecraft:survives_explosion'}, ...(extra.conditions || [])]}], random_sequence: `${NS}blocks/${id}`});

export function outputs() {
  const out = [];
  for (const [name, [make, height]] of Object.entries(TEXTURES))
    renderX(make(), {height}).forEach((m, i) => out.push([path.join(ASSETS, 'textures/block/town', name + ['', '_s', '_n'][i] + '.png'), m]));
  for (const pc of pieces()) out.push([path.join(ASSETS, 'cabin_mesh', pc.name + '.json'), JSON.stringify(serialise(pc, TEX)) + '\n']);
  out.push([SHAPE_FILE, shapeData()]);
  const bm = id => `${NS}block/town/${id}`;
  for (const [name, model] of Object.entries(jsonModels())) out.push([path.join(ASSETS, 'models/block', name + '.json'), json(model)]);
  for (const [id, state] of Object.entries(jsonStates())) out.push([path.join(ASSETS, 'blockstates', id + '.json'), json(state)]);
  for (const b of BLOCKS) {
    const id = b.id;
    if (b.kind === 'mesh') {
      const model = {parent: 'minecraft:block/block', loader: NS + 'cabin', textures: {particle: TEX + PARTICLE[id]}, item: ITEMS[id]};
      if (TALL.has(id)) model.display = TALL_DISPLAY;
      if (id === 'concrete_stoop_steps') model.display = LONG_DISPLAY;
      out.push([path.join(ASSETS, 'models/block/town', id + '.json'), json(model)]);
      out.push([path.join(ASSETS, 'blockstates', id + '.json'), json({variants: {'': {model: bm(id)}}})]);
    }
    const item = b.kind === 'faced' || id === 'plaster_partition' || id === 'limestone_coping' ? bm(id + '_inventory') : bm(id);
    out.push([path.join(ASSETS, 'models/item', id + '.json'), json({parent: item})]);
    const lootExtra = id.endsWith('_door') ? {conditions: [{condition: 'minecraft:block_state_property', block: NS + id, properties: {half: 'lower'}}]}
      : id === 'concrete_stoop_steps' ? {conditions: [{condition: 'minecraft:block_state_property', block: NS + id, properties: {part: 'lower'}}]} : {};
    out.push([path.join(DATA, 'apocalypse_firstlight/loot_tables/blocks', id + '.json'), loot(id, lootExtra)]);
  }
  return out;
}

// =============================================================================================== audit
const readJson = p => JSON.parse(fs.readFileSync(path.join(ROOT, p), 'utf8'));
function audit() {
  const errs = [];
  const tagHas = (file, id) => { try { return readJson(file).values.some(v => (typeof v === 'string' ? v : v.id) === NS + id); } catch { return false; } };
  const tags = 'src/main/resources/data/minecraft/tags/blocks/';
  for (const b of BLOCKS) {
    const want = b.tool === 'axe' ? 'mineable/axe.json' : 'mineable/pickaxe.json', other = b.tool === 'axe' ? 'mineable/pickaxe.json' : 'mineable/axe.json';
    if (!tagHas(tags + want, b.id)) errs.push(`${b.id}: missing from minecraft:${want}`);
    if (tagHas(tags + other, b.id)) errs.push(`${b.id}: also in minecraft:${other}`);
    for (const t of ['stone', 'iron', 'diamond']) {
      const has = tagHas(tags + `needs_${t}_tool.json`, b.id);
      if (has !== (b.tier === t)) errs.push(`${b.id}: needs_${t}_tool ${has ? 'set' : 'missing'}`);
    }
  }
  const massText = JSON.stringify(readJson('src/main/resources/data/apocalypse_firstlight/item_mass/afl_content_v1.json'));
  for (const b of BLOCKS) if (!massText.includes(`"${NS}${b.id}"`)) errs.push(`${b.id}: no item mass`);
  for (const lang of ['en_us', 'zh_cn']) {
    const l = readJson(`src/main/resources/assets/apocalypse_firstlight/lang/${lang}.json`);
    for (const b of BLOCKS) { const v = l['block.apocalypse_firstlight.' + b.id]; if (v !== (lang === 'en_us' ? b.en : b.zh)) errs.push(`${b.id}: lang ${lang} = ${v}`); }
  }
  for (const id of ['house_front_door', 'house_back_door', 'interior_panel_door']) if (!tagHas(tags + 'wooden_doors.json', id)) errs.push(`${id}: missing from minecraft:wooden_doors.json`);
  return errs;
}

const isMain = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  const out = outputs();
  if (process.argv.includes('--check')) {
    const stale = out.filter(([file, data]) => { const cur = fs.existsSync(file) ? fs.readFileSync(file) : null; return !cur || !cur.equals(Buffer.isBuffer(data) ? data : Buffer.from(data)); });
    for (const [file] of stale) console.log('stale ' + path.relative(ROOT, file));
    const errs = audit();
    for (const e of errs) console.log('audit: ' + e);
    if (stale.length || errs.length) process.exit(1);
    console.log(`CHECK OK (${out.length} outputs)`);
  } else {
    for (const [file] of out) fs.mkdirSync(path.dirname(file), {recursive: true});
    for (const [file, data] of out) fs.writeFileSync(file, data);
    const pcs = pieces();
    const quads = pcs.reduce((s, p) => s + serialise(p, TEX).quads.length, 0);
    console.log(`wrote ${out.length} files (${pcs.length} mesh pieces, ${quads} quads)`);
  }
}
