// Wooden knob at the workbench (2026-10-22, docs/gameplay/furniture_recipes_v1.md 4.1): writes the props
// player_props/wooden_knob_<stage>.json (the blank at each stage of the whittling, parts of the wooden_knob_work mesh) and
// the action player_actions/tool/wooden_knob_carve.json (one 10 s loop per knob, the recipe's time), and the chest clip
// tool.wooden_knob_carve lives in tools/author-player-actions.mjs (CHEST below, the same numbers). No arm clips: the props'
// placements and the hands' targets, solved by the IK.
//
// The work (client/workbench/WorkbenchWork KNOB_*, the same numbers as WORK below; client/WorkbenchRenderer draws the hook
// and the strip): a bench hook on the top at the bench's right end, a 3.2 cm strip on it against the back cleat, its end
// over the hook's right end. One loop:
//   0-0.7     the saw comes up from beside the right hip to the cut, the left hand presses the strip on the hook;
//   0.7-2.7   three strokes across the strip (push 0.4 s, pull 0.4 s), the kerf going down through it, the saw flatter each
//             stroke (its toe stays over the bench top); the end falls off onto the top at 2.7;
//   2.7-3.35  the saw lifted out and back to the hip (gone at 3.35);
//   3.35-4.1  the right hand picks the off-cut up and brings it before the body, the left hand takes it by its foot;
//   4.1-4.9   the right hand takes the knife from the hip (open) to the blank;
//   4.9-8.95  whittling (push cuts away from the left hand, the blank turned between cuts): the corners (2 cuts, octagon at
//             6.0), rounding (3 cuts, at 7.4), the neck (2 cuts, at 8.1), turned end for end and held by the head, the
//             tenon (2 cuts, finished at 8.95);
//   8.95-9.75 the knife back to the hip (gone at 9.3, the saw there again), the knob turned before the eyes, the chips blown
//             off (9.3), set down on its side on the top left of the hook;
//   9.75-10   the left hand back on the strip: the loop starts again.
// Every time Java needs (the saw's sounds and dust, the whittle sounds and shavings, the off-cut's fall, the blow, the set
// down) is in the action's events (WorkbenchWork reads them), positions as model px from the carve anchor.
//   node tools/author-wooden-knob-actions.mjs [--check] [--report]
import fs from 'node:fs';
import path from 'node:path';
import {createRequire} from 'node:module';
import {fileURLToPath} from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const ASSETS = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight');
const read = p => JSON.parse(fs.readFileSync(path.join(ASSETS, p), 'utf8'));
const r4 = v => v.map(n => Math.round(n * 10000) / 10000 + 0);
const r3 = v => v.map(n => Math.round(n * 1000) / 1000 + 0);
const r2 = v => v.map(n => Math.round(n * 100) / 100 + 0);
const RAD = Math.PI / 180, DEG = 180 / Math.PI;
const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
const add = (a, b) => a.map((v, i) => v + b[i]), sub = (a, b) => a.map((v, i) => v - b[i]), mul = (a, k) => a.map(v => v * k);
const dot = (a, b) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
const cross = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
const norm = a => mul(a, 1 / Math.hypot(...a));
const perp = (v, axis) => norm(sub(v, mul(axis, dot(v, axis))));

