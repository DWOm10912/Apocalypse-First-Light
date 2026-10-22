// P9-01 V3 AFL Native first-person animation authoring (light 9 mm duty pistol). Started as a copy of
// tools/author-p9-01-v2-native-animations.mjs (same rig, same bone names, same idle / shoot / draw clips); the reloads and
// inspects are the trained-tier remake of docs/dev/p9_reload_inspect_v1.md (2026-10-17).
// Same motion language and solvers as tools/author-blackridge-50-animations.mjs, re-calibrated for the P9 rig;
// knots below are the editable design; every clip is baked at 60 Hz into linear keys.
// Hand-keyed clips (HAND_KEYED, 2026-10-22): the listed channels are bezier keys placed by hand in Blockbench and read
// back from the .bbmodel; the script only solves the rest of the clip from them (magazine in the hand, the insert, the
// falling magazine, the grip slip) and bakes everything to 60 Hz for the runtime.
//   node tools/author-p9-01-v3-native-animations.mjs                 -> audit/summary only
//   --write-source     write the clips into src/main/blockbench/p9_01_v3_native.bbmodel (hand keys kept verbatim)
//   --write-runtime    write assets/.../animations/p9_01_v3_native.animation.json
import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {ARM_HALF, MP, HP, RP, LP, LA, MAG_TILT} from './build-p9-01-v2-native.mjs';
import {nativePath as sourcePath} from './build-p9-01-v3-native.mjs';
import {bbChannel} from './bb-keyframes.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
export const runtimePath = path.join(root, 'src/main/resources/assets/apocalypse_firstlight/animations/p9_01_v3_native.animation.json');
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
export const SLIDE_LOCK = 1.88, SLIDE_TOP_REAR = [0, 6.0, 2.7];
const SLIDE_SHOT = 1.7;
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
const GRIP_SLIDE = [-(ARM_HALF + 0.76), 0.25, 0];
const FIRST_DRAW_POSE = {pos: [-1.0, 2.0, 1.2], rot: [13, -2, 44]};  // chest-left, wrist rolled in (about the forearm) so the slide faces the rack
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

// ---------- trained-tier reloads and inspects (docs/dev/p9_reload_inspect_v1.md, 2026-10-17) ----------
// References: the user's CS2 FN57 and COD P890 recordings (2026-10-16). What the V2 clips lacked: the gun stood still
// while the support hand worked and the end was dead time. The reloads and inspects are hand keyed in Blockbench now
// (bezier keys, 2026-10-22); the gun never stops: it comes up, floats toward the incoming magazine, is knocked up by the
// seat, swings home and settles. The unload is still a chain of legs, each easing from the previous pose with `go`.
// The firing arm is a rigid box without a wrist: the gun turns with the whole forearm, and the support arm only ever
// comes in from below / lower left so its box does not cover the gun (V2's inspect and empty rack did).
const SLIP = +(process.env.P9_SLIP ?? 1);      // grip slip out of the fist while the magwell faces the support hand
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
// a slow look-over sway (the lower tiers' procedural reloads)
const drift = (t, t0, t1, amp, per) => t <= t0 || t >= t1 ? Z
    : mul(amp, Math.sin(2 * Math.PI * (t - t0) / per) * clamp01((t - t0) / 0.25) * clamp01((t1 - t) / 0.25));
const RELOAD_UP ={pos: [0.4, 3.4, 2.0], rot: [27, 40, -24]};     // up and rolled: the magwell faces the support hand
const RELOAD_MEET = {pos: [-0.3, 2.8, 2.3], rot: [23, 35, -30]};  // floated down-left toward the incoming magazine
const RELOAD_HOLD = {pos: [0.1, 3.1, 2.1], rot: [26, 38, -25]};   // after the seat (empty: until the slide is let go)

// ADS reload (docs/dev/p9_reload_inspect_v1.md 2.7): played while aiming, under the ADS correction (sights at the eye).
// Eyes stay on the target: the gun only dips a little below the line of sight and cants, the hands work below the frame
// by feel; the magazine change reads through the gun's twitches and the sounds. Same beats as the hip clips.
const ADS_DIP = (process.env.P9_ADS_DIP || '0,-1.7,0.6,-3,2,-10').split(',').map(Number);
const RELOAD_ADS_DIP = {pos: ADS_DIP.slice(0, 3), rot: ADS_DIP.slice(3)};
const RELOAD_ADS_MEET = {pos: add(RELOAD_ADS_DIP.pos, [-0.15, -0.2, 0]), rot: add(RELOAD_ADS_DIP.rot, [-1, 1.5, -3])};

