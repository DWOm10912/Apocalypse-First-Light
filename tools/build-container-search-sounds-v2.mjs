// Progressive Container Search: the one shared search sound (every searchable container, no materials yet), a seamless
// loop of a soft steady rustle (user 2026-10-02: a pure rustle instead of V1's three rummaging clips with knocks in them,
// played as one loop). Mono 48 kHz Ogg Vorbis; the client loops it at the container while a search is running
// (client/ContainerSearchSoundController, started and kept alive by the server, AflContainerSearchState).
//   node tools/build-container-search-sounds-v2.mjs [sourceDir]     (default E:/Download; needs ffmpeg on PATH)
// Source (2 s stereo 48 kHz WAV, left/right correlation 0.95, checked by SHA-256 prefix): rustle. It fades in over its
// first 0.4 s and out over its last 0.25 s, so the loop takes the steady 1.3 s between (0.40-1.70 s), levelled over
// 50 ms periods with the gain smoothed over only one period either side (its level swings by about 20 dB, with one
// 50 ms drop near 1.25 s; a wider smoothing left that drop as a short gap twice a loop: 10 dB of swing remain), the
// few crackles in the highs turned down to 10 dB above their surroundings, then mirrored (forward + backward, 2.6 s):
// steady noise has no period to keep in phase, and a crossfade would dip. Loudness and loops: tools/sound-mix-lib.mjs buildLoop. It runs continuously next to
// the player, so it sits 10 LU under the industrial locker's door opening.
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {buildLoop} from './sound-mix-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const SRC = process.argv[2] ?? 'E:/Download';
const SOUNDS = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight/sounds');

console.log(JSON.stringify(buildLoop({srcDir: SRC, soundsDir: SOUNDS, source: {name: 'rustle', sha: 'c794eebeba06e379'},
  file: 'container_search/rustle_loop.ogg', from: 0.40, period: 0.05, periods: 26, crossfade: 0, smooth: 1,
  reference: 'industrial_locker/open.ogg', offset: -10, declick: 10, mirror: true}), null, 1));
