import {readFile} from 'node:fs/promises';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';

const source=JSON.parse(await readFile('src/main/blockbench/water_dispenser.bbmodel','utf8'));
const runtime=JSON.parse(await readFile('src/main/resources/assets/apocalypse_firstlight/models/block/water_dispenser.json','utf8'));
const render=JSON.parse(await readFile('src/main/resources/assets/apocalypse_firstlight/models/block/water_dispenser_render.json','utf8'));
const png=await readFile('src/main/resources/assets/apocalypse_firstlight/textures/block/water_dispenser.png');
const blockstate=JSON.parse(await readFile('src/main/resources/assets/apocalypse_firstlight/blockstates/water_dispenser.json','utf8'));
const itemModel=JSON.parse(await readFile('src/main/resources/assets/apocalypse_firstlight/models/item/water_dispenser.json','utf8'));
const loot=JSON.parse(await readFile('src/main/resources/data/apocalypse_firstlight/loot_tables/blocks/water_dispenser.json','utf8'));
const pickaxe=JSON.parse(await readFile('src/main/resources/data/minecraft/tags/blocks/mineable/pickaxe.json','utf8'));
const iron=JSON.parse(await readFile('src/main/resources/data/minecraft/tags/blocks/needs_iron_tool.json','utf8'));
const en=JSON.parse(await readFile('src/main/resources/assets/apocalypse_firstlight/lang/en_us.json','utf8'));
const zh=JSON.parse(await readFile('src/main/resources/assets/apocalypse_firstlight/lang/zh_cn.json','utf8'));
const renderTypesJava=await readFile('src/main/java/com/antaurora/apofirstlight/client/AflBlockRenderTypes.java','utf8');

assert.equal(source.elements.length,160);
assert.equal(runtime.elements.length,160);
assert.equal(source.groups.length,22);
assert.equal(source.textures.length,1);
assert.equal(source.resolution.width,128);
assert.equal(source.resolution.height,128);
assert.equal(runtime.render_type,'minecraft:translucent');
assert.equal(runtime.ambientocclusion,false);
assert.equal(runtime.textures['0'],'apocalypse_firstlight:block/water_dispenser');
assert.equal(render.loader,'forge:composite');
assert.equal(render.ambientocclusion,false);
assert.deepEqual(render.item_render_order,['opaque','glass']);
assert.equal(render.children.opaque.render_type,'minecraft:solid');
assert.equal(render.children.glass.render_type,'minecraft:translucent');
assert.equal(render.children.opaque.elements.length,129);
assert.equal(render.children.glass.elements.length,31);
assert.equal(render.children.opaque.elements.length+render.children.glass.elements.length,160);
assert(!renderTypesJava.includes('setRenderLayer(AflBlocks.WATER_DISPENSER'));
assert.deepEqual(png,Buffer.from(source.textures[0].source.split(',')[1],'base64'));

const byName=new Map(runtime.elements.map(element=>[element.name,element]));
assert.equal(byName.size,160);
let renderedFaces=0;
for(const cube of source.elements){
  assert.equal(cube.type,'cube');
  const exported=byName.get(cube.name);
  assert(exported,cube.name);
  assert.deepEqual(exported.from,cube.from);
  assert.deepEqual(exported.to,cube.to);
  for(let axis=0;axis<3;axis++){
    assert(cube.to[axis]>cube.from[axis],cube.name+' zero-volume');
    assert(cube.from[axis]>=0,cube.name+' below model bounds');
    assert(cube.to[axis]<=(axis===1?32:16),cube.name+' above model bounds');
  }
  for(const [direction,face] of Object.entries(cube.faces)){
    if(face.texture===null){
      assert(!exported.faces[direction]);
      continue;
    }
    assert.equal(exported.faces[direction].texture,'#0');
    assert.deepEqual(exported.faces[direction].uv,face.uv.map(value=>value/8));
    for(const uv of face.uv)assert(uv>=0&&uv<=128);
    renderedFaces++;
  }
  assert(Object.values(cube.faces).some(face=>face.texture!==null),cube.name+' fully hidden');
}

const referenced=[];
function walk(nodes){
  for(const node of nodes){
    if(typeof node==='string')referenced.push(node);
    else walk(node.children??[]);
  }
}
walk(source.outliner);
assert.equal(new Set(referenced).size,160);
assert.deepEqual(new Set(referenced),new Set(source.elements.map(element=>element.uuid)));

const hot=source.elements.find(element=>element.name==='hot_lever_cap');
const cold=source.elements.find(element=>element.name==='cold_lever_cap');
assert(hot.from[0]>cold.from[0]);

