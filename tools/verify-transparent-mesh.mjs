// Read-only integration checks. No client, GPU, or shader-pack visual claims.
import fs from 'node:fs';
import zlib from 'node:zlib';
import assert from 'node:assert/strict';
import {read,convert,meshCounts} from './export-afl-mesh.mjs';
const root='src/main/resources/assets/apocalypse_firstlight';
const source=read('src/main/blockbench/pistol_red_dot_mesh.bbmodel');
const geo=read(`${root}/geo/pistol_red_dot.geo.json`),mesh=read(`${root}/meshes/pistol_red_dot.aflmesh.json`);
const layers=read('tools/pistol-red-dot.layers.json');
assert.deepEqual(mesh,convert(source,geo,{},'saved source',2,null,layers));
const bare=convert(source,geo,{},'saved source',2);
assert.deepEqual(mesh.parts.map(({render_layer,...p})=>p),bare.parts,'metadata only, no geometry/UV rewrite');
const glass=mesh.parts.filter(p=>p.render_layer==='translucent');
assert.equal(glass.length,1);assert.equal(mesh.parts.length-glass.length,5);
assert.equal(meshCounts({parts:glass}).triangleEquivalent,20);
assert.ok(geo['minecraft:geometry'][0].bones.some(b=>b.name==='lens_center'));
assert.ok(geo['minecraft:geometry'][0].bones.some(b=>b.name==='lens_aperture'));
assert.ok(!geo['minecraft:geometry'][0].bones.some(b=>b.name.includes('reticle')));
assert.equal(read(`${root}/models/item/pistol_red_dot.json`).parent,'builtin/entity');
for(const suffix of ['', '_s','_n']) assert.ok(fs.readFileSync(`${root}/textures/item/pistol_red_dot${suffix}.png`)
    .equals(fs.readFileSync(`src/main/blockbench/textures/pistol_red_dot${suffix}.png`)),'unchanged source atlas copy');
const png=fs.readFileSync(`${root}/textures/item/pistol_red_dot.png`),idat=[];
assert.equal(png.readUInt32BE(16),256);assert.equal(png.readUInt32BE(20),256);
assert.equal(png[24],8);assert.equal(png[25],6);
for(let at=8;at<png.length;) {const n=png.readUInt32BE(at);if(png.toString('ascii',at+4,at+8)==='IDAT') idat.push(png.subarray(at+8,at+8+n));at+=n+12;}
const raw=zlib.inflateSync(Buffer.concat(idat)),pixels=Buffer.alloc(256*256*4),stride=1024;
const paeth=(a,b,c)=>{const p=a+b-c,pa=Math.abs(p-a),pb=Math.abs(p-b),pc=Math.abs(p-c);return pa<=pb&&pa<=pc?a:pb<=pc?b:c;};
for(let y=0;y<256;y++)for(let x=0;x<stride;x++) {
    const i=y*stride+x,a=x>=4?pixels[i-4]:0,b=y?pixels[i-stride]:0,c=y&&x>=4?pixels[i-stride-4]:0;
    const filter=raw[y*(stride+1)];assert.ok(filter<=4);
    pixels[i]=(raw[y*(stride+1)+1+x]+[0,a,b,Math.floor((a+b)/2),paeth(a,b,c)][filter])&255;
}
for(const face of glass[0].faces) {
    const corners=face.map(i=>glass[0].vertices[i]);
    const u=Math.floor(256*corners.reduce((s,v)=>s+v[3],0)/corners.length);
    const v=Math.floor(256*corners.reduce((s,v)=>s+v[4],0)/corners.length);
    assert.equal(pixels[(v*256+u)*4+3],24,'lens alpha remains source-driven');
}
const high=fs.readFileSync('src/main/java/com/antaurora/apofirstlight/client/mesh/AflHybridMeshRendering.java','utf8');
assert.ok(high.indexOf('AflMeshPart.Layer.CUTOUT')<high.indexOf('AflMeshPart.Layer.TRANSLUCENT'));
assert.ok(high.includes('!AflShaderCompat.activeShadowPass()'));
assert.ok(high.includes('entityNoOutline(texture)'));
assert.ok(!high.includes('optic_lens')&&!high.includes('pistol_red_dot'));
console.log('TRANSPARENT_METADATA_PASS: 5 cutout + 1 translucent, 20 lens triangles / 80 vertices; source geometry/UV/atlases unchanged; alpha 24/255; locators retained; generic layer/shadow dispatch. GPU visuals NOT VERIFIED.');
