// Offline geometry/UV proof, not a substitute for Minecraft rendering.
import fs from 'node:fs';
import {parts,pixels,png} from './build-crowbar-assets.mjs';
const W=900,H=1000,out=new Uint8Array(W*H*4),depth=new Float64Array(W*H).fill(-Infinity);
for(let y=0;y<H;y++)for(let x=0;x<W;x++){let c=23+Math.round(8*(1-y/H));out.set([c,c+3,c+5,255],(y*W+x)*4);}
function rotate(v,axis,angle){let r=angle*Math.PI/180,c=Math.cos(r),s=Math.sin(r),[x,y,z]=v;return axis==='x'?[x,y*c-z*s,y*s+z*c]:axis==='y'?[x*c+z*s,y,-x*s+z*c]:[x*c-y*s,x*s+y*c,z];}
const sides={north:[[0,0,0],[1,0,0],[1,1,0],[0,1,0]],south:[[1,0,1],[0,0,1],[0,1,1],[1,1,1]],west:[[0,0,1],[0,0,0],[0,1,0],[0,1,1]],east:[[1,0,0],[1,0,1],[1,1,1],[1,1,0]],up:[[0,1,0],[1,1,0],[1,1,1],[0,1,1]],down:[[0,0,1],[1,0,1],[1,0,0],[0,0,0]]};
function render(cx,cy,scale,yaw,roll){
 for(let p of parts)for(let [face,coords] of Object.entries(sides)){
  let vertices=coords.map(v=>{let q=rotate(v.map((n,i)=>(n-.5)*p.size[i]),p.axis,p.angle).map((n,i)=>n+p.center[i]);q=rotate(q,'y',yaw);q=rotate(q,'z',roll);return [cx+q[0]*scale,cy-q[1]*scale,q[2]];});
  let strip=p.material==='red'?({north:1,south:0,east:2,west:3,up:1,down:2}[face]):p.material==='edge'?6:({north:5,south:4,east:7,west:5,up:6,down:4}[face]);
  let vspan=Math.min(63.5,Math.max(8,p.size[1]*2.7)),uv=[[0,vspan],[7.4,vspan],[7.4,0],[0,0]];
  let light=({north:1,south:.8,west:.88,east:.63,up:1.15,down:.55}[face]);
  for(let ids of [[0,1,2],[0,2,3]]){
   let [a,b,c]=ids.map(i=>vertices[i]),den=(b[1]-c[1])*(a[0]-c[0])+(c[0]-b[0])*(a[1]-c[1]);if(Math.abs(den)<.001)continue;
   for(let y=Math.max(0,Math.floor(Math.min(a[1],b[1],c[1])));y<Math.min(H,Math.ceil(Math.max(a[1],b[1],c[1])));y++)for(let x=Math.max(0,Math.floor(Math.min(a[0],b[0],c[0])));x<Math.min(W,Math.ceil(Math.max(a[0],b[0],c[0])));x++){
    let w0=((b[1]-c[1])*(x+.5-c[0])+(c[0]-b[0])*(y+.5-c[1]))/den,w1=((c[1]-a[1])*(x+.5-c[0])+(a[0]-c[0])*(y+.5-c[1]))/den,w2=1-w0-w1;
    if(Math.min(w0,w1,w2)<0)continue;let ws=[w0,w1,w2],z=ws.reduce((s,w,i)=>s+w*vertices[ids[i]][2],0),index=y*W+x;if(z<=depth[index])continue;depth[index]=z;
    let u=strip*8+Math.min(7,Math.max(0,Math.floor(ws.reduce((s,w,i)=>s+w*uv[ids[i]][0],0)))),v=Math.min(63,Math.max(0,Math.floor(ws.reduce((s,w,i)=>s+w*uv[ids[i]][1],0))));
    let ti=(v*64+u)*4;out.set([0,1,2].map(i=>Math.min(255,Math.round(pixels[ti+i]*light))).concat(255),index*4);
   }
  }
 }
}
render(300,800,30,-28,10);
render(635,820,22,48,-18);
fs.mkdirSync('build/crowbar-preview',{recursive:true});
fs.writeFileSync('build/crowbar-preview/crowbar-preview.png',png(W,H,out));
console.log('Saved build/crowbar-preview/crowbar-preview.png');
