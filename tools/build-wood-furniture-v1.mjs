// Wood Furniture V1 (docs/models/wood_furniture_v1.md, 2026-10-13): the generic wood furniture, first used in the forest
// start home B ("溪谷老宅", docs/worldgen/forest_start_home_b_v1_plan.md, M2.1). User 2026-10-13: wood furniture and a gun
// rack first; the rack a wall rack of two brackets with three notches (any long gun) and a small shelf (pistols, ammo,
// small items), items shown on it; the chairs can be sat on; the sink a cabinet with an enamel sink and a cast-iron
// pitcher pump (looks only in V1). Furniture is one set for every wood (any planks make it, recipes later) in a neutral
// stained finish (furniture/wood-furniture-art.mjs); the building materials stay per species.
//   wooden_table           76 cm, a glued-up top, aprons and legs; joins the tables beside it into one top (legs only at
//                          the outer corners, the boards along the longer run)
//   wooden_chair           a ladder-back chair, seat 46 cm; facing = where the sitter looks
//   wooden_kitchen_counter base cabinet 90 cm high, 60 cm deep, a 3.8 cm butcher-block worktop, a drawer, two
//                          frame-and-panel doors (open: swung out, the carcass, shelf and floor inside); 18 slots
//   wooden_sink_counter    the same cabinet with a drop-in enamel sink, a cast-iron pitcher pump beside it and, under the
//                          sink, the drain trap and the pump's riser; 9 slots
//   wooden_gun_rack        on a wall: two brackets with three notches each, a shelf under them; facing = out of the wall
// Every piece is drawn facing north (front at z 0, the back / wall at z 1) and is one closed solid per part (solid()).
// Writes textures (block/furniture/*.png + _s / _n), mesh pieces (cabin_mesh/furniture/, the cabin mesh loader), block /
// item models, blockstates, loot tables. Tags, item mass and lang are edited by hand; --check audits them.
//   node tools/build-wood-furniture-v1.mjs            -> writes every output
//   node tools/build-wood-furniture-v1.mjs --check    -> verifies every output is up to date and the hand-edited data
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {renderX} from './cabin/cabin-art.mjs';
import {FURNITURE_TEXTURES} from './furniture/wood-furniture-art.mjs';
import {bounds, state, profile} from './furniture/furniture-shapes.mjs';
import {Piece, box, solid, cylinder, mirroredX, serialise, newell, dot, sub, cross, norm, add, scl} from './cabin/cabin-geo.mjs';
import {furnitureMesh} from './furniture/furniture-mesh.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const ASSETS = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight');
const DATA = path.join(ROOT, 'src/main/resources/data');
const NS = 'apocalypse_firstlight:', TEX = NS + 'block/furniture/';
const json = v => JSON.stringify(v, null, 2) + '\n';

/** The furniture blocks: id, item mass (kg, furniture carry scale), carry tag (like the office desk / chair), names. All on the axe without a tier (vanilla wood). */
export const BLOCKS = [
  {id: 'wooden_table', mass: 12, carry: 'oversized', en: 'Wooden Table', zh: '木桌'},
  {id: 'wooden_chair', mass: 5, carry: 'bulky', en: 'Wooden Chair', zh: '木椅'},
  {id: 'wooden_kitchen_counter', mass: 30, carry: 'oversized', en: 'Wooden Kitchen Counter', zh: '木质橱柜'},
  {id: 'wooden_sink_counter', mass: 38, carry: 'oversized', en: 'Wooden Sink Counter', zh: '木质水槽柜'},
  {id: 'wooden_gun_rack', mass: 4, carry: 'bulky', en: 'Wooden Gun Rack', zh: '木质枪架'},
];

const TRIM = 'wood', TRIM_V = 'wood_v', TOP = 'wood_top', INSIDE = 'wood_inside', PANEL = 'wood_panel';
const IRON = 'iron', CAST = 'cast_iron', ENAMEL = 'enamel';

