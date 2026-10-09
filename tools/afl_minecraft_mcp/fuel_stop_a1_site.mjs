// Fuel Stop A1 site ground (gas_station_02_site) through the AFL authoring bridge: live authoring only, no export.
// docs/worldgen/fuel_stop_a1_site_build_v1.md. 2026-10-08 (user: "接下来是不是应该先把地基铺好，现在加油站的其他地方还是草坪"):
// the 64 x 64 lot's surface layer outside the store module and the fuel court, from the design page's site plan
// (design/buildings/fuel_stop_a1/fuel_stop_a1.html GROUND, with the city-interface round: the east entrance E2 removed).
//   asphalt  u 3..59 x v 1..59 (parking rows and drive aisles), the main-road entrance S1 u 8..16 x v 60..63, the service
//            entrance E1 u 60..63 x v 1..6
//   concrete_pavement  the dumpster pad u 55..59 x v 7..12 (garbage trucks; axis z)
//   grass stays: the green areas beside the canopy (west u 3..8 x v 33..50, east u 54..59 x v 32..50) and the lot's edges
//            outside the asphalt; the parking-row end islands are paved until curbs and mesh trees exist
// Lot frame as in fuel_stop_a1_forecourt.mjs: u east, v south, k metres above G (k -1 the surface layer); world x = -50 - u,
// y = -51 + k, z = 474 - v. Only k -1 is written (the subgrade stays: the store feeder cable runs at k -2 under the
// parking strip, u 31 v 27..37), and plants standing on newly paved cells at k 0 are cleared.
// The plot covers the whole lot (u 0..63, v 0..63, k -1..0); the store module (u 16..46 x v 7..26) and the fuel court
// (u 10..52 x v 38..59) are left alone. Before writing, the k -1 layer is read and anything but grass, dirt, air or an earlier
// copy of this ground stops the script.
//   node tools/afl_minecraft_mcp/fuel_stop_a1_site.mjs plan   -> offline: cell counts, the resume command
//   node tools/afl_minecraft_mcp/fuel_stop_a1_site.mjs build  -> checks and paves the active site plot
//   node tools/afl_minecraft_mcp/fuel_stop_a1_site.mjs markings -> the parking-lot markings (docs/models/pavement_markings_v1.md
//            plan; see markings()), at k 0 on the paved ground, after checking every cell is air or an earlier marking
//   node tools/afl_minecraft_mcp/fuel_stop_a1_site.mjs curbs  -> Curbs V1 (docs/models/curbs_v1.md): reads k -1 and turns every
//            walk / grass cell that meets asphalt or concrete pavement (and the inner-corner cells) into curb_sidewalk /
//            curb_grass; the four ramp cells in front of the store are flush (yellow warning). The world is read, not the
//            design, since the user edits the lot by hand.
import {BridgeClient} from './bridge_client.mjs';
import {mkdir, writeFile} from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

export const ID = 'gas_station_02_site';
export const LOT = {u0: 0, u1: 63, k0: -1, k1: 0, v0: 0, v1: 63};
export const SIZE = [LOT.u1 - LOT.u0 + 1, LOT.k1 - LOT.k0 + 1, LOT.v1 - LOT.v0 + 1];   // width (x), height, depth (z)
export const toWorld = (u, k, v) => [-50 - u, -51 + k, 474 - v];
export const ORIGIN = toWorld(LOT.u1, LOT.k0, LOT.v1);
export const RESUME = `/afl_author resume ${ID} ${ORIGIN.join(' ')} ${SIZE[0]} ${SIZE[2]} ${SIZE[1]} ${-LOT.k0}`;
const A = id => 'apocalypse_firstlight:' + id;
const ASPHALT = A('asphalt'), PAD = A('concrete_pavement[axis=z]');

const inBox = (u, v, [u0, v0, u1, v1]) => u >= u0 && u <= u1 && v >= v0 && v <= v1;
const KEEP = [[16, 7, 46, 26], [10, 38, 52, 59]];   // the store module and the fuel court: built by their own scripts
// the green areas beside the canopy; the design's parking-row end islands (u 18 / u 44 at v 27..32, u 3..8 at v 7..8, u 54..59
// at v 13) are paved for now (user 2026-10-08: no vanilla trees, trees come back as meshes; the islands come back curbed and
// stall-sized with them)
const GRASS = [[3, 33, 8, 50], [54, 32, 59, 50]];
const PADS = [[55, 7, 59, 12]];
const PAVED = [[3, 1, 59, 59], [8, 60, 16, 63], [60, 1, 63, 6]];

