// Pick strike sounds V1 (docs/gameplay/rock_strike_fx_v1.md section 2): the user's three Firefly takes (2026-10-24,
// E:/Download, 10 s stereo 48 kHz WAV, left/right correlation 0.986-0.995, checked by SHA-256 prefix) cut at their
// silences into mono Ogg Vorbis variants:
//   硬岩石 (granite face): five strikes at 0.02 / 1.87 / 3.70 / 6.14 / 8.61 s -> rock_strike/hard_1..5;
//   软岩石 (shale face): six strikes, the sixth's grit cut off by the end of the take, so the first five
//     (0.02 / 1.95 / 3.83 / 5.39 / 7.60 s) -> rock_strike/soft_1..5;
//   大块掉下来 (a chunk falling, no pick in it: user 2026-10-24): impacts at 0.0, 4.1, 6.25 and 7.85 s, each a heavy
//     landing, tumbling and gravel settling; the take asked for three, the second runs on into a fourth landing after a
//     short dip (6.15-6.20 s), so it is cut there: four variants -> rock_strike/break_1..4.
// Timing: a strike's sound starts at once (the pick lands on the tick); a break plays in the same tick on top of the
// strike, its first landing 0.25 s in (the chunk drops about half a metre first). Each variant is cut from shortly before
// its onset to just before the next event, with a 4 ms fade-in where it starts inside the last one's tail.
// Level (tools/sound-mix-lib.mjs): every variant scaled to its max 400 ms momentary loudness = the metal trash can's lid
// (-24.3 LUFS) plus a role offset (strikes +3 as the axe chops; the falling chunk +2, heard on top of a strike), sample
// peak at most -1 dBFS. High-passed first: the strikes at 40 Hz, the chunk at 30 Hz (keeps the weight of its landing).
// The strikes' first 1-3 ms (the steel's tick) peaked 20+ dB over their body, so the peak ceiling held them 2-6 dB under
// the target and 4 dB apart; a transient limiter (tame) holds every peak to CREST dB over the 30 ms RMS around it.
//   node tools/rock/build-pick-strike-sounds-v1.mjs [sourceDir]     (default E:/Download; needs ffmpeg on PATH)
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {SR, decode, trimHead, trimTail, peakAt, highPass, buildSounds} from '../sound-mix-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const SRC = process.argv[2] ?? 'E:/Download';
const SOUNDS = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight/sounds');
const REF = 'metal_trash_can/open.ogg';

const HARD = '硬岩石.wav', SOFT = '软岩石.wav', FALL = '大块掉下来.wav';
const sources = {[HARD]: '98e043da618f969e', [SOFT]: '4f7a35595beb327e', [FALL]: 'ba42b8e0f8145b0e'};
const HP = {[HARD]: 40, [SOFT]: 40, [FALL]: 30};
const CREST = 12;

/** Peaks held to CREST dB over the centred 30 ms RMS: gain from the peak within 1 ms (look-ahead), eased over 2 ms. */
function tame(x) {
  const R = Math.round(0.015 * SR), P = Math.round(0.001 * SR), E = Math.round(0.002 * SR), n = x.length;
  const sq = new Float64Array(n + 1);
  for (let i = 0; i < n; i++) sq[i + 1] = sq[i] + x[i] * x[i];
  const raw = new Float64Array(n);
  for (let i = 0; i < n; i++) {
    const a = Math.max(0, i - R), b = Math.min(n, i + R), rms = Math.sqrt((sq[b] - sq[a]) / (b - a));
    let pk = 0;
    for (let j = Math.max(0, i - P); j < Math.min(n, i + P); j++) pk = Math.max(pk, Math.abs(x[j]));
    raw[i] = pk > 0 ? Math.min(1, rms * Math.pow(10, CREST / 20) / pk) : 1;
  }
  const y = new Float64Array(n);
  for (let i = 0; i < n; i++) {
    let s = 0, c = 0;
    for (let j = Math.max(0, i - E); j < Math.min(n, i + E); j++) { s += raw[j]; c++; }
    y[i] = x[i] * Math.min(raw[i], s / c);
  }
  return y;
}
const prepare = {[HARD]: x => tame(highPass(x, HP[HARD])), [SOFT]: x => tame(highPass(x, HP[SOFT])), [FALL]: x => highPass(x, HP[FALL])};

/** A window [from, from + len) of src, faded over its last fade seconds, its first sound at onset s into the output. */
function cut(src, from, len, onset, fade = 0.15) {
  const c = {trim: from, fadeFrom: len - fade, fadeTo: len, ...(from > 0 ? {fadeIn: 0.004} : {})};
  let x = prepare[src](decode(path.join(SRC, src))).slice(Math.round(from * SR));
  x = x.slice(0, Math.round(len * SR));
  return {src, at: Math.ceil((onset + peakAt(trimTail(trimHead(x))) / SR) * 1e4) / 1e4, gain: 0, ...c};
}
const one = (file, offset, layer) => ({file, reference: REF, offset, layers: [layer]});

const outputs = {};
[[0, 1.7], [1.75, 1.75], [3.55, 2.35], [6.0, 2.4], [8.45, 1.55]].forEach(([from, len], i) => {
  outputs[`hard_${i + 1}`] = one(`rock_strike/hard_${i + 1}.ogg`, 3, cut(HARD, from, len, 0));
});
[[0, 1.8], [1.85, 1.8], [3.7, 1.5], [5.25, 2.15], [7.45, 1.75]].forEach(([from, len], i) => {
  outputs[`soft_${i + 1}`] = one(`rock_strike/soft_${i + 1}.ogg`, 3, cut(SOFT, from, len, 0));
});
[[0, 2.95, 0.3], [4.05, 2.16, 0.15], [6.22, 1.56, 0.15], [7.8, 2.2, 0.3]].forEach(([from, len, fade], i) => {
  outputs[`break_${i + 1}`] = one(`rock_strike/break_${i + 1}.ogg`, 2, cut(FALL, from, len, 0.25, fade));
});

console.log(JSON.stringify(buildSounds({srcDir: SRC, soundsDir: SOUNDS, sources, outputs, prepare}), null, 1));
