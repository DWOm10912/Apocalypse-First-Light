// Authoring sheet for the medical alcohol on the head, chest, belly and a shin (docs/gameplay/medical_actions_by_part_v1.md):
// item/alcohol_clean's cap work as it is (author_alcohol.cjs: the same times, so MedicalItem.ALCOHOL_CUES and the pour window
// of client/MedicalPourJets, 2.1..2.9 s, hold for every part), the bottle taken to the part instead of over the held-out
// forearm, the cap held aside in the left fingertips meanwhile, and a body clip: the head tipped to the left, its right side
// up, poured on above the ear; the chest or belly leant back a little, poured on its front; a shin sat on the ground (the
// left knee up, as bandage_leg; MedicalItem sits the player first), poured on down the shin bone. Keyed right-handed and on
// the left leg: client/skeleton/SkeletonActions mirrors them. The bottle and the cap are keyed props, both hands solved by
// IK at run time (as alcohol_clean); the body is the clip medical.alcohol_<part> (hold_body; the head held too, hold_head).
//   node author_alcohol_parts_v1.cjs [--write]     (ONLY=head|chest|belly|leg to print one; ROWS=1 the rows)
//   animations/player/medical.animation.json   medical.alcohol_{head,chest,belly,leg}
//   player_actions/item/alcohol_{head,chest,belly,leg}.json
// Java model px: y down, the front -z, the body's right -x; prop keys in the chest's frame.
const A = require('./alc_lib.cjs');
const P = require('./pose_lib.cjs');
const {fs, path, core, v3, q4, tf, rad, rig, poser, bottle, cap, loc, rotOf, r4, bottleTurn, fileEuler} = A;
const WRITE = process.argv.includes('--write');
const ONLY = process.env.ONLY || null;
const LEN = 4.6;   // MedicalItem.ALCOHOL_TICKS 92

// ------------------------------------------------------------------ as author_alcohol.cjs (the cap's work, its times)
const U0 = {G: [-1.5, 8.5, -6], axis: [0.45, -0.85, 0.15], g: [-1, 0, 0.4]};
let U = U0;
const POLE_L = [1, -0.6, 0.25], TWIST = 22, POLE_OUT = [-0.5, 1, 0.3], ROLL_UP = -30;
const REST_G = [-6, 10.5, 0];
const T = {up: 0.45, reach: 0.2, turns: 0.5, thread: 1.4, off: 1.48, lifted: 1.6, clear: 1.75, held: 2.0,
    bMove: 1.6, over: 1.92, tipped: 2.15, poured: 2.85, back: 3.1, capOut: 2.9, capBack: 3.2, upright: 3.45, onThread: 3.55, release: 4.36};
const GRIP = 0.3, TURN_T = 0.2, GRIP2 = 0.27, TURN_T2 = 0.18, STEP = 50, RISE = 0.12, LIFT = 1.3, CLEAR = 1.6;
const POLE_R = [-0.8, 0.6, 0.3];

