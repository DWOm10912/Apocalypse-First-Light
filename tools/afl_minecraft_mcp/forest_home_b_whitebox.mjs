// Forest start home B "溪谷老宅" — M0 white-box (2026-10-12; docs/worldgen/forest_home_b_whitebox_m0.md) through the AFL
// authoring bridge: live authoring only, no export, no new blocks, no road, no spawn logic.
// Paper frame = the concept's block plan (docs/worldgen/forest_start_home_concept_v1.md section 4): X east, Z south (front of
// the house faces +Z), k = layer above grade (k 0 = the first air layer at the player's feet: plinth, floors, porch deck).
// v2 (user 2026-10-12, "屋子里面可能有点太挤了，扩大一点"): main house walls X 0..12 x Z 0..10 (13 x 11 m; v1 was 11 x 9),
// bedroom + storeroom | living partition X 5, bedroom | storeroom partition Z 7; addition X 12..17 x Z 0..7 (shared wall X 12);
// porch X 0..12 x Z 11..13, steps (8, 14). Plot = 27 x 9 x 20: x = X + 1 (X -1..25), y = k (0..8), z = Z + 5 (Z -5..14),
// front SOUTH, no rotation, nothing below grade.
// Heights: floor top 1.0 m above grade, ground floor 3 m clear (k 1..3), attic floor k 4, knee wall k 5, wall plate 6.0,
// roof 1:2 (26.6 deg, user 2026-10-12) as slab steps (0.5 rise per block; white-box stand-in only: the real roof is a Mesh
// metal roof, plan S2), ridge 9.0. Two porch roofs (--porch=):
//   low (default since v2; the concept render): a separate 1:2 porch roof under the main eave; header at 3.0, 2.0 m clear at the posts;
//   catslide: the main roof's front slope runs on over the porch; header at 4.0, 3.0 m clear at the posts, 4.5 m at the wall.
//   node tools/afl_minecraft_mcp/forest_home_b_whitebox.mjs plan [--porch=catslide]   -> counts + checks + cells JSON (no game)
//   node tools/afl_minecraft_mcp/forest_home_b_whitebox.mjs build [--porch=catslide]  -> writes into the active plot (27 x 9 x 20, id forest_home_b_m0_v2),
//                                                                                        reconciles fence / pane shapes, audits support
//   node tools/afl_minecraft_mcp/forest_home_b_whitebox.mjs switch --porch=catslide   -> changes a built house to the other porch (cell diff)
//   node tools/afl_minecraft_mcp/forest_home_b_whitebox.mjs fixtures                  -> genset, kit chest and barrels (block entities, place_fixture)
import {BridgeClient} from './bridge_client.mjs';
import {mkdir, writeFile} from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

export const ID = 'forest_home_b_m0_v2', SIZE = [27, 9, 20];
const A = id => 'apocalypse_firstlight:' + id, M = id => 'minecraft:' + id;

