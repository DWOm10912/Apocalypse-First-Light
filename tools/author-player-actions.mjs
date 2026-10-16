// Player actions V1 (docs/dev/player_actions_v1_plan.md 3): writes the action clips, one file per group,
// src/main/resources/assets/apocalypse_firstlight/animations/player/<group>.animation.json (Bedrock 1.8.0, clip keys
// <group>.<name>, the format Blockbench exports, so a clip can later be replaced by a hand-keyed one of the same name).
// The clips only carry the body: where the hands go is the action definition's (player_actions/<group>/<name>.json),
// solved by IK at run time. Conventions as tools/author-player-locomotion.mjs: rotations are the Java ModelPart angles in
// degrees (x: + = limb back / torso forward, y: + = turn right, z: + = right limb out), positions Bedrock px (y up).
// Run: node tools/author-player-actions.mjs [--check]
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const assets = path.join(root, 'src/main/resources/assets/apocalypse_firstlight');
const rig = JSON.parse(fs.readFileSync(path.join(assets, 'rigs/afl_humanoid_v1.json'), 'utf8'));
const pivot = name => rig.bones.find(b => b.name === name).pivot;   // Bedrock px

const DEG = 180 / Math.PI;
const r3 = v => v.map(n => Math.round(n * 1000) / 1000);
const THIGH = pivot('thigh_r')[1] - pivot('shin_r')[1];
const SHIN = pivot('shin_r')[1] - pivot('foot_r')[1];

// Feet planted (as the locomotion's crouch): the leg's local x angles (thigh, shin, foot; deg) for the hip moved down by
// drop and back by back px over an ankle ankleForward px ahead of the rest hip, the pelvis pitched forward by pelvisPitch.
function plantedLeg(drop, back, pelvisPitch, ankleForward = 0) {
  const hip = [drop, back], ankle = [THIGH + SHIN, -ankleForward];
  const dy = ankle[0] - hip[0], dz = ankle[1] - hip[1], d = Math.hypot(dy, dz);
  const reach = Math.min(d, THIGH + SHIN - 1e-6);
  const a = Math.acos((THIGH * THIGH + reach * reach - SHIN * SHIN) / (2 * THIGH * reach));
  const thigh = Math.atan2(dz, dy) - a;     // knee forward
  const knee = [hip[0] + THIGH * Math.cos(thigh), hip[1] + THIGH * Math.sin(thigh)];
  const shin = Math.atan2(ankle[1] - knee[1], ankle[0] - knee[0]);
  return [thigh * DEG - pelvisPitch, (shin - thigh) * DEG, -shin * DEG];
}

// A held pose: one key (the runtime samples it at any time).
const held = bones => ({loop: true, animation_length: 1, bones: Object.fromEntries(Object.entries(bones).map(([bone, ch]) =>
    [bone, Object.fromEntries(Object.entries(ch).map(([k, v]) => [k, r3(v)]))]))});

// A keyed clip played once: {bone: {rotation: {seconds: [x, y, z]}}}, eased (catmullrom) between the keys.
const keyed = (length, bones) => ({loop: false, animation_length: length, bones: Object.fromEntries(Object.entries(bones).map(([bone, ch]) =>
    [bone, Object.fromEntries(Object.entries(ch).map(([k, keys]) => [k, Object.fromEntries(Object.entries(keys).map(([t, v]) =>
        [Number(t).toFixed(2), {post: r3(v), lerp_mode: 'catmullrom'}]))]))]))});

const groups = {item: {}, machine: {}, tool: {}};

// Clinical thermometer under the armpit: the right hand goes across to the left armpit (the action's IK); the left arm
// presses in against the side and its forearm comes across the belly, holding the thermometer in.
groups.item['item.thermometer_armpit'] = held({
  upper_arm_l: {rotation: [-6, 0, -1]},
  forearm_l: {rotation: [-30, 0, 0]},
});