// ------------------------------------------------------------------ the parts
const Z = a => q4.z(rad(a)), X = a => q4.x(rad(a)), mul = q4.mul, ID = [0, 0, 0, 1];
// work: the body while opening and closing; pour: while pouring (chest / head local turns; a shin: pose_lib keys).
// wound: where it pours (the bone's rest px), normal out of it, slide the way the mouth moves along it over the pour;
// out / up: the mouth that far off the skin and above it; tip: how far the bottle's axis points down (world, 0..1);
// lift: a way point (chest px, added to the middle) on the way from the rest and back to it, over the knee;
// over: the half-tipped bottle on the way, off the mouth's place (out along the normal, up, then shift chest px: the shin's
// knee is up by the chest, the hand kept off it); via: a way point (chest px,
// added to the middle of the way up and back) round the face; aside: the left palm holding the cap (chest frame) meanwhile.
const PARTS = {
    head: {mask: 'upper', fp: 30, work: {chest: ID, head: X(25)}, pour: {chest: Z(10), head: mul(Z(30), X(15))},
        bone: 'head', wound: [-4, -5.6, -1.2], normal: [-1, 0, 0], slide: [0, 0, 2.2], out: 1.0, up: 1.2, tip: 0.5,
        over: {out: 2.5, up: 0.3}, via: [-3.5, 0, -1.5], aside: [5.5, 7.0, -4.5]},
    chest: {mask: 'upper', fp: 65, work: {chest: ID, head: X(30)}, pour: {chest: X(-8), head: X(8)},
        bone: 'chest', wound: [-2.4, 3.6, -2], normal: [0, 0, -1], slide: [2.4, 0.6, 0], out: 0.7, up: 1.0, tip: 0.55,
        over: {out: 2.5, up: 1.5}, aside: [5.5, 7.0, -4.5]},
    belly: {mask: 'upper', fp: 70, work: {chest: ID, head: X(30)}, pour: {chest: X(-8), head: X(45)},
        bone: 'pelvis', wound: [-2.4, 8.6, -2], normal: [0, 0, -1], slide: [2.4, 0.6, 0], out: 0.7, up: 1.0, tip: 0.55,
        over: {out: 1.5, up: 3.0}, aside: [5.5, 6.0, -5.0]},
    leg: {mask: 'full', fp: 90, seated: true, U: {G: [-3.0, 3.0, -7.0], axis: [0.45, -0.85, 0.15], g: [-1, 0, 0.4]},
        bone: 'shin_l', wound: [1.9, 19.2, -2], normal: [0, 0, -1], slide: [0, 1.8, 0], out: 1.2, up: 1.0, tip: 0.55,
        over: {out: 2.5, up: 1.5, shift: [0, 0, -2.5]}, lift: [-2, -4, 0], aside: [8.0, 7.0, -5.0]},
};
// sat for the shin: bandage_leg's seat (pose_lib SHIN_WORK), leant less (the bottle only goes over the shin, not round it)
const SHIN_POUR = Object.assign({}, P.SHIN_WORK, {tip: 16, lean: 4, view: 40});
SHIN_POUR.legL = Object.assign({}, SHIN_POUR.legL, {ik: [3.0, 22, -8.0]});   // the knee lower: the shin bone faces up, off the face
// the body over the use: [t, pose] with smoothstep between (rest: none for the upper body, the seat for a shin)
const BODY_KEYS = [[0, 'rest'], [0.4, 'work'], [1.6, 'work'], [2.02, 'pour'], [2.95, 'pour'], [3.4, 'work'], [4.36, 'work'], [LEN, 'rest']];
const smooth = x => { x = Math.max(0, Math.min(1, x)); return x * x * (3 - 2 * x); };
const slerp = (a, b, w) => q4.slerp(a, q4.dot(a, b) < 0 ? q4.neg(b) : b, w);
/** The body's local turns (and the pelvis' offset, sat) at a named pose. */
function poseOf(S, name) {
    if (S.seated) {
        const b = P.bodyOf(name === 'rest' ? P.REST : SHIN_POUR), q = {};
        for (const n of P.BODY) q[n] = b[n];
        return {q, pos: b.pos};
    }
    const p = name === 'rest' ? {chest: ID, head: ID} : S[name];
    return {q: {chest: p.chest, head: p.head}, pos: null};
}
function bodyAt(S, t) {
    let k = 0;
    while (k < BODY_KEYS.length - 2 && BODY_KEYS[k + 1][0] <= t) k++;
    const [ta, na] = BODY_KEYS[k], [tb, nb] = BODY_KEYS[k + 1];
    const w = smooth((t - ta) / (tb - ta)), a = poseOf(S, na), b = poseOf(S, nb), e = {};
    for (const n of Object.keys(a.q)) e[n] = fileEuler(slerp(a.q[n], b.q[n], w));
    return {eulers: e, pos: a.pos ? v3.lerp(a.pos, b.pos, w) : null};
}
function bodyClip(S) {
    const keys = [];
    for (let i = 0; i <= Math.round(LEN / 0.05); i++) keys.push(Object.assign({t: r4(i * 0.05)}, bodyAt(S, r4(i * 0.05))));
    return P.clipOf(keys, LEN, k => ({eulers: k.eulers, pos: k.pos, miss: ''})).clip;
}

