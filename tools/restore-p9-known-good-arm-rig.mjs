// One-time, P9-only source/runtime retarget. The supplied old source is byte-identical
// to HEAD's known-good P9 source; the old locator frame, not the artist proxy, wins.
import assert from 'node:assert/strict';
import { createHash, randomUUID } from 'node:crypto';
import { readFileSync, writeFileSync } from 'node:fs';
import { execFileSync } from 'node:child_process';

const sourcePath = 'src/main/blockbench/p9_01.bbmodel';
const geoPath = 'src/main/resources/assets/apocalypse_firstlight/geo/p9_01.geo.json';
const animationPath = 'src/main/resources/assets/apocalypse_firstlight/animations/p9_01.animation.json';
const oldBytes = readFileSync('E:/Download/p9_01.bbmodel');
const committed = execFileSync('git', ['show', 'HEAD:src/main/blockbench/p9_01.bbmodel'], { maxBuffer: 8 * 1024 * 1024 });
assert.equal(createHash('sha256').update(oldBytes).digest('hex'),
    createHash('sha256').update(committed).digest('hex'), 'supplied model is not known-good HEAD');
const oldSource = JSON.parse(oldBytes);
const source = JSON.parse(readFileSync(sourcePath, 'utf8'));
const geoText = readFileSync(geoPath, 'utf8');
const animationText = readFileSync(animationPath, 'utf8');
const geo = JSON.parse(geoText);
const animations = JSON.parse(animationText);
const bones = geo['minecraft:geometry'][0].bones;
const byBone = new Map(bones.map(b => [b.name, b]));
const oldGeo = JSON.parse(execFileSync('git', ['show', 'HEAD:src/main/resources/assets/apocalypse_firstlight/geo/p9_01.geo.json'], { maxBuffer: 8 * 1024 * 1024 }));
const oldBones = new Map(oldGeo['minecraft:geometry'][0].bones.map(b => [b.name, b]));
const oldGroups = new Map(oldSource.groups.map(g => [g.uuid, g]));
const oldElements = new Map(oldSource.elements.map(e => [e.uuid, e]));
const newGroups = new Map(source.groups.map(g => [g.name, g]));
const newSourceGroups = new Map(source.groups.map(g => [g.uuid, g]));
assert.ok(!byBone.has('right_hand_anchor') && !byBone.has('left_hand_anchor'));
assert.ok(newGroups.has('gun') && newGroups.has('righthand') && newGroups.has('lefthand'));

// Old and artist gun marker pairs fit this exact single similarity. Runtime X
// is inverted by Blockbench's exporter, hence source X has the opposite offset.
const ratio = .74;
const runtimeShift = [-2.2015, -2.28382, -3.74021];
const sourceShift = [2.2015, -2.28382, -3.74021];
const mapPoint = (v, shift) => v.map((n, i) => +(n * ratio + shift[i]).toFixed(8));
for (const name of ['gun', 'frame', 'muzzle_anchor', 'sight_anchor', 'rear_sight', 'ejection_anchor']) {
    const old = oldBones.get(name), artist = byBone.get(name);
    assert.ok(old && artist, `${name} marker missing`);
    assert.ok(mapPoint(old.pivot, runtimeShift).every((v, i) => Math.abs(v - artist.pivot[i]) < .0001),
        `${name} violates known gun similarity`);
}

// The original two complete motion->locator->hidden Classic/Slim reference
// subtrees are copied into the editable source. Attach under the artist gun
// bone: the old gun_model_root and the artist gun share the same rigid frame,
// while all other artist gun descendants remain unchanged.
function findNode(nodes, id) {
    for (const node of nodes) if (typeof node !== 'string') {
        if (node.uuid === id) return node;
        const nested = findNode(node.children ?? [], id);
        if (nested) return nested;
    }
}
const artistGun = findNode(source.outliner, newGroups.get('gun').uuid);
assert.ok(artistGun);
const names = ['right_hand_motion', 'right_hand_anchor', 'right_arm_reference', 'right_arm_reference_slim',
    'left_hand_motion', 'left_hand_anchor', 'left_arm_reference', 'left_arm_reference_slim'];
