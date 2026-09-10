// Authorized live refinement, 2026-09-10. No compilation, save editing, export or guard bypass.
import {BridgeClient} from './bridge_client.mjs';
import {mkdir,readFile,writeFile} from 'node:fs/promises';
import path from 'node:path';
const c=new BridgeClient('./run');
const epoch='337fcee5-8fc8-4fcf-b519-ec666e15681d',id='modern_glass_tower_02';
const origin=[16,-33,-160],size=[37,90,41],dir=path.resolve('build/authoring_checks',id,'refine_20260910');
const at=(x,y,z)=>(y*41+z)*37+x;
const pos=(x,y,z)=>[x+16,y-33,z-160];
const floors=Array.from({length:15},(_,i)=>6+5*i);
const native=s=>s.replace(/^Block\{([^}]+)\}/,'$1');
const air='minecraft:air';
const canonical=s=>{const m=s.match(/^([^[]+)(?:\[(.*)\])?$/);return m[1]+(m[2]?'['+m[2].split(',').sort().join(',')+']':'');};
async function json(file,value){await mkdir(dir,{recursive:true});await writeFile(path.join(dir,file+'.json'),JSON.stringify(value,null,2));}
async function guard(){
 const s=await c.call('minecraft_status'),a=await c.call('authoring_info');
 if(s.world_session!==epoch||s.world!=='新的世界'||s.dimension!=='minecraft:overworld'||a.id!==id||JSON.stringify(a.min)!==JSON.stringify(origin)||JSON.stringify(a.max)!=='[52,56,-120]')throw Error('WORLD_OR_PLOT_CHANGED');
 return {s,a};
}
async function snapshot(label){
 await guard();const cells=Array(37*90*41),palette=[],lookup=new Map();
 for(let y=0;y<90;y++){
  const r=await c.call('get_horizontal_slice',{target:'AUTHORING_SESSION',coordinate:y-33,encoding:'palette'});
  const decode=Object.fromEntries(Object.entries(r.palette).map(([s,v])=>[v,canonical(native(s))]));
  for(let z=0;z<41;z++)for(let x=0;x<37;x++){
   const s=decode[r.rows[z][x]];if(!s)throw Error('INCOMPLETE_SLICE');if(!lookup.has(s)){lookup.set(s,palette.length);palette.push(s);}cells[at(x,y,z)]=lookup.get(s);
  }
 }
 const count=await c.call('inspect_selection',{target:'AUTHORING_SESSION'});
 const result={epoch,id,origin,size,palette,cells,count};await json(label,result);return result;
}
function recipe(stage,initial){
 const cells=initial.cells.map(n=>initial.palette[n]),original=[...cells];
 const get=(x,y,z)=>cells[at(x,y,z)];
 function set(x,y,z,b){if(![x,y,z].every(Number.isInteger)||x<0||x>36||y<0||y>89||z<0||z>40)throw Error('LOCAL_BOUNDS');cells[at(x,y,z)]=canonical(b.includes(':')?b:'minecraft:'+b);}
 function box(x,y,z,X,Y,Z,b,filter=()=>true){for(let v=y;v<=Y;v++)for(let w=z;w<=Z;w++)for(let u=x;u<=X;u++)if(filter(get(u,v,w)))set(u,v,w,b);}
 const glass=s=>s.includes('stained_glass');
 const stairs=(material,face)=>`${material}_stairs[facing=${face},half=bottom,shape=straight,waterlogged=false]`;
 const slab=material=>`${material}_slab[type=top,waterlogged=false]`;
 const door=(x,y,z,face='south')=>{set(x,y,z,`birch_door[facing=${face},half=lower,hinge=left,open=false,powered=false]`);set(x,y+1,z,`birch_door[facing=${face},half=upper,hinge=left,open=false,powered=false]`);};
 if(stage==='facades'){
  // Remove only the two original outdoor lamps and their now-unused iron-bar posts.
  for(const x of [2,34]){if(get(x,5,3)!=='minecraft:sea_lantern')throw Error('OUTDOOR_LAMP_CHANGED');set(x,5,3,'air');box(x,1,3,x,4,3,'air',s=>s.startsWith('minecraft:iron_bars'));}
  // Main narrow faces: opaque spandrels and continuous structural mullions.
  for(const z of [8,31]){
   for(const y of floors){box(11,y+1,z,25,y+1,z,'cyan_terracotta',glass);box(11,y,z,25,y,z,'gray_concrete');}
   for(const x of [14,22])box(x,7,z,x,76,z,'light_gray_concrete',glass);
  }
  // On the long side faces, apply the same hierarchy at each step of the silhouette.
  for(const [x,z,Z,lo,hi] of [[4,10,29,6,61],[32,10,29,6,41],[10,8,31,61,81],[26,8,31,41,81]]){
   for(const y of floors.filter(y=>y>=lo&&y<hi)){
    box(x,y,z,x,y,Z,'gray_concrete');box(x,y+1,z,x,y+1,Z,'cyan_terracotta',glass);
    // Slim projected sill: architecture, not an added balcony floor.
    const outside=x===4||x===10?x-1:x+1;
    box(outside,y,z+1,outside,y,Z-1,'polished_andesite_slab[type=top,waterlogged=false]',s=>s===air);
   }
   for(const zz of [z,z+6,Z-6,Z]){
    box(x,lo+1,zz,x,hi-1,zz,'light_gray_concrete',glass);
    const outside=x===4||x===10?x-1:x+1;
    box(outside,lo+1,zz,outside,hi,zz,'light_gray_concrete',s=>s===air);
   }
  }
  // Frame the tower face without covering the existing white diagonal wing braces.
  for(const z of [7,32])for(const x of [10,26])box(x,7,z,x,76,z,'light_gray_concrete',s=>s===air);
  // Podium window sills, solid service-side infill, extra verticals on long glazing.
  for(const x of [3,33])for(const z of [10,20,30])box(x,1,z,x,5,z,'light_gray_concrete',glass);
  box(3,1,6,3,1,34,'light_gray_concrete',glass);box(33,1,6,33,1,34,'light_gray_concrete',glass);
  box(4,1,5,32,2,5,'light_gray_concrete',glass);
  for(const x of [9,15,21,27])box(x,1,5,x,5,5,'light_gray_concrete');
  for(const z of [5,35])box(4,1,z,32,1,z,'light_gray_concrete',glass);
 }
 else if(stage==='rooms'){
  for(const y of floors){
   // These doubled glazed walls are INTERNAL between tower and occupied wings, not windows.
   if(y<61){box(9,y+1,11,10,y+4,28,'smooth_quartz',glass);box(9,y+1,20,10,y+3,22,'air');}
   if(y<41){box(26,y+1,11,27,y+4,28,'smooth_quartz',glass);box(26,y+1,20,27,y+3,22,'air');}
   // Meeting room: solid lower/top bands and only two narrow observation panes.
   box(21,y+1,23,21,y+3,29,'smooth_quartz',glass);box(21,y+1,22,25,y+3,22,'smooth_quartz',glass);
   set(21,y+2,25,'glass_pane[east=false,north=true,south=true,west=false,waterlogged=false]');
   set(21,y+2,27,'glass_pane[east=false,north=true,south=true,west=false,waterlogged=false]');
   // Close only one half of the old two-wide doorway. Preserve a clear north-to-south access.
   box(23,y+1,22,23,y+2,22,'smooth_quartz');door(22,y+1,22);
   // A real enclosed single-user WC: preserve entrance and create privacy at the former glazed wall.
   box(10,y+1,18,10,y+3,21,'smooth_quartz');
   box(11,y,19,13,y,20,'smooth_quartz');
   set(11,y+1,19,'smooth_quartz');set(11,y+2,19,'quartz_slab[type=bottom,waterlogged=false]');
   set(11,y+1,20,stairs('quartz','north'));
   set(13,y+1,19,'cauldron');set(13,y+2,18,'polished_blackstone_button[face=wall,facing=south,powered=false]');
   door(13,y+1,21);
   // Better circulation finish; not furniture across the lift / stair circulation strip.
   box(15,y,19,20,y,22,'light_gray_concrete');
   // Small storage/print rooms in the west wing, with a south-facing door to the lounge aisle.
   if(y<61){
    box(5,y+1,24,8,y+3,24,'smooth_quartz');door(7,y+1,24);
    box(5,y+1,26,6,y+2,26,'bookshelf');
    set(8,y+1,27,'spruce_planks');set(8,y+2,27,'gray_concrete');
    // Retain the existing planted corner, not a bare glass box.
   }
  }
  // Ground-floor public washroom and janitor store in the unoccupied north-west lobby area.
  box(5,1,6,9,4,6,'smooth_quartz');box(5,1,7,5,4,12,'smooth_quartz');box(9,1,7,9,4,12,'smooth_quartz');
  box(5,1,12,9,4,12,'smooth_quartz');door(8,1,12);box(6,0,7,8,0,11,'smooth_quartz');
  // Relocate the old corner plant so the sanitary fixture is not clipped into foliage.
  box(5,1,7,5,3,7,'smooth_quartz');set(6,1,7,'smooth_quartz');set(6,2,7,'quartz_slab[type=bottom,waterlogged=false]');
  set(6,1,8,stairs('quartz','north'));set(8,1,7,'cauldron');
  box(5,1,14,9,4,14,'smooth_quartz');box(5,1,15,5,4,18,'smooth_quartz');box(9,1,15,9,4,18,'smooth_quartz');
  box(5,1,18,9,4,18,'smooth_quartz');door(8,1,18);box(6,1,15,7,2,15,'bookshelf');
  // Reception backing and a display ledge make the ground floor read as a lobby.
  box(11,1,25,14,3,25,'smooth_quartz');box(11,1,25,14,1,25,'spruce_planks');
  box(11,3,25,14,3,25,'polished_andesite_slab[type=top,waterlogged=false]');
 }
 else throw Error('UNKNOWN_STAGE');
 const diff=new Map();for(let y=0;y<90;y++)for(let z=0;z<41;z++)for(let x=0;x<37;x++){const i=at(x,y,z);if(cells[i]!==original[i])diff.set(`${x},${y},${z}`,{x,y,z,b:cells[i],before:original[i]});}
 // Exact changed cells -> X runs -> Z rectangles -> Y prisms. No unchanged filler cells.
 const runs=[];
 for(let y=0;y<90;y++)for(let z=0;z<41;z++)for(let x=0;x<37;x++){
  const d=diff.get(`${x},${y},${z}`);if(!d)continue;let X=x;while(X+1<37&&diff.get(`${X+1},${y},${z}`)?.b===d.b)X++;
  runs.push({x,X,y,Y:y,z,Z:z,b:d.b});x=X;
 }
 function merge(input,axis){
  const map=new Map(),output=[],upper=axis.toUpperCase();
  for(const r of input.sort((a,b)=>a[axis]-b[axis])){
   const key=['x','X','y','Y','z','Z','b'].filter(k=>k!==axis&&k!==upper).map(k=>r[k]).join('|'),prev=map.get(key);
   if(prev&&prev[upper]+1===r[axis])prev[upper]=r[upper];else{const copy={...r};output.push(copy);map.set(key,copy);}
  }return output;
 }
 const ops=merge(merge(runs,'z'),'y').map(r=>({min:pos(r.x,r.y,r.z),max:pos(r.X,r.Y,r.Z),block:r.b}));
 return {stage,epoch,id,diff:[...diff.values()],operations:ops};
}
async function plan(stage){const baseline=await snapshot(stage+'_before'),p=recipe(stage,baseline);await json(stage+'_plan',p);console.log(JSON.stringify({stage,changes:p.diff.length,operations:p.operations.length,batches:Math.ceil(p.operations.length/120)}));}
async function apply(stage){
 await guard();const p=JSON.parse(await readFile(path.join(dir,stage+'_plan.json'),'utf8'));if(p.epoch!==epoch)throw Error('PLAN_EPOCH');
 const ledgerFile=path.join(dir,stage+'_ledger.json');
 try{const prev=JSON.parse(await readFile(ledgerFile,'utf8'));if(!process.argv.includes('--retry-preflight')||prev.status!=='PREFLIGHT'||prev.completed.length||prev.pending!==undefined)throw Error('STAGE_ALREADY_ATTEMPTED');}catch(e){if(e.code!=='ENOENT')throw e;}
 const live=await snapshot(stage+'_preflight');for(const d of p.diff)if(live.palette[live.cells[at(d.x,d.y,d.z)]]!==d.before)throw Error('WORLD_CHANGED_SINCE_PLAN '+[d.x,d.y,d.z]);
 const batches=[];for(let i=0;i<p.operations.length;i+=120)batches.push(p.operations.slice(i,i+120));
 const ledger={stage,epoch,status:'PREFLIGHT',completed:[]};await json(stage+'_ledger',ledger);
 for(const operations of batches)await c.call('we_batch_set',{operations,dry_run:true});
 for(let i=0;i<batches.length;i++){
  ledger.status='WRITING';ledger.pending=i;await json(stage+'_ledger',ledger);
  const r=await c.call('we_batch_set',{operations:batches[i]});ledger.completed.push(r);delete ledger.pending;await json(stage+'_ledger',ledger);console.log('APPLIED',stage,i);
 }
 const after=await snapshot(stage+'_after');
 const mismatches=p.diff.filter(d=>after.palette[after.cells[at(d.x,d.y,d.z)]]!==d.b);
 ledger.status=mismatches.length?'READBACK_MISMATCH':'APPLIED';ledger.mismatches=mismatches;await json(stage+'_ledger',ledger);
 console.log(JSON.stringify({stage,changes:p.diff.length,mismatches:mismatches.length,non_air:after.count.non_air,entities:after.count.entities}));
 if(mismatches.length)throw Error('READBACK_MISMATCH');
}
async function view(label,pose){
 await guard();if(pose){await c.call('camera_move',{...pose,dry_run:true});await c.call('camera_move',pose);}
 for(let i=0;i<12;i++){const s=await c.call('camera_status');if(s.client_frame_ready){const capture=await c.call('capture_current_view');await json('view_'+label,{s,capture});console.log(JSON.stringify({s,capture}));return;}await new Promise(r=>setTimeout(r,250));}
 throw Error('CAMERA_NOT_SETTLED');
}
async function main(){
 const action=process.argv[2],label=process.argv[3];
 if(action==='plan')await plan(label);
 else if(action==='apply')await apply(label);
 else if(action==='snapshot'){const r=await snapshot(label);console.log(JSON.stringify({non_air:r.count.non_air,entities:r.count.entities,palette:r.count.palette}));}
 else if(action==='view'){const a=process.argv.slice(4).map(Number);await view(label,a.length?{position:a.slice(0,3),yaw:a[3],pitch:a[4]}:null);}
 else if(action==='restore'){await guard();await c.call('camera_restore',{dry_run:true});console.log(await c.call('camera_restore'));await view('restored');}
 else throw Error('UNKNOWN_ACTION');
}
main().catch(e=>{console.error(e.message);process.exitCode=1;});
