(async()=>{
 const fs=require('fs'),base='D:/Minecraft Modding/Apocalypse First Light/docs/models/previews/commercial_flushometer_toilet/';
 const p=Preview.selected;
 for(const [name,position]of [['low_front',[22,-8,-35]],['low_rear',[22,-8,35]],['low_left',[-40,2,0]],['low_right',[40,2,0]],['low_front_center',[0,1,-50]],['low_rear_center',[0,1,50]]]){
  p.loadAnglePreset({position,target:[0,4.9,0],projection:'orthographic',zoom:1.5});p.controls.update();Canvas.updateAll();
  await new Promise(r=>setTimeout(r,100));await new Promise(done=>Screencam.screenshotPreview(p,{width:1000,height:1000},data=>{fs.writeFileSync(base+name+'.png',Buffer.from(data.split(',')[1],'base64'));done();}));
 }
 return 'six low angle views';
})()
