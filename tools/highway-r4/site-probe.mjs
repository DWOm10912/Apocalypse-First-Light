// Highway R4 sample V1: the find command's site search offline (docs/worldgen/highway_r4_sample_v1.md, F/J). Rebuilds the
// Terrain V2 plan of a seed (about 10 s, 3 GB heap) and runs the game's own R4SiteSearch around a point for each radius;
// prints the suitable count, the rejection reasons and the three best candidates with their plan commands. No Gradle, no
// Minecraft; the road-protection filter of the game is not applied.
//   node tools/highway-r4/site-probe.mjs SEED X Z RADIUS [RADIUS ...]
// Needs a JDK 17 (JAVA_HOME, or javac on PATH) and the Gson jar already in the Gradle cache.
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const args = process.argv.slice(2);
if (args.length < 4) { console.error('usage: node tools/highway-r4/site-probe.mjs SEED X Z RADIUS [RADIUS ...]'); process.exit(2); }
const exe = n => (process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin', n) : n) + (process.platform === 'win32' && process.env.JAVA_HOME ? '.exe' : '');
const base = path.join(os.homedir(), '.gradle/caches/modules-2/files-2.1/com.google.code.gson/gson');
let gson = null;
for (const v of fs.existsSync(base) ? fs.readdirSync(base).sort().reverse() : []) for (const h of fs.readdirSync(path.join(base, v))) {
  const jar = path.join(base, v, h, `gson-${v}.jar`);
  if (!gson && fs.existsSync(jar)) gson = jar;
}
if (!gson) throw new Error('Gson jar not found under ' + base);
const sep = process.platform === 'win32' ? ';' : ':';
const out = fs.mkdtempSync(path.join(os.tmpdir(), 'afl-r4probe-'));
const run = (cmd, a) => { const r = spawnSync(cmd, a, {stdio: 'inherit'}); if (r.error) throw r.error; return r.status; };
try {
  const src = ['src/main/java', 'src/dev/java', 'tools/highway-r4'].map(p => path.join(ROOT, p)).join(sep);
  if (run(exe('javac'), ['-encoding', 'UTF-8', '-nowarn', '-cp', gson, '-d', out, '-sourcepath', src, path.join(ROOT, 'tools/highway-r4/SiteProbe.java')]) !== 0) process.exit(2);
  process.exitCode = run(exe('java'), ['-Xmx3g', '-cp', out + sep + gson, 'com.antaurora.apofirstlight.dev.highway.r4.SiteProbe', ...args]);
} finally {
  fs.rmSync(out, {recursive: true, force: true});
}
