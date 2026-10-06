// Jerry can first-person rig and animations (docs/models/fuel_containers_v1.md "第一人称动画"), V2 2026-10-05.
// The editable source is a Blockbench Free Model, src/main/blockbench/jerry_can_first_person.bbmodel: the jerry can's own
// mesh and atlas (tools/build-fuel-containers-v1.mjs; its spout cap and cam lever on their own group so they unscrew),
// the hand anchors with source-only reference arms (export=false), and four animations as key poses (catmull-rom), in
// the crowbar's first-person frame (client/CrowbarFirstPerson): camera space px, the eye at the origin, x right, y up,
// -z ahead; the can about 10-12 px ahead, as close as the guns' hands. Runtime (client/JerryCanFirstPerson):
//   geo/jerry_can_first_person.geo.json, meshes/jerry_can_first_person.aflmesh.json (tools/export-afl-mesh.mjs convert),
//   animations/jerry_can_first_person.animation.json.
//   node tools/author-jerry-can-first-person.mjs            -> writes the source from the key poses below, then the runtime
//   node tools/author-jerry-can-first-person.mjs --export   -> runtime only, from the source as saved (edited in Blockbench)
//   node tools/author-jerry-can-first-person.mjs --check    -> verifies the runtime files match the source
// Conventions (GeckoLib 4.7.4, as tools/author-blackridge-50-animations.mjs): a Blockbench value is the runtime value; the
// geo stores pivots as (-x, y, z) and rotations as (-rx, -ry, rz); animation files store rotations as (-rx, -ry, rz) and
// positions as (-x, y, z); every group rests unrotated and every rotation key carries the whole orientation.
import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {B, CAN} from './build-fuel-containers-v1.mjs';
import {convert} from './export-afl-mesh.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const SOURCE = path.join(ROOT, 'src/main/blockbench/jerry_can_first_person.bbmodel');
const ASSETS = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight');
const GEO = path.join(ASSETS, 'geo/jerry_can_first_person.geo.json'), MESH = path.join(ASSETS, 'meshes/jerry_can_first_person.aflmesh.json');
const ANIMATION = path.join(ASSETS, 'animations/jerry_can_first_person.animation.json');
const D2R = Math.PI / 180;
const add = (a, b) => a.map((v, i) => v + b[i]), sub = (a, b) => a.map((v, i) => v - b[i]), mul = (a, k) => a.map(v => v * k);
const dot = (a, b) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
const cross = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
const norm = a => mul(a, 1 / Math.hypot(...a));
const lerp = (a, b, t) => a.map((v, i) => v + (b[i] - v) * t);
const r4 = v => +v.toFixed(4) || 0;
const uuid = s => { const h = createHash('md5').update('afl_jerry_can_fp/' + s).digest('hex'); return `${h.slice(0, 8)}-${h.slice(8, 12)}-${h.slice(12, 16)}-${h.slice(16, 20)}-${h.slice(20)}`; };

// ---------------- rotations: arrays of columns [X, Y, Z]; Blockbench / Gecko M = Rz Ry Rx ----------------
const R = {
  x: a => { const c = Math.cos(a * D2R), s = Math.sin(a * D2R); return [[1, 0, 0], [0, c, s], [0, -s, c]]; },
  y: a => { const c = Math.cos(a * D2R), s = Math.sin(a * D2R); return [[c, 0, -s], [0, 1, 0], [s, 0, c]]; },
  z: a => { const c = Math.cos(a * D2R), s = Math.sin(a * D2R); return [[c, s, 0], [-s, c, 0], [0, 0, 1]]; },
  axis: (k, a) => { k = norm(k); const c = Math.cos(a * D2R), s = Math.sin(a * D2R);
    const f = v => add(add(mul(v, c), mul(cross(k, v), s)), mul(k, dot(k, v) * (1 - c))); return [f([1, 0, 0]), f([0, 1, 0]), f([0, 0, 1])]; },
};
const ap = (M, v) => add(add(mul(M[0], v[0]), mul(M[1], v[1])), mul(M[2], v[2]));
const mm = (A, Bm) => Bm.map(c => ap(A, c));
const tr = M => [0, 1, 2].map(i => [M[0][i], M[1][i], M[2][i]]);
const fromEuler = ([a, b, c]) => mm(R.z(c), mm(R.y(b), R.x(a)));
const euler = ([X, Y, Z]) => [Math.atan2(Y[2], Z[2]) / D2R, Math.asin(Math.max(-1, Math.min(1, -X[2]))) / D2R, Math.atan2(X[1], X[0]) / D2R];
const unwrap = (v, prev) => prev === undefined ? v : v + 360 * Math.round((prev - v) / 360);
const eulerNear = (M, prev) => {
  const [a, b, c] = euler(M), fit = e => e.map((v, i) => unwrap(v, prev?.[i])), A = fit([a, b, c]), Bn = fit([a + 180, 180 - b, c + 180]);
  const d = e => prev ? e.reduce((s, v, i) => s + Math.abs(v - prev[i]), 0) : 0;
  return prev && d(Bn) < d(A) ? Bn : A;
};
// a hand: forearm Y toward the hand (from its shoulder), palm Z (orthogonalised), lateral X = Y x Z
const handPalm = (Y, palm) => { const Z = norm(sub(palm, mul(Y, dot(palm, Y)))); return [cross(Y, Z), Y, Z]; };
const handAlong = (Y, along) => { const X = norm(sub(along, mul(Y, dot(along, Y)))); return [X, Y, cross(X, Y)]; };

