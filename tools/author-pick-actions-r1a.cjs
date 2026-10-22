// Rock R1a (docs/gameplay/rock_r1_stones_boulders_design.md 11.2): writes the pick mattock's skeleton actions and clip,
// then checks them with the AFL Rig core (tools/blockbench/afl_rig.js) as the game poses them.
//   player_actions/item/pick_mattock.json   the hold (two hands, the handle up across the body, the head at the right shoulder)
//   player_actions/tool/pick_strike_low.json   strike at a rock point below the waist, 1.1 s (boulder/BoulderStrike
//                                              DURATION 22 ticks, the hit at 12 = 0.6 s)
//   player_actions/tool/pick_strike.json       the same from the waist to the belt
//   player_actions/tool/pick_strike_high.json  the same from the belt to the chest
//   animations/player/tool.animation.json      tool.pick_strike_low / _strike / _strike_high (the body: hips, knees, chest)
// The pick's tip strikes the "strike" anchor: the rock point client/skeleton/SkeletonActions#pickAnchor gives, SIDE to the
// right, clamped to the band of the strike it picks by the point's height (STRIKES near/far/low/high, SkeletonActions
// PICK_*). The pick never passes through the head or the torso (the boxes below).
// The wind-up stays out to the right at the shoulder, not over the head: the arms are too short to cross over it and too
// thick to pass the eye in the own first person (as the crowbar's smash). The check samples every 0.05 s over each band
// (its corners and middles), at the view pitch of the look at the rock point +- 10 deg (the hold at -10..65): each palm
// on its handle point (off <= 1.25 px once the hand is fully on it), wrist <= 50 deg, forearm twist <= 60 deg, the pick
// no more than 0.4 px into the head or the torso box.
// Run: node tools/author-pick-actions-r1a.cjs (write + check) | --check (check the files as they are, fail if stale)
const fs = require('fs'), path = require('path');
const root = path.resolve(__dirname, '..');
const core = require(path.join(root, 'tools/blockbench/afl_rig.js'));
const {q4, v3} = core;
const A = path.join(root, 'src/main/resources/assets/apocalypse_firstlight');
const read = p => JSON.parse(fs.readFileSync(p, 'utf8'));
const PX = 16 / 0.9375;
const CHECK = process.argv.includes('--check');

// ------------------------------------------------------------------ the reach (SkeletonActions PICK_*; blocks)
const SIDE = 0.1;

// ------------------------------------------------------------------ the body, as tools/author-shovel-actions-a2.cjs
function body([a, p, c]) {
    const r = Math.PI / 180, b = Math.asin(6 * Math.sin(a * r) / 4) / r;
    const drop = 10 - 6 * Math.cos(a * r) - 4 * Math.cos(b * r);
    const f = v => +v.toFixed(3);
    return {pelvis: {rotation: [p, 0, 0], position: [0, f(-drop), 0]}, chest: {rotation: [c, 0, 0]},
        thigh_r: {rotation: [f(-a - p), 0, 0]}, shin_r: {rotation: [f(a + b), 0, 0]}, foot_r: {rotation: [f(-b), 0, 0]},
        thigh_l: {rotation: [f(-a - p), 0, 0]}, shin_l: {rotation: [f(a + b), 0, 0]}, foot_l: {rotation: [f(-b), 0, 0]}};
}

function clip(length, keys) {
    const bones = {};
    for (const [t, pose] of keys) for (const [bone, ch] of Object.entries(body(pose))) for (const [c, v] of Object.entries(ch)) {
        bones[bone] = bones[bone] || {};
        bones[bone][c] = bones[bone][c] || {};
        bones[bone][c][t.toFixed(2)] = {post: v, lerp_mode: 'catmullrom'};
    }
    return {loop: false, animation_length: length, bones};
}