// ---- white-box palette: existing AFL blocks wherever AFL has one (user 2026-10-12: the first in-game build used vanilla
// concrete / prismarine and read as a stucco house; then waxed copper slabs for the roof were rejected too). Vanilla only where
// AFL has nothing yet (doors, windows, plinth stone, small furniture). None of this is the final look (M1 / M2: Mesh). ----
export const P = {
  LOG_WALL: axis => A(`white_pine_rounded_timber[axis=${axis}]`),   // main house log walls, logs along the wall (plan S1)
  BOARD_WALL: A('eastern_hemlock_planks'),  // addition / shed board-and-batten (S6)
  PARTITION: A('white_pine_planks'),        // interior board partitions
  PLINTH: M('stone_bricks'),                // stone plinth (S8 placeholder) and hearth (S10): no AFL fieldstone yet
  FLOOR: A('white_pine_planks'),            // floors, attic floor, porch deck (S9 placeholder)
  ROOF: t => A(t === 'full' ? 'steel_plate' : `steel_plate_slab[type=${t},waterlogged=false]`),   // every roof: AFL steel sheet stand-in for S2
  HEADER: A('white_pine_rounded_timber[axis=x]'),   // porch header: a full cell, so its top is the roof surface (a beam is only 8/16 thick)
  POST: axis => A(`white_pine_beam[axis=${axis},waterlogged=false]`),
  COUNTER: A('white_oak_planks'),           // kitchen counters, freezer stand-in
  STOVE: A('steel_block'),                  // cast-iron wood stove (F1)
  PIPE: A('steel_cable[facing=north,segment=vertical]'),   // stove pipe, 2/16 thin
  BED: M('smooth_quartz_slab[type=bottom,waterlogged=false]'),   // bed (P4): beds are block entities the bridge does not place
  GUN_RACK: M('spruce_trapdoor[facing=north,half=bottom,open=true,powered=false,waterlogged=false]'),   // gun rack marker on the front wall (F2)
};
// main house outer walls and the attic
const MX = 12, MZ = 10, RIDGE_Z = 5;
// log cabin walls: logs along each wall; the four corners alternate by layer like notched corners
const logAt = (X, k, Z) => {
  const corner = (X === 0 || X === MX) && (Z === 0 || Z === MZ);
  if (corner) return P.LOG_WALL(k % 2 === 1 ? 'x' : 'z');
  return P.LOG_WALL(Z === 0 || Z === MZ ? 'x' : 'z');
};
const pane = axis => M(`glass_pane[east=${axis === 'x'},north=${axis === 'z'},south=${axis === 'z'},waterlogged=false,west=${axis === 'x'}]`);
const door = (facing, half, hinge = 'left') => M(`spruce_door[facing=${facing},half=${half},hinge=${hinge},open=false,powered=false]`);
const stairs = facing => M(`spruce_stairs[facing=${facing},half=bottom,shape=straight,waterlogged=false]`);
const slab = t => M(`spruce_slab[type=${t},waterlogged=false]`);
const fence = (n, e, s, w) => M(`spruce_fence[east=${e},north=${n},south=${s},waterlogged=false,west=${w}]`);

// Roof surface heights (paper X / Z of a cell -> metres above grade); the roof cell is a bottom slab at layer floor(s) when s
// ends in .5, else a top slab at layer s - 1; over a gable / rake wall a whole number s takes a full roof block at layer s - 1
// (the wall stops one layer lower), so the roof reads unbroken from above and the gable has no half-block slit.
export const mainRoofS = Z => 9.0 - Math.abs(Z - RIDGE_Z) / 2;    // ridge along X at Z 5 (6.5 over the walls Z 0 / Z 10)
export const lowPorchRoofS = Z => 4.0 + (13 - Z) / 2;              // the separate porch roof (--porch=low), header top 4.0 at Z 13
export const undersideOf = s => s % 1 !== 0 ? Math.floor(s) : s - 0.5;   // bottom of the roof cell under a surface height
export const additionRoofS = X => 4.5 + (17 - X) / 2;             // shed roof falling east, 6.5 against the main gable (= its eave)
export const backShedRoofS = Z => 3.5 + (Z + 2) / 2;              // woodshed + genset bay lean-to, falling north
export const carportRoofS = X => 4.5 - (Math.abs(X - 21.5) - 0.5) / 2;   // carport gable, ridge along Z between X 21 and 22

