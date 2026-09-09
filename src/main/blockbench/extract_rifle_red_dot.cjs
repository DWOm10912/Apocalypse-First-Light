// Deterministic extraction of approved BR51 geometry, not a redesign or texture repaint.
const fs=require('fs'),path=require('path'),assert=require('assert');
const root=path.resolve(__dirname,'../../..'),base='src/main/resources/assets/apocalypse_firstlight/';
const read=p=>JSON.parse(fs.readFileSync(path.join(root,p),'utf8'));
const write=(p,v)=>fs.writeFileSync(path.join(root,p),JSON.stringify(v,null,2)+'\n');
const src=read('src/main/blockbench/br51_01.bbmodel');
const find=(ns,id)=>{for(const n of ns){if(n.uuid===id)return n;const v=n.children&&find(n.children,id);if(v)return v;}};
const optic=src.groups.find(g=>g.name==='sight'),dot=src.groups.find(g=>g.name==='bone15_illuminated');
const seat=[0,12.75,5.54688],shift=v=>v.map((x,i)=>+(x-seat[i]).toFixed(8));
const extract=g=>find(src.outliner,g.uuid).children.filter(c=>typeof c==='string').map(id=>{
 const e=structuredClone(src.elements.find(e=>e.uuid===id));e.from=shift(e.from);e.to=shift(e.to);e.origin=shift(e.origin||[0,0,0]);e.export=true;e.visibility=true;return e;
});
const body=extract(optic),reticle=extract(dot);assert.equal(body.length,28);assert.equal(reticle.length,1);
for(const e of [...body,...reticle])for(const f of Object.values(e.faces))assert(f.texture===0||f.texture===null);
const groups=[{...structuredClone(optic),name:'rifle_red_dot_root',origin:[0,0,0],export:true,visibility:true},
 {...structuredClone(dot),name:'reticle',origin:shift(dot.origin),export:true,visibility:true}];
const tex=structuredClone(src.textures[0]);tex.name='rifle_red_dot_01.png';delete tex.path;delete tex.relative_path;
write('src/main/blockbench/rifle_red_dot_01.bbmodel',{
 meta:{...src.meta},name:'rifle_red_dot_01',resolution:src.resolution,
 elements:[...body,...reticle],groups,textures:[tex],
 outliner:[{uuid:groups[0].uuid,children:[...body.map(e=>e.uuid),{uuid:groups[1].uuid,children:reticle.map(e=>e.uuid)}]}]
});
const flip=v=>[-v[0],v[1],v[2]];
const cube=e=>{
 const c={origin:[-e.to[0],e.from[1],e.from[2]],size:e.to.map((v,i)=>+(v-e.from[i]).toFixed(8)),uv:{}};
 if(e.inflate)c.inflate=e.inflate;
 if(e.rotation?.some(v=>v)){c.pivot=flip(e.origin);c.rotation=[-e.rotation[0],-e.rotation[1],e.rotation[2]];}
 for(const [d,f]of Object.entries(e.faces))if(f.texture!==null){const u=f.uv;c.uv[d]={uv:u.slice(0,2),uv_size:[u[2]-u[0],u[3]-u[1]]};}
 return c;
};
write(base+'geo/rifle_red_dot_01.geo.json',{'format_version':'1.12.0','minecraft:geometry':[{description:{identifier:'geometry.rifle_red_dot_01',texture_width:src.resolution.width,texture_height:src.resolution.height,visible_bounds_width:2,visible_bounds_height:2},bones:[
 {name:'rifle_red_dot_root',pivot:[0,0,0],cubes:body.map(cube)},
 {name:'reticle',parent:'rifle_red_dot_root',pivot:flip(groups[1].origin),cubes:reticle.map(cube)}]}]});
fs.copyFileSync(path.join(root,base+'textures/item/br51_01.png'),path.join(root,base+'textures/item/rifle_red_dot_01.png'));
console.log(JSON.stringify({body:body.length,reticle:reticle.length,seat,aim:dot.origin,sourceTexture:tex.uuid,resolution:src.resolution}));
