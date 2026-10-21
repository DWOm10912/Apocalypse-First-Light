// Authoring sheet for item/splint_arm and item/splint_leg (fractures and splints V1, docs/gameplay/fracture_splint_v1_plan.md):
// the keyframes are chosen by hand below; this does the geometry for them, as author_bandage.cjs does for the bandage.
// node author_splint.cjs arm|leg [--write]  -> prints the check numbers; --write writes the clip, the action and the prop.
//
// Both are keyed on the left limb (SkeletonActions mirrors them for a right one) and run 8 s (SplintItem.USE_TICKS 160).
// The schedule is client/BandageWrapRenderer's (SPLINT_SLATS_AT, SPLINT_TIE_FROM, SPLINT_TIE_TURN): the right hand lays the
// splint (player_props/splint) on the limb's outer side; at 1.3 s it is hidden and the renderer draws the two slats there;
// the right hand fetches the gauze roll (player_props/bandage) and ties three ties, distal to near, one turn each from 2.0,
// 3.75 and 5.5 s (0.83 s a turn: the renderer's tie grows by the clock from the limb's front, the roll 30 deg ahead of it),
// a short tug for the knot after each; the roll is put away at 6.7 s, the palm presses the splint twice, the arms come back.
// The arm: the left forearm held out in front of the chest (as the bandage). The leg: sat on the ground, the left leg out
// straight, the right one out beside it, leaning over it; the left hand holds the splint against the shin, the roll goes
// under the leg out of sight (pushed under); the first-person camera sits on the posed eye (first_person_eye).
const L = require('./lib.cjs');
const {fs, path, core, v3, q4, tf, rad, deg, A, rig, poser, armFk, fileEuler, between} = L;
const LIMB = process.argv[2];
if (LIMB !== 'arm' && LIMB !== 'leg') throw new Error('usage: node author_splint.cjs arm|leg [--write]');
const WRITE = process.argv.includes('--write');
const ARM = LIMB === 'arm';
const OV = process.env.OV ? JSON.parse(process.env.OV) : {};

// ------------------------------------------------------------------ the schedule (BandageWrapRenderer)
const LEN = 8.0;
const SLATS_AT = 1.3, ROLL_AT = 1.6, TIE_FROM = [2.0, 3.75, 5.5], TURN = 2.5 / 3, PUT_AWAY = 6.7, HOLD_T = 7.4;
const TRAIL = 30;                                   // the roll ahead of the tie's open end (deg), BandageWrapRenderer TRAIL
// the slats and ties along the limb, px back from its distal end (BandageWrapRenderer ARM_SLAT / ARM_TIES, LEG_*)
const SLAT = ARM ? [0.8, 7.2] : [0.8, 7.6], TIES = ARM ? [1.6, 4.0, 6.5] : [1.6, 4.2, 6.9];
const SLAT_MID = (SLAT[0] + SLAT[1]) / 2;
// orbit angles round the limb (0 = up, 90 = the inner side toward the right hand): where the renderer's loops start (the
// limb's rest front, -z) and where its outer slat lies (rest +x)
const START = ARM ? 50 : 0, OUTER_SLAT = ARM ? -40 : -90;
const R_ROLL = OV.r ?? 4.8, R_PINCH = 2.6, R_SPLINT = 2.55;
const AXIS_OFF = 4.2;                                 // player_props/bandage: its axis point this far under its middle
const LAY = OV.lay ?? (ARM ? 10 : -20);               // where the hand lays the splint on (then it is hidden, the outer slat drawn at OUTER_SLAT)
const KNOT = OV.knot ?? (ARM ? START : 40);          // where the knot is pulled (the leg's on its inner top, nearer the hand)
const CATCH_K = 4;                                  // of the six steps of a turn: the hand takes the roll again at this one
const POLE = OV.pole || (ARM ? [-0.6, 1, 0.4] : [-0.5, 1, 0.6]);
const POLE_L = OV.poleL || [0.5, 1, 0.6];

