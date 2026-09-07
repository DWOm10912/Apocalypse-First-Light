// Source-only Reload authoring candidate. No runtime export, client, images or writes.
// --candidate emits JSON for apply_patch; default audits the currently saved source.
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {pathToFileURL} from 'node:url';
import {read,sourcePath} from './export-native-gun.mjs';
import {I,chain,T,R,pt,scene,sample} from './check-native-gun-aimline.mjs';
const axes=['x','y','z'],zero=[0,0,0];
const add=(a,b)=>a.map((v,i)=>v+b[i]),sub=(a,b)=>a.map((v,i)=>v-b[i]),scale=(a,n)=>a.map(v=>v*n);
const smooth=u=>{u=Math.max(0,Math.min(1,u));return u*u*u*(10+u*(-15+6*u))};
const rotation=v=>chain(R(2,v[2]),R(1,v[1]),R(0,v[0]));
function inverseRigid(m){const n=I();for(let i=0;i<3;i++)for(let j=0;j<3;j++)n[4*i+j]=m[4*j+i];const p=pt(n,[-m[3],-m[7],-m[11]]);p.forEach((v,i)=>n[4*i+3]=v);return n}
function inverseUniform(m){const n=I(),s2=m[0]**2+m[4]**2+m[8]**2;for(let i=0;i<3;i++)for(let j=0;j<3;j++)n[i*4+j]=m[j*4+i]/s2;pt(n,[-m[3],-m[7],-m[11]]).forEach((v,i)=>n[i*4+3]=v);return n}
function euler(m){const y=Math.asin(Math.max(-1,Math.min(1,-m[8])));assert(Math.abs(Math.cos(y))>.1);return [Math.atan2(m[9],m[10]),y,Math.atan2(m[4],m[0])].map(v=>v*180/Math.PI)}
// Shape-preserving cubic Hermite. Monotonic intervals do not overshoot, equal values dwell.
function scalar(points,t){
 const x=points.map(p=>p[0]),y=points.map(p=>p[1]),h=x.slice(1).map((v,i)=>v-x[i]),d=h.map((v,i)=>(y[i+1]-y[i])/v),m=y.map(()=>0);
 for(let i=1;i<y.length-1;i++)if(d[i-1]*d[i]>0){const w1=2*h[i]+h[i-1],w2=h[i]+2*h[i-1];m[i]=(w1+w2)/(w1/d[i-1]+w2/d[i])}
 if(t<=x[0])return y[0];if(t>=x.at(-1))return y.at(-1);let j=x.findIndex(v=>v>=t)-1;
 const u=(t-x[j])/h[j];return (2*u**3-3*u*u+1)*y[j]+(u**3-2*u*u+u)*h[j]*m[j]+(-2*u**3+3*u*u)*y[j+1]+(u**3-u*u)*h[j]*m[j+1];
}
const path=(knots,t)=>axes.map((_,i)=>scalar(knots.map(([s,v])=>[s,v[i]]),t));
const key=(bone,ch,t,v,interpolation='linear')=>{
 const hash=createHash('sha256').update(`afl-reload-rework:${bone}:${ch}:${t}`).digest('hex');
 return {channel:ch,data_points:[Object.fromEntries(axes.map((a,i)=>[a,String(+v[i].toFixed(10))]))],
 uuid:`${hash.slice(0,8)}-${hash.slice(8,12)}-4${hash.slice(13,16)}-8${hash.slice(17,20)}-${hash.slice(20,32)}`,time:t,color:-1,interpolation};
};
export function contactPoint(source){
 const g=source.groups.find(g=>g.name==='magazine'),plate=source.elements.find(c=>c.name==='magazine_baseplate');
 // Distal-cap contact under the baseplate, biased to its left half. Geometry unchanged.
 const local=[plate.from[0]+(plate.to[0]-plate.from[0])*.25,plate.from[1]+.02,(plate.from[2]+plate.to[2])/2];
 return pt(chain(T(g.origin),rotation(g.rotation),T(scale(g.origin,-1))),local);
}
export function candidate(input,pose=[-35,-15,-80]){
 const s=structuredClone(input),g=n=>s.groups.find(g=>g.name===n),clip=s.animations.find(a=>a.name==='animation.p9_01.reload');
 assert.equal(clip.length,1.3);assert.deepEqual(g('fp_root').origin,g('weapon_root').origin);
 for(const n of ['root','fp_root','weapon_root','gun_model_root','gun'])assert.deepEqual(g(n).rotation,zero);
 const oldWeapon=structuredClone(clip.animators[g('weapon_root').uuid].keyframes);
 const tracks={fp_root:[],left_hand_motion:[],magazine:[],reload_magazine:[]};
 const cap=contactPoint(s),anchor=g('left_hand_anchor').origin,pivot=g('left_hand_motion').origin;
 const magDirection=pt(rotation(g('magazine').rotation),[0,-1,0]);
 // Release at .40, completely clear before departure. New magazine coaxial by .82.
 const distance=[[0,0],[.36,0],[.40,.24],[.43,.34],[.52,6.6],[.55,6.6],[.64,8.2],[.70,8.2],[.72,8],[.76,7.4],[.82,6.5],[.88,2],[.92,.45],[.95,0],[1.3,0]];
 const lateral=[[0,0],[.55,0],[.64,1],[.70,1],[.72,1],[.76,.6],[.82,0],[1.3,0]];
 // After full axial clearance, leave down/left in the revealed composition.
 // This is authored into source translation, not a runtime camera offset.
 const departure=pt(inverseRigid(rotation(pose)),[-3,-5,0]);
 const approach=[[0,anchor],[.04,anchor],[.16,add(anchor,[-1.5,-.5,.8])],[.26,add(cap,[-1.2,.6,-.5])],[.32,cap],[1.3,cap]];
 const returning=[[0,cap],[1.06,cap],[1.16,add(anchor,[-1.2,-.8,1.5])],[1.27,anchor],[1.3,anchor]];
 const wrist=[[0,zero],[.04,zero],[.32,[48,12,10]],[.55,[48,12,10]],[.66,[52,8,2]],[.76,[48,12,10]],[1.06,[48,12,10]],[1.27,zero],[1.3,zero]];
 for(let i=0;i<=260;i++){
  const t=+(i*.005).toFixed(3);
  const entrance=smooth(t/.30),exit=1-smooth((t-1.06)/.24),envelope=entrance*exit;
  const beat=t<.95||t>1.035?0:Math.sin(Math.PI*(t-.95)/.085)**2;
  const desiredR=add(scale(pose,envelope),[.35*beat,0,.15*beat]);
  // Rotate about the EXISTING right-hand cap, not the old carrier pivot.
  // This translation is pivot compensation, not a camera/presentation displacement.
  const gripOffset=sub(g('right_hand_anchor').origin,g('weapon_root').origin);
  const desiredP=sub(gripOffset,pt(rotation(desiredR),gripOffset));
  // Cancel only the inherited old carrier motion INSIDE fp_root authoring keys.
  // Preserve weapon_root verbatim, hence no change to third-person gun choreography.
  const fpR=chain(rotation(desiredR),inverseRigid(rotation(sample(oldWeapon,'rotation',t))));
  const fpP=sub(desiredP,pt(fpR,sample(oldWeapon,'position',t)));
  tracks.fp_root.push(key('fp_root','position',t,fpP),key('fp_root','rotation',t,euler(fpR)));
  const mp=add(scale(magDirection,scalar(distance,t)),scale(departure,scalar(lateral,t)));
  for(const n of ['magazine','reload_magazine'])tracks[n].push(key(n,'position',t,mp));
  const target=t<.32?path(approach,t):t<=1.06?add(cap,mp):path(returning,t),wr=path(wrist,t);
  // Motion rotates about its existing pivot. Solve translation to retain the authored cap contact.
  const lp=sub(sub(target,pivot),pt(rotation(wr),sub(anchor,pivot)));
  tracks.left_hand_motion.push(key('left_hand_motion','position',t,lp),key('left_hand_motion','rotation',t,wr));
 }
 for(const [t,v] of [[0,1],[.64,0],[.72,1],[1.3,1]])tracks.magazine.push(key('magazine','scale',t,[v,v,v],'step'));
 for(const [name,keys]of Object.entries(tracks)){
  const id=g(name).uuid,animator=clip.animators[id];assert(animator,'Existing animator required '+name);
  animator.keyframes=keys.sort((a,b)=>a.channel.localeCompare(b.channel)||a.time-b.time);
 }
 // Zero endpoint relative pose, including contact-solved hand translation rounding.
 for(const n of ['fp_root','left_hand_motion','magazine','reload_magazine'])for(const k of clip.animators[g(n).uuid].keyframes)
  if(k.time===0||k.time===1.3)k.data_points=[Object.fromEntries(axes.map(a=>[a,k.channel==='scale'?'1':'0']))];
 return s;
}
const project=p=>[p[0]/-p[2]*540/Math.tan(35*Math.PI/180),-p[1]/-p[2]*540/Math.tan(35*Math.PI/180)];
// Screen-space line proxy, clipped to a 1920x1080 viewport and the near plane.
// It does NOT infer skin visibility, depth-buffer occlusion or visual approval.
function visibleLine(a,b){
 let lo=0,hi=1;const f=540/Math.tan(35*Math.PI/180);
 const planes=p=>[-p[2]-.05,-p[2]*960/f-p[0],-p[2]*960/f+p[0],-p[2]*540/f-p[1],-p[2]*540/f+p[1]];
 const av=planes(a),bv=planes(b);
 for(let i=0;i<av.length;i++){if(av[i]<0&&bv[i]<0)return 0;if(av[i]<0)lo=Math.max(lo,av[i]/(av[i]-bv[i]));if(bv[i]<0)hi=Math.min(hi,av[i]/(av[i]-bv[i]));}
 if(lo>hi)return 0;return Math.hypot(...sub(project(add(a,scale(sub(b,a),lo))),project(add(a,scale(sub(b,a),hi)))));
}
export function composition(s){
 const g=n=>s.groups.find(g=>g.name===n),ref=s.elements.find(e=>e.name==='right_arm_reference_cube');
 const cap=g('right_hand_anchor').origin,proximal=[cap[0],ref.from[1],cap[2]],magwell=contactPoint(s);
 // Static gun bounding-box center excludes both hand proxies; lower magwell uses
 // the authored baseplate contact as a repeatable mouth landmark, not moving mag.
 const cubes=s.elements.filter(e=>!e.name.includes('reference'));
 const center=[0,1,2].map(i=>(Math.min(...cubes.map(c=>c.from[i]))+Math.max(...cubes.map(c=>c.to[i])))/2);
 const marks=[0,.10,.20,.30,.40,.52,.65,.80,.95,1.06,1.18,1.30],rows=[],all=[];
 function rayBlocked(sc,point){
  const m=inverseUniform(sc.matrix('right_arm_reference')),a=pt(m,zero),b=pt(m,point);let lo=0,hi=1;
  for(let i=0;i<3;i++){const d=b[i]-a[i],min=ref.from[i]/16,max=ref.to[i]/16;if(Math.abs(d)<1e-12){if(a[i]<min||a[i]>max)return false;continue;}const t0=(min-a[i])/d,t1=(max-a[i])/d;lo=Math.max(lo,Math.min(t0,t1));hi=Math.min(hi,Math.max(t0,t1));}
  return lo<hi&&hi>0&&lo<1;
 }
 for(let i=0;i<=1040;i++){
  const t=+(i*.00125).toFixed(5),sc=scene(s,'firstperson_righthand','reload',t);
  const grip=sc.point('right_hand_anchor'),gun=pt(sc.matrix('gun'),scale(center,1/16)),mouth=pt(sc.matrix('weapon_root'),scale(magwell,1/16));
  const end=pt(sc.matrix('right_arm_reference'),scale(proximal,1/16));
  const row={t,grip:project(grip),gun:project(gun),magwell:project(mouth),forearmPx:visibleLine(grip,end),left:project(sc.point('left_hand_anchor')),gunDepth:gun[2],grip3:grip,magwellRayBlockedByRightProxy:rayBlocked(sc,mouth)};all.push(row);if(marks.includes(t))rows.push(row);
 }
 const span=f=>Math.max(...all.map(f))-Math.min(...all.map(f)),main=all.filter(r=>r.t>=.3&&r.t<=1.0);
 return {gripScreenDriftPx:Math.max(...all.map(r=>Math.hypot(...sub(r.grip,all[0].grip)))),gunVerticalTravelPx:span(r=>r.gun[1]),gunDepthTravel:span(r=>r.gunDepth),mainForearmMeanPx:main.reduce((v,r)=>v+r.forearmPx,0)/main.length,mainForearmMinPx:Math.min(...main.map(r=>r.forearmPx)),mainMagwellBlockedSamples:main.filter(r=>r.magwellRayBlockedByRightProxy).length,mainMagwellBounds:[0,1].map(i=>[Math.min(...main.map(r=>r.magwell[i])),Math.max(...main.map(r=>r.magwell[i]))]),rows};
}
export function audit(s){
 const clip=s.animations.find(a=>a.name.endsWith('.reload')),g=n=>s.groups.find(g=>g.name===n),cap=contactPoint(s),rows=[];
 const ready=scene(s);
 const rightLocal=T(scale(g('right_hand_anchor').origin,1/16));
 const gripReference=chain(inverseUniform(ready.matrix('frame')),ready.matrix('right_hand_anchor'),rightLocal);
 let gripDrift=0,contactError=0,guideError=0,minY=Infinity,maxY=-Infinity,near=0,maxStep=0,last=null;
 const marks=[0,.20,.40,.52,.70,.85,.95,1.08,1.20,1.30];
 for(let i=0;i<=260;i++){
  const t=+(i*.005).toFixed(3),sc=scene(s,'firstperson_righthand','reload',t),grip=chain(inverseUniform(sc.matrix('frame')),sc.matrix('right_hand_anchor'),rightLocal);
  gripDrift=Math.max(gripDrift,...grip.map((v,j)=>Math.abs(v-gripReference[j])));
  const left=sc.point('left_hand_anchor'),mag=sc.point('magazine'),guide=sc.point('reload_magazine');
  guideError=Math.max(guideError,Math.hypot(...sub(mag,guide)));
  const mp=sample(clip.animators[g('magazine').uuid].keyframes,'position',t),expected=pt(sc.matrix('weapon_root'),scale(add(cap,mp),1/16));
  if((t>=.34&&t<.62)||(t>=.72&&t<=1.035))contactError=Math.max(contactError,Math.hypot(...sub(left,expected)));
  const root=sc.point('weapon_root'),screen=project(root);minY=Math.min(minY,screen[1]);maxY=Math.max(maxY,screen[1]);
  for(const p of [sc.point('muzzle_anchor'),root,sc.point('right_hand_anchor')])if(p[2]>=-.05)near++;
  if(last)maxStep=Math.max(maxStep,Math.hypot(...sub(left,last)));last=left;
  if(marks.includes(t))rows.push({t,left,magazine:mag,guide,rootScreenY:screen[1],leftScreen:project(left),magScale:sample(clip.animators[g('magazine').uuid].keyframes,'scale',t)[0]});
 }
 for(const name of ['muzzle_anchor','right_hand_anchor','left_hand_anchor','magazine'])assert(Math.hypot(...sub(scene(s,'firstperson_righthand','reload',1.3).point(name),ready.point(name)))<1e-8,'Returns Ready '+name);
 return {duration:clip.length,gripDrift,contactError,guideError,rootVerticalTravelPx:maxY-minY,rootScreenY:[minY,maxY],nearPlaneLocatorViolations:near,maxLeftCapStepRenderUnitsAt5ms:maxStep,rows,
  boundary:'Source matrices only. 1080 high/FOV70. No Blockbench playback, runtime, collisions, or user visual PASS.'};
}
if(process.argv[1]&&import.meta.url===pathToFileURL(process.argv[1]).href){
 const source=read(sourcePath);if(process.argv.includes('--candidate'))console.log(JSON.stringify(candidate(source)));
 else {
  const result=audit(source);
  assert(result.gripDrift<1e-8,'Source right grip drift');
  assert(result.contactError<1e-8,'Source active magazine contact');
  assert(result.guideError<1e-8,'Magazine/guide same frame');
  assert.equal(result.nearPlaneLocatorViolations,0,'Control locators ahead of near plane');
  assert(result.rootScreenY[0]>0,'Gun carrier remains below screen center');
  assert(result.maxLeftCapStepRenderUnitsAt5ms<.02,'No large cap position step');
  const screen=composition(source);assert(screen.gripScreenDriftPx<.1,'Grip screen drift including subframes');assert.equal(screen.mainMagwellBlockedSamples,0,'Mouth landmark not hidden by right reference proxy');
  console.log(JSON.stringify({...result,composition:screen,numericChecks:'PASS'},null,2));
 }
}
