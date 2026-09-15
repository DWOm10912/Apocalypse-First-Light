// One-time P9 hand-adapter migration. Never writes to the external artist source.
// Mirrors migrate-br51_01.mjs: artist drivers stay authoritative; AFL anchors
// inherit them, cancel the authored 1.6 Y proxy stretch, and carry source-only refs.
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { readFileSync, writeFileSync } from 'node:fs';

const sourcePath = 'src/main/blockbench/p9_01.bbmodel';
const geoPath = 'src/main/resources/assets/apocalypse_firstlight/geo/p9_01.geo.json';
const animPath = 'src/main/resources/assets/apocalypse_firstlight/animations/p9_01.animation.json';
const artist = JSON.parse(readFileSync('E:/Download/AFL/p9_01.bbmodel'));
const source = JSON.parse(readFileSync(sourcePath));
const geo = JSON.parse(readFileSync(geoPath));
const animation = JSON.parse(readFileSync(animPath));
const bones = geo['minecraft:geometry'][0].bones;
const clips = ['static_idle', 'empty_idle', 'shoot', 'reload_tactical', 'reload_empty',
    'draw', 'put_away', 'inspect', 'inspect_empty'];
assert.deepEqual(source.animations.map(a => a.name), artist.animations.map(a => a.name));
assert.deepEqual(source.animations.map(a => a.name).sort(), [...clips].sort());
assert.equal(artist.groups.length, 42);
assert.equal(artist.elements.length, 171);
assert.deepEqual(source.groups.slice(0, 42).map(g => [g.name, g.uuid, g.origin, g.rotation]),
    artist.groups.map(g => [g.name, g.uuid, g.origin, g.rotation]));
assert.deepEqual(source.elements.slice(0, 171).filter(e => e.name !== 'camera')
    .map(e => [e.name, e.uuid, e.from, e.to]),
    artist.elements.filter(e => e.name !== 'camera')
        .map(e => [e.name, e.uuid, e.from, e.to]));
const originalTracks = Object.fromEntries(clips.map(name => [name,
    structuredClone(animation.animations[name].bones)]));
const oldNames = ['right_hand_motion', 'right_hand_anchor', 'left_hand_motion',
    'left_hand_anchor'];
const oldGroups = new Map(oldNames.map(name => [name, source.groups.find(g => g.name === name)]));
for (const [name, group] of oldGroups) assert.ok(group, `missing identifiable failed fix ${name}`);
for (const name of oldNames) assert.equal(bones.filter(b => b.name === name).length, 1);
for (const clip of clips) {
    for (const name of ['right_hand_motion', 'left_hand_motion']) {
        assert.ok(animation.animations[clip].bones[name], `${clip} failed retarget absent`);
        delete animation.animations[clip].bones[name];
        delete source.animations.find(a => a.name === clip).animators[oldGroups.get(name).uuid];
    }
}

function find(nodes, uuid) {
    for (const node of nodes) {
        if (typeof node === 'string') continue;
        if (node.uuid === uuid) return node;
        const nested = find(node.children ?? [], uuid);
        if (nested) return nested;
    }
}
const gun = find(source.outliner, source.groups.find(g => g.name === 'gun').uuid);
assert.ok(gun);
for (const name of ['right_hand_motion', 'left_hand_motion']) {
    const id = oldGroups.get(name).uuid;
    assert.equal(gun.children.filter(n => n.uuid === id).length, 1);
    gun.children = gun.children.filter(n => n.uuid !== id);
}
source.groups = source.groups.filter(g => !oldNames.includes(g.name));
bones.splice(0, bones.length, ...bones.filter(b => !oldNames.includes(b.name)));
const id = name => {
    const h = createHash('md5').update('afl-p9_01-adapter/' + name).digest('hex');
    return `${h.slice(0, 8)}-${h.slice(8, 12)}-${h.slice(12, 16)}-${h.slice(16, 20)}-${h.slice(20)}`;
};
const display = JSON.parse(readFileSync('src/main/resources/assets/apocalypse_firstlight/models/item/p9_01_in_hand.json'));
const itemScale = display.display.firstperson_righthand.scale[0];
assert.equal(itemScale, 0.41, 'P9 display changed; recalculate reference size');

