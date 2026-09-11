import {mkdir, readFile, writeFile} from 'node:fs/promises';

const namespace = 'apocalypse_firstlight';
const sourcePath = 'src/main/blockbench/office_cubicle_partition.bbmodel';
const texturePath = 'src/main/resources/assets/apocalypse_firstlight/textures/block/office_cubicle_partition.png';
const modelDirectory = 'src/main/resources/assets/apocalypse_firstlight/models/block/office_cubicle_partition';
const blockstatePath = 'src/main/resources/assets/apocalypse_firstlight/blockstates/office_cubicle_partition.json';
const itemPath = 'src/main/resources/assets/apocalypse_firstlight/models/item/office_cubicle_partition.json';

const source = JSON.parse(await readFile(sourcePath, 'utf8'));
await readFile(texturePath);
if (source.elements.length !== 42) throw new Error('Expected the approved 42-cube office partition source');
if (Math.max(...source.elements.map(element => element.to[1])) !== 32) {
  throw new Error('Expected the approved two-block-tall office partition source');
}

const texture = `${namespace}:block/office_cubicle_partition`;
const directions = ['north', 'east', 'south', 'west', 'up', 'down'];
const materialUv = index => {
  const x = (index % 4) * 4;
  const y = Math.floor(index / 4) * 4;
  return [x + 0.125, y + 0.125, x + 3.875, y + 3.875];
};

const faces = material => Object.fromEntries(directions.map(direction => [direction, {
  uv: materialUv(material), texture: '#0'
}]));

const cube = (name, from, to, material) => ({name, from, to, shade: true, faces: faces(material)});

const model = elements => ({
  credit: 'Apocalypse: First Light — office_cubicle_partition',
  parent: 'minecraft:block/block',
  ambientocclusion: false,
  texture_size: [128, 128],
  textures: {'0': texture, particle: texture},
  elements
});

const convertSourceElement = element => ({
  name: element.name,
  from: element.from,
  to: element.to,
  shade: element.shade ?? true,
  faces: Object.fromEntries(Object.entries(element.faces).map(([direction, face]) => [direction, {
    uv: face.uv.map(value => value / 8),
    texture: '#0'
  }]))
});

const addPanelSegment = (elements, name, start, end) => {
  elements.push(
    cube(`${name}_core`, [start, 3.2, 7.55], [end, 31, 8.45], 8),
    cube(`${name}_fabric_front`, [start, 3.2, 7.4], [end, 31, 7.55], 4),
    cube(`${name}_fabric_back`, [start, 3.2, 8.45], [end, 31, 8.6], 4),
    cube(`${name}_top_rail_core`, [start, 31, 7.25], [end, 31.8, 8.75], 0),
    cube(`${name}_top_rail_cap`, [start, 31.8, 7.1], [end, 32, 8.9], 1),
    cube(`${name}_top_front_highlight`, [start, 31, 7.1], [end, 31.8, 7.25], 3),
    cube(`${name}_top_back_shadow`, [start, 31, 8.75], [end, 31.8, 8.9], 14),
    cube(`${name}_bottom_rail_core`, [start, 2.45, 7.25], [end, 3.2, 8.75], 0),
    cube(`${name}_bottom_lower_edge`, [start, 2.3, 7.1], [end, 2.45, 8.9], 2),
    cube(`${name}_bottom_front_highlight`, [start, 2.45, 7.1], [end, 3.2, 7.25], 3),
    cube(`${name}_bottom_back_shadow`, [start, 2.45, 8.75], [end, 3.2, 8.9], 14)
  );
};

const addEndPost = (elements, name) => {
  elements.push(
    cube(`${name}_core`, [-0.4, 0.55, 7.2], [0.4, 31.8, 8.8], 0),
    cube(`${name}_outer_spine`, [-0.55, 0.55, 7.05], [-0.4, 31.8, 8.95], 2),
    cube(`${name}_inner_reveal`, [0.4, 2.7, 7.3], [0.55, 31.35, 8.7], 12),
    cube(`${name}_front_highlight`, [-0.4, 0.55, 7.05], [0.4, 31.8, 7.2], 3),
    cube(`${name}_back_shadow`, [-0.4, 0.55, 8.8], [0.4, 31.8, 8.95], 14),
    cube(`${name}_top_cap`, [-0.55, 31.8, 7.05], [0.55, 32, 8.95], 1),
    cube(`${name}_foot_pad`, [-0.8, 0, 6.85], [0.8, 0.15, 9.15], 9),
    cube(`${name}_foot_base`, [-0.9, 0.15, 6.7], [0.9, 0.35, 9.3], 2),
    cube(`${name}_foot_collar`, [-0.6, 0.35, 7.15], [0.6, 0.55, 8.85], 0),
    cube(`${name}_foot_front_edge`, [-0.8, 0.35, 6.85], [0.8, 0.47, 7.15], 1),
    cube(`${name}_foot_back_edge`, [-0.8, 0.35, 8.85], [0.8, 0.47, 9.15], 14)
  );
};

