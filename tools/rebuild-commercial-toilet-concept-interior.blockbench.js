(()=>{
 const fs=require('fs'),base='D:/Minecraft Modding/Apocalypse First Light/';
 if(Project.name!=='commercial_flushometer_toilet')throw Error('Select toilet');
 const backup=base+'src/main/blockbench/previews/commercial_flushometer_toilet_before_concept_interior.bbmodel';
 if(fs.existsSync(backup))throw Error('Backup exists');fs.writeFileSync(backup,Codecs.project.compile());
 const inner=Group.all.find(g=>g.name==='bowl_inner'),water=Group.all.find(g=>g.name==='bowl_water');
 for(const c of [...inner.children])c.remove();for(const c of [...water.children])c.remove();
 const liner=new Group({name:'continuous_ceramic_liner',origin:[0,0,0]}).addTo(inner).init();
 const tex=Texture.all[0];
 function box(g,name,center,size,mat,rot=[0,0,0],faceOnly=false){
  const c=new Cube({name,from:center.map((v,i)=>v-size[i]/2),to:center.map((v,i)=>v+size[i]/2),origin:center,rotation:rot,autouv:0,box_uv:false}).addTo(g).init();
  for(const [key,f]of Object.entries(c.faces)){f.texture=faceOnly&&key!=='south'?null:tex.uuid;const x=mat%4*32,y=Math.floor(mat/4)*32;f.uv=[x+8,y+8,x+24,y+24];}return c;
 }
 // Use the existing elongated rim's corner construction, not polar sampling.
 const raw=[[-1.5,-6],[1.5,-6],[3.25,-5.25],[4.25,-3.5],[4.25,3.25],[3,4.5],[-3,4.5],[-4.25,3.25],[-4.25,-3.5],[-3.25,-5.25]],poly=[];
 raw.forEach((v,i)=>{for(const q of [raw[(i+9)%10],raw[(i+1)%10]]){const d=Math.hypot(q[0]-v[0],q[1]-v[1]),t=Math.min(.34,d*.2)/d;poly.push([(v[0]+(q[0]-v[0])*t)*.82,-.5+(v[1]+(q[1]-v[1])*t+.5)*.83]);}});
 const levels=[{s:1,y:8.01},{s:.91,y:7.45},{s:.61,y:6.58},{s:.29,y:5.81}];
 for(let j=0;j<3;j++)for(let i=0;i<20;i++){
  const p=poly[i],q=poly[(i+1)%20],top=levels[j],bottom=levels[j+1];
  const point=(a,l)=>new THREE.Vector3(a[0]*l.s,l.y,-.5+(a[1]+.5)*l.s);
  const a=point(p,top),b=point(q,top),c=point(p,bottom),d=point(q,bottom);
  const u=b.clone().sub(a).normalize(),tc=a.clone().add(b).multiplyScalar(.5),bc=c.clone().add(d).multiplyScalar(.5);
  const rise=tc.clone().sub(bc),tangent=rise.dot(u);rise.addScaledVector(u,-tangent);
  const height=rise.length(),v=rise.normalize(),n=new THREE.Vector3().crossVectors(u,v).normalize();
  const matrix=new THREE.Matrix4().makeBasis(u,v,n),quat=new THREE.Quaternion().setFromRotationMatrix(matrix),e=new THREE.Euler().setFromQuaternion(quat,'ZYX');
  const center=tc.clone().add(bc).multiplyScalar(.5).addScaledVector(n,-.04);
  // Single inward face: rectangular continuation stays behind adjacent liner
  // panels, while non-facing cuboid sides cannot form visible pipes/spikes.
  const width=Math.max(a.distanceTo(b),c.distanceTo(d))+Math.abs(tangent)+.35;
  box(liner,'ceramic_liner_'+j+'_'+i,center.toArray(),[width,height+.075,.08],j===2?3:2,[e.x,e.y,e.z].map(v=>v*180/Math.PI),true);
 }
 box(water,'small_water_surface',[0,5.785,-.5],[2.35,.03,2.85],6);
 Cube.selected.empty();Group.all.forEach(g=>g.selected=false);Canvas.updateAll();
 fs.writeFileSync(base+'src/main/blockbench/commercial_flushometer_toilet.bbmodel',Codecs.project.compile());Project.saved=true;
 return {count:Cube.all.length,liner:60,water:1,backup};
})()
