// Authoring sheet for item/bandage_wrap (keyframes chosen by hand below; this only does the geometry for them):
// the left arm's FK keys, the roll's keys (pivot on the forearm's middle line), the right hand's targets, and the right
// arm's FK keys for the moments the hand lets go of the roll (solved as IK once, stored as clip keys).
// node author_bandage.cjs [--write]  -> prints the check numbers; --write writes the clip group, the action and the prop
// into the D worktree (the plugin then imports, shows and re-exports them).
const L = require('./lib.cjs');
const {fs, path, core, v3, q4, tf, rad, deg, A, rig, poser, armFk, fileEuler} = L;
const WRITE = process.argv.includes('--write');

// ------------------------------------------------------------------ the hand-picked numbers
const LEN = 4.0;                               // the use (MedicalItem.BANDAGE_TICKS 80)
const OV = process.env.OV ? JSON.parse(process.env.OV) : {};
const CHEST_TWIST = OV.twist ?? 0;                        // deg, the chest turned so the left forearm comes in front (q y +)
const LEFT = {u: OV.u || [-0.15, 0.62, -0.77], f: OV.f || [-0.45, 0.35, -0.82]};   // upper arm and forearm directions in the chest frame
const R_ROLL = 4.2, R_PINCH = 2.6;             // the roll's middle off the forearm's middle line; the palm off the roll's middle
const WRAP_FROM = 0.6, TURN = 2.5 / 3;          // BandageWrapRenderer WRAP_FROM / WRAP_SECONDS
const S_TURN = OV.s || [1.5, 0.5, -0.5];               // each turn's place along the forearm from the band's middle (+ = toward the wrist)
const REL = 50, OUTER = -90, CATCH = OV.catch ?? 105;      // orbit angles (0 = top, 90 = the inner side, toward the right hand)
const HAND_ROLL = 0;                           // the right hand's turn about its forearm on the roll
const POLE = OV.pole || [-0.6, 1, 0.4];                  // the right elbow: down and out, not back (the default [-0.35, 1, 0.45] lifts it out sideways here)

// ------------------------------------------------------------------ prop
const propPath = path.join(A, 'player_props/bandage.json');
const prop = L.read(propPath);
const K = 16 * prop.scale;
const centreY = prop.points.center[1];
prop.points.axis = [0.5, +(centreY - R_ROLL / K).toFixed(4), prop.points.center[2]];
prop.points.pinch = [0.5, +(centreY + R_PINCH / K).toFixed(4), prop.points.center[2]];
const local = n => core.propLocal(prop, n);

// ------------------------------------------------------------------ the left arm (FK)
const fkL = armFk(LEFT.u, LEFT.f, null, 0);
const twistQ = q4.y(rad(CHEST_TWIST));
// the band's middle line in the chest frame, from the posed skeleton
function clipFile(bones) { return {animations: {'medical.bandage_wrap': {animation_length: LEN, bones}}}; }
function actionOf(json, clipBones) {
    const clips = core.loadClips(rig, clipFile(clipBones));
    return core.actionFromJson(json, id => (id === 'bandage' ? prop : id === 'dummy' ? DUMMY : null), k => clips[k] || null);
}
const DUMMY = {scale: 1 / 16, points: {p: [0, 0, 0]}};
const E = q => fileEuler(q);
function unwrap(seq) {   // Euler triples made continuous key to key
    for (let i = 1; i < seq.length; i++) for (let k = 0; k < 3; k++) {
        while (seq[i][1][k] - seq[i - 1][1][k] > 180) seq[i][1][k] -= 360;
        while (seq[i][1][k] - seq[i - 1][1][k] < -180) seq[i][1][k] += 360;
    }
    return seq;
}
const keysOf = seq => { const o = {}; for (const [t, v] of unwrap(seq)) o[t.toFixed(4).replace(/0+$/, '').replace(/\.$/, '.0')] = v.map(x => +x.toFixed(3)); return o; };

