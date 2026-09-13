import fs from 'node:fs';
import assert from 'node:assert/strict';
import crypto from 'node:crypto';
const root='src/main/resources/assets/apocalypse_firstlight/';
const dir=root+'models/block/restroom_partition/';
const read=p=>JSON.parse(fs.readFileSync(p));
const write=(p,v)=>{fs.mkdirSync(p.slice(0,p.lastIndexOf('/')),{recursive:true});fs.writeFileSync(p,JSON.stringify(v,null,2)+'\n');};
const source=read('src/main/blockbench/restroom_stall_door.bbmodel');
const groups=new Map(source.groups.map(g=>[g.uuid,g]));
const elements=new Map(source.elements.map(g=>[g.uuid,g]));
const bones=[];
function walk(n,parent){const g=groups.get(n.uuid);const b={name:g.name,pivot:[8-g.origin[0],g.origin[1],g.origin[2]-8]};if(parent)b.parent=parent;
 b.cubes=n.children.filter(c=>typeof c==='string').map(id=>{const e=elements.get(id);assert((e.rotation??[0,0,0]).every(v=>v===0));return {origin:[8-e.to[0],e.from[1],e.from[2]-8],size:e.to.map((v,i)=>v-e.from[i]),uv:Object.fromEntries(Object.entries(e.faces).map(([f,v])=>[f,{uv:v.uv.slice(0,2),uv_size:[v.uv[2]-v.uv[0],v.uv[3]-v.uv[1]]}]))};});
 bones.push(b);n.children.filter(c=>typeof c!=='string').forEach(c=>walk(c,g.name));}
source.outliner.forEach(n=>walk(n));
write(root+'geo/restroom_stall_door.geo.json',{format_version:'1.12.0','minecraft:geometry':[{description:{identifier:'geometry.restroom_stall_door',texture_width:128,texture_height:128,visible_bounds_width:3,visible_bounds_height:3,visible_bounds_offset:[0,1,0]},bones}]});
const animations={};
for(const a of source.animations){assert.equal(a.length,.35);const track=Object.values(a.animators).find(b=>b.keyframes?.length);assert.equal(track.name,'door_leaf');const keys=track.keyframes;const start=+keys[0].data_points[0].y,end=+keys[1].data_points[0].y;const rotation={};
 for(let i=0;i<=28;i++){const t=i/28;rotation[(i*.0125).toFixed(4)]={vector:[0,-(start+(end-start)*t*t*(3-2*t)),0]};}
 animations[a.name]={loop:false,animation_length:.35,bones:{door_leaf:{rotation}}};}
for(const [n,y]of [['closed',0],['open',90]])animations['door_'+n+'_pose']={loop:true,animation_length:.05,bones:{door_leaf:{rotation:[0,y,0]}}};
write(root+'animations/restroom_stall_door.animation.json',{format_version:'1.8.0',animations});
fs.mkdirSync(root+'textures/entity',{recursive:true});fs.writeFileSync(root+'textures/entity/restroom_stall_door.png',Buffer.from(source.textures[0].source.split(',')[1],'base64'));
write(root+'blockstates/restroom_stall_door.json',{variants:{'':{model:'apocalypse_firstlight:block/restroom_stall_door'}}});
// Entity textures are not supplied by the default block atlas directory source.
// Reuse the identical, already-stitched partition atlas for mining particles.
write(root+'models/block/restroom_stall_door.json',{parent:'minecraft:block/block',textures:{particle:'apocalypse_firstlight:block/restroom_partition'}});
const item=read(root+'models/item/commercial_glass_double_door.json');item.textures.particle='apocalypse_firstlight:block/restroom_partition';write(root+'models/item/restroom_stall_door.json',item);
write('src/main/resources/data/apocalypse_firstlight/loot_tables/blocks/restroom_stall_door.json',{type:'minecraft:block',pools:[{rolls:1,conditions:[{condition:'minecraft:block_state_property',block:'apocalypse_firstlight:restroom_stall_door',properties:{half:'lower'}},{condition:'minecraft:survives_explosion'}],entries:[{type:'minecraft:item',name:'apocalypse_firstlight:restroom_stall_door'}]}]});
// Terminal geometry is generated separately; ordinary multipart conditions/graph remain intact.
const clone=v=>structuredClone(v);
function rotate(e,turns){e=clone(e);for(let i=0;i<(turns+4)%4;i++){const f=e.from,t=e.to;e.from=[16-t[2],f[1],f[0]];e.to=[16-f[2],t[1],t[0]];}return e;}
const base=read(dir+'single.json');
const model=n=>read(dir+n+'.json').elements;
const dirs=[[1,3,'north'],[2,1,'south'],[4,0,'east'],[8,2,'west']];
function normal(mask){const count=mask.toString(2).replaceAll('0','').length;
 if(!count)return clone(base.elements);
 if(count===1)return model('end_east').map(e=>rotate(e,dirs.find(d=>d[0]===mask)[1]));
 if(mask===12||mask===3)return model('straight_ew').map(e=>rotate(e,mask===12?0:1));
 return [...model('junction'),...dirs.filter(d=>mask&d[0]).flatMap(d=>model('arm_east').map(e=>rotate(e,d[1])))];}
