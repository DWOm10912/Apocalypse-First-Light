import fs from 'node:fs';
import assert from 'node:assert/strict';

const sourcePath='src/main/blockbench/water_dispenser.bbmodel';
const directPath='src/main/resources/assets/apocalypse_firstlight/models/block/water_dispenser.json';
const renderPath='src/main/resources/assets/apocalypse_firstlight/models/block/water_dispenser_render.json';
const itemPath='src/main/resources/assets/apocalypse_firstlight/models/item/water_dispenser.json';
const source=JSON.parse(fs.readFileSync(sourcePath,'utf8'));
const direct=JSON.parse(fs.readFileSync(directPath,'utf8'));
const item=JSON.parse(fs.readFileSync(itemPath,'utf8'));
const groupDefs=new Map(source.groups.map(group=>[group.uuid,group]));
const elementsByUuid=new Map(source.elements.map(element=>[element.uuid,element]));

function resolvedGroup(node){
  assert.equal(typeof node,'object');
  const group={...groupDefs.get(node.uuid),...node};
  assert(group.name,'Unknown group '+node.uuid);
  return group;
}

function findGroup(nodes,name){
  for(const node of nodes){
    if(typeof node==='string')continue;
    const group=resolvedGroup(node);
    if(group.name===name)return group;
    const nested=findGroup(group.children??[],name);
    if(nested)return nested;
  }
  return null;
}

function collectElementIds(nodes,result=[]){
  for(const node of nodes){
    if(typeof node==='string')result.push(node);
    else collectElementIds(resolvedGroup(node).children??[],result);
  }
  return result;
}

const bottleGroup=findGroup(source.outliner,'water_bottle');
assert(bottleGroup,'Missing water_bottle group');
const bottleIds=collectElementIds(bottleGroup.children??[]);
const bottleNames=new Set(bottleIds.map(uuid=>{
  const element=elementsByUuid.get(uuid);
  assert(element,'Missing bottle element '+uuid);
  return element.name;
}));
assert.equal(bottleNames.size,31);
assert.equal(new Set(direct.elements.map(element=>element.name)).size,direct.elements.length);

const glass=direct.elements.filter(element=>bottleNames.has(element.name));
const opaque=direct.elements.filter(element=>!bottleNames.has(element.name));
assert.equal(glass.length,31);
assert.equal(opaque.length+glass.length,direct.elements.length);

const child=(elements,render_type)=>({
  ambientocclusion:false,
  textures:direct.textures,
  render_type,
  elements
});
const render={
  loader:'forge:composite',
  ambientocclusion:false,
  textures:direct.textures,
  children:{
    opaque:child(opaque,'minecraft:solid'),
    glass:child(glass,'minecraft:translucent')
  },
  item_render_order:['opaque','glass']
};

fs.writeFileSync(renderPath,JSON.stringify(render,null,2)+'\n');
const handTransforms=['thirdperson_righthand','thirdperson_lefthand','firstperson_righthand','firstperson_lefthand'];
item.display??={};
for(const key of handTransforms){
  const transform=source.display?.[key];
  assert(transform?.scale?.length===3,'Missing '+key+' scale in Blockbench source');
  item.display[key]=structuredClone(transform);
}
fs.writeFileSync(itemPath,JSON.stringify(item,null,2)+'\n');
console.log(JSON.stringify({
  renderPath,
  itemPath,
  opaque:opaque.length,
  glass:glass.length,
  firstPersonScale:item.display.firstperson_righthand.scale,
  thirdPersonScale:item.display.thirdperson_righthand.scale,
  sourceGeometryUnchanged:true
}));