// ---------------------------------------------------------------- the work (blocks, the bench's north frame; WorkbenchWork KNOB_*)
/** Model px per block (PlayerRenderer scales the model by 0.9375); the feet at model y 24.016. */
const PX = 16 / 0.9375, FEET = 1.501 * 16;
/** The blank prop's scale (the knife's 0.42 px per cm, as the 4 px fist to a hand); the strip on the hook is drawn as big. */
const SCALE = 1.35;
/** One real cm of the work mesh in the world (blocks: the mesh's cm, the prop's scale, the model's 0.9375). */
const CM = 0.0196850394 * SCALE * 0.9375;
export const WORK = {
  hookTop: 0.92,                   // the hook board's top (board x 0.11..0.42, z 0.02..0.24, on the top at 0.9)
  backCleat: 0.21,                 // the back cleat's front face
  cutX: 0.10,                      // the saw's plane, just right of the hook board's end
  standLeft: 0.36, standBack: 0.34, yaw: -15,   // the stance off the cut: as tools/author-hand-saw-actions.mjs, closer (the strip is low)
  holdLeft: 0.13, holdUp: 0.03,    // the left hand on the strip: left of the cut, over its top
  carveAhead: 0.30, carveLeft: 0.0, carveUp: 1.05,   // the blank in the hands while whittling (from the stand point)
  setX: 0.56, setZ: 0.10,          // where the knob is set down, on its side
};
const STICK_HALF = 1.6 * CM, STICK_Z = WORK.backCleat - STICK_HALF, STICK_TOP = WORK.hookTop + 2 * STICK_HALF;
const KERF_END = WORK.hookTop - 0.004;
const STAND = [WORK.cutX + WORK.standLeft, STICK_Z - WORK.standBack];
const YS = Math.sin(WORK.yaw * RAD), YC = Math.cos(WORK.yaw * RAD);
/** A bench point (x, y, z) as model px of the body stood at STAND facing yaw (the hand saw script's m()). */
const model = (x, y, z) => { const l = x - STAND[0], a = z - STAND[1]; return [(l * YC - a * YS) * PX, FEET - y * PX, -(l * YS + a * YC) * PX]; };
/** A bench direction as a model direction. */
const modelDir = v => norm([v[0] * YC - v[2] * YS, -v[1], -(v[0] * YS + v[2] * YC)]);
const CARVE = [STAND[0] + WORK.carveLeft * YC + WORK.carveAhead * YS, WORK.carveUp, STAND[1] - WORK.carveLeft * YS + WORK.carveAhead * YC];
const SET_Y = 0.9 + 1.5 * CM;                      // the head's radius over the top
const OFFCUT = [WORK.cutX - 2.1 * CM, 0.9 + STICK_HALF, STICK_Z];
/** The kerf's depth by the cycle's second: down through the strip over the strokes (WorkbenchWork#knobKerfY). */
const kerfY = t => STICK_TOP - (STICK_TOP - KERF_END) * clamp((t - 0.7) / 2.0, 0, 1);
const anchorsAt = t => ({
  kerf: model(WORK.cutX, kerfY(t), STICK_Z),
  hold: model(WORK.cutX + WORK.holdLeft, STICK_TOP + WORK.holdUp, STICK_Z),
  offcut: model(...OFFCUT),
  carve: model(...CARVE),
  set: model(WORK.setX, SET_Y, WORK.setZ),
});

// ---------------------------------------------------------------- the props
// The work mesh (tools/build-furniture-materials-v1.mjs woodenKnobWork): its foot at item y 0.458661, the blank standing
// along +y 4.2 cm from the tenon's end, 3.2 cm square, the axis on x = z = 0.5.
const VERTICAL = 0.458661, MCM = 0.0196850394;
const KP = 16 * SCALE;                               // px per item unit
const OUT = 2.0 / KP;                                // a hand's palm face on a point: 2 px past it (as medical_alcohol_cap)
const FOOT = VERTICAL, HEAD = VERTICAL + 4.2 * MCM;
const STAGES = ['blank', 'octagon', 'rough', 'necked', 'finished'];
const KNOB_POINTS = {
  center: [0.5, (FOOT + HEAD) / 2, 0.5],
  foot: [0.5, FOOT, 0.5],
  head: [0.5, HEAD, 0.5],
  hold_foot: [0.5, FOOT - OUT, 0.5],
  hold_head: [0.5, HEAD + OUT, 0.5],
  pinch: [0.5, (FOOT + HEAD) / 2, 0.5 + 1.6 * MCM + OUT],
};
const knobProp = stage => ({
  about: 'The wooden knob\'s blank at the workbench, stage ' + stage + ' (docs/gameplay/furniture_recipes_v1.md 4.1; written by tools/author-wooden-knob-actions.mjs): the part ' + stage + ' of the wooden_knob_work mesh (code only, no item). Points, bounds: item block units of the mesh as drawn (meshes/wooden_knob_work.aflmesh.json + (0.5, ' + VERTICAL + ', 0.5)); y along the blank from the tenon\'s end (foot) to the head. Scale ' + SCALE + ': as the knife (0.42 px per cm), the 4 px fist a hand. center; foot, head (the ends); hold_foot, hold_head: the left palm on an end (2 px past it); pinch: the right palm on the +z face.',
  items: [],
  scale: SCALE,
  bounds: [r4([0.5 - 1.6 * MCM, FOOT, 0.5 - 1.6 * MCM]), r4([0.5 + 1.6 * MCM, HEAD, 0.5 + 1.6 * MCM])],
  symmetry: 'x',
  mesh: {model: 'wooden_knob_work', atlas: 'wooden_knob_work_mesh', offset: VERTICAL, parts: [stage]},
  points: Object.fromEntries(Object.entries(KNOB_POINTS).map(([n, p]) => [n, r4(p)])),
});
const PROPS = Object.fromEntries(STAGES.map(s => ['wooden_knob_' + s, knobProp(s)]));
const SAW = read('player_props/hand_saw.json'), KNIFE = read('player_props/folding_knife.json');
const local = (prop, p) => { const k = 16 * prop.scale, q = typeof p === 'string' ? prop.points[p] : p; return [q[0] * k, -q[1] * k, -q[2] * k]; };
const defs = {...PROPS, hand_saw: SAW, folding_knife: KNIFE};
// the knife's edge where it cuts: 35 % out from the pivot, just in from the edge (the blade's edge at x 0.418 there)
const KNIFE_CUT = [0.425, 0.77, 0.5];

