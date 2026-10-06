// Fuel fire sounds (docs/gameplay/fuel_fire_v1.md "声音"), from three user-generated source recordings (2026-10-05,
// E:/Download, stereo 48 kHz WAV, checked by SHA-256 prefix):
// - 点燃（短促轰燃）: 1 s, a whoosh with a fast decay (-10 LUFS at 0.1 s to -42 by 0.9 s) -> fuel_fire/ignite.ogg, used whole;
// - 持续燃烧（可循环）: 5 s of steady roaring fire, even within about 6 dB -> fuel_fire/burn_loop.ogg, a 4 s equal-power
//   crossfaded loop (noise: no period to keep in phase);
// - 油罐爆炸: 3 s, a boom then a long decay -> fuel_fire/explode.ogg, used whole.
// Levels (BS.1770 momentary 400 ms, peaks at or below -1 dBFS; reference: the industrial electrical box door): the
// ignition 2 LU above it, the burning loop 8 LU below, the blast 8 LU above.
//   node tools/build-fuel-fire-sounds-v1.mjs [sourceDir]     (default E:/Download; needs ffmpeg on PATH)
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {SR, decode, trimHead, peakAt, buildSounds, buildLoop} from './sound-mix-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const SRC = process.argv[2] ?? 'E:/Download';
const SOUNDS = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight/sounds');
const REFERENCE = 'industrial_electrical_box/open.ogg';
const IGNITE = '点燃（短促轰燃）', LOOP = '持续燃烧（可循环）', BLAST = '油罐爆炸';
const SHA = {[IGNITE]: 'bbde1dcbab416ce4', [LOOP]: '274f085fe8780f13', [BLAST]: '08253c806c68b4fe'};

// a single-layer sound keeps its own timing: its peak lands where it is after the lead-in silence is cut
const peakTime = name => peakAt(trimHead(decode(path.join(SRC, name + '.wav')))) / SR;
const report = buildSounds({srcDir: SRC, soundsDir: SOUNDS, sources: {[IGNITE]: SHA[IGNITE], [BLAST]: SHA[BLAST]}, outputs: {
  ignite: {file: 'fuel_fire/ignite.ogg', reference: REFERENCE, offset: 2, layers: [{src: IGNITE, at: peakTime(IGNITE), gain: 0}]},
  explode: {file: 'fuel_fire/explode.ogg', reference: REFERENCE, offset: 8, layers: [{src: BLAST, at: peakTime(BLAST), gain: 0}]},
}});
report.burn_loop = buildLoop({srcDir: SRC, soundsDir: SOUNDS, source: {name: LOOP, sha: SHA[LOOP]}, file: 'fuel_fire/burn_loop.ogg',
  from: 0.3, period: 0.05, periods: 80, crossfade: 8, equalPower: true, reference: REFERENCE, offset: -8});
console.log(JSON.stringify(report, null, 1));
