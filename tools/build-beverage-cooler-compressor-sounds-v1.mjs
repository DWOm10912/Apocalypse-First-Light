// Beverage Cooler compressor sounds: start, stop and the running loop, from three user-generated source recordings
// (mono 48 kHz Ogg Vorbis). The compressor runs in cycles while the cooler has power (BeverageCoolerBlockEntity); the
// door sounds are tools/build-beverage-cooler-sounds-v1.mjs.
//   node tools/build-beverage-cooler-compressor-sounds-v1.mjs [sourceDir]     (default E:/Download; needs ffmpeg on PATH)
// Sources (1 s stereo 48 kHz WAV, left/right correlation 0.996-0.998, checked by SHA-256 prefix):
// - cooler_compressor_start: relay click at 0.025 s (the peak), motor kicks at 0.10 s and 0.26 s, the hum dying away by
//   0.98 s; used whole;
// - cooler_compressor_stop: steady low hum until a shudder at 0.66 s, quiet by 0.8 s; the first 0.30 s of hum are cut so
//   the wind-down follows the loop's end sooner;
// - cooler_compressor_loop: a 58.9 Hz motor (period 815 samples) with harmonics at 117 / 176 / 234 Hz, level within
//   3 dB; a burst of rattles at 0.22-0.34 s (12.7 dB above the texture) and single ticks at 0.67 / 0.82-0.94 s. The first
//   loop (49 periods from 0.02 s) carried the burst, heard in game as a hitch once a loop (2026-10-01). Now: 29 periods
//   (0.49 s) from 0.35 s, after the burst, levelled, ticks in the highs turned down to 2.5 dB over their median
//   (declick), cross-faded over 4 periods (buildLoop). Checked with the Minecraft decoder: exact length, clean seam, the
//   largest transient left 9.3 dB against a 4.0 dB texture median (was 12.5).
// Reference: the industrial electrical box door; start 8 LU below it, stop 10 LU below, the loop 14 LU below.
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {buildSounds, buildLoop} from './sound-mix-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const SRC = process.argv[2] ?? 'E:/Download';
const SOUNDS = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight/sounds');
const SOURCES = {cooler_compressor_start: '7734c93cca35155a', cooler_compressor_stop: 'e1bf56090025f5f7'};
const LOOP = {name: 'cooler_compressor_loop', sha: '6bc62f3fe43d435a'};

// 'at' is where the source's own peak sample lands.
const report = buildSounds({srcDir: SRC, soundsDir: SOUNDS, sources: SOURCES, outputs: {
  start: {file: 'beverage_cooler/compressor_start.ogg', reference: 'distribution_panel/open.ogg', offset: -8,
    layers: [{src: 'cooler_compressor_start', at: 0.03, gain: 0}]},
  stop: {file: 'beverage_cooler/compressor_stop.ogg', reference: 'distribution_panel/open.ogg', offset: -10,
    layers: [{src: 'cooler_compressor_stop', at: 0.37, gain: 0, trim: 0.30}]},
}});
report.loop = buildLoop({srcDir: SRC, soundsDir: SOUNDS, source: LOOP, file: 'beverage_cooler/compressor_loop.ogg', from: 0.3496,
  period: 815 / 48000, periods: 29, crossfade: 4, reference: 'distribution_panel/open.ogg', offset: -14, declick: 2.5});
console.log(JSON.stringify(report, null, 1));