// Tactical: thumb release, the old magazine drops; fresh one from lower left on an arc, the gun meets it, palm slap,
// home. Empty: same, the old magazine falls free, then the right thumb drops the slide off the slide stop (trained tier:
// no overhand rack).
// No magazine in the gun (reload_*_no_mag[_ads]; ConfiguredNativeGunItem#reloadClip): no thumb on the release, nothing
// shaken out; the support hand goes straight down for the new magazine, every beat NO_MAG_CUT s earlier (whole ticks:
// the game takes the cut from the two clip lengths, length and magazine-in tick alike).
export const NO_MAG_CUT = 0.1;
// Hand-keyed tactical reload (hand = Blockbench keys of handling, lefthand and camera; docs/dev/p9_reload_inspect_v1.md
// 2.11): the keys carry the gun, the camera and the support hand's free travel. Solved from them: the magazine rides in
// the hand until the align, then the magwell constraint takes over (insert, palm off, slap) with the hand on the
// baseplate; after the slap the hand blends back onto its keys and lands on the grip of the moving gun.
const W = (t, a, b) => { const u = clamp01((t - a) / (b - a)); return u * u * (3 - 2 * u); };
const magOnHand = lh => {                      // inverse of onMag(m, 0): the magazine as held by the support hand
    const Rh = E(lh.rot), R = mm(Rh, T3(E(HAND_ON_MAG))), point = add(add(lh.pos, LP), mv(Rh, sub(LA, LP)));
    return {pos: sub(sub(sub(point, mv(R, mul(AX, 0.12))), MP), mv(R, sub(MAG_BOTTOM, MP))), rot: euler(R), R};
};
// Empty: the slide stays locked back until the right thumb drops it at `stop`; the left hand regrips after that.
// ADS (2.7) and no magazine (2.10) are keyed the same way (user 2026-10-22): ADS keys the small dip and cant under the
// sights with the hands working below them; no magazine has no release and nothing to drop, every beat NO_MAG_CUT earlier.
function reloadKeyed(hand, empty = false, ads = false, noMag = false) {
    const T = empty ? {release: 0.22, drop: 0.24, enter: 0.46, align: 0.66, seat: 0.9, stop: 1.0, regrip: 1.34, end: 2.0}
        : {release: 0.22, drop: 0.24, enter: 0.42, align: 0.62, seat: 0.86, regrip: 1.18, end: 1.7};
    if (ads || noMag) T.regrip += 0.1;   // the palm turns back onto the grip over more time (2026-10-22); the passed hip clips keep theirs
    if (noMag) for (const k of Object.keys(T)) if (k !== 'release' && k !== 'drop') T[k] = +(T[k] - NO_MAG_CUT).toFixed(3);
    const hr = hand.ch('handling', 'rotation'), hp = hand.ch('handling', 'position'), cam = hand.ch('camera', 'rotation');
    const lr = hand.ch('lefthand', 'rotation'), lp = hand.ch('lefthand', 'position'), pre = E(hand.pre);
    const H = t => ({rot: hr(t), pos: hp(t)});
    const slip = t => ads ? 0 : SLIP * clamp01(H(t).rot[1] / RELOAD_UP.rot[1]);   // the gun slips out of the fist as it turns up
    const keyedLH = t => ({pos: lp(t), rot: euler(mm(E(lr(t)), pre))});
    const LH = t => {
        const k = keyedLH(t);
        if (t < T.align) return mixT(mixT(leftOnGun(H(t)), k, W(t, 0, 0.1)), onMag(magInGun(H(t), MAG_ENTRY)), W(t, T.align - 0.06, T.align));
        if (t < T.seat + 0.06) return onMag(newMag(t), slapGap(t, T.seat));
        return mixT(mixT(onMag(newMag(t)), k, W(t, T.seat + 0.06, T.seat + 0.12)), leftOnGun(H(t)), W(t, T.regrip - 0.1, T.regrip));
    };
    const newMag = t => t < T.align ? magOnHand(LH(t)) : magInGun(H(t), Math.max(0, seatDepth(t, T.align, T.seat)));
    const g = 520, t0 = T.drop;
    const axial = t => t < t0 ? 0 : 0.5 * g * (t - t0) ** 2;
    const tClear = t0 + Math.sqrt(2 * MAG_CLEAR / g);
    const inWell = t => magInGun(H(t), axial(t));
    const vRel = mul(sub(inWell(tClear).pos, inWell(tClear - 0.004).pos), 1 / 0.004);
    const oldMag = t => {
        if (t <= tClear) return inWell(t);
        const base = inWell(tClear), dt = t - tClear;
        const pos = add(add(base.pos, mul(vRel, dt)), [0, -0.5 * g * dt * dt, 0]);
        return {pos, rot: euler(mm(base.R, E([-160 * dt, 10 * dt, 70 * dt])))};
    };
    const tracks = {
        handling: {position: hp, rotation: hr},
        magazine: {scale: t => (t >= (noMag ? 0 : t0) && t < T.seat) ? [0, 0, 0] : [1, 1, 1], step: true},
        follower: {position: t => empty && t < t0 ? Z : FOLLOWER_LOADED},   // empty magazine until the swap
        empty_old_mag: noMag ? {scale: () => [0, 0, 0], step: true}
            : {position: t => oldMag(t).pos, rotation: t => oldMag(t).rot, scale: t => (t >= t0 && t < t0 + 0.5) ? [1, 1, 1] : [0, 0, 0], step: true},
        mag_out: {position: t => newMag(t).pos, rotation: t => newMag(t).rot},
        reload_magazine: {scale: t => (t >= T.enter - 0.06 && t < T.seat) ? [1, 1, 1] : [0, 0, 0], step: true},
        righthand: {position: t => sub(RIGHT_REST.pos, mul(GRIP_SLIDE, slip(t))), rotation: () => RIGHT_REST.rot},
        lefthand: lhTracks(LH),
        camera: {rotation: cam},
    };
    if (empty) tracks.slide = {position: t => [0, 0, t < T.stop ? SLIDE_LOCK : SLIDE_LOCK * (1 - clamp01((t - T.stop) / 0.025))]};
    const sounds = [...(noMag ? [] : [[T.release - 0.01, 'magazine_release'], [T.drop + 0.02, 'magazine_out']]),
        [T.seat - 0.275, 'magazine_in'], [T.seat - 0.005, 'magazine_seat'], ...(empty ? [[T.stop + 0.015, 'slide_release']] : [])];
    return {tracks, sounds, timing: T};
}
clips.push(clip('reload_tactical', 1.7, 'once', hand => reloadKeyed(hand)));
clips.push(clip('reload_empty', 2.0, 'once', hand => reloadKeyed(hand, true)));
clips.push(clip('reload_tactical_ads', 1.7, 'once', hand => reloadKeyed(hand, false, true)));
clips.push(clip('reload_empty_ads', 2.0, 'once', hand => reloadKeyed(hand, true, true)));
clips.push(clip('reload_tactical_no_mag', +(1.7 - NO_MAG_CUT).toFixed(3), 'once', hand => reloadKeyed(hand, false, false, true)));
clips.push(clip('reload_empty_no_mag', +(2.0 - NO_MAG_CUT).toFixed(3), 'once', hand => reloadKeyed(hand, true, false, true)));
clips.push(clip('reload_tactical_no_mag_ads', +(1.7 - NO_MAG_CUT).toFixed(3), 'once', hand => reloadKeyed(hand, false, true, true)));
clips.push(clip('reload_empty_no_mag_ads', +(2.0 - NO_MAG_CUT).toFixed(3), 'once', hand => reloadKeyed(hand, true, true, true)));

