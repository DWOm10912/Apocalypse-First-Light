// One explicitly reserved author-world site. No worldgen registration or automatic export.
import {readFile,writeFile,mkdir} from 'node:fs/promises';
import {gzipSync} from 'node:zlib';
import {BridgeClient} from './bridge_client.mjs';
export const origin=[368,-33,-240],size=[38,12,35],id='convenience_store_v2_candidate';
const epoch='b57a64dd-a140-4428-a35b-35c6391edec9';
const A='apocalypse_firstlight:', M='minecraft:';
const registry=JSON.parse(await readFile('run/afl_authoring_bridge/target_registry_1_20_1.json','utf8')).blocks;
const full=b=>b.includes(':')?b:A+b;
function check(b){let [name,props]=b.replace(']','').split('[');if(!registry[name])throw Error('Unknown block '+name);if(props)for(const pair of props.split(',')){let [k,v]=pair.split('=');if(!registry[name].properties[k]?.includes(v))throw Error('Invalid state '+b);}}
const abs=p=>p.map((n,i)=>n+origin[i]);
const phases={lot:[],shell:[],zones:[],ceiling:[],exterior:[],cleanup:[]};
function box(phase,a,b,block){block=full(block);check(block);for(let i=0;i<3;i++)if(a[i]<0||b[i]>=size[i]||a[i]>b[i])throw Error('Out of bounds');phases[phase].push({min:abs(a),max:abs(b),block});}
const one=(phase,x,y,z,b)=>box(phase,[x,y,z],[x,y,z],b);
box('lot',[0,0,0],[37,0,34],'asphalt');
box('lot',[4,0,2],[32,0,24],'reinforced_concrete');
box('lot',[7,0,6],[29,0,20],M+'smooth_quartz');
// Sidewalk curb and step access; surface is local Y=1.
for(const x of [3,33])box('lot',[x,0,2],[x,0,24],'reinforced_concrete_slab[type=bottom]');
box('lot',[4,0,25],[32,0,25],'reinforced_concrete_slab[type=bottom]');
// Floor perimeter 25 x 17; five clear full cells, roof at local Y=6.
box('shell',[6,1,5],[30,5,5],'reinforced_concrete');
for(const x of [6,30])box('shell',[x,1,6],[x,5,21],'reinforced_concrete');
box('shell',[6,1,21],[30,4,21],M+'glass');
for(const x of [6,10,14,17,20,24,27,30])box('shell',[x,1,21],[x,4,21],'steel_plate');
box('shell',[6,4,21],[30,4,21],'steel_plate');
box('shell',[6,5,21],[30,5,21],'reinforced_concrete');
// Door is two cells wide at x18..19; no fake glass in the opening.
box('shell',[18,1,21],[19,2,21],M+'air');
// Rear rooms: storage/office 15x5, restroom 7x5; common sales-side entrances.
box('shell',[7,1,11],[29,5,11],M+'light_gray_concrete');
box('shell',[22,1,6],[22,5,10],M+'light_gray_concrete');
for(const x of [8,23])box('shell',[x,1,11],[x,2,11],M+'air');
box('shell',[9,1,5],[9,2,5],M+'air');
// Subtle side clerestory strips and structural jambs.
for(const x of [6,30])for(const z of [14,18])box('shell',[x,3,z],[x,4,z+1],M+'light_gray_stained_glass');
// Modest cash counters; full block top supports actual registers.
box('shell',[8,1,18],[8,1,20],M+'smooth_quartz');
box('shell',[7,1,18],[7,1,18],M+'light_gray_concrete');
// Temporary zoning cues, removed before fixtures.
for(const [a,b,c] of [[[10,0,14],[11,0,18],'yellow_concrete'],[[14,0,14],[15,0,18],'orange_concrete'],[[18,0,14],[19,0,18],'yellow_concrete'],[[23,0,6],[29,0,10],'cyan_concrete']])box('zones',a,b,M+c);
box('cleanup',[7,0,6],[29,0,20],M+'smooth_quartz');
box('ceiling',[6,6,5],[30,6,21],M+'smooth_quartz');
// Muted terracotta accent, not a real-world multi-colour stripe system.
box('ceiling',[5,6,22],[31,6,22],M+'orange_terracotta');
for(const x of [5,31])box('ceiling',[x,6,4],[x,6,21],M+'orange_terracotta');
box('ceiling',[5,6,4],[31,6,4],M+'orange_terracotta');
for(const z of [4,22])box('ceiling',[5,7,z],[31,7,z],'reinforced_concrete_slab[type=bottom]');
for(const x of [5,31])box('ceiling',[x,7,5],[x,7,21],'reinforced_concrete_slab[type=bottom]');
box('ceiling',[16,6,23],[21,7,23],M+'white_concrete');
// Dark flat roof tray, two restrained HVAC placeholders.
box('ceiling',[7,7,6],[29,7,20],'steel_plate_slab[type=bottom]');
for(const x of [11,24]){box('ceiling',[x,7,8],[x+2,8,10],M+'gray_concrete');box('ceiling',[x,9,8],[x+2,9,10],'steel_grate');}
for(const x of [9,14,19,24,28])for(const z of [8,13,18])one('ceiling',x,5,z,'industrial_utility_light[facing=down]');
for(const x of [8,15,22,28])one('ceiling',x,6,19,M+'sea_lantern');
// Seven parking bays, three-unit line pitch, five-unit bay depth, four-unit drive aisle.
for(let x=7;x<=28;x+=3)box('exterior',[x,1,26],[x,1,30],'white_lane_divider[facing=north]');
for(let x=8;x<=26;x+=3)box('exterior',[x,1,26],[x+1,1,26],'reinforced_concrete_slab[type=bottom]');
for(const x of [7,12,16,22,26,30])one('exterior',x,1,23,M+'polished_blackstone_wall');
for(const x of [4,32]){box('exterior',[x,1,19],[x,1,21],M+'polished_andesite');box('exterior',[x,2,19],[x,2,21],M+'oak_leaves[persistent=true]');}
box('exterior',[24,0,0],[31,0,3],'reinforced_concrete');
box('exterior',[24,1,0],[31,2,0],M+'gray_concrete');
box('exterior',[31,1,1],[31,2,3],M+'gray_concrete');
for(const [x,z,f] of [[7,22,'south'],[29,22,'south'],[5,8,'west'],[31,8,'east'],[12,4,'north'],[27,4,'north']])one('exterior',x,3,z,'industrial_utility_light[facing='+f+']');