// ---------------- the can (its model frame: px about its cell centre, drawn CAN.scale times about its foot) ----------------
const S = CAN.scale, sy = y => -8 + (y + 8) * S;
const GRIP = [CAN.handles[1] * S, sy(-0.65), 0];                  // the middle handle's bar
const CAP_FOOT = [CAN.spout[0] * S, sy(-1.05), 0], SPOUT_TOP = [CAN.spout[0] * S, sy(-0.5), 0];
const BACK = [-CAN.w * S, sy(-3.1), 0];                           // its back end, low: where the left hand bears it
const CAP_H = 0.55 * S;
// rest rig: the grip at G0. Carried, the can is as at one's side: its broad faces to the left and right, the narrow side and
// the row of handles toward the eye, the spout ahead (the carry frame: model +X -> -Z, its thickness +Z -> +X; user,
// 2026-10-05: the broad face to the eye looked wrong); raised and poured it is held across, the spout to the left (user,
// 2026-10-06: held across, the hands each at an end, nothing passes through it). The bones rest half way, turned
// REST_YAW from the carry frame, so the can's turn either way stays clear of the Euler singularity at a middle angle of 90
// (GeckoLib interpolates each angle on its own).
const Ry90 = v => [v[2], v[1], -v[0]], Ry90inv = v => [-v[2], v[1], v[0]];
const REST_YAW = 45;
const G0 = [4.4, -5, -10.5];
// drawn K times the placed can about its grip (1: its own size; the view is close, about 11 px, as the guns', so the arms
// reach in from the edges of the screen)
const K = 1.0;
const rig = v => add(G0, mul(ap(R.y(REST_YAW), Ry90(sub(v, GRIP))), K));     // a model point at rest
const unrig = r => add(GRIP, mul(Ry90inv(ap(R.y(-REST_YAW), sub(r, G0))), 1 / K));
// the spout end's face, low: where the other hand holds the can across
const SUPPORT = [CAN.w * S, sy(-4.6), 0];
const P = {can: G0, cap: rig(CAP_FOOT), spout: rig(SPOUT_TOP), right_hand_anchor: G0, left_hand_anchor: rig(add(CAP_FOOT, [0, CAP_H, 0])),
  left_under_anchor: rig(SUPPORT)};

// ---------------- key poses ----------------
// Carried by its nearest handle in the right hand, at the right, the forearm along the can; raised and swung across in
// front, the spout to the left, its top leaning toward the eye, the right hand keeping that handle (now at the right end)
// and the left hand coming in to unscrew the cap; the cap taken away in the left hand, which comes back to hold the spout
// end's face; then the spout end tipped down to the left, over the fill cover (client/JerryCanPourView turns the view so
// the opening is there), the hands at the two ends so nothing crosses the can.
// Weight (a full can is near 20 kg; user, 2026-10-05: without it the motion felt limp): an anticipating dip before
// the lift, an overshoot and settle at the top, the cap stuck and then cracking loose, the pour tipping past and
// sloshing back; the camera bone dips and jolts with it (as the guns', client/JerryCanFirstPerson).
const HANDLE = i => [CAN.handles[i] * S, sy(-0.65), 0];          // 0 rear (nearest when carried, the right end across), 1 middle, 2 front
// orientation [pitch, yaw, roll] in the carry frame: Ry(yaw) Rx(pitch) Rz(roll); yaw > 0 turns the spout left (90: across,
// the spout to the left); pitch > 0 lifts the spout, < 0 tips it down; roll > 0 leans the top left (across: toward the eye)
const orient = ([pitch, yaw, roll = 0]) => mm(R.y(yaw), mm(R.x(pitch), R.z(roll)));
const CARRY = [0, 15, 0], RAISED = [6, 90, 22], POUR = [-40, 90, 12];
const GR = [1.6, -3.4, -11.0], G1 = [3.2, -0.2, -11.5];
// where the left forearm comes from: on the cap, from the left side (the can across, the hands at its ends, the forearms
// reach in from the sides above it and their cut ends leave the screen sideways); on the spout end's face, from below
const SHOULDER_L_CAP = [-12, -3, 0];
const SHOULDER_L = [-10, -14, 4];
// the right forearm runs with the can, as a gun's (user, 2026-10-05: reaching in square across it looked crooked): its
// shoulder behind the hand along the can's heading, a little right and below (turned only half the can's turn: across,
// the forearm square behind it would leave its cut end on screen)
const shoulderR = can => lerp(SHOULDER_R, SHOULDER_R_ACROSS, Math.max(0, Math.min(1, (can.o[1] - CARRY[1]) / (90 - CARRY[1]))));
const SHOULDER_R = [6, -6, 1], SHOULDER_R_ACROSS = [13, -2, 2];
const onRight = (can, i) => onHandle(can, i, shoulderR(can));
// the bar in the fist's lower part (the fingers curl under it), so the fist clears the can's top
const HAND_LIFT = 0.6;
// a pose: C the turn in the carry frame, M the can bone's (from the rest)
const canPose = (g, o) => { const C = orient(o), M = mm(C, R.y(-REST_YAW)); return {g, M, C, o, at: v => add(g, ap(M, sub(v, G0)))}; };   // v: a rest rig point
const cap = (pos, e) => ({pos, e, M: fromEuler(e)});
const CAP_ON = (turn, lift = 0) => cap([0, lift, 0], [0, turn, 0]);
const CAP_LIFT = cap([0, 1.1, 0], [0, 360, 0]);
const capTop = (can, c) => can.at(add(P.cap, mul(add(c.pos, ap(c.M, [0, CAP_H, 0])), K)));   // c.pos: placed-can px
// a hand round a handle's bar (across the can's thickness) from above, palm down, the bar 2 px inside the fist: its lateral
// axis along the bar where the forearm crosses it, turning along the can where the forearm runs along it, blended so the
// wrist never flips (2026-10-05: the knuckles kept along the bar flipped as the forearm swung parallel to it)
const onHandle = (can, i, shoulder) => { const g = can.at(rig(HANDLE(i))), Y = norm(sub(g, shoulder)), up = ap(can.C, [0, 1, 0]);
  const side = v => { const p = sub(v, mul(Y, dot(v, Y))); return dot(cross(p, Y), up) > 0 ? mul(p, -1) : p; };
  const a = side(ap(can.C, [1, 0, 0])), b = side(ap(can.C, [0, 0, -1])), w = dot(a, a);
  return {O: add(add(g, mul(Y, 2.0)), mul(up, HAND_LIFT)), M: handAlong(Y, add(mul(a, w), mul(b, 1 - w))), S: shoulder}; };
