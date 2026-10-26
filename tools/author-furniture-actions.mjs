// Furniture work at the workbench (2026-10-22, docs/gameplay/furniture_bench_work_v1.md 3): writes the props
// player_props/{claw_hammer,screwdriver,furniture_*}.json, the actions player_actions/furniture/*.json (one per step and per
// beat of the nail's and screw's timing bars) and the chest clips tool.furniture_crosscut and tool.furniture_lean (merged
// into animations/player/tool.animation.json). No arm clips: the props' placements and the hands' targets, solved by the IK.
//
// The work (client/WorkbenchRenderer draws it, client/workbench/WorkbenchWork computes the anchors; both use the numbers of
// CUT and ASM below, in the bench's north frame: the player at z < 0 facing +z, +x the player's left, the top at y 0.9):
//   crosscut   the board lies along x over the bench's right end; each time it is slid 0.28 right past the cut plane x 0
//              (the left hand pushing it, 0.1-0.6 s), sawn through across its width (6 strokes, 0.9-5.7 s), the off-cut
//              carried to the pile at the back right (6.1-6.4 s); the stance as the wooden knob's (tools/author-wooden-knob-actions.mjs);
//   assembly   every other step at the bench's middle (the stand 1.20, -0.16, facing +z): the piece on the top at x 0.75-1.65,
//              the nail box or screw box at its left back (the left hand picks the nails), the can at its right back.
// Nails and screws: a set or start action, a hold looped on the timing bar, then one beat per press (strike, glance, seat;
// turn, slip, seat); each beat starts and ends at the hold's pose (the seat at the rest before the next nail), so the plays
// chain without a blend (FurnitureWork.Plan). The nail's head (anchor head) and the screw's (shead) sink with the good blows
// (WorkbenchWork). Every sound, its noise for the infected and the sawdust is in the actions' events (WorkbenchWork plays them,
// cabin/WorkbenchCrafting reads the noise).
//   node tools/author-furniture-actions.mjs [--check] [--report]
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
const t3 = n => Math.round(n * 1000) / 1000;
const RAD = Math.PI / 180, DEG = 180 / Math.PI;
const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
const add = (a, b) => a.map((v, i) => v + b[i]), sub = (a, b) => a.map((v, i) => v - b[i]), mul = (a, k) => a.map(v => v * k);
const dot = (a, b) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
const cross = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
const norm = a => mul(a, 1 / Math.hypot(...a));
const perp = (v, axis) => norm(sub(v, mul(axis, dot(v, axis))));
const around = (axis, ref, deg) => add(mul(ref, Math.cos(deg * RAD)), mul(cross(axis, ref), Math.sin(deg * RAD)));

// ---------------------------------------------------------------- the frames (blocks, the bench's north frame)
/** Model px per block (PlayerRenderer scales the model by 0.9375); the feet at model y 24.016. */
const PX = 16 / 0.9375, FEET = 1.501 * 16;
/** A body stood at (x, z) turned yaw deg: bench points and offsets as its model px (the wooden knob script's model()). */
const stance = (sx, sz, yaw) => {
  const YS = Math.sin(yaw * RAD), YC = Math.cos(yaw * RAD);
  const off = v => [(v[0] * YC - v[2] * YS) * PX, -v[1] * PX, -(v[0] * YS + v[2] * YC) * PX];
  return {sx, sz, yaw, YS, YC, off, dir: v => norm(off(v)), at: (x, y, z) => add(off([x - sx, 0, z - sz]), [0, FEET - y * PX, 0])};
};
/** The crosscut (WorkbenchWork CUT_*): the cut plane, the board, the stance (as the wooden knob's), the pile. */
export const CUT = {x: 0.0, z0: 0.06, z1: 0.26, top: 0.925, thick: 0.025, piece: 0.28, standX: 0.36, standZ: -0.18, yaw: -15,
  holdX: 0.13, holdY: 0.955, pileX: 0.24, pileZ: 0.37};
/** The assembly (WorkbenchWork ASM_*): the stand, the can, the boxes, the lid's, stick's and rag's rests. */
export const ASM = {standX: 1.20, standZ: -0.08, can: [0.60, 0.9, 0.30], box: [1.76, 0.9, 0.14], lid: [0.52, 0.905, 0.13],
  stick: [0.45, 0.905, 0.30], rag: [0.66, 0.9, 0.12]};
const CS = stance(CUT.standX, CUT.standZ, CUT.yaw), AS = stance(ASM.standX, ASM.standZ, 0);
/** The assembly's stand follows the work along the bench (WorkbenchWork#standX): the nail or screw a little right of the body's middle. */
export const STAND_RIGHT = 0.18;
const standFor = x => clamp(x + STAND_RIGHT, 0.6, 1.62);
/** ... and steps back from the bench for the near ones (a near nail under the shoulder folds the arm): 0.42 behind the work, at most at the front. */
export const STAND_BACK = 0.36;
const standZFor = z => Math.min(ASM.standZ, z - STAND_BACK);
/** The can's actions stand at the can; the wipes at WIPE.standX, the strokes from x a to b (the right hand's reach: the rag passes the
 *  piece's right part, the bench paints the whole strip). */
export const CAN_STAND = 0.62;
export const WIPE = {standX: 1.10, a: 0.80, b: 1.32, y: 0.015};
/** The left hand presses the piece by the work: to its left and nearer the body, within reach (WorkbenchWork#press). */
export const PRESS_LEFT = 0.25, PRESS_BACK = 0.10, PRESS_REACH = 0.36;
/** Real size: one mesh unit (0.508 m) as drawn by a 0.508 prop (the model's 0.9375). */
const K = 0.508 * 0.9375;
const NAIL_LEN = 0.1 * K, SCREW_LEN = 0.063 * K, CAN_H = 0.238 * K;
const CAN_MOUTH = [ASM.can[0], ASM.can[1] + CAN_H, ASM.can[2]];