// ------------------------------------------------------------------ solving
const bLocal = n => loc(bottle, n), cLocal = n => loc(cap, n);
const bone = n => (n === 'head' ? rig.head : n === 'chest' ? rig.chest : rig.find(n));
function actOf(S, name, json, clip) {
    const clips = core.loadClips(rig, {animations: {['medical.alcohol_' + name]: clip}});
    return core.actionFromJson(Object.assign({clip: 'medical.alcohol_' + name, mask: S.mask, hold_body: true, hold_head: S.mask !== 'full', blend: {in: 0.3, out: 0.3}}, json),
        A.defFor, k => clips[k] || null);
}
/** A turn its key's XYZ euler gives back (none near the y = +-90 deg lock, where the file's x and z run together). */
const keyable = Q => { const e = rotOf(Q).map(rad); return Math.abs(q4.dot(q4.xyz(e[0], e[1], e[2]), Q)) > 0.9999; };
const frame = (act, s, pitch) => core.solveFrame(poser, {action: act, seconds: s, time: s, pitch: pitch || 0, headClip: 1});

function author(name) {
    const S = PARTS[name], clip = bodyClip(S);
    U = S.U || U0;
    const bottleKeys = [], capKeys = [], handR = [], handL = [];
    const push = (keys, t, k) => {
        const prev = keys.length ? keys[keys.length - 1] : null, start = prev ? prev._t : 0;
        const key = {at: r4(prev ? start : 0), frame: k.frame || 'chest', point: k.point, pos: k.pos.map(r4), rot: k.rot.map(x => +x.toFixed(3)), blend: r4(prev ? t - start : 0)};
        if (k.hidden) key.hidden = true;
        Object.defineProperty(key, '_t', {value: t, enumerable: false});
        keys.push(key);
    };
    const hold = (keys, t) => { const p = keys[keys.length - 1]; push(keys, t, {frame: p.frame, point: p.point, pos: p.pos, rot: p.rot, hidden: p.hidden}); };
    const bottleAt = (G, axis, g) => ({point: 'grip', pos: G, rot: rotOf(bottleTurn(axis, g))});
    const json = () => ({props: [{id: 'medical_alcohol', keys: bottleKeys}, {id: 'medical_alcohol_cap', keys: capKeys}], hands: {right: handR, left: handL}});

    // the pour: the wound's place in the chest frame at the pour pose (the body alone, no props)
    const still = actOf(S, name, {props: [], hands: {right: [], left: []}}, clip);
    const at = frame(still, 2.5), inv = tf.inv(at.pose.world[rig.chest]);
    const toChest = p => tf.apply(inv, at.pose.point(bone(S.bone), p));
    const down = q4.apply(q4.conj(at.pose.rotation(rig.chest)), [0, 1, 0]);
    const nrm = v3.norm(v3.sub(toChest(v3.add(S.wound, S.normal)), toChest(S.wound)));
    const mouthAt = w => v3.add(v3.add(toChest(w), v3.mul(nrm, S.out)), v3.mul(down, -S.up));
    const h = v3.norm(v3.sub(v3.mul(nrm, -1), v3.mul(down, v3.dot(v3.mul(nrm, -1), down))));
    const pourAxis = v3.norm(v3.add(v3.mul(h, Math.sqrt(1 - S.tip * S.tip)), v3.mul(down, S.tip)));
    const M0 = mouthAt(S.wound), M1 = mouthAt(v3.add(S.wound, S.slide));
    // the grip side and the hand's roll that reach both ends of the pour best (the right palm on the bottle's side)
    let pick = null;
    for (const g of [[-1, 0, 0], [-1, 1, 0], [-1, 0, 1], [0, 1, 0], [-1, -1, 0], [0, 0, 1], [-1, 0, -1], [1, 1, 0]]) {
        const Q = bottleTurn(pourAxis, g);
        if (!keyable(Q)) continue;
        const Gs = [M0, M1].map(m => v3.add(m, q4.apply(Q, v3.sub(bLocal('grip'), bLocal('mouth')))));
        for (let roll = -90; roll <= 90; roll += 15) {
            let sc = 0;
            for (const G of Gs) {
                const a = actOf(S, name, {props: [{id: 'medical_alcohol', keys: [{at: 0, frame: 'chest', point: 'grip', pos: G, rot: rotOf(Q), blend: 0}]}],
                    hands: {right: [{at: 0, target: 'prop:grip', blend: 0, roll, pole: POLE_R}], left: []}}, clip);
                const ro = core.readouts(frame(a, 2.5)).right;
                sc += ro.off + 0.01 * Math.max(0, ro.twist - 40) + 0.01 * Math.max(0, ro.wrist - 50);
            }
            if (!pick || sc < pick.sc) pick = {sc, g, Q, roll, Gs};
        }
    }
    const pourKey = G => ({point: 'grip', pos: G, rot: rotOf(pick.Q)});
    // half tipped, the mouth off its pouring place, on the way up and back
    const overAxis = v3.norm(v3.lerp(v3.norm(U.axis), pourAxis, 0.45));
    const side = v3.norm(v3.sub(pick.g, v3.mul(pourAxis, v3.dot(pick.g, pourAxis))));   // the pour's grip side, kept on the way
    const overQ = bottleTurn(overAxis, side);
    const overG = v3.add(v3.add(v3.add(v3.add(M0, v3.mul(nrm, S.over.out)), v3.mul(down, -S.over.up)), q4.apply(overQ, v3.sub(bLocal('grip'), bLocal('mouth')))), S.over.shift || [0, 0, 0]);
    const viaAxis = v3.norm(v3.lerp(v3.norm(U.axis), overAxis, 0.5)), viaQ = bottleTurn(viaAxis, v3.lerp(v3.norm(U.g), side, 0.5));
    const viaKey = () => ({point: 'grip', pos: v3.add(v3.lerp(U.G, overG, 0.5), S.via), rot: rotOf(viaQ)});
    // the right palm at rest (the bottle's grip as the action starts and ends): the arm down, or sat on the right knee
    let restG = REST_G;
    if (S.seated) {
        const r0 = frame(still, LEN);   // the seat at full weight (at 0 the action is still blending in over the standing body)
        restG = tf.apply(tf.inv(r0.pose.world[rig.chest]), tf.apply(r0.pose.world[rig.root], v3.add(P.bodyOf(P.REST).knee.R, [0.2, 1.4, -2.0])));
    }

    // ---------------------------------------------------------------- the bottle
    push(bottleKeys, 0, bottleAt(restG, [0, -1, 0], [-1, 0, 0.3]));
    if (S.lift) push(bottleKeys, T.up / 2, bottleAt(v3.add(v3.lerp(restG, U.G, 0.5), S.lift), v3.lerp([0, -1, 0], U.axis, 0.5), [-1, 0, 0.35]));
    push(bottleKeys, T.up, bottleAt(U.G, U.axis, U.g));
    hold(bottleKeys, T.bMove);
    if (S.via) push(bottleKeys, (T.bMove + T.over) / 2, viaKey());
    push(bottleKeys, T.over, {point: 'grip', pos: overG, rot: rotOf(overQ)});
    push(bottleKeys, T.tipped, pourKey(pick.Gs[0]));
    push(bottleKeys, T.poured, pourKey(pick.Gs[1]));
    push(bottleKeys, T.back, {point: 'grip', pos: overG, rot: rotOf(overQ)});
    hold(bottleKeys, T.capBack);
    if (S.via) push(bottleKeys, (T.capBack + T.upright) / 2, viaKey());
    push(bottleKeys, T.upright, bottleAt(U.G, U.axis, U.g));
    hold(bottleKeys, T.release);
    if (S.lift) push(bottleKeys, (T.release + LEN) / 2, bottleAt(v3.add(v3.lerp(U.G, restG, 0.5), S.lift), v3.lerp([0, -1, 0], U.axis, 0.5), [-1, 0, 0.35]));
    // sat: all the way back to the knee (the body is back on the seat by then; a point between is out of reach)
    push(bottleKeys, LEN, bottleAt(S.seated ? restG : v3.lerp(U.G, restG, 0.6), [0.15, -1, 0], [-1, 0, 0.3]));

    // ---------------------------------------------------------------- the cap and the left hand (alcohol_clean's, held aside)
    const pinch = 'prop:medical_alcohol_cap:pinch';
    const L = (t, blend, roll, pole, target) => handL.push({at: r4(t), target: target || pinch, blend: r4(blend), roll, pole: pole || POLE_L});
    const capOn = (spin, rise) => ({frame: 'prop:medical_alcohol', point: 'center', pos: v3.add(cLocal('center'), [0, -rise, 0]), rot: [0, spin, 0]});
    const uQ = bottleTurn(U.axis, U.g), uPlace = {q: uQ, t: v3.sub(U.G, q4.apply(uQ, bLocal('grip')))};
    const capChestFrom = (spin, rise) => ({frame: 'chest', point: 'center', pos: tf.apply(uPlace, v3.add(cLocal('center'), [0, -rise, 0])), rot: rotOf(q4.mul(uPlace.q, q4.y(rad(spin))))});
    push(capKeys, 0, capOn(0, 0));
    hold(capKeys, T.turns);
    L(0, 0, TWIST, POLE_L, 'pose');
    L(T.reach, T.turns - T.reach, TWIST);
    let spin = 0, rise = 0, t = T.turns;
    for (let i = 0; i < 3; i++) {
        spin -= STEP; rise += RISE;
        push(capKeys, t + TURN_T, capOn(spin, rise));
        L(t, TURN_T, -TWIST);
        hold(capKeys, t + GRIP);
        L(t + TURN_T, GRIP - TURN_T, TWIST);
        t += GRIP;
    }
    spin -= 30; rise += 0.1;
    push(capKeys, T.off, capOn(spin, rise));
    L(T.thread, T.off - T.thread, TWIST - 30);
    push(capKeys, T.lifted, capOn(spin, rise + LIFT));
    const capClear = capChestFrom(spin, rise + LIFT + CLEAR);
    push(capKeys, T.lifted + 0.0001, capChestFrom(spin, rise + LIFT));
    push(capKeys, T.clear, capClear);
    // aside: the cap's up back along the hand (its top toward the fingertips), as alcohol_clean's held-out hand
    let capHeld = ID;
    for (let pass = 0; pass < 2; pass++) {
        const a = actOf(S, name, {props: [{id: 'medical_alcohol', keys: [{at: 0, frame: 'chest', point: 'grip', pos: pick.Gs[0], rot: rotOf(pick.Q), blend: 0}]},
            {id: 'medical_alcohol_cap', keys: [{at: 0, frame: 'chest', point: 'pinch', pos: S.aside, rot: rotOf(capHeld), blend: 0}]}],
        hands: {right: [], left: [{at: 0, target: pinch, blend: 0, roll: ROLL_UP, pole: POLE_OUT}]}}, clip);
        const f = A.frameInfo(frame(a, 2.5));
        const cy = v3.norm(v3.sub(f.capL, f.wristL)), cx0 = Math.abs(cy[1]) < 0.9 ? [0, 1, 0] : [1, 0, 0];
        const cz = v3.norm(v3.cross(cx0, cy));
        capHeld = q4.norm(q4.fromBasis(v3.cross(cy, cz), cy, cz));
    }
    push(capKeys, T.held, {frame: 'chest', point: 'pinch', pos: S.aside, rot: rotOf(capHeld)});
    L(T.clear, T.held - T.clear, ROLL_UP, POLE_OUT);
    hold(capKeys, T.capOut);
    push(capKeys, T.capBack, capClear);
    L(T.capOut, T.capBack - T.capOut, TWIST - 30);
    hold(capKeys, T.upright);
    push(capKeys, T.upright + 0.0001, capOn(spin, rise + LIFT + CLEAR));
    push(capKeys, T.onThread, capOn(spin, rise));
    L(T.upright, T.onThread - T.upright, -TWIST);
    t = T.onThread;
    const back = [spin + 60, spin + 120, 0];
    for (let i = 0; i < 3; i++) {
        const toRise = i === 2 ? 0 : Math.max(0, rise - (i + 1) * 0.15);
        push(capKeys, t + TURN_T2, capOn(back[i], toRise));
        L(t, TURN_T2, TWIST);
        hold(capKeys, t + GRIP2);
        if (i < 2) L(t + TURN_T2, GRIP2 - TURN_T2, -TWIST);
        t += GRIP2;
    }
    L(T.release, LEN - T.release, TWIST, POLE_L, 'pose');
    hold(capKeys, LEN);

    // ---------------------------------------------------------------- the right hand on the grip, rolled so the palm faces the bottle
    function bestRollR(seconds) {
        let best = null;
        for (let r = -60; r <= 60; r += 5) {   // the forearm's twist kept in its range (alcohol_clean's limit)
            handR.length = 0;
            handR.push({at: 0, target: 'prop:grip', blend: 0, roll: r, pole: POLE_R});
            const res = frame(actOf(S, name, json(), clip), seconds), f = A.frameInfo(res), ro = core.readouts(res);
            const axisPt = tf.apply(f.inv, tf.apply(res.props[0], [bLocal('mouth')[0], bLocal('grip')[1], bLocal('mouth')[2]]));
            const score = v3.dot(f.palmNormalR, v3.norm(v3.sub(axisPt, f.palmR))) - (ro.right.twist > 30 ? 1 : 0) - (ro.right.wrist > 50 ? 1 : 0) - ro.right.off;
            if (!best || score > best.score) best = {r, score, twist: ro.right.twist, wrist: ro.right.wrist, off: ro.right.off};
        }
        return best;
    }
    const rAt = [[0, T.up], [T.bMove, T.over], [T.over, T.tipped], [T.tipped, T.poured], [T.poured, T.back], [T.capBack, T.upright], [T.release, LEN]];
    const first = bestRollR(T.up), rolls = rAt.map(([a, b]) => bestRollR(b));
    handR.length = 0;
    handR.push({at: 0, target: 'prop:grip', blend: 0, roll: first.r, pole: POLE_R});
    rAt.forEach(([a, b], i) => handR.push({at: r4(a), target: 'prop:grip', blend: r4(b - a), roll: rolls[i].r, pole: POLE_R}));
    for (const k of bottleKeys.concat(capKeys)) {
        const e = k.rot.map(rad), q = q4.xyz(e[0], e[1], e[2]);
        if (!keyable(q)) throw new Error(name + ': a prop key near the euler lock at ' + k.at);
    }
    return {S, clip, json: json(), pick, M0, M1, nrm, rolls};
}

