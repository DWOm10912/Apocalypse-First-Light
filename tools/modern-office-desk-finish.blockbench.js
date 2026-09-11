(() => {
 if(Project.name!=='modern_office_desk'||Cube.all.length!==144)throw Error('Unexpected project');
 Undo.initEdit({elements:Cube.all,outliner:true});
 const main=Cube.all.find(x=>x.name==='tabletop_main');main.from=[-15.9,11,.1];main.to=[31.9,11.85,15.9];
 const faces={};for(const [d,f]of Object.entries(main.faces))faces[d]={uv:f.uv.slice(),texture:f.texture};
 new Cube({name:'tabletop_continuous_surface',from:[-16,11.85,0],to:[32,12,16],faces,box_uv:false,autouv:0}).addTo(main.parent).init();
 Undo.finishEdit('Separate tabletop edge bands and continuous top');for(const p of Preview.all){if(!p.controls||!p.camera)continue;p.controls.target.set(8,6,8);p.camera.position.set(55,35,-48);p.camera.zoom=.8;p.camera.updateProjectionMatrix();p.controls.update();}Canvas.updateAll();return {cubes:Cube.all.length};
})()