// Sparse Vanilla StructureTemplate fixture payload: all peers placed in one transaction,
// followed by Vanilla's neighbour reconciliation. No air palette and no inventories.
export const fixtures=[];
function fixture(x,y,z,b){b=full(b);check(b);if(fixtures.some(v=>v.pos.join()===[x,y,z].join()))throw Error('Fixture overlap '+[x,y,z]);fixtures.push({pos:[x,y,z],block:b});}
function tall(x,z,name,f='south',extra=''){for(const [dy,h]of [[0,'lower'],[1,'upper']])fixture(x,1+dy,z,`${name}[facing=${f},half=${h}${extra}]`);}
for(const x of [11,15,19])for(let z=15;z<=18;z++)tall(x,z,'retail_shelf_single','east');
for(const x of [10,12,14])for(const [dx,s] of [[0,'left'],[1,'right']])for(const [dy,h]of [[0,'lower'],[1,'upper']])fixture(x+dx,1+dy,12,`beverage_cooler[facing=south,part=${h}_${s},left_open=false,right_open=false]`);
for(const z of [13,16,19])for(const [dz,p]of [[0,'left'],[1,'right']])fixture(29,1,z+dz,`chest_freezer[facing=west,part=${p},lid=closed]`);
// SOUTH double door expands CLOCKWISE = WEST, unlike cooler width convention.
for(const [x,s]of [[19,'left'],[18,'right']])for(const [y,h]of [[1,'lower'],[2,'upper']])fixture(x,y,21,`commercial_glass_double_door[facing=south,part=${h}_${s},open=false]`);
tall(7,22,'vending_machine','south',',broken=false');
fixture(29,1,22,'metal_trash_can');
for(const z of [18,20])fixture(8,2,z,'cash_register[facing=west]');
for(const [x,z,f]of [[9,5,'north'],[8,11,'south'],[23,11,'south']])tall(x,z,'steel_door',f,',hinge=left,open=false,powered=false');
tall(7,6,'industrial_locker');tall(8,6,'industrial_locker');tall(11,6,'water_dispenser');
fixture(7,2,8,'industrial_electrical_box[facing=east]');
fixture(13,1,6,M+'barrel[facing=up,open=false]');fixture(14,1,6,M+'barrel[facing=up,open=false]');
for(const [x,p]of [[19,'left'],[18,'center'],[17,'right']])fixture(x,1,6,`modern_office_desk[facing=south,part=${p}]`);
fixture(18,2,6,'office_computer_station[facing=south,lowered=true]');
fixture(18,1,8,'modern_office_chair[facing=north]');fixture(20,1,6,'low_filing_cabinet[facing=south]');
// Two closed stalls, shared centre partition. The door-specific supports update normally.
const partitions=[];
for(const x of [24,26,28])for(let z=6;z<=8;z++)partitions.push([x,z]);
for(const [x,z]of partitions){const has=(dx,dz)=>partitions.some(p=>p[0]===x+dx&&p[1]===z+dz);fixture(x,1,z,`restroom_partition[north=${has(0,-1)},south=${has(0,1)},east=${has(1,0)},west=${has(-1,0)},door_support=0]`);}
for(const x of [25,27]){fixture(x,1,6,'commercial_flushometer_toilet[facing=south]');tall(x,8,'restroom_stall_door','south',',hinge=left,open=false');}
tall(29,10,'commercial_wall_mounted_sink','west');
fixture(25,1,1,'commercial_dumpster[facing=south,part=master]');fixture(24,1,1,'commercial_dumpster[facing=south,part=secondary]');
fixture(15,2,4,'industrial_electrical_box[facing=north]');

