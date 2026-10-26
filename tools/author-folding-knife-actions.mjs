// Folding knife player actions (docs/gameplay/folding_knife_v1.md 5, 2026-10-22): writes the prop
// player_props/folding_knife.json and the actions player_actions/item/folding_knife*.json. No clips: the prop's placement
// timeline and the hands' targets, solved by the skeleton's IK at run time (as item/flip_lighter).
//
// The right hand holds the handle (grip: its middle). The blade turns about the pivot in the renderer
// (client/FoldingKnifeRenderer, item/FoldingKnifeItem#bladeDegrees); the left hand follows it through points on the blade
// written at fixed angles (nick_180: the nail nick of the closed blade; pull_<deg>: a pinch on the blade's face at that
// angle; release: the lock bar on the back of the handle).
// Holds (model axes: x the player's left, y down, z back):
//   LOW    closed, in the fist before the belly (chest frame), the handle ahead and up a little;
//   WORK   before the chest (chest frame), the handle across the body pointing left, the back up: where both hands open
//          and fold the blade (the camera dips to it: client/FoldingKnifeClient);
//   READY  open, low before the right of the lower chest (chest frame: it stays there whatever the look; the first person
//          sees the tip low right when looking ahead, the whole knife when looking down), the elbow bent in at the side,
//          the blade ahead and angled up, the edge down: the forward grip held close (docs/gameplay/folding_knife_v1.md 5.3);
//   DRAW   the fist dropped back to the hip, the tip up;
//   STAB   driven from the hip ahead and up to a belly's height before the player, the arm almost straight.
// Actions (item/folding_knife_<act>, the item's NBT Act; times are FoldingKnifeItem's constants, keep them in step):
//   folding_knife        closed, no act yet (just taken out): LOW, eased in;
//   folding_knife_open   LOW -> WORK (0-0.4 s), the left thumbnail in the nick 0.40 s (the sound's catch), the blade drawn
//                        out 0.42-1.20 s, locked 1.23 s, the left hand away 1.25-1.55 s, out to READY 1.3-1.8 s;
//   folding_knife_close  READY -> WORK (0-0.35 s), the left thumb on the lock bar 0.40 s (the sound's click), the blade
//                        pushed round to 150 deg 0.45-1.08 s, the spring snaps it shut 1.18 s, down to LOW 1.2-1.65 s;
//   folding_knife_stab   READY, down and back to DRAW (0-0.09 s), driven ahead and up from below (0.1-0.2 s), back to
//                        READY by 0.5 s (the attack's 10-tick recharge); played from the vanilla swing's start
//                        (client/skeleton/SkeletonActions).
//   node tools/author-folding-knife-actions.mjs [--check] [--report]
import fs from 'node:fs';
import path from 'node:path';
import {createRequire} from 'node:module';
import {fileURLToPath} from 'node:url';
import {KNIFE} from './build-equipment-meshes-v1.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const ASSETS = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight');
const r4 = v => v.map(n => Math.round(n * 10000) / 10000);
const r2 = v => v.map(n => Math.round(n * 100) / 100);
const RAD = Math.PI / 180, DEG = 180 / Math.PI;
const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
const sub = (a, b) => a.map((v, i) => v - b[i]), mul = (a, k) => a.map(v => v * k);
const dot = (a, b) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
const cross = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
const norm = a => mul(a, 1 / Math.hypot(...a));

