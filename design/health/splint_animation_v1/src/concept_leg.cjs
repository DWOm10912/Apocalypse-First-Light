// Concept (not the shipped action): self-splinting a broken left lower leg, key poses -> a clip for the Blockbench render.
// Java model px (y down, ground 24, front -z, right side -x). Body FK by hand; legs on the ground by 2-bone IK; palms by the
// AFL Rig core's arm IK (a dummy prop per hand in the root frame). Writes concept_leg.clip.json + prints IK misses.
const L = require('D:/Minecraft Modding/AFL-survivor-v0/tools/medical-actions-v1a/lib.cjs');
const {fs, path, core, v3, q4, rad, rig, poser, between, fileEuler} = L;
const OUT = path.join(__dirname, 'concept_leg.clip.json');

const ID = [0, 0, 0, 1];
const DOWN = [0, 1, 0];
const conj = q4.conj;
const mul = q4.mul;

// ---------------------------------------------------------------- the leg (hip world point, ankle target, knee hint)
const THIGH = 6, SHIN = 4;
function legIk(hip, ankle, hint) {
    let d = v3.sub(ankle, hip), len = v3.len(d);
    if (len > THIGH + SHIN - 0.01) { d = v3.mul(v3.norm(d), THIGH + SHIN - 0.01); len = THIGH + SHIN - 0.01; ankle = v3.add(hip, d); }
    const u = v3.norm(d);
    const a = (THIGH * THIGH - SHIN * SHIN + len * len) / (2 * len), h = Math.sqrt(Math.max(0, THIGH * THIGH - a * a));
    const n = v3.norm(v3.sub(hint, v3.mul(u, v3.dot(hint, u))));
    const knee = v3.add(hip, v3.add(v3.mul(u, a), v3.mul(n, h)));
    return {thighDir: v3.norm(v3.sub(knee, hip)), shinDir: v3.norm(v3.sub(ankle, knee))};
}
/** World turns of thigh / shin / foot from directions; foot: 'flat' (on the sole), or toes-up angle (deg past the shin). */
function legWorld(thighDir, shinDir, foot) {
    // the thigh's twist: keep its front (-z) toward the body's front / up
    const thighW = between(DOWN, thighDir);
    const shinW = mul(between(q4.apply(thighW, DOWN), shinDir), thighW);
    let footW;
    if (foot === 'flat') footW = ID;
    else {
        // toes (rest -z) turned foot deg up from pointing straight ahead (-z) toward up (-y): 0 flat ahead, 90 straight up
        const toes = q4.apply(shinW, [0, 0, -1]);
        const want = v3.norm(v3.add(v3.mul([0, 0, -1], Math.cos(rad(foot))), v3.mul([0, -1, 0], Math.sin(rad(foot)))));
        footW = mul(between(toes, want), shinW);
    }
    return {thighW, shinW, footW};
}

// ---------------------------------------------------------------- one key pose -> local quats of every bone
const SIDE = {R: -1.9, L: 1.9};
function bodyOf(k) {
    const pelvisW = q4.x(rad(k.tip || 0));
    const chestL = mul(q4.x(rad(k.lean || 0)), q4.y(rad(k.yaw || 0)));
    const chestW = mul(pelvisW, chestL);
    const headL = mul(q4.x(rad(k.head || 0)), q4.y(rad(k.headYaw || 0)));
    const hipY = 12 + (k.drop || 0), hipZ = k.zoff || 0;
    const legs = {};
    for (const s of ['R', 'L']) {
        const spec = k['leg' + s];
        const hip = [SIDE[s], hipY, hipZ];
        let dirs;
        if (spec.ik) dirs = legIk(hip, spec.ik, spec.hint || [0, -0.4, -1]);
        else {
            // straight: along spec.dir, or down to the ground at its heel
            let dir = spec.dir;
            if (!dir) { const dy = 24 - hip[1] - 0.3; dir = [spec.x || 0, dy, -Math.sqrt(Math.max(0, 144 - dy * dy))]; }
            dir = v3.norm(dir);
            dirs = {thighDir: dir, shinDir: dir};
        }
        const w = legWorld(dirs.thighDir, dirs.shinDir, spec.foot === undefined ? 'flat' : spec.foot);
        legs[s] = {thigh: mul(conj(pelvisW), w.thighW), shin: mul(conj(w.thighW), w.shinW), foot: mul(conj(w.shinW), w.footW)};
    }
    return {
        pelvis: pelvisW, chest: chestL, head: headL,
        thigh_r: legs.R.thigh, shin_r: legs.R.shin, foot_r: legs.R.foot,
        thigh_l: legs.L.thigh, shin_l: legs.L.shin, foot_l: legs.L.foot,
        pos: [0, -(k.drop || 0), hipZ],
    };
}

