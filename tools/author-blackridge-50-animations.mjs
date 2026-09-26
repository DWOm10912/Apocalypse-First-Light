// Blackridge .50 first-person animation authoring (MW2-style handling language).
// Knots below are the editable design; every clip is baked at 60 Hz into linear keys.
//   node tools/author-blackridge-50-animations.mjs            -> audit/summary only
//   --emit <file>      write Blockbench-format animations (load into the open project)
//   --write-runtime    write assets/.../animations/blackridge_50.animation.json
import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const sourcePath = path.join(root, 'src/main/blockbench/blackridge_50.bbmodel');
const runtimePath = path.join(root, 'src/main/resources/assets/apocalypse_firstlight/animations/blackridge_50.animation.json');
const NS = 'apocalypse_firstlight:blackridge_50_';
const soundDir = path.join(root, 'src/main/resources/assets/apocalypse_firstlight/sounds/weapons/blackridge_50');
const FPS = 60;

// ---------- math ----------
const add = (a, b) => a.map((v, i) => v + b[i]), sub = (a, b) => a.map((v, i) => v - b[i]);
const mul = (a, n) => a.map(v => v * n), lerp = (a, b, s) => a.map((v, i) => v + (b[i] - v) * s);
const norm = a => mul(a, 1 / Math.hypot(...a));
const clamp01 = u => Math.max(0, Math.min(1, u));
const smooth = u => { u = clamp01(u); return u * u * u * (10 + u * (-15 + 6 * u)); };
const win = (t, a, b) => smooth((t - a) / (b - a));
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
// Shape-preserving cubic Hermite (no overshoot between monotonic knots; equal knots dwell).
function scalar(pts, t) {
    const x = pts.map(p => p[0]), y = pts.map(p => p[1]);
    if (t <= x[0]) return y[0]; if (t >= x.at(-1)) return y.at(-1);
    const h = x.slice(1).map((v, i) => v - x[i]), d = h.map((v, i) => (y[i + 1] - y[i]) / v), m = y.map(() => 0);
    for (let i = 1; i < y.length - 1; i++) if (d[i - 1] * d[i] > 0) { const w1 = 2 * h[i] + h[i - 1], w2 = h[i] + 2 * h[i - 1]; m[i] = (w1 + w2) / (w1 / d[i - 1] + w2 / d[i]); }
    const j = x.findIndex(v => v >= t) - 1, u = (t - x[j]) / h[j];
    return (2 * u ** 3 - 3 * u * u + 1) * y[j] + (u ** 3 - 2 * u * u + u) * h[j] * m[j] + (-2 * u ** 3 + 3 * u * u) * y[j + 1] + (u ** 3 - u * u) * h[j] * m[j + 1];
}
const curve = knots => t => [0, 1, 2].map(i => scalar(knots.map(([s, v]) => [s, v[i]]), t));
const curve1 = knots => t => scalar(knots, t);
const Z = [0, 0, 0];

// ---------- rig facts (source coordinates, +X = gun right / ejection side, muzzle -Z) ----------
const HP = [1.15, 4, 3.75];                        // handling pivot = right grip
const MP = [0, 4, 3.8];                            // magazine / mag_out / empty_old_mag pivot
const AX = norm([0, 0.117 - 8.196, 4.738 - 2.384]); // magazine axis, pointing OUT of the magwell
const MAG_BOTTOM = [0, 0.117, 4.738];
const LP = [0, 0, -3], LA = [-1.2, 5.8, -2.5];      // lefthand pivot / left_hand_anchor
const SLIDE_BACK = 2.1, SLIDE_TOP_REAR = [0, 9.75, 4.6];
const FOLLOWER_LOADED = mul(AX, 0.6);              // follower rest = empty (top); loaded = pushed down
const MAG_CLEAR = 8.6;                             // axial travel that clears the magwell

// ---------- motion language (Silverwood / CoD) ----------
// go(): anticipation dip -> accelerating travel -> hard arrival -> damped overshoot.
function go(t, t0, t1, {antic = 0.04, over = 0.07, per = 0.17, tau = 0.07, pre = 0.08} = {}) {
    if (t < t0 - pre) return 0;
    if (t < t0) return -antic * Math.sin(Math.PI * (t - t0 + pre) / pre);
    if (t < t1) { const u = (t - t0) / (t1 - t0); return 1.6 * u * u - 0.6 * u * u * u; }
    const s = t - t1; return 1 + over * Math.sin(2 * Math.PI * s / per) * Math.exp(-s / tau);
}
// jolt(): one- to two-frame mechanical hit, ringing out quickly.
function jolt(t, te, amp, {rise = 0.017, per = 0.11, tau = 0.045} = {}) {
    const s = t - te; if (s <= 0) return Z;
    const k = s < rise ? s / rise : Math.exp(-(s - rise) / tau) * Math.cos(2 * Math.PI * (s - rise) / per);
    return mul(amp, k);
}
const sum = (...v) => v.reduce((a, b) => add(a, b), Z);

