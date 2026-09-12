import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const sourcePath = path.join(root, 'src/main/blockbench/afl_supermarket_shelf_v2_review.bbmodel');
const sourceTexturePath = path.join(root, 'src/main/blockbench/textures/afl_supermarket_shelf_v2.png');
const modelPath = path.join(root, 'src/main/resources/assets/apocalypse_firstlight/models/block/retail_shelf_single.json');
const texturePath = path.join(root, 'src/main/resources/assets/apocalypse_firstlight/textures/block/retail_shelf_single.png');
const textureId = 'apocalypse_firstlight:block/retail_shelf_single';
const directions = ['north', 'east', 'south', 'west', 'up', 'down'];

const source = JSON.parse(fs.readFileSync(sourcePath, 'utf8'));
const oldRuntime = JSON.parse(fs.readFileSync(modelPath, 'utf8'));
assert.equal(source.meta.model_format, 'free');
assert.deepEqual(source.resolution, { width: 128, height: 128 });
assert.equal(source.elements.length, 171);
assert.equal(source.textures.length, 1);

const embedded = Buffer.from(source.textures[0].source.replace(/^data:image\/png;base64,/, ''), 'base64');
const external = fs.readFileSync(sourceTexturePath);
assert.ok(embedded.equals(external), 'editable model atlas differs from its PNG');

const elements = source.elements.map((cube) => {
  assert.equal(cube.type, 'cube');
  assert.ok(!cube.rotation || cube.rotation.every((angle) => angle === 0), `rotated cube: ${cube.name}`);
  const from = [cube.from[0] + 8, cube.from[1], cube.from[2] + 8]
    .map((coordinate) => Number(coordinate.toFixed(3)));
  const to = [cube.to[0] + 8, cube.to[1], cube.to[2] + 8]
    .map((coordinate) => Number(coordinate.toFixed(3)));
  for (const axis of [0, 2]) {
    assert.ok(from[axis] >= 0 && to[axis] <= 16, `out-of-block cube: ${cube.name}`);
  }
  assert.ok(from[1] >= 0 && to[1] <= 32, `out-of-height cube: ${cube.name}`);
  const faces = {};
  for (const direction of directions) {
    const face = cube.faces[direction];
    assert.ok(face && face.texture === 0, `missing atlas face ${direction}: ${cube.name}`);
    faces[direction] = {
      uv: face.uv.map((coordinate) => Number((coordinate / 8).toFixed(6))),
      texture: '#0',
    };
  }
  return { name: cube.name, from, to, faces };
});

const decks = elements.filter((cube) => /^tier_0[1-5]_load_deck$/.test(cube.name));
assert.equal(decks.length, 5);
for (const deck of decks) {
  assert.ok(Math.abs(deck.to[2] - deck.from[2] - 8.75) < 1e-6);
}
assert.equal(Math.max(...elements.map((cube) => cube.to[2])), 16);
assert.equal(Math.max(...elements.map((cube) => cube.to[1])), 32);

const runtime = {
  ambientocclusion: false,
  texture_size: [128, 128],
  textures: { '0': textureId, particle: textureId },
  elements,
  display: oldRuntime.display,
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

process.stdout.write(`retail_shelf_single: ${elements.length} cubes, ${decks.length} decks, north front, south back at Z=16\n`);
