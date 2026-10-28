// BR-51 V3 (M14 mechanism) gun sounds, cut from the user's Firefly takes (2026-10-21, 48 kHz stereo WAV, checked by SHA-256
// prefix; docs/native_guns/br51_01_v3_game_model_v1.md, "声音"). One take per sound family, as the P9: the reload and the
// inspect play the same cuts.
//   node tools/build-br51-01-sounds.mjs [sourceDir]     (default E:/Download; needs ffmpeg on PATH)
// Takes and cuts (source seconds):
//   开火.wav            fire (0.03 onset, 2 s tail)               -> fire.ogg (mono) + fire_near.ogg (the same mono in both ears)
//   弹匣出 + 入.wav     0.00-0.30 latch press                       -> magazine_release.ogg
//                       0.38-1.40 the magazine rocked out             -> magazine_out.ogg
//                       1.50-1.72 hooked and rocked in (scrape 1.59)   -> magazine_in.ogg
//                       1.72-2.00 the latch click (1.78)               -> magazine_seat.ogg (the clip's seat; split 2026-10-27 so the
//                                 tier reloads find their seat cue, NativeReloadTiers; both at the gain of the whole 1.50-end)
//   拉机柄拉 + 放.wav   0.00-0.27 operating rod pulled back           -> charging_back.ogg
//                       0.27-end  let go, the bolt slams home         -> charging_release.ogg
//   掏枪.wav / 收枪.wav  draw / put away                              -> draw.ogg / put_away.ogg
// Levels: maximum momentary loudness (400 ms, BS.1770) set per sound, sample peak at most -1 dBFS. The P9 for scale: fire
// -18.8, magazine_out -28.9, magazine_in -20.4, slide_back -19.6, slide_release -23.9, draw -26.9, put_away -26.4 LUFS. The
// rifle's shot sits 4 LU over the pistol's; the latch press and the rock-out keep their level against each other (one gain).
import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {execFileSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {SR, decode, maxLoudness, peak, db, writeOgg, trimTail, PEAK_CEILING} from './sound-mix-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const SRC = process.argv[2] ?? 'E:/Download';
const OUT = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight/sounds/weapons/br51_01');
const TAKES = {fire: ['开火.wav', '7356cdd9cfd5cdd8'], mag: ['弹匣出 + 入.wav', '1a2a82bb22267ab9'], charge: ['拉机柄拉 + 放.wav', 'eb64fc848315c3b3'],
  draw: ['掏枪.wav', '833b8981c9422836'], putAway: ['收枪.wav', 'e51da73ce06362e4']};
// name: [take, from s, to s, target LUFS (400 ms) | {gainOf: other name}]
const CUTS = {
  fire: ['fire', 0, null, -15],
  magazine_release: ['mag', 0, 0.30, {gainOf: 'magazine_out'}],
  magazine_out: ['mag', 0.38, 1.40, -28],
  magazine_in: ['mag', 1.50, 1.72, {gainOf: 'rocked_in'}],
  magazine_seat: ['mag', 1.72, null, {gainOf: 'rocked_in'}],
  charging_back: ['charge', 0, 0.27, -24],
  charging_release: ['charge', 0.27, null, -18.5],
  draw: ['draw', 0, null, -27],
  put_away: ['putAway', 0, null, -27],
};
const EDGE = 0.004;   // fade at an inner cut (s), so a cut never clicks
// gain references that are not written: the whole rock-in, as cut until 2026-10-27
const GAIN_REF = {rocked_in: ['mag', 1.50, null, -21]};

const take = {};
for (const [k, [file, sha]] of Object.entries(TAKES)) {
  const f = path.join(SRC, file);
  const h = createHash('sha256').update(fs.readFileSync(f)).digest('hex').slice(0, 16);
  if (h !== sha) throw new Error(`${file} changed (sha ${h}, expected ${sha})`);
  take[k] = decode(f);
}
const cut = ([t, from, to]) => {
  const x = take[t].slice(Math.round(from * SR), to == null ? undefined : Math.round(to * SR)), F = Math.round(EDGE * SR);
  for (let i = 0; i < F && i < x.length; i++) { if (from > 0) x[i] *= i / F; if (to != null) x[x.length - 1 - i] *= i / F; }
  return x;
};
const gains = {};
const gainFor = name => {
  if (gains[name] != null) return gains[name];
  const c = CUTS[name] ?? GAIN_REF[name], target = c[3];
  if (typeof target === 'object') return gains[name] = gainFor(target.gainOf);
  const x = cut(c);
  let g = target - maxLoudness(x, 0.4);
  const pk = 20 * Math.log10(peak(x)) + g;
  if (pk > PEAK_CEILING) g -= pk - PEAK_CEILING;
  return gains[name] = g;
};
const report = {};
fs.mkdirSync(OUT, {recursive: true});
for (const name of Object.keys(CUTS)) {
  const g = gainFor(name);
  let x = cut(CUTS[name]).map(v => v * db(g));
  const pk = 20 * Math.log10(peak(x));
  if (pk > PEAK_CEILING) x = x.map(v => v * db(PEAK_CEILING - pk));
  if (CUTS[name][2] == null) x = trimTail(x);
  const file = path.join(OUT, name + '.ogg');
  writeOgg(x, file);
  const enc = decode(file);
  report[name] = {seconds: +(x.length / SR).toFixed(3), gainDb: +g.toFixed(1), maxMomentaryLUFS: +maxLoudness(enc, 0.4).toFixed(1), peakDbfs: +(20 * Math.log10(peak(enc))).toFixed(1)};
}
// fire_near: the shooter's own shot at the ear (client/RangedSoundInstance plays <event>_near), both channels the mono shot
execFileSync('ffmpeg', ['-v', 'error', '-y', '-i', path.join(OUT, 'fire.ogg'), '-af', 'pan=stereo|c0=c0|c1=c0', '-c:a', 'libvorbis', '-q:a', '6',
  '-map_metadata', '-1', '-fflags', '+bitexact', path.join(OUT, 'fire_near.ogg')]);
report.fire_near = {from: 'fire.ogg', channels: 2};
console.log(JSON.stringify(report, null, 1));
