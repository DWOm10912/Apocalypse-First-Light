// Felling axe skeleton (docs/gameplay/felling_axe_v1.md 4, 2026-10-20): writes the prop player_props/felling_axe.json and
// the actions player_actions/item/felling_axe.json (the still hold) and player_actions/tool/felling_axe_chop.json (the
// chop). No arm clips: the prop's placement timeline and the hands' targets on it, solved by the skeleton's IK at run time
// (as item/crowbar); the chop's chest clip is tool.felling_axe_chop (tools/author-player-actions.mjs).
//
// Size (user 2026-10-20 "斧头有点太小了……用玩家的比例做参照"): scale 0.75, the 25.5 px handle 1.2 blocks, the axe a bit
// over 60 % of the player's height and its head (2.7 px) about the size of the 4 px hand, as a tool held by the blocky
// player reads; at 0.508 (a real 32 inch axe) it was 13 model px, the head smaller than the hand.
//
// The chop (one swing, 1.0 s; tree/FellingRules SWING_TICKS 20 and IMPACT_TICKS 11, keep them in step):
//   0.00 the hold: left hand on the knob at the left of the belly, right hand up the handle, head over the right shoulder;
//   0.40 the top of the wind-up: both hands by the right side of the face, the head up and back over the right shoulder,
//        the chest turned right and leaning back;
//   0.55 the impact: ease "in" (from rest at the top, fastest at the cut), the right hand slid down next to the left, the
//        arms out before the belly, the handle forward and a little down and left, the bit leading down and left (a
//        diagonal cut from the right, as the notch's top cut); the chest bent forward and turned left;
//   0.60 the bite: the head stops in the wood, a little further on;
//   0.75 pulled out, a little back; 1.00 back in the hold (the right hand back up the handle).
// Every pose is grip_end (the left hand) at a place px from the right shoulder (frame shoulder_r) with the handle along H
// and the bit along B (model axes: x the player's left, y down, z back); the turn is solved from them. The swing from the
// top to the impact turns about the axis between the two handle lines, so the bit leads along the swing.
//   node tools/author-felling-axe-actions.mjs [--check] [--report]
import fs from 'node:fs';
import path from 'node:path';
import {createRequire} from 'node:module';
import {fileURLToPath} from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const ASSETS = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight');
const r4 = v => v.map(n => Math.round(n * 10000) / 10000);
const r2 = v => v.map(n => Math.round(n * 100) / 100);
const RAD = Math.PI / 180, DEG = 180 / Math.PI;
const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
const add = (a, b) => a.map((v, i) => v + b[i]), sub = (a, b) => a.map((v, i) => v - b[i]), mul = (a, k) => a.map(v => v * k);
const dot = (a, b) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
const cross = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
const norm = a => mul(a, 1 / Math.hypot(...a));
/** A direction (model axes) yaw deg to the player's right of ahead, pitch deg up. */
const dir = (yaw, pitch) => [-Math.sin(yaw * RAD) * Math.cos(pitch * RAD), -Math.sin(pitch * RAD), -Math.cos(yaw * RAD) * Math.cos(pitch * RAD)];

// ---------------------------------------------------------------- the prop
const SCALE = 0.75, K = 16 * SCALE;
// the handle's centreline as tools/build-equipment-meshes-v1.mjs fellingAxe() lofts it (baked px: x at the top 1.3469)
const mid = y => 0.25 * Math.sin(Math.PI * clamp(y - 2, 0, 21) / 21) - (y < 3 ? 0.3 * (1 - y / 3) ** 2 : 0);
const X0 = 1.3469, EYE = 24.4;
// grips (px up the handle): the left hand just above the knob's swell, the right next to it (the chop's impact) or up
// the handle (the hold, the wind-up); with 4 px hands at this scale (5.3 px of handle) they sit side by side
const GRIPS = {grip_end: 2.8, grip: 8.3, grip_high: 13.1};
const PX = {
  ...Object.fromEntries(Object.entries(GRIPS).map(([n, y]) => [n, [X0 + mid(y), y, 0]])),
  end: [X0 + mid(0), 0, 0], top: [X0, 25.5, 0], eye: [X0, EYE, 0], edge: [-2.8731, 24.46, 0], poll: [2.8669, 24.4, 0],
};
const itemUnits = p => [p[0] / 16 + 0.5, p[1] / 16 - 0.31, p[2] / 16 + 0.5];
const PROP = {
  about: 'The felling axe as the skeleton holds it, first and third person (docs/gameplay/felling_axe_v1.md 4; written by tools/author-felling-axe-actions.mjs). Points, bounds: item block units of the axe mesh as client/AflStaticMeshItemClient draws it (meshes/felling_axe.aflmesh.json + (0.5, -0.31, 0.5); y up the handle, the bit to -x at the top). Scale 0.75 (2026-10-20, the user: too small in the hand at a real 32 inch axe\'s 0.508): the 25.5 px handle is 1.2 blocks, the head about the size of the hand. Grips: grip_end the left hand just above the knob, grip the right hand next to it (the chop), grip_high the right hand up the handle (the hold, the wind-up).',
  items: ['felling_axe'],
  scale: SCALE,
  bounds: [[0.3208, -0.31, 0.4675], [0.6792, 1.31, 0.5325]],
  symmetry: 'z',
  points: Object.fromEntries(Object.entries(PX).map(([n, p]) => [n, r4(itemUnits(p))])),
};
const local = name => { const p = PROP.points[name]; return [p[0] * K, -p[1] * K, -p[2] * K]; };

