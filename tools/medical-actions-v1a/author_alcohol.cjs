// Authoring sheet for item/alcohol_clean (keyframes chosen by hand below; this does the geometry for them): the bottle in the
// right hand (player_props/medical_alcohol, first prop), its cap (player_props/medical_alcohol_cap) riding it while screwed and
// carried in the left fingertips while off; hand rolls found so the palms face what they hold.
// node author_alcohol.cjs [--write]
const A = require('./alc_lib.cjs');
const {fs, path, core, v3, q4, tf, rad, deg, rig, poser, bottle, cap, loc, rotOf, r4, bottleTurn} = A;
const WRITE = process.argv.includes('--write');
const LEN = 4.6;   // MedicalItem.ALCOHOL_TICKS 92

// ------------------------------------------------------------------ hand-picked places (chest frame, Java px)
const U = {G: [-1.5, 8.5, -6], axis: [0.45, -0.85, 0.15], g: [-1, 0, 0.4]};   // unscrewing: right palm, bottle up, grip side
const POLE_L = [1, -0.6, 0.25];         // left elbow out and up over the cap
const TWIST = 22;                       // the left hand's roll either side while it turns the cap
const P_L = [4, 5, -9];                 // the left palm point while the forearm is held out, palm up (cap in the fingertips)
const POLE_OUT = [-0.5, 1, 0.3], ROLL_UP = -30;   // elbow in and down, palm up (the forearm's twist at the 30 deg limit)
const POUR_AXIS = [0.9, 0.38, -0.1];    // the bottle's up while pouring: over to the left and down (world y -0.38: it pours)
const MOUTH_UP = 2.0, POUR_SLIDE = [2.8, 4.4];   // the mouth this far over the inner forearm's sleeve; from/to px back from the hand's end
const REST_G = [-6, 10.5, 0];           // the right palm at rest

// ------------------------------------------------------------------ helpers
const bottleKeys = [], capKeys = [], handR = [], handL = [];
const bLocal = n => loc(bottle, n), cLocal = n => loc(cap, n);
function json(bones) {
    return {clip: 'medical.alcohol_clean', mask: 'upper', blend: {in: 0.3, out: 0.3},
        props: [{id: 'medical_alcohol', keys: bottleKeys}, {id: 'medical_alcohol_cap', keys: capKeys}],
        hands: {right: handR, left: handL}};
}
/** Appends a prop key arriving at t (at = the previous key's arrival). */
function push(keys, t, k) {
    const prev = keys.length ? keys[keys.length - 1] : null, start = prev ? prev._t : 0;
    const key = {at: r4(prev ? start : 0), frame: k.frame || 'chest', point: k.point, pos: k.pos.map(r4), rot: k.rot.map(x => +x.toFixed(3)), blend: r4(prev ? t - start : 0)};
    if (k.hidden) key.hidden = true;
    Object.defineProperty(key, '_t', {value: t, enumerable: false});
    keys.push(key);
}
const hold = (keys, t) => { const p = keys[keys.length - 1]; push(keys, t, {frame: p.frame, point: p.point, pos: p.pos, rot: p.rot, hidden: p.hidden}); };
/** The bottle with its grip at G, up along axis, grip side g. */
const bottleAt = (G, axis, g) => ({point: 'grip', pos: G, rot: rotOf(bottleTurn(axis, g))});
/** The bottle's placement (model frame -> chest frame) for a grip key. */
function bottlePlace(G, axis, g) {
    const q = bottleTurn(axis, g);
    return {q, t: v3.sub(G, q4.apply(q, bLocal('grip')))};
}
/** The cap on the bottle (prop frame): spun spin deg about the bottle's axis (- loosens), risen rise px. */
const capOn = (spin, rise) => ({frame: 'prop:medical_alcohol', point: 'center', pos: v3.add(cLocal('center'), [0, -rise, 0]), rot: [0, spin, 0]});
/** The same cap in the chest frame, from the bottle's placement. */
function capChestFrom(place, spin, rise) {
    const turn = q4.mul(place.q, q4.y(rad(spin)));
    const at = tf.apply(place, v3.add(cLocal('center'), [0, -rise, 0]));
    return {frame: 'chest', point: 'center', pos: at, rot: rotOf(turn)};
}

