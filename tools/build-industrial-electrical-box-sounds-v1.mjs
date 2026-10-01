// Industrial Electrical Box sounds: quarter-turn latch (one sound for unlock and lock), door open, door close, each from
// one user-generated source recording, loudness-normalised and placed on its animation keyframe (mono 48 kHz Ogg Vorbis).
//   node tools/build-industrial-electrical-box-sounds-v1.mjs [sourceDir]     (default E:/Download; needs ffmpeg on PATH)
// Sources (1 s stereo 48 kHz WAV, left/right correlation 0.87-0.97, checked by SHA-256 prefix): ebox_latch,
// ebox_door_open, ebox_door_close. Loudness and mixing: tools/sound-mix-lib.mjs.
// Reference: the industrial locker door sounds; this door is smaller, so it sits 2 LU lower, the latch click 4 LU lower.
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {buildSounds} from './sound-mix-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const SRC = process.argv[2] ?? 'E:/Download';
const SOUNDS = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight/sounds');
const SOURCES = {ebox_latch: '998ad17092b1f13e', ebox_door_open: 'cbbec9c349ddb9a0', ebox_door_close: 'a88eadc2bed9fb24'};

// Keyframes (profile block_mesh_profiles/industrial_electrical_box.json, smoothstep easing):
// - latch, channel 'unlock', 4 ticks = 0.2 s: the cam clicks into its detent near the end of the quarter turn (0.18 s);
// - door, channel 'open', 10 ticks = 0.5 s: opening, the hinge creak runs during the swing and the source's light knock
//   lands when the door stops at 0.50 s; closing, the door hits the frame at 0.50 s (last frame).
// 'at' is where the source's own peak sample lands.
console.log(JSON.stringify(buildSounds({srcDir: SRC, soundsDir: SOUNDS, sources: SOURCES, outputs: {
  latch: {file: 'industrial_electrical_box/latch.ogg', reference: 'industrial_locker/open.ogg', offset: -4,
    layers: [{src: 'ebox_latch', at: 0.18, gain: 0}]},
  open: {file: 'industrial_electrical_box/open.ogg', reference: 'industrial_locker/open.ogg', offset: -2,
    layers: [{src: 'ebox_door_open', at: 0.50, gain: 0}]},
  close: {file: 'industrial_electrical_box/close.ogg', reference: 'industrial_locker/close.ogg', offset: -2,
    layers: [{src: 'ebox_door_close', at: 0.50, gain: 0}]},
}}), null, 1));