const leftCap = (can, c, twist) => { const O = add(capTop(can, c), [0, 0.5, 0]), Y = norm(sub(O, SHOULDER_L_CAP));
  return {O: add(add(O, mul(Y, 1.6)), [0, CAP_LIFT_HAND, 0]), M: mm(R.axis(Y, twist), handPalm(Y, [0, -1, 0])), S: SHOULDER_L_CAP}; };
const CAP_LIFT_HAND = 0.7;
// the other hand on the spout end's face, low, the palm against it (half the arm's width out). Its own bone,
// left_under_anchor: from the cap, palm down, to the end face is a turn no one bone's Euler angles interpolate cleanly;
// the hands swap off screen
const leftSupport = can => { const n = ap(can.C, [0, 0, -1]), pt = can.at(rig(SUPPORT)), O0 = add(pt, mul(n, 1.24 + 0.15)), Y = norm(sub(O0, SHOULDER_L));
  return {O: add(O0, mul(ap(can.C, [0, 1, 0]), 0.6)), M: handPalm(Y, mul(n, -1)), S: SHOULDER_L, under: true}; };   // the fingers up the face, not into it
const leftAway = () => { const O = [-10, -12, -6], Y = norm(sub(O, SHOULDER_L_CAP)); return {O, M: mm(R.axis(Y, 20), handPalm(Y, [0, -1, 0])), S: SHOULDER_L_CAP}; };   // turned as on the cap, so it comes and goes without a spin
const leftAwayUnder = can => { const O = [-12, -8, -9], Y = norm(sub(O, SHOULDER_L)); return {O, M: handPalm(Y, ap(can.C, [0, 0, 1])), S: SHOULDER_L, under: true}; };   // turned as it will be on the end
// the cap in the left hand, under the fist (placed-can px about its foot, as cap()); away, off screen, it is hidden
const capIn = (can, hand) => { const Y = norm(sub(hand.O, hand.S)), top = sub(sub(hand.O, mul(Y, 1.6)), [0, 0.5, 0]);
  return cap(sub(mul(add(ap(tr(can.M), sub(top, can.g)), sub(G0, P.cap)), 1 / K), [0, CAP_H, 0]), [0, 360, 0]); };
const capGone = can => ({...capIn(can, leftAway()), hidden: true});
const mix = (a, b, t) => ({O: lerp(a.O, b.O, t), M: t < 0.5 ? a.M : b.M, S: a.S});   // re-aimed at its shoulder when written
// a key: the can, its cap, the hands (left null: hidden), the camera as the view turns (degrees: pitch down +, yaw, roll)
const key = (can, c, r, l, cam = [0, 0, 0]) => ({can, cap: c, right: r, left: l, cam});
const carried = (g = G0, o = CARRY) => canPose(g, o);
const idle = (t, len) => { const k = Math.sin(2 * Math.PI * t / len); return carried(add(G0, [0, 0.2 * k, 0]), [0, 15, 1.2 * k]); };

