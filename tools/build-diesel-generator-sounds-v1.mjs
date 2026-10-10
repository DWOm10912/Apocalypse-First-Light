// Diesel standby generator V1 sounds (docs/machines/diesel_standby_generator_v1.md "声音"): crank, start, run loop and
// stop, all cut from ONE user-generated take (Adobe Firefly, 2026-10-09; 30 s stereo 48 kHz WAV, its two channels
// correlated 0.99, decoded to mono). One take so the four are the same engine (user: separately generated pieces "每一段
// 的风格都不一样"); the user's second take was the steadier one to cut (the other drifted 2.5 LU over its run):
//   0.05 s  the starter engages; 0.05-0.40 s it cranks (5-8 Hz compressions); 0.40 s the engine fires, flares (to about
//   6 LU over the run) and settles by 1.0 s; 1.5-25.5 s steady (400 ms loudness within 0.6 LU sd, 2 s frames within
//   1.1 LU); 25.75 s the fuel is cut: it chugs down (firing rate 22 -> 8 Hz) and is quiet by 27.1 s.
//   crank.ogg     0 .. CRANK_END: the starter alone, for a start with an empty tank (it cranks and never fires)
//   start.ogg     0 .. START.end, the last START.fade seconds fading out; the game turns to running at the fire
//                 (DieselGeneratorBlockEntity.CRANK_TICKS = 7: the fire lands at 0.35 s once the head silence is trimmed) and the client's loop waits for the flare to settle and
//                 fades in over the start's fade (LOOP_FADE_IN)
//   run_loop.ogg  10 s from LOOP.from, levelled over +-1 s, 0.5 s equal-power crossfade (the engine is noise-like
//                 between its firings: the two sides are uncorrelated)
//   stop.ogg      STOP.from .. STOP.end (the run-down is under -55 LUFS by 27.1 s; after it only a -60 LUFS floor), a 15 ms
//                 fade-in and a 0.25 s fade-out: 0.3 s of running, then the fuel cut
// Levels (BS.1770, against the distribution panel's door as the other block sounds): the loop's momentary maximum 3 LU
// below it (the loudest thing on the lot); crank, start and stop take the loop's gain (matched on the mean level of the
// loop window), so the flare and the run-down keep their size against the run. Peaks at or below -1 dBFS.
//   node tools/build-diesel-generator-sounds-v1.mjs [sourceDir]     (default E:/Download; needs ffmpeg on PATH)
import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {SR, PEAK_CEILING, decode, maxLoudness, kweight, peak, db, trimHead, writeOgg, buildLoop} from './sound-mix-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const SRC = process.argv[2] ?? 'E:/Download';
const SOUNDS = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight/sounds');
const TAKE = {name: 'Firefly_audio_A_large_diesel_standby_generator_inside_a_steel_so_variation4 (1)', sha: '76a82de45cde9e7e'};
const REFERENCE = 'distribution_panel/open.ogg', LOOP_OFFSET = -3;
const CRANK_END = 0.36, CRANK_FADE = 0.04;
const START = {end: 2.1, fade: 1.0};
const LOOP = {from: 13.0, period: 0.05, periods: 200, crossfade: 10, smooth: 20};
const STOP = {from: 25.45, end: 27.35, fadeIn: 0.015, fadeOut: 0.25};

const file = path.join(SRC, TAKE.name + '.wav');
const h = createHash('sha256').update(fs.readFileSync(file)).digest('hex').slice(0, 16);
if (h !== TAKE.sha) throw new Error(`${TAKE.name}.wav changed (sha ${h}, expected ${TAKE.sha})`);
const x = decode(file);
const meanLufs = y => { const k = kweight(y); let e = 0; for (const v of k) e += v * v; return -0.691 + 10 * Math.log10(e / k.length + 1e-20); };
const cut = (a, b) => x.slice(Math.round(a * SR), Math.round(b * SR));
const fadeOut = (y, seconds) => { const F = Math.round(seconds * SR); for (let i = 0; i < F && i < y.length; i++) y[y.length - 1 - i] *= 0.5 - 0.5 * Math.cos(Math.PI * i / F); return y; };
const fadeIn = (y, seconds) => { const F = Math.round(seconds * SR); for (let i = 0; i < F && i < y.length; i++) y[i] *= i / F; return y; };

const report = {};
report.loop = buildLoop({srcDir: SRC, soundsDir: SOUNDS, source: TAKE, file: 'diesel_generator/run_loop.ogg', from: LOOP.from, period: LOOP.period,
  periods: LOOP.periods, crossfade: LOOP.crossfade, smooth: LOOP.smooth, reference: REFERENCE, offset: LOOP_OFFSET, equalPower: true});
// the loop's gain, as the mean level of what came out over the mean level of the window it was cut from
const window = cut(LOOP.from, LOOP.from + LOOP.periods * LOOP.period);
const gainDb = meanLufs(decode(path.join(SOUNDS, 'diesel_generator/run_loop.ogg'))) - meanLufs(window);
report.sharedGainDb = +gainDb.toFixed(2);
function write(name, y) {
  const g = Math.min(db(gainDb), db(PEAK_CEILING) / peak(y)), out = y.map(v => v * g);
  writeOgg(out, path.join(SOUNDS, 'diesel_generator', name + '.ogg'));
  report[name] = {seconds: +(out.length / SR).toFixed(3), lufs400: +maxLoudness(out, 0.4).toFixed(1), peakDb: +(20 * Math.log10(peak(out))).toFixed(1),
    peakLimited: g < db(gainDb)};
}
write('crank', fadeOut(trimHead(cut(0, CRANK_END)), CRANK_FADE));
write('start', fadeOut(trimHead(cut(0, START.end)), START.fade));
write('stop', fadeOut(fadeIn(cut(STOP.from, STOP.end), STOP.fadeIn), STOP.fadeOut));
report.referenceLUFS = +maxLoudness(decode(path.join(SOUNDS, REFERENCE)), 0.4).toFixed(1);
console.log(JSON.stringify(report, null, 1));