const partState=read(root+'blockstates/restroom_partition.json');
// Idempotent regeneration removes only this generator's previous clauses.
partState.multipart=partState.multipart.filter(r=>!r.apply.model.includes('/doorway_')).map(r=>{if(r.when?.AND)r.when=r.when.AND[0];return {...r,when:{AND:[r.when,{door_support:'0'}]}};});
const methods=[];const cases=[];let count=0;
for(let graph=0;graph<16;graph++)for(let support=1;support<16;support++){
 if(graph&support)continue;
 let cubes=normal(graph);
 // A one-neighbor end module contains a full unconnected half and terminal post.
 // When a doorway turns that endpoint, use only the real connected arm and a
 // junction: retaining the opposite half produces the two forward-facing stubs.
 if([1,2,4,8].includes(graph) && dirs.some(([bit])=>(support&bit)&&((bit<=2)!==(graph<=2)))){
   const turn=dirs.find(([bit])=>bit===graph)[1];
   cubes=[...model('junction'),...model('arm_east').map(e=>rotate(e,turn))];
 }
 if(graph===0&&(support&3)&&!(support&12))cubes=cubes.map(e=>rotate(e,1));
 for(const [bit,turn]of dirs){if(!(support&bit))continue;
   cubes=cubes.map(e=>rotate(e,-turn)).filter(e=>!(/post|foot/.test(e.name)&&(e.from[0]+e.to[0])/2>14));
   const panel=cubes.filter(e=>!/post|foot/.test(e.name)&&e.to[0]>14);
   if(panel.length)panel.forEach(e=>e.to[0]=16.2);
   else cubes.push(...model('arm_east').map(e=>({...e,to:[16.2,e.to[1],e.to[2]]})));
   cubes.push(...base.elements.filter(e=>/^right_(post|foot)/.test(e.name)).map(e=>({...clone(e),name:'door_support_'+bit+'_'+e.name,from:[e.from[0]+1.5,e.from[1],e.from[2]],to:[e.to[0]+1.5,e.to[1],e.to[2]]})));
   cubes=cubes.map(e=>rotate(e,turn));
 }
 const name=`doorway_${graph}_${support}`;write(dir+name+'.json',{...base,elements:cubes});
 if(graph===0&&support===4){
   const editable=read('src/main/blockbench/restroom_partition.bbmodel');
   const uuid=name=>{const h=crypto.createHash('md5').update('afl-doorway:'+name).digest('hex');return `${h.slice(0,8)}-${h.slice(8,12)}-${h.slice(12,16)}-${h.slice(16,20)}-${h.slice(20)}`;};
   const groupId=uuid('root');editable.name='restroom_partition_doorway_support';editable.animations=[];
   editable.groups=[{name:'restroom_partition_doorway_support_root',uuid:groupId,origin:[8,0,8],rotation:[0,0,0],export:true,isOpen:true}];
   editable.elements=cubes.map(e=>({...clone(e),type:'cube',uuid:uuid(e.name),origin:[8,0,8],rotation:[0,0,0],box_uv:false,export:true,faces:Object.fromEntries(Object.entries(e.faces).map(([f,v])=>[f,{uv:v.uv.map(u=>u*8),texture:0}]))}));
   editable.outliner=[{uuid:groupId,children:editable.elements.map(e=>e.uuid)}];
   write('src/main/blockbench/restroom_partition_doorway_support.bbmodel',editable);
 }
 partState.multipart.push({when:{north:String(!!(graph&1)),south:String(!!(graph&2)),east:String(!!(graph&4)),west:String(!!(graph&8)),door_support:String(support)},apply:{model:'apocalypse_firstlight:block/restroom_partition/'+name}});
 const key=graph*16+support;
 cases.push(`case ${key} -> shape${key}();`);
 methods.push(`private static VoxelShape shape${key}(){return Shapes.or(Shapes.empty(),\n${cubes.map(e=>`Block.box(${[...e.from,...e.to].map(v=>Number(v.toFixed(5))).join(',')})`).join(',\n')}).optimize();}`);
 count++;
}
write(root+'blockstates/restroom_partition.json',partState);
fs.writeFileSync('src/main/java/com/antaurora/apofirstlight/block/RestroomDoorwayShapes.java',`package com.antaurora.apofirstlight.block;\nimport net.minecraft.world.level.block.Block;\nimport net.minecraft.world.phys.shapes.*;\n/** Generated by tools/build-restroom-doorway-runtime.mjs from the same boxes as the doorway models. */\nfinal class RestroomDoorwayShapes {\nprivate static final VoxelShape[] CACHE=new VoxelShape[256];\nstatic VoxelShape get(int graph,int support){int key=graph*16+support; if(CACHE[key]==null)CACHE[key]=switch(key){${cases.join('\n')}default->Shapes.empty();};return CACHE[key];}\n${methods.join('\n')}\n}\n`);
console.log(`Door geo/animations/material exported; ${count} isolated doorway variants, matching generated shapes.`);