// ---------- lower tiers (docs/dev/gun_proficiency_v1_plan.md 4, docs/dev/p9_reload_inspect_v1.md 5.1) ----------
// Hand keyed since 2026-10-22 (same standard as the practised clips, 2.11 / 2.12): handling, lefthand and camera are bezier
// keys in the .bbmodel; reloadTierKeyed solves the magazines, the magwell contact, the fumble, the overhand rack and the slip.
//   experienced (Lv 4-6, x1.35): the gun comes down to the chest work space instead of up to the eye line, the head dips
//     onto it, the magazine is guided in and pushed home without a stop; the empty reload racks the slide overhand.
//     From Lv 5 a reload started while aiming plays the experienced ADS clip (<clip>_experienced_ads): the practised ADS
//     keys on the experienced beats, the slide still dropped with the thumb (eyes stay on the target).
//   novice (Lv 0-3, x1.8): the gun is held close, the head well down the whole time; the thumb hunts for the release, the
//     magazine is lined up and pushed in slowly, then the gun is tipped for a second look; slow overhand rack. The fumble
//     variant (server roll, gun proficiency plan 4): the magazine goes in crooked, stops on the magwell edge, one more push
//     does not help, it comes back out, is straightened and goes in (+FUMBLE s). Never a dropped magazine or a failed reload.
// No magazine in the gun (<clip>_no_mag_<tier>): no release, nothing dropped, every beat from the pouch on NO_MAG_CUT
// earlier (the third-person action is the full one started that much in, SkeletonGunStance).
export const TIERS = {
    experienced: {
        work: {pos: [-1.3, 2.0, 3.6], rot: [20, 30, -34]}, meet: {pos: [-1.8, 1.6, 3.8], rot: [17, 26, -38]},
        hold: {pos: [-1.4, 1.9, 3.6], rot: [20, 28, -32]}, rack: {pos: [-1.0, 2.0, 1.6], rot: [13, -2, 42]},
        tactical: {up: 0.38, release: 0.3, drop: 0.33, enter: 0.62, align: 0.92, seat: 1.2, back: 1.32, backEnd: 1.85, regrip: 1.62, end: 2.3},
        empty: {up: 0.38, release: 0.3, drop: 0.32, enter: 0.66, align: 0.96, seat: 1.24, grab: 1.7, pull: 1.76, pulled: 1.88, let: 1.94,
            back: 2.06, backEnd: 2.52, regrip: 2.4, end: 2.8},
    },
    novice: {
        work: {pos: [-2.3, 2.3, 5.2], rot: [26, 22, -48]}, meet: {pos: [-2.7, 2.0, 5.4], rot: [24, 19, -52]},
        hold: {pos: [-2.3, 2.2, 5.2], rot: [25, 21, -48]}, look: {pos: [-2.0, 2.6, 5.0], rot: [40, 12, -30]},
        rack: {pos: [-1.3, 1.6, 2.2], rot: [14, -2, 44]},
        tactical: {up: 0.5, release: 0.62, drop: 0.68, enter: 1.05, align: 1.5, seat: 2.0, lookAt: 2.06, lookEnd: 2.36, back: 2.42, backEnd: 2.95,
            regrip: 2.8, end: 3.1},
        empty: {up: 0.5, release: 0.62, drop: 0.66, enter: 1.05, align: 1.5, seat: 2.0, lookAt: 2.06, lookEnd: 2.32, grab: 2.74, pull: 2.84,
            pulled: 3.04, let: 3.14, back: 3.24, backEnd: 3.64, regrip: 3.58, end: 3.8},
    },
};
// experienced ADS reload: the practised ADS keys warped onto these beats (stop: the thumb drops the slide)
export const TIER_ADS = {
    tactical: {release: 0.3, drop: 0.33, enter: 0.62, align: 0.92, seat: 1.2, regrip: 1.66, end: 2.3},
    empty: {release: 0.3, drop: 0.32, enter: 0.66, align: 0.96, seat: 1.24, stop: 1.36, regrip: 1.84, end: 2.7},
};
export const FUMBLE = 0.9;   // the first try: in crooked to the edge (0.26 s), one more push and out (0.3 s), straightened (0.34 s)
const NO_MAG_SHIFT = ['enter', 'align', 'bump', 'out', 'again', 'seat', 'stop', 'grab', 'pull', 'pulled', 'let', 'lookAt', 'lookEnd',
    'back', 'backEnd', 'regrip', 'end'];
