// T15 pickup sounds V1 (docs/vehicles/first_pickup_v0_design.md 8.11): 22 user-generated Firefly takes (2026-10-16, 48 kHz
// stereo WAV in E:/Download, the user's Chinese names) cut into loops and one-shots under sounds/pickup_t15/. Each take is
// decoded to mono (the engine takes are near-mono: stereo correlation 0.99+). The user: many takes need loudness
// normalising, some are loud and some can hardly be heard - so every output is levelled here, none keeps its take's level:
//   - one-shots: their maximum momentary loudness (BS.1770, 400 ms) to the reference (distribution_panel/open, as the
//     other AFL machine sounds) plus a role offset (ROLE below), sample peak at or below -1 dBFS;
//   - loops (buildLoop): levelled over the window (a pass-by's swell flattened), whole periods, equal-power crossfade,
//     the same 400 ms maximum to the reference plus the offset;
//   - the engine's catch and stop come out of the same take as the idle loop and take the idle loop's gain (matched on
//     the window's mean level), so the flare and the run-down keep their size against the idle.
// Take timings (measured 2026-10-16, scratchpad snd_window / snd_spec: 40 ms K-weighted loudness, the firing rate from
// the envelope spectrum; rpm = firing rate x 30 for a four-cylinder four-stroke):
//   起动、着车、怠速、熄火 (start, 30 s): 0.80 s glow-plug relay click; 1.6 s the starter; 2.7-2.9 s it fires (flare to
//     -10 LUFS), settled by 3.1 s; idle 25-26 Hz = about 770 rpm (the spec's idle is 760) to 29.1 s, slowly 3 LU quieter;
//     29.2 s fuel cut, quiet by 29.7 s (the stop: from 28.9 s, its first 0.25 s still running).
//   转速循环 (revs, 30 s): idle about 840 rpm to 7 s, a run-up 8-14 s, steady at about 1830 rpm (61 Hz) 15.5-23.5 s, idle
//     again from 26 s (no steady high speed in the take: the mid loop is pitched up to 2x for the top end).
//   带负荷 (pulling, 20 s) and 松油门滑行 (coasting, 15 s): drive-bys (the truck passes the microphone): loudest 10-14 s and
//     3.5-8.5 s, used as layers; levelled.
//   打不着 (no start, 10 s): three cranks 0-1.8, 2.6-3.8, 5.2-6.9 s; the crank loop from the first (compressions at about
//     10 Hz: a 0.1 s period).
//   tyres (10 s each): asphalt, loose gravel, dry dirt track steady after 2-4 s; grass rises 15 LU over the take (levelled).
//   车里的风噪 (cab wind, 20 s): steady 1-19 s.
//   one-shots: see ONESHOTS (each [from, to] s in its take; fade-in 5 ms unless a cut lands on silence, fade-out FADE).
//   2H 4H 拨杆、手刹 is three takes joined by digital silence (1.6 and 3.9 s): the transfer lever, the handbrake pulled
//     up (a click and its ratchet), released.
//   node tools/build-pickup-t15-sounds.mjs [sourceDir]     (default E:/Download; needs ffmpeg on PATH)
import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {SR, PEAK_CEILING, decode, maxLoudness, kweight, peak, db, writeOgg, buildLoop, highPass} from './sound-mix-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const SRC = process.argv[2] ?? 'E:/Download';
const SOUNDS = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight/sounds');
const OUT = 'pickup_t15';
const REFERENCE = 'distribution_panel/open.ogg';   // -24.6 LUFS (400 ms maximum)
const TAKES = {
  start: {name: '起动、着车、怠速、熄火', sha: 'fd04bc253349314b'},
  revs: {name: '转速循环', sha: '33a9fe33d3fdc0ac'},
  load: {name: '带负荷', sha: '8568da8d8938df2b'},
  coast: {name: '松油门滑行', sha: '40ae3e2db563ed1a'},
  nostart: {name: '打不着', sha: '30484d9d9a0fbdf6'},
  asphalt: {name: 'asphalt', sha: '8110d44895688b13'},
  gravel: {name: 'loose gravel', sha: '68151deee650e2e7'},
  dirt: {name: 'dry dirt track', sha: '4c340b991112c791'},
  grass: {name: 'grass', sha: 'ffaacfdbecf40c96'},
  wind: {name: '车里的风噪', sha: '33920fade1b91551'},
  skid: {name: '急刹、打滑', sha: '67f783f720af6837'},
  bottom: {name: '悬挂压到底', sha: 'bf6ad185ea91e261'},
  crashLight: {name: '碰撞（轻）', sha: 'f43aeecd0e223d05'},
  crashHeavy: {name: '碰撞（重）', sha: '0c439b1ee11ac7ff'},
  scrape: {name: '刮蹭', sha: 'b028a4701e1965c8'},
  door: {name: '车门', sha: '79db9a759fad2223'},
  tailgate: {name: '尾门', sha: '0d2970bcf8e47fb1'},
  cap: {name: '油箱盖', sha: '98eed1737cf6f3ce'},
  key: {name: '钥匙', sha: 'c3455e0d5156a2a6'},
  shift: {name: '换挡', sha: '38c8dac56d4e10e7'},
  levers: {name: '2H  4H 拨杆、手刹', sha: 'e512a46b5de32215'},
  horn: {name: '喇叭', sha: 'a6fdd045ff66eb41'},
};
// role offsets (LU against the reference): the engine as loud as the portable generator's run (-2.2) at idle, louder
// revving; tyres and wind under the engine; small mechanisms well under the doors; the horn and a heavy crash on top
const ROLE = {
  idle: -1.5, mid: 2.5, load: 2.5, coast: -0.5, crank: -0.5, glow: -9,
  tyre: -2.5, wind: -3.5,
  skid: 2.5, bottom: 0, crashLight: 2.5, crashHeavy: 7, scrape: 2,
  doorOpen: -3, doorClose: 1, tailgate: 1.5, capOpen: -6.5, capClose: -6.5, key: -8, shift: -6, transfer: -4, handbrakePull: -5, handbrakeRelease: -6,
  horn: 6,
};
const FADE = 0.04;
// one-shots: [take, from, to, role, fade-out (s)]; the engine's catch and stop take the idle loop's gain instead
const ONESHOTS = {
  glow_click: ['start', 0.74, 1.10, 'glow', 0.05],
  bottom_out: ['bottom', 1.02, 1.90, 'bottom', 0.25],
  skid: ['skid', 0.42, 2.10, 'skid', 0.35],
  crash_light_1: ['crashLight', 1.04, 1.80, 'crashLight', 0.25],
  crash_light_2: ['crashLight', 2.36, 3.40, 'crashLight', 0.30],
  crash_light_3: ['crashLight', 6.06, 7.10, 'crashLight', 0.30],
  crash_heavy: ['crashHeavy', 0.0, 2.70, 'crashHeavy', 0.50],
  scrape: ['scrape', 0.30, 2.90, 'scrape', 0.30],
  door_open: ['door', 0.30, 0.90, 'doorOpen', 0.10],
  door_close: ['door', 3.58, 4.70, 'doorClose', 0.30],
  tailgate: ['tailgate', 0.66, 1.50, 'tailgate', 0.20],
  fuel_cap_open: ['cap', 0.0, 0.62, 'capOpen', 0.06],
  fuel_cap_close: ['cap', 0.94, 2.0, 'capClose', 0.10],
  key: ['key', 2.02, 2.42, 'key', 0.06],
  shift_1: ['shift', 0.0, 0.62, 'shift', 0.12],
  shift_2: ['shift', 2.02, 2.70, 'shift', 0.12],
  shift_3: ['shift', 3.98, 4.62, 'shift', 0.12],
  shift_4: ['shift', 5.56, 6.20, 'shift', 0.12],
  shift_5: ['shift', 8.46, 9.10, 'shift', 0.12],
  shift_6: ['shift', 10.46, 11.06, 'shift', 0.12],
  transfer: ['levers', 0.24, 1.00, 'transfer', 0.10],
  handbrake_pull: ['levers', 1.82, 3.30, 'handbrakePull', 0.15],
  handbrake_release: ['levers', 4.60, 5.60, 'handbrakeRelease', 0.15],
};
// engine catch (from the starter's last turns to the settled idle, fading under the idle loop's fade-in) and stop
const CATCH = {from: 2.52, to: 4.40, fade: 1.0}, STOP = {from: 28.90, to: 30.0, fade: 0.25, running: 0.25};
// loops: take, window start, period (s), periods, crossfade periods, smoothing (periods), role
const LOOPS = {
  idle_loop: ['start', 8.0, 0.05, 160, 10, 20, 'idle'],
  mid_loop: ['revs', 16.0, 0.05, 120, 10, 20, 'mid'],
  load_loop: ['load', 10.0, 0.05, 70, 10, 20, 'load'],
  coast_loop: ['coast', 4.0, 0.05, 80, 10, 20, 'coast'],
  crank_loop: ['nostart', 0.10, 0.1, 9, 2, 3, 'crank'],
  tyre_asphalt: ['asphalt', 4.0, 0.05, 100, 10, 20, 'tyre'],
  tyre_gravel: ['gravel', 3.5, 0.05, 100, 10, 20, 'tyre'],
  tyre_dirt: ['dirt', 2.0, 0.05, 100, 10, 20, 'tyre'],
  tyre_grass: ['grass', 4.0, 0.05, 100, 10, 20, 'tyre'],
  wind_loop: ['wind', 2.0, 0.05, 240, 20, 30, 'wind'],
};
// the engine takes carry a lot of rumble under 30 Hz (the start take's spectral centroid is near 25 Hz): high-passed first
const RUMBLE = {start: 30, revs: 30, load: 30, coast: 30, nostart: 30, bottom: 25, crashHeavy: 25, crashLight: 25, door: 40, tailgate: 40};

