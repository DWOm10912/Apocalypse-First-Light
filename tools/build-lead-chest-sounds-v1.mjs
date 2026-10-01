// Lead Chest open / close sounds: six single-event source recordings, loudness-normalised, mixed onto the lid animation's
// keyframes (14 ticks = 0.7 s, smoothstep) and written as mono 48 kHz Ogg Vorbis.
//   node tools/build-lead-chest-sounds-v1.mjs [sourceDir]     (default E:/Download; needs ffmpeg on PATH)
// Sources (user generated, 1 s stereo 48 kHz WAV, checked by SHA-256 prefix): latch_open, latch_close, seal_release,
// seal_compress, lid_open, lid_close.
// Loudness and mixing: tools/sound-mix-lib.mjs. Reference: the industrial locker door sound of the same direction.
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {buildSounds} from './sound-mix-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const SRC = process.argv[2] ?? 'E:/Download';
const SOUNDS = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight/sounds');
const SOURCES = {latch_open: '1718100843eb1854', latch_close: '3abb5473c5f62843', seal_release: '31e1a9647df813ac',
  seal_compress: '8192757625dfd453', lid_open: '60eddcd5c22b571b', lid_close: 'dea922d3d3903652'};
// reference: the industrial locker door sounds (same playback volume 0.8)
const REFERENCE = {open: 'industrial_locker/open.ogg', close: 'industrial_locker/close.ogg'};

// Lid animation (profile 'open'): 0.7 s, smoothstep. Opening: latches release at the start, the seal breaks as the lid
// starts to lift, the hinge scrape peaks at the fastest swing (t = 0.35 s). Closing: the lid lands at 0.70 s, then the
// latches are snapped shut. 'at' is where the element's own peak sample lands; trim cuts the start of the source.
const MIX = {
  open: [
    {src: 'latch_open', at: 0.03, gain: -4},
    {src: 'latch_open', at: 0.09, gain: -5, rate: 1.04},
    {src: 'seal_release', at: 0.12, gain: -8},
    {src: 'lid_open', at: 0.36, gain: 0, trim: 0.10, fadeFrom: 0.62, fadeTo: 0.86},
  ],
  close: [
    {src: 'lid_close', at: 0.70, gain: 0},
    {src: 'seal_compress', at: 0.71, gain: -10},
    {src: 'latch_close', at: 0.76, gain: -5},
    {src: 'latch_close', at: 0.83, gain: -6, rate: 0.97},
  ],
};
console.log(JSON.stringify(buildSounds({srcDir: SRC, soundsDir: SOUNDS, sources: SOURCES, outputs: {
  open: {file: 'lead_chest/open.ogg', reference: REFERENCE.open, layers: MIX.open},
  close: {file: 'lead_chest/close.ogg', reference: REFERENCE.close, layers: MIX.close},
}}), null, 1));
