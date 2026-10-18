// AFL Rig Blockbench plugin check (docs/dev/afl_rig_blockbench_plugin_v1.md): the plugin's port of the poser against the
// game's Java code, plus the plugin's own conversions.
//
// 1. ./gradlew aflRigParity  (writes build/afl_rig_parity.json from the real HumanoidPoser / ActionPose)
// 2. node tools/blockbench/afl_rig_check.mjs
//
// Every scene of the Java file is posed again by tools/blockbench/afl_rig.js; bone and prop matrices are compared on test
// points (px). Then Euler / basis round trips and the prop key <-> Blockbench keyframe mapping.
import {createRequire} from 'module';
import fs from 'fs';
import path from 'path';
import {fileURLToPath} from 'url';

const require = createRequire(import.meta.url);
const core = require('./afl_rig.js');
const here = path.dirname(fileURLToPath(import.meta.url));
const repo = path.resolve(here, '..', '..');
const assets = path.join(repo, 'src/main/resources/assets/apocalypse_firstlight');
// the phase 2 test action and its props live in the test resources only
const testAssets = path.join(repo, 'src/test/resources/assets/apocalypse_firstlight');
const read = p => JSON.parse(fs.readFileSync(fs.existsSync(path.join(assets, p)) ? path.join(assets, p) : path.join(testAssets, p), 'utf8'));
const {v3, q4, tf} = core;

let failures = 0;
const fail = msg => { failures++; console.log('  FAIL ' + msg); };

// ------------------------------------------------ parity with Java
const rig = core.loadRig(read('rigs/afl_humanoid_v1.json'));
const poser = new core.Poser(rig, core.loadClips(rig, read('animations/player/locomotion.animation.json')));
const groups = {};
const clipFor = key => {
    const g = key.split('.')[0];
    if (!(g in groups)) {
        const file = path.join(assets, 'animations/player', g + '.animation.json');
        groups[g] = fs.existsSync(file) ? core.loadClips(rig, JSON.parse(fs.readFileSync(file, 'utf8'))) : {};
    }
    return groups[g][key] || null;
};
const actionCache = {};
// the whole file: a gun prop's grips and carry preview too
const defFor = id => read('player_props/' + id + '.json');
const actionOf = id => {
    if (!actionCache[id]) actionCache[id] = core.actionFromJson(read('player_actions/' + id + '.json'), defFor, clipFor);
    return actionCache[id];
};

const parityFile = path.join(repo, 'build/afl_rig_parity.json');
if (!fs.existsSync(parityFile)) {
    console.log('no ' + parityFile + ': run ./gradlew aflRigParity first');
    process.exit(1);
}
const frames = JSON.parse(fs.readFileSync(parityFile, 'utf8')).frames;
const mat = (a, p) => [a[0] * p[0] + a[4] * p[1] + a[8] * p[2] + a[12], a[1] * p[0] + a[5] * p[1] + a[9] * p[2] + a[13],
    a[2] * p[0] + a[6] * p[1] + a[10] * p[2] + a[14]];
const probes = c => [c, v3.add(c, [4, 0, 0]), v3.add(c, [0, 4, 0]), v3.add(c, [0, 0, 4])];
let worstBone = 0, worstProp = 0, props = 0;
const perAction = {};
for (const f of frames) {
    const res = core.solveFrame(poser, {slim: f.slim, crouch: f.crouch, pitch: f.pitch, action: actionOf(f.action), seconds: f.t, fp: !!f.fp});
    let err = 0;
    for (let b = 0; b < rig.size; b++) {
        for (const p of probes(rig.pivot[b])) err = Math.max(err, v3.dist(tf.apply(res.pose.world[b], p), mat(f.world[b], p)));
    }
    let perr = 0;
    if ((f.prop === null) !== (res.prop === null)) fail(`${f.action} t=${f.t}: prop ${f.prop ? 'java only' : 'plugin only'}`);
    f.props.forEach((jp, i) => {
        const pp = res.props[i] || null;
        if ((jp === null) !== (pp === null)) return fail(`${f.action} t=${f.t}: prop ${i} ${jp ? 'java only' : 'plugin only'}`);
        if (f.hidden[i] !== !!(res.hidden[i])) fail(`${f.action} t=${f.t}: prop ${i} hidden ${f.hidden[i]} in java`);
        if (!jp) return;
        props++;
        for (const p of probes([0, 0, 0])) perr = Math.max(perr, v3.dist(tf.apply(pp, p), mat(jp, p)));
    });
    worstBone = Math.max(worstBone, err);
    worstProp = Math.max(worstProp, perr);
    const a = perAction[f.action] || (perAction[f.action] = {n: 0, bone: 0, prop: 0});
    a.n++;
    a.bone = Math.max(a.bone, err);
    a.prop = Math.max(a.prop, perr);
    if (err > 0.01 || perr > 0.01) fail(`${f.action} t=${f.t} pitch=${f.pitch} slim=${f.slim} crouch=${f.crouch}${f.fp ? ' fp' : ''}: bones off ${err.toFixed(4)} px, prop ${perr.toFixed(4)} px`);
}
console.log(`parity: ${frames.length} scenes, ${props} prop placements; worst bone point ${worstBone.toFixed(5)} px, worst prop point ${worstProp.toFixed(5)} px`);
for (const id in perAction) {
    const a = perAction[id];
    console.log(`  ${id.padEnd(24)} ${String(a.n).padStart(3)} scenes  bones ${a.bone.toFixed(5)} px  prop ${a.prop.toFixed(5)} px`);
}