const decoded = {};
for (const [k, t] of Object.entries(TAKES)) {
  const file = path.join(SRC, t.name + '.wav');
  const h = createHash('sha256').update(fs.readFileSync(file)).digest('hex').slice(0, 16);
  if (h !== t.sha) throw new Error(`${t.name}.wav changed (sha ${h}, expected ${t.sha})`);
  decoded[k] = RUMBLE[k] ? highPass(decode(file), RUMBLE[k]) : decode(file);
}
const refLufs = maxLoudness(decode(path.join(SOUNDS, REFERENCE)), 0.4);
const meanLufs = y => { const k = kweight(y); let e = 0; for (const v of k) e += v * v; return -0.691 + 10 * Math.log10(e / k.length + 1e-20); };
const cut = (x, a, b) => x.slice(Math.round(a * SR), Math.min(x.length, Math.round(b * SR)));
const fadeOut = (y, s) => { const F = Math.round(s * SR); for (let i = 0; i < F && i < y.length; i++) y[y.length - 1 - i] *= 0.5 - 0.5 * Math.cos(Math.PI * i / F); return y; };
const fadeIn = (y, s) => { const F = Math.round(s * SR); for (let i = 0; i < F && i < y.length; i++) y[i] *= i / F; return y; };
const report = {referenceLUFS: +refLufs.toFixed(1)};
function write(name, y, gainDb) {
  const g = Math.min(db(gainDb), db(PEAK_CEILING) / peak(y)), out = y.map(v => v * g);
  writeOgg(out, path.join(SOUNDS, OUT, name + '.ogg'));
  const enc = decode(path.join(SOUNDS, OUT, name + '.ogg'));
  report[name] = {seconds: +(enc.length / SR).toFixed(3), lufs400: +maxLoudness(enc, 0.4).toFixed(1), peakDb: +(20 * Math.log10(peak(enc))).toFixed(1),
    peakLimited: g < db(gainDb)};
}

