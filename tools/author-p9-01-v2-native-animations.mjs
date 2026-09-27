// P9-01 V2 AFL Native first-person animation authoring (light 9 mm duty pistol, MW-style handling pass).
// Same motion language and solvers as tools/author-blackridge-50-animations.mjs, re-calibrated for the P9 rig;
// knots below are the editable design; every clip is baked at 60 Hz into linear keys.
//   node tools/author-p9-01-v2-native-animations.mjs                 -> audit/summary only
//   --write-source     write the clips into src/main/blockbench/p9_01_v2_native.bbmodel
//   --write-runtime    write assets/.../animations/p9_01_v2_native.animation.json
import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {nativePath as sourcePath, ARM_HALF, MP, HP, RP, LP, LA, MAG_TILT} from './build-p9-01-v2-native.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
export const runtimePath = path.join(root, 'src/main/resources/assets/apocalypse_firstlight/animations/p9_01_v2_native.animation.json');
const NS = 'apocalypse_firstlight:p9_01_';
const soundDir = path.join(root, 'src/main/resources/assets/apocalypse_firstlight/sounds/weapons/p9_01');
const FPS = 60;

// ---------- math ----------
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

// ---------- rig facts (P9 source coordinates, +X = ejection side, muzzle -Z) ----------
const rotX = (p, deg, o) => { const c = Math.cos(deg * D), s = Math.sin(deg * D), y = p[1] - o[1], z = p[2] - o[2]; return [p[0], o[1] + y * c - z * s, o[2] + y * s + z * c]; };
const AX = norm(sub(rotX([0, -1, 0], MAG_TILT, [0, 0, 0]), Z));   // magazine axis, pointing OUT of the magwell (down and back)
const MAG_BOTTOM = rotX([0, -0.99, 1.79], MAG_TILT, MP);           // baseplate centre
const MAG_CLEAR = 4.9;                                             // axial travel that clears the magwell (magazine length + lips)
const MAG_ENTRY = 4.2;                                             // new magazine lined up this far below its seat
const SLIDE_LOCK = 1.88, SLIDE_SHOT = 1.7, SLIDE_TOP_REAR = [0, 6.0, 2.7];
const FOLLOWER_LOADED = mul(AX, 0.45);                             // loaded magazine: follower pushed down (empty = rest at the lips)
const K = ARM_HALF / (4 * 0.62 / 0.37 / 2);                        // P9 hand size relative to the Blackridge authoring hand
const Q = 0.45 / 0.53;                                             // same on-screen travel as Blackridge (Display 0.45 vs 0.53)

// ---------- motion language (same as Blackridge; P9 values are lighter and quicker) ----------
function go(t, t0, t1, {antic = 0.04, over = 0.07, per = 0.15, tau = 0.06, pre = 0.07} = {}) {
    if (t < t0 - pre) return 0;
    if (t < t0) return -antic * Math.sin(Math.PI * (t - t0 + pre) / pre);
    if (t < t1) { const u = (t - t0) / (t1 - t0); return 1.6 * u * u - 0.6 * u * u * u; }
    const s = t - t1; return 1 + over * Math.sin(2 * Math.PI * s / per) * Math.exp(-s / tau);
}
function jolt(t, te, amp, {rise = 0.017, per = 0.1, tau = 0.04} = {}) {
    const s = t - te; if (s <= 0) return Z;
    const k = s < rise ? s / rise : Math.exp(-(s - rise) / tau) * Math.cos(2 * Math.PI * (s - rise) / per);
    return mul(amp, k);
}
const sum = (...v) => v.reduce((a, b) => add(a, b), Z);

// ---------- hands (AFL standard: anchor = distal hand cap, forearm along local -Y, palm +Z) ----------
const cross = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
function levelArm(forearm, palm = [0, -1, 0]) {
    const ey = mul(norm(forearm), -1), ez = norm(sub(palm, mul(ey, palm.reduce((a, v, i) => a + v * ey[i], 0))));
    const ex = cross(ey, ez); return euler([0, 1, 2].map(r => [ex[r], ey[r], ez[r]]));
}
// Standard pistol grip (shared with Blackridge): the grip sits inside the firing hand, the hand top just under the
// beavertail, knuckles at the upper front strap, forearm level and straight back.
const RH_TOP = 4.45;
const RH_POINT = [0, RH_TOP - ARM_HALF, 0.95];
const RH_ROT = levelArm([0, -0.01, 1]);
// Support hand: Blackridge calibration scaled by hand size (cupped on the left, leaning on the firing hand).
const LG_BASE = [-2 * ARM_HALF + 0.35 * K, RH_TOP - 0.65 * K - ARM_HALF, 0.95 - 0.5 * K];
const LG_ROT = levelArm([-0.35, -0.02, 0.94]);
const LG_POINT = add(LG_BASE, mv(E(LG_ROT), [-6 * K, 0, 0]));
const HAND_ON_MAG = levelArm([-0.3, -0.55, 0.78], [1, 0.45, 0]);   // magazine space: palm cupping the base plate from the left
const HAND_ON_SLIDE = levelArm([-0.45, -0.3, 0.84]);               // gun space: palm down over the slide
const HAND_RACK = levelArm([-0.86, -0.3, 0.4]);                    // overhand rack: forearm comes in from the left, so it does not cover the gun
const OFFSCREEN_MAG = {pos: [-3.8, -10.2, 6], rot: [-45, 10, 35]};

