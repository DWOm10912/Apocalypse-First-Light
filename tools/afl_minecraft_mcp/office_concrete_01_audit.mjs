// Readback + conservative floor connectivity + live player-AABB clearance.
// This is not a keyboard-driven Survival stair traversal or elevator test.
import {readFile,writeFile} from 'node:fs/promises';
import {BridgeClient} from './bridge_client.mjs';
import {recipe,FLOORS,ROOF} from './office_concrete_01.mjs';
const dir='build/authoring_checks/office_concrete_01/',r=JSON.parse(await readFile(dir+'plan.json','utf8'));
const a=JSON.parse(await readFile(dir+'final_after.json','utf8')),idx=(x,y,z)=>(y*23+z)*29+x;
const at=(x,y,z)=>a.cells[idx(x,y,z)],air='minecraft:air';
const floorAudit=[];let multipartErrors=0,lightSupportErrors=0;
for(const d of r.desks){const dx=d.facing==='north'?1:-1;for(const [o,part] of [[-1,'left'],[0,'center'],[1,'right']])if(at(d.x+o*dx,d.y,d.z)!==`apocalypse_firstlight:modern_office_desk[facing=${d.facing},part=${part}]`)multipartErrors++;if(d.computer&&!at(d.x,d.y+1,d.z).includes('office_computer_station'))multipartErrors++;}
for(let y=0;y<70;y++)for(let z=0;z<23;z++)for(let x=0;x<29;x++){
 const s=at(x,y,z);if(!s.includes('industrial_utility_light'))continue;
 const f=s.match(/facing=([^\]]+)/)[1],offset={down:[0,1,0],east:[-1,0,0],south:[0,0,-1]}[f];
 if(!offset||!/concrete|quartz/.test(at(x+offset[0],y+offset[1],z+offset[2])))lightSupportErrors++;
}
for(let j=0;j<9;j++){
 const f=FLOORS[j],next=FLOORS[j+1]??ROOF;
 const clear=(x,z)=>x>=3&&x<=25&&z>=3&&z<=17&&at(x,f+1,z)===air&&at(x,f+2,z)===air&&/concrete|quartz/.test(at(x,f,z))&&!at(x,f,z).includes('stairs');
 const q=[[20,12]],seen=new Set(['20,12']);for(let n=0;n<q.length;n++){const [x,z]=q[n];for(const [dx,dz] of [[1,0],[-1,0],[0,1],[0,-1]]){const X=x+dx,Z=z+dz,k=[X,Z].join(',');if(!seen.has(k)&&clear(X,Z)){seen.add(k);q.push([X,Z]);}}}
 const goals=r.destinations.filter(g=>g.y===f+1),missing=goals.filter(g=>!seen.has([g.x,g.z].join(',')));
 floorAudit.push({floor:j+1,world_slab_y:f-33,clear_height:next-f-1,reachable_clear_cells:seen.size,missing_destinations:missing});
}
const permutations=[];
for(let seed=1;seed<=20;seed++){
 const p=recipe(seed,true),base=recipe();let structuralChanges=0;
 // Fixed envelope/core/floors remain identical across seeded furnishings.
 for(let y=0;y<70;y++)for(let z=0;z<23;z++)for(let x=0;x<29;x++)if(x<=2||x>=26||z<=2||z>=18||FLOORS.includes(y)||y>=64||x>=19&&z<=12)if(p.cells[idx(x,y,z)]!==base.cells[idx(x,y,z)])structuralChanges++;
 let unreachableDestinations=0;
 for(const f of FLOORS){const get=(x,y,z)=>p.cells[idx(x,y,z)],q=[[20,12]],seen=new Set(['20,12']);
  for(let n=0;n<q.length;n++){const[x,z]=q[n];for(const[dx,dz]of[[1,0],[-1,0],[0,1],[0,-1]]){const X=x+dx,Z=z+dz,k=[X,Z].join(',');if(X<3||X>25||Z<3||Z>17||seen.has(k))continue;if(get(X,f+1,Z)===air&&get(X,f+2,Z)===air&&/concrete|quartz/.test(get(X,f,Z))&&!get(X,f,Z).includes('stairs')){seen.add(k);q.push([X,Z]);}}}
  unreachableDestinations+=p.destinations.filter(g=>g.y===f+1&&!seen.has([g.x,g.z].join(','))).length;
 }
 permutations.push({seed,structuralChanges,unreachableDestinations,templates:p.floorFunctions.map(v=>v.template)});
}
const result={readback_mismatches:a.cells.filter((s,i)=>s!==r.cells[i]).length,multipartErrors,lightSupportErrors,floorAudit,permutations,height:70,notes:['Conservative full-cell BFS for furniture access; not full navmesh.','No working elevator, loot, random worldgen hook, or Survival mining tests.']};
if(process.argv.includes('--live')){
 const c=new BridgeClient('./run'),s=await c.call('minecraft_status'),plot=await c.call('authoring_info');
 if(s.world_session!=='dc8e97dc-0438-4667-97e5-79dad371ba00'||plot.id!=='office_concrete_01')throw Error('WORLD_PLOT_CHANGED');
 const poses=[];
 function add(x,y,z,note){poses.push({position:[x+80,y-33,z-464],yaw:180,pitch:0,note});}
 for(let j=0;j<9;j++){
  const f=FLOORS[j],rise=(FLOORS[j+1]??ROOF)-f;
  for(let k=1;k<=4;k++){add(20.8,f+k+.5,11-k+.82,`${j+1}F flight1 lower ${k}`);add(20.8,f+k+1,11-k+.35,`${j+1}F flight1 upper ${k}`);}
  for(const x of [20.8,21.8,22.8,23.8])add(x,f+5,5.5,`${j+1}F landing`);
  for(let k=5;k<=rise;k++){add(23.8,f+k+.5,k+2+.18,`${j+1}F flight2 lower ${k}`);add(23.8,f+k+1,k+2+.65,`${j+1}F flight2 upper ${k}`);}
  for(const x of [20.8,21.8,22.8,23.8])add(x,f+rise+1,11.5,`${j+1}F top return`);
 }
 const failures=[];for(const p of poses){try{await c.call('camera_move',{position:p.position,yaw:p.yaw,pitch:p.pitch,dry_run:true});}catch(e){failures.push({...p,error:e.message});}}
 result.liveClearance={samples:poses.length,failures,method:'Server level.noCollision using actual player AABB; dry_run only; 0 movements.'};
}
await writeFile(dir+'audit.json',JSON.stringify(result,null,2));
console.log(JSON.stringify(result));
if(result.readback_mismatches||multipartErrors||lightSupportErrors||floorAudit.some(f=>f.missing_destinations.length)||permutations.some(p=>p.structuralChanges||p.unreachableDestinations)||result.liveClearance?.failures.length)process.exitCode=1;