export function recipe({porch = 'low'} = {}) {
  if (porch !== 'catslide' && porch !== 'low') throw Error('porch must be catslide or low');
  const cells = new Map(), fixtures = [], tags = new Map();
  const key = (X, k, Z) => X + ',' + k + ',' + Z;
  const put = (X, k, Z, s, tag) => {
    if (X < -1 || X > 25 || k < 0 || k > 8 || Z < -5 || Z > 14) throw Error('OUT ' + [X, k, Z]);
    cells.set(key(X, k, Z), s); if (tag) tags.set(key(X, k, Z), tag);
  };
  const has = (X, k, Z) => cells.has(key(X, k, Z));
  const fill = (X0, k0, Z0, X1, k1, Z1, s, tag) => { for (let k = k0; k <= k1; k++) for (let Z = Z0; Z <= Z1; Z++) for (let X = X0; X <= X1; X++) put(X, k, Z, s, tag); };
  // a roof cell from its surface height; wallFill = the wall material when a gable / rake wall stands under it
  const roofCell = (X, Z, s, slabOf, wallFill, wallTop) => {
    const half = s % 1 !== 0, top = half ? Math.floor(s) : s - 1;
    if (wallFill) { for (let k = wallTop + 1; k <= top - 1; k++) put(X, k, Z, wallFill, 'gable'); if (!half) { put(X, top, Z, slabOf('full'), 'roof'); return; } }
    if (!has(X, top, Z)) put(X, top, Z, slabOf(half ? 'bottom' : 'top'), 'roof');
  };
  // storage is placed with place_fixture (block entities)
  const storage = (X, k, Z, id, facing) => fixtures.push({tool: 'place_fixture', block_id: M(id), at: [X, k, Z], facing});

  // ---------------- main house: plinth, floor, log walls k 1..5 ----------------
  fill(0, 0, 0, MX, 0, MZ, P.PLINTH); fill(1, 0, 1, MX - 1, 0, MZ - 1, P.FLOOR);
  for (let k = 1; k <= 5; k++) for (let X = 0; X <= MX; X++) for (let Z = 0; Z <= MZ; Z++)
    if (X === 0 || X === MX || Z === 0 || Z === MZ) put(X, k, Z, logAt(X, k, Z));
  // addition: plinth, floor, board walls k 1..3 (X 12 is the main house's east gable wall)
  fill(13, 0, 0, 17, 0, 7, P.PLINTH); fill(13, 0, 1, 16, 0, 6, P.FLOOR);
  for (let k = 1; k <= 3; k++) { fill(13, k, 0, 17, k, 0, P.BOARD_WALL); fill(13, k, 7, 17, k, 7, P.BOARD_WALL); fill(17, k, 1, 17, k, 6, P.BOARD_WALL); }
  // partitions k 1..3: X 5 (bedroom + storeroom | living), Z 7 (bedroom | storeroom)
  fill(5, 1, 1, 5, 3, 9, P.PARTITION); fill(1, 1, 7, 4, 3, 7, P.PARTITION);
  // attic floor k 4: north band Z 1..4 over the whole width, and over the bedroom + storeroom (X 1..5, Z 5..9); ladder hole (6, 3)
  fill(1, 4, 1, MX - 1, 4, 4, P.FLOOR); fill(1, 4, 5, 5, 4, 9, P.FLOOR);
  // porch deck k 0 (X 0..12, Z 11..13) and the steps (8, 14)
  fill(0, 0, 11, MX, 0, 13, P.FLOOR); put(8, 0, 14, stairs('north'));

  // ---------------- openings: doors k 1..2, windows ----------------
  const openDoor = (X, Z, facing, hinge) => { put(X, 1, Z, door(facing, 'lower', hinge), 'door'); put(X, 2, Z, door(facing, 'upper', hinge), 'door'); };
  openDoor(8, 10, 'north', 'left');   // front door into the living room
  openDoor(5, 5, 'east', 'left');     // bedroom
  openDoor(5, 8, 'east', 'right');    // storeroom
  openDoor(12, 2, 'east', 'right');   // living -> kitchen
  openDoor(16, 0, 'south', 'left');   // back door (woodshed, genset, cellar)
  openDoor(15, 7, 'north', 'right');  // mudroom door (yard, carport)
  const win = (X, Z, axis, k0, k1) => { for (let k = k0; k <= k1; k++) put(X, k, Z, pane(axis), 'window'); };
  for (const X of [6, 10]) win(X, MZ, 'x', 2, 3);    // living, front, 1 x 2 (sill 1 m, head 3 m)
  win(2, MZ, 'x', 2, 2);                              // storeroom, front, 1 x 1
  win(3, 0, 'x', 2, 3);                               // bedroom, back
  win(0, 3, 'z', 2, 3);                               // bedroom, west
  win(14, 0, 'x', 2, 2);                              // over the sink, 1 x 1
  win(13, 7, 'x', 2, 2);                              // addition front, 1 x 1 over the bench
  win(17, 3, 'z', 2, 3);                              // addition east

  // ---------------- roofs ----------------
  // main roof X -1..13, Z -1..14 (catslide) or ..11 (low); gable walls X 0 and X 12 (Z 0..10) carry the wall up to the
  // surface; the east overhang (X 13) stops where the addition roof is (Z -1..8)
  const mainZMax = porch === 'catslide' ? 14 : MZ + 1;
  for (let X = -1; X <= MX + 1; X++) for (let Z = -1; Z <= mainZMax; Z++) {
    if (X === MX + 1 && Z >= -1 && Z <= 8) continue;
    const gable = (X === 0 || X === MX) && Z >= 0 && Z <= MZ;
    roofCell(X, Z, mainRoofS(Z), P.ROOF, gable ? P.LOG_WALL('z') : null, 5);
  }
  // addition shed roof X 13..18, Z -1..8; rake walls Z 0 and Z 7 (X 13..16) and the east wall X 17 carry up to it
  for (let X = 13; X <= 18; X++) for (let Z = -1; Z <= 8; Z++) {
    const rake = (Z === 0 || Z === 7) && X >= 13 && X <= 16;
    roofCell(X, Z, additionRoofS(X), P.ROOF, rake ? P.BOARD_WALL : null, 3);
  }

  // attic gable windows: west 1 x 2 at eye level, east 1 x 1 above the addition roof
  win(0, RIDGE_Z, 'z', 5, 6); win(MX, RIDGE_Z, 'z', 7, 7);

  // ---------------- porch: posts, header, railing, chair, firewood ----------------
  const headerK = porch === 'catslide' ? 4 : 3;
  for (const X of [0, 6, MX]) fill(X, 1, 13, X, headerK - 1, 13, P.POST('y'));
  fill(0, headerK, 13, MX, headerK, 13, P.HEADER, 'header');   // header; its top is the roof surface at Z 13, so no slab there
  if (porch === 'low') for (let X = -1; X <= MX + 1; X++) for (let Z = 11; Z <= 14; Z++) roofCell(X, Z, lowPorchRoofS(Z), P.ROOF, null, 0);
  const rail = [[1, 13], [2, 13], [3, 13], [4, 13], [5, 13], [7, 13], [9, 13], [10, 13], [11, 13], [0, 11], [0, 12], [MX, 11], [MX, 12]];
  const railSet = new Set(rail.map(([X, Z]) => X + ',' + Z));
  for (const [X, Z] of rail) {
    const r = (a, b) => railSet.has(a + ',' + b);
    put(X, 1, Z, fence(r(X, Z - 1), r(X + 1, Z), r(X, Z + 1), r(X - 1, Z)));
  }
  put(2, 1, 12, stairs('north'));                                         // rocking chair (stand-in) facing the view
  put(11, 1, 11, M('spruce_log[axis=z]')); put(11, 1, 12, M('spruce_log[axis=z]'));   // firewood on the porch

  // ---------------- interior stand-ins ----------------
  // living room X 6..11, Z 1..9 (6 x 9 m): hearth + stove (8, 1), pipe up through the attic and roof, wood box, shelves, ladder,
  // two armchairs + side table facing the stove, radio desk by the partition, dining table, gun rack by the front door
  fill(7, 0, 1, 9, 0, 1, P.PLINTH);                                     // hearth pad
  put(8, 1, 1, P.STOVE, 'stove');
  for (let k = 2; k <= 8; k++) cells.delete(key(8, k, 1));
  fill(8, 2, 1, 8, 8, 1, P.PIPE, 'pipe');
  storage(9, 1, 1, 'barrel', 'up');                                       // wood box
  fill(10, 1, 1, 11, 2, 1, M('bookshelf'));                               // open shelves on the back wall (the kitchen door (12, 2) stays clear)
  cells.delete(key(6, 4, 3)); fill(6, 1, 3, 6, 4, 3, M('ladder[facing=east,waterlogged=false]'), 'ladder');
  put(8, 1, 4, stairs('south')); put(10, 1, 4, stairs('south'));         // armchairs facing the stove
  put(9, 1, 4, fence(false, false, false, false)); put(9, 2, 4, M('lantern[hanging=false,waterlogged=false]'));
  put(6, 1, 7, slab('top')); put(7, 1, 7, stairs('east'));                // radio desk + chair
  put(11, 1, 7, slab('top')); put(11, 1, 8, slab('top'));                 // dining table
  put(10, 1, 7, stairs('west')); put(10, 1, 8, stairs('west'));
  put(7, 2, 9, P.GUN_RACK, 'gunrack');                                  // gun rack location (F2)
  // attic edge railing k 5: along Z 4 over the living room (the ladder steps off to (6, 2) or (7, 3)), along X 5 over the bedroom
  const atticRail = [[6, 4], [7, 4], [8, 4], [9, 4], [10, 4], [11, 4], [5, 4], [5, 5], [5, 6], [5, 7], [5, 8], [5, 9]];
  const ar = new Set(atticRail.map(([X, Z]) => X + ',' + Z));
  for (const [X, Z] of atticRail) put(X, 5, Z, fence(ar.has(X + ',' + (Z - 1)), ar.has((X + 1) + ',' + Z), ar.has(X + ',' + (Z + 1)), ar.has((X - 1) + ',' + Z)));
  // bedroom X 1..4, Z 1..6: bed (1, 1..2) = spawn, kit chest at its foot, dresser under the back window, wardrobe
  put(1, 1, 1, P.BED, 'bed'); put(1, 1, 2, P.BED, 'bed');
  storage(1, 1, 3, 'chest', 'east');
  storage(3, 1, 1, 'barrel', 'up');
  for (const k of [1, 2]) for (const Z of [5, 6]) storage(1, k, Z, 'barrel', 'east');
  // storeroom X 1..4, Z 8..9: shelves along the west wall, a barrel
  fill(1, 1, 8, 1, 2, 9, M('bookshelf'));
  storage(4, 1, 9, 'barrel', 'up');
  // kitchen / mudroom X 13..16, Z 1..6: counters + sink under the window, pantry shelves, freezer, mudroom bench
  put(13, 1, 1, P.COUNTER); put(14, 1, 1, M('cauldron')); put(15, 1, 1, P.COUNTER);
  fill(13, 1, 4, 13, 2, 5, M('bookshelf'));
  put(16, 1, 4, P.COUNTER); put(16, 1, 5, P.COUNTER);                     // chest freezer stand-in
  put(13, 1, 6, slab('bottom'));

  // ---------------- back sheds: woodshed X 18..22, genset bay X 23..25, Z -4..-2, one lean-to roof falling north ----------------
  fill(18, 0, -4, 25, 1, -4, P.BOARD_WALL);                              // back wall
  for (const X of [18, 22]) { put(X, 0, -3, P.BOARD_WALL); put(X, 1, -3, P.BOARD_WALL); put(X, 2, -3, P.BOARD_WALL); }   // side wall / partition
  for (const X of [18, 22, 23, 25]) fill(X, 0, -2, X, 2, -2, P.POST('y'));
  for (let X = 18; X <= 25; X++) for (let Z = -5; Z <= -1; Z++) roofCell(X, Z, backShedRoofS(Z), P.ROOF, null, 0);
  fill(19, 0, -3, 20, 1, -3, M('spruce_log[axis=x]')); put(21, 0, -3, M('spruce_log[axis=x]'));   // woodpile, about half full (F5)
  put(19, 0, -1, M('spruce_log[axis=y]'));                                // chopping block
  put(17, 0, -1, M('cauldron'));                                          // rain barrel at the addition's corner
  fixtures.push({tool: 'place_fixture', block_id: A('portable_diesel_generator'), at: [24, 0, -3], facing: 'south'});
  // jerry can (23, 0, -3): not on the bridge whitelist (docs/authoring/afl_industrial_palette_v1.md), placed by hand if wanted

  // ---------------- carport X 19..24, Z 2..8: six posts, gable roof, the vehicle anchor footprint ----------------
  for (const X of [19, 24]) for (const Z of [2, 5, 8]) fill(X, 0, Z, X, 2, Z, P.POST('y'));
  for (let X = 19; X <= 25; X++) for (let Z = 1; Z <= 9; Z++) roofCell(X, Z, carportRoofS(X), P.ROOF, null, 0);
  fill(21, 0, 3, 22, 0, 7, M('yellow_carpet'), 'vehicle_anchor');        // pickup about 5.2 x 1.9 m, nose north (plan F8)

  return {cells, fixtures, tags};
}

