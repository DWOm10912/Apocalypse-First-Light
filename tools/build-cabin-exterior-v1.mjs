// Cabin Exterior V1 (docs/models/cabin_exterior_v1.md, 2026-10-12): the exterior asset pack of the forest start home B
// ("溪谷老宅", docs/worldgen/forest_start_home_b_v1_plan.md, M1). User 2026-10-12: Mesh models + 240 px LabPBR, white oak
// (aged, oiled honey brown) instead of the pale white pine, one pass for the whole exterior.
//   Mesh blocks (pieces in assets/apocalypse_firstlight/cabin_mesh/, drawn by CabinMeshModel, picked per state by the
//   block classes in com.antaurora.apofirstlight.cabin):
//     white_oak_log_wall           three peeled logs per block bulging 7 cm out of each face, cream chinking between;
//                                  logs along X and along Z sit half a course apart, so corners interleave; a corner
//                                  pushes round log ends 27 cm out (saddle-notch look); full opaque block
//     green_standing_seam_roof     1:2 roof (26.6 degrees) in half-block steps: lower / upper / ridge cells, 20 cm thick,
//     galvanized_corrugated_roof   ceiling boards underneath, fascia + drip at the eave, barge board + rake trim at a
//     rusty_corrugated_roof        gable edge, flashing at a top edge against a wall, ridge cap; under a roof cell above
//                                  a wall it fills down to the wall (logs or boards, cut to the slope; 2026-10-14: logs
//                                  for a roof turned a quarter in _q pieces, the boards' battens per side); a header beam
//                                  and a stove pipe boot on request
//     white_oak_cabin_door         two-tall doors in a lined opening with casings both sides (exterior: glazed top half,
//     white_oak_board_door         interior: ledged boards, strap hinges)
//     white_oak_double_hung_window one or two blocks tall, lined, cased, sill and stool, two sashes, glass
//     brown_board_and_batten       full block of vertical boards with 5 cm battens on the open faces
//     grey_board_and_batten
//     white_oak_porch_post         chamfered 16 cm post, rail stubs toward neighbouring railings
//     white_oak_porch_railing      top and bottom rail, 8 balusters per block
//     white_oak_porch_deck         (M1.1) 3 cm deck boards along the axis on joists, a rim board and nosing on the open
//                                  sides, supports under the open corners, every second edge cell and the posts (M1.2: a
//                                  14 cm oak post on a 34 cm squared stone block); walkable thin top, open underneath
//     stove_pipe                   15 cm black pipe, a rain cap on top
//   JSON blocks: fieldstone (M1.2: roughly squared rubble, a 3 x 2 block texture in tiles, MacroTileModel, parallax), firewood_stack (axis), white_oak_stairs / white_oak_slab (the
//   porch deck's boards, treads along the nosing; M1.1), woven_wire_fence (vanilla fence connections + a post every third
//   block or at a turn, cut-out wire).
// M1.1 (user 2026-10-12): structure and form only. The galvanised and rusty roofs have real Mesh corrugation, the porch is
// the thin deck. The 240 px texture set stays (user 2026-10-12: a 32 px pixel set for logs / fieldstone / firewood and a
// denoised 240 set were tried and rejected, they blurred the forms); later tweaks per piece after the in-game check.
// M1.2 (user 2026-10-12): the M1.1 fieldstone read as river-cobble cladding and the 24 cm cobble-clad piers as thin
// columns; of two concepts (design/buildings/forest_start_home_v1/m1_source/plinth_concepts) the user picked B: roughly
// squared rubble for the plinth, an oak post on a squared stone block for each porch support.
// Writes textures (block/cabin/*.png + _s / _n), the mesh pieces, block / item models, blockstates, loot tables. Tags,
// item mass and lang are edited by hand; --check audits them.
//   node tools/build-cabin-exterior-v1.mjs            -> writes every output
//   node tools/build-cabin-exterior-v1.mjs --check    -> verifies every output is up to date and the hand-edited data
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {renderX, TEXTURES, CHINK_HALF, CORRUGATION, FS_COLS, FS_ROWS, fieldstoneTiles} from './cabin/cabin-art.mjs';
import {Piece, box, solid, cylinder, turned, mirroredX, clipped, serialise, newell, dot} from './cabin/cabin-geo.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const ASSETS = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight');
const DATA = path.join(ROOT, 'src/main/resources/data');
const NS = 'apocalypse_firstlight:', TEX = NS + 'block/cabin/';
const json = v => JSON.stringify(v, null, 2) + '\n';

/** Every block of the pack: id, kind (mesh / json), mining (axe / pickaxe, tier), item mass (kg), names. */
export const BLOCKS = [
  {id: 'white_oak_log_wall', mesh: true, tool: 'axe', mass: 0.3, en: 'White Oak Log Wall', zh: '白栎原木墙'},
  {id: 'green_standing_seam_roof', mesh: true, tool: 'pickaxe', tier: 'stone', mass: 0.2, en: 'Green Standing Seam Roof', zh: '绿色立边咬口铁皮屋面'},
  {id: 'galvanized_corrugated_roof', mesh: true, tool: 'pickaxe', tier: 'stone', mass: 0.2, en: 'Galvanized Corrugated Roof', zh: '镀锌瓦楞铁皮屋面'},
  {id: 'rusty_corrugated_roof', mesh: true, tool: 'pickaxe', tier: 'stone', mass: 0.2, en: 'Rusty Corrugated Roof', zh: '生锈瓦楞铁皮屋面'},
  {id: 'white_oak_cabin_door', mesh: true, tool: 'axe', mass: 2.0, en: 'White Oak Cabin Door', zh: '白栎木屋外门'},
  {id: 'white_oak_board_door', mesh: true, tool: 'axe', mass: 1.6, en: 'White Oak Board Door', zh: '白栎木板门'},
  {id: 'white_oak_double_hung_window', mesh: true, tool: 'axe', mass: 1.2, en: 'White Oak Double-Hung Window', zh: '白栎双悬窗'},
  {id: 'brown_board_and_batten', mesh: true, tool: 'axe', mass: 0.15, en: 'Brown Board and Batten', zh: '棕色板条墙'},
  {id: 'grey_board_and_batten', mesh: true, tool: 'axe', mass: 0.15, en: 'Grey Board and Batten', zh: '灰色板条墙'},
  {id: 'white_oak_porch_post', mesh: true, tool: 'axe', mass: 0.8, en: 'White Oak Porch Post', zh: '白栎门廊立柱'},
  {id: 'white_oak_porch_railing', mesh: true, tool: 'axe', mass: 0.6, en: 'White Oak Porch Railing', zh: '白栎门廊栏杆'},
  {id: 'white_oak_porch_deck', mesh: true, tool: 'axe', mass: 0.15, en: 'White Oak Porch Deck', zh: '白栎门廊地板'},
  {id: 'stove_pipe', mesh: true, tool: 'pickaxe', tier: 'stone', mass: 1.0, en: 'Stove Pipe', zh: '炉子烟管'},
  {id: 'fieldstone', tool: 'pickaxe', mass: 0.25, en: 'Fieldstone', zh: '毛石'},
  {id: 'firewood_stack', tool: 'axe', mass: 0.3, en: 'Firewood Stack', zh: '柴垛'},
  {id: 'white_oak_stairs', tool: 'axe', mass: 0.1, en: 'White Oak Stairs', zh: '白栎楼梯'},
  {id: 'white_oak_slab', tool: 'axe', mass: 0.05, en: 'White Oak Slab', zh: '白栎台阶'},
  {id: 'woven_wire_fence', tool: 'pickaxe', tier: 'stone', mass: 0.4, en: 'Woven Wire Fence', zh: '菜园铁丝围栏'},
];

// =============================================================================================== log wall
// One block: three logs. A log's face is an arc of chord 2 LA and rise LS beyond the block face (radius LR), the
// chinking between logs lies in the block face. X logs centre on y 1/6, 1/2, 5/6; Z logs on 0, 1/3, 2/3, 1 (halves at
// the block's top and bottom), so the courses of two walls alternate at a corner.
export const LA = 0.5 / 3 - CHINK_HALF, LS = 0.07, LR = (LA * LA + LS * LS) / (2 * LS), TH0 = Math.asin(LA / LR);
const XC = [1 / 6, 1 / 2, 5 / 6], ZC = [0, 1 / 3, 2 / 3, 1], SEG = 8;
/** Round log ends at a corner: radius SR, SL beyond the face, the outer side flush with the faces' arcs. */
export const SR = 0.172, SL = 0.27, SC = SR - LS;
const WALL = 'white_oak_log_wall', ROUND = 'white_oak_log_round', END = 'white_oak_log_end';

/** A polygon whose corners are reordered so its Newell normal points along `out`. */
function polyOut(pc, t, pts, out, opts = {}) {
  if (dot(newell(pts), out) < 0) {
    pts = [...pts].reverse();
    if (Array.isArray(opts.n) && typeof opts.n[0] !== 'number') opts = {...opts, n: [...opts.n].reverse()};
    if (opts.uv) opts = {...opts, uv: [...opts.uv].reverse()};
  }
  return pc.poly(t, pts, opts);
}
/** Arc points of one course (centre yc) on the north face (z <= 0): [y, z, ny, nz] from bottom to top. */
function arc(yc) {
  const pts = [];
  for (let i = 0; i <= SEG; i++) { const th = -TH0 + 2 * TH0 * i / SEG; pts.push([yc + LR * Math.sin(th), (LR - LS) - LR * Math.cos(th), Math.sin(th), -Math.cos(th)]); }
  return pts;
}
/**
 * Logs along X (courses cs) from x 0 to 1, faces bulging out of z 0 and z 1. ends: the cut ends at x 0 / 1; tops: the
 * faces at y 0 / 1. v offset off (the Z logs' texture is half a course down). Clipped to y 0..1.
 */
