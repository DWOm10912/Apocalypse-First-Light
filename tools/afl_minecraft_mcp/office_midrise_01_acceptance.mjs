// One-world acceptance harness: actual exported NBT, live bridge readback, bounded cleanup.
// Placement/export commands are deliberately left to the game UI. No Gradle or region edits.
import {BridgeClient} from './bridge_client.mjs';
import {readFile,writeFile,mkdir} from 'node:fs/promises';
import {execFileSync} from 'node:child_process';
import {createHash} from 'node:crypto';
import path from 'node:path';
const c=new BridgeClient('./run'),epoch='6ef9c05f-0913-4f9e-83be-af3d566abca2';
const root=path.resolve('build/authoring_checks/office_midrise_01/export_20260910/fixed');
const source={id:'office_midrise_01',origin:[17,-33,-159],size:[35,86,39]};
const test={id:'office_midrise_01_rotation_test',origin:[17,120,-159],size:[39,86,39]};
const canonical=s=>{s=s.replace(/^Block\{([^}]+)\}/,'$1');const [name,p]=s.split('[');return name+(p?'['+p.slice(0,-1).split(',').sort().join(',')+']':'');};
const index=(size,x,y,z)=>(y*size[2]+z)*size[0]+x;
const load=async n=>JSON.parse(await readFile(path.join(root,n+'.json'),'utf8'));
async function save(n,v){await mkdir(root,{recursive:true});await writeFile(path.join(root,n+'.json'),JSON.stringify(v));}
async function guard(p){const s=await c.call('minecraft_status'),a=await c.call('authoring_info');
 if(s.world_session!==epoch||s.world!=='新的世界'||s.dimension!=='minecraft:overworld'||a.id!==p.id||JSON.stringify(a.min)!==JSON.stringify(p.origin)||a.width!==p.size[0]||a.height!==p.size[1]||a.depth!==p.size[2])throw Error('WORLD_OR_PLOT_CHANGED');return {s,a};}
async function snapshot(p,name){await guard(p);const palette=[],map=new Map(),cells=[];
 for(let y=0;y<p.size[1];y++){const r=await c.call('get_horizontal_slice',{target:'AUTHORING_SESSION',coordinate:p.origin[1]+y,encoding:'palette'});
  const decode=Object.fromEntries(Object.entries(r.palette).map(([s,i])=>[i,canonical(s)]));
  for(let z=0;z<p.size[2];z++)for(let x=0;x<p.size[0];x++){const s=decode[r.rows[z][x]];if(!s)throw Error('INCOMPLETE_SLICE');if(!map.has(s)){map.set(s,palette.length);palette.push(s);}cells.push(map.get(s));}}
 const count=await c.call('inspect_selection',{target:'AUTHORING_SESSION'}),r={...p,epoch,palette,cells,count};await save(name,r);return r;}
function exported(){const py='C:/Users/willi/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe';
 const code="import json;from pathlib import Path;from tools.afl_reference_map_importer.anvil_reader import nbt,inflate;print(json.dumps(nbt(inflate(Path('run/afl_authoring_exports/office_midrise_01.nbt').read_bytes(),1))))";
 const n=JSON.parse(execFileSync(py,['-c',code],{encoding:'utf8',maxBuffer:32*1024*1024}));
 if(JSON.stringify(n.size)!==JSON.stringify(source.size)||n.entities.length||n.blocks.some(b=>b.nbt)||n.blocks.length!==117390)throw Error('NBT_CONTRACT_CHANGED');
 const palette=n.palette.map(b=>canonical(b.Name+(b.Properties?'['+Object.entries(b.Properties).map(([k,v])=>k+'='+v).join(',')+']':'')));
 const cells=Array(117390),seen=new Set();for(const b of n.blocks){const i=index(n.size,...b.pos);if(seen.has(i)||b.pos.some((v,k)=>v<0||v>=n.size[k]))throw Error('INVALID_NBT_POSITION');seen.add(i);cells[i]=palette[b.state];}return {n,palette,cells};}
