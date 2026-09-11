(() => {
  if (Project.uuid !== '29d38e55-9c03-dc4a-cece-fee79e5021c7' || Cube.all.length || Texture.all.length) {
    throw new Error('Expected the empty office_mouse Blockbench project');
  }
  Undo.initEdit({elements: [], textures: [], outliner: true});
  Project.name = 'office_mouse';
  Project.texture_width = 32;
  Project.texture_height = 32;
  Project.ambientocclusion = false;
  const canvas = document.createElement('canvas');
  canvas.width = canvas.height = 32;
  const ctx = canvas.getContext('2d');
  ctx.imageSmoothingEnabled = false;
  const tile = (x, y, color, highlight, shadow) => {
    ctx.fillStyle = color;
    ctx.fillRect(x, y, 16, 16);
    ctx.fillStyle = highlight;
    ctx.fillRect(x + 1, y + 1, 14, 1);
    ctx.fillStyle = shadow;
    ctx.fillRect(x + 1, y + 14, 14, 1);
  };
  tile(0, 0, '#292d30', '#3b4043', '#191c1e');
  tile(16, 0, '#33383b', '#474d50', '#222629');
  tile(0, 16, '#1a1d1f', '#292d30', '#101214');
  tile(16, 16, '#444a4d', '#596064', '#2a2f32');
  for (let x = 18; x <= 28; x += 3) {
    ctx.fillStyle = x % 2 ? '#2b3033' : '#353b3e';
    ctx.fillRect(x, 19, 1, 10);
  }
  const texture = new Texture({
    name: 'office_mouse.png',
    namespace: 'apocalypse_firstlight',
    folder: 'block',
    id: '0'
  }).fromDataURL(canvas.toDataURL()).add(false);
  const groups = {};
  const addGroup = (name, parent) => {
    groups[name] = new Group({name, origin: [8, 0, 8]}).addTo(parent ? groups[parent] : 'root').init();
  };
  addGroup('office_mouse_root');
  addGroup('mouse_body', 'office_mouse_root');
  addGroup('left_button', 'office_mouse_root');
  addGroup('right_button', 'office_mouse_root');
  addGroup('scroll_wheel', 'office_mouse_root');
  const uv = {
    shell: [1, 1, 15, 15],
    button: [17, 1, 31, 15],
    underside: [1, 17, 15, 31],
    wheel: [17, 17, 31, 31]
  };
  const addCube = (name, from, to, parent, faceUv, topUv = faceUv) => {
    const faces = {};
    for (const direction of ['north', 'south', 'east', 'west', 'up', 'down']) {
      faces[direction] = {uv: [...faceUv], texture: texture.uuid};
    }
    faces.up.uv = [...topUv];
    return new Cube({name, from, to, faces, box_uv: false, autouv: 0}).addTo(groups[parent]).init();
  };
  addCube('bottom_plate', [7.15, 0, 6.35], [8.85, 0.15, 9.45], 'mouse_body', uv.underside);
  addCube('lower_shell_core', [7.05, 0.15, 6.65], [8.95, 0.55, 9.35], 'mouse_body', uv.shell);
  addCube('front_lower_nose', [7.3, 0.15, 6.05], [8.7, 0.45, 6.65], 'mouse_body', uv.shell);
  addCube('left_side_shell', [6.95, 0.23, 6.85], [7.05, 0.55, 9.15], 'mouse_body', uv.shell);
  addCube('right_side_shell', [8.95, 0.23, 6.85], [9.05, 0.55, 9.15], 'mouse_body', uv.shell);
  addCube('front_top_deck', [7.15, 0.55, 6.3], [8.85, 0.72, 7.82], 'mouse_body', uv.shell);
  addCube('rear_hump', [7.08, 0.55, 7.82], [8.92, 0.95, 9.1], 'mouse_body', uv.shell);
  addCube('rear_taper_cap', [7.22, 0.55, 9.1], [8.78, 0.8, 9.45], 'mouse_body', uv.shell);
  addCube('left_click_surface', [7.18, 0.72, 6.3], [7.78, 0.85, 7.7], 'left_button', uv.button);
  addCube('left_click_front', [7.28, 0.45, 6.05], [7.78, 0.72, 6.3], 'left_button', uv.button);
  addCube('right_click_surface', [8.22, 0.72, 6.3], [8.82, 0.85, 7.7], 'right_button', uv.button);
  addCube('right_click_front', [8.22, 0.45, 6.05], [8.72, 0.72, 6.3], 'right_button', uv.button);
  addCube('scroll_wheel_center', [7.86, 0.72, 6.92], [8.14, 1, 7.45], 'scroll_wheel', uv.wheel);
  Project.save_path = 'D:/Minecraft Modding/Apocalypse First Light/src/main/blockbench/office_mouse.bbmodel';
  Undo.finishEdit('Create AFL office mouse');
  Canvas.updateAll();
  return {project: Project.name, cubes: Cube.all.length, groups: Group.all.length, texture: [32, 32]};
})()
