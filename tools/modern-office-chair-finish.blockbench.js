(() => {
 if(Project.name!=='modern_office_chair'||Cube.all.length!==192)throw Error('Unexpected asset');
 Undo.initEdit({elements:Cube.all,outliner:true});
 const left=Cube.all.find(c=>c.name==='left_arm_mount');left.to[0]=4;
 const right=Cube.all.find(c=>c.name==='right_arm_mount');right.from[0]=12;
 const template=Cube.all.find(c=>c.name==='seat_tilt_housing');
 function add(n,f,t,g,r=[0,0,0],o=[8,0,8]){const faces={};for(const [d,v]of Object.entries(template.faces))faces[d]={uv:v.uv.slice(),texture:v.texture};new Cube({name:n,from:f,to:t,faces,rotation:r,origin:o,box_uv:false,autouv:0}).addTo(Group.all.find(x=>x.name===g)).init();}
 for(let i=0;i<2;i++){let y=12.2+i*3.2,z=12.5+i*.35;add('backrest_seam_backing_'+i,[3.7,y,z],[12.3,y+.2,z+.25],'backrest_frame');}
 for(let i=0;i<5;i++)add('base_underside_spine_'+(i+1),[7.7,1.35,9],[8.3,1.55,12.4],'leg_'+String(i+1).padStart(2,'0'),[0,i*72,0]);
 add('tilt_axle',[5.5,6,7.3],[10.5,6.45,7.8],'central_mount');
 add('tilt_left_cap',[5.35,5.95,7.25],[5.5,6.5,7.85],'central_mount');
 add('tilt_right_cap',[10.5,5.95,7.25],[10.65,6.5,7.85],'central_mount');
 Undo.finishEdit('Chair joint and backing refinement');
 for(const p of Preview.all){if(!p.controls||!p.camera)continue;p.controls.target.set(8,9,8);p.camera.position.set(38,26,43);p.camera.zoom=.75;p.camera.updateProjectionMatrix();p.controls.update();}Canvas.updateAll();return {cubes:Cube.all.length,groups:Group.all.length};
})()