// ---------------------------------------------------------------- the props
const OUT = (px, scale) => px / (16 * scale);
const W = 0.508;
/** Small things are held in the fingertips: the palm (the IK's target) 3 px back toward the body, as mounted. */
const FINGERS = OUT(3, W);
const meshProp = (about, parts, scale, lo, hi, points) => ({
  about: about + ' (docs/gameplay/furniture_bench_work_v1.md; written by tools/author-furniture-actions.mjs): parts of the furniture_work mesh (code only, no item). Points, bounds: item block units of the mesh as drawn (meshes/furniture_work.aflmesh.json + (0.5, 0, 0.5)). Scale ' + scale + '.',
  items: [], scale, bounds: [r4(lo), r4(hi)], symmetry: 'x',
  mesh: {model: 'furniture_work', atlas: 'furniture_work_mesh', offset: 0, parts},
  points: Object.fromEntries(Object.entries(points).map(([n, p]) => [n, r4(p)])),
});
const itemProp = (about, item, offset, lo, hi, points, symmetry = 'z') => ({
  about: about + ' (docs/gameplay/furniture_bench_work_v1.md; written by tools/author-furniture-actions.mjs). Points, bounds: item block units of the ' + item + ' mesh as client/AflStaticMeshItemClient draws it (+ (0.5, ' + offset + ', 0.5)). Scale 0.508: real size.',
  items: [item], scale: W, bounds: [r4(add(lo, [0.5, offset, 0.5])), r4(add(hi, [0.5, offset, 0.5]))], symmetry,
  points: Object.fromEntries(Object.entries(points).map(([n, p]) => [n, r4(add(p, [0.5, offset, 0.5]))])),
});
const PROPS = {
  claw_hammer: itemProp('The claw hammer at the workbench: y along the handle to the head, the face to -x, the claw to +x. grip: the right hand; face: the striking face\'s middle; claw: the claw\'s tip', 'claw_hammer', 0.17375,
      [-0.138, 0, -0.029], [0.138, 0.652, 0.029], {grip: [-0.014, 0.15, 0], face: [-0.138, 0.622, 0], claw: [0.138, 0.569, 0]}),
  screwdriver: itemProp('The screwdriver at the workbench: y from the handle\'s butt to the blade\'s tip, the blade\'s width along x. grip: the right hand; tip; butt', 'screwdriver', 0.244094,
      [-0.028, 0, -0.028], [0.028, 0.512, 0.028], {grip: [0, 0.1, 0], tip: [0, 0.512, 0], butt: [0, 0, 0]}),
  furniture_hinge: itemProp('A cabinet hinge carried to its place: y along the pin, the leaves along x. base: the pin\'s end on the plates\' plane (the hardware\'s anchor hw); hold: the left palm over the plates (the fingertips on it)', 'cabinet_hinge', 0.420276,
      [-0.063, 0, -0.002], [0.063, 0.159, 0.002], {base: [0, 0, 0], hold: [-FINGERS, 0.08, OUT(1.5, W)]}),
  furniture_knob: itemProp('An iron knob carried to its place: y along its axis from the foot. base: the foot (the anchor hw); hold: the left palm on its top (the fingertips on it)', 'iron_knob', 0.447835,
      [-0.031, 0, -0.031], [0.031, 0.104, 0.031], {base: [0, 0, 0], hold: [0, 0.104 + OUT(1.5, W), -FINGERS]}, 'x'),
  furniture_knob_wooden: itemProp('A wooden knob carried to its place: y along its axis from the tenon\'s end. base: the foot (the anchor hw); hold: the left palm on its top (the fingertips on it)', 'wooden_knob', 0.459646,
      [-0.028, 0, -0.028], [0.028, 0.081, 0.028], {base: [0, 0, 0], hold: [0, 0.081 + OUT(1.5, W), -FINGERS]}, 'x'),
  furniture_escutcheon: itemProp('A keyhole escutcheon carried to its place: y along the plate from its foot, the plate\'s face +z. base: the foot (the anchor hw); hold: the left palm on its face (the fingertips on it)', 'keyhole_escutcheon', 0.455709,
      [-0.028, 0, -0.001], [0.028, 0.089, 0.001], {base: [0, 0, 0], hold: [0, 0.045 - FINGERS, OUT(1.5, W)]}),
  furniture_nail: meshProp('A 6d nail in the left fingers: y from the tip to the head. tip, head; pinch: the left palm, the fingertips holding it by its middle from the body side', ['nail'], W,
      [0.494, 0, 0.494], [0.506, 0.1, 0.506], {tip: [0.5, 0, 0.5], head: [0.5, 0.1, 0.5], pinch: [0.5, 0.06, 0.5 - FINGERS]}),
  furniture_screw: meshProp('A wood screw in the left fingers: y from the tip to the head, the slot along x. tip, head; pinch: the left palm, the fingertips holding it by its middle from the body side', ['screw', 'screw_slot'], W,
      [0.492, 0, 0.492], [0.508, 0.063, 0.508], {tip: [0.5, 0, 0.5], head: [0.5, 0.063, 0.5], pinch: [0.5, 0.04, 0.5 - FINGERS]}),
  furniture_rag: meshProp('The staining rag, a folded pad: y up from its face. center: the face\'s middle (on the wood); hold: the right palm on its back', ['rag'], 0.7,
      [0.393, 0, 0.421], [0.607, 0.029, 0.579], {center: [0.5, 0, 0.5], hold: [0.5, 0.029 + OUT(1.5, 0.7), 0.5]}),
  furniture_stick: meshProp('The stirring stick (an off-cut strip), its wet end in the stain: y from the wet end up. end; center; grip: the right hand near the top', ['stick', 'stick_wet'], W,
      [0.476, 0, 0.488], [0.524, 0.512, 0.512], {end: [0.5, 0, 0.5], center: [0.5, 0.256, 0.5], grip: [0.5, 0.44, 0.5 + OUT(1.5, W)]}),
  furniture_lid: meshProp('The can\'s lid: y up from its seat. center: the seat\'s middle; hold: the left palm on its top', ['lid'], W,
      [0.399, 0, 0.399], [0.601, 0.014, 0.601], {center: [0.5, 0, 0.5], hold: [0.5, 0.014 + OUT(1.5, W), 0.5]}),
};
const SAW = read('player_props/hand_saw.json');
const defs = {...PROPS, hand_saw: SAW};
const local = (prop, p) => { const k = 16 * prop.scale, q = typeof p === 'string' ? prop.points[p] : p; return [q[0] * k, -q[1] * k, -q[2] * k]; };

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
// item +y is local -y, item +x local +x, item +z local -z (local() above)
const upTurn = (h, b) => r3(turnFor([0, -1, 0], [1, 0, 0], h, b));           // item y to h, item x toward b
const hammerTurn = (h, b) => r3(turnFor([0, -1, 0], [-1, 0, 0], h, b));      // up the handle h, the face toward b
const UP = AS.dir([0, 1, 0]);

