// Authoring sheet for the splint V2 actions and sitting on the ground (docs/gameplay/splint_animation_v2_plan.md), from the
// approved concept (design/health/splint_animation_v1/src/concept_leg.cjs, concept_arm.cjs): key poses by hand below, the
// body by FK, the legs on the ground by two-bone IK, the palms by the AFL Rig core's arm IK (a dummy prop per hand), all baked
// into the clips as FK (the actions hold the body: PlayerAction#holdBody, so the look neither twists nor bends the chest and
// the keyed hands stay on the leg / forearm).
//   node author_splint_v2.cjs [--write]   -> prints each key's IK misses and the seated eye; --write writes:
//   animations/player/body.animation.json     body.sit_down (1.25 s), body.sit, body.stand_up (1.0 s)
//   animations/player/medical.animation.json  medical.splint_leg (6 s, sat), medical.splint_arm (8 s, standing)
//   player_actions/body/{sit_down,sit,stand_up}.json, player_actions/item/{splint_leg,splint_arm}.json
// All keyed with the LEFT leg / forearm the broken one: client/skeleton/GroundSitClient and SkeletonActions mirror them for
// the right. Java model px: y down, the ground at 24, the front -z, the body's right -x.
const L = require('./lib.cjs');
const {fs, path, core, v3, q4, rad, A, rig, armFk, fileEuler} = L;
const WRITE = process.argv.includes('--write');
const P = require('./pose_lib.cjs');   // the legs, the body by FK, the palms by IK baked to FK, the seat
const {ID, bendOf, fullKey, solveArms, clipOf, SEAT, REST, STAND, kneeUp} = P;
const {mul} = q4;
const SIT_LEN = 1.25, STAND_LEN = 1.0;
const SIT_DOWN = [
    Object.assign({t: 0}, STAND),
    // weight on the right leg, the right hand back toward the ground, the left leg slid out ahead, heel down
    {t: 0.35, view: 15, drop: 3, zoff: 0.5, tip: 14, legR: {ik: [-1.9, 22, 0.3], hint: [0, -0.3, -1]}, legL: {foot: 25}, palmR: [-7, 16, 2], palmL: [6, 12, -5]},
    {t: 0.7, view: 25, drop: 7, zoff: 1.5, tip: 26, legR: {ik: [-1.9, 22, 0.2], hint: [0, -0.3, -1]}, legL: {foot: 30}, palmR: [-6.2, 21.8, 2.5], palmL: [5.5, 13, -7]},
    // down, leaning back on the right hand a moment, then the forearm onto the knee
    Object.assign({t: 0.95}, SEAT, {view: 20, tip: 6, lean: -4, palmR: [-6.2, 23.6, 3.5], palmL: [3.6, 19.6, -0.5]}),
    Object.assign({}, REST, {t: SIT_LEN}),
];
const SITTING = [Object.assign({}, REST, {t: 0}), Object.assign({}, REST, {t: 2})];
const STAND_UP = [
    Object.assign({}, REST, {t: 0}),
    // the right foot pulled in, both hands on the right knee, the left leg kept straight, heel down
    Object.assign({t: 0.2}, SEAT, {view: 25, tip: 22, lean: 6, legR: kneeUp(-1.5, -2.2), palmR: [-2.6, 16.6, -4.2], palmL: [-0.8, 16.8, -4.0]}),
    {t: 0.5, view: 15, drop: 6, zoff: 0.8, tip: 32, lean: 2, legR: {ik: [-2.2, 22, -1.5], hint: [0, -0.6, -1]}, legL: {foot: 28}, palmR: [-2.8, 15.5, -3.6], palmL: [-1.0, 15.7, -3.4]},
    {t: 0.75, view: 5, drop: 2, zoff: 0.2, tip: 12, legR: {ik: [-1.9, 22, -0.8], hint: [0, -0.3, -1]}, legL: {foot: 15}, armR: q4.x(rad(-15)), armL: q4.x(rad(-12))},
    Object.assign({t: STAND_LEN}, STAND),
];

