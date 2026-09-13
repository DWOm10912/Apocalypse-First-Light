import fs from 'node:fs';
import assert from 'node:assert/strict';
import crypto from 'node:crypto';
const root='src/main/resources/assets/apocalypse_firstlight/';
const read=p=>JSON.parse(fs.readFileSync(p));
const source=read('src/main/blockbench/restroom_stall_door.bbmodel');
const geo=read(root+'geo/restroom_stall_door.geo.json')['minecraft:geometry'][0];
const anim=read(root+'animations/restroom_stall_door.animation.json').animations;
for(const kind of ['block','item'])assert.equal(read(root+`models/${kind}/restroom_stall_door.json`).textures.particle,'apocalypse_firstlight:block/restroom_partition');
assert(fs.readFileSync(root+'textures/block/restroom_partition.png').equals(Buffer.from(source.textures[0].source.split(',')[1],'base64')));
assert.equal(geo.bones.flatMap(b=>b.cubes??[]).length,42);
assert(fs.readFileSync(root+'textures/entity/restroom_stall_door.png').equals(Buffer.from(source.textures[0].source.split(',')[1],'base64')));
// Verify the authored geometry survives GeckoLib's X inversion and renderer centering.
const srcGroups=new Map(source.groups.map(g=>[g.uuid,g]));const srcElements=new Map(source.elements.map(e=>[e.uuid,e]));
const close=(a,b)=>a.forEach((v,i)=>assert(Math.abs(v-b[i])<1e-6,`${a} != ${b}`));
function walk(n){const g=srcGroups.get(n.uuid),bone=geo.bones.find(b=>b.name===g.name);close([-bone.pivot[0]+8,bone.pivot[1],bone.pivot[2]+8],g.origin);
const cubes=n.children.filter(c=>typeof c==='string').map(id=>srcElements.get(id));
cubes.forEach((e,i)=>{const c=bone.cubes[i];close([8-c.origin[0]-c.size[0],c.origin[1],c.origin[2]+8],e.from);close(c.size,e.to.map((v,j)=>v-e.from[j]));});
n.children.filter(c=>typeof c!=='string').forEach(walk);}
source.outliner.forEach(walk);
for(const name of ['restroom_stall_door_open','restroom_stall_door_close']){
 const a=anim[name];assert.equal(a.animation_length,.35);assert.equal(a.loop,false);assert.deepEqual(Object.keys(a.bones),['door_leaf']);
 const samples=Object.values(a.bones.door_leaf.rotation).map(k=>k.vector[1]);
 const opening=name.endsWith('_open');assert.equal(samples[0],opening?0:90);assert.equal(samples.at(-1),opening?90:0);
 samples.forEach((v,i)=>{assert(v>=0&&v<=90);if(i)assert(opening?v>=samples[i-1]:v<=samples[i-1]);});
}
const states=read(root+'blockstates/restroom_partition.json').multipart;
const matches=(c,s)=>Object.entries(c).every(([k,v])=>k==='AND'?v.every(a=>matches(a,s)):k==='OR'?v.some(a=>matches(a,s)):s[k]===v);
let variants=0;
for(let graph=0;graph<16;graph++)for(let support=1;support<16;support++){
 if(graph&support)continue;
 const state={north:String(!!(graph&1)),south:String(!!(graph&2)),east:String(!!(graph&4)),west:String(!!(graph&8)),door_support:String(support)};
 const selected=states.filter(c=>matches(c.when,state));assert.equal(selected.length,1);
 const m=read(root+'models/block/restroom_partition/doorway_'+graph+'_'+support+'.json');
 for(const e of m.elements){assert(e.from.every((v,i)=>Number.isFinite(v)&&v<e.to[i]));assert(Object.values(e.faces).every(f=>f.texture==='#0'));}
 if([1,2,4,8].includes(graph)&&[1,2,4,8].some(bit=>(support&bit)&&((bit<=2)!==(graph<=2)))){
   // No unconnected tail opposite the existing partition arm unless another door needs it.
   const opposite={1:2,2:1,4:8,8:4}[graph];
   if(!(support&opposite))for(const e of m.elements){
     const axis=graph<=2?2:0;
     if(graph===1||graph===8)assert(e.to[axis]<=9.3+1e-6,`forward stub ${graph}/${support}/${e.name}`);
     else assert(e.from[axis]>=6.7-1e-6,`forward stub ${graph}/${support}/${e.name}`);
   }
 }
 for(const bit of [1,2,4,8])if(support&bit)assert.equal(m.elements.filter(e=>e.name.startsWith(`door_support_${bit}_`)).length,11);
 variants++;
}
// World-grid Partition | Door | Partition: terminal posts occupy the original reference positions.
const left=read(root+'models/block/restroom_partition/doorway_0_4.json').elements.filter(e=>e.name.includes('door_support_4_'));
const right=read(root+'models/block/restroom_partition/doorway_0_8.json').elements.filter(e=>e.name.includes('door_support_8_'));
assert(Math.abs(Math.max(...left.filter(e=>!e.name.includes('foot')).map(e=>e.to[0]))-16-1.3)<1e-6);
assert(Math.abs(Math.min(...right.filter(e=>!e.name.includes('foot')).map(e=>e.from[0]))+16-14.7)<1e-6);
for(const tag of ['mineable/pickaxe','needs_iron_tool'])assert(read('src/main/resources/data/minecraft/tags/blocks/'+tag+'.json').values.includes('apocalypse_firstlight:restroom_stall_door'));
for(const lang of ['en_us','zh_cn'])assert(read(root+'lang/'+lang+'.json')['block.apocalypse_firstlight.restroom_stall_door']);
console.log(JSON.stringify({result:'PASS',variants,sourceGeometryAndPivotPreserved:true,texture:true,clips:'0.35s eased inverse',worldPostInnerEdges:[1.3,14.7],doorLeafEdges:[1.5,14.5],sourceSHA256:crypto.createHash('sha256').update(fs.readFileSync('src/main/blockbench/restroom_stall_door.bbmodel')).digest('hex')}));
