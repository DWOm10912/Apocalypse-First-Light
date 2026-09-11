import {randomUUID} from 'node:crypto';
import {mkdir, readFile, writeFile} from 'node:fs/promises';

const basePath = 'build/asset_checks/office_desk_input_preview/office_desk_input_preview.bbmodel';
const base = JSON.parse(await readFile(basePath, 'utf8'));
const chair = JSON.parse(await readFile('src/main/blockbench/modern_office_chair.bbmodel', 'utf8'));
const partition = JSON.parse(await readFile('src/main/blockbench/office_cubicle_partition.bbmodel', 'utf8'));

const shiftModel = (model, offset) => {
  for (const element of model.elements) {
    for (let axis = 0; axis < 3; axis++) {
      element.from[axis] += offset[axis];
      element.to[axis] += offset[axis];
      if (element.origin) element.origin[axis] += offset[axis];
    }
  }
  for (const group of model.groups ?? []) {
    for (let axis = 0; axis < 3; axis++) group.origin[axis] += offset[axis];
  }
};

const chairTexture = chair.textures[0];
chairTexture.id = '4';
chairTexture.particle = false;
for (const element of chair.elements) {
  for (const face of Object.values(element.faces)) face.texture = chairTexture.uuid;
}
shiftModel(chair, [0, 0, -9.6]);
const chairRoot = chair.groups.find(group => group.uuid === chair.outliner[0].uuid);
chairRoot.name = 'preview_office_chair';
chairRoot.rotation = [0, 180, 0];

const duplicatePartition = (index, offset) => {
  const uuidMap = new Map();
  for (const element of partition.elements) uuidMap.set(element.uuid, randomUUID());
  for (const group of partition.groups) uuidMap.set(group.uuid, randomUUID());
  const elements = partition.elements.map(source => {
    const element = structuredClone(source);
    element.uuid = uuidMap.get(source.uuid);
    for (let axis = 0; axis < 3; axis++) {
      element.from[axis] += offset[axis];
      element.to[axis] += offset[axis];
      if (element.origin) element.origin[axis] += offset[axis];
    }
    for (const face of Object.values(element.faces)) face.texture = partition.textures[0].uuid;
    return element;
  });
  const groups = partition.groups.map(source => {
    const group = structuredClone(source);
    group.uuid = uuidMap.get(source.uuid);
    group.name = `${source.name}_${index}`;
    for (let axis = 0; axis < 3; axis++) group.origin[axis] += offset[axis];
    return group;
  });
  const remapOutliner = node => {
    if (typeof node === 'string') return uuidMap.get(node);
    const copy = structuredClone(node);
    copy.uuid = uuidMap.get(node.uuid);
    copy.name = `${node.name}_${index}`;
    copy.children = (node.children ?? []).map(remapOutliner);
    return copy;
  };
  return {elements, groups, outliner: partition.outliner.map(remapOutliner)};
};

const modules = [
  duplicatePartition(1, [-16, 0, 8.7]),
  duplicatePartition(2, [0, 0, 8.7]),
  duplicatePartition(3, [16, 0, 8.7])
];
const partitionTexture = structuredClone(partition.textures[0]);
partitionTexture.id = '5';
partitionTexture.particle = false;

const rootUuid = randomUUID();
const root = {
  name: 'office_cubicle_preview_root', uuid: rootUuid, export: true, locked: false,
  scope: 0, selected: false, origin: [8, 0, 8], rotation: [0, 0, 0], color: 0,
  children: [], reset: false, shade: true, mirror_uv: false, visibility: true,
  autouv: 0, isOpen: true, primary_selected: false
};
const priorOutliner = base.outliner;
const preview = {
  ...base,
  name: 'office_cubicle_workstation_preview',
  elements: [...base.elements, ...chair.elements, ...modules.flatMap(module => module.elements)],
  groups: [root, ...base.groups, ...chair.groups, ...modules.flatMap(module => module.groups)],
  outliner: [{...root, children: [...priorOutliner, chair.outliner[0], ...modules.flatMap(module => module.outliner)]}],
  textures: [...base.textures, chairTexture, partitionTexture]
};

const directory = 'build/asset_checks/office_cubicle_preview';
const output = `${directory}/office_cubicle_workstation_preview.bbmodel`;
await mkdir(directory, {recursive: true});
await writeFile(output, JSON.stringify(preview));
const packed = Buffer.from(JSON.stringify(preview)).toString('base64url');
const live = `(() => {const raw='${packed}'.replaceAll('-','+').replaceAll('_','/');const model=JSON.parse(new TextDecoder().decode(Uint8Array.from(atob(raw),c=>c.charCodeAt(0))));newProject(Formats.java_block);Codecs.project.parse(model,'D:/Minecraft Modding/Apocalypse First Light/${output}');Project.save_path='D:/Minecraft Modding/Apocalypse First Light/${output}';for(const p of Preview.all){if(!p.controls||!p.camera)continue;p.controls.target.set(8,10,7);p.camera.position.set(62,38,-67);p.camera.zoom=.72;p.camera.updateProjectionMatrix();p.controls.update();}Canvas.updateAll();return {name:Project.name,cubes:Cube.all.length,groups:Group.all.length,textures:Texture.all.length};})()`;
await writeFile(`${directory}/load-preview.blockbench.js`, live);
console.log(JSON.stringify({output, cubes: preview.elements.length, groups: preview.groups.length, textures: preview.textures.length, partitionModules: modules.length}));