// ------------------------------------------------------------------ the leg's splint (sat; the concept's B-D, 6 s)
const LEG_LEN = 6.0, LEG_SLATS_AT = 0.9, LEG_TIES = [1.45, 2.7, 3.95], LEG_TIE_TURN = 0.65;
const tieZ = s => s - 10;                                   // a tie s px from the sole on the leg out straight (hip 2 px back)
const LEG = [Object.assign({}, REST, {t: 0, view: 55})];
const legKey = (t, k) => LEG.push(Object.assign({}, SEAT, k, {t}));
// B. the splint laid on the shin (the right hand carries it: hands keyed to the prop meanwhile), opened to both sides
legKey(0.35, {view: 65, tip: 12, lean: 0, palmR: [1.6, 16.5, -5.5], palmL: [3.6, 19.6, -1.5]});
legKey(0.7, {view: 75, tip: 14, lean: 0, palmR: [1.2, 18.8, -5.8], palmL: [2.8, 18.8, -5.0]});
legKey(1.05, {view: 75, tip: 14, lean: 0, palmR: [-1.0, 20.3, -5.8], palmL: [4.8, 20.3, -5.4]});
legKey(1.25, {view: 75, tip: 14, lean: 0, palmR: [-0.9, 20.8, -5.6], palmL: [4.7, 20.8, -5.2]});
// C. three ties, ankle to knee: the right hand pushes the strip under the leg from the inside, the left takes it on the
// outside, both ends up over the outer top, crossed and pulled tight
const LEG_TIE_POSE = [{tip: 25, lean: 4, view: 78, s: 1.6}, {tip: 19, lean: 2, view: 76, s: 4.2}, {tip: 12, lean: 0, view: 72, s: 6.9}];
LEG_TIES.forEach((t0, i) => {
    const T = LEG_TIE_POSE[i], z = tieZ(T.s), base = {tip: T.tip, lean: T.lean, view: T.view};
    const hold = z < -6 ? z + 1.8 : z - 1.8;
    legKey(t0, Object.assign({}, base, {palmR: [-0.9, 22.6, z], palmL: [4.6, 20.6, hold]}));
    legKey(t0 + 0.35, Object.assign({}, base, {palmR: [-0.7, 22.7, z], palmL: [4.8, 22.6, z]}));
    legKey(t0 + 0.65, Object.assign({}, base, {palmR: [3.0, 19.0, z - 0.6], palmL: [4.4, 19.1, z + 0.6]}));
    legKey(t0 + 0.9, Object.assign({}, base, {palmR: [3.2, 19.1, z - 2.4], palmL: [4.4, 19.2, z + 2.2]}));
    legKey(t0 + 1.1, Object.assign({}, base, {palmR: [2.2, 18.3, z - 1.2], palmL: [4.2, 18.3, z + 1.0]}));
});
// D. the hands on the thigh, the toes moved twice (blood flow), then sat back as before
const CHK = {view: 70, tip: 6, lean: 0, palmR: [0.4, 19.6, -1.8], palmL: [3.6, 19.6, -1.0]};
legKey(5.3, CHK);
legKey(5.45, Object.assign({}, CHK, {legL: {dir: [0, 0, -1], foot: 58}}));
legKey(5.6, Object.assign({}, CHK, {legL: {dir: [0, 0, -1], foot: 88}}));
legKey(5.75, Object.assign({}, CHK, {legL: {dir: [0, 0, -1], foot: 58}}));
LEG.push(Object.assign({}, REST, {t: LEG_LEN, view: 55}));