// ---------------------------------------------------------------- the hammer (prop 'claw_hammer', anchor head: the nail's head)
const H = PROPS.claw_hammer;
const FACE_TO_GRIP = sub(local(H, 'grip'), local(H, 'face'));
const STRIKE_ROT = hammerTurn(AS.dir([0.15, -0.2, 1]), AS.dir([0, -1, 0]));
const STRIKE = r3(turnApply(STRIKE_ROT, FACE_TO_GRIP));                     // the grip with the face on the head
const READY_ROT = hammerTurn(AS.dir([0.1, 0.85, 0.55]), AS.dir([0, -0.55, 0.85]));
const READY = r3(add(STRIKE, AS.off([0, 0.05, -0.03])));                    // the wrist cocked over the nail
const hammerAt = (at, pos, rot, blend, extra = {}) => ({at: t3(at), frame: 'anchor:head', point: 'grip', pos: r2(pos), rot, blend: t3(blend), ...extra});
const ready = (at, blend = 0, extra) => hammerAt(at, READY, READY_ROT, blend, extra);
const strike = (at, blend, extra) => hammerAt(at, STRIKE, STRIKE_ROT, blend, {ease: 'in', ...extra});
const GLANCE = add(STRIKE, AS.off([-0.012, 0.01, 0.016]));
const HAMMER_REST = {frame: 'chest', point: 'grip', pos: [-4.5, 8, -8], rot: hammerTurn([0.1, -0.6, -1], [0, 1, -0.4])};
const HAMMER_HIP = {frame: 'chest', point: 'grip', pos: [-4.4, 8.8, -2.8], rot: hammerTurn([0.1, 1, 0.2], [0, 0, -1])};
const rest = (spot, at, blend = 0, extra = {}) => ({at: t3(at), ...spot, blend: t3(blend), ...extra});

// ---------------------------------------------------------------- the screwdriver (prop 'screwdriver', anchor shead: the screw's head)
const DRIVER_H = AS.dir([0.06, -1, 0.1]);                                 // butt to tip: down, its top toward the body
const driverTurn = (h, b) => r3(turnFor([0, -1, 0], [1, 0, 0], h, b));
const driver = (at, deg, blend, lift = 0, side = [0, 0, 0], extra = {}) => ({at: t3(at), frame: 'anchor:shead', point: 'tip',
  pos: r2(add(AS.off([0, lift, 0]), AS.off(side))), rot: driverTurn(DRIVER_H, around(DRIVER_H, perp(AS.dir([1, 0, 0]), DRIVER_H), deg)), blend: t3(blend), ...extra});
const DRIVER_REST = {frame: 'chest', point: 'grip', pos: [-4.5, 7, -8], rot: driverTurn([0.1, 0.6, -1], [1, 0, 0])};
const DRIVER_HIP = {frame: 'chest', point: 'grip', pos: [-4.4, 8.8, -2.8], rot: driverTurn([0.1, 1, 0.3], [1, 0, 0])};

// ---------------------------------------------------------------- the hardware, the can's things (bench directions)
// mounted (WorkbenchRenderer the same): the hinge flat on the back rail, its pin along x; the knob upright; the escutcheon flat,
// its foot toward the body
const HW_ROT = {
  furniture_hinge: upTurn(AS.dir([1, 0, 0]), AS.dir([0, 0, 1])),
  furniture_knob: upTurn(UP, AS.dir([1, 0, 0])),
  furniture_knob_wooden: upTurn(UP, AS.dir([1, 0, 0])),
  furniture_escutcheon: upTurn(AS.dir([0, 0, 1]), AS.dir([-1, 0, 0])),
};
const LID_UP = upTurn(UP, AS.dir([1, 0, 0])), LID_DOWN = upTurn(AS.dir([0, -1, 0]), AS.dir([1, 0, 0]));
export const STICK_LEAN_DIR = [0.55, 1, 0.1];                               // the stick in the can, leaning on the rim's left
const STICK_LIE = upTurn(AS.dir([0, 0, 1]), AS.dir([0, 1, 0]));      // lying along z right of the can
const STICK_UP = upTurn(AS.dir([0.1, 1, -0.25]), AS.dir([1, 0, 0]));
const STICK_LEAN = upTurn(AS.dir(STICK_LEAN_DIR), AS.dir([0, 0, 1]));
const NAIL_UP = upTurn(UP, AS.dir([1, 0, 0]));
const RAG_HAND = {frame: 'chest', point: 'center', pos: [-4.5, 7.5, -7], rot: [0, 0, 0]};
const L_POLE = [0.8, 1.0, 0.2];
const key = (at, frame, point, pos, rot, blend, extra = {}) => ({at: t3(at), frame, point, pos: r2(pos), rot, blend: t3(blend), ...extra});
const hand = (at, target, blend, extra = {}) => ({at: t3(at), target, blend: t3(blend), ...(target === 'pose' ? {} : {roll: 0}), ...extra});
const ev = (at, sound, radius, o = {}) => ({at: t3(at), sound, radius, ...o});

// ---------------------------------------------------------------- the actions
const LEAN = 'tool.furniture_lean', SAWING = 'tool.furniture_crosscut';
const VIEW = 58;
const base = (about, length, loop, extra = {}) => ({
  format_version: 1,
  about: about + ' (client/workbench/WorkbenchWork plays it, docs/gameplay/furniture_bench_work_v1.md 3; written by tools/author-furniture-actions.mjs)',
  clip: LEAN, layer: 'action', mask: 'upper', priority: 55, first_person: 'body', held_item: 'hidden',
  ...(loop ? {loop: 'time'} : {}), length,
  blend: {in: 0.3, out: 0.25},
  approach: {anchor: 'stand', distance: 0, side: 0, max: 2.0, face: 'face', pitch: 40, in: 0.6},
  first_person_view: [[0, VIEW], [length, VIEW]],
  ...extra,
});
const ACTIONS = {};