// loops: the prepared (high-passed) take, so the loop is cut from what the one-shots are cut from
for (const [name, [take, from, period, periods, crossfade, smooth, role]] of Object.entries(LOOPS)) {
  report[name] = buildLoop({srcDir: SRC, soundsDir: SOUNDS, source: TAKES[take], file: `${OUT}/${name}.ogg`, from, period, periods, crossfade, smooth,
    reference: REFERENCE, offset: ROLE[role], equalPower: true, prepare: RUMBLE[take] ? x => highPass(x, RUMBLE[take]) : undefined});
}
// the horn: a held tone, so its loop is whole periods of its own pitch (the raw signal's autocorrelation, 1.5-10 ms)
{
  const x = cut(decoded.horn, 0.9, 1.5);
  let best = -1, lag = 0;
  for (let l = Math.round(0.0015 * SR); l <= Math.round(0.010 * SR); l++) {
    let r = 0, a = 0, b = 0; for (let i = 0; i + l < x.length; i++) { r += x[i] * x[i + l]; a += x[i] * x[i]; b += x[i + l] * x[i + l]; }
    r /= Math.sqrt(a * b) + 1e-20; if (r > best) { best = r; lag = l; }
  }
  const per = lag / SR, n = Math.max(1, Math.round(0.02 / per));
  report.hornPitch = {hz: +(1 / per).toFixed(1), correlation: +best.toFixed(3)};
  report.horn_loop = buildLoop({srcDir: SRC, soundsDir: SOUNDS, source: TAKES.horn, file: `${OUT}/horn_loop.ogg`, from: 0.80, period: per * n,
    periods: Math.floor(0.62 / (per * n)), crossfade: Math.max(2, Math.round(0.08 / (per * n))), smooth: 4, reference: REFERENCE, offset: ROLE.horn});
}
// catch and stop: the idle loop's gain
const window = cut(decoded.start, LOOPS.idle_loop[1], LOOPS.idle_loop[1] + LOOPS.idle_loop[2] * LOOPS.idle_loop[3]);
const idleGain = meanLufs(decode(path.join(SOUNDS, OUT, 'idle_loop.ogg'))) - meanLufs(window);
report.idleGainDb = +idleGain.toFixed(2);
write('catch', fadeOut(fadeIn(cut(decoded.start, CATCH.from, CATCH.to), 0.01), CATCH.fade), idleGain);
// the take's idle has faded about 3 LU by its end (and the cut lands in a quieter stretch): the stop's running start is
// matched to the idle loop instead, so the run-down follows the loop without a step
const stopGain = meanLufs(decode(path.join(SOUNDS, OUT, 'idle_loop.ogg'))) - meanLufs(cut(decoded.start, STOP.from, STOP.from + STOP.running));
report.stopGainDb = +stopGain.toFixed(2);
write('stop', fadeOut(fadeIn(cut(decoded.start, STOP.from, STOP.to), 0.015), STOP.fade), stopGain);
// one-shots, each to its role's level
for (const [name, [take, a, b, role, f]] of Object.entries(ONESHOTS)) {
  const y = fadeOut(fadeIn(cut(decoded[take], a, b), 0.005), f ?? FADE);
  write(name, y, refLufs + ROLE[role] - maxLoudness(y, 0.4));
}
console.log(JSON.stringify(report, null, 1));
