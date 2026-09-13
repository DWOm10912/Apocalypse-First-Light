(async()=>{
 const fs=require('fs'),dir='D:/Minecraft Modding/Apocalypse First Light/docs/models/previews/commercial_wall_mounted_sink/';fs.mkdirSync(dir,{recursive:true});
 if(Project.name!=='commercial_wall_mounted_sink')throw Error('Select sink');
 const p=Preview.selected;
 for(const [name,position]of Object.entries({angle:[27,29,-34],front:[0,17,-45],side:[45,17,0],top:[0,53,2],underside:[22,-7,-28],back:[22,20,38]})){
 p.loadAnglePreset({position,target:[0,12.4,2.7],projection:'orthographic',zoom:1.1});p.controls.update();Canvas.updateAll();await new Promise(r=>setTimeout(r,100));
 await new Promise(r=>Screencam.screenshotPreview(p,{width:1000,height:1000},data=>{fs.writeFileSync(dir+name+'.png',Buffer.from(data.split(',')[1],'base64'));r()}));
 }
 p.loadAnglePreset({position:[27,29,-34],target:[0,12.4,2.7],projection:'orthographic',zoom:1.1});p.controls.update();return dir;
})()