function logsAlongX(name, cs, {ends = true, tops = true, off = 0} = {}) {
  const pc = new Piece(name), o = {proj: 'z', off: [0, off]};
  for (const yc of cs) {
    const a = arc(yc);
    for (const side of [0, 1]) {
      const Z = z => side ? 1 - z : z, outZ = side ? 1 : -1;
      for (let i = 0; i < SEG; i++) {
        const [y0, z0, ny0, nz0] = a[i], [y1, z1, ny1, nz1] = a[i + 1];
        const n0 = [0, ny0, side ? -nz0 : nz0], n1 = [0, ny1, side ? -nz1 : nz1];
        polyOut(pc, WALL, [[0, y0, Z(z0)], [1, y0, Z(z0)], [1, y1, Z(z1)], [0, y1, Z(z1)]], [0, 0, outZ], {...o, n: [n0, n0, n1, n1]});
      }
      if (ends) for (const x of [0, 1]) polyOut(pc, END, a.map(([y, z]) => [x, y, Z(z)]), [x ? 1 : -1, 0, 0], {proj: 'x'});
    }
  }
  // chinking: the face between the courses
  const cover = cs.map(c => [c - LA, c + LA]).sort((p, q) => p[0] - q[0]), gaps = [];
  let y = 0;
  for (const [lo, hi] of cover) { if (lo > y + 1e-9) gaps.push([y, Math.min(lo, 1)]); y = Math.max(y, hi); }
  if (y < 1 - 1e-9) gaps.push([y, 1]);
  for (const [lo, hi] of gaps) {
    if (hi <= 0 || lo >= 1) continue;
    const a = Math.max(lo, 0), b = Math.min(hi, 1);
    box(pc, WALL, [0, a, 0], [1, b, 1], 'ns', {all: o});
  }
  if (ends) box(pc, END, [0, 0, 0], [1, 1, 1], 'we', {all: {proj: 'x'}});
  if (tops) {
    const ext = cs.includes(0) ? LS : 0;   // a log centred on the top / bottom: its arc reaches out there
    box(pc, ROUND, [0, 0, -ext], [1, 1, 1 + ext], 'ud', {all: {proj: 'y'}});
  }
  return clipped(pc, [[[0, -1, 0], 0], [[0, 1, 0], 1]]);
}
/** The same logs along Z (the X logs turned a quarter: their faces bulge out of x 0 and x 1). */
const alongZ = (name, opts) => turned(logsAlongX(name, ZC, {...opts, off: 1 / 6}), 1, name);

/**
 * Round log ends beyond a corner. stubz: Z logs from z 0 out to z -SL; stubx: X logs from x 0 out to x -SL. side: where
 * across the wall they sit ('neg' flush with the x 0 / z 0 face, 'pos' with the 1 face, 'mid' the middle: a T junction).
 */
const stubC = side => side === 'neg' ? SC : side === 'pos' ? 1 - SC : 0.5;
function stub(name, along, side) {
  const pc = new Piece(name), c0 = stubC(side);
  if (along === 'z') {
    for (const yc of ZC) cylinder(pc, ROUND, END, 'z', [c0, yc], -SL, 0, SR, 16, 'a');
    // the halves cut by the block's top and bottom: the flat cut
    const cut = new Piece(name);
    box(cut, ROUND, [c0 - SR, 0, -SL], [c0 + SR, 1, 0], 'ud', {all: {proj: 'y'}});
    return clipped(pc, [[[0, -1, 0], 0], [[0, 1, 0], 1]]).merge(cut);
  }
  for (const yc of XC) cylinder(pc, ROUND, END, 'x', [yc, c0], -SL, 0, SR, 16, 'a');
  return pc;
}

/**
 * Pads behind the wall fixtures that stand out on the logs (block/LogMount, 2026-10-13: outlets and the lampholder's pull
 * chain sank into the 7 cm bulge). One flat white oak board per kind, from the wall face PAD_DEPTH out (1 cm in front of
 * the log crests, so the two never fight at a distance), drawn by the wall on the fixture's side (CabinBlocks.LogWall
 * flags: log_wall/pad_<kind>, turned to that side). x0, y0, x1, y1 in px of the north face (= the fixture's own frame),
 * mirrored from LogMount.PADS (the fixtures' outlines include them; --check compares). No back face (it lies in the wall
 * face, on the chinking); the sides run into the logs. The linear light's rail stops 0.1 px short of the cell sides: a
 * run's cut log end lies in the side plane.
 */
export const PAD_DEPTH = 0.08;
export const PADS = [[6.8, 3.9, 9.2, 7.3], [6.75, 5.47, 9.25, 7.97], [4.0, 5.6, 12.0, 10.3], [4.8, 7.0, 11.2, 11.7], [2.9, 2.9, 13.1, 13.1],
  [0.1, 7.0, 15.9, 9.0], [3.2, 2.1, 12.8, 13.9], [5.05, 4.5, 10.95, 12.3], [1.2, 1.1, 13.9, 11.7], [6.3, 3.8, 9.7, 10.2],
  [6.8, 1.5, 9.2, 4.9],    // 11 wall switch (2026-10-14)
  [0.2, 10.6, 15.8, 14.6]];   // 12 wooden tool rack (Wood Furniture V2, 2026-10-15)
const padPieces = () => PADS.map(([x0, y0, x1, y1], i) =>
  box(new Piece('log_wall/pad_' + (i + 1)), y1 - y0 > x1 - x0 ? TRIM_V : TRIM, [x0 / 16, y0 / 16, -PAD_DEPTH], [x1 / 16, y1 / 16, 0], 'nweud'));

function logWallPieces() {
  const P = [...padPieces()];
  P.push(logsAlongX('log_wall/run_x', XC));
  P.push(alongZ('log_wall/run_z', {}));
  P.push(logsAlongX('log_wall/core_x', XC, {ends: false, tops: false}));
  P.push(alongZ('log_wall/core_z', {ends: false}));
  for (const s of ['neg', 'mid', 'pos']) { P.push(stub('log_wall/stubx_' + s, 'x', s)); P.push(stub('log_wall/stubz_' + s, 'z', s)); }
  return P;
}

