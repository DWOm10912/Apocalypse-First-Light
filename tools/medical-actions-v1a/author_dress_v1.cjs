// Authoring sheet for the bandage on the head, chest, belly and a shin (docs/gameplay/medical_actions_by_part_v1.md; user
// 2026-10-21: a pad on the wound and turns round the part, the two hands passing the roll behind the head / the back /
// under the shin; a leg is done sat on the ground). Each action: the roll (player_props/bandage) keyed every STEP s on a
// path round the part (a rounded box, exponent EXP, D px off the bare part, in the part bone's frame), three turns on the
// clock of client/BandageWrapRenderer (wrapFrom / wrapSeconds, the same band), the way its turns go round (from the middle of
// the front toward the body's right, -x, on the head and trunk; toward the outside, +x, on the left shin); both palms put
// on the roll or the pad by the AFL Rig core's arm IK and baked into the clip as FK at every key, the hand rolled so the palm
// faces what it holds. Keyed right-handed / on the left leg: client/skeleton/SkeletonActions mirrors them.
//   node author_dress_v1.cjs [--write]     (ONLY=head|chest|belly|leg to print one)
//   animations/player/medical.animation.json   medical.bandage_{head,chest,belly,leg}
//   player_actions/item/bandage_{head,chest,belly,leg}.json
// Java model px: y down, the ground at 24, the front -z, the body's right -x.
const L = require('./lib.cjs');
const {fs, path, core, v3, q4, tf, rad, deg, A, rig, poser, fileEuler} = L;
const P = require('./pose_lib.cjs');
const {ID, BODY, bodyOf, constClip, clipOf, REST} = P;
const WRITE = process.argv.includes('--write');
const ONLY = process.env.ONLY || null;

const STEP = 0.05, EXP = 4;
const prop = L.read(path.join(A, 'player_props/bandage.json'));
const local = n => core.propLocal(prop, n);
const DUMMY = P.DUMMY;
const r4 = x => +(+x).toFixed(4);
const smooth = x => { x = Math.max(0, Math.min(1, x)); return x * x * (3 - 2 * x); };
const lerp = (a, b, s) => a + (b - a) * s;
const rotOf = q => q4.eulerXYZ(q).map(deg).map(x => +x.toFixed(3));

// ------------------------------------------------------------------ the parts (rest model px of the part's bone)
// band: px back from the distal end yEnd (BandageWrapRenderer HEAD_BAND / CHEST_BAND / BELLY_BAND / LEG_BAND), width a turn's;
// from / turn: wrapFrom and a third of wrapSeconds; beats: MedicalItem's wrapCues (press, lay, tear, the pats, lower);
// drop: the path this much lower (toward yEnd) behind the part; dir: +1 the turns go front -> -x first, -1 front -> +x.
const PARTS = {
    head: {len: 5, frame: 'head', cx: 0, cz: 0, hx: 4, hz: 4, yEnd: 0, band: [4.6, 7.4], width: 1.3, D: 1.6, drop: 2.2, bulge: 1.0, padDown: 1.4, padOff: 3.0, patAt: 40, dir: 1,
        reach: 0.35, press: 0.5, from: 0.8, turn: 1.0, tear: 4.0, pats: [4.15, 4.3], lower: 4.45, mask: 'upper', fp: 30,
        poleR: [-1, 0.25, 0.35], poleL: [1, 0.25, 0.35], grip: {out: 1.3, along: 2.4, down: 1.6}},
    chest: {len: 6, frame: 'chest', cx: 0, cz: 0, hx: 4, hz: 2, yEnd: 12, band: [6, 11], width: 2.0, D: 1.6, drop: 3.0, bulge: 1.0, padDown: 1.5, padOff: 2.5, dir: 1,
        reach: 0.4, press: 0.6, from: 0.9, turn: 1.4, tear: 5.3, pats: [5.45, 5.6], lower: 5.65, mask: 'upper', fp: 75,
        poleR: [-0.8, 0.7, 0.45], poleL: [0.8, 0.7, 0.45], grip: {out: 1.3, along: 2.4, down: 1.0}},
    belly: {len: 6, frame: 'pelvis', cx: 0, cz: 0, hx: 4, hz: 2, yEnd: 12, band: [0.6, 5.4], width: 2.0, D: 1.6, drop: -2.0, bulge: 1.0, padDown: 0, padOff: 2.5, dir: 1,
        reach: 0.4, press: 0.6, from: 0.9, turn: 1.4, tear: 5.3, pats: [5.45, 5.6], lower: 5.65, mask: 'upper', fp: 75,
        poleR: [-0.7, 1, 0.4], poleL: [0.7, 1, 0.4], grip: {out: 1.3, along: 2.4, down: 0.6}},
    leg: {len: 5, frame: 'shin_l', cx: 1.9, cz: 0, hx: 2, hz: 2, yEnd: 24, band: [2, 6], width: 1.7, D: 1.4, backIn: 0.8, drop: 0.3, bulge: 1.0, padDown: 0, padOff: 2.5, dir: -1,
        reach: 0.45, press: 0.5, from: 0.7, turn: 1.0, tear: 3.9, pats: [4.05, 4.2], lower: 4.4, mask: 'full', fp: 85,
        poleR: [-0.5, 1, 0.6], poleL: [0.5, 1, 0.6], grip: {out: 1.2, along: 3.4, down: 0}, ground: true},
};
// the upper body's lean (chest, deg forward) and the head's pitch (+ chin down) while working, per part
const LOOK = {
    head: {lean: 0, head: t => 0},
    chest: {lean: 4, head: () => 15},
    belly: {lean: 9, head: () => 38},
};
// sat on the ground for the shin (pose_lib SHIN_WORK): leant forward, the left knee up, the right leg out to the side
const LEG_WORK = P.SHIN_WORK;

