// Beverage Cooler door sounds (both doors share them): open and close, each from one user-generated source recording,
// loudness-normalised and placed on the door animation keyframes (mono 48 kHz Ogg Vorbis).
//   node tools/build-beverage-cooler-sounds-v1.mjs [sourceDir]     (default E:/Download; needs ffmpeg on PATH)
// Sources (1 s stereo 48 kHz WAV, left/right correlation 0.997 / 0.999, checked by SHA-256 prefix): cooler_door_open
// (handle touch 0.06 s, rubber seal pop 0.17 s, glass rattle until 0.34 s), cooler_door_close (contact 0.10 s, seal
// suction 0.14 s, settles by 0.30 s). Mixing: tools/sound-mix-lib.mjs.
// Reference: the industrial electrical box door sounds; a glass door on a rubber gasket is softer, 1 LU lower.
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {buildSounds} from './sound-mix-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const SRC = process.argv[2] ?? 'E:/Download';
const SOUNDS = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight/sounds');
const SOURCES = {cooler_door_open: '686d16426f3353a9', cooler_door_close: '6955d69fafa362f4'};

// Keyframes (block_mesh_profiles/beverage_cooler.json, channels 'left_open' / 'right_open': 8 ticks = 0.40 s,
// ease_in_out; the sound starts at the click, together with the swing):
// - open: the handle touch before the source's pop cannot precede the click, so its first 0.14 s are cut; the seal pop
//   lands at 0.05 s, as the slowly starting door leaves the gasket;
// - close: the door eases onto the gasket on the last frame, the seal suction lands at 0.40 s.
// 'at' is where the source's own peak sample lands.
console.log(JSON.stringify(buildSounds({srcDir: SRC, soundsDir: SOUNDS, sources: SOURCES, outputs: {
  open: {file: 'beverage_cooler/door_open.ogg', reference: 'industrial_electrical_box/open.ogg', offset: -1,
    layers: [{src: 'cooler_door_open', at: 0.05, gain: 0, trim: 0.14}]},
  close: {file: 'beverage_cooler/door_close.ogg', reference: 'industrial_electrical_box/close.ogg', offset: -1,
    layers: [{src: 'cooler_door_close', at: 0.40, gain: 0}]},
}}), null, 1));
