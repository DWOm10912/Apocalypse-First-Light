import {randomUUID} from 'node:crypto';
import {mkdir, readFile, writeFile} from 'node:fs/promises';

const source = JSON.parse(await readFile('src/main/blockbench/office_cubicle_partition.bbmodel', 'utf8'));
const workstation = JSON.parse(await readFile('build/asset_checks/office_desk_input_preview/office_desk_input_preview.bbmodel', 'utf8'));
const chair = JSON.parse(await readFile('src/main/blockbench/modern_office_chair.bbmodel', 'utf8'));
if (source.elements.length !== 42) throw new Error('Expected approved 42-cube partition source');
if (Math.max(...source.elements.map(cube => cube.to[1])) !== 32) throw new Error('Expected approved 2-block partition source');

const elements = [];
const groups = [];
const outliner = [];
const textures = [];

const makeGroup = (name, parent = null, origin = [0, 0, 0]) => {
  const group = {
    name, uuid: randomUUID(), export: true, locked: false, scope: 0, selected: false,
    origin, rotation: [0, 0, 0], color: groups.length % 8, children: [], reset: false,
    shade: true, mirror_uv: false, visibility: true, autouv: 0, isOpen: true,
    primary_selected: false
  };
  groups.push(group);
  if (parent) parent.children.push(group);
  else outliner.push(group);
  return group;
};

const root = makeGroup('connection_preview_root');
const scenarioNames = [
  'preview_single', 'preview_straight_2', 'preview_straight_3',
  'preview_corner_L', 'preview_U_cubicle', 'preview_T_junction', 'preview_cross_optional'
];
const scenarios = Object.fromEntries(scenarioNames.map(name => [name, makeGroup(name, root)]));

const partitionTexture = structuredClone(source.textures[0]);
partitionTexture.id = '0';
partitionTexture.particle = true;
textures.push(partitionTexture);
const uv = index => {
  const x = (index % 4) * 32;
  const y = Math.floor(index / 4) * 32;
  return [x + 1, y + 1, x + 31, y + 31];
};

const rotatePoint = (x, z, rotation) => {
  const normalized = ((rotation % 360) + 360) % 360;
  if (normalized === 0) return [x, z];
  if (normalized === 90) return [-z, x];
  if (normalized === 180) return [-x, -z];
  if (normalized === 270) return [z, -x];
  throw new Error(`Unsupported rotation ${rotation}`);
};
const transformBox = (from, to, offset, rotation = 0) => {
  const points = [
    rotatePoint(from[0], from[2], rotation), rotatePoint(from[0], to[2], rotation),
    rotatePoint(to[0], from[2], rotation), rotatePoint(to[0], to[2], rotation)
  ];
  return [
    [Math.min(...points.map(point => point[0])) + offset[0], from[1] + offset[1], Math.min(...points.map(point => point[1])) + offset[2]],
    [Math.max(...points.map(point => point[0])) + offset[0], to[1] + offset[1], Math.max(...points.map(point => point[1])) + offset[2]]
  ];
};
const addCube = (name, from, to, material, parent, offset = [0, 0, 0], rotation = 0) => {
  const [worldFrom, worldTo] = transformBox(from, to, offset, rotation);
  const faces = {};
  for (const direction of ['north', 'south', 'east', 'west', 'up', 'down']) {
    faces[direction] = {uv: uv(material), texture: partitionTexture.uuid};
  }
  const cube = {
    name, rescale: false, locked: false, from: worldFrom, to: worldTo,
    autouv: 0, color: elements.length % 8, origin: [8, 8, 8], rotation: [0, 0, 0],
    faces, type: 'cube', uuid: randomUUID()
  };
  elements.push(cube);
  parent.children.push(cube.uuid);
  return cube;
};

