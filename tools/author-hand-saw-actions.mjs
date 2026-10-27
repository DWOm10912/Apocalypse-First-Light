// Hand saw at the workbench (2026-10-21, docs/gameplay/afl_workbench_v1_design.md 17): writes the prop
// player_props/hand_saw.json and the actions player_actions/tool/hand_saw_set.json (taking the saw to the cut, played once)
// and player_actions/tool/hand_saw_rip.json (the strokes, a loop), and the chest clips tool.hand_saw_set / tool.hand_saw_rip
// live in tools/author-player-actions.mjs. No arm clips: the saw's placement and the hands' targets, solved by the IK.
//
// The work (client/workbench/WorkbenchWork places the anchors from the bench, the same numbers as WORK below): a white
// oak log lies on the top over the drawers, along the bench's depth, its front end over the front edge; the saw rips it
// down the middle from that end. The body steps to the stand anchor (the right shoulder in the saw's plane, the body
// behind the cut) and faces into the bench; the saw's tooth line runs through the kerf anchor (the cut's front on the
// log's top), the toe ahead and down at RIP deg, the teeth down; a stroke slides the saw along its tooth line by STROKE
// either way of the middle (push = the cut, 0.4 s, back 0.4 s). The right hand holds the grip, the left hand braces the
// log's left side behind the cut.
//   node tools/author-hand-saw-actions.mjs [--check] [--report]
import fs from 'node:fs';
import path from 'node:path';
import {createRequire} from 'node:module';
import {fileURLToPath} from 'node:url';
import {ITEMS} from './build-equipment-meshes-v1.mjs';

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

// ---------------------------------------------------------------- the work (blocks; keep in step with WorkbenchWork)
/** Model px per block (PlayerRenderer scales the model by 0.9375); the feet at model y 24.016. */
const PX = 16 / 0.9375, FEET = 1.501 * 16;
export const WORK = {
  logR: 0.09,          // the log's half width (an 18 cm log)
  top: 0.9,            // the bench top
  standLeft: 0.36,     // the body's middle this far to the left of the saw's plane (the right shoulder over it)
  standBack: 0.42,     // ... and this far before the kerf (along the cut)
  yaw: -15,            // the body turned this far to the left of the cut (deg; to the right: the left shoulder toward the log)
  holdLeft: 0.13,      // the left hand: on the log's left flank, this far left of the cut,
  holdBack: 0.1,       // this far before the kerf (WorkbenchWork keeps it on the log: at most the end + 0.03),
  holdUp: 0.15,        // this high over the top
};
// the saw: the tooth line's slope (deg, the toe down), half the stroke (blocks), the point of the tooth line in the kerf
// in the middle of a stroke (baked px up the saw from its handle end)
const SAW = {rip: 20, stroke: 0.11, kerfAt: 11};
// the chest (tools/author-player-actions.mjs tool.hand_saw_set / tool.hand_saw_rip, the same numbers): leaning into the
// bench and turned right (the left shoulder toward the log, the right back behind the saw), a little more into the push
export const CHEST = {lean: 25, turn: 18, push: 5};
// tuning a report: key=value (WORK's and SAW's keys)
for (const a of process.argv) {
  const m = /^(\w+)=(-?[\d.]+)$/.exec(a);
  if (m && m[1] in WORK) WORK[m[1]] = Number(m[2]);
  if (m && m[1] in SAW) SAW[m[1]] = Number(m[2]);
  if (m && m[1] in CHEST) CHEST[m[1]] = Number(m[2]);
}
const RIP = SAW.rip, STROKE = SAW.stroke, KERF_AT = SAW.kerfAt;