const CLIPS = {
  idle: {length: 2.4, loop: true, keys: [0, 0.6, 1.2, 1.8, 2.4].map(t => { const can = idle(t, 2.4); return [t, key(can, CAP_ON(0), onRight(can, 0), null)]; })},
  pour_start: {length: 3.4, loop: 'hold_on_last_frame', keys: (() => {
    const dip = carried(add(G0, [0, -0.5, 0.3]), [0, 15, -3]);
    const rising = canPose(lerp(G0, GR, 0.55), [4, 55, 10]), over = canPose(add(GR, [0, 0.35, 0]), [9, 93, 25]), raised = canPose(GR, RAISED);
    const r1 = can => onRight(can, 0), going = mix(leftCap(raised, CAP_LIFT, 26), leftAway(), 0.45);
    const SET = [9, 90, 22], PAST = [POUR[0] - 6, POUR[1], POUR[2]];
    const set = canPose(add(GR, [0, -0.15, 0]), SET);                                                 // tips back a little before the pour
    const pastPour = canPose(add(G1, [0, -0.25, 0]), PAST), poured = canPose(G1, POUR);
    // on the way over (the hand on the end follows the can, not a straight line through it)
    const tipping = k => canPose(lerp(set.g, pastPour.g, k), lerp(SET, PAST, k));
    const two = (can, cam) => key(can, capGone(raised), onRight(can, 0), leftSupport(can), cam);
    return [
      [0.0, key(carried(), CAP_ON(0), onRight(carried(), 0), null)],
      [0.14, key(dip, CAP_ON(0), onRight(dip, 0), leftAway(), [0.6, 0, 0])],                             // gathers itself
      [0.38, key(rising, CAP_ON(0), onRight(rising, 0), mix(leftAway(), leftCap(rising, CAP_ON(0), -30), 0.5), [1.8, 0, 0.3])],
      [0.55, key(over, CAP_ON(0), onRight(over, 0), leftCap(over, CAP_ON(0), -30), [1.2, 0, 0.2])],     // up, a little past
      [0.72, key(raised, CAP_ON(0), r1(raised), leftCap(raised, CAP_ON(0), -30), [0.8, 0, 0])],
      [0.98, key(raised, CAP_ON(0), r1(raised), leftCap(raised, CAP_ON(0), -24), [0.9, 0, -0.3])],      // the cap is stuck
      [1.06, key(raised, CAP_ON(45), r1(raised), leftCap(raised, CAP_ON(45), 8), [0.2, 0.3, 0.9])],     // and cracks loose
      [1.38, key(raised, CAP_ON(180, 0.08), r1(raised), leftCap(raised, CAP_ON(180, 0.08), 30), [0.6, 0, 0.2])],
      [1.54, key(raised, CAP_ON(180, 0.08), r1(raised), leftCap(raised, CAP_ON(180, 0.08), -30), [0.6, 0, 0])],   // turns the hand back
      [1.9, key(raised, CAP_ON(360, 0.2), r1(raised), leftCap(raised, CAP_ON(360, 0.2), 30), [0.6, 0, 0.1])],
      [2.05, key(raised, CAP_ON(360, 0.25), r1(raised), leftCap(raised, CAP_ON(360, 0.25), 28), [0.6, 0, 0])],
      [2.22, key(raised, CAP_LIFT, r1(raised), leftCap(raised, CAP_LIFT, 26), [0.4, 0, 0])],             // lifted off
      [2.38, key(raised, capIn(raised, going), r1(raised), going, [0.5, 0, 0])],
      [2.52, key(raised, capIn(raised, leftAway()), r1(raised), leftAway(), [0.6, 0, 0])],               // takes it away
      [2.54, key(raised, capGone(raised), r1(raised), leftAwayUnder(set), [0.6, 0, 0])],                 // (the hands swap off screen)
      [2.76, two(set, [0.9, 0, 0])],                                                                     // the other hand on the spout end
      [2.88, two(tipping(0.3), [1.2, 0, -0.2])],
      [3.0, two(tipping(0.65), [1.5, 0, -0.4])],
      [3.12, two(pastPour, [1.8, 0, -0.6])],                                                             // tips, past it
      [3.4, two(poured, [1.1, 0, -0.3])]];
  })()},
  pour_loop: {length: 1.6, loop: true, keys: [0, 0.4, 0.8, 1.2, 1.6].map(t => {
    const k = Math.sin(2 * Math.PI * t / 1.6), can = canPose(add(G1, [0, 0.1 * k, 0]), [POUR[0] + 2 * k, POUR[1], POUR[2] + 1.5 * k]);
    return [t, key(can, capGone(canPose(GR, RAISED)), onRight(can, 0), leftSupport(can), [1.0 + 0.2 * k, 0, -0.3 + 0.15 * k])];
  })},
  pour_end: {length: 3.0, loop: 'hold_on_last_frame', keys: (() => {
    const poured = canPose(G1, POUR), back = canPose(add(GR, [0, 0.2, 0]), [10, 90, 22]), raised = canPose(GR, RAISED);
    const r1 = can => onRight(can, 0), coming = mix(leftAway(), leftCap(raised, CAP_LIFT, 26), 0.6), gone = capGone(raised);
    const lowering = canPose(lerp(GR, G0, 0.6), [4, 45, 8]), under = carried(add(G0, [0, -0.35, 0]), [0, 15, -2]);
    return [
      [0.0, key(poured, gone, onRight(poured, 0), leftSupport(poured), [1.1, 0, -0.3])],
      [0.17, key(canPose(lerp(G1, back.g, 0.5), lerp(POUR, [10, 90, 22], 0.5)), gone, onRight(canPose(lerp(G1, back.g, 0.5), lerp(POUR, [10, 90, 22], 0.5)), 0),
        leftSupport(canPose(lerp(G1, back.g, 0.5), lerp(POUR, [10, 90, 22], 0.5))), [0.7, 0, 0])],
      [0.35, key(back, gone, onRight(back, 0), leftSupport(back), [0.4, 0, 0.2])],                     // back up, past level
      [0.5, key(raised, gone, r1(raised), leftAwayUnder(back), [0.6, 0, 0])],
      [0.52, key(raised, capIn(raised, leftAway()), r1(raised), leftAway(), [0.6, 0, 0])],               // (the hands swap off screen)
      [0.72, key(raised, capIn(raised, coming), r1(raised), coming, [0.6, 0, 0])],                       // brings the cap back
      [0.95, key(raised, CAP_LIFT, r1(raised), leftCap(raised, CAP_LIFT, 26), [0.6, 0, 0])],
      [1.15, key(raised, CAP_ON(360, 0.25), r1(raised), leftCap(raised, CAP_ON(360, 0.25), 28), [0.6, 0, 0])],   // on the spout
      [1.5, key(raised, CAP_ON(180, 0.08), r1(raised), leftCap(raised, CAP_ON(180, 0.08), -30), [0.6, 0, -0.1])],
      [1.66, key(raised, CAP_ON(180, 0.08), r1(raised), leftCap(raised, CAP_ON(180, 0.08), 30), [0.6, 0, 0])],
      [2, key(raised, CAP_ON(8), r1(raised), leftCap(raised, CAP_ON(8), -30), [0.7, 0, -0.1])],
      [2.08, key(raised, CAP_ON(0), r1(raised), leftCap(raised, CAP_ON(0), -30), [0.3, -0.2, -0.6])],    // snug: a last jolt
      [2.3, key(raised, CAP_ON(0), r1(raised), leftAway(), [0.6, 0, 0])],
      [2.6, key(lowering, CAP_ON(0), onRight(lowering, 0), null, [0.8, 0, 0])],
      [2.8, key(under, CAP_ON(0), onRight(under, 0), null, [0.5, 0, 0])],                                // the weight settles
      [3, key(carried(), CAP_ON(0), onRight(carried(), 0), null)]];
  })()},
};

