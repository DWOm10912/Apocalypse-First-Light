// Authoring sheet for being knocked down and collapsing, injury knockdown V1 (docs/gameplay/injury_knockdown_v1_plan.md 3.3,
// 4): each clip is the whole thing, the fall, a moment on the ground and getting up (or, collapsing, lying as prone.idle).
//   knock.front     shoved from behind: a stumbling step, hands and knees, sprawled on the front, then the lightning get-up
//   knock.back      hit from the front: the knees give, sat down hard, onto the back, sit up, feet in, squat, up
//   knock.side      hit from the right: onto the left side (mirrored: the right), rolled onto all fours, half kneel, up
//   knock.collapse  very low health: the knees give, kneeling, slumped, toppled onto the front; ends as prone.idle
// The body by FK, the legs by two-bone IK (hips turned with the pelvis, so a rolled body's legs reach), the palms by the AFL
// Rig core's arm IK, all baked into the clip as FK. Then the baked clips are sampled every SAMPLE s (what the game draws
// between the keys) and every box corner and limb point is checked against the ground.
//   node author_knockdown_v1.cjs [--write]   -> prints each key's IK misses and the ground checks; --write writes:
//   animations/player/knock.animation.json, player_actions/body/knock_front.json, knock_back.json, knock_side.json, collapse.json
// Java model px: y down, the ground at 24, the front -z, the body's right -x.
const L = require('./lib.cjs');
const {fs, path, core, v3, q4, rad, A, rig, poser, between, fileEuler} = L;
const WRITE = process.argv.includes('--write');
const ID = [0, 0, 0, 1], DOWN = [0, 1, 0];
const {mul, conj} = q4;
const BODY = ['pelvis', 'chest', 'head', 'thigh_r', 'shin_r', 'foot_r', 'thigh_l', 'shin_l', 'foot_l'];

// the lightning get-up's keys (lying on the front -> standing, 2.6 s), its own printout and writing left out
const argv = process.argv.slice(), log = console.log;
process.argv = process.argv.filter(a => a !== '--write');
console.log = () => {};
const {GET_UP} = require('./author_lightning_getup_v1.cjs');
console.log = log;
process.argv = argv;

// ------------------------------------------------------------------ the legs
const THIGH = 6, SHIN = 4, SIDE = {R: -1.9, L: 1.9};
function legIk(hip, ankle, hint) {
    let d = v3.sub(ankle, hip), len = v3.len(d);
    if (len > THIGH + SHIN - 0.01) { d = v3.mul(v3.norm(d), THIGH + SHIN - 0.01); len = THIGH + SHIN - 0.01; ankle = v3.add(hip, d); }
    const u = v3.norm(d);
    const a = (THIGH * THIGH - SHIN * SHIN + len * len) / (2 * len), h = Math.sqrt(Math.max(0, THIGH * THIGH - a * a));
    const n = v3.norm(v3.sub(hint, v3.mul(u, v3.dot(hint, u))));
    const knee = v3.add(hip, v3.add(v3.mul(u, a), v3.mul(n, h)));
    return {thighDir: v3.norm(v3.sub(knee, hip)), shinDir: v3.norm(v3.sub(ankle, knee)), knee, reach: len};
}
/** World turns of thigh / shin / foot; foot 'flat' (sole on the ground) or 'shin' (along the shin). */
function legWorld(thighDir, shinDir, foot) {
    const thighW = between(DOWN, thighDir);
    const shinW = mul(between(q4.apply(thighW, DOWN), shinDir), thighW);
    return {thighW, shinW, footW: foot === 'shin' ? shinW : ID};
}
/**
 * A key's body: tip (pelvis forward, deg), roll (pelvis toward the left side, deg), lean (chest on the pelvis), head (pitch,
 * + down), drop / zoff (the pelvis down / back, px); legs {ik: ankle, hint, foot}. The hips turn with the pelvis.
 */
