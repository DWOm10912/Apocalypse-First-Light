(()=>{
 const fs=require('fs'),base='D:/Minecraft Modding/Apocalypse First Light/';
 if(Project.name!=='commercial_flushometer_toilet')throw Error('Select toilet');
 const backup=base+'src/main/blockbench/previews/commercial_flushometer_toilet_before_liner_seal.bbmodel';
 if(fs.existsSync(backup))throw Error('Backup exists');fs.writeFileSync(backup,Codecs.project.compile());
 const raw=[[-1.5,-6],[1.5,-6],[3.25,-5.25],[4.25,-3.5],[4.25,3.25],[3,4.5],[-3,4.5],[-4.25,3.25],[-4.25,-3.5],[-3.25,-5.25]],p=[];
 raw.forEach((v,i)=>{for(const q of [raw[(i+9)%10],raw[(i+1)%10]]){const d=Math.hypot(q[0]-v[0],q[1]-v[1]),t=Math.min(.34,d*.2)/d;p.push([v[0]+(q[0]-v[0])*t,v[1]+(q[1]-v[1])*t]);}});
 const edges=p.map((a,i)=>{const b=p[(i+1)%20],dx=b[0]-a[0],dz=b[1]-a[1],l=Math.hypot(dx,dz);return {n:[-dz/l,dx/l],a};});
 // Intersect the neighboring edge-offset lines. Unlike uniform scaling,
 // this keeps a constant overlap under the rim along sides AND front bevels.
 const top=p.map((v,i)=>{const a=edges[(i+19)%20],b=edges[i],offset=.37,A=a.n,B=b.n,ca=A[0]*v[0]+A[1]*v[1]+offset,cb=B[0]*v[0]+B[1]*v[1]+offset,det=A[0]*B[1]-A[1]*B[0];return [(ca*B[1]-A[1]*cb)/det,(A[0]*cb-ca*B[0])/det];});
 const tex=Texture.all[0],group=Group.all.find(g=>g.name==='continuous_ceramic_liner');
 for(let i=0;i<20;i++){
  const index=(i+1)%20,point=(a)=>new THREE.Vector3(a[0]*.82*.91,7.45,-.5+(a[1]+.5)*.83*.91);
  const a=new THREE.Vector3(top[i][0],8.04,top[i][1]),b=new THREE.Vector3(top[index][0],8.04,top[index][1]),c=point(p[i]),d=point(p[index]);
  const u=b.clone().sub(a).normalize(),tc=a.clone().add(b).multiplyScalar(.5),bc=c.clone().add(d).multiplyScalar(.5),rise=tc.clone().sub(bc),tangent=rise.dot(u);rise.addScaledVector(u,-tangent);
  const height=rise.length(),v=rise.normalize(),n=new THREE.Vector3().crossVectors(u,v).normalize(),e=new THREE.Euler().setFromQuaternion(new THREE.Quaternion().setFromRotationMatrix(new THREE.Matrix4().makeBasis(u,v,n)),'ZYX');
  const center=tc.clone().add(bc).multiplyScalar(.5).addScaledVector(n,-.055),size=[Math.max(a.distanceTo(b),c.distanceTo(d))+Math.abs(tangent)+.42,height+.12,.11];
  const cube=Cube.all.find(c=>c.name==='ceramic_liner_0_'+i);
  cube.from=center.toArray().map((x,k)=>x-size[k]/2);cube.to=center.toArray().map((x,k)=>x+size[k]/2);cube.origin=center.toArray();cube.rotation=[e.x,e.y,e.z].map(v=>v*180/Math.PI);
 }
 for(const c of group.children)if(c instanceof Cube){for(const f of Object.values(c.faces)){f.texture=tex.uuid;f.uv=[...c.faces.south.uv];}}
 Cube.selected.empty();Canvas.updateAll();fs.writeFileSync(base+'src/main/blockbench/commercial_flushometer_toilet.bbmodel',Codecs.project.compile());Project.saved=true;
 return {count:Cube.all.length,sealedTopPanels:20,closedBacks:60,backup};
})()