// =============================================================================================== roofs
// Facing north: the slope falls toward z 0 (the eave side), the ridge runs along x. Surface heights (block units):
// lower 0 -> 0.5, upper 0.5 -> 1, ridge 0.5 -> 0.75 -> 0.5 (a ridge on the middle of a block: odd-width gables, the
// ridge at n + 0.75). The roof is RT thick (vertical): steel on top, ceiling boards underneath.
export const RT = 0.2;
export const SHAPES = ['lower', 'upper', 'ridge'];
export const ys = (shape, z) => shape === 'lower' ? 0.5 * z : shape === 'upper' ? 0.5 + 0.5 * z : 0.75 - 0.5 * Math.abs(z - 0.5);
const zsOf = shape => shape === 'ridge' ? [0, 0.5, 1] : [0, 1];
const MATS = {green: 'roof_green', galvanized: 'roof_galvanized', rust: 'roof_rust'};
const TRIM = 'oak_trim', TRIM_V = 'oak_trim_v';
// M1.1 corrugation (galvanized, rust): CORRUGATION waves a block across the slope (12.5 cm pitch), CA deep each side of
// the slope plane (2.2 cm crest to valley, about a 7/8 inch profile: vanilla bakes up to 7.5 % shade on the flanks from the
// normals, the eave and rake show the wave), a crest on every block edge so cells and turns join;
// smooth normals (6 segments a wave: 4 read as a zigzag at the eave). The sheet is ST thick where its edge shows (the eave
// overhang); its texture shades the valleys 10 %.
const CA = 0.011, ST = 0.004, CSEG = 6 * CORRUGATION, CORR = {galvanized: 'roof_galvanized_sheet', rust: 'roof_rust_sheet'};
const wave = x => CA * Math.cos(2 * Math.PI * CORRUGATION * x), waveD = x => -CA * 2 * Math.PI * CORRUGATION * Math.sin(2 * Math.PI * CORRUGATION * x);
/** The slope's rise per block toward +z at z (ridge: up to the middle, then down). */
const slopeAt = (shape, z) => shape !== 'ridge' || z < 0.5 ? 0.5 : -0.5;
/** A corrugated sheet surface between z a and b (may reach below 0: the eave overhang), dy above the slope, facing up / down. */
function corrugated(pc, t, shape, za, zb, dy, up = true) {
  const zs = [za, ...zsOf(shape).filter(z => z > za + 1e-9 && z < zb - 1e-9), zb], s = up ? 1 : -1;
  for (let j = 0; j + 1 < zs.length; j++) {
    const a = zs[j], b = zs[j + 1], g = slopeAt(shape, (a + b) / 2);
    const P = (x, z) => [x, ys(shape, z) + dy + wave(x), z], nv = x => [-waveD(x) * s, s, -g * s];
    for (let i = 0; i < CSEG; i++) {
      const x0 = i / CSEG, x1 = (i + 1) / CSEG;
      polyOut(pc, t, [P(x0, a), P(x1, a), P(x1, b), P(x0, b)], [0, s, 0], {proj: 'y', n: [nv(x0), nv(x1), nv(x1), nv(x0)]});
    }
  }
}
/** A vertical band at z facing -z (or +z) whose top follows the corrugation (dy above the slope) and whose bottom is level at y0 or follows it too (dy0). */
function waveBand(pc, t, shape, z, out, top, bottom) {
  for (let i = 0; i < CSEG; i++) {
    const x0 = i / CSEG, x1 = (i + 1) / CSEG, y = (x, d) => typeof d === 'number' ? d : ys(shape, z) + d.dy + wave(x);
    polyOut(pc, t, [[x0, y(x0, bottom), z], [x1, y(x1, bottom), z], [x1, y(x1, top), z], [x0, y(x0, top), z]], [0, 0, out], {proj: 'z'});
  }
}
/** A strip following the slope from x x0 to x1, offset dy above the surface, between z a and b (facing up / down). */
function slopeStrip(pc, t, shape, x0, x1, za, zb, dy, up = true) {
  const zs = [za, ...zsOf(shape).filter(z => z > za + 1e-9 && z < zb - 1e-9), zb];
  for (let i = 0; i + 1 < zs.length; i++) {
    const a = zs[i], b = zs[i + 1];
    polyOut(pc, t, [[x0, ys(shape, a) + dy, a], [x1, ys(shape, a) + dy, a], [x1, ys(shape, b) + dy, b], [x0, ys(shape, b) + dy, b]], [0, up ? 1 : -1, 0], {proj: 'y'});
  }
}
/** A vertical band at x (facing out along x) between the surface offsets lo and hi, z a..b. */
function slopeBandX(pc, t, shape, x, out, za, zb, lo, hi) {
  const zs = [za, ...zsOf(shape).filter(z => z > za + 1e-9 && z < zb - 1e-9), zb];
  for (let i = 0; i + 1 < zs.length; i++) {
    const a = zs[i], b = zs[i + 1];
    polyOut(pc, t, [[x, ys(shape, a) + lo, a], [x, ys(shape, b) + lo, b], [x, ys(shape, b) + hi, b], [x, ys(shape, a) + hi, a]], [out, 0, 0], {proj: 'x'});
  }
}
function roofPieces() {
  const P = [];
  for (const shape of SHAPES) {
    // the underside (shared by every material)
    const under = new Piece('roof/under_' + shape);
    slopeStrip(under, 'oak_roof_boards', shape, 0, 1, 0, 1, -RT, false);
    P.push(under);
    for (const [mat, tex] of Object.entries(MATS)) {
      const top = new Piece(`roof/${mat}_${shape}`), corr = CORR[mat], lift = corr ? CA + 0.004 : 0;
      if (corr) corrugated(top, corr, shape, 0, 1, 0, true);
      else slopeStrip(top, tex, shape, 0, 1, 0, 1, 0, true);
      // standing seams: 2.2 cm wide, 2.8 cm tall, 50 cm apart; on the ridge they stop under the cap
      if (mat === 'green') for (const xr of [0.25, 0.75]) for (const [za, zb] of shape === 'ridge' ? [[0, 0.34], [0.66, 1]] : [[0, 1]]) {
        slopeStrip(top, tex, shape, xr - 0.011, xr + 0.011, za, zb, 0.028, true);
        slopeBandX(top, tex, shape, xr - 0.011, -1, za, zb, 0, 0.028);
        slopeBandX(top, tex, shape, xr + 0.011, 1, za, zb, 0, 0.028);
      }
      if (shape === 'ridge') {   // the ridge cap: a bent sheet 17 cm down each side and a roll on top
        slopeStrip(top, tex, shape, 0, 1, 0.5 - 0.17, 0.5 + 0.17, 0.035, true);
        for (const [z, out] of [[0.33, -1], [0.67, 1]]) polyOut(top, tex, [[0, ys(shape, z) - lift, z], [1, ys(shape, z) - lift, z], [1, ys(shape, z) + 0.035, z], [0, ys(shape, z) + 0.035, z]], [0, 0, out], {proj: 'z'});
        box(top, tex, [0, 0.75 + 0.035, 0.485], [1, 0.75 + 0.06, 0.515], 'nsu');
      }
      P.push(top);
      for (const side of ['neg', 'pos']) {   // gable edge: barge board and rake trim
        const r = new Piece(`roof/${mat}_rake_${side}_${shape}`);
        slopeBandX(r, TRIM, shape, -0.035, -1, 0, 1, -RT - 0.08, 0.01);
        slopeBandX(r, TRIM, shape, 0, 1, 0, 1, -RT - 0.08, -RT);
        slopeStrip(r, TRIM, shape, -0.035, 0, 0, 1, -RT - 0.08, false);
        for (const z of [0, 1]) polyOut(r, TRIM, [[-0.035, ys(shape, z) - RT - 0.08, z], [0, ys(shape, z) - RT - 0.08, z], [0, ys(shape, z) + 0.01, z], [-0.035, ys(shape, z) + 0.01, z]], [0, 0, z ? 1 : -1], {proj: 'z'});
        slopeStrip(r, tex, shape, -0.045, 0.03, 0, 1, 0.014, true);
        slopeBandX(r, tex, shape, -0.045, -1, 0, 1, -0.03, 0.014);
        slopeBandX(r, tex, shape, 0.03, 1, 0, 1, -lift, 0.014);
        P.push(side === 'neg' ? r : mirroredX(r, `roof/${mat}_rake_pos_${shape}`));
      }
      if (shape === 'ridge') continue;
      const base = ys(shape, 0), hi = ys(shape, 1);
      const eave = new Piece(`roof/${mat}_eave_${shape}`);
      if (corr) {   // fascia under the sheet, a black foam closure following the waves (oak there showed as a row of dots
        // under the crests), the sheet running on 5 cm over the fascia as the drip
        // the fascia's top is level: below the valleys' underside at its outer face (the sheet keeps falling over it)
        const ft = ys(shape, -0.035) - CA - ST - 0.004;
        box(eave, TRIM, [0, base - RT - 0.06, -0.035], [1, ft, 0], 'ndswe');   // 's': its back below the ceiling boards faces the porch
        box(eave, 'stove_pipe', [0, ft, -0.035], [1, ft, 0], 'u');   // the closure strip on the fascia (seen under the crests)
        waveBand(eave, 'stove_pipe', shape, 0, -1, {dy: -ST}, ft);
        corrugated(eave, corr, shape, -0.05, 0, 0, true);
        corrugated(eave, corr, shape, -0.05, 0, -ST, false);
        waveBand(eave, corr, shape, -0.05, -1, {dy: 0}, {dy: -ST});
      } else {      // fascia and drip edge at z 0
        box(eave, TRIM, [0, base - RT - 0.06, -0.035], [1, base + 0.006, 0], 'nudswe');
        box(eave, tex, [0, base - 0.03, -0.047], [1, base + 0.012, -0.035], 'nudwe');
        polyOut(eave, tex, [[0, base + 0.012, -0.047], [1, base + 0.012, -0.047], [1, base + 0.012, 0], [0, base + 0.012, 0]], [0, 1, 0], {proj: 'y'});
        if (mat === 'green') for (const xr of [0.25, 0.75]) polyOut(eave, tex, [[xr - 0.011, base, 0], [xr + 0.011, base, 0], [xr + 0.011, base + 0.028, 0], [xr - 0.011, base + 0.028, 0]], [0, 0, -1], {proj: 'z'});
      }
      P.push(eave);
      const corner = new Piece(`roof/${mat}_eave_corner_neg_${shape}`);   // where the fascia meets the barge board
      box(corner, TRIM, [-0.035, base - RT - 0.08, -0.035], [0, base + 0.01, 0], 'nwud');
      P.push(corner, mirroredX(corner, `roof/${mat}_eave_corner_pos_${shape}`));
      const topE = new Piece(`roof/${mat}_top_${shape}`);   // high edge (against a wall): closure and a flashing strip
      polyOut(topE, TRIM, [[0, hi - RT, 1], [1, hi - RT, 1], [1, hi, 1], [0, hi, 1]], [0, 0, 1], {proj: 'z'});
      slopeStrip(topE, tex, shape, 0, 1, 0.86, 1, 0.034, true);
      polyOut(topE, tex, [[0, ys(shape, 0.86) - lift, 0.86], [1, ys(shape, 0.86) - lift, 0.86], [1, ys(shape, 0.86) + 0.034, 0.86], [0, ys(shape, 0.86) + 0.034, 0.86]], [0, 0, -1], {proj: 'z'});
      P.push(topE);
      const pipe = new Piece(`roof/${mat}_pipe_${shape}`);   // a stove pipe through the roof: the pipe, a boot, a flashing plate
      cylinder(pipe, 'stove_pipe', 'stove_pipe', 'y', [0.5, 0.5], 0, 1, 0.075, 16);
      slopeStrip(pipe, tex, shape, 0.25, 0.75, 0.25, 0.75, 0.006 + lift, true);
      const ring = (r, dy) => Array.from({length: 16}, (_, i) => { const a = i / 16 * Math.PI * 2, x = 0.5 + Math.cos(a) * r, z = 0.5 + Math.sin(a) * r; return [x, ys(shape, z) + dy, z]; });
      const outer = ring(0.13, 0.01 + lift), inner = ring(0.077, 0.09 + lift);
      for (let i = 0; i < 16; i++) { const j = (i + 1) % 16, mid = [Math.cos((i + 0.5) / 16 * Math.PI * 2), 0.6, Math.sin((i + 0.5) / 16 * Math.PI * 2)];
        polyOut(pipe, 'stove_pipe', [outer[i], outer[j], inner[j], inner[i]], mid, {uv: [[i / 16, 1], [(i + 1) / 16, 1], [(i + 1) / 16, 0.8], [i / 16, 0.8]]}); }
      P.push(pipe);
    }
    if (shape !== 'ridge') {   // a header beam under the roof on the block's middle line (porch fronts): up to the ceiling boards
      const beam = new Piece('roof/beam_' + shape);
      box(beam, TRIM, [0, 0, 0.42], [1, ys(shape, 0.58) - RT, 0.58], 'nsudew');
      P.push(beam);
    }
    // the fill under a roof block that sits on a wall: logs along the slope (a gable), across it (an eave knee wall) or
    // siding boards, cut by the ceiling boards' plane
    const planes = shape === 'ridge' ? [[[0, 1, -0.5], 0.5 - RT], [[0, 1, 0.5], 1.0 - RT]] : [[[0, 1, -0.5], ys(shape, 0) - RT]];
    P.push(clipped(alongZ('x', {}), planes, 'roof/infill_log_along_' + shape));
    P.push(clipped(logsAlongX('x', XC), planes, 'roof/infill_log_across_' + shape));
    P.push(clipped(logsAlongX('x', XC, {ends: false, tops: false}).merge(alongZ('x', {ends: false})), planes, 'roof/infill_log_corner_' + shape));
    // 2026-10-14 (user: the logs under the addition's east-falling roof sat half a course off the wall): a roof facing
    // east / west is turned a quarter in game, which turned the X logs' courses (1/6, 1/2, 5/6) onto Z logs and back. Its
    // fill (_q) is drawn with the courses swapped, so once turned it is the wall's own run_x / run_z / core again.
    P.push(clipped(turned(logsAlongX('x', XC), 1), planes, `roof/infill_log_along_${shape}_q`));
    P.push(clipped(logsAlongX('x', ZC, {off: 1 / 6}), planes, `roof/infill_log_across_${shape}_q`));
    P.push(clipped(turned(logsAlongX('x', XC, {ends: false, tops: false}), 1).merge(logsAlongX('x', ZC, {ends: false, off: 1 / 6})), planes,
      `roof/infill_log_corner_${shape}_q`));
    for (const c of ['brown', 'grey']) {
      const b = box(new Piece('x'), c + '_board_siding', [0, 0, 0], [1, 1, 1], 'nsewd');
      P.push(clipped(b, planes, `roof/infill_board_${c}_${shape}`));
      // 2026-10-14: the wall's battens carry on up the fill (per side of the roof's north frame, CabinBlocks.Roof picks the
      // sides the wall below has them on)
      SIDES.forEach((s, r) => P.push(clipped(turned(battens(c), r), planes, `roof/infill_battens_${c}_${shape}_${s}`)));
    }
  }
  return P;
}

