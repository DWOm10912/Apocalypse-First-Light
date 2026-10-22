// Earthwork A2 (docs/worldgen/earthwork_a2.md 6): writes the round point shovel's skeleton actions and clips, then checks
// them with the AFL Rig core (tools/blockbench/afl_rig.js) as the game poses them.
//   player_actions/item/round_point_shovel.json  the hold (two hands, carried across the body)
//   player_actions/tool/shovel_dig.json          dig, 1.1 s (interaction/ShovelAction DURATION 22 ticks, commit 16)
//   player_actions/tool/shovel_pour.json         pour, 0.8 s (16 ticks, commit 9)
//   player_actions/tool/shovel_scrape.json       scrape, 1.0 s (20 ticks, commit 14)
//   animations/player/tool.animation.json        tool.shovel_dig / _pour / _scrape (the body: hips, knees, chest)
// The blade works the "dig" anchor: the ground point client/skeleton/SkeletonActions#shovelAnchor gives, clamped to what
// these poses reach (0.55..0.8 ahead of the feet, 0.15 to the right, 0.2 below to 0.25 above them). The check samples every
// 0.05 s over that range, at view pitches 30..80 (and crouched for the scrape, a sneak): each palm on its handle point
// (off <= 1 px = 6 cm once the hand is fully on it: more only at the range's corners mid-move), wrist <= 50 deg, forearm
// twist <= 60 deg (the scrape's push to ground 0.25 above the feet at pitch 30 reaches 56).
// Run: node tools/author-shovel-actions-a2.cjs (write + check) | --check (check the files as they are, fail if stale)
const fs = require('fs'), path = require('path');
const root = path.resolve(__dirname, '..');
const core = require(path.join(root, 'tools/blockbench/afl_rig.js'));
const A = path.join(root, 'src/main/resources/assets/apocalypse_firstlight');
const read = p => JSON.parse(fs.readFileSync(p, 'utf8'));
const PX = 16 / 0.9375;
const CHECK = process.argv.includes('--check');

// ------------------------------------------------------------------ the body: squat (thigh forward a deg, ankle under
// the hip), hips tipped p, chest bent c (deg, + forward). Legs follow so the feet stay flat: thigh -a - p, shin a + b,
// foot -b with 6 sin a = 4 sin b (thigh 6 px, shin 4 px); the hips drop by what the bent legs lose.
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

// ------------------------------------------------------------------ the poses (solved with the rig core, 2026-10-21)
const HOLD = {frame: 'shoulder_r', point: 'grip', pos: [2, 6, -7], rot: [-20, 0, 45]};
const blade = (pos, rot) => ({frame: 'anchor:dig', point: 'blade', pos, rot});
// [arrives at s, body [squat, hips, chest], prop placement]
const STROKES = {
    dig: {length: 1.1, pitch: 45, about: 'Dig (interaction/ShovelAction DIG: 22 ticks, the ground and the blade change at 16 = 0.8 s): from the hold the blade comes up over the ground point (0.3 s), goes in with the hips down and the knees bent (0.55 s), the handle is levered back as the legs straighten (0.8 s, the scoop), the loaded blade comes up (0.95 s) and back into the hold.',
        keys: [[0.30, [0, 30, 20], blade([0, -3, 0], [-30, -5, 0])], [0.55, [38, 30, 20], blade([0, 1.5, 0], [-20, -5, 0])],
            [0.80, [0, 30, 30], blade([0, -0.5, 2], [-20, -15, 0])], [0.95, [0, 10, 10], blade([0, -6, 5], [-30, -5, 10])]]},
    pour: {length: 0.8, pitch: 40, about: 'Pour (interaction/ShovelAction POUR: 16 ticks, the load goes down at 9 = 0.45 s): the loaded blade is carried out over the ground point (0.25 s) and turned on its handle (0.45 s), so the load slides off its side, and back into the hold.',
        keys: [[0.25, [0, 10, 10], blade([0, -8, 0], [-40, -5, 0])], [0.45, [0, 20, 10], blade([0, -6, -1], [-25, 70, -10])]]},
    scrape: {length: 1.0, pitch: 50, about: 'Scrape (sneak; interaction/ShovelAction SCRAPE: 20 ticks, the ground changes at 14 = 0.7 s): crouched low, the blade goes down flat near the feet (0.3 s) and is pushed out along the ground (0.65 s), comes up (0.85 s) and back into the hold.',
        keys: [[0.30, [38, 20, 10], blade([0, 0.5, 3], [-20, -15, 0])], [0.65, [38, 30, 30], blade([0, 0.5, -3], [-30, -5, 0])],
            [0.85, [0, 10, 10], blade([0, -6, 3], [-30, -5, 10])]]},
};
const HANDS = {right: [{at: 0.0, target: 'prop:grip', blend: 0, roll: 0}], left: [{at: 0.0, target: 'prop:grip_end', blend: 0, roll: 0}]};
const key = (at, blend, p) => ({at: +at.toFixed(3), frame: p.frame, point: p.point, pos: p.pos, rot: p.rot, blend: +blend.toFixed(3)});

