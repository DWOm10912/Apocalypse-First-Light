(() => {
 if(Project.uuid!=='9c1865a4-f31d-4978-ad47-b11c579d342a'||Cube.all.length)throw Error('Expected empty monitor');
 Undo.initEdit({elements:[],textures:[],outliner:true});Project.texture_width=Project.texture_height=128;Project.ambientocclusion=false;
 const c=document.createElement('canvas');c.width=c.height=128;const ctx=c.getContext('2d');ctx.fillStyle='#141719';ctx.fillRect(0,0,128,128);
 const colors=['#303437','#3a3f42','#24282b','#444a4d','#1d2124','#50565a','#292e31','#373c40'];colors.forEach((v,i)=>{const x=i%4*32,y=64+Math.floor(i/4)*32;ctx.fillStyle=v;ctx.fillRect(x,y,32,32);ctx.fillStyle='rgba(255,255,255,.018)';ctx.fillRect(x+4,y+5,12,8);});
 const tex=new Texture({name:'modern_lcd_monitor.png',namespace:'apocalypse_firstlight',folder:'block',id:'0'}).fromDataURL(c.toDataURL()).add(false);
 const gs={};function g(n,p,o=[8,0,8]){gs[n]=new Group({name:n,origin:o}).addTo(p?gs[p]:'root').init();}
 g('modern_lcd_monitor_root');g('display_assembly','modern_lcd_monitor_root',[8,6.5,8.1]);g('bezel','display_assembly',[8,6.5,8.1]);for(const n of ['top','bottom','left','right'])g('bezel_'+n,'bezel',[8,6.5,8.1]);for(const n of ['screen_surface','status_indicator'])g(n,'display_assembly',[8,6.5,8.1]);g('rear_housing_assembly','display_assembly',[8,6.5,8.1]);for(const n of ['rear_housing_main','rear_center_panel','rear_detail'])g(n,'rear_housing_assembly',[8,6.5,8.1]);g('stand_assembly','modern_lcd_monitor_root',[8,.6,8.5]);for(const n of ['stand_column','stand_mount','stand_connector'])g(n,'stand_assembly',[8,.6,8.5]);g('base_assembly','modern_lcd_monitor_root');for(const n of ['base_main','base_upper','base_lower'])g(n,'base_assembly');
 function b(n,f,t,m,p){const u=m%4*32,v=64+Math.floor(m/4)*32,faces={};for(const d of ['north','south','east','west','up','down'])faces[d]={uv:[u+1,v+1,u+31,v+31],texture:tex.uuid};return new Cube({name:n,from:f,to:t,faces,box_uv:false,autouv:0}).addTo(gs[p]).init();}
 const screen=b('lcd_black_panel',[.7,4,7.11],[15.3,12.2125,7.2],4,'screen_surface');screen.faces.north.uv=[0,0,128,72];
 screen.faces.north.uv=[0,0,112,63];
 const ring=(prefix,x0,x1,y0,y1,z0,z1,w,m,parent)=>{b(prefix+'_top',[x0,y1-w,z0],[x1,y1,z1],m,parent||'bezel_top');b(prefix+'_bottom',[x0,y0,z0],[x1,y0+w,z1],m,parent||'bezel_bottom');b(prefix+'_left',[x0,y0+w,z0],[x0+w,y1-w,z1],m,parent||'bezel_left');b(prefix+'_right',[x1-w,y0+w,z0],[x1,y1-w,z1],m,parent||'bezel_right');};
 ring('outer_shell',0,16,3.05,12.8,7.45,8.1,.35,0);ring('front_bezel',.1,15.9,3.15,12.7,7.03,7.45,.5,0);ring('outer_edge',0,16,3.05,12.8,7.28,7.45,.1,1);ring('panel_recess',.6,15.4,3.9,12.3125,7.06,7.2,.1,4);ring('rear_edge',.12,15.88,3.17,12.68,8.1,8.2,.16,6,'rear_housing_main');
 b('lower_bezel_face',[.6,3.15,7.03],[15.4,3.9,7.45],0,'bezel_bottom');b('lower_bezel_reveal',[.6,3.9,7.03],[15.4,4,7.06],6,'bezel_bottom');b('top_bezel_fill',[.6,12.3125,7.03],[15.4,12.7,7.45],0,'bezel_top');
 b('status_socket',[7.8,3.4,6.99],[8.2,3.7,7.03],4,'status_indicator');b('status_unlit_insert',[7.9,3.48,6.97],[8.1,3.61,6.99],3,'status_indicator');
 b('rear_main_panel',[.35,3.4,7.5],[15.65,12.45,8.12],6,'rear_housing_main');b('rear_center_moulding',[5.8,4.2,8.12],[10.2,8.5,8.45],0,'rear_center_panel');b('rear_center_cover',[6.15,4.5,8.45],[9.85,8.2,8.6],6,'rear_center_panel');
 ring('rear_center_border',5.8,10.2,4.2,8.5,8.45,8.5,.12,1,'rear_center_panel');
 for(const y of [4,11.6]){b('rear_seam_'+y,[1,y,8.12],[15,y+.06,8.14],4,'rear_detail');for(let i=0;i<6;i++)b('rear_vent_'+y+'_'+i,[5.2+i*.95,y+.22,8.12],[5.85+i*.95,y+.32,8.145],4,'rear_detail');}
 b('stand_column_body',[7.3,.55,8.2],[8.7,5.4,9.05],0,'stand_column');b('stand_column_front',[7.45,.75,8.1],[8.55,4.8,8.2],6,'stand_column');b('stand_column_back',[7.45,.75,9.05],[8.55,5.1,9.15],1,'stand_column');
 for(const x of [7.3,8.6])b('stand_column_edge_'+x,[x,.65,8.2],[x+.1,5.3,9.05],1,'stand_column');
 b('stand_foot_socket',[6.95,.5,7.95],[9.05,.9,9.3],6,'stand_mount');ring('stand_socket_lip',6.95,9.05,.6,.9,7.95,8.08,.08,1,'stand_mount');
 b('stand_rear_bracket',[7,4.5,8.6],[9,6.8,8.95],0,'stand_connector');b('tilt_joint_body',[6.8,6.1,8.45],[9.2,6.8,9.05],6,'stand_connector');for(const x of [6.65,9.2])b('tilt_joint_end_'+x,[x,6.2,8.5],[x+.15,6.7,9],3,'stand_connector');
 for(const x of [7.2,8.55])for(const y of [4.8,6.45])b('mount_recess_'+x+'_'+y,[x,y,8.95],[x+.25,y+.2,8.98],4,'stand_connector');
 b('base_core',[4.4,.15,5.6],[11.6,.5,10.4],0,'base_main');b('base_upper_plate',[4.6,.5,5.8],[11.4,.65,10.2],1,'base_upper');b('base_under_plate',[4.6,.08,5.8],[11.4,.15,10.2],4,'base_lower');
 for(const [name,z]of [['front',5.6],['back',10.3]]){b('base_'+name+'_edge',[4.4,.4,z],[11.6,.5,z+.1],3,'base_main');b('base_'+name+'_under_edge',[4.5,.15,z+.02],[11.5,.23,z+.08],6,'base_lower');}
 for(const x of [4.4,11.5]){b('base_side_edge_'+x,[x,.4,5.7],[x+.1,.5,10.3],3,'base_main');b('base_side_under_'+x,[x+.02,.15,5.7],[x+.08,.23,10.3],6,'base_lower');}
 for(const x of [4.85,10.45])for(const z of [6.05,9.45]){b('base_rubber_pad_'+x+'_'+z,[x,0,z],[x+.7,.08,z+.5],4,'base_lower');b('base_pad_mount_'+x+'_'+z,[x-.08,.08,z-.08],[x+.78,.15,z+.58],6,'base_lower');}
 Project.save_path='D:/Minecraft Modding/Apocalypse First Light/src/main/blockbench/modern_lcd_monitor.bbmodel';Undo.finishEdit('Modern LCD monitor');Canvas.updateAll();return {cubes:Cube.all.length,groups:Group.all.length};
})()