// ------------------------------------------------------------------ the actions
const DOC = 'docs/gameplay/medical_actions_by_part_v1.md';
const ABOUT = {
    head: 'the head: the head tipped to the left (and the body a little), its right side up; the bottle taken up beside it and poured on above the right ear, the mouth moving back along it.',
    chest: 'the chest: leant back a little, looking down; poured on the front of the chest, the mouth moving across it.',
    belly: 'the belly: leant back a little, looking down; poured on the front of the belly, the mouth moving across it.',
    leg: 'a shin, sat on the ground (MedicalItem sits the player first and leaves them sat, sit/GroundSit; keyed on the left leg, mirrored for the right): from the seat (body.sit) leant forward, the left knee up as for bandage_leg (pose_lib SHIN_WORK), poured on down the shin bone; back to the seat at the end. The own first-person camera on the posed eye (first_person_eye).',
};
function actionOf(name, out) {
    const S = out.S;
    const a = {format_version: 1,
        about: 'Medical alcohol by part (' + DOC + '), 4.6 s (MedicalItem.ALCOHOL_TICKS 92) for every part: item/alcohol_clean\'s cap work at its times (MedicalItem.ALCOHOL_CUES), the pour 2.15..2.85 s (client/MedicalPourJets 2.1..2.9: the stream from the bottle prop\'s mouth while its axis points down), the cap held aside in the left fingertips while the right hand pours on ' + ABOUT[name] + ' Both hands by IK at run time on the keyed props; the body the clip (hold_body' + (S.mask === 'full' ? '' : ', hold_head') + '). Written by tools/medical-actions-v1a/author_alcohol_parts_v1.cjs.',
        afl_rig: {plugin: core.VERSION, animation: 'medical.alcohol_' + name}, clip: 'medical.alcohol_' + name, layer: 'action', mask: S.mask, priority: 50,
        first_person: 'body', blend: {in: 0.3, out: 0.3}, first_person_pitch: S.fp};
    if (S.mask === 'full') a.first_person_eye = true;
    a.hold_body = true;
    if (S.mask !== 'full') a.hold_head = true;
    Object.assign(a, out.json, {events: [], check: {use_ticks: 92}});
    return a;
}

