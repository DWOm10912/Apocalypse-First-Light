import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const sourcePath=path.join(root,'src/main/blockbench/office_multifunction_printer.bbmodel');
const assetRoot=path.join(root,'src/main/resources/assets/apocalypse_firstlight');
const checkOnly=process.argv.includes('--check');
const model=JSON.parse(fs.readFileSync(sourcePath,'utf8'));
const clean=n=>Math.round(n*1e8)/1e8;
const near=(a,b)=>Math.abs(a-b)<1e-7;
const faceAxes={north:[2,0,0,-1,1,-1],south:[2,1,0,1,1,-1],west:[0,0,2,1,1,-1],east:[0,1,2,-1,1,-1],up:[1,1,0,1,2,1],down:[1,0,0,1,2,-1]};

assert.equal(model.name,'office_multifunction_printer');
assert.equal(model.elements.length,296,'approved printer cube count changed');
assert.deepEqual(model.resolution,{width:128,height:128});
assert.equal(model.textures.length,1);
for(const required of ['scanner_lid','control_panel_assembly','output_tray','paper_drawer_1','paper_drawer_2']){
 const group=model.groups.find(group=>group.name===required);
 assert(group,`missing required group ${required}`);
 assert(Array.isArray(group.origin)&&group.origin.length===3,`missing pivot for ${required}`);
}
for(const element of model.elements)for(const face of Object.values(element.faces??{}))
 assert(face.texture===null||face.texture===0||face.texture===model.textures[0].uuid,`bad texture binding: ${element.name}`);

let normalizedRotations=0;
function runtimeElement(element,yOffset=0){
 const faces={};
 for(const [name,face] of Object.entries(element.faces??{}))if(face.texture!==null)faces[name]={
  uv:face.uv.map((v,index)=>clean(v*16/(index%2?model.resolution.height:model.resolution.width))),
  texture:'#0',...(face.rotation?{rotation:face.rotation}:{})
 };
 const rotation=element.rotation??[0,0,0];
 const axes=rotation.flatMap((value,index)=>Math.abs(value)>1e-7?[index]:[]);
 let rotationJson;
 if(axes.length){
  assert.equal(axes.length,1,`unsupported multi-axis rotation: ${element.name}`);
  const angle=rotation[axes[0]];
  if([-45,-22.5,22.5,45].includes(angle))rotationJson={
   origin:element.origin.map((value,index)=>clean(value-(index===1?yOffset:0))),
   axis:'xyz'[axes[0]],angle,rescale:Boolean(element.rescale)
  };
  else{
   assert.equal(angle,8,`unsupported runtime angle ${angle}: ${element.name}`);
   normalizedRotations++;
  }
 }
 return {name:element.name,from:element.from.map((value,index)=>clean(value-(index===1?yOffset:0))),to:element.to.map((value,index)=>clean(value-(index===1?yOffset:0))),...(rotationJson?{rotation:rotationJson}:{}),shade:element.shade!==false,faces};
}

function clipUv(faceName,face,whole,low,high){
 const [normalAxis,positive,uAxis,uSign,vAxis,vSign]=faceAxes[faceName];
 if(!near(positive?high[normalAxis]:low[normalAxis],positive?whole.to[normalAxis]:whole.from[normalAxis]))return null;
 const interval=(axis,sign)=>{
  const size=whole.to[axis]-whole.from[axis];
  if(near(size,0))return[0,1];
  return sign>0?[(low[axis]-whole.from[axis])/size,(high[axis]-whole.from[axis])/size]:[(whole.to[axis]-high[axis])/size,(whole.to[axis]-low[axis])/size];
 };
 const [u0,u1]=interval(uAxis,uSign),[v0,v1]=interval(vAxis,vSign),[a,b,c,d]=face.uv;
 return {...face,uv:[a+(c-a)*u0,b+(d-b)*v0,a+(c-a)*u1,b+(d-b)*v1].map(clean)};
}

