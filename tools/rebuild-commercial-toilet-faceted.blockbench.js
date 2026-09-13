(async()=>{
 const fs=require('fs'),base='D:/Minecraft Modding/Apocalypse First Light/';
 if(Project.name!=='commercial_flushometer_toilet')throw Error('Select toilet, not BR51');
 const backup=base+'src/main/blockbench/previews/commercial_flushometer_toilet_before_br51_rebuild.bbmodel';
 if(fs.existsSync(backup))throw Error('Backup already exists');
 fs.writeFileSync(backup,Codecs.project.compile());
 const root=Group.all.find(g=>g.name==='commercial_toilet_root'),metal=Group.all.find(g=>g.name==='flushometer_assembly');
 for(const g of [...root.children])if(g!==metal)g.remove();
 const groups={};
 function group(n,p=root){return groups[n]=new Group({name:n,origin:[0,0,0]}).addTo(p).init();}
 const body=group('toilet_body_faceted');
 for(const n of ['bowl_rim','bowl_shell','bowl_inner','pedestal','base','rear_connector'])group(n,body);
 group('open_front_seat');group('bowl_water');
 const tex=Texture.all[0];
 function box(g,n,center,size,mat=0,rot=[0,0,0]){
  const c=new Cube({name:n,from:center.map((v,i)=>v-size[i]/2),to:center.map((v,i)=>v+size[i]/2),origin:center,rotation:rot,autouv:0,box_uv:false}).addTo(groups[g]).init();
  for(const f of Object.values(c.faces)){f.texture=tex.uuid;const x=mat%4*32,y=Math.floor(mat/4)*32;f.uv=[x+8,y+8,x+24,y+24];}return c;
 }
 // Hand-laid longitudinal silhouette: two long sides, four front bevels,
 // two rear shoulders and two end faces. No angular/radius sampling.
 const outline=[[-1.5,-6], [1.5,-6], [3.25,-5.25], [4.25,-3.5], [4.25,3.25], [3,4.5], [-3,4.5], [-4.25,3.25], [-4.25,-3.5], [-3.25,-5.25]];
 function edges(g,prefix,y,height,width,mat,open=false){
  outline.forEach((p,i)=>{
   if(open&&i===0)return;
   const q=outline[(i+1)%outline.length],dx=q[0]-p[0],dz=q[1]-p[1];
   const lift=Math.abs((p[0]+q[0])/2)*.002;
   box(g,prefix+'_'+i,[(p[0]+q[0])/2,y+lift,(p[1]+q[1])/2],[Math.hypot(dx,dz)+.19,height,width],mat,[0,-Math.atan2(dz,dx)*180/Math.PI,0]);
  });
 }
 edges('bowl_rim','rim_main_facet',7.7,.8,1.05,1);
 Cube.selected.empty();Group.all.forEach(g=>g.selected=false);Canvas.updateAll();
 const shot=async(name,pos,target=[0,7.7,-.5],zoom=1.35)=>{
  const p=Preview.selected;p.loadAnglePreset({position:pos,target,projection:'orthographic',zoom});p.controls.update();Canvas.updateAll();
  await new Promise(r=>setTimeout(r,150));await new Promise(done=>Screencam.screenshotPreview(p,{width:1000,height:1000},data=>{fs.writeFileSync(base+'docs/models/previews/commercial_flushometer_toilet/'+name+'.png',Buffer.from(data.split(',')[1],'base64'));done();}));
 };
 globalThis.aflFacetedToilet={box,edges,outline,groups,shot,base,fs,finish(){
  edges('open_front_seat','seat',8.38,.55,1.22,13,true);
  // Full-height longitudinal cheek plates; bevels are vertical/slanted,
  // not stacks of horizontal concentric courses.
  for(const s of [-1,1]){
   box('bowl_shell','long_cheek_'+s,[s*3.85,6.25,-.1],[1.35,3.05,6.75],0,[0,0,-s*22.5]);
   box('bowl_shell','front_cheek_'+s,[s*2.8,6.2,-4.55],[2.7,2.8,1.3],0,[-30,-s*60,0]);
   box('bowl_shell','nose_bevel_'+s,[s*1.7,6.35,-5.35],[2.7,2.7,1.35],0,[-30,-s*22.5,0]);
   box('bowl_shell','rear_shoulder_'+s,[s*3.15,6.2,3.7],[2.3,3.1,1.2],0,[15,s*45,0]);
   box('bowl_inner','inner_long_slope_'+s,[s*2.95,6.35,-.35],[.35,2.7,6.3],2,[0,0,-s*22.5]);
   box('bowl_inner','inner_front_slope_'+s,[s*1.8,6.15,-4.15],[2.8,2.4,.4],2,[-35,-s*32.5,0]);
   box('bowl_inner','inner_rear_slope_'+s,[s*1.85,6.35,3.4],[2.6,2.4,.45],2,[22.5,s*30,0]);
  }
  box('bowl_shell','front_apron',[0,6.3,-5.55],[3.2,2.8,1.1],0,[-30,0,0]);
  box('bowl_shell','rear_apron',[0,6.1,4.25],[6,3.45,1.2],0,[15,0,0]);
  box('bowl_inner','inner_nose_slope',[0,6.2,-4.65],[2.8,2.7,.4],2,[-35,0,0]);
  box('bowl_inner','inner_back_slope',[0,6.35,3.75],[3.8,2.7,.4],2,[22.5,0,0]);
  // Dry elongated ceramic sump, with diagonal end shoulders.
  box('bowl_inner','dry_sump_core',[0,4.85,-.4],[4.7,.35,6.8],3);
  for(const s of [-1,1])box('bowl_inner','dry_sump_front_bevel_'+s,[s*1.15,4.84,-3.6],[2.55,.32,1.9],3,[0,s*30,0]);
  // Rectangular pedestal with corner chamfers, shifted rearward under the bowl.
  box('pedestal','pedestal_main',[0,2.55,1.35],[4.15,4.1,5.5],0);
  for(const s of [-1,1]){
   box('pedestal','pedestal_side_'+s,[s*2.05,2.55,1.35],[.75,3.95,4.45],0);
   for(const z of [-1,1])box('pedestal','pedestal_corner_'+s+'_'+z,[s*1.85,2.55,1.35+z*2.45],[1,3.95,1.05],0,[0,s*z*45,0]);
   box('pedestal','upper_haunch_'+s,[s*2.05,4.1,.75],[1,1.6,5.3],0,[0,0,-s*22.5]);
  }
  box('base','base_core',[0,.3,1.3],[4.6,.6,6.15],2);
  for(const s of [-1,1]){
   box('base','base_side_'+s,[s*2.35,.3,1.3],[.7,.6,5.1],2);
   for(const z of [-1,1])box('base','base_chamfer_'+s+'_'+z,[s*2.1,.3,1.3+z*2.75],[1.05,.6,1.05],2,[0,45,0]);
  }
  box('rear_connector','rear_neck',[0,6.45,5.25],[3.8,2.9,2.65],0);
  box('rear_connector','valve_socket_deck',[0,7.65,5.8],[4.4,.5,3],1);
  for(const s of [-1,1])box('open_front_seat','hinge_'+s,[s*2.25,8.1,4.5],[.55,.35,.85],9);
  Cube.selected.empty();Canvas.updateAll();
  const path=base+'src/main/blockbench/commercial_flushometer_toilet.bbmodel';fs.writeFileSync(path,Codecs.project.compile());Project.save_path=path;Project.saved=true;
  return {count:Cube.all.length,path};
 }};
 await shot('phase1_top',[0,65,-.001]);return {phase:'TOP SILHOUETTE ONLY',facets:10,backup};
})()