// the prop's own frame: lu up the handle (grip_end to the eye), lw toward the bit, lv across
const LU = norm(sub(local('eye'), local('grip_end')));
const LW = (() => { const w = sub(local('edge'), local('eye')); return norm(sub(w, mul(LU, dot(w, LU)))); })();
const LV = cross(LU, LW);

/** The key turn (deg, about z then y then x: Rx Ry Rz) taking the handle to h and the bit toward b. */
function turnFor(h, b) {
  h = norm(h);
  b = norm(sub(b, mul(h, dot(b, h))));
  const v = cross(h, b);
  const R = [0, 1, 2].map(r => [0, 1, 2].map(c => h[r] * LU[c] + b[r] * LW[c] + v[r] * LV[c]));
  const yb = Math.asin(clamp(R[0][2], -1, 1)), xa = Math.atan2(-R[1][2], R[2][2]), zc = Math.atan2(-R[0][1], R[0][0]);
  return [xa * DEG, yb * DEG, zc * DEG];
}
/** Rx(a) Ry(b) Rz(c) applied to v (deg). */
function turnApply(rot, v) {
  const [a, b, c] = rot.map(d => d * RAD);
  let [x, y, z] = v;
  [x, y] = [x * Math.cos(c) - y * Math.sin(c), x * Math.sin(c) + y * Math.cos(c)];
  [x, z] = [x * Math.cos(b) + z * Math.sin(b), -x * Math.sin(b) + z * Math.cos(b)];
  [y, z] = [y * Math.cos(a) - z * Math.sin(a), y * Math.sin(a) + z * Math.cos(a)];
  return [x, y, z];
}

// ---------------------------------------------------------------- poses
// the hold: item/crowbar's rest pose (the axe's V1 hold) as it was, the left hand kept where it held the knob there
const OLD = {scale: 0.508, grip: [0.591, 0.0025, 0.5], gripEnd: [0.5799, -0.2119, 0.5], pos: [5.7, 6.2, -6.0], rot: [-90.0, -60.6, -113.5]};
const oldLocal = p => [p[0] * 16 * OLD.scale, -p[1] * 16 * OLD.scale, -p[2] * 16 * OLD.scale];
const HOLD = {
  e: add(OLD.pos, turnApply(OLD.rot, sub(oldLocal(OLD.gripEnd), oldLocal(OLD.grip)))),
  h: turnApply(OLD.rot, LU), b: turnApply(OLD.rot, LW),
};
// the top of the wind-up: the handle up and back over the right shoulder
const TOP_H = dir(140, 38);
// the impact: the handle ahead, a little down and to the left
const HIT_H = dir(-12, -24);
// the swing's axis, and the bit along the swing at both ends
const AXIS = norm(cross(TOP_H, HIT_H));
const along = h => cross(AXIS, h);
const turnAbout = (h, deg) => add(add(mul(h, Math.cos(deg * RAD)), mul(cross(AXIS, h), Math.sin(deg * RAD))), mul(AXIS, dot(AXIS, h) * (1 - Math.cos(deg * RAD))));
const POSES = {
  hold: HOLD,
  lift: {e: [0.4, 0.6, -5.2], h: dir(100, 32), b: along(dir(100, 32))},
  top: {e: [-1.2, -1.8, -5.0], h: TOP_H, b: along(TOP_H)},
  hit: {e: [3.0, 4.0, -3.7], h: HIT_H, b: along(HIT_H)},
  bite: {e: [3.0, 4.1, -3.7], h: turnAbout(HIT_H, 4), b: along(turnAbout(HIT_H, 4))},
  out: {e: [2.6, 3.4, -3.8], h: turnAbout(HIT_H, -14), b: along(turnAbout(HIT_H, -14))},
};
const key = (at, pose, blend, ease) => {
  const p = POSES[pose], k = {at, frame: 'shoulder_r', point: 'grip_end', pos: r2(p.e), rot: r2(turnFor(p.h, p.b)), blend};
  if (ease) k.ease = ease;
  return k;
};

