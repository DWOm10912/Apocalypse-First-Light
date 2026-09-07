// Offline, untextured geometry/depth diagnostic; NOT Minecraft visual acceptance.
// Amber highlights the temporary magazine; pale grey is the source reference arm.
import fs from 'node:fs';
import {read,sourcePath} from './export-native-gun.mjs';
import {scene,chain,R,T,pt,sample} from './check-native-gun-aimline.mjs';
const s=read(sourcePath),by=new Map(s.groups.map(g=>[g.uuid,g])),owners=new Map();
function walk(nodes,parent){for(const n of nodes)typeof n==='string'?owners.set(n,parent):(walk(n.children,by.get(n.uuid)))}walk(s.outliner,null);
const w=480,h=270,times=[.30,.40,.48,.56,.64,.72],W=w*3,H=h*2;
const pixels=Buffer.alloc(W*H*3,29),zbuf=new Float64Array(W*H).fill(Infinity);
const faces=[[0,2,3,1],[4,5,7,6],[0,1,5,4],[2,6,7,3],[0,4,6,2],[1,3,7,5]];
const sub=(a,b)=>a.map((x,i)=>x-b[i]),cross=(a,b)=>[a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]];
const clip=poly=>{const out=[];for(let i=0;i<poly.length;i++){const a=poly[i],b=poly[(i+1)%poly.length],inside=a[2]<=-.025,next=b[2]<=-.025;if(inside)out.push(a);if(inside!==next){const q=(-.025-a[2])/(b[2]-a[2]);out.push(a.map((v,j)=>v+(b[j]-v)*q))}}return out};
for(let k=0;k<times.length;k++){
 const t=times[k],q=scene(s,'firstperson_righthand','reload_empty',t),ox=k%3*w,oy=Math.floor(k/3)*h;
 const a=s.animations.find(a=>a.name.endsWith('.reload_empty'));
 for(const cube of s.elements){
  if(cube.name.includes('slim'))continue;
  const g=owners.get(cube.uuid);if(!g)continue;
  if(['magazine','empty_old_magazine'].includes(g.name)&&sample(a.animators[g.uuid].keyframes,'scale',t)[0]===0)continue;
  const r=cube.rotation??[0,0,0],o=(cube.origin??[0,0,0]).map(v=>v/16);
  const m=chain(q.matrix(g.name),T(o),R(2,r[2]),R(1,r[1]),R(0,r[0]),T(o.map(v=>-v)));
  const vs=Array.from({length:8},(_,i)=>pt(m,[0,1,2].map(j=>(i&(1<<j)?cube.to[j]:cube.from[j])/16)));
  for(const face of faces){
   const p=clip(face.map(i=>vs[i]));if(p.length<3)continue;
   const n=cross(sub(p[1],p[0]),sub(p[2],p[0])),light=.45+.5*Math.abs(n[1])/Math.hypot(...n);
   const base=g.name==='empty_old_magazine'?[237,170,55]:cube.name.includes('reference')?[128,166,175]:[72,76,81];
   const color=base.map(v=>Math.round(v*light)),f=h/2/Math.tan(35*Math.PI/180);
   const projected=p.map(v=>[w/2+v[0]/-v[2]*f,h/2-v[1]/-v[2]*f,-v[2]]);
   for(let j=1;j<p.length-1;j++){
    const [a,b,c]=[projected[0],projected[j],projected[j+1]],den=(b[1]-c[1])*(a[0]-c[0])+(c[0]-b[0])*(a[1]-c[1]);if(Math.abs(den)<1e-6)continue;
    for(let y=Math.max(0,Math.floor(Math.min(a[1],b[1],c[1])));y<Math.min(h,Math.ceil(Math.max(a[1],b[1],c[1])));y++)for(let x=Math.max(0,Math.floor(Math.min(a[0],b[0],c[0])));x<Math.min(w,Math.ceil(Math.max(a[0],b[0],c[0])));x++){
     const u=((b[1]-c[1])*(x+.5-c[0])+(c[0]-b[0])*(y+.5-c[1]))/den,v=((c[1]-a[1])*(x+.5-c[0])+(a[0]-c[0])*(y+.5-c[1]))/den,l=1-u-v;if(Math.min(u,v,l)<0)continue;
     const z=1/(u/a[2]+v/b[2]+l/c[2]),i=(oy+y)*W+ox+x;if(z>=zbuf[i])continue;zbuf[i]=z;pixels.set(color,i*3);
    }
   }
  }
 }
}
fs.writeFileSync('build/p9-empty-snap-depth.ppm',Buffer.concat([Buffer.from(`P6\n${W} ${H}\n255\n`),pixels]));
console.log({times,diagnostic:'FOV70 / Classic source arm / depth-tested untextured proxy, amber = old magazine'});
