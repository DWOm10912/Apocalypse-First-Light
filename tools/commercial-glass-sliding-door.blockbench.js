(function () {
  if (Project.name !== 'commercial glass door' || Outliner.elements.length) throw Error('Expected the inspected empty door project');
  Formats.geckolib_model.convertTo();
  Project.name = 'commercial_glass_sliding_door';
  Project.texture_width = Project.texture_height = 128;
  const canvas = document.createElement('canvas'); canvas.width = canvas.height = 128;
  const ctx = canvas.getContext('2d');
  const colors = ['#abb0b3','#c3c8cb','#858d92','#363d42','#191f24','#738491','#d7e1e5','rgba(175,210,222,0.16)'];
  colors.forEach((c,i)=>{ctx.fillStyle=c;ctx.fillRect(i*16,0,16,128);});
  const texture = new Texture({name:'commercial_glass_sliding_door.png'}).fromDataURL(canvas.toDataURL()).add();
  function group(name,parent){const g=new Group({name,origin:[0,0,0]});g.addTo(parent);g.init();return g;}
  const root=group('sliding_door_root');const frame=group('frame',root);
  function cube(name,from,to,parent,material=0){
    const c=new Cube({name,from,to,autouv:0});c.addTo(parent);c.init();
    for(const f of Object.values(c.faces)){f.texture=texture.uuid;f.uv=[material*16+2,2,material*16+14,14];}
    return c;
  }
  cube('header_motor_housing',[-16,29.5,-3],[16,32,3],frame);
  cube('header_front_inset',[-15.6,29.85,-3.16],[15.6,31.5,-3],frame,1);
  cube('header_lower_seal',[-15.5,29.25,-2.7],[15.5,29.5,2.7],frame,3);
  cube('sill',[-16,0,-3],[16,0.3,3],frame,2);
  for(let t=0;t<3;t++)cube('track_'+t,[-15.8,0.3,-1.45+t*1.35],[15.8,0.42,-1.3+t*1.35],frame,3);
  for(const side of [-1,1]){
    const a=side<0?-16:11.05,b=side<0?-11.05:16;
    const p=group(side<0?'left_pocket':'right_pocket',frame);
    cube('pocket_front',[a,0.3,-3],[b,29.5,-2.6],p,0);
    cube('pocket_back',[a,0.3,2.6],[b,29.5,3],p,0);
    cube('outer_jamb',[side<0?-16:15.45,0.3,-2.6],[side<0?-15.45:16,29.5,2.6],p,1);
    cube('pocket_front_reveal',[side<0?-15.45:11.25,1,-3.03],[side<0?-11.25:15.45,28.8,-3],p,2);
    cube('pocket_edge_highlight',[side<0?-11.25:11.05,0.6,-3.06],[side<0?-11.05:11.25,29.2,-2.6],p,1);
  }
  const sensor=group('sensor',frame);
  cube('sensor_housing',[-1.5,30.05,-3.65],[1.5,31.3,-3.16],sensor,3);
  cube('sensor_lens',[-1.08,30.28,-3.71],[1.08,31.06,-3.65],sensor,4);
  cube('sensor_status',[-0.18,30.1,-3.72],[0.18,30.2,-3.65],sensor,5);
  const travel={};
  for(const side of [-1,1])for(let i=0;i<3;i++){
    const name=(side<0?'left':'right')+'_sliding_panel_'+(i+1);
    const g=group(name,root);const x0=side<0?-4*(i+1):4*i,x1=x0+4,z=-1.9+i*1.35;
    travel[name]=side*(11.1-4*i);
    cube('stile_outer',[x0,0.45,z],[x0+0.26,29.2,z+0.55],g,1);
    cube('stile_inner',[x1-0.26,0.45,z],[x1,29.2,z+0.55],g,0);
    cube('rail_top',[x0+0.26,28.55,z],[x1-0.26,29.2,z+0.55],g,0);
    cube('rail_bottom',[x0+0.26,0.45,z],[x1-0.26,1.15,z+0.55],g,2);
    cube('glass',[x0+0.26,1.15,z+0.24],[x1-0.26,28.55,z+0.3],g,7);
    cube('safety_manifestation',[x0+0.32,14.45,z+0.22],[x1-0.32,14.65,z+0.24],g,6);
    if(i===0)cube('meeting_seal',[side<0?-0.1:0,1.15,z-0.06],[side<0?0:0.1,28.55,z],g,4);
  }
  const animations=[['door_open',false],['door_close',true]];
  for(const [name,reverse]of animations){
    const animation=new Animation({name,length:0.6,loop:'hold'}).add();
    for(const [bone,dx]of Object.entries(travel)){
      const g=Group.all.find(g=>g.name===bone);const animator=animation.getBoneAnimator(g);
      for(const [time,p]of [[0,0],[0.15,0.15625],[0.3,0.5],[0.45,0.84375],[0.6,1]])animator.addKeyframe({channel:'position',time,interpolation:'catmullrom',data_points:[{x:dx*(reverse?1-p:p),y:0,z:0}]});
    }
  }
  Project.save_path='D:/Minecraft Modding/Apocalypse First Light/src/main/blockbench/commercial_glass_sliding_door.bbmodel';
  Canvas.updateAll();unselectAllElements();
  return JSON.stringify({name:Project.name,cubes:Cube.all.length,travel,texture:texture.uuid});
})();