// ---------------------------------------------------------------- actions
const POLE_TOP = [-1, -0.6, 0.3], POLE_HIT = [-0.6, 1, 0.3];
const ACTIONS = {
  'item/felling_axe': {
    format_version: 1,
    about: 'Felling axe in the main hand, first and third person alike (client/skeleton/SkeletonActions, 2026-10-20; written by tools/author-felling-axe-actions.mjs). A still two-handed hold, the crowbar\'s rest pose: the left hand low on the handle just above the knob, at the left of the belly, the handle slanting up to the right and ahead, the right hand up it at the right of the chest, the head ahead over the right shoulder. A chop or a melee swing is tool/felling_axe_chop, which starts and ends in this hold. docs/gameplay/felling_axe_v1.md',
    layer: 'action', mask: 'arms', priority: 30, first_person: 'body',
    blend: {in: 0.0, out: 0.2},
    prop: {id: 'felling_axe', keys: [key(0, 'hold', 0)]},
    hands: {
      right: [{at: 0, target: 'prop:grip_high', blend: 0, roll: 0}],
      left: [{at: 0, target: 'prop:grip_end', blend: 0, roll: 0}],
    },
    events: [],
  },
  'tool/felling_axe_chop': {
    format_version: 1,
    about: 'One swing of the felling axe, first and third person alike (client/skeleton/SkeletonActions; written by tools/author-felling-axe-actions.mjs). A chop at a tree (tree/FellingSwingClient: seconds since the swing started, the server lands it at 0.55 s = FellingRules IMPACT_TICKS) or a melee swing (the vanilla attack, played 2.2 times as fast). Starts and ends in item/felling_axe\'s hold: up over the right shoulder by 0.40 s (the chest turned right), down from the right into the cut by 0.55 s (from rest, fastest at the cut; the right hand slides down next to the left), the bite, pulled out, back in the hold at 1.0 s. docs/gameplay/felling_axe_v1.md',
    clip: 'tool.felling_axe_chop',
    layer: 'action', mask: 'upper', priority: 60, first_person: 'body',
    blend: {in: 0.0, out: 0.3},
    prop: {id: 'felling_axe', keys: [
      key(0, 'hold', 0),
      key(0.001, 'lift', 0.199, 'in'),
      key(0.2, 'top', 0.2, 'out'),
      key(0.4, 'hit', 0.15, 'in'),
      key(0.55, 'bite', 0.05),
      key(0.6, 'out', 0.15),
      key(0.75, 'hold', 0.25),
    ]},
    hands: {
      right: [
        {at: 0, target: 'prop:grip_high', blend: 0, roll: 0},
        {at: 0.001, target: 'prop:grip_high', blend: 0.399, pole: POLE_TOP, roll: 0},
        {at: 0.42, target: 'prop:grip', blend: 0.12, pole: POLE_HIT, roll: 0},
        {at: 0.85, target: 'prop:grip_high', blend: 0.15, roll: 0},
      ],
      left: [{at: 0, target: 'prop:grip_end', blend: 0, roll: 0}],
    },
    events: [],
  },
};

const json = o => JSON.stringify(o, null, 2).replace(/\[\s+([^\[\]{}]+?)\s+\]/g, (m, inner) => '[' + inner.split(/,\s*/).join(', ') + ']') + '\n';
const files = {[path.join(ASSETS, 'player_props/felling_axe.json')]: json(PROP)};
for (const [id, a] of Object.entries(ACTIONS)) files[path.join(ASSETS, `player_actions/${id}.json`)] = json(a);

// the turns solve back to the poses
for (const [name, p] of Object.entries(POSES)) {
  const rot = turnFor(p.h, p.b), h = turnApply(rot, LU), b = turnApply(rot, LW);
  const bh = norm(sub(p.b, mul(norm(p.h), dot(p.b, norm(p.h)))));
  if (Math.hypot(...sub(h, norm(p.h))) > 1e-6 || Math.hypot(...sub(b, bh)) > 1e-6) throw new Error('turn does not solve back: ' + name);
}

if (process.argv.includes('--check')) {
  let stale = 0;
  for (const [p, text] of Object.entries(files)) {
    if (!fs.existsSync(p) || fs.readFileSync(p, 'utf8').replace(/\r\n/g, '\n') !== text) { console.error('STALE ' + path.relative(ROOT, p)); stale++; }
  }
  if (stale) process.exit(1);
  console.log('CHECK OK');
} else if (!process.argv.includes('--report')) {
  for (const [p, text] of Object.entries(files)) fs.writeFileSync(p, text);
  console.log('wrote ' + Object.keys(files).length + ' files');
}

