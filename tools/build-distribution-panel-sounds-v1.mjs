// Distribution Panel door sounds: latch click, door open, door close, each from one user-generated source recording,
// loudness-normalised and placed on its animation keyframe (mono 48 kHz Ogg Vorbis). Made 2026-09-30 for the Industrial
// Electrical Box (removed 2026-10-07, docs/models/industrial_electrical_box_v2.md); its door ran the same 10 ticks, so the
// files moved to the panel unchanged. They are also the loudness reference of most later AFL sound generators.
//   node tools/build-distribution-panel-sounds-v1.mjs [sourceDir]     (default E:/Download; needs ffmpeg on PATH)
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

// Keyframes (profile block_mesh_profiles/distribution_panel.json, smoothstep easing):
// - latch: timed for the box's 4-tick quarter-turn latch (click at 0.18 s); the panel plays it as the door starts to open;
// - door, channel 'open', 10 ticks = 0.5 s: opening, the hinge creak runs during the swing and the source's light knock
//   lands when the door stops at 0.50 s; closing, the door hits the frame at 0.50 s (last frame).
// 'at' is where the source's own peak sample lands.
console.log(JSON.stringify(buildSounds({srcDir: SRC, soundsDir: SOUNDS, sources: SOURCES, outputs: {
  latch: {file: 'distribution_panel/latch.ogg', reference: 'industrial_locker/open.ogg', offset: -4,
    layers: [{src: 'ebox_latch', at: 0.18, gain: 0}]},
  open: {file: 'distribution_panel/open.ogg', reference: 'industrial_locker/open.ogg', offset: -2,
    layers: [{src: 'ebox_door_open', at: 0.50, gain: 0}]},
  close: {file: 'distribution_panel/close.ogg', reference: 'industrial_locker/close.ogg', offset: -2,
    layers: [{src: 'ebox_door_close', at: 0.50, gain: 0}]},
}}), null, 1));