// ------------------------------------------------------------------ the held-out left arm (pour), solved first
const uPlace = bottlePlace(U.G, U.axis, U.g);
let capHeld = {q: q4.id()}, outArm = null;
for (let pass = 0; pass < 2; pass++) {   // the cap's up back along the hand (its top toward the fingertips)
    const res = A.still({G: U.G, Q: bottleTurn(U.axis, U.g), capChest: {pos: P_L, q: capHeld.q}, rollL: ROLL_UP, poleL: POLE_OUT});
    const f = A.frameInfo(res), ro = core.readouts(res);
    const handDir = v3.norm(v3.sub(f.capL, f.wristL));
    const cy = handDir, cx0 = Math.abs(cy[1]) < 0.9 ? [0, 1, 0] : [1, 0, 0];
    const cz = v3.norm(v3.cross(cx0, cy)), cx = v3.cross(cy, cz);
    capHeld = {q: q4.norm(q4.fromBasis(cx, cy, cz))};
    outArm = {f, ro, elbow: f.elbowL, cap: f.capL, fore: v3.norm(v3.sub(f.wristL, f.elbowL)), up: f.palmNormalL};
}
console.log('arm out: palm normal', outArm.up.map(x => +x.toFixed(2)), 'twist', outArm.ro.left.twist.toFixed(0), 'wrist', outArm.ro.left.wrist.toFixed(0), 'elbow', outArm.elbow.map(x => +x.toFixed(1)), 'forearm', outArm.fore.map(x => +x.toFixed(2)));
const along = d => v3.sub(outArm.cap, v3.mul(outArm.fore, d));
const mouthAt = d => v3.add(along(d), [0, -(2.25 + MOUTH_UP), 0]);   // straight over the sleeve: the stream falls onto it

// ------------------------------------------------------------------ timeline (s)
const T = {up: 0.45, reach: 0.2, turns: 0.5, thread: 1.4, off: 1.48, lifted: 1.6, clear: 1.75, held: 2.0,
    bMove: 1.6, over: 1.85, tipped: 2.15, poured: 2.85, back: 3.05, capOut: 2.9, capBack: 3.2, upright: 3.45, onThread: 3.55, release: 4.36};
const GRIP = 0.3, TURN_T = 0.2, GRIP2 = 0.27, TURN_T2 = 0.18, STEP = 50, RISE = 0.12, LIFT = 1.3, CLEAR = 1.6;

// ------------------------------------------------------------------ the bottle (first prop)
const pourQ = bottleTurn(POUR_AXIS, v3.sub([0, 1, 0], v3.mul(v3.norm(POUR_AXIS), v3.norm(POUR_AXIS)[1])));
const pourG = at => v3.add(at, q4.apply(pourQ, v3.sub(bLocal('grip'), bLocal('mouth'))));
const POUR_FROM_AT = mouthAt(POUR_SLIDE[0]), POUR_TO_AT = mouthAt(POUR_SLIDE[1]);
const overAxis = v3.lerp(v3.norm(U.axis), v3.norm(POUR_AXIS), 0.45);
const overQ = bottleTurn(overAxis, v3.sub([0, 1, 0], v3.mul(overAxis, overAxis[1])));
const overG = v3.add(v3.add(POUR_FROM_AT, [0, -3.2, 0]), q4.apply(overQ, v3.sub(bLocal('grip'), bLocal('mouth'))));   // mouth 3.2 px higher, half tipped
push(bottleKeys, 0, bottleAt(REST_G, [0, -1, 0], [-1, 0, 0.3]));
push(bottleKeys, T.up, bottleAt(U.G, U.axis, U.g));                 // up in front, tilted toward the left hand
hold(bottleKeys, T.bMove);
push(bottleKeys, T.over, {point: 'grip', pos: overG, rot: rotOf(overQ)});                  // over the held-out forearm
push(bottleKeys, T.tipped, {point: 'grip', pos: pourG(POUR_FROM_AT), rot: rotOf(pourQ)});  // tipped, the mouth over the inner forearm
push(bottleKeys, T.poured, {point: 'grip', pos: pourG(POUR_TO_AT), rot: rotOf(pourQ)});    // pouring along it toward the elbow
push(bottleKeys, T.back, {point: 'grip', pos: overG, rot: rotOf(overQ)});                 // up off the forearm
hold(bottleKeys, T.capBack);
push(bottleKeys, T.upright, bottleAt(U.G, U.axis, U.g));            // down under the cap the left hand brought back
hold(bottleKeys, T.release);
push(bottleKeys, LEN, bottleAt(v3.lerp(U.G, REST_G, 0.6), [0.15, -1, 0], [-1, 0, 0.3]));   // going down