// ---------------------------------------------------------------- the prop
// Item block units of the folding_knife mesh as the renderer draws it (meshes/folding_knife.aflmesh.json + (0.5, -0.1596,
// 0.5)); the build's points before its bake shift, so shift them as the bake did (the build's stats: pivot -0.1048, 11.6072).
const VERTICAL = -0.1596;
const SHIFT = [-0.1048 - KNIFE.pivot[0], 11.6072 - KNIFE.pivot[1], 0];
const baked = p => [p[0] + SHIFT[0], p[1] + SHIFT[1], p[2] + SHIFT[2]];
const item = p => [p[0] / 16 + 0.5, p[1] / 16 + VERTICAL, p[2] / 16 + 0.5];
const PIVOT = baked(KNIFE.pivot);
/** A blade point (mesh px, open) with the blade turned this far toward closed (+deg about z, as the renderer). */
const turned = (p, deg) => {
  const c = Math.cos(deg * RAD), s = Math.sin(deg * RAD), dx = p[0] - PIVOT[0], dy = p[1] - PIVOT[1];
  return [PIVOT[0] + dx * c - dy * s, PIVOT[1] + dx * s + dy * c, p[2]];
};
// the pinch on the blade: its face a third of the way out from the pivot
const PINCH = baked([0.35, 15.2, 0.4]);
const SCALE = 0.4;   // the open knife 8.4 model px: a real knife to the 4 px fist, as the lighter's
const points = {
  grip: baked(KNIFE.grip),
  pivot: PIVOT,
  tip: baked(KNIFE.tip),
  butt: baked([0, 0, 0]),
  nick_180: turned(baked(KNIFE.nick), 180),
  release: baked([1.23, 5.4, 0]),
};
for (const deg of [150, 120, 90, 60, 30, 0]) points['pull_' + deg] = turned(PINCH, deg);
const PROP = {
  about: 'The folding knife as the skeleton holds it, first and third person (docs/gameplay/folding_knife_v1.md 5; written by tools/author-folding-knife-actions.mjs). Points, bounds: item block units of the folding_knife mesh as client/FoldingKnifeRenderer draws it (meshes/folding_knife.aflmesh.json + (0.5, -0.1596, 0.5)); y from the butt up the open blade, the back (spring) on +x, the edge on -x, the nail nick on the +z face. Scale 0.4 (the open knife 8.4 model px, as a real knife to the 4 px fist). grip: the handle\'s middle in the fist; pivot, tip, butt; nick_180: the nail nick of the closed blade; pull_<deg>: a pinch on the blade\'s face with the blade turned that far toward closed; release: the lock bar on the back.',
  items: ['folding_knife'],
  scale: SCALE,
  bounds: [r4(item([-1.4, 0, -0.85])), r4(item([1.4, 21.11, 0.85]))],
  symmetry: 'z',
  points: Object.fromEntries(Object.entries(points).map(([n, p]) => [n, r4(item(p))])),
};

// ---------------------------------------------------------------- turns
// the prop's own frame (local = [ix K, -iy K, -iz K]): LU up the blade to the tip, LW toward the edge, LV = LU x LW
const LU = [0, -1, 0], LW = [-1, 0, 0], LV = cross(LU, LW);
/** The key turn (deg, about z then y then x) taking the blade's line to h and its edge toward b. */
function turnFor(h, b) {
  h = norm(h);
  b = norm(sub(b, mul(h, dot(b, h))));
  const v = cross(h, b);
  const R = [0, 1, 2].map(r => [0, 1, 2].map(c => h[r] * LU[c] + b[r] * LW[c] + v[r] * LV[c]));
  const yb = Math.asin(clamp(R[0][2], -1, 1));
  if (Math.abs(R[0][2]) > 0.99999) return [Math.atan2(R[2][1], R[1][1]) * DEG, yb * DEG, 0];
  const xa = Math.atan2(-R[1][2], R[2][2]), zc = Math.atan2(-R[0][1], R[0][0]);
  return [xa * DEG, yb * DEG, zc * DEG];
}
const DOWN = [0, 1, 0];
const hold = (frame, pos, h, b = DOWN) => ({frame, point: 'grip', pos, rot: r2(turnFor(h, b))});
// closed in the fist before the belly: the handle ahead and up, toward the middle
const LOW = hold('chest', [-3.0, 6.6, -4.6], [0.35, -0.45, -1]);
// before the chest, the handle across to the left and a little ahead, the back up: the blade swings out to the left,
// down past the edge side (the nick face toward the player)
const WORK = hold('chest', [-1.2, 2.6, -7.2], [1, 0.12, -0.4]);
// open, low before the right of the lower chest (chest frame: it stays there whatever the look), the elbow in at the
// side, the blade ahead and angled up, the edge down (a forward grip held close: docs/gameplay/folding_knife_v1.md 5.3)
const READY = hold('chest', [-3.4, 4.5, -6.4], [0.15, -0.55, -1]);
// the stab from below: the fist dropped back to the hip, the tip up; then driven ahead and up to the height of a belly
const DRAW = hold('chest', [-4.4, 8.8, -2.8], [0.1, -0.9, -1]);
const STAB = hold('chest', [-2.2, 3.0, -7.6], [0.05, -0.35, -1]);
// the lock: a small jolt of the hand as the blade snaps home
const LOCK = {...WORK, rot: r2(sub(WORK.rot, [0, 0, -4]))};

