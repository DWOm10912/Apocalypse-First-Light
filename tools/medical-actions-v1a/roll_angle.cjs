// The bandage roll's middle in the left forearm's rest frame over the use: its angle round the arm (atan2(z, x - 6), 0 = +x the
// outside, 90 = +z) and its place along the arm (px back from the arm's end, y 12), against BandageWrapRenderer's time-based turn.
const L = require('./lib.cjs');
const {core, v3, tf, rig, poser, A, read, path} = L;
const json = read(path.join(A, 'player_actions/item/bandage_wrap.json'));
const clips = core.loadClips(rig, read(path.join(A, 'animations/player/medical.animation.json')));
const prop = read(path.join(A, 'player_props/bandage.json'));
const act = core.actionFromJson(json, id => (id === 'bandage' ? prop : null), k => clips[k] || null);
const centre = core.propLocal(prop, 'center');
let prev = null, unwrapped = 0;
for (let t = 0.5; t <= 3.3; t += 0.05) {
    const res = core.solveFrame(poser, {action: act, seconds: t, time: t, pitch: 0});
    const placed = res.props[0];
    if (!placed) continue;
    const posed = tf.apply(placed, centre);
    const rest = tf.apply(tf.inv(res.pose.world[rig.forearmL]), posed);
    const ang = Math.atan2(rest[2], rest[0] - 6) * 180 / Math.PI;
    if (prev !== null) { let d = ang - prev; while (d > 180) d -= 360; while (d < -180) d += 360; unwrapped += d; } else unwrapped = ang;
    prev = ang;
    const cover = Math.min(1, Math.max(0, (t - 0.6) / 2.5));
    const leadDeg = ((cover * 3) % 1) * 360;
    console.log(t.toFixed(2), 'roll', ang.toFixed(0).padStart(5), 'unwrapped', unwrapped.toFixed(0).padStart(6), 'r', Math.hypot(rest[2], rest[0] - 6).toFixed(1), 'back', (12 - rest[1]).toFixed(1), '| renderer lead', leadDeg.toFixed(0));
}
