import {mkdir,writeFile,access,readFile} from 'node:fs/promises';
let session;
async function rpc(method,params,id=1){
 const r=await fetch('http://localhost:3000/bb-mcp',{method:'POST',headers:{'Content-Type':'application/json',Accept:'application/json, text/event-stream',...(session?{'mcp-session-id':session}:{})},body:JSON.stringify({jsonrpc:'2.0',...(id===null?{}:{id}),method,params})});
 session=r.headers.get('mcp-session-id')||session;
 if(!r.ok)throw Error(await r.text());const s=await r.text();if(!s)return null;
 const v=JSON.parse(s);if(v.error)throw Error(JSON.stringify(v.error));return v.result;
}
async function call(name,args={}){const r=await rpc('tools/call',{name,arguments:args});if(r.isError)throw Error(JSON.stringify(r));return JSON.parse(r.content.find(c=>c.type==='text').text);}
await rpc('initialize',{protocolVersion:'2024-11-05',capabilities:{},clientInfo:{name:'afl-city-trash-can-export',version:'1.0'}});
await rpc('notifications/initialized',{},null);
const info=await call('get_project_info');
if(info.project.uuid!=='b8e40069-0a06-8a5d-41df-7d35617d1f0a'||info.counts.cubes!==204)throw Error('Unexpected live project');
const exported=await call('export_model',{codec_id:'project',max_content_length:2000000});
if(exported.truncated)throw Error('Truncated export');
const source=JSON.parse(exported.content);
if(source.elements.length!==204||source.textures.length!==1)throw Error('Invalid asset counts');
const texture=source.textures[0];
if(!texture.source?.startsWith('data:image/png;base64,'))throw Error('Missing PNG');
for(const e of source.elements)for(const f of Object.values(e.faces))if(f.texture!==0&&f.texture!==texture.uuid)throw Error('Unbound face '+e.name);
const png=Buffer.from(texture.source.split(',')[1],'base64');
if(png.readUInt32BE(16)!==128||png.readUInt32BE(20)!==128)throw Error('Texture dimensions');
const files=[['src/main/blockbench/city_trash_can.bbmodel',exported.content],['src/main/resources/assets/apocalypse_firstlight/textures/block/city_trash_can.png',png]];
const update=process.argv.includes('--update-rim');
if(update){
 const previous=JSON.parse(await readFile(files[0][0],'utf8'));
 if(previous.elements.length!==204)throw Error('Unexpected saved model');
 for(const old of previous.elements.filter(e=>!e.name.startsWith('lid_skirt_'))){const next=source.elements.find(e=>e.uuid===old.uuid);if(!next||JSON.stringify(old)!==JSON.stringify(next))throw Error('Unrelated element changed: '+old.name);}
 const backup='build/asset_checks/city_trash_can/pre_align_'+Date.now();await mkdir(backup,{recursive:true});
 for(const [p]of files)await writeFile(backup+'/'+p.split('/').at(-1),await readFile(p),{flag:'wx'});
}else for(const [p] of files){try{await access(p);throw Error('Already exists: '+p);}catch(e){if(e.code!=='ENOENT')throw e;}}
for(const [p,data]of files){await mkdir(p.slice(0,p.lastIndexOf('/')),{recursive:true});await writeFile(p,data,{flag:update?'w':'wx'});console.log(p);}
console.log('Verified: 204 cubes; 128x128 PNG; all face texture bindings.');
