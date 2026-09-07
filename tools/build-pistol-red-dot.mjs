// Original AFL micro optic. Editable source is authoritative after --create.
// A small hand-authored material atlas follows P9's established dark-metal palette.
import fs from 'node:fs';import zlib from 'node:zlib';import crypto from 'node:crypto';import assert from 'node:assert/strict';
const source='src/main/blockbench/pistol_red_dot.bbmodel',assets='src/main/resources/assets/apocalypse_firstlight';
const id=n=>{let h=crypto.createHash('md5').update('afl-micro-optic-v1/'+n).digest('hex');return `${h.slice(0,8)}-${h.slice(8,12)}-${h.slice(12,16)}-${h.slice(16,20)}-${h.slice(20)}`};
const crc=b=>{let c=0xffffffff;for(const x of b){c^=x;for(let j=0;j<8;j++)c=c>>>1^((c&1)?0xedb88320:0)}return(c^0xffffffff)>>>0};
function chunk(n,b){const name=Buffer.from(n),len=Buffer.alloc(4),tail=Buffer.alloc(4);len.writeUInt32BE(b.length);tail.writeUInt32BE(crc(Buffer.concat([name,b])));return Buffer.concat([len,name,b,tail])}
if(process.argv.includes('--create')){
 assert(!fs.existsSync(source),'Source exists; edit it in Blockbench and use --export');
 const raw=Buffer.alloc(32*(32*4+1)),colors=[[47,50,53],[65,69,73],[84,89,94],[26,29,31],[111,117,120],[53,56,60],[244,39,27],[0,0,0]];
 for(let y=0;y<32;y++)for(let x=0;x<32;x++){
  const strip=x>>2,k=strip===6?0:(x%4===0?6:x%4===3?-4:0)+((y*3+x)%3-1);
  raw.set([...colors[strip].map(v=>Math.max(0,Math.min(255,v+k))),strip===7?0:255],y*129+1+x*4);
 }
 const head=Buffer.alloc(13);head.writeUInt32BE(32);head.writeUInt32BE(32,4);head[8]=8;head[9]=6;
 const png=Buffer.concat([Buffer.from('89504e470d0a1a0a','hex'),chunk('IHDR',head),chunk('IDAT',zlib.deflateSync(raw)),chunk('IEND',Buffer.alloc(0))]);
 const groups=['sight_root','mount_base','sight_body','window_frame','illuminated'].map(name=>({name,uuid:id(name),origin:[0,0,0],rotation:[0,0,0],export:true,visibility:true}));
 const children=new Map(groups.map(g=>[g.name,[]])),elements=[];
 function box(name,group,from,to,strip=0,rotation=[0,0,0]){
  const faces=Object.fromEntries(['north','east','south','west','up','down'].map((n,i)=>[n,{uv:[strip*4+.5,2+i*4,strip*4+3.5,5+i*4],texture:0}]));
  const c={name,uuid:id(name),type:'cube',from,to,origin:from.map((v,i)=>(v+to[i])/2),rotation,inflate:0,autouv:0,box_uv:false,export:true,visibility:true,faces};
  elements.push(c);children.get(group).push(c.uuid);
 }
 box('adapter_plate','mount_base',[-1.06,0,-1.2],[1.06,.14,1.2],1);
 box('plate_lower_bevel','mount_base',[-.98,-.012,-1.12],[.98,.035,1.12],3);
 box('body_housing','sight_body',[-.99,.14,-1.1],[.99,.37,.76],0);
 box('emitter_housing','sight_body',[-.32,.37,-.99],[.32,.53,-.53],3);
 box('emitter_cap','sight_body',[-.29,.51,-.96],[.29,.56,-.56],1);
 box('frame_sill','window_frame',[-1.03,.30,.55],[1.03,.47,.96],1);
 for(const sign of [-1,1]){
  box('window_upright_'+sign,'window_frame',[sign<0?-1.03:.84,.44,.56],[sign<0?-.84:1.03,1.46,.92],0);
  box('corner_'+sign,'window_frame',[sign<0?-.99:.69,1.4,.56],[sign<0?-.69:.99,1.68,.92],1,[0,0,sign*22.5]);
  box('side_facet_'+sign,'sight_body',[sign<0?-1.01:.94,.2,-.95],[sign<0?-.94:1.01,.36,.35],2);
  box('mount_screw_'+sign,'mount_base',[sign*.62-.11,.371,-.42],[sign*.62+.11,.396,-.2],4);
  box('screw_slot_'+sign,'mount_base',[sign*.62-.07,.397,-.33],[sign*.62+.07,.403,-.29],3);
 }
 box('window_crown','window_frame',[-.75,1.55,.56],[.75,1.72,.92],1);
 box('crown_edge','window_frame',[-.67,1.705,.575],[.67,1.74,.88],2);
 box('adjuster_recess','sight_body',[1.012,.2,-.35],[1.04,.32,-.02],3);
 box('adjuster','sight_body',[1.041,.235,-.3],[1.054,.285,-.07],4);
 // Open center: no glass polygon, no alpha sorting; independent tiny reticle.
 box('reticle_dot','illuminated',[-.028, .972,.938],[.028,1.028,.942],6);
 const display={gui:{rotation:[20,140,0],translation:[0,-2,0],scale:[5.6,5.6,5.6]},ground:{translation:[0,2,0],scale:[.8,.8,.8]},fixed:{rotation:[0,180,0],scale:[5,5,5]},firstperson_righthand:{rotation:[0,-35,0],translation:[0,1,0],scale:[.8,.8,.8]},thirdperson_righthand:{rotation:[0,0,0],translation:[0,1,0],scale:[.8,.8,.8]}};
 const s={meta:{format_version:'4.12',model_format:'geckolib_model',box_uv:false},name:'pistol_red_dot',model_identifier:'pistol_red_dot',resolution:{width:32,height:32},elements,groups,display,
 outliner:[{uuid:id('sight_root'),children:groups.slice(1).map(g=>({uuid:g.uuid,children:children.get(g.name)}))}],textures:[{uuid:id('texture'),name:'pistol_red_dot.png',id:'0',width:32,height:32,uv_width:32,uv_height:32,source:'data:image/png;base64,'+png.toString('base64')}],animations:[]};
 fs.writeFileSync(source,JSON.stringify(s,null,2)+'\n');
}
assert(process.argv.includes('--export')||process.argv.includes('--create')||process.argv.includes('--check'),'Use --create, --export or --check');
const s=JSON.parse(fs.readFileSync(source)),out=new Map(),by=new Map(s.groups.map(g=>[g.uuid,g])),parents=new Map(),owners=new Map();
function walk(ns,parent){for(const n of ns)if(typeof n==='string')owners.set(n,parent);else{parents.set(n.uuid,parent);walk(n.children,n.uuid)}}walk(s.outliner,null);
const faces=['north','south','east','west','up','down'],texture='apocalypse_firstlight:item/pistol_red_dot';
function block(c){const e={from:c.from.map(v=>v+8),to:c.to.map(v=>v+8),faces:Object.fromEntries(faces.map(f=>[f,{uv:c.faces[f].uv.map(v=>v/2),texture:'#0'}]))};const axis=c.rotation.findIndex(v=>v!==0);if(axis>=0)e.rotation={origin:c.origin.map(v=>v+8),axis:'xyz'[axis],angle:c.rotation[axis]};return e}
const model=es=>({ambientocclusion:false,textures:{'0':texture,particle:texture},elements:es.map(block)});
const dots=s.elements.filter(c=>by.get(owners.get(c.uuid)).name==='illuminated'),body=s.elements.filter(c=>!dots.includes(c));
out.set(`${assets}/models/item/pistol_red_dot.json`,{...model(s.elements),display:s.display});
out.set(`${assets}/models/item/pistol_red_dot_body.json`,model(body));out.set(`${assets}/models/item/pistol_red_dot_reticle.json`,model(dots));
out.set(`${assets}/geo/pistol_red_dot.geo.json`,{format_version:'1.12.0','minecraft:geometry':[{description:{identifier:'geometry.pistol_red_dot',texture_width:32,texture_height:32,visible_bounds_width:1,visible_bounds_height:1,visible_bounds_offset:[0,0,0]},bones:s.groups.map(g=>({name:g.name,pivot:[-g.origin[0],g.origin[1],g.origin[2]],...(parents.get(g.uuid)?{parent:by.get(parents.get(g.uuid)).name}:{}),cubes:s.elements.filter(c=>owners.get(c.uuid)===g.uuid).map(c=>({origin:[-c.to[0],c.from[1],c.from[2]],size:c.to.map((v,i)=>v-c.from[i]),pivot:[-c.origin[0],c.origin[1],c.origin[2]],rotation:[-c.rotation[0],-c.rotation[1],c.rotation[2]],uv:Object.fromEntries(faces.map(f=>[f,{uv:c.faces[f].uv.slice(0,2),uv_size:[c.faces[f].uv[2]-c.faces[f].uv[0],c.faces[f].uv[3]-c.faces[f].uv[1]]}]))}))}))}]});
const png=Buffer.from(s.textures[0].source.split(',')[1],'base64');
for(const [p,v]of out)process.argv.includes('--check')?assert.deepEqual(JSON.parse(fs.readFileSync(p)),JSON.parse(JSON.stringify(v))):fs.writeFileSync(p,JSON.stringify(v,null,2)+'\n');
if(process.argv.includes('--check'))assert(fs.readFileSync(`${assets}/textures/item/pistol_red_dot.png`).equals(png));else fs.writeFileSync(`${assets}/textures/item/pistol_red_dot.png`,png);
console.log({source,cubes:s.elements.length,texture:'32x32',window:'open',reticle:'.056 model units',bounds:{width:2.12,length:2.4,height:1.752},mode:process.argv.includes('--check')?'verified':'exported'});
