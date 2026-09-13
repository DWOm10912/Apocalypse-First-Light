(()=>{
 const fs=require('fs'),base='D:/Minecraft Modding/Apocalypse First Light/';
 if(Project.name!=='commercial_flushometer_toilet')throw Error('Wrong project');
 const g=Group.all.find(g=>g.name==='continuous_ceramic_throat'),t=Texture.all[0];
 for(const c of [...g.children])if(/^sealed_front_/.test(c.name))c.remove();
 function box(name,center,size,rot=[0,0,0]){const c=new Cube({name,from:center.map((v,i)=>v-size[i]/2),to:center.map((v,i)=>v+size[i]/2),origin:center,rotation:rot,autouv:0,box_uv:false}).addTo(g).init();for(const f of Object.values(c.faces)){f.texture=t.uuid;f.uv=[8,8,24,24];}return c;}
 // Joined front return: broad chamfered prism without exposed compound-angle
 // end caps. All parts overlap the saddle and the side cheeks.
 box('sealed_front_core',[0,5.85,-4.5],[4.4,1.3,2.4]);
 for(const s of [-1,1])box('sealed_front_chamfer_'+s,[s*2.2,5.85,-4.3],[2.4,1.3,2.4],[0,45,0]);
 const saddle=Cube.all.find(c=>c.name==='sealed_saddle');saddle.from[2]=-3.4;
 const water=Cube.all.find(c=>c.name==='small_water_surface');
 if(water){water.name='dry_ceramic_sump';water.from[1]=5.64;water.to[1]=5.8;for(const f of Object.values(water.faces)){f.texture=t.uuid;f.uv=[72,8,88,24];}water.addTo(Group.all.find(g=>g.name==='bowl_inner'));}
 Cube.selected.empty();Canvas.updateAll();fs.writeFileSync(base+'src/main/blockbench/commercial_flushometer_toilet.bbmodel',Codecs.project.compile());Project.saved=true;
 return {count:Cube.all.length,waterRemoved:true};
})()
