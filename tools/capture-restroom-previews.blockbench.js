(async () => {
 const fs=require('fs'),base='D:/Minecraft Modding/Apocalypse First Light/';
 const dir=base+'docs/models/previews/restroom_partition/';fs.mkdirSync(dir,{recursive:true});
 const shots=[['front',[8,16,-80]],['back',[8,16,96]],['side',[96,16,8]],['angle',[58,35,-66]]];
 const capture=async(name,position,target=[8,16,8])=>{
  const p=Preview.selected;p.controls.target.set(...target);p.camera.position.set(...position);p.camera.zoom=name==='U_cubicle'?.25:.4;p.camera.updateProjectionMatrix();p.controls.update();Canvas.updateAll();
  await new Promise(r=>setTimeout(r,150));
  await new Promise(resolve=>Screencam.screenshotPreview(p,{width:1000,height:1000},data=>{fs.writeFileSync(dir+name+'.png',Buffer.from(data.split(',')[1],'base64'));resolve();}));
 };
 const single=ModelProject.all.find(p=>p.name==='restroom_partition');await single.select();await new Promise(r=>setTimeout(r,200));
 for(const [name,pos] of shots)await capture(name,pos);
 for(const [scenario,target,position] of [
  ['straight_2',[48,16,0],[95,38,-75]],
  ['corner_L',[8,16,72],[66,45,22]],
  ['U_cubicle',[64,16,72],[126,65,-10]]
 ]){
  const path=base+'src/main/blockbench/previews/restroom_preview_'+scenario+'.bbmodel';
  newProject(Formats.java_block);Codecs.project.parse(JSON.parse(fs.readFileSync(path,'utf8')),path);Project.save_path=path;Project.saved=true;
  Preview.selected.setProjectionMode(true);
  await capture(scenario,position,target);
 }
 single.select();await capture('angle',[58,35,-66]);
 return {screenshots:7,directory:dir};
})()
