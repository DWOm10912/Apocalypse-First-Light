// Forest start home B "溪谷老宅" — M1 exterior (2026-10-12; docs/worldgen/forest_home_b_m1_exterior.md) through the AFL
// authoring bridge: the M0 v2 house (docs/worldgen/forest_home_b_whitebox_m0.md) rebuilt with the Cabin Exterior V1 blocks
// (docs/models/cabin_exterior_v1.md): white oak log walls, Mesh roofs, doors, double-hung windows, porch posts / railings,
// board and batten, fieldstone, stove pipe, firewood; plus the user's extras (2026-10-12): a root cellar, an outhouse and a
// woven wire garden. No antenna, no power line. M2.1 (2026-10-13): the Wood Furniture V1 pieces (docs/models/wood_furniture_v1.md)
// replace the M0 stand-ins for the chairs, tables, kitchen counters, sink and gun rack; the other M0 stand-ins stay until
// their M2 pieces exist.
// Paper frame as M0: X east, Z south (the front faces +Z), k = layer above grade (k 0 = the first air layer: plinth, floors,
// porch deck; k -1 = the grade surface). Plot 37 x 13 x 26: x = X + 10 (X -10..26), y = k + 3 (k -3..9), z = Z + 10
// (Z -10..15), front SOUTH, no rotation. The site is graded: k -1 grass, k -3..-2 dirt, everything else in the plot is air
// or the recipe (pick a dry, fairly level spot; no water, no entities in the plot).
// Roof 1:2 in half-block steps (lower / upper / ridge cells): main ridge 8.75 over Z 5, walls to 6.0, 1 m overhang;
// addition shed roof 6.5 -> 3.5 falling east; porch roof 5.0 -> 3.5 falling south, header beam on the posts (Z 13);
// woodshed lean-to falling north; carport gable, ridge along Z over X 22; outhouse lean-to falling north.
//   node tools/afl_minecraft_mcp/forest_home_b_m1.mjs plan       -> counts + checks + cells JSON (no game)
//   node tools/afl_minecraft_mcp/forest_home_b_m1.mjs build      -> grades the active plot (id forest_home_b_m1) and writes the
//                                                                   recipe, reconciles fence / stairs shapes, audits support
//   node tools/afl_minecraft_mcp/forest_home_b_m1.mjs fixtures   -> genset, kit chest, barrels, counters, gun rack (place_fixture)
//   node tools/afl_minecraft_mcp/forest_home_b_m1.mjs slices 2 5 -> redo those Z slices (clear + their cells), e.g. after an animal stopped one
//   node tools/afl_minecraft_mcp/forest_home_b_m1.mjs region 3 15 -10 -2 -> redo one X / Z box only (M1.3: the root cellar)
//   node tools/afl_minecraft_mcp/forest_home_b_m1.mjs furniture  -> M2.1 in a built house: only the Wood Furniture cells and fixtures
import {BridgeClient} from './bridge_client.mjs';
import {mkdir, writeFile} from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

export const ID = 'forest_home_b_m1', SIZE = [37, 13, 26];
const X0 = -10, X1 = 26, K0 = -3, K1 = 9, Z0 = -10, Z1 = 15;
const A = id => 'apocalypse_firstlight:' + id, M = id => 'minecraft:' + id;

// ---- palette (Cabin Exterior V1 / Wood Furniture V1 where they have a block; the other M0 stand-ins stay until M2) ----
export const P = {
  LOG: axis => A(`white_oak_log_wall[axis=${axis}]`),
  BB_BROWN: A('brown_board_and_batten'), BB_GREY: A('grey_board_and_batten'),
  STONE: A('fieldstone'),
  FLOOR: A('white_oak_planks'), PARTITION: A('white_oak_planks'),
  ROOF: (mat, facing, shape, beam = false) => A(`${mat}[beam=${beam},facing=${facing},shape=${shape}]`),
  POST: A('white_oak_porch_post'), RAIL: axis => A(`white_oak_porch_railing[axis=${axis}]`),
  DECK: axis => A(`white_oak_porch_deck[axis=${axis}]`),
  PIPE: A('stove_pipe'), FIREWOOD: axis => A(`firewood_stack[axis=${axis}]`),
  WINDOW: facing => A(`white_oak_double_hung_window[facing=${facing}]`),
  DOOR: (type, facing, half, hinge) => A(`white_oak_${type}_door[facing=${facing},half=${half},hinge=${hinge},open=false,powered=false]`),
  STAIRS: facing => A(`white_oak_stairs[facing=${facing},half=bottom,shape=straight,waterlogged=false]`),
  FENCE: A('woven_wire_fence[east=false,north=false,post=true,south=false,waterlogged=false,west=false]'),
  GRASS: M('grass_block[snowy=false]'), DIRT: M('dirt'), BED_SOIL: M('coarse_dirt'),
  // Wood Furniture V1 (M2.1): chairs face where the sitter looks; a table lists the tables it joins
  CHAIR: facing => A(`wooden_chair[facing=${facing}]`),
  TABLE: (n = false, e = false, s = false, w = false) => A(`wooden_table[east=${e},north=${n},south=${s},west=${w}]`),
  // M0 interior stand-ins (later M2 pieces replace them): the cold box, the stove, the bed
  COLD_BOX: A('white_oak_planks'), STOVE: A('steel_block'),
  BED: M('smooth_quartz_slab[type=bottom,waterlogged=false]'),
};
const GREEN = 'green_standing_seam_roof', GALV = 'galvanized_corrugated_roof', RUST = 'rusty_corrugated_roof';
const vstairs = facing => M(`spruce_stairs[facing=${facing},half=bottom,shape=straight,waterlogged=false]`);
const vslab = t => M(`spruce_slab[type=${t},waterlogged=false]`);
const vfence = M('spruce_fence[east=false,north=false,south=false,waterlogged=false,west=false]');

