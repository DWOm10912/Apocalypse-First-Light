import fs from 'node:fs';
const path='src/main/blockbench/';
const source=JSON.parse(fs.readFileSync(path+'restroom_partition.bbmodel'));
const office=JSON.parse(fs.readFileSync(path+'office_cubicle_partition.bbmodel'));
const geometry=e=>JSON.stringify([e.from,e.to,e.origin,e.rotation,e.faces]);
for(let i=0;i<42;i++)if(geometry(source.elements[i])!==geometry(office.elements[i]))throw Error('Geometry/UV drift');
if(JSON.stringify(source.groups.map(g=>g.origin))!==JSON.stringify(office.groups.map(g=>g.origin)))throw Error('Pivot drift');
const preview=JSON.parse(fs.readFileSync(path+'previews/office_cubicle_partition_connection_preview.bbmodel'));
for(const scenario of ['preview_straight_2','preview_corner_L','preview_U_cubicle']){
 const group=preview.groups.find(g=>g.name===scenario);
 const node=structuredClone(preview.outliner[0].children.find(n=>n.uuid===group.uuid));
 const ids=new Set(),gids=new Set();
 function trim(n){gids.add(n.uuid);n.children=n.children.filter(c=>{
  if(typeof c==='string'){const e=preview.elements.find(e=>e.uuid===c);if(e.faces.north.texture!==0)return false;ids.add(c);return true;}
  trim(c);return c.children.length>0;
 });}
 trim(node);
 gids.clear();function collect(n){gids.add(n.uuid);for(const c of n.children)if(typeof c==='object')collect(c);}collect(node);
 const m=structuredClone(source);m.name='restroom_'+scenario;
 m.elements=structuredClone(preview.elements.filter(e=>ids.has(e.uuid)));
 m.groups=structuredClone(preview.groups.filter(g=>gids.has(g.uuid)));
 for(const e of m.elements){e.name=e.name.replaceAll('fabric','laminate');for(const f of Object.values(e.faces))f.texture=0;}
 m.outliner=[node];m.textures[0].relative_path='../textures/restroom_partition.png';
 fs.writeFileSync(path+'previews/'+m.name+'.bbmodel',JSON.stringify(m,null,2)+'\n');
 console.log(m.name,m.elements.length);
}
console.log('PASS: exact 42-cube geometry, UV, origins retained from office source.');