// =============================================================================================== doors, windows
// Facing north frame. The opening is the block: 5 cm linings on the jambs and the head (1.95 m clear), casings 12 cm
// wide on both faces standing proud of the log faces (z -0.095..-0.07 and 1.07..1.095). Doors as vanilla's: closed at
// the south face (the panel z 0.92..0.965, inside vanilla's 13..16 shape), hinge right = the east (x 1) jamb, open
// swung a quarter into the opening against the hinge jamb.
// M1.1 (user 2026-10-12, a window standing alone turned see-through from the side and from above): every frame, sash and
// panel is a closed solid (solid(): the union's outer faces only), no single-sided linings or casings; the head lining
// runs over the jambs to the block edges.
const J = 0.05, F0 = -0.07, F1 = 1.07, PZ0 = 0.92, PZ1 = 0.965;
/** Casing boxes on both faces: the side casings from lo (north) / loS (south) up to hi, a head casing over them. */
function casings(lo, hi, head, loS = lo) {
  const B = [];
  for (const [z0, z1, l] of [[-0.095, F0, lo], [F1, 1.095, loS]]) {
    B.push([TRIM_V, [-0.07, l, z0], [J, hi, z1]], [TRIM_V, [1 - J, l, z0], [1.07, hi, z1]]);
    if (head) B.push([TRIM, [-0.07, 0.95, z0], [1.07, 1.07, z1]]);
  }
  return B;
}
/** Lining boxes: the jambs up to top, the head lining (0.95..1) across the whole opening when head. */
const linings = (top, head) => [...(head ? [[TRIM, [0, 0.95, F0], [1, 1, F1]]] : []), [TRIM_V, [0, 0, F0], [J, top, F1]], [TRIM_V, [1 - J, 0, F0], [1, top, F1]]];
/** The frame's boxes: linings, the threshold (lower half), casings. */
function doorFrameBoxes(half) {
  const up = half === 'upper', top = up ? 0.95 : 1;
  return [...linings(top, up), ...(up ? [] : [[TRIM, [J, 0, F0], [1 - J, 0.022, F1]]]), ...casings(0, top, up)];
}
const doorFrame = (name, half) => solid(new Piece(name), doorFrameBoxes(half));
/** A closed door panel, hinge right (x 1 side), the handle side at x 0. */
function doorPanel(type, half) {
  const pc = new Piece('p'), top = half === 'upper' ? 0.95 : 1, B = 'oak_door_boards', I = 'cabin_iron', S = [];
  if (type === 'cabin' && half === 'upper') {   // glazed: stiles and rails, a muntin cross, the glass
    S.push([TRIM_V, [J, 0, PZ0], [J + 0.1, top, PZ1]], [TRIM_V, [1 - J - 0.1, 0, PZ0], [1 - J, top, PZ1]],
      [TRIM, [J + 0.1, 0.83, PZ0], [1 - J - 0.1, top, PZ1]], [TRIM, [J + 0.1, 0, PZ0], [1 - J - 0.1, 0.1, PZ1]],
      ['cabin_sash', [0.488, 0.1, PZ0 + 0.008], [0.512, 0.83, PZ1 - 0.008]],
      ['cabin_sash', [J + 0.1, 0.453, PZ0 + 0.008], [1 - J - 0.1, 0.477, PZ1 - 0.008]]);
    const zm = (PZ0 + PZ1) / 2;
    box(pc, 'cabin_glass', [J + 0.1, 0.1, zm], [1 - J - 0.1, 0.83, zm], 'ns');
  } else S.push([{n: B, s: B, all: TRIM_V}, [J, 0, PZ0], [1 - J, top, PZ1]]);
  if (type === 'board') {   // ledges inside, strap hinges outside
    const [l0, l1] = half === 'upper' ? [0.66, 0.78] : [0.14, 0.26], hy = half === 'upper' ? 0.7 : 0.18;
    S.push([TRIM, [J + 0.04, l0, PZ0 - 0.025], [1 - J - 0.04, l1, PZ0]], [I, [0.5, hy, PZ1], [1 - J - 0.01, hy + 0.04, PZ1 + 0.007]]);
  }
  if (half === 'lower') {   // the handle (both faces) and its plate
    for (const [z0, z1, f] of [[PZ1, PZ1 + 0.025, 's'], [PZ0 - 0.025, PZ0, 'n']])
      S.push([I, [0.1, 0.84, z0], [0.125, 0.98, z1]], [I, [0.09, 0.82, f === 's' ? PZ1 : PZ0 - 0.004], [0.135, 1.0, f === 's' ? PZ1 + 0.004 : PZ0]]);
  }
  return solid(pc, S);
}
/** The open panel: the closed one swung a quarter about the hinge line (x 1 - J, z PZ0). */
const swing = ([x, y, z]) => [1 - J - (z - PZ0), y, PZ0 + (x - (1 - J))];
function swung(pc) {
  const out = new Piece(pc.name);
  return out.merge(pc, swing, ([nx, ny, nz]) => [-nz, ny, nx]);
}
function doorPieces() {
  const P = [];
  for (const type of ['cabin', 'board']) for (const half of ['lower', 'upper']) {
    P.push(doorFrame(`door/${type}_frame_${half}`, half));
    const closed = doorPanel(type, half), open = swung(closed);
    const nm = (h, o) => `door/${type}_panel_${half}_${h}_${o}`;
    P.push(turned(closed, 0, nm('right', 'closed')), turned(open, 0, nm('right', 'open')));
    P.push(mirroredX(closed, nm('left', 'closed')), mirroredX(open, nm('left', 'open')));
  }
  return P;
}
// windows: outside on the north (z < 0); the upper sash in the outer track, the lower in the inner one
/** One sash's boxes (stiles, rails, the muntins) into S; its glass into pc. */
function sashFrame(pc, S, z0, z1, y0, y1, rails, muntinY) {
  const T = 'cabin_sash', a = J, b = 1 - J, w = 0.045;
  S.push([T, [a, y0, z0], [a + w, y1, z1]], [T, [b - w, y0, z0], [b, y1, z1]]);
  for (const [r0, r1] of rails) S.push([T, [a + w, r0, z0], [b - w, r1, z1]]);
  const [lo, hi] = [rails[0][1], rails[rails.length - 1][0]];   // the glass between the bottom and top rails
  S.push([T, [0.491, lo, z0 + 0.008], [0.509, hi, z1 - 0.008]]);
  if (muntinY != null) S.push([T, [a + w, muntinY - 0.009, z0 + 0.008], [b - w, muntinY + 0.009, z1 - 0.008]]);
  const zm = (z0 + z1) / 2;
  box(pc, 'cabin_glass', [a + w, lo, zm], [b - w, hi, zm], 'ns');
}
/** A window part's frame boxes: the sill, stool and apron, the linings, the casings. */
function windowFrame(part) {
  const hasSill = part !== 'top', hasHead = part !== 'bottom', top = hasHead ? 0.95 : 1, bot = hasSill ? 0.04 : 0;
  // the sill, stool and apron first: they own the cells they share with the jambs (one grain along them)
  const S = hasSill ? [[TRIM, [-0.06, 0, -0.13], [1.06, 0.04, 0.3]], [TRIM, [-0.06, 0.03, 0.3], [1.06, 0.065, 1.12]], [TRIM, [-0.03, -0.08, F1], [1.03, 0.03, 1.09]]] : [];
  S.push(...linings(top, hasHead), ...casings(bot, top, hasHead, hasSill ? 0.065 : 0));
  return S;
}
/** A window part's sashes, sashFrame's arguments: z0, z1 (the upper sash in the outer track), y0, y1, rails, muntin. */
function windowSashes(part) {
  const U = [0.06, 0.105], L = [0.11, 0.155];
  if (part === 'single') return [[...L, 0.04, 0.515, [[0.04, 0.1], [0.48, 0.515]], null], [...U, 0.485, 0.95, [[0.485, 0.52], [0.9, 0.95]], null]];
  if (part === 'bottom') return [[...L, 0.04, 1, [[0.04, 0.1], [0.965, 1]], 0.53]];
  return [[...U, 0, 0.95, [[0, 0.035], [0.89, 0.95]], 0.46]];
}
function windowPiece(part) {
  const pc = new Piece('window/' + part), S = windowFrame(part);
  for (const s of windowSashes(part)) sashFrame(pc, S, ...s);
  return solid(pc, S);
}

