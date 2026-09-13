(function(){
 const fs=require('fs'),root='D:/Minecraft Modding/Apocalypse First Light/';
 if(Project.name!=='commercial_wall_mounted_sink')throw Error('Select sink');
 const supports=Cube.all.filter(c=>/^wall_bracket_/.test(c.name));
 if(supports.length!==2)throw Error('Expected two wall brackets');
 Undo.initEdit({elements:supports});
 // The ceramic rear face stays at Z=8. Recess only the metal rear faces.
 for(const c of supports)c.to[2]=7.96;
 Undo.finishEdit('Separate sink bracket rear faces from ceramic back');
 Canvas.updateAll();
 fs.writeFileSync(root+'src/main/blockbench/commercial_wall_mounted_sink.bbmodel',Codecs.project.compile());
 Project.saved=true;
 const vertices=Cube.all.flatMap(c=>c.getGlobalVertexPositions());
 const min=[0,1,2].map(i=>Math.min(...vertices.map(v=>v[i])));
 const max=[0,1,2].map(i=>Math.max(...vertices.map(v=>v[i])));
 return JSON.stringify({cubes:Cube.all.length,min,max,size:max.map((v,i)=>v-min[i]),invalidDimensions:Cube.all.filter(c=>c.to.some((v,i)=>v<=c.from[i])).map(c=>c.name),supports:supports.map(c=>({name:c.name,rear:c.to[2]}))});
})()
