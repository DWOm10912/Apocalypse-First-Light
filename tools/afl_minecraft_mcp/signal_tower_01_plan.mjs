// Live authoring plan only: no automatic connection, placement, export or worldgen registration.
export const origin = [384, -33, -448];
export const size = [9, 23, 9];
const afl = name => `apocalypse_firstlight:${name}`;
const steel = afl('steel_block');
const plate = afl('steel_plate');
const grate = `${afl('steel_grate')}[waterlogged=false]`;
const slab = `${afl('steel_block_slab')}[type=top,waterlogged=false]`;
const copper = 'minecraft:waxed_cut_copper';
const stairs = facing => `${copper}_stairs[facing=${facing},half=bottom,shape=straight,waterlogged=false]`;
const ops = [];
function box(a,b,block) {ops.push({min:a.map((v,i)=>v+origin[i]),max:b.map((v,i)=>v+origin[i]),block});}
function cell(x,y,z,block) {box([x,y,z],[x,y,z],block);}
function ring(y,lo,hi,block) {
  box([lo,y,lo],[hi,y,lo],block); box([lo,y,hi],[hi,y,hi],block);
  box([lo,y,lo+1],[lo,y,hi-1],block); box([hi,y,lo+1],[hi,y,hi-1],block);
}
// Four independent concrete footings and a walk-through base.
for(const x of [1,7]) for(const z of [1,7]) {
  box([x===1?0:6,0,z===1?0:6],[x===1?2:8,0,z===1?2:8],afl('reinforced_concrete'));
  cell(x,1,z,'minecraft:polished_andesite');
  box([x,2,z],[x,19,z],steel);
}
for(const y of [2,6,15]) ring(y,1,7,slab);
export const foundation = ops.splice(0);
// Four open truss bays on every face. Waxing keeps the warm brace colour stable.
for(const [lo,hi] of [[2,6],[6,10],[10,15],[15,19]]) {
  for(const side of [1,7]) for(const axis of ['x','z']) {
    for(let t=1;t<=5;t++) {
      const y1=Math.round(lo+(hi-lo)*t/6);
      const y2=Math.round(hi-(hi-lo)*t/6);
      const put=(y,block)=>axis==='x'?cell(1+t,y,side,block):cell(side,y,1+t,block);
      put(y1,stairs(axis==='x'?'east':'south'));
      put(y2,stairs(axis==='x'?'west':'north'));
      if(y1===y2) put(y1,copper);
    }
  }
}
// Steel joint collars sit on the columns, never close the open bays.
for(const y of [2,6,10,15,19]) for(const x of [1,7]) for(const z of [1,7]) cell(x,y,z,plate);
export const bracing = ops.splice(0);
// Full walkable grating decks, with the continuous ladder shaft left open.
for(const y of [10,19]) {
  for(let z=0;z<=8;z++) for(let x=0;x<=8;x++) {
    if(x===2 && z===2) continue;
    cell(x,y,z,(x===0||x===8||z===0||z===8)?steel:grate);
  }
  for(let z=0;z<=8;z++) for(let x=0;x<=8;x++) {
    if(x!==0&&x!==8&&z!==0&&z!==8) continue;
    const n=z>0&&(x===0||x===8),s=z<8&&(x===0||x===8);
    const w=x>0&&(z===0||z===8),e=x<8&&(z===0||z===8);
    cell(x,y+1,z,`minecraft:iron_bars[north=${n},south=${s},west=${w},east=${e},waterlogged=false]`);
  }
}
export const decks = ops.splice(0);
// Supported internal ladder: narrow metal spine on the north face, opening on both decks.
box([2,1,1],[2,20,1],steel);
box([2,0,2],[2,20,2],'minecraft:ladder[facing=south,waterlogged=false]');
// The first rung has its own bearing at ground level.
cell(2,0,1,afl('reinforced_concrete'));
// Simple central aerial with four exposed fixing points.
cell(4,20,4,plate);
cell(4,21,4,'minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]');
cell(4,22,4,'minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]');
export const access = ops.splice(0);
export const phases = {foundation,bracing,decks,access};
export const operations = Object.values(phases).flat();