// ---------------------------------------------------------------- the prop
const SCALE = 0.508;   // real size, as the bench and the tool rack hang it (a 26 inch saw, 0.79 m over all)
const spec = ITEMS.hand_saw();
const all = spec.PARTS.flatMap(p => p.v);
const lo = [0, 1, 2].map(k => Math.min(...all.map(q => q[k]))), hi = [0, 1, 2].map(k => Math.max(...all.map(q => q[k])));
const shift = [-(lo[0] + hi[0]) / 2, -lo[1], -(lo[2] + hi[2]) / 2];
const baked = p => add(p, shift);
const size = sub(hi, lo);
const VERTICAL = 0.5 - size[1] / 32;   // client/AflStaticMeshItemClient's offset for this mesh (-0.27375)
const itemUnits = p => [p[0] / 16 + 0.5, p[1] / 16 + VERTICAL, p[2] / 16 + 0.5];
const P = Object.fromEntries(Object.entries(spec.points).map(([n, p]) => [n, baked(p)]));
const TEETH_X = P.heel[0];
P.kerf = [TEETH_X, KERF_AT, 0];
const PROP = {
  about: 'The hand saw as the skeleton holds it at the workbench (docs/gameplay/afl_workbench_v1_design.md 17; written by tools/author-hand-saw-actions.mjs). Points, bounds: item block units of the saw mesh as client/AflStaticMeshItemClient draws it (meshes/hand_saw.aflmesh.json + (0.5, ' + VERTICAL.toFixed(5) + ', 0.5); y along the blade from the handle to the toe, the teeth to -x). Scale 0.508: real size, a 26 inch rip saw, as the bench\'s pegs hang it. Points: grip the right hand in the handle, kerf the tooth line where it sits in the cut in the middle of a stroke, toe, heel (the tooth line\'s ends), teeth_mid, hole_c.',
  items: ['hand_saw'],
  scale: SCALE,
  bounds: [r4(itemUnits(baked(lo))), r4(itemUnits(baked(hi)))],
  symmetry: 'z',
  points: Object.fromEntries(Object.entries(P).map(([n, p]) => [n, r4(itemUnits(p))])),
};
const K = 16 * SCALE;
const local = name => { const p = PROP.points[name]; return [p[0] * K, -p[1] * K, -p[2] * K]; };
const localOf = p => { const u = itemUnits(p); return [u[0] * K, -u[1] * K, -u[2] * K]; };

// the prop's own frame: LU along the tooth line to the toe, LW from the back toward the teeth, LV across
const LU = norm(sub(local('toe'), local('heel')));
const LW = (() => { const w = sub(local('heel'), local('grip')); return norm(sub(w, mul(LU, dot(w, LU)))); })();
const LV = cross(LU, LW);

/** The key turn (deg, about z then y then x: Rx Ry Rz) taking the tooth line to h and the teeth toward b. */
function turnFor(h, b) {
  h = norm(h);
  b = norm(sub(b, mul(h, dot(b, h))));
  const v = cross(h, b);
  const R = [0, 1, 2].map(r => [0, 1, 2].map(c => h[r] * LU[c] + b[r] * LW[c] + v[r] * LV[c]));
  const yb = Math.asin(clamp(R[0][2], -1, 1));
  // y at +-90 deg (the rip hold: the blade's plane across x): x and z turn about the same axis, all of it on x
  if (Math.abs(R[0][2]) > 0.99999) return [Math.atan2(R[2][1], R[1][1]) * DEG, yb * DEG, 0];
  const xa = Math.atan2(-R[1][2], R[2][2]), zc = Math.atan2(-R[0][1], R[0][0]);
  return [xa * DEG, yb * DEG, zc * DEG];
}
function turnApply(rot, v) {
  const [a, b, c] = rot.map(d => d * RAD);
  let [x, y, z] = v;
  [x, y] = [x * Math.cos(c) - y * Math.sin(c), x * Math.sin(c) + y * Math.cos(c)];
  [x, z] = [x * Math.cos(b) + z * Math.sin(b), -x * Math.sin(b) + z * Math.cos(b)];
  [y, z] = [y * Math.cos(a) - z * Math.sin(a), y * Math.sin(a) + z * Math.cos(a)];
  return [x, y, z];
}

// ---------------------------------------------------------------- poses (model axes: x the player's left, y down, z back)
// the body faces yaw to the left of the cut: in its frame the cut runs ahead and to the right
const YS = Math.sin(WORK.yaw * RAD), YC = Math.cos(WORK.yaw * RAD);
const D = [-YS * Math.cos(RIP * RAD), Math.sin(RIP * RAD), -YC * Math.cos(RIP * RAD)];   // the tooth line, toe ahead and down
const T = [YS * Math.sin(RIP * RAD), Math.cos(RIP * RAD), YC * Math.sin(RIP * RAD)];      // the teeth: down, a little back
const RIP_TURN = r2(turnFor(D, T));
const strokeKey = (at, u, blend) => ({at, frame: 'anchor:kerf', point: 'kerf', pos: r2(mul(D, u * PX)), rot: RIP_TURN, blend});
// taking it to the cut: from beside the right hip (the toe ahead and down, teeth down) into the back of a stroke
const SIDE_H = norm([0.1, 0.8, -1]), SIDE_T = norm(sub([0, 1, 0], mul(SIDE_H, dot([0, 1, 0], SIDE_H))));
const SET = 0.7;

