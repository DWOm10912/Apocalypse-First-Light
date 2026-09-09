import fs from 'node:fs';
import assert from 'node:assert/strict';
const base='src/main/resources/assets/apocalypse_firstlight/';
const sp='src/main/blockbench/gun_maintenance_bench.bbmodel',mp=base+'models/block/gun_maintenance_bench.json',gp=base+'geo/gun_maintenance_bench.geo.json';
const read=p=>JSON.parse(fs.readFileSync(p,'utf8'));
const s=read(sp),m=read(mp),g=read(gp);
const removedGroups=new Set(s.groups.filter(x=>['vise','parts_tray'].includes(x.name)).map(x=>x.uuid));
const matGroup=s.groups.find(x=>x.name==='maintenance_mat').uuid;
const removed=new Set(),mat=new Set();
function collect(nodes,mode=''){for(const n of nodes){if(typeof n==='string'){if(mode==='remove')removed.add(n);if(mode==='mat')mat.add(n);}else collect(n.children??[],removedGroups.has(n.uuid)?'remove':n.uuid===matGroup?'mat':mode);}}
collect(s.outliner);
if(!removed.size){console.log('Layout already migrated');process.exit(0);}
assert.equal(removed.size,55);assert.equal(mat.size,2);
const deletedNames=new Set(s.elements.filter(e=>removed.has(e.uuid)).map(e=>e.name));
const matNames=new Set(s.elements.filter(e=>mat.has(e.uuid)).map(e=>e.name));
const round=n=>Math.round(n*1e6)/1e6;
function expand(e){for(const key of ['from','to']){e[key][0]=round(8.6+(e[key][0]-8.6)*2);e[key][2]=round(7.3+(e[key][2]-7.3)*1.4);}}
s.elements=s.elements.filter(e=>!removed.has(e.uuid));s.elements.filter(e=>mat.has(e.uuid)).forEach(expand);
s.groups=s.groups.filter(e=>!removedGroups.has(e.uuid));
function clean(a){return a.filter(n=>typeof n==='string'?!removed.has(n):!removedGroups.has(n.uuid)).map(n=>typeof n==='string'?n:{...n,children:clean(n.children??[])});}
s.outliner=clean(s.outliner);
const index=new Map();let next=0;m.elements.forEach((e,i)=>{if(!deletedNames.has(e.name))index.set(i,next++);});
m.elements=m.elements.filter(e=>!deletedNames.has(e.name));m.elements.filter(e=>matNames.has(e.name)).forEach(expand);
function groups(a){return a.filter(n=>typeof n==='number'?index.has(n):!['vise','parts_tray'].includes(n.name)).map(n=>typeof n==='number'?index.get(n):{...n,children:groups(n.children??[])});}
m.groups=groups(m.groups??[]);
g['minecraft:geometry'][0].bones=g['minecraft:geometry'][0].bones.filter(b=>!['vise','parts_tray'].includes(b.name));
for(const c of g['minecraft:geometry'][0].bones.find(b=>b.name==='maintenance_mat').cubes){c.origin[0]=round(-8.6+(c.origin[0]+8.6)*2);c.size[0]=round(c.size[0]*2);c.origin[2]=round(7.3+(c.origin[2]-7.3)*1.4);c.size[2]=round(c.size[2]*1.4);}
for(const [p,j]of[[sp,s],[mp,m],[gp,g]])fs.writeFileSync(p,JSON.stringify(j,null,p===sp?0:2)+'\n');
console.log('Removed 55 cubes; mat width x2, depth x1.4; center/height/UV preserved');
