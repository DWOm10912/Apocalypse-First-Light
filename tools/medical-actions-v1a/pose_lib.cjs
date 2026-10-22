// Body and arm helpers shared by the medical actions that pose the whole body (author_splint_v2.cjs, author_dress_v1.cjs):
// the legs on the ground by two-bone IK, a key's body by FK, the palms by the AFL Rig core's arm IK (a dummy prop per hand)
// baked into clip keys as FK, keys -> a clip, and the seat on the ground (sit/GroundSit, the stance body.sit).
// Java model px: y down, the ground at 24, the front -z, the body's right -x.
const L = require('./lib.cjs');
const {core, v3, q4, rad, rig, poser, between, fileEuler} = L;
const ID = [0, 0, 0, 1], DOWN = [0, 1, 0];
const {mul, conj} = q4;
const BODY = ['pelvis', 'chest', 'head', 'thigh_r', 'shin_r', 'foot_r', 'thigh_l', 'shin_l', 'foot_l'];
const ARMS = ['upper_arm_r', 'forearm_r', 'hand_r', 'upper_arm_l', 'forearm_l', 'hand_l'];
/** The chest's bend for a look (HumanoidPoser#chestPitch empty-handed), baked into the clips' chest. */
const bendOf = view => Math.max(-15, Math.min(20, (view || 0) * 0.3));

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
/** World turns of thigh / shin / foot; foot 'flat' or toes-up angle (deg from straight ahead toward up). */
function legWorld(thighDir, shinDir, foot) {
    const thighW = between(DOWN, thighDir);
    const shinW = mul(between(q4.apply(thighW, DOWN), shinDir), thighW);
    let footW = ID;
    if (foot !== 'flat') {
        const toes = q4.apply(shinW, [0, 0, -1]);
        const want = v3.norm(v3.add(v3.mul([0, 0, -1], Math.cos(rad(foot))), v3.mul([0, -1, 0], Math.sin(rad(foot)))));
        footW = mul(between(toes, want), shinW);
    }
    return {thighW, shinW, footW};
}
/** A seated / standing key's body: local quats, the pelvis offset, the knees (world px). */
function bodyOf(k) {
    const pelvisW = q4.x(rad(k.tip || 0));
    const out = {pelvis: pelvisW, chest: q4.x(rad((k.lean || 0) + bendOf(k.view))), head: ID, pos: [0, -(k.drop || 0), k.zoff || 0], knee: {}};
    const hipY = 12 + (k.drop || 0), hipZ = k.zoff || 0;
    for (const s of ['R', 'L']) {
        const spec = k['leg' + s];
        const hip = [SIDE[s], hipY, hipZ];
        let dirs;
        if (spec.ik) dirs = legIk(hip, spec.ik, spec.hint || [0, -0.4, -1]);
        else {
            let dir = spec.dir;
            if (!dir) { const dy = 24 - hip[1] - 0.3; dir = [0, dy, -Math.sqrt(Math.max(0, 144 - dy * dy))]; }
            dir = v3.norm(dir);
            dirs = {thighDir: dir, shinDir: dir, knee: v3.add(hip, v3.mul(dir, THIGH))};
        }
        const w = legWorld(dirs.thighDir, dirs.shinDir, spec.foot === undefined ? 'flat' : spec.foot);
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
/** Solves the palms by IK on the posed body (the body held: no look on the chest), returns the arms' locals. */
function solveArms(eulers, pos, mask, k, palmFor) {
    const clips = constClip(eulers, pos);
    const base = () => core.actionFromJson({clip: 'c', mask, hold_body: true, blend: {in: 0, out: 0}}, () => null, x => clips[x] || null);
    let res = core.solveFrame(poser, {action: base(), seconds: 0.5, time: 0, pitch: k.view || 0});
    const palms = palmFor(res);
    const props = [], hands = {right: [], left: []};
    for (const [side, palm] of [['right', palms.R], ['left', palms.L]]) {
        if (!palm) continue;
        props.push({id: 'd_' + side, keys: [{at: 0, frame: 'root', point: 'p', pos: palm, rot: [0, 0, 0], blend: 0}]});
        hands[side].push({at: 0, target: 'prop:d_' + side + ':p', blend: 0, roll: (side === 'right' ? k.rollR : k.rollL) || 0, pole: side === 'right' ? (k.poleR || [-0.6, 1, 0.3]) : (k.poleL || [0.6, 1, 0.3])});
    }
    if (props.length) {
        const act = core.actionFromJson({clip: 'c', mask, hold_body: true, blend: {in: 0, out: 0}, props, hands}, id => (id.startsWith('d_') ? DUMMY : null), x => clips[x] || null);
        res = core.solveFrame(poser, {action: act, seconds: 0.5, time: 0, pitch: k.view || 0});
    }
    const ro = core.readouts(res), P = res.pose.local, arms = {};
    const miss = s => (ro[s].mode === 'ik' ? ro[s].off.toFixed(2) + '/' + Math.round(ro[s].wrist) : '-');
    if (palms.R) Object.assign(arms, {upper_arm_r: fileEuler(P[rig.upperArmR]), forearm_r: fileEuler(P[rig.forearmR]), hand_r: fileEuler(P[rig.handR])});
    if (palms.L) Object.assign(arms, {upper_arm_l: fileEuler(P[rig.upperArmL]), forearm_l: fileEuler(P[rig.forearmL]), hand_l: fileEuler(P[rig.handL])});
    return {arms, res, ro, miss: 'R ' + miss('right') + ' L ' + miss('left')};
}
/** A seated / standing key (full body): the body by FK, the palms by IK, unkeyed arms by armR / armL (local quats) or rest. */
function fullKey(k) {
    const b = bodyOf(k);
    const eulers = {};
    for (const n of BODY) eulers[n] = fileEuler(b[n]);
    const s = solveArms(eulers, b.pos, 'full', k, () => ({R: typeof k.palmR === 'function' ? k.palmR(b) : k.palmR, L: typeof k.palmL === 'function' ? k.palmL(b) : k.palmL}));
    for (const [n, side] of [['upper_arm_r', 'R'], ['upper_arm_l', 'L']]) {
        if (s.arms[n]) continue;
        const q = k['arm' + side] || ID;
        s.arms[n] = fileEuler(q);
        s.arms[n.replace('upper_arm', 'forearm')] = [0, 0, 0];
        s.arms[n.replace('upper_arm', 'hand')] = [0, 0, 0];
    }
    return {eulers: Object.assign(eulers, s.arms), pos: b.pos, res: s.res, ro: s.ro, miss: s.miss, body: b};
}
/** Keys -> a clip {animation_length, bones}; euler jumps unwrapped per channel. */
function clipOf(keys, length, solve) {
    const bones = {}, rows = [];
    for (const k of keys) {
        const s = solve(k);
        const t = k.t.toFixed(4).replace(/0+$/, '').replace(/\.$/, '.0');
        for (const [n, e] of Object.entries(s.eulers)) ((bones[n] = bones[n] || {rotation: {}}).rotation[t] = e);
        if (s.pos) ((bones.pelvis.position = bones.pelvis.position || {})[t] = s.pos);
        rows.push(k.t.toFixed(2) + ' ' + s.miss);
    }
    for (const b of Object.values(bones)) {
        const ts = Object.keys(b.rotation).sort((a, c) => a - c);
        for (let i = 1; i < ts.length; i++) for (let j = 0; j < 3; j++) {
            while (b.rotation[ts[i]][j] - b.rotation[ts[i - 1]][j] > 180) b.rotation[ts[i]][j] -= 360;
            while (b.rotation[ts[i]][j] - b.rotation[ts[i - 1]][j] < -180) b.rotation[ts[i]][j] += 360;
        }
    }
    return {clip: {animation_length: length, bones}, rows};
}

// ------------------------------------------------------------------ sitting on the ground
const SIT_L = {dir: [0, 0, -1], foot: 78};                                  // the (broken) left leg out straight, toes up
const kneeUp = (z, x) => ({ik: [x || -2.6, 22, z], hint: [0, -1, -0.4]});   // the right knee up, its foot flat
const SEAT = {drop: 10, zoff: 2, legR: kneeUp(-3.5), legL: SIT_L};
// sat: the right forearm over the raised knee, the left hand on the left thigh
const REST = Object.assign({}, SEAT, {tip: 6, lean: -2, view: 0,
    palmR: b => v3.add(b.knee.R, [0.2, 1.4, -2.0]), palmL: [3.6, 19.5, -1.2], poleR: [-0.7, 0.6, 0.6]});
const STAND = {view: 0, legR: {ik: [-1.9, 22, 0]}, legL: {ik: [1.9, 22, 0]}};
// sat working on the left shin (author_dress_v1 bandage_leg, author_alcohol_parts_v1 alcohol_leg): leant well forward, the
// left knee up and its foot flat ahead, the right leg out to the side (room for the inner hand under the shin)
const SHIN_WORK = Object.assign({}, SEAT, {tip: 26, lean: 8, view: 70,
    legL: {ik: [3.5, 22, -6.5], hint: [0.1, -1, -0.3]}, legR: {ik: [-10.5, 22, 0], hint: [0, -0.3, -1], foot: 60}});

module.exports = {ID, DOWN, BODY, ARMS, bendOf, THIGH, SHIN, SIDE, legIk, legWorld, bodyOf, DUMMY, constClip, solveArms, fullKey, clipOf,
    SIT_L, kneeUp, SEAT, REST, STAND, SHIN_WORK};
