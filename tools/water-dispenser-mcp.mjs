import {readFile,writeFile,mkdir} from 'node:fs/promises';
const endpoint='http://localhost:3000/bb-mcp';
let session;
async function rpc(method,params,id=1){const r=await fetch(endpoint,{method:'POST',headers:{'Content-Type':'application/json',Accept:'application/json, text/event-stream',...(session?{'mcp-session-id':session}:{})},body:JSON.stringify({jsonrpc:'2.0',...(id===null?{}:{id}),method,params})});session=r.headers.get('mcp-session-id')||session;if(!r.ok)throw Error(await r.text());const s=await r.text();if(!s)return null;const v=JSON.parse(s);if(v.error)throw Error(JSON.stringify(v.error));return v.result;}
await rpc('initialize',{protocolVersion:'2024-11-05',capabilities:{},clientInfo:{name:'afl-water-dispenser-art',version:'1.0'}});
await rpc('notifications/initialized',{},null);
const [action,arg]=process.argv.slice(2);
let result;
if(action==='save-assets'){
 const decode=r=>{if(r.isError)throw Error(JSON.stringify(r));return JSON.parse(r.content.find(c=>c.type==='text').text);};
 const info=decode(await rpc('tools/call',{name:'get_project_info',arguments:{}}));
 const expected=arg==='update-nonoverlap'?160:arg==='update-hollow'?199:179;
 if(info.project.uuid!=='42df8ab4-de82-2321-38b1-b2d0c0aeb904'||info.counts.cubes!==expected)throw Error('LIVE_PROJECT_CHANGED');
 const project=decode(await rpc('tools/call',{name:'export_model',arguments:{codec_id:'project',max_content_length:2000000}}));
 const model=decode(await rpc('tools/call',{name:'export_model',arguments:{codec_id:'java_block',max_content_length:2000000}}));
 if(project.truncated||model.truncated)throw Error('TRUNCATED_EXPORT');
 const source=JSON.parse(project.content),runtime=JSON.parse(model.content);
 if(source.elements.length!==expected||runtime.elements.length!==expected||source.textures.length!==1)throw Error('EXPORT_COUNTS');
 runtime.render_type='minecraft:translucent';runtime.textures.particle='apocalypse_firstlight:block/water_dispenser';
 const texture=source.textures[0].source;if(!texture?.startsWith('data:image/png;base64,'))throw Error('MISSING_EMBEDDED_TEXTURE');
 const outputs=[['src/main/blockbench/water_dispenser.bbmodel',project.content],['src/main/resources/assets/apocalypse_firstlight/models/block/water_dispenser.json',JSON.stringify(runtime,null,2)+'\n'],['src/main/resources/assets/apocalypse_firstlight/textures/block/water_dispenser.png',Buffer.from(texture.split(',')[1],'base64')]];
 if(arg==='update-hollow'||arg==='update-nonoverlap'){
 const previous=JSON.parse(await readFile(outputs[0][0],'utf8'));const previousExpected=arg==='update-nonoverlap'?199:179;if(previous.elements.length!==previousExpected)throw Error('Expected '+previousExpected+' cube export');
  if(arg==='update-hollow')for(const old of previous.elements.filter(c=>!/^bottle_\d+_profile_\d+$/.test(c.name)&&c.name!=='top_moulded_flat')){const next=source.elements.find(c=>c.uuid===old.uuid);if(!next||JSON.stringify(old.from)!==JSON.stringify(next.from)||JSON.stringify(old.to)!==JSON.stringify(next.to)||JSON.stringify(old.faces)!==JSON.stringify(next.faces))throw Error('NON_BOTTLE_GEOMETRY_CHANGED: '+old.name);}
  if(arg==='update-nonoverlap'){
   const removed=/^shell_|^thin_|^(inverted_neck|neck_front_lip|neck_side_lip)$/;
   for(const old of previous.elements.filter(c=>!removed.test(c.name))){const next=source.elements.find(c=>c.uuid===old.uuid);if(!next||JSON.stringify(old.from)!==JSON.stringify(next.from)||JSON.stringify(old.to)!==JSON.stringify(next.to)||JSON.stringify(old.faces)!==JSON.stringify(next.faces))throw Error('NON_BOTTLE_GEOMETRY_CHANGED: '+old.name);}
   const allowedNew=/^(wall_[0-4]_(north|south|west|east)|cap_(bottom|top)_sealed|neck_(lower|collar)_(north|south|west|east))$/;
   for(const next of source.elements.filter(c=>!previous.elements.some(old=>old.uuid===c.uuid)))if(!allowedNew.test(next.name))throw Error('UNEXPECTED_NEW_GEOMETRY: '+next.name);
  }
  const backup=arg==='update-nonoverlap'?'build/asset_checks/water_dispenser/pre_nonoverlap':'build/asset_checks/water_dispenser/pre_hollow';await mkdir(backup,{recursive:true});for(const [p]of outputs)await writeFile(backup+'/'+p.split('/').at(-1),await readFile(p),{flag:'wx'});
 }else for(const [p]of outputs){try{await readFile(p);throw Error('OUTPUT_ALREADY_EXISTS: '+p);}catch(e){if(e.code!=='ENOENT')throw e;}}
 for(const [p,data]of outputs){await mkdir(p.slice(0,p.lastIndexOf('/')),{recursive:true});await writeFile(p,data,{flag:arg==='update-hollow'||arg==='update-nonoverlap'?'w':'wx'});console.log(p);}
 process.exit(0);
}
else if(action==='eval'){result=await rpc('tools/call',{name:'risky_eval',arguments:{code:await readFile(arg,'utf8')}});}
else if(action==='capture'){result=await rpc('tools/call',{name:'capture_screenshot',arguments:{}});}
else result=await rpc('tools/call',{name:action,arguments:arg?JSON.parse(arg):{}});
if(result.isError)throw Error(JSON.stringify(result));
for(const item of result.content??[]){if(item.type==='image'){await mkdir('build/asset_checks/water_dispenser',{recursive:true});const label=action==='capture'?(arg||'preview'):action;const p=`build/asset_checks/water_dispenser/${label}.png`;await writeFile(p,Buffer.from(item.data,'base64'));console.log(p);}else if(item.type==='text')console.log(item.text);}