// ------------------------------------------------------------------ the forearm's splint (standing, 8 s, upper body)
const ARM_LEN = 8.0, ARM_SLATS_AT = 1.3, ARM_TIES = [2.0, 3.4, 4.8], ARM_TIE_TURN = 0.55;
// the left arm (chest frame directions): low = across the belly, a hand's width out; up = raised to the mouth
const POSE_L = {
    low: {u: [-0.1, 0.42, -0.9], f: [-0.62, 0.12, -0.78]},
    up1: {u: [0.05, 0.45, -1], f: [-1, -0.75, -0.15]},
    up2: {u: [-0.1, 0.3, -1], f: [-1, -0.8, 0.1]},
    up3: {u: [-0.3, 0.1, -1], f: [-1, -0.7, 0.3]},
    close: {u: [0.1, 1, -0.3], f: [-0.9, -0.1, -0.5]},
};
function armKeyEulers(k) {
    const e = {chest: fileEuler(q4.x(rad((k.lean || 0) + bendOf(k.view))))};
    const a = k.armL ? POSE_L[k.armL] : null;
    if (a) {
        const fk = armFk(a.u, a.f, null, 0);
        Object.assign(e, {upper_arm_l: fileEuler(fk.upper), forearm_l: fileEuler(fk.fore), hand_l: fileEuler(mul(fk.hand, q4.x(rad(k.wiggle || 0))))});
    } else Object.assign(e, {upper_arm_l: [0, 0, 0], forearm_l: [0, 0, 0], hand_l: [0, 0, 0]});
    return e;
}
/** A point round the posed left forearm: s px back from its hand end, angle a (0 top, 90 out front, 180 under, 270 toward the body), r px off its axis. */
function around(res, s, a, r) {
    const P = res.pose;
    const cap = P.point(rig.handL, [6, 12, 0]), elbow = P.point(rig.forearmL, [6, 6, 0]);
    const F = v3.norm(v3.sub(elbow, cap));
    const UP = v3.norm(v3.sub([0, -1, 0], v3.mul(F, -F[1])));
    let OUTV = v3.norm(v3.cross(F, UP));
    if (OUTV[2] > 0) OUTV = v3.mul(OUTV, -1);
    const dir = v3.add(v3.mul(UP, Math.cos(rad(a))), v3.mul(OUTV, Math.sin(rad(a))));
    return v3.add(v3.add(cap, v3.mul(F, s)), v3.mul(dir, r));
}
function armKey(k) {
    const eulers = armKeyEulers(k);
    const s = solveArms(eulers, null, 'upper', k, res => ({R: k.at ? around(res, ...k.at) : null}));
    if (!k.at) Object.assign(s.arms, {upper_arm_r: [0, 0, 0], forearm_r: [0, 0, 0], hand_r: [0, 0, 0]});
    return {eulers: Object.assign(eulers, s.arms), pos: null, res: s.res, miss: s.miss};
}
const ARM = [];
const aKey = (t, k) => ARM.push(Object.assign({t}, k));
const LOW = {armL: 'low', view: 50, lean: 0};
aKey(0.0, {view: 0});
aKey(0.6, Object.assign({}, LOW, {at: [4, 0, 4.5]}));
// B. the splint (both slats): on the palm side and the back, pressed together (the right hand carries it to 1.3 s)
aKey(1.0, Object.assign({}, LOW, {at: [4, 260, 3.4]}));
aKey(1.3, Object.assign({}, LOW, {at: [4, 70, 2.8]}));
aKey(1.65, Object.assign({}, LOW, {at: [4, 20, 3.0]}));
// C. three ties, wrist to elbow: under, round the outside, over the top; the forearm up to the mouth, the teeth hold one end
// and the right hand pulls the other; back down
const ARM_TIE_POSE = [{s: 1.6, up: 'up1'}, {s: 4.0, up: 'up2'}, {s: 6.5, up: 'up3'}];
ARM_TIES.forEach((t0, i) => {
    const T = ARM_TIE_POSE[i];
    aKey(t0, Object.assign({}, LOW, {at: [T.s, 215, 2.4]}));
    aKey(t0 + 0.3, Object.assign({}, LOW, {at: [T.s, 70, 2.6]}));
    aKey(t0 + 0.55, Object.assign({}, LOW, {view: 40, at: [T.s, 10, 3.0]}));
    aKey(t0 + 0.8, {armL: T.up, view: 20, lean: 2, at: [T.s, 60, 5.0]});
    aKey(t0 + 1.0, {armL: T.up, view: 20, lean: 2, at: [T.s, 70, 6.0]});
    aKey(t0 + 1.25, Object.assign({}, LOW, {at: [T.s, 30, 4.0]}));
});
// D. the fingers moved (the hand flexes twice), the right hand under the forearm
for (const [t, w] of [[6.2, 0], [6.35, 18], [6.5, -8], [6.65, 18], [6.8, 0]]) aKey(t, Object.assign({}, LOW, {at: [5, 215, 2.4], wiggle: w}));
// E. carried close to the body a moment, then let down
aKey(7.3, {armL: 'close', view: 20, lean: 2, at: [4, 180, 3.0]});
aKey(ARM_LEN, {view: 0});

// ------------------------------------------------------------------ solve, report
const sitDown = clipOf(SIT_DOWN, SIT_LEN, fullKey), sitting = clipOf(SITTING, 2, fullKey), standUp = clipOf(STAND_UP, STAND_LEN, fullKey);
const leg = clipOf(LEG, LEG_LEN, fullKey), arm = clipOf(ARM, ARM_LEN, armKey);
console.log('sit_down\n' + sitDown.rows.join('\n') + '\nsit\n' + sitting.rows.join('\n') + '\nstand_up\n' + standUp.rows.join('\n'));
console.log('splint_leg\n' + leg.rows.join('\n') + '\nsplint_arm\n' + arm.rows.join('\n'));
// the seated eye (ActionPose#cameraEye: the posed neck, the eye's rest offset turned with the root only) over the ground
const rest = fullKey(REST);
const eyeRest = rig.sockets.eye.pos;
const neck = rest.res.pose.point(rig.head, rig.pivot[rig.head]);
const eye = v3.add(neck, v3.sub(eyeRest, rig.pivot[rig.head]));
console.log('seated eye (model px)', eye.map(x => x.toFixed(2)).join(', '), ' over the ground (blocks, at the player scale 15/16)', ((24 - eye[1]) * 0.9375 / 16).toFixed(3),
    ' knee R', rest.body.knee.R.map(x => x.toFixed(2)).join(', '));

