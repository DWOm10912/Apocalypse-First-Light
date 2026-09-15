import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { execFileSync } from 'node:child_process';

const source = JSON.parse(readFileSync('src/main/blockbench/p9_01.bbmodel', 'utf8'));
const geo = JSON.parse(readFileSync('src/main/resources/assets/apocalypse_firstlight/geo/p9_01.geo.json', 'utf8'));
const clips = JSON.parse(readFileSync('src/main/resources/assets/apocalypse_firstlight/animations/p9_01.animation.json', 'utf8')).animations;
const old = JSON.parse(execFileSync('git', ['show', 'HEAD:src/main/resources/assets/apocalypse_firstlight/geo/p9_01.geo.json']));
const bones = new Map(geo['minecraft:geometry'][0].bones.map(b => [b.name, b]));
const oldBones = new Map(old['minecraft:geometry'][0].bones.map(b => [b.name, b]));
const groups = new Map(source.groups.map(g => [g.name, g]));
const ratio = .74, shift = [-2.2015, -2.28382, -3.74021];
for (const [motion, anchor] of [['right_hand_motion', 'right_hand_anchor'],
    ['left_hand_motion', 'left_hand_anchor']]) {
    const m = bones.get(motion), a = bones.get(anchor);
    assert.ok(m && a && groups.has(motion) && groups.has(anchor));
    assert.equal(m.parent, 'gun');
    assert.equal(a.parent, motion);
    assert.ok(!m.cubes?.length && !a.cubes?.length);
    for (const bone of [m, a]) {
        const original = oldBones.get(bone.name);
        assert.ok(original);
        assert.deepEqual(bone.rotation ?? [0, 0, 0], original.rotation ?? [0, 0, 0]);
        assert.ok(bone.pivot.every((n, i) => Math.abs(n - (ratio * original.pivot[i] + shift[i])) < 1e-5));
    }
}
assert.equal(source.elements.filter(e => e.export === false && e.name?.includes('arm')).length >= 4, true);
const renderer = readFileSync('src/main/java/com/antaurora/apofirstlight/weapon/client/P901Renderer.java', 'utf8');
const handLayer = readFileSync('src/main/java/com/antaurora/apofirstlight/weapon/client/P901HandLayer.java', 'utf8');
assert.match(renderer, /"right_hand_anchor", "left_hand_anchor"/);
assert.match(handLayer, /NativePlayerArmRenderer\.render\(locator, right, buffers, light, overlay\)/);
assert.doesNotMatch(handLayer, /renderAtModelScale|righthand_1|lefthand_1|locator\.scale/);
const expected = ['static_idle', 'empty_idle', 'shoot', 'reload_tactical', 'reload_empty',
    'draw', 'put_away', 'inspect', 'inspect_empty'];
assert.deepEqual(Object.keys(clips).sort(), [...expected].sort());
for (const clipName of expected) {
    const sourceClip = source.animations.find(a => a.name === clipName);
    const runtimeClip = clips[clipName];
    assert.ok(sourceClip && runtimeClip);
    for (const motion of ['right_hand_motion', 'left_hand_motion']) {
        const track = runtimeClip.bones[motion];
        const sourceTrack = sourceClip.animators[groups.get(motion).uuid];
        assert.ok(track?.position && track.rotation && sourceTrack);
        for (const channel of ['position', 'rotation']) {
            const runtimeKeys = track[channel].vector
                ? [[0, track[channel].vector]]
                : Object.entries(track[channel]).map(([t, key]) => [Number(t), key.vector]);
            const sourceKeys = sourceTrack.keyframes.filter(key => key.channel === channel);
            assert.equal(sourceKeys.length, runtimeKeys.length, `${clipName}/${motion}/${channel} source/runtime count`);
            for (const [time, vector] of runtimeKeys) {
                const key = sourceKeys.find(k => k.time === time);
                assert.ok(key, `${clipName}/${motion}/${channel}/${time} source key missing`);
                const exported = ['x', 'y', 'z'].map((axis, i) => {
                    const n = Number(key.data_points[0][axis]);
                    return i === 0 || (channel === 'rotation' && i === 1) ? -n : n;
                });
                assert.ok(vector.every((n, i) => Math.abs(n - exported[i]) < 1e-5));
            }
        }
    }
    // No retarget channel may be orphaned. Existing artist orphan tracks are
    // retained verbatim and audited separately, not silently repurposed.
    for (const motion of ['right_hand_motion', 'left_hand_motion']) assert.ok(bones.has(motion));
}
for (const name of ['gun', 'frame', 'slide', 'magazine', 'muzzle_anchor', 'sight_anchor',
    'ejection_anchor', 'camera', 'righthand', 'lefthand']) assert.ok(bones.has(name));
const knownOrphans = new Set(['barrel', 'barrel2', 'bullet2', 'bullet_in_barrel', 'group2', 'slide_1']);
const orphanTracks = [...new Set(Object.values(clips).flatMap(clip => Object.keys(clip.bones)))]
    .filter(name => !bones.has(name));
assert.ok(orphanTracks.every(name => knownOrphans.has(name)), `new orphan tracks: ${orphanTracks}`);
console.log(JSON.stringify({ pass: true, sourceGroups: source.groups.length,
    runtimeBones: bones.size, clips: expected.length, knownArtistOrphans: orphanTracks }, null, 2));
