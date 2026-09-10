(() => {
 const w=globalThis.aflWater;if(!w||Project.uuid!==w.project||Cube.all.length!==101)throw Error('Bottle stage changed');
 Undo.initEdit({elements:[],outliner:true});const add=w.add;
 for(const [name,x,mat] of [['hot',5.5,'red'],['cold',10.5,'blue']]){
  add(name+'_indicator_mount',[x-.6,16.48,1.78],[x+.6,16.92,1.88],'rubber',name+'_control');
  add(name+'_indicator',[x-.38,16.59,1.71],[x+.38,16.82,1.78],mat,name+'_control');
  add(name+'_valve_backplate',[x-.85,13.55,5.28],[x+.85,15.65,5.72],'metal',name+'_control');
  add(name+'_valve_body',[x-.6,14.1,3.55],[x+.6,15.25,5.28],'dark',name+'_control');
  add(name+'_lever_hinge',[x-.82,14.82,3.37],[x+.82,15.12,3.75],'metal',name+'_control');
  add(name+'_lever_cap',[x-.62,14.65,2.88],[x+.62,15.52,3.48],mat,name+'_control');
  add(name+'_lever_top',[x-.49,15.52,3],[x+.49,15.66,3.4],mat,name+'_control');
  add(name+'_press_paddle',[x-.39,13.67,3.05],[x+.39,14.65,3.3],'metal',name+'_control');
  add(name+'_spout_collar',[x-.55,13.9,3.65],[x+.55,14.3,4.55],'dark',name+'_spout');
  add(name+'_spout_nozzle',[x-.32,13.15,3.83],[x+.32,13.9,4.37],'metal',name+'_spout');
  add(name+'_nozzle_outlet',[x-.21,13.1,3.94],[x+.21,13.15,4.26],'rubber',name+'_spout');
 }
 add('tray_basin',[3.2,9.5,1.5],[12.8,9.8,5.7],'dark','drip_tray');
 add('tray_front_rim',[3.2,9.8,1.4],[12.8,10.2,1.72],'metal','drip_tray');
 add('tray_back_rim',[3.2,9.8,5.42],[12.8,10.2,5.7],'metal','drip_tray');
 for(const x of [3.2,12.47])add('tray_side_'+x,[x,9.8,1.72],[x+.33,10.2,5.42],'metal','drip_tray');
 for(let i=0;i<6;i++){const x=3.9+i*1.43;add('drain_grille_'+i,[x,9.84,1.8],[x+.5,10.01,5.3],'metal','drip_tray');}
 for(const y of [2,2.6,3.2])add('front_lower_vent_'+y,[8.4,y,2.02],[11.9,y+.18,2.1],'vent','lower_vents');
 for(const side of [0,1]){const x=side?14.01:1.94;
  add('side_access_plate_'+side,[x,1.8,6.9],[x+.05,7.2,12.45],'shell','side_details');
  for(let i=0;i<5;i++)add('side_vent_'+side+'_'+i,[side?14.06:1.88,2.6+i*.65,7.4],[side?14.14:1.94,2.84+i*.65,11.95],'vent','side_details');
  for(const z of [3.3,12.7])add('side_panel_seam_'+side+'_'+z,[side?14:1.95,1.7,z],[side?14.05:2,19.3,z+.09],'dark','side_details');
  for(const y of [2.05,6.65])for(const z of [7.1,12.1])add('side_access_screw_'+side+'_'+y+'_'+z,[side?14.07:1.83,y,z],[side?14.17:1.94,y+.22,z+.22],'metal','side_details');
 }
 add('rear_service_cover',[3.7,2,13.55],[12.3,17.7,13.68],'shell','rear_panel');
 for(let i=0;i<6;i++)add('rear_vent_'+i,[5,11.9+i*.65,13.68],[11,12.15+i*.65,13.77],'vent','rear_panel');
 for(const x of [4,11.8])for(const y of [2.4,17.15])add('rear_cover_screw_'+x+'_'+y,[x,y,13.69],[x+.22,y+.22,13.8],'metal','rear_panel');
 add('rear_power_grommet',[10.3,3.4,13.68],[11.6,4.35,13.9],'rubber','rear_panel');
 add('rear_short_cord_relief',[10.7,3,13.9],[11.15,3.9,14.35],'dark','rear_panel');
 add('rear_service_label',[5,7.8,13.69],[8.2,9.3,13.73],'label','rear_panel');
 for(const x of [2.3,13.4])for(const y of [1.4,19.4])add('front_case_fastener_'+x+'_'+y,[x,y,2.06],[x+.22,y+.22,2.15],'metal','upper_body');
 Undo.finishEdit('Water dispenser: dual taps, drain grille, service panels and fixings');Canvas.updateAll();return {stage:'detail',cubes:Cube.all.length,groups:Group.all.length};
})()
