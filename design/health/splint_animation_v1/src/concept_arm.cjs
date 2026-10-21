// Concept (not the shipped action): self-splinting a broken left forearm, standing. Key poses -> a clip for the Blockbench
// render. Java model px (y down, front -z). The left arm by FK (directions in the chest frame); the right palm by the core's
// IK at points round the posed left forearm; the knot pulled between the teeth and the right hand (the forearm comes up to
// the mouth). Writes concept_arm.clip.json.
const L = require('D:/Minecraft Modding/AFL-survivor-v0/tools/medical-actions-v1a/lib.cjs');
const {fs, path, core, v3, q4, rad, rig, poser, armFk, fileEuler} = L;
const OUT = path.join(__dirname, 'concept_arm.clip.json');
const ID = [0, 0, 0, 1];
const DUMMY = {scale: 1 / 16, points: {p: [0, 0, 0]}};
const BODY = ['pelvis', 'chest', 'head', 'thigh_r', 'shin_r', 'foot_r', 'thigh_l', 'shin_l', 'foot_l'];

// left arm poses (chest frame): low = across the belly, a hand's width out; up = raised to the mouth
const ARM = {
    low: {u: [-0.1, 0.42, -0.9], f: [-0.62, 0.12, -0.78]},
    up1: {u: [0.05, 0.45, -1], f: [-1, -0.75, -0.15]},
    up2: {u: [-0.1, 0.3, -1], f: [-1, -0.8, 0.1]},
    up3: {u: [-0.3, 0.1, -1], f: [-1, -0.7, 0.3]},
    up: {u: [0.05, 0.45, -1], f: [-1, -0.75, -0.15]},
    rest: null,
};

