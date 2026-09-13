(async()=>{
 const fs=require('fs'),base='D:/Minecraft Modding/Apocalypse First Light/';
 if(Project.name!=='commercial_flushometer_toilet')throw Error('Select production model');
 const backup=base+'src/main/blockbench/previews/commercial_flushometer_toilet_before_full_rebuild.bbmodel';
 if(fs.existsSync(backup))throw Error('Backup already exists');
 fs.writeFileSync(backup,Codecs.project.compile());
 const oldCount=Cube.all.length,root=Group.all.find(g=>g.name==='commercial_toilet_root');
 const metal=Group.all.find(g=>g.name==='flushometer_assembly');
 const metalIds=Cube.all.filter(c=>{let g=c.parent;while(g instanceof Group){if(g===metal)return true;g=g.parent;}return false;}).map(c=>c.uuid);
 for(const g of [...root.children])if(g instanceof Group&&g!==metal)g.remove();
 const groups={};
 function group(name,parent=root){return groups[name]=new Group({name,origin:[0,0,0]}).addTo(parent).init();}
 const body=group('toilet_body_new');for(const n of ['bowl_outer_new','bowl_inner_new','pedestal_new','base_new','rear_connector_new'])group(n,body);
 group('toilet_seat_new');group('bowl_water_new');
 const texture=Texture.all[0],N=28,cz=-.65;
 function cube(g,name,from,to,mat,origin=[0,0,0],rotation=[0,0,0]){
  const c=new Cube({name,from,to,origin,rotation,autouv:0,box_uv:false}).addTo(groups[g]).init();
  for(const f of Object.values(c.faces)){f.texture=texture.uuid;const x=mat%4*32,y=Math.floor(mat/4)*32;f.uv=[x+8,y+8,x+24,y+24];}return c;
 }
 function ring(g,prefix,rx,rz,y,h,width,mat,open=false,tilt=0){
  for(let i=0;i<N;i++){
   const a=2*Math.PI*i/N,b=2*Math.PI*(i+1)/N,mid=(a+b)/2;
   if(open&&Math.sin(mid)<-.97)continue;
   const p=[rx*Math.cos(a),cz+rz*Math.sin(a)],q=[rx*Math.cos(b),cz+rz*Math.sin(b)];
   const x=(p[0]+q[0])/2,z=(p[1]+q[1])/2,len=Math.hypot(q[0]-p[0],q[1]-p[1])+.12;
   const lift=prefix==='floor_base'?0:Math.abs(Math.cos(mid))*.006;
   cube(g,prefix+'_'+i,[x-len/2,y-h/2+lift,z-width/2],[x+len/2,y+h/2+lift,z+width/2],mat,[x,y+lift,z],[tilt,-Math.atan2(q[1]-p[1],q[0]-p[0])*180/Math.PI,0]);
  }
 }
 ring('bowl_outer_new','oval_rim',5.05,5.45,7.85,.7,1.05,1);
 Cube.selected.empty();Group.all.forEach(g=>g.selected=false);Canvas.updateAll();
 globalThis.aflToiletRebuild={cube,ring,groups,base,fs,oldCount,metalIds,backup,finish(){
  ring('toilet_seat_new','open_front_seat',5.05,5.45,8.47,.5,1.2,13,true);
  for(let k=0;k<4;k++)ring(k<2?'bowl_inner_new':'bowl_outer_new','tapered_bowl_'+k,3.35+k*.53,3.72+k*.55,4.7+k*.8,1.02,1.35,k<2?2:0,false,-28);
  ring('pedestal_new','pedestal',2.4,3.5,2.27,3.32,1.3,0,false,-3);
  ring('base_new','floor_base',2.65,3.8,.3,.6,1.3,2);
  for(let i=0;i<7;i++){const z=-4+i,w=[1.6,2.4,2.9,3.05,3.05,2.7,2.1][i];cube('bowl_inner_new','dry_sump_'+i,[-w,4,z],[w,4.1,z+1],2);}
  cube('rear_connector_new','rear_neck',[-1.85,4.2,4.7],[1.85,7.6,6.8],0);
  cube('rear_connector_new','rear_socket_bridge',[-2.2,7.6,4.4],[2.2,7.9,7.3],0);
  for(const s of [-1,1])cube('toilet_seat_new','seat_hinge_'+s,[s*2.5-.25,7.9,4.35],[s*2.5+.25,8.14,5.05],9);
  Cube.selected.empty();Canvas.updateAll();
  const path=base+'src/main/blockbench/commercial_flushometer_toilet.bbmodel';fs.writeFileSync(path,Codecs.project.compile());Project.save_path=path;Project.saved=true;
  return {oldCount,newCount:Cube.all.length,metalPreserved:metalIds.every(id=>Cube.all.some(c=>c.uuid===id)),path};
 }};
 return {phase:'TOP RIM ONLY',segments:N,oldCount,backup};
})()