/** A polygon whose corners are reordered so its Newell normal points along `out`. */
function polyOut(pc, t, pts, out, opts = {}) {
  if (dot(newell(pts), out) < 0) pts = [...pts].reverse();
  return pc.poly(t, pts, opts);
}
/** A closed bar from a to b, w wide (across, perpendicular to `up`) and h high (along up's part across the bar). */
function bar(pc, t, a, b, up, w, h) {
  const d = norm(sub(b, a)), s = norm(cross(d, up)), u = cross(s, d);
  const C = (p, i, j) => add(add(p, scl(s, i * w / 2)), scl(u, j * h / 2));
  const A = [C(a, -1, -1), C(a, 1, -1), C(a, 1, 1), C(a, -1, 1)], B = [C(b, -1, -1), C(b, 1, -1), C(b, 1, 1), C(b, -1, 1)];
  polyOut(pc, t, A, scl(d, -1)); polyOut(pc, t, B, d);
  for (let i = 0; i < 4; i++) { const j = (i + 1) % 4, mid = sub(scl(add(A[i], A[j]), 0.5), a); polyOut(pc, t, [A[i], A[j], B[j], B[i]], mid); }
  return pc;
}

// =============================================================================================== table
// Top 0.725..0.76 (3.5 cm), 2 cm in from an open side, out to the block edge on a joined one; 6 cm legs 4 cm in from the
// outer corners; 10 cm aprons (2.2 cm) 5 cm in from an open side, between the legs or on to a joined side's edge.
// mask: the joined sides (N 1, E 2, S 4, W 8); axis x: boards along x (z: along z, a run along z).
export const TABLE_Y = 0.76;
const TT = 0.035, APRON = 0.1, LEG = 0.06, LEG_IN = 0.04, AP_IN = 0.05, AP_T = 0.022;
function table(mask, axis) {
  const j = b => (mask & b) !== 0, [N, E, S, W] = [j(1), j(2), j(4), j(8)], y0 = TABLE_Y - TT, ya = y0 - APRON, sw = {swap: axis === 'z'};
  const B = [[{u: TOP, d: TOP, all: TRIM}, [W ? 0 : 0.02, y0, N ? 0 : 0.02], [E ? 1 : 0.98, TABLE_Y, S ? 1 : 0.98], {u: sw, d: sw}]];
  const L0 = LEG_IN, L1 = LEG_IN + LEG, H0 = 1 - L1, H1 = 1 - L0;
  for (const [open, x0, x1, z0, z1] of [[!N && !W, L0, L1, L0, L1], [!N && !E, H0, H1, L0, L1], [!S && !E, H0, H1, H0, H1], [!S && !W, L0, L1, H0, H1]])
    if (open) B.push([TRIM_V, [x0, 0, z0], [x1, y0, z1]]);
  const a0 = AP_IN, a1 = AP_IN + AP_T;
  if (!N) B.push([TRIM, [W ? 0 : L1, ya, a0], [E ? 1 : H0, y0, a1]]);
  if (!S) B.push([TRIM, [W ? 0 : L1, ya, 1 - a1], [E ? 1 : H0, y0, 1 - a0]]);
  if (!W) B.push([TRIM, [a0, ya, N ? 0 : L1], [a1, y0, S ? 1 : H0]]);
  if (!E) B.push([TRIM, [1 - a1, ya, N ? 0 : L1], [1 - a0, y0, S ? 1 : H0]]);
  return solid(new Piece(`furniture/table_${axis}_${mask}`), B);
}

// =============================================================================================== chair
// The sitter looks north (-z); the back posts at the south. Seat 0.43..0.465, 42 x 42 cm; 4 cm legs, the back legs on up
// as posts to 0.98, two ladder slats and a top rail; stretchers low between the legs.
export const SEAT_Y = 0.465;
function chair() {
  const fx = [0.31, 0.65], lz = [[0.32, 0.36], [0.66, 0.70]], B = [];
  B.push([{u: TRIM, d: TRIM, all: TRIM}, [0.29, 0.43, 0.30], [0.71, SEAT_Y, 0.72]]);
  for (const x of fx) {
    B.push([TRIM_V, [x, 0, lz[0][0]], [x + 0.04, 0.43, lz[0][1]]]);   // front legs
    B.push([TRIM_V, [x, 0, lz[1][0]], [x + 0.04, 0.98, lz[1][1]]]);   // back legs and posts
    B.push([TRIM, [x + 0.01, 0.14, lz[0][1]], [x + 0.03, 0.16, lz[1][0]]]);   // side stretchers
  }
  B.push([TRIM, [0.35, 0.2, 0.33], [0.65, 0.22, 0.35]], [TRIM, [0.35, 0.2, 0.67], [0.65, 0.22, 0.69]]);
  for (const [y0, y1, t] of [[0.6, 0.67, 0.02], [0.74, 0.81, 0.02], [0.88, 0.96, 0.028]]) B.push([TRIM, [0.35, y0, 0.682 - t / 2], [0.65, y1, 0.682 + t / 2]]);
  return solid(new Piece('furniture/chair'), B);
}