// ------------------------------------------------------------------ props
const bandage = L.read(path.join(A, 'player_props/bandage.json'));
const SPLINT_SCALE = 0.62, OFFSET = 0.475938;   // client/AflStaticMeshItemClient splint
const splintPath = path.join(A, 'player_props/splint.json');
const SK = 16 * SPLINT_SCALE;
const centreY = OFFSET + 0.225 / 16;           // the slats' middle (mesh y 0..0.45 px over the item's foot)
const gripY = +(centreY + (0.385 + 1.6 / SPLINT_SCALE) / 16).toFixed(4);   // the palm 1.6 px (drawn) off the band's top
const along = px => +(0.5 + px / SK).toFixed(4);   // px (drawn) along the slats (+: the item's +x)
const splint = {
    about: 'Fractures and splints V1 (docs/gameplay/fracture_splint_v1_plan.md): the splint in the hand while the player actions item/splint_arm and item/splint_leg lay it on a broken limb. Drawn as the splint\'s equipment mesh (tools/build-equipment-meshes-v1.mjs SPLINT: two slats 10.4 px long side by side along x, bound by a gauze band; item offset 0.475938, client/AflStaticMeshItemClient) at 0.62 (6.4 px, the slats\' length on the limb). Points (item block units): center, the slats\' middle; grip, 1.6 px (drawn) over the band\'s top, where a palm presses it; hold_1 / hold_2 / hold_3, the same 1.6 / 3.0 px along +x and 1.2 px along -x: where the leg action\'s other hand holds it while a tie goes on elsewhere. Written by tools/medical-actions-v1a/author_splint.cjs.',
    afl_rig: {plugin: core.VERSION},
    items: ['splint'],
    scale: SPLINT_SCALE,
    bounds: [[+(0.5 - 5.2 / 16).toFixed(4), +(OFFSET - 0.16 / 16).toFixed(4), +(0.5 - 1.88 / 16).toFixed(4)],
        [+(0.5 + 5.2 / 16).toFixed(4), +(OFFSET + 0.61 / 16).toFixed(4), +(0.5 + 1.88 / 16).toFixed(4)]],
    symmetry: 'x',
    mesh: {model: 'splint', atlas: 'splint_mesh', offset: OFFSET, parts: ['slat_0', 'slat_1', 'band']},
    points: {
        center: [0.5, +centreY.toFixed(4), 0.5],
        grip: [0.5, gripY, 0.5],
        hold_1: [along(1.6), gripY, 0.5],
        hold_2: [along(3.0), gripY, 0.5],
        hold_3: [along(-1.2), gripY, 0.5],
    },
};
const DUMMY = {scale: 1 / 16, points: {p: [0, 0, 0]}};
const defFor = id => (id === 'splint' ? splint : id === 'bandage' ? bandage : id === 'dummy' ? DUMMY : null);
const localOf = (def, n) => core.propLocal(def, n);

// ------------------------------------------------------------------ the body clip (FK)
const E = q => fileEuler(q);
function unwrap(seq) {
    for (let i = 1; i < seq.length; i++) for (let k = 0; k < 3; k++) {
        while (seq[i][1][k] - seq[i - 1][1][k] > 180) seq[i][1][k] -= 360;
        while (seq[i][1][k] - seq[i - 1][1][k] < -180) seq[i][1][k] += 360;
    }
    return seq;
}
const tkey = t => t.toFixed(4).replace(/0+$/, '').replace(/\.$/, '.0');
const keysOf = seq => { const o = {}; for (const [t, v] of unwrap(seq)) o[tkey(t)] = v.map(x => +x.toFixed(3)); return o; };
const held = (q, back) => keysOf([[0, [0, 0, 0]], [0.45, E(q)], [HOLD_T, E(q)], [LEN, back ? E(back) : [0, 0, 0]]]);
const ID = [0, 0, 0, 1];
const CLIP = ARM ? 'medical.splint_arm' : 'medical.splint_leg';

