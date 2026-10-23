// Earthwork A2 (docs/worldgen/earthwork_a2.md 6): writes the round point shovel's skeleton actions and clips, then checks
// them with the AFL Rig core (tools/blockbench/afl_rig.js) as the game poses them.
//   player_actions/item/round_point_shovel.json  the hold (two hands, carried across the body)
//   player_actions/tool/shovel_dig.json          dig, 2.2 s (interaction/ShovelAction DURATION 44 ticks, commit 30; the load
//                                                 stays on the blade until it is poured, user 2026-10-22)
//   player_actions/tool/shovel_pour.json         pour, 1.2 s (24 ticks, commit 14)
//   player_actions/tool/shovel_scrape.json       scrape, 1.4 s (28 ticks, commit 18)
//   animations/player/tool.animation.json        tool.shovel_dig / _pour / _scrape (the body: hips, knees, chest)
// The pour and the scrape work the "dig" anchor: the ground point client/skeleton/SkeletonActions#shovelAnchor gives,
// clamped to what these poses reach (its SHOVEL_NEAR..FAR ahead of the feet, SIDE to the right, LOW below to HIGH above). The
// dig is placed on the body (frame root): the blade goes in half a stride ahead of the right foot, so the foot steps onto
// the blade's step (interaction/ShovelAction puts the hole round that spot: DIG_AT); its body and head are the clip's
// (hold_body, hold_head), not the look's: looking down bent the head forward into the handle and the hands (user
// 2026-10-22: the dig clipped badly); so are the pour's and the scrape's. The hands are rings round the handle (user
// 2026-10-22): they slide along it between handle points (carry in the hold, dig_r / dig_l through the dig, dig_r and the
// D through the pour and the scrape). The check samples every 0.05 s over that range, at
// view pitches 0..80 (and crouched for the hold and the scrape, a sneak): each palm on its handle point (off <= 1 px = 6 cm
// once the hand is fully on it), wrist <= 50 deg, forearm twist <= 60 deg; nothing in the body (the skin boxes): an upper
// arm at most 1.2 px into the chest or the head where it leaves the shoulder (as the accepted holds: the pick, the axe,
// the guns, 0.6..1.3 px), a forearm or a hand at most 0.5 px (3 cm, a sleeve against the coat), the handle's centre line
// 0.4 px clear of the body (a shin may brush it on the blade's step); every stroke's soles: the left one on the ground
// (0.8 px) all through, the right one on the ground but while it is on the dig's blade step (on it within 1 px at the
// stamp).
// Run: node tools/author-shovel-actions-a2.cjs (write + check) | --check (check the files as they are, fail if stale)
const fs = require('fs'), path = require('path');
const root = path.resolve(__dirname, '..');
const core = require(path.join(root, 'tools/blockbench/afl_rig.js'));
const A = path.join(root, 'src/main/resources/assets/apocalypse_firstlight');
const read = p => JSON.parse(fs.readFileSync(p, 'utf8'));
const PX = 16 / 0.9375;
const CHECK = process.argv.includes('--check');
// the reach the pour and the scrape are checked over: client/skeleton/SkeletonActions SHOVEL_* (m), read from it
const SHOVEL = (() => {
    const src = fs.readFileSync(path.join(root, 'src/main/java/com/antaurora/apofirstlight/client/skeleton/SkeletonActions.java'), 'utf8');
    const of = n => { const m = new RegExp('SHOVEL_' + n + ' = (-?[\\d.]+)').exec(src); if (!m) throw new Error('SkeletonActions SHOVEL_' + n); return +m[1]; };
    return {near: of('NEAR'), far: of('FAR'), side: of('SIDE'), low: of('LOW'), high: of('HIGH')};
})();