// =============================================================================================== counters
// Front at the north: the carcass from z 0.42 to the wall, 1.8 cm sides, a 10 cm toe kick set 7 cm back, the cabinet
// floor 0.10..0.118, a shelf at 0.40, a drawer (0.69..0.855 front) over two overlay doors (0.118..0.684, frame and a
// recessed V-groove panel, iron knobs), the worktop 0.862..0.90 out to z 0.37. Doors open 90 degrees about their outer
// front edges (out into the room).
export const WORKTOP_Y = 0.9;
const CZ = 0.42, DZ = 0.4, TZ = 0.37, WT0 = 0.862, KICK = 0.1, KZ = 0.49, SIDE = 0.018, FLOOR = 0.118;
const DOOR = [0.004, 0.118, 0.497, 0.684];   // the left door: x0, y0, x1, y1
function carcass(sink) {
  const B = [
    [{all: TRIM_V, e: INSIDE}, [0, KICK, CZ], [SIDE, WT0, 1]], [{all: TRIM_V, e: INSIDE}, [0, 0, KZ], [SIDE, KICK, 1]],
    [{all: TRIM_V, w: INSIDE}, [1 - SIDE, KICK, CZ], [1, WT0, 1]], [{all: TRIM_V, w: INSIDE}, [1 - SIDE, 0, KZ], [1, KICK, 1]],
    [INSIDE, [SIDE, KICK, 1 - SIDE], [1 - SIDE, WT0, 1]],
    [{all: INSIDE, n: TRIM}, [SIDE, KICK, CZ], [1 - SIDE, FLOOR, 1 - SIDE]],
    [TRIM, [SIDE, 0, KZ], [1 - SIDE, KICK, 1 - SIDE]],
    [INSIDE, [SIDE, 0.4, 0.46], [1 - SIDE, 0.418, 1 - SIDE]],
    [TRIM, [0.003, 0.69, DZ], [0.997, 0.855, CZ]],   // the drawer front (the sink's: a false front)
  ];
  if (!sink) B.push([{all: INSIDE, n: TRIM}, [SIDE, 0.682, CZ], [1 - SIDE, 0.69, 1 - SIDE]], [INSIDE, [SIDE, 0.69, CZ], [1 - SIDE, WT0, 1 - SIDE]]);
  return B;
}
function counter() {
  const pc = new Piece('furniture/counter_body');
  solid(pc, [...carcass(false), [{u: TOP, all: TRIM}, [0, WT0, TZ], [1, WORKTOP_Y, 1]]]);
  // the drawer pull: an iron bar on two posts
  return solid(pc, [[IRON, [0.45, 0.762, 0.372], [0.55, 0.778, 0.384]], [IRON, [0.455, 0.762, 0.384], [0.467, 0.778, DZ]], [IRON, [0.533, 0.762, 0.384], [0.545, 0.778, DZ]]]);
}
/** The left door, closed: stiles, rails and a recessed V-groove panel, a knob near its free edge. */
function door() {
  const [x0, y0, x1, y1] = DOOR, f = 0.06, pc = new Piece('d');
  solid(pc, [[TRIM_V, [x0, y0, DZ], [x0 + f, y1, CZ]], [TRIM_V, [x1 - f, y0, DZ], [x1, y1, CZ]], [TRIM, [x0 + f, y0, DZ], [x1 - f, y0 + f, CZ]],
    [TRIM, [x0 + f, y1 - f, DZ], [x1 - f, y1, CZ]], [{n: PANEL, all: TRIM}, [x0 + f, y0 + f, DZ + 0.007], [x1 - f, y1 - f, CZ]]]);
  return solid(pc, [[IRON, [x1 - 0.045, 0.6, DZ - 0.022], [x1 - 0.027, 0.618, DZ]]]);
}
/** A piece swung a quarter about the vertical line (hx, hz): (dx, dz) -> (dz, -dx), out toward -z. */
function swungOut(pc, hx, hz, name) {
  const out = new Piece(name);
  return out.merge(pc, ([x, y, z]) => [hx + (z - hz), y, hz - (x - hx)], ([nx, ny, nz]) => [nz, ny, -nx]);
}
/**
 * The counters' Mesh Shape profile (wooden_counter, both counters; cabin/FurnitureOpening): closed, the doors (open them);
 * open, the two swung doors (close them) and the opening between them (search / view). The worktop is no region.
 */