// ---------- rig solvers ----------
const handleWorld = (h, x) => add(add(h.pos, HP), mv(E(h.rot), sub(x, HP)));
function magInGun(h, d, lr = Z) {
    const R = mm(E(h.rot), E(lr));
    const pos = sub(handleWorld(h, add(MP, mul(AX, d))), MP);
    return {pos, rot: euler(R), R};
}
const magPointWorld = (m, x) => add(add(m.pos, MP), mv(m.R ?? E(m.rot), sub(x, MP)));
const solve = (pivot, anchor) => (point, R) => ({pos: sub(sub(point, pivot), mv(R, sub(anchor, pivot))), rot: euler(R)});
const handAt = solve(LP, LA);
const RIGHT_REST = solve(RP, HP)(RH_POINT, E(RH_ROT));            // righthand lives under handling; right_hand_anchor rests at HP
const LOW_LEFT = handAt([-6, -7, 7.6], E(levelArm([-0.25, -0.55, 0.8])));
const leftOnGun = h => handAt(handleWorld(h, LG_POINT), mm(E(h.rot), E(LG_ROT)));
const onMag = (m, gap = 0) => handAt(add(magPointWorld(m, MAG_BOTTOM), mv(m.R, mul(AX, 0.12 + gap))), mm(m.R, E(HAND_ON_MAG)));
// Two-stage insert: pushed most of the way in while held, the palm drops off, then slaps it home exactly at `seat`
// (magazine_seat is a palm-slap sound). depth = distance still to travel along the magazine axis.
// Sound: magazine_in starts 0.275 s before the seat so its built-in catch click (0.28 s into the file) lands on the
// slap together with magazine_seat; its slide-in scrape covers the magazine entering the well.
const PART = 0.9, SLAP_GAP = 1.1;
const seatDepth = (t, align, seat) => t < seat - 0.12 ? PART + (MAG_ENTRY - PART) * (1 - go(t, align + 0.03, seat - 0.12, {antic: 0.03, over: 0, pre: 0.03}))
    : t < seat - 0.035 ? PART : PART * (1 - clamp01((t - seat + 0.035) / 0.035));
const slapGap = (t, seat) => t < seat - 0.12 || t >= seat - 0.035 ? 0
    : t < seat - 0.07 ? SLAP_GAP * go(t, seat - 0.12, seat - 0.07, {antic: 0, over: 0}) : SLAP_GAP * (1 - ((t - seat + 0.07) / 0.035) ** 2);
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
const LH_POS_CANDIDATES = [[0, 90, 0], [0, -90, 0], [90, 0, 0], [-90, 0, 0], [0, 0, 90], [0, 0, -90], [0, 0, 0]];
const T3 = m => [0, 1, 2].map(i => [0, 1, 2].map(j => m[j][i]));
const lhTracks = LH => ({position: t => LH(t).pos, solved: t => LH(t).rot});
const HIDE_HELPERS = {reload_magazine: {scale: () => [0, 0, 0], step: true}, empty_old_mag: {scale: () => [0, 0, 0], step: true}};


// ---------- design poses (MW-style composition: raised to screen centre, muzzle up-left, left side to the eye) ----------
// The Vanilla arm is a rigid block with no wrist and the grip sits inside it, so to expose the magwell the gun must
// slip sideways out of the fist (the hand counter-moves and stays put). The slip is the minimum that clears it
// (half hand width + half grip width); a slide along the grip axis alone leaves the magwell behind the forearm.
const RELOAD_POSE = {pos: [0.6, 3.6, 2.2], rot: [30, 42, -22]};
const GRIP_SLIDE = [-(ARM_HALF + 0.76), 0.25, 0];
const INSPECT_A = {pos: [0.8, 3.8, 2.4], rot: [26, 54, -16]};      // left side presented, raised
const INSPECT_B = {pos: [-0.6, 2.6, 1.2], rot: [16, -2, 48]};       // press check: wrist rolled in about the forearm (pitch keeps the rigid forearm low), port to the eye
const INSPECT_C = {pos: [-0.8, 2.6, 1.2], rot: [16, -2, 68]};       // empty: roll about the forearm, open port and chamber to the eye
const FIRST_DRAW_POSE = {pos: [-1.0, 2.0, 1.2], rot: [13, -2, 44]};  // chest-left, wrist rolled in (about the forearm) so the slide faces the rack
const SLIDE_TOP_FRONT = [0, 6.0, -5.3];                             // front serrations (press-check pinch)
const LOW = {pos: [0.5, -11, 0.85], rot: [-20, -8, -24]};           // below frame; the rigid forearm limits wrist angles