let bodyBones;
if (ARM) {
    // the left forearm held out in front of the chest, as the bandage's
    const fk = armFk([-0.15, 0.62, -0.77], [-0.45, 0.35, -0.82], null, 0);
    bodyBones = {
        upper_arm_l: {rotation: held(fk.upper, q4.slerp(fk.upper, ID, 0.6))},
        forearm_l: {rotation: held(fk.fore, q4.slerp(fk.fore, ID, 0.5))},
    };
} else {
    // sat on the ground: the hips 10 px down (the legs' backs on the ground), the pelvis tipped forward, the chest leaning
    // over the legs; the left leg straight out ahead, the right one out a little to its side; the right foot's toes up
    const DROP = OV.drop ?? 10, PELVIS = OV.pelvis ?? 20, CHEST = OV.chest ?? 25;
    const fwd = [0, 0, -1];
    const pelvisQ = q4.x(rad(PELVIS));
    // a thigh (child of the pelvis) pointing along d in the model: local = pelvis^-1 * between(down, d)
    const thighTo = d => q4.mul(q4.conj(pelvisQ), between([0, 1, 0], d));
    const thighL = thighTo(fwd), thighR = thighTo(v3.norm([-0.22, 0, -1]));
    const toesUp = thigh => {   // the foot (under the straight shin) turned so its toes point up
        const world = q4.mul(pelvisQ, thigh);
        const toes = q4.apply(world, [0, 0, -1]);
        return q4.mul(q4.conj(world), q4.mul(between(toes, [0, -1, 0.35]), world));
    };
    const lean = q4.x(rad(CHEST));
    // check the signs: the pelvis and chest lean forward (their up, -y, toward -z)
    const upP = q4.apply(pelvisQ, [0, -1, 0]), upC = q4.apply(q4.mul(pelvisQ, lean), [0, -1, 0]);
    if (!(upP[2] < 0 && upC[2] < upP[2])) throw new Error('lean sign ' + upP + ' ' + upC);
    const sat = (q, back) => keysOf([[0, E(q)], [LEN, E(back || q)]]);
    bodyBones = {
        pelvis: {rotation: sat(pelvisQ), position: {'0.0': [0, -DROP, 0], [tkey(LEN)]: [0, -DROP, 0]}},
        chest: {rotation: sat(lean)},
        thigh_l: {rotation: sat(thighL)}, shin_l: {rotation: sat(ID)}, foot_l: {rotation: sat(toesUp(thighL))},
        thigh_r: {rotation: sat(thighR)}, shin_r: {rotation: sat(ID)}, foot_r: {rotation: sat(toesUp(thighR))},
    };
}

function clipFile(bones) { return {animations: {[CLIP]: {animation_length: LEN, bones}}}; }
function actionOf(json, clipBones) {
    const clips = core.loadClips(rig, clipFile(clipBones));
    return core.actionFromJson(json, defFor, k => clips[k] || null);
}
const MASK = ARM ? 'upper' : 'full';
const BLEND = ARM ? 0.3 : 0.6;

// ------------------------------------------------------------------ the limb's frame for the props
// arm: the chest frame, the forearm measured on the posed arm; leg: the left shin's own rest frame (bone:shin_l)
let FRAME, DIST, F, UP, IN;
if (ARM) {
    const probe = core.solveFrame(poser, {action: actionOf({clip: CLIP, mask: MASK, blend: {in: 0, out: 0}, hands: {}}, bodyBones), seconds: 1});
    const chestInv = tf.inv(probe.pose.world[rig.chest]);
    const P = (b, p) => tf.apply(chestInv, probe.pose.point(b, p));
    const elbow = P(rig.forearmL, [6, 6, 0]), cap = P(rig.forearmL, [6, 12, 0]);
    F = v3.norm(v3.sub(cap, elbow));
    DIST = cap;
    UP = v3.norm(v3.sub([0, -1, 0], v3.mul(F, -F[1])));
    IN = v3.norm(v3.cross(F, UP));
    if (IN[0] > 0) IN = v3.mul(IN, -1);
    FRAME = 'chest';
} else {
    // rest: the shin along +y to the sole (y 24) on x 1.9; sat, its rest front (-z) faces up and its rest -x the right leg
    F = [0, 1, 0];
    DIST = [1.9, 24, 0];
    UP = [0, 0, -1];
    IN = [-1, 0, 0];
    FRAME = 'bone:shin_l';
}
const dirAt = th => v3.add(v3.mul(UP, Math.cos(rad(th))), v3.mul(IN, Math.sin(rad(th))));
const axisAt = back => v3.sub(DIST, v3.mul(F, back));
/** A prop turned for orbit angle th: its x along the limb (toward the distal end, or the near end), its -y out along dirAt(th). */
function turnAt(th, proximal) {
    const cx = proximal ? v3.mul(F, -1) : F, cy = v3.mul(dirAt(th), -1), cz = v3.cross(cx, cy);
    return q4.norm(q4.fromBasis(cx, cy, cz));
}
/** A turn as the key's rot (deg, PropKey: q4.xyz), right at the gimbal lock too (y = ±90: x 0, z found). */
function rotOf(q) {
    const err = e => { const r = q4.xyz(rad(e[0]), rad(e[1]), rad(e[2])); return 1 - Math.abs(q4.dot(r, q)); };
    let e = q4.eulerXYZ(q).map(deg);
    if (err(e) > 1e-7) {
        let best = null;
        for (const y of [90, -90]) for (let z = -180; z < 180; z += 0.5) { const c = [0, y, z], v = err(c); if (!best || v < best.v) best = {c, v}; }
        for (let step = 0.25; step > 1e-4; step /= 2) for (const d of [-step, step]) { const c = [0, best.c[1], best.c[2] + d], v = err(c); if (v < best.v) best = {c, v}; }
        e = best.c;
    }
    return e.map(x => +x.toFixed(3));
}
const r4 = x => +x.toFixed(4);
const REST_PALM = [-6, 10.5, 0];
const R0 = q4.fromBasis([1, 0, 0], [0, 0, -1], [0, 1, 0]);

