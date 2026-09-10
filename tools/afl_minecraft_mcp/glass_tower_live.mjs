import {BridgeClient} from './bridge_client.mjs';
const c=new BridgeClient('./run'),stage=process.argv[2],ops=[];
const status=await c.call('minecraft_status');if(status.world_session!=='38bbdeb6-b033-455b-ac5b-b5feff4cc937')throw Error('WORLD_CHANGED');
const info=await c.call('authoring_info');if(info.id!=='glass_braced_tower_01'||JSON.stringify(info.min)!=='[-128,-33,80]')throw Error('PLOT_CHANGED');
function box(x,y,z,X,Y,Z,b){ops.push({min:[x-128,y-33,z+80],max:[X-128,Y-33,Z+80],block:'minecraft:'+b});}
function shell(x,z,X,Z,lo,hi){box(x,lo,z,X,hi,z,'blue_stained_glass');box(x,lo,Z,X,hi,Z,'blue_stained_glass');box(x,lo,z+1,x,hi,Z-1,'blue_stained_glass');box(X,lo,z+1,X,hi,Z-1,'blue_stained_glass');}
if(stage==='massing'){
 box(1,0,1,35,0,37,'smooth_stone');shell(3,5,33,33,1,15);box(3,15,5,33,15,33,'smooth_stone');
 shell(11,9,25,29,16,126);shell(5,12,10,31,16,105);shell(26,13,31,31,16,81);
 for(let y=5;y<=125;y+=5)box(12,y,10,24,y,28,'smooth_stone');
 for(const [x,X,h] of [[5,10,105],[26,31,81]])for(let y=20;y<=h;y+=5)box(x,y,14,X,y,30,'smooth_stone');
 box(11,126,9,25,126,29,'gray_concrete');box(5,106,12,10,106,31,'gray_concrete');box(26,82,13,31,82,31,'gray_concrete');
}
if(stage==='exterior'){
 // Dark spandrels contrast with blue glazing; finer light vertical frame.
 for(let y=20;y<=125;y+=5){box(11,y,8,25,y,8,'gray_concrete');box(11,y,30,25,y,30,'gray_concrete');}
 for(const x of [11,25]){box(x,16,8,x,126,8,'smooth_quartz');box(x,16,30,x,126,30,'smooth_quartz');}
 for(const [x,X,z,h] of [[5,10,11,105],[26,31,12,81]]){
  for(const u of [x,X])box(u,16,z,u,h,z,'smooth_quartz');
  for(let y=16;y<h;y+=12){box(x,y,z,X,y,z,'smooth_quartz');for(let k=0;k<12&&y+k<=h;k++){let u=x+Math.round((X-x)*(Math.floor((y-16)/12)%2?1-k/11:k/11));box(u,y+k,z-1,u,y+k,z-1,'smooth_quartz');}}
 }
 for(let y=4;y<=14;y+=5){box(3,y,4,33,y,4,'light_gray_concrete');}
 for(const x of [3,9,16,23,33])box(x,1,4,x,14,4,'smooth_quartz');
 box(15,1,5,21,4,5,'air');box(14,5,2,22,5,5,'polished_andesite_slab[type=top]');box(15,0,1,21,0,5,'polished_andesite');
 // Crown louvers and two narrow mast details.
 for(let y=117;y<=125;y+=2)box(12,y,7,24,y,7,'polished_andesite_slab[type=top]');
 box(11,127,9,25,128,9,'light_blue_stained_glass');box(11,127,29,25,128,29,'light_blue_stained_glass');
 for(const x of [10,26])box(x,123,15,x,130,15,'iron_bars');
}
if(stage==='interior'){
 box(7,1,20,12,1,22,'polished_andesite');box(8,2,22,12,2,22,'oak_slab[type=top]');
 // Compact rear stair well; every floor has a walkable return aisle.
 for(let y=5;y<=120;y+=5){
  box(21,y,21,23,y+4,27,'air');
  for(let k=0;k<5;k++)box(21,y+k,26-k,23,y+k,26-k,'stone_brick_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]');
  box(21,y+4,20,23,y+4,21,'smooth_stone');
  box(14,y+1,12,17,y+1,12,'oak_slab[type=top]');box(15,y+1,14,15,y+1,14,'dark_oak_stairs[facing=north]');
  box(19,y+1,16,19,y+3,20,'white_stained_glass');box(14,y+4,17,15,y+4,17,'sea_lantern');
 }
}
if(stage==='roof'){
 for(const [x,z,y] of [[14,17,127],[6,22,107],[27,22,83]]){box(x,y,z,x+2,y+1,z+3,'polished_andesite');box(x,y+2,z+1,x+2,y+2,z+2,'iron_bars');}
 box(18,127,18,22,127,18,'gray_concrete');box(22,127,18,22,127,24,'gray_concrete');
}
if(stage==='review1'){
 for(const y of [5,10])box(4,y,6,32,y,32,'smooth_stone');
 for(let k=0;k<5;k++)box(21,k,26-k,23,k,26-k,'stone_brick_stairs[facing=north]');
 // Ground floor circulation and brighter lobby ceiling, front clear of desks.
 box(12,1,6,24,4,19,'air');box(12,5,6,24,5,19,'smooth_stone');
 for(const x of [8,18,28])box(x,4,10,x+1,4,11,'sea_lantern');
 // Stair top openings/landings aligned with each 5-block floor.
 for(let y=10;y<=125;y+=5){box(21,y,21,23,y+3,26,'air');for(let k=0;k<5&&y<=120;k++)box(21,y+k,26-k,23,y+k,26-k,'stone_brick_stairs[facing=north]');box(21,y,20,23,y,20,'smooth_stone');}
}
if(stage==='review2'){
 // More neutral recessed spandrels soften the overly dark horizontal bands.
 for(let y=20;y<=115;y+=5){box(12,y,8,24,y,8,'light_blue_stained_glass');box(12,y,30,24,y,30,'light_blue_stained_glass');}
 // Reinforce the receding side wings and finer rear rhythm.
 for(let y=20;y<=105;y+=5)box(5,y,32,10,y,32,'gray_concrete');
 for(let y=20;y<=80;y+=5)box(26,y,32,31,y,32,'gray_concrete');
 for(const x of [5,10,26,31])box(x,16,32,x,x<20?105:81,32,'smooth_quartz');
 for(const x of [4,29]){box(x,1,1,x+3,1,2,'stone_bricks');box(x,2,1,x+3,2,2,'oak_leaves[persistent=true,distance=1]');}
}
for(let i=0;i<ops.length;i+=100){let a={operations:ops.slice(i,i+100)};await c.call('we_batch_set',{...a,dry_run:true});await c.call('we_batch_set',a);console.log('applied',stage,i,a.operations.length);}
const r=await c.call('inspect_selection',{target:'AUTHORING_SESSION'});console.log(JSON.stringify({stage,non_air:r.non_air,bounds:r.bounds}));console.log(JSON.stringify(await c.call('capture_current_view')));
