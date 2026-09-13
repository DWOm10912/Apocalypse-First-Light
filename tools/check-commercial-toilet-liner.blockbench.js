(async()=>{
 const fs=require('fs'),base='D:/Minecraft Modding/Apocalypse First Light/';
 if(Project.name!=='commercial_flushometer_toilet')throw Error('Wrong project');
 const polygon=[[-1.5,-6],[1.5,-6],[3.25,-5.25],[4.25,-3.5],[4.25,3.25],[3,4.5],[-3,4.5],[-4.25,3.25],[-4.25,-3.5],[-3.25,-5.25]];
 const inside=(x,z)=>{let c=false;for(let i=0,j=9;i<10;j=i++)if((polygon[i][1]>z)!==(polygon[j][1]>z)&&x<(polygon[j][0]-polygon[i][0])*(z-polygon[i][1])/(polygon[j][1]-polygon[i][1])+polygon[i][0])c=!c;return c;};
 Canvas.updateAll();const meshes=Cube.all.map(c=>{c.mesh.updateWorldMatrix(true,false);return c.mesh}),names=new Map(Cube.all.map(c=>[c.mesh,c.name])),ray=new THREE.Raycaster();
 const report={samples:0,topHoles:[],bottomHoles:[],visibleStructure:[]};
 for(let iz=0;iz<105;iz++)for(let ix=0;ix<85;ix++){
  const x=-4.2+ix*.1,z=-5.95+iz*.1;if(!inside(x,z))continue;report.samples++;
  ray.set(new THREE.Vector3(x,20,z),new THREE.Vector3(0,-1,0));let hit=ray.intersectObjects(meshes,false)[0];
  if(!hit)report.topHoles.push([x,z]);else if(!/liner|rim|seat|water|hinge|rear_|socket|pipe|valve|cap|feed|flange|lever|highlight/.test(names.get(hit.object)))report.visibleStructure.push({x,z,name:names.get(hit.object)});
  ray.set(new THREE.Vector3(x,-3,z),new THREE.Vector3(0,1,0));hit=ray.intersectObjects(meshes,false)[0];if(!hit)report.bottomHoles.push([x,z]);
 }
 fs.writeFileSync(base+'docs/models/previews/commercial_flushometer_toilet/liner_coverage.json',JSON.stringify(report,null,2));
 const p=Preview.selected;
 async function shot(name,position){p.loadAnglePreset({position,target:[0,6.5,-.4],projection:'orthographic',zoom:1.2});p.controls.update();Canvas.updateAll();await new Promise(r=>setTimeout(r,150));await new Promise(done=>Screencam.screenshotPreview(p,{width:1000,height:1000},data=>{fs.writeFileSync(base+'docs/models/previews/commercial_flushometer_toilet/'+name+'.png',Buffer.from(data.split(',')[1],'base64'));done();}));}
 await shot('bottom_seal',[0,-60,.001]);await shot('top_seal',[0,65,-.001]);
 p.loadAnglePreset({position:[32,22,-40],target:[0,7.7,0],projection:'orthographic',zoom:1.1});p.controls.update();
 return {samples:report.samples,topHoles:report.topHoles.length,bottomHoles:report.bottomHoles.length,visibleStructure:report.visibleStructure.slice(0,12)};
})()
