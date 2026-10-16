// Player skeleton V1 P1 (docs/dev/player_skeleton_v1_design.md 10.1, 11): writes the locomotion clips
// src/main/resources/assets/apocalypse_firstlight/animations/player/locomotion.animation.json (Bedrock 1.8.0, the format
// Blockbench exports, so the clips open and can be refined in the template). Authored here from formulas because the
// moves are cyclic and the crouch needs feet planted (two-bone leg IK in the side plane).
// Conventions: rotations are the Java ModelPart angles in degrees (x: + = limb back / torso forward, y: + = turn right,
// z: + = right limb out); positions are Bedrock px (y up). Stride phase 0 = right leg back, left leg forward (as vanilla's
// cos(limbSwing * 0.6662)); the runtime samples walk / run / crouch_walk by phase, the others by time.
// Run: node tools/author-player-locomotion.mjs [--check]
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const assets = path.join(root, 'src/main/resources/assets/apocalypse_firstlight');
const out = path.join(assets, 'animations/player/locomotion.animation.json');
const rig = JSON.parse(fs.readFileSync(path.join(assets, 'rigs/afl_humanoid_v1.json'), 'utf8'));
const pivot = name => rig.bones.find(b => b.name === name).pivot;   // Bedrock px

const TAU = Math.PI * 2, DEG = 180 / Math.PI;
const r3 = v => v.map(n => Math.round(n * 1000) / 1000);
const pos = n => Math.max(0, n);

// Rig lengths (px): hip -> knee -> ankle, as the rig says.
const THIGH = pivot('thigh_r')[1] - pivot('shin_r')[1];
const SHIN = pivot('shin_r')[1] - pivot('foot_r')[1];
const HIP_Y = pivot('thigh_r')[1];
const ANKLE_Y = pivot('foot_r')[1], SOLE_FRONT = 2;         // ankle over the sole; sole front edge ahead of the ankle

// Feet planted: the leg's angles for a hip moved by (drop, back) px (Java y down, z back) over an ankle that stays put,
// with the pelvis pitched forward by pelvisPitch. Knee forward. Returns local x angles of thigh, shin, foot (deg).
// heel > 0 raises the heel by that many degrees (foot toes down), pivoting on the sole's front edge.
function plantedLeg(drop, back, pelvisPitch, ankleForward = 0, heel = 0) {
  const hip = [HIP_Y - HIP_Y + drop, back];                 // [y, z] relative to the rest hip
  const h = heel / DEG, lift = ANKLE_Y * Math.cos(h) + SOLE_FRONT * Math.sin(h) - ANKLE_Y;
  const ankle = [THIGH + SHIN - lift, -ankleForward];
  const dy = ankle[0] - hip[0], dz = ankle[1] - hip[1], d = Math.hypot(dy, dz);
  const reach = Math.min(d, THIGH + SHIN - 1e-6);
  const a = Math.acos((THIGH * THIGH + reach * reach - SHIN * SHIN) / (2 * THIGH * reach));
  const base = Math.atan2(dz, dy);          // angle of hip -> ankle from straight down (+ = back)
  const thigh = base - a;                   // knee forward
  const knee = [hip[0] + THIGH * Math.cos(thigh), hip[1] + THIGH * Math.sin(thigh)];
  const shin = Math.atan2(ankle[1] - knee[1], ankle[0] - knee[0]);
  return [thigh * DEG - pelvisPitch, (shin - thigh) * DEG, -shin * DEG + heel];
}

// Vanilla eye drop for the crouch: eye 1.62 -> 1.27 blocks, the renderer already lowers the model 0.125: the rest
// is the body's own (model px = blocks * 16 / 0.9375).
const EYE_SOCKET = rig.sockets.eye.pos;                     // Bedrock px on the head
const EYE_OVER_NECK = EYE_SOCKET[1] - pivot('head')[1];
const NECK_OVER_WAIST = pivot('head')[1] - pivot('chest')[1], WAIST_OVER_HIP = pivot('chest')[1] - pivot('pelvis')[1];
const CROUCH_EYE_DROP = (1.62 - 1.27 - 0.125) * 16 / 0.9375;
// Split stance (one foot ahead, one behind on the ball of the foot) instead of both feet together: the thighs stay out
// of the torso and the hips need not go far back. The walk keeps the feet side by side.
const CROUCH = {pelvisPitch: 10, chestPitch: 20, back: 3, front: 3, rear: 3.5, heel: 40, walkBack: 1.5};   // front / rear: ankles from the hips
{
  const p = CROUCH.pelvisPitch / DEG, t = (CROUCH.pelvisPitch + CROUCH.chestPitch) / DEG;
  // eye height over the rest: hip drop plus the torso's fold (the head stays level, looking ahead)
  const fold = WAIST_OVER_HIP * (1 - Math.cos(p)) + NECK_OVER_WAIST * (1 - Math.cos(t));
  CROUCH.drop = CROUCH_EYE_DROP - fold;
  CROUCH.eyeForward = WAIST_OVER_HIP * Math.sin(p) + NECK_OVER_WAIST * Math.sin(t) - CROUCH.back;
}

