(function(){
 const fs=require('fs'),root='D:/Minecraft Modding/Apocalypse First Light/';
 if(Project.name!=='commercial_wall_mounted_sink')throw Error('Select sink');
 for(const c of [...Cube.all])if(/^(rim_surface_|outer_lip_bevel_)/.test(c.name))c.remove();
 function contour(hx,hz,r,cz){let p=[];for(let k=0;k<4;k++){let x=[hx-r,-hx+r,-hx+r,hx-r][k],z=[hz-r,hz-r,-hz+r,-hz+r][k];for(let j=0;j<6;j++){let t=(k*90+j*18)*Math.PI/180;p.push([x+r*Math.cos(t),cz+z+r*Math.sin(t)]);}}return p;}
 const outer=contour(7,4.75,1.1,2.75),inner=contour(5.4,3.2,.85,2.4),g=Group.all.find(g=>g.name==='outer_rim'),t=Texture.all[0];
 const breaks=[...new Set([...outer,...inner].map(p=>Number(p[1].toFixed(6))))].sort((a,b)=>a-b);
 function width(p,z){let x=[];for(let i=0;i<p.length;i++){let a=p[i],b=p[(i+1)%p.length];if(Math.abs(a[1]-b[1])<1e-8)continue;if(z>=Math.min(a[1],b[1])-1e-7&&z<=Math.max(a[1],b[1])+1e-7)x.push(a[0]+(b[0]-a[0])*(z-a[1])/(b[1]-a[1]));}return x.length?Math.max(...x):0;}
 let n=0;function add(a,b,z0,z1){if(b-a<.0001)return;let c=new Cube({name:'continuous_rim_tile_'+n++,from:[a,9.73,z0],to:[b,10.03,z1],autouv:0,box_uv:false}).addTo(g).init();for(const f of Object.values(c.faces)){f.texture=t.uuid;f.uv=[42,10,54,22];}}
 for(let i=0;i<breaks.length-1;i++){let a=breaks[i],b=breaks[i+1];if(b-a<1e-5)continue;let mid=(a+b)/2,o=width(outer,mid),inn=width(inner,mid);if(inn===0)add(-o,o,a,b);else{add(-o,-inn,a,b);add(inn,o,a,b);}}
 Canvas.updateAll();fs.writeFileSync(root+'src/main/blockbench/commercial_wall_mounted_sink.bbmodel',Codecs.project.compile());Project.saved=true;return JSON.stringify({cubes:Cube.all.length,rimTiles:n});
})()
