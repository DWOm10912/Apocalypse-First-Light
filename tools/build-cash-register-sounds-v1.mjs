// Cash Register sounds: drawer open (with the "ka-ching" bell) and drawer close, from three user-generated source
// recordings, loudness-normalised and placed on the drawer animation keyframes (mono 48 kHz Ogg Vorbis).
//   node tools/build-cash-register-sounds-v1.mjs [sourceDir]     (default E:/Download; needs ffmpeg on PATH)
// Sources (1 s stereo 48 kHz WAV, checked by SHA-256 prefix): register_drawer_open (release click 0.13 s, roller slide,
// stop impact 0.40 s with coin rattle; left/right correlation 0.77), register_drawer_close (push 0.17-0.42 s, catch impact
// 0.47 s; 0.90), register_bell (lever "ka" 0.05 s, bell strike 0.11 s, ring until 0.98 s; 0.93). Mixing: tools/sound-mix-lib.mjs.
// Reference: the industrial electrical box door sounds (a comparable small steel box).
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {buildSounds} from './sound-mix-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const SRC = process.argv[2] ?? 'E:/Download';
const SOUNDS = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight/sounds');
const SOURCES = {register_drawer_open: '5c67df7e1f56b8d9', register_drawer_close: 'de7a622c096e1c7e', register_bell: '67cebec1225e54ae'};

// Keyframes (block_mesh_profiles/cash_register.json, channel 'open': 6 ticks = 0.30 s, linear both ways):
// - open: the bell strikes right after the release (peak 0.10 s, its lever "ka" just before); the source's 0.12 s of
//   pre-roll is cut so its release click lands near 0.03 s and the stop impact on the last frame (0.30 s); the bell rings
//   on under the coin rattle, 3 dB below the drawer;
// - close: the first 0.18 s of quiet slide are cut, the catch impact lands on the last frame (0.30 s).
// 'at' is where the source's own peak sample lands.
console.log(JSON.stringify(buildSounds({srcDir: SRC, soundsDir: SOUNDS, sources: SOURCES, outputs: {
  open: {file: 'cash_register/open.ogg', reference: 'industrial_electrical_box/open.ogg', offset: 0,
    layers: [{src: 'register_drawer_open', at: 0.30, gain: 0, trim: 0.12}, {src: 'register_bell', at: 0.10, gain: -3}]},
  close: {file: 'cash_register/close.ogg', reference: 'industrial_electrical_box/close.ogg', offset: -1,
    layers: [{src: 'register_drawer_close', at: 0.30, gain: 0, trim: 0.18}]},
}}), null, 1));