// ---------- design poses ----------
const ARM_HALF_FWD = 4 * 0.62 / 0.37 / 2;
const RELOAD_POSE = {pos: [2.4, 2.2, 1.2], rot: [18, 50, -16]};   // "auto displacement": gun to the side, magwell to the left hand
const GRIP_SLIDE = [-(ARM_HALF_FWD + 1.19 + 0.15), 0.4, 0];                // gun-local: grip moves clear of the right hand, to its left
const INSPECT_A = {pos: [1.8, 3.0, 1.0], rot: [26, 64, -12]};     // left side, raised
const INSPECT_B = {pos: [-0.8, 1.6, 1.2], rot: [60, -16, 14]};     // stood up, slide toward the eye (press check)
// Hands. Anchor = distal cap, forearm along local -Y (AFL hand locator standard).
const RP = [0, 0, -3], RA = [1.15, 4, 3.75];
// Grip copied from P9-01 idle (gun-space hand poses), re-seated on the larger Blackridge grip.
// Idle: the grip sits INSIDE the firing hand (arm box centred on the grip, top just under the slide).
const ARM_HALF = 4 * 0.62 / 0.37 / 2;                                    // classic arm half width at FP display 0.37
const RH_POINT = [0, 7.05 - ARM_HALF, 1.7];                               // knuckles at the grip front
const LG_BASE = [-2 * ARM_HALF + 0.35, 6.4 - ARM_HALF, 1.2];            // first pass: support hand cupped on the left
// Forearm direction as in P9, but rolled level (P9 reads level only through its -20.56 deg presentation cant).
const cross = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
function levelArm(forearm, palm = [0, -1, 0]) {
    const ey = mul(norm(forearm), -1), ez = norm(sub(palm, mul(ey, palm.reduce((a, v, i) => a + v * ey[i], 0))));
    const ex = cross(ey, ez); return euler([0, 1, 2].map(r => [ex[r], ey[r], ez[r]]));
}
const RH_ROT = levelArm([0, -0.01, 1]), LG_ROT = levelArm([-0.35, -0.02, 0.94]);
// User calibration in Blockbench (2026-09-26): left arm slid -6 along the hand's local X so it leans on the firing hand.
const LG_POINT = add(LG_BASE, mv(E(LG_ROT), [-6, 0, 0]));
let HAND_ON_MAG;                               // left grip on a magazine base (magazine space)
let HAND_ON_SLIDE;                             // left hand over the rear of the slide (gun space)
let LOW_LEFT;      // left hand off-screen (root space)
const OFFSCREEN_MAG = {pos: [-4.5, -12, 7], rot: [-45, 10, 35]};   // new magazine pick-up point (root space, relative)

// ---------- rig solvers ----------
const handleWorld = (h, x) => add(add(h.pos, HP), mv(E(h.rot), sub(x, HP)));
// Magazine drawn by a root-level bone whose rest pivot is MP, carried by the gun at axial offset d with local rotation lr.
function magInGun(h, d, lr = Z) {
    const R = mm(E(h.rot), E(lr));
    const pos = sub(handleWorld(h, add(MP, mul(AX, d))), MP);
    return {pos, rot: euler(R), R};
}
const magPointWorld = (m, x) => add(add(m.pos, MP), mv(m.R ?? E(m.rot), sub(x, MP)));
// Bone transform (pivot, anchor) that puts the anchor on a point with a rotation, in the parent's space.
const solve = (pivot, anchor) => (point, R) => ({pos: sub(sub(point, pivot), mv(R, sub(anchor, pivot))), rot: euler(R)});
const handAt = solve(LP, LA);
const RIGHT_REST = solve(RP, RA)(RH_POINT, E(RH_ROT));                   // righthand lives under handling
// Secondary hand shapes, defined like the grip: forearm direction + palm normal.
HAND_ON_MAG = levelArm([-0.3, -0.55, 0.78], [1, 0.45, 0]);                // magazine space: palm cupping the base plate from the left (<180 deg from the grip, so blends never flip sides)
HAND_ON_SLIDE = levelArm([-0.45, -0.3, 0.84]);                           // gun space: palm down over the slide
LOW_LEFT = handAt([-7, -8, 9], E(levelArm([-0.25, -0.55, 0.8])));        // off-screen low left, still palm down
const leftOnGun = h => handAt(handleWorld(h, LG_POINT), mm(E(h.rot), E(LG_ROT)));
const onMag = m => handAt(add(magPointWorld(m, MAG_BOTTOM), mv(m.R, mul(AX, 0.15))), mm(m.R, E(HAND_ON_MAG)));
// Rotation blends go through quaternions: Euler lerp takes the long way round near +/-180 deg.
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
// Pick the Euler triple equivalent to v that is closest to ref (removes +/-360 and gimbal-mirror jumps).
function unwrap(v, ref) {
    let best = null, bd = Infinity;
    for (const base of [v, [v[0] + 180, 180 - v[1], v[2] + 180]])
        for (const k0 of [-2, -1, 0, 1, 2]) for (const k1 of [-2, -1, 0, 1, 2]) for (const k2 of [-2, -1, 0, 1, 2]) {
            const c = [base[0] + 360 * k0, base[1] + 360 * k1, base[2] + 360 * k2], d = c.reduce((m, x, i) => m + (x - ref[i]) ** 2, 0);
            if (d < bd) { bd = d; best = c; }
        }
    return best;
}
const IDLE = {pos: Z, rot: Z};
const poseAt = (p, w) => ({pos: mul(p.pos, w), rot: mul(p.rot, w)});
// Gimbal guard: lefthand_pos carries a constant Y+90 pre-rotation (same pivot as lefthand), so the
// animated lefthand Euler only hits its singularity when the palm faces the camera, which never happens.
// Anchor pose is unchanged: R_lefthand * R_pos == R_solved.
// The pre-rotation is chosen per clip (see build) from these candidates, whichever gives the smoothest Euler track.
const LH_POS_CANDIDATES = [[0, 90, 0], [0, -90, 0], [90, 0, 0], [-90, 0, 0], [0, 0, 90], [0, 0, -90], [0, 0, 0]];
const T3 = m => [0, 1, 2].map(i => [0, 1, 2].map(j => m[j][i]));
const lhTracks = LH => ({position: t => LH(t).pos, solved: t => LH(t).rot});

