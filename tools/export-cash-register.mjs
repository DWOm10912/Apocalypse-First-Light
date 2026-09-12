import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const sourcePath = path.join(root, 'src/main/blockbench/afl_cash_register.bbmodel');
const sourceTexturePath = path.join(root, 'src/main/blockbench/textures/afl_cash_register.png');
const modelPath = path.join(root, 'src/main/resources/assets/apocalypse_firstlight/models/block/cash_register.json');
const texturePath = path.join(root, 'src/main/resources/assets/apocalypse_firstlight/textures/block/cash_register.png');
const textureId = 'apocalypse_firstlight:block/cash_register';
const directions = ['north', 'east', 'south', 'west', 'up', 'down'];
const source = JSON.parse(fs.readFileSync(sourcePath, 'utf8'));

assert.equal(source.meta.model_format, 'free');
assert.deepEqual(source.resolution, { width: 128, height: 128 });
assert.equal(source.elements.length, 145);
assert.equal(source.textures.length, 1);
const embedded = Buffer.from(source.textures[0].source.replace(/^data:image\/png;base64,/, ''), 'base64');
const external = fs.readFileSync(sourceTexturePath);
assert.ok(embedded.equals(external), 'Blockbench embedded atlas differs from source PNG');

const round = value => Number(value.toFixed(6));
const offset = coordinates => coordinates.map((value, axis) => round(value + (axis === 1 ? 0 : 8)));
const elements = source.elements.map(cube => {
  assert.equal(cube.type, 'cube');
  const from = offset(cube.from);
  const to = offset(cube.to);
  for (let axis = 0; axis < 3; axis++) {
    assert.ok(from[axis] >= 0 && to[axis] <= 16 && from[axis] <= to[axis],
      `out of block: ${cube.name} axis ${axis}`);
  }
  const element = { name: cube.name, from, to };
  if (cube.rotation?.some(angle => angle !== 0)) {
    assert.deepEqual(cube.rotation, [-22.5, 0, 0], `unexpected rotation: ${cube.name}`);
    element.rotation = { angle: -22.5, axis: 'x', origin: offset(cube.origin) };
  }
  element.faces = {};
  for (const direction of directions) {
    const face = cube.faces[direction];
    assert.ok(face && face.texture === 0, `missing texture: ${cube.name} ${direction}`);
    element.faces[direction] = {
      uv: face.uv.map(value => round(value / 8)), texture: '#0',
    };
  }
  return element;
});

const runtime = {
  parent: 'minecraft:block/block',
  ambientocclusion: false,
  texture_size: [128, 128],
  textures: { '0': textureId, particle: textureId },
  elements,
};
const encoded = `${JSON.stringify(runtime, null, 2)}\n`;
if (process.argv.includes('--write')) {
  fs.writeFileSync(modelPath, encoded);
  fs.copyFileSync(sourceTexturePath, texturePath);
} else if (process.argv.includes('--check')) {
  assert.equal(fs.readFileSync(modelPath, 'utf8'), encoded, 'runtime model is stale');
  assert.ok(fs.readFileSync(texturePath).equals(external), 'runtime texture is stale');
} else {
  throw new Error('Pass --write or --check');
}
process.stdout.write(`cash_register: ${elements.length} textured cubes, ${elements.filter(e => e.rotation).length} sloped cubes\n`);
