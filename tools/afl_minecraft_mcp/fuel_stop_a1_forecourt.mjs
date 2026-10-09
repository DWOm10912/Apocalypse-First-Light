// Fuel Stop A1 fuel court (gas_station_02_forecourt) through the AFL authoring bridge: live authoring only, no export.
// docs/worldgen/fuel_stop_a1_forecourt_build_v1.md; layout from docs/worldgen/fuel_stop_a1_design_v1.md ("总平面"):
// canopy x 12..50, z 40..50 (5 m soffit); four islands at x 16, 26, 36, 46 (end, dispenser, column, dispenser, end over
// z 42..48, the column at z 45); three 3 x 3 x 7 tanks along z at x 24, 30, 36 over z 52..58 (gasoline, gasoline, diesel),
// pump manway mid-tank (z 55), fill at the south end (z 58).
// Frame ("lot"): u east = design x, v south = design z, k = metres above G (k -1 is the ground's surface layer). The A1
// lot is turned 180 degrees in the dev world (the store module was normalised to the NORTH template frame):
//   world x = -50 - u, y = -51 + k, z = 474 - v; directions north <-> south, east <-> west.
// Layers under the court (one cell each):
//   k -1  surface: concrete, the fill / manhole covers, the dispenser sumps' upper cells, the canopy feed under island 1's column
//   k -2  the sumps' lower cells (their ports), the pumps on the tank ports, the pump outlets, fill risers, port stubs
//   k -3  gasoline header (z 50) and branches          (tank tops are k -3 under z 52..58)
//   k -4  power: header (z 49) and branches            (fed from the store's Distribution Panel through the feeder,
//         fuel_stop_a1_feeder.mjs, joining at x 31; a cable stub at the header's west end for a test energy cell)
//   k -5  diesel header (z 51) and branches            (tank bottoms)
// Every port of a sump faces k -2, and both sumps of an island have one port toward the column's cell (z 45): that cell
// is a pipe, so the canopy (powered only through a column base's bottom face) can be fed only through an island whose
// inner ports stay unused. Island 1 (x 16) is gasoline only and feeds the canopy; islands 2-4 have gasoline and diesel.
// Pipes and cables are written with exact links (run members and ports only); runs never share a cell.
//   node tools/afl_minecraft_mcp/fuel_stop_a1_forecourt.mjs plan   -> offline: counts, cell conflicts, the resume command
//   node tools/afl_minecraft_mcp/fuel_stop_a1_forecourt.mjs build  -> writes the whole court into the active plot
//   node tools/afl_minecraft_mcp/fuel_stop_a1_forecourt.mjs canopy -> rewrites only the canopy layer (k 5): lights, ceiling, fascia
//   node tools/afl_minecraft_mcp/fuel_stop_a1_forecourt.mjs audit  -> audit_support of the plot
//   node tools/afl_minecraft_mcp/fuel_stop_a1_forecourt.mjs pavement -> the built court's placeholder slab to concrete_pavement (we_replace, k -1)
//   node tools/afl_minecraft_mcp/fuel_stop_a1_forecourt.mjs pit    -> the built court's tank pit: the deadmen, and the backfill once it is not dirt (see PIT)
import {BridgeClient} from './bridge_client.mjs';
import {mkdir, writeFile} from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {courtFeed} from './fuel_stop_a1_feeder.mjs';

export const ID = 'gas_station_02_forecourt';
/** Plot extents (lot frame, inclusive) and size [width (u), height (k), depth (v)]. */
export const LOT = {u0: 10, u1: 52, k0: -5, k1: 5, v0: 38, v1: 59};
export const SIZE = [LOT.u1 - LOT.u0 + 1, LOT.k1 - LOT.k0 + 1, LOT.v1 - LOT.v0 + 1];
export const toWorld = (u, k, v) => [-50 - u, -51 + k, 474 - v];
/** The plot's minimum corner in the world (the lot's u1 / v1 corner, it is turned). */
export const ORIGIN = toWorld(LOT.u1, LOT.k0, LOT.v1);
export const RESUME = `/afl_author resume ${ID} ${ORIGIN.join(' ')} ${SIZE[0]} ${SIZE[2]} ${SIZE[1]} ${-LOT.k0}`;
const A = id => 'apocalypse_firstlight:' + id, M = id => 'minecraft:' + id;
const TURN = {north: 'south', south: 'north', east: 'west', west: 'east', up: 'up', down: 'down'};
const DIRS = {north: [0, 0, -1], south: [0, 0, 1], east: [1, 0, 0], west: [-1, 0, 0], up: [0, 1, 0], down: [0, -1, 0]};   // lot [u, k, v]

