// Shared helpers for authoring the medical actions with the AFL Rig core.
const fs = require('fs'), path = require('path');
const root = path.resolve(__dirname, '../..');   // the worktree this folder is in
const core = require(path.join(root, 'tools/blockbench/afl_rig.js'));
const {v3, q4, tf, rad, deg} = core;
const A = path.join(root, 'src/main/resources/assets/apocalypse_firstlight');
const read = p => JSON.parse(fs.readFileSync(p, 'utf8'));
// the rig the clips are made on (AFL_RIG: a rigs/ file name; author_ground_sit_b.cjs sets afl_block_mesh_v2)
const RIG = process.env.AFL_RIG || 'afl_humanoid_v1';
const rig = core.loadRig(read(path.join(A, 'rigs/' + RIG + '.json')));
const poser = new core.Poser(rig, core.loadClips(rig, read(path.join(A, 'animations/player/locomotion.animation.json'))));
const propDef = id => read(path.join(A, 'player_props', id + '.json'));

/** Rotation taking a to b (unit vectors), shortest. */
function between(a, b) {
    a = v3.norm(a); b = v3.norm(b);
    const d = v3.dot(a, b);
    if (d < -0.999999) { const ax = Math.abs(a[0]) < 0.9 ? v3.norm(v3.cross(a, [1, 0, 0])) : v3.norm(v3.cross(a, [0, 1, 0])); return [ax[0], ax[1], ax[2], 0]; }
    const c = v3.cross(a, b);
    return q4.norm([c[0], c[1], c[2], 1 + d]);
}

/**
 * Arm FK from directions in the chest frame (Java px, y down, front -z): u the upper arm (shoulder to elbow), f the forearm
 * (elbow to wrist), h the hand (wrist to cap, default f), twist deg about the forearm. The elbow hinges about the upper arm's
 * local x. Returns local quats {upper, fore, hand}.
 */
function armFk(u, f, h, twist) {
    u = v3.norm(u); f = v3.norm(f); h = h ? v3.norm(h) : f;
    const cosA = Math.max(-1, Math.min(1, v3.dot(u, f)));
    let perp = v3.sub(f, v3.mul(u, cosA));
    let upper;
    if (v3.len(perp) < 1e-6) upper = between([0, 1, 0], u);
    else {
        const cz = v3.mul(v3.norm(perp), -1), cy = u, cx = v3.cross(cy, cz);
        upper = q4.fromBasis(cx, cy, cz);
    }
    const alpha = Math.acos(cosA);
    let fore = q4.x(-alpha);
    if (twist) fore = q4.mul(fore, q4.y(rad(twist)));
    // hand: from the forearm's world turn, bring its y onto h
    const foreW = q4.mul(upper, fore);
    const yW = q4.apply(foreW, [0, 1, 0]);
    const handW = q4.mul(between(yW, h), foreW);
    const hand = q4.mul(q4.conj ? q4.conj(foreW) : q4.inv(foreW), handW);
    return {upper, fore, hand};
}

/** A local quat as clip file numbers (the plugin's clip: q = zyx(rad v2, rad v1, rad v0)). */
const fileEuler = q => q4.eulerZYX(q).map(deg).map(x => +x.toFixed(3));

module.exports = {fs, path, root, core, v3, q4, tf, rad, deg, A, read, RIG, rig, poser, propDef, between, armFk, fileEuler};