// ------------------------------------------------------------------ the path round a part
const spow = (u, e) => Math.sign(u) * Math.pow(Math.abs(u), e);
/** The rounded box's point at th deg (0 the front, -z; dir 1: 90 the right, -x), off px out of the bare part (y 0). */
function ring(S, th, off) {
    const a = S.hx + off, b = S.hz + off, s = rad(th);
    return [S.cx - S.dir * a * spow(Math.sin(s), 2 / EXP), 0, S.cz - b * spow(Math.cos(s), 2 / EXP)];
}
function tangent(S, th) { return v3.norm(v3.sub(ring(S, th + 0.5, S.D), ring(S, th - 0.5, S.D))); }
function outward(S, th) { const t = tangent(S, th); return v3.norm([-t[2] * S.dir, 0, t[0] * S.dir]); }
const turnY = (S, i) => S.yEnd - (i === 0 ? S.band[0] + S.width / 2 : i === 1 ? (S.band[0] + S.band[1]) / 2 : S.band[1] - S.width / 2);
const wrapEnd = S => S.from + 3 * S.turn;
/** A hand or roll state round the part: th (deg, unwrapped), off (px out of the bare part), y, tan (px along the path), aim. */
function place(S, st) {
    const p = ring(S, st.th, st.off);
    p[1] = st.y;
    return v3.add(p, v3.mul(tangent(S, st.th), st.tan || 0));
}
/** The roll: on the path while wrapping, laid on and put away after; null before reach (it rides the hand there). */
function rollState(S, t) {
    const end = wrapEnd(S);
    if (t <= S.from) return {th: 0, off: S.D, y: turnY(S, 0)};
    if (t <= end) {
        const u = (t - S.from) / S.turn, th = 360 * u;
        return {th, off: S.D - (S.backIn || 0) * (1 - Math.cos(rad(th))) / 2, y: lerp(turnY(S, 0), turnY(S, 2), u / 3) + S.drop * (1 - Math.cos(rad(th))) / 2};
    }
    const s = smooth((t - end) / Math.max(0.05, S.tear - end));
    return {th: 1080 + 35 * s, off: S.D - 0.4 * s, y: turnY(S, 2)};
}
/** A palm on the roll, from its own side of the body (first hand: the side the roll goes to first). */
function grip(S, first, r) {
    const c = Math.cos(rad(r.th)), G = S.grip;
    return {th: r.th, off: r.off + G.out + 1.4 * (1 - Math.abs(c)), y: r.y + G.down, tan: G.along * (first ? c : -c), aimOff: r.off, aimY: r.y};
}
const pad = (S, th, y, off) => ({th, off: off === undefined ? S.padOff : off, y: y + S.padDown, tan: 0, aimOff: 0, aimY: y + S.padDown});