const OUT_T = 0.45, HOLD_T = 3.75;
const leftBones = {
    chest: {rotation: keysOf([[0, [0, 0, 0]], [OUT_T, E(twistQ)], [HOLD_T, E(twistQ)], [LEN, [0, 0, 0]]])},
    upper_arm_l: {rotation: keysOf([[0, [0, 0, 0]], [OUT_T, E(fkL.upper)], [HOLD_T, E(fkL.upper)], [LEN, E(q4.slerp(fkL.upper, [0, 0, 0, 1], 0.6))]])},
    forearm_l: {rotation: keysOf([[0, [0, 0, 0]], [OUT_T, E(fkL.fore)], [HOLD_T, E(fkL.fore)], [LEN, E(q4.slerp(fkL.fore, [0, 0, 0, 1], 0.5))]])},
};
// measure the band on the posed arm (seconds 1, pitch 0)
const probe = core.solveFrame(poser, {action: actionOf({clip: 'medical.bandage_wrap', mask: 'upper', blend: {in: 0, out: 0}, hands: {}}, leftBones), seconds: 1});
const chestInv = tf.inv(probe.pose.world[rig.chest]);
const P = (b, p) => tf.apply(chestInv, probe.pose.point(b, p));
const ELBOW = P(rig.forearmL, [6, 6, 0]), WRIST = P(rig.handL, [6, 10, 0]);
const F = v3.norm(v3.sub(WRIST, ELBOW));
const C = P(rig.forearmL, [6, 12 - 3.5, 0]);
// in the chest frame as twisted: the chest frame of props is the chest bone's (the twist included), so C, F are right as they are
let UP = v3.norm(v3.sub([0, -1, 0], v3.mul(F, -F[1])));
let IN = v3.norm(v3.cross(F, UP));
if (IN[0] > 0) IN = v3.mul(IN, -1);
const dirAt = th => v3.add(v3.mul(UP, Math.cos(rad(th))), v3.mul(IN, Math.sin(rad(th))));
/** The roll's turn at orbit angle th: its axis along the forearm, its middle out along dirAt(th). */
function rollTurn(th) {
    const e = dirAt(th), cx = F, cy = v3.mul(e, -1), cz = v3.cross(cx, cy);
    return q4.norm(q4.fromBasis(cx, cy, cz));
}
const rotOf = q => q4.eulerXYZ(q).map(deg).map(x => +x.toFixed(3));
const axisAt = s => v3.add(C, v3.mul(F, s));
const sAt = t => S_TURN[0] + (S_TURN[2] - S_TURN[0]) * Math.min(1, Math.max(0, (t - WRAP_FROM) / (3 * TURN)));
const r4 = x => +x.toFixed(4);