function stateRotate(s,turns){const [name,p]=s.split('[');if(!p)return s;const props=Object.fromEntries(p.slice(0,-1).split(',').map(v=>v.split('='))),out={};
 const dirs=['north','east','south','west'],dir=d=>dirs.includes(d)?dirs[(dirs.indexOf(d)+turns)%4]:d;
 for(const [k,v] of Object.entries(props)){if(k==='facing')out[k]=dir(v);else if(dirs.includes(k))out[dir(k)]=v;else if(k==='axis'&&turns%2&&['x','z'].includes(v))out[k]=v==='x'?'z':'x';else if(k==='rotation')out[k]=String((Number(v)+4*turns)%16);else out[k]=v;}
 return canonical(name+'['+Object.entries(out).map(([k,v])=>k+'='+v).join(',')+']');}
function expected(turns,settled=false){const e=exported(),cells=Array(39*86*39).fill('minecraft:air');
 if(settled){
  if(e.palette.some(s=>/_log\b|_wood\b/.test(s)))throw Error('LEAF_DISTANCE_REQUIRES_NEW_REVIEW');
  const original=[...e.cells];
  for(let y=0;y<86;y++)for(let z=0;z<39;z++)for(let x=0;x<35;x++){const i=index(source.size,x,y,z),b=original[i];
   if(b==='minecraft:oak_leaves[distance=1,persistent=true,waterlogged=false]')e.cells[i]=b.replace('distance=1','distance=7');
   if(b==='minecraft:iron_bars[east=false,north=false,south=false,waterlogged=false,west=false]'){
    const properties={waterlogged:'false'};for(const [dir,dx,dz] of [['north',0,-1],['east',1,0],['south',0,1],['west',-1,0]])properties[dir]=String(x+dx>=0&&x+dx<35&&z+dz>=0&&z+dz<39&&original[index(source.size,x+dx,y,z+dz)].startsWith('minecraft:iron_bars['));
    e.cells[i]=canonical('minecraft:iron_bars['+Object.entries(properties).map(([k,v])=>k+'='+v).join(',')+']');
   }
  }
 }
 for(let y=0;y<86;y++)for(let z=0;z<39;z++)for(let x=0;x<35;x++){const [X,Z]=[[x,z],[38-z,x],[34-x,38-z],[z,34-x]][turns];cells[index(test.size,X,y,Z)]=stateRotate(e.cells[index(source.size,x,y,z)],turns);}return cells;}
const decoded=s=>s.cells.map(i=>s.palette[i]);
function compare(expected,actual){const mismatch=[];for(let i=0;i<expected.length;i++)if(expected[i]!==actual[i])mismatch.push({i,expected:expected[i],actual:actual[i]});return mismatch;}
async function audit(turns){const s=await snapshot(test,'rotation_'+turns),actual=decoded(s),mismatch=compare(expected(turns,true),actual),raw=compare(expected(turns),actual);
 const lamp=actual.filter(s=>s.startsWith('apocalypse_firstlight:industrial_utility_light')),buttons=actual.filter(s=>s.startsWith('minecraft:polished_blackstone_button[')),report={turns,degrees:turns*90,nonAir:s.count.non_air,industrialLights:lamp.length,buttons:buttons.length,vanillaStateUpdates:raw,mismatches:mismatch,entities:s.count.entities,blockEntities:s.count.block_entities};
 await save('rotation_'+turns+'_audit',report);console.log(JSON.stringify({...report,vanillaStateUpdates:raw.length,mismatches:mismatch.length,examples:mismatch.slice(0,12)}));if(mismatch.length||lamp.length!==239||buttons.length!==15||s.count.non_air!==29922||s.count.entities!==0||s.count.block_entities!==0)throw Error('ROTATION_MISMATCH');}
async function clear(turns){await guard(test);await load('rotation_'+turns+'_audit');
 const captured=await load('rotation_'+turns),before=await snapshot(test,'clear_'+turns+'_before');if(compare(decoded(captured),decoded(before)).length||before.count.entities||before.count.block_entities)throw Error('TEST_COPY_CHANGED');
 const args={min:test.origin,max:[55,205,-121],block:'minecraft:air'};const dry=await c.call('we_set',{...args,dry_run:true});await save('clear_'+turns+'_intent',{epoch,args,dry});const result=await c.call('we_set',args);await save('clear_'+turns+'_result',result);
 const after=await snapshot(test,'clear_'+turns+'_after');if(after.count.non_air||after.count.entities)throw Error('CLEANUP_NOT_EMPTY');console.log(JSON.stringify({cleared:true,nonAir:after.count.non_air}));}