// ---------- clip builder ----------
function clip(name, length, loop, build) { return {name, length, loop, build}; }
const clips = [];

clips.push(clip('static_idle', 0.25, 'hold', () => ({
    tracks: {
        righthand: {position: () => RIGHT_REST.pos, rotation: () => RIGHT_REST.rot},
        lefthand: lhTracks(() => leftOnGun(IDLE)),
        follower: {position: () => FOLLOWER_LOADED},
        reload_magazine: {scale: () => [0, 0, 0], step: true}, empty_old_mag: {scale: () => [0, 0, 0], step: true}},
})));

clips.push(clip('static_bolt_caught', 0.25, 'loop', () => ({
    tracks: {slide: {position: () => [0, 0, SLIDE_BACK]}, safety: {position: () => [0, 0, SLIDE_BACK]},
        slide_stop: {rotation: () => [6, 0, 0]}, follower: {position: () => Z}},
})));

// Shoot: .50 AE punch. Hard 1-frame kick, slide slam, heavy settle. Camera stays with Native recoil.
clips.push(clip('shoot', 0.36, 'once', () => {
    const slide = curve1([[0, 0], [0.012, 0.35], [0.03, SLIDE_BACK], [0.048, SLIDE_BACK], [0.092, 0.1], [0.1, 0]]);
    const H = t => ({
        rot: sum(jolt(t, 0, [7.5, 0.8, 2.6], {rise: 0.025, per: 0.3, tau: 0.075}), jolt(t, 0.1, [0.9, 0, -0.4], {per: 0.09, tau: 0.03})),
        pos: sum(jolt(t, 0, [0, 0.25, 1.1], {rise: 0.02, per: 0.3, tau: 0.07}), jolt(t, 0.1, [0, 0, 0.12], {per: 0.09, tau: 0.03}))});
    return {tracks: {
        handling: {position: t => H(t).pos, rotation: t => H(t).rot},
        lefthand: lhTracks(t => leftOnGun(H(t))),
        slide: {position: t => [0, 0, slide(t)]}, safety: {position: t => [0, 0, slide(t)]},
        hammer: {rotation: curve([[0, Z], [0.008, [-14, 0, 0]], [0.03, [22, 0, 0]], [0.06, Z], [0.36, Z]])},
        trigger: {rotation: curve([[0, [-11, 0, 0]], [0.06, [-11, 0, 0]], [0.12, Z], [0.36, Z]])},
    }};
}));

// Safety handling by the support hand: thumb on the left safety lever at the rear of the slide.
// Lever rotation: -55 = safe (up), 0 = fire (down).
const SAFETY_TOUCH = [-1.55 - ARM_HALF * 0.8, 8.95, 4.5];                  // gun space, hand box beside the lever
const HAND_ON_SAFETY = levelArm([-0.3, -0.5, 0.81], [1, 0.25, 0]);          // palm turned toward the gun
const onSafety = (h, press = 0) => handAt(handleWorld(h, add(SAFETY_TOUCH, [0, -0.45 * press, 0.1 * press])), mm(E(h.rot), E(HAND_ON_SAFETY)));

// Draw: the gun comes up first; once it settles the left thumb sweeps the safety off, then wraps the grip.
clips.push(clip('draw', 0.9, 'once', () => {
    const low = {pos: [0.8, -7, 1.5], rot: [-48, 16, 28]}, FLICK = 0.44;
    const H = t => { const w = go(t, 0.02, 0.28, {antic: 0, over: 0.09, per: 0.2, tau: 0.08});
        return {pos: sum(lerp(low.pos, Z, w), jolt(t, 0.27, [0, -0.15, 0]), jolt(t, FLICK + 0.02, [0, -0.06, 0])),
            rot: sum(lerp(low.rot, Z, w), jolt(t, 0.27, [1.8, 0, -0.8], {per: 0.13, tau: 0.05}), jolt(t, FLICK + 0.02, [0.8, 0, 0.5]))}; };
    const press = t => clamp01((t - FLICK) / 0.035) * (1 - clamp01((t - FLICK - 0.06) / 0.06));
    const LH = t => {
        if (t < 0.4) return mixT(LOW_LEFT, onSafety(H(t)), clamp01(go(t, 0.12, 0.4, {antic: 0, over: 0})));
        if (t < 0.52) return onSafety(H(t), press(t));
        return mixT(onSafety(H(t)), leftOnGun(H(t)), go(t, 0.52, 0.74, {antic: 0.02, over: 0.05}));
    };
    return {tracks: {
        handling: {position: t => H(t).pos, rotation: t => H(t).rot},
        safety: {rotation: t => t < FLICK ? [-55, 0, 0] : lerp([-55, 0, 0], Z, go(t, FLICK, FLICK + 0.035, {antic: 0, over: 0.12, per: 0.08, tau: 0.03}))},
        lefthand: lhTracks(LH),
        camera: {rotation: t => sum(mul([-1.6, 0.6, 1.2], 1 - go(t, 0.02, 0.3, {antic: 0, over: 0.12})), jolt(t, 0.27, [0.9, -0.2, -0.4]), jolt(t, FLICK + 0.02, [0.5, 0, -0.2]))},
    }, sounds: [[FLICK - 0.01, 'safety_lever']]};
}));