// ---------------------------------------------------------------- turns
/** The key turn (deg, about z then y then x) taking the prop's lu to h and its lw toward b. */
function turnFor(lu, lw, h, b) {
  const lv = cross(lu, lw);
  h = norm(h);
  b = perp(b, h);
  const v = cross(h, b);
  const R = [0, 1, 2].map(r => [0, 1, 2].map(c => h[r] * lu[c] + b[r] * lw[c] + v[r] * lv[c]));
  const yb = Math.asin(clamp(R[0][2], -1, 1));
  if (Math.abs(R[0][2]) > 0.99999) return [Math.atan2(R[2][1], R[1][1]) * DEG, yb * DEG, 0];
  return [Math.atan2(-R[1][2], R[2][2]) * DEG, yb * DEG, Math.atan2(-R[0][1], R[0][0]) * DEG];
}
function turnApply(rot, v) {
  const [a, b, c] = rot.map(d => d * RAD);
  let [x, y, z] = v;
  [x, y] = [x * Math.cos(c) - y * Math.sin(c), x * Math.sin(c) + y * Math.cos(c)];
  [x, z] = [x * Math.cos(b) + z * Math.sin(b), -x * Math.sin(b) + z * Math.cos(b)];
  [y, z] = [y * Math.cos(a) - z * Math.sin(a), y * Math.sin(a) + z * Math.cos(a)];
  return [x, y, z];
}
// the blank: item +y (to the head) local -y, item +z local -z; the knife: up the blade local -y, the edge local -x; the saw
// as tools/author-hand-saw-actions.mjs (along the tooth line to the toe, the teeth)
const knobTurn = (h, b) => r3(turnFor([0, -1, 0], [0, 0, -1], h, b));
const knifeTurn = (h, b) => r3(turnFor([0, -1, 0], [-1, 0, 0], h, b));
const SAW_U = norm(sub(local(SAW, 'toe'), local(SAW, 'heel')));
const SAW_W = perp(sub(local(SAW, 'heel'), local(SAW, 'grip')), SAW_U);
const sawTurn = (h, b) => r3(turnFor(SAW_U, SAW_W, h, b));

// ---------------------------------------------------------------- the saw (prop 'hand_saw')
const STROKE = 0.10;                                  // half a stroke (blocks)
const TOE = Math.hypot(...sub(local(SAW, 'toe'), local(SAW, 'kerf'))) / PX;   // the kerf point to the toe (blocks)
/** The tooth line's slope at a second: as steep as 14 deg while the toe at the push's end stays 5 mm over the top. */
const sawSlope = t => Math.min(14, Math.asin(clamp((kerfY(t) - 0.905) / (TOE + STROKE), 0, 1)) * DEG);
const sawDir = deg => [-YS * Math.cos(deg * RAD), Math.sin(deg * RAD), -YC * Math.cos(deg * RAD)];
const sawTeeth = deg => [YS * Math.sin(deg * RAD), Math.cos(deg * RAD), YC * Math.sin(deg * RAD)];
const sawKey = (at, u, blend, reach, lift = 0) => {
  const deg = sawSlope(reach);
  return {at, frame: 'anchor:kerf', point: 'kerf', pos: r2(add(mul(sawDir(deg), u * PX), [0, -lift * PX, 0])), rot: sawTurn(sawDir(deg), sawTeeth(deg)), blend};
};
const SAW_HIP = read('player_actions/tool/hand_saw_set.json').prop.keys[0];
const hip = (at, hidden, blend = 0) => ({at, frame: SAW_HIP.frame, point: SAW_HIP.point, pos: [0.5, 8.3, -3.0], rot: SAW_HIP.rot, blend, ...(hidden ? {hidden: true} : {})});
const SAW_KEYS = [
  hip(0, false),
  sawKey(0.15, -STROKE, 0.55, 0.7),
  ...[0, 1, 2].flatMap(i => [
    sawKey(0.7 + 0.8 * i, STROKE, 0.4, 1.1 + 0.8 * i),
    ...(i < 2 ? [sawKey(1.1 + 0.8 * i, -STROKE, 0.4, 1.5 + 0.8 * i)] : []),
  ]),
  sawKey(2.7, -STROKE * 0.6, 0.3, 3.0, 0.08),         // through: lifted out
  hip(3.0, false, 0.35),
  hip(3.35, true),
  hip(9.3, false),
];

