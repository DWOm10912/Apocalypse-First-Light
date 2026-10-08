// Repackage the checked-in V2-0 prototype for development only. No geometry regeneration.
import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const input='tools/highway-v2-0/demo',output='src/dev/highway_mesh_resources';
const metadata=JSON.parse(fs.readFileSync(path.join(root,input,'metadata.json'),'utf8'));
const sha=data=>createHash('sha256').update(data).digest('hex');
const files=new Map(),entries=[];
for(const module of metadata.modules){
  for(const name of [module.id+'.aflmesh.json',module.id+'.geo.json']) {
    const bytes=fs.readFileSync(path.join(root,input,name));
    files.set(output+'/assets/afl_highway_demo/prototype/'+name,bytes);
    entries.push({name,sha256:sha(bytes)});
  }
}
files.set(output+'/assets/afl_highway_demo/prototype/placeholder.png',fs.readFileSync(path.join(root,input,'placeholder.png')));
const descriptor={schema:1,runtimeContract:'highway-v2-m1a-1',sourceRecipe:metadata.recipe.recipeVersion,length:metadata.routeLength,width:metadata.width,
  modules:metadata.modules.map(m=>({id:m.id,origin:m.origin})),centerline:metadata.samples.map(s=>[s.s,s.x,s.y,s.z]),entries};
const descriptorJson=JSON.stringify(descriptor);
files.set(output+'/assets/afl_highway_demo/prototype/scene.json',JSON.stringify({version:sha(descriptorJson),descriptor_json:descriptorJson},null,2)+'\n');
files.set(output+'/assets/apocalypse_firstlight/blockstates/dev_highway_surface.json',JSON.stringify({variants:Object.fromEntries(Array.from({length:32},(_,i)=>['layers='+(i+1),{model:'apocalypse_firstlight:block/dev_highway_surface'}]))},null,2)+'\n');
files.set(output+'/assets/apocalypse_firstlight/models/block/dev_highway_surface.json',JSON.stringify({textures:{particle:'apocalypse_firstlight:block/asphalt'}},null,2)+'\n');
assert(process.argv.slice(2).every(x=>x==='--check'));
for(const [relative,data] of files){const file=path.join(root,relative),bytes=Buffer.from(data);if(process.argv.includes('--check'))assert(fs.existsSync(file)&&fs.readFileSync(file).equals(bytes),'stale: '+relative);else{fs.mkdirSync(path.dirname(file),{recursive:true});fs.writeFileSync(file,bytes);}}
console.log(`${process.argv.includes('--check')?'CHECKED':'PREPARED'} ${files.size} development resources; reused ${metadata.modules.length} V2-0 meshes; version=${sha(descriptorJson)}`);
