(() => {
 if(Project.uuid!=='42df8ab4-de82-2321-38b1-b2d0c0aeb904'||Cube.all.length||Texture.all.length)throw Error('Expected inspected empty water_dispenser project');
 const atlas=document.createElement('canvas');atlas.width=atlas.height=128;const ctx=atlas.getContext('2d');
 const regions={shell:[0,0,48,64],side:[48,0,80,64],door:[0,64,40,112],bottle:[80,0,128,64],ring:[80,64,128,80],dark:[40,64,64,96],metal:[64,64,80,96],red:[40,96,56,112],blue:[56,96,72,112],rubber:[72,96,88,112],label:[88,80,112,104],badge:[88,104,128,120],vent:[0,112,40,128],edge:[40,112,80,128],cap:[112,80,128,104]};
 const colors={shell:[187,183,169],side:[165,164,153],door:[186,182,168],bottle:[78,111,139,208],ring:[106,136,159,226],dark:[48,51,50],metal:[128,132,129],red:[135,53,43],blue:[43,82,121],rubber:[40,43,43],label:[158,178,187],badge:[176,175,160],vent:[40,44,43],edge:[204,199,184],cap:[48,61,66]};
 let seed=417;const rand=()=>{seed=(seed*1664525+1013904223)>>>0;return seed/4294967296;};
 for(const [name,r] of Object.entries(regions)){const [x,y,x2,y2]=r,c=colors[name],a=(c[3]??255)/255;
  ctx.fillStyle=`rgba(${c[0]},${c[1]},${c[2]},${a})`;ctx.fillRect(x,y,x2-x,y2-y);
  for(let i=0;i<(x2-x)*(y2-y)/30;i++){const px=x+1+Math.floor(rand()*(x2-x-2)),py=y+1+Math.floor(rand()*(y2-y-2));const d=rand()<.55?-9:7;ctx.fillStyle=`rgba(${c[0]+d},${c[1]+d},${c[2]+d},${a})`;ctx.fillRect(px,py,1+Math.floor(rand()*3),1+Math.floor(rand()*2));}
  ctx.fillStyle=`rgba(${c[0]+18},${c[1]+18},${c[2]+16},${a})`;ctx.fillRect(x,y,x2-x,1);ctx.fillRect(x,y,1,y2-y);
  ctx.fillStyle=`rgba(${c[0]-21},${c[1]-20},${c[2]-18},${a})`;ctx.fillRect(x,y2-1,x2-x,1);ctx.fillRect(x2-1,y,1,y2-y);
  if(['shell','side','door'].includes(name)){for(let i=0;i<18;i++){const px=x+Math.floor(rand()*(x2-x)),py=y2-2-Math.floor(rand()*8);ctx.fillStyle='#8b8778';ctx.fillRect(px,py,1,1+Math.floor(rand()*3));}ctx.fillStyle='#dad5c6';for(let i=0;i<5;i++)ctx.fillRect(x+3+Math.floor(rand()*(x2-x-8)),y+3+Math.floor(rand()*(y2-y-9)),1,3);}
 }
 ctx.fillStyle='rgba(140,167,186,.85)';ctx.fillRect(85,4,2,52);ctx.fillRect(89,9,1,39);ctx.fillStyle='rgba(61,89,113,.6)';ctx.fillRect(121,5,3,54);
 ctx.fillStyle='#49667b';ctx.fillRect(91,84,14,2);ctx.fillRect(91,88,10,1);ctx.fillRect(91,90,8,1);ctx.fillRect(91,93,12,1);
 ctx.fillStyle='#687f8b';ctx.fillRect(101,96,7,3);ctx.fillRect(103,94,3,2);
 ctx.fillStyle='#64675f';ctx.fillRect(101,107,3,3);ctx.fillRect(105,105,3,5);ctx.fillRect(109,107,3,3);ctx.fillRect(98,112,16,1);ctx.fillRect(102,115,10,1);
 Undo.initEdit({elements:[],textures:[],outliner:true});
 const texture=new Texture({name:'water_dispenser.png',namespace:'apocalypse_firstlight',folder:'block',id:'0',render_sides:'front'}).fromDataURL(atlas.toDataURL()).add(false);
 Project.texture_width=Project.texture_height=128;
 const groups={};function group(name,parent){const g=new Group({name,origin:[8,0,8]}).addTo(parent?groups[parent]:'root').init();groups[name]=g;return g;}
 group('water_dispenser_root');for(const n of ['water_bottle','upper_body','dispensing_section','lower_body','side_details','rear_panel','base'])group(n,'water_dispenser_root');
 for(const n of ['bottle_body','bottle_rings','bottle_neck','bottle_label'])group(n,'water_bottle');
 for(const n of ['recess_frame','inner_dark_panel','hot_control','cold_control','hot_spout','cold_spout','drip_tray'])group(n,'dispensing_section');
 for(const n of ['lower_door','door_handle','lower_vents'])group(n,'lower_body');
 const specs=[];
 function add(name,from,to,material,parent,extra={}){const r=regions[material];const faces={};for(const d of ['north','south','east','west','up','down'])faces[d]={uv:r.slice(),texture:texture.uuid};const c=new Cube({name,from,to,box_uv:false,autouv:0,faces,...extra}).addTo(groups[parent]).init();specs.push({name,material,parent,uuid:c.uuid});return c;}
 globalThis.aflWater={project:Project.uuid,texture:texture.uuid,groups,regions,specs,add};
 add('plinth_center',[2,0,2.4],[14,1,13.6],'dark','base');
 for(const [name,f,t] of [['front',[2.3,0,2.1],[13.7,1,2.4]],['rear',[2.3,0,13.6],[13.7,1,13.9]],['left',[1.7,.2,2.7],[2,1,13.3]],['right',[14,.2,2.7],[14.3,1,13.3]]])add('plinth_'+name,f,t,'metal','base');
 for(const x of [2.2,12.4])for(const z of [2.8,12.2])add('rubber_foot_'+x+'_'+z,[x,0,z],[x+1.4,.35,z+1.1],'rubber','base');
 for(const x of [2,13.15]){add('side_shell_'+x,[x,1,2.6],[x+.85,20.05,13.4],'side','upper_body');add('front_corner_'+x,[x,1,2.15],[x+.85,20.05,2.6],'shell','upper_body');add('rear_corner_'+x,[x,1,13.4],[x+.85,20.05,13.75],'edge','upper_body');}
 add('rear_enclosure',[2.85,1,13.05],[13.15,20.05,13.55],'side','rear_panel');
 add('crown_top_plate',[2.2,20.05,2.4],[13.8,20.4,13.5],'edge','upper_body');
 add('crown_front_lip',[2.45,19.85,2.05],[13.55,20.2,2.4],'shell','upper_body');
 add('front_brand_panel',[2.85,17.7,2.25],[13.15,19.85,3.25],'shell','upper_body');
 add('front_emblem',[6.1,18.2,2.2],[9.9,19.4,2.25],'badge','upper_body');
 add('lower_cabinet_core',[2.85,1,2.65],[13.15,9.7,13.05],'side','lower_body');
 add('door_shadow_gasket',[3,1.25,2.35],[13,9.35,2.65],'rubber','lower_door');
 add('door_skin',[3.18,1.45,2.1],[12.82,9.17,2.35],'door','lower_door');
 for(const x of [3.18,12.57])add('door_edge_'+x,[x,1.45,2.03],[x+.25,9.17,2.1],'edge','lower_door');
 for(const y of [1.45,8.97])add('door_horizontal_edge_'+y,[3.43,y,2.03],[12.57,y+.2,2.1],'shell','lower_door');
 for(const y of [2.5,7.7]){add('hinge_'+y,[12.82,y,2.14],[13.13,y+.75,2.53],'metal','lower_door');add('hinge_pin_'+y,[12.92,y-.1,2.02],[13.06,y+.85,2.17],'dark','lower_door');}
 for(const y of [3.25,6.45])add('handle_mount_'+y,[3.85,y,1.75],[4.55,y+.45,2.15],'metal','door_handle');
 add('vertical_handle',[3.95,3.4,1.45],[4.45,6.75,1.83],'dark','door_handle');add('handle_edge',[3.95,3.6,1.4],[4.09,6.55,1.46],'metal','door_handle');
 add('recess_back',[3.05,9.7,5.7],[12.95,17.7,6.15],'dark','inner_dark_panel');
 for(const x of [2.85,12.45])add('recess_side_liner_'+x,[x,9.7,2.65],[x+.7,17.7,5.7],'dark','recess_frame');
 for(const x of [2.85,12.8])add('recess_outer_frame_'+x,[x,9.5,2.12],[x+.35,17.8,2.65],'edge','recess_frame');
 add('control_panel_border',[3.2,16.05,1.98],[12.8,17.8,2.65],'metal','recess_frame');
 add('control_panel_face',[3.42,16.22,1.88],[12.58,17.61,1.98],'dark','recess_frame');
 add('recess_ceiling',[3.55,15.85,2.65],[12.45,16.2,5.7],'dark','inner_dark_panel');
 for(const x of [3.65,12.1])add('recess_inner_seam_'+x,[x,10.1,5.6],[x+.18,15.6,5.7],'metal','inner_dark_panel');
 for(const [x,z] of [[4.5,4.5],[10,4.5],[4.5,10],[10,10]])add('bottle_cradle_pad_'+x+'_'+z,[x,20.4,z],[x+1.5,20.55,z+1.5],'rubber','upper_body');
 add('bottle_socket',[5.1,20.4,5.1],[10.9,20.75,10.9],'dark','upper_body');
 add('bottle_socket_rim',[5.4,20.75,5.4],[10.6,20.9,10.6],'metal','upper_body');
 Undo.finishEdit('Water dispenser: textured cabinet and recessed dispensing bay');Canvas.updateAll();
 return {stage:'cabinet',cubes:Cube.all.length,texture:texture.uuid,groups:Group.all.length};
})()