// ---------------------------------------------------------------- the carving (model px from the carve anchor)
// the blank's axis while the head is worked: from the left hand ahead, right and up; the face cut: up, toward the eyes
const A = norm([-0.55, -0.35, -0.75]);
const N = perp([0.1, -1, 0.5], A);
let T = norm(cross(A, N));
if (T[0] < 0) T = mul(T, -1);                          // the blade across to the left, the handle in the right hand
const around = (axis, ref, deg) => add(mul(ref, Math.cos(deg * RAD)), mul(cross(axis, ref), Math.sin(deg * RAD)));
/** The blank at the carve anchor: its axis (to the head) a, its +z face turned deg about it from n. */
const knobKey = (at, a, n, deg, blend, extra = {}) => ({at, frame: 'anchor:carve', point: 'center', pos: [0, 0, 0], rot: knobTurn(a, around(a, perp(n, a), deg)), blend, ...extra});
const KNIFE_H = norm(add(T, mul(A, 0.35))), KNIFE_B = norm(sub(mul(A, 0.5), N));
const KNIFE_ROT = knifeTurn(KNIFE_H, KNIFE_B);
const KNIFE_REACH = sub(local(KNIFE, KNIFE_CUT), local(KNIFE, 'grip'));
/** The knife with its cutting edge at c (model px from the carve anchor). */
const knifeKey = (at, c, blend, ease) => ({at, frame: 'anchor:carve', point: 'grip', pos: r3(sub(c, turnApply(KNIFE_ROT, KNIFE_REACH))), rot: KNIFE_ROT, blend, ...(ease ? {ease} : {})});
const PXCM = MCM * KP;                                 // the blank's px per cm
/** A point on the blank: s px along the working axis from the centre, r px out on the cut face. */
const on = (s, r) => add(mul(A, s), mul(N, r));

const KNIFE_HIP = {frame: 'chest', point: 'grip', pos: [-4.4, 8.8, -2.8], rot: knifeTurn([0.1, -0.9, -1], [0, 1, 0])};
const knifeHip = (at, hidden, blend = 0) => ({at, ...KNIFE_HIP, blend, ...(hidden ? {hidden: true} : {})});

// the cuts: [start, length, from s, to s, r (px), the blank's turn (deg)]; the head 2.1 cm ahead of the centre, the foot behind
const H = 2.1 * PXCM;
const CUTS = {
  corners: [[4.9, 0.55, 0.0, 0.9 * H, 0.74, 0], [5.45, 0.55, 0.0, 0.9 * H, 0.74, 90]],
  rounding: [[6.0, 0.47, -0.6 * H, 0.9 * H, 0.68, 45], [6.47, 0.47, -0.6 * H, 0.9 * H, 0.66, 160], [6.94, 0.46, -0.6 * H, 0.9 * H, 0.66, 260]],
  neck: [[7.4, 0.35, 0.05 * H, 0.45 * H, 0.46, 320], [7.75, 0.35, 0.05 * H, 0.45 * H, 0.44, 410]],
  // turned end for end at 8.1 (held by the head): s from the centre toward the foot, now ahead
  tenon: [[8.35, 0.3, 0.15 * H, 0.95 * H, 0.32, 0], [8.65, 0.3, 0.15 * H, 0.95 * H, 0.3, 120]],
};
const FLIP = 8.1, FLIP_LEN = 0.25, DONE = 8.95;

