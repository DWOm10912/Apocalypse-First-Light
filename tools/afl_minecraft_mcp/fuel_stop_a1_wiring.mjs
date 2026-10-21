// Fuel Stop A1 (gas_station_01) — hidden wiring (2026-10-20; docs/gameplay/building_wiring_v1.md 5.5,
// docs/worldgen/gas_station_01_asset_v1.md): turns the store from LEGACY_AREA area supply into a WIRED panel with 4 circuits,
// laid through the AFL authoring bridge on a copy of the template placed in a fresh test world (never the original build world).
// The plan is read from the template itself (src/main/resources/.../structures/gas_station_01.nbt): every wiring device, the
// cells that can hold wire, and runs found by a shortest path with a turn penalty, one per device, each starting on wire of
// its own circuit already planned and keeping off every other circuit's device mounts (a tap goes to the first circuit laid
// through a device's mount). Template coordinates (x, y, z); in game = the authoring plot's origin + (x, y, z), no rotation.
//   node tools/afl_minecraft_mcp/fuel_stop_a1_wiring.mjs plan   -> offline: devices, circuits, runs, checks (no game)
//   node tools/afl_minecraft_mcp/fuel_stop_a1_wiring.mjs wire   -> panel WIRED, wiring_lay per run, groups, main on
//   node tools/afl_minecraft_mcp/fuel_stop_a1_wiring.mjs check  -> reads every device's feed back against the plan
// Reports go to build/authoring_checks/gas_station_01/wiring_<mode>.json.
import {BridgeClient} from './bridge_client.mjs';
import {mkdir, readFile, writeFile} from 'node:fs/promises';
import {gunzipSync} from 'node:zlib';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const ID = 'gas_station_01', SIZE = [64, 16, 64];
const TEMPLATE = 'src/main/resources/data/apocalypse_firstlight/structures/gas_station_01.nbt';
const NL = String.fromCharCode(10);
const key = p => p.join(',');

// ------------------------------------------------------------------------------------------------------- template
function readNbt(buf) {   // big-endian NBT, root compound; lists of compounds as arrays
  let i = 0;
  const u8 = () => buf[i++], i16 = () => { const v = buf.readInt16BE(i); i += 2; return v; };
  const i32 = () => { const v = buf.readInt32BE(i); i += 4; return v; };
  const str = () => { const n = buf.readUInt16BE(i); i += 2; const s = buf.toString('utf8', i, i + n); i += n; return s; };
  const val = t => {
    switch (t) {
      case 1: return buf.readInt8(i++);
      case 2: return i16();
      case 3: return i32();
      case 4: { const v = buf.readBigInt64BE(i); i += 8; return v; }
      case 5: { const v = buf.readFloatBE(i); i += 4; return v; }
      case 6: { const v = buf.readDoubleBE(i); i += 8; return v; }
      case 7: { const n = i32(); const v = [...buf.subarray(i, i + n)]; i += n; return v; }
      case 8: return str();
      case 9: { const et = u8(), n = i32(), out = []; for (let k = 0; k < n; k++) out.push(val(et)); return out; }
      case 10: { const o = {}; for (;;) { const ct = u8(); if (ct === 0) return o; const name = str(); o[name] = val(ct); } }
      case 11: { const n = i32(), out = []; for (let k = 0; k < n; k++) out.push(i32()); return out; }
      case 12: { const n = i32(), out = []; for (let k = 0; k < n; k++) { out.push(buf.readBigInt64BE(i)); i += 8; } return out; }
      default: throw Error('NBT tag ' + t);
    }
  };
  u8(); str();
  return val(10);
}

async function template() {
  const root = readNbt(gunzipSync(await readFile(TEMPLATE)));
  const pal = root.palette.map(p => ({name: p.Name.replace(/^[a-z_]+:/, ''), props: p.Properties ?? {}}));
  const cells = new Map();
  for (const b of root.blocks) cells.set(key(b.pos), {...pal[b.state], be: !!b.nbt});
  for (const e of EDITS) cells.set(key(e.at), {name: e.block, props: e.props, be: false});
  return cells;
}

