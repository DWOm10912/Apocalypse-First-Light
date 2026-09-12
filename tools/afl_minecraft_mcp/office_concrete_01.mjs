// New office V1: live authoring only. No NBT export, registry changes or save-file edits.
import {BridgeClient} from './bridge_client.mjs';
import {mkdir, readFile, writeFile} from 'node:fs/promises';
import path from 'node:path';
export const ID='office_concrete_01', ORIGIN=[80,-33,-464], SIZE=[29,70,23];
const EPOCH='dc8e97dc-0438-4667-97e5-79dad371ba00';
export const FLOORS=[0,8,15,22,29,36,43,50,57], ROOF=64;
const dir=path.resolve('build/authoring_checks',ID), c=new BridgeClient('./run');
const air='minecraft:air', index=(x,y,z)=>(y*23+z)*29+x;
const canon=s=>s.replace(/^Block\{([^}]+)\}/,'$1').replace(/\[([^\]]+)\]/,(_,p)=>'['+p.split(',').sort().join(',')+']');
const abs=(x,y,z)=>[x+80,y-33,z-464];
async function save(n,v){await mkdir(dir,{recursive:true});await writeFile(path.join(dir,n+'.json'),JSON.stringify(v));}
async function load(n){return JSON.parse(await readFile(path.join(dir,n+'.json'),'utf8'));}
async function absent(n){try{await load(n);throw Error('ALREADY_EXISTS '+n);}catch(e){if(e.code!=='ENOENT')throw e;}}
async function guard(){const s=await c.call('minecraft_status'),a=await c.call('authoring_info');if(s.world_session!==EPOCH||s.world!=='新的世界'||s.dimension!=='minecraft:overworld'||a.id!==ID||JSON.stringify(a.min)!==JSON.stringify(ORIGIN)||JSON.stringify(a.max)!=='[108,36,-442]')throw Error('WORLD_PLOT_CHANGED');return{s,a};}
async function snapshot(n){await guard();const cells=[];for(let y=0;y<70;y++){const r=await c.call('get_horizontal_slice',{target:'AUTHORING_SESSION',coordinate:y-33,encoding:'palette'}),p=Object.fromEntries(Object.entries(r.palette).map(([s,i])=>[i,canon(s)]));for(let z=0;z<23;z++)for(let x=0;x<29;x++){const s=p[r.rows[z][x]];if(!s)throw Error('SLICE');cells[index(x,y,z)]=s;}}const r={origin:ORIGIN,size:SIZE,epoch:EPOCH,cells,count:await c.call('inspect_selection',{target:'AUTHORING_SESSION'})};await save(n,r);return r;}

