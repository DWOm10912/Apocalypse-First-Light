// Shared block-sound mixer: single-event source recordings -> loudness-normalised, keyframe-aligned mono Ogg Vorbis.
// Used by tools/build-lead-chest-sounds-v1.mjs, tools/build-industrial-electrical-box-sounds-v1.mjs,
// tools/build-cash-register-sounds-v1.mjs, tools/build-beverage-cooler-sounds-v1.mjs,
// tools/build-charging-station-sounds-v1.mjs, tools/build-beverage-cooler-compressor-sounds-v1.mjs,
// tools/build-container-search-sounds-v2.mjs (a mirrored loop) and tools/build-metal-trash-can-sounds-v1.mjs (needs ffmpeg).
// buildLoop makes seamless loops from steady hums (optionally with the ticks in the highs turned down), or, with
// mirror, from steady noise such as a rustle (the window forward then backward: no crossfade, no seam).
// Loudness: BS.1770 K-weighting. Each element is first brought to the same 100 ms short-window loudness (a click and a
// thud then sit at the same level), then mixed with a role gain; each finished sound is scaled so its maximum momentary
// loudness (400 ms) matches a reference sound plus an offset, with the sample peak kept at or below -1 dBFS.
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {execFileSync} from 'node:child_process';

export const SR = 48000;
export const PEAK_CEILING = -1;
export const TARGET_ELEMENT = -20;   // LUFS over 100 ms, before the role gain

export function decode(file) {   // mono = average of the channels (callers check the sources are highly correlated)
  const ch = +execFileSync('ffprobe', ['-v', 'error', '-select_streams', 'a:0', '-show_entries', 'stream=channels', '-of', 'csv=p=0', file]).toString().trim();
  const af = ch === 2 ? ['-af', 'pan=mono|c0=0.5*c0+0.5*c1'] : ['-ac', '1'];
  const buf = execFileSync('ffmpeg', ['-v', 'error', '-i', file, ...af, '-ar', String(SR), '-f', 'f32le', '-'], {maxBuffer: 1 << 28});
  return Float64Array.from(new Float32Array(buf.buffer, buf.byteOffset, buf.length / 4));
}
function biquad(x, b, a) { const y = new Float64Array(x.length); let x1 = 0, x2 = 0, y1 = 0, y2 = 0;
  for (let i = 0; i < x.length; i++) { const v = b[0] * x[i] + b[1] * x1 + b[2] * x2 - a[1] * y1 - a[2] * y2; x2 = x1; x1 = x[i]; y2 = y1; y1 = v; y[i] = v; } return y; }
export const kweight = x => biquad(biquad(x, [1.53512485958697, -2.69169618940638, 1.19839281085285], [1, -1.69065929318241, 0.73248077421585]),
  [1, -2, 1], [1, -1.99004745483398, 0.99007225036621]);
export function maxLoudness(x, win) {   // max K-weighted loudness over a sliding window (seconds), LUFS
  const k = kweight(x), W = Math.round(win * SR), H = Math.round(0.005 * SR), pad = new Float64Array(k.length + W); pad.set(k);
  let best = -Infinity;
  for (let s = 0; s + W <= pad.length; s += H) { let e = 0; for (let i = s; i < s + W; i++) e += pad[i] * pad[i]; best = Math.max(best, -0.691 + 10 * Math.log10(e / W + 1e-20)); }
  return best;
}
export const peak = x => x.reduce((p, v) => Math.max(p, Math.abs(v)), 0);
export const peakAt = x => { let p = 0, at = 0; x.forEach((v, i) => { if (Math.abs(v) > p) { p = Math.abs(v); at = i; } }); return at; };
export const db = g => Math.pow(10, g / 20);
export function resample(x, rate) {   // play `rate` times faster (pitch up), linear interpolation
  const n = Math.floor(x.length / rate), y = new Float64Array(n);
  for (let i = 0; i < n; i++) { const p = i * rate, j = Math.floor(p), f = p - j; y[i] = x[j] * (1 - f) + (x[j + 1] ?? 0) * f; }
  return y;
}
export function trimHead(x, thrDb = -50, keep = 0.005) {   // cut the silence before the onset, keep 5 ms of lead-in
  const thr = db(thrDb); let i = 0; while (i < x.length && Math.abs(x[i]) < thr) i++;
  return x.slice(Math.max(0, i - Math.round(keep * SR)));
}
export function trimTail(x, thrDb = -60, fade = 0.03) {   // cut after the last sample above the threshold, short fade-out
  const thr = db(thrDb); let end = x.length; while (end > 0 && Math.abs(x[end - 1]) < thr) end--;
  const y = x.slice(0, Math.min(x.length, end + Math.round(0.01 * SR))), F = Math.round(fade * SR);
  for (let i = 0; i < F && i < y.length; i++) y[y.length - 1 - i] *= i / F;
  return y;
}
export function writeOgg(x, file) {
  const tmp = path.join(os.tmpdir(), 'afl-sound-' + path.basename(path.dirname(file)) + '-' + path.basename(file, '.ogg') + '.f32');
  fs.writeFileSync(tmp, Buffer.from(new Float32Array(x).buffer));
  fs.mkdirSync(path.dirname(file), {recursive: true});
  execFileSync('ffmpeg', ['-v', 'error', '-y', '-f', 'f32le', '-ar', String(SR), '-ac', '1', '-i', tmp, '-c:a', 'libvorbis', '-q:a', '6', '-map_metadata', '-1', '-fflags', '+bitexact', file]);
  fs.unlinkSync(tmp);
}

