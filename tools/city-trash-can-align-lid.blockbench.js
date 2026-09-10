(() => {
 if(Project.uuid!=='b8e40069-0a06-8a5d-41df-7d35617d1f0a'||Cube.all.length!==204)throw Error('Unexpected model');
 const parts=Cube.all.filter(c=>c.name.startsWith('lid_skirt_'));
 if(parts.length!==16)throw Error('Expected 16 rim segments');
 Undo.initEdit({elements:parts});
 for(const c of parts){
  const i=Number(c.name.split('_').at(-1))-1,deg=i*22.5,t=deg*Math.PI/180;
  const q=Math.floor((deg+45)/90),cx=8+Math.sin(t)*5.71,cz=8+Math.cos(t)*5.71;
  const dx=q%2?1:2.48,dz=q%2?2.48:1;
  c.from=[cx-dx/2,14.45,cz-dz/2];c.to=[cx+dx/2,15.52,cz+dz/2];
  c.origin=[cx,14.45,cz];c.rotation=[0,deg-q*90,0];
  for(const f of Object.values(c.faces))f.uv=[101,8,121,55];
 }
 Canvas.updateAll();Undo.finishEdit('Concentric symmetrical lid rim');
 return {segments:parts.length,radius:5.71,spacingDegrees:22.5};
})()
