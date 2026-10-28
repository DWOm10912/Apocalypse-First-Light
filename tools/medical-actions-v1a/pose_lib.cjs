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
// the legs the keys were written for (vanilla's, afl_humanoid_v1) and the rig's own (Java px): the thigh, the shin, the
// hips' x, their y standing, the ankle's y standing on the ground
const geoOf = r => ({THIGH: v3.len(v3.sub(r.pivot[r.shinR], r.pivot[r.thighR])), SHIN: v3.len(v3.sub(r.pivot[r.footR], r.pivot[r.shinR])),
    SIDE: {R: r.pivot[r.thighR][0], L: -r.pivot[r.thighR][0]}, HIP: r.pivot[r.thighR][1], ANKLE: r.pivot[r.footR][1], SHOULDER: r.pivot[r.upperArmR][1]});
const KEYED = {THIGH: 6, SHIN: 4, SIDE: {R: -1.9, L: 1.9}, HIP: 12, ANKLE: 22, SHOULDER: 2}, GEO = geoOf(rig);
const OWN = GEO.HIP === KEYED.HIP && GEO.THIGH === KEYED.THIGH && GEO.SHIN === KEYED.SHIN;
const {THIGH, SHIN} = GEO, SIDE = OWN ? KEYED.SIDE : GEO.SIDE;
/** The hip joints over the ground when sat (vanilla's; Block-Mesh V2's thighs lie on the ground at it too). */
const SEAT_H = 2;
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
/**
 * Another rig's leg slid out ahead, straight, heel down (a key without ik or dir): as steep as leaves its heel and toes
 * over the ground (vanilla's keys: the ankle line 0.3 px over it, the heel dipping 1.4 px on B's longer foot).
 */