// --- the nail (hammer in the right hand from the nail's set to its seat, the left pressing the piece by the nail)
ACTIONS.nail_set = base('Set a nail: the left hand takes one from the box (0.3 s), stands it on its place (0.85), the hammer comes from the rest to over it (0.45-0.8) and taps it once to stand (0.9), the left hand back to press the piece by it (0.95-1.2); ends at the hold\'s pose', 1.2, false, {
  props: [
    {id: 'claw_hammer', keys: [rest(HAMMER_REST, 0), ready(0.45, 0.35), strike(0.8, 0.1), ready(0.9, 0.3)]},
    {id: 'furniture_nail', keys: [
      key(0, 'anchor:box', 'pinch', [0, 0, 0], NAIL_UP, 0, {hidden: true}),
      key(0.3, 'anchor:box', 'pinch', [0, 0, 0], NAIL_UP, 0),
      key(0.35, 'anchor:head', 'head', AS.off([0, 0.15 * NAIL_LEN, 0]), NAIL_UP, 0.5),
      key(0.88, 'anchor:head', 'head', [0, 0, 0], NAIL_UP, 0.04, {ease: 'in'}),
      key(0.95, 'anchor:head', 'head', [0, 0, 0], NAIL_UP, 0, {hidden: true}),
    ]},
  ],
  hands: {
    right: [hand(0, 'prop:claw_hammer:grip', 0)],
    left: [hand(0, 'pose', 0), hand(0.02, 'block:box', 0.28, {pole: L_POLE}), hand(0.3, 'prop:furniture_nail:pinch', 0.05, {pole: L_POLE}),
      hand(0.95, 'block:press', 0.25, {pole: L_POLE})],
  },
  events: [ev(0.3, 'furniture_nail_pick', 2, {volume: 0.6}), ev(0.9, 'furniture_nail_tap', 4, {volume: 0.35, pitch: 1.2, noise: 4})],
});
const holdHands = (right) => ({right: [hand(0, right, 0)], left: [hand(0, 'block:press', 0, {pole: L_POLE})]});
ACTIONS.nail_hold = base('The hammer cocked over the set nail, the left hand pressing the piece: looped while the timing bar runs (a press plays a strike, a glance or the seat)', 1, true, {
  props: [{id: 'claw_hammer', keys: [ready(0), ready(0.05, 0.45, {pos: r2(add(READY, AS.off([0, 0.008, 0])))}), ready(0.5, 0.45)]}],
  hands: holdHands('prop:claw_hammer:grip'),
});
ACTIONS.nail = {...ACTIONS.nail_hold, about: 'The nail step\'s own file (the recipe names it; FurnitureWork plays nail_set, nail_hold and the blows): a copy of nail_hold (written by tools/author-furniture-actions.mjs)'};
ACTIONS.nail_strike = base('A good blow: from the hold\'s pose down onto the head (0.05-0.2, the nail goes in a share), back up to it (0.2-0.35)', 0.35, false, {
  props: [{id: 'claw_hammer', keys: [ready(0), strike(0.05, 0.15), ready(0.2, 0.15)]}],
  hands: holdHands('prop:claw_hammer:grip'),
  events: [ev(0.2, 'furniture_nail_strike', 10, {noise: 10})],
});
ACTIONS.nail_glance = base('A miss: the face glances off the head\'s edge (0.2), the nail does not move; back to the hold\'s pose', 0.35, false, {
  props: [{id: 'claw_hammer', keys: [ready(0), hammerAt(0.05, GLANCE, STRIKE_ROT, 0.15, {ease: 'in'}), ready(0.2, 0.15)]}],
  hands: holdHands('prop:claw_hammer:grip'),
  events: [ev(0.2, 'furniture_nail_glance', 4, {noise: 4})],
});
ACTIONS.nail_seat = base('The last blow: the head driven flush (0.2), the face rests on it, the hammer back to the rest (0.3-0.55) and the left hand off the piece', 0.55, false, {
  props: [{id: 'claw_hammer', keys: [ready(0), strike(0.05, 0.15), strike(0.2, 0.1, {ease: 'smooth'}), rest(HAMMER_REST, 0.3, 0.25)]}],
  hands: {right: [hand(0, 'prop:claw_hammer:grip', 0)], left: [hand(0, 'block:press', 0, {pole: L_POLE}), hand(0.3, 'pose', 0.25)]},
  events: [ev(0.2, 'furniture_nail_seat', 10, {noise: 10})],
});

