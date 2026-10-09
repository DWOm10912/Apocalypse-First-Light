// Fuel Stop A1 details V1 (docs/models/fuel_stop_a1_details_v1.md) through the AFL authoring bridge, in the lighting plot
// (gas_station_02_lighting, the whole lot k -2..8): live authoring only, no export. 2026-10-09 (user: "可以，按这个顺序开始做").
//   price sign     4 x 4 cells at u 50..53, v 61, facing lot north (its two faces show the same); the master c1r0 at u 51 sits
//                  on the south row cable's riser (fuel_stop_a1_lighting.mjs SIGN_FEED), which this script writes too
//   PRAIRIE        channel letters at k 4 (the brick band, 4..5 m) in front of the storefront wall, v 24, u 28..34, facing lot south
//   roof           the k 4 roof / ceiling slab (u 19..43, v 9..22, smooth_quartz placeholder) becomes roof_tpo
//   rooftop units  three at k 5 on the roof, v 15, anchors u 23 / 30 / 37 (the second column at u + 1), facing lot north
//   trash enclosure  on the dumpster pad: cmu_screen_wall 2 high round u 56..59 x v 8..12 (the west and east runs hug the
//                  enclosure's outer edges, the back run the south edge of v 12, capped at k 1), a pair of enclosure gates in
//                  the v 7 row (hinges at the side walls, opening lot north onto the rear drive), the green commercial dumpster
//                  at v 10 (u 57..58, lids toward the gates) and two bollards behind it at v 11 (they keep it off the back wall)
// Lot frame as in fuel_stop_a1_site.mjs: u east, v south, k metres above G; world x = -50 - u, y = -51 + k, z = 474 - v, so lot
// north = world south and lot east = world west. Before writing, every cell it writes is read; anything but what it expects
// stops the script (see build()). A re-run skips the multiblocks already in place.
//   node tools/afl_minecraft_mcp/fuel_stop_a1_details.mjs plan  -> offline: counts, the resume command
//   node tools/afl_minecraft_mcp/fuel_stop_a1_details.mjs build -> checks and writes into the active lighting plot
import {BridgeClient} from './bridge_client.mjs';
import {mkdir, writeFile} from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {ID, LOT, SIZE, ORIGIN, RESUME, toWorld, cableTree, cableState, SIGN_FEED} from './fuel_stop_a1_lighting.mjs';

const A = id => 'apocalypse_firstlight:' + id;
const TURN = {north: 'south', south: 'north', east: 'west', west: 'east'};   // lot side -> world side
const key = c => c.join(',');

export const SIGN = {anchor: [SIGN_FEED[0], 0, SIGN_FEED[1]], cells: {u0: 50, u1: 53, v: 61, k0: 0, k1: 3}, facing: 'north'};
export const LETTERS = {word: 'PRAIRIE', u0: 28, k: 4, v: 24, facing: 'south'};
export const ROOF = {u0: 19, u1: 43, v0: 9, v1: 22, k: 4};
export const RTUS = [23, 30, 37].map(u => ({anchor: [u, 5, 15], facing: 'north'}));
export const ENCLOSURE = {u0: 56, u1: 59, v0: 8, v1: 12, gateRow: 7,
  gates: [{anchor: [56, 0, 7], hinge: 'right'}, {anchor: [58, 0, 7], hinge: 'left'}],
  dumpster: {anchor: [57, 0, 10], id: 'commercial_dumpster'}, bollards: [[57, 0, 11], [58, 0, 11]]};

/** The cable cells this round writes (the south row from its last pole on to the sign, the riser), from the lighting tree. */
function signFeed() {
  const t = cableTree(), out = new Map();
  for (let u = 46; u <= SIGN_FEED[0]; u++) out.set(key([u, -2, SIGN_FEED[1]]), cableState(t.links.get(key([u, -2, SIGN_FEED[1]]))));
  out.set(key([SIGN_FEED[0], -1, SIGN_FEED[1]]), cableState(t.links.get(key([SIGN_FEED[0], -1, SIGN_FEED[1]]))));
  return out;
}