function counterShapes() {
  const d = door(), sw = swungOut(d, DOOR[0], DZ, 'x');
  const body = [[0, 0, TZ], [1, WORKTOP_Y, 1]], [lo, hi] = bounds(d), left = bounds(sw), right = bounds(mirroredX(sw));
  const g = ([a, b], e) => [a.map(v => v - e), b.map(v => v + e)];
  const knob = [DOOR[2] - 0.036, 0.609, DZ - 0.011], swung = ([x, y, z]) => [DOOR[0] + (z - DZ), y, DZ - (x - DOOR[0])];
  return profile({
    closed: state({physical: [body], regions: {door: [[g([[lo[0], lo[1], lo[2]], [1 - lo[0], hi[1], hi[2]]], 0.01)], [0.5, knob[1], knob[2]]]}}),
    open: state({physical: [body], selection: [body, left, right], regions: {
      door: [[g(left, 0.01), g(right, 0.01)], swung(knob)],
      interior: [[[[left[1][0], lo[1], TZ - 0.01], [right[0][0], hi[1], CZ + 0.03]]], [0.5, 0.42, DZ]]}}),
  });
}
/**
 * The counters as AFL Animated Block Meshes (tools/furniture/furniture-mesh.mjs, 2026-10-15; the cabin pieces swapped the
 * doors closed / open in one frame): the body, and each door its own bone hinged on its outer stile at the front (x
 * DOOR[0] and mirrored, z DZ); 'open' swings both a quarter out, 8 ticks.
 */
function counterMesh(id, body) {
  const d = door();
  return {id, bones: [{name: 'body', pivot: [0.5, 0, 0.5], pieces: [body]},
    {name: 'door_left', pivot: [DOOR[0], 0, DZ], pieces: [d]}, {name: 'door_right', pivot: [1 - DOOR[0], 0, DZ], pieces: [mirroredX(d)]}],
    open: {ticks: 8, transforms: {door_left: {rotation: [0, 90, 0]}, door_right: {rotation: [0, -90, 0]}}}};
}
// the sink: a drop-in enamel basin (outer 64 x 42 cm, 19 cm deep under its rim, 2 cm walls, a 1.2 cm rolled rim 2 cm
// over the worktop) left of the middle, the worktop round it; on the strip to its right a cast-iron pitcher pump, the spout
// over the basin, the handle raised toward +x (a pump's handle is opposite its spout; at the back it would hit the wall's
// logs, which bulge 7 cm into the cell); under the sink the drain (a tail piece and a trap to the wall) and the pump's riser
const BX0 = 0.1, BX1 = 0.74, BZ0 = 0.42, BZ1 = 0.84, BY0 = 0.72, RIM = 0.912, BW = 0.02, PUMP_X = 0.875, PUMP_Z = 0.63;
function sink() {
  const pc = new Piece('furniture/sink_body');
  solid(pc, [...carcass(true),
    [{u: TOP, all: TRIM}, [0, WT0, TZ], [1, WORKTOP_Y, BZ0]], [{u: TOP, all: TRIM}, [0, WT0, BZ1], [1, WORKTOP_Y, 1]],
    [{u: TOP, all: TRIM}, [0, WT0, BZ0], [BX0, WORKTOP_Y, BZ1]], [{u: TOP, all: TRIM}, [BX1, WT0, BZ0], [1, WORKTOP_Y, BZ1]],
    [ENAMEL, [BX0, BY0, BZ0], [BX1, BY0 + BW, BZ1]],
    [ENAMEL, [BX0, BY0 + BW, BZ0], [BX1, RIM, BZ0 + BW]], [ENAMEL, [BX0, BY0 + BW, BZ1 - BW], [BX1, RIM, BZ1]],
    [ENAMEL, [BX0, BY0 + BW, BZ0 + BW], [BX0 + BW, RIM, BZ1 - BW]], [ENAMEL, [BX1 - BW, BY0 + BW, BZ0 + BW], [BX1, RIM, BZ1 - BW]],
    [ENAMEL, [BX0 - 0.02, WORKTOP_Y, BZ0 - 0.02], [BX1 + 0.02, RIM, BZ0]], [ENAMEL, [BX0 - 0.02, WORKTOP_Y, BZ1], [BX1 + 0.02, RIM, BZ1 + 0.02]],
    [ENAMEL, [BX0 - 0.02, WORKTOP_Y, BZ0], [BX0, RIM, BZ1]], [ENAMEL, [BX1, WORKTOP_Y, BZ0], [BX1 + 0.02, RIM, BZ1]],
  ]);
  const cx = (BX0 + BX1) / 2, cz = (BZ0 + BZ1) / 2, px = PUMP_X, pz = PUMP_Z;
  cylinder(pc, CAST, CAST, 'y', [cz, cx], BY0 + BW, BY0 + BW + 0.003, 0.03, 16, 'b');          // the drain's strainer
  cylinder(pc, CAST, CAST, 'y', [cz, cx], 0.508, BY0, 0.022, 12, 'a');                          // tail piece
  cylinder(pc, CAST, CAST, 'z', [cx, 0.53], cz, 1 - SIDE, 0.022, 12);                           // trap arm to the wall
  cylinder(pc, CAST, CAST, 'y', [pz, px], FLOOR, WT0, 0.02, 12);                                // the pump's riser
  // the pitcher pump: flange, barrel, pitcher with its lid, spout and nozzle, the fulcrum lug and the raised handle
  cylinder(pc, CAST, CAST, 'y', [pz, px], WORKTOP_Y, 0.915, 0.06, 16, 'b');
  cylinder(pc, CAST, CAST, 'y', [pz, px], 0.915, 1.12, 0.045, 16);
  cylinder(pc, CAST, CAST, 'y', [pz, px], 1.12, 1.24, 0.062, 16, 'ab');
  cylinder(pc, CAST, CAST, 'x', [1.065, pz], BX1 - 0.045, px - 0.02, 0.022, 12);
  solid(pc, [[CAST, [BX1 - 0.06, 1.025, pz - 0.024], [BX1 - 0.033, 1.089, pz + 0.024]], [CAST, [px + 0.03, 1.24, pz - 0.016], [px + 0.058, 1.3, pz + 0.016]]]);
  const ang = 50 * Math.PI / 180, piv = [px + 0.044, 1.28, pz], tip = [piv[0] + 0.24 * Math.cos(ang), piv[1] + 0.24 * Math.sin(ang), pz];
  const grip = [tip[0] + 0.08 * Math.cos(ang), tip[1] + 0.08 * Math.sin(ang), pz];
  bar(pc, CAST, piv, tip, [0, 0, 1], 0.022, 0.026);
  bar(pc, CAST, tip, grip, [0, 0, 1], 0.03, 0.034);
  return pc;
}

