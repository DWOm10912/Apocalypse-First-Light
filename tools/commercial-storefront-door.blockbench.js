(function(){
  if(!['commercial_glass_sliding_door','commercial_glass_swing_door'].includes(Project.name))throw Error('Unexpected active project');
  newProject(Formats.geckolib_model);
  Project.name='commercial_glass_swing_door';
  Project.texture_width=Project.texture_height=128;
  Project.box_uv=false;
  const cv=document.createElement('canvas');cv.width=cv.height=128;const cx=cv.getContext('2d');
  const palette=['#b8bdbf','#d4d8d9','#91999e','#343f48','#666f74','#e1e6e7'];
  palette.forEach((c,i)=>{cx.fillStyle=c;cx.fillRect(i*16,0,16,128);});
  cx.fillStyle='rgba(123,172,203,0.26)';cx.fillRect(96,0,32,128);
  const tx=new Texture({name:'commercial_glass_swing_door.png',render_mode:'translucent'}).fromDataURL(cv.toDataURL()).add();
  function group(name,parent,origin=[0,0,0]){const g=new Group({name,origin});g.addTo(parent);g.init();return g;}
  const root=group('commercial_door_root');const fixed=group('fixed_storefront',root);
  function box(name,a,b,g,m=0){const c=new Cube({name,from:a,to:b,autouv:0,box_uv:false});c.addTo(g);c.init();for(const [d,f]of Object.entries(c.faces)){f.texture=tx.uuid;const mat=m===0?(d==='up'?1:d==='down'?2:0):m;f.uv=[mat*16+3,3,mat*16+12,12];}return c;}
  box('outer_header',[-16,31.25,-2],[16,32,2],fixed);
  box('outer_left_jamb',[-16,0,-2],[-14.9,31.25,2],fixed);
  box('outer_right_jamb',[14.9,0,-2],[16,31.25,2],fixed);
  box('left_sill',[-14.9,0,-2],[-8.25,0.85,2],fixed);
  box('right_sill',[8.25,0,-2],[14.9,0.85,2],fixed);
  box('threshold',[-8.25,0,-1.6],[8.25,0.18,1.6],fixed,2);
  box('left_mullion',[-8.65,0,-1.7],[-7.95,31.25,1.7],fixed,1);
  box('right_mullion',[7.95,0,-1.7],[8.65,31.25,1.7],fixed,1);
  for(const [name,x0,x1]of [['transom_crossbar',-7.95,7.95],['left_transom_crossbar',-14.9,-8.65],['right_transom_crossbar',8.65,14.9]])box(name,[x0,30,-1.65],[x1,30.3,1.65],fixed,1);
  function glazing(name,x0,x1,y0,y1,g,z=0){
    box(name+'_gasket_left',[x0,y0,z-0.23],[x0+0.14,y1,z+0.23],g,3);
    box(name+'_gasket_right',[x1-0.14,y0,z-0.23],[x1,y1,z+0.23],g,3);
    box(name+'_gasket_bottom',[x0+0.14,y0,z-0.23],[x1-0.14,y0+0.14,z+0.23],g,3);
    box(name+'_gasket_top',[x0+0.14,y1-0.14,z-0.23],[x1-0.14,y1,z+0.23],g,3);
    box(name+'_glass',[x0+0.14,y0+0.14,z-0.06],[x1-0.14,y1-0.14,z+0.06],g,6);
    if(y1-y0>4)for(let i=0;i<3;i++)box(name+'_reflection_'+i,[x0+0.5+i*0.42,y1-2.15+i*0.42,z-0.082],[x0+0.92+i*0.42,y1-1.73+i*0.42,z-0.07],g,6);
  }
  glazing('left_sidelight',-14.9,-8.65,0.85,30,fixed);
  glazing('right_sidelight',8.65,14.9,0.85,30,fixed);
  glazing('left_upper_light',-14.9,-8.65,30.3,31.25,fixed);
  glazing('center_transom',-7.95,7.95,30.3,31.25,fixed);
  glazing('right_upper_light',8.65,14.9,30.3,31.25,fixed);
  for(const side of [-1,1]){
    const left=side<0,name=left?'left_door':'right_door',x0=left?-7.86:0.08,x1=left?-0.08:7.86;
    const door=group(name,root,[left?x0:x1,0,0]);
    box(name+'_hinge_stile',[x0,0.26,-0.65],[x0+0.7,29.85,0.65],door);
    box(name+'_meeting_stile',[x1-0.7,0.26,-0.65],[x1,29.85,0.65],door);
    box(name+'_top_rail',[x0+0.7,28.9,-0.65],[x1-0.7,29.85,0.65],door,1);
    box(name+'_bottom_rail',[x0+0.7,0.26,-0.65],[x1-0.7,1.48,0.65],door);
    glazing(name+'_infill',x0+0.7,x1-0.7,1.48,28.9,door);
    const handle=group(name+'_pull_handle',door);const hx=left?x1-0.53:x0+0.53;
    for(const face of [-1,1]){
      const z0=face<0?-1.8:0.66,z1=face<0?-0.66:1.8;
      for(const y of [8.0,12.7])box('handle_mount_'+face+'_'+y,[hx-0.24,y,z0],[hx+0.24,y+0.38,z1],handle,2);
      box('pull_bar_'+face,[hx-0.22,8,face<0?-2.08:1.8],[hx+0.22,13.08,face<0?-1.8:2.08],handle,1);
    }
    const hinges=group(name+'_hinges',door);const hx2=left?x0:x1;
    for(const y of [3.2,24.8]){
      box('hinge_leaf_'+y,[hx2-0.26,y,-0.86],[hx2+0.26,y+1.8,-0.65],hinges,2);
      box('hinge_knuckle_'+y,[hx2-0.18,y+0.15,-1.12],[hx2+0.18,y+1.65,-0.86],hinges,1);
    }
  }
  for(const animationName of ['door_open','door_close']){
  const animation=new Animation({name:animationName,length:0.6,loop:'hold'}).add();
  for(const [name,angle]of [['left_door',-90],['right_door',90]]){
    const a=animation.getBoneAnimator(Group.all.find(g=>g.name===name));
    for(const [time,p]of [[0,0],[0.05,0.11],[0.1,0.39],[0.15,0.72],[0.2,0.96],[0.23,1],[0.6,1]])a.addKeyframe({channel:'rotation',time,interpolation:'linear',data_points:[{x:0,y:angle*(animationName==='door_close'?1-p:p),z:0}]});
  }
  }
  Project.save_path='D:/Minecraft Modding/Apocalypse First Light/src/main/blockbench/commercial_glass_swing_door.bbmodel';
  Canvas.updateAll();unselectAllElements();
  return JSON.stringify({project:Project.name,cubes:Cube.all.length,groups:Group.all.length,texture:tx.uuid});
})();