export function recipe(seed=91012,randomFloors=false){
 const cells=Array(29*70*23).fill(air),stages={},desks=[],destinations=[],floorFunctions=[];
 let rng=seed>>>0;const random=()=>{rng=(Math.imul(rng,1664525)+1013904223)>>>0;return rng/4294967296;};
 if(randomFloors)for(let i=0;i<8;i++)random(); // Mix nearby seeds before selecting floor families.
 const b=(x,y,z,X,Y,Z,s)=>{if(x>X||y>Y||z>Z||x<0||X>28||y<0||Y>69||z<0||Z>22)throw Error('BOUNDS '+[x,y,z,X,Y,Z]);s=canon(s.includes(':')?s:'minecraft:'+s);for(let j=y;j<=Y;j++)for(let k=z;k<=Z;k++)for(let i=x;i<=X;i++)cells[index(i,j,k)]=s;};
 const p=(x,y,z,s)=>b(x,y,z,x,y,z,s), get=(x,y,z)=>cells[index(x,y,z)];
 const afl=(id,props='')=>'apocalypse_firstlight:'+id+(props?'['+props+']':'');
 const walls=(x,y,z,X,Y,Z,s)=>{b(x,y,z,X,Y,z,s);b(x,y,Z,X,Y,Z,s);b(x,y,z+1,x,Y,Z-1,s);b(X,y,z+1,X,Y,Z-1,s);};
 const plant=(x,y,z)=>{p(x,y,z,'light_gray_concrete');p(x,y+1,z,'azalea_leaves[persistent=true,distance=1,waterlogged=false]');};
 const two=(x,y,z,id,facing='south')=>{for(const [dy,half] of [[0,'lower'],[1,'upper']])p(x,y+dy,z,afl(id,`facing=${facing},half=${half}`));};
 const chair=(x,y,z,facing)=>p(x,y,z,afl('modern_office_chair',`facing=${facing}`));
 const desk=(x,f,z,computer=true,facing='north')=>{
  const dx=facing==='north'?1:-1;for(const [off,part] of [[-1,'left'],[0,'center'],[1,'right']])p(x+off*dx,f+1,z,afl('modern_office_desk',`facing=${facing},part=${part}`));
  if(computer)p(x,f+2,z,afl('office_computer_station',`facing=${facing},lowered=true`));
  chair(x,f+1,z+(facing==='north'?-1:1),facing==='north'?'south':'north');
  desks.push({x,y:f+1,z,facing,computer});destinations.push({x,y:f+1,z:z+(facing==='north'?-2:2),kind:'desk_access'});
 };
 const low=(x,f,z,face='south')=>p(x,f+1,z,afl('low_filing_cabinet',`facing=${face}`));
 const panel=(x,f,z,X,Z)=>{for(let k=z;k<=Z;k++)for(let i=x;i<=X;i++)p(i,f+1,k,afl('office_cubicle_partition',`east=${i<X},north=${k>z},south=${k<Z},west=${i>x}`));};
 const glassWall=(x,f,z,X,Z)=>{b(x,f+1,z,X,f+1,Z,'light_gray_concrete');b(x,f+2,z,X,f+4,Z,'gray_stained_glass');};
 const gap=(x,f,z,X,Z)=>b(x,f+1,z,X,f+3,Z,'air');
 const stationFront=(f)=>{for(const x of [5,10,17,23])desk(x,f,16);};
 const meeting=(f,x=5,z=6)=>{desk(x,f,z,false);chair(x,f+1,z+1,'north');chair(x-2,f+1,z,'east');chair(x+2,f+1,z,'west');};
 // Ground and one-block slabs: floor finish and soffit are the same structural layer.
 b(0,0,0,28,0,22,'light_gray_concrete');b(2,0,2,26,0,18,'white_concrete');
 for(const f of [...FLOORS.slice(1),ROOF])b(2,f,2,26,f,18,'white_concrete');
 for(let i=0;i<FLOORS.length;i++){
  const f=FLOORS[i],next=FLOORS[i+1]??ROOF;
  walls(2,f+1,2,26,next-1,18,'light_blue_stained_glass');
  if(i){walls(2,f+1,2,26,f+1,18,'light_gray_concrete');walls(2,next-1,2,26,next-1,18,'gray_concrete');}
 }
 // Recessed blue glazing, four equally treated elevations, light structural fins.
 for(const x of [2,8,14,20,26])for(const z of [2,18])b(x,1,z,x,64,z,'white_concrete');
 for(const x of [2,26])for(const z of [7,13])b(x,1,z,x,64,z,'white_concrete');
 // Subtle projection on outermost corners only, not heavy dark stone bands.
 for(const [x,z] of [[1,2],[27,2],[1,18],[27,18]])b(x,1,z,x,65,z,'white_concrete');
 for(const x of [19,25])b(x,1,3,x,64,3,'white_concrete');
 for(const f of [8,36,64]){b(2,f,1,26,f,1,'white_concrete');b(2,f,19,26,f,19,'white_concrete');}
 // Bridge disallows BlockEntity writes: reserve two double-door bays, currently open.
 b(12,1,18,15,2,18,'air');
 b(10,3,18,17,3,18,'white_concrete');b(10,4,19,17,4,21,'smooth_quartz_slab[type=top,waterlogged=false]');
 // Roof boundary and modest white mechanical crown, total occupied Y 0..69.
 walls(2,65,2,26,65,18,'light_gray_concrete');
 stages.shell=[...cells];
 // Stair box, lift and service riser: a single continuous core, never capped by floor slabs.
 walls(19,1,3,25,67,12,'white_concrete');b(20,1,4,24,67,11,'air');
 for(const f of [...FLOORS,ROOF]){b(20,f,11,24,f,12,'white_concrete');gap(20,f,12,24,12);}
 walls(15,1,3,18,67,8,'light_gray_concrete');b(16,1,4,17,67,7,'air');
 walls(12,1,3,14,64,6,'white_concrete');b(13,1,4,13,63,5,'air');
 // Steel-reinforced columns confined to bearing corners inside the core.
 for(const x of [19,25])b(x,1,3,x,64,3,afl('reinforced_concrete'));
 for(let j=0;j<FLOORS.length;j++){
  const f=FLOORS[j],next=FLOORS[j+1]??ROOF,rise=next-f;
  for(let k=1;k<=4;k++)b(20,f+k,11-k,21,f+k,11-k,'smooth_quartz_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]');
  b(20,f+4,4,24,f+4,6,'white_concrete');
  for(let k=5;k<=rise;k++)b(23,f+k,k+2,24,f+k,k+2,'smooth_quartz_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]');
  b(23,next,rise+3,24,next,11,'white_concrete');
  // Solid slim central divider: landing connects behind it, floor connects ahead.
  b(22,f+1,7,22,next,10,'light_gray_concrete');
  b(16,f+1,8,17,f+3,8,'iron_block');p(16,f+4,8,'gray_concrete');
  destinations.push({x:17,y:f+1,z:10,kind:'lift_lobby'},{x:20,y:f+1,z:12,kind:'stair_entry'});
  // Wall-mounted panels illuminate each flight without hanging in the clearance.
  p(20,f+3,11,afl('industrial_utility_light','facing=east'));
 }
 b(19,68,3,25,68,12,'white_concrete');b(15,68,3,18,68,8,'light_gray_concrete');
 b(19,69,3,25,69,3,'light_gray_concrete');
 stages.core=[...cells];
 // Five maintainable furnishing families share protected corridors and core interfaces.
 const templates={
  open(f,dense=false){stationFront(f);for(const z of [5,9])for(const x of [5,10])desk(x,f,z);if(dense){panel(4,f,6,6,6);panel(9,f,6,11,6);for(const x of [3,7])low(x,f,3);}},
  cubicle(f){stationFront(f);for(const z of [5,9])for(const x of [5,10])desk(x,f,z);for(const z of [6,10]){panel(4,f,z,6,z);panel(9,f,z,11,z);}panel(8,f,8,8,9);},
  mixed(f){stationFront(f);glassWall(8,f,3,8,9);gap(8,f,3,8,4);gap(8,f,8,8,9);desk(5,f,5);meeting(f,5,9);for(const z of [5,9])desk(10,f,z);},
  meeting(f,executive=false){stationFront(f);glassWall(3,f,10,12,10);gap(10,f,10,11,10);if(executive){desk(5,f,6,false);desk(8,f,6,false);desk(11,f,6,false);for(const x of [5,8,11])chair(x,f+1,7,'north');}else{meeting(f,5,6);desk(10,f,5,false);desk(10,f,8,false);}low(3,f,3);low(4,f,3);},
  archive(f){stationFront(f);desk(5,f,8);desk(10,f,8);for(const x of [3,5,7,9,11])two(x,f+1,3,'tall_filing_cabinet');for(const x of [3,5,7,9,11])low(x,f,5);glassWall(8,f,6,8,9);gap(8,f,8,8,9);}
 };
 const pool=Object.keys(templates),assignment=['lobby','open','cubicle','mixed','meeting','open','archive','management','boardroom'];
 if(randomFloors){const shuffled=[...pool];for(let i=shuffled.length-1;i>0;i--){const j=Math.floor(random()*(i+1));[shuffled[i],shuffled[j]]=[shuffled[j],shuffled[i]];}[1,2,3,5,6].forEach((slot,i)=>assignment[slot]=shuffled[i]);}
 // Lobby: reception to left, seating to right, central axis stays open.
 b(4,1,12,8,1,13,'white_concrete');b(4,2,13,8,2,13,'smooth_quartz_slab[type=bottom,waterlogged=false]');
 p(6,2,12,afl('office_computer_station','facing=south,lowered=false'));chair(6,1,11,'south');
 for(const x of [17,21,24])chair(x,1,15,'north');
 b(17,1,13,18,1,13,'smooth_quartz_slab[type=bottom,waterlogged=false]');
 b(21,1,13,22,1,13,'smooth_quartz_slab[type=bottom,waterlogged=false]');
 desk(5,0,5);low(3,0,8);low(4,0,8);two(10,1,3,'water_dispenser');
 for(const [x,z] of [[3,16],[25,16],[10,9]])plant(x,1,z);
 for(let i=1;i<9;i++){
  const f=FLOORS[i],type=assignment[i];
  if(type==='management'){templates.mixed(f);low(3,f,3);low(4,f,3);}
  else if(type==='boardroom')templates.meeting(f,true);
  else templates[type](f,i===5);
  // Shared print/tea point is north-west of the lobby, outside the main two-wide aisles.
  two(15,f+1,10,'office_multifunction_printer','south');two(18,f+1,10,'water_dispenser','south');
  p(18,f+1,9,afl('metal_trash_can'));low(3,f,12,'east');
  plant(25,f+1,17);if(random()>.3)plant(3,f+1,17);
  floorFunctions.push({floor:i+1,slab:f,world_y:f-33,template:type,clear:6});
 }
 floorFunctions.unshift({floor:1,slab:0,world_y:-33,template:'lobby',clear:7});
 // Ceiling panels touch the underside of the next slab, no added soffit layer.
 for(let i=0;i<9;i++){
  const f=FLOORS[i],ceiling=(FLOORS[i+1]??ROOF)-1;
  for(const [x,z] of [[5,5],[10,5],[5,9],[10,9],[5,14],[10,14],[16,14],[23,14],[17,11]]){
   if(get(x,ceiling+1,z)==='minecraft:white_concrete')p(x,ceiling,z,afl('industrial_utility_light','facing=down'));
  }
 }
 stages.interior=[...cells];
 // Roof mechanical units, fan grilles, isolated pipe run and maintenance circulation.
 for(const [x,z] of [[5,5],[9,5],[5,12],[10,12]]){
  b(x,65,z,x+2,65,z+2,'gray_concrete');b(x,66,z,x+2,67,z+2,'light_gray_concrete');
  b(x,68,z,x+2,68,z+2,'smooth_quartz_slab[type=bottom,waterlogged=false]');
  b(x,66,z+3,x+2,67,z+3,'iron_bars[east=true,north=false,south=false,west=true,waterlogged=false]');
 }
 b(14,65,13,14,65,16,'gray_concrete');b(14,65,16,20,65,16,'gray_concrete');
 p(19,66,13,afl('industrial_utility_light','facing=south'));
 // Forecourt with planted strips and low benches; entrance path stays six blocks wide.
 for(const x of [3,20]){b(x,1,20,x+5,1,21,'white_concrete');b(x,2,20,x+5,2,21,'azalea_leaves[persistent=true,distance=1,waterlogged=false]');}
 for(const x of [1,27])for(const z of [4,16]){p(x,1,z,'white_concrete');b(x,2,z,x,4,z,'stripped_oak_log[axis=y]');b(x,4,z-1,x,5,z+1,'azalea_leaves[persistent=true,distance=1,waterlogged=false]');}
 for(const x of [9,18])b(x,1,21,x,1,22,'smooth_quartz_slab[type=bottom,waterlogged=false]');
 b(11,0,19,16,0,22,'white_concrete');
 b(11,0,22,16,0,22,'smooth_quartz_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]');
 stages.finished=[...cells];
 return {seed,randomFloors,stages,cells,desks,destinations,floorFunctions,pool};
}

