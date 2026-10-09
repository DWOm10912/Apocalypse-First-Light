// Fuel Stop A1 fuel court feeder (gas_station_02_feeder) through the AFL authoring bridge: live authoring only, no export.
// docs/worldgen/fuel_stop_a1_forecourt_build_v1.md ("供电"). 2026-10-08 (user): when the store's Service Meter Box is live,
// the fuel court works with it. The store's Distribution Panel feeds its top port (the "外接电缆" branch: main breaker and
// branch on, at most 512 FE/t) into one power cable that runs to the court's power header:
//   up from the panel top to the utility-room ceiling, along the ceiling by the room's west side (store X 7) to its
//   south-east corner, down that corner through the floor to k -2, east under the walk-in cooler to u 31, south under the
//   store, its sidewalk and the parking strip, down to k -4 at the court's north edge (v 38) and south between islands 2
//   and 3 into the header at v 49.
// Frame: the lot frame of fuel_stop_a1_forecourt.mjs (u east, v south, k metres above G; world x = -50 - u, y = -51 + k,
// z = 474 - v, so lot north = world south and lot east = world west). The store's paper frame is X = u - 18, Z = v - 8.
// The route crosses three plots: the store (v <= 26; also written by fuel_stop_a1_store.mjs power), the parking strip
// (v 27..37; only this script, until that plot is built) and the court (v >= 38; part of fuel_stop_a1_forecourt.mjs build).
// This script writes the whole route from one narrow plot that covers it, after checking that every route cell holds only
// ground, floor, air or an earlier copy of the route.
//   node tools/afl_minecraft_mcp/fuel_stop_a1_feeder.mjs plan  -> offline: cells per plot, link check against the court recipe, the resume command
//   node tools/afl_minecraft_mcp/fuel_stop_a1_feeder.mjs build -> checks and writes the route into the active feeder plot
import {BridgeClient} from './bridge_client.mjs';
import {mkdir, writeFile} from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

export const ID = 'gas_station_02_feeder';
export const toWorld = (u, k, v) => [-50 - u, -51 + k, 474 - v];
const A = id => 'apocalypse_firstlight:' + id;
const DIRS = {north: [0, 0, -1], south: [0, 0, 1], east: [1, 0, 0], west: [-1, 0, 0], up: [0, 1, 0], down: [0, -1, 0]};   // lot [u, k, v]
const WORLD_SIDE = {north: 'south', south: 'north', east: 'west', west: 'east', up: 'up', down: 'down'};

/** The Distribution Panel (store paper (8, 1, 1)) and the court header cell the route joins from the north. */
export const PANEL = [26, 1, 9];
export const JUNCTION = [31, -4, 49];
/** Plot spans along v (lot): the store module, the parking strip between, the fuel court. */
export const SPANS = {store: [7, 26], strip: [27, 37], court: [38, 59]};

/** Cells between corners, one axis at a time, without repeating a corner. */
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
export const ROUTE = trace(
  [26, 2, 9], [26, 3, 9],      // on the panel's top port, up to the ceiling
  [25, 3, 9], [25, 3, 12],     // west, then south along the ceiling by the room's west side (the linear light hangs at u 26, v 11)
  [26, 3, 12], [26, -2, 12],   // the room's south-east corner (partitions east and south), down through the floor
  [31, -2, 12],                // east under the walk-in cooler
  [31, -2, 38],                // south under the store, the sidewalk and the parking strip to the court's north edge
  [31, -4, 38], [31, -4, 48]); // down to the court's power layer, south between islands 2 and 3

