(async function(){
const dir='D:/Minecraft Modding/Apocalypse First Light/src/main/blockbench/ammo_9x19_cube_vFinal/';
const canvas=document.createElement('canvas');canvas.width=canvas.height=64;const ctx=canvas.getContext('2d');
const hex=a=>'#'+a.map(v=>Math.max(0,Math.min(255,Math.round(v))).toString(16).padStart(2,'0')).join('');
for(let y=0;y<32;y++)for(let x=0;x<64;x++){
 const copper=x>=32,u=x%32,base=copper?[157,88,39]:[174,132,43];
 let light=u<4?-30:u<8?-17:u<12?3:u<15?34:u<18?53:u<21?17:u<25?-2:-22;
 light+=y===0?-13:y>28?-16:y>26?-7:0;
 if(copper&&y<3&&u>=12&&u<18)light+=7;
 ctx.fillStyle=hex(base.map((v,i)=>v+light*(i===2?.70:1)));ctx.fillRect(x,y,1,1);
}
const tiles=[[158,116,37],[205,187,126],[91,65,27],[60,43,24],[195,151,61],[183,112,58],[211,171,77],[112,75,30]];
tiles.forEach((base,i)=>{for(let y=0;y<16;y++)for(let x=0;x<16;x++){
 const edge=Math.min(x,y,15-x,15-y),d=(edge===0?-16:edge===1?9:0)+(x>=5&&x<=7?15:x>11?-9:0);
 ctx.fillStyle=hex(base.map(v=>v+d));ctx.fillRect((i%4)*16+x,32+Math.floor(i/4)*16+y,1,1);
}});
const png=canvas.toDataURL();Blockbench.writeFile(dir+'9x19mm_cube_vFinal_shared.png',{savetype:'image',content:png});
const report=[];
for(const kind of ['round','casing']){
 newProject(Formats.java_block);Project.name='9x19mm_'+kind+'_cube_vFinal';Project.texture_width=Project.texture_height=64;Project.box_uv=false;
 const tex=new Texture({name:'9x19mm_cube_vFinal_shared.png',id:'0',width:64,height:64,uv_width:64,uv_height:64}).fromDataURL(png).add(false);
 const root=new Group({name:Project.name,origin:[8,8,8]}).init(),parts=[];
 const groups={};for(const name of ['case_body','case_mouth','rim_and_groove','primer','copper_projectile'])groups[name]=new Group({name,origin:[8,8,8]}).addTo(root).init();
 const tileMap={head:0,primer:1,groove:2,inside:3,brass:4,copper:5,mouth:6,seat:7};
 function cube(name,x0,x1,y0,y1,z0,z1,mat,r,group){
  const c=new Cube({name,from:[8+x0,y0,8+z0],to:[8+x1,y1,8+z1],origin:[8,8,8],rotation:[0,0,0],box_uv:false,autouv:0}).addTo(groups[group]).init();
  for(const [face,f] of Object.entries(c.faces)){
   f.texture=tex.uuid;
   if((mat==='brass'||mat==='copper')&&face!=='up'&&face!=='down'){
    const horizontal=face==='north'||face==='south',a=horizontal?x0:z0,b=horizontal?x1:z1,offset=mat==='copper'?32:0;
    const u0=offset+.2+(a/r+1)*15.8,u1=offset+.2+(b/r+1)*15.8;
    const top=mat==='copper'?12.35:9.7,bottom=mat==='copper'?9.7:1.5;
    const v0=.2+(top-y1)/(top-bottom)*31.6,v1=.2+(top-y0)/(top-bottom)*31.6;
    f.uv=[u0,Math.max(.2,v0),u1,Math.min(31.8,v1)];
   }else{
    let tile=tileMap[mat];if(face==='down'&&group==='rim_and_groove'&&mat==='brass')tile=0;
    const tx=tile%4*16,ty=32+Math.floor(tile/4)*16;
    f.uv=[tx+.3+(x0/r+1)*7.7,ty+.3+(z0/r+1)*7.7,tx+.3+(x1/r+1)*7.7,ty+.3+(z1/r+1)*7.7];
    if(face!=='up'&&face!=='down')f.uv=[tx+2,ty+3,tx+14,ty+12];
   }
  }
  parts.push(c);return c;
 }
 function section(name,r,y0,y1,mat,group){
  const a=r*.78;
  cube(name+'_middle',-r,r,y0,y1,-a,a,mat,r,group);
  cube(name+'_north',-a,a,y0,y1,-r,-a,mat,r,group);
  cube(name+'_south',-a,a,y0,y1,a,r,mat,r,group);
 }
 function ring(name,r,y0,y1,mat){
  const a=r*.78,b=1.40;
  const rects=[[-r,-b,-b,b],[b,r,-b,b],[-r,r,-a,-b],[-r,r,b,a],[-a,a,-r,-a],[-a,a,a,r]];
  rects.forEach(([x0,x1,z0,z1],i)=>{
   const c=cube(name+'_'+i,x0,x1,y0,y1,z0,z1,mat,r,'case_mouth');
   const inner=i===0?'east':i===1?'west':i===2?'south':i===3?'north':null;
   if(inner)c.faces[inner].uv=[49,34,63,45];
  });
 }
 section('restrained_rim',2.45,1,1.24,'brass','rim_and_groove');
 section('extractor_groove',2.26,1.24,1.50,'groove','rim_and_groove');
 section('case_lower',2.42,1.50,5.55,'brass','case_body');
 section('case_upper',2.34,5.55,kind==='round'?9.52:8.55,'brass','case_body');
 cube('primer_seat',-.98,.98,.963,1,-.98,.98,'groove',1.10,'primer');
 cube('primer_face',-.77,.77,.938,.963,-.77,.77,'primer',.85,'primer');
 if(kind==='round'){
  section('mouth_lip',2.37,9.52,9.70,'mouth','case_mouth');
  section('fmj_straight_seat',2.18,9.70,10.56,'copper','copper_projectile');
  section('fmj_shoulder',1.92,10.56,11.39,'copper','copper_projectile');
  section('fmj_taper',1.38,11.39,12.00,'copper','copper_projectile');
  section('fmj_blunt_tip',.76,12.00,12.35,'copper','copper_projectile');
 }else{
  ring('shallow_cavity_wall',2.34,8.55,9.52,'brass');ring('thin_mouth_lip',2.37,9.52,9.70,'mouth');
  parts.filter(c=>c.name.startsWith('case_upper')).forEach(c=>c.faces.up.uv=[49,33,63,47]);
 }
 const planes={west:[0,0],east:[0,1],down:[1,0],up:[1,1],north:[2,0],south:[2,1]};
 for(const c of parts)for(const [face,[axis,high]] of Object.entries(planes)){
  const plane=(high?c.to:c.from)[axis],other=[0,1,2].filter(a=>a!==axis);
  if(parts.some(d=>d!==c&&Math.abs((high?d.from:d.to)[axis]-plane)<1e-6&&other.every(a=>d.from[a]<=c.from[a]+1e-6&&d.to[a]>=c.to[a]-1e-6)))c.faces[face].texture=null;
 }
 groups.copper_projectile.children.length||groups.copper_projectile.remove();
 for(const c of parts){c.preview_controller.updateGeometry(c);c.preview_controller.updateUV(c);c.preview_controller.updateFaces(c);}
 Canvas.updateAll();await new Promise(resolve=>setTimeout(resolve,350));
 Project.save_path=dir+Project.name+'.bbmodel';Blockbench.writeFile(Project.save_path,{content:Codecs.project.compile()});Project.saved=true;
 const compiled=Codecs.java_block.compile();report.push({name:Project.name,cubes:parts.length,mesh:Mesh.all.length,javaElements:JSON.parse(compiled).elements.length});
 const p=Preview.selected;p.setProjectionMode(true);p.camOrtho.zoom=1.35;p.camOrtho.updateProjectionMatrix();
 for(const [label,pos] of [['three_quarter',[24,18,32]],['side',[8,6.65,38]],['top',[8,40,8.01]],['bottom',[8,-30,8.01]]]){
  p.camera.position.set(...pos);p.controls.target.set(8,kind==='round'?6.65:5.35,8);p.controls.update();p.render();
  await new Promise(resolve=>p.screenshot({width:600,height:720},data=>{Blockbench.writeFile(dir+kind+'_'+label+'.png',{savetype:'image',content:data});resolve();}));
 }
}
return JSON.stringify(report);
})();
