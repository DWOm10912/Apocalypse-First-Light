(() => {
 const bottle=Cube.all.filter(c=>/^(wall_|cap_|neck_|faded_water_label)/.test(c.name)),positive=[],activeCoplanar=[],axes=[['west','east'],['down','up'],['north','south']];
 for(let i=0;i<bottle.length;i++)for(let j=i+1;j<bottle.length;j++){const a=bottle[i],b=bottle[j],ov=[0,1,2].map(k=>Math.min(a.to[k],b.to[k])-Math.max(a.from[k],b.from[k]));if(ov.every(v=>v>1e-6))positive.push([a.name,b.name,ov]);
  for(let k=0;k<3;k++){let fa,fb;if(Math.abs(a.to[k]-b.from[k])<1e-6)[fa,fb]=[axes[k][1],axes[k][0]];else if(Math.abs(b.to[k]-a.from[k])<1e-6)[fa,fb]=[axes[k][0],axes[k][1]];else continue;const other=[0,1,2].filter(n=>n!==k).map(n=>Math.min(a.to[n],b.to[n])-Math.max(a.from[n],b.from[n]));if(other.every(v=>v>1e-6)&&a.faces[fa].texture!==null&&b.faces[fb].texture!==null)activeCoplanar.push([a.name,fa,b.name,fb,other]);}
 }
 const box={min:[4.2,21.55,4.2],max:[11.8,31.15,11.8]},fluidIntersections=bottle.filter(c=>c.from.every((v,i)=>v<box.max[i])&&c.to.every((v,i)=>v>box.min[i])).map(c=>c.name);
 return {bottleCubes:bottle.length,positiveVolumeOverlaps:positive.length,activeCoplanarOverlaps:activeCoplanar.length,fluidIntersections:fluidIntersections.length,positiveExamples:positive.slice(0,8),coplanarExamples:activeCoplanar.slice(0,12)};
})()