// =============================================================================================== siding, porch, pipe
/** The north face's battens over the board joints. */
function battens(c, name = 'x') {
  const b = new Piece(name), t = c + '_board_siding';
  for (let k = 0; k < 4; k++) { const x = 0.125 + 0.25 * k; box(b, t, [x - 0.025, 0, -0.022], [x + 0.025, 1, 0], 'nweud'); }
  return b;
}
function sidingPieces() {
  const P = [];
  for (const c of ['brown', 'grey']) {
    P.push(box(new Piece(`siding/${c}_core`), c + '_board_siding', [0, 0, 0], [1, 1, 1], 'nsewud'));
    P.push(battens(c, `siding/${c}_battens`));
  }
  return P;
}
/** The post's faces (x and z a..b); the railing from the bottom rail's underside to the top rail's top, the top rail's half width. */
const POST_A = 0.42, POST_B = 0.58, RAIL_LO = 0.06, RAIL_HI = 0.94, RAIL_HALF = 0.06;
function porchPieces() {
  const post = new Piece('porch/post'), a = POST_A, b = POST_B, c = 0.015;
  const oct = [[a + c, a], [b - c, a], [b, a + c], [b, b - c], [b - c, b], [a + c, b], [a, b - c], [a, a + c]];
  for (let i = 0; i < 8; i++) {
    const [x0, z0] = oct[i], [x1, z1] = oct[(i + 1) % 8], mid = [(x0 + x1) / 2 - 0.5, 0, (z0 + z1) / 2 - 0.5];
    polyOut(post, TRIM_V, [[x0, 0, z0], [x1, 0, z1], [x1, 1, z1], [x0, 1, z0]], mid);
  }
  polyOut(post, 'white_oak_log_end', oct.map(([x, z]) => [x, 1, z]), [0, 1, 0], {uv: oct.map(([x, z]) => [x, z])});
  polyOut(post, 'white_oak_log_end', oct.map(([x, z]) => [x, 0, z]), [0, -1, 0], {uv: oct.map(([x, z]) => [x, z])});
  const rail = new Piece('porch/railing');   // along x
  box(rail, TRIM, [0, 0.86, 0.5 - RAIL_HALF], [1, RAIL_HI, 0.5 + RAIL_HALF], 'nsudwe');
  box(rail, TRIM, [0, RAIL_LO, 0.46], [1, 0.12, 0.54], 'nsudwe');
  for (let k = 0; k < 8; k++) { const x = 0.0625 + 0.125 * k; box(rail, TRIM_V, [x - 0.019, 0.12, 0.481], [x + 0.019, 0.86, 0.519], 'nswe'); }
  const stubR = new Piece('porch/post_rail');   // north: from the post's face out to the block edge
  box(stubR, TRIM, [0.44, 0.86, 0], [0.56, 0.94, a], 'udwe');
  box(stubR, TRIM, [0.46, 0.06, 0], [0.54, 0.12, a], 'udwe');
  for (const z of [0.0625, 0.1875, 0.3125]) box(stubR, TRIM_V, [0.481, 0.12, z - 0.019], [0.519, 0.86, z + 0.019], 'nswe');
  return [post, rail, stubR];
}
/**
 * M1.1 porch deck (white_oak_porch_deck, axis = the boards' direction): 3 cm boards (top at the block top, 0.97..1), two
 * 5 x 20 cm joists across them, on an open side a 3.5 cm rim board (0.74..0.97) under a 2.5 cm nosing, the corner square of
 * the nosing where two open sides meet; porch supports (M1.2, user's pick: simple and regular, not the plinth's rubble):
 * a 34 cm squared stone block 22 cm high on the ground and a 14 cm oak post on it up to the joists (0.77), one at an open
 * corner (set 3 cm in from both rims), one at the middle of an open edge cell (CabinBlocks.PorchDeck: every second cell
 * and under a post). Each is one closed solid. The rim and nosing pieces are made per side here (their board texture must not turn with them); the
 * piers turn in game (CabinBlocks.PorchDeck).
 */
export const DECK_BOT = 0.97, RIM_BOT = 0.74, JOIST_BOT = 0.77, PIER_STONE = 0.17, PIER_STONE_TOP = 0.22, PIER_POST = 0.07;
const DECK = 'oak_deck_boards', SIDES = ['n', 'e', 's', 'w'], CORNERS = ['ne', 'se', 'sw', 'nw'];
const JOIST = 0.025, RIM = 0.035, NOSING = 0.025, PIERS = [['porch/pier_edge', 0.5], ['porch/pier_corner', 0.8]];
/** The boards and the two joists across them (axis = the boards' direction). */
const deckBoxes = axis => [[DECK, [0, DECK_BOT, 0], [1, 1, 1]], ...[0.25, 0.75].map(c => [TRIM,
  axis === 'x' ? [c - JOIST, JOIST_BOT, 0] : [0, JOIST_BOT, c - JOIST], axis === 'x' ? [c + JOIST, DECK_BOT, 1] : [1, DECK_BOT, c + JOIST]])];
/** A north rim board under the nosing; the nosing's corner square at the north-east. */
const RIM_BOXES = [[TRIM, [0, RIM_BOT, 0], [1, DECK_BOT, RIM]], [DECK, [0, DECK_BOT, -NOSING], [1, 1, 0]]];
const DECK_CORNER_BOX = [DECK, [1, DECK_BOT, -NOSING], [1 + NOSING, 1, 0]];
/** A support on the north side centred at x cx: the squared stone block, the oak post up to the joists. */
const pierBoxes = cx => [['stone_footing', [cx - PIER_STONE, 0, 0.03], [cx + PIER_STONE, PIER_STONE_TOP, 0.37]],
  [TRIM_V, [cx - PIER_POST, PIER_STONE_TOP, 0.2 - PIER_POST], [cx + PIER_POST, JOIST_BOT, 0.2 + PIER_POST]]];
function deckPieces() {
  const P = [];
  for (const axis of ['x', 'z']) {
    const sw = {swap: axis === 'z'}, deck = new Piece('porch/deck_' + axis), [boards, ...joists] = deckBoxes(axis);
    box(deck, DECK, boards[1], boards[2], 'ud', {all: sw});
    for (const [t, a, b] of joists) box(deck, t, a, b, axis === 'x' ? 'wed' : 'nsd');
    P.push(deck);
    const rim = new Piece('x');   // north
    box(rim, TRIM, RIM_BOXES[0][1], RIM_BOXES[0][2], 'nsdwe');
    box(rim, DECK, RIM_BOXES[1][1], RIM_BOXES[1][2], 'nudwe', {all: sw});
    const corner = box(new Piece('x'), DECK, DECK_CORNER_BOX[1], DECK_CORNER_BOX[2], 'neud', {all: sw});   // north-east
    for (let r = 0; r < 4; r++) {
      P.push(turned(rim, r, `porch/deck_rim_${axis}_${SIDES[r]}`));
      P.push(turned(corner, r, `porch/deck_corner_${axis}_${CORNERS[r]}`));
    }
  }
  for (const [name, cx] of PIERS) P.push(solid(new Piece(name), pierBoxes(cx)));   // north edge, north-east corner
  return P;
}
/** The pipe's radius; the rain cap: the pipe stops at PIPE_TOP, three legs up to the cone (radius CAP_R) from CAP_Y0 to CAP_Y1. */
const PIPE_R = 0.075, PIPE_TOP = 0.55, CAP_R = 0.16, CAP_Y0 = 0.62, CAP_Y1 = 0.75;
function pipePieces() {
  const pipe = cylinder(new Piece('pipe/stove_pipe'), 'stove_pipe', 'stove_pipe', 'y', [0.5, 0.5], 0, 1, PIPE_R, 16, 'a');
  const cap = cylinder(new Piece('pipe/stove_pipe_cap'), 'stove_pipe', 'stove_pipe', 'y', [0.5, 0.5], 0, PIPE_TOP, PIPE_R, 16, 'ab');
  const S = 'stove_pipe', R = CAP_R, y0 = CAP_Y0, apex = [0.5, CAP_Y1, 0.5];
  for (let i = 0; i < 16; i++) {
    const a0 = i / 16 * Math.PI * 2, a1 = (i + 1) / 16 * Math.PI * 2, p0 = [0.5 + Math.cos(a0) * R, y0, 0.5 + Math.sin(a0) * R], p1 = [0.5 + Math.cos(a1) * R, y0, 0.5 + Math.sin(a1) * R];
    const mid = [Math.cos((a0 + a1) / 2), 1.2, Math.sin((a0 + a1) / 2)];
    polyOut(cap, S, [p0, p1, apex], mid, {uv: [[i / 16, 1], [(i + 1) / 16, 1], [(i + 0.5) / 16, 0]]});
  }
  polyOut(cap, S, Array.from({length: 16}, (_, i) => { const a = i / 16 * Math.PI * 2; return [0.5 + Math.cos(a) * R, y0, 0.5 + Math.sin(a) * R]; }), [0, -1, 0], {proj: 'y'});
  for (let k = 0; k < 3; k++) { const a = k / 3 * Math.PI * 2, x = 0.5 + Math.cos(a) * 0.09, z = 0.5 + Math.sin(a) * 0.09; box(cap, S, [x - 0.007, 0.55, z - 0.007], [x + 0.007, y0, z + 0.007], 'nswe'); }
  return [pipe, cap, floorPipe()];
}
/**
 * 2026-10-14 (M2 wrap-up): the pipe through a plank floor (the old house's attic floor at (8, 4, 1) was a 1 m square
 * hole round it). The cell is drawn as the floor: white oak plank faces (the planks block's own texture, projected like a
 * cube face, so they run on from the neighbours) round a FLOOR_BOX square shaft; under the ceiling a black steel trim
 * plate, on the attic floor a firestop plate, both FLOOR_PLATE square with the pipe through; the shaft is closed by them.
 */
