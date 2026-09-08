import fs from 'node:fs';
import zlib from 'node:zlib';
import assert from 'node:assert/strict';

// Export project / java_block / bedrock from Blockbench first. This checks those
// actual exports, extracts the embedded atlas, and normalizes the optional Geo.
const id='precision_fabrication_station', base='src/main/resources/assets/apocalypse_firstlight/';
const read=p=>JSON.parse(fs.readFileSync(p,'utf8'));
const source=read(`src/main/blockbench/${id}.bbmodel`);
const java=read(base+`models/block/${id}.json`);
const geoPath=base+`geo/${id}.geo.json`, geo=read(geoPath), geometry=geo['minecraft:geometry'][0];
const near=(a,b)=>Math.abs(a-b)<1e-5;
const vec=(a,b)=>assert(a.length===b.length&&a.every((v,i)=>near(v,b[i])),`${a} != ${b}`);
const groups=new Map(), ordered=[];
const groupDefinitions=new Map((source.groups??[]).map(g=>[g.uuid,g]));
const elements=new Map(source.elements.map(e=>[e.uuid,e]));
function walk(list,parent) {
  for(const node of list) {
    if(typeof node==='string') {ordered.push(elements.get(node));continue;}
    const g={...groupDefinitions.get(node.uuid),...node};
    groups.set(g.name,{...g,parent}); walk(g.children,g.name);
  }
}
walk(source.outliner,null);
assert(source.elements.length>0 && source.elements.length<1000);
assert.equal(source.textures.length,1);assert.equal(source.resolution.width,256);assert.equal(source.resolution.height,256);
assert.equal(java.elements.length,source.elements.length);assert.equal(ordered.length,source.elements.length);
assert.equal(geometry.bones.length,groups.size);
assert.equal(geometry.description.identifier,`geometry.${id}`);
assert.equal(java.textures['0'],`apocalypse_firstlight:block/${id}`);
assert.equal(java.textures.particle,java.textures['0']);
assert(!source.animations?.length,'V1 must stay static');
const required=['assembly_head','central_fixture_left_clamp','central_fixture_right_clamp','work_light','status_light'];
required.forEach(n=>assert(groups.has(n),n));
for(const [i,c] of ordered.entries()) {
  const j=java.elements[i];assert.equal(c.name,j.name);vec(c.from,j.from);vec(c.to,j.to);
  assert(!(c.rotation??[]).some(v=>v!==0),'Rotated future geometry needs extended verification');
  for(const [side,f] of Object.entries(c.faces)) {
    assert.equal(source.textures[f.texture]?.uuid??f.texture,source.textures[0].uuid);
    assert.equal(j.faces[side].texture,'#0');vec(f.uv.map(v=>v/16),j.faces[side].uv);
    assert(!f.cullface && !j.faces[side].cullface);
    assert(f.uv.every(v=>v>=0&&v<=256));
  }
}
for(const bone of geometry.bones) {
  const g=groups.get(bone.name);assert(g);assert.equal(bone.parent??null,g.parent);vec(bone.pivot,[-g.origin[0],g.origin[1],g.origin[2]]);
  const cubes=g.children.filter(n=>typeof n==='string').map(n=>elements.get(n));
  assert.equal((bone.cubes??[]).length,cubes.length);
  cubes.forEach((c,i)=>{
    const b=bone.cubes[i];vec(b.origin,[-c.to[0],c.from[1],c.from[2]]);vec(b.size,c.to.map((v,k)=>v-c.from[k]));
    for(const [side,f]of Object.entries(c.faces)) {
      const [u,v,s,t]=f.uv, flip=side==='up'||side==='down';
      vec(b.uv[side].uv,flip?[s,t]:[u,v]);vec(b.uv[side].uv_size,flip?[u-s,v-t]:[s-u,t-v]);
    }
  });
}
let overlap=0,coplanar=0;
for(let i=0;i<source.elements.length;i++)for(let j=i+1;j<source.elements.length;j++) {
  const a=source.elements[i],b=source.elements[j];
  const d=a.to.map((v,k)=>Math.min(v,b.to[k])-Math.max(a.from[k],b.from[k]));
  if(d.every(v=>v>1e-5))overlap++;
  for(let k=0;k<3;k++)if(d.every((v,t)=>t===k||v>1e-5))
    for(const side of ['from','to'])if(near(a[side][k],b[side][k]))coplanar++;
}
assert.equal(overlap,0,'Intersecting solids');assert.equal(coplanar,0,'Duplicate coplanar faces');
const min=[0,1,2].map(i=>Math.min(...source.elements.map(e=>e.from[i])));
const max=[0,1,2].map(i=>Math.max(...source.elements.map(e=>e.to[i])));
vec(min,[-8,0,.15]);vec(max,[24,32,16]);
const port=source.elements.find(e=>e.name==='standard_machine_back_panel');
assert(port);vec(port.faces.south.uv,[224,224,256,256]);