// ------------------------------------------------------------------ the actions
const r4 = x => +(+x).toFixed(4);
const DOC = 'docs/gameplay/splint_animation_v2_plan.md';
const stanceAction = (name, clip, about) => ({format_version: 1, about,
    afl_rig: {plugin: core.VERSION, animation: clip}, clip, layer: 'stance', mask: 'full', priority: 10, first_person: 'body',
    blend: {in: 0, out: 0}, hands: {right: [], left: []}, events: []});
// the splint carried to the limb and laid on (player_props/splint, its center on the limb's outer front, as V1's), hidden as
// BandageWrapRenderer starts drawing the two slats
const SPLINT_ROT = [180, -70, 90];
function splintTrack(bone, from, on, layAt, slatsAt) {
    return {id: 'splint', keys: [
        {at: 0, frame: 'chest', point: 'grip', pos: [-6, 10.5, 0], rot: [-90, 0, 0], blend: 0},
        {at: 0, frame: 'chest', point: 'grip', pos: [-6, 10.5, 0], rot: [-90, 0, 0], blend: r4(layAt)},
        {at: r4(layAt), frame: 'bone:' + bone, point: 'center', pos: from, rot: SPLINT_ROT, blend: r4(slatsAt - layAt - 0.2)},
        {at: r4(slatsAt - 0.2), frame: 'bone:' + bone, point: 'center', pos: on, rot: SPLINT_ROT, blend: 0.2},
        {at: r4(slatsAt), frame: 'bone:' + bone, point: 'center', pos: on, rot: SPLINT_ROT, blend: 0.05, hidden: true},
    ]};
}
const hk = (at, target, blend, pole) => ({at: r4(at), target, blend: r4(blend), roll: 0, pole});
const legAction = {format_version: 1,
    about: 'Splint V2 (' + DOC + '): splinting a broken lower leg sat on the ground (the seat: sit/GroundSit, the stance body.sit under it; SplintItem sits the player down first). 6 s (SplintItem.LEG_USE_TICKS 120), keyed on the left leg, mirrored for the right (client/skeleton/SkeletonActions). The right hand lays the splint (player_props/splint) on the shin (0-0.9 s, the left hand steadies it); at 0.9 s it is hidden and client/BandageWrapRenderer draws the two slats on both sides of the shin; three ties, ankle to knee, from 1.45, 2.7 and 3.95 s: the right hand pushes the strip under the leg from the inside, the left takes it on the outside, both ends up over the outer top and pulled tight (the renderer grows each tie over its first 0.65 s); the hands back on the thigh, the toes moved twice (5.3-5.75 s), sat back as body.sit. The body held (hold_body: arms keyed by FK, the look does not bend the chest); the own first-person camera on the posed eye (first_person_eye), the view eased down to 70 deg. Written by tools/medical-actions-v1a/author_splint_v2.cjs from the approved concept.',
    afl_rig: {plugin: core.VERSION, animation: 'medical.splint_leg'}, clip: 'medical.splint_leg', layer: 'action', mask: 'full', priority: 50,
    first_person: 'body', blend: {in: 0.3, out: 0.4}, first_person_pitch: 70, first_person_eye: true, hold_body: true,
    props: [splintTrack('shin_l', [3.1826, 19.8, -3.5238], [2.7722, 19.8, -2.3962], 0.4, LEG_SLATS_AT)],
    hands: {right: [hk(0, 'prop:splint:grip', 0, [-0.5, 1, 0.6]), hk(LEG_SLATS_AT, 'pose', 0.25, [-0.5, 1, 0.6])],
        left: [hk(0, 'pose', 0, [0.5, 1, 0.6]), hk(0.45, 'prop:splint:hold_2', 0.2, [0.5, 1, 0.6]), hk(LEG_SLATS_AT, 'pose', 0.2, [0.5, 1, 0.6])]},
    events: [], check: {use_ticks: Math.round(LEG_LEN * 20)}};
