(() => {
 const w=globalThis.aflWater;if(!w||Project.uuid!==w.project||Cube.all.length!==51)throw Error('Cabinet stage changed');
 Undo.initEdit({elements:[],outliner:true});const add=w.add;
 const layers=[[21.2,.6,3.2,'bottle'],[21.8,.7,4.3,'bottle'],[22.5,.65,5,'ring'],[23.15,.55,5.2,'ring'],[23.7,5.1,4.8,'bottle'],[28.8,.6,5.15,'ring'],[29.4,.65,5.2,'ring'],[30.05,.7,4.9,'bottle'],[30.75,.65,4.15,'bottle']];
 layers.forEach(([y,h,r,mat],i)=>{const limits=[-1,-.6,-.22,.22,.6,1],widths=[.6,.91,1,.91,.6];for(let j=0;j<5;j++)add('bottle_'+i+'_profile_'+j,[8-r*widths[j],y,8+r*limits[j]],[8+r*widths[j],y+h,8+r*limits[j+1]],mat,mat==='ring'?'bottle_rings':'bottle_body');});
 add('inverted_neck',[6.45,20.75,6.45],[9.55,21.3,9.55],'cap','bottle_neck');
 add('neck_front_lip',[6.1,20.85,6.55],[9.9,21.15,9.45],'cap','bottle_neck');
 add('neck_side_lip',[6.55,20.85,6.1],[9.45,21.15,9.9],'cap','bottle_neck');
 add('top_moulded_flat',[5.8,31.4,5.8],[10.2,31.55,10.2],'bottle','bottle_body');
 add('faded_water_label',[6.2,24.3,3.15],[9.8,27.55,3.2],'label','bottle_label');
 Undo.finishEdit('Water dispenser: stepped blue plastic bottle, rings and label');Canvas.updateAll();return {stage:'bottle',cubes:Cube.all.length,bottleCubes:50,height:31.55};
})()