// The only change to the building (2026-10-20): the side eyebrow canopies hang in front of glazing all along, so nothing
// behind them holds wire (glass is no_wiring). Each grows one cell north to the corner pier, ending flush with the
// storefront, and the downlights' wire comes in through that pier. The canopy that was the end now continues.
const canopy = (facing, left, right) => ({facing, left: String(left), right: String(right), rod: 'true'});
export const EDITS = [
  {at: [18, 8, 40], was: 'air', block: 'metal_eyebrow_canopy', props: canopy('west', false, true)},
  {at: [18, 8, 41], was: 'metal_eyebrow_canopy', block: 'metal_eyebrow_canopy', props: canopy('west', true, true)},
  {at: [46, 8, 40], was: 'air', block: 'metal_eyebrow_canopy', props: canopy('east', true, false)},
  {at: [46, 8, 41], was: 'metal_eyebrow_canopy', block: 'metal_eyebrow_canopy', props: canopy('east', true, true)},
];
const stateOf = e => 'apocalypse_firstlight:' + e.block + '[' + Object.entries(e.props).map(([k, v]) => k + '=' + v).join(',') + ']';

// ------------------------------------------------------------------------------------------------------ the plan
// Cells the runs may use: the store's walls, floor slab and roof, and the eyebrow canopies with the jambs that carry them
// (all hold wire in game: something to collide with, no block entity, not no_wiring). Never the storefront glazing.
const SOLID = new Set(['face_brick_charcoal', 'face_brick_warm_gray', 'white_concrete', 'roof_tpo', 'reinforced_concrete',
  'porcelain_floor_tile', 'sealed_concrete_floor', 'restroom_floor_tile', 'ground_face_block', 'metal_wall_panel',
  'metal_eyebrow_canopy', 'metal_panel_jamb']);
const BOX = {min: [17, 4, 38], max: [47, 9, 57]};
const DEVICES = new Set(['industrial_utility_light', 'linear_light', 'emergency_light', 'wall_outlet', 'wall_pack',
  'canopy_downlight', 'channel_letter']);
export const PANEL = [37, 6, 54], METER = [39, 6, 56];
const BACK_ROOMS_Z = 50;   // the sales floor is z 41-49; the partition at z 50, the back rooms z 51-54

// What each device is on. Circuit 1 sales-floor lights (and the two emergency lights on its wall), 2 back-room lights,
// 3 outlets, 4 outside: wall packs, eyebrow-canopy downlights, the PRAIRIE letters, all on the photocell group.
function assign(name, at) {
  if (name === 'wall_outlet') return {circuit: 3, group: 0, room: 'outlets'};
  if (name === 'wall_pack' || name === 'canopy_downlight' || name === 'channel_letter') return {circuit: 4, group: 15, room: 'outside'};
  if (name === 'emergency_light') return {circuit: 1, group: 0, room: 'sales floor'};
  return at[2] < BACK_ROOMS_Z ? {circuit: 1, group: 0, room: 'sales floor'} : {circuit: 2, group: 0, room: 'back rooms'};
}
const STEP = {down: [0, 1, 0], up: [0, -1, 0], north: [0, 0, 1], south: [0, 0, -1], west: [1, 0, 0], east: [-1, 0, 0]};
const mountOf = (at, facing) => at.map((v, i) => v + STEP[facing][i]);   // the cell behind its facing; none: the cell above

const DIRS = [[1, 0, 0], [-1, 0, 0], [0, 1, 0], [0, -1, 0], [0, 0, 1], [0, 0, -1]];
const TURN = 4, ROOF = 9;

