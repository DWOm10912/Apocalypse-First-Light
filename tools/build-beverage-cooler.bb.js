(function () {
  if (Cube.all.length || Texture.all.length) throw new Error('Requires an empty generic project');
  Project.name = 'afl_beverage_cooler';
  Project.texture_width = Project.texture_height = 128;
  const atlas = document.createElement('canvas');
  atlas.width = atlas.height = 128;
  const ctx = atlas.getContext('2d');
  const colors = ['#262c31','#343c43','#444e56','#1a2127','#53616b','#aebbc2','#c2cdd1','#899aa4','#d8e3e6','#314757','#467894','#8fb5c7','#10191f','#64737d','rgba(85,149,179,0.16)','rgba(211,237,245,0.23)'];
  colors.forEach((color,i) => {
    const x=i%4*32,y=Math.floor(i/4)*32;
    ctx.fillStyle=color; ctx.fillRect(x,y,32,32);
    if(i<14) {
      ctx.fillStyle='rgba(255,255,255,0.035)';ctx.fillRect(x,y,32,1);
      ctx.fillStyle='rgba(0,0,0,0.045)';ctx.fillRect(x,y+31,32,1);
    }
  });
  const grad=ctx.createLinearGradient(64,0,96,0);
  grad.addColorStop(0,'#335d78');grad.addColorStop(.5,'#80adc2');grad.addColorStop(1,'#335d78');
  ctx.fillStyle=grad;ctx.fillRect(64,64,32,32);
  const tex=new Texture({name:'afl_beverage_cooler.png',render_mode:'default'}).fromDataURL(atlas.toDataURL()).add(false);
  const root=new Group({name:'beverage_cooler_root',origin:[16,0,8]}).init();
  const groups={};
  function group(name,parent=root,origin=[16,0,8]) { return groups[name]=new Group({name,origin}).addTo(parent).init(); }
  ['cabinet_body','top_sign_box','bottom_vent_base','interior_shell'].forEach(n=>group(n));
  for(let i=1;i<=5;i++){group('shelf_0'+i);group('display_row_0'+i,root,[16,4.16+(i-1)*4.85,5.5]);}
  group('left_door',root,[1.13,16,.505]);group('right_door',root,[30.87,16,.505]);
  for(const s of ['left','right']) for(const n of ['door_frame','door_glass','handle']) group(s+'_'+n,groups[s+'_door'],groups[s+'_door'].origin.slice());
  function box(g,n,f,t,m=0) {
    const c=new Cube({name:n,from:f.map(v=>+v.toFixed(4)),to:t.map(v=>+v.toFixed(4)),autouv:0,box_uv:false}).addTo(groups[g]).init();
    for(const face of Object.values(c.faces)) {face.texture=tex.uuid;const u=m%4*32,v=Math.floor(m/4)*32;face.uv=[u+1,v+1,u+31,v+31];}
    return c;
  }
  const b=(n,f,t,m=0)=>box('cabinet_body',n,f,t,m);
  b('left_insulated_wall',[0,.4,1.2],[1.05,31.7,16]);
  b('right_insulated_wall',[30.95,.4,1.2],[32,31.7,16]);
  b('rear_insulated_wall',[1.05,.4,14.8],[30.95,31.7,15.92]);
  b('rear_service_panel',[2,1,15.92],[30,8,15.98],1);
  b('rear_vent_recess',[3,2,15.98],[12,6,15.99],12);
  for(let i=0;i<6;i++) b('rear_vent_louver_'+i,[3.2,2.25+i*.58,15.99],[11.8,2.5+i*.58,16],2);
  for(const x of [0,30.95]) b('roof_front_corner_'+x,[x,31.7,1.2],[x+1.05,32,3.3],1);
  b('roof_cap',[0,31.7,3.3],[32,32,16],1);
  b('left_front_post',[0,.4,.65],[1.05,32,1.2],1);
  b('right_front_post',[30.95,.4,.65],[32,32,1.2],1);
  for(const x of [.28,31.4]) b('front_post_highlight_'+x,[x,.65,.60],[x+.25,31.72,.65],2);
  for(const x of [.65,29.75]) for(const z of [1.7,13.6]) {
    b('rubber_foot_'+x+'_'+z,[x,0,z],[x+1.6,.4,z+1.65],12);
  }
  const v=(n,f,t,m=0)=>box('bottom_vent_base',n,f,t,m);
  v('compressor_enclosure',[1.05,.4,1.2],[30.95,3.5,14.8],0);
  v('grille_recess',[1.3,.65,.94],[30.7,3.3,1.18],12);
  v('grille_frame_lower',[1.05,.4,.65],[30.95,.7,1.2],1);
  v('grille_frame_upper',[1.05,3.18,.65],[30.95,3.5,1.2],1);
  v('grille_frame_left',[1.05,.7,.65],[1.45,3.18,1.2],1);
  v('grille_frame_right',[30.55,.7,.65],[30.95,3.18,1.2],1);
  for(let i=0;i<7;i++) v('vent_louver_'+i,[1.55,.86+i*.31,.69],[30.45,1.01+i*.31,.94],2);
  v('thermostat_bezel',[27.75,2.64,.48],[30.15,3.15,.69],4);
  v('thermostat_dark_display',[28,2.74,.46],[29.2,3.03,.48],9);
  v('thermostat_status_square',[29.55,2.78,.455],[29.82,2.99,.48],11);
  const h=(n,f,t,m=0)=>box('top_sign_box',n,f,t,m);
  h('lightbox_housing',[1.05,28.9,1.2],[30.95,32,3.3],0);
  h('header_top',[1.05,31.35,.65],[30.95,32,1.2],1);
  h('header_bottom',[1.05,28.9,.65],[30.95,29.55,1.2],1);
  h('header_left',[1.05,29.55,.65],[2,31.35,1.2],1);
  h('header_right',[30,29.55,.65],[30.95,31.35,1.2],1);
  h('blank_blue_lightbox',[2,29.55,1.1],[30,31.35,1.2],10);
  h('header_lower_edge',[1.5,29.33,.59],[30.5,29.43,.65],4);
  h('header_upper_edge',[1.5,31.55,.59],[30.5,31.65,.65],2);
  h('lightbox_ceiling_return',[1.05,28.9,3.3],[30.95,29.2,14.8],1);
  const ins=(n,f,t,m=5)=>box('interior_shell',n,f,t,m);
  ins('back_liner',[1.3,3.5,14.35],[30.7,28.9,14.8],5);
  ins('left_liner',[1.05,3.5,1.2],[1.3,28.9,14.8],5);
  ins('right_liner',[30.7,3.5,1.2],[30.95,28.9,14.8],5);
  ins('ceiling_liner',[1.3,28.6,1.2],[30.7,28.9,14.35],6);
  ins('floor_liner',[1.3,3.5,1.2],[30.7,3.65,14.35],7);
  ins('rear_central_air_duct',[15.15,4,14.14],[16.85,28.3,14.35],7);
  for(let i=0;i<8;i++) ins('rear_air_slot_'+i,[15.42,25.1+i*.3,14.11],[16.58,25.24+i*.3,14.14],4);
  for(const x of [1.3,30.28]) {
    ins('vertical_led_housing_'+x,[x,4.05,2.05],[x+.42,28.35,2.65],7);
    ins('vertical_led_diffuser_'+x,[x+.07,4.2,2.01],[x+.35,28.2,2.05],8);
  }
  ins('ceiling_light_housing',[7.5,28.38,4],[24.5,28.6,6.1],7);
  ins('ceiling_light_diffuser',[8,28.31,4.3],[24,28.38,5.8],8);
  for(const x of [2,29.7]) {
    ins('shelf_mount_rail_'+x,[x,3.7,14.06],[x+.3,28.4,14.35],7);
    for(let i=0;i<18;i++) ins('rail_slot_'+x+'_'+i,[x+.07,4.15+i*1.3,14.03],[x+.23,4.55+i*1.3,14.06],4);
  }
  for(let tier=1;tier<=5;tier++) {
    const g='shelf_0'+tier,y=3.8+(tier-1)*4.85;
    const s=(n,f,t,m=6)=>box(g,n,f,t,m);
    s('tier_'+tier+'_front_rail',[1.5,y,3],[30.5,y+.36,3.6],6);
    s('tier_'+tier+'_rear_rail',[1.5,y,12.5],[30.5,y+.36,13.1],7);
    s('tier_'+tier+'_left_rail',[1.5,y,3.6],[1.85,y+.36,12.5],6);
    s('tier_'+tier+'_right_rail',[30.15,y,3.6],[30.5,y+.36,12.5],6);
    for(let i=0;i<44;i++) s('tier_'+tier+'_wire_'+String(i+1).padStart(2,'0'),[2+i*.645,y+.24,3.6],[2.12+i*.645,y+.36,12.5],6);
    for(const z of [5.2,8.1,10.9]) s('tier_'+tier+'_cross_support_'+z,[1.85,y+.1,z],[30.15,y+.24,z+.18],7);
    for(const x of [1.3,30]) for(const z of [5,11.8]) s('tier_'+tier+'_bracket_'+x+'_'+z,[x,Math.max(y-.28,3.65),z],[x+.7,y,z+.6],7);
    s('tier_'+tier+'_label_channel',[1.8,y-.05,2.91],[30.2,y+.22,3],7);
    s('tier_'+tier+'_label_insert',[2.1,y+.01,2.89],[29.9,y+.17,2.91],6);
    for(const x of [2.2,28.65]) s('tier_'+tier+'_rail_endcap_'+x,[x,y+.22,2.96],[x+1.15,y+.34,3],8);
  }
  for(const side of ['left','right']) {
    const mirror=side==='right',f=mirror?16.06:1.08,t=mirror?30.92:15.94;
    const frame=(n,a,b,m=1)=>box(side+'_door_frame',side+'_'+n,a,b,m);
    frame('hinge_stile',[f,3.58,.62],[f+.64,28.75,1.1]);
    frame('meeting_stile',[t-.64,3.58,.62],[t,28.75,1.1]);
    frame('bottom_rail',[f+.64,3.58,.62],[t-.64,4.18,1.1]);
    frame('top_rail',[f+.64,28.15,.62],[t-.64,28.75,1.1]);
    frame('outer_edge',[mirror?t-.1:f+.02,3.7,.57],[mirror?t-.02:f+.1,28.62,.62],4);
    frame('lower_edge',[f+.78,3.65,.57],[t-.78,3.74,.62],2);
    frame('upper_edge',[f+.78,28.56,.57],[t-.78,28.65,.62],2);
    frame('inner_gasket_left',[f+.64,4.18,.8],[f+.78,28.15,1.08],3);
    frame('inner_gasket_right',[t-.78,4.18,.8],[t-.64,28.15,1.08],3);
    frame('inner_gasket_bottom',[f+.78,4.18,.8],[t-.78,4.3,1.08],3);
    frame('inner_gasket_top',[f+.78,28.03,.8],[t-.78,28.15,1.08],3);
    box(side+'_door_glass',side+'_transparent_pane',[f+.78,4.3,.94],[t-.78,28.03,1.02],14);
    box(side+'_door_glass',side+'_glass_upper_glint',[f+1.1,27.65,.932],[t-1.1,27.81,.94],15);
    box(side+'_door_glass',side+'_glass_side_glint',[f+1.1,4.8,.932],[f+1.2,27.4,.94],15);
    const hx=mirror?f+.11:t-.49;
    for(const y of [13.1,19.1]) {
      box(side+'_handle',side+'_handle_mount_'+y,[hx-.1,y,.25],[hx+.48,y+.45,.62],2);
      box(side+'_handle',side+'_handle_socket_'+y,[hx-.06,y+.03,.18],[hx+.44,y+.42,.25],4);
    }
    box(side+'_handle',side+'_pull_grip',[hx,13.15,.03],[hx+.38,19.5,.18],1);
    box(side+'_handle',side+'_grip_highlight',[hx+.06,13.35,0],[hx+.13,19.3,.03],4);
    for(const y of [4.7,26.7]) {
      const xx=mirror?t-.18:f-.08;
      frame('hinge_barrel_'+y,[xx,y,.39],[xx+.26,y+1.05,.64],2);
      frame('hinge_top_'+y,[xx+.02,y+1.05,.41],[xx+.24,y+1.15,.60],4);
      frame('hinge_bottom_'+y,[xx+.02,y-.1,.41],[xx+.24,y,.60],4);
    }
  }
  Canvas.updateAll();
  return {cubes:Cube.all.length,groups:Group.all.length,texture:tex.uuid,doorPivots:[groups.left_door.origin,groups.right_door.origin]};
})();
