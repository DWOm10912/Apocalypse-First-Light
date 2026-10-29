// Blackridge .50 V2 gun sounds, cut from the user's Firefly takes (2026-10-28, one generation per action, 48 kHz stereo
// 32-bit float WAV, checked by SHA-256 prefix; docs/native_guns/blackridge_50_remake_v1.md 10.1). As the BR-51
// (tools/build-br51-01-sounds.mjs): the reloads, the inspects and the tiers all play these cuts. Written to a folder of
// their own (sounds/weapons/blackridge_50_v2/): the old blackridge_50 files stay as they were.
//   node tools/build-blackridge-50-sounds.mjs [sourceDir]   (default E:/Download/AFL/音效原始/blackridge_50, copies of the
//                                                            takes as downloaded to E:/Download; needs ffmpeg on PATH)
// Takes and cuts (source seconds):
//   开火.wav          fire (0.02 onset, tail to ~1 s)            -> fire.ogg (mono) + fire_near.ogg (the same mono in both ears)
//   空击.wav          the hammer snap                             -> dry_fire.ogg
//   按弹匣卡笋.wav    0.00-0.30 the button press                  -> magazine_release.ogg (the light release at 0.88 not used)
//   弹匣出.wav        the magazine slid out into the hand         -> magazine_out.ogg
//   弹匣入.wav        0.30-0.62 pushed in (scrape 0.39)           -> magazine_in.ogg
//                     0.62-end  the latch click (0.69)            -> magazine_seat.ogg (both at the gain of the whole 0.30-end)
//   套筒拉 + 放.wav   0.00-0.60 pulled back to the stop           -> slide_back.ogg
//                     1.50-2.00 let go, slams home (1.60)         -> slide_release.ogg (the second pair at 4.5 s not used)
//   保险关 + 开.wav   1.20-1.60 one detent click (1.33)           -> safety.ogg (the lever on and off both play it)
// All but the shot high-passed at 200 Hz (a floor of rumble under the takes, about -75 dB). Levels: maximum momentary
// loudness (400 ms, BS.1770) set per sound, sample peak at most -1 dBFS; the P9 for scale (fire -18.8, magazine_out -28.9,
// magazine_in -20.4, slide_back -19.6, slide_release -23.9 LUFS), the shot 3 LU over the P9's, the slide heavier.
import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {execFileSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {SR, decode, maxLoudness, peak, db, writeOgg, trimTail, highPass, PEAK_CEILING} from './sound-mix-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const SRC = process.argv[2] ?? 'E:/Download/AFL/音效原始/blackridge_50';
const OUT = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight/sounds/weapons/blackridge_50_v2');
const TAKES = {fire: ['开火.wav', '88840c4c59ea5a8c'], dry: ['空击.wav', '4153536f2088690d'], release: ['按弹匣卡笋.wav', 'ca280b059eba48c6'],
  out: ['弹匣出.wav', '4ae74deba667fcc6'], in: ['弹匣入.wav', '437ad2be8a4003b5'], slide: ['套筒拉 + 放.wav', '7de902658ce4c1db'],
  safety: ['保险关 + 开.wav', '0d57d0e3ab65cde8']};
// name: [take, from s, to s, target LUFS (400 ms) | {gainOf: other name}]
const CUTS = {
  fire: ['fire', 0, null, -16],
  dry_fire: ['dry', 0, null, -25],
  magazine_release: ['release', 0, 0.30, -31],
  magazine_out: ['out', 0, null, -28],
  magazine_in: ['in', 0.30, 0.62, {gainOf: 'pushed_in'}],
  magazine_seat: ['in', 0.62, null, {gainOf: 'pushed_in'}],
  slide_back: ['slide', 0, 0.60, -20],
  slide_release: ['slide', 1.50, 2.00, -19],
  safety: ['safety', 1.20, 1.60, -30],
};
const GAIN_REF = {pushed_in: ['in', 0.30, null, -21]};   // gain reference only, not written
const HIGH_PASS = 200;
const EDGE = 0.004;   // fade at an inner cut (s), so a cut never clicks

const take = {};
for (const [k, [file, sha]] of Object.entries(TAKES)) {
  const f = path.join(SRC, file);
  const h = createHash('sha256').update(fs.readFileSync(f)).digest('hex').slice(0, 16);
  if (h !== sha) throw new Error(`${file} changed (sha ${h}, expected ${sha})`);
  take[k] = k === 'fire' ? decode(f) : highPass(decode(f), HIGH_PASS);
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