// ------------------------------------------------------------------ the hands' tracks (waypoints {t, st}: a state, 'carry', 'rest')
function tracks(S) {
    const F = [], Sx = [];
    const T = i => S.from + i * S.turn, TT = S.turn, end = wrapEnd(S), y0 = turnY(S, 0), y2 = turnY(S, 2);
    const [take, letGo] = S.hand || [0.47, 0.53];   // the second hand takes the roll behind, the first lets it go (of a turn)
    // the first hand brings the roll to the front of the band; the second presses the loose end on beside it
    F.push({t: 0, st: 'rest'}, {t: S.reach, st: 'carry'});
    Sx.push({t: 0, st: 'rest'}, {t: S.press - 0.12, st: pad(S, -25, y0, 3.4)}, {t: S.press, st: pad(S, -25, y0)});
    for (let i = 0; i < 3; i++) {
        const t0 = T(i), yi = lerp(y0, y2, (i + 0.85) / 3);
        // first hand: carries it round its side to the back, lets go, comes back round its side to press the front, takes it again
        F.push({t: t0 + letGo * TT, st: 'carry'}, {t: t0 + 0.78 * TT, st: pad(S, 360 * i + 25, yi)}, {t: t0 + 0.92 * TT, st: pad(S, 360 * i + 25, yi)});
        if (i < 2) F.push({t: t0 + 0.97 * TT, st: 'carry'});
        // second hand: presses the front, goes round its side to the back, takes it there and brings it to the front
        const yp = lerp(y0, y2, i / 3);
        if (i > 0) Sx.push({t: t0 + 0.08 * TT, st: pad(S, 360 * i - 25, yp)});
        Sx.push({t: t0 + 0.17 * TT, st: pad(S, 360 * i - 25, yp)}, {t: t0 + take * TT, st: 'carry'}, {t: t0 + TT, st: 'carry'});
    }
    // the end laid on by the first hand (it takes the roll from the second as the last turn closes), torn, patted twice
    F.push({t: end - 0.03 * TT, st: 'carry'}, {t: S.tear, st: 'carry'});
    const pat = 1080 + (S.patAt || 12), py = y2 - 0.3 + (S.patDy || 0), up = pad(S, pat, py, S.padOff + 0.9), down = pad(S, pat, py);
    F.push({t: S.pats[0] - 0.07, st: up}, {t: S.pats[0], st: down}, {t: S.pats[1] - 0.07, st: up}, {t: S.pats[1], st: down},
        {t: S.lower - 0.05, st: up}, {t: S.len, st: 'rest'});
    const hold = 1080 - (S.patAt || 25);
    Sx.push({t: end + 0.08 * TT, st: pad(S, hold, y2)}, {t: S.lower, st: pad(S, hold, y2)}, {t: S.len, st: 'rest'});
    return {F, S: Sx};
}

// ------------------------------------------------------------------ the body over the action
function leanOf(S, t) { const w = smooth(t / S.reach) * (1 - smooth((t - S.lower) / (S.len - S.lower))); return w; }
function bodyAt(S, name, t) {
    const w = leanOf(S, t);
    if (S.mask === 'full') {
        const a = bodyOf(REST), b = bodyOf(LEG_WORK), e = {};
        for (const n of BODY) e[n] = fileEuler(q4.slerp(a[n], q4.dot(a[n], b[n]) < 0 ? q4.neg(b[n]) : b[n], w));
        return {eulers: e, pos: v3.lerp(a.pos, b.pos, w), view: lerp(REST.view || 0, LEG_WORK.view, w), headClip: 0,
            knee: a.knee.R};
    }
    const look = LOOK[name];
    let head = look.head(t) * w;
    if (name === 'head' && t > S.from && t < wrapEnd(S)) {   // the head tipped back a little each time the roll goes behind it
        const th = 360 * (t - S.from) / S.turn;
        head = 4 - 14 * (1 - Math.cos(rad(th))) / 2;
    } else if (name === 'head') head = 4 * w;
    return {eulers: {chest: fileEuler(q4.x(rad(look.lean * w))), head: fileEuler(q4.x(rad(head)))}, pos: null, view: 0, headClip: 1};
}