function fragment(whole,part,minY,maxY,offset){
 const low=[whole.from[0],Math.max(whole.from[1],minY),whole.from[2]];
 const high=[whole.to[0],Math.min(whole.to[1],maxY),whole.to[2]];
 if(high[1]-low[1]<=1e-8)return null;
 if((whole.rotation??[]).some(value=>Math.abs(value)>1e-7)){
  assert(whole.from[1]>=minY&&whole.to[1]<=maxY,`rotated element crosses split: ${whole.name}`);
  return runtimeElement(whole,offset);
 }
 const copy=structuredClone(whole);
 copy.name=`${whole.name}_${part}`;
 copy.from=low;
 copy.to=high;
 copy.faces={};
 for(const [faceName,face] of Object.entries(whole.faces??{})){
  const clipped=clipUv(faceName,face,whole,low,high);
  if(clipped)copy.faces[faceName]=clipped;
 }
 return runtimeElement(copy,offset);
}

const exported=model.elements.filter(element=>element.export!==false);
const lower=[],upper=[];
for(const element of exported){
 const low=fragment(element,'lower',-64,16,0);
 const high=fragment(element,'upper',16,64,16);
 if(low)lower.push(low);
 if(high)upper.push(high);
}
const itemElements=exported.map(element=>runtimeElement(element));
for(const [label,elements] of [['lower',lower],['upper',upper]])for(const element of elements){
 assert(element.from[1]>=-1e-7&&element.to[1]<=16.0000001,`${label} runtime element escapes block: ${element.name}`);
}

const textures={'0':'apocalypse_firstlight:block/office_multifunction_printer',particle:'apocalypse_firstlight:block/office_multifunction_printer'};
const blockModel=elements=>({credit:'Apocalypse: First Light — office multifunction printer',parent:'minecraft:block/block',ambientocclusion:false,texture_size:[128,128],textures,elements});
const blockstate={variants:Object.fromEntries([['north',0],['east',90],['south',180],['west',270]].flatMap(([facing,y])=>['lower','upper'].map(half=>[`facing=${facing},half=${half}`,{model:`apocalypse_firstlight:block/office_multifunction_printer_${half}`,...(y?{y}:{})}])))};
const itemModel={...blockModel(itemElements),gui_light:'side',display:{
 thirdperson_righthand:{rotation:[75,45,0],translation:[0,-1,0],scale:[0.3,0.3,0.3]},
 thirdperson_lefthand:{rotation:[75,45,0],translation:[0,-1,0],scale:[0.3,0.3,0.3]},
 firstperson_righthand:{rotation:[0,45,0],translation:[0,-2,0],scale:[0.34,0.34,0.34]},
 firstperson_lefthand:{rotation:[0,225,0],translation:[0,-2,0],scale:[0.34,0.34,0.34]},
 gui:{rotation:[25,135,0],translation:[0,-2,0],scale:[0.46,0.46,0.46]},
 ground:{translation:[0,0,0],scale:[0.32,0.32,0.32]},
 fixed:{rotation:[0,180,0],translation:[0,-3,0],scale:[0.4,0.4,0.4]}
}};
const outputs=new Map([
 [path.join(assetRoot,'models/block/office_multifunction_printer_lower.json'),blockModel(lower)],
 [path.join(assetRoot,'models/block/office_multifunction_printer_upper.json'),blockModel(upper)],
 [path.join(assetRoot,'blockstates/office_multifunction_printer.json'),blockstate],
 [path.join(assetRoot,'models/item/office_multifunction_printer.json'),itemModel]
]);
const texture=Buffer.from(model.textures[0].source.split(',')[1],'base64');
const texturePath=path.join(assetRoot,'textures/block/office_multifunction_printer.png');
if(checkOnly){
 for(const [file,data] of outputs)assert.deepEqual(JSON.parse(fs.readFileSync(file,'utf8')),data,`${path.relative(root,file)} is stale`);
 assert(fs.readFileSync(texturePath).equals(texture),'runtime texture is stale');
}else{
 for(const [file,data] of outputs){fs.mkdirSync(path.dirname(file),{recursive:true});fs.writeFileSync(file,JSON.stringify(data,null,2)+'\n');}
 fs.mkdirSync(path.dirname(texturePath),{recursive:true});fs.writeFileSync(texturePath,texture);
}
console.log(JSON.stringify({sourceCubes:exported.length,lowerElements:lower.length,upperElements:upper.length,itemElements:itemElements.length,normalizedEightDegreeAdfParts:normalizedRotations/2,texture:'128x128',mode:checkOnly?'check':'write'}));
