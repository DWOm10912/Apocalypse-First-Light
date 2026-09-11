import {execFileSync} from 'node:child_process';
import {mkdir, readFile, writeFile} from 'node:fs/promises';

const call = (name, args = {}) => JSON.parse(execFileSync(
  process.execPath,
  ['tools/water-dispenser-mcp.mjs', name, JSON.stringify(args)],
  {encoding: 'utf8', maxBuffer: 4_000_000}
));

const result = call('export_model', {codec_id: 'project', max_content_length: 2_000_000});
if (result.truncated) throw new Error('Blockbench project export was truncated');
const model = JSON.parse(result.content);
if (model.name !== 'office_keyboard') throw new Error(`Unexpected live project: ${model.name}`);
if (model.elements.length !== 34) throw new Error(`Expected 34 cubes, got ${model.elements.length}`);
if (model.textures.length !== 1) throw new Error(`Expected one texture, got ${model.textures.length}`);

const texture = model.textures[0];
if (texture.width !== 64 || texture.height !== 64) throw new Error('Expected a 64x64 texture');
if (!texture.source?.startsWith('data:image/png;base64,')) throw new Error('Missing embedded texture');

for (const element of model.elements) {
  for (const face of Object.values(element.faces)) {
    if (face.texture !== 0 && face.texture !== texture.uuid) {
      throw new Error(`Incorrect texture binding on ${element.name}`);
    }
    if (face.uv.some(value => value < 0 || value > 64)) {
      throw new Error(`Out-of-range UV on ${element.name}`);
    }
  }
}

for (let i = 0; i < model.elements.length; i++) {
  const a = model.elements[i];
  for (let j = i + 1; j < model.elements.length; j++) {
    const b = model.elements[j];
    if ([0, 1, 2].every(axis => Math.min(a.to[axis], b.to[axis]) - Math.max(a.from[axis], b.from[axis]) > 1e-7)) {
      throw new Error(`Overlapping volumes: ${a.name} / ${b.name}`);
    }
  }
}

const mins = [Infinity, Infinity, Infinity];
const maxs = [-Infinity, -Infinity, -Infinity];
for (const element of model.elements) {
  for (let axis = 0; axis < 3; axis++) {
    mins[axis] = Math.min(mins[axis], element.from[axis]);
    maxs[axis] = Math.max(maxs[axis], element.to[axis]);
  }
}
const size = maxs.map((value, axis) => Number((value - mins[axis]).toFixed(4)));
if (JSON.stringify(size) !== JSON.stringify([12.6, 0.94, 3.5])) {
  throw new Error(`Unexpected bounds: ${JSON.stringify(size)}`);
}

const outputs = [
  ['src/main/blockbench/office_keyboard.bbmodel', result.content],
  ['src/main/resources/assets/apocalypse_firstlight/textures/block/office_keyboard.png', Buffer.from(texture.source.split(',')[1], 'base64')]
];
for (const [path, data] of outputs) {
  await mkdir(path.slice(0, path.lastIndexOf('/')), {recursive: true});
  await writeFile(path, data, {flag: 'wx'});
  const actual = await readFile(path);
  if (!actual.equals(Buffer.from(data))) throw new Error(`Write verification failed: ${path}`);
  console.log(path);
}
console.log(`34 cubes; 5 groups; bounds ${size.join(' x ')}; 64x64; no volume overlaps; texture UUID bindings verified`);
