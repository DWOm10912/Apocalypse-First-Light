(() => {
 if(Project.uuid!=='b8e40069-0a06-8a5d-41df-7d35617d1f0a'||Cube.all.length!==201)throw Error('Unexpected live model');
 Undo.initEdit({elements:Cube.all,textures:Texture.all,outliner:true});
 const old=Cube.all.filter(c=>c.name.startsWith('lid_stamped_segment_')),faces=Object.fromEntries(Object.entries(old[0].faces).map(([k,f])=>[k,{uv:f.uv.slice(),texture:f.texture}])),parent=old[0].parent;
 old.forEach(c=>c.remove());
 for(let i=0;i<17;i++){const z0=2.15+i*11.7/17,z1=2.15+(i+1)*11.7/17;const dz=Math.max(Math.abs(z0-8),Math.abs(z1-8));const w=Math.sqrt(6.12*6.12-dz*dz);new Cube({name:'lid_stamped_segment_'+String(i+1).padStart(2,'0'),from:[8-w,15.05,z0],to:[8+w,15.45,z1],faces:JSON.parse(JSON.stringify(faces)),box_uv:false,autouv:0}).addTo(parent).init();}
 for(const c of Cube.all.filter(c=>c.name.startsWith('body_shell_')))for(const f of Object.values(c.faces))f.uv=[5,8,25,55];
 for(const c of Cube.all.filter(c=>c.name.startsWith('lid_stamped_segment_')))for(const f of Object.values(c.faces))f.uv=[37,8,57,55];
 Undo.finishEdit('Refine lid closure and quiet shell seams');Canvas.updateAll();
 return {cubes:Cube.all.length};
})()