const copiedIds = new Set();
const copiedElements = new Set();
for (const motionName of ['right_hand_motion', 'left_hand_motion']) {
    const oldMotion = oldSource.groups.find(g => g.name === motionName);
    assert.ok(oldMotion && !newGroups.has(motionName));
    const subtree = structuredClone(findNode(oldSource.outliner, oldMotion.uuid));
    assert.ok(subtree);
    function copyTree(node) {
        for (const child of node.children ?? []) {
            if (typeof child === 'string') {
                assert.ok(!source.elements.some(e => e.uuid === child));
                const element = structuredClone(oldElements.get(child));
                assert.ok(element?.export === false, 'only hidden reference cubes may be copied');
                const anchor = motionName.startsWith('right') ? 'right_hand_anchor' : 'left_hand_anchor';
                const oldPivot = oldSource.groups.find(g => g.name === anchor).origin;
                const newPivot = mapPoint(oldPivot, sourceShift);
                const delta = newPivot.map((n, i) => n - oldPivot[i]);
                for (const key of ['from', 'to', 'origin'])
                    if (element[key]) element[key] = element[key].map((n, i) => +(n + delta[i]).toFixed(8));
                source.elements.push(element);
                copiedElements.add(child);
            } else {
                const group = structuredClone(oldGroups.get(child.uuid));
                assert.ok(group && names.includes(group.name) && !newSourceGroups.has(group.uuid));
                group.origin = mapPoint(group.origin, sourceShift);
                source.groups.push(group);
                copiedIds.add(group.uuid);
                copyTree(child);
            }
        }
    }
    const group = structuredClone(oldMotion);
    group.origin = mapPoint(group.origin, sourceShift);
    source.groups.push(group);
    copiedIds.add(group.uuid);
    copyTree(subtree);
    artistGun.children.push(subtree);
}
assert.equal(copiedIds.size, 8);
assert.equal(copiedElements.size, 4);
for (const name of ['right_hand_motion', 'right_hand_anchor', 'left_hand_motion', 'left_hand_anchor']) {
    const old = oldBones.get(name);
    assert.ok(old && !old.cubes?.length, `${name} must be a cube-free old locator`);
    const restored = { name, parent: name.endsWith('_motion') ? 'gun' : name.replace('_anchor', '_motion'),
        pivot: mapPoint(old.pivot, runtimeShift), ...(old.rotation ? { rotation: old.rotation } : {}) };
    bones.push(restored);
    byBone.set(name, restored);
}