/** A tier's beats (seconds); fumble: the first try (bump, out, again) and everything after it shifted by FUMBLE. */
export function tierTiming(empty, tierName, fumble = false, {ads = false, noMag = false} = {}) {
    const T = ads ? {...TIER_ADS[empty ? 'empty' : 'tactical']} : {...TIERS[tierName][empty ? 'empty' : 'tactical']};
    if (fumble) {   // everything from the first try on shifts by the second one
        T.bump = +(T.align + 0.26).toFixed(3); T.out = +(T.bump + 0.3).toFixed(3); T.again = +(T.align + FUMBLE).toFixed(3);
        for (const n of ['seat', 'lookAt', 'lookEnd', 'grab', 'pull', 'pulled', 'let', 'back', 'backEnd', 'regrip', 'end']) if (T[n] != null) T[n] = +(T[n] + FUMBLE).toFixed(3);
    }
    if (noMag) for (const n of NO_MAG_SHIFT) if (T[n] != null) T[n] = +(T[n] - NO_MAG_CUT).toFixed(3);
    return T;
}
/** Clip name of a tier reload: the no-magazine part before the tier, the ADS suffix last (NativeGunActions). */
export const tierClip = (empty, tierName, {fumble = false, ads = false, noMag = false} = {}) =>
    `reload_${empty ? 'empty' : 'tactical'}${noMag ? '_no_mag' : ''}_${tierName}${fumble ? '_fumble' : ''}${ads ? '_ads' : ''}`;