const addPanelSegment = (parent, name, offset, rotation = 0, startTrim = 0, endTrim = 0) => {
  const group = makeGroup(name, parent, offset);
  const start = startTrim;
  const end = 16 - endTrim;
  addCube(`${name}_core`, [start, 3.2, -0.45], [end, 31, 0.45], 8, group, offset, rotation);
  addCube(`${name}_fabric_front`, [start, 3.2, -0.6], [end, 31, -0.45], 4, group, offset, rotation);
  addCube(`${name}_fabric_back`, [start, 3.2, 0.45], [end, 31, 0.6], 4, group, offset, rotation);
  addCube(`${name}_top_rail_core`, [start, 31, -0.75], [end, 31.8, 0.75], 0, group, offset, rotation);
  addCube(`${name}_top_rail_cap`, [start, 31.8, -0.9], [end, 32, 0.9], 1, group, offset, rotation);
  addCube(`${name}_top_front_highlight`, [start, 31, -0.9], [end, 31.8, -0.75], 3, group, offset, rotation);
  addCube(`${name}_top_back_shadow`, [start, 31, 0.75], [end, 31.8, 0.9], 14, group, offset, rotation);
  addCube(`${name}_bottom_rail_core`, [start, 2.45, -0.75], [end, 3.2, 0.75], 0, group, offset, rotation);
  addCube(`${name}_bottom_lower_edge`, [start, 2.3, -0.9], [end, 2.45, 0.9], 2, group, offset, rotation);
  addCube(`${name}_bottom_front_highlight`, [start, 2.45, -0.9], [end, 3.2, -0.75], 3, group, offset, rotation);
  addCube(`${name}_bottom_back_shadow`, [start, 2.45, 0.75], [end, 3.2, 0.9], 14, group, offset, rotation);
  return group;
};

const addEndPost = (parent, name, offset, withFoot = true, rotation = 0) => {
  const group = makeGroup(name, parent, offset);
  addCube(`${name}_core`, [-0.4, 0.55, -0.8], [0.4, 31.8, 0.8], 0, group, offset, rotation);
  addCube(`${name}_outer_spine`, [-0.55, 0.55, -0.95], [-0.4, 31.8, 0.95], 2, group, offset, rotation);
  addCube(`${name}_inner_reveal`, [0.4, 2.7, -0.7], [0.55, 31.35, 0.7], 12, group, offset, rotation);
  addCube(`${name}_front_highlight`, [-0.4, 0.55, -0.95], [0.4, 31.8, -0.8], 3, group, offset, rotation);
  addCube(`${name}_back_shadow`, [-0.4, 0.55, 0.8], [0.4, 31.8, 0.95], 14, group, offset, rotation);
  addCube(`${name}_top_cap`, [-0.55, 31.8, -0.95], [0.55, 32, 0.95], 1, group, offset, rotation);
  if (withFoot) {
    addCube(`${name}_foot_pad`, [-0.8, 0, -1.15], [0.8, 0.15, 1.15], 9, group, offset, rotation);
    addCube(`${name}_foot_base`, [-0.9, 0.15, -1.3], [0.9, 0.35, 1.3], 2, group, offset, rotation);
    addCube(`${name}_foot_collar`, [-0.6, 0.35, -0.85], [0.6, 0.55, 0.85], 0, group, offset, rotation);
  }
  return group;
};

