// Mesh Trees P0-A: offline checks (docs/rendering/mesh_trees_p0a.md, section N).
// 1. Static audit: the sample stays development-only (dev package, dev assets and tags excluded from the jar, the
//    blocks registered only outside production, no formal source references the dev package).
// 2. Textures match tools/mesh-trees/build-tree-textures.mjs.
// 3. Compiles src/dev/.../meshtree/TreeShape.java (pure Java) with TreeCheck.java and runs it: cell reach, Oculus
//    midBlock range, budgets, turns, determinism, the Voxy mip simulation; previews go to build/mesh-trees/.
// 4. P1 natural forest (docs/rendering/mesh_trees_p1_forest.md, N): static audit of the dev-only worldgen wiring, then
//    ForestPlan (pure Java) over the real Terrain V2 plan cache of a seed with ForestCheck.java (densities, spacing,
//    determinism and generation order, buffer, cells against the planned ground, cost, test spots, a map).
// 5. P2 felling (docs/rendering/mesh_trees_p2_felling.md, O): static audit of the dev-only felling wiring, then
//    FellingRules, TreeCut and FellingLedger (pure Java) with FellCheck.java (chops, drops, cut geometry, fall curves,
//    rest angles, directions, the ledger, shared cells of natural trees over the same plan cache).
//   node tools/mesh-trees/check.mjs [seed centreX centreZ]      (default: AFL_TREE_01's seed and spawn)
// Needs a JDK 17 (JAVA_HOME, or javac on PATH).
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const read = p => fs.readFileSync(path.join(ROOT, p), 'utf8');
let failures = 0;
const require = (ok, what) => { if (!ok) { failures++; console.log('FAIL ' + what); } };

// ---- 1. static audit
const gradle = read('build.gradle');
require(/exclude 'com\/antaurora\/apofirstlight\/dev\/\*\*'/.test(gradle), 'the jar drops the development package');
require(/exclude 'assets\/afl_tree_demo\/\*\*'/.test(gradle), 'the jar drops the tree sample assets');
for (const f of ['tree_root', 'tree_wood', 'tree_foliage', 'tree_canopy', 'white_pine_foliage', 'white_pine_canopy'].flatMap(n => [`blockstates/dev_${n}.json`, `models/block/dev_${n}.json`])) {
  require(fs.existsSync(path.join(ROOT, 'src/dev/highway_mesh_resources/assets/apocalypse_firstlight', f)), `dev resource ${f} exists`);
  require(gradle.includes(`exclude 'assets/apocalypse_firstlight/${f}'`), `the jar drops ${f}`);
}
for (const t of ['logs', 'leaves']) {
  const p = `src/dev/highway_mesh_resources/data/minecraft/tags/blocks/${t}.json`;
  require(fs.existsSync(path.join(ROOT, p)), `dev tag ${t}.json exists`);
  require(gradle.includes(`exclude 'data/minecraft/tags/blocks/${t}.json'`), `the jar drops the dev ${t} tag`);
  require(!fs.existsSync(path.join(ROOT, `src/main/resources/data/minecraft/tags/blocks/${t}.json`)), `no formal ${t} tag collides with the dev one`);
  if (fs.existsSync(path.join(ROOT, p))) {
    const tag = JSON.parse(read(p));
    require(tag.replace === false && tag.values.every(v => typeof v === 'object' && v.required === false), `dev ${t} tag adds optional entries only`);
  }
}
const dev = 'src/dev/java/com/antaurora/apofirstlight/dev/meshtree';
require(/FMLEnvironment\.production\) return/.test(read(dev + '/TreeBlocks.java')), 'the sample blocks register only outside production');
for (const pure of ['TreeShape', 'TreeForms']) require(!/net\.minecraft|net\.minecraftforge|com\.mojang/.test(read(dev + '/' + pure + '.java')), pure + ' is pure Java');
const walk = d => fs.readdirSync(d, {withFileTypes: true}).flatMap(e => e.isDirectory() ? walk(path.join(d, e.name)) : [path.join(d, e.name)]);
for (const f of walk(path.join(ROOT, 'src/main/java')).filter(f => f.endsWith('.java')))
  require(!/dev\.meshtree/.test(fs.readFileSync(f, 'utf8')), `formal ${path.relative(ROOT, f)} uses no mesh tree sample class`);