/**
 * sources: {name: sha256 prefix} (WAV files in srcDir); outputs: {key: {file, reference, offset, layers}} with file and
 * reference relative to soundsDir. A layer is {src, at (where its peak sample lands, s), gain (dB), rate?, trim? (cut the
 * start of the source, s), fadeFrom? / fadeTo? (linear fade-out window in the trimmed source, s)}. Returns a report.
 */
export function buildSounds({srcDir, soundsDir, sources, outputs}) {
  const src = {}, level = {};
  for (const [name, sha] of Object.entries(sources)) {
    const file = path.join(srcDir, name + '.wav');
    const h = createHash('sha256').update(fs.readFileSync(file)).digest('hex').slice(0, 16);
    if (h !== sha) throw new Error(`${name}.wav changed (sha ${h}, expected ${sha})`);
    src[name] = decode(file);
    level[name] = maxLoudness(src[name], 0.1);
  }
  const report = {};
  for (const [key, out] of Object.entries(outputs)) {
    const parts = out.layers.map(L => {
      let x = src[L.src];
      if (L.trim) x = x.slice(Math.round(L.trim * SR));
      if (L.fadeFrom !== undefined) { x = x.slice(0, Math.round(L.fadeTo * SR)); const a = Math.round(L.fadeFrom * SR), b = x.length; for (let i = a; i < b; i++) x[i] *= 1 - (i - a) / (b - a); }
      if (L.rate) x = resample(x, L.rate);
      x = trimTail(trimHead(x));
      const g = db(TARGET_ELEMENT - level[L.src] + L.gain), start = Math.round(L.at * SR) - peakAt(x);
      if (start < 0) throw new Error(`${key}/${L.src}: peak cannot land at ${L.at}s`);
      return {x: x.map(v => v * g), start};
    });
    const mixed = new Float64Array(Math.max(...parts.map(p => p.start + p.x.length)));
    for (const p of parts) p.x.forEach((v, i) => { mixed[p.start + i] += v; });
    const ref = maxLoudness(decode(path.join(soundsDir, out.reference)), 0.4) + (out.offset ?? 0);
    let gain = ref - maxLoudness(mixed, 0.4);
    const pk = 20 * Math.log10(peak(mixed)) + gain;
    if (pk > PEAK_CEILING) gain -= pk - PEAK_CEILING;
    const final = trimTail(mixed.map(v => v * db(gain)));
    writeOgg(final, path.join(soundsDir, out.file));
    const enc = decode(path.join(soundsDir, out.file));
    report[key] = {file: out.file, seconds: +(final.length / SR).toFixed(3), targetLUFS: +ref.toFixed(1), maxMomentaryLUFS: +maxLoudness(enc, 0.4).toFixed(1),
      peakDbfs: +(20 * Math.log10(peak(enc))).toFixed(1), peakLimited: pk > PEAK_CEILING,
      layers: out.layers.map(L => `${L.src}${L.rate ? '@' + L.rate : ''} peak at ${L.at}s, ${(TARGET_ELEMENT - level[L.src] + L.gain).toFixed(1)} dB`)};
  }
  return {sourceLevels100ms: Object.fromEntries(Object.entries(level).map(([k, v]) => [k, +v.toFixed(1)])), ...report};
}