function slidOut(hip, foot) {
    const len = THIGH + SHIN, sole = 24 - GEO.ANKLE;
    const lowest = dy => {
        const dir = v3.norm([0, dy, -Math.sqrt(Math.max(0, len * len - dy * dy))]);
        const ankle = v3.add(hip, v3.mul(dir, len)), w = legWorld(dir, dir, foot);
        return Math.max(...[[0, sole, 2], [0, sole, -3.5]].map(c => v3.add(ankle, q4.apply(w.footW, c))[1]));
    };
    let lo = 0, hi = len;   // dy: the ankle's drop under the hip; lowest() grows with it
    for (let i = 0; i < 40; i++) { const mid = (lo + hi) / 2; if (lowest(mid) > 23.9) hi = mid; else lo = mid; }
    return [0, lo, -Math.sqrt(Math.max(0, len * len - lo * lo))];
}
/** A seated / standing key's body: local quats, the pelvis offset, the knees (world px). */
function bodyOf(k) {
    const pelvisW = q4.x(rad(k.tip || 0));
    // another rig's legs (Block-Mesh V2, user 2026-10-26 "你看看之前旧版本的是怎么做的坐下动画"): the hips as high over the
    // ground in proportion (sat: on it as vanilla's; standing: the rig's own), the feet out from the hips in proportion
    const keyedH = 24 - (KEYED.HIP + (k.drop || 0));
    const h = OWN ? keyedH : SEAT_H + (keyedH - SEAT_H) * (24 - GEO.HIP - SEAT_H) / (24 - KEYED.HIP - SEAT_H);
    const drop = 24 - h - GEO.HIP, reach = (GEO.THIGH + GEO.SHIN) / (KEYED.THIGH + KEYED.SHIN);
    const out = {pelvis: pelvisW, chest: q4.x(rad((k.lean || 0) + bendOf(k.view))), head: ID, pos: [0, -drop, k.zoff || 0], knee: {}, shin: {}};
    const hipY = GEO.HIP + drop, hipZ = k.zoff || 0;
    for (const s of ['R', 'L']) {
        const spec = k['leg' + s];
        const hip = [SIDE[s], hipY, hipZ];
        let dirs;
        if (spec.ik) {
            const at = OWN ? spec.ik : [spec.ik[0], spec.ik[1] + GEO.ANKLE - KEYED.ANKLE, hipZ + (spec.ik[2] - hipZ) * reach];
            dirs = legIk(hip, at, spec.hint || [0, -0.4, -1]);
        } else {
            let dir = spec.dir;
            if (!dir && OWN) { const dy = 24 - hip[1] - 0.3; dir = [0, dy, -Math.sqrt(Math.max(0, 144 - dy * dy))]; }
            else if (!dir) dir = slidOut(hip, spec.foot === undefined ? 'flat' : spec.foot);
            dir = v3.norm(dir);
            dirs = {thighDir: dir, shinDir: dir, knee: v3.add(hip, v3.mul(dir, THIGH))};
        }
        const w = legWorld(dirs.thighDir, dirs.shinDir, spec.foot === undefined ? 'flat' : spec.foot);
        out['thigh_' + s.toLowerCase()] = mul(conj(pelvisW), w.thighW);
        out['shin_' + s.toLowerCase()] = mul(conj(w.thighW), w.shinW);
        out['foot_' + s.toLowerCase()] = mul(conj(w.shinW), w.footW);
        out.knee[s] = dirs.knee;
        out.shin[s] = dirs.shinDir;
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
/** The keys' own legs (vanilla's), for a palm by a knee on another rig ({@link palmOn}): their knees. */
function keyedKnees(k) {
    if (OWN) return null;
    const out = {}, hipY = KEYED.HIP + (k.drop || 0), hipZ = k.zoff || 0;
    for (const s of ['R', 'L']) {
        const spec = k['leg' + s], hip = [KEYED.SIDE[s], hipY, hipZ];
        if (!spec.ik) { out[s] = v3.add(hip, v3.mul(v3.norm(spec.dir || [0, 1, 0]), KEYED.THIGH)); continue; }
        let d = v3.sub(spec.ik, hip), len = v3.len(d);
        const most = KEYED.THIGH + KEYED.SHIN - 0.01;
        if (len > most) { d = v3.mul(v3.norm(d), most); len = most; }
        const u = v3.norm(d), hint = spec.hint || [0, -0.4, -1];
        const a = (KEYED.THIGH ** 2 - KEYED.SHIN ** 2 + len * len) / (2 * len), hh = Math.sqrt(Math.max(0, KEYED.THIGH ** 2 - a * a));
        out[s] = v3.add(hip, v3.add(v3.mul(u, a), v3.mul(v3.norm(v3.sub(hint, v3.mul(u, v3.dot(hint, u)))), hh)));
    }
    return out;
}
/**
 * A palm the keys put by a knee (within 3.5 px of vanilla's) goes with this rig's knee, then down the shin as far as keeps
 * it as far under the shoulders as the keys have it (B: the shoulders 2.5 px nearer the hips and the knee pulled up higher
 * on the longer shin, so both hands on it came up under the chin, the shoulders shrugged); one pushing on the ground comes
 * up by the larger hand's fingers (2.2 px on B); others stay.
 */
function palmOn(palm, b, keyed, k) {
    if (!palm || !keyed) return palm;
    if (palm[1] > 23) return [palm[0], palm[1] - 2.2, palm[2]];
    const lower = ((KEYED.HIP - KEYED.SHOULDER) - (GEO.HIP - GEO.SHOULDER)) * Math.cos(rad((k.tip || 0) + (k.lean || 0)));
    for (const s of ['R', 'L']) {
        if (v3.len(v3.sub(palm, keyed[s])) >= 3.5) continue;
        const on = v3.add(palm, v3.sub(b.knee[s], keyed[s])), want = palm[1] + lower, down = b.shin[s];
        if (on[1] >= want || down[1] < 0.2) return on;
        return v3.add(on, v3.mul(down, Math.min((want - on[1]) / down[1], 0.6 * SHIN)));
    }
    return palm;
}
function fullKey(k) {
    const b = bodyOf(k), keyed = keyedKnees(k);
    const eulers = {};
    for (const n of BODY) eulers[n] = fileEuler(b[n]);
    const s = solveArms(eulers, b.pos, 'full', k, () => ({R: typeof k.palmR === 'function' ? k.palmR(b) : palmOn(k.palmR, b, keyed, k), L: typeof k.palmL === 'function' ? k.palmL(b) : palmOn(k.palmL, b, keyed, k)}));
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

// the sitting clips (body.sit_down, body.sit, body.stand_up: author_splint_v2.cjs; Block-Mesh V2's: author_ground_sit_b.cjs)
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

module.exports = {ID, DOWN, BODY, ARMS, bendOf, THIGH, SHIN, SIDE, GEO, OWN, legIk, legWorld, bodyOf, DUMMY, constClip, solveArms, fullKey, clipOf,
    SIT_L, kneeUp, SEAT, REST, STAND, SHIN_WORK, SIT_LEN, STAND_LEN, SIT_DOWN, SITTING, STAND_UP};