// Shortest path (step 1, turn TURN) from any cell of the tree to the target, over SOLID cells not in blocked.
function route(cells, tree, target, blocked) {
  const inBox = p => p.every((v, i) => v >= BOX.min[i] && v <= BOX.max[i]);
  const ok = p => { const k = key(p); if (k === key(target)) return true; if (blocked.has(k) || !inBox(p)) return false; const c = cells.get(k); return !!c && SOLID.has(c.name) && !c.be; };
  const heap = [], best = new Map(), prev = new Map();
  const push = (cost, p, d, from) => {
    const s = key(p) + '/' + d;
    if (best.has(s) && best.get(s) <= cost) return;
    best.set(s, cost); prev.set(s, from);
    heap.push([cost, heap.length, p, d, s]);
    let k = heap.length - 1;
    while (k > 0) { const up = (k - 1) >> 1; if (cmp(heap[up], heap[k]) <= 0) break; [heap[up], heap[k]] = [heap[k], heap[up]]; k = up; }
  };
  const cmp = (a, b) => a[0] - b[0] || a[1] - b[1];
  const pop = () => {
    const top = heap[0], last = heap.pop();
    if (heap.length) { heap[0] = last; let k = 0; for (;;) { const l = 2 * k + 1, r = l + 1; let m = k; if (l < heap.length && cmp(heap[l], heap[m]) < 0) m = l; if (r < heap.length && cmp(heap[r], heap[m]) < 0) m = r; if (m === k) break; [heap[m], heap[k]] = [heap[k], heap[m]]; k = m; } }
    return top;
  };
  for (const t of tree) push(0, t.split(',').map(Number), -1, null);
  while (heap.length) {
    const [cost, , p, d, s] = pop();
    if (best.get(s) < cost) continue;
    if (key(p) === key(target)) {
      const out = []; let cur = s;
      while (cur) { out.push(cur.split('/')[0].split(',').map(Number)); cur = prev.get(cur); }
      return out.reverse();
    }
    if (key(p) !== key(target) && !tree.has(key(p)) && !ok(p)) continue;
    DIRS.forEach((v, nd) => {
      const q = p.map((x, i) => x + v[i]);
      if (!ok(q) && !tree.has(key(q))) return;
      // runs stay in the roof or the floor slab rather than along the middle of a wall where they can
      const lift = v[1] === 0 && q[1] !== ROOF && q[1] !== 4 ? 0.5 : 0;
      push(cost + 1 + lift + (d >= 0 && d !== nd ? TURN : 0), q, nd, s);
    });
  }
  return null;
}

