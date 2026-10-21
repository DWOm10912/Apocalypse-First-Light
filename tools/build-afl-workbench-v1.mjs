// AFL Workbench V1 (docs/gameplay/afl_workbench_v1_design.md 13-14, 2026-10-21): the first crafting station's block, a
// solid-wood joiner's bench two cells long, one deep and two high against a wall, built from the Blender concept
// (design/furniture/afl_workbench_v1/workbench_v1_concept.png; user 2026-10-21: "可以做，这些储物空间要做成能互动的吧……最好加
// 上平滑的动画……做好PBR"). No crafting screen yet ("先不做UI").
//   frame       walnut legs and back posts (the posts carry the tool board and the top shelf), rails, aprons
//   top         six red maple boards (aged amber), a row of dog holes; a tool well behind them (pine floor)
//   vise        a front vise on the viewer's left: maple jaws, cast iron plate and body, two guide rods, the screw, a T-bar
//   drawers     two drawers under the right half (9 slots each), iron bar pulls; each pulls out 40 cm on its own channel
//   tool board  thirteen white oak boards, walnut rails and header; five pairs of hickory pegs (the tool slots: a tool hangs
//               on a pair head up, client WorkbenchRenderer) and a small-tool strip (six holes, looks only in V1)
//   shelves     a lower shelf of hemlock slats with pine offcuts and a crate; a top shelf on two brackets with a box and tins
// Geometry is written in the concept's frame (metres = blocks: x along the bench from the viewer's left, y from the front
// to the wall, z up) and turned into the furniture frame (blocks, facing north, the master cell x 0..1, front -z) by
// N(x, y, z) = (2 - x, z, y): the drawers' half is the master cell (the block's own left, Wide's), the vise's the right.
// Writes the animated mesh (tools/furniture/furniture-mesh.mjs: source, geo, aflmesh, profile with the channels drawer_left
// and drawer_right, item and block model, atlas), the Mesh Shape profile, blockstate and loot table.
//   node tools/build-afl-workbench-v1.mjs            -> writes every output
//   node tools/build-afl-workbench-v1.mjs --check    -> verifies every output is up to date and the hand-edited data
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {Piece, solid, cylinder, newell, dot, sub, scl, add} from './cabin/cabin-geo.mjs';
import {furnitureMesh} from './furniture/furniture-mesh.mjs';
import {state, profile} from './furniture/furniture-shapes.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const ASSETS = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight');
const DATA = path.join(ROOT, 'src/main/resources/data');
const NS = 'apocalypse_firstlight:', TEX = NS + 'block/furniture/';
const json = v => JSON.stringify(v, null, 2) + '\n';

/** The block: id, item mass (kg, furniture carry scale), carry tag, mining (wood: the axe, no tier), names. */
export const BLOCK = {id: 'wooden_workbench', mass: 70, carry: 'oversized', tool: 'axe', en: 'Workbench', zh: '工作台'};

// materials: Main's walnut furniture set, solid-wood timber faces (240 px, LabPBR), the furniture hardware
const WAL = 'wood', WAL_V = 'wood_v', INSIDE = 'wood_inside', IRON = 'iron', CAST = 'cast_iron', CHROME = NS + 'block/radio/chrome';
const MAPLE = NS + 'block/wood/red_maple_timber_side*0.95,0.82,0.66', OAK = NS + 'block/wood/white_oak_timber_side*0.9,0.8,0.66';
const HEMLOCK = NS + 'block/wood/eastern_hemlock_timber_side', PINE = NS + 'block/wood/white_pine_timber_side*0.92,0.82,0.66';
const HICKORY = NS + 'block/wood/shagbark_hickory_timber_side';

