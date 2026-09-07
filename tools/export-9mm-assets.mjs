// Original AFL cube-only ammunition assets. No external artwork or geometry.
import fs from 'node:fs';
import zlib from 'node:zlib';
import crypto from 'node:crypto';
const root = new URL('../', import.meta.url);
const colors = ['ad894a','806132','c2a367','74503b','976a49','b18258','484039','746e5e'];
const crc = b => { let c=0xffffffff; for(const v of b){c^=v;for(let i=0;i<8;i++)c=(c>>>1)^((c&1)?0xedb88320:0);}return (c^0xffffffff)>>>0; };
const chunk=(s,b)=>{const t=Buffer.from(s),n=Buffer.alloc(4),c=Buffer.alloc(4);n.writeUInt32BE(b.length);c.writeUInt32BE(crc(Buffer.concat([t,b])));return Buffer.concat([n,t,b,c]);};
const h=Buffer.alloc(13);h.writeUInt32BE(16);h.writeUInt32BE(16,4);h[8]=8;h[9]=2;
const rows=[];for(let y=0;y<16;y++){rows.push(0);for(let x=0;x<16;x++){const hex=colors[Math.floor(x/4)+4*Math.floor(y/8)];rows.push(...Buffer.from(hex,'hex'));}}
const png=Buffer.concat([Buffer.from('89504e470d0a1a0a','hex'),chunk('IHDR',h),chunk('IDAT',zlib.deflateSync(Buffer.from(rows))),chunk('IEND',Buffer.alloc(0))]);
const texture='apocalypse_firstlight:item/9mm_palette';
const display={gui:{rotation:[25,0,-35],translation:[0,0,0],scale:[1.15,1.15,1.15]},firstperson_righthand:{rotation:[0,-25,-15],translation:[0,1,0],scale:[.65,.65,.65]},firstperson_lefthand:{rotation:[0,25,15],translation:[0,1,0],scale:[.65,.65,.65]},thirdperson_righthand:{rotation:[0,0,-10],translation:[0,1,0],scale:[.4,.4,.4]},thirdperson_lefthand:{rotation:[0,0,10],translation:[0,1,0],scale:[.4,.4,.4]},ground:{rotation:[0,0,90],translation:[0,0,0],scale:[.5,.5,.5]},fixed:{rotation:[0,0,-30],translation:[0,0,0],scale:[.8,.8,.8]}};
const uv=i=>[i%4*4+.5,Math.floor(i/4)*8+.5,i%4*4+3.5,Math.floor(i/4)*8+7.5];
const casing=[];
const box=(list,name,from,to,mat,group)=>list.push({name,from,to,mat,group});
// Y is longitudinal. Cross-shaped terraces soften the square silhouette.
box(casing,'base',[-1.6,0,-1.6],[1.6,.35,1.6],1,'rim');
box(casing,'rim_x',[-2,.35,-1.5],[2,.8,1.5],0,'rim');
box(casing,'rim_z',[-1.5,.35,-2],[1.5,.8,2],0,'rim');
box(casing,'extractor_groove',[-1.65,.8,-1.65],[1.65,1.15,1.65],1,'rim');
// Four walls leave a genuine open cavity; corner shoulders form stepped outline.
box(casing,'wall_front',[-1.5,1.15,-2],[1.5,8,-1.5],0,'casing');
box(casing,'wall_back',[-1.5,1.15,1.5],[1.5,8,2],0,'casing');
box(casing,'wall_left',[-2,1.15,-1.5],[-1.5,8,1.5],0,'casing');
box(casing,'wall_right',[1.5,1.15,-1.5],[2,8,1.5],0,'casing');
box(casing,'cavity_floor',[-1.5,1.15,-1.5],[1.5,1.4,1.5],6,'mouth');
box(casing,'primer',[-.55,-.015,-.55],[.55,.015,.55],7,'primer');
const round=structuredClone(casing);
box(round,'fmj_seated',[-1.49,7.5,-1.49],[1.49,9.9,1.49],4,'bullet');
box(round,'fmj_shoulder',[-1.25,9.9,-1.25],[1.25,10.85,1.25],4,'bullet');
box(round,'fmj_nose',[-.9,10.85,-.9],[.9,11.55,.9],5,'bullet');
box(round,'fmj_tip',[-.5,11.55,-.5],[.5,11.985,.5],5,'bullet');
const generated=[];
for(const [name,parts] of [['9mm_round',round],['9mm_casing',casing]]){
 const length=name==='9mm_round'?12:8.015;
 const shift=v=>[v[0]+8,v[1]+(16-length)/2+.015,v[2]+8];
 const elements=parts.map((p,i)=>({name:p.name,from:shift(p.from),to:shift(p.to),faces:Object.fromEntries(['north','south','east','west','up','down'].map(f=>[f,{uv:uv(p.mat),texture:'#0'}]))}));
 const runtime={credit:'Original AFL 9mm asset V1',texture_size:[16,16],textures:{'0':texture,particle:texture},elements,display,gui_light:'front'};
 const id=s=>{const h=crypto.createHash('md5').update(name+s).digest('hex');return `${h.slice(0,8)}-${h.slice(8,12)}-${h.slice(12,16)}-${h.slice(16,20)}-${h.slice(20)}`;};
 const bb={meta:{format_version:'4.10',model_format:'java_block',box_uv:false},name,model_identifier:name,visible_box:[1,1,0],resolution:{width:16,height:16},elements:elements.map((e,i)=>({...e,type:'cube',uuid:id('cube'+i),origin:[8,8,8],rotation:[0,0,0],rescale:false,locked:false,box_uv:false,autouv:0,color:parts[i].mat,faces:Object.fromEntries(Object.entries(e.faces).map(([f,v])=>[f,{...v,texture:0}]))})),outliner:[{name:'root',origin:[8,8,8],rotation:[0,0,0],uuid:id('root'),export:true,isOpen:true,children:[...new Set(parts.map(p=>p.group))].map(g=>({name:g,origin:[8,8,8],rotation:[0,0,0],uuid:id(g),export:true,isOpen:true,children:parts.flatMap((p,i)=>p.group===g?[id('cube'+i)]:[])}))}],textures:[{path:'',name:'9mm_palette.png',folder:'item',namespace:'apocalypse_firstlight',id:'0',uuid:id('texture'),width:16,height:16,uv_width:16,uv_height:16,source:'data:image/png;base64,'+png.toString('base64')}],display};
 generated.push([`src/main/blockbench/${name}.bbmodel`,JSON.stringify(bb,null,2)+'\n'],[`src/main/resources/assets/apocalypse_firstlight/models/item/${name}.json`,JSON.stringify(runtime,null,2)+'\n']);
 const min=[0,1,2].map(a=>Math.min(...parts.map(p=>p.from[a]))),max=[0,1,2].map(a=>Math.max(...parts.map(p=>p.to[a])));
 console.log(name, {cubes:parts.length,size:max.map((v,i)=>v-min[i]),contexts:Object.keys(display).length});
}
generated.push(['src/main/resources/assets/apocalypse_firstlight/textures/item/9mm_palette.png',png]);
for(const [path,data] of generated){const target=new URL(path,root);if(process.argv.includes('--check')){if(!fs.existsSync(target)||!fs.readFileSync(target).equals(Buffer.from(data)))throw Error('Stale asset: '+path);}else fs.writeFileSync(target,data);}
