// Authoring sheet for landing from a fall V1 (docs/gameplay/fall_landing_v1_plan.md), from the approved concept
// (design/health/fall_landing_v1/src/concept_fall.cjs, user 2026-10-20 "概念通过"): key poses by hand below, the body by FK,
// the legs by two-bone IK (feet / knees on the ground), the palms by the AFL Rig core's arm IK, all baked into the clips as FK.
//   node author_fall_v1.cjs [--write]   -> prints each key's IK misses; --write writes:
//   animations/player/fall.animation.json   fall.land_hard (0.6 s), fall.fall_over (2.8 s)
//   player_actions/body/land_hard.json, player_actions/body/fall_over.json
// fall_over is keyed with the LEFT hand taking the ground first (the arm that may break): health/FallLanding sends the side,
// client/skeleton/FallLandingClient mirrors it for the right. Java model px: y down, the ground at 24, the front -z, the
// body's right -x.
const L = require('./lib.cjs');
const {fs, path, core, v3, q4, rad, A, rig, poser, between, fileEuler} = L;
const WRITE = process.argv.includes('--write');
const ID = [0, 0, 0, 1], DOWN = [0, 1, 0];
const {mul, conj} = q4;
const BODY = ['pelvis', 'chest', 'head', 'thigh_r', 'shin_r', 'foot_r', 'thigh_l', 'shin_l', 'foot_l'];

// ------------------------------------------------------------------ the legs
const THIGH = 6, SHIN = 4, SIDE = {R: -1.9, L: 1.9};
function legIk(hip, ankle, hint) {
    let d = v3.sub(ankle, hip), len = v3.len(d);
    if (len > THIGH + SHIN - 0.01) { d = v3.mul(v3.norm(d), THIGH + SHIN - 0.01); len = THIGH + SHIN - 0.01; ankle = v3.add(hip, d); }
    const u = v3.norm(d);
    const a = (THIGH * THIGH - SHIN * SHIN + len * len) / (2 * len), h = Math.sqrt(Math.max(0, THIGH * THIGH - a * a));
    const n = v3.norm(v3.sub(hint, v3.mul(u, v3.dot(hint, u))));
    const knee = v3.add(hip, v3.add(v3.mul(u, a), v3.mul(n, h)));
    return {thighDir: v3.norm(v3.sub(knee, hip)), shinDir: v3.norm(v3.sub(ankle, knee)), knee};
}
/** World turns of thigh / shin / foot; foot 'flat' (sole on the ground), 'shin' (along the shin: kneeling, the instep down). */
function legWorld(thighDir, shinDir, foot) {
    const thighW = between(DOWN, thighDir);
    const shinW = mul(between(q4.apply(thighW, DOWN), shinDir), thighW);
    return {thighW, shinW, footW: foot === 'shin' ? shinW : ID};
}
/**
 * A key's body: tip (pelvis forward, deg), roll (pelvis toward the left side, deg), lean (chest on the pelvis), head (pitch,
 * + down), drop / zoff (the pelvis down / back, px); legs {ik: ankle, hint, foot}.
 */
function bodyOf(k) {
    const pelvisW = mul(q4.z(rad(k.roll || 0)), q4.x(rad(k.tip || 0)));
    const out = {pelvis: pelvisW, chest: q4.x(rad(k.lean || 0)), head: q4.x(rad(k.head || 0)), pos: [0, -(k.drop || 0), k.zoff || 0], knee: {}};
    const hipY = 12 + (k.drop || 0), hipZ = k.zoff || 0;
    for (const s of ['R', 'L']) {
        const spec = k['leg' + s] || {ik: [SIDE[s], 22, 0]};
        const hip = [SIDE[s], hipY, hipZ];
        const dirs = legIk(hip, spec.ik, spec.hint || [0, -0.4, -1]);
        const w = legWorld(dirs.thighDir, dirs.shinDir, spec.foot || 'flat');
        out['thigh_' + s.toLowerCase()] = mul(conj(pelvisW), w.thighW);
        out['shin_' + s.toLowerCase()] = mul(conj(w.thighW), w.shinW);
        out['foot_' + s.toLowerCase()] = mul(conj(w.shinW), w.footW);
        out.knee[s] = dirs.knee;
    }
    return out;
}

