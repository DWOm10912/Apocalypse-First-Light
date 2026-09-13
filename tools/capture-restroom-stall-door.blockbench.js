(async()=>{
  const fs=require('fs'),base='D:/Minecraft Modding/Apocalypse First Light/';
  const dir=base+'docs/models/previews/restroom_stall_door/';fs.mkdirSync(dir,{recursive:true});
  const door=ModelProject.all.find(p=>p.name==='restroom_stall_door');
  if(!door)throw Error('Door project not loaded');
  await door.select();
  const capture=async(name,position,target=[8,16.5,8],zoom=.62)=>{
    const p=Preview.selected;p.setProjectionMode(true);
    p.controls.target.set(...target);p.camera.position.set(...position);p.camera.zoom=zoom;
    p.camera.updateProjectionMatrix();p.controls.update();Canvas.updateAll();
    await new Promise(r=>setTimeout(r,150));
    await new Promise(resolve=>Screencam.screenshotPreview(p,{width:1000,height:1000},data=>{
      fs.writeFileSync(dir+name+'.png',Buffer.from(data.split(',')[1],'base64'));resolve();
    }));
  };
  for(const [name,pos] of [['front',[8,16.5,-80]],['back',[8,16.5,96]],['side',[90,16.5,8]],['angle',[65,35,-65]]])await capture(name,pos);
  const path=base+'src/main/blockbench/previews/restroom_stall_door_installation_preview.bbmodel';
  newProject(Formats.free);Codecs.project.parse(JSON.parse(fs.readFileSync(path,'utf8')),path);
  Project.save_path=path;Project.saved=true;
  await capture('installation',[58,35,-70]);
  await capture('installation_front',[8,16,-80]);
  const leaf=Group.all.find(g=>g.name==='door_leaf');leaf.rotation[1]=-90;Canvas.updateAll();
  await capture('pivot_check_90deg',[58,40,-65]);
  leaf.rotation[1]=0;Canvas.updateAll();Project.saved=true;
  await door.select();await capture('angle',[65,35,-65]);
  return {directory:dir,screenshots:7,closedRotation:Group.all.find(g=>g.name==='door_leaf').rotation};
})()