// ------------------------------------------------------------------ solving one key
function solve(body, mask, hands, rolls) {
    const clips = constClip(body.eulers, body.pos);
    const props = [], hk = {right: [], left: []};
    for (const [side, h] of [['right', hands.R], ['left', hands.L]]) {
        if (!h) continue;
        props.push({id: 'd_' + side, keys: [{at: 0, frame: 'root', point: 'p', pos: h.palm, rot: [0, 0, 0], blend: 0}]});
        hk[side].push({at: 0, target: 'prop:d_' + side + ':p', blend: 0, roll: side === 'right' ? rolls.R : rolls.L, pole: h.pole});
    }
    const act = core.actionFromJson({clip: 'c', mask, hold_body: true, blend: {in: 0, out: 0}, props, hands: hk},
        id => (id.startsWith('d_') ? DUMMY : null), x => clips[x] || null);
    return core.solveFrame(poser, {action: act, seconds: 0.5, time: 0, pitch: body.view, headClip: body.headClip});
}
const bone = n => rig.find(n);
/** Model px to the root bone's frame (the dummy targets' frame: the root shifted to keep the eye over the feet). */
const inRoot = (res, p) => tf.apply(tf.inv(res.pose.world[rig.root]), p);
const palmOf = (res, right) => res.pose.point(right ? rig.handR : rig.handL, right ? [-6, 10.5, 0] : [6, 10.5, 0]);
const normalOf = (res, right) => q4.apply(res.pose.rotation(right ? rig.handR : rig.handL), right ? [1, 0, 0] : [-1, 0, 0]);
const ROLLS = [];
for (let r = -60; r <= 60; r += 10) ROLLS.push(r);

