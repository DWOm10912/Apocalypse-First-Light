(() => {
 if(Project.uuid!=='601baf23-d800-23e5-c0e3-fae6ba39682a'||Cube.all.length)throw Error('Expected empty office desk');
 Undo.initEdit({elements:[],textures:[],outliner:true});
 Project.name='modern_office_desk';Project.texture_width=128;Project.texture_height=128;Project.ambientocclusion=false;
 const c=document.createElement('canvas');c.width=c.height=128;const ctx=c.getContext('2d');
 const colors=['#d1d0c8','#bcbdb7','#e0dfd7','#969994','#303437','#3a3f42','#24282b','#555b5e','#444a4d','#1d2124','#777d7e','#b0b2ad','#c9c9c2','#34393c','#50575a','#2b3033'];
 colors.forEach((v,i)=>{let x=i%4*32,y=Math.floor(i/4)*32;ctx.fillStyle=v;ctx.fillRect(x,y,32,32);ctx.fillStyle='rgba(255,255,255,.035)';ctx.fillRect(x+4,y+5,13,8);ctx.fillRect(x+20,y+20,9,7);ctx.fillStyle='rgba(0,0,0,.025)';ctx.fillRect(x+5,y+22,10,6);});
 const tex=new Texture({name:'modern_office_desk.png',namespace:'apocalypse_firstlight',folder:'block',id:'0'}).fromDataURL(c.toDataURL()).add(false);
 const gs={};function g(n,p){gs[n]=new Group({name:n,origin:[8,0,8]}).addTo(p?gs[p]:'root').init();}
 g('modern_office_desk_root');for(const n of ['tabletop_assembly','left_frame_assembly','right_frame_assembly','center_underframe','modesty_panel','cable_management'])g(n,'modern_office_desk_root');
 function b(n,f,t,m,p){let u=m%4*32,v=Math.floor(m/4)*32;const faces={};for(const d of ['north','south','east','west','up','down'])faces[d]={uv:[u+1,v+1,u+31,v+31],texture:tex.uuid};return new Cube({name:n,from:f,to:t,faces,box_uv:false,autouv:0}).addTo(gs[p]).init();}
 b('tabletop_main',[-16,11,0],[32,12,16],0,'tabletop_assembly');
 for(const [name,z]of [['front',0],['back',15.9]]){b('tabletop_edge_'+name,[-16,11,z],[32,11.85,z+.1],1,'tabletop_assembly');b('tabletop_lower_reveal_'+name,[-15.9,10.9,z],[31.9,11,z+.1],3,'tabletop_assembly');}
 for(const [name,x]of [['left',-16],['right',31.9]]){b('tabletop_edge_'+name,[x,11,.1],[x+.1,11.85,15.9],1,'tabletop_assembly');b('tabletop_lower_reveal_'+name,[x,10.9,.1],[x+.1,11,15.9],3,'tabletop_assembly');}
 for(const [side,x]of [['left',-14.8],['right',29.4]]){
 const p=side+'_frame_assembly';
 for(const [end,z]of [['front',1.2],['rear',13.4]]){g(side+'_'+end+'_leg',p);const q=side+'_'+end+'_leg';
 b(side+'_'+end+'_upright',[x,.35,z],[x+1.4,10.65,z+1.4],4,q);
 b(side+'_'+end+'_foot',[x-.16,0,z-.16],[x+1.56,.35,z+1.56],6,q);
 b(side+'_'+end+'_foot_collar',[x-.08,.35,z-.08],[x+1.48,.6,z+1.48],5,q);
 b(side+'_'+end+'_outer_edge',[x,.6,z],[x+.12,10.65,z+.12],5,q);
 b(side+'_'+end+'_top_socket',[x-.06,9.9,z-.06],[x+1.46,10.6,z+1.46],5,q);
 for(const yy of [1.35,9.95]){b(side+'_'+end+'_joint_plate_'+yy,[x+1.4,yy,z+.15],[x+1.5,yy+.55,z+1.25],8,q);b(side+'_'+end+'_joint_screw_'+yy,[x+1.5,yy+.18,z+.57],[x+1.56,yy+.37,z+.77],10,q);}
 }
 b(side+'_top_support',[x,9.85,2.6],[x+1.4,10.85,13.4],4,p);
 b(side+'_bottom_support',[x,.75,2.6],[x+1.4,1.7,13.4],4,p);
 b(side+'_bottom_upper_edge',[x,1.7,2.6],[x+1.4,1.77,13.4],5,p);
 for(const z of [2.6,12.4]){b(side+'_upper_corner_bracket_'+z,[x+.15,9.2,z],[x+1.25,9.85,z+1],6,p);b(side+'_lower_corner_bracket_'+z,[x+.15,1.7,z],[x+1.25,2.15,z+1],6,p);}
 for(const z of [2,7.3,12.6]){b(side+'_table_mount_'+z,[x-.25,10.65,z],[x+1.65,10.9,z+1.4],8,p);for(const xx of [x-.15,x+1.3])b(side+'_mount_screw_'+z+'_'+xx,[xx,10.53,z+.6],[xx+.2,10.65,z+.8],10,p);}
 }
 for(const [name,z]of [['front',2.4],['rear',12.4]]){b(name+'_long_support_bar',[-13.4,9.85,z],[29.4,10.85,z+1.1],4,'center_underframe');b(name+'_beam_lower_edge',[-13.4,9.8,z+.1],[29.4,9.85,z+1],6,'center_underframe');for(const x of [-13.4,27.9]){b(name+'_beam_end_sleeve_'+x,[x,9.75,z-.08],[x+1.5,10.9,z+1.18],5,'center_underframe');for(const xx of [x+.3,x+1])b(name+'_sleeve_bolt_'+xx,[xx,10.15,z-.15],[xx+.2,10.35,z-.08],10,'center_underframe');}}
 for(const x of [-5,7.5,20]){b('cross_support_'+x,[x,10.15,3.5],[x+1,10.8,12.4],5,'center_underframe');for(const z of [3.5,11.2]){b('cross_mount_'+x+'_'+z,[x-.35,9.95,z],[x+1.35,10.15,z+1.2],8,'center_underframe');b('cross_mount_bolt_'+x+'_'+z,[x+.4,9.85,z+.5],[x+.6,9.95,z+.7],10,'center_underframe');}}
 b('modesty_panel_main',[-12.5,4.7,12.85],[28.5,9.5,13.15],4,'modesty_panel');
 for(const y of [4.7,9.3]){b('panel_fold_'+y,[-12.5,y,12.55],[28.5,y+.2,12.85],5,'modesty_panel');b('panel_return_'+y,[-12.5,y,13.15],[28.5,y+.2,13.3],6,'modesty_panel');}
 for(const x of [-12.5,28.25])b('panel_end_fold_'+x,[x,4.9,12.55],[x+.25,9.3,13.3],5,'modesty_panel');
 for(const x of [-11,7.5,26]){b('panel_hanger_'+x,[x,8.65,12.3],[x+.8,10.3,12.55],8,'modesty_panel');b('panel_hanger_return_'+x,[x,8.65,12.55],[x+.8,9,12.85],8,'modesty_panel');for(const y of [8.8,9.8])b('panel_fixing_'+x+'_'+y,[x+.3,y,12.22],[x+.5,y+.2,12.3],10,'modesty_panel');}
 b('cable_tray_floor',[-10,8.9,13.6],[26,9.1,15.2],6,'cable_management');
 for(const z of [13.6,15]){b('tray_fold_'+z,[-10,9.1,z],[26,9.7,z+.2],4,'cable_management');b('tray_safety_lip_'+z,[-10,9.7,z],[26,9.8,z+.2],5,'cable_management');}
 for(const x of [-10,25.8])b('tray_end_stop_'+x,[x,9.1,13.8],[x+.2,9.5,15],4,'cable_management');
 for(const x of [-8,23]){b('tray_mount_vertical_'+x,[x,9.1,14.3],[x+.7,10.75,14.5],8,'cable_management');b('tray_mount_foot_'+x,[x,9.1,13.9],[x+.7,9.3,14.3],8,'cable_management');b('tray_mount_top_'+x,[x,10.75,13.8],[x+.7,10.9,14.8],8,'cable_management');for(const z of [14,14.5])b('tray_mount_screw_'+x+'_'+z,[x+.25,10.65,z],[x+.45,10.75,z+.2],10,'cable_management');}
 Project.save_path='D:/Minecraft Modding/Apocalypse First Light/src/main/blockbench/modern_office_desk.bbmodel';
 Undo.finishEdit('Modern office desk');Canvas.updateAll();return {cubes:Cube.all.length,groups:Group.all.length};
})()