export async function plan() {
  const cells = await template(), problems = [];
  const devices = [];
  for (const [k, c] of cells) {
    if (!DEVICES.has(c.name)) continue;
    const at = k.split(',').map(Number), facing = c.props.facing ?? 'down';
    devices.push({name: c.name + ' ' + k, block: c.name, at, facing, mount: mountOf(at, facing), ...assign(c.name, at)});
  }
  devices.sort((a, b) => a.circuit - b.circuit || a.at[1] - b.at[1] || a.at[2] - b.at[2] || a.at[0] - b.at[0]);
  const panelMount = mountOf(PANEL, cells.get(key(PANEL)).props.facing), meterMount = mountOf(METER, cells.get(key(METER)).props.facing);
  const mounts = new Map();
  for (const d of devices) {
    if (mounts.has(key(d.mount))) problems.push('two devices on one mount ' + key(d.mount));
    mounts.set(key(d.mount), d);
  }
  const runs = [], laid = new Map();
  const grow = (circuit, root, targets, label) => {
    const tree = new Set([key(root)]);
    const blocked = new Set([...mounts.values()].filter(d => d.circuit !== circuit).map(d => key(d.mount)));
    if (circuit !== 5) blocked.add(key(meterMount));
    let left = [...targets];
    while (left.length) {
      // the nearest target first (straight-line distance from the tree), ties in plan order
      left.sort((a, b) => near(tree, a.mount) - near(tree, b.mount));
      const t = left.shift();
      if (tree.has(key(t.mount))) continue;
      const cellsRun = route(cells, tree, t.mount, blocked);
      if (!cellsRun) { problems.push('no route to ' + t.name + ' mount ' + key(t.mount)); continue; }
      runs.push({circuit, name: label(t), cells: cellsRun});
      for (const c of cellsRun) tree.add(key(c));
    }
    laid.set(circuit, tree);
  };
  const near = (tree, p) => Math.min(...[...tree].map(k => k.split(',').map(Number).reduce((s, v, i) => s + Math.abs(v - p[i]), 0)));
  grow(5, meterMount, [{name: 'panel', mount: panelMount}], () => 'feeder: meter box to panel');
  for (let c = 1; c <= 4; c++) grow(c, panelMount, devices.filter(d => d.circuit === c), d => d.room + ': ' + d.name);
  // checks: every device's mount carries its own circuit and no other circuit 1-4
  for (const d of devices) for (const [c, set] of laid) {
    if (c === 5) continue;
    if (c === d.circuit && !set.has(key(d.mount))) problems.push(d.name + ': circuit ' + c + ' misses its mount');
    if (c !== d.circuit && set.has(key(d.mount))) problems.push(d.name + ': circuit ' + c + ' also runs through its mount');
  }
  for (const r of runs) for (let i = 1; i < r.cells.length; i++)
    if (r.cells[i].reduce((s, v, j) => s + Math.abs(v - r.cells[i - 1][j]), 0) !== 1) problems.push('run ' + r.name + ' not continuous at ' + key(r.cells[i]));
  const perCircuit = [...laid].map(([c, s]) => ({circuit: c, cells: s.size, devices: devices.filter(d => d.circuit === c).length}));
  return {devices, runs, perCircuit, panelMount, meterMount, problems};
}

// ------------------------------------------------------------------------------------------------------- in game
async function stateAt(c, w) {
  const r = await c.call('get_horizontal_slice', {coordinate: w[1], encoding: 'palette', target: 'AUTHORING_SESSION', min: w, max: w});
  const id = r.rows[0][0];
  return Object.entries(r.palette).find(([, v]) => v === id)[0];
}

async function connect() {
  const c = new BridgeClient('./run');
  await c.call('minecraft_status');
  const info = await c.call('authoring_info');
  if (info.id !== ID) throw Error('PLOT_MISMATCH ' + JSON.stringify(info));
  const o = info.min, world = p => [o[0] + p[0], o[1] + p[1], o[2] + p[2]];
  const span = info.max.map((v, i) => v - o[i] + 1);
  if (key(span) !== key(SIZE)) throw Error('PLOT_SIZE ' + span + ' (expected ' + SIZE + ')');
  return {c, world};
}
async function report(mode, data) {
  const dir = path.resolve('build/authoring_checks', ID);
  await mkdir(dir, {recursive: true});
  await writeFile(path.join(dir, 'wiring_' + mode + '.json'), JSON.stringify(data, null, 2));
}

