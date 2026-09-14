import {BridgeClient} from './bridge_client.mjs';
const b=new BridgeClient('run');const s=await b.call('minecraft_status');
if(s.world_session!=='623d05c6-a348-4f1d-bc49-8617ecb9d706')throw Error('WORLD_CHANGED');
const mode=process.argv[2];
const viewpoints={
  front:[[708,-26,-440],180,5],
  front_three_quarter:[[740,-21,-440],135,12],
  rear:[[708,-23,-514],0,10],
  rear_three_quarter:[[675,-19,-510],-45,16],
  left:[[675,-25,-479],-90,5],
  right:[[743,-25,-476],90,5],
  first_floor:[[704.5,-31,-472.5],130,0],
  kitchen:[[699.5,-31,-481.5],-60,8],
  second_floor:[[704,-26,-472.5],180,0],
  primary:[[697.5,-26,-471.5],150,10],
  garage:[[720,-31,-470],150,8],
  roof:[[709,-6,-470],180,60],
};
if(mode==='audit'){console.log(JSON.stringify(await b.call('inspect_selection',{target:'AUTHORING_SESSION'})));process.exit(0);}
if(mode==='slice'){console.log(JSON.stringify(await b.call('get_horizontal_slice',{target:'AUTHORING_SESSION',coordinate:Number(process.argv[3]),relative:true,encoding:'palette'})));process.exit(0);}
if(mode==='validate'){console.log(JSON.stringify(await b.call('authoring_validate')));process.exit(0);}
if(mode==='restore'){console.log(JSON.stringify(await b.call('camera_restore',{dry_run:true})));console.log(JSON.stringify(await b.call('camera_restore')));}
else {
  const v=viewpoints[mode];if(!v)throw Error('UNKNOWN_VIEW');const a={position:v[0],yaw:v[1],pitch:v[2]};
  console.log(JSON.stringify(await b.call('camera_move',{...a,dry_run:true})));
  console.log(JSON.stringify(await b.call('camera_move',a)));
}
let ready=false;for(let i=0;i<20;i++){const c=await b.call('camera_status');if(c.client_frame_ready){ready=true;break;}await new Promise(r=>setTimeout(r,150));}
if(!ready)throw Error('CAMERA_NOT_READY');
console.log(JSON.stringify(await b.call('capture_current_view')));
