// Original AFL Crowbar V1. Authored cuboids + deterministic hand-designed pixel atlas.
// No external geometry or texture input. Run --check to verify committed exports.
import fs from 'node:fs';
import zlib from 'node:zlib';
import crypto from 'node:crypto';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const asset='src/main/resources/assets/apocalypse_firstlight/';
const uuid=s=>{let h=crypto.createHash('md5').update('afl-original-crowbar-v1/'+s).digest('hex');return `${h.slice(0,8)}-${h.slice(8,12)}-${h.slice(12,16)}-${h.slice(16,20)}-${h.slice(20)}`;};
const crc=b=>{let c=0xffffffff;for(let v of b){c^=v;for(let i=0;i<8;i++)c=(c>>>1)^((c&1)?0xedb88320:0);}return (c^0xffffffff)>>>0;};
const chunk=(s,b)=>{let t=Buffer.from(s),n=Buffer.alloc(4),c=Buffer.alloc(4);n.writeUInt32BE(b.length);c.writeUInt32BE(crc(Buffer.concat([t,b])));return Buffer.concat([n,t,b,c]);};
function png(w,h,pixels){let head=Buffer.alloc(13);head.writeUInt32BE(w);head.writeUInt32BE(h,4);head[8]=8;head[9]=6;let raw=Buffer.alloc(h*(w*4+1));for(let y=0;y<h;y++)Buffer.from(pixels.slice(y*w*4,(y+1)*w*4)).copy(raw,y*(w*4+1)+1);return Buffer.concat([Buffer.from('89504e470d0a1a0a','hex'),chunk('IHDR',head),chunk('IDAT',zlib.deflateSync(raw)),chunk('IEND',Buffer.alloc(0))]);}
// Eight 8px-wide material strips. Long red faces have continuous restrained wear.
const bases=[[111,35,30],[133,44,35],[83,27,25],[121,41,32],[66,68,66],[92,94,89],[124,127,119],[48,51,51]];
const pixels=new Uint8Array(64*64*4);
for(let y=0;y<64;y++)for(let x=0;x<64;x++){
 let strip=x>>3,u=x%8,base=bases[strip],v=((x*13+y*7)%7)-3;
 let edge=u===0?8:u===7?-8:0;
 let color=base.map(c=>c+v+edge);
 if(strip<4&&((u===1&&(y===12||y===13||y===43))||(u===6&&y>=53&&y<=55)))color=[101,97,85];
 if(strip>=4&&((u===1&&y%17<5)||(u===5&&y%23===4)))color=color.map(c=>c+16);
 const i=(y*64+x)*4; pixels.set([...color.map(c=>Math.max(0,Math.min(255,c))),255],i);
}
const texturePng=png(64,64,pixels),parts=[];
const faces=['north','south','east','west','up','down'];
function box(name,group,center,size,angle=0,axis='z',material='red'){
 parts.push({name,group,center,size,angle,axis,material});
}
// Long central shaft, subtly stepped shoulders, no ornamental collars or grip sleeve.
// Crossed prisms and a 45-degree core fill a chamfered, near-octagonal section.
box('shaft_core','shaft_main',[0,8.75,0],[.70,23.1,.92]);
box('shaft_side_facets','shaft_main',[0,8.75,0],[.92,23.1,.70]);
box('shaft_chamfers','shaft_main',[0,8.75,0],[.66,23.1,.66],45,'y');
function segment(name,group,a,b,width,depth,material){
 let dx=b[0]-a[0],dy=b[1]-a[1],angle=-Math.atan2(dx,dy)*180/Math.PI;
 // A horizontal segment is expressed as a native unrotated X cuboid.
 if(Math.abs(dy)<1e-6){box(name,group,[(a[0]+b[0])/2,a[1],0],[Math.abs(dx)+.11,width,depth],0,'z',material);return;}
 if(dy<0){[a,b]=[b,a];dx=-dx;dy=-dy;angle=-Math.atan2(dx,dy)*180/Math.PI;}
 box(name,group,[(a[0]+b[0])/2,(a[1]+b[1])/2,0],[width,Math.hypot(dx,dy)+.10,depth],Math.round(angle*1000)/1000,'z',material);
}
const s=Math.SQRT1_2,t=Math.tan(Math.PI/8);
// Forged hook: progressively bent shoulders, real inside clearance, split chisel tip.
let a=[0,20.2],b=[-.42,20.2+.42/t];
segment('painted_shoulder','hook_head',a,b,.90,.88,'red');
a=b;b=[a[0]-.70,a[1]+.70];segment('paint_to_steel_transition','hook_head',a,b,.88,.86,'red');
a=b;b=[a[0]-1.10,a[1]];segment('forged_crown','hook_head',a,b,.86,.84,'steel');
a=b;b=[a[0]-.62,a[1]-.62];segment('hook_return','hook_head',a,b,.81,.81,'steel');
a=b;b=[a[0]-.66,a[1]-.66/t];segment('hook_neck','hook_head',a,b,.68,.79,'steel');
// Twin tapered prongs retain a visible nail slot, not a pipe-like end cap.
for(let sign of [-1,1]){
 box('hook_prong_'+sign,'hook_head',[b[0]-.12,b[1]-.29,sign*.255],[.53,.80,.29],-22.5,'z','edge');
 box('hook_chisel_'+sign,'hook_head',[b[0]-.25,b[1]-.61,sign*.255],[.29,.35,.29],-22.5,'z','edge');
}
// Opposite slight bend, widening to a flattened steel pry blade.
box('lower_paint_transition','bottom_pry_head',[.10,-3.13,0],[.88,.95,.88],22.5,'z','red');
box('forged_lower_shoulder','bottom_pry_head',[.38,-3.78,0],[.79,.85,1.02],22.5,'z','steel');
box('flattened_wedge','bottom_pry_head',[.62,-4.32,0],[.57,.65,1.24],22.5,'z','steel');
for(let sign of [-1,1])box('lower_split_edge_'+sign,'bottom_pry_head',[.80,-4.76,sign*.37],[.28,.46,.58],22.5,'z','edge');
const uvFor=(p,f)=>{
 let n=p.material==='red'?({north:1,south:0,east:2,west:3,up:1,down:2}[f]):p.material==='edge'?6:({north:5,south:4,east:7,west:5,up:6,down:4}[f]);
 return [n*8+.25,.25,n*8+7.75,Math.min(63.75,Math.max(8,p.size[1]*2.7))];
};
const shift=v=>[v[0]+9,v[1]-.8,v[2]+8];
const display={
 gui:{rotation:[12,-28,-33],translation:[0,0,0],scale:[.53,.53,.53]},
 firstperson_righthand:{rotation:[0,-18,-12],translation:[1.25,1.5,-.5],scale:[.75,.75,.75]},
 firstperson_lefthand:{rotation:[0,18,12],translation:[1.25,1.5,-.5],scale:[.75,.75,.75]},
 thirdperson_righthand:{rotation:[0,0,-8],translation:[0,2,0],scale:[.70,.70,.70]},
 thirdperson_lefthand:{rotation:[0,0,8],translation:[0,2,0],scale:[.70,.70,.70]},
 ground:{rotation:[0,0,90],translation:[0,-2,0],scale:[.52,.52,.52]},
 fixed:{rotation:[0,-90,-30],translation:[0,0,0],scale:[.55,.55,.55]}
};
const elements=parts.map(p=>{
 let center=shift(p.center);
 return {name:p.name,from:center.map((v,i)=>v-p.size[i]/2),to:center.map((v,i)=>v+p.size[i]/2),...(p.angle?{rotation:{origin:center,axis:p.axis,angle:p.angle,rescale:false}}:{}),faces:Object.fromEntries(faces.map(f=>[f,{uv:uvFor(p,f).map(n=>n/4),texture:'#0'}]))};
});
const bb={meta:{format_version:'4.10',model_format:'java_block',box_uv:false},name:'crowbar',model_identifier:'crowbar',resolution:{width:64,height:64},elements:elements.map((e,i)=>({...e,uuid:uuid(e.name),type:'cube',origin:shift(parts[i].center),rotation:[0,0,0].map((_,a)=>'xyz'[a]===parts[i].axis?parts[i].angle:0),rescale:false,box_uv:false,autouv:0,faces:Object.fromEntries(faces.map(f=>[f,{uv:uvFor(parts[i],f),texture:0}]))})),outliner:['shaft_main','hook_head','bottom_pry_head'].map(g=>({name:g,origin:[9,8,8],rotation:[0,0,0],uuid:uuid(g),export:true,isOpen:true,children:parts.filter(p=>p.group===g).map(p=>uuid(p.name))})),textures:[{path:'',name:'crowbar.png',folder:'item',namespace:'apocalypse_firstlight',id:'0',uuid:uuid('texture'),width:64,height:64,uv_width:64,uv_height:64,source:'data:image/png;base64,'+texturePng.toString('base64')}],display};
// Bedrock coordinates reflect Java X around the item center; rotations X/Y change sign.
const geo={format_version:'1.12.0','minecraft:geometry':[{description:{identifier:'geometry.crowbar',texture_width:64,texture_height:64,visible_bounds_width:3,visible_bounds_height:3,visible_bounds_offset:[0,.5,0]},bones:bb.outliner.map(g=>({name:g.name,pivot:[0,0,0],cubes:parts.filter(p=>p.group===g.name).map(p=>{
 let e=elements[parts.indexOf(p)],uv={};
 for(let f of faces){let source=f==='east'?'west':f==='west'?'east':f;let q=uvFor(p,source);uv[f]={uv:[q[0],q[1]],uv_size:[q[2]-q[0],q[3]-q[1]]};}
 return {origin:[8-e.to[0],e.from[1],e.from[2]-8],size:p.size,pivot:[8-shift(p.center)[0],shift(p.center)[1],shift(p.center)[2]-8],rotation:[0,0,0].map((_,a)=>'xyz'[a]===p.axis?p.angle*(a<2?-1:1):0),uv};
 })}))}]};
