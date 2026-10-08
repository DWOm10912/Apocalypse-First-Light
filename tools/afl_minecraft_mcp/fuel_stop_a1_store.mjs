// Fuel Stop A1 store module (gas_station_02_store) through the AFL authoring bridge: live authoring only, no export.
// Round 1 (2026-10-07, docs/worldgen/fuel_stop_a1_store_build_v1.md): the store shell, facade, doors, roof and basic interior;
// the sales floor revised the same day (central checkout island, docs/worldgen/fuel_stop_a1_design_v1.md 'Plan')
// from the A1 design page's block model (design/buildings/fuel_stop_a1/fuel_stop_a1.html) with the later revisions in
// docs/worldgen/fuel_stop_a1_design_v1.md; no tanks, canopy or fuel court. Pre-war intact state.
// Recipe frame ("paper", the design page): store X 0..26 east, Z 0..15 south (Z 0 the back wall, Z 15 the storefront),
// k = metres above G. Module = 31 x 10 x 20 (u 16..46, k -2..7, v 7..26), x = X + 2, z = Z + 1, y = k + 2, then normalised
// to the NORTH template frame (docs/worldgen/fuel_stop_a1_city_interface_v1.md 5.3): x' = 30 - x, z' = 19 - z, states turned 180.
//   node tools/afl_minecraft_mcp/fuel_stop_a1_store.mjs plan    -> counts only (no game)
//   node tools/afl_minecraft_mcp/fuel_stop_a1_store.mjs build   -> writes into the active plot (must be EMPTY-sized 31 x 10 x 20)
//   node tools/afl_minecraft_mcp/fuel_stop_a1_store.mjs furnish -> the fixtures in history-sized rounds (fresh build)
//   node tools/afl_minecraft_mcp/fuel_stop_a1_store.mjs relayout -> the first build's sales floor to the revised one (furniture only)
//   node tools/afl_minecraft_mcp/fuel_stop_a1_store.mjs tweak   -> the same-day tweak (12 lights, freezer to the east wall)
//   node tools/afl_minecraft_mcp/fuel_stop_a1_store.mjs check   -> offline route check of the revised sales floor
import {BridgeClient} from './bridge_client.mjs';
import {mkdir, writeFile} from 'node:fs/promises';
import path from 'node:path';

export const ID = 'gas_station_02_store', SIZE = [31, 10, 20];
const A = id => 'apocalypse_firstlight:' + id, M = id => 'minecraft:' + id;
const TURN = {north: 'south', south: 'north', east: 'west', west: 'east'};

