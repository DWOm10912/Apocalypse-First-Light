import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const sourceRoot=path.join(root,'src/main/blockbench');
const assetRoot=path.join(root,'src/main/resources/assets/apocalypse_firstlight');
const checkOnly=process.argv.includes('--check');
const clean=n=>Math.round(n*1e8)/1e8;
const near=(a,b)=>Math.abs(a-b)<1e-7;
const faceAxes={north:[2,0,0,-1,1,-1],south:[2,1,0,1,1,-1],west:[0,0,2,1,1,-1],east:[0,1,2,-1,1,-1],up:[1,1,0,1,2,1],down:[1,0,0,1,2,-1]};

function source(id,count,drawers){
 const model=JSON.parse(fs.readFileSync(path.join(sourceRoot,`${id}.bbmodel`),'utf8'));
 assert.equal(model.meta?.model_format,'java_block');
 assert.equal(model.elements.length,count,`${id}: approved cube count changed`);
 assert.equal(new Set(model.elements.map(e=>e.uuid)).size,count,`${id}: duplicate element UUID`);
 assert.deepEqual(model.resolution,{width:128,height:128});
 assert.equal(model.textures.length,1);
 const drawerGroups=model.groups.filter(g=>/^drawer_\d\d$/.test(g.name));
 assert.equal(drawerGroups.length,drawers,`${id}: drawer groups changed`);
 drawerGroups.forEach((g,index)=>assert(g.origin.every((value,axis)=>near(value,[8,1+index*7.4+3.55,8][axis])),`${id}: drawer pivot changed`));
 for(const element of model.elements)for(const face of Object.values(element.faces??{}))
  assert(face.texture===null||face.texture===0||face.texture===model.textures[0].uuid,`${id}: bad texture binding`);
 return model;
}

function runtimeElement(model,element){
 assert.equal(element.type,'cube');
 const faces={};
 for(const [name,face]of Object.entries(element.faces??{}))if(face.texture!==null)faces[name]={
  uv:face.uv.map((v,i)=>clean(v*16/(i%2?model.resolution.height:model.resolution.width))),
  texture:'#0',...(face.rotation?{rotation:face.rotation}:{})
 };
 const rotation=element.rotation??[0,0,0];
 const axes=rotation.flatMap((v,i)=>v?[i]:[]);
 assert(axes.length<=1&&(!axes.length||[-45,-22.5,22.5,45].includes(rotation[axes[0]])),`${model.name}: unsupported rotation`);
 return {name:element.name,from:element.from.map(clean),to:element.to.map(clean),
  ...(axes.length?{rotation:{origin:element.origin.map(clean),axis:'xyz'[axes[0]],angle:rotation[axes[0]],rescale:Boolean(element.rescale)}}:{}),
  shade:element.shade!==false,faces};
}

function clipUv(faceName,face,whole,low,high){
 const [normalAxis,positive,uAxis,uSign,vAxis,vSign]=faceAxes[faceName];
 if(!near(positive?high[normalAxis]:low[normalAxis],positive?whole.to[normalAxis]:whole.from[normalAxis]))return null;
 const interval=(axis,sign)=>{const size=whole.to[axis]-whole.from[axis];if(near(size,0))return[0,1];return sign>0?[(low[axis]-whole.from[axis])/size,(high[axis]-whole.from[axis])/size]:[(whole.to[axis]-high[axis])/size,(whole.to[axis]-low[axis])/size];};
 const [u0,u1]=interval(uAxis,uSign),[v0,v1]=interval(vAxis,vSign),[a,b,c,d]=face.uv;
 return {...face,uv:[a+(c-a)*u0,b+(d-b)*v0,a+(c-a)*u1,b+(d-b)*v1].map(clean)};
}

function splitAt(model,elements,boundary){
 const parts={lower:[],upper:[]};
 for(const whole of elements){
  assert(!whole.rotation,`${model.name}: split element unexpectedly rotated: ${whole.name}`);
  for(const [part,minY,maxY,offset]of [['lower',0,boundary,0],['upper',boundary,32,boundary]]){
   const low=[whole.from[0],Math.max(whole.from[1],minY),whole.from[2]];
   const high=[whole.to[0],Math.min(whole.to[1],maxY),whole.to[2]];
   if(high[1]-low[1]<=1e-8)continue;
   const fragment=structuredClone(whole);
   fragment.name=`${whole.name}_${part}`;
   fragment.from=low.map((v,i)=>clean(v-(i===1?offset:0)));
   fragment.to=high.map((v,i)=>clean(v-(i===1?offset:0)));
   fragment.faces={};
   for(const [faceName,face]of Object.entries(whole.faces)){const clipped=clipUv(faceName,face,whole,low,high);if(clipped)fragment.faces[faceName]=clipped;}
   parts[part].push(fragment);
  }
 }
 return parts;
}

