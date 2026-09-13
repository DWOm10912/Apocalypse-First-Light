(async()=>{
 const fs=require('fs'),base='D:/Minecraft Modding/Apocalypse First Light/';
 if(Project.name!=='commercial_flushometer_toilet')throw Error('Wrong project');
 for(const [i,z,Z]of [[0,3.2,4.7],[1,4.7,12.8],[2,12.8,14.5]]){
  const c=Cube.all.find(c=>c.name==='pedestal_main_'+i);c.from[2]=z-8;c.to[2]=Z-8;c.to[1]=4.1;
 }
 const path=base+'src/main/blockbench/commercial_flushometer_toilet.bbmodel';fs.writeFileSync(path,Codecs.project.compile());Project.saved=true;
 const production=Project,dir=base+'docs/models/previews/commercial_flushometer_toilet/';
 async function shot(name,position,target=[8,8,8],zoom=1.15){
  position=position.map((v,i)=>i===1?v:v-8);target=target.map((v,i)=>i===1?v:v-8);
  const p=Preview.selected;p.loadAnglePreset({position,target,projection:'orthographic',zoom});p.controls.update();Canvas.updateAll();await new Promise(r=>setTimeout(r,150));
  await new Promise(done=>Screencam.screenshotPreview(p,{width:1000,height:1000},data=>{fs.writeFileSync(dir+name+'.png',Buffer.from(data.split(',')[1],'base64'));done();}));
 }
 for(const [n,pos]of [['angle',[40,30,-40]],['front',[8,10,-65]],['side',[70,10,8]],['back',[35,24,65]],['top',[8,75,7.99]],['cavity_check',[24,48,-14]]])await shot(n,pos);
 newProject(Formats.free);Codecs.project.parse(JSON.parse(fs.readFileSync(path,'utf8')));Project.name='commercial_flushometer_toilet_wall_preview';
 const g=new Group({name:'preview_only_wall',export:false}).init();
 for(const [n,f,t]of [['wall',[-10,0,8],[10,20,9]],['floor',[-10,-.3,-10],[10,0,9]]]){const c=new Cube({name:'preview_'+n,from:f,to:t,export:false,autouv:0,box_uv:false}).addTo(g).init();for(const face of Object.values(c.faces)){face.texture=Texture.all[0].uuid;face.uv=[70,102,90,121];}}
 const pp=base+'src/main/blockbench/previews/commercial_flushometer_toilet_wall_preview.bbmodel';fs.writeFileSync(pp,Codecs.project.compile());Project.save_path=pp;Project.saved=true;await new Promise(r=>setTimeout(r,200));await shot('wall_installation',[42,29,-36],[8,9,8],1);
 await production.select();await shot('angle',[40,30,-40]);return 'captures saved';
})()