// ================================================================================================ frame
/** A concept-frame point in the furniture frame. */
const N = ([x, y, z]) => [2 - x, z, y];
/** A concept-frame box [[x0, y0, z0], [x1, y1, z1]] in the furniture frame. */
const nbox = ([a, b]) => [[2 - b[0], a[2], a[1]], [2 - a[0], b[2], b[1]]];
/** A concept-frame box as a solid() record (furniture faces: n front, s back, w the concept's +x end, e its -x end). */
const bx = (tex, a, b, opts) => { const [lo, hi] = nbox([a, b]); return [tex, lo, hi, opts]; };
/** Walnut for a member along concept axis ax ('x' along the bench, 'y' front to back, 'z' up): its grain along it. */
const wal = (ax, extra = {}) => ({...(ax === 'x' ? {all: WAL} : ax === 'z' ? {all: WAL_V} : {all: WAL, u: WAL_V, d: WAL_V}), ...extra});
/** A board of a timber texture (grain along v) along concept axis ax, its grain shifted by board number k. */
function board(tex, ax, k, a, b) {
  const off = [(k * 0.377) % 1, (k * 0.613) % 1], sw = {swap: true, off}, pl = {off};
  return bx(tex, a, b, ax === 'x' ? {n: sw, s: sw, u: sw, d: sw, all: pl} : ax === 'z' ? {all: pl} : {w: sw, e: sw, all: pl});
}
/**
 * A cylinder along concept axis ax from a to b (a < b), centred on the other two concept coordinates c ('x': [y, z],
 * 'y': [x, z], 'z': [x, y]); caps 'a' / 'b' at those ends.
 */
function cyl(pc, skin, end, ax, c, a, b, r, seg, caps = '') {
  if (ax === 'x') return cylinder(pc, skin, end, 'x', [c[1], c[0]], 2 - b, 2 - a, r, seg, caps.replace(/[ab]/g, m => m === 'a' ? 'b' : 'a'));
  if (ax === 'y') return cylinder(pc, skin, end, 'z', [2 - c[0], c[1]], a, b, r, seg, caps);
  return cylinder(pc, skin, end, 'y', [c[1], 2 - c[0]], a, b, r, seg, caps);
}
/** A prism: an outline [y, z] in the concept's y-z plane between concept x0 and x1 (convex). */
function prismX(pc, tex, pts, x0, x1) {
  const A = pts.map(([y, z]) => N([x0, y, z])), B = pts.map(([y, z]) => N([x1, y, z]));
  const c = scl([...A, ...B].reduce(add, [0, 0, 0]), 1 / (2 * pts.length));
  const face = (vs, out) => pc.poly(tex, dot(newell(vs), out) < 0 ? [...vs].reverse() : vs);
  face(A, [1, 0, 0]);
  face(B, [-1, 0, 0]);
  for (let i = 0; i < pts.length; i++) {
    const j = (i + 1) % pts.length, q = [A[i], A[j], B[j], B[i]];
    face(q, sub(scl(q.reduce(add, [0, 0, 0]), 0.25), c));
  }
  return pc;
}

// ================================================================================================ dimensions
/** Tool slots: five peg pairs (concept x), the pegs 3.5 cm either side, their tops at PEG_Z; tools hang in the plane y TOOL_Y. */
export const SLOT_X = [0.22, 0.49, 0.76, 1.03, 1.3], PEG_HALF = 0.035, PEG_Z = 1.62, PEG_R = 0.01, TOOL_Y = 0.8;
/** The drawers: the case's inner span (x0..x1) and the front (f0..f1); DRAWER_OUT: how far they pull out. */
export const DRAWERS = {drawer_left: {x0: 1.0, x1: 1.425, f0: 0.985, f1: 1.428}, drawer_right: {x0: 1.435, x1: 1.82, f0: 1.432, f1: 1.838}};
export const DRAWER_OUT = 0.4;
export const TOP_Z = 0.9;

