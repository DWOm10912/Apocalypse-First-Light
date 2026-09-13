// Native Blockbench OBJ export of the saved, dry production source.
// Run in Blockbench with this file evaluated; the currently edited tab is preserved.
(function () {
  const fs = require('fs');
  const path = require('path');
  const root = 'D:/Minecraft Modding/Apocalypse First Light';
  const source = path.join(root, 'src/main/blockbench/commercial_flushometer_toilet.bbmodel');
  const target = path.join(root, 'src/main/resources/assets/apocalypse_firstlight/models/block');
  const saved = JSON.parse(fs.readFileSync(source, 'utf8'));
  if (saved.elements.length !== 203 || saved.elements.some(e => /water|funnel/i.test(e.name))) {
    throw Error('The saved dry production model was changed; inspect it before export.');
  }
  const original = Project;
  try {
    Codecs.project.load(saved, {path: source, no_file: true});
    if (Cube.all.length !== saved.elements.length) throw Error('Blockbench did not load every saved cube');
    const uuid = Texture.all[0]?.uuid;
    if (uuid !== saved.textures[0].uuid) throw Error('Texture UUID changed during load');
    let obj = Codecs.obj.compile({mtl_name: 'commercial_flushometer_toilet.mtl'});
    const objects = (obj.match(/^o /gm) || []).length;
    if (objects !== 203) throw Error(`Expected 203 OBJ objects, found ${objects}`);
    // The authoring model is centered on X/Z=0 and the OBJ exporter uses 1/16 units.
    // Shift to the Minecraft block center X/Z=0.5 without changing its geometry.
    obj = obj.replace(/^v ([^ ]+) ([^ ]+) ([^\r\n]+)/gm, (_, x, y, z) =>
      `v ${Number(x) + .5} ${y} ${Number(z) + .5}`);
    const vertices = obj.split('\n').filter(line => line.startsWith('v '));
    for (const line of vertices) {
      const [, x, y, z] = line.split(' ').map(Number);
      if (![x, y, z].every(Number.isFinite) || x < 0 || x > 1 || y < 0 || y > 1 || z < 0 || z > 1)
        throw Error(`OBJ vertex outside one block: ${line}`);
    }
    const material = `newmtl m_${uuid}\nKd 1 1 1\nmap_Kd apocalypse_firstlight:block/commercial_flushometer_toilet\n`;
    fs.mkdirSync(target, {recursive: true});
    fs.writeFileSync(path.join(target, 'commercial_flushometer_toilet.obj'), obj);
    fs.writeFileSync(path.join(target, 'commercial_flushometer_toilet.mtl'), material);
    return JSON.stringify({objects, faces: (obj.match(/^f /gm) || []).length, vertices: vertices.length,
      savedCubes: saved.elements.length, textureUuid: uuid});
  } finally {
    original?.select();
  }
})()