const item={credit:'Original AFL Crowbar V1; no external asset input',texture_size:[64,64],textures:{'0':'apocalypse_firstlight:item/crowbar',particle:'apocalypse_firstlight:item/crowbar'},elements,display,gui_light:'front'};
const outputs=[['src/main/blockbench/crowbar.bbmodel',JSON.stringify(bb,null,2)+'\n'],[asset+'geo/crowbar.geo.json',JSON.stringify(geo,null,2)+'\n'],[asset+'models/item/crowbar.json',JSON.stringify(item,null,2)+'\n'],[asset+'textures/item/crowbar.png',texturePng]];
for(let e of elements){if(e.rotation&&![-45,-22.5,0,22.5,45].includes(e.rotation.angle))throw Error('Unsupported Java element angle');if([...e.from,...e.to].some(n=>n< -16||n>32))throw Error('Java bounds');}
for(let [p,data] of outputs){let target=path.join(root,p);if(process.argv.includes('--check')){if(!fs.existsSync(target)||!fs.readFileSync(target).equals(Buffer.from(data)))throw Error('Stale export '+p);}else{fs.mkdirSync(path.dirname(target),{recursive:true});fs.writeFileSync(target,data);}}
console.log(`Crowbar: ${parts.length} cubes; 3 groups; 64x64 embedded/external atlas; 7 display contexts; Java rotation/bounds valid.`);
export {parts,pixels,png,display};
