// Breaker trip sound (docs/models/building_power_v1.md, docs/machines/portable_diesel_generator_v1.md), from one
// user-generated source recording (2026-10-10, E:/Download, Firefly, 0.52 s stereo 48 kHz Ogg Vorbis, left/right
// correlation 0.92, checked by SHA-256 prefix): one trip, a short arc crackle and the toggle's snap (its loudest sample at
// 0.045 s), a panel rattle, quiet by 0.25 s. Used whole, at its own timing (the trip is instant), as breaker/trip.ogg:
// the distribution panel's main breaker and the portable generator's breakers. The user: one sound, no random variants.
// Level: the distribution panel's door opening (BS.1770 momentary 400 ms, peak at or below -1 dBFS).
//   node tools/build-breaker-trip-sound-v1.mjs [sourceDir]     (default E:/Download; needs ffmpeg on PATH)
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {SR, decode, trimHead, peakAt, buildSounds} from './sound-mix-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const SRC = process.argv[2] ?? 'E:/Download';
const SOUNDS = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight/sounds');
const TRIP = 'Firefly_audio_Close-up_recordings_of_an_industrial_circuit_break_variation1.ogg';

const at = peakAt(trimHead(decode(path.join(SRC, TRIP)))) / SR;   // its own timing once the lead-in silence is cut
console.log(JSON.stringify(buildSounds({srcDir: SRC, soundsDir: SOUNDS, sources: {[TRIP]: '99090153077f0276'}, outputs: {
  trip: {file: 'breaker/trip.ogg', reference: 'distribution_panel/open.ogg', offset: 0, layers: [{src: TRIP, at, gain: 0}]},
}}), null, 1));
