// Read-only, version-pinned READY geometry audit. Not a renderer or visual PASS.
// Run from the repository root: node tools/audit-native-gun-ready.mjs
// No files written, no client launched, no screenshots. Re-audit on contract changes.
import {readFileSync} from 'node:fs';
import assert from 'node:assert/strict';
const client='src/main/java/com/antaurora/apofirstlight/weapon/client/';
const resources='src/main/resources/assets/apocalypse_firstlight/';
const json=p=>JSON.parse(readFileSync(p,'utf8').replace(/^\uFEFF/,''));
const display=json(resources+'models/item/service_pistol_in_hand.json').display.firstperson_righthand;
assert.deepEqual(display.translation,[-8.75,-.25,0]);
assert.deepEqual(display.scale,[.5,.5,.5]);
assert.deepEqual(display.rotation||[0,0,0],[0,0,0]);
const presentation=readFileSync(client+'ServicePistolPresentation.java','utf8');
for(const [key,value] of Object.entries({BASE_X:.51,BASE_Y:-.44,BASE_Z:-.70,BASE_SCALE:.82,BASE_PITCH:-4,BASE_YAW:-4})) {
  const match=presentation.match(new RegExp(key+'\\s*=\\s*([-0-9.]+)F'));
  assert.equal(Number(match?.[1]),value,'Re-audit changed presentation: '+key);
}
assert.match(readFileSync(client+'ServicePistolRenderer.java','utf8'), /"gun_model_root", "right_hand_anchor", "left_hand_anchor", \.8F/);
assert.match(readFileSync(client+'NativePlayerArmRenderer.java','utf8'), /PLAYER_ARM_SCALE = \.246F/);
assert.match(readFileSync(client+'NativeHandBinding.java','utf8'), /DISTAL_Y = 10F/);
const geometry=json(resources+'geo/service_pistol.geo.json')['minecraft:geometry'][0].bones;
assert.equal(geometry.reduce((n,b)=>n+(b.cubes||[]).length,0),77);
for(const b of geometry) {
  assert.ok(!b.neverRender && !b.inflate,'Unsupported bone: '+b.name);
  for(const c of b.cubes||[]) assert.ok(!c.inflate,'Unsupported cube inflation');
}
// Canonical arm bounds already include B_skin centering of Vanilla Classic/Slim.
// GeoFactory reflection: pivot.x negated; cube origin.x=-(origin.x+size.x);
// rotation=(-Rx,-Ry,+Rz), then Z/Y/X as confirmed from GeckoLib 4.7.4 bytecode.
// Ray tests assume opaque cube volumes. Texture alpha, HUD, arm self-occlusion,
// bob/equip/animation, rasterization and actual GPU state are NOT simulated.
// 11x11 grid per face includes duplicate edge points; ratios are NOT pixel coverage.
const load=()=>geometry.map(b=>({...b,cubes:b.cubes||[]}));
const text=value=>console.log(JSON.stringify(value,null,2));

