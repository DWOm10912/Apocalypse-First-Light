import fs from 'node:fs';
import assert from 'node:assert/strict';
const read=p=>JSON.parse(fs.readFileSync(p));
const m=read('src/main/blockbench/commercial_flushometer_toilet.bbmodel'),old=read('src/main/blockbench/previews/commercial_flushometer_toilet_before_liner_seal.bbmodel');
assert.equal(m.elements.length,198);assert.equal(m.elements.length,old.elements.length);
assert(m.elements.every(e=>e.type==='cube'));
const liner=m.elements.filter(e=>e.name.startsWith('ceramic_liner_'));
assert.equal(liner.length,60);
for(const e of old.elements.filter(e=>!e.name.startsWith('ceramic_liner_'))){const a=m.elements.find(v=>v.uuid===e.uuid);assert(a);for(const k of ['name','from','to','origin','rotation','faces','visibility'])assert.deepEqual(a[k],e[k],e.name+': '+k);}
for(const e of m.elements)for(const f of Object.values(e.faces)){assert(f.texture===null||f.texture===0||f.texture===m.textures[0].uuid);assert(f.uv.every(v=>v>=0&&v<=128));}
for(const e of liner)assert(Object.values(e.faces).every(f=>f.texture!==null),'Unsealed face '+e.name);
assert(!m.groups.some(g=>g.name==='dry_drain_funnel'));
assert(!m.elements.some(e=>/^(dry_drain_|drain_lip_|lower_drain_|front_funnel_|rear_funnel_|upper_side_funnel_|lower_side_funnel_)/.test(e.name)));
const water=m.elements.filter(e=>e.name==='small_water_surface');assert.equal(water.length,1);assert.deepEqual(water[0],old.elements.find(e=>e.uuid===water[0].uuid));
assert.equal(m.textures[0].source,old.textures[0].source);assert.deepEqual(Buffer.from(m.textures[0].source.split(',')[1],'base64'),fs.readFileSync('src/main/blockbench/textures/commercial_flushometer_toilet.png'));
assert.equal(m.animations?.length??0,0);
function vertices(e){const o=e.origin??[0,0,0],r=(e.rotation??[0,0,0]).map(v=>v*Math.PI/180),out=[];for(const x of [e.from[0],e.to[0]])for(const y of [e.from[1],e.to[1]])for(const z of [e.from[2],e.to[2]]){let[a,b,c]=[x-o[0],y-o[1],z-o[2]];[b,c]=[b*Math.cos(r[0])-c*Math.sin(r[0]),b*Math.sin(r[0])+c*Math.cos(r[0])];[a,c]=[a*Math.cos(r[1])+c*Math.sin(r[1]),-a*Math.sin(r[1])+c*Math.cos(r[1])];[a,b]=[a*Math.cos(r[2])-b*Math.sin(r[2]),a*Math.sin(r[2])+b*Math.cos(r[2])];out.push([a+o[0],b+o[1],c+o[2]]);}return out;}
const ps=liner.flatMap(vertices);for(const p of ps)assert(ps.some(q=>Math.abs(p[0]+q[0])<1e-4&&Math.abs(p[1]-q[1])<1e-4&&Math.abs(p[2]-q[2])<1e-4),'Liner mirror mismatch');
const all=m.elements.flatMap(vertices),min=[0,1,2].map(i=>Math.min(...all.map(p=>p[i]))),max=[0,1,2].map(i=>Math.max(...all.map(p=>p[i])));
assert(min[0]>=-8&&max[0]<=8&&min[2]>=-8&&max[2]<=8);assert.equal(min[1],0);
const audit=read('docs/models/previews/commercial_flushometer_toilet/liner_coverage.json');assert(audit.samples>=8000);assert.equal(audit.topHoles.length,0);assert.equal(audit.bottomHoles.length,0);assert.equal(audit.visibleStructure.length,0);
console.log(JSON.stringify({result:'PASS',cubes:198,linerPanels:60,exteriorAndWaterUnchanged:true,allLinerFacesBound:true,linerMirror:true,textureUnchanged:true,removedDrainParts:true,topAndBottomRaySamples:audit.samples,topHoles:0,bottomHoles:0,visibleInternalStructure:0,min,max,runtimeVerified:false},null,2));
