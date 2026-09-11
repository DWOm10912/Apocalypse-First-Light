(() => {
  const source = ModelProject.all.find(project => project.name === 'office_cubicle_partition');
  if (!source) throw new Error('Office cubicle partition source project is not open');
  source.select();
  if (Cube.all.length !== 42 || Texture.all.length !== 1) throw new Error('Unexpected office partition source state');
  const required = names => names.map(name => {
    const cube = Cube.all.find(element => element.name === name);
    if (!cube) throw new Error(`Missing cube ${name}`);
    return cube;
  });
  Undo.initEdit({elements: Cube.all, outliner: true});
  for (const cube of required(['panel_core_main', 'fabric_panel_front_main', 'fabric_panel_back_main'])) cube.to[1] = 23;
  for (const side of ['left', 'right']) {
    for (const cube of required([
      `${side}_post_core`, `${side}_post_outer_spine`,
      `${side}_post_front_highlight`, `${side}_post_back_shadow`
    ])) cube.to[1] = 23.8;
    required([`${side}_post_inner_reveal`])[0].to[1] = 23.35;
    const cap = required([`${side}_post_top_cap`])[0];
    cap.from[1] = 23.8;
    cap.to[1] = 24;
    for (const cube of required([`${side}_front_joint_2`, `${side}_back_joint_2`])) {
      cube.from[1] += 4;
      cube.to[1] += 4;
    }
  }
  for (const cube of required(['top_rail_core', 'top_rail_cap', 'top_rail_front_highlight', 'top_rail_back_shadow'])) {
    cube.from[1] += 4;
    cube.to[1] += 4;
  }
  Undo.finishEdit('Raise office partition to 1.5 blocks');
  for (const preview of Preview.all) {
    if (!preview.controls || !preview.camera) continue;
    preview.controls.target.set(8, 12, 8);
    preview.camera.position.set(36, 30, -38);
    preview.camera.zoom = 1.2;
    preview.camera.updateProjectionMatrix();
    preview.controls.update();
  }
  Canvas.updateAll();
  return {project: Project.name, cubes: Cube.all.length, height: Math.max(...Cube.all.map(cube => cube.to[1]))};
})()
