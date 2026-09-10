(() => {
 const w=globalThis.aflWater;if(!w||Project.uuid!==w.project||Cube.all.length!==179||w.finished)throw Error('Detail stage changed or finish already applied');
 Undo.initEdit({elements:Cube.all,uv_only:false});
 for(const s of w.specs.filter(s=>/^(hot|cold)_/.test(s.parent))){const c=Cube.all.find(c=>c.uuid===s.uuid),a=c.from[0],b=c.to[0];c.from[0]=16-b;c.to[0]=16-a;}
 const dirs={west:[0,-1],east:[0,1],down:[1,-1],up:[1,1],north:[2,-1],south:[2,1]};let culled=0;
 for(const c of Cube.all)for(const [face,[axis,sign]] of Object.entries(dirs)){
  const plane=sign>0?c.to[axis]:c.from[axis],axes=[0,1,2].filter(i=>i!==axis),[a,b]=axes;
  const rects=Cube.all.filter(d=>d!==c&&(sign>0?(d.from[axis]<=plane+1e-6&&d.to[axis]>plane+1e-6):(d.to[axis]>=plane-1e-6&&d.from[axis]<plane-1e-6))).map(d=>[Math.max(c.from[a],d.from[a]),Math.max(c.from[b],d.from[b]),Math.min(c.to[a],d.to[a]),Math.min(c.to[b],d.to[b])]).filter(r=>r[2]>r[0]&&r[3]>r[1]);
  if(!rects.length)continue;
  const xs=[...new Set([c.from[a],c.to[a],...rects.flatMap(r=>[r[0],r[2]])])].sort((a,b)=>a-b);let covered=true;
  for(let i=0;i<xs.length-1&&covered;i++){const x=(xs[i]+xs[i+1])/2,ys=rects.filter(r=>r[0]<=x&&r[2]>=x).map(r=>[r[1],r[3]]).sort((a,b)=>a[0]-b[0]);let end=c.from[b];for(const [lo,hi]of ys){if(lo>end+1e-6)break;end=Math.max(end,hi);}if(end<c.to[b]-1e-6)covered=false;}
  if(covered){c.faces[face].texture=null;culled++;}
 }
 Project.credit='Apocalypse: First Light — original voxel asset from supplied concept';
 Project.save_path='D:/Minecraft Modding/Apocalypse First Light/src/main/blockbench/water_dispenser.bbmodel';
 Project.export_path='D:/Minecraft Modding/Apocalypse First Light/src/main/resources/assets/apocalypse_firstlight/models/block/water_dispenser.json';
 w.finished=true;Undo.finishEdit('Water dispenser: correct hot/cold order and cull fully hidden surfaces');Canvas.updateAll();
 return {cubes:Cube.all.length,hiddenFacesRemoved:culled,front:'NORTH (-Z), hot at viewer left',bounds:{min:[1.7,0,1.4],max:[14.3,31.55,14.35]},groups:Group.all.length};
})()