// the body as a constant clip, the palms by IK
const DUMMY = {scale: 1 / 16, points: {p: [0, 0, 0]}};
function solveKey(k) {
    const b = bodyOf(k);
    const bones = {};
    for (const n of ['pelvis', 'chest', 'head', 'thigh_r', 'shin_r', 'foot_r', 'thigh_l', 'shin_l', 'foot_l']) {
        const e = fileEuler(b[n]);
        bones[n] = {rotation: {'0.0': e, '1.0': e}};
    }
    bones.pelvis.position = {'0.0': b.pos, '1.0': b.pos};
    const clips = core.loadClips(rig, {animations: {c: {animation_length: 1, bones}}});
    const props = [], hands = {right: [], left: []};
    for (const [side, key] of [['right', 'palmR'], ['left', 'palmL']]) {
        if (!k[key]) continue;
        const id = 'd_' + side;
        props.push({id, keys: [{at: 0, frame: 'root', point: 'p', pos: k[key], rot: k[side === 'right' ? 'rotR' : 'rotL'] || [0, 0, 0], blend: 0}]});
        hands[side].push({at: 0, target: 'prop:' + id + ':p', blend: 0, roll: 0, pole: side === 'right' ? (k.poleR || [-0.6, 1, 0.3]) : (k.poleL || [0.6, 1, 0.3])});
    }
    const json = {clip: 'c', mask: 'full', blend: {in: 0, out: 0}, props, hands};
    const act = core.actionFromJson(json, id => (id.startsWith('d_') ? DUMMY : null), x => clips[x] || null);
    const res = core.solveFrame(poser, {action: act, seconds: 0.5, time: 0, pitch: k.view || 0});
    const ro = core.readouts(res);
    const out = {};
    for (const n of ['pelvis', 'chest', 'head', 'thigh_r', 'shin_r', 'foot_r', 'thigh_l', 'shin_l', 'foot_l']) out[n] = fileEuler(b[n]);
    const L2 = res.pose.local;
    out.upper_arm_r = fileEuler(L2[rig.upperArmR]); out.forearm_r = fileEuler(L2[rig.forearmR]); out.hand_r = fileEuler(L2[rig.handR]);
    out.upper_arm_l = fileEuler(L2[rig.upperArmL]); out.forearm_l = fileEuler(L2[rig.forearmL]); out.hand_l = fileEuler(L2[rig.handL]);
    if (!k.palmR) { out.upper_arm_r = fileEuler(k.armR || ID); out.forearm_r = [0, 0, 0]; out.hand_r = [0, 0, 0]; }
    if (!k.palmL) { out.upper_arm_l = fileEuler(k.armL || ID); out.forearm_l = [0, 0, 0]; out.hand_l = [0, 0, 0]; }
    return {euler: out, pos: b.pos, miss: {R: ro.right.mode === 'ik' ? +ro.right.off.toFixed(2) : '-', L: ro.left.mode === 'ik' ? +ro.left.off.toFixed(2) : '-',
        wrR: ro.right.mode === 'ik' ? Math.round(ro.right.wrist) : '-', wrL: ro.left.mode === 'ik' ? Math.round(ro.left.wrist) : '-'}};
}

// ---------------------------------------------------------------- the key poses
// view: the look pitch (deg down) the director holds; the head follows it and the chest bends 0.3 x it on top of lean
const SIT_L = {dir: [0, 0, -1], foot: 78};               // the broken leg out straight on the ground, toes up
const kneeUp = (z, x) => ({ik: [x || -2.6, 22, z], hint: [0, -1, -0.4]});   // the good knee up, its foot flat
const tieZ = s => s - 10;                                   // a tie s px from the sole, the hip 2 px back
const K = [];
const key = (t, k) => K.push(Object.assign({t}, k));
const SIT = {drop: 10, zoff: 2, legR: kneeUp(-3.5), legL: SIT_L};

// A. sit down (its own clip, also the sit key's): weight on the good leg, the right hand down to the ground, the broken
// leg slid out ahead, heel down
key(0.0, {view: 0, legR: {ik: [-1.9, 22, 0]}, legL: {ik: [1.9, 22, 0]}});
key(0.35, {view: 15, drop: 3, zoff: 0.5, tip: 14, lean: 0, legR: {ik: [-1.9, 22, 0.3], hint: [0, -0.3, -1]}, legL: {foot: 25},
    palmR: [-7, 16, 2], palmL: [6, 12, -5]});
key(0.7, {view: 25, drop: 7, zoff: 1.5, tip: 26, lean: 0, legR: {ik: [-1.9, 22, 0.2], hint: [0, -0.3, -1]}, legL: {foot: 30},
    palmR: [-6.2, 21.8, 2.5], palmL: [5.5, 13, -7]});