const BLANK_KEYS = [], KNIFE_KEYS = [], EVENTS = [];
const off = {hidden: true};
// on the bench, then lifted to the hands
const OFF_A = modelDir([-1, 0, 0]), OFF_N = modelDir([0, 1, 0]);
const offKey = (at, drop, blend, extra = {}) => ({at, frame: 'anchor:offcut', point: 'center', pos: r3([0, -drop * PX, 0]), rot: knobTurn(OFF_A, OFF_N), blend, ...extra});
BLANK_KEYS.push(offKey(0, 0.02, 0, off), offKey(2.7, 0.02, 0), offKey(2.72, 0, 0.1, {ease: 'in'}));
BLANK_KEYS.push(knobKey(3.7, A, N, -30, 0.4, {pos: r3([0, -0.6, 0.8])}));   // held up before the body, the right hand under it
KNIFE_KEYS.push(knifeHip(0, true), knifeHip(4.1, false));
const firstCut = CUTS.corners[0];
KNIFE_KEYS.push(knifeKey(4.4, add(on(firstCut[2], firstCut[4] + 0.8), mul(A, -0.3)), 0.45));
EVENTS.push(
  ...[0, 1, 2].flatMap(i => [{at: r3([0.7 + 0.8 * i])[0], type: 'saw_push', until: r3([1.1 + 0.8 * i])[0]}, ...(i < 2 ? [{at: r3([1.1 + 0.8 * i])[0], type: 'saw_pull'}] : [])]),
  {at: 2.7, type: 'cut_through'},
  {at: 2.82, type: 'drop', pos: r2(sub(model(...OFFCUT), model(...CARVE)))},
);
// keys of the base blank from 6.0 on are hidden (the later stages ride on it)
const shown = at => at >= 2.7 && at < 6.0;
const stageBlank = (key) => ({...key, ...(shown(key.at) ? {} : {hidden: true})});
for (const [phase, cuts] of Object.entries(CUTS)) for (const [t0, len, s0, s1, r, deg] of cuts) {
  const flipped = phase === 'tenon';
  // the blank: turned for this cut while the knife comes back (flipped: its axis back into the left hand, s toward the foot)
  const axis = flipped ? mul(A, -1) : A;
  BLANK_KEYS.push(stageBlank(knobKey(t0, axis, N, deg, 0.35 * len)));
  const from = on(s0, r), to = on(s1, r * 0.92);
  KNIFE_KEYS.push(
    knifeKey(t0, add(on(s0 - 0.2, r + 0.7), [0, 0, 0]), 0.35 * len),
    knifeKey(r3([t0 + 0.35 * len])[0], from, 0.1 * len),
    knifeKey(r3([t0 + 0.45 * len])[0], to, 0.45 * len),
    knifeKey(r3([t0 + 0.9 * len])[0], on(s1 + 0.25, r + 0.6), 0.1 * len),
  );
  EVENTS.push({at: r3([t0 + 0.45 * len])[0], type: 'whittle', until: r3([t0 + 0.9 * len])[0], from: r2(from), to: r2(to),
    dir: r3(norm(sub(to, from))), out: r3(N), phase});
}
// turned end for end (8.1): the head into the left hand
BLANK_KEYS.push(stageBlank(knobKey(FLIP, mul(A, -1), N, 0, FLIP_LEN)));
BLANK_KEYS.sort((a, b) => a.at - b.at);
// done: the knife back to the hip, the knob turned before the eyes, blown, set down
KNIFE_KEYS.push(knifeHip(DONE, false, 0.35), knifeHip(9.3, true));
const LOOK = norm([-0.15, -0.55, -0.8]);
const SET_A = modelDir([-Math.cos(14 * RAD), Math.sin(14 * RAD), 0]), SET_N = modelDir([0, 1, 0]);
BLANK_KEYS.push(
  stageBlank(knobKey(DONE, LOOK, N, 0, 0.3, {pos: [0, -2.4, 2.2]})),
  stageBlank(knobKey(9.25, LOOK, N, 110, 0.2, {pos: [0, -2.6, 2.4]})),
  stageBlank({at: 9.45, frame: 'anchor:set', point: 'center', pos: [0, 0, 0], rot: knobTurn(SET_A, SET_N), blend: 0.3}),
);
EVENTS.push(
  {at: 9.3, type: 'blow', pos: r2(add([0, -2.6, 2.4], mul(LOOK, H)))},
  {at: 9.72, type: 'set', pos: r2(sub(model(WORK.setX, SET_Y, WORK.setZ), model(...CARVE)))},
);
EVENTS.sort((a, b) => a.at - b.at);

// the later stages ride on the blank, each shown from its swap to the next
const SWAPS = [['octagon', 6.0, 7.4], ['rough', 7.4, FLIP], ['necked', FLIP, DONE], ['finished', DONE, 99]];
const CENTER = r4(local(PROPS.wooden_knob_blank, 'center'));
const rider = (at, hidden) => ({at, frame: 'prop:wooden_knob_blank', point: 'center', pos: CENTER, rot: [0, 0, 0], blend: 0, ...(hidden ? {hidden: true} : {})});
const STAGE_TRACKS = SWAPS.map(([stage, from, to]) => ({id: 'wooden_knob_' + stage, keys: [rider(0, true), rider(from, false), ...(to < 10 ? [rider(to, true)] : [])]}));

const L_POLE = [0.8, 1.0, 0.2];
/**
 * The look down (deg; the head, and the own first-person view on the posed eye): PlayerAction first_person_view, which
 * with an approach takes the place of its pitch (ActionPose#pitch). A fixed 40 left the wrists 44-67 deg under the view's
 * centre, out of a 70 deg high view (in-game, 2026-10-22): down on the kerf while sawing, lower for the off-cut, on the
 * blank in the hands while whittling, up a little to look the knob over, the loop's end as its start.
 */
