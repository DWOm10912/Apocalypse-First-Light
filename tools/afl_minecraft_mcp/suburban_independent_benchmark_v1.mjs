// Original concept-only benchmark. Does not read/import any building assets.
import {BridgeClient} from './bridge_client.mjs';
const ORIGIN=[688,-33,-496], ID='suburban_independent_benchmark_v1';
const vox=new Map();
const key=(x,y,z)=>`${x},${y},${z}`;
function box(x,y,z,X,Y,Z,state){
  if(!state.startsWith('minecraft:'))state='minecraft:'+state;
  for(let a=x;a<=X;a++)for(let b=y;b<=Y;b++)for(let c=z;c<=Z;c++)vox.set(key(a,b,c),state);
}
const put=(x,y,z,s)=>box(x,y,z,x,y,z,s);
const air=(x,y,z,X,Y,Z)=>box(x,y,z,X,Y,Z,'air');
const stair=(m,f)=>`${m}_stairs[facing=${f},half=bottom,shape=straight,waterlogged=false]`;
function walls(x,z,X,Z,lo,hi,s){box(x,lo,z,X,hi,z,s);box(x,lo,Z,X,hi,Z,s);box(x,lo,z,x,hi,Z,s);box(X,lo,z,X,hi,Z,s);}
function door(x,y,z,f='south',hinge='left'){
  for(const half of ['lower','upper'])put(x,y+(half==='upper'?1:0),z,`dark_oak_door[facing=${f},half=${half},hinge=${hinge},open=false,powered=false]`);
}
function frontWindow(x,X,z,y,Y,shutters=true){
  box(x-1,y-1,z,X+1,y-1,z,'smooth_quartz');box(x-1,Y+1,z,X+1,Y+1,z,'smooth_quartz');
  box(x-1,y,z,x-1,Y,z,'smooth_quartz');box(X+1,y,z,X+1,Y,z,'smooth_quartz');
  box(x,y,z,X,Y,z,'glass_pane[east=true,west=true,north=false,south=false,waterlogged=false]');
  if(shutters)for(const a of [x-1,X+1])box(a,y,z+1,a,Y,z+1,'spruce_trapdoor[facing=south,half=bottom,open=true,powered=false,waterlogged=false]');
}
function sideWindow(x,z,Z,y,Y){
  box(x,y-1,z-1,x,y-1,Z+1,'smooth_quartz');box(x,Y+1,z-1,x,Y+1,Z+1,'smooth_quartz');
  box(x,y,z-1,x,Y,z-1,'smooth_quartz');box(x,y,Z+1,x,Y,Z+1,'smooth_quartz');
  box(x,y,z,x,Y,Z,'glass_pane[east=false,west=false,north=true,south=true,waterlogged=false]');
}
// Ground and three distinct structural footprints. Front faces SOUTH (+Z).
box(1,0,1,38,0,38,'grass_block');
box(4,0,7,23,0,25,'stone_bricks');box(4,1,7,23,1,25,'birch_planks');
walls(4,7,23,25,2,10,'birch_planks');
box(4,6,7,23,6,25,'birch_planks');box(4,11,7,23,11,25,'smooth_quartz');
for(const [x,z] of [[4,7],[23,7],[4,25],[23,25]])box(x,1,z,x,11,z,'quartz_pillar[axis=y]');
// Horizontal trim and lightly projecting two-storey bay.
walls(4,7,23,25,6,6,'smooth_quartz');
box(17,0,25,23,1,27,'stone_bricks');box(18,0,28,22,1,28,'stone_bricks');
for(const [a,b] of [[2,5],[7,10]]){
  air(18,a,25,22,b,27);box(17,a,26,17,b,27,'birch_planks');box(23,a,26,23,b,27,'birch_planks');box(18,a,28,22,b,28,'birch_planks');
  for(const x of [18,22])box(x,a,28,x,b,28,'quartz_pillar[axis=y]');
  frontWindow(19,21,28,a+1,b,false);sideWindow(17,26,27,a+1,b);sideWindow(23,26,27,a+1,b);
}
box(17,6,25,23,6,27,'birch_planks');box(18,6,28,22,6,28,'smooth_quartz');
box(17,11,25,23,11,27,'smooth_quartz');box(18,11,28,22,11,28,'smooth_quartz');
box(18,6,29,22,6,29,stair('deepslate_tile','north'));
box(17,6,26,17,6,28,stair('deepslate_tile','east'));box(23,6,26,23,6,28,stair('deepslate_tile','west'));
// Garage: 10x14 clear internal parking space, attached through mudroom.
box(24,0,12,35,1,28,'smooth_stone');walls(24,12,35,28,2,6,'birch_planks');box(24,7,12,35,7,28,'smooth_quartz');
for(const x of [24,35])for(const z of [12,28])box(x,1,z,x,7,z,'quartz_pillar[axis=y]');
// Two modest panelled doors, glazed top row. Deliberately static facade, no redstone.
for(const [x,X] of [[25,28],[30,33]]){
  box(x,2,28,X,5,28,'quartz_block');
  box(x,5,28,X,5,28,'glass_pane[east=true,west=true,north=false,south=false,waterlogged=false]');
  for(const y of [2,3,4])box(x,y,29,X,y,29,'birch_trapdoor[facing=south,half=bottom,open=true,powered=false,waterlogged=false]');
}
box(29,2,28,29,6,28,'smooth_quartz');
sideWindow(35,16,18,3,4);sideWindow(35,23,25,3,4);
// Windows follow rooms, identical sill/head levels on both floors.
for(const y of [3,8]){frontWindow(7,9,25,y,y+2);frontWindow(13,14,25,y,y+2,false);}
// Entrance replaces the lower middle window and opens onto sheltered porch.
box(12,2,25,15,5,25,'smooth_quartz');door(13,2,25);door(14,2,25,'south','right');
box(13,4,25,14,4,25,'glass_pane[east=true,west=true,north=false,south=false,waterlogged=false]');
for(const y of [3,8])for(const [z,Z] of [[10,12],[19,21]])sideWindow(4,z,Z,y,y+2);
for(const [x,X] of [[7,9],[13,15],[19,21]])for(const y of [3,8])frontWindow(x,X,7,y,y+2,false);
sideWindow(23,9,11,8,10);sideWindow(23,19,21,8,10);
// Rear kitchen exit and small deck.
door(16,2,7,'north');box(12,1,3,18,1,6,'spruce_planks');box(14,0,2,16,0,2,stair('stone_brick','south'));
// Covered front porch, two-block-wide circulation through opening in balustrade.
box(3,1,26,16,1,30,'smooth_stone');box(12,0,31,15,0,31,stair('stone_brick','north'));
for(const x of [4,10,16]){put(x,2,30,'stone_bricks');box(x,3,30,x,5,30,'quartz_pillar[axis=y]');}
box(4,5,30,16,5,30,'smooth_quartz');
for(const [x,X] of [[5,9],[11,11]])box(x,2,30,X,2,30,'birch_fence[east=true,west=true,north=false,south=false,waterlogged=false]');
box(4,2,26,4,2,29,'birch_fence[east=false,west=false,north=true,south=true,waterlogged=false]');
for(let z=26;z<=31;z++){const y=6+Math.floor((31-z)/3);box(3,y,z,16,y,z,'deepslate_tile_slab[type=bottom,waterlogged=false]');}
// Roof heightfield union. Cross-gables meet a continuous main east-west ridge.
const roof=new Map();
function surface(x,z,h,kind,facing){const k=`${x},${z}`;if(!roof.has(k)||roof.get(k).h<h)roof.set(k,{h,kind,facing});}
for(let x=3;x<=24;x++)for(let z=6;z<=26;z++)surface(x,z,24+Math.min(z-6,26-z),'half',z<16?'south':'north');
for(let x=5;x<=17;x++)for(let z=16;z<=27;z++)surface(x,z,22+2*(6-Math.abs(x-11)),'stair',x<11?'east':'west');
for(let x=16;x<=24;x++)for(let z=20;z<=29;z++)surface(x,z,24+2*(4-Math.abs(x-20)),'stair',x<20?'east':'west');
for(let x=23;x<=36;x++)for(let z=11;z<=29;z++)surface(x,z,14+2*Math.min(x-23,36-x),'stair',x<30?'east':'west');
// Attic gable infill: no exposed back face beneath roof junctions.
for(let z=7;z<=25;z++)for(const x of [4,23]){const r=roof.get(`${x},${z}`);if(r)box(x,12,z,x,Math.floor(r.h/2)-1,z,'birch_planks');}
for(const [a,b,z,lo] of [[5,17,25,12],[17,23,28,12],[24,35,28,8],[24,35,12,8]])for(let x=a;x<=b;x++){const r=roof.get(`${x},${z}`);if(r&&Math.floor(r.h/2)>lo)box(x,lo,z,x,Math.floor(r.h/2)-1,z,'birch_planks');}
for(const [k,r] of roof){const[x,z]=k.split(',').map(Number),y=Math.floor(r.h/2);put(x,y,z,r.kind==='half'?(r.h%2?'deepslate_tiles':'deepslate_tile_slab[type=bottom,waterlogged=false]'):stair('deepslate_tile',r.facing));}
// White rake edges, dark roof behind; separate scales for three gables.
for(const [a,b,z,base,c] of [[5,17,27,11,11],[16,24,29,12,20],[23,36,29,7,29.5]]){
  for(let x=a;x<=b;x++){const y=base+Math.floor((b-a)/2-Math.abs(x-c));put(x,y,z,stair('smooth_quartz',x<c?'east':'west'));}
}
// Continuous eave fascia and ridge caps.
box(3,11,6,24,11,6,'smooth_quartz');box(3,11,26,4,11,26,'smooth_quartz');
box(3,11,6,3,11,26,'smooth_quartz');box(24,7,11,36,7,11,'smooth_quartz');
box(36,7,11,36,7,29,'smooth_quartz');
box(9,17,16,13,17,16,'deepslate_tile_slab[type=bottom,waterlogged=false]');
// Small conventional masonry chimney, kept away from valleys.
box(5,12,10,6,18,11,'bricks');box(5,19,10,6,19,11,'stone_brick_slab[type=bottom,waterlogged=false]');
// Ground-floor service rooms, stair and open living/dining/kitchen circulation.
box(18,2,14,18,5,24,'white_terracotta');box(18,2,14,22,5,14,'white_terracotta');box(18,2,19,22,5,19,'white_terracotta');
door(18,2,16,'west');door(20,2,19,'south');
// Attached garage doorway removes both adjoining one-block exterior walls.
air(23,2,16,24,3,17);door(24,2,16,'east');door(24,2,17,'east','right');
// Powder room is a small separate rear portion of the entry-side service strip.
box(20,2,22,22,5,22,'white_terracotta');door(20,2,22,'north');
// Stair rises NORTH, floor-to-floor five blocks; two-wide flight and a broad side hall.
air(12,6,17,13,6,22);
for(let i=0;i<5;i++){const z=22-i,y=2+i;box(12,2,z,13,y-1,z,'birch_planks');box(12,y,z,13,y,z,stair('birch','north'));air(12,y+1,z,13,y+3,z);}
box(12,6,16,13,6,17,'birch_planks');
// Upper partitions: west primary suite + dressing + ensuite, two east bedrooms.
box(11,7,8,11,10,24,'white_terracotta');box(17,7,8,17,10,27,'white_terracotta');
box(5,7,13,10,10,13,'white_terracotta');box(5,7,17,10,10,17,'white_terracotta');
door(8,7,13,'south');door(8,7,17,'south');door(11,7,23,'east');
box(18,7,17,22,10,17,'white_terracotta');door(17,7,14,'west');door(17,7,23,'west');
// Shared bathroom at rear of central hall.
box(12,7,12,16,10,12,'white_terracotta');door(14,7,12,'south');
for(let z=18;z<=22;z++)put(14,7,z,'birch_fence[east=false,west=false,north=true,south=true,waterlogged=false]');
// Light furniture only: upholstered beds represented by wool, no BE/custom assets.
function bed(x,y,z,color){box(x,y,z,x+2,y,z+3,`${color}_wool`);box(x,y,z,x+2,y,z,'white_wool');box(x,y,z-1,x+2,y+1,z-1,'spruce_planks');}
bed(6,7,20,'light_gray');bed(19,7,10,'light_blue');bed(19,7,22,'green');
// Living-room couch and coffee table; dining table with four chairs.
box(6,2,19,6,2,23,stair('quartz','east'));box(8,2,20,9,2,22,'spruce_slab[type=bottom,waterlogged=false]');box(5,2,17,10,2,17,'light_gray_carpet');
box(7,2,10,9,2,12,'oak_slab[type=top,waterlogged=false]');
for(const z of [10,12]){put(6,2,z,stair('birch','east'));put(10,2,z,stair('birch','west'));}
// Kitchen cabinets, island, dry sink and refrigerator-shaped cabinetry.
box(12,2,8,21,2,8,'smooth_quartz');box(22,2,8,22,2,12,'smooth_quartz');
box(12,4,8,15,4,8,'birch_planks');box(19,4,8,22,4,8,'birch_planks');
put(18,2,8,stair('quartz','north'));put(18,3,8,'stone_button[face=wall,facing=south,powered=false]');
box(21,2,12,22,4,12,'iron_block');box(14,2,11,16,2,12,'smooth_quartz');
put(21,3,8,'heavy_weighted_pressure_plate[power=0]');
box(19,2,15,20,2,15,'quartz_block');box(19,3,15,20,3,15,'stone_pressure_plate[powered=false]');
box(21,2,18,22,2,18,'spruce_slab[type=bottom,waterlogged=false]');
// Dry bathroom fittings, no liquids or complex machinery.
for(const [x,y,z] of [[21,2,20],[6,7,9],[13,7,9]]){
  put(x,y,z,stair('quartz','north'));put(x,y+1,z-1,'smooth_quartz');put(x+1,y,z,'smooth_quartz');
}
box(5,7,10,5,7,12,'smooth_quartz');box(6,7,12,9,7,12,'smooth_quartz');
box(6,8,12,9,9,12,'glass_pane[east=true,west=true,north=false,south=false,waterlogged=false]');
box(5,7,14,5,9,16,'spruce_planks');
// Lanterns under ceilings and porch beams; no invisible light blocks.
for(const [x,y,z] of [[8,5,21],[8,5,11],[15,5,10],[16,5,23],[21,5,16],[21,5,21],[28,6,16],[32,6,24],[8,10,22],[8,10,10],[15,10,15],[15,10,24],[20,10,11],[20,10,23],[14,10,10],[7,5,29],[15,5,29]])put(x,y,z,'lantern[hanging=true,waterlogged=false]');
// Minimal apron, front path, rear access and foundation planting.
box(24,0,29,35,0,38,'smooth_stone');box(12,0,32,15,0,38,'stone_bricks');
box(12,0,37,35,0,38,'stone_bricks');
for(const [x,X,z,Z] of [[5,9,32,33],[18,22,31,32],[2,2,9,23],[36,37,13,15]])box(x,1,z,X,1,Z,'oak_leaves[persistent=true,distance=7,waterlogged=false]');
for(const x of [11,16]){put(x,1,34,'stone_brick_wall[up=true,east=none,west=none,north=none,south=none,waterlogged=false]');put(x,2,34,'lantern[hanging=false,waterlogged=false]');}
// Greedy exact-state cuboids: final geometry, not repeated broad clear operations.
function compress(){
  const remaining=new Map(vox), out=[];
  for(const [k,state] of vox){if(!remaining.has(k))continue;const[x,y,z]=k.split(',').map(Number);let X=x,Z=z,Y=y;
    while(remaining.get(key(X+1,y,z))===state)X++;
    outer:while(true){for(let a=x;a<=X;a++)if(remaining.get(key(a,y,Z+1))!==state)break outer;Z++;}
    upper:while(true){for(let a=x;a<=X;a++)for(let c=z;c<=Z;c++)if(remaining.get(key(a,Y+1,c))!==state)break upper;Y++;}
    for(let a=x;a<=X;a++)for(let b=y;b<=Y;b++)for(let c=z;c<=Z;c++)remaining.delete(key(a,b,c));
    out.push({min:[x,y,z].map((v,i)=>v+ORIGIN[i]),max:[X,Y,Z].map((v,i)=>v+ORIGIN[i]),block:state});
  }return out;
}
const ops=compress();
const palette=[...new Set(vox.values())];
if(palette.some(x=>!x.startsWith('minecraft:')))throw Error('NON_VANILLA');
for(const o of ops)for(let i=0;i<3;i++)if(o.min[i]<ORIGIN[i]||o.max[i]>ORIGIN[i]+[39,25,39][i])throw Error('OUTSIDE_PLOT');
const mode=process.argv[2]??'plan';
console.log(JSON.stringify({mode,blocks:vox.size,operations:ops.length,batches:Math.ceil(ops.length/128),palette}));
if(mode==='plan')process.exit(0);
const b=new BridgeClient('run');const status=await b.call('minecraft_status');
if(status.world_session!=='623d05c6-a348-4f1d-bc49-8617ecb9d706')throw Error('WORLD_CHANGED');
const info=await b.call('authoring_info');if(info.id!==ID||JSON.stringify(info.min)!==JSON.stringify(ORIGIN))throw Error('PLOT_CHANGED');
if(mode==='preview'){for(let i=0;i<ops.length;i+=128)console.log(JSON.stringify({batch:i/128,result:await b.call('we_batch_set',{operations:ops.slice(i,i+128),dry_run:true})}));}
else if(mode==='build'){for(let i=0;i<ops.length;i+=128)console.log(JSON.stringify({batch:i/128,result:await b.call('we_batch_set',{operations:ops.slice(i,i+128)})}));}
else throw Error('UNKNOWN_MODE');
