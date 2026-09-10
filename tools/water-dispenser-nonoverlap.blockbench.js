(() => {
 const w=globalThis.aflWater;if(!w||Project.uuid!==w.project||Cube.all.length!==199||!w.hollow||w.nonOverlap)throw Error('Expected inspected V1.1 hollow dispenser');
 const old=Cube.all.filter(c=>/^shell_|^thin_|^(inverted_neck|neck_front_lip|neck_side_lip)$/.test(c.name));if(old.length!==69)throw Error('V1.1 bottle geometry changed');
 Undo.initEdit({elements:Cube.all.slice(),outliner:true});old.forEach(c=>c.remove());const add=w.add,tex=w.texture;
 const clear=(c,...faces)=>faces.forEach(f=>c.faces[f].texture=null),bands=[[21.45,22.5,4.3,.45,'bottle'],[22.5,23.7,5.2,1.35,'ring'],[23.7,28.8,4.8,.35,'bottle'],[28.8,30.05,5.2,1.35,'ring'],[30.05,31.3,4.4,.45,'bottle']],made=[];
 for(let i=0;i<bands.length;i++){const [y,y2,r,t,mat]=bands[i],g=mat==='ring'?'bottle_rings':'bottle_body',q=[];
  q.push(add('wall_'+i+'_north',[8-r,y,8-r],[8+r,y2,8-r+t],mat,g));
 q.push(add('wall_'+i+'_south',[8-r,y,8+r-t],[8+r,y2,8+r],mat,g));
 q.push(add('wall_'+i+'_west',[8-r,y,8-r+t],[8-r+t,y2,8+r-t],mat,g));
  q.push(add('wall_'+i+'_east',[8+r-t,y,8-r+t],[8+r,y2,8+r-t],mat,g));clear(q[2],'north','south');clear(q[3],'north','south');made.push(q);
 }
 clear(...[]);
 clear(made[0][0],'down');clear(made[0][1],'down');clear(made[0][2],'down');clear(made[0][3],'down');
 clear(made[4][0],'up');clear(made[4][1],'up');clear(made[4][2],'up');clear(made[4][3],'up');
 for(let i=0;i<4;i++){const lower=made[i],upper=made[i+1],upperLarger=bands[i+1][2]>bands[i][2];for(const c of upperLarger?lower:upper)clear(c,upperLarger?'up':'down');}
 const bottom=add('cap_bottom_sealed',[3.7,21.2,3.7],[12.3,21.45,12.3],'bottle','bottle_body'),top=add('cap_top_sealed',[3.6,31.3,3.6],[12.4,31.55,12.4],'bottle','bottle_body');
 function ring(prefix,y,y2,r,t){const g='bottle_neck',q=[add(prefix+'_north',[8-r,y,8-r],[8+r,y2,8-r+t],'cap',g),add(prefix+'_south',[8-r,y,8+r-t],[8+r,y2,8+r],'cap',g),add(prefix+'_west',[8-r,y,8-r+t],[8-r+t,y2,8+r-t],'cap',g),add(prefix+'_east',[8+r-t,y,8-r+t],[8+r,y2,8+r-t],'cap',g)];clear(q[2],'north','south');clear(q[3],'north','south');return q;}
 const neck=ring('neck_lower',20.75,20.95,1.55,.3),collar=ring('neck_collar',20.95,21.2,1.9,.3);for(const c of neck)clear(c,'up');for(const c of collar)clear(c,'up');
 const label=Cube.all.find(c=>c.name==='faded_water_label');if(!label)throw Error('Label missing');clear(label,'south');
 w.nonOverlap={fluidSafeBox:{min:[4.2,21.55,4.2],max:[11.8,31.15,11.8]},bands:made.map(q=>q.map(c=>c.uuid)),caps:[bottom.uuid,top.uuid],neck:[...neck,...collar].map(c=>c.uuid)};
 Undo.finishEdit('Water dispenser: non-overlapping translucent bottle shell');Canvas.updateAll();
 return {cubes:Cube.all.length,removedV11Cubes:old.length,wallCubes:20,capCubes:2,neckCubes:8,fluidSafeBox:w.nonOverlap.fluidSafeBox};
})()
