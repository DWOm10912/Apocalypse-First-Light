// Portable diesel generator V1: first-person recoil QTE check (client/PortableGeneratorPull, docs/machines/portable_diesel_generator_v1.md
// "拉绳"). Replays the client's formulas frame by frame (quarter ticks) for a player standing 0.7 .. 1.6 m from the starter on
// its side, looking at it: the view easing into the fixed bent view (BLEND_IN), a pull while held (the handle out, ease out over
// 5 ticks from YANK_AT, guided back from RELEASE_AT, the view's small lift), holding, and easing back out (BLEND_OUT). Checks:
//   - the arm's far (cut) end never enters the 70 degree view (16:9);
//   - while held the hand is on screen;
//   - the arm's axis stays at least 0.3 m from the camera (no near-plane clipping, it is 0.155 m thick);
//   - the view turns at most 8 degrees a tick (easing in from wherever the player looked).
// The numbers mirror PortableDieselGeneratorBlock (HANDLE_REST, PULL), PortableDieselGeneratorBlockEntity (YANK_AT, RELEASE_AT)
// and PortableGeneratorPull (BLEND_*, BEND_*, the hand's start, the virtual shoulder, NativePlayerArmRenderer's 0.78 length).
//   node tools/check-portable-generator-pull-v1.mjs
const v = (x, y, z) => ({x, y, z}), add = (a, b) => v(a.x + b.x, a.y + b.y, a.z + b.z), sub = (a, b) => v(a.x - b.x, a.y - b.y, a.z - b.z);
const mul = (a, k) => v(a.x * k, a.y * k, a.z * k), dot = (a, b) => a.x * b.x + a.y * b.y + a.z * b.z, len = a => Math.hypot(a.x, a.y, a.z), nrm = a => mul(a, 1 / len(a));
const cross = (a, b) => v(a.y * b.z - a.z * b.y, a.z * b.x - a.x * b.z, a.x * b.y - a.y * b.x);
const world = s => v(s.x / 16, s.y / 16, s.z / 16);   // a north-facing set at the origin: structure px -> world blocks
const REST = v(13.702, 5.542, 7.135), PULL = v(7.352, 4.558, -1.617), handle = t => world(add(REST, mul(PULL, t)));
const YANK_AT = 0, RELEASE_AT = 6, PULL_TICKS = 12, BLEND_IN = 10, BLEND_OUT = 8, ARM = 12 * 0.78 / 16;
const BEND_OUT = 0.55, BEND_UP = 0.62, BEND_MAX = 1.4, SHOULDER = {right: 0.30, up: -0.60, fwd: -0.05}, UNDER = {fwd: 0.30, up: -0.62, right: 0.26};
const smooth = t => { t = Math.max(0, Math.min(1, t)); return t * t * (3 - 2 * t); };
const easeOut = t => 1 - (1 - t) * (1 - t);
// the timeline: enter, a pull, a pause, leave
const T_PULL = BLEND_IN + 2, T_LEAVE = T_PULL + PULL_TICKS + 6, T_END = T_LEAVE + BLEND_OUT;
const blend = t => t < T_LEAVE ? smooth(t / BLEND_IN) : 1 - smooth((t - T_LEAVE) / BLEND_OUT);
const pullAge = t => t >= T_PULL && t < T_PULL + PULL_TICKS ? t - T_PULL : -1;
const pullV = t => { const a = pullAge(t); if (a < 0) return 0; return a < RELEASE_AT ? easeOut(Math.min(1, (a - YANK_AT) / 5)) : 1 - easeOut(Math.min(1, (a - RELEASE_AT) / 5)); };
const dirOf = (yaw, pitch) => { const y = yaw * Math.PI / 180, p = pitch * Math.PI / 180; return v(-Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p)); };
const T35 = Math.tan(35 * Math.PI / 180), onScreen = (cam, fwd, right, up, p) => { const d = sub(p, cam), z = dot(d, fwd); return z > 0.05 && Math.abs(dot(d, up) / z) < T35 && Math.abs(dot(d, right) / z) < T35 * 16 / 9; };
function run(dist) {
  const rest = handle(0), eye = v(rest.x + dist, 1.62, rest.z), look0 = sub(rest, eye);
  const yaw0 = Math.atan2(-look0.x, look0.z) * 180 / Math.PI, pitch0 = -Math.atan2(look0.y, Math.hypot(look0.x, look0.z)) * 180 / Math.PI;
  const out = {dist, cutInView: 0, handOffHeld: 0, nearestAxis: 9, maxTurnPerTick: 0};
  let prev = null;
  for (let t = 0; t <= T_END; t += 0.25) {
    const k = blend(t); let flat = sub(eye, rest); flat = nrm(v(flat.x, 0, flat.z));
    let off = sub(add(add(rest, mul(flat, BEND_OUT)), v(0, BEND_UP, 0)), eye); if (len(off) > BEND_MAX) off = mul(nrm(off), BEND_MAX);
    const age = pullAge(t), kick = age >= 0 && age < RELEASE_AT ? Math.sin(Math.PI * age / RELEASE_AT) * 0.035 : 0;
    const cam = add(add(eye, mul(off, k)), v(0, kick * k, 0)), lk = sub(add(rest, v(0, 0.04, 0)), cam);
    const yaw = yaw0 + (Math.atan2(-lk.x, lk.z) * 180 / Math.PI - yaw0) * k, pitch = pitch0 + (-Math.atan2(lk.y, Math.hypot(lk.x, lk.z)) * 180 / Math.PI - pitch0) * k;
    if (t % 1 === 0) { if (prev) out.maxTurnPerTick = Math.max(out.maxTurnPerTick, Math.hypot(yaw - prev[0], pitch - prev[1])); prev = [yaw, pitch]; }
    if (k <= 0) continue;
    const fwd = dirOf(yaw, pitch), right = nrm(cross(fwd, v(0, 1, 0))), up = cross(right, fwd);
    const under = add(add(add(cam, mul(fwd, UNDER.fwd)), mul(up, UNDER.up)), mul(right, UNDER.right)), g = handle(pullV(t));
    const hand = add(under, mul(sub(g, under), k));
    const shoulder = add(add(add(cam, mul(right, SHOULDER.right)), mul(up, SHOULDER.up)), mul(fwd, SHOULDER.fwd));
    const Y = nrm(sub(hand, shoulder)), cap = add(hand, mul(Y, 0.035)), far = sub(cap, mul(Y, ARM));
    if (onScreen(cam, fwd, right, up, far)) out.cutInView++;
    if (t >= BLEND_IN && t < T_LEAVE && !onScreen(cam, fwd, right, up, hand)) out.handOffHeld++;
    const ax = sub(far, cap), s = Math.max(0, Math.min(1, dot(sub(cam, cap), ax) / dot(ax, ax)));
    out.nearestAxis = Math.min(out.nearestAxis, len(sub(add(cap, mul(ax, s)), cam)));
  }
  out.nearestAxis = +out.nearestAxis.toFixed(3); out.maxTurnPerTick = +out.maxTurnPerTick.toFixed(2);
  return out;
}
const results = [0.7, 1.0, 1.4, 1.6].map(run);
for (const r of results) console.log(JSON.stringify(r));
const bad = results.filter(r => r.cutInView || r.handOffHeld || r.nearestAxis < 0.3 || r.maxTurnPerTick > 8);
if (bad.length) { console.error('FAIL', JSON.stringify(bad)); process.exit(1); }
console.log('PASS');