// --- the screw (the screwdriver in the right hand, upright on the screw; the left pressing the hardware)
const fit = (id, what) => base('Fit ' + what + ': the left hand takes it from beside the screw box (0.25 s) and lays it on its place (0.3-0.85; drawn by the bench from then), the screwdriver from the right hip to the rest (0.15-0.65), the left hand presses it (0.85-1.0)', 1, false, {
  props: [
    {id: 'screwdriver', keys: [rest(DRIVER_HIP, 0, 0, {hidden: true}), rest(DRIVER_HIP, 0.15), rest(DRIVER_REST, 0.2, 0.45)]},
    {id, keys: [
      key(0, 'anchor:box', 'base', [0, 0, 0], HW_ROT[id], 0, {hidden: true}),
      key(0.25, 'anchor:box', 'base', [0, 0, 0], HW_ROT[id], 0),
      key(0.3, 'anchor:hw', 'base', [0, 0, 0], HW_ROT[id], 0.55),
      key(0.85, 'anchor:hw', 'base', [0, 0, 0], HW_ROT[id], 0, {hidden: true}),
    ]},
  ],
  hands: {
    right: [hand(0, 'prop:screwdriver:grip', 0)],
    left: [hand(0, 'pose', 0), hand(0.02, 'block:box', 0.25, {pole: L_POLE}), hand(0.27, 'prop:' + id + ':hold', 0.05, {pole: L_POLE}),
      hand(0.85, 'block:press', 0.15, {pole: L_POLE})],
  },
  events: [ev(0.85, 'workbench_tool_down', 2, {volume: 0.3, pitch: 1.7})],
});
ACTIONS.fit_hinge = fit('furniture_hinge', 'a hinge');
ACTIONS.fit_knob = fit('furniture_knob', 'the knob');
ACTIONS.fit_knob_wooden = fit('furniture_knob_wooden', 'the wooden knob (WorkbenchWork plays it for fit_knob when the job\'s knob is wooden; the same length and events)');
ACTIONS.fit_escutcheon = fit('furniture_escutcheon', 'the escutcheon');
const ENGAGED = -40;
ACTIONS.screw_start = base('Start a screw: the left hand puts one from its fingers into the hole (0-0.35), the screwdriver from the rest into its slot (0.1-0.55; the bench draws the screw from 0.55), the left hand back on the hardware', 0.6, false, {
  props: [
    {id: 'screwdriver', keys: [rest(DRIVER_REST, 0), driver(0.1, ENGAGED, 0.3, 0.03), driver(0.4, ENGAGED, 0.15, 0, [0, 0, 0], {ease: 'in'})]},
    {id: 'furniture_screw', keys: [
      key(0, 'anchor:press', 'pinch', [0, 0, 0], NAIL_UP, 0, {hidden: true}),
      key(0.02, 'anchor:press', 'pinch', [0, 0, 0], NAIL_UP, 0),
      key(0.05, 'anchor:shead', 'head', [0, 0, 0], NAIL_UP, 0.3),
      key(0.55, 'anchor:shead', 'head', [0, 0, 0], NAIL_UP, 0, {hidden: true}),
    ]},
  ],
  hands: {right: [hand(0, 'prop:screwdriver:grip', 0)],
    left: [hand(0, 'block:press', 0, {pole: L_POLE}), hand(0.02, 'prop:furniture_screw:pinch', 0.03, {pole: L_POLE}), hand(0.4, 'block:press', 0.2, {pole: L_POLE})]},
  events: [ev(0.05, 'furniture_screw_pick', 2, {pitch: 0.9})],
});
ACTIONS.screw_hold = base('The screwdriver in the slot, the left hand on the hardware: looped while the timing bar runs (a press plays a turn, a slip or the seat)', 1, true, {
  props: [{id: 'screwdriver', keys: [driver(0, ENGAGED, 0), driver(0.05, ENGAGED + 4, 0.45, -0.002), driver(0.5, ENGAGED, 0.45)]}],
  hands: holdHands('prop:screwdriver:grip'),
});
ACTIONS.screw_turn = base('A good turn: the wrist turns the driver a quarter (0.05-0.3, the screw goes in a share), eases off and takes a new grip (0.3-0.5)', 0.5, false, {
  props: [{id: 'screwdriver', keys: [driver(0, ENGAGED, 0), driver(0.05, 40, 0.25, -0.002), driver(0.3, 40, 0.05, 0.004), driver(0.35, ENGAGED, 0.1, 0.004), driver(0.45, ENGAGED, 0.05)]}],
  hands: holdHands('prop:screwdriver:grip'),
  events: [ev(0.05, 'furniture_screw_turn', 2, {noise: 2})],
});
ACTIONS.screw_slip = base('A miss: the blade slips out of the slot to the side (0.1), the screw does not move; set back into it', 0.5, false, {
  props: [{id: 'screwdriver', keys: [driver(0, ENGAGED, 0), driver(0.05, 15, 0.12, 0.004, [0.008, 0, 0.005], {ease: 'in'}), driver(0.2, ENGAGED, 0.15, 0.012), driver(0.35, ENGAGED, 0.15)]}],
  hands: holdHands('prop:screwdriver:grip'),
  events: [ev(0.1, 'furniture_screw_slip', 2, {noise: 2})],
});
ACTIONS.screw_seat = base('The last turn: the wrist presses and turns the head flush (0.05-0.3), stops; the blade out of the slot (0.4-0.55), the driver back to the rest (0.55-0.8)', 0.8, false, {
  props: [{id: 'screwdriver', keys: [driver(0, ENGAGED, 0), driver(0.05, 50, 0.25, -0.003), driver(0.3, 50, 0.1, -0.003), driver(0.4, 50, 0.15, 0.03), rest(DRIVER_REST, 0.55, 0.25)]}],
  hands: holdHands('prop:screwdriver:grip'),
  events: [ev(0.05, 'furniture_screw_seat', 2, {noise: 2})],
});