const clips = {};
const sampled = (length, steps, fn) => {
  // fn(phase 0..1) -> {bone: {rotation: [x, y, z], position: [x, y, z]}}; loops close on the first key.
  const bones = {};
  for (let i = 0; i <= steps; i++) {
    const phase = i / steps, t = (phase * length).toFixed(4).replace(/0+$/, '').replace(/\.$/, '.0');
    for (const [bone, channels] of Object.entries(fn(phase % 1))) {
      for (const [channel, value] of Object.entries(channels)) {
        ((bones[bone] ??= {})[channel] ??= {})[t] = r3(value);
      }
    }
  }
  return bones;
};
const constant = values => Object.fromEntries(Object.entries(values).map(([bone, ch]) =>
    [bone, Object.fromEntries(Object.entries(ch).map(([k, v]) => [k, r3(v)]))]));

const HANG = {upper: 4, fore: -8};                          // relaxed arms: a little out, elbows a little bent

// Standing idle: breathing on the chest, arms hanging.
clips['locomotion.idle'] = {loop: true, animation_length: 4, bones: sampled(4, 16, ph => {
  const breath = Math.sin(ph * TAU);
  return {
    chest: {rotation: [0.6 * breath, 0, 0]},
    upper_arm_r: {rotation: [0.8 * breath, 0, HANG.upper + 0.5 * (1 + breath)]},
    upper_arm_l: {rotation: [0.8 * breath, 0, -HANG.upper - 0.5 * (1 + breath)]},
    forearm_r: {rotation: [HANG.fore, 0, 0]}, forearm_l: {rotation: [HANG.fore, 0, 0]},
    thigh_r: {rotation: [0, 0, 1.5]}, thigh_l: {rotation: [0, 0, -1.5]},
  };
})};

function stride(o) {
  return ph => {
    const c = Math.cos(ph * TAU), s = Math.sin(ph * TAU);
    const swingR = pos(s), swingL = pos(-s);                // right leg swings forward in the first half
    const kneeR = o.stance + o.knee * swingR, kneeL = o.stance + o.knee * swingL;
    const thighR = o.thigh * c + o.thighBias, thighL = -o.thigh * c + o.thighBias;
    return {
      pelvis: {rotation: [o.lean * 0.3, o.hips * c, 0], position: [0, -o.bob * (1 + Math.cos(2 * ph * TAU)) / 2, 0]},
      chest: {rotation: [o.lean * 0.7, -o.hips * 1.5 * c, 0]},
      thigh_r: {rotation: [thighR, 0, 1]}, thigh_l: {rotation: [thighL, 0, -1]},
      shin_r: {rotation: [kneeR, 0, 0]}, shin_l: {rotation: [kneeL, 0, 0]},
      foot_r: {rotation: [-0.35 * kneeR + 0.3 * pos(c) * o.toe, 0, 0]},
      foot_l: {rotation: [-0.35 * kneeL + 0.3 * pos(-c) * o.toe, 0, 0]},
      upper_arm_r: {rotation: [-o.arm * c, 0, HANG.upper]}, upper_arm_l: {rotation: [o.arm * c, 0, -HANG.upper]},
      forearm_r: {rotation: [o.fore - o.foreSwing * pos(c), 0, 0]}, forearm_l: {rotation: [o.fore - o.foreSwing * pos(-c), 0, 0]},
    };
  };
}

clips['locomotion.walk'] = {loop: true, animation_length: 1, bones: sampled(1, 16, stride(
    {thigh: 30, thighBias: 0, knee: 40, stance: 5, toe: 20, arm: 22, fore: -10, foreSwing: 14, hips: 4, lean: 3, bob: 0.6}))};
clips['locomotion.run'] = {loop: true, animation_length: 1, bones: sampled(1, 16, stride(
    {thigh: 45, thighBias: -5, knee: 85, stance: 15, toe: 25, arm: 35, fore: -70, foreSwing: 15, hips: 6, lean: 8, bob: 1.0}))};