// ------------------------------------------------------------------ the body: squat (thigh forward a deg, ankle under
// the hip), hips tipped p, chest bent c (deg, + forward); [a, p, c], or {a, p, c, cy, py, rl} with the chest and the hips
// turned cy, py (deg, + to the right) and the right leg's thigh, shin, foot angles rl (the foot up on the blade's step);
// hx, hy: the head bent down and turned (deg) where the action holds the head.
// Legs follow so the feet stay flat: thigh -a - p, shin a + b, foot -b with 6 sin a = 4 sin b (thigh 6 px, shin 4 px),
// the thighs turned back against the hips; the hips drop by what the bent legs lose.
function body(pose) {
    const {a, p, c, cy = 0, py = 0, rl = null, hx = null, hy = 0} = Array.isArray(pose) ? {a: pose[0], p: pose[1], c: pose[2]} : pose;
    const r = Math.PI / 180, b = Math.asin(6 * Math.sin(a * r) / 4) / r;
    const drop = 10 - 6 * Math.cos(a * r) - 4 * Math.cos(b * r);
    const f = v => +v.toFixed(3) || 0;
    const right = rl || [-a - p, a + b, -b];
    const head = hx === null ? {} : {head: {rotation: [f(hx), f(hy), 0]}};
    return {...head, pelvis: {rotation: [p, f(py), 0], position: [0, f(-drop), 0]}, chest: {rotation: [c, f(cy), 0]},
        thigh_r: {rotation: [f(right[0]), f(-py), 0]}, shin_r: {rotation: [f(right[1]), 0, 0]}, foot_r: {rotation: [f(right[2]), 0, 0]},
        thigh_l: {rotation: [f(-a - p), f(-py), 0]}, shin_l: {rotation: [f(a + b), 0, 0]}, foot_l: {rotation: [f(-b), 0, 0]}};
}

// eased (catmullrom) key to key; the right leg straight (linear) from one key with its foot planted to the next, so the
// curve does not swing the planted foot about on either side of a step
const PLANTED_R = new Set(['thigh_r', 'shin_r', 'foot_r']);
function clip(length, keys) {
    const bones = {};
    for (const [t, pose] of keys) for (const [bone, ch] of Object.entries(body(pose))) for (const [c, v] of Object.entries(ch)) {
        bones[bone] = bones[bone] || {};
        bones[bone][c] = bones[bone][c] || {};
        bones[bone][c][t.toFixed(2)] = {post: v, lerp_mode: PLANTED_R.has(bone) && !pose.rl ? 'linear' : 'catmullrom'};
    }
    return {loop: false, animation_length: length, bones};
}