export const ISLANDS = [16, 26, 36, 46];
export const POWER_ISLAND = 16;
export const TANKS = [[24, 'gasoline'], [30, 'gasoline'], [36, 'diesel']];
export const CANOPY = {u0: 12, u1: 50, v0: 40, v1: 50, k: 5};
const LIGHT_U = new Set(ISLANDS.flatMap(u => [u - 3, u + 3]));
// the court slab: concrete pavement (Ground Materials V1, 2026-10-08; saw cuts every 4 m by world position), the cars run
// along the islands (z); the backfill is a placeholder
const GROUND = A('concrete_pavement[axis=z]'), SOIL = M('dirt'), OLD_GROUND = M('light_gray_concrete');
const TANK_V = 55, FILL_V = 58;                               // tank master (along 3) and fill (along 6) rows
// The tank pit (2026-10-08, user asked whether the tanks should be cased in concrete): North American practice is not a
// concrete case but an excavation backfilled with pea gravel or crushed stone, reinforced-concrete deadmen beside the tank
// bottoms (strapped over the tanks against groundwater uplift) and a reinforced-concrete slab on top (the court pavement).
// Gravel fills the pit k -5..-2; a deadman runs 1 m out from each side of every tank at its bottom (k -5), where no line is.
export const PIT = {u0: 22, u1: 38, k0: -5, k1: -2, v0: 51, v1: 59};
// Backfill: gravel is a falling block, which the bridge refuses (AuthoringRegionGuard), so the pit keeps the placeholder dirt
// until a non-falling crushed-stone block exists (2026-10-08, the user to decide).
const BACKFILL = M('dirt'), DEADMAN = A('reinforced_concrete');

/** The two dispensers of an island: [a0 row, facing]. The b column is at facing.getClockWise(). */
export function dispensers(u) {
  return u === POWER_ISLAND ? [[43, 'east'], [47, 'west']] : [[44, 'west'], [46, 'east']];
}