// ---------- clips ----------
function clip(name, length, loop, build) { return {name, length, loop, build}; }
const clips = [];
const poseMix = (weights) => ({pos: weights.reduce((a, [p, w]) => add(a, mul(p.pos, w)), Z), rot: weights.reduce((a, [p, w]) => add(a, mul(p.rot, w)), Z)});
const slideHand = (h, point, z, shape = HAND_RACK) => handAt(handleWorld(h, add(point, [0, 0, z])), mm(E(h.rot), E(shape)));

clips.push(clip('static_idle', 0.25, 'hold', () => ({tracks: {
    righthand: {position: () => RIGHT_REST.pos, rotation: () => RIGHT_REST.rot},
    lefthand: lhTracks(() => leftOnGun(IDLE)),
    follower: {position: () => FOLLOWER_LOADED}, ...HIDE_HELPERS}})));

clips.push(clip('empty_idle', 0.25, 'loop', () => ({tracks: {
    slide: {position: () => [0, 0, SLIDE_LOCK]}, follower: {position: () => Z}, ...HIDE_HELPERS}})));

// Shoot: short, snappy 9 mm cycle. Native recoil owns the camera; this is the gun's mechanical read.
clips.push(clip('shoot', 0.3, 'once', () => {
    const slide = curve1([[0, 0], [0.01, 0.45], [0.024, SLIDE_SHOT], [0.036, SLIDE_SHOT], [0.07, 0.08], [0.078, 0]]);
    const H = t => ({
        rot: sum(jolt(t, 0, [4.6, 0.5, 1.5], {rise: 0.018, per: 0.2, tau: 0.05}), jolt(t, 0.078, [0.5, 0, -0.25], {per: 0.07, tau: 0.02})),
        pos: sum(jolt(t, 0, [0, 0.14, 0.65], {rise: 0.016, per: 0.2, tau: 0.05}), jolt(t, 0.078, [0, 0, 0.07], {per: 0.07, tau: 0.02}))});
    return {tracks: {
        handling: {position: t => H(t).pos, rotation: t => H(t).rot},
        lefthand: lhTracks(t => leftOnGun(H(t))),
        slide: {position: t => [0, 0, slide(t)]}}};
}));

// Draw (every switch): quick rise from low right, canted in, support hand joins late.
clips.push(clip('draw', 0.5, 'once', () => {
    const H = t => { const w = go(t, 0.02, 0.22, {antic: 0, over: 0.08, per: 0.18, tau: 0.06});
        return {pos: sum(lerp(LOW.pos, Z, w), jolt(t, 0.3, [0, -0.12, 0])), rot: sum(lerp(LOW.rot, Z, w), jolt(t, 0.3, [1.2, 0, -0.5], {per: 0.11, tau: 0.035}))}; };
    const LH = t => mixT(LOW_LEFT, leftOnGun(H(t)), clamp01(go(t, 0.12, 0.3, {antic: 0, over: 0})));
    return {tracks: {
        handling: {position: t => H(t).pos, rotation: t => H(t).rot},
        lefthand: lhTracks(LH),
        camera: {rotation: t => sum(mul([-1.2, 0.5, 1.0], 1 - go(t, 0.02, 0.24, {antic: 0, over: 0.1})), jolt(t, 0.3, [0.6, -0.15, -0.3]))},
    }, sounds: [[0, 'draw']]};
}));

// Put away: support hand off first, then the gun rolls in and drops out low right.
clips.push(clip('put_away', 0.5, 'once', () => {
    const H = t => { const w = go(t, 0.08, 0.42, {antic: 0.04, over: 0});
        return {pos: lerp(Z, LOW.pos, w), rot: lerp(Z, LOW.rot, w)}; };
    const LH = t => mixT(leftOnGun(H(t)), LOW_LEFT, clamp01(go(t, 0.0, 0.2, {antic: 0, over: 0})));
    return {tracks: {
        handling: {position: t => H(t).pos, rotation: t => H(t).rot},
        lefthand: lhTracks(LH),
        camera: {rotation: t => mul([-1.0, 0.4, 0.9], go(t, 0.08, 0.42, {antic: 0, over: 0}))},
    }, sounds: [[0, 'put_away']]};
}));

