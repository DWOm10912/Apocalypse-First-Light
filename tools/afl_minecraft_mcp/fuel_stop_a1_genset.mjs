// Fuel Stop A1: the diesel standby generator (docs/machines/diesel_standby_generator_v1.md) behind the store, through the AFL
// authoring bridge in the lighting plot (gas_station_02_lighting, the whole lot k -2..8): live authoring only, no export.
// 2026-10-09 (user: "就这样做吧 ... 一轮做进游戏").
//   genset     3 x 1 x 2 cells at u 25..27, v 6, its front (doors, panel) toward the rear drive (lot north = world south); the
//              master is the middle column (u 26), its back face centre the standard steel power port
//   cable      from the port's cell (u 26, k 0, v 7) along the back sidewalk under the wall to u 24, up into the service
//              meter box's bottom port (u 24, k 1, v 7; fuel_stop_a1_store.mjs powerSteps)
//   pad        concrete pavement under the set and the strip its doors swing over (u 24..28, v 5..6, k -1; was asphalt)
//   bollards   two at v 4 (u 25, 27), 2 m in front: clear of the doors (they reach 1 m out)
//   test cells the three energy cells the user put at u 22..24, v 6 (2026-10-09, the panel's trip test) are removed, and the
//              k 0 cable row behind them (u 22, 23, v 7; u 24 becomes this run's riser): the genset replaces them ("装好后拆掉
//              3 个测试能量单元", the approved plan; docs/worldgen/fuel_stop_a1_site_build_v1.md "供电")
// Lot frame as in fuel_stop_a1_site.mjs: u east, v south, k metres above G; world x = -50 - u, y = -51 + k, z = 474 - v (lot
// north = world south, lot east = world west). Every cell it writes is read first; anything but what it expects stops it.
//   node tools/afl_minecraft_mcp/fuel_stop_a1_genset.mjs plan  -> offline: the cells and the resume command
//   node tools/afl_minecraft_mcp/fuel_stop_a1_genset.mjs build -> checks and writes into the active lighting plot
import {BridgeClient} from './bridge_client.mjs';
import {mkdir, writeFile} from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {ID, LOT, SIZE, ORIGIN, RESUME, toWorld} from './fuel_stop_a1_lighting.mjs';

const A = id => 'apocalypse_firstlight:' + id;
const key = c => c.join(',');
export const GENSET = {anchor: [25, 0, 6], facing: 'south' /* world: the front toward lot north */, cells: [25, 26, 27].flatMap(u => [0, 1].map(k => [u, k, 6]))};
export const METER = [24, 1, 7];
export const TEST_CELLS = [[22, 0, 6], [23, 0, 6], [24, 0, 6]], TEST_WIRES = [[22, 0, 7], [23, 0, 7]];
export const BOLLARDS = [[25, 0, 4], [27, 0, 4]];
export const PAD = {u0: 24, u1: 28, v0: 5, v1: 6, k: -1};
// world sides of a lot cell: lot north (v - 1) = world south, lot east (u + 1) = world west
const cable = sides => A(`power_cable[down=false,east=${sides.includes('east')},north=${sides.includes('north')},south=${sides.includes('south')},up=${sides.includes('up')},west=${sides.includes('west')}]`);
/** The run: the port's cell (its world south side into the genset's back face), west along v 7 (world east), up into the meter box. */
export const CABLE = new Map([
  [key([26, 0, 7]), cable(['south', 'east'])],
  [key([25, 0, 7]), cable(['west', 'east'])],
  [key([24, 0, 7]), cable(['west', 'up'])],
]);

export function plan() {
  return {id: ID, resume: RESUME, genset: GENSET, cable: Object.fromEntries(CABLE), pad: PAD, bollards: BOLLARDS, remove: [...TEST_CELLS, ...TEST_WIRES],
    world: {anchor: toWorld(...GENSET.anchor), port: toWorld(26, 0, 7), meter: toWorld(...METER)}};
}

const AIR = new Set(['minecraft:air', 'minecraft:cave_air']);
const PLANTS = /^minecraft:(grass|short_grass|tall_grass|fern|large_fern|dandelion|poppy|azure_bluet|oxeye_daisy|cornflower|blue_orchid|allium|.*_tulip|lily_of_the_valley|dead_bush)$/;
const PAD_FROM = new Set([A('asphalt'), A('concrete_pavement')]);