export function recipe() {
  const key = (u, k, v) => u + ',' + k + ',' + v;
  const used = new Map(), conflicts = [];
  const claim = (u, k, v, what) => { const kk = key(u, k, v); if (used.has(kk)) conflicts.push(kk + ' ' + used.get(kk) + ' / ' + what); else used.set(kk, what); };
  const fixtures = [], cells = new Map(), voids = [];
  const fix = (tool, id, at, facing, properties = {}, cellsOf = [at]) => { fixtures.push({tool, id, at, facing, properties}); for (const c of cellsOf) claim(...c, id); };
  const put = (u, k, v, state, what = state) => { claim(u, k, v, what); cells.set(key(u, k, v), state); };

  // ---- tanks, pumps, covers, fill risers ----
  for (const [t, fuel] of TANKS) {
    const tankCells = [];
    for (let u = t - 1; u <= t + 1; u++) for (let k = -5; k <= -3; k++) for (let v = 52; v <= 58; v++) tankCells.push([u, k, v]);
    voids.push([[t - 1, -5, 52], [t + 1, -3, 58]]);
    fix('place_multiblock', 'underground_fuel_tank_' + fuel, [t, -5, TANK_V], 'south', {}, tankCells);   // anchor = bottom centre, fill end south
    voids.push([[t, -2, TANK_V], [t, -1, TANK_V]], [[t, -1, FILL_V], [t, -1, FILL_V]]);
    fix('place_fixture', 'submersible_fuel_pump', [t, -2, TANK_V], 'north');                            // outlet north (to the court), power port south
    fix('place_fixture', 'pump_manhole_cover', [t, -1, TANK_V], 'north');
    fix('place_fixture', 'fuel_fill_cover_' + fuel, [t, -1, FILL_V], 'north');
  }
  // ---- islands: ends, dispensers over their sumps, the column, bollards ----
  for (const u of ISLANDS) {
    put(u, 0, 42, A('fuel_island_end[facing=west]'));   // joining side south, round side north
    put(u, 0, 48, A('fuel_island_end[facing=east]'));
    for (const [a0, f] of dispensers(u)) {
      const b = a0 + (f === 'east' ? 1 : -1);
      voids.push([[u, -2, Math.min(a0, b)], [u, -1, Math.max(a0, b)]]);
      fix('place_multiblock', 'fuel_dispenser_sump', [u, -2, a0], f, {}, [[u, -2, a0], [u, -2, b], [u, -1, a0], [u, -1, b]]);
      const d = []; for (let k = 0; k <= 2; k++) d.push([u, k, a0], [u, k, b]);
      fix('place_multiblock', 'fuel_dispenser', [u, 0, a0], f, {}, d);
    }
  }
  // ---- canopy (k 5): fascia round the edge (corners outer), ceiling inside; lights over the fuelling positions, 3 m either
  // side of each island in rows z 42 / 45 / 48 (24; the first build's 57, every other cell, were too dense: user 2026-10-08) ----
  const {u0, u1, v0, v1, k: ck} = CANOPY;
  const fascia = (f, shape) => A(`fuel_canopy_fascia[facing=${f},shape=${shape}]`);
  for (let u = u0; u <= u1; u++) for (let v = v0; v <= v1; v++) {
    let s;
    if (v === v0) s = fascia('north', u === u0 ? 'outer_left' : u === u1 ? 'outer_right' : 'straight');
    else if (v === v1) s = fascia('south', u === u0 ? 'outer_right' : u === u1 ? 'outer_left' : 'straight');
    else if (u === u0) s = fascia('west', 'straight');
    else if (u === u1) s = fascia('east', 'straight');
    else s = [42, 45, 48].includes(v) && LIGHT_U.has(u) ? A('fuel_canopy_light[lit=false]') : A('fuel_canopy_ceiling');
    put(u, ck, v, s, 'canopy');
  }
  // columns after the canopy (their head plate follows the canopy piece above); bollards after the ends
  const columns = [];
  for (const u of ISLANDS) {
    columns.push({tool: 'place_fixture', id: 'fuel_canopy_column', at: [u, 0, 45], facing: 'east', properties: {island: 'true'}});
    for (let k = 1; k <= 4; k++) columns.push({tool: 'place_fixture', id: 'fuel_canopy_column', at: [u, k, 45], facing: 'east', properties: {}});
    for (let k = 0; k <= 4; k++) claim(u, k, 45, 'fuel_canopy_column');
  }
  const bollards = [];
  for (const u of ISLANDS) for (const v of [42, 48]) { bollards.push([u, 1, v]); claim(u, 1, v, 'bollard'); }

  // ---- lines ----
  const runs = {gasoline: [], diesel: [], power: [], fill: []}, ports = new Map();
  const run = (name, ...list) => { for (const c of list) runs[name].push(c); };
  const seg = (name, [ua, ka, va], [ub, kb, vb]) => { for (let u = Math.min(ua, ub); u <= Math.max(ua, ub); u++) for (let k = Math.min(ka, kb); k <= Math.max(ka, kb); k++) for (let v = Math.min(va, vb); v <= Math.max(va, vb); v++) runs[name].push([u, k, v]); };
  const port = (c, dir) => { const kk = key(...c); (ports.get(kk) || ports.set(kk, new Set()).get(kk)).add(dir); };
  // gasoline: pumps north to z 51, down to the header at k -3 / z 50
  for (const [t, fuel] of TANKS) {
    if (fuel === 'gasoline') { seg('gasoline', [t, -2, 54], [t, -2, 51]); run('gasoline', [t, -3, 51]); port([t, -2, 54], 'south'); }
    run('fill', [t, -2, FILL_V]); port([t, -2, FILL_V], 'up'); port([t, -2, FILL_V], 'down');
  }
  seg('gasoline', [16, -3, 50], [48, -3, 50]);
  // diesel: the pump east round the tank, down to k -5, north to the header at z 51
  run('diesel', [36, -2, 54], [37, -2, 54]); seg('diesel', [38, -2, 54], [38, -5, 54]); seg('diesel', [38, -5, 53], [38, -5, 51]); port([36, -2, 54], 'south');
  seg('diesel', [26, -5, 51], [46, -5, 51]);
  // power: header at k -4 / z 49 from the stub (x 14) east
  seg('power', [14, -4, 49], [47, -4, 49]);
  // the feed from the store's Distribution Panel (fuel_stop_a1_feeder.mjs): in from the parking strip at the court's north
  // edge (k -2), down to k -4, south between islands 2 and 3 into the header at x 31
  const feed = courtFeed();
  run('power', ...feed.cells); port(feed.entry, 'north'); claim(...feed.outside, 'feeder (parking strip)');
  for (const [t] of TANKS) {   // pump power ports face south: a stub west (diesel) or east (gasoline) of the tank, down, north
    const s = t === 36 ? -1 : 1, x = t + 2 * s;
    run('power', [t, -2, 56], [t + s, -2, 56]); seg('power', [x, -2, 56], [x, -4, 56]); seg('power', [x, -4, 55], [x, -4, 50]); port([t, -2, 56], 'north');
  }
  for (const u of ISLANDS) {
    if (u === POWER_ISLAND) {
      // gasoline at the outer ends (sump a0 ports face north at z 43 / south at z 47), down to k -3, south to the header
      run('gasoline', [u, -2, 42], [u, -2, 48]); seg('gasoline', [u, -3, 42], [u, -3, 49]); port([u, -2, 42], 'south'); port([u, -2, 48], 'north');
      // the canopy feed: up into the column base's bottom face, through the unused cell between the sumps, west, down
      run('power', [u, -1, 45], [u, -2, 45], [u - 1, -2, 45], [u - 1, -3, 45]); port([u, -1, 45], 'up');
      // sump power ports: a0 backs (west of z 43, east of z 47)
      run('power', [u - 1, -2, 43], [u - 1, -3, 43]); seg('power', [u - 1, -4, 43], [u - 1, -4, 48]); port([u - 1, -2, 43], 'east');
      run('power', [u + 1, -2, 47], [u + 1, -3, 47]); seg('power', [u + 1, -4, 47], [u + 1, -4, 48]); port([u + 1, -2, 47], 'west');
    } else {
      // gasoline between the sumps (both a0 ports face z 45), down, east two, south to the header
      run('gasoline', [u, -2, 45], [u, -3, 45], [u + 1, -3, 45]); seg('gasoline', [u + 2, -3, 45], [u + 2, -3, 49]); port([u, -2, 45], 'north'); port([u, -2, 45], 'south');
      // diesel at the outer ends (b0 ports), down to k -5, the branch south to the header
      run('diesel', [u, -2, 42], [u, -2, 48]); seg('diesel', [u, -3, 42], [u, -4, 42]); seg('diesel', [u, -3, 48], [u, -4, 48]); seg('diesel', [u, -5, 42], [u, -5, 50]);
      port([u, -2, 42], 'south'); port([u, -2, 48], 'north');
      // sump power ports: a0 backs (east of z 44, west of z 46)
      run('power', [u + 1, -2, 44], [u + 1, -3, 44]); seg('power', [u + 1, -4, 44], [u + 1, -4, 48]); port([u + 1, -2, 44], 'west');
      run('power', [u - 1, -2, 46], [u - 1, -3, 46]); seg('power', [u - 1, -4, 46], [u - 1, -4, 48]); port([u - 1, -2, 46], 'east');
    }
  }
  // links: a run's cells link to each other (orthogonal neighbours in the same run) and to their ports
  const lines = new Map();
  for (const [name, list] of Object.entries(runs)) {
    const set = new Set(list.map(c => key(...c)));
    for (const c of list) {
      const kk = key(...c);
      if (lines.has(kk)) { if (lines.get(kk).name !== name) conflicts.push(kk + ' ' + lines.get(kk).name + ' / ' + name); continue; }
      const links = new Set(ports.get(kk) || []);
      for (const [d, [du, dk, dv]] of Object.entries(DIRS)) {
        const n = key(c[0] + du, c[1] + dk, c[2] + dv);
        // fill risers are their own run per tank: never link one to another
        if (set.has(n) && name !== 'fill') links.add(d);
      }
      lines.set(kk, {name, links});
      claim(...c, name);
    }
  }
  // deadmen: beside each tank's bottom row, where no line runs
  const deadmen = [];
  for (const [t] of TANKS) for (const u of [t - 2, t + 2]) for (let v = 52; v <= 58; v++)
    if (!used.has(key(u, -5, v))) { put(u, -5, v, DEADMAN, 'deadman'); deadmen.push([u, -5, v]); }
  // runs of different fuels must not touch (a pipe placed later between them could join them)
  const touching = [], portsMet = {};
  for (const [kk, l] of lines) {
    if (l.name === 'power') continue;
    const [u, k, v] = kk.split(',').map(Number);
    for (const [du, dk, dv] of Object.values(DIRS)) { const o = lines.get(key(u + du, k + dk, v + dv)); if (o && o.name !== 'power' && (o.name !== l.name || l.name === 'fill')) touching.push(kk + ' ' + l.name + '/' + o.name); }
  }
  // every link must meet the same run linking back, or a port cell of a fixture (what the port belongs to)
  const dangling = [];
  for (const [kk, l] of lines) {
    const [u, k, v] = kk.split(',').map(Number);
    for (const d of l.links) {
      const [du, dk, dv] = DIRS[d], nk = key(u + du, k + dk, v + dv), o = lines.get(nk);
      const back = Object.keys(DIRS).find(x => DIRS[x].every((c, i) => c === -DIRS[d][i]));
      if (o) { if (o.name !== l.name || !o.links.has(back)) dangling.push(kk + ' ' + d + ' -> ' + nk + ' not linked back'); }
      else if (!used.has(nk) || used.get(nk) === 'canopy' || used.get(nk) === 'bollard') dangling.push(kk + ' ' + d + ' -> ' + nk + ' ' + (used.get(nk) || 'nothing'));
      else (portsMet[used.get(nk)] ||= []).push(kk + ' ' + d);
    }
  }
  for (const [kk, l] of lines) {
    const id = l.name === 'power' ? 'power_cable' : 'fluid_pipe', props = ['down', 'east', 'north', 'south', 'up', 'west'].map(d => d + '=' + l.links.has(d)).join(',');
    cells.set(kk, A(`${id}[${props}]`));
  }
  return {fixtures, cells, voids, columns, bollards, conflicts, touching, lines, dangling, portsMet, deadmen};
}