// ------------------------------------------------------------------ the poses (solved with the rig core, 2026-10-22)
// the hold: the D at the left of the belly in the left hand, the right hand at carry down the handle, the shaft across
// the body to the blade low ahead on the right, its load face turned up (the arms only: the body follows the look)
const HOLD = {frame: 'shoulder_r', point: 'grip_end', pos: [8.72, 3.68, -4.334], rot: [-34.973, 0.95, 44.054]};
// the same placed by the right hand's place (carry): a stroke coming back to the hold turns the shovel about that hand
const HOLD_BY_CARRY = {frame: 'shoulder_r', point: 'carry', pos: [5.63, 6.33, -6.12], rot: HOLD.rot};
const byD = (pos, rot) => ({frame: 'anchor:dig', point: 'grip_end', pos, rot});
const rooted = (pos, rot) => ({frame: 'root', point: 'tip', pos, rot});
// the same placed by the left hand's place: the shovel turns about the hand on its way to it from the key before
const inHand = (pos, rot) => ({frame: 'root', point: 'dig_l', pos, rot});
// [arrives at s, body, prop placement]
const STROKES = {
    // the big shovel (user 2026-10-22; real technique: the blade set half a stride ahead of the right foot, the handle
    // upright in front of the right shoulder, a foot on the step to drive it, the handle levered back over bent knees, the
    // load lifted with the legs). The head and the chest are the clip's (hold_head, hold_body): the head looks down a
    // little, past the left of the handle; both hands slid down off the D (dig_l, dig_r). Solved with the rig core for both
    // hands on the handle, nothing in the body and the soles where they belong.
    // its ends: the chest and the head as the look bends them at the dig's 45 deg (rig core Poser.chestPitch: 13.5 deg),
    // so the body, the eye and with it the feet do not jump coming out of the hold and going back to it
    dig: {length: 2.2, pitch: 45, hold: true, ends: {a: 0, p: 0, c: 13.5, hx: 31.5}, back: HOLD_BY_CARRY, steps: [0.70, 1.25], stamp: [0.90, 1.08],
        hands: [['carry', 'dig_r'], ['grip_end', 'dig_l']],
        about: 'Dig (interaction/ShovelAction DIG: 44 ticks, the scoop at 30 = 1.5 s): from the hold the blade is set upright half a stride ahead of the right foot (0.45 s), both hands sliding down the handle, and pushed in (0.7 s); the right foot goes up onto the blade\'s step (0.9 s) and stamps it in (1.08 s), comes back down (1.25 s), the handle is levered back over bent knees (1.48 s, the scoop) and the loaded blade lifted with the legs (1.8 s) into the hold. The load stays on the blade until it is poured.',
        keys: [
            [0.45, {a: 2.85, p: 6.91, c: 3.81, cy: -1.48, py: 1, hx: 8.28, hy: -7.21}, inHand([-1.36, 2.26, -8.97], [167.1, 8.4, 177.9])],
            [0.70, {a: 6.11, p: 8.12, c: 5.85, cy: -1.82, py: 0.88, hx: 11.55, hy: -6.45}, rooted([-2.71, 26, -10.25], [168.3, 6.6, 178.4])],
            [0.90, {a: 6.5, p: 7.07, c: 7.09, cy: 9.42, py: 3.34, hx: 10.15, hy: -5.35, rl: [-76.72, 8.44, 24.94]}, rooted([-4.32, 25.89, -9.74], [167.4, 10.3, -179.8])],
            [1.08, {a: 22.01, p: 7.33, c: 4.79, cy: 9.55, py: 3.77, hx: 12.07, hy: -6.4, rl: [-65.72, -7.56, 20.15]}, rooted([-4.05, 27.95, -9.48], [166, 0.1, 178.1])],
            [1.25, {a: 9.42, p: 12.61, c: 8.32, cy: -1.66, py: 1, hx: 14.25, hy: -6.58}, rooted([-2.78, 28.34, -10.04], [167.3, 6.2, 177.2])],
            [1.48, {a: 17.45, p: 7.09, c: 8.34, cy: -1.48, py: 1.48, hx: 14.16, hy: -6.75}, rooted([-2.75, 28.05, -10.9], [163.1, 5.2, 177.5])],
            [1.80, {a: 10.78, p: 8.92, c: 9.43, cy: -6.24, py: -0.94, hx: 11.82, hy: -2.65}, inHand([1.29, 7.69, -6.82], [-55, 27.7, 37.4])]]},
    // the pour and the scrape: the blade about a metre ahead (SkeletonActions SHOVEL_NEAR..FAR 1.0..1.08 m, LOW..HIGH
    // +-0.05 m), the D in the left hand beside the left hip, the right hand slid down to dig_r; nearer, the 1.6 m handle can
    // only go into the chest. The body and the head are the clip's as in the dig, the ends at the look's 40 / 50 deg (chest
    // 12 / 15, head 28 / 35 deg); the body's keys made dense (dense) so the feet stay flat into the deep squat.
    // Placed about the D (byD), so the left hand stays on it from the hold through the stroke and back. 'via': a key between
    // two others (the right hand where its slide has got to then), so the arms reach the handle on the way: eased straight
    // from the hold, the shovel ran ahead of the bending body and the right hand came 3 px off it.
    pour: {length: 1.2, pitch: 40, hold: true, dense: true, ends: {a: 0, p: 0, c: 12, hx: 28}, hands: [['carry', 'dig_r'], ['grip_end', 'grip_end']],
        about: 'Pour (interaction/ShovelAction POUR: 24 ticks, the load goes down at 14 = 0.7 s): bending at the hips, the loaded blade is carried out a metre ahead over the ground point (0.4 s) and turned on its handle (0.7 s), so the load slides off its side, and back into the hold.',
        keys: [
            [0.20, {a: 3.43, p: 14.16, c: 14.55, cy: -14.5, py: 0.26, hx: 16.83, hy: 2.58}, byD([8.11, -18.81, 17.26], [-41.5, -11.6, 39.7]), 'via'],
            [0.40, {a: 5.75, p: 20.59, c: 12.48, cy: -16.76, py: -0.17, hx: 15.48, hy: -0.5}, byD([8.03, -18.23, 18.65], [-51.9, -23.8, 29.4])],
            [0.55, {a: 7.39, p: 22.33, c: 11.9, cy: -20.12, py: 1.14, hx: 15.93, hy: 0.52}, byD([8.21, -18.35, 17.98], [1.5, -58.9, 71.2]), 'via'],
            [0.70, {a: 7.97, p: 22.26, c: 13.76, cy: -15.13, py: -0.28, hx: 17.65, hy: -1.26}, byD([7.91, -17.49, 18.56], [73.3, -49.6, 137.5])]]},
    scrape: {length: 1.4, pitch: 50, hold: true, dense: true, ends: {a: 0, p: 0, c: 15, hx: 35}, hands: [['carry', 'dig_r'], ['grip_end', 'grip_end']],
        about: 'Scrape (sneak; interaction/ShovelAction SCRAPE: 28 ticks, the ground changes at 18 = 0.9 s): squatting low, the blade goes down on the ground a metre ahead (0.45 s) and is pushed out along it (0.9 s), comes up (1.15 s) and back into the hold.',
        keys: [
            [0.18, {a: 18.25, p: 16.84, c: 14.05, cy: -15.68, py: 1.15, hx: 25, hy: 0.53}, byD([7.4, -16.71, 17.56], [-15.4, -26.2, 44.9]), 'via'],
            [0.45, {a: 41.81, p: 27.57, c: 9.96, cy: -29.43, py: 2.59, hx: 9.85, hy: -1.31}, byD([7.94, -13.76, 17.3], [-37.5, -11.6, 22.4])],
            [0.70, {a: 41.81, p: 35.34, c: 13.35, cy: -28.65, py: 2.02, hx: 11.63, hy: 0.26}, byD([8.01, -12.09, 16.32], [-42, -11.8, 23.7])],
            [0.90, {a: 41.81, p: 36.39, c: 19.2, cy: -25.45, py: 1.83, hx: 14.78, hy: 0.49}, byD([7.8, -10.76, 16.37], [-50.4, -13.7, 24.1])],
            [1.00, {a: 32.91, p: 35.19, c: 19.05, cy: -28.9, py: 2.8, hx: 14.25, hy: 1.6}, byD([7.94, -13.87, 17.05], [-46.5, -15, 25.4]), 'via'],
            [1.15, {a: 12.39, p: 28.14, c: 16.15, cy: -26.79, py: 2.01, hx: 14.85, hy: 1.99}, byD([7.59, -16.95, 18.88], [-46.3, -14.9, 24.9])],
            [1.27, {a: 6.63, p: 18.83, c: 15.77, cy: -19.28, py: 1.64, hx: 23.07, hy: 2.25}, byD([7.95, -17.89, 17.26], [-22.6, -25.2, 43.8]), 'via']]},
};
const HANDS = {right: [{at: 0.0, target: 'prop:carry', blend: 0, roll: 0}], left: [{at: 0.0, target: 'prop:grip_end', blend: 0, roll: 0}]};
// a stroke's hands: each slides from its place in the hold to its place for the stroke by the first key (not a via), and
// back from the last one by the end
function strokeHands(s) {
    const work = s.keys.filter(k => k[3] !== 'via');
    const [r, l] = s.hands || [['carry', 'grip'], ['grip_end', 'grip_end']], first = work[0][0], last = work[work.length - 1][0];
    const side = ([hold, work]) => (hold === work ? [{at: 0.0, target: 'prop:' + hold, blend: 0, roll: 0}] : [
        {at: 0.0, target: 'prop:' + hold, blend: 0, roll: 0}, {at: 0.001, target: 'prop:' + work, blend: +(first - 0.001).toFixed(3), roll: 0},
        {at: last, target: 'prop:' + hold, blend: +(s.length - last).toFixed(3), roll: 0}]);
    return {right: side(r), left: side(l)};
}
const key = (at, blend, p) => ({at: +at.toFixed(3), frame: p.frame, point: p.point, pos: p.pos, rot: p.rot, blend: +blend.toFixed(3)});

