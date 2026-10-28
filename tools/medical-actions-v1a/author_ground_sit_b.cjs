// AFL Block-Mesh V2 (B5) sitting on the ground (docs/dev/block_mesh_v2_b_ingame.md 17.5; user 2026-10-26 "你看看之前旧版本的是
// 怎么做的坐下动画"): vanilla's sitting keys (pose_lib.cjs SIT_DOWN, SITTING, STAND_UP, the ones author_splint_v2.cjs makes
// body.sit_down / body.sit / body.stand_up from) solved on the B rig: its 15 px legs, the hips as high over the ground in
// proportion (sat: on it), the feet out in proportion, a palm by a knee with that knee, the palms by the rig's arm IK baked
// to FK. Writes animations/player/body_b.animation.json (body_b.sit_down, body_b.sit, body_b.stand_up), which
// client/skeleton/GroundSitClient plays for B instead of vanilla's (B's plantFeet off for them).
//   node tools/medical-actions-v1a/author_ground_sit_b.cjs [--write]
process.env.AFL_RIG = 'afl_block_mesh_v2';
const L = require('./lib.cjs');
const P = require('./pose_lib.cjs');
const {path, A, v3, rig} = L;
const {fullKey, clipOf, SIT_LEN, STAND_LEN, SIT_DOWN, SITTING, STAND_UP, REST, GEO, OWN} = P;
if (OWN) throw new Error('author_ground_sit_b: the B rig did not load');

// getting up: vanilla's keys put both hands on the right knee; on B that knee comes up against the chest (the shin longer
// than the thigh), so the left arm crossing to it lifted its elbow by the head (the 2026-10-26 renders). B's left hand
// pushes on its own left thigh by the knee instead.
const onLeftThigh = b => v3.add(b.knee.L, [0.4, -2.6, 1.2]);
const STAND_UP_B = STAND_UP.map(k => (k.t === 0.2 || k.t === 0.5 ? Object.assign({}, k, {palmL: onLeftThigh}) : k));
const sitDown = clipOf(SIT_DOWN, SIT_LEN, fullKey), sitting = clipOf(SITTING, 2, fullKey), standUp = clipOf(STAND_UP_B, STAND_LEN, fullKey);
console.log('legs', JSON.stringify(GEO));
console.log('sit_down (palm off / wrist deg)\n' + sitDown.rows.join('\n') + '\nsit\n' + sitting.rows.join('\n') + '\nstand_up\n' + standUp.rows.join('\n'));
const rest = fullKey(REST);
const eye = v3.add(rest.res.pose.point(rig.head, rig.pivot[rig.head]), v3.sub(rig.sockets.eye.pos, rig.pivot[rig.head]));
console.log('sat: eye over the ground (blocks at 15/16)', ((24 - eye[1]) * 0.9375 / 16).toFixed(3), ' knee R', rest.body.knee.R.map(x => x.toFixed(2)).join(', '),
    ' knee L', rest.body.knee.L.map(x => x.toFixed(2)).join(', '));

if (process.argv.includes('--write')) {
    const rename = c => c;   // the bones are the same names on both rigs
    const out = {format_version: '1.8.0', afl_rig: {about: 'Written by tools/medical-actions-v1a/author_ground_sit_b.cjs: vanilla\'s sitting keys solved on the Block-Mesh V2 rig (docs/dev/block_mesh_v2_b_ingame.md 17.5). Played for B by client/skeleton/GroundSitClient.'},
        animations: {'body_b.sit_down': rename(sitDown.clip), 'body_b.sit': rename(sitting.clip), 'body_b.stand_up': rename(standUp.clip)}};
    L.fs.writeFileSync(path.join(A, 'animations/player/body_b.animation.json'), JSON.stringify(out, null, 2) + '\n');
    console.log('written');
}