// ------------------------------------------------------------------ one key -> every bone's file euler
const DUMMY = {scale: 1 / 16, points: {p: [0, 0, 0]}};
function constClip(eulers, pos) {
    const bones = {};
    for (const [n, e] of Object.entries(eulers)) bones[n] = {rotation: {'0.0': e, '1.0': e}};
    if (pos) bones.pelvis.position = {'0.0': pos, '1.0': pos};
    return core.loadClips(rig, {animations: {c: {animation_length: 1, bones}}});
}
function fullKey(k) {
    const b = bodyOf(k);
    const eulers = {};
    for (const n of BODY) eulers[n] = fileEuler(b[n]);
    const clips = constClip(eulers, b.pos);
    const props = [], hands = {right: [], left: []};
    const at = p => (typeof p === 'function' ? p(b) : p);
    for (const [side, palm] of [['right', at(k.palmR)], ['left', at(k.palmL)]]) {
        if (!palm) continue;
        props.push({id: 'd_' + side, keys: [{at: 0, frame: 'root', point: 'p', pos: palm, rot: [0, 0, 0], blend: 0}]});
        hands[side].push({at: 0, target: 'prop:d_' + side + ':p', blend: 0, roll: 0, pole: side === 'right' ? (k.poleR || [-1, 0.2, 0.6]) : (k.poleL || [1, 0.2, 0.6])});
    }
    const act = core.actionFromJson({clip: 'c', mask: 'full', hold_body: true, blend: {in: 0, out: 0}, props, hands}, id => (id.startsWith('d_') ? DUMMY : null), x => clips[x] || null);
    const res = core.solveFrame(poser, {action: act, seconds: 0.5, time: 0, pitch: 0});
    const ro = core.readouts(res), P = res.pose.local;
    const miss = s => (ro[s].mode === 'ik' ? ro[s].off.toFixed(2) : '-');
    for (const [side, S] of [['right', 'R'], ['left', 'L']]) {
        const n = S.toLowerCase();
        if (k['palm' + S]) Object.assign(eulers, {['upper_arm_' + n]: fileEuler(P[rig['upperArm' + S]]), ['forearm_' + n]: fileEuler(P[rig['forearm' + S]]), ['hand_' + n]: fileEuler(P[rig['hand' + S]])});
        else Object.assign(eulers, {['upper_arm_' + n]: fileEuler(k['arm' + S] || ID), ['forearm_' + n]: fileEuler(k['fore' + S] || ID), ['hand_' + n]: [0, 0, 0]});
    }
    // the eye (ActionPose#cameraEye: the posed neck plus the eye's rest offset; here turned with the head too) and its pitch
    const W = res.pose;
    const eye = W.point(rig.head, rig.sockets.eye.pos.map((v, i) => v + 0 * i));
    return {eulers, pos: b.pos, miss: 'R ' + miss('right') + ' L ' + miss('left'), eye, res};
}
function clipOf(keys, length) {
    const bones = {}, rows = [], view = {};
    for (const k of keys) {
        const s = fullKey(k);
        const t = k.t.toFixed(4).replace(/0+$/, '').replace(/\.$/, '.0');
        for (const [n, e] of Object.entries(s.eulers)) ((bones[n] = bones[n] || {rotation: {}}).rotation[t] = e);
        ((bones.pelvis.position = bones.pelvis.position || {})[t] = s.pos);
        rows.push(k.t.toFixed(2) + ' ' + s.miss + (k.note ? '  ' + k.note : ''));
        view[t] = k.view || 0;
    }
    for (const b of Object.values(bones)) {
        const ts = Object.keys(b.rotation).sort((a, c) => a - c);
        for (let i = 1; i < ts.length; i++) for (let j = 0; j < 3; j++) {
            while (b.rotation[ts[i]][j] - b.rotation[ts[i - 1]][j] > 180) b.rotation[ts[i]][j] -= 360;
            while (b.rotation[ts[i]][j] - b.rotation[ts[i - 1]][j] < -180) b.rotation[ts[i]][j] += 360;
        }
    }
    return {clip: {animation_length: length, bones, view}, rows};
}

// ------------------------------------------------------------------ poses
const deg = d => q4.x(rad(d));
const ARMS_OUT = {armR: mul(q4.z(rad(28)), deg(-15)), armL: mul(q4.z(rad(-28)), deg(-15)), foreR: deg(-25), foreL: deg(-25)};
const STAND = {legR: {ik: [-1.9, 22, 0]}, legL: {ik: [1.9, 22, 0]}};
const crouch = (drop, z) => ({drop, zoff: z || 0, legR: {ik: [-1.9, 22, -0.4]}, legL: {ik: [1.9, 22, -0.4]}});
// kneeling: the knee centre 2 px over the ground, the shin back along it, the instep down
const kneel = (s, kz) => ({ik: [SIDE[s], 22, kz + SHIN], hint: [0, 1, -1], foot: 'shin'});

