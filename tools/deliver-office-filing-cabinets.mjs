import {execFileSync} from 'node:child_process';
import {readFile,mkdir,writeFile} from 'node:fs/promises';
const call=(name,args={})=>JSON.parse(execFileSync(process.execPath,['tools/water-dispenser-mcp.mjs',name,JSON.stringify(args)],{encoding:'utf8',maxBuffer:12000000}));
const models={};
for(const name of ['low_filing_cabinet','tall_filing_cabinet']){
 call('risky_eval',{code:`ModelProject.all.find(p=>p.name==='${name}').select(); 'selected'`});
 const result=call('export_model',{codec_id:'project',max_content_length:2000000});
 if(result.truncated)throw Error('truncated');
 const m=JSON.parse(result.content);models[name]=m;
 if(m.name!==name)throw Error('wrong project');
 for(const e of m.elements)for(const f of Object.values(e.faces))if(f.texture!==0&&f.texture!==m.textures[0].uuid)throw Error('texture binding');
 await writeFile(`src/main/blockbench/${name}.bbmodel`,result.content,{flag:'wx'});
}
const texture=Buffer.from(models.low_filing_cabinet.textures[0].source.split(',')[1],'base64');
if(!texture.equals(Buffer.from(models.tall_filing_cabinet.textures[0].source.split(',')[1],'base64')))throw Error('textures differ');
await mkdir('src/main/blockbench/textures',{recursive:true});
await writeFile('src/main/blockbench/textures/office_filing_cabinet.png',texture,{flag:'wx'});
const desk=JSON.parse(await readFile('src/main/blockbench/modern_office_desk.bbmodel','utf8'));
const preview={meta:models.low_filing_cabinet.meta,name:'office_filing_cabinet_preview',resolution:{width:128,height:128},elements:[],groups:[],outliner:[],textures:[],ambientocclusion:false};
let serial=0;
function add(m,label,offset){
 const copy=structuredClone(m),map=new Map();
 for(const e of [...copy.elements,...copy.groups,...copy.textures])map.set(e.uuid,`${label}_${e.uuid}`);
 const start=preview.textures.length;
 copy.textures.forEach(t=>{t.uuid=map.get(t.uuid);t.id=String(preview.textures.length);preview.textures.push(t);});
 for(const e of copy.elements){e.uuid=map.get(e.uuid);e.name=label+'_'+e.name;for(let a=0;a<3;a++){e.from[a]+=offset[a];e.to[a]+=offset[a];if(e.origin)e.origin[a]+=offset[a];}for(const f of Object.values(e.faces))f.texture=map.get(f.texture)??preview.textures[start+Number(f.texture)]?.uuid;preview.elements.push(e);}
 for(const g of copy.groups){g.uuid=map.get(g.uuid);g.name=label+'_'+g.name;g.origin=g.origin.map((v,a)=>v+offset[a]);preview.groups.push(g);}
 function node(n){if(typeof n==='string')return map.get(n);const g=copy.groups.find(g=>g.uuid===map.get(n.uuid));return {...g,children:(n.children??[]).map(node)};}
 preview.outliner.push(...m.outliner.map(node));serial++;
}
add(desk,'desk',[0,0,0]);
add(models.low_filing_cabinet,'desk_side_low',[36,0,0]);
add(models.tall_filing_cabinet,'tall_left',[60,0,0]);
add(models.tall_filing_cabinet,'tall_right',[77,0,0]);
add(models.low_filing_cabinet,'comparison_low',[100,0,0]);
add(models.tall_filing_cabinet,'comparison_tall',[117,0,0]);
await writeFile('src/main/blockbench/previews/office_filing_cabinet_preview.bbmodel',JSON.stringify(preview),{flag:'wx'});
const encoded=Buffer.from(JSON.stringify(preview)).toString('base64');
await writeFile('build/asset_checks/filing-preview-load.js',`newProject(Formats.java_block);Codecs.project.parse(JSON.parse(new TextDecoder().decode(Uint8Array.from(atob('${encoded}'),c=>c.charCodeAt(0)))));Project.save_path='D:/Minecraft Modding/Apocalypse First Light/src/main/blockbench/previews/office_filing_cabinet_preview.bbmodel';Canvas.updateAll();'preview loaded'`);
execFileSync(process.execPath,['tools/water-dispenser-mcp.mjs','eval','build/asset_checks/filing-preview-load.js'],{encoding:'utf8',maxBuffer:12000000});
console.log(JSON.stringify({low:models.low_filing_cabinet.elements.length,tall:models.tall_filing_cabinet.elements.length,preview:preview.elements.length}));