/** The surface block of every lot cell this script owns (lot u,v -> state), the rest stays as it is. */
export function ground() {
  const cells = new Map();
  for (let v = LOT.v0; v <= LOT.v1; v++) for (let u = LOT.u0; u <= LOT.u1; u++) {
    if (KEEP.some(b => inBox(u, v, b)) || GRASS.some(b => inBox(u, v, b))) continue;
    if (PADS.some(b => inBox(u, v, b))) cells.set(u + ',' + v, PAD);
    else if (PAVED.some(b => inBox(u, v, b))) cells.set(u + ',' + v, ASPHALT);
  }
  return cells;
}

// ---- parking-lot markings (Pavement Markings V1 plan, 2026-10-08) ----
// Directions: lot east = world west, lot north = world south (A1 is turned 180 degrees in the dev world).
const WORLD = {east: 'west', west: 'east', north: 'south', south: 'north'};
const EDGE = {white: A('edge_lane_white'), blue: A('edge_lane_blue')};
const edge = (colour, lotSide) => `${EDGE[colour]}[connections=0,facing=${WORLD[lotSide]},rises=0]`;
const LOT_STEP = {north: [0, -1], south: [0, 1], east: [1, 0], west: [-1, 0]};
/**
 * The marking at each lot cell (u,v -> state), from the concept page (refs/marking_concept.html, a1_markings_concept_v1.png):
 * stall lines on cell edges (12.5 cm), each in the stall-side cell where an aisle or hatch holds the other; the accessible
 * stalls and their hatched access aisles in blue, the symbol in each accessible stall; continental crosswalks (0.5 m bars,
 * one per metre) across the front aisle in line with the access aisles; at both entrances an exit stop bar and in / out
 * arrows (traffic keeps right); the tanker unloading zone yellow cross-hatched with the fill covers left clear.
 */
export function markings() {
  const m = new Map(), put = (u, v, state) => { const k = u + ',' + v; if (m.has(k)) throw Error('MARKING_OVERLAP ' + k + ' ' + m.get(k) + ' / ' + state); m.set(k, state); };
  // front row v 27..32: lines at x = 19 .. 44
  const frontLines = [[19, 19, 'west', 'blue'], [22, 21, 'east', 'blue'], [24, 24, 'west', 'blue'], [27, 27, 'west', 'white'], [30, 30, 'west', 'white'],
    [33, 33, 'west', 'white'], [36, 36, 'west', 'white'], [39, 38, 'east', 'blue'], [41, 41, 'west', 'blue'], [44, 43, 'east', 'blue']];
  for (const [, u, side, colour] of frontLines) for (let v = 27; v <= 32; v++) put(u, v, edge(colour, side));
  for (const u of [20, 42]) put(u, 30, A(`pavement_accessible_symbol[facing=${WORLD.north}]`));   // upright for someone in the aisle looking at the store
  // access aisles: blue hatching (one facing, so the corners follow the field)
  const hatch = new Set(); for (const a of [22, 39]) for (let u = a; u <= a + 1; u++) for (let v = 27; v <= 32; v++) hatch.add(u + ',' + v);
  const corners = (set, u, v, cross) => { const n = set.has(u + ',' + (v + 1)), sth = set.has(u + ',' + (v - 1)), e = set.has((u - 1) + ',' + v), w = set.has((u + 1) + ',' + v);
    // facing north (world): model north / east = world north / east = lot south / west
    return `ne=${n || e},sw=${sth || w},nw=${cross && (n || w)},se=${cross && (sth || e)}`; };
  for (const k of hatch) { const [u, v] = k.split(',').map(Number); put(u, v, A(`pavement_hatch[color=blue,facing=north,${corners(hatch, u, v, false)}]`)); }
  // crosswalks across the front aisle (v 33..39), bars along the traffic (lot u = world x)
  for (const a of [22, 39]) for (let u = a; u <= a + 1; u++) for (let v = 33; v <= 39; v++) put(u, v, A('pavement_bar[color=white,facing=north]'));
  // west row (v 9..33, u 3..8) and east row (v 14..32, u 54..59): lines across the row at every 3 m, in the cell north of the line
  for (let z = 9; z <= 33; z += 3) for (let u = 3; u <= 8; u++) put(u, z - 1, edge('white', 'south'));
  for (let z = 14; z <= 32; z += 3) for (let u = 54; u <= 59; u++) put(u, z - 1, edge('white', 'south'));
  // entrances: S1 (u 8..16, from the main road to the south; in lane east, out lane west), E1 (v 1..6, from the side road to
  // the east; in lane north, out lane south)
  const arrow = (tail, lotDir) => { const [du, dv] = LOT_STEP[lotDir]; for (let f = 0; f <= 2; f++) put(tail[0] + du * f, tail[1] + dv * f, A(`pavement_arrow[facing=${WORLD[lotDir]},kind=straight,part=${2 - f}]`)); };
  arrow([9, 60], 'south'); arrow([15, 62], 'north');
  for (let u = 8; u <= 11; u++) put(u, 63, A('pavement_bar[color=white,facing=north]'));
  arrow([62, 2], 'west'); arrow([60, 5], 'east');
  for (let v = 4; v <= 6; v++) put(63, v, A('pavement_bar[color=white,facing=east]'));
  // tanker unloading zone on the tank pad: yellow cross-hatch, the fill covers (v 58 at u 24 / 30 / 36) left clear
  const zone = new Set(); for (let u = 23; u <= 39; u++) for (let v = 56; v <= 59; v++) if (!(v === 58 && [24, 30, 36].includes(u))) zone.add(u + ',' + v);
  for (const k of zone) { const [u, v] = k.split(',').map(Number); put(u, v, A(`pavement_crosshatch[color=yellow,facing=north,${corners(zone, u, v, true)}]`)); }
  return m;
}