// ================================================================================================ body
function body() {
  const pc = new Piece('workbench_body');
  // legs to the top, back posts on to the top shelf
  solid(pc, [0.07, 1.84].flatMap(x => [bx(wal('z'), [x, 0.07, 0], [x + 0.09, 0.16, 0.83]), bx(wal('z'), [x, 0.86, 0], [x + 0.09, 0.95, 1.86])]));
  // the tool well's floor first (its top face over the end rails'), side rails low and high, long stretchers, the middle
  // bearer under the slats, the front apron (cut for the vise), the back apron up to the tool board
  solid(pc, [
    board(PINE, 'x', 9, [0.04, 0.73, 0.815], [1.96, 0.87, 0.83]),
    ...[0.09, 1.86].flatMap(x => [bx(wal('y'), [x, 0.16, 0.12], [x + 0.05, 0.86, 0.2]), bx(wal('y'), [x, 0.16, 0.7], [x + 0.05, 0.86, 0.83])]),
    bx(wal('x'), [0.16, 0.1, 0.12], [1.84, 0.13, 0.2]), bx(wal('x'), [0.16, 0.88, 0.12], [1.84, 0.91, 0.2]),
    bx(wal('y'), [0.98, 0.13, 0.12], [1.02, 0.88, 0.2]),
    bx(wal('x'), [0.16, 0.09, 0.7], [0.24, 0.125, 0.83]), bx(wal('x'), [0.56, 0.09, 0.7], [0.98, 0.125, 0.83]),
    bx(wal('x'), [0.16, 0.87, 0.7], [1.84, 0.91, 0.92]),
  ]);
  // lower shelf: five hemlock slats on the end rails
  solid(pc, Array.from({length: 5}, (_, k) => board(HEMLOCK, 'x', k, [0.1, 0.17 + 0.14 * k, 0.2], [1.9, 0.29 + 0.14 * k, 0.222])));
  // the drawer case under the right half: two walls and a divider, its bottom and back (the faces toward the drawers plain);
  // the walls' tops 1 mm under the top (at 0.83 they lay in the tool well floor's top face and flickered, 2026-10-21)
  solid(pc, [
    bx(wal('y', {w: INSIDE}), [0.98, 0.085, 0.545], [1.0, 0.86, 0.829]),
    bx(wal('y', {w: INSIDE, e: INSIDE}), [1.425, 0.085, 0.565], [1.435, 0.86, 0.829]),
    bx(wal('y', {e: INSIDE}), [1.82, 0.085, 0.545], [1.84, 0.86, 0.829]),
    bx({all: WAL, u: INSIDE}, [1.0, 0.085, 0.545], [1.82, 0.86, 0.565]),
    bx({all: WAL, n: INSIDE}, [1.0, 0.86, 0.545], [1.82, 0.87, 0.7]),
  ]);
  // the top: six maple boards, 7 cm thick, a row of eight 2 cm dog holes through the front one
  const top = [];
  for (let k = 0; k < 6; k++) {
    const y0 = 0.04 + 0.115 * k, y1 = y0 + 0.115;
    if (k) { top.push(board(MAPLE, 'x', k, [0.04, y0, 0.83], [1.96, y1, TOP_Z])); continue; }
    top.push(board(MAPLE, 'x', 0, [0.04, y0, 0.83], [1.96, 0.085, TOP_Z]), board(MAPLE, 'x', 0, [0.04, 0.105, 0.83], [1.96, y1, TOP_Z]));
    let x = 0.04;
    for (let j = 0; j < 8; j++) { const h = 0.79 + 0.15 * j; top.push(board(MAPLE, 'x', 0, [x, 0.085, 0.83], [h, 0.105, TOP_Z])); x = h + 0.02; }
    top.push(board(MAPLE, 'x', 0, [x, 0.085, 0.83], [1.96, 0.105, TOP_Z]));
  }
  solid(pc, top);
  // tool board: thirteen white oak boards, every other one 3 mm proud; walnut rails across and the header
  // (2026-10-21 z-fight fixes: the boards 5 mm inside the posts' outer faces, the low rail 5 mm below the boards, the
  // header 3 mm under the shelf, the board joints clear of the brackets' faces)
  const W = 1.85 / 13;
  solid(pc, Array.from({length: 13}, (_, k) => board(OAK, 'z', k, [0.075 + W * k, k % 2 ? 0.847 : 0.85, 0.92], [0.075 + W * (k + 1), 0.87, 1.8])));
  solid(pc, [bx(wal('x'), [0.07, 0.832, 0.915], [1.93, 0.85, 0.97]), bx(wal('x'), [0.07, 0.832, 1.27], [1.93, 0.85, 1.32]),
    bx(wal('x'), [0.04, 0.82, 1.78], [1.96, 0.875, 1.857])]);
  // top shelf with a front lip, on two brackets between the slots
  solid(pc, [bx(wal('x'), [0.04, 0.6, 1.86], [1.96, 0.96, 1.89]), bx(wal('x'), [0.04, 0.6, 1.89], [1.96, 0.615, 1.915])]);
  for (const x of [0.625, 1.37]) prismX(pc, WAL, [[0.85, 1.86], [0.85, 1.62], [0.62, 1.86]], x - 0.0125, x + 0.0125);
  // the pegs: hickory dowels out of the board, 2 cm across, their front ends capped
  for (const x of SLOT_X) for (const d of [-PEG_HALF, PEG_HALF]) cyl(pc, HICKORY, HICKORY, 'y', [x + d, PEG_Z], 0.745, 0.85, PEG_R, 10, 'a');
  // the small-tool strip: a walnut rail with six square holes, on two end blocks
  const strip = [bx(wal('x'), [1.44, 0.765, 1.6], [1.86, 0.786, 1.625]), bx(wal('x'), [1.44, 0.804, 1.6], [1.86, 0.85, 1.625])];
  let sx = 1.44;
  for (let k = 0; k < 6; k++) { const h = 1.48 + 0.068 * k; strip.push(bx(wal('x'), [sx, 0.786, 1.6], [h - 0.009, 0.804, 1.625])); sx = h + 0.009; }
  strip.push(bx(wal('x'), [sx, 0.786, 1.6], [1.86, 0.804, 1.625]), bx(wal('y'), [1.44, 0.8, 1.55], [1.46, 0.85, 1.6]), bx(wal('y'), [1.84, 0.8, 1.55], [1.86, 0.85, 1.6]));
  solid(pc, strip);
  vise(pc);
  // things: a pine box and two tins on the top shelf, offcuts and a crate on the lower one
  solid(pc, [board(PINE, 'x', 3, [1.0, 0.7, 1.89], [1.35, 0.92, 1.97])]);
  cyl(pc, CHROME, CHROME, 'z', [0.42, 0.8], 1.89, 1.97, 0.04, 16, 'b');
  cyl(pc, CHROME, CHROME, 'z', [0.52, 0.79], 1.89, 1.95, 0.035, 16, 'b');
  for (let k = 0; k < 3; k++) solid(pc, [board(PINE, 'x', 4 + k, [0.25 + 0.05 * k, 0.25 + 0.01 * k, 0.222 + 0.023 * k], [1.2 - 0.15 * k, 0.4 - 0.01 * k, 0.245 + 0.023 * k])]);
  solid(pc, [board(PINE, 'x', 10, [1.35, 0.3, 0.222], [1.75, 0.315, 0.42]), board(PINE, 'x', 11, [1.35, 0.685, 0.222], [1.75, 0.7, 0.42]),
    board(PINE, 'y', 12, [1.35, 0.315, 0.222], [1.365, 0.685, 0.42]), board(PINE, 'y', 13, [1.735, 0.315, 0.222], [1.75, 0.685, 0.42]),
    board(PINE, 'x', 14, [1.365, 0.315, 0.222], [1.735, 0.685, 0.235])]);
  return pc;
}

