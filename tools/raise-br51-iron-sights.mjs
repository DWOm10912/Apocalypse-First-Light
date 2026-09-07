// Source-first, narrowly scoped sight riser; no animation, texture or Display writes.
import fs from 'node:fs';
import assert from 'node:assert/strict';
import crypto from 'node:crypto';
const sp='src/main/blockbench/br51_01.bbmodel';
const gp='src/main/resources/assets/apocalypse_firstlight/geo/br51_01.geo.json';
const source=JSON.parse(fs.readFileSync(sp)),geo=JSON.parse(fs.readFileSync(gp));
const bones=geo['minecraft:geometry'][0].bones;
const lift=.875;
if(source.groups.some(g=>g.name==='afl_iron_risers'))throw Error('Already applied; do not raise twice');
const group=name=>source.groups.find(g=>g.name===name);
function node(id,ns=source.outliner){for(const n of ns){if(typeof n==='string')continue;if(n.uuid===id)return n;const r=node(id,n.children);if(r)return r;}}
const rear=node(group('octagon9').uuid);
const housing=node(group('bone5').uuid);
const selected=new Set([...rear.children,...housing.children.filter(id=>{
 const c=source.elements.find(e=>e.uuid===id);
 return c&&c.from[1]>=11.94&&c.from[2]>=8.22;
})]);
assert.equal(rear.children.length,8);
const moved=source.elements.filter(e=>selected.has(e.uuid));
for(const c of moved){c.from[1]+=lift;c.to[1]+=lift;if(c.origin)c.origin[1]+=lift;}
group('octagon9').origin[1]+=lift;
// Update exactly corresponding exported cubes, preserving every UV/inflate/property.
for(const name of ['octagon9','bone5']){
 const b=bones.find(b=>b.name===name);
 for(const c of b.cubes||[])if(name==='octagon9'||(c.origin[1]>=11.94&&c.origin[2]>=8.22)){
  c.origin[1]+=lift;if(c.pivot)c.pivot[1]+=lift;
 }
 if(name==='octagon9')b.pivot[1]+=lift;
}
const uuid=()=>crypto.randomUUID();
const template=source.elements.find(e=>e.uuid===housing.children.find(c=>typeof c==='string'));
const additions=[];
function box(name,from,to){const c={...structuredClone(template),name,uuid:uuid(),from,to,origin:[0,0,0],rotation:[0,0,0],export:true,visibility:true,inflate:0};additions.push(c);}
// Low mounting shoe bridges the original rear housing to the elevated aperture support.
box('rear_riser_foot',[-.72,12.30,8.15],[.72,12.52,9.42]);
box('rear_riser_body',[-.53,12.50,8.23],[.53,13.06,9.30]);
// Dedicated narrow front post on the forward handguard; top equals rear aperture center.
box('front_riser_foot',[-.50,13.09,-10.15],[.50,13.25,-9.45]);
box('front_riser_body',[-.28,13.25,-10.02],[.28,13.43,-9.58]);
box('front_sight_post',[-.065,13.43,-9.88],[.065,13.6875,-9.72]);
const ng={...structuredClone(group('octagon9')),name:'afl_iron_risers',uuid:uuid(),origin:[0,13.6875,-9.8],children:[],rotation:[0,0,0]};
source.groups.push(ng);source.elements.push(...additions);
node(group('br51_01_default').uuid).children.push({uuid:ng.uuid,isOpen:true,children:additions.map(c=>c.uuid)});
const exported=additions.map(c=>({origin:[-c.to[0],c.from[1],c.from[2]],size:c.to.map((v,i)=>v-c.from[i]),uv:Object.fromEntries(Object.entries(c.faces).filter(([,f])=>f.texture!==null).map(([d,f])=>[d,{uv:f.uv.slice(0,2),uv_size:[f.uv[2]-f.uv[0],f.uv[3]-f.uv[1]]}]))}));
bones.push({name:ng.name,parent:'br51_01_default',pivot:[0,13.6875,-9.8],cubes:exported});
assert.equal(bones.find(b=>b.name==='octagon9').pivot[1],13.6875);
assert.equal(additions.at(-1).to[1],13.6875);
fs.writeFileSync(sp,JSON.stringify(source,null,2)+'\n');
fs.writeFileSync(gp,JSON.stringify(geo,null,2)+'\n');
console.log('Raised rear assembly',moved.length,'cubes; added five riser/post cubes; sight axis Y=13.6875, clearance over 13.125 top = .5625');
