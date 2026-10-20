// Checks the health V1a medical actions (bandage_wrap, alcohol_clean) with the AFL Rig core: palm off its target, wrist,
// forearm twist, and the palms against the torso box, with their props (player_props/) and the medical clips; and that each
// action's check.use_ticks is health/MedicalItem's use time. Run from the D worktree root.
const fs = require('fs'), path = require('path');
const root = process.cwd();
const core = require(path.join(root, 'tools/blockbench/afl_rig.js'));
const {tf} = core;
const A = path.join(root, 'src/main/resources/assets/apocalypse_firstlight');
const read = p => JSON.parse(fs.readFileSync(p, 'utf8'));
const rig = core.loadRig(read(path.join(A, 'rigs/afl_humanoid_v1.json')));
const poser = new core.Poser(rig, core.loadClips(rig, read(path.join(A, 'animations/player/locomotion.animation.json'))));
const clipFile = n => read(path.join(A, 'animations/player', n + '.animation.json'));
const clips = Object.assign(core.loadClips(rig, clipFile('item')), core.loadClips(rig, clipFile('medical')));
const propDef = id => { const p = path.join(A, 'player_props', id + '.json'); return fs.existsSync(p) ? read(p) : null; };
const java = fs.readFileSync(path.join(root, 'src/main/java/com/antaurora/apofirstlight/health/MedicalItem.java'), 'utf8');
const ticks = {bandage_wrap: +/BANDAGE_TICKS = (\d+)/.exec(java)[1], alcohol_clean: +/ALCOHOL_TICKS = (\d+)/.exec(java)[1]};
function outside(p, b) {
    const dx = Math.max(b[0] - p[0], p[0] - b[1]), dy = Math.max(b[2] - p[1], p[1] - b[3]), dz = Math.max(b[4] - p[2], p[2] - b[5]);
    const o = Math.hypot(Math.max(dx, 0), Math.max(dy, 0), Math.max(dz, 0));
    return o > 0 ? o : Math.max(dx, dy, dz);
}
const TORSO = [-4, 4, 0, 12, -2, 2];
const out = {};
for (const name of ['bandage_wrap', 'alcohol_clean']) {
    const json = read(path.join(A, 'player_actions/item', name + '.json'));
    const action = core.actionFromJson(json, propDef, k => clips[k] || null);
    if (json.check.use_ticks !== ticks[name]) throw new Error(name + ': use_ticks ' + json.check.use_ticks + ' but MedicalItem has ' + ticks[name]);
    const len = json.check.use_ticks / 20, bad = [];
    let wristMax = 0, twistMax = 0, offMax = 0, reachMax = 0, clearMin = Infinity, clearAt = '';
    for (const pitch of [0, -30, 30, 60]) for (const crouch of [false, true]) for (let t = 0; t <= len + 1e-6; t += 0.05) {
        const res = core.solveFrame(poser, {action, seconds: t, time: t, pitch, crouch});
        const ro = core.readouts(res);
        for (const side of ['right', 'left']) {
            const r = ro[side];
            if (r.mode !== 'ik' || r.w < 0.5) continue;
            wristMax = Math.max(wristMax, r.wrist); twistMax = Math.max(twistMax, r.twist); reachMax = Math.max(reachMax, r.reach || 0);
            if (r.w > 0.99) offMax = Math.max(offMax, r.off);
            if ((r.w > 0.99 && r.off > 0.6) || r.wrist > 50 || r.twist > 30) bad.push(`${t.toFixed(2)}/${pitch}${crouch ? 'c' : ''} ${side} off ${r.off.toFixed(2)} wrist ${r.wrist.toFixed(0)} twist ${r.twist.toFixed(0)}`);
        }
        // the palms (hand bone, 2 px above its end) against the torso
        const inv = tf.inv(res.pose.world[rig.chest]);
        for (const [hand, x] of [[rig.handR, -5], [rig.handL, 5]]) {
            const palm = res.pose.point(hand, [x, 24 - 11, 0]);
            const o = outside(tf.apply(inv, palm), TORSO);
            if (o < clearMin) { clearMin = o; clearAt = `${hand === rig.handR ? 'R' : 'L'} ${t.toFixed(2)}/${pitch}${crouch ? 'c' : ''}`; }
        }
    }
    out[name] = {offMax: +offMax.toFixed(2), wristMax: +wristMax.toFixed(0), twistMax: +twistMax.toFixed(0), reachMax: +reachMax.toFixed(2),
        palmClearMin: +clearMin.toFixed(2), clearAt, useTicks: json.check.use_ticks, bad: bad.length, firstBad: bad.slice(0, 10)};
}
console.log(JSON.stringify(out, null, 1));
