// White-tailed deer sounds V1 (docs/gameplay/afl_whitetail_deer_d0_design.md 4.1): the user's Firefly takes (2026-10-28,
// E:/Download, 9 files, stereo 48 kHz, one take each, checked by SHA-256 prefix) levelled into mono Ogg Vorbis under
// sounds/deer/ (creature/deer/DeerSounds). The head shake had no take (it plays silent). The snort take was checked
// breathy, not voiced (spectral flatness 0.51 over its loudest 200 ms; the bleat and grunt are 0.06).
//
// Level (tools/sound-mix-lib.mjs): every file's max 400 ms momentary loudness sits at the metal trash can's lid (-24.3
// LUFS, the one-shot reference) plus a role offset, sample peak at most -1 dBFS:
//   snort +3 (the alarm, carries furthest), hurt +1, stomp 0, bleat -3, grunt -4, bound -4, brush -5, step -9, graze -10.
// All play at volume 1 in game, so the file is the level; the pitch varies a little per play. Voices are high-passed at
// 60 Hz, hooves and brush at 40 Hz.
//   node tools/build-deer-sounds-v1.mjs [sourceDir]     (default E:/Download; needs ffmpeg on PATH)
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {SR, decode, trimHead, trimTail, peakAt, highPass, buildSounds} from './sound-mix-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const SRC = process.argv[2] ?? 'E:/Download';
const SOUNDS = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight/sounds');
const REF = 'metal_trash_can/open.ogg';

// role: [take, sha prefix, offset dB, high-pass Hz]
const S = {
  snort: ['报警喷鼻.wav', '6f5b795c4e11bdad', 3, 60],
  stomp: ['跺脚.wav', 'fb7fb7e9d25aa21c', 0, 40],
  bleat: ['雌鹿联络.wav', '3fdec9fe580244ae', -3, 60],
  grunt: ['雄鹿低哼.wav', '2f916d1099b82013', -4, 60],
  hurt: ['受伤哀叫.wav', '401430328d3593d0', 1, 60],
  step: ['走路一步.wav', '836be2cae4c898f4', -9, 40],
  bound: ['奔跑落地.wav', 'c7930d9e0eb6300b', -4, 40],
  brush: ['穿过灌丛.wav', '5300f0428ef88b30', -5, 40],
  graze: ['吃草一口.wav', '8d8d4e574f747377', -10, 40],
};
const prepare = Object.fromEntries(Object.values(S).map(([f, , , hp]) => [f, x => highPass(x, hp)]));

const outputs = Object.fromEntries(Object.entries(S).map(([role, [file, , offset]]) => {
  const x = prepare[file](decode(path.join(SRC, file)));
  const at = Math.ceil(peakAt(trimTail(trimHead(x))) / SR * 1e4) / 1e4;
  return [role, {file: `deer/${role}.ogg`, reference: REF, offset, layers: [{src: file, at, gain: 0}]}];
}));

console.log(JSON.stringify(buildSounds({srcDir: SRC, soundsDir: SOUNDS, sources: Object.fromEntries(Object.values(S).map(([f, h]) => [f, h])), outputs, prepare}), null, 1));
