// Intake Pump sounds (docs/models/intake_pump_v1.md): the isolator switch, the motor's start, running loop and stop, from
// five user-generated source recordings (2026-10-05; 1-4 s stereo 48 kHz WAV).
//   node tools/build-intake-pump-sounds-v1.mjs [sourceDir]     (default E:/Download; needs ffmpeg on PATH)
// Sources (checked by SHA-256 prefix):
// - pump_switch: one rotary isolator detent click (peak at 0.133 s), clean decay; used whole;
// - pump_contactor: one contactor clack (peak at 0.025 s) followed by a -54 LUFS noise floor; cut 0.15 s after the click;
// - pump_motor_spinup: a knock in the first 0.1 s, then the motor winds up from -38 to -17 LUFS and holds; the knock is
//   cut (the contactor stands in for it), the last 0.6 s fade out so the loop can fade in under them (client:
//   BlockLoopSoundController's intake pump source, LOOP_FADE_IN);
// - pump_motor_loop: 4 s of steady motor, even within 2 dB, but most of its energy in a 4-8 kHz hiss (the user found it
//   "很吵"): low-passed at 6 kHz (2nd order) before the loop is cut, a 3 s equal-power crossfaded loop;
// - pump_water_settle: low gurgles and a short drain, most energy at 60-125 Hz (also "很吵"): high-passed at 90 Hz and
//   laid 8 LU under the motor in the stop.
// No water-flow layer (user, 2026-10-05: the motor is enough). The stop is the finished loop itself slowing down: played
// at a falling rate (speed 1 -> 0.32, time constant 0.5 s) and fading, so it carries on from the loop without a seam (the
// user's idea), with the water settling under it.
// Levels (loudness-normalised, BS.1770 momentary 400 ms, peaks at or below -1 dBFS; reference: the industrial
// electrical box door): the switch 3 LU below it; the motor (start at full speed, loop, the stop's start) 11 LU below.
import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {SR, PEAK_CEILING, TARGET_ELEMENT, decode, maxLoudness, peak, db, trimHead, trimTail, writeOgg, buildLoop} from './sound-mix-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const SRC = process.argv[2] ?? 'E:/Download';
const SOUNDS = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight/sounds');
const SHA = {pump_switch: '7038d7e261a66d83', pump_contactor: '0ee61c6b59f7192f', pump_motor_spinup: '56408c0a672b9d6a',
  pump_motor_loop: '30d1ae1209a72276', pump_water_settle: 'e2bd9d36eaa7a75d'};
const REFERENCE = 'industrial_electrical_box/open.ogg', SWITCH_OFFSET = -3, MOTOR_OFFSET = -11, SETTLE_UNDER = -8;
const LOWPASS = 6000, SETTLE_HIGHPASS = 90;
// start: the contactor's click at 0, the wind-up from START_SPINUP_AT; its last SPIN_FADE seconds fade out. The client's
// loop waits until the fade begins and fades in over it (IntakePumpBlockEntity.LOOP_FADE_IN, ticks).
const CONTACTOR_KEEP = 0.15, SPINUP_TRIM = 0.12, START_SPINUP_AT = 0.06, SPIN_FADE = 0.6;
// stop: speed(t) = FLOOR + (1 - FLOOR) exp(-t / TAU); level exp(-t / DECAY), the last FADE seconds to silence
const STOP = {seconds: 1.8, floor: 0.32, tau: 0.5, decay: 0.7, fade: 0.4, settleAt: 0.55};

// ---- small filters (RBJ biquads, 2nd order, Q 0.7071)
function biquad(x, b, a) { const y = new Float64Array(x.length); let x1 = 0, x2 = 0, y1 = 0, y2 = 0;
  for (let i = 0; i < x.length; i++) { const v = b[0] * x[i] + b[1] * x1 + b[2] * x2 - a[1] * y1 - a[2] * y2; x2 = x1; x1 = x[i]; y2 = y1; y1 = v; y[i] = v; } return y; }
function lowPass2(x, fc) { const w = 2 * Math.PI * fc / SR, c = Math.cos(w), al = Math.sin(w) / (2 * Math.SQRT1_2), a0 = 1 + al;
  return biquad(x, [(1 - c) / 2 / a0, (1 - c) / a0, (1 - c) / 2 / a0], [1, -2 * c / a0, (1 - al) / a0]); }
function highPass2(x, fc) { const w = 2 * Math.PI * fc / SR, c = Math.cos(w), al = Math.sin(w) / (2 * Math.SQRT1_2), a0 = 1 + al;
  return biquad(x, [(1 + c) / 2 / a0, -(1 + c) / a0, (1 + c) / 2 / a0], [1, -2 * c / a0, (1 - al) / a0]); }