// the fumbled magazine: tilted on the magwell lips, how far it gets before the edge stops it, how far it is pulled back out
export const CROOKED = [7, 0, 9], BUMP_DEPTH = 3.0, OUT_DEPTH = 4.9;
const easeInTo = (t, a, b) => { const u = clamp01((t - a) / (b - a)); return u * u * (2 - u); };   // still moving at b: it hits something
function reloadTierKeyed(hand, empty, tierName, {fumble = false, ads = false, noMag = false} = {}) {
    const T = tierTiming(empty, tierName, fumble, {ads, noMag}), rack = empty && !ads;
    const hr = hand.ch('handling', 'rotation'), hp = hand.ch('handling', 'position'), cam = hand.ch('camera', 'rotation');
    const lr = hand.ch('lefthand', 'rotation'), lp = hand.ch('lefthand', 'position'), pre = E(hand.pre);
    const H = t => ({rot: hr(t), pos: hp(t)});
    // the grip slips out of the fist as the magwell turns to the support hand (none under the sights, none once the slide
    // is turned up for the rack: the yaw is back near zero there)
    const slip = t => ads ? 0 : SLIP * clamp01(H(t).rot[1] / TIERS[tierName].work.rot[1]);
    const keyedLH = t => ({pos: lp(t), rot: euler(mm(E(lr(t)), pre))});
    const slide = t => rack ? (t < T.pull ? SLIDE_LOCK : t < T.let ? SLIDE_LOCK + 0.08 * W(t, T.pull, T.pulled)
        : (SLIDE_LOCK + 0.08) * (1 - clamp01((t - T.let) / 0.028)))
        : t < T.stop ? SLIDE_LOCK : SLIDE_LOCK * (1 - clamp01((t - T.stop) / 0.025));
    const onSlide = t => slideHand(H(t), SLIDE_TOP_REAR, slide(t));
    const entryAt = t => magInGun(H(t), MAG_ENTRY, fumble ? CROOKED : Z);
    const fumblePath = t => {
        const d = t < T.bump ? MAG_ENTRY + (BUMP_DEPTH - MAG_ENTRY) * easeInTo(t, T.align + 0.02, T.bump)
            : t < T.out ? BUMP_DEPTH - 0.14 * Math.sin(Math.PI * clamp01((t - T.bump - 0.1) / 0.14))   // one more push: it will not go
            : t < T.out + 0.16 ? BUMP_DEPTH + (OUT_DEPTH - BUMP_DEPTH) * W(t, T.out, T.out + 0.16)
            : OUT_DEPTH + (MAG_ENTRY - OUT_DEPTH) * W(t, T.out + 0.16, T.again);
        return magInGun(H(t), d, mul(CROOKED, 1 - W(t, T.out + 0.08, T.again - 0.04)));
    };
    const LH = t => {
        const k = keyedLH(t);
        if (t < T.align) return mixT(mixT(leftOnGun(H(t)), k, W(t, 0, 0.1)), onMag(entryAt(t)), W(t, T.align - 0.06, T.align));
        if (t < T.seat + 0.06) return onMag(newMag(t), slapGap(t, T.seat));
        const off = mixT(onMag(newMag(t)), k, W(t, T.seat + 0.06, T.seat + 0.12));
        if (!rack) return mixT(off, leftOnGun(H(t)), W(t, T.regrip - 0.1, T.regrip));
        if (t < T.let + 0.04) return mixT(off, onSlide(t), W(t, T.grab - 0.1, T.grab));
        return mixT(mixT(onSlide(t), k, W(t, T.let + 0.04, T.let + 0.1)), leftOnGun(H(t)), W(t, T.regrip - 0.1, T.regrip));
    };
    const newMag = t => t < T.align ? magOnHand(LH(t)) : fumble && t < T.again ? fumblePath(t)
        : magInGun(H(t), Math.max(0, seatDepth(t, fumble ? T.again : T.align, T.seat)));
    const g = 520, t0 = T.drop;
    const axial = t => t < t0 ? 0 : 0.5 * g * (t - t0) ** 2;
    const tClear = t0 + Math.sqrt(2 * MAG_CLEAR / g);
    const inWell = t => magInGun(H(t), axial(t));
    const vRel = mul(sub(inWell(tClear).pos, inWell(tClear - 0.004).pos), 1 / 0.004);
    const oldMag = t => {
        if (t <= tClear) return inWell(t);
        const base = inWell(tClear), dt = t - tClear;
        const pos = add(add(base.pos, mul(vRel, dt)), [0, -0.5 * g * dt * dt, 0]);
        return {pos, rot: euler(mm(base.R, E([-160 * dt, 10 * dt, 70 * dt])))};
    };
    const tracks = {
        handling: {position: hp, rotation: hr},
        magazine: {scale: t => (t >= (noMag ? 0 : t0) && t < T.seat) ? [0, 0, 0] : [1, 1, 1], step: true},
        follower: {position: t => empty && t < t0 ? Z : FOLLOWER_LOADED},
        empty_old_mag: noMag ? {scale: () => [0, 0, 0], step: true}
            : {position: t => oldMag(t).pos, rotation: t => oldMag(t).rot, scale: t => (t >= t0 && t < t0 + 0.5) ? [1, 1, 1] : [0, 0, 0], step: true},
        mag_out: {position: t => newMag(t).pos, rotation: t => newMag(t).rot},
        reload_magazine: {scale: t => (t >= T.enter - 0.06 && t < T.seat) ? [1, 1, 1] : [0, 0, 0], step: true},
        righthand: {position: t => sub(RIGHT_REST.pos, mul(GRIP_SLIDE, slip(t))), rotation: () => RIGHT_REST.rot},
        lefthand: lhTracks(LH),
        camera: {rotation: cam},
    };
    if (empty) tracks.slide = {position: t => [0, 0, slide(t)]};
    const sounds = [...(noMag ? [] : [[T.release - 0.01, 'magazine_release'], [T.drop + 0.02, 'magazine_out']]),
        [T.seat - 0.275, 'magazine_in'], [T.seat - 0.005, 'magazine_seat'],
        ...(rack ? [[T.pull - 0.01, 'slide_back'], [T.let - 0.005, 'slide_release']] : empty ? [[T.stop + 0.015, 'slide_release']] : [])];
    return {tracks, sounds, timing: T};
}
export const TIER_CLIPS = [];
for (const noMag of [false, true]) for (const empty of [false, true]) {
    const variants = [['experienced', {}], ['experienced', {ads: true}], ['novice', {}], ['novice', {fumble: true}]];
    for (const [tierName, o] of variants) {
        const opts = {...o, noMag}, name = tierClip(empty, tierName, opts);
        TIER_CLIPS.push({name, empty, tierName, ...opts});
        clips.push(clip(name, tierTiming(empty, tierName, !!o.fumble, opts).end, 'once', hand => reloadTierKeyed(hand, empty, tierName, opts)));
    }
}

// Inspect (CS2-style, trained): one hand shows the left side and holds it with a slow look, the wrist rolls to show the
// right side (ejection port; empty: the open chamber), then a magazine check: thumb release, the magazine drops into the
// waiting support hand, is tipped toward the eye, pushed home with a palm slap; back to the ready. No press check (the
// support arm's box covered the whole gun in V2).
const SHOW_L = {pos: [0.2, 3.8, 3.0], rot: [16, 60, -8]};
// right side: the muzzle swings right so the ejection port faces the eye. The box arm has no wrist and would then lie
// across the screen along the bore; instead the righthand bone turns back about the grip (a bent wrist), so the forearm
// still comes in from the lower right while the hand's end stays round the grip.
const rolled = (pitch, yaw, roll) => euler(mm(E([pitch, yaw, 0]), E([0, 0, roll])));
const SHOW_R_RPY = (process.env.P9_SHOWR || '20,-60,10').split(',').map(Number);
const SHOW_R = {pos: [0.4, 3.0, 2.2], rot: rolled(...SHOW_R_RPY)};
const SHOW_R_EMPTY = {pos: [0.4, 3.0, 2.2], rot: rolled(SHOW_R_RPY[0], SHOW_R_RPY[1], SHOW_R_RPY[2] + 12)};
const FOREARM_VIEW = (process.env.P9_FOREARM || '0.35,-0.6,1').split(',').map(Number);   // forearm way in view space
const bentRight = rot => solve(RP, HP)(RH_POINT, E(levelArm(mv(T3(E(rot)), norm(FOREARM_VIEW)))));
const MAG_CHECK = {pos: [0.3, 3.2, 2.0], rot: [24, 36, -22]};
// Hand-keyed inspect (user 2026-10-22: unhurried, every move eased and cushioned; the magazine is brought out to the left of
// the screen and turned so its sides, its top and the round in it can be seen; docs/dev/p9_reload_inspect_v1.md 2.4).
// Keyed: handling, lefthand, camera and (with a magazine) mag_out. Solved from them: the grip slip and the bent right
// wrist from the beats; the thumb release and the pull out of the magwell; while the magazine is out the support hand
// holds it by the base plate: its turn is keyed, its position follows the magazine's base; the insert and the slap.
// noMag (user 2026-10-20: no magazine, no magazine check): the same sides, then the gun tips up to look into the empty
// magwell; the support hand stays down and comes back to the grip on the way home.
export const INSPECT_T = {up: 1.05, turn: 2.0, turned: 3.05, check: 3.8, checked: 4.7, release: 4.8, drop: 4.82, out: 5.17,
    look: 5.95, lookEnd: 8.5, align: 9.1, seat: 9.45, back: 9.56, backEnd: 10.4, regrip: 10.2, end: 11.1};
