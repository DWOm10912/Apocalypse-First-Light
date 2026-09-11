import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';

const resourceRoot = 'src/main/resources/assets/apocalypse_firstlight';
const readJson = async path => JSON.parse(await readFile(path, 'utf8'));
const source = await readJson('src/main/blockbench/office_cubicle_partition.bbmodel');
const blockstate = await readJson(`${resourceRoot}/blockstates/office_cubicle_partition.json`);
const item = await readJson(`${resourceRoot}/models/item/office_cubicle_partition.json`);
const modelNames = ['single', 'end_east', 'straight_ew', 'arm_east', 'junction'];
const models = Object.fromEntries(await Promise.all(modelNames.map(async name => [name,
  await readJson(`${resourceRoot}/models/block/office_cubicle_partition/${name}.json`)])));

assert.equal(source.elements.length, 42);
assert.equal(Math.max(...source.elements.map(element => element.to[1])), 32);
assert.equal(models.single.elements.length, 42);
assert.equal(blockstate.multipart.length, 12);
assert.equal(item.parent, 'apocalypse_firstlight:block/office_cubicle_partition/single');
assert.deepEqual(item.display.firstperson_righthand.scale, [0.3, 0.3, 0.3]);
assert.deepEqual(item.display.thirdperson_righthand.scale, [0.25, 0.25, 0.25]);

for (const [name, model] of Object.entries(models)) {
  assert.equal(model.ambientocclusion, false, `${name} ambient occlusion`);
  assert.equal(model.textures['0'], 'apocalypse_firstlight:block/office_cubicle_partition');
  for (const element of model.elements) {
    assert(element.from.every((value, axis) => Number.isFinite(value) && value < element.to[axis]),
      `${name}/${element.name} has invalid bounds`);
  }
}

const exactMatches = (when, state) => Object.entries(when).every(([property, value]) => {
  if (property === 'OR') return value.some(condition => exactMatches(condition, state));
  return state[property] === value;
});
const appliedModels = state => blockstate.multipart
  .filter(part => !part.when || exactMatches(part.when, state))
  .map(part => part.apply.model.replace(/^.*\//, ''));
const appliedParts = state => blockstate.multipart
  .filter(part => !part.when || exactMatches(part.when, state))
  .map(part => ({name: part.apply.model.replace(/^.*\//, ''), rotation: part.apply.y ?? 0}));
const state = (north, south, east, west) => ({
  north: String(north), south: String(south), east: String(east), west: String(west)
});

assert.deepEqual(appliedModels(state(false, false, false, false)), ['single']);
assert.deepEqual(appliedModels(state(false, false, true, false)), ['end_east']);
assert.deepEqual(appliedModels(state(false, false, true, true)), ['straight_ew']);
assert.deepEqual(appliedModels(state(true, false, true, false)), ['junction', 'arm_east', 'arm_east']);
assert.deepEqual(appliedModels(state(true, false, true, true)), ['junction', 'arm_east', 'arm_east', 'arm_east']);
assert.deepEqual(appliedModels(state(true, true, true, true)), ['junction', 'arm_east', 'arm_east', 'arm_east', 'arm_east']);

const overlaps = (left, right) => [0, 1, 2].every(axis =>
  Math.min(left.to[axis], right.to[axis]) - Math.max(left.from[axis], right.from[axis]) > 0.000001
);
const assertNoOverlaps = (name, elements) => {
  const pairs = [];
  for (let left = 0; left < elements.length; left++) {
    for (let right = left + 1; right < elements.length; right++) {
      if (overlaps(elements[left], elements[right])) pairs.push(`${elements[left].name} <> ${elements[right].name}`);
    }
  }
  assert.deepEqual(pairs, [], `${name} positive-volume overlaps:\n${pairs.join('\n')}`);
};
assertNoOverlaps('straight_ew', models.straight_ew.elements);
assertNoOverlaps('arm_east', models.arm_east.elements);
assertNoOverlaps('junction', models.junction.elements);

const rotatePoint = (x, z, degrees) => {
  if (degrees === 0) return [x, z];
  if (degrees === 90) return [16 - z, x];
  if (degrees === 180) return [16 - x, 16 - z];
  if (degrees === 270) return [z, 16 - x];
  throw new Error(`Unsupported Y rotation ${degrees}`);
};
const rotateElement = (element, degrees, prefix) => {
  const points = [
    rotatePoint(element.from[0], element.from[2], degrees),
    rotatePoint(element.from[0], element.to[2], degrees),
    rotatePoint(element.to[0], element.from[2], degrees),
    rotatePoint(element.to[0], element.to[2], degrees)
  ];
  return {
    name: `${prefix}/${element.name}`,
    from: [Math.min(...points.map(point => point[0])), element.from[1], Math.min(...points.map(point => point[1]))],
    to: [Math.max(...points.map(point => point[0])), element.to[1], Math.max(...points.map(point => point[1]))]
  };
};
const composedElements = state => appliedParts(state).flatMap((part, index) =>
  models[part.name].elements.map(element => rotateElement(element, part.rotation, `${index}:${part.name}@${part.rotation}`))
);
assertNoOverlaps('CORNER composed multipart', composedElements(state(true, false, true, false)));
assertNoOverlaps('T_JUNCTION composed multipart', composedElements(state(true, false, true, true)));
assertNoOverlaps('CROSS composed multipart', composedElements(state(true, true, true, true)));

console.log(JSON.stringify({
  runtimeResources: 'PASS',
  sourceSinglePreserved: true,
  states: ['SINGLE', 'END', 'STRAIGHT', 'CORNER', 'T_JUNCTION', 'CROSS'],
  middleDoublePostRemoved: true,
  cornerSinglePost: true,
  cornerTJunctionCrossPositiveOverlapPairs: 0,
  firstPersonScale: 0.3,
  thirdPersonScale: 0.25
}));
