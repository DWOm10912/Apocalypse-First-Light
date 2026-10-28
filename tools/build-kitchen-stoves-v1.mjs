// Kitchen stoves (Cooking V1 K1c, docs/gameplay/cooking_v1_plan.md 7.4; cabin/KitchenStoves): the old enamel wood
// cookstove (forest home) and the 30 inch propane range (town houses), modelled and baked in Blender:
//   blender -b --factory-startup --python tools/stove/kitchen_build.py -- --model cookstove_v1 --bake 4096
//   blender -b --factory-startup --python tools/stove/kitchen_build.py -- --model propane_range_v1
//   blender -b --factory-startup --python tools/stove/kitchen_export.py -- --model cookstove_v1   (and propane_range_v1)
// then copied in here from design/furniture/<name>/: the cabin pieces (cabin_mesh/kitchen/<name>_body, and the
// cookstove's upper cell <name>_top), the baked LabPBR sprite (textures/block/kitchen/<name>_body + _s / _n), the block
// and item models (the cabin loader; the cookstove's item shows both cells), blockstates and loot tables (the cookstove
// drops from its lower half). Tags, item mass and lang are edited by hand; --check audits them.
//   node tools/build-kitchen-stoves-v1.mjs            -> writes every output
//   node tools/build-kitchen-stoves-v1.mjs --check    -> verifies every output is up to date and the hand-edited data
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const ASSETS = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight');
const DATA = path.join(ROOT, 'src/main/resources/data');
const NS = 'apocalypse_firstlight:';
const json = v => JSON.stringify(v, null, 2) + '\n';
/** The blocks: id, two cells high, item mass (kg), mining tier, names. */
export const BLOCKS = [
  {id: 'wood_cookstove', tall: true, mass: 180, tier: 'iron', en: 'Enamel Wood Cookstove', zh: '搪瓷柴灶'},
  {id: 'propane_range', tall: false, mass: 68, tier: 'stone', en: 'Propane Range', zh: '丙烷灶'},
];

function outputs() {
  const out = [];
  for (const b of BLOCKS) {
    const D = path.join(ROOT, 'design/furniture', b.id), sprite = b.id + '_body';
    const cells = b.tall ? ['body', 'top'] : ['body'];
    for (const c of cells) out.push([path.join(ASSETS, 'cabin_mesh/kitchen', `${b.id}_${c}.json`), fs.readFileSync(path.join(D, 'export', c + '.json'))]);
    for (const k of ['', '_s', '_n'])
      out.push([path.join(ASSETS, 'textures/block/kitchen', sprite + k + '.png'), fs.readFileSync(path.join(D, 'textures', sprite + k + '.png'))]);
    const item = cells.map(c => c === 'top' ? [`kitchen/${b.id}_top`, 0, 0, 1, 0] : [`kitchen/${b.id}_body`, 0]);
    out.push([path.join(ASSETS, 'models/block/kitchen', b.id + '.json'), json({parent: 'minecraft:block/block', loader: NS + 'cabin',
      textures: {particle: NS + 'block/kitchen/' + sprite}, item})]);
    out.push([path.join(ASSETS, 'blockstates', b.id + '.json'), json({variants: {'': {model: NS + 'block/kitchen/' + b.id}}})]);
    out.push([path.join(ASSETS, 'models/item', b.id + '.json'), json({parent: NS + 'block/kitchen/' + b.id})]);
    const conditions = [{condition: 'minecraft:survives_explosion'}];
    if (b.tall) conditions.push({condition: 'minecraft:block_state_property', block: NS + b.id, properties: {half: 'lower'}});
    out.push([path.join(DATA, 'apocalypse_firstlight/loot_tables/blocks', b.id + '.json'), json({type: 'minecraft:block',
      pools: [{rolls: 1, bonus_rolls: 0, entries: [{type: 'minecraft:item', name: NS + b.id}], conditions}], random_sequence: `${NS}blocks/${b.id}`})]);
  }
  return out;
}

const readJson = p => JSON.parse(fs.readFileSync(path.join(ROOT, p), 'utf8'));
function audit() {
  const errs = [], tags = 'src/main/resources/data/minecraft/tags/blocks/', afl = 'src/main/resources/data/apocalypse_firstlight/';
  const tagHas = (file, id) => { try { return readJson(file).values.some(v => (typeof v === 'string' ? v : v.id) === (id.includes(':') ? id : NS + id)); } catch { return false; } };
  for (const b of BLOCKS) {
    if (!tagHas(tags + 'mineable/pickaxe.json', b.id)) errs.push(`${b.id}: missing from minecraft:mineable/pickaxe`);
    for (const t of ['stone', 'iron', 'diamond']) if (tagHas(tags + `needs_${t}_tool.json`, b.id) !== (b.tier === t)) errs.push(`${b.id}: needs_${t}_tool ${b.tier === t ? 'missing' : 'unexpected'}`);
    if (!tagHas(`${afl}tags/blocks/noise_metal_blocks.json`, b.id)) errs.push(`${b.id}: missing from noise_metal_blocks`);
    if (!tagHas(`${afl}tags/items/carry/oversized.json`, b.id)) errs.push(`${b.id}: missing from carry/oversized`);
  }
  const mass = readJson(afl + 'item_mass/afl_content_v1.json').items ?? {};
  for (const b of BLOCKS) if (mass[NS + b.id]?.unit_mass_kg !== b.mass) errs.push(`${b.id}: item mass ${mass[NS + b.id]?.unit_mass_kg} (want ${b.mass})`);
  for (const lang of ['en_us', 'zh_cn']) {
    const l = readJson(`src/main/resources/assets/apocalypse_firstlight/lang/${lang}.json`);
    for (const b of BLOCKS) { const v = l['block.apocalypse_firstlight.' + b.id]; if (v !== (lang === 'en_us' ? b.en : b.zh)) errs.push(`${b.id}: lang ${lang} = ${v}`); }
  }
  for (const id of ['cast_iron_dutch_oven', 'cast_iron_skillet']) if (!tagHas(`${afl}tags/items/cookware.json`, id)) errs.push(`cookware tag: ${id} missing`);
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
    console.log(`wrote ${out.length} files`);
    for (const e of audit()) console.log('audit: ' + e);
  }
}
