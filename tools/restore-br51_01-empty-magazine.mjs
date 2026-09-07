// Restore the missing object on the authored empty-reload driver, not its motion.
import fs from 'node:fs';
import crypto from 'node:crypto';
import assert from 'node:assert/strict';
const base='src/main/resources/assets/apocalypse_firstlight/';
const paths=['src/main/blockbench/br51_01.bbmodel',base+'geo/br51_01.geo.json',base+'animations/br51_01.animation.json'];
const [source,geo,animation]=paths.map(p=>JSON.parse(fs.readFileSync(p)));
const before=structuredClone(animation);
const sourceBefore=structuredClone(source.animations);
const bones=geo['minecraft:geometry'][0].bones;
const prefix='empty_old_';
const uid=s=>{const h=crypto.createHash('md5').update('afl-br51_01-empty/'+s).digest('hex');return `${h.slice(0,8)}-${h.slice(8,12)}-${h.slice(12,16)}-${h.slice(16,20)}-${h.slice(20)}`;};
function node(uuid,nodes=source.outliner){for(const n of nodes){if(typeof n==='string')continue;if(n.uuid===uuid)return n;const found=node(uuid,n.children||[]);if(found)return found;}}
const original=source.groups.find(g=>g.name==='mag_standard');
const target=source.groups.find(g=>g.name==='additional_magazine');
assert(original&&target);
if(!source.groups.some(g=>g.name===prefix+'mag_standard')){
  const names=new Set(['mag_standard']);
  let size;
  do {size=names.size;for(const b of bones)if(names.has(b.parent))names.add(b.name);}while(size!==names.size);
  for(const b of [...bones])if(names.has(b.name)){
    const copy=structuredClone(b);copy.name=prefix+b.name;
    copy.parent=b.name==='mag_standard'?'additional_magazine':prefix+b.parent;
    bones.push(copy);
  }
  function clone(n){
    if(typeof n==='string'){
      const e=structuredClone(source.elements.find(e=>e.uuid===n));assert(e);
      e.uuid=uid(n);e.name=prefix+e.name;source.elements.push(e);return e.uuid;
    }
    const g=structuredClone(source.groups.find(g=>g.uuid===n.uuid));assert(g);
    g.uuid=uid(n.uuid);g.name=prefix+g.name;source.groups.push(g);
    return {...n,uuid:g.uuid,children:n.children.map(clone)};
  }
  node(target.uuid).children.push(clone(node(original.uuid)));
}
// Only the added child gets visibility channels. All original channels stay intact.
const child=prefix+'mag_standard',childId=source.groups.find(g=>g.name===child).uuid;
for(const [name,clip]of Object.entries(animation.animations)){
  clip.bones[child]={scale:name==='reload_empty'?{'0.0':[1,1,1],'1.5':[0,0,0]}:[0,0,0]};
  // Keep the old magazine fully sized until its existing parent fades it at 1.4–1.5.
  if(name==='reload_empty')clip.bones[child].scale={'0.0':[1,1,1],'1.5':{pre:[1,1,1],post:[0,0,0]}};
}
for(const clip of source.animations){
  const empty=clip.name==='reload_empty';
  const key=(time,value)=>({uuid:uid(clip.name+'/'+time),channel:'scale',time,interpolation:'step',data_points:[{x:value,y:value,z:value}]});
  clip.animators[childId]={name:child,type:'bone',keyframes:empty?[key(0,1),key(1.5,0)]:[key(0,0)]};
}
for(const [name,clip]of Object.entries(before.animations)){
  for(const [bone,track]of Object.entries(clip.bones))assert.deepEqual(animation.animations[name].bones[bone],track,name+'/'+bone);
}
for(const clip of sourceBefore){const next=source.animations.find(a=>a.uuid===clip.uuid);for(const [id,track]of Object.entries(clip.animators))if(id!==childId)assert.deepEqual(next.animators[id],track);}
for(const [i,obj]of [source,geo,animation].entries())fs.writeFileSync(paths[i],JSON.stringify(obj,null,2));
console.log('Restored standard magazine subtree (24 cubes); original source/runtime tracks unchanged; added child visible only during reload_empty 0–1.5s.');