// All matrices are rigid row-major 4x4. The only authored nonuniform scale is
// below the artist hand bones; we never traverse either *_hand_1 proxy.
const I = () => [1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1];
const mul = (a, b) => Array.from({ length: 16 }, (_, index) => {
    let sum = 0;
    for (let k = 0; k < 4; k++) sum += a[(index >> 2) * 4 + k] * b[k * 4 + index % 4];
    return sum;
});
const chain = (...parts) => parts.reduce(mul, I());
const T = v => { const m = I(); v.forEach((n, i) => m[i * 4 + 3] = n); return m; };
const R = (axis, deg) => {
    const m = I(), j = (axis + 1) % 3, k = (axis + 2) % 3;
    const c = Math.cos(deg * Math.PI / 180), s = Math.sin(deg * Math.PI / 180);
    m[j * 4 + j] = m[k * 4 + k] = c;
    m[j * 4 + k] = -s;
    m[k * 4 + j] = s;
    return m;
};
const rot = v => chain(R(2, v[2]), R(1, v[1]), R(0, v[0]));
const inverse = m => {
    const n = I();
    for (let i = 0; i < 3; i++) for (let j = 0; j < 3; j++) n[i * 4 + j] = m[j * 4 + i];
    for (let i = 0; i < 3; i++) n[i * 4 + 3] = -[0, 1, 2].reduce((v, j) => v + n[i * 4 + j] * m[j * 4 + 3], 0);
    return n;
};
const point = (m, p) => [0, 1, 2].map(i => m[i * 4 + 3] + p.reduce((v, n, j) => v + m[i * 4 + j] * n, 0));
const unit = v => v.map(n => n / 16);
const zero = [0, 0, 0];
function sample(channel, t, fallback) {
    if (!channel) return fallback;
    if (channel.vector) return channel.vector;
    const keys = Object.entries(channel).map(([time, value]) => [Number(time), value.vector]).sort((a, b) => a[0] - b[0]);
    if (!keys.length) return fallback;
    if (t <= keys[0][0]) return keys[0][1];
    if (t >= keys.at(-1)[0]) return keys.at(-1)[1];
    const next = keys.findIndex(([time]) => time >= t), a = keys[next - 1], b = keys[next];
    const f = (t - a[0]) / (b[0] - a[0]);
    return a[1].map((value, i) => value + (b[1][i] - value) * f);
}
function frame(clip, name, t, cache = new Map()) {
    if (cache.has(name)) return cache.get(name);
    const bone = byBone.get(name);
    assert.ok(bone, `${name} bone absent`);
    const track = clip.bones[name] ?? {};
    const pivot = unit(bone.pivot);
    const rotation = sample(track.rotation, t, zero).map((n, i) => n + (bone.rotation?.[i] ?? 0));
    const local = chain(T(unit(sample(track.position, t, zero))), T(pivot), rot(rotation), T(pivot.map(n => -n)));
    const result = bone.parent ? mul(frame(clip, bone.parent, t, cache), local) : local;
    cache.set(name, result);
    return result;
}
function cap(clip, name, t, cache) {
    return mul(frame(clip, name, t, cache), T(unit(byBone.get(name).pivot)));
}
function euler(m, previous) {
    const y = Math.asin(Math.max(-1, Math.min(1, -m[8])));
    assert.ok(Math.abs(Math.cos(y)) > .0001, 'retarget rotation is singular');
    const angles = [Math.atan2(m[9], m[10]), y, Math.atan2(m[4], m[0])].map(n => n * 180 / Math.PI);
    if (previous) for (let i = 0; i < 3; i++) while (angles[i] - previous[i] > 180) angles[i] -= 360;
    if (previous) for (let i = 0; i < 3; i++) while (angles[i] - previous[i] < -180) angles[i] += 360;
    return angles;
}
const baseline = animations.animations.static_idle;
const baseRelative = new Map();
for (const hand of ['righthand', 'lefthand']) {
    const cache = new Map();
    baseRelative.set(hand, mul(inverse(frame(baseline, 'gun', 0, cache)), cap(baseline, hand, 0, cache)));
}
const axes = ['x', 'y', 'z'];
const round = n => +n.toFixed(6);
function bake(clip, hand, motionName, anchorName, time) {
    const cache = new Map();
    const gun = frame(clip, 'gun', time, cache);
    const artist = cap(clip, hand, time, cache);
    const relative = mul(inverse(gun), artist);
    const anchor = byBone.get(anchorName), motion = byBone.get(motionName);
    const oldRest = chain(T(unit(anchor.pivot)), rot(anchor.rotation ?? zero));
    const target = chain(relative, inverse(baseRelative.get(hand)), oldRest);
    const rm = mul(target, inverse(rot(anchor.rotation ?? zero)));
    rm[3] = rm[7] = rm[11] = 0;
    const rotation = euler(rm);
    const pm = unit(motion.pivot), pa = unit(anchor.pivot);
    const rotated = point(rm, pa.map((n, i) => n - pm[i]));
    const position = [0, 1, 2].map(i => (target[i * 4 + 3] - pm[i] - rotated[i]) * 16);
    const rebuilt = chain(T(unit(position)), T(pm), rot(rotation), T(pa.map((n, i) => n - pm[i])), rot(anchor.rotation ?? zero));
    assert.ok(rebuilt.every((n, i) => Math.abs(n - target[i]) < 1e-5), `${clip} ${hand} ${time} retarget residual`);
    return { position: position.map(round), rotation: rotation.map(round) };
}
function timesFor(clip, length) {
    const times = new Set([0, length]);
    for (const name of ['root', 'g19_and_mag', 'g17', 'lower', 'gun', 'righthand', 'lefthand'])
        for (const channel of Object.values(clip.bones[name] ?? {}))
            if (channel && !channel.vector)
                for (const time of Object.keys(channel)) times.add(Number(time));
    // Matrix composition is nonlinear between authored Euler keys. Add bounded
    // interpolation samples without changing any original artist key or timing.
    if (length > 0) for (let t = 0; t < length; t += .025) times.add(+t.toFixed(5));
    return [...times].sort((a, b) => a - b);
}
function keyframe(channel, time, vector) {
    const sourceVector = vector.map((n, i) => (i === 0 || (channel === 'rotation' && i === 1)) ? -n : n);
    return { channel, data_points: [Object.fromEntries(axes.map((axis, i) => [axis, String(sourceVector[i])]))],
        uuid: randomUUID(), time, color: -1, interpolation: 'linear' };
}
for (const clipSource of source.animations) {
    const clip = animations.animations[clipSource.name];
    assert.ok(clip, `missing runtime ${clipSource.name}`);
    const length = clip.animation_length ?? clipSource.length ?? 0;
    const times = timesFor(clip, length);
    for (const [hand, motion, anchor] of [
        ['righthand', 'right_hand_motion', 'right_hand_anchor'],
        ['lefthand', 'left_hand_motion', 'left_hand_anchor']]) {
        const values = times.map(time => ({ time, ...bake(clip, hand, motion, anchor, time) }));
        const channels = {};
        const sourceKeys = [];
        for (const channel of ['position', 'rotation']) {
            const entries = values.map(({ time, [channel]: vector }) => [time, vector]);
            // Consecutive duplicate rest samples carry no action. Retain the
            // first/last sample and every authored transition/interpolation.
            const selected = entries.filter((entry, i) => i === 0 || i === entries.length - 1
                || entry[1].some((n, axis) => Math.abs(n - entries[i - 1][1][axis]) > 1e-5)
                || entries[i + 1][1].some((n, axis) => Math.abs(n - entry[1][axis]) > 1e-5));
            channels[channel] = selected.length === 1
                ? { vector: selected[0][1] }
                : Object.fromEntries(selected.map(([time, vector]) => [String(time), { vector }]));
            for (const [time, vector] of selected) sourceKeys.push(keyframe(channel, time, vector));
        }
        clip.bones[motion] = channels;
        const group = source.groups.find(g => g.name === motion);
        clipSource.animators[group.uuid] = { name: motion, type: 'bone', rotation_global: false,
            quaternion_interpolation: false, keyframes: sourceKeys };
    }
}