// ---------------- offline checks ----------------
// porch clear height (m above the deck top 1.0) under the lowest roof / header cell over each porch row
function porchClear(cells, tags) {
  const out = {};
  for (let Z = MZ + 1; Z <= MZ + 3; Z++) {
    let low = 99;
    for (let k = 1; k <= 8; k++) {
      const kk = '3,' + k + ',' + Z;
      if (!['roof', 'header'].includes(tags.get(kk))) continue;
      low = Math.min(low, k + (cells.get(kk).includes('type=top') ? 0.5 : 0));
    }
    out['Z' + Z] = low - 1;
  }
  return out;
}
export function checks({cells, tags, fixtures}) {
  const get = (X, k, Z) => cells.get(X + ',' + k + ',' + Z) || null, out = [];
  const tagAt = (X, k, Z) => tags.get(X + ',' + k + ',' + Z) || null;
  const solid = s => s && !/pane|door|ladder|cable|fence|trapdoor|lantern|carpet|slab|stairs|beam|cauldron/.test(s);
  const inside = (X, k, Z) => (X >= 1 && X <= MX - 1 && Z >= 1 && Z <= MZ - 1 && k >= 1 && k <= 5) || (X >= 13 && X <= 16 && Z >= 1 && Z <= 6 && k >= 1 && k <= 3);
  for (const [kk, s] of cells) {
    const [X, k, Z] = kk.split(',').map(Number);
    if (s.includes('half=lower') && !(get(X, k + 1, Z) || '').includes('half=upper')) out.push('door without upper ' + kk);
    if (s.includes('ladder[facing=east') && k <= 3 && !solid(get(X - 1, k, Z))) out.push('ladder unsupported ' + kk);
    if (tagAt(X, k, Z) === 'roof' && inside(X, k, Z)) out.push('roof cell inside the house ' + kk);
  }
  // fixtures must land on air cells
  for (const f of fixtures) if (get(...f.at)) out.push('fixture on a cell ' + f.at);
  // headroom in metres from the attic floor (top 5.0) to the underside of the main roof, per Z over the attic
  const attic = {}; for (let Z = 1; Z <= MZ - 1; Z++) attic['Z' + Z] = +(undersideOf(mainRoofS(Z)) - 5).toFixed(2);
  // walking routes on the ground floor (3 clear layers, k 1..3): front door -> stove and kitchen door, to the bedroom,
  // storeroom and ladder doors, through the kitchen to the back and mudroom doors; fixtures count as blocked
  const fx = new Set(fixtures.map(f => f.at.join(',')));
  const walk = [[8, 9], [8, 8], [8, 7], [8, 6], [8, 5], [9, 5], [10, 5], [11, 5], [11, 4], [11, 3], [11, 2], [7, 2], [7, 3], [6, 2],
    [6, 5], [7, 5], [6, 8], [7, 8], [4, 5], [4, 8], [3, 5], [3, 8],
    [13, 2], [13, 3], [14, 3], [15, 3], [16, 2], [16, 1], [15, 4], [15, 5], [15, 6]];
  for (const [X, Z] of walk) for (let k = 1; k <= 3; k++) if (get(X, k, Z) || fx.has([X, k, Z].join(','))) out.push('walk cell blocked ' + [X, k, Z]);
  const pipeTop = [...cells].filter(([, s]) => s.includes('steel_cable')).map(([kk]) => Number(kk.split(',')[1]));
  return {issues: out, pipeTop: Math.max(...pipeTop) + 1, atticHeadroom: attic,
    porchClear: porchClear(cells, tags), ridge: mainRoofS(RIDGE_Z)};
}