// ------------------------------------------------------------------ the roll's keys (at = start, blend = travel)
const rollKeys = [];
let lastT = 0;
function rollKey(arrive, k) {   // k: {point, pos, rot, hidden}
    rollKeys.push({at: r4(rollKeys.length ? lastT : 0), frame: 'chest', point: k.point, pos: k.pos.map(r4), rot: k.rot, blend: r4(rollKeys.length ? arrive - lastT : 0), hidden: k.hidden});
    lastT = arrive;
}
function holdUntil(t) { const k = rollKeys[rollKeys.length - 1]; rollKeys.push(Object.assign({}, k, {at: r4(lastT), blend: r4(t - lastT)})); lastT = t; }
const orbitKey = (t, th, r, hidden) => {   // the axis point on the middle line (r = R_ROLL) or moved out/in along dirAt for r != R_ROLL
    const pos = v3.add(axisAt(sAt(t)), v3.mul(dirAt(th), r - R_ROLL));
    rollKey(t, {point: 'axis', pos, rot: rotOf(rollTurn(th)), hidden});
};
// rest: the roll in front of the right palm (rest palm -6, 10.5, 0)
{
    const R0 = q4.fromBasis([1, 0, 0], [0, 0, -1], [0, 1, 0]);
    rollKey(0, {point: 'pinch', pos: [-6, 10.5, 0], rot: rotOf(R0)});
}
orbitKey(OUT_T, REL, R_ROLL);            // up to the forearm, on its inner top
orbitKey(0.53, REL, 3.75);               // presses the loose end on
orbitKey(WRAP_FROM, REL, R_ROLL);
const turns = [];
for (let i = 0; i < 3; i++) {
    const T0 = WRAP_FROM + i * TURN;
    orbitKey(T0 + 0.25, OUTER, R_ROLL);        // over the top and down the far side, on its own
    orbitKey(T0 + 0.5, CATCH, R_ROLL);         // under the forearm to the hand
    orbitKey(T0 + TURN, REL, R_ROLL);          // the hand brings it up the inner side
    turns.push(T0);
}
const END = WRAP_FROM + 3 * TURN;        // 3.1
orbitKey(3.25, 60, 3.6);                 // the end laid on the inner top
// the roll put away (hidden from here: the end torn off and tucked); its pinch now marks where the palm presses
const pressTurn = rotOf(rollTurn(70));
const pressAt = r => v3.add(axisAt(-0.3), v3.mul(dirAt(70), r));
rollKey(3.3, {point: 'pinch', pos: pressAt(4.95), rot: pressTurn, hidden: true});
rollKey(3.42, {point: 'pinch', pos: pressAt(4.5), rot: pressTurn, hidden: true});
rollKey(3.54, {point: 'pinch', pos: pressAt(4.95), rot: pressTurn, hidden: true});
rollKey(3.66, {point: 'pinch', pos: pressAt(4.5), rot: pressTurn, hidden: true});
for (const k of rollKeys) if (!k.hidden) delete k.hidden;

// ------------------------------------------------------------------ the right hand
const handKeys = [{at: 0, target: 'prop:pinch', blend: 0, roll: HAND_ROLL, pole: POLE}];
for (const T0 of turns) {
    handKeys.push({at: r4(T0), target: 'pose', blend: 0.1, roll: HAND_ROLL, pole: POLE});           // lets go at the top
    handKeys.push({at: r4(T0 + 0.4), target: 'prop:pinch', blend: 0.1, roll: HAND_ROLL, pole: POLE});   // takes it under the forearm
}
handKeys.push({at: 3.75, target: 'pose', blend: 0.25, roll: HAND_ROLL, pole: POLE});

// the right arm's FK while the hand is off the roll: IK solved once at these palm places, kept as clip keys
function ikLocals(t, palm) {
    const json = {clip: 'medical.bandage_wrap', mask: 'upper', blend: {in: 0, out: 0},
        props: [{id: 'dummy', keys: [{at: 0, frame: 'chest', point: 'p', pos: palm, rot: [0, 0, 0], blend: 0}]}],
        hands: {right: [{at: 0, target: 'prop:dummy:p', blend: 0, roll: HAND_ROLL, pole: POLE}]}};
    const res = core.solveFrame(poser, {action: actionOf(json, leftBones), seconds: t});
    return [res.pose.local[rig.upperArmR], res.pose.local[rig.forearmR], res.pose.local[rig.handR]];
}
const pinchAt = (t, th) => v3.add(axisAt(sAt(t)), v3.mul(dirAt(th), R_ROLL + R_PINCH));
const rightSeq = [[], [], []];
const addR = (t, locals) => locals.forEach((q, i) => rightSeq[i].push([t, E(q)]));
addR(0, [[0, 0, 0, 1], [0, 0, 0, 1], [0, 0, 0, 1]]);
for (const T0 of turns) {
    addR(T0, ikLocals(T0, pinchAt(T0, REL)));
    addR(T0 + 0.2, ikLocals(T0 + 0.2, v3.add(axisAt(sAt(T0 + 0.2)), v3.mul(dirAt(95), R_ROLL + R_PINCH + 0.6))));
    addR(T0 + 0.4, ikLocals(T0 + 0.4, pinchAt(T0 + 0.5, CATCH)));
}
addR(HOLD_T, ikLocals(HOLD_T, pressAt(4.5)));
addR(LEN, [q4.x(rad(-20)), q4.x(rad(-15)), [0, 0, 0, 1]]);
const bones = Object.assign({}, leftBones, {
    upper_arm_r: {rotation: keysOf(rightSeq[0])}, forearm_r: {rotation: keysOf(rightSeq[1])}, hand_r: {rotation: keysOf(rightSeq[2])},
});