/** Links of each route cell (lot sides): its neighbours on the route, the panel's top port (first), the header (last). */
export function routeLinks() {
  const side = (a, b) => Object.keys(DIRS).find(d => DIRS[d].every((x, i) => a[i] + x === b[i]));
  return ROUTE.map((c, i) => {
    const links = new Set();
    if (i > 0) links.add(side(c, ROUTE[i - 1])); else links.add(side(c, PANEL));
    if (i < ROUTE.length - 1) links.add(side(c, ROUTE[i + 1])); else links.add(side(c, JUNCTION));
    if ([...links].some(d => !d)) throw Error('ROUTE_GAP at ' + c);
    return {cell: c, links};
  });
}
const props = links => ['down', 'east', 'north', 'south', 'up', 'west'].map(d => d + '=' + links.has(d)).join(',');
/** Cable state with lot-frame sides (the court recipe's frame; it turns states when it writes). */
export const lotState = links => A(`power_cable[${props(links)}]`);
/** Cable state with world sides (what the bridge writes). */
export const worldState = links => A(`power_cable[${props(new Set([...links].map(d => WORLD_SIDE[d])))}]`);

/** The store part (paper frame X, k, Z with world-side states) for fuel_stop_a1_store.mjs powerSteps. */
export function storeCells() {
  return routeLinks().filter(({cell}) => cell[2] <= SPANS.store[1]).map(({cell: [u, k, v], links}) => [u - 18, k, v - 8, worldState(links)]);
}
/** The court part for fuel_stop_a1_forecourt.mjs: its cells, the cell entering from the strip and the strip cell north of it. */
export function courtFeed() {
  const cells = ROUTE.filter(c => c[2] >= SPANS.court[0]), entry = cells[0];
  return {cells, entry, outside: [entry[0], entry[1], entry[2] - 1]};
}

/** The plot covering the route (lot box) and its resume command (origin = the box's world minimum: lot u1 / k0 / v1). */
export const BOX = (() => {
  const all = [...ROUTE, JUNCTION], lo = j => Math.min(...all.map(c => c[j])), hi = j => Math.max(...all.map(c => c[j]));
  return {u0: lo(0), u1: hi(0), k0: lo(1), k1: hi(1), v0: lo(2), v1: hi(2)};
})();
export const SIZE = [BOX.u1 - BOX.u0 + 1, BOX.k1 - BOX.k0 + 1, BOX.v1 - BOX.v0 + 1];   // width (x), height, depth (z)
export const ORIGIN = toWorld(BOX.u1, BOX.k0, BOX.v1);
export const RESUME = `/afl_author resume ${ID} ${ORIGIN.join(' ')} ${SIZE[0]} ${SIZE[2]} ${SIZE[1]} ${-BOX.k0}`;

/** Offline: counts, and the court cells against the court recipe (same states, the header linked back). */
export async function plan() {
  const {recipe} = await import('./fuel_stop_a1_forecourt.mjs');
  const r = recipe(), mismatches = [], key = c => c.join(',');
  for (const {cell, links} of routeLinks().filter(({cell}) => cell[2] >= SPANS.court[0]))
    if (r.cells.get(key(cell)) !== lotState(links)) mismatches.push(key(cell) + ' feeder ' + lotState(links) + ' court ' + r.cells.get(key(cell)));
  const junction = r.cells.get(key(JUNCTION));
  if (!junction || !/north=true/.test(junction)) mismatches.push('junction ' + key(JUNCTION) + ' ' + junction);
  const per = name => ROUTE.filter(c => c[2] >= SPANS[name][0] && c[2] <= SPANS[name][1]).length;
  return {id: ID, cells: ROUTE.length, per_plot: {store: per('store'), strip: per('strip'), court: per('court')}, box: BOX, size: SIZE, origin: ORIGIN,
    resume: RESUME, court_recipe: {mismatches, dangling: r.dangling, conflicts: r.conflicts}};
}

// cells the route may replace: air, ground, the store floor / court slab placeholders, or an earlier copy of the route
const REPLACEABLE = new Set(['minecraft:air', 'minecraft:cave_air', 'minecraft:dirt', 'minecraft:coarse_dirt', 'minecraft:grass_block', 'minecraft:stone',
  'minecraft:gravel', 'minecraft:sand', 'minecraft:light_gray_concrete', A('power_cable')]);

