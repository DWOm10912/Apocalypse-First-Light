(() => {
 const fs=require('fs'),base='D:/Minecraft Modding/Apocalypse First Light/';
 const file='src/main/blockbench/restroom_partition.bbmodel';
 if(fs.existsSync(base+file))throw Error('Output already exists; inspect before replacing');
 const model=JSON.parse(fs.readFileSync(base+'src/main/blockbench/office_cubicle_partition.bbmodel','utf8'));
 if(model.elements.length!==42)throw Error('Unexpected template');
 model.name='restroom_partition';
 const rename=n=>n.replace('office_cubicle_partition_root','restroom_partition_root').replaceAll('fabric','laminate');
 for(const g of model.groups||[])g.name=rename(g.name);
 for(const e of model.elements)e.name=rename(e.name);
 function walk(nodes){for(const n of nodes)if(typeof n==='object'){if(n.name)n.name=rename(n.name);walk(n.children||[]);}}
 walk(model.outliner);
 const canvas=document.createElement('canvas');canvas.width=canvas.height=128;
 const ctx=canvas.getContext('2d');
 const colors=['#696E71','#7A7E80','#565D61','#828789','#C5C4BC','#C9C8C0','#BEBEB6','#CBCAC2','#B2B3AD','#41484C','#8A8F90','#ACAEA8','#5D6568','#C2C2BA','#60676B','#CFCEC6'];
 colors.forEach((c,i)=>{
  const x=i%4*32,y=Math.floor(i/4)*32;
  ctx.fillStyle=c;ctx.fillRect(x,y,32,32);
  if([4,5,6,7,13,15].includes(i)){
   const gradient=ctx.createLinearGradient(x,y,x+12,y+32);gradient.addColorStop(0,c);gradient.addColorStop(1,i===4?'#BEBEB7':c);
   ctx.fillStyle=gradient;ctx.fillRect(x,y,32,32);
  }else{
   ctx.fillStyle='rgba(235,240,239,0.10)';ctx.fillRect(x+1,y+1,30,1);
   ctx.fillStyle='rgba(25,32,35,0.12)';ctx.fillRect(x+1,y+30,30,1);
  }
 });
 const data=canvas.toDataURL('image/png');
 const texPath=base+'src/main/blockbench/textures/restroom_partition.png';
 fs.mkdirSync(base+'src/main/blockbench/textures',{recursive:true});
 const tex=model.textures[0];tex.name='restroom_partition.png';tex.path=texPath;tex.relative_path='textures/restroom_partition.png';tex.source=data;
 fs.writeFileSync(texPath,Buffer.from(data.split(',')[1],'base64'));
 fs.writeFileSync(base+file,JSON.stringify(model,null,2)+'\n');
 newProject(Formats.java_block);Codecs.project.parse(model,base+file);Project.save_path=base+file;Project.saved=true;
 Canvas.updateAll();
 return {name:Project.name,cubes:Cube.all.length,texture:Texture.all[0].uuid,file,texPath};
})()
