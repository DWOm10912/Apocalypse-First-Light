// Forest start home sounds V1 (docs/audio/forest_home_sounds_v1.md): the user's Firefly takes (2026-10-14, E:/Download,
// 21 files, stereo 48 kHz, checked by SHA-256 prefix) cut, levelled and timed into mono Ogg Vorbis for the start home's
// own pieces: the box stove (door, wood, fire, frying, boiling), the crank radio (crank, switch, static), the wood
// cabinets, the lamp switches, the power plugs, the rural mailbox's flag, the tube farm gate, axe chops and a falling tree.
// Not here (the user, 2026-10-14): the gun rack and the generator inlet box keep their vanilla sounds; the mailbox door
// had no take. B (the fuel stop) waits for its own pass.
//
// Level (tools/sound-mix-lib.mjs): every one-shot is scaled so its max 400 ms momentary loudness sits at the metal trash
// can's lid (-24.3 LUFS) plus a role offset (doors 0, clicks -7..-9, chops +3, the tree's crash +5), sample peak at most
// -1 dBFS; the loops sit at the portable generator's run loop (-26.8) minus 3..6. All play at volume 1 in game, so the
// file is the level. Door and click takes are high-passed first (the stove's door takes carried a rumble: 50 % of the
// close take's energy under 80 Hz, held to its cut-off end).
// Timing: the stove door and the cabinets swap their models at once, so their onset is at 0; the mailbox flag turns in
// 10 ticks (its click lands at 0.42 s); the gate swings in 24 ticks (the closing clang lands at 1.15 s, the leaf's stop);
// the radio's crank turns a quarter per 4-tick use (0.2 s slices of the ratchet takes); a chop every 10 ticks (each
// under 0.5 s); the tree cracks as it starts to fall and crashes on impact (one take cut in two at 2.75 s).
//   node tools/build-forest-home-sounds-v1.mjs [sourceDir]     (default E:/Download; needs ffmpeg on PATH)
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {SR, decode, trimHead, trimTail, peakAt, highPass, buildSounds, buildLoop} from './sound-mix-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const SRC = process.argv[2] ?? 'E:/Download';
const SOUNDS = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight/sounds');
const DOOR_REF = 'metal_trash_can/open.ogg', LOOP_REF = 'portable_generator/run_loop.ogg';

const S = {
  flag: ['信箱红旗.wav', '09f87afb9917e5e5'], chain: ['拉线灯座.wav', 'd2ffc1139b7ed18d'], radioSwitch: ['收音机开关.wav', '39b2fdcdfceae305'],
  crank1: ['收音机摇柄1.wav', '666595fb676695f3'], crank2: ['收音机摇柄2.wav', '5875e4356e47cac1'], tree: ['树倒下.wav', '386e2718f9d899c8'],
  wood: ['添柴.wav', '668b51e96831062a'], chop: ['砍树.wav', 'f393f318ad83368d'], gateClose: ['农场大门关.ogg', '6247ca24d4d966f4'],
  gateOpen: ['农场大门开.ogg', '53a471290db5eda1'], cabClose: ['厨房柜门关.ogg', 'c4fd7316e8159f96'], cabOpen: ['厨房柜门开.ogg', '69fbdca35fc2de72'],
  wallSwitch: ['墙面开关.ogg', '94f3fccf458e8c17'], plugOut: ['插头拔出.ogg', '877765da69198d99'], plugIn: ['插头插入.ogg', 'e239f2d8975bae1a'],
  stoveClose: ['柴炉关门.ogg', '7ec70da3899a0099'], stoveOpen: ['柴炉开门.ogg', '02e42d29da0810fa'],
};
// high-pass per source (Hz): rumble under the doors and clicks; the chops and the tree keep their weight
const HP = {stoveOpen: 110, stoveClose: 110, cabOpen: 60, cabClose: 50, wood: 60, flag: 80, chain: 100, radioSwitch: 100,
  crank1: 100, crank2: 100, wallSwitch: 100, plugIn: 100, plugOut: 100, gateOpen: 60, gateClose: 50, chop: 40, tree: 30};
const name = k => S[k][0];
const prepare = Object.fromEntries(Object.entries(HP).map(([k, hz]) => [name(k), x => highPass(x, hz)]));

/** A layer at its own timing: where its loudest sample falls once cut and trimmed as buildSounds does. */
function own(k, cut = {}) {
  let x = prepare[name(k)](decode(path.join(SRC, name(k))));
  if (cut.trim) x = x.slice(Math.round(cut.trim * SR));
  if (cut.fadeFrom !== undefined) x = x.slice(0, Math.round(cut.fadeTo * SR));
  return {src: name(k), at: Math.ceil(peakAt(trimTail(trimHead(x))) / SR * 1e4) / 1e4, gain: 0, ...cut};
}
const at = (k, t, cut = {}) => ({src: name(k), at: t, gain: 0, ...cut});
const one = (file, offset, layer) => ({file, reference: DOOR_REF, offset, layers: [layer]});

