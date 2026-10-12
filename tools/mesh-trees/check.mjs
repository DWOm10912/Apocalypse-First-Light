// Mesh Trees offline checks (P0-A docs/rendering/mesh_trees_p0a.md N ... P4 mesh_trees_p4_formal.md P).
// 1. Static audit (P4, formal): the trees live in src/main (package com.antaurora.apofirstlight.tree), the development
//    commands in src/dev/java and out of the jar; no formal class depends on them or on a dev package; common classes
//    load no client class; no production gate is left; formal ids, the old-id remap, resources, tags, lang, loot,
//    the forest's biome modifier and config, the jar excludes.
// 2. Textures match tools/mesh-trees/build-tree-textures.mjs.
// 3. TreeShape (pure Java) with TreeCheck.java: cell reach, Oculus midBlock range, budgets, turns, determinism, the Voxy
//    mip simulation; previews go to build/mesh-trees/.
// 4. ForestPlan over the real Terrain V2 plan cache of a seed with ForestCheck.java (densities, mixes, spacing,
//    determinism and generation order, cells against the planned ground, cost, test spots, a map).
// 5. FellingRules, TreeCut, FellingLedger, LogShape with FellCheck.java (chops, drops, cut geometry, fall curves, rest
//    angles, directions, the ledger, log blocks, shared cells of natural trees over the same plan cache).
//   node tools/mesh-trees/check.mjs [seed centreX centreZ]      (default: AFL_TREE_01's seed and spawn)
// Needs a JDK 17 (JAVA_HOME, or javac on PATH).
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const read = p => fs.readFileSync(path.join(ROOT, p), 'utf8');
const exists = p => fs.existsSync(path.join(ROOT, p));
let failures = 0;
const require = (ok, what) => { if (!ok) { failures++; console.log('FAIL ' + what); } };
const walk = d => fs.readdirSync(d, {withFileTypes: true}).flatMap(e => e.isDirectory() ? walk(path.join(d, e.name)) : [path.join(d, e.name)]);
const code = s => s.replace(/\/\*[\s\S]*?\*\//g, '').replace(/\/\/.*$/gm, '');   // without comments

// ---- 1. static audit
const gradle = read('build.gradle');
const dev = 'src/main/java/com/antaurora/apofirstlight/tree';          // the formal package (named dev in P0-P3)
const devCommands = 'src/dev/java/com/antaurora/apofirstlight/tree';
const A = 'src/main/resources/assets/apocalypse_firstlight/';
const SPECIES = [['white_oak', 'white_oak_leaves'], ['white_pine', 'white_pine_needles'], ['red_oak', 'red_oak_leaves'],
  ['shagbark_hickory', 'shagbark_hickory_leaves'], ['red_maple', 'red_maple_leaves'], ['eastern_hemlock', 'eastern_hemlock_needles']];
const P3 = SPECIES.slice(2);
{
  // where the classes are
  require(!exists('src/dev/java/com/antaurora/apofirstlight/dev/meshtree') || fs.readdirSync(path.join(ROOT, 'src/dev/java/com/antaurora/apofirstlight/dev/meshtree')).length === 0,
    'no class left in the P0-P3 dev package');
  const formal = fs.readdirSync(path.join(ROOT, dev)).filter(f => f.endsWith('.java'));
  const commands = ['TreeCommands', 'ForestCommands', 'FellingCommands'];
  for (const c of commands) {
    require(exists(`${devCommands}/${c}.java`), `${c} stays in src/dev/java`);
    require(!formal.includes(c + '.java'), `${c} is not formal`);
    require(gradle.includes(`exclude 'com/antaurora/apofirstlight/tree/${c}*'`), `the jar drops ${c}`);
  }
  // WoodModels: the rounded timber's TreeLogModel (Wood Building Blocks V1)
  const CLIENT = ['TreeCellModel', 'TreeLogModel', 'TreeModels', 'TreeClient', 'FallingTreeRenderer', 'TreeShaderIds', 'FellingClient', 'WoodModels'];
  for (const f of formal) {
    const name = f.slice(0, -5), src = code(read(`${dev}/${f}`));
    for (const c of commands) require(!new RegExp('\\b' + c + '\\b').test(src), `formal ${name} does not use the development ${c}`);
    require(!/com\.antaurora\.apofirstlight\.dev\b/.test(src), `formal ${name} uses no dev package`);
    require(!/FMLEnvironment\.production/.test(src), `formal ${name} has no development-only gate`);
    require(!/AFL_TREE_|afl_tree_demo/.test(src), `formal ${name} has no test-world gate or dev namespace`);
    if (!CLIENT.includes(name)) {
      require(!/import (net\.minecraft\.client|com\.mojang\.blaze3d|net\.minecraftforge\.client)\./.test(src), `common ${name} imports no client class`);
      for (const c of CLIENT) {
        const uses = src.split('\n').filter(l => new RegExp('\\b' + c + '\\b').test(l));
        require(uses.every(l => /DistExecutor/.test(l)), `common ${name} reaches ${c} only through DistExecutor (${uses.length})`);
      }
    } else if (name !== 'FellingClient' && name !== 'TreeClient' && name !== 'TreeModels' && name !== 'FallingTreeRenderer') {
      require(!/@Mod\.EventBusSubscriber\((?![^)]*Dist\.CLIENT)/.test(src), `client ${name} subscribes to no common bus`);
    }
    if (['TreeClient', 'TreeModels', 'FellingClient', 'WoodModels'].includes(name)) require(/@Mod\.EventBusSubscriber\([^)]*Dist\.CLIENT/.test(src), `${name} subscribes on the client only`);
  }
  for (const pure of ['TreeShape', 'TreeForms', 'FellingRules', 'TreeCut', 'FellingLedger', 'LogShape'])
    require(!/net\.minecraft|net\.minecraftforge|com\.mojang/.test(code(read(`${dev}/${pure}.java`))), pure + ' is pure Java');
  require(!/net\.minecraft|net\.minecraftforge|com\.mojang/.test(code(read(`${dev}/ForestPlan.java`))), 'ForestPlan is pure Java');
  for (const f of walk(path.join(ROOT, 'src/main/java')).filter(f => f.endsWith('.java')))
    require(!/dev\.meshtree/.test(fs.readFileSync(f, 'utf8')), `formal ${path.relative(ROOT, f)} uses no P0-P3 dev class`);

  // ids: formal names; every P0-P3 dev id remapped
  const blocks = read(`${dev}/TreeBlocks.java`), reg = read(`${dev}/FellingRegistry.java`), gate = read(`${dev}/ForestGate.java`), events = read(`${dev}/TreeServerEvents.java`);
  require(/"tree_root"/.test(blocks) && /"tree_wood"/.test(blocks) && /"tree_stump"/.test(blocks), 'formal root, wood and stump ids');
  require(/s\.id \+ \(carrier \? "_foliage" : "_canopy"\)/.test(blocks), 'formal crown ids <species>_foliage / _canopy');
  require(/"falling_tree"/.test(reg) && /"mesh_forest"/.test(gate), 'formal falling tree and forest ids');
  for (const f of formal.filter(f => f !== 'TreeServerEvents.java'))
    require(!/"dev_/.test(code(read(`${dev}/${f}`))), `formal ${f} registers no dev_ id`);
  const oldIds = ['dev_tree_root', 'dev_tree_wood', 'dev_tree_stump', 'dev_tree_foliage', 'dev_tree_canopy',
    ...SPECIES.slice(1).flatMap(([id]) => [`dev_${id}_foliage`, `dev_${id}_canopy`]), 'dev_falling_tree', 'dev_mesh_forest'];
  for (const id of oldIds) require(events.includes(`"${id}"`), `the old id ${id} is remapped`);
  require(/MissingMappingsEvent/.test(events) && /m\.remap\(target\)/.test(events), 'old ids remap through MissingMappingsEvent');
  // the formal tree ids are not registered anywhere else
  const others = walk(path.join(ROOT, 'src/main/java')).filter(f => f.endsWith('.java') && !f.replace(/\\/g, '/').includes('/apofirstlight/tree/'));
  const names = ['tree_root', 'tree_wood', 'tree_stump', 'falling_tree', 'mesh_forest', ...SPECIES.flatMap(([id, l]) => [`${id}_log`, `${id}_branch`, l, `${id}_foliage`, `${id}_canopy`])];
  for (const f of others) {
    const s = fs.readFileSync(f, 'utf8');
    for (const n of names) require(!s.includes(`"${n}"`), `${path.basename(f)} does not register ${n} again`);
  }

  // resources: blockstates, models, item models, loot, textures, tags, lang; none left in the dev resources
  const blockIds = ['tree_root', 'tree_wood', 'tree_stump', ...SPECIES.flatMap(([id]) => [`${id}_foliage`, `${id}_canopy`, `${id}_log`])];
  for (const b of blockIds) for (const f of [`blockstates/${b}.json`, `models/block/${b}.json`]) require(exists(A + f), `formal ${f}`);
  for (const [id, leaves] of SPECIES) for (const f of [`${id}_log`, `${id}_branch`, leaves, `${leaves}_3d`]) require(exists(A + `models/item/${f}.json`), `formal item model ${f}`);
  const texOk = ref => exists(A + 'textures/' + ref.split(':')[1] + '.png');
  for (const f of walk(path.join(ROOT, A, 'models')).filter(f => /(tree_|_foliage|_canopy|_log|_branch|_leaves|_needles)/.test(path.basename(f))))
    for (const m of fs.readFileSync(f, 'utf8').matchAll(/"(apocalypse_firstlight:(?:block|item)\/tree\/[\w/]+)"/g))
      require(texOk(m[1]), `${path.basename(f)}: texture ${m[1]} exists`);
  for (const [id, leaves] of SPECIES) {
    for (const t of [`${id}_bark`, leaves, `${leaves}_mid`, `${leaves}_far`, `${leaves}_proxy`, `${id}_cut`, `${id}_end_grain`, `${id}_log_end`])
      require(exists(A + `textures/block/tree/${t}.png`), `texture block/tree/${t}`);
    for (const t of [`${id}_branch`, leaves]) require(exists(A + `textures/item/tree/${t}.png`), `icon item/tree/${t}`);
    require(!exists(A + `textures/item/tree/${id}_log.png`), `no 2D icon for the ${id}_log block`);
    const loot = `src/main/resources/data/apocalypse_firstlight/loot_tables/blocks/${id}_log.json`;
    require(exists(loot) && JSON.parse(read(loot)).pools[0].entries[0].name === `apocalypse_firstlight:${id}_log`, `${id}_log drops itself`);
  }
  const tag = t => JSON.parse(read(`src/main/resources/data/minecraft/tags/blocks/${t}.json`)).values.map(v => typeof v === 'string' ? v : v.id);
  const logs = tag('logs'), leavesTag = tag('leaves');
  for (const b of ['tree_root', 'tree_wood', 'tree_stump', ...SPECIES.map(([id]) => `${id}_log`)]) require(logs.includes('apocalypse_firstlight:' + b), `${b} in minecraft:logs (axe, Terrain Smooth)`);
  for (const [id] of SPECIES) for (const k of ['foliage', 'canopy']) require(leavesTag.includes(`apocalypse_firstlight:${id}_${k}`), `${id}_${k} in minecraft:leaves`);
  for (const lang of ['en_us', 'zh_cn']) {
    const l = JSON.parse(read(A + `lang/${lang}.json`));
    for (const b of blockIds) require(typeof l[`block.apocalypse_firstlight.${b}`] === 'string', `${lang}: block ${b}`);
    for (const [id, leaves] of SPECIES) for (const i of [`${id}_branch`, leaves]) require(typeof l[`item.apocalypse_firstlight.${i}`] === 'string', `${lang}: item ${i}`);
    for (const k of ['entity.apocalypse_firstlight.falling_tree', 'message.apocalypse_firstlight.felling.need_axe', 'message.apocalypse_firstlight.felling.not_loaded',
      'message.apocalypse_firstlight.felling.no_fall']) require(typeof l[k] === 'string', `${lang}: ${k}`);
    // Jade (P4 U2): the trunk cells are one block for all species, so the species' trunk / stump names come from the provider
    for (const [id] of SPECIES) for (const k of ['trunk', 'stump'])
      require(typeof l[`jade.apocalypse_firstlight.tree.${k}.${id}`] === 'string', `${lang}: Jade ${k} name of ${id}`);
    require(typeof l['config.jade.plugin_apocalypse_firstlight.tree'] === 'string', `${lang}: Jade config name of the tree provider`);
  }
  const jade = read('src/main/java/com/antaurora/apofirstlight/compat/jade/AflJadePlugin.java');
  require(/TreeBlocks\.Root\.class/.test(jade) && /TreeBlocks\.Wood\.class/.test(jade) && /TreeBlocks\.Stump\.class/.test(jade)
    && /registerBlockComponent\(TreeSpeciesJadeProvider\.INSTANCE/.test(jade) && /registerBlockIcon\(TreeSpeciesJadeProvider\.INSTANCE/.test(jade),
    'Jade names the species on root, wood and stump (name and icon)');
  const leftovers = walk(path.join(ROOT, 'src/dev/highway_mesh_resources')).map(f => path.relative(ROOT, f).replace(/\\/g, '/'))
    .filter(f => /afl_tree_demo|tree_|_foliage|_canopy|mesh_forest|tags\/blocks\/(logs|leaves)|_log\.json|_branch|_leaves|_needles/.test(f));
  require(leftovers.length === 0, `no tree resource left in the dev resources (${leftovers.slice(0, 3)})`);
  for (const f of walk(path.join(ROOT, 'src/main/resources')).filter(f => f.endsWith('.json')))
    require(!/afl_tree_demo|apocalypse_firstlight:dev_(tree|white|red|shagbark|eastern|mesh|falling)/.test(fs.readFileSync(f, 'utf8')), `${path.basename(f)} names no dev tree resource`);
  // the jar keeps every tree resource
  for (const line of gradle.split('\n').filter(l => /^\s*exclude '/.test(l)))
    require(!/tree|_log\.json|_branch|_leaves|_needles|_foliage|_canopy|mesh_forest|tags\/blocks\/(logs|leaves)/.test(line) || /tree\/(TreeCommands|ForestCommands|FellingCommands)\*/.test(line),
      `the jar keeps the trees (${line.trim()})`);

  // the forest: formal data, the modifier covers exactly the plan stands, the config decides
  for (const f of ['forge/biome_modifier/mesh_forest.json', 'worldgen/configured_feature/mesh_forest.json', 'worldgen/placed_feature/mesh_forest.json'])
    require(exists('src/main/resources/data/apocalypse_firstlight/' + f), `formal forest data ${f}`);
  const mod = JSON.parse(read('src/main/resources/data/apocalypse_firstlight/forge/biome_modifier/mesh_forest.json'));
  require(mod.type === 'apocalypse_firstlight:mesh_forest' && mod.feature === 'apocalypse_firstlight:mesh_forest', 'the modifier and its feature are mesh_forest');
  const plan = read(`${dev}/ForestPlan.java`);
  const covered = [...plan.matchAll(/STANDS\[EcologyBiomes\.(\w+)\] = new Stand/g)].map(m => m[1]);
  const ids = {GRASSLAND: 'temperate_grassland', WOODLAND: 'oak_hickory_woodland', RIDGE_FOREST: 'ridge_oak_forest', HEMLOCK_HOLLOW: 'hemlock_hollow',
    COASTAL_PINE: 'coastal_pine_oak_forest', COASTAL_CLEARING: 'coastal_sandy_clearing', SLOPE_FOREST: 'mixed_mesophytic_forest',
    RIPARIAN_FOREST: 'riparian_forest', SWAMP_FOREST: 'coastal_swamp_forest'};
  require(covered.length === mod.biomes.length && covered.every(c => mod.biomes.includes('apocalypse_firstlight:' + ids[c])), 'the modifier covers exactly the plan stands');
  for (const b of mod.biomes) {
    const biome = JSON.parse(read('src/main/resources/data/apocalypse_firstlight/worldgen/biome/' + b.split(':')[1] + '.json'));
    const trees = biome.features.flat().filter(f => /trees|tree_/.test(f));
    require(trees.length > 0 && trees.every(f => mod.remove.includes(f)), `${b}: every tree feature is removed (${trees})`);
  }
  require(/MeshForestConfig\.ENABLED\.get\(\)/.test(gate), 'the forest gate reads the server config');
  require(/registerConfig\([\s\S]{0,120}ModConfig\.Type\.SERVER,[\s\S]{0,120}MeshForestConfig\.SPEC/.test(read('src/main/java/com/antaurora/apofirstlight/ApocalypseFirstLight.java')),
    'the forest config is a registered SERVER config');
  require(/static final int VERSION = \d+;/.test(plan) && /ForestRecord/.test(events), 'the forest records its generation version per world');

  // felling: the order of the fell, the legacy ledger, the log blocks
  const srv = read(`${dev}/FellingServer.java`);
  require(/addFreshEntity\(fall\)\) \{[^}]*return; \}\s*var journal/.test(srv), 'the falling tree is added before any cell is removed');
  require(/LEGACY = "afl_dev_mesh_tree_felling"/.test(srv), 'a P0-P3 test world keeps its felling ledger');
  const lb = read(`${dev}/TreeLogBlock.java`);
  require(!/requiresCorrectToolForDrops/.test(lb) && /strength\(2\.0F\)/.test(lb) && /getMaxLightLevel\(\)/.test(lb), 'the log block: vanilla log strength, no tool requirement, blocks light');
  const forms = read(`${dev}/TreeForms.java`);
  for (const [id] of SPECIES) require(forms.includes(`"${id}"`), `${id} in the species table`);
  require(/keepQuads = FMLEnvironment\.dist\.isClient\(\)/.test(blocks), 'a dedicated server keeps no draw geometry');
}
console.log(failures === 0 ? 'static audit: formal tree wiring OK' : `static audit: ${failures} failures`);

// ---- 2. textures
const tex = spawnSync(process.execPath, [path.join(ROOT, 'tools/mesh-trees/build-tree-textures.mjs'), '--check'], {stdio: 'inherit'});
require(tex.status === 0, 'textures match the generator');

// ---- 3. geometry
const sources = [dev + '/TreeForms.java', dev + '/TreeShape.java', 'tools/mesh-trees/TreeCheck.java'].map(p => path.join(ROOT, p));
const exe = n => (process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin', n) : n) + (process.platform === 'win32' && process.env.JAVA_HOME ? '.exe' : '');
const out = fs.mkdtempSync(path.join(os.tmpdir(), 'afl-treecheck-'));
const previews = path.join(ROOT, 'build/mesh-trees');
const run = (cmd, a) => { const r = spawnSync(cmd, a, {stdio: 'inherit'}); if (r.error) throw r.error; return r.status; };
try {
  if (run(exe('javac'), ['-encoding', 'UTF-8', '-nowarn', '-d', out, ...sources]) !== 0) process.exit(2);
  const status = run(exe('java'), ['-Djava.awt.headless=true', '-cp', out, 'com.antaurora.apofirstlight.tree.TreeCheck',
    path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight/textures/block/tree'), previews]);
  // 4. the natural forest over a real plan
  const [seed, cx, cz] = process.argv.length >= 5 ? process.argv.slice(2, 5) : ['-4770121886729009152', '-856', '856'];
  const cache = path.join(ROOT, 'run/afl_cache/terrain_plan', `afl_terrain_plan_v2_r1_${(BigInt(seed) & ((1n << 64n) - 1n)).toString()}.bin`);
  let forest = 0;
  if (!fs.existsSync(cache)) console.log(`forest: no plan cache for seed ${seed} (${cache}); open a world with that seed once`), forest = 1;
  else {
    const fout = fs.mkdtempSync(path.join(os.tmpdir(), 'afl-forestcheck-'));
    try {
      const sp = [path.join(ROOT, 'src/main/java'), path.join(ROOT, 'src/dev/java')].join(path.delimiter);
      if (run(exe('javac'), ['-encoding', 'UTF-8', '-nowarn', '-d', fout, '-sourcepath', sp, path.join(ROOT, 'tools/mesh-trees/ForestCheck.java')]) !== 0) forest = 2;
      else forest = run(exe('java'), ['-Xmx3g', '-Djava.awt.headless=true', '-cp', fout, 'com.antaurora.apofirstlight.tree.ForestCheck', cache, seed, previews, cx, cz]);
    } finally {
      fs.rmSync(fout, {recursive: true, force: true});
    }
  }
  // 5. the felling
  let felling = 0;
  {
    const fout = fs.mkdtempSync(path.join(os.tmpdir(), 'afl-fellcheck-'));
    try {
      const sp = [path.join(ROOT, 'src/main/java'), path.join(ROOT, 'src/dev/java')].join(path.delimiter);
      if (run(exe('javac'), ['-encoding', 'UTF-8', '-nowarn', '-d', fout, '-sourcepath', sp, path.join(ROOT, 'tools/mesh-trees/FellCheck.java')]) !== 0) felling = 2;
      else felling = run(exe('java'), ['-Xmx3g', '-cp', fout, 'com.antaurora.apofirstlight.tree.FellCheck', ...(fs.existsSync(cache) ? [cache, seed, cx, cz] : [])]);
    } finally {
      fs.rmSync(fout, {recursive: true, force: true});
    }
  }
  process.exitCode = status !== 0 ? status : forest !== 0 ? forest : felling !== 0 ? felling : failures === 0 ? 0 : 1;
} finally {
  fs.rmSync(out, {recursive: true, force: true});
}
