import {execFileSync} from 'node:child_process';
import {mkdir, readFile, writeFile} from 'node:fs/promises';

const call = (name, args = {}) => JSON.parse(execFileSync(
  process.execPath,
  ['tools/water-dispenser-mcp.mjs', name, JSON.stringify(args)],
  {encoding: 'utf8', maxBuffer: 8_000_000}
));

const result = call('export_model', {codec_id: 'project', max_content_length: 2_000_000});
if (result.truncated) throw new Error('Blockbench project export was truncated');
const model = JSON.parse(result.content);
if (model.name !== 'office_cubicle_partition') throw new Error(`Unexpected live project: ${model.name}`);
if (model.elements.length !== 42) throw new Error(`Expected 42 cubes, got ${model.elements.length}`);
if (model.textures.length !== 1) throw new Error(`Expected one texture, got ${model.textures.length}`);

const texture = model.textures[0];
if (texture.width !== 128 || texture.height !== 128) throw new Error('Expected a 128x128 texture');
if (!texture.source?.startsWith('data:image/png;base64,')) throw new Error('Missing embedded texture');

for (const element of model.elements) {
  if (element.from.some((value, axis) => value >= element.to[axis])) {
    throw new Error(`Degenerate cube: ${element.name}`);
  }
  for (const face of Object.values(element.faces)) {
    if (face.texture !== 0 && face.texture !== texture.uuid) {
      throw new Error(`Incorrect texture binding on ${element.name}`);
    }
    if (face.uv.some(value => value < 0 || value > 128)) {
      throw new Error(`Out-of-range UV on ${element.name}`);
    }
  }
}

const mins = [0, 1, 2].map(axis => Math.min(...model.elements.map(element => element.from[axis])));
const maxs = [0, 1, 2].map(axis => Math.max(...model.elements.map(element => element.to[axis])));
const size = maxs.map((value, axis) => Number((value - mins[axis]).toFixed(4)));
if (JSON.stringify(size) !== JSON.stringify([16, 32, 2.6])) {
  throw new Error(`Unexpected bounds: ${JSON.stringify(size)}`);
}

const outputs = [
  ['src/main/blockbench/office_cubicle_partition.bbmodel', result.content],
  ['src/main/resources/assets/apocalypse_firstlight/textures/block/office_cubicle_partition.png', Buffer.from(texture.source.split(',')[1], 'base64')]
];
const updateHeight2 = process.argv.includes('--update-height-2');
const updateHeight = process.argv.includes('--update-height') || updateHeight2;
if (updateHeight) {
  const previous = JSON.parse(await readFile(outputs[0][0], 'utf8'));
  const previousMins = [0, 1, 2].map(axis => Math.min(...previous.elements.map(element => element.from[axis])));
  const previousMaxs = [0, 1, 2].map(axis => Math.max(...previous.elements.map(element => element.to[axis])));
  const previousSize = previousMaxs.map((value, axis) => Number((value - previousMins[axis]).toFixed(4)));
  const expectedPrevious = updateHeight2 ? [16, 24, 2.6] : [16, 20, 2.6];
  if (JSON.stringify(previousSize) !== JSON.stringify(expectedPrevious)) throw new Error('Unexpected source height before update');
  const backup = updateHeight2
    ? 'build/asset_checks/office_cubicle_partition/pre_height_2_blocks'
    : 'build/asset_checks/office_cubicle_partition/pre_height';
  await mkdir(backup, {recursive: true});
  await writeFile(`${backup}/office_cubicle_partition.bbmodel`, await readFile(outputs[0][0]), {flag: 'wx'});
  await writeFile(`${backup}/office_cubicle_partition.png`, await readFile(outputs[1][0]), {flag: 'wx'});
}
for (const [path, data] of outputs) {
  await mkdir(path.slice(0, path.lastIndexOf('/')), {recursive: true});
  await writeFile(path, data, {flag: updateHeight ? 'w' : 'wx'});
  const actual = await readFile(path);
  if (!actual.equals(Buffer.from(data))) throw new Error(`Write verification failed: ${path}`);
  console.log(path);
}
console.log(`42 cubes; ${model.groups.length} groups; bounds ${size.join(' x ')}; 128x128; texture UUID bindings verified`);