// ------------------------------------------------------------------ prop timelines (at = travel start, blend = travel)
function timeline() {
    const keys = [];
    let last = 0;
    return {
        keys,
        key(arrive, k) {
            keys.push({at: r4(keys.length ? last : 0), frame: k.frame || FRAME, point: k.point, pos: k.pos.map(r4), rot: k.rot, blend: r4(keys.length ? arrive - last : 0), hidden: k.hidden});
            last = arrive;
        },
        hold(t, hidden) {
            const k = keys[keys.length - 1];
            keys.push(Object.assign({}, k, {at: r4(last), blend: r4(t - last), hidden: hidden === undefined ? k.hidden : hidden}));
            last = t;
        },
        done() { for (const k of keys) if (!k.hidden) delete k.hidden; return keys; },
    };
}

// the splint: from the right palm onto the limb's outer side, hidden at SLATS_AT (the renderer's slats take its place)
const sp = timeline();
sp.key(0, {frame: 'chest', point: 'grip', pos: REST_PALM, rot: rotOf(R0)});
const splintPos = v3.add(axisAt(SLAT_MID), v3.mul(dirAt(LAY), R_SPLINT));
const splintRot = rotOf(turnAt(LAY, !ARM));
sp.key(0.45, {frame: 'chest', point: 'grip', pos: REST_PALM, rot: rotOf(R0)});
sp.key(1.0, {point: 'center', pos: v3.add(splintPos, v3.mul(dirAt(LAY), 1.2)), rot: splintRot});
sp.key(1.15, {point: 'center', pos: splintPos, rot: splintRot});
sp.hold(SLATS_AT);
sp.hold(LEN, true);

// the roll: hidden in the palm until ROLL_AT, then the ties; its axis point on the limb's middle line (swings round it)
const ro = timeline();
const axisKey = (t, back, th, r, hidden) => ro.key(t, {point: 'axis', pos: v3.add(axisAt(back), v3.mul(dirAt(th), r - AXIS_OFF)), rot: rotOf(turnAt(th)), hidden});
// under the leg on the ground: pushed under, out of sight, from the outer side round to the inner side where the hand takes it
const under = k => !ARM && k >= 2 && k <= CATCH_K;
ro.key(0, {frame: 'chest', point: 'pinch', pos: REST_PALM, rot: rotOf(R0), hidden: true});
ro.hold(ROLL_AT, true);
ro.hold(ROLL_AT + 0.05, false);
ro.key(ROLL_AT + 0.25, {frame: 'chest', point: 'pinch', pos: [-5, 8, -7], rot: rotOf(R0)});   // up in front of the chest, not through it
const tieSteps = [];
TIE_FROM.forEach((T0, i) => {
    const back = TIES[i];
    axisKey(T0 - 0.15, back, START, R_ROLL);                 // up to the tie's place, at the loops' start
    axisKey(T0 - 0.05, back, START, R_ROLL - 0.7);           // the loose end pressed on
    const steps = [];
    for (let k = 0; k <= 6; k++) {
        const th = START - TRAIL - 60 * k, t = T0 + TURN * k / 6;
        axisKey(Math.max(t, T0 + 0.02), back, th, R_ROLL, under(k));
        steps.push({t, th});
    }
    tieSteps.push(steps);
    const end = T0 + TURN;
    axisKey(end + 0.15, back, KNOT, R_ROLL - 0.7);            // the knot: pressed on
    axisKey(end + 0.3, back, KNOT - 15, R_ROLL + 0.9);        // pulled tight
    axisKey(end + 0.45, back, KNOT, R_ROLL);
});
// put away: hidden where it is; then the pinch point marks where the palm presses the splint twice
const PRESS = OV.press ?? (ARM ? START : 20);
const pressRot = rotOf(turnAt(PRESS));
const pressAt = r => v3.add(axisAt(SLAT_MID), v3.mul(dirAt(PRESS), r));
ro.key(PUT_AWAY, {point: 'pinch', pos: pressAt(5.3), rot: pressRot, hidden: true});
ro.key(PUT_AWAY + 0.15, {point: 'pinch', pos: pressAt(4.7), rot: pressRot, hidden: true});
ro.key(PUT_AWAY + 0.3, {point: 'pinch', pos: pressAt(5.3), rot: pressRot, hidden: true});
ro.key(PUT_AWAY + 0.45, {point: 'pinch', pos: pressAt(4.7), rot: pressRot, hidden: true});
ro.hold(LEN, true);

