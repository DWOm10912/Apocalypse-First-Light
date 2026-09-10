import {BridgeClient} from './bridge_client.mjs';
import {mkdir,writeFile} from 'node:fs/promises';
const c=new BridgeClient('./run'),label=process.argv[2]??'audit';
async function run(){
 const status=await c.call('minecraft_status'),info=await c.call('authoring_info');
 if(status.world_session!=='38bbdeb6-b033-455b-ac5b-b5feff4cc937'||info.id!=='modern_glass_tower_02')throw Error('WORLD_OR_PLOT_CHANGED');
 const slices=[];const blocks=new Map();
 // Read the actual stairwell plus lobby aisle as five vertical exact-state slices.
 for(let x=20;x<=24;x++){
  const r=await c.call('get_vertical_slice',{target:'AUTHORING_SESSION',axis:'X',coordinate:16+x,min:[36,-33,-150],max:[40,51,-140],encoding:'palette'});
  slices.push(r);const pal=Object.fromEntries(Object.entries(r.palette).map(([k,v])=>[v,k]));
  for(let row=0;row<r.rows.length;row++)for(let col=0;col<r.rows[row].length;col++)blocks.set([x,84-row,10+col].join(','),pal[r.rows[row][col]]);
 }
 const state=(x,y,z)=>blocks.get([x,y,z].join(','))??'UNKNOWN';
 const air=(x,y,z)=>state(x,y,z)==='Block{minecraft:air}';
 // Conservative full-cube support and two-air-block headroom approximation.
 // Stairs treated as solid-top steps; results do not substitute for a player walk test.
 const stand=(x,y,z)=>y>=1&&y<=83&&x>=20&&x<=24&&z>=10&&z<=20&&!air(x,y-1,z)&&state(x,y-1,z)!=='UNKNOWN'&&air(x,y,z)&&air(x,y+1,z);
 const start=[20,1,18],seen=new Set([start.join(',')]),q=[start];
 for(let i=0;i<q.length;i++){let [x,y,z]=q[i];for(const [dx,dz] of [[1,0],[-1,0],[0,1],[0,-1]])for(const dy of [0,1,-1]){let t=[x+dx,y+dy,z+dz],k=t.join(',');if(!seen.has(k)&&stand(...t)){seen.add(k);q.push(t);}}}
 const levels=[0,...Array.from({length:15},(_,i)=>6+i*5),81].map(y=>({floor:y,reachable:seen.has([24,y+1,17].join(',')),entry:state(20,y+1,17),head:state(20,y+2,17)}));
 const ground=await c.call('get_horizontal_slice',{target:'AUTHORING_SESSION',coordinate:-32,encoding:'palette'});
 const typical=await c.call('get_horizontal_slice',{target:'AUTHORING_SESSION',coordinate:-25,encoding:'palette'});
 const roof=await c.call('get_horizontal_slice',{target:'AUTHORING_SESSION',coordinate:49,encoding:'palette'});
 const counts=await c.call('inspect_selection',{target:'AUTHORING_SESSION'});
 const result={label,levels,stair_reachable_all:levels.every(x=>x.reachable),method:'Conservative block-grid 2-air-headroom and <=1-step adjacency; not in-game player physics test',slices,ground,typical,roof,counts};
 await mkdir('build/authoring_checks/modern_glass_tower_02',{recursive:true});await writeFile(`build/authoring_checks/modern_glass_tower_02/${label}.json`,JSON.stringify(result,null,2));
 console.log(JSON.stringify({levels,non_air:counts.non_air,entities:counts.entities}));console.log(await c.call('capture_current_view'));
}
run().catch(e=>{console.error(e.message);process.exitCode=1;});