// A. a hard landing, kept on the feet (0.6 s): down through the knees and hips, the arms out for balance, back up
const LAND = [
    Object.assign({t: 0, view: 5, note: 'feet touch'}, STAND, ARMS_OUT, {head: -5}),
    Object.assign({t: 0.1, view: 20, note: 'deepest'}, crouch(4.2, 0.6), {tip: 24, lean: 6, head: -12}, ARMS_OUT),
    Object.assign({t: 0.28, view: 10}, crouch(2.6, 0.3), {tip: 14, lean: 3, head: -6, armR: deg(-12), armL: deg(-12), foreR: deg(-15), foreL: deg(-15)}),
    Object.assign({t: 0.6, view: 0}, STAND),
];
// B. falling over (2.8 s): the knees give, onto the knees, the left hand first, all fours, a knee up, up
const FALL = [
    Object.assign({t: 0, view: 5, note: 'feet touch'}, STAND, ARMS_OUT, {head: -5}),
    Object.assign({t: 0.12, view: 25, note: 'the knees give'}, crouch(4.6, 0.8), {tip: 30, lean: 8, head: -10}, ARMS_OUT),
    // pitching forward, the knees going down, both hands reaching for the ground (the left lower and further out)
    {t: 0.32, view: 30, note: 'pitching', drop: 8, zoff: 1.2, tip: 50, lean: 10, roll: 6, head: -25,
        legR: {ik: [-1.9, 22.5, 1.8], hint: [0, 0.3, -1], foot: 'flat'}, legL: {ik: [1.9, 22.5, 1.6], hint: [0, 0.3, -1], foot: 'flat'},
        palmR: [-5.0, 14.5, -15.5], palmL: [5.5, 16.0, -16.0], poleR: [-1, 0.6, 0.3], poleL: [1, 0.6, 0.3]},
    // the knees on the ground, the left palm hits (the thud and the arm's fracture roll: 0.45 s)
    {t: 0.45, view: 60, note: 'left hand hits', drop: 4, zoff: 0, tip: 68, lean: 10, roll: 10, head: -32,
        legR: kneel('R', 0), legL: kneel('L', 0), palmR: [-5.0, 20.5, -11.0], palmL: [5.6, 23.6, -11.0]},
    {t: 0.56, view: 65, note: 'right hand down', drop: 4, zoff: 0, tip: 76, lean: 12, roll: 4, head: -28,
        legR: kneel('R', 0), legL: kneel('L', 0), palmR: [-5.2, 23.6, -11.2], palmL: [5.6, 23.6, -11.0], poleL: [1, 0.6, 0.2]},
    // the weight onto the hands, elbows give a little, head hung
    {t: 0.78, view: 75, note: 'all fours', drop: 4.4, zoff: 0, tip: 82, lean: 10, roll: 0, head: 10,
        legR: kneel('R', 0), legL: kneel('L', 0), palmR: [-5.2, 23.6, -11.2], palmL: [5.6, 23.6, -11.0], poleR: [-1, 0.6, 0.2], poleL: [1, 0.6, 0.2]},
    {t: 1.2, view: 80, note: 'breath', drop: 4.8, zoff: 0, tip: 84, lean: 12, head: 18,
        legR: kneel('R', 0), legL: kneel('L', 0), palmR: [-5.2, 23.6, -11.2], palmL: [5.6, 23.6, -11.0], poleR: [-1, 0.6, 0.2], poleL: [1, 0.6, 0.2]},
    {t: 1.5, view: 70, drop: 4.4, zoff: 0, tip: 82, lean: 10, head: 6,
        legR: kneel('R', 0), legL: kneel('L', 0), palmR: [-5.2, 23.6, -11.2], palmL: [5.6, 23.6, -11.0], poleR: [-1, 0.6, 0.2], poleL: [1, 0.6, 0.2]},
    // sat back over the knees, the hands off the ground, the right foot forward flat (half kneel)
    {t: 1.85, view: 35, note: 'right foot forward', drop: 5, zoff: 0.6, tip: 40, lean: 6, head: -5,
        legR: {ik: [-1.9, 22, -5.5], hint: [0, -1, -0.6]}, legL: kneel('L', 0.4), palmR: [-2.6, 15.5, -6.5], palmL: [0.4, 15.8, -6.0]},
    // both hands on the right knee, pushing up
    {t: 2.15, view: 25, note: 'push on the knee', drop: 4.6, zoff: 0.2, tip: 26, lean: 4, head: -8,
        legR: {ik: [-1.9, 22, -5.0], hint: [0, -1, -0.6]}, legL: kneel('L', 0.2), palmR: b => v3.add(b.knee.R, [-0.6, -2.0, -1.2]), palmL: b => v3.add(b.knee.R, [1.6, -2.0, -1.0])},
    {t: 2.45, view: 10, note: 'rising', drop: 2, zoff: -0.8, tip: 14, lean: 2, head: -4,
        legR: {ik: [-1.9, 22, -2.6], hint: [0, -0.4, -1]}, legL: {ik: [1.9, 22, 1.2], hint: [0, -0.2, -1]}, armR: deg(-15), armL: deg(-12)},
    Object.assign({t: 2.8, view: 0}, STAND),
];

