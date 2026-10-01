// Lead Chest open / close sounds: six single-event source recordings, loudness-normalised, mixed onto the lid animation's
// keyframes (14 ticks = 0.7 s, smoothstep) and written as mono 48 kHz Ogg Vorbis.
//   node tools/build-lead-chest-sounds-v1.mjs [sourceDir]     (default E:/Download; needs ffmpeg on PATH)
// Sources (user generated, 1 s stereo 48 kHz WAV, checked by SHA-256 prefix): latch_open, latch_close, seal_release,
// seal_compress, lid_open, lid_close.
// Loudness: BS.1770 K-weighting. Each element is first brought to the same 100 ms short-window loudness (a click and a
// thud then sit at the same level), then mixed with a role gain, then each finished sound is scaled so its maximum
// momentary loudness (400 ms) matches the industrial locker door sound of the same direction, with the sample peak kept
// at or below -1 dBFS.
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {execFileSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const SR = 48000, SRC = process.argv[2] ?? 'E:/Download';
const SOUNDS = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight/sounds');
const SOURCES = {latch_open: '1718100843eb1854', latch_close: '3abb5473c5f62843', seal_release: '31e1a9647df813ac',
  seal_compress: '8192757625dfd453', lid_open: '60eddcd5c22b571b', lid_close: 'dea922d3d3903652'};
// reference: the industrial locker door sounds (same playback volume 0.8)
const REFERENCE = {open: 'industrial_locker/open.ogg', close: 'industrial_locker/close.ogg'};

// Lid animation (profile 'open'): 0.7 s, smoothstep. Opening: latches release at the start, the seal breaks as the lid
// starts to lift, the hinge scrape peaks at the fastest swing (t = 0.35 s). Closing: the lid lands at 0.70 s, then the
// latches are snapped shut. 'at' is where the element's own peak sample lands; trim cuts the start of the source.
const MIX = {
  open: [
    {src: 'latch_open', at: 0.03, gain: -4},
    {src: 'latch_open', at: 0.09, gain: -5, rate: 1.04},
    {src: 'seal_release', at: 0.12, gain: -8},
    {src: 'lid_open', at: 0.36, gain: 0, trim: 0.10, fadeFrom: 0.62, fadeTo: 0.86},
  ],
  close: [
    {src: 'lid_close', at: 0.70, gain: 0},
    {src: 'seal_compress', at: 0.71, gain: -10},
    {src: 'latch_close', at: 0.76, gain: -5},
    {src: 'latch_close', at: 0.83, gain: -6, rate: 0.97},
  ],
};
const PEAK_CEILING = -1;

// ---------------- audio helpers ----------------
function decode(file) {   // mono = average of the channels (sources are > 0.98 correlated, no cancellation)
  const ch = +execFileSync('ffprobe', ['-v', 'error', '-select_streams', 'a:0', '-show_entries', 'stream=channels', '-of', 'csv=p=0', file]).toString().trim();
  const af = ch === 2 ? ['-af', 'pan=mono|c0=0.5*c0+0.5*c1'] : ['-ac', '1'];
  const buf = execFileSync('ffmpeg', ['-v', 'error', '-i', file, ...af, '-ar', String(SR), '-f', 'f32le', '-'], {maxBuffer: 1 << 28});
  return Float64Array.from(new Float32Array(buf.buffer, buf.byteOffset, buf.length / 4));
}
function biquad(x, b, a) { const y = new Float64Array(x.length); let x1 = 0, x2 = 0, y1 = 0, y2 = 0;
  for (let i = 0; i < x.length; i++) { const v = b[0] * x[i] + b[1] * x1 + b[2] * x2 - a[1] * y1 - a[2] * y2; x2 = x1; x1 = x[i]; y2 = y1; y1 = v; y[i] = v; } return y; }
const kweight = x => biquad(biquad(x, [1.53512485958697, -2.69169618940638, 1.19839281085285], [1, -1.69065929318241, 0.73248077421585]),
  [1, -2, 1], [1, -1.99004745483398, 0.99007225036621]);
function maxLoudness(x, win) {   // max K-weighted loudness over a sliding window (seconds), LUFS
  const k = kweight(x), W = Math.round(win * SR), H = Math.round(0.005 * SR), pad = new Float64Array(k.length + W); pad.set(k);
  let best = -Infinity;
  for (let s = 0; s + W <= pad.length; s += H) { let e = 0; for (let i = s; i < s + W; i++) e += pad[i] * pad[i]; best = Math.max(best, -0.691 + 10 * Math.log10(e / W + 1e-20)); }
  return best;
}
const peak = x => x.reduce((p, v) => Math.max(p, Math.abs(v)), 0);
const peakAt = x => { let p = 0, at = 0; x.forEach((v, i) => { if (Math.abs(v) > p) { p = Math.abs(v); at = i; } }); return at; };
const db = g => Math.pow(10, g / 20);
function resample(x, rate) {   // play `rate` times faster (pitch up), linear interpolation
  const n = Math.floor(x.length / rate), y = new Float64Array(n);
  for (let i = 0; i < n; i++) { const p = i * rate, j = Math.floor(p), f = p - j; y[i] = x[j] * (1 - f) + (x[j + 1] ?? 0) * f; }
  return y;
}
function trimHead(x, thrDb = -50, keep = 0.005) {   // cut the silence before the onset, keep 5 ms of lead-in
  const thr = db(thrDb); let i = 0; while (i < x.length && Math.abs(x[i]) < thr) i++;
  return x.slice(Math.max(0, i - Math.round(keep * SR)));
}
function trimTail(x, thrDb = -60, fade = 0.03) {   // cut after the last sample above the threshold, short fade-out
  const thr = db(thrDb); let end = x.length; while (end > 0 && Math.abs(x[end - 1]) < thr) end--;
  const y = x.slice(0, Math.min(x.length, end + Math.round(0.01 * SR))), F = Math.round(fade * SR);
  for (let i = 0; i < F && i < y.length; i++) y[y.length - 1 - i] *= i / F;
  return y;
}
function writeOgg(x, file) {
  const tmp = path.join(os.tmpdir(), 'afl-lead-chest-' + path.basename(file, '.ogg') + '.f32');
  fs.writeFileSync(tmp, Buffer.from(new Float32Array(x).buffer));
  fs.mkdirSync(path.dirname(file), {recursive: true});
  execFileSync('ffmpeg', ['-v', 'error', '-y', '-f', 'f32le', '-ar', String(SR), '-ac', '1', '-i', tmp, '-c:a', 'libvorbis', '-q:a', '6', '-map_metadata', '-1', '-fflags', '+bitexact', file]);
  fs.unlinkSync(tmp);
}

// ---------------- build ----------------
const src = {}, level = {};
for (const [name, sha] of Object.entries(SOURCES)) {
  const file = path.join(SRC, name + '.wav');
  const h = createHash('sha256').update(fs.readFileSync(file)).digest('hex').slice(0, 16);
  if (h !== sha) throw new Error(`${name}.wav changed (sha ${h}, expected ${sha})`);
  src[name] = decode(file);
  level[name] = maxLoudness(src[name], 0.1);
}
const TARGET_ELEMENT = -20;   // LUFS over 100 ms, before the role gain
const report = {};
for (const [dir, layers] of Object.entries(MIX)) {
  const parts = layers.map(L => {
    let x = src[L.src];
    if (L.trim) x = x.slice(Math.round(L.trim * SR));
    if (L.fadeFrom !== undefined) { x = x.slice(0, Math.round(L.fadeTo * SR)); const a = Math.round(L.fadeFrom * SR), b = x.length; for (let i = a; i < b; i++) x[i] *= 1 - (i - a) / (b - a); }
    if (L.rate) x = resample(x, L.rate);
    x = trimTail(trimHead(x));
    const g = db(TARGET_ELEMENT - level[L.src] + L.gain), start = Math.round(L.at * SR) - peakAt(x);
    if (start < 0) throw new Error(`${dir}/${L.src}: peak cannot land at ${L.at}s`);
    return {x: x.map(v => v * g), start};
  });
  const out = new Float64Array(Math.max(...parts.map(p => p.start + p.x.length)));
  for (const p of parts) p.x.forEach((v, i) => { out[p.start + i] += v; });
  const ref = maxLoudness(decode(path.join(SOUNDS, REFERENCE[dir])), 0.4);
  let gain = ref - maxLoudness(out, 0.4);
  const pk = 20 * Math.log10(peak(out)) + gain;
  if (pk > PEAK_CEILING) gain -= pk - PEAK_CEILING;
  const final = trimTail(out.map(v => v * db(gain)));
  writeOgg(final, path.join(SOUNDS, `lead_chest/${dir}.ogg`));
  const enc = decode(path.join(SOUNDS, `lead_chest/${dir}.ogg`));
  report[dir] = {seconds: +(final.length / SR).toFixed(3), referenceLUFS: +ref.toFixed(1), maxMomentaryLUFS: +maxLoudness(enc, 0.4).toFixed(1),
    peakDbfs: +(20 * Math.log10(peak(enc))).toFixed(1), peakLimited: pk > PEAK_CEILING,
    layers: layers.map(L => `${L.src}${L.rate ? '@' + L.rate : ''} peak at ${L.at}s, ${(TARGET_ELEMENT - level[L.src] + L.gain).toFixed(1)} dB`)};
}
console.log(JSON.stringify({sourceLevels100ms: Object.fromEntries(Object.entries(level).map(([k, v]) => [k, +v.toFixed(1)])), ...report}, null, 1));
