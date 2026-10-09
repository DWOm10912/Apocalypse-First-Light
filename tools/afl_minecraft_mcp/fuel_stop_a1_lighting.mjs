// Fuel Stop A1 site lighting (gas_station_02_lighting) through the AFL authoring bridge: live authoring only, no export.
// docs/models/site_lighting_v1.md, docs/worldgen/fuel_stop_a1_site_build_v1.md ("照明"). 2026-10-09 (user: "镀锌灰，其他的按照你
// 说的做 / 不过模型要做的精致一点"): the approved plan, built from Site Lighting V1 blocks.
//   16 poles  light_pole_base at k 0 (the hand hole facing the lot), light_pole k 1..7, area_light at k 8 (about 8 m up);
//             the outer ring single heads facing into the lot, the poles between the parking rows and in the green areas twin
//   cables    an underground tree at k -2 fed from the store feeder (u 31, k -2, v 12..38; fuel_stop_a1_feeder.mjs): a
//             crossbar along v 29 (through the feeder, under the two parking-row end poles) to a west spine (u 1) and an east
//             spine (u 62), the north row (v 0) and the south row (v 61) off the west spine, short branches to the green-area
//             poles; under each base a riser cell at k -1 (the base's full-cell pad hides it); the front pole (u 31, v 25)
//             rises straight off the feeder. No loops. The two north-edge poles stand on the curb row
//             (v 0 is all curb): no riser, the k -2 cable links up under the curb (CURB_POLES).
//   7 wall packs  on the store's outside walls at k 3: west and east walls (u 17 / u 45 at v 13 and 17, on brick: the west
//             wall has windows at v 9..12, the side eyebrows take v 19..22) and the back wall (v 7 at u 24, 31, 38)
//   6 canopy downlights  under the storefront eyebrow (k 3, v 24) in the k 2 cells at u 21, 25, 29, 33, 37, 41
// Lot frame as in fuel_stop_a1_site.mjs: u east, v south, k metres above G; world x = -50 - u, y = -51 + k, z = 474 - v, so
// lot north = world south and lot east = world west. The plot covers the whole lot from k -2 to k 8.
// Before writing, every cell it writes is read: cables only into ground or an earlier cable, bases only on ground with air
// (or plants) above, poles and heads only into air, the cells the heads hang in must be air, wall packs only into air in
// front of a wall, downlights only into air under an eyebrow canopy. Any other block stops the script.
//   node tools/afl_minecraft_mcp/fuel_stop_a1_lighting.mjs plan  -> offline: counts, the cable tree check, the resume command
//   node tools/afl_minecraft_mcp/fuel_stop_a1_lighting.mjs build -> checks and writes into the active lighting plot
import {BridgeClient} from './bridge_client.mjs';
import {mkdir, writeFile} from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {routeLinks as feederLinks} from './fuel_stop_a1_feeder.mjs';

export const ID = 'gas_station_02_lighting';
export const LOT = {u0: 0, u1: 63, k0: -2, k1: 8, v0: 0, v1: 63};
export const SIZE = [LOT.u1 - LOT.u0 + 1, LOT.k1 - LOT.k0 + 1, LOT.v1 - LOT.v0 + 1];   // width (x), height, depth (z)
export const toWorld = (u, k, v) => [-50 - u, -51 + k, 474 - v];
export const ORIGIN = toWorld(LOT.u1, LOT.k0, LOT.v1);
export const RESUME = `/afl_author resume ${ID} ${ORIGIN.join(' ')} ${SIZE[0]} ${SIZE[2]} ${SIZE[1]} ${-LOT.k0}`;
const A = id => 'apocalypse_firstlight:' + id;
const TURN = {north: 'south', south: 'north', east: 'west', west: 'east', up: 'up', down: 'down'};   // lot side -> world side
const STEP = {north: [0, 0, -1], south: [0, 0, 1], east: [1, 0, 0], west: [-1, 0, 0], up: [0, 1, 0], down: [0, -1, 0]};   // lot [u, k, v]
const CW = {north: 'east', east: 'south', south: 'west', west: 'north'};   // the same quarter turn in lot and world (a half-turn frame)
const HEAD_TURNS = {single: [0], twin: [0, 2]};