function clipOf(k, wiggle) {
    const bones = {};
    const put = (n, q) => { const e = fileEuler(q); bones[n] = {rotation: {'0.0': e, '1.0': e}}; };
    put('chest', q4.x(rad(k.lean || 0)));
    for (const n of BODY) if (!bones[n]) put(n, ID);
    const a = typeof k.armL === 'string' ? ARM[k.armL] : k.armL;
    if (a) {
        const fk = armFk(a.u, a.f, null, a.twist || 0);
        put('upper_arm_l', fk.upper); put('forearm_l', fk.fore); put('hand_l', q4.mul(fk.hand, q4.x(rad(wiggle || 0))));
    } else { put('upper_arm_l', ID); put('forearm_l', ID); put('hand_l', ID); }
    return bones;
}
function solve(k, palmR) {
    const bones = clipOf(k, k.wiggle);
    const clips = core.loadClips(rig, {animations: {c: {animation_length: 1, bones}}});
    const props = [], hands = {right: [], left: []};
    if (palmR) {
        props.push({id: 'd_r', keys: [{at: 0, frame: 'root', point: 'p', pos: palmR, rot: k.rotR || [0, 0, 0], blend: 0}]});
        hands.right.push({at: 0, target: 'prop:d_r:p', blend: 0, roll: 0, pole: k.poleR || [-0.8, 1, 0.5]});
    }
    const act = core.actionFromJson({clip: 'c', mask: 'full', blend: {in: 0, out: 0}, props, hands}, () => DUMMY, x => clips[x] || null);
    return {res: core.solveFrame(poser, {action: act, seconds: 0.5, time: 0, pitch: k.view || 0}), bones};
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
const mouthOf = res => res.pose.point(rig.head, [0, -1.5, -4.3]);

const K = [];
const key = (t, k) => K.push(Object.assign({t}, k));
const LOW = {armL: 'low', view: 50, lean: 0};
key(0.0, {view: 0});
key(0.6, Object.assign({}, LOW, {at: [4, 0, 4.5]}));
// lay the slats: the first between the forearm and the belly (palm side), the second on the outside, pressed together
key(1.0, Object.assign({}, LOW, {at: [4, 260, 3.4]}));
key(1.3, Object.assign({}, LOW, {at: [4, 70, 2.8]}));
key(1.65, Object.assign({}, LOW, {at: [4, 20, 3.0]}));
// three ties, wrist to elbow: under, round the outside, over the top; the forearm up to the mouth, the teeth hold one end,
// the right hand pulls the other
const TIES = [{t0: 2.0, s: 1.6, up: 'up1'}, {t0: 3.4, s: 4.0, up: 'up2'}, {t0: 4.8, s: 6.5, up: 'up3'}];
for (const T of TIES) {
    key(T.t0, Object.assign({}, LOW, {at: [T.s, 215, 2.4]}));
    key(T.t0 + 0.3, Object.assign({}, LOW, {at: [T.s, 70, 2.6]}));
    key(T.t0 + 0.55, Object.assign({}, LOW, {view: 40, at: [T.s, 10, 3.0]}));
    key(T.t0 + 0.8, {armL: T.up, view: 20, lean: 2, at: [T.s, 60, 5.0]});
    key(T.t0 + 1.0, {armL: T.up, view: 20, lean: 2, at: [T.s, 70, 6.0]});
    key(T.t0 + 1.25, Object.assign({}, LOW, {at: [T.s, 30, 4.0]}));
}
// check: the fingers move (the hand flexes twice), the right hand under the forearm
key(6.2, Object.assign({}, LOW, {at: [5, 215, 2.4]}));
key(6.35, Object.assign({}, LOW, {at: [5, 215, 2.4], wiggle: 18}));
key(6.5, Object.assign({}, LOW, {at: [5, 215, 2.4], wiggle: -8}));
key(6.65, Object.assign({}, LOW, {at: [5, 215, 2.4], wiggle: 18}));
key(6.8, Object.assign({}, LOW, {at: [5, 215, 2.4]}));
// carry it close to the body a moment, then let it down
key(7.3, {armL: {u: [0.1, 1, -0.3], f: [-0.9, -0.1, -0.5]}, view: 20, lean: 2, at: [4, 180, 3.0]});
key(8.0, {view: 0});

const out = {length: 8.0, ties: TIES.map(T => T.t0), view: {}, bones: {}};
const rows = [];
for (const k of K) {
    let palm = null, base = solve(k, null);
    if (k.at) palm = around(base.res, ...k.at);
    const s = palm ? solve(k, palm) : base;
    const P = s.res.pose.local;
    const e = Object.assign({}, Object.fromEntries(Object.entries(s.bones).map(([n, b]) => [n, b.rotation['0.0']])));
    if (palm) { e.upper_arm_r = fileEuler(P[rig.upperArmR]); e.forearm_r = fileEuler(P[rig.forearmR]); e.hand_r = fileEuler(P[rig.handR]); }
    else { e.upper_arm_r = [0, 0, 0]; e.forearm_r = [0, 0, 0]; e.hand_r = [0, 0, 0]; }
    const tk = k.t.toFixed(2);
    for (const [n, v] of Object.entries(e)) ((out.bones[n] = out.bones[n] || {rotation: {}}).rotation[tk] = v);
    out.view[tk] = k.view || 0;
    const ro = core.readouts(s.res);
    let note = '';
    if (String(k.armL).startsWith('up')) note = ' tie-mouth ' + v3.dist(around(s.res, k.at[0], 0, 2), mouthOf(s.res)).toFixed(1);
    rows.push(tk + ' miss R ' + (ro.right.mode === 'ik' ? ro.right.off.toFixed(2) + ' wrist ' + Math.round(ro.right.wrist) : '-') + note);
}
for (const b of Object.values(out.bones)) {
    const ts = Object.keys(b.rotation).sort((a, c) => a - c);
    for (let i = 1; i < ts.length; i++) for (let j = 0; j < 3; j++) {
        while (b.rotation[ts[i]][j] - b.rotation[ts[i - 1]][j] > 180) b.rotation[ts[i]][j] -= 360;
        while (b.rotation[ts[i]][j] - b.rotation[ts[i - 1]][j] < -180) b.rotation[ts[i]][j] += 360;
    }
}
fs.writeFileSync(OUT, JSON.stringify(out, null, 1));
console.log(rows.join('\n'));
