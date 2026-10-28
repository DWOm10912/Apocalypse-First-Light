// Box Stove V1 (docs/models/box_stove_v1.md, 2026-10-13): the cast-iron box stove, the stove heat shield and the flagstone
// of the forest start home B ("溪谷老宅", docs/worldgen/forest_start_home_b_v1_plan.md, M2 F1). User 2026-10-13: stove A of
// the concepts (design/buildings/forest_start_home_v1/m2_sheets/m2_2_stove_A.png: the old long box stove, no oven), the heat
// shield as a block of its own, the hearth in flat stone slabs.
//   box_stove          V2 (2026-10-26, docs/models/box_stove_v2_plan.md): body 90 x 50 cm on four splayed legs, the top
//                      plate at 65.5 cm with two cook lids, the loading door in the end (hinged on the wall side, it
//                      swings to the wall so the room sees the fire), a spin draft in it, a coil-spring latch, an ash
//                      apron under it; the flue collar at the cell's middle and a pipe stub with its damper up to the cell
//                      top, where the stove pipe (cabin) takes over. Firebrick lining, an ash bed and up to three splits
//                      inside. Modelled and baked in Blender (tools/stove/box_stove_v2/), the pieces copied in from there.
//   stove_heat_shield  a galvanised sheet 10 cm out from the wall on ceramic spools (3 cm off the log faces), open at the
//                      bottom and the top so air rises behind it; joins the shields (and the stove / pipe it stands
//                      behind) beside, above and below it into one sheet: 16 pieces by the joined sides
//   flagstone          a full block: coursed random rectangular bluestone on top (a 3 x 3 macro texture), the fieldstone's
//                      rubble on its sides (MacroTileModel, its separate top group)
// Every piece is drawn facing north (the front at z 0, the wall at z 1); the stove's door end is x 0 (in the house, facing
// south, that is the east end toward the chairs).
// The loading door swings as an AFL Animated Block Mesh (box_stove_door, tools/furniture/furniture-mesh.mjs, 2026-10-15:
// the cabin pieces swapped it closed / open in one frame): only the door, drawn by the block entity renderer; the body,
// the firebox, the splits and the shield sheet behind it stay cabin pieces drawn by the chunk. stove/door_closed is the
// item's door.
// Writes textures (block/stove/*.png + _s / _n), mesh pieces (cabin_mesh/stove/, cabin_mesh/shield/), the door's mesh
// (geo, sidecar, profile, atlas block/stove/box_stove_door, src/main/blockbench/stove/), block / item models,
// blockstates, loot tables. Tags, item mass, the temperature source and lang are edited by hand; --check audits them.
//   node tools/build-box-stove-v1.mjs            -> writes every output
//   node tools/build-box-stove-v1.mjs --check    -> verifies every output is up to date and the hand-edited data
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {renderX} from './cabin/cabin-art.mjs';
import {STOVE_TEXTURES, flagstoneTopTiles, FL_COLS, FL_ROWS} from './stove/box-stove-art.mjs';
import {Piece, solid, cylinder, serialise} from './cabin/cabin-geo.mjs';
import {furnitureMesh} from './furniture/furniture-mesh.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const ASSETS = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight');
const DATA = path.join(ROOT, 'src/main/resources/data');
const NS = 'apocalypse_firstlight:', TEX = NS + 'block/';
const json = v => JSON.stringify(v, null, 2) + '\n';

/** The blocks: id, item mass (kg; carry scale), carry tag, mining (AGENTS.md audit), names. */
export const BLOCKS = [
  {id: 'box_stove', mass: 90, carry: 'oversized', tier: 'iron', noise: 'metal', en: 'Cast Iron Box Stove', zh: '铸铁箱式炉'},
  {id: 'stove_heat_shield', mass: 3, carry: 'bulky', tier: 'stone', noise: 'metal', en: 'Stove Heat Shield', zh: '隔热钢板'},
  {id: 'flagstone', mass: 0.25, carry: null, tier: null, noise: 'stone', en: 'Flagstone', zh: '石板'},
];

const IRON = 'stove/cast_iron', EDGE = 'stove/cast_iron_edge';
const BARK = 'stove/firewood_bark', END = 'stove/firewood_end', SHEET = 'stove/heat_shield', CERAMIC = 'stove/ceramic';

