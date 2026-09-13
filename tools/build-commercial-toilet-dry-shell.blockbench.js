(()=>{
 const fs=require('fs'),base='D:/Minecraft Modding/Apocalypse First Light/';
 if(Project.name!=='commercial_flushometer_toilet')throw Error('Select toilet');
 const backup=base+'src/main/blockbench/previews/commercial_flushometer_toilet_before_closed_dry_shell.bbmodel';
 if(!fs.existsSync(backup))fs.writeFileSync(backup,Codecs.project.compile());
 const inner=Group.all.find(g=>g.name==='bowl_inner'),water=Group.all.find(g=>g.name==='bowl_water');
 if(inner.children.some(c=>c.name==='closed_ceramic_shell'))throw Error('Dry shell already exists');
 const templates=JSON.parse(fs.readFileSync(backup)).elements.filter(c=>c.name.startsWith('ceramic_liner_')).map(c=>({name:c.name,from:[...c.from],to:[...c.to],origin:[...c.origin],rotation:[...c.rotation],uv:[...c.faces.south.uv]}));
 if(templates.length!==60)throw Error('Expected sixty approved surface templates');
 for(const c of [...inner.children])c.remove();if(water)for(const c of [...water.children])c.remove();
 const g=new Group({name:'closed_ceramic_shell',origin:[0,0,0]}).addTo(inner).init(),texture=Texture.all[0];
 for(const t of templates){
  const layer=Number(t.name.split('_')[2]),centerY=(t.from[1]+t.to[1])/2;
  const bareHeight=t.to[1]-t.from[1]-(layer===0?.12:.075),height=bareHeight+.06;
  // Preserve the approved inward surface. Add material OUTWARD from that
  // surface, with 0.03-unit end overlap and a true 0.30-unit wall thickness.
  const from=[t.from[0],centerY-height/2,t.to[2]-.3],to=[t.to[0],centerY+height/2,t.to[2]];
  const c=new Cube({name:t.name.replace('ceramic_liner','dry_shell'),from,to,origin:t.origin,rotation:t.rotation,autouv:0,box_uv:false}).addTo(g).init();
  for(const f of Object.values(c.faces)){f.texture=texture.uuid;f.uv=[...t.uv];}
 }
 const bottom=new Cube({name:'closed_ceramic_bottom',from:[-1.175,5.48,-1.925],to:[1.175,5.83,.925],origin:[0,5.655,-.5],rotation:[0,0,0],autouv:0,box_uv:false}).addTo(g).init();
 for(const f of Object.values(bottom.faces)){f.texture=texture.uuid;f.uv=[72,8,88,24];}
 Cube.selected.empty();Canvas.updateAll();fs.writeFileSync(base+'src/main/blockbench/commercial_flushometer_toilet.bbmodel',Codecs.project.compile());Project.saved=true;
 return {count:Cube.all.length,shellCubes:61,wallThickness:.3,axialEndOverlap:.03,bottomThickness:.35,water:0,backup};
})()
