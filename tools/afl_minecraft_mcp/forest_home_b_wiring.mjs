// Forest start home B "溪谷老宅" — Building Wiring V1 phase 4 (2026-10-14; docs/gameplay/building_wiring_v1.md 5.3,
// design docs/gameplay/building_wiring_v1_design.md 13.2): the house's hidden wiring, laid through the AFL authoring bridge
// in the built house of the "house test" world. Only adds: the switches, the keyless lampholders that replace the pull-chain
// lampholders in the switched rooms, the attic and cellar lamps, then the wire, the tap groups and the panel main.
// Paper frame as forest_home_b_m1.mjs: X east, Z south, k = layer above grade (k 0 = floors and the fieldstone plinth);
// world = plot origin + (X + 10, k + 3, Z + 10); in "house test" world = (X - 775, k + 86, Z - 134).
//   node tools/afl_minecraft_mcp/forest_home_b_wiring.mjs plan      -> the cells, devices and groups, offline checks (no game)
//   node tools/afl_minecraft_mcp/forest_home_b_wiring.mjs fixtures  -> checks every target cell, then the lamps and switches
//   node tools/afl_minecraft_mcp/forest_home_b_wiring.mjs wire      -> wiring_lay per run, wiring_group, panel main on
//   node tools/afl_minecraft_mcp/forest_home_b_wiring.mjs check     -> reads every device's feed back against the plan
// Reports go to build/authoring_checks/forest_home_b_m1/wiring_<mode>.json.
import {BridgeClient} from './bridge_client.mjs';
import {mkdir, writeFile} from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const ID = 'forest_home_b_m1', X0 = -10, K0 = -3, Z0 = -10;
const A = 'apocalypse_firstlight:';
const plotOf = ([X, k, Z]) => [X - X0, k - K0, Z - Z0];
const NL = String.fromCharCode(10);

// The panel (load center, facing west, user re-placed 2026-10-14: WIRED) and the generator inlet box outside the east wall.
export const PANEL = [16, 2, 2], INLET = [18, 1, 2];

// Lamps. replace: a porcelain pull-chain lampholder there now becomes a keyless one (the switched rooms, design 13.1 item 4).
export const LAMPS = [
  {name: 'living ceiling', at: [9, 3, 4], facing: 'down', replace: true, circuit: 1, group: 1},
  {name: 'dining wall', at: [11, 3, 9], facing: 'north', replace: true, circuit: 1, group: 1},
  {name: 'bedroom ceiling', at: [2, 3, 3], facing: 'down', replace: true, circuit: 1, group: 2},
  {name: 'attic gable', at: [1, 6, 3], facing: 'east', add: 'keyless_lampholder', circuit: 1, group: 3},
  {name: 'kitchen wall', at: [14, 3, 1], facing: 'south', replace: true, circuit: 2, group: 1},
  {name: 'entry wall', at: [16, 3, 6], facing: 'north', replace: true, circuit: 2, group: 1},
  {name: 'storage ceiling', at: [2, 3, 8], keep: 'porcelain_lampholder', circuit: 2, group: 0},
  {name: 'cellar ceiling', at: [7, -1, -6], facing: 'down', add: 'porcelain_lampholder', properties: {on: 'false'}, circuit: 2, group: 0},
  {name: 'porch wall', at: [9, 3, 11], keep: 'porcelain_lampholder', circuit: 2, group: 15},
];
// Wall switches, 1.2 m up (k 2), on the latch side inside the door (the attic one at the foot of the ladder).
export const SWITCHES = [
  {name: 'front door', at: [9, 2, 9], facing: 'north', circuit: 1, group: 1},
  {name: 'bedroom door', at: [4, 2, 6], facing: 'west', circuit: 1, group: 2},
  {name: 'attic ladder', at: [6, 2, 2], facing: 'east', circuit: 1, group: 3},
  {name: 'kitchen front door', at: [14, 2, 6], facing: 'north', circuit: 2, group: 1},
  {name: 'kitchen passage door', at: [13, 2, 1], facing: 'east', circuit: 2, group: 1},
];
export const OUTLETS = [
  {name: 'kitchen counter', at: [15, 2, 1], circuit: 3, group: 0},
  {name: 'dining', at: [10, 1, 9], circuit: 3, group: 0},
];