/**
 * Seamless loop from a steady, periodic source (a hum): the window [from, from + periods x period + crossfade periods) is
 * levelled (gain = mean level / RMS of each period, smoothed over `smooth` periods, so a swelling source plays at one
 * level), then the loop is `periods` whole periods long and its first `crossfade` periods are a linear crossfade from
 * the continuation after the loop end into the loop start; whole periods keep both sides in phase, so the seam does not
 * click. source: {name, sha} (WAV in srcDir); file and reference relative to soundsDir; offset as buildSounds. The level
 * is matched on the 400 ms momentary loudness of the loop. declick (dB, optional): ticks and rattles in a textured
 * source repeat with every loop and read as a hitch. They live in the highs while a hum lives in the lows, so the signal
 * is split at DECLICK.cutoff (400 Hz, 4th-order) into complementary bands (low-pass + remainder) and in the high band a 2.5 ms block whose
 * RMS stands more than `declick` dB above the median block RMS of the 80 ms either side is turned down to that limit;
 * the low band is untouched. Pick the window to avoid rattle bursts longer than a few blocks (a median cannot see them).
 * mirror (with crossfade 0): for steady noise with no period to keep in phase (a rustle). The levelled window plays
 * forward, then backward without its two end samples, so both joins repeat a sample's neighbour: no crossfade dip, no
 * seam, and the loop is twice the window. Reversed noise sounds the same; keep the window free of anything with a
 * direction (an attack, a word). equalPower (with a crossfade): sine / cosine crossfade weights instead of linear ones, for
 * noise with ticks in it (a rolling wheel): the two sides are uncorrelated, so a linear crossfade dips about 3 dB in the
 * middle while equal power keeps the level; mirroring would turn the ticks around. Returns a report.
 */
export function buildLoop({srcDir, soundsDir, source, file, from, period, periods, crossfade, smooth = 5, reference, offset = 0, declick, mirror = false, equalPower = false}) {
  const wav = path.join(srcDir, source.name + '.wav');
  const h = createHash('sha256').update(fs.readFileSync(wav)).digest('hex').slice(0, 16);
  if (h !== source.sha) throw new Error(`${source.name}.wav changed (sha ${h}, expected ${source.sha})`);
  const P = Math.round(period * SR), L = periods * P, C = crossfade * P, start = Math.round(from * SR);
  const x = decode(wav).slice(start, start + L + C);
  if (x.length < L + C) throw new Error(`${source.name}: window runs past the end of the source`);
  const rms = Array.from({length: periods + crossfade}, (_, k) => { let e = 0; for (let i = k * P; i < (k + 1) * P; i++) e += x[i] * x[i]; return Math.sqrt(e / P); });
  const smoothRms = rms.map((_, k) => { let s = 0, n = 0; for (let j = Math.max(0, k - smooth); j <= Math.min(rms.length - 1, k + smooth); j++) { s += rms[j]; n++; } return s / n; });
  const mean = Math.exp(smoothRms.reduce((s, v) => s + Math.log(v), 0) / smoothRms.length);
  // per-sample gain, interpolated between period centres
  let levelled = x.map((v, i) => { const t = Math.max(0, Math.min(smoothRms.length - 1, i / P - 0.5)), k = Math.floor(t), f = t - k;
    return v * mean / (smoothRms[k] * (1 - f) + smoothRms[Math.min(k + 1, smoothRms.length - 1)] * f); });
  const excessBefore = declick !== undefined ? maxHighExcess(levelled) : 0;
  if (declick !== undefined) levelled = declickHighs(levelled, declick);
  if (mirror && C) throw new Error(`${source.name}: a mirrored loop takes no crossfade`);
  let loop = levelled.slice(0, L);
  for (let i = 0; i < C; i++) { const w = i / C, a = equalPower ? Math.sin(w * Math.PI / 2) : w, b = equalPower ? Math.cos(w * Math.PI / 2) : 1 - w;
    loop[i] = levelled[i] * a + levelled[L + i] * b; }
  if (mirror) { const m = new Float64Array(2 * L - 2); m.set(loop); for (let i = 1; i < L - 1; i++) m[L - 1 + i] = loop[L - 1 - i]; loop = m; }
  const N = loop.length;
  const ref = maxLoudness(decode(path.join(soundsDir, reference)), 0.4) + offset;
  const twice = new Float64Array(2 * N); twice.set(loop); twice.set(loop, N);   // measure across the seam
  let gain = ref - maxLoudness(twice, 0.4);
  const pk = 20 * Math.log10(peak(loop)) + gain;
  if (pk > PEAK_CEILING) gain -= pk - PEAK_CEILING;
  const out = loop.map(v => v * db(gain));
  writeOgg(out, path.join(soundsDir, file));
  const enc = decode(path.join(soundsDir, file));
  const seam = Math.abs(out[0] - out[N - 1]), step = out.reduce((m, v, i) => i ? Math.max(m, Math.abs(v - out[i - 1])) : m, 0);
  return {file, seconds: +(enc.length / SR).toFixed(4), samples: enc.length, expectedSamples: N, targetLUFS: +ref.toFixed(1),
    maxMomentaryLUFS: +maxLoudness(enc, 0.4).toFixed(1), peakDbfs: +(20 * Math.log10(peak(enc))).toFixed(1), peakLimited: pk > PEAK_CEILING,
    sourceSwingDb: +(20 * Math.log10(Math.max(...rms) / Math.min(...rms))).toFixed(1), seamJump: +seam.toFixed(5), largestStep: +step.toFixed(5),
    ...(declick !== undefined ? {highExcessBeforeDb: +excessBefore.toFixed(1), highExcessAfterDb: +maxHighExcess(out).toFixed(1)} : {})};
}

