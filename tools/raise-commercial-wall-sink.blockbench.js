(function(){
 const fs=require('fs'),root='D:/Minecraft Modding/Apocalypse First Light/';
 if(Project.name!=='commercial_wall_mounted_sink'||Cube.all.length!==250)throw Error('Select the final sink');
 const highest=Math.max(...Cube.all.flatMap(c=>c.getGlobalVertexPositions().map(v=>v[1])));
 if(highest>16)throw Error('Sink already raised; refusing a second offset');
 Undo.initEdit({elements:Cube.all,outliner:Group.all});
 for(const c of Cube.all){c.from[1]+=5;c.to[1]+=5;c.origin[1]+=5;}
 for(const g of Group.all)g.origin[1]+=5;
 Undo.finishEdit('Raise complete wall sink by five model units');
 Canvas.updateAll();
 fs.writeFileSync(root+'src/main/blockbench/commercial_wall_mounted_sink.bbmodel',Codecs.project.compile());
 Project.saved=true;
 const vertices=Cube.all.flatMap(c=>c.getGlobalVertexPositions());
 const min=[0,1,2].map(i=>Math.min(...vertices.map(v=>v[i]))),max=[0,1,2].map(i=>Math.max(...vertices.map(v=>v[i])));
 return JSON.stringify({cubes:Cube.all.length,min,max,size:max.map((v,i)=>v-min[i]),rimTop:Cube.all.filter(c=>/^continuous_rim_tile_/.test(c.name)).reduce((m,c)=>Math.max(m,c.to[1]),0)});
})()