/** The screen wall cells: lot cell -> world state. */
function walls() {
  const E = ENCLOSURE, out = new Map();
  for (const k of [0, 1]) for (let v = E.v0; v <= E.v1; v++) for (let u = E.u0; u <= E.u1; u++) {
    const sides = [];
    if (u === E.u0) sides.push('west');
    if (u === E.u1) sides.push('east');
    if (v === E.v1) sides.push('south');
    if (!sides.length) continue;
    const world = new Set(sides.map(s => TURN[s]));
    out.set(key([u, k, v]), A(`cmu_screen_wall[${['east', 'north', 'south', 'west'].map(d => d + '=' + world.has(d)).join(',')},cap=${k === 1}]`));
  }
  return out;
}

/** Everything written cell by cell (lot key -> world state), and the multiblocks (placed by the authoring bridge). */
export function recipe() {
  const cells = new Map(), multis = [], problems = [];
  const put = (c, s) => { if (cells.has(key(c)) && cells.get(key(c)) !== s) problems.push('OVERLAP ' + key(c)); cells.set(key(c), s); };
  for (const [k, s] of signFeed()) put(k.split(',').map(Number), s);
  [...LETTERS.word.toLowerCase()].forEach((l, i) => put([LETTERS.u0 + i, LETTERS.k, LETTERS.v], A(`channel_letter[facing=${TURN[LETTERS.facing]},letter=${l},lit=false]`)));
  for (let v = ROOF.v0; v <= ROOF.v1; v++) for (let u = ROOF.u0; u <= ROOF.u1; u++) put([u, ROOF.k, v], A('roof_tpo'));
  for (const [k, s] of walls()) put(k.split(',').map(Number), s);
  for (const b of ENCLOSURE.bollards) put(b, A('fuel_island_bollard[on_curb=false]'));
  multis.push({id: 'price_sign', anchor: SIGN.anchor, facing: TURN[SIGN.facing], properties: {},
    cells: [...range(SIGN.cells.u0, SIGN.cells.u1)].flatMap(u => [...range(SIGN.cells.k0, SIGN.cells.k1)].map(k => [u, k, SIGN.cells.v]))});
  for (const r of RTUS) multis.push({id: 'rooftop_unit', anchor: r.anchor, facing: TURN[r.facing], properties: {},
    cells: [0, 1].flatMap(du => [0, 1].map(dk => [r.anchor[0] + du, r.anchor[1] + dk, r.anchor[2]]))});
  for (const g of ENCLOSURE.gates) multis.push({id: 'enclosure_gate', anchor: g.anchor, facing: TURN.north, properties: {hinge: g.hinge},
    cells: [0, 1].flatMap(du => [0, 1].map(dk => [g.anchor[0] + du, dk, g.anchor[2]]))});
  const d = ENCLOSURE.dumpster;
  multis.push({id: d.id, anchor: d.anchor, facing: TURN.north, properties: {}, cells: [d.anchor, [d.anchor[0] + 1, 0, d.anchor[2]]]});
  for (const m of multis) for (const c of m.cells) if (cells.has(key(c))) problems.push('OVERLAP ' + m.id + ' ' + key(c));
  return {cells, multis, problems};
}
function* range(a, b) { for (let i = a; i <= b; i++) yield i; }

