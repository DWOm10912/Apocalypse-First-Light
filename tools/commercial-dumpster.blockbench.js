(() => {
 if(Project.uuid!=='9fbb94a0-6491-3958-f87b-1b168ea7e0d3'||Cube.all.length)throw Error('Expected empty dumpster project');
 Undo.initEdit({elements:[],textures:[],outliner:true});
 const canvas=document.createElement('canvas');canvas.width=128;canvas.height=128;
 const ctx=canvas.getContext('2d');
 const colors=['#29483d','#315346','#3c6051','#203b32','#272b2e','#34393d','#474d50','#1c2221','#51584d','#515c58','#253f35','#1a2c25'];
 colors.forEach((color,i)=>{const x=i%4*32,y=Math.floor(i/4)*40;ctx.fillStyle=color;ctx.fillRect(x,y,32,40);ctx.fillStyle=['#385c4d','#426754','#527360','#305142','#373c40','#454b50','#5b6163','#303732','#61685d','#656f68','#355445','#2a3b32'][i];ctx.fillRect(x+1,y+1,30,1);ctx.fillRect(x+1,y+2,1,36);ctx.fillStyle='rgba(0,0,0,.17)';ctx.fillRect(x+29,y+2,2,37);ctx.fillRect(x+2,y+37,27,2);if(i===8||i===9){ctx.fillStyle='#62675c';ctx.fillRect(x+4,y+3,5,1);ctx.fillRect(x+23,y+33,4,2);ctx.fillStyle='#47463b';ctx.fillRect(x+3,y+31,9,4);}});
 const tex=new Texture({name:'commercial_dumpster.png',namespace:'apocalypse_firstlight',folder:'block',id:'0'}).fromDataURL(canvas.toDataURL()).add(false);
 Project.texture_width=128;Project.texture_height=128;Project.ambientocclusion=false;
 const groups={};
 function group(name,parent,origin=[16,0,8]){groups[name]=new Group({name,origin}).addTo(parent?groups[parent]:'root').init();}
 group('commercial_dumpster_root');group('dumpster_body','commercial_dumpster_root');
 for(const n of ['body_front','body_back','body_left','body_right','body_bottom','top_rim','bottom_skids','structural_details'])group(n,'dumpster_body');
 for(const side of ['left','right']){const x=side==='left'?8:24;group(side+'_lid_assembly','commercial_dumpster_root',[x,21,15]);for(const n of ['main','ribs','frame','handle_optional'])group(side+'_lid_'+n,side+'_lid_assembly',[x,21,15]);group(side+'_lift_pocket_assembly','commercial_dumpster_root',[side==='left'?0:32,8,8]);for(const n of ['shell','supports'])group(side+'_lift_pocket_'+n,side+'_lift_pocket_assembly');}
 group('hinge_bar_assembly','commercial_dumpster_root',[16,21,15]);for(const n of ['hinge_bar','left_hinge_parts','right_hinge_parts'])group(n,'hinge_bar_assembly',[16,21,15]);group('optional_small_details','commercial_dumpster_root');
 function box(name,from,to,mat,g,rotation=[0,0,0],origin=[16,0,8],smooth=false){const x=mat%4*32,y=Math.floor(mat/4)*40,uv=smooth?[x+4,y+4,x+27,y+35]:[x,y,x+32,y+40];const faces={};for(const f of ['north','south','east','west','up','down'])faces[f]={uv:uv.slice(),texture:tex.uuid};return new Cube({name,from,to,faces,box_uv:false,autouv:0,rotation,origin}).addTo(groups[g]).init();}
 const slope=Math.tan(Math.PI/8);
 for(let i=0;i<8;i++){const x=1+i*3.75;box('front_panel_'+i,[x,1.25,1],[x+3.75,15.45,1.45],i%3===1?1:0,'body_front',undefined,undefined,true);box('rear_panel_'+i,[x,1.25,14.55],[x+3.75,21,15],0,'body_back',undefined,undefined,true);}
 for(const side of ['left','right']){const x=side==='left'?1:30.55;for(let i=0;i<16;i++){const z=1.45+i*13.1/16;box(side+'_slope_panel_'+i,[x,1.25,z],[x+.45,15.45+(z-1)*slope, z+13.1/16],0,'body_'+side,undefined,undefined,true);}}
 box('sealed_inner_floor',[1,1,1],[31,1.5,15],11,'body_bottom',undefined,undefined,true);
 for(const side of ['front','back']){const z=side==='front'?.8:15,top=side==='front'?15.4:20.9;for(let i=0;i<=8;i++){const x=.85+i*3.75;box(side+'_panel_stiffener_'+i,[x,1.2,z],[x+.3,top,z+.2],1,'structural_details');}for(let i=0;i<4;i++){const x=.75+i*7.5;box(side+'_lower_edge_'+i,[x,1,z-.05],[x+7.5,1.7,z+.3],8,'structural_details');}}
 for(const x of [.8,30.5])for(const z of [.8,14.5])box('corner_post_'+x+'_'+z,[x,1,z],[x+.7,z<8?15.2:20.8,z+.7],1,'structural_details');
 for(const x of [1.2,15.2,29.2]){box('skid_rail_'+x,[x,0,1],[x+1.6,1,15],8,'bottom_skids');for(const z of [1,13]){box('skid_shoe_'+x+'_'+z,[x-.15,0,z],[x+1.75,.35,z+2],9,'bottom_skids');}box('skid_center_pad_'+x,[x-.1,.35,6],[x+1.7,1.2,10],8,'bottom_skids');}
 for(const x of [.65,30.55])box('sloped_upper_rail_'+x,[x,20.65,.6],[x+.8,21.25,15.4],2,'top_rim',[-22.5,0,0],[16,21,15]);
 box('front_upper_crossrail',[.65,14.95,.65],[31.35,15.6,1.45],2,'top_rim');box('rear_upper_crossrail',[.65,20.55,14.5],[31.35,21.25,15.35],2,'top_rim');
 for(const side of ['left','right']){const x0=side==='left'?.75:16.15,x1=side==='left'?15.85:31.25,p=[side==='left'?8:24,21,15],r=[-22.5,0,0];const b=(n,f,t,m,g)=>box(side+'_lid_'+n,f,t,m,side+'_lid_'+g,r,p);
  b('plate',[x0,21,.55],[x1,21.35,15.3],4,'main');
  for(let i=0;i<10;i++){const x=x0+.55+i*1.4;b('rib_'+i,[x,21.35,1.05],[x+.45,21.65,14.7],5,'ribs');b('rib_nose_'+i,[x,21.35,.65],[x+.45,21.5,1.05],6,'ribs');b('rib_tail_'+i,[x,21.35,14.7],[x+.45,21.5,15.1],5,'ribs');}
  for(const x of [x0,x1-.25])b('side_bead_'+x,[x,21.35,.55],[x+.25,21.6,15.3],5,'frame');
  for(const z of [.55,15.05])b('cross_bead_'+z,[x0+.25,21.35,z],[x1-.25,21.6,z+.25],5,'frame');
  const mid=(x0+x1)/2;for(const x of [mid-2,mid+1.65])b('grip_foot_'+x,[x,21.6,1.55],[x+.35,22,2.05],6,'handle_optional');b('grip',[mid-2,22,1.55],[mid+2,22.3,2.05],5,'handle_optional');
 }
 box('rear_hinge_axle',[.3,20.85,14.9],[31.7,21.15,15.25],9,'hinge_bar');
 for(const side of ['left','right']){const start=side==='left'?1.2:16.6;for(let i=0;i<3;i++){const x=start+i*6.1;box(side+'_hinge_saddle_'+i,[x,20.4,14.6],[x+.9,21.35,15.4],2,side+'_hinge_parts');box(side+'_hinge_pin_'+i,[x-.1,20.85,15.35],[x+1,21.15,15.5],9,side+'_hinge_parts');}}
 for(const side of ['left','right']){const a=side==='left'?-1.1:31,b=side==='left'?1:33.1,g=side+'_lift_pocket_shell',h=side+'_lift_pocket_supports';
  box(side+'_pocket_roof',[a,9.4,4],[b,9.85,12],2,g);box(side+'_pocket_floor',[a,6.7,4],[b,7.15,12],3,g);box(side+'_pocket_outer_wall',[side==='left'?a:b-.4,7.15,4],[side==='left'?a+.4:b,9.4,12],0,g);box(side+'_pocket_mount_back',[side==='left'?.7:31,6.4,3.7],[side==='left'?1:31.3,10.15,12.3],1,g);
  for(const z of [4.3,10.8]){box(side+'_pocket_bracket_'+z,[a+.25,5.9,z],[b-.25,6.7,z+.75],3,h);box(side+'_pocket_lower_brace_'+z,[side==='left'?.2:31.1,5.3,z],[side==='left'?.9:31.8,5.9,z+.75],3,h);}
  for(const z of [3.65,12]){box(side+'_pocket_mount_strip_'+z,[side==='left'?.55:31.05,6.2,z],[side==='left'?.95:31.45,10.3,z+.35],2,h);for(const y of [6.6,9.7])box(side+'_pocket_bolt_'+z+'_'+y,[side==='left'?.4:31.45,y,z+.06],[side==='left'?.55:31.6,y+.2,z+.26],9,h);}
  for(const z of [4,11.7]){box(side+'_pocket_mouth_top_'+z,[a,9.85,z],[b,10,z+.3],9,g);box(side+'_pocket_mouth_bottom_'+z,[a,6.55,z],[b,6.7,z+.3],8,g);}
 }
 for(const side of ['front','back']){const z=side==='front'?.73:15.21;for(let i=0;i<9;i++){const x=.92+i*3.75;for(const y of [2,side==='front'?14.35:19.85])box(side+'_fastener_'+i+'_'+y,[x,y,z],[x+.16,y+.16,z+.07],9,'optional_small_details');}}
 Project.save_path='D:/Minecraft Modding/Apocalypse First Light/src/main/blockbench/commercial_dumpster.bbmodel';Project.credit='Apocalypse: First Light — Commercial Dumpster';
 Undo.finishEdit('Commercial dumpster shell, twin lids and lift pockets');Canvas.updateAll();
 return {cubes:Cube.all.length,groups:Group.all.length};
})()
