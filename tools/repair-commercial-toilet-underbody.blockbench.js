(async()=>{
 const fs=require('fs'),base='D:/Minecraft Modding/Apocalypse First Light/';
 if(Project.name!=='commercial_flushometer_toilet')throw Error('Select production toilet');
 const backup=base+'src/main/blockbench/previews/commercial_flushometer_toilet_before_underbody_repair.bbmodel';
 if(fs.existsSync(backup))throw Error('Backup exists');fs.writeFileSync(backup,Codecs.project.compile());
 const shell=Group.all.find(g=>g.name==='bowl_shell'),throat=Group.all.find(g=>g.name==='continuous_ceramic_throat'),ped=Group.all.find(g=>g.name==='pedestal');
 const removed=[];
 for(const c of [...shell.children])if(c instanceof Cube&&!c.name.startsWith('crown_subfacet_')){removed.push(c.name);c.remove();}
 for(const c of [...throat.children]){removed.push(c.name);c.remove();}
 const tex=Texture.all[0];
 function box(name,center,size,rot=[0,0,0]){
  const c=new Cube({name,from:center.map((v,i)=>v-size[i]/2),to:center.map((v,i)=>v+size[i]/2),origin:center,rotation:rot,autouv:0,box_uv:false}).addTo(throat).init();
  for(const f of Object.values(c.faces)){f.texture=tex.uuid;f.uv=[8,8,24,24];}return c;
 }
 // Extend exactly the existing chamfered pedestal cross-section. No floating
 // angled brackets: every extension starts on the old pedestal's top plane.
 for(const c of ped.children.filter(c=>c instanceof Cube)){
  const from=[...c.from],to=[...c.to];from[1]=c.to[1];to[1]=5.52;
  const n=new Cube({name:'sealed_neck_'+c.name,from,to,origin:[...c.origin],rotation:[...c.rotation],autouv:0,box_uv:false}).addTo(throat).init();
  for(const [k,f]of Object.entries(c.faces)){n.faces[k].texture=f.texture;n.faces[k].uv=[...f.uv];}n.faces.down.texture=null;
 }
 // A filled under-basin saddle remains below the existing water and liner.
 // Overlapping solid bevels connect it directly to the unchanged crown.
 box('sealed_saddle',[0,4.88,.45],[4.7,1.28,6.55]);
 for(const s of [-1,1]){
  box('sealed_side_cheek_'+s,[s*3.15,5.38,-.1],[1.3,3.05,7.6],[0,0,-s*45]);
  box('sealed_front_corner_'+s,[s*2.35,5.45,-4.15],[3.2,2.5,1.5],[-45,-s*35,0]);
 }
 box('sealed_front_return',[0,5.4,-4.55],[3.65,2.65,1.5],[-45,0,0]);
 box('sealed_rear_return',[0,5.55,3.95],[5.6,2.05,1.9]);
 Cube.selected.empty();Group.all.forEach(g=>g.selected=false);Canvas.updateAll();
 fs.writeFileSync(base+'src/main/blockbench/commercial_flushometer_toilet.bbmodel',Codecs.project.compile());Project.saved=true;
 return {count:Cube.all.length,removed,backup};
})()
