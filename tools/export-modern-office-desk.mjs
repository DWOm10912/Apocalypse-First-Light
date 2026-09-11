import {execFileSync} from 'node:child_process';
import {writeFile,mkdir,readFile} from 'node:fs/promises';
const raw=execFileSync(process.execPath,['tools/water-dispenser-mcp.mjs','export_model',JSON.stringify({codec_id:'project',max_content_length:2000000})],{encoding:'utf8',maxBuffer:4000000});
const result=JSON.parse(raw);if(result.truncated)throw Error('Truncated');
const model=JSON.parse(result.content);if(model.name!=='modern_office_desk'||model.elements.length!==145||model.textures.length!==1)throw Error('Wrong asset');
const tex=model.textures[0];
for(const e of model.elements){if(e.from.some((v,i)=>v>=e.to[i]))throw Error('Degenerate '+e.name);for(const f of Object.values(e.faces)){if(f.texture!==0&&f.texture!==tex.uuid)throw Error('Binding');if(f.uv.some(v=>v<0||v>128))throw Error('UV range');}}
const lo=[0,1,2].map(i=>Math.min(...model.elements.map(e=>e.from[i]))),hi=[0,1,2].map(i=>Math.max(...model.elements.map(e=>e.to[i])));
if(JSON.stringify(hi.map((v,i)=>v-lo[i]))!=='[48,12,16]')throw Error('Dimensions');
const png=Buffer.from(tex.source.split(',')[1],'base64');if(png.readUInt32BE(16)!==128||png.readUInt32BE(20)!==128)throw Error('PNG');
for(const [p,data]of [['src/main/blockbench/modern_office_desk.bbmodel',result.content],['src/main/resources/assets/apocalypse_firstlight/textures/block/modern_office_desk.png',png]]){await mkdir(p.slice(0,p.lastIndexOf('/')),{recursive:true});await writeFile(p,data,{flag:'wx'});if(!(await readFile(p)).equals(Buffer.from(data)))throw Error('Save mismatch');console.log(p);}
console.log(JSON.stringify({cubes:model.elements.length,groups:model.groups?.length,bounds:[lo,hi],uv:'PASS'}));
