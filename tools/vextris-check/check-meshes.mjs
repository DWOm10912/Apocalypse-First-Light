// Loads every AFL mesh through the real Vextris loader (the jar AFL builds against), without Gradle or a Minecraft start:
// java's single-file source launcher runs MeshCheck.java with Gson and the Vextris jar on the class path.
//   node tools/vextris-check/check-meshes.mjs                    every mesh (VEXTRIS_JAR / GRADLE_USER_HOME / JAVA_HOME override the defaults)
//   node tools/vextris-check/check-meshes.mjs --stats <id> ...   corner-normal lean per part (SmoothStats.java)
// Exit code 1 when any mesh is rejected.
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url)), root = path.resolve(here, '../..');
const props = Object.fromEntries(fs.readFileSync(path.join(root, 'gradle.properties'), 'utf8').split(/\r?\n/)
  .filter(l => /^[a-z_]+=/.test(l)).map(l => l.split('=')));
const version = props.vextris_version;
const vextris = process.env.VEXTRIS_JAR || path.resolve(root, props.vextris_repo, `io/github/dwom10912/vextris/${version}/vextris-${version}.jar`);
if (!fs.existsSync(vextris)) { console.error('Vextris jar not found: ' + vextris); process.exit(2); }
const gradle = process.env.GRADLE_USER_HOME || path.join(os.homedir(), '.gradle');
const jars = dir => fs.existsSync(dir) ? fs.readdirSync(dir, {withFileTypes: true}).flatMap(e => e.isDirectory() ? jars(path.join(dir, e.name))
  : e.name.endsWith('.jar') && !e.name.includes('sources') ? [path.join(dir, e.name)] : []) : [];
const pick = group => jars(path.join(gradle, 'caches/modules-2/files-2.1', group)).sort().at(-1);
const cp = [vextris, pick('com.google.code.gson/gson'), pick('org.joml/joml')].filter(Boolean);
const java = process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'java.exe' : 'java') : 'java';
const stats = process.argv[2] === '--stats';
const run = spawnSync(java, ['--class-path', cp.join(path.delimiter), path.join(here, stats ? 'SmoothStats.java' : 'MeshCheck.java'),
  path.join(root, 'src/main/resources/assets/apocalypse_firstlight'), ...(stats ? process.argv.slice(3) : [])],
  {encoding: 'utf8', timeout: 300000, windowsHide: true, maxBuffer: 64 << 20});
process.stdout.write('vextris ' + path.basename(vextris) + '\n' + (run.stdout ?? '') + (run.stderr ?? ''));
process.exit(run.status ?? 2);