function author(name) {
    const S = PARTS[name], b = bone(S.frame), tr = tracks(S);
    const firstRight = S.dir > 0;   // the first hand: the right (head, trunk), the left on the left shin
    const keys = [], rollKeys = [], rows = [];
    let prevRoll = {R: 0, L: 0}, worst = {R: 0, L: 0, Rt: 0, Lt: 0};
    const N = Math.round(S.len / STEP);
    // what a waypoint means at t on this body
    // sat: the seat's palms (pose_lib REST: on the right knee, on the left thigh) held on their thighs as the legs move
    let seatPalm = null;
    if (S.mask === 'full') {
        const r = solve(bodyAt(S, name, 0), S.mask, {}, prevRoll), W = p => tf.apply(r.pose.world[rig.root], p);
        const kR = bodyOf(REST).knee.R;
        seatPalm = {R: tf.apply(tf.inv(r.pose.world[bone('thigh_r')]), W(v3.add(kR, [0.2, 1.4, -2.0]))),
            L: tf.apply(tf.inv(r.pose.world[bone('thigh_l')]), W([3.6, 19.5, -1.2]))};
    }
    const restPalm = (res0, body, right) => {
        if (S.mask === 'full') return inRoot(res0, res0.pose.point(bone(right ? 'thigh_r' : 'thigh_l'), right ? seatPalm.R : seatPalm.L));
        return null;   // the upper body's rest is FK identity: its palm read off the posed rest arms
    };
    function handAt(track, first, t, res0, body, restRes) {
        let k = 0;
        while (k < track.length - 1 && track[k + 1].t <= t) k++;
        const a = track[k], c = track[Math.min(k + 1, track.length - 1)];
        const right = first === firstRight;
        const toRoot = p => inRoot(res0, res0.pose.point(b, p));
        const evalWp = (wp, at) => {
            if (wp.st === 'rest') {
                const p = restPalm(res0, body, right) || inRoot(restRes, palmOf(restRes, right));
                return {root: true, palm: p, aim: v3.add(p, [right ? 1 : -1, 0, 0])};
            }
            if (wp.st === 'carry') return grip(S, first, rollAt(at));
            return wp.st;
        };
        let st;
        if (a.st === 'carry' && c.st === 'carry') st = grip(S, first, rollAt(t));
        else if (a === c || t >= c.t) st = evalWp(c, t);
        else {
            const s = smooth((t - a.t) / (c.t - a.t));
            const fa = evalWp(a, a.t), fc = evalWp(c, c.t);
            if (fa.root || fc.root) {
                const pa = fa.root ? fa : {palm: toRoot(place(S, fa)), aim: toRoot(place(S, {th: fa.th, off: fa.aimOff, y: fa.aimY}))};
                const pc = fc.root ? fc : {palm: toRoot(place(S, fc)), aim: toRoot(place(S, {th: fc.th, off: fc.aimOff, y: fc.aimY}))};
                // out in front of the body on the way (not through the hip); sat, up over the thigh (the shin is ahead already)
                const lift = S.mask === 'full' ? [0, -1.5, 0] : [0, 0, -3];
                return {palm: v3.add(v3.lerp(pa.palm, pc.palm, s), v3.mul(lift, Math.sin(Math.PI * s))), aim: v3.lerp(pa.aim, pc.aim, s)};
            }
            let th0 = fa.th;
            while (th0 - fc.th > 180) th0 -= 360;
            while (fc.th - th0 > 180) th0 += 360;
            st = {th: lerp(th0, fc.th, s), off: lerp(fa.off, fc.off, s) + S.bulge * Math.sin(Math.PI * s), y: lerp(fa.y, fc.y, s),
                tan: lerp(fa.tan || 0, fc.tan || 0, s), aimOff: lerp(fa.aimOff, fc.aimOff, s), aimY: lerp(fa.aimY, fc.aimY, s)};
        }
        if (st.root) return {palm: st.palm, aim: st.aim};
        return {palm: toRoot(place(S, st)), aim: toRoot(place(S, {th: st.th, off: st.aimOff, y: st.aimY}))};
    }
    function rollAt(t) { return rollState(S, Math.max(t, S.reach)); }
    for (let i = 0; i <= N; i++) {
        const t = r4(i * STEP);
        const body = bodyAt(S, name, t);
        const res0 = solve(body, S.mask, {}, prevRoll);
        const restRes = res0;
        const isRestFk = S.mask !== 'full' && (i === 0 || i === N);
        const hands = {};
        if (!isRestFk) for (const [first, track] of [[true, tr.F], [false, tr.S]]) {
            const right = first === firstRight;
            const h = handAt(track, first, t, res0, body, restRes);
            if (S.ground) h.palm[1] = Math.min(h.palm[1], 21.8);
            hands[right ? 'R' : 'L'] = {palm: h.palm, aim: h.aim, pole: right ? S.poleR : S.poleL};
        }
        // the hand rolls: the palm toward what it holds, kept near the last key's
        let rolls = Object.assign({}, prevRoll);
        if (!isRestFk) {
            const best = {R: null, L: null};
            const cands = i <= 1 ? ROLLS : ROLLS.filter(r => Math.abs(r - prevRoll.R) <= 40 || Math.abs(r - prevRoll.L) <= 40);
            for (const r of cands) {
                const res = solve(body, S.mask, hands, {R: r, L: r});
                const ro = core.readouts(res);
                for (const side of ['R', 'L']) {
                    if (Math.abs(r - prevRoll[side]) > 40 && i > 1) continue;
                    const right = side === 'R', h = hands[side], rr = ro[right ? 'right' : 'left'];
                    const want = v3.norm(v3.sub(h.aim, inRoot(res, palmOf(res, right))));
                    const score = v3.dot(normalOf(res, right), want) - 0.004 * Math.abs(r - prevRoll[side]) - 0.01 * Math.max(0, rr.twist - 40) - 0.01 * Math.max(0, rr.wrist - 45) - 0.5 * rr.off;
                    if (!best[side] || score > best[side].score) best[side] = {r, score};
                }
            }
            rolls = {R: best.R.r, L: best.L.r};
        }
        const res = isRestFk ? res0 : solve(body, S.mask, hands, rolls);
        prevRoll = rolls;
        const ro = core.readouts(res);
        const e = Object.assign({}, body.eulers);
        const P2 = res.pose.local;
        if (isRestFk) for (const n of ['upper_arm_r', 'forearm_r', 'hand_r', 'upper_arm_l', 'forearm_l', 'hand_l']) e[n] = [0, 0, 0];
        else Object.assign(e, {upper_arm_r: fileEuler(P2[rig.upperArmR]), forearm_r: fileEuler(P2[rig.forearmR]), hand_r: fileEuler(P2[rig.handR]),
            upper_arm_l: fileEuler(P2[rig.upperArmL]), forearm_l: fileEuler(P2[rig.forearmL]), hand_l: fileEuler(P2[rig.handL])});
        for (const side of ['right', 'left']) if (ro[side].mode === 'ik' && ro[side].off > worst[side[0].toUpperCase()]) { worst[side[0].toUpperCase()] = ro[side].off; worst[side[0].toUpperCase() + 't'] = t; }
        keys.push({t, eulers: e, pos: body.pos});
        // the roll: in the first hand before reach (in front of its palm), on the path after; hidden from the tear
        let rp, rq;
        if (t < S.reach) {
            const firstPalm = palmOf(isRestFk ? res0 : res, firstRight);
            const inv = tf.inv(res0.pose.world[b]);
            const at = tf.apply(inv, v3.add(firstPalm, [0, 0.6, -2.0]));
            const target = place(S, rollState(S, S.reach));
            rp = v3.lerp(at, target, smooth(t / S.reach));
            rq = rollTurnQ(S, 0);
        } else {
            const r = rollState(S, t);
            rp = place(S, r);
            rq = rollTurnQ(S, r.th);
        }
        const hidden = t >= S.tear;
        const prev = rollKeys.length ? rollKeys[rollKeys.length - 1] : null;
        const key = {at: r4(prev ? t - STEP : 0), frame: S.frame === 'chest' ? 'chest' : 'bone:' + S.frame, point: 'center', pos: rp.map(r4), rot: rotOf(rq), blend: prev ? STEP : 0};
        if (hidden) key.hidden = true;
        if (!(hidden && prev && prev.hidden)) rollKeys.push(key);
        if (Math.abs(t * 10 - Math.round(t * 10)) < 1e-6) rows.push(`${t.toFixed(2)} R ${ro.right.mode === 'ik' ? ro.right.off.toFixed(2) + ' r' + rolls.R + ' tw' + ro.right.twist.toFixed(0) + ' wr' + ro.right.wrist.toFixed(0) : 'fk'}  L ${ro.left.mode === 'ik' ? ro.left.off.toFixed(2) + ' r' + rolls.L + ' tw' + ro.left.twist.toFixed(0) + ' wr' + ro.left.wrist.toFixed(0) : 'fk'}`);
    }
    const clip = clipOf(keys, S.len, k => ({eulers: k.eulers, pos: k.pos, miss: ''})).clip;
    return {S, clip, rollKeys, rows, worst};
}
/** The roll's turn at th: its axis along the part (y), its middle out from it (BandageWrapRenderer: the axis point inward). */
function rollTurnQ(S, th) {
    const e = outward(S, th), cx = [0, 1, 0], cy = v3.mul(e, -1), cz = v3.cross(cx, cy);
    return q4.norm(q4.fromBasis(cx, cy, cz));
}