function bodyOf(k) {
    const pelvisW = mul(q4.z(rad(k.roll || 0)), q4.x(rad(k.tip || 0)));
    const out = {pelvis: pelvisW, chest: q4.x(rad(k.lean || 0)), head: q4.x(rad(k.head || 0)), pos: [0, -(k.drop || 0), k.zoff || 0], knee: {}};
    const pivot = [0, 12 + (k.drop || 0), k.zoff || 0];
    for (const s of ['R', 'L']) {
        const spec = k['leg' + s] || {ik: [SIDE[s], 22, 0]};
        const hip = v3.add(pivot, q4.apply(pelvisW, [SIDE[s], 0, 0]));
        const dirs = legIk(hip, spec.ik, spec.hint || [0, -0.4, -1]);
        const w = legWorld(dirs.thighDir, dirs.shinDir, spec.foot || 'flat');
        out['thigh_' + s.toLowerCase()] = mul(conj(pelvisW), w.thighW);
        out['shin_' + s.toLowerCase()] = mul(conj(w.thighW), w.shinW);
        out['foot_' + s.toLowerCase()] = mul(conj(w.shinW), w.footW);
        out.knee[s] = dirs.knee;
        out['short' + s] = Math.max(0, v3.dist(hip, spec.ik) - (THIGH + SHIN - 0.01));
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
function fullKey(k) {
    const b = bodyOf(k);
    const eulers = {};
    for (const n of BODY) eulers[n] = fileEuler(b[n]);
    const clips = constClip(eulers, b.pos);
    const props = [], hands = {right: [], left: []};
    const at = p => (typeof p === 'function' ? p(b) : p);
    for (const [side, palm] of [['right', at(k.palmR)], ['left', at(k.palmL)]]) {
        if (!palm) continue;
        props.push({id: 'd_' + side, keys: [{at: 0, frame: 'root', point: 'p', pos: palm, rot: [0, 0, 0], blend: 0}]});
        hands[side].push({at: 0, target: 'prop:d_' + side + ':p', blend: 0, roll: 0, pole: side === 'right' ? (k.poleR || [-1, 0.2, 0.6]) : (k.poleL || [1, 0.2, 0.6])});
    }
    const act = core.actionFromJson({clip: 'c', mask: 'full', hold_body: true, blend: {in: 0, out: 0}, props, hands}, id => (id.startsWith('d_') ? DUMMY : null), x => clips[x] || null);
    const res = core.solveFrame(poser, {action: act, seconds: 0.5, time: 0, pitch: 0});
    const ro = core.readouts(res), P = res.pose.local;
    const miss = s => (ro[s].mode === 'ik' ? ro[s].off.toFixed(2) : '-');
    for (const [side, S] of [['right', 'R'], ['left', 'L']]) {
        const n = S.toLowerCase();
        if (k['palm' + S]) Object.assign(eulers, {['upper_arm_' + n]: fileEuler(P[rig['upperArm' + S]]), ['forearm_' + n]: fileEuler(P[rig['forearm' + S]]), ['hand_' + n]: fileEuler(P[rig['hand' + S]])});
        else Object.assign(eulers, {['upper_arm_' + n]: fileEuler(k['arm' + S] || ID), ['forearm_' + n]: fileEuler(k['fore' + S] || ID), ['hand_' + n]: [0, 0, 0]});
    }
    const legs = (b.shortR > 0.05 || b.shortL > 0.05) ? ' legs short R ' + b.shortR.toFixed(2) + ' L ' + b.shortL.toFixed(2) : '';
    return {eulers, pos: b.pos, miss: 'R ' + miss('right') + ' L ' + miss('left') + legs};
}
const tkey = t => t.toFixed(4).replace(/0+$/, '').replace(/\.$/, '.0');
function clipOf(keys, length) {
    const bones = {}, rows = [], view = [];
    for (const k of keys) {
        const s = fullKey(k);
        const t = tkey(k.t);
        for (const [n, e] of Object.entries(s.eulers)) ((bones[n] = bones[n] || {rotation: {}}).rotation[t] = e);
        ((bones.pelvis.position = bones.pelvis.position || {})[t] = s.pos);
        rows.push('  ' + k.t.toFixed(2) + ' ' + s.miss + (k.note ? '  ' + k.note : ''));
        view.push([+k.t.toFixed(4), k.view || 0]);
    }
    for (const b of Object.values(bones)) {
        const ts = Object.keys(b.rotation).sort((a, c) => a - c);
        for (let i = 1; i < ts.length; i++) for (let j = 0; j < 3; j++) {
            while (b.rotation[ts[i]][j] - b.rotation[ts[i - 1]][j] > 180) b.rotation[ts[i]][j] -= 360;
            while (b.rotation[ts[i]][j] - b.rotation[ts[i - 1]][j] < -180) b.rotation[ts[i]][j] += 360;
        }
    }
    return {clip: {animation_length: length, bones}, rows, view};
}

// ------------------------------------------------------------------ the ground check (the baked clip between the keys)
const PALM = {R: v3.add(rig.pivot[rig.upperArmR], [core.centreX(true, false), core.DISTAL_Y - core.PALM_Y, 0]),
    L: v3.add(rig.pivot[rig.upperArmL], [core.centreX(false, false), core.DISTAL_Y - core.PALM_Y, 0])};
// the torso's two halves and the head as boxes (rest px): x, y, z ranges
const BOXES = [['pelvis', [-4, 4], [6, 12], [-2, 2]], ['chest', [-4, 4], [0, 6], [-2, 2]], ['head', [-4, 4], [-8, 0], [-4, 4]]];
const LIMB_MAX = 23.75, SAMPLE = 0.05, VERBOSE = process.argv.includes('--verbose');
const TRACE = (process.argv.find(a => a.startsWith('--trace=')) || '').slice(8).split(',');
function check(clip, name) {
    const clips = core.loadClips(rig, {animations: {c: clip}});
    const act = core.actionFromJson({clip: 'c', mask: 'full', hold_body: true, blend: {in: 0, out: 0}, props: [], hands: {right: [], left: []}}, () => null, x => clips[x] || null);
    const worst = {box: -99, limb: -99, eye: -99};
    for (let t = 0; t <= clip.animation_length + 1e-6; t += SAMPLE) {
        const W = core.solveFrame(poser, {action: act, seconds: t, time: 0, pitch: 0, free: true}).pose;
        for (const [bone, xs, ys, zs] of BOXES)
            for (const x of xs) for (const y of ys) for (const z of zs) {
                const p = W.point(rig[bone], [x, y, z]);
                if (p[1] > worst.box) Object.assign(worst, {box: p[1], boxAt: bone + ' ' + t.toFixed(2) + ' corner ' + [x, y, z].join(',')});
            }
        const limbs = {elbowR: W.point(rig.forearmR, rig.pivot[rig.forearmR]), elbowL: W.point(rig.forearmL, rig.pivot[rig.forearmL]),
            palmR: W.point(rig.handR, PALM.R), palmL: W.point(rig.handL, PALM.L),
            kneeR: W.point(rig.shinR, rig.pivot[rig.shinR]), kneeL: W.point(rig.shinL, rig.pivot[rig.shinL]),
            ankleR: W.point(rig.footR, rig.pivot[rig.footR]), ankleL: W.point(rig.footL, rig.pivot[rig.footL])};
        if (VERBOSE) { const under = Object.entries(limbs).filter(([, p]) => p[1] > LIMB_MAX).map(([n, p]) => n + ' ' + p[1].toFixed(2)); if (under.length) console.log('    ' + t.toFixed(2) + ' ' + under.join(' ')); }
        if (TRACE[0] === name && t >= +TRACE[1] - 1e-6 && t <= +TRACE[2] + 1e-6) console.log('    ' + t.toFixed(2) + ' ' + ['kneeL', 'kneeR', 'ankleL', 'ankleR'].map(n => n + ' ' + limbs[n].map(x => x.toFixed(1)).join(',')).join('  ') + '  hipL ' + W.point(rig.thighL, rig.pivot[rig.thighL]).map(x => x.toFixed(1)).join(','));
        for (const [n, p] of Object.entries(limbs)) if (p[1] > worst.limb) Object.assign(worst, {limb: p[1], limbAt: n + ' ' + t.toFixed(2)});
        const eye = W.point(rig.head, rig.sockets.eye.pos);
        if (eye[1] > worst.eye) Object.assign(worst, {eye: eye[1], eyeAt: t.toFixed(2)});
    }
    return worst;
}

// ------------------------------------------------------------------ poses
const deg = d => q4.x(rad(d));
const STAND = {legR: {ik: [-1.9, 22, 0]}, legL: {ik: [1.9, 22, 0]}};
const kneel = (s, kz) => ({ik: [SIDE[s], 22, kz + SHIN], hint: [0, 1, -1], foot: 'shin'});
// kneeling sat back toward the heels: the ankles at az under the seat, the knees found ahead
const heels = (s, az) => ({ik: [SIDE[s], 22, az], hint: [0, 0.3, -1], foot: 'shin'});
// lying on the front (as prone): the legs straight back, the insteps down; on the back: straight ahead, the toes up
const lying = (z, s) => ({ik: [SIDE[s], 22.6, z + 9.6], hint: [0, 1, 0.2], foot: 'shin'});
const onBack = (z, s) => ({ik: [SIDE[s], 22.6, z - 9.6], hint: [0, -1, -0.2], foot: 'shin'});
const BEHIND = {R: [-0.6, -0.2, 1], L: [0.6, -0.2, 1]};
const FOURS = {legR: kneel('R', 0), legL: kneel('L', 0), palmR: [-5.2, 23.6, -11.2], palmL: [5.6, 23.6, -11.0], poleR: [-1, 0.6, 0.2], poleL: [1, 0.6, 0.2]};
const shift = (keys, dt) => keys.map(k => Object.assign({}, k, {t: +(k.t + dt).toFixed(4)}));

// A. shoved from behind (4.5 s): a stumbling step, hands and knees, sprawled, a breath, then the lightning get-up
const FRONT_DOWN = 1.9;
const FRONT = [
    Object.assign({t: 0, view: 0}, STAND),
    {t: 0.15, view: 15, note: 'shoved', drop: 1.5, zoff: -0.5, tip: 22, lean: 6, head: -10,
        legR: {ik: [-1.9, 22, -5.5], hint: [0, -1, -0.6]}, legL: {ik: [1.9, 22, 2.5], hint: [0, -0.4, -1]},
        armR: deg(-40), armL: deg(-35), foreR: deg(-20), foreL: deg(-20)},
    {t: 0.38, view: 40, note: 'reaching for the ground', drop: 6, zoff: 0.5, tip: 55, lean: 10, head: -30,
        legR: {ik: [-1.9, 22.4, -0.5], hint: [0, 0.3, -1]}, legL: {ik: [1.9, 22.5, 3], hint: [0, 0.3, -1]},
        palmR: [-5.0, 17.5, -15], palmL: [5.4, 18, -15], poleR: [-1, 0.6, 0.3], poleL: [1, 0.6, 0.3]},
    Object.assign({t: 0.52, view: 60, note: 'hands and knees hit', drop: 4.4, zoff: 0, tip: 72, lean: 10, head: -30}, FOURS,
        {palmR: [-5.2, 23.6, -12], palmL: [5.6, 23.6, -12]}),
    {t: 0.8, view: 30, note: 'sprawled (thump)', drop: 9, zoff: 5, tip: 90, lean: -6, head: -20,
        legR: lying(5, 'R'), legL: lying(5, 'L'), palmR: [-5.4, 23.5, -7.5], palmL: [5.4, 23.5, -7.5], poleR: [-1, -0.6, 0.4], poleL: [1, -0.6, 0.4]},
    {t: 1.1, view: 12, drop: 9, zoff: 5, tip: 90, lean: -8, head: -35,
        legR: lying(5, 'R'), legL: lying(5, 'L'), palmR: [-5.4, 23.5, -6], palmL: [5.4, 23.5, -6], poleR: [-1, -0.6, 0.4], poleL: [1, -0.6, 0.4]},
    ...shift(GET_UP, FRONT_DOWN),
];

// B. hit from the front (3.8 s): the knees give, sat down hard on the hands, onto the back; sit up, feet in, squat, up
const BACK = [
    Object.assign({t: 0, view: 0}, STAND),
    {t: 0.15, view: -10, note: 'hit', drop: 1.5, zoff: 0.8, tip: -12, lean: -8, head: 10,
        legR: {ik: [-1.9, 22, 4], hint: [0, -0.4, -1]}, legL: {ik: [1.9, 22, -1], hint: [0, -0.4, -1]},
        armR: deg(-50), armL: deg(-45), foreR: deg(-30), foreL: deg(-30)},
    // the feet come up off the ground as the seat goes down (else they swing back to front through it)
    {t: 0.27, view: -15, drop: 5.5, zoff: 1.5, tip: -18, lean: -6, head: 10,
        legR: {ik: [-2.2, 21, -2], hint: [0, -1, -0.5]}, legL: {ik: [2.2, 21.2, -3], hint: [0, -1, -0.5]},
        armR: deg(-55), armL: deg(-50), foreR: deg(-30), foreL: deg(-30)},
    {t: 0.38, view: -20, note: 'sat down hard', drop: 9.5, zoff: 2, tip: -20, lean: -5, head: 10,
        legR: {ik: [-2.4, 22, -6], hint: [0, -1, -0.3]}, legL: {ik: [2.4, 22, -6.5], hint: [0, -1, -0.3]},
        palmR: [-5.5, 23.6, 6], palmL: [5.5, 23.6, 6], poleR: BEHIND.R, poleL: BEHIND.L},
    {t: 0.62, view: -60, note: 'on the back (thump)', drop: 9, zoff: 0, tip: -88, lean: 4, head: 15,
        legR: {ik: [-2.2, 22.4, -8], hint: [0, -1, -0.2]}, legL: {ik: [2.2, 22.4, -8.4], hint: [0, -1, -0.2]},
        palmR: [-6.5, 22.5, 3], palmL: [6.5, 22.5, 3], poleR: [-1, 0, 0.3], poleL: [1, 0, 0.3]},
    {t: 0.85, view: -70, drop: 9, zoff: 0, tip: -90, lean: 0, head: 5,
        legR: onBack(0, 'R'), legL: onBack(0, 'L'), palmR: [-6.5, 22.5, 2.5], palmL: [6.5, 22.5, 2.5], poleR: [-1, 0, 0.3], poleL: [1, 0, 0.3]},
    {t: 1.8, view: -70, drop: 9, zoff: 0, tip: -90, lean: 0, head: 10,
        legR: onBack(0, 'R'), legL: onBack(0, 'L'), palmR: [-6.5, 22.5, 2.5], palmL: [6.5, 22.5, 2.5], poleR: [-1, 0, 0.3], poleL: [1, 0, 0.3]},
    {t: 2.2, view: -20, note: 'sit up', drop: 9.5, zoff: 0.5, tip: -45, lean: 25, head: 10,
        legR: {ik: [-2.4, 22, -7], hint: [0, -1, -0.3]}, legL: {ik: [2.4, 22, -7], hint: [0, -1, -0.3]},
        palmR: [-5.6, 23.6, 6], palmL: [5.6, 23.6, 6], poleR: BEHIND.R, poleL: BEHIND.L},
    {t: 2.55, view: 0, note: 'sitting, feet in', drop: 9.8, zoff: 1, tip: -5, lean: 10, head: 0,
        legR: {ik: [-2.6, 22, -4.5], hint: [0, -1, -0.3]}, legL: {ik: [2.6, 22, -4.5], hint: [0, -1, -0.3]},
        palmR: [-5.4, 23.6, 4.5], palmL: [5.4, 23.6, 4.5], poleR: BEHIND.R, poleL: BEHIND.L},
    {t: 2.95, view: 30, note: 'squat', drop: 7.5, zoff: 1.5, tip: 42, lean: 12, head: -15,
        legR: {ik: [-2.4, 22, -1], hint: [0, -1, -0.5]}, legL: {ik: [2.4, 22, -1], hint: [0, -1, -0.5]},
        palmR: [-3.5, 23.6, -6.5], palmL: [3.5, 23.6, -6.5], poleR: [-1, 0.6, 0.2], poleL: [1, 0.6, 0.2]},
    {t: 3.4, view: 10, note: 'rising', drop: 3, zoff: 0.5, tip: 20, lean: 4, head: -5,
        legR: {ik: [-1.9, 22, -0.3]}, legL: {ik: [1.9, 22, -0.3]}, armR: deg(-15), armL: deg(-12)},
    Object.assign({t: 3.8, view: 0}, STAND),
];

// C. hit from the right (3.7 s): onto the left side (the left hand first), a breath, rolled onto all fours, then as the
// get-up from all fours (half kneel, push on the knee, up)
const GET_UP_FOURS = GET_UP.findIndex(k => k.note === 'all fours');
const SIDE_FOURS = 2.4;
const SIDE_KEYS = [
    Object.assign({t: 0, view: 0}, STAND),
    {t: 0.15, view: 5, note: 'hit', roll: 10, drop: 1.5,
        legR: {ik: [-1.9, 22, 0]}, legL: {ik: [5.5, 22, 0], hint: [0.3, -0.4, -1]},
        armR: mul(q4.z(rad(28)), deg(-15)), armL: mul(q4.z(rad(-40)), deg(-20)), foreR: deg(-20), foreL: deg(-20)},
    {t: 0.38, view: 20, note: 'going down', roll: 40, drop: 5.5, tip: 5, head: -5,
        legR: {ik: [-1.5, 22, 1], hint: [0, -0.3, -1]}, legL: {ik: [4.5, 22.4, -0.5], hint: [0.5, 0.2, -1]},
        palmL: [11, 21, -3], poleL: [1, 0.3, 0.3], armR: mul(q4.z(rad(20)), deg(-30)), foreR: deg(-30)},
    // the left knee folds forward over the ground while the hip goes down (else the leg sweeps through it)
    {t: 0.47, view: 20, roll: 65, drop: 7, tip: 6, head: -8,
        legR: {ik: [-4, 19.5, 0], hint: [0, -0.2, -1], foot: 'shin'}, legL: {ik: [-3, 21.5, 0.5], hint: [0, -0.5, -1], foot: 'shin'},
        palmL: [10.5, 22.6, -4.5], poleL: [-0.3, 0.3, -1], armR: mul(q4.z(rad(20)), deg(-30)), foreR: deg(-30)},
    // on the side: the left forearm along the ground ahead of the face, its elbow forward (the chest's frame: +x is down
    // now), the right hand flat in front of the chest
    {t: 0.55, view: 20, note: 'down on the side (thump)', roll: 82, drop: 8, tip: 6, head: -10,
        legR: {ik: [-7.5, 18.5, -1.5], hint: [0, 0, -1], foot: 'shin'}, legL: {ik: [-7.5, 22.3, 1.5], hint: [0, 0.2, -1], foot: 'shin'},
        palmL: [10, 23.6, -8], palmR: [3, 23.6, -5], poleL: [-0.3, 0.3, -1], poleR: [-0.3, -1, 0.3]},
    {t: 0.75, view: 15, roll: 88, drop: 8, tip: 6, head: -12,
        legR: {ik: [-7.8, 18.3, -1.5], hint: [0, 0, -1], foot: 'shin'}, legL: {ik: [-7.8, 22.3, 1.5], hint: [0, 0.2, -1], foot: 'shin'},
        palmL: [10, 23.6, -8], palmR: [3, 23.6, -5], poleL: [-0.3, 0.3, -1], poleR: [-0.3, -1, 0.3]},
    {t: 1.7, view: 15, roll: 88, drop: 8, tip: 6, head: -16,
        legR: {ik: [-7.8, 18.3, -1.5], hint: [0, 0, -1], foot: 'shin'}, legL: {ik: [-7.8, 22.3, 1.5], hint: [0, 0.2, -1], foot: 'shin'},
        palmL: [10, 23.6, -8], palmR: [3, 23.6, -5], poleL: [-0.3, 0.3, -1], poleR: [-0.3, -1, 0.3]},
    // the knees drawn up in front first, so the lower leg is not swept through the ground by the roll
    {t: 1.88, view: 30, roll: 70, drop: 7.6, zoff: 0.5, tip: 22, head: -10,
        legR: {ik: [-3.5, 19.5, 1.5], hint: [0, -0.2, -1], foot: 'shin'}, legL: {ik: [-3, 22.4, 1.5], hint: [0, 0, -1], foot: 'shin'},
        palmL: [9, 23.6, -8], palmR: [2, 23.6, -6], poleL: [-0.3, 0.3, -1], poleR: [-0.3, -1, 0.3]},
    {t: 2.05, view: 50, note: 'rolling onto the front', roll: 45, tip: 50, drop: 6, zoff: 1, head: -5,
        legR: {ik: [-1.9, 22, 4], hint: [0, 1, -1], foot: 'shin'}, legL: {ik: [0, 22.4, 1], hint: [0, 0.2, -1], foot: 'shin'},
        palmL: [6.5, 23.6, -9], palmR: [-2, 23.6, -8], poleR: [-1, 0.6, 0.2], poleL: [1, 0.6, 0.2]},
    Object.assign({t: SIDE_FOURS, view: 55, note: 'all fours', drop: 4.6, zoff: 0, tip: 82, lean: 10, head: 12}, FOURS),
    ...shift(GET_UP.slice(GET_UP_FOURS + 1), SIDE_FOURS - GET_UP[GET_UP_FOURS].t),
];

// D. collapsing (2.0 s): the knees give, kneeling, slumped back toward the heels, toppled onto the front, settled as
// prone.idle. The server lays the player down (forced crawl pose) at the start, so ProneClient sets the body back 7 px
// over its 0.35 s ease (smoothstep): every key here is moved forward by that much so the body stays put.
const PRONE_BACK = 7, PRONE_EASE = 0.35;
const smooth = x => { x = Math.max(0, Math.min(1, x)); return x * x * (3 - 2 * x); };
function eased(k) {
    const dz = -PRONE_BACK * smooth(k.t / PRONE_EASE);
    const o = Object.assign({}, k, {zoff: (k.zoff || 0) + dz});
    for (const s of ['R', 'L']) {
        const leg = k['leg' + s] || {ik: [SIDE[s], 22, 0]};
        o['leg' + s] = Object.assign({}, leg, {ik: [leg.ik[0], leg.ik[1], leg.ik[2] + dz]});
        const p = k['palm' + s];
        // a palm given as a function follows the (moved) knees already
        if (Array.isArray(p)) o['palm' + s] = [p[0], p[1], p[2] + dz];
    }
    return o;
}
const COLLAPSE = [
    Object.assign({t: 0, view: 0}, STAND),
    {t: 0.12, view: 15, note: 'knees give', drop: 2.5, tip: 8, head: 15, legR: {ik: [-1.9, 22, -0.6]}, legL: {ik: [1.9, 22, -0.6]}},
    {t: 0.35, view: 25, drop: 6, zoff: 0.5, tip: 12, lean: 6, head: 25,
        legR: {ik: [-1.9, 22, -0.4], hint: [0, -0.2, -1]}, legL: {ik: [1.9, 22, -0.4], hint: [0, -0.2, -1]}, armR: deg(-8), armL: deg(-6)},
    {t: 0.5, view: 35, note: 'on the knees', drop: 4.6, zoff: 0.5, tip: 4, lean: 10, head: 30,
        legR: kneel('R', 0), legL: kneel('L', 0), armR: deg(-8), armL: deg(-8)},
    // sat back toward the heels: the knees stay down ahead, the shins flat back under the seat
    {t: 0.8, view: 40, note: 'slumped', drop: 6.5, zoff: 2.5, tip: 15, lean: 22, head: 35,
        legR: heels('R', 1.5), legL: heels('L', 1.5), armR: deg(-5), armL: deg(-5)},
    {t: 1.1, view: 55, note: 'toppling', drop: 6.5, zoff: 3, tip: 55, lean: 15, head: 20,
        legR: heels('R', 2.5), legL: heels('L', 2.5), palmR: [-5, 21, -11], palmL: [5.4, 21.5, -11.5], poleR: [-1, 0.6, 0.3], poleL: [1, 0.6, 0.3]},
    // the hips up over the knees a moment (the hands catch, then give), so the knees slide back on the ground, not through it
    {t: 1.22, view: 45, drop: 5.5, zoff: 2, tip: 70, lean: 4, head: 5,
        legR: {ik: [SIDE.R, 22, 9], hint: [0, 1, 0], foot: 'shin'}, legL: {ik: [SIDE.L, 22, 9], hint: [0, 1, 0], foot: 'shin'},
        palmR: [-5.2, 22.6, -9.5], palmL: [5.4, 22.8, -10], poleR: [-1, 0.6, 0.3], poleL: [1, 0.6, 0.3]},
    {t: 1.35, view: 30, note: 'chest hits (thump)', drop: 8.5, zoff: 4, tip: 85, lean: -4, head: -10,
        legR: lying(4, 'R'), legL: lying(4, 'L'), palmR: [-5.4, 23.4, -8], palmL: [5.4, 23.4, -8], poleR: [-1, 0.5, 0.4], poleL: [1, 0.5, 0.4]},
    {t: 1.6, view: 15, drop: 9, zoff: 5, tip: 90, lean: -10, head: -20,
        legR: lying(5, 'R'), legL: lying(5, 'L'), palmR: [-3.5, 22.4, -9.5], palmL: [3.6, 22.4, -9.8], poleR: [-1, 0.5, 0.4], poleL: [1, 0.5, 0.4]},
    // prone.idle (author_prone_v1.cjs idle(0, 0)), the head lifted a little toward the look it hands over to
    {t: 2.0, view: 10, note: 'prone.idle', drop: 9, zoff: 5, tip: 90, lean: -14, head: -20,
        legR: lying(5, 'R'), legL: lying(5, 'L'), palmR: [-2.6, 22.1, -10.4], palmL: [2.7, 22.1, -10.9], poleR: [-1, 0.5, 0.4], poleL: [1, 0.5, 0.4]},
].map(eased);

// ------------------------------------------------------------------ build, check, write
const CLIPS = {
    front: {keys: FRONT, length: FRONT_DOWN + 2.6, thump: 0.8, about: 'shoved from behind: a stumbling step, both hands and the knees hit at 0.52 s, sprawled on the front at 0.8 s (the thump), a breath, then the lightning get-up (push up, knees under, all fours, half kneel, up)'},
    back: {keys: BACK, length: 3.8, thump: 0.62, about: 'hit from the front: the knees give, sat down hard onto both hands, onto the back at 0.62 s (the thump), a breath, sit up, feet in, a squat with the hands on the ground, up'},
    side: {keys: SIDE_KEYS, length: SIDE_FOURS + 1.3, thump: 0.55, about: 'hit from the right: a step out, onto the LEFT side, the left hand first, down at 0.55 s (the thump; mirrored when hit from the left), a breath, rolled onto all fours, half kneel, push on the knee, up'},
    collapse: {keys: COLLAPSE, length: 2.0, thump: 1.35, about: 'collapsing at very low health: the knees give, kneeling, slumped back, toppled onto the front, the chest hits at 1.35 s (the thump), settled as prone.idle; every key moved forward by ProneClient\'s 7 px set-back over its 0.35 s ease (the server lays the player down at the start)'},
};
const DOC = 'docs/gameplay/injury_knockdown_v1_plan.md';
const animations = {}, actions = {};
let bad = false;
for (const [name, c] of Object.entries(CLIPS)) {
    const built = clipOf(c.keys, c.length);
    const w = check(built.clip, name);
    const f = x => x.toFixed(2);
    console.log('knock.' + name + ' ' + c.length + ' s (' + Math.round(c.length * 20) + ' ticks), thump ' + c.thump + ' s (' + Math.round(c.thump * 20) + ' ticks)');
    console.log(built.rows.join('\n'));
    console.log('  lowest: box corner y ' + f(w.box) + ' (' + w.boxAt + '), limb point y ' + f(w.limb) + ' (' + w.limbAt + '), eye y ' + f(w.eye) + ' (' + w.eyeAt + ')');
    // a box corner at most 0.4 px into the ground; a limb point (4 px thick: elbows, knees, ankles, palms) at most LIMB_MAX:
    // the approved prone and get-up clips plant palms at 23.6 and lie with the knees at 23.1
    if (w.box > 24.4 || w.limb > LIMB_MAX || w.eye > 23) { bad = true; console.log('  ** under the ground'); }
    animations['knock.' + name] = built.clip;
    const id = name === 'collapse' ? 'collapse' : 'knock_' + name;
    actions[id] = {format_version: 1,
        about: 'Injury knockdown V1 (' + DOC + '): ' + c.about + '. ' + c.length + ' s (health/FallLanding). The whole body (mask full), held (hold_body: arms keyed by FK); the own first person draws the body, the camera on the posed eye, the view following first_person_view. No moving or using items meanwhile (client/skeleton/FallLandingClient). Written by tools/medical-actions-v1a/author_knockdown_v1.cjs.',
        afl_rig: {plugin: core.VERSION, animation: 'knock.' + name}, clip: 'knock.' + name, layer: 'action', mask: 'full', priority: 70,
        first_person: 'body', blend: {in: 0.06, out: 0.3}, first_person_eye: true, hold_body: true, first_person_view: built.view,
        hands: {right: [], left: []},
        events: [{at: c.thump, kind: 'sound', id: 'apocalypse_firstlight:body_fall', about: 'played by the server (health/FallLanding)'}]};
}
if (bad) console.log('NOT OK: a clip goes under the ground');

if (WRITE) {
    const pretty = o => JSON.stringify(o, null, 2) + '\n';
    fs.writeFileSync(path.join(A, 'animations/player/knock.animation.json'), pretty({format_version: '1.8.0',
        afl_rig: {about: 'Written by tools/medical-actions-v1a/author_knockdown_v1.cjs (knocked down and collapsing, ' + DOC + '); check in Blockbench with the AFL Rig plugin.'},
        animations}));
    for (const [id, a] of Object.entries(actions)) fs.writeFileSync(path.join(A, 'player_actions/body/' + id + '.json'), pretty(a));
    console.log('written');
}
