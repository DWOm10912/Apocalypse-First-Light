// DEV-only saved-source diagnostics. No runtime transforms, images or file writes.
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {pathToFileURL} from 'node:url';
import {read,sourcePath,assets,compile} from './export-native-gun.mjs';
export const I=()=>[1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1];
export const mul=(a,b)=>Array.from({length:16},(_,i)=>{let n=0;for(let k=0;k<4;k++)n+=a[(i>>2)*4+k]*b[k*4+i%4];return n});
export const chain=(...ms)=>ms.reduce(mul,I());
export const T=v=>{let m=I();v.forEach((n,i)=>m[i*4+3]=n);return m};
export const S=v=>{let m=I();v.forEach((n,i)=>m[i*5]=n);return m};
export const R=(axis,deg)=>{let m=I(),j=(axis+1)%3,k=(axis+2)%3,c=Math.cos(deg*Math.PI/180),s=Math.sin(deg*Math.PI/180);m[j*4+j]=m[k*4+k]=c;m[j*4+k]=-s;m[k*4+j]=s;return m};
export const pt=(m,v)=>[0,1,2].map(i=>m[i*4+3]+v.reduce((n,x,j)=>n+m[i*4+j]*x,0));
const scale=(v,s)=>v.map(n=>n*s),sub=(a,b)=>a.map((n,i)=>n-b[i]);
export function sample(keys,ch,t){
 const ks=keys.filter(k=>k.channel===ch).sort((a,b)=>a.time-b.time),vec=k=>['x','y','z'].map(a=>+k.data_points[0][a]);
 if(!ks.length)return [0,0,0];let i=ks.findIndex(k=>k.time>=t);
 if(i===0)return vec(ks[0]);if(i<0)return vec(ks.at(-1));
 const a=ks[i-1],b=ks[i],u=a.interpolation==='step'&&t<b.time?0:(t-a.time)/(b.time-a.time);
 return vec(a).map((n,j)=>n+(vec(b)[j]-n)*u);
}
export function scene(source,context='firstperson_righthand',action='ready',t=0){
 const groups=new Map(source.groups.map(g=>[g.uuid,g])),by=new Map(source.groups.map(g=>[g.name,g])),parents=new Map();
 function walk(nodes,parent){for(const n of nodes)if(typeof n!=='string'){let g=groups.get(n.uuid);parents.set(g.name,parent);walk(n.children,g.name)}}walk(source.outliner,null);
 const d=source.display[context],r=d.rotation||[0,0,0],tr=d.translation.map(n=>n/16),left=context.endsWith('lefthand');
 const java=fs.readFileSync(new URL('../src/main/java/com/antaurora/apofirstlight/weapon/client/P901FirstPerson.java',import.meta.url),'utf8');
 const offset=['X','Y'].map(axis=>Number(java.match(new RegExp('COMPOSITION_'+axis+' = ([.0-9]+)F'))?.[1]||0));
 const base=chain(T([left?-offset[0]:offset[0],offset[1],0]),T([left?-tr[0]:tr[0],tr[1],tr[2]]),R(0,r[0]),R(1,left?-r[1]:r[1]),R(2,left?-r[2]:r[2]),S(d.scale),T([0,.01,0]));
 const anim=source.animations.find(a=>a.name.endsWith('.'+action)),cache=new Map();
 function matrix(name){if(!name)return base;if(cache.has(name))return cache.get(name);
  const g=by.get(name),keys=anim?.animators[g.uuid]?.keyframes||[],p=scale(g.origin,1/16),rot=(g.rotation||[0,0,0]).map((n,i)=>n+sample(keys,'rotation',t)[i]);
  let m=chain(matrix(parents.get(name)),T(scale(sample(keys,'position',t),1/16)),T(p),R(2,rot[2]),R(1,rot[1]),R(0,rot[0]),T(scale(p,-1)));cache.set(name,m);return m;
 }
 return {matrix,point:name=>pt(matrix(name),scale(by.get(name).origin,1/16))};
}
// Perspective reference: 1080 high, vertical FOV70, camera -Z. Not ADS or ballistics.
const project=p=>{assert(p[2]<-.05);return [p[0]/-p[2],-p[1]/-p[2]].map(n=>n*540/Math.tan(35*Math.PI/180))};
export function aimline(source,context='firstperson_righthand'){
 const s=scene(source,context),m=s.point('muzzle_anchor'),origin=pt(s.matrix('barrel'),[0,0,0]),p=pt(s.matrix('barrel'),[0,0,-1]),axis=sub(p,origin),length=Math.hypot(...axis),forward=scale(axis,1/length);
 assert(forward[2]<0,'Barrel must face camera -Z');
 const atDepth=(p,d)=>p.map((n,i)=>n+d[i]*(-20-p[2])/d[2]);
 const f=s.point('front_sight'),b=s.point('rear_sight'),sight=sub(f,b);
 return {muzzle:m,barrelForward:forward,muzzleProjected:project(m),muzzleRay20:project(atDepth(m,forward)),sightRay20:project(atDepth(s.point('sight_anchor'),sight)),frontProjected:project(f),rearProjected:project(b)};
}
export function calibrate(source,context){
 const copy=structuredClone(source),r=copy.display[context].rotation;
 for(let i=0;i<8;i++){
  const e=aimline(copy,context).muzzleRay20,J=[];
  for(let a=0;a<2;a++){r[a]+=.001;J.push(aimline(copy,context).muzzleRay20.map((n,j)=>(n-e[j])/.001));r[a]-=.001}
  const det=J[0][0]*J[1][1]-J[1][0]*J[0][1];assert(Math.abs(det)>1e-8);
  r[0]-=(e[0]*J[1][1]-e[1]*J[1][0])/det;r[1]-=(J[0][0]*e[1]-J[0][1]*e[0])/det;
 }return r.map(n=>Math.round(n*1e8)/1e8);
}
export function entrance(source){
 const g=source.groups.find(g=>g.name==='fp_root'),keys=source.animations.find(a=>a.name.endsWith('.reload')).animators[g.uuid].keyframes;
 let previous={p:[0,0,0],r:[0,0,0],t:0};
 return [0,.04,.08,.12,.16,.20,.24].map(t=>{let p=sample(keys,'position',t),r=sample(keys,'rotation',t),dt=t-previous.t;
  let row={t,p,r,translationSpeed:dt?Math.hypot(...sub(p,previous.p))/dt:0,rotationSpeed:dt?Math.hypot(...sub(r,previous.r))/dt:0};previous={p,r,t};return row});
}
if(process.argv[1]&&import.meta.url===pathToFileURL(process.argv[1]).href){
 const source=read(sourcePath),out=compile(source,read(assets+'/geo/p9_01.geo.json'),read(assets+'/models/item/p9_01_in_hand.json'));
 assert.deepEqual(out.animations,read(assets+'/animations/p9_01.animation.json'),'Saved-source animation export');
 assert.deepEqual(out.display,read(assets+'/models/item/p9_01_in_hand.json'),'Saved-source FP Display export');
 for(const c of ['firstperson_righthand','firstperson_lefthand'])assert(Math.hypot(...aimline(source,c).muzzleRay20)<.05);
 const g=source.groups.find(g=>g.name==='fp_root'),keys=source.animations.find(a=>a.name.endsWith('.reload')).animators[g.uuid].keyframes;
 let firstSpeed=0,lastSpeed=0,peakSpeed=0,previous=sample(keys,'position',0);
 for(let i=1;i<=48;i++){const p=sample(keys,'position',i*.005),speed=Math.hypot(...sub(p,previous))/.005;
  if(i===1)firstSpeed=speed;if(i===48)lastSpeed=speed;peakSpeed=Math.max(peakSpeed,speed);previous=p;
 }
 assert(firstSpeed<.1,'Entrance first segment starts near rest');assert(lastSpeed<3,'Entrance decelerates into hold');
 assert.deepEqual(sample(keys,'position',0),[0,0,0]);
 // Fire and Reload must return to their common Ready carrier, never a separate aim pose.
 for(const action of ['fire','reload'])for(const name of ['muzzle_anchor','right_hand_anchor','left_hand_anchor']){
  const end=scene(source,'firstperson_righthand',action,action==='fire'?.14:1.3).point(name),start=scene(source).point(name);
  assert(Math.hypot(...sub(end,start))<1e-7,'Return to Ready '+action+' '+name);
 }
 console.log(JSON.stringify({pass:true,reference:'1080px high / FOV70 / ray at camera Z=-20 render units; +X right, +Y down; no ADS, no GPU claim',right:aimline(source),left:aimline(source,'firstperson_lefthand'),entrance:entrance(source),firstSpeed,lastSpeed,peakSpeed,geometryExport:'intentionally not rewritten: pre-existing format/guard rounding difference; geometry frozen'},null,2));
}
