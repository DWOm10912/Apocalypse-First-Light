// Asset extraction only: original Mag2 geometry/UV, including its existing baseplate.
const fs=require('fs'),path=require('path'),assert=require('assert');
const root=path.resolve(__dirname,'../../..'),asset='src/main/resources/assets/apocalypse_firstlight/';
const src=JSON.parse(fs.readFileSync(path.join(root,'src/main/blockbench/br51_01.bbmodel'),'utf8'));
const find=(ns,id)=>{for(const n of ns){if(n.uuid===id)return n;const v=n.children&&find(n.children,id);if(v)return v;}};
const mag=src.groups.find(g=>g.name==='mag_extended_2'),seat=mag.origin;
const shift=v=>v.map((x,i)=>+(x-seat[i]).toFixed(8)),flip=v=>[-v[0],v[1],v[2]];
const groups=[],elements=[],bones=[];
const cube=e=>{let c={origin:[-e.to[0],e.from[1],e.from[2]],size:e.to.map((v,i)=>+(v-e.from[i]).toFixed(8)),uv:{}};
 if(e.inflate)c.inflate=e.inflate;
 if(e.rotation?.some(Boolean)){c.pivot=flip(e.origin);c.rotation=[-e.rotation[0],-e.rotation[1],e.rotation[2]];}
 for(const[d,f]of Object.entries(e.faces))if(f.texture!==null){assert.equal(f.texture,0);c.uv[d]={uv:f.uv.slice(0,2),uv_size:[f.uv[2]-f.uv[0],f.uv[3]-f.uv[1]]};}return c;};
function walk(n,parent){let g=structuredClone(src.groups.find(g=>g.uuid===n.uuid));
 g.name=parent?'baseplate':'br51_extended_magazine_35_root';g.origin=shift(g.origin);g.export=g.visibility=true;groups.push(g);
 const b={name:g.name,pivot:flip(g.origin),cubes:[]};if(parent)b.parent=parent;
 if(g.rotation?.some(Boolean))b.rotation=[-g.rotation[0],-g.rotation[1],g.rotation[2]];bones.push(b);
 const children=n.children.map(c=>{if(typeof c!=='string')return walk(c,g.name);
 let e=structuredClone(src.elements.find(e=>e.uuid===c));e.from=shift(e.from);e.to=shift(e.to);e.origin=shift(e.origin||[0,0,0]);e.export=e.visibility=true;elements.push(e);b.cubes.push(cube(e));return c;});
 return {uuid:g.uuid,children};}
const outliner=[walk(find(src.outliner,mag.uuid))];assert.equal(elements.length,24);
const texture=structuredClone(src.textures[0]);texture.name='br51_extended_magazine_35.png';delete texture.path;delete texture.relative_path;
const write=(p,o)=>fs.writeFileSync(path.join(root,p),JSON.stringify(o,null,2)+'\n');
write('src/main/blockbench/br51_extended_magazine_35.bbmodel',{meta:src.meta,name:'br51_extended_magazine_35',resolution:src.resolution,groups,elements,outliner,textures:[texture]});
write(asset+'geo/br51_extended_magazine_35.geo.json',{'format_version':'1.12.0','minecraft:geometry':[{description:{identifier:'geometry.br51_extended_magazine_35',texture_width:src.resolution.width,texture_height:src.resolution.height,visible_bounds_width:2,visible_bounds_height:2},bones}]});
fs.copyFileSync(path.join(root,asset+'textures/item/br51_01.png'),path.join(root,asset+'textures/item/br51_extended_magazine_35.png'));
console.log(JSON.stringify({source:mag.name,cubes:elements.length,seat,bones:bones.map(b=>b.name)}));
