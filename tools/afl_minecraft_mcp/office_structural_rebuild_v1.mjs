// SUPERSEDED: user cancelled the36-block design; original tower restored. Do not reapply.
// Historical structural-only recipe/recovery evidence. Never exports NBT or edits saves.
import {BridgeClient} from './bridge_client.mjs';
import {mkdir,readFile,writeFile} from 'node:fs/promises';
import path from 'node:path';
const c=new BridgeClient('./run');
const epoch='dc8e97dc-0438-4667-97e5-79dad371ba00';
const origin=[-48,-33,-112],size=[35,86,39],id='office_midrise_01';
const dir=path.resolve('build/authoring_checks/office_midrise_01/structural_v1_20260912');
const idx=(x,y,z)=>((y+33)*39+z+112)*35+x+48;
const canon=s=>s.replace(/^Block\{([^}]+)\}/,'$1').replace(/\[([^\]]+)\]/,(_,p)=>'['+p.split(',').sort().join(',')+']');
const air='minecraft:air';
const floors=[-33,-26,-20,-14,-8],roof=-2;
async function save(n,v){await mkdir(dir,{recursive:true});await writeFile(path.join(dir,n+'.json'),JSON.stringify(v));}
async function read(n){return JSON.parse(await readFile(path.join(dir,n+'.json'),'utf8'));}
async function guard(){const s=await c.call('minecraft_status'),a=await c.call('authoring_info');if(s.world_session!==epoch||s.world!=='新的世界'||s.dimension!=='minecraft:overworld'||a.id!==id||JSON.stringify(a.min)!==JSON.stringify(origin)||JSON.stringify(a.max)!=='[-14,52,-74]')throw Error('WORLD_PLOT_CHANGED');return{s,a};}
async function snapshot(n){await guard();const cells=Array(117390),palette=[],map=new Map();for(let y=-33;y<=52;y++){const s=await c.call('get_horizontal_slice',{target:'AUTHORING_SESSION',coordinate:y,encoding:'palette'}),p=Object.fromEntries(Object.entries(s.palette).map(([k,v])=>[v,canon(k)]));for(let z=0;z<39;z++)for(let x=0;x<35;x++){const v=p[s.rows[z][x]];if(v===undefined)throw Error('SLICE');if(!map.has(v)){map.set(v,palette.length);palette.push(v);}cells[idx(x-48,y,z-112)]=map.get(v);}}const r={epoch,origin,size,palette,cells,count:await c.call('inspect_selection',{target:'AUTHORING_SESSION'})};await save(n,r);return r;}
function design(before){
 const base=before.cells.map(i=>before.palette[i]),cells=Array(base.length).fill(air);
 const get=(x,y,z)=>cells[idx(x,y,z)];
 const set=(x,y,z,b)=>{if(x< -48||x> -14||y< -33||y>52||z< -112||z> -74)throw Error('BOUNDS');cells[idx(x,y,z)]=canon(b.includes(':')?b:'minecraft:'+b);};
 const box=(x,y,z,X,Y,Z,b)=>{for(let j=y;j<=Y;j++)for(let k=z;k<=Z;k++)for(let i=x;i<=X;i++)set(i,j,k,b);};
 // Keep the entire existing ground pad, without carrying old above-ground decorations.
 for(let z=-112;z<=-74;z++)for(let x=-48;x<=-14;x++)set(x,-33,z,base[idx(x,-33,z)]);
 function plate(x,z,X,Z,y){box(x,y,z,X,y,Z,'smooth_quartz');box(x,y,z,X,y,z,'gray_concrete');box(x,y,Z,X,y,Z,'gray_concrete');box(x,y,z,x,y,Z,'gray_concrete');box(X,y,z,X,y,Z,'gray_concrete');}
 // Floor undersides are the ceilings: one continuous slab, no additional soffit.
 plate(-46,-108,-16,-78,-26);
 for(const y of [-20,-14,-8])plate(-45,-105,-17,-82,y);
 plate(-43,-105,-19,-82,roof);
 function storey(x,z,X,Z,lo,hi,lobby=false){
  for(const zz of [z,Z]){box(x,lo+1,zz,X,hi-1,zz,lobby?'light_blue_stained_glass':'blue_stained_glass');if(!lobby)box(x,lo+1,zz,X,lo+1,zz,'cyan_terracotta');}
  for(const xx of [x,X]){box(xx,lo+1,z,xx,hi-1,Z,'light_blue_stained_glass');if(!lobby)box(xx,lo+1,z,xx,lo+1,Z,'cyan_terracotta');}
  const columns=lobby?[x,x+6,x+12,X-12,X-6,X]:[x,x+6,X-6,X];
  for(const xx of new Set(columns))for(const zz of [z,Z])box(xx,lo+1,zz,xx,hi-1,zz,'light_gray_concrete');
  for(const zz of [z,z+6,Z-6,Z])for(const xx of [x,X])box(xx,lo+1,zz,xx,hi-1,zz,'light_gray_concrete');
  // Opaque northern service bay aligned with core; not glass behind stair walls.
  box(-41,lo+1,z,-21,hi-1,z,'light_gray_concrete');
  for(const xx of [-41,-36,-31,-26,-21])box(xx,lo+1,z,xx,hi-1,z,'gray_concrete');
 }
 storey(-46,-108,-16,-78,-33,-26,true);
 for(let i=1;i<4;i++)storey(-45,-105,-17,-82,floors[i],floors[i+1]);
 storey(-43,-105,-19,-82,-8,roof);
 // South recessed portal: exact 2x1x2 future commercial-door void and glazed transom.
 box(-33,-32,-78,-30,-30,-78,'smooth_quartz');box(-32,-32,-78,-31,-31,-78,'air');
 box(-33,-29,-78,-30,-28,-78,'light_blue_stained_glass');box(-33,-27,-78,-30,-27,-78,'light_gray_concrete');
 // Canopy / jambs are structure, not props. Keep a flat entry path.
 box(-34,-30,-77,-29,-30,-76,'smooth_quartz_slab[type=top,waterlogged=false]');
 box(-34,-32,-78,-34,-27,-78,'light_gray_concrete');box(-29,-32,-78,-29,-27,-78,'light_gray_concrete');
 // West service opening, 2 wide x 3 high, kept clear for later door selection.
 box(-46,-32,-103,-46,-30,-102,'air');box(-46,-29,-104,-46,-29,-101,'gray_concrete');
 // Two capped decorative elevator shafts; no walkable shaft opening or machinery.
 for(const [x,X] of [[-40,-37],[-35,-32]]){
  box(x,-32,-103,X,-3,-96,'smooth_quartz');box(x+1,-32,-102,X-1,-3,-97,'air');
  for(const y of floors){box(x,y+1,-96,X,y+5,-96,'gray_concrete');box(x+1,y+1,-96,X-1,y+3,-96,'iron_block');}
 }
 // Stair enclosure, generous 8-wide internal width and two 3-wide flights.
 box(-31,-32,-103,-22,1,-103,'smooth_quartz');box(-31,-32,-102,-31,1,-93,'smooth_quartz');box(-22,-32,-102,-22,1,-93,'smooth_quartz');
 box(-30,-32,-102,-23,1,-95,'air');
 box(-27,-32,-97,-26,1,-95,'smooth_quartz');
 for(let i=0;i<floors.length;i++){
  const y=floors[i],up=i===4?roof:floors[i+1],rise=up-y;
  box(-30,y,-94,-23,y,-92,'smooth_quartz');
  box(-31,y+1,-93,-22,up-1,-93,'smooth_quartz');box(-30,y+1,-93,-23,y+3,-93,'air');
  for(let k=1;k<=3;k++)box(-30,y+k,-94-k,-28,y+k,-94-k,'stone_brick_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]');
  box(-30,y+3,-100,-23,y+3,-98,'smooth_quartz');
  for(let k=4;k<=rise;k++)box(-25,y+k,-101+k,-23,y+k,-101+k,'stone_brick_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]');
  box(-30,up,-94,-23,up,-92,'smooth_quartz');
  if(rise===7)box(-25,up,-94,-23,up,-94,'stone_brick_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]');
 }
 // Perimeter roof guard and setback terraces; no HVAC equipment.
 const ring=(x,z,X,Z,y)=>{box(x,y,z,X,y,z,'light_gray_concrete');box(x,y,Z,X,y,Z,'light_gray_concrete');box(x,y,z,x,y,Z,'light_gray_concrete');box(X,y,z,X,y,Z,'light_gray_concrete');};
 ring(-43,-105,-19,-82,-1);
 for(const xx of [-45,-17])box(xx,-7,-105,xx,-7,-82,'light_gray_concrete');
 for(const z of [-105,-82]){box(-45,-7,z,-44,-7,z,'light_gray_concrete');box(-18,-7,z,-17,-7,z,'light_gray_concrete');}
 // Lobby roof edge is a slim fascia; not a tall additional storey.
 box(-46,-25,-108,-16,-25,-108,'light_gray_concrete');
 box(-31,-1,-93,-22,1,-93,'smooth_quartz');box(-29,-1,-93,-26,1,-93,'air');
 box(-31,2,-103,-22,2,-93,'smooth_quartz_slab[type=top,waterlogged=false]');
 box(-40,-1,-100,-35,-1,-97,'polished_andesite_slab[type=bottom,waterlogged=false]');
 // Exact diff cuboids. Unchanged cells are never included to bypass scope checks.
 const runs=[];let changed=0;
 for(let y=-33;y<=52;y++)for(let z=-112;z<=-74;z++)for(let x=-48;x<=-14;x++){
  const k=idx(x,y,z),b=cells[k];if(b===base[k])continue;let X=x;while(X< -14&&cells[idx(X+1,y,z)]===b&&cells[idx(X+1,y,z)]!==base[idx(X+1,y,z)])X++;changed+=X-x+1;runs.push({x,X,y,Y:y,z,Z:z,b});x=X;
 }
 function merge(list,axis){const upper=axis.toUpperCase(),m=new Map(),out=[];for(const r of list.sort((a,b)=>a[axis]-b[axis])){const key=['x','X','y','Y','z','Z','b'].filter(k=>k!==axis&&k!==upper).map(k=>r[k]).join('|'),p=m.get(key);if(p&&p[upper]+1===r[axis])p[upper]=r[upper];else{const n={...r};out.push(n);m.set(key,n);}}return out;}
 const operations=merge(merge(runs,'z'),'y').map(r=>({min:[r.x,r.y,r.z],max:[r.X,r.Y,r.Z],block:r.b}));
 return {epoch,origin,size,changed,operations,cells};
}
async function plan(){try{await read('baseline');throw Error('BASELINE_ALREADY_EXISTS');}catch(e){if(e.code!=='ENOENT')throw e;}const b=await snapshot('baseline'),p=design(b);await save('plan',p);console.log(JSON.stringify({changes:p.changed,operations:p.operations.length,batches:Math.ceil(p.operations.length/120),planned_non_air:p.cells.filter(s=>s!==air).length}));}
async function apply(){await guard();try{await read('ledger');throw Error('ALREADY_ATTEMPTED_INSPECT_LEDGER');}catch(e){if(e.code!=='ENOENT')throw e;}const p=await read('plan'),b=await read('baseline'),live=await snapshot('preflight');if(live.cells.some((v,i)=>live.palette[v]!==b.palette[b.cells[i]]))throw Error('BASELINE_CONFLICT');const batches=[];for(let i=0;i<p.operations.length;i+=120)batches.push(p.operations.slice(i,i+120));const ledger={status:'PREFLIGHT',completed:[]};await save('ledger',ledger);for(const operations of batches)await c.call('we_batch_set',{operations,dry_run:true});for(let i=0;i<batches.length;i++){ledger.status='WRITING';ledger.pending=i;await save('ledger',ledger);const r=await c.call('we_batch_set',{operations:batches[i]});ledger.completed.push(r);delete ledger.pending;await save('ledger',ledger);console.log('APPLIED_BATCH',i+1,'/',batches.length);}const a=await snapshot('after');const mismatch=a.cells.flatMap((v,i)=>a.palette[v]!==p.cells[i]?[i]:[]);ledger.status=mismatch.length?'MISMATCH':'APPLIED_VERIFIED';ledger.mismatches=mismatch;await save('ledger',ledger);console.log(JSON.stringify({status:ledger.status,non_air:a.count.non_air,mismatches:mismatch.length}));}
async function view(label,args){await guard();const values=args.map(Number);if(values.length){const pose={position:values.slice(0,3),yaw:values[3],pitch:values[4]};await c.call('camera_move',{...pose,dry_run:true});await c.call('camera_move',pose);}for(let i=0;i<20;i++){const s=await c.call('camera_status');if(s.client_frame_ready){const capture=await c.call('capture_current_view');await save('view_'+label,{s,capture});console.log(JSON.stringify(capture));return;}await new Promise(r=>setTimeout(r,250));}throw Error('CAMERA_NOT_READY');}
async function rollback(){
 await guard();const l=await read('ledger'),p=await read('plan'),b=await read('baseline');
 if(l.status!=='APPLIED_VERIFIED'||l.completed.length!==21)throw Error('UNEXPECTED_HISTORY');
 try{await read('rollback_ledger');throw Error('ROLLBACK_ALREADY_ATTEMPTED');}catch(e){if(e.code!=='ENOENT')throw e;}
 const live=await snapshot('rollback_before');if(live.cells.some((v,i)=>live.palette[v]!==p.cells[i]))throw Error('LIVE_CHANGED_STOP_FOR_REVIEW');
 const r={status:'RESTORING',completed:[]};await save('rollback_ledger',r);
 for(let i=0;i<21;i++){await c.call('we_undo',{dry_run:true});r.pending=i;await save('rollback_ledger',r);r.completed.push(await c.call('we_undo',{}));delete r.pending;await save('rollback_ledger',r);console.log('UNDONE',i+1);}
 const a=await snapshot('restored');r.mismatches=a.cells.flatMap((v,i)=>a.palette[v]!==b.palette[b.cells[i]]?[i]:[]);r.status=r.mismatches.length?'RESTORE_MISMATCH':'RESTORED_BASELINE_VERIFIED';await save('rollback_ledger',r);console.log(JSON.stringify({status:r.status,non_air:a.count.non_air,height:a.count.occupied_height,mismatches:r.mismatches.length}));
}
const action=process.argv[2];
try{if(action==='plan')await plan();else if(action==='apply')await apply();else if(action==='rollback')await rollback();else if(action==='view')await view(process.argv[3],process.argv.slice(4));else if(action==='snapshot'){const s=await snapshot(process.argv[3]);console.log(JSON.stringify(s.count));}else if(action==='restore'){await guard();await c.call('camera_restore',{dry_run:true});console.log(await c.call('camera_restore'));}else throw Error('ACTION');}catch(e){console.error(e.message);process.exitCode=1;}