/** Poles: lot u, v, the first head's lot direction (into the lot), the head layout. */
export const POLES = [
  [1, 10, 'east', 'single'], [1, 26, 'east', 'single'], [1, 56, 'east', 'single'],           // west edge
  [62, 10, 'west', 'single'], [62, 26, 'west', 'single'], [62, 56, 'west', 'single'],        // east edge
  [22, 0, 'south', 'single'], [42, 0, 'south', 'single'],                                    // north edge, behind the store
  [18, 61, 'north', 'single'], [31, 61, 'north', 'single'], [46, 61, 'north', 'single'],     // south edge, by the main road
  [6, 42, 'north', 'twin'], [57, 42, 'north', 'twin'],                                       // the green areas beside the canopy
  [18, 29, 'east', 'twin'], [45, 29, 'east', 'twin'],                                        // the parking-row ends in front of the store
  [31, 25, 'south', 'single']];                                                              // the storefront sidewalk, between the doors
export const POLE_TOP = 8;
/**
 * Poles standing on a curb cell (the lot's north edge, v 0, is all curb_grass: grass beside the service drive). The plan
 * allows a base on a curb (the pier in the middle, the 15 cm lip at the cell's road edge runs past the pad); the riser cell
 * would be the curb itself, so it stays and the k -2 cable links up under it: the cable passes under the curb into the
 * base's bottom port (energy/PowerCableTransfer, curb pass-through, 2026-10-09).
 */
export const CURB_POLES = new Set(['22,0', '42,0']);
/**
 * Wall packs: lot u, k, v, facing (lot, away from the wall). On the side walls the solid stretch is v 13..18 (the west wall has
 * a window strip at v 9..12 and both have the corner glass and side eyebrows at v 19..22): the charcoal pier at v 13 and the
 * brick at v 17, the same on both sides (2026-10-09: v 12 on the west wall was glass, its wall pack unsupported).
 */
export const WALL_PACKS = [[17, 3, 13, 'west'], [17, 3, 17, 'west'], [45, 3, 13, 'east'], [45, 3, 17, 'east'],
  [24, 3, 7, 'north'], [31, 3, 7, 'north'], [38, 3, 7, 'north']];
/** Canopy downlights: lot u, k, v (under the eyebrow canopy at k + 1). */
export const DOWNLIGHTS = [21, 25, 29, 33, 37, 41].map(u => [u, 2, 24]);

const key = c => c.join(',');
/** Cells between corners, one axis at a time (as fuel_stop_a1_feeder.mjs). */
function trace(...corners) {
  const out = [corners[0]];
  for (let i = 1; i < corners.length; i++) {
    const a = corners[i - 1], b = corners[i], axes = [0, 1, 2].filter(j => a[j] !== b[j]);
    if (axes.length !== 1) throw Error('TRACE ' + a + ' -> ' + b);
    const j = axes[0], s = Math.sign(b[j] - a[j]);
    for (let t = a[j] + s; t !== b[j] + s; t += s) { const c = [...a]; c[j] = t; out.push(c); }
  }
  return out;
}
const side = (a, b) => Object.keys(STEP).find(d => STEP[d].every((x, i) => a[i] + x === b[i]));

