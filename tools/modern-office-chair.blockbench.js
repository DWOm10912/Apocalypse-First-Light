(() => {
 if(Project.uuid!=='b0ecf7de-022c-86c0-b8e4-7d87636c1433'||Cube.all.length)throw Error('Expected empty chair project');
 Undo.initEdit({elements:[],textures:[],outliner:true});
 Project.texture_width=Project.texture_height=128;Project.ambientocclusion=false;
 const canvas=document.createElement('canvas');canvas.width=canvas.height=128;const ctx=canvas.getContext('2d');
 const palette=['#33373b','#3b3f43','#45494d','#292d31','#303437','#3a3f42','#24282b','#555b5e','#444a4d','#1d2124','#777d7e','#50565a','#383c40','#2b3033','#474d51','#62686b'];
 palette.forEach((v,i)=>{const x=i%4*32,y=Math.floor(i/4)*32;ctx.fillStyle=v;ctx.fillRect(x,y,32,32);ctx.fillStyle='rgba(255,255,255,.025)';ctx.fillRect(x+3,y+4,12,10);ctx.fillRect(x+19,y+20,10,8);ctx.fillStyle='rgba(0,0,0,.02)';ctx.fillRect(x+4,y+23,9,6);});
 const tex=new Texture({name:'modern_office_chair.png',namespace:'apocalypse_firstlight',folder:'block',id:'0'}).fromDataURL(canvas.toDataURL()).add(false);
 const groups={};function g(n,p,o=[8,7,8]){groups[n]=new Group({name:n,origin:o}).addTo(p?groups[p]:'root').init();}
 function b(n,f,t,m,p,r=[0,0,0],o=[8,0,8]){const x=m%4*32,y=Math.floor(m/4)*32,faces={};for(const d of ['north','south','east','west','up','down'])faces[d]={uv:[x+1,y+1,x+31,y+31],texture:tex.uuid};return new Cube({name:n,from:f,to:t,faces,rotation:r,origin:o,box_uv:false,autouv:0}).addTo(groups[p]).init();}
 g('office_chair_root',null,[8,0,8]);
 for(const n of ['backrest_assembly','seat_assembly','left_armrest_assembly','right_armrest_assembly','center_column_assembly','wheel_base_assembly'])g(n,'office_chair_root',n==='wheel_base_assembly'?[8,2,8]:[8,7,8]);
 for(const n of ['backrest_main','backrest_frame','backrest_support'])g(n,'backrest_assembly',[8,8,12]);
 for(const n of ['seat_main','seat_edge','seat_support_plate'])g(n,'seat_assembly');
 for(const s of ['left','right'])for(const n of ['pad','support'])g(s+'_armrest_'+n,s+'_armrest_assembly');
 for(const n of ['center_column','gas_lift_cover','central_mount'])g(n,'center_column_assembly',[8,3,8]);
 g('base_hub','wheel_base_assembly',[8,2,8]);
 b('seat_cushion_core',[3.1,7.5,3],[12.9,8.45,12.2],0,'seat_main');
 b('seat_soft_top',[3.4,8.45,3.3],[12.6,8.7,11.9],1,'seat_main');
 b('seat_under_shell',[3.3,7.1,3.2],[12.7,7.5,12],6,'seat_support_plate');
 for(const [n,z]of [['front',2.85],['rear',12.2]]){b('seat_'+n+'_rounded_edge',[3.4,7.7,z],[12.6,8.35,z+.15],1,'seat_edge');b('seat_'+n+'_welt',[3.4,7.5,z+.03],[12.6,7.65,z+.12],3,'seat_edge');}
 for(const [n,x]of [['left',2.95],['right',12.9]]){b('seat_'+n+'_rounded_edge',[x,7.7,3.3],[x+.15,8.35,11.9],1,'seat_edge');b('seat_'+n+'_welt',[x+.03,7.5,3.3],[x+.12,7.65,11.9],3,'seat_edge');}
 for(const x of [3.1,12.6])for(const z of [3,11.9])b('seat_corner_'+x+'_'+z,[x,7.65,z],[x+.3,8.4,z+.3],0,'seat_edge');
 for(const x of [4,11]){b('underseat_rail_'+x,[x,6.7,4],[x+1,7.1,11.5],4,'seat_support_plate');for(const z of [4.4,10.4]){b('seat_mount_'+x+'_'+z,[x-.15,6.5,z],[x+1.15,6.7,z+.7],8,'seat_support_plate');b('seat_mount_bolt_'+x+'_'+z,[x+.4,6.4,z+.25],[x+.6,6.5,z+.45],10,'seat_support_plate');}}
 for(let i=0;i<3;i++){const y=9.1+i*3.2,z=11.65+i*.35;b('backrest_panel_'+i,[3.65,y,z],[12.35,y+3.14,z+.85],i===1?0:12,'backrest_main');b('backrest_rear_shell_'+i,[3.4,y-.08,z+.85],[12.6,y+3.18,z+1.1],6,'backrest_frame');for(const x of [3.35,12.35]){b('backrest_side_rail_'+i+'_'+x,[x,y,z+.18],[x+.3,y+3.2,z+.85],4,'backrest_frame');b('backrest_side_piping_'+i+'_'+x,[x+.05,y+.08,z+.1],[x+.25,y+3.1,z+.18],2,'backrest_frame');}}
 b('backrest_top_soft_edge',[3.85,18.64,12.42],[12.15,19.2,13.02],1,'backrest_main');
 b('backrest_top_frame',[3.6,18.7,13.02],[12.4,19.05,13.45],4,'backrest_frame');
 b('backrest_lower_edge',[3.8,8.9,11.8],[12.2,9.1,12.6],3,'backrest_frame');
 for(const x of [5.5,9.8]){b('backrest_mount_upright_'+x,[x,7,12],[x+.7,12.7,12.65],4,'backrest_support');b('backrest_mount_foot_'+x,[x,6.8,9.8],[x+.7,7.2,12.65],4,'backrest_support');b('backrest_mount_cover_'+x,[x-.12,9.7,12.65],[x+.82,12.4,12.9],5,'backrest_support');for(const y of [10,12])b('backrest_mount_cap_'+x+'_'+y,[x+.22,y,12.9],[x+.48,y+.25,13],8,'backrest_support');}
 for(const [s,x]of [['left',1.4],['right',13.3]]){const p=s+'_armrest_pad',q=s+'_armrest_support';b(s+'_arm_pad_core',[x,10.25,4.1],[x+1.3,10.65,10.4],6,p);b(s+'_arm_pad_top',[x+.1,10.65,4.25],[x+1.2,10.8,10.25],1,p);for(const z of [4,10.4])b(s+'_arm_pad_end_'+z,[x+.15,10.3,z],[x+1.15,10.6,z+.1],0,p);b(s+'_arm_post',[x+.25,7,7.3],[x+1.05,10.25,8.25],4,q);b(s+'_arm_post_inner',[x+.35,7.5,7.2],[x+.95,9.9,7.3],5,q);b(s+'_arm_mount',[Math.min(x+.25,4),6.85,7.2],[Math.max(x+1.05,12),7.1,8.35],6,q);b(s+'_arm_post_socket',[x+.17,7,7.22],[x+1.13,7.55,8.33],8,q);for(const y of [7.3,9.7])b(s+'_arm_fastener_'+y,[x+.5,y,8.25],[x+.75,y+.25,8.36],7,q);}
 b('seat_tilt_housing',[5.7,5.8,6],[10.3,6.7,10.3],6,'central_mount');b('tilt_lower_plate',[6,5.6,6.3],[10,5.8,10],4,'central_mount');b('gas_lift_piston',[7.6,3.2,7.6],[8.4,5.6,8.4],10,'center_column');
 for(let i=0;i<3;i++){const w=1.65-i*.2;b('gas_lift_sleeve_'+i,[8-w/2,2.3+i*.65,8-w/2],[8+w/2,2.95+i*.65,8+w/2],i%2?4:6,'gas_lift_cover');}
 b('height_lever_stem',[9.8,6.1,8.8],[12.2,6.3,9.1],7,'central_mount');b('height_lever_grip',[11.6,6,8.5],[12.65,6.45,9.45],6,'central_mount');
 for(const [y,w]of [[1.6,2.5],[2.2,2.1],[2.6,1.8]])b('hub_tier_'+y,[8-w/2,y,8-w/2],[8+w/2,y+.4,8+w/2],4,'base_hub');
 for(let i=0;i<5;i++){const a=i*72,rad=a*Math.PI/180,r=[0,a,0],o=[8,0,8],n=String(i+1).padStart(2,'0'),p='leg_'+n,q='wheel_'+n;g(p,'wheel_base_assembly',[8,2,8]);g(q,p,[8+Math.sin(rad)*6.3,1,8+Math.cos(rad)*6.3]);
 const box=(name,f,t,m,grp=p)=>b(name+'_'+n,f,t,m,grp,r,o);
 box('base_leg_inner',[7.35,1.85,8.8],[8.65,2.55,10.5],4);box('base_leg_middle',[7.4,1.55,10.5],[8.6,2.25,12.5],4);box('base_leg_outer',[7.45,1.3,12.5],[8.55,1.95,14.3],4);
 box('leg_inner_top',[7.48,2.55,9],[8.52,2.65,10.45],5);box('leg_middle_top',[7.53,2.25,10.55],[8.47,2.35,12.45],5);box('leg_outer_top',[7.58,1.95,12.55],[8.42,2.05,14.2],5);
 box('caster_socket',[7.5,1.1,13.8],[8.5,1.3,14.7],6);box('caster_swivel_pin',[7.8,.95,14],[8.2,1.3,14.4],10,q);box('caster_fork_bridge',[7.2,.95,13.7],[8.8,1.15,14.7],4,q);
 for(const x of [7.15,8.35]){box('wheel_tire_'+x,[x,.15,13.5],[x+.5,.95,14.9],9,q);box('wheel_top_'+x,[x+.03,.95,13.7],[x+.47,1.1,14.7],6,q);box('wheel_bottom_'+x,[x+.03,0,13.7],[x+.47,.15,14.7],6,q);box('wheel_nose_'+x,[x+.04,.3,13.35],[x+.46,.8,13.5],6,q);box('wheel_tail_'+x,[x+.04,.3,14.9],[x+.46,.8,15.05],6,q);box('wheel_hub_'+x,[x-.035,.4,14],[x+.535,.7,14.4],8,q);}
 }
 Project.save_path='D:/Minecraft Modding/Apocalypse First Light/src/main/blockbench/modern_office_chair.bbmodel';
 Undo.finishEdit('Modern office chair with five casters');for(const p of Preview.all){if(!p.controls||!p.camera)continue;p.controls.target.set(8,9,8);p.camera.position.set(37,28,-36);p.camera.zoom=1.5;p.camera.updateProjectionMatrix();p.controls.update();}Canvas.updateAll();return {cubes:Cube.all.length,groups:Group.all.length};
})()