export const INSPECT_NO_MAG_T = {up: 1.05, turn: 2.0, turned: 3.05, check: 3.8, checked: 4.7, back: 5.55, backEnd: 6.2, regrip: 6.05, end: 6.75};
const DROP_GAP = 0.4;                          // the support hand waits this far under the base plate; the magazine drops onto it
const inspectSlip = T => t => SLIP * (W(t, T.check, T.checked) - W(t, T.back, T.backEnd));
const inspectWrist = T => t => W(t, T.turn, T.turned) - W(t, T.check, T.checked);
const MAG_HOLD = add(MAG_BOTTOM, mul(AX, 0.12));   // where the support hand holds a magazine (onMag's anchor point)
function inspectKeyed(hand, empty, noMag = false) {
    const T = noMag ? INSPECT_NO_MAG_T : INSPECT_T;
    const hr = hand.ch('handling', 'rotation'), hp = hand.ch('handling', 'position'), cam = hand.ch('camera', 'rotation');
    const lr = hand.ch('lefthand', 'rotation'), lp = hand.ch('lefthand', 'position'), pre = E(hand.pre);
    const H = t => ({rot: hr(t), pos: hp(t)});
    const slip = inspectSlip(T), wrist = inspectWrist(T);
    const RH = t => mixT({pos: sub(RIGHT_REST.pos, mul(GRIP_SLIDE, slip(t))), rot: RIGHT_REST.rot}, bentRight(H(t).rot), wrist(t));
    const keyedLH = t => ({pos: lp(t), rot: euler(mm(E(lr(t)), pre))});
    const grip = t => mixT(leftOnGun(H(t)), keyedLH(t), W(t, 0, 0.2));
    // the thumb drops the magazine onto the waiting palm, then the hand draws it out of the magwell
    const axial = t => DROP_GAP * W(t, T.drop, T.drop + 0.05) + (MAG_CLEAR - DROP_GAP) * W(t, T.drop + 0.08, T.out);
    const mk = noMag ? null : {r: hand.ch('mag_out', 'rotation'), p: hand.ch('mag_out', 'position')};
    const withR = m => ({...m, R: E(m.rot)});
    const held = t => {
        if (noMag) return magInGun(H(t), 0);   // nothing drawn there: no magazine in the gun hides its bones
        if (t < T.out) return magInGun(H(t), axial(t));
        if (t < T.align) return withR(mixT(mixT(magInGun(H(t), MAG_CLEAR), {pos: mk.p(t), rot: mk.r(t)}, W(t, T.out, T.out + 0.12)),
            magInGun(H(t), MAG_ENTRY), W(t, T.align - 0.08, T.align)));
        return magInGun(H(t), Math.max(0, seatDepth(t, T.align, T.seat)));
    };
    const LH = t => {
        if (noMag) return mixT(grip(t), leftOnGun(H(t)), W(t, T.regrip - 0.2, T.regrip));
        if (t < T.release - 0.03) return mixT(grip(t), onMag(magInGun(H(t), 0), DROP_GAP), W(t, T.release - 0.25, T.release - 0.03));
        if (t < T.out) return onMag(held(t), Math.max(0, DROP_GAP - axial(t)));
        if (t < T.align) {   // holding the magazine out: the hand's turn is keyed, its anchor on the base plate
            const m = held(t), R = E(slerpRot(slerpRot(onMag(m).rot, keyedLH(t).rot, W(t, T.out, T.out + 0.12)), onMag(m).rot, W(t, T.align - 0.1, T.align)));
            return handAt(magPointWorld(m, MAG_HOLD), R);
        }
        if (t < T.seat + 0.06) return onMag(held(t), slapGap(t, T.seat));
        return mixT(mixT(onMag(held(t)), keyedLH(t), W(t, T.seat + 0.06, T.seat + 0.16)), leftOnGun(H(t)), W(t, T.regrip - 0.2, T.regrip));
    };
    const tracks = {
        handling: {position: hp, rotation: hr},
        magazine: {scale: t => !noMag && t >= T.drop && t < T.seat ? [0, 0, 0] : [1, 1, 1], step: true},
        follower: {position: () => empty ? Z : FOLLOWER_LOADED},
        mag_out: {position: t => held(t).pos, rotation: t => held(t).rot},
        reload_magazine: {scale: t => !noMag && t >= T.drop && t < T.seat ? [1, 1, 1] : [0, 0, 0], step: true},
        empty_old_mag: {scale: () => [0, 0, 0], step: true},
        righthand: {position: t => RH(t).pos, rotation: t => RH(t).rot},
        lefthand: lhTracks(LH),
        slide: {position: () => [0, 0, empty ? SLIDE_LOCK : 0]},
        camera: {rotation: cam},
    };
    const sounds = noMag ? [] : [[T.release - 0.01, 'magazine_release'], [T.drop + 0.03, 'magazine_out'], [T.seat - 0.275, 'magazine_in'], [T.seat - 0.005, 'magazine_seat']];
    return {tracks, sounds, timing: T};
}
clips.push(clip('inspect', INSPECT_T.end, 'once', hand => inspectKeyed(hand, false)));
clips.push(clip('inspect_empty', INSPECT_T.end, 'once', hand => inspectKeyed(hand, true)));
clips.push(clip('inspect_no_mag', INSPECT_NO_MAG_T.end, 'once', hand => inspectKeyed(hand, false, true)));
clips.push(clip('inspect_empty_no_mag', INSPECT_NO_MAG_T.end, 'once', hand => inspectKeyed(hand, true, true)));

