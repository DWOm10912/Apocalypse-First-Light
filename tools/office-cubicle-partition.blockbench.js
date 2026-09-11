(() => {
  if (Project.uuid !== '8f7f6624-0844-bfd3-e9ea-92d1f98ee746' || Cube.all.length || Texture.all.length) {
    throw new Error('Expected the empty Office Cubicle Partition project');
  }

  Undo.initEdit({elements: [], textures: [], outliner: true});
  Project.name = 'office_cubicle_partition';
  Project.texture_width = 128;
  Project.texture_height = 128;
  Project.ambientocclusion = false;

  const canvas = document.createElement('canvas');
  canvas.width = canvas.height = 128;
  const ctx = canvas.getContext('2d');
  ctx.imageSmoothingEnabled = false;

  const swatch = (index, base, light, dark) => {
    const x = (index % 4) * 32;
    const y = Math.floor(index / 4) * 32;
    ctx.fillStyle = base;
    ctx.fillRect(x, y, 32, 32);
    ctx.fillStyle = light;
    ctx.fillRect(x + 1, y + 1, 30, 2);
    ctx.fillRect(x + 2, y + 3, 1, 26);
    ctx.fillStyle = dark;
    ctx.fillRect(x + 1, y + 29, 30, 2);
    ctx.fillRect(x + 29, y + 3, 2, 26);
  };

  swatch(0, '#30353a', '#42494e', '#202428');
  swatch(1, '#3a4045', '#50585e', '#292e32');
  swatch(2, '#252a2e', '#353b40', '#171b1e');
  swatch(3, '#4a5156', '#626a70', '#343a3e');
  swatch(4, '#aeb7be', '#bac2c8', '#9ca5ac');
  swatch(5, '#b5bdc3', '#c0c7cc', '#a3abb1');
  swatch(6, '#a7b0b7', '#b4bcc2', '#969fa6');
  swatch(7, '#bbc2c7', '#c5cbd0', '#aab1b6');
  swatch(8, '#989fa5', '#a7aeb4', '#858d93');
  swatch(9, '#1d2124', '#2b3034', '#111416');
  swatch(10, '#555d62', '#697177', '#3d4449');
  swatch(11, '#8f989f', '#a0a8ae', '#7d858c');
  swatch(12, '#353b40', '#495056', '#24292d');
  swatch(13, '#adb5bb', '#b9c0c5', '#9aa2a8');
  swatch(14, '#2a2f33', '#3d4348', '#1b1f22');
  swatch(15, '#c0c6ca', '#c9ced2', '#afb5b9');
  const fabricSwatch = (index, base, softLight, softDark) => {
    const x = (index % 4) * 32;
    const y = Math.floor(index / 4) * 32;
    ctx.fillStyle = base;
    ctx.fillRect(x, y, 32, 32);
    ctx.fillStyle = softLight;
    ctx.fillRect(x + 3, y + 4, 15, 10);
    ctx.fillRect(x + 18, y + 18, 11, 8);
    ctx.fillStyle = softDark;
    ctx.fillRect(x + 4, y + 21, 12, 7);
  };
  fabricSwatch(4, '#adb6bd', '#b0b9c0', '#aab3ba');
  fabricSwatch(5, '#afb8be', '#b2bbc1', '#acb5bb');
  fabricSwatch(6, '#abb4bb', '#aeb7be', '#a8b1b8');
  fabricSwatch(7, '#b1b9bf', '#b4bcc2', '#aeb6bc');

  const texture = new Texture({
    name: 'office_cubicle_partition.png',
    namespace: 'apocalypse_firstlight',
    folder: 'block',
    id: '0'
  }).fromDataURL(canvas.toDataURL()).add(false);

  const groups = {};
  const group = (name, parent, origin = [8, 0, 8]) => {
    groups[name] = new Group({name, origin}).addTo(parent ? groups[parent] : 'root').init();
  };
  group('office_cubicle_partition_root');
  group('frame_assembly', 'office_cubicle_partition_root');
  for (const name of ['left_post', 'right_post', 'top_rail', 'bottom_rail', 'frame_details']) {
    group(name, 'frame_assembly');
  }
  group('panel_assembly', 'office_cubicle_partition_root');
  for (const name of ['fabric_panel_front', 'fabric_panel_back', 'panel_core']) {
    group(name, 'panel_assembly');
  }
  group('left_foot_assembly', 'office_cubicle_partition_root');
  group('left_foot_main', 'left_foot_assembly');
  group('left_foot_pad', 'left_foot_assembly');
  group('right_foot_assembly', 'office_cubicle_partition_root');
  group('right_foot_main', 'right_foot_assembly');
  group('right_foot_pad', 'right_foot_assembly');

  const uv = index => {
    const x = (index % 4) * 32;
    const y = Math.floor(index / 4) * 32;
    return [x + 1, y + 1, x + 31, y + 31];
  };
  const cube = (name, from, to, material, parent) => {
    const faces = {};
    for (const direction of ['north', 'south', 'east', 'west', 'up', 'down']) {
      faces[direction] = {uv: uv(material), texture: texture.uuid};
    }
    return new Cube({name, from, to, faces, box_uv: false, autouv: 0})
      .addTo(groups[parent]).init();
  };

  cube('panel_core_main', [1.3, 3.2, 7.55], [14.7, 31, 8.45], 8, 'panel_core');
  cube('fabric_panel_front_main', [1.3, 3.2, 7.4], [14.7, 31, 7.55], 4, 'fabric_panel_front');
  cube('fabric_panel_back_main', [1.3, 3.2, 8.45], [14.7, 31, 8.6], 4, 'fabric_panel_back');

  const post = (side, x0, x1, outer0, outer1, inner0, inner1, parent) => {
    cube(`${side}_post_core`, [x0, 0.55, 7.2], [x1, 31.8, 8.8], 0, parent);
    cube(`${side}_post_outer_spine`, [outer0, 0.55, 7.05], [outer1, 31.8, 8.95], 2, parent);
    cube(`${side}_post_inner_reveal`, [inner0, 2.7, 7.3], [inner1, 31.35, 8.7], 12, parent);
    cube(`${side}_post_front_highlight`, [x0, 0.55, 7.05], [x1, 31.8, 7.2], 3, parent);
    cube(`${side}_post_back_shadow`, [x0, 0.55, 8.8], [x1, 31.8, 8.95], 14, parent);
    cube(`${side}_post_top_cap`, [Math.min(outer0, x0), 31.8, 7.05], [Math.max(outer1, x1), 32, 8.95], 1, parent);
  };
  post('left', 0.35, 1.15, 0.2, 0.35, 1.15, 1.3, 'left_post');
  post('right', 14.85, 15.65, 15.65, 15.8, 14.7, 14.85, 'right_post');

  cube('top_rail_core', [1.15, 31, 7.25], [14.85, 31.8, 8.75], 0, 'top_rail');
  cube('top_rail_cap', [1, 31.8, 7.1], [15, 32, 8.9], 1, 'top_rail');
  cube('top_rail_front_highlight', [1.15, 31, 7.1], [14.85, 31.8, 7.25], 3, 'top_rail');
  cube('top_rail_back_shadow', [1.15, 31, 8.75], [14.85, 31.8, 8.9], 14, 'top_rail');

  cube('bottom_rail_core', [1.15, 2.45, 7.25], [14.85, 3.2, 8.75], 0, 'bottom_rail');
  cube('bottom_rail_lower_edge', [1.15, 2.3, 7.1], [14.85, 2.45, 8.9], 2, 'bottom_rail');
  cube('bottom_rail_front_highlight', [1.15, 2.45, 7.1], [14.85, 3.2, 7.25], 3, 'bottom_rail');
  cube('bottom_rail_back_shadow', [1.15, 2.45, 8.75], [14.85, 3.2, 8.9], 14, 'bottom_rail');

  for (const [side, x0, x1] of [['left', 0.52, 0.98], ['right', 15.02, 15.48]]) {
    for (const [index, y] of [2.95, 30.45].entries()) {
      cube(`${side}_front_joint_${index + 1}`, [x0, y, 6.9], [x1, y + 0.42, 7.05], 10, 'frame_details');
      cube(`${side}_back_joint_${index + 1}`, [x0, y, 8.95], [x1, y + 0.42, 9.1], 2, 'frame_details');
    }
  }
  cube('bottom_rail_center_joint', [7.75, 2.62, 6.95], [8.25, 3.02, 7.1], 10, 'frame_details');

  const foot = (side, x0, x1, mainParent, padParent) => {
    cube(`${side}_foot_pad`, [x0 + 0.1, 0, 6.85], [x1 - 0.1, 0.15, 9.15], 9, padParent);
    cube(`${side}_foot_base`, [x0, 0.15, 6.7], [x1, 0.35, 9.3], 2, mainParent);
    cube(`${side}_foot_collar`, [x0 + 0.3, 0.35, 7.15], [x1 - 0.3, 0.55, 8.85], 0, mainParent);
    cube(`${side}_foot_front_edge`, [x0 + 0.1, 0.35, 6.85], [x1 - 0.1, 0.47, 7.15], 1, mainParent);
    cube(`${side}_foot_back_edge`, [x0 + 0.1, 0.35, 8.85], [x1 - 0.1, 0.47, 9.15], 14, mainParent);
  };
  foot('left', 0, 1.8, 'left_foot_main', 'left_foot_pad');
  foot('right', 14.2, 16, 'right_foot_main', 'right_foot_pad');

  Project.save_path = 'D:/Minecraft Modding/Apocalypse First Light/src/main/blockbench/office_cubicle_partition.bbmodel';
  Undo.finishEdit('Create AFL office cubicle partition');
  for (const preview of Preview.all) {
    if (!preview.controls || !preview.camera) continue;
    preview.controls.target.set(8, 10, 8);
    preview.camera.position.set(34, 25, -35);
    preview.camera.zoom = 1.35;
    preview.camera.updateProjectionMatrix();
    preview.controls.update();
  }
  Canvas.updateAll();
  return {
    project: Project.name,
    cubes: Cube.all.length,
    groups: Group.all.length,
    texture: [Project.texture_width, Project.texture_height],
    bounds: [16, 32, 2.6]
  };
})()
