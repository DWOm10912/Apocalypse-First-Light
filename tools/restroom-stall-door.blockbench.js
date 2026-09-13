(async () => {
  const fs = require('fs');
  const base = 'D:/Minecraft Modding/Apocalypse First Light/';
  const path = base + 'src/main/blockbench/restroom_stall_door.bbmodel';
  if (fs.existsSync(path)) throw Error('Door source exists; inspect before replacing');
  const source = JSON.parse(fs.readFileSync(base + 'src/main/blockbench/restroom_partition.bbmodel', 'utf8'));
  const texturePath = base + 'src/main/blockbench/textures/restroom_stall_door.png';
  const atlas = source.textures[0].source;
  newProject(Formats.free);
  Project.name = 'restroom_stall_door';
  Project.texture_width = Project.texture_height = 128;
  const texture = new Texture({name:'restroom_stall_door.png', width:128, height:128}).fromDataURL(atlas).add();
  texture.path = texturePath;
  const pivot = [14.6, 0, 6.95];
  const root = new Group({name:'restroom_stall_door_root', origin:pivot}).init();
  const leaf = new Group({name:'door_leaf', origin:pivot}).addTo(root).init();
  const groups = {};
  for (const name of ['panel','frame','hinge_upper','hinge_lower','handle','latch']) {
    groups[name] = new Group({name, origin:pivot}).addTo(leaf).init();
  }
  const uv = i => {const x=i%4*32,y=Math.floor(i/4)*32;return [x+1,y+1,x+31,y+31];};
  const cube = (group,name,from,to,material) => {
    const c = new Cube({name,from,to,origin:pivot,autouv:0,box_uv:false}).addTo(groups[group]).init();
    for (const face of Object.values(c.faces)) {face.texture=texture.uuid;face.uv=uv(material);}
    return c;
  };
  cube('panel','laminate_core',[1.7,3.7,7.45],[14.3,29.3,8.55],8);
  cube('panel','laminate_front',[1.7,3.7,7.35],[14.3,29.3,7.45],4);
  cube('panel','laminate_back',[1.7,3.7,8.55],[14.3,29.3,8.65],4);
  const edges = [
    ['latch_edge',1.5,3.5,1.7,29.5],['hinge_edge',14.3,3.5,14.5,29.5],
    ['bottom_edge',1.7,3.5,14.3,3.7],['top_edge',1.7,29.3,14.3,29.5]
  ];
  for (const [name,x,y,X,Y] of edges) {
    cube('frame',name+'_core',[x,y,7.35],[X,Y,8.65],0);
    cube('frame',name+'_front',[x,y,7.25],[X,Y,7.35],3);
    cube('frame',name+'_back',[x,y,8.65],[X,Y,8.75],14);
  }
  for (const [group,y] of [['hinge_upper',25],['hinge_lower',6]]) {
    cube(group,group+'_leaf_plate',[13.55,y,6.95],[14.25,y+1.8,7.35],0);
    cube(group,group+'_bridge',[14.25,y+0.3,6.95],[14.35,y+1.5,7.35],2);
    cube(group,group+'_knuckle_lower',[14.35,y,6.7],[14.85,y+0.3,7.2],3);
    cube(group,group+'_knuckle_body',[14.35,y+0.3,6.7],[14.85,y+1.5,7.2],0);
    cube(group,group+'_knuckle_upper',[14.35,y+1.5,6.7],[14.85,y+1.8,7.2],3);
    for (const offset of [0.3,1.3]) cube(group,group+'_fastener_'+offset,
      [13.75,y+offset,6.91],[13.98,y+offset+0.2,6.95],2);
  }
  cube('handle','pull_backplate',[2.2,14.7,7.1],[3.1,18,7.35],0);
  cube('handle','pull_lower_mount',[2.4,15,6.55],[2.9,15.35,7.1],2);
  cube('handle','pull_upper_mount',[2.4,17.35,6.55],[2.9,17.7,7.1],2);
  cube('handle','pull_grip',[2.4,15,6.25],[2.9,17.7,6.55],0);
  cube('handle','pull_grip_highlight',[2.4,15,6.2],[2.9,17.7,6.25],3);
  for (const y of [14.8,17.8]) cube('handle','pull_fastener_'+y,[2.5,y,7.05],[2.75,y+0.12,7.1],2);
  cube('latch','neutral_latch_housing',[1.85,15.2,8.65],[3.25,17.4,8.9],2);
  cube('latch','neutral_latch_face',[1.95,15.3,8.9],[3.15,17.3,9.05],0);
  cube('latch','neutral_slide',[2.25,16.05,9.05],[3.6,16.55,9.25],3);
  cube('latch','neutral_slide_grip',[3.15,16.05,9.25],[3.6,16.55,9.55],0);
  for (const y of [15.45,16.95]) cube('latch','latch_fastener_'+y,[2.1,y,9.05],[2.3,y+0.15,9.09],2);
  Cube.selected.empty(); Group.all.forEach(g=>g.selected=false);
  Canvas.updateAll();
  fs.writeFileSync(texturePath,Buffer.from(atlas.split(',')[1],'base64'));
  fs.writeFileSync(path,Codecs.project.compile());
  Project.save_path=path; Project.saved=true;
  const model=JSON.parse(fs.readFileSync(path,'utf8'));
  const preview=JSON.parse(JSON.stringify(model));
  preview.name='restroom_stall_door_installation_preview';
  const refs=new Group({name:'preview_only_posts',origin:[8,0,8]});
  preview.groups.push({name:refs.name,uuid:refs.uuid,origin:[8,0,8],rotation:[0,0,0],export:false,visibility:true});
  const children=[];
  for (const e of source.elements.filter(e=>/post|foot/.test(e.name))) {
    const c=JSON.parse(JSON.stringify(e));c.uuid=guid();c.name='preview_only_'+e.name;c.export=false;
    for(const face of Object.values(c.faces))face.texture=0;
    preview.elements.push(c);children.push(c.uuid);
  }
  preview.outliner.push({uuid:refs.uuid,children});
  const previewPath=base+'src/main/blockbench/previews/restroom_stall_door_installation_preview.bbmodel';
  fs.writeFileSync(previewPath,JSON.stringify(preview,null,2)+'\n');
  return {file:path,texture:texturePath,cubes:Cube.all.length,pivot,preview:previewPath,format:Format.id};
})()