// ------------------------------------------------------------------ the actions
const DOC = 'docs/gameplay/medical_actions_by_part_v1.md';
const ABOUT = {
    head: 'the head (5 s, MedicalItem.BANDAGE_HEAD_TICKS 100), standing or sat (upper body, the head held to the clip: hold_head). The pad (WoundSkin\'s gauze patch) pressed on; three turns round the brow and the back of the head from 0.8 s, 1 s each (BandageWrapRenderer.wrapFrom / wrapSeconds): the right hand takes the roll round the right side to the back of the head, the left takes it there and brings it round the left side to the front while the right presses the front; the head tips back a little each time the roll passes behind it. The end laid on (3.8 s), torn off (4.0 s), patted twice, hands down from 4.45 s.',
    chest: 'the chest (6 s, MedicalItem.BANDAGE_TORSO_TICKS 120), standing or sat (upper body). The pad pressed on under the right hand; three turns round the chest under the armpits from 0.9 s, 1.4 s each: the right hand takes the roll round the right side to the back, the left takes it there (the roll a little lower behind) and brings it round the left side to the front while the right presses the front. The end laid on (5.1 s), torn off (5.3 s), patted twice, hands down from 5.65 s; the head looks down at it.',
    belly: 'the belly (6 s, MedicalItem.BANDAGE_TORSO_TICKS 120), standing or sat (upper body; the roll keyed on the pelvis). As the chest: three turns round the waist from 0.9 s, 1.4 s each, the hands passing the roll behind the back; the end laid on (5.1 s), torn off (5.3 s), patted twice, hands down from 5.65 s; leaning a little, looking down.',
    leg: 'a shin, sat on the ground (5 s, MedicalItem.BANDAGE_LEG_TICKS 100; MedicalItem sits the player down first and leaves them sat, sit/GroundSit). Keyed on the left leg, mirrored for the right. From the seat (body.sit) it leans well forward, the left knee comes up, its foot flat ahead, the right leg out to the side; three turns round the shin from 0.7 s, 1 s each, ankle to knee: the left hand takes the roll round the outside and under the shin, the right takes it there and brings it up the inside to the front while the left presses the front; the end laid on (3.7 s), torn off (3.9 s), patted twice, back to the seat from 4.4 s. The own first-person camera on the posed eye (first_person_eye).',
};
function actionOf(name, out) {
    const S = PARTS[name];
    const a = {format_version: 1,
        about: 'Bandage by part (' + DOC + '): ' + ABOUT[name] + ' The roll (player_props/bandage) keyed every ' + STEP + ' s on a rounded path ' + S.D + ' px off the part; both arms solved by IK on it and baked into the clip as FK (hold_body). Written by tools/medical-actions-v1a/author_dress_v1.cjs.',
        afl_rig: {plugin: core.VERSION, animation: 'medical.bandage_' + name}, clip: 'medical.bandage_' + name, layer: 'action', mask: S.mask, priority: 50,
        first_person: 'body', blend: {in: 0.3, out: 0.3}, first_person_pitch: S.fp};
    if (S.mask === 'full') a.first_person_eye = true;
    a.hold_body = true;
    if (S.mask !== 'full') a.hold_head = true;
    Object.assign(a, {prop: {id: 'bandage', keys: out.rollKeys}, hands: {right: [], left: []}, events: [], check: {use_ticks: Math.round(S.len * 20)}});
    return a;
}