// Unload (docs/native_guns/magazine_system_v1.md 3.1, user 2026-10-20): the reload key with no fuller magazine on the rig
// or in the pockets takes the magazine out to be loaded by hand. The inspect's magazine check up to the catch: the gun comes
// up rolled so the magwell faces the support hand, the support hand cups under it, the thumb drops the magazine into it;
// the hand takes it down off the lower left while the gun drops out low right (put_away's LOW). The server then puts the
// magazine in the hand slot and selects it at UNLOAD_SWAP (NativeGunActions: before the clip's end, so the gun is never seen
// back at the ready while the switch reaches the client); the clip holds LOW to its end. unload_empty: slide locked back.
export const UNLOAD = {up: 0.3, release: 0.36, drop: 0.38, out: 0.52, carry: 0.55, carried: 0.85, down: 0.62, downEnd: 1.0, swap: 1.05, end: 1.3};
function unload(empty) {
    const T = UNLOAD;
    const path = legs([
        [0.02, T.up, MAG_CHECK, {antic: 0.03, over: 0.04, per: 0.2, tau: 0.08}],
        [T.down, T.downEnd, LOW, {antic: 0.04, over: 0}]]);
    const check = t => go(t, 0.02, T.up, {antic: 0, over: 0}) - go(t, T.down, T.downEnd, {antic: 0, over: 0});
    const hits = t => sum(jolt(t, T.release, [-0.6, 0, 0.4]), jolt(t, T.out, [0.5, 0, -0.25]));
    const slip = t => SLIP * clamp01(check(t));
    const H = t => { const p = path(t), rot = sum(p.rot, hits(t));
        return {rot, pos: sum(p.pos, mv(E(rot), mul(GRIP_SLIDE, slip(t))), mul([0, 0.1, 0], hits(t)[0]))}; };
    // the magazine slides down the magwell into the cupped support hand, which takes it down off the lower left
    const axial = t => MAG_CLEAR * clamp01(go(t, T.drop, T.out, {antic: 0, over: 0}));
    const held = t => {
        if (t < T.out) return magInGun(H(t), axial(t));
        const a = magInGun(H(T.out), MAG_CLEAR), s = clamp01(go(t, T.carry, T.carried, {antic: 0.02, over: 0}));
        const R = E(lerp(a.rot, OFFSCREEN_MAG.rot, s)); return {pos: lerp(a.pos, OFFSCREEN_MAG.pos, s), rot: euler(R), R};
    };
    const LH = t => {
        if (t < 0.16) return mixT(leftOnGun(H(t)), LOW_LEFT, go(t, 0.0, 0.16, {antic: 0, over: 0}));
        if (t < T.drop) return mixT(LOW_LEFT, onMag(held(t), 0.5), clamp01(go(t, 0.16, T.release - 0.02, {antic: 0, over: 0})));
        return onMag(held(t), t < T.out ? 0.5 * (1 - clamp01((t - T.drop) / (T.out - T.drop))) : 0);
    };
    const camera = t => sum(mul([-1.0, 1.2, 1.5], check(t)), mul(hits(t), 0.35), mul([-1.0, 0.4, 0.9], go(t, T.down, T.downEnd, {antic: 0, over: 0})));
    const tracks = {
        handling: {position: t => H(t).pos, rotation: t => H(t).rot},
        magazine: {scale: t => t >= T.drop ? [0, 0, 0] : [1, 1, 1], step: true},
        follower: {position: () => empty ? Z : FOLLOWER_LOADED},
        mag_out: {position: t => held(t).pos, rotation: t => held(t).rot},
        reload_magazine: {scale: t => (t >= T.drop && t < T.carried + 0.05) ? [1, 1, 1] : [0, 0, 0], step: true},
        empty_old_mag: {scale: () => [0, 0, 0], step: true},
        righthand: {position: t => sub(RIGHT_REST.pos, mul(GRIP_SLIDE, slip(t))), rotation: () => RIGHT_REST.rot},
        lefthand: lhTracks(LH),
        slide: {position: () => [0, 0, empty ? SLIDE_LOCK : 0]},
        camera: {rotation: camera},
    };
    const sounds = [[T.release - 0.01, 'magazine_release'], [T.release + 0.03, 'magazine_out'], [T.down, 'put_away']];
    return {tracks, sounds, timing: T};
}
clips.push(clip('unload', UNLOAD.end, 'once', () => unload(false)));
clips.push(clip('unload_empty', UNLOAD.end, 'once', () => unload(true)));