// ---------------- clipping ----------------
// How deep each arm goes into the can's body, sampled: points through the arm's box (from the hand's end back an arm's
// length, its width across) that fall inside the body's box (its rounded corners ignored, so shrunk a little)
const BODY = {x: CAN.w * S - 0.15, y0: sy(CAN.y0) + 0.15, y1: sy(CAN.y1) - 0.15, z: CAN.d * S - 0.15};
function armInBody(can, hand) {
  let M = hand.M; const Y = norm(sub(hand.O, hand.S));
  if (Math.abs(dot(Y, M[1]) - 1) > 1e-6) M = handPalm(Y, M[2]);
  let n = 0;
  for (let t = 0.3; t <= ARM.l; t += 0.6) for (const a of [-1.1, 0, 1.1]) for (const b of [-1.1, 0, 1.1]) {
    const p = add(add(sub(hand.O, mul(M[1], t)), mul(M[0], a)), mul(M[2], b));
    const m = unrig(add(G0, ap(tr(can.M), sub(p, can.g))));
    if (Math.abs(m[0]) < BODY.x && m[1] > BODY.y0 && m[1] < BODY.y1 && Math.abs(m[2]) < BODY.z) n++;
  }
  return n;
}

// Each hand is drawn on a child of its animated anchor (right_hand, left_hand, left_under_hand) that rests turned by Q, so
// the anchor's own rotation is the hand's turn away from Q. Q is chosen among the hand's own sampled poses as the one that
// keeps every frame's Euler angles furthest from the singularity (the forearms reach in from the sides and from behind, a
// quarter turn and more from the unrotated hand). Set by buildSource.
let HAND_Q;
const handBone = (p, side) => side === 'right' ? (p.right ? 'right_hand_anchor' : null) : !p.left ? null : p.left.under ? 'left_under_anchor' : 'left_hand_anchor';
// the hand's turn against the can, as written: a blended hand keeps its forearm on its shoulder
const handRel = (can, hand) => { let M = hand.M; const Y = norm(sub(hand.O, hand.S));
  if (Math.abs(dot(Y, M[1]) - 1) > 1e-6) M = handPalm(Y, M[2]);
  return mm(tr(can.M), M); };
function chooseHandQ() {
  const rels = {right_hand_anchor: [], left_hand_anchor: [], left_under_anchor: []};
  for (const clip of Object.values(CLIPS)) for (const [, p] of densify(clip))
    for (const side of ['right', 'left']) { const bone = handBone(p, side); if (bone) rels[bone].push(handRel(p.can, p[side])); }
  const Q = {};
  for (const [bone, list] of Object.entries(rels)) {
    let best = null, bestScore = 2;
    for (const cand of list.filter((_, i) => i % 3 === 0)) {
      const ct = tr(cand); let score = 0;
      for (const rel of list) score = Math.max(score, Math.abs(mm(rel, ct)[0][2]));   // |sin| of the middle angle
      if (score < bestScore) { bestScore = score; best = cand; }
    }
    if (bestScore > 0.95) throw new Error(`${bone}: no bind turn keeps its frames clear of the Euler singularity (${bestScore.toFixed(3)})`);
    Q[bone] = best;
  }
  return Q;
}

// ---------------- the source ----------------
// the guns' arm as it is (NativePlayerArmRenderer.render: 0.62 x 0.78 x 0.62 of 4 x 12 x 4 px; client/JerryCanFirstPerson)
const ARM = {w: 2.48, l: 9.36};
// the arm's cut end (ARM.l from the hand, toward its shoulder) should be off screen: 70 degrees high at 16:9, as the
// guns' (a frame where it is not is reported)
const OFF = {x: Math.tan(35 * D2R) * 16 / 9, y: Math.tan(35 * D2R)};
const armEndShows = hand => { const e = add(hand.O, mul(norm(sub(hand.S, hand.O)), ARM.l));
  return e[2] < -0.5 && Math.abs(e[0] / -e[2]) < OFF.x && Math.abs(e[1] / -e[2]) < OFF.y ? e : null; };
