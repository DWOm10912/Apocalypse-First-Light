// Wood Building Blocks V1 (docs/models/wood_building_blocks_v1.md, 2026-10-12): the planks, rounded timber and beams of
// the six AFL tree species.
// Textures: since 2026-10-20 (user: "现在各类木板没有换贴图") every texture of these blocks comes from CC0 photos,
// tools/build-wood-photo-v1.mjs: <species>_planks (four 25 cm boards from the timber photo, staggered butt joints),
// <species>_timber_side (planed, grain along v) and <species>_timber_end (a square from inside the cross-section photo,
// drawn inside the timber's rounded outline, WoodBlocks.TIMBER_RADIUS). The procedural planks, side and end of
// 2026-10-12 (straight grain from the trees' palettes) are gone.
// Also writes the block / item models, blockstates and loot tables (one self drop each). The tags (axe, wood break
// noise), item mass and lang are edited by hand; --check audits them.
//   node tools/build-wood-blocks-v1.mjs                 -> writes models, blockstates, loot tables
//   node tools/build-wood-blocks-v1.mjs --check         -> verifies every output is up to date and the hand-edited data
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

/** The species in Species order. */
export const SPECIES = ['white_oak', 'white_pine', 'red_oak', 'shagbark_hickory', 'red_maple', 'eastern_hemlock'].map(id => ({id}));

// ---- assets and data ----
const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const ASSETS = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight');
const DATA = path.join(ROOT, 'src/main/resources/data');
const json = v => JSON.stringify(v, null, 2) + '\n';
const NS = 'apocalypse_firstlight:block/wood/';
export const KINDS = ['planks', 'rounded_timber', 'beam'];
export const IDS = SPECIES.flatMap(sp => KINDS.map(k => `${sp.id}_${k}`));

/** The beam along Y, 8 x 8 / 16: sides from the middle of the side texture, ends from the middle of the end texture. */
function beamModel(id) {
  const side = {texture: '#side', uv: [4, 0, 12, 16]};
  return {parent: 'minecraft:block/block', textures: {particle: NS + id + '_timber_side', side: NS + id + '_timber_side', end: NS + id + '_timber_end'},
    elements: [{from: [4, 0, 4], to: [12, 16, 12], faces: {
      down: {texture: '#end', uv: [4, 4, 12, 12], cullface: 'down'}, up: {texture: '#end', uv: [4, 4, 12, 12], cullface: 'up'},
      north: side, south: side, west: side, east: side}}]};
}

export function outputs() {
  const out = [];
  const axes = model => ({variants: {'axis=y': {model}, 'axis=z': {model, x: 90}, 'axis=x': {model, x: 90, y: 90}}});
  for (const sp of SPECIES) {
    const id = sp.id, m = k => `apocalypse_firstlight:block/wood/${id}_${k}`;
    out.push([path.join(ASSETS, 'models/block/wood', id + '_planks.json'), json({parent: 'minecraft:block/cube_all', textures: {all: NS + id + '_planks'}})]);
    // the rounded timber's geometry is TreeLogModel's (WoodModels); the JSON holds the particle and the item transforms
    out.push([path.join(ASSETS, 'models/block/wood', id + '_rounded_timber.json'), json({parent: 'minecraft:block/block', textures: {particle: NS + id + '_timber_side'}})]);
    out.push([path.join(ASSETS, 'models/block/wood', id + '_beam.json'), json(beamModel(id))]);
    out.push([path.join(ASSETS, 'blockstates', id + '_planks.json'), json({variants: {'': {model: m('planks')}}})]);
    out.push([path.join(ASSETS, 'blockstates', id + '_rounded_timber.json'), json({variants: {'': {model: m('rounded_timber')}}})]);
    out.push([path.join(ASSETS, 'blockstates', id + '_beam.json'), json(axes(m('beam')))]);
    for (const k of KINDS) {
      out.push([path.join(ASSETS, 'models/item', `${id}_${k}.json`), json({parent: m(k)})]);
      out.push([path.join(DATA, 'apocalypse_firstlight/loot_tables/blocks', `${id}_${k}.json`), json({type: 'minecraft:block', pools: [{rolls: 1, bonus_rolls: 0,
        entries: [{type: 'minecraft:item', name: `apocalypse_firstlight:${id}_${k}`}], conditions: [{condition: 'minecraft:survives_explosion'}]}],
        random_sequence: `apocalypse_firstlight:blocks/${id}_${k}`})]);
    }
  }
  return out;
}

