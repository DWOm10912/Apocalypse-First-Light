(()=>{
 const fs=require('fs'),base='D:/Minecraft Modding/Apocalypse First Light/';
 const original=JSON.parse(fs.readFileSync(base+'src/main/blockbench/previews/commercial_flushometer_toilet_before_subdivision.bbmodel'));
 const pg=Group.all.find(g=>g.name==='pedestal');
 for(const c of [...pg.children])if(c.name.includes('_subfacet_'))c.remove();
 for(const old of original.elements.filter(e=>e.name.startsWith('corner_chamfer_')&&e.from[1]>.1)){
  const center=old.origin,size=old.from.map((v,i)=>old.to[i]-v),s=center[0]<0?-1:1;
  const q=new THREE.Quaternion().setFromEuler(new THREE.Euler(0,s*Math.PI/4,0,'ZYX'));
  let start=new THREE.Vector3(-size[0]/2,0,0);
  for(let i=0;i<3;i++){
   const angle=(i===0?5:i===2?-5:0)*Math.PI/180;
   const dq=new THREE.Quaternion().setFromAxisAngle(new THREE.Vector3(0,1,0),angle),len=size[0]/3/Math.cos(angle);
   const step=new THREE.Vector3(len,0,0).applyQuaternion(dq),ctr=start.clone().addScaledVector(step,.5).applyQuaternion(q).add(new THREE.Vector3(...center));
   const c=new Cube({name:old.name+'_subfacet_'+i,from:[ctr.x-len/2,ctr.y-size[1]/2,ctr.z-size[2]/2],to:[ctr.x+len/2,ctr.y+size[1]/2,ctr.z+size[2]/2],origin:ctr.toArray(),rotation:[0,s*45+angle*180/Math.PI,0],autouv:0,box_uv:false}).addTo(pg).init();
   for(const [k,f]of Object.entries(old.faces)){c.faces[k].texture=f.texture===0?Texture.all[0].uuid:f.texture;c.faces[k].uv=[...f.uv];}
   if(i>0)c.faces.west.texture=null;if(i<2)c.faces.east.texture=null;
   start.add(step);
  }
 }
 // Three coplanar longitudinal base panels retain the exact approved base
 // envelope. This is topology-only subdivision, not another support tier.
 const c=Cube.all.find(c=>c.name==='base_long'),a=c.from[2],step=(c.to[2]-a)/3;
 for(let i=0;i<3;i++){
  const n=new Cube({name:'base_long_panel_'+i,from:[c.from[0],c.from[1],a+i*step],to:[c.to[0],c.to[1],a+(i+1)*step],origin:[...c.origin],rotation:[...c.rotation],autouv:0,box_uv:false}).addTo(c.parent).init();
  for(const [k,f]of Object.entries(c.faces)){n.faces[k].texture=f.texture;n.faces[k].uv=[...f.uv];}
  if(i>0)n.faces.north.texture=null;if(i<2)n.faces.south.texture=null;
 }c.remove();
 Cube.selected.empty();Canvas.updateAll();
 fs.writeFileSync(base+'src/main/blockbench/commercial_flushometer_toilet.bbmodel',Codecs.project.compile());Project.saved=true;return Cube.all.length;
})()
