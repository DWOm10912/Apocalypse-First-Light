// Commercial Dumpster V2 sounds: lid open, lid shut, each from one user-generated source recording, loudness-normalised
// and placed on its animation keyframe (mono 48 kHz Ogg Vorbis). Shared by every colour.
//   node tools/build-commercial-dumpster-sounds-v1.mjs [sourceDir]     (default E:/Download; needs ffmpeg on PATH)
// Sources (1 s stereo 48 kHz WAV, left/right correlation 0.93 / 0.89, checked by SHA-256 prefix): dumpster_open,
// dumpster_close. Loudness and mixing: tools/sound-mix-lib.mjs. Reference: the industrial locker door sounds; a heavy
// plastic lid on a big empty steel bin, so the opening sits level with the locker's and the slam 1 LU above.
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {buildSounds} from './sound-mix-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const SRC = process.argv[2] ?? 'E:/Download';
const SOUNDS = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight/sounds');
const SOURCES = {dumpster_open: '1306616133c4afb4', dumpster_close: '45dc8e3678cfd82a'};

// Keyframes (profiles block_mesh_profiles/commercial_dumpster_*.json, channels 'left_open' / 'right_open', 10 ticks =
// 0.5 s, ease_in_out):
// - opening, the lid comes off the rim as it starts to swing: the source's first hit at 0.06 s, its creak runs on;
// - shutting, the lid lands on the rim on the last frame: the source's boom at 0.50 s.
// 'at' is where the source's own peak sample lands.
console.log(JSON.stringify(buildSounds({srcDir: SRC, soundsDir: SOUNDS, sources: SOURCES, outputs: {
  open: {file: 'commercial_dumpster/open.ogg', reference: 'industrial_locker/open.ogg', offset: 0,
    layers: [{src: 'dumpster_open', at: 0.06, gain: 0}]},
  close: {file: 'commercial_dumpster/close.ogg', reference: 'industrial_locker/close.ogg', offset: 1,
    layers: [{src: 'dumpster_close', at: 0.50, gain: 0}]},
}}), null, 1));