/** The hand-edited data: the axe tag, the wood break-noise tag, no tier tag, no planks / logs tags, item mass, both lang files. */
function audit() {
  const fails = [];
  const read = p => JSON.parse(fs.readFileSync(path.join(ROOT, p), 'utf8'));
  const axe = read('src/main/resources/data/minecraft/tags/blocks/mineable/axe.json').values;
  // not in #minecraft:planks / logs, so the wood break noise needs them by name
  const noise = read('src/main/resources/data/apocalypse_firstlight/tags/blocks/noise_wood_blocks.json').values;
  const tiers = ['needs_stone_tool', 'needs_iron_tool', 'needs_diamond_tool'].flatMap(t => { const f = path.join(DATA, 'minecraft/tags/blocks', t + '.json'); return fs.existsSync(f) ? JSON.parse(fs.readFileSync(f, 'utf8')).values : []; });
  const others = ['minecraft/tags/blocks/planks.json', 'minecraft/tags/items/planks.json', 'minecraft/tags/blocks/logs.json', 'minecraft/tags/items/logs.json']
    .flatMap(t => { const f = path.join(DATA, t); return fs.existsSync(f) ? JSON.parse(fs.readFileSync(f, 'utf8')).values : []; });
  const mass = read('src/main/resources/data/apocalypse_firstlight/item_mass/afl_content_v1.json');
  const en = read('src/main/resources/assets/apocalypse_firstlight/lang/en_us.json'), zh = read('src/main/resources/assets/apocalypse_firstlight/lang/zh_cn.json');
  const massOf = id => { const e = mass.items?.[id] ?? mass[id]; return e && typeof e === 'object' ? e.unit_mass_kg : e; };
  // the timber's corner radius: the model (WoodBlocks) and FellCheck's prism test agree on it
  const src = p => fs.readFileSync(path.join(ROOT, p), 'utf8');
  if (!src('src/main/java/com/antaurora/apofirstlight/tree/WoodBlocks.java').includes('TIMBER_RADIUS = 1.5F / 16;')) fails.push('WoodBlocks.TIMBER_RADIUS is not 1.5F / 16');
  if (!src('tools/mesh-trees/FellCheck.java').includes('TIMBER_RADIUS = 1.5F / 16;')) fails.push('FellCheck.TIMBER_RADIUS is not 1.5F / 16');
  for (const id of IDS) {
    const full = 'apocalypse_firstlight:' + id;
    if (!axe.includes(full)) fails.push(full + ' not in minecraft:mineable/axe');
    if (!noise.includes(full)) fails.push(full + ' not in apocalypse_firstlight:noise_wood_blocks');
    if (tiers.includes(full)) fails.push(full + ' has a tool tier tag (wood: none)');
    if (others.includes(full)) fails.push(full + ' is in a vanilla planks / logs tag (would enable vanilla recipes)');
    if (!(massOf(full) > 0)) fails.push(full + ' has no item mass');
    for (const [name, lang] of [['en_us', en], ['zh_cn', zh]]) if (!lang['block.apocalypse_firstlight.' + id]) fails.push(full + ' has no ' + name + ' name');
  }
  return fails;
}


const isMain = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  if (process.argv.includes('--check')) {
    const stale = [];
    for (const [file, data] of outputs()) { const cur = fs.existsSync(file) ? fs.readFileSync(file) : null; if (!cur || !cur.equals(Buffer.isBuffer(data) ? data : Buffer.from(data))) stale.push('stale ' + path.relative(ROOT, file)); }
    const fails = [...stale, ...audit()];
    for (const f of fails.slice(0, 30)) console.log('FAIL ' + f);
    console.log(fails.length ? `CHECK FAILED: ${fails.length}` : `CHECK OK (${IDS.length} blocks)`);
    process.exit(fails.length ? 1 : 0);
  } else {
    const list = outputs();
    for (const [file] of list) fs.mkdirSync(path.dirname(file), {recursive: true});
    for (const [file, data] of list) fs.writeFileSync(file, data);
    console.log('wrote ' + list.length + ' files');
  }
}