// The wire, run by run; a run starts next to a cell laid before it (or at the panel's mount, the wall cell behind it).
// The runs keep off every other device's mount cell, so each device's tap comes from its own circuit.
const line = (a, b) => {   // the cells from a to b along one axis, both ends included
  const out = [], d = a.map((v, i) => Math.sign(b[i] - v)), n = Math.max(...a.map((v, i) => Math.abs(b[i] - v)));
  if (d.filter(x => x !== 0).length > 1) throw Error('LINE_NOT_STRAIGHT ' + a + ' ' + b);
  for (let i = 0; i <= n; i++) out.push(a.map((v, j) => v + d[j] * i));
  return out;
};
const path3 = (...pts) => pts.slice(1).reduce((acc, p, i) => acc.concat(line(pts[i], p).slice(1)), [pts[0]]);
export const RUNS = [
  {circuit: 5, name: 'feeder: inlet box to panel', cells: path3([17, 1, 2], [17, 2, 2])},
  // circuit 1, main house lights: under the floor along Z 2, up partition X 5 into the ceiling
  {circuit: 1, name: 'trunk', cells: path3([17, 2, 2], [17, 0, 2], [5, 0, 2], [5, 4, 2])},
  {circuit: 1, name: 'living ceiling', cells: path3([6, 4, 2], [8, 4, 2], [8, 4, 4], [9, 4, 4])},
  {circuit: 1, name: 'bedroom and attic', cells: path3([4, 4, 2], [2, 4, 2], [2, 4, 3], [0, 4, 3], [0, 6, 3])},
  {circuit: 1, name: 'bedroom switch', cells: path3([5, 3, 3], [5, 3, 6], [5, 2, 6])},
  {circuit: 1, name: 'dining wall', cells: path3([11, 0, 3], [11, 0, 10], [11, 3, 10])},
  {circuit: 1, name: 'front door switch', cells: path3([10, 0, 10], [9, 0, 10], [9, 2, 10])},
  // circuit 2, kitchen, entry, storage, cellar, porch
  {circuit: 2, name: 'kitchen north wall', cells: path3([17, 2, 2], [17, 3, 2], [17, 3, 0], [12, 3, 0], [12, 3, 1], [12, 2, 1])},
  {circuit: 2, name: 'north plinth', cells: path3([13, 2, 0], [13, 0, 0], [6, 0, 0])},
  {circuit: 2, name: 'cellar', cells: path3([7, -1, 0], [7, -1, -3], [7, 0, -3], [7, 0, -6])},
  {circuit: 2, name: 'storage', cells: path3([6, 0, 1], [6, 0, 6], [2, 0, 6], [2, 0, 7], [2, 4, 7], [2, 4, 8])},
  {circuit: 2, name: 'porch', cells: path3([7, 0, 6], [7, 0, 10], [7, 3, 10], [9, 3, 10])},
  {circuit: 2, name: 'entry and kitchen door', cells: path3([17, 1, 2], [17, 1, 7], [16, 1, 7], [16, 3, 7], [14, 3, 7], [14, 2, 7])},
  // circuit 3, outlets
  {circuit: 3, name: 'kitchen outlet', cells: path3([17, 2, 2], [17, 2, 0], [17, 0, 0], [15, 0, 0], [15, 2, 0])},
  {circuit: 3, name: 'dining outlet', cells: path3([15, 0, 1], [15, 0, 7], [12, 0, 7], [12, 0, 10], [10, 0, 10], [10, 1, 10])},
];

const mountOf = (at, facing) => {   // the cell behind a facing (a ceiling fixture: the cell above)
  const d = {down: [0, 1, 0], up: [0, -1, 0], north: [0, 0, 1], south: [0, 0, -1], west: [1, 0, 0], east: [-1, 0, 0]}[facing];
  return at.map((v, i) => v + d[i]);
};
const key = p => p.join(',');