const FLOOR_BOX = 0.15, FLOOR_PLATE = 0.2, FLOOR_GAP = 0.004, PLANKS = NS + 'block/wood/white_oak_planks';
function floorPipe() {
  const pc = cylinder(new Piece('pipe/stove_pipe_floor'), 'stove_pipe', 'stove_pipe', 'y', [0.5, 0.5], 0, 1, PIPE_R, 16);
  const a = 0.5 - FLOOR_BOX, b = 0.5 + FLOOR_BOX;
  box(pc, PLANKS, [0, 0, 0], [1, 1, 1], 'nsew');
  for (const [y, f] of [[1, 'u'], [0, 'd']])
    for (const [x0, z0, x1, z1] of [[0, 0, 1, a], [0, b, 1, 1], [0, a, a, b], [b, a, 1, b]]) box(pc, PLANKS, [x0, y, z0], [x1, y, z1], f);
  const p0 = 0.5 - FLOOR_PLATE, p1 = 0.5 + FLOOR_PLATE;
  for (const [y, s] of [[1 + FLOOR_GAP, 1], [-FLOOR_GAP, -1]]) {
    // a square ring: 16 corners on the square (its corners among them) to 16 on the pipe
    const sq = i => { const t = i / 16 * Math.PI * 2, c = Math.cos(t), n = Math.sin(t), k = FLOOR_PLATE / Math.max(Math.abs(c), Math.abs(n)); return [0.5 + c * k, y, 0.5 + n * k]; };
    const rd = i => { const t = i / 16 * Math.PI * 2; return [0.5 + Math.cos(t) * (PIPE_R + FLOOR_GAP), y, 0.5 + Math.sin(t) * (PIPE_R + FLOOR_GAP)]; };
    for (let i = 0; i < 16; i++) polyOut(pc, 'stove_pipe', [sq(i), sq(i + 1), rd(i + 1), rd(i)], [0, s, 0]);
    box(pc, 'stove_pipe', [p0, Math.min(y, y - s * FLOOR_GAP), p0], [p1, Math.max(y, y - s * FLOOR_GAP), p1], 'nsew');
  }
  return pc;
}

export function pieces() {
  return [...logWallPieces(), ...roofPieces(), ...doorPieces(), ...['single', 'bottom', 'top'].map(windowPiece), ...sidingPieces(), ...porchPieces(), ...deckPieces(), ...pipePieces()];
}

// =============================================================================================== shapes
// Outline and collision (user 2026-10-13: the house had cube outlines and invisible walls where the meshes are thinner or
// thicker): boxes wrapping each mesh piece, written to CabinShapeData.java (pixels, the pieces' north frame) and turned per
// state by CabinBlocks. They follow the pieces' own boxes and constants above, so the two cannot drift (--check). Left out
// as too small to point at: the roofs' eave and rake trims, standing seams and corrugation, door handles and ledges, the
// battens on board and batten, the pads behind wall fixtures (the fixtures' outlines include them).
export const ROOF_STEPS = 8;
const SHAPE_FILE = path.join(ROOT, 'src/main/java/com/antaurora/apofirstlight/cabin/CabinShapeData.java');
const px = v => Math.round(v * 16 * 10000) / 10000 + 0;
const pxBox = ([x0, y0, z0], [x1, y1, z1]) => [x0, y0, z0, x1, y1, z1].map(px);
/** The boxes of a [texture, from, to] list. */
const pxBoxes = list => list.map(([, a, b]) => pxBox(a, b));
/**
 * A roof cell: the slab (steel on top, the ceiling boards RT under it) in ROOF_STEPS steps along the slope, each step from
 * the lowest underside to the highest surface over it (the ridge cap 3.5 cm higher, its roll on top); fill: down to the
 * cell's bottom at least (the fill to a wall below).
 */
function roofShape(shape, fill) {
  const B = [];
  for (let k = 0; k < ROOF_STEPS; k++) {
    const a = k / ROOF_STEPS, b = (k + 1) / ROOF_STEPS, h = [a, b].map(z => ys(shape, z));
    const top = Math.max(...h) + (shape === 'ridge' && b > 0.33 && a < 0.67 ? 0.035 : 0), under = Math.min(...h) - RT;
    B.push(pxBox([0, fill ? Math.min(0, under) : under, a], [1, top, b]));
  }
  if (shape === 'ridge') B.push(pxBox([0, 0.785, 0.485], [1, 0.81, 0.515]));
  return B;
}
/** The closed door panel's box (hinge right) and the same swung open. */
const panelBox = half => [[J, 0, PZ0], [1 - J, half === 'upper' ? 0.95 : 1, PZ1]];
function swungBox([a, b]) {
  const p = swing(a), q = swing(b);
  return [[0, 1, 2].map(i => Math.min(p[i], q[i])), [0, 1, 2].map(i => Math.max(p[i], q[i]))];
}
export function shapeData() {
  const halves = ['lower', 'upper'], parts = ['single', 'bottom', 'top'], sides = ['neg', 'mid', 'pos'];
  const sashes = part => windowSashes(part).map(([z0, z1, y0, y1]) => pxBox([J, y0, z0], [1 - J, y1, z1]));
  const capLegs = 0.09 + 0.007;
  const D = [
    ['white_oak_log_wall: a run along X (the logs bulge LS out of both z faces) / along Z, out to the log crests', [
      ['LOG_X', [pxBox([0, 0, -LS], [1, 1, 1 + LS])]], ['LOG_Z', [pxBox([-LS, 0, 0], [1 + LS, 1, 1])]]]],
    ['A corner\'s round log ends beyond its open sides: west of the cell (X logs) / north (Z logs); neg, mid, pos across the wall', [
      ['LOG_STUB_X', sides.map(s => [pxBox([-SL, 0, stubC(s) - SR], [0, 1, stubC(s) + SR])])],
      ['LOG_STUB_Z', sides.map(s => [pxBox([stubC(s) - SR, 0, -SL], [stubC(s) + SR, 1, 0])])]]],
    ['Roofs, lower / upper / ridge (facing north: the eave at z 0): the slab in ROOF_STEPS steps; ROOF_FILL with the fill down to a wall', [
      ['ROOF', SHAPES.map(s => roofShape(s, false))], ['ROOF_FILL', SHAPES.map(s => roofShape(s, true))]]],
    ['Doors, lower / upper half: the frame (linings, threshold, casings on both faces); the panel closed / open, hinge right (the east jamb)', [
      ['DOOR_FRAME', halves.map(h => pxBoxes(doorFrameBoxes(h)))], ['DOOR_PANEL_CLOSED', halves.map(h => [pxBox(...panelBox(h))])],
      ['DOOR_PANEL_OPEN', halves.map(h => [pxBox(...swungBox(panelBox(h)))])]]],
    ['The double-hung window, single / bottom / top: sill, stool, apron, linings, casings, each sash as a slab (its glass is in it)', [
      ['WINDOW', parts.map(p => [...pxBoxes(windowFrame(p)), ...sashes(p)])]]],
    ['Porch deck: the boards and joists (boards along X / Z); a north rim board and nosing; the nosing\'s north-east corner; the supports (north edge, north-east corner)', [
      ['DECK_X', pxBoxes(deckBoxes('x'))], ['DECK_Z', pxBoxes(deckBoxes('z'))], ['DECK_RIM', pxBoxes(RIM_BOXES)], ['DECK_CORNER', pxBoxes([DECK_CORNER_BOX])],
      ['PIER_EDGE', pxBoxes(pierBoxes(0.5))], ['PIER_CORNER', pxBoxes(pierBoxes(0.8))]]],
    ['Porch railing along X and a post\'s rail stub toward the north: outline (the rails and balusters), collision 1.5 high like a fence', [
      ['RAILING', [pxBox([0, RAIL_LO, 0.5 - RAIL_HALF], [1, RAIL_HI, 0.5 + RAIL_HALF])]], ['RAILING_COLLISION', [pxBox([0, 0, 7 / 16], [1, 1.5, 9 / 16])]],
      ['POST', [pxBox([POST_A, 0, POST_A], [POST_B, 1, POST_B])]],
      ['POST_RAIL', [pxBox([0.5 - RAIL_HALF, RAIL_LO, 0], [0.5 + RAIL_HALF, RAIL_HI, POST_A])]], ['POST_RAIL_COLLISION', [pxBox([7 / 16, 0, 0], [9 / 16, 1.5, POST_A])]]]],
    ['Stove pipe: running on; ending in the rain cap (the pipe, the legs, the cone)', [
      ['PIPE', [pxBox([0.5 - PIPE_R, 0, 0.5 - PIPE_R], [0.5 + PIPE_R, 1, 0.5 + PIPE_R])]],
      ['PIPE_CAPPED', [pxBox([0.5 - PIPE_R, 0, 0.5 - PIPE_R], [0.5 + PIPE_R, PIPE_TOP, 0.5 + PIPE_R]),
        pxBox([0.5 - capLegs, PIPE_TOP, 0.5 - capLegs], [0.5 + capLegs, CAP_Y0, 0.5 + capLegs]), pxBox([0.5 - CAP_R, CAP_Y0, 0.5 - CAP_R], [0.5 + CAP_R, CAP_Y1, 0.5 + CAP_R])]]]],
  ];
  const boxes = list => '{' + list.map(b => '{' + b.join(', ') + '}').join(', ') + '}';
  const out = ['// Generated by tools/build-cabin-exterior-v1.mjs (shapeData): do not edit, run the script (its --check compares).',
    'package com.antaurora.apofirstlight.cabin;', '',
    '/**',
    ' * The outline and collision boxes of the Cabin Exterior V1 mesh blocks (docs/models/cabin_exterior_v1.md, "Outline and',
    ' * collision"): in pixels of the mesh pieces\' north frame, built from the same boxes and constants as the pieces; CabinBlocks',
    ' * turns them per state.',
    ' */',
    'final class CabinShapeData {',
    `    static final int ROOF_STEPS = ${ROOF_STEPS};`, ''];
  for (const [doc, fields] of D) {
    out.push(`    /** ${doc}. */`);
    for (const [name, v] of fields) {
      const nested = Array.isArray(v[0][0]);
      out.push(`    static final double[]${nested ? '[][]' : '[]'} ${name} = ${nested ? '{' + v.map(boxes).join(',\n            ') + '}' : boxes(v)};`);
    }
    out.push('');
  }
  out.push('    private CabinShapeData() {}', '}', '');
  return out.join('\n');
}

