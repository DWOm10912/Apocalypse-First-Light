(() => {
  if(Project.uuid !== '667f2894-3d66-99b7-bd0d-0877ad7d847d' || Cube.all.length) throw Error('Expected empty Filing Cabinet project');
  const canvas=document.createElement('canvas');canvas.width=canvas.height=128;
  const ctx=canvas.getContext('2d');
  const colors=['#aaa9a2','#b8b7b0','#96968f','#c2c1ba','#454a4d','#34393c','#5b6062','#d0cfc5','#292e31','#92938d','#b0afa8','#777b7b','#a3a39c','#bdbcb5','#505558','#9c9d96'];
  colors.forEach((c,i)=>{ctx.fillStyle=c;ctx.fillRect(i%4*32,Math.floor(i/4)*32,32,32);});
  const image=canvas.toDataURL();
  function build(name,n){
    Project.name=name;Project.texture_width=128;Project.texture_height=128;Project.ambientocclusion=false;
    const tex=new Texture({name:'office_filing_cabinet.png',id:'0'}).fromDataURL(image).add(false);
    const root=new Group({name:name+'_root',origin:[8,0,8]}).init();
    const group=(name,parent,origin=[8,0,8])=>new Group({name,origin}).addTo(parent).init();
    const body=group('cabinet_body',root);const H=1.2+n*7.4;
    const cube=(name,a,b,m,g)=>{const faces={};for(const d of ['north','south','east','west','up','down'])faces[d]={uv:[m%4*32+2,Math.floor(m/4)*32+2,m%4*32+30,Math.floor(m/4)*32+30],texture:tex.uuid};return new Cube({name,from:a,to:b,faces}).addTo(g).init();};
    const base=group('base',body);
    cube('base_plinth',[.2,0,1],[15.8,.7,16],8,base);
    cube('base_front_edge',[.2,.7,1],[15.8,.9,1.3],6,base);
    cube('base_deck',[.2,.7,1.3],[15.8,.9,16],5,base);
    const top=group('top',body);
    cube('top_plate',[0,H-.2,1],[16,H,16],1,top);
    cube('top_front_fold',[0,H-.35,1],[16,H-.2,1.35],3,top);
    cube('top_rear_fold',[0,H-.35,15.65],[16,H-.2,16],2,top);
    for(const side of ['left','right']){
      const g=group('side_'+side,body),x=side==='left'?0:15.5;
      cube(side+'_front_stile',[x,.9,1],[x+.5,H-.35,1.7],1,g);
      cube(side+'_rear_stile',[x,.9,15.4],[x+.5,H-.35,16],0,g);
      cube(side+'_panel',[x,.9,1.7],[x+.5,H-.35,15.4],0,g);
      cube(side+'_upper_return',[x,H-.35,1.35],[x+.5,H-.2,15.65],1,g);
    }
    const back=group('back',body);
    cube('back_sheet',[.5,.9,15.65],[15.5,H-.35,16],2,back);
    cube('back_upper_return',[.5,H-.35,1.35],[15.5,H-.2,15.65],0,back);
    const shell=group('shell',body);
    cube('front_shadow_recess',[.5,.9,1.65],[15.5,H-.35,1.8],8,shell);
    for(let i=0;i<n;i++){
      const y=1+i*7.4,g=group('drawer_'+String(i+1).padStart(2,'0'),root,[8,y+3.55,8]);
      const f=group('drawer_front',g),l=group('label_slot',g),h=group('handle',g);
      cube('drawer_face',[.7,y+.16,1.15],[15.3,y+7.1,1.6],0,f);
      cube('front_top_fold',[.7,y+7.1,1.15],[15.3,y+7.2,1.6],3,f);
      cube('front_bottom_fold',[.7,y+.06,1.15],[15.3,y+.16,1.6],2,f);
      cube('front_left_fold',[.6,y+.06,1.15],[.7,y+7.2,1.6],1,f);
      cube('front_right_fold',[15.3,y+.06,1.15],[15.4,y+7.2,1.6],2,f);
      cube('label_recess',[6.1,y+5.05,1],[9.9,y+6.25,1.15],5,l);
      cube('label_blank',[6.45,y+5.35,.96],[9.55,y+5.95,1],7,l);
      cube('label_frame_top',[6.1,y+6,.88],[9.9,y+6.25,1],6,l);
      cube('label_frame_bottom',[6.1,y+5.05,.88],[9.9,y+5.3,1],4,l);
      cube('label_frame_left',[6.1,y+5.3,.88],[6.4,y+6,1],4,l);
      cube('label_frame_right',[9.6,y+5.3,.88],[9.9,y+6,1],4,l);
      cube('handle_mount_left',[5.1,y+2.75,.25],[5.6,y+3.65,1.15],5,h);
      cube('handle_mount_right',[10.4,y+2.75,.25],[10.9,y+3.65,1.15],5,h);
      cube('handle_bar',[5.1,y+2.75,0],[10.9,y+3.65,.25],4,h);
      cube('handle_top_highlight',[5.1,y+3.65,0],[10.9,y+3.75,.25],6,h);
      cube('handle_lower_return',[5.1,y+2.65,0],[10.9,y+2.75,.25],5,h);
      cube('handle_left_cap',[5,y+2.75,0],[5.1,y+3.65,.25],6,h);
      cube('handle_right_cap',[10.9,y+2.75,0],[11,y+3.65,.25],5,h);
      cube('drawer_rear_lip',[.7,y+.16,1.6],[15.3,y+.36,1.65],2,f);
      cube('drawer_upper_lip',[.7,y+6.9,1.6],[15.3,y+7.1,1.65],1,f);
      cube('drawer_left_lip',[.7,y+.36,1.6],[.9,y+6.9,1.65],2,f);
      cube('drawer_right_lip',[15.1,y+.36,1.6],[15.3,y+6.9,1.65],2,f);
    }
    Project.save_path='D:/Minecraft Modding/Apocalypse First Light/src/main/blockbench/'+name+'.bbmodel';
    Canvas.updateAll();return {name,cubes:Cube.all.length,height:H};
  }
  const low=build('low_filing_cabinet',2);
  newProject(Formats.java_block);const tall=build('tall_filing_cabinet',4);
  return {low,tall};
})()