const ACTIONS = {
  'tool/hand_saw_set': {
    format_version: 1,
    about: 'Hand saw at the workbench, the start (client/workbench/WorkbenchWork; written by tools/author-hand-saw-actions.mjs): played once, ' + SET + ' s, then tool/hand_saw_rip. The body steps to the stand anchor before the log on the top and faces into the bench (approach), the head down to the cut; the saw comes up from beside the right hip into the kerf at the back of a stroke, the left hand goes to the log\'s left side. What the player holds is not drawn (held_item hidden: the saw is drawn from its own item, carried anywhere). docs/gameplay/afl_workbench_v1_design.md 17',
    clip: 'tool.hand_saw_set',
    layer: 'action', mask: 'upper', priority: 55, first_person: 'body', held_item: 'hidden',
    blend: {in: 0.3, out: 0.25},
    approach: {anchor: 'stand', distance: 0, side: 0, max: 2.0, face: 'face', pitch: 40, in: 0.8, out: 0.8},
    prop: {id: 'hand_saw', keys: [
      {at: 0, frame: 'shoulder_r', point: 'grip', pos: [0.5, 9.5, -3.5], rot: r2(turnFor(SIDE_H, SIDE_T)), blend: 0},
      {...strokeKey(0.15, -STROKE, SET - 0.15)},
    ]},
    hands: {
      right: [{at: 0, target: 'prop:grip', blend: 0, roll: 0}],
      left: [{at: 0, target: 'pose'}, {at: 0.25, target: 'block:hold', blend: 0.4, pole: [0.8, 1.0, 0.2], roll: 0}],
    },
    events: [],
  },
  'tool/hand_saw_rip': {
    format_version: 1,
    about: 'Hand saw at the workbench, the strokes (client/workbench/WorkbenchWork, after tool/hand_saw_set; written by tools/author-hand-saw-actions.mjs): a ' + (2 * 0.4).toFixed(1) + ' s loop, the saw pushed along its tooth line ' + Math.round(2 * STROKE * 100) + ' cm into the cut (0.4 s) and drawn back, the tooth line through the kerf anchor (it moves into the log as the item is made), the toe ' + RIP + ' deg down; the chest rocks into the push. The right hand holds the grip, the left braces the log. No blend in (it follows the start\'s last pose). docs/gameplay/afl_workbench_v1_design.md 17',
    clip: 'tool.hand_saw_rip',
    layer: 'action', mask: 'upper', priority: 55, first_person: 'body', held_item: 'hidden',
    loop: 'time', length: 0.8,
    blend: {in: 0, out: 0.25},
    approach: {anchor: 'stand', distance: 0, side: 0, max: 2.0, face: 'face', pitch: 40, out: 0.8},
    prop: {id: 'hand_saw', keys: [strokeKey(0, -STROKE, 0), strokeKey(0.001, STROKE, 0.399), strokeKey(0.4, -STROKE, 0.4)]},
    hands: {
      right: [{at: 0, target: 'prop:grip', blend: 0, roll: 0}],
      left: [{at: 0, target: 'block:hold', blend: 0, pole: [0.8, 1.0, 0.2], roll: 0}],
    },
    events: [],
  },
};

const json = o => JSON.stringify(o, null, 2).replace(/\[\s+([^\[\]{}]+?)\s+\]/g, (m, inner) => '[' + inner.split(/,\s*/).join(', ') + ']') + '\n';
const files = {[path.join(ASSETS, 'player_props/hand_saw.json')]: json(PROP)};
for (const [id, a] of Object.entries(ACTIONS)) files[path.join(ASSETS, `player_actions/${id}.json`)] = json(a);