export const FP_VIEW = [[0, 56], [0.7, 63], [2.7, 63], [3.1, 58], [3.6, 64], [4.3, 70], [8.95, 70], [9.3, 62], [9.6, 60], [10, 56]];
const viewAt = t => {
  if (t <= FP_VIEW[0][0]) return FP_VIEW[0][1];
  for (let i = 1; i < FP_VIEW.length; i++) {
    if (t > FP_VIEW[i][0]) continue;
    const u = (t - FP_VIEW[i - 1][0]) / (FP_VIEW[i][0] - FP_VIEW[i - 1][0]), e = u * u * (3 - 2 * u);
    return FP_VIEW[i - 1][1] + (FP_VIEW[i][1] - FP_VIEW[i - 1][1]) * e;
  }
  return FP_VIEW[FP_VIEW.length - 1][1];
};
const ACTION = {
  format_version: 1,
  about: 'The wooden knob at the workbench (client/workbench/WorkbenchWork, the recipe woodwork/wooden_knob\'s work; written by tools/author-wooden-knob-actions.mjs): one 10 s loop per knob, the recipe\'s time. A bench hook at the bench\'s right end, a strip on it (client/WorkbenchRenderer). 0-0.7 s the saw comes up from the right hip, the left hand presses the strip; 0.7-2.7 three strokes across it, the kerf going down, the saw flatter each stroke; the end falls off at 2.7; the saw back to the hip (3.0-3.35); the right hand brings the off-cut up, the left takes it by the foot (3.35-4.1); the right takes the open knife from the hip (4.1-4.9); whittling, push cuts away from the left hand, the blank turned between cuts: the corners (4.9-6.0, octagon), rounding (6.0-7.4), the neck (7.4-8.1), turned end for end, the tenon (8.35-8.95); the knife back (8.95-9.3, the saw at the hip again), the knob turned before the eyes, the chips blown off (9.3), set down on its side (9.45-9.75), the left hand back on the strip. events: the times WorkbenchWork plays sounds and chips at (positions: model px from the carve anchor). docs/gameplay/furniture_recipes_v1.md 4.1',
  clip: 'tool.wooden_knob_carve',
  layer: 'action', mask: 'upper', priority: 55, first_person: 'body', held_item: 'hidden',
  loop: 'time', length: 10,
  blend: {in: 0.3, out: 0.25},
  approach: {anchor: 'stand', distance: 0, side: 0, max: 2.0, face: 'face', pitch: 40, in: 0.6},
  first_person_view: FP_VIEW,
  props: [
    {id: 'wooden_knob_blank', keys: BLANK_KEYS},
    ...STAGE_TRACKS,
    {id: 'hand_saw', keys: SAW_KEYS},
    {id: 'folding_knife', open_from: 0, keys: KNIFE_KEYS},
  ],
  hands: {
    right: [
      {at: 0, target: 'prop:hand_saw:grip', blend: 0, roll: 0},
      {at: 3.35, target: 'prop:wooden_knob_blank:pinch', blend: 0.35, roll: 0},
      {at: 4.1, target: 'prop:folding_knife:grip', blend: 0.3, roll: 0},
      {at: 9.3, target: 'prop:hand_saw:grip', blend: 0.15, roll: 0},
    ],
    left: [
      {at: 0, target: 'block:hold', blend: 0, pole: L_POLE, roll: 0},
      {at: 3.0, target: 'pose', blend: 0.4},
      {at: 3.8, target: 'prop:wooden_knob_blank:hold_foot', blend: 0.3, roll: 0},
      {at: FLIP, target: 'prop:wooden_knob_blank:hold_head', blend: FLIP_LEN, roll: 0},
      {at: DONE, target: 'prop:wooden_knob_blank:hold_foot', blend: 0.3, roll: 0},
      {at: 9.75, target: 'block:hold', blend: 0.25, pole: L_POLE, roll: 0},
    ],
  },
  events: EVENTS,
};
// the chest (tools/author-player-actions.mjs tool.wooden_knob_carve, the same numbers): over the bench and turned right
// for the saw (as the hand saw), down and square for the off-cut (the right shoulder ahead), straighter and higher for
// the whittling, up for the look
export const CHEST = {
  0: [28, 18, 0], 0.7: [28, 18, 0], 1.1: [33, 15, 0], 1.5: [28, 18, 0], 1.9: [33, 15, 0], 2.3: [28, 18, 0], 2.7: [33, 15, 0],
  3.1: [26, 14, 0], 3.5: [42, 0, 0], 3.75: [42, 0, 0], 4.2: [16, 6, 0], 6.0: [17, 6, 0], 7.4: [16, 5, 0], 8.95: [15, 5, 0], 9.25: [8, 2, 0],
  9.45: [10, 0, 0], 9.7: [22, -4, 0], 10: [28, 18, 0],
};