// ------------------------------------------------------------------ checks: the real action every 0.025 s
const BOXES = {
    head: [-4, 4, -8, 0, -4, 4], chest: [-4, 4, 0, 6, -2, 2], pelvis: [-4, 4, 6, 12, -2, 2],
    thigh_r: [-3.9, 0.1, 12, 18, -2, 2], shin_r: [-3.9, 0.1, 18, 22, -2, 2], foot_r: [-3.9, 0.1, 22, 24, -2, 2],
    thigh_l: [-0.1, 3.9, 12, 18, -2, 2], shin_l: [-0.1, 3.9, 18, 22, -2, 2], foot_l: [-0.1, 3.9, 22, 24, -2, 2],
};
const boxDist = (p, B) => Math.hypot(Math.max(B[0] - p[0], 0, p[0] - B[1]), Math.max(B[2] - p[1], 0, p[1] - B[3]), Math.max(B[4] - p[2], 0, p[2] - B[5]));
function check(name, act) {
    const S = PARTS[name];
    const w = {offR: 0, offRAt: 0, offL: 0, offLAt: 0, bottle: 99, bottleAt: '', palm: 99, palmAt: '', capGap: 99, mouthWound: 99, axisDown: 1, rows: []};
    const woundAt = res => res.pose.point(bone(S.bone), v3.add(S.wound, v3.mul(S.slide, 0.5)));
    for (let s = 0.3; s <= LEN - 0.3 + 1e-6; s += 0.025) {
        const res = frame(act, s), ro = core.readouts(res), f = A.frameInfo(res);
        if (ro.right.mode === 'ik' && ro.right.w > 0.99 && ro.right.off > w.offR) { w.offR = ro.right.off; w.offRAt = s.toFixed(2); }
        if (ro.left.mode === 'ik' && ro.left.w > 0.99 && ro.left.off > w.offL) { w.offL = ro.left.off; w.offLAt = s.toFixed(2); }
        const bp = res.props[0], cp = res.props[1];
        const base = tf.apply(bp, bLocal('base')), mouth = tf.apply(bp, bLocal('mouth')), tip = tf.apply(bp, bLocal('mouth_tip'));
        if (s > 0.6 && cp) w.capGap = Math.min(w.capGap, v3.dist(tf.apply(f.inv, tf.apply(cp, cLocal('center'))), f.capL));
        for (const [bn, B] of Object.entries(BOXES)) {
            if (S.mask !== 'full' && !['head', 'chest', 'pelvis'].includes(bn)) continue;
            const iv = tf.inv(res.pose.world[bone(bn)]);
            for (let i = 0; i <= 8; i++) {   // the bottle's axis (r 1.0 px)
                const d = boxDist(tf.apply(iv, v3.lerp(base, mouth, i / 8)), B) - 1.0;
                if (d < w.bottle) { w.bottle = d; w.bottleAt = bn + '@' + s.toFixed(2); }
            }
            for (const right of [true, false]) {
                const d = boxDist(tf.apply(iv, res.pose.point(right ? rig.handR : rig.handL, right ? [-6, 10.5, 0] : [6, 10.5, 0])), B) - 2;
                if (bn !== 'chest' && d < w.palm) { w.palm = d; w.palmAt = (right ? 'R ' : 'L ') + bn + '@' + s.toFixed(2); }
            }
        }
        if (s >= 2.15 && s <= 2.85) {
            const axis = v3.norm(v3.sub(tip, mouth));
            w.axisDown = Math.min(w.axisDown, axis[1]);
            // the stream falls from the mouth: how far it passes from the wound's middle (horizontal), the drop to it
            const wd = woundAt(res), d = v3.sub(wd, mouth);
            w.mouthWound = Math.min(w.mouthWound, Math.hypot(d[0], d[2]));
            w.dropTo = d[1];
        }
        if (process.env.ROWS && Math.abs(s * 10 - Math.round(s * 10)) < 1e-6)
            w.rows.push(`${s.toFixed(1)} R ${ro.right.mode === 'ik' ? ro.right.off.toFixed(2) + ' tw' + ro.right.twist.toFixed(0) + ' wr' + ro.right.wrist.toFixed(0) : '-'} L ${ro.left.mode === 'ik' ? ro.left.off.toFixed(2) + ' tw' + ro.left.twist.toFixed(0) + ' wr' + ro.left.wrist.toFixed(0) : '-'}`);
    }
    return w;
}
function screens(name, act) {
    const S = PARTS[name], out = [];
    for (const s of [1.0, 2.3, 2.7, 4.0]) {
        const res = frame(act, s, S.fp), p = rad(S.fp);
        const cam = S.mask === 'full' ? res.pose.point(rig.sockets.eye.bone, rig.sockets.eye.pos) : [0, core.eyeModelY(false), 0];
        const fwd = [0, Math.sin(p), -Math.cos(p)], up = [0, -Math.cos(p), -Math.sin(p)];
        const sc = Pm => { const rel = v3.sub(Pm, cam), f = v3.dot(rel, fwd); return `${(Math.atan2(-rel[0], f) * 180 / Math.PI).toFixed(0)}/${(Math.atan2(v3.dot(rel, up), f) * 180 / Math.PI).toFixed(0)}`; };
        out.push(`${s.toFixed(1)} mouth ${sc(tf.apply(res.props[0], bLocal('mouth')))} cap ${sc(tf.apply(res.props[1], cLocal('center')))} palmL ${sc(res.pose.point(rig.handL, [6, 10.5, 0]))}`);
    }
    return out;
}

