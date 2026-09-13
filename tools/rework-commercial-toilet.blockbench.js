(async()=>{
 const fs=require('fs'),base='D:/Minecraft Modding/Apocalypse First Light/';
 if(Project.name!=='commercial_flushometer_toilet')throw Error('Select production source first');
 const backup=base+'src/main/blockbench/previews/commercial_flushometer_toilet_before_rounding.bbmodel';
 if(fs.existsSync(backup))throw Error('Rework backup exists; do not overwrite');
 const previous=Codecs.project.compile();fs.writeFileSync(backup,previous);
 const old=JSON.parse(previous),oldCount=Cube.all.length,texture=Texture.all[0];
 const preserved=Cube.all.filter(c=>{let g=c.parent;while(g instanceof Group){if(g.name==='flushometer_assembly')return true;g=g.parent;}return false;}).map(c=>c.uuid);
 for(const c of [...Cube.all])if(!preserved.includes(c.uuid))c.remove();
 const groups=Object.fromEntries(Group.all.map(g=>[g.name,g]));
 const cube=(group,name,from,to,mat=0)=>{
  const c=new Cube({name,from,to,autouv:0,box_uv:false}).addTo(groups[group]).init();
  for(const f of Object.values(c.faces)){f.texture=texture.uuid;const x=mat%4*32,y=Math.floor(mat/4)*32;f.uv=[x+6,y+6,x+26,y+25];}return c;
 };
 const bands=[[1.2,2.8,3.7,0],[2.8,4.4,5.3,2.7],[4.4,10.4,6,4.15],[10.4,12,5.3,2.7],[12,13.2,3.7,0]];
 function shell(group,prefix,y,Y,inset,innerInset,mat){
  bands.forEach(([z,Z,w,v],i)=>{
   const a=w-inset,b=v?Math.max(0,v-innerInset):0;
   if(!b)cube(group,prefix+'_'+i,[8-a,y,z],[8+a,Y,Z],mat);
   else for(const s of [-1,1])cube(group,prefix+'_'+i+'_'+s,[s<0?8-a:8+b,y,z],[s<0?8-b:8+a,Y,Z],mat);
  });
 }
 shell('bowl_outer','upper_bowl',5.65,7.5,0,0,0);
 shell('bowl_inner','lower_bowl',4.2,5.65,.65,.8,0);
 shell('bowl_outer','thin_rim',7.5,7.85,0,0,1);
 for(const [z,Z,w,v]of bands)if(v)cube('bowl_inner','cavity_floor_'+z,[8-v+.8,4.16,z],[8+v-.8,4.2,Z],2);
 bands.forEach(([z,Z,w,v],i)=>{
  const inside=i===0?1.35:v===0?0:v;
  if(!inside)cube('toilet_seat','seat_rear_'+i,[8-w,7.9,z],[8+w,8.25,Z],13);
  else for(const s of [-1,1])cube('toilet_seat','open_seat_'+i+'_'+s,[s<0?8-w:8+inside,7.9,z],[s<0?8-inside:8+w,8.25,Z],13);
 });
 for(const [i,z,Z,w]of [[0,3.2,4.7,2.7],[1,4.7,12.8,3.6],[2,12.8,14.5,3]]){
  cube('floor_base','thin_base_'+i,[8-w,0,z],[8+w,.55,Z],2);
  cube('pedestal','pedestal_main_'+i,[8-w+.3,.55,z],[8+w-.3,4.2,Z],0);
 }
 cube('rear_connector','rear_ceramic_bridge',[4.3,6.7,13.2],[11.7,7.9,15.3],0);
 cube('rear_connector','rear_neck',[5.3,4.16,13.2],[10.7,6.7,14.5],0);
 for(const x of [4.6,11]){
  cube('toilet_seat','seat_hinge_'+x,[x,7.85,12.7],[x+.4,8.02,13.5],9);
  cube('floor_base','anchor_'+x,[x,.55,11.5],[x+.4,.85,12],8);
 }
 Cube.selected.empty();Group.all.forEach(g=>g.selected=false);Canvas.updateAll();
 const path=base+'src/main/blockbench/commercial_flushometer_toilet.bbmodel';fs.writeFileSync(path,Codecs.project.compile());Project.save_path=path;Project.saved=true;
 return {oldCount,newCount:Cube.all.length,preservedMetalCubes:preserved.length,backup};
})()
