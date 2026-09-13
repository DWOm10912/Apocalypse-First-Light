// Restore only the two missing dry-cavity cubes explicitly approved by the user.
(function () {
  const fs = require('fs');
  const root = 'D:/Minecraft Modding/Apocalypse First Light/';
  const source = root + 'src/main/blockbench/commercial_flushometer_toilet.bbmodel';
  const backup = root + 'src/main/blockbench/previews/commercial_flushometer_toilet_before_dry_closure_restore.bbmodel';
  const template = root + 'src/main/blockbench/previews/commercial_flushometer_toilet_before_closed_dry_shell.bbmodel';
  if (Project.name !== 'commercial_flushometer_toilet' || Cube.all.length !== 201) throw Error('Unexpected live toilet model');
  const saved = JSON.parse(fs.readFileSync(source, 'utf8'));
  if (saved.elements.length !== 201) throw Error('Saved source changed; inspect before restoring');
  const group = Group.all.find(g => g.name === 'closed_ceramic_shell');
  if (!group || Cube.all.some(c => c.name === 'dry_shell_2_18' || c.name === 'closed_ceramic_bottom'))
    throw Error('Dry cavity no longer matches the expected two missing parts');
  if (!fs.existsSync(backup)) fs.writeFileSync(backup, fs.readFileSync(source));
  const old = JSON.parse(fs.readFileSync(template, 'utf8')).elements.find(c => c.name === 'ceramic_liner_2_18');
  if (!old) throw Error('The saved inner-wall template is missing');
  const centerY = (old.from[1] + old.to[1]) / 2;
  const height = old.to[1] - old.from[1] - .075 + .06;
  const tex = Texture.all[0].uuid;
  const wall = new Cube({name: 'dry_shell_2_18', from: [old.from[0], centerY - height / 2, old.to[2] - .3],
    to: [old.to[0], centerY + height / 2, old.to[2]], origin: old.origin,
    rotation: old.rotation, autouv: 0, box_uv: false}).addTo(group).init();
  for (const face of Object.values(wall.faces)) { face.texture = tex; face.uv = [...old.faces.south.uv]; }
  const bottom = new Cube({name: 'closed_ceramic_bottom', from: [-1.175, 5.48, -1.925],
    to: [1.175, 5.83, .925], origin: [0, 5.655, -.5], rotation: [0, 0, 0],
    autouv: 0, box_uv: false}).addTo(group).init();
  for (const face of Object.values(bottom.faces)) { face.texture = tex; face.uv = [72, 8, 88, 24]; }
  if (Cube.all.length !== 203) throw Error('Unexpected restored count');
  Cube.selected.empty(); Canvas.updateAll();
  fs.writeFileSync(source, Codecs.project.compile());
  Project.saved = true;
  return JSON.stringify({restored: [wall.name, bottom.name], cubes: Cube.all.length});
})()
