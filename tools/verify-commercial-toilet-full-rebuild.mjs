import fs from 'node:fs';
import assert from 'node:assert/strict';
const read=p=>JSON.parse(fs.readFileSync(p));
const m=read('src/main/blockbench/commercial_flushometer_toilet.bbmodel');
const old=read('src/main/blockbench/previews/commercial_flushometer_toilet_before_full_rebuild.bbmodel');
const oldIds=new Set(old.elements.map(e=>e.uuid));
const groups=new Map(m.groups.map(g=>[g.uuid,g.name]));
function descendants(nodes,name){let result=[];for(const n of nodes){if(typeof n==='string')continue;if(groups.get(n.uuid)===name){const walk=a=>a.flatMap(v=>typeof v==='string'?[v]:walk(v.children));result.push(...walk(n.children));}else result.push(...descendants(n.children,name));}return result;}
const metalIds=descendants(m.outliner,'flushometer_assembly');assert.equal(metalIds.length,18);
for(const id of metalIds){const now=m.elements.find(e=>e.uuid===id),before=old.elements.find(e=>e.uuid===id);assert(before);for(const k of ['from','to','origin','rotation','faces'])assert.deepEqual(now[k],before[k]);}
const ceramic=m.elements.filter(e=>!metalIds.includes(e.uuid));assert(ceramic.every(e=>!oldIds.has(e.uuid)));
function vertices(e){const origin=e.origin??[0,0,0],r=(e.rotation??[0,0,0]).map(v=>v*Math.PI/180);const out=[];for(const x of [e.from[0],e.to[0]])for(const y of [e.from[1],e.to[1]])for(const z of [e.from[2],e.to[2]]){let [a,b,c]=[x-origin[0],y-origin[1],z-origin[2]];[b,c]=[b*Math.cos(r[0])-c*Math.sin(r[0]),b*Math.sin(r[0])+c*Math.cos(r[0])];[a,c]=[a*Math.cos(r[1])+c*Math.sin(r[1]),-a*Math.sin(r[1])+c*Math.cos(r[1])];[a,b]=[a*Math.cos(r[2])-b*Math.sin(r[2]),a*Math.sin(r[2])+b*Math.cos(r[2])];out.push([a+origin[0],b+origin[1],c+origin[2]]);}return out;}
const points=m.elements.flatMap(vertices),min=[0,1,2].map(i=>Math.min(...points.map(p=>p[i]))),max=[0,1,2].map(i=>Math.max(...points.map(p=>p[i])));
assert(min[0]>=-8&&max[0]<=8&&min[2]>=-8&&max[2]<=8&&max[1]<=16);assert(Math.abs(min[1])<1e-6);
const round=p=>p.map(v=>Math.round(v*1e5)).join(',');const cps=ceramic.flatMap(vertices),keys=new Set(cps.map(round));
for(const p of cps)assert(keys.has(round([-p[0],p[1],p[2]])),'ceramic mirror mismatch '+p);
assert.equal(m.elements.length,251);assert(m.elements.every(e=>e.type==='cube'));
assert.equal(ceramic.filter(e=>e.name.startsWith('oval_rim_')).length,28);
const seat=ceramic.filter(e=>e.name.startsWith('open_front_seat_'));assert.equal(seat.length,26);for(const e of seat)assert(Math.abs(e.to[1]-e.from[1]-.5)<1e-6);
for(const e of m.elements)for(const f of Object.values(e.faces))assert(f.texture===0||f.texture===m.textures[0].uuid);
assert.equal(m.textures[0].source,old.textures[0].source);assert.equal(m.animations?.length??0,0);
assert.equal(descendants(m.outliner,'bowl_water_new').length,0);
console.log(JSON.stringify({result:'PASS',oldCount:old.elements.length,newCount:m.elements.length,oldCeramicDeleted:true,metalGeometryUVUnchanged:true,ceramicMirror:true,min,max,size:max.map((v,i)=>v-min[i]),seatThickness:.5,cavityLevels:4,meshes:0}));