function strokeAction(name, s) {
    const keys = [key(0, 0, HOLD)];
    let last = 0.001;
    for (const [t, , p] of s.keys) { keys.push(key(last, t - last, p)); last = t; }
    keys.push(key(last, s.length - last, s.back || HOLD));
    return {
        format_version: 1,
        about: 'Round point shovel, ' + s.about + ' First and third person alike; the own first person sits on the posed eye (it goes down with the hips) and looks down to ' + s.pitch + ' deg at least. Written by tools/author-shovel-actions-a2.cjs. docs/worldgen/earthwork_a2.md 6',
        clip: 'tool.shovel_' + name, layer: 'action', mask: 'full', priority: 60, first_person: 'body', first_person_pitch: s.pitch,
        first_person_eye: true, ...(s.hold ? {hold_body: true, hold_head: true} : {}), blend: {in: s.hold ? 0.15 : 0.0, out: 0.2},
        prop: {id: 'round_point_shovel', keys}, hands: strokeHands(s), events: [],
    };
}

const HOLD_ACTION = {
    format_version: 1,
    about: 'Round point shovel in the main hand, first and third person alike (Earthwork A2). Held two-handed across the body as a shovel is carried to the work: the left hand on the D grip at the left of the belly, the right down the handle in front of the right hip, the blade low ahead and out to the right with its load face turned up (it carries the load until it is poured). A stroke (dig, pour, scrape: tool/shovel_*) starts and ends in this hold. Written by tools/author-shovel-actions-a2.cjs. docs/worldgen/earthwork_a2.md 6',
    layer: 'action', mask: 'arms', priority: 30, first_person: 'body', blend: {in: 0.0, out: 0.2},
    prop: {id: 'round_point_shovel', keys: [key(0, 0, HOLD)]}, hands: HANDS, events: [],
};