// ---- emission: lot -> plot-relative world ----
const turnState = s => s.replace(/(facing)=(north|south|east|west)/, (_, p, f) => p + '=' + TURN[f]).replace(/\[([^\]]*)\]/, (_, body) => {
  // pipe / cable links: swap the horizontal sides
  if (!/(^|,)north=/.test(body) || /facing=/.test(body)) return '[' + body + ']';
  const kv = Object.fromEntries(body.split(',').map(p => p.split('=')));
  const out = {...kv, north: kv.south, south: kv.north, east: kv.west, west: kv.east};
  return '[' + Object.keys(kv).sort().map(k => k + '=' + out[k]).join(',') + ']';
});
const rel = (u, k, v) => { const w = toWorld(u, k, v); return [w[0] - ORIGIN[0], w[1] - ORIGIN[1], w[2] - ORIGIN[2]]; };
function boxOps(boxes, block) {
  return boxes.map(([a, b]) => { const p = rel(...a), q = rel(...b); return {min: [0, 1, 2].map(i => Math.min(p[i], q[i])), max: [0, 1, 2].map(i => Math.max(p[i], q[i])), block}; });
}
/** Cells to cuboid ops: runs of one state along the world x axis, in batches of <= 128. */
export function cellBatches(cells) {
  const byRow = new Map();
  for (const [kk, s] of cells) { const [u, k, v] = kk.split(',').map(Number), [x, y, z] = rel(u, k, v), r = y + ',' + z;
    (byRow.get(r) || byRow.set(r, []).get(r)).push([x, turnState(s)]); }
  const ops = [];
  for (const [r, list] of byRow) {
    const [y, z] = r.split(',').map(Number); list.sort((a, b) => a[0] - b[0]);
    let i = 0; while (i < list.length) { let j = i; while (j + 1 < list.length && list[j + 1][0] === list[j][0] + 1 && list[j + 1][1] === list[i][1]) j++;
      ops.push({min: [list[i][0], y, z], max: [list[j][0], y, z], block: list[i][1]}); i = j + 1; }
  }
  const out = []; for (let i = 0; i < ops.length; i += 128) out.push(ops.slice(i, i + 128));
  return out;
}