/** The cable tree: lot cell -> set of lot sides (feeder cells it joins keep their own links and gain the new ones). */
export function cableTree() {
  const links = new Map(), cells = new Map();
  const link = (a, b) => { const s = side(a, b); if (!s) throw Error('NOT_ADJACENT ' + a + ' / ' + b);
    for (const [c, d] of [[a, s], [b, side(b, a)]]) { cells.set(key(c), c); (links.get(key(c)) || links.set(key(c), new Set()).get(key(c))).add(d); } };
  const run = (...corners) => { const t = trace(...corners); for (let i = 1; i < t.length; i++) link(t[i - 1], t[i]); };
  run([1, -2, 29], [62, -2, 29]);                   // crossbar, through the feeder at u 31
  run([1, -2, 0], [1, -2, 61]);                     // west spine
  run([1, -2, 0], [42, -2, 0]);                     // north row
  run([1, -2, 61], [46, -2, 61]);                   // south row
  run([1, -2, 42], [6, -2, 42]);                    // west green pole
  run([62, -2, 10], [62, -2, 56]);                  // east spine
  run([62, -2, 42], [57, -2, 42]);                  // east green pole
  for (const [u, v] of POLES) {   // risers; up = the base's port (through the curb for a pole on a curb)
    if (CURB_POLES.has(u + ',' + v)) { links.get(key([u, -2, v])).add('up'); continue; }
    link([u, -2, v], [u, -1, v]); cells.set(key([u, -1, v]), [u, -1, v]); links.get(key([u, -1, v])).add('up');
  }
  // the feeder cells the tree joins keep their links
  const feeder = new Map(feederLinks().map(({cell, links: l}) => [key(cell), l]));
  for (const [k, l] of links) if (feeder.has(k)) for (const d of feeder.get(k)) l.add(d);
  return {cells, links, joins: [...links.keys()].filter(k => feeder.has(k))};
}
const props = l => ['down', 'east', 'north', 'south', 'up', 'west'].map(d => d + '=' + l.has(d)).join(',');
const cableState = l => A(`power_cable[${props(new Set([...l].map(d => TURN[d])))}]`);

/** Everything written: cells (lot key -> world state), the bases to place, the cells the heads hang in. */
export function recipe() {
  const t = cableTree(), cells = new Map(), bases = [], hang = [], problems = [];
  const put = (c, s) => { if (cells.has(key(c)) && cells.get(key(c)) !== s) problems.push('OVERLAP ' + key(c)); cells.set(key(c), s); };
  for (const [k, c] of t.cells) put(c, cableState(t.links.get(k)));
  // every cable link has its partner (both ends agree), and no link points into a cell that is neither cable nor base
  const baseCells = new Set(POLES.map(([u, v]) => key([u, 0, v]))), curbCells = new Set([...CURB_POLES].map(k => { const [u, v] = k.split(','); return key([+u, -1, +v]); }));
  for (const [k, l] of t.links) for (const d of l) {
    const c = t.cells.get(k), n = c.map((x, i) => x + STEP[d][i]), back = Object.keys(STEP).find(e => STEP[e].every((x, i) => x === -STEP[d][i]));
    if ((baseCells.has(key(n)) || curbCells.has(key(n))) && d === 'up') continue;
    const feederEnd = !t.links.has(key(n));   // a feeder cell outside the tree (its own route continues)
    if (!feederEnd && !t.links.get(key(n)).has(back)) problems.push('DANGLING ' + k + ' ' + d);
    if (feederEnd && !t.joins.includes(k)) problems.push('OPEN_END ' + k + ' ' + d);
  }
  for (const [u, v, f, heads] of POLES) {
    bases.push({at: [u, 0, v], facing: TURN[f]});
    put([u, 1, v], A(`light_pole[handhole=${TURN[f]}]`));
    for (let k = 2; k < POLE_TOP; k++) put([u, k, v], A('light_pole[handhole=none]'));
    put([u, POLE_TOP, v], A(`area_light[facing=${TURN[f]},heads=${heads},lit=false]`));
    for (const turn of HEAD_TURNS[heads]) { let d = f; for (let i = 0; i < turn; i++) d = CW[d]; hang.push([u + STEP[d][0], POLE_TOP, v + STEP[d][2]]); }
  }
  for (const [u, k, v, f] of WALL_PACKS) put([u, k, v], A(`wall_pack[facing=${TURN[f]},lit=false]`));
  for (const c of DOWNLIGHTS) put(c, A('canopy_downlight[lit=false]'));
  return {cells, bases, hang, problems, cables: t.cells.size, joins: t.joins};
}

