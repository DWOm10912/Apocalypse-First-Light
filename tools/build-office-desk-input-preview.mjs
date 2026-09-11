import {execFileSync} from 'node:child_process';
import {mkdir, readFile, writeFile} from 'node:fs/promises';

const paths = [
  'src/main/blockbench/modern_office_desk.bbmodel',
  'src/main/blockbench/modern_lcd_monitor.bbmodel',
  'src/main/blockbench/office_keyboard.bbmodel',
  'src/main/blockbench/office_mouse.bbmodel'
];
const models = await Promise.all(paths.map(async path => JSON.parse(await readFile(path, 'utf8'))));
const [desk, monitor, keyboard, mouse] = models;
const placements = [
  {model: desk, name: 'preview_desk', offset: [0, 0, 0], rotationY: 0, mirrorX: false},
  {model: monitor, name: 'preview_monitor', offset: [0, 13.5, 3.2], rotationY: 0, mirrorX: false},
  {model: keyboard, name: 'preview_keyboard', offset: [1.2, 13.5, -1.9], rotationY: 0, mirrorX: true},
  {model: mouse, name: 'preview_mouse', offset: [-6.7, 13.5, -2], rotationY: 180, mirrorX: false}
];

const elements = [];
const groups = [];
const textures = [];
const componentRoots = [];
for (let textureIndex = 0; textureIndex < placements.length; textureIndex++) {
  const {model, name, offset, rotationY, mirrorX} = placements[textureIndex];
  const [dx, dy, dz] = offset;
  if (rotationY === 180 || mirrorX) {
    for (const element of model.elements) {
      const rotatedFromX = 16 - element.to[0];
      const rotatedToX = 16 - element.from[0];
      element.from[0] = rotatedFromX;
      element.to[0] = rotatedToX;
      element.origin[0] = 16 - element.origin[0];
      if (rotationY === 180) {
        const rotatedFromZ = 16 - element.to[2];
        const rotatedToZ = 16 - element.from[2];
        element.from[2] = rotatedFromZ;
        element.to[2] = rotatedToZ;
        element.origin[2] = 16 - element.origin[2];
      }
      const directionMap = rotationY === 180
        ? {north: 'south', south: 'north', east: 'west', west: 'east'}
        : {east: 'west', west: 'east'};
      element.faces = Object.fromEntries(Object.entries(element.faces).map(([direction, face]) =>
        [directionMap[direction] ?? direction, face]));
    }
    for (const group of model.groups) {
      group.origin[0] = 16 - group.origin[0];
      if (rotationY === 180) group.origin[2] = 16 - group.origin[2];
    }
  }
  for (const element of model.elements) {
    element.from[0] += dx;
    element.from[1] += dy;
    element.from[2] += dz;
    element.to[0] += dx;
    element.to[1] += dy;
    element.to[2] += dz;
    element.origin[0] += dx;
    element.origin[1] += dy;
    element.origin[2] += dz;
    for (const face of Object.values(element.faces)) face.texture = textureIndex;
  }
  for (const group of model.groups) {
    group.origin[0] += dx;
    group.origin[1] += dy;
    group.origin[2] += dz;
  }
  const componentRoot = model.groups.find(group => group.uuid === model.outliner[0].uuid);
  componentRoot.name = name;
  componentRoot.rotation = [0, 0, 0];
  componentRoots.push(model.outliner[0]);
  const texture = model.textures[0];
  texture.id = String(textureIndex);
  texture.particle = textureIndex === 0;
  textures.push(texture);
  elements.push(...model.elements);
  groups.push(...model.groups);
}

const rootUuid = '89610d13-40d2-40b7-a8f5-47d6edfa95b4';
const root = {
  name: 'office_desk_input_preview_root',
  uuid: rootUuid,
  export: true,
  locked: false,
  scope: 0,
  selected: false,
  _static: {properties: {}, temp_data: {}},
  origin: [8, 0, 8],
  rotation: [0, 0, 0],
  color: 0,
  children: [],
  reset: false,
  shade: true,
  mirror_uv: false,
  visibility: true,
  autouv: 0,
  isOpen: true,
  primary_selected: false
};
const preview = {
  ...desk,
  name: 'office_desk_input_preview',
  ambientocclusion: false,
  elements,
  groups: [root, ...groups],
  outliner: [{uuid: rootUuid, isOpen: true, children: componentRoots}],
  textures
};
const directory = 'build/asset_checks/office_desk_input_preview';
const output = `${directory}/office_desk_input_preview.bbmodel`;
await mkdir(directory, {recursive: true});
await writeFile(output, JSON.stringify(preview));

const packed = Buffer.from(JSON.stringify(preview)).toString('base64url');
const liveCode = `(() => {const raw='${packed}'.replaceAll('-','+').replaceAll('_','/');const model=JSON.parse(new TextDecoder().decode(Uint8Array.from(atob(raw),character=>character.charCodeAt(0))));newProject(Formats.java_block);Codecs.project.parse(model,'D:/Minecraft Modding/Apocalypse First Light/${output}');Project.save_path='D:/Minecraft Modding/Apocalypse First Light/${output}';for(const preview of Preview.all){if(!preview.controls||!preview.camera)continue;preview.controls.target.set(8,12,8);preview.camera.position.set(55,34,-48);preview.camera.zoom=.78;preview.camera.updateProjectionMatrix();preview.controls.update();}Canvas.updateAll();return {name:Project.name,cubes:Cube.all.length,groups:Group.all.length,textures:Texture.all.length};})()`;
const loader = `${directory}/load-preview.blockbench.js`;
await writeFile(loader, liveCode);

console.log(JSON.stringify({
  output,
  loader,
  cubes: elements.length,
  groups: groups.length + 1,
  textures: textures.length,
  placements: placements.map(({name, offset, rotationY, mirrorX}) => ({name, offset, rotationY, mirrorX}))
}));
if (!process.argv.includes('--no-load')) {
  console.log(execFileSync(process.execPath, ['tools/water-dispenser-mcp.mjs', 'eval', loader], {
    encoding: 'utf8',
    maxBuffer: 8_000_000
  }).trim());
}