// ------------------------------------------------------------------ the prop's turn from where its handle and pick point
// A direction by the body (forward, up, right) in Java model space (x left, y down, z back).
const dir = (fwd, up, right) => v3.norm([-right, -up, -fwd]);
// The rot (deg, the key's xyz) that points the handle (up it, to the head) along h and the pick along p (made square to h).
// The prop at rot 0: the handle up (model -y), the pick ahead (model -z), so x = handle x pick.
function aim(h, p) {
    const u = v3.norm(h), w = v3.norm(v3.sub(p, v3.mul(u, v3.dot(p, u)))), s = v3.cross(u, w);
    // columns: e_x -> s, e_y -> -u, e_z -> -w
    const m = [[s[0], -u[0], -w[0]], [s[1], -u[1], -w[1]], [s[2], -u[2], -w[2]]];
    const tr = m[0][0] + m[1][1] + m[2][2];
    let q;
    if (tr > 0) {
        const k = Math.sqrt(tr + 1) * 2;
        q = [(m[2][1] - m[1][2]) / k, (m[0][2] - m[2][0]) / k, (m[1][0] - m[0][1]) / k, 0.25 * k];
    } else if (m[0][0] > m[1][1] && m[0][0] > m[2][2]) {
        const k = Math.sqrt(1 + m[0][0] - m[1][1] - m[2][2]) * 2;
        q = [0.25 * k, (m[0][1] + m[1][0]) / k, (m[0][2] + m[2][0]) / k, (m[2][1] - m[1][2]) / k];
    } else if (m[1][1] > m[2][2]) {
        const k = Math.sqrt(1 + m[1][1] - m[0][0] - m[2][2]) * 2;
        q = [(m[0][1] + m[1][0]) / k, 0.25 * k, (m[1][2] + m[2][1]) / k, (m[0][2] - m[2][0]) / k];
    } else {
        const k = Math.sqrt(1 + m[2][2] - m[0][0] - m[1][1]) * 2;
        q = [(m[0][2] + m[2][0]) / k, (m[1][2] + m[2][1]) / k, 0.25 * k, (m[1][0] - m[0][1]) / k];
    }
    const e = q4.eulerXYZ(q4.norm(q)).map(core.deg).map(v => +v.toFixed(1));
    // the round trip, so a convention slip shows at once
    const back = q4.xyz(core.rad(e[0]), core.rad(e[1]), core.rad(e[2]));
    if (v3.dist(q4.apply(back, [0, -1, 0]), u) > 0.01 || v3.dist(q4.apply(back, [0, 0, -1]), w) > 0.01) throw new Error('aim: euler round trip');
    return e;
}