// ------------------------------------------------ conversions
const rnd = () => (Math.random() * 2 - 1) * Math.PI;
const same = (a, b) => Math.abs(Math.abs(q4.dot(q4.norm(a), q4.norm(b))) - 1) < 1e-9;
let euler = 0, basis = 0;
for (let i = 0; i < 2000; i++) {
    const q = q4.norm([Math.random() - 0.5, Math.random() - 0.5, Math.random() - 0.5, Math.random() - 0.5]);
    const z = q4.eulerZYX(q), x = q4.eulerXYZ(q);
    if (!same(q4.zyx(z[2], z[1], z[0]), q)) euler++;
    if (!same(q4.xyz(x[0], x[1], x[2]), q)) euler++;
    const cols = [[1, 0, 0], [0, 1, 0], [0, 0, 1]].map(e => q4.apply(q, e));
    if (!same(q4.fromBasis(cols[0], cols[1], cols[2]), q)) basis++;
}
console.log(`euler round trips: ${euler ? euler + ' FAIL' : 'ok'}; basis -> quaternion: ${basis ? basis + ' FAIL' : 'ok'}`);
if (euler || basis) failures++;

// a Blockbench keyframe of the prop group <-> the runtime PropKey: the same placement either way (prop: frames with a
// random rest placement of the prop they ride on)
let keyErr = 0;
const FRAMES = ['chest', 'root', 'shoulder_r', 'shoulder_l', 'bone:thigh_r', 'socket:holster', 'socket:armpit_l', 'prop:x'];
for (let i = 0; i < 800; i++) {
    const G = [Math.random() * 20 - 10, Math.random() * 30, Math.random() * 20 - 10];
    const e = v3.add(G, [Math.random() * 8 - 4, Math.random() * 8 - 4, Math.random() * 8 - 4]);
    const frame = FRAMES[i % FRAMES.length];
    const F = frame.startsWith('prop:') ? {q: q4.norm([Math.random() - 0.5, Math.random() - 0.5, Math.random() - 0.5, Math.random() - 0.5]),
        t: [Math.random() * 20 - 10, Math.random() * 20, Math.random() * 20 - 10]} : undefined;
    const rot = [deg(rnd()), deg(rnd() / 2), deg(rnd())], delta = [Math.random() * 10 - 5, Math.random() * 10 - 5, Math.random() * 10 - 5];
    const key = core.bbToPropKey(rig, rot, delta, G, e, frame, F);
    const back = core.propKeyToBB(rig, {pos: key.pos, rot: key.rot, frame}, G, e, F);
    const R1 = q4.zyx(core.rad(rot[2]), core.rad(rot[1]), core.rad(rot[0])), R2 = q4.zyx(core.rad(back.rot[2]), core.rad(back.rot[1]), core.rad(back.rot[0]));
    // the placement of a few content points under both keyframes
    for (const p of [e, G, v3.add(G, [3, -2, 1])]) {
        const a = v3.add(v3.add(G, delta), q4.apply(R1, v3.sub(p, G))), b = v3.add(v3.add(G, back.delta), q4.apply(R2, v3.sub(p, G)));
        keyErr = Math.max(keyErr, v3.dist(a, b));
    }
}
function deg(r) { return r * 180 / Math.PI; }
console.log(`prop key <-> keyframe: worst ${keyErr.toExponential(2)} px`);
if (keyErr > 1e-6) failures++;

// the runtime placement of a converted keyframe at rest equals the Blockbench group's own display at rest
{
    const prop = read('player_props/crowbar.json');
    const def = {scale: prop.scale, points: prop.points}, k = 16 * def.scale;
    const D = [3, 8, -5], hold = 'grip', G = v3.add(D, v3.mul(def.points[hold], k));
    const rot = [20, -35, 70], delta = [1.5, -2, 4];
    let err = 0;
    for (const frame of ['chest', 'root', 'shoulder_r', 'bone:thigh_r', 'socket:holster']) {
        const key = core.bbToPropKey(rig, rot, delta, G, G, frame);
        const action = {mask: 'arms', blendIn: 0, blendOut: 0, clip: null, right: [], left: [],
            props: [{id: 'crowbar', def, keys: [{at: 0, frame, point: hold, pos: key.pos, rot: key.rot, blend: 0}], openFrom: NaN}]};
        // the rest pose (no clips, identity bones): what Blockbench's own display shows
        const placed = core.restPlacement(rig, action, 0, 0);
        const R = q4.zyx(core.rad(rot[2]), core.rad(rot[1]), core.rad(rot[0]));
        for (const name in def.points) {
            const e = v3.add(D, v3.mul(def.points[name], k));
            const bb = v3.add(v3.add(G, delta), q4.apply(R, v3.sub(e, G)));   // Blockbench's own display of the content point
            const local = core.propLocal(def, name);   // = Rx180 (e - D)
            err = Math.max(err, v3.dist(core.j2b(tf.apply(placed, local)), bb));
        }
    }
    console.log(`prop keyframe shown at rest = Blockbench's own display: worst ${err.toExponential(2)} px`);
    if (err > 1e-6) failures++;
}

console.log(failures ? `${failures} FAILED` : 'all ok');
process.exit(failures ? 1 : 0);
