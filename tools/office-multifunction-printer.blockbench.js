(() => {
  newProject(Formats.java_block);
  Project.name = 'office_multifunction_printer';
  Project.texture_width = 128;
  Project.texture_height = 128;
  Project.ambientocclusion = false;

  const canvas = document.createElement('canvas');
  canvas.width = canvas.height = 128;
  const ctx = canvas.getContext('2d');
  const colors = [
    '#b8b7b0','#c9c8c1','#a4a49e','#8f9190',
    '#34393d','#24292d','#4c5358','#151a1e',
    '#7890a5','#4f667b','#657b8f','#bec9d0',
    '#717578','#555b5f','#d8d7cf','#969994'
  ];
  colors.forEach((c, i) => {
    const x = (i % 4) * 32, y = Math.floor(i / 4) * 32;
    const grad = ctx.createLinearGradient(x, y, x + 32, y + 32);
    grad.addColorStop(0, c);
    grad.addColorStop(1, colors[(i + 1) % colors.length]);
    ctx.fillStyle = grad;
    ctx.fillRect(x, y, 32, 32);
    ctx.fillStyle = 'rgba(255,255,255,0.035)';
    ctx.fillRect(x, y, 32, 2);
    ctx.fillStyle = 'rgba(0,0,0,0.045)';
    ctx.fillRect(x, y + 30, 32, 2);
  });
  const tex = new Texture({name:'office_multifunction_printer.png', id:'0'})
    .fromDataURL(canvas.toDataURL()).add(false);

  const root = new Group({name:'office_multifunction_printer_root', origin:[8,0,8]}).init();
  const group = (name, parent, origin=[8,0,8]) => new Group({name, origin}).addTo(parent).init();
  const facesFor = (m) => {
    const x=(m%4)*32+2, y=Math.floor(m/4)*32+2;
    const faces={};
    for(const d of ['north','south','east','west','up','down']) faces[d]={uv:[x,y,x+28,y+28],texture:tex.uuid};
    return faces;
  };
  const cube = (name, from, to, mat, parent, extra={}) => new Cube({
    name, from, to, faces:facesFor(mat), ...extra
  }).addTo(parent).init();
  const strip = (prefix, count, start, step, make) => {
    for(let i=0;i<count;i++) make(prefix+String(i+1).padStart(2,'0'), start+i*step, i);
  };

  const base = group('base', root);
  cube('base_plinth', [.7,.35,1.25], [15.3,2.05,14.8], 5, base);
  cube('base_front_band', [.7,.35,1.05], [15.3,1.25,1.25], 4, base);
  cube('base_left_band', [.55,.35,1.25], [.7,1.25,14.8], 4, base);
  cube('base_right_band', [15.3,.35,1.25], [15.45,1.25,14.8], 4, base);
  cube('base_rear_band', [.7,.35,14.8], [15.3,1.25,15.05], 4, base);
  [['front_left',1,1.2],['front_right',13.5,1.2],['rear_left',1,13.5],['rear_right',13.5,13.5]].forEach(([n,x,z])=>{
    cube('foot_'+n,[x,0,z],[x+1.5,.35,z+1.3],5,base);
    cube('foot_pad_'+n,[x+.15,0,z+.12],[x+1.35,.12,z+1.18],7,base);
  });
  strip('base_front_rib_', 8, 1.15, 1.78, (n,x)=>cube(n,[x,1.34,1.02],[x+1.2,1.58,1.18],6,base));

  const body = group('main_body', root);
  cube('body_core',[1.05,2.05,1.35],[14.95,16.15,14.65],0,body);
  cube('left_shell',[.7,2.05,1.35],[1.05,16.15,14.65],1,body);
  cube('right_shell',[14.95,2.05,1.35],[15.3,16.15,14.65],1,body);
  cube('rear_shell',[1.05,2.05,14.65],[14.95,16.15,15.0],2,body);
  cube('upper_body_deck',[.85,16.15,1.25],[15.15,17.1,14.75],1,body);
  cube('upper_body_front_trim',[.75,16.1,1.05],[15.25,16.65,1.25],4,body);
  cube('upper_body_side_trim_left',[.65,16.1,1.25],[.85,16.65,14.75],4,body);
  cube('upper_body_side_trim_right',[15.15,16.1,1.25],[15.35,16.65,14.75],4,body);
  cube('waist_band_front',[.7,9.1,1.05],[15.3,9.55,1.35],3,body);
  cube('waist_band_left',[.7,9.1,1.35],[1.0,9.55,14.65],3,body);
  cube('waist_band_right',[15.0,9.1,1.35],[15.3,9.55,14.65],3,body);
  cube('waist_band_rear',[1.0,9.1,14.65],[15.0,9.55,15.0],3,body);
  [['left',.62,1.45,.7],['right',15.3,1.45,15.38]].forEach(([side,x0,z0,x1])=>{});

  const sidePanels = group('side_panels', root);
  for(const side of ['left','right']){
    const g=group(side+'_side_panel',sidePanels);
    const x0=side==='left'?.58:15.3, x1=side==='left'?.7:15.42;
    cube(side+'_panel_upper',[x0,10.0,2.0],[x1,15.4,14.1],2,g);
    cube(side+'_panel_lower',[x0,2.45,2.0],[x1,8.7,14.1],0,g);
    cube(side+'_panel_front_stile',[x0-.04,2.3,1.45],[x1+.04,15.65,2.0],1,g);
    cube(side+'_panel_rear_stile',[x0-.04,2.3,14.1],[x1+.04,15.65,14.65],1,g);
    cube(side+'_panel_top_rail',[x0-.04,15.4,2.0],[x1+.04,15.65,14.1],1,g);
    cube(side+'_panel_mid_rail',[x0-.04,8.7,2.0],[x1+.04,10.0,14.1],3,g);
    const vz0=side==='left'?10.4:9.1;
    strip(side+'_vent_', 7, vz0, .55, (n,z)=>cube(n,[x0-.08,11.2,z],[x1+.08,11.55,z+.28],5,g));
    strip(side+'_lower_vent_', 5, 10.9, .56, (n,z)=>cube(n,[x0-.08,3.2,z],[x1+.08,3.52,z+.28],5,g));
    strip(side+'_panel_fastener_', 4, 0, 1, (n,_,i)=>{
      const y=i<2?3.0:15.0,z=i%2?13.65:2.35;
      cube(n,[x0-.11,y,z],[x1+.11,y+.3,z+.3],6,g);
    });
  }

  const rear = group('rear_panel', root);
  cube('rear_main_panel',[1.4,2.4,14.92],[14.6,15.7,15.12],2,rear);
  cube('rear_left_stile',[.95,2.15,14.85],[1.4,15.95,15.15],1,rear);
  cube('rear_right_stile',[14.6,2.15,14.85],[15.05,15.95,15.15],1,rear);
  cube('rear_top_rail',[1.4,15.55,14.88],[14.6,15.95,15.15],1,rear);
  cube('rear_mid_rail',[1.4,8.95,14.9],[14.6,9.35,15.15],3,rear);
  strip('rear_upper_vent_', 10, 2.0, .85, (n,x)=>cube(n,[x,12.3,15.10],[x+.5,12.62,15.24],5,rear));
  strip('rear_lower_vent_', 10, 2.0, .85, (n,x)=>cube(n,[x,4.0,15.10],[x+.5,4.32,15.24],5,rear));
  strip('rear_service_seam_', 6, 3.0, 1.72, (n,x)=>cube(n,[x,9.55,15.11],[x+.8,9.72,15.22],3,rear));

  const drawers = group('paper_drawers', root);
  function makeDrawer(index,y0){
    const g=group('paper_drawer_'+index,drawers,[8,y0+2.7,1.05]);
    const frame=group('drawer_'+index+'_frame',g);
    const face=group('drawer_'+index+'_face',g);
    const handle=group('drawer_'+index+'_handle',g);
    cube('drawer_'+index+'_recess',[1.35,y0,1.02],[14.65,y0+5.0,1.2],3,frame);
    cube('drawer_'+index+'_front',[1.15,y0+.2,.72],[14.85,y0+4.8,1.02],0,face);
    cube('drawer_'+index+'_top_fold',[1.15,y0+4.8,.72],[14.85,y0+5.02,1.05],1,face);
    cube('drawer_'+index+'_bottom_fold',[1.15,y0,.72],[14.85,y0+.2,1.05],2,face);
    cube('drawer_'+index+'_left_fold',[.98,y0,.72],[1.15,y0+5.02,1.05],1,face);
    cube('drawer_'+index+'_right_fold',[14.85,y0,.72],[15.02,y0+5.02,1.05],2,face);
    cube('drawer_'+index+'_handle_recess',[5.25,y0+2.6,.52],[10.75,y0+3.75,.72],5,handle);
    cube('drawer_'+index+'_handle_inner',[5.65,y0+2.88,.38],[10.35,y0+3.47,.52],7,handle);
    cube('drawer_'+index+'_handle_top',[5.25,y0+3.75,.35],[10.75,y0+3.94,.6],6,handle);
    cube('drawer_'+index+'_handle_bottom',[5.25,y0+2.42,.35],[10.75,y0+2.6,.6],4,handle);
    cube('drawer_'+index+'_handle_left',[5.05,y0+2.42,.35],[5.25,y0+3.94,.6],4,handle);
    cube('drawer_'+index+'_handle_right',[10.75,y0+2.42,.35],[10.95,y0+3.94,.6],4,handle);
    strip('drawer_'+index+'_paper_mark_',4,6.1,.9,(n,x)=>cube(n,[x,y0+1.35,.5],[x+.55,y0+1.5,.65],3,face));
    for(let i=0;i<4;i++){
      const x=i%2?14.55:1.15, yy=i<2?y0+.38:y0+4.55;
      cube('drawer_'+index+'_fastener_'+(i+1),[x,yy,.48],[x+.28,yy+.28,.7],6,face);
    }
  }
  makeDrawer(1,4.05);
  makeDrawer(2,9.15);

  const output = group('output_section', root);
  const bay=group('output_bay',output);
  cube('output_bay_back',[2.15,14.35,3.0],[13.85,16.05,10.9],7,bay);
  cube('output_bay_ceiling',[1.65,16.0,2.2],[14.35,16.35,11.3],4,bay);
  cube('output_bay_left_wall',[1.55,14.0,2.15],[2.15,16.2,11.4],1,bay);
  cube('output_bay_right_wall',[13.85,14.0,2.15],[14.45,16.2,11.4],1,bay);
  cube('output_bay_rear_shelf',[2.15,14.15,10.9],[13.85,14.55,11.4],5,bay);
  strip('output_upper_guide_',8,2.5,1.45,(n,x)=>cube(n,[x,15.65,2.0],[x+.85,15.92,2.28],6,bay));
  const tray=group('output_tray',output,[8,14.1,10.8]);
  cube('output_tray_base',[2.1,13.85,2.15],[13.9,14.22,10.95],5,tray);
  cube('output_tray_front_lip',[1.9,13.82,1.82],[14.1,14.55,2.15],4,tray);
  cube('output_tray_left_lip',[1.9,13.82,2.15],[2.15,14.48,10.9],6,tray);
  cube('output_tray_right_lip',[13.85,13.82,2.15],[14.1,14.48,10.9],6,tray);
  cube('output_tray_rear_lip',[2.15,13.82,10.9],[13.85,14.48,11.15],6,tray);
  strip('output_tray_rib_',10,2.55,1.13,(n,x)=>cube(n,[x,14.22,2.35],[x+.52,14.38,10.55],6,tray));
  strip('output_tray_front_notch_',5,4.1,1.55,(n,x)=>cube(n,[x,14.55,1.75],[x+.75,14.72,2.05],3,tray));

  const upper = group('upper_body', root);
  cube('upper_left_tower',[.95,16.6,2.0],[3.0,19.25,13.9],0,upper);
  cube('upper_right_tower',[13.0,16.6,2.0],[15.05,19.25,13.9],0,upper);
  cube('upper_rear_bridge',[3.0,16.6,11.7],[13.0,19.25,13.9],1,upper);
  cube('upper_front_bridge',[3.0,17.65,2.0],[13.0,19.25,4.0],1,upper);
  cube('upper_front_shadow',[3.0,16.6,2.05],[13.0,17.65,3.6],5,upper);
  cube('upper_left_cap',[.8,18.95,1.8],[3.15,19.35,14.1],1,upper);
  cube('upper_right_cap',[12.85,18.95,1.8],[15.2,19.35,14.1],1,upper);
  strip('upper_left_seam_',5,4.0,2.0,(n,z)=>cube(n,[.74,17.0,z],[.94,18.75,z+.22],2,upper));
  strip('upper_right_seam_',5,4.0,2.0,(n,z)=>cube(n,[15.06,17.0,z],[15.26,18.75,z+.22],2,upper));

  const scanner = group('scanner_assembly', root);
  const scannerBody=group('scanner_body',scanner);
  cube('scanner_lower_body',[.75,19.25,1.5],[15.25,20.35,14.5],0,scannerBody);
  cube('scanner_front_band',[.6,19.25,1.25],[15.4,19.9,1.5],4,scannerBody);
  cube('scanner_left_band',[.6,19.25,1.5],[.85,20.05,14.5],3,scannerBody);
  cube('scanner_right_band',[15.15,19.25,1.5],[15.4,20.05,14.5],3,scannerBody);
  cube('scanner_rear_band',[.85,19.25,14.5],[15.15,20.05,14.8],3,scannerBody);
  cube('scanner_glass',[2.0,20.35,2.6],[14.0,20.48,13.2],8,scannerBody);
  cube('scanner_glass_inner',[2.45,20.48,3.05],[13.55,20.52,12.75],9,scannerBody);
  strip('scanner_front_marker_',8,2.0,1.55,(n,x)=>cube(n,[x,20.34,1.8],[x+.85,20.5,2.12],3,scannerBody));
  for(let i=0;i<4;i++){
    const x=i%2?14.25:1.45,z=i<2?2.0:13.4;
    cube('scanner_fastener_'+(i+1),[x,20.34,z],[x+.28,20.56,z+.28],6,scannerBody);
  }
  const lid=group('scanner_lid',scanner,[8,20.45,14.0]);
  cube('scanner_lid_main',[1.0,20.52,1.7],[15.0,21.25,14.25],1,lid);
  cube('scanner_lid_inset',[1.55,21.25,2.2],[14.45,21.52,13.65],0,lid);
  cube('scanner_lid_front_edge',[.85,20.72,1.45],[15.15,21.45,1.7],3,lid);
  cube('scanner_lid_left_edge',[.85,20.72,1.7],[1.1,21.45,14.25],2,lid);
  cube('scanner_lid_right_edge',[14.9,20.72,1.7],[15.15,21.45,14.25],2,lid);
  cube('scanner_lid_rear_edge',[1.1,20.72,14.25],[14.9,21.45,14.55],2,lid);
  const hinges=group('scanner_hinges',scanner,[8,20.45,14.0]);
  cube('scanner_hinge_left',[2.0,20.2,13.65],[4.1,20.82,14.75],4,hinges);
  cube('scanner_hinge_right',[11.9,20.2,13.65],[14.0,20.82,14.75],4,hinges);
  cube('scanner_hinge_left_pin',[2.35,20.32,13.4],[3.75,20.7,15.0],6,hinges);
  cube('scanner_hinge_right_pin',[12.25,20.32,13.4],[13.65,20.7,15.0],6,hinges);
  const adf=group('adf_detail',lid,[8,21.0,9.2]);
  cube('adf_body',[1.4,21.52,3.4],[9.55,23.35,13.75],0,adf);
  cube('adf_top_plate',[1.15,23.35,3.15],[9.8,23.75,14.0],1,adf);
  cube('adf_front_bevel',[1.15,22.0,2.85],[9.8,23.3,3.4],2,adf);
  cube('adf_left_bevel',[.95,21.9,3.15],[1.4,23.55,14.0],2,adf);
  cube('adf_rear_bevel',[1.4,21.9,13.75],[9.55,23.55,14.2],2,adf);
  cube('adf_feed_slot',[2.0,23.75,4.0],[9.2,23.98,12.9],5,adf);
  cube('adf_feed_floor',[2.45,23.98,4.45],[8.75,24.12,12.45],7,adf);
  strip('adf_feed_rib_',7,2.6,.9,(n,x)=>cube(n,[x,24.12,4.65],[x+.4,24.28,12.25],6,adf));
  cube('adf_output_ramp',[9.8,21.75,5.4],[14.15,22.4,13.45],4,adf);
  cube('adf_output_ramp_top',[9.55,22.4,5.1],[14.35,22.75,13.65],5,adf);
  cube('adf_output_lip',[13.95,21.55,5.25],[14.55,22.7,13.8],6,adf);
  strip('adf_side_vent_',6,4.1,1.35,(n,z)=>cube(n,[.78,22.15,z],[1.02,22.45,z+.72],5,adf));

  const panel = group('control_panel_assembly', root,[8,18.0,1.15]);
  const panelBody=group('control_panel_body',panel,[8,18.0,1.15]);
  cube('control_panel_back',[3.3,17.2,-.15],[14.8,20.0,1.7],4,panelBody);
  cube('control_panel_face',[3.0,16.8,-.55],[15.1,19.55,-.15],5,panelBody);
  cube('control_panel_top',[3.0,19.55,-.55],[15.1,19.9,-.05],6,panelBody);
  cube('control_panel_bottom',[3.0,16.48,-.5],[15.1,16.8,-.05],7,panelBody);
  cube('control_panel_left',[2.7,16.48,-.5],[3.0,19.9,-.05],6,panelBody);
  cube('control_panel_right',[15.1,16.48,-.5],[15.4,19.9,-.05],6,panelBody);
  cube('control_panel_support_left',[3.45,19.6,.0],[4.2,20.1,2.2],4,panelBody);
  cube('control_panel_support_right',[13.7,19.6,.0],[14.45,20.1,2.2],4,panelBody);
  const screen=group('control_panel_screen',panel);
  cube('screen_recess',[3.7,17.18,-.72],[9.6,19.15,-.5],7,screen);
  cube('screen_glass',[4.05,17.48,-.78],[9.25,18.85,-.72],8,screen);
  cube('screen_highlight',[4.2,18.55,-.80],[7.1,18.72,-.78],11,screen);
  const buttons=group('control_panel_buttons',panel);
  for(let row=0;row<3;row++) for(let col=0;col<3;col++){
    const x=10.25+col*1.0,y=17.15+row*.72;
    cube('panel_button_'+(row*3+col+1),[x,y,-.78],[x+.55,y+.42,-.55],col===2&&row===0?12:3,buttons);
  }
  strip('panel_function_button_',4,13.35,.52,(n,x,i)=>cube(n,[x,17.18,-.80],[x+.34,17.58,-.55],i===0?10:i===1?12:i===2?13:3,buttons));
  cube('panel_start_button',[13.35,18.3,-.82],[14.05,18.95,-.55],12,buttons);
  cube('panel_stop_button',[14.25,18.3,-.82],[14.95,18.95,-.55],13,buttons);
  cube('status_indicator_blue',[12.8,19.12,-.81],[13.15,19.35,-.55],8,buttons);
  cube('status_indicator_green',[13.35,19.12,-.81],[13.7,19.35,-.55],12,buttons);
  cube('status_indicator_orange',[13.9,19.12,-.81],[14.25,19.35,-.55],13,buttons);

  const details=group('body_details',root);
  strip('front_left_vent_',6,10.1,.66,(n,y)=>cube(n,[1.18,y,.92],[2.85,y+.3,1.08],5,details));
  strip('front_right_vent_',6,10.1,.66,(n,y)=>cube(n,[13.15,y,.92],[14.82,y+.3,1.08],5,details));
  for(let i=0;i<8;i++){
    const x=i%2?14.55:1.18,y=i<2?2.55:i<4?8.55:i<6?10.0:15.25;
    cube('front_fastener_'+(i+1),[x,y,.79],[x+.27,y+.27,.95],6,details);
  }
  cube('power_panel',[13.2,2.35,14.98],[14.45,3.7,15.22],5,details);
  cube('power_button',[13.58,2.75,15.22],[14.05,3.22,15.35],12,details);
  cube('service_panel',[1.7,5.0,15.10],[5.4,7.2,15.28],3,details);
  strip('service_panel_louver_',4,2.1,.67,(n,x)=>cube(n,[x,5.65,15.28],[x+.42,6.55,15.4],5,details));

  Project.save_path='D:/Minecraft Modding/Apocalypse First Light/src/main/blockbench/office_multifunction_printer.bbmodel';
  Canvas.updateAll();
  return {
    project:Project.name,
    cubes:Cube.all.length,
    groups:Group.all.length,
    texture:tex.name,
    size:{x:14.9,y:24.28,z:15.9},
    bounds:{min:[.55,0,-.82],max:[15.45,24.28,15.24]}
  };
})()