// ------------------------------------------------------------------ checks: the real action, every 0.025 s
const BOXES = {   // rest model px
    head: [-4, 4, -8, 0, -4, 4], chest: [-4, 4, 0, 6, -2, 2], pelvis: [-4, 4, 6, 12, -2, 2],
    thigh_r: [-3.9, 0.1, 12, 18, -2, 2], shin_r: [-3.9, 0.1, 18, 22, -2, 2], foot_r: [-3.9, 0.1, 22, 24, -2, 2],
    thigh_l: [-0.1, 3.9, 12, 18, -2, 2], shin_l: [-0.1, 3.9, 18, 22, -2, 2], foot_l: [-0.1, 3.9, 22, 24, -2, 2],
};
const boxDist = (p, B) => Math.hypot(Math.max(B[0] - p[0], 0, p[0] - B[1]), Math.max(B[2] - p[1], 0, p[1] - B[3]), Math.max(B[4] - p[2], 0, p[2] - B[5]));
function check(name, out, act) {
    const S = PARTS[name];
    const near = {roll: 99, rollAt: '', palm: 99, palmAt: '', ground: 99};
    for (let t = 0.3; t <= S.tear - 0.02; t += 0.025) {
        const res = core.solveFrame(poser, {action: act, seconds: t, time: t, pitch: 0, headClip: S.mask === 'full' ? 0 : 1});
        const placed = res.props[0];
        if (!placed || res.hidden[0]) continue;
        const c = tf.apply(placed, local('center'));
        near.ground = Math.min(near.ground, 24 - c[1] - 1);
        for (const [bn, B] of Object.entries(BOXES)) {
            const q = tf.apply(tf.inv(res.pose.world[bone(bn)]), c);
            const d = boxDist(q, B) - 1 - (bn === S.frame ? S.D - 1 - 0.25 : 0);   // the roll's surface off the box (its own part: off the sleeve)
            if (d < near.roll) { near.roll = d; near.rollAt = bn + '@' + t.toFixed(2); }
            for (const right of [true, false]) {
                const pq = tf.apply(tf.inv(res.pose.world[bone(bn)]), palmOf(res, right));
                const dp = boxDist(pq, B) - 2;
                if (dp < near.palm) { near.palm = dp; near.palmAt = (right ? 'R ' : 'L ') + bn + '@' + t.toFixed(2); }
            }
        }
    }
    return near;
}
function screens(name, act) {
    const S = PARTS[name], out = [];
    for (const t of [0.4, S.from + 0.2 * S.turn, S.from + 0.5 * S.turn, S.from + 0.85 * S.turn, S.pats[0]]) {
        const res = core.solveFrame(poser, {action: act, seconds: t, time: t, pitch: S.fp, headClip: S.mask === 'full' ? 0 : 1});
        const p = rad(S.fp);
        const cam = S.mask === 'full' ? res.pose.point(rig.sockets.eye.bone, rig.sockets.eye.pos) : [0, core.eyeModelY(false), 0];
        const fwd = [0, Math.sin(p), -Math.cos(p)], up = [0, -Math.cos(p), -Math.sin(p)];
        const sc = Pm => { const rel = v3.sub(Pm, cam), f = v3.dot(rel, fwd); return `${(Math.atan2(-rel[0], f) * 180 / Math.PI).toFixed(0)}/${(Math.atan2(v3.dot(rel, up), f) * 180 / Math.PI).toFixed(0)}@${v3.len(rel).toFixed(1)}`; };
        const placed = res.props[0];
        out.push(`${t.toFixed(2)} roll ${placed && !res.hidden[0] ? sc(tf.apply(placed, local('center'))) : '-'} palmR ${sc(palmOf(res, true))} palmL ${sc(palmOf(res, false))}`);
    }
    return out;
}

