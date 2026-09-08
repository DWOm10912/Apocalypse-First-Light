(async()=>{
 if(Cube.all.length||Texture.all.length||!['thermal_generator','thermal_generator_3d'].includes(Project.name))throw Error('Requires the empty thermal generator project');
 const specs=[], groups={};
 const group=(n,p='thermal_generator_root',origin=[8,0,8])=>groups[n]={p,origin};
 group('thermal_generator_root',null);
 for(const n of ['frame','front','top','rear','left_service_panel','right_service_panel'])group(n);
 for(const n of ['outer_frame','bottom_chassis','top_frame'])group(n,'frame');
 for(const n of ['front_function_panel','heat_window_frame','heat_window_glass','combustion_chamber','rotor_housing','status_lights'])group(n,'front');
 group('generator_rotor','front',[3.6,7.7,4.8]);
 for(const n of ['flame_core','flame_mid','flame_outer'])group(n,'combustion_chamber',[10.35,4.8,3.6]);
 for(const n of ['status_green','status_yellow','status_red'])group(n,'status_lights');
 for(const n of ['top_vent','exhaust_stack'])group(n,'top');
 group('rear_power_panel','rear');group('fe_output_port','rear_power_panel',[8,8,16]);
 const b=(g,n,f,t,m='steel',faces={},rotation=null)=>{if(f.some((v,i)=>v>=t[i]))throw Error(n);specs.push({g,n,f,t,of:f,ot:t,m,faces,rotation});};
 const bolt=(g,x,y,z)=>{b(g,'bolt_socket',[x-.25,y-.25,z],[x+.25,y+.25,z+.1],'dark');b(g,'bolt',[x-.14,y-.14,z-.12],[x+.14,y+.14,z],'edge');};
 const frame=(g,n,x0,y0,x1,y1,z,th=.5)=>{b(g,n+'_top',[x0,y1-th,z],[x1,y1,z+.35],'edge');b(g,n+'_bottom',[x0,y0,z],[x1,y0+th,z+.35],'steel');b(g,n+'_left',[x0,y0+th,z],[x0+th,y1-th,z+.35],'steel');b(g,n+'_right',[x1-th,y0+th,z],[x1,y1-th,z+.35],'steel');};
 for(const x of [.2,14.2])for(const z of [.7,13.7]){
  b('bottom_chassis','foot',[x,0,z],[x+1.6,.65,z+1.6],'edge');
  b('outer_frame','corner_column',[x,.65,z],[x+1.6,14.4,z+1.6]);
 }
 b('bottom_chassis','sealed_base',[.5,.65,.8],[15.5,1.5,15.5],'dark',{down:'bottom_reference'});
 b('bottom_chassis','lower_front_rail',[.3,1.5,.5],[15.7,2.1,1.25],'edge');
 b('bottom_chassis','lower_back_rail',[.3,1.5,14.9],[15.7,2.1,15.6],'edge');
 b('top_frame','roof',[.2,13.7,.5],[15.8,14.4,15.7],'panel');
 for(const x of [.2,15.05])b('top_frame','roof_side_lip',[x,14.4,.8],[x+.75,14.75,15.4],'edge');
 for(const z of [.5,14.95])b('top_frame','roof_end_lip',[.2,14.4,z],[15.8,14.75,z+.75],'steel');
 for(const x of [1,15])for(const y of [2.7,12.7])bolt('outer_frame',x,y,.45);
 for(const [g,x0,x1,face] of [['left_service_panel',15.45,15.9,'east'],['right_service_panel',.1,.55,'west']]){
  b(g,'side_backplate',[x0,2.1,2.25],[x1,13.7,13.7],'dark');
  b(g,'large_service_panel',[x0,4.55,2.7],[x1,12.65,13.15],'panel');
  for(const z of [2.5,12.85])b(g,'side_panel_vertical',[x0,2.65,z],[x1,13.2,z+.5],'steel');
  for(const y of [2.65,12.7])b(g,'side_panel_rail',[x0,y,2.5],[x1,y+.5,13.35],'edge');
  b(g,'side_intake_panel',[x0,3.05,3.05],[x1,5.7,8.4],'steel',{[face]:'vent'});
  b(g,'folded_service_seam',[x0,5.7,10.6],[x1,11.9,10.82],'dark');
  b(g,'offset_service_seam',[x0,11.9,9.55],[x1,12.2,10.82],'dark');
  for(const z of [3.1,12.6])for(const y of [3.4,12.4])b(g,'side_bolt',[x0-.04,y,z],[x1+.04,y+.27,z+.27],'edge');
 }
 b('rear_power_panel','rear_recess',[1.8,2.1,14.6],[14.2,13.7,15.45],'dark');
 b('rear_power_panel','service_cover',[2.35,2.75,15.45],[13.65,13.05,15.75],'panel');
 frame('rear_power_panel','rear_cover_rim',2.05,2.4,13.95,13.4,15.75,.45);
 b('rear_power_panel','rear_intake',[2.7,3,15.75],[13.3,4.5,16],'steel',{south:'vent'});
 b('rear_power_panel','rear_vertical_intake',[11.85,5,15.75],[13.3,11.85,16],'steel',{south:'vertical_vent'});
 b('fe_output_port','socket_mount',[5.5,5.5,15.75],[10.5,10.5,16],'steel',{south:'socket'});
 for(const x of [2.5,13.45])for(const y of [2.95,12.8])b('rear_power_panel','rear_fastener',[x,y,15.75],[x+.25,y+.25,16],'edge');
 b('front_function_panel','indicator_carrier',[6.55,11.55,.35],[14.1,13.7,1.4],'panel');
 b('front_function_panel','indicator_recess',[6.85,11.8,.15],[13.8,13.45,.35],'dark');
 for(const [x,n,m] of [[11.65,'status_green','green'],[9.35,'status_yellow','yellow'],[7.05,'status_red','red']]){
  b(n,'lamp_bezel',[x,12,.05],[x+1.65,13.3,.3],'steel');
  b(n,'lamp_lens',[x+.3,12.22,0],[x+1.35,13.08,.05],m);
 }
 b('combustion_chamber','chamber_back',[6.55,3.7,6.25],[14.1,11.55,6.65],'soot');
 b('combustion_chamber','chamber_floor',[6.55,3.7,1.3],[14.1,4.25,6.25],'soot');
 b('combustion_chamber','chamber_ceiling',[6.55,11.05,1.3],[14.1,11.55,6.25],'soot');
 for(const x of [6.55,13.6])b('combustion_chamber','chamber_wall',[x,4.25,1.3],[x+.5,11.05,6.25],'soot');
 frame('heat_window_frame','door_pressure_rim',6.65,3.75,14,11.5,.45,.65);
 frame('heat_window_frame','recessed_inner_lip',7.3,4.4,13.35,10.85,.85,.27);
 b('heat_window_frame','door_crossbar',[6.65,10.95,.1],[14,11.5,.45],'edge');
 for(const x of [7,13.65])for(const y of [4.1,11.13])bolt('heat_window_frame',x,y,.26);
 for(const y of [5.15,9.45])b('heat_window_frame','door_hinge',[13.9,y,.4],[14.3,y+.9,1.35],'steel');
 b('heat_window_glass','recessed_smoked_glass',[7.57,4.67,1.23],[13.08,10.58,1.27],'glass');
 for(const x of [7.45,8.5,9.55,10.6,11.65,12.7])b('combustion_chamber','fire_grate',[x,4.25,1.75],[x+.4,4.55,5.6],'dark');
 for(const z of [2.1,3.2,4.3])b('flame_outer','ember_bed',[7.9,4.55,z],[12.65,4.78,z+.6],'ember');
 for(const [x,h,z]of [[8.1,1.7,3.4],[9.15,3.2,4.3],[10.1,2.4,3.8],[11.25,2.8,4.2],[12,1.4,3.3]]){
  b('flame_outer','static_outer_flame',[x,4.78,z],[x+.65,4.78+h,z+.5],'flame_outer');
  b('flame_mid','static_middle_flame',[x+.1,4.78,z-.25],[x+.55,4.78+h*.7,z],'flame_mid');
  b('flame_core','static_flame_core',[x+.18,4.78,z-.4],[x+.47,4.78+h*.38,z-.25],'flame_core');
 }
 frame('front_function_panel','lower_left_intake',6.65,2.15,14.1,3.7,.35,.3);
 b('front_function_panel','lower_left_slits',[6.95,2.45,.55],[13.8,3.4,.95],'dark',{north:'vent'});
 frame('front_function_panel','lower_right_intake',1.85,2.15,6.3,3.7,.35,.3);
 b('front_function_panel','lower_right_slits',[2.15,2.45,.55],[6,3.4,.95],'dark',{north:'vent'});
 b('rotor_housing','rotor_rear_wall',[1.8,3.7,8.8],[6.55,12.8,9.3],'dark');
 b('rotor_housing','rotor_top_canopy',[1.65,12.05,.45],[6.55,13.7,8.8],'steel');
 b('rotor_housing','rotor_floor',[1.8,3.7,1.1],[6.55,4.2,8.8],'dark');
 for(const x of [1.85,5.8])b('rotor_housing','rotor_guard_post',[x,4.2,.45],[x+.55,12.05,1],'edge');
 b('rotor_housing','safety_crossbar',[1.85,6.35,.2],[6.35,6.8,.75],'steel');
 for(const x of [2.1,6.07])for(const y of [4.55,11.65])bolt('rotor_housing',x,y,.3);
 b('generator_rotor','rotor_axle',[1.8,7.3,4.4],[6.25,8.1,5.2],'edge');
 for(let i=0;i<8;i++){
  const a=i*Math.PI/4,cy=7.7+3.05*Math.cos(a),cz=4.8+3.05*Math.sin(a);
  let rot=i*45;while(rot>45)rot-=90;const swap=i%4===2||i%4===3;
  for(const [x0,x1,m] of [[2.5,3.1,'dark'],[3.1,4.25,'copper'],[4.25,4.85,'steel']]){
   const sy=swap?2.1:.8,sz=swap?.8:2.1;
   b('generator_rotor','octagonal_rotor_segment',[x0,cy-sy/2,cz-sz/2],[x1,cy+sy/2,cz+sz/2],m,{},rot?{axis:'x',angle:rot,origin:[3.6,cy,cz]}:null);
  }
 }
 for(const angle of [0,45,-45])b('generator_rotor','rotor_spoke',[3.25,5.25,4.57],[3.95,10.15,5.03],'dark',{},angle?{axis:'x',angle,origin:[3.6,7.7,4.8]}:null);
 b('top_vent','top_access_plinth',[8.1,14.4,7.8],[13.95,14.9,13.5],'dark');
 b('top_vent','raised_vent_lid',[8.25,15.2,7.95],[13.8,15.55,13.35],'edge');
 for(const x of [8.25,13.4])b('top_vent','lid_support',[x,14.9,8.05],[x+.4,15.2,13.25],'steel');
 for(const z of [8.5,9.4,10.3,11.2,12.1])b('top_vent','vent_louver',[8.7,14.9,z],[13.35,15.02,z+.3],'steel');
 for(const z of [3.05,5.2])b('top_frame','roof_service_seam',[7.6,14.4,z],[14,14.5,z+.12],'dark');
 b('exhaust_stack','stack_plinth',[2.2,14.4,10],[6.8,15,14.4],'edge');
 b('exhaust_stack','stack_lower',[2.65,15,10.45],[6.35,15.8,13.95],'panel');
 b('exhaust_stack','stack_mesh_core',[2.95,15.8,10.75],[6.05,16.85,13.65],'mesh');
 b('exhaust_stack','stack_cap',[2.6,16.85,10.4],[6.4,17.2,14],'edge');
 for(const x of [2.8,6])for(const z of [10.6,13.65])b('exhaust_stack','stack_screw',[x,15.8,z],[x+.2,15.95,z+.2],'edge');
 const colors={steel:'#464b50',panel:'#53585d',dark:'#282d31',edge:'#71777c',soot:'#242426',copper:'#aa682a',ember:'#643219',flame_outer:'#ad4319',flame_mid:'#e48023',flame_core:'#ffbd50',green:'#5f9143',yellow:'#b79a3e',red:'#a44938',glass:'#9da6aa',vent:'#3f4549',vertical_vent:'#3f4549',mesh:'#303538',socket:'#53585d',bottom_reference:'#393e43'};
 const canvas=document.createElement('canvas');canvas.width=canvas.height=256;const ctx=canvas.getContext('2d');ctx.imageSmoothingEnabled=false;
 ctx.fillStyle='#353a3e';ctx.fillRect(0,0,256,256);const tiles={};let index=0;
 for(const [name,c]of Object.entries(colors)){const x=index%8*32,y=Math.floor(index/8)*32;index++;tiles[name]=[x,y,x+32,y+32];ctx.fillStyle=c;ctx.fillRect(x,y,32,32);
  ctx.fillStyle='#ffffff07';ctx.fillRect(x+1,y+1,30,8);ctx.fillStyle='#00000009';ctx.fillRect(x+1,y+24,30,7);
  ctx.fillStyle='#ffffff20';ctx.fillRect(x,y,32,1);ctx.fillRect(x,y,1,32);ctx.fillStyle='#00000035';ctx.fillRect(x,y+31,32,1);ctx.fillRect(x+31,y,1,32);
 }
 const rect=(n,x,y,w,h,c)=>{ctx.fillStyle=c;ctx.fillRect(tiles[n][0]+x,tiles[n][1]+y,w,h);};
 for(const n of ['vent','vertical_vent'])for(let y=5;y<29;y+=6)rect(n,4,y,24,2,'#171c20');
 for(let y=2;y<30;y+=6)for(let x=2;x<30;x+=6){rect('mesh',x,y,3,3,'#171b1e');rect('mesh',x,y+3,3,1,'#565e63');}
 const [gx,gy]=tiles.glass;ctx.clearRect(gx,gy,32,32);ctx.fillStyle='#8d9b9d60';ctx.fillRect(gx+2,gy+3,1,21);ctx.fillStyle='#20272b90';ctx.fillRect(gx,gy,32,2);ctx.fillRect(gx,gy+30,32,2);
 const fs=require('fs'),base='D:/Minecraft Modding/Apocalypse First Light/src/main/resources/assets/apocalypse_firstlight/textures/block/';
 const img=new Image();img.src='data:image/png;base64,'+fs.readFileSync(base+'machine_back.png').toString('base64');await img.decode();
 ctx.drawImage(img,11,11,10,10,tiles.socket[0]+11,tiles.socket[1]+11,10,10);
 const bottom=new Image();bottom.src='data:image/png;base64,'+fs.readFileSync(base+'machine_bottom.png').toString('base64');await bottom.decode();ctx.drawImage(bottom,tiles.bottom_reference[0],tiles.bottom_reference[1]);
 const subtract=(a,o)=>{if(a.rotation||o.rotation)return[a];const lo=a.f.map((v,i)=>Math.max(v,o.f[i])),hi=a.t.map((v,i)=>Math.min(v,o.t[i]));if(lo.some((v,i)=>hi[i]-v<1e-6))return[a];let f=[...a.f],t=[...a.t],out=[];for(let i=0;i<3;i++){if(f[i]<lo[i]){let tt=[...t];tt[i]=lo[i];out.push({...a,f:[...f],t:tt});f[i]=lo[i];}if(t[i]>hi[i]){let ff=[...f];ff[i]=hi[i];out.push({...a,f:ff,t:[...t]});t[i]=hi[i];}}return out;};
 let solids=[];for(const s of specs){solids=solids.flatMap(p=>subtract(p,s));solids.push(s);}
 const faceAxes={north:[0,-1,1,-1],south:[0,1,1,-1],east:[2,-1,1,-1],west:[2,1,1,-1],up:[0,1,2,1],down:[0,1,2,-1]};
 const uvFor=(s,side)=>{const mat=s.faces[side]??s.m;let uv=tiles[mat];const [u,us,v,vs]=faceAxes[side];if(!['glass','vent','vertical_vent','mesh','socket','bottom_reference'].includes(mat)){const dim=s.ot.map((n,i)=>n-s.of[i]);uv=[uv[0]+2,uv[1]+2,uv[0]+2+Math.min(28,Math.max(2,dim[u]*4)),uv[1]+2+Math.min(28,Math.max(2,dim[v]*4))];}const frac=(axis,sign)=>sign>0?[(s.f[axis]-s.of[axis])/(s.ot[axis]-s.of[axis]),(s.t[axis]-s.of[axis])/(s.ot[axis]-s.of[axis])]:[(s.ot[axis]-s.t[axis])/(s.ot[axis]-s.of[axis]),(s.ot[axis]-s.f[axis])/(s.ot[axis]-s.of[axis])];const [a,b]=frac(u,us),[c,d]=frac(v,vs);return[uv[0]+(uv[2]-uv[0])*a,uv[1]+(uv[3]-uv[1])*c,uv[0]+(uv[2]-uv[0])*b,uv[1]+(uv[3]-uv[1])*d];};
 Undo.initEdit({elements:[],textures:[],outliner:true});Project.name='thermal_generator_3d';Project.geometry_name='thermal_generator_3d';Project.texture_width=Project.texture_height=256;
 const texture=new Texture({name:'thermal_generator_3d.png',namespace:'apocalypse_firstlight',folder:'block'}).fromDataURL(canvas.toDataURL()).add(false);texture.particle=true;
 const live={};for(const [n,d]of Object.entries(groups)){const g=new Group({name:n,origin:d.origin});if(d.p)g.addTo(live[d.p]);g.init();live[n]=g;}
 for(const s of solids){const c=new Cube({name:s.n,from:s.f,to:s.t,box_uv:false,autouv:0,origin:s.rotation?.origin??groups[s.g].origin,rotation:s.rotation?['x','y','z'].map(a=>s.rotation.axis===a?s.rotation.angle:0):[0,0,0]}).addTo(live[s.g]).init();
  for(const side of ['north','south','east','west','up','down'])c.faces[side].extend({uv:uvFor(s,side),texture:texture.uuid});
 }
 for(const [slot,data]of Object.entries({gui:{rotation:[25,-135,0],scale:[.624,.624,.624],translation:[0,-.5,0]},ground:{scale:[.4,.4,.4],translation:[0,2,0]},fixed:{rotation:[0,180,0],scale:[.7,.7,.7]},thirdperson_righthand:{rotation:[75,45,0],scale:[.38,.38,.38]},firstperson_righthand:{rotation:[0,45,0],scale:[.45,.45,.45]}}))Project.display_settings[slot]=new DisplaySlot(slot,data);
 Undo.finishEdit('Build thermal generator 3D V1');Canvas.updateAll();
 eval(fs.readFileSync('D:/Minecraft Modding/Apocalypse First Light/tools/align-thermal-rotor.bb.js','utf8'));
 await eval(fs.readFileSync('D:/Minecraft Modding/Apocalypse First Light/tools/add-thermal-fuel-windows.bb.js','utf8'));
 return JSON.stringify({cubes:Cube.all.length,groups:Group.all.length,texture:texture.uuid,tiles});
})()