export function recipe() {
  const cells = new Map(), fixtures = [];
  const key = (x, y, z) => x + ',' + y + ',' + z;
  // paper -> module NORTH frame
  const at = (X, k, Z) => { const x = 30 - (X + 2), y = k + 2, z = 19 - (Z + 1);
    if (x < 0 || x > 30 || y < 0 || y > 9 || z < 0 || z > 19) throw Error('OUT ' + [X, k, Z]); return [x, y, z]; };
  const turnState = s => s.replace(/facing=(north|south|east|west)/, (_, f) => 'facing=' + TURN[f]);
  const put = (X, k, Z, s) => cells.set(key(...at(X, k, Z)), turnState(s));
  const fill = (X0, k0, Z0, X1, k1, Z1, s) => { for (let k = k0; k <= k1; k++) for (let Z = Z0; Z <= Z1; Z++) for (let X = X0; X <= X1; X++) put(X, k, Z, s); };
  const fix = (tool, id, X, k, Z, facing, properties = {}) => fixtures.push({tool, block_id: A(id), pos: at(X, k, Z), facing: facing ? (TURN[facing] || facing) : undefined, properties});

  const base = f => A(`ground_face_block[facing=${f},shape=straight,cap=false]`);
  const BRICK = A('face_brick_warm_gray'), DARK = A('face_brick_charcoal');
  const glaze = (f, mullion, transom) => A(`storefront_glazing[facing=${f},left=false,right=false,up=false,down=false,mullion=${mullion},transom=${transom}]`);
  const cornice = (f, band = false) => A(`aluminum_cornice[facing=${f},shape=straight,band=${band}]`);
  const PANEL = A('metal_wall_panel[cap=false,north=false,east=false,south=false,west=false]');
  const jamb = f => A(`metal_panel_jamb[facing=${f},eyebrow=none]`);
  const canopy = (f, rod = false) => A(`metal_eyebrow_canopy[facing=${f},left=false,right=false,rod=${rod}]`);
  const SIDEWALK = A('road_sidewalk_surface[layers=16]'), FOOTING = A('reinforced_concrete');
  // placeholders (round 1): interior floor, partitions, ceiling / roof slab, subgrade
  const FLOOR = M('light_gray_concrete'), PART = M('white_concrete'), SLAB = M('smooth_quartz'), FILL = M('dirt');

  // ---- ground: k -2 fill, k -1 sidewalks round the store, footing under the walls, floor inside ----
  fill(-2, -2, -1, 28, -2, 18, FILL);
  fill(-2, -1, -1, 28, -1, 18, SIDEWALK);
  fill(0, -1, 0, 26, -1, 15, FOOTING);
  fill(1, -1, 1, 25, -1, 14, FLOOR);

  // ---- a wall column: base course k0 (unless glass or a door stands on the floor), then the k1..4 contents, cornice k5 ----
  const column = (X, Z, f, kinds, top = cornice(f)) => {   // kinds: 5 entries for k0..4 (null = leave empty)
    kinds.forEach((s, k) => { if (s) put(X, k, Z, s); }); if (top) put(X, 5, Z, top);
  };
  const plain = f => [base(f), BRICK, BRICK, BRICK, BRICK], pier = f => [base(f), DARK, DARK, DARK, DARK];

  // front wall (Z 15, outside = south)
  for (let X = 0; X <= 26; X++) {
    if ([0, 5, 21, 26].includes(X)) { column(X, 15, 'south', pier('south')); continue; }
    if ((X >= 1 && X <= 4) || (X >= 22 && X <= 25)) continue;   // the portals, below
    const mull = ['edge', 'center', 'none'][(X - 6) % 3];       // 1.5 m lites from X 6
    column(X, 15, 'south', [base('south'), glaze('south', mull, false), glaze('south', mull, true), glaze('south', mull, false), BRICK], cornice('south', true));
  }
  // entry portals: glazing round the 2 m door in the wall line, metal panel head 5..7 m over both rows, jamb plates in front
  for (const a of [1, 22]) {
    for (const X of [a, a + 3]) for (let k = 0; k <= 4; k++) put(X, k, 15, glaze('south', 'edge', k === 1 || k === 2));
    for (const X of [a + 1, a + 2]) for (let k = 2; k <= 4; k++) put(X, k, 15, glaze('south', 'edge', k === 2));
    fill(a, 5, 15, a + 3, 6, 16, PANEL);
    for (let k = 0; k <= 4; k++) { put(a, k, 16, jamb('west')); put(a + 3, k, 16, jamb('east')); }
    // the storefront door: anchor = lower-left master; the second leaf at facing.getClockWise() (west for south)
    fix('place_multiblock', 'commercial_glass_double_door_black', a + 2, 0, 15, 'south');
  }
  // side walls (X 0 west, X 26 east), Z 1..14
  for (const [X, f] of [[0, 'west'], [26, 'east']]) for (let Z = 1; Z <= 14; Z++) {
    if (Z === 5) { column(X, Z, f, pier(f)); continue; }
    if (Z >= 11) {   // front-corner glass, 1..4 m, transom at 3 m, 2 m lites (viewer's left: north on the west side, south on the east)
      const first = f === 'west' ? Z - 11 : 14 - Z;
      const m = first % 2 === 0 ? 'edge' : 'none';
      column(X, Z, f, [base(f), glaze(f, m, false), glaze(f, m, true), glaze(f, m, false), BRICK]); continue;
    }
    if (f === 'east' && (Z === 2 || Z === 3)) { column(X, Z, f, [base(f), glaze(f, 'edge', false), glaze(f, 'edge', false), BRICK, BRICK]); continue; }   // office window 1..3 m
    if (f === 'west' && Z <= 4) { column(X, Z, f, [base(f), BRICK, BRICK, glaze(f, 'edge', false), BRICK]); continue; }   // restroom high window 3..4 m
    column(X, Z, f, plain(f));
  }
  // rear wall (Z 0, outside = north): piers at X 0, 9, 17, 26; equipment door X 7; stock double door X 19..20
  for (let X = 0; X <= 26; X++) {
    if ([0, 9, 17, 26].includes(X)) { column(X, 0, 'north', pier('north')); continue; }
    if (X === 7 || X === 19 || X === 20) { column(X, 0, 'north', [null, null, BRICK, BRICK, BRICK]); continue; }
    column(X, 0, 'north', plain('north'));
  }
  fix('place_multiblock', 'steel_door', 7, 0, 0, 'south', {hinge: 'left'});
  fix('place_multiblock', 'steel_door', 19, 0, 0, 'south', {hinge: 'right'});
  fix('place_multiblock', 'steel_door', 20, 0, 0, 'south', {hinge: 'left'});
  // the corner cells of the cornice take their facing from the front / rear wall; reconcile_shapes turns them into outer corners

  // ---- eyebrow canopies (3 m; the rear one over the stock door at 2 m) ----
  for (let X = 5; X <= 21; X++) put(X, 3, 16, canopy('south', [7, 10, 13, 16, 19].includes(X)));
  for (const a of [1, 22]) for (const X of [a + 1, a + 2]) put(X, 3, 16, canopy('south'));
  for (let Z = 11; Z <= 14; Z++) { put(-1, 3, Z, canopy('west')); put(27, 3, Z, canopy('east')); }
  for (let X = 18; X <= 21; X++) put(X, 2, -1, canopy('north'));

  // ---- roof / ceiling slab (k4: ceiling at 4 m, roof at 5 m, behind the 1 m parapet) ----
  fill(1, 4, 1, 25, 4, 14, SLAB);

  // ---- interior partitions (to the 4 m ceiling) ----
  for (let X = 1; X <= 25; X++) {
    if ([2, 4, 22].includes(X)) { put(X, 2, 5, PART); put(X, 3, 5, PART); continue; }   // doors below
    if (X >= 10 && X <= 15) { put(X, 2, 5, PART); put(X, 3, 5, PART); continue; }       // beverage coolers below
    fill(X, 0, 5, X, 3, 5, PART);
  }
  for (const X of [3, 6, 9, 16, 23]) for (let Z = 1; Z <= 4; Z++) {
    if ((X === 16 && Z === 2) || (X === 23 && Z === 3)) { put(X, 2, Z, PART); put(X, 3, Z, PART); continue; }
    fill(X, 0, Z, X, 3, Z, PART);
  }
  // interior doors
  for (const X of [2, 4, 22]) fix('place_multiblock', 'poplar_door', X, 0, 5, 'north', {hinge: 'left'});
  fix('place_multiblock', 'poplar_door', 16, 0, 2, 'west', {hinge: 'left'});
  fix('place_multiblock', 'poplar_door', 23, 0, 3, 'east', {hinge: 'left'});

  // ---- back of house ----
  for (const [X, sink] of [[1, 2], [5, 4]]) { fix('place_fixture', 'commercial_flushometer_toilet', X, 0, 1, 'south'); fix('place_multiblock', 'commercial_wall_mounted_sink', sink, 0, 1, 'south'); }
  fix('place_fixture', 'industrial_electrical_box', 8, 1, 1, 'south');
  for (let X = 10; X <= 15; X++) fix('place_multiblock', 'storage_rack', X, 0, 1, 'south');
  for (let X = 17; X <= 21; X++) fix('place_multiblock', 'storage_rack', X, 0, 4, 'north');
  // round 1 stopped here (the bridge history is full after 32 entries); the remaining racks and everything else: steps()
  return {cells, fixtures};
}

