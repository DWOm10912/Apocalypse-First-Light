(()=>{
 if(Project.name!=='thermal_generator_3d')throw Error('Wrong project');
 const g=Group.all.find(g=>g.name==='generator_rotor');
 if(!g)throw Error('Missing rotor');
 const old=Cube.all.filter(c=>c.parent===g),rim=old.filter(c=>c.name==='octagonal_rotor_segment');
 if(rim.length!==24)throw Error('Unexpected rim layout');
 const center=[4.1,8.125,4.8];
 const x0=Math.min(...rim.map(c=>c.from[0])),x1=Math.max(...rim.map(c=>c.to[0]));
 const cy=(Math.max(...rim.map(c=>(c.from[1]+c.to[1])/2))+Math.min(...rim.map(c=>(c.from[1]+c.to[1])/2)))/2;
 const offset=[center[0]-(x0+x1)/2,center[1]-cy,0];
 const dark=old.find(c=>c.name==='rotor_spoke'||c.name==='rotor_spoke_0'),metal=old.find(c=>c.name==='rotor_axle'||c.name==='rotor_hub');
 if(!dark||!metal)throw Error('Missing rotor material references');
 const facesOf=c=>Object.fromEntries(Object.entries(c.faces).map(([n,f])=>[n,{uv:[...f.uv],texture:f.texture}]));
 const df=facesOf(dark),mf=facesOf(metal);
 const outside=JSON.stringify(Cube.all.filter(c=>c.parent!==g).map(c=>({id:c.uuid,f:c.from,t:c.to,o:c.origin,r:c.rotation})));
 Undo.initEdit({elements:old,outliner:true});g.origin=[...center];
 for(const c of rim){c.from=c.from.map((v,i)=>v+offset[i]);c.to=c.to.map((v,i)=>v+offset[i]);c.origin=c.rotation.some(v=>v)?[center[0],c.origin[1]+offset[1],c.origin[2]]:[...center];}
 for(const c of old.filter(c=>!rim.includes(c)))c.remove();
 const make=(n,f,t,faces,rot=0,origin=center)=>{const c=new Cube({name:n,from:f,to:t,origin:[...origin],rotation:[rot,0,0],box_uv:false,autouv:0}).addTo(g).init();for(const [s,v]of Object.entries(faces))c.faces[s].extend(v);return c;};
 make('rotor_hub',[3.65,7.675,4.35],[4.55,8.575,5.25],mf);
 make('rotor_axle_left',[1.85,7.825,4.5],[3.65,8.425,5.1],mf);
 make('rotor_axle_right',[4.55,7.825,4.5],[6.35,8.425,5.1],mf);
 for(let i=0;i<8;i++){
  const a=i*Math.PI/4,y=center[1]+1.55*Math.cos(a),z=center[2]+1.55*Math.sin(a);
  let rot=i*45;while(rot>45)rot-=90;const swap=i%4===2||i%4===3;
  const sy=swap?.38:2.2,sz=swap?2.2:.38;
  make('rotor_spoke_'+i,[3.75,y-sy/2,z-sz/2],[4.45,y+sy/2,z+sz/2],df,rot,[4.1,y,z]);
 }
 if(outside!==JSON.stringify(Cube.all.filter(c=>c.parent!==g).map(c=>({id:c.uuid,f:c.from,t:c.to,o:c.origin,r:c.rotation}))))throw Error('Outside geometry changed');
 Undo.finishEdit('Center rotor hub and shaft in unchanged housing');Canvas.updateAll();
 return JSON.stringify({center,offset,rimSegments:rim.length,spokes:8,cubes:Cube.all.length,housingChanged:false});
})()
