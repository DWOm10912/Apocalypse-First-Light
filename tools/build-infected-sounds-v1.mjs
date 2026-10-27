// Infected sounds V1 (docs/gameplay/afl_infected_z0_design.md 12.1): the user's Firefly takes (2026-10-26, E:/Download,
// 14 files, stereo 48 kHz, channels 0.94-1.0 correlated, checked by SHA-256 prefix) levelled into mono Ogg Vorbis under
// sounds/infected/ for the shambler (Z1a). One take = one variant; the door take holds two blows, cut in two at 0.55 s.
// The drag step had no take (the shambler uses the block's own step sound for now); the body fall reuses health/fall_*.
//
// Level (tools/sound-mix-lib.mjs): every file's max 400 ms momentary loudness sits at the metal trash can's lid
// (-24.3 LUFS, the one-shot reference of the forest home sounds) plus a role offset, sample peak at most -1 dBFS:
//   attack +3, alert +2, hurt +1, death 0, idle groan -2, chase breath -6 (it repeats every stride; still above the
//   player's own heavy breathing, stamina/breath_heavy at -33), door blow +5 (the tree crash's weight).
// All play at volume 1 in game, so the file is the level. Voices are high-passed at 60 Hz, the door at 40 Hz.
//   node tools/build-infected-sounds-v1.mjs [sourceDir]     (default E:/Download; needs ffmpeg on PATH)
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {SR, decode, trimHead, trimTail, peakAt, highPass, buildSounds} from './sound-mix-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const SRC = process.argv[2] ?? 'E:/Download';
const SOUNDS = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight/sounds');
const REF = 'metal_trash_can/open.ogg';

const S = {
  idle1: ['待机低吼.wav', 'ee13f8fb190501ca'], idle2: ['待机低吼 (2).wav', 'fbe1ea06c9327003'], idle3: ['待机低吼 (3).wav', '3a564b5bdfd820f1'],
  alert1: ['发现目标.wav', '2a789ca41f744ab1'], alert2: ['发现目标 (2).wav', 'b898299b50bdc90f'],
  chase1: ['追赶喘息.wav', '581a12088e4f0253'],
  attack1: ['扑抓.wav', '2c986a00f60712f2'], attack2: ['扑抓 (2).wav', '5ed601c307cf0d25'], attack3: ['扑抓 (3).wav', '21972074283c5be2'],
  hurt1: ['受伤.wav', '4b70201c490e7392'], hurt2: ['受伤 (2).wav', '1e1f65d8754387d4'],
  death1: ['死亡.wav', '30963d348e73686b'], death2: ['死亡 (2).wav', '7ae73235cef91fae'],
  door: ['砸门.wav', '80a4a5dc82d6647c'],
};
const name = k => S[k][0];
const prepare = Object.fromEntries(Object.keys(S).map(k => [name(k), x => highPass(x, k === 'door' ? 40 : 60)]));

/** The take's own timing: its loudest sample lands where it falls once cut and trimmed as buildSounds does. */
function own(k, cut = {}) {
  let x = prepare[name(k)](decode(path.join(SRC, name(k))));
  if (cut.trim) x = x.slice(Math.round(cut.trim * SR));
  if (cut.fadeFrom !== undefined) x = x.slice(0, Math.round(cut.fadeTo * SR));
  return {src: name(k), at: Math.ceil(peakAt(trimTail(trimHead(x))) / SR * 1e4) / 1e4, gain: 0, ...cut};
}
const one = (file, offset, layer) => ({file, reference: REF, offset, layers: [layer]});
const set = (role, offset, keys) => Object.fromEntries(keys.map((k, i) => [`${role}_${i + 1}`, one(`infected/${role}_${i + 1}.ogg`, offset, own(k))]));

const outputs = {
  ...set('idle', -2, ['idle1', 'idle2', 'idle3']),
  ...set('alert', 2, ['alert1', 'alert2']),
  ...set('chase', -6, ['chase1']),
  ...set('attack', 3, ['attack1', 'attack2', 'attack3']),
  ...set('hurt', 1, ['hurt1', 'hurt2']),
  ...set('death', 0, ['death1', 'death2']),
  door_bang_1: one('infected/door_bang_1.ogg', 5, own('door', {fadeFrom: 0.45, fadeTo: 0.55})),
  door_bang_2: one('infected/door_bang_2.ogg', 5, own('door', {trim: 0.55})),
};

console.log(JSON.stringify(buildSounds({srcDir: SRC, soundsDir: SOUNDS, sources: Object.fromEntries(Object.values(S)), outputs, prepare}), null, 1));