// ---------------- batches: runs along x of one state, at most 128 ops per call ----------------
export function batches(cells) {
  const byRow = new Map();
  for (const [k, s] of cells) { const [x, y, z] = k.split(',').map(Number), r = y + ',' + z; (byRow.get(r) || byRow.set(r, []).get(r)).push([x, s]); }
  const ops = [];
  for (const [r, list] of [...byRow].sort((a, b) => { const [ya, za] = a[0].split(',').map(Number), [yb, zb] = b[0].split(',').map(Number); return ya - yb || za - zb; })) {
    const [y, z] = r.split(',').map(Number); list.sort((a, b) => a[0] - b[0]);
    let i = 0; while (i < list.length) { let j = i; while (j + 1 < list.length && list[j + 1][0] === list[j][0] + 1 && list[j + 1][1] === list[i][1]) j++;
      ops.push({min: [list[i][0], y, z], max: [list[j][0], y, z], block: list[i][1]}); i = j + 1; }
  }
  const out = []; for (let i = 0; i < ops.length; i += 128) out.push(ops.slice(i, i + 128));
  return out;
}

async function build() {
  const c = new BridgeClient('./run'), dir = path.resolve('build/authoring_checks', ID);
  const status = await c.call('minecraft_status'), info = await c.call('authoring_info');
  if (info.id !== ID || info.width !== SIZE[0] || info.height !== SIZE[1] || info.depth !== SIZE[2]) throw Error('PLOT_MISMATCH ' + JSON.stringify(info));
  const o = info.min, world = ([x, y, z]) => [o[0] + x, o[1] + y, o[2] + z];
  const {cells, fixtures} = recipe(), report = {plot: info, world: status.world, writes: [], fixtures: [], failures: []};
  for (const ops of batches(cells)) {
    const r = await c.call('we_batch_set', {target: 'AUTHORING_SESSION', operations: ops.map(op => ({min: world(op.min), max: world(op.max), block: op.block}))});
    report.writes.push({ops: ops.length, changed: r.changed ?? r.blocks_changed ?? null});
  }
  for (let pass = 0; pass < 2; pass++) report['reconcile_' + pass] = (await c.call('reconcile_shapes', {target: 'AUTHORING_SESSION'})).reconciled;
  for (const f of fixtures) {
    const args = {block_id: f.block_id, ...(f.tool === 'place_multiblock' ? {anchor: world(f.pos)} : {pos: world(f.pos)}), ...(f.facing ? {facing: f.facing} : {}),
      ...(Object.keys(f.properties).length ? {properties: f.properties} : {})};
    try { const r = await c.call(f.tool, args); report.fixtures.push({id: f.block_id, at: args.anchor || args.pos, ok: true, warnings: r.warnings}); }
    catch (e) { report.failures.push({id: f.block_id, at: args.anchor || args.pos, facing: f.facing, error: String(e.message || e)}); }
  }
  report.reconcile_final = (await c.call('reconcile_shapes', {target: 'AUTHORING_SESSION'})).reconciled;
  const audit = await c.call('audit_support', {target: 'AUTHORING_SESSION'});
  report.audit = {issue_count: audit.issue_count, counts: audit.counts, issues: audit.issues?.slice(0, 40)};
  await mkdir(dir, {recursive: true}); await writeFile(path.join(dir, 'build_round1.json'), JSON.stringify(report, null, 1));
  console.log(JSON.stringify({batches: report.writes.length, cells: cells.size, fixtures: report.fixtures.length, failures: report.failures, reconcile: [report.reconcile_0, report.reconcile_1, report.reconcile_final], audit: report.audit.counts}, null, 1));
}

