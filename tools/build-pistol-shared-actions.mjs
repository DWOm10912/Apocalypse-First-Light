// Every pistol is held alike (user 2026-10-28: "所有手枪的idle，基本上都用的是一个姿势吧，这样最好"): the P9's third-person
// stance loops and holster actions (gun/p9_01_idle, _sprint, _draw, _holster; docs/dev/p9_stance_loops_v1.md,
// docs/dev/p9_draw_holster_actions_v1.md) copied for each other pistol with the gun prop swapped for its own
// (player_props/<gun>.json, ./gradlew gunPropExport --args=<gun>). The props key the gun by its grip point (grip_r), which
// every pistol has on the P9 hand point, and the holster by its own thigh carry, so each gun lands where the P9 does.
// Reloads, inspects and unloads stay per gun (their magazines and beats differ).
//   node tools/build-pistol-shared-actions.mjs [--check]     (rerun after the P9's actions change)
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const dir = path.join(root, 'src/main/resources/assets/apocalypse_firstlight/player_actions/gun');
const props = path.join(root, 'src/main/resources/assets/apocalypse_firstlight/player_props');
const SOURCE = 'p9_01', PISTOLS = ['blackridge_50'], ACTIONS = ['idle', 'sprint', 'draw', 'holster'];
const check = process.argv.includes('--check');
let stale = 0;
for (const gun of PISTOLS) {
  if (!fs.existsSync(path.join(props, gun + '.json'))) throw new Error(`player_props/${gun}.json missing (./gradlew gunPropExport --args=${gun})`);
  for (const a of ACTIONS) {
    const src = JSON.parse(fs.readFileSync(path.join(dir, `${SOURCE}_${a}.json`), 'utf8'));
    if (src.prop?.id !== SOURCE) throw new Error(`${SOURCE}_${a}: prop ${src.prop?.id}`);
    const out = {...src, about: `${src.about} -- Copied for the ${gun} by tools/build-pistol-shared-actions.mjs (every pistol held alike), its own gun prop; do not edit, edit gun/${SOURCE}_${a}.`,
      prop: {...src.prop, id: gun}};
    const text = JSON.stringify(out, null, 2) + '\n', file = path.join(dir, `${gun}_${a}.json`);
    if (check) { if (!fs.existsSync(file) || fs.readFileSync(file, 'utf8').replace(/\r\n/g, '\n') !== text) { console.log('STALE ' + path.relative(root, file)); stale++; } }
    else fs.writeFileSync(file, text);
  }
}
if (stale) process.exit(1);
console.log(check ? 'up to date' : `wrote ${PISTOLS.length * ACTIONS.length} actions`);
