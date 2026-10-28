// BR-51 V3 AFL Native first-person animation authoring (docs/native_guns/br51_01_v3_game_model_v1.md, "动画"). The M14
// mechanism (user 2026-10-21: "原版外观 + M14 正确机构"): an operating rod on the right whose handle the right hand racks,
// a rotating bolt that unlocks (turns about the bore) before it travels, a bolt lock that holds the action open on an empty
// magazine, and a magazine that hooks its front lug into the receiver's front catch and rocks in and out about it, with the
// latch behind it pressed forward by the support thumb. Normal-tier clips and their ADS reloads (since 2026-10-27): the P9 V3 script's
// solvers, bake and motion language (tools/author-p9-01-v3-native-animations.mjs), re-calibrated for a shouldered rifle.
// The reloads and inspects are hand-keyed since 2026-10-27 (HAND_KEYED / KEY_SOURCE: the gun and the camera are bezier keys
// in the .bbmodel, docs/native_guns/br51_animation_redo_v1.md); everything else is solved and baked at 60 Hz.
//   node tools/author-br51-01-v3-native-animations.mjs                 -> audit/summary only
//   --write-source     write the clips into src/main/blockbench/br51_01_v3_native.bbmodel (hand keys kept verbatim)
//   --write-runtime    write assets/.../animations/br51_01_v3_native.animation.json
import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {ARM_HALF, MP, HP, RP, LP, LA, nativePath as sourcePath} from './build-br51-01-v3-native.mjs';
import {bbChannel} from './bb-keyframes.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
export const runtimePath = path.join(root, 'src/main/resources/assets/apocalypse_firstlight/animations/br51_01_v3_native.animation.json');
const NS = 'apocalypse_firstlight:br51_01_';
const soundDir = path.join(root, 'src/main/resources/assets/apocalypse_firstlight/sounds/weapons/br51_01');
const FPS = 60;

// ---------- math (as the P9 V3 script) ----------
const add = (a, b) => a.map((v, i) => v + b[i]), sub = (a, b) => a.map((v, i) => v - b[i]);
const mul = (a, n) => a.map(v => v * n), lerp = (a, b, s) => a.map((v, i) => v + (b[i] - v) * s);
const norm = a => mul(a, 1 / Math.hypot(...a));
const clamp01 = u => Math.max(0, Math.min(1, u));
const D = Math.PI / 180;
// Blockbench / Gecko bone rotation: M = Rz * Ry * Rx (degrees).
function E([x, y, z]) {
    const [cx, sx, cy, sy, cz, sz] = [Math.cos(x * D), Math.sin(x * D), Math.cos(y * D), Math.sin(y * D), Math.cos(z * D), Math.sin(z * D)];
    return [[cy * cz, sx * sy * cz - cx * sz, cx * sy * cz + sx * sz],
            [cy * sz, sx * sy * sz + cx * cz, cx * sy * sz - sx * cz],
            [-sy, sx * cy, cx * cy]];
}
const mv = (m, v) => m.map(r => r[0] * v[0] + r[1] * v[1] + r[2] * v[2]);
const mm = (a, b) => a.map(r => [0, 1, 2].map(j => r[0] * b[0][j] + r[1] * b[1][j] + r[2] * b[2][j]));
const euler = m => [Math.atan2(m[2][1], m[2][2]), Math.asin(Math.max(-1, Math.min(1, -m[2][0]))), Math.atan2(m[1][0], m[0][0])].map(v => v / D);
function scalar(pts, t) {
    const x = pts.map(p => p[0]), y = pts.map(p => p[1]);
    if (t <= x[0]) return y[0]; if (t >= x.at(-1)) return y.at(-1);
    const h = x.slice(1).map((v, i) => v - x[i]), d = h.map((v, i) => (y[i + 1] - y[i]) / v), m = y.map(() => 0);
    for (let i = 1; i < y.length - 1; i++) if (d[i - 1] * d[i] > 0) { const w1 = 2 * h[i] + h[i - 1], w2 = h[i] + 2 * h[i - 1]; m[i] = (w1 + w2) / (w1 / d[i - 1] + w2 / d[i]); }
    const j = x.findIndex(v => v >= t) - 1, u = (t - x[j]) / h[j];
    return (2 * u ** 3 - 3 * u * u + 1) * y[j] + (u ** 3 - 2 * u * u + u) * h[j] * m[j] + (-2 * u ** 3 + 3 * u * u) * y[j + 1] + (u ** 3 - u * u) * h[j] * m[j + 1];
}
const curve1 = knots => t => scalar(knots, t);
const Z = [0, 0, 0];
const T3 = m => [0, 1, 2].map(i => [0, 1, 2].map(j => m[j][i]));
const cross = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];

// ---------- motion language (as the P9; a 4.4 kg rifle moves slower and settles longer) ----------
function go(t, t0, t1, {antic = 0.04, over = 0.07, per = 0.15, tau = 0.06, pre = 0.07} = {}) {
    if (t < t0 - pre) return 0;
    if (t < t0) return -antic * Math.sin(Math.PI * (t - t0 + pre) / pre);
    if (t < t1) { const u = (t - t0) / (t1 - t0); return 1.6 * u * u - 0.6 * u * u * u; }
    const s = t - t1; return 1 + over * Math.sin(2 * Math.PI * s / per) * Math.exp(-s / tau);
}
const ease = (t, t0, t1) => clamp01(go(t, t0, t1, {antic: 0, over: 0, pre: 0}));
function jolt(t, te, amp, {rise = 0.017, per = 0.1, tau = 0.04} = {}) {
    const s = t - te; if (s <= 0) return Z;
    const k = s < rise ? s / rise : Math.exp(-(s - rise) / tau) * Math.cos(2 * Math.PI * (s - rise) / per);
    return mul(amp, k);
}
const sum = (...v) => v.reduce((a, b) => add(a, b), Z);
function legs(list) {                          // list: [t0, t1, pose, go options]; the first leg starts from IDLE
    return t => {
        let pos = Z, rot = Z, prev = IDLE;
        for (const [t0, t1, p, o] of list) {
            const w = go(t, t0, t1, {antic: 0, over: 0, ...o});
            pos = add(pos, mul(sub(p.pos, prev.pos), w)); rot = add(rot, mul(sub(p.rot, prev.rot), w)); prev = p;
        }
        return {pos, rot};
    };
}
const drift = (t, t0, t1, amp, per) => t <= t0 || t >= t1 ? Z
    : mul(amp, Math.sin(2 * Math.PI * (t - t0) / per) * clamp01((t - t0) / 0.25) * clamp01((t1 - t) / 0.25));

// ---------- hands (AFL standard: anchor = distal hand cap, forearm along local -Y, palm +Z) ----------
function levelArm(forearm, palm = [0, -1, 0]) {
    const ey = mul(norm(forearm), -1), ez = norm(sub(palm, mul(ey, palm.reduce((a, v, i) => a + v * ey[i], 0))));
    const ex = cross(ey, ez); return euler([0, 1, 2].map(r => [ex[r], ey[r], ez[r]]));
}
// Firing hand under the stock's wrist (z 11-14, y 6.2-10.4, x +-0.87), its front face against the trigger's lower end
// (tip y 7.83, z 8.35) and the grip's underside sunk 0.1-0.4 into the top of the hand; the trigger guard's rear bow runs
// into the hand's front as the finger would. Forearm back and down the wrist's slope toward the elbow out right, steep
// enough to pass under the butt. User 2026-10-21 round 3: round 2 (cap [0.5, 8.45, 9.6], forearm [0.22, -0.42, 1]) had
// the stock wrist and butt through the hand and forearm; "往左下，让右手前面卡在扳机上，要自然". Fitted offline
// against the game low poly (stock and guard vertices vs the hand box).
const RH_POINT = [-0.13, 6.99, 7.72];
const RH_ROT = levelArm([0.2, -0.7, 1]);
// Support hand under the forend (stock underside y 9.30-9.40 at z -6 to -9): palm up, the forend sunk 1 unit into the
// top of the hand, the forearm across to the elbow out left and down (user 2026-10-21: the forearm hanging straight down
// under the gun looked strange; on screen it now runs from the hand to the lower left edge, x 0.18 -> -0.27; round 2:
// tucked in toward the gun, the forearm's sideways part from -0.88 to -0.6).
const LG_POINT = [-0.35, 9.35 + 1.0 - ARM_HALF, -7.0];
const LG_ROT = levelArm([-0.6, -0.5, 0.5], [0.3, 1, 0]);
// Support hand on the magazine (magazine space: the 20-round body is x +-0.67, y 4.0-11.0, z 1.45-5.52 from the gun's
// origin): palm on its left side, the thumb up behind it on the latch (mag_latch, z 5.73).
const MAG_GRIP = [-0.75, 6.3, 3.7];
const MAG_FOREARM = [-0.55, -0.78, 0.3];
const HAND_ON_MAG = levelArm(MAG_FOREARM, [1, 0.25, 0]);
// Firing hand on the operating rod handle (x 1.17-1.50, y 11.32-11.95, z 1.74-2.98 at rest): the hand box beside the
// receiver, the handle in the edge of the hand, the forearm back and down out right; it travels with the rod.
const OP_HANDLE = [1.34, 11.63, 2.36];
const RH_CHARGE_POINT = add(OP_HANDLE, [ARM_HALF - 0.55, -0.15, -0.75]);
const RH_CHARGE_ROT = levelArm([0.42, -0.36, 0.84], [-1, -0.2, 0]);