// ---------------- furnishing in history-sized rounds ----------------
// The bridge keeps at most 32 undo entries per reservation (BridgeHistory.reserve), and every fixture placement is one; the
// first build round used all of them. The rest is placed in steps (one entry each), repeated fixtures by placing one and
// copying it with we_stack. Each run does at most 31 steps (one entry left for reconcile_shapes) and records its progress;
// after a run the reservation is cancelled and re-created (/afl_author resume ...) to clear the history.
export function steps() {
  const S = [], P = (id, X, k, Z, facing, properties = {}, multi = false) => S.push({kind: multi ? 'place_multiblock' : 'place_fixture', id, at: [X, k, Z], facing, properties});
  const ST = (from, to, d, count) => S.push({kind: 'we_stack', from, to, d, count});
  P('storage_rack', 21, 0, 4, 'north', {}, true); for (const Z of [1, 2]) P('storage_rack', 22, 0, Z, 'west', {}, true);
  salesLights(P, ST);
  // sales floor (2026-10-07 revision): two double-sided gondola runs, coolers, coffee / hot food on the side walls, the island
  for (const [X, f] of [[4, 'west'], [5, 'east'], [21, 'west'], [22, 'east']]) P('retail_shelf_single', X, 0, 8, f, {}, true);
  ST([4, 0, 8], [5, 1, 8], [0, 0, 1], 4); ST([21, 0, 8], [22, 1, 8], [0, 0, 1], 4);
  P('beverage_cooler', 10, 0, 5, 'south', {}, true); ST([10, 0, 5], [11, 1, 5], [2, 0, 0], 2);
  P('checkout_counter', 1, 0, 8, 'east'); ST([1, 0, 8], [1, 0, 8], [0, 0, 1], 4);             // coffee bar (placeholder)
  P('checkout_counter_display', 25, 0, 8, 'west'); ST([25, 0, 8], [25, 0, 8], [0, 0, 1], 4); // hot food (placeholder)
  island(P, ST);
  P('chest_freezer', 25, 0, 6, 'west', {}, true);                // east wall by the coolers; second part at facing.ccw (south): Z 7
  P('modern_office_desk', 25, 0, 2, 'west', {}, true); P('modern_office_chair', 24, 0, 2, 'east');
  P('office_computer_station', 25, 1, 2, 'west'); P('low_filing_cabinet', 25, 0, 4, 'west');
  for (const [X, Z] of [[1, 3], [4, 3], [8, 3]]) P('industrial_utility_light', X, 3, Z, 'down');
  P('industrial_utility_light', 12, 3, 3, 'down'); ST([12, 3, 3], [12, 3, 3], [2, 0, 0], 1);
  P('industrial_utility_light', 19, 3, 2, 'down'); ST([19, 3, 2], [19, 3, 2], [2, 0, 0], 1);
  P('industrial_utility_light', 24, 3, 3, 'down');
  P('industrial_electrical_box', 6, 1, -1, 'north');
  for (const X of [0, 26]) P('metal_trash_can', X, 0, 16, 'south');
  P('fuel_island_bollard', 6, 0, 18); P('fuel_island_bollard', 8, 0, 18); ST([6, 0, 18], [8, 0, 18], [5, 0, 0], 2); P('fuel_island_bollard', 21, 0, 18);
  return S;
}
// The central checkout island (paper frame): Z 8 back-to-back shelves facing the cooler aisle, Z 9 back bar, Z 10 the 1 m
// cashier aisle, Z 11 the 10 m counter (POS at X 10 and X 16; X 13 kept for a third POS); two impulse counters close the
// west end facing the west aisle, the staff gate the east end (hinge right: its flap folds onto the counter at X 18, Z 11).
// (The ice cream freezer stood at the west end first; it moved to the east wall the same day.)
// sales-floor ceiling lights (2026-10-07: 12, was 18): X 3, 8, 13, 18, 23 x Z 8, 12, and two over the cashier aisle
export function salesLights(P, ST) {
  P('industrial_utility_light', 3, 3, 8, 'down'); ST([3, 3, 8], [3, 3, 8], [5, 0, 0], 4); ST([3, 3, 8], [23, 3, 8], [0, 0, 4], 1);
  P('industrial_utility_light', 11, 3, 10, 'down'); ST([11, 3, 10], [11, 3, 10], [4, 0, 0], 1);
}
export function island(P, ST) {
  P('retail_shelf_single', 9, 0, 8, 'north', {}, true); ST([9, 0, 8], [9, 1, 8], [1, 0, 0], 9);
  P('back_bar_shelf', 9, 0, 9, 'south', {}, true); ST([9, 0, 9], [9, 1, 9], [1, 0, 0], 9);
  P('checkout_counter_display', 9, 0, 11, 'south'); P('checkout_counter', 10, 0, 11, 'south');
  P('checkout_counter_display', 11, 0, 11, 'south'); ST([11, 0, 11], [11, 0, 11], [1, 0, 0], 4);
  P('checkout_counter', 16, 0, 11, 'south');
  P('checkout_counter_display', 17, 0, 11, 'south'); ST([17, 0, 11], [17, 0, 11], [1, 0, 0], 1);
  P('checkout_counter_gate', 18, 0, 10, 'east', {hinge: 'right'});
  P('checkout_counter_display', 8, 0, 10, 'west'); P('checkout_counter_display', 8, 0, 11, 'west');
  P('cash_register', 10, 1, 11, 'north'); P('cash_register', 16, 1, 11, 'north');
}
// the first build's sales-floor pieces that move (paper boxes, inclusive): freezer, back bar, counter + gate + registers,
// the single shelf runs X 8 / X 18, and the Z 13 ends of the coffee bar and hot food
export const OLD_SALES = [[[12, 0, 8], [13, 0, 8]], [[11, 0, 10], [15, 1, 10]], [[11, 0, 12], [15, 1, 12]], [[8, 0, 8], [8, 1, 12]], [[18, 0, 8], [18, 1, 12]],
  [[1, 0, 13], [1, 0, 13]], [[25, 0, 13], [25, 0, 13]]];