const files = {
    [path.join(A, 'player_actions/item/round_point_shovel.json')]: HOLD_ACTION,
    [path.join(A, 'player_actions/tool/shovel_dig.json')]: strokeAction('dig', STROKES.dig),
    [path.join(A, 'player_actions/tool/shovel_pour.json')]: strokeAction('pour', STROKES.pour),
    [path.join(A, 'player_actions/tool/shovel_scrape.json')]: strokeAction('scrape', STROKES.scrape),
};
const clipsFile = path.join(A, 'animations/player/tool.animation.json');
const tool = read(clipsFile);
// a body's keys every 0.05 s on the curve the rig eases through them (afl_rig Channel: catmullrom key to key), each solved
// for flat feet: from standing into a deep squat the legs' angles eased apart would lift the soles off the ground
const FIELDS = ['a', 'p', 'c', 'cy', 'py', 'hx', 'hy'];
function dense(keys) {
    const v = keys.map(([, b]) => FIELDS.map(k => b[k] || 0)), out = [];
    const cr = (f, p0, p1, p2, p3) => 0.5 * (2 * p1 + (-p0 + p2) * f + (2 * p0 - 5 * p1 + 4 * p2 - p3) * f * f + (-p0 + 3 * p1 - 3 * p2 + p3) * f * f * f);
    for (let i = 0; i < keys.length - 1; i++) {
        const [ta] = keys[i], [tb] = keys[i + 1], n = Math.max(1, Math.round((tb - ta) / 0.05));
        for (let j = 0; j < n; j++) {
            const f = j / n, p0 = v[Math.max(0, i - 1)], p3 = v[Math.min(v.length - 1, i + 2)];
            // the squat no deeper than the shin reaches (6 sin a <= 4: a <= 41.8 deg), the curve overshooting it between keys
            const at = m => cr(f, p0[m], v[i][m], v[i + 1][m], p3[m]);
            out.push([+(ta + (tb - ta) * f).toFixed(3), Object.fromEntries(FIELDS.map((k, m) => [k, +(k === 'a' ? Math.min(41.8, at(m)) : at(m)).toFixed(3)]))]);
        }
    }
    return out.concat([keys[keys.length - 1]]);
}
for (const [name, s] of Object.entries(STROKES)) {
    const keys = [[0, s.ends || [0, 0, 0]], ...s.keys.map(([t, b]) => [t, b]), [s.length, s.ends || [0, 0, 0]]];
    tool.animations['tool.shovel_' + name] = clip(s.length, s.dense ? dense(keys) : keys);
}
// number arrays on one line and the file's own line ends, as the hand-written files
const inline = s => s.replace(/\[\s*(-?[\d.e+-]+(?:,\s*-?[\d.e+-]+)*)\s*\]/g, (m, a) => '[' + a.split(/,\s*/).join(', ') + ']');
const eol = (s, f) => (fs.existsSync(f) && fs.readFileSync(f, 'utf8').includes('\r\n') ? s.replace(/\n/g, '\r\n') : s);
const text = j => inline(JSON.stringify(j, null, 2)) + '\n';
const clipText = eol(inline(JSON.stringify(tool, null, 1)) + '\n', clipsFile);
let stale = [];
for (const [f, j] of Object.entries(files)) {
    if (CHECK) { if (!fs.existsSync(f) || fs.readFileSync(f, 'utf8') !== text(j)) stale.push(path.relative(root, f)); }
    else fs.writeFileSync(f, text(j));
}
if (CHECK) { if (fs.readFileSync(clipsFile, 'utf8') !== clipText) stale.push(path.relative(root, clipsFile)); }
else fs.writeFileSync(clipsFile, clipText);