// First draw (first equip / empty chamber): up to chest-left with the slide rolled toward the eye, the support hand
// racks it overhand, then the gun settles into the two-handed idle. Needs a Java trigger to be played.
clips.push(clip('first_draw', 1.2, 'once', () => {
    const T = {up: 0.26, reach: 0.14, grab: 0.42, pull: 0.46, pulled: 0.56, let: 0.62, ret: 0.68, retEnd: 0.98};
    const slide = t => t < T.pull ? 0 : t < T.let ? (SLIDE_LOCK + 0.08) * clamp01(go(t, T.pull, T.pulled, {antic: 0, over: 0})) : (SLIDE_LOCK + 0.08) * (1 - clamp01((t - T.let) / 0.028));
    const hits = t => sum(jolt(t, T.pulled, [-1.2, 0.6, 0.8], {rise: 0.03, per: 0.14, tau: 0.04}), jolt(t, T.let, [2.4, 0.3, -1.4], {per: 0.1, tau: 0.04}));
    const H = t => { const a = go(t, 0.02, T.up, {antic: 0, over: 0.06, per: 0.18, tau: 0.06}), b = go(t, T.ret, T.retEnd, {antic: 0.03, over: 0.08, per: 0.2, tau: 0.07});
        const up = {pos: lerp(LOW.pos, FIRST_DRAW_POSE.pos, a), rot: lerp(LOW.rot, FIRST_DRAW_POSE.rot, a)};
        return {pos: add(lerp(up.pos, Z, b), mul([0, 0.1, 0.2], hits(t)[0])), rot: add(lerp(up.rot, Z, b), hits(t))}; };
    const onSlide = t => slideHand(H(t), SLIDE_TOP_REAR, slide(t));
    const LH = t => {
        if (t < T.grab) return mixT(LOW_LEFT, onSlide(t), clamp01(go(t, T.reach, T.grab, {antic: 0, over: 0})));
        if (t < T.let + 0.04) return onSlide(t);
        return mixT(onSlide(t), leftOnGun(H(t)), go(t, T.let + 0.04, T.retEnd, {antic: 0, over: 0.04}));
    };
    return {tracks: {
        handling: {position: t => H(t).pos, rotation: t => H(t).rot},
        slide: {position: t => [0, 0, slide(t)]},
        lefthand: lhTracks(LH),
        camera: {rotation: t => sum(mul([-1.4, 0.6, 1.2], 1 - go(t, 0.02, 0.3, {antic: 0, over: 0.1})), mul(hits(t), 0.35))},
    }, sounds: [[0, 'draw'], [T.pull - 0.01, 'slide_back'], [T.let - 0.005, 'slide_release']]};
}));