const key = (at, pose, blend) => ({at, frame: pose.frame, point: pose.point, pos: r4(pose.pos), rot: r4(pose.rot), blend});
const hand = keys => keys.map(([at, target, blend]) => ({at, target, blend, roll: 0}));
const GRIP = [[0, 'prop:grip', 0]];

/** The blade's angle (deg toward closed) at t s into "open" / "close": item/FoldingKnifeItem#bladeDegrees. */
const smooth = u => { u = clamp(u, 0, 1); return u * u * (3 - 2 * u); };
const openDeg = t => 180 * (1 - smooth((t - 0.42) / 0.78));
const closeDeg = t => t < 1.08 ? 150 * smooth((t - 0.45) / 0.63) : 150 + 30 * clamp((t - 1.08) / 0.1, 0, 1) ** 2;
/** When the blade passes deg (s), searched on its curve. */
const when = (curve, deg, from, to) => {
  let best = from;
  for (let t = from; t <= to; t += 0.001) if (Math.abs(curve(t) - deg) < Math.abs(curve(best) - deg)) best = t;
  return Math.round(best * 1000) / 1000;
};
// the left hand following the blade: from one pull point to the next as the blade passes it
const follow = (curve, degs, from, to) => {
  const ts = degs.map(d => when(curve, d, from, to));
  return degs.slice(1).map((d, i) => [ts[i], 'prop:pull_' + d, ts[i + 1] - ts[i]]);
};

const action = (about, prop, hands, blend = {in: 0.0, out: 0.25}) => ({
  format_version: 1,
  about,
  layer: 'action',
  mask: 'arms',
  priority: 30,
  first_person: 'body',
  blend,
  view_pitch: [-40, 35],
  prop: {id: 'folding_knife', keys: prop},
  hands,
  events: [],
});

const OPEN_LEFT = [
  [0, 'pose', 0],
  [0.12, 'prop:nick_180', 0.28],                         // the thumbnail in the nick as the sound catches (0.40 s)
  [0.42, 'prop:pull_150', when(openDeg, 150, 0.42, 1.2) - 0.42],
  ...follow(openDeg, [150, 120, 90, 60, 30, 0], 0.42, 1.2),
  [1.25, 'pose', 0.3],
];
const CLOSE_LEFT = [
  [0, 'pose', 0],
  [0.12, 'prop:release', 0.28],                          // the thumb on the lock bar as it clicks (0.40 s)
  [0.4, 'prop:pull_0', 0.05],
  ...follow(closeDeg, [0, 30, 60, 90, 120, 150], 0.45, 1.08),
  [1.1, 'pose', 0.3],                                    // let go: the spring snaps it shut
];