const outputs = {
  // box stove: the door swaps at once; a split dropped in
  stove_door_open: one('box_stove/door_open.ogg', 0, own('stoveOpen', {fadeFrom: 0.32, fadeTo: 0.55})),
  stove_door_close: one('box_stove/door_close.ogg', 0, own('stoveClose', {fadeFrom: 0.36, fadeTo: 0.6})),
  stove_add_wood: one('box_stove/add_wood.ogg', -2, own('wood', {fadeFrom: 0.6, fadeTo: 0.95})),
  // crank radio: the switch's two clicks (on, off); the crank, 0.2 s of ratchet per quarter turn, eight slices
  radio_switch_on: one('emergency_radio/switch_on.ogg', -8, own('radioSwitch', {fadeFrom: 0.5, fadeTo: 0.62})),
  radio_switch_off: one('emergency_radio/switch_off.ogg', -8, own('radioSwitch', {trim: 0.64})),
  ...Object.fromEntries([['crank1', 0.02], ['crank1', 0.22], ['crank1', 0.42], ['crank1', 0.62], ['crank2', 0.05], ['crank2', 0.25], ['crank2', 0.45], ['crank2', 0.65]]
    .map(([k, t], i) => [`radio_crank_${i + 1}`, one(`emergency_radio/crank_${i + 1}.ogg`, -9, own(k, {trim: t, fadeIn: 0.004, fadeFrom: 0.17, fadeTo: 0.2}))])),
  // wood cabinets (the counter and the sink counter): the doors swap at once
  cabinet_open: one('wood_cabinet/open.ogg', -3, own('cabOpen')),
  cabinet_close: one('wood_cabinet/close.ogg', -3, own('cabClose')),
  // lamp switches and plugs
  pull_chain_1: one('lamp_switch/pull_chain_1.ogg', -7, own('chain', {fadeFrom: 0.55, fadeTo: 0.7})),
  pull_chain_2: one('lamp_switch/pull_chain_2.ogg', -7, own('chain', {trim: 0.72})),
  wall_switch: one('lamp_switch/wall_switch.ogg', -7, own('wallSwitch')),
  plug_in: one('power_plug/plug_in.ogg', -8, own('plugIn')),
  plug_out: one('power_plug/plug_out.ogg', -8, own('plugOut')),
  // rural mailbox: the flag turns in 10 ticks, its click at the stop
  mailbox_flag_up: one('rural_mailbox/flag_up.ogg', -4, at('flag', 0.42, {fadeFrom: 0.9, fadeTo: 1.25})),
  mailbox_flag_down: one('rural_mailbox/flag_down.ogg', -4, at('flag', 0.42, {trim: 1.3})),
  // tube farm gate: the chain and the swing from the start; closing, the clang at the leaf's stop (24 ticks)
  gate_open: one('tube_farm_gate/open.ogg', 2, own('gateOpen')),
  gate_close: one('tube_farm_gate/close.ogg', 2, at('gateClose', 1.15)),
  // felling: three chops, the crack as the tree goes, the crash on impact
  chop_1: one('tree_felling/chop_1.ogg', 3, own('chop', {fadeFrom: 0.9, fadeTo: 1.2})),
  chop_2: one('tree_felling/chop_2.ogg', 3, own('chop', {trim: 1.7, fadeFrom: 0.9, fadeTo: 1.2})),
  chop_3: one('tree_felling/chop_3.ogg', 3, own('chop', {trim: 3.84, fadeFrom: 0.9, fadeTo: 1.16})),
  tree_crack: one('tree_felling/crack.ogg', 1, own('tree', {fadeFrom: 2.55, fadeTo: 2.75})),
  tree_crash: one('tree_felling/crash.ogg', 5, own('tree', {trim: 2.75})),
};

const report = {one: buildSounds({srcDir: SRC, soundsDir: SOUNDS, sources: Object.fromEntries(Object.values(S)), outputs, prepare})};
// loops: noisy textures (crackle, sizzle, bubbling, static): 12 s windows, 1 s equal-power crossfade at the seam
const loop = (src, sha, file, offset, from) => buildLoop({srcDir: SRC, soundsDir: SOUNDS, source: {name: src, sha}, file, from,
  period: 0.05, periods: 240, crossfade: 20, smooth: 20, reference: LOOP_REF, offset, equalPower: true, prepare: x => highPass(x, 40)});
report.loops = {
  fire: loop('炉火循环', '4d2e24ab90ad720d', 'box_stove/fire_loop.ogg', -6, 4),
  fry: loop('煎炒', 'df5484e156787d73', 'box_stove/fry_loop.ogg', -3, 4),
  boil: loop('烧水', 'e5949ba934cba2f8', 'box_stove/boil_loop.ogg', -4, 4),
  static: loop('收音机杂音', 'acc788e283d14fae', 'emergency_radio/static_loop.ogg', -5, 4),
};
console.log(JSON.stringify(report, null, 1));
