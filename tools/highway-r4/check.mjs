// Highway R4 sample V1: offline kernel checks (docs/worldgen/highway_r4_sample_v1.md, M). Compiles the pure R4 kernel
// (R4Section, R4Profile, R4Design, R4Earthworks, R4Mesh, R4Recipe, R4Journal) with the shared M1 geometry (RoadAlignment,
// ChunkMeshGeometry) and tools/highway-r4/R4Check.java into a temporary folder and runs it; no Gradle, no Minecraft.
//   node tools/highway-r4/check.mjs      -> prints the results, writes tools/highway-r4/check-results.json
// Needs a JDK 17 (JAVA_HOME, or javac on PATH) and the Gson jar already in the Gradle cache (no network).
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const R4 = 'src/dev/java/com/antaurora/apofirstlight/dev/highway/r4/';
const M1 = 'src/dev/java/com/antaurora/apofirstlight/dev/highwaymesh/';
const sources = [...['R4Section', 'R4Profile', 'R4Design', 'R4Earthworks', 'R4Mesh', 'R4Recipe', 'R4Journal'].map(n => R4 + n + '.java'),
  ...['RoadAlignment', 'ChunkMeshGeometry'].map(n => M1 + n + '.java'), 'tools/highway-r4/R4Check.java'].map(p => path.join(ROOT, p));

const exe = n => (process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin', n) : n) + (process.platform === 'win32' && process.env.JAVA_HOME ? '.exe' : '');

function findGson() {
  const base = path.join(os.homedir(), '.gradle/caches/modules-2/files-2.1/com.google.code.gson/gson');
  if (!fs.existsSync(base)) throw new Error('Gson not in the Gradle cache: ' + base);
  for (const v of fs.readdirSync(base).sort().reverse()) for (const h of fs.readdirSync(path.join(base, v))) {
    const jar = path.join(base, v, h, `gson-${v}.jar`);
    if (fs.existsSync(jar)) return jar;
  }
  throw new Error('Gson jar not found under ' + base);
}

const gson = findGson(), out = fs.mkdtempSync(path.join(os.tmpdir(), 'afl-r4check-'));
const sep = process.platform === 'win32' ? ';' : ':';
const run = (cmd, args) => { const r = spawnSync(cmd, args, {stdio: 'inherit'}); if (r.error) throw r.error; return r.status; };
try {
  if (run(exe('javac'), ['-encoding', 'UTF-8', '-nowarn', '-cp', gson, '-d', out, ...sources]) !== 0) process.exit(2);
  const status = run(exe('java'), ['-cp', out + sep + gson, 'com.antaurora.apofirstlight.dev.highway.r4.R4Check', path.join(ROOT, 'tools/highway-r4/check-results.json')]);
  process.exitCode = status;
} finally {
  fs.rmSync(out, {recursive: true, force: true});
}