// ------------------------------------------------------------------ the check
const rigJson = read(path.join(A, 'rigs/afl_humanoid_v1.json')), rig = core.loadRig(rigJson);
// the skin boxes (geo -> model px: x, 24 - y, z), the head 8 px; how deep a point is inside one (<= 0: outside)
const boxes = {head: {min: [-4, -8, -4], max: [4, 0, 4]}};
for (const part of rigJson.skin) {
    const top = part.from[1] + part.size[1];
    part.bones.forEach((bone, i) => {
        const hi = i === 0 ? top : top - part.cuts[i - 1], lo = i === part.bones.length - 1 ? part.from[1] : top - part.cuts[i];
        boxes[bone] = {min: [part.from[0], 24 - hi, part.from[2]], max: [part.from[0] + part.size[0], 24 - lo, part.from[2] + part.size[2]]};
    });
}
const boneOf = n => rig.find(n);
function depth(pose, name, q) {
    const b = boxes[name], l = core.tf.apply(core.tf.inv(pose.world[boneOf(name)]), q);
    let d = Infinity;
    for (let i = 0; i < 3; i++) d = Math.min(d, l[i] - b.min[i], b.max[i] - l[i]);
    return d;
}
const BODY = ['head', 'chest', 'pelvis', 'thigh_r', 'thigh_l', 'shin_r', 'shin_l'];
// the arms on a 1 px grid inside their boxes shrunk 0.5 px; an upper arm from 2.5 px below its top (the shoulder joint
// itself sits in the chest's corner)
const ARMS = [['upper_arm_r', 2.5], ['forearm_r', 0], ['hand_r', 0], ['upper_arm_l', 2.5], ['forearm_l', 0], ['hand_l', 0]];
function clipping(res, prop) {
    const pose = res.pose, out = {upper: {d: 0, who: ''}, lower: {d: 0, who: ''}, tool: {d: -Infinity, who: ''}};
    for (const [a, cut] of ARMS) {
        const b = boxes[a], at = a.startsWith('upper') ? out.upper : out.lower;
        for (let x = b.min[0] + 0.5; x <= b.max[0] - 0.5 + 1e-6; x++) for (let y = b.min[1] + 0.5 + cut; y <= b.max[1] - 0.5 + 1e-6; y++)
            for (let z = b.min[2] + 0.5; z <= b.max[2] - 0.5 + 1e-6; z++) {
                const q = pose.point(boneOf(a), [x, y, z]);
                for (const body of BODY) { const d = depth(pose, body, q); if (d > at.d) { at.d = d; at.who = a + '>' + body; } }
            }
    }
    // the shovel along its handle (the D to the blade's heel) and down the blade to its tip
    if (!res.prop) { out.tool = {d: Infinity, who: "no shovel"}; return out; }
    const P = n => core.tf.apply(res.prop, core.propLocal(prop, n)), pts = [];
    for (let i = 0; i <= 50; i++) pts.push(core.v3.lerp(P('grip_end'), P('heel'), i / 50));
    for (let i = 1; i <= 10; i++) pts.push(core.v3.lerp(P('heel'), P('tip'), i / 10));
    for (const body of BODY) for (const q of pts) { const d = depth(pose, body, q); if (d > out.tool.d) { out.tool.d = d; out.tool.who = 'shovel>' + body; } }
    return out;
}
// the handle's radius 0.32 px: its centre line at least 0.4 px outside the body; a leg's skin it may brush (0.1 px: the
// right shin right behind the handle while that foot is on the blade's step)
const CLIP = {upper: 1.2, lower: 0.5, tool: -0.4, leg: 0.1};
const poser = new core.Poser(rig, core.loadClips(rig, read(path.join(A, 'animations/player/locomotion.animation.json'))));
const clips = core.loadClips(rig, read(clipsFile));
const prop = read(path.join(A, 'player_props/round_point_shovel.json'));
const out = {}, bad = [];
for (const [f] of Object.entries(files)) {
    const name = path.basename(f, '.json'), json = read(f);
    const action = core.actionFromJson(json, id => (id === 'round_point_shovel' ? prop : null), k => clips[k] || null);
    const len = json.clip ? STROKES[name.slice(7)].length : 0;
    let offMax = 0, wristMax = 0, twistMax = 0, n = 0;
    const deep = {upper: 0, lower: 0, tool: -Infinity};
    const anchors = [];
    if (name === 'shovel_pour' || name === 'shovel_scrape')
        for (const d of [SHOVEL.near, (SHOVEL.near + SHOVEL.far) / 2, SHOVEL.far]) for (const h of [SHOVEL.low, 0, SHOVEL.high]) anchors.push([-SHOVEL.side * PX, 24 - h * PX, -d * PX]);
    else anchors.push(null);
    const pitches = json.clip ? [30, 45, 60, 80] : [0, 30, 45, 60, 80];
    for (const pitch of pitches) for (const crouch of name === 'shovel_scrape' || !json.clip ? [false, true] : [false]) for (const anchor of anchors)
        for (let t = json.blend.in > 0 ? 0.05 : 0; t <= len + 1e-6; t += 0.05) {
            const res = core.solveFrame(poser, {action, seconds: t, time: t, pitch, crouch, anchors: k => (k === 'dig' ? anchor : null)});
            const ro = core.readouts(res), c = clipping(res, prop);
            const where = `${name} ${t.toFixed(2)} pitch ${pitch}${crouch ? ' crouched' : ''}${anchor ? ` d ${(-anchor[2] / PX).toFixed(2)} h ${((24 - anchor[1]) / PX).toFixed(2)}` : ''}`;
            for (const k of ['upper', 'lower', 'tool']) {
                deep[k] = Math.max(deep[k], c[k].d);
                if (c[k].d > (k === 'tool' && /(thigh|shin)/.test(c[k].who) ? CLIP.leg : CLIP[k])) bad.push(`${where}: ${c[k].who} ${c[k].d.toFixed(2)} px`);
            }
            for (const side of ['right', 'left']) {
                const r = ro[side];
                if (r.mode !== 'ik') { bad.push(name + ' ' + side + ' not on the handle at ' + t.toFixed(2)); continue; }
                n++;
                if (r.w > 0.99) offMax = Math.max(offMax, r.off);
                wristMax = Math.max(wristMax, r.wrist);
                twistMax = Math.max(twistMax, r.twist);
                if ((r.w > 0.99 && r.off > 1.0) || r.wrist > 50 || r.twist > 60)
                    bad.push(`${where} ${side}: off ${r.off.toFixed(2)} wrist ${r.wrist.toFixed(0)} twist ${r.twist.toFixed(0)}`);
            }
            if (!json.clip) break;
        }
    out[name] = {samples: n, offMax: +offMax.toFixed(2), wristMax: +wristMax.toFixed(0), twistMax: +twistMax.toFixed(0),
        upperArm: +deep.upper.toFixed(2), lowerArm: +deep.lower.toFixed(2), shovel: +deep.tool.toFixed(2)};
    const st = json.clip ? STROKES[name.slice(7)] : null;
    if (st) {
        // the soles (model px; standing, their toe and heel ends at x 1.9 left / -1.9 right, y 24, z 2.27 and 6.27): the
        // left one planted all through (within 0.8 px: the body's turn slides them a little, the feet pivoting with it);
        // the right one planted outside the dig's step, on the blade's step at the stamp's keys
        const steps = st.steps || [Infinity, -Infinity], stamp = st.stamp || [];
        const ends = (pose, foot) => [-2, 2].map(z => pose.point(foot, [rig.pivot[foot][0], 24, z]));
        const off = (q, x) => Math.max(Math.hypot(q[0][0] - x, q[0][1] - 24, q[0][2] - 2.27), Math.hypot(q[1][0] - x, q[1][1] - 24, q[1][2] - 6.27));
        let left = 0, right = 0, step = 0;
        for (let t = 0; t <= len + 1e-6; t += 0.01) {
            const res = core.solveFrame(poser, {action, seconds: t, time: t, pitch: 45, anchors: () => null});
            const l = ends(res.pose, rig.footL), r = ends(res.pose, rig.footR);
            left = Math.max(left, off(l, 1.9));
            if (t < steps[0] - 1e-6 || t > steps[1] + 1e-6) right = Math.max(right, off(r, -1.9));
            if (stamp.some(k => Math.abs(k - t) < 0.005)) {
                // the sole's middle over the step, its lower end on it
                const at = core.tf.apply(res.prop, core.propLocal(prop, 'step_r'));
                step = Math.max(step, Math.abs(Math.max(r[0][1], r[1][1]) - at[1]), Math.abs((r[0][0] + r[1][0]) / 2 - at[0]));
            }
        }
        Object.assign(out[name], {leftSole: +left.toFixed(2), rightSole: +right.toFixed(2), onStep: +step.toFixed(2)});
        if (left > 0.8) bad.push(name + ' left sole off the ground by ' + left.toFixed(2) + ' px');
        if (right > 0.8) bad.push(name + ' right sole off the ground by ' + right.toFixed(2) + ' px outside the step');
        if (step > 1.0) bad.push(name + ' right sole off the blade\'s step by ' + step.toFixed(2) + ' px at the stamp');
    }
}
console.log(JSON.stringify(out));
if (stale.length) console.log('stale (rerun without --check): ' + stale.join(', '));
if (bad.length) console.log(bad.length + ' bad samples' + (process.argv.includes('--all') ? ':\n' + bad.join('\n') : ', first: ' + bad.slice(0, 12).join(' | ')));
process.exit(stale.length || bad.length ? 1 : 0);
