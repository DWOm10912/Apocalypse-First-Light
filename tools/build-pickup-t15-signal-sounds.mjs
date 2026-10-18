// T15 turn signal sounds (docs/vehicles/first_pickup_v0_design.md 8.11 / 8.12, client/vehicle/TruckSounds): one user
// recording (2026-10-17, "Turn Signal Sound Effect.ogg" in E:/Download, 7 s, 48 kHz, both channels identical) cut into the
// stalk switched on, the flasher relay's two clicks and the stalk switched off, under sounds/pickup_t15/.
// The take (measured 2026-10-17, 10 ms RMS): 0.36 s the stalk on with the first relay click on top; the relay then every
// 0.35 s, a louder click as the lamps light (1.10, 1.80, 2.50 ... 5.31 s, about -25 dBFS) and a softer one as they go out
// (0.75, 1.45, 2.15 ... 5.66 s, about -33): one flash every 0.70 s (86 a minute); 5.80 s the stalk back to the middle
// (-13), ringing out to 6.3 s.
// The user: the take has some hiss and hum under it. Its noise floor is about -53 dBFS, nearly all below 1 kHz (a low
// hum about -60 per band; above 1 kHz under -68), while the clicks sit at 1-10 kHz. So, before the cuts:
//   - a 4th-order high-pass at HIGH_PASS Hz (the hum; the clicks keep their body above it);
//   - spectral subtraction (STFT, FFT N with a hop of N / 4, Hann in and out): the noise power per bin is its median
//     over the whole take (the clicks fill under a quarter of the frames), each bin's gain sqrt(1 - OVER * noise /
//     power), at least FLOOR, smoothed over 3 bins and held over time (a gain falls at most to RELEASE of the frame
//     before), so the clicks' tails ring out instead of being chopped.
// Levels: the stalk on's maximum momentary loudness (BS.1770, 400 ms) to the reference plus ROLE (small controls: the key
// -8, the gear lever -6), and every other cut the same gain, so the clicks keep the take's balance (the stalk off TRIM
// under it); sample peaks at or below -1 dBFS.
//   node tools/build-pickup-t15-signal-sounds.mjs [source file]     (needs ffmpeg on PATH)
import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {SR, PEAK_CEILING, decode, maxLoudness, peak, db, writeOgg, highPass} from './sound-mix-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const SRC = process.argv[2] ?? 'E:/Download/Turn Signal Sound Effect.ogg';
const SHA = '93eccb2dbc561f1f';
const SOUNDS = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight/sounds');
const OUT = 'pickup_t15';
const REFERENCE = 'distribution_panel/open.ogg';
const ROLE = -7;
/** The stalk snapping back is 8 LU over the stalk on in the take, as loud as a door: this much under the take's balance. */
const TRIM = {signal_off: -4};
const HIGH_PASS = 180, N = 2048, OVER = 2.0, FLOOR = db(-32), RELEASE = 0.55;
// cuts: [near the onset (s), to (s), fade-out (s)]; each starts 5 ms before the first 1 ms block 20 dB over the floor and
// ends once its click has rung down to the cleaned floor (the relay clicks are under -55 dBFS 50-60 ms on), so no stretch
// of bare floor is left in a sound
const CUTS = {
  signal_on: [0.33, 0.68, 0.10],
  signal_tick_1: [2.48, 2.66, 0.09],
  signal_tick_2: [3.89, 4.07, 0.09],
  signal_tock_1: [2.13, 2.29, 0.08],
  signal_tock_2: [4.24, 4.40, 0.08],
  signal_off: [5.77, 6.24, 0.14],
};

const sha = createHash('sha256').update(fs.readFileSync(SRC)).digest('hex').slice(0, 16);
if (sha !== SHA) throw new Error(`${SRC}: sha ${sha}, expected ${SHA}`);
const x = highPass(decode(SRC), HIGH_PASS);
const y = denoise(x);
const report = {floorBefore: floor(x), floorAfter: floor(y)};
// AFL_SIGNAL_DUMP=<file>: the whole cleaned take as raw mono f32 (48 kHz), to listen to or measure
if (process.env.AFL_SIGNAL_DUMP) fs.writeFileSync(process.env.AFL_SIGNAL_DUMP, Buffer.from(new Float32Array(y).buffer));

const refLufs = maxLoudness(decode(path.join(SOUNDS, REFERENCE)), 0.4);
const cuts = {};
for (const [name, [near, to, fade]] of Object.entries(CUTS)) cuts[name] = fadeOut(cut(y, onset(y, near), to), fade);
let gain = refLufs + ROLE - maxLoudness(cuts.signal_on, 0.4);
const loudest = Math.max(...Object.values(cuts).map(peak));
gain = Math.min(gain, PEAK_CEILING - 20 * Math.log10(loudest));
report.gainDb = +gain.toFixed(2);
for (const [name, c] of Object.entries(cuts)) {
  const out = c.map(v => v * db(gain + (TRIM[name] ?? 0)));
  writeOgg(out, path.join(SOUNDS, OUT, name + '.ogg'));
  report[name] = {seconds: +(out.length / SR).toFixed(3), lufs400: +maxLoudness(out, 0.4).toFixed(1), peakDb: +(20 * Math.log10(peak(out))).toFixed(1)};
}
console.log(JSON.stringify(report, null, 1));

