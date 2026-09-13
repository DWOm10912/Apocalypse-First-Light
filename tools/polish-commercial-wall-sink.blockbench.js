(function(){
 const fs=require('fs'),root='D:/Minecraft Modding/Apocalypse First Light/';
 if(Project.name!=='commercial_wall_mounted_sink')throw Error('Select sink');
 for(const c of Cube.all){
  if(/^(outer_ceramic_|basin_slope_|outer_lip_bevel_)/.test(c.name)){
   // These end faces are buried by adjacent solid ceramic panels.
   for(const side of ['east','west','up','down'])c.faces[side].texture=null;
  }
  if(/^(outer_ceramic_|basin_slope_|rim_surface_|outer_lip_bevel_)/.test(c.name))for(const f of Object.values(c.faces)){
   const x=Math.floor(f.uv[0]/32)*32,y=Math.floor(f.uv[1]/32)*32;f.uv=[x+10,y+10,x+22,y+22];
  }
  if(c.name.startsWith('u_bend_'))c.rotation[0]=-c.rotation[0];
 }
 const g=Group.all.find(g=>g.name==='underside_shell'),tex=Texture.all[0];
 const add=(name,from,to)=>{let c=new Cube({name,from,to,autouv:0,box_uv:false}).addTo(g).init();for(const f of Object.values(c.faces)){f.texture=tex.uuid;f.uv=[74,10,86,22];}};
 // Overlapping solid underside plates join the recessed basin floor to the outer shell.
 add('closed_underpan_main',[-5.15,7.02,-.6],[5.15,7.48,6.1]);
 add('closed_underpan_cross',[-5.68,7.04,.03],[5.68,7.46,5.47]);
 Canvas.updateAll();fs.writeFileSync(root+'src/main/blockbench/commercial_wall_mounted_sink.bbmodel',Codecs.project.compile());Project.saved=true;return Cube.all.length;
})()
