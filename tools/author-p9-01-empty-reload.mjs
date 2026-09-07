// Source-only authoring. --format is mechanical JSON formatting; --patch emits an apply_patch patch.
import fs from 'node:fs';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {read,sourcePath} from './export-native-gun.mjs';
import {I,chain,T,R,pt,scene,sample} from './check-native-gun-aimline.mjs';
const axes=['x','y','z'],add=(a,b)=>a.map((v,i)=>v+b[i]),sub=(a,b)=>a.map((v,i)=>v-b[i]);
const mul=(a,n)=>a.map(v=>v*n),smooth=u=>{u=Math.max(0,Math.min(1,u));return u*u*u*(10+u*(-15+6*u));};
const mix=(a,b,u)=>a.map((v,i)=>v+(b[i]-v)*smooth(u));
const rot=v=>chain(R(2,v[2]),R(1,v[1]),R(0,v[0]));
function inverse(m){const n=I(),s=m[0]**2+m[4]**2+m[8]**2;for(let i=0;i<3;i++)for(let j=0;j<3;j++)n[i*4+j]=m[j*4+i]/s;pt(n,[-m[3],-m[7],-m[11]]).forEach((v,i)=>n[i*4+3]=v);return n;}
function euler(m){return [Math.atan2(m[9],m[10]),Math.asin(Math.max(-1,Math.min(1,-m[8]))),Math.atan2(m[4],m[0])].map(v=>v*180/Math.PI);}
function format(v,d=0,k=''){
 const pad='  '.repeat(d),next=pad+'  ';
 if(v===null||typeof v!=='object')return JSON.stringify(v);
 if(Array.isArray(v))return !v.length?'[]':'[\n'+v.map(x=>next+(k==='keyframes'?JSON.stringify(x):format(x,d+1))).join(',\n')+'\n'+pad+']';
 const entries=Object.entries(v);return !entries.length?'{}':'{\n'+entries.map(([a,b])=>next+JSON.stringify(a)+': '+format(b,d+1,a)).join(',\n')+'\n'+pad+'}';
}
function key(name,ch,time,v){const h=createHash('sha256').update(`afl-empty-rack:${name}:${ch}:${time}`).digest('hex');return {
 channel:ch,data_points:[Object.fromEntries(axes.map((a,i)=>[a,String(+v[i].toFixed(10))]))],
 uuid:`${h.slice(0,8)}-${h.slice(8,12)}-4${h.slice(13,16)}-8${h.slice(17,20)}-${h.slice(20,32)}`,time,color:-1,interpolation:'linear'};}
