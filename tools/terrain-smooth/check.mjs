// Terrain smoothing prototype P1: offline checks of the shared rules (docs/worldgen/terrain_smooth_visual_prototype_p0.md).
// Compiles src/dev/.../terrainsmooth/SmoothRules.java (pure Java, no Minecraft) with tools/terrain-smooth/SmoothCheck.java
// into a temporary folder and runs it.
//   node tools/terrain-smooth/check.mjs
// Needs a JDK 17 (JAVA_HOME, or javac on PATH).
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const sources = ['src/dev/java/com/antaurora/apofirstlight/dev/terrainsmooth/SmoothRules.java', 'tools/terrain-smooth/SmoothCheck.java'].map(p => path.join(ROOT, p));
const exe = n => (process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin', n) : n) + (process.platform === 'win32' && process.env.JAVA_HOME ? '.exe' : '');
const out = fs.mkdtempSync(path.join(os.tmpdir(), 'afl-smoothcheck-'));
const run = (cmd, a) => { const r = spawnSync(cmd, a, {stdio: 'inherit'}); if (r.error) throw r.error; return r.status; };
try {
  if (run(exe('javac'), ['-encoding', 'UTF-8', '-nowarn', '-d', out, ...sources]) !== 0) process.exit(2);
  process.exitCode = run(exe('java'), ['-cp', out, 'com.antaurora.apofirstlight.dev.terrainsmooth.SmoothCheck']);
} finally {
  fs.rmSync(out, {recursive: true, force: true});
}