export function plan() {
  const r = recipe(), count = {};
  for (const s of r.cells.values()) { const id = s.replace(/\[.*$/, '').replace('apocalypse_firstlight:', ''); count[id] = (count[id] || 0) + 1; }
  return {id: ID, size: SIZE, origin: ORIGIN, resume: RESUME, poles: POLES.length, heads: POLES.reduce((n, p) => n + HEAD_TURNS[p[3]].length, 0),
    cells: count, bases: r.bases.length, feeder_joins: r.joins, problems: r.problems};
}

// what a cell may hold before it is written
const GROUND = new Set(['minecraft:dirt', 'minecraft:coarse_dirt', 'minecraft:grass_block', 'minecraft:stone', 'minecraft:gravel', 'minecraft:sand',
  'minecraft:air', 'minecraft:cave_air', A('asphalt'), A('concrete_pavement'), A('concrete_sidewalk'), A('power_cable')]);
const PLANTS = /^minecraft:(grass|short_grass|tall_grass|fern|large_fern|dandelion|poppy|azure_bluet|oxeye_daisy|cornflower|blue_orchid|allium|.*_tulip|lily_of_the_valley|dead_bush)$/;
const OURS = new Set(['minecraft:air', 'minecraft:cave_air', A('lamp_glow'), A('light_pole'), A('area_light'), A('wall_pack'), A('canopy_downlight')]);

async function build() {
  const r = recipe();
  if (r.problems.length) throw Error('RECIPE ' + JSON.stringify(r.problems));
  const c = new BridgeClient('./run'), dir = path.resolve('build/authoring_checks', ID);
  const status = await c.call('minecraft_status'), info = await c.call('authoring_info');
  if (info.id !== ID || info.width !== SIZE[0] || info.height !== SIZE[1] || info.depth !== SIZE[2] || info.min.some((x, i) => x !== ORIGIN[i]))
    throw Error('PLOT_MISMATCH ' + JSON.stringify(info) + ' run ' + RESUME);
  const o = info.min;
  // the plot, layer by layer (rows z ascending, columns x ascending) -> lot key "u,k,v" -> state
  const at = new Map();
  const read = async () => { at.clear(); for (let k = LOT.k0; k <= LOT.k1; k++) {
    const sl = await c.call('get_horizontal_slice', {target: 'AUTHORING_SESSION', coordinate: toWorld(0, k, 0)[1], encoding: 'palette'});
    const names = Object.fromEntries(Object.entries(sl.palette).map(([s, i]) => [i, s]));
    sl.rows.forEach((row, z) => row.forEach((i, x) => at.set(key([-50 - (o[0] + x), k, 474 - (o[2] + z)]), names[i])));
  } };
  await read();
  const id = s => (/^Block\{([^}]+)\}/.exec(s || '') || [])[1] || (s || '').replace(/\[.*$/, '');
  const here = cell => id(at.get(key(cell)));
  const player = (await c.call('get_player_state')).block, pu = -50 - player[0], pv = 474 - player[2], pk = player[1] + 51;
  const blocked = [], plants = new Map();
  for (const [k, s] of r.cells) {
    const cell = k.split(',').map(Number), now = here(cell);
    if (s.includes('power_cable')) { if (!GROUND.has(now)) blocked.push(k + ' cable ' + now); }
    else if (!OURS.has(now)) blocked.push(k + ' ' + now);
  }
  for (const {at: [u, , v]} of r.bases) {
    const now = here([u, 0, v]);
    if (CURB_POLES.has(u + ',' + v) && !/^apocalypse_firstlight:curb_/.test(here([u, -1, v]))) blocked.push(key([u, -1, v]) + ' expected a curb under the base: ' + here([u, -1, v]));
    if (PLANTS.test(now)) plants.set(key([u, 0, v]), 'minecraft:air');
    else if (now !== 'minecraft:air' && now !== A('light_pole_base')) blocked.push(key([u, 0, v]) + ' base ' + now);
    if (pu === u && pv === v && pk >= -1 && pk <= POLE_TOP) blocked.push('PLAYER_IN_POLE ' + key([u, 0, v]));
  }
  for (const cell of r.hang) if (!OURS.has(here(cell))) blocked.push(key(cell) + ' head space ' + here(cell));
  const WALL = /^apocalypse_firstlight:(face_brick_|metal_wall_panel|ground_face_block|reinforced_concrete)/;
  for (const [u, k, v, f] of WALL_PACKS) { const w = [u - STEP[f][0], k, v - STEP[f][2]], now = here(w); if (!WALL.test(now)) blocked.push(key(w) + ' no masonry wall behind the wall pack: ' + now); }
  // wall packs and downlights of an earlier layout that this one no longer has
  const stale = new Map([...at].filter(([k, st]) => /^Block\{apocalypse_firstlight:(wall_pack|canopy_downlight)\}/.test(st || '') && !r.cells.has(k)).map(([k]) => [k, 'minecraft:air']));
  for (const [u, k, v] of DOWNLIGHTS) { const above = here([u, k + 1, v]); if (above !== A('metal_eyebrow_canopy')) blocked.push(key([u, k + 1, v]) + ' no eyebrow over the downlight: ' + above); }
  if (blocked.length) throw Error('LIGHTING_BLOCKED ' + JSON.stringify(blocked.slice(0, 40)) + (blocked.length > 40 ? ' +' + (blocked.length - 40) : ''));

  const report = {plot: info, world: status.world, plan: plan(), steps: [], failures: []};
  const step = async (label, tool, args) => {
    try { const res = await c.call(tool, args); report.steps.push(label + ' ok'); return res; }
    catch (e) { const msg = String(e.message || e); report.failures.push({label, error: msg}); report.steps.push(label + ' FAIL ' + msg); return null; }
  };
  const ops = cells => [...cells].map(([k, block]) => { const w = toWorld(...k.split(',').map(Number)); return {min: w, max: w, block}; });
  const chunks = (list, n) => Array.from({length: Math.ceil(list.length / n)}, (_, i) => list.slice(i * n, i * n + n));
  // 1. plants off the base cells; 2. cables (k -2 tree, risers, the two feeder joins); 3. bases; 4. poles, heads, fixtures
  if (plants.size) await step('plants', 'we_batch_set', {target: 'AUTHORING_SESSION', operations: ops(plants)});
  if (stale.size) await step('stale_fixtures', 'we_batch_set', {target: 'AUTHORING_SESSION', operations: ops(stale)});
  const cables = [...r.cells].filter(([, s]) => s.includes('power_cable')), rest = [...r.cells].filter(([, s]) => !s.includes('power_cable'));
  for (const [i, part] of chunks(cables, 120).entries()) await step('cables_' + i, 'we_batch_set', {target: 'AUTHORING_SESSION', operations: ops(part)});
  for (const b of r.bases) if (here(b.at) !== A('light_pole_base'))
    await step('light_pole_base @' + key(b.at), 'place_fixture', {block_id: A('light_pole_base'), pos: toWorld(...b.at), facing: b.facing, replace_policy: 'REPLACEABLE'});
  for (const [i, part] of chunks(rest, 120).entries()) await step('fixtures_' + i, 'we_batch_set', {target: 'AUTHORING_SESSION', operations: ops(part)});
  // read back: every written cell holds its state (properties in any order)
  await read();
  const norm = s => { const m = /^(?:Block\{([^}]+)\})?([^\[]*)(?:\[(.*)\])?$/.exec(s || ''); return (m[1] || m[2]) + (m[3] ? '[' + m[3].split(',').sort().join(',') + ']' : ''); };
  const wrong = [...r.cells].filter(([k, s]) => norm(at.get(k)) !== norm(s)).map(([k]) => k + ' ' + at.get(k));
  for (const b of r.bases) if (here(b.at) !== A('light_pole_base')) wrong.push(key(b.at) + ' base ' + at.get(key(b.at)));
  const audit = await c.call('audit_support', {target: 'AUTHORING_SESSION'}).catch(e => ({error: String(e.message || e)}));
  Object.assign(report, {readback: {wrong}, audit});
  await mkdir(dir, {recursive: true}); await writeFile(path.join(dir, 'site_lighting_v1.json'), JSON.stringify(report, null, 1));
  console.log(JSON.stringify({cells: r.cells.size, bases: r.bases.length, plants: plants.size, stale: [...stale.keys()], failures: report.failures, wrong: wrong.slice(0, 12),
    audit: audit.counts ?? audit, issues: audit.issues?.slice(0, 12)}, null, 1));
}

const direct = process.argv[1] && path.resolve(process.argv[1]).toLowerCase() === fileURLToPath(import.meta.url).toLowerCase();
const mode = direct ? process.argv[2] : null;
const fail = e => { console.error(e); process.exitCode = 1; };
if (mode === 'plan') console.log(JSON.stringify(plan(), null, 1));
else if (mode === 'build') build().catch(fail);