// the turn solves back
{
  const h = turnApply(RIP_TURN, LU), b = turnApply(RIP_TURN, LW);
  if (Math.hypot(...sub(h, D)) > 1e-3 || Math.hypot(...sub(b, T)) > 1e-3) throw new Error('rip turn does not solve back');
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

// ---------------------------------------------------------------- report: the plugin's poser (= the game's), the stood body
if (process.argv.includes('--report')) {
  // the plugin's solveFrame with the action's anchors (it has none of its own): the stood body's frame, the anchors as
  // WorkbenchWork places them relative to it (the root at the stand anchor, facing the bench)
  const src = fs.readFileSync(path.join(ROOT, 'tools/blockbench/afl_rig.js'), 'utf8')
      .replace('anchors: () => null, prop: null', 'anchors: o.anchors || (() => null), prop: null');
  const mod = {exports: {}};
  new Function('module', 'exports', 'require', src)(mod, mod.exports, createRequire(import.meta.url));
  const core = mod.exports;
  const read = p => JSON.parse(fs.readFileSync(path.join(ASSETS, p), 'utf8'));
  const rig = core.loadRig(read('rigs/afl_humanoid_v1.json'));
  const poser = new core.Poser(rig, core.loadClips(rig, read('animations/player/locomotion.animation.json')));
  // the chest clips as tools/author-player-actions.mjs writes them, from CHEST (the tuning's numbers)
  const ch = {lean: CHEST.lean, turn: CHEST.turn};
  const rot = keys => ({chest: {rotation: Object.fromEntries(Object.entries(keys).map(([t, v]) => [Number(t).toFixed(2), {post: v, lerp_mode: 'catmullrom'}]))}});
  const clips = core.loadClips(rig, {format_version: '1.8.0', animations: {
    'tool.hand_saw_set': {loop: false, animation_length: SET, bones: rot({0: [0, 0, 0], [SET]: [ch.lean, ch.turn, 0]})},
    'tool.hand_saw_rip': {loop: true, animation_length: 0.8, bones: rot({0: [ch.lean, ch.turn, 0], 0.4: [ch.lean + CHEST.push, ch.turn - 3, 0], 0.8: [ch.lean, ch.turn, 0]})},
  }});
  const {tf} = core;
  const W = WORK, logTop = W.top + 2 * W.logR;
  // a world offset (left of the cut, up, ahead along it; blocks from the stand point) as model px of the body turned yaw
  // to the left
  const cy = Math.cos(W.yaw * RAD), sy = Math.sin(W.yaw * RAD);
  const m = (l, up, a) => [(l * cy - a * sy) * PX, FEET - up * PX, -(l * sy + a * cy) * PX];
  const anchors = {
    kerf: m(-W.standLeft, logTop, W.standBack),
    hold: m(W.holdLeft - W.standLeft, W.top + W.holdUp, W.standBack - W.holdBack),
  };
  // the saw's outline, the log's box (model px; along z the cut part and on), body boxes
  const samples = [];
  for (const p of spec.PARTS) for (let i = 0; i < p.v.length; i += 7) samples.push([p.name, localOf(baked(p.v[i]))]);
  const logLo = [anchors.kerf[0] - W.logR * PX, FEET - logTop * PX, -99], logHi = [anchors.kerf[0] + W.logR * PX, FEET - W.top * PX, 99];
  const boxes = [
    ['chest', rig.find('chest'), [-4, 0, -2], [4, 6, 2]],
    ['belly', rig.find('pelvis'), [-4, 6, -2], [4, 12, 2]],
    ['head', rig.find('head'), [-4, -8, -4], [4, 0, 4]],
    ['legs', rig.find('pelvis'), [-4, 12, -2], [4, 24, 2]],
  ];
  const clearance = (q, lo, hi) => {
    const d = [0, 1, 2].map(i => Math.max(lo[i] - q[i], q[i] - hi[i]));
    const out = Math.hypot(...d.map(v => Math.max(0, v)));
    return out > 0 ? out : Math.max(...d);
  };
  const eye = [0, core.eyeModelY(false), 0];
  for (const [name, a] of Object.entries(ACTIONS)) {
    const act = core.actionFromJson(a, () => PROP, n => clips[n] || null);
    console.log('== ' + name);
    const times = name.endsWith('set') ? [0, 0.15, 0.3, 0.45, 0.6, 0.7] : [0, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7];
    for (const t of times) {
      const res = core.solveFrame(poser, {pitch: a.approach.pitch, action: act, seconds: t, fp: true, anchors: n => anchors[n] || null});
      const place = res.props[0];
      if (!place) { console.log(`  t${t}: no prop`); continue; }
      const ro = core.readouts(res);
      let worst = {c: 99}, deep = -99, near = 99;
      for (const [part, l] of samples) {
        const q = tf.apply(place, l);
        near = Math.min(near, Math.hypot(...sub(q, eye)));
        for (const [box, bone, blo, bhi] of boxes) {
          const r = tf.apply(tf.inv(res.pose.world[bone]), q);
          const c = clearance(r, blo, bhi) - 0.1;
          if (c < worst.c) worst = {c, box, part};
        }
        // the blade below the log's top inside its width: how far below the log's bottom (the bench top) it goes
        if (Math.abs(q[0] - anchors.kerf[0]) < W.logR * PX && q[2] < anchors.kerf[2] + 0.01 * PX && q[1] > logLo[1]) deep = Math.max(deep, q[1] - logHi[1]);
      }
      const grip = tf.apply(place, local('grip'));
      const fmt = r => r && r.mode === 'ik' ? `off ${r.off.toFixed(2)} reach ${r.reach.toFixed(2)} elbow ${r.elbow.toFixed(0)}` : 'clip';
      console.log(`  t${t.toFixed(2)}: R ${fmt(ro.right)} | L ${fmt(ro.left)} | clear ${worst.c.toFixed(2)} (${worst.part}/${worst.box}) | cam ${near.toFixed(1)} px | grip ${r2(grip).join(', ')} (${((FEET - grip[1]) / PX).toFixed(2)} high) | toe under the log ${deep.toFixed(2)} px`);
    }
  }
  console.log('rip turn', RIP_TURN.join(', '), ' kerf anchor', r2(anchors.kerf).join(', '), ' hold', r2(anchors.hold).join(', '));
}
