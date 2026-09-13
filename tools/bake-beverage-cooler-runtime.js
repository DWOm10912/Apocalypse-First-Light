// Mechanical export from the authoritative editable Blockbench source.
// Run with: node tools/bake-beverage-cooler-runtime.js
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');

const root = path.resolve(__dirname, '..');
const source = JSON.parse(fs.readFileSync(path.join(root, 'src/main/blockbench/afl_beverage_cooler.bbmodel'), 'utf8'));
const assetRoot = path.join(root, 'src/main/resources/assets/apocalypse_firstlight');
const groups = new Map(source.groups.map(group => [group.uuid, group]));
const elements = new Map(source.elements.map(element => [element.uuid, element]));
const bones = [];
const faces = ['north', 'east', 'south', 'west', 'up', 'down'];

assert.equal(source.elements.length, 452);
assert.deepEqual(source.resolution, {width: 128, height: 128});
assert.deepEqual(source.animations.map(animation => animation.name).sort(),
  ['left_door_close', 'left_door_open', 'right_door_close', 'right_door_open']);

function convertCube(element) {
  assert.ok(!element.rotation || element.rotation.every(value => value === 0), element.name);
  const uv = {};
  for (const face of faces) {
    const data = element.faces[face];
    assert.equal(data.texture, 0, `${element.name}/${face} texture`);
    const [u0, v0, u1, v1] = data.uv;
    uv[face] = {uv: [u0, v0], uv_size: [u1 - u0, v1 - v0]};
  }
  return {
    // GeoBlockRenderer's horizontal rotation reverses the authored X handedness.
    // Mirror around the two-cell midpoint so source left_door is left in-game too.
    origin: [8 - element.to[0], element.from[1], element.from[2]],
    size: element.to.map((value, axis) => value - element.from[axis]),
    uv
  };
}

function walk(groupNode, parent) {
  const group = groups.get(groupNode.uuid);
  assert.ok(group, groupNode.uuid);
  const bone = {name: group.name};
  if (parent) bone.parent = parent;
  bone.pivot = [8 - group.origin[0], group.origin[1], group.origin[2]];
  const cubes = groupNode.children.filter(child => typeof child === 'string').map(uuid => {
    const element = elements.get(uuid);
    assert.ok(element, uuid);
    return convertCube(element);
  });
  if (cubes.length) bone.cubes = cubes;
  bones.push(bone);
  for (const child of groupNode.children) if (typeof child !== 'string') walk(child, group.name);
}
for (const groupNode of source.outliner) walk(groupNode, undefined);
assert.equal(bones.length, 23);
assert.equal(bones.reduce((count, bone) => count + (bone.cubes?.length || 0), 0), 452);

const geo = {
  format_version: '1.12.0',
  'minecraft:geometry': [{
    description: {
      identifier: 'geometry.beverage_cooler',
      texture_width: 128, texture_height: 128,
      visible_bounds_width: 4, visible_bounds_height: 3.5,
      visible_bounds_offset: [0, 1, 0]
    },
    bones
  }]
};

// The two source keys use cubic Bezier with 1/3-duration time handles.
// Eight 20-tick-compatible samples retain the actual curve and 0.40s endpoints.
const animations = {};
for (const sourceAnimation of source.animations) {
  const door = sourceAnimation.name.startsWith('left_') ? 'left_door' : 'right_door';
  const group = source.groups.find(group => group.name === door);
  const active = Object.entries(sourceAnimation.animators).filter(([, animator]) => animator.keyframes?.length);
  assert.equal(active.length, 1, `${sourceAnimation.name} nonempty tracks`);
  assert.equal(active[0][0], group.uuid, `${sourceAnimation.name} target`);
  const keys = active[0][1].keyframes;
  assert.equal(keys.length, 2);
  assert.ok(keys.every(key => key.channel === 'rotation' && key.interpolation === 'bezier'));
  assert.equal(sourceAnimation.length, 0.4);
  const start = Number(keys[0].data_points[0].y);
  const finish = Number(keys[1].data_points[0].y);
  const p1 = start + Number(keys[0].bezier_right_value[1]);
  const p2 = finish + Number(keys[1].bezier_left_value[1]);
  const rotation = {};
  for (let sample = 0; sample <= 8; sample++) {
    const t = sample / 8;
    const angle = Math.pow(1 - t, 3) * start + 3 * Math.pow(1 - t, 2) * t * p1
      + 3 * (1 - t) * t * t * p2 + Math.pow(t, 3) * finish;
    rotation[(sample * 0.05).toFixed(2).replace(/0+$/, '').replace(/\.$/, '') || '0'] = {
      vector: [0, Number((-angle).toFixed(5)), 0]
    };
  }
  animations[sourceAnimation.name] = {
    loop: false, animation_length: 0.4,
    bones: {[door]: {rotation}}
  };
}
for (const door of ['left_door', 'right_door']) {
  for (const open of [false, true]) {
    const angle = open ? (door === 'left_door' ? 95 : -95) : 0;
    animations[`${door}_${open ? 'open' : 'closed'}_pose`] = {
      loop: true, animation_length: 0.05,
      bones: {[door]: {rotation: [0, angle, 0]}}
    };
  }
}
const animationJson = {format_version: '1.8.0', animations};
for (const [relative, object] of [
  ['geo/beverage_cooler.geo.json', geo],
  ['animations/beverage_cooler.animation.json', animationJson]
]) {
  const target = path.join(assetRoot, relative);
  fs.mkdirSync(path.dirname(target), {recursive: true});
  fs.writeFileSync(target, JSON.stringify(object, null, 2) + '\n');
}
console.log('Baked 452 cubes, 23 bones, four 0.40s door clips and four stable end poses.');