// ------------------------------------------------------------------ the poses (solved with the rig core against the check
// below, 2026-10-21). grip: the right palm's point from the right shoulder; tip: the pick's tip on the strike anchor.
// Each key [arrives at s, body [squat, hips, chest], prop key]. One fixed handle slope only reaches a narrow band of
// heights, so each strike owns its band (low/high over the feet, near/far ahead of them; SkeletonActions PICK_*).
const grip = (pos, h, p) => ({frame: 'shoulder_r', point: 'grip', pos, rot: aim(h, p)});
const tip = (pos, h, p) => ({frame: 'anchor:strike', point: 'tip', pos, rot: aim(h, p)});
const HOLD = grip([1.53, 8.06, -3.67], dir(0.65, 0.49, -0.59), dir(-0.11, 0.82, 0.56));
const ABOUT = 'from the hold the head comes up ahead of the right shoulder (0.2 s) and out to its right with the chest turned up and the left hand slid down to the right (0.4 s), comes over and down along the look so the pick bites the rock point (0.6 s), is levered back on the stuck point (0.75 s), pulled out (0.9 s) and back into the hold.';
const HIT = '(boulder/BoulderStrike: 22 ticks, the hit lands at 12 = 0.6 s)';
const STRIKES = {
    strike_low: {length: 1.1, pitch: 30, near: 0.85, far: 1.0, low: 0.2, high: 0.45,
        about: 'Strike low ' + HIT + ' at a rock point below the waist, the hips and the chest bent low into it: ' + ABOUT,
        keys: [
            [0.20, [0, 0, -5], grip([7.75, 5.03, -4.78], dir(0.82, 0.54, 0.19), dir(0.46, -0.82, 0.34))],
            [0.40, [0, -5, -10], grip([4.43, 4.07, -3.65], dir(-0.15, 0.44, 0.88), dir(0.18, -0.87, 0.47))],
            [0.60, [24, 32, 32], tip([0, 0, 0], dir(0.77, -0.38, 0.51), dir(0.51, -0.11, -0.85))],
            [0.75, [24, 32, 28], tip([0, -0.5, 0.5], dir(0.98, -0.03, 0.21), dir(-0.01, -1, -0.08))],
            [0.90, [12, 16, 16], grip([5.49, 2.97, -4.81], dir(0.89, 0.42, -0.17), dir(0.43, -0.9, 0.08))]]},
    strike: {length: 1.1, pitch: 15, near: 0.9, far: 1.05, low: 0.45, high: 0.7,
        about: 'Strike ' + HIT + ' at a rock point from the waist to the belt, bent into it: ' + ABOUT,
        keys: [
            [0.20, [0, 0, -5], grip([7.95, 4.67, -4.91], dir(0.81, 0.54, 0.21), dir(0.47, -0.82, 0.32))],
            [0.40, [0, -5, -10], grip([4.55, 3.72, -3.34], dir(-0.05, 0.43, 0.9), dir(-0.24, -0.88, 0.41))],
            [0.60, [14, 22, 20], tip([0, 0, 0], dir(0.94, -0.11, 0.33), dir(0.01, -0.94, -0.35))],
            [0.75, [14, 22, 16], tip([0, -0.5, 0.5], dir(0.99, -0.03, 0.16), dir(-0.06, -0.98, 0.18))],
            [0.90, [7, 11, 10], grip([5.42, 3.75, -4.33], dir(0.88, 0.43, -0.21), dir(0.45, -0.89, 0.09))]]},
    strike_high: {length: 1.1, pitch: 10, near: 0.95, far: 1.05, low: 0.7, high: 1.0,
        about: 'Strike high ' + HIT + ' at a rock point from the belt to the chest, the handle level at the hit: ' + ABOUT,
        keys: [
            [0.20, [0, 0, -5], grip([8.09, 3.92, -5.14], dir(0.79, 0.58, 0.22), dir(0.46, -0.78, 0.43))],
            [0.40, [0, -5, -10], grip([4.88, 3.45, -3.66], dir(0.22, 0.53, 0.82), dir(-0.75, -0.44, 0.49))],
            [0.60, [12, 18, 16], tip([0, 0, 0], dir(0.98, 0.19, 0.02), dir(0.15, -0.84, 0.52))],
            [0.75, [12, 18, 12], tip([0, -0.5, 0.5], dir(0.99, 0.07, -0.12), dir(0.13, -0.34, 0.93))],
            [0.90, [6, 9, 8], grip([6.55, 3.76, -2.73], dir(0.83, 0.47, -0.31), dir(0.51, -0.86, 0.07))]]},
};
const HANDS = {right: [{at: 0.0, target: 'prop:grip', blend: 0, roll: 0}], left: [{at: 0.0, target: 'prop:grip_mid', blend: 0, roll: 0},
    {at: 0.4, target: 'prop:grip_close', blend: 0.2, roll: 0}, {at: 0.9, target: 'prop:grip_mid', blend: 0.2, roll: 0}]};
const key = (at, blend, p) => ({at: +at.toFixed(3), frame: p.frame, point: p.point, pos: p.pos, rot: p.rot, blend: +blend.toFixed(3)});

function strikeAction(name, s) {
    const keys = [key(0, 0, HOLD)];
    let last = 0.001;
    for (const [t, , p] of s.keys) { keys.push(key(last, t - last, p)); last = t; }
    keys.push(key(last, s.length - last, HOLD));
    return {
        format_version: 1,
        about: 'Pick mattock, ' + s.about + ' First and third person alike; the own first person sits on the posed eye (it goes down with the hips) and looks down to ' + s.pitch + ' deg at least. Written by tools/author-pick-actions-r1a.cjs. docs/gameplay/rock_r1_stones_boulders_design.md 11.2',
        clip: 'tool.pick_' + name, layer: 'action', mask: 'full', priority: 60, first_person: 'body', first_person_pitch: s.pitch,
        first_person_eye: true, blend: {in: 0.0, out: 0.2},
        prop: {id: 'pick_mattock', keys}, hands: HANDS, events: [],
    };
}

