// T15 rain sounds (docs/vehicles/first_pickup_v0_design.md 8.17 "雨天的声音"): the wipers, the tyres on wet asphalt and
// the rain heard in the cab.
//   wiper_up_1..4 / wiper_down_1..4.ogg   single sweeps from an Adobe Firefly take (2026-10-18, second prompt: a rubber wiper
//                    blade swept by hand over wet glass, no motor in the words; 15 s, 48 kHz stereo WAV, decoded to mono):
//                    19 short swishes 0.08-0.16 s long, no drone, but a low thump (< 120 Hz) as loud as the swish under each
//                    one and a faint broadband floor between them (-54 dBFS). Cleaned with a 150 Hz high-pass (twice) and an
//                    FFT denoiser tracking that floor (ffmpeg afftdn nr 18, nf -60; floor then -78 dBFS). UP cuts are firm
//                    sweeps, DOWN cuts light ones a further 10 dB down (as the real recording before; user PASS on the
//                    preview build/sound-preview/t15_rain/wiper_ff2_clean_slow / _fast, 2026-10-18). TruckSounds plays an up
//                    cut as a stroke leaves the bottom and a down cut at the top. Retired: the YouTube recording ("CAR WIPER
//                    SOUND EFFECT IN HIGH QUALITY", licence not stated) and the first Firefly take (a 150-450 Hz motor drone).
//   tyre_wet.ogg     a loop of the wet-asphalt take (Adobe Firefly, 2026-10-18, 48 kHz stereo WAV, decoded to mono; steady from
//                    the start), levelled like the dry tyre loops (same role).
//   cab_rain.ogg     a loop of the in-cab rain take (Firefly as above; steady; drops on the roof and the glass), for riders in
//                    the cab, where vanilla's rain sounds are muted.
// Levels as tools/build-pickup-t15-sounds.mjs: the 400 ms maximum loudness against distribution_panel/open plus a role
// offset; peaks at or below -1 dBFS. A take missing from sourceDir is skipped and its committed .ogg kept.
//   node tools/build-pickup-t15-rain-sounds.mjs [sourceDir]     (default E:/Download; needs ffmpeg on PATH)
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import {execFileSync} from 'node:child_process';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {SR, PEAK_CEILING, decode, maxLoudness, peak, db, writeOgg, buildLoop} from './sound-mix-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const SRC = process.argv[2] ?? 'E:/Download';
const SOUNDS = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight/sounds');
const OUT = 'pickup_t15';
const REFERENCE = 'distribution_panel/open.ogg';
const TAKES = {
  wiper: {name: 'Firefly_audio_Foley_artist_holding_a_detached_rubber_windshield__variation2', ext: '.wav', sha: '377ff67b8a132ebb'},
  wet: {name: 'Firefly_audio_Tires_rolling_on_wet_asphalt_at_a_steady_highway_s_variation4', ext: '.wav', sha: 'a4e31edfff8722f2'},
  cab: {name: 'Firefly_audio_Steady_rain_heard_from_inside_a_parked_pickup_truc_variation2', ext: '.wav', sha: '8539f89ede7c7572'},
};
// role offsets (LU against the reference; build-pickup-t15-sounds.mjs: tyres -2.5, wind -3.5, gear lever -6)
const ROLE = {wiper: -7, tyre: -2.5, cabRain: -4};
const WIPER_CLEAN = 'highpass=f=150,highpass=f=150,afftdn=nr=18:nf=-60:tn=1';
// sweep starts in the take (s; 30 ms before each swish), each cut 0.45 s with a 10 ms fade-in and a 0.15 s fade-out
const UP = [1.28, 2.07, 4.95, 8.41], DOWN = [6.54, 10.79, 12.99, 14.65];
const PRE = 0.03, LEN = 0.45, FADE_IN = 0.01, FADE_OUT = 0.15, DOWN_DB = -10;

const sha = file => createHash('sha256').update(fs.readFileSync(file)).digest('hex').slice(0, 16);
const have = {};
for (const [k, t] of Object.entries(TAKES)) {
  const file = path.join(SRC, t.name + t.ext);
  if (!(have[k] = fs.existsSync(file))) { console.log(`${t.name}${t.ext} not in ${SRC}: kept the committed sound`); continue; }
  const h = sha(file);
  if (h !== t.sha) throw new Error(`${t.name}${t.ext} changed (sha ${h}, expected ${t.sha})`);
}
const refLufs = maxLoudness(decode(path.join(SOUNDS, REFERENCE)), 0.4);
const report = {referenceLUFS: +refLufs.toFixed(1)};

// wipers: one gain for all eight (matched on the loudest up cut), the down cuts DOWN_DB lower
if (have.wiper) {
  const cleaned = path.join(os.tmpdir(), 'afl_wiper_clean.wav');
  execFileSync('ffmpeg', ['-v', 'error', '-y', '-i', path.join(SRC, TAKES.wiper.name + TAKES.wiper.ext), '-af', WIPER_CLEAN, cleaned]);
  const x = decode(cleaned);
  const cut = t => {
    const a = Math.round((t - PRE) * SR), n = Math.round(LEN * SR), y = new Float64Array(n);
    const fi = FADE_IN * SR, fo = FADE_OUT * SR;
    for (let i = 0; i < n; i++) y[i] = (x[a + i] || 0) * Math.min(1, i / fi) * Math.min(1, (n - i) / fo);
    return y;
  };
  const ups = UP.map(cut), downs = DOWN.map(t => cut(t).map(v => v * db(DOWN_DB)));
  const cuts = [...ups.map((y, i) => ['wiper_up_' + (i + 1), y]), ...downs.map((y, i) => ['wiper_down_' + (i + 1), y])];
  const loudest = Math.max(...ups.map(y => maxLoudness(y, 0.4)));
  let gain = refLufs + ROLE.wiper - loudest;
  const pk = Math.max(...cuts.map(([, y]) => 20 * Math.log10(peak(y)))) + gain;
  if (pk > PEAK_CEILING) gain -= pk - PEAK_CEILING;
  for (const [name, y] of cuts) {
    const file = path.join(SOUNDS, OUT, name + '.ogg');
    writeOgg(Array.from(y, v => v * db(gain)), file);
    const enc = decode(file);
    report[name] = {seconds: +(enc.length / SR).toFixed(3), lufs400: +maxLoudness(enc, 0.4).toFixed(1), peakDb: +(20 * Math.log10(peak(enc))).toFixed(1)};
  }
}
if (have.wet) report.tyre_wet = buildLoop({srcDir: SRC, soundsDir: SOUNDS, source: TAKES.wet, file: `${OUT}/tyre_wet.ogg`, from: 1.0, period: 0.05,
  periods: 160, crossfade: 10, smooth: 20, reference: REFERENCE, offset: ROLE.tyre, equalPower: true});
if (have.cab) report.cab_rain = buildLoop({srcDir: SRC, soundsDir: SOUNDS, source: TAKES.cab, file: `${OUT}/cab_rain.ogg`, from: 1.0, period: 0.05,
  periods: 240, crossfade: 20, smooth: 30, reference: REFERENCE, offset: ROLE.cabRain, equalPower: true});
console.log(JSON.stringify(report, null, 1));
