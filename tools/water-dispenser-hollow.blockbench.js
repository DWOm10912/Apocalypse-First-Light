(() => {
 const w=globalThis.aflWater;if(!w||Project.uuid!==w.project||Cube.all.length!==179||w.hollow)throw Error('Expected original live dispenser');
 const texture=Texture.all.find(t=>t.uuid===w.texture);if(!texture)throw Error('Texture binding changed');
 const removed=Cube.all.filter(c=>/^bottle_\d+_profile_\d+$/.test(c.name)||c.name==='top_moulded_flat');if(removed.length!==46)throw Error('Bottle selection changed');
 w.preHollow=Codecs.project.compile();
 Undo.initEdit({elements:Cube.all.slice(),textures:[texture],bitmap:true,outliner:true});
 const canvas=document.createElement('canvas');canvas.width=canvas.height=128;const ctx=canvas.getContext('2d');
 const colors={shell:[190,187,175],side:[177,177,167],door:[190,187,175],bottle:[89,123,150,150],ring:[108,141,165,185],dark:[49,53,53],metal:[142,147,145],red:[144,57,47],blue:[49,91,132],rubber:[39,43,43],label:[176,190,194],badge:[187,185,173],vent:[35,40,41],edge:[207,204,192],cap:[58,72,78]};
 for(const [name,r]of Object.entries(w.regions)){const [x,y,x2,y2]=r,c=colors[name],alpha=c[3]??255;
  const paint=(d)=>{ctx.fillStyle=`rgba(${c[0]+d},${c[1]+d},${c[2]+d},${alpha/255})`;};
  paint(0);ctx.fillRect(x,y,x2-x,y2-y);
  paint(6);ctx.fillRect(x,y,x2-x,1);paint(-7);ctx.fillRect(x,y2-1,x2-x,1);
  if(['shell','door','side'].includes(name)){paint(-3);ctx.fillRect(x+1,y2-5,x2-x-2,3);paint(4);ctx.fillRect(x+2,y+2,1,y2-y-9);}
 }
 ctx.fillStyle='rgba(148,176,197,0.36)';ctx.fillRect(86,6,2,46);ctx.fillStyle='rgba(132,159,183,0.24)';ctx.fillRect(90,9,3,38);
 ctx.fillStyle='#718895';ctx.fillRect(91,84,14,2);ctx.fillRect(91,89,9,1);ctx.fillRect(91,93,12,1);ctx.fillRect(101,96,7,3);ctx.fillRect(103,94,3,2);
 ctx.fillStyle='#72786f';ctx.fillRect(101,107,3,3);ctx.fillRect(105,105,3,5);ctx.fillRect(109,107,3,3);ctx.fillRect(98,112,16,1);ctx.fillRect(102,115,10,1);
 texture.fromDataURL(canvas.toDataURL());
 removed.forEach(c=>c.remove());
 const add=w.add;
 const bands=[[21.45,22.5,4.3,.45,'bottle'],[22.5,23.7,5.2,1.35,'ring'],[23.7,28.8,4.8,.35,'bottle'],[28.8,30.05,5.2,1.35,'ring'],[30.05,31.3,4.4,.45,'bottle']];
 bands.forEach(([y,y2,r,t,mat],i)=>{const a=.72*r,b=.5*r,parent=mat==='ring'?'bottle_rings':'bottle_body';
  for(const sign of [-1,1]){
   const lo=sign<0?-r:r-t,hi=sign<0?-r+t:r;
   add('shell_'+i+'_end_'+sign,[8-a,y,8+lo],[8+a,y2,8+hi],mat,parent);
   add('shell_'+i+'_side_'+sign,[8+lo,y,8-b],[8+hi,y2,8+b],mat,parent);
   for(const zsign of [-1,1]){
    const xlo=sign<0?-a:a-t,xhi=sign<0?-a+t:a;
    add('shell_'+i+'_corner_'+sign+'_'+zsign,[8+xlo,y,8+(zsign<0?-r:-0+b)],[8+xhi,y2,8+(zsign<0?-b:r)],mat,parent);
    add('shell_'+i+'_step_'+sign+'_'+zsign,[8+(sign<0?-r:a-t),y,8+(zsign<0?-b:-0+b-t)],[8+(sign<0?-a+t:r),y2,8+(zsign<0?-b+t:b)],mat,parent);
   }
  }
 });
 for(const [name,y,y2,r]of [['bottom',21.2,21.45,4.3],['top',31.3,31.55,4.4]]){
  add('thin_'+name+'_center',[8-r,y,8-r*.5],[8+r,y2,8+r*.5],'bottle','bottle_body');
  for(const s of [-1,1])add('thin_'+name+'_end_'+s,[8-r*.72,y,8+(s<0?-r:r*.5)],[8+r*.72,y2,8+(s<0?-r*.5:r)],'bottle','bottle_body');
 }
 w.hollow={bands,fluidSafeBox:{min:[5.7,21.55,5.7],max:[10.3,31.15,10.3]},wallThickness:.35};
 Undo.finishEdit('Water dispenser: clean smooth palette and genuinely hollow bottle shell');Canvas.updateAll();
 return {cubes:Cube.all.length,removedSolidBottleCubes:removed.length,shellCubes:60,thinCaps:6,fluidSafeBox:w.hollow.fluidSafeBox};
})()