// ---- curbs (Curbs V1, docs/models/curbs_v1.md, 2026-10-09) ----
const ROAD_IDS = new Set([ASPHALT, A('concrete_pavement')]);
const RAMPS = new Set(['22,26', '23,26', '39,26', '40,26']);   // in line with the hatched access aisles and the crosswalks
const curbKind = id => ROAD_IDS.has(id) ? 'road' : id === A('concrete_sidewalk') || id === A('curb_sidewalk') ? 'walk'
  : id === 'minecraft:grass_block' || id === A('curb_grass') ? 'grass' : null;
/**
 * The curb cells (lot "u,v" -> {kind, level}) of a k -1 surface (lot "u,v" -> block id), with the block's own rule
 * (block/CurbGeometry): a walk or grass cell with a road neighbour carries the curb; a cell with none whose road diagonal has
 * walk / grass on both sides holds the inner-corner post. Only lot cells (the plot) change.
 */
export function curbPlan(surface) {
  const at = (u, v) => curbKind(surface.get(u + ',' + v)), ped = k => k === 'walk' || k === 'grass', out = new Map();
  for (let v = LOT.v0; v <= LOT.v1; v++) for (let u = LOT.u0; u <= LOT.u1; u++) {
    const k = at(u, v); if (!ped(k)) continue;
    const edge = [[0, -1], [1, 0], [0, 1], [-1, 0]].some(([du, dv]) => at(u + du, v + dv) === 'road');
    const inner = !edge && [[1, 1], [1, -1], [-1, 1], [-1, -1]].some(([du, dv]) => at(u + du, v + dv) === 'road' && ped(at(u + du, v)) && ped(at(u, v + dv)));
    if (edge || inner) out.set(u + ',' + v, {kind: k, edge, level: k === 'walk' && RAMPS.has(u + ',' + v) ? 'flush' : 'full'});
  }
  return out;
}
/** The surface the scripts lay (for the offline plan): this ground, the store's walk ring and floor, the fuel court, grass. */
export function designSurface() {
  const g = ground(), m = new Map();
  for (let v = LOT.v0; v <= LOT.v1; v++) for (let u = LOT.u0; u <= LOT.u1; u++) {
    const k = u + ',' + v;
    m.set(k, (g.get(k) || '').replace(/\[.*$/, '') || (inBox(u, v, [18, 8, 44, 23]) ? A('porcelain_floor_tile') : inBox(u, v, KEEP[0]) ? A('concrete_sidewalk')
      : inBox(u, v, KEEP[1]) ? A('concrete_pavement') : 'minecraft:grass_block'));
  }
  return m;
}

/** Runs of one state along world x (the plot's rows), as plot-relative cuboids, in batches of 128. */
function batches(cells, k) {
  const rows = new Map();
  for (const [key, s] of cells) { const [u, v] = key.split(',').map(Number), w = toWorld(u, k, v), x = w[0] - ORIGIN[0], z = w[2] - ORIGIN[2];
    (rows.get(z) || rows.set(z, []).get(z)).push([x, s]); }
  const ops = [], y = toWorld(0, k, 0)[1] - ORIGIN[1];
  for (const [z, list] of rows) {
    list.sort((a, b) => a[0] - b[0]);
    let i = 0; while (i < list.length) { let j = i; while (j + 1 < list.length && list[j + 1][0] === list[j][0] + 1 && list[j + 1][1] === list[i][1]) j++;
      ops.push({min: [list[i][0], y, z], max: [list[j][0], y, z], block: list[i][1]}); i = j + 1; }
  }
  const out = []; for (let i = 0; i < ops.length; i += 128) out.push(ops.slice(i, i + 128));
  return out;
}

export function plan() {
  const cells = ground(), count = {}, marks = {};
  for (const s of cells.values()) count[s] = (count[s] || 0) + 1;
  const m = markings(); for (const st of m.values()) { const id = st.replace(/\[.*$/, '').replace('apocalypse_firstlight:', ''); marks[id] = (marks[id] || 0) + 1; }
  // every marking sits on paved ground (asphalt here, or the store / court concrete the other scripts lay)
  const unpaved = [...m.keys()].filter(k => { const [u, v] = k.split(',').map(Number); return !cells.has(k) && !KEEP.some(b => inBox(u, v, b)); });
  const cp = curbPlan(designSurface()), curbs = {};
  for (const c of cp.values()) { const k = c.kind + (c.edge ? '' : '_inner') + (c.level === 'flush' ? '_flush' : ''); curbs[k] = (curbs[k] || 0) + 1; }
  return {id: ID, size: SIZE, origin: ORIGIN, resume: RESUME, cells: cells.size, count, batches: batches(cells, -1).length,
    markings: m.size, marks, marking_batches: batches(m, 0).length, unpaved, curb_cells: cp.size, curbs};
}

async function paint() {
  const c = new BridgeClient('./run'), dir = path.resolve('build/authoring_checks', ID);
  const status = await c.call('minecraft_status'), info = await c.call('authoring_info');
  if (info.id !== ID || info.width !== SIZE[0] || info.height !== SIZE[1] || info.depth !== SIZE[2] || info.min.some((x, i) => x !== ORIGIN[i]))
    throw Error('PLOT_MISMATCH ' + JSON.stringify(info) + ' run ' + RESUME);
  const o = info.min, m = markings();
  const sl = await c.call('get_horizontal_slice', {target: 'AUTHORING_SESSION', coordinate: toWorld(0, 0, 0)[1], encoding: 'palette'});
  const names = Object.fromEntries(Object.entries(sl.palette).map(([st, i]) => [i, st])), at = new Map();
  sl.rows.forEach((row, z) => row.forEach((i, x) => at.set((o[0] + x) + ',' + (o[2] + z), names[i])));
  const id = st => (/^Block\{([^}]+)\}/.exec(st || '') || [])[1] || st;
  const ok = st => st === 'minecraft:air' || /^apocalypse_firstlight:(edge_lane_|pavement_)/.test(st);
  const blocked = [];
  for (const k of m.keys()) { const [u, v] = k.split(',').map(Number), w = toWorld(u, 0, v), here = id(at.get(w[0] + ',' + w[2])); if (!ok(here)) blocked.push(k + ' ' + here); }
  if (blocked.length) throw Error('MARKINGS_BLOCKED ' + JSON.stringify(blocked.slice(0, 30)));
  const world = p => [o[0] + p[0], o[1] + p[1], o[2] + p[2]], log = [];
  for (const ops of batches(m, 0)) {
    await c.call('we_batch_set', {target: 'AUTHORING_SESSION', operations: ops.map(op => ({min: world(op.min), max: world(op.max), block: op.block}))});
    log.push('ok ' + ops.length);
  }
  const audit = await c.call('audit_support', {target: 'AUTHORING_SESSION'}).catch(e => ({error: String(e.message || e)}));
  const report = {plot: info, world: status.world, plan: plan(), log, audit};
  await mkdir(dir, {recursive: true}); await writeFile(path.join(dir, 'site_markings_v1.json'), JSON.stringify(report, null, 1));
  console.log(JSON.stringify({markings: m.size, log, audit: audit.counts ?? audit, issues: audit.issues?.slice(0, 10)}, null, 1));
}

async function curbs() {
  const c = new BridgeClient('./run'), dir = path.resolve('build/authoring_checks', ID);
  const status = await c.call('minecraft_status'), info = await c.call('authoring_info');
  if (info.id !== ID || info.width !== SIZE[0] || info.height !== SIZE[1] || info.depth !== SIZE[2] || info.min.some((x, i) => x !== ORIGIN[i]))
    throw Error('PLOT_MISMATCH ' + JSON.stringify(info) + ' run ' + RESUME);
  const o = info.min, y = toWorld(0, -1, 0)[1];
  const read = async () => { const sl = await c.call('get_horizontal_slice', {target: 'AUTHORING_SESSION', coordinate: y, encoding: 'palette'});
    const names = Object.fromEntries(Object.entries(sl.palette).map(([st, i]) => [i, st])), lot = new Map();
    sl.rows.forEach((row, z) => row.forEach((i, x) => { const wx = o[0] + x, wz = o[2] + z, u = -50 - wx, v = 474 - wz; lot.set(u + ',' + v, names[i]); }));
    return lot; };
  const id = st => (/^Block\{([^}]+)\}/.exec(st || '') || [])[1] || st;
  const before = await read(), surface = new Map([...before].map(([k, st]) => [k, id(st)]));
  const cp = curbPlan(surface), cells = new Map();
  for (const [k, cc] of cp) {
    const st = before.get(k) || '', prop = (name, dflt) => (new RegExp(name + '=([a-z]+)').exec(st) || [])[1] || dflt;
    const axis = cc.kind === 'walk' ? prop('axis', 'x') : 'z', curb = /curb_(sidewalk|grass)/.test(st);
    // V2 (2026-10-09): the edge flags (world directions; lot north = world south, lot east = world west), kept where an
    // earlier run or the game set them; an existing curb keeps the level and paint the user gave it
    const [u, v] = k.split(',').map(Number), road = (du, dv) => curbKind(surface.get((u + du) + ',' + (v + dv))) === 'road';
    const flag = (world, du, dv) => road(du, dv) || prop(world, 'false') === 'true';
    cells.set(k, `${A(cc.kind === 'walk' ? 'curb_sidewalk' : 'curb_grass')}[axis=${axis},east=${flag('east', -1, 0)},level=${curb ? prop('level', cc.level) : cc.level},`
      + `north=${flag('north', 0, 1)},paint=${curb ? prop('paint', 'none') : 'none'},south=${flag('south', 0, -1)},west=${flag('west', 1, 0)}]`);
  }
  const world = p => [o[0] + p[0], o[1] + p[1], o[2] + p[2]], log = [];
  for (const ops of batches(cells, -1)) {
    await c.call('we_batch_set', {target: 'AUTHORING_SESSION', operations: ops.map(op => ({min: world(op.min), max: world(op.max), block: op.block}))});
    log.push('ok ' + ops.length);
  }
  // read back: every planned cell holds its state (properties in any order), nothing else on the layer changed
  const after = await read(), norm = st => { const m = /^(?:Block\{([^}]+)\})?([^\[]*)(?:\[(.*)\])?$/.exec(st || ''); return (m[1] || m[2]) + (m[3] ? '[' + m[3].split(',').sort().join(',') + ']' : ''); };
  const wrong = [], changed = [];
  for (const [k, st] of cells) if (norm(after.get(k)) !== norm(st)) wrong.push(k + ' ' + after.get(k));
  for (const [k, st] of after) if (!cells.has(k) && st !== before.get(k)) changed.push(k);
  const audit = await c.call('audit_support', {target: 'AUTHORING_SESSION'}).catch(e => ({error: String(e.message || e)}));
  const counts = {}; for (const cc of cp.values()) { const kk = cc.kind + (cc.edge ? '' : '_inner') + (cc.level === 'flush' ? '_flush' : ''); counts[kk] = (counts[kk] || 0) + 1; }
  const report = {plot: info, world: status.world, curbs: cells.size, counts, log, readback: {wrong, changed}, audit};
  await mkdir(dir, {recursive: true}); await writeFile(path.join(dir, 'site_curbs_v1.json'), JSON.stringify(report, null, 1));
  console.log(JSON.stringify({curbs: cells.size, counts, log, wrong: wrong.slice(0, 10), changed: changed.slice(0, 10), audit: audit.counts ?? audit, issues: audit.issues?.slice(0, 10)}, null, 1));
}

