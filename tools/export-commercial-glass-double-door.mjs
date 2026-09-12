import fs from 'node:fs';
import path from 'node:path';

const root = path.resolve(import.meta.dirname, '..');
const source = JSON.parse(fs.readFileSync(path.join(root, 'src/main/blockbench/commercial_glass_swing_door.bbmodel'), 'utf8'));
const assets = path.join(root, 'src/main/resources/assets/apocalypse_firstlight');
const geoPath = path.join(assets, 'geo/commercial_glass_double_door.geo.json');
const geo = JSON.parse(fs.readFileSync(geoPath, 'utf8'));
const geometry = geo['minecraft:geometry'][0];
geometry.description.identifier = 'geometry.commercial_glass_double_door';
geometry.description.visible_bounds_width = 3;
geometry.description.visible_bounds_height = 3;
geometry.description.visible_bounds_offset = [0, 1, 0];
const names = new Set(geometry.bones.map(b => b.name));
if (!names.has('left_door') || !names.has('right_door')) throw new Error('Missing swing door bones');
const clips = {};
for (const animation of source.animations.filter(a => ['door_open', 'door_close'].includes(a.name))) {
  const bones = {};
  for (const animator of Object.values(animation.animators)) {
    if (!['left_door', 'right_door'].includes(animator.name)) continue;
    const rotation = {};
    for (const key of animator.keyframes.filter(k => k.channel === 'rotation')) {
      const p = key.data_points[0];
      rotation[String(key.time)] = { vector: [Number(p.x), -Number(p.y), Number(p.z)] };
    }
    bones[animator.name] = { rotation };
  }
  if (Object.keys(bones).length !== 2) throw new Error(`Incomplete ${animation.name}`);
  clips[animation.name] = { loop: 'hold_on_last_frame', animation_length: animation.length, bones };
}
if (Object.keys(clips).length !== 2) throw new Error('Expected opening and closing animations');
for (const [name, angle] of [['door_closed_pose', 0], ['door_open_pose', 90]]) {
  clips[name] = { loop: true, animation_length: 0.05, bones: {
    left_door: { rotation: [0, angle, 0] }, right_door: { rotation: [0, -angle, 0] }
  } };
}
const png = Buffer.from(source.textures[0].source.split(',')[1], 'base64');
const outputs = [
  [geoPath, Buffer.from(JSON.stringify(geo, null, 2) + '\n')],
  [path.join(assets, 'animations/commercial_glass_double_door.animation.json'), Buffer.from(JSON.stringify({ format_version: '1.8.0', animations: clips }, null, 2) + '\n')],
  [path.join(assets, 'textures/entity/commercial_glass_double_door.png'), png],
  [path.join(root, 'src/main/blockbench/textures/commercial_glass_swing_door.png'), png]
];
for (const [target, data] of outputs) {
  if (process.argv.includes('--check')) {
    if (!fs.existsSync(target) || !fs.readFileSync(target).equals(data)) throw new Error(`Stale: ${target}`);
  } else {
    fs.mkdirSync(path.dirname(target), { recursive: true });
    fs.writeFileSync(target, data);
  }
}
console.log('Commercial double swing door geometry, animation and texture synchronized');