const MX = 12, MZ = 10, RIDGE_Z = 5;

/**
 * A 1:2 slope of roof cells from its top: d = 1 is the cell next to the ridge (or the high edge), alternating lower / upper
 * going down. Returns {k, shape} for a cell d steps down from a ridge at layer kr (ridge cell d = 0: shape ridge).
 */
export function slopeCell(kr, d) {
  if (d === 0) return {k: kr, shape: 'ridge'};
  return {k: kr - Math.floor(d / 2), shape: d % 2 === 1 ? 'lower' : 'upper'};
}

export function recipe() {
  const cells = new Map(), fixtures = [], tags = new Map();
  const key = (X, k, Z) => X + ',' + k + ',' + Z;
  const put = (X, k, Z, s, tag) => {
    if (X < X0 || X > X1 || k < K0 || k > K1 || Z < Z0 || Z > Z1) throw Error('OUT ' + [X, k, Z]);
    cells.set(key(X, k, Z), s); if (tag) tags.set(key(X, k, Z), tag);
  };
  const del = (X, k, Z) => { cells.delete(key(X, k, Z)); tags.delete(key(X, k, Z)); };
  const get = (X, k, Z) => cells.get(key(X, k, Z));
  const fill = (xa, ka, za, xb, kb, zb, s, tag) => { for (let k = ka; k <= kb; k++) for (let Z = za; Z <= zb; Z++) for (let X = xa; X <= xb; X++) put(X, k, Z, s, tag); };
  const storage = (X, k, Z, id, facing) => fixtures.push({tool: 'place_fixture', block_id: id.includes(':') ? id : M(id), at: [X, k, Z], facing});
  const door = (type, X, Z, facing, hinge, k = 1) => { put(X, k, Z, P.DOOR(type, facing, 'lower', hinge), 'door'); put(X, k + 1, Z, P.DOOR(type, facing, 'upper', hinge), 'door'); };
  const win = (X, Z, facing, ka, kb) => { for (let k = ka; k <= kb; k++) put(X, k, Z, P.WINDOW(facing), 'window'); };
  const roof = (X, k, Z, mat, facing, shape, beam) => put(X, k, Z, P.ROOF(mat, facing, shape, beam), 'roof');

  // ---------------- site: graded pad ----------------
  fill(X0, -3, Z0, X1, -2, Z1, P.DIRT, 'site');
  fill(X0, -1, Z0, X1, -1, Z1, P.GRASS, 'site');

  // ---------------- main house: fieldstone plinth k 0 (footing k -1), floor, log walls k 1..5 ----------------
  for (let X = 0; X <= MX; X++) for (let Z = 0; Z <= MZ; Z++) {
    const edge = X === 0 || X === MX || Z === 0 || Z === MZ;
    put(X, 0, Z, edge ? P.STONE : P.FLOOR);
    if (edge) put(X, -1, Z, P.STONE);
    if (edge) for (let k = 1; k <= 5; k++) put(X, k, Z, P.LOG(Z === 0 || Z === MZ ? 'x' : 'z'));
  }
  // addition X 13..17 x Z 0..7: plinth, floor, brown board and batten k 1..3 (X 12 is the main house's east gable wall)
  for (let X = 13; X <= 17; X++) for (let Z = 0; Z <= 7; Z++) {
    const edge = X === 17 || Z === 0 || Z === 7;
    put(X, 0, Z, edge ? P.STONE : P.FLOOR);
    if (edge) { put(X, -1, Z, P.STONE); for (let k = 1; k <= 3; k++) put(X, k, Z, P.BB_BROWN); }
  }
  // partitions k 1..3: X 5 (bedroom + storeroom | living), Z 7 (bedroom | storeroom); attic floor k 4, ladder hole (6, 3)
  fill(5, 1, 1, 5, 3, 9, P.PARTITION); fill(1, 1, 7, 4, 3, 7, P.PARTITION);
  fill(1, 4, 1, MX - 1, 4, 4, P.FLOOR); fill(1, 4, 5, 5, 4, 9, P.FLOOR);
  // porch deck k 0 (X 0..12, Z 11..13; M1.1: thin boards along X on joists, rim boards and supports on the open sides,
  // M1.2: oak posts on squared stone blocks; the grade under it), the steps (8, 14)
  fill(0, 0, 11, MX, 0, 13, P.DECK('x'));
  put(8, 0, 14, P.STAIRS('north'));

  // ---------------- doors and windows ----------------
  door('cabin', 8, MZ, 'north', 'left');       // front door into the living room
  door('board', 5, 5, 'east', 'left');         // bedroom
  door('board', 5, 8, 'east', 'right');        // storeroom
  door('board', 12, 2, 'east', 'right');       // living -> kitchen (in the east gable wall)
  door('board', 16, 0, 'south', 'left');       // back door (woodshed, genset, cellar)
  door('cabin', 15, 7, 'north', 'right');      // mudroom door (yard, carport)
  for (const X of [6, 10]) win(X, MZ, 'south', 2, 3);   // living, front, 1 x 2
  win(2, MZ, 'south', 2, 2);                             // storeroom, front
  win(3, 0, 'north', 2, 3);                              // bedroom, back
  win(0, 3, 'west', 2, 3);                               // bedroom, west
  win(14, 0, 'north', 2, 2);                             // over the sink
  win(13, 7, 'south', 2, 2);                             // addition front
  win(17, 3, 'east', 2, 3);                              // addition east

  // ---------------- main roof: ridge cell Z 5 at k 8 (top 8.75), lower over the walls at k 6 (6.0), eave Z -1 / 11 ----------------
  const mainCell = Z => slopeCell(8, Math.abs(Z - RIDGE_Z));
  for (let X = -1; X <= MX + 1; X++) for (let Z = -1; Z <= MZ + 1; Z++) {
    if (X === MX + 1 && Z <= 8) continue;   // the addition roof takes the east overhang over Z -1..8
    const {k, shape} = mainCell(Z), facing = Z < RIDGE_Z ? 'north' : Z > RIDGE_Z ? 'south' : 'north';
    roof(X, k, Z, GREEN, facing, shape);
    // gable walls X 0 / X 12 carry the logs up to the roof cell (it fills down to them)
    if ((X === 0 || X === MX) && Z >= 0 && Z <= MZ) for (let kk = 6; kk < k; kk++) put(X, kk, Z, P.LOG('z'), 'gable');
  }
  // attic gable windows: west 1 x 2 at eye level, east 1 x 1 above the addition roof
  win(0, RIDGE_Z, 'west', 5, 6); win(MX, RIDGE_Z, 'east', 7, 7);

  // ---------------- addition shed roof X 13..18 (6.5 -> 3.5 falling east), rakes Z -1 / 8 ----------------
  // X 13 lower k 6 (6.0..6.5, against the gable), 14 upper k 5, 15 lower k 5, 16 upper k 4, 17 lower k 4 (over the wall), 18 upper k 3
  const addCell = X => slopeCell(6, X - 12);   // d 1 at X 13: lower k 6
  for (let X = 13; X <= 18; X++) for (let Z = -1; Z <= 8; Z++) {
    const {k, shape} = addCell(X);
    roof(X, k, Z, GREEN, 'east', shape);
    // the side walls Z 0 / Z 7 carry their boards up under the slope
    if ((Z === 0 || Z === 7) && X <= 17) for (let kk = 4; kk < k; kk++) put(X, kk, Z, P.BB_BROWN, 'gable');
  }

  // ---------------- porch: posts, railing, low roof 5.0 -> 3.5 with the header beam at Z 13 ----------------
  for (const X of [0, 6, MX]) fill(X, 1, 13, X, 2, 13, P.POST);
  for (let X = -1; X <= MX + 1; X++) {
    roof(X, 4, 11, GALV, 'south', 'upper');          // 4.5..5.0 against the front wall
    roof(X, 4, 12, GALV, 'south', 'lower');          // 4.0..4.5
    roof(X, 3, 13, GALV, 'south', 'upper', true);    // 3.5..4.0, the beam on the posts (post tops 3.0)
  }
  for (const X of [1, 2, 3, 4, 5, 7, 9, 10, 11]) put(X, 1, 13, P.RAIL('x'));
  for (const Z of [11, 12]) { put(0, 1, Z, P.RAIL('z')); put(MX, 1, Z, P.RAIL('z')); }
  put(2, 1, 12, vstairs('north'));                         // rocking chair stand-in facing the view
  put(11, 1, 11, P.FIREWOOD('z')); put(11, 1, 12, P.FIREWOOD('z'));   // firewood on the porch

  // ---------------- interior stand-ins (M0) ----------------
  fill(7, 0, 1, 9, 0, 1, P.STONE);                         // hearth pad
  put(8, 1, 1, P.STOVE, 'stove');
  del(8, 4, 1);                                            // through the attic floor
  fill(8, 2, 1, 8, 5, 1, P.PIPE, 'pipe');                  // up to the roof cell (8, 6, 1): a boot round it
  fill(8, 7, 1, 8, 8, 1, P.PIPE, 'pipe');                  // above the roof, rain cap on top
  storage(9, 1, 1, 'barrel', 'up');
  fill(10, 1, 1, 11, 2, 1, M('bookshelf'));
  del(6, 4, 3); fill(6, 1, 3, 6, 4, 3, M('ladder[facing=east,waterlogged=false]'), 'ladder');
  // by the stove: two chairs facing it (armchairs later), the lamp stand between them (stand-in)
  put(8, 1, 4, P.CHAIR('north')); put(10, 1, 4, P.CHAIR('north'));
  put(9, 1, 4, vfence); put(9, 2, 4, M('lantern[hanging=false,waterlogged=false]'));
  put(6, 1, 7, P.TABLE()); put(7, 1, 7, P.CHAIR('west'));                   // the radio table and its chair
  put(11, 1, 7, P.TABLE(false, false, true)); put(11, 1, 8, P.TABLE(true));   // the dining table, two cells joined along Z
  put(10, 1, 7, P.CHAIR('east')); put(10, 1, 8, P.CHAIR('east'));
  storage(7, 2, 9, A('wooden_gun_rack'), 'north');                          // on the front wall by the door, 1.1..2.0 m up
  for (const [X, Z] of [[6, 4], [7, 4], [8, 4], [9, 4], [10, 4], [11, 4], [5, 4], [5, 5], [5, 6], [5, 7], [5, 8], [5, 9]]) put(X, 5, Z, vfence);
  put(1, 1, 1, P.BED, 'bed'); put(1, 1, 2, P.BED, 'bed');
  storage(1, 1, 3, 'chest', 'east');
  storage(3, 1, 1, 'barrel', 'up');
  for (const k of [1, 2]) for (const Z of [5, 6]) storage(1, k, Z, 'barrel', 'east');
  fill(1, 1, 8, 1, 2, 9, M('bookshelf'));
  storage(4, 1, 9, 'barrel', 'up');
  // the kitchen run on the back wall under the window: counter, sink counter (the pump handle over the west counter), counter
  storage(13, 1, 1, A('wooden_kitchen_counter'), 'south'); storage(14, 1, 1, A('wooden_sink_counter'), 'south');
  storage(15, 1, 1, A('wooden_kitchen_counter'), 'south');
  fill(13, 1, 4, 13, 2, 5, M('bookshelf'));
  put(16, 1, 4, P.COLD_BOX); put(16, 1, 5, P.COLD_BOX);
  put(13, 1, 6, vslab('bottom'));

  // ---------------- woodshed X 19..23 + genset bay X 24..26, Z -4..-2, rusty lean-to falling north ----------------
  fill(19, 0, -4, 26, 1, -4, P.BB_GREY);                    // back wall (top 2.0)
  for (const X of [19, 23]) fill(X, 0, -3, X, 1, -3, P.BB_GREY);   // side wall / partition
  for (const X of [19, 23, 24, 26]) fill(X, 0, -2, X, 2, -2, P.POST);
  for (let X = 19; X <= 26; X++) {
    roof(X, 3, -1, RUST, 'north', 'upper');                // 3.5..4.0, the high open edge
    roof(X, 3, -2, RUST, 'north', 'lower', true);          // 3.0..3.5 on the posts
    roof(X, 2, -3, RUST, 'north', 'upper');
    roof(X, 2, -4, RUST, 'north', 'lower');                // over the back wall
    roof(X, 1, -5, RUST, 'north', 'upper');                // eave 1.5
  }
  fill(20, 0, -3, 21, 1, -3, P.FIREWOOD('x')); put(22, 0, -3, P.FIREWOOD('x'));   // woodpile, about half full
  put(20, 0, -1, P.FIREWOOD('y'));                         // chopping block stand-in
  put(17, 0, -1, M('cauldron'));                           // rain barrel at the addition's corner
  fixtures.push({tool: 'place_fixture', block_id: A('portable_diesel_generator'), at: [25, 0, -3], facing: 'south'});

  // ---------------- carport X 19..25, Z 1..9: six posts, galvanised gable, ridge X 22 (k 4, 4.75) ----------------
  for (const X of [20, 24]) for (const Z of [2, 5, 8]) fill(X, 0, Z, X, 2, Z, P.POST);
  for (let X = 19; X <= 25; X++) for (let Z = 1; Z <= 9; Z++) {
    const d = Math.abs(X - 22), {k, shape} = slopeCell(4, d);
    roof(X, k, Z, GALV, X < 22 ? 'west' : X > 22 ? 'east' : 'west', shape, d === 2);
  }
  fill(21, -1, 3, 23, -1, 7, M('dirt_path'), 'vehicle_anchor');   // worn pad under the pickup (plan F8)

  // ---------------- outhouse X -6..-4, Z -8..-6: deck platform k 0 (M1.1, was solid planks), grey boards, board door, rusty lean-to falling north ----------------
  fill(-6, 0, -8, -4, 0, -6, P.DECK('x')); put(-5, 0, -5, P.STAIRS('north'));
  for (let X = -6; X <= -4; X++) for (let Z = -8; Z <= -6; Z++) {
    if (X === -5 && Z === -7) continue;                     // the 1 x 1 inside
    const top = Z === -6 ? 3 : 2;                           // back and middle to 3.0, the front up under the slope
    for (let k = 1; k <= top; k++) put(X, k, Z, P.BB_GREY);
  }
  door('board', -5, -6, 'north', 'left');
  for (let X = -7; X <= -3; X++) {
    roof(X, 4, -5, RUST, 'north', 'upper');                 // 4.5..5.0, the front overhang
    roof(X, 4, -6, RUST, 'north', 'lower');                 // over the front wall
    roof(X, 3, -7, RUST, 'north', 'upper');
    roof(X, 3, -8, RUST, 'north', 'lower');                 // over the back wall
    roof(X, 2, -9, RUST, 'north', 'upper');                 // eave 2.5
  }
  put(-5, 1, -7, vslab('bottom'));                          // the seat (M2)

  // ---------------- root cellar (M1.3): sunken fieldstone vault, inside X 5..10 x Z -7..-5, door at the east end ----------------
  // User 2026-10-13: the M1.1 cellar (inside 3 x 2) was too small and could not be entered: its lower stair stood a block
  // above the floor, so walking off it the head hit the stone over the door. Now the inside is 6 m deep, 3 m wide, 2 m
  // clear (k -2..-1, floor k -3, ceiling k 0), an aisle from the door with shelf stand-ins along both side walls at the
  // back and a lantern by the door (no dark floor for mobs). Walls X 4 / Z -8 / Z -4, the headwall X 11 (k -3..1) with the
  // board door at (11, -2..-1, -6). The trench in front runs east along Z -6: a landing at floor level (12), stairs (13, k -2)
  // and (14, k -1) rising east to the grade at X 15; fieldstone retaining walls Z -7 / Z -5. The mound steps one block at a
  // time for Terrain Smooth V1: grass k 1 over the inside (2.0 above grade), grass k 0 on the walls (1.0), then the grade;
  // the headwall steps the same way (k 1 across the inside, k 0 at its ends).
  for (let X = 4; X <= 11; X++) for (let Z = -8; Z <= -4; Z++) {
    const inside = X >= 5 && X <= 10 && Z >= -7 && Z <= -5, head = X === 11;
    put(X, -3, Z, P.STONE);
    for (const k of [-2, -1]) put(X, k, Z, inside ? M('air') : P.STONE);
    put(X, 0, Z, inside || head ? P.STONE : P.GRASS);
    if (inside) put(X, 1, Z, P.GRASS);
    else if (head && Z >= -7 && Z <= -5) put(X, 1, Z, P.STONE);
  }
  door('board', 11, -6, 'west', 'left', -2);
  put(12, -3, -6, P.STONE); put(12, -2, -6, M('air')); put(12, -1, -6, M('air'));   // the landing at floor level
  put(13, -2, -6, P.STAIRS('east')); put(13, -1, -6, M('air'));
  put(14, -1, -6, P.STAIRS('east'));
  for (const Z of [-7, -5]) { fill(12, -3, Z, 13, -1, Z, P.STONE); fill(14, -2, Z, 14, -1, Z, P.STONE); }
  for (const Z of [-7, -5]) fill(5, -2, Z, 7, -1, Z, M('bookshelf'));   // shelf stand-ins (M2)
  put(9, -1, -7, M('lantern[hanging=true,waterlogged=false]'));            // hangs from the ceiling beside the aisle

  // ---------------- garden X -9..-3, Z 1..9: woven wire fence round tilled beds, gate gap toward the porch ----------------
  for (let X = -9; X <= -3; X++) for (const Z of [1, 9]) put(X, 0, Z, P.FENCE, 'fence');
  for (let Z = 2; Z <= 8; Z++) for (const X of [-9, -3]) put(X, 0, Z, P.FENCE, 'fence');
  del(-6, 0, 9);                                            // gate gap
  for (const Z of [3, 5, 7]) fill(-8, -1, Z, -4, -1, Z, P.BED_SOIL, 'bed');

  // air cells above the pad are already air after the clearing pass
  for (const [kk, s] of [...cells]) if (s === M('air') && Number(kk.split(',')[1]) >= 0) cells.delete(kk);
  return {cells, fixtures, tags};
}

