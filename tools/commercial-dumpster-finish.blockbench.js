(() => {
 if(Project.uuid!=='9fbb94a0-6491-3958-f87b-1b168ea7e0d3'||Cube.all.length!==256)throw Error('Unexpected project');
 Undo.initEdit({elements:Cube.all,outliner:true});
 for(const side of ['left','right']){const main=Cube.all.find(c=>c.name===side+'_lid_plate');const g=Group.all.find(g=>g.name===side+'_lid_frame');for(const z of [4,11])new Cube({name:side+'_lid_underbrace_'+z,from:[main.from[0]+.4,20.78,z],to:[main.to[0]-.4,21,z+.6],origin:main.origin.slice(),rotation:main.rotation.slice(),box_uv:false,autouv:0,faces:Object.fromEntries(Object.entries(main.faces).map(([k,f])=>[k,{uv:f.uv.slice(),texture:f.texture}]))}).addTo(g).init();}
 Undo.finishEdit('Lid underside reinforcement');Canvas.updateAll();return {cubes:Cube.all.length};
})()
