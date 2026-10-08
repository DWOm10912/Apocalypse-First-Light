// Pure offline road recipe. It does not import Minecraft, read terrain, or publish a RouteGraph.
import assert from 'node:assert/strict';
export const sub=(a,b)=>a.map((x,i)=>x-b[i]);
export const cross=(a,b)=>[a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]];
export const unit=a=>a.map(x=>x/Math.hypot(...a));
const clamp=x=>Math.max(0,Math.min(1,x));
const smooth=x=>{x=clamp(x);return x*x*(3-2*x);};
const integralSmooth=x=>{x=clamp(x);return x*x*x-0.5*x*x*x*x;};
export function createRoad(p) {
  for(const k of ['length','stationStep','moduleLength','laneWidth','outsideShoulder','insideShoulder','medianUnpaved','textureRepeat']) assert(Number.isFinite(p[k])&&p[k]>0,k);
  assert(p.length<=512&&p.stationStep>=0.25&&p.textureSize===128,'bounded offline demo budget');
  assert(Number.isFinite(p.curve.radius)&&p.curve.radius>0&&Number.isFinite(p.curve.bank));
  assert(p.curve.start>=0&&p.curve.transition>0&&p.curve.end<=p.length);
  assert(p.vertical.start>=0&&p.vertical.transition>0&&p.vertical.plateau>=0&&Number.isFinite(p.vertical.grade));
  assert(p.vertical.start+2*p.vertical.transition+p.vertical.plateau<=p.length);
  assert(p.markingWidth>0&&p.markingWidth<Math.min(p.insideShoulder,p.laneWidth));
  assert(p.lanesPerDirection===2,'This demonstrator implements the four-lane section only');
  assert.equal(p.length%p.moduleLength,0);
  assert.equal(p.moduleLength%p.stationStep,0);
  assert.equal(p.moduleLength%p.textureRepeat,0,'module boundary must retain the texture tile phase');
  assert.equal(p.textureRepeat%p.stationStep,0);
  assert.equal(p.dashOn%p.stationStep,0);
  assert.equal(p.dashPeriod%p.stationStep,0);
  assert(p.curve.end-p.curve.start>=2*p.curve.transition);
  const m=p.medianUnpaved/2,inner=m+p.insideShoulder,outer=inner+2*p.laneWidth,half=outer+p.outsideShoulder;
  const {start:a,transition:t,end:b,radius:R}=p.curve;
  const curvatureWeight=s=>smooth((s-a)/t)*smooth((b-s)/t);
  function heading(s) {
    if(s<=a)return 0;
    if(s<a+t)return t*integralSmooth((s-a)/t)/R;
    if(s<=b-t)return (t/2+s-a-t)/R;
    if(s<b){const q=(s-(b-t))/t;return (t/2+b-a-2*t+t*(q-integralSmooth(q)))/R;}
    return (b-a-t)/R;
  }
  // Simpson integration from a fixed station origin: window order never changes the result.
  // Not accumulation from a previous mesh endpoint; samples in any module are reproducible.
  function horizontal(s) {
    if(s<=a)return [s,0];
    const n=Math.max(2,Math.ceil((s-a)/0.25/2)*2),h=(s-a)/n;
    let x=0,z=0;
    for(let i=0;i<=n;i++){const k=i===0||i===n?1:i%2?4:2,angle=heading(a+i*h);x+=k*Math.cos(angle);z+=k*Math.sin(angle);}
    return [a+x*h/3,z*h/3];
  }
  const {start:vs,transition:vt,plateau:vp,grade:g}=p.vertical;
  function vertical(s) {
    let q=s-vs;
    if(q<=0)return {y:0,grade:0};
    if(q<vt)return {y:g*q*q/(2*vt),grade:g*q/vt};
    q-=vt;
    if(q<vp)return {y:g*vt/2+g*q,grade:g};
    q-=vp;
    if(q<vt)return {y:g*vt/2+g*vp+g*q-g*q*q/(2*vt),grade:g*(1-q/vt)};
    return {y:g*(vt+vp),grade:0};
  }
  const cache=new Map();
  function frame(s) {
    if(!cache.has(s)) { const [x,z]=horizontal(s),v=vertical(s); cache.set(s,{s,x,z,...v,heading:heading(s),curvature:curvatureWeight(s)/R,bankWeight:curvatureWeight(s)}); }
    return cache.get(s);
  }
  function surface(s,u) {
    const f=frame(s),w=f.bankWeight;
    const crown=Math.abs(u)<=m ? -0.3*(1-Math.abs(u)/m) : -p.normalCrossfall*(Math.abs(u)-m);
    return [f.x-Math.sin(f.heading)*u,f.y+(1-w)*crown+w*p.curve.bank*u,f.z+Math.cos(f.heading)*u];
  }
  // Analytic centerline + finite difference surface normal, for offline inspection only.
  // One-sided normal at the median crease; not a registered vehicle physics query.
  function normal(s,u) {
    const h=0.0001,lo=Math.max(0,s-h),hi=Math.min(p.length,s+h);
    const ds=sub(surface(hi,u),surface(lo,u)),du=sub(surface(s,u+h),surface(s,u-h));
    return unit(cross(du,ds));
  }
  const bands=[{name:'median_left',a:-m,b:0,material:'median'},{name:'median_right',a:0,b:m,material:'median'}];
  for(const sign of [-1,1]) {
    const side=sign<0?'minus':'plus';
    for(const [name,lo,hi,material] of [['inside_shoulder',m,inner,'shoulder'],['lane_inner',inner,inner+p.laneWidth,'asphalt'],['lane_outer',inner+p.laneWidth,outer,'asphalt'],['outside_shoulder',outer,half,'shoulder']])
      bands.push({name:side+'_'+name,a:Math.min(sign*lo,sign*hi),b:Math.max(sign*lo,sign*hi),material});
  }
  bands.sort((a,b)=>a.a-b.a);
  const markings=[];
  for(const sign of [-1,1])for(const [u,material,dashed] of [[inner,'yellow',false],[inner+p.laneWidth,'white',true],[outer,'white',false]])
    markings.push({u:sign*u,material,dashed});
  return {frame,surface,normal,bands,markings,halfWidth:half};
}