// =============================================================================================== the stove
// Box Stove V2 (docs/models/box_stove_v2_plan.md, user 2026-10-26: "可以，进游戏吧"): the body, the firebox lining and the
// door are modelled and baked in Blender (tools/stove/box_stove_v2/: model.py, build.py, export.py), each with its own
// LabPBR sprite; export.py writes them as cabin pieces to design/furniture/box_stove_v2/export/, copied here as
// stove/body, stove/firebox and stove/door_closed (the item's door; the door's animated mesh is made from it).
// Body x 0.06..0.96, z 0.13..0.63, y 0.19..0.62, the top plate to 0.655, the two cook lids' tops 0.661; the door end
// (x 0.06) has the loading opening y 0.28..0.54 x z 0.24..0.52, the door hangs on the wall side (hinge x 0.050, z 0.555).
const V2 = path.join(ROOT, 'design/furniture/box_stove_v2');
export const HINGE = [0.050, 0.555];
const BAKED = [['body', 'stove/body', 'box_stove_body', 'box_stove_v2_body'], ['firebox', 'stove/firebox', 'box_stove_firebox', 'box_stove_v2_inside'],
  ['door', 'stove/door_closed', 'box_stove_door', 'box_stove_v2_door']];   // export file, piece, sprite, baked texture
const bakedPiece = name => fs.readFileSync(path.join(V2, 'export', name + '.json'));
const bakedMaps = tex => ['', '_s', '_n'].map(k => path.join(V2, 'textures', tex + k + '.png'));
/** The splits on the ash bed (its top y 0.21..0.22, export.py): one, two or three (the load: the fuel left in logs' worth). */
const SPLITS = [[0.045, 0.22, 0.64, 0.254, 0.33], [0.042, 0.26, 0.66, 0.254, 0.43], [0.040, 0.20, 0.60, 0.325, 0.38]];   // r, x0, x1, y, z
export const FLAMES = [[0.43, 0.27, 0.33, 0.30, 0.20], [0.46, 0.26, 0.43, 0.28, 0.18], [0.40, 0.34, 0.38, 0.24, 0.16]];   // x, base y, z, width, height
function loads() {
  return [1, 2, 3].map(n => {
    const pc = new Piece('stove/load_' + n);
    for (const [r, a, b, y, z] of SPLITS.slice(0, n)) cylinder(pc, BARK, END, 'x', [y, z], a, b, r, 10, 'ab');
    return pc;
  });
}
/**
 * The loading door as an animated mesh: one bone hinged on the vertical line HINGE (the wall side); 'open' swings it a
 * quarter out of the door end toward the wall, (dx, dz) -> (dz, -dx). Its polygons are stove/door_closed's (a triangle's
 * repeated corner dropped), its texture the baked one.
 */
export function doorMesh() {
  const polys = JSON.parse(bakedPiece('door')).quads.map(q => {
    const c = [0, 1, 2, 3].map(i => q.slice(1 + i * 8, 9 + i * 8));
    if (c[3].every((v, i) => v === c[2][i])) c.pop();
    return {v: c.map(k => k.slice(0, 3)), uv: c.map(k => k.slice(3, 5))};
  });
  return furnitureMesh(ROOT, {id: 'box_stove_door', dir: 'stove', particle: TEX + IRON, part: true, atlas: bakedMaps('box_stove_v2_door'),
    bones: [{name: 'door', pivot: [HINGE[0], 0, HINGE[1]], polys}],
    animations: {open: {duration_ticks: 10, easing: 'ease_in_out', transforms: {door: {rotation: [0, 90, 0]}}}}});
}