/** The front vise under the left of the top: maple jaws, the cast iron plate and body, rods, screw, hub and T-bar. */
function vise(pc) {
  solid(pc, [board(MAPLE, 'x', 7, [0.2, -0.015, 0.66], [0.6, 0.015, TOP_Z])]);   // the front jaw, flush with the top
  solid(pc, [board(MAPLE, 'x', 8, [0.2, 0.04, 0.7], [0.6, 0.07, 0.83])]);         // the rear jaw under the top's edge
  solid(pc, [bx(CAST, [0.25, 0.015, 0.68], [0.55, 0.035, 0.82])]);
  solid(pc, [bx(CAST, [0.25, 0.07, 0.74], [0.55, 0.32, 0.83])]);
  for (const x of [0.29, 0.51]) cyl(pc, CHROME, CHROME, 'y', [x, 0.76], 0.015, 0.3, 0.009, 10);
  cyl(pc, CHROME, CHROME, 'y', [0.4, 0.76], -0.05, 0.3, 0.012, 12);
  cyl(pc, CAST, CAST, 'y', [0.4, 0.76], -0.055, -0.015, 0.026, 16, 'a');
  cyl(pc, CHROME, CHROME, 'x', [-0.035, 0.76], 0.235, 0.565, 0.008, 10);
  cyl(pc, HICKORY, HICKORY, 'x', [-0.035, 0.76], 0.2, 0.235, 0.015, 12, 'a');
  cyl(pc, HICKORY, HICKORY, 'x', [-0.035, 0.76], 0.565, 0.6, 0.015, 12, 'b');
}

