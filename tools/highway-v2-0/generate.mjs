import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {createHash} from 'node:crypto';
import assert from 'node:assert/strict';
import {convert,serializeCompact,meshCounts} from '../export-afl-mesh.mjs';
import {png} from '../cube-slab-mesh-lib.mjs';
import {createRoad} from './geometry.mjs';
import {previewFiles} from './preview.mjs';
export const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
export const demo='tools/highway-v2-0/demo';
export const sourceDir='src/main/blockbench/dev/highway_v2_0';
export const materialColors={asphalt:[57,64,72],shoulder:[94,102,107],median:[79,113,70],white:[239,241,230],yellow:[237,192,55]};
export function build() {
  const p=JSON.parse(fs.readFileSync(path.join(root,'tools/highway-v2-0/recipe.json'),'utf8'));
  const road=createRoad(p),files=new Map(),N=p.textureSize,rgba=Buffer.alloc(N*N*4),materials=Object.keys(materialColors);
  // Five swatches, periodic in V, with unused padding. Placeholder only, no authored PBR.
  for(let y=0;y<N;y++)for(let x=0;x<N;x++){
    const k=Math.min(4,Math.floor(x/(N/5))),c=materialColors[materials[k]],variation=k<3?(y%16<8?2:-2):0;
    const i=(y*N+x)*4;rgba.set([...c.map(v=>v+variation),255],i);
  }
  const texture=png(rgba,N,N);files.set(demo+'/placeholder.png',texture);
  const modules=[],scene=[];
  for(let start=0;start<p.length;start+=p.moduleLength) {
    const end=start+p.moduleLength,id='highway_v2_0_demo_'+String(start).padStart(3,'0'),origin=[road.frame(start).x,road.frame(start).y,road.frame(start).z];
    const elements=materials.map((material,i)=>({name:material,uuid:`demo-part-${i}`,type:'mesh',origin:[0,0,0],rotation:[0,0,0],shading:'flat',vertices:{},faces:{}}));
    function patch(s0,s1,u0,u1,material,raised=0) {
      const element=elements[materials.indexOf(material)],idx=Object.keys(element.faces).length;
      // Lateral first, station second: CCW upward in +X-forward / +Z-lateral coordinates.
      const params=[[s0,u0],[s0,u1],[s1,u1],[s1,u0]],keys=params.map((_,i)=>`v${idx}_${i}`),uv={};
      const tile=Math.floor((s0+1e-8)/p.textureRepeat),mi=materials.indexOf(material);
      params.forEach(([s,u],i)=>{
        const pos=road.surface(s,u);pos[1]+=raised;
        element.vertices[keys[i]]=pos.map((v,k)=>(v-origin[k])*16);
        uv[keys[i]]=[N*(mi/5+0.02+0.16*(i===1||i===2?1:0)),N*(0.0625+0.875*(s-tile*p.textureRepeat)/p.textureRepeat)];
      });
      element.faces[`f${String(idx).padStart(5,'0')}`]={vertices:keys,uv,texture:0};
    }
    for(let s=start;s<end;s+=p.stationStep) {
      const next=Math.min(end,s+p.stationStep);
      for(const band of road.bands)patch(s,next,band.a,band.b,band.material);
      for(const line of road.markings)if(!line.dashed||s%p.dashPeriod<p.dashOn)
        patch(s,next,line.u-p.markingWidth/2,line.u+p.markingWidth/2,line.material,0.003);
    }
    const source={meta:{format_version:'4.10',model_format:'free'},name:id,resolution:{width:N,height:N},
      textures:[{uuid:'demo-texture',name:'placeholder.png',uv_width:N,uv_height:N,width:N,height:N,source:'data:image/png;base64,'+texture.toString('base64')}],
      elements,outliner:[{name:'road',uuid:'demo-road-root',origin:[0,0,0],children:elements.map(e=>e.uuid)}]};
    const geometry={format_version:'1.12.0','minecraft:geometry':[{description:{identifier:'geometry.'+id,texture_width:N,texture_height:N},bones:[{name:'road',pivot:[0,0,0]}]}]};
    const diagnostics={},mesh=convert(source,geometry,{},id,2,diagnostics);
    const meshText=serializeCompact(mesh);assert(meshText.length<=4*1024*1024,'loader character budget');
    files.set(sourceDir+'/'+id+'.bbmodel',serializeCompact(source));
    files.set(demo+'/'+id+'.geo.json',serializeCompact(geometry));
    files.set(demo+'/'+id+'.aflmesh.json',meshText);
    const vertices=mesh.parts.flatMap(p=>p.vertices),bounds={min:[0,1,2].map(k=>Math.min(...vertices.map(v=>v[k]))),max:[0,1,2].map(k=>Math.max(...vertices.map(v=>v[k])))};
    const seam=s=>road.bands.flatMap(b=>[b.a,b.b]).filter((u,i,a)=>a.indexOf(u)===i).sort((a,b)=>a-b).map(u=>({u,position:road.surface(s,u),normal:road.normal(s,u)}));
    modules.push({id,start,end,origin,source:sourceDir+'/'+id+'.bbmodel',mesh:demo+'/'+id+'.aflmesh.json',geometry:demo+'/'+id+'.geo.json',
      storedVertices:vertices.length,...meshCounts(mesh),bounds,extent:bounds.max.map((v,i)=>v-bounds.min[i]),diagnostics,seams:{start:seam(start),end:seam(end)}});
    for(const part of mesh.parts) for(const face of part.faces)scene.push({color:materialColors[part.name],points:face.map(i=>part.vertices[i].slice(0,3).map((x,k)=>x+origin[k]))});
  }
  const samples=[];
  for(let s=0;s<=p.length;s+=p.stationStep)samples.push({...road.frame(s),surfaceNormalAtLane:road.normal(s,p.medianUnpaved/2+p.insideShoulder+p.laneWidth/2)});
  files.set(demo+'/centerline.csv','station_m,x_m,y_datum_m,z_m,grade,heading_rad,curvature_per_m,bank_weight\n'+samples.map(f=>[f.s,f.x,f.y,f.z,f.grade,f.heading,f.curvature,f.bankWeight].join(',')).join('\n')+'\n');
  files.set(demo+'/metadata.json',JSON.stringify({recipe:p,axes:'Y up; station initially +X; lateral +Z; origins in virtual world coordinates',
    routeLength:p.length,width:2*road.halfWidth,samples,modules,simulatedChunkSeam:{worldPlane:'x=64',station:64,leftChunkX:3,rightChunkX:4,scope:'All XZ chunks crossed by road width share this plane. Other module joins are station windows, not actual chunk clipping.'},
    limitations:['Visual only; no collision, registry, RouteGraph or worldgen hookup','5% vertical demonstration is not 60-80 mph design acceptance','Preview uses material colors, not Minecraft shading/PBR or texel sampling','Flat face normals: analytic seam normals are future-query metadata, not imported vertex normals','Markings float 0.003 m: shader/depth acceptance is untested','Four unique windows demonstrate export, not a national permanent asset catalog']},null,2)+'\n');
  for(const [name,data] of previewFiles(scene,samples,p))files.set(demo+'/'+name,data);
  const hashes=Object.fromEntries([...files].map(([name,data])=>[name,createHash('sha256').update(data).digest('hex')]));
  const inputNames=['tools/highway-v2-0/recipe.json','tools/highway-v2-0/geometry.mjs','tools/highway-v2-0/generate.mjs','tools/highway-v2-0/preview.mjs','tools/export-afl-mesh.mjs','tools/cube-slab-mesh-lib.mjs'];
  const inputs=Object.fromEntries(inputNames.map(name=>[name,createHash('sha256').update(fs.readFileSync(path.join(root,name),'utf8').replace(/\r\n/g,'\n')).digest('hex')]));
  files.set(demo+'/manifest.json',JSON.stringify({recipeVersion:p.recipeVersion,inputHashNormalization:'UTF-8 LF',inputsSha256:inputs,sha256:hashes},null,2)+'\n');
  return {files,modules,p};
}
export function writeOrCheck(files,check) {
  for(const [name,data] of files){const file=path.join(root,name),bytes=Buffer.isBuffer(data)?data:Buffer.from(data);if(check)assert(fs.existsSync(file)&&fs.readFileSync(file).equals(bytes),'stale/missing: '+name);else {fs.mkdirSync(path.dirname(file),{recursive:true});fs.writeFileSync(file,bytes);}}
}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url)) {
  assert(process.argv.slice(2).every(x=>x==='--check'),'Usage: node generate.mjs [--check]');
  const {files,modules}=build();writeOrCheck(files,process.argv.includes('--check'));
  console.log(JSON.stringify({mode:process.argv.includes('--check')?'CHECKED':'GENERATED',files:files.size,modules:modules.map(m=>({id:m.id,storedVertices:m.storedVertices,triangleEquivalent:m.triangleEquivalent,triangles:m.triangles,quads:m.quads}))},null,2));
}