// ---- sources
const src = {}, correlation = {};
for (const [name, sha] of Object.entries(SHA)) {
  const file = path.join(SRC, name + '.wav'), h = createHash('sha256').update(fs.readFileSync(file)).digest('hex').slice(0, 16);
  if (h !== sha) throw new Error(`${name}.wav changed (sha ${h}, expected ${sha})`);
  src[name] = decode(file);
}
const ref = maxLoudness(decode(path.join(SOUNDS, REFERENCE)), 0.4);
const scaleTo = (x, target) => { let g = target - maxLoudness(x, 0.4); const pk = 20 * Math.log10(peak(x)) + g; if (pk > PEAK_CEILING) g -= pk - PEAK_CEILING; return x.map(v => v * db(g)); };
const element = (x, gainDb = 0) => x.map(v => v * db(TARGET_ELEMENT - maxLoudness(x, 0.1) + gainDb));
const fadeOutTail = (x, seconds) => { const y = Float64Array.from(x), F = Math.round(seconds * SR); for (let i = 0; i < F; i++) y[y.length - 1 - i] *= i / F; return y; };
const report = {referenceLUFS: +ref.toFixed(1)};
const write = (key, x, file) => {
  writeOgg(x, path.join(SOUNDS, file));
  const enc = decode(path.join(SOUNDS, file));
  report[key] = {file, seconds: +(enc.length / SR).toFixed(3), maxMomentaryLUFS: +maxLoudness(enc, 0.4).toFixed(1), peakDbfs: +(20 * Math.log10(peak(enc))).toFixed(1)};
};

// ---- switch
write('switch', scaleTo(trimTail(trimHead(src.pump_switch)), ref + SWITCH_OFFSET), 'intake_pump/switch.ogg');

// ---- loop (written first: the stop is made from it)
report.loop = buildLoop({srcDir: SRC, soundsDir: SOUNDS, source: {name: 'pump_motor_loop', sha: SHA.pump_motor_loop}, file: 'intake_pump/loop.ogg',
  from: 0.3, period: 0.05, periods: 60, crossfade: 6, equalPower: true, reference: REFERENCE, offset: MOTOR_OFFSET, declick: 6,
  prepare: x => lowPass2(x, LOWPASS)});
const loop = decode(path.join(SOUNDS, 'intake_pump/loop.ogg'));

// ---- start: contactor + wind-up, matched to the loop at full speed
{
  let clack = trimHead(src.pump_contactor).slice(0, Math.round(CONTACTOR_KEEP * SR));
  clack = element(fadeOutTail(clack, 0.04));
  let spin = lowPass2(src.pump_motor_spinup, LOWPASS).slice(Math.round(SPINUP_TRIM * SR));
  for (let i = 0; i < Math.round(0.02 * SR); i++) spin[i] *= i / (0.02 * SR);   // no click where the knock was cut
  spin = element(fadeOutTail(spin, SPIN_FADE));
  const at = Math.round(START_SPINUP_AT * SR), mix = new Float64Array(at + spin.length);
  clack.forEach((v, i) => { mix[i] += v; });
  spin.forEach((v, i) => { mix[at + i] += v; });
  // the wind-up's full-speed stretch (before its fade) at the loop's level
  const full = spin.slice(spin.length - Math.round((SPIN_FADE + 0.4) * SR), spin.length - Math.round(SPIN_FADE * SR));
  let g = maxLoudness(loop, 0.4) - maxLoudness(full, 0.4);
  const pk = 20 * Math.log10(peak(mix)) + g; if (pk > PEAK_CEILING) g -= pk - PEAK_CEILING;
  write('start', mix.map(v => v * db(g)), 'intake_pump/start.ogg');
  report.start.loopFadeInFrom = +(START_SPINUP_AT + spin.length / SR - SPIN_FADE).toFixed(3);
  report.start.loopFadeInSeconds = SPIN_FADE;
}

// ---- stop: the loop slowing down and fading, the water settling under it
{
  const N = Math.round(STOP.seconds * SR), motor = new Float64Array(N);
  let p = 0;
  for (let n = 0; n < N; n++) {
    const t = n / SR, speed = STOP.floor + (1 - STOP.floor) * Math.exp(-t / STOP.tau);
    const j = Math.floor(p), f = p - j, a = loop[j % loop.length], b = loop[(j + 1) % loop.length];
    const tail = t > STOP.seconds - STOP.fade ? (STOP.seconds - t) / STOP.fade : 1;
    motor[n] = (a * (1 - f) + b * f) * Math.exp(-t / STOP.decay) * tail;
    p += speed;
  }
  let settle = trimTail(trimHead(highPass2(src.pump_water_settle, SETTLE_HIGHPASS)));
  settle = settle.map(v => v * db(maxLoudness(loop, 0.4) + SETTLE_UNDER - maxLoudness(settle, 0.4)));
  const at = Math.round(STOP.settleAt * SR), mix = new Float64Array(Math.max(N, at + settle.length));
  motor.forEach((v, i) => { mix[i] += v; });
  settle.forEach((v, i) => { mix[at + i] += v; });
  const pk = 20 * Math.log10(peak(mix));
  write('stop', trimTail(pk > PEAK_CEILING ? mix.map(v => v * db(PEAK_CEILING - pk)) : mix), 'intake_pump/stop.ogg');
}
console.log(JSON.stringify(report, null, 1));