function operations(before,after){
 const runs=[];for(let y=0;y<70;y++)for(let z=0;z<23;z++)for(let x=0;x<29;x++){
  const s=after[index(x,y,z)];if(s===before[index(x,y,z)])continue;let X=x;while(X<28&&after[index(X+1,y,z)]===s&&s!==before[index(X+1,y,z)])X++;
  runs.push({x,X,y,Y:y,z,Z:z,block:s});x=X;
 }
 function merge(rs,axis){const u=axis.toUpperCase(),out=[],last=new Map();for(const r of rs.sort((a,b)=>a[axis]-b[axis])){const k=['x','X','y','Y','z','Z','block'].filter(k=>k!==axis&&k!==u).map(k=>r[k]).join('|'),p=last.get(k);if(p&&p[u]+1===r[axis])p[u]=r[u];else{const q={...r};out.push(q);last.set(k,q);}}return out;}
 return merge(merge(runs,'z'),'y').map(r=>({min:abs(r.x,r.y,r.z),max:abs(r.X,r.Y,r.Z),block:r.block}));
}
async function plan(){
 await absent('baseline');const base=await snapshot('baseline');if(base.cells.some(s=>s!==air))throw Error('PLOT_NOT_EMPTY');
 const r=recipe(),registry=JSON.parse(await readFile('run/afl_authoring_bridge/target_registry_1_20_1.json','utf8')).blocks;
 for(const s of new Set(r.cells)){const [id,props]=s.replace(']','').split('['),entry=registry[id];if(!entry)throw Error('UNKNOWN_BLOCK '+id);for(const kv of props?.split(',')??[]){const[k,v]=kv.split('=');if(!entry.properties[k]?.includes(v))throw Error('UNKNOWN_STATE '+s);}}
 await save('plan',r);console.log(JSON.stringify({floorFunctions:r.floorFunctions,pool:r.pool,desks:r.desks.length,non_air:r.cells.filter(s=>s!==air).length,states:new Set(r.cells).size}));
}
async function replan(){for(const s of ['shell','core','interior','finished']){try{const l=await load(s+'_ledger');if(l.status!=='PREFLIGHT'||l.completed.length||l.pending!==undefined)throw Error('WORLD_WRITE_ALREADY_ATTEMPTED');}catch(e){if(e.code!=='ENOENT')throw e;}}const base=await load('baseline');if(base.cells.some(s=>s!==air))throw Error('NOT_EMPTY_BASELINE');await save('plan',recipe());console.log('REPLANNED_BEFORE_ANY_WORLD_WRITE');}
async function apply(stage){
 await guard();try{const l=await load(stage+'_ledger');if(l.status!=='PREFLIGHT'||l.completed.length||l.pending!==undefined)throw Error('STAGE_ALREADY_ATTEMPTED');}catch(e){if(e.code!=='ENOENT')throw e;}const r=await load('plan'),names=Object.keys(r.stages),j=names.indexOf(stage);if(j<0)throw Error('STAGE');
 const before=await snapshot(stage+'_before'),expected=j?r.stages[names[j-1]]:(await load('baseline')).cells;
 if(before.cells.some((s,i)=>s!==expected[i]))throw Error('PREVIOUS_STAGE_OR_MANUAL_CONFLICT');
 // Multipart desks schedule a one-tick integrity check. Keep each complete desk
 // in a single server batch; never merge matching parts across different floors.
 const target=r.stages[stage],withoutDesks=target.map((s,i)=>s.includes(':modern_office_desk[')?before.cells[i]:s);
 const ops=operations(before.cells,withoutDesks),batches=[];for(let i=0;i<ops.length;i+=110)batches.push(ops.slice(i,i+110));
 let furniture=[];for(const d of r.desks){const dx=d.facing==='north'?1:-1,parts=[-1,0,1].map(o=>[d.x+o*dx,d.y,d.z]);if(!parts.some(([x,y,z])=>target[index(x,y,z)].includes(':modern_office_desk[')&&target[index(x,y,z)]!==before.cells[index(x,y,z)]))continue;for(const [x,y,z] of parts)furniture.push({min:abs(x,y,z),max:abs(x,y,z),block:target[index(x,y,z)]});if(furniture.length>=108){batches.push(furniture);furniture=[];}}if(furniture.length)batches.push(furniture);
 const ledger={epoch:EPOCH,stage,status:'PREFLIGHT',completed:[]};await save(stage+'_ledger',ledger);
 for(const operations of batches)await c.call('we_batch_set',{operations,dry_run:true});
 for(let i=0;i<batches.length;i++){ledger.status='WRITING';ledger.pending=i;await save(stage+'_ledger',ledger);ledger.completed.push(await c.call('we_batch_set',{operations:batches[i]}));delete ledger.pending;await save(stage+'_ledger',ledger);console.log(stage,i+1,'/',batches.length);}
 const a=await snapshot(stage+'_after');ledger.mismatches=a.cells.flatMap((s,i)=>s!==r.stages[stage][i]?[i]:[]);ledger.status=ledger.mismatches.length?'MISMATCH':'VERIFIED';await save(stage+'_ledger',ledger);console.log(JSON.stringify({status:ledger.status,mismatches:ledger.mismatches.length,non_air:a.count.non_air}));
}
async function repairDesk(){await guard();await absent('desk_repair');const r=await load('plan'),a=await snapshot('desk_repair_before'),diff=a.cells.flatMap((s,i)=>s!==r.stages.interior[i]?[i]:[]),expected=[25159,25160,25161];if(JSON.stringify(diff)!==JSON.stringify(expected)||diff.some(i=>a.cells[i]!==air))throw Error('UNEXPECTED_REPAIR_STATE');const operations=expected.map(i=>({min:abs(i%29,Math.floor(i/667),Math.floor(i/29)%23),max:abs(i%29,Math.floor(i/667),Math.floor(i/29)%23),block:r.stages.interior[i]}));await c.call('we_batch_set',{operations,dry_run:true});await save('desk_repair',{status:'INTENT',operations});const result=await c.call('we_batch_set',{operations});const v=await snapshot('interior_after_repair');await save('desk_repair',{result,mismatches:v.cells.filter((s,i)=>s!==r.stages.interior[i]).length});console.log('DESK_REPAIR',v.cells.filter((s,i)=>s!==r.stages.interior[i]).length);}
async function refine(label='refine'){if(!/^[a-z0-9_]+$/.test(label))throw Error('LABEL');await guard();await absent(label+'_ledger');const old=await load('plan'),a=await snapshot(label+'_before');if(a.cells.some((s,i)=>s!==old.cells[i]))throw Error('MANUAL_CONFLICT');const next=recipe(),ops=operations(a.cells,next.cells);if(ops.length>128)throw Error('REFINEMENT_TOO_LARGE');await c.call('we_batch_set',{operations:ops,dry_run:true});await save(label+'_ledger',{status:'INTENT',ops});const result=await c.call('we_batch_set',{operations:ops});await save('plan_before_'+label,old);await save('plan',next);const v=await snapshot('final_after');await save(label+'_ledger',{result,mismatches:v.cells.filter((s,i)=>s!==next.cells[i]).length});console.log('REFINE',v.cells.filter((s,i)=>s!==next.cells[i]).length);}
async function view(label,values){await guard();if(values.length){const n=values.map(Number),pose={position:n.slice(0,3),yaw:n[3],pitch:n[4]};await c.call('camera_move',{...pose,dry_run:true});await c.call('camera_move',pose);}for(let i=0;i<40;i++){const s=await c.call('camera_status');if(s.client_frame_ready){const capture=await c.call('capture_current_view');await save('view_'+label,{s,capture});console.log(JSON.stringify(capture));return;}await new Promise(r=>setTimeout(r,100));}throw Error('FRAME');}
if(process.argv[1]?.endsWith('office_concrete_01.mjs'))try{const a=process.argv[2];if(a==='plan')await plan();else if(a==='replan')await replan();else if(a==='repair-desk')await repairDesk();else if(a==='refine')await refine(process.argv[3]);else if(a==='apply')await apply(process.argv[3]);else if(a==='view')await view(process.argv[3],process.argv.slice(4));else if(a==='snapshot'){const a=await snapshot(process.argv[3]??'latest');console.log({non_air:a.count.non_air,height:a.count.occupied_height});}else if(a==='restore'){await guard();await c.call('camera_restore',{dry_run:true});console.log(await c.call('camera_restore'));}else throw Error('ACTION');}catch(e){console.error(e.stack);process.exitCode=1;}