export const DECLICK = {cutoff: 400, block: 0.0025, around: 0.08};
function lowPass(x, fc) {   // 4th-order Butterworth: two RBJ biquads, Q 0.5412 and 1.3066
  const w = 2 * Math.PI * fc / SR, c = Math.cos(w);
  return [0.5412, 1.3066].reduce((y, q) => { const al = Math.sin(w) / (2 * q), a0 = 1 + al;
    return biquad(y, [(1 - c) / 2 / a0, (1 - c) / a0, (1 - c) / 2 / a0], [1, -2 * c / a0, (1 - al) / a0]); }, x);
}
// high band and, per 2.5 ms block, its RMS over the median block RMS within +-80 ms, dB
function highExcess(x) {
  const lo = lowPass(x, DECLICK.cutoff), hi = x.map((v, i) => v - lo[i]);
  const B = Math.round(DECLICK.block * SR), M = Math.round(DECLICK.around / DECLICK.block), n = Math.ceil(x.length / B), rb = [];
  for (let k = 0; k < n; k++) { let e = 0, c = 0; for (let i = k * B; i < Math.min(x.length, (k + 1) * B); i++) { e += hi[i] * hi[i]; c++; } rb.push(Math.sqrt(e / c)); }
  const excess = rb.map((r, k) => { const w = rb.slice(Math.max(0, k - M), Math.min(n, k + M + 1)).sort((a, b) => a - b); return 20 * Math.log10((r + 1e-12) / (w[w.length >> 1] + 1e-12)); });
  return {lo, hi, B, excess};
}
const maxHighExcess = x => Math.max(...highExcess(x).excess);
// turn high-band blocks down to limitDb above their median; each block's gain is the lowest of it and its neighbours,
// interpolated per sample, so the gain moves before a tick arrives and never steps; low band + scaled high band
function declickHighs(x, limitDb) {
  const {lo, hi, B, excess} = highExcess(x), n = excess.length;
  const gains = excess.map(e => e > limitDb ? db(limitDb - e) : 1);
  const g = gains.map((v, k) => Math.min(v, k ? gains[k - 1] : 1, k + 1 < n ? gains[k + 1] : 1));
  return x.map((_, i) => { const t = Math.max(0, i / B - 0.5), k = Math.min(n - 1, Math.floor(t)), f = Math.min(1, t - k);
    return lo[i] + hi[i] * (g[k] * (1 - f) + g[Math.min(n - 1, k + 1)] * f); });
}
