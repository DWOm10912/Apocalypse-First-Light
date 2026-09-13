(async()=>{
 const fs=require('fs'),base='D:/Minecraft Modding/Apocalypse First Light/';
 const modelPath=base+'src/main/blockbench/commercial_flushometer_toilet.bbmodel';
 if(Project.name!=='commercial_flushometer_toilet')throw Error('Wrong active project');
 const t=Texture.all[0],inner=Group.all.find(g=>g.name==='bowl_inner');
 Cube.all.find(c=>c.name==='recessed_sump')?.remove();
 Cube.all.find(c=>c.name==='still_water')?.remove();
 const widths=[2.5,3.6,4.6,5.35,5.8,6,6,5.8,5.3,4.7,4,3.4];
 if(!Cube.all.some(c=>c.name==='cavity_floor_2'))for(let i=2;i<=9;i++){
  const w=widths[i]-3.1;
  const c=new Cube({name:'cavity_floor_'+i,from:[8-w,4.29,1.2+i],to:[8+w,4.32,2.2+i],autouv:0,box_uv:false}).addTo(inner).init();
  for(const f of Object.values(c.faces)){f.texture=t.uuid;f.uv=[6,38,26,57];}
 }
 for(const c of Cube.all){if(c.name.startsWith('cavity_floor_')){c.from[1]=4.29;c.to[1]=4.32;}}
 for(const c of Cube.all)for(const f of Object.values(c.faces)){
  const x=Math.floor(f.uv[0]/32)*32,y=Math.floor(f.uv[1]/32)*32;f.uv=[x+6,y+6,x+26,y+25];
 }
 fs.writeFileSync(modelPath,Codecs.project.compile());Project.saved=true;
 const production=Project;
 const dir=base+'docs/models/previews/commercial_flushometer_toilet/';fs.mkdirSync(dir,{recursive:true});
 async function capture(name,position,target=[8,8,8],zoom=1.2){
  const p=Preview.selected;p.loadAnglePreset({position,target,projection:'orthographic',zoom});p.controls.update();Canvas.updateAll();
  await new Promise(r=>setTimeout(r,150));
  await new Promise(resolve=>Screencam.screenshotPreview(p,{width:1000,height:1000},data=>{fs.writeFileSync(dir+name+'.png',Buffer.from(data.split(',')[1],'base64'));resolve();}));
 }
 for(const [name,pos]of [['angle',[40,30,-40]],['front',[8,13,-65]],['side',[70,12,8]],['back',[36,24,65]]])await capture(name,pos);
 const preview=JSON.parse(fs.readFileSync(modelPath,'utf8'));
 newProject(Formats.free);Codecs.project.parse(preview);Project.name='commercial_flushometer_toilet_wall_preview';
 const g=new Group({name:'preview_only_wall',export:false}).init();
 for(const [name,from,to]of [['wall',[-2,0,16],[18,20,17]],['floor',[-2,-.3,-2],[18,0,17]]]){
  const c=new Cube({name:'preview_'+name,from,to,export:false,autouv:0,box_uv:false}).addTo(g).init();
  for(const f of Object.values(c.faces)){f.texture=Texture.all[0].uuid;f.uv=[70,102,90,121];}
 }
 const pp=base+'src/main/blockbench/previews/commercial_flushometer_toilet_wall_preview.bbmodel';
 fs.mkdirSync(base+'src/main/blockbench/previews',{recursive:true});fs.writeFileSync(pp,Codecs.project.compile());Project.save_path=pp;Project.saved=true;
 await new Promise(r=>setTimeout(r,300));
 await capture('wall_installation',[42,29,-36],[8,9,8],1.0);
 await production.select();await capture('angle',[40,30,-40]);
 return {directory:dir,cubes:Cube.all.length,previewPath:pp};
})()