// Reloads, MW-style: support hand leaves at once while the gun comes up to screen centre, muzzle up-left; the grip
// slips just clear of the fist. Tactical: thumb release, the old magazine drops, fresh one from low left, palm slap.
// Empty: the wrist whips up so the empty magazine is flung forward out of the well, fresh magazine, palm slap, then
// the support hand racks the slide overhand off the stop.
function reload(empty) {
    const T = empty
        ? {pose: 0.24, release: 0.26, flick: 0.3, pick: 0.34, carry: 0.46, align: 0.66, seat: 0.9, hide: 1.6, grab: 1.06, pull: 1.12, pulled: 1.22, let: 1.26, ret: 1.4, retEnd: 1.68, end: 2.0}
        : {pose: 0.26, release: 0.24, flick: null, pick: 0.3, carry: 0.42, align: 0.62, seat: 0.86, hide: 0.85, ret: 1.0, retEnd: 1.28, end: 1.7};
    const P = RELOAD_POSE;
    const w = t => go(t, 0.0, T.pose, {antic: 0.04, over: 0.06, per: 0.18, tau: 0.07}) - go(t, T.ret, T.retEnd, {antic: 0.04, over: 0.08, per: 0.2, tau: 0.07});
    // Empty flick: wind down a touch, whip the muzzle up hard (the well swings to face forward), follow through, recover.
    const flick = t => !empty ? 0 : go(t, T.flick - 0.07, T.flick + 0.02, {antic: 0.35, pre: 0.1, over: 0.18, per: 0.22, tau: 0.07})
        - go(t, T.flick + 0.1, T.flick + 0.36, {antic: 0, over: 0.05});
    const FLICK = {rot: [38, -22, 12], pos: [0, 1.4, -1.4]};         // peak: muzzle up, slightly left; magwell faces forward-left
    const hits = t => sum(
        jolt(t, T.seat, [2.6, -0.5, 1.3], {rise: 0.017, per: 0.1, tau: 0.045}),
        empty ? jolt(t, T.pulled, [-1.4, 1.6, 1.2], {rise: 0.03, per: 0.14, tau: 0.045}) : Z,
        empty ? jolt(t, T.let, [2.6, 0.4, -1.6], {rise: 0.017, per: 0.1, tau: 0.045}) : Z,
        jolt(t, T.release, [-0.5, 0, 0.3]));
    const shiftW = t => clamp01(w(t));
    const H = t => { const rot = sum(mul(P.rot, w(t)), mul(FLICK.rot, flick(t)), hits(t));
        return {rot, pos: sum(mul(P.pos, w(t)), mul(FLICK.pos, flick(t)), mv(E(rot), mul(GRIP_SLIDE, shiftW(t))), mul([0, 0.1, 0], hits(t)[0]))}; };
    // Old magazine leaves along its own axis, then keeps its true world velocity (gun motion included, no kink).
    // Tactical: dropped, gravity only. Empty: flung at the peak of the whip, so it sails forward and tumbles.
    const g = empty ? 190 : 520, t0 = empty ? T.flick - 0.02 : T.release + 0.02, EJ = empty ? 55 : 0;   // empty: let go on the upswing, so it rises first
    const axial = t => t < t0 ? 0 : EJ * (t - t0) + (empty ? 0 : 0.5 * g * (t - t0) ** 2);
    const tClear = empty ? t0 + MAG_CLEAR / EJ : t0 + Math.sqrt(2 * MAG_CLEAR / g);
    const inWell = t => magInGun(H(t), axial(t));
    const vRel = mul(sub(inWell(tClear).pos, inWell(tClear - 0.004).pos), 1 / 0.004);
    const oldMag = t => {
        if (t <= tClear) return inWell(t);
        const base = inWell(tClear), dt = t - tClear;
        const pos = add(add(base.pos, mul(vRel, dt)), [0, -0.5 * g * dt * dt, 0]);
        const R = mm(base.R, E(empty ? [620 * dt, -40 * dt, 60 * dt] : [-160 * dt, 0, 60 * dt])); return {pos, rot: euler(R), R};
    };
    const newMag = t => {
        if (t < T.align) {
            const s = clamp01(go(t, T.carry, T.align, {antic: 0, over: 0.04, per: 0.1, tau: 0.035})), g0 = magInGun(H(t), MAG_ENTRY);
            const R = E(lerp(OFFSCREEN_MAG.rot, g0.rot, s));
            return {pos: lerp(OFFSCREEN_MAG.pos, g0.pos, s), rot: euler(R), R};
        }
        return magInGun(H(t), Math.max(0, seatDepth(t, T.align, T.seat)));
    };
    const RACK = 0.3;
    const slidePos = t => !empty ? 0 : t < T.pull ? SLIDE_LOCK : t < T.let ? SLIDE_LOCK + RACK * clamp01(go(t, T.pull, T.pulled, {antic: 0, over: 0}))
        : (SLIDE_LOCK + RACK) * (1 - clamp01((t - T.let) / 0.028));
    const onSlide = t => slideHand(H(t), SLIDE_TOP_REAR, slidePos(t));
    const LH = t => {
        if (t < T.pick) return mixT(leftOnGun(H(t)), LOW_LEFT, go(t, 0.0, T.pick, {antic: 0, over: 0}));
        if (t < T.seat + 0.06) return mixT(LOW_LEFT, onMag(newMag(Math.min(t, T.seat)), slapGap(t, T.seat)), clamp01(go(t, T.pick, T.carry + 0.04, {antic: 0, over: 0})));
        if (!empty) return mixT(onMag(newMag(T.seat)), leftOnGun(H(t)), go(t, T.seat + 0.06, T.ret + 0.1, {antic: 0.02, over: 0.04}));
        if (t < T.let + 0.04) return mixT(onMag(newMag(T.seat)), onSlide(t), clamp01(go(t, T.seat + 0.05, T.grab, {antic: 0.02, over: 0})));
        return mixT(onSlide(t), leftOnGun(H(t)), go(t, T.let + 0.04, T.ret + 0.14, {antic: 0, over: 0.04}));
    };
    const camera = t => sum(mul([-1.2, 1.4, 1.7], w(t)), mul([1.2, 0, 0.4], flick(t)), mul(hits(t), 0.4), jolt(t, T.seat, [1.0, 0, 0.35]));
    const tracks = {
        handling: {position: t => H(t).pos, rotation: t => H(t).rot},
        magazine: {scale: t => (t >= t0 && t < T.seat) ? [0, 0, 0] : [1, 1, 1], step: true},
        follower: {position: t => empty && t < t0 ? Z : FOLLOWER_LOADED},   // empty magazine until the swap
        empty_old_mag: {position: t => oldMag(t).pos, rotation: t => oldMag(t).rot, scale: t => (t >= t0 && t < T.hide) ? [1, 1, 1] : [0, 0, 0], step: true},
        mag_out: {position: t => newMag(t).pos, rotation: t => newMag(t).rot},
        reload_magazine: {scale: t => (t >= T.carry && t < T.seat) ? [1, 1, 1] : [0, 0, 0], step: true},
        righthand: {position: t => sub(RIGHT_REST.pos, mul(GRIP_SLIDE, shiftW(t))), rotation: () => RIGHT_REST.rot},
        lefthand: lhTracks(LH),
        camera: {rotation: camera},
    };
    if (empty) tracks.slide = {position: t => [0, 0, slidePos(t)]};
    // foley_raise / foley_lower markers removed on user request (2026-09-27); files stay registered for later use.
    const sounds = [[T.release - 0.01, 'magazine_release'], [(empty ? T.flick : T.release) + 0.02, 'magazine_out'],
        [T.seat - 0.275, 'magazine_in'], [T.seat - 0.005, 'magazine_seat'], ...(empty ? [[T.pull - 0.01, 'slide_back'], [T.let - 0.005, 'slide_release']] : [])];
    return {tracks, sounds, timing: T};
}
clips.push(clip('reload_tactical', 1.7, 'once', () => reload(false)));
clips.push(clip('reload_empty', 2.0, 'once', () => reload(true)));

