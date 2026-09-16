import fs from 'node:fs';
import assert from 'node:assert/strict';

const assetRoot = 'src/main/resources/assets/apocalypse_firstlight';
const sourceRoot = 'src/main/blockbench/fixed_diagonal_braces';
const beam = JSON.parse(fs.readFileSync(`${assetRoot}/models/block/steel_beam.json`, 'utf8'));
const write = (path, value) => fs.writeFileSync(path, JSON.stringify(value, null, 2) + '\n');
fs.mkdirSync(`${assetRoot}/models/block`, { recursive: true });
fs.mkdirSync(`${assetRoot}/models/item`, { recursive: true });
fs.mkdirSync(`${assetRoot}/blockstates`, { recursive: true });
fs.mkdirSync(sourceRoot, { recursive: true });

assert.deepEqual(beam.texture_size, [64, 64]);
assert.equal(beam.textures['0'], 'apocalypse_firstlight:block/steel_beam');
assert.equal(beam.elements.length, 34);

const clone = value => JSON.parse(JSON.stringify(value));
const diagonalScale = Math.SQRT2;
const stretchAlongBeam = point => [point[0], 8 + (point[1] - 8) * diagonalScale, point[2]];
function xyElements(angle) {
  return beam.elements.map(source => ({
    ...clone(source),
    from: stretchAlongBeam(source.from),
    to: stretchAlongBeam(source.to),
    rotation: { origin: [8, 8, 8], axis: 'z', angle, rescale: false }
  }));
}

function zyElements(angle) {
  const directions = { north: 'east', east: 'south', south: 'west', west: 'north', up: 'up', down: 'down' };
  return xyElements(angle).map(source => ({
    ...source,
    from: [16 - source.to[2], source.from[1], source.from[0]],
    to: [16 - source.from[2], source.to[1], source.to[0]],
    rotation: { origin: [8, 8, 8], axis: 'x', angle: -angle, rescale: false },
    faces: Object.fromEntries(Object.entries(source.faces).map(([direction, face]) => [directions[direction], clone(face)]))
  }));
}

const model = elements => ({
  format_version: beam.format_version,
  credit: beam.credit,
  texture_size: clone(beam.texture_size),
  textures: clone(beam.textures),
  elements
});
const aXY = model(xyElements(-45));
const bXY = model(xyElements(45));
const aZY = model(zyElements(-45));
const bZY = model(zyElements(45));
const combine = (a, b) => model([...clone(a.elements), ...clone(b.elements)]);
const offsetElements = (sourceModel, axis, amount) => model(sourceModel.elements.map(element => {
  const result = clone(element);
  result.from[axis] += amount;
  result.to[axis] += amount;
  if (result.rotation) result.rotation.origin[axis] += amount;
  return result;
}));
const crossLayerOffset = 0.6;
const models = {
  diagonal_brace_a_xy: aXY,
  diagonal_brace_a_zy: aZY,
  diagonal_brace_b_xy: bXY,
  diagonal_brace_b_zy: bZY,
  // Keep both complete braces, but separate them along the plane normal to
  // prevent coplanar faces from z-fighting at the center crossing.
  cross_brace_xy: combine(
    offsetElements(aXY, 2, crossLayerOffset),
    offsetElements(bXY, 2, -crossLayerOffset)
  ),
  cross_brace_zy: combine(
    offsetElements(aZY, 0, crossLayerOffset),
    offsetElements(bZY, 0, -crossLayerOffset)
  )
};