// ------------------------------------------------------------------ the hands
const right = [{at: 0, target: 'prop:splint:grip', blend: 0, roll: 0, pole: POLE}];
right.push({at: SLATS_AT, target: 'pose', blend: 0.25, roll: 0, pole: POLE});
right.push({at: ROLL_AT, target: 'prop:bandage:pinch', blend: 0.05, roll: 0, pole: POLE});
for (const steps of tieSteps) {
    right.push({at: r4(steps[0].t - 0.04), target: 'pose', blend: 0.12, roll: 0, pole: POLE});               // lets go
    right.push({at: r4(steps[CATCH_K].t - 0.12), target: 'prop:bandage:pinch', blend: 0.12, roll: 0, pole: POLE});   // takes it again
    if (!ARM) {
        // the leg: the turn's last sixth over the far top of the shin (out of reach at the ankle) on its own, taken for the knot
        right.push({at: r4(steps[5].t + 0.02), target: 'pose', blend: 0.1, roll: 0, pole: POLE});
        right.push({at: r4(steps[6].t + 0.05), target: 'prop:bandage:pinch', blend: 0.1, roll: 0, pole: POLE});
    }
}
right.push({at: r4(HOLD_T), target: 'pose', blend: 0.3, roll: 0, pole: POLE});
let left = [];
if (!ARM) {
    // the left hand: on the knee until the splint comes, then holds it against the shin away from the tie going on
    left = [
        {at: 0.85, target: 'prop:splint:hold_2', blend: 0.25, roll: 0, pole: POLE_L},
        {at: r4(TIE_FROM[0] - 0.4), target: 'prop:splint:hold_1', blend: 0.3, roll: 0, pole: POLE_L},
        {at: r4(TIE_FROM[1] - 0.45), target: 'prop:splint:hold_2', blend: 0.3, roll: 0, pole: POLE_L},
        {at: r4(TIE_FROM[2] - 0.45), target: 'prop:splint:hold_3', blend: 0.3, roll: 0, pole: POLE_L},
        {at: r4(HOLD_T), target: 'pose', blend: 0.3, roll: 0, pole: POLE_L},
    ];
}