// =============================================================================================== the heat shield
// The sheet z 0.896..0.900 (10 cm out from the cell's wall face, 3 cm off the log faces at 0.93), ceramic spools behind
// it to z 1 at x 0.25 / 0.75, y 0.5, a screw head on each. Joined sides (mask: 1 x-, 2 x+, 4 up, 8 down) run to the
// cell edge; open ones stop 3 cm short with a rolled hem (open at the bottom and top: air in under it, out over it).
export const SHEET_Z = [0.896, 0.9];
function shield(mask) {
  const pc = new Piece('shield/panel_' + mask), j = b => (mask & b) !== 0, [z0, z1] = SHEET_Z, zm = (z0 + z1) / 2, hem = 0.0042;
  const x0 = j(1) ? 0 : 0.03, x1 = j(2) ? 1 : 0.97, y0 = j(8) ? 0 : 0.03, y1 = j(4) ? 1 : 0.97;
  solid(pc, [[SHEET, [x0, y0, z0], [x1, y1, z1]]]);
  if (!j(1)) cylinder(pc, SHEET, SHEET, 'y', [zm, x0], y0, y1, hem, 8, 'ab');
  if (!j(2)) cylinder(pc, SHEET, SHEET, 'y', [zm, x1], y0, y1, hem, 8, 'ab');
  if (!j(8)) cylinder(pc, SHEET, SHEET, 'x', [y0, zm], x0, x1, hem, 8, 'ab');
  if (!j(4)) cylinder(pc, SHEET, SHEET, 'x', [y1, zm], x0, x1, hem, 8, 'ab');
  for (const x of [0.25, 0.75]) {
    cylinder(pc, CERAMIC, CERAMIC, 'z', [x, 0.5], z1, 1, 0.014, 12);
    cylinder(pc, EDGE, EDGE, 'z', [x, 0.5], z0 - 0.003, z0, 0.0065, 10, 'a');
  }
  return pc;
}

/** The generated pieces: the splits and the shield sheets (the stove's own come baked, BAKED). */
export function pieces() {
  return [...loads(), ...Array.from({length: 16}, (_, m) => shield(m))];
}

// =============================================================================================== models, states, loot
const ITEMS = {box_stove: [['stove/body', 0], ['stove/door_closed', 0]], stove_heat_shield: [['shield/panel_0', 0]]};
const PARTICLE = {box_stove: IRON, stove_heat_shield: SHEET};
const loot = id => json({type: 'minecraft:block', pools: [{rolls: 1, bonus_rolls: 0, entries: [{type: 'minecraft:item', name: NS + id}],
  conditions: [{condition: 'minecraft:survives_explosion'}]}], random_sequence: `${NS}blocks/${id}`});

export function outputs() {
  const out = [];
  const texOut = (name, maps) => maps.forEach((m, i) => out.push([path.join(ASSETS, 'textures/block/stove', name + ['', '_s', '_n'][i] + '.png'), m]));
  for (const [name, [make, height]] of Object.entries(STOVE_TEXTURES)) texOut(name, renderX(make(), {height}));
  flagstoneTopTiles().forEach((maps, t) => texOut('flagstone_top_' + t, maps));
  for (const pc of pieces()) out.push([path.join(ASSETS, 'cabin_mesh', pc.name + '.json'), JSON.stringify(serialise(pc, TEX)) + '\n']);
  for (const [file, piece, sprite, tex] of BAKED) {
    out.push([path.join(ASSETS, 'cabin_mesh', piece + '.json'), bakedPiece(file)]);
    if (file !== 'door') bakedMaps(tex).forEach((f, i) => out.push([path.join(ASSETS, 'textures/block/stove', sprite + ['', '_s', '_n'][i] + '.png'), fs.readFileSync(f)]));   // the door's: doorMesh
  }
  out.push(...doorMesh().outputs);
  const bm = id => `${NS}block/stove/${id}`;
  for (const {id} of BLOCKS) {
    if (id === 'flagstone') {
      const side = Object.fromEntries(Array.from({length: 6}, (_, t) => ['tile' + t, TEX + 'cabin/fieldstone_' + t]));
      const top = Object.fromEntries(Array.from({length: FL_COLS * FL_ROWS}, (_, t) => ['top' + t, TEX + 'stove/flagstone_top_' + t]));
      out.push([path.join(ASSETS, 'models/block/stove', id + '.json'), json({parent: 'minecraft:block/block', loader: NS + 'macro_tile', columns: 3, rows: 2,
        top_columns: FL_COLS, top_rows: FL_ROWS, textures: {particle: TEX + 'stove/flagstone_top_0', ...side, ...top}})]);
    } else out.push([path.join(ASSETS, 'models/block/stove', id + '.json'), json({parent: 'minecraft:block/block', loader: NS + 'cabin', textures: {particle: TEX + PARTICLE[id]}, item: ITEMS[id]})]);
    out.push([path.join(ASSETS, 'blockstates', id + '.json'), json({variants: {'': {model: bm(id)}}})]);
    out.push([path.join(ASSETS, 'models/item', id + '.json'), json({parent: bm(id)})]);
    out.push([path.join(DATA, 'apocalypse_firstlight/loot_tables/blocks', id + '.json'), loot(id)]);
  }
  return out;
}