const HOLD_ACTION = {
    format_version: 1,
    about: 'Pick mattock in the main hand, first and third person alike (Rock R1a). Carried two-handed across the body as a pick is carried to the work: the right hand at the end of the handle at the right hip, the left up the handle ahead of the belly, the head up and out ahead to the left, the pick ahead. A strike (tool/pick_strike_low, pick_strike, pick_strike_high) starts and ends in this hold. Written by tools/author-pick-actions-r1a.cjs. docs/gameplay/rock_r1_stones_boulders_design.md 11.2',
    layer: 'action', mask: 'arms', priority: 30, first_person: 'body', blend: {in: 0.0, out: 0.2},
    prop: {id: 'pick_mattock', keys: [key(0, 0, HOLD)]}, hands: HANDS, events: [],
};

const files = {
    [path.join(A, 'player_actions/item/pick_mattock.json')]: HOLD_ACTION,
    [path.join(A, 'player_actions/tool/pick_strike.json')]: strikeAction('strike', STRIKES.strike),
    [path.join(A, 'player_actions/tool/pick_strike_low.json')]: strikeAction('strike_low', STRIKES.strike_low),
    [path.join(A, 'player_actions/tool/pick_strike_high.json')]: strikeAction('strike_high', STRIKES.strike_high),
};
const clipsFile = path.join(A, 'animations/player/tool.animation.json');
const tool = read(clipsFile);
for (const [name, s] of Object.entries(STRIKES))
    tool.animations['tool.pick_' + name] = clip(s.length, [[0, [0, 0, 0]], ...s.keys.map(([t, b]) => [t, b]), [s.length, [0, 0, 0]]]);
const inline = s => s.replace(/\[\s*(-?[\d.e+-]+(?:,\s*-?[\d.e+-]+)*)\s*\]/g, (m, a) => '[' + a.split(/,\s*/).join(', ') + ']');
const eol = (s, f) => (fs.existsSync(f) && fs.readFileSync(f, 'utf8').includes('\r\n') ? s.replace(/\n/g, '\r\n') : s);
const text = j => inline(JSON.stringify(j, null, 2)) + '\n';
const clipText = eol(inline(JSON.stringify(tool, null, 1)) + '\n', clipsFile);
const stale = [];
for (const [f, j] of Object.entries(files)) {
    if (CHECK) { if (!fs.existsSync(f) || fs.readFileSync(f, 'utf8') !== text(j)) stale.push(path.relative(root, f)); }
    else fs.writeFileSync(f, text(j));
}
if (CHECK) { if (fs.readFileSync(clipsFile, 'utf8') !== clipText) stale.push(path.relative(root, clipsFile)); }
else fs.writeFileSync(clipsFile, clipText);

// ------------------------------------------------------------------ the check
const rig = core.loadRig(read(path.join(A, 'rigs/afl_humanoid_v1.json')));
const poser = new core.Poser(rig, core.loadClips(rig, read(path.join(A, 'animations/player/locomotion.animation.json'))));
const clips = core.loadClips(rig, read(clipsFile));
const prop = read(path.join(A, 'player_props/pick_mattock.json'));
const anchorAt = (d, h) => [-SIDE * PX, 24 - h * PX, -d * PX];