for (const side of ['right', 'left']) {
    const driver = `${side}hand`;
    const parent = `${driver}_pos`;
    const anchorName = `${side}_hand_anchor`;
    const proxy = source.elements.find(e => e.name === parent);
    const authorProxy = artist.elements.find(e => e.name === parent);
    const group = source.groups.find(g => g.name === parent);
    const parentNode = find(source.outliner, group?.uuid);
    const runtimeParent = bones.find(b => b.name === parent);
    assert.ok(proxy && authorProxy && parentNode && runtimeParent);
    assert.deepEqual([proxy.from, proxy.to], [authorProxy.from, authorProxy.to]);
    assert.equal(proxy.to[1], 20);
    assert.ok(proxy.to.every((v, i) => Math.abs(v - proxy.from[i] - [4, 12, 4][i]) < 1e-6));
    assert.equal(parentNode.children.filter(c => c === proxy.uuid).length, 1);
    assert.equal(runtimeParent.cubes?.length ?? 0, 0, 'artist proxy already removed in runtime');
    proxy.export = false;
    proxy.visibility = false;
    const x = (proxy.from[0] + proxy.to[0]) / 2;
    const z = (proxy.from[2] + proxy.to[2]) / 2;
    const origin = [x, 20, z];
    const upper = `${driver}_1`;
    for (const clip of clips) {
        const scale = animation.animations[clip].bones[upper]?.scale?.vector;
        assert.deepEqual(scale, [1, 1.6, 1], `${clip}/${upper} scale semantic changed`);
        animation.animations[clip].bones[anchorName] = { scale: { vector: [1, 0.625, 1] } };
        source.animations.find(a => a.name === clip).animators[id(anchorName)] = {
            name: anchorName, type: 'bone', keyframes: [{ channel: 'scale', time: 0,
                data_points: [{ x: 1, y: 0.625, z: 1 }], interpolation: 'linear',
                uuid: id(`${clip}/${anchorName}/scale`) }]
        };
    }
    source.groups.push({ name: anchorName, uuid: id(anchorName), origin,
        rotation: [0, 0, 0], export: true });
    bones.push({ name: anchorName, parent, pivot: [-x, 20, z], rotation: [0, 0, 0] });
    const anchorNode = { uuid: id(anchorName), children: [] };
    parentNode.children.push(anchorNode);
    for (const slim of [false, true]) {
        const refName = `${side}_arm_reference${slim ? '_slim' : ''}`;
        const ref = source.groups.find(g => g.name === refName);
        const cube = source.elements.find(e => e.name === `${refName}_cube`);
        assert.ok(ref && cube && ref.export === false && cube.export === false);
        const width = (slim ? 3 : 4) * .62 / itemScale;
        const length = 12 * .78 / itemScale;
        const depth = 4 * .62 / itemScale;
        ref.origin = [...origin]; ref.rotation = [0, 0, 0]; ref.export = false;
        cube.origin = [...origin]; cube.from = [x - width / 2, 20 - length, z - depth / 2];
        cube.to = [x + width / 2, 20, z + depth / 2]; cube.export = false;
        anchorNode.children.push({ uuid: ref.uuid, children: [cube.uuid] });
    }
}
source.elements = source.elements.filter(e => !e.name.match(/^(right|left)_arm_reference(_slim)?_cube$/)
    || source.groups.some(g => g.name === e.name.replace(/_cube$/, '')));
for (const clip of clips) {
    const expected = structuredClone(originalTracks[clip]);
    delete expected.right_hand_motion; delete expected.left_hand_motion;
    for (const name of ['right_hand_anchor', 'left_hand_anchor']) delete expected[name];
    const actual = structuredClone(animation.animations[clip].bones);
    delete actual.right_hand_anchor; delete actual.left_hand_anchor;
    assert.deepEqual(actual, expected, `Artist motion changed in ${clip}`);
}
writeFileSync(sourcePath, JSON.stringify(source));
for (const [path, value] of [[geoPath, geo], [animPath, animation]])
    writeFileSync(path, JSON.stringify(value, null, '\t') + '\n');
console.log('P9 artist hand adapter migrated; original motion retained, wrong retarget removed');
