import {readFile,writeFile,mkdir} from 'node:fs/promises';
const desk=JSON.parse(await readFile('src/main/blockbench/modern_office_desk.bbmodel','utf8'));
const chair=JSON.parse(await readFile('src/main/blockbench/modern_office_chair.bbmodel','utf8'));
const shift=-9.6;
for(const e of chair.elements){e.from[2]+=shift;e.to[2]+=shift;e.origin[2]+=shift;for(const f of Object.values(e.faces))if(f.texture===0)f.texture=1;}
for(const g of chair.groups)g.origin[2]+=shift;
const chairRoot=chair.groups.find(g=>g.uuid===chair.outliner[0].uuid);chairRoot.rotation=[0,180,0];chairRoot.name='preview_chair_tucked';
const deskRoot=desk.groups.find(g=>g.uuid===desk.outliner[0].uuid);deskRoot.name='preview_desk';
chair.textures[0].id='1';chair.textures[0].particle=false;
const rootUuid='f0f09680-2da5-4bd8-96aa-91e35959c176';
const root={name:'office_workstation_preview_root',uuid:rootUuid,export:true,locked:false,scope:0,selected:false,_static:{properties:{},temp_data:{}},origin:[8,0,0],rotation:[0,0,0],color:0,children:[],reset:false,shade:true,mirror_uv:false,visibility:true,autouv:0,isOpen:true,primary_selected:false};
const preview={...desk,name:'office_workstation_preview',ambientocclusion:false,elements:[...desk.elements,...chair.elements],groups:[root,...desk.groups,...chair.groups],outliner:[{uuid:rootUuid,isOpen:true,children:[desk.outliner[0],chair.outliner[0]]}],textures:[desk.textures[0],chair.textures[0]]};
preview.meta={...preview.meta,model_format:'java_block'};
const output='build/asset_checks/office_workstation_preview/office_workstation_preview.bbmodel';await mkdir('build/asset_checks/office_workstation_preview',{recursive:true});await writeFile(output,JSON.stringify(preview));
const packed=Buffer.from(JSON.stringify(preview)).toString('base64url');const live=`(() => {const raw='${packed}'.replaceAll('-','+').replaceAll('_','/');const model=JSON.parse(new TextDecoder().decode(Uint8Array.from(atob(raw),c=>c.charCodeAt(0))));newProject(Formats.java_block);Codecs.project.parse(model,'D:/Minecraft Modding/Apocalypse First Light/${output}');Project.save_path='D:/Minecraft Modding/Apocalypse First Light/${output}';for(const p of Preview.all){if(!p.controls||!p.camera)continue;p.controls.target.set(8,7,0);p.camera.position.set(54,34,-55);p.camera.zoom=.72;p.camera.updateProjectionMatrix();p.controls.update();}Canvas.updateAll();return {name:Project.name,cubes:Cube.all.length,groups:Group.all.length,textures:Texture.all.length};})()`;
await writeFile('build/asset_checks/office_workstation_preview/load-preview.blockbench.js',live);
const armMax=10.8,deskUnderside=10.9,armNearEdgeZ=2*(8+shift)-(4.1+shift),beamFront=2.4;
console.log(JSON.stringify({output,cubes:preview.elements.length,groups:preview.groups.length,textures:preview.textures.length,chairShiftZ:shift,chairRotationY:180,verticalArmClearance:deskUnderside-armMax,horizontalArmBeamClearance:beamFront-armNearEdgeZ}));
