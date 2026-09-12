(function () {
  if (Cube.all.length || Texture.all.length) throw new Error('Run only in an empty cash register project');
  Project.name = 'afl_cash_register';
  Project.texture_width = Project.texture_height = 128;
  const canvas = document.createElement('canvas');
  canvas.width = canvas.height = 128;
  const ctx = canvas.getContext('2d');
  const colors = ['#292d30','#353a3e','#42484c','#22272b','#535b60','#70797e','#899196','#151b20','#253540','#344954','#576c7b','#93a6b3','#303436','#45494a','#585d5e','#1d2124'];
  colors.forEach((color,i) => {
    const x=(i%4)*32, y=Math.floor(i/4)*32;
    ctx.fillStyle=color; ctx.fillRect(x,y,32,32);
    ctx.fillStyle='rgba(255,255,255,0.045)'; ctx.fillRect(x,y,32,2);
    ctx.fillStyle='rgba(0,0,0,0.065)'; ctx.fillRect(x,y+30,32,2);
  });
  const tex = new Texture({name:'afl_cash_register.png'}).fromDataURL(canvas.toDataURL()).add(false);
  const root = new Group({name:'cash_register_root',origin:[8,0,8]}).init();
  const groups = {};
  ['base_body','front_drawer','keypad_panel','display_mount','display_screen','side_shell_left','side_shell_right','covered_service_module','rear_details'].forEach(name => groups[name] = new Group({name,origin:[8,0,8]}).addTo(root).init());
  function box(group,name,from,to,mat=0,rotation=null,origin=null) {
    const c = new Cube({name,from,to,autouv:0,box_uv:false});
    if(rotation) { c.rotation=rotation; c.origin=origin.slice(); }
    c.addTo(groups[group]).init();
    for(const [dir,face] of Object.entries(c.faces)) {
      const m=dir==='down' ? (mat===0?3:mat) : mat;
      const u=(m%4)*4, v=Math.floor(m/4)*4;
      face.texture=tex.uuid;
      face.uv=[u+.15,v+.15,u+3.85,v+3.85].map(n=>n*8);
    }
    return c;
  }
  const b=(n,f,t,m=0)=>box('base_body',n,f,t,m);
  b('lower_recessed_plinth',[1.25,.18,2.65],[14.75,.6,13.4],3);
  for(const x of [1.65,12.75]) for(const z of [3.1,11.9]) b('rubber_foot_'+x+'_'+z,[x,0,z],[x+1.6,.2,z+1.2],15);
  b('drawer_case_core',[1.35,.6,2],[14.65,2.85,14],0);
  b('top_perimeter_deck',[1.15,2.85,2.15],[14.85,3.12,13.85],2);
  b('deck_inset',[1.45,3.12,2.5],[14.55,3.25,13.5],1);
  for(const [x,label] of [[1,'left'],[14.65,'right']]) {
    b(label+'_edge_rail',[x,.7,2.15],[x+.35,2.8,13.85],1);
    b(label+'_top_edge',[x,2.8,2.3],[x+.35,2.9,13.7],2);
    b(label+'_rear_corner',[x,.65,13.85],[x+.35,2.85,14],2);
  }
  const d=(n,f,t,m=0)=>box('front_drawer',n,f,t,m);
  d('drawer_shadow_reveal',[1.65,.85,1.97],[14.35,2.55,2.04],7);
  d('drawer_front_face',[1.85,1.02,1.83],[14.15,2.35,2],1);
  d('drawer_top_highlight',[1.9,2.35,1.84],[14.1,2.44,1.98],2);
  d('drawer_bottom_return',[1.9,.96,1.88],[14.1,1.02,2],3);
  d('lock_bezel',[7.66,1.43,1.68],[8.34,2.05,1.84],4);
  d('lock_face',[7.78,1.54,1.61],[8.22,1.94,1.68],6);
  d('lock_key_slot',[7.965,1.62,1.595],[8.035,1.83,1.61],7);
  d('drawer_lower_grip',[5.85,1.01,1.77],[10.15,1.13,1.83],3);
  const pivot=[10,3.8,4];
  const rot=[-22.5,0,0];
  const k=(n,f,t,m=0)=>box('keypad_panel',n,f,t,m,rot,pivot);
  // All keypad parts share one pivot, preserving the slope and surface clearance.
  k('sloped_panel_subshell',[6.25,2.9,3.8],[14.15,3.9,11.5],0);
  k('keywell_dark_bed',[6.8,3.9,4.35],[13.6,4,10.95],7);
  k('panel_left_bevel',[6.25,3.9,3.8],[6.8,4.13,11.5],2);
  k('panel_right_bevel',[13.6,3.9,3.8],[14.15,4.13,11.5],2);
  k('panel_front_bevel',[6.8,3.9,3.8],[13.6,4.13,4.35],1);
  k('panel_back_bevel',[6.8,3.9,10.95],[13.6,4.13,11.5],1);
  for(let row=0;row<5;row++) for(let col=0;col<4;col++) {
    const x=7.02+col*1.14, z=4.65+row*1.2;
    k('key_'+row+'_'+col+'_rim',[x,4,z],[x+.97,4.12,z+.95],3);
    k('key_'+row+'_'+col+'_cap',[x+.07,4.12,z+.06],[x+.9,4.25,z+.88],row===4?4:5);
  }
  for(let row=0;row<5;row++) {
    const z=4.65+row*1.2;
    k('function_'+row+'_rim',[11.78,4,z],[13.26,4.12,z+.95],3);
    k('function_'+row+'_cap',[11.85,4.12,z+.06],[13.19,4.25,z+.88],row===0?6:4);
  }
  const l=(n,f,t,m=0)=>box('covered_service_module',n,f,t,m);
  l('left_housing',[1.65,3.25,3.1],[6,4.6,12.9],0);
  l('front_lower_cover',[1.85,3.25,2.85],[5.85,4.08,4.7],1);
  l('front_cover_top',[1.95,4.08,3.05],[5.75,4.22,4.65],2);
  l('service_recess',[1.85,4.6,4.75],[5.8,4.65,10.75],3);
  l('service_lid',[1.95,4.65,4.95],[5.7,4.8,10.6],1);
  l('sealed_rear_module',[1.95,4.6,10.65],[5.7,6.05,12.9],0);
  l('sealed_module_top',[2.08,6.05,10.8],[5.57,6.2,12.75],2);
  l('module_front_return',[2.08,5.85,10.52],[5.57,6.05,10.8],1);
  l('lid_front_edge',[2,4.8,4.95],[5.65,4.86,5.12],2);
  l('lid_left_edge',[1.87,4.62,4.95],[1.95,4.78,10.6],2);
  l('lid_right_edge',[5.7,4.62,4.95],[5.78,4.78,10.6],2);
  // Stepped cheeks close the wedge under the inclined operating panel.
  for(let i=0;i<8;i++) {
    const z=4+i*1.08, h=3.6+i*.45;
    for(const [side,x] of [['left',6.12],['right',13.98]]) box('side_shell_'+side,side+'_wedge_step_'+i,[x,3.25,z],[x+.3,h,z+1.08],0);
  }
  box('side_shell_right','rear_upper_housing',[6,3.25,12.1],[14.25,6.8,13.2],0);
  box('side_shell_right','rear_deck_trim',[5.95,6.8,12.05],[14.3,7,13.25],2);
  const m=(n,f,t,mat=0)=>box('display_mount',n,f,t,mat);
  m('pedestal_base',[8.7,7,11.7],[11.4,7.28,13],1);
  m('pedestal_stem',[9.35,7.28,11.94],[10.75,8.85,12.7],0);
  m('stem_front_inset',[9.55,7.38,11.91],[10.55,8.75,11.94],3);
  m('stem_collar',[9.1,8.62,11.82],[11,8.92,12.82],1);
  const s=(n,f,t,mat=0)=>box('display_screen',n,f,t,mat);
  s('display_rear_case',[6.75,8.6,12],[13.35,11.85,13],0);
  s('rear_inset_cover',[7.05,8.85,13],[13.05,11.6,13.08],3);
  s('bezel_left',[6.7,8.65,11.67],[7.2,11.8,12],1);
  s('bezel_right',[12.9,8.65,11.67],[13.4,11.8,12],1);
  s('bezel_upper',[7.2,11.32,11.67],[12.9,11.8,12],2);
  s('bezel_lower',[7.2,8.65,11.67],[12.9,9.1,12],1);
  s('screen_gasket',[7.2,9.1,11.79],[12.9,11.32,12],7);
  s('screen_glass',[7.42,9.3,11.765],[12.68,11.12,11.79],8);
  s('screen_upper_reflection',[7.47,10.98,11.758],[12.63,11.09,11.765],9);
  for(let i=0;i<5;i++) s('subtle_display_segment_'+i,[9.05+i*.5,9.83,11.747],[9.45+i*.5,10.12,11.758],i%2?10:11);
  s('case_top_edge',[6.85,11.85,12.04],[13.25,11.93,12.9],2);
  const r=(n,f,t,mat=0)=>box('rear_details',n,f,t,mat);
  for(let i=0;i<3;i++) {
    r('right_base_vent_'+i,[15,1.05+i*.42,9.7],[15.025,1.23+i*.42,12.6],7);
    r('left_base_vent_'+i,[.975,1.05+i*.42,9.7],[1,1.23+i*.42,12.6],7);
    r('rear_base_vent_'+i,[2.2,1.05+i*.42,14],[4.7,1.23+i*.42,14.025],7);
  }
  for(const [x,name] of [[11,'network'],[12.7,'power']]) {
    r(name+'_recess',[x,1.12,14],[x+1.05,2.3,14.04],7);
    r(name+'_rim_top',[x+.12,2.05,14.04],[x+.93,2.2,14.075],4);
    r(name+'_rim_bottom',[x+.12,1.22,14.04],[x+.93,1.37,14.075],3);
    r(name+'_rim_left',[x+.12,1.37,14.04],[x+.26,2.05,14.075],3);
    r(name+'_rim_right',[x+.79,1.37,14.04],[x+.93,2.05,14.075],4);
  }
  for (const c of Cube.all) {
    if (c.name.includes('_wedge_step_')) {
      c.to[1] = 3.38 + (c.from[2]-4)*Math.tan(Math.PI/8);
    }
    const oldX=c.from[0]; c.from[0]=16-c.to[0]; c.to[0]=16-oldX;
    c.origin[0]=16-c.origin[0];
  }
  Canvas.updateAll();
  return {cubes:Cube.all.length,texture:tex.uuid,groups:Object.keys(groups),atlas:canvas.toDataURL()};
})();
