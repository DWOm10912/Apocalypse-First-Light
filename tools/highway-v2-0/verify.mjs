import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {build,writeOrCheck,root,demo} from './generate.mjs';
import {createRoad,sub,cross,unit} from './geometry.mjs';
import {meshCounts} from '../export-afl-mesh.mjs';
assert(process.argv.slice(2).every(x=>x==='--check'),'Usage: node verify.mjs [--check]');
const first=build(),second=build();
for(const [name,data] of first.files)assert(Buffer.from(data).equals(Buffer.from(second.files.get(name))),'nondeterministic '+name);
writeOrCheck(first.files,true);
const {p,modules}=first,road=createRoad(p),loaded=modules.map(m=>JSON.parse(first.files.get(m.mesh)));
assert.equal(road.halfWidth*2,38);
assert.equal(road.frame(156).y-road.frame(136).y,1,'20 m / 1 m grade demonstration');
assert.equal(road.frame(p.length).y,4);
let minArea=Infinity,minUvDet=Infinity,upwardMin=1,storedVertices=0,triangles=0,quads=0,triangleEquivalent=0;
for(const model of loaded) {
  assert.equal(model.format_version,2);assert.equal(model.coordinate_space,'bone_pivot_local_blocks');
  assert(model.parts.length<=128);const count=meshCounts(model);assert(count.triangleEquivalent<=16384);
  triangles+=count.triangles;quads+=count.quads;triangleEquivalent+=count.triangleEquivalent;
  for(const part of model.parts) {
    storedVertices+=part.vertices.length;
    for(const v of part.vertices){assert(v.length===5&&v.every(Number.isFinite));assert(v.slice(0,3).every(x=>Math.abs(x)<=256));assert(v.slice(3).every(x=>x>=0&&x<=1));}
    for(const f of part.faces){
      assert([3,4].includes(f.length));assert(new Set(f).size===f.length&&f.every(i=>Number.isInteger(i)&&i>=0&&i<part.vertices.length));
      const vertices=f.map(i=>part.vertices[i]),tris=f.length===4?[[0,1,2],[2,3,0]]:[[0,1,2]];
      // Source-aware quad classification already ran during byte-identical regeneration.
      // Float quantization recovery is checked by the real loader, not the older preservedQuad helper.
      for(const ids of tris){const [a,b,c]=ids.map(i=>vertices[i].map(Math.fround)),ab=sub(b.slice(0,3),a.slice(0,3)),ac=sub(c.slice(0,3),a.slice(0,3)),n=cross(ab,ac),area=Math.hypot(...n)/2;
        const det=(b[3]-a[3])*(c[4]-a[4])-(c[3]-a[3])*(b[4]-a[4]);
        assert(area>1e-9&&Math.abs(det)>1e-10,'degenerate geometry/UV');minArea=Math.min(minArea,area);minUvDet=Math.min(minUvDet,Math.abs(det));upwardMin=Math.min(upwardMin,unit(n)[1]);
      }
    }
  }
}
assert(upwardMin>.99,'winding must be upward');
// Check actual exported boundary vertices after the Java float conversion, not just source samples.
const seamChecks=[];
for(let i=0;i+1<modules.length;i++) {
  const a=modules[i],b=modules[i+1],station=a.end;
  const worldVertices=(model,m)=>model.parts.filter(p=>!['white','yellow'].includes(p.name)).flatMap(p=>p.vertices.map(v=>v.slice(0,3).map((x,k)=>Math.fround(x)+m.origin[k])));
  const av=worldVertices(loaded[i],a),bv=worldVertices(loaded[i+1],b);
  let maxGap=0,maxError=0;
  for(const expected of a.seams.end){
    const nearest=vs=>vs.reduce((a,v)=>Math.hypot(...sub(v,expected.position))<Math.hypot(...sub(a,expected.position))?v:a,vs[0]);
    const aa=nearest(av),bb=nearest(bv);maxGap=Math.max(maxGap,Math.hypot(...sub(aa,bb)));maxError=Math.max(maxError,Math.hypot(...sub(aa,expected.position)),Math.hypot(...sub(bb,expected.position)));
  }
  assert(maxError<1e-5&&maxGap<1e-5,'exported seam mismatch');
  assert.deepEqual(a.seams.end,b.seams.start,'analytic frame/normal join');
  seamChecks.push({station,maxFloat32WorldPositionGap:maxGap,maxFloat32SurfaceError:maxError,realChunkPlane:station===64});
}
// For the exact x=64 chunk seam, independently compare complete material/position/U/periodic-V edge sets.
const edge=(model,m)=>new Set(model.parts.flatMap(part=>part.vertices.filter(v=>Math.abs(v[0]+m.origin[0]-64)<1e-8).map(v=>part.name+'|'+v.slice(0,3).map((x,k)=>(x+m.origin[k]).toFixed(7)).join(',')+'|'+v[3].toFixed(7)+'|'+(((v[4]-.0625)/.875)%1).toFixed(7))));
assert.deepEqual(edge(loaded[0],modules[0]),edge(loaded[1],modules[1]),'chunk-plane topology/material/periodic UV seam');
let maxGrade=0,maxCurvature=0,maxJoinHeadingJump=0;
for(let s=0;s<=p.length;s+=.25){const f=road.frame(s);maxGrade=Math.max(maxGrade,f.grade);maxCurvature=Math.max(maxCurvature,f.curvature);assert(Math.abs(Math.hypot(...road.normal(s,10))-1)<1e-9);
  if(s>0&&s<p.length){const h=.0001,a=road.frame(s-h),b=road.frame(s+h);
    assert(Math.abs((b.y-a.y)/(2*h)-f.grade)<1e-7,'profile derivative does not match grade');
    assert(Math.abs((b.heading-a.heading)/(2*h)-f.curvature)<1e-7,'heading derivative does not match curvature');
    assert(Math.abs(Math.hypot(b.x-a.x,b.z-a.z)/(2*h)-1)<1e-6,'station is not horizontal arc length');
  }
}
for(const s of [64,96,136,176,192,216,224]){const a=road.frame(s-1e-5),b=road.frame(s+1e-5);maxJoinHeadingJump=Math.max(maxJoinHeadingJump,Math.abs(a.heading-b.heading));assert(Math.abs(a.grade-b.grade)<1e-7);}
assert(maxGrade<=.05+1e-12&&maxCurvature<=1/600+1e-12&&maxJoinHeadingJump<1e-7);
const result={status:'PASS_OFFLINE_GEOMETRY',routeLength:256,width:38,moduleCount:4,storedVertices,triangles,quads,triangleEquivalent,
  exporterSubmittedCorners:4*(triangles+quads),minTriangleAreaFloat32:minArea,minUvDetFloat32:minUvDet,minUpwardNormalY:upwardMin,
  maxGrade,maxCurvature,minRadius:1/maxCurvature,maxJoinHeadingJump,seamChecks,
  repeatability:'two independent builds byte-identical; checked against all committed generated files',
  format:'unchanged existing exporter format_version=2',javaLoader:'Run verify-loader.mjs separately',
  notVerified:['Minecraft client','chunk world generation','UV appearance in Minecraft','PBR/Oculus/Embeddium','collision/vehicles','runtime frame time or draw calls','full civil-engineering design-speed compliance']};
const report=JSON.stringify(result,null,2)+'\n',output=path.join(root,demo,'validation.json');
if(process.argv.includes('--check'))assert.equal(fs.readFileSync(output,'utf8'),report,'stale validation.json');else fs.writeFileSync(output,report);
console.log(report);