// the tweak of the same day: the 18 sales lights and the island-end freezer out; the freezer to the east wall, the island
// end closed with impulse counters, 12 lights
export function tweakSteps() {
  const S = [], P = (id, X, k, Z, facing, properties = {}, multi = false) => S.push({kind: multi ? 'place_multiblock' : 'place_fixture', id, at: [X, k, Z], facing, properties});
  const ST = (from, to, d, count) => S.push({kind: 'we_stack', from, to, d, count});
  S.push({kind: 'clear', boxes: [[[1, 3, 6], [25, 3, 14]], [[8, 0, 10], [8, 0, 11]]]});
  P('chest_freezer', 25, 0, 6, 'west', {}, true);
  P('checkout_counter_display', 8, 0, 10, 'west'); P('checkout_counter_display', 8, 0, 11, 'west');
  salesLights(P, ST);
  return S;
}
export function relayoutSteps() {
  const S = [], P = (id, X, k, Z, facing, properties = {}, multi = false) => S.push({kind: multi ? 'place_multiblock' : 'place_fixture', id, at: [X, k, Z], facing, properties});
  const ST = (from, to, d, count) => S.push({kind: 'we_stack', from, to, d, count});
  S.push({kind: 'clear', boxes: OLD_SALES});
  P('checkout_counter', 1, 0, 8, 'east'); P('checkout_counter_display', 25, 0, 8, 'west');
  island(P, ST);
  return S;
}
const paper = (X, k, Z) => [30 - (X + 2), k + 2, 19 - (Z + 1)];
const turned = f => f && TURN[f] ? TURN[f] : f;

