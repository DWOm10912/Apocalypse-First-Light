// Live authoring only. Never compiles/deploys, edits saves, exports NBT or registers a building.
import {BridgeClient} from './bridge_client.mjs';
import {mkdir,readFile,writeFile} from 'node:fs/promises';
import path from 'node:path';
const stage=process.argv[2],c=new BridgeClient('./run');
const origin=[16,-33,-160],id='modern_glass_tower_02',epoch='38bbdeb6-b033-455b-ac5b-b5feff4cc937';
const reportDir=path.resolve('build/authoring_checks',id);
const ops=[],floors=Array.from({length:15},(_,i)=>6+5*i);
function box(x,y,z,X,Y,Z,b){
 if(x>X||y>Y||z>Z||x<0||X>36||y<0||Y>89||z<0||Z>40)throw Error('LOCAL_BOUNDS');
 ops.push({min:[x+16,y-33,z-160],max:[X+16,Y-33,Z-160],block:'minecraft:'+b});
}
const p=(x,y,z,b)=>box(x,y,z,x,y,z,b);
function shell(x,z,X,Z,b,t,glass='blue_stained_glass'){
 box(x,b,z,X,t,z,glass);box(x,b,Z,X,t,Z,glass);
 box(x,b,z+1,x,t,Z-1,'light_blue_stained_glass');box(X,b,z+1,X,t,Z-1,'light_blue_stained_glass');
}
function lamp(x,y,z){box(x,y,z,x+1,y,z,'sea_lantern');}
function plant(x,y,z){p(x,y,z,'light_gray_concrete');p(x,y+1,z,'oak_leaves[persistent=true,distance=1]');}
function seat(x,y,z,face='north'){p(x,y,z,`spruce_stairs[facing=${face},half=bottom,shape=straight,waterlogged=false]`);}
function desk(x,y,z){box(x,y,z,x+1,y,z,'spruce_slab[type=top,waterlogged=false]');p(x,y+1,z,'black_concrete');seat(x,y,z+2);}
if(stage==='massing'){
 box(1,0,1,35,0,39,'stone_bricks');box(3,0,5,33,0,35,'polished_andesite');
 shell(3,5,33,35,1,5,'light_blue_stained_glass');box(3,6,5,33,6,35,'light_gray_concrete');
 shell(10,8,26,31,7,80);shell(4,10,9,29,7,60);shell(27,10,32,29,7,40);
 for(const y of floors){box(10,y,8,26,y,31,'polished_andesite');if(y<61)box(4,y,10,9,y,29,'polished_andesite');if(y<41)box(27,y,10,32,y,29,'polished_andesite');}
 box(10,81,8,26,81,31,'gray_concrete');box(4,61,10,9,61,29,'gray_concrete');box(27,41,10,32,41,29,'gray_concrete');
 for(const y of floors){if(y<61)box(9,y+1,20,10,y+3,22,'air');if(y<41)box(26,y+1,20,27,y+3,22,'air');}
}
if(stage==='exterior'){
 for(const y of floors){
  box(11,y,31,25,y,31,'cyan_terracotta');box(11,y,8,25,y,8,'cyan_terracotta');
  if(y<61)box(4,y,10,9,y,10,'light_gray_concrete');if(y<41)box(27,y,10,32,y,10,'light_gray_concrete');
 }
 // Continuous thin light frame, recessed glazing, glass corner strips.
 for(const x of [10,26])for(const z of [8,31])box(x,6,z,x,82,z,'light_gray_concrete');
 for(const x of [14,22]){box(x,7,31,x,75,31,'light_blue_stained_glass');box(x,7,8,x,80,8,'light_blue_stained_glass');}
 for(const [x,X,top] of [[4,9,61],[27,32,41]]){
  for(const z of [9,30]){
   for(const u of [x,X])box(u,6,z,u,top,z,'white_concrete');
   for(let y=6;y<top;y+=10){box(x,y,z,X,y,z,'light_gray_concrete');
    for(let k=0;k<10&&y+k<top;k++){const t=Math.floor((y-6)/10)%2?k/9:1-k/9,u=x+Math.round((X-x)*t);p(u,y+k,z,'white_concrete');}
   }
  }
 }
 // Podium reads as a generous glazed lobby, not stacked tiny floors.
 for(const x of [3,9,15,21,27,33]){box(x,1,35,x,5,35,'light_gray_concrete');box(x,1,5,x,5,5,'light_gray_concrete');}
 for(const z of [5,15,25,35])for(const x of [3,33])box(x,1,z,x,5,z,'light_gray_concrete');
 box(2,6,4,34,6,36,'smooth_quartz_slab[type=top,waterlogged=false]');
 // Restore roof under tower; podium edge is a slim cornice only.
 box(4,6,6,32,6,34,'polished_andesite');
 box(16,1,35,20,4,35,'air');box(15,5,35,21,5,38,'smooth_quartz_slab[type=top,waterlogged=false]');
 box(16,0,35,20,0,39,'smooth_quartz');
 // Dense mechanical crown concentrated only at the top.
 for(let y=77;y<=81;y++)box(11,y,32,25,y,32,y%2?'polished_andesite_slab[type=top,waterlogged=false]':'gray_concrete');
 box(10,83,8,26,83,8,'light_blue_stained_glass');box(10,83,31,26,83,31,'light_blue_stained_glass');
 box(10,82,8,26,82,8,'light_gray_concrete');box(10,82,31,26,82,31,'light_gray_concrete');
}
if(stage==='core'){
 // Two separate enclosed decorative lift shafts; no working elevator is claimed.
 for(const [x,X] of [[11,14],[16,18]]){
  box(x,1,10,X,80,10,'gray_concrete');box(x,1,15,X,80,15,'gray_concrete');
  box(x,1,11,x,80,14,'gray_concrete');box(X,1,11,X,80,14,'gray_concrete');
 }
 box(19,1,9,19,80,17,'gray_concrete');box(25,1,9,25,80,17,'gray_concrete');
 box(20,1,9,24,80,9,'gray_concrete');
 // One continuous void. Stairs are constructed after all slabs, avoiding capped risers.
 box(20,1,10,24,83,15,'air');
 for(const y of [0,...floors]){
  const rise=y===0?6:5;
  if(y>=81)continue;
  // Three risers northward, broad half landing, two/three southward.
  for(let k=1;k<=3;k++)box(20,y+k,16-k,21,y+k,16-k,'stone_brick_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]');
  box(20,y+3,11,24,y+3,12,'polished_andesite');
  for(let k=4;k<=rise;k++)box(23,y+k,9+k,24,y+k,9+k,'stone_brick_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]');
  box(23,y+rise,15,24,y+rise,16,'polished_andesite');
  box(20,y,16,24,y,18,'polished_andesite');
  // Lift door panels recessed north of a 3-block lift lobby.
  for(const x of [12,17]){box(x,y+1,15,x+1,y+3,15,'iron_block');p(x,y+4,15,'sea_lantern');}
  lamp(20,y+4,17);
 }
 // Roof headhouse caps the stair safely, with a south-facing open passage.
 box(19,82,9,25,84,9,'gray_concrete');box(19,82,10,19,84,18,'gray_concrete');box(25,82,10,25,84,18,'gray_concrete');
 box(19,85,9,25,85,18,'smooth_quartz_slab[type=top,waterlogged=false]');
 box(19,82,18,25,84,18,'gray_concrete');box(21,82,18,23,84,18,'air');
}
if(stage==='interior'){
 for(let i=0;i<floors.length;i++){
  const y=floors[i];
  // Small WC with privacy partition and clear entry to the west; inert fixture blocks.
  box(11,y+1,18,14,y+3,18,'smooth_quartz');box(14,y+1,19,14,y+3,21,'smooth_quartz');box(11,y+1,21,12,y+3,21,'smooth_quartz');
  p(11,y+1,19,'quartz_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]');p(13,y+1,19,'smooth_quartz_slab[type=top,waterlogged=false]');
  // Tea counter leaves a full corridor to stair/lift lobby.
  box(16,y+1,18,18,y+1,18,'spruce_planks');p(16,y+2,18,'smooth_quartz_slab[type=bottom,waterlogged=false]');p(18,y+2,18,'black_concrete');
  // Meeting room along east glazing, open doorway at north end.
  box(21,y+1,23,21,y+3,29,'white_stained_glass');box(21,y+1,22,25,y+3,22,'white_stained_glass');box(22,y+1,22,23,y+2,22,'air');
  box(23,y+1,25,23,y+1,28,'spruce_slab[type=top,waterlogged=false]');seat(22,y+1,25,'east');seat(24,y+1,27,'west');
  for(const [x,z] of [[12,24],[17,24],[12,28],[17,28]])desk(x,y+1,z);
  plant(11,y+1,22);plant(25,y+1,30);
  lamp(16,y+4,23);lamp(23,y+4,26);lamp(12,y+4,20);
  // Furnish stepped wings where present: lounge west, project office east.
  if(y<61){box(5,y+1,13,7,y+1,13,'spruce_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]');box(6,y+1,15,7,y+1,16,'spruce_slab[type=bottom,waterlogged=false]');plant(5,y+1,27);lamp(6,y+4,22);}
  if(y<41){desk(28,y+1,15);plant(31,y+1,27);lamp(29,y+4,23);}
 }
}
if(stage==='lobby_roof'){
 // Reception beside the clear entrance-to-lift axis, cafe and waiting areas.
 box(11,1,27,14,1,28,'spruce_planks');box(11,2,27,14,2,27,'spruce_slab[type=bottom,waterlogged=false]');p(12,2,28,'black_concrete');
 for(const z of [23,29]){box(6,1,z,8,1,z,'spruce_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]');box(10,1,z,10,1,z+1,'smooth_quartz_slab[type=bottom,waterlogged=false]');}
 box(27,1,17,31,1,17,'spruce_planks');box(29,2,17,31,2,17,'smooth_quartz_slab[type=bottom,waterlogged=false]');
 for(const z of [24,29]){box(28,1,z,29,1,z+1,'spruce_slab[type=top,waterlogged=false]');seat(27,1,z,'east');seat(30,1,z+1,'west');}
 for(const [x,z] of [[5,7],[31,7],[5,33],[31,33],[15,19]])plant(x,1,z);
 for(const x of [7,17,28])for(const z of [11,22,31])lamp(x,5,z);
 // Roof parapets and plant on all three roof terraces.
 for(const [x,X,z,Z,y] of [[10,26,8,31,81],[4,9,10,29,61],[27,32,10,29,41]]){
  box(x,y+1,z,X,y+1,z,'light_gray_concrete');box(x,y+1,Z,X,y+1,Z,'light_gray_concrete');
  box(x,y+1,z+1,x,y+1,Z-1,'light_gray_concrete');box(X,y+1,z+1,X,y+1,Z-1,'light_gray_concrete');
 }
 for(const [x,z,y] of [[12,22,82],[20,25,82],[6,16,62],[29,16,42]]){
  box(x,y,z,x+1,y+1,z+3,'polished_andesite');box(x,y+2,z+1,x+1,y+2,z+2,'iron_bars');
 }
 box(15,82,23,20,82,23,'gray_concrete');box(20,82,23,20,82,24,'gray_concrete');
 // Landscaped pavement, modest lamps and benches; no ruin or random damage.
 for(const x of [4,28]){box(x,1,37,x+4,1,38,'stone_bricks');box(x,2,37,x+4,2,38,'oak_leaves[persistent=true,distance=1]');}
 for(const x of [2,34]){box(x,1,3,x,4,3,'iron_bars');p(x,5,3,'sea_lantern');}
}
if(stage==='review1'){
 // Visual review: exposed hanging cube lights and monotonous stone soffit.
 box(4,5,19,32,5,34,'smooth_quartz');
 for(const x of [7,17,28])for(const z of [11,22,31]){if(z===11){box(x,5,z,x+1,5,z,'air');lamp(x,6,z);}else lamp(x,5,z);}
 for(const y of floors){
  box(11,y+4,19,25,y+4,30,'smooth_quartz');
  lamp(16,y+4,23);lamp(23,y+4,26);lamp(12,y+4,20);
  // No room layouts or stair voids touched by these soffits.
  p(13,y+1,21,'birch_door[facing=south,half=lower,hinge=left,open=false,powered=false]');
  p(13,y+2,21,'birch_door[facing=south,half=upper,hinge=left,open=false,powered=false]');
 }
}
async function run(){
 const s=await c.call('minecraft_status');if(s.world_session!==epoch)throw Error('WORLD_CHANGED');
 const a=await c.call('authoring_info');if(a.id!==id||JSON.stringify(a.min)!==JSON.stringify(origin))throw Error('PLOT_CHANGED');
 if(!ops.length&&!['inspect'].includes(stage))throw Error('UNKNOWN_STAGE');
 await mkdir(reportDir,{recursive:true});
 // Persistent per-stage journal: never reapply a successful/uncertain batch blindly.
 const journalPath=path.join(reportDir,stage+'.json');
 try{
  const prior=JSON.parse(await readFile(journalPath,'utf8'));
  // Explicitly retry only preflight rejections: no mutation was ever attempted.
  if(process.argv[3]!=='--retry-preflight'||prior.status!=='PREFLIGHT'||prior.batches.length||prior.pending!==undefined)throw Error('STAGE_ALREADY_STARTED: inspect journal/world before resuming');
 }catch(e){if(e.code!=='ENOENT')throw e;}
 const journal={stage,epoch,origin,status:'PREFLIGHT',batches:[],operations:ops.length};
 await writeFile(journalPath,JSON.stringify(journal,null,2));
 const batches=[];let batch=[],volume=0;
 for(const op of ops){let v=op.max.reduce((n,k,i)=>n*(k-op.min[i]+1),1);if(batch.length>=110||volume+v>180000){batches.push(batch);batch=[];volume=0;}batch.push(op);volume+=v;}if(batch.length)batches.push(batch);
 // Check all batches before sending any write in this stage.
 for(const operations of batches)await c.call('we_batch_set',{operations,dry_run:true});
 for(let i=0;i<batches.length;i++){
  journal.status='WRITING';journal.pending=i;await writeFile(journalPath,JSON.stringify(journal,null,2));
  const result=await c.call('we_batch_set',{operations:batches[i]});journal.batches.push(result);delete journal.pending;await writeFile(journalPath,JSON.stringify(journal,null,2));console.log('APPLIED',stage,i,batches[i].length);
 }
 journal.world=await c.call('inspect_selection',{target:'AUTHORING_SESSION'});
 journal.slice=await c.call('get_horizontal_slice',{target:'AUTHORING_SESSION',coordinate:origin[1]+(stage==='massing'?6:8),encoding:'palette'});
 journal.status='APPLIED';await writeFile(journalPath,JSON.stringify(journal,null,2));
 console.log(JSON.stringify({stage,non_air:journal.world.non_air,entities:journal.world.entities,height:journal.world.occupied_height,batches:batches.length}));
 console.log(JSON.stringify(await c.call('capture_current_view')));
}
run().catch(e=>{console.error(e.message);process.exitCode=1;});
