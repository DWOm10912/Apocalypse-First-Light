// Narrow P9 source/runtime fix: keep authored clip times and motion except the
// requested empty-idle slide lockback; normalize only existing sound marker IDs.
import assert from 'node:assert/strict';
import { readFileSync, writeFileSync } from 'node:fs';

const sourcePath = 'src/main/blockbench/p9_01.bbmodel';
const animationPath = 'src/main/resources/assets/apocalypse_firstlight/animations/p9_01.animation.json';
const source = JSON.parse(readFileSync(sourcePath));
const runtime = JSON.parse(readFileSync(animationPath));
const clip = name => source.animations.find(a => a.name === name);
const runtimeClip = name => runtime.animations[name];
const sourceTrack = (name, bone) => Object.values(clip(name).animators)
    .find(animator => animator.name === bone);

// Earlier source/runtime shoot rebase already retained the static grip and
// authored recoil. Refuse to alter it further if that precondition changes.
for (const bone of ['righthand', 'lefthand', 'righthand_1', 'lefthand_1',
    'righthand_pos', 'lefthand_pos']) {
    assert.deepEqual(runtimeClip('shoot').bones[bone], runtimeClip('static_idle').bones[bone],
        `shoot ${bone} must use static grip`);
}
assert.equal(clip('shoot').length, 0.6);
assert.equal(runtimeClip('shoot').animation_length, 0.6);

const empty = clip('empty_idle'), emptyRuntime = runtimeClip('empty_idle');
const inspectSource = sourceTrack('inspect_empty', 'slide');
const emptySource = sourceTrack('empty_idle', 'slide');
for (const channel of ['rotation', 'position']) {
    const target = emptySource.keyframes.find(k => k.channel === channel && k.time === 0);
    const baseline = inspectSource.keyframes.find(k => k.channel === channel && k.time === 0);
    assert.ok(target && baseline, `missing ${channel} frame zero`);
    target.data_points = structuredClone(baseline.data_points);
    const runtimeChannel = runtimeClip('inspect_empty').bones.slide[channel];
    assert.ok(runtimeChannel['0.0']?.vector, `missing runtime inspect ${channel} zero`);
    emptyRuntime.bones.slide[channel] = { vector: [...runtimeChannel['0.0'].vector] };
}
empty.length = 0.04167; empty.loop = 'hold';
emptyRuntime.animation_length = 0.04167;
emptyRuntime.loop = 'hold_on_last_frame';

const events = {
    p9_01_fire: 'apocalypse_firstlight:p9_01_fire',
    p9_01_mag_out: 'apocalypse_firstlight:p9_01_magazine_out',
    p9_01_mag_in: 'apocalypse_firstlight:p9_01_magazine_in',
    p9_01_slide_action: 'apocalypse_firstlight:p9_01_slide_action',
    p9_01_inspect: 'apocalypse_firstlight:p9_01_inspect',
    p9_01_inspect_empty: 'apocalypse_firstlight:p9_01_inspect'
};
const names = ['static_idle', 'empty_idle', 'shoot', 'reload_tactical',
    'reload_empty', 'draw', 'put_away', 'inspect', 'inspect_empty'];
for (const name of names) {
    const sourceMarkers = Object.values(clip(name).animators)
        .flatMap(animator => (animator.keyframes ?? [])
            .filter(key => key.channel === 'sound')
            .flatMap(key => key.data_points.map(point => ({ key, point }))));
    const runtimeMarkers = Object.entries(runtimeClip(name).sound_effects ?? {});
    assert.equal(sourceMarkers.length, runtimeMarkers.length, `${name} marker count changed`);
    for (const { key, point } of sourceMarkers) {
        assert.ok(events[point.effect], `${name} unmapped source sound ${point.effect}`);
        assert.ok(runtimeMarkers.some(([time]) => Math.abs(Number(time) - key.time) < 1e-5),
            `${name} source/runtime sound timing changed`);
        point.effect = events[point.effect];
    }
    for (const [time, marker] of runtimeMarkers) {
        assert.ok(events[marker.effect], `${name} unmapped runtime sound ${marker.effect}@${time}`);
        marker.effect = events[marker.effect];
    }
}
writeFileSync(sourcePath, JSON.stringify(source));
writeFileSync(animationPath, JSON.stringify(runtime, null, '\t') + '\n');
console.log('P9 empty idle locked to inspect_empty frame zero; source/runtime markers normalized');