// ---------- hand-keyed channels ----------
// Keyed by hand in Blockbench (bezier), read back from the source; lefthand keys are local to the clip's lefthand_pos.
const RELOAD_HAND = {handling: ['rotation', 'position'], lefthand: ['rotation', 'position'], camera: ['rotation']};
const INSPECT_HAND = {...RELOAD_HAND, mag_out: ['rotation', 'position']};
export const HAND_KEYED = {reload_tactical: RELOAD_HAND, reload_empty: RELOAD_HAND, reload_tactical_ads: RELOAD_HAND, reload_empty_ads: RELOAD_HAND,
    reload_tactical_no_mag: RELOAD_HAND, reload_empty_no_mag: RELOAD_HAND, reload_tactical_no_mag_ads: RELOAD_HAND, reload_empty_no_mag_ads: RELOAD_HAND,
    inspect: INSPECT_HAND, inspect_empty: INSPECT_HAND,
    inspect_no_mag: RELOAD_HAND, inspect_empty_no_mag: RELOAD_HAND,
    ...Object.fromEntries(TIER_CLIPS.map(c => [c.name, RELOAD_HAND]))};
function handKeys(source, name) {
    const spec = HAND_KEYED[name]; if (!spec) return null;
    const anim = source.animations?.find(a => a.name === name);
    const byBone = new Map(Object.values(anim?.animators ?? {}).map(a => [a.name, a]));
    const kf = {};
    for (const [bone, chs] of Object.entries(spec)) for (const ch of chs) {
        const keys = (byBone.get(bone)?.keyframes ?? []).filter(k => k.channel === ch);
        assert(keys.some(k => k.interpolation === 'bezier'), `${name}: ${bone}.${ch} has no hand keys in the source (seed them in Blockbench first)`);
        assert(!byBone.get(bone).quaternion_interpolation, `${name}: ${bone} uses quaternion interpolation`);
        (kf[bone] ??= {})[ch] = keys;
    }
    const pre = byBone.get('lefthand_pos')?.keyframes.find(k => k.channel === 'rotation');
    assert(pre, `${name}: lefthand_pos pre-rotation missing`);
    return {kf, pre: ['x', 'y', 'z'].map(a => +pre.data_points[0][a]), ch: (bone, ch) => bbChannel(kf[bone][ch])};
}

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
    const uuid = s => { const h = createHash('sha256').update('p9-01-v3-native-anim:' + s).digest('hex'); return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-8${h.slice(17, 20)}-${h.slice(20, 32)}`; };
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
        // Helper magazines are hidden explicitly in every clip that does not drive them (no reliance on the idle layer).
        for (const [bone, ch] of Object.entries(HIDE_HELPERS)) if (!tracks[bone]) tracks[bone] = ch;
        {   // gimbal guard for the support hand: constant per-clip pre-rotation on lefthand_pos
            const solved = tracks.lefthand.solved; let best = null;
            for (const cand of hand ? [hand.pre] : LH_POS_CANDIDATES) {   // hand keys are local to their pre-rotation
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
                const keys = bake(chs[ch], c.length, step, ref), track = {}, handCh = hand?.kf[bone]?.[ch];
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

// rig and solvers, for seeding / checking hand keys from other scripts
export const RIG = {E, euler, mm, mv, T3, unwrap, slerpRot, levelArm, handAt, leftOnGun, onMag, magInGun, magOnHand, handleWorld,
    GRIP_SLIDE, RELOAD_UP, RELOAD_MEET, RELOAD_HOLD, RELOAD_ADS_DIP, RELOAD_ADS_MEET, OFFSCREEN_MAG, LOW_LEFT, MAG_ENTRY, PART, SLAP_GAP, AX, LP, LA, HP, MP, RP, RIGHT_REST,
    magPointWorld, slideHand, SLIDE_TOP_REAR, SLIDE_LOCK, HAND_RACK, seatDepth, MAG_HOLD, MAG_CLEAR, HAND_ON_MAG, SHOW_L, SHOW_R, SHOW_R_EMPTY, MAG_CHECK, DROP_GAP, inspectSlip, inspectWrist, bentRight, W, LOW};

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
    const args = process.argv.slice(2), {bb, runtime, stats, lhChoice} = build();
    if (args.includes('--write-source')) {
        const source = JSON.parse(fs.readFileSync(sourcePath, 'utf8'));
        source.animations = bb; fs.writeFileSync(sourcePath, JSON.stringify(source));
    }
    if (args.includes('--write-runtime')) fs.writeFileSync(runtimePath, JSON.stringify(runtime, null, 2) + '\n');
    console.log(JSON.stringify({stats, lhChoice}, null, 1));
}