const plateUv = [12, 0, 16, 8];
const boltUv = [12, 12, 16, 16];
const allFaces = uv => Object.fromEntries(
  ['north', 'east', 'south', 'west', 'up', 'down'].map(direction => [direction, { uv: clone(uv), texture: '#0' }])
);
function endpointJointXY(side, height, angle) {
  const endpointSourceY = height === 'low'
    ? 8 - 8 * diagonalScale
    : 8 + 8 * diagonalScale;
  const outwardSign = height === 'low' ? -1 : 1;
  const overlapAlongMember = 0.03 * diagonalScale;
  const gapAlongMember = 6 * diagonalScale;
  const innerY = endpointSourceY - outwardSign * overlapAlongMember;
  const outerY = endpointSourceY + outwardSign * gapAlongMember;
  const segment = {
    name: `${side}_${height}_diagonal_connector`,
    // Avoid every existing beam skin/web plane (notably 6.75 and 9.25), so
    // the nested connector cannot produce coplanar faces at the hand-off.
    from: [7.1, Math.min(innerY, outerY), 7.1],
    to: [8.9, Math.max(innerY, outerY), 8.9],
    rotation: { origin: [8, 8, 8], axis: 'z', angle, rescale: false },
    faces: allFaces(plateUv)
  };

  // A neighboring vertical steel beam occupies 6..10 in its own cell, so its
  // brace-facing surface is x=-6 on the negative side and x=22 on positive.
  const beamFaceX = side === 'negative' ? -6 : 22;
  const endpointY = height === 'low' ? -6 : 22;
  const plateFromX = side === 'negative' ? beamFaceX - 0.15 : beamFaceX - 0.1;
  const plateToX = side === 'negative' ? beamFaceX + 0.1 : beamFaceX + 0.15;
  const elements = [segment, {
    name: `${side}_${height}_beam_end_plate`,
    from: [plateFromX, endpointY - 2, 5.5],
    to: [plateToX, endpointY + 2, 10.5],
    faces: allFaces(plateUv)
  }];
  const boltFace = side === 'negative'
    ? [beamFaceX + 0.08, beamFaceX + 0.28]
    : [beamFaceX - 0.28, beamFaceX - 0.08];
  for (const yOffset of [-1.25, 0.9]) {
    for (const zOffset of [-1.55, 1.2]) {
      elements.push({
        name: `${side}_${height}_bolt_${yOffset}_${zOffset}`,
        from: [boltFace[0], endpointY + yOffset, 8 + zOffset],
        to: [boltFace[1], endpointY + yOffset + 0.35, 8 + zOffset + 0.35],
        faces: allFaces(boltUv)
      });
    }
  }
  return model(elements);
}
function mapModelXYtoZY(sourceModel) {
  const directions = { north: 'east', east: 'south', south: 'west', west: 'north', up: 'up', down: 'down' };
  return model(sourceModel.elements.map(source => ({
    ...clone(source),
    from: [16 - source.to[2], source.from[1], source.from[0]],
    to: [16 - source.from[2], source.to[1], source.to[0]],
    faces: Object.fromEntries(Object.entries(source.faces).map(([direction, face]) => [directions[direction], clone(face)]))
  })));
}
const jointXY = {
  a_negative: endpointJointXY('negative', 'low', -45),
  a_positive: endpointJointXY('positive', 'high', -45),
  b_negative: endpointJointXY('negative', 'high', 45),
  b_positive: endpointJointXY('positive', 'low', 45)
};
jointXY.cross_negative = combine(
  offsetElements(jointXY.a_negative, 2, crossLayerOffset),
  offsetElements(jointXY.b_negative, 2, -crossLayerOffset)
);
jointXY.cross_positive = combine(
  offsetElements(jointXY.a_positive, 2, crossLayerOffset),
  offsetElements(jointXY.b_positive, 2, -crossLayerOffset)
);
const jointModels = {};
for (const [name, value] of Object.entries(jointXY)) {
  jointModels[`${name}_xy`] = value;
  jointModels[`${name}_zy`] = mapModelXYtoZY(value);
}

for (const [name, value] of Object.entries(models)) {
  write(`${assetRoot}/models/block/${name}.json`, value);
}
for (const [name, value] of Object.entries(jointModels)) {
  write(`${assetRoot}/models/block/${name}_joint.json`, value);
}
for (const id of ['diagonal_brace_a', 'diagonal_brace_b', 'cross_brace']) {
  const jointPrefix = id === 'diagonal_brace_a' ? 'a' : id === 'diagonal_brace_b' ? 'b' : 'cross';
  write(`${assetRoot}/blockstates/${id}.json`, { multipart: [
    { when: { horizontal_axis: 'x' }, apply: { model: `apocalypse_firstlight:block/${id}_xy` } },
    { when: { horizontal_axis: 'z' }, apply: { model: `apocalypse_firstlight:block/${id}_zy` } },
    { when: { horizontal_axis: 'x', joint_negative: 'true' }, apply: { model: `apocalypse_firstlight:block/${jointPrefix}_negative_xy_joint` } },
    { when: { horizontal_axis: 'x', joint_positive: 'true' }, apply: { model: `apocalypse_firstlight:block/${jointPrefix}_positive_xy_joint` } },
    { when: { horizontal_axis: 'z', joint_negative: 'true' }, apply: { model: `apocalypse_firstlight:block/${jointPrefix}_negative_zy_joint` } },
    { when: { horizontal_axis: 'z', joint_positive: 'true' }, apply: { model: `apocalypse_firstlight:block/${jointPrefix}_positive_zy_joint` } }
  ]});
  write(`${assetRoot}/models/item/${id}.json`, { parent: `apocalypse_firstlight:block/${id}_xy` });
}

