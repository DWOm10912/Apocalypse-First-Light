// Portable diesel generator V1 sounds (docs/machines/portable_diesel_generator_v1.md "声音"): pull, start, run loop and stop,
// all cut from ONE user-generated take (Adobe Firefly, 2026-10-09; 30 s stereo 48 kHz WAV, its channels correlated 0.95,
// decoded to mono). The take, measured (50 ms and 400 ms loudness, spectrograms):
//   0.00-0.44 s  silence
//   0.44-0.90 s  one recoil pull: the rope's whirr rising, the engine turned over two compressions (low thumps)
//   0.92, 1.10 s the first two firings: it caught
//   1.2-28.6 s   steady single-cylinder running, about 29 firings a second (400 ms loudness sd 0.75 LU, 2 s frames within
//                0.7 LU)
//   28.7 s       the stop: gone in about 0.2 s, a faint low hum after
// Cuts:
//   pull.ogg      PULL: the pull alone, faded out just before the first firing; the pull that catches (the server decides at
//                 the press which it is), the start follows it
//   pull_fail_1/2.ogg  from a second take (Firefly, 2026-10-09, "Three separate pulls ..."; 5 s, two pulls came out): each
//                 a pull and the engine turned over for about 1.3 s without firing, then still; the pulls that do not catch,
//                 one or the other at random. Quieter in the take than the first take's pull and a little darker (centroid
//                 2.1-2.3 kHz against 2.6), so each is brought to the catching pull's momentary level
//   start.ogg     START: from the first firing, the catch and two seconds of running, its last START.fade seconds fading
//                 out; the game decides the catch at the pull's tick 9 (PortableDieselGeneratorBlockEntity.CATCH_AT, 0.45 s:
//                 the take's first firing comes 0.48 s after the pull begins), and the client's loop waits and fades in
//                 over the start's fade (LOOP_FADE_IN)
//   run_loop.ogg  LOOP: about 10 s of the steady run; its length is the one within +-0.1 s of 10 s whose seam keeps the
//                 firing beat in step: the correlation of the firing envelope (above 300 Hz, rectified, smoothed under 120
//                 Hz) over 0.5 s across the seam (the raw waveform is noise between firings and correlated only 0.15; a
//                 single cylinder's beat would flam if the two sides met out of step), joined by a 0.1 s equal-power
//                 crossfade
//   stop.ogg      STOP: 0.5 s of running, the stop, 0.3 s of the hum, faded
// Levels (BS.1770 momentary 400 ms, against the distribution panel's door as the other block sounds): the loop 2 LU under it
// (an open frame, louder than the standby set's -3); pull, start and stop take the loop's gain, each only limited to peaks
// at or below -1 dBFS.
//   node tools/build-portable-generator-sounds-v1.mjs [sourceDir]     (default E:/Download; needs ffmpeg on PATH)
import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {SR, PEAK_CEILING, decode, maxLoudness, kweight, peak, db, writeOgg} from './sound-mix-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const SRC = process.argv[2] ?? 'E:/Download';
const SOUNDS = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight/sounds');
const TAKE = {name: 'Firefly_audio_A_small_portable_open-frame_diesel_generator_with__variation4', sha: 'e3572af312a9ccc4'};
const REFERENCE = 'distribution_panel/open.ogg', LOOP_OFFSET = -2;
const PULL = {from: 0.43, to: 0.905, fade: 0.04};
const START = {from: 0.885, to: 3.0, fadeIn: 0.012, fade: 1.0};
const LOOP = {from: 8.0, length: 10.0, search: 0.1, window: 0.5, crossfade: 0.1};
const STOP = {from: 28.2, to: 29.2, fadeIn: 0.015, fade: 0.25};
const FAILS = {name: 'Firefly_audio_Three_separate_pulls_on_the_recoil_starter_rope_of_variation2', sha: '781581c4f929745f',
  cuts: [{from: 0.0, to: 1.78, fade: 0.18}, {from: 2.72, to: 4.42, fade: 0.18}]};

const file = path.join(SRC, TAKE.name + '.wav');
const h = createHash('sha256').update(fs.readFileSync(file)).digest('hex').slice(0, 16);
if (h !== TAKE.sha) throw new Error(`${TAKE.name}.wav changed (sha ${h}, expected ${TAKE.sha})`);
const x = decode(file);
const cut = (a, b) => x.slice(Math.round(a * SR), Math.round(b * SR));
const fadeOut = (y, s) => { const F = Math.round(s * SR); for (let i = 0; i < F && i < y.length; i++) y[y.length - 1 - i] *= 0.5 - 0.5 * Math.cos(Math.PI * i / F); return y; };
const fadeIn = (y, s) => { const F = Math.round(s * SR); for (let i = 0; i < F && i < y.length; i++) y[i] *= i / F; return y; };
const meanLufs = y => { const k = kweight(y); let e = 0; for (const v of k) e += v * v; return -0.691 + 10 * Math.log10(e / k.length + 1e-20); };