async function view(name,pose,p=test){await guard(p);if(pose){await c.call('camera_move',{...pose,dry_run:true});await c.call('camera_move',pose);}
 for(let i=0;i<20;i++){const state=await c.call('camera_status');if(state.client_frame_ready){const capture=await c.call('capture_current_view');await save('view_'+name,{state,capture});console.log(JSON.stringify({state,capture}));return;}await new Promise(r=>setTimeout(r,250));}throw Error('CAMERA_NOT_READY');}
async function main(){const [action,arg,...rest]=process.argv.slice(2);
 if(action==='repair-buttons'){
  try{await load('button_repair_intent');throw Error('REPAIR_ALREADY_ATTEMPTED');}catch(e){if(e.code!=='ENOENT')throw e;}
  const before=await snapshot(source,'button_repair_before'),cells=decoded(before),operations=[];
  const button='minecraft:polished_blackstone_button[face=wall,facing=south,powered=false]';
  for(let floor=0;floor<15;floor++){const y=-25+5*floor,i=index(source.size,12,y+33,17),j=index(source.size,12,y+33,18);
   if(cells[i]!==button||cells[j]!=='minecraft:air')throw Error('BUTTON_ZONE_CHANGED');
   operations.push({min:[29,y,-142],max:[29,y,-142],block:'minecraft:smooth_quartz'},{min:[29,y,-141],max:[29,y,-141],block:button});cells[i]='minecraft:smooth_quartz';cells[j]=button;
  }
  const dry=await c.call('we_batch_set',{operations,dry_run:true});await save('button_repair_intent',{epoch,operations,dry});await save('button_repair_result',await c.call('we_batch_set',{operations}));
  const after=await snapshot(source,'button_repair_after'),mismatches=compare(cells,decoded(after));await save('button_repair_audit',{mismatches,nonAir:after.count.non_air,changed:30});console.log(JSON.stringify({changed:30,nonAir:after.count.non_air,mismatches:mismatches.length}));if(mismatches.length)throw Error('REPAIR_READBACK_FAILED');
 }
 else if(action==='source'){const s=await snapshot(source,'source'),e=exported(),mismatch=compare(e.cells,decoded(s)),validation=await c.call('authoring_validate');
  const files=['run/afl_authoring_exports/office_midrise_01.nbt','src/main/resources/data/apocalypse_firstlight/structures/office_midrise_01.nbt'],hashes=await Promise.all(files.map(async file=>({file,sha256:createHash('sha256').update(await readFile(file)).digest('hex')})));
  const r={epoch,bounds:s,validation,hashes,mismatches:mismatch};delete r.bounds.cells;await save('export_audit',r);console.log(JSON.stringify({validation,hashes,mismatches:mismatch.length}));if(mismatch.length||hashes[0].sha256!==hashes[1].sha256)throw Error('EXPORT_MISMATCH');}
 else if(action==='preflight'){const s=await snapshot(test,'test_preflight');if(s.count.non_air||s.count.entities||s.count.block_entities)throw Error('TEST_AREA_NOT_EMPTY');console.log(JSON.stringify({nonAir:s.count.non_air,entities:s.count.entities}));}
 else if(action==='audit')await audit(Number(arg));
 else if(action==='clear')await clear(Number(arg));
 else if(action==='view'){const v=rest.map(Number);await view(arg,v.length?{position:v.slice(0,3),yaw:v[3],pitch:v[4]}:null);}
 else if(action==='source-view'){const v=rest.map(Number);await view(arg,{position:v.slice(0,3),yaw:v[3],pitch:v[4]},source);}
 else if(action==='restore'){const p=arg==='source'?source:test;await guard(p);await c.call('camera_restore',{dry_run:true});console.log(await c.call('camera_restore'));await view('restored',null,p);}
 else if(action==='cancel'){const p=arg==='source'?source:test;await guard(p);const camera=await c.call('camera_status');if(camera.return_available)throw Error('RESTORE_CAMERA_FIRST');console.log(await c.call('authoring_cancel'));}
 else throw Error('UNKNOWN_ACTION');}
main().catch(e=>{console.error(e.message);process.exitCode=1;});