// ------------------------------------------------------------------ the free arm's clip keys (IK solved once where the hand is off the props)
function ikLocals(t, palm, side, base) {
    const json = {clip: CLIP, mask: MASK, blend: {in: 0, out: 0},
        props: [{id: 'dummy', keys: [{at: 0, frame: FRAME, point: 'p', pos: palm, rot: [0, 0, 0], blend: 0}]}],
        hands: {[side]: [{at: 0, target: 'prop:dummy:p', blend: 0, roll: 0, pole: side === 'right' ? POLE : POLE_L}]}};
    const res = core.solveFrame(poser, {action: actionOf(json, base), seconds: t});
    return side === 'right' ? [res.pose.local[rig.upperArmR], res.pose.local[rig.forearmR], res.pose.local[rig.handR]]
        : [res.pose.local[rig.upperArmL], res.pose.local[rig.forearmL], res.pose.local[rig.handL]];
}
const pinchAt = (back, th) => v3.add(axisAt(back), v3.mul(dirAt(th), R_ROLL + R_PINCH));
const seqR = [[], [], []];
const addR = (t, locals) => locals.forEach((q, i) => seqR[i].push([t, E(q)]));
addR(0, [ID, ID, ID]);
addR(SLATS_AT, ikLocals(SLATS_AT, v3.add(splintPos, v3.mul(dirAt(LAY), 2.0)), 'right', bodyBones));
addR(ROLL_AT, [ID, ID, ID]);
TIE_FROM.forEach((T0, i) => {
    const s = tieSteps[i];
    addR(s[0].t + 0.05, ikLocals(s[0].t, pinchAt(TIES[i], s[0].th), 'right', bodyBones));
    const mid = (s[0].t + s[CATCH_K].t) / 2;
    addR(mid, ikLocals(mid, v3.add(axisAt(TIES[i]), v3.mul(dirAt(START + 50), R_ROLL + R_PINCH + 1.0)), 'right', bodyBones));
    addR(s[CATCH_K].t - 0.12, ikLocals(s[CATCH_K].t, pinchAt(TIES[i], s[CATCH_K].th), 'right', bodyBones));
    if (!ARM) {
        addR(s[5].t + 0.05, ikLocals(s[5].t, pinchAt(TIES[i], s[5].th), 'right', bodyBones));
        addR(s[6].t + 0.1, ikLocals(s[6].t + 0.15, v3.add(axisAt(TIES[i]), v3.mul(dirAt(KNOT), R_ROLL - 0.7 + R_PINCH)), 'right', bodyBones));
    }
});
addR(HOLD_T, ikLocals(HOLD_T, pressAt(5.0), 'right', bodyBones));
addR(LEN, ARM ? [q4.x(rad(-20)), q4.x(rad(-15)), ID] : [ID, ID, ID]);
const bones = Object.assign({}, bodyBones, {
    upper_arm_r: {rotation: keysOf(seqR[0])}, forearm_r: {rotation: keysOf(seqR[1])}, hand_r: {rotation: keysOf(seqR[2])},
});
if (!ARM) {
    // the left arm before the splint comes and after: the hand on the left knee's top
    const knee = v3.add(axisAt(12 - 6), v3.mul(dirAt(20), 2.2 + 1.6));
    const onKnee = ikLocals(0.5, knee, 'left', bodyBones);
    const seqL = [[], [], []];
    const addL = (t, locals) => locals.forEach((q, i) => seqL[i].push([t, E(q)]));
    addL(0, onKnee); addL(LEN, onKnee);
    Object.assign(bones, {upper_arm_l: {rotation: keysOf(seqL[0])}, forearm_l: {rotation: keysOf(seqL[1])}, hand_l: {rotation: keysOf(seqL[2])}});
}

// ------------------------------------------------------------------ the action
const about = ARM
    ? 'Fractures and splints V1: splinting a broken forearm (item use 8 s, SplintItem.USE_TICKS), first and third person alike; keyed on the left arm, mirrored for the right (client/skeleton/SkeletonActions). The left forearm comes out in front of the chest (FK clip keys, as the bandage); the right hand lays the splint (player_props/splint) along its outer top (0.45-1.15 s); at 1.3 s the prop is hidden and client/BandageWrapRenderer draws the two slats on the forearm\'s outer and inner side; the hand fetches the gauze roll (player_props/bandage, its axis point on the forearm\'s middle line) and ties three ties, at the wrist, the middle and just over the elbow (one turn of 0.83 s from 2.0, 3.75 and 5.5 s, the renderer\'s tie growing behind the roll: the hand lets go over the top, the roll goes round under on its own, the hand takes it under the forearm; a tug for the knot after each); the roll is put away at 6.7 s, the palm presses the splint twice; both arms come down from 7.4 s. Written by tools/medical-actions-v1a/author_splint.cjs; docs/gameplay/fracture_splint_v1_plan.md'
    : 'Fractures and splints V1: splinting a broken lower leg (item use 8 s, SplintItem.USE_TICKS), first and third person alike; keyed on the left leg, mirrored for the right (client/skeleton/SkeletonActions). The player sits down on the ground over the action\'s 0.6 s blend (hips 10 px down, the pelvis tipped and the chest leaning forward, both legs out ahead, the right a little aside; the own first-person camera on the posed eye, first_person_eye); the right hand lays the splint (player_props/splint) along the shin\'s outer side (0.45-1.15 s), the left hand takes it and holds it there, moving away from each tie; at 1.3 s the prop is hidden and client/BandageWrapRenderer draws the slats; the right hand fetches the gauze roll (player_props/bandage, its axis point on the shin\'s middle line, bone:shin_l) and ties three ties, at the ankle, the middle and just over the knee (one turn of 0.83 s from 2.0, 3.75 and 5.5 s; the roll goes over the shin and is pushed under the leg out of sight, the hand takes it on the inner side); the roll is put away at 6.7 s, the palm presses the splint twice; the hands come back from 7.4 s and the body gets up over the blend-out. Written by tools/medical-actions-v1a/author_splint.cjs; docs/gameplay/fracture_splint_v1_plan.md';