// Crouch: hips down, pelvis and chest folded forward, feet planted; standing still the left foot is ahead (flat) and
// the right one behind with the heel up, walking the feet are side by side and the stride swings them.
const frontLeg = plantedLeg(CROUCH.drop, CROUCH.back, CROUCH.pelvisPitch, CROUCH.front - CROUCH.back);
const rearLeg = plantedLeg(CROUCH.drop, CROUCH.back, CROUCH.pelvisPitch, -CROUCH.rear - CROUCH.back, CROUCH.heel);
const walkLeg = plantedLeg(CROUCH.drop, CROUCH.walkBack, CROUCH.pelvisPitch);
const crouchBody = back => ({
  pelvis: {rotation: [CROUCH.pelvisPitch, 0, 0], position: [0, -CROUCH.drop, back]},
  chest: {rotation: [CROUCH.chestPitch, 0, 0]},
  upper_arm_r: {rotation: [-12, 0, HANG.upper + 2]}, upper_arm_l: {rotation: [-12, 0, -HANG.upper - 2]},
  forearm_r: {rotation: [-20, 0, 0]}, forearm_l: {rotation: [-20, 0, 0]},
});
const crouchLegs = (r, l) => ({
  thigh_r: {rotation: [r[0], 0, 2]}, thigh_l: {rotation: [l[0], 0, -2]},
  shin_r: {rotation: [r[1], 0, 0]}, shin_l: {rotation: [l[1], 0, 0]},
  foot_r: {rotation: [r[2], 0, 0]}, foot_l: {rotation: [l[2], 0, 0]},
});
const crouchIdle = {...crouchBody(CROUCH.back), ...crouchLegs(rearLeg, frontLeg)};
const crouchWalk = {...crouchBody(CROUCH.walkBack), ...crouchLegs(walkLeg, walkLeg)};
clips['locomotion.crouch_idle'] = {loop: true, animation_length: 4, bones: constant(crouchIdle)};
clips['locomotion.crouch_walk'] = {loop: true, animation_length: 1, bones: sampled(1, 16, ph => {
  const c = Math.cos(ph * TAU), s = Math.sin(ph * TAU);
  const b = JSON.parse(JSON.stringify(crouchWalk));
  b.thigh_r.rotation[0] += 18 * c; b.thigh_l.rotation[0] -= 18 * c;
  b.shin_r.rotation[0] += 25 * pos(s); b.shin_l.rotation[0] += 25 * pos(-s);
  b.foot_r.rotation[0] -= 8 * pos(s); b.foot_l.rotation[0] -= 8 * pos(-s);
  b.upper_arm_r.rotation[0] -= 10 * c; b.upper_arm_l.rotation[0] += 10 * c;
  b.pelvis.position[1] -= 0.3 * (1 + Math.cos(2 * ph * TAU)) / 2;
  return b;
})};

// Step turn (the pelvis catching up with the chest in about 0.25 s): two small steps, right then left. The runtime adds
// it on top of the current legs (its change from the first key), so it also works crouched.
clips['locomotion.turn_step'] = {loop: false, animation_length: 0.5, bones: sampled(0.5, 10, ph => {
  const r = ph < 0.5 ? Math.sin(ph * 2 * Math.PI) : 0, l = ph >= 0.5 ? Math.sin((ph - 0.5) * 2 * Math.PI) : 0;
  return {
    thigh_r: {rotation: [-14 * r, 0, 1.5]}, shin_r: {rotation: [26 * r, 0, 0]}, foot_r: {rotation: [-8 * r, 0, 0]},
    thigh_l: {rotation: [-14 * l, 0, -1.5]}, shin_l: {rotation: [26 * l, 0, 0]}, foot_l: {rotation: [-8 * l, 0, 0]},
  };
})};

const json = JSON.stringify({format_version: '1.8.0', animations: clips}, null, 1)
    .replace(/\[\s+(\S+),\s+(\S+),\s+(\S+)\s+\]/g, '[$1, $2, $3]') + '\n';
const legText = l => l.map(n => n.toFixed(1)).join(' / ');
console.log(`crouch: hips down ${CROUCH.drop.toFixed(2)} px, back ${CROUCH.back} px (walk ${CROUCH.walkBack}); eye forward `
    + `of the hips ${CROUCH.eyeForward.toFixed(2)} px before the socket; thigh / knee / foot deg: front (left) `
    + `${legText(frontLeg)}, rear (right) ${legText(rearLeg)}, walk ${legText(walkLeg)}`);
if (process.argv.includes('--check')) {
  const same = fs.existsSync(out) && fs.readFileSync(out, 'utf8') === json;
  console.log(same ? 'locomotion clips up to date' : 'locomotion clips OUT OF DATE');
  process.exit(same ? 0 : 1);
}
fs.mkdirSync(path.dirname(out), {recursive: true});
fs.writeFileSync(out, json);
console.log(`wrote ${path.relative(root, out)}: ${Object.keys(clips).join(', ')}`);