// ---------------- batches: runs along X of one state, at most 128 ops per call ----------------
const plotOf = ([X, k, Z]) => [X + 1, k, Z + 5];
export function batches(cells) {
  const byRow = new Map();
  for (const [kk, s] of cells) { const [X, k, Z] = kk.split(',').map(Number), r = k + ',' + Z; (byRow.get(r) || byRow.set(r, []).get(r)).push([X, s]); }
  const ops = [];
  for (const [r, list] of [...byRow].sort((a, b) => { const [ka, za] = a[0].split(',').map(Number), [kb, zb] = b[0].split(',').map(Number); return ka - kb || za - zb; })) {
    const [k, Z] = r.split(',').map(Number); list.sort((a, b) => a[0] - b[0]);
    let i = 0; while (i < list.length) { let j = i; while (j + 1 < list.length && list[j + 1][0] === list[j][0] + 1 && list[j + 1][1] === list[i][1]) j++;
      ops.push({min: plotOf([list[i][0], k, Z]), max: plotOf([list[j][0], k, Z]), block: list[i][1]}); i = j + 1; }
  }
  // block entities go through place_fixture (fixtures mode); a stray one still gets its own batch so it cannot stop the shell
  const be = op => /_bed|chest|barrel|furnace|_sign/.test(op.block);
  const shell = ops.filter(op => !be(op)), extra = ops.filter(be), out = [];
  for (let i = 0; i < shell.length; i += 128) out.push({name: 'shell ' + (i / 128 + 1), ops: shell.slice(i, i + 128)});
  const kinds = new Map(); for (const op of extra) { const n = op.block.replace(/\[.*$/, ''); (kinds.get(n) || kinds.set(n, []).get(n)).push(op); }
  for (const [n, list] of kinds) out.push({name: n, ops: list});
  return out;
}

async function build() {
  const c = new BridgeClient('./run'), dir = path.resolve('build/authoring_checks', ID);
  const status = await c.call('minecraft_status'), info = await c.call('authoring_info');
  if (info.id !== ID || info.width !== SIZE[0] || info.height !== SIZE[1] || info.depth !== SIZE[2]) throw Error('PLOT_MISMATCH ' + JSON.stringify(info));
  const o = info.min, world = ([x, y, z]) => [o[0] + x, o[1] + y, o[2] + z];
  const {cells} = recipe(OPTS), report = {plot: info, world: status.world, porch: OPTS.porch, writes: [], failures: []};
  for (const b of batches(cells)) {
    try {
      const r = await c.call('we_batch_set', {target: 'AUTHORING_SESSION', operations: b.ops.map(op => ({min: world(op.min), max: world(op.max), block: op.block}))});
      report.writes.push({batch: b.name, ops: b.ops.length, changed: r.changed ?? r.blocks_changed ?? null});
    } catch (e) { report.failures.push({batch: b.name, ops: b.ops.length, error: String(e.message || e)}); }
  }
  const rec = await c.call('reconcile_shapes', {target: 'AUTHORING_SESSION'});   // fences / panes against their real neighbours
  report.reconciled = rec.reconciled ?? rec.changed_blocks ?? null;
  const audit = await c.call('audit_support', {target: 'AUTHORING_SESSION'});
  report.audit = {issue_count: audit.issue_count, counts: audit.counts, issues: audit.issues?.slice(0, 40)};
  await mkdir(dir, {recursive: true}); await writeFile(path.join(dir, 'build_m0.json'), JSON.stringify(report, null, 1));
  console.log(JSON.stringify({batches: report.writes.length, failures: report.failures, reconciled: report.reconciled, audit: report.audit.counts}, null, 1));
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
  await mkdir(dir, {recursive: true}); await writeFile(path.join(dir, 'fixtures_m0.json'), JSON.stringify(log, null, 1));
  console.log(JSON.stringify(log.map(l => (l.ok ? 'ok ' : 'FAIL ') + l.id.replace(/^.*:/, '') + ' ' + l.pos + (l.error ? ' ' + l.error : ''))));
}

// the cells that change between two porch variants: air where the old one had a block, the new state elsewhere
export function porchDiff(from, to) {
  const a = recipe({porch: from}).cells, b = recipe({porch: to}).cells, diff = new Map();
  for (const [kk, st] of a) if (!b.has(kk)) diff.set(kk, M('air'));
  for (const [kk, st] of b) if (a.get(kk) !== st) diff.set(kk, st);
  return diff;
}

async function switchPorch() {
  const c = new BridgeClient('./run'), dir = path.resolve('build/authoring_checks', ID);
  await c.call('minecraft_status'); const info = await c.call('authoring_info');
  if (info.id !== ID || info.width !== SIZE[0] || info.height !== SIZE[1] || info.depth !== SIZE[2]) throw Error('PLOT_MISMATCH ' + JSON.stringify(info));
  const o = info.min, world = ([x, y, z]) => [o[0] + x, o[1] + y, o[2] + z];
  const to = OPTS.porch, from = to === 'low' ? 'catslide' : 'low', diff = porchDiff(from, to), report = {from, to, cells: diff.size, writes: []};
  for (const b of batches(diff)) {
    const r = await c.call('we_batch_set', {target: 'AUTHORING_SESSION', operations: b.ops.map(op => ({min: world(op.min), max: world(op.max), block: op.block}))});
    report.writes.push({batch: b.name, ops: b.ops.length, changed: r.changed ?? r.blocks_changed ?? null});
  }
  await c.call('reconcile_shapes', {target: 'AUTHORING_SESSION'});
  await mkdir(dir, {recursive: true}); await writeFile(path.join(dir, 'switch_porch_' + to + '.json'), JSON.stringify(report, null, 1));
  console.log(JSON.stringify(report, null, 1));
}

async function plan() {
  const r = recipe(OPTS), b = batches(r.cells), dir = path.resolve('build/authoring_checks', ID);
  const list = [...r.cells].map(([kk, s]) => { const [X, k, Z] = kk.split(',').map(Number); return {X, k, Z, s, tag: r.tags.get(kk) || null}; });
  await mkdir(dir, {recursive: true}); await writeFile(path.join(dir, 'cells_m0_' + OPTS.porch + '.json'), JSON.stringify({size: SIZE, porch: OPTS.porch, cells: list, fixtures: r.fixtures}));
  console.log(JSON.stringify({porch: OPTS.porch, cells: r.cells.size, porchDiffCells: porchDiff('catslide', 'low').size, batches: b.map(x => x.name + ':' + x.ops.length), ops: b.reduce((n, x) => n + x.ops.length, 0), fixtures: r.fixtures.length, checks: checks(r)}, null, 1));
}

const direct = process.argv[1] && path.resolve(process.argv[1]).toLowerCase() === fileURLToPath(import.meta.url).toLowerCase();
const mode = direct ? process.argv[2] : null;
const porchArg = direct ? process.argv.find(a => a.startsWith('--porch=')) : null;
const OPTS = {porch: porchArg ? porchArg.slice(8) : 'low'};
if (mode === 'plan') await plan();
else if (mode === 'build') await build();
else if (mode === 'switch') await switchPorch();
else if (mode === 'fixtures') await placeFixtures();
else if (direct) console.log('modes: plan | build | switch --porch=low|catslide | fixtures');