export function plan() {
  const r = recipe();
  const canopyCells = new Map([...r.cells].filter(([kk]) => kk.split(',')[1] === String(CANOPY.k) || kk.split(',')[1] === '0'));
  const lineCells = new Map([...r.cells].filter(([kk]) => r.lines.has(kk)));
  return {id: ID, size: SIZE, origin: ORIGIN, resume: RESUME, fixtures: r.fixtures.length, columns: r.columns.length, bollards: r.bollards.length,
    voids: r.voids.length, canopy_and_ends: canopyCells.size, line_cells: lineCells.size,
    runs: Object.fromEntries(['gasoline', 'diesel', 'power', 'fill'].map(n => [n, [...r.lines.values()].filter(l => l.name === n).length])),
    batches: {surface: cellBatches(canopyCells).length, lines: cellBatches(lineCells).length},
    lights: [...r.cells.values()].filter(s => s.includes('fuel_canopy_light')).length, deadmen: r.deadmen.length,
    conflicts: r.conflicts, different_fuels_touching: r.touching, dangling: r.dangling,
    ports_met: Object.fromEntries(Object.entries(r.portsMet).map(([k, v]) => [k, v.length])),
    energy_cell: {lot: [13, -4, 49], world: toWorld(13, -4, 49), facing_world: TURN.west, note: 'its back port (opposite its facing) on the stub at lot x 14'}};
}

