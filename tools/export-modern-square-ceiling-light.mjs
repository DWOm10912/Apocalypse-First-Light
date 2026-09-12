import fs from 'node:fs';
import path from 'node:path';

const root = path.resolve(import.meta.dirname, '..');
const sourcePath = path.join(root, 'src/main/blockbench/modern_square_ceiling_light.bbmodel');
const runtimePath = path.join(root, 'src/main/resources/assets/apocalypse_firstlight/models/block/industrial_utility_light.json');
const sourceTexturePath = path.join(root, 'src/main/blockbench/textures/modern_square_ceiling_light.png');
const runtimeTexturePath = path.join(root, 'src/main/resources/assets/apocalypse_firstlight/textures/block/industrial_utility_light.png');
const source = JSON.parse(fs.readFileSync(sourcePath, 'utf8'));
const oldRuntime = JSON.parse(fs.readFileSync(runtimePath, 'utf8'));

if (source.elements.length !== 23 || source.resolution.width !== 128 || source.resolution.height !== 128) {
  throw new Error('Unexpected ceiling-light source model; review the export conversion.');
}

// The existing blockstates expect a floor-facing base model and rotate it onto the ceiling.
// The editable Blockbench source is authored ceiling-first, so only the runtime geometry is inverted.
const texture = 'apocalypse_firstlight:block/industrial_utility_light';
const convertFace = face => face?.texture === null ? undefined : {
  uv: face.uv.map(value => value / 8),
  texture: '#0',
};
const invertedY = value => Math.round((16 - value) * 10000) / 10000;
const elements = source.elements.map(element => {
  const faces = {};
  for (const direction of ['north', 'east', 'south', 'west', 'up', 'down']) {
    const sourceDirection = direction === 'up' ? 'down' : direction === 'down' ? 'up' : direction;
    const face = convertFace(element.faces[sourceDirection]);
    if (face) faces[direction] = face;
  }
  return {
    name: element.name,
    from: [element.from[0], invertedY(element.to[1]), element.from[2]],
    to: [element.to[0], invertedY(element.from[1]), element.to[2]],
    ...(element.shade === false ? {shade: false} : {}),
    faces,
  };
});

const runtime = {
  format_version: '1.9.0',
  credit: 'Made with Blockbench',
  ambientocclusion: false,
  texture_size: [128, 128],
  textures: {'0': texture, particle: texture},
  elements,
  display: oldRuntime.display,
};

fs.writeFileSync(runtimePath, JSON.stringify(runtime, null, 2) + '\n');
fs.copyFileSync(sourceTexturePath, runtimeTexturePath);
console.log(`Exported ${elements.length} cubes to ${runtimePath}`);