// =============================================================================================== gun rack
// On a wall (the wall at z 1, the rack faces north). Two 3 cm brackets at x 0.10 and 0.90: a spine 0.86..1 from y 0.04 to
// 0.97, at each notch level an arm out to z 0.76 and a lip at its tip (the gun lies in the 7 cm gap z 0.79..0.86, its
// underside at the level); a foot under the shelf; a top cleat between the brackets on the wall; the shelf board 0.085..0.11
// from z 0.70 to the wall with a front lip.
export const NOTCH_Y = [0.42, 0.66, 0.9], GUN_Z = 0.825, SHELF_Y = 0.11, SHELF_Z = 0.86, SHELF_X = [0.3, 0.7];
function gunRack() {
  const B = [];
  for (const bx of [0.1, 0.9]) {
    const x0 = bx - 0.015, x1 = bx + 0.015;
    B.push([TRIM_V, [x0, 0.04, 0.86], [x1, 0.97, 1]], [TRIM_V, [x0, 0.04, 0.72], [x1, 0.085, 0.86]]);
    for (const y of NOTCH_Y) B.push([TRIM_V, [x0, y - 0.045, 0.76], [x1, y, 0.86]], [TRIM_V, [x0, y, 0.76], [x1, y + 0.035, 0.79]]);
  }
  B.push([TRIM, [0.115, 0.93, 0.975], [0.885, 0.97, 1]]);
  B.push([TRIM, [0.05, 0.085, 0.7], [0.95, SHELF_Y, 1]], [TRIM, [0.05, SHELF_Y, 0.7], [0.95, 0.128, 0.716]]);
  return solid(new Piece('furniture/gun_rack'), B);
}

export function pieces() {
  const P = [];
  for (const axis of ['x', 'z']) for (let m = 0; m < 16; m++) P.push(table(m, axis));
  P.push(chair(), gunRack());
  return P;
}
export const MESH_IDS = ['wooden_kitchen_counter', 'wooden_sink_counter'];
export function meshes() {
  return [counterMesh('wooden_kitchen_counter', counter()), counterMesh('wooden_sink_counter', sink())]
    .map(m => furnitureMesh(ROOT, {...m, dir: 'furniture', texPrefix: TEX, particle: TEX + PARTICLE[m.id]}));
}

