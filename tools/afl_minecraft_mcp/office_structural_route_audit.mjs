// Read-only static collision audit of exact live snapshot; not player-input verification.
import {readFile,writeFile} from 'node:fs/promises';
const dir='build/authoring_checks/office_midrise_01/structural_v1_20260912/';
const s=JSON.parse(await readFile(dir+'after.json','utf8'));
const at=(x,y,z)=>s.palette[s.cells[((y+33)*39+z+112)*35+x+48]];
const boxes=(x,y,z)=>{
 if(x< -48||x> -14||z< -112||z> -74||y< -33||y>52)return [];
 const b=at(x,y,z);if(b==='minecraft:air')return [];
 if(b.includes('_stairs')){const d=b.match(/facing=(\w+)/)[1];return [[x,y,z,x+1,y+.5,z+1],d==='north'?[x,y+.5,z,x+1,y+1,z+.5]:[x,y+.5,z+.5,x+1,y+1,z+1]];}
 if(b.includes('_slab'))return [b.includes('type=top')?[x,y+.5,z,x+1,y+1,z+1]:[x,y,z,x+1,y+.5,z+1]];
 return [[x,y,z,x+1,y+1,z+1]];
};
const path=[];
function move(x,z){if(!path.length){path.push([x,z]);return;}const [a,b]=path.at(-1),n=Math.ceil(Math.hypot(x-a,z-b)/.1);for(let i=1;i<=n;i++)path.push([a+(x-a)*i/n,b+(z-b)*i/n]);}
move(-31.5,-75.5);move(-31.5,-90.5);
const checkPoints=[];
for(let f=0;f<5;f++){
 move(-29.5,-90.5);move(-29.5,-99.5);move(-24.5,-99.5);move(-24.5,-93.5);
 // Roof doorway is offset west from the return flight; turn on full-width landing.
 if(f===4){move(-27.5,-93.5);move(-27.5,-90.5);}else move(-24.5,-90.5);
 checkPoints.push(path.length-1);
}
const upwardLength=path.length;path.push(...path.slice(0,-1).reverse());
let foot=-32,maxStep=0,minClear=Infinity;const errors=[],heights=[];
for(let i=0;i<path.length;i++){
 const [x,z]=path[i],near=[];for(let by=Math.floor(foot)-2;by<=Math.floor(foot)+3;by++)for(let bz=Math.floor(z-.3);bz<=Math.floor(z+.3);bz++)for(let bx=Math.floor(x-.3);bx<=Math.floor(x+.3);bx++)near.push(...boxes(bx,by,bz));
 const footprint=b=>b[0]<x+.3-1e-6&&b[3]>x-.3+1e-6&&b[2]<z+.3-1e-6&&b[5]>z-.3+1e-6;
 const support=near.filter(b=>footprint(b)&&b[4]<=foot+.60001&&b[4]>=foot-.60001).map(b=>b[4]);
 if(!support.length){errors.push({i,x,z,foot,error:'NO_STEP_SUPPORT'});break;}
 const next=Math.max(...support);maxStep=Math.max(maxStep,Math.abs(next-foot));foot=next;
 const hits=near.filter(b=>footprint(b)&&b[1]<foot+1.8-1e-6&&b[4]>foot+1e-6);
 if(hits.length){errors.push({i,x,z,foot,error:'BODY_COLLISION',hits});break;}
 const overhead=near.filter(b=>footprint(b)&&b[1]>=foot).map(b=>b[1]-foot);if(overhead.length)minClear=Math.min(minClear,...overhead);
 if(checkPoints.includes(i))heights.push(foot);
}
const result={kind:'STATIC_AABB_ROUTE_NOT_ACTUAL_PLAYER_WALK',samples:path.length,upward_samples:upwardLength,completed:!errors.length,maximum_step:maxStep,minimum_sampled_overhead:minClear,landings:heights,expected_landings:[-25,-19,-13,-7,-1],errors};
await writeFile(dir+'route_audit.json',JSON.stringify(result,null,2));console.log(JSON.stringify(result));