// ---------------------------------------------------------------- write / check
const json = o => JSON.stringify(o, null, 2).replace(/\[\s+([^\[\]{}]+?)\s+\]/g, (m, inner) => '[' + inner.split(/,\s*/).join(', ') + ']') + '\n';
const files = {[path.join(ASSETS, 'player_actions/tool/wooden_knob_carve.json')]: json(ACTION)};
for (const [id, p] of Object.entries(PROPS)) files[path.join(ASSETS, `player_props/${id}.json`)] = json(p);
// the chest clip, merged into the tool clips as tools/author-shovel-actions-a2.cjs does (not tools/author-player-actions.mjs:
// it writes the whole file from its own clips and would drop the shovel's and the pick's)
{
  const tool = read('animations/player/tool.animation.json');
  const keys = Object.fromEntries(Object.entries(CHEST).sort((a, b) => a[0] - b[0]).map(([t, v]) => [Number(t).toFixed(2), {post: v, lerp_mode: 'catmullrom'}]));
  tool.animations['tool.wooden_knob_carve'] = {loop: true, animation_length: 10, bones: {chest: {rotation: keys}}};
  const inline = s => s.replace(/\[\s*(-?[\d.e+-]+(?:,\s*-?[\d.e+-]+)*)\s*\]/g, (m, a) => '[' + a.split(/,\s*/).join(', ') + ']');
  files[path.join(ASSETS, 'animations/player/tool.animation.json')] = inline(JSON.stringify(tool, null, 1)) + '\n';
}
/** The file's own line ends (the clips file has CRLF). */
const eol = (s, f) => (fs.existsSync(f) && fs.readFileSync(f, 'utf8').includes('\r\n') ? s.replace(/\n/g, '\r\n') : s);

if (process.argv.includes('--check')) {
  let stale = 0;
  for (const [p, text] of Object.entries(files)) {
    if (!fs.existsSync(p) || fs.readFileSync(p, 'utf8').replace(/\r\n/g, '\n') !== text) { console.error('STALE ' + path.relative(ROOT, p)); stale++; }
  }
  if (stale) process.exit(1);
  console.log('CHECK OK');
} else if (!process.argv.includes('--report')) {
  for (const [p, text] of Object.entries(files)) fs.writeFileSync(p, eol(text, p));
  console.log('wrote ' + Object.keys(files).length + ' files');
}