const ARM_ENDS = [], ARM_CLIPS = [];
function buildSource() {
  HAND_Q = chooseHandQ();
  const groups = [], elements = [];
  const group = (name, origin, extra = {}) => { const g = {name, uuid: uuid('group:' + name), export: true, locked: false, scope: 0, selected: false, visibility: true,
    _static: {properties: {}, temp_data: {}}, origin: origin.map(r4), rotation: [0, 0, 0], color: 0, children: [], reset: false, shade: true, mirror_uv: false,
    autouv: 0, isOpen: true, primary_selected: false, ...extra}; groups.push(g); return {uuid: g.uuid, isOpen: true, children: []}; };
  const root = group('root', [0, 0, 0]), camera = group('camera', [0, 0, 0]), can = group('can', P.can), capG = group('cap', P.cap), spout = group('spout', P.spout);
  const rightA = group('right_hand_anchor', P.right_hand_anchor), leftA = group('left_hand_anchor', P.left_hand_anchor);
  const rightG = group('right_hand', P.right_hand_anchor, {rotation: euler(HAND_Q.right_hand_anchor).map(r4)});
  const leftG = group('left_hand', P.left_hand_anchor, {rotation: euler(HAND_Q.left_hand_anchor).map(r4)});
  rightA.children.push(rightG);
  leftA.children.push(leftG);
  const underA = group('left_under_anchor', P.left_under_anchor), underG = group('left_under_hand', P.left_under_anchor, {rotation: euler(HAND_Q.left_under_anchor).map(r4)});
  underA.children.push(underG);
  root.children.push(camera, can);
  can.children.push(capG, spout, rightA, leftA, underA);
  // the can's meshes, at rest, vertices absolute (origin 0): the exporter stores them relative to their group's pivot
  const capParts = new Set(['spout_cap', 'cam_lever']);
  for (const p of B.PARTS) {
    if (p.bone !== 'jerry_can/body') continue;
    const short = p.name.split('__').pop(), key = i => i.toString(36).padStart(4, '0'), vertices = {}, faces = {}, uvs = B.UV.faceUV.get(p);
    p.v.forEach((q, i) => { vertices[key(i)] = rig(q).map(v => +v.toFixed(8)); });
    p.f.forEach((f, fi) => { const uv = uvs.get(f); faces['f' + key(fi)] = {uv: Object.fromEntries(f.ids.map((id, j) => [key(id), uv[j].map(v => +v.toFixed(8))])), vertices: f.ids.map(key), texture: 0}; });
    const e = {name: short, color: 0, origin: [0, 0, 0], rotation: [0, 0, 0], shading: 'flat', export: true, visibility: true, locked: false,
      render_order: 'default', scope: 0, allow_mirror_modeling: true, vertices, faces, type: 'mesh', uuid: uuid('mesh:' + short)};
    elements.push(e);
    (capParts.has(short) ? capG : can).children.push(e.uuid);
  }
  // source-only reference arms: the hand's end at the anchor, the forearm toward -Y (the canonical arm frame)
  for (const [g, name, o] of [[rightG, 'right', P.right_hand_anchor], [leftG, 'left', P.left_hand_anchor], [underG, 'left_under', P.left_under_anchor]]) {
    const e = {name: name + '_arm_reference_only', box_uv: false, render_order: 'default', locked: false, export: false, scope: 0,
      allow_mirror_modeling: true, from: add(o, [-ARM.w / 2, -ARM.l, -ARM.w / 2]).map(r4), to: add(o, [ARM.w / 2, 0, ARM.w / 2]).map(r4), autouv: 0, color: 6,
      visibility: true, origin: o.map(r4), rotation: [0, 0, 0],
      faces: Object.fromEntries(['north', 'east', 'south', 'west', 'up', 'down'].map(f => [f, {uv: [1, 1, 7, 7], texture: 1}])), type: 'cube', uuid: uuid('arm:' + name)};
    elements.push(e);
    g.children.push(e.uuid);
  }
  const atlas = B.atlas, reference = refTexture();
  return {meta: {format_version: '5.0', model_format: 'free', box_uv: false}, name: 'jerry_can_first_person', model_identifier: '', visible_box: [1, 1, 0],
    variable_placeholders: '', variable_placeholder_buttons: [], timeline_setups: [], unhandled_root_fields: {}, resolution: {width: atlas, height: atlas},
    elements, groups, outliner: [root],
    textures: [texture('fuel_containers_v1.png', 'textures/fuel_containers_v1.png', atlas, B.maps[0], 'texture'),
      texture('afl_arm_reference_source_only.png', '', 16, reference, 'reference')],
    animations: Object.entries(CLIPS).map(([name, clip]) => bbAnimation(name, clip, groups))};
}
function texture(name, rel, size, data, id) {
  return {name, relative_path: rel, folder: '', namespace: '', id: id === 'texture' ? '0' : '1', group: '', scope: 0, width: size, height: size, uv_width: size, uv_height: size,
    particle: false, use_as_default: false, layers_enabled: false, sync_to_project: '', file_format: 'png', render_mode: 'default', render_sides: 'auto',
    wrap_mode: 'limited', pbr_channel: 'color', fps: 7, frame_time: 1, frame_order_type: 'loop', frame_order: '', frame_interpolate: false, visible: true,
    internal: true, saved: true, uuid: uuid('texture:' + id), source: 'data:image/png;base64,' + data.toString('base64')};
}
// a flat warm skin tone, 16 x 16 (the reference arms only)
function refTexture() {
  const w = 16, raw = Buffer.alloc((w * 4 + 1) * w);
  for (let y = 0; y < w; y++) for (let x = 0; x < w; x++) raw.set([196, 140, 108, 255], y * (w * 4 + 1) + 1 + x * 4);
  const crcT = Array.from({length: 256}, (_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xEDB88320 ^ (c >>> 1) : c >>> 1; return c >>> 0; });
  const crc = b => { let c = 0xFFFFFFFF; for (const x of b) c = crcT[(c ^ x) & 0xFF] ^ (c >>> 8); return (c ^ 0xFFFFFFFF) >>> 0; };
  const chunk = (t, d) => { const len = Buffer.alloc(4); len.writeUInt32BE(d.length); const td = Buffer.concat([Buffer.from(t), d]); const c = Buffer.alloc(4); c.writeUInt32BE(crc(td)); return Buffer.concat([len, td, c]); };
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(w, 4); ihdr[8] = 8; ihdr[9] = 6;
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]);
}