const fluidBox={min:[4.2,21.55,4.2],max:[11.8,31.15,11.8]};
const intersects=(cube,box)=>cube.from.every((value,axis)=>value<box.max[axis])&&cube.to.every((value,axis)=>value>box.min[axis]);
const fluidIntersections=source.elements.filter(cube=>intersects(cube,fluidBox));
assert.equal(fluidIntersections.length,0,'Fluid cavity intersects '+fluidIntersections.map(c=>c.name).join(', '));

assert.equal(source.elements.filter(c=>/^wall_[0-4]_(north|south|west|east)$/.test(c.name)).length,20);
assert.equal(source.elements.filter(c=>/^cap_(bottom|top)_sealed$/.test(c.name)).length,2);
assert.equal(source.elements.filter(c=>/^neck_(lower|collar)_(north|south|west|east)$/.test(c.name)).length,8);
assert(!source.elements.some(c=>/^shell_|^thin_|^bottle_\d+_profile_|^(inverted_neck|neck_front_lip|neck_side_lip)$/.test(c.name)));

const bottle=source.elements.filter(c=>/^(wall_[0-4]_(north|south|west|east)|cap_(bottom|top)_sealed|neck_(lower|collar)_(north|south|west|east)|faded_water_label)$/.test(c.name));
assert.equal(bottle.length,31);
const epsilon=1e-7;
let positiveVolumeOverlaps=0;
let activeCoplanarOverlaps=0;
const negativeFace=['west','down','north'];
const positiveFace=['east','up','south'];
const active=(cube,direction)=>cube.faces[direction]?.texture!==null&&cube.faces[direction]?.texture!==undefined;
for(let i=0;i<bottle.length;i++)for(let j=i+1;j<bottle.length;j++){
  const a=bottle[i],b=bottle[j];
  const overlap=a.from.map((value,axis)=>Math.min(a.to[axis],b.to[axis])-Math.max(value,b.from[axis]));
  if(overlap.every(value=>value>epsilon)){
    positiveVolumeOverlaps++;
    continue;
  }
  const touchingAxes=overlap.map((value,axis)=>Math.abs(value)<=epsilon?axis:-1).filter(axis=>axis>=0);
  if(touchingAxes.length!==1||overlap.some(value=>value<-epsilon))continue;
  const axis=touchingAxes[0];
  const aBeforeB=Math.abs(a.to[axis]-b.from[axis])<=epsilon;
  const bBeforeA=Math.abs(b.to[axis]-a.from[axis])<=epsilon;
  if((aBeforeB&&active(a,positiveFace[axis])&&active(b,negativeFace[axis]))
      ||(bBeforeA&&active(b,positiveFace[axis])&&active(a,negativeFace[axis])))activeCoplanarOverlaps++;
}
assert.equal(positiveVolumeOverlaps,0);
assert.equal(activeCoplanarOverlaps,0);

assert.equal(Object.keys(blockstate.variants).length,8);
for(const [key,value] of Object.entries(blockstate.variants)){
  assert.match(key,/^facing=(north|east|south|west),half=(lower|upper)$/);
  assert.equal(value.model,key.endsWith('half=lower')?'apocalypse_firstlight:block/water_dispenser_render':'apocalypse_firstlight:block/water_dispenser_empty');
}
assert.equal(itemModel.parent,'apocalypse_firstlight:block/water_dispenser_render');
for(const key of ['firstperson_righthand','firstperson_lefthand']){
  assert.deepEqual(itemModel.display[key].scale,[0.3,0.3,0.3]);
  assert.deepEqual(itemModel.display[key],source.display[key]);
}
for(const key of ['thirdperson_righthand','thirdperson_lefthand']){
  assert.deepEqual(itemModel.display[key].scale,[0.25,0.25,0.25]);
  assert.deepEqual(itemModel.display[key],source.display[key]);
}
assert.deepEqual(itemModel.display.gui.scale,[0.4,0.4,0.4]);
assert.equal(loot.pools[0].conditions[0].properties.half,'lower');
assert(pickaxe.values.includes('apocalypse_firstlight:water_dispenser'));
assert(iron.values.includes('apocalypse_firstlight:water_dispenser'));
assert.equal(en['block.apocalypse_firstlight.water_dispenser'],'Water Dispenser');
assert.equal(zh['block.apocalypse_firstlight.water_dispenser'],'饮水机');

console.log(JSON.stringify({
  pass:true,
  cubes:160,
  groups:22,
  renderedFaces,
  sourceRuntimeGeometryUVMatch:true,
  embeddedTextureMatchesPNG:true,
  bottleCubes:31,
  positiveVolumeOverlaps,
  activeCoplanarOverlaps,
  fluidBox,
  fluidBoxIntersections:fluidIntersections.length,
  registrationResources:true,
  textureSHA256:createHash('sha256').update(png).digest('hex'),
  gameRuntimeTested:false
},null,2));
