(()=>{
 const fs=require('fs');
 // The original solid pedestal caps must sit below the new dry basin.
 // Otherwise a hidden support face occludes the drain and recreates a flat pan.
 for(const c of Cube.all){
  if(c.parent?.name==='pedestal')c.to[1]=Math.min(c.to[1],4.25);
  if(c.name==='throat_solid_core'){c.from[1]=3.8;c.to[1]=4.42;}
 }
 Cube.selected.empty();Canvas.updateAll();fs.writeFileSync(Project.save_path,Codecs.project.compile());Project.saved=true;return 'Pedestal cap below drain';
})()