export function plan() {
  const problems = [], laid = new Map();   // circuit -> Set of cells
  for (const r of RUNS) {
    const set = laid.get(r.circuit) ?? new Set();
    const first = r.cells[0], touches = [...set].some(k => k.split(',').map(Number).reduce((s, v, i) => s + Math.abs(v - first[i]), 0) <= 1);
    if (set.size && !touches && !set.has(key(first))) problems.push('run ' + r.name + ' does not start next to its circuit');
    for (const c of r.cells) set.add(key(c));
    laid.set(r.circuit, set);
  }
  // every device's mount carries its own circuit and no other circuit 1-4 (one tap per device, the first circuit there wins)
  const devices = [...LAMPS.map(l => ({...l, facing: l.facing ?? KNOWN_FACING[key(l.at)]})), ...SWITCHES, ...OUTLETS.map(o => ({...o, facing: KNOWN_FACING[key(o.at)]}))];
  for (const d of devices) {
    const m = key(mountOf(d.at, d.facing));
    for (const [c, set] of laid) {
      if (c === 5) continue;
      if (c === d.circuit && !set.has(m)) problems.push(d.name + ': circuit ' + c + ' misses its mount ' + m);
      if (c !== d.circuit && set.has(m)) problems.push(d.name + ': circuit ' + c + ' also runs through its mount ' + m);
    }
  }
  const metres = [...laid].map(([c, s]) => ({circuit: c, cells: s.size}));
  return {devices: devices.length, runs: RUNS.length, metres, problems};
}
// Facings of the devices that stay as they are (read back 2026-10-14).
const KNOWN_FACING = {'2,3,8': 'down', '9,3,11': 'south', '15,2,1': 'south', '10,1,9': 'north'};

async function connect() {
  const c = new BridgeClient('./run');
  await c.call('minecraft_status');
  const info = await c.call('authoring_info');
  if (info.id !== ID) throw Error('PLOT_MISMATCH ' + JSON.stringify(info));
  const o = info.min, world = p => { const q = plotOf(p); return [o[0] + q[0], o[1] + q[1], o[2] + q[2]]; };
  return {c, world};
}
async function stateAt(c, w) {
  const r = await c.call('get_horizontal_slice', {coordinate: w[1], encoding: 'palette', target: 'AUTHORING_SESSION', min: w, max: w});
  const id = r.rows[0][0];
  return Object.entries(r.palette).find(([, v]) => v === id)[0];
}
async function report(mode, data) {
  const dir = path.resolve('build/authoring_checks', ID);
  await mkdir(dir, {recursive: true});
  await writeFile(path.join(dir, 'wiring_' + mode + '.json'), JSON.stringify(data, null, 2));
}

async function fixtures() {
  const {c, world} = await connect(), out = [], bad = [];
  // preflight: every cell shows what the plan expects before anything changes
  for (const l of LAMPS) {
    const s = await stateAt(c, world(l.at));
    if (l.replace && !(s.includes(A + 'porcelain_lampholder') && s.includes('facing=' + l.facing))) bad.push(l.name + ' expected porcelain facing ' + l.facing + ', found ' + s);
    if (l.add && !s.includes('minecraft:air')) bad.push(l.name + ' expected air, found ' + s);
    if (l.keep && !s.includes(A + l.keep)) bad.push(l.name + ' expected ' + l.keep + ', found ' + s);
  }
  for (const sw of SWITCHES) { const s = await stateAt(c, world(sw.at)); if (!s.includes('minecraft:air')) bad.push(sw.name + ' switch cell not air: ' + s); }
  if (bad.length) { console.log('PREFLIGHT FAILED' + NL + bad.join(NL)); await report('fixtures', {bad}); process.exit(1); }
  const removals = LAMPS.filter(l => l.replace).map(l => ({min: world(l.at), max: world(l.at), block: 'minecraft:air'}));
  await c.call('we_batch_set', {target: 'AUTHORING_SESSION', operations: removals});
  out.push('removed ' + removals.length + ' pull-chain lampholders');
  const place = [...LAMPS.filter(l => l.replace || l.add).map(l => ({name: l.name, block_id: A + (l.add ?? 'keyless_lampholder'), at: l.at, facing: l.facing, properties: l.properties})),
    ...SWITCHES.map(s => ({name: s.name + ' switch', block_id: A + 'wall_switch', at: s.at, facing: s.facing, properties: {on: 'true'}}))];
  for (const f of place) {
    const pos = world(f.at);
    try {
      await c.call('place_fixture', {block_id: f.block_id, pos, facing: f.facing, ...(f.properties ? {properties: f.properties} : {})});
      const rec = await c.call('reconcile_shapes', {target: 'AUTHORING_SESSION', min: pos, max: pos});
      out.push('ok ' + f.name + ' ' + pos + ' -> ' + (await stateAt(c, pos)) + ' (reconciled ' + (rec.reconciled ?? 0) + ')');
    } catch (e) { out.push('FAIL ' + f.name + ' ' + pos + ' ' + String(e.message || e)); }
  }
  const lo = world([-1, -2, -9]), hi = world([19, 7, 12]);
  const audit = await c.call('audit_support', {target: 'AUTHORING_SESSION', min: lo, max: hi});
  out.push('audit: ' + JSON.stringify(audit).slice(0, 600));
  console.log(out.join(NL));
  await report('fixtures', {out, audit});
}