const addJunctionPost = elements => {
  elements.push(
    cube('junction_post_body', [7.3, 0.55, 7.3], [8.7, 31.8, 8.7], 0),
    cube('junction_post_front_highlight', [7.3, 0.55, 7.1], [8.7, 31.8, 7.3], 3),
    cube('junction_post_back_shadow', [7.3, 0.55, 8.7], [8.7, 31.8, 8.9], 14),
    cube('junction_post_left_edge', [7.1, 0.55, 7.3], [7.3, 31.8, 8.7], 2),
    cube('junction_post_right_edge', [8.7, 0.55, 7.3], [8.9, 31.8, 8.7], 12),
    cube('junction_post_corner_nw', [7.1, 0.55, 7.1], [7.3, 31.8, 7.3], 2),
    cube('junction_post_corner_ne', [8.7, 0.55, 7.1], [8.9, 31.8, 7.3], 3),
    cube('junction_post_corner_sw', [7.1, 0.55, 8.7], [7.3, 31.8, 8.9], 14),
    cube('junction_post_corner_se', [8.7, 0.55, 8.7], [8.9, 31.8, 8.9], 12),
    cube('junction_post_top_cap', [7.1, 31.8, 7.1], [8.9, 32, 8.9], 1),
    cube('junction_post_foot', [6.95, 0, 6.95], [9.05, 0.3, 9.05], 9),
    cube('junction_post_foot_collar', [7.1, 0.3, 7.1], [8.9, 0.55, 8.9], 0)
  );
};

const single = model(source.elements.map(convertSourceElement));
const endEastElements = [];
addPanelSegment(endEastElements, 'end_east_panel', 0.55, 16);
addEndPost(endEastElements, 'end_east_outer_post');
const straightElements = [];
addPanelSegment(straightElements, 'straight_panel', 0, 16);
const armEastElements = [];
addPanelSegment(armEastElements, 'junction_arm_east', 8.9, 16);
const junctionElements = [];
addJunctionPost(junctionElements);

const exact = (north, south, east, west) => ({
  north: String(north), south: String(south), east: String(east), west: String(west)
});
const apply = (name, y) => ({
  model: `${namespace}:block/office_cubicle_partition/${name}`,
  ...(y === undefined ? {} : {y})
});

const perpendicular = {
  OR: [
    {north: 'true', east: 'true'},
    {east: 'true', south: 'true'},
    {south: 'true', west: 'true'},
    {west: 'true', north: 'true'}
  ]
};
const blockstate = {
  multipart: [
    {when: exact(false, false, false, false), apply: apply('single')},
    {when: exact(false, false, true, false), apply: apply('end_east')},
    {when: exact(false, true, false, false), apply: apply('end_east', 90)},
    {when: exact(false, false, false, true), apply: apply('end_east', 180)},
    {when: exact(true, false, false, false), apply: apply('end_east', 270)},
    {when: exact(false, false, true, true), apply: apply('straight_ew')},
    {when: exact(true, true, false, false), apply: apply('straight_ew', 90)},
    {when: perpendicular, apply: apply('junction')},
    {when: {OR: [{east: 'true', north: 'true'}, {east: 'true', south: 'true'}]}, apply: apply('arm_east')},
    {when: {OR: [{south: 'true', east: 'true'}, {south: 'true', west: 'true'}]}, apply: apply('arm_east', 90)},
    {when: {OR: [{west: 'true', north: 'true'}, {west: 'true', south: 'true'}]}, apply: apply('arm_east', 180)},
    {when: {OR: [{north: 'true', east: 'true'}, {north: 'true', west: 'true'}]}, apply: apply('arm_east', 270)}
  ]
};

const item = {
  parent: `${namespace}:block/office_cubicle_partition/single`,
  gui_light: 'side',
  display: {
    thirdperson_righthand: {rotation: [75, 45, 0], translation: [0, 1.5, 0], scale: [0.25, 0.25, 0.25]},
    thirdperson_lefthand: {rotation: [75, 45, 0], translation: [0, 1.5, 0], scale: [0.25, 0.25, 0.25]},
    firstperson_righthand: {rotation: [0, 45, 0], translation: [0, 1, 0], scale: [0.3, 0.3, 0.3]},
    firstperson_lefthand: {rotation: [0, 225, 0], translation: [0, 1, 0], scale: [0.3, 0.3, 0.3]},
    gui: {rotation: [25, 135, 0], translation: [0, -3, 0], scale: [0.38, 0.38, 0.38]},
    ground: {translation: [0, 1.5, 0], scale: [0.25, 0.25, 0.25]},
    fixed: {rotation: [0, 180, 0], translation: [0, -3, 0], scale: [0.35, 0.35, 0.35]}
  }
};

await mkdir(modelDirectory, {recursive: true});
await mkdir(blockstatePath.slice(0, blockstatePath.lastIndexOf('/')), {recursive: true});
await mkdir(itemPath.slice(0, itemPath.lastIndexOf('/')), {recursive: true});
const outputs = [
  [`${modelDirectory}/single.json`, single],
  [`${modelDirectory}/end_east.json`, model(endEastElements)],
  [`${modelDirectory}/straight_ew.json`, model(straightElements)],
  [`${modelDirectory}/arm_east.json`, model(armEastElements)],
  [`${modelDirectory}/junction.json`, model(junctionElements)],
  [blockstatePath, blockstate],
  [itemPath, item]
];
for (const [path, contents] of outputs) {
  await writeFile(path, `${JSON.stringify(contents, null, 2)}\n`);
}

console.log(JSON.stringify({
  source: sourcePath,
  sourceCubes: source.elements.length,
  sourceHeight: 32,
  runtimeModels: 5,
  multipartRules: blockstate.multipart.length,
  outputs: outputs.map(([path]) => path)
}));
