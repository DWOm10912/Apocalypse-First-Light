(() => {
 if(Project.uuid!=='b8e40069-0a06-8a5d-41df-7d35617d1f0a'||Cube.all.length||Texture.all.length)throw Error('Expected inspected empty city_trash_can');
 Undo.initEdit({elements:[],textures:[],outliner:true});
 const atlas=document.createElement('canvas');atlas.width=atlas.height=128;const ctx=atlas.getContext('2d');
 const colors=['#292c2e','#303335','#373a3c','#444749','#202325','#55595b','#343739','#181b1d'];
 const regions=[];
 colors.forEach((color,i)=>{const x=(i%4)*32,y=Math.floor(i/4)*64;regions.push([x,y,x+32,y+64]);ctx.fillStyle=color;ctx.fillRect(x,y,32,64);ctx.fillStyle=['#333638','#383b3d','#414446','#505456','#292c2e','#666a6c','#3d4042','#242729'][i];ctx.fillRect(x+2,y+2,1,60);ctx.fillRect(x+3,y+2,27,1);ctx.fillStyle='rgba(0,0,0,0.13)';ctx.fillRect(x+27,y+4,3,57);ctx.fillRect(x+3,y+60,27,2);if(i===3||i===5){ctx.fillStyle='#777a7a';ctx.fillRect(x+6,y+2,4,1);ctx.fillRect(x+22,y+61,3,1);}});
 const tex=new Texture({name:'city_trash_can.png',namespace:'apocalypse_firstlight',folder:'block',id:'0'}).fromDataURL(atlas.toDataURL()).add(false);
 Project.texture_width=Project.texture_height=128;Project.ambientocclusion=false;
 const gs={};function group(n,p,origin=[8,0,8]){return gs[n]=new Group({name:n,origin}).addTo(p?gs[p]:'root').init();}
 group('city_trash_can_root');group('trash_can_body','city_trash_can_root');
 for(const n of ['body_shell','body_ribs','top_rim','bottom_rim','body_details'])group(n,'trash_can_body');
 group('lid_assembly','city_trash_can_root',[8,14.8,8]);for(const n of ['lid_main','lid_rings','lid_segments','lid_handle'])group(n,'lid_assembly',[8,15.6,8]);
 for(const side of ['left','right']){const x=side==='left'?1.7:14.3;group(side+'_handle_assembly','city_trash_can_root',[x,12.1,8]);group(side+'_handle_mount',side+'_handle_assembly',[x,12.1,8]);group(side+'_handle_ring',side+'_handle_assembly',[x,12.1,8]);}
 group('optional_inner_bucket_or_misc','city_trash_can_root');
 function box(n,f,t,m,g,rotation=[0,0,0],origin=[8,0,8]){const faces={};for(const d of ['north','south','east','west','up','down'])faces[d]={uv:regions[m].slice(),texture:tex.uuid};return new Cube({name:n,from:f,to:t,faces,box_uv:false,autouv:0,rotation,origin}).addTo(gs[g]).init();}
 function radial(n,i,r,w,depth,y0,y1,m,g){const deg=i*22.5,theta=deg*Math.PI/180;const cx=8+Math.sin(theta)*r,cz=8+Math.cos(theta)*r;const q=Math.floor((deg+45)/90),a=deg-q*90;const dx=q%2?depth:w,dz=q%2?w:depth;box(n,[cx-dx/2,y0,cz-dz/2],[cx+dx/2,y1,cz+dz/2],m,g,[0,a,0],[cx,y0,cz]);}
 for(let i=0;i<16;i++){
  const id=String(i+1).padStart(2,'0');
  for(let band=0;band<3;band++){const y0=[0.8,4.9,9.1][band],y1=[4.9,9.1,13.8][band];radial('body_shell_'+id+'_'+band,i,5.6,2.27,.36,y0,y1,i%3===0?1:0,'body_shell');}
  radial('body_rib_'+id+'_foot',i,5.85,.28,.23,1.6,2.1,2,'body_ribs');radial('body_rib_'+id+'_shaft',i,5.87,.32,.25,2.1,12.75,2,'body_ribs');radial('body_rib_'+id+'_head',i,5.85,.28,.23,12.75,13.2,2,'body_ribs');
  radial('base_rolled_edge_'+id,i,5.65,2.30,.52,.35,.8,3,'bottom_rim');radial('base_seam_'+id,i,5.67,2.30,.42,.8,1.1,0,'bottom_rim');
  radial('mouth_rolled_edge_'+id,i,5.7,2.34,.56,13.8,14.45,3,'top_rim');
  radial('lid_skirt_'+id,i,5.86,2.40,.5,14.45,15.05,3,'lid_rings');
 }
 for(let i=0;i<9;i++){const z0=2.15+i*1.3,z1=z0+1.3;const dz=Math.max(Math.abs(z0-8),Math.abs(z1-8));const width=Math.sqrt(Math.max(0,6.05*6.05-dz*dz));box('lid_stamped_segment_'+(i+1),[8-width,15.05,z0],[8+width,15.45,z1],i%3===0?2:1,'lid_segments');}
 for(let i=0;i<5;i++){const z0=2.7+i*2.12,z1=z0+2.12;const dz=Math.max(Math.abs(z0-8),Math.abs(z1-8));const w=Math.sqrt(Math.max(0,5.65*5.65-dz*dz));box('inner_floor_strip_'+(i+1),[8-w,.65,z0],[8+w,.85,z1],7,'optional_inner_bucket_or_misc');}
 box('lid_handle_plate',[6.1,15.45,6.9],[9.9,15.65,9.1],0,'lid_main');
 for(const x of [6.3,9.1]){box('lid_handle_foot_'+x,[x,15.65,7.55],[x+.6,15.9,8.45],3,'lid_handle');box('lid_handle_upright_'+x,[x+.1,15.9,7.75],[x+.5,16.7,8.25],2,'lid_handle');}
 box('lid_handle_grip',[6.4,16.7,7.75],[9.6,17.1,8.25],3,'lid_handle');box('lid_handle_grip_highlight',[6.7,17.1,7.85],[9.3,17.18,8.15],5,'lid_handle');
 for(const side of ['left','right']){const x=side==='left'?1.7:14.3;const g=side+'_handle_mount',h=side+'_handle_ring';box(side+'_mount_plate',[x-.18,11.6,6.5],[x+.18,12.5,9.5],0,g);box(side+'_hinge_bar',[x-.3,11.95,6.7],[x+.3,12.3,9.3],3,g);for(const z of [6.75,9.05])box(side+'_rivet_'+z,[x-.23,12.28,z],[x+.23,12.48,z+.2],5,g);for(const z of [6.6,9.05]){box(side+'_ring_side_'+z,[x-.2,10.05,z],[x+.2,11.95,z+.35],3,h);box(side+'_ring_corner_'+z,[x-.2,9.85,z],[x+.2,10.05,z+.35],2,h);}box(side+'_ring_bottom',[x-.2,9.65,6.6],[x+.2,9.85,9.4],3,h);box(side+'_ring_top',[x-.2,11.95,6.6],[x+.2,12.15,9.4],2,h);}
 Project.credit='Apocalypse: First Light — City metal trash can';
 Project.save_path='D:/Minecraft Modding/Apocalypse First Light/src/main/blockbench/city_trash_can.bbmodel';
 Undo.finishEdit('City trash can: shell, removable lid and pivoted handles');Canvas.updateAll();
 return {cubes:Cube.all.length,groups:Group.all.length,texture:tex.uuid};
})()