async function build() {
  const r = recipe();
  if (r.conflicts.length || r.touching.length || r.dangling.length) throw Error('RECIPE ' + JSON.stringify({conflicts: r.conflicts, touching: r.touching, dangling: r.dangling}));
  const c = new BridgeClient('./run'), dir = path.resolve('build/authoring_checks', ID);
  const status = await c.call('minecraft_status'), info = await c.call('authoring_info');
  if (info.id !== ID || info.width !== SIZE[0] || info.height !== SIZE[1] || info.depth !== SIZE[2]) throw Error('PLOT_MISMATCH ' + JSON.stringify(info) + ' run ' + RESUME);
  const o = info.min, world = p => [o[0] + p[0], o[1] + p[1], o[2] + p[2]];
  const player = await c.call('get_player_state'), [px, py, pz] = player.block;
  if (px >= o[0] && px < o[0] + SIZE[0] && pz >= o[2] && pz < o[2] + SIZE[2] && py >= o[1] - 2 && py < o[1] + SIZE[1]) throw Error('PLAYER_IN_PLOT: stand outside x ' + o[0] + '..' + (o[0] + SIZE[0] - 1) + ' z ' + o[2] + '..' + (o[2] + SIZE[2] - 1));
  const report = {plot: info, world: status.world, steps: [], failures: []};
  const step = async (label, tool, args) => {
    try { const res = await c.call(tool, args); report.steps.push(label + ' ok'); return res; }
    catch (e) { const msg = String(e.message || e); report.failures.push({label, error: msg}); report.steps.push(label + ' FAIL ' + msg); return null; }
  };
  const batch = async (label, ops) => step(label, 'we_batch_set', {target: 'AUTHORING_SESSION', operations: ops.map(op => ({min: world(op.min), max: world(op.max), block: op.block}))});
  const place = async f => {
    const pos = world(rel(...f.at)), args = {block_id: A(f.id), ...(f.tool === 'place_multiblock' ? {anchor: pos} : {pos}), ...(f.facing ? {facing: TURN[f.facing]} : {}),
      ...(Object.keys(f.properties).length ? {properties: f.properties} : {})};
    return step(f.id + ' @' + f.at.join(','), f.tool, args);
  };
  // 1. ground: backfill, slab, clear air; 2. the voids the fixtures go into
  const all = [[LOT.u0, 0, LOT.v0], [LOT.u1, 0, LOT.v1]];
  await batch('ground', [...boxOps([[[LOT.u0, LOT.k0, LOT.v0], [LOT.u1, -2, LOT.v1]]], SOIL), ...boxOps([[[LOT.u0, -1, LOT.v0], [LOT.u1, -1, LOT.v1]]], GROUND),
    ...boxOps([[[LOT.u0, 0, LOT.v0], [LOT.u1, LOT.k1, LOT.v1]]], 'minecraft:air'), ...boxOps([[[PIT.u0, PIT.k0, PIT.v0], [PIT.u1, PIT.k1, PIT.v1]]], BACKFILL)]);
  if (report.failures.length) return finish(c, dir, report);
  await batch('voids', boxOps(r.voids, 'minecraft:air'));
  // 3. tanks, pumps, covers, sumps, dispensers
  for (const f of r.fixtures) await place(f);
  // 4. canopy and island ends (plain blocks), then columns bottom-up, bollards
  const surface = new Map([...r.cells].filter(([kk]) => !r.lines.has(kk)));
  for (const [i, ops] of cellBatches(surface).entries()) await batch('canopy_ends_' + i, ops);
  for (const f of r.columns) await place(f);
  await batch('bollards', r.bollards.map(b => { const p = rel(...b); return {min: p, max: p, block: A('fuel_island_bollard[on_curb=true]')}; }));
  // 5. pipes and cables, exact links
  const lineCells = new Map([...r.cells].filter(([kk]) => r.lines.has(kk)));
  for (const [i, ops] of cellBatches(lineCells).entries()) await batch('lines_' + i, ops);
  return finish(c, dir, report);
}

