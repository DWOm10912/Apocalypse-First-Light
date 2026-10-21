// Authoring sheet for lying on the front, lightning injury round 2 (docs/gameplay/lightning_injury_v1_plan.md 10.2, user
// 2026-10-21 "按推荐做"): the skeleton's clips for any player lying on the ground (the crawl pose out of the water).
//   prone.idle   lying awake and still: the forearms flat under the chest, breathing
//   prone.drag   paralysed legs: the forearms in turn reach ahead and pull the body, the legs dragged behind
//   prone.crawl  the ordinary crawl (a one-block gap): the same arms, the same side's knee drawn up and pushing
//   prone.out    unconscious: flat, the face turned to the side on the ground, the arms where they fell, faint breathing
// The moving clips loop over one stride cycle of two pulls (right, then left). The body moves in pulses: little while an
// arm reaches, most while it pulls (the speed profile below, the same numbers as skeleton/ProneCrawl), and a pulling palm
// keeps its place on the ground (its model z moves back by exactly the distance the body went).
//   node author_prone_v1.cjs [--write]   -> prints each key's IK misses and the checks; --write writes
//   animations/player/prone.animation.json
// Java model px: y down, the ground at 24, the front -z, the body's right -x.
const L = require('./lib.cjs');
const {fs, path, core, v3, q4, rad, A, rig, poser, between, fileEuler} = L;
const WRITE = process.argv.includes('--write');
const ID = [0, 0, 0, 1], DOWN = [0, 1, 0];
const {mul, conj} = q4;
const BODY = ['pelvis', 'chest', 'head', 'thigh_r', 'shin_r', 'foot_r', 'thigh_l', 'shin_l', 'foot_l'];

// ------------------------------------------------------------------ the stride (skeleton/ProneCrawl: keep in step)
/** Share of each half cycle an arm reaches (the rest it pulls); the speed while reaching (1 = the pull's peak). */
const REACH = 0.35, SLOW = 0.2;
/** One cycle (two pulls) covers this many blocks; the clip file's length for one cycle (s, the phase × this). */
const CYCLE_BLOCKS = 1.2, CYCLE_SECONDS = 2.0;
const MEAN = SLOW + (1 - SLOW) * (1 - REACH) / 2;
/** The speed (0..1 of the peak) at phase u. */
function speed(u) {
    const v = ((u % 0.5) + 0.5) % 0.5 / 0.5;
    if (v < REACH) return SLOW;
    const s = Math.sin(Math.PI * (v - REACH) / (1 - REACH));
    return SLOW + (1 - SLOW) * s * s;
}
/** The share of a cycle's distance covered from phase 0 to u (0..1 over a cycle, growing past it). */
function covered(u) {
    const half = Math.floor(u / 0.5), v = (u - half * 0.5) / 0.5;
    const g = v < REACH ? 0 : (v - REACH) / 2 - (1 - REACH) / (4 * Math.PI) * Math.sin(2 * Math.PI * (v - REACH) / (1 - REACH));
    return (half + (SLOW * v + (1 - SLOW) * g) / MEAN) / 2;
}
const CYCLE_PX = CYCLE_BLOCKS * 16;
const D = u => CYCLE_PX * covered(u);

