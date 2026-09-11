import {readFile,writeFile,access,mkdir} from 'node:fs/promises';
let session;
async function rpc(method,params,id=1){const r=await fetch('http://localhost:3000/bb-mcp',{method:'POST',headers:{'Content-Type':'application/json',Accept:'application/json, text/event-stream',...(session?{'mcp-session-id':session}:{})},body:JSON.stringify({jsonrpc:'2.0',...(id===null?{}:{id}),method,params})});session=r.headers.get('mcp-session-id')||session;if(!r.ok)throw Error(await r.text());const text=await r.text();if(!text)return;const value=JSON.parse(text);if(value.error)throw Error(JSON.stringify(value.error));return value.result;}
async function call(name,args={}){const result=await rpc('tools/call',{name,arguments:args});if(result.isError)throw Error(JSON.stringify(result));return JSON.parse(result.content.find(c=>c.type==='text').text);}
await rpc('initialize',{protocolVersion:'2024-11-05',capabilities:{},clientInfo:{name:'afl-dumpster-save',version:'1.0'}});await rpc('notifications/initialized',{},null);
const info=await call('get_project_info');if(info.project.uuid!=='9fbb94a0-6491-3958-f87b-1b168ea7e0d3'||info.counts.cubes!==260)throw Error('Unexpected live asset');
const result=await call('export_model',{codec_id:'project',max_content_length:2000000});if(result.truncated)throw Error('Truncated source');
const source=JSON.parse(result.content);if(source.elements.length!==260||source.textures.length!==1)throw Error('Counts');
const texture=source.textures[0];
for(const element of source.elements)for(const face of Object.values(element.faces))if(face.texture!==0&&face.texture!==texture.uuid)throw Error('Texture binding');
const groups=source.groups??[];for(const side of ['left','right']){const g=groups.find(g=>g.name===side+'_lid_assembly');if(!g||g.rotation?.some(v=>v!==0)||g.origin[1]!==21||g.origin[2]!==15)throw Error('Lid must be closed with rear hinge pivot');}
const png=Buffer.from(texture.source.split(',')[1],'base64');if(png.readUInt32BE(16)!==128||png.readUInt32BE(20)!==128)throw Error('PNG dimensions');
const outputs=[['src/main/blockbench/commercial_dumpster.bbmodel',result.content],['src/main/resources/assets/apocalypse_firstlight/textures/block/commercial_dumpster.png',png]];
for(const [p]of outputs){try{await access(p);throw Error('Existing asset: '+p);}catch(e){if(e.code!=='ENOENT')throw e;}}
for(const [p,data]of outputs){await mkdir(p.slice(0,p.lastIndexOf('/')),{recursive:true});await writeFile(p,data,{flag:'wx'});const saved=await readFile(p);if(!saved.equals(Buffer.from(data)))throw Error('Save mismatch');console.log(p);}
console.log('Verified: 260 cubes, '+groups.length+' groups, 128x128 PNG, face bindings, closed rear-pivot lids.');