// ---------------------------------------------------------------- report: the plugin's poser (= the game's)
if (process.argv.includes('--report')) {
  const core = createRequire(import.meta.url)('./blockbench/afl_rig.js');
  const read = p => JSON.parse(fs.readFileSync(path.join(ASSETS, p), 'utf8'));
  const rig = core.loadRig(read('rigs/afl_humanoid_v1.json'));
  const poser = new core.Poser(rig, core.loadClips(rig, read('animations/player/locomotion.animation.json')));
  const tools = core.loadClips(rig, read('animations/player/tool.animation.json'));
  const {tf} = core;
  // the axe sampled: the handle's centreline, the head's outline and inside (px), as item units
  const samples = [];
  for (let y = 0; y <= 25.5; y += 0.75) samples.push(['handle', [X0 + mid(y), y, 0]]);
  for (let x = -2.87; x <= 2.87; x += 0.41) {
    const f = clamp((1.35 - x) / 4.22, 0, 1), up = 0.92 + 0.6 * f, down = 0.92 + 1.16 * f;
    for (const y of [EYE + up, EYE, EYE - down]) samples.push(['head', [x, y, 0]]);
  }
  const pts = samples.map(([part, p]) => [part, (() => { const u = itemUnits(p); return [u[0] * K, -u[1] * K, -u[2] * K]; })()]);
  // body boxes (rest model px, y down) and the bones carrying them
  const boxes = [
    ['chest', rig.find('chest'), [-4, 0, -2], [4, 6, 2]],
    ['belly', rig.find('pelvis'), [-4, 6, -2], [4, 12, 2]],
    ['head', rig.find('head'), [-4, -8, -4], [4, 0, 4]],
    ['legs', rig.find('pelvis'), [-4, 12, -2], [4, 24, 2]],
  ];
  const clearance = (q, lo, hi) => {   // signed: > 0 outside by that much, < 0 inside
    const d = [0, 1, 2].map(i => Math.max(lo[i] - q[i], q[i] - hi[i]));
    const out = Math.hypot(...d.map(v => Math.max(0, v)));
    return out > 0 ? out : Math.max(...d);
  };
  for (const [name, a] of Object.entries(ACTIONS)) {
    const act = core.actionFromJson(a, () => PROP, n => tools[n] || null);
    console.log('== ' + name);
    const times = name.endsWith('chop') ? [0, 0.1, 0.2, 0.3, 0.4, 0.45, 0.5, 0.53, 0.55, 0.6, 0.7, 0.8, 0.9, 1.0] : [0];
    for (const pitch of [0, 35, 60]) for (const t of times) {
      const res = core.solveFrame(poser, {pitch, action: act, seconds: t, fp: true});
      const place = res.props[0];
      if (!place) { console.log(`  p${pitch} t${t}: no prop`); continue; }
      const ro = core.readouts(res);
      const eye = [0, core.eyeModelY(false), 0];
      let worst = {c: 99}, near = 99;
      for (const [part, l] of pts) {
        const q = tf.apply(place, l);
        near = Math.min(near, Math.hypot(...sub(q, eye)));
        for (const [box, bone, lo, hi] of boxes) {
          const r = tf.apply(tf.inv(res.pose.world[bone]), q);
          const c = clearance(r, lo, hi) - (part === 'handle' ? 0.3 : 0.05);
          if (c < worst.c) worst = {c, box, part};
        }
      }
      const head = tf.apply(place, local('eye'));
      const d = sub(head, eye), hd = Math.hypot(d[0], d[2]);
      const down = Math.atan2(d[1], hd) * DEG - pitch, right = Math.atan2(-d[0], -d[2]) * DEG;
      const fmt = r => r && r.mode === 'ik' ? `off ${r.off.toFixed(2)} reach ${r.reach.toFixed(2)} elbow ${r.elbow.toFixed(0)}` : 'clip';
      console.log(`  p${pitch} t${t.toFixed(2)}: R ${fmt(ro.right)} | L ${fmt(ro.left)} | clear ${worst.c.toFixed(2)} (${worst.part}/${worst.box}) | cam ${near.toFixed(1)} px | head ${down.toFixed(0)}v ${right.toFixed(0)}> ${hd.toFixed(1)} px out, ${(24 - head[1]).toFixed(1)} px up`);
    }
  }
  for (const [n, p] of Object.entries(POSES)) console.log(`${n}: rot ${r2(turnFor(p.h, p.b)).join(', ')} pos ${r2(p.e).join(', ')}`);
}
