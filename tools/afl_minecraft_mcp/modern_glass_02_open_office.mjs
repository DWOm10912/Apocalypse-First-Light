// Live meeting-room simplification only; no Gradle, offline save editing or export.
import {BridgeClient} from './bridge_client.mjs';
import {mkdir,readFile,writeFile} from 'node:fs/promises';
import path from 'node:path';
const c=new BridgeClient('./run');
const epoch='6ef9c05f-0913-4f9e-83be-af3d566abca2',id='modern_glass_tower_02';
const origin=[16,-33,-160],size=[37,90,41];
const dir=path.resolve('build/authoring_checks',id,'open_office_20260910');
const at=(x,y,z)=>(y*41+z)*37+x,absolute=(x,y,z)=>[x+16,y-33,z-160];
const canonical=s=>{s=s.replace(/^Block\{([^}]+)\}/,'$1');const m=s.match(/^([^[]+)(?:\[(.*)\])?$/);return m[1]+(m[2]?'['+m[2].split(',').sort().join(',')+']':'');};
const floors=Array.from({length:15},(_,i)=>6+5*i),lamp='apocalypse_firstlight:industrial_utility_light';
const air='minecraft:air',desk='minecraft:spruce_slab[type=top,waterlogged=false]',monitor='minecraft:black_concrete';
async function save(name,value){await mkdir(dir,{recursive:true});await writeFile(path.join(dir,name+'.json'),JSON.stringify(value,null,2));}
async function load(name){return JSON.parse(await readFile(path.join(dir,name+'.json'),'utf8'));}
async function absent(name){try{await load(name);throw Error('ALREADY_EXISTS: '+name);}catch(e){if(e.code!=='ENOENT')throw e;}}
async function guard(){
 const status=await c.call('minecraft_status'),a=await c.call('authoring_info');
 if(status.world_session!==epoch||status.world!=='新的世界'||status.dimension!=='minecraft:overworld'||a.id!==id||JSON.stringify(a.min)!==JSON.stringify(origin)||JSON.stringify(a.max)!=='[52,56,-120]')throw Error('WORLD_OR_PLOT_CHANGED');
 return {status,a};
}
async function snapshot(name){
 await guard();const palette=[],map=new Map(),cells=[];
 for(let y=0;y<90;y++){
  const r=await c.call('get_horizontal_slice',{target:'AUTHORING_SESSION',coordinate:y-33,encoding:'palette'});
  const decode=Object.fromEntries(Object.entries(r.palette).map(([b,v])=>[v,canonical(b)]));
  for(let z=0;z<41;z++)for(let x=0;x<37;x++){
   const b=decode[r.rows[z][x]];if(!b)throw Error('INCOMPLETE_SLICE');
   if(!map.has(b)){map.set(b,palette.length);palette.push(b);}cells[at(x,y,z)]=map.get(b);
  }
 }
 const count=await c.call('inspect_selection',{target:'AUTHORING_SESSION'});
 const result={epoch,id,origin,size,palette,cells,count};await save(name,result);return result;
}
const decoded=s=>s.cells.map(i=>s.palette[i]);
function operations(changes){
 // Merge only adjacent changed cells with the same target state; no unaffected filler.
 const runs=[];let previous;
 for(const d of [...changes].sort((a,b)=>a.local[1]-b.local[1]||a.local[2]-b.local[2]||a.local[0]-b.local[0])){
  const [x,y,z]=d.local;
  if(previous&&previous.y===y&&previous.z===z&&previous.X+1===x&&previous.block===d.block)previous.X=x;
  else {previous={x,X:x,y,Y:y,z,Z:z,block:d.block};runs.push(previous);}
 }
 function merge(input,axis){const out=[],last=new Map(),upper=axis.toUpperCase();
  for(const r of input.sort((a,b)=>a[axis]-b[axis])){
   const key=['x','X','y','Y','z','Z','block'].filter(k=>k!==axis&&k!==upper).map(k=>r[k]).join('|'),p=last.get(key);
   if(p&&p[upper]+1===r[axis])p[upper]=r[upper];else{const copy={...r};out.push(copy);last.set(key,copy);}
  }return out;
 }
 return merge(merge(runs,'z'),'y').map(r=>({min:absolute(r.x,r.y,r.z),max:absolute(r.X,r.Y,r.Z),block:r.block}));
}
function recipe(stage,s){
 if(!['pilot','remaining'].includes(stage))throw Error('UNKNOWN_STAGE');
 const levels=stage==='pilot'?[6]:floors.slice(1),cells=decoded(s),original=[...cells];
 function set(x,y,z,b,allowed){
  const i=at(x,y,z),old=cells[i];
  if(!levels.some(f=>y>=f+1&&y<=f+3)||x<21||x>25||z<22||z>29)throw Error('OUTSIDE_MEETING_ZONE');
  if(!allowed(old))throw Error('UNREVIEWED_BLOCK '+[x,y,z]+' '+old);
  cells[i]=canonical(b);
 }
 const partition=b=>b==='minecraft:smooth_quartz'||b.startsWith('minecraft:glass_pane[')||b.startsWith('minecraft:birch_door[')||b===air;
 for(const f of levels){
  for(let dy=1;dy<=3;dy++){
   for(let z=22;z<=29;z++)set(21,f+dy,z,air,partition);
   for(let x=22;x<=25;x++)set(x,f+dy,22,air,partition);
  }
  // Only the reviewed old conference table and its two seats; retain other furniture.
  for(let z=25;z<=28;z++)set(23,f+1,z,air,b=>b===desk);
  set(22,f+1,25,air,b=>b==='minecraft:spruce_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]');
  set(24,f+1,27,air,b=>b==='minecraft:spruce_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]');
  // Two simple placeholder desks align with the existing office rows. No added chairs.
  for(const z of [24,28]){
   for(const x of [23,24])set(x,f+1,z,desk,b=>b===air);
   set(23,f+2,z,monitor,b=>b===air);
  }
 }
 const changes=[];
 for(let y=0;y<90;y++)for(let z=0;z<41;z++)for(let x=0;x<37;x++){
  const i=at(x,y,z);if(cells[i]!==original[i])changes.push({local:[x,y,z],position:absolute(x,y,z),before:original[i],block:cells[i]});
 }
 return {epoch,id,stage,levels,desks:levels.length*2,changes,operations:operations(changes)};
}
async function plan(stage){
 await absent(stage+'_ledger');await absent(stage+'_plan');
 const before=await snapshot(stage+'_before'),p=recipe(stage,before);await save(stage+'_plan',p);
 console.log(JSON.stringify({stage,levels:p.levels,desks:p.desks,changes:p.changes.length,operations:p.operations.length,batches:Math.ceil(p.operations.length/120)}));
}
async function apply(stage){
 await guard();await absent(stage+'_ledger');const p=await load(stage+'_plan');
 if(p.epoch!==epoch||p.id!==id||p.stage!==stage)throw Error('PLAN_MISMATCH');
 const live=decoded(await snapshot(stage+'_preflight'));
 // Protect all contextual supports / untouched furniture, not just changed cells.
 const baseline=decoded(await load(stage+'_before'));
 if(live.some((b,i)=>b!==baseline[i]))throw Error('WORLD_CHANGED_SINCE_PLAN');
 const batches=[];for(let i=0;i<p.operations.length;i+=120)batches.push(p.operations.slice(i,i+120));
 const ledger={epoch,stage,status:'PREFLIGHT',completed:[]};await save(stage+'_ledger',ledger);
 for(const ops of batches)await c.call('we_batch_set',{operations:ops,dry_run:true});
 for(let i=0;i<batches.length;i++){
  ledger.status='WRITING';ledger.pending=i;await save(stage+'_ledger',ledger);
  const r=await c.call('we_batch_set',{operations:batches[i]});ledger.completed.push(r);delete ledger.pending;await save(stage+'_ledger',ledger);
 }
 for(const d of p.changes)baseline[at(...d.local)]=d.block;
 const after=decoded(await snapshot(stage+'_after'));
 ledger.mismatches=[];for(let i=0;i<after.length;i++)if(after[i]!==baseline[i])ledger.mismatches.push({i,expected:baseline[i],actual:after[i]});
 ledger.status=ledger.mismatches.length?'READBACK_MISMATCH':'APPLIED';await save(stage+'_ledger',ledger);
 console.log(JSON.stringify({stage,status:ledger.status,changed:p.changes.length,desks:p.desks,mismatches:ledger.mismatches.length}));
 if(ledger.mismatches.length)throw Error('READBACK_MISMATCH');
}
async function audit(){
 const initial=decoded(await load('pilot_before')),expected=[...initial],plans=await Promise.all(['pilot','remaining'].map(s=>load(s+'_plan')));
 for(const p of plans)for(const d of p.changes){const i=at(...d.local);if(expected[i]!==d.before)throw Error('PLAN_CHAIN_CHANGED');expected[i]=d.block;}
 const s=await snapshot('final'),actual=decoded(s),mismatches=[],protectedChanges=[];let changed=0;
 for(let i=0;i<actual.length;i++){
  if(actual[i]!==expected[i])mismatches.push({i,expected:expected[i],actual:actual[i]});
  if(actual[i]!==initial[i]){changed++;if(expected[i]===initial[i])protectedChanges.push(i);}
 }
 const aisleObstructions=[];
 for(const f of floors){
  // Former partition line plus north entry remain fully open with two-block headroom.
  for(let z=22;z<=29;z++)for(let dy=1;dy<=2;dy++)for(const x of [21,22])if(actual[at(x,f+dy,z)]!==air)aisleObstructions.push([x,f+dy,z]);
 }
 const lightChanges=actual.flatMap((b,i)=>(b.startsWith(lamp)||initial[i].startsWith(lamp))&&b!==initial[i]?[i]:[]);
 const result={epoch,id,roomsRemoved:15,placeholderDesks:30,changed,nonAir:s.count.non_air,industrial:actual.filter(b=>b.startsWith(lamp)).length,
  seaLanterns:actual.filter(b=>b==='minecraft:sea_lantern').length,mismatches,protectedChanges,aisleObstructions,lightChanges};
 await save('final_audit',result);console.log(JSON.stringify(result));
 if(mismatches.length||protectedChanges.length||aisleObstructions.length||lightChanges.length)throw Error('FINAL_AUDIT_FAILED');
}
async function view(name,pose){
 await guard();if(pose){await c.call('camera_move',{...pose,dry_run:true});await c.call('camera_move',pose);}
 for(let i=0;i<12;i++){
  const state=await c.call('camera_status');if(state.client_frame_ready){const capture=await c.call('capture_current_view');await save('view_'+name,{state,capture});console.log(JSON.stringify({state,capture}));return;}
  await new Promise(r=>setTimeout(r,250));
 }throw Error('CAMERA_NOT_SETTLED');
}
async function main(){
 const [action,name,...args]=process.argv.slice(2);
 if(action==='plan')await plan(name);
 else if(action==='apply')await apply(name);
 else if(action==='audit')await audit();
 else if(action==='snapshot'){await absent(name);const s=await snapshot(name);console.log(JSON.stringify({nonAir:s.count.non_air,palette:s.palette}));}
 else if(action==='view'){const a=args.map(Number);await view(name,a.length?{position:a.slice(0,3),yaw:a[3],pitch:a[4]}:null);}
 else if(action==='restore'){await guard();await c.call('camera_restore',{dry_run:true});console.log(JSON.stringify(await c.call('camera_restore')));await view('restored');}
 else throw Error('UNKNOWN_ACTION');
}
main().catch(e=>{console.error(e.message);process.exitCode=1;});