key(1.05, Object.assign({}, SIT, {view: 45, tip: 6, lean: -4, palmR: [-6.2, 23.6, 3.5], palmL: [3.6, 19.6, -0.5]}));
// B. lay the splint on, open it to both sides of the shin
key(1.4, Object.assign({}, SIT, {view: 65, tip: 12, lean: 0, palmR: [1.6, 16.5, -5.5], palmL: [3.6, 19.6, -1.5]}));
key(1.75, Object.assign({}, SIT, {view: 75, tip: 14, lean: 0, palmR: [1.2, 18.8, -5.8], palmL: [2.8, 18.8, -5.0]}));
key(2.1, Object.assign({}, SIT, {view: 75, tip: 14, lean: 0, palmR: [-1.0, 20.3, -5.8], palmL: [4.8, 20.3, -5.4]}));
key(2.3, Object.assign({}, SIT, {view: 75, tip: 14, lean: 0, palmR: [-0.9, 20.8, -5.6], palmL: [4.7, 20.8, -5.2]}));
// C. three ties: under the leg from the inside, the left hand takes the end on the outside, the knot on the outer top
const TIES = [{t0: 2.5, s: 1.6, tip: 25, lean: 4, view: 78}, {t0: 3.75, s: 4.2, tip: 19, lean: 2, view: 76}, {t0: 5.0, s: 6.9, tip: 12, lean: 0, view: 72}];
for (const T of TIES) {
    const z = tieZ(T.s), base = Object.assign({}, SIT, {tip: T.tip, lean: T.lean, view: T.view});
    const hold = z < -6 ? z + 1.8 : z - 1.8;
    key(T.t0, Object.assign({}, base, {palmR: [-0.9, 22.6, z], palmL: [4.6, 20.6, hold]}));
    key(T.t0 + 0.35, Object.assign({}, base, {palmR: [-0.7, 22.7, z], palmL: [4.8, 22.6, z]}));
    key(T.t0 + 0.65, Object.assign({}, base, {palmR: [3.0, 19.0, z - 0.6], palmL: [4.4, 19.1, z + 0.6]}));
    key(T.t0 + 0.9, Object.assign({}, base, {palmR: [3.2, 19.1, z - 2.4], palmL: [4.4, 19.2, z + 2.2]}));
    key(T.t0 + 1.1, Object.assign({}, base, {palmR: [2.2, 18.3, z - 1.2], palmL: [4.2, 18.3, z + 1.0]}));
}
// D. check: hands back on the thigh, the toes move twice (blood flow), then sit back
const chk = Object.assign({}, SIT, {view: 70, tip: 6, lean: 0, palmR: [0.4, 19.6, -1.8], palmL: [3.6, 19.6, -1.0]});
key(6.35, Object.assign({}, chk));
key(6.5, Object.assign({}, chk, {legL: {dir: [0, 0, -1], foot: 58}}));
key(6.65, Object.assign({}, chk, {legL: {dir: [0, 0, -1], foot: 88}}));
key(6.8, Object.assign({}, chk, {legL: {dir: [0, 0, -1], foot: 58}}));
key(6.95, Object.assign({}, chk, {legL: SIT_L, view: 55}));
// E. stand up (its own clip, also the sit key's): the good foot pulled in, both hands push on the good knee, the broken
// leg kept straight, heel down
key(7.2, Object.assign({}, SIT, {view: 25, tip: 22, lean: 6, legR: kneeUp(-1.5, -2.2), palmR: [-2.6, 16.6, -4.2], palmL: [-0.8, 16.8, -4.0]}));
key(7.5, {view: 15, drop: 6, zoff: 0.8, tip: 32, lean: 2, legR: {ik: [-2.2, 22, -1.5], hint: [0, -0.6, -1]}, legL: {foot: 28},
    palmR: [-2.8, 15.5, -3.6], palmL: [-1.0, 15.7, -3.4]});
key(7.75, {view: 5, drop: 2, zoff: 0.2, tip: 12, lean: 0, legR: {ik: [-1.9, 22, -0.8], hint: [0, -0.3, -1]}, legL: {foot: 15}, palmR: null, palmL: null,
    armR: q4.x(rad(-15)), armL: q4.x(rad(-12))});
key(8.0, {view: 0, drop: 0.3, zoff: 0, tip: 2, lean: 0, legR: {ik: [-1.9, 22, 0]}, legL: {foot: 6}, armR: ID, armL: ID});


// ---------------------------------------------------------------- write
const bones = {};
const rows = [];
for (const k of K) {
    const s = solveKey(k);
    for (const [n, e] of Object.entries(s.euler)) ((bones[n] = bones[n] || {rotation: {}}).rotation[k.t.toFixed(2)] = e);
    ((bones.pelvis.position = bones.pelvis.position || {})[k.t.toFixed(2)] = s.pos);
    rows.push(k.t.toFixed(2) + ' miss R ' + s.miss.R + ' L ' + s.miss.L + ' wrist R ' + s.miss.wrR + ' L ' + s.miss.wrL);
}
// unwrap euler jumps per channel
for (const b of Object.values(bones)) {
    const ts = Object.keys(b.rotation).sort((a, c) => a - c);
    for (let i = 1; i < ts.length; i++) for (let j = 0; j < 3; j++) {
        while (b.rotation[ts[i]][j] - b.rotation[ts[i - 1]][j] > 180) b.rotation[ts[i]][j] -= 360;
        while (b.rotation[ts[i]][j] - b.rotation[ts[i - 1]][j] < -180) b.rotation[ts[i]][j] += 360;
    }
}
const view = {}; for (const k of K) view[k.t.toFixed(2)] = k.view || 0;
fs.writeFileSync(OUT, JSON.stringify({length: 8.0, ties: TIES.map(T => T.t0), view, bones}, null, 1));
console.log(rows.join('\n'));
console.log('written', OUT);
