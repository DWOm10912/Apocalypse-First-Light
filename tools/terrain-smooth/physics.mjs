// Terrain smoothing prototype P1: offline walking check (docs/worldgen/terrain_smooth_p1_interaction.md).
// Compiles SmoothRules + SmoothShapes (the shapes the game builds) with tools/terrain-smooth/{SmoothCheck,PhysicsCheck}.java
// against the recompiled Forge 1.20.1 jar (real VoxelShape / Shapes.collide code) and runs PhysicsCheck.
//   node tools/terrain-smooth/physics.mjs                      synthetic hills and terrain (pass / fail)
//   node tools/terrain-smooth/physics.mjs world <file> <minX> <minZ> <size>   blocks exported from a save by export-area.mjs: walks and
//                                                               standing per ground block, vanilla blocks vs mode B (report only)
// Needs a JDK 17 (JAVA_HOME, or javac on PATH) and the Gradle caches of this workspace (run a Gradle build once).
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const GRADLE = path.join(process.env.GRADLE_USER_HOME || path.join(os.homedir(), '.gradle'), 'caches');
const FORGE = path.join(GRADLE, 'forge_gradle/minecraft_user_repo/net/minecraftforge/forge/1.20.1-47.4.22_mapped_official_1.20.1/forge-1.20.1-47.4.22_mapped_official_1.20.1-recomp.jar');
const LIBS = ['it.unimi.dsi/fastutil', 'com.google.guava/guava', 'com.google.guava/failureaccess', 'com.mojang/datafixerupper',
  'com.mojang/logging', 'org.slf4j/slf4j-api', 'org.apache.commons/commons-lang3', 'org.joml/joml', 'com.google.code.gson/gson', 'com.mojang/brigadier', 'com.mojang/authlib'];

function newestJar(group) {
  const dir = path.join(GRADLE, 'modules-2/files-2.1', group);
  const found = [];
  const walk = d => { for (const e of fs.readdirSync(d, {withFileTypes: true})) {
    const p = path.join(d, e.name);
    if (e.isDirectory()) walk(p); else if (e.name.endsWith('.jar') && !e.name.includes('sources')) found.push(p);
  } };
  if (fs.existsSync(dir)) walk(dir);
  if (!found.length) throw new Error('missing library ' + group + ' in ' + dir);
  return found.sort((a, b) => path.basename(a).localeCompare(path.basename(b), 'en', {numeric: true})).pop();
}

if (!fs.existsSync(FORGE)) throw new Error('missing ' + FORGE);
const classpath = [FORGE, ...LIBS.map(newestJar)];
const src = 'src/dev/java/com/antaurora/apofirstlight/dev/terrainsmooth/';
const sources = [src + 'SmoothRules.java', src + 'SmoothShapes.java', 'tools/terrain-smooth/SmoothCheck.java', 'tools/terrain-smooth/PhysicsCheck.java'].map(p => path.join(ROOT, p));
const exe = n => (process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin', n) : n) + (process.platform === 'win32' && process.env.JAVA_HOME ? '.exe' : '');
const out = fs.mkdtempSync(path.join(os.tmpdir(), 'afl-smoothphysics-'));
const run = (cmd, a) => { const r = spawnSync(cmd, a, {stdio: 'inherit'}); if (r.error) throw r.error; return r.status; };
try {
  const cp = classpath.join(path.delimiter);
  if (run(exe('javac'), ['-encoding', 'UTF-8', '-nowarn', '-cp', cp, '-d', out, ...sources]) !== 0) process.exit(2);
  process.exitCode = run(exe('java'), ['-cp', [out, cp].join(path.delimiter), 'com.antaurora.apofirstlight.dev.terrainsmooth.PhysicsCheck', ...process.argv.slice(2)]);
} finally {
  fs.rmSync(out, {recursive: true, force: true});
}
