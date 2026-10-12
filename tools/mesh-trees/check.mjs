// Mesh Trees P0-A: offline checks (docs/rendering/mesh_trees_p0a.md, section N).
// 1. Static audit: the sample stays development-only (dev package, dev assets and tags excluded from the jar, the
//    blocks registered only outside production, no formal source references the dev package).
// 2. Textures match tools/mesh-trees/build-tree-textures.mjs.
// 3. Compiles src/dev/.../meshtree/TreeShape.java (pure Java) with TreeCheck.java and runs it: cell reach, Oculus
//    midBlock range, budgets, turns, determinism, the Voxy mip simulation; previews go to build/mesh-trees/.
//   node tools/mesh-trees/check.mjs
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
  process.exitCode = status !== 0 ? status : failures === 0 ? 0 : 1;
} finally {
  fs.rmSync(out, {recursive: true, force: true});
}