// ---------------- dense keys ----------------
// GeckoLib interpolates each Euler angle on its own, which strays far from the turn between two orientations some tens of
// degrees apart (in game, 2026-10-05: the arms swung and twitched between keys); so the key poses are sampled every STEP
// here (positions and angles catmull-rom, orientations blended as matrices and re-orthonormalised) and written as linear
// keys, close enough that the per-angle interpolation between them stays on the path.
const STEP = 0.05;
const cr = (a, b, c, d, u) => b.map((_, i) => 0.5 * (2 * b[i] + (c[i] - a[i]) * u + (2 * a[i] - 5 * b[i] + 4 * c[i] - d[i]) * u * u + (3 * b[i] - a[i] - 3 * c[i] + d[i]) * u * u * u));
const crM = (a, b, c, d, u) => { const col = j => cr(a[j], b[j], c[j], d[j], u), Y = norm(col(1)), X0 = col(0), X = norm(sub(X0, mul(Y, dot(X0, Y)))); return [X, Y, cross(X, Y)]; };
const crHand = (a, b, c, d, u) => { b ||= c; c ||= b; a ||= b; d ||= c;
  if (!b.under !== !c.under) return u < 0.5 ? b : c;   // the two left hands (left_hand_anchor, left_under_anchor) swap
  if (!a.under !== !b.under) a = b;
  if (!d.under !== !c.under) d = c;
  return {under: b.under, O: cr(a.O, b.O, c.O, d.O, u), M: crM(a.M, b.M, c.M, d.M, u), S: cr(a.S, b.S, c.S, d.S, u)}; };
function densify(clip) {
  const keys = clip.keys, n = keys.length, out = [];
  const at = i => clip.loop === true ? keys[(i + n - 1) % (n - 1)] : keys[Math.max(0, Math.min(n - 1, i))];   // loops: first = last
  const times = new Set(keys.map(k => k[0]));
  for (let t = 0; t < clip.length - 1e-6; t += STEP) times.add(+t.toFixed(4));
  for (const t of [...times].sort((x, y) => x - y)) {
    let i = 0; while (i < n - 2 && keys[i + 1][0] <= t) i++;
    const [t0, b] = keys[i], [t1, c] = keys[i + 1], a = at(i - 1)[1], d = at(i + 2)[1], u = Math.min(1, Math.max(0, (t - t0) / (t1 - t0)));
    if (u === 0 || u === 1) { out.push([t, u === 0 ? b : c]); continue; }
    out.push([t, {can: {g: cr(a.can.g, b.can.g, c.can.g, d.can.g, u), M: crM(a.can.M, b.can.M, c.can.M, d.can.M, u)},
      cap: {pos: cr(a.cap.pos, b.cap.pos, c.cap.pos, d.cap.pos, u), e: cr(a.cap.e, b.cap.e, c.cap.e, d.cap.e, u)},
      right: crHand(a.right, b.right, c.right, d.right, u), left: b.left || c.left ? crHand(a.left, b.left, c.left, d.left, u) : null,
      cam: cr(a.cam, b.cam, c.cam, d.cam, u)}]);
  }
  return out;
}

// the clip's keyframes in Blockbench form: every bone's rotation and position every STEP, the hidden hand's scale at the keys
function bbAnimation(name, clip, groups) {
  const byName = Object.fromEntries(groups.map(g => [g.name, g.uuid])), animators = {}, prev = {};
  const keyframe = (bone, channel, t, v, interpolation = 'linear') => {
    (animators[byName[bone]] ||= {name: bone, type: 'bone', keyframes: []}).keyframes.push({channel, data_points: [{x: String(r4(v[0])), y: String(r4(v[1])), z: String(r4(v[2]))}],
      uuid: uuid(`${name}:${bone}:${channel}:${t}`), time: t, color: -1, interpolation});
  };
  for (const [t, p] of clip.keys) {
    keyframe('cap', 'scale', t, p.cap.hidden ? [0, 0, 0] : [1, 1, 1], 'step');
    keyframe('left_hand_anchor', 'scale', t, p.left && !p.left.under ? [1, 1, 1] : [0, 0, 0], 'step');
    keyframe('left_under_anchor', 'scale', t, p.left?.under ? [1, 1, 1] : [0, 0, 0], 'step');
  }
  for (const [t, p] of densify(clip)) {
    const can = p.can, Mt = tr(can.M);
    keyframe('can', 'rotation', t, (prev.can = eulerNear(can.M, prev.can)));
    if (Math.abs(Math.cos(prev.can[1] * D2R)) < 0.26) throw new Error(`${name} can at ${t}: middle angle ${prev.can[1].toFixed(1)}, near the Euler singularity`);
    keyframe('can', 'position', t, sub(can.g, G0));
    keyframe('cap', 'rotation', t, p.cap.e);
    keyframe('cap', 'position', t, mul(p.cap.pos, K));
    keyframe('camera', 'rotation', t, mul(p.cam, -1));   // the view turns opposite the bone (NativeCameraBoneConsumer)
    for (const [bone, hand] of [['right_hand_anchor', p.right], [p.left?.under ? 'left_under_anchor' : 'left_hand_anchor', p.left]]) {
      if (!hand) continue;
      keyframe(bone, 'rotation', t, (prev[bone] = eulerNear(mm(handRel(can, hand), tr(HAND_Q[bone])), prev[bone])));
      if (Math.abs(Math.cos(prev[bone][1] * D2R)) < 0.26) throw new Error(`${name} ${bone} at ${t}: middle angle ${prev[bone][1].toFixed(1)}, near the Euler singularity`);
      keyframe(bone, 'position', t, sub(add(G0, ap(Mt, sub(hand.O, can.g))), P[bone]));
      const end = armEndShows(hand), inside = armInBody(can, hand);
      if (inside) ARM_CLIPS.push({clip: name, t, bone, inside});
      if (end) ARM_ENDS.push(`${name} ${t} ${bone}: [${end.map(v => v.toFixed(1))}] at ${(end[0] / -end[2]).toFixed(2)}, ${(end[1] / -end[2]).toFixed(2)}`);
    }
  }
  const loop = clip.loop === true ? 'loop' : 'hold';
  return {uuid: uuid('animation:' + name), name, loop, override: false, length: clip.length, snapping: 20, selected: false, anim_time_update: '',
    blend_weight: '', start_delay: '', loop_delay: '', animators};
}

