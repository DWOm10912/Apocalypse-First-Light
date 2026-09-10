(() => {
 if(Project.uuid!=='b8e40069-0a06-8a5d-41df-7d35617d1f0a'||Cube.all.length!==209)throw Error('Unexpected model');
 Undo.initEdit({elements:Cube.all,outliner:true});
 const cloneFaces=c=>Object.fromEntries(Object.entries(c.faces).map(([k,f])=>[k,{uv:f.uv.slice(),texture:f.texture}]));
 for(let i=1;i<=16;i++){
  const prefix='body_shell_'+String(i).padStart(2,'0')+'_';
  const parts=Cube.all.filter(c=>c.name.startsWith(prefix));
  parts[0].to[1]=13.8;parts.slice(1).forEach(c=>c.remove());
 }
 const floor=Cube.all.filter(c=>c.name.startsWith('inner_floor_strip_')),faces=cloneFaces(floor[0]),parent=floor[0].parent;
 floor.forEach(c=>c.remove());
 for(let i=0;i<32;i++){
  const z0=2.35+i*11.3/32,z1=2.35+(i+1)*11.3/32;
  const dz=z0<=8&&z1>=8?0:Math.min(Math.abs(z0-8),Math.abs(z1-8));
  const w=Math.sqrt(5.65*5.65-dz*dz);
  new Cube({name:'inner_floor_strip_'+String(i+1).padStart(2,'0'),from:[8-w,.45,z0],to:[8+w,.7,z1],faces:JSON.parse(JSON.stringify(faces)),box_uv:false,autouv:0}).addTo(parent).init();
 }
 for(const c of Cube.all.filter(c=>c.name.startsWith('lid_skirt_'))){
  const axis=c.to[0]-c.from[0]<c.to[2]-c.from[2]?0:2;
  c.from[axis]-=.4;c.to[1]=15.52;
  c.to[axis]+=.1;
 }
 Canvas.updateAll();Undo.finishEdit('Seal inset lid perimeter and continuous bucket floor');
 return {cubes:Cube.all.length,groups:Group.all.length};
})()
