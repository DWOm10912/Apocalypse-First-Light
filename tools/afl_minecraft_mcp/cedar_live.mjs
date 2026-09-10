import {BridgeClient} from './bridge_client.mjs';
const c=new BridgeClient('./run');const stage=process.argv[2];
const s=await c.call('minecraft_status');if(s.world_session!=='9ab75b9a-b4cc-4f50-9e87-7e9b51cc6c65')throw Error('World changed');
const info=await c.call('authoring_info');if(info.id!=='office_cedar_06'||JSON.stringify(info.min)!=='[48,-33,96]')throw Error('Plot changed');
let n=0;async function box(x,y,z,X,Y,Z,block){const a={min:[48+x,-33+y,96+z],max:[48+X,-33+Y,96+Z],block:'minecraft:'+block};await c.call('we_set',{...a,dry_run:true});await c.call('we_set',a);n++;}
const floors=[0,6,11,16,21,26,31];
if(stage==='shell'){
 await box(0,0,0,36,0,30,'smooth_stone');
 for(const y of floors){await box(3,y,3,33,y,25,'smooth_stone');}
 for(let f=0;f<6;f++){let y=floors[f],top=floors[f+1]-1;
  await box(3,y+1,3,33,top,3,'bricks');await box(3,y+1,25,33,top,25,'light_gray_terracotta');
  await box(3,y+1,4,3,top,24,'bricks');await box(33,y+1,4,33,top,24,'bricks');
  for(const x of [5,12,19,26]){await box(x,y+2,25,x+4,top-1,25,'gray_stained_glass');await box(x,y+2,3,x+4,top-1,3,'gray_stained_glass');}
  for(const z of [6,13,20])for(const x of [3,33])await box(x,y+2,z,x,top-1,z+2,'gray_stained_glass');
 }
 await box(3,32,3,33,32,3,'stone_bricks');await box(3,32,25,33,32,25,'stone_bricks');await box(3,32,4,3,32,24,'stone_bricks');await box(33,32,4,33,32,24,'stone_bricks');
 await box(10,1,25,14,4,25,'air');await box(9,5,25,15,5,28,'polished_andesite_slab[type=top]');
 await box(10,0,26,14,0,30,'polished_andesite');
}
if(stage==='interior'){
 for(let f=0;f<6;f++){const y=floors[f],h=floors[f+1]-y;
  // Rear-west straight stair in its own open well, five/six risers and a landing.
  await box(5,y+1,5,7,y+h,11,'air');
  for(let k=0;k<h;k++)await box(5,y+k+1,11-k,7,y+k+1,11-k,'stone_brick_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]');
  await box(5,y+h,4,7,y+h,4,'smooth_stone');
  await box(8,y+1,4,8,y+h-1,12,'light_gray_terracotta');
  await box(8,y+1,11,8,y+3,12,'air');
  // Cross corridor and south-facing rooms, different from reference's central core.
  await box(10,y+1,16,31,y+3,16,'white_stained_glass');
  for(const x of [12,22,29])await box(x,y+1,16,x+1,y+3,16,'air');
  for(const x of [18,26])await box(x,y+1,17,x,y+3,24,'light_gray_terracotta');
  for(const [x,z] of [[12,21],[22,21],[29,21],[14,8],[23,8],[29,8]]){
   await box(x,y+1,z,x+2,y+1,z,'oak_slab[type=top]');
   await box(x+1,y+1,z+1,x+1,y+1,z+1,'dark_oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]');
   await box(x+1,y+2,z,x+1,y+2,z,'black_concrete');
  }
  for(const x of [12,23,29])await box(x,y+h-1,14,x+1,y+h-1,14,'sea_lantern');
 }
 // Lobby replaces selected desks with reception and lounge.
 await box(10,1,19,16,2,22,'air');await box(16,1,20,16,2,23,'polished_andesite');
 await box(20,1,7,25,1,8,'dark_oak_slab[type=top]');
 // Roof plant: two offset AHUs with ducts, no extra occupied floor.
 for(const [x,z] of [[15,7],[25,17]]){await box(x,32,z,x+4,33,z+3,'polished_andesite');await box(x+1,34,z+1,x+3,34,z+2,'iron_bars');}
 await box(19,32,9,25,32,9,'gray_concrete');await box(25,32,9,25,32,16,'gray_concrete');
}
if(stage==='round1'){
 // Provide return circulation to each stair foot; clear tight landing headroom.
 for(let f=0;f<6;f++){let y=floors[f];await box(4,y+1,4,4,y+3,12,'air');await box(5,y+1,12,8,y+3,13,'air');}
 // Exterior window reveals and narrower vertical mullions.
 for(let f=0;f<6;f++){let y=floors[f];for(const x of [5,12,19,26]){
 await box(x,y+1,26,x+4,y+1,26,'stone_slab[type=top]');
 await box(x+2,y+2,25,x+2,floors[f+1]-2,25,'gray_concrete');}}
 // Restore unobstructed entry after sill detailing.
 await box(10,1,25,14,4,26,'air');
}
if(stage==='round2'){
 // Roof safety: cap the open stair well with a compact access headhouse.
 await box(4,32,4,8,35,4,'stone_bricks');await box(4,32,5,4,35,12,'stone_bricks');await box(8,32,5,8,35,12,'stone_bricks');await box(4,35,4,8,35,12,'smooth_stone');await box(4,32,12,8,34,12,'stone_bricks');await box(5,32,12,7,34,12,'air');
 // Lobby identity and front plaza seating, without imported signs/text.
 await box(18,1,28,23,1,28,'dark_oak_slab[type=bottom]');
 await box(28,1,28,31,1,29,'stone_bricks');await box(28,2,28,31,2,29,'oak_leaves[persistent=true,distance=1]');
 await box(6,1,28,8,1,29,'stone_bricks');await box(6,2,28,8,2,29,'oak_leaves[persistent=true,distance=1]');
}
const r=await c.call('inspect_selection',{target:'AUTHORING_SESSION'});console.log(JSON.stringify({stage,operations:n,non_air:r.non_air,bounds:r.bounds}));console.log(JSON.stringify(await c.call('capture_current_view')));