export function plan() {
  const r = recipe(), count = {};
  for (const s of r.cells.values()) { const id = s.replace(/\[.*$/, '').replace('apocalypse_firstlight:', ''); count[id] = (count[id] || 0) + 1; }
  return {id: ID, resume: RESUME, cells: count, multiblocks: r.multis.map(m => `${m.id} @${key(m.anchor)} ${m.facing}${m.properties.hinge ? ' ' + m.properties.hinge : ''}`), problems: r.problems};
}

// what a cell may hold before it is written
const AIR = new Set(['minecraft:air', 'minecraft:cave_air']);
const PLANTS = /^minecraft:(grass|short_grass|tall_grass|fern|large_fern|dandelion|poppy|azure_bluet|oxeye_daisy|cornflower|blue_orchid|allium|.*_tulip|lily_of_the_valley|dead_bush)$/;
const GROUND = new Set(['minecraft:dirt', 'minecraft:coarse_dirt', 'minecraft:grass_block', 'minecraft:stone', 'minecraft:gravel', 'minecraft:sand', A('power_cable')]);

async function build() {
  const r = recipe();
  if (r.problems.length) throw Error('RECIPE ' + JSON.stringify(r.problems));
  const c = new BridgeClient('./run'), dir = path.resolve('build/authoring_checks', ID);
  const status = await c.call('minecraft_status'), info = await c.call('authoring_info');
  if (info.id !== ID || info.width !== SIZE[0] || info.height !== SIZE[1] || info.depth !== SIZE[2] || info.min.some((x, i) => x !== ORIGIN[i]))
    throw Error('PLOT_MISMATCH ' + JSON.stringify(info) + ' run ' + RESUME);
  const o = info.min, at = new Map();
  const read = async () => { at.clear(); for (let k = LOT.k0; k <= LOT.k1; k++) {
    const sl = await c.call('get_horizontal_slice', {target: 'AUTHORING_SESSION', coordinate: toWorld(0, k, 0)[1], encoding: 'palette'});
    const names = Object.fromEntries(Object.entries(sl.palette).map(([s, i]) => [i, s]));
    sl.rows.forEach((row, z) => row.forEach((i, x) => at.set(key([-50 - (o[0] + x), k, 474 - (o[2] + z)]), names[i])));
  } };
  await read();
  const id = s => (/^Block\{([^}]+)\}/.exec(s || '') || [])[1] || (s || '').replace(/\[.*$/, '');
  const here = cell => id(at.get(key(cell)));
  const blocked = [], clear = new Map();
  const free = (cell, what, ok = []) => { const now = here(cell);
    if (PLANTS.test(now)) clear.set(key(cell), 'minecraft:air'); else if (!AIR.has(now) && !ok.includes(now)) blocked.push(key(cell) + ' ' + what + ' ' + now); };
  // cables: into ground or an earlier cable; the last pole's cell already a cable
  for (const k of r.cells.keys()) { const cell = k.split(',').map(Number); if (cell[1] >= 0) continue;
    if (!GROUND.has(here(cell)) && !AIR.has(here(cell))) blocked.push(k + ' cable ' + here(cell)); }
  if (here([46, -2, SIGN_FEED[1]]) !== A('power_cable')) blocked.push('46,-2,61 expected the south row cable: ' + here([46, -2, SIGN_FEED[1]]));
  // letters: air, a sturdy brick wall behind
  [...LETTERS.word].forEach((_, i) => { const u = LETTERS.u0 + i; free([u, LETTERS.k, LETTERS.v], 'letter', [A('channel_letter')]);
    if (here([u, LETTERS.k, LETTERS.v - 1]) !== A('face_brick_warm_gray')) blocked.push(key([u, LETTERS.k, LETTERS.v - 1]) + ' no brick behind the letter: ' + here([u, LETTERS.k, LETTERS.v - 1])); });
  // roof: the placeholder slab (or an earlier roof)
  for (let v = ROOF.v0; v <= ROOF.v1; v++) for (let u = ROOF.u0; u <= ROOF.u1; u++) { const now = here([u, ROOF.k, v]);
    if (now !== 'minecraft:smooth_quartz' && now !== A('roof_tpo')) blocked.push(key([u, ROOF.k, v]) + ' roof ' + now); }
  // walls and bollards: air on the pad
  for (const [k, s] of r.cells) { const cell = k.split(',').map(Number);
    if (/cmu_screen_wall|fuel_island_bollard/.test(s)) { free(cell, 'enclosure', [A('cmu_screen_wall'), A('fuel_island_bollard')]);
      if (cell[1] === 0 && here([cell[0], -1, cell[2]]) !== A('concrete_pavement')) blocked.push(key([cell[0], -1, cell[2]]) + ' not the pad: ' + here([cell[0], -1, cell[2]])); } }
  // multiblocks: their cells air (or already this block), the sign / dumpster / gates on ground, the units on the roof
  const placed = m => here(m.anchor) === A(m.id);
  for (const m of r.multis) {
    if (placed(m)) continue;
    for (const cell of m.cells) free(cell, m.id);
    for (const cell of m.cells.filter(q => q[1] === m.anchor[1])) { const below = here([cell[0], cell[1] - 1, cell[2]]);
      if (m.id === 'rooftop_unit' ? !(below === 'minecraft:smooth_quartz' || below === A('roof_tpo')) : AIR.has(below)) blocked.push(key(cell) + ' ' + m.id + ' on ' + below); }
  }
  const player = (await c.call('get_player_state')).block, pu = -50 - player[0], pv = 474 - player[2], pk = player[1] + 51;
  for (const m of r.multis) if (!placed(m) && m.cells.some(q => q[0] === pu && q[2] === pv && (q[1] === pk || q[1] === pk + 1))) blocked.push('PLAYER_IN ' + m.id + ' ' + key(m.anchor));
  if (blocked.length) throw Error('DETAILS_BLOCKED ' + JSON.stringify(blocked.slice(0, 40)) + (blocked.length > 40 ? ' +' + (blocked.length - 40) : ''));

  const report = {plot: info, world: status.world, plan: plan(), steps: [], failures: []};
  const step = async (label, tool, args) => {
    try { const res = await c.call(tool, args); report.steps.push(label + ' ok'); return res; }
    catch (e) { const msg = String(e.message || e); report.failures.push({label, error: msg}); report.steps.push(label + ' FAIL ' + msg); return null; }
  };
  const ops = cells => [...cells].map(([k, block]) => { const w = toWorld(...k.split(',').map(Number)); return {min: w, max: w, block}; });
  const chunks = (list, n) => Array.from({length: Math.ceil(list.length / n)}, (_, i) => list.slice(i * n, i * n + n));
  if (clear.size) await step('plants', 'we_batch_set', {target: 'AUTHORING_SESSION', operations: ops(clear)});
  // 1. the sign's cable (before the sign, so its port finds it); 2. the sign and the other multiblocks; 3. the cells
  const cables = [...r.cells].filter(([, s]) => s.includes('power_cable')), rest = [...r.cells].filter(([, s]) => !s.includes('power_cable'));
  await step('sign_feed', 'we_batch_set', {target: 'AUTHORING_SESSION', operations: ops(cables)});
  for (const [i, part] of chunks(rest, 120).entries()) await step('cells_' + i, 'we_batch_set', {target: 'AUTHORING_SESSION', operations: ops(part)});
  for (const m of r.multis) if (!placed(m))
    await step(`${m.id} @${key(m.anchor)}`, 'place_multiblock', {block_id: A(m.id), anchor: toWorld(...m.anchor), facing: m.facing,
      ...(Object.keys(m.properties).length ? {properties: m.properties} : {})});
  // read back: every written cell holds its state (properties in any order), every multiblock cell its block
  await read();
  const norm = s => { const mm = /^(?:Block\{([^}]+)\})?([^\[]*)(?:\[(.*)\])?$/.exec(s || ''); return (mm[1] || mm[2]) + (mm[3] ? '[' + mm[3].split(',').sort().join(',') + ']' : ''); };
  const wrong = [...r.cells].filter(([k, s]) => norm(at.get(k)) !== norm(s)).map(([k]) => k + ' ' + at.get(k));
  for (const m of r.multis) for (const cell of m.cells) if (here(cell) !== A(m.id)) wrong.push(key(cell) + ' ' + m.id + ' ' + at.get(key(cell)));
  const audit = await c.call('audit_support', {target: 'AUTHORING_SESSION'}).catch(e => ({error: String(e.message || e)}));
  Object.assign(report, {readback: {wrong}, audit});
  await mkdir(dir, {recursive: true}); await writeFile(path.join(dir, 'fuel_stop_a1_details_v1.json'), JSON.stringify(report, null, 1));
  console.log(JSON.stringify({cells: r.cells.size, multiblocks: r.multis.length, plants: clear.size, failures: report.failures, wrong: wrong.slice(0, 12),
    audit: audit.counts ?? audit, issues: audit.issues?.slice(0, 12)}, null, 1));
}

const direct = process.argv[1] && path.resolve(process.argv[1]).toLowerCase() === fileURLToPath(import.meta.url).toLowerCase();
const mode = direct ? process.argv[2] : null;
const fail = e => { console.error(e); process.exitCode = 1; };
if (mode === 'plan') console.log(JSON.stringify(plan(), null, 1));
else if (mode === 'build') build().catch(fail);