async function furnish() {
  const c = new BridgeClient('./run'), dir = path.resolve('build/authoring_checks', ID), file = path.join(dir, 'furnish_progress.json');
  await c.call('minecraft_status'); const info = await c.call('authoring_info');
  if (info.id !== ID || info.width !== SIZE[0] || info.height !== SIZE[1] || info.depth !== SIZE[2]) throw Error('PLOT_MISMATCH ' + JSON.stringify(info));
  const o = info.min, world = ([x, y, z]) => [o[0] + x, o[1] + y, o[2] + z];
  let prog; try { prog = JSON.parse(await (await import('node:fs/promises')).readFile(file, 'utf8')); } catch { prog = {done: [], failures: []}; }
  const all = steps(), log = [];
  let used = 0;
  for (let i = 0; i < all.length && used < 31; i++) {
    if (prog.done.includes(i)) continue;
    const s = all[i];
    try {
      if (s.kind === 'we_stack') {
        const a = paper(...s.from), b = paper(...s.to), min = [0, 1, 2].map(k => Math.min(a[k], b[k])), max = [0, 1, 2].map(k => Math.max(a[k], b[k]));
        await c.call('we_stack', {target: 'AUTHORING_SESSION', min: world(min), max: world(max), offset: [-s.d[0], s.d[1], -s.d[2]], count: s.count});
      } else {
        const pos = world(paper(...s.at)), args = {block_id: A(s.id), ...(s.kind === 'place_multiblock' ? {anchor: pos} : {pos}),
          ...(s.facing ? {facing: turned(s.facing)} : {}), ...(Object.keys(s.properties).length ? {properties: s.properties} : {})};
        await c.call(s.kind, args);
      }
      prog.done.push(i); used++; log.push(i + ' ok ' + (s.id || 'stack'));
    } catch (e) {
      const msg = String(e.message || e); log.push(i + ' FAIL ' + (s.id || 'stack') + ' ' + msg);
      if (msg.startsWith('HISTORY_LIMIT')) break;
      prog.failures.push({i, step: s, error: msg}); prog.done.push(i);   // a rule failure is recorded, not retried
    }
  }
  const rec = await c.call('reconcile_shapes', {target: 'AUTHORING_SESSION'}), audit = await c.call('audit_support', {target: 'AUTHORING_SESSION'});
  await mkdir(dir, {recursive: true}); await writeFile(file, JSON.stringify(prog, null, 1));
  console.log(JSON.stringify({used, remaining: all.length - prog.done.length, reconciled: rec.reconciled, audit: audit.counts, log}, null, 1));
}

