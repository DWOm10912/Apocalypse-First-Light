// Facade-only overlay on a fresh live snapshot; never replay the V1 interior recipe.
import {BridgeClient} from './bridge_client.mjs';
import {mkdir,readFile,writeFile} from 'node:fs/promises';
import path from 'node:path';
import {ORIGIN,SIZE,ID,FLOORS} from './office_concrete_01.mjs';
const epoch='dc8e97dc-0438-4667-97e5-79dad371ba00';
const dir=path.resolve('build/authoring_checks',ID,'facade_v2'), c=new BridgeClient('./run');
const ix=(x,y,z)=>(y*23+z)*29+x, xyz=i=>[i%29,Math.floor(i/667),Math.floor(i/29)%23];
const abs=(x,y,z)=>[x+80,y-33,z-464];
const canon=s=>s.replace(/^Block\{([^}]+)\}/,'$1').replace(/\[([^\]]+)\]/,(_,p)=>'['+p.split(',').sort().join(',')+']');
const interior=([x,y,z])=>x>=3&&x<=25&&z>=3&&z<=17;
async function save(n,v){await mkdir(dir,{recursive:true});await writeFile(path.join(dir,n+'.json'),JSON.stringify(v));}
async function load(n){return JSON.parse(await readFile(path.join(dir,n+'.json'),'utf8'));}
async function absent(n){try{await load(n);throw Error('ALREADY_ATTEMPTED '+n);}catch(e){if(e.code!=='ENOENT')throw e;}}
async function guard(){const s=await c.call('minecraft_status'),a=await c.call('authoring_info');if(s.world_session!==epoch||s.world!=='新的世界'||a.id!==ID||JSON.stringify(a.min)!==JSON.stringify(ORIGIN)||JSON.stringify(a.max)!=='[108,36,-442]')throw Error('WORLD_PLOT_CHANGED');}
async function snapshot(n){await guard();const cells=[];for(let y=0;y<70;y++){const r=await c.call('get_horizontal_slice',{target:'AUTHORING_SESSION',coordinate:y-33,encoding:'palette'}),p=Object.fromEntries(Object.entries(r.palette).map(([s,i])=>[i,canon(s)]));for(let z=0;z<23;z++)for(let x=0;x<29;x++)cells[ix(x,y,z)]=p[r.rows[z][x]];}const r={origin:ORIGIN,size:SIZE,epoch,cells,count:await c.call('inspect_selection',{target:'AUTHORING_SESSION'})};await save(n,r);return r;}