/** A drawer, closed: an overlay front, a box of 12 mm sides, back and floor behind it, an iron bar pull. */
function drawer(name) {
  const {x0, x1, f0, f1} = DRAWERS[name], pc = new Piece('workbench_' + name), a = x0 + 0.004, b = x1 - 0.004, cx = (f0 + f1) / 2;
  solid(pc, [
    bx(wal('x'), [f0, 0.06, 0.575], [f1, 0.085, 0.82]),
    bx(wal('y', {w: INSIDE}), [a, 0.085, 0.57], [a + 0.012, 0.8, 0.79]),
    bx(wal('y', {e: INSIDE}), [b - 0.012, 0.085, 0.57], [b, 0.8, 0.79]),
    bx(wal('x', {n: INSIDE}), [a + 0.012, 0.788, 0.57], [b - 0.012, 0.8, 0.79]),
    bx({all: WAL, u: INSIDE}, [a + 0.012, 0.085, 0.57], [b - 0.012, 0.788, 0.58]),
  ]);
  cyl(pc, IRON, IRON, 'x', [0.037, 0.7], cx - 0.055, cx + 0.055, 0.006, 8, 'ab');
  for (const d of [-0.045, 0.045]) cyl(pc, IRON, IRON, 'y', [cx + d, 0.7], 0.034, 0.06, 0.005, 8);
  return pc;
}

/** The animated mesh: the body, the two drawers on their own channels (8 ticks, eased; 40 cm out). */
function mesh() {
  const drawerBone = name => { const {f0, f1} = DRAWERS[name]; return {name, pivot: N([(f0 + f1) / 2, 0.07, 0.7]), pieces: [drawer(name)]}; };
  const pull = name => ({duration_ticks: 8, easing: 'ease_in_out', transforms: {[name]: {translation: [0, 0, -DRAWER_OUT]}}});
  return furnitureMesh(ROOT, {id: BLOCK.id, dir: 'furniture', texPrefix: TEX, particle: TEX + WAL,
    bones: [{name: 'body', pivot: [0.5, 0, 0.5], pieces: [body()]}, drawerBone('drawer_left'), drawerBone('drawer_right')],
    animations: {drawer_left: pull('drawer_left'), drawer_right: pull('drawer_right')}});
}