// --- the can, the stain (the can at the piece's right back; anchors can: its mouth, canhold: the left hand steadying it)
const RIM = 0.101 * K;
const PRY = [[0, -1], [-0.87, -0.5], [0.87, -0.5]].map(([x, z]) => [x, 0, z]);
const pryKeys = [];
PRY.forEach((d, i) => {
  const t0 = 0.15 + 0.75 * i;
  const claw = AS.off(add(mul(d, RIM + 0.002), [0, -0.002, 0]));
  const inRot = hammerTurn(AS.dir(add(mul(d, -0.5), [0, -0.85, 0])), AS.dir(d));
  const lever = hammerTurn(AS.dir(add(mul(d, -0.9), [0, -0.45, 0])), AS.dir(add(d, [0, 0.4, 0])));
  pryKeys.push(key(t0, 'anchor:can', 'claw', claw, inRot, 0.3), key(t0 + 0.3, 'anchor:can', 'claw', add(claw, AS.off([0, 0.004, 0])), lever, 0.3),
      key(t0 + 0.6, 'anchor:can', 'claw', add(claw, AS.off(add(mul(d, 0.02), [0, 0.02, 0]))), inRot, 0.15));
});
const stir = [];
for (let i = 0; i < 8; i++) {
  const a = i * 90 * RAD;
  stir.push(key(4.15 + 0.225 * i, 'anchor:can', 'end', AS.off([Math.cos(a) * 0.022, -0.07, Math.sin(a) * 0.022]), STICK_UP, 0.225));
}
ACTIONS.can_open = base('Open the can, once a batch before the first wipe: the claw pries the lid at three places (0.15-2.4, the left hand steadying the can), the left hand lifts the lid off and lays it upside down (2.4-3.1), the hammer to the hip (2.4-3.4), the right hand takes the stirring stick (3.4-3.7) and stirs slowly (4.0-5.95), leans it in the can (6.0-6.2) and picks the rag up (6.2-6.5)', 6.5, false, {
  props: [
    {id: 'claw_hammer', keys: [rest(HAMMER_REST, 0), ...pryKeys, rest(HAMMER_REST, 2.4, 0.35), rest(HAMMER_HIP, 3.1, 0.3), rest(HAMMER_HIP, 3.4, 0, {hidden: true})]},
    {id: 'furniture_lid', keys: [key(0, 'anchor:can', 'center', [0, 0, 0], LID_UP, 0, {hidden: true}), key(2.4, 'anchor:can', 'center', [0, 0, 0], LID_UP, 0),
      key(2.45, 'anchor:can', 'center', AS.off([0, 0.05, 0]), LID_UP, 0.25), key(2.7, 'anchor:lid', 'center', [0, 0, 0], LID_DOWN, 0.4),
      key(3.1, 'anchor:lid', 'center', [0, 0, 0], LID_DOWN, 0, {hidden: true})]},
    {id: 'furniture_stick', keys: [key(0, 'anchor:stick_bench', 'center', [0, 0, 0], STICK_LIE, 0, {hidden: true}), key(3.45, 'anchor:stick_bench', 'center', [0, 0, 0], STICK_LIE, 0),
      key(3.7, 'anchor:can', 'end', AS.off([0, 0.06, 0]), STICK_UP, 0.3), key(4.0, 'anchor:can', 'end', AS.off([0.022, -0.07, 0]), STICK_UP, 0.15), ...stir,
      key(6.0, 'anchor:stick_rest', 'end', [0, 0, 0], STICK_LEAN, 0.2), key(6.2, 'anchor:stick_rest', 'end', [0, 0, 0], STICK_LEAN, 0, {hidden: true})]},
    {id: 'furniture_rag', keys: [key(0, 'anchor:rag_rest', 'center', [0, 0, 0], [0, 0, 0], 0, {hidden: true}), key(6.2, 'anchor:rag_rest', 'center', [0, 0, 0], [0, 0, 0], 0),
      rest(RAG_HAND, 6.35, 0.15)]},
  ],
  hands: {
    right: [hand(0, 'prop:claw_hammer:grip', 0), hand(3.4, 'prop:furniture_stick:grip', 0.3), hand(6.2, 'prop:furniture_rag:hold', 0.15)],
    left: [hand(0, 'pose', 0), hand(0.05, 'block:canhold', 0.3, {pole: L_POLE}), hand(2.3, 'prop:furniture_lid:hold', 0.1, {pole: L_POLE}),
      hand(3.1, 'block:canhold', 0.3, {pole: L_POLE}), hand(6.2, 'block:press', 0.3, {pole: L_POLE})],
  },
  events: [ev(0.05, 'furniture_can_open', 4, {noise: 4}), ev(4.0, 'furniture_stain_stir', 2, {noise: 2})],
});
const ragAt = (at, anchor, blend, up = 0) => key(at, 'anchor:' + anchor, 'center', AS.off([0, up, 0]), [0, 0, 0], blend);
ACTIONS.wipe = base('Wipe the stain on: the rag dipped (0.05-0.5), three strokes along the grain across the piece (0.8, 1.7, 2.6; anchors w0a..w2b, the strokes\' ends; the bench paints each strip as it is passed), the rag back before the body (3.2-4.0)', 4, false, {
  props: [{id: 'furniture_rag', keys: [rest(RAG_HAND, 0), ragAt(0.05, 'can', 0.25, 0.02), ragAt(0.3, 'can', 0.15, -0.025), ragAt(0.45, 'can', 0.1, 0.03),
    ragAt(0.55, 'w0a', 0.25), ragAt(0.8, 'w0b', 0.6), ragAt(1.4, 'w1a', 0.3), ragAt(1.7, 'w1b', 0.6), ragAt(2.3, 'w2a', 0.3), ragAt(2.6, 'w2b', 0.6),
    rest(RAG_HAND, 3.2, 0.8)]}],
  hands: {right: [hand(0, 'prop:furniture_rag:hold', 0)], left: [hand(0, 'block:press', 0, {pole: L_POLE})]},
  events: [ev(0.3, 'furniture_rag_dip', 2, {noise: 2}), ev(0.8, 'furniture_rag_wipe', 2, {noise: 2}), ev(1.7, 'furniture_rag_wipe', 2, {noise: 2}),
    ev(2.6, 'furniture_rag_wipe', 2, {noise: 2})],
});
ACTIONS.wipe_off = base('Wipe the stain off, once a piece: the rag folded, two passes along the piece (0.3, 1.2; anchors o0a..o1b), laid down on its rest (1.9-2.25), the hands off', 2.5, false, {
  props: [{id: 'furniture_rag', keys: [rest(RAG_HAND, 0), ragAt(0.05, 'o0a', 0.25), ragAt(0.3, 'o0b', 0.7), ragAt(1.0, 'o1a', 0.2), ragAt(1.2, 'o1b', 0.7),
    ragAt(1.9, 'rag_rest', 0.35), {...ragAt(2.25, 'rag_rest', 0), hidden: true}]}],
  hands: {right: [hand(0, 'prop:furniture_rag:hold', 0), hand(2.25, 'pose', 0.25)], left: [hand(0, 'block:press', 0, {pole: L_POLE}), hand(2.0, 'pose', 0.4)]},
  events: [ev(0.3, 'furniture_rag_wipe', 2, {volume: 0.6, noise: 2}), ev(1.2, 'furniture_rag_wipe', 2, {volume: 0.6, noise: 2})],
});
ACTIONS.can_close = base('Close the can at a batch\'s end (no sound, user 2026-10-22): the stick out and laid down (0-0.65), the lid from its rest turned over onto the can (0.6-1.3), the right palm presses it shut (1.3-1.6)', 2, false, {
  props: [
    {id: 'furniture_stick', keys: [key(0, 'anchor:stick_rest', 'end', [0, 0, 0], STICK_LEAN, 0), key(0.05, 'anchor:can', 'end', AS.off([0, 0.12, 0]), STICK_UP, 0.3),
      key(0.35, 'anchor:stick_bench', 'center', [0, 0, 0], STICK_LIE, 0.3), key(0.65, 'anchor:stick_bench', 'center', [0, 0, 0], STICK_LIE, 0, {hidden: true})]},
    {id: 'furniture_lid', keys: [key(0, 'anchor:lid', 'center', [0, 0, 0], LID_DOWN, 0, {hidden: true}), key(0.6, 'anchor:lid', 'center', [0, 0, 0], LID_DOWN, 0),
      key(0.75, 'anchor:lid', 'center', AS.off([0, 0.06, 0]), LID_UP, 0.25), key(1.0, 'anchor:can', 'center', [0, 0, 0], LID_UP, 0.3),
      key(1.3, 'anchor:can', 'center', [0, 0, 0], LID_UP, 0, {hidden: true})]},
  ],
  hands: {
    right: [hand(0, 'prop:furniture_stick:grip', 0), hand(0.65, 'block:cantop', 0.4), hand(1.3, 'block:cantop', 0.15, {}), hand(1.6, 'pose', 0.4)],
    left: [hand(0, 'block:canhold', 0, {pole: L_POLE}), hand(0.5, 'prop:furniture_lid:hold', 0.2, {pole: L_POLE}), hand(1.3, 'block:canhold', 0.2, {pole: L_POLE}),
      hand(1.6, 'pose', 0.4)],
  },
  events: [],
});