async function wire() {
  const {c, world} = await connect(), out = [];
  for (const r of RUNS) {
    try {
      const res = await c.call('wiring_lay', {cells: r.cells.map(world), circuit: r.circuit});
      out.push('ok ' + r.name + ' (circuit ' + r.circuit + '): ' + res.cells + ' cells, ' + res.laid + ' new, taps ' + JSON.stringify(res.taps));
    } catch (e) { out.push('FAIL ' + r.name + ' ' + String(e.message || e)); console.log(out.join(NL)); await report('wire', {out}); process.exit(1); }
  }
  for (const d of [...LAMPS, ...SWITCHES]) if (d.group) {
    try { const res = await c.call('wiring_group', {device: world(d.at), group: d.group}); out.push('group ' + d.name + ' -> ' + res.group + ' (circuit ' + res.circuit + ')'); }
    catch (e) { out.push('FAIL group ' + d.name + ' ' + String(e.message || e)); }
  }
  const panel = await c.call('wiring_panel', {pos: world(PANEL), main: true});
  out.push('panel ' + JSON.stringify(panel));
  console.log(out.join(NL));
  await report('wire', {out, panel});
}

async function check() {
  const {c, world} = await connect(), out = [], bad = [];
  const lo = world([-1, -2, -9]), hi = world([19, 7, 12]);
  const r = await c.call('wiring_read', {target: 'AUTHORING_SESSION', min: lo, max: hi});
  const feeds = new Map(r.devices.map(d => [key(d.pos), d]));
  for (const d of [...LAMPS, ...SWITCHES, ...OUTLETS]) {
    const f = feeds.get(key(world(d.at)));
    const got = f?.feed ? 'circuit ' + f.feed.circuit + ' group ' + f.feed.group : 'no feed';
    const ok = f?.feed && f.feed.circuit === d.circuit && f.feed.group === d.group;
    (ok ? out : bad).push((ok ? 'ok ' : 'BAD ') + d.name + ': ' + got + (ok ? '' : ' (plan circuit ' + d.circuit + ' group ' + d.group + ')'));
  }
  const inlet = feeds.get(key(world(INLET)));
  (inlet?.entry_panel ? out : bad).push('inlet box -> panel ' + JSON.stringify(inlet?.entry_panel ?? null));
  out.push('nodes ' + r.nodes.length + ', panel ' + JSON.stringify(r.panels ?? null));
  console.log([...bad, ...out].join(NL));
  await report('check', {bad, out, nodes: r.nodes.length, panels: r.panels});
  if (bad.length) process.exit(1);
}

const direct = process.argv[1] && path.resolve(process.argv[1]).toLowerCase() === fileURLToPath(import.meta.url).toLowerCase();
const mode = direct ? process.argv[2] : null;
if (mode === 'plan') { const p = plan(); console.log(JSON.stringify(p, null, 2)); if (p.problems.length) process.exit(1); }
else if (mode === 'fixtures') await fixtures();
else if (mode === 'wire') await wire();
else if (mode === 'check') await check();
else if (direct) console.log('modes: plan | fixtures | wire | check');