// Preserve original file bytes for all artist geometry/animation/camera/sound
// entries; inject only the four new runtime bones and two new tracks per clip.
function closeAt(text, start, open, close) {
    let depth = 0, quoted = false, escaped = false;
    for (let i = start; i < text.length; i++) {
        const c = text[i];
        if (quoted) {
            if (escaped) escaped = false;
            else if (c === '\\') escaped = true;
            else if (c === '"') quoted = false;
        } else if (c === '"') quoted = true;
        else if (c === open) depth++;
        else if (c === close && --depth === 0) return i;
    }
    throw new Error('unclosed JSON container');
}
const geoStart = geoText.indexOf('"bones": [');
const geoOpen = geoText.indexOf('[', geoStart);
const geoEnd = closeAt(geoText, geoOpen, '[', ']');
const geoAppend = bones.slice(-4).map(b => JSON.stringify(b)).join(',\n\t\t\t\t');
const patchedGeo = geoText.slice(0, geoEnd).replace(/\s+$/, '') + ',\n\t\t\t\t' + geoAppend + '\n\t\t\t' + geoText.slice(geoEnd);
let patchedAnimation = animationText;
for (const clipSource of [...source.animations].reverse()) {
    const clipName = clipSource.name;
    const marker = `"${clipName}": {`;
    const clipStart = patchedAnimation.indexOf(marker);
    assert.ok(clipStart >= 0);
    const bonesStart = patchedAnimation.indexOf('"bones": {', clipStart);
    const bonesOpen = patchedAnimation.indexOf('{', bonesStart);
    const bonesEnd = closeAt(patchedAnimation, bonesOpen, '{', '}');
    const added = ['right_hand_motion', 'left_hand_motion'].map(name =>
        `\t\t\t\t"${name}": ${JSON.stringify(animations.animations[clipName].bones[name])}`).join(',\n');
    patchedAnimation = patchedAnimation.slice(0, bonesEnd).replace(/\s+$/, '') + ',\n' + added + '\n\t\t\t' + patchedAnimation.slice(bonesEnd);
}
assert.equal(JSON.stringify(JSON.parse(patchedGeo)), JSON.stringify(geo));
assert.equal(JSON.stringify(JSON.parse(patchedAnimation)), JSON.stringify(animations));
writeFileSync(sourcePath, JSON.stringify(source));
writeFileSync(geoPath, patchedGeo);
writeFileSync(animationPath, patchedAnimation);
console.log('P9 known-good cube-free hand anchors restored; nine artist clips retargeted relative to gun');
