(async()=>{
 const fs=require('fs'),base='D:/Minecraft Modding/Apocalypse First Light/';
 if(Project.name!=='commercial_flushometer_toilet')throw Error('Wrong project');
 const production=Project,dir=base+'docs/models/previews/commercial_flushometer_toilet/';
 async function shot(n,position,target=[0,7.7,0],zoom=1.1){const p=Preview.selected;p.loadAnglePreset({position,target,projection:'orthographic',zoom});p.controls.update();Canvas.updateAll();await new Promise(r=>setTimeout(r,150));await new Promise(done=>Screencam.screenshotPreview(p,{width:1000,height:1000},data=>{fs.writeFileSync(dir+n+'.png',Buffer.from(data.split(',')[1],'base64'));done();}));}
 for(const [n,pos]of [['angle',[32,22,-40]],['front',[0,10,-65]],['side',[65,10,0]],['back',[32,24,65]],['top',[0,65,-.001]],['cavity_check',[20,48,-25]]])await shot(n,pos);
 const path=base+'src/main/blockbench/commercial_flushometer_toilet.bbmodel';
 newProject(Formats.free);Codecs.project.parse(JSON.parse(fs.readFileSync(path,'utf8')));Project.name='commercial_flushometer_toilet_wall_preview';
 const g=new Group({name:'preview_only_wall',export:false}).init();
 for(const [name,from,to]of [['wall',[-10,0,8],[10,20,9]],['floor',[-10,-.3,-10],[10,0,9]]]){const c=new Cube({name:'preview_'+name,from,to,export:false,autouv:0,box_uv:false}).addTo(g).init();for(const f of Object.values(c.faces)){f.texture=Texture.all[0].uuid;f.uv=[70,102,90,121];}}
 const pp=base+'src/main/blockbench/previews/commercial_flushometer_toilet_wall_preview.bbmodel';fs.writeFileSync(pp,Codecs.project.compile());Project.save_path=pp;Project.saved=true;await new Promise(r=>setTimeout(r,200));await shot('wall_installation',[35,25,-40],[0,9,0],.95);
 await production.select();await shot('angle',[32,22,-40]);return '7 current previews saved';
})()