function build(input){
 const s=structuredClone(input),g=n=>s.groups.find(g=>g.name===n),a=s.animations.find(a=>a.name.endsWith('.reload_empty'));
 const normal=input.animations.find(a=>a.name.endsWith('.reload')),old=structuredClone(a);
 assert.equal(old.length,1.3,'Only upgrade the existing simplified clip once');a.length=1.65;
 const slideNames=['slide','front_sight','rear_sight','sight_anchor'];
 for(const [id,animator]of Object.entries(a.animators)){
  if(!animator.keyframes?.length)continue;const name=gName(id),keys=old.animators[id].keyframes;
  if(slideNames.includes(name))continue;
  const out=keys.filter(k=>k.time<=.95);
  for(const ch of new Set(keys.map(k=>k.channel))){
   if(!out.some(k=>k.channel===ch&&k.time===.95))out.push(key(name,ch,.95,sample(keys,ch,.95)));
   for(let i=96;i<=165;i++){const t=i/100,base=.95+.35*smooth((t-1.4)/.25);out.push(key(name,ch,t,sample(keys,ch,base)));}
  }
  animator.keyframes=out.sort((a,b)=>a.channel.localeCompare(b.channel)||a.time-b.time);
 }
 function gName(id){return s.groups.find(g=>g.uuid===id).name;}
 const travel=t=>t<=1.25?1.28:t<=1.33?1.28+.20*smooth((t-1.25)/.08):t<=1.43?1.48:t<1.46?1.48*(1-smooth((t-1.43)/.03)):0;
 for(const name of slideNames){const animator=a.animators[g(name).uuid];animator.keyframes=[];
  for(let i=0;i<=330;i++){const t=i*.005;animator.keyframes.push(key(name,'position',+t.toFixed(3),[0,0,travel(t)]));}}
 const anchor=g('left_hand_anchor'),pivot=g('left_hand_motion').origin;
 const localPoint=(sc,bone,p)=>mul(pt(inverse(sc.matrix('weapon_root')),pt(sc.matrix(bone),mul(p,1/16))),16);
 const sc0=scene(s,'firstperson_righthand','reload_empty',.95),start=localPoint(sc0,'left_hand_anchor',anchor.origin);
 const original=old.animators[g('left_hand_motion').uuid].keyframes,startR=sample(original,'rotation',.95);
 // Distal cap approaches the left/rear slide wall; proximal forearm remains outside and below it.
 const rackR=euler(chain(rot([-65,-15,-30]),inverse(rot(anchor.rotation))));
 const tracks=a.animators[g('left_hand_motion').uuid];tracks.keyframes=tracks.keyframes.filter(k=>k.time<=.95);
 for(let i=191;i<=330;i++){
  const t=+(i*.005).toFixed(3),sc=scene(s,'firstperson_righthand','reload_empty',t);
  const contact=t<=1.43?localPoint(sc,'slide',[-6.9,10.45,8.0]):localPoint(sc,'gun',[-6.9,10.45,9.48]);
  const approach=add(contact,[-.9,-4.2,1.5]),clear=add(contact,[-1.2,-1.2,2]);
  let target,r;
  if(t<=1.05){target=start;r=startR;}
  else if(t<=1.15){target=mix(start,approach,(t-1.05)/.10);r=mix(startR,rackR,(t-1.05)/.20);}
  else if(t<=1.25){target=mix(approach,contact,(t-1.15)/.10);r=mix(startR,rackR,(t-1.05)/.20);}
  else if(t<=1.43){target=contact;r=rackR;}
  else if(t<=1.50){target=mix(contact,clear,(t-1.43)/.07);r=rackR;}
  else {target=mix(clear,anchor.origin,(t-1.50)/.15);r=mix(rackR,[0,0,0],(t-1.50)/.15);}
  const p=sub(sub(target,pivot),pt(rot(r),sub(anchor.origin,pivot)));
  tracks.keyframes.push(key('left_hand_motion','position',t,p),key('left_hand_motion','rotation',t,r));
 }
 tracks.keyframes.sort((a,b)=>a.channel.localeCompare(b.channel)||a.time-b.time);
 assert.deepEqual(s.animations.filter(x=>x!==a),input.animations.filter(x=>x!==input.animations.find(a=>a.name.endsWith('.reload_empty'))));
 for(const [id,v]of Object.entries(normal.animators))if(v.keyframes?.length&&!slideNames.includes(gName(id)))
  for(let i=0;i<=190;i++)for(const ch of new Set(v.keyframes.map(k=>k.channel))){
   const t=i*.005;assert(Math.hypot(...sub(sample(v.keyframes,ch,t),sample(a.animators[id].keyframes,ch,t)))<1e-7,'Frozen first .95s '+gName(id));}
 for(const name of ['left_hand_anchor','right_hand_anchor','muzzle_anchor','slide'])
  assert(Math.hypot(...sub(scene(s,'firstperson_righthand','reload_empty',1.65).point(name),scene(s).point(name)))<1e-7,'Ready return '+name);
 a.animators.effects={name:'Effects',type:'effect',keyframes:[{channel:'sound',data_points:[{effect:'slide_action',file:'E:/Download/slide_action.ogg'}],uuid:'a0650000-0025-4000-8000-000000000001',time:1.25,color:-1,interpolation:'linear'}]};
 return s;
}
const original=read(sourcePath);
if(process.argv.includes('--format')){fs.writeFileSync(sourcePath,format(original)+'\n');}
else if(process.argv.includes('--patch')){
 const before=format(original).split('\n'),after=format(build(original)).split('\n');
 // Unique-line monotone anchors yield small source-only patches without touching frozen sections.
 const positions=lines=>{let m=new Map();lines.forEach((l,i)=>m.set(l,m.has(l)?-1:i));return m;};
 const om=positions(before),nm=positions(after),anchors=[[-1,-1]];let last=-1;
 before.forEach((l,i)=>{let j=nm.get(l);if(om.get(l)===i&&j!==undefined&&j>last){anchors.push([i,j]);last=j;}});
 anchors.push([before.length,after.length]);let patch='*** Begin Patch\n*** Update File: '+sourcePath.replaceAll('\\','/')+'\n';
 for(let i=1;i<anchors.length;i++){const [oa,na]=anchors[i-1],[ob,nb]=anchors[i],old=before.slice(oa+1,ob),fresh=after.slice(na+1,nb);
  if(JSON.stringify(old)===JSON.stringify(fresh))continue;
  patch+='@@\n'+old.map(l=>'-'+l+'\n').join('')+fresh.map(l=>'+'+l+'\n').join('');if(ob<before.length)patch+=' '+before[ob]+'\n';
 }
 patch+='*** End Patch\n';
 if(process.argv.includes('--length'))console.log(patch.length);
 else if(process.argv.includes('--slice')){const i=process.argv.indexOf('--slice');process.stdout.write(patch.slice(+process.argv[i+1],+process.argv[i+2]));}
 else process.stdout.write(patch);
}else{
 const a=original.animations.find(a=>a.name.endsWith('.reload_empty'));
 console.log(JSON.stringify({duration:a.length,sourceOnly:true,preview:'PENDING_USER'}));
}
