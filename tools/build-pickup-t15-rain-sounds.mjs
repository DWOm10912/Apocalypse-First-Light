// T15 rain sounds (docs/vehicles/first_pickup_v0_design.md 8.17 "雨天的声音"): the wipers, the tyres on wet asphalt and
// the rain heard in the cab.
//   wiper_up_1..4 / wiper_down_1..4.ogg   half-strokes of a real wiper recording the user found on YouTube (2026-10-18,
//                    "CAR WIPER SOUND EFFECT IN HIGH QUALITY", n Beats 2014, mp3 48 kHz stereo, decoded to mono; licence not
//                    stated, see the doc). The take runs at 1.43 s a cycle (low speed is WiperSystem.LOW_STROKE 1.333 s), so
//                    it is cut, not stretched: each cycle has a loud clunk (about -16 dB) and a soft one (about -27 dB) 0.6 s
//                    later, the floor between them 30 dB down. UP cuts start just before a loud clunk and run to the next soft
//                    one, DOWN cuts from a soft clunk to the next loud one; TruckSounds plays an up cut as a stroke leaves the
//                    bottom and a down cut at the top. The earlier Firefly wiper take (a 150-450 Hz drone under every sweep;
//                    high-passed and time-stretched it read as "电流噪音") is retired.
//   tyre_wet.ogg     a loop of the wet-asphalt take (Adobe Firefly, 2026-10-18, 48 kHz stereo WAV, decoded to mono; steady from
//                    the start), levelled like the dry tyre loops (same role).
//   cab_rain.ogg     a loop of the in-cab rain take (Firefly as above; steady; drops on the roof and the glass), for riders in
//                    the cab, where vanilla's rain sounds are muted.
// Levels as tools/build-pickup-t15-sounds.mjs: the 400 ms maximum loudness against distribution_panel/open plus a role
// offset; peaks at or below -1 dBFS. A take missing from sourceDir is skipped and its committed .ogg kept.
//   node tools/build-pickup-t15-rain-sounds.mjs [sourceDir]     (default E:/Download; needs ffmpeg on PATH)
import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {SR, PEAK_CEILING, decode, maxLoudness, peak, db, writeOgg, buildLoop, highPass} from './sound-mix-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const SRC = process.argv[2] ?? 'E:/Download';
const SOUNDS = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight/sounds');
const OUT = 'pickup_t15';
const REFERENCE = 'distribution_panel/open.ogg';
const TAKES = {
  wiper: {name: 'CAR WIPER SOUND EFFECT IN HIGH QUALITY', ext: '.mp3', sha: '4fdc74c8b60eea07'},
  wet: {name: 'Firefly_audio_Tires_rolling_on_wet_asphalt_at_a_steady_highway_s_variation4', ext: '.wav', sha: 'a4e31edfff8722f2'},
  cab: {name: 'Firefly_audio_Steady_rain_heard_from_inside_a_parked_pickup_truc_variation2', ext: '.wav', sha: '8539f89ede7c7572'},
};
// role offsets (LU against the reference; build-pickup-t15-sounds.mjs: tyres -2.5, wind -3.5, gear lever -6)
const ROLE = {wiper: -7, tyre: -2.5, cabRain: -4};
const WIPER_HP = 30, FADE_IN = 0.04, FADE_OUT = 0.1;
// half-strokes in the take (s), from 60 ms before a clunk's rise; the take's first and last cycles (clicks, fade-out) left
// out. The take's up stroke is 0.60 s and its down stroke 0.83 s, the game's are both LOW_STROKE / 2 (0.667 s): the up cuts
// run 0.10 s on into the next down cut's start so the motor floor does not drop out between them; the fades overlap.
const UP = [[4.73, 5.43], [6.15, 6.89], [7.62, 8.32], [9.03, 9.78]];
const DOWN = [[3.90, 4.73], [5.33, 6.15], [6.79, 7.62], [8.22, 9.03]];

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

// wipers: one gain for all eight (matched on the loudest), so the up and down cuts keep the take's balance
if (have.wiper) {
  const x = highPass(decode(path.join(SRC, TAKES.wiper.name + TAKES.wiper.ext)), WIPER_HP);
  const cut = ([a, b]) => {
    const y = x.slice(Math.round(a * SR), Math.round(b * SR));
    const fi = Math.round(FADE_IN * SR), fo = Math.round(FADE_OUT * SR);
    for (let i = 0; i < fi && i < y.length; i++) y[i] *= i / fi;
    for (let i = 0; i < fo && i < y.length; i++) y[y.length - 1 - i] *= 0.5 - 0.5 * Math.cos(Math.PI * i / fo);
    return y;
  };
  const cuts = [...UP.map((c, i) => ['wiper_up_' + (i + 1), cut(c)]), ...DOWN.map((c, i) => ['wiper_down_' + (i + 1), cut(c)])];
  const loudest = Math.max(...cuts.map(([, y]) => maxLoudness(y, 0.4)));
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