const addJunctionPost = (parent, name, offset, type) => {
  const group = makeGroup(name, parent, offset);
  addCube(`${name}_body`, [-0.7, 0.55, -0.7], [0.7, 31.8, 0.7], 0, group, offset);
  addCube(`${name}_front_highlight`, [-0.7, 0.55, -0.9], [0.7, 31.8, -0.7], 3, group, offset);
  addCube(`${name}_back_shadow`, [-0.7, 0.55, 0.7], [0.7, 31.8, 0.9], 14, group, offset);
  addCube(`${name}_left_edge`, [-0.9, 0.55, -0.7], [-0.7, 31.8, 0.7], 2, group, offset);
  addCube(`${name}_right_edge`, [0.7, 0.55, -0.7], [0.9, 31.8, 0.7], 12, group, offset);
  addCube(`${name}_corner_nw`, [-0.9, 0.55, -0.9], [-0.7, 31.8, -0.7], 2, group, offset);
  addCube(`${name}_corner_ne`, [0.7, 0.55, -0.9], [0.9, 31.8, -0.7], 3, group, offset);
  addCube(`${name}_corner_sw`, [-0.9, 0.55, 0.7], [-0.7, 31.8, 0.9], 14, group, offset);
  addCube(`${name}_corner_se`, [0.7, 0.55, 0.7], [0.9, 31.8, 0.9], 12, group, offset);
  addCube(`${name}_top_cap`, [-0.9, 31.8, -0.9], [0.9, 32, 0.9], 1, group, offset);
  addCube(`${name}_${type}_foot`, [-1.05, 0, -1.05], [1.05, 0.3, 1.05], 9, group, offset);
  addCube(`${name}_foot_collar`, [-0.9, 0.3, -0.9], [0.9, 0.55, 0.9], 0, group, offset);
  return group;
};

const addSeam = (parent, name, offset, rotation = 0) => {
  const group = makeGroup(name, parent, offset);
  addCube(`${name}_front`, [-0.08, 3.2, -0.68], [0.08, 31, -0.6], 12, group, offset, rotation);
  addCube(`${name}_back`, [-0.08, 3.2, 0.6], [0.08, 31, 0.68], 14, group, offset, rotation);
  return group;
};

const importModel = (model, parent, prefix, offset, textureStart) => {
  const uuidMap = new Map();
  for (const element of model.elements) uuidMap.set(element.uuid, randomUUID());
  for (const group of model.groups ?? []) uuidMap.set(group.uuid, randomUUID());
  const textureMap = new Map();
  for (let index = 0; index < model.textures.length; index++) {
    const texture = structuredClone(model.textures[index]);
    const newUuid = randomUUID();
    textureMap.set(model.textures[index].uuid, newUuid);
    texture.uuid = newUuid;
    texture.id = String(textureStart + index);
    texture.particle = false;
    textures.push(texture);
  }
  for (const original of model.elements) {
    const cube = structuredClone(original);
    cube.uuid = uuidMap.get(original.uuid);
    cube.name = `${prefix}_${original.name}`;
    for (let axis = 0; axis < 3; axis++) {
      cube.from[axis] += offset[axis];
      cube.to[axis] += offset[axis];
      if (cube.origin) cube.origin[axis] += offset[axis];
    }
    for (const face of Object.values(cube.faces)) {
      face.texture = textureMap.get(face.texture) ?? textures[textureStart]?.uuid;
    }
    elements.push(cube);
  }
  const importedGroups = new Map();
  for (const original of model.groups ?? []) {
    const group = structuredClone(original);
    group.uuid = uuidMap.get(original.uuid);
    group.name = `${prefix}_${original.name}`;
    for (let axis = 0; axis < 3; axis++) group.origin[axis] += offset[axis];
    groups.push(group);
    importedGroups.set(original.uuid, group);
  }
  const remapNode = node => {
    if (typeof node === 'string') return uuidMap.get(node);
    const group = importedGroups.get(node.uuid);
    const copy = {...group, children: (node.children ?? []).map(remapNode)};
    return copy;
  };
  for (const node of model.outliner ?? []) parent.children.push(remapNode(node));
};

const singleOffset = [0, 0, 0];
importModel(source, scenarios.preview_single, 'single', [0, 0, -8], 1);

const straight = (scenario, count, origin) => {
  const segmentRoot = makeGroup('panel_segments', scenario, origin);
  const postRoot = makeGroup('end_posts', scenario, origin);
  const seamRoot = makeGroup('narrow_seams', scenario, origin);
  for (let index = 0; index < count; index++) {
    addPanelSegment(segmentRoot, `panel_segment_${index + 1}`, [origin[0] + index * 16, origin[1], origin[2]], 0, index === 0 ? 0.55 : 0, index === count - 1 ? 0.55 : 0);
    if (index > 0) addSeam(seamRoot, `seam_${index}`, [origin[0] + index * 16, origin[1], origin[2]]);
  }
  addEndPost(postRoot, 'end_post_left', origin);
  addEndPost(postRoot, 'end_post_right', [origin[0] + count * 16, origin[1], origin[2]]);
};
straight(scenarios.preview_straight_2, 2, [32, 0, 0]);
straight(scenarios.preview_straight_3, 3, [80, 0, 0]);