function pixels(bytes) {
  const width=bytes.readUInt32BE(16),height=bytes.readUInt32BE(20),type=bytes[25];
  assert.equal(bytes[24],8);assert.equal(bytes[28],0);assert(type===6||type===2);
  let pos=8,idat=[];while(pos<bytes.length){const len=bytes.readUInt32BE(pos),tag=bytes.toString('ascii',pos+4,pos+8);if(tag==='IDAT')idat.push(bytes.subarray(pos+8,pos+8+len));pos+=12+len;}
  const raw=zlib.inflateSync(Buffer.concat(idat)),channels=type===6?4:3,stride=width*channels;
  const unfiltered=Buffer.alloc(height*stride),rgba=Buffer.alloc(width*height*4);
  const paeth=(a,b,c)=>{const p=a+b-c,x=Math.abs(p-a),y=Math.abs(p-b),z=Math.abs(p-c);return x<=y&&x<=z?a:y<=z?b:c;};
  for(let y=0;y<height;y++) {const filter=raw[y*(stride+1)];assert(filter<=4);
    for(let x=0;x<stride;x++){const a=x>=channels?unfiltered[y*stride+x-channels]:0,b=y?unfiltered[(y-1)*stride+x]:0,c=y&&x>=channels?unfiltered[(y-1)*stride+x-channels]:0;
      unfiltered[y*stride+x]=(raw[y*(stride+1)+1+x]+[0,a,b,Math.floor((a+b)/2),paeth(a,b,c)][filter])&255;}}
  for(let i=0;i<width*height;i++){for(let c=0;c<3;c++)rgba[i*4+c]=unfiltered[i*channels+c];rgba[i*4+3]=channels===4?unfiltered[i*4+3]:255;}
  return {width,height,rgba};
}
const atlas=Buffer.from(source.textures[0].source.split(',')[1],'base64');
const p=pixels(atlas),standard=pixels(fs.readFileSync(base+'textures/block/machine_back.png'));
assert.equal(p.width,256);assert.equal(p.height,256);assert.equal(standard.width,32);assert.equal(standard.height,32);
for(let y=0;y<32;y++)for(let x=0;x<32;x++) {
  const a=((y+224)*256+x+224)*4,b=(y*32+x)*4;
  const socket=x>=11&&x<21&&y>=11&&y<21;
  const background=(y*256+x+96)*4;
  const border=x<2||x>=30||y<2||y>=30;
  assert(p.rgba.subarray(a,a+4).equals(socket?standard.rgba.subarray(b,b+4):border?Buffer.from([98,102,106,255]):p.rgba.subarray(background,background+4)),
    socket?'Standard socket pixels changed':'Port background does not match housing panel');
}
fs.writeFileSync(base+`textures/block/${id}.png`,atlas);
geo.format_version='1.12.0';delete geometry.item_display_transforms;
fs.writeFileSync(geoPath,JSON.stringify(geo,null,2)+'\n');
console.log(JSON.stringify({id,cubes:source.elements.length,groups:groups.size,min,max,atlas:'256x256',
  overlap,coplanar,machineSocketExactPixels:true,portBackgroundMatchesHousing:true,sourceRuntimeSync:true,animationReadyGroups:required},null,2));
