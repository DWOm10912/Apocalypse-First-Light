// Flip lighter player actions (docs/gameplay/flip_lighter_v1.md 4, 2026-10-20): writes the prop
// player_props/flip_lighter.json and the actions player_actions/item/flip_lighter*.json. No clips: only the prop's
// placement timeline and the hands' targets, solved by the skeleton's IK at run time (as item/crowbar).
//
// Holds (the grip point: the lighter's foot, sitting in the fist; the lighter upright, its broad face to the eye, the lid's
// hinge on the right so the lid flips out to the right):
//   LOW    closed, in front of the belly (chest frame): out of the first-person view unless looking down;
//   READY  open, before the chest (chest frame): where the lid is flicked and the wheel struck, the camera dips to it
//          (client/FlipLighterClient, the camera director);
//   LIT    lit, held out before the chest right of the view, the arm nearly straight (view frame; the flame about 17 deg
//          below and 23 deg right of the view, as a lighter held out to light the way): the flame lights the way.
// Actions (item/flip_lighter_<act>, the item's NBT Act; their times are FlipLighterItem's constants, keep them in step):
//   flip_lighter        no act yet (just taken out): LOW, eased in;
//   flip_lighter_open   LOW -> READY, the lid flicked at 0.30-0.42 s (a small jolt of the hand);
//   flip_lighter_strike READY, the wheel struck 0.10-0.22 s, the left hand cupping the flame 0.0-0.45 s, then out to LIT;
//   flip_lighter_spark  READY, the wheel struck, no flame; the left hand cups and goes;
//   flip_lighter_close  READY -> LOW, the lid shut 0.08-0.18 s;
//   flip_lighter_close_lit LIT -> LOW, the lid shut 0.08-0.18 s (snuffing the flame);
//   flip_lighter_out    LIT -> READY (the flame died: no fuel left, under water).
//   node tools/author-flip-lighter-actions.mjs [--check] [--report]
import fs from 'node:fs';
import path from 'node:path';
import {createRequire} from 'node:module';
import {fileURLToPath} from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const ASSETS = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight');
const r4 = v => v.map(n => Math.round(n * 10000) / 10000);

// ---------------------------------------------------------------- the prop
// Item block units of the flip_lighter mesh as the item renderer draws it (meshes/flip_lighter.aflmesh.json + (0.5,
// 0.14375, 0.5); tools/build-equipment-meshes-v1.mjs LIGHTER, baked: case centre x -0.2109 px, hinge (3.7891, 7.3),
// wheel (1.8391, 10.45), wick (0.6891, 10.42), lid top 11.4 px; 1 px = 1/16).
const item = (x, y, z = 0) => [+(x / 16 + 0.5).toFixed(4), +(y / 16 + 0.14375).toFixed(4), +(z / 16 + 0.5).toFixed(4)];
const SCALE = 0.2;
const PROP = {
  about: 'The flip lighter as the skeleton holds it, first and third person (docs/gameplay/flip_lighter_v1.md 4; written by tools/author-flip-lighter-actions.mjs). Points, bounds: item block units of the flip_lighter mesh as client/FlipLighterRenderer draws it (meshes/flip_lighter.aflmesh.json + (0.5, 0.14375, 0.5)); y up the case, the hinge on +x, the broad faces on z. Scale 0.2 (the lighter 2.3 model px tall: to the 4 px fist as a real lighter to a fist): grip, the palm, 1.4 px below the case foot: the foot just inside the top of the fist, the case and lid stand out of it. flame: the wick top; wheel: the flint wheel; thumb: the lid edge the thumb flicks; cup: where the left hand cups the flame (its left, a little ahead).',
  items: ['flip_lighter'],
  scale: SCALE,
  bounds: [item(-4.011, 0, -1.3), item(4.011, 11.4, 1.3)],
  symmetry: 'z',
  points: {
    grip: item(-0.2109, -1.4 / SCALE),   // 1.4 model px below the case foot (model px = mesh px x scale): the palm, so the foot sits just inside the fist's top
    flame: item(0.6891, 10.42),
    wheel: item(1.8391, 10.45),
    thumb: item(-4.011, 11.0),
    cup: item(-0.2109 - 3.6 / SCALE, 7.0, -0.6 / SCALE),
  },
};

// ---------------------------------------------------------------- holds (grip point; Java model px; rot deg z, y, x)
// The broad face turned toward the eye: about y by 180 (hinge to the player's right) plus the angle to the eye.
const LOW = {frame: 'chest', point: 'grip', pos: [-3.0, 6.8, -4.2], rot: [-4, 205, 0]};
const READY = {frame: 'chest', point: 'grip', pos: [-1.6, 1.8, -7.0], rot: [-8, 200, 0]};
const LIT = {frame: 'view', point: 'grip', pos: [-1.8, 4.9, -4.9], rot: [-4, 203, 0]};
const FLICK = {...READY, rot: [-16, 199, -6]};   // the hand's jolt as the thumb flicks the lid
const STRIKE = {...READY, pos: [READY.pos[0], READY.pos[1] + 0.4, READY.pos[2]], rot: [-6, 204, 0]};   // the thumb rolls the wheel down

const key = (at, pose, blend) => ({at, frame: pose.frame, point: pose.point, pos: r4(pose.pos), rot: r4(pose.rot), blend});
const right = keys => keys.map(([at, target, blend]) => ({at, target, blend, roll: 0}));
const GRIP = [[0, 'prop:grip', 0]];
const CUP_L = (from, leave) => right([[0, 'pose', 0], [from, 'prop:cup', 0.15], [leave, 'pose', 0.3]]);