// =============================================================================================== models, states, loot
/** Item views of the mesh blocks: [piece, turn, dx, dy, dz]. */
const ITEMS = {
  white_oak_log_wall: [['log_wall/run_x', 0]],
  green_standing_seam_roof: [['roof/green_lower', 0], ['roof/under_lower', 0], ['roof/green_eave_lower', 0], ['roof/green_rake_neg_lower', 0], ['roof/green_rake_pos_lower', 0]],
  galvanized_corrugated_roof: [['roof/galvanized_lower', 0], ['roof/under_lower', 0], ['roof/galvanized_eave_lower', 0], ['roof/galvanized_rake_neg_lower', 0], ['roof/galvanized_rake_pos_lower', 0]],
  rusty_corrugated_roof: [['roof/rust_lower', 0], ['roof/under_lower', 0], ['roof/rust_eave_lower', 0], ['roof/rust_rake_neg_lower', 0], ['roof/rust_rake_pos_lower', 0]],
  white_oak_cabin_door: [['door/cabin_frame_lower', 0], ['door/cabin_panel_lower_right_closed', 0], ['door/cabin_frame_upper', 0, 0, 1, 0], ['door/cabin_panel_upper_right_closed', 0, 0, 1, 0]],
  white_oak_board_door: [['door/board_frame_lower', 0], ['door/board_panel_lower_right_closed', 0], ['door/board_frame_upper', 0, 0, 1, 0], ['door/board_panel_upper_right_closed', 0, 0, 1, 0]],
  white_oak_double_hung_window: [['window/single', 0]],
  brown_board_and_batten: [['siding/brown_core', 0], ['siding/brown_battens', 0], ['siding/brown_battens', 1], ['siding/brown_battens', 2], ['siding/brown_battens', 3]],
  grey_board_and_batten: [['siding/grey_core', 0], ['siding/grey_battens', 0], ['siding/grey_battens', 1], ['siding/grey_battens', 2], ['siding/grey_battens', 3]],
  white_oak_porch_post: [['porch/post', 0]],
  white_oak_porch_railing: [['porch/railing', 0]],
  white_oak_porch_deck: [['porch/deck_x', 0], ...SIDES.map(s => ['porch/deck_rim_x_' + s, 0]), ...CORNERS.map(c => ['porch/deck_corner_x_' + c, 0]), ...[0, 1, 2, 3].map(r => ['porch/pier_corner', r])],
  stove_pipe: [['pipe/stove_pipe', 0]],
};
const PARTICLE = {white_oak_log_wall: 'white_oak_log_wall', green_standing_seam_roof: 'roof_green', galvanized_corrugated_roof: 'roof_galvanized',
  rusty_corrugated_roof: 'roof_rust', white_oak_cabin_door: 'oak_door_boards', white_oak_board_door: 'oak_door_boards', white_oak_double_hung_window: 'oak_trim',
  brown_board_and_batten: 'brown_board_siding', grey_board_and_batten: 'grey_board_siding', white_oak_porch_post: 'oak_trim_v', white_oak_porch_railing: 'oak_trim', white_oak_porch_deck: 'oak_deck_boards',
  stove_pipe: 'stove_pipe'};
const DOOR_DISPLAY = {gui: {rotation: [30, 225, 0], translation: [0, -2.5, 0], scale: [0.38, 0.38, 0.38]}, ground: {rotation: [0, 0, 0], translation: [0, 3, 0], scale: [0.2, 0.2, 0.2]},
  fixed: {rotation: [0, 0, 0], translation: [0, -4, 0], scale: [0.4, 0.4, 0.4]}, thirdperson_righthand: {rotation: [75, 45, 0], translation: [0, 1, 0], scale: [0.25, 0.25, 0.25]},
  firstperson_righthand: {rotation: [0, 45, 0], translation: [0, -2, 0], scale: [0.3, 0.3, 0.3]}, firstperson_lefthand: {rotation: [0, 225, 0], translation: [0, -2, 0], scale: [0.3, 0.3, 0.3]}};

const loot = (id, extra = {}) => json({type: 'minecraft:block', pools: [{rolls: 1, bonus_rolls: 0, entries: [{type: 'minecraft:item', name: NS + id, ...extra.entry}],
  conditions: [{condition: 'minecraft:survives_explosion'}, ...(extra.conditions || [])]}], random_sequence: `${NS}blocks/${id}`});

/**
 * The stairs and slab models (M1.1, user 2026-10-12: the stairs were paler than the porch deck): the porch deck's boards
 * (oak_deck_boards, oiled white oak) on every face, vanilla's stair and slab shapes. A stair's treads (up / down faces)
 * are turned a quarter, so their boards run along the nosing (the default model faces east, its nosing along z), and the
 * blockstate has no uvlock, so the boards turn with the stairs. Tread UVs: u = z, v = 16 - x (vanilla's 90 degree face
 * rotation), so the boards line up across the elements and four boards fill a half-block tread.
 */
function stairModels() {
  const T = TEX + 'oak_deck_boards', CULL = {down: 'down', up: 'up', north: 'north', south: 'south', west: 'west', east: 'east'};
  // one element: faces {dir: cull?}; side UVs as vanilla's (u along the face, v from the top), treads turned
  const el = (from, to, faces, turn) => {
    const [x0, y0, z0] = from, [x1, y1, z1] = to, f = {};
    for (const [d, cull] of Object.entries(faces)) {
      let uv, rot = 0;
      if (d === 'up' || d === 'down') {
        if (turn) { uv = d === 'up' ? [z0, 16 - x1, z1, 16 - x0] : [16 - z1, 16 - x1, 16 - z0, 16 - x0]; rot = 90; }
        else uv = d === 'up' ? [x0, z0, x1, z1] : [x0, 16 - z1, x1, 16 - z0];
      } else {
        const [ua, ub] = d === 'north' ? [16 - x1, 16 - x0] : d === 'south' ? [x0, x1] : d === 'west' ? [z0, z1] : [16 - z1, 16 - z0];
        uv = [ua, 16 - y1, ub, 16 - y0];
      }
      f[d] = {uv, texture: '#all', ...(rot ? {rotation: rot} : {}), ...(cull ? {cullface: CULL[d]} : {})};
    }
    return {from, to, faces: f};
  };
  const all6 = {down: true, up: false, north: true, south: true, west: true, east: true};
  const lower = turn => el([0, 0, 0], [16, 8, 16], all6, turn);
  const model = (elements, display) => ({parent: 'minecraft:block/block', ...(display ? {display} : {}), textures: {particle: T, all: T}, elements});
  const STAIR_DISPLAY = {gui: {rotation: [30, 135, 0], translation: [0, 0, 0], scale: [0.625, 0.625, 0.625]}, head: {rotation: [0, -90, 0], translation: [0, 0, 0], scale: [1, 1, 1]},
    thirdperson_lefthand: {rotation: [75, -135, 0], translation: [0, 2.5, 0], scale: [0.375, 0.375, 0.375]}};   // vanilla block/stairs
  const stepBack = el([8, 8, 0], [16, 16, 16], {up: true, north: true, south: true, west: false, east: true}, true);
  return {
    stairs: model([lower(true), stepBack], STAIR_DISPLAY),
    stairs_inner: model([lower(true), stepBack, el([0, 8, 8], [8, 16, 16], {up: true, north: false, south: true, west: true}, true)]),
    stairs_outer: model([lower(true), el([8, 8, 8], [16, 16, 16], {up: true, north: false, south: true, west: false, east: true}, true)]),
    slab: model([lower(false)]),
    slab_top: model([el([0, 8, 0], [16, 16, 16], {down: false, up: true, north: true, south: true, west: true, east: true}, false)]),
    slab_double: model([el([0, 0, 0], [16, 16, 16], {down: true, up: true, north: true, south: true, west: true, east: true}, false)]),
  };
}

function stairsState(m) {
  const base = {east: 0, south: 90, west: 180, north: 270}, variants = {};
  for (const [f, y0] of Object.entries(base)) for (const half of ['bottom', 'top']) for (const shape of ['straight', 'inner_left', 'inner_right', 'outer_left', 'outer_right']) {
    const model = shape === 'straight' ? m('stairs') : shape.startsWith('inner') ? m('stairs_inner') : m('stairs_outer');
    let y = y0;
    if (half === 'bottom' && shape.endsWith('left')) y = y0 - 90;
    if (half === 'top' && shape.endsWith('right')) y = y0 + 90;
    y = ((y % 360) + 360) % 360;
    const v = {model};
    if (half === 'top') v.x = 180;
    if (y) v.y = y;
    variants[`facing=${f},half=${half},shape=${shape}`] = v;
  }
  return {variants};
}