// --- the crosscut (the hand saw, as the wooden knob's strip: tools/author-wooden-knob-actions.mjs)
const SAW_U = norm(sub(local(SAW, 'toe'), local(SAW, 'heel')));
const SAW_W = perp(sub(local(SAW, 'heel'), local(SAW, 'grip')), SAW_U);
const sawTurn = (h, b) => r3(turnFor(SAW_U, SAW_W, h, b));
const STROKE = 0.10, SLOPE = 18;
const sawDir = deg => [-CS.YS * Math.cos(deg * RAD), Math.sin(deg * RAD), -CS.YC * Math.cos(deg * RAD)];
const sawTeeth = deg => [CS.YS * Math.sin(deg * RAD), Math.cos(deg * RAD), CS.YC * Math.sin(deg * RAD)];
const sawKey = (at, u, blend, lift = 0) => ({at: t3(at), frame: 'anchor:kerf', point: 'kerf', pos: r2(add(mul(sawDir(SLOPE), u * PX), [0, -lift * PX, 0])),
  rot: sawTurn(sawDir(SLOPE), sawTeeth(SLOPE)), blend: t3(blend)});
const SAW_HIP = read('player_actions/tool/hand_saw_set.json').prop.keys[0];
const sawHip = (at, hidden, blend = 0) => ({at: t3(at), frame: SAW_HIP.frame, point: SAW_HIP.point, pos: [0.5, 8.3, -3.0], rot: SAW_HIP.rot, blend: t3(blend), ...(hidden ? {hidden: true} : {})});
/** The strokes: [push start, pull start], 0.4 s each (WorkbenchCrafting's noise, the dust). */
export const STROKES = [0, 1, 2, 3, 4, 5].map(i => [t3(0.9 + 0.8 * i), t3(1.3 + 0.8 * i)]);
/** The kerf's depth across the board by the second (WorkbenchWork#cutKerfZ). */
const kerfZ = t => CUT.z0 + (CUT.z1 - CUT.z0) * clamp((t - 0.9) / 4.8, 0, 1);
/** The board's slide (0..1) and the off-cut's carry (0..1) by the second (WorkbenchWork the same). */
export const SLIDE = [0.1, 0.6], CARRY = [6.1, 6.4], THROUGH = 5.7;
const SAW_KEYS = [sawHip(0, false), sawKey(0.15, -STROKE, 0.35), sawKey(0.5, -STROKE * 0.4, 0.2), sawKey(0.7, -STROKE, 0.2),
  ...STROKES.flatMap(([push, pull]) => [sawKey(push, STROKE, 0.4), sawKey(pull, -STROKE, 0.4)]),
  sawKey(THROUGH, -STROKE * 0.6, 0.1, 0.07), sawHip(5.8, false, 0.1), sawHip(5.9, true), sawHip(6.4, false)];
const CUT_VIEW = [[0, 56], [0.5, 60], [5.7, 60], [6.1, 64], [6.4, 58], [6.5, 56]];
ACTIONS.crosscut = {
  ...base('Crosscut a board: the left hand slides it 0.28 right past the cut over the bench\'s end (0.1-0.6 s), the saw from the right hip to the cut (0.15-0.5), two short starting strokes, six strokes across the board (0.9-5.7, the kerf going from its front edge to the back), through at 5.7; the saw back to the hip, the right hand carries the off-cut to the pile at the back (6.1-6.4) and goes back to the saw. The stance as the wooden knob\'s', 6.5, false),
  clip: SAWING,
  first_person_view: CUT_VIEW,
  props: [{id: 'hand_saw', keys: SAW_KEYS}],
  hands: {
    right: [hand(0, 'prop:hand_saw:grip', 0), hand(5.9, 'block:carry', 0.2), hand(6.4, 'prop:hand_saw:grip', 0.1)],
    left: [hand(0, 'block:hold', 0, {pole: L_POLE})],
  },
  events: [
    ...STROKES.flatMap(([push, pull]) => [ev(push, 'hand_saw_push', 8, {noise: 8, type: 'dust', until: pull}), ev(pull, 'hand_saw_pull', 4)]),
    {at: THROUGH, type: 'cut_through'},
    ev(6.4, 'workbench_tool_down', 4, {volume: 0.45, pitch: 1.35}),
  ],
};
// the chest: the assembly's one lean over the piece; the crosscut's as the wooden knob's saw (turned right, more on each push)
const CHEST_LEAN = {0: [30, 0, 0], 1: [30, 0, 0]};
const CHEST_CUT = {0: [28, 18, 0], 0.9: [28, 18, 0]};
for (const [push, pull] of STROKES) { CHEST_CUT[t3(push + 0.4)] = [33, 15, 0]; CHEST_CUT[t3(pull + 0.4)] = [28, 18, 0]; }
Object.assign(CHEST_CUT, {6.0: [30, 8, 0], 6.25: [34, 4, 0], 6.5: [28, 18, 0]});