// ------------------------------------------------------------------ the cap (second prop) and the left hand
const pinch = 'prop:medical_alcohol_cap:pinch';
const L = (at, blend, roll, pole, target) => handL.push({at: r4(at), target: target || pinch, blend: r4(blend), roll, pole: pole || POLE_L});
push(capKeys, 0, capOn(0, 0));
hold(capKeys, T.turns);
L(0, 0, TWIST, POLE_L, 'pose');
L(T.reach, T.turns - T.reach, TWIST);
let spin = 0, rise = 0, t = T.turns;
for (let i = 0; i < 3; i++) {                                      // three turns of the fingers, each 50 deg the loose way
    spin -= STEP; rise += RISE;
    push(capKeys, t + TURN_T, capOn(spin, rise));
    L(t, TURN_T, -TWIST);
    hold(capKeys, t + GRIP);
    L(t + TURN_T, GRIP - TURN_T, TWIST);                            // a new hold
    t += GRIP;
}
spin -= 30; rise += 0.1;
push(capKeys, T.off, capOn(spin, rise));                           // the last of the thread
L(T.thread, T.off - T.thread, TWIST - 30);
push(capKeys, T.lifted, capOn(spin, rise + LIFT));                 // off the mouth
const capClear = capChestFrom(uPlace, spin, rise + LIFT + CLEAR);
push(capKeys, T.lifted + 0.0001, capChestFrom(uPlace, spin, rise + LIFT));   // the same place, now in the chest frame
push(capKeys, T.clear, capClear);                                  // further up, clear of the bottle
push(capKeys, T.held, {frame: 'chest', point: 'pinch', pos: P_L, rot: rotOf(capHeld.q)});   // out with the forearm, palm up
L(T.clear, T.held - T.clear, ROLL_UP, POLE_OUT);
hold(capKeys, T.capOut);
push(capKeys, T.capBack, capClear);                                // back to where the mouth will be, lined up
L(T.capOut, T.capBack - T.capOut, TWIST - 30);
hold(capKeys, T.upright);
push(capKeys, T.upright + 0.0001, capOn(spin, rise + LIFT + CLEAR));
push(capKeys, T.onThread, capOn(spin, rise));                       // down onto the thread
L(T.upright, T.onThread - T.upright, -TWIST);
t = T.onThread;
const back = [spin + 60, spin + 120, 0];
for (let i = 0; i < 3; i++) {                                      // three turns the tight way
    const toRise = i === 2 ? 0 : Math.max(0, rise - (i + 1) * 0.15);
    push(capKeys, t + TURN_T2, capOn(back[i], toRise));
    L(t, TURN_T2, TWIST);
    hold(capKeys, t + GRIP2);
    if (i < 2) L(t + TURN_T2, GRIP2 - TURN_T2, -TWIST);
    t += GRIP2;
}
L(T.release, LEN - T.release, TWIST, POLE_L, 'pose');
hold(capKeys, LEN);

