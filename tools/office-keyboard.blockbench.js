(() => {
  if (Project.uuid !== '66c1c3c2-f506-2c10-83ec-fd2b7aeb4f2f' || Cube.all.length || Texture.all.length) {
    throw new Error('Expected the empty keyboard Blockbench project');
  }

  Undo.initEdit({elements: [], textures: [], outliner: true});
  Project.name = 'office_keyboard';
  Project.texture_width = 64;
  Project.texture_height = 64;
  Project.ambientocclusion = false;

  const canvas = document.createElement('canvas');
  canvas.width = canvas.height = 64;
  const ctx = canvas.getContext('2d');
  ctx.imageSmoothingEnabled = false;

  ctx.fillStyle = '#202326';
  ctx.fillRect(0, 0, 64, 64);
  const tile = (x, y, color, top, bottom) => {
    ctx.fillStyle = color;
    ctx.fillRect(x, y, 16, 16);
    ctx.fillStyle = top;
    ctx.fillRect(x + 1, y + 1, 14, 1);
    ctx.fillStyle = bottom;
    ctx.fillRect(x + 1, y + 14, 14, 1);
  };
  tile(0, 0, '#25292c', '#34393c', '#191c1e');
  tile(16, 0, '#2d3134', '#404549', '#202326');
  tile(32, 0, '#383d40', '#4b5154', '#262a2d');
  tile(48, 0, '#171a1c', '#24282a', '#0f1112');

  const drawKeyRow = (x, y, width, cells, offset = 0) => {
    ctx.fillStyle = '#16191b';
    ctx.fillRect(x, y, width, 4);
    const gap = 1;
    const keyWidth = Math.max(2, Math.floor((width - gap * (cells + 1)) / cells));
    let cursor = x + gap + offset;
    for (let i = 0; i < cells && cursor + keyWidth <= x + width - 1; i++) {
      ctx.fillStyle = i % 3 === 1 ? '#303538' : '#2b3033';
      ctx.fillRect(cursor, y + 1, keyWidth, 2);
      ctx.fillStyle = '#3b4144';
      ctx.fillRect(cursor, y + 1, keyWidth, 1);
      cursor += keyWidth + gap;
    }
  };
  drawKeyRow(0, 16, 40, 12);
  drawKeyRow(0, 20, 40, 11, 1);
  drawKeyRow(0, 24, 40, 10, 2);
  drawKeyRow(0, 28, 40, 9, 3);
  drawKeyRow(40, 16, 8, 3);
  drawKeyRow(40, 20, 8, 3);
  drawKeyRow(40, 24, 8, 3);
  drawKeyRow(48, 16, 16, 4);
  drawKeyRow(48, 20, 16, 4);
  drawKeyRow(48, 24, 16, 4);
  drawKeyRow(48, 28, 16, 4);

  ctx.fillStyle = '#2c3134';
  ctx.fillRect(0, 32, 40, 4);
  ctx.fillStyle = '#42484b';
  ctx.fillRect(10, 33, 20, 1);
  ctx.fillStyle = '#2b3033';
  ctx.fillRect(40, 32, 8, 4);
  ctx.fillStyle = '#363c3f';
  ctx.fillRect(42, 33, 4, 1);
  ctx.fillStyle = '#2b3033';
  ctx.fillRect(48, 32, 16, 4);
  ctx.fillStyle = '#3c4245';
  ctx.fillRect(51, 33, 10, 1);

  const texture = new Texture({
    name: 'office_keyboard.png',
    namespace: 'apocalypse_firstlight',
    folder: 'block',
    id: '0'
  }).fromDataURL(canvas.toDataURL()).add(false);

  const groups = {};
  const addGroup = (name, parent) => {
    groups[name] = new Group({name, origin: [8, 0, 8]}).addTo(parent ? groups[parent] : 'root').init();
  };
  addGroup('office_keyboard_root');
  addGroup('keyboard_body', 'office_keyboard_root');
  addGroup('main_key_area', 'office_keyboard_root');
  addGroup('navigation_key_area', 'office_keyboard_root');
  addGroup('numpad_area', 'office_keyboard_root');

  const materialUv = {
    body: [1, 1, 15, 15],
    deck: [17, 1, 31, 15],
    edge: [33, 1, 47, 15],
    recess: [49, 1, 63, 15]
  };
  const addCube = (name, from, to, parent, uv = materialUv.body, topUv = uv) => {
    const faces = {};
    for (const direction of ['north', 'south', 'east', 'west', 'up', 'down']) {
      faces[direction] = {uv: [...uv], texture: texture.uuid};
    }
    faces.up.uv = [...topUv];
    return new Cube({name, from, to, faces, box_uv: false, autouv: 0}).addTo(groups[parent]).init();
  };

  addCube('lower_shell', [1.8, 0.1, 6.35], [14.2, 0.55, 9.7], 'keyboard_body', materialUv.body);
  addCube('upper_deck', [1.95, 0.55, 6.45], [14.05, 0.72, 9.55], 'keyboard_body', materialUv.deck);
  addCube('front_lip', [2, 0, 6.2], [14, 0.1, 6.45], 'keyboard_body', materialUv.edge);
  addCube('rear_rail', [1.95, 0.72, 9.3], [14.05, 0.88, 9.6], 'keyboard_body', materialUv.edge);
  addCube('left_endcap', [1.7, 0.15, 6.4], [1.8, 0.7, 9.55], 'keyboard_body', materialUv.edge);
  addCube('right_endcap', [14.2, 0.15, 6.4], [14.3, 0.7, 9.55], 'keyboard_body', materialUv.edge);
  addCube('underside_foot_left', [2.4, 0, 9.2], [3.6, 0.1, 9.55], 'keyboard_body', materialUv.recess);
  addCube('underside_foot_right', [12.4, 0, 9.2], [13.6, 0.1, 9.55], 'keyboard_body', materialUv.recess);
  addCube('main_navigation_divider', [9.82, 0.72, 6.75], [9.9, 0.86, 9.16], 'keyboard_body', materialUv.edge);
  addCube('navigation_numpad_divider', [11.4, 0.72, 6.75], [11.48, 0.86, 9.16], 'keyboard_body', materialUv.edge);

  addCube('main_key_recess', [2.25, 0.72, 6.62], [9.72, 0.8, 9.2], 'main_key_area', materialUv.recess);
  addCube('function_key_row', [2.4, 0.8, 8.82], [9.57, 0.9, 9.08], 'main_key_area', materialUv.body, [0, 16, 40, 20]);
  addCube('main_key_row_01', [2.4, 0.8, 8.36], [9.57, 0.92, 8.67], 'main_key_area', materialUv.body, [0, 16, 40, 20]);
  addCube('main_key_row_02', [2.45, 0.8, 7.91], [9.57, 0.92, 8.22], 'main_key_area', materialUv.body, [0, 20, 40, 24]);
  addCube('main_key_row_03', [2.55, 0.8, 7.46], [9.57, 0.92, 7.77], 'main_key_area', materialUv.body, [0, 24, 40, 28]);
  addCube('main_key_row_04', [2.7, 0.8, 7.01], [9.57, 0.92, 7.32], 'main_key_area', materialUv.body, [0, 28, 40, 32]);
  addCube('left_modifier_cluster', [2.4, 0.8, 6.65], [4.18, 0.91, 6.94], 'main_key_area', materialUv.body, [40, 32, 48, 36]);
  addCube('spacebar', [4.38, 0.8, 6.65], [8.32, 0.93, 6.94], 'main_key_area', materialUv.body, [0, 32, 40, 36]);
  addCube('right_modifier_cluster', [8.52, 0.8, 6.65], [9.57, 0.91, 6.94], 'main_key_area', materialUv.body, [40, 32, 48, 36]);

  addCube('navigation_recess', [10.02, 0.72, 6.62], [11.3, 0.8, 9.2], 'navigation_key_area', materialUv.recess);
  addCube('navigation_top_row', [10.15, 0.8, 8.78], [11.17, 0.91, 9.08], 'navigation_key_area', materialUv.body, [40, 16, 48, 20]);
  addCube('navigation_middle_row', [10.15, 0.8, 8.31], [11.17, 0.91, 8.61], 'navigation_key_area', materialUv.body, [40, 20, 48, 24]);
  addCube('arrow_key_up', [10.47, 0.8, 7.43], [10.86, 0.91, 7.73], 'navigation_key_area', materialUv.body, [40, 24, 48, 28]);
  addCube('arrow_key_bottom_row', [10.15, 0.8, 7.01], [11.17, 0.91, 7.31], 'navigation_key_area', materialUv.body, [40, 24, 48, 28]);

  addCube('numpad_recess', [11.58, 0.72, 6.62], [13.74, 0.8, 9.2], 'numpad_area', materialUv.recess);
  addCube('numpad_row_01', [11.7, 0.8, 8.78], [13.6, 0.92, 9.08], 'numpad_area', materialUv.body, [48, 16, 64, 20]);
  addCube('numpad_row_02', [11.7, 0.8, 8.34], [13.6, 0.92, 8.64], 'numpad_area', materialUv.body, [48, 20, 64, 24]);
  addCube('numpad_row_03', [11.7, 0.8, 7.9], [13.6, 0.92, 8.2], 'numpad_area', materialUv.body, [48, 24, 64, 28]);
  addCube('numpad_row_04', [11.7, 0.8, 7.46], [12.92, 0.92, 7.76], 'numpad_area', materialUv.body, [48, 28, 64, 32]);
  addCube('numpad_zero_cluster', [11.7, 0.8, 6.97], [12.92, 0.92, 7.27], 'numpad_area', materialUv.body, [48, 32, 64, 36]);
  addCube('numpad_enter', [13.08, 0.8, 6.97], [13.6, 0.93, 7.7], 'numpad_area', materialUv.body, [48, 32, 64, 36]);

  for (let i = 0; i < 3; i++) {
    addCube(`status_indicator_${String(i + 1).padStart(2, '0')}`, [12.92 + i * 0.25, 0.88, 9.35], [13.05 + i * 0.25, 0.94, 9.48], 'numpad_area', materialUv.edge);
  }

  Project.save_path = 'D:/Minecraft Modding/Apocalypse First Light/src/main/blockbench/office_keyboard.bbmodel';
  Undo.finishEdit('Create AFL office keyboard');
  Canvas.updateAll();
  return {
    project: Project.name,
    cubes: Cube.all.length,
    groups: Group.all.length,
    texture: [Project.texture_width, Project.texture_height]
  };
})()
