(()=>{
 const fs=require('fs'),base='D:/Minecraft Modding/Apocalypse First Light/';
 const old=JSON.parse(fs.readFileSync(base+'src/main/blockbench/previews/commercial_flushometer_toilet_before_subdivision.bbmodel'));
 for(const c of Cube.all){
  if(c.name.startsWith('rim_edge_')){
   const theta=c.rotation[1]*Math.PI/180;
   const dx=Math.sin(theta)*.005,dz=Math.cos(theta)*.005;
   for(const v of [c.from,c.to,c.origin]){v[0]+=dx;v[2]+=dz;}
  }
  if(c.name.includes('_transition_')){
   const src=old.elements.find(e=>e.name===c.name.replace(/_transition_\d$/,''));
   for(const k of ['up','down']){c.faces[k].texture=Texture.all[0].uuid;c.faces[k].uv=[...src.faces[k].uv];}
   c.from[1]-=.012;c.to[1]+=.012;
  }
 }
 // Keep the pre-subdivision front extent and overall width exactly;
 // extra facets refine corners, not scale the assembly.
 const front=Cube.all.find(c=>c.name==='rim_subfacet_1');
 if(Math.abs(front.rotation[1])>1e-6)throw Error('Expected straight front facet');
 front.from[2]=-6.598100149871586;
 Cube.selected.empty();Canvas.updateAll();
 fs.writeFileSync(base+'src/main/blockbench/commercial_flushometer_toilet.bbmodel',Codecs.project.compile());Project.saved=true;
 return 'Final boundary and seam check';
})()