// Inspect (CoD-style). Loaded: wrist rolls in, the support hand pinches the front serrations for two quick short
// pulls and a deep look, then the gun turns to the left side, the magazine drops into the waiting hand, is shown
// lips-to-eye, slapped home, and a final confirming palm tap on the base plate closes the inspect.
// Empty (slide locked): open port and chamber to the eye, empty magazine shown and slapped home, one overhand rack
// that catches on the empty follower again (ends locked back, matching empty_idle), final palm tap.
// tapGap: the palm drops away from the base plate and strikes it at `contact` (magazine already seated).
const tapGap = (t, contact) => t < contact - 0.14 || t >= contact ? 0
    : t < contact - 0.07 ? 1.3 * go(t, contact - 0.14, contact - 0.07, {antic: 0, over: 0}) : 1.3 * (1 - ((t - contact + 0.07) / 0.07) ** 2);
function inspect(empty) {
    const T = empty
        ? {up: 0.45, portEnd: 1.45, side: 1.85, release: 2.0, drop: 2.02, out: 2.3, show: 2.62, showEnd: 3.2, align: 3.28, seat: 3.6,
           grab: 3.78, pulls: [[3.86, 4.02, 4.08, 0.028]], tap: 4.46, back: 4.56, backEnd: 4.86, end: 5.3}
        : {up: 0.4, grab: 0.46, pulls: [[0.52, 0.68, 0.76, 0.1], [0.94, 1.1, 1.18, 0.1], [1.36, 1.58, 2.03, 0.12]], turn: 2.22, turned: 2.5,
           release: 2.62, drop: 2.64, out: 2.92, show: 3.27, showEnd: 3.8, align: 3.88, seat: 4.2, tap: 4.56, back: 4.66, backEnd: 4.96, end: 5.4};
    const DEPTH = empty ? [0.3] : [0.35, 0.35, 0.75];           // loaded: short, short, deep look; empty: one rack past the lock
    const A = INSPECT_A, B = INSPECT_B, C = INSPECT_C;
    const out = t => go(t, T.back, T.backEnd, {antic: 0.03, over: 0.08, per: 0.2, tau: 0.08});
    const wC = t => !empty ? 0 : go(t, 0.02, T.up, {antic: 0.05, over: 0.07}) - go(t, T.portEnd, T.side, {antic: 0.02, over: 0.05});
    const wB = t => empty ? 0 : go(t, 0.02, T.up, {antic: 0.05, over: 0.07}) - go(t, T.turn, T.turned, {antic: 0.03, over: 0.05});
    const wA = t => (empty ? go(t, T.portEnd, T.side, {antic: 0.02, over: 0.05}) : go(t, T.turn, T.turned, {antic: 0.03, over: 0.05})) - out(t);
    const lastLet = T.pulls.at(-1)[2] + T.pulls.at(-1)[3];      // slide back in battery / on the lock
    const hits = t => sum(jolt(t, T.release, [-0.6, 0, 0.4]), jolt(t, T.out, [0.5, 0, -0.25]), jolt(t, T.seat, [2.4, -0.4, 1.1]),
        jolt(t, T.tap, [1.8, -0.3, 0.8], {rise: 0.015, per: 0.09, tau: 0.035}),
        ...T.pulls.flatMap(([p0, p1, let_, ret], i) => [jolt(t, p1, mul([-0.9, 0.35, 0.5], DEPTH[i] / 0.5), {rise: 0.03, per: 0.13, tau: 0.04}),
            jolt(t, let_ + ret, mul([1.6, 0.25, -1.0], empty ? 1 : DEPTH[i] / 0.6), {per: 0.1, tau: 0.035})]));
    const shiftW = t => clamp01(wA(t));        // grip slips clear of the fist while the magazine side faces the support hand
    const [, lp1, llet, lret] = T.pulls.at(-1), lend = llet + lret;
    const look = t => empty ? mul([0, 0, 8], Math.sin(2 * Math.PI * clamp01((t - T.up) / (T.portEnd - T.up))) * wC(t))      // roll to read the chamber
        : mul([0, 6, -8], Math.sin(Math.PI * clamp01((t - lp1) / (llet - lp1))));                                              // deep press-check look
    const H = t => { const m = poseMix([[A, wA(t)], [B, wB(t)], [C, wC(t)]]), rot = sum(m.rot, look(t), hits(t));
        return {rot, pos: sum(m.pos, mv(E(rot), mul(GRIP_SLIDE, shiftW(t))), mul([0, 0.1, 0], hits(t)[0]))}; };
    // Magazine slides down its axis into the cupped support hand, is turned lips-to-eye, then slapped home.
    const axial = t => MAG_CLEAR * clamp01(go(t, T.drop, T.out, {antic: 0, over: 0}));
    const showPose = {pos: [-6.2, 4.6, 8.6], rot: [78, -18, 10]};
    const held = t => {
        if (t < T.out) return magInGun(H(t), axial(t));
        if (t < T.showEnd) {
            const a = magInGun(H(T.out), MAG_CLEAR), s = go(t, T.out + 0.04, T.show, {antic: 0, over: 0.05});
            const wig = mul([5, -8, 3], Math.sin(2 * Math.PI * clamp01((t - T.show) / (T.showEnd - T.show))) * clamp01((t - T.show) / 0.1));
            const R = E(add(lerp(a.rot, showPose.rot, s), wig)); return {pos: lerp(a.pos, showPose.pos, s), rot: euler(R), R};
        }
        if (t < T.align) {
            const g = magInGun(H(t), MAG_ENTRY), s = go(t, T.showEnd, T.align, {antic: 0, over: 0.04, per: 0.1, tau: 0.035});
            const R = E(lerp(showPose.rot, g.rot, s)); return {pos: lerp(showPose.pos, g.pos, s), rot: euler(R), R};
        }
        return magInGun(H(t), Math.max(0, seatDepth(t, T.align, T.seat)));
    };
    // Slide: sum of pulls, each [start, fully pulled, return starts, return time]. Loaded: hand-eased return (press check);
    // empty: let go, it snaps back onto the lock. Loaded rests at 0; empty rests on the lock and catches again.
    const slideBase = empty ? SLIDE_LOCK : 0;
    const ease = u => u * u * (3 - 2 * u);
    const slide = t => slideBase + T.pulls.reduce((acc, [p0, p1, let_, ret], i) => acc + (t < p0 ? 0 : t < let_
        ? DEPTH[i] * clamp01(go(t, p0, p1, {antic: 0, over: 0})) : DEPTH[i] * (1 - (empty ? clamp01((t - let_) / ret) : ease(clamp01((t - let_) / ret))))), 0);
    const onSlide = t => slideHand(H(t), empty ? SLIDE_TOP_REAR : SLIDE_TOP_FRONT, slide(t));
    const onBase = t => onMag(held(Math.min(t, T.seat)), t < T.seat + 0.06 ? slapGap(t, T.seat) : tapGap(t, T.tap));
    const LH = t => {
        if (!empty) {
            if (t < T.grab) return mixT(leftOnGun(H(t)), onSlide(t), clamp01(go(t, 0.16, T.grab, {antic: 0, over: 0})));
            if (t < lend + 0.05) return onSlide(t);
            if (t < T.drop) return mixT(onSlide(t), onMag(held(t)), clamp01(go(t, lend + 0.05, T.release - 0.02, {antic: 0.02, over: 0})));
            if (t < T.tap + 0.04) return onBase(t);
            return mixT(onBase(T.tap), leftOnGun(H(t)), go(t, T.tap + 0.04, T.back + 0.15, {antic: 0, over: 0.04}));
        }
        if (t < T.drop) return mixT(leftOnGun(H(t)), onMag(held(t)), clamp01(go(t, T.release - 0.3, T.release - 0.02, {antic: 0.02, over: 0})));
        if (t < T.seat + 0.06) return onBase(t);
        if (t < lastLet + 0.04) return mixT(onBase(T.seat + 0.06), onSlide(t), clamp01(go(t, T.seat + 0.05, T.grab, {antic: 0.02, over: 0})));
        if (t < T.tap + 0.04) return mixT(onSlide(t), onBase(t), clamp01(go(t, lastLet + 0.04, T.tap - 0.14, {antic: 0, over: 0})));
        return mixT(onBase(T.tap), leftOnGun(H(t)), go(t, T.tap + 0.04, T.back + 0.15, {antic: 0, over: 0.04}));
    };
    const camera = t => sum(mul([-1.2, 1.8, 2.0], wA(t)), mul([-1.4, -1.2, -1.4], wB(t)), mul([-1.0, -1.6, 1.2], wC(t)), mul(hits(t), 0.35),
        jolt(t, T.seat, [1.0, 0, 0.35]), jolt(t, T.tap, [0.7, 0, 0.25]));
    const tracks = {
        handling: {position: t => H(t).pos, rotation: t => H(t).rot},
        magazine: {scale: t => (t >= T.drop && t < T.seat) ? [0, 0, 0] : [1, 1, 1], step: true},
        follower: {position: () => empty ? Z : FOLLOWER_LOADED},
        mag_out: {position: t => held(t).pos, rotation: t => held(t).rot},
        reload_magazine: {scale: t => (t >= T.drop && t < T.seat) ? [1, 1, 1] : [0, 0, 0], step: true},
        empty_old_mag: {scale: () => [0, 0, 0], step: true},
        righthand: {position: t => sub(RIGHT_REST.pos, mul(GRIP_SLIDE, shiftW(t))), rotation: () => RIGHT_REST.rot},
        lefthand: lhTracks(LH),
        slide: {position: t => [0, 0, slide(t)]},
        camera: {rotation: camera},
    };
    const sounds = [[T.release - 0.01, 'magazine_release'], [T.release + 0.03, 'magazine_out'], [T.seat - 0.275, 'magazine_in'], [T.seat - 0.005, 'magazine_seat'],
        ...T.pulls.flatMap(([p0, , let_, ret]) => [[p0 - 0.01, 'slide_back'], [let_ + ret - 0.015, 'slide_release']]), [T.tap - 0.005, 'magazine_seat']];
    return {tracks, sounds, timing: T};
}
clips.push(clip('inspect', 5.4, 'once', () => inspect(false)));
clips.push(clip('inspect_empty', 5.3, 'once', () => inspect(true)));

