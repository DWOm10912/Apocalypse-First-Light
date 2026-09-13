(async()=>{
 const fs=require('fs'),base='D:/Minecraft Modding/Apocalypse First Light/';
 const path=base+'src/main/blockbench/commercial_flushometer_toilet.bbmodel';
 if(fs.existsSync(path))throw Error('Source already exists; inspect before replacing');
 const canvas=document.createElement('canvas');canvas.width=canvas.height=128;
 const ctx=canvas.getContext('2d');
 const colors=['#dddcd6','#ecebe5','#cacbc7','#b9bfbd','#a3aaa7','#8c9998','#b6c7c8','#d4ddda','#959c9e','#b4b9b9','#747e82','#d2d6d4','#555f63','#eeeDE7','#a8aeaa','#c4c8c4'];
 colors.forEach((color,i)=>{const x=i%4*32,y=Math.floor(i/4)*32;ctx.fillStyle=color;ctx.fillRect(x,y,32,32);ctx.fillStyle='rgba(255,255,255,0.055)';ctx.fillRect(x,y,32,5);ctx.fillStyle='rgba(0,0,0,0.035)';ctx.fillRect(x,y+26,32,6);});
 newProject(Formats.free);Project.name='commercial_flushometer_toilet';Project.texture_width=Project.texture_height=128;
 const texPath=base+'src/main/blockbench/textures/commercial_flushometer_toilet.png';
 const texture=new Texture({name:'commercial_flushometer_toilet.png',width:128,height:128}).fromDataURL(canvas.toDataURL()).add();texture.path=texPath;
 const groups={};
 function group(name,parent){return groups[name]=new Group({name,origin:[8,0,8]}).addTo(parent?groups[parent]:undefined).init();}
 group('commercial_toilet_root');group('toilet_body','commercial_toilet_root');
 for(const n of ['floor_base','pedestal','bowl_outer','bowl_inner','rear_connector'])group(n,'toilet_body');
 for(const n of ['toilet_seat','bowl_water','flushometer_assembly'])group(n,'commercial_toilet_root');
 for(const n of ['vertical_flush_pipe','valve_body','flush_handle','wall_supply_pipe','wall_flange'])group(n,'flushometer_assembly');
 const cube=(g,n,f,t,m)=>{const c=new Cube({name:n,from:f,to:t,autouv:0,box_uv:false}).addTo(groups[g]).init();for(const [side,face]of Object.entries(c.faces)){const mat=side==='down'&&m===0?2:m;const x=mat%4*32,y=Math.floor(mat/4)*32;face.texture=texture.uuid;face.uv=[x+1,y+1,x+31,y+31];}return c;};
 const widths=[2.5,3.6,4.6,5.35,5.8,6,6,5.8,5.3,4.7,4,3.4];
 for(let i=0;i<10;i++){const z=2+i*1.2,w=[2.4,3.1,3.6,3.8,3.8,3.8,3.8,3.8,3.7,3.5][i];cube('floor_base','base_segment_'+i,[8-w,0,z],[8+w,.65,z+1.2],2);}
 for(let layer=0;layer<3;layer++)for(let i=0;i<6;i++){const z=4+i*1.5,w=[2.45,2.6,2.75,2.85,3.1,3.2][i]+(layer===2?.3:0);cube('pedestal','pedestal_'+layer+'_'+i,[8-w,.65+layer*1.2,z],[8+w,1.85+layer*1.2,z+1.5],layer===0?2:0);}
 for(let layer=0;layer<3;layer++)for(let i=0;i<12;i++){
  const z=1.2+i,w=widths[i]-(2-layer)*.4,y=4.25+layer*1.05;
  const inner=(i<2||i>9)?0:Math.max(0,widths[i]-2.1-(2-layer)*.5);
  if(inner===0)cube('bowl_outer','bowl_solid_'+layer+'_'+i,[8-w,y,z],[8+w,y+1.05,z+1],0);
  else for(const s of [-1,1])cube('bowl_inner','cavity_wall_'+layer+'_'+i+'_'+s,[s<0?8-w:8+inner,y,z],[s<0?8-inner:8+w,y+1.05,z+1],layer===2?0:2);
 }
 cube('bowl_inner','recessed_sump',[5.7,4.18,4.5],[10.3,4.25,10.5],4);
 for(let i=0;i<12;i++){
  const z=1.2+i,w=widths[i],inner=(i<1||i>10)?0:Math.max(0,w-1.25);
  if(!inner)cube('bowl_outer','rim_end_'+i,[8-w,7.4,z],[8+w,7.9,z+1],1);
  else for(const s of [-1,1])cube('bowl_outer','rim_'+i+'_'+s,[s<0?8-w:8+inner,7.4,z],[s<0?8-inner:8+w,7.9,z+1],1);
  const seatInner=i<2?1.35:i>9?0:Math.max(1.35,w-1.55);
  if(!seatInner)cube('toilet_seat','seat_back_'+i,[8-w,7.95,z],[8+w,8.55,z+1],13);
  else for(const s of [-1,1])cube('toilet_seat','open_front_seat_'+i+'_'+s,[s<0?8-w:8+seatInner,7.95,z],[s<0?8-seatInner:8+w,8.55,z+1],13);
 }
 cube('rear_connector','ceramic_rear_shoulder',[4.6,6.7,13.2],[11.4,7.9,15.3],0);
 cube('rear_connector','rear_riser',[5,1.85,13],[11,6.7,14.8],0);
 for(const x of [4.6,11]){cube('floor_base','anchor_cap_'+x,[x,.65,11.8],[x+.4,1.02,12.3],8);cube('toilet_seat','seat_hinge_'+x,[x,7.9,12.8],[x+.55,8.12,13.5],9);}
 cube('vertical_flush_pipe','ceramic_socket',[7,7.9,13.35],[9,8.25,15.1],2);
 cube('vertical_flush_pipe','socket_nut',[7.15,8.25,13.5],[8.85,8.65,14.95],9);
 cube('vertical_flush_pipe','pipe',[7.65,8.65,13.85],[8.35,12,14.55],8);
 cube('vertical_flush_pipe','pipe_bright_face',[7.65,8.65,13.8],[7.86,12,13.85],11);
 cube('valve_body','lower_union',[7.35,11.65,13.5],[8.65,12.15,14.85],10);
 cube('valve_body','lower_nut',[7.15,12.15,13.3],[8.85,12.6,15.05],9);
 cube('valve_body','valve_core',[6.85,12.6,13],[9.15,14.9,15.3],8);
 cube('valve_body','cap_ring',[6.65,14.9,12.8],[9.35,15.4,15.5],9);
 cube('valve_body','cap_top',[6.95,15.4,13.1],[9.05,15.75,15.2],11);
 cube('valve_body','front_highlight',[6.85,12.6,12.95],[7.15,14.9,13],9);
 cube('wall_supply_pipe','horizontal_feed',[7.5,13.25,15.3],[8.5,14.25,15.75],8);
 cube('wall_supply_pipe','feed_collar',[7.3,13.05,15.3],[8.7,14.45,15.55],10);
 cube('wall_flange','wall_flange',[6.8,12.6,15.75],[9.2,15,16],9);
 cube('wall_flange','flange_center',[7.2,13,15.55],[8.8,14.6,15.75],11);
 cube('flush_handle','lever_socket',[6.4,13.1,13.45],[6.85,14,14.35],10);
 cube('flush_handle','lever_hub',[6.1,13.2,13.55],[6.4,13.9,14.25],9);
 cube('flush_handle','flush_lever',[4.3,13.37,13.7],[6.1,13.7,14.05],9);
 cube('flush_handle','lever_end',[4.05,13.3,13.65],[4.3,13.77,14.1],11);
 Cube.selected.empty();Group.all.forEach(g=>g.selected=false);Canvas.updateAll();
 fs.writeFileSync(texPath,Buffer.from(canvas.toDataURL().split(',')[1],'base64'));
 fs.writeFileSync(path,Codecs.project.compile());Project.save_path=path;Project.saved=true;
 return {path,cubes:Cube.all.length,texture:texture.uuid,front:'-Z',wall:'+Z at16'};
})()
