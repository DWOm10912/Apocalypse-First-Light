// Stamina V1 breathing (docs/项目内容/01 - 设计/生存/耐力.md §7): single nasal breaths, played one at a time at random by
// the client while tired (light set) or winded (heavy set), each pick with a small random pitch. Mono 48 kHz Ogg Vorbis.
//   node tools/build-stamina-breath-sounds-v1.mjs [sourceDir]     (default E:/Download; needs ffmpeg on PATH)
// Sources (user 2026-10-03, generated, stereo 48 kHz Ogg, checked by SHA-256 prefix). Each holds one or two breaths
// instead of the six to eight asked for, so the sets are put together from three breath pieces:
//   heavy  重鼻息（力竭用） 3.2 s: a quiet nasal inhale (0.00-0.62 s) and a long nasal exhale (1.33-2.80 s), 0.9 s apart;
//   light  轻鼻息（疲惫用） 3.3 s: one long soft breath (0.00-1.60 s); its second breath ends in a falling whistle
//          (about 2 kHz down to 1.3 kHz), not used.
// A high-pass at 120 Hz takes off the low wind rumble under the heavy source. Light breaths are the pieces near their
// recorded pace; heavy breaths are the same pieces time-compressed without a pitch change (ffmpeg atempo: shorter and
// sharper, not higher). The mouth-panting source (力竭喘气) is not used: its exhales carry the voice's resonances.
// Loudness: each breath's maximum momentary loudness (400 ms) is set against the container search rustle (heavy level
// with it, light 4 LU under), sample peak at or below -1 dBFS.
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {execFileSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {SR, decode, maxLoudness, peak, db, writeOgg} from './sound-mix-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const SRC = process.argv[2] ?? 'E:/Download';
const SOUNDS = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight/sounds');
const SOURCES = {heavy: ['重鼻息（力竭用）.ogg', 'dfa53536e42a609e'], light: ['轻鼻息（疲惫用）.ogg', '6b2911d6a70d4f92']};
const REFERENCE = 'container_search/rustle_loop.ogg';

const src = {};
for (const [key, [name, sha]] of Object.entries(SOURCES)) {
  const file = path.join(SRC, name);
  const got = createHash('sha256').update(fs.readFileSync(file)).digest('hex').slice(0, 16);
  if (got !== sha) throw new Error(`${name}: sha256 ${got}, expected ${sha}`);
  src[key] = decode(file);
}
/** ffmpeg audio filter on a mono 48 kHz signal. */
function filter(x, af) {
  const tmp = path.join(os.tmpdir(), 'afl-breath-filter.f32');
  fs.writeFileSync(tmp, Buffer.from(new Float32Array(x).buffer));
  const buf = execFileSync('ffmpeg', ['-v', 'error', '-f', 'f32le', '-ar', String(SR), '-ac', '1', '-i', tmp, '-af', af, '-f', 'f32le', '-'], {maxBuffer: 1 << 26});
  fs.unlinkSync(tmp);
  return Float64Array.from(new Float32Array(buf.buffer, buf.byteOffset, buf.length / 4));
}
/** A piece of a source (s), high-passed, with short linear fades so the cut edges do not click. */
function piece(x, from, to, fadeIn = 0.01, fadeOut = 0.06) {
  const y = filter(x.slice(Math.round(from * SR), Math.round(to * SR)), 'highpass=f=120:poles=2');
  const a = Math.round(fadeIn * SR), b = Math.round(fadeOut * SR);
  for (let i = 0; i < a && i < y.length; i++) y[i] *= i / a;
  for (let i = 0; i < b && i < y.length; i++) y[y.length - 1 - i] *= i / b;
  return y;
}
const silence = s => new Float64Array(Math.round(s * SR));
const join = (...parts) => { const y = new Float64Array(parts.reduce((n, p) => n + p.length, 0)); let o = 0; for (const p of parts) { y.set(p, o); o += p.length; } return y; };
const tempo = (x, t) => filter(x, `atempo=${t}`);

const inhale = piece(src.heavy, 0.00, 0.62);
const exhale = piece(src.heavy, 1.33, 2.80);
const soft = piece(src.light, 0.00, 1.60, 0.01, 0.12);

const ref = maxLoudness(decode(path.join(SOUNDS, REFERENCE)), 0.4);
const sets = {
  light: {offset: -4, breaths: [
    join(inhale, silence(0.12), exhale),           // the heavy source's breath, its 0.9 s pause closed up
    soft,                                           // the light source's breath
    join(inhale, silence(0.10), soft),              // heavy inhale into the soft breath
    tempo(join(inhale, silence(0.15), exhale), 0.92), // the first, a little slower
  ]},
  heavy: {offset: 0, breaths: [
    tempo(join(inhale, silence(0.04), exhale), 1.8),
    tempo(exhale, 1.6),                             // a sharp exhale on its own
    tempo(soft, 1.7),
    tempo(join(inhale, silence(0.03), soft), 1.9),
    join(tempo(inhale, 1.3), tempo(exhale, 2.0)),   // quick in, sharper out
  ]},
};
const report = {};
for (const [set, {offset, breaths}] of Object.entries(sets)) {
  breaths.forEach((x, i) => {
    const target = ref + offset;
    let g = db(target - maxLoudness(x, 0.4));
    const p = peak(x) * g;
    if (p > db(-1)) g *= db(-1) / p;
    const y = x.map(v => v * g);
    const file = `stamina/breath_${set}_${i + 1}.ogg`;
    writeOgg(y, path.join(SOUNDS, file));
    report[file] = {seconds: +(y.length / SR).toFixed(2), loudness: +maxLoudness(y, 0.4).toFixed(1), peakDb: +(20 * Math.log10(peak(y))).toFixed(1)};
  });
}
console.log(JSON.stringify({reference: +ref.toFixed(1), report}, null, 1));
