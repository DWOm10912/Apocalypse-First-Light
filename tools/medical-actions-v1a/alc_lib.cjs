// Geometry for item/alcohol_clean: the bottle and cap props, their turns, solving one still moment.
const L = require('./lib.cjs');
const {core, v3, q4, tf, rad, deg, rig, poser} = L;
const bottle = L.propDef('medical_alcohol');
const cap = L.propDef('medical_alcohol_cap');
const K = 16 * bottle.scale;
cap.points = {center: cap.points.center, top: cap.points.top, pinch: [0.5, +(cap.points.top[1] + 1.6 / K).toFixed(4), 0.5]};
const loc = (def, n) => core.propLocal(def, n);
const rotOf = q => q4.eulerXYZ(q).map(deg).map(x => +x.toFixed(3));
const r4 = x => +x.toFixed(4);
/** The bottle turned: its up (base to mouth) along axis, its grip side (from its axis to the grip) along g (made perpendicular). */
function bottleTurn(axis, g) {
    axis = v3.norm(axis);
    g = v3.norm(v3.sub(g, v3.mul(axis, v3.dot(g, axis))));
    const cx = v3.mul(g, -1), cy = v3.mul(axis, -1), cz = v3.cross(cx, cy);
    return q4.norm(q4.fromBasis(cx, cy, cz));
}
const defFor = id => (id === 'medical_alcohol' ? bottle : id === 'medical_alcohol_cap' ? cap : null);
function actionOf(json, bones) {
    const clips = core.loadClips(rig, {animations: {'medical.alcohol_clean': {animation_length: 4.6, bones: bones || {}}}});
    return core.actionFromJson(json, defFor, k => clips[k] || null);
}
/** One still moment: bottle grip at G turned by Q (chest), cap on it at spin deg / rise px, hands as given. */
function still(o) {
    const capKey = o.capChest
        ? {at: 0, frame: 'chest', point: 'pinch', pos: o.capChest.pos, rot: rotOf(o.capChest.q), blend: 0}
        : {at: 0, frame: 'prop:medical_alcohol', point: 'center', pos: v3.add(loc(cap, 'center'), [0, -(o.rise || 0), 0]), rot: [0, o.spin || 0, 0], blend: 0};
    const json = {clip: 'medical.alcohol_clean', mask: 'upper', blend: {in: 0, out: 0},
        props: [{id: 'medical_alcohol', keys: [{at: 0, frame: 'chest', point: 'grip', pos: o.G, rot: rotOf(o.Q), blend: 0}]},
            {id: 'medical_alcohol_cap', keys: [capKey]}],
        hands: {right: [{at: 0, target: 'prop:grip', blend: 0, roll: o.rollR || 0, pole: o.poleR}],
            left: o.left === false ? [] : [{at: 0, target: 'prop:medical_alcohol_cap:pinch', blend: 0, roll: o.rollL || 0, pole: o.poleL}]}};
    for (const s of ['right', 'left']) for (const k of json.hands[s]) if (!k.pole) delete k.pole;
    const res = core.solveFrame(poser, {action: actionOf(json, o.bones), seconds: 0.1, pitch: o.pitch || 0});
    return res;
}
/** Chest-frame helpers on a solved frame. */
function frameInfo(res) {
    const inv = tf.inv(res.pose.world[rig.chest]);
    const P = (b, p) => tf.apply(inv, res.pose.point(b, p));
    const R = b => q4.mul(inv.q, res.pose.rotation(b));
    return {P, R, inv,
        elbowL: P(rig.forearmL, [6, 6, 0]), wristL: P(rig.handL, [6, 10, 0]), capL: P(rig.handL, [6, 12, 0]), palmL: P(rig.handL, [6, 10.5, 0]),
        elbowR: P(rig.forearmR, [-6, 6, 0]), wristR: P(rig.handR, [-6, 10, 0]), palmR: P(rig.handR, [-6, 10.5, 0]),
        palmNormalL: q4.apply(R(rig.handL), [-1, 0, 0]), palmNormalR: q4.apply(R(rig.handR), [1, 0, 0]),
        backL: q4.apply(R(rig.handL), [0, 0, 1])};
}
module.exports = Object.assign({}, L, {bottle, cap, K, loc, rotOf, r4, bottleTurn, actionOf, still, frameInfo, defFor});
