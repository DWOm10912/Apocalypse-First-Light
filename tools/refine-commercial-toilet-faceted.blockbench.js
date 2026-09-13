(()=>{
 const {box,edges,groups,base,fs}=aflFacetedToilet;
 for(const g of ['bowl_shell','bowl_inner','pedestal','base'])for(const c of [...groups[g].children])c.remove();
 // A vertical crown and a solid, bevelled underbody replace intersecting
 // full-height slanted corner plates. The dry cavity remains above the core.
 edges('bowl_shell','crown',6.95,1.15,.95,0);
 box('bowl_shell','underbody_core',[0,5.65,-.1],[6.4,1.55,7.4],0);
 for(const s of [-1,1]){
  box('bowl_shell','underbody_side_bevel_'+s,[s*3.2,5.95,-.1],[1.55,1.55,7.4],0,[0,0,45]);
  box('bowl_shell','nose_rounding_'+s,[s*1.5,5.95,-4.2],[3.4,1.3,2.9],0,[0,-s*22.5,0]);
 }
 box('bowl_shell','nose_under_chamfer',[0,5.8,-4.1],[4.4,1.8,1.8],0,[45,0,0]);
 box('bowl_shell','rear_support_shoulder',[0,5.95,3.8],[5.8,1.4,2.5],0);
 // Broad slopes terminate on a shallow dry ceramic sump, not a water plane.
 for(const s of [-1,1]){
  box('bowl_inner','cavity_side_'+s,[s*3.15,7.05,-.3],[.45,1.8,6.6],2,[0,0,-s*22.5]);
  box('bowl_inner','cavity_nose_'+s,[s*1.75,7,-4.45],[2.9,1.7,.55],2,[-22.5,-s*32.5,0]);
  box('bowl_inner','cavity_rear_'+s,[s*1.95,7,3.5],[2.65,1.7,.55],2,[22.5,s*30,0]);
 }
 box('bowl_inner','cavity_front',[0,7,-4.95],[3,1.7,.55],2,[-22.5,0,0]);
 box('bowl_inner','cavity_back',[0,7,3.75],[3.9,1.7,.55],2,[22.5,0,0]);
 box('bowl_inner','dry_ceramic_floor',[0,6.28,-.45],[5.95,.2,7.3],2);
 for(const s of [-1,1])box('bowl_inner','dry_floor_corner_'+s,[s*1.35,6.27,-3.9],[3,.18,1.8],2,[0,-s*30,0]);
 // Crossed broad cuboids form one chamfered rectangular prism (BR51 method).
 // Near-coincident caps are buried in the base / shoulder, not visible.
 box('pedestal','rectangular_core',[0,2.8,1.75],[3.65,4.4,5.6],0);
 box('pedestal','rectangular_wide_core',[0,2.8,1.75],[4.85,4.39,4.4],0);
 for(const s of [-1,1])for(const z of [-1,1])box('pedestal','corner_chamfer_'+s+'_'+z,[s*1.825,2.8,1.75+z*2.2],[.85,4.38,.85],0,[0,45,0]);
 box('base','base_long',[0,.3,1.75],[3.95,.6,6.2],2);
 box('base','base_wide',[0,.299,1.75],[5.45,.598,4.7],2);
 for(const s of [-1,1])for(const z of [-1,1])box('base','corner_chamfer_'+s+'_'+z,[s*1.975,.298,1.75+z*2.35],[1.06,.596,1.06],2,[0,45,0]);
 Cube.selected.empty();Canvas.updateAll();
 fs.writeFileSync(base+'src/main/blockbench/commercial_flushometer_toilet.bbmodel',Codecs.project.compile());Project.saved=true;
 return {count:Cube.all.length};
})()