async function wire() {
  const p = await plan();
  if (p.problems.length) throw Error('PLAN_PROBLEMS ' + p.problems.join('; '));
  const {c, world} = await connect(), out = [];
  // the canopy edits first: each cell must still hold what the template has (or the edit already)
  const todo = [];
  for (const e of EDITS) {
    const s = await stateAt(c, world(e.at));
    if (s === stateOf(e) || (s.startsWith('apocalypse_firstlight:' + e.block) && e.was === e.block && s.includes('rod=true'))) { if (s !== stateOf(e)) todo.push(e); continue; }
    if (!s.includes(e.was)) throw Error('EDIT_PREFLIGHT ' + key(e.at) + ' holds ' + s + ', expected ' + e.was);
    todo.push(e);
  }
  if (todo.length) {
    await c.call('we_batch_set', {target: 'AUTHORING_SESSION', operations: todo.map(e => ({min: world(e.at), max: world(e.at), block: stateOf(e)}))});
    for (const e of todo) out.push('canopy ' + key(e.at) + ' -> ' + (await stateAt(c, world(e.at))));
  }
  const r0 = await c.call('wiring_read', {target: 'AUTHORING_SESSION', min: world(BOX.min), max: world(BOX.max)});
  if (r0.nodes.length) throw Error('ALREADY_WIRED: ' + r0.nodes.length + ' nodes in the store; clear them first (wiring_clear)');
  const before = await c.call('wiring_panel', {pos: world(PANEL), mode: 'wired'});
  out.push('panel mode -> ' + before.mode);
  for (const r of p.runs) {
    try {
      const res = await c.call('wiring_lay', {cells: r.cells.map(world), circuit: r.circuit});
      out.push('ok ' + r.name + ' (circuit ' + r.circuit + '): ' + res.cells + ' cells, ' + res.laid + ' new, taps ' + res.taps.map(t => key(t.device)).join(' '));
    } catch (e) { out.push('FAIL ' + r.name + ' ' + String(e.message || e)); console.log(out.join(NL)); await report('wire', {out}); process.exit(1); }
  }
  for (const d of p.devices) if (d.group) {
    try { const res = await c.call('wiring_group', {device: world(d.at), group: d.group}); out.push('group ' + d.name + ' -> ' + res.group); }
    catch (e) { out.push('FAIL group ' + d.name + ' ' + String(e.message || e)); }
  }
  const panel = await c.call('wiring_panel', {pos: world(PANEL), main: true});
  out.push('panel ' + JSON.stringify(panel));
  console.log(out.join(NL));
  await report('wire', {out, panel});
}

async function check() {
  const p = await plan();
  const {c, world} = await connect(), out = [], bad = [];
  const r = await c.call('wiring_read', {target: 'AUTHORING_SESSION', min: world(BOX.min), max: world(BOX.max)});
  const feeds = new Map(r.devices.map(d => [key(d.pos), d]));
  for (const d of p.devices) {
    const f = feeds.get(key(world(d.at)));
    const got = f?.feed ? 'circuit ' + f.feed.circuit + ' group ' + f.feed.group : 'no feed';
    const ok = f?.feed && f.feed.circuit === d.circuit && f.feed.group === d.group;
    (ok ? out : bad).push((ok ? 'ok ' : 'BAD ') + d.name + ': ' + got + (ok ? '' : ' (plan circuit ' + d.circuit + ' group ' + d.group + ')'));
  }
  const meter = feeds.get(key(world(METER)));
  (meter?.entry_panel ? out : bad).push('meter box -> panel ' + JSON.stringify(meter?.entry_panel ?? null));
  const panel = await c.call('wiring_panel', {pos: world(PANEL)});
  out.push('nodes ' + r.nodes.length + ', panel ' + JSON.stringify(panel));
  console.log([...bad, ...out].join(NL));
  await report('check', {bad, out, nodes: r.nodes.length, panel});
  if (bad.length) process.exit(1);
}

const direct = process.argv[1] && path.resolve(process.argv[1]).toLowerCase() === fileURLToPath(import.meta.url).toLowerCase();
const mode = direct ? process.argv[2] : null;
if (mode === 'plan') {
  const p = await plan();
  const lines = p.runs.map(r => 'circuit ' + r.circuit + ' ' + r.name + ': ' + r.cells.length + ' cells ' + key(r.cells[0]) + ' -> ' + key(r.cells.at(-1)));
  console.log(lines.join(NL));
  console.log(JSON.stringify({devices: p.devices.length, runs: p.runs.length, perCircuit: p.perCircuit, problems: p.problems}, null, 2));
  await report('plan', {devices: p.devices.map(d => ({name: d.name, circuit: d.circuit, group: d.group, mount: d.mount})), runs: p.runs, perCircuit: p.perCircuit, problems: p.problems});
  if (p.problems.length) process.exit(1);
} else if (mode === 'wire') await wire();
else if (mode === 'check') await check();
else if (direct) { console.log('usage: plan | wire | check'); process.exit(2); }
