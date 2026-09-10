// Live, plot-scoped lighting only. No Gradle, save editing, export or arbitrary commands.
import {BridgeClient} from './bridge_client.mjs';
import {mkdir,readFile,writeFile} from 'node:fs/promises';
import path from 'node:path';
const c=new BridgeClient('./run');
const epoch='6ef9c05f-0913-4f9e-83be-af3d566abca2',id='modern_glass_tower_02';
const origin=[16,-33,-160],size=[37,90,41];
const dir=path.resolve('build/authoring_checks',id,'lighting_20260910');
const at=(x,y,z)=>(y*41+z)*37+x;
const absolute=(x,y,z)=>[x+16,y-33,z-160];
const canonical=s=>{s=s.replace(/^Block\{([^}]+)\}/,'$1');let m=s.match(/^([^[]+)(?:\[(.*)\])?$/);return m[1]+(m[2]?'['+m[2].split(',').sort().join(',')+']':'');};
const lamp='apocalypse_firstlight:industrial_utility_light';
const sea='minecraft:sea_lantern',air='minecraft:air';
const floors=Array.from({length:15},(_,i)=>6+5*i);
async function save(name,value){await mkdir(dir,{recursive:true});await writeFile(path.join(dir,name+'.json'),JSON.stringify(value,null,2));}
async function load(name){return JSON.parse(await readFile(path.join(dir,name+'.json'),'utf8'));}
async function guard(){
 const status=await c.call('minecraft_status'),a=await c.call('authoring_info');
 if(status.world_session!==epoch||status.world!=='新的世界'||status.dimension!=='minecraft:overworld'||a.id!==id||JSON.stringify(a.min)!==JSON.stringify(origin)||JSON.stringify(a.max)!=='[52,56,-120]')throw Error('WORLD_OR_PLOT_CHANGED');
 return {status,a};
}
async function snapshot(name){
 await guard();const palette=[],map=new Map(),cells=[];
 for(let y=0;y<90;y++){
  const r=await c.call('get_horizontal_slice',{target:'AUTHORING_SESSION',coordinate:y-33,encoding:'palette'});
  const decode=Object.fromEntries(Object.entries(r.palette).map(([s,v])=>[v,canonical(s)]));
  for(let z=0;z<41;z++)for(let x=0;x<37;x++){
   const b=decode[r.rows[z][x]];if(!b)throw Error('INCOMPLETE_SLICE');
   if(!map.has(b)){map.set(b,palette.length);palette.push(b);}cells[at(x,y,z)]=map.get(b);
  }
 }
 const count=await c.call('inspect_selection',{target:'AUTHORING_SESSION'});
 const result={epoch,id,origin,size,palette,cells,count};await save(name,result);return result;
}
function positions(s,match){let result=[];for(let y=0;y<90;y++)for(let z=0;z<41;z++)for(let x=0;x<37;x++)if(match(s.palette[s.cells[at(x,y,z)]]))result.push([x,y,z]);return result;}
const backing={down:[0,1,0],south:[0,0,-1],east:[-1,0,0]};
const supportBlocks=new Set(['minecraft:smooth_quartz','minecraft:polished_andesite','minecraft:gray_concrete']);
function recipe(stage,s){
 if(!['pilot','remaining'].includes(stage))throw Error('UNKNOWN_LIGHTING_STAGE');
 const cells=s.cells.map(i=>s.palette[i]),diff=new Map(),fixtures=[];
 const get=(x,y,z)=>cells[at(x,y,z)];
 function set(x,y,z,b){
  if(x<0||x>36||y<0||y>89||z<0||z>40)throw Error('LOCAL_BOUNDS');
  b=canonical(b);let i=at(x,y,z),before=s.palette[s.cells[i]];cells[i]=b;
  if(before!==b)diff.set(i,{position:absolute(x,y,z),local:[x,y,z],before,block:b});else diff.delete(i);
 }
 const levels=stage==='pilot'?[0,6]:floors.filter(y=>y!==6);
 // Restore the solid soffit / lift wall; erase only formerly floating light cubes.
 for(const [x,y,z] of positions(s,b=>b===sea)){
  const f=y<=6?0:y-4;if(!levels.includes(f))continue;
  let material;
  if(z===15&&(x===12||x===17))material='gray_concrete';
  else if(z===17&&(x===20||x===21))material='air';
  else if(f===0&&y===5&&[22,31].includes(z))material='smooth_quartz';
  else if(f===0&&y===6&&z===11)material=x===18?'gray_concrete':'polished_andesite';
  else if(f>0&&((z===20&&[12,13].includes(x))||(z===23&&[16,17].includes(x))||(z===26&&[23,24].includes(x))))material='smooth_quartz';
  else if(f>0&&((z===22&&[6,7].includes(x))||(z===23&&[29,30].includes(x))))material='air';
  else throw Error('UNREVIEWED_SEA_LANTERN '+[x,y,z]);
  set(x,y,z,'minecraft:'+material);
 }
 function fixture(x,y,z,facing,room){
  if(get(x,y,z)!==air)throw Error('FIXTURE_CELL_OCCUPIED '+[x,y,z]+' '+get(x,y,z));
  const support=backing[facing].map((d,i)=>[x,y,z][i]+d),material=get(...support);
  if(!supportBlocks.has(material))throw Error('UNSUPPORTED_FIXTURE '+[x,y,z]+' '+material);
  set(x,y,z,`${lamp}[facing=${facing}]`);fixtures.push({local:[x,y,z],position:absolute(x,y,z),facing,room,support:absolute(...support),supportMaterial:material});
 }
 for(const f of levels){
  // Vertical lights over each lift; one flush stair-wall light, no obstruction to the risers.
  for(const x of [12,17])fixture(x,f+4,16,'south','lift_lobby');
  fixture(20,f+4,17,'east','stair_entry');
  if(f===0){
   // North service rooms have the original 6-block-high podium ceiling.
   for(const [x,z,room] of [[7,9,'public_wc'],[7,16,'janitor'],[17,7,'north_hall'],[28,9,'north_lobby'],[28,16,'cafe_counter']])fixture(x,5,z,'down',room);
   for(const x of [7,14,21,28])for(const z of [21,27,32])fixture(x,4,z,'down','main_lobby');
  }else{
   fixture(12,f+3,20,'down','wc');fixture(18,f+3,21,'down','corridor');
   for(const x of [13,18])for(const z of [24,28])fixture(x,f+3,z,'down','open_office');
   for(const z of [25,28])fixture(23,f+3,z,'down','meeting_room');
   if(f<61)for(const z of [14,20,26])fixture(7,f+4,z,'down',z===26?'storage':'west_lounge');
   if(f<41)for(const z of [14,20,26])fixture(29,f+4,z,'down','east_project_office');
  }
 }
 return {epoch,id,stage,levels,fixtures,changes:[...diff.values()]};
}
async function plan(stage){
 const s=await snapshot(stage+'_before'),p=recipe(stage,s);await save(stage+'_plan',p);
 console.log(JSON.stringify({stage,changes:p.changes.length,fixtures:p.fixtures.length,rooms:Object.fromEntries([...new Set(p.fixtures.map(f=>f.room))].map(r=>[r,p.fixtures.filter(f=>f.room===r).length]))}));
}
async function apply(stage){
 await guard();let oldLedger;try{oldLedger=await load(stage+'_ledger');}catch(e){if(e.code!=='ENOENT')throw e;}
 if(oldLedger)throw Error('STAGE_ALREADY_ATTEMPTED: inspect ledger/world, never replay uncertain edits');
 const p=await load(stage+'_plan');if(p.epoch!==epoch||p.id!==id||p.stage!==stage)throw Error('PLAN_MISMATCH');
 const s=await snapshot(stage+'_preflight');const get=(pos)=>s.palette[s.cells[at(pos[0]-16,pos[1]+33,pos[2]+160)]];
 for(const d of p.changes)if(get(d.position)!==d.before)throw Error('WORLD_CHANGED_SINCE_PLAN '+d.position);
 const final=new Map(p.changes.map(d=>[d.position.join(','),d.block]));
 for(const f of p.fixtures)if((final.get(f.support.join(','))??get(f.support))!==f.supportMaterial)throw Error('SUPPORT_CHANGED_SINCE_PLAN');
 const batches=[];for(let i=0;i<p.changes.length;i+=100)batches.push(p.changes.slice(i,i+100).map(d=>({min:d.position,max:d.position,block:d.block})));
 const ledger={epoch,stage,status:'PREFLIGHT',completed:[]};await save(stage+'_ledger',ledger);
 for(const operations of batches)await c.call('we_batch_set',{operations,dry_run:true});
 for(let i=0;i<batches.length;i++){
  ledger.status='WRITING';ledger.pending=i;await save(stage+'_ledger',ledger);
  const result=await c.call('we_batch_set',{operations:batches[i]});ledger.completed.push(result);delete ledger.pending;await save(stage+'_ledger',ledger);
 }
 const after=await snapshot(stage+'_after');const state=(pos)=>after.palette[after.cells[at(pos[0]-16,pos[1]+33,pos[2]+160)]];
 ledger.mismatches=p.changes.filter(d=>state(d.position)!==d.block);
 ledger.supportMismatches=p.fixtures.filter(f=>state(f.support)!==f.supportMaterial);
 ledger.status=ledger.mismatches.length||ledger.supportMismatches.length?'READBACK_MISMATCH':'APPLIED';await save(stage+'_ledger',ledger);
 console.log(JSON.stringify({stage,status:ledger.status,changed:p.changes.length,fixtures:p.fixtures.length,seaRemaining:positions(after,b=>b===sea).length,industrial:positions(after,b=>b.startsWith(lamp)).length}));
 if(ledger.status!=='APPLIED')throw Error('READBACK_MISMATCH');
}
async function view(name,pose){
 await guard();if(pose){await c.call('camera_move',{...pose,dry_run:true});await c.call('camera_move',pose);}
 for(let i=0;i<12;i++){
  let state=await c.call('camera_status');if(state.client_frame_ready){let capture=await c.call('capture_current_view');await save('view_'+name,{state,capture});console.log(JSON.stringify({state,capture}));return;}
  await new Promise(r=>setTimeout(r,250));
 }throw Error('CAMERA_NOT_SETTLED');
}
async function audit(){
 const before=await load('before'),after=await snapshot('final'),plans=await Promise.all(['pilot','remaining'].map(s=>load(s+'_plan')));
 const expected=before.cells.map(i=>before.palette[i]),planned=new Map(),fixtures=plans.flatMap(p=>p.fixtures);
 for(const p of plans){
  if(p.epoch!==epoch||p.id!==id)throw Error('AUDIT_PLAN_MISMATCH');
  for(const d of p.changes){const i=at(...d.local);if(expected[i]!==d.before)throw Error('AUDIT_PLAN_CHAIN_MISMATCH');expected[i]=d.block;planned.set(i,d);}
 }
 const mismatches=[],unplanned=[];
 let actualChanged=0;
 for(let i=0;i<expected.length;i++){
  const actual=after.palette[after.cells[i]];
  if(actual!==expected[i])mismatches.push({index:i,expected:expected[i],actual});
  if(actual!==before.palette[before.cells[i]]){actualChanged++;if(!planned.has(i))unplanned.push(i);}
 }
 const unsupported=fixtures.filter(f=>{
  const p=f.support,actual=after.palette[after.cells[at(p[0]-16,p[1]+33,p[2]+160)]];
  return actual!==f.supportMaterial||!supportBlocks.has(actual);
 });
 const result={epoch,id,plannedChanged:planned.size,actualChanged,originalSea:positions(before,b=>b===sea).length,
  seaRemaining:positions(after,b=>b===sea).length,industrial:positions(after,b=>b.startsWith(lamp)).length,
  nonAir:after.count.non_air,rooms:Object.fromEntries([...new Set(fixtures.map(f=>f.room))].map(r=>[r,fixtures.filter(f=>f.room===r).length])),
  mismatches,unplanned,unsupported};
 await save('final_audit',result);console.log(JSON.stringify(result));
 if(mismatches.length||unplanned.length||unsupported.length||result.seaRemaining||result.industrial!==239)throw Error('FINAL_AUDIT_FAILED');
}
async function main(){
 const action=process.argv[2],name=process.argv[3];
 if(action==='plan')await plan(name);
 else if(action==='apply')await apply(name);
 else if(action==='snapshot'){const s=await snapshot(name);console.log(JSON.stringify({count:s.count.non_air,sea:positions(s,b=>b===sea),industrial:positions(s,b=>b.startsWith(lamp))}));}
 else if(action==='view'){const a=process.argv.slice(4).map(Number);await view(name,a.length?{position:a.slice(0,3),yaw:a[3],pitch:a[4]}:null);}
 else if(action==='restore'){await guard();await c.call('camera_restore',{dry_run:true});console.log(await c.call('camera_restore'));await view('restored');}
 else if(action==='audit')await audit();
 else throw Error('UNKNOWN_ACTION');
}
main().catch(e=>{console.error(e.message);process.exitCode=1;});
