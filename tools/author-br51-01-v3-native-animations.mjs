// BR-51 V3 AFL Native first-person animation authoring (docs/native_guns/br51_01_v3_game_model_v1.md, "动画"). The M14
// mechanism (user 2026-10-21: "原版外观 + M14 正确机构"): an operating rod on the right whose handle the right hand racks,
// a rotating bolt that unlocks (turns about the bore) before it travels, a bolt lock that holds the action open on an empty
// magazine, and a magazine that hooks its front lug into the receiver's front catch and rocks in and out about it, with the
// latch behind it pressed forward by the support thumb. Normal-tier clips only (no ADS variants): the P9 V3 script's
// solvers, bake and motion language (tools/author-p9-01-v3-native-animations.mjs), re-calibrated for a shouldered rifle.
//   node tools/author-br51-01-v3-native-animations.mjs                 -> audit/summary only
//   --write-source     write the clips into src/main/blockbench/br51_01_v3_native.bbmodel
//   --write-runtime    write assets/.../animations/br51_01_v3_native.animation.json
import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {ARM_HALF, MP, HP, RP, LP, LA, nativePath as sourcePath} from './build-br51-01-v3-native.mjs';

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
function magInGun(h, a = 0, o = Z) {
    const R = mm(E(h.rot), E([a, 0, 0]));
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
// Rolled right about the wrist so the magwell comes left into view, a little up and the muzzle a little left.
const RELOAD_TILT = {pos: [-1.2, 1.6, 0.8], rot: [6, 10, -26]};
const RELOAD_MEET = {pos: [-1.6, 1.2, 1.0], rot: [4, 13, -30]};   // drifted down-left toward the incoming magazine
// Rolled left so the operating rod handle on the right side faces up into view for the firing hand.
const CHARGE_TILT = {pos: [-0.6, 1.0, 0.6], rot: [5, -6, 16]};
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

// ---------- reloads (normal tier) ----------
// Tactical: the support hand leaves the forend for the magazine, the thumb presses the latch forward, the magazine is
// rocked forward off its front catch and taken down to the belt; a fresh one comes up, its front lug is hooked into the
// catch and it is rocked back until the latch clicks (magazine_in's click 0.28 s into the file lands on the seat), the
// hand goes back to the forend. Empty: the empty magazine is rocked out and let fall, and after the seat the firing hand
// racks the rod off the bolt lock (charging_back / charging_release).
export const RELOAD = {
    tactical: {grab: 0.36, release: 0.42, out0: 0.45, out1: 0.62, unhook: 0.68, gone: 0.95, fetch: 1.05, entry: 1.4, hook: 1.47, seat: 1.7,
        regrip: 2.02, back: 1.84, backEnd: 2.24, end: 2.4},
    empty: {grab: 0.36, release: 0.42, out0: 0.45, out1: 0.62, unhook: 0.68, gone: 0.95, fetch: 1.05, entry: 1.4, hook: 1.47, seat: 1.7,
        regrip: 1.96, rhOff: 1.84, rhOn: 2.02, pull: 2.05, pulled: 2.27, let: 2.3, rhBack: 2.36, rhHome: 2.6, charge: 1.8, back: 2.32, backEnd: 2.72, end: 2.9},
};
// No magazine in the gun (reload_*_no_mag; ConfiguredNativeGunItem#reloadClip): the support hand goes straight from the
// forend to the belt for the new magazine. Everything from the fetch on is the reload's, NO_MAG_CUT s earlier, so the
// length and the magazine-in tick move by the same whole ticks (the game takes the cut from the two clip lengths).
export const NO_MAG_CUT = 0.65;
const cutT = T => Object.fromEntries(Object.entries(T).filter(([k]) => !['grab', 'release', 'out0', 'out1', 'unhook', 'gone'].includes(k))
    .map(([k, v]) => [k, +(v - NO_MAG_CUT).toFixed(3)]));
RELOAD.tacticalNoMag = cutT(RELOAD.tactical);
RELOAD.emptyNoMag = cutT(RELOAD.empty);
const rockOut = (t, T) => ({a: MAG_ROCK * ease(t, T.out0, T.out1), o: mul(MAG_UNHOOK, ease(t, T.out1 - 0.03, T.unhook))});
const rockIn = (t, T) => ({a: MAG_ROCK * (1 - ease(t, T.hook, T.seat)), o: mul(MAG_UNHOOK, 1 - ease(t, T.entry, T.hook))});
const latch = (t, T) => t < T.release - 0.03 ? Z : t < T.out1 + 0.04 ? [LATCH_PRESS * ease(t, T.release - 0.03, T.release), 0, 0]
    : t < T.seat - 0.06 ? [LATCH_PRESS * (1 - ease(t, T.out1 + 0.04, T.out1 + 0.1)), 0, 0]
    : t < T.seat ? [LATCH_PRESS * 0.7 * ease(t, T.seat - 0.06, T.seat - 0.01), 0, 0] : [LATCH_PRESS * 0.7 * (1 - clamp01((t - T.seat) / 0.02)), 0, 0];
function reload(empty, noMag = false) {
    const T = noMag ? (empty ? RELOAD.emptyNoMag : RELOAD.tacticalNoMag) : empty ? RELOAD.empty : RELOAD.tactical;
    const path = legs(empty ? [
        [0.0, 0.32, RELOAD_TILT, {antic: 0.03, over: 0.04, per: 0.24, tau: 0.08}],
        [0.38, T.hook, RELOAD_MEET, {}],
        [T.charge, T.rhOn, CHARGE_TILT, {antic: 0.02, over: 0.03}],
        [T.back, T.backEnd, IDLE, {antic: 0.03, over: 0.07, per: 0.26, tau: 0.09}]] : [
        [0.0, 0.32, RELOAD_TILT, {antic: 0.03, over: 0.04, per: 0.24, tau: 0.08}],
        [0.38, T.hook, RELOAD_MEET, {}],
        [T.back, T.backEnd, IDLE, {antic: 0.03, over: 0.07, per: 0.26, tau: 0.09}]]);
    const c = empty ? charge(T, OP_HELD) : null;
    const hits = t => sum(
        noMag ? Z : jolt(t, T.release, [-0.5, 0.2, 0.4]),                                // thumb on the latch
        noMag ? Z : jolt(t, T.out1, [-1.0, 0.3, 0.8], {rise: 0.03, per: 0.16, tau: 0.05}),   // the magazine comes off the catch
        jolt(t, T.hook, [0.8, -0.2, 0.3], {rise: 0.02, per: 0.12, tau: 0.04}),           // front lug into the catch
        jolt(t, T.seat, [2.2, -0.5, 1.0], {rise: 0.017, per: 0.12, tau: 0.05}),          // rocked home, the latch clicks
        empty ? c.hits(t) : Z,
        jolt(t, T.backEnd, [0.6, 0, -0.25], {per: 0.22, tau: 0.08}));
    const look = t => sum(drift(t, 0.4, T.hook, [0.8, -1.0, 1.2], 1.0), drift(t, T.seat + 0.05, T.back + 0.05, [0.5, 0.6, -0.6], 0.55));
    const H = t => { const p = path(t), rot = sum(p.rot, look(t), hits(t)); return {rot, pos: sum(p.pos, mul([0, 0.08, -0.05], hits(t)[0]))}; };
    // the old magazine: rocked out in the hand, then (tactical) taken down to the belt, (empty) let fall with its velocity
    const oldInGun = t => { const r = rockOut(t, T); return magInGun(H(t), r.a, r.o); };
    const g = 520, tFree = noMag ? 0 : T.unhook;
    const vRel = noMag ? Z : mul(sub(oldInGun(tFree).pos, oldInGun(tFree - 0.004).pos), 1 / 0.004);
    const oldMag = t => {
        if (t <= tFree) return oldInGun(t);
        if (!empty) return mixM(oldInGun(tFree), OFFSCREEN_MAG, go(t, tFree, T.gone, {antic: 0, over: 0}));
        const base = oldInGun(tFree), dt = t - tFree;
        const pos = add(add(base.pos, mul(vRel, dt)), [0, -0.5 * g * dt * dt, 0]);
        const R = mm(base.R, E([90 * dt, 10 * dt, -50 * dt])); return {pos, rot: euler(R), R};
    };
    // the new magazine: up from the belt on an arc into line with the catch, hooked, rocked home
    const newMag = t => {
        if (t < T.entry) {
            const s = clamp01(go(t, T.fetch, T.entry, {antic: 0, over: 0.03, per: 0.12, tau: 0.04})), r = rockIn(T.entry, T), g0 = magInGun(H(t), r.a, r.o);
            const m = mixM(OFFSCREEN_MAG, g0, s); m.pos = add(m.pos, mul([-1.6, 0.8, 0.5], Math.sin(Math.PI * s))); return m;
        }
        const r = rockIn(Math.min(t, T.seat), T); return magInGun(H(t), r.a, r.o);
    };
    const LH = t => {
        if (noMag && t < T.fetch) return mixT(leftOnGun(H(t)), onMag(OFFSCREEN_MAG), go(t, 0.04, T.fetch - 0.02, {antic: 0, over: 0}));
        if (t < T.grab) return mixT(leftOnGun(H(t)), onMag(oldInGun(t), 0.6), go(t, 0.04, T.grab, {antic: 0, over: 0}));
        if (t < T.gone) return !empty || t <= tFree ? onMag(oldMag(t))   // empty: let go, then down to the belt for the next one
            : mixT(onMag(oldInGun(tFree), 1.2 * ease(t, tFree, tFree + 0.08)), onMag(OFFSCREEN_MAG), go(t, tFree + 0.04, T.gone, {antic: 0, over: 0}));
        if (t < T.fetch) return onMag(newMag(T.fetch));
        if (t < T.seat + 0.04) return onMag(newMag(t));
        return mixT(onMag(newMag(T.seat)), leftOnGun(H(t)), go(t, T.seat + 0.04, T.regrip, {antic: 0.02, over: 0.04}));
    };
    const camera = t => sum(mul([-0.8, 1.0, 1.2], ease(t, 0, 0.32) - ease(t, T.back, T.backEnd)), mul(look(t), 0.3), mul(hits(t), 0.35),
        jolt(t, T.seat, [0.8, 0, 0.3]), empty ? jolt(t, T.let + 0.03, [-0.5, 0, 0.2]) : Z);
    const tracks = {
        handling: {position: t => H(t).pos, rotation: t => H(t).rot},
        magazine: hiddenFrom(noMag ? 0 : T.out0, T.seat),
        empty_old_mag: noMag ? {scale: () => [0, 0, 0], step: true}
            : {position: t => oldMag(t).pos, rotation: t => oldMag(t).rot, ...shown(T.out0, empty ? T.out0 + 0.75 : T.gone)},
        mag_out: {position: t => newMag(t).pos, rotation: t => newMag(t).rot},
        reload_magazine: shown(T.fetch, T.seat),
        mag_latch: {rotation: t => latch(t, noMag ? {...T, release: -1, out1: -1} : T)},
        righthand: empty ? {position: t => c.RH(t).pos, rotation: t => c.RH(t).rot} : {position: () => RIGHT_REST.pos, rotation: () => RIGHT_REST.rot},
        lefthand: lhTracks(LH),
        camera: {rotation: camera},
        ...(empty ? {...mechT(c.op), bolt_lock: {rotation: c.lock}} : mech(0)),
    };
    const sounds = [...(noMag ? [] : [[T.release - 0.02, 'magazine_release'], [T.out0, 'magazine_out']]), [T.seat - 0.28, 'magazine_in'],
        ...(empty ? [[T.pull - 0.01, 'charging_back'], [T.let - 0.01, 'charging_release']] : [])];
    return {tracks, sounds, timing: T};
}
clips.push(clip('reload_tactical', RELOAD.tactical.end, 'once', () => reload(false)));
clips.push(clip('reload_empty', RELOAD.empty.end, 'once', () => reload(true)));
clips.push(clip('reload_tactical_no_mag', RELOAD.tacticalNoMag.end, 'once', () => reload(false, true)));
clips.push(clip('reload_empty_no_mag', RELOAD.emptyNoMag.end, 'once', () => reload(true, true)));

// ---------- inspect ----------
// Both hands stay on the rifle (4.4 kg): brought in and turned to show the left side (bolt lock, receiver flat), rolled
// over to show the right (the operating rod and its handle; empty: the action held open), then the magazine check: the
// support hand rocks the magazine out, tips it toward the eye to look at the top round, hooks and rocks it back home.
// noMag (no magazine fitted): the same sides, then tipped up to look into the empty magwell; no magazine work or sounds.
const SHOW_L = {pos: [-2.0, 2.8, 1.5], rot: [12, 34, -6]};
const SHOW_R = {pos: [-1.0, 2.4, 1.2], rot: [10, -24, 26]};
const MAG_CHECK = {pos: [-1.4, 2.0, 1.0], rot: [8, 14, -30]};
const WELL_LOOK = {pos: [-1.2, 2.4, 1.0], rot: [16, 12, -36]};
const MAG_SHOW = {pos: [-4, 3, 6], rot: [40, -30, 20]};
// the magazine check's look (user 2026-10-21: no quick shake, give it rhythm): out slowly, up to the eye and held, then
// one slow turn onto its side to see the top round and the follower spring's window, held again, back in
const MAG_TURN = [8, -16, 10];
export const INSPECT_LENGTH = {full: 7.2, empty: 7.2, noMag: 5.0, emptyNoMag: 5.0};
function inspect(empty, noMag = false) {
    const T = noMag ? {up: 0.7, turn: 1.9, turned: 2.6, check: 3.3, checked: 3.6, back: 4.15, backEnd: 4.7}
        : {up: 0.7, turn: 1.9, turned: 2.6, check: 3.3, checked: 3.6, grab: 3.55, release: 3.62, out0: 3.66, out1: 3.92, unhook: 4.02,
            show: 4.5, turnOn: 4.75, turnOff: 5.15, showEnd: 5.35, entry: 5.78, hook: 5.88, seat: 6.12, regrip: 6.46, back: 6.22, backEnd: 6.8};
    const path = legs([
        [0.02, T.up, SHOW_L, {antic: 0.04, over: 0.05, per: 0.28, tau: 0.1}],
        [T.turn, T.turned, SHOW_R, {antic: 0.03, over: 0.05, per: 0.26, tau: 0.09}],
        [T.check, T.checked, noMag ? WELL_LOOK : MAG_CHECK, {antic: 0.02, over: 0.03}],
        [T.back, T.backEnd, IDLE, {antic: 0.03, over: 0.07, per: 0.26, tau: 0.1}]]);
    const look = t => sum(drift(t, T.up - 0.1, T.turn + 0.05, [1.8, 3.0, -1.6], 1.6), drift(t, T.turned - 0.1, T.check + 0.05, [1.6, -2.0, 2.4], 1.3),
        drift(t, T.checked, noMag ? T.back : T.out0, [0.6, -0.6, 0.8], 0.9));
    const hits = t => noMag ? jolt(t, T.backEnd, [0.6, 0, -0.25], {per: 0.22, tau: 0.08})
        : sum(jolt(t, T.release, [-0.5, 0, 0.35]), jolt(t, T.out1, [-0.8, 0.2, 0.6], {rise: 0.03, per: 0.16, tau: 0.05}),
            jolt(t, T.hook, [0.6, -0.2, 0.25]), jolt(t, T.seat, [2.0, -0.4, 0.9]), jolt(t, T.backEnd, [0.6, 0, -0.25], {per: 0.22, tau: 0.08}));
    const H = t => { const p = path(t), rot = sum(p.rot, look(t), hits(t)); return {rot, pos: sum(p.pos, mul([0, 0.08, 0], hits(t)[0]))}; };
    // at the eye: brought up (settling with a small overshoot), held, one slow turn, held; a breath of hand sway
    const showAt = (t, out) => {
        const m = mixM(out, MAG_SHOW, go(t, T.unhook, T.show, {antic: 0, over: 0.04, per: 0.3, tau: 0.1}));
        m.rot = add(m.rot, add(mul(MAG_TURN, ease(t, T.turnOn, T.turnOff)), drift(t, T.show - 0.1, T.showEnd + 0.1, [0.5, -0.6, 0.4], 1.4)));
        m.R = E(m.rot); return m;
    };
    const held = t => {
        if (noMag) return magInGun(H(t));
        if (t < T.unhook) { const r = rockOut(t, T); return magInGun(H(t), r.a, r.o); }
        const out = (() => { const r = rockOut(T.unhook, T); return magInGun(H(T.unhook), r.a, r.o); })();
        if (t < T.showEnd) return showAt(t, out);
        if (t < T.entry) { const r = rockIn(T.entry, T); return mixM(showAt(T.showEnd, out), magInGun(H(t), r.a, r.o), go(t, T.showEnd, T.entry, {antic: 0.02, over: 0.03, per: 0.1, tau: 0.035})); }
        const r = rockIn(Math.min(t, T.seat), T); return magInGun(H(t), r.a, r.o);
    };
    const LH = t => {
        if (noMag || t < T.check - 0.15) return leftOnGun(H(t));
        if (t < T.grab) return mixT(leftOnGun(H(t)), onMag(held(t), 0.6), go(t, T.check - 0.15, T.grab, {antic: 0, over: 0}));
        if (t < T.seat + 0.04) return onMag(held(t));
        return mixT(onMag(held(T.seat)), leftOnGun(H(t)), go(t, T.seat + 0.04, T.regrip, {antic: 0.02, over: 0.04}));
    };
    const camera = t => sum(mul([-0.8, 1.4, 1.0], ease(t, 0.02, T.up) - ease(t, T.turn, T.turned)),
        mul([-0.8, -1.0, -1.2], ease(t, T.turn, T.turned) - ease(t, T.check, T.checked)),
        mul([-0.8, 1.0, 1.2], ease(t, T.check, T.checked) - ease(t, T.back, T.backEnd)), mul(look(t), 0.25), mul(hits(t), 0.35),
        noMag ? Z : jolt(t, T.seat, [0.8, 0, 0.3]));
    const op = empty ? OP_HELD : 0;
    const tracks = {
        handling: {position: t => H(t).pos, rotation: t => H(t).rot},
        magazine: noMag ? {scale: () => [1, 1, 1], step: true} : hiddenFrom(T.out0, T.seat),
        mag_out: {position: t => held(t).pos, rotation: t => held(t).rot},
        reload_magazine: noMag ? {scale: () => [0, 0, 0], step: true} : shown(T.out0, T.seat),
        empty_old_mag: {scale: () => [0, 0, 0], step: true},
        mag_latch: {rotation: t => noMag ? Z : latch(t, T)},
        righthand: {position: () => RIGHT_REST.pos, rotation: () => RIGHT_REST.rot},
        lefthand: lhTracks(LH),
        ...mech(op), bolt_lock: {rotation: () => empty ? [BOLT_LOCK_UP, 0, 0] : Z},
        camera: {rotation: camera},
    };
    const sounds = noMag ? [] : [[T.release - 0.02, 'magazine_release'], [T.out0, 'magazine_out'], [T.seat - 0.28, 'magazine_in']];
    return {tracks, sounds, timing: T};
}
clips.push(clip('inspect', INSPECT_LENGTH.full, 'once', () => inspect(false)));
clips.push(clip('inspect_empty', INSPECT_LENGTH.empty, 'once', () => inspect(true)));
clips.push(clip('inspect_no_mag', INSPECT_LENGTH.noMag, 'once', () => inspect(false, true)));
clips.push(clip('inspect_empty_no_mag', INSPECT_LENGTH.emptyNoMag, 'once', () => inspect(true, true)));

// ---------- unload (docs/native_guns/magazine_system_v1.md 3.1) ----------
// The reload key with no fuller magazine: the magazine check up to the catch, the magazine rocked out and taken down off
// the lower left while the gun drops out low right. The server hands the magazine over at NativeGunActions.UNLOAD_SWAP_TICK
// (21 = 1.05 s); the clip holds LOW to its end. unload_empty: the action held open.
export const UNLOAD = {up: 0.3, grab: 0.28, release: 0.34, out0: 0.38, out1: 0.54, unhook: 0.6, carried: 0.9, down: 0.62, downEnd: 1.0, swap: 1.05, end: 1.3};
function unload(empty) {
    const T = UNLOAD;
    const path = legs([[0.02, T.up, MAG_CHECK, {antic: 0.03, over: 0.04, per: 0.22, tau: 0.08}], [T.down, T.downEnd, LOW, {antic: 0.04, over: 0}]]);
    const hits = t => sum(jolt(t, T.release, [-0.5, 0, 0.35]), jolt(t, T.out1, [-0.8, 0.2, 0.6], {rise: 0.03, per: 0.16, tau: 0.05}));
    const H = t => { const p = path(t), rot = sum(p.rot, hits(t)); return {rot, pos: sum(p.pos, mul([0, 0.08, 0], hits(t)[0]))}; };
    const held = t => {
        if (t < T.unhook) { const r = rockOut(t, T); return magInGun(H(t), r.a, r.o); }
        const r = rockOut(T.unhook, T); return mixM(magInGun(H(T.unhook), r.a, r.o), OFFSCREEN_MAG, go(t, T.unhook, T.carried, {antic: 0.02, over: 0}));
    };
    const LH = t => t < T.grab ? mixT(leftOnGun(H(t)), onMag(held(t), 0.6), go(t, 0.02, T.grab, {antic: 0, over: 0})) : onMag(held(t));
    const camera = t => sum(mul([-0.8, 1.0, 1.2], ease(t, 0.02, T.up) - ease(t, T.down, T.downEnd)), mul(hits(t), 0.35),
        mul([-1.0, 0.4, 0.9], ease(t, T.down, T.downEnd)));
    const tracks = {
        handling: {position: t => H(t).pos, rotation: t => H(t).rot},
        magazine: hiddenFrom(T.out0, 99),
        mag_out: {position: t => held(t).pos, rotation: t => held(t).rot},
        reload_magazine: shown(T.out0, T.carried + 0.05),
        empty_old_mag: {scale: () => [0, 0, 0], step: true},
        mag_latch: {rotation: t => latch(t, {...T, seat: 99})},
        righthand: {position: () => RIGHT_REST.pos, rotation: () => RIGHT_REST.rot},
        lefthand: lhTracks(LH),
        ...mech(empty ? OP_HELD : 0), bolt_lock: {rotation: () => empty ? [BOLT_LOCK_UP, 0, 0] : Z},
        camera: {rotation: camera},
    };
    const sounds = [[T.release - 0.02, 'magazine_release'], [T.out0, 'magazine_out'], [T.down, 'put_away']];
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
        const {tracks, sounds = [], timing} = c.build();
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
                const keys = bake(chs[ch], c.length, step, ref), track = {};
                keys.forEach(([t, v], i) => {
                    animator.keyframes.push({channel: ch, data_points: [{x: String(v[0]), y: String(v[1]), z: String(v[2])}],
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
