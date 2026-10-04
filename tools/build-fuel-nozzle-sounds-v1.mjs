// Fuel Dispenser V1 nozzle sounds: take, hang up, breakaway, each cut from one user-generated source recording,
// loudness-normalised (mono 48 kHz Ogg Vorbis). They replace the vanilla clips the events borrowed at first.
//   node tools/build-fuel-nozzle-sounds-v1.mjs [sourceDir]     (default E:/Download; needs ffmpeg on PATH)
// Sources (1.0 s stereo 48 kHz WAV, checked by SHA-256 prefix):
//   拿起油枪 (take): a scrape from 0.08 s building to the clunk of the nozzle leaving the boot at 0.43 s, gone by 0.7 s;
//   挂回油枪 (hang): a quiet slide 0.0..0.65 s, the nozzle seating with a solid click-clunk at 0.71 s;
//   拉断阀脱开 (breakaway): the coupling's pop at 0.13 s, the hose whipping until about 0.45 s, then only noise floor.
// The events play the moment the click lands (the nozzle model swaps at once), so each source is trimmed close to its
// main hit: take keeps 0.19 s of the scrape before the clunk, hang 0.27 s of the slide before seating, breakaway starts on
// the pop. Reference: the industrial locker door; taking and hanging are handled gently (5 / 3 LU under it), the
// breakaway snaps (1 LU over). Loudness and mixing: tools/sound-mix-lib.mjs.
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {buildSounds} from './sound-mix-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const SRC = process.argv[2] ?? 'E:/Download';
const SOUNDS = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight/sounds');
const SOURCES = {'拿起油枪': '02d65ac1a309be22', '挂回油枪': 'bdf6900e40b880d3', '拉断阀脱开': '2437ab3225f312d9'};

console.log(JSON.stringify(buildSounds({srcDir: SRC, soundsDir: SOUNDS, sources: SOURCES, outputs: {
  take: {file: 'fuel_nozzle/take.ogg', reference: 'industrial_locker/open.ogg', offset: -5, layers: [
    {src: '拿起油枪', trim: 0.25, fadeFrom: 0.38, fadeTo: 0.50, at: 0.19, gain: 0},
  ]},
  hang: {file: 'fuel_nozzle/hang.ogg', reference: 'industrial_locker/open.ogg', offset: -3, layers: [
    {src: '挂回油枪', trim: 0.45, fadeFrom: 0.42, fadeTo: 0.55, at: 0.27, gain: 0},
  ]},
  breakaway: {file: 'fuel_nozzle/breakaway.ogg', reference: 'industrial_locker/open.ogg', offset: 1, layers: [
    {src: '拉断阀脱开', trim: 0.03, fadeFrom: 0.42, fadeTo: 0.62, at: 0.11, gain: 0},
  ]},
}}), null, 1));