// ---------- mechanism (M14: travel along the bore, Blockbench px) ----------
const OP_FREE = 0.8;         // operating rod travel before its cam has turned the bolt (the bolt stays put)
const OP_FULL = 4.4;         // full rearward travel (the handle at the rear of its cut)
const OP_HELD = 4.2;         // held open by the bolt lock
const BOLT_TURN = 25;        // bolt unlock rotation about the bore (the right lug and roller rise)
const BOLT_LOCK_UP = 15;     // bolt lock raised into the bolt's path on an empty magazine
const LATCH_PRESS = -12;     // magazine latch pressed forward by the thumb
const MAG_ROCK = 22;         // magazine rocked about its front lug: the rear drops, the bottom swings forward
const MAG_UNHOOK = [0, -0.65, -0.55];   // then down and forward off the front catch
const boltOf = op => ({z: Math.max(0, op - OP_FREE), r: BOLT_TURN * clamp01(op / OP_FREE)});
const mech = op => ({oprod: {position: () => [0, 0, op]}, bolt: {position: () => [0, 0, boltOf(op).z], rotation: () => [0, 0, boltOf(op).r]}});
const mechT = op => ({oprod: {position: t => [0, 0, op(t)]}, bolt: {position: t => [0, 0, boltOf(op(t)).z], rotation: t => [0, 0, boltOf(op(t)).r]}});