// ---------------------------------------------------------------- report: the plugin's poser (= the game's), the stood body
if (process.argv.includes('--report')) {
  const src = fs.readFileSync(path.join(ROOT, 'tools/blockbench/afl_rig.js'), 'utf8')
      .replace('anchors: () => null, prop: null', 'anchors: o.anchors || (() => null), prop: null');
  const mod = {exports: {}};
  new Function('module', 'exports', 'require', src)(mod, mod.exports, createRequire(import.meta.url));
  const core = mod.exports;
  const rig = core.loadRig(read('rigs/afl_humanoid_v1.json'));
  const poser = new core.Poser(rig, core.loadClips(rig, read('animations/player/locomotion.animation.json')));
  const rot = Object.fromEntries(Object.entries(CHEST).sort((a, b) => a[0] - b[0]).map(([t, v]) => [Number(t).toFixed(2), {post: v, lerp_mode: 'catmullrom'}]));
  const clips = core.loadClips(rig, {format_version: '1.8.0', animations: {'tool.wooden_knob_carve': {loop: true, animation_length: 10, bones: {chest: {rotation: rot}}}}});
  const {tf} = core;
  const act = core.actionFromJson(ACTION, id => defs[id], n => clips[n] || null);
  const boxes = [
    ['chest', rig.find('chest'), [-4, 0, -2], [4, 6, 2]],
    ['belly', rig.find('pelvis'), [-4, 6, -2], [4, 12, 2]],
    ['head', rig.find('head'), [-4, -8, -4], [4, 0, 4]],
  ];
  const clearance = (q, lo, hi) => {
    const d = [0, 1, 2].map(i => Math.max(lo[i] - q[i], q[i] - hi[i]));
    const o = Math.hypot(...d.map(v => Math.max(0, v)));
    return o > 0 ? o : Math.max(...d);
  };
  const index = id => ACTION.props.findIndex(p => p.id === id);
  const IB = index('wooden_knob_blank'), IS = index('hand_saw'), IK = index('folding_knife');
  const benchY = q => (FEET - q[1]) / PX;
  const fmt = r => r && r.mode === 'ik' ? `off ${r.off.toFixed(2)} reach ${r.reach.toFixed(2)} elb ${r.elbow.toFixed(0)}` : (r ? r.mode : '-');
  const times = [0, 0.4, 0.7, 1.1, 1.5, 2.3, 2.7, 3.0, 3.5, 3.7, 4.1, 4.4, 4.9, 5.2, 5.4, 6.2, 7.0, 7.6, 8.2, 8.5, 8.8, 9.1, 9.3, 9.6, 9.8, 9.99];
  for (const t of times) {
    const anchors = anchorsAt(t);
    const res = core.solveFrame(poser, {pitch: viewAt(t), action: act, seconds: t, fp: true, anchors: n => anchors[n] || null});
    const ro = core.readouts(res);
    let line = `t${t.toFixed(2)}: R ${fmt(ro.right)} | L ${fmt(ro.left)}`;
    const shownAt = i => res.props[i] && !(res.propHidden && res.propHidden[i]);
    if (shownAt(IS)) {
      const toe = tf.apply(res.props[IS], local(SAW, 'toe'));
      line += ` | toe ${benchY(toe).toFixed(3)} high`;
    }
    if (res.props[IK] && t > 4.3 && t < 9) {
      const cut = tf.apply(res.props[IK], local(KNIFE, KNIFE_CUT));
      const blank = res.props[IB], c = tf.apply(blank, local(PROPS.wooden_knob_blank, 'center'));
      const foot = tf.apply(blank, local(PROPS.wooden_knob_blank, 'foot')), head = tf.apply(blank, local(PROPS.wooden_knob_blank, 'head'));
      const ax = norm(sub(head, foot)), d = sub(cut, c), radial = Math.hypot(...sub(d, mul(ax, dot(d, ax))));
      let worst = 99;
      for (const n of ['grip', 'tip', 'pivot', 'butt']) for (const [, bone, lo, hi] of boxes)
        worst = Math.min(worst, clearance(tf.apply(tf.inv(res.pose.world[bone]), tf.apply(res.props[IK], local(KNIFE, n))), lo, hi));
      line += ` | edge ${radial.toFixed(2)} px off the axis, ${dot(d, ax).toFixed(2)} along | knife clear ${worst.toFixed(2)}`;
    }
    if (res.props[IB] && t > 2.6) {
      const c = tf.apply(res.props[IB], local(PROPS.wooden_knob_blank, 'center'));
      line += ` | blank ${benchY(c).toFixed(3)} high`;
    }
    // the own first-person view (SkeletonActions#camera: ActionPose.cameraEye, the root's facing, the approach's pitch):
    // where the hands and the work are on the screen (deg off the centre, up/left +; the view is 70 deg high: within -30 clear
    // of the hotbar)
    {
      const {q4} = core, pose = res.pose, head = rig.head;
      const eye = add(q4.apply(pose.rotation(rig.root), sub(rig.sockets.eye.pos, rig.pivot[head])), pose.point(head, rig.pivot[head]));
      const f0 = q4.apply(pose.rotation(rig.root), [0, 0, -1]), f = norm([f0[0], 0, f0[2]]);
      const s = Math.sin(viewAt(t) * RAD), c = Math.cos(viewAt(t) * RAD);
      const fw = [f[0] * c, s, f[2] * c], up = [f[0] * s, -c, f[2] * s], lt = [-f[2], 0, f[0]];
      const on = q => { const d = sub(q, eye), z = dot(d, fw); return `${(Math.atan2(dot(d, up), z) * DEG).toFixed(0)}/${(Math.atan2(dot(d, lt), z) * DEG).toFixed(0)}`; };
      line += ` | fp ${viewAt(t).toFixed(0)} eye ${benchY(eye).toFixed(2)} hi, R ${on(pose.point(rig.handR, rig.pivot[rig.handR]))} L ${on(pose.point(rig.handL, rig.pivot[rig.handL]))}`;
      if (res.props[IB] && t > 2.6) line += ` blank ${on(tf.apply(res.props[IB], local(PROPS.wooden_knob_blank, 'center')))}`;
      line += ` kerf ${on(anchors.kerf)}`;
    }
    console.log(line);
  }
  console.log('stand', r4(STAND).join(', '), ' carve', r4(CARVE).join(', '), ' offcut', r4(OFFCUT).join(', '), ' stick z', STICK_Z.toFixed(4),
      ' top', STICK_TOP.toFixed(4), ' kerf end', KERF_END.toFixed(3), ' set y', SET_Y.toFixed(4), ' cm', CM.toFixed(5), ' toe', TOE.toFixed(3));
}