/** The quietest tenth's 10 ms RMS (dBFS): the floor between the clicks. */
function floor(s) {
  const W = SR / 100, r = [];
  for (let i = 0; i + W <= s.length; i += W) { let e = 0; for (let j = i; j < i + W; j++) e += s[j] * s[j]; r.push(10 * Math.log10(e / W + 1e-20)); }
  r.sort((a, b) => a - b);
  const n = Math.max(1, Math.floor(r.length / 10));
  return +(r.slice(0, n).reduce((a, b) => a + b, 0) / n).toFixed(1);
}

function onset(s, near) {
  const B = SR / 1000, thr = report.floorAfter + 20;
  for (let i = Math.round((near - 0.04) * SR); i < Math.round((near + 0.08) * SR); i += B) {
    let e = 0; for (let j = i; j < i + B; j++) e += s[j] * s[j];
    if (10 * Math.log10(e / B + 1e-20) > thr) return Math.max(0, i / SR - 0.005);
  }
  throw new Error('no onset near ' + near);
}

function cut(s, a, b) {
  const y = s.slice(Math.round(a * SR), Math.round(b * SR)), F = Math.round(0.002 * SR);
  for (let i = 0; i < F; i++) y[i] *= i / F;
  return y;
}

function fadeOut(s, f) {
  const F = Math.round(f * SR), y = Float64Array.from(s);
  for (let i = 0; i < F && i < y.length; i++) y[y.length - 1 - i] *= Math.pow(i / F, 2);
  return y;
}

function denoise(s) {
  const H = N / 4, frames = Math.ceil((s.length + N) / H), bins = N / 2 + 1;
  const pad = new Float64Array(frames * H + N); pad.set(s, N);
  const win = Float64Array.from({length: N}, (_, i) => 0.5 - 0.5 * Math.cos(2 * Math.PI * i / N));
  const re = [], im = [], pow = [];
  for (let f = 0; f < frames; f++) {
    const r = new Float64Array(N), q = new Float64Array(N);
    for (let i = 0; i < N; i++) r[i] = pad[f * H + i] * win[i];
    fft(r, q, false);
    re.push(r); im.push(q);
    const p = new Float64Array(bins);
    for (let k = 0; k < bins; k++) p[k] = r[k] * r[k] + q[k] * q[k];
    pow.push(p);
  }
  const noise = new Float64Array(bins);
  for (let k = 0; k < bins; k++) {
    const col = pow.map(p => p[k]).sort((a, b) => a - b);
    noise[k] = col[Math.floor(col.length / 2)];
  }
  const out = new Float64Array(pad.length), norm = new Float64Array(pad.length);
  let last = new Float64Array(bins).fill(1);
  for (let f = 0; f < frames; f++) {
    const g = new Float64Array(bins);
    for (let k = 0; k < bins; k++) g[k] = Math.max(FLOOR, Math.sqrt(Math.max(0, 1 - OVER * noise[k] / (pow[f][k] + 1e-30))));
    const sm = new Float64Array(bins);
    for (let k = 0; k < bins; k++) sm[k] = Math.max(FLOOR, Math.max(g[k], ((g[k - 1] ?? g[k]) + g[k] + (g[k + 1] ?? g[k])) / 3, last[k] * RELEASE));
    last = sm;
    const r = re[f], q = im[f];
    for (let k = 0; k < bins; k++) {
      r[k] *= sm[k]; q[k] *= sm[k];
      if (k > 0 && k < N / 2) { r[N - k] = r[k]; q[N - k] = -q[k]; }
    }
    fft(r, q, true);
    for (let i = 0; i < N; i++) { out[f * H + i] += r[i] * win[i]; norm[f * H + i] += win[i] * win[i]; }
  }
  const y = new Float64Array(s.length);
  for (let i = 0; i < s.length; i++) y[i] = norm[i + N] > 1e-9 ? out[i + N] / norm[i + N] : 0;
  return y;
}

/** In-place radix-2 FFT (inverse scaled by 1 / n). */
function fft(re, im, inverse) {
  const n = re.length;
  for (let i = 1, j = 0; i < n; i++) {
    let bit = n >> 1;
    for (; j & bit; bit >>= 1) j ^= bit;
    j ^= bit;
    if (i < j) { [re[i], re[j]] = [re[j], re[i]]; [im[i], im[j]] = [im[j], im[i]]; }
  }
  for (let len = 2; len <= n; len <<= 1) {
    const a = 2 * Math.PI / len * (inverse ? 1 : -1), wr = Math.cos(a), wi = Math.sin(a);
    for (let i = 0; i < n; i += len) {
      let cr = 1, ci = 0;
      for (let j = 0; j < len / 2; j++) {
        const ur = re[i + j], ui = im[i + j], k = i + j + len / 2;
        const vr = re[k] * cr - im[k] * ci, vi = re[k] * ci + im[k] * cr;
        re[i + j] = ur + vr; im[i + j] = ui + vi; re[k] = ur - vr; im[k] = ui - vi;
        const t = cr * wr - ci * wi; ci = cr * wi + ci * wr; cr = t;
      }
    }
  }
  if (inverse) for (let i = 0; i < n; i++) { re[i] /= n; im[i] /= n; }
}