async function build() {
  const c = new BridgeClient('./run'), dir = path.resolve('build/authoring_checks', ID);
  const status = await c.call('minecraft_status'), info = await c.call('authoring_info');
  if (info.id !== ID || info.width !== SIZE[0] || info.height !== SIZE[1] || info.depth !== SIZE[2] || info.min.some((x, i) => x !== ORIGIN[i]))
    throw Error('PLOT_MISMATCH ' + JSON.stringify(info) + ' run ' + RESUME);
  const o = info.min, at = new Map();
  const read = async () => { at.clear(); for (const k of [-1, 0, 1]) {
    const sl = await c.call('get_horizontal_slice', {target: 'AUTHORING_SESSION', coordinate: toWorld(0, k, 0)[1], encoding: 'palette'});
    const names = Object.fromEntries(Object.entries(sl.palette).map(([s, i]) => [i, s]));
    sl.rows.forEach((row, z) => row.forEach((i, x) => at.set(key([-50 - (o[0] + x), k, 474 - (o[2] + z)]), names[i])));
  } };
  await read();
  const id = s => (/^Block\{([^}]+)\}/.exec(s || '') || [])[1] || (s || '').replace(/\[.*$/, '');
  const here = cell => id(at.get(key(cell)));
  const placed = here(GENSET.anchor) === A('diesel_generator');
  const blocked = [], clear = new Map();
  const free = (cell, what, ok = []) => { const now = here(cell);
    if (PLANTS.test(now)) clear.set(key(cell), 'minecraft:air'); else if (!AIR.has(now) && !ok.includes(now)) blocked.push(key(cell) + ' ' + what + ' ' + now); };
  if (here(METER) !== A('service_meter_box')) blocked.push(key(METER) + ' expected the meter box: ' + here(METER));
  for (const cell of TEST_CELLS) { const now = here(cell); if (!AIR.has(now) && now !== A('energy_cell')) blocked.push(key(cell) + ' test cell spot holds ' + now); }
  for (const cell of TEST_WIRES) { const now = here(cell); if (!AIR.has(now) && now !== A('power_cable')) blocked.push(key(cell) + ' test wire spot holds ' + now); }
  if (!placed) for (const cell of GENSET.cells) free(cell, 'genset');
  for (const k of CABLE.keys()) free(k.split(',').map(Number), 'cable', [A('power_cable')]);
  for (const b of BOLLARDS) free(b, 'bollard', [A('fuel_island_bollard')]);
  for (let v = PAD.v0; v <= PAD.v1; v++) for (let u = PAD.u0; u <= PAD.u1; u++) if (!PAD_FROM.has(here([u, PAD.k, v]))) blocked.push(key([u, PAD.k, v]) + ' pad over ' + here([u, PAD.k, v]));
  for (const b of BOLLARDS) if (!PAD_FROM.has(here([b[0], -1, b[2]]))) blocked.push(key(b) + ' bollard on ' + here([b[0], -1, b[2]]));
  const player = (await c.call('get_player_state')).block, pu = -50 - player[0], pv = 474 - player[2], pk = player[1] + 51;
  if (!placed && GENSET.cells.some(q => q[0] === pu && q[2] === pv && (q[1] === pk || q[1] === pk + 1))) blocked.push('PLAYER_IN genset');
  if (blocked.length) throw Error('GENSET_BLOCKED ' + JSON.stringify(blocked));

  const report = {plot: info, world: status.world, plan: plan(), steps: [], failures: []};
  const step = async (label, tool, args) => {
    try { const res = await c.call(tool, args); report.steps.push(label + ' ok'); return res; }
    catch (e) { const msg = String(e.message || e); report.failures.push({label, error: msg}); report.steps.push(label + ' FAIL ' + msg); return null; }
  };
  const ops = cells => [...cells].map(([k, block]) => { const w = toWorld(...k.split(',').map(Number)); return {min: w, max: w, block}; });
  // 1. the test cells out, plants cleared; 2. the pad; 3. the cable (before the genset, so its port finds it); 4. the genset; 5. bollards
  const out = new Map([...TEST_CELLS.filter(q => here(q) === A('energy_cell')), ...TEST_WIRES.filter(q => here(q) === A('power_cable'))].map(q => [key(q), 'minecraft:air']).concat([...clear]));
  if (out.size) await step('clear', 'we_batch_set', {target: 'AUTHORING_SESSION', operations: ops(out)});
  const pad = new Map(); for (let v = PAD.v0; v <= PAD.v1; v++) for (let u = PAD.u0; u <= PAD.u1; u++) pad.set(key([u, PAD.k, v]), A('concrete_pavement[axis=x]'));
  await step('pad', 'we_batch_set', {target: 'AUTHORING_SESSION', operations: ops(pad)});
  await step('cable', 'we_batch_set', {target: 'AUTHORING_SESSION', operations: ops(CABLE)});
  if (!placed) await step('diesel_generator', 'place_multiblock', {block_id: A('diesel_generator'), anchor: toWorld(...GENSET.anchor), facing: GENSET.facing});
  await step('bollards', 'we_batch_set', {target: 'AUTHORING_SESSION', operations: ops(new Map(BOLLARDS.map(b => [key(b), A('fuel_island_bollard[on_curb=false]')])))});
  // read back
  await read();
  const norm = s => { const mm = /^(?:Block\{([^}]+)\})?([^\[]*)(?:\[(.*)\])?$/.exec(s || ''); return (mm[1] || mm[2]) + (mm[3] ? '[' + mm[3].split(',').sort().join(',') + ']' : ''); };
  const wrong = [...CABLE].filter(([k, s]) => norm(at.get(k)) !== norm(s)).map(([k]) => k + ' ' + at.get(k));
  for (const cell of GENSET.cells) if (here(cell) !== A('diesel_generator')) wrong.push(key(cell) + ' genset ' + at.get(key(cell)));
  for (const cell of TEST_CELLS) if (here(cell) === A('energy_cell')) wrong.push(key(cell) + ' test cell still there');
  for (const cell of TEST_WIRES) if (here(cell) === A('power_cable')) wrong.push(key(cell) + ' test wire still there');
  const audit = await c.call('audit_support', {target: 'AUTHORING_SESSION'}).catch(e => ({error: String(e.message || e)}));
  Object.assign(report, {readback: {wrong}, audit});
  await mkdir(dir, {recursive: true}); await writeFile(path.join(dir, 'fuel_stop_a1_genset_v1.json'), JSON.stringify(report, null, 1));
  console.log(JSON.stringify({failures: report.failures, wrong, audit: audit.counts ?? audit, issues: audit.issues?.slice(0, 12)}, null, 1));
}

const direct = process.argv[1] && path.resolve(process.argv[1]).toLowerCase() === fileURLToPath(import.meta.url).toLowerCase();
const mode = direct ? process.argv[2] : null;
const fail = e => { console.error(e); process.exitCode = 1; };
if (mode === 'plan') console.log(JSON.stringify(plan(), null, 1));
else if (mode === 'build') build().catch(fail);
void LOT;
