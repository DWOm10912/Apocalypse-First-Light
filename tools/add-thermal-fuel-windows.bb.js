(async()=>{
 if(Project.name!=='thermal_generator_3d')throw Error('Wrong project');
 if(Group.all.some(g=>g.name==='liquid_heat_window'))throw Error('Fuel window already exists; edit source instead');
 const fs=require('fs'),front=Group.all.find(g=>g.name==='front'),solid=Group.all.find(g=>g.name==='combustion_chamber');
 const texture=Texture.all.find(t=>t.name==='thermal_generator_3d.png');
 if(!front||!solid||!texture)throw Error('Missing required source assets');
 const before=new Map(Cube.all.map(c=>[c.uuid,JSON.stringify(c.getSaveCopy())]));
 const edited=new Set(Cube.all.filter(c=>c.name.startsWith('lower_left_')||c.parent?.name.startsWith('flame_')).map(c=>c.uuid));
 const atlas=document.createElement('canvas');atlas.width=atlas.height=256;const ctx=atlas.getContext('2d');ctx.imageSmoothingEnabled=false;
 const oldImage=new Image();oldImage.src=texture.source;await oldImage.decode();ctx.drawImage(oldImage,0,0);
 for(const [side,x]of [['left',96],['right',128]]){
  const img=new Image();img.src='data:image/png;base64,'+fs.readFileSync('D:/Minecraft Modding/Apocalypse First Light/src/main/resources/assets/apocalypse_firstlight/textures/block/machine_side_'+side+'_fluid.png').toString('base64');await img.decode();
  ctx.drawImage(oldImage,32,0,32,32,x,96,32,32);
  const temp=document.createElement('canvas');temp.width=temp.height=32;const tc=temp.getContext('2d');tc.drawImage(img,0,0);
  const src=tc.getImageData(0,0,32,32),dst=ctx.getImageData(x,96,32,32);
  for(let y=0;y<32;y++)for(let u=0;u<32;u++){
   const distance=Math.max(Math.abs(u-15.5),Math.abs(y-15.5));
   const a=distance<=6.5?1:Math.max(0,Math.min(1,(12.5-distance)/6));
   const i=(y*32+u)*4;for(let k=0;k<3;k++)dst.data[i+k]=Math.round(src.data[i+k]*a+dst.data[i+k]*(1-a));
  }
  ctx.putImageData(dst,x,96);
 }
 Undo.initEdit({elements:Cube.all.slice(),textures:[texture],outliner:true});
 texture.fromDataURL(atlas.toDataURL());
 const group=(n,p,o=[8,0,8],exp=true)=>new Group({name:n,origin:o,export:exp}).addTo(p).init();
 const mats={steel:[2,2,30,30],panel:[34,2,62,30],dark:[66,2,94,30],edge:[98,2,126,30],soot:[130,2,158,30],glass:[160,32,192,64]};
 const cube=(n,g,f,t,m='steel',faceUV={})=>{
  const c=new Cube({name:n,from:f,to:t,origin:[...g.origin],box_uv:false,autouv:0}).addTo(g).init();
  for(const s of Object.keys(c.faces))c.faces[s].extend({uv:faceUV[s]??mats[m],texture:texture.uuid});return c;
 };
 const liquid=group('liquid_heat_window',front),frame=group('liquid_window_frame',liquid),glass=group('liquid_window_glass',liquid),chamber=group('liquid_chamber',liquid);
 for(const c of Cube.all.filter(c=>c.name.startsWith('lower_left_'))){
  if(c.name==='lower_left_slits'){c.remove();continue;}
  c.name=c.name.replace('lower_left_intake','liquid_pressure_frame');c.to[2]=1.06;c.addTo(frame);
 }
 cube('liquid_recessed_heat_glass',glass,[6.95,2.45,1.06],[13.8,3.4,1.1],'glass');
 cube('liquid_tank_floor',chamber,[6.65,2.15,1.06],[14.1,2.45,4.6],'soot');
 cube('liquid_tank_ceiling',chamber,[6.65,3.4,1.06],[14.1,3.7,4.6],'soot');
 cube('liquid_tank_wall_low_x',chamber,[6.65,2.45,1.06],[6.95,3.4,4.6],'soot');
 cube('liquid_tank_wall_high_x',chamber,[13.8,2.45,1.06],[14.1,3.4,4.6],'soot');
 cube('liquid_tank_back',chamber,[6.65,2.15,4.6],[14.1,3.7,4.9],'soot');
 for(const x of [7.02,13.48])for(const y of [2.59,2.88,3.17])cube('liquid_level_tick',chamber,[x,y,1.16],[x+.25,y+.045,1.2],'edge');
 const right=group('right_lower_vent',front);for(const c of Cube.all.filter(c=>c.name.startsWith('lower_right_')))c.addTo(right);
 solid.name='solid_combustion_chamber';
 const preview=group('source_only_flame_preview',solid,[10.325,4.55,3.825],false);preview.visibility=false;
 for(const g of Group.all.filter(g=>['flame_core','flame_mid','flame_outer'].includes(g.name))){g.addTo(preview);g.export=false;g.visibility=false;for(const c of g.children){c.export=false;c.visibility=false;}}
 const anchors=[
  ['solid_fuel_display_anchor',solid,[10.325,4.55,3.825]],
  ['solid_flame_fx_anchor',solid,[10.325,4.8,3.825]],
  ['solid_ember_fx_anchor',solid,[10.325,6,3.825]],
  ['liquid_render_anchor',liquid,[10.375,2.5,2.9]],
  ['liquid_surface_anchor',liquid,[10.375,3.32,2.9]],
  ['liquid_fx_anchor',liquid,[10.375,3.34,2.9]]
 ];
 for(const[n,p,o]of anchors)group(n,p,o);
 for(const[n,p,f,t]of [['solid_fuel_display_volume',solid,[7.65,4.55,2.1],[13,6.3,5.55]],['liquid_display_volume',liquid,[7.35,2.5,1.45],[13.4,3.32,4.35]]]){
  const g=group(n+'_source_only',p,f,false);g.visibility=false;const c=cube(n,g,f,t,'dark');c.export=false;c.visibility=false;
 }
 for(const [side,x0,x1,face]of [['left',15.9,16,'east'],['right',0,.1,'west']]){
  const parent=Group.all.find(g=>g.name===side+'_service_panel');
  const port=group(side+'_fluid_port',parent,[(x0+x1)/2,8,8]);
  cube(side+'_fluid_interface',port,[x0,6,6],[x1,10,10],'panel',{[face]:[side==='left'?96:128,96,side==='left'?128:160,128]});
  group(side==='left'?'fluid_input_anchor':'fluid_output_anchor',port,[side==='left'?16:0,8,8]);
 }
 const changedOutside=Cube.all.filter(c=>before.has(c.uuid)&&!edited.has(c.uuid)&&before.get(c.uuid)!==JSON.stringify(c.getSaveCopy())).map(c=>c.name);
 if(changedOutside.length)throw Error('Unexpected geometry changes: '+changedOutside.join(','));
 Undo.finishEdit('Liquid heat window, fuel anchors and standard fluid ports');Canvas.updateAll();
 return JSON.stringify({cubes:Cube.all.length,groups:Group.all.length,anchors:anchors.map(([n,p,o])=>({name:n,parent:p.name,origin:o})),unrelatedGeometryUnchanged:true});
})()