// ------------------------------------------------------------------ the legs (as author_lightning_getup_v1.cjs)
const THIGH = 6, SHIN = 4, SIDE = {R: -1.9, L: 1.9};
function legIk(hip, ankle, hint) {
    let d = v3.sub(ankle, hip), len = v3.len(d);
    if (len > THIGH + SHIN - 0.01) { d = v3.mul(v3.norm(d), THIGH + SHIN - 0.01); len = THIGH + SHIN - 0.01; ankle = v3.add(hip, d); }
    const u = v3.norm(d);
    const a = (THIGH * THIGH - SHIN * SHIN + len * len) / (2 * len), h = Math.sqrt(Math.max(0, THIGH * THIGH - a * a));
    const n = v3.norm(v3.sub(hint, v3.mul(u, v3.dot(hint, u))));
    const knee = v3.add(hip, v3.add(v3.mul(u, a), v3.mul(n, h)));
    return {thighDir: v3.norm(v3.sub(knee, hip)), shinDir: v3.norm(v3.sub(ankle, knee)), knee};
}
function legWorld(thighDir, shinDir, foot) {
    const thighW = between(DOWN, thighDir);
    const shinW = mul(between(q4.apply(thighW, DOWN), shinDir), thighW);
    return {thighW, shinW, footW: foot === 'shin' ? shinW : ID};
}
/** A key's body: tip, roll, lean, head (pitch, + down) and headYaw (deg); drop / zoff (px); legs {ik, hint, foot}. */
function bodyOf(k) {
    const pelvisW = mul(q4.z(rad(k.roll || 0)), q4.x(rad(k.tip || 0)));
    const out = {pelvis: pelvisW, chest: q4.x(rad(k.lean || 0)), head: mul(q4.x(rad(k.head || 0)), q4.y(rad(k.headYaw || 0))),
        pos: [0, -(k.drop || 0), k.zoff || 0], knee: {}};
    const hipY = 12 + (k.drop || 0), hipZ = k.zoff || 0;
    for (const s of ['R', 'L']) {
        const spec = k['leg' + s];
        const hip = [SIDE[s], hipY, hipZ];
        const dirs = legIk(hip, spec.ik, spec.hint || [0, -0.4, -1]);
        const w = legWorld(dirs.thighDir, dirs.shinDir, spec.foot || 'flat');
        out['thigh_' + s.toLowerCase()] = mul(conj(pelvisW), w.thighW);
        out['shin_' + s.toLowerCase()] = mul(conj(w.thighW), w.shinW);
        out['foot_' + s.toLowerCase()] = mul(conj(w.shinW), w.footW);
        out.knee[s] = dirs.knee;
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
/** The palm on the posed hand (the IK's palm point, rest: the upper arm's pivot + centre, DISTAL_Y - PALM_Y), the
 *  elbows, the hands' finger direction, and the body's points to check. */
const PALM = {R: v3.add(rig.pivot[rig.upperArmR], [core.centreX(true, false), core.DISTAL_Y - core.PALM_Y, 0]),
    L: v3.add(rig.pivot[rig.upperArmL], [core.centreX(false, false), core.DISTAL_Y - core.PALM_Y, 0])};
function readout(W) {
    const hc = W.point(rig.head, [0, -4, 0]), cc = W.point(rig.chest, [0, 3, 0]), pc = W.point(rig.pelvis, [0, 9, 0]);
    const fingers = (h, p) => v3.norm(v3.sub(W.point(h, v3.add(p, [0, 1, 0])), W.point(h, p)));
    return {head: hc, neck: W.point(rig.head, rig.pivot[rig.head]), chest: cc, pelvis: pc,
        palmR: W.point(rig.handR, PALM.R), palmL: W.point(rig.handL, PALM.L), eye: W.point(rig.head, rig.sockets.eye.pos),
        shoulderR: W.point(rig.upperArmR, rig.pivot[rig.upperArmR]), elbowR: W.point(rig.forearmR, rig.pivot[rig.forearmR]), elbowL: W.point(rig.forearmL, rig.pivot[rig.forearmL]),
        handR: fingers(rig.handR, PALM.R), handL: fingers(rig.handL, PALM.L)};
}
function fullKey(k) {
    const b = bodyOf(k);
    const eulers = {};
    for (const n of BODY) eulers[n] = fileEuler(b[n]);
    const clips = constClip(eulers, b.pos);
    const props = [], hands = {right: [], left: []};
    for (const [side, palm] of [['right', k.palmR], ['left', k.palmL]]) {
        props.push({id: 'd_' + side, keys: [{at: 0, frame: 'root', point: 'p', pos: palm, rot: [0, 0, 0], blend: 0}]});
        hands[side].push({at: 0, target: 'prop:d_' + side + ':p', blend: 0, roll: 0, pole: side === 'right' ? k.poleR : k.poleL});
    }
    const act = core.actionFromJson({clip: 'c', mask: 'full', hold_body: true, blend: {in: 0, out: 0}, props, hands}, id => (id.startsWith('d_') ? DUMMY : null), x => clips[x] || null);
    const res = core.solveFrame(poser, {action: act, seconds: 0.5, time: 0, pitch: 0, free: true, headClip: k.headClip || 0});
    const ro = core.readouts(res), P = res.pose.local;
    for (const S of ['R', 'L']) {
        const n = S.toLowerCase();
        Object.assign(eulers, {['upper_arm_' + n]: fileEuler(P[rig['upperArm' + S]]), ['forearm_' + n]: fileEuler(P[rig['forearm' + S]]), ['hand_' + n]: fileEuler(P[rig['hand' + S]])});
    }
    return {eulers, pos: b.pos, miss: [ro.right.off, ro.left.off], at: readout(res.pose)};
}
const tkey = t => t.toFixed(4).replace(/0+$/, '').replace(/\.$/, '.0');
function clipOf(keys, length) {
    const bones = {}, solved = [];
    for (const k of keys) {
        const s = fullKey(k);
        const t = tkey(k.t);
        for (const [n, e] of Object.entries(s.eulers)) ((bones[n] = bones[n] || {rotation: {}}).rotation[t] = e);
        ((bones.pelvis.position = bones.pelvis.position || {})[t] = s.pos);
        solved.push({k, s});
    }
    for (const b of Object.values(bones)) {
        const ts = Object.keys(b.rotation).sort((a, c) => a - c);
        for (let i = 1; i < ts.length; i++) for (let j = 0; j < 3; j++) {
            while (b.rotation[ts[i]][j] - b.rotation[ts[i - 1]][j] > 180) b.rotation[ts[i]][j] -= 360;
            while (b.rotation[ts[i]][j] - b.rotation[ts[i - 1]][j] < -180) b.rotation[ts[i]][j] += 360;
        }
    }
    return {clip: {animation_length: length, loop: true, bones}, solved};
}
/** The baked clip's pose at t (no IK): what the game draws between the keys. */
function sample(clip, t, headClip) {
    const clips = core.loadClips(rig, {animations: {c: clip}});
    const act = core.actionFromJson({clip: 'c', mask: 'full', hold_body: true, blend: {in: 0, out: 0}, props: [], hands: {right: [], left: []}}, () => null, x => clips[x] || null);
    return readout(core.solveFrame(poser, {action: act, seconds: t, time: 0, pitch: 0, free: true, headClip: headClip || 0}).pose);
}

// ------------------------------------------------------------------ poses
const smooth = x => { x = Math.max(0, Math.min(1, x)); return x * x * (3 - 2 * x); };
const lerp = (a, b, t) => a + (b - a) * t;
const lying = (z, s) => ({ik: [SIDE[s], 22.6, z + 9.6], hint: [0, 1, 0.2], foot: 'shin'});
const BASE = {drop: 9, zoff: 5, tip: 90};
// the arms' cycle: a palm goes down ahead at FRONT_Z and is pulled back to about BACK_Z (where the drift leaves it), lifted
// a little while the other arm pulls, then reaches up and ahead again
const BACK_Z = -2.5, PALM_Y = 22.1, LIFT = 0.7, REACH_UP = 1.2, PALM_X = 6;
const planted = {start: 0.5 * REACH, end: 0.5 + 0.5 * REACH};   // right arm, phase; the left one half a cycle later
const FRONT_Z = BACK_Z - (D(planted.end) - D(planted.start));
/** One arm's palm at phase u (right: side 'R'; the left runs half a cycle later, mirrored). */
function palm(u, s) {
    const w = s === 'R' ? u : (u + 0.5) % 1, x = s === 'R' ? -PALM_X : PALM_X;
    if (w >= planted.start && w <= planted.end) return {at: [x, PALM_Y, FRONT_Z + D(w) - D(planted.start)], down: true};
    if (w > planted.end) {
        // the other arm pulls: this one lies light on the ground, carried along
        const t = smooth((w - planted.end) / 0.08);
        return {at: [x, PALM_Y - LIFT * t, BACK_Z], down: false};
    }
    // reaching: out round the shoulder, a little off the ground, and ahead, down at FRONT_Z as the pull starts
    const t = w / planted.start, e = smooth(t);
    return {at: [x * (1 + 0.45 * Math.sin(Math.PI * t)), PALM_Y - LIFT * (1 - e) - REACH_UP * Math.sin(Math.PI * t), lerp(BACK_Z, FRONT_Z, e)], down: false};
}
/** How far through its pull the half cycle's arm is (0 reaching, rising to 1 at the pull's middle and back). */
function effort(u) {
    const v = (u % 0.5) / 0.5;
    return v < REACH ? 0 : Math.sin(Math.PI * (v - REACH) / (1 - REACH));
}
/** The elbow's pole (the chest's frame: +y toward the feet, -z toward the ground) for an arm at its own phase w: down
 *  on the ground while the forearm lies ahead, swinging out and back as the pull folds the arm beside the chest. */
const POLE_AHEAD = [0.3, -0.3], POLE_BACK = [0.8, -0.1];
const POLE_LIFT = [-0.3, 0.5];
function POLE(w, x) {
    const mix = (a, b, t) => [x, lerp(a[0], b[0], smooth(t)), lerp(a[1], b[1], smooth(t))];
    if (w < planted.start) {
        // reaching: the elbow comes up off the ground first, then goes down ahead
        const t = w / planted.start;
        return t < 0.3 ? mix(POLE_BACK, POLE_LIFT, t / 0.3) : mix(POLE_LIFT, POLE_AHEAD, (t - 0.3) / 0.7);
    }
    return w <= planted.end ? mix(POLE_AHEAD, POLE_BACK, (w - planted.start) / (planted.end - planted.start)) : mix(POLE_BACK, POLE_BACK, 0);
}
function moving(u, crawl) {
    const right = u < 0.5, v = (u % 0.5) / 0.5, e = effort(u);
    const reachRoll = v < REACH ? Math.sin(Math.PI * v / REACH) : 0;
    const k = Object.assign({}, BASE, {
        t: u * CYCLE_SECONDS,
        // the chest comes up off the ground with the pull and settles at its end (the camera's dip); the reaching
        // shoulder lifts the body a little onto the other side
        lean: -11 - 6 * e + 1.5 * reachRoll, drop: 9 - 0.25 * e, roll: (right ? -1 : 1) * 4 * reachRoll,
        palmR: palm(u, 'R').at, palmL: palm(u, 'L').at,
        poleR: POLE(u, -1), poleL: POLE((u + 0.5) % 1, 1),
    });
    for (const s of ['R', 'L']) {
        // dragged: the feet sway a little with the body; crawling: the pulling arm's knee draws up while it reaches and
        // pushes while it pulls
        const mine = (s === 'R') === right;
        if (!crawl || !mine) {
            k['leg' + s] = lying(5, s);
            k['leg' + s].ik[0] += (right ? 1 : -1) * 0.5 * reachRoll;
            continue;
        }
        const draw = v < REACH ? smooth(v / REACH) : 1 - smooth((v - REACH) / (1 - REACH));
        const out = s === 'R' ? -1 : 1;
        k['leg' + s] = {ik: [SIDE[s] + out * 3.2 * draw, 22.6, 5 + 9.6 - 5.4 * draw], hint: [out * draw, 1 - 0.6 * draw, 0.2], foot: 'shin'};
    }
    return k;
}
const STEPS = 40;
const cycle = crawl => Array.from({length: STEPS + 1}, (_, i) => moving(i / STEPS, crawl));

const IDLE_LEN = 4, OUT_LEN = 5, IP = 0.4;
const idle = (t, breath) => Object.assign({}, BASE, {t, lean: -14 - 1.2 * breath, drop: 9,
    legR: lying(5, 'R'), legL: lying(5, 'L'),
    palmR: [-2.6, 22.1, -10.4], palmL: [2.7, 22.1, -10.9], poleR: [-1, 0.5, IP], poleL: [1, 0.5, IP]});
const IDLE = [idle(0, 0), idle(1.6, 1), idle(IDLE_LEN, 0)];
// out cold: flat, the head turned right onto its cheek, the right arm down by the hip, the left one up beside the head,
// the left knee a little out
const out = (t, breath) => Object.assign({}, BASE, {t, lean: -10 - 0.5 * breath, drop: 9.1, roll: 3, headYaw: -78, head: 6, headClip: 1,
    legR: lying(5, 'R'), legL: {ik: [1.9 + 1.6, 22.6, 5 + 8.6], hint: [1, 0.8, 0.2], foot: 'shin'},
    palmR: [-6.6, 22.1, 3.2], palmL: [6.4, 22.1, -10.6], poleR: [-1, 0.2, 0.8], poleL: [1, 0.3, 0.2]});
const OUT = [out(0, 0), out(2.2, 1), out(OUT_LEN, 0)];

// ------------------------------------------------------------------ build and check
const built = {idle: clipOf(IDLE, IDLE_LEN), drag: clipOf(cycle(false), CYCLE_SECONDS), crawl: clipOf(cycle(true), CYCLE_SECONDS), out: clipOf(OUT, OUT_LEN)};
const f = x => x.toFixed(2);
let worst = {miss: 0, head: -99, chest: -99, pelvis: -99, limb: -99, slide: 0};
console.log('stride: reach ' + REACH + ', slow ' + SLOW + ', mean ' + MEAN.toFixed(4) + ', cycle ' + CYCLE_BLOCKS + ' blocks; a pull moves ' + f(BACK_Z - FRONT_Z) + ' px (front ' + FRONT_Z + ', back ' + f(BACK_Z) + ')');
for (const [name, b] of Object.entries(built)) {
    const rows = [];
    for (const {k, s} of b.solved) {
        const a = s.at;
        if (Math.max(s.miss[0], s.miss[1]) > worst.miss) worst.missAt = name + ' ' + k.t.toFixed(3);
        worst.miss = Math.max(worst.miss, s.miss[0], s.miss[1]);
        // the head's middle at most 4 px over the ground (an 8 px head), the torso's 2 px (a 4 px torso), with the head
        // turned the view's way (idle, drag, crawl) only the neck is checked
        worst.head = Math.max(worst.head, (name === 'out' ? a.head[1] : a.neck[1]) - 20);
        worst.chest = Math.max(worst.chest, a.chest[1] - 22);
        worst.pelvis = Math.max(worst.pelvis, a.pelvis[1] - 22);
        // the arms are 4 px thick: elbows and palms (the hand's middle line) at most 2 px over the ground
        const limb = Math.max(a.elbowR[1], a.elbowL[1], a.palmR[1], a.palmL[1]) - 22.05;
        if (limb > worst.limb) worst.limbAt = name + ' ' + k.t.toFixed(3);
        worst.limb = Math.max(worst.limb, limb);
        if (b.solved.length <= 5 || Math.round(k.t / CYCLE_SECONDS * STEPS) % 5 === 0)
            rows.push('  ' + k.t.toFixed(2) + ' miss R ' + f(s.miss[0]) + ' L ' + f(s.miss[1]) + ' neck y ' + f(a.neck[1]) + ' head y ' + f(a.head[1])
                + ' chest y ' + f(a.chest[1]) + ' pelvis y ' + f(a.pelvis[1]) + ' eye ' + a.eye.map(f).join(','));
    }
    console.log(name + '\n' + rows.join('\n'));
}
// a pulling palm between the keys: its middle against where it went down plus the distance the body has gone since
for (const name of ['drag', 'crawl']) {
    const clip = built[name].clip;
    for (const s of ['R', 'L']) {
        const start = s === 'R' ? planted.start : planted.start + 0.5, end = s === 'R' ? planted.end : planted.end + 0.5;
        const p0 = sample(clip, (start % 1) * CYCLE_SECONDS)['palm' + s];
        for (let w = start; w <= end + 1e-9; w += 0.005) {
            const p = sample(clip, (w % 1) * CYCLE_SECONDS)['palm' + s];
            const want = [p0[0], p0[1], p0[2] + D(w) - D(start)];
            if (v3.dist(p, want) > worst.slide) worst.slideAt = name + ' ' + s + ' phase ' + w.toFixed(3) + ' got ' + p.map(f).join(',') + ' want ' + want.map(f).join(',');
            worst.slide = Math.max(worst.slide, v3.dist(p, want));
        }
    }
}
console.log('worst: IK miss ' + f(worst.miss) + ' px; over the ground: head ' + f(worst.head) + ' chest ' + f(worst.chest) + ' pelvis ' + f(worst.pelvis)
    + ' elbows/palms ' + f(worst.limb) + ' px (<= 0 is above); a pulling palm slides ' + f(worst.slide) + ' px');
console.log('  worst miss at ' + worst.missAt + '; elbows/palms at ' + worst.limbAt + '; worst slide at ' + worst.slideAt);
const ARM = (process.argv.find(a => a.startsWith('--arm')) || '').split('=')[1] || 'drag';
if (process.argv.some(a => a.startsWith('--arm'))) for (const {k, s} of built[ARM].solved)
    console.log('  ' + (k.t / CYCLE_SECONDS).toFixed(3) + ' L miss ' + f(s.miss[1]) + ' elbowL ' + s.at.elbowL.map(f).join(',') + ' palmL ' + s.at.palmL.map(f).join(',') + ' R miss ' + f(s.miss[0]) + ' palm ' + s.at.palmR.map(f).join(',')
        + ' shoulder ' + s.at.shoulderR.map(f).join(',') + ' elbow ' + s.at.elbowR.map(f).join(',') + ' fingers ' + s.at.handR.map(f).join(','));

if (WRITE) {
    const DOC = 'docs/gameplay/lightning_injury_v1_plan.md';
    const animations = {};
    for (const [name, b] of Object.entries(built)) animations['prone.' + name] = b.clip;
    fs.writeFileSync(path.join(A, 'animations/player/prone.animation.json'), JSON.stringify({format_version: '1.8.0',
        afl_rig: {about: 'Written by tools/medical-actions-v1a/author_prone_v1.cjs (lying on the front, ' + DOC + ' 10.2): prone.drag and prone.crawl loop over one stride of ' + CYCLE_BLOCKS + ' blocks (skeleton/ProneCrawl), prone.idle and prone.out by time. Check in Blockbench with the AFL Rig plugin.'},
        animations}, null, 1) + '\n');
    console.log('written');
}
module.exports = {REACH, SLOW, CYCLE_BLOCKS, CYCLE_SECONDS, MEAN, speed, covered};