// Fuel nozzle in an opening (the player is crouched by NozzleFillView): the torso leans toward it; the parameter clip
// leans further for a low opening (a jerry can on the ground).
groups.item['item.nozzle_fill'] = held({chest: {rotation: [12, 0, 0]}});
groups.item['item.nozzle_fill_low'] = held({chest: {rotation: [58, 0, 0]}});

// Jerry can pour (the parameter clip, client/skeleton/SkeletonActions: how low the fill opening is, coming in with the
// step): the torso leans over the opening.
groups.item['item.jerry_can_pour_lean'] = held({chest: {rotation: [40, 0, 0]}});

// Recoil starter (portable diesel generator, handle 0.35 block over the ground): feet apart, left foot ahead, knees bent,
// back bent over the set; the left hand presses on the frame, the right on the T handle (the action's IK). The yank
// (parameter clip, by the handle's travel): the torso comes up and turns right, the right shoulder back.
const PULL = {drop: 3, back: 2, pelvisPitch: 35, chestPitch: 35, front: 2, rear: -2};
const pullFront = plantedLeg(PULL.drop, PULL.back, PULL.pelvisPitch, PULL.front);
const pullRear = plantedLeg(PULL.drop, PULL.back, PULL.pelvisPitch, PULL.rear);
const pullLegs = {
  thigh_r: {rotation: [pullRear[0], 0, 6]}, thigh_l: {rotation: [pullFront[0], 0, -6]},
  shin_r: {rotation: [pullRear[1], 0, 0]}, shin_l: {rotation: [pullFront[1], 0, 0]},
  foot_r: {rotation: [pullRear[2], 0, 0]}, foot_l: {rotation: [pullFront[2], 0, 0]},
};
groups.machine['machine.generator_pull'] = held({
  pelvis: {rotation: [PULL.pelvisPitch, 0, 0], position: [0, -PULL.drop, PULL.back]},
  chest: {rotation: [PULL.chestPitch, 0, 0]},
  ...pullLegs,
});
groups.machine['machine.generator_pull_yank'] = held({chest: {rotation: [PULL.chestPitch - 22, 20, 0]}});

// Crowbar smash (interaction/CrowbarSmashTimeline: impact 0.6 s, done 1.65 s): the chest winds up turned right and leaning
// back while the right hand goes up behind the shoulder (the action's IK), swings through forward and left at the impact,
// and settles; the left arm comes forward for balance.
groups.tool['tool.crowbar_smash'] = keyed(1.65, {
  chest: {rotation: {0: [0, 0, 0], 0.4: [-5, 6, 0], 0.6: [16, 2, 0], 0.75: [14, 2, 0], 1.3: [9, 1, 0], 1.65: [0, 0, 0]}},
  upper_arm_l: {rotation: {0: [0, 0, 0], 0.4: [-35, 0, -10], 0.6: [-20, 0, -5], 1.3: [0, 0, 0], 1.65: [0, 0, 0]}},
});

const outputs = Object.entries(groups).map(([group, clips]) => [
  path.join(assets, `animations/player/${group}.animation.json`),
  JSON.stringify({format_version: '1.8.0', animations: clips}, null, 1).replace(/\[\s+(\S+),\s+(\S+),\s+(\S+)\s+\]/g, '[$1, $2, $3]') + '\n',
  clips,
]);
const legText = l => l.map(n => n.toFixed(1)).join(' / ');
console.log(`generator pull legs (thigh / knee / foot deg): front (left) ${legText(pullFront)}, rear (right) ${legText(pullRear)}`);
if (process.argv.includes('--check')) {
  let ok = true;
  for (const [out, json] of outputs) {
    const same = fs.existsSync(out) && fs.readFileSync(out, 'utf8') === json;
    console.log(`${path.relative(root, out)}: ${same ? 'up to date' : 'OUT OF DATE'}`);
    ok &&= same;
  }
  process.exit(ok ? 0 : 1);
}
for (const [out, json, clips] of outputs) {
  fs.mkdirSync(path.dirname(out), {recursive: true});
  fs.writeFileSync(out, json);
  console.log(`wrote ${path.relative(root, out)}: ${Object.keys(clips).join(', ')}`);
}