async function build() {
  const p = await plan();
  if (p.court_recipe.mismatches.length || p.court_recipe.dangling.length || p.court_recipe.conflicts.length) throw Error('RECIPE ' + JSON.stringify(p.court_recipe));
  const c = new BridgeClient('./run'), dir = path.resolve('build/authoring_checks', ID);
  const status = await c.call('minecraft_status'), info = await c.call('authoring_info');
  if (info.id !== ID || info.width !== SIZE[0] || info.height !== SIZE[1] || info.depth !== SIZE[2] || info.min.some((x, i) => x !== ORIGIN[i]))
    throw Error('PLOT_MISMATCH ' + JSON.stringify(info) + ' run ' + RESUME);
  const [px, py, pz] = (await c.call('get_player_state')).block;
  const onRoute = ROUTE.some(cell => { const [x, y, z] = toWorld(...cell); return x === px && z === pz && (y === py || y === py + 1); });
  if (onRoute) throw Error('PLAYER_ON_ROUTE: step off ' + [px, py, pz]);
  // what is there now: one X section per route column (rows y descending from the box top, columns z ascending)
  const bid = s => (/^Block\{([^}]+)\}/.exec(s) || [])[1] || s, found = new Map();
  for (const x of new Set(ROUTE.map(cell => toWorld(...cell)[0]))) {
    const sl = await c.call('get_vertical_slice', {target: 'AUTHORING_SESSION', axis: 'X', coordinate: x, encoding: 'palette'});
    const names = Object.fromEntries(Object.entries(sl.palette).map(([s, i]) => [i, s]));
    sl.rows.forEach((row, r) => row.forEach((i, col) => found.set([x, info.max[1] - r, info.min[2] + col].join(','), names[i])));
  }
  const at = cell => found.get(toWorld(...cell).join(','));
  const blocked = ROUTE.filter(cell => !REPLACEABLE.has(bid(at(cell)))).map(cell => cell.join(',') + ' ' + at(cell));
  if (bid(at(JUNCTION)) !== A('power_cable')) blocked.push('junction ' + JUNCTION.join(',') + ' ' + at(JUNCTION));
  if (blocked.length) throw Error('ROUTE_BLOCKED ' + JSON.stringify(blocked));
  // the route and the header cell it joins (east, west and north), exact states with world sides
  const {recipe} = await import('./fuel_stop_a1_forecourt.mjs');
  const junctionLinks = new Set(/\[([^\]]*)\]/.exec(recipe().cells.get(JUNCTION.join(',')))[1].split(',').filter(kv => kv.endsWith('=true')).map(kv => kv.split('=')[0]));
  const ops = [...routeLinks().map(({cell, links}) => [cell, worldState(links)]), [JUNCTION, worldState(junctionLinks)]]
    .map(([cell, block]) => { const w = toWorld(...cell); return {min: w, max: w, block}; });
  const write = await c.call('we_batch_set', {target: 'AUTHORING_SESSION', operations: ops});
  const audit = await c.call('audit_support', {target: 'AUTHORING_SESSION'}).catch(e => ({error: String(e.message || e)}));
  const report = {plot: info, world: status.world, before: Object.fromEntries(ROUTE.map(cell => [cell.join(','), bid(at(cell))])), ops: ops.length, write, audit};
  await mkdir(dir, {recursive: true}); await writeFile(path.join(dir, 'feeder_v1.json'), JSON.stringify(report, null, 1));
  const replaced = {}; for (const cell of ROUTE) { const b = bid(at(cell)); replaced[b] = (replaced[b] || 0) + 1; }
  console.log(JSON.stringify({ops: ops.length, replaced, audit: audit.counts ?? audit, issues: audit.issues?.slice(0, 12)}, null, 1));
}

const direct = process.argv[1] && path.resolve(process.argv[1]).toLowerCase() === fileURLToPath(import.meta.url).toLowerCase();
const mode = direct ? process.argv[2] : null;
// no top-level await: plan() imports the court script, which imports this module back
const fail = e => { console.error(e); process.exitCode = 1; };
if (mode === 'plan') plan().then(p => console.log(JSON.stringify(p, null, 1)), fail);
else if (mode === 'build') build().catch(fail);
