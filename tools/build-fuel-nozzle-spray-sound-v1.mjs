// Fuel nozzle spray loop (docs/models/fuel_dispenser_v1.md "滋油"): the running stream, from one user-generated source
// recording (2026-10-05, E:/Download/喷油循环.wav: 5 s stereo 48 kHz, steady within 3 dB, but its energy mostly above 6 kHz,
// a hiss). Low-passed at 6.5 kHz (2nd order) before the loop is cut: a 4 s equal-power crossfaded loop (noise).
// No trigger sounds (user, 2026-10-05: the generated clicks did not work); the client fades the loop in and out
// (client/FuelNozzleSprayLoop). Level: 9 LU below the industrial electrical box door, peaks at or below -1 dBFS.
//   node tools/build-fuel-nozzle-spray-sound-v1.mjs [sourceDir]     (default E:/Download; needs ffmpeg on PATH)
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {SR, buildLoop} from './sound-mix-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const SRC = process.argv[2] ?? 'E:/Download';
const SOUNDS = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight/sounds');
const LOWPASS = 6500;

function biquad(x, b, a) { const y = new Float64Array(x.length); let x1 = 0, x2 = 0, y1 = 0, y2 = 0;
  for (let i = 0; i < x.length; i++) { const v = b[0] * x[i] + b[1] * x1 + b[2] * x2 - a[1] * y1 - a[2] * y2; x2 = x1; x1 = x[i]; y2 = y1; y1 = v; y[i] = v; } return y; }
function lowPass2(x, fc) { const w = 2 * Math.PI * fc / SR, c = Math.cos(w), al = Math.sin(w) / (2 * Math.SQRT1_2), a0 = 1 + al;
  return biquad(x, [(1 - c) / 2 / a0, (1 - c) / a0, (1 - c) / 2 / a0], [1, -2 * c / a0, (1 - al) / a0]); }

const report = buildLoop({srcDir: SRC, soundsDir: SOUNDS, source: {name: '喷油循环', sha: 'a79ee64c28aefd21'}, file: 'fuel_nozzle/spray.ogg',
  from: 0.3, period: 0.05, periods: 80, crossfade: 8, equalPower: true, reference: 'distribution_panel/open.ogg', offset: -9, declick: 6,
  prepare: x => lowPass2(x, LOWPASS)});
console.log(JSON.stringify(report, null, 1));