// ---------------- runtime from a source ----------------
function geoOf(source) {
  const G = new Map(source.groups.map(g => [g.uuid, g])), bones = [];
  const walk = (node, parent) => { const g = G.get(node.uuid); if (!g || g.export === false) return;
    bones.push({name: g.name, ...(parent ? {parent} : {}), pivot: [r4(-g.origin[0]), r4(g.origin[1]), r4(g.origin[2])],
      ...(g.rotation.some(v => v) ? {rotation: [r4(-g.rotation[0]), r4(-g.rotation[1]), r4(g.rotation[2])]} : {})});
    for (const c of node.children || []) if (typeof c !== 'string') walk(c, g.name); };
  for (const n of source.outliner) if (typeof n !== 'string') walk(n, null);
  return {format_version: '1.12.0', 'minecraft:geometry': [{description: {identifier: 'geometry.jerry_can_first_person', texture_width: source.resolution.width,
    texture_height: source.resolution.height, visible_bounds_width: 4, visible_bounds_height: 4, visible_bounds_offset: [0, 0, 0]}, bones}]};
}
function animationOf(source) {
  const out = {format_version: '1.8.0', animations: {}};
  for (const a of source.animations) {
    const bones = {};
    for (const animator of Object.values(a.animators)) {
      if (animator.type && animator.type !== 'bone') continue;
      for (const k of [...animator.keyframes].sort((x, y) => x.time - y.time)) {
        const v = ['x', 'y', 'z'].map(c => Number(k.data_points[0][c])), sign = k.channel === 'rotation' ? [-1, -1, 1] : k.channel === 'position' ? [-1, 1, 1] : [1, 1, 1];
        const value = v.map((x, i) => r4(x * sign[i])), t = (+k.time).toFixed(4).replace(/0+$/, '').replace(/\.$/, '.0');
        const track = ((bones[animator.name] ||= {})[k.channel] ||= {});
        track[t] = k.interpolation === 'catmullrom' ? {post: value, lerp_mode: 'catmullrom'} : k.interpolation === 'step' ? {pre: value, post: value} : value;
      }
    }
    out.animations[a.name] = {loop: a.loop === 'loop' ? true : a.loop === 'hold' ? 'hold_on_last_frame' : false, animation_length: a.length, bones};
  }
  return out;
}
const POUR_DELAY_JAVA = path.join(ROOT, 'src/main/java/com/antaurora/apofirstlight/item/FuelCanItem.java');
function runtime(source) {
  const start = source.animations.find(a => a.name === 'pour_start'), ticks = Math.round(start.length * 20);
  if (!fs.readFileSync(POUR_DELAY_JAVA, 'utf8').includes(`POUR_DELAY = ${ticks};`))
    throw new Error(`pour_start is ${start.length} s: set item/FuelCanItem POUR_DELAY = ${ticks}`);
  const geo = geoOf(source);
  const mesh = convert(source, geo, {}, SOURCE, 2);
  return [[GEO, JSON.stringify(geo, null, 2) + '\n'], [MESH, JSON.stringify(mesh) + '\n'], [ANIMATION, JSON.stringify(animationOf(source), null, 2) + '\n']];
}

const isMain = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  const args = process.argv.slice(2);
  let source;
  if (args.includes('--export') || args.includes('--check')) source = JSON.parse(fs.readFileSync(SOURCE, 'utf8'));
  else { source = buildSource(); fs.writeFileSync(SOURCE, JSON.stringify(source)); console.log('wrote ' + path.relative(ROOT, SOURCE)); }
  const files = runtime(source);
  if (args.includes('--check')) {
    for (const [file, text] of files) if (!fs.existsSync(file) || fs.readFileSync(file, 'utf8') !== text) throw new Error('stale ' + path.relative(ROOT, file));
    console.log('CHECK OK');
  } else {
    for (const [file, text] of files) { fs.mkdirSync(path.dirname(file), {recursive: true}); fs.writeFileSync(file, text); console.log('wrote ' + path.relative(ROOT, file)); }
  }
  console.log('clips', Object.fromEntries(source.animations.map(a => [a.name, a.length])));
  if (ARM_CLIPS.length) {
    const by = {};
    for (const c of ARM_CLIPS) { const k = c.clip + ' ' + c.bone, e = (by[k] ||= {frames: 0, max: 0, at: 0, from: c.t, to: c.t}); e.frames++; e.to = c.t; if (c.inside > e.max) { e.max = c.inside; e.at = c.t; } }
    console.log('ARM INSIDE THE CAN (sample points):\n  ' + Object.entries(by).map(([k, e]) => `${k}: ${e.frames} frames ${e.from}-${e.to}, worst ${e.max} at ${e.at}`).join('\n  '));
  }
  if (ARM_ENDS.length) console.log(`ARM END ON SCREEN (${ARM_ENDS.length}):\n  ` + ARM_ENDS.filter((_, i) => i % 4 === 0).join('\n  '));
}
