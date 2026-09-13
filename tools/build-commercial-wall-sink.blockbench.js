(async()=>{
 const fs=require('fs'),root='D:/Minecraft Modding/Apocalypse First Light/';
 const dest=root+'src/main/blockbench/commercial_wall_mounted_sink.bbmodel';
 if(fs.existsSync(dest))throw Error('Existing sink source: inspect before replacing');
 const canvas=document.createElement('canvas');canvas.width=canvas.height=128;
 const ctx=canvas.getContext('2d'),colors=['#e2e7e8','#f0f3f3','#d1d8da','#bdc7ca','#aab7bc','#dce3e5','#f5f7f7','#c7d0d3','#919ea5','#b7c2c8','#737f87','#d8e0e4','#444e55','#66747c','#a2afb5','#e8edee'];
 colors.forEach((v,i)=>{let x=i%4*32,y=Math.floor(i/4)*32;ctx.fillStyle=v;ctx.fillRect(x,y,32,32);ctx.fillStyle='rgba(255,255,255,.065)';ctx.fillRect(x,y,32,6);ctx.fillStyle='rgba(0,0,0,.025)';ctx.fillRect(x,y+26,32,6);});
 newProject(Formats.free);Project.name='commercial_wall_mounted_sink';Project.texture_width=Project.texture_height=128;
 const texPath=root+'src/main/blockbench/textures/commercial_wall_mounted_sink.png';
 const tex=new Texture({name:'commercial_wall_mounted_sink.png',width:128,height:128}).fromDataURL(canvas.toDataURL()).add();tex.path=texPath;
 const groups={};function group(n,p){return groups[n]=new Group({name:n,origin:[0,0,0]}).addTo(p?groups[p]:undefined).init();}
 group('sink_root');group('ceramic_body','sink_root');for(const n of ['outer_rim','inner_basin','underside_shell','rear_deck'])group(n,'ceramic_body');
 group('faucet_assembly','sink_root');for(const n of ['faucet_base','spout','single_lever'])group(n,'faucet_assembly');
 group('drain_assembly','sink_root');for(const n of ['drain_ring','p_trap_or_pipe','wall_connector'])group(n,'drain_assembly');group('wall_mount_support','sink_root');
 function cube(g,n,c,s,m=0,rot=[0,0,0]){if(s.some(v=>v<=0))throw Error(n+' invalid thickness');let a=new Cube({name:n,from:c.map((v,i)=>v-s[i]/2),to:c.map((v,i)=>v+s[i]/2),origin:c,rotation:rot,autouv:0,box_uv:false}).addTo(groups[g]).init();for(const [side,f]of Object.entries(a.faces)){let mat=side==='down'&&m===0?2:m,x=mat%4*32,y=Math.floor(mat/4)*32;f.texture=tex.uuid;f.uv=[x+2,y+2,x+30,y+30];}return a;}
 function contour(hx,hz,r,cz,y){let a=[];for(let k=0;k<4;k++){let cx=[hx-r,-hx+r,-hx+r,hx-r][k],z=[hz-r,hz-r,-hz+r,-hz+r][k];for(let j=0;j<6;j++){let t=(k*90+j*18)*Math.PI/180;a.push(new THREE.Vector3(cx+r*Math.cos(t),y,cz+z+r*Math.sin(t)));}}return a;}
 // Sloping ceramic courses follow a rounded rectangular outline, using solid cuboids.
 function loft(g,n,lower,upper,thickness,mat){for(let i=0;i<upper.length;i++){let j=(i+1)%upper.length,u=upper[i].clone().add(upper[j]).multiplyScalar(.5),l=lower[i].clone().add(lower[j]).multiplyScalar(.5),x=upper[j].clone().sub(upper[i]).normalize(),dy=u.clone().sub(l),y=dy.clone().addScaledVector(x,-dy.dot(x)),h=y.length();y.normalize();let z=x.clone().cross(y).normalize(),q=new THREE.Matrix4().makeBasis(x,y,z),r=new THREE.Euler().setFromRotationMatrix(q,'ZYX');let w=Math.max(upper[i].distanceTo(upper[j]),lower[i].distanceTo(lower[j]))+.06;cube(g,n+'_'+i,u.add(l).multiplyScalar(.5).toArray(),[w,h+.06,thickness],mat,[r.x,r.y,r.z].map(v=>v*180/Math.PI));}}
 const outer=[contour(5.8,3.7,.9,2.75,7.15),contour(6.2,4.1,1,2.75,7.8),contour(6.8,4.55,1.05,2.75,8.8),contour(7,4.75,1.1,2.75,9.85)];
 for(let k=0;k<3;k++)loft('underside_shell','outer_ceramic_'+k,outer[k],outer[k+1],.4,k===0?2:0);
 const inside=[contour(4.45,2.35,.7,2.4,7.6),contour(4.8,2.65,.75,2.4,8.25),contour(5.1,2.95,.8,2.4,9.05),contour(5.4,3.2,.85,2.4,9.86)];
 for(let k=0;k<3;k++)loft('inner_basin','basin_slope_'+k,inside[k],inside[k+1],.32,k===0?2:0);
 // Horizontal rim bridges the two wall contours, with overlap at both edges.
 for(let i=0;i<24;i++){let j=(i+1)%24,o=outer[3][i].clone().add(outer[3][j]).multiplyScalar(.5),v=inside[3][i].clone().add(inside[3][j]).multiplyScalar(.5),t=outer[3][j].clone().sub(outer[3][i]).normalize(),normal=new THREE.Vector3(-t.z,0,t.x),d=Math.abs(o.clone().sub(v).dot(normal));let c=o.add(v).multiplyScalar(.5);c.y=9.88;let yaw=-Math.atan2(t.z,t.x)*180/Math.PI;cube('outer_rim','rim_surface_'+i,c.toArray(),[outer[3][i].distanceTo(outer[3][j])+.06,.3,d+.12],1,[0,yaw,0]);}
 loft('outer_rim','outer_lip_bevel',contour(7,4.75,1.1,2.75,9.75),contour(6.93,4.68,1.1,2.75,10),.19,1);
 cube('inner_basin','solid_ceramic_floor',[0,7.36,2.4],[9.2,.54,5.05],2);
 cube('rear_deck','back_mount_face',[0,8.85,7.7],[13.6,2.25,.6],0);
 cube('rear_deck','faucet_deck',[0,10.04,6.68],[13.6,.38,2.2],1);
 cube('rear_deck','backsplash',[0,10.38,7.67],[13.4,.5,.55],0);
 cube('rear_deck','backsplash_top_bevel',[0,10.63,7.67],[13.2,.16,.48],1);
 // A small dark overflow recess, framed in ceramic below the faucet.
 cube('inner_basin','overflow_recess',[0,9.17,5.43],[.52,.42,.08],12);
 for(const x of [-.32,.32])cube('inner_basin','overflow_edge_'+x,[x,9.17,5.4],[.12,.62,.16],2);
 for(const y of [8.89,9.45])cube('inner_basin','overflow_edge_'+y,[0,y,5.4],[.64,.1,.16],1);
 // Commercial single lever faucet: rectangular chrome with restrained edge facets.
 cube('faucet_base','gasket',[0,10.3,6.5],[1.55,.13,1.5],10);
 cube('faucet_base','chrome_plinth',[0,10.44,6.5],[1.4,.2,1.35],9);
 cube('faucet_base','tap_column',[0,11.6,6.5],[.9,2.2,.95],8);
 for(const x of [-.43,.43])cube('faucet_base','column_chamfer_'+x,[x,11.6,6.09],[.16,2.16,.16],9,[0,45,0]);
 cube('faucet_base','front_highlight',[0,11.6,6.018],[.68,2.05,.045],11);
 cube('spout','neck',[0,12.54,6.24],[1.02,.66,1.28],8);
 cube('spout','spout_body',[0,12.52,4.8],[1,.5,2.5],9);
 cube('spout','spout_top',[0,12.795,4.76],[.84,.08,2.3],11);
 for(const x of [-.46,.46])cube('spout','spout_bevel_'+x,[x,12.7,4.76],[.13,.13,2.3],9,[0,0,45]);
 cube('spout','spout_tip',[0,12.51,3.55],[1.02,.48,.24],11);
 cube('spout','aerator_mount',[0,12.22,3.77],[.65,.17,.54],10);
 cube('spout','dry_aerator',[0,12.11,3.77],[.46,.08,.36],12);
 cube('single_lever','pivot',[0,13.04,6.5],[.65,.48,.65],10);
 cube('single_lever','lever_root',[0,13.29,6.46],[1.03,.35,1.08],9);
 cube('single_lever','lever',[0,13.37,5.54],[.9,.22,2.1],9,[-3,0,0]);
 cube('single_lever','lever_top',[0,13.5,5.55],[.76,.045,1.95],11,[-3,0,0]);
 // Dry drain strainer. Recessed dark slots do not represent liquid.
 cube('drain_ring','strainer_frame',[0,7.68,2.4],[1.14,.14,1.14],9);
 cube('drain_ring','drain_recess',[0,7.76,2.4],[.86,.06,.86],12);
 for(let i=0;i<5;i++)cube('drain_ring','strainer_bar_'+i,[-.36+i*.18,7.81,2.4],[.07,.06,.85],9);
 function pipe(n,c,s){cube('p_trap_or_pipe',n,c,s,8);}
 pipe('tailpiece',[0,5.86,2.4],[.65,2.6,.65]);
 for(const y of [7,5]){pipe('coupling_'+y,[0,y,2.4],[.95,.45,.95]);for(const x of [-.43,.43])cube('p_trap_or_pipe','coupling_chamfer_'+y+'_'+x,[x,y,2.4],[.18,.45,.8],9,[0,45,0]);}
 pipe('trap_front',[0,3.93,2.4],[.8,2.2,.8]);
 // A continuous U bend of overlapping short solid links in the Y/Z plane.
 const centerY=3.1,centerZ=3.32,radius=.92;
 for(let i=0;i<10;i++){let a=Math.PI+i*Math.PI/10,b=Math.PI+(i+1)*Math.PI/10;let p=new THREE.Vector3(0,centerY+radius*Math.sin(a),centerZ+radius*Math.cos(a)),q=new THREE.Vector3(0,centerY+radius*Math.sin(b),centerZ+radius*Math.cos(b)),d=q.clone().sub(p),length=d.length(),rot=-Math.atan2(d.z,d.y)*180/Math.PI;cube('p_trap_or_pipe','u_bend_'+i,p.add(q).multiplyScalar(.5).toArray(),[.8,length+.09,.8],8,[rot,0,0]);}
 pipe('trap_rear_rise',[0,3.66,4.24],[.8,1.32,.8]);
 pipe('outlet_elbow',[0,4.3,4.38],[.85,.85,1.05]);
 pipe('wall_outlet',[0,4.35,6.05],[.7,.7,3.35]);
 for(const z of [4.9,7.3])cube('wall_connector','outlet_collar_'+z,[0,4.35,z],[.98,.98,.45],9);
 cube('wall_connector','wall_flange',[0,4.35,7.83],[1.65,1.65,.34],9);
 cube('wall_connector','wall_flange_inset',[0,4.35,7.625],[1.3,1.3,.1],8);
 // Mounting brackets are compact, legible from below, and terminate at Z=8.
 for(const x of [-4.9,4.9]){cube('wall_mount_support','wall_bracket_'+x,[x,7.7,7.8],[.65,2.5,.4],10);cube('wall_mount_support','support_arm_'+x,[x,7.04,6.32],[.6,.32,2.9],8);cube('wall_mount_support','support_gusset_'+x,[x,7.05,7.03],[.45,1.8,.28],8,[-35,0,0]);for(const y of [6.7,8.65])cube('wall_mount_support','mount_bolt_'+x+'_'+y,[x,y,7.555],[.22,.22,.12],9);}
 const all=Cube.all;if(all.some(c=>c.to.some((v,i)=>v<=c.from[i])))throw Error('Zero thickness cube');
 Canvas.updateAll();fs.mkdirSync(root+'src/main/blockbench/textures',{recursive:true});fs.writeFileSync(texPath,Buffer.from(canvas.toDataURL().split(',')[1],'base64'));tex.saved=true;
 fs.writeFileSync(dest,Codecs.project.compile());Project.save_path=dest;Project.saved=true;
 const b=new THREE.Box3();for(const c of all)for(const p of c.getGlobalVertexPositions())b.expandByPoint(new THREE.Vector3(...p));
 return JSON.stringify({cubes:all.length,texture:[128,128],bounds:{min:b.min.toArray(),max:b.max.toArray()},size:b.getSize(new THREE.Vector3()).toArray(),source:dest});
})()