export function facadeV2(before){
 const cells=[...before], groups={};let group='';
 const box=(x,y,z,X,Y,Z,state)=>{state=canon('minecraft:'+state);for(let j=y;j<=Y;j++)for(let k=z;k<=Z;k++)for(let i=x;i<=X;i++){
  if(i<0||i>28||j<0||j>69||k<0||k>22||interior([i,j,k]))throw Error('PROTECTED_INTERIOR_OR_BOUNDS');
  const n=ix(i,j,k),old=before[n];if(!/^minecraft:(air|white_concrete|light_gray_concrete|gray_concrete|light_blue_stained_glass|smooth_quartz_slab)(\[|$)/.test(old))throw Error('PROTECTED_MATERIAL '+[i,j,k]+old);
  cells[n]=state;(groups[group]??=[]).push(n);
 }};
 const ring=(x,y,z,X,Z,state)=>{box(x,y,z,X,y,z,state);box(x,y,Z,X,y,Z,state);box(x,y,z+1,x,y,Z-1,state);box(X,y,z+1,X,y,Z-1,state);};
 group='corner_piers';
 for(const x of [1,26])for(const z of [1,18])box(x,1,z,x+1,66,z+1,'white_concrete');
 group='projected_tower_frame';
 for(const y of [...FLOORS.slice(1),64])ring(1,y,1,27,19,'white_concrete');
 for(const x of [8,20])for(const z of [1,19])box(x,8,z,x,64,z,'white_concrete');
 for(const x of [1,27])for(const z of [7,13])box(x,8,z,x,64,z,'white_concrete');
 group='transparent_lobby';
 for(const x of [8,20])for(const z of [2,18])box(x,1,z,x,6,z,'light_blue_stained_glass');
 for(const x of [2,26])for(const z of [7,13])box(x,1,z,x,6,z,'light_blue_stained_glass');
 group='podium';
 ring(0,8,0,28,20,'white_concrete');
 ring(0,7,0,28,20,'smooth_quartz_slab[type=top,waterlogged=false]');
 group='entry_portal';
 box(9,4,19,18,4,21,'smooth_quartz_slab[type=top,waterlogged=false]');
 for(const x of [9,18])box(x,1,19,x,3,19,'light_gray_concrete');
 // Mid-height double-depth thin ledge is the single tower rhythm accent.
 group='midheight_ledge';ring(0,36,0,28,20,'smooth_quartz_slab[type=bottom,waterlogged=false]');
 group='roof_coping';
 ring(0,64,0,28,20,'smooth_quartz_slab[type=top,waterlogged=false]');
 ring(1,65,1,27,19,'light_gray_concrete');
 ring(1,66,1,27,19,'smooth_quartz_slab[type=bottom,waterlogged=false]');
 const changed=cells.flatMap((s,i)=>s!==before[i]?[i]:[]);
 if(changed.some(i=>interior(xyz(i))||before[i].startsWith('apocalypse_firstlight:')))throw Error('PRESERVATION_FAILED');
 return {cells,changed,groups:Object.fromEntries(Object.entries(groups).map(([g,is])=>[g,new Set(is.filter(i=>cells[i]!==before[i])).size]))};
}
function operations(before,after){
 const runs=[];for(let y=0;y<70;y++)for(let z=0;z<23;z++)for(let x=0;x<29;x++){const s=after[ix(x,y,z)];if(s===before[ix(x,y,z)])continue;let X=x;while(X<28&&after[ix(X+1,y,z)]===s&&s!==before[ix(X+1,y,z)])X++;runs.push({x,X,y,Y:y,z,Z:z,block:s});x=X;}
 function merge(rs,axis){const u=axis.toUpperCase(),out=[],last=new Map();for(const r of rs.sort((a,b)=>a[axis]-b[axis])){const k=['x','X','y','Y','z','Z','block'].filter(k=>k!==axis&&k!==u).map(k=>r[k]).join('|'),p=last.get(k);if(p&&p[u]+1===r[axis])p[u]=r[u];else{const q={...r};out.push(q);last.set(k,q);}}return out;}
 return merge(merge(runs,'z'),'y').map(r=>({min:abs(r.x,r.y,r.z),max:abs(r.X,r.Y,r.Z),block:r.block}));
}
async function plan(){await absent('plan');const b=await snapshot('baseline'),p=facadeV2(b.cells);
 const v1=JSON.parse(await readFile(path.resolve(dir,'../plan.json'),'utf8'));
 const manual=b.cells.flatMap((s,i)=>s!==v1.cells[i]?[i]:[]);
 if(manual.some(i=>p.cells[i]!==b.cells[i]))throw Error('USER_EDIT_CONFLICT');
 const ops=operations(b.cells,p.cells);await save('plan',{...p,ops,user_edits_preserved:manual.length});
 console.log(JSON.stringify({changes:p.changed.length,groups:p.groups,ops:ops.length,user_edits_preserved:manual.length}));}
async function apply(){await absent('ledger');const b=await load('baseline'),p=await load('plan'),now=await snapshot('before');if(now.cells.some((s,i)=>s!==b.cells[i]))throw Error('LIVE_CHANGED_SINCE_PLAN');
 const batches=[];for(let i=0;i<p.ops.length;i+=110)batches.push(p.ops.slice(i,i+110));
 const l={status:'PREFLIGHT',completed:[]};await save('ledger',l);for(const operations of batches)await c.call('we_batch_set',{operations,dry_run:true});
 for(let i=0;i<batches.length;i++){l.status='WRITING';l.pending=i;await save('ledger',l);l.completed.push(await c.call('we_batch_set',{operations:batches[i]}));delete l.pending;await save('ledger',l);console.log('batch',i+1,'/',batches.length);}
 const a=await snapshot('after');l.mismatches=a.cells.flatMap((s,i)=>s!==p.cells[i]?[i]:[]);l.interior_changes=a.cells.filter((s,i)=>interior(xyz(i))&&s!==b.cells[i]).length;l.custom_changes=a.cells.filter((s,i)=>b.cells[i].startsWith('apocalypse_firstlight:')&&s!==b.cells[i]).length;l.non_air=a.count.non_air;l.height=a.count.occupied_height;l.status=l.mismatches.length?'MISMATCH':'VERIFIED';await save('ledger',l);console.log(JSON.stringify(l));if(l.mismatches.length)throw Error('READBACK_MISMATCH');}
if(process.argv[1]?.endsWith('office_concrete_01_facade_v2.mjs'))try{if(process.argv[2]==='plan')await plan();else if(process.argv[2]==='apply')await apply();else throw Error('ACTION');}catch(e){console.error(e.stack);process.exitCode=1;}
