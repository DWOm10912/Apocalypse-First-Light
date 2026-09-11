(() => {
  if (Project.name !== 'office_cubicle_partition' || Cube.all.length !== 100 || Texture.all.length !== 1) {
    throw new Error('Expected the 100-cube live office cubicle partition project');
  }
  const frontGroup = Group.all.find(group => group.name === 'fabric_panel_front');
  const backGroup = Group.all.find(group => group.name === 'fabric_panel_back');
  const texture = Texture.all[0];
  if (!frontGroup || !backGroup || !texture) throw new Error('Panel groups or texture missing');
  const oldPanels = Cube.all.filter(cube => /^fabric_(front|back)_\d/.test(cube.name));
  if (oldPanels.length !== 60) throw new Error(`Expected 60 segmented panel cubes, got ${oldPanels.length}`);
  Undo.initEdit({elements: oldPanels, outliner: true});
  for (const panel of oldPanels) panel.remove();
  const faces = {};
  for (const direction of ['north', 'south', 'east', 'west', 'up', 'down']) {
    faces[direction] = {uv: [1, 33, 31, 63], texture: texture.uuid};
  }
  new Cube({
    name: 'fabric_panel_front_main',
    from: [1.3, 3.2, 7.4],
    to: [14.7, 19, 7.55],
    faces: JSON.parse(JSON.stringify(faces)),
    box_uv: false,
    autouv: 0
  }).addTo(frontGroup).init();
  new Cube({
    name: 'fabric_panel_back_main',
    from: [1.3, 3.2, 8.45],
    to: [14.7, 19, 8.6],
    faces: JSON.parse(JSON.stringify(faces)),
    box_uv: false,
    autouv: 0
  }).addTo(backGroup).init();
  Undo.finishEdit('Replace segmented fabric with single panels');
  Canvas.updateAll();
  return {project: Project.name, removed: oldPanels.length, cubes: Cube.all.length};
})()