// ---------------- offline checks ----------------
export function checks({cells, tags, fixtures}) {
  const get = (X, k, Z) => cells.get(X + ',' + k + ',' + Z) || null, out = [];
  const isRoof = s => s && /_roof\[/.test(s);
  for (const [kk, s] of cells) {
    const [X, k, Z] = kk.split(',').map(Number);
    if (s.includes('half=lower') && !(get(X, k + 1, Z) || '').includes('half=upper')) out.push('door without upper ' + kk);
    if (s.includes('half=upper') && !(get(X, k - 1, Z) || '').includes('half=lower')) out.push('door without lower ' + kk);
    if (s.includes('_door[') && s.includes('half=lower') && !get(X, k - 1, Z)) out.push('door on air ' + kk);
    if (s === 'apocalypse_firstlight:white_oak_porch_post' && k === 0 && !get(X, -1, Z)) out.push('post on air ' + kk);
    if (s.includes('stove_pipe') && !(get(X, k - 1, Z) || '').match(/stove_pipe|steel_block|_roof\[/)) out.push('pipe unsupported ' + kk);
  }
  // roof cells: every one needs a same-facing neighbour chain or an eave; count shapes and materials
  const roofs = {};
  for (const [, s] of cells) if (isRoof(s)) { const m = s.match(/:(\w+)\[beam=(\w+),facing=(\w+),shape=(\w+)\]/); const k2 = m[1] + ' ' + m[4]; roofs[k2] = (roofs[k2] || 0) + 1; }
  for (const f of fixtures) if (get(...f.at)) out.push('fixture on a cell ' + f.at);
  // ground floor walking routes (M0 v2), 3 clear layers k 1..3
  const fx = new Set(fixtures.map(f => f.at.join(',')));
  const walk = [[8, 9], [8, 8], [8, 7], [8, 6], [8, 5], [9, 5], [10, 5], [11, 5], [11, 4], [11, 3], [11, 2], [7, 2], [7, 3], [6, 2],
    [6, 5], [7, 5], [6, 8], [7, 8], [4, 5], [4, 8], [3, 5], [3, 8], [13, 2], [13, 3], [14, 3], [15, 3], [16, 2], [16, 1], [15, 4], [15, 5], [15, 6]];
  for (const [X, Z] of walk) for (let k = 1; k <= 3; k++) if (get(X, k, Z) || fx.has([X, k, Z].join(','))) out.push('walk cell blocked ' + [X, k, Z]);
  // the way into the cellar (M1.3): feet cell + head cell clear from the grade down to the aisle, solid under each step
  const clear = (X, k, Z) => { const c = get(X, k, Z); return !c || c === 'minecraft:air' || c.includes('_door['); };
  for (const [X, k, Z] of [[15, 0, -6], [14, 0, -6], [13, -1, -6], [12, -2, -6], [11, -2, -6], [10, -2, -6], [9, -2, -6], [8, -2, -6], [7, -2, -6], [6, -2, -6], [5, -2, -6]]) {
    if (!clear(X, k, Z) || !clear(X, k + 1, Z)) out.push('cellar way blocked ' + [X, k, Z]);
    if (clear(X, k - 1, Z) && !(X === 15)) out.push('cellar way on air ' + [X, k, Z]);
  }
  for (const [X, k, Z] of [[14, 0, -6], [13, -1, -6]]) if (!clear(X, k + 1, Z) || (k === -1 && !clear(12, 0, Z))) out.push('cellar head room ' + [X, k, Z]);
  // porch clearance under the beam row: posts k 1..2 -> 2.0 m over the deck
  return {issues: out, roofs, cells: cells.size};
}

// ---------------- batches: runs along X of one state, at most 128 ops per call ----------------
const plotOf = ([X, k, Z]) => [X - X0, k - K0, Z - Z0];
export function batches(cells) {
  const byRow = new Map();
  for (const [kk, s] of cells) { const [X, k, Z] = kk.split(',').map(Number), r = k + ',' + Z; (byRow.get(r) || byRow.set(r, []).get(r)).push([X, s]); }
  const ops = [];
  for (const [r, list] of [...byRow].sort((a, b) => { const [ka, za] = a[0].split(',').map(Number), [kb, zb] = b[0].split(',').map(Number); return ka - kb || za - zb; })) {
    const [k, Z] = r.split(',').map(Number); list.sort((a, b) => a[0] - b[0]);
    let i = 0; while (i < list.length) { let j = i; while (j + 1 < list.length && list[j + 1][0] === list[j][0] + 1 && list[j + 1][1] === list[i][1]) j++;
      ops.push({min: plotOf([list[i][0], k, Z]), max: plotOf([list[j][0], k, Z]), block: list[i][1]}); i = j + 1; }
  }
  const out = [];
  for (let i = 0; i < ops.length; i += 128) out.push({name: 'cells ' + (i / 128 + 1), ops: ops.slice(i, i + 128)});
  return out;
}

async function build() {
  const c = new BridgeClient('./run'), dir = path.resolve('build/authoring_checks', ID);
  const status = await c.call('minecraft_status'), info = await c.call('authoring_info');
  if (info.id !== ID || info.width !== SIZE[0] || info.height !== SIZE[1] || info.depth !== SIZE[2]) throw Error('PLOT_MISMATCH ' + JSON.stringify(info));
  const o = info.min, world = ([x, y, z]) => [o[0] + x, o[1] + y, o[2] + z];
  const {cells} = recipe(), report = {plot: info, world: status.world, writes: [], failures: []};
  // clear the plot above the pad first (trees, terrain; one call per Z slice, so a stray animal stops only its slice), then the
  // recipe (the pad itself is part of it); the summary lists what failed (rerun build after moving the cause away)
  const clear = [];
  for (let Z = Z0; Z <= Z1; Z++) clear.push({name: 'clear Z' + Z, ops: [{min: plotOf([X0, 0, Z]), max: plotOf([X1, K1, Z]), block: M('air')}]});
  for (const b of [...clear, ...batches(cells)]) {
    try {
      const r = await c.call('we_batch_set', {target: 'AUTHORING_SESSION', operations: b.ops.map(op => ({min: world(op.min), max: world(op.max), block: op.block}))});
      report.writes.push({batch: b.name, ops: b.ops.length, changed: r.changed ?? r.blocks_changed ?? null});
    } catch (e) { report.failures.push({batch: b.name, ops: b.ops.length, error: String(e.message || e)}); }
  }
  const rec = await c.call('reconcile_shapes', {target: 'AUTHORING_SESSION'});
  report.reconciled = rec.reconciled ?? rec.changed_blocks ?? null;
  const audit = await c.call('audit_support', {target: 'AUTHORING_SESSION'});
  report.audit = {issue_count: audit.issue_count, counts: audit.counts, issues: audit.issues?.slice(0, 40)};
  await mkdir(dir, {recursive: true}); await writeFile(path.join(dir, 'build_m1.json'), JSON.stringify(report, null, 1));
  console.log(JSON.stringify({batches: report.writes.length, failures: report.failures, reconciled: report.reconciled, audit: report.audit.counts}, null, 1));
}

/** Redo whole Z slices (clear above the pad, then their recipe cells): for a slice whose clearing an animal stopped. */
async function rebuildSlices(zs) {
  const c = new BridgeClient('./run');
  await c.call('minecraft_status'); const info = await c.call('authoring_info');
  if (info.id !== ID) throw Error('PLOT_MISMATCH ' + JSON.stringify(info));
  const o = info.min, world = ([x, y, z]) => [o[0] + x, o[1] + y, o[2] + z], out = [];
  const {cells} = recipe();
  for (const Z of zs) {
    const slice = new Map([...cells].filter(([kk]) => Number(kk.split(',')[2]) === Z));
    for (const b of [{name: 'clear Z' + Z, ops: [{min: plotOf([X0, 0, Z]), max: plotOf([X1, K1, Z]), block: M('air')}]}, ...batches(slice)]) {
      try { await c.call('we_batch_set', {target: 'AUTHORING_SESSION', operations: b.ops.map(op => ({min: world(op.min), max: world(op.max), block: op.block}))}); out.push('ok ' + b.name); }
      catch (e) { out.push('FAIL ' + b.name + ' ' + String(e.message || e)); if (b.name.startsWith('clear')) break; }
    }
  }
  await c.call('reconcile_shapes', {target: 'AUTHORING_SESSION'});
  console.log(out.join(String.fromCharCode(10)));
}

/**
 * Redo one box (X xa..xb, Z za..zb, every layer): clear it above the pad, then write its recipe cells, layers below the
 * grade included. For a change that must not touch the rest of its Z rows (the genset and the containers are fixtures
 * and a slice clear would remove them). M1.3: `region 3 15 -10 -2` = the root cellar.
 */
async function rebuildRegion(xa, xb, za, zb) {
  const c = new BridgeClient('./run');
  await c.call('minecraft_status'); const info = await c.call('authoring_info');
  if (info.id !== ID) throw Error('PLOT_MISMATCH ' + JSON.stringify(info));
  const o = info.min, world = ([x, y, z]) => [o[0] + x, o[1] + y, o[2] + z], out = [];
  const inBox = kk => { const [X, , Z] = kk.split(',').map(Number); return X >= xa && X <= xb && Z >= za && Z <= zb; };
  const cells = new Map([...recipe().cells].filter(([kk]) => inBox(kk)));
  const clearOps = [];
  for (let Z = za; Z <= zb; Z++) clearOps.push({min: plotOf([xa, 0, Z]), max: plotOf([xb, K1, Z]), block: M('air')});
  for (const b of [{name: 'clear', ops: clearOps}, ...batches(cells)]) {
    try { await c.call('we_batch_set', {target: 'AUTHORING_SESSION', operations: b.ops.map(op => ({min: world(op.min), max: world(op.max), block: op.block}))}); out.push('ok ' + b.name + ' (' + b.ops.length + ' ops)'); }
    catch (e) { out.push('FAIL ' + b.name + ' ' + String(e.message || e)); if (b.name === 'clear') break; }
  }
  const rec = await c.call('reconcile_shapes', {target: 'AUTHORING_SESSION'});
  const audit = await c.call('audit_support', {target: 'AUTHORING_SESSION'});
  out.push('reconciled ' + (rec.reconciled ?? rec.changed_blocks ?? '?') + ', support issues ' + audit.issue_count);
  console.log(out.join(String.fromCharCode(10)));
}

async function placeFixtures() {
  const c = new BridgeClient('./run'), dir = path.resolve('build/authoring_checks', ID);
  await c.call('minecraft_status'); const info = await c.call('authoring_info');
  if (info.id !== ID) throw Error('PLOT_MISMATCH ' + JSON.stringify(info));
  const o = info.min, world = ([x, y, z]) => [o[0] + x, o[1] + y, o[2] + z];
  const log = [];
  for (const f of recipe().fixtures) {
    const pos = world(plotOf(f.at));
    try { const r = await c.call(f.tool, {block_id: f.block_id, pos, facing: f.facing}); log.push({id: f.block_id, pos, ok: true, warnings: r.warnings}); }
    catch (e) { log.push({id: f.block_id, pos, error: String(e.message || e)}); }
  }
  await mkdir(dir, {recursive: true}); await writeFile(path.join(dir, 'fixtures_m1.json'), JSON.stringify(log, null, 1));
  console.log(JSON.stringify(log.map(l => (l.ok ? 'ok ' : 'FAIL ') + l.id.replace(/^.*:/, '') + ' ' + l.pos + (l.error ? ' ' + l.error : ''))));
}

async function plan() {
  const r = recipe(), b = batches(r.cells), dir = path.resolve('build/authoring_checks', ID);
  const list = [...r.cells].map(([kk, s]) => { const [X, k, Z] = kk.split(',').map(Number); return {X, k, Z, s, tag: r.tags.get(kk) || null}; });
  await mkdir(dir, {recursive: true}); await writeFile(path.join(dir, 'cells_m1.json'), JSON.stringify({size: SIZE, cells: list, fixtures: r.fixtures}));
  console.log(JSON.stringify({cells: r.cells.size, batches: b.length, ops: b.reduce((n, x) => n + x.ops.length, 0), fixtures: r.fixtures.length, checks: checks(r)}, null, 1));
}

/**
 * M2.1 (2026-10-13): swap the M0 furniture stand-ins of a built house for Wood Furniture V1 without touching anything else:
 * the chair and table cells are rewritten, the cells of the counters and the gun rack cleared (the planks, the cauldron, the
 * trapdoor) and those placed as fixtures (they keep a block entity).
 */
async function furniture() {
  const c = new BridgeClient('./run');
  await c.call('minecraft_status'); const info = await c.call('authoring_info');
  if (info.id !== ID) throw Error('PLOT_MISMATCH ' + JSON.stringify(info));
  const o = info.min, world = ([x, y, z]) => [o[0] + x, o[1] + y, o[2] + z], out = [];
  const r = recipe(), fx = r.fixtures.filter(f => f.block_id.includes(':wooden_')), ops = [];
  for (const [kk, s] of r.cells) if (s.includes(':wooden_')) { const at = world(plotOf(kk.split(',').map(Number))); ops.push({min: at, max: at, block: s}); }
  for (const f of fx) { const at = world(plotOf(f.at)); ops.push({min: at, max: at, block: M('air')}); }
  try { await c.call('we_batch_set', {target: 'AUTHORING_SESSION', operations: ops}); out.push('ok cells (' + ops.length + ' ops)'); }
  catch (e) { out.push('FAIL cells ' + String(e.message || e)); }
  for (const f of fx) {
    const pos = world(plotOf(f.at));
    try { await c.call(f.tool, {block_id: f.block_id, pos, facing: f.facing}); out.push('ok ' + f.block_id.replace(/^.*:/, '') + ' ' + pos); }
    catch (e) { out.push('FAIL ' + f.block_id.replace(/^.*:/, '') + ' ' + pos + ' ' + String(e.message || e)); }
  }
  console.log(out.join(String.fromCharCode(10)));
}

const direct = process.argv[1] && path.resolve(process.argv[1]).toLowerCase() === fileURLToPath(import.meta.url).toLowerCase();
const mode = direct ? process.argv[2] : null;
if (mode === 'plan') await plan();
else if (mode === 'build') await build();
else if (mode === 'fixtures') await placeFixtures();
else if (mode === 'slices') await rebuildSlices(process.argv.slice(3).map(Number));
else if (mode === 'region') await rebuildRegion(...process.argv.slice(3, 7).map(Number));
else if (mode === 'furniture') await furniture();
else if (direct) console.log('modes: plan | build | fixtures | slices <Z..> | region <xa> <xb> <za> <zb> | furniture');