export function outputs() {
  const out = [];
  const texOut = (name, maps) => maps.forEach((m, i) => out.push([path.join(ASSETS, 'textures/block/cabin', name + ['', '_s', '_n'][i] + '.png'), m]));
  for (const [name, [make, height]] of Object.entries(TEXTURES)) texOut(name, renderX(make(), {height}));
  fieldstoneTiles().forEach((maps, t) => texOut('fieldstone_' + t, maps));   // one 3 x 2 block texture in tiles (MacroTileModel)
  for (const pc of pieces()) out.push([path.join(ASSETS, 'cabin_mesh', pc.name + '.json'), JSON.stringify(serialise(pc, TEX)) + '\n']);
  out.push([SHAPE_FILE, shapeData()]);
  const bm = id => `${NS}block/cabin/${id}`;
  for (const b of BLOCKS) {
    const id = b.id;
    if (b.mesh) {
      const model = {parent: 'minecraft:block/block', loader: NS + 'cabin', textures: {particle: TEX + PARTICLE[id]}, item: ITEMS[id]};
      if (id.endsWith('_door')) model.display = DOOR_DISPLAY;
      out.push([path.join(ASSETS, 'models/block/cabin', id + '.json'), json(model)]);
      out.push([path.join(ASSETS, 'blockstates', id + '.json'), json({variants: {'': {model: bm(id)}}})]);
      out.push([path.join(ASSETS, 'models/item', id + '.json'), json({parent: bm(id)})]);
    }
    const lootExtra = id.endsWith('_door') ? {conditions: [{condition: 'minecraft:block_state_property', block: NS + id, properties: {half: 'lower'}}]}
      : id === 'white_oak_slab' ? {entry: {functions: [{function: 'minecraft:set_count', count: 2, add: false, conditions: [{condition: 'minecraft:block_state_property', block: NS + id, properties: {type: 'double'}}]}, {function: 'minecraft:explosion_decay'}]}} : {};
    out.push([path.join(DATA, 'apocalypse_firstlight/loot_tables/blocks', id + '.json'), loot(id, lootExtra)]);
  }
  // JSON-model blocks
  const fsTiles = Object.fromEntries(Array.from({length: FS_COLS * FS_ROWS}, (_, t) => ['tile' + t, TEX + 'fieldstone_' + t]));
  out.push([path.join(ASSETS, 'models/block/cabin/fieldstone.json'), json({parent: 'minecraft:block/block', loader: NS + 'macro_tile', columns: FS_COLS, rows: FS_ROWS,
    textures: {particle: TEX + 'fieldstone_0', ...fsTiles}})]);
  out.push([path.join(ASSETS, 'blockstates/fieldstone.json'), json({variants: {'': {model: bm('fieldstone')}}})]);
  out.push([path.join(ASSETS, 'models/item/fieldstone.json'), json({parent: bm('fieldstone')})]);
  out.push([path.join(ASSETS, 'models/block/cabin/firewood_stack.json'), json({parent: 'minecraft:block/cube_column', textures: {end: TEX + 'firewood_end', side: TEX + 'firewood_side'}})]);
  out.push([path.join(ASSETS, 'models/block/cabin/firewood_stack_horizontal.json'), json({parent: 'minecraft:block/cube_column_horizontal', textures: {end: TEX + 'firewood_end', side: TEX + 'firewood_side'}})]);
  out.push([path.join(ASSETS, 'blockstates/firewood_stack.json'), json({variants: {'axis=y': {model: bm('firewood_stack')}, 'axis=z': {model: bm('firewood_stack_horizontal'), x: 90},
    'axis=x': {model: bm('firewood_stack_horizontal'), x: 90, y: 90}}})]);
  out.push([path.join(ASSETS, 'models/item/firewood_stack.json'), json({parent: bm('firewood_stack_horizontal')})]);
  for (const [k, model] of Object.entries(stairModels())) out.push([path.join(ASSETS, `models/block/cabin/white_oak_${k}.json`), json(model)]);
  out.push([path.join(ASSETS, 'blockstates/white_oak_stairs.json'), json(stairsState(k => bm('white_oak_' + k)))]);
  out.push([path.join(ASSETS, 'blockstates/white_oak_slab.json'), json({variants: {'type=bottom': {model: bm('white_oak_slab')}, 'type=top': {model: bm('white_oak_slab_top')},
    'type=double': {model: bm('white_oak_slab_double')}}})]);
  out.push([path.join(ASSETS, 'models/item/white_oak_stairs.json'), json({parent: bm('white_oak_stairs')})]);
  out.push([path.join(ASSETS, 'models/item/white_oak_slab.json'), json({parent: bm('white_oak_slab')})]);
  // woven wire fence: a green steel T-post (when post=true) and a wire panel toward each connected side (cut-out)
  const W = TEX + 'woven_wire', TP = TEX + 'fence_t_post';
  out.push([path.join(ASSETS, 'models/block/cabin/woven_wire_fence_post.json'), json({parent: 'minecraft:block/block', render_type: 'minecraft:cutout', textures: {particle: TP, post: TP},
    elements: [{from: [7.4, 0, 7.4], to: [8.6, 20, 8.6], faces: Object.fromEntries(['north', 'south', 'east', 'west'].map(f => [f, {uv: [7.4, 0, 8.6, 16], texture: '#post'}]).concat([['up', {uv: [7.4, 7.4, 8.6, 8.6], texture: '#post'}], ['down', {uv: [7.4, 7.4, 8.6, 8.6], texture: '#post', cullface: 'down'}]]))}]})]);
  out.push([path.join(ASSETS, 'models/block/cabin/woven_wire_fence_side.json'), json({parent: 'minecraft:block/block', render_type: 'minecraft:cutout', ambientocclusion: false, textures: {particle: W, wire: W},
    elements: [{from: [8, 0, 0], to: [8, 16, 8], shade: false, faces: {east: {uv: [8, 0, 16, 16], texture: '#wire'}, west: {uv: [0, 0, 8, 16], texture: '#wire'}}}]})]);
  out.push([path.join(ASSETS, 'models/item/woven_wire_fence.json'), json({parent: 'minecraft:block/block', render_type: 'minecraft:cutout', textures: {particle: W, wire: W, post: TP},
    elements: [{from: [7.4, 0, 1.4], to: [8.6, 20, 2.6], faces: Object.fromEntries(['north', 'south', 'east', 'west', 'up', 'down'].map(f => [f, {uv: [7, 0, 9, 16], texture: '#post'}]))},
      {from: [7.4, 0, 13.4], to: [8.6, 20, 14.6], faces: Object.fromEntries(['north', 'south', 'east', 'west', 'up', 'down'].map(f => [f, {uv: [7, 0, 9, 16], texture: '#post'}]))},
      {from: [8, 0, 2], to: [8, 16, 14], shade: false, faces: {east: {uv: [2, 0, 14, 16], texture: '#wire'}, west: {uv: [2, 0, 14, 16], texture: '#wire'}}}]})]);
  const fparts = [{when: {post: 'true'}, apply: {model: bm('woven_wire_fence_post')}}];
  for (const [f, y] of [['north', 0], ['east', 90], ['south', 180], ['west', 270]]) fparts.push({when: {[f]: 'true'}, apply: {model: bm('woven_wire_fence_side'), ...(y ? {y} : {})}});
  out.push([path.join(ASSETS, 'blockstates/woven_wire_fence.json'), json({multipart: fparts})]);
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
  const mass = readJson('src/main/resources/data/apocalypse_firstlight/item_mass/afl_content_v1.json');
  const massText = JSON.stringify(mass);
  for (const b of BLOCKS) if (!massText.includes(`"${NS}${b.id}"`)) errs.push(`${b.id}: no item mass`);
  for (const lang of ['en_us', 'zh_cn']) {
    const l = readJson(`src/main/resources/assets/apocalypse_firstlight/lang/${lang}.json`);
    for (const b of BLOCKS) { const v = l['block.apocalypse_firstlight.' + b.id]; if (v !== (lang === 'en_us' ? b.en : b.zh)) errs.push(`${b.id}: lang ${lang} = ${v}`); }
  }
  for (const [file, ids] of [['wooden_doors.json', ['white_oak_cabin_door', 'white_oak_board_door']], ['stairs.json', ['white_oak_stairs']], ['slabs.json', ['white_oak_slab']], ['fences.json', ['woven_wire_fence']]])
    for (const id of ids) if (!tagHas(tags + file, id)) errs.push(`${id}: missing from minecraft:${file}`);
  // the log wall pads mirror block/LogMount (its PADS, DEPTH) and the walls that carry them (round_log_walls)
  const lm = fs.readFileSync(path.join(ROOT, 'src/main/java/com/antaurora/apofirstlight/block/LogMount.java'), 'utf8');
  const javaPads = [...lm.slice(lm.indexOf('PADS = {')).split('};')[0].matchAll(/\{([-\d.,\s]+)\}/g)].map(m => m[1].split(',').map(Number));
  if (JSON.stringify(javaPads) !== JSON.stringify(PADS)) errs.push('log wall pads differ from LogMount.PADS');
  if (+(/DEPTH = ([\d.]+)/.exec(lm) || [])[1] !== PAD_DEPTH) errs.push('PAD_DEPTH differs from LogMount.DEPTH');
  if (!readJson('src/main/resources/data/apocalypse_firstlight/tags/blocks/round_log_walls.json').values.includes(NS + 'white_oak_log_wall')) errs.push('white_oak_log_wall: missing from round_log_walls');
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
