(()=>{
 const fs=require('fs'),base='D:/Minecraft Modding/Apocalypse First Light/';
 if(Project.name!=='commercial_flushometer_toilet')throw Error('Wrong model');
 const backup=base+'src/main/blockbench/previews/commercial_flushometer_toilet_before_integrated_body.bbmodel';
 if(fs.existsSync(backup))throw Error('Backup exists');fs.writeFileSync(backup,Codecs.project.compile());
 const body=Group.all.find(g=>g.name==='toilet_body_faceted'),inner=Group.all.find(g=>g.name==='bowl_inner');
 const bridge=new Group({name:'continuous_ceramic_throat',origin:[0,0,0]}).addTo(body).init();
 const sump=new Group({name:'dry_drain_funnel',origin:[0,0,0]}).addTo(inner).init();
 const tex=Texture.all[0];
 function box(g,name,center,size,mat,rotation=[0,0,0]){
  const c=new Cube({name,from:center.map((v,i)=>v-size[i]/2),to:center.map((v,i)=>v+size[i]/2),origin:center,rotation,autouv:0,box_uv:false}).addTo(g).init();
  for(const f of Object.values(c.faces)){const x=mat%4*32,y=Math.floor(mat/4)*32;f.texture=tex.uuid;f.uv=[x+8,y+8,x+24,y+24];}return c;
 }
 for(const c of [...Cube.all])if(c.name==='underbody_core'||c.name==='dry_ceramic_floor'||c.name.startsWith('dry_floor_corner_'))c.remove();
 // Continuous external porcelain throat: rear-supported neck, broad front
 // return and paired shoulder bevels physically overlap bowl and pedestal.
 box(bridge,'throat_solid_core',[0,4.35,1.15],[4.1,.7,4.4],0);
 box(bridge,'front_continuous_return',[0,4.3,-1.85],[4.3,2.9,1.05],0,[-45,0,0]);
 for(const s of [-1,1]){
  box(bridge,'shoulder_blend_'+s,[s*2.4,4.8,.65],[1.1,2.25,4.5],0,[0,0,-s*22.5]);
  box(bridge,'rear_neck_blend_'+s,[s*1.8,4.9,3.55],[1.2,1.8,1.6],0,[15,0,-s*15]);
 }
 // Two directional slope transitions contract in BOTH horizontal axes.
 // No wide flat sump, circular sampling, liquid, or horizontal ring courses.
 for(const s of [-1,1]){
  box(sump,'upper_side_funnel_'+s,[s*2.3,5.88,-.35],[.32,1.35,5.7],2,[0,0,-s*52.5]);
  box(sump,'lower_side_funnel_'+s,[s*1.24,5.05,.15],[.32,1.5,2.9],3,[0,0,-s*52.5]);
  box(sump,'front_funnel_corner_'+s,[s*1.45,5.85,-2.85],[2.9,1.65,.35],2,[-45,-s*30,0]);
  box(sump,'rear_funnel_corner_'+s,[s*1.35,5.85,2.35],[2.75,1.6,.35],2,[45,s*30,0]);
  box(sump,'lower_drain_cheek_'+s,[s*.68,4.66,.5],[.3,.95,1.45],3,[0,0,-s*30]);
 }
 box(sump,'front_funnel_main',[0,5.8,-2.75],[3,1.9,.35],2,[-50,0,0]);
 box(sump,'front_funnel_lower',[0,4.95,-.98],[2.2,1.6,.35],3,[-50,0,0]);
 box(sump,'rear_funnel_main',[0,5.8,2.4],[3,1.7,.35],2,[50,0,0]);
 box(sump,'rear_funnel_lower',[0,4.95,1.7],[2.1,1.4,.35],3,[50,0,0]);
 // The optional dark drain cap was subsequently removed in the live model.
 // Keep the ceramic opening without reinstating that user-side change.
 // A little ceramic lip gives the dark recess depth, rather than a painted
 // patch on a large flat floor. It remains a static dry opening.
 for(const s of [-1,1])box(sump,'drain_lip_'+s,[s*.52,4.65,.48],[.24,.28,1.3],3);
 Cube.selected.empty();Canvas.updateAll();
 fs.writeFileSync(base+'src/main/blockbench/commercial_flushometer_toilet.bbmodel',Codecs.project.compile());Project.saved=true;
 return {count:Cube.all.length,backup};
})()