// ---- the loop: the best seam near LOOP.length, by the firing envelope ----
function biquad(y, type, f, q = 0.707) { const w = 2 * Math.PI * f / SR, c = Math.cos(w), a = Math.sin(w) / (2 * q), a0 = 1 + a;
  const b = type === 'lp' ? [(1 - c) / 2, 1 - c, (1 - c) / 2] : [(1 + c) / 2, -(1 + c), (1 + c) / 2], o = new Float64Array(y.length); let x1 = 0, x2 = 0, y1 = 0, y2 = 0;
  for (let i = 0; i < y.length; i++) { const v = (b[0] * y[i] + b[1] * x1 + b[2] * x2 + 2 * c * y1 - (1 - a) * y2) / a0; x2 = x1; x1 = y[i]; y2 = y1; y1 = v; o[i] = v; } return o; }
const env = biquad(biquad(Float64Array.from(biquad(x, 'hp', 300), Math.abs), 'lp', 120), 'lp', 120);
const s0 = Math.round(LOOP.from * SR), W = Math.round(LOOP.window * SR), C = Math.round(LOOP.crossfade * SR);
const corr = L => { let ma = 0, mb = 0; for (let i = 0; i < W; i++) { ma += env[s0 + i]; mb += env[s0 + L + i]; } ma /= W; mb /= W;
  let sab = 0, saa = 0, sbb = 0; for (let i = 0; i < W; i++) { const a = env[s0 + i] - ma, b = env[s0 + L + i] - mb; sab += a * b; saa += a * a; sbb += b * b; }
  return sab / Math.sqrt(saa * sbb + 1e-20); };
let best = -1, bestL = Math.round(LOOP.length * SR);
for (let L = Math.round((LOOP.length - LOOP.search) * SR); L <= Math.round((LOOP.length + LOOP.search) * SR); L += 8) { const r = corr(L); if (r > best) { best = r; bestL = L; } }
for (let L = bestL - 7; L <= bestL + 7; L++) { const r = corr(L); if (r > best) { best = r; bestL = L; } }
const loopRaw = x.slice(s0, s0 + bestL + C), loop = loopRaw.slice(0, bestL);
for (let i = 0; i < C; i++) { const w = i / C; loop[i] = loopRaw[i] * Math.sin(w * Math.PI / 2) + loopRaw[bestL + i] * Math.cos(w * Math.PI / 2); }
// the shared gain: the loop to its target (measured across the seam)
const ref = maxLoudness(decode(path.join(SOUNDS, REFERENCE)), 0.4);
const twice = new Float64Array(2 * loop.length); twice.set(loop); twice.set(loop, loop.length);
const gainDb = Math.min(ref + LOOP_OFFSET - maxLoudness(twice, 0.4), 20 * Math.log10(db(PEAK_CEILING) / peak(loop)));
const report = {referenceLUFS: +ref.toFixed(1), loop: {seconds: +(bestL / SR).toFixed(4), envelopeCorrelation: +best.toFixed(3), atExactly10s: +corr(Math.round(10 * SR)).toFixed(3)}, gainDb: +gainDb.toFixed(2)};
function write(name, y) {
  const g = Math.min(db(gainDb), db(PEAK_CEILING) / peak(y)), out = y.map(v => v * g);
  writeOgg(out, path.join(SOUNDS, 'portable_generator', name + '.ogg'));
  report[name] = {...(report[name] || {}), seconds: +(out.length / SR).toFixed(3), lufs400: +maxLoudness(out, 0.4).toFixed(1), meanLufs: +meanLufs(out).toFixed(1),
    peakDb: +(20 * Math.log10(peak(out))).toFixed(1), peakLimited: g < db(gainDb) - 1e-9};
}
write('pull', fadeOut(fadeIn(cut(PULL.from, PULL.to), 0.004), PULL.fade));
write('start', fadeOut(fadeIn(cut(START.from, START.to), START.fadeIn), START.fade));
write('run_loop', loop);
write('stop', fadeOut(fadeIn(cut(STOP.from, STOP.to), STOP.fadeIn), STOP.fade));
// the failing pulls: the second take, each at the catching pull's momentary level
{
  const f2 = path.join(SRC, FAILS.name + '.wav'), h2 = createHash('sha256').update(fs.readFileSync(f2)).digest('hex').slice(0, 16);
  if (h2 !== FAILS.sha) throw new Error(`${FAILS.name}.wav changed (sha ${h2}, expected ${FAILS.sha})`);
  const y2 = decode(f2), target = report.pull.lufs400;
  FAILS.cuts.forEach((c, i) => {
    const y = fadeOut(fadeIn(y2.slice(Math.round(c.from * SR), Math.round(c.to * SR)), 0.004), c.fade);
    const g = Math.min(db(target - maxLoudness(y, 0.4)), db(PEAK_CEILING) / peak(y)), out = y.map(v => v * g);
    writeOgg(out, path.join(SOUNDS, 'portable_generator', `pull_fail_${i + 1}.ogg`));
    report[`pull_fail_${i + 1}`] = {seconds: +(out.length / SR).toFixed(3), lufs400: +maxLoudness(out, 0.4).toFixed(1), peakDb: +(20 * Math.log10(peak(out))).toFixed(1)};
  });
}
const L2 = loop.map(v => v * db(gainDb));
report.loop.seamJump = +Math.abs(L2[0] - L2[L2.length - 1]).toFixed(4);
console.log(JSON.stringify(report, null, 1));