// Minimal named NBT encoder, standard structure schema only.
const i32=n=>{let b=Buffer.alloc(4);b.writeInt32BE(n);return b;};
const str=s=>{let b=Buffer.from(s),h=Buffer.alloc(2);h.writeUInt16BE(b.length);return Buffer.concat([h,b]);};
const tag=(t,n,p)=>Buffer.concat([Buffer.from([t]),str(n),p]);
const compound=entries=>Buffer.concat([...entries,Buffer.from([0])]);
const list=(type,arr)=>Buffer.concat([Buffer.from([type]),i32(arr.length),...arr]);
function template(blocks){const palette=[...new Set(blocks.map(b=>b.block))];return gzipSync(tag(10,'',compound([
tag(3,'DataVersion',i32(3465)),tag(9,'size',list(3,size.map(i32))),
tag(9,'palette',list(10,palette.map(s=>{const [name,p]=s.replace(']','').split('[');return compound([tag(8,'Name',str(name)),...(p?[tag(10,'Properties',compound(p.split(',').map(pair=>{const[k,v]=pair.split('=');return tag(8,k,str(v));})))]:[])]);}))),
tag(9,'blocks',list(10,blocks.map(b=>compound([tag(9,'pos',list(3,b.pos.map(i32))),tag(3,'state',i32(palette.indexOf(b.block)))])))),tag(9,'entities',list(10,[]))])));}

const action=process.argv[2]??'plan';
if(action==='prepare'){
 const dir='run/saves/新的世界/generated/afl_authoring/structures';await mkdir(dir,{recursive:true});
 for(const [name,blocks]of [['store_v2_fixtures',fixtures],['store_v2_test_shelf',fixtures.filter(b=>b.pos[0]===11&&b.pos[2]===15)]]){
  await writeFile(`${dir}/${name}.nbt`,template(blocks),{flag:'wx'});
 }
 await mkdir('build/authoring_checks',{recursive:true});await writeFile('build/authoring_checks/convenience_store_v2_plan.json',JSON.stringify({origin,size,phases,fixtures},null,2));
 console.log({fixtureCells:fixtures.length,phases:Object.fromEntries(Object.entries(phases).map(([k,v])=>[k,v.length]))});
}else if(phases[action]){
 const c=new BridgeClient('./run');const status=await c.call('minecraft_status');const info=await c.call('authoring_info');
 if(status.world_session!==epoch||status.world!=='新的世界'||info.id!==id||JSON.stringify(info.min)!==JSON.stringify(origin)||JSON.stringify([info.width,info.height,info.depth])!==JSON.stringify(size))throw Error('LIVE_SCOPE_CHANGED');
 for(let i=0;i<phases[action].length;i+=128){const args={operations:phases[action].slice(i,i+128)};console.log(await c.call('we_batch_set',{...args,dry_run:true}));if(process.argv.includes('--apply'))console.log(await c.call('we_batch_set',args));}
}else console.log({origin,size,fixtures:fixtures.length,phases:Object.fromEntries(Object.entries(phases).map(([k,v])=>[k,v.length]))});