const cornerOrigin = [0, 0, 64];
const cornerPanels = makeGroup('panel_segments', scenarios.preview_corner_L, cornerOrigin);
const cornerPosts = makeGroup('corner_and_end_posts', scenarios.preview_corner_L, cornerOrigin);
addPanelSegment(cornerPanels, 'corner_panel_horizontal', cornerOrigin, 0, 0.55, 0.9);
addPanelSegment(cornerPanels, 'corner_panel_vertical', [cornerOrigin[0] + 16, 0, cornerOrigin[2]], 90, 0.9, 0.55);
addEndPost(cornerPosts, 'corner_end_post_horizontal', cornerOrigin);
addJunctionPost(cornerPosts, 'corner_post', [cornerOrigin[0] + 16, 0, cornerOrigin[2]], 'corner');
addEndPost(cornerPosts, 'corner_end_post_vertical', [cornerOrigin[0] + 16, 0, cornerOrigin[2] + 16], true, 90);

const uOrigin = [40, 0, 64];
const uPanels = makeGroup('panel_segments', scenarios.preview_U_cubicle, uOrigin);
const uPosts = makeGroup('corner_and_end_posts', scenarios.preview_U_cubicle, uOrigin);
const uSeams = makeGroup('narrow_seams', scenarios.preview_U_cubicle, uOrigin);
for (let index = 0; index < 3; index++) addPanelSegment(uPanels, `u_back_panel_${index + 1}`, [uOrigin[0] + index * 16, 0, uOrigin[2] + 16], 0, index === 0 ? 0.9 : 0, index === 2 ? 0.9 : 0);
addSeam(uSeams, 'u_back_seam_1', [uOrigin[0] + 16, 0, uOrigin[2] + 16]);
addSeam(uSeams, 'u_back_seam_2', [uOrigin[0] + 32, 0, uOrigin[2] + 16]);
addPanelSegment(uPanels, 'u_left_panel', [uOrigin[0], 0, uOrigin[2] + 16], 270, 0.9, 0.55);
addPanelSegment(uPanels, 'u_right_panel', [uOrigin[0] + 48, 0, uOrigin[2]], 90, 0.55, 0.9);
addJunctionPost(uPosts, 'u_corner_post_left', [uOrigin[0], 0, uOrigin[2] + 16], 'corner');
addJunctionPost(uPosts, 'u_corner_post_right', [uOrigin[0] + 48, 0, uOrigin[2] + 16], 'corner');
addEndPost(uPosts, 'u_end_post_left', [uOrigin[0], 0, uOrigin[2]], true, 270);
addEndPost(uPosts, 'u_end_post_right', [uOrigin[0] + 48, 0, uOrigin[2]], true, 90);
importModel(workstation, scenarios.preview_U_cubicle, 'u_workstation', [uOrigin[0] + 16, 0, uOrigin[2]], textures.length);
const chairCopy = structuredClone(chair);
const chairRoot = chairCopy.groups.find(group => group.uuid === chairCopy.outliner[0].uuid);
chairRoot.rotation = [0, 180, 0];
importModel(chairCopy, scenarios.preview_U_cubicle, 'u_chair', [uOrigin[0] + 16, 0, uOrigin[2] - 9.6], textures.length);