const action = (about, prop, hands, blend = {in: 0.0, out: 0.25}) => ({
  format_version: 1,
  about,
  layer: 'action',
  mask: 'arms',
  priority: 30,
  first_person: 'body',
  blend,
  view_pitch: [-40, 35],
  prop: {id: 'flip_lighter', keys: prop},
  hands,
  events: [],
});

const ACTIONS = {
  flip_lighter: action('Flip lighter in the main hand, closed, no act yet (just taken out): held before the belly, eased in. docs/gameplay/flip_lighter_v1.md 4; written by tools/author-flip-lighter-actions.mjs.',
      [key(0, LOW, 0)], {right: right(GRIP)}, {in: 0.25, out: 0.25}),
  flip_lighter_open: action('Flip lighter: flicked open (Act open). From before the belly up before the chest (0-0.28 s), the thumb flicks the lid open 0.30-0.42 s with a jolt of the hand; held there open. The own first-person camera dips to it (client/FlipLighterClient).',
      [key(0, LOW, 0), key(0.001, READY, 0.28), key(0.29, FLICK, 0.05), key(0.36, READY, 0.12)], {right: right(GRIP)}),
  flip_lighter_strike: action('Flip lighter: struck and lit (Act strike). Before the chest, the left hand comes to cup the flame (0-0.15 s), the thumb rolls the wheel down 0.10-0.22 s, the flame catches 0.16 s; the left hand goes 0.45 s, the lit lighter goes out before the chest, right of the view (0.35-0.8 s).',
      [key(0, READY, 0), key(0.08, STRIKE, 0.1), key(0.22, READY, 0.1), key(0.35, LIT, 0.45)], {right: right(GRIP), left: CUP_L(0.0, 0.45)}),
  flip_lighter_spark: action('Flip lighter: struck, no flame (Act spark: the strike failed, or no fuel). Before the chest, the left hand cups and goes, the thumb rolls the wheel 0.10-0.22 s; held there open.',
      [key(0, READY, 0), key(0.08, STRIKE, 0.1), key(0.22, READY, 0.1)], {right: right(GRIP), left: CUP_L(0.0, 0.3)}),
  flip_lighter_close: action('Flip lighter: snapped shut (Act close, from open). The lid shut 0.08-0.18 s, the hand back down before the belly (0.15-0.5 s).',
      [key(0, READY, 0), key(0.15, LOW, 0.35)], {right: right(GRIP)}),
  flip_lighter_close_lit: action('Flip lighter: snapped shut while lit (Act close_lit), snuffing the flame. The lid shut 0.08-0.18 s held out, the hand back down before the belly (0.18-0.6 s).',
      [key(0, LIT, 0), key(0.18, LOW, 0.42)], {right: right(GRIP)}),
  flip_lighter_out: action('Flip lighter: the flame died (Act out: no fuel left, under water). From held out back to before the chest, open (0-0.5 s).',
      [key(0, LIT, 0), key(0.05, READY, 0.45)], {right: right(GRIP)}),
};

// ---------------------------------------------------------------- write / check
const files = {[path.join(ASSETS, 'player_props/flip_lighter.json')]: JSON.stringify(PROP, null, 2) + '\n'};
for (const [name, a] of Object.entries(ACTIONS)) files[path.join(ASSETS, 'player_actions/item', name + '.json')] = JSON.stringify(a, null, 2) + '\n';

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

// ---------------------------------------------------------------- report: the plugin's poser (= the game's, afl_rig_check)
if (process.argv.includes('--report')) {
  const core = createRequire(import.meta.url)('./blockbench/afl_rig.js');
  const read = p => JSON.parse(fs.readFileSync(path.join(ASSETS, p), 'utf8'));
  const rig = core.loadRig(read('rigs/afl_humanoid_v1.json'));
  const poser = new core.Poser(rig, core.loadClips(rig, read('animations/player/locomotion.animation.json')));
  const {v3, tf, q4} = core;
  const deg = r => r * 180 / Math.PI;
  for (const [name, a] of Object.entries(ACTIONS)) {
    const act = core.actionFromJson(a, () => PROP, () => null);
    console.log('== ' + name);
    for (const pitch of [0, 40]) for (const t of [0, 0.15, 0.3, 0.45, 0.8]) {
      const res = core.solveFrame(poser, {pitch, action: act, seconds: t, fp: true});
      if (!res.props[0]) continue;   // still blending in
      const read2 = core.readouts(res), place = res.props[0];
      const eye = [0, core.eyeModelY(false), 0];
      const at = n => tf.apply(place, core.propLocal(PROP, n));
      const ang = p => { const d = v3.sub(p, eye); return [deg(Math.atan2(d[1], -d[2])), deg(Math.atan2(-d[0], -d[2]))]; };   // down, right
      const up = v3.norm(v3.sub(at('flame'), at('grip')));
      const hand = res.pose.world[rig.handR], fore = v3.norm(v3.sub(tf.apply(hand, v3.add(rig.pivot[rig.handR], [0, 4, 0])), tf.apply(hand, rig.pivot[rig.handR])));
      const fmt = r => r && r.mode === 'ik' ? `off ${r.off.toFixed(2)} reach ${r.reach.toFixed(2)} wrist ${r.wrist.toFixed(0)} elbow ${r.elbow.toFixed(0)} twist ${r.twist.toFixed(0)}` : 'clip';
      const [fd, fr] = ang(at('flame')), [gd, gr] = ang(at('grip'));
      console.log(`  p${pitch} t${t}: R ${fmt(read2.right)} | L ${fmt(read2.left)} | grip ${gd.toFixed(0)}v ${gr.toFixed(0)}> flame ${fd.toFixed(0)}v ${fr.toFixed(0)}> | lighter vs forearm ${deg(Math.acos(Math.max(-1, Math.min(1, v3.dot(up, fore))))).toFixed(0)}`);
    }
  }
}