function bbmodel(name, elements) {
  return {
    meta: { format_version: '5.0', model_format: 'free', box_uv: false },
    name,
    resolution: { width: 64, height: 64 },
    elements: elements.map((element, index) => ({
      ...clone(element),
      type: 'cube', box_uv: false, origin: [8, 8, 8], color: index % 10,
      uuid: `fixed-${name}-${index}`,
      faces: Object.fromEntries(Object.entries(element.faces).map(([direction, face]) => [direction, { ...clone(face), texture: 0 }]))
    })),
    textures: [{
      name: 'steel_beam.png', relative_path: '../steel_beam/steel_beam.png', id: '0',
      width: 64, height: 64, uv_width: 64, uv_height: 64,
      path: 'src/main/blockbench/steel_beam/steel_beam.png'
    }]
  };
}
write(`${sourceRoot}/diagonal_brace_a_1x1.bbmodel`, bbmodel('diagonal_brace_a_1x1', aXY.elements));
write(`${sourceRoot}/diagonal_brace_b_1x1.bbmodel`, bbmodel('diagonal_brace_b_1x1', bXY.elements));
write(`${sourceRoot}/cross_brace_1x1.bbmodel`, bbmodel('cross_brace_1x1', models.cross_brace_xy.elements));
for (const [name, value] of Object.entries(jointModels)) {
  write(`${sourceRoot}/${name}_joint.bbmodel`, bbmodel(`${name}_joint`, value.elements));
}

function rotatedBounds(element) {
  const { origin, axis, angle } = element.rotation;
  const radians = angle * Math.PI / 180;
  const c = Math.cos(radians), s = Math.sin(radians), points = [];
  for (const x of [element.from[0], element.to[0]]) {
    for (const y of [element.from[1], element.to[1]]) {
      for (const z of [element.from[2], element.to[2]]) {
        const p = [x - origin[0], y - origin[1], z - origin[2]];
        points.push(axis === 'z'
          ? [origin[0] + c * p[0] - s * p[1], origin[1] + s * p[0] + c * p[1], z]
          : [x, origin[1] + c * p[1] - s * p[2], origin[2] + s * p[1] + c * p[2]]);
      }
    }
  }
  return [0, 1, 2].map(axisIndex => [
    Math.min(...points.map(point => point[axisIndex])),
    Math.max(...points.map(point => point[axisIndex]))
  ]);
}

for (const [name, value] of Object.entries(models)) {
  assert.equal(value.elements.length, name.startsWith('cross_brace') ? 68 : 34);
  for (const element of value.elements) rotatedBounds(element);
}
for (const [name, value] of Object.entries(jointModels)) {
  const expected = name.startsWith('cross_') ? 12 : 6;
  assert.equal(value.elements.length, expected);
  assert.equal(value.textures['0'], 'apocalypse_firstlight:block/steel_beam');
}

// Exact reuse audit: geometry structure and UVs are unchanged; only the beam's
// longitudinal coordinate is stretched by sqrt(2), then the whole member rotates.
for (const candidate of [aXY, bXY]) {
  candidate.elements.forEach((element, index) => {
    const source = beam.elements[index];
    assert.deepEqual(element.from, stretchAlongBeam(source.from));
    assert.deepEqual(element.to, stretchAlongBeam(source.to));
    assert.deepEqual(element.faces, source.faces);
  });
}
const centerline = (angle, y) => {
  const radians = angle * Math.PI / 180, c = Math.cos(radians), s = Math.sin(radians);
  const x = 0, localY = y - 8;
  return [8 + c * x - s * localY, 8 + s * x + c * localY];
};
assert.ok(centerline(-45, 8 - 8 * diagonalScale).every(value => Math.abs(value) < 1e-9));
assert.ok(centerline(-45, 8 + 8 * diagonalScale).every(value => Math.abs(value - 16) < 1e-9));
console.log(JSON.stringify({
  staticCheck: 'PASS', beamGeometryReusedWithLongitudinalStretch: true, uvUnchanged: true,
  sourceLength: 16, stretchedLength: 16 * diagonalScale,
  beamElements: 34, aElements: 34, bElements: 34, xElements: 68,
  crossLayerOffset,
  jointModels: Object.fromEntries(Object.entries(jointModels).map(([name, value]) => [name, value.elements.length])),
  texture: beam.textures['0']
}, null, 2));