const results = {};
for (const name of Object.keys(PARTS)) {
    if (ONLY && ONLY !== name) continue;
    const out = author(name);
    const action = actionOf(name, out);
    const clips = core.loadClips(rig, {animations: {['medical.alcohol_' + name]: out.clip}});
    const act = core.actionFromJson(action, A.defFor, k => clips[k] || null);
    const w = check(name, act);
    console.log('== alcohol_' + name + '  grip side ' + out.pick.g + ' roll ' + out.pick.roll + ' (reach ' + out.pick.sc.toFixed(2) + ')  right rolls ' + out.rolls.map(r => r.r).join(','));
    if (process.env.ROWS) console.log(w.rows.join('\n'));
    console.log(`IK worst: R ${w.offR.toFixed(2)} @${w.offRAt}  L ${w.offL.toFixed(2)} @${w.offLAt}  cap to the left fingertips ${w.capGap.toFixed(2)}`);
    console.log(`nearest: bottle ${w.bottle.toFixed(2)} ${w.bottleAt}  palm ${w.palm.toFixed(2)} ${w.palmAt}`);
    console.log(`pour: axis down >= ${w.axisDown.toFixed(2)} (MedicalPourJets: > 0.2)  the stream's way past the wound's middle ${w.mouthWound.toFixed(2)} px, ${w.dropTo.toFixed(1)} px down to it`);
    console.log('FP (deg right/up): ' + screens(name, act).join(' | '));
    results[name] = {out, action};
}

if (WRITE) {
    const pretty = o => JSON.stringify(o, null, 2) + '\n';
    const clipPath = path.join(A.A, 'animations/player/medical.animation.json');
    const medical = A.read(clipPath);
    for (const [name, r] of Object.entries(results)) {
        medical.animations['medical.alcohol_' + name] = r.out.clip;
        fs.writeFileSync(path.join(A.A, 'player_actions/item/alcohol_' + name + '.json'), pretty(r.action));
    }
    fs.writeFileSync(clipPath, pretty(medical));
    console.log('written', Object.keys(results).join(', '));
}