// ------------------------------------------------------------------ the action
const action = {
    format_version: 1,
    about: 'Health V1a: wrapping a bandage (item use 4 s, MedicalItem.BANDAGE_TICKS), first and third person alike. Authored in Blockbench with the AFL Rig plugin (src/main/blockbench/player_actions/medical_bandage_wrap.bbmodel). The left forearm comes out in front of the chest (FK clip keys, the chest not turned); the right hand brings the roll (player_props/bandage, its axis point kept on the forearm\'s middle line, so between keys it swings round the forearm) to the forearm\'s inner top and presses the loose end on (0.45-0.6 s), then three turns of 0.83 s from 0.6 to 3.1 s with BandageWrapRenderer\'s band (each: let go at the inner top, the roll goes over and down the far side and under on its own, the hand takes it under the forearm and brings it up the inner side); the end is laid on (3.1-3.3 s), the roll is put away and the palm presses the wrap twice (3.3-3.75 s); both arms come down from 3.75 s. docs/gameplay/survival_health_v1a.md 7',
    afl_rig: {plugin: core.VERSION, animation: 'medical.bandage_wrap'},
    clip: 'medical.bandage_wrap',
    layer: 'action', mask: 'upper', priority: 50, first_person: 'body', blend: {in: 0.3, out: 0.3},
    first_person_pitch: 65,
    prop: {id: 'bandage', keys: rollKeys},
    hands: {right: handKeys, left: []},
    events: [],
    check: {use_ticks: 80},
};

// ------------------------------------------------------------------ check
const act = actionOf(action, bones);
const CHEST = [-4.25, 4.25, 0, 12, -2.25, 2.25];
const bd = (p, b) => Math.hypot(Math.max(b[0] - p[0], 0, p[0] - b[1]), Math.max(b[2] - p[1], 0, p[1] - b[3]), Math.max(b[4] - p[2], 0, p[2] - b[5]));
const segDist = (p, a, b) => { const ab = v3.sub(b, a), t = Math.max(0, Math.min(1, v3.dot(v3.sub(p, a), ab) / v3.dot(ab, ab))); return v3.dist(p, v3.add(a, v3.mul(ab, t))); };
const rows = [];
let worst = {roll: 99, rollAt: 0, hand: 99, handAt: 0, chestHand: 99, chestHandAt: 0, off: 0, offAt: 0, wrist: 0, twist: 0};
for (let t = 0; t <= LEN + 1e-6; t += 0.025) {
    const res = core.solveFrame(poser, {action: act, seconds: t, time: t, pitch: 0});
    const inv = tf.inv(res.pose.world[rig.chest]);
    const Q = (b, p) => tf.apply(inv, res.pose.point(b, p));
    const el = Q(rig.forearmL, [6, 6, 0]), wr = Q(rig.handL, [6, 10, 0]), cap = Q(rig.handL, [6, 12, 0]);
    const placed = res.props[0], hidden = res.hidden[0];
    const centre = placed ? tf.apply(inv, tf.apply(placed, local('center'))) : null;
    const palmR = Q(rig.handR, [-6, 10.5, 0]);
    const ro = core.readouts(res);
    if (centre && !hidden && t > 0.4) {
        const d = segDist(centre, el, cap) - 1.0;   // roll surface to the forearm's middle line
        if (d < worst.roll) { worst.roll = d; worst.rollAt = t; }
    }
    if (t > 0.4 && t < 3.8) {
        const dh = segDist(palmR, el, cap);
        if (dh < worst.hand) { worst.hand = dh; worst.handAt = t; }
    }
    const dc = bd(palmR, CHEST);
    if (dc < worst.chestHand) { worst.chestHand = dc; worst.chestHandAt = t; }
    const r = ro.right;
    if (r.mode === 'ik' && r.w > 0.99 && r.off > worst.off) { worst.off = r.off; worst.offAt = t; }
    if (r.mode === 'ik' && r.w > 0.5) { worst.wrist = Math.max(worst.wrist, r.wrist); worst.twist = Math.max(worst.twist, r.twist); }
    if (Math.abs(t * 10 - Math.round(t * 10)) < 1e-6) rows.push(`${t.toFixed(1)} roll ${centre ? centre.map(x => x.toFixed(1)).join(',') : '-'}${hidden ? ' (hidden)' : ''} palmR ${palmR.map(x => x.toFixed(1)).join(',')} ${r.mode === 'ik' ? 'w ' + r.w.toFixed(2) + ' off ' + r.off.toFixed(2) + ' wrist ' + r.wrist.toFixed(0) + ' twist ' + r.twist.toFixed(0) : 'clip'}`);
}
console.log('band middle', C.map(x => +x.toFixed(2)), 'forearm', F.map(x => +x.toFixed(3)), 'elbow', ELBOW.map(x => +x.toFixed(2)), 'wrist', WRIST.map(x => +x.toFixed(2)));
console.log(rows.join('\n'));
console.log(JSON.stringify(worst, (k, v) => (typeof v === 'number' ? +v.toFixed(2) : v)));