// Put away: the left thumb pushes the safety on first, then the gun drops out of view.
clips.push(clip('put_away', 0.6, 'once', () => {
    const low = {pos: [0.8, -7.5, 1.5], rot: [-50, 16, 30]}, FLICK = 0.13;
    const H = t => { const w = go(t, 0.2, 0.55, {antic: 0.06, over: 0});
        return {pos: lerp(Z, low.pos, w), rot: sum(lerp(Z, low.rot, w), jolt(t, FLICK + 0.02, [-0.8, 0, 0.4]))}; };
    const press = t => clamp01((t - FLICK) / 0.035) * (1 - clamp01((t - FLICK - 0.05) / 0.05));
    const LH = t => {
        if (t < FLICK) return mixT(leftOnGun(H(t)), onSafety(H(t)), clamp01(go(t, 0.0, FLICK, {antic: 0, over: 0})));
        if (t < 0.22) return onSafety(H(t), press(t));
        return mixT(onSafety(H(t)), LOW_LEFT, go(t, 0.22, 0.46, {antic: 0, over: 0}));
    };
    return {tracks: {
        handling: {position: t => H(t).pos, rotation: t => H(t).rot},
        safety: {rotation: t => lerp(Z, [-55, 0, 0], go(t, FLICK, FLICK + 0.035, {antic: 0, over: 0.1, per: 0.08, tau: 0.03}))},
        lefthand: lhTracks(LH),
        camera: {rotation: t => sum(jolt(t, FLICK + 0.02, [0.5, 0, -0.3]), mul([-1.2, 0.4, 1], go(t, 0.2, 0.55, {antic: 0, over: 0})))},
    }, sounds: [[FLICK - 0.01, 'safety_lever']]};
}));

