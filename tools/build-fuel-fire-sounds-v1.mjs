// Fuel fire sounds (docs/gameplay/fuel_fire_v1.md "声音"), from three user-generated source recordings (2026-10-05,
// E:/Download, stereo 48 kHz WAV, checked by SHA-256 prefix):
// - 点燃（短促轰燃）: 1 s, a whoosh with a fast decay (-10 LUFS at 0.1 s to -42 by 0.9 s) -> fuel_fire/ignite.ogg, used whole;
// - 持续燃烧（可循环）: 5 s of steady roaring fire, even within about 6 dB -> fuel_fire/burn_loop.ogg, a 4 s equal-power
//   crossfaded loop (noise: no period to keep in phase);
// - gas_tank_explode (2026-10-09, the second blast take; the first, 油罐爆炸, was 3 s and the user found it "特别短，一会就没声音了"):
//   12 s, the blast at once, 2.5 s of fireball roar (5-10 dB under the blast), then a rolling rumble (-20 dB at 5 s, -30 at
//   8 s, -40 at 9 s, under -60 by 11.3 s) -> fuel_fire/explode.ogg, used whole. Wide stereo (correlation 0.64) but no lag
//   between the channels, so the mono sum does not comb; it is 0.8-1.4 dB under the channels, then levelled anyway.
// A source not in sourceDir is skipped and its sound kept as it is (the 2026-10-05 takes were cleared from E:/Download).
// Levels (BS.1770 momentary 400 ms, peaks at or below -1 dBFS; reference: the industrial electrical box door): the
// ignition 2 LU above it, the burning loop 8 LU below, the blast 8 LU above.
//   node tools/build-fuel-fire-sounds-v1.mjs [sourceDir]     (default E:/Download; needs ffmpeg on PATH)
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {SR, decode, trimHead, peakAt, buildSounds, buildLoop} from './sound-mix-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const SRC = process.argv[2] ?? 'E:/Download';
const SOUNDS = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight/sounds');
const REFERENCE = 'distribution_panel/open.ogg';
const IGNITE = '点燃（短促轰燃）', LOOP = '持续燃烧（可循环）', BLAST = 'gas_tank_explode';
const SHA = {[IGNITE]: 'bbde1dcbab416ce4', [LOOP]: '274f085fe8780f13', [BLAST]: 'c9dbfc3db8431692'};
const have = name => fs.existsSync(path.join(SRC, name + '.wav'));

// a single-layer sound keeps its own timing: its peak lands where it is after the lead-in silence is cut
const peakTime = name => peakAt(trimHead(decode(path.join(SRC, name + '.wav')))) / SR;
const OUTPUTS = {
  ignite: {file: 'fuel_fire/ignite.ogg', reference: REFERENCE, offset: 2, src: IGNITE},
  explode: {file: 'fuel_fire/explode.ogg', reference: REFERENCE, offset: 8, src: BLAST},
};
const report = {skipped: []};
for (const [key, out] of Object.entries(OUTPUTS)) {
  if (!have(out.src)) { report.skipped.push(`${key} (${out.src}.wav not in ${SRC})`); continue; }
  Object.assign(report, buildSounds({srcDir: SRC, soundsDir: SOUNDS, sources: {[out.src]: SHA[out.src]}, outputs: {
    [key]: {file: out.file, reference: out.reference, offset: out.offset, layers: [{src: out.src, at: peakTime(out.src), gain: 0}]}}}));
}
if (!have(LOOP)) report.skipped.push(`burn_loop (${LOOP}.wav not in ${SRC})`);
else report.burn_loop = buildLoop({srcDir: SRC, soundsDir: SOUNDS, source: {name: LOOP, sha: SHA[LOOP]}, file: 'fuel_fire/burn_loop.ogg',
  from: 0.3, period: 0.05, periods: 80, crossfade: 8, equalPower: true, reference: REFERENCE, offset: -8});
console.log(JSON.stringify(report, null, 1));