// ---------- bake ----------
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
export function build() {
    const source = JSON.parse(fs.readFileSync(sourcePath, 'utf8'));
    const groups = new Map(source.groups.map(g => [g.name, g]));
    const bb = [], runtime = {format_version: '1.8.0', animations: {}};
    const uuid = s => { const h = createHash('sha256').update('p9-01-v2-native-anim:' + s).digest('hex'); return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-8${h.slice(17, 20)}-${h.slice(20, 32)}`; };
    const stats = {}, lhChoice = {}, timings = {};
    const idleRot = new Map(Object.entries(clips.find(c => c.name === 'static_idle').build().tracks).filter(([, ch]) => ch.rotation).map(([b, ch]) => [b, ch.rotation(0)]));
    idleRot.set('righthand', RIGHT_REST.rot);
    const lhIdle = leftOnGun(IDLE).rot;
    for (const c of clips) {
        const {tracks, sounds = [], timing} = c.build();
        if (timing) timings[c.name] = timing;
        if (!tracks.righthand) tracks.righthand = {position: () => RIGHT_REST.pos, rotation: () => RIGHT_REST.rot};
        if (!tracks.lefthand) tracks.lefthand = lhTracks(() => leftOnGun(IDLE));
        // Helper magazines are hidden explicitly in every clip that does not drive them (no reliance on the idle layer).
        for (const [bone, ch] of Object.entries(HIDE_HELPERS)) if (!tracks[bone]) tracks[bone] = ch;
        {   // gimbal guard for the support hand: constant per-clip pre-rotation on lefthand_pos
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
        for (const [, s] of sounds) assert(fs.existsSync(path.join(soundDir, `p9_01_${s}.ogg`)), 'Missing sound ' + s);
        if (sounds.length) {
            anim.animators.effects = {name: 'Effects', type: 'effect', keyframes: sounds.map(([t, s]) => ({channel: 'sound',
                data_points: [{effect: NS + s, file: path.join(soundDir, `p9_01_${s}.ogg`)}], uuid: uuid(`${c.name}:sound:${t}`), time: round(t), color: -1, interpolation: 'linear'}))};
            out.sound_effects = Object.fromEntries(sounds.sort((a, b) => a[0] - b[0]).map(([t, s]) => [t === 0 ? '0.0' : String(round(t)), {effect: NS + s}]));
        }
        bb.push(anim); runtime.animations[c.name] = out;
        stats[c.name] = {length: c.length, bones: Object.keys(tracks).length, keys: count, sounds: sounds.map(([t, s]) => `${round(t)} ${s}`)};
    }
    return {bb, runtime, stats, lhChoice, timings};
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
    const args = process.argv.slice(2), {bb, runtime, stats, lhChoice} = build();
    if (args.includes('--write-source')) {
        const source = JSON.parse(fs.readFileSync(sourcePath, 'utf8'));
        source.animations = bb; fs.writeFileSync(sourcePath, JSON.stringify(source));
    }
    if (args.includes('--write-runtime')) fs.writeFileSync(runtimePath, JSON.stringify(runtime, null, 2) + '\n');
    console.log(JSON.stringify({stats, lhChoice}, null, 1));
}