const ACTIONS = {
  folding_knife: action('Folding knife in the main hand, closed, no act yet (just taken out): held before the belly, eased in. docs/gameplay/folding_knife_v1.md 5; written by tools/author-folding-knife-actions.mjs.',
      [key(0, LOW, 0)], {right: hand(GRIP)}, {in: 0.25, out: 0.25}),
  folding_knife_open: action('Folding knife: opened with both hands (Act open). Up before the chest, the handle across to the left (0-0.4 s); the left thumbnail in the nail nick at 0.40 s, the blade drawn out and round past the edge side 0.42-1.20 s, the left hand following it; the lock clicks at 1.23 s with a small jolt; the left hand away 1.25-1.55 s; the open knife down to the ready hold low before the chest 1.3-1.8 s. The own first-person camera dips to it (client/FoldingKnifeClient).',
      [key(0, LOW, 0), key(0.001, WORK, 0.4), key(1.22, LOCK, 0.03), key(1.25, WORK, 0.06), key(1.32, READY, 0.48)],
      {right: hand(GRIP), left: hand(OPEN_LEFT)}),
  folding_knife_close: action('Folding knife: folded with both hands (Act close). From the ready hold before the chest (0-0.35 s); the left thumb presses the lock bar at 0.40 s, then pushes the blade round 0.45-1.08 s to 150 deg and lets go: the backspring snaps it shut at 1.18 s; the closed knife down before the belly 1.2-1.65 s. The own first-person camera dips to it (client/FoldingKnifeClient).',
      [key(0, READY, 0), key(0.001, WORK, 0.35), key(1.17, LOCK, 0.02), key(1.2, WORK, 0.05), key(1.25, LOW, 0.4)],
      {right: hand(GRIP), left: hand(CLOSE_LEFT)}),
  folding_knife_stab: action('Folding knife: a stab (the melee attack, played from the vanilla swing\'s start: client/skeleton/SkeletonActions). From the ready hold low before the chest, the fist dropped back to the hip with the tip up (0-0.09 s), then driven ahead and up from below to a belly\'s height before the player (0.1-0.2 s), held a moment, back to the ready hold by 0.5 s (the attack\'s 10-tick recharge).',
      [key(0, READY, 0), key(0.001, DRAW, 0.09), key(0.1, STAB, 0.1), key(0.24, READY, 0.26)],
      {right: hand(GRIP)}),
};

// ---------------------------------------------------------------- write / check
const files = {[path.join(ASSETS, 'player_props/folding_knife.json')]: JSON.stringify(PROP, null, 2) + '\n'};
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
  const {v3, tf} = core;
  const times = {
    folding_knife: [0, 0.3],
    folding_knife_open: [0, 0.4, 0.6, 0.8, 1.0, 1.2, 1.4, 1.8],
    folding_knife_close: [0, 0.4, 0.6, 0.8, 1.0, 1.1, 1.3, 1.7],
    folding_knife_stab: [0, 0.09, 0.2, 0.3, 0.5],
  };
  for (const [name, a] of Object.entries(ACTIONS)) {
    const act = core.actionFromJson(a, () => PROP, () => null);
    console.log('== ' + name);
    for (const pitch of [-30, 0, 30, 60]) for (const t of times[name]) {
      const res = core.solveFrame(poser, {pitch, action: act, seconds: t, fp: true});
      if (!res.props[0]) continue;
      const ro = core.readouts(res), place = res.props[0];
      const eye = [0, core.eyeModelY(false), 0];
      const at = n => tf.apply(place, core.propLocal(PROP, n));
      const ang = p => { const d = v3.sub(p, eye); return [DEG * Math.atan2(d[1], -d[2]), DEG * Math.atan2(-d[0], -d[2]), Math.hypot(...d)]; };
      const fmt = r => r && r.mode === 'ik' ? `off ${r.off.toFixed(2)} reach ${r.reach.toFixed(2)} wrist ${r.wrist.toFixed(0)} elbow ${r.elbow.toFixed(0)}` : (r ? r.mode : '-');
      const [gd, gr, gl] = ang(at('grip')), [td, tr] = ang(at('tip'));
      const dir = v3.norm(v3.sub(at('tip'), at('grip')));
      console.log(`  p${pitch} t${t}: R ${fmt(ro.right)} | L ${fmt(ro.left)} | grip ${gd.toFixed(0)}v ${gr.toFixed(0)}> ${gl.toFixed(1)}px | tip ${td.toFixed(0)}v ${tr.toFixed(0)}> | blade dir ${r2(dir)}`);
    }
  }
}