const results = {};
for (const name of Object.keys(PARTS)) {
    if (ONLY && ONLY !== name) continue;
    const out = author(name);
    const action = actionOf(name, out);
    const clips = core.loadClips(rig, {animations: {['medical.bandage_' + name]: out.clip}});
    const act = core.actionFromJson(action, id => (id === 'bandage' ? prop : null), k => clips[k] || null);
    const near = check(name, out, act);
    console.log('== bandage_' + name + ' (' + out.rollKeys.length + ' roll keys)');
    if (process.env.ROWS) console.log(out.rows.join('\n'));
    console.log('IK worst off: R ' + out.worst.R.toFixed(2) + ' @' + out.worst.Rt + '  L ' + out.worst.L.toFixed(2) + ' @' + out.worst.Lt);
    console.log('nearest: roll ' + near.roll.toFixed(2) + ' ' + near.rollAt + '  palm ' + near.palm.toFixed(2) + ' ' + near.palmAt + '  roll over ground ' + near.ground.toFixed(2));
    console.log('FP (deg right/up @px): ' + screens(name, act).join(' | '));
    results[name] = {out, action};
}

if (WRITE) {
    const pretty = o => JSON.stringify(o, null, 2) + '\n';
    const clipPath = path.join(A, 'animations/player/medical.animation.json');
    const medical = L.read(clipPath);
    for (const [name, r] of Object.entries(results)) {
        medical.animations['medical.bandage_' + name] = r.out.clip;
        fs.writeFileSync(path.join(A, 'player_actions/item/bandage_' + name + '.json'), pretty(r.action));
    }
    fs.writeFileSync(clipPath, pretty(medical));
    console.log('written', Object.keys(results).join(', '));
}
module.exports = {PARTS};
