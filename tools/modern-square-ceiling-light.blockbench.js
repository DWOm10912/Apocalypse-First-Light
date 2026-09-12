(() => {
  if (Format.id !== 'java_block' || Cube.all.length || Texture.all.length) throw Error('Select an empty Java block project');
  Undo.initEdit({elements: [], textures: [], outliner: true});
  Project.name = 'modern_square_ceiling_light';
  Project.texture_width = Project.texture_height = 128;
  Project.ambientocclusion = false;
  const palette = ['#303438','#3b4044','#454a4e','#262a2e',
    '#555b60','#a6aaa7','#bfc3be','#777e7f',
    '#202629','#d9dedb','#f0f0e9','#e5e8e1',
    '#33383c','#4b5155','#a0a7a6','#eceedf'];
  const canvas = document.createElement('canvas');
  canvas.width = canvas.height = 128;
  const ctx = canvas.getContext('2d');
  palette.forEach((color, i) => {ctx.fillStyle = color; ctx.fillRect(i % 4 * 32, Math.floor(i / 4) * 32, 32, 32);});
  const texture = new Texture({name: 'modern_square_ceiling_light.png', id: '0',
    namespace: 'apocalypse_firstlight', folder: 'block'}).fromDataURL(canvas.toDataURL()).add(false);
  const groups = {};
  function group(name, parent) {
    groups[name] = new Group({name, origin: [8,16,8]}).addTo(parent ? groups[parent] : 'root').init();
  }
  group('modern_ceiling_light_root');
  ['top_mount_base','outer_frame','inner_trim','diffuser_panel'].forEach(n => group(n,'modern_ceiling_light_root'));
  function cube(name, from, to, material, parent, downMaterial = material) {
    const faces = {};
    for (const direction of ['north','south','east','west','up','down']) {
      const m = direction === 'down' ? downMaterial : material;
      const u = m % 4 * 32, v = Math.floor(m / 4) * 32;
      faces[direction] = {uv: [u+2,v+2,u+30,v+30], texture: texture.uuid};
    }
    return new Cube({name, from, to, faces, box_uv: false, autouv: 0,
      shade: parent !== 'diffuser_panel'}).addTo(groups[parent]).init();
  }
  function ring(name, outer, inner, bottom, top, material, parent, downMaterial = material) {
    const a = 8-outer/2, b = 8+outer/2, c = 8-inner/2, d = 8+inner/2;
    cube(name+'_north',[a,bottom,a],[b,top,c],material,parent,downMaterial);
    cube(name+'_south',[a,bottom,d],[b,top,b],material,parent,downMaterial);
    cube(name+'_west',[a,bottom,c],[c,top,d],material,parent,downMaterial);
    cube(name+'_east',[d,bottom,c],[b,top,d],material,parent,downMaterial);
  }
  cube('concealed_mount_plate',[1.8,15.65,1.8],[14.2,16,14.2],3,'top_mount_base');
  cube('closed_upper_housing',[1,15.1,1],[15,15.65,15],1,'top_mount_base');
  ring('main_charcoal_frame',14,11.7,13.6,15.1,0,'outer_frame',3);
  ring('pressed_edge_step',13.8,11.7,13.35,13.6,2,'outer_frame',0);
  ring('fine_lower_return',13.5,11.7,13.2,13.35,1,'outer_frame',0);
  ring('satin_inset_trim',11.7,10.7,13.45,14.2,7,'inner_trim',5);
  ring('diffuser_seating_gasket',10.7,10.5,13.58,14.2,8,'inner_trim');
  cube('single_frosted_diffuser',[2.75,13.6,2.75],[13.25,14.2,13.25],9,'diffuser_panel',10);
  const axes = {north:[2,0,-1],south:[2,1,1],west:[0,0,-1],east:[0,1,1],down:[1,0,-1],up:[1,1,1]};
  let hiddenFaces = 0;
  for (const a of Cube.all) for (const [face,[axis,end,sign]] of Object.entries(axes)) {
    const plane = (end ? a.to : a.from)[axis];
    const remaining = [0,1,2].filter(i=>i!==axis);
    if (Cube.all.some(b=>b!==a && Math.abs((sign>0?b.from:b.to)[axis]-plane)<1e-7 &&
      remaining.every(i=>b.from[i]<=a.from[i]+1e-7 && b.to[i]>=a.to[i]-1e-7))) {
      a.faces[face].texture = null; hiddenFaces++;
    }
  }
  Project.save_path = 'D:/Minecraft Modding/Apocalypse First Light/src/main/blockbench/modern_square_ceiling_light.bbmodel';
  Undo.finishEdit('Build modern square ceiling light');
  Canvas.updateAll();
  return {cubes:Cube.all.length,groups:Group.all.length,texture:texture.uuid,hiddenFaces,
    bounds:{from:[1,13.2,1],to:[15,16,15]},mount:[8,16,8]};
})()
