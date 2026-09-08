import fs from 'node:fs';
import assert from 'node:assert/strict';
// Explicit, idempotent source/runtime migration requested for the clear V2 work surface.
const sourcePath='src/main/blockbench/gun_maintenance_bench.bbmodel';
const base='src/main/resources/assets/apocalypse_firstlight/';
const modelPath=base+'models/block/gun_maintenance_bench.json';
const geoPath=base+'geo/gun_maintenance_bench.geo.json';
const read=p=>JSON.parse(fs.readFileSync(p,'utf8'));
const source=read(sourcePath),model=read(modelPath),geo=read(geoPath);
const names=new Set(['mat_small_component','mat_dark_part']);
const removed=source.elements.filter(e=>names.has(e.name));
assert(removed.length===0||removed.length===2,'Expected exactly the two approved decorations');
if(removed.length){
  const ids=new Set(removed.map(e=>e.uuid));
  source.elements=source.elements.filter(e=>!ids.has(e.uuid));
  const clean=nodes=>nodes.filter(n=>typeof n!=='string'||!ids.has(n)).map(n=>typeof n==='object'?{...n,children:clean(n.children??[])}:n);
  source.outliner=clean(source.outliner);
  const indexMap=new Map();let next=0;
  model.elements.forEach((e,i)=>{if(!names.has(e.name))indexMap.set(i,next++);});
  model.elements=model.elements.filter(e=>!names.has(e.name));
  const groups=nodes=>nodes.filter(n=>typeof n!=='number'||indexMap.has(n)).map(n=>typeof n==='number'?indexMap.get(n):{...n,children:groups(n.children??[])});
  model.groups=groups(model.groups??[]);
  let geoRemoved=0;
  for(const bone of geo['minecraft:geometry'][0].bones)if(bone.cubes)bone.cubes=bone.cubes.filter(c=>{
    const match=removed.some(e=>c.origin.every((v,i)=>Math.abs(v-(i===0?-e.to[0]:e.from[i]))<1e-5)&&c.size.every((v,i)=>Math.abs(v-(e.to[i]-e.from[i]))<1e-5));
    if(match)geoRemoved++;return !match;
  });
  assert.equal(geoRemoved,2);
  for(const [p,j] of [[sourcePath,source],[modelPath,model],[geoPath,geo]])fs.writeFileSync(p,JSON.stringify(j,null,p===sourcePath?0:2)+'\n');
}
console.log('MAT_DECORATIONS_REMOVED',removed.length,'REMAINING_CUBES',source.elements.length);
// Preserve Blockbench's compact arrays/face objects instead of expanding every UV into many lines.
let formatted=JSON.stringify(model,null,'\t').replace(/\[\s*([-\d.eE+,\s]+)\s*\]/g,(_,numbers)=>'['+numbers.trim().split(',').map(n=>n.trim()).join(', ')+']');
formatted=formatted.replace(/"(rotation|north|south|east|west|up|down)": \{([^{}]+)\}/g,(_,key,body)=>'"'+key+'": {'+body.trim().replace(/\s*\n\s*/g,' ')+'}');
fs.writeFileSync(modelPath,formatted+'\n');