/** The canopy layer alone (the columns' head plates keep a canopy piece above them). */
async function canopyOnly() {
  const r = recipe(), c = new BridgeClient('./run');
  await c.call('minecraft_status'); const info = await c.call('authoring_info');
  if (info.id !== ID || info.width !== SIZE[0] || info.height !== SIZE[1] || info.depth !== SIZE[2]) throw Error('PLOT_MISMATCH ' + JSON.stringify(info) + ' run ' + RESUME);
  const o = info.min, world = p => [o[0] + p[0], o[1] + p[1], o[2] + p[2]], log = [];
  const layer = new Map([...r.cells].filter(([kk]) => kk.split(',')[1] === String(CANOPY.k)));
  for (const ops of cellBatches(layer)) {
    await c.call('we_batch_set', {target: 'AUTHORING_SESSION', operations: ops.map(op => ({min: world(op.min), max: world(op.max), block: op.block}))});
    log.push('ok ' + ops.length + ' ops');
  }
  console.log(JSON.stringify({log, lights: [...layer.values()].filter(s => s.includes('fuel_canopy_light')).length}));
}

/**
 * The k -1 boxes that cut no multiblock: the bridge refuses an edit box splitting one (the dispenser sumps span k -2..-1),
 * so rows holding sump cells are cut into the runs between them; equal neighbouring rows merge.
 */
export function slabBoxes() {
  const r = recipe(), blocked = new Set();
  for (const f of r.fixtures) if (f.tool === 'place_multiblock' && f.id === 'fuel_dispenser_sump') {
    const [u, , a0] = f.at, b = a0 + (f.facing === 'east' ? 1 : -1);
    for (const v of [a0, b]) blocked.add(u + ',' + v);
  }
  const runs = v => { const out = []; let start = null;
    for (let u = LOT.u0; u <= LOT.u1 + 1; u++) {
      const open = u <= LOT.u1 && !blocked.has(u + ',' + v);
      if (open && start === null) start = u; else if (!open && start !== null) { out.push([start, u - 1]); start = null; }
    }
    return out; };
  const boxes = [];
  let prev = null, from = LOT.v0;
  for (let v = LOT.v0; v <= LOT.v1 + 1; v++) {
    const key = v <= LOT.v1 ? JSON.stringify(runs(v)) : null;
    if (key !== prev) { if (prev !== null) for (const [u0, u1] of JSON.parse(prev)) boxes.push([[u0, -1, from], [u1, -1, v - 1]]); prev = key; from = v; }
  }
  return boxes;
}

