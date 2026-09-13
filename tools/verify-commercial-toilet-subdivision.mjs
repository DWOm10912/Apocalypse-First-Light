import fs from 'node:fs';
import assert from 'node:assert/strict';
const read=p=>JSON.parse(fs.readFileSync(p));
const path='src/main/blockbench/commercial_flushometer_toilet.bbmodel';
const m=read(path),old=read('src/main/blockbench/previews/commercial_flushometer_toilet_before_subdivision.bbmodel');
function ids(model,name){const names=new Map(model.groups.map(g=>[g.uuid,g.name]));const flat=n=>n.flatMap(v=>typeof v==='string'?[v]:flat(v.children));const walk=n=>n.flatMap(v=>typeof v==='string'?[]:names.get(v.uuid)===name?flat(v.children):walk(v.children));return walk(model.outliner);}
const metal=ids(old,'flushometer_assembly');assert.equal(metal.length,18);
for(const id of metal){const a=m.elements.find(e=>e.uuid===id),b=old.elements.find(e=>e.uuid===id);assert(a);for(const k of ['from','to','origin','rotation','faces'])assert.deepEqual(a[k],b[k]);}
function vertices(e){const o=e.origin??[0,0,0],r=(e.rotation??[0,0,0]).map(v=>v*Math.PI/180),out=[];for(const x of [e.from[0],e.to[0]])for(const y of [e.from[1],e.to[1]])for(const z of [e.from[2],e.to[2]]){let[a,b,c]=[x-o[0],y-o[1],z-o[2]];[b,c]=[b*Math.cos(r[0])-c*Math.sin(r[0]),b*Math.sin(r[0])+c*Math.cos(r[0])];[a,c]=[a*Math.cos(r[1])+c*Math.sin(r[1]),-a*Math.sin(r[1])+c*Math.cos(r[1])];[a,b]=[a*Math.cos(r[2])-b*Math.sin(r[2]),a*Math.sin(r[2])+b*Math.cos(r[2])];out.push([a+o[0],b+o[1],c+o[2]]);}return out;}
function bounds(model){const p=model.elements.flatMap(vertices);return {min:[0,1,2].map(i=>Math.min(...p.map(v=>v[i]))),max:[0,1,2].map(i=>Math.max(...p.map(v=>v[i])))};}
const b=bounds(m),before=bounds(old);
assert(b.min[0]>=-8&&b.max[0]<=8&&b.min[2]>=-8&&b.max[2]<=8);
assert.equal(b.min[1],0);
const ceramic=m.elements.filter(e=>!metal.includes(e.uuid)),p=ceramic.flatMap(vertices),key=p=>p.map(v=>Math.round(v*1e4)).join(','),keys=new Set(p.map(key));
const mismatches=p.filter(v=>!keys.has(key([-v[0],v[1],v[2]])));
assert.equal(mismatches.length,0,'Mirror mismatches: '+JSON.stringify(mismatches.slice(0,5)));
assert.equal(m.elements.length,179);assert(m.elements.every(e=>e.type==='cube'));
assert.equal(m.elements.filter(e=>e.name.startsWith('rim_subfacet_')).length,20);
assert.equal(m.elements.filter(e=>e.name.startsWith('crown_subfacet_')).length,20);
const seat=m.elements.filter(e=>e.name.startsWith('seat_subfacet_'));assert.equal(seat.length,17);
for(const e of seat)assert(Math.abs(e.to[1]-e.from[1]-.55)<1e-6);
assert.equal(ids(m,'bowl_water').length,0);assert.equal(m.animations?.length??0,0);
for(const e of m.elements)for(const f of Object.values(e.faces)){assert(f.texture===null||f.texture===0||f.texture===m.textures[0].uuid);assert(f.uv.every(v=>v>=0&&v<=128));}
assert.equal(m.textures[0].source,old.textures[0].source);
assert.deepEqual(Buffer.from(m.textures[0].source.split(',')[1],'base64'),fs.readFileSync('src/main/blockbench/textures/commercial_flushometer_toilet.png'));
for(const n of ['rear_neck','valve_socket_deck','hinge_-1','hinge_1','rear_support_shoulder']){const e=old.elements.find(e=>e.name===n),a=m.elements.find(v=>v.uuid===aId(e));assert(a);for(const k of ['from','to','origin','rotation','faces'])assert.deepEqual(a[k],e[k]);}
function aId(e){assert(e);return e.uuid;}
const size=b.max.map((v,i)=>v-b.min[i]),oldSize=before.max.map((v,i)=>v-before.min[i]);
assert(size.every((v,i)=>Math.abs(v-oldSize[i])<.05),'Overall proportion drift');
console.log(JSON.stringify({result:'PASS',beforeCubes:old.elements.length,cubes:m.elements.length,outlineFacets:20,seatFacets:seat.length,seatThickness:.55,bounds:b,previousBounds:before,size,oldSize,metalUnchanged:true,coreAnchorsUnchanged:true,mirror:true,texturePNGMatches:true,liquid:false,runtimeTested:false},null,2));
// Latest change: keep the entire rim/seat/base from the 161-cube checkpoint.
const previous=read('src/main/blockbench/previews/commercial_flushometer_toilet_before_integrated_body.bbmodel');
for(const group of ['bowl_rim','open_front_seat','base'])for(const id of ids(previous,group)){
 const a=m.elements.find(e=>e.uuid===id),b=previous.elements.find(e=>e.uuid===id);assert(a);
 for(const k of ['from','to','origin','rotation','faces'])assert.deepEqual(a[k],b[k]);
}
assert(!m.elements.some(e=>/^(dry_ceramic_floor|dry_floor_corner_|underbody_core)/.test(e.name)));
assert(!m.elements.some(e=>e.name==='dry_drain_recess'),'Do not restore the removed dark drain cap');
const dot=(a,b)=>a.reduce((s,v,i)=>s+v*b[i],0),sub=(a,b)=>a.map((v,i)=>v-b[i]),cross=(a,b)=>[a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]];
function obb(e){const v=vertices(e),edges=[4,2,1].map(i=>sub(v[i],v[0])),h=edges.map(v=>Math.hypot(...v)/2);return {e,c:v[0].map((x,i)=>(x+v[7][i])/2),h,u:edges.map((v,i)=>v.map(x=>x/(h[i]*2)))};}
const boxes=m.elements.map(obb);
function overlaps(a,b){const d=sub(a.c,b.c),axes=[...a.u,...b.u,...a.u.flatMap(x=>b.u.map(y=>cross(x,y)))];return axes.every(n=>Math.abs(dot(d,n))<=a.h.reduce((s,h,i)=>s+h*Math.abs(dot(a.u[i],n)),0)+b.h.reduce((s,h,i)=>s+h*Math.abs(dot(b.u[i],n)),0)+1e-6);}
const bridgeIds=ids(m,'continuous_ceramic_throat'),pedIds=ids(m,'pedestal'),shellIds=ids(m,'bowl_shell'),baseIds=ids(m,'base');
const connected=(as,bs)=>boxes.filter(a=>as.includes(a.e.uuid)).some(a=>boxes.filter(b=>bs.includes(b.e.uuid)).some(b=>overlaps(a,b)));
assert(connected(bridgeIds,shellIds),'Throat must touch bowl');assert(connected(bridgeIds,pedIds),'Throat must touch pedestal');assert(connected(pedIds,baseIds),'Pedestal must touch base');
// A vertical ray through the drain must not encounter the old flat support cap.
const rayOrigin=[0,20,.48],direction=[0,-1,0];
const hits=boxes.map(b=>{const d=sub(rayOrigin,b.c);let lo=0,hi=Infinity;for(let i=0;i<3;i++){const o=dot(d,b.u[i]),v=dot(direction,b.u[i]);if(Math.abs(v)<1e-9){if(Math.abs(o)>b.h[i])return null;continue;}const t=[(-b.h[i]-o)/v,(b.h[i]-o)/v].sort((a,b)=>a-b);lo=Math.max(lo,t[0]);hi=Math.min(hi,t[1]);if(lo>hi)return null;}return {name:b.e.name,t:lo};}).filter(Boolean).sort((a,b)=>a.t-b.t);
assert.equal(hits[0].name,'throat_solid_core','Drain blocked by '+JSON.stringify(hits.slice(0,3)));
assert(Math.abs(20-hits[0].t-4.42)<1e-6,'Support must remain below the drain lip');
console.log(JSON.stringify({integratedBody:'PASS',rimSeatBaseUnchanged:true,solidConnections:'bowl -> throat -> pedestal -> base',oldFlatFloorRemoved:true,drainCenterClearToY:4.42,removedDarkCapPreserved:true}));
