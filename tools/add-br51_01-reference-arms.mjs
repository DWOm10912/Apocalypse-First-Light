// Source-only preview helpers. Never changes runtime assets or animation channels.
import fs from 'node:fs';
import crypto from 'node:crypto';
const file = 'src/main/blockbench/br51_01.bbmodel';
const model = JSON.parse(fs.readFileSync(file));
const template = JSON.parse(fs.readFileSync('src/main/blockbench/p9_01_v03_8_fire_slide_cleanup.bbmodel'));
const before = structuredClone(model);
const uuid = name => {
  const h = crypto.createHash('md5').update('afl-br51_01-preview/' + name).digest('hex');
  return `${h.slice(0,8)}-${h.slice(8,12)}-${h.slice(12,16)}-${h.slice(16,20)}-${h.slice(20)}`;
};
function nodeFor(nodes, id) {
  for (const n of nodes) {
    if (typeof n === 'string') continue;
    if (n.uuid === id) return n;
    const found = nodeFor(n.children || [], id);
    if (found) return found;
  }
}
const textureName = 'afl_arm_reference_source_only.png';
let textureIndex = model.textures.findIndex(t => t.name === textureName);
if (textureIndex < 0) {
  const texture = structuredClone(template.textures.find(t => t.name === textureName));
  if (!texture?.source) throw Error('Missing embedded AFL reference texture');
  texture.uuid = uuid(textureName);
  texture.id = String(model.textures.length);
  texture.path = ''; delete texture.relative_path;
  textureIndex = model.textures.push(texture) - 1;
}
for (const side of ['right', 'left']) {
  const anchor = model.groups.find(g => g.name === `${side}_hand_anchor`);
  const anchorNode = nodeFor(model.outliner, anchor?.uuid);
  if (!anchor || !anchorNode) throw Error('Missing anchor ' + side);
  for (const slim of [false, true]) {
    const name = `${side}_arm_reference${slim ? '_slim' : ''}`;
    if (model.groups.some(g => g.name === name)) throw Error('Reference already exists; refuse to overwrite authored pose: ' + name);
    const group = structuredClone(template.groups.find(g => g.name === name));
    const cube = structuredClone(template.elements.find(e => e.name === name + '_cube'));
    if (!group || !cube) throw Error('Missing reference template ' + name);
    const shift = anchor.origin.map((v, i) => v - group.origin[i]);
    for (const key of ['from', 'to', 'origin']) cube[key] = cube[key].map((v, i) => v + shift[i]);
    group.origin = [...anchor.origin]; group.rotation = [0, 0, 0]; group.children = [];
    group.uuid = uuid(name); cube.uuid = uuid(cube.name);
    group.export = cube.export = false;
    group.visibility = cube.visibility = !slim;
    group.selected = group.primary_selected = false;
    for (const face of Object.values(cube.faces)) face.texture = textureIndex;
    model.groups.push(group); model.elements.push(cube);
    anchorNode.children.push({uuid: group.uuid, children: [cube.uuid]});
    console.log(name, 'size=', cube.to.map((v,i) => v-cube.from[i]), 'visible=', !slim, 'export=false');
  }
}
// Preserve all pre-existing data, allowing only children appended to the two anchors.
if (JSON.stringify(model.animations) !== JSON.stringify(before.animations)
    || JSON.stringify(model.groups.slice(0, before.groups.length)) !== JSON.stringify(before.groups)
    || JSON.stringify(model.elements.slice(0, before.elements.length)) !== JSON.stringify(before.elements)
    || JSON.stringify(model.display) !== JSON.stringify(before.display)) throw Error('Existing authoring data changed');
fs.writeFileSync(file, JSON.stringify(model, null, 2));
