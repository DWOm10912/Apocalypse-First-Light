// Read-only V0.5 BEFORE-contract projection (inherited Display), NOT the V0.5.1 renderer.
// For current before/after use check-native-arm-presentation.mjs.
// Reads local assets/JAR in memory; never writes/extracts assets.
import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';
import crypto from 'node:crypto';
import assert from 'node:assert/strict';
import {fileURLToPath} from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const read=p=>JSON.parse(fs.readFileSync(path.join(root,p),'utf8').replace(/^\uFEFF/,''));
const jarPath=path.join(root,'.gradle-user/caches/modules-2/files-2.1/curse.maven/timeless-and-classics-zero-1028108/8141310/bddafeea4c9c1132ed720c30fbaedfe5ab25e846/timeless-and-classics-zero-1028108-8141310.jar');
// ZIP central directory, stored/deflated entries only. No filesystem extraction.
export function zipEntries(data){
 let end=data.length-22;while(end>=Math.max(0,data.length-65557)&&data.readUInt32LE(end)!==0x06054b50)end--;
 assert(end>=0,'ZIP end missing'); const entries=new Map();let offset=data.readUInt32LE(end+16);
 for(let i=0;i<data.readUInt16LE(end+10);i++){
  assert.equal(data.readUInt32LE(offset),0x02014b50);
  const method=data.readUInt16LE(offset+10),size=data.readUInt32LE(offset+20),n=data.readUInt16LE(offset+28),ex=data.readUInt16LE(offset+30),co=data.readUInt16LE(offset+32),local=data.readUInt32LE(offset+42);
  const name=data.toString('utf8',offset+46,offset+46+n);
  entries.set(name,()=>{const start=local+30+data.readUInt16LE(local+26)+data.readUInt16LE(local+28),body=data.subarray(start,start+size);assert([0,8].includes(method));return method===8?zlib.inflateRawSync(body):body});
  offset+=46+n+ex+co;
 }return entries;
}
const jar=fs.readFileSync(jarPath),entries=zipEntries(jar),prefix='assets/tacz/custom/tacz_default_gun/assets/tacz/';
const tjson=p=>JSON.parse(entries.get(prefix+p)().toString('utf8'));
const tg=tjson('geo_models/gun/glock_17_geo.json')['minecraft:geometry'][0].bones;
const ta=tjson('animations/glock_17.animation.json').animations;
const td=tjson('display/guns/glock_17_display.json');
const ap='src/main/resources/assets/apocalypse_firstlight/';
const ag=read(ap+'geo/p9_01.geo.json')['minecraft:geometry'][0].bones,aa=read(ap+'animations/p9_01.animation.json').animations,ad=read(ap+'models/item/p9_01_in_hand.json').display.firstperson_righthand;
assert.match(fs.readFileSync(path.join(root,'src/main/java/com/antaurora/apofirstlight/weapon/client/NativePlayerArmRenderer.java'),'utf8'),/PLAYER_ARM_SCALE = 1F/);
assert(ad.scale.every(n=>n===.41),'Re-audit changed common Display');
const I=()=>[1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1];
const mul=(a,b)=>Array.from({length:16},(_,i)=>{const r=i>>2,c=i%4;let n=0;for(let k=0;k<4;k++)n+=a[r*4+k]*b[k*4+c];return n});
const chain=(...ms)=>ms.reduce(mul,I());
const T=v=>{const a=I();for(let i=0;i<3;i++)a[i*4+3]=v[i];return a};
const S=v=>{const a=I();for(let i=0;i<3;i++)a[i*4+i]=v[i];return a};
const rot=(axis,deg)=>{const a=I(),j=(axis+1)%3,k=(axis+2)%3,t=deg*Math.PI/180,c=Math.cos(t),s=Math.sin(t);a[j*4+j]=a[k*4+k]=c;a[j*4+k]=-s;a[k*4+j]=s;return a};
const R=v=>chain(rot(2,v[2]),rot(1,v[1]),rot(0,v[0]));
const add=(a,b)=>a.map((v,i)=>v+b[i]),sub=(a,b)=>a.map((v,i)=>v-b[i]),times=(a,n)=>a.map(v=>v*n);
const pt=(m,v)=>[0,1,2].map(i=>m[i*4+3]+v.reduce((s,n,j)=>s+m[i*4+j]*n,0));
const direction=(m,v)=>sub(pt(m,v),pt(m,[0,0,0]));
const norm=v=>times(v,1/Math.hypot(...v));
const dot=(a,b)=>a.reduce((n,v,i)=>n+v*b[i],0);
const cross=(a,b)=>[a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]];
function inverse(m){
 const a=[m[0],m[4],m[8]],b=[m[1],m[5],m[9]],c=[m[2],m[6],m[10]],d=dot(a,cross(b,c));
 assert(Math.abs(d)>1e-14,'Singular matrix');const rows=[cross(b,c),cross(c,a),cross(a,b)].map(v=>times(v,1/d)),out=I();
 for(let i=0;i<3;i++)for(let j=0;j<3;j++)out[i*4+j]=rows[i][j];
 const p=pt(out,[-m[3],-m[7],-m[11]]);for(let i=0;i<3;i++)out[i*4+3]=p[i];return out;
}
const zero=[0,0,0],one=[1,1,1],div=v=>times(v,1/16);
const auditMatrix=chain(T([.2,-.3,.4]),R([17,-31,12]),S([.41,1.5,.7]));
assert(chain(auditMatrix,inverse(auditMatrix)).every((v,i)=>Math.abs(v-I()[i])<1e-12));
function sample(channel,t,fallback){
 if(channel==null)return fallback;
 if(Array.isArray(channel))return channel.map(Number);
 const keys=Object.keys(channel).map(Number).sort((a,b)=>a-b);
 const vec=v=>Array.isArray(v)?v:v.vector||v.post||v.pre;
 let i=keys.findIndex(k=>k>=t);
 if(i===0)return vec(channel[String(keys[0])]??channel[Object.keys(channel).find(k=>+k===keys[0])]).map(Number);
 const val=k=>channel[Object.keys(channel).find(n=>+n===k)];
 if(i<0)return vec(val(keys.at(-1))).map(Number);
 const a=keys[i-1],b=keys[i],v=vec(val(a)).map(Number),w=vec(val(b)).map(Number);
 const f=val(b).easing==='afl_hold'&&t<b?0:(t-a)/(b-a);
 return v.map((n,j)=>n*(1-f)+w[j]*f);
}
const pivotA=b=>[-b.pivot[0],b.pivot[1],b.pivot[2]];
const angleA=r=>[-r[0],-r[1],r[2]];
function aflScene(action,t){
 const anim=aa['animation.p9_01.'+action]?.bones||{},by=new Map(ag.map(b=>[b.name,b])),cache=new Map();
 const base=chain(T(div(ad.translation)),rot(0,ad.rotation[0]),rot(1,ad.rotation[1]),rot(2,ad.rotation[2]),S(ad.scale),T([0,.01,0]));
 function bm(name){if(!name)return base;if(cache.has(name))return cache.get(name);
  const b=by.get(name),a=anim[name]||{},p=div(pivotA(b)),r=add(angleA(b.rotation||zero),angleA(sample(a.rotation,t,zero))),pos=sample(a.position,t,zero);pos[0]*=-1;
  const m=chain(bm(b.parent),T(div(pos)),T(p),R(r),S(sample(a.scale,t,one)),T(times(p,-1)));cache.set(name,m);return m;
 }
 const boxes=[];
 for(const b of ag)for(const c of b.cubes||[]){
  const m=chain(bm(b.name),T(div(c.pivot?pivotA(c):zero)),R(angleA(c.rotation||zero)),T(times(div(c.pivot?pivotA(c):zero),-1)));
  if(Math.hypot(m[0],m[4],m[8])<1e-10)continue;
  const inflate=c.inflate||0,lo=div([-(c.origin[0]+c.size[0])-inflate,c.origin[1]-inflate,c.origin[2]-inflate]),hi=add(lo,div(c.size.map(n=>n+2*inflate)));
  boxes.push({name:b.name,lo,hi,inv:inverse(m)});
 }
 return {boxes,gunFrame:pt(bm('weapon_root'),div(pivotA(by.get('weapon_root')))),arm:(side,width)=>chain(bm(side+'_hand_anchor'),T(div(pivotA(by.get(side+'_hand_anchor'))))),locator:side=>pt(chain(bm(side+'_hand_anchor'),T(div(pivotA(by.get(side+'_hand_anchor'))))),zero)};
}
function taczScene(action,t){
 const by=new Map(tg.map(b=>[b.name,b])),cache=new Map(),clip=ta[action]||ta.static_idle;
 function track(name,ch){return sample(clip.bones?.[name]?.[ch],t,sample(ta.static_idle.bones?.[name]?.[ch],0,ch==='scale'?one:zero))}
 function offset(b){const p=b.pivot,parent=by.get(b.parent);return div(parent?[p[0]-parent.pivot[0],parent.pivot[1]-p[1],p[2]-parent.pivot[2]]:[p[0],24-p[1],p[2]])}
 function raw(name,animated=true){if(!name)return I();const key=name+animated;if(cache.has(key))return cache.get(key);
  const b=by.get(name),v=animated?track(name,'position'):zero,extra=animated?track(name,'rotation'):zero;
  const m=chain(raw(b.parent,animated),T(div([v[0],-v[1],v[2]])),T(offset(b)),R(b.rotation||zero),R(extra),S(animated?track(name,'scale'):one));cache.set(key,m);return m;
 }
 const inverseView=chain(inverse(raw('idle_view',false)),T([0,1.5,0]));
 const base=chain(T([0,1.5,0]),rot(2,180),T([0,1.5,0]),inverseView,T([0,-1.5,0]));
 const bm=name=>mul(base,raw(name));
 // Standard magazine, loaded gun, no attachments. Extended branches are mutually exclusive.
 const excluded=new Set(['lefthand_pos','righthand_pos','muzzle_flash','shell','bullet','mag_extended_1','mag_extended_2','mag_extended_3']);
 function previewBranch(b){return excluded.has(b.name)||(b.parent&&previewBranch(by.get(b.parent)))}
 const boxes=[];
 for(const b of tg){if(previewBranch(b))continue;for(const c of b.cubes||[]){
  const p=c.rotation?c.pivot:b.pivot;
  const cp=div([p[0]-b.pivot[0],b.pivot[1]-p[1],p[2]-b.pivot[2]]);
  const m=chain(bm(b.name),T(cp),R(c.rotation||zero));if(Math.hypot(m[0],m[4],m[8])<1e-10)continue;
  const n=c.inflate||0,lo=div([c.origin[0]-p[0]-n,p[1]-c.origin[1]-c.size[1]-n,c.origin[2]-p[2]-n]),hi=add(lo,div(c.size.map(v=>v+2*n)));
  boxes.push({name:b.name,lo,hi,inv:inverse(m)});
 }}
 return {boxes,gunFrame:pt(bm('g17'),zero),arm:(side,width)=>{
  const right=side==='right',centre=(right?-1:1)*(width===3?.5:1);
  return chain(bm(side+'hand_pos'),rot(2,180),T(div([(right?-5:5)+centre,12,0])));
 },locator:side=>pt(bm(side+'hand_pos'),zero)};
}
function ray(point,box){
 const o=pt(box.inv,zero),d=sub(pt(box.inv,point),o);let enter=0,leave=1;
 for(let i=0;i<3;i++){if(Math.abs(d[i])<1e-12){if(o[i]<box.lo[i]||o[i]>box.hi[i])return false}
 else{const a=(box.lo[i]-o[i])/d[i],b=(box.hi[i]-o[i])/d[i];enter=Math.max(enter,Math.min(a,b));leave=Math.min(leave,Math.max(a,b));}
 if(enter>leave)return false;}return leave>0&&enter<.99999;
}
const fov=70,aspect=16/9,H=1080,tan=Math.tan(fov*Math.PI/360);
const project=p=>[p[0]/(-p[2]*tan*aspect),p[1]/(-p[2]*tan)];
const inside=p=>p[2]<-.05&&Math.abs(project(p)[0])<=1&&Math.abs(project(p)[1])<=1;
function analyze(scene,side,width,inflate=0){
 const m=scene.arm(side,width),inv=inverse(m),lengths=[0,1,2].map(i=>Math.hypot(m[i],m[4+i],m[8+i]));
 const axis=norm(direction(m,[0,-1,0])),palm=norm(direction(m,[0,0,1]));
 const positions=Object.fromEntries([['hand_cap',0],['wrist_proxy',4],['midpoint',6],['proximal_end',12]].map(([n,y])=>[n,pt(m,[0,-y/16,0])]));
 // 6 faces x 11x11. Duplicate edges intentional, compatible with old 242-point report.
 let front=0,blocked=0,clipped=0,visible=0;const faceHits=[0,0,0],byGun={};
 const lo=div([-width/2-inflate,-12-inflate,-2-inflate]),hi=div([width/2+inflate,inflate,2+inflate]);
 for(let axis=0;axis<3;axis++)for(const s of[0,1])for(let i=0;i<=10;i++)for(let j=0;j<=10;j++){
  const a=[0,1,2].filter(k=>k!==axis),v=[...lo];v[axis]=s?hi[axis]:lo[axis];v[a[0]]=lo[a[0]]+(hi[a[0]]-lo[a[0]])*i/10;v[a[1]]=lo[a[1]]+(hi[a[1]]-lo[a[1]])*j/10;
  const p=pt(m,v),n=norm([inv[axis*4],inv[axis*4+1],inv[axis*4+2]].map(v=>v*(s?1:-1)));
  if(dot(n,times(p,-1))<=0)continue;front++;faceHits[axis]++;
  const hit=scene.boxes.find(b=>ray(p,b));if(hit){blocked++;byGun[hit.name]=(byGun[hit.name]||0)+1}
  if(!inside(p))clipped++;else if(!hit)visible++;
 }
 // Longitudinal visible-length proxy: any camera-facing surface ray survives at station.
 // Primary ratio uses 0..12 whole-arm stations: Vanilla has no anatomical wrist joint.
 // 4..12 is an explicitly arbitrary wrist proxy, not an anatomical measurement.
 function segment(start,end){
  let proj=0,vis=0,clip=0,occ=0,visibleStations=0;let furthest=null;
  const N=400;
  for(let i=0;i<N;i++){
   const y=start+(end-start)*(i+.5)/N,p=pt(m,[0,-y/16,0]),p0=pt(m,[0,-(start+(end-start)*i/N)/16,0]),p1=pt(m,[0,-(start+(end-start)*(i+1)/N)/16,0]);
   const uv0=project(p0),uv1=project(p1),weight=Math.hypot((uv1[0]-uv0[0])*H*aspect/2,(uv1[1]-uv0[1])*H/2);
   if(p0[2]>=-.05||p1[2]>=-.05)continue;
   proj+=weight;let anyInside=false,anyVisible=false;
   for(const axis of[0,2])for(const s of[0,1])for(let j=0;j<=6;j++){
    const v=[0,-y/16,0],other=axis===0?2:0;v[axis]=s?hi[axis]:lo[axis];v[other]=lo[other]+(hi[other]-lo[other])*j/6;
    const q=pt(m,v),n=norm([inv[axis*4],inv[axis*4+1],inv[axis*4+2]].map(v=>v*(s?1:-1)));
    if(dot(n,times(q,-1))<=0||!inside(q))continue;
    anyInside=true;if(!scene.boxes.some(b=>ray(q,b))){anyVisible=true;break;}
   }
   if(anyVisible){vis+=weight;visibleStations++;furthest=p}else if(anyInside)occ+=weight;else clip+=weight;
  }
  const crossesNear=[start,end].some(y=>pt(m,[0,-y/16,0])[2]>=-.05);
  return {projected_px:crossesNear?null:proj,near_truncated_projected_px:proj,visible_px:vis,gun_occluded_px:occ,viewport_clipped_px:clip,projected_ratio:crossesNear?null:vis/proj,visible_station_ratio:visibleStations/N,near_plane_crossing:crossesNear,furthest_visible_station:furthest};
 }
 const eye=norm(times(pt(m,[0,-6/16,0]),-1)),faceWeights=[0,2].map(i=>Math.abs(dot(norm([inv[i*4],inv[i*4+1],inv[i*4+2]]),eye)));
 return {axis_scale:lengths,physical_dimensions:times([width*lengths[0],12*lengths[1],4*lengths[2]],1/16),locator:scene.locator(side),gun_frame:scene.gunFrame,hand_cap_minus_gun_frame:sub(positions.hand_cap,scene.gunFrame),positions,forearm_axis:axis,palm_normal:palm,inward_skin_side_normal:norm(direction(m,[side==='right'?1:-1,0,0])),forward_dot:-axis[2],face_view_cosines:{four_px_side:faceWeights[0],width_px_front_back:faceWeights[1]},dominant_long_face:faceWeights[0]>faceWeights[1]?'4px-side':width+'px-front/back',
 surface:{front,blocked,gun_occlusion_ratio:blocked/front,viewport_clipped:clipped,visible,byGun},whole_arm:segment(0,12),forearm:segment(4,12)};
}
function digest(p){return crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex')}
const packChecks=['geo_models/gun/glock_17_geo.json','animations/glock_17.animation.json','display/guns/glock_17_display.json'].map(p=>({path:p,run_pack_matches_bundled:fs.readFileSync(path.join(root,'run/tacz/tacz_default_gun/assets/tacz',p)).equals(entries.get(prefix+p)())}));
const result={assumptions:{fov,aspect,height:H,near:.05,stance:'standing right-handed equip=0 ADS=0, loaded standard magazine, no bob/sway/camera animation, opaque gun OBBs; no HUD/arm-vs-arm occlusion',animation:'raw clip numeric channels linearly interpolated; no live state blending; pinned keyframe t=.5 for both',palm_normal:'canonical +Z front/back-plane proxy, NOT anatomical palm; inward_skin_side_normal is local right +X / left -X',primary_visible_ratio:'fraction of 400 whole-arm longitudinal stations with at least one visible facing-surface sample'},source:{jar_sha256:digest(jarPath),afl_geo_sha256:digest(path.join(root,ap+'geo/p9_01.geo.json')),packChecks},tacz_asset:{idle_hand_scale:ta.static_idle.bones.righthand.scale,reload_hand_scale:ta.reload_tactical.bones.righthand.scale,display:td.transform,reload_length:ta.reload_tactical.animation_length,camera_track_present:!!ta.reload_tactical.bones.camera},rows:[]};
for(const[system,fn,states]of[['AFL',aflScene,['ready','reload']],['TaCZ',taczScene,['static_idle','reload_tactical']]])
 for(const state of states){const t=state.includes('reload')?.5:0,scene=fn(state,t);
  for(const side of['right','left'])for(const width of[3,4])result.rows.push({system,state,t,side,width,...analyze(scene,side,width)});
  result.rows.push({system,state,t,side:'right',width:3,sleeve:true,...analyze(scene,'right',3,.25)});
 }
if(process.argv.includes('--timeline'))for(const[system,fn,state]of[['AFL',aflScene,'reload'],['TaCZ',taczScene,'reload_tactical']])for(const t of[.3,.8])result.rows.push({system,state,t,side:'right',width:3,...analyze(fn(state,t),'right',3)});
export {aflScene,analyze,chain,S};
if(process.argv[1] && path.resolve(process.argv[1])===fileURLToPath(import.meta.url)) {
 if(process.argv.includes('--metadata'))console.log(JSON.stringify({...result,rows:[]}));
 else console.log(JSON.stringify(result));
}
