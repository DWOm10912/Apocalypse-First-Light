// Run inside Blockbench's JavaScript context; creates an independent GeckoLib asset.
(() => {
  newProject(Formats.geckolib_model);
  Project.name='pistol_suppressor_01';Project.model_identifier='pistol_suppressor_01';
  Project.texture_width=64;Project.texture_height=128;Project.box_uv=false;
  const canvas=document.createElement('canvas');canvas.width=64;canvas.height=128;
  const ctx=canvas.getContext('2d');ctx.fillStyle='#24282a';ctx.fillRect(0,0,64,128);
  const colors=['#303437','#262a2c','#393d40','#1b1e20','#101416','#45494b'];
  colors.forEach((color,i)=>{ctx.fillStyle=color;ctx.fillRect(0,i*20,64,20);ctx.fillStyle=i===4?'#14181a':'#34383a';ctx.fillRect(2,i*20+1,1,18);});
  const tex=new Texture({name:'pistol_suppressor_01.png'}).fromDataURL(canvas.toDataURL()).add(false);
  tex.uv_width=64;tex.uv_height=128;
  const root=new Group({name:'pistol_suppressor_root',origin:[0,0,0]}).init();
  const groups={};for(const name of ['mount','body','front_cap','muzzle_exit_anchor'])groups[name]=new Group({name,origin:name==='muzzle_exit_anchor'?[0,0,-9.1]:[0,0,0]}).addTo(root).init();
  function cube(name,from,to,rotation,parent,band,omit=[]){
    const c=new Cube({name,from,to,origin:[0,0,0],rotation:[0,0,rotation],box_uv:false,autouv:0}).addTo(parent).init();
    for(const [key,face] of Object.entries(c.faces)){
      const dims=key==='north'||key==='south'?[to[0]-from[0],to[1]-from[1]]:key==='east'||key==='west'?[to[2]-from[2],to[1]-from[1]]:[to[0]-from[0],to[2]-from[2]];
      face.texture=omit.includes(key)?null:tex.uuid;
      face.uv=[2,band*20+1,2+Math.max(.25,dims[0]*2),band*20+1+Math.max(.25,dims[1]*2)];
    }
    return c;
  }
  function tube(name,start,end,r,thickness,parent,band,ends=false){
    const w=2*r*Math.tan(Math.PI/8);
    for(let i=0;i<8;i++){
      // Interior joins overlap; exposed end plates are staggered slightly to avoid coplanar seams.
      const inset=ends?(i%2)*.002:0;
      cube(name+'_'+i,[-w/2,r-thickness,-end+inset],[w/2,r,-start],45*i,parent,band,ends?[]:['north','south']);
    }
  }
  tube('rear_socket',0,.7,.53,.22,groups.mount,1,true);
  tube('rear_shoulder',.7,1.05,.70,.38,groups.mount,2,true);
  tube('main_shell',1.05,8.65,.75,.2,groups.body,0);
  tube('rear_retaining_ring',1.05,1.35,.79,.25,groups.body,2,true);
  tube('front_retaining_ring',8.2,8.5,.79,.25,groups.body,2,true);
  tube('front_rim',8.65,9.1,.73,.43,groups.front_cap,2,true);
  tube('recessed_bore',7.9,9.098,.305,.11,groups.front_cap,4);
  cube('bore_shadow_back',[-.29,-.29,-7.92],[.29,.29,-7.9],0,groups.front_cap,4);
  Canvas.updateAll();unselectAllElements();
  return JSON.stringify({project:Project.name,cubes:Cube.all.length,texture:tex.uuid,length:9.1,diameter:1.58});
})()
