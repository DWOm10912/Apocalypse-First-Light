(() => {
  newProject(Formats.free || Formats.java_block);
  Project.name='office_multifunction_printer';
  Project.texture_width=Project.texture_height=128;
  Project.ambientocclusion=false;
  const cv=document.createElement('canvas');cv.width=cv.height=128;
  const ct=cv.getContext('2d');
  const palette=['#bcbcb6','#cfcec7','#a8aaa5','#e0dfd7','#393e42','#252a2e','#535b60','#171d22','#344956','#536b7b','#93a2ad','#7a8284','#66826e','#9d8060','#727a7e','#999e9c'];
  palette.forEach((s,i)=>{ct.fillStyle=s;ct.fillRect(i%4*32,Math.floor(i/4)*32,32,32);ct.fillStyle='rgba(255,255,255,0.018)';ct.fillRect(i%4*32,Math.floor(i/4)*32,32,8);});
  const tex=new Texture({name:'office_multifunction_printer.png',id:'0'}).fromDataURL(cv.toDataURL()).add(false);
  const root=new Group({name:'office_multifunction_printer_root',origin:[8,0,8]}).init();
  const G=(name,p=root,origin=[8,0,8])=>new Group({name,origin}).addTo(p).init();
  const C=(name,a,b,m,g,extra={})=>{
    const u=m%4*32+3,v=Math.floor(m/4)*32+3,faces={};
    for(const d of ['north','south','west','east','up','down'])faces[d]={uv:[u,v,u+26,v+26],texture:tex.uuid};
    return new Cube({name,from:a,to:b,faces,...extra}).addTo(g).init();
  };
  const base=G('base');
  C('plinth',[1,.4,2],[15,1.5,14.6],4,base);
  C('plinth_front_fold',[1,.4,1.7],[15,1.25,2],5,base);
  C('plinth_top_reveal',[1,1.25,1.7],[15,1.5,2],6,base);
  for(const [n,x,z] of [['fl',1.35,2.05],['fr',13.25,2.05],['bl',1.35,12.65],['br',13.25,12.65]]){
    C('foot_'+n,[x,0,z],[x+1.4,.4,z+1.5],5,base);
    C('foot_front_'+n,[x,.12,z-.1],[x+1.4,.35,z],4,base);
  }
  C('lower_chassis',[1.55,1.5,2.1],[14.45,1.8,14.15],2,base);
  const side=G('side_panels');
  for(const [name,x0,x1] of [['left',1,1.65],['right',14.35,15]]){
    const g=G(name+'_shell',side);
    C(name+'_lower_sheet',[x0,1.5,2.2],[x1,10.55,14.1],0,g);
    C(name+'_upper_sheet',[x0,10.7,2.2],[x1,18.65,14.1],0,g);
    C(name+'_front_lower_column',[x0,1.5,1.8],[x1,10.55,2.2],1,g);
    C(name+'_front_upper_column',[x0,10.7,1.8],[x1,16.8,2.2],1,g);
    C(name+'_rear_column',[x0,1.5,14.1],[x1,18.65,14.55],1,g);
    C(name+'_waist_join',[x0,10.55,2.2],[x1,10.7,14.1],5,g);
    C(name+'_lower_edge',[x0,1.5,2.2],[x1,1.72,14.1],2,g);
    const out=name==='left'?x0-.08:x1, end=out+.08;
    for(const [part,y0,y1,z0,z1] of [['service',3.1,9.45,3,13.2],['upper',11.7,17.6,3,13.2]]){
      C(name+'_'+part+'_cover',[out,y0,z0],[end,y1,z1],0,g);
      C(name+'_'+part+'_upper_seam',[out-.01,y1,z0],[end+.01,y1+.08,z1],2,g);
      C(name+'_'+part+'_rear_seam',[out-.01,y0,z1],[end+.01,y1,z1+.08],2,g);
      C(name+'_'+part+'_front_fold',[out-.02,y0,z0-.12],[end+.02,y1,z0],1,g);
    }
    const vent=G(name+'_vent_grilles',g);
    for(const [vn,y0,z0,w,rows] of [['low',3.65,10.35,2.0,5],['high',14.5,3.55,1.6,5]]){
      C(name+'_'+vn+'_vent_recess',[out-.04,y0-.16,z0-.17],[end+.04,y0+rows*.34,z0+w+.17],2,vent);
      for(let i=0;i<rows;i++){
        C(name+'_'+vn+'_slot_'+i,[out-.08,y0+i*.34,z0],[end+.08,y0+i*.34+.17,z0+w],5,vent);
        C(name+'_'+vn+'_louver_'+i,[out-.1,y0+i*.34+.17,z0],[end+.1,y0+i*.34+.23,z0+w],1,vent);
      }
    }
    for(let i=0;i<4;i++){
      const yy=i<2?3.45:17.15,zz=i%2?12.9:3.4;
      C(name+'_panel_captive_'+i,[out-.07,yy,zz],[end+.07,yy+.16,zz+.16],14,g);
    }
  }
  const rear=G('rear_panel');
  C('rear_lower_cover',[1.65,1.5,14.15],[14.35,10.55,14.55],2,rear);
  C('rear_upper_cover',[1.65,10.7,14.15],[14.35,18.65,14.55],0,rear);
  C('rear_join_recess',[1.65,10.55,14.15],[14.35,10.7,14.5],5,rear);
  C('rear_service_hatch',[3,3.2,14.55],[12.95,9.5,14.67],0,rear);
  C('rear_service_top_seam',[3,9.5,14.55],[12.95,9.6,14.68],2,rear);
  C('rear_service_right_seam',[12.95,3.2,14.55],[13.05,9.6,14.68],2,rear);
  for(let i=0;i<7;i++){
    C('rear_exhaust_slot_'+i,[9.4,12.45+i*.4,14.55],[12.8,12.65+i*.4,14.69],5,rear);
    C('rear_exhaust_lip_'+i,[9.4,12.65+i*.4,14.55],[12.8,12.73+i*.4,14.72],1,rear);
  }
  for(let i=0;i<4;i++)C('rear_cover_screw_'+i,[i%2?12.45:3.35,i<2?3.5:9.15,14.67],[i%2?12.65:3.55,i<2?3.7:9.35,14.74],14,rear);
  const drawers=[];
  for(let j=0;j<2;j++){
    const y=1.83+j*4.35, g=G('paper_drawer_'+(j+1),root,[8,y,8]);drawers.push(g);
    const f=G('drawer_front',g),h=G('drawer_handle',g),basket=G('drawer_frame',g);
    C('drawer_'+j+'_front_lower',[1.83,y,1.8],[14.17,y+2.0,2.17],0,f);
    C('drawer_'+j+'_front_upper',[1.83,y+2.87,1.8],[14.17,y+4.15,2.17],0,f);
    C('drawer_'+j+'_front_left',[1.83,y+2,1.8],[6.1,y+2.87,2.17],0,f);
    C('drawer_'+j+'_front_right',[9.9,y+2,1.8],[14.17,y+2.87,2.17],0,f);
    C('drawer_'+j+'_edge_top',[1.75,y+4.15,1.8],[14.25,y+4.28,2.25],1,f);
    C('drawer_'+j+'_edge_bottom',[1.75,y-.1,1.8],[14.25,y,2.25],2,f);
    C('drawer_'+j+'_edge_left',[1.75,y,1.8],[1.83,y+4.15,2.25],1,f);
    C('drawer_'+j+'_edge_right',[14.17,y,1.8],[14.25,y+4.15,2.25],2,f);
    C('drawer_'+j+'_grip_back',[6.1,y+2,2.5],[9.9,y+2.87,2.65],5,h);
    C('drawer_'+j+'_grip_upper',[6.1,y+2.72,1.87],[9.9,y+2.87,2.5],4,h);
    C('drawer_'+j+'_grip_lower',[6.1,y+2,1.8],[9.9,y+2.13,2.5],1,h);
    C('drawer_'+j+'_grip_left',[6.1,y+2.13,1.9],[6.23,y+2.72,2.5],2,h);
    C('drawer_'+j+'_grip_right',[9.77,y+2.13,1.9],[9.9,y+2.72,2.5],2,h);
    C('drawer_'+j+'_bottom',[1.95,y+.12,2.25],[14.05,y+.32,13.3],2,basket);
    C('drawer_'+j+'_basket_left',[1.95,y+.32,2.25],[2.15,y+3.25,13.3],2,basket);
    C('drawer_'+j+'_basket_right',[13.85,y+.32,2.25],[14.05,y+3.25,13.3],2,basket);
    C('drawer_'+j+'_basket_back',[2.15,y+.32,13.1],[13.85,y+3.25,13.3],2,basket);
    for(let k=0;k<2;k++){
      const x=k?13.7:2.15;
      C('drawer_'+j+'_runner_'+k,[x,y+.4,2.5],[x+.15,y+.7,13],6,basket);
      C('drawer_'+j+'_upper_return_'+k,[k?13.65:2.15,y+3.25,2.5],[k?13.85:2.35,y+3.4,12.9],1,basket);
    }
    C('drawer_'+j+'_paper_guide',[3,y+.32,3.2],[3.25,y+1.15,11.75],6,basket);
    C('drawer_'+j+'_paper_stop',[3.25,y+.32,11.5],[12.2,y+1.15,11.75],6,basket);
  }
  const output=G('output_section');const bay=G('output_bay',output),inner=G('output_inner_detail',output),tray=G('output_tray',output,[8,10.65,11.9]);
  C('bay_floor',[1.65,10.47,2.2],[14.35,10.8,14.15],2,bay);
  C('bay_back_wall',[1.65,10.8,11.8],[14.35,16.65,14.15],5,bay);
  C('bay_ceiling',[1.65,16.45,2.2],[14.35,16.8,11.8],5,bay);
  C('bay_left_inner',[1.65,10.8,2.25],[2.1,16.45,11.8],4,bay);
  C('bay_right_inner',[13.9,10.8,2.25],[14.35,16.45,11.8],4,bay);
  C('output_feed_mouth',[3,14.55,11.65],[13,15.35,11.8],7,inner);
  C('output_feed_upper_lip',[3,15.35,11.4],[13,15.52,11.8],6,inner);
  C('output_feed_lower_lip',[3,14.4,11.25],[13,14.55,11.8],6,inner);
  for(let i=0;i<8;i++){
    C('feed_roller_'+i,[3.4+i*1.18,14.7,11.37],[4.08+i*1.18,15.1,11.65],4,inner);
    C('back_guide_'+i,[3.55+i*1.18,11.3,11.64],[3.78+i*1.18,14.3,11.8],6,inner);
  }
  C('tray_plate',[2.55,10.8,2.5],[13.45,11.15,11.5],4,tray);
  C('tray_front_return',[2.45,10.8,2.25],[13.55,11.5,2.5],4,tray);
  C('tray_front_edge',[2.45,11.5,2.25],[13.55,11.62,2.6],6,tray);
  C('tray_left_guide',[2.45,11.15,2.5],[2.68,11.5,11.5],6,tray);
  C('tray_right_guide',[13.32,11.15,2.5],[13.55,11.5,11.5],6,tray);
  for(let i=0;i<7;i++)C('tray_paper_rib_'+i,[3.1+i*1.5,11.15,2.85],[3.32+i*1.5,11.26,11.25],6,tray);
  const upper=G('upper_body');
  C('upper_main_bridge',[1,16.8,2.2],[15,19.0,14.55],0,upper);
  C('upper_front_bridge',[1.65,16.8,1.8],[14.35,17.18,2.2],1,upper);
  C('upper_left_corner',[1,16.8,1.8],[1.65,18.7,2.2],1,upper);
  C('upper_right_corner',[14.35,16.8,1.8],[15,18.7,2.2],1,upper);
  C('upper_front_shadow',[1.65,17.18,1.9],[14.35,18.65,2.2],4,upper);
  C('scanner_base_reveal',[.95,19,2],[15.05,19.24,14.6],5,upper);
  const scanner=G('scanner_assembly');const sb=G('scanner_body',scanner);
  C('scanner_platform',[.95,19.24,1.9],[15.05,19.95,14.6],0,sb);
  C('scanner_front_lip',[.85,19.28,1.7],[15.15,19.85,1.9],1,sb);
  C('scanner_frame_left',[.95,19.95,1.9],[2.05,20.15,14.6],1,sb);
  C('scanner_frame_right',[13.95,19.95,1.9],[15.05,20.15,14.6],1,sb);
  C('scanner_frame_front',[2.05,19.95,1.9],[13.95,20.15,3],1,sb);
  C('scanner_frame_back',[2.05,19.95,13.5],[13.95,20.15,14.6],1,sb);
  C('scanner_glass',[2.05,19.95,3],[13.95,20.06,13.5],8,sb);
  C('scanner_sensor_strip',[2.16,20.06,3.16],[2.65,20.1,13.32],5,sb);
  const lid=G('scanner_lid',scanner,[8,20.2,14.05]);const lh=G('lid_housing',lid);
  C('lid_lower_gasket',[1.45,20.16,2.4],[14.55,20.28,14.05],5,lh);
  C('lid_pressure_pad',[2.15,20.12,3.1],[13.85,20.16,13.45],3,lh);
  C('lid_shell',[1.15,20.28,2.1],[14.85,20.88,14.4],0,lh);
  C('lid_top_inset',[1.45,20.88,2.4],[14.55,21.05,14.1],1,lh);
  C('lid_front_bevel',[1.15,20.88,2.1],[14.85,21,2.4],3,lh);
  C('lid_left_bevel',[1.15,20.88,2.4],[1.45,21,14.4],1,lh);
  C('lid_right_bevel',[14.55,20.88,2.4],[14.85,21,14.4],2,lh);
  C('lid_rear_bevel',[1.45,20.88,14.1],[14.55,21,14.4],2,lh);
  C('lid_front_grip',[6.1,20.47,1.9],[9.9,20.79,2.1],4,lh);
  const hinge=G('scanner_hinges',scanner,[8,20.2,14.05]);
  for(const [n,x] of [['left',2.2],['right',12.0]]){
    C(n+'_hinge_mount',[x,19.5,14.6],[x+1.8,19.95,14.88],4,hinge);
    C(n+'_hinge_pin',[x+.1,20.04,13.88],[x+1.7,20.36,14.25],6,hinge);
    C(n+'_hinge_arm',[x+.35,20.36,13.85],[x+1.45,20.72,14.15],4,lid);
  }
  const adf=G('adf_detail',lid,[8,21.05,9]);
  C('adf_drive_shell',[1.6,21.05,3.1],[7.4,23.5,13.6],0,adf);
  C('adf_top_cap',[1.85,23.5,3.35],[7.15,23.68,13.35],1,adf);
  C('adf_front_rollover',[1.6,23.32,3.1],[7.4,23.5,3.35],3,adf);
  C('adf_back_rollover',[1.6,23.32,13.35],[7.4,23.5,13.6],2,adf);
  C('adf_left_rollover',[1.6,23.5,3.35],[1.85,23.6,13.35],1,adf);
  C('adf_right_rollover',[7.15,23.5,3.35],[7.4,23.6,13.35],2,adf);
  C('adf_drive_base',[1.45,21.05,2.95],[7.55,21.35,13.75],1,adf);
  C('adf_throat',[7.4,22.2,4.0],[7.65,23.25,12.7],5,adf);
  C('adf_output_shelf',[7.55,21.24,4.1],[13.8,21.52,13],4,adf);
  C('adf_output_end',[13.8,21.24,4.1],[14.05,21.72,13],6,adf);
  const feed=G('adf_feed_tray',adf,[7.5,23.12,8.5]);
  const feedExtra={origin:[7.5,23.12,8.5],rotation:[0,0,8]};
  C('adf_feed_plate',[7.45,23.05,3.75],[13.85,23.27,13.15],4,feed,feedExtra);
  C('adf_feed_front_rail',[7.45,23.27,3.75],[13.85,23.5,3.95],6,feed,feedExtra);
  C('adf_feed_back_rail',[7.45,23.27,12.95],[13.85,23.5,13.15],6,feed,feedExtra);
  C('adf_feed_outer_rail',[13.65,23.27,3.95],[13.85,23.5,12.95],6,feed,feedExtra);
  for(let i=0;i<6;i++)C('adf_feed_guide_'+i,[7.9,23.27,4.55+i*1.35],[13.55,23.35,4.72+i*1.35],5,feed,feedExtra);
  for(let i=0;i<3;i++){
    C('adf_output_rib_'+i,[8.2,21.52,5.2+i*2.4],[13.35,21.63,5.42+i*2.4],6,adf);
    C('adf_shell_join_'+i,[1.51,21.7+i*.55,4.2],[1.6,21.77+i*.55,7.3],2,adf);
  }
  const panel=G('control_panel_assembly',root,[9.0,18.0,2.0]);
  const pb=G('control_panel_body',panel),ps=G('control_screen',panel),pk=G('control_buttons',panel),pi=G('indicator_section',panel);
  const pe={origin:[9,18,2],rotation:[22.5,0,0]};
  const P=(n,a,b,m,g)=>C(n,a,b,m,g,pe);
  P('panel_rear_housing',[3.95,16.8,1.9],[14.35,19.6,2.55],4,pb);
  P('panel_face',[4.1,16.95,1.65],[14.2,19.45,1.9],5,pb);
  P('panel_upper_bezel',[4.1,19.45,1.65],[14.2,19.6,1.9],6,pb);
  P('panel_lower_bezel',[4.1,16.8,1.65],[14.2,16.95,1.9],4,pb);
  P('panel_left_bezel',[3.95,16.8,1.65],[4.1,19.6,1.9],6,pb);
  P('panel_right_bezel',[14.2,16.8,1.65],[14.35,19.6,1.9],6,pb);
  P('screen_frame',[4.55,17.3,1.54],[9.8,19.1,1.65],6,ps);
  P('screen_black_border',[4.74,17.48,1.47],[9.61,18.92,1.54],7,ps);
  P('screen_lcd',[4.92,17.62,1.43],[9.43,18.78,1.47],8,ps);
  P('screen_soft_reflection',[5.02,18.59,1.42],[9.29,18.68,1.43],9,ps);
  for(let j=0;j<3;j++)for(let i=0;i<3;i++){
    const x=10.35+i*.97,y=17.35+j*.57;
    P('key_'+j+'_'+i,[x,y,1.49],[x+.58,y+.34,1.65],j===0&&i===2?12:14,pk);
    P('key_top_'+j+'_'+i,[x+.06,y+.27,1.48],[x+.52,y+.31,1.49],15,pk);
  }
  P('status_led',[13.45,18.96,1.5],[13.69,19.08,1.65],12,pi);
  P('stop_key',[13.38,17.35,1.48],[13.85,17.69,1.65],13,pi);
  C('panel_left_bracket',[4.3,17.7,2.55],[5.0,18.9,3.2],4,pb);
  C('panel_right_bracket',[13.2,17.7,2.55],[13.9,18.9,3.2],4,pb);
  const details=G('body_details');
  C('front_header_left',[1.65,17.3,1.78],[3.75,18.9,1.9],4,details);
  C('header_left_top_edge',[1.65,18.9,1.78],[3.75,19.04,2],6,details);
  C('bay_lower_front_rail',[1.65,10.47,1.8],[14.35,10.8,2.2],1,details);
  for(let j=0;j<2;j++){
    const x=j?14.35:1;
    C('front_bay_column_'+j,[x,10.8,1.8],[x+.65,16.8,2.2],1,details);
    C('front_column_lower_fold_'+j,[x,10.8,1.69],[x+.65,11.01,1.8],2,details);
    C('front_column_upper_fold_'+j,[x,16.57,1.69],[x+.65,16.8,1.8],3,details);
  }
  Project.save_path='D:/Minecraft Modding/Apocalypse First Light/src/main/blockbench/office_multifunction_printer.bbmodel';
  for(const p of Preview.all){if(!p.controls||!p.camera)continue;p.controls.target.set(8,12,8);p.camera.position.set(39,28,-43);p.camera.zoom=1;p.camera.updateProjectionMatrix();p.controls.update();}
  Canvas.updateAll();
  return {name:Project.name,cubes:Cube.all.length,groups:Group.all.length,texture:tex.uuid};
})()