async function relayout(list = relayoutSteps(), report = 'relayout_sales_v2.json') {
  const c = new BridgeClient('./run'), dir = path.resolve('build/authoring_checks', ID);
  await c.call('minecraft_status'); const info = await c.call('authoring_info');
  if (info.id !== ID || info.width !== SIZE[0] || info.height !== SIZE[1] || info.depth !== SIZE[2]) throw Error('PLOT_MISMATCH ' + JSON.stringify(info));
  const o = info.min, world = ([x, y, z]) => [o[0] + x, o[1] + y, o[2] + z], log = [];
  const box = (p, q) => { const a = paper(...p), b = paper(...q); return [[0, 1, 2].map(k => Math.min(a[k], b[k])), [0, 1, 2].map(k => Math.max(a[k], b[k]))]; };
  for (const st of list) {
    try {
      if (st.kind === 'clear') {
        await c.call('we_batch_set', {target: 'AUTHORING_SESSION', operations: st.boxes.map(([p, q]) => { const [mn, mx] = box(p, q); return {min: world(mn), max: world(mx), block: 'minecraft:air'}; })});
        log.push('ok clear ' + st.boxes.length);
      } else if (st.kind === 'we_stack') {
        const [mn, mx] = box(st.from, st.to);
        await c.call('we_stack', {target: 'AUTHORING_SESSION', min: world(mn), max: world(mx), offset: [-st.d[0], st.d[1], -st.d[2]], count: st.count}); log.push('ok stack');
      } else {
        const pos = world(paper(...st.at));
        await c.call(st.kind, {block_id: A(st.id), ...(st.kind === 'place_multiblock' ? {anchor: pos} : {pos}), facing: turned(st.facing),
          ...(Object.keys(st.properties).length ? {properties: st.properties} : {})});
        log.push('ok ' + st.id);
      }
    } catch (e) { log.push('FAIL ' + (st.id || st.kind) + ' ' + String(e.message || e)); if (String(e.message || e).startsWith('HISTORY_LIMIT') || st.kind === 'clear') break; }
  }
  const r1 = await c.call('reconcile_shapes', {target: 'AUTHORING_SESSION'}), audit = await c.call('audit_support', {target: 'AUTHORING_SESSION'});
  await mkdir(dir, {recursive: true}); await writeFile(path.join(dir, report), JSON.stringify({log, reconciled: r1.reconciled, audit}, null, 1));
  console.log(JSON.stringify({log, reconciled: r1.reconciled, audit: audit.counts, issues: audit.issues?.slice(0, 10)}, null, 1));
}

