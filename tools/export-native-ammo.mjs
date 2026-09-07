// Deterministic, hand-authored assets; no generated artwork. Rifle geometry/UV from BR51.
import fs from 'node:fs';
import zlib from 'node:zlib';
import crypto from 'node:crypto';
const root = new URL('../', import.meta.url);
const resource='src/main/resources/assets/apocalypse_firstlight/';
const read=p=>fs.readFileSync(new URL(p,root));
const write=(p,v)=>fs.writeFileSync(new URL(p,root),typeof v==='object'&&!Buffer.isBuffer(v)?JSON.stringify(v,null,2)+'\n':v);
const uid=s=>{let h=crypto.createHash('md5').update(s).digest('hex');return `${h.slice(0,8)}-${h.slice(8,12)}-${h.slice(12,16)}-${h.slice(16,20)}-${h.slice(20)}`;};
const directions=['north','east','south','west','up','down'];
const source=JSON.parse(read('src/main/blockbench/br51_01.bbmodel'));
const group=source.groups.find(g=>g.uuid==='335a03a2-aae3-f396-93b6-9a21cf307383');
if(group?.name!=='3') throw Error('BR51 bullet source changed; inspect before extracting');
function find(ns){for(const n of ns){if(typeof n==='string')continue;if(n.uuid===group.uuid)return n;const r=find(n.children||[]);if(r)return r;}}
const originals=find(source.outliner).children.map(id=>source.elements.find(e=>e.uuid===id));
if(originals.length!==12)throw Error('Expected one complete twelve-cube cartridge');
// Undo only the magazine placement parent: cubes already stand along local Y.
const move=v=>[(v[0]-.3125)*4+8,(v[1]-8.46068)*4+1.3,(v[2]-4.13888)*4+8];
const rifle=originals.map((e,i)=>({name:['case_body','projectile_seated','shoulder_front','projectile_ogive','shoulder_back','projectile_nose','projectile_tip','projectile_point','shoulder_right','shoulder_left','case_rim','extractor_groove'][i],from:move(e.from),to:move(e.to),...(e.rotation?{rotation:{origin:move(e.origin),axis:e.rotation[0]?'x':'z',angle:e.rotation[0]||e.rotation[2],rescale:false}}:{}),faces:Object.fromEntries(Object.entries(e.faces).filter(([,f])=>f.texture!==null).map(([d,f])=>[d,{uv:f.uv.map(v=>v/8),texture:'#0'}]))}));
const rifleTexture=read(resource+'textures/item/br51_01.png');
// Discard copper projectile. Keep original case, four shoulder facets, rim and groove.
const rifleCase=rifle.filter(e=>!e.name.startsWith('projectile')).map(e=>structuredClone(e));
const face=uv=>Object.fromEntries(directions.map(d=>[d,{uv,texture:'#0'}]));
// Dark recessed opening and primer use existing dark / brass atlas pixels.
rifleCase.push({name:'recessed_mouth',from:move([.14,10.33,3.967]),to:move([.485,10.345,4.311]),faces:face([0,0,.0625,.0625])});
rifle.push({name:'primer',from:move([.22,8.451,4.046]),to:move([.405,8.461,4.231]),faces:face([10.75,5.125,10.8125,5.1875])});
rifleCase.push(structuredClone(rifle.at(-1)));
// 32px restrained metal palette, with gentle longitudinal variation and edge highlights.
const colors=['b79a49','d1b663','927738','635735','b17e51','cd9863','805736','423b2c'];
const crc=b=>{let c=0xffffffff;for(const v of b){c^=v;for(let i=0;i<8;i++)c=(c>>>1)^((c&1)?0xedb88320:0);}return(c^0xffffffff)>>>0;};
const chunk=(s,b)=>{const t=Buffer.from(s),n=Buffer.alloc(4),c=Buffer.alloc(4);n.writeUInt32BE(b.length);c.writeUInt32BE(crc(Buffer.concat([t,b])));return Buffer.concat([n,t,b,c]);};
const h=Buffer.alloc(13);h.writeUInt32BE(32);h.writeUInt32BE(32,4);h[8]=8;h[9]=2;
const rows=[];for(let y=0;y<32;y++){rows.push(0);for(let x=0;x<32;x++){const rgb=Buffer.from(colors[Math.floor(x/8)+4*Math.floor(y/16)],'hex');const delta=x%8===0?8:y%16===15?-5:0;rows.push(...[...rgb].map(v=>Math.max(0,Math.min(255,v+delta))));}}
const pistolTexture=Buffer.concat([Buffer.from('89504e470d0a1a0a','hex'),chunk('IHDR',h),chunk('IDAT',zlib.deflateSync(Buffer.from(rows))),chunk('IEND',Buffer.alloc(0))]);
const uv=i=>[i%4*4+.25,Math.floor(i/4)*8+.25,i%4*4+3.75,Math.floor(i/4)*8+7.75];
const pistolCase=[];
function box(list,name,from,to,mat,angle=0){list.push({name,from,to,...(angle?{rotation:{origin:[8,8,8],axis:'y',angle,rescale:false}}:{}),faces:face(uv(mat))});}
// Eight narrow parallel slices approximate a round cross-section without star-shaped overlaps.
function oct(list,name,y0,y1,r,mat){for(let i=0;i<8;i++){const z0=-r+i*r/4,z1=z0+r/4;const mid=(z0+z1)/2;const x=Math.sqrt(r*r-mid*mid);box(list,name+'_'+i,[8-x,y0,8+z0],[8+x,y1,8+z1],mat);}}
oct(pistolCase,'rim',2.2,2.55,1.67,1);
oct(pistolCase,'groove',2.55,2.86,1.38,3);
oct(pistolCase,'case',2.86,9.0,1.58,0);
oct(pistolCase,'lip',8.88,9.08,1.60,1);
box(pistolCase,'primer',[7.53,2.185,7.53],[8.47,2.205,8.47],2);
// Recessed dark cap instead of an opaque brass end; reads as spent mouth at small scale.
oct(pistolCase,'mouth',9.081,9.09,1.32,7);
const pistol=pistolCase.filter(e=>!e.name.startsWith('mouth')).map(e=>structuredClone(e));
oct(pistol,'copper_base',9.0,10.15,1.49,4);
oct(pistol,'copper_ogive',10.15,10.88,1.36,4);
oct(pistol,'copper_nose',10.88,11.42,1.09,5);
oct(pistol,'copper_crown',11.42,11.73,.66,5);
const display=rifle=>({gui:{rotation:[18,30,-35],scale:rifle?[1.05,1.05,1.05]:[1.2,1.2,1.2]},firstperson_righthand:{rotation:[0,-20,-15],translation:[0,1,0],scale:[.45,.45,.45]},firstperson_lefthand:{rotation:[0,20,15],translation:[0,1,0],scale:[.45,.45,.45]},thirdperson_righthand:{rotation:[0,0,-15],scale:[.25,.25,.25]},thirdperson_lefthand:{rotation:[0,0,15],scale:[.25,.25,.25]},ground:{rotation:[0,0,90],translation:[0,1,0],scale:[.22,.22,.22]},fixed:{rotation:[0,0,-30],scale:[.8,.8,.8]}});
for(const [id,elements,png,size] of [['9x19mm_round',pistol,pistolTexture,32],['9x19mm_casing',pistolCase,pistolTexture,32],['762x51mm_round',rifle,rifleTexture,256],['762x51mm_casing',rifleCase,rifleTexture,256]]){
 const tex='apocalypse_firstlight:item/'+id,dis=display(id.startsWith('762'));
 write(resource+'models/item/'+id+'.json',{credit:'AFL Native Ammunition V1; BR51 bullet extraction for 7.62',textures:{'0':tex,particle:tex},elements,display:dis,gui_light:'front'});
 write(resource+'textures/item/'+id+'.png',png);
 const cubes=elements.map((e,i)=>({...e,type:'cube',uuid:uid(id+i),origin:e.rotation?.origin||[8,8,8],rotation:e.rotation?['x','y','z'].map(a=>a===e.rotation.axis?e.rotation.angle:0):[0,0,0],rescale:false,box_uv:false,autouv:0,export:true,faces:Object.fromEntries(directions.map(d=>[d,e.faces[d]?{...e.faces[d],uv:e.faces[d].uv.map(v=>v*size/16),texture:0}:{uv:[0,0,0,0],texture:null}]))}));
 write('src/main/blockbench/'+id+'.bbmodel',{meta:{format_version:'4.10',model_format:'java_block',box_uv:false},name:id,resolution:{width:size,height:size},elements:cubes,outliner:[{name:'root',uuid:uid(id+'root'),origin:[8,8,8],children:cubes.map(e=>e.uuid),export:true}],textures:[{name:id+'.png',uuid:uid(id+'texture'),id:'0',width:size,height:size,uv_width:size,uv_height:size,source:'data:image/png;base64,'+png.toString('base64')}],display:dis});
 console.log(id,elements.length+' cubes');
}