const textureRef={'0':'apocalypse_firstlight:block/office_filing_cabinet',particle:'apocalypse_firstlight:block/office_filing_cabinet'};
const blockModel=elements=>({credit:'Apocalypse: First Light — office filing cabinets',parent:'minecraft:block/block',ambientocclusion:false,texture_size:[128,128],textures:textureRef,elements});
const variants=modelFor=>({variants:Object.fromEntries([['north',0],['east',90],['south',180],['west',270]].map(([facing,y])=>[`facing=${facing}`,{model:modelFor,...(y?{y}:{})}]))});
const low=source('low_filing_cabinet',61,2),tall=source('tall_filing_cabinet',105,4);
const lowElements=low.elements.filter(e=>e.export!==false).map(e=>runtimeElement(low,e));
const tallElements=tall.elements.filter(e=>e.export!==false).map(e=>runtimeElement(tall,e));
const tallParts=splitAt(tall,tallElements,16);
const outputs=new Map([
 [path.join(assetRoot,'models/block/low_filing_cabinet.json'),blockModel(lowElements)],
 [path.join(assetRoot,'blockstates/low_filing_cabinet.json'),variants('apocalypse_firstlight:block/low_filing_cabinet')],
 [path.join(assetRoot,'models/item/low_filing_cabinet.json'),{parent:'apocalypse_firstlight:block/low_filing_cabinet',gui_light:'side',display:{thirdperson_righthand:{rotation:[75,45,0],translation:[0,2,0],scale:[0.55,0.55,0.55]},thirdperson_lefthand:{rotation:[75,45,0],translation:[0,2,0],scale:[0.55,0.55,0.55]},firstperson_righthand:{rotation:[0,45,0],translation:[0,1,0],scale:[0.6,0.6,0.6]},firstperson_lefthand:{rotation:[0,225,0],translation:[0,1,0],scale:[0.6,0.6,0.6]},gui:{rotation:[25,135,0],translation:[0,-1,0],scale:[0.68,0.68,0.68]},ground:{translation:[0,2,0],scale:[0.5,0.5,0.5]},fixed:{rotation:[0,180,0],translation:[0,-1,0],scale:[0.6,0.6,0.6]}}}],
 [path.join(assetRoot,'models/block/tall_filing_cabinet_lower.json'),blockModel(tallParts.lower)],
 [path.join(assetRoot,'models/block/tall_filing_cabinet_upper.json'),blockModel(tallParts.upper)],
 [path.join(assetRoot,'blockstates/tall_filing_cabinet.json'),{variants:Object.fromEntries([['north',0],['east',90],['south',180],['west',270]].flatMap(([facing,y])=>['lower','upper'].map(half=>[`facing=${facing},half=${half}`,{model:`apocalypse_firstlight:block/tall_filing_cabinet_${half}`,...(y?{y}:{})}])))}],
 [path.join(assetRoot,'models/item/tall_filing_cabinet.json'),{...blockModel(tallElements),gui_light:'side',display:{thirdperson_righthand:{rotation:[75,45,0],translation:[0,1,0],scale:[0.32,0.32,0.32]},thirdperson_lefthand:{rotation:[75,45,0],translation:[0,1,0],scale:[0.32,0.32,0.32]},firstperson_righthand:{rotation:[0,45,0],translation:[0,-2,0],scale:[0.36,0.36,0.36]},firstperson_lefthand:{rotation:[0,225,0],translation:[0,-2,0],scale:[0.36,0.36,0.36]},gui:{rotation:[25,135,0],translation:[0,-5,0],scale:[0.45,0.45,0.45]},ground:{translation:[0,0,0],scale:[0.34,0.34,0.34]},fixed:{rotation:[0,180,0],translation:[0,-5,0],scale:[0.43,0.43,0.43]}}}]
]);

// Inventory framing: slightly smaller cabinets, lifted within the item slot.
outputs.get(path.join(assetRoot,'models/item/low_filing_cabinet.json')).display.gui =
 {rotation:[25,135,0],translation:[0,1,0],scale:[0.6,0.6,0.6]};
outputs.get(path.join(assetRoot,'models/item/tall_filing_cabinet.json')).display.gui =
 {rotation:[25,135,0],translation:[0,-3,0],scale:[0.4,0.4,0.4]};

const lowTexture=Buffer.from(low.textures[0].source.split(',')[1],'base64');
const tallTexture=Buffer.from(tall.textures[0].source.split(',')[1],'base64');
assert(lowTexture.equals(tallTexture),'Cabinet sources do not share the same texture');
const texturePath=path.join(assetRoot,'textures/block/office_filing_cabinet.png');
if(checkOnly){
 for(const [file,data]of outputs)assert.deepEqual(JSON.parse(fs.readFileSync(file,'utf8')),data,`${path.relative(root,file)} is stale`);
 assert(fs.readFileSync(texturePath).equals(lowTexture),'runtime texture is stale');
}else{
 for(const [file,data]of outputs){fs.mkdirSync(path.dirname(file),{recursive:true});fs.writeFileSync(file,JSON.stringify(data,null,2)+'\n');}
 fs.mkdirSync(path.dirname(texturePath),{recursive:true});fs.writeFileSync(texturePath,lowTexture);
}
console.log(JSON.stringify({lowCubes:lowElements.length,tallCubes:tallElements.length,tallLowerFragments:tallParts.lower.length,tallUpperFragments:tallParts.upper.length,texture:'128x128',mode:checkOnly?'check':'write'}));
