// One-shot, scoped mechanical name migration. Binary media bytes are never edited.
import fs from 'node:fs';
import path from 'node:path';
const root=process.cwd();
if(!fs.existsSync('src/main/resources/assets/apocalypse_firstlight/models/item/service_pistol.json'))throw Error('One-shot migration already applied; do not rewrite legacy compatibility mappings');
const convert=s=>s.replaceAll('SERVICE_PISTOL','P9_01').replaceAll('ServicePistol','P901').replaceAll('servicePistol','p901').replaceAll('service_pistol','p9_01').replaceAll('service-pistol','p9-01').replaceAll('M14Combat','BR5101Combat').replaceAll('M14','BR51_01').replaceAll('m14','br51_01').replaceAll('Service Pistol','P9-01 Service Pistol');
const walk=d=>fs.readdirSync(d,{withFileTypes:true}).flatMap(e=>e.isDirectory()?walk(path.join(d,e.name)):[path.join(d,e.name)]);
const files=['src','tools','docs'].flatMap(walk).filter(p=>!p.endsWith('rename-native-models.mjs'));
const textExt=new Set(['.java','.json','.bbmodel','.md','.mjs','.js','.ps1','.gradle','.toml','.properties']);
const entries=[];
function jsonValue(x){if(typeof x==='string')return x.startsWith('data:')?x:convert(x);if(Array.isArray(x))return x.map(jsonValue);if(x&&typeof x==='object')return Object.fromEntries(Object.entries(x).map(([k,v])=>[convert(k),jsonValue(v)]));return x;}
for(const p of files){
 const dest=convert(p);if(dest!==p&&fs.existsSync(dest))throw Error('Collision '+dest);
 const bytes=fs.readFileSync(p);let result=bytes;
 if(textExt.has(path.extname(p))){const old=bytes.toString('utf8');let next;
   if(path.extname(p)==='.bbmodel')next=JSON.stringify(jsonValue(JSON.parse(old)),null,2);
   else next=convert(old);
   if(next!==old)result=Buffer.from(next);
 }
 if(dest!==p||!result.equals(bytes))entries.push({p,dest,result});
}
for(const e of entries){const abs=path.resolve(e.dest);if(!abs.startsWith(root+path.sep))throw Error('Out of workspace');fs.mkdirSync(path.dirname(e.dest),{recursive:true});fs.writeFileSync(e.dest,e.result);if(e.dest!==e.p)fs.unlinkSync(e.p);}
fs.mkdirSync('build',{recursive:true});fs.writeFileSync('build/native-rename-manifest.json',JSON.stringify(entries.map(({p,dest})=>({from:p,to:dest})),null,2));
console.log('Updated/renamed',entries.length,'files; manifest build/native-rename-manifest.json');
