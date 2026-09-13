(async()=>{
 const fs=require('fs'),base='D:/Minecraft Modding/Apocalypse First Light/';
 if(Project.name!=='commercial_flushometer_toilet')throw Error('Select production toilet');
 const backup=base+'src/main/blockbench/previews/commercial_flushometer_toilet_before_subdivision.bbmodel';
 if(fs.existsSync(backup))throw Error('Subdivision backup exists');
 fs.writeFileSync(backup,Codecs.project.compile());
 const root=Group.all.find(g=>g.name==='commercial_toilet_root');
 const tex=Texture.all[0];
 const source=[...Cube.all];
 function box(parent,name,center,size,mat,rot=[0,0,0]){
  const c=new Cube({name,from:center.map((v,i)=>v-size[i]/2),to:center.map((v,i)=>v+size[i]/2),origin:center,rotation:rot,autouv:0,box_uv:false}).addTo(parent).init();
  for(const f of Object.values(c.faces)){f.texture=tex.uuid;const x=mat%4*32,y=Math.floor(mat/4)*32;f.uv=[x+8,y+8,x+24,y+24];}return c;
 }
 // Corner-cut subdivision of the approved ten-point outline. This is a
 // longitudinal chamfered outline, not a polar/radius-sampled circle.
 const p=[[-1.5,-6],[1.5,-6],[3.25,-5.25],[4.25,-3.5],[4.25,3.25],[3,4.5],[-3,4.5],[-4.25,3.25],[-4.25,-3.5],[-3.25,-5.25]];
 const outline=[];
 p.forEach((v,i)=>{
  const a=p[(i+9)%10],b=p[(i+1)%10];
  for(const q of [a,b]){const d=Math.hypot(q[0]-v[0],q[1]-v[1]),t=Math.min(.34,d*.2)/d;outline.push([v[0]+(q[0]-v[0])*t,v[1]+(q[1]-v[1])*t]);}
 });
 for(const c of source)if(/^(rim_main_facet_|seat_\d|crown_)/.test(c.name))c.remove();
 function strip(group,prefix,y,h,w,mat,open=false){
  outline.forEach((a,i)=>{
   const b=outline[(i+1)%20],x=(a[0]+b[0])/2,z=(a[1]+b[1])/2;
   if(open&&z< -5.9)return;
   const len=Math.hypot(b[0]-a[0],b[1]-a[1]),ang=-Math.atan2(b[1]-a[1],b[0]-a[0])*180/Math.PI;
   box(Group.all.find(g=>g.name===group),prefix+i,[x,y+Math.abs(x)*.002,z],[len+.19,h,w],mat,[0,ang,0]);
  });
 }
 strip('bowl_rim','rim_subfacet_',7.7,.8,1.05,1);
 strip('open_front_seat','seat_subfacet_',8.38,.55,1.22,13,true);
 strip('bowl_shell','crown_subfacet_',6.95,1.15,.95,0);
 // Split each existing sloping plate along its height into three connected
 // cuboids. +/- 5 degrees adds only a shallow local bend; endpoints stay fixed.
 function bend(c,axis,sign=1){
  const center=c.from.map((v,i)=>(v+c.to[i])/2),size=c.from.map((v,i)=>c.to[i]-v);
  let originalRot=[...c.rotation];
  if(/underbody_side_bevel/.test(c.name))originalRot=[0,0,center[0]<0?-45:45];
  const e=new THREE.Euler(...originalRot.map(v=>v*Math.PI/180),'ZYX'),q=new THREE.Quaternion().setFromEuler(e);
  let start=new THREE.Vector3(0,-size[1]/2,0);
  for(let i=0;i<3;i++){
   const angle=(i===0?5:i===2?-5:0)*sign*Math.PI/180;
   const dq=new THREE.Quaternion().setFromAxisAngle(axis==='x'?new THREE.Vector3(1,0,0):new THREE.Vector3(0,0,1),angle);
   const length=size[1]/3/Math.cos(angle),step=new THREE.Vector3(0,length,0).applyQuaternion(dq);
   const ctr=start.clone().addScaledVector(step,.5).applyQuaternion(q).add(new THREE.Vector3(...center));
   const nq=q.clone().multiply(dq),nr=new THREE.Euler().setFromQuaternion(nq,'ZYX');
   const n=box(c.parent,c.name+'_transition_'+i,ctr.toArray(),[size[0],length,size[2]],0,[nr.x,nr.y,nr.z].map(v=>v*180/Math.PI));
   for(const [key,f]of Object.entries(c.faces)){n.faces[key].texture=f.texture;n.faces[key].uv=[...f.uv];}
   // Internal slice caps are hidden; their continuous shared seam has no
   // duplicate coplanar visible face.
   if(i>0)n.faces.down.texture=null;if(i<2)n.faces.up.texture=null;
   start.add(step);
  }
  c.remove();
 }
 for(const c of source){
  if(/^cavity_/.test(c.name))bend(c,c.name.includes('side')?'z':'x',c.name.includes('side')?(c.origin[0]<0?-1:1):1);
  else if(/underbody_side_bevel/.test(c.name))bend(c,'z',c.origin[0]<0?-1:1);
  else if(c.name==='nose_under_chamfer')bend(c,'x');
 }
 // Subdivide the four rectangular pedestal corner chamfers across their
 // horizontal breadth; do not create a circular support or another base.
 for(const c of source.filter(c=>c.parent?.name==='pedestal'&&c.name.startsWith('corner_chamfer_'))){
  const ctr=c.origin,size=c.from.map((v,i)=>c.to[i]-v),s=ctr[0]<0?-1:1;
  const q=new THREE.Quaternion().setFromEuler(new THREE.Euler(0,s*Math.PI/4,0,'ZYX'));
  for(let i=0;i<3;i++){
   const v=new THREE.Vector3((i-1)*size[0]/3,0,0).applyQuaternion(q).add(new THREE.Vector3(...ctr));
   box(c.parent,c.name+'_subfacet_'+i,v.toArray(),[size[0]/3,size[1],size[2]],0,[0,s*45,0]);
  }c.remove();
 }
 // Upper edge of each crown facet gets a narrow bevel rather than another
 // full-height horizontal shell course. Bounds remain inside the old rim.
 const bevelGroup=new Group({name:'rim_edge_chamfers',origin:[0,0,0]}).addTo(Group.all.find(g=>g.name==='bowl_rim')).init();
 outline.forEach((a,i)=>{
  const b=outline[(i+1)%20],x=(a[0]+b[0])/2,z=(a[1]+b[1])/2,ang=-Math.atan2(b[1]-a[1],b[0]-a[0]);
  const offset=.47;
  // The local -Z edge is outside for this winding.
  box(bevelGroup,'rim_edge_'+i,[x-Math.sin(ang)*offset,7.99+Math.abs(x)*.002,z-Math.cos(ang)*offset],[Math.hypot(b[0]-a[0],b[1]-a[1])+.1,.16,.16],1,[45,ang*180/Math.PI,0]);
 });
 Cube.selected.empty();Group.all.forEach(g=>g.selected=false);Canvas.updateAll();
 const path=base+'src/main/blockbench/commercial_flushometer_toilet.bbmodel';fs.writeFileSync(path,Codecs.project.compile());Project.save_path=path;Project.saved=true;
 return {before:source.length,after:Cube.all.length,outlineFacets:20,backup};
})()