// Reloads. Tactical: release, old mag drops, fresh mag from below, palm-driven seat.
// Empty: release + wrist flick throws the empty mag, fresh mag, thumb drops the slide.
function reload(empty) {
    // Heavy pistol: ~1.3x slower than a service pistol, weighty entry and settle.
    const T = empty
        ? {in0: 0.0, pose: 0.26, release: 0.22, drop: 0.3, hide: 0.95, pick: 0.38, carry: 0.56, align: 0.92, seat: 1.14, grab: 1.38, pull: 1.44, pulled: 1.55, slide: 1.58, ret: 1.76, retEnd: 2.06, end: 2.7}
        : {in0: 0.03, pose: 0.36, release: 0.38, drop: null, hide: 1.05, pick: 0.48, carry: 0.64, align: 1.05, seat: 1.26, slide: null, ret: 1.48, retEnd: 1.8, end: 2.45};
    const P = RELOAD_POSE;
    const w = t => go(t, T.in0, T.pose, {antic: 0.06, over: 0.07, per: 0.22, tau: 0.09}) - go(t, T.ret, T.retEnd, {antic: 0.04, over: 0.1, per: 0.26, tau: 0.1});
    // Empty: whole-arm throw like the inspect toss. Wind, whip until the well faces right, the mag leaves with the gun's velocity.
    const swing = t => !empty ? 0 : go(t, T.drop - 0.09, T.drop + 0.03, {antic: 0.35, pre: 0.14, over: 0.2, per: 0.3, tau: 0.1})
        - go(t, T.drop + 0.14, T.drop + 0.5, {antic: 0, over: 0.06});
    const SWING = {rot: [-18, 0, 98], pos: [1.6, 3.0, 0.4]};
    const hits = t => sum(
        jolt(t, T.seat, [3.2, -0.6, 1.6], {rise: 0.017, per: 0.12, tau: 0.05}),
        T.slide ? jolt(t, T.slide + 0.03, [3.6, 0.5, -2.4], {rise: 0.017, per: 0.12, tau: 0.05}) : Z,
        empty ? jolt(t, T.pull, [-2.2, 3.5, 2.5], {rise: 0.05, per: 0.2, tau: 0.06}) : Z,   // left hand yanks the slide
        jolt(t, T.release, [-0.6, 0, 0.4]));
    // Palm-side slide (TaCZ trick): inside the reload pose the gun slips out of the firing hand to the
    // hand's left, exposing the magwell; the right hand counter-moves so it appears to stay put.
    // The gun stays beside the hand through the slide rack and returns with the pose.
    const shiftW = t => clamp01(w(t));
    const H = t => { const rot = sum(mul(P.rot, w(t)), mul(SWING.rot, swing(t)), hits(t));
        return {rot, pos: sum(mul(P.pos, w(t)), mul(SWING.pos, swing(t)), mv(E(rot), mul(GRIP_SLIDE, shiftW(t))), mul([0, 0.12, 0], hits(t)[0]), T.slide ? jolt(t, T.slide + 0.03, [0, 0.1, 0.35]) : Z)}; };
    // Old magazine leaves the well along its own axis, then flies on with its true world velocity (no kink).
    // Tactical: released, it drops under gravity. Empty: thrown out at the peak of the swing.
    const g = empty ? 330 : 520, t0 = empty ? T.drop + 0.02 : T.release + 0.02, EJ = 80;
    const axial = t => t < t0 ? 0 : empty ? EJ * (t - t0) : 0.5 * g * (t - t0) ** 2;
    const tClear = empty ? t0 + MAG_CLEAR / EJ : t0 + Math.sqrt(2 * MAG_CLEAR / g);
    const inWell = t => magInGun(H(t), axial(t));
    const vRel = mul(sub(inWell(tClear).pos, inWell(tClear - 0.004).pos), 1 / 0.004);
    const oldMag = t => {
        if (t <= tClear) return inWell(t);
        const base = inWell(tClear), dt = t - tClear;
        const pos = add(add(base.pos, mul(vRel, dt)), [0, -0.5 * g * dt * dt, 0]);
        const R = mm(base.R, E(empty ? [-300 * dt, -80 * dt, -500 * dt] : [-140 * dt, 0, 50 * dt]));
        return {pos, rot: euler(R), R};
    };
    // New magazine: off-screen -> lined up under the magwell (hard stop) -> driven home.
    const newMag = t => {
        if (t < T.align) {
            const s = clamp01(go(t, T.carry, T.align, {antic: 0, over: 0.04, per: 0.12, tau: 0.04})), g0 = magInGun(H(t), 7);
            const R = E(lerp(OFFSCREEN_MAG.rot, g0.rot, s));
            return {pos: lerp(OFFSCREEN_MAG.pos, g0.pos, s), rot: euler(R), R};
        }
        const d = 7 * (1 - go(t, T.align + 0.03, T.seat, {antic: 0.03, over: 0, pre: 0.03}));
        return magInGun(H(t), Math.max(0, d));
    };
    const LH = t => {
        if (t < T.pick) return mixT(leftOnGun(H(t)), LOW_LEFT, go(t, T.release - 0.04, T.pick, {antic: 0, over: 0}));
        if (t < T.seat + 0.08) return mixT(LOW_LEFT, onMag(newMag(Math.min(t, T.seat))), clamp01(go(t, T.pick, T.carry + 0.04, {antic: 0, over: 0})));
        if (!empty) return mixT(onMag(newMag(T.seat)), leftOnGun(H(t)), go(t, T.seat + 0.08, T.ret + 0.1, {antic: 0.02, over: 0.04}));
        // Empty: overhand grab on the slide serrations, rack it back off the stop, let it slam, return to the grip.
        if (t < T.slide + 0.04) return mixT(onMag(newMag(T.seat)), onSlide(t), clamp01(go(t, T.seat + 0.06, T.grab, {antic: 0.02, over: 0})));
        return mixT(onSlide(t), leftOnGun(H(t)), go(t, T.slide + 0.04, T.ret + 0.15, {antic: 0, over: 0.04}));
    };
    const RACK = 0.4;
    const slidePos = t => !empty ? 0 : t < T.pull ? SLIDE_BACK : t < T.slide ? SLIDE_BACK + RACK * clamp01(go(t, T.pull, T.pulled, {antic: 0, over: 0}))
        : (SLIDE_BACK + RACK) * (1 - clamp01((t - T.slide) / 0.03));
    const onSlide = t => { const h = H(t); return handAt(handleWorld(h, add(SLIDE_TOP_REAR, [0, 0, slidePos(t)])), mm(E(h.rot), E(HAND_ON_SLIDE))); };
    const camera = t => sum(mul([-1.4, 1.6, 2.0], w(t)), mul(hits(t), 0.45), jolt(t, T.seat, [1.2, 0, 0.4]),
        T.slide ? jolt(t, T.slide + 0.03, [1.6, -0.3, -0.8]) : Z, empty ? jolt(t, T.pull, [-0.6, 0.8, 0.6]) : Z);
    const tracks = {
        handling: {position: t => H(t).pos, rotation: t => H(t).rot},
        magazine: {scale: t => (t >= t0 && t < T.seat) ? [0, 0, 0] : [1, 1, 1], step: true},
        follower: {position: () => FOLLOWER_LOADED},
        magazine_release: {position: t => [0.12 * clamp01((t - T.release + 0.03) / 0.03) * (1 - clamp01((t - T.release - 0.08) / 0.05)), 0, 0]},
        empty_old_mag: {position: t => oldMag(t).pos, rotation: t => oldMag(t).rot,
            scale: t => (t >= t0 && t < T.hide) ? [1, 1, 1] : [0, 0, 0], step: true},
        mag_out: {position: t => newMag(t).pos, rotation: t => newMag(t).rot},
        righthand: {position: t => sub(RIGHT_REST.pos, mul(GRIP_SLIDE, shiftW(t))), rotation: () => RIGHT_REST.rot},
        reload_magazine: {scale: t => (t >= T.carry && t < T.seat) ? [1, 1, 1] : [0, 0, 0], step: true},
        lefthand: lhTracks(LH),
        camera: {rotation: camera},
    };
    if (empty) {
        tracks.slide = {position: t => [0, 0, slidePos(t)]};
        tracks.safety = {position: t => [0, 0, slidePos(t)]};
        tracks.slide_stop = {rotation: t => [6 * (1 - clamp01((t - T.pull) / 0.04)), 0, 0]};
    }
    const sounds = [[T.release - 0.02, 'magazine_release'], [empty ? T.drop - 0.02 : T.release + 0.04, empty ? 'magazine_flick' : 'magazine_out'],
        [T.seat - 0.03, 'magazine_in'], ...(empty ? [[T.pull - 0.02, 'slide_back'], [+(T.slide + 0.03 - 0.135).toFixed(3), 'slide_release']] : [])];
    return {tracks, sounds, timing: T};
}
clips.push(clip('reload_tactical', 2.45, 'once', () => reload(false)));
clips.push(clip('reload_empty', 2.7, 'once', () => reload(true)));