const I=()=>[1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1];
const mul=(a,b)=>Array.from({length:16},(_,n)=>{let r=n%4,c=Math.floor(n/4);return [0,1,2,3].reduce((s,k)=>s+a[k*4+r]*b[c*4+k],0)});
const T=(v)=>{let a=I();v.forEach((x,i)=>a[12+i]=x);return a};
const S=(s)=>{let a=I();[0,5,10].forEach(i=>a[i]=s);return a};
const R=(axis,deg)=>{let a=I(),c=Math.cos(deg*Math.PI/180),s=Math.sin(deg*Math.PI/180);let j=(axis+1)%3,k=(axis+2)%3;a[j*4+j]=a[k*4+k]=c;a[j*4+k]=s;a[k*4+j]=-s;return a};
const chain=(...args)=>args.reduce(mul,I());
const pt=(a,v)=>[0,1,2].map(r=>a[12+r]+a[r]*v[0]+a[4+r]*v[1]+a[8+r]*v[2]);
const neg=v=>v.map(x=>-x);
const convert=v=>[-v[0],v[1],v[2]];
const rotate=v=>chain(R(2,v[2]),R(1,-v[1]),R(0,-v[0]));
const inverse=a=>{let r=I();let s2=a[0]**2+a[1]**2+a[2]**2;for(let i=0;i<3;i++)for(let j=0;j<3;j++)r[i*4+j]=a[j*4+i]/s2;let t=pt(r,neg(a.slice(12,15)));for(let i=0;i<3;i++)r[12+i]=t[i];return r};
const bones=load("audit_geo_small"),by=new Map(bones.map(b=>[b.name,b]));
const bm=(n)=>{if(!n)return I();let b=by.get(n),p=convert(b.pivot).map(v=>v/16);return chain(bm(b.parent),T(p),rotate(b.rotation||[0,0,0]),S(n==="gun_model_root"?.8:1),T(neg(p)))};
const base=chain(T([.51,-.44,-.70]),R(1,-4),R(0,-4),S(.82),T([-8.75/16,-.25/16,0]),S(.5),T([0,.01,0]));
const loc=chain(base,bm("right_hand_anchor"),T(convert(by.get("right_hand_anchor").pivot).map(v=>v/16)));
const arm=loc.slice();let len=Math.hypot(loc[0],loc[1],loc[2]);for(let c=0;c<3;c++)for(let r=0;r<3;r++)arm[c*4+r]*=.246/len;
const boxes=[];
for(const b of bones) for(const cube of b.cubes) {
 const p=convert(cube.pivot||[0,0,0]).map(v=>v/16);
 const m=chain(base,bm(b.name),T(p),rotate(cube.rotation||[0,0,0]),T(neg(p)));
 const lo=[-(cube.origin[0]+cube.size[0]),cube.origin[1],cube.origin[2]].map(v=>v/16),hi=lo.map((v,i)=>v+cube.size[i]/16);
 boxes.push({name:b.name,lo,hi,inv:inverse(m)});
}
const hit=(point,box)=>{let o=pt(box.inv,[0,0,0]),d=pt(box.inv,point).map((v,i)=>v-o[i]),enter=0,leave=1;
 for(let i=0;i<3;i++){if(Math.abs(d[i])<1e-12){if(o[i]<box.lo[i]||o[i]>box.hi[i])return false;}else{let a=(box.lo[i]-o[i])/d[i],b=(box.hi[i]-o[i])/d[i];enter=Math.max(enter,Math.min(a,b));leave=Math.min(leave,Math.max(a,b));}if(enter>leave)return false;}return leave>0&&enter<.99999};
const output=[];
for(const width of [4,3])for(const inflate of [0,.25]){let points=[],occluded=0,viewport=0,visible=0,front=0,frontBlocked=0,names={};for(let axis=0;axis<3;axis++)for(const side of[0,1])for(let i=0;i<=10;i++)for(let j=0;j<=10;j++){
 let lo=[-width/2-inflate,-12-inflate,-2-inflate],hi=[width/2+inflate,inflate,2+inflate],v=lo.slice();v[axis]=side?hi[axis]:lo[axis];let axes=[0,1,2].filter(k=>k!==axis);v[axes[0]]=lo[axes[0]]+(hi[axes[0]]-lo[axes[0]])*i/10;v[axes[1]]=lo[axes[1]]+(hi[axes[1]]-lo[axes[1]])*j/10;let p=pt(arm,v.map(n=>n/16));points.push(p);
 let tan=Math.tan(35*Math.PI/180),inside=p[2]<-.05&&Math.abs(p[1]/(-p[2]*tan))<=1&&Math.abs(p[0]/(-p[2]*tan*16/9))<=1;
 let normal=[0,1,2].map(r=>arm[axis*4+r]*(side?1:-1));let facing=normal.reduce((s,v,k)=>s-v*p[k],0)>0;if(facing)front++;
 if(inside)viewport++;let h=boxes.find(b=>hit(p,b));if(h&&facing)frontBlocked++;if(h){occluded++;names[h.name]=(names[h.name]||0)+1;}else if(inside)visible++;
 }output.push({width,inflate,frontFacingSamples:front,frontFacingBlocked:frontBlocked,samples:points.length,inViewport:viewport,occludedByGun:occluded,notOccludedInViewport:visible,occluders:names,bounds:[0,1,2].map(k=>[Math.min(...points.map(p=>p[k])),Math.max(...points.map(p=>p[k]))])})}
text({assumptions:"READY no bob/equip; 70deg vertical FOV 16:9; opaque gun volumes; surface-grid/frontface counts are NOT pixel coverage or visual acceptance.",locator:loc.slice(12,15),output});