// ---------- rig solvers ----------
const IDLE = {pos: Z, rot: Z};
const handleWorld = (h, x) => add(add(h.pos, HP), mv(E(h.rot), sub(x, HP)));
// the gun's magazine (or a helper drawn as it) in the gun: rocked `a` degrees about the front lug, its pivot moved by `o`
function magInGun(h, a = 0, o = Z, cant = 0) {   // cant: rolled about its own long axis (the tier fumble's crooked magazine)
    const R = mm(E(h.rot), E([a, 0, cant]));
    return {pos: sub(handleWorld(h, add(MP, o)), MP), rot: euler(R), R};
}
const magPointWorld = (m, x) => add(add(m.pos, MP), mv(m.R ?? E(m.rot), sub(x, MP)));
const solve = (pivot, anchor) => (point, R) => ({pos: sub(sub(point, pivot), mv(R, sub(anchor, pivot))), rot: euler(R)});
const handAt = solve(LP, LA);
const rightAt = solve(RP, HP);                                    // righthand lives under handling; right_hand_anchor rests at HP
const RIGHT_REST = rightAt(RH_POINT, E(RH_ROT));
const leftOnGun = h => handAt(handleWorld(h, LG_POINT), mm(E(h.rot), E(LG_ROT)));
const LOW_LEFT = handAt([-9, -9, 6], E(levelArm([-0.4, -0.7, 0.55], [0.3, 1, 0])));
const onMag = (m, gap = 0) => handAt(magPointWorld(m, add(MAG_GRIP, mul(norm(MAG_FOREARM), gap))), mm(m.R, E(HAND_ON_MAG)));
const onHandle = op => rightAt(add(RH_CHARGE_POINT, [0, 0, op]), E(RH_CHARGE_ROT));
function quat(m) {
    const tr = m[0][0] + m[1][1] + m[2][2];
    if (tr > 0) { const S = Math.sqrt(tr + 1) * 2; return [(m[2][1] - m[1][2]) / S, (m[0][2] - m[2][0]) / S, (m[1][0] - m[0][1]) / S, S / 4]; }
    if (m[0][0] > m[1][1] && m[0][0] > m[2][2]) { const S = Math.sqrt(1 + m[0][0] - m[1][1] - m[2][2]) * 2; return [S / 4, (m[0][1] + m[1][0]) / S, (m[0][2] + m[2][0]) / S, (m[2][1] - m[1][2]) / S]; }
    if (m[1][1] > m[2][2]) { const S = Math.sqrt(1 + m[1][1] - m[0][0] - m[2][2]) * 2; return [(m[0][1] + m[1][0]) / S, S / 4, (m[1][2] + m[2][1]) / S, (m[0][2] - m[2][0]) / S]; }
    const S = Math.sqrt(1 + m[2][2] - m[0][0] - m[1][1]) * 2; return [(m[0][2] + m[2][0]) / S, (m[1][2] + m[2][1]) / S, S / 4, (m[1][0] - m[0][1]) / S];
}
function qmat([x, y, z, w]) {
    return [[1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
            [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
            [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)]];
}
function slerpRot(ra, rb, s) {
    const a = quat(E(ra)); let b = quat(E(rb)), d = a.reduce((v, x, i) => v + x * b[i], 0);
    if (d < 0) { b = b.map(v => -v); d = -d; }
    let q; if (d > 0.9995) q = a.map((v, i) => v + (b[i] - v) * s);
    else { const th = Math.acos(d), sa = Math.sin((1 - s) * th) / Math.sin(th), sb = Math.sin(s * th) / Math.sin(th); q = a.map((v, i) => v * sa + b[i] * sb); }
    const n = Math.hypot(...q); return euler(qmat(q.map(v => v / n)));
}
const mixT = (a, b, s) => ({pos: lerp(a.pos, b.pos, s), rot: slerpRot(a.rot, b.rot, s)});
const mixM = (a, b, s) => { const r = slerpRot(a.rot, b.rot, s); return {pos: lerp(a.pos, b.pos, s), rot: r, R: E(r)}; };
function unwrap(v, ref) {
    let best = null, bd = Infinity;
    for (const base of [v, [v[0] + 180, 180 - v[1], v[2] + 180]])
        for (const k0 of [-2, -1, 0, 1, 2]) for (const k1 of [-2, -1, 0, 1, 2]) for (const k2 of [-2, -1, 0, 1, 2]) {
            const c = [base[0] + 360 * k0, base[1] + 360 * k1, base[2] + 360 * k2], d = c.reduce((m, x, i) => m + (x - ref[i]) ** 2, 0);
            if (d < bd) { bd = d; best = c; }
        }
    return best;
}
const LH_POS_CANDIDATES = [[0, 90, 0], [0, -90, 0], [90, 0, 0], [-90, 0, 0], [0, 0, 90], [0, 0, -90], [0, 0, 0]];
const lhTracks = LH => ({position: t => LH(t).pos, solved: t => LH(t).rot});
const HIDE_HELPERS = {reload_magazine: {scale: () => [0, 0, 0], step: true}, empty_old_mag: {scale: () => [0, 0, 0], step: true}};
const shown = (t0, t1) => ({scale: t => (t >= t0 && t < t1) ? [1, 1, 1] : [0, 0, 0], step: true});
const hiddenFrom = (t0, t1) => ({scale: t => (t >= t0 && t < t1) ? [0, 0, 0] : [1, 1, 1], step: true});

// ---------- design poses (the gun about the firing hand's wrist, HP) ----------
// Low: muzzle down and out of the frame bottom right (checked: receiver and muzzle below the screen edge).
const LOW = {pos: [1.5, -16, 2], rot: [-35, -6, -18]};
// Rolled left so the operating rod handle on the right side faces up into view for the firing hand (first draw).
const CHARGE_TILT = {pos: [-0.6, 1.0, 0.6], rot: [5, -6, 16]};
// The unload's magazine check: rolled right about the wrist so the magwell comes left into view. (The reloads' and
// inspects' poses are hand keys in the .bbmodel since 2026-10-27.)
const MAG_CHECK = {pos: [-1.4, 2.0, 1.0], rot: [8, 14, -30]};
const OFFSCREEN_MAG = {pos: [-6, -14, 4], rot: [-30, 20, 40], R: E([-30, 20, 40])};    // a magazine at the belt, lower left (root space)

// ---------- clips ----------
function clip(name, length, loop, build) { return {name, length, loop, build}; }
const clips = [];

clips.push(clip('static_idle', 0.25, 'hold', () => ({tracks: {
    righthand: {position: () => RIGHT_REST.pos, rotation: () => RIGHT_REST.rot},
    lefthand: lhTracks(() => leftOnGun(IDLE)),
    ...mech(0), bolt_lock: {rotation: () => Z}, ...HIDE_HELPERS}})));

// Empty: the bolt lock holds the action open (the operating rod 4.2 back, the bolt turned and back).
clips.push(clip('empty_idle', 0.25, 'loop', () => ({tracks: {
    ...mech(OP_HELD), bolt_lock: {rotation: () => [BOLT_LOCK_UP, 0, 0]}, ...HIDE_HELPERS}})));

// Shoot: the gas drives the rod back in one tick (the last shot hands over to empty_idle at its rearward pose); the
// rifle's kick is heavier and slower than the pistol's. Native recoil owns the camera.
clips.push(clip('shoot', 0.25, 'once', () => {
    const op = curve1([[0, 0], [0.008, 1.0], [0.022, OP_FULL], [0.034, OP_FULL], [0.07, 0.6], [0.082, 0]]);
    const H = t => ({
        rot: sum(jolt(t, 0, [3.4, 0.5, 1.2], {rise: 0.02, per: 0.24, tau: 0.06}), jolt(t, 0.082, [0.4, 0, -0.2], {per: 0.08, tau: 0.025})),
        pos: sum(jolt(t, 0, [0, 0.12, 0.95], {rise: 0.018, per: 0.24, tau: 0.06}), jolt(t, 0.082, [0, 0, 0.08], {per: 0.08, tau: 0.025}))});
    return {tracks: {
        handling: {position: t => H(t).pos, rotation: t => H(t).rot},
        lefthand: lhTracks(t => leftOnGun(H(t))),
        ...mechT(op)}};
}));

// Draw: up from low right, the support hand meets the forend late.
clips.push(clip('draw', 0.6, 'once', () => {
    const H = t => { const w = go(t, 0.02, 0.3, {antic: 0, over: 0.06, per: 0.22, tau: 0.07});
        return {pos: sum(lerp(LOW.pos, Z, w), jolt(t, 0.38, [0, -0.12, 0])), rot: sum(lerp(LOW.rot, Z, w), jolt(t, 0.38, [1.0, 0, -0.4], {per: 0.13, tau: 0.04}))}; };
    const LH = t => mixT(LOW_LEFT, leftOnGun(H(t)), ease(t, 0.18, 0.4));
    return {tracks: {
        handling: {position: t => H(t).pos, rotation: t => H(t).rot},
        lefthand: lhTracks(LH),
        camera: {rotation: t => sum(mul([-1.2, 0.5, 1.0], 1 - go(t, 0.02, 0.32, {antic: 0, over: 0.1})), jolt(t, 0.38, [0.5, -0.15, -0.3]))},
    }, sounds: [[0, 'draw']]};
}));

// Put away: the support hand off the forend first, then down out low right.
clips.push(clip('put_away', 0.55, 'once', () => {
    const H = t => { const w = go(t, 0.1, 0.48, {antic: 0.04, over: 0});
        return {pos: lerp(Z, LOW.pos, w), rot: lerp(Z, LOW.rot, w)}; };
    const LH = t => mixT(leftOnGun(H(t)), LOW_LEFT, ease(t, 0.0, 0.24));
    return {tracks: {
        handling: {position: t => H(t).pos, rotation: t => H(t).rot},
        lefthand: lhTracks(LH),
        camera: {rotation: t => mul([-1.0, 0.4, 0.9], ease(t, 0.1, 0.48))},
    }, sounds: [[0, 'put_away']]};
}));

// The firing hand racks the operating rod: off the wrist (the support hand holds the forend), onto the handle, pulled to
// the rear and let go (the rod and bolt slam home, picking up a round), back round the wrist. T: rhOff, rhOn, pull,
// pulled, let, rhBack, rhHome. The action starts at `from` (OP_HELD on an empty magazine, 0 closed).
function charge(T, from) {
    const op = t => t < T.pull ? from : t < T.let ? from + (OP_FULL + 0.05 - from) * ease(t, T.pull, T.pulled)
        : (OP_FULL + 0.05) * (1 - clamp01((t - T.let) / 0.035));
    const RH = t => {
        if (t < T.rhOn) return mixT(RIGHT_REST, onHandle(op(T.rhOn)), go(t, T.rhOff, T.rhOn, {antic: 0, over: 0.03, pre: 0}));
        if (t < T.let + 0.02) return onHandle(op(t));
        return mixT(onHandle(op(T.let + 0.02)), RIGHT_REST, go(t, T.rhBack, T.rhHome, {antic: 0.02, over: 0.03}));
    };
    const hits = t => sum(jolt(t, T.pulled, [-0.8, -0.3, 0.6], {rise: 0.03, per: 0.14, tau: 0.04}),
        jolt(t, T.let + 0.03, [2.2, 0.4, -1.4], {rise: 0.015, per: 0.1, tau: 0.04}));
    return {op, RH, hits, lock: t => from > 0 && t < T.pull + 0.03 ? [BOLT_LOCK_UP, 0, 0] : Z};
}

// First draw (first equip / a gun with rounds and none chambered; NativeGunActions.FIRST_DRAW): up to the ready rolled
// left, the firing hand racks the rod, back on the wrist and settled.
clips.push(clip('first_draw', 1.4, 'once', () => {
    const T = {up: 0.32, rhOff: 0.4, rhOn: 0.56, pull: 0.58, pulled: 0.76, let: 0.78, rhBack: 0.84, rhHome: 1.08, back: 0.86, backEnd: 1.22};
    const c = charge(T, 0);
    const path = legs([[0.02, T.up, CHARGE_TILT, {antic: 0, over: 0.05, per: 0.22, tau: 0.07}], [T.back, T.backEnd, IDLE, {antic: 0.03, over: 0.06, per: 0.24, tau: 0.08}]]);
    const H = t => { const p = path(t), w = go(t, 0.02, T.up, {antic: 0, over: 0});
        const rot = sum(lerp(LOW.rot, Z, w), p.rot, c.hits(t)); return {rot, pos: sum(lerp(LOW.pos, Z, w), p.pos, mul([0, 0.08, 0.2], c.hits(t)[0]))}; };
    const LH = t => mixT(LOW_LEFT, leftOnGun(H(t)), ease(t, 0.16, 0.38));
    return {tracks: {
        handling: {position: t => H(t).pos, rotation: t => H(t).rot},
        righthand: {position: t => c.RH(t).pos, rotation: t => c.RH(t).rot},
        lefthand: lhTracks(LH),
        ...mechT(c.op),
        camera: {rotation: t => sum(mul([-1.2, 0.5, 1.0], 1 - go(t, 0.02, 0.34, {antic: 0, over: 0.1})), mul(c.hits(t), 0.35))},
    }, sounds: [[0, 'draw'], [T.pull - 0.01, 'charging_back'], [T.let - 0.01, 'charging_release']], timing: T};
}));

// ---------- reloads and inspects: hand-keyed (docs/native_guns/br51_animation_redo_v1.md) ----------
// Redo 2026-10-27 (user: 「可以，按你建议的来，开始吧」, after the COD references and the keyframe table): the gun
// (`handling`, rotation and position) and the `camera` are bezier keys placed in Blockbench and read back from the .bbmodel
// (HAND_KEYED); the script solves the rest from them: the support hand on the forend / the magazine, the magazines, the
// latch, the action, the firing hand (the operating rod rack, the wrist) and the force beats. The 2026-10-23 rule for every
// move (feedback: 沉稳 lives in the pauses): eased out of rest, a short deceleration into the next pose and a dead stop,
// no overshoot, no drift during holds; a force beat (the magazine rocked home, the rod let go) is one single-peak impulse,
// critically damped back. The old procedural clips (overshoot `go`, ringing `jolt`, sinusoidal `drift`) are in git.
const move = (t, t0, t1) => { const u = clamp01((t - t0) / (t1 - t0)); return u * u * (3 - 2 * u); };
function hit(t, te, amp, {rise = 0.02, tau = 0.045} = {}) {
    const s = t - te; if (s <= 0) return Z;
    if (s < rise) return mul(amp, Math.sin(0.5 * Math.PI * s / rise));
    const x = (s - rise) / tau; return mul(amp, (1 + x) * Math.exp(-x));
}
// The firing hand's wrist: the hand box stays on the grip (RH_POINT) while the gun turns up about it, and the forearm keeps
// WRIST_HOLD of its rest direction in view instead of swinging up into the screen with the gun (no wrist on a block arm;
// the P9's bentRight, docs/dev/p9_reload_inspect_v1.md).
// The further the gun turns from the ready (TURN_FULL degrees), the more the forearm drops toward RH_DROP in view: the
// elbow falls and the arm leaves the bottom of the screen instead of covering the magwell (the references show no firing
// arm while the support hand works).
const RH_FOREARM = norm([0.2, -0.7, 1]);
const RH_DROP = norm([0.45, -1, 0.2]);
const WRIST_HOLD = 0.8, TURN_FULL = 40;
const viewArm = (rot, rest, drop) => {
    const w = clamp01(Math.hypot(...rot) / TURN_FULL), view = norm(lerp(rest, drop, w));
    return norm(lerp(rest, mv(T3(E(rot)), view), WRIST_HOLD));
};
const bentRight = rot => rightAt(RH_POINT, E(levelArm(viewArm(rot, RH_FOREARM, RH_DROP))));
// On the rod handle (raised gun): the forearm out to the right edge of the screen and a little down, as the references'
// right hand comes in from the right, instead of back toward the eye (where a near box fills the top right).
// At the eye (ADS) the handle sits beside the line of sight: the forearm comes up steeper from the bottom right corner.
const RH_CHARGE_FOREARM = norm([0.42, -0.36, 0.84]), RH_CHARGE_VIEW = norm([0.8, -1, 0]);
const RH_CHARGE_VIEW_ADS = norm(process.env.BR51_RACK_VIEW ? JSON.parse(process.env.BR51_RACK_VIEW) : [0.3, -1, 0.2]);
const onHandleBent = (op, rot, view = RH_CHARGE_VIEW) => rightAt(add(RH_CHARGE_POINT, [0, 0, op]),
    E(levelArm(norm(lerp(RH_CHARGE_FOREARM, mv(T3(E(rot)), view), WRIST_HOLD)), [-1, -0.2, 0])));

// Tactical: the support hand leaves the forend for the magazine, the thumb presses the latch forward, the magazine is
// rocked forward off its front catch and taken down to the belt; a fresh one comes up, its front lug is hooked into the
// catch and it is rocked back until the latch clicks (magazine_in's click 0.28 s into the file lands on the seat), the
// hand goes back to the forend. Empty: the empty magazine is rocked out and let fall; after the seat the support hand holds
// the forend and the firing hand leaves the grip to rack the rod off the bolt lock (charging_back / charging_release).
export const RELOAD = {
    tactical: {grab: 0.36, release: 0.42, out0: 0.45, out1: 0.62, unhook: 0.68, gone: 0.95, fetch: 1.05, entry: 1.4, hook: 1.47, seat: 1.7,
        regrip: 2.02, back: 1.84, backEnd: 2.24, end: 2.4},
    empty: {grab: 0.36, release: 0.42, out0: 0.45, out1: 0.62, unhook: 0.68, gone: 0.95, fetch: 1.05, entry: 1.4, hook: 1.47, seat: 1.7,
        regrip: 1.9, rhOff: 1.92, rhOn: 2.05, pull: 2.07, pulled: 2.27, let: 2.3, rhBack: 2.34, rhHome: 2.56, back: 2.4, backEnd: 2.75, end: 2.9},
};
// No magazine in the gun (reload_*_no_mag; ConfiguredNativeGunItem#reloadClip): the base clip's keys from the moment its
// magazine is off (NO_MAG_CUT s in), the gun brought up into that pose over the first NO_MAG_INTO s, the support hand going
// straight from the forend to the belt. The length and the magazine-in tick move by the same whole ticks (the game takes
// the cut from the two clip lengths).
export const NO_MAG_CUT = 0.65;
// magazine_in (the hook and the rock in) starts 0.28 s before the seat; magazine_seat (the latch click, split off it 2026-10-27,
// tools/build-br51-01-sounds.mjs) 0.22 s into that: its click is on the seat. The tiers' moment the rounds go in is this cue
// (NativeReloadTiers), as the P9's.
const SEAT_LEAD = 0.06;
const NO_MAG_INTO = 0.32;
const cutT = T => Object.fromEntries(Object.entries(T).filter(([k]) => !['grab', 'release', 'out0', 'out1', 'unhook', 'gone'].includes(k))
    .map(([k, v]) => [k, +(v - NO_MAG_CUT).toFixed(3)]));
RELOAD.tacticalNoMag = cutT(RELOAD.tactical);
RELOAD.emptyNoMag = cutT(RELOAD.empty);
// ADS reload (round 2; docs/native_guns/br51_animation_redo_v1.md 5.7; the P9's docs/dev/p9_reload_inspect_v1.md 2.7): played
// while aiming, under the ADS correction (the sights at the eye). Eyes stay on the target: the rifle stays in the shoulder and
// only dips a little and cants, the support hand works under the line of sight by feel; same beats, sounds and length as the
// hip clips, back on the sights right after the seat (empty: after the rack) and held there to the end. `back` / `backEnd`
// are where the gun is back on the sights (the third-person action ends there).
RELOAD.tacticalAds = {...RELOAD.tactical, back: 1.74, backEnd: 2.0};
RELOAD.emptyAds = {...RELOAD.empty, back: 2.32, backEnd: 2.6};
RELOAD.tacticalNoMagAds = cutT(RELOAD.tacticalAds);
RELOAD.emptyNoMagAds = cutT(RELOAD.emptyAds);
const ADS_HIT = 0.45;   // the force beats on the gun at the eye (a degree there shows a lot)
const rockOut = (t, T) => ({a: MAG_ROCK * move(t, T.out0, T.out1), o: mul(MAG_UNHOOK, move(t, T.out1 - 0.03, T.unhook))});
const rockIn = (t, T) => ({a: MAG_ROCK * (1 - move(t, T.hook, T.seat)), o: mul(MAG_UNHOOK, 1 - move(t, T.entry, T.hook))});
const latch = (t, T) => t < T.release - 0.03 ? Z : t < T.out1 + 0.04 ? [LATCH_PRESS * move(t, T.release - 0.03, T.release), 0, 0]
    : t < T.seat - 0.06 ? [LATCH_PRESS * (1 - move(t, T.out1 + 0.04, T.out1 + 0.1)), 0, 0]
    : t < T.seat ? [LATCH_PRESS * 0.7 * move(t, T.seat - 0.06, T.seat - 0.01), 0, 0] : [LATCH_PRESS * 0.7 * (1 - clamp01((t - T.seat) / 0.02)), 0, 0];
// The firing hand racks the operating rod: off the grip (the support hand holds the forend), onto the handle, pulled to the
// rear and let go (the rod and bolt slam home, picking up a round), back onto the grip. T: rhOff, rhOn, pull, pulled, let,
// rhBack, rhHome. The action starts at `from` (OP_HELD on an empty magazine, 0 closed); `gun` is the gun's rotation (the
// hand on the grip and on the handle, bentRight / onHandleBent).
function rack(T, from, gun, view = RH_CHARGE_VIEW) {
    const op = t => t < T.pull ? from : t < T.let ? from + (OP_FULL + 0.05 - from) * move(t, T.pull, T.pulled)
        : (OP_FULL + 0.05) * (1 - clamp01((t - T.let) / 0.035));
    const RH = t => {
        const r = gun(t);
        if (t < T.rhOn) return mixT(bentRight(r), onHandleBent(op(T.rhOn), r, view), move(t, T.rhOff, T.rhOn));
        if (t < T.let + 0.02) return onHandleBent(op(t), r, view);
        return mixT(onHandleBent(op(T.let + 0.02), r, view), bentRight(r), move(t, T.rhBack, T.rhHome));
    };
    const hits = t => sum(hit(t, T.pulled, [-0.6, -0.2, 0.45], {rise: 0.03}), hit(t, T.let + 0.03, [1.8, 0.3, -1.1], {rise: 0.015, tau: 0.04}));
    return {op, RH, hits, lock: t => from > 0 && t < T.pull + 0.03 ? [BOLT_LOCK_UP, 0, 0] : Z};
}
function reloadKeyed(hand, empty, noMag = false, ads = false, tier = null) {
    const T = tier ? (noMag ? tier.noMag : tier.T) : RELOAD[(empty ? 'empty' : 'tactical') + (noMag ? 'NoMag' : '') + (ads ? 'Ads' : '')];
    const cut = noMag ? NO_MAG_CUT : 0, into = t => noMag ? move(t, 0, NO_MAG_INTO) : 1;
    const kr = hand.ch('handling', 'rotation'), kp = hand.ch('handling', 'position'), kc = hand.ch('camera', 'rotation');
    const kt = tier ? tier.warp : t => t;   // a tier plays the practised keys on its own beats
    const K = t => { const k = {rot: kr(kt(t + cut)), pos: kp(kt(t + cut))}; return into(t) < 1 ? mixT(IDLE, k, into(t)) : k; };
    let H = null;
    const c = empty ? rack(T, OP_HELD, t => K(t).rot, ads ? RH_CHARGE_VIEW_ADS : RH_CHARGE_VIEW) : null;
    const hits = t => sum(
        noMag ? Z : hit(t, T.release, [-0.4, 0.15, 0.3]),                                // thumb on the latch
        noMag ? Z : hit(t, T.out1, [-0.7, 0.2, 0.5], {rise: 0.03}),                      // the magazine comes off the catch
        hit(t, T.hook, [0.5, -0.15, 0.2]),                                               // front lug into the catch
        hit(t, T.seat, [1.8, -0.4, 0.8], {rise: 0.017, tau: 0.05}),                      // rocked home, the latch clicks
        T.miss != null ? sum(hit(t, T.miss + FUMBLE_AT.bump * FUMBLE, [0.9, 0.3, -0.4], {rise: 0.02}),            // the crooked magazine stops on the edge
            hit(t, T.miss + FUMBLE_AT.shove * FUMBLE, [0.5, 0.2, -0.2])) : Z,                                        // pushed again, it will not go
        empty ? c.hits(t) : Z);
    H = t => { const k = K(t), h = mul(hits(t), ads ? ADS_HIT : 1); return {rot: add(k.rot, h), pos: add(k.pos, mul([0, 0.08, -0.05], h[0]))}; };
    // the old magazine: rocked out in the hand, then (tactical) taken down to the belt, (empty) let fall with its velocity
    const oldInGun = t => { const r = rockOut(t, T); return magInGun(H(t), r.a, r.o); };
    const g = 520, tFree = noMag ? 0 : T.unhook;
    const vRel = noMag ? Z : mul(sub(oldInGun(tFree).pos, oldInGun(tFree - 0.004).pos), 1 / 0.004);
    const oldMag = t => {
        if (t <= tFree) return oldInGun(t);
        if (!empty) return mixM(oldInGun(tFree), OFFSCREEN_MAG, move(t, tFree, T.gone));
        const base = oldInGun(tFree), dt = t - tFree;
        const pos = add(add(base.pos, mul(vRel, dt)), [0, -0.5 * g * dt * dt, 0]);
        const R = mm(base.R, E([90 * dt, 10 * dt, -50 * dt])); return {pos, rot: euler(R), R};
    };
    // the new magazine: up from the belt on an arc into line with the catch, hooked, rocked home
    const newMag = t => {
        if (T.miss != null && t >= T.miss && t < T.entry) return fumbleMag(H(t), (t - T.miss) / FUMBLE);
        if (t < (T.miss ?? T.entry)) {
            const s = move(t, T.fetch, T.miss ?? T.entry), r = rockIn(T.entry, T), g0 = magInGun(H(t), r.a, r.o);
            const m = mixM(OFFSCREEN_MAG, g0, s); m.pos = add(m.pos, mul([-1.6, 0.8, 0.5], Math.sin(Math.PI * s))); return m;
        }
        const r = rockIn(Math.min(t, T.seat), T); return magInGun(H(t), r.a, r.o);
    };
    const LH = t => {
        if (noMag && t < T.fetch) return mixT(leftOnGun(H(t)), onMag(OFFSCREEN_MAG), move(t, 0.04, T.fetch - 0.02));
        if (t < T.grab) return mixT(leftOnGun(H(t)), onMag(oldInGun(t), 0.6), move(t, 0.04, T.grab));
        if (t < T.gone) return !empty || t <= tFree ? onMag(oldMag(t))   // empty: let go, then down to the belt for the next one
            : mixT(onMag(oldInGun(tFree), 1.2 * move(t, tFree, tFree + 0.08)), onMag(OFFSCREEN_MAG), move(t, tFree + 0.04, T.gone));
        if (t < T.fetch) return onMag(newMag(T.fetch));
        if (t < T.seat + 0.04) return onMag(newMag(t));
        return mixT(onMag(newMag(T.seat)), leftOnGun(H(t)), move(t, T.seat + 0.04, T.regrip));
    };
    const camera = t => sum(mul(sum(mul(kc(kt(t + cut)), into(t)), mul(hits(t), 0.35), hit(t, T.seat, [0.7, 0, 0.25]),
        empty ? hit(t, T.let + 0.03, [-0.45, 0, 0.18]) : Z), ads ? ADS_HIT : 1), tier && !ads ? headDown(t + cut, tier) : Z);
    const RH = empty ? c.RH : t => bentRight(H(t).rot);
    const tracks = {
        handling: {position: t => H(t).pos, rotation: t => H(t).rot},
        magazine: hiddenFrom(noMag ? 0 : T.out0, T.seat),
        empty_old_mag: noMag ? {scale: () => [0, 0, 0], step: true}
            : {position: t => oldMag(t).pos, rotation: t => oldMag(t).rot, ...shown(T.out0, empty ? T.out0 + 0.75 : T.gone)},
        mag_out: {position: t => newMag(t).pos, rotation: t => newMag(t).rot},
        reload_magazine: shown(T.fetch, T.seat),
        mag_latch: {rotation: t => latch(t, noMag ? {...T, release: -1, out1: -1} : T)},
        righthand: {position: t => RH(t).pos, rotation: t => RH(t).rot},
        lefthand: lhTracks(LH),
        camera: {rotation: camera},
        ...(empty ? {...mechT(c.op), bolt_lock: {rotation: c.lock}} : mech(0)),
    };
    const sounds = [...(noMag ? [] : [[T.release - 0.02, 'magazine_release'], [T.out0, 'magazine_out']]), [T.seat - 0.28, 'magazine_in'], [T.seat - SEAT_LEAD, 'magazine_seat'],
        ...(empty ? [[T.pull - 0.01, 'charging_back'], [T.let - 0.01, 'charging_release']] : [])];
    return {tracks, sounds, timing: T};
}
clips.push(clip('reload_tactical', RELOAD.tactical.end, 'once', hand => reloadKeyed(hand, false)));
clips.push(clip('reload_empty', RELOAD.empty.end, 'once', hand => reloadKeyed(hand, true)));
clips.push(clip('reload_tactical_no_mag', RELOAD.tacticalNoMag.end, 'once', hand => reloadKeyed(hand, false, true)));
clips.push(clip('reload_empty_no_mag', RELOAD.emptyNoMag.end, 'once', hand => reloadKeyed(hand, true, true)));
// ---------- proficiency tiers (round 3; docs/native_guns/br51_animation_redo_v1.md 5.8; NativeReloadTiers) ----------
// Below the practised tier the server plays <clip>_experienced (Lv 4-6) or <clip>_novice (Lv 0-3), a novice sometimes
// <clip>_novice_fumble; aimed from Lv 5, <clip>_experienced_ads. Not keyed again: the practised clip's hand keys played on the
// tier's own beats (a piecewise-linear time map through the beats, `warp`), the solvers on those beats, plus what the tier adds:
// the eyes down on the work (the camera bone, not aimed), a novice's second look at the seated magazine, a fumble's crooked
// magazine that stops on the magwell edge and goes in on the second try. Up to the old magazine off the catch (unhook) every
// tier keeps the practised beats, so the no-magazine tiers cut the same NO_MAG_CUT as the practised one (the server's
// noMagReloadCut, the third person's fallback) and the old magazine comes out as fast; the slowness is in the fetch, the
// aim at the catch, the rock home and the rack.
const KEEP = {grab: 0.36, release: 0.42, out0: 0.45, out1: 0.62, unhook: 0.68};
export const FUMBLE = 1.0;   // the fumble's extra time: everything from the second try at the catch on is this much later
// the fumble's moments (fractions of FUMBLE from the first try): pushed up crooked and stopped, shoved again, taken back down, lined up
const FUMBLE_AT = {stop: 0.28, bump: 0.28, shove: 0.48, down: 0.75, lined: 1.0};
export const TIER_T = {
    experienced: {
        tactical: {...KEEP, gone: 1.05, fetch: 1.2, entry: 1.75, hook: 1.86, seat: 2.16, back: 2.32, regrip: 2.5, backEnd: 2.8, end: 3.0},
        empty: {...KEEP, gone: 1.05, fetch: 1.2, entry: 1.75, hook: 1.86, seat: 2.16, regrip: 2.38, rhOff: 2.4, rhOn: 2.56, pull: 2.6,
            pulled: 2.84, let: 2.88, rhBack: 2.93, rhHome: 3.18, back: 2.98, backEnd: 3.4, end: 3.6},
    },
    novice: {
        tactical: {...KEEP, gone: 1.15, fetch: 1.4, entry: 2.05, hook: 2.25, seat: 2.6, lookA: 2.66, lookB: 2.96, back: 3.0, regrip: 3.25,
            backEnd: 3.65, end: 3.9},
        empty: {...KEEP, gone: 1.15, fetch: 1.4, entry: 2.05, hook: 2.25, seat: 2.6, lookA: 2.66, lookB: 2.9, regrip: 2.95, rhOff: 2.98,
            rhOn: 3.2, pull: 3.26, pulled: 3.6, let: 3.65, rhBack: 3.72, rhHome: 4.02, back: 3.78, backEnd: 4.4, end: 4.7},
    },
};
// fumble: the novice beats with the first try at the catch at `miss` (the novice's entry), the second FUMBLE later
const fumbled = T => ({...T, ...Object.fromEntries(Object.entries(T).filter(([, v]) => v >= T.entry).map(([k, v]) => [k, +(v + FUMBLE).toFixed(3)])),
    miss: T.entry});
TIER_T.fumble = {tactical: fumbled(TIER_T.novice.tactical), empty: fumbled(TIER_T.novice.empty)};
// experienced while aiming: the experienced beats, back on the sights right after the seat / the rack (as the practised ADS clip)
TIER_T.experiencedAds = {
    tactical: {...TIER_T.experienced.tactical, back: 2.2, backEnd: 2.46},
    empty: {...TIER_T.experienced.empty, back: 2.93, backEnd: 3.2},
};
// the time map tier -> practised through the beats both have; inside a fumble the practised keys wait at their entry
const WARP_KEYS = ['unhook', 'gone', 'fetch', 'entry', 'hook', 'seat', 'rhOn', 'let', 'rhHome', 'back', 'backEnd', 'end'];
function warpOf(T, P) {
    const pts = [[0, 0]];
    for (const k of WARP_KEYS) if (T[k] != null && P[k] != null) {
        if (k === 'entry' && T.miss != null) pts.push([T.miss, P.entry]);
        pts.push([T[k], P[k]]);
    }
    pts.sort((a, b) => a[0] - b[0]);
    for (let i = 1; i < pts.length; i++) assert(pts[i][1] >= pts[i - 1][1], 'warp not monotonic at ' + pts[i][0]);
    return t => {
        if (t <= 0) return 0;
        for (let i = 1; i < pts.length; i++) if (t <= pts[i][0]) {
            const [a, pa] = pts[i - 1], [b, pb] = pts[i]; return b > a ? pa + (pb - pa) * (t - a) / (b - a) : pb;
        }
        return pts.at(-1)[1] + (t - pts.at(-1)[0]);
    };
}
// eyes down on the work (camera bone x-: down) from the grab, up again with the gun; a novice further, and once more onto the
// seated magazine; the aimed reloads keep the eyes on the sights
const HEAD_DOWN = {experienced: 6.5, novice: 13, fumble: 13};
function headDown(t, tier) {
    const T = tier.T, d = HEAD_DOWN[tier.name];
    const down = move(t, T.grab - 0.12, T.unhook) - move(t, T.back, T.backEnd - 0.1);
    const again = T.lookA != null ? move(t, T.lookA - 0.05, T.lookA + 0.12) - move(t, T.lookB - 0.1, T.lookB + 0.06) : 0;
    return [-(d * down + 2.5 * again), 0, 0];
}
// the fumble's magazine (u 0..1 over FUMBLE from the first try): pushed straight up without its front lug in the catch, rolled
// a little and off to one side, it stops on the magwell edge; shoved once more; taken back down and lined up for the second try
function fumbleMag(h, u) {
    const lined = {a: MAG_ROCK, o: MAG_UNHOOK, c: 0};
    const crooked = {a: MAG_ROCK * 0.3, o: add(mul(MAG_UNHOOK, 0.45), [0.7, 0, 0]), c: 13};
    const low = {a: MAG_ROCK * 1.1, o: mul(MAG_UNHOOK, 1.5), c: 3};
    const shove = 0.12 * (move(u, FUMBLE_AT.shove - 0.06, FUMBLE_AT.shove) - move(u, FUMBLE_AT.shove + 0.02, FUMBLE_AT.shove + 0.12));
    const legs = [[0, FUMBLE_AT.stop, lined, crooked], [FUMBLE_AT.shove + 0.12, FUMBLE_AT.down, crooked, low],
        [FUMBLE_AT.down + 0.04, FUMBLE_AT.lined, low, lined]];
    let m = lined;
    for (const [a, b, from, to] of legs) if (u >= a) {
        const w = move(u, a, b);
        m = {a: from.a + (to.a - from.a) * w, o: lerp(from.o, to.o, w), c: from.c + (to.c - from.c) * w};
    }
    return magInGun(h, m.a, add(m.o, [0, shove, 0]), m.c);
}
// clip names: reload_<tactical|empty>[_no_mag]_<experienced|novice|novice_fumble>[_ads] (NativeReloadTiers, the P9's tierClip)
const TIER_NAMES = {experienced: 'experienced', novice: 'novice', fumble: 'novice_fumble', experiencedAds: 'experienced'};
for (const [tierName, suffix] of Object.entries(TIER_NAMES)) for (const empty of [false, true]) {
    const ads = tierName === 'experiencedAds', P = RELOAD[(empty ? 'empty' : 'tactical') + (ads ? 'Ads' : '')];
    const T = TIER_T[tierName][empty ? 'empty' : 'tactical'];
    const tier = {name: ads ? 'experienced' : tierName, T, noMag: cutT(T), warp: warpOf(T, P)};
    const base = `reload_${empty ? 'empty' : 'tactical'}`, tail = `${suffix}${ads ? '_ads' : ''}`;
    clips.push(clip(`${base}_${tail}`, T.end, 'once', hand => reloadKeyed(hand, empty, false, ads, tier)));
    clips.push(clip(`${base}_no_mag_${tail}`, cutT(T).end, 'once', hand => reloadKeyed(hand, empty, true, ads, tier)));
}

clips.push(clip('reload_tactical_ads', RELOAD.tacticalAds.end, 'once', hand => reloadKeyed(hand, false, false, true)));
clips.push(clip('reload_empty_ads', RELOAD.emptyAds.end, 'once', hand => reloadKeyed(hand, true, false, true)));
clips.push(clip('reload_tactical_no_mag_ads', RELOAD.tacticalNoMagAds.end, 'once', hand => reloadKeyed(hand, false, true, true)));
clips.push(clip('reload_empty_no_mag_ads', RELOAD.emptyNoMagAds.end, 'once', hand => reloadKeyed(hand, true, true, true)));

// Inspect: both hands stay on the rifle (4.4 kg): brought in and turned to show the left side (bolt lock, receiver flat),
// rolled over to show the right (the operating rod and its handle; empty: the action held open), then the magazine check:
// the support hand rocks the magazine out and brings it up and away from the gun to the left of the screen, held at a
// reading distance (user 2026-10-27: 「检视的弹匣那边还是老问题，离枪太近了根本看不见细节，看看能不能做成p9那样」; the P9's
// magazine check, docs/dev/p9_reload_inspect_v1.md 2.4): one side, held; turned so the top (the feed lips and the round) faces
// the eye, held; the other side, held; back down, hooked and rocked home. The rifle rests a little lower meanwhile.
// noMag (no magazine fitted): the same sides, then tipped up to look into the empty magwell; no magazine work or sounds.
// MAG_LOOK: the magazine (mag_out, root space about its pivot) at each look: [the moment it is there, its pose]
// user 2026-10-27: 「弹匣不用看底面，主要是看侧面、压弹什么的」: the two sides, and the top turned three-quarters to the eye (the
// round on the follower and a side at once); close enough to read the ribs
const MAG_LOOK_DEFAULT = {side: {pos: [-12, 5, 10], rot: [10, 70, 0]}, top: {pos: [-11, 5, 10], rot: [50, 45, 0]}, other: {pos: [-12, 5, 10], rot: [10, -70, 0]}};
// the support hand under the base plate (its cap on the plate's middle, magazine px), the forearm down and out left in view
const MAG_BASE_PT = [0, 3.6, 3.5], SHOW_FOREARM = [-0.35, -1, 0.35], SHOW_PALM = [1, 0, 0.3];
const onMagBase = m => handAt(magPointWorld(m, MAG_BASE_PT), E(levelArm(SHOW_FOREARM, SHOW_PALM)));
const MAG_LOOK = process.env.BR51_MAG ? {...MAG_LOOK_DEFAULT, ...JSON.parse(process.env.BR51_MAG)} : MAG_LOOK_DEFAULT;
export const INSPECT_LENGTH = {full: 9.8, empty: 9.8, noMag: 5.0, emptyNoMag: 5.0};
export const INSPECT_T = {up: 0.7, turn: 1.9, turned: 2.6, check: 3.3, checked: 3.6, grab: 3.55, release: 3.62, out0: 3.66, out1: 3.92,
    unhook: 4.02, show: 4.6, sideEnd: 5.4, top: 5.9, topEnd: 6.7, other: 7.2, showEnd: 7.9, entry: 8.4, hook: 8.5, seat: 8.75, regrip: 9.1,
    back: 8.85, backEnd: 9.4, end: 9.8};
export const INSPECT_NO_MAG_T = {up: 0.7, turn: 1.9, turned: 2.6, check: 3.3, checked: 3.6, back: 4.15, backEnd: 4.7};
function inspectKeyed(hand, empty, noMag = false) {
    const T = noMag ? INSPECT_NO_MAG_T : INSPECT_T;
    const kr = hand.ch('handling', 'rotation'), kp = hand.ch('handling', 'position'), kc = hand.ch('camera', 'rotation');
    const hits = t => noMag ? Z : sum(hit(t, T.release, [-0.4, 0, 0.28]), hit(t, T.out1, [-0.6, 0.15, 0.45], {rise: 0.03}),
        hit(t, T.hook, [0.45, -0.15, 0.2]), hit(t, T.seat, [1.6, -0.3, 0.7], {rise: 0.017, tau: 0.05}));
    const H = t => { const h = hits(t); return {rot: add(kr(t), h), pos: add(kp(t), mul([0, 0.08, 0], h[0]))}; };
    // held up at the left: brought up to one side, held, turned to the top, held, to the other side, held
    const showAt = (t, out) => {
        const L = MAG_LOOK, legs = [[T.unhook, T.show, out, L.side], [T.sideEnd, T.top, L.side, L.top], [T.topEnd, T.other, L.top, L.other]];
        let m = out;
        for (const [a, b, from, to] of legs) if (t >= a) m = mixM(from, to, move(t, a, b));
        return m;
    };
    const held = t => {
        if (noMag) return magInGun(H(t));
        if (t < T.unhook) { const r = rockOut(t, T); return magInGun(H(t), r.a, r.o); }
        const out = (() => { const r = rockOut(T.unhook, T); return magInGun(H(T.unhook), r.a, r.o); })();
        if (t < T.showEnd) return showAt(t, out);
        if (t < T.entry) { const r = rockIn(T.entry, T); return mixM(showAt(T.showEnd, out), magInGun(H(t), r.a, r.o), move(t, T.showEnd, T.entry)); }
        const r = rockIn(Math.min(t, T.seat), T); return magInGun(H(t), r.a, r.o);
    };
    const LH = t => {
        if (noMag || t < T.check - 0.15) return leftOnGun(H(t));
        if (t < T.grab) return mixT(leftOnGun(H(t)), onMag(held(t), 0.6), move(t, T.check - 0.15, T.grab));
        // up at the left the hand moves from the side of the magazine to under its base plate (the P9's), the forearm hanging
        // down out of the screen whichever way the magazine turns, so neither hides it
        if (t < T.seat + 0.04) return mixT(onMag(held(t)), onMagBase(held(t)), move(t, T.unhook, T.show) - move(t, T.showEnd, T.entry));
        return mixT(onMag(held(T.seat)), leftOnGun(H(t)), move(t, T.seat + 0.04, T.regrip));
    };
    const camera = t => sum(kc(t), mul(hits(t), 0.35), noMag ? Z : hit(t, T.seat, [0.6, 0, 0.22]));
    const RH = t => bentRight(H(t).rot);
    const tracks = {
        handling: {position: t => H(t).pos, rotation: t => H(t).rot},
        magazine: noMag ? {scale: () => [1, 1, 1], step: true} : hiddenFrom(T.out0, T.seat),
        mag_out: {position: t => held(t).pos, rotation: t => held(t).rot},
        reload_magazine: noMag ? {scale: () => [0, 0, 0], step: true} : shown(T.out0, T.seat),
        empty_old_mag: {scale: () => [0, 0, 0], step: true},
        mag_latch: {rotation: t => noMag ? Z : latch(t, T)},
        righthand: {position: t => RH(t).pos, rotation: t => RH(t).rot},
        lefthand: lhTracks(LH),
        ...mech(empty ? OP_HELD : 0), bolt_lock: {rotation: () => empty ? [BOLT_LOCK_UP, 0, 0] : Z},
        camera: {rotation: camera},
    };
    const sounds = noMag ? [] : [[T.release - 0.02, 'magazine_release'], [T.out0, 'magazine_out'], [T.seat - 0.28, 'magazine_in'], [T.seat - SEAT_LEAD, 'magazine_seat']];
    return {tracks, sounds, timing: T};
}
clips.push(clip('inspect', INSPECT_LENGTH.full, 'once', hand => inspectKeyed(hand, false)));
clips.push(clip('inspect_empty', INSPECT_LENGTH.empty, 'once', hand => inspectKeyed(hand, true)));
clips.push(clip('inspect_no_mag', INSPECT_LENGTH.noMag, 'once', hand => inspectKeyed(hand, false, true)));
clips.push(clip('inspect_empty_no_mag', INSPECT_LENGTH.emptyNoMag, 'once', hand => inspectKeyed(hand, true, true)));

// the mechanism, for the third-person actions (tools/author-br51-01-reload-actions.mjs)
export const MECH = {MAG_ROCK, MAG_UNHOOK, OP_HELD, OP_FULL, OP_HANDLE};

// ---------- hand-keyed channels ----------
// Keyed by hand in Blockbench (bezier), read back from the source. The no-magazine reloads and the empty inspects play their
// base clip's keys (KEY_SOURCE); only the base clips carry hand keys in the source, the others get the bake.
const KEYED = {handling: ['rotation', 'position'], camera: ['rotation']};
export const KEY_SOURCE = {reload_tactical: 'reload_tactical', reload_empty: 'reload_empty', reload_tactical_no_mag: 'reload_tactical',
    reload_empty_no_mag: 'reload_empty', reload_tactical_ads: 'reload_tactical_ads', reload_empty_ads: 'reload_empty_ads',
    reload_tactical_no_mag_ads: 'reload_tactical_ads', reload_empty_no_mag_ads: 'reload_empty_ads', inspect: 'inspect',
    ...Object.fromEntries(['tactical', 'empty'].flatMap(k => ['experienced', 'novice', 'novice_fumble', 'experienced_ads'].flatMap(tier => {
        const from = `reload_${k}${tier.endsWith('_ads') ? '_ads' : ''}`;
        return [[`reload_${k}_${tier}`, from], [`reload_${k}_no_mag_${tier}`, from]];
    }))), inspect_empty: 'inspect', inspect_no_mag: 'inspect_no_mag', inspect_empty_no_mag: 'inspect_no_mag'};
function handKeys(source, name) {
    const from = KEY_SOURCE[name]; if (!from) return null;
    const anim = source.animations?.find(a => a.name === from);
    const byBone = new Map(Object.values(anim?.animators ?? {}).map(a => [a.name, a]));
    const kf = {};
    for (const [bone, chs] of Object.entries(KEYED)) for (const ch of chs) {
        const keys = (byBone.get(bone)?.keyframes ?? []).filter(k => k.channel === ch);
        assert(keys.some(k => k.interpolation === 'bezier'), `${from}: ${bone}.${ch} has no hand keys in the source (seed them in Blockbench first)`);
        assert(!byBone.get(bone).quaternion_interpolation, `${from}: ${bone} uses quaternion interpolation`);
        (kf[bone] ??= {})[ch] = keys;
    }
    return {kf, own: from === name, ch: (bone, ch) => bbChannel(kf[bone][ch])};
}

// ---------- unload (docs/native_guns/magazine_system_v1.md 3.1) ----------
// A tap of the reload key with a magazine fitted: the magazine check up to the catch, the magazine rocked out and taken
// down off the lower left to the rig while the gun comes back to the ready one-handed. The server stows it at
// NativeGunActions.UNLOAD_STOW_TICK (18 = 0.9 s: rig, pockets, backpack, else the ground; user 2026-10-24: the gun stays in
// the hand), the hand comes back onto the handguard and the clip ends at the ready. unload_empty: the action held open.
export const UNLOAD = {up: 0.3, grab: 0.28, release: 0.34, out0: 0.38, out1: 0.54, unhook: 0.6, carried: 0.9, home: 0.62, homeEnd: 1.0,
    stow: 0.9, back: 0.95, regrip: 1.3, end: 1.45};
function unload(empty) {
    const T = UNLOAD;
    const path = legs([[0.02, T.up, MAG_CHECK, {antic: 0.03, over: 0.04, per: 0.22, tau: 0.08}], [T.home, T.homeEnd, IDLE, {antic: 0, over: 0}]]);
    const hits = t => sum(jolt(t, T.release, [-0.5, 0, 0.35]), jolt(t, T.out1, [-0.8, 0.2, 0.6], {rise: 0.03, per: 0.16, tau: 0.05}));
    const H = t => { const p = path(t), rot = sum(p.rot, hits(t)); return {rot, pos: sum(p.pos, mul([0, 0.08, 0], hits(t)[0]))}; };
    const held = t => {
        if (t < T.unhook) { const r = rockOut(t, T); return magInGun(H(t), r.a, r.o); }
        const r = rockOut(T.unhook, T); return mixM(magInGun(H(T.unhook), r.a, r.o), OFFSCREEN_MAG, go(t, T.unhook, T.carried, {antic: 0.02, over: 0}));
    };
    const LH = t => t < T.grab ? mixT(leftOnGun(H(t)), onMag(held(t), 0.6), go(t, 0.02, T.grab, {antic: 0, over: 0}))
        : t < T.back ? onMag(held(t)) : mixT(onMag(held(T.back)), leftOnGun(H(t)), go(t, T.back, T.regrip, {antic: 0, over: 0}));
    const camera = t => sum(mul([-0.8, 1.0, 1.2], ease(t, 0.02, T.up) - ease(t, T.home, T.homeEnd)), mul(hits(t), 0.35));
    const tracks = {
        handling: {position: t => H(t).pos, rotation: t => H(t).rot},
        magazine: hiddenFrom(T.out0, 99),
        mag_out: {position: t => held(t).pos, rotation: t => held(t).rot},
        reload_magazine: shown(T.out0, T.stow),
        empty_old_mag: {scale: () => [0, 0, 0], step: true},
        mag_latch: {rotation: t => latch(t, {...T, seat: 99})},
        righthand: {position: () => RIGHT_REST.pos, rotation: () => RIGHT_REST.rot},
        lefthand: lhTracks(LH),
        ...mech(empty ? OP_HELD : 0), bolt_lock: {rotation: () => empty ? [BOLT_LOCK_UP, 0, 0] : Z},
        camera: {rotation: camera},
    };
    const sounds = [[T.release - 0.02, 'magazine_release'], [T.out0, 'magazine_out']];
    return {tracks, sounds, timing: T};
}
clips.push(clip('unload', UNLOAD.end, 'once', () => unload(false)));
clips.push(clip('unload_empty', UNLOAD.end, 'once', () => unload(true)));

// ---------- bake (as the P9 V3 script) ----------
const round = v => +v.toFixed(4);
function bake(fn, length, step, rotRef = null) {
    const n = Math.round(length * FPS), keys = [];
    let prev = rotRef;
    for (let i = 0; i <= n; i++) {
        const t = round(Math.min(length, i / FPS)); let v = fn(t);
        if (prev) { v = unwrap(v, prev); prev = v; }
        if (prev && keys.length) {
            const refine = (ta, va, tb, vb, depth) => {
                if (Math.max(...va.map((x, j) => Math.abs(x - vb[j]))) <= 6 || depth > 6) return [];
                const tm = (ta + tb) / 2, vm = unwrap(fn(tm), va);
                return [...refine(ta, va, tm, vm, depth + 1), [tm, vm], ...refine(tm, vm, tb, vb, depth + 1)];
            };
            const [tp, vp] = keys.at(-1);
            for (const [tm, vm] of refine(tp, vp, t, v, 0)) keys.push([+tm.toFixed(5), vm.map(round)]);
        }
        keys.push([t, v.map(round)]);
    }
    const out = [keys[0]];
    for (let i = 1; i < keys.length - 1; i++) {
        const [ta, a] = out.at(-1), [t, v] = keys[i], [tb, b] = keys[i + 1];
        const same = step ? v.every((x, k) => x === a[k]) : v.every((x, k) => Math.abs(a[k] + (b[k] - a[k]) * (t - ta) / (tb - ta) - x) < 2e-4);
        if (!same) out.push(keys[i]);
    }
    if (keys.length > 1) out.push(keys.at(-1));
    return step ? out.filter((k, i) => i === 0 || i === out.length - 1 || !k[1].every((x, j) => x === out[i - 1][1][j])) : out;
}

// ---------- audit: where things land on screen (hip display of models/item/br51_01_v3_native_in_hand.json) ----------
// view = T(hip/16) * Ry(4) * S(0.45) * p/16; screen = (x, y) / -z against the 70 deg FOV half extents (16:9: 1.245, 0.70)
const HIP = [3.8, -7.2, -11.5], HIP_YAW = 4, HIP_SCALE = 0.45;
const toView = p => add(mul(HIP, 1 / 16), mul(mv(E([0, HIP_YAW, 0]), mul(p, 1 / 16)), HIP_SCALE));
const screen = p => { const v = toView(p); return v[2] >= -0.05 ? null : [+(v[0] / -v[2] / 1.245).toFixed(2), +(v[1] / -v[2] / 0.7).toFixed(2)]; };

export function build() {
    const source = JSON.parse(fs.readFileSync(sourcePath, 'utf8'));
    const groups = new Map(source.groups.map(g => [g.name, g]));
    const bb = [], runtime = {format_version: '1.8.0', animations: {}};
    const uuid = s => { const h = createHash('sha256').update('br51-01-v3-native-anim:' + s).digest('hex'); return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-8${h.slice(17, 20)}-${h.slice(20, 32)}`; };
    const stats = {}, lhChoice = {}, timings = {};
    const idleRot = new Map(Object.entries(clips.find(c => c.name === 'static_idle').build().tracks).filter(([, ch]) => ch.rotation).map(([b, ch]) => [b, ch.rotation(0)]));
    idleRot.set('righthand', RIGHT_REST.rot);
    const lhIdle = leftOnGun(IDLE).rot;
    for (const c of clips) {
        const hand = handKeys(source, c.name);
        const {tracks, sounds = [], timing} = c.build(hand);
        if (timing) timings[c.name] = timing;
        if (!tracks.righthand) tracks.righthand = {position: () => RIGHT_REST.pos, rotation: () => RIGHT_REST.rot};
        if (!tracks.lefthand) tracks.lefthand = lhTracks(() => leftOnGun(IDLE));
        for (const [bone, ch] of Object.entries(HIDE_HELPERS)) if (!tracks[bone]) tracks[bone] = ch;
        // every clip drives the action, so a clip never inherits a held-open rod from the last
        if (!tracks.oprod) Object.assign(tracks, mech(0));
        if (!tracks.bolt_lock) tracks.bolt_lock = {rotation: () => Z};
        {   // gimbal guard for the support hand: constant per-clip pre-rotation on lefthand_pos
            const solved = tracks.lefthand.solved; let best = null;
            for (const cand of LH_POS_CANDIDATES) {
                const inv = T3(E(cand)), fn = t => euler(mm(E(solved(t)), inv));
                const keys = bake(fn, c.length, false, euler(mm(E(lhIdle), inv)));
                let worst = 0; for (let i = 1; i < keys.length; i++) worst = Math.max(worst, ...keys[i][1].map((v, j) => Math.abs(v - keys[i - 1][1][j])));
                if (!best || worst < best.worst - 1e-6) best = {cand, fn, worst, ref: euler(mm(E(lhIdle), inv))};
            }
            tracks.lefthand = {position: tracks.lefthand.position, rotation: best.fn, ref: best.ref, solved};
            tracks.lefthand_pos = {rotation: () => best.cand};
            lhChoice[c.name] = {pre: best.cand, worstStep: +best.worst.toFixed(2)};
        }
        const anim = {uuid: uuid(c.name), name: c.name, loop: c.loop, override: false, length: c.length, snapping: FPS,
            selected: false, anim_time_update: '', blend_weight: '', start_delay: '', loop_delay: '', animators: {}};
        const out = {animation_length: c.length, bones: {}};
        if (c.loop === 'loop') out.loop = true; else if (c.loop === 'hold') out.loop = 'hold_on_last_frame';
        let count = 0;
        for (const [bone, chs] of Object.entries(tracks)) {
            const g = groups.get(bone); assert(g, 'Missing bone ' + bone); assert(g.export !== false, 'Animated editor-only bone ' + bone);
            const animator = {name: bone, type: 'bone', keyframes: []}, rt = {};
            for (const ch of ['rotation', 'position', 'scale']) {
                if (!chs[ch]) continue;
                const step = ch === 'scale' && chs.step;
                const ref = ch === 'rotation' ? (chs.ref ?? idleRot.get(bone) ?? Z) : null;
                const keys = bake(chs[ch], c.length, step, ref), track = {}, handCh = hand?.own ? hand.kf[bone]?.[ch] : null;
                if (handCh) animator.keyframes.push(...handCh);   // the source keeps the hand keys; the runtime gets the bake
                keys.forEach(([t, v], i) => {
                    if (!handCh) animator.keyframes.push({channel: ch, data_points: [{x: String(v[0]), y: String(v[1]), z: String(v[2])}],
                        uuid: uuid(`${c.name}:${bone}:${ch}:${t}`), time: t, color: -1, interpolation: step ? 'step' : 'linear'});
                    const sign = ch === 'rotation' ? [-1, -1, 1] : ch === 'position' ? [-1, 1, 1] : [1, 1, 1];
                    const value = {vector: v.map((x, k) => round(x * sign[k]) || 0)};
                    if (step && i) value.easing = 'afl_hold';
                    const time = t > 0 && Number.isInteger(t) ? t.toFixed(1) : String(t);
                    track[time] = value;
                });
                rt[ch] = track; count += keys.length;
            }
            anim.animators[g.uuid] = animator; out.bones[bone] = rt;
        }
        for (const [, s] of sounds) assert(fs.existsSync(path.join(soundDir, `${s}.ogg`)), 'Missing sound ' + s);
        if (sounds.length) {
            anim.animators.effects = {name: 'Effects', type: 'effect', keyframes: sounds.map(([t, s]) => ({channel: 'sound',
                data_points: [{effect: NS + s, file: path.join(soundDir, `${s}.ogg`)}], uuid: uuid(`${c.name}:sound:${t}`), time: round(t), color: -1, interpolation: 'linear'}))};
            out.sound_effects = Object.fromEntries(sounds.sort((a, b) => a[0] - b[0]).map(([t, s]) => [t === 0 ? '0.0' : String(round(t)), {effect: NS + s}]));
        }
        bb.push(anim); runtime.animations[c.name] = out;
        // audit: the hands' caps and the muzzle on screen, and the fastest gun turn (deg/s)
        const H = tracks.handling, LHs = tracks.lefthand.solved, LHp = tracks.lefthand.position;
        const at = t => {
            const h = H ? {pos: H.position(t), rot: H.rotation(t)} : IDLE;
            const lh = {pos: LHp(t), rot: LHs(t)}, leftCap = add(add(lh.pos, LP), mv(E(lh.rot), sub(LA, LP)));
            const rh = {pos: tracks.righthand.position(t), rot: tracks.righthand.rotation(t)};
            const rightCap = handleWorld(h, add(add(rh.pos, RP), mv(E(rh.rot), sub(HP, RP))));
            return {left: screen(leftCap), right: screen(rightCap), muzzle: screen(handleWorld(h, [0, 11.4375, -28]))};
        };
        let turn = 0;
        if (H) for (let i = 1; i <= Math.round(c.length * FPS); i++) {
            const a = H.rotation((i - 1) / FPS), b = H.rotation(i / FPS); turn = Math.max(turn, Math.hypot(...sub(b, a)) * FPS);
        }
        const probe = timing ? Object.fromEntries(['grab', 'out1', 'gone', 'entry', 'seat', 'rhOn', 'pulled', 'show', 'turned'].filter(k => timing[k] != null).map(k => [k, at(timing[k])])) : {};
        stats[c.name] = {length: c.length, bones: Object.keys(tracks).length, keys: count, maxTurnDegPerS: Math.round(turn),
            sounds: sounds.map(([t, s]) => `${round(t)} ${s}`), rest: at(0), ...(Object.keys(probe).length ? {probe} : {})};
    }
    return {bb, runtime, stats, lhChoice, timings};
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
    const args = process.argv.slice(2), {bb, runtime, stats, lhChoice} = build();
    if (args.includes('--write-source')) {
        const source = JSON.parse(fs.readFileSync(sourcePath, 'utf8'));
        source.animations = bb; fs.writeFileSync(sourcePath, JSON.stringify(source));
    }
    const runtimeText = JSON.stringify(runtime, null, 2) + '\n';
    if (args.includes('--write-runtime')) fs.writeFileSync(runtimePath, runtimeText);
    if (args.includes('--check') && (!fs.existsSync(runtimePath) || fs.readFileSync(runtimePath, 'utf8').replace(/\r\n/g, '\n') !== runtimeText)) {
        console.log('STALE ' + path.relative(root, runtimePath)); process.exit(1);
    }
    console.log(JSON.stringify({stats, lhChoice}, null, 1));
}