// Inspect: present the left side, thumb drops the mag into the left hand, show it, slam it home;
// loaded only: stand the gun up and press-check the slide.
function inspect(empty) {
    const T = {up: 0.45, release: 0.66, drop: 0.7, catch: 1.15, show: 1.5, showEnd: 2.2, align: 2.5, seat: 2.7,
        turn: 2.95, grab: 3.2, pull: 3.34, pulled: 3.46, let: 3.9, back: 4.1, backEnd: 4.35, end: 4.8};
    if (empty) Object.assign(T, {back: 2.95, backEnd: 3.2, end: 3.65});
    const A = INSPECT_A, B = INSPECT_B;
    const out = t => go(t, T.back, T.backEnd, {antic: 0.03, over: 0.1, per: 0.22, tau: 0.09});
    const wA = t => go(t, 0.02, T.up, {antic: 0.05, over: 0.08}) - (empty ? out(t) : go(t, T.turn, T.grab, {antic: 0.03, over: 0.06}));
    const wB = t => empty ? 0 : go(t, T.turn, T.grab, {antic: 0.03, over: 0.06}) - out(t);
    const hits = t => sum(jolt(t, T.release, [-0.8, 0, 0.5]), jolt(t, T.catch, [0.6, 0, -0.3]), jolt(t, T.seat, [3, -0.5, 1.4]),
        empty ? Z : sum(jolt(t, T.pulled, [-1.2, 0, 0.6]), jolt(t, T.let + 0.02, [2.6, 0.4, -1.6])));
    // Same palm-side slide as the reloads while the magazine is out; back in the hand for the press check.
    const shiftW = t => clamp01(wA(t));
    // Whole-arm toss: wind down-right, whip up-left hard, follow through, relax while the left hand catches.
    const swing = t => go(t, T.drop - 0.06, T.drop + 0.04, {antic: 0.4, pre: 0.14, over: 0.22, per: 0.3, tau: 0.1})
        - go(t, T.drop + 0.16, T.catch + 0.05, {antic: 0, over: 0.06});
    const SWING = {rot: [30, 14, -78], pos: [-1.6, 3.4, 0.6]};   // at the peak the magwell faces up-left
    const H = t => { const rot = sum(mul(A.rot, wA(t)), mul(B.rot, wB(t)), mul(SWING.rot, swing(t)), hits(t));
        return {rot, pos: sum(mul(A.pos, wA(t)), mul(B.pos, wB(t)), mul(SWING.pos, swing(t)), mv(E(rot), mul(GRIP_SLIDE, shiftW(t))), mul([0, 0.1, 0], hits(t)[0]))}; };
    // Toss: at the top of the swing the well faces up-left; the magazine slides out along its own axis and
    // leaves with its true world velocity (gun motion included), so the flight continues the exit without a kink.
    const EJ = 70, G = 260, ts = T.drop + 0.02, tClear = ts + MAG_CLEAR / EJ;
    const inWell = t => magInGun(H(t), t < ts ? 0 : EJ * (t - ts));
    const vRel = mul(sub(inWell(tClear).pos, inWell(tClear - 0.004).pos), 1 / 0.004);
    const toss = t => {
        if (t <= tClear) return inWell(t);
        const base = inWell(tClear), dt = t - tClear;
        const pos = add(add(base.pos, mul(vRel, dt)), [0, -0.5 * G * dt * dt, 0]);
        const R = mm(base.R, E([-300 * dt, 60 * dt, 200 * dt])); return {pos, rot: euler(R), R};
    };
    const showPose = {pos: [-7, 1.5, 6], rot: [-40, 30, 18]};
    const held = t => {
        if (t < T.catch) return toss(t);
        if (t < T.showEnd) {
            const a = toss(T.catch);
            const s = go(t, T.catch + 0.06, T.show, {antic: 0, over: 0.06});
            const wig = mul([6, -10, 4], Math.sin(2 * Math.PI * clamp01((t - T.show) / (T.showEnd - T.show))) * clamp01((t - T.show) / 0.1));
            const R = E(add(lerp(a.rot, showPose.rot, s), wig)); return {pos: lerp(a.pos, showPose.pos, s), rot: euler(R), R};
        }
        if (t < T.align) {
            const g = magInGun(H(t), 7), s = go(t, T.showEnd, T.align, {antic: 0, over: 0.04, per: 0.12, tau: 0.04});
            const R = E(lerp(showPose.rot, g.rot, s)); return {pos: lerp(showPose.pos, g.pos, s), rot: euler(R), R};
        }
        return magInGun(H(t), Math.max(0, 7 * (1 - go(t, T.align + 0.03, T.seat, {antic: 0.03, over: 0, pre: 0.03}))));
    };
    const slide = t => empty ? SLIDE_BACK : t < T.pull ? 0 : t < T.let ? 1.05 * clamp01(go(t, T.pull, T.pulled, {antic: 0, over: 0.03})) : 1.05 * (1 - clamp01((t - T.let) / 0.03));
    const onSlide = t => { const h = H(t); return handAt(handleWorld(h, add(SLIDE_TOP_REAR, [0, 0, slide(t)])), mm(E(h.rot), E(HAND_ON_SLIDE))); };
    const LH = t => {
        if (t < T.catch) return mixT(leftOnGun(H(t)), onMag(toss(T.catch)), clamp01(go(t, T.catch - 0.22, T.catch, {antic: 0.03, over: 0})));   // reach up only at the end
        if (t < T.seat + 0.08) return onMag(held(Math.min(t, T.seat)));
        if (empty) return mixT(onMag(held(T.seat)), leftOnGun(H(t)), go(t, T.seat + 0.08, T.back + 0.1, {antic: 0.02, over: 0.04}));
        if (t < T.let + 0.05) return mixT(onMag(held(T.seat)), onSlide(t), go(t, T.seat + 0.08, T.grab, {antic: 0.02, over: 0.03}));
        return mixT(onSlide(t), leftOnGun(H(t)), go(t, T.let + 0.05, T.back + 0.15, {antic: 0, over: 0.04}));
    };
    const camera = t => sum(mul([-1.4, 2.0, 2.2], wA(t)), mul([-1.8, -1.6, -1.6], wB(t)), mul(hits(t), 0.4),
        jolt(t, T.seat, [1.1, 0, 0.4]), empty ? Z : jolt(t, T.let + 0.02, [1.4, -0.3, -0.7]));
    const tracks = {
        handling: {position: t => H(t).pos, rotation: t => H(t).rot},
        magazine: {scale: t => (t >= T.drop && t < T.seat) ? [0, 0, 0] : [1, 1, 1], step: true},
        follower: {position: () => empty ? Z : FOLLOWER_LOADED},
        magazine_release: {position: t => [0.12 * clamp01((t - T.release + 0.03) / 0.03) * (1 - clamp01((t - T.release - 0.1) / 0.05)), 0, 0]},
        mag_out: {position: t => held(t).pos, rotation: t => held(t).rot},
        righthand: {position: t => sub(RIGHT_REST.pos, mul(GRIP_SLIDE, shiftW(t))), rotation: () => RIGHT_REST.rot},
        reload_magazine: {scale: t => (t >= T.drop && t < T.seat) ? [1, 1, 1] : [0, 0, 0], step: true},
        empty_old_mag: {scale: () => [0, 0, 0], step: true},
        lefthand: lhTracks(LH),
        slide: {position: t => [0, 0, slide(t)]}, safety: {position: t => [0, 0, slide(t)]},
        camera: {rotation: camera},
    };
    if (empty) tracks.slide_stop = {rotation: () => [6, 0, 0]};
    const sounds = [[T.release - 0.02, 'magazine_release'], [T.drop - 0.02, 'magazine_flick'], [T.seat - 0.03, 'magazine_in'],
        ...(empty ? [] : [[T.pull - 0.02, 'slide_back'], [+(T.let - 0.135).toFixed(3), 'slide_release']])];
    return {tracks, sounds, timing: T};
}
clips.push(clip('inspect', 4.8, 'once', () => inspect(false)));
clips.push(clip('inspect_empty', 3.65, 'once', () => inspect(true)));