// first person: screen angles (deg, x right, y up) and distance from the camera at the eye, looking down FP_PITCH
const FP_PITCH = +(process.env.FP || action.first_person_pitch);
function screen(Pm) {
    const p = rad(FP_PITCH), cam = [0, core.eyeModelY(false), 0], rel = v3.sub(Pm, cam);
    const fwd = [0, Math.sin(p), -Math.cos(p)], up = [0, -Math.cos(p), -Math.sin(p)], right = [-1, 0, 0];
    const f = v3.dot(rel, fwd);
    return `${(Math.atan2(v3.dot(rel, right), f) * 180 / Math.PI).toFixed(0)}/${(Math.atan2(v3.dot(rel, up), f) * 180 / Math.PI).toFixed(0)}@${v3.len(rel).toFixed(1)}`;
}
const fpRows = [];
for (const t of [0.3, 0.6, 0.75, 0.9, 1.1, 1.3, 2.0, 3.0, 3.5]) {
    const res = core.solveFrame(poser, {action: act, seconds: t, time: t, pitch: FP_PITCH});
    const placed = res.props[0];
    fpRows.push(`${t} roll ${placed ? screen(tf.apply(placed, local('center'))) : '-'} palmR ${screen(res.pose.point(rig.handR, [-6, 10.5, 0]))} band ${screen(res.pose.point(rig.forearmL, [6, 8.5, 0]))} capL ${screen(res.pose.point(rig.handL, [6, 12, 0]))} elbowL ${screen(res.pose.point(rig.forearmL, [6, 6, 0]))}`);
}
console.log('FP at ' + FP_PITCH);
console.log(fpRows.join(String.fromCharCode(10)));

if (WRITE) {
    const pretty = o => JSON.stringify(o, null, 2) + '\n';
    const clipPath = path.join(A, 'animations/player/medical.animation.json');
    let clipFileJson = fs.existsSync(clipPath) ? L.read(clipPath) : {format_version: '1.8.0', afl_rig: {about: 'Written by the AFL Rig Blockbench plugin (tools/blockbench/afl_rig.js). Edit in Blockbench, not by hand.'}, animations: {}};
    clipFileJson.animations['medical.bandage_wrap'] = {animation_length: LEN, bones};
    fs.writeFileSync(clipPath, pretty(clipFileJson));
    fs.writeFileSync(path.join(A, 'player_actions/item/bandage_wrap.json'), pretty(action));
    fs.writeFileSync(propPath, pretty(prop));
    console.log('written');
}