const SAFE = new Set(['minecraft:grass_block', 'minecraft:dirt', 'minecraft:coarse_dirt', 'minecraft:air', ASPHALT, A('concrete_pavement')]);
const PLANTS = /^minecraft:(grass|short_grass|tall_grass|fern|large_fern|dandelion|poppy|azure_bluet|oxeye_daisy|cornflower|blue_orchid|allium|.*_tulip|lily_of_the_valley|dead_bush)$/;

async function build() {
  const c = new BridgeClient('./run'), dir = path.resolve('build/authoring_checks', ID);
  const status = await c.call('minecraft_status'), info = await c.call('authoring_info');
  if (info.id !== ID || info.width !== SIZE[0] || info.height !== SIZE[1] || info.depth !== SIZE[2] || info.min.some((x, i) => x !== ORIGIN[i]))
    throw Error('PLOT_MISMATCH ' + JSON.stringify(info) + ' run ' + RESUME);
  const o = info.min, cells = ground();
  // what is there now, k -1 and k 0 (rows z ascending, columns x ascending)
  const layer = async k => { const sl = await c.call('get_horizontal_slice', {target: 'AUTHORING_SESSION', coordinate: toWorld(0, k, 0)[1], encoding: 'palette'});
    const names = Object.fromEntries(Object.entries(sl.palette).map(([s, i]) => [i, s])), at = new Map();
    sl.rows.forEach((row, z) => row.forEach((i, x) => at.set((o[0] + x) + ',' + (o[2] + z), names[i]))); return at; };
  const id = s => (/^Block\{([^}]+)\}/.exec(s || '') || [])[1] || s;
  const surface = await layer(-1), above = await layer(0);
  const blocked = [], plants = new Map();
  for (const key of cells.keys()) {
    const [u, v] = key.split(',').map(Number), w = toWorld(u, -1, v), here = id(surface.get(w[0] + ',' + w[2])), up = id(above.get(w[0] + ',' + w[2]));
    if (!SAFE.has(here)) blocked.push(key + ' ' + here);
    if (up && PLANTS.test(up)) plants.set(key, 'minecraft:air');
  }
  if (blocked.length) throw Error('GROUND_BLOCKED ' + JSON.stringify(blocked.slice(0, 30)) + (blocked.length > 30 ? ' +' + (blocked.length - 30) : ''));
  const world = p => [o[0] + p[0], o[1] + p[1], o[2] + p[2]], log = [];
  for (const ops of [...batches(plants, 0), ...batches(cells, -1)]) {
    await c.call('we_batch_set', {target: 'AUTHORING_SESSION', operations: ops.map(op => ({min: world(op.min), max: world(op.max), block: op.block}))});
    log.push('ok ' + ops.length);
  }
  const audit = await c.call('audit_support', {target: 'AUTHORING_SESSION'}).catch(e => ({error: String(e.message || e)}));
  const report = {plot: info, world: status.world, plan: plan(), plants: plants.size, log, audit};
  await mkdir(dir, {recursive: true}); await writeFile(path.join(dir, 'site_ground_v1.json'), JSON.stringify(report, null, 1));
  console.log(JSON.stringify({paved: cells.size, plants_cleared: plants.size, log, audit: audit.counts ?? audit, issues: audit.issues?.slice(0, 10)}, null, 1));
}

const direct = process.argv[1] && path.resolve(process.argv[1]).toLowerCase() === fileURLToPath(import.meta.url).toLowerCase();
const mode = direct ? process.argv[2] : null;
if (mode === 'plan') console.log(JSON.stringify(plan(), null, 1));
else if (mode === 'build') await build();
else if (mode === 'markings') await paint();
else if (mode === 'curbs') await curbs();
