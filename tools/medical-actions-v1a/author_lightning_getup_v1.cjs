// Authoring sheet for getting up after lying paralysed, lightning injury V1 (docs/gameplay/lightning_injury_v1_plan.md 3.5):
// from lying on the front (the crawl pose) up onto the hands, the knees drawn under to all fours, then the same half kneel,
// push on the knee and rise as falling over (author_fall_v1.cjs, user PASS 2026-10-20). The body by FK, the legs by
// two-bone IK, the palms by the AFL Rig core's arm IK, all baked into the clip as FK.
//   node author_lightning_getup_v1.cjs [--write]   -> prints each key's IK misses; --write writes:
//   animations/player/lightning.animation.json   lightning.prone_get_up (2.6 s, health/FallLanding.GET_UP_TICKS 52)
//   player_actions/body/prone_get_up.json
// Java model px: y down, the ground at 24, the front -z, the body's right -x.
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
        rows.push(k.t.toFixed(2) + ' ' + s.miss + ' eye ' + s.eye.map(x => x.toFixed(1)).join(',') + (k.note ? '  ' + k.note : ''));
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
const STAND = {legR: {ik: [-1.9, 22, 0]}, legL: {ik: [1.9, 22, 0]}};
const kneel = (s, kz) => ({ik: [SIDE[s], 22, kz + SHIN], hint: [0, 1, -1], foot: 'shin'});
// lying on the front: the pelvis 2 px over the ground and back so the body lies over the feet's spot, the legs straight
// back, the insteps down
const lying = (z, s) => ({ik: [SIDE[s], 22.6, z + 9.6], hint: [0, 1, 0.2], foot: 'shin'});
const FOURS = {legR: kneel('R', 0), legL: kneel('L', 0), palmR: [-5.2, 23.6, -11.2], palmL: [5.6, 23.6, -11.0], poleR: [-1, 0.6, 0.2], poleL: [1, 0.6, 0.2]};

const GET_UP = [
    // lying, the palms flat beside the chest, the head lifted
    {t: 0, view: 10, note: 'lying', drop: 9, zoff: 5, tip: 90, lean: -10, head: -45,
        legR: lying(5, 'R'), legL: lying(5, 'L'), palmR: [-5.4, 23.5, -5.4], palmL: [5.4, 23.5, -5.4], poleR: [-1, -0.6, 0.4], poleL: [1, -0.6, 0.4]},
    {t: 0.25, view: 12, drop: 9, zoff: 5, tip: 90, lean: -10, head: -42,
        legR: lying(5, 'R'), legL: lying(5, 'L'), palmR: [-5.4, 23.5, -5.4], palmL: [5.4, 23.5, -5.4], poleR: [-1, -0.6, 0.4], poleL: [1, -0.6, 0.4]},
    // the chest pushed up off the ground, the hips still down
    {t: 0.6, view: 20, note: 'push up', drop: 10, zoff: 5, tip: 90, lean: -28, head: -8,
        legR: lying(5, 'R'), legL: lying(5, 'L'), palmR: [-5.6, 23.6, -4.5], palmL: [5.6, 23.6, -4.5], poleR: [-1, -0.2, 0.6], poleL: [1, -0.2, 0.6]},
    // the hips drawn back over the knees
    {t: 1.0, view: 45, note: 'knees under', drop: 6, zoff: 2, tip: 80, lean: 0, head: 0,
        legR: kneel('R', 2), legL: kneel('L', 2), palmR: [-5.4, 23.6, -8.0], palmL: [5.6, 23.6, -8.0], poleR: [-1, 0.6, 0.2], poleL: [1, 0.6, 0.2]},
    Object.assign({t: 1.3, view: 55, note: 'all fours', drop: 4.6, zoff: 0, tip: 82, lean: 10, head: 12}, FOURS),
    // from here as falling over (its 1.85 .. 2.8 s)
    {t: 1.65, view: 35, note: 'right foot forward', drop: 5, zoff: 0.6, tip: 40, lean: 6, head: -5,
        legR: {ik: [-1.9, 22, -5.5], hint: [0, -1, -0.6]}, legL: kneel('L', 0.4), palmR: [-2.6, 15.5, -6.5], palmL: [0.4, 15.8, -6.0]},
    {t: 1.95, view: 25, note: 'push on the knee', drop: 4.6, zoff: 0.2, tip: 26, lean: 4, head: -8,
        legR: {ik: [-1.9, 22, -5.0], hint: [0, -1, -0.6]}, legL: kneel('L', 0.2), palmR: b => v3.add(b.knee.R, [-0.6, -2.0, -1.2]), palmL: b => v3.add(b.knee.R, [1.6, -2.0, -1.0])},
    {t: 2.25, view: 10, note: 'rising', drop: 2, zoff: -0.8, tip: 14, lean: 2, head: -4,
        legR: {ik: [-1.9, 22, -2.6], hint: [0, -0.4, -1]}, legL: {ik: [1.9, 22, 1.2], hint: [0, -0.2, -1]}, armR: deg(-15), armL: deg(-12)},
    Object.assign({t: 2.6, view: 0}, STAND),
];

const LEN = 2.6;
const up = clipOf(GET_UP, LEN);
console.log(['prone_get_up', ...up.rows].join('\n'));
const v = up.clip.view;
delete up.clip.view;
const upView = Object.keys(v).map(Number).sort((a, b) => a - b).map(t => [t, v[Object.keys(v).find(k => +k === t)]]);
console.log('view', JSON.stringify(upView));

const DOC = 'docs/gameplay/lightning_injury_v1_plan.md';
const action = {format_version: 1,
    about: 'Lightning injury V1 (' + DOC + ' 3.5): getting up after lying paralysed, 2.6 s (health/FallLanding.GET_UP_TICKS 52): from lying on the front the palms push the chest up, the knees drawn under to all fours, then as falling over: the right foot forward, both hands push on the knee, up. The whole body (mask full), held (hold_body: arms keyed by FK); the own first person draws the body, the camera on the posed eye, the view following first_person_view. No moving or using items meanwhile (client/skeleton/FallLandingClient). Written by tools/medical-actions-v1a/author_lightning_getup_v1.cjs.',
    afl_rig: {plugin: core.VERSION, animation: 'lightning.prone_get_up'}, clip: 'lightning.prone_get_up', layer: 'action', mask: 'full', priority: 70,
    first_person: 'body', blend: {in: 0.3, out: 0.3}, first_person_eye: true, hold_body: true, first_person_view: upView,
    hands: {right: [], left: []}, events: []};

if (WRITE) {
    const pretty = o => JSON.stringify(o, null, 2) + '\n';
    fs.writeFileSync(path.join(A, 'animations/player/lightning.animation.json'), pretty({format_version: '1.8.0',
        afl_rig: {about: 'Written by tools/medical-actions-v1a/author_lightning_getup_v1.cjs (getting up after lightning paralysis, ' + DOC + '); check in Blockbench with the AFL Rig plugin.'},
        animations: {'lightning.prone_get_up': up.clip}}));
    fs.writeFileSync(path.join(A, 'player_actions/body/prone_get_up.json'), pretty(action));
    console.log('written');
}
module.exports = {LEN, fullKey, GET_UP};   // fullKey, GET_UP: for offline checks (head and chest over the ground)