// ------------------------------------------------------------------ the right hand: on the grip, rolled so the palm faces the bottle
function bestRollR(seconds, act) {
    let best = null;
    for (let r = -120; r <= 120; r += 5) {
        handR.length = 0;
        handR.push({at: 0, target: 'prop:grip', blend: 0, roll: r});
        const res = core.solveFrame(poser, {action: A.actionOf(json()), seconds});
        const f = A.frameInfo(res), ro = core.readouts(res);
        const placed = res.props[0];
        const axisPt = tf.apply(f.inv, tf.apply(placed, v3.lerp(bLocal('grip'), [bLocal('mouth')[0], bLocal('grip')[1], bLocal('mouth')[2]], 1)));
        const want = v3.norm(v3.sub(axisPt, f.palmR));
        const score = v3.dot(f.palmNormalR, want) - (ro.right.twist > 30 ? 1 : 0) - (ro.right.wrist > 50 ? 1 : 0);
        if (!best || score > best.score) best = {r, score, twist: ro.right.twist, wrist: ro.right.wrist, off: ro.right.off};
    }
    return best;
}
const rollTimes = [[0, T.up], [T.up, T.bMove], [T.bMove, T.over], [T.over, T.tipped], [T.tipped, T.poured], [T.poured, T.back], [T.capBack, T.upright], [T.release, LEN]];
const rolls = rollTimes.map(([a, b]) => { const r = bestRollR(b); return {at: a, arrive: b, r}; });
handR.length = 0;
handR.push({at: 0, target: 'prop:grip', blend: 0, roll: rolls[0].r.r});
const rAt = [[0, T.up, 1], [T.bMove, T.over - T.bMove, 2], [T.over, T.tipped - T.over, 3], [T.tipped, T.poured - T.tipped, 4], [T.poured, T.back - T.poured, 5], [T.capBack, T.upright - T.capBack, 6], [T.release, LEN - T.release, 7]];
for (const [at, bl, i] of rAt) handR.push({at: r4(at), target: 'prop:grip', blend: r4(bl), roll: rolls[i].r.r});
console.log('right rolls', rolls.map(x => x.at + '->' + x.r.r + ' (twist ' + x.r.twist.toFixed(0) + ', wrist ' + x.r.wrist.toFixed(0) + ')').join('; '));

// ------------------------------------------------------------------ the action
const action = Object.assign({
    format_version: 1,
    about: '',
    afl_rig: {plugin: core.VERSION, animation: 'medical.alcohol_clean'},
    clip: undefined,
}, {
    layer: 'action', mask: 'upper', priority: 50, first_person: 'body', blend: {in: 0.3, out: 0.3},
    first_person_pitch: 65,
    props: [{id: 'medical_alcohol', keys: bottleKeys}, {id: 'medical_alcohol_cap', keys: capKeys}],
    hands: {right: handR, left: handL},
    events: [],
    check: {use_ticks: 92},
});
delete action.clip;

