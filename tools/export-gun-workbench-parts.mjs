import fs from 'node:fs';
import assert from 'node:assert/strict';
import './validate-gun-workbench.mjs';

// Split the approved Blockbench Java export, never rebuild or rescale its geometry.
const root = 'src/main/resources/assets/apocalypse_firstlight/';
const read = p => JSON.parse(fs.readFileSync(p, 'utf8'));
const source = read('src/main/blockbench/gun_workbench.bbmodel');
const full = read(root + 'models/block/gun_workbench.json');
const parts = [['base',0,0], ['side',1,0], ['upper',0,1], ['upper_side',1,1]];
const near = (a,b) => Math.abs(a-b) < 1e-5;
const volume = e => e.to.reduce((v,n,i) => v*(n-e.from[i]),1);
const clean = n => Math.round(n*1e8)/1e8;
const write = (p,obj) => { fs.mkdirSync(p.slice(0,p.lastIndexOf('/')), {recursive:true}); fs.writeFileSync(p, JSON.stringify(obj,null,2)+'\n'); };
const faces = {
  north: [2,0,0,-1,1,-1], south: [2,1,0,1,1,-1],
  west: [0,0,2,1,1,-1], east: [0,1,2,-1,1,-1],
  up: [1,1,0,1,2,1], down: [1,0,0,1,2,-1]
};
const output = Object.fromEntries(parts.map(([name]) => [name,[]]));
const remaining = [...source.elements];
for (const original of full.elements) {
  const index = remaining.findIndex(s => s.name===original.name && ['from','to'].every(k=>s[k].every((v,i)=>near(v,original[k][i]))));
  assert(index>=0, `Export geometry stale: ${original.name}`);
  const [src] = remaining.splice(index,1);
  assert.equal(original.name, src.name);
  for (const bound of ['from','to']) assert.deepEqual(original[bound], src[bound]);
  for (const [name,face] of Object.entries(original.faces)) {
    face.uv.forEach((v,i) => assert(near(v,src.faces[name].uv[i]/16), `Export UV stale: ${src.name}`));
    assert(!face.rotation, 'Rotated UV needs explicit clipping support');
  }
  const expected = src.rotation ?? [0,0,0];
  const rotation = original.rotation;
  expected.forEach((v,i) => assert(near(v, rotation && rotation.axis === 'xyz'[i] ? rotation.angle : 0)));
  if (rotation?.angle) assert.deepEqual(rotation.origin,src.origin);
  const whole = structuredClone(original);
  whole.from[0] += 8; whole.to[0] += 8;
  let total = 0, fragments = 0;
  for (const [name,col,row] of parts) {
    const offset = [col*16,row*16,0];
    const lo = whole.from.map((v,i) => Math.max(v,offset[i]));
    const hi = whole.to.map((v,i) => Math.min(v,offset[i]+16));
    if (lo.some((v,i) => hi[i]-v <= 1e-8)) continue;
    const cut = structuredClone(whole); cut.from=lo; cut.to=hi; cut.faces={};
    for (const [side,face] of Object.entries(whole.faces)) {
      const [axis,positive,u,usign,v,vsign] = faces[side];
      if (!near(positive?hi[axis]:lo[axis],positive?whole.to[axis]:whole.from[axis])) continue;
      const interval = (i,sign) => sign>0
        ? [(lo[i]-whole.from[i])/(whole.to[i]-whole.from[i]),(hi[i]-whole.from[i])/(whole.to[i]-whole.from[i])]
        : [(whole.to[i]-hi[i])/(whole.to[i]-whole.from[i]),(whole.to[i]-lo[i])/(whole.to[i]-whole.from[i])];
      const [u0,u1]=interval(u,usign), [v0,v1]=interval(v,vsign);
      const [a,b,c,d]=face.uv;
      cut.faces[side]={...face,uv:[a+(c-a)*u0,b+(d-b)*v0,a+(c-a)*u1,b+(d-b)*v1].map(clean)};
    }
    total += volume(cut); fragments++;
    cut.from=lo.map((v,i)=>clean(v-offset[i])); cut.to=hi.map((v,i)=>clean(v-offset[i]));
    if (cut.rotation?.angle) cut.rotation.origin=cut.rotation.origin.map((v,i)=>clean(v+(i===0?8:0)-offset[i]));
    else delete cut.rotation;
    output[name].push(cut);
  }
  assert(near(total,volume(whole)), `Lost geometry: ${whole.name}`);
  assert(!rotation?.angle || fragments===1, `Rotated cube crosses cell: ${whole.name}`);
}
for(const [name,elements] of Object.entries(output)) {
  write(root+`models/block/gun_workbench/${name}.json`,{textures:full.textures,elements});
}
const variants={};
for(const [facing,y] of [['north',0],['east',90],['south',180],['west',270]]) for(const [name] of parts)
  variants[`facing=${facing},part=${name}`]={model:`apocalypse_firstlight:block/gun_workbench/${name}`,y};
write(root+'blockstates/gun_workbench.json',{variants});
write(root+'models/item/gun_workbench.json',{parent:'apocalypse_firstlight:block/gun_workbench'});
console.log('WORKBENCH_PARTS_EXPORTED',Object.fromEntries(Object.entries(output).map(([k,v])=>[k,v.length])));
