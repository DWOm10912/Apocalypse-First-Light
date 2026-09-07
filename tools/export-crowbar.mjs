// Export the USER-SAVED source; never author or overwrite crowbar.bbmodel.
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const source=path.join(root,'src/main/blockbench/crowbar.bbmodel');
const before=fs.readFileSync(source),b=JSON.parse(before);
if(b.meta.model_format!=='java_block')throw Error('Expected Java block model');
const base='src/main/resources/assets/apocalypse_firstlight/';
const groups=new Map((b.groups||[]).map(g=>[g.uuid,g])), membership=new Map(),bones=[];
function walk(nodes,parent){for(const n of nodes){if(typeof n==='string'){membership.set(n,parent);continue;}let g={...groups.get(n.uuid),...n};if(g.export===false)continue;if((g.rotation||[]).some(v=>v!==0))throw Error('Rotated Java group requires native Blockbench export');bones.push({name:g.name,pivot:[8-(g.origin?.[0]||0),g.origin?.[1]||0,(g.origin?.[2]||0)-8],...(parent?{parent}:{}),cubes:[]});walk(g.children,g.name);}}
walk(b.outliner);
const elements=[],tex=b.textures[0];
if(b.textures.length!==1||!tex.source?.startsWith('data:image/png;base64,'))throw Error('Expected single embedded PNG');
for(const e of b.elements){if(e.export===false)continue;if(e.type!=='cube')throw Error('Unsupported non-cube');if(!membership.has(e.uuid))throw Error('Missing group membership');
 const r=e.rotation||[0,0,0],axes=r.map((v,i)=>v?i:-1).filter(i=>i>=0);
 if(axes.length>1||axes.some(i=>![-45,-22.5,22.5,45].includes(r[i])))throw Error('Unsupported Java rotation');
 if([...e.from,...e.to].some(n=>!Number.isFinite(n)||n< -16||n>32))throw Error('Java bounds');
 const f={};for(const [name,v] of Object.entries(e.faces)){if(v.texture===null)continue;if(v.texture!==0)throw Error('Texture binding');f[name]={uv:v.uv.map((n,i)=>n*16/(i%2?b.resolution.height:b.resolution.width)),texture:'#0',...(v.rotation?{rotation:v.rotation}:{}),...(v.cullface?{cullface:v.cullface}:{})};}
 elements.push({name:e.name,from:e.from,to:e.to,...(axes.length?{rotation:{origin:e.origin,axis:'xyz'[axes[0]],angle:r[axes[0]],rescale:!!e.rescale}}:{}),shade:e.shade!==false,faces:f});
 const uv={};for(const [name,v] of Object.entries(e.faces)){if(v.texture===null)continue;if(v.rotation)throw Error('Rotated face UV requires native geo export');let q=v.uv;uv[name]={uv:[q[0],q[1]],uv_size:[q[2]-q[0],q[3]-q[1]]};}
 bones.find(g=>g.name===membership.get(e.uuid)).cubes.push({origin:[8-e.to[0],e.from[1],e.from[2]-8],size:e.to.map((v,i)=>v-e.from[i]),pivot:[8-e.origin[0],e.origin[1],e.origin[2]-8],rotation:[-r[0],-r[1],r[2]],uv});
}
const item={credit:'Original AFL crowbar; exported from saved Blockbench source',texture_size:[b.resolution.width,b.resolution.height],textures:{'0':'apocalypse_firstlight:item/crowbar',particle:'apocalypse_firstlight:item/crowbar'},elements,display:b.display,gui_light:b.front_gui_light===false?'side':'front'};
const geo={format_version:'1.12.0','minecraft:geometry':[{description:{identifier:'geometry.crowbar',texture_width:b.resolution.width,texture_height:b.resolution.height,visible_bounds_width:3,visible_bounds_height:3,visible_bounds_offset:[0,.5,0]},bones}]};
for(const [p,data] of [[base+'models/item/crowbar.json',JSON.stringify(item,null,2)+'\n'],[base+'geo/crowbar.geo.json',JSON.stringify(geo,null,2)+'\n'],[base+'textures/item/crowbar.png',Buffer.from(tex.source.split(',')[1],'base64')]]){const target=path.join(root,p);if(process.argv.includes('--check')){if(!fs.readFileSync(target).equals(Buffer.from(data)))throw Error('Stale '+p);}else fs.writeFileSync(target,data);}
if(!before.equals(fs.readFileSync(source)))throw Error('Source changed during export');
console.log('PASS: source unchanged; '+elements.length+' cubes; saved Display and embedded texture exported.');