// =============================================================================================== models, states, loot
const ITEMS = {
  wooden_table: [['furniture/table_x_0', 0]],
  wooden_chair: [['furniture/chair', 0]],
  wooden_gun_rack: [['furniture/gun_rack', 0]],
};
const PARTICLE = {wooden_table: TOP, wooden_chair: TRIM, wooden_kitchen_counter: TRIM_V, wooden_sink_counter: TRIM_V, wooden_gun_rack: TRIM_V};
const loot = id => json({type: 'minecraft:block', pools: [{rolls: 1, bonus_rolls: 0, entries: [{type: 'minecraft:item', name: NS + id}],
  conditions: [{condition: 'minecraft:survives_explosion'}]}], random_sequence: `${NS}blocks/${id}`});

export function outputs() {
  const out = [];
  const texOut = (name, maps) => maps.forEach((m, i) => out.push([path.join(ASSETS, 'textures/block/furniture', name + ['', '_s', '_n'][i] + '.png'), m]));
  for (const [name, [make, height]] of Object.entries(FURNITURE_TEXTURES)) texOut(name, renderX(make(), {height}));
  for (const pc of pieces()) out.push([path.join(ASSETS, 'cabin_mesh', pc.name + '.json'), JSON.stringify(serialise(pc, TEX)) + '\n']);
  out.push([path.join(DATA, 'apocalypse_firstlight/mesh_shapes/wooden_counter.json'), counterShapes()]);
  const bm = id => `${NS}block/furniture/${id}`;
  for (const {id} of BLOCKS) {
    if (!MESH_IDS.includes(id)) {
      out.push([path.join(ASSETS, 'models/block/furniture', id + '.json'), json({parent: 'minecraft:block/block', loader: NS + 'cabin', textures: {particle: TEX + PARTICLE[id]}, item: ITEMS[id]})]);
      out.push([path.join(ASSETS, 'models/item', id + '.json'), json({parent: bm(id)})]);
    }
    out.push([path.join(ASSETS, 'blockstates', id + '.json'), json({variants: {'': {model: bm(id)}}})]);
    out.push([path.join(DATA, 'apocalypse_firstlight/loot_tables/blocks', id + '.json'), loot(id)]);
  }
  for (const m of meshes()) out.push(...m.outputs);
  return out;
}

// =============================================================================================== audit
const readJson = p => JSON.parse(fs.readFileSync(path.join(ROOT, p), 'utf8'));
function audit() {
  const errs = [], tags = 'src/main/resources/data/minecraft/tags/blocks/';
  const tagHas = (file, id) => { try { return readJson(file).values.some(v => (typeof v === 'string' ? v : v.id) === NS + id); } catch { return false; } };
  for (const {id} of BLOCKS) {
    if (!tagHas(tags + 'mineable/axe.json', id)) errs.push(`${id}: missing from minecraft:mineable/axe`);
    for (const t of ['mineable/pickaxe', 'needs_stone_tool', 'needs_iron_tool', 'needs_diamond_tool']) if (tagHas(tags + t + '.json', id)) errs.push(`${id}: in minecraft:${t}`);
    if (!tagHas('src/main/resources/data/apocalypse_firstlight/tags/blocks/noise_wood_blocks.json', id)) errs.push(`${id}: missing from noise_wood_blocks`);
  }
  const mass = readJson('src/main/resources/data/apocalypse_firstlight/item_mass/afl_content_v1.json').items ?? {};
  for (const b of BLOCKS) if (mass[NS + b.id]?.unit_mass_kg !== b.mass) errs.push(`${b.id}: item mass ${mass[NS + b.id]?.unit_mass_kg} (want ${b.mass})`);
  for (const b of BLOCKS) for (const c of ['bulky', 'oversized'])
    if (tagHas(`src/main/resources/data/apocalypse_firstlight/tags/items/carry/${c}.json`, b.id) !== (b.carry === c)) errs.push(`${b.id}: carry/${c} ${b.carry === c ? 'missing' : 'unexpected'}`);
  for (const lang of ['en_us', 'zh_cn']) {
    const l = readJson(`src/main/resources/assets/apocalypse_firstlight/lang/${lang}.json`);
    for (const b of BLOCKS) { const v = l['block.apocalypse_firstlight.' + b.id]; if (v !== (lang === 'en_us' ? b.en : b.zh)) errs.push(`${b.id}: lang ${lang} = ${v}`); }
  }
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
    for (const p of pcs.filter(p => !p.name.startsWith('furniture/table'))) console.log('  ' + p.name, serialise(p, TEX).quads.length);
  }
}