// =============================================================================================== audit
const readJson = p => JSON.parse(fs.readFileSync(path.join(ROOT, p), 'utf8'));
function audit() {
  const errs = [], tags = 'src/main/resources/data/minecraft/tags/blocks/', afl = 'src/main/resources/data/apocalypse_firstlight/';
  const tagHas = (file, id) => { try { return readJson(file).values.some(v => (typeof v === 'string' ? v : v.id) === (id.includes(':') ? id : NS + id)); } catch { return false; } };
  for (const b of BLOCKS) {
    if (!tagHas(tags + 'mineable/pickaxe.json', b.id)) errs.push(`${b.id}: missing from minecraft:mineable/pickaxe`);
    if (tagHas(tags + 'mineable/axe.json', b.id)) errs.push(`${b.id}: in minecraft:mineable/axe`);
    for (const t of ['stone', 'iron', 'diamond']) if (tagHas(tags + `needs_${t}_tool.json`, b.id) !== (b.tier === t)) errs.push(`${b.id}: needs_${t}_tool ${b.tier === t ? 'missing' : 'unexpected'}`);
    if (!tagHas(`${afl}tags/blocks/noise_${b.noise}_blocks.json`, b.id)) errs.push(`${b.id}: missing from noise_${b.noise}_blocks`);
  }
  const mass = readJson(afl + 'item_mass/afl_content_v1.json').items ?? {};
  for (const b of BLOCKS) if (mass[NS + b.id]?.unit_mass_kg !== b.mass) errs.push(`${b.id}: item mass ${mass[NS + b.id]?.unit_mass_kg} (want ${b.mass})`);
  for (const b of BLOCKS) for (const c of ['bulky', 'oversized'])
    if (tagHas(`${afl}tags/items/carry/${c}.json`, b.id) !== (b.carry === c)) errs.push(`${b.id}: carry/${c} ${b.carry === c ? 'missing' : 'unexpected'}`);
  for (const lang of ['en_us', 'zh_cn']) {
    const l = readJson(`src/main/resources/assets/apocalypse_firstlight/lang/${lang}.json`);
    for (const b of BLOCKS) { const v = l['block.apocalypse_firstlight.' + b.id]; if (v !== (lang === 'en_us' ? b.en : b.zh)) errs.push(`${b.id}: lang ${lang} = ${v}`); }
  }
  for (const f of ['logs', 'wood', 'kindling']) if (!fs.existsSync(path.join(ROOT, `${afl}tags/items/stove_fuel/${f}.json`))) errs.push(`stove_fuel/${f} tag missing`);
  const src = readJson(afl + 'temperature/temperature_v1.json').sources.find(s => s.block === NS + 'box_stove');
  if (!src || src.lit !== true) errs.push('box_stove: no lit heat source in temperature_v1.json');
  return errs;
}

const isMain = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  const out = outputs();
  if (process.argv.includes('--check')) {
    const stale = out.filter(([file, data]) => { const cur = fs.existsSync(file) ? fs.readFileSync(file) : null; return !cur || !cur.equals(Buffer.isBuffer(data) ? data : Buffer.from(data)); });
    for (const [file] of stale) console.log('stale ' + path.relative(ROOT, file));
    const errs = audit();
    for (const e of errs) console.log('audit: ' + e);
    if (stale.length || errs.length) process.exit(1);
    console.log(`CHECK OK (${out.length} outputs)`);
  } else {
    for (const [file] of out) fs.mkdirSync(path.dirname(file), {recursive: true});
    for (const [file, data] of out) fs.writeFileSync(file, data);
    const pcs = pieces();
    const quads = pcs.reduce((s, p) => s + serialise(p, TEX).quads.length, 0);
    console.log(`wrote ${out.length} files (${pcs.length} mesh pieces, ${quads} quads)`);
    console.log('  mesh ' + JSON.stringify(doorMesh().stats));
    for (const p of pcs.filter(p => !p.name.startsWith('shield/') || p.name === 'shield/panel_0' || p.name === 'shield/panel_15')) console.log('  ' + p.name, serialise(p, TEX).quads.length);
    const errs = audit();
    for (const e of errs) console.log('audit: ' + e);
  }
}
