import fs from 'node:fs';
import assert from 'node:assert/strict';
const read=p=>JSON.parse(fs.readFileSync(p));
const m=read('src/main/blockbench/commercial_flushometer_toilet.bbmodel'),old=read('src/main/blockbench/previews/commercial_flushometer_toilet_before_closed_dry_shell.bbmodel');
const shell=m.elements.filter(e=>e.name.startsWith('dry_shell_')||e.name==='closed_ceramic_bottom');assert.equal(shell.length,61);
assert(m.elements.every(e=>e.type==='cube'));
for(const e of shell){assert(e.to.every((v,i)=>v>e.from[i]),'Zero/negative size: '+e.name);assert(Object.values(e.faces).every(f=>f.texture===0||f.texture===m.textures[0].uuid),'Missing face: '+e.name);}
for(const e of shell.filter(e=>e.name.startsWith('dry_shell_')))assert(Math.abs(e.to[2]-e.from[2]-.3)<1e-5);
const cap=shell.find(e=>e.name==='closed_ceramic_bottom');assert(Math.abs(cap.to[1]-cap.from[1]-.35)<1e-6);
for(const e of old.elements.filter(e=>!e.name.startsWith('ceramic_liner_'))){const a=m.elements.find(v=>v.uuid===e.uuid);assert(a,'Removed protected element: '+e.name);for(const k of ['name','from','to','origin','rotation','faces','visibility'])assert.deepEqual(a[k],e[k],e.name+': '+k);}
assert(!m.elements.some(e=>/water|funnel|drain_lip|drain_recess|ceramic_liner_/.test(e.name)));
assert.equal(m.textures[0].source,old.textures[0].source);assert.deepEqual(Buffer.from(m.textures[0].source.split(',')[1],'base64'),fs.readFileSync('src/main/blockbench/textures/commercial_flushometer_toilet.png'));
for(const e of m.elements)for(const f of Object.values(e.faces))assert(f.texture===null||f.texture===0||f.texture===m.textures[0].uuid);
const dot=(a,b)=>a.reduce((s,v,i)=>s+v*b[i],0),sub=(a,b)=>a.map((v,i)=>v-b[i]),cross=(a,b)=>[a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]];
function rotate(p,r){let[a,b,c]=p;[b,c]=[b*Math.cos(r[0])-c*Math.sin(r[0]),b*Math.sin(r[0])+c*Math.cos(r[0])];[a,c]=[a*Math.cos(r[1])+c*Math.sin(r[1]),-a*Math.sin(r[1])+c*Math.cos(r[1])];return [a*Math.cos(r[2])-b*Math.sin(r[2]),a*Math.sin(r[2])+b*Math.cos(r[2]),c];}
function obb(e){const r=(e.rotation??[0,0,0]).map(v=>v*Math.PI/180),o=e.origin??[0,0,0],mid=e.from.map((v,i)=>(v+e.to[i])/2);return {c:rotate(sub(mid,o),r).map((v,i)=>v+o[i]),h:e.from.map((v,i)=>(e.to[i]-v)/2),u:[[1,0,0],[0,1,0],[0,0,1]].map(v=>rotate(v,r))};}
function overlap(x,y){const a=obb(x),b=obb(y),d=sub(a.c,b.c),axes=[...a.u,...b.u,...a.u.flatMap(u=>b.u.map(v=>cross(u,v)))];return axes.every(n=>Math.abs(dot(d,n))<=a.h.reduce((s,h,i)=>s+h*Math.abs(dot(a.u[i],n)),0)+b.h.reduce((s,h,i)=>s+h*Math.abs(dot(b.u[i],n)),0)+1e-5);}
const panel=(j,i)=>shell.find(e=>e.name===`dry_shell_${j}_${i}`);let joins=0;
for(let j=0;j<3;j++)for(let i=0;i<20;i++){assert(overlap(panel(j,i),panel(j,(i+1)%20)),`Lateral gap ${j}/${i}`);joins++;if(j<2){assert(overlap(panel(j,i),panel(j+1,i)),`Tier gap ${j}/${i}`);joins++;}else{assert(overlap(panel(j,i),cap),`Bottom gap ${i}`);joins++;}}
const coverage=read('docs/models/previews/commercial_flushometer_toilet/dry_shell_coverage.json');assert(coverage.rays>10000);assert.equal(coverage.misses.length,0);
console.log(JSON.stringify({result:'PASS',totalCubes:m.elements.length,shellCubes:61,wallThickness:.3,bottomThickness:.35,zeroSizeCubes:0,exteriorPreserved:true,water:false,joins,isolatedShellRays:coverage.rays,misses:0,runtimeVerified:false},null,2));
