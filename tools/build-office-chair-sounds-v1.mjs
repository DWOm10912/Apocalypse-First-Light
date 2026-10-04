// Office Chair rolling sound: one seamless loop, played by the client at a chair entity while it moves, louder and a little
// higher the faster it rolls (client/OfficeChairRollSound). Mono 48 kHz Ogg Vorbis.
//   node tools/build-office-chair-sounds-v1.mjs [sourceDir]     (default E:/Download; needs ffmpeg on PATH)
// Source (user 2026-10-03, generated, 3 s stereo 48 kHz WAV, left/right correlation 0.965, checked by SHA-256 prefix):
// office_chair_roll, plastic casters on a hard floor. It swells in over its first 0.7 s and dies away after 2.45 s; the
// steady roll between holds faint wheel ticks (up to 15 dB crest in 10 ms blocks). The loop takes 0.80-2.40 s: 28 periods
// of 50 ms levelled (smoothed over 5 periods either side), the ticks in the highs held to 8 dB over their surroundings
// (they repeat every loop and would read as a hitch), the last 4 periods cross-faded into the start with equal-power
// weights (uncorrelated noise: a linear crossfade dips ~3 dB). Loudness and loops: tools/sound-mix-lib.mjs buildLoop.
// The user found the source loud: it sits 16 LU under the industrial locker's door opening, 6 LU under the container
// search loop (which also plays right next to the player).
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {buildLoop} from './sound-mix-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const SRC = process.argv[2] ?? 'E:/Download';
const SOUNDS = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight/sounds');

console.log(JSON.stringify(buildLoop({srcDir: SRC, soundsDir: SOUNDS, source: {name: 'office_chair_roll', sha: '0b0d1cc3155240f3'},
  file: 'office_chair/roll_loop.ogg', from: 0.80, period: 0.05, periods: 28, crossfade: 4, smooth: 5,
  reference: 'industrial_locker/open.ogg', offset: -16, declick: 8, equalPower: true}), null, 1));