// ================================================================================================ shapes
// The Mesh Shape profile (cabin/Workbench, docs/rendering/mesh_shape_runtime_v1.md): two cells high; each half's states in
// its own frame (the right half one block over), physical and selection boxes cut to the half, the regions over the whole
// bench. The left (master) half's state names which drawers are open (left_<drawer_left><drawer_right>); the right half
// never changes. Regions: drawer_left / drawer_right (open or close), interior_left / interior_right while open (the
// drawer's screen), peg0..peg4 (hang or take a tool). Regions are grown 1 cm so their edges are easy to hit.
const TABLE = nbox([[0.04, 0.04, 0], [1.96, 0.96, TOP_Z]]), VISE = nbox([[0.2, -0.075, 0.66], [0.6, 0.04, TOP_Z]]);
const BOARD = nbox([[0.04, 0.82, TOP_Z], [1.96, 0.96, 1.86]]), SHELF = nbox([[0.04, 0.6, 1.86], [1.96, 0.96, 1.97]]);
const HANG = nbox([[0.07, 0.7, 0.92], [1.93, 0.86, 1.8]]);
const drawerOut = d => nbox([[d.f0, 0.031 - DRAWER_OUT, 0.565], [d.f1, 0.04, 0.82]]);
const drawerFront = (d, out) => nbox([[d.f0 - 0.01, 0.025 - out, 0.565], [d.f1 + 0.01, 0.095 - out, 0.83]]);
const drawerInterior = d => nbox([[d.x0 + 0.016, 0.085 - DRAWER_OUT, 0.58], [d.x1 - 0.016, 0.04, 0.825]]);
/** Where the prompts sit: a drawer's pull, the middle of its open box, a peg pair's middle. */
export const pullPoint = (d, out) => N([(d.f0 + d.f1) / 2, 0.034 - out, 0.7]);
const interiorPoint = d => N([(d.x0 + d.x1) / 2, (0.085 - DRAWER_OUT + 0.04) / 2, 0.79]);
export const pegPoint = i => N([SLOT_X[i], TOOL_Y, PEG_Z]);
const pegRegion = i => nbox([[SLOT_X[i] - 0.13, 0.7, 1.0], [SLOT_X[i] + 0.13, 0.86, 1.72]]);

function shapes() {
  const cut = ([a, b], c0) => [[Math.max(a[0], c0), a[1], a[2]], [Math.min(b[0], c0 + 1), b[1], b[2]]];
  const states = {};
  const make = (c0, dx, open) => {
    const drawers = Object.entries(DRAWERS), outs = drawers.filter(([n]) => open[n]).map(([, d]) => drawerOut(d));
    const physical = [TABLE, BOARD, SHELF, ...outs].map(b => cut(b, c0)).filter(([a, b]) => b[0] - a[0] > 1e-6);
    const selection = [...physical, ...[VISE, HANG].map(b => cut(b, c0)).filter(([a, b]) => b[0] - a[0] > 1e-6)];
    const regions = {};
    for (const [name, d] of drawers) {
      const out = open[name] ? DRAWER_OUT : 0;
      regions[name] = [[drawerFront(d, out)], pullPoint(d, out)];
      if (open[name]) regions[name.replace('drawer', 'interior')] = [[drawerInterior(d)], interiorPoint(d)];
    }
    SLOT_X.forEach((_, i) => { regions['peg' + i] = [[pegRegion(i)], pegPoint(i)]; });
    return state({physical, selection, regions}, dx);
  };
  for (const l of [0, 1]) for (const r of [0, 1]) states[`left_${l}${r}`] = make(0, 0, {drawer_left: l, drawer_right: r});
  states.right = make(1, -1, {});
  return profile(states, 2);
}

// ================================================================================================ outputs
/** Loot: the workbench from its master cell (the lower left: part left, half lower). */
const loot = () => json({type: 'minecraft:block', pools: [{rolls: 1, bonus_rolls: 0, entries: [{type: 'minecraft:item', name: NS + BLOCK.id}],
  conditions: [{condition: 'minecraft:survives_explosion'},
    {condition: 'minecraft:block_state_property', block: NS + BLOCK.id, properties: {part: 'left', half: 'lower'}}]}], random_sequence: `${NS}blocks/${BLOCK.id}`});