/** The court built before the tank pit detail: its backfill in the pit to gravel (tanks, pumps and lines stay), then the deadmen. */
async function pit() {
  const r = recipe(), c = new BridgeClient('./run');
  await c.call('minecraft_status'); const info = await c.call('authoring_info');
  if (info.id !== ID || info.width !== SIZE[0] || info.height !== SIZE[1] || info.depth !== SIZE[2]) throw Error('PLOT_MISMATCH ' + JSON.stringify(info) + ' run ' + RESUME);
  const o = info.min, world = p => [o[0] + p[0], o[1] + p[1], o[2] + p[2]], [box] = boxOps([[[PIT.u0, PIT.k0, PIT.v0], [PIT.u1, PIT.k1, PIT.v1]]], BACKFILL);
  if (BACKFILL !== SOIL) await c.call('we_replace', {target: 'AUTHORING_SESSION', min: world(box.min), max: world(box.max), from: SOIL, block: BACKFILL});
  const ops = r.deadmen.map(cell => { const p = world(rel(...cell)); return {min: p, max: p, block: DEADMAN}; });
  await c.call('we_batch_set', {target: 'AUTHORING_SESSION', operations: ops});
  const audit = await c.call('audit_support', {target: 'AUTHORING_SESSION'});
  console.log(JSON.stringify({deadmen: ops.length, audit: audit.counts, issues: audit.issues?.slice(0, 8)}));
}

/** The court built before Ground Materials V1: its light gray concrete slab (k -1) to the pavement; covers, sumps and cables stay. */
async function pavement() {
  const c = new BridgeClient('./run');
  await c.call('minecraft_status'); const info = await c.call('authoring_info');
  if (info.id !== ID || info.width !== SIZE[0] || info.height !== SIZE[1] || info.depth !== SIZE[2]) throw Error('PLOT_MISMATCH ' + JSON.stringify(info) + ' run ' + RESUME);
  const o = info.min, world = p => [o[0] + p[0], o[1] + p[1], o[2] + p[2]], ops = boxOps(slabBoxes(), GROUND), log = [];
  for (const op of ops) {
    const r = await c.call('we_replace', {target: 'AUTHORING_SESSION', min: world(op.min), max: world(op.max), from: OLD_GROUND, block: GROUND});
    log.push(r.changed_blocks ?? r.changed ?? null);
  }
  console.log(JSON.stringify({boxes: ops.length, reported: log}));
}

async function finish(c, dir, report) {
  const audit = await c.call('audit_support', {target: 'AUTHORING_SESSION'}).catch(e => ({error: String(e.message || e)}));
  report.audit = audit;
  await mkdir(dir, {recursive: true}); await writeFile(path.join(dir, 'forecourt_v1.json'), JSON.stringify(report, null, 1));
  console.log(JSON.stringify({steps: report.steps.length, failures: report.failures, audit: audit.counts ?? audit, issues: audit.issues?.slice(0, 12),
    energy_cell: plan().energy_cell}, null, 1));
}

const direct = process.argv[1] && path.resolve(process.argv[1]).toLowerCase() === fileURLToPath(import.meta.url).toLowerCase();   // not when imported (fuel_stop_a1_feeder.mjs)
const mode = direct ? process.argv[2] : null;
if (mode === 'plan') console.log(JSON.stringify(plan(), null, 1));
else if (mode === 'build') await build();
else if (mode === 'canopy') await canopyOnly();
else if (mode === 'pavement') await pavement();
else if (mode === 'pit') await pit();
else if (mode === 'audit') { const c = new BridgeClient('./run'); await c.call('minecraft_status'); console.log(JSON.stringify(await c.call('audit_support', {target: 'AUTHORING_SESSION'}), null, 1)); }