// The pick clear of the head and the torso: samples along the handle (grip to eye) and the head (edge to tip), in each
// bone's rest frame, against its box (Java model px) grown by the handle's half thickness.
const BOXES = [['head', [-4, -8, -4], [4, 0, 4]], ['chest', [-4, 0, -2], [4, 12, 2]]];
const SAMPLES = [];
for (let i = 0; i <= 12; i++) SAMPLES.push(['grip', 'eye', i / 12]);
for (let i = 0; i <= 8; i++) SAMPLES.push(['edge', 'tip', i / 8]);
function clearance(res) {
    const pl = res.props[0];
    if (!pl) return null;
    let worst = null;
    for (const [bone, lo, hi] of BOXES) {
        const inv = core.tf.inv(res.pose.world[rig.find(bone)]);
        for (const [a, b, t] of SAMPLES) {
            const p = core.tf.apply(inv, core.tf.apply(pl, v3.lerp(core.propLocal(prop, a), core.propLocal(prop, b), t)));
            const g = 0.6;   // the handle's half thickness, px
            const depth = Math.min(p[0] - lo[0] + g, hi[0] + g - p[0], p[1] - lo[1] + g, hi[1] + g - p[1], p[2] - lo[2] + g, hi[2] + g - p[2]);
            if (depth > 0 && (!worst || depth > worst.depth)) worst = {bone, depth, at: a + '-' + b + ' ' + t.toFixed(2)};
        }
    }
    return worst;
}

const out = {}, bad = [];
for (const [f] of Object.entries(files)) {
    const name = path.basename(f, '.json'), json = read(f);
    const action = core.actionFromJson(json, id => (id === 'pick_mattock' ? prop : null), k => clips[k] || null);
    const s = STRIKES[name.slice(5)], len = s ? s.length : 0;
    let offMax = 0, wristMax = 0, twistMax = 0, inside = 0, n = 0;
    const cases = [];
    if (!s) for (const pitch of [-10, 10, 30, 50, 65]) cases.push([pitch, anchorAt(0.9, 0.5)]);
    else for (const d of [s.near, (s.near + s.far) / 2, s.far]) for (const h of [s.low, (s.low + s.high) / 2, s.high]) {
        const look = Math.atan2(1.62 - h, d) * 180 / Math.PI;
        for (const pitch of [look - 10, look, look + 10]) cases.push([+Math.max(-10, Math.min(65, pitch)).toFixed(0), anchorAt(d, h)]);
    }
    for (const [pitch, anchor] of cases)
        for (let t = 0; t <= len + 1e-6; t += 0.05) {
            const res = core.solveFrame(poser, {action, seconds: t, time: t, pitch, anchors: k => (k === 'strike' ? anchor : null)});
            const ro = core.readouts(res);
            const hit = clearance(res);
            if (hit) inside = Math.max(inside, hit.depth);
            if (hit && hit.depth > 0.4) bad.push(`${name} ${t.toFixed(2)} pitch ${pitch} d ${(-anchor[2] / PX).toFixed(2)} h ${((24 - anchor[1]) / PX).toFixed(2)}: through the ${hit.bone} ${hit.depth.toFixed(2)} px at ${hit.at}`);
            for (const side of ['right', 'left']) {
                const r = ro[side];
                if (r.mode !== 'ik') { bad.push(name + ' ' + side + ' not on the handle at ' + t.toFixed(2)); continue; }
                n++;
                if (r.w > 0.99) offMax = Math.max(offMax, r.off);
                wristMax = Math.max(wristMax, r.wrist);
                twistMax = Math.max(twistMax, r.twist);
                if ((r.w > 0.99 && r.off > 1.25) || r.wrist > 50 || r.twist > 60)
                    bad.push(`${name} ${t.toFixed(2)} pitch ${pitch} d ${(-anchor[2] / PX).toFixed(2)} h ${((24 - anchor[1]) / PX).toFixed(2)} ${side}: off ${r.off.toFixed(2)} wrist ${r.wrist.toFixed(0)} twist ${r.twist.toFixed(0)}`);
            }
            if (!json.clip) break;
        }
    out[name] = {samples: n, offMax: +offMax.toFixed(2), wristMax: +wristMax.toFixed(0), twistMax: +twistMax.toFixed(0), inside: +inside.toFixed(2)};
}
console.log(JSON.stringify(out));
if (stale.length) console.log('stale (rerun without --check): ' + stale.join(', '));
if (bad.length) console.log(bad.length + ' bad samples' + (process.argv.includes('--all') ? ':\n' + bad.join('\n') : ', first: ' + bad.slice(0, 12).join(' | ')));
process.exit(stale.length || bad.length ? 1 : 0);
