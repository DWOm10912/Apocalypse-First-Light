import assert from 'node:assert/strict';
import { readFileSync, writeFileSync } from 'node:fs';
import { randomUUID } from 'node:crypto';

// One-time mechanical source/runtime synchronization. The artist's recoil,
// slide and camera timing remain intact; only their base pose changes.
const sourcePath = new URL('../src/main/blockbench/p9_01.bbmodel', import.meta.url);
const runtimePath = new URL('../src/main/resources/assets/apocalypse_firstlight/animations/p9_01.animation.json', import.meta.url);
const sourceText = readFileSync(sourcePath, 'utf8');
const runtimeText = readFileSync(runtimePath, 'utf8');
const source = JSON.parse(sourceText);
const runtime = JSON.parse(runtimeText);
const sourceIdle = source.animations.find(animation => animation.name === 'static_idle');
const sourceShoot = source.animations.find(animation => animation.name === 'shoot');
const runtimeIdle = runtime.animations.static_idle;
const runtimeShoot = runtime.animations.shoot;
assert.ok(sourceIdle && sourceShoot && runtimeIdle && runtimeShoot);
assert.equal(sourceShoot.length, 0.6);
assert.equal(runtimeShoot.animation_length, 0.6);
assert.ok(!runtimeShoot.bones.righthand && !runtimeShoot.bones.lefthand,
    'shoot is already based on static_idle; refusing a second offset');

const clone = value => structuredClone(value);
const baseVector = channel => channel.vector;
const add = (vector, base) => vector.map((component, index) =>
    Math.round((Number(component) + Number(base[index])) * 1e6) / 1e6);
const additiveChannels = new Set(['root.rotation', 'slide.position', 'camera.rotation']);

for (const [bone, idleChannels] of Object.entries(runtimeIdle.bones)) {
    const shootChannels = runtimeShoot.bones[bone] ??= {};
    for (const [channel, idleChannel] of Object.entries(idleChannels)) {
        if (!(channel in shootChannels)) {
            shootChannels[channel] = clone(idleChannel);
        } else if (additiveChannels.has(`${bone}.${channel}`)) {
            const base = baseVector(idleChannel);
            assert.ok(Array.isArray(base), `${bone}.${channel} static base missing`);
            for (const keyframe of Object.values(shootChannels[channel]))
                keyframe.vector = add(keyframe.vector, base);
        }
    }
}

const sourceByName = animation => new Map(Object.entries(animation.animators)
    .filter(([, animator]) => animator.name && animator.keyframes?.length)
    .map(([id, animator]) => [animator.name, { id, animator }]));
const idleAnimators = sourceByName(sourceIdle);
const shootAnimators = sourceByName(sourceShoot);
for (const [bone, { id, animator: idleAnimator }] of idleAnimators) {
    if (bone === '效果') continue;
    const active = shootAnimators.get(bone);
    if (!active) {
        const copied = clone(idleAnimator);
        copied.keyframes.forEach(keyframe => keyframe.uuid = randomUUID());
        sourceShoot.animators[id] = copied;
        continue;
    }
    const existingChannels = new Set(active.animator.keyframes.map(keyframe => keyframe.channel));
    for (const idleFrame of idleAnimator.keyframes) {
        if (existingChannels.has(idleFrame.channel)) continue;
        const copied = clone(idleFrame);
        copied.uuid = randomUUID();
        active.animator.keyframes.push(copied);
    }
    for (const channel of ['rotation', 'position']) {
        if (!additiveChannels.has(`${bone}.${channel}`)) continue;
        const baseFrame = idleAnimator.keyframes.find(keyframe => keyframe.channel === channel);
        assert.ok(baseFrame && baseFrame.data_points.length === 1);
        const base = ['x', 'y', 'z'].map(axis => Number(baseFrame.data_points[0][axis]));
        for (const frame of active.animator.keyframes.filter(keyframe => keyframe.channel === channel))
            for (const point of frame.data_points) {
                const result = add(['x', 'y', 'z'].map(axis => point[axis]), base);
                ['x', 'y', 'z'].forEach((axis, index) => point[axis] = String(result[index]));
            }
    }
}

// Preserve formatting and byte identity outside the runtime shoot member.
const marker = '\t\t"shoot": ';
const member = runtimeText.indexOf(marker);
assert.ok(member >= 0);
const opening = runtimeText.indexOf('{', member + marker.length);
let depth = 0, quoted = false, escape = false, closing = -1;
for (let index = opening; index < runtimeText.length; index++) {
    const char = runtimeText[index];
    if (quoted) {
        if (escape) escape = false;
        else if (char === '\\') escape = true;
        else if (char === '"') quoted = false;
    } else if (char === '"') quoted = true;
    else if (char === '{') depth++;
    else if (char === '}' && --depth === 0) { closing = index; break; }
}
assert.ok(closing > opening);
const formatted = JSON.stringify(runtimeShoot, null, '\t').split('\n')
    .map((line, index) => index === 0 ? line : `\t\t${line}`).join('\n');
const nextRuntime = runtimeText.slice(0, opening) + formatted + runtimeText.slice(closing + 1);
assert.deepEqual(JSON.parse(nextRuntime).animations.shoot, runtimeShoot);
writeFileSync(sourcePath, JSON.stringify(source));
writeFileSync(runtimePath, nextRuntime);
console.log('P9 shoot now inherits static_idle grip and keeps authored recoil timing');
