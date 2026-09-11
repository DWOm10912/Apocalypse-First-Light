import {execFileSync} from 'node:child_process';
import {readFile,writeFile,mkdir} from 'node:fs/promises';
import {randomUUID} from 'node:crypto';
const base='src/main/blockbench';
const checks='build/asset_checks/office_copier';
await mkdir(checks,{recursive:true});
const call=(name,args={})=>{
 const raw=execFileSync(process.execPath,['tools/water-dispenser-mcp.mjs',name,JSON.stringify(args)],{encoding:'utf8',maxBuffer:16000000});
 try{return JSON.parse(raw);}catch{return raw.trim();}
};
const action=process.argv[2]||'view';
if(action==='view'){
 const view=process.argv[3]||'front';
 const views={front:[[40,29,-48],[8,12,8]],rear:[[-36,27,57],[8,12,8]],top:[[30,53,-27],[8,13,8]],straight:[[8,14,-57],[8,12,8]],preview:[[96,69,-103],[18,11,6]]};
 const [position,target]=views[view];
 call('set_camera_angle',{position,target,projection:'perspective'});
 const captured=call('capture_screenshot');
 await writeFile(`${checks}/${view}.png`,await readFile(captured));
 console.log(`${checks}/${view}.png`);
}else if(action==='save'){
 const result=call('export_model',{codec_id:'project',max_content_length:2000000});
 if(result.truncated)throw Error('Truncated project');
 const model=JSON.parse(result.content);
 if(model.name!=='office_multifunction_printer')throw Error('Wrong active model');
 await writeFile(`${base}/office_multifunction_printer.bbmodel`,result.content);
 await mkdir(`${base}/textures`,{recursive:true});
 await writeFile(`${base}/textures/office_multifunction_printer.png`,Buffer.from(model.textures[0].source.split(',')[1],'base64'));
 console.log(JSON.stringify({saved:model.name,cubes:model.elements.length,groups:model.groups?.length}));
}else if(action==='preview'){
 const source=JSON.parse(await readFile(`${base}/office_multifunction_printer.bbmodel`,'utf8'));
 const preview={meta:source.meta,name:'office_copier_office_preview',resolution:{width:128,height:128},elements:[],groups:[],outliner:[],textures:[],ambientocclusion:false};
 async function add(file,label,offset){
  const m=JSON.parse(await readFile(`${base}/${file}.bbmodel`,'utf8'));
  const ids=new Map(), textureStart=preview.textures.length;
  for(const obj of [...m.elements,...(m.groups||[]),...m.textures])ids.set(obj.uuid,randomUUID());
  function mapNode(n){
   if(typeof n==='string')return ids.get(n);
   if(!ids.has(n.uuid))ids.set(n.uuid,randomUUID());
   return {...n,uuid:ids.get(n.uuid),origin:n.origin?.map((v,a)=>v+offset[a]),children:(n.children||[]).map(mapNode)};
  }
  const nodes=m.outliner.map(mapNode);
  for(const t of m.textures)preview.textures.push({...t,uuid:ids.get(t.uuid),id:String(preview.textures.length)});
  for(const e of m.elements){
   const c=structuredClone(e);c.uuid=ids.get(e.uuid);
   for(const key of ['from','to','origin'])if(c[key])c[key]=c[key].map((v,a)=>v+offset[a]);
   for(const f of Object.values(c.faces))if(f.texture!==null&&f.texture!==undefined)f.texture=ids.get(f.texture)||preview.textures[textureStart+Number(f.texture)]?.uuid;
   preview.elements.push(c);
  }
  for(const g of m.groups||[])preview.groups.push({...g,uuid:ids.get(g.uuid),origin:g.origin.map((v,a)=>v+offset[a])});
  const id=randomUUID();preview.groups.push({name:label,uuid:id,origin:offset,rotation:[0,0,0]});preview.outliner.push({uuid:id,children:nodes});
 }
 await add('modern_office_desk','preview_desk',[-30,0,0]);
 await add('modern_office_chair','preview_chair',[-22,0,-18]);
 await add('office_multifunction_printer','preview_copier',[28,0,0]);
 await add('low_filing_cabinet','preview_filing_cabinet',[47,0,0]);
 await add('office_cubicle_partition','preview_partition_1',[-30,0,21]);
 await add('office_cubicle_partition','preview_partition_2',[-14,0,21]);
 await add('office_cubicle_partition','preview_partition_3',[2,0,21]);
 const path=`${base}/previews/office_copier_office_preview.bbmodel`;
 await writeFile(path,JSON.stringify(preview));
 const code=`newProject(Formats.free);Codecs.project.parse(JSON.parse(new TextDecoder().decode(Uint8Array.from(atob('${Buffer.from(JSON.stringify(preview)).toString('base64')}'),c=>c.charCodeAt(0)))));Project.save_path='D:/Minecraft Modding/Apocalypse First Light/${path}';Canvas.updateAll();'preview loaded'`;
 const script=`${checks}/load-preview.js`;await writeFile(script,code);
 console.log(execFileSync(process.execPath,['tools/water-dispenser-mcp.mjs','eval',script],{encoding:'utf8',maxBuffer:16000000}));
}