const armAction = {format_version: 1,
    about: 'Splint V2 (' + DOC + '): splinting a broken forearm, standing or sat (upper body). 8 s (SplintItem.USE_TICKS 160), keyed on the left arm, mirrored for the right (client/skeleton/SkeletonActions). The left forearm held across the belly; the right hand lays the splint (player_props/splint) on it (0-1.3 s), then BandageWrapRenderer draws the two slats on its palm side and back; three ties, wrist to elbow, from 2.0, 3.4 and 4.8 s: the right hand takes the strip under the forearm, round the outside and over the top (the renderer grows each tie over its first 0.55 s), then the forearm comes up to the mouth, the teeth hold one end and the right hand pulls the other tight (t + 0.8 - 1.0 s), back down; the fingers moved twice (6.2-6.8 s); the forearm carried close to the body and let down. The body held (hold_body: arms keyed by FK); the own first-person view eased down to 45 deg. Written by tools/medical-actions-v1a/author_splint_v2.cjs from the approved concept.',
    afl_rig: {plugin: core.VERSION, animation: 'medical.splint_arm'}, clip: 'medical.splint_arm', layer: 'action', mask: 'upper', priority: 50,
    first_person: 'body', blend: {in: 0.3, out: 0.3}, first_person_pitch: 45, hold_body: true,
    props: [splintTrack('forearm_l', [7.28, 8.0, -3.52], [6.87, 8.0, -2.4], 0.6, ARM_SLATS_AT)],
    hands: {right: [hk(0, 'prop:splint:grip', 0, [-0.6, 1, 0.4]), hk(ARM_SLATS_AT, 'pose', 0.25, [-0.6, 1, 0.4])], left: []},
    events: [], check: {use_ticks: Math.round(ARM_LEN * 20)}};

if (WRITE) {
    const pretty = o => JSON.stringify(o, null, 2) + '\n';
    const anim = p => path.join(A, 'animations/player', p + '.animation.json');
    const body = {format_version: '1.8.0', afl_rig: {about: 'Written by tools/medical-actions-v1a/author_splint_v2.cjs (sitting on the ground, ' + DOC + '); check in Blockbench with the AFL Rig plugin.'},
        animations: {'body.sit_down': sitDown.clip, 'body.sit': sitting.clip, 'body.stand_up': standUp.clip}};
    fs.writeFileSync(anim('body'), pretty(body));
    const medical = L.read(anim('medical'));
    medical.animations['medical.splint_leg'] = leg.clip;
    medical.animations['medical.splint_arm'] = arm.clip;
    fs.writeFileSync(anim('medical'), pretty(medical));
    const act = p => path.join(A, 'player_actions', p + '.json');
    fs.mkdirSync(path.join(A, 'player_actions/body'), {recursive: true});
    fs.writeFileSync(act('body/sit_down'), pretty(stanceAction('sit_down', 'body.sit_down', 'Sitting on the ground (' + DOC + ' 3): sitting down, 1.25 s (sit/GroundSit.SIT_TICKS 25): the weight on the right leg, the right hand back to the ground, the left leg slid out ahead heel down, down onto the seat, leaning back on the hand a moment, then the right forearm onto the raised right knee. A stance clip under any action (client/skeleton/GroundSitClient, as this client first sees the seat); mirrored when the right leg is the broken one. Written by tools/medical-actions-v1a/author_splint_v2.cjs.')));
    fs.writeFileSync(act('body/sit'), pretty(Object.assign(stanceAction('sit', 'body.sit', 'Sitting on the ground (' + DOC + ' 3): sat, the left leg out straight (toes up), the right knee up with the forearm over it, the left hand on the left thigh; the eye 1.03 blocks over the ground (sit/GroundSit.EYE). A stance clip under any action (client/skeleton/GroundSitClient); mirrored when the right leg is the broken one. Written by tools/medical-actions-v1a/author_splint_v2.cjs.'), {loop: 'time', length: 2})));
    fs.writeFileSync(act('body/stand_up'), pretty(stanceAction('stand_up', 'body.stand_up', 'Sitting on the ground (' + DOC + ' 3): getting up, 1.0 s (sit/GroundSit.STAND_TICKS 20; the seat lets the player off at its end): the right foot pulled in, both hands push on the right knee, the left leg kept straight heel down, up. A stance clip (client/skeleton/GroundSitClient); mirrored when the right leg is the broken one. Written by tools/medical-actions-v1a/author_splint_v2.cjs.')));
    fs.writeFileSync(act('item/splint_leg'), pretty(legAction));
    fs.writeFileSync(act('item/splint_arm'), pretty(armAction));
    console.log('written');
}
module.exports = {LEG_SLATS_AT, LEG_TIES, LEG_TIE_TURN, ARM_SLATS_AT, ARM_TIES, ARM_TIE_TURN};
