import assert from 'node:assert/strict';
import fs from 'node:fs';
const root='src/main/blockbench/';
const model=JSON.parse(fs.readFileSync(root+'restroom_stall_door.bbmodel'));
const partition=JSON.parse(fs.readFileSync(root+'restroom_partition.bbmodel'));
const preview=JSON.parse(fs.readFileSync(root+'previews/restroom_stall_door_installation_preview.bbmodel'));
assert.equal(model.elements.length,42);
assert.equal(model.textures.length,1);
assert.equal(model.resolution.width,128);assert.equal(model.resolution.height,128);
assert.equal(model.animations.length,2);
for (const [i,a] of model.animations.entries()) {
  assert.equal(a.name,`restroom_stall_door_${i===0?'open':'close'}`);
  assert.equal(a.length,.35);assert.equal(a.loop,'hold');
  const active=Object.values(a.animators).filter(b=>b.keyframes?.length);
  assert.equal(active.length,1);assert.equal(active[0].name,'door_leaf');
  const keys=active[0].keyframes;
  assert.equal(keys.length,2);
  keys.forEach((k,j)=>{
    assert.equal(k.channel,'rotation');assert.equal(k.interpolation,'bezier');
    assert.equal(k.time,j*.35);
    assert.deepEqual(Object.values(k.data_points[0]).map(Number),[0,(i===j?0:-90),0]);
    assert(k.bezier_left_value.every(v=>v===0)&&k.bezier_right_value.every(v=>v===0));
  });
}
assert.deepEqual(model.groups.find(g=>g.name==='door_leaf').origin,[14.6,0,6.95]);
assert(model.groups.every(g=>g.rotation.every(v=>v===0)));
assert(!model.elements.some(e=>/post|foot|rail|preview/.test(e.name)));
assert(model.elements.every(e=>Object.values(e.faces).every(f=>f.texture===0)));
for(const e of model.elements)assert(e.from.every((v,i)=>v<e.to[i]));
const png=fs.readFileSync(root+'textures/restroom_stall_door.png');
assert(png.equals(fs.readFileSync(root+'textures/restroom_partition.png')));
assert(png.equals(Buffer.from(model.textures[0].source.split(',')[1],'base64')));
assert(preview.elements.filter(e=>e.name.startsWith('preview_only_')).every(e=>e.export===false));
const posts=partition.elements.filter(e=>/post|foot/.test(e.name));
const rotating=model.elements.filter(e=>!e.name.startsWith('hinge_'));
const rectangle=e=>[[e.from[0],e.from[2]],[e.to[0],e.from[2]],[e.to[0],e.to[2]],[e.from[0],e.to[2]]];
const overlaps=(a,b)=>{
  for(const poly of [a,b])for(let i=0;i<4;i++){
    const p=poly[i],q=poly[(i+1)%4],axis=[q[1]-p[1],p[0]-q[0]];
    const pa=a.map(v=>v[0]*axis[0]+v[1]*axis[1]),pb=b.map(v=>v[0]*axis[0]+v[1]*axis[1]);
    if(Math.min(Math.max(...pa),Math.max(...pb))-Math.max(Math.min(...pa),Math.min(...pb))<1e-8)return false;
  }
  return true;
};
for(let degree=0;degree<=90;degree++){
  const angle=-degree*Math.PI/180,c=Math.cos(angle),s=Math.sin(angle);
  for(const e of rotating){
    const poly=rectangle(e).map(([x,z])=>{x-=14.6;z-=6.95;return [14.6+x*c+z*s,6.95-x*s+z*c];});
    for(const post of posts){
      if(Math.min(e.to[1],post.to[1])-Math.max(e.from[1],post.from[1])<=1e-8)continue;
      assert(!overlaps(poly,rectangle(post)),`${degree} degrees: ${e.name} intersects ${post.name}`);
    }
  }
}
console.log('PASS: 42 cubes, 128x128 bound atlas, two 0.35s rotation-only clips, no production posts, 0..90 degree leaf/frame/handle/latch clearance sweep; hinge hardware excluded from clearance sweep.');