const action = {
    format_version: 1,
    about,
    afl_rig: {plugin: core.VERSION, animation: CLIP},
    clip: CLIP,
    layer: 'action', mask: MASK, priority: 50, first_person: 'body', blend: {in: BLEND, out: BLEND},
    first_person_pitch: ARM ? 65 : (OV.fp ?? 80),
};
if (!ARM) action.first_person_eye = true;
Object.assign(action, {
    props: [{id: 'splint', keys: sp.done()}, {id: 'bandage', keys: ro.done()}],
    hands: {right, left},
    events: [],
    check: {use_ticks: 160},
});

// ------------------------------------------------------------------ check
const act = actionOf(action, bones);
const segDist = (p, a, b) => { const ab = v3.sub(b, a), t = Math.max(0, Math.min(1, v3.dot(v3.sub(p, a), ab) / v3.dot(ab, ab))); return v3.dist(p, v3.add(a, v3.mul(ab, t))); };
const worst = {roll: 99, rollAt: 0, ground: 99, groundAt: 0, offR: 0, offRAt: 0, offL: 0, offLAt: 0, wrist: 0, twist: 0, palmLimb: 99, palmLimbAt: 0};
const rows = [];
for (let t = 0; t <= LEN + 1e-6; t += 0.025) {
    const res = core.solveFrame(poser, {action: act, seconds: t, time: t, pitch: 0});
    const pose = res.pose;
    const limbA = ARM ? pose.point(rig.forearmL, [6, 6, 0]) : pose.point(rig.thighL, [1.9, 12, 0]);
    const limbB = ARM ? pose.point(rig.handL, [6, 12, 0]) : pose.point(rig.footL, [1.9, 24, 0]);
    const placed = res.props[1], hidden = res.hidden[1];
    const centre = placed ? tf.apply(placed, localOf(bandage, 'center')) : null;
    if (centre && !hidden && t > TIE_FROM[0] - 0.2 && t < PUT_AWAY) {
        const d = segDist(centre, limbA, limbB) - 1.0;
        if (d < worst.roll) { worst.roll = d; worst.rollAt = t; }
    }
    if (!ARM && centre && !hidden) {
        const g = 24 - centre[1] - 1.0;   // the roll's bottom over the ground (Java y down, ground 24)
        if (g < worst.ground) { worst.ground = g; worst.groundAt = t; }
    }
    const palmR = pose.point(rig.handR, [-6, 10.5, 0]);
    if (t > SLATS_AT && t < HOLD_T) {
        const d = segDist(palmR, limbA, limbB);
        if (d < worst.palmLimb) { worst.palmLimb = d; worst.palmLimbAt = t; }
    }
    const ro2 = core.readouts(res);
    for (const side of ['right', 'left']) {
        const r = ro2[side];
        if (r.mode === 'ik' && r.w > 0.99) {
            const k = side === 'right' ? 'offR' : 'offL';
            if (r.off > worst[k]) { worst[k] = r.off; worst[k + 'At'] = t; }
        }
        if (r.mode === 'ik' && r.w > 0.5) { worst.wrist = Math.max(worst.wrist, r.wrist); worst.twist = Math.max(worst.twist, r.twist); }
    }
    if (Math.abs(t * 4 - Math.round(t * 4)) < 1e-6) {
        const r = ro2.right, l = ro2.left;
        rows.push(`${t.toFixed(2)} roll ${centre ? centre.map(x => x.toFixed(1)).join(',') : '-'}${hidden ? ' (hidden)' : ''} R ${r.mode === 'ik' ? 'w ' + r.w.toFixed(2) + ' off ' + r.off.toFixed(2) + ' wr ' + r.wrist.toFixed(0) + ' tw ' + r.twist.toFixed(0) : 'clip'} L ${l.mode === 'ik' ? 'w ' + l.w.toFixed(2) + ' off ' + l.off.toFixed(2) : 'clip'}`);
    }
}
console.log(LIMB, 'frame', FRAME, 'F', F.map(x => +x.toFixed(3)), 'UP', UP.map(x => +x.toFixed(3)), 'IN', IN.map(x => +x.toFixed(3)));
console.log(rows.join('\n'));
console.log(JSON.stringify(worst, (k, v) => (typeof v === 'number' ? +v.toFixed(2) : v)));

