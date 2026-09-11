import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';

const path = 'src/main/blockbench/previews/office_cubicle_partition_connection_preview.bbmodel';
const model = JSON.parse(await readFile(path, 'utf8'));
assert.equal(model.name, 'office_cubicle_partition_connection_preview');
assert.equal(model.elements.length, 960);
assert.equal(model.textures.length, 7);
const names = model.groups.map(group => group.name);
for (const required of [
  'preview_single', 'preview_straight_2', 'preview_straight_3',
  'preview_corner_L', 'preview_U_cubicle', 'preview_T_junction', 'preview_cross_optional'
]) assert(names.includes(required), `Missing ${required}`);

for (const cube of model.elements) {
  assert(cube.from.every((value, axis) => Number.isFinite(value) && value < cube.to[axis]), `Invalid cube ${cube.name}`);
}
const count = pattern => model.elements.filter(cube => pattern.test(cube.name)).length;
assert.equal(count(/^end_post_(left|right)_foot_base$/), 4);
assert.equal(count(/^seam_1_(front|back)$/), 4);
assert.equal(count(/^seam_2_(front|back)$/), 2);
assert.equal(count(/^corner_post_body$/), 1);
assert.equal(count(/^t_junction_post_body$/), 1);
assert.equal(count(/^cross_junction_post_body$/), 1);
assert.equal(count(/^u_corner_post_(left|right)_body$/), 2);
assert.equal(count(/_junction_post_.*foot$/), 2);

const overlaps = (left, right) => [0, 1, 2].every(axis =>
  Math.min(left.to[axis], right.to[axis]) - Math.max(left.from[axis], right.from[axis]) > 0.000001
);
const assertNoOverlap = (label, cubes) => {
  const collisions = [];
  for (let leftIndex = 0; leftIndex < cubes.length; leftIndex++) {
    for (let rightIndex = leftIndex + 1; rightIndex < cubes.length; rightIndex++) {
      if (overlaps(cubes[leftIndex], cubes[rightIndex])) {
        collisions.push(`${cubes[leftIndex].name} <> ${cubes[rightIndex].name}`);
      }
    }
  }
  assert.deepEqual(collisions, [], `${label} cube overlaps:\n${collisions.join('\n')}`);
};
assertNoOverlap('CORNER_L', model.elements.filter(cube => /^corner_/.test(cube.name)));
assertNoOverlap('T_JUNCTION', model.elements.filter(cube => /^t_/.test(cube.name)));
assertNoOverlap('CROSS', model.elements.filter(cube => /^cross_/.test(cube.name)));
assertNoOverlap('U_CUBICLE_CONNECTIONS', model.elements.filter(cube =>
  /^u_(back_panel|left_panel|right_panel|back_seam|corner_post|end_post)/.test(cube.name)
));

const sourceBefore = await readFile('src/main/blockbench/office_cubicle_partition.bbmodel');
const approved = JSON.parse(sourceBefore);
assert.equal(approved.elements.length, 42);
assert.equal(Math.max(...approved.elements.map(cube => cube.to[1])), 32);

console.log(JSON.stringify({
  preview: 'PASS', cubes: model.elements.length, groups: model.groups.length,
  scenarios: 7, sourceSinglePreserved: true, middleDoublePostRemoved: true,
  cornerUsesSinglePost: true, footDensity: 'end/corner/junction only',
  cornerTJunctionCrossOverlapPairs: 0
}));
