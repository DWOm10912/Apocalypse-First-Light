// Terrain Smooth V1: offline checks (docs/worldgen/terrain_smooth_v1.md).
// 1. Static audit of the release wiring: the formal mixin config lists every Terrain Smooth mixin (the ServerLevel block
//    change hook above all: P1's stale server cache), the jar keeps the formal package and drops the dev one, and the
//    common package never touches client classes (a dedicated server must not load them).
// 2. Compiles src/main/.../terrainsmooth/SmoothRules.java (pure Java, no Minecraft) with tools/terrain-smooth/SmoothCheck.java
//    and SeamAudit.java into a temporary folder and runs them: the rules' checks, then the seam audit's synthetic
//    earthworks scenes (graded_turf beside smoothed ground must leave no see-through hole).
//   node tools/terrain-smooth/check.mjs
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
const mixins = JSON.parse(read('src/main/resources/apocalypse_firstlight.mixins.json'));
const common = mixins.mixins || [], client = mixins.client || [];
for (const m of ['TerrainSmoothBlockStateMixin', 'TerrainSmoothBlockCollisionsMixin', 'TerrainSmoothLevelMixin', 'TerrainSmoothServerLevelMixin', 'TerrainSmoothLevelChunkMixin',
                 'TerrainSmoothMoveControlMixin', 'TerrainSmoothPathNavigationMixin']) {
  require(common.includes(m), `formal mixin config lists ${m} in the common section`);
  require(fs.existsSync(path.join(ROOT, 'src/main/java/com/antaurora/apofirstlight/mixin', m + '.java')), `${m}.java exists`);
}
require(/@Mixin\(ServerLevel\.class\)/.test(read('src/main/java/com/antaurora/apofirstlight/mixin/TerrainSmoothServerLevelMixin.java'))
  && /onBlockStateChange/.test(read('src/main/java/com/antaurora/apofirstlight/mixin/TerrainSmoothServerLevelMixin.java')), 'the server block change hook targets ServerLevel#onBlockStateChange');
require(!fs.existsSync(path.join(ROOT, 'src/dev/highway_mesh_resources/apocalypse_firstlight_dev.mixins.json')), 'no development mixin config left');
const gradle = read('build.gradle');
require(!/mixin\.config/.test(gradle), 'no run argument loads extra mixin configs (formal mixins load from the jar manifest)');
require(/exclude 'com\/antaurora\/apofirstlight\/dev\/\*\*'/.test(gradle), 'the jar drops the development package');
require(!/exclude '[^']*terrainsmooth/.test(gradle), 'the jar keeps the formal terrainsmooth package');
const formal = 'src/main/java/com/antaurora/apofirstlight/terrainsmooth';
for (const f of fs.readdirSync(path.join(ROOT, formal)).filter(f => f.endsWith('.java'))) {
  const src = read(formal + '/' + f).replace(/DistExecutor\.unsafeRunWhenOn\(Dist\.CLIENT,[\s\S]*?\)\)\);/g, '');   // the standard client hand-off
  require(!/net\.minecraft\.client|terrainsmooth\.client|com\.mojang\.blaze3d/.test(src), `common ${f} uses no client classes`);
  require(!/apofirstlight\.dev\./.test(src), `formal ${f} uses no development classes`);
}
for (const f of fs.readdirSync(path.join(ROOT, formal, 'client')).filter(f => f.endsWith('.java')))
  require(!/apofirstlight\.dev\./.test(read(formal + '/client/' + f)), `formal client/${f} uses no development classes`);
require(/registerConfig\([\s\S]{0,200}TerrainSmoothConfig\.SPEC/.test(read('src/main/java/com/antaurora/apofirstlight/ApocalypseFirstLight.java')), 'the config is registered (SERVER type)');
require(/TerrainSmoothStatePacket::handle/.test(read('src/main/java/com/antaurora/apofirstlight/network/AflNetwork.java')), 'the state packet is registered');
console.log(failures === 0 ? 'static audit: release wiring OK' : `static audit: ${failures} failures`);

// ---- 2. rules
const sources = [formal + '/SmoothRules.java', 'tools/terrain-smooth/SmoothCheck.java', 'tools/terrain-smooth/SeamAudit.java'].map(p => path.join(ROOT, p));
const exe = n => (process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin', n) : n) + (process.platform === 'win32' && process.env.JAVA_HOME ? '.exe' : '');
const out = fs.mkdtempSync(path.join(os.tmpdir(), 'afl-smoothcheck-'));
const run = (cmd, a) => { const r = spawnSync(cmd, a, {stdio: 'inherit'}); if (r.error) throw r.error; return r.status; };
try {
  if (run(exe('javac'), ['-encoding', 'UTF-8', '-nowarn', '-d', out, ...sources]) !== 0) process.exit(2);
  const status = run(exe('java'), ['-cp', out, 'com.antaurora.apofirstlight.terrainsmooth.SmoothCheck']);
  const seams = run(exe('java'), ['-cp', out, 'com.antaurora.apofirstlight.terrainsmooth.SeamAudit']);
  process.exitCode = status !== 0 ? status : seams !== 0 ? seams : failures === 0 ? 0 : 1;
} finally {
  fs.rmSync(out, {recursive: true, force: true});
}
