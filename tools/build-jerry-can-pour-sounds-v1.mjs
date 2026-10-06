// Jerry can pour sounds (docs/models/fuel_containers_v1.md "声音"), from two user-generated source recordings (2026-10-06,
// E:/Download, 3 s stereo 48 kHz each):
//   开盖倾倒  the cap worked loose and lifted, the can raised: faint lift noises at 0.20 / 0.43 / 0.83 s, the cap straining
//             at 1.16 / 1.21 s, cracking loose at 1.26 s, two turns at 1.85 / 2.09 s, lifted off at 2.43 s (its loudest).
//   倒油循环  fuel glugging into a pipe, steady (its level 10.7 LU above the cap's: the user found it far too loud).
// Outputs (client/JerryCanPourSounds plays them on the first-person clips' clock, tools/author-jerry-can-first-person.mjs):
//   jerry_can/open.ogg   the first source from 0.20 s: its events then land on pour_start's keys (strain 0.98, crack 1.06,
//                        second turn 1.9, cap off 2.22) without moving the animation.
//   jerry_can/close.ogg  pour_end from pieces of the same source (the user: opening and closing can share): the lift noise
//                        as the can tips back (0.36 s), the cap-off tick as the cap goes back on (1.15), the two turns
//                        (1.5, 1.8), the crack, softer, as it seats (2.08), a lift noise as the can is lowered (2.62).
//   jerry_can/pour.ogg   a 2.75 s loop of the second source (equal-power crossfade: glugs are noise, not a period), its
//                        slow swells only levelled (smooth over 3 s, so the glugs keep their shape).
// Level: open and close at the fuel nozzle's take (handling foley); the pour 7 LU under the opening's loudest moment.
//   node tools/build-jerry-can-pour-sounds-v1.mjs [sourceDir]     (default E:/Download; needs ffmpeg on PATH)
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {buildSounds, buildLoop} from './sound-mix-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const SRC = process.argv[2] ?? 'E:/Download';
const SOUNDS = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight/sounds');
const OPEN = '开盖倾倒', HEAD = 0.20, OPEN_PEAK = 2.436;   // the source's loudest sample: the cap lifted off

// a piece of the opening source: from `trim` s for `len` s (the last 0.05 s faded), its loudest sample landing at `at`
const piece = (trim, len, at, gain = 0) => ({src: OPEN, trim, fadeFrom: Math.max(0, len - 0.05), fadeTo: len, at, gain});

const report = buildSounds({srcDir: SRC, soundsDir: SOUNDS, sources: {[OPEN]: 'b8dc555e5e05ea8a'}, outputs: {
  open: {file: 'jerry_can/open.ogg', reference: 'fuel_nozzle/take.ogg', offset: 0, layers: [{src: OPEN, trim: HEAD, at: OPEN_PEAK - HEAD + 0.006, gain: 0}]},   // (+ the 5 ms lead-in trimHead keeps)
  close: {file: 'jerry_can/close.ogg', reference: 'fuel_nozzle/take.ogg', offset: 0, layers: [
    piece(0.10, 0.55, 0.36, -2),     // tipped back: the 0.43 s lift noise
    piece(2.30, 0.55, 1.15),         // the cap set back on: the cap-off tick
    piece(1.78, 0.20, 1.50),         // first turn
    piece(2.02, 0.17, 1.80),         // second turn
    piece(1.22, 0.30, 2.08, -4),     // seated: the crack, softer
    piece(0.70, 0.35, 2.62, -2)]},   // lowered: the 0.83 s lift noise
}});
report.pour = buildLoop({srcDir: SRC, soundsDir: SOUNDS, source: {name: '倒油循环', sha: 'a1c5b3ebf2cbc15d'}, file: 'jerry_can/pour.ogg',
  from: 0.0, period: 0.05, periods: 55, crossfade: 4, smooth: 60, equalPower: true, reference: 'jerry_can/open.ogg', offset: -7});
console.log(JSON.stringify(report, null, 1));
