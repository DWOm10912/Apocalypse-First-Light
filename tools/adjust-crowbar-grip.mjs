// Targeted adjustment preserves the currently saved source, UVs, and animation tracks.
import fs from 'node:fs';
const sourcePath='src/main/blockbench/crowbar_first_person.bbmodel';
const geoPath='src/main/resources/assets/apocalypse_firstlight/geo/crowbar_first_person.geo.json';
const source=JSON.parse(fs.readFileSync(sourcePath,'utf8'));
const geo=JSON.parse(fs.readFileSync(geoPath,'utf8'));
let count=0;
for(const g of source.groups||[])if(g.name==='crowbar_model'){g.rotation=[0,-110,0];count++;}
function visit(nodes){for(const n of nodes||[])if(typeof n==='object'){
 if(n.name==='crowbar_model'){n.rotation=[0,-110,0];count++;}visit(n.children);
}}
visit(source.outliner);
if(!count)throw new Error('Missing source tool group');
const bone=geo['minecraft:geometry'][0].bones.find(b=>b.name==='crowbar_model');
if(!bone)throw new Error('Missing runtime tool bone');
bone.rotation=[0,110,0];
fs.writeFileSync(sourcePath,JSON.stringify(source,null,2)+'\n');
fs.writeFileSync(geoPath,JSON.stringify(geo,null,2)+'\n');
console.log('Tool-only axial rotation applied; hand and animation tracks preserved.');
