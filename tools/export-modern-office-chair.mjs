import {execFileSync} from 'node:child_process';
import {writeFile,mkdir,readFile} from 'node:fs/promises';
const raw=execFileSync(process.execPath,['tools/water-dispenser-mcp.mjs','export_model',JSON.stringify({codec_id:'project',max_content_length:2000000})],{encoding:'utf8',maxBuffer:4000000});
const result=JSON.parse(raw);if(result.truncated)throw Error('Truncated');const model=JSON.parse(result.content);
if(model.name!=='modern_office_chair'||model.elements.length!==202||model.textures.length!==1)throw Error('Wrong asset');
const tex=model.textures[0],points=[];
for(const e of model.elements){if(e.from.some((v,i)=>v>=e.to[i]))throw Error('Degenerate '+e.name);for(const f of Object.values(e.faces)){if(f.texture!==0&&f.texture!==tex.uuid)throw Error('Binding');if(f.uv.some(v=>v<0||v>128))throw Error('UV');}for(let k=0;k<8;k++){let p=e.from.map((v,i)=>(k>>i&1)?e.to[i]:v),o=e.origin??[0,0,0],a=(e.rotation?.[1]??0)*Math.PI/180;let x=p[0]-o[0],z=p[2]-o[2];points.push([o[0]+x*Math.cos(a)+z*Math.sin(a),p[1],o[2]-x*Math.sin(a)+z*Math.cos(a)]);}}
const bounds=[0,1,2].map(i=>[Math.min(...points.map(p=>p[i])),Math.max(...points.map(p=>p[i]))]);
for(let i=1;i<=5;i++)if(!model.groups.some(g=>g.name==='wheel_'+String(i).padStart(2,'0')))throw Error('Missing caster');
const png=Buffer.from(tex.source.split(',')[1],'base64');if(png.readUInt32BE(16)!==128||png.readUInt32BE(20)!==128)throw Error('PNG');
for(const [p,data]of [['src/main/blockbench/modern_office_chair.bbmodel',result.content],['src/main/resources/assets/apocalypse_firstlight/textures/block/modern_office_chair.png',png]]){await mkdir(p.slice(0,p.lastIndexOf('/')),{recursive:true});await writeFile(p,data,{flag:'wx'});if(!(await readFile(p)).equals(Buffer.from(data)))throw Error('Save mismatch');console.log(p);}
console.log(JSON.stringify({cubes:model.elements.length,groups:model.groups.length,bounds,uv:'PASS'}));