function strokeAction(name, s) {
    const keys = [key(0, 0, HOLD)];
    let last = 0.001;
    for (const [t, , p] of s.keys) { keys.push(key(last, t - last, p)); last = t; }
    keys.push(key(last, s.length - last, HOLD));
    return {
        format_version: 1,
        about: 'Round point shovel, ' + s.about + ' First and third person alike; the own first person sits on the posed eye (it goes down with the hips) and looks down to ' + s.pitch + ' deg at least. Written by tools/author-shovel-actions-a2.cjs. docs/worldgen/earthwork_a2.md 6',
        clip: 'tool.shovel_' + name, layer: 'action', mask: 'full', priority: 60, first_person: 'body', first_person_pitch: s.pitch,
        first_person_eye: true, blend: {in: 0.0, out: 0.2},
        prop: {id: 'round_point_shovel', keys}, hands: HANDS, events: [],
    };
}

const HOLD_ACTION = {
    format_version: 1,
    about: 'Round point shovel in the main hand, first and third person alike (Earthwork A2). Held two-handed across the body as a shovel is carried to the work: the left hand at the top of the handle at the left of the belly, the right down the handle at the right, the blade low ahead and out to the right. A stroke (dig, pour, scrape: tool/shovel_*) starts and ends in this hold. Written by tools/author-shovel-actions-a2.cjs. docs/worldgen/earthwork_a2.md 6',
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
for (const [name, s] of Object.entries(STROKES))
    tool.animations['tool.shovel_' + name] = clip(s.length, [[0, [0, 0, 0]], ...s.keys.map(([t, b]) => [t, b]), [s.length, [0, 0, 0]]]);
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
const rig = core.loadRig(read(path.join(A, 'rigs/afl_humanoid_v1.json')));
const poser = new core.Poser(rig, core.loadClips(rig, read(path.join(A, 'animations/player/locomotion.animation.json'))));
const clips = core.loadClips(rig, read(clipsFile));
const prop = read(path.join(A, 'player_props/round_point_shovel.json'));
const out = {}, bad = [];
for (const [f] of Object.entries(files)) {
    const name = path.basename(f, '.json'), json = read(f);
    const action = core.actionFromJson(json, id => (id === 'round_point_shovel' ? prop : null), k => clips[k] || null);
    const len = json.clip ? STROKES[name.slice(7)].length : 0;
    let offMax = 0, wristMax = 0, twistMax = 0, n = 0;
    const anchors = [];
    for (const d of [0.55, 0.68, 0.8]) for (const h of [-0.2, 0, 0.25]) anchors.push([-0.15 * PX, 24 - h * PX, -d * PX]);
    for (const pitch of [30, 45, 60, 80]) for (const crouch of name === 'shovel_scrape' ? [false, true] : [false]) for (const anchor of anchors)
        for (let t = 0; t <= len + 1e-6; t += 0.05) {
            const res = core.solveFrame(poser, {action, seconds: t, time: t, pitch, crouch, anchors: k => (k === 'dig' ? anchor : null)});
            const ro = core.readouts(res);
            for (const side of ['right', 'left']) {
                const r = ro[side];
                if (r.mode !== 'ik') { bad.push(name + ' ' + side + ' not on the handle at ' + t.toFixed(2)); continue; }
                n++;
                if (r.w > 0.99) offMax = Math.max(offMax, r.off);
                wristMax = Math.max(wristMax, r.wrist);
                twistMax = Math.max(twistMax, r.twist);
                if ((r.w > 0.99 && r.off > 1.0) || r.wrist > 50 || r.twist > 60)
                    bad.push(`${name} ${t.toFixed(2)} pitch ${pitch}${crouch ? ' crouched' : ''} d ${(-anchor[2] / PX).toFixed(2)} h ${((24 - anchor[1]) / PX).toFixed(2)} ${side}: off ${r.off.toFixed(2)} wrist ${r.wrist.toFixed(0)} twist ${r.twist.toFixed(0)}`);
            }
            if (!json.clip) break;
        }
    out[name] = {samples: n, offMax: +offMax.toFixed(2), wristMax: +wristMax.toFixed(0), twistMax: +twistMax.toFixed(0)};
}
console.log(JSON.stringify(out));
if (stale.length) console.log('stale (rerun without --check): ' + stale.join(', '));
if (bad.length) console.log(bad.length + ' bad samples' + (process.argv.includes('--all') ? ':\n' + bad.join('\n') : ', first: ' + bad.slice(0, 12).join(' | ')));
process.exit(stale.length || bad.length ? 1 : 0);