// ---------- bake ----------
const round = v => +v.toFixed(4);
function bake(fn, length, step, rotRef = null) {
    const n = Math.round(length * FPS), keys = [];
    let prev = rotRef;
    for (let i = 0; i <= n; i++) {
        const t = round(Math.min(length, i / FPS)); let v = fn(t);
        if (prev) { v = unwrap(v, prev); prev = v; }
        if (prev && keys.length) {
            // Near gimbal lock neighbouring Euler samples can differ a lot for a small real rotation:
            // subdivide so linear interpolation between keys never swings through a wrong pose.
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
    // drop keys that are reproduced by their neighbours (linear) or repeated (step)
    const out = [keys[0]];
    for (let i = 1; i < keys.length - 1; i++) {
        const [ta, a] = out.at(-1), [t, v] = keys[i], [tb, b] = keys[i + 1];
        const same = step ? v.every((x, k) => x === a[k]) : v.every((x, k) => Math.abs(a[k] + (b[k] - a[k]) * (t - ta) / (tb - ta) - x) < 2e-4);
        if (!same) out.push(keys[i]);
    }
    if (keys.length > 1) out.push(keys.at(-1));
    return step ? out.filter((k, i) => i === 0 || i === out.length - 1 || !k[1].every((x, j) => x === out[i - 1][1][j])) : out;
}
export function build() {
    const source = JSON.parse(fs.readFileSync(sourcePath, 'utf8'));
    const groups = new Map(source.groups.map(g => [g.name, g]));
    const bb = [], runtime = {format_version: '1.8.0', animations: {}};
    const uuid = s => { const h = createHash('sha256').update('blackridge-50-anim:' + s).digest('hex'); return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-8${h.slice(17, 20)}-${h.slice(20, 32)}`; };
    const stats = {}, lhChoice = {};
    // Rotation tracks start from the idle clip's Euler representation of the same bone.
    const idleRot = new Map(Object.entries(clips.find(c => c.name === 'static_idle').build().tracks).filter(([, ch]) => ch.rotation).map(([b, ch]) => [b, ch.rotation(0)]));
    if (!idleRot.has('righthand')) idleRot.set('righthand', RIGHT_REST.rot);
    const lhIdle = leftOnGun(IDLE).rot;
    for (const c of clips) {
        const {tracks, sounds = []} = c.build();
        // Every clip carries the firing-hand grip so Blockbench previews match the in-game baseline layer.
        if (!tracks.righthand) tracks.righthand = {position: () => RIGHT_REST.pos, rotation: () => RIGHT_REST.rot};
        if (!tracks.lefthand) tracks.lefthand = lhTracks(() => leftOnGun(IDLE));
        {   // gimbal guard for the support hand
            const solved = tracks.lefthand.solved; let best = null;
            for (const cand of LH_POS_CANDIDATES) {
                const inv = T3(E(cand)), fn = t => euler(mm(E(solved(t)), inv));
                const keys = bake(fn, c.length, false, euler(mm(E(lhIdle), inv)));
                let worst = 0; for (let i = 1; i < keys.length; i++) worst = Math.max(worst, ...keys[i][1].map((v, j) => Math.abs(v - keys[i - 1][1][j])));
                if (!best || worst < best.worst - 1e-6) best = {cand, fn, worst, ref: euler(mm(E(lhIdle), inv))};
            }
            tracks.lefthand = {position: tracks.lefthand.position, rotation: best.fn, ref: best.ref};
            tracks.lefthand_pos = {rotation: () => best.cand};
            lhChoice[c.name] = {pre: best.cand, worstStep: +best.worst.toFixed(2)};
        }
        const anim = {uuid: uuid(c.name), name: c.name, loop: c.loop, override: false, length: c.length, snapping: FPS,
            selected: false, anim_time_update: '', blend_weight: '', start_delay: '', loop_delay: '', animators: {}};
        const out = {animation_length: c.length, bones: {}};
        if (c.loop === 'loop') out.loop = true; else if (c.loop === 'hold') out.loop = 'hold_on_last_frame';
        let count = 0;
        for (const [bone, chs] of Object.entries(tracks)) {
            const g = groups.get(bone); assert(g, 'Missing bone ' + bone);
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
        // Blockbench preview plays the repository OGG of each marker. The gunshot is preview-only:
        // in game it comes from the server shot path, so it never enters the runtime sound_effects.
        const preview = c.name === 'shoot' ? [...sounds, [0, 'fire']] : sounds;
        for (const [, s] of preview) assert(fs.existsSync(path.join(soundDir, `blackridge_50_${s}.ogg`)), 'Missing sound ' + s);
        if (preview.length) anim.animators.effects = {name: 'Effects', type: 'effect', keyframes: preview.map(([t, s]) => ({channel: 'sound',
            data_points: [{effect: NS + s, file: path.join(soundDir, `blackridge_50_${s}.ogg`)}], uuid: uuid(`${c.name}:sound:${t}`), time: round(t), color: -1, interpolation: 'linear'}))};
        if (sounds.length) out.sound_effects = Object.fromEntries(sounds.sort((a, b) => a[0] - b[0]).map(([t, s]) => [String(round(t)), {effect: NS + s}]));
        bb.push(anim); runtime.animations[c.name] = out; stats[c.name] = {length: c.length, bones: Object.keys(tracks).length, keys: count, sounds: sounds.map(([t, s]) => `${round(t)} ${s}`)};
    }
    return {bb, runtime, stats, lhChoice};
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
    const args = process.argv.slice(2), {bb, runtime, stats} = build();
    const emit = args.indexOf('--emit');
    if (emit >= 0) fs.writeFileSync(args[emit + 1], JSON.stringify(bb));
    if (args.includes('--write-runtime')) fs.writeFileSync(runtimePath, JSON.stringify(runtime, null, 2) + '\n');
    console.log(JSON.stringify(stats, null, 1));
}