// first person: screen angles (deg, x right, y up) and distance from the camera, looking down FP; the leg's camera on
// the posed eye (ActionPose.cameraEye: the neck plus the eye's rest offset turned with the root only)
const FP = +(process.env.FP || action.first_person_pitch);
const eyeRest = rig.sockets.eye.pos;
function screen(Pm, cam) {
    const p = rad(FP), rel = v3.sub(Pm, cam);
    const fwd = [0, Math.sin(p), -Math.cos(p)], up = [0, -Math.cos(p), -Math.sin(p)], rightV = [-1, 0, 0];
    const f = v3.dot(rel, fwd);
    return `${(Math.atan2(v3.dot(rel, rightV), f) * 180 / Math.PI).toFixed(0)}/${(Math.atan2(v3.dot(rel, up), f) * 180 / Math.PI).toFixed(0)}@${v3.len(rel).toFixed(1)}`;
}
const fpRows = [];
for (const t of [0.3, 0.8, 1.2, 1.6, 2.1, 2.4, 3.9, 5.6, 6.0, 7.0]) {
    const res = core.solveFrame(poser, {action: act, seconds: t, time: t, pitch: FP});
    const pose = res.pose;
    const neck = pose.point(rig.head, rig.pivot[rig.head]);
    const cam = ARM ? [0, core.eyeModelY(false), 0] : v3.add(neck, q4.apply(pose.rotation(rig.root), v3.sub(eyeRest, rig.pivot[rig.head])));
    const sp0 = res.props[0], ro0 = res.props[1];
    const mid = ARM ? pose.point(rig.forearmL, [6, 12 - SLAT_MID, 0]) : pose.point(rig.shinL, [1.9, 24 - SLAT_MID, 0]);
    const dist = ARM ? pose.point(rig.handL, [6, 12, 0]) : pose.point(rig.footL, [1.9, 24, 0]);
    fpRows.push(`${t} cam ${cam.map(x => x.toFixed(1)).join(',')} splint ${sp0 ? screen(tf.apply(sp0, localOf(splint, 'center')), cam) : '-'}${res.hidden[0] ? 'h' : ''} roll ${ro0 ? screen(tf.apply(ro0, localOf(bandage, 'center')), cam) : '-'}${res.hidden[1] ? 'h' : ''} palmR ${screen(pose.point(rig.handR, [-6, 10.5, 0]), cam)} palmL ${screen(pose.point(rig.handL, [6, 10.5, 0]), cam)} mid ${screen(mid, cam)} distal ${screen(dist, cam)}`);
}
console.log('FP at ' + FP);
console.log(fpRows.join('\n'));

if (WRITE) {
    const pretty = o => JSON.stringify(o, null, 2) + '\n';
    const clipPath = path.join(A, 'animations/player/medical.animation.json');
    const clipFileJson = L.read(clipPath);
    clipFileJson.animations[CLIP] = {animation_length: LEN, bones};
    fs.writeFileSync(clipPath, pretty(clipFileJson));
    fs.writeFileSync(path.join(A, 'player_actions/item/splint_' + LIMB + '.json'), pretty(action));
    fs.writeFileSync(splintPath, pretty(splint));
    console.log('written');
}
if (process.env.DBG) {
    const [a, b] = process.env.DBG.split(',').map(Number);
    for (const k of action.props[1].keys) if (k.at >= a - 0.5 && k.at <= b) console.log(JSON.stringify(k));
    for (let t = a; t <= b + 1e-6; t += 0.05) {
        const res = core.solveFrame(poser, {action: act, seconds: t, time: t, pitch: 0});
        const pl = res.props[1];
        console.log(t.toFixed(2), 'axis', tf.apply(pl, localOf(bandage, 'axis')).map(x => x.toFixed(2)).join(','), 'centre', tf.apply(pl, localOf(bandage, 'center')).map(x => x.toFixed(2)).join(','), 'shin', res.pose.point(rig.shinL, [1.9, 20, 0]).map(x => x.toFixed(2)).join(','));
    }
}