const tOrigin = [112, 0, 64];
const tPanels = makeGroup('panel_segments', scenarios.preview_T_junction, tOrigin);
const tPosts = makeGroup('junction_and_end_posts', scenarios.preview_T_junction, tOrigin);
addPanelSegment(tPanels, 't_panel_left', tOrigin, 0, 0.55, 0.9);
addPanelSegment(tPanels, 't_panel_right', [tOrigin[0] + 16, 0, tOrigin[2]], 0, 0.9, 0.55);
addPanelSegment(tPanels, 't_panel_branch', [tOrigin[0] + 16, 0, tOrigin[2]], 90, 0.9, 0.55);
addEndPost(tPosts, 't_end_post_left', tOrigin);
addEndPost(tPosts, 't_end_post_right', [tOrigin[0] + 32, 0, tOrigin[2]]);
addEndPost(tPosts, 't_end_post_branch', [tOrigin[0] + 16, 0, tOrigin[2] + 16], true, 90);
addJunctionPost(tPosts, 't_junction_post', [tOrigin[0] + 16, 0, tOrigin[2]], 'junction');

const crossOrigin = [160, 0, 64];
const crossPanels = makeGroup('panel_segments', scenarios.preview_cross_optional, crossOrigin);
const crossPosts = makeGroup('junction_and_end_posts', scenarios.preview_cross_optional, crossOrigin);
addPanelSegment(crossPanels, 'cross_panel_left', crossOrigin, 0, 0.55, 0.9);
addPanelSegment(crossPanels, 'cross_panel_right', [crossOrigin[0] + 16, 0, crossOrigin[2]], 0, 0.9, 0.55);
addPanelSegment(crossPanels, 'cross_panel_front', [crossOrigin[0] + 16, 0, crossOrigin[2]], 90, 0.9, 0.55);
addPanelSegment(crossPanels, 'cross_panel_back', [crossOrigin[0] + 16, 0, crossOrigin[2]], 270, 0.9, 0.55);
addEndPost(crossPosts, 'cross_end_left', crossOrigin);
addEndPost(crossPosts, 'cross_end_right', [crossOrigin[0] + 32, 0, crossOrigin[2]]);
addEndPost(crossPosts, 'cross_end_front', [crossOrigin[0] + 16, 0, crossOrigin[2] + 16], true, 90);
addEndPost(crossPosts, 'cross_end_back', [crossOrigin[0] + 16, 0, crossOrigin[2] - 16], true, 270);
addJunctionPost(crossPosts, 'cross_junction_post', [crossOrigin[0] + 16, 0, crossOrigin[2]], 'junction');

const preview = {
  meta: {...source.meta, model_format: 'java_block'},
  name: 'office_cubicle_partition_connection_preview',
  geometry_name: '', modular: false, box_uv: false,
  resolution: {width: 128, height: 128},
  elements, outliner, textures, groups,
  ambientocclusion: false,
  front_gui_light: false
};
const output = 'src/main/blockbench/previews/office_cubicle_partition_connection_preview.bbmodel';
await mkdir('src/main/blockbench/previews', {recursive: true});
await writeFile(output, JSON.stringify(preview));
const directory = 'build/asset_checks/office_partition_connections';
await mkdir(directory, {recursive: true});
const packed = Buffer.from(JSON.stringify(preview)).toString('base64url');
const loader = `(() => {const raw='${packed}'.replaceAll('-','+').replaceAll('_','/');const model=JSON.parse(new TextDecoder().decode(Uint8Array.from(atob(raw),c=>c.charCodeAt(0))));newProject(Formats.java_block);Codecs.project.parse(model,'D:/Minecraft Modding/Apocalypse First Light/${output}');Project.save_path='D:/Minecraft Modding/Apocalypse First Light/${output}';Canvas.updateAll();return {name:Project.name,cubes:Cube.all.length,groups:Group.all.length,textures:Texture.all.length};})()`;
await writeFile(`${directory}/load-preview.blockbench.js`, loader);
const summary = {
  output, cubes: elements.length, groups: groups.length, textures: textures.length,
  scenarios: scenarioNames, sourceBytesBefore: (await readFile('src/main/blockbench/office_cubicle_partition.bbmodel')).length
};
console.log(JSON.stringify(summary));