// ------------------------------------------------------------------ check
const act = A.actionOf(action);
if (process.env.DBG) console.log(JSON.stringify(act.props.map(p => ({id: p.id, def: !!p.def, n: p.keys.length, k0: p.keys[0]}))), act.blendIn);
const rows = [];
let worst = {L: {off: 0, wrist: 0, twist: 0}, R: {off: 0, wrist: 0, twist: 0}, capHand: 99, capHandAt: 0, capBottle: 99, capBottleAt: 0};
const FP = +(process.env.FP || action.first_person_pitch);
function screen(Pm) {
    const p = rad(FP), cam = [0, core.eyeModelY(false), 0], rel = v3.sub(Pm, cam);
    const fwd = [0, Math.sin(p), -Math.cos(p)], up = [0, -Math.cos(p), -Math.sin(p)];
    const f = v3.dot(rel, fwd);
    return `${(Math.atan2(-rel[0], f) * 180 / Math.PI).toFixed(0)}/${(Math.atan2(v3.dot(rel, up), f) * 180 / Math.PI).toFixed(0)}@${v3.len(rel).toFixed(1)}`;
}
for (let s = 0; s <= LEN + 1e-6; s += 0.025) {
    const res = core.solveFrame(poser, {action: act, seconds: s, time: s, pitch: 0});
    const f = A.frameInfo(res), ro = core.readouts(res);
    for (const side of ['right', 'left']) {
        const r = ro[side], w = worst[side === 'right' ? 'R' : 'L'];
        if (r.mode === 'ik' && r.w > 0.99) w.off = Math.max(w.off, r.off);
        if (r.mode === 'ik' && r.w > 0.5) { w.wrist = Math.max(w.wrist, r.wrist); w.twist = Math.max(w.twist, r.twist); }
    }
    const bp = res.props[0], cp = res.props[1];
    if (!bp || !cp) continue;
    const capC = tf.apply(f.inv, tf.apply(cp, cLocal('center')));
    const mouth = tf.apply(f.inv, tf.apply(bp, bLocal('mouth'))), tip = tf.apply(f.inv, tf.apply(bp, bLocal('mouth_tip')));
    const handGap = v3.dist(capC, f.capL);       // cap middle to the left hand's end
    if (s > 0.6 && handGap < worst.capHand) { worst.capHand = handGap; worst.capHandAt = s; }
    // the bottle (axis base..mouth, r 1) against the left forearm and hand (middle lines, half 2.25), the chest; the cap off it
    const base = tf.apply(f.inv, tf.apply(bp, bLocal('base')));
    const segSeg = (a, b, c, d) => { let m = 1e9; for (let i = 0; i <= 20; i++) { const p = v3.lerp(a, b, i / 20); const cd = v3.sub(d, c); const dd = v3.dot(cd, cd); const u = dd < 1e-9 ? 0 : Math.max(0, Math.min(1, v3.dot(v3.sub(p, c), cd) / dd)); m = Math.min(m, v3.dist(p, v3.add(c, v3.mul(cd, u)))); } return m; };
    const bArm = segSeg(base, mouth, f.elbowL, f.wristL) - 1 - 2.25;
    const bArmR = segSeg(base, mouth, f.elbowR, f.wristR) - 1 - 2.25;
    if (s > 0.5 && bArm < (worst.bottleArm || 99)) { worst.bottleArm = bArm; worst.bottleArmAt = s; }
    const bHandL = segSeg(base, mouth, f.palmL, f.palmL) - 1 - 2;
    if (s > 0.5 && bHandL < (worst.bottleHandL || 99)) { worst.bottleHandL = bHandL; worst.bottleHandLAt = s; }
    if (process.env.HITS && (bArm < 0 || bHandL < 0 || bArmR < -0.3)) console.log('hit', s.toFixed(3), 'arm', bArm.toFixed(2), 'handL', bHandL.toFixed(2), 'armR', bArmR.toFixed(2));
    if (s > 0.5 && bArmR < (worst.bottleArmR || 99)) { worst.bottleArmR = bArmR; worst.bottleArmRAt = s; }
    const capOffBottle = segSeg(capC, capC, base, mouth) - 1 - 0.5;
    if (s > T.lifted + 0.02 && s < T.capBack - 0.02 && capOffBottle < (worst.capBottle)) { worst.capBottle = capOffBottle; worst.capBottleAt = s; }
    if (Math.abs(s * 10 - Math.round(s * 10)) < 1e-6) {
        const res2 = core.solveFrame(poser, {action: act, seconds: s, time: s, pitch: FP});
        const fp = `fp bottle ${screen(tf.apply(res2.props[0], bLocal('mouth')))} cap ${screen(tf.apply(res2.props[1], cLocal('center')))} palmL ${screen(res2.pose.point(rig.handL, [6, 10.5, 0]))} palmR ${screen(res2.pose.point(rig.handR, [-6, 10.5, 0]))}`;
        rows.push(`${s.toFixed(1)} mouth ${mouth.map(x => x.toFixed(1)).join(',')} axisY ${v3.norm(v3.sub(tip, mouth))[1].toFixed(2)} cap ${capC.map(x => x.toFixed(1)).join(',')} L ${ro.left.mode === 'ik' ? 'w' + ro.left.w.toFixed(2) + ' off' + ro.left.off.toFixed(1) + ' wr' + ro.left.wrist.toFixed(0) + ' tw' + ro.left.twist.toFixed(0) : 'clip'} R w${ro.right.w.toFixed(2)} off${ro.right.off.toFixed(1)} wr${ro.right.wrist.toFixed(0)} tw${ro.right.twist.toFixed(0)} | ${fp}`);
    }
}
console.log(rows.join('\n'));
console.log(JSON.stringify(worst, (k, v) => (typeof v === 'number' ? +v.toFixed(2) : v)));

if (WRITE) {
    const pretty = o => JSON.stringify(o, null, 2) + '\n';
    fs.writeFileSync(path.join(A.A, 'player_actions/item/alcohol_clean.json'), pretty(action));
    const capPath = path.join(A.A, 'player_props/medical_alcohol_cap.json');
    const capJson = A.read(capPath);
    capJson.points = cap.points;
    fs.writeFileSync(capPath, pretty(capJson));
    console.log('written');
}