// ---------------------------------------------------------------- write / check
const json = o => JSON.stringify(o, null, 2).replace(/\[\s+([^\[\]{}]+?)\s+\]/g, (m, inner) => '[' + inner.split(/,\s*/).join(', ') + ']') + '\n';
const files = {};
for (const [id, a] of Object.entries(ACTIONS)) files[path.join(ASSETS, `player_actions/furniture/${id}.json`)] = json(a);
for (const [id, p] of Object.entries(PROPS)) files[path.join(ASSETS, `player_props/${id}.json`)] = json(p);
const clipKeys = chest => Object.fromEntries(Object.entries(chest).sort((a, b) => a[0] - b[0]).map(([t, v]) => [Number(t).toFixed(2), {post: v, lerp_mode: 'catmullrom'}]));
const CLIPS = {
  [LEAN]: {loop: true, animation_length: 1, bones: {chest: {rotation: clipKeys(CHEST_LEAN)}}},
  [SAWING]: {loop: true, animation_length: 6.5, bones: {chest: {rotation: clipKeys(CHEST_CUT)}}},
};
// merged into the tool clips as tools/author-wooden-knob-actions.mjs does
{
  const tool = read('animations/player/tool.animation.json');
  Object.assign(tool.animations, CLIPS);
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
  fs.mkdirSync(path.join(ASSETS, 'player_actions/furniture'), {recursive: true});
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
  const clips = core.loadClips(rig, {format_version: '1.8.0', animations: CLIPS});
  const fmt = r => r && r.mode === 'ik' ? `off ${r.off.toFixed(2)} reach ${r.reach.toFixed(2)} elb ${r.elbow.toFixed(0)}` : (r ? r.mode : '-');
  // sample anchors (WorkbenchWork computes them per state): a far right and a far left nail of the frame, the box's top nail
  const SLOTS = {near_right: [0.83, 0.94, 0.095], far_left: [1.57, 0.94, 0.405], box_top: [1.35, 1.05, 0.3725]};
  const assembly = (slot, t, id) => {
    const [x, y, z] = slot;
    const can = /^can/.test(id), wipe = /^wipe/.test(id);
    const sz = can ? ASM.standZ : standZFor(z);
    const press = wipe ? [WIPE.b + 0.22, y + 0.03, 0.2] : [Math.min(x + PRESS_LEFT, 1.62), y + 0.03, clamp(z - PRESS_BACK, 0.09, sz + PRESS_REACH)];
    const st = stance(can ? CAN_STAND : wipe ? WIPE.standX : standFor(x), sz, 0), m = (...p) => st.at(...p);
    const o = {head: m(x, y + 0.85 * NAIL_LEN, z), shead: m(x, y + 0.65 * SCREW_LEN, z), press: m(...press), box: m(ASM.box[0], ASM.box[1] + 0.13, ASM.box[2]),
      hw: m(x - 0.038, y + 0.001, z), can: m(...CAN_MOUTH), cantop: m(CAN_MOUTH[0], CAN_MOUTH[1] + 0.025, CAN_MOUTH[2]),
      canhold: m(ASM.can[0] + 0.075, ASM.can[1] + 0.07, ASM.can[2]), lid: m(...ASM.lid), stick_bench: m(...ASM.stick),
      stick_rest: m(ASM.can[0] - 0.02, ASM.can[1] + 0.02, ASM.can[2]), rag_rest: m(...ASM.rag)};
    for (let i = 0; i < 3; i++) { const bz = 0.12 + 0.12 * i; o['w' + i + 'a'] = m(WIPE.a, y + WIPE.y, bz); o['w' + i + 'b'] = m(WIPE.b, y + WIPE.y, bz); }
    for (let i = 0; i < 2; i++) { const bz = 0.16 + 0.16 * i; o['o' + i + 'a'] = m(WIPE.a, y + WIPE.y, bz); o['o' + i + 'b'] = m(WIPE.b, y + WIPE.y, bz); }
    return o;
  };
  const crosscut = t => {
    const slide = clamp((t - SLIDE[0]) / (SLIDE[1] - SLIDE[0]), 0, 1), c = clamp((t - CARRY[0]) / (CARRY[1] - CARRY[0]), 0, 1);
    const from = [CUT.x - CUT.piece / 2, CUT.top + 0.02, (CUT.z0 + CUT.z1) / 2], to = [CUT.pileX, 0.9 + 0.025 + 0.02, CUT.pileZ];
    const carry = add(add(from, mul(sub(to, from), c)), [0, 0.08 * Math.sin(Math.PI * c), 0]);
    return {kerf: CS.at(CUT.x, CUT.top - 0.01, kerfZ(t)), hold: CS.at(CUT.holdX + CUT.piece * (1 - slide), CUT.holdY, (CUT.z0 + CUT.z1) / 2), carry: CS.at(...carry)};
  };
  const only = process.argv.find(a => a.startsWith('--only='))?.slice(7);
  for (const [id, a] of Object.entries(ACTIONS)) {
    if (id === 'nail' || only && id !== only) continue;
    const act = core.actionFromJson({...a, blend: {in: 0, out: 0}}, n => defs[n], n => clips[n] || null);
    const view = a.first_person_view;
    const viewAt = t => { let v = view[0][1]; for (const [at, d] of view) if (t >= at) v = d; return v; };
    const slots = /^(nail|screw|fit)/.test(id) ? Object.entries(SLOTS) : /^(can|wipe)/.test(id) ? [['-', [1.02, 0.94, 0.25]]] : [['-', SLOTS.near_right]];
    for (const [sn, slot] of slots) {
      const lines = [];
      for (let t = 0; t <= a.length + 1e-6; t += Math.max(0.05, a.length / 8)) {
        const anchors = id === 'crosscut' ? crosscut(t) : assembly(slot, t, id);
        const res = core.solveFrame(poser, {pitch: viewAt(t), action: act, seconds: t, fp: true, anchors: n => anchors[n] || null});
        const ro = core.readouts(res);
        lines.push(`  t${t.toFixed(2)} R ${fmt(ro.right)} | L ${fmt(ro.left)}`);
      }
      console.log(id + (sn === '-' ? '' : ' @' + sn));
      console.log(lines.join('\n'));
    }
  }
}
