(async () => {
  if (Project.name !== 'precision_fabrication_station' || Cube.all.length) throw Error('Requires empty precision_fabrication_station project');
  const specs=[], groups={}, names=[];
  const group=(name,parent='precision_fabrication_station_root',pivot=[8,0,8])=>{groups[name]={parent,pivot}; return name;};
  group('precision_fabrication_station_root',null);
  group('frame'); for(const n of ['left_column','right_column','top_beam','base_frame']) group(n,'frame');
  for(const n of ['rear_panel','work_light','left_drive_module','assembly_head','central_fixture','control_panel','status_light','fe_power_port','lower_storage','side_panels']) group(n);
  group('central_fixture_left_clamp','central_fixture',[13,17,6]);
  group('central_fixture_right_clamp','central_fixture',[3,17,6]);
  groups.assembly_head.pivot=[8,28.7,11.5];
  for(const n of ['green_parts_crate','drawer_unit','yellow_parts_bin']) group(n,'lower_storage');
  const box=(g,n,f,t,m='steel',faces={})=>{if(f.some((v,i)=>v>=t[i]))throw Error(n); specs.push({g,n,f,t,m,faces});};
  const bolt=(g,x,y,z)=>{box(g,'fastener_seat',[x-.26,y-.26,z],[x+.26,y+.26,z+.12],'steel_dark');box(g,'fastener_head',[x-.15,y-.15,z-.12],[x+.15,y+.15,z],'silver');};
  for(const [x,g] of [[-8,'right_column'],[22,'left_column']]) {
    box(g,'rear_column',[x,1,14],[x+2,30.6,16]);
    box(g,'column_front_flange',[x+.2,1.2,13.7],[x+1.8,30.4,14],'steel_mid');
    box(g,'front_leg',[x,1,.6],[x+2,14.8,2.4]);
    for(const z of [.6,14]) box(g,'foot_shoe',[x,0,z],[x+2,1,Math.min(16,z+2)],'steel_mid');
    box(g,'upper_corner_cap',[x,30.6,10.3],[x+2,32,16],'steel_mid');
    for(const y of [3,12.8,18,29]) bolt(g,x+1,y,13.58);
    for(const y of [2,13]) bolt(g,x+1,y,.48);
  }
  box('top_beam','top_box_beam',[-6,30.6,10.3],[22,32,16]);
  box('top_beam','top_fold_front',[-6,30.6,10],[22,31.05,10.3],'steel_mid');
  box('top_beam','top_ridge',[-5.7,31.75,12.5],[21.7,32,15.7],'steel_dark');
  box('base_frame','bottom_shelf',[-6,1.5,2.1],[22,2.25,14.5]);
  box('base_frame','bottom_front_fold',[-6,1.5,1.8],[22,2.3,2.1],'bronze');
  box('base_frame','front_crossmember',[-6,12.9,1.25],[22,14.8,2.35]);
  box('base_frame','rear_crossmember',[-6,12.9,14.5],[22,14.8,16]);
  box('base_frame','table_solid',[-8,14.8,.4],[24,16.2,15.8],'steel_mid');
  box('base_frame','table_wear_surface',[-7.8,16.2,.65],[23.8,16.45,15.55],'steel');
  box('base_frame','table_front_composite_edge',[-8,15.05,.15],[24,16.2,.4],'bronze');
  box('base_frame','table_front_lip',[-8,14.8,.15],[24,15.05,.4],'steel_dark');
  box('rear_panel','upper_structural_back',[-6,16.45,14.45],[22,30.6,15.55],'panel',{north:'peg'});
  box('rear_panel','bottom_service_back',[-6,2.3,14.5],[22,12.9,15.4],'panel');
  for(const x of [-5.7,21.15]) box('rear_panel','upper_side_seam',[x,16.65,14.12],[x+.55,30.3,14.45],'steel_dark');
  for(const y of [16.65,29.8]) box('rear_panel','panel_horizontal_frame',[-5.15,y,14.12],[21.15,y+.45,14.45],'steel_mid');
  for(const x of [-5.45,21.42]) for(const y of [17.4,29.35]) bolt('rear_panel',x,y,13.98);
  for(const x of [-8,23.4]) {
    box('side_panels','lower_side_panel',[x,2.3,2.4],[x+.6,14.8,14],'panel');
    box('side_panels','side_access_cover',[x===-8?-8:23.6,4.2,4.1],[x===-8?-7.65:24,12.7,12.9],'steel_mid',{east:'vent',west:'vent'});
    box('side_panels','upper_rear_cheek',[x,16.45,11.7],[x+.6,30.6,14],'steel_dark');
  }
  box('work_light','lamp_carrier',[-5.6,30.05,10.5],[21.6,30.6,12.3],'steel_dark');
  box('work_light','lamp_diffuser',[-4.9,29.86,10.7],[20.9,30.05,12.05],'warm_light');
  for(const x of [-5.6,20.9]) box('work_light','lamp_end_cap',[x,29.8,10.5],[x+.7,30.05,12.3],'steel_mid');
  for(const x of [5.05,10.35]) {
    box('top_beam','guide_top_mount',[x-.35,28.5,12.3],[x+1,30.6,14.4],'steel_dark');
    box('top_beam','vertical_guide',[x,24.6,11.95],[x+.65,28.5,12.6],'silver');
  }
  box('assembly_head','ram_upper',[6.55,26.8,11.1],[9.45,29,13.6],'steel_dark');
  box('assembly_head','head_main',[5.05,23.4,9.4],[10.95,26.8,13.75],'steel_mid');
  box('assembly_head','head_front_plate',[5.55,23.8,9.1],[10.45,26.4,9.4],'panel',{north:'head_mark'});
  for(const x of [4.7,10.95]) box('assembly_head','head_cheek',[x,23.75,9.65],[x+.35,26.4,13.35],'silver');
  box('assembly_head','head_lower_neck',[6.4,22.75,10.05],[9.6,23.4,13],'steel_dark');
  box('assembly_head','brass_chuck',[6.95,22.1,10.45],[9.05,22.75,12.55],'orange');
  box('assembly_head','press_shank',[7.48,20.8,10.95],[8.52,22.1,12],'silver');
  box('assembly_head','press_tip',[7.64,20.25,11.15],[8.36,20.8,11.85],'steel_dark');
  for(const x of [5.85,10.1]) for(const y of [24.05,26.13]) bolt('assembly_head',x,y,8.96);
  box('central_fixture','precision_base',[.6,16.45,3.2],[15.4,17.15,13.6],'steel_dark');
  box('central_fixture','base_front_bevel',[.9,16.45,2.8],[15.1,16.8,3.2],'steel_mid');
  box('central_fixture','warning_inlay',[2.1,16.8,2.85],[13.9,16.95,3.2],'hazard');
  for(const z of [5,11.8]) box('central_fixture','guide_rail',[1.1,17.15,z],[14.9,17.5,z+.65],'silver');
  box('central_fixture','central_work_rest',[5.15,17.15,7.4],[10.85,17.65,12.25],'steel_mid');
  box('central_fixture','center_insert',[5.5,17.65,8.5],[10.5,17.85,12],'orange');
  box('central_fixture','work_landing',[6,17.85,9.05],[10,18,11.6],'silver');
  for(const [x,g] of [[1.45,'central_fixture_right_clamp'],[11.55,'central_fixture_left_clamp']]) {
    box(g,'clamp_carriage',[x,17.5,4.9],[x+3,18.25,12.7],'steel_mid');
    box(g,'clamp_body',[x+.35,18.25,6.2],[x+2.65,20.1,12.2],'steel');
    box(g,'jaw_top',[x+.6,20.1,7],[x+2.4,20.4,11.9],'silver');
    box(g,'jaw_face',[x+(x<8?2.3:.35),18.5,8],[x+(x<8?2.65:.7),20.1,11.9],'silver');
    box(g,'clamp_front_lock',[x+.9,18.55,5.85],[x+2.1,19.6,6.2],'steel_dark');
    bolt(g,x+1.5,19.05,5.71);
  }
  for(const x of [1.1,14.5]) for(const z of [3.9,13]) box('central_fixture','base_anchor',[x,17.15,z],[x+.45,17.35,z+.45],'silver');
  box('left_drive_module','drive_back_mount',[15.8,21.8,12.1],[21.6,26.5,14.45],'steel_dark');
  box('left_drive_module','drive_housing',[16.2,22.05,9.65],[21.2,26.7,12.1],'steel_mid');
  box('left_drive_module','motor_core',[17.15,22.2,8.9],[20.1,26.6,9.65],'steel_dark');
  for(const x of [17.05,19.6]) box('left_drive_module','orange_retainer',[x,22,8.65],[x+.5,26.85,9.65],'orange');
  box('left_drive_module','front_service_cover',[20.15,22.65,9.1],[21.65,26.1,9.65],'panel');
  for(const y of [23,25.7]) bolt('left_drive_module',20.9,y,8.96);
  box('left_drive_module','lower_drive_shaft',[18.2,19,11],[19.5,22.05,12.3],'silver');
  box('left_drive_module','shaft_guard',[17.7,18.55,10.5],[20,19.25,12.8],'steel_dark',{north:'hazard'});
  box('left_drive_module','lower_mount',[17.25,16.45,10.1],[20.45,18.55,13.2],'steel_mid');
  box('left_drive_module','upper_conduit',[11,28.6,13.6],[18.8,29.1,14.15],'steel_dark');
  box('left_drive_module','motor_conduit',[18.3,26.7,13.6],[18.8,28.6,14.15],'steel_dark');
  box('control_panel','control_pedestal',[-5.5,16.45,8.3],[-.3,18.35,13],'steel_dark');
  box('control_panel','console_body',[-5.9,18.35,7.85],[.1,26.05,12.4],'steel_mid');
  box('control_panel','console_front',[-5.55,18.7,7.55],[-.25,25.7,7.85],'panel');
  box('control_panel','screen_bezel',[-5.12,21.5,7.2],[-.68,25.2,7.55],'steel_dark');
  box('control_panel','status_screen',[-4.82,21.82,7.05],[-.98,24.85,7.2],'screen');
  for(const [x,mat] of [[-4.83,'red'],[-3.45,'green'],[-2.07,'orange']]) {
    box('control_panel','button_socket',[x,19.6,7.25],[x+1.06,20.75,7.55],'steel_dark');
    box('control_panel','button',[x+.17,19.8,7.02],[x+.89,20.57,7.25],mat);
  }
  for(const y of [19.2,25.35]) for(const x of [-5.31,-.46]) bolt('control_panel',x,y,7.41);
  box('control_panel','console_side_cover',[-5.98,19.3,8.65],[-5.9,24.85,11.65],'vent');
  box('status_light','beacon_foot',[-4.35,26.05,10],[-2.1,26.45,12],'steel_dark');
  box('status_light','beacon_lens',[-4.04,26.45,10.28],[-2.4,28.04,11.72],'red');
  box('status_light','beacon_top',[-4.04,28.04,10.28],[-2.4,28.23,11.72],'steel_mid');
  box('status_light','beacon_highlight',[-3.75,26.8,10.2],[-3.3,27.8,10.28],'red_light');
  const g='green_parts_crate';
  box(g,'crate_body',[13.3,2.25,3.3],[21.5,7.8,12.3],'olive');
  box(g,'crate_lid',[13.05,7.8,3.05],[21.75,8.45,12.55],'olive_light');
  box(g,'lid_inset',[14.2,8.45,4.2],[20.6,8.65,11.4],'olive');
  for(const x of [13.7,20.6]) box(g,'crate_corner_band',[x,2.55,3.13],[x+.38,7.6,3.3],'olive_light');
  for(const x of [14.5,20]) box(g,'crate_latch',[x,6.7,2.85],[x+.55,8.15,3.05],'silver');
  box(g,'crate_handle_base',[16.4,8.65,6.6],[18.4,8.85,8.2],'steel_dark');
  const d='drawer_unit';
  box(d,'cabinet_case',[3.6,2.25,4.8],[11.95,9.65,13.5],'steel_dark');
  for(const y of [2.8,6.25]) {
    box(d,'drawer_front',[4,y,4.45],[11.55,y+2.92,4.8],'steel');
    box(d,'drawer_handle_foot',[6.45,y+1.25,4.1],[9.1,y+1.85,4.45],'steel_dark');
    box(d,'drawer_pull',[6.65,y+1.48,3.85],[8.9,y+1.85,4.1],'silver');
  }
  box(d,'cabinet_top_cap',[3.45,9.65,4.6],[12.1,10.1,13.65],'steel_mid');
  const b='yellow_parts_bin';
  box(b,'bin_floor',[-5.65,2.25,3.65],[1.75,2.9,10.9],'orange_dark');
  box(b,'bin_front',[-5.65,2.9,3.65],[1.75,5.5,4.15],'orange');
  box(b,'bin_back',[-5.65,2.9,10.35],[1.75,6.6,10.9],'orange');
  for(const x of [-5.65,1.2]) box(b,'bin_side',[x,2.9,4.15],[x+.55,6.6,10.35],'orange');
  box(b,'bin_front_upper_rail',[-5.65,5.5,3.65],[1.75,6.2,4.15],'orange_light');
  box(b,'bin_label_socket',[-3.15,4.5,3.5],[-.9,5.25,3.65],'steel_dark');
  for(const [x,y,z] of [[-4.65,5.8,6],[-2.35,7.45,8.8],[.2,6.8,6.8]]) {
    box(b,'generic_component',[x,2.9,z],[x+1.15,y,z+1.3],'steel_dark');
    box(b,'component_cap',[x,y,z],[x+1.15,y+.22,z+1.3],'steel_mid');
  }
  box('fe_power_port','standard_machine_back_panel',[12,3.9,15.4],[20,11.9,16],'panel',{south:'machine_back'});
  box('rear_panel','rear_access_frame',[-5.25,3.9,15.4],[10,11.9,15.75],'steel_mid');
  box('rear_panel','rear_access_cover',[-4.75,4.4,15.75],[9.5,11.4,16],'panel',{south:'rear_vent'});
  for(const x of [-5.1,9.62]) for(const y of [4.05,11.52]) box('rear_panel','rear_fastener',[x,y,15.75],[x+.23,y+.23,16],'silver');
  box('rear_panel','back_upper_service',[-5.5,17,15.55],[21.5,30.1,15.85],'panel');
  for(const x of [-5.5,21.15]) box('rear_panel','back_upper_rail',[x,17,15.85],[x+.35,30.1,16],'steel_mid');
  for(const y of [17,29.75]) box('rear_panel','back_upper_rail',[-5.15,y,15.85],[21.15,y+.35,16],'steel_mid');
  const subtract=(a,b)=>{
    const lo=a.f.map((v,i)=>Math.max(v,b.f[i])),hi=a.t.map((v,i)=>Math.min(v,b.t[i]));
    if(lo.some((v,i)=>hi[i]-v<1e-6)) return [a];
    const f=[...a.f],t=[...a.t],out=[];
    for(let i=0;i<3;i++) {if(f[i]<lo[i]-1e-6){const tt=[...t];tt[i]=lo[i];out.push({...a,f:[...f],t:tt});f[i]=lo[i];}
      if(t[i]>hi[i]+1e-6){const ff=[...f];ff[i]=hi[i];out.push({...a,f:ff,t:[...t]});t[i]=hi[i];}}
    return out;
  };
  let solids=[];
  for(const spec of specs) {solids=solids.flatMap(s=>subtract(s,spec));solids.push(spec);}
  const tiles={}, colors={steel:'#414548',steel_mid:'#62666a',steel_dark:'#292d30',panel:'#333638',silver:'#979b9b',bronze:'#796047',orange:'#ab7d32',orange_dark:'#6c542b',orange_light:'#c39546',olive:'#515733',olive_light:'#6c7243',warm_light:'#eee1b1',red:'#993d32',green:'#38664b',red_light:'#ee9077',peg:'#303434',vent:'#34383a',screen:'#204a43',hazard:'#b88b35',head_mark:'#444a4c',rear_vent:'#363a3b'};
  const canvas=document.createElement('canvas');canvas.width=256;canvas.height=256;const ctx=canvas.getContext('2d');ctx.imageSmoothingEnabled=false;
  ctx.fillStyle='#303437';ctx.fillRect(0,0,256,256);
  let idx=0;for(const [name,color] of Object.entries(colors)) {
    const x=(idx%8)*32,y=Math.floor(idx/8)*32;idx++;tiles[name]=[x,y,x+32,y+32];
    ctx.fillStyle=color;ctx.fillRect(x,y,32,32);
    for(let py=0;py<32;py++)for(let px=0;px<32;px++) {
      const n=((px*17+py*31+px*py*3)%43);ctx.fillStyle=n<4?'#ffffff0b':n>39?'#0000000b':'#00000000';ctx.fillRect(x+px,y+py,1,1);
    }
    ctx.fillStyle='#ffffff12';ctx.fillRect(x,y,32,1);ctx.fillRect(x,y,1,32);
    ctx.fillStyle='#00000024';ctx.fillRect(x+31,y,1,32);ctx.fillRect(x,y+31,32,1);
  }
  const rect=(tile,x,y,w,h,col)=>{ctx.fillStyle=col;const [u,v]=tiles[tile];ctx.fillRect(u+x,v+y,w,h);};
  for(let y=3;y<32;y+=8)for(let x=3;x<32;x+=8){rect('peg',x,y,4,3,'#15191a');rect('peg',x,y+3,4,1,'#43494b');}
  tiles.peg=[0,128,128,192];ctx.fillStyle='#303436';ctx.fillRect(0,128,128,64);
  for(let y=134;y<186;y+=12)for(let x=6;x<123;x+=16) {ctx.fillStyle='#161a1b';ctx.fillRect(x,y,6,3);ctx.fillStyle='#414648';ctx.fillRect(x,y+3,6,1);}
  for(const name of ['vent','rear_vent'])for(let y=5;y<28;y+=6) {
    if(name==='rear_vent'){for(const x of [5,19])rect(name,x,y,8,2,'#171c1d');}
    else rect(name,6,y,20,2,'#171c1d');
  }
  rect('screen',2,2,28,28,'#285f53');rect('screen',4,5,11,17,'#6caa8e');rect('screen',6,7,6,4,'#a4cbb0');
  for(const [x,y,w] of [[18,5,9],[18,10,5],[18,15,9],[18,20,6],[4,26,12],[20,26,7]])rect('screen',x,y,w,2,'#83b79a');
  for(let y=0;y<32;y++)for(let x=0;x<32;x++)if((x+y)%12<5)rect('hazard',x,y,1,1,'#30302a');
  rect('head_mark',14,13,4,7,'#b88b35');rect('head_mark',10,19,12,3,'#b88b35');rect('head_mark',13,23,6,1,'#73797a');
  for(const name of ['steel','steel_mid','steel_dark','panel','silver','vent','head_mark','rear_vent']) {
    const [x,y]=tiles[name];ctx.fillStyle=colors[name];ctx.fillRect(x,y,32,32);
    ctx.fillStyle='#ffffff05';ctx.fillRect(x+2,y+2,28,8);ctx.fillStyle='#00000004';ctx.fillRect(x+2,y+23,28,7);
    ctx.fillStyle='#ffffff10';ctx.fillRect(x,y,32,1);ctx.fillRect(x,y,1,32);
    ctx.fillStyle='#00000018';ctx.fillRect(x+31,y,1,32);ctx.fillRect(x,y+31,32,1);
  }
  for(const name of ['vent','rear_vent'])for(let y=5;y<28;y+=6) {
    if(name==='rear_vent'){for(const x of [6,20])rect(name,x,y+2,6,2,'#171c1d');}else rect(name,6,y,20,2,'#171c1d');
  }
  rect('head_mark',14,13,4,7,'#b88b35');rect('head_mark',10,19,12,3,'#b88b35');rect('head_mark',13,23,6,1,'#73797a');
  const fs=require('fs');
  const backPath='D:/Minecraft Modding/Apocalypse First Light/src/main/resources/assets/apocalypse_firstlight/textures/block/machine_back.png';
  const img=new Image();img.src='data:image/png;base64,'+fs.readFileSync(backPath).toString('base64');await img.decode();
  if(img.width!==32||img.height!==32)throw Error('Standard machine back size changed');
  ctx.drawImage(canvas,96,0,32,32,224,224,32,32);
  ctx.fillStyle='#62666a';ctx.fillRect(224,224,32,2);ctx.fillRect(224,254,32,2);ctx.fillRect(224,226,2,28);ctx.fillRect(254,226,2,28);
  ctx.drawImage(img,11,11,10,10,235,235,10,10);
  tiles.machine_back=[224,224,256,256];
  Project.texture_width=256;Project.texture_height=256;
  Project.geometry_name='precision_fabrication_station';
  Undo.initEdit({elements:[],textures:[],outliner:true});
  const texture=new Texture({name:'precision_fabrication_station.png',namespace:'apocalypse_firstlight',folder:'block'}).fromDataURL(canvas.toDataURL()).add(false);
  texture.particle=true;
  const live={};for(const [name,{parent,pivot}] of Object.entries(groups)){const gr=new Group({name,origin:pivot});if(parent)gr.addTo(live[parent]);gr.init();live[name]=gr;}
  const shading={north:0,south:2,up:4,down:6,east:1,west:3};
  for(const s of solids) {
    const c=new Cube({name:s.n,from:s.f,to:s.t,origin:groups[s.g].pivot,box_uv:false,autouv:0}).addTo(live[s.g]).init();
    for(const side of ['north','south','east','west','up','down']) {
      const material=s.faces[side]??s.m;const tile=tiles[material];
      const full=['screen','peg','vent','head_mark','rear_vent','hazard','machine_back'].includes(material);
      const dim=s.t.map((v,i)=>v-s.f[i]),axes=side==='up'||side==='down'?[0,2]:side==='east'||side==='west'?[2,1]:[0,1];
      const uv=full?tile:[tile[0]+2,tile[1]+2,tile[0]+2+Math.min(28,Math.max(2,dim[axes[0]]*2)),tile[1]+2+Math.min(28,Math.max(2,dim[axes[1]]*2))];
      c.faces[side].extend({uv,texture:texture.uuid});
    }
  }
  const settings={gui:{rotation:[25,-135,0],translation:[0,-2.8,0],scale:[.35,.35,.35]},ground:{translation:[0,2,0],scale:[.25,.25,.25]},fixed:{rotation:[0,180,0],translation:[0,-3,0],scale:[.35,.35,.35]},firstperson_righthand:{rotation:[0,45,0],scale:[.25,.25,.25]},thirdperson_righthand:{rotation:[75,45,0],translation:[0,2.5,0],scale:[.25,.25,.25]}};
  for(const [slot,data] of Object.entries(settings)) Project.display_settings[slot]=new DisplaySlot(slot,data);
  Undo.finishEdit('Build precision assembly station V1');Canvas.updateAll();
  return JSON.stringify({cubes:Cube.all.length,groups:Group.all.length,texture:texture.uuid,textureSize:[256,256],feAtlasRegion:tiles.machine_back});
})()