const LAND_LEN = 0.6, FALL_LEN = 2.8, HAND_HITS = 0.45;
const land = clipOf(LAND, LAND_LEN), fall = clipOf(FALL, FALL_LEN);
console.log(['land_hard', ...land.rows, 'fall_over', ...fall.rows].join('\n'));
// the view keyed per key (first_person_view), out of the clips
const viewKeys = clip => { const v = clip.view; delete clip.view; return Object.keys(v).map(Number).sort((a, b) => a - b).map(t => [t, v[Object.keys(v).find(k => +k === t)]]); };
const landView = viewKeys(land.clip), fallView = viewKeys(fall.clip);

const DOC = 'docs/gameplay/fall_landing_v1_plan.md';
const landAction = {format_version: 1,
    about: 'Landing from a fall V1 (' + DOC + ' 2): a hard landing kept on the feet, 0.6 s (health/FallLanding sends it on a damaging fall that is not a fall-over): down through the knees and hips, the pelvis tipped forward, back up. The legs and hips only (mask legs), so the arms keep what the held item does; the own first person keeps its view model, the camera rides the posed eye down and up (first_person_eye). Written by tools/medical-actions-v1a/author_fall_v1.cjs from the approved concept.',
    afl_rig: {plugin: core.VERSION, animation: 'fall.land_hard'}, clip: 'fall.land_hard', layer: 'action', mask: 'legs', priority: 60,
    first_person: 'view_model', blend: {in: 0.04, out: 0.15}, first_person_eye: true, hands: {right: [], left: []}, events: []};
const fallAction = {format_version: 1,
    about: 'Landing from a fall V1 (' + DOC + ' 2): falling over on a landing, 2.8 s (health/FallLanding.FALL_TICKS 56): the knees give, pitched forward onto the knees with both hands out ahead, the LEFT hand takes the ground first at 0.45 s (the body thud and the arm fracture roll; mirrored when the right hand does), all fours a moment, the right foot forward, both hands push on the knee, up. The whole body (mask full), held (hold_body: arms keyed by FK); the own first person draws the body, the camera on the posed eye, the view following first_person_view. No moving or using items meanwhile (client/skeleton/FallLandingClient). Written by tools/medical-actions-v1a/author_fall_v1.cjs from the approved concept.',
    afl_rig: {plugin: core.VERSION, animation: 'fall.fall_over'}, clip: 'fall.fall_over', layer: 'action', mask: 'full', priority: 70,
    first_person: 'body', blend: {in: 0.06, out: 0.3}, first_person_eye: true, hold_body: true, first_person_view: fallView,
    hands: {right: [], left: []}, events: [{at: HAND_HITS, kind: 'sound', id: 'apocalypse_firstlight:body_fall', about: 'played by the server (health/FallLanding), with the arm fracture roll'}]};
console.log('land view', JSON.stringify(landView));
console.log('fall view', JSON.stringify(fallView));

if (WRITE) {
    const pretty = o => JSON.stringify(o, null, 2) + '\n';
    fs.writeFileSync(path.join(A, 'animations/player/fall.animation.json'), pretty({format_version: '1.8.0',
        afl_rig: {about: 'Written by tools/medical-actions-v1a/author_fall_v1.cjs (landing from a fall, ' + DOC + '); check in Blockbench with the AFL Rig plugin.'},
        animations: {'fall.land_hard': land.clip, 'fall.fall_over': fall.clip}}));
    fs.mkdirSync(path.join(A, 'player_actions/body'), {recursive: true});
    fs.writeFileSync(path.join(A, 'player_actions/body/land_hard.json'), pretty(landAction));
    fs.writeFileSync(path.join(A, 'player_actions/body/fall_over.json'), pretty(fallAction));
    console.log('written');
}
module.exports = {LAND_LEN, FALL_LEN, HAND_HITS};
