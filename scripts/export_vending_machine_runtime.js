// Static cube export: preserve Blockbench XYZ, translate X/Z by 8, UV pixels to 16-unit UV.
const fs=require('fs'),path=require('path');
const root=path.resolve(__dirname,'..'),assets=path.join(root,'src/main/resources/assets/apocalypse_firstlight');
const read=p=>JSON.parse(fs.readFileSync(p,'utf8'));
const write=(p,v)=>{fs.mkdirSync(path.dirname(p),{recursive:true});fs.writeFileSync(p,JSON.stringify(v,null,2)+'\n');};
const merge=(p,entries)=>{const old=fs.readFileSync(p,'utf8'),data=JSON.parse(old);const added=Object.entries(entries).filter(([k,v])=>JSON.stringify(data[k])!==JSON.stringify(v));if(!added.length)return;if(added.some(([k])=>k in data))throw Error('Review existing entry before replacing');fs.writeFileSync(p,old.replace(/\s*}\s*$/,',\n'+added.map(([k,v])=>'  '+JSON.stringify(k)+': '+JSON.stringify(v)).join(',\n')+'\n}\n'));};
const models=['intact','broken'].map(v=>read(path.join(root,`src/main/blockbench/afl_vending_machine_${v}.bbmodel`)));
function elements(model,glass) {
 const glassIds=new Set();
 function visit(nodes,inside=false) {for(const n of nodes) {if(typeof n==='string') {if(inside)glassIds.add(n);} else {const name=n.name||model.groups?.find(g=>g.uuid===n.uuid)?.name;visit(n.children||[],inside||name==='glass_panel'||name==='broken_glass_parts');}}}
 visit(model.outliner);
 return model.elements.filter(e=>e.type==='cube' && glassIds.has(e.uuid)===glass).sort((a,b)=>a.name.localeCompare(b.name)).map(e=>{
  if(e.rotation?.some(v=>v))throw Error('Rotated source cube requires explicit conversion');
  const result={from:e.from.map((v,i)=>v+(i===1?0:8)),to:e.to.map((v,i)=>v+(i===1?0:8)),faces:{}};
  for(const [face,f] of Object.entries(e.faces))if(f.texture!==null)result.faces[face]={uv:f.uv.map(v=>v/8),texture:'#all',...(f.rotation?{rotation:f.rotation}:{})};
  return result;
 });
}
const body=elements(models[0],false);
if(JSON.stringify(body)!==JSON.stringify(elements(models[1],false)))throw Error('Variants have different bodies');
for(const [part,els] of [['body',body],['intact_glass',elements(models[0],true)],['broken_glass',elements(models[1],true)]])
 write(path.join(assets,`models/block/vending_machine_${part}.json`),{ambientocclusion:false,textures:{all:'apocalypse_firstlight:entity/vending_machine',particle:'minecraft:block/gray_concrete'},elements:els});
write(path.join(assets,'models/block/vending_machine.json'),{textures:{particle:'minecraft:block/gray_concrete'},elements:[]});
write(path.join(assets,'blockstates/vending_machine.json'),{variants:{'':{model:'apocalypse_firstlight:block/vending_machine'}}});
write(path.join(assets,'models/item/vending_machine.json'),{parent:'builtin/entity',textures:{particle:'minecraft:block/gray_concrete'},display:{
 gui:{rotation:[20,35,0],translation:[0,-5,0],scale:[.4,.4,.4]},ground:{translation:[0,2,0],scale:[.25,.25,.25]},
 fixed:{rotation:[0,180,0],scale:[.4,.4,.4]},thirdperson_righthand:{rotation:[75,45,0],scale:[.25,.25,.25]},firstperson_righthand:{rotation:[0,45,0],scale:[.3,.3,.3]}}});
fs.mkdirSync(path.join(assets,'textures/entity'),{recursive:true});
fs.copyFileSync(path.join(root,'src/main/blockbench/textures/afl_vending_machine.png'),path.join(assets,'textures/entity/vending_machine.png'));
const atlasPath=path.join(root,'src/main/resources/assets/minecraft/atlases/blocks.json'),atlas=read(atlasPath);
if(!atlas.sources.some(source=>source.resource==='apocalypse_firstlight:entity/vending_machine')) {
 atlas.sources.push({type:'minecraft:single',resource:'apocalypse_firstlight:entity/vending_machine'});write(atlasPath,atlas);
}
fs.copyFileSync('E:/Download/vending_machine_break.ogg',path.join(assets,'sounds/vending_machine_break.ogg'));
for(const lang of ['en_us','zh_cn']) {
 const p=path.join(assets,`lang/${lang}.json`);
 merge(p,{'block.apocalypse_firstlight.vending_machine':lang==='zh_cn'?'自动售货机':'Vending Machine',
 'hint.apocalypse_firstlight.break_glass':lang==='zh_cn'?'破坏玻璃':'Break glass'});
}
merge(path.join(assets,'sounds.json'),{vending_machine_break:{sounds:['apocalypse_firstlight:vending_machine_break']}});
for(const tag of ['mineable/pickaxe','needs_diamond_tool']) {
 const p=path.join(root,`src/main/resources/data/minecraft/tags/blocks/${tag}.json`),data=read(p);
 if(!data.values.includes('apocalypse_firstlight:vending_machine'))data.values.push('apocalypse_firstlight:vending_machine');write(p,data);
}
// Machine item is dropped once by playerWillDestroy, independent of which half is mined.
write(path.join(root,'src/main/resources/data/apocalypse_firstlight/loot_tables/blocks/vending_machine.json'),{type:'minecraft:block',pools:[]});
console.log('Exported matching bodies, two glass states, texture, sound, item, tags and localization.');