// P1 natural forest: dev data dropped from the jar, the feature and the modifier registered only outside production, the
// gate reads the server's world folder, the modifier's biomes and removed features match the plan and the biome files
for (const f of ['forge/biome_modifier/dev_mesh_forest.json', 'worldgen/configured_feature/dev_mesh_forest.json', 'worldgen/placed_feature/dev_mesh_forest.json']) {
  require(fs.existsSync(path.join(ROOT, 'src/dev/highway_mesh_resources/data/apocalypse_firstlight', f)), `dev forest data ${f} exists`);
  require(gradle.includes(`exclude 'data/apocalypse_firstlight/${f}'`), `the jar drops ${f}`);
  require(!fs.existsSync(path.join(ROOT, 'src/main/resources/data/apocalypse_firstlight', f)), `no formal ${f}`);
}
const gate = read(dev + '/ForestGate.java');
require(/if \(FMLEnvironment\.production\) return;\s*event\.register\(ForgeRegistries\.Keys\.FEATURES/.test(gate), 'the forest feature and modifier register only outside production');
require(/getWorldPath\(LevelResource\.ROOT\)/.test(gate) && /startsWith\(TreeCommands\.PREFIX\)/.test(gate) && /generated\(root\)/.test(gate), 'the gate reads the server world folder, its prefix and whether it has chunks');
require(!/net\.minecraft|net\.minecraftforge|com\.mojang/.test(read(dev + '/ForestPlan.java')), 'ForestPlan is pure Java');
{
  const mod = JSON.parse(read('src/dev/highway_mesh_resources/data/apocalypse_firstlight/forge/biome_modifier/dev_mesh_forest.json'));
  const plan = read(dev + '/ForestPlan.java');
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
}
// P2 felling: the stump and the item models dropped from the jar, no formal namesakes, registrations outside
// production only, the server gated to AFL_TREE_* overworlds, the rules pure
const P3 = [['red_oak', 'red_oak_leaves'], ['shagbark_hickory', 'shagbark_hickory_leaves'], ['red_maple', 'red_maple_leaves'],
  ['eastern_hemlock', 'eastern_hemlock_needles']];
{
  const A = 'src/dev/highway_mesh_resources/assets/apocalypse_firstlight/';
  const files = ['blockstates/dev_tree_stump.json', 'models/block/dev_tree_stump.json',
    ...['white_oak_log', 'white_pine_log', 'white_oak_branch', 'white_pine_branch', 'white_oak_leaves', 'white_pine_needles'].map(i => `models/item/${i}.json`),
    // P2.2: the log blocks and the 3D leaf material
    ...['white_oak_log', 'white_pine_log'].flatMap(i => [`blockstates/${i}.json`, `models/block/${i}.json`]),
    'models/item/white_oak_leaves_3d.json', 'models/item/white_pine_needles_3d.json',
    // P3: per new species its crown blocks, log block, branch and leaf material
    ...P3.flatMap(([id, leaves]) => [`blockstates/dev_${id}_foliage.json`, `blockstates/dev_${id}_canopy.json`, `models/block/dev_${id}_foliage.json`,
      `models/block/dev_${id}_canopy.json`, `blockstates/${id}_log.json`, `models/block/${id}_log.json`, `models/item/${id}_log.json`,
      `models/item/${id}_branch.json`, `models/item/${leaves}.json`, `models/item/${leaves}_3d.json`])];
  for (const f of files) {
    require(fs.existsSync(path.join(ROOT, A, f)), `dev felling resource ${f} exists`);
    require(gradle.includes(`exclude 'assets/apocalypse_firstlight/${f}'`), `the jar drops ${f}`);
    require(!fs.existsSync(path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight', f)), `no formal ${f}`);
  }
  const reg = read(dev + '/FellingRegistry.java'), srv = read(dev + '/FellingServer.java');
  require(/if \(FMLEnvironment\.production\) return;\s*event\.register\(ForgeRegistries\.Keys\.BLOCKS[\s\S]*?\}\);\s*event\.register\(ForgeRegistries\.Keys\.ITEMS/.test(reg),
    'the log blocks, the felling items and the entity register only outside production');
  require(/if \(!testWorld\(level\)\)/.test(srv) && /TreeCommands\.PREFIX/.test(srv) && /Level\.OVERWORLD/.test(srv), 'chopping is gated to AFL_TREE_* overworlds');
  require(/addFreshEntity\(fall\)\) \{[^}]*return; \}\s*var journal/.test(srv), 'the falling tree is added before any cell is removed');
  for (const pure of ['FellingRules', 'TreeCut', 'FellingLedger', 'LogShape'])
    require(!/net\.minecraft|net\.minecraftforge|com\.mojang/.test(read(dev + '/' + pure + '.java')), pure + ' is pure Java');
  const logs = JSON.parse(read('src/dev/highway_mesh_resources/data/minecraft/tags/blocks/logs.json'));
  require(logs.values.some(v => v.id === 'apocalypse_firstlight:dev_tree_stump' && v.required === false), 'the stump is an optional entry of the dev logs tag');
  // P2.2 log blocks: self-dropping (dev loot tables dropped from the jar), axe through the dev logs tag, no 2D log icon
  for (const log of ['white_oak_log', 'white_pine_log', ...P3.map(([id]) => id + '_log')]) {
    const loot = `data/apocalypse_firstlight/loot_tables/blocks/${log}.json`;
    require(fs.existsSync(path.join(ROOT, 'src/dev/highway_mesh_resources', loot)), `dev loot table ${log} exists`);
    require(gradle.includes(`exclude '${loot}'`), `the jar drops the ${log} loot table`);
    require(!fs.existsSync(path.join(ROOT, 'src/main/resources', loot)), `no formal ${log} loot table`);
    const table = JSON.parse(read('src/dev/highway_mesh_resources/' + loot));
    require(table.pools[0].entries[0].name === 'apocalypse_firstlight:' + log, `${log} drops itself`);
    require(logs.values.some(v => v.id === 'apocalypse_firstlight:' + log && v.required === false), `${log} is an optional entry of the dev logs tag`);
    require(!fs.existsSync(path.join(ROOT, 'src/dev/highway_mesh_resources/assets/afl_tree_demo/textures/item', log + '.png')), `no 2D icon for the ${log} block`);
  }
  // P3: every species' crown blocks in the dev leaves tag; every species in the forms, the textures and the lang
  const leavesTag = JSON.parse(read('src/dev/highway_mesh_resources/data/minecraft/tags/blocks/leaves.json'));
  const lang = JSON.parse(read('src/dev/highway_mesh_resources/assets/afl_tree_demo/lang/zh_cn.json'));
  const T = 'src/dev/highway_mesh_resources/assets/afl_tree_demo/textures/';
  for (const [id, leaves] of P3) {
    for (const k of ['foliage', 'canopy']) require(leavesTag.values.some(v => v.id === `apocalypse_firstlight:dev_${id}_${k}`), `dev_${id}_${k} in the dev leaves tag`);
    for (const t of [`block/${id}_bark`, `block/${leaves}`, `block/${leaves}_mid`, `block/${leaves}_far`, `block/${leaves}_proxy`, `block/${id}_cut`,
      `block/${id}_end_grain`, `block/${id}_log_end`, `item/${id}_branch`, `item/${leaves}`])
      require(fs.existsSync(path.join(ROOT, T, t + '.png')), `texture ${t}`);
    for (const k of [`block.apocalypse_firstlight.${id}_log`, `item.apocalypse_firstlight.${id}_branch`, `item.apocalypse_firstlight.${leaves}`,
      `block.apocalypse_firstlight.dev_${id}_foliage`]) require(typeof lang[k] === 'string', `zh_cn ${k}`);
  }
  const forms = read(dev + '/TreeForms.java');
  for (const [id] of P3) require(forms.includes(`"${id}"`), `${id} in the species table`);
  const lb = read(dev + '/TreeLogBlock.java');
  require(!/requiresCorrectToolForDrops/.test(lb) && /strength\(2\.0F\)/.test(lb) && /getMaxLightLevel\(\)/.test(lb), 'the log block: vanilla log strength, no tool requirement, blocks light');
}
console.log(failures === 0 ? 'static audit: development-only wiring OK' : `static audit: ${failures} failures`);

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
  const status = run(exe('java'), ['-Djava.awt.headless=true', '-cp', out, 'com.antaurora.apofirstlight.dev.meshtree.TreeCheck',
    path.join(ROOT, 'src/dev/highway_mesh_resources/assets/afl_tree_demo/textures/block'), previews]);
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
      else forest = run(exe('java'), ['-Xmx3g', '-Djava.awt.headless=true', '-cp', fout, 'com.antaurora.apofirstlight.dev.meshtree.ForestCheck', cache, seed, previews, cx, cz]);
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
      else felling = run(exe('java'), ['-Xmx3g', '-cp', fout, 'com.antaurora.apofirstlight.dev.meshtree.FellCheck', ...(fs.existsSync(cache) ? [cache, seed, cx, cz] : [])]);
    } finally {
      fs.rmSync(fout, {recursive: true, force: true});
    }
  }
  process.exitCode = status !== 0 ? status : forest !== 0 ? forest : felling !== 0 ? felling : failures === 0 ? 0 : 1;
} finally {
  fs.rmSync(out, {recursive: true, force: true});
}