export function outputs() {
  const m = mesh();
  return {stats: m.stats, files: [...m.outputs,
    [path.join(DATA, 'apocalypse_firstlight/mesh_shapes', BLOCK.id + '.json'), shapes()],
    [path.join(ASSETS, 'blockstates', BLOCK.id + '.json'), json({variants: {'': {model: `${NS}block/furniture/${BLOCK.id}`}}})],
    [path.join(DATA, 'apocalypse_firstlight/loot_tables/blocks', BLOCK.id + '.json'), loot()]]};
}

// ================================================================================================ audit
const readJson = p => JSON.parse(fs.readFileSync(path.join(ROOT, p), 'utf8'));
function audit() {
  const errs = [], tags = 'src/main/resources/data/minecraft/tags/blocks/', {id} = BLOCK;
  const tagHas = (file, v) => { try { return readJson(file).values.some(e => (typeof e === 'string' ? e : e.id) === NS + v); } catch { return false; } };
  if (!tagHas(tags + 'mineable/axe.json', id)) errs.push('minecraft:mineable/axe missing');
  for (const t of ['mineable/pickaxe', 'needs_stone_tool', 'needs_iron_tool', 'needs_diamond_tool']) if (tagHas(tags + t + '.json', id)) errs.push(`minecraft:${t} unexpected`);
  if (!tagHas('src/main/resources/data/apocalypse_firstlight/tags/blocks/noise_wood_blocks.json', id)) errs.push('missing from noise_wood_blocks');
  const mass = readJson('src/main/resources/data/apocalypse_firstlight/item_mass/afl_content_v1.json').items ?? {};
  if (mass[NS + id]?.unit_mass_kg !== BLOCK.mass) errs.push(`item mass ${mass[NS + id]?.unit_mass_kg} (want ${BLOCK.mass})`);
  for (const c of ['bulky', 'oversized']) if (tagHas(`src/main/resources/data/apocalypse_firstlight/tags/items/carry/${c}.json`, id) !== (BLOCK.carry === c)) errs.push(`carry/${c} wrong`);
  for (const lang of ['en_us', 'zh_cn']) {
    const v = readJson(`src/main/resources/assets/apocalypse_firstlight/lang/${lang}.json`)['block.apocalypse_firstlight.' + id];
    if (v !== (lang === 'en_us' ? BLOCK.en : BLOCK.zh)) errs.push(`lang ${lang} = ${v}`);
  }
  return errs;
}

const isMain = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  const {files, stats} = outputs();
  if (process.argv.includes('--check')) {
    const stale = files.filter(([file, data]) => { const cur = fs.existsSync(file) ? fs.readFileSync(file) : null; return !cur || !cur.equals(Buffer.isBuffer(data) ? data : Buffer.from(data)); });
    for (const [file] of stale) console.log('stale ' + path.relative(ROOT, file));
    const errs = audit();
    for (const e of errs) console.log('audit: ' + e);
    if (stale.length || errs.length) process.exit(1);
    console.log(`CHECK OK (${files.length} outputs)`);
  } else {
    for (const [file, data] of files) { fs.mkdirSync(path.dirname(file), {recursive: true}); fs.writeFileSync(file, data); }
    console.log(`wrote ${files.length} files`, JSON.stringify(stats));
    // the numbers cabin/Workbench mirrors (furniture frame)
    console.log('slots x', SLOT_X.map(x => +(2 - x).toFixed(4)), 'peg y', PEG_Z, 'tool z', TOOL_Y, 'peg top', PEG_Z + PEG_R,
      'pull points', Object.values(DRAWERS).map(d => pullPoint(d, 0).map(v => +v.toFixed(4))));
    const errs = audit();
    for (const e of errs) console.log('audit: ' + e);
  }
}
