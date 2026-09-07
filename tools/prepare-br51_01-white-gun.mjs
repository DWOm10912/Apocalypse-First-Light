// V1 asset selection only. Preserve source geometry and all animation channels.
import fs from 'node:fs';
import assert from 'node:assert/strict';
const base='src/main/resources/assets/apocalypse_firstlight/';
const sp='src/main/blockbench/br51_01.bbmodel',gp=base+'geo/br51_01.geo.json';
const source=JSON.parse(fs.readFileSync(sp)),geo=JSON.parse(fs.readFileSync(gp));
const anim=JSON.parse(fs.readFileSync(base+'animations/br51_01.animation.json'));
const before=JSON.stringify(source.animations);
const roots=['mag_extended_1','mag_extended_2','mag_extended_3','sight','grip_default'];
const groups=new Map(source.groups.map(g=>[g.uuid,g]));
const elements=new Map(source.elements.map(e=>[e.uuid,e]));
const excluded=new Set();let count=0;
function visit(nodes,hidden=false){for(const n of nodes){
  if(typeof n==='string'){if(hidden){const e=elements.get(n);assert(e);e.visibility=false;e.export=false;count++;}continue;}
  const g=groups.get(n.uuid);assert(g);
  const omit=hidden||roots.includes(g.name);
  if(omit){g.visibility=false;g.export=false;excluded.add(g.name);}
  visit(n.children||[],omit);
}}
visit(source.outliner);
for(const name of roots)assert(excluded.has(name),'Missing source selection '+name);
const model=geo['minecraft:geometry'][0];
// No selected accessory is an animation driver in the eight current clips.
for(const [name,clip]of Object.entries(anim.animations))for(const bone of Object.keys(clip.bones))assert(!excluded.has(bone),'Animated excluded bone '+name+'/'+bone);
model.bones=model.bones.filter(b=>!excluded.has(b.name));
const names=new Set(model.bones.map(b=>b.name));
for(const b of model.bones)assert(!b.parent||names.has(b.parent),'Dangling parent '+b.name);
for(const name of ['mag_standard','empty_old_mag_standard','bolt','br51_01_default','right_hand_anchor','left_hand_anchor'])assert(names.has(name));
assert.equal(Object.keys(anim.animations).length,8);
assert.equal(JSON.stringify(source.animations),before);
fs.writeFileSync(sp,JSON.stringify(source,null,2));fs.writeFileSync(gp,JSON.stringify(geo,null,2));
console.log(JSON.stringify({sourceOnlyGroups:[...excluded],sourceOnlyCubes:count,runtimeBones:model.bones.length,animationChanged:false},null,2));
