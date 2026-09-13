(async()=>{
 const fs=require('fs'),base='D:/Minecraft Modding/Apocalypse First Light/',shell=Cube.all.filter(c=>c.name.startsWith('dry_shell_')||c.name==='closed_ceramic_bottom');
 if(shell.length!==61)throw Error('Expected closed dry shell');
 Canvas.updateAll();const meshes=shell.map(c=>{c.mesh.updateWorldMatrix(true,false);return c.mesh}),ray=new THREE.Raycaster();
 let rays=0;const misses=[];
 // Test the SHELL ALONE: exterior geometry cannot conceal missing faces.
 // Rays from multiple interior heights, 72 azimuths and downward elevations.
 for(let y=5.9;y<=7.91;y+=.2)for(let a=0;a<360;a+=5)for(let p=0;p>=-85;p-=5){
  const az=a*Math.PI/180,pitch=p*Math.PI/180,dir=new THREE.Vector3(Math.cos(az)*Math.cos(pitch),Math.sin(pitch),Math.sin(az)*Math.cos(pitch));
  ray.set(new THREE.Vector3(0,y,-.5),dir);rays++;if(!ray.intersectObjects(meshes,false).length)misses.push({y,a,p});
 }
 const report={scope:'isolated ceramic shell, exterior excluded',rays,misses};
 fs.writeFileSync(base+'docs/models/previews/commercial_flushometer_toilet/dry_shell_coverage.json',JSON.stringify(report,null,2));
 const p=Preview.selected,visibility=Cube.all.map(c=>[c.mesh,c.mesh.visible]);
 try{
  for(const c of Cube.all)c.mesh.visible=shell.includes(c);
  for(const [name,pos]of [['dry_shell_inside',[22,38,-28]],['dry_shell_outside',[22,-20,-28]]]){
   p.loadAnglePreset({position:pos,target:[0,6.8,-.5],projection:'orthographic',zoom:1.6});p.controls.update();
   await new Promise(r=>setTimeout(r,100));await new Promise(done=>Screencam.screenshotPreview(p,{width:1000,height:1000},data=>{fs.writeFileSync(base+'docs/models/previews/commercial_flushometer_toilet/'+name+'.png',Buffer.from(data.split(',')[1],'base64'));done();}));
  }
 }finally{for(const [mesh,v]of visibility)mesh.visible=v;Canvas.updateAll();}
 p.loadAnglePreset({position:[32,22,-40],target:[0,7.7,0],projection:'orthographic',zoom:1.1});p.controls.update();
 return {rays,misses:misses.length,examples:misses.slice(0,5)};
})()
