// Checkout Counter V1 gate sounds: open and shut, each cut from one user-generated source recording into its events,
// loudness-normalised and placed on the gate's animation (mono 48 kHz Ogg Vorbis).
//   node tools/build-checkout-counter-gate-sounds-v1.mjs [sourceDir]     (default E:/Download; needs ffmpeg on PATH)
// Sources (1.5 s stereo 48 kHz WAV, left/right correlation 0.989 / 0.992, checked by SHA-256 prefix):
//   开门 (open): a knock as the flap comes off at 0.06 s, the hinge / swing creak 0.44..0.95 s, the flap landing (the loudest
//                hit) at 1.08 s;
//   关门 (shut): the door clapping against its stop at 0.06 s, the flap coming down at 0.78 s.
// Animation (tools/build-checkout-counter-v1.mjs GATE_ANIMATION, profiles checkout_counter_gate_left / _right): both
// channels start with the click; the door swings 90 degrees in 8 ticks (0.4 s, ease_out), the flap turns 180 degrees in
// 16 ticks (0.8 s, ease_in_out). So:
//   open : the knock at 0.08 s, the creak peaking at 0.30 s while the door swings away, the landing at 0.80 s when the
//          flap lies on the neighbouring counter;
//   shut : the clap at 0.40 s when the door reaches its stop, the flap's thud at 0.80 s.
// Every event keeps the source's own level (one gain per source). Reference: the industrial locker door; a laminate
// counter gate is lighter, 3 LU under it. Loudness and mixing: tools/sound-mix-lib.mjs.
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {buildSounds} from './sound-mix-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const SRC = process.argv[2] ?? 'E:/Download';
const SOUNDS = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight/sounds');
const SOURCES = {'开门': '8a819ec1eb3bfe96', '关门': '1e4cc550c0a189c3'};

console.log(JSON.stringify(buildSounds({srcDir: SRC, soundsDir: SOUNDS, sources: SOURCES, outputs: {
  open: {file: 'checkout_counter_gate/open.ogg', reference: 'industrial_locker/open.ogg', offset: -3, layers: [
    {src: '开门', fadeFrom: 0.30, fadeTo: 0.40, at: 0.08, gain: 0},                  // the flap comes off
    {src: '开门', trim: 0.40, fadeFrom: 0.50, fadeTo: 0.60, at: 0.30, gain: 0},     // the door swings away
    {src: '开门', trim: 1.00, at: 0.80, gain: 0},                                   // the flap lands on the neighbour
  ]},
  close: {file: 'checkout_counter_gate/close.ogg', reference: 'industrial_locker/close.ogg', offset: -3, layers: [
    {src: '关门', fadeFrom: 0.45, fadeTo: 0.60, at: 0.40, gain: 0},                  // the door claps against its stop
    {src: '关门', trim: 0.60, at: 0.80, gain: 0},                                   // the flap comes down onto the frame
  ]},
}}), null, 1));
