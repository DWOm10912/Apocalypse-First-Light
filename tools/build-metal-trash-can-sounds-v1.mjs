// Metal Trash Can V2 sounds: lid open, lid shut, each from one user-generated source recording, loudness-normalised and
// placed on its animation keyframe (mono 48 kHz Ogg Vorbis).
//   node tools/build-metal-trash-can-sounds-v1.mjs [sourceDir]     (default E:/Download; needs ffmpeg on PATH)
// Sources (1 s stereo 48 kHz WAV, left/right correlation 0.99 / 0.88, checked by SHA-256 prefix): trash_can_open,
// trash_can_close. Loudness and mixing: tools/sound-mix-lib.mjs. Reference: the industrial locker door sounds; the can's
// lid is smaller, so both sit 2 LU lower.
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {buildSounds} from './sound-mix-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const SRC = process.argv[2] ?? 'E:/Download';
const SOUNDS = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight/sounds');
const SOURCES = {trash_can_open: 'a1c12935ae1871b5', trash_can_close: '1666e17482e00ca1'};

// Keyframes (profile block_mesh_profiles/metal_trash_can.json, channel 'open', 8 ticks = 0.4 s, ease_in_out):
// - opening, the lid comes off the rim as it starts to move: the source's clank at 0.10 s, its hinge creak trails the swing;
// - shutting, the lid lands on the rim on the last frame: the source's clang at 0.40 s.
// 'at' is where the source's own peak sample lands.
console.log(JSON.stringify(buildSounds({srcDir: SRC, soundsDir: SOUNDS, sources: SOURCES, outputs: {
  open: {file: 'metal_trash_can/open.ogg', reference: 'industrial_locker/open.ogg', offset: -2,
    layers: [{src: 'trash_can_open', at: 0.10, gain: 0}]},
  close: {file: 'metal_trash_can/close.ogg', reference: 'industrial_locker/close.ogg', offset: -2,
    layers: [{src: 'trash_can_close', at: 0.40, gain: 0}]},
}}), null, 1));
