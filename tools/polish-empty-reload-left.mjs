// Only the empty-reload left motion track is authored; emits a patch, never writes assets.
import fs from 'node:fs';
import assert from 'node:assert/strict';
import {read,sourcePath} from './export-native-gun.mjs';
import {I,chain,R,pt,scene,sample} from './check-native-gun-aimline.mjs';
const s=read(sourcePath),g=n=>s.groups.find(x=>x.name===n),a=s.animations.find(x=>x.name.endsWith('.reload_empty'));
const id=g('left_hand_motion').uuid,old=structuredClone(a.animators[id]),anchor=g('left_hand_anchor'),pivot=g('left_hand_motion').origin;
const sub=(a,b)=>a.map((v,i)=>v-b[i]),scale=(a,k)=>a.map(v=>v*k),add=(a,b)=>a.map((v,i)=>v+b[i]);
const smooth=u=>{u=Math.max(0,Math.min(1,u));return u*u*u*(10+u*(-15+6*u));},mix=(a,b,u)=>a.map((v,i)=>v+(b[i]-v)*smooth(u));
const rot=v=>chain(R(2,v[2]),R(1,v[1]),R(0,v[0]));
function inv(m){const n=I(),k=m[0]**2+m[4]**2+m[8]**2;for(let i=0;i<3;i++)for(let j=0;j<3;j++)n[i*4+j]=m[j*4+i]/k;pt(n,[-m[3],-m[7],-m[11]]).forEach((v,i)=>n[i*4+3]=v);return n;}
const euler=m=>[Math.atan2(m[9],m[10]),Math.asin(Math.max(-1,Math.min(1,-m[8]/Math.hypot(m[0],m[4],m[8])))),Math.atan2(m[4],m[0])].map(v=>v*180/Math.PI);
const local=(sc,b,p)=>scale(pt(chain(inv(sc.matrix('weapon_root')),sc.matrix(b)),scale(p,1/16)),16);
const initial=scene(s,'firstperson_righthand','reload_empty',.95),start=local(initial,'left_hand_anchor',anchor.origin),startR=sample(old.keyframes,'rotation',.95);
const keys=structuredClone(old.keyframes.filter(k=>k.time<=.95));
// Preserve the magazine segment's Y/Z and rotations. Keep its palm centre on the same side.
for(const k of keys.filter(k=>k.channel==='position')){
 const sc=scene(s,'firstperson_righthand','reload_empty',k.time),m=chain(inv(sc.matrix('gun')),sc.matrix('left_hand_anchor'));
 const x=scale(pt(m,scale(anchor.origin,1/16)),16)[0],dx=Math.min(0,g('gun').origin[0]-.15-x);
 const frame=chain(inv(sc.matrix('weapon_root')),sc.matrix('gun')),delta=sub(pt(frame,[dx,0,0]),pt(frame,[0,0,0]));
 ['x','y','z'].forEach((axis,i)=>k.data_points[0][axis]=String(+(+k.data_points[0][axis]+delta[i]).toFixed(10)));
}
for(let i=191;i<=330;i++){
 const t=+(i*.005).toFixed(3),sc=scene(s,'firstperson_righthand','reload_empty',t);
 // Rear serration side, expressed in the actual slide bone model coordinates.
 const serration=local(sc,'slide',[-4.164,9.9,8.3]);
 const gunFrame=chain(inv(sc.matrix('weapon_root')),sc.matrix('gun'));
 const rack=euler(chain(gunFrame,rot([45,0,-20]),inv(rot(anchor.rotation))));
 // The inboard palm edge touches the same-side serration. No camera-space selection.
 const armFrame=chain(rot(rack),rot(anchor.rotation)),gunArm=chain(inv(gunFrame),armFrame);
 const corners=[[-3.02439,0,-3.02439],[-3.02439,0,3.02439],[3.02439,0,-3.02439],[3.02439,0,3.02439]];
 const touch=corners.reduce((a,b)=>pt(gunArm,a)[0]>pt(gunArm,b)[0]?a:b);
 const contact=sub(serration,pt(armFrame,touch));
 let target,r;
 if(t<=1.12){target=start;r=startR;}
 else if(t<=1.27){target=mix(start,contact,(t-1.12)/.15);r=mix(startR,rack,(t-1.12)/.15);}
 else if(t<=1.38){target=contact;r=rack;}
 else {const release=add(contact,[-.25,-1,0]);target=t<=1.45?mix(contact,release,(t-1.38)/.07):mix(release,anchor.origin,(t-1.45)/.20);r=mix(rack,[0,0,0],(t-1.45)/.20);}
 if(process.argv.includes('--debug')&&[1.12,1.27,1.38,1.5].includes(t))console.error({t,startR,rack,r,target});
 const p=sub(sub(target,pivot),pt(rot(r),sub(anchor.origin,pivot)));
 for(const [ch,v]of [['position',p],['rotation',r]]){const k=structuredClone(old.keyframes.find(k=>k.channel===ch&&Math.abs(k.time-t)<1e-6));assert(k);k.data_points=[Object.fromEntries(['x','y','z'].map((ax,i)=>[ax,String(+v[i].toFixed(10))]))];keys.push(k);}
}
keys.sort((a,b)=>a.channel.localeCompare(b.channel)||a.time-b.time);a.animators[id].keyframes=keys;
const cube=s.elements.find(x=>x.name==='left_arm_reference_cube');let maxX=-Infinity,at=0,contactMax=-Infinity;
if(process.argv.includes('--debug'))for(const t of [.95,1.12,1.27,1.38,1.5,1.65])console.error({t,cap:scene(s,'firstperson_righthand','reload_empty',t).point('left_hand_anchor')});
for(let i=0;i<=1650;i++){const sc=scene(s,'firstperson_righthand','reload_empty',i*.001),m=chain(inv(sc.matrix('gun')),sc.matrix('left_hand_anchor'));const p=scale(pt(m,scale(anchor.origin,1/16)),16);if(i>=1120&&i<=1380)contactMax=Math.max(contactMax,p[0]);if(p[0]>maxX){maxX=p[0];at=i*.001;}}
assert(maxX<g('gun').origin[0],'Palm centre must stay on the same side throughout empty reload');
const gunPalm=t=>{const sc=scene(s,'firstperson_righthand','reload_empty',t);return scale(pt(chain(inv(sc.matrix('gun')),sc.matrix('left_hand_anchor')),scale(anchor.origin,1/16)),16);};
let path=0,previous=gunPalm(1.12);const origin=previous;
for(let i=1121;i<=1270;i++){const p=gunPalm(i/1000);path+=Math.hypot(...sub(p,previous));previous=p;}
const direct=Math.hypot(...sub(previous,origin)),rackDelta=sub(gunPalm(1.38),gunPalm(1.27));
assert(path/direct<1.01,'Approach must be a direct short path');
assert(Math.hypot(rackDelta[0],rackDelta[1])<1e-7,'Rack follows slide Z only');
assert(Math.hypot(...sub(scene(s,'firstperson_righthand','reload_empty',1.65).point('left_hand_anchor'),scene(s).point('left_hand_anchor')))<1e-7);
if(process.argv.includes('--patch')){let patch='*** Begin Patch\n*** Update File: '+sourcePath.replaceAll('\\','/')+'\n';const lines=fs.readFileSync(sourcePath,'utf8').split(/\r?\n/);for(let i=0;i<keys.length;i++){const k=keys[i],prior=old.keyframes.find(x=>x.uuid===k.uuid);if(JSON.stringify(k)!==JSON.stringify(prior)){const line=lines.find(l=>l.includes(prior.uuid)),suffix=line.trim().endsWith(',')?',':'';patch+='@@\n-'+line+'\n+            '+JSON.stringify(k)+suffix+'\n';}}console.log(patch+'*** End Patch');}
else console.log(JSON.stringify({gunCenterPlaneX:g('gun').origin[0],maxPalmCentreX:maxX,at,approachContactMaxX:contactMax,approachLength:path,directLength:direct,pathRatio:path/direct,rackDelta,duration:a.length,onlyTrack:'reload_empty/left_hand_motion',visualPass:false}));