// Offline route check of the revised sales floor (paper grid, 1 m cells, k0): who can reach what, and the queue case.
export function routeCheck() {
  const blocked = new Set(), staffOnly = new Set(), k = (X, Z) => X + ',' + Z;
  for (let X = 0; X <= 26; X++) { blocked.add(k(X, 0)); blocked.add(k(X, 15)); }
  for (let Z = 0; Z <= 15; Z++) { blocked.add(k(0, Z)); blocked.add(k(26, Z)); }
  for (const X of [2, 3, 23, 24]) blocked.delete(k(X, 15));                        // the two storefront doors
  for (let X = 1; X <= 25; X++) for (let Z = 1; Z <= 5; Z++) blocked.add(k(X, Z));   // back of house + partition + coolers
  const fix = (X0, Z0, X1, Z1) => { for (let X = X0; X <= X1; X++) for (let Z = Z0; Z <= Z1; Z++) blocked.add(k(X, Z)); };
  fix(4, 8, 5, 12); fix(21, 8, 22, 12); fix(1, 8, 1, 12); fix(25, 8, 25, 12);       // gondolas, coffee, hot food
  fix(9, 8, 18, 9); fix(9, 11, 18, 11); fix(8, 10, 8, 11);                           // island: shelves + back bar, counter, west-end counters
  fix(25, 6, 25, 7);                                                                  // ice cream freezer, east wall
  staffOnly.add(k(18, 10));                                                           // the gate
  const bfs = (from, staff, extra = new Set()) => { const d = new Map([[k(...from), 0]]), q = [from];
    while (q.length) { const [X, Z] = q.shift(); for (const [dx, dz] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) { const n = [X + dx, Z + dz], kk = k(...n);
      if (n[0] < 0 || n[0] > 26 || n[1] < 0 || n[1] > 16 || d.has(kk) || blocked.has(kk) || extra.has(kk) || (!staff && staffOnly.has(kk))) continue; d.set(kk, d.get(k(X, Z)) + 1); q.push(n); } }
    return d; };
  const targets = {pos_west: [10, 12], pos_east: [16, 12], coolers: [12, 6], coffee: [2, 10], hot_food: [24, 10], freezer: [24, 6], island_west_end: [7, 10], west_gondola_w: [3, 10], west_gondola_e: [6, 10],
    east_gondola_w: [20, 10], east_gondola_e: [23, 10], island_back_shelves: [13, 7], restroom_doors: [3, 6], stock_door_front: [22, 6]};
  const out = {};
  for (const [door, at] of [['west_door', [2, 15]], ['east_door', [24, 15]]]) { const d = bfs(at, false); out[door] = Object.fromEntries(Object.entries(targets).map(([n, t]) => [n, d.get(k(...t)) ?? 'UNREACHABLE'])); }
  const staff = bfs([22, 6], true); out.staff_stock_door_to_cashier_aisle = {gate: staff.get(k(18, 10)) ?? 'UNREACHABLE', pos_west_operator: staff.get(k(10, 10)) ?? 'UNREACHABLE', pos_east_operator: staff.get(k(16, 10)) ?? 'UNREACHABLE'};
  out.customer_into_cashier_aisle = bfs([2, 15], false).has(k(13, 10)) ? 'POSSIBLE (bad)' : 'blocked (gate / end counters close the ends)';
  // queue case: two people in each line (Z 12, Z 13 at X 10 and X 16): door to door along the front still open?
  const queue = new Set([k(10, 12), k(10, 13), k(16, 12), k(16, 13)]);
  out.door_to_door_with_queues = bfs([2, 15], false, queue).get(k(24, 15)) ?? 'UNREACHABLE';
  out.door_to_door_free = bfs([2, 15], false).get(k(24, 15));
  return out;
}

const mode = process.argv[2];
if (mode === 'plan') { const {cells, fixtures} = recipe(); console.log(JSON.stringify({cells: cells.size, batches: batches(cells).length, ops: batches(cells).flat().length, fixtures: fixtures.length, steps: steps().length})); }
else if (mode === 'build') await build();
else if (mode === 'furnish') await furnish();
else if (mode === 'relayout') await relayout();
else if (mode === 'tweak') await relayout(tweakSteps(), 'tweak_sales_v3.json');
else if (mode === 'check') console.log(JSON.stringify(routeCheck(), null, 1));
